package main;

import java.util.HashMap;
import java.util.Map;
import jadex.bridge.IComponentIdentifier;
import jadex.bridge.IExternalAccess;
import jadex.bridge.service.RequiredServiceInfo;
import jadex.bridge.service.search.SServiceProvider;
import jadex.bridge.service.types.cms.CreationInfo;
import jadex.bridge.service.types.cms.IComponentManagementService;
import jadex.commons.future.IFuture;
import jadex.commons.future.ITuple2Future;
import jadex.commons.future.ThreadSuspendable;
import util.AgentDeployer;
import util.Configuration;

public class Main {
	public static IExternalAccess platform;
	public static String platformName;
	private static IComponentManagementService cms;
	private static Configuration settings;

	public static void main(String[] args) throws Exception {
		String[] defargs = new String[] { "-gui", "false", "-welcome", "false", "-cli", "false", "-printpass", "false",
				"-awareness", "false" };
		IFuture<IExternalAccess> plataform = jadex.base.Starter.createPlatform(defargs);
		ThreadSuspendable sus = new ThreadSuspendable();
		platform = plataform.get(sus);
		cms = SServiceProvider.getService(platform.getServiceProvider(), IComponentManagementService.class,
				RequiredServiceInfo.SCOPE_PLATFORM).get(sus);

		settings = new Configuration("conf/settings.ini");

		int agent_quantity = Integer.parseInt(settings.getPropertyValue("agent_quantity"));
		String manager_class = settings.getPropertyValue("manager_class");
		String agent_class = settings.getPropertyValue("agent_class");
		boolean debug = Boolean.getBoolean(settings.getPropertyValue("debug"));
		
		Map<String, Object> agParam = new HashMap<String, Object>();
		agParam.put("agent_quantity", agent_quantity);
		agParam.put("agent_class", agent_class);
		agParam.put("debug", debug);
		
		new AgentDeployer(agParam, manager_class, cms).deploy();

	}

}
