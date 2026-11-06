import java.util.ArrayList;
import java.util.List;

public class EightPuzzle implements SearchTree.Problem<String> {

    static final String[] MOVE_NAMES = {"Up", "Down", "Left", "Right"};
    static final int[] MOVE_DELTA = {-3, 3, -1, 1};

    final String start, goal;

    // creates the puzzle with a start and a goal board
    EightPuzzle(String start, String goal) {
        this.start = start;
        this.goal = goal;
    }

    // start board
    @Override
    public String initial() {
        return start;
    }

    // true if the board equals the goal
    @Override
    public boolean isGoal(String s) {
        return s.equals(goal);
    }

    // boards after moving the blank up, down, left or right
    @Override
    public List<SearchTree.Child<String>> expand(String s) {
        int b = s.indexOf('0');
        int r = b / 3, c = b % 3;
        List<SearchTree.Child<String>> out = new ArrayList<>();
        for (int m = 0; m < 4; m++) {
            String name = MOVE_NAMES[m];
            if ((name.equals("Up") && r == 0) || (name.equals("Down") && r == 2)
                    || (name.equals("Left") && c == 0) || (name.equals("Right") && c == 2)) {
                continue;
            }
            char[] t = s.toCharArray();
            int d = MOVE_DELTA[m];
            t[b] = t[b + d];
            t[b + d] = '0';
            out.add(new SearchTree.Child<>(name, new String(t), null));
        }
        return out;
    }

    // objective function f(S), tiles not in their goal place
    int misplaced(String s) {
        int k = 0;
        for (int i = 0; i < 9; i++) {
            if (s.charAt(i) != '0' && s.charAt(i) != goal.charAt(i)) k++;
        }
        return k;
    }

    // board as one line, like 283/164/7_5
    @Override
    public String label(String s) {
        String t = s.replace('0', '_');
        return t.substring(0, 3) + "/" + t.substring(3, 6) + "/" + t.substring(6, 9);
    }

    // board as three lines and f(S)
    @Override
    public List<String> lines(String s) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < 9; i += 3) {
            StringBuilder row = new StringBuilder();
            for (int j = i; j < i + 3; j++) {
                if (j > i) row.append(' ');
                char ch = s.charAt(j);
                row.append(ch == '0' ? '·' : ch);
            }
            out.add(row.toString());
        }
        out.add("f=" + misplaced(s));
        return out;
    }

    // reads a board, the digits 0-8 once each
    static String parse(String text) {
        StringBuilder digits = new StringBuilder();
        for (char ch : text.toCharArray()) if (Character.isDigit(ch)) digits.append(ch);
        char[] sorted = digits.toString().toCharArray();
        java.util.Arrays.sort(sorted);
        if (!new String(sorted).equals("012345678")) {
            throw new IllegalArgumentException("a board needs the digits 0-8 once each (0 = blank)");
        }
        return digits.toString();
    }

    // number of inversions, used to check if the goal is reachable
    static int inversions(String s) {
        String t = s.replace("0", "");
        int k = 0;
        for (int i = 0; i < t.length(); i++) {
            for (int j = i + 1; j < t.length(); j++) {
                if (t.charAt(i) > t.charAt(j)) k++;
            }
        }
        return k;
    }

    static final String USAGE = String.join("\n",
            "usage: java EightPuzzle [-h] [--start START] [--goal GOAL]",
            "                        [--strategy {bfs,dfs}] [--depth DEPTH]",
            "                        [--repeat {graph,path,none}] [--all]",
            "                        [--max-nodes MAX_NODES] [--quiet] [--out OUT]",
            "",
            "8-puzzle search tree",
            "",
            "options:",
            "  --start START                start board, 9 digits, 0 = blank (default 283164705)",
            "  --goal GOAL                  goal board (default 123804765)",
            SearchTree.Options.HELP.replace("--depth DEPTH                depth limit of the tree",
                                            "--depth DEPTH                depth limit of the tree (default 5)"));

    // reads the arguments and builds the search tree
    public static void main(String[] args) {
        SearchTree.useUtf8();
        String start = "283164705", goal = "123804765";
        SearchTree.Options opts = new SearchTree.Options(5, "graph");
        try {
            for (int i = 0; i < args.length; ) {
                switch (args[i]) {
                    case "-h", "--help" -> {
                        System.out.println(USAGE);
                        return;
                    }
                    case "--start" -> {
                        start = parse(SearchTree.value(args, i));
                        i += 2;
                    }
                    case "--goal" -> {
                        goal = parse(SearchTree.value(args, i));
                        i += 2;
                    }
                    default -> {
                        int next = opts.parse(args, i);
                        if (next < 0) throw new IllegalArgumentException("unrecognized argument: " + args[i]);
                        i = next;
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            System.err.println(USAGE);
            System.err.println("error: " + e.getMessage());
            System.exit(2);
        }

        EightPuzzle p = new EightPuzzle(start, goal);
        System.out.println("Start " + p.label(p.start) + "   goal " + p.label(p.goal) + "   "
                + "f(start) = " + p.misplaced(p.start) + " misplaced tiles");
        System.out.println("Landscape: 9! = 362880 boards, 181440 reachable");
        if (inversions(p.start) % 2 != inversions(p.goal) % 2) {
            System.out.println("The goal is NOT reachable from this start (different inversion parity).");
        }
        SearchTree.runWithBigStack(() -> SearchTree.run(p, opts, "eight_puzzle",
                "8-Puzzle search tree (" + opts.strategy.toUpperCase() + ")"));
    }
}
