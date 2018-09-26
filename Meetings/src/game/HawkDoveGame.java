package game;

public class HawkDoveGame extends Game {
	public static int HAWK = 0;
	public static int DOVE = 1;
	protected int strategy[] = { HAWK, DOVE };
	protected int payoff[][] = { { -1, 1 }, { -1, 1 }, };
}
