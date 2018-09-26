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

	@AgentArgument
	private int type;
	@AgentArgument
	private int index;
	@Agent
	private IInternalAccess agent;

	@Belief
	private int adversary = -1;

	@Belief
	private boolean go = false;

	@Belief
	private int acc_payoff = 0;

	@Belief
	private int last_payoff = 0;

	@AgentCreated
	public void created() {
		agentName = createName(this.getClass().getName(), 0);
		registerSelf(agentName, agent.getComponentIdentifier());
	}

	@AgentMessageArrived
	private void messageArrived(Map<String, Object> msg, MessageType mt) {
		if (msg.get(SFipa.PERFORMATIVE) == SFipa.INFORM) {
			String[] players = msg.get(SFipa.CONTENT).toString().split("x");
			for (String p : players) {
				if (Integer.parseInt(p) != index) {
					adversary = Integer.parseInt(p);
				}
			}
			return;
		} else if (msg.get(SFipa.PERFORMATIVE) == SFipa.REQUEST) {
			go = true;
			return;
		} else if (msg.get(SFipa.PERFORMATIVE) == SFipa.PROPOSE) {
			int adversary_strategy = Integer.parseInt(msg.get(SFipa.CONTENT).toString());
			last_payoff = HawkDoveGame.getPayoff(type, adversary_strategy);
			if (adversary < index) {
				sendProposalMessage();
			}
			return;
		}
	}

	@Plan(trigger = @Trigger(factchangeds = "go"))
	private void play() {
		if (go && index < adversary) {
			sendProposalMessage();
		}
		return;
	}

	public void sendProposalMessage() {
		Map<String, Object> msg = new HashMap<>();
		msg.put(SFipa.CONTENT, "" + type);
		msg.put(SFipa.PERFORMATIVE, SFipa.PROPOSE);
		msg.put(SFipa.RECEIVERS,
				new IComponentIdentifier[] { df.getAgentAID(createName(this.getClass().getName(), adversary)) });
		msg.put(SFipa.SENDER, agent.getComponentIdentifier());
		agent.getComponentFeature(IMessageFeature.class).sendMessage(msg, SFipa.FIPA_MESSAGE_TYPE);
	}

	@Plan(trigger = @Trigger(factchangeds = "last_payoff"))
	private void evaluate() {
		acc_payoff += last_payoff;
		if (last_payoff < 0) {
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
		msg.put(SFipa.RECEIVERS,
				new IComponentIdentifier[] { df.getAgentAID(createName(ManagerBDI.class.getName(), 0)) });
		msg.put(SFipa.SENDER, agent.getComponentIdentifier());
		agent.getComponentFeature(IMessageFeature.class).sendMessage(msg, SFipa.FIPA_MESSAGE_TYPE);
		go = false;
	}

	// TODO create super class with this
	protected String createName(String classname, int id) {
		return classname + "_" + id;
	}

	protected void registerSelf(String name, IComponentIdentifier identifier) {
		df = DirectoryFacilitator.getInstance();
		df.registerAgent(name, identifier);
	}
}
