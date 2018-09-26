package agents;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import game.HawkDoveGame;
import jadex.bridge.IComponentIdentifier;
import jadex.bridge.IComponentStep;
import jadex.bridge.IInternalAccess;
import jadex.bridge.component.IMessageFeature;
import jadex.bridge.fipa.SFipa;
import jadex.bridge.service.RequiredServiceInfo;
import jadex.bridge.service.search.SServiceProvider;
import jadex.bridge.service.types.cms.IComponentManagementService;
import jadex.bridge.service.types.message.MessageType;
import jadex.commons.future.IFuture;
import jadex.commons.future.ThreadSuspendable;
import jadex.micro.annotation.Agent;
import jadex.micro.annotation.AgentArgument;
import jadex.micro.annotation.AgentCreated;
import jadex.micro.annotation.AgentMessageArrived;
import jadex.micro.annotation.Argument;
import jadex.micro.annotation.Arguments;
import util.AgentDeployer;
import util.DirectoryFacilitator;

@Agent
@Arguments({ @Argument(name = "agQty", clazz = Integer.class) })
public class ManagerBDI {
	private int id[];
	private int type[];
	private IComponentIdentifier addresses[];
	private boolean done[];
	private String agentName;
	protected DirectoryFacilitator df;
	private static IComponentManagementService cms;
	private int max_generations = 1000;
	private int curr_generation = 0;
	private boolean debug = false;

	@AgentArgument
	private int agQty;

	@Agent
	private IInternalAccess agent;

	@AgentCreated
	public void created() {
		agentName = createName(this.getClass().getName(), 0);
		registerSelf(agentName, agent.getComponentIdentifier());
		id = new int[agQty];
		type = new int[agQty];
		done = new boolean[agQty];
		addresses = new IComponentIdentifier[agQty];
		startHawkDove50_50();
		start();
	}

	public void start() {
		waitFor(1500);
		printMessage("Gen: " + curr_generation);
		shuffleEncounters();
		sendMessages();

	}

	public void startHawkDove50_50() {
		ThreadSuspendable sus = new ThreadSuspendable();
		cms = SServiceProvider.getService(agent.getServiceProvider(), IComponentManagementService.class,
				RequiredServiceInfo.SCOPE_PLATFORM).get(sus);
		for (int i = 0; i < agQty; i++) {
			Map<String, Object> agParam = new HashMap<String, Object>();
			agParam.put("type", i % 2 == 0 ? HawkDoveGame.DOVE : HawkDoveGame.HAWK);
			agParam.put("index", i);
			new AgentDeployer(agParam, "bin/agents/AgentBDI.class", cms).deploy();
			type[i] = i % 2 == 0 ? HawkDoveGame.DOVE : HawkDoveGame.HAWK;
			addresses[i] = null;
		}
	}

	public void shuffleEncounters() {
		ArrayList<Integer> indexes = new ArrayList<>();
		for (int i = 0; i < agQty; i++) {
			indexes.add(i);
		}
		Collections.shuffle(indexes);
		for (int i = 0; i < agQty; i++) {
			id[i] = indexes.get(i);
		}
	}

	public void sendMessages() {
		if (curr_generation > max_generations) {
			System.exit(0);
			return;
		}
		int typeHawk = 0;
		int typeDove = 0;
		for (int i = 0; i < agQty; i = i + 2) {
			int p1 = id[i];
			int p2 = id[i + 1];
			if (type[p1] == HawkDoveGame.HAWK) {
				typeHawk++;
			} else {
				typeDove++;
			}

			if (type[p2] == HawkDoveGame.HAWK) {
				typeHawk++;
			} else {
				typeDove++;
			}

			IComponentIdentifier p1_AID = addresses[p1];
			IComponentIdentifier p2_AID = addresses[p2];
			while (addresses[p1] == null || addresses[p2] == null) {
				waitFor(1500);

				p1_AID = df.getAgentAID(createName(AgentBDI.class.getName(), p1));
				addresses[p1] = p1_AID;
				p2_AID = df.getAgentAID(createName(AgentBDI.class.getName(), p2));
				addresses[p2] = p2_AID;
			}
			if (p1 > p2) {
				int temp = p2;
				p2 = p1;
				p1 = temp;
				IComponentIdentifier tempAID = p2_AID;
				p1_AID = p2_AID;
				p2_AID = tempAID;
			}
			Map<String, Object> msg = new HashMap<>();
			msg.put(SFipa.CONTENT, p1 + "x" + p2);
			msg.put(SFipa.PERFORMATIVE, SFipa.INFORM);
			msg.put(SFipa.RECEIVERS, new IComponentIdentifier[] { p1_AID });
			msg.put(SFipa.SENDER, agent.getComponentIdentifier());

			agent.getComponentFeature(IMessageFeature.class).sendMessage(msg, SFipa.FIPA_MESSAGE_TYPE);
			printMessage("Sent INFORM message to " + p1_AID);
			done[p1] = false;
			done[p2] = false;
		}
		printMessage(String.format("%s\t%s\t%s", curr_generation, typeHawk, typeDove), true);
		return;

	}

	@AgentMessageArrived
	private synchronized void messageArrived(Map<String, Object> msg, MessageType mt) {
		printMessage("Received INFORM message from " + msg.get(SFipa.SENDER).toString());
		String content[] = msg.get(SFipa.CONTENT).toString().split(" ");
		int curr_index = Integer.parseInt(content[0]);
		int curr_type = Integer.parseInt(content[1]);
		done[curr_index] = true;
		type[curr_index] = curr_type;
		Boolean all_done = true;
		for (int i = 0; i < agQty; i++) {
			all_done = all_done && done[i];
		}
		if (all_done) {
			printMessage("Ending this gen");
			curr_generation++;
			start();
		}
		return;
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
			System.out.println(agentName + ": " + message);
		}
	}

	protected synchronized void printMessage(String message, boolean permit) {
		if (permit) {
			System.out.println(message);
		}
	}

	protected void waitFor(long time) {
		agent.waitForDelay(time, new IComponentStep<Void>() {

			@Override
			public IFuture execute(IInternalAccess arg0) {
				return IFuture.DONE;
			}
		}).get();
	}

}
