import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

public class RotatingWorkforce implements SearchTree.Problem<String> {

    static final String[] DAYS = {"Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"};
    static final char OFF = '-';
    static final String[] FORBIDDEN = {"ND", "NA", "AD"};

    final int n, total;
    final List<Character> shifts;
    final Map<Character, int[]> demand;
    final List<Character> values;
    final TreeSet<String> forbidden = new TreeSet<>();
    final int[] workLimits, offLimits;

    // creates the problem from the employees, demand and block limits
    RotatingWorkforce(int n, Map<Character, int[]> shiftDemand, int[] work, int[] off) {
        this.n = n;
        this.total = 7 * n;
        this.shifts = new ArrayList<>(shiftDemand.keySet());
        this.demand = new LinkedHashMap<>(shiftDemand);
        int[] offDemand = new int[7];
        for (int d = 0; d < 7; d++) {
            int sum = 0;
            for (char s : shifts) sum += shiftDemand.get(s)[d];
            offDemand[d] = n - sum;
            if (offDemand[d] < 0) {
                throw new IllegalArgumentException("demand on some day is larger than the number of employees");
            }
        }
        this.demand.put(OFF, offDemand);
        this.values = new ArrayList<>(shifts);
        this.values.add(OFF);
        for (String f : FORBIDDEN) {
            if (shiftDemand.containsKey(f.charAt(0)) && shiftDemand.containsKey(f.charAt(1))) forbidden.add(f);
        }
        this.workLimits = work;
        this.offLimits = off;
    }

    // min and max length of a work or off block
    int[] limits(boolean work) {
        return work ? workLimits : offLimits;
    }

    // true if shift b can't follow shift a
    boolean isForbidden(char a, char b) {
        return forbidden.contains("" + a + b);
    }

    // empty schedule
    @Override
    public String initial() {
        return "";
    }

    // true if the schedule is full and f(S) = 0
    @Override
    public boolean isGoal(String s) {
        return s.length() == total && objective(s) == 0;
    }

    // schedules after giving the next cell each value
    @Override
    public List<SearchTree.Child<String>> expand(String s) {
        int k = s.length();
        List<SearchTree.Child<String>> out = new ArrayList<>();
        if (k == total) return out;
        for (char v : values) {
            String name = DAYS[k % 7] + (k / 7 + 1) + "=" + v;
            out.add(new SearchTree.Child<>(name, s + v, check(s, v)));
        }
        return out;
    }

    // how many times value v is on day d
    static int countOnDay(String s, int d, char v) {
        int c = 0;
        for (int j = d; j < s.length(); j += 7) if (s.charAt(j) == v) c++;
        return c;
    }

    // the broken constraint if v goes in the next cell, or null
    String check(String s, char v) {
        int k = s.length(), d = k % 7;
        if (countOnDay(s, d, v) + 1 > demand.get(v)[d]) {
            return "too many " + v + " on " + DAYS[d];
        }
        if (k > 0 && isForbidden(s.charAt(k - 1), v)) {
            return s.charAt(k - 1) + "→" + v + " forbidden";
        }
        boolean work = v != OFF;
        int runLen = 0;
        while (runLen < k && (s.charAt(k - 1 - runLen) != OFF) == work) runLen++;
        int hi = limits(work)[1];
        if (runLen + 1 > hi) {
            return (work ? "work" : "off") + " block > " + hi;
        }
        if (k > 0 && runLen == 0) {
            int prev = 0;
            while (prev < k && (s.charAt(k - 1 - prev) != OFF) != work) prev++;
            int plo = limits(!work)[0];
            if (prev < k && prev < plo) {
                return (work ? "off" : "work") + " block < " + plo;
            }
        }
        if (k == total - 1 && violations(s + v) > 0) {
            return "wrap-around rule broken";
        }
        return null;
    }

    // forbidden successions and wrong block lengths of a full schedule
    int violations(String s) {
        int len = s.length();
        int bad = 0;
        for (int i = 0; i < len; i++) {
            if (isForbidden(s.charAt(i), s.charAt((i + 1) % len))) bad++;
        }
        boolean[] kinds = new boolean[len];
        boolean anyWork = false, allWork = true;
        for (int i = 0; i < len; i++) {
            kinds[i] = s.charAt(i) != OFF;
            anyWork |= kinds[i];
            allWork &= kinds[i];
        }
        if (allWork || !anyWork) return bad + 1;
        int start = 0;
        while (kinds[start] == kinds[(start - 1 + len) % len]) start++;
        int i = 0;
        while (i < len) {
            boolean kind = kinds[(start + i) % len];
            int length = 0;
            while (i < len && kinds[(start + i) % len] == kind) {
                length++;
                i++;
            }
            int[] lim = limits(kind);
            if (length < lim[0] || length > lim[1]) bad++;
        }
        return bad;
    }

    // objective function f(S)
    int objective(String s) {
        int cover = 0;
        for (char v : shifts) {
            for (int d = 0; d < 7; d++) cover += Math.abs(countOnDay(s, d, v) - demand.get(v)[d]);
        }
        return cover + violations(s);
    }

    // schedule as one line, weeks split with |
    @Override
    public String label(String s) {
        if (s.isEmpty()) return "(empty)";
        List<String> weeks = new ArrayList<>();
        for (int i = 0; i < s.length(); i += 7) weeks.add(s.substring(i, Math.min(i + 7, s.length())));
        return String.join("|", weeks);
    }

    // schedule as one line per week
    @Override
    public List<String> lines(String s) {
        if (s.isEmpty()) return List.of("(empty)");
        List<String> out = new ArrayList<>();
        int rows = (s.length() + 6) / 7;
        for (int r = 0; r < rows; r++) {
            String week = s.substring(r * 7, Math.min((r + 1) * 7, s.length()));
            out.add("W" + (r + 1) + " " + week + ".".repeat(7 - week.length()));
        }
        return out;
    }

    // factorial of k
    static BigInteger fact(int k) {
        BigInteger r = BigInteger.ONE;
        for (int i = 2; i <= k; i++) r = r.multiply(BigInteger.valueOf(i));
        return r;
    }

    // size of the search space and schedules that meet the demand
    BigInteger[] landscape() {
        BigInteger raw = BigInteger.valueOf(values.size()).pow(total);
        BigInteger perDay = BigInteger.ONE;
        for (int d = 0; d < 7; d++) {
            BigInteger m = fact(n);
            for (char v : values) m = m.divide(fact(demand.get(v)[d]));
            perDay = perDay.multiply(m);
        }
        return new BigInteger[] {raw, perDay};
    }

    static class NodeLimit extends RuntimeException {
        // thrown when the search reaches the node limit
        NodeLimit() {
            super(null, null, false, false);
        }
    }

    record SolveResult(String sol, Boolean found, long nodes, long dead) {}

    // DFS with backtracking on the full tree
    SolveResult solve(long nodeLimit) {
        StringBuilder s = new StringBuilder();
        long[] stats = new long[2];
        Boolean found;
        try {
            found = solveRec(s, nodeLimit, stats);
        } catch (NodeLimit e) {
            found = null;
        }
        return new SolveResult(Boolean.TRUE.equals(found) ? s.toString() : null, found, stats[0], stats[1]);
    }

    // recursive step of the DFS
    private boolean solveRec(StringBuilder s, long nodeLimit, long[] stats) {
        stats[0]++;
        if (stats[0] > nodeLimit) throw new NodeLimit();
        if (s.length() == total) return true;
        String cur = s.toString();
        for (char v : values) {
            if (check(cur, v) != null) {
                stats[1]++;
                continue;
            }
            s.append(v);
            if (solveRec(s, nodeLimit, stats)) return true;
            s.setLength(s.length() - 1);
        }
        return false;
    }

    // prints the schedule and the count of each shift per day
    void printSchedule(String s) {
        System.out.println("        " + String.join("  ", DAYS));
        for (int r = 0; r < n; r++) {
            List<String> cells = new ArrayList<>();
            for (int d = 0; d < 7; d++) cells.add(String.valueOf(s.charAt(r * 7 + d)));
            System.out.println(String.format(Locale.ROOT, "Week %-2d ", r + 1) + String.join("   ", cells));
        }
        for (char v : shifts) {
            List<String> counts = new ArrayList<>();
            for (int d = 0; d < 7; d++) counts.add(String.valueOf(countOnDay(s, d, v)));
            System.out.println(String.format(Locale.ROOT, "%6s  ", v) + String.join("   ", counts));
        }
    }

    // reads the demand, like D=1 or D=1,1,1,1,1,2,2
    static Map<Character, int[]> parseDemand(List<String> items) {
        Map<Character, int[]> demand = new LinkedHashMap<>();
        for (String item : items) {
            int eq = item.indexOf('=');
            String name = eq < 0 ? item : item.substring(0, eq);
            String val = eq < 0 ? "" : item.substring(eq + 1);
            int[] nums;
            try {
                nums = Arrays.stream(val.split(",")).map(String::trim).mapToInt(Integer::parseInt).toArray();
            } catch (NumberFormatException e) {
                nums = new int[0];
            }
            if (name.length() != 1 || name.charAt(0) == OFF || (nums.length != 1 && nums.length != 7)) {
                throw new IllegalArgumentException("bad --demand item '" + item + "'; use e.g. D=1 or D=1,1,1,1,1,2,2");
            }
            if (nums.length == 1) {
                int x = nums[0];
                nums = new int[] {x, x, x, x, x, x, x};
            }
            demand.put(name.charAt(0), nums);
        }
        return demand;
    }

    static final String USAGE = String.join("\n",
            "usage: java RotatingWorkforce [-h] [--employees EMPLOYEES]",
            "                              [--demand DEMAND [DEMAND ...]] [--work MIN MAX]",
            "                              [--off MIN MAX] [--node-limit NODE_LIMIT]",
            "                              [--strategy {bfs,dfs}] [--depth DEPTH]",
            "                              [--repeat {graph,path,none}] [--all]",
            "                              [--max-nodes MAX_NODES] [--quiet] [--out OUT]",
            "",
            "Rotating workforce scheduling search tree",
            "",
            "options:",
            "  --employees EMPLOYEES        number of employees = weeks in the cycle (default 4)",
            "  --demand DEMAND [DEMAND ...] per shift: S=k (every day) or S=k1,...,k7 (default D=1 N=1)",
            "  --work MIN MAX               allowed length of a block of work days (default 2 4)",
            "  --off MIN MAX                allowed length of a block of days off (default 1 3)",
            "  --node-limit NODE_LIMIT      node limit for the full DFS solve (default 5000000)",
            SearchTree.Options.HELP.replace("--depth DEPTH                depth limit of the tree",
                                            "--depth DEPTH                depth limit of the tree (default 4)")
                    .replace("--repeat {graph,path,none}   repeated state check",
                             "--repeat {graph,path,none}   repeated state check (default none)"));

    // list of values as text, like ['D', 'N', '-']
    static String pyList(List<Character> xs) {
        List<String> parts = new ArrayList<>();
        for (char c : xs) parts.add("'" + c + "'");
        return "[" + String.join(", ", parts) + "]";
    }

    // reads the arguments, builds the tree and solves the schedule
    public static void main(String[] args) {
        SearchTree.useUtf8();
        int employees = 4;
        List<String> demandItems = List.of("D=1", "N=1");
        int[] work = {2, 4}, off = {1, 3};
        long nodeLimit = 5_000_000;
        SearchTree.Options opts = new SearchTree.Options(4, "none");
        RotatingWorkforce problem = null;
        try {
            for (int i = 0; i < args.length; ) {
                switch (args[i]) {
                    case "-h", "--help" -> {
                        System.out.println(USAGE);
                        return;
                    }
                    case "--employees" -> {
                        employees = SearchTree.intValue(args, i);
                        i += 2;
                    }
                    case "--demand" -> {
                        List<String> items = new ArrayList<>();
                        int j = i + 1;
                        while (j < args.length && !args[j].startsWith("--")) items.add(args[j++]);
                        if (items.isEmpty()) throw new IllegalArgumentException("argument --demand needs a value");
                        demandItems = items;
                        i = j;
                    }
                    case "--work", "--off" -> {
                        int[] pair = {SearchTree.intValue(args, i), SearchTree.intValue(args, i + 1)};
                        if (args[i].equals("--work")) work = pair;
                        else off = pair;
                        i += 3;
                    }
                    case "--node-limit" -> {
                        nodeLimit = SearchTree.intValue(args, i);
                        i += 2;
                    }
                    default -> {
                        int next = opts.parse(args, i);
                        if (next < 0) throw new IllegalArgumentException("unrecognized argument: " + args[i]);
                        i = next;
                    }
                }
            }
            problem = new RotatingWorkforce(employees, parseDemand(demandItems), work, off);
        } catch (IllegalArgumentException e) {
            System.err.println(USAGE);
            System.err.println("error: " + e.getMessage());
            System.exit(2);
        }

        RotatingWorkforce p = problem;
        BigInteger[] land = p.landscape();
        System.out.println(p.n + " employees, cycle of " + p.n + " weeks = " + p.total + " cells, values "
                + pyList(p.values));
        List<String> demandText = new ArrayList<>();
        for (char v : p.shifts) demandText.add(v + "=" + Arrays.toString(p.demand.get(v)));
        System.out.println("Demand per day: " + String.join(", ", demandText));
        List<String> forb = new ArrayList<>();
        for (String f : p.forbidden) forb.add("('" + f.charAt(0) + "', '" + f.charAt(1) + "')");
        System.out.println("Forbidden: " + (forb.isEmpty() ? "none" : "[" + String.join(", ", forb) + "]")
                + "   work block (" + work[0] + ", " + work[1] + "), off block (" + off[0] + ", " + off[1] + ")");
        System.out.println(String.format(Locale.ROOT, "Landscape: raw %,d schedules, %,d meet the daily demand",
                land[0], land[1]));

        final long limit = nodeLimit;
        SearchTree.runWithBigStack(() -> {
            SearchTree.run(p, opts, "rotating_workforce",
                    "Rotating Workforce Scheduling search tree, first " + opts.depth + " levels");

            System.out.println("\nFull DFS backtracking search (same tree, all " + p.total + " levels):");
            long t0 = System.nanoTime();
            SolveResult r = p.solve(limit);
            System.out.println(String.format(Locale.ROOT, "  nodes=%d dead ends cut=%d time=%.2fs",
                    r.nodes(), r.dead(), (System.nanoTime() - t0) / 1e9));
            if (r.sol() != null) {
                p.printSchedule(r.sol());
                System.out.println("  f(S) = " + p.objective(r.sol()));
            } else if (r.found() == null) {
                System.out.println("  stopped at the node limit (" + limit + ")");
            } else {
                System.out.println("  no schedule exists for this instance");
            }
        });
    }
}
