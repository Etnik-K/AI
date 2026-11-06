import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SearchTree<S> {

    public record Child<S>(String action, S state, String error) {}

    public interface Problem<S> {
        // start state
        S initial();

        // goal test
        boolean isGoal(S state);

        // children of a state, with an error text if a child breaks a constraint
        List<Child<S>> expand(S state);

        // state as one line of text
        String label(S state);

        // state as lines of text for the drawings
        default List<String> lines(S state) {
            return List.of(label(state));
        }
    }

    public static class Node<S> {
        final int id;
        final S state;
        final Node<S> parent;
        final String action;
        final int depth;
        final List<Node<S>> children = new ArrayList<>();
        String status = "open";
        String note = "";
        int order = 0;
        boolean onPath = false;

        // creates a tree node
        Node(int id, S state, Node<S> parent, String action, int depth) {
            this.id = id;
            this.state = state;
            this.parent = parent;
            this.action = action;
            this.depth = depth;
        }
    }

    final Problem<S> problem;
    final String strategy;
    final int maxDepth;
    final String repeat;
    final boolean stopAtGoal;
    final int maxNodes;
    final List<Node<S>> nodes = new ArrayList<>();
    final List<Node<S>> goals = new ArrayList<>();
    int expanded = 0;
    boolean truncated = false;
    final Node<S> root;

    // builds the tree and marks the solution path
    public SearchTree(Problem<S> problem, String strategy, int maxDepth, String repeat,
                      boolean stopAtGoal, int maxNodes) {
        this.problem = problem;
        this.strategy = strategy;
        this.maxDepth = maxDepth;
        this.repeat = repeat;
        this.stopAtGoal = stopAtGoal;
        this.maxNodes = maxNodes;
        this.root = build();
        if (!goals.isEmpty()) {
            for (Node<S> n = goals.get(0); n != null; n = n.parent) n.onPath = true;
        }
    }

    // adds a new node to the tree
    private Node<S> newNode(S state, Node<S> parent, String action) {
        Node<S> node = new Node<>(nodes.size(), state, parent, action, parent != null ? parent.depth + 1 : 0);
        nodes.add(node);
        if (parent != null) parent.children.add(node);
        return node;
    }

    // checks if the state was seen before
    private boolean isRepeated(Node<S> node, Set<S> seen) {
        if (repeat.equals("graph")) return seen.contains(node.state);
        if (repeat.equals("path")) {
            for (Node<S> a = node.parent; a != null; a = a.parent) {
                if (a.state.equals(node.state)) return true;
            }
        }
        return false;
    }

    // builds the search tree with BFS or DFS
    private Node<S> build() {
        Node<S> root = newNode(problem.initial(), null, null);
        Set<S> seen = new HashSet<>();
        seen.add(problem.initial());
        if (problem.isGoal(root.state)) {
            root.status = "goal";
            goals.add(root);
            return root;
        }
        if (maxDepth == 0) {
            root.status = "cutoff";
            return root;
        }
        ArrayDeque<Node<S>> frontier = new ArrayDeque<>();
        frontier.add(root);
        while (!frontier.isEmpty()) {
            if (nodes.size() >= maxNodes) {
                truncated = true;
                break;
            }
            Node<S> node = strategy.equals("bfs") ? frontier.pollFirst() : frontier.pollLast();
            expanded++;
            node.status = "expanded";
            node.order = expanded;
            List<Node<S>> newOpen = new ArrayList<>();
            for (Child<S> ch : problem.expand(node.state)) {
                Node<S> child = newNode(ch.state(), node, ch.action());
                if (ch.error() != null) {
                    child.status = "invalid";
                    child.note = ch.error();
                    continue;
                }
                if (isRepeated(child, seen)) {
                    child.status = "repeated";
                    continue;
                }
                seen.add(ch.state());
                if (problem.isGoal(ch.state())) {
                    child.status = "goal";
                    goals.add(child);
                } else if (child.depth >= maxDepth) {
                    child.status = "cutoff";
                } else {
                    newOpen.add(child);
                }
            }
            if (!goals.isEmpty() && stopAtGoal) break;
            if (strategy.equals("bfs")) {
                frontier.addAll(newOpen);
            } else {
                for (int i = newOpen.size() - 1; i >= 0; i--) frontier.addLast(newOpen.get(i));
            }
        }
        return root;
    }

    // path from the root to the first goal, or null
    public List<Node<S>> solution() {
        if (goals.isEmpty()) return null;
        List<Node<S>> path = new ArrayList<>();
        for (Node<S> n = goals.get(0); n != null; n = n.parent) path.add(0, n);
        return path;
    }

    // number of nodes for each status
    public Map<String, Integer> counts() {
        Map<String, Integer> c = new HashMap<>();
        for (Node<S> n : nodes) c.merge(n.status, 1, Integer::sum);
        return c;
    }

    // one-line summary of the tree
    public String summary() {
        Map<String, Integer> c = counts();
        List<String> parts = new ArrayList<>();
        for (String k : List.of("expanded", "goal", "invalid", "repeated", "cutoff", "open")) {
            if (c.containsKey(k)) parts.add(k + "=" + c.get(k));
        }
        int depth = 0;
        for (Node<S> n : nodes) depth = Math.max(depth, n.depth);
        String s = "strategy=" + strategy.toUpperCase(Locale.ROOT) + " repeated-state check=" + repeat
                + " nodes=" + nodes.size() + " depth=" + depth + "\n  " + String.join(" ", parts);
        if (truncated) s += "\n  (stopped at the limit of " + maxNodes + " nodes)";
        return s;
    }

    static final Map<String, String> MARKS = Map.of(
            "goal", "  ★ GOAL",
            "repeated", "  ↺ repeated",
            "cutoff", "  … depth limit",
            "open", "  (not expanded)");

    // status text shown after a node in the text tree
    private static String mark(Node<?> node) {
        if (node.status.equals("expanded")) return "  #" + node.order;
        if (node.status.equals("invalid")) return "  ✗ " + node.note;
        return MARKS.get(node.status);
    }

    // prints the tree as text
    public void printTree(PrintStream out) {
        printRec(out, root, "", true, true);
    }

    // prints a node and its subtree
    private void printRec(PrintStream out, Node<S> node, String prefix, boolean last, boolean isRoot) {
        String conn = isRoot ? "" : (last ? "└── " : "├── ");
        String act = node.action != null ? "[" + node.action + "] " : "";
        String star = node.onPath && !goals.isEmpty() ? "*" : "";
        out.println(prefix + conn + act + star + problem.label(node.state) + mark(node));
        String next = prefix + (isRoot ? "" : (last ? "    " : "│   "));
        for (int i = 0; i < node.children.size(); i++) {
            printRec(out, node.children.get(i), next, i == node.children.size() - 1, false);
        }
    }

    static final Map<String, String> FILL = Map.of(
            "expanded", "#dbe9f6", "goal", "#b7e1b0", "invalid", "#f4c2c2",
            "repeated", "#e0e0e0", "cutoff", "#ffffff", "open", "#fff4c2");

    // status text shown in the node boxes
    private static String caption(Node<?> node) {
        switch (node.status) {
            case "expanded": return "#" + node.order;
            case "invalid": return "✗ " + node.note;
            case "goal": return "GOAL";
            case "repeated": return "repeated";
            case "cutoff": return "depth limit";
            default: return "not expanded";
        }
    }

    // all text lines of a node box
    private List<String> texts(Node<S> n) {
        List<String> t = new ArrayList<>(problem.lines(n.state));
        t.add(caption(n));
        return t;
    }

    // writes the tree as a Graphviz .dot file
    public void writeDot(String path, String title) throws IOException {
        StringBuilder f = new StringBuilder();
        f.append("digraph SearchTree {\n");
        f.append("  graph [labelloc=t, fontname=\"monospace\"");
        if (!title.isEmpty()) f.append(", label=\"").append(title).append('"');
        f.append("];\n");
        f.append("  node [shape=box, style=\"rounded,filled\", fontname=\"monospace\"];\n");
        f.append("  edge [fontname=\"monospace\", fontsize=10];\n");
        boolean bold = !goals.isEmpty();
        for (Node<S> n : nodes) {
            String text = String.join("\\n", texts(n)).replace("\"", "\\\"");
            String style = n.status.equals("cutoff") ? "\"rounded,filled,dashed\"" : "\"rounded,filled\"";
            String pen = n.onPath && bold ? ", penwidth=3" : "";
            f.append("  n").append(n.id).append(" [label=\"").append(text).append("\", fillcolor=\"")
             .append(FILL.get(n.status)).append("\", style=").append(style).append(pen).append("];\n");
        }
        for (Node<S> n : nodes) {
            if (n.parent != null) {
                String pen = n.onPath && bold ? ", penwidth=3" : "";
                String act = String.valueOf(n.action).replace("\"", "\\\"");
                f.append("  n").append(n.parent.id).append(" -> n").append(n.id)
                 .append(" [label=\"").append(act).append('"').append(pen).append("];\n");
            }
        }
        f.append("}\n");
        Files.writeString(Path.of(path), f.toString());
    }

    // escapes text for SVG
    static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#x27;");
    }

    // rounds a number to the given digits
    private static String num(double v, int digits) {
        return new java.math.BigDecimal(v).setScale(digits, java.math.RoundingMode.HALF_EVEN).toPlainString();
    }

    // rounds a number to one digit
    private static String num(double v) {
        return num(v, 1);
    }

    // computes the x positions of a subtree
    private List<double[]> layout(Node<S> n, Map<Integer, Double> rel) {
        List<double[]> result = new ArrayList<>();
        if (n.children.isEmpty()) {
            result.add(new double[] {0.0, 0.0});
            return result;
        }
        List<double[]> contour = null;
        List<Double> offsets = new ArrayList<>();
        for (Node<S> c : n.children) {
            List<double[]> cc = layout(c, rel);
            double shift;
            if (contour == null) {
                shift = 0.0;
                contour = new ArrayList<>(cc);
            } else {
                shift = Double.NEGATIVE_INFINITY;
                for (int d = 0; d < Math.min(contour.size(), cc.size()); d++) {
                    shift = Math.max(shift, contour.get(d)[1] + 1 - cc.get(d)[0]);
                }
                for (int d = 0; d < cc.size(); d++) {
                    double l = cc.get(d)[0], r = cc.get(d)[1];
                    if (d < contour.size()) {
                        contour.set(d, new double[] {contour.get(d)[0], r + shift});
                    } else {
                        contour.add(new double[] {l + shift, r + shift});
                    }
                }
            }
            offsets.add(shift);
        }
        double mid = (offsets.get(0) + offsets.get(offsets.size() - 1)) / 2;
        for (int i = 0; i < n.children.size(); i++) rel.put(n.children.get(i).id, offsets.get(i) - mid);
        result.add(new double[] {0.0, 0.0});
        for (double[] lr : contour) result.add(new double[] {lr[0] - mid, lr[1] - mid});
        return result;
    }

    // draws the tree as an SVG picture
    public void writeSvg(String path, String title) throws IOException {
        double charW = 7.2, lineH = 14, pad = 6;
        Map<Integer, List<String>> texts = new HashMap<>();
        int maxLen = 0, maxLines = 0;
        for (Node<S> n : nodes) {
            List<String> t = texts(n);
            texts.put(n.id, t);
            maxLines = Math.max(maxLines, t.size());
            for (String line : t) maxLen = Math.max(maxLen, line.codePointCount(0, line.length()));
        }
        double boxW = maxLen * charW + 2 * pad;
        double boxH = maxLines * lineH + 2 * pad;
        double gapX = 18, gapY = 46;
        double top = title.isEmpty() ? 15 : 40;

        Map<Integer, Double> rel = new HashMap<>();
        List<double[]> rootContour = layout(root, rel);
        double left = Double.POSITIVE_INFINITY, right = Double.NEGATIVE_INFINITY;
        for (double[] lr : rootContour) {
            left = Math.min(left, lr[0]);
            right = Math.max(right, lr[1]);
        }
        double unit = boxW + gapX;
        double[] x = new double[nodes.size()];
        for (Node<S> n : nodes) {
            double base = n.parent != null ? x[n.parent.id] : -left;
            x[n.id] = base + rel.getOrDefault(n.id, 0.0);
        }
        for (int i = 0; i < x.length; i++) x[i] *= unit;
        double slots = right - left + 1;
        int depth = 0;
        for (Node<S> n : nodes) depth = Math.max(depth, n.depth);
        double width = Math.max(slots * unit + 20, title.isEmpty() ? 0 : 420);
        double height = top + (depth + 1) * (boxH + gapY) + 10;
        boolean anyGoal = !goals.isEmpty();

        List<String> out = new ArrayList<>();
        out.add("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"" + num(width, 0) + "\" height=\""
                + num(height, 0) + "\" font-family=\"monospace\" font-size=\"12\">");
        out.add("<rect width=\"100%\" height=\"100%\" fill=\"white\"/>");
        if (!title.isEmpty()) {
            out.add("<text x=\"10\" y=\"24\" font-size=\"15\" font-weight=\"bold\">" + escape(title) + "</text>");
        }
        for (Node<S> n : nodes) {
            if (n.parent != null) {
                boolean bold = n.onPath && anyGoal;
                double x1 = x[n.parent.id] + 10 + boxW / 2, y1 = top + n.parent.depth * (boxH + gapY) + boxH;
                double x2 = x[n.id] + 10 + boxW / 2, y2 = top + n.depth * (boxH + gapY);
                out.add("<line x1=\"" + num(x1) + "\" y1=\"" + num(y1) + "\" x2=\"" + num(x2) + "\" y2=\"" + num(y2)
                        + "\" stroke=\"" + (bold ? "#1b5e20" : "#888") + "\" stroke-width=\"" + (bold ? 3 : 1) + "\"/>");
                if (n.action != null) {
                    out.add("<text x=\"" + num((x1 + x2) / 2 + 3) + "\" y=\"" + num((y1 + y2) / 2 + 4)
                            + "\" font-size=\"10\" fill=\"#444\">" + escape(n.action) + "</text>");
                }
            }
        }
        for (Node<S> n : nodes) {
            double bx = x[n.id] + 10, by = top + n.depth * (boxH + gapY);
            boolean bold = n.onPath && anyGoal;
            String dash = n.status.equals("cutoff") ? " stroke-dasharray=\"4,3\"" : "";
            out.add("<rect x=\"" + num(bx) + "\" y=\"" + num(by) + "\" width=\"" + num(boxW) + "\" height=\"" + num(boxH)
                    + "\" rx=\"6\" fill=\"" + FILL.get(n.status) + "\" stroke=\"" + (bold ? "#1b5e20" : "#555")
                    + "\" stroke-width=\"" + (bold ? 3 : 1) + "\"" + dash + "/>");
            List<String> t = texts.get(n.id);
            for (int i = 0; i < t.size(); i++) {
                boolean last = i == t.size() - 1;
                out.add("<text x=\"" + num(bx + boxW / 2) + "\" y=\"" + num(by + pad + (i + 1) * lineH - 3)
                        + "\" text-anchor=\"middle\"" + (last ? " font-size=\"10\" fill=\"#333\"" : "")
                        + " xml:space=\"preserve\">" + escape(t.get(i)) + "</text>");
            }
        }
        out.add("</svg>");
        Files.writeString(Path.of(path), String.join("\n", out));
    }

    public static class Options {
        String strategy = "bfs";
        int depth;
        String repeat;
        final String defaultRepeat;
        boolean all = false;
        int maxNodes = 5000;
        boolean quiet = false;
        String out = null;

        // default options of a problem
        public Options(int depth, String repeat) {
            this.depth = depth;
            this.repeat = repeat;
            this.defaultRepeat = repeat;
        }

        static final String HELP = String.join("\n",
                "  --strategy {bfs,dfs}         search strategy (default bfs)",
                "  --depth DEPTH                depth limit of the tree",
                "  --repeat {graph,path,none}   repeated state check",
                "  --all                        keep building after the first goal is found",
                "  --max-nodes MAX_NODES        stop the tree at this many nodes (default 5000)",
                "  --quiet                      do not print the text tree",
                "  --out OUT                    base name for the .dot/.svg files");

        // reads one option, returns the next index or -1
        public int parse(String[] args, int i) {
            switch (args[i]) {
                case "--strategy" -> strategy = choice(args, i, List.of("bfs", "dfs"));
                case "--depth" -> depth = intValue(args, i);
                case "--repeat" -> repeat = choice(args, i, List.of("graph", "path", "none"));
                case "--max-nodes" -> maxNodes = intValue(args, i);
                case "--out" -> out = value(args, i);
                case "--all" -> {
                    all = true;
                    return i + 1;
                }
                case "--quiet" -> {
                    quiet = true;
                    return i + 1;
                }
                default -> {
                    return -1;
                }
            }
            return i + 2;
        }
    }

    // returns the value after an option
    public static String value(String[] args, int i) {
        if (i + 1 >= args.length) throw new IllegalArgumentException("argument " + args[i] + " needs a value");
        return args[i + 1];
    }

    // returns the integer after an option
    public static int intValue(String[] args, int i) {
        String v = value(args, i);
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("argument " + args[i] + ": invalid int value: '" + v + "'");
        }
    }

    // returns the value after an option and checks it
    public static String choice(String[] args, int i, List<String> choices) {
        String v = value(args, i);
        if (!choices.contains(v)) {
            throw new IllegalArgumentException("argument " + args[i] + ": invalid choice: '" + v
                    + "' (choose from " + String.join(", ", choices) + ")");
        }
        return v;
    }

    // prints the output in UTF-8
    public static void useUtf8() {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
    }

    // runs a task on a thread with a bigger stack
    public static void runWithBigStack(Runnable task) {
        Thread t = new Thread(null, task, "main", 1L << 28);
        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // builds the tree, prints it and writes the .dot and .svg files
    public static <S> SearchTree<S> run(Problem<S> problem, Options args, String name, String title) {
        SearchTree<S> tree = new SearchTree<>(problem, args.strategy, args.depth, args.repeat,
                !args.all, args.maxNodes);
        PrintStream out = System.out;
        if (!args.quiet) {
            out.println("\nSearch tree (#k = expansion order, * = on the solution path):");
            tree.printTree(out);
        }
        out.println("\n" + tree.summary());
        List<Node<S>> sol = tree.solution();
        if (sol != null) {
            out.println("\nSolution with " + (sol.size() - 1) + " steps:");
            for (int i = 0; i < sol.size(); i++) {
                Node<S> n = sol.get(i);
                out.printf(Locale.ROOT, "  %2d. %12s  ->  %s%n", i,
                        n.action == null ? "start" : n.action, problem.label(n.state));
            }
        } else {
            out.println("\nNo goal inside this tree.");
        }
        String base = args.out != null ? args.out
                : name + "_" + args.strategy + (args.repeat.equals(args.defaultRepeat) ? "" : "_" + args.repeat);
        try {
            tree.writeDot(base + ".dot", title);
            tree.writeSvg(base + ".svg", title);
            out.println("\nWrote " + base + ".dot and " + base + ".svg");
        } catch (IOException e) {
            System.err.println("could not write the drawings: " + e.getMessage());
        }
        return tree;
    }
}
