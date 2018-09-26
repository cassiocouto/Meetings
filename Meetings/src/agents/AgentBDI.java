package agents;

import java.util.HashMap;
import java.util.Map;

import game.HawkDoveGame;
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
@Arguments({ @Argument(name = "type", clazz = Integer.class), @Argument(name = "index", clazz = Integer.class) })
public class AgentBDI {
	protected DirectoryFacilitator df;
	private String agentName;
	private boolean debug = false;

	@AgentArgument
	private int type;
	@AgentArgument
	private int index;
	@Agent
	private IInternalAccess agent;

	@Belief
	private int adversary = -1;

	@Belief
	private double acc_payoff = 0;

	@Belief
	private Double last_payoff = null;

	@AgentCreated
	public void created() {
		agentName = createName(this.getClass().getName(), index);
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
			int adversary_strategy = Integer.parseInt(msg.get(SFipa.CONTENT).toString());
			if (adversary == -1) {
				sendProposalMessage(adversary_AID);
			}
			last_payoff = HawkDoveGame.getPayoff(type, adversary_strategy);
			evaluate();
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
		acc_payoff += last_payoff;
		if (last_payoff < 0) {
			printMessage("I'm going to change strategies");
			if (type == HawkDoveGame.HAWK) {
				type = HawkDoveGame.DOVE;
			} else {
				type = HawkDoveGame.HAWK;
			}
		}
		informManager();
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

	// TODO create super class with this
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
