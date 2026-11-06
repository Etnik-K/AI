import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MissionariesCannibals implements SearchTree.Problem<MissionariesCannibals.State> {

    public record State(int m, int c, char b) {}

    final int n, k;
    final List<int[]> loads = new ArrayList<>();

    // creates the problem and the list of boat loads
    MissionariesCannibals(int n, int k) {
        this.n = n;
        this.k = k;
        for (int m = 0; m <= k; m++) {
            for (int c = 0; c <= k - m; c++) {
                if (m + c >= 1) loads.add(new int[] {m, c});
            }
        }
        loads.sort(Comparator.<int[], Boolean>comparing(mc -> mc[0] > 0 && mc[1] > 0)
                .thenComparing(mc -> mc[0] == 0)
                .thenComparingInt(mc -> mc[0] + mc[1]));
    }

    // start state (N, N, L)
    @Override
    public State initial() {
        return new State(n, n, 'L');
    }

    // true for the goal (0, 0, R)
    @Override
    public boolean isGoal(State s) {
        return s.equals(new State(0, 0, 'R'));
    }

    // the broken constraint, or null if the state is safe
    String problemWith(State s) {
        int m = s.m(), c = s.c();
        if (0 < m && m < c) return "C>M left";
        if (0 < n - m && n - m < n - c) return "C>M right";
        return null;
    }

    // states after every possible boat crossing
    @Override
    public List<SearchTree.Child<State>> expand(State s) {
        int sign = s.b() == 'L' ? -1 : 1;
        List<SearchTree.Child<State>> out = new ArrayList<>();
        for (int[] load : loads) {
            int dm = load[0], dc = load[1];
            int nm = s.m() + sign * dm, nc = s.c() + sign * dc;
            if (nm < 0 || nm > n || nc < 0 || nc > n) {
                continue;
            }
            State child = new State(nm, nc, s.b() == 'L' ? 'R' : 'L');
            String name = (dm > 0 ? dm + "M" : "") + (dc > 0 ? dc + "C" : "");
            name = s.b() == 'L' ? name + "→" : "←" + name;
            out.add(new SearchTree.Child<>(name, child, problemWith(child)));
        }
        return out;
    }

    // state as (M,C,B)
    @Override
    public String label(State s) {
        return "(" + s.m() + "," + s.c() + "," + s.b() + ")";
    }

    // state and the people on each bank
    @Override
    public List<String> lines(State s) {
        String left = "M".repeat(s.m()) + "C".repeat(s.c());
        String right = "M".repeat(n - s.m()) + "C".repeat(n - s.c());
        String boat = s.b() == 'L' ? "<b" : "b>";
        return List.of(label(s), (left.isEmpty() ? "-" : left) + " " + boat + " " + (right.isEmpty() ? "-" : right));
    }

    static final String USAGE = String.join("\n",
            "usage: java MissionariesCannibals [-h] [-n N] [-k K] [--strategy {bfs,dfs}]",
            "                                  [--depth DEPTH] [--repeat {graph,path,none}]",
            "                                  [--all] [--max-nodes MAX_NODES] [--quiet]",
            "                                  [--out OUT]",
            "",
            "Missionaries and cannibals search tree",
            "",
            "options:",
            "  -n N                         missionaries = cannibals = N (default 3)",
            "  -k K                         boat capacity (default 2)",
            SearchTree.Options.HELP.replace("--depth DEPTH                depth limit of the tree",
                                            "--depth DEPTH                depth limit of the tree (default 30)"));

    // reads the arguments and builds the search tree
    public static void main(String[] args) {
        SearchTree.useUtf8();
        int n = 3, k = 2;
        SearchTree.Options opts = new SearchTree.Options(30, "graph");
        try {
            for (int i = 0; i < args.length; ) {
                switch (args[i]) {
                    case "-h", "--help" -> {
                        System.out.println(USAGE);
                        return;
                    }
                    case "-n" -> {
                        n = SearchTree.intValue(args, i);
                        i += 2;
                    }
                    case "-k" -> {
                        k = SearchTree.intValue(args, i);
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

        MissionariesCannibals p = new MissionariesCannibals(n, k);
        int states = 0, safe = 0;
        for (int m = 0; m <= n; m++) {
            for (int c = 0; c <= n; c++) {
                for (char b : new char[] {'L', 'R'}) {
                    states++;
                    if (p.problemWith(new State(m, c, b)) == null) safe++;
                }
            }
        }
        System.out.println("N=" + n + ", boat capacity=" + k);
        System.out.println("Landscape: " + states + " states, " + safe + " safe");
        System.out.println("State (M,C,B): M, C on the left bank, B = boat side");
        SearchTree.runWithBigStack(() -> SearchTree.run(p, opts, "missionaries_cannibals",
                "Missionaries and Cannibals search tree (" + opts.strategy.toUpperCase() + ")"));
    }
}
