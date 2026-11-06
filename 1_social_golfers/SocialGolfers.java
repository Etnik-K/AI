import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class SocialGolfers {

    static class StopSearch extends RuntimeException {
        // stops the search when the time or node limit is reached
        StopSearch() {
            super(null, null, false, false);
        }
    }

    final int g, p, w, n;
    final double timeLimit;
    final boolean forwardCheck;
    final Random rng;
    long nodeLimit = 0;
    long nodes = 0;
    long backtracks = 0;
    long start;
    final int[][] x;
    final long[] met;
    final long[][] members;
    final int[][] size;

    // creates an empty schedule for g groups of p players and w weeks
    SocialGolfers(int g, int p, int w, double timeLimit, boolean forwardCheck, Random rng) {
        this.g = g;
        this.p = p;
        this.w = w;
        this.n = g * p;
        this.timeLimit = timeLimit;
        this.forwardCheck = forwardCheck;
        this.rng = rng;
        this.x = new int[w][n];
        for (int[] row : x) java.util.Arrays.fill(row, -1);
        this.met = new long[n];
        this.members = new long[w][g];
        this.size = new int[w][g];
    }

    // puts player i in group grp of week k and updates who met whom
    long place(int k, int i, int grp) {
        long mask = members[k][grp];
        x[k][i] = grp;
        met[i] |= mask;
        for (long m = mask; m != 0; m &= m - 1) {
            met[Long.numberOfTrailingZeros(m)] |= 1L << i;
        }
        members[k][grp] |= 1L << i;
        size[k][grp]++;
        return mask;
    }

    // removes player i from group grp of week k
    void unplace(int k, int i, int grp, long mask) {
        x[k][i] = -1;
        members[k][grp] &= ~(1L << i);
        size[k][grp]--;
        met[i] &= ~mask;
        for (long m = mask; m != 0; m &= m - 1) {
            met[Long.numberOfTrailingZeros(m)] &= ~(1L << i);
        }
    }

    // seconds since the search started
    double elapsed() {
        return (System.nanoTime() - start) / 1e9;
    }

    // fixes week 0 and runs the DFS, returns the schedule or null
    List<List<List<Integer>>> solve() {
        start = System.nanoTime();
        for (int i = 0; i < n; i++) {
            place(0, i, i / p);
        }
        if (w == 1) {
            return schedule();
        }
        try {
            return dfs(1, 0, 0, true) ? schedule() : null;
        } catch (StopSearch e) {
            return null;
        }
    }

    // forward checking: every unassigned player of week k still has a possible group
    boolean futureOk(int k, int i, int opened) {
        if (opened < g) {
            return true;
        }
        for (int j = i; j < n; j++) {
            long mj = met[j];
            boolean possible = false;
            for (int grp = 0; grp < g && !possible; grp++) {
                possible = size[k][grp] < p && (mj & members[k][grp]) == 0;
            }
            if (!possible) {
                return false;
            }
        }
        return true;
    }

    // DFS with backtracking, assigns player i of week k
    boolean dfs(int k, int i, int opened, boolean tight) {
        if (i == n) {
            if (k + 1 == w) {
                return true;
            }
            return dfs(k + 1, 0, 0, true);
        }

        nodes++;
        if (nodeLimit > 0 && nodes > nodeLimit) {
            throw new StopSearch();
        }
        if (timeLimit > 0 && (nodes & 0xFFF) == 0 && elapsed() > timeLimit) {
            throw new StopSearch();
        }

        int prev = k >= 2 ? x[k - 1][i] : -1;
        int hi = Math.min(opened, g - 1);
        int[] order = new int[hi + 1];
        for (int j = 0; j <= hi; j++) {
            order[j] = j;
        }
        if (rng != null) {
            for (int j = order.length - 1; j > 0; j--) {
                int r = rng.nextInt(j + 1);
                int t = order[j];
                order[j] = order[r];
                order[r] = t;
            }
        }
        for (int grp : order) {
            if (tight && grp < prev) continue;
            if (size[k][grp] == p) continue;
            if ((met[i] & members[k][grp]) != 0) continue;
            int newOpened = grp == opened ? opened + 1 : opened;
            long mask = place(k, i, grp);
            if ((!forwardCheck || futureOk(k, i + 1, newOpened))
                    && dfs(k, i + 1, newOpened, tight && grp == prev)) {
                return true;
            }
            unplace(k, i, grp, mask);
        }
        backtracks++;
        return false;
    }

    // true if the search stopped because of the node limit
    boolean nodeLimitHit() {
        return nodeLimit > 0 && nodes > nodeLimit;
    }

    // builds the schedule as weeks -> groups -> players
    List<List<List<Integer>>> schedule() {
        List<List<List<Integer>>> weeks = new ArrayList<>();
        for (int k = 0; k < w; k++) {
            List<List<Integer>> groups = new ArrayList<>();
            for (int grp = 0; grp < g; grp++) {
                groups.add(new ArrayList<>());
            }
            for (int i = 0; i < n; i++) {
                groups.get(x[k][i]).add(i);
            }
            weeks.add(groups);
        }
        return weeks;
    }

    // objective function f(S), the number of repeated meetings
    static int objective(List<List<List<Integer>>> schedule, int n) {
        int[][] meet = new int[n][n];
        for (List<List<Integer>> week : schedule) {
            for (List<Integer> group : week) {
                for (int a = 0; a < group.size(); a++) {
                    for (int b = a + 1; b < group.size(); b++) {
                        meet[group.get(a)][group.get(b)]++;
                    }
                }
            }
        }
        int f = 0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                f += Math.max(0, meet[i][j] + meet[j][i] - 1);
            }
        }
        return f;
    }

    // checks group sizes, that every player plays each week and f(S) = 0
    static boolean isValid(List<List<List<Integer>>> schedule, int g, int p) {
        int n = g * p;
        for (List<List<Integer>> week : schedule) {
            if (week.size() != g) return false;
            boolean[] seen = new boolean[n];
            for (List<Integer> group : week) {
                if (group.size() != p) return false;
                for (int v : group) {
                    if (v < 0 || v >= n || seen[v]) return false;
                    seen[v] = true;
                }
            }
        }
        return objective(schedule, n) == 0;
    }

    // factorial of k
    static BigInteger fact(int k) {
        BigInteger r = BigInteger.ONE;
        for (int i = 2; i <= k; i++) r = r.multiply(BigInteger.valueOf(i));
        return r;
    }

    record Landscape(BigInteger raw, BigInteger perWeek, BigInteger partitions,
                     BigInteger reduced, int upperW) {}

    // computes the size of the search space
    static Landscape landscape(int g, int p, int w) {
        int n = g * p;
        BigInteger raw = BigInteger.valueOf(g).pow(n * w);
        BigInteger perWeek = fact(n).divide(fact(p).pow(g).multiply(fact(g)));
        BigInteger partitions = perWeek.pow(w);
        BigInteger reduced = w > 1 ? perWeek.pow(w - 1).divide(fact(w - 1)) : BigInteger.ONE;
        int pairs = n * (n - 1) / 2;
        int pairsPerWeek = g * p * (p - 1) / 2;
        int upperW = Math.min(pairs / pairsPerWeek, (n - 1) / (p - 1));
        return new Landscape(raw, perWeek, partitions, reduced, upperW);
    }

    // formats a big number like 1.23e45
    static String sci(BigInteger x) {
        if (x.compareTo(BigInteger.valueOf(1_000_000)) < 0) return x.toString();
        String s = x.toString();
        return s.charAt(0) + "." + s.substring(1, 3) + "e" + (s.length() - 1);
    }

    // prints the landscape
    static void printLandscape(int g, int p, int w) {
        Landscape l = landscape(g, p, w);
        System.out.println(fmt("Landscape for n=%d (g=%d, p=%d), w=%d:", g * p, g, p, w));
        System.out.println("  raw space g^(n*w)                     = " + sci(l.raw()));
        System.out.println("  ways to split one week into groups    = " + sci(l.perWeek()));
        System.out.println("  schedules made of valid weeks         = " + sci(l.partitions()));
        System.out.println("  after fixing week 0 and ordering weeks = " + sci(l.reduced()));
        System.out.println("  upper bound on weeks (n-1)/(p-1)       = " + l.upperW());
    }

    // joins the players of a group with commas
    static String join(List<Integer> group) {
        StringBuilder sb = new StringBuilder();
        for (int a = 0; a < group.size(); a++) {
            if (a > 0) sb.append(',');
            sb.append(group.get(a));
        }
        return sb.toString();
    }

    // pads a string with spaces on the right
    static String ljust(String s, int width) {
        return s.length() >= width ? s : s + " ".repeat(width - s.length());
    }

    // prints the schedule as a table
    static void printSchedule(List<List<List<Integer>>> schedule) {
        int g = schedule.get(0).size();
        StringBuilder header = new StringBuilder("Week  | ");
        for (int j = 0; j < g; j++) {
            if (j > 0) header.append(" | ");
            header.append(ljust("Group " + (j + 1), join(schedule.get(0).get(j)).length()));
        }
        System.out.println(header);
        for (int k = 0; k < schedule.size(); k++) {
            StringBuilder row = new StringBuilder(ljust(String.valueOf(k + 1), 5) + " | ");
            List<List<Integer>> week = schedule.get(k);
            for (int j = 0; j < week.size(); j++) {
                if (j > 0) row.append(" | ");
                String s = join(week.get(j));
                row.append(ljust(s, Math.max(("Group " + (j + 1)).length(), s.length())));
            }
            System.out.println(row);
        }
    }

    record Result(List<List<List<Integer>>> schedule, long nodes, long backtracks, double seconds) {}

    // solves one instance with plain DFS or DFS with restarts
    static Result runOne(int g, int p, int w, double timeLimit, boolean restarts, long seed) {
        long t0 = System.nanoTime();
        if (!restarts) {
            SocialGolfers solver = new SocialGolfers(g, p, w, timeLimit, true, null);
            List<List<List<Integer>>> sched = solver.solve();
            return new Result(sched, solver.nodes, solver.backtracks, (System.nanoTime() - t0) / 1e9);
        }
        Random rng = new Random(seed);
        long nodes = 0, backtracks = 0;
        long limit = 1000;
        while (true) {
            double left = timeLimit - (System.nanoTime() - t0) / 1e9;
            if (left <= 0) {
                return new Result(null, nodes, backtracks, (System.nanoTime() - t0) / 1e9);
            }
            SocialGolfers solver = new SocialGolfers(g, p, w, left, true, rng);
            solver.nodeLimit = limit;
            List<List<List<Integer>>> sched = solver.solve();
            nodes += solver.nodes;
            backtracks += solver.backtracks;
            if (sched != null) {
                return new Result(sched, nodes, backtracks, (System.nanoTime() - t0) / 1e9);
            }
            if (solver.nodes <= limit && !solver.nodeLimitHit()) {
                return new Result(null, nodes, backtracks, (System.nanoTime() - t0) / 1e9);
            }
            limit = (long) (limit * 1.5);
        }
    }

    // String.format with a dot as the decimal separator
    static String fmt(String format, Object... args) {
        return String.format(Locale.ROOT, format, args);
    }

    static final String USAGE = String.join("\n",
            "usage: java SocialGolfers [-h] [-g G] [-p P] [-w W] [--max] [-t TIME_LIMIT]",
            "                          [--restarts] [--seed SEED]",
            "",
            "Social Golfers Problem with DFS + backtracking",
            "",
            "options:",
            "  -g G                  number of groups (default 8)",
            "  -p P                  players per group (default 4)",
            "  -w W                  number of weeks",
            "  --max                 search for the max number of weeks",
            "  -t, --time-limit T    time limit in seconds per instance (default 60)",
            "  --restarts            randomised DFS with restarts instead of plain DFS",
            "  --seed SEED           random seed for --restarts (default 0)");

    // prints the usage and an error, then exits
    static void fail(String msg) {
        System.err.println(USAGE);
        System.err.println("error: " + msg);
        System.exit(2);
    }

    // returns the value after an option
    static String value(String[] a, int i) {
        if (i + 1 >= a.length) fail("argument " + a[i] + " needs a value");
        return a[i + 1];
    }

    // reads the arguments and starts the search
    public static void main(String[] args) throws InterruptedException {
        int g = 8, p = 4;
        Integer w = null;
        boolean max = false, restarts = false;
        double timeLimit = 60.0;
        long seed = 0;
        try {
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "-h", "--help" -> {
                        System.out.println(USAGE);
                        return;
                    }
                    case "-g" -> g = Integer.parseInt(value(args, i++));
                    case "-p" -> p = Integer.parseInt(value(args, i++));
                    case "-w" -> w = Integer.parseInt(value(args, i++));
                    case "--max" -> max = true;
                    case "-t", "--time-limit" -> timeLimit = Double.parseDouble(value(args, i++));
                    case "--restarts" -> restarts = true;
                    case "--seed" -> seed = Long.parseLong(value(args, i++));
                    default -> fail("unrecognized argument: " + args[i]);
                }
            }
        } catch (NumberFormatException e) {
            fail("invalid number: " + e.getMessage());
        }
        if (g < 1 || p < 2) fail("need g >= 1 and p >= 2");
        if (g * p > 64) fail("at most 64 golfers are supported (bitmasks are 64-bit)");
        if (w != null && w < 1) fail("w must be at least 1");

        final int fg = g, fp = p;
        final Integer fw = w;
        final boolean fmax = max, frestarts = restarts;
        final double ftime = timeLimit;
        final long fseed = seed;
        Thread t = new Thread(null, () -> run(fg, fp, fw, fmax, ftime, frestarts, fseed), "main", 1L << 28);
        t.start();
        t.join();
    }

    // solves one w, or finds the max number of weeks
    static void run(int g, int p, Integer w, boolean max, double timeLimit, boolean restarts, long seed) {
        if (w != null && !max) {
            printLandscape(g, p, w);
            Result r = runOne(g, p, w, timeLimit, restarts, seed);
            System.out.println();
            if (r.schedule() != null) {
                printSchedule(r.schedule());
                System.out.println(fmt("%nObjective f(S) = %d   valid = %s",
                        objective(r.schedule(), g * p), isValid(r.schedule(), g, p) ? "True" : "False"));
            } else {
                System.out.println("No schedule found (search space exhausted or time limit reached).");
            }
            System.out.println(fmt("Nodes: %d  backtracks: %d  time: %.3fs", r.nodes(), r.backtracks(), r.seconds()));
            return;
        }

        int upper = landscape(g, p, 2).upperW();
        System.out.println(fmt("n=%d golfers, g=%d, p=%d. Upper bound on weeks: %d%n", g * p, g, p, upper));
        List<List<List<Integer>>> best = null;
        for (int wk = 1; wk <= upper; wk++) {
            Result r = runOne(g, p, wk, timeLimit, restarts, seed);
            String status = r.schedule() != null ? "found" : (r.seconds() >= timeLimit ? "timeout" : "impossible");
            System.out.println(fmt("w=%2d: %-10s nodes=%-10d backtracks=%-10d time=%.3fs",
                    wk, status, r.nodes(), r.backtracks(), r.seconds()));
            if (r.schedule() == null) break;
            best = r.schedule();
        }
        if (best != null) {
            System.out.println(fmt("%nBest schedule found: %d weeks%n", best.size()));
            printSchedule(best);
            System.out.println(fmt("%nObjective f(S) = %d   valid = %s",
                    objective(best, g * p), isValid(best, g, p) ? "True" : "False"));
        }
    }
}
