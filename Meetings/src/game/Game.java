package game;

public abstract class Game {
	protected static int strategy[] = { 0, 1, 2 };
	protected static int payoff[][] = { { 0, 0, 0 }, { 0, 0, 0 }, { 0, 0, 0 } };

	public static int getPayoff(int strategy1, int strategy2) {
		return payoff[strategy1][strategy2];
	}
}
