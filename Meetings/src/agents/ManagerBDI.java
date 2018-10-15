package agents;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import game.GenericGame;
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
import util.Configuration;
import util.DirectoryFacilitator;

@Agent
@Arguments({ @Argument(name = "agent_quantity", clazz = Integer.class),
		@Argument(name = "agent_class", clazz = String.class), @Argument(name = "debug", clazz = Boolean.class) })
public class ManagerBDI {
	private int id[];
	private int census[];
	private int type_changes[];
	private IComponentIdentifier addresses[];
	private boolean done[];
	private String agentName;
	protected DirectoryFacilitator df;
	private static IComponentManagementService cms;
	private int max_generations = 1000;
	private int curr_generation = 0;
	private GenericGame model;

	@AgentArgument
	private int agent_quantity;
	@AgentArgument
	private String agent_class;
	@AgentArgument
	private Boolean debug;
	@Agent
	private IInternalAccess agent;

	@AgentCreated
	public void created() throws Exception {
		agentName = createName(this.getClass().getName(), 0);
		model = GenericGame.getInstance();
		registerSelf(agentName, agent.getComponentIdentifier());
		id = new int[agent_quantity];
		census = new int[agent_quantity];
		type_changes = new int[model.strategy_count * model.strategy_count];
		done = new boolean[agent_quantity];
		addresses = new IComponentIdentifier[agent_quantity];
		startAgents();
		start();
	}

	public void start() {
		waitFor(1500);
		printMessage("Gen: " + curr_generation);
		shuffleEncounters();
		sendMessages();

	}

	public void startAgents() throws Exception {
		ThreadSuspendable sus = new ThreadSuspendable();
		cms = SServiceProvider.getService(agent.getServiceProvider(), IComponentManagementService.class,
				RequiredServiceInfo.SCOPE_PLATFORM).get(sus);

		int distribution[] = new int[agent_quantity];
		int index = 0;
		for (int i = 0; i < model.profiles.length; i++) {
			int threshold = (int) Math.round(model.getStrategy_proportion()[i] * agent_quantity);
			if (i == model.profiles.length - 1) {
				for (; index < agent_quantity; index++) {
					distribution[index] = i;
				}
			} else {
				int j = index;
				for (; j < index + threshold; j++) {
					distribution[j] = i;
				}
				index = j;
			}
		}
		Configuration c = new Configuration("conf/settings.ini");
		for (int i = 0; i < agent_quantity; i++) {
			Map<String, Object> agParam = new HashMap<String, Object>();
			agParam.put("type", distribution[i]);
			agParam.put("index", i);
			agParam.put("strategy_change_condition", c.getPropertyValue("strategy_change_condition"));
			agParam.put("strategy_change_type", c.getPropertyValue("strategy_change_type"));
			new AgentDeployer(agParam, agent_class, cms).deploy();
			census[i] = distribution[i];
			addresses[i] = null;
		}
		printMessage(getHeader(), true);
		printMessage(getResultLine(curr_generation), true);
	}

	public void shuffleEncounters() {
		ArrayList<Integer> indexes = new ArrayList<>();
		for (int i = 0; i < agent_quantity; i++) {
			indexes.add(i);
		}
		Collections.shuffle(indexes);
		for (int i = 0; i < agent_quantity; i++) {
			id[i] = indexes.get(i);
		}
	}

	public void sendMessages() {
		if (curr_generation > max_generations) {
			System.exit(0);
			return;
		}
		int types_count[] = new int[model.strategy_count];

		for (int i = 0; i < types_count.length; i++) {
			types_count[i] = 0;
		}

		for (int i = 0; i < agent_quantity; i = i + 2) {
			int p1 = id[i];
			int p2 = id[i + 1];
			for (int j = 0; j < model.strategy_count; j++) {
				if (census[p1] == j) {
					types_count[j] += 1;
				}
				if (census[p2] == j) {
					types_count[j] += 1;
				}
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
		return;

	}

	@AgentMessageArrived
	private synchronized void messageArrived(Map<String, Object> msg, MessageType mt) {
		printMessage("Received INFORM message from " + msg.get(SFipa.SENDER).toString());
		String content[] = msg.get(SFipa.CONTENT).toString().split(" ");
		int curr_index = Integer.parseInt(content[0]);
		int curr_type = Integer.parseInt(content[1]);
		done[curr_index] = true;
		// census[curr_index] = curr_type;
		registerCensus(curr_index, curr_type);
		Boolean all_done = true;
		for (int i = 0; i < agent_quantity; i++) {
			all_done = all_done && done[i];
		}
		if (all_done) {
			printMessage("Ending this gen");
			curr_generation++;
			printMessage(getResultLine(curr_generation), true);
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

	private void registerCensus(int id, int type) {
		int old_type = census[id];
		registerChanges(old_type, type);
		census[id] = type;
	}

	private int[] countCensus() {
		int[] result = new int[model.strategy_count];
		for (int i = 0; i < census.length; i++) {
			result[census[i]] += 1;
		}
		return result;
	}

	private void resetChanges() {
		type_changes = new int[model.strategy_count * model.strategy_count];
		for (int i = 0; i < type_changes.length; i++) {
			type_changes[i] = 0;
		}
	}

	private void registerChanges(int old_type, int new_type) {
		int index = (old_type * model.strategy_count) + new_type;
		type_changes[index] += 1;
	}

	private String getHeader() {
		String aux = "gen\t";
		for (int i = 0; i < model.strategy_count; i++) {
			aux = aux + String.format("strategy_%s\t", i);
		}
		for (int i = 0; i < model.strategy_count; i++) {
			for (int j = 0; j < model.strategy_count; j++) {
				aux = aux + String.format("change%s->%s\t", i, j);
			}
		}
		aux = aux + "\n";
		return aux;
	}

	private String getResultLine(int gen) {
		String aux = String.format("%s\t", gen);
		int[] result = countCensus();
		for (int i = 0; i < model.strategy_count; i++) {
			aux = aux + String.format("%s\t", result[i]);
		}
		for (int i = 0; i < type_changes.length; i++) {
			aux = aux + String.format("%s\t", type_changes[i]);
		}
		aux = aux + "\n";
		resetChanges();
		return aux;
	}

}
