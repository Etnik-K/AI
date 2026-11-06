import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class SudokuBFS {

    static final String[][] PUZZLES = {
        {"easy", "53..7....6..195....98....6.8...6...34..8.3..17...2...6.6....28....419..5....8..79"},
        {"medium", "..9748...7.........2.1.9.....7...24..64.1.59..98...3.....8.3.2.........6...2759.."},
        {"hard", "8..........36......7..9.2...5...7.......457.....1...3...1....68..85...1..9....4.."},
    };

    static final int ALL = 0x3FE;
    static final int[] ROW = new int[81], COL = new int[81], BOX = new int[81];
    static final int[][] UNITS = new int[27][9];

    static {
        for (int i = 0; i < 81; i++) {
            ROW[i] = i / 9;
            COL[i] = i % 9;
            BOX[i] = (i / 27) * 3 + (i % 9) / 3;
        }
        for (int r = 0; r < 9; r++)
            for (int c = 0; c < 9; c++) UNITS[r][c] = r * 9 + c;
        for (int c = 0; c < 9; c++)
            for (int r = 0; r < 9; r++) UNITS[9 + c][r] = r * 9 + c;
        for (int b = 0; b < 9; b++)
            for (int k = 0; k < 9; k++) UNITS[18 + b][k] = (b / 3) * 27 + (b % 3) * 3 + (k / 3) * 9 + k % 3;
    }

    // reads a puzzle of 81 cells, '.' or '0' is empty
    static byte[] parse(String text) {
        byte[] grid = new byte[81];
        int count = 0;
        for (char ch : text.toCharArray()) {
            if ("0123456789.".indexOf(ch) >= 0) {
                if (count < 81) grid[count] = (byte) (ch == '.' ? 0 : ch - '0');
                count++;
            }
        }
        if (count != 81) throw new IllegalArgumentException("a puzzle needs 81 cells, got " + count);
        return grid;
    }

    // bitmasks of the values used in every row, column and box
    static int[][] usedMasks(byte[] grid) {
        int[] rows = new int[9], cols = new int[9], boxes = new int[9];
        for (int i = 0; i < 81; i++) {
            if (grid[i] != 0) {
                int b = 1 << grid[i];
                rows[ROW[i]] |= b;
                cols[COL[i]] |= b;
                boxes[BOX[i]] |= b;
            }
        }
        return new int[][] {rows, cols, boxes};
    }

    // values that can go in cell i
    static int[] candidates(int i, int[][] masks) {
        int free = ALL & ~(masks[0][ROW[i]] | masks[1][COL[i]] | masks[2][BOX[i]]);
        int[] vals = new int[Integer.bitCount(free)];
        int k = 0;
        for (int v = 1; v <= 9; v++) {
            if ((free >> v & 1) != 0) vals[k++] = v;
        }
        return vals;
    }

    // number of empty cells
    static int countEmpty(byte[] grid) {
        int e = 0;
        for (byte v : grid) if (v == 0) e++;
        return e;
    }

    // objective function f(S) = empty cells + conflicting pairs
    static int objective(byte[] grid) {
        int conflicts = 0;
        for (int[] unit : UNITS) {
            int[] count = new int[10];
            for (int i : unit) if (grid[i] != 0) count[grid[i]]++;
            for (int v = 1; v <= 9; v++) conflicts += count[v] * (count[v] - 1) / 2;
        }
        return countEmpty(grid) + conflicts;
    }

    // true if the grid has no conflicts
    static boolean isConsistent(byte[] grid) {
        return objective(grid) == countEmpty(grid);
    }

    record Choice(int cell, int[] values) {}

    // picks the next cell to fill and its possible values
    static Choice chooseCell(byte[] grid, int[][] masks, String order) {
        if (order.equals("first")) {
            int i = 0;
            while (grid[i] != 0) i++;
            return new Choice(i, candidates(i, masks));
        }
        int best = -1;
        int[] bestVals = null;
        for (int i = 0; i < 81; i++) {
            if (grid[i] == 0) {
                int[] vals = candidates(i, masks);
                if (best < 0 || vals.length < bestVals.length) {
                    best = i;
                    bestVals = vals;
                    if (vals.length <= 1) break;
                }
            }
        }
        return new Choice(best, bestVals);
    }

    static class BfsStats {
        long expanded = 0, generated = 1, pruned = 0, maxFrontier = 1;
        int depth = 0;
    }

    static class TooManyStates extends RuntimeException {
        // thrown when BFS generates too many states
        TooManyStates(long max) {
            super("BFS stopped after " + max + " states");
        }
    }

    record Item(byte[] grid, int depth) {}

    record BfsResult(byte[] solution, BfsStats stats) {}

    // BFS with backtracking over partial grids
    static BfsResult bfsSolve(byte[] puzzle, String order, long maxStates) {
        BfsStats st = new BfsStats();
        if (!isConsistent(puzzle)) return new BfsResult(null, st);
        ArrayDeque<Item> frontier = new ArrayDeque<>();
        frontier.add(new Item(puzzle, 0));
        while (!frontier.isEmpty()) {
            Item item = frontier.pollFirst();
            byte[] grid = item.grid();
            if (countEmpty(grid) == 0) {
                st.depth = item.depth();
                return new BfsResult(grid, st);
            }
            st.expanded++;
            int[][] masks = usedMasks(grid);
            Choice ch = chooseCell(grid, masks, order);
            if (ch.values().length == 0) {
                st.pruned++;
                continue;
            }
            for (int v : ch.values()) {
                byte[] child = grid.clone();
                child[ch.cell()] = (byte) v;
                frontier.addLast(new Item(child, item.depth() + 1));
                st.generated++;
            }
            st.maxFrontier = Math.max(st.maxFrontier, frontier.size());
            if (st.generated > maxStates) throw new TooManyStates(maxStates);
        }
        return new BfsResult(null, st);
    }

    record DfsResult(byte[] solution, long expanded, long backtracks) {}

    // DFS backtracking, used for --compare
    static DfsResult dfsBacktracking(byte[] puzzle, String order) {
        byte[] grid = puzzle.clone();
        long[] stats = new long[2];
        boolean ok = dfsRec(grid, order, stats);
        return new DfsResult(ok ? grid : null, stats[0], stats[1]);
    }

    // recursive step of the DFS backtracking
    static boolean dfsRec(byte[] grid, String order, long[] stats) {
        if (countEmpty(grid) == 0) return true;
        stats[0]++;
        Choice ch = chooseCell(grid, usedMasks(grid), order);
        for (int v : ch.values()) {
            grid[ch.cell()] = (byte) v;
            if (dfsRec(grid, order, stats)) return true;
        }
        grid[ch.cell()] = 0;
        stats[1]++;
        return false;
    }

    record Landscape(BigInteger raw, int empty, BigInteger free, BigInteger reduced) {}

    // computes the size of the search space
    static Landscape landscape(byte[] puzzle) {
        int empty = countEmpty(puzzle);
        int[][] masks = usedMasks(puzzle);
        BigInteger reduced = BigInteger.ONE;
        for (int i = 0; i < 81; i++) {
            if (puzzle[i] == 0) reduced = reduced.multiply(BigInteger.valueOf(candidates(i, masks).length));
        }
        BigInteger nine = BigInteger.valueOf(9);
        return new Landscape(nine.pow(81), empty, nine.pow(empty), reduced);
    }

    // formats a big number like 1.23e45
    static String sci(BigInteger x) {
        if (x.compareTo(BigInteger.valueOf(1_000_000)) < 0) return x.toString();
        String s = x.toString();
        return s.charAt(0) + "." + s.substring(1, 3) + "e" + (s.length() - 1);
    }

    // grid as text with box lines
    static String show(byte[] grid) {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < 9; r++) {
            if (r > 0 && r % 3 == 0) sb.append("------+-------+------\n");
            StringBuilder row = new StringBuilder();
            for (int c = 0; c < 9; c++) {
                if (c > 0 && c % 3 == 0) row.append("| ");
                int v = grid[r * 9 + c];
                row.append(v != 0 ? String.valueOf(v) : ".").append(' ');
            }
            sb.append(row.toString().stripTrailing());
            if (r < 8) sb.append('\n');
        }
        return sb.toString();
    }

    // solves one puzzle and prints the results
    static void solveAndReport(String name, byte[] puzzle, String order, boolean compare) {
        System.out.printf(Locale.ROOT, "=== %s (%d givens) ===%n", name.toUpperCase(Locale.ROOT), 81 - countEmpty(puzzle));
        System.out.println(show(puzzle));
        Landscape l = landscape(puzzle);
        System.out.println("\nLandscape: all grids 9^81 = " + sci(l.raw()) + "; "
                + "filling the " + l.empty() + " empty cells 9^" + l.empty() + " = " + sci(l.free()) + "; "
                + "after removing values excluded by the givens = " + sci(l.reduced()));
        System.out.println("Objective of the start grid f = " + objective(puzzle));
        long t0 = System.nanoTime();
        BfsResult res;
        try {
            res = bfsSolve(puzzle, order, 5_000_000);
        } catch (TooManyStates e) {
            System.out.println("\nBFS (" + order + "): " + e.getMessage() + "\n");
            return;
        }
        double dt = (System.nanoTime() - t0) / 1e9;
        if (res.solution() == null) {
            System.out.println("\nNo solution exists.\n");
            return;
        }
        BfsStats st = res.stats();
        System.out.println("\nSolution (BFS, cell order = " + order + "):");
        System.out.println(show(res.solution()));
        System.out.println("f(solution) = " + objective(res.solution()));
        System.out.printf(Locale.ROOT, "BFS: expanded=%d generated=%d dead ends=%d max frontier=%d depth=%d time=%.3fs%n",
                st.expanded, st.generated, st.pruned, st.maxFrontier, st.depth, dt);
        if (compare) {
            t0 = System.nanoTime();
            DfsResult d = dfsBacktracking(puzzle, order);
            System.out.printf(Locale.ROOT, "DFS backtracking: expanded=%d backtracks=%d time=%.3fs same solution=%s%n",
                    d.expanded(), d.backtracks(), (System.nanoTime() - t0) / 1e9,
                    Arrays.equals(d.solution(), res.solution()) ? "True" : "False");
        }
        System.out.println();
    }

    static final String USAGE = String.join("\n",
            "usage: java SudokuBFS [-h] [--level {easy,medium,hard,all}] [--puzzle PUZZLE]",
            "                      [--file FILE] [--order {mrv,first}] [--compare]",
            "",
            "Sudoku solver with BFS + backtracking",
            "",
            "options:",
            "  --level LEVEL    easy, medium, hard or all (default all)",
            "  --puzzle PUZZLE  81-character puzzle, '.' or '0' for empty cells",
            "  --file FILE      file containing a puzzle",
            "  --order ORDER    which empty cell to fill next: mrv or first (default mrv)",
            "  --compare        also run DFS backtracking");

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
        if (choices != null && !choices.contains(v)) fail("argument " + a[i] + ": invalid choice '" + v + "'");
        return v;
    }

    // reads the arguments and solves the puzzles
    public static void main(String[] args) throws IOException {
        String level = "all", puzzle = null, file = null, order = "mrv";
        boolean compare = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-h", "--help" -> {
                    System.out.println(USAGE);
                    return;
                }
                case "--level" -> level = value(args, i++, List.of("easy", "medium", "hard", "all"));
                case "--puzzle" -> puzzle = value(args, i++, null);
                case "--file" -> file = value(args, i++, null);
                case "--order" -> order = value(args, i++, List.of("mrv", "first"));
                case "--compare" -> compare = true;
                default -> fail("unrecognized argument: " + args[i]);
            }
        }

        try {
            if (puzzle != null || file != null) {
                String text = puzzle != null ? puzzle : Files.readString(Path.of(file));
                solveAndReport("custom", parse(text), order, compare);
                return;
            }
            for (String[] entry : PUZZLES) {
                if (level.equals("all") || level.equals(entry[0])) {
                    solveAndReport(entry[0], parse(entry[1]), order, compare);
                }
            }
        } catch (IllegalArgumentException e) {
            System.err.println("error: " + e.getMessage());
            System.exit(1);
        }
    }
}
