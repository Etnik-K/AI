import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class LatinSquare {

    static final Map<Integer, BigInteger> LATIN_COUNT = Map.of(
            1, new BigInteger("1"), 2, new BigInteger("2"), 3, new BigInteger("12"),
            4, new BigInteger("576"), 5, new BigInteger("161280"), 6, new BigInteger("812851200"),
            7, new BigInteger("61479419904000"), 8, new BigInteger("108776032459082956800"));
    static final Map<Integer, BigInteger> REDUCED_COUNT = Map.of(
            1, new BigInteger("1"), 2, new BigInteger("1"), 3, new BigInteger("1"),
            4, new BigInteger("4"), 5, new BigInteger("56"), 6, new BigInteger("9408"),
            7, new BigInteger("16942080"), 8, new BigInteger("535281401856"));

    static final int FALSE = 0, TRUE = 1, CUTOFF = 2;

    final int n;
    final String order;
    final long full;
    final int[] grid;
    final long[] rowUsed, colUsed;
    final int[] free;
    long nodes = 0;
    long backtracks = 0;
    List<long[]> iterations = new ArrayList<>();

    // creates an empty n x n square, fixes row 1 and column 1 if symmetry is on
    LatinSquare(int n, boolean symmetry, String order) {
        this.n = n;
        this.order = order;
        this.full = (1L << (n + 1)) - 2;
        this.grid = new int[n * n];
        this.rowUsed = new long[n];
        this.colUsed = new long[n];
        if (symmetry) {
            for (int c = 0; c < n; c++) set(c, c + 1);
            for (int r = 1; r < n; r++) set(r * n, r + 1);
        }
        int count = 0;
        for (int v : grid) if (v == 0) count++;
        free = new int[count];
        int k = 0;
        for (int i = 0; i < n * n; i++) if (grid[i] == 0) free[k++] = i;
    }

    // puts value v in cell i
    void set(int i, int v) {
        grid[i] = v;
        rowUsed[i / n] |= 1L << v;
        colUsed[i % n] |= 1L << v;
    }

    // clears cell i
    void unset(int i, int v) {
        grid[i] = 0;
        rowUsed[i / n] &= ~(1L << v);
        colUsed[i % n] &= ~(1L << v);
    }

    // values not used yet in the row and column of cell i
    int[] values(int i) {
        long allowed = full & ~(rowUsed[i / n] | colUsed[i % n]);
        int[] vals = new int[Long.bitCount(allowed)];
        int k = 0;
        for (int v = 1; v <= n; v++) if ((allowed >> v & 1) != 0) vals[k++] = v;
        return vals;
    }

    record Choice(int cell, int[] values) {}

    // picks the next cell to fill, cell -1 means a dead end
    Choice nextCell(int depth) {
        if (order.equals("rowmajor")) {
            int i = free[depth];
            return new Choice(i, values(i));
        }
        int best = -1, bestCnt = n + 1;
        for (int i : free) {
            if (grid[i] == 0) {
                int cnt = Long.bitCount(full & ~(rowUsed[i / n] | colUsed[i % n]));
                if (cnt < bestCnt) {
                    best = i;
                    bestCnt = cnt;
                    if (cnt <= 1) break;
                }
            }
        }
        if (bestCnt == 0) return new Choice(-1, new int[0]);
        return new Choice(best, values(best));
    }

    // depth-limited DFS, returns TRUE, FALSE or CUTOFF
    int dls(int depth, int limit) {
        nodes++;
        if (depth == free.length) return TRUE;
        if (depth == limit) return CUTOFF;
        Choice ch = nextCell(depth);
        boolean cutoff = false;
        for (int v : ch.values()) {
            set(ch.cell(), v);
            int res = dls(depth + 1, limit);
            if (res == TRUE) return TRUE;
            if (res == CUTOFF) cutoff = true;
            unset(ch.cell(), v);
        }
        return cutoff ? CUTOFF : FALSE;
    }

    // IDDFS: runs the depth-limited DFS with limit 0, 1, 2, ...
    int[][] iddfs(boolean verbose) {
        iterations = new ArrayList<>();
        for (int limit = 0; limit <= free.length; limit++) {
            long before = nodes;
            int res = dls(0, limit);
            iterations.add(new long[] {limit, nodes - before});
            if (verbose) System.out.printf(Locale.ROOT, "  limit=%3d  nodes=%d%n", limit, nodes - before);
            if (res == TRUE) return square();
            if (res == FALSE) return null;
        }
        return null;
    }

    // solves the square with backtracking
    int[][] backtracking() {
        backtracks = 0;
        return rec(0) ? square() : null;
    }

    // recursive step of the backtracking
    boolean rec(int depth) {
        nodes++;
        if (depth == free.length) return true;
        Choice ch = nextCell(depth);
        for (int v : ch.values()) {
            set(ch.cell(), v);
            if (rec(depth + 1)) return true;
            unset(ch.cell(), v);
        }
        backtracks++;
        return false;
    }

    // the grid as an n x n array
    int[][] square() {
        int[][] sq = new int[n][n];
        for (int r = 0; r < n; r++) System.arraycopy(grid, r * n, sq[r], 0, n);
        return sq;
    }

    // objective function f(S)
    static int objective(int[][] square) {
        int n = square.length;
        int f = 0;
        for (int[] row : square) for (int v : row) if (v == 0) f++;
        for (int r = 0; r < n; r++) {
            boolean[] seen = new boolean[n + 1];
            int distinct = 0;
            for (int c = 0; c < n; c++) {
                int v = square[r][c];
                if (v != 0 && !seen[v]) {
                    seen[v] = true;
                    distinct++;
                }
            }
            f += n - distinct;
        }
        for (int c = 0; c < n; c++) {
            boolean[] seen = new boolean[n + 1];
            int distinct = 0;
            for (int r = 0; r < n; r++) {
                int v = square[r][c];
                if (v != 0 && !seen[v]) {
                    seen[v] = true;
                    distinct++;
                }
            }
            f += n - distinct;
        }
        return f;
    }

    // checks that every row and column has 1..n once
    static boolean isLatin(int[][] square) {
        int n = square.length;
        for (int r = 0; r < n; r++) {
            boolean[] rowSeen = new boolean[n + 1], colSeen = new boolean[n + 1];
            for (int c = 0; c < n; c++) {
                int a = square[r][c], b = square[c][r];
                if (a < 1 || a > n || rowSeen[a] || b < 1 || b > n || colSeen[b]) return false;
                rowSeen[a] = true;
                colSeen[b] = true;
            }
        }
        return true;
    }

    // formats a big number like 1.23e45
    static String sci(BigInteger x) {
        if (x.compareTo(BigInteger.valueOf(1_000_000)) < 0) return x.toString();
        String s = x.toString();
        return s.charAt(0) + "." + s.substring(1, 3) + "e" + (s.length() - 1);
    }

    // factorial of k
    static BigInteger fact(int k) {
        BigInteger r = BigInteger.ONE;
        for (int i = 2; i <= k; i++) r = r.multiply(BigInteger.valueOf(i));
        return r;
    }

    // prints the size of the search space
    static void printLandscape(int n, boolean symmetry) {
        BigInteger bn = BigInteger.valueOf(n);
        System.out.println("Landscape for n=" + n + ":");
        System.out.println("  raw space n^(n*n)                    = " + sci(bn.pow(n * n)));
        System.out.println("  rows are permutations (n!)^n          = " + sci(fact(n).pow(n)));
        if (LATIN_COUNT.containsKey(n)) {
            System.out.println("  Latin squares (solutions) L(n)        = " + sci(LATIN_COUNT.get(n)));
            System.out.println("  reduced Latin squares R(n)            = " + sci(REDUCED_COUNT.get(n)));
        }
        int free = symmetry ? (n - 1) * (n - 1) : n * n;
        System.out.println("  free cells for the search / max depth = " + free);
        if (symmetry) {
            System.out.println("  raw space with first row/col fixed    = " + sci(bn.pow(free)));
        }
    }

    // prints the square
    static void printSquare(int[][] square) {
        int w = String.valueOf(square.length).length();
        for (int[] row : square) {
            StringBuilder sb = new StringBuilder();
            for (int c = 0; c < row.length; c++) {
                if (c > 0) sb.append(' ');
                sb.append(String.format("%" + w + "d", row[c]));
            }
            System.out.println(sb);
        }
    }

    static final String USAGE = String.join("\n",
            "usage: java LatinSquare [-h] [--method {iddfs,backtracking,both}]",
            "                        [--no-symmetry] [--order {mrv,rowmajor}] [--verbose]",
            "                        [n]",
            "",
            "Latin square with IDDFS and backtracking",
            "",
            "positional arguments:",
            "  n                 size of the square (default 5)",
            "",
            "options:",
            "  --method METHOD   iddfs, backtracking or both (default both)",
            "  --no-symmetry     do not fix the first row and column",
            "  --order ORDER     cell order: mrv = fewest values first (default) or rowmajor",
            "  --verbose         print each IDDFS iteration");

    // prints the usage and an error, then exits
    static void fail(String msg) {
        System.err.println(USAGE);
        System.err.println("error: " + msg);
        System.exit(2);
    }

    // returns the value after an option and checks it
    static String value(String[] a, int i, List<String> choices) {
        if (i + 1 >= a.length) fail("argument " + a[i] + " needs a value");
        String v = a[i + 1];
        if (!choices.contains(v)) fail("argument " + a[i] + ": invalid choice '" + v + "'");
        return v;
    }

    // reads the arguments and starts the search
    public static void main(String[] args) throws InterruptedException {
        int n = 5;
        String method = "both", order = "mrv";
        boolean symmetry = true, verbose = false, gotN = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-h", "--help" -> {
                    System.out.println(USAGE);
                    return;
                }
                case "--method" -> method = value(args, i++, List.of("iddfs", "backtracking", "both"));
                case "--no-symmetry" -> symmetry = false;
                case "--order" -> order = value(args, i++, List.of("mrv", "rowmajor"));
                case "--verbose" -> verbose = true;
                default -> {
                    if (gotN) fail("unrecognized argument: " + args[i]);
                    try {
                        n = Integer.parseInt(args[i]);
                    } catch (NumberFormatException e) {
                        fail("argument n: invalid int value: '" + args[i] + "'");
                    }
                    gotN = true;
                }
            }
        }
        if (n < 1) fail("n must be at least 1");
        if (n > 62) fail("n must be at most 62 (bitmasks are 64-bit)");

        final int fn = n;
        final String fmethod = method, forder = order;
        final boolean fsym = symmetry, fverbose = verbose;
        Thread t = new Thread(null, () -> run(fn, fmethod, forder, fsym, fverbose), "main", 1L << 28);
        t.start();
        t.join();
    }

    // runs IDDFS and/or backtracking and prints the results
    static void run(int n, String method, String order, boolean sym, boolean verbose) {
        printLandscape(n, sym);
        List<String> methods = method.equals("both") ? List.of("iddfs", "backtracking") : List.of(method);
        for (String m : methods) {
            LatinSquare solver = new LatinSquare(n, sym, order);
            long t0 = System.nanoTime();
            int[][] sq = m.equals("iddfs") ? solver.iddfs(verbose) : solver.backtracking();
            double dt = (System.nanoTime() - t0) / 1e9;
            System.out.println("\n--- " + m.toUpperCase(Locale.ROOT) + " ---");
            if (sq == null) {
                System.out.println("No Latin square found.");
            } else {
                printSquare(sq);
                System.out.println("f(S) = " + objective(sq) + "   valid = " + (isLatin(sq) ? "True" : "False"));
            }
            String extra = m.equals("iddfs") ? " iterations=" + solver.iterations.size()
                                             : " backtracks=" + solver.backtracks;
            System.out.printf(Locale.ROOT, "nodes=%d%s time=%.3fs%n", solver.nodes, extra, dt);
        }
    }
}
