package util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Set;

public class Configuration {

	private HashMap<String, String> configs;
	private Set<String> keys;

	public Configuration(String iniPath) throws Exception {
		File iniFile = new File(iniPath);
		BufferedReader br = new BufferedReader(new FileReader(iniFile));
		String aux = null;
		configs = new HashMap<>();
		while ((aux = br.readLine()) != null) {
			if (aux.trim().length() == 0 || aux.startsWith("%")) {
				continue;
			}
			String[] components = aux.split("=");
			configs.put(components[0].trim(), components[1].trim());
		}
		keys = configs.keySet();
		br.close();
	}

	public String getPropertyValue(String property) {
		if(keys.contains(property)) {
			return configs.get(property);
		}
		return null;
	}

}
