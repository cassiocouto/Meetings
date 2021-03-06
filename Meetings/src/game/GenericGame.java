package game;

import util.Configuration;

public class GenericGame {
	
	public int strategy_count;
	public String profiles[];
	public double strategy_payoff[][];
	public double strategy_proportion[];


	private static GenericGame instance;

	public static GenericGame getInstance() {
		if (instance == null) {
			instance = new GenericGame();
		}
		return instance;
	}

	private GenericGame() {
		try {
			Configuration settings = new Configuration("conf/settings.ini");
			strategy_count = Integer.parseInt(settings.getPropertyValue("strategy_count"));
			profiles = settings.getPropertyValue("strategy_profiles").replace('{', ' ').replace('}', ' ').split(",");
			strategy_payoff = new double[strategy_count][strategy_count];
			for (int i = 0; i < strategy_count; i++) {
				String aux[] = settings.getPropertyValue("strategy_" + i).replace('{', ' ').replace('}', ' ')
						.split(",");
				for (int j = 0; j < aux.length; j++) {
					strategy_payoff[i][j] = Double.parseDouble(aux[j].trim());
				}
			}
			strategy_proportion = new double[strategy_count];
			String aux[] = settings.getPropertyValue("strategy_proportion").replace('{', ' ').replace('}', ' ')
					.split(",");
			for (int i = 0; i < strategy_proportion.length; i++) {
				strategy_proportion[i] = Double.parseDouble(aux[i].trim());
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public int getStrategy_count() {
		return strategy_count;
	}

	public String[] getProfiles() {
		return profiles;
	}

	public double get_strategy_payoff(int my_type, int opponent_type) {
		return strategy_payoff[my_type][opponent_type];
	}

	public int get_best_response(int opponent_type) {
		int candidate = -1;
		for (int i = 0; i < strategy_payoff[opponent_type].length; i++) {
			if (strategy_payoff[opponent_type][i] < 0) {
				candidate = i;
				if (strategy_payoff[i][opponent_type] > 0) {
					return candidate;
				}
			}
		}
		return opponent_type;
	}

	public double[] getStrategy_proportion() {
		return strategy_proportion;
	}

	public void setStrategy_proportion(double[] strategy_proportion) {
		this.strategy_proportion = strategy_proportion;
	}

}
