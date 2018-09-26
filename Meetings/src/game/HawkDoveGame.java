package game;

public class HawkDoveGame extends Game {
	public static int HAWK = 0;
	public static int DOVE = 1;
	protected static double strategy[] = { HAWK, DOVE };
	protected static double payoff[][] = { { -1, 1 }, { -1, 1 }, };

	public static double getPayoff(int strategy1, int strategy2) {
		return payoff[strategy1][strategy2];
	}
}
