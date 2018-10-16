package agents;

import java.util.HashMap;
import java.util.Map;

import game.GenericGame;
import jadex.bdiv3.annotation.Belief;
import jadex.bdiv3.annotation.Plan;
import jadex.bdiv3.annotation.Trigger;
import jadex.bridge.IComponentIdentifier;
import jadex.bridge.IInternalAccess;
import jadex.bridge.component.IMessageFeature;
import jadex.bridge.fipa.SFipa;
import jadex.bridge.service.types.message.MessageType;
import jadex.micro.annotation.Agent;
import jadex.micro.annotation.AgentArgument;
import jadex.micro.annotation.AgentCreated;
import jadex.micro.annotation.AgentMessageArrived;
import jadex.micro.annotation.Argument;
import jadex.micro.annotation.Arguments;
import util.DirectoryFacilitator;

@Agent
@Arguments({ @Argument(name = "type", clazz = Integer.class), @Argument(name = "index", clazz = Integer.class),
		@Argument(name = "strategy_change_condition", clazz = String.class),
		@Argument(name = "strategy_change_type", clazz = String.class) })
public class AgentBDI {
	protected DirectoryFacilitator df;
	private String agentName;
	private boolean debug = false;
	private GenericGame model;

	@AgentArgument
	private int type;
	@AgentArgument
	private int index;
	@AgentArgument
	private String strategy_change_condition;
	@AgentArgument
	private String strategy_change_type;
	@Agent
	private IInternalAccess agent;

	@Belief
	private int adversary = -1;
	@Belief
	private int adversary_strategy = -1;

	@Belief
	private Double last_payoff = null;

	@Belief
	private Double strategy_acc_payoff_history[];

	@AgentCreated
	public void created() throws Exception {
		agentName = createName(this.getClass().getName(), index);
		model = GenericGame.getInstance();
		strategy_acc_payoff_history = new Double[model.strategy_count];
		for (int i = 0; i < strategy_acc_payoff_history.length; i++) {
			strategy_acc_payoff_history[i] = 1d;
		}
		registerSelf(agentName, agent.getComponentIdentifier());
	}

	@AgentMessageArrived
	private void messageArrived(Map<String, Object> msg, MessageType mt) {
		if (msg.get(SFipa.PERFORMATIVE) == SFipa.INFORM) {
			printMessage("Received INFORM message from " + msg.get(SFipa.SENDER).toString() + ": "
					+ msg.get(SFipa.CONTENT).toString());
			String[] players = msg.get(SFipa.CONTENT).toString().split("x");
			for (String p : players) {
				if (Integer.parseInt(p) != index) {
					adversary = Integer.parseInt(p);
				}
			}
			return;
		} else if (msg.get(SFipa.PERFORMATIVE) == SFipa.PROPOSE) {
			printMessage("Received PROPOSE message from " + msg.get(SFipa.SENDER).toString());
			IComponentIdentifier adversary_AID = (IComponentIdentifier) msg.get(SFipa.SENDER);
			adversary_strategy = Integer.parseInt(msg.get(SFipa.CONTENT).toString());
			if (adversary == -1) {
				sendProposalMessage(adversary_AID);
			}
			last_payoff = model.get_strategy_payoff(type, adversary_strategy);
			evaluate();
			return;
		} else if (msg.get(SFipa.PERFORMATIVE) == SFipa.PROTOCOL_RECRUITING) {
			changeStrategy(true);
			return;
		}
	}

	@Plan(trigger = @Trigger(factchangeds = "adversary"))
	private void play() {
		if (adversary != -1 && index < adversary) {
			IComponentIdentifier adversary_AID = df.getAgentAID(createName(this.getClass().getName(), adversary));
			sendProposalMessage(adversary_AID);
		}
		return;
	}

	public void sendProposalMessage(IComponentIdentifier adversary_AID) {
		Map<String, Object> msg = new HashMap<>();
		msg.put(SFipa.CONTENT, "" + type);
		msg.put(SFipa.PERFORMATIVE, SFipa.PROPOSE);
		msg.put(SFipa.RECEIVERS, new IComponentIdentifier[] { adversary_AID });
		msg.put(SFipa.SENDER, agent.getComponentIdentifier());
		agent.getComponentFeature(IMessageFeature.class).sendMessage(msg, SFipa.FIPA_MESSAGE_TYPE);
		printMessage("Sent PROPOSE message to " + adversary_AID);
	}

	private void evaluate() {
		printMessage("Evaluating payoff...");
		if (last_payoff == null)
			return;

		if (strategy_acc_payoff_history[type] + last_payoff >= 0) {
			strategy_acc_payoff_history[type] += last_payoff;
		} else {
			strategy_acc_payoff_history[type] = 0d;
		}

		changeStrategy();
		informManager();
	}

	private void changeStrategy() {
		changeStrategy(false);
	}

	private void changeStrategy(boolean inconditional_change) {
		if (inconditional_change || (strategy_change_condition.equalsIgnoreCase("negative_payoff") && last_payoff < 0)
				|| (strategy_change_condition.equalsIgnoreCase("negative_zero_payoff") && last_payoff <= 0)) {

		} else {
			return;
		}
		printMessage("I'm going to change strategies");
		if (strategy_change_type.equalsIgnoreCase("best_acc_payoff")) {
			double sum = 0;
			for (int i = 0; i < strategy_acc_payoff_history.length; i++) {
				if (i == type) {
					continue;
				}
				sum += strategy_acc_payoff_history[i];
			}

			double random = Math.random();
			for (int i = 0; i < strategy_acc_payoff_history.length; i++) {
				if (i == type) {
					continue;
				}
				if (random < strategy_acc_payoff_history[i] / sum) {
					type = i;
					return;
				}
			}
		} else if (strategy_change_type.equalsIgnoreCase("revenge")) {
			type = model.get_best_response(adversary_strategy);
		}
	}

	private void informManager() {
		Map<String, Object> msg = new HashMap<>();
		msg.put(SFipa.CONTENT, index + " " + type);
		msg.put(SFipa.PERFORMATIVE, SFipa.INFORM);
		IComponentIdentifier manager_AID = df.getAgentAID(createName(ManagerBDI.class.getName(), 0));
		msg.put(SFipa.RECEIVERS, new IComponentIdentifier[] { manager_AID });
		msg.put(SFipa.SENDER, agent.getComponentIdentifier());
		agent.getComponentFeature(IMessageFeature.class).sendMessage(msg, SFipa.FIPA_MESSAGE_TYPE);
		printMessage("Sent INFORM message to " + manager_AID);
		reset();
		printMessage("Ending this gen");
	}

	public void reset() {
		adversary = -1;
	}

	protected String createName(String classname, int id) {
		return classname + "_" + id;
	}

	protected void registerSelf(String name, IComponentIdentifier identifier) {
		df = DirectoryFacilitator.getInstance();
		df.registerAgent(name, identifier);
	}

	protected synchronized void printMessage(String message) {
		if (debug) {
			System.out.println(agentName + ":" + message);
		}
	}
}
