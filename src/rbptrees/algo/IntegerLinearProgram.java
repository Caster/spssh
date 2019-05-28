/*
 * This file is part of SPSSH.
 *
 * SPSSH is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SPSSH is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SPSSH. If not, see <https://www.gnu.org/licenses/>.
 *
 */

package rbptrees.algo;

import java.awt.Font;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import javax.swing.JLabel;

import ilog.concert.IloException;
import ilog.concert.IloIntVar;
import ilog.concert.IloLinearNumExpr;
import ilog.cplex.IloCplex;
import nl.tue.geometrycore.datastructures.doublylinkedlist.DoublyLinkedList;
import nl.tue.geometrycore.datastructures.doublylinkedlist.DoublyLinkedListItem;
import nl.tue.geometrycore.geometry.linear.LineSegment;
import nl.tue.geometrycore.gui.sidepanel.SideTab;
import nl.tue.geometrycore.util.DoubleUtil;
import rbptrees.algo.cplex.Callback;
import rbptrees.data.ColoredPointSet;
import rbptrees.data.ColoredPointSet.ColoredPoint;
import rbptrees.data.SupportGraph;
import rbptrees.data.SupportGraph.SupportNode;

public class IntegerLinearProgram extends ThreadableAlgorithm {

    private static final boolean DEBUG = false;

    private IloCplex cplex;
    private Callback callback;
    private IloIntVar[] e = null; // see initializeVariables
    private IloIntVar[] f = null; // see initializeVariables
    private IloIntVar[] g = null; // see initializeVariables
    int N; // number of points in input
    int C; // number of colors in input
    List<ColoredPoint> points;
    Map<Integer, ColoredPoint> sinks;

    private int maxIntersections = 0;
    private double intersectionWeight = 0;
    private boolean forceTree = false;
    private LazyMode lazy = LazyMode.LAZY_ALLPAIRS;
    private JLabel status = null;

    private SupportGraph[] warmstarts = new SupportGraph[0];

    public IloCplex getCplex() {
        return cplex;
    }

    public int getMaxIntersections() {
        return maxIntersections;
    }

    public void setMaxIntersections(int maxIntersections) {
        this.maxIntersections = maxIntersections;
    }

    public double getIntersectionWeight() {
        return intersectionWeight;
    }

    public void setIntersectionWeight(double intersectionWeight) {
        this.intersectionWeight = intersectionWeight;
    }

    public boolean isForceTree() {
        return forceTree;
    }

    public void setForceTree(boolean forceTree) {
        this.forceTree = forceTree;
    }

    public void setLazy(LazyMode lazy) {
        this.lazy = lazy;
    }

    public LazyMode getLazy() {
        return lazy;
    }

    public IntegerLinearProgram() {
        super("ILP");
        try {
            cplex = new IloCplex();
            cplex.setOut(null); // disable output
            cplex.setWarning(null); // disable warning output

            cplex.setParam(IloCplex.DoubleParam.WorkMem, 2048);
            cplex.setParam(IloCplex.IntParam.NodeFileInd, 3);
            cplex.setParam(IloCplex.DoubleParam.TreLim, 10000);
            // cplex.setParam(IloCplex.StringParam.WorkDir, "./tmpCplex/");

            callback = new Callback(cplex);
        } catch (IloException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void initialize(ColoredPointSet input) {
        super.initialize(input);
        warmstarts = new SupportGraph[0];
    }

    public void initialize(ColoredPointSet input, SupportGraph... warmstarts) {
        super.initialize(input);
        this.warmstarts = warmstarts;
    }

    @Override
    public void addListener(ProgressListener listener) {
        callback.addListener(listener);
    }

    @Override
    public void removeListener(ProgressListener listener) {
        callback.removeListener(listener);
    }

    @Override
    public boolean runSync() {
        try {
            if (e != null) {
                cplex.remove(e);
            }
            if (f != null) {
                cplex.remove(f);
            }
            if (g != null && g.length > 0) {
                cplex.remove(g);
            }
            cplex.clearCallbacks();
            cplex.clearCuts();
            cplex.clearModel();

            points = input.getPoints();
            N = input.size();
            C = input.getColors().size();
            sinks = new HashMap<>(C);

            findSinks();
            initializeVariables(cplex);
            initializeObjective(cplex);

            LazyEvaluation leval = new LazyEvaluation(cplex);
            if (leval.initializeConstraints()) {
                cplex.use(leval);
            }

            for (SupportGraph warmstart : warmstarts) {
                initializeVariableValues(cplex, warmstart);
            }

            callback.reset();
            callback.setCplex(cplex);
            cplex.use(callback);

            // disable presolving (is done in CPLEX example too)
            cplex.setParam(IloCplex.BooleanParam.PreInd, false);
            // do not apply periodic heuristic (determined by tuning model)
            cplex.setParam(IloCplex.LongParam.HeurFreq, -1);

            if (!cplex.solve()) {
                cplex.clearCallbacks();
                log("No solution found");
                callback.done();
                return false;
            }
            cplex.clearCallbacks();

            if (!callback.isAborted()) {
                output.getEdges().clear();
                createEdges(cplex);
                callback.done();
                return true;
            }
        } catch (IloException e) {
            e.printStackTrace();
        } catch (NoSuchElementException e) {
            System.err.println("Cannot find sinks.");
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public void setStatus(String text) {
        if (status != null) {
            status.setText(text);
        }
    }

    /**
     * Given two vertices u and v, return the index at which that edge would be
     * stored. This returns the index in an array of all undirected edges, where
     * loops are not stored.
     */
    private int edgeIndex(int u, int v) {
        if (u == v || u < 0 || v < 0 || u >= N || v >= N) {
            throw new RuntimeException();
        }
        if (v < u) {
            return edgeIndex(v, u);
        }
        return u * (2 * N - u - 1) / 2 + (v - u) - 1;
    }

    private int edgePairIndex(int ij, int kl) {
        return ij * (N * (N - 1) - ij - 1) / 2 + (kl - ij) - 1;
    }

    private int edgePairIndex(int i, int j, int k, int l) {
        int u = edgeIndex(i, j);
        int v = edgeIndex(k, l);
        return u * (N * (N - 1) - u - 1) / 2 + (v - u) - 1;
    }

    /**
     * Given a color and two vertices u and v, return the index at which the
     * flow in color {@code c} from {@code u} to {@code v} is stored.
     */
    private int flowIndex(int c, int u, int v) {
        if (u == v || u < 0 || v < 0 || u >= N || v >= N) {
            throw new RuntimeException();
        }
        return c * N * (N - 1)
                + // repeated for every color
                (N - 1) * u + (u > v ? v : v - 1);
    }

    /**
     * Find, for all colors used in the point set, an arbitrary point of
     * (amongst others) that color that functions as a sink.
     *
     * @throws NoSuchElementException When no sink can be found for any color.
     */
    private void findSinks() throws NoSuchElementException {
        colorLoop:
        for (Integer c : input.getColors()) {
            for (ColoredPoint p : input.iterateUnion(c)) {
                sinks.put(c, p);
                continue colorLoop;
            }
            throw new NoSuchElementException("cannot find a sink for color " + c);
        }
    }

    private void initializeVariableValues(IloCplex cplex, SupportGraph warm) throws IloException {
        double[] values = new double[e.length];

        for (int i = 0; i < N; ++i) {
            for (int j = i + 1; j < N; ++j) {
                int eij = edgeIndex(i, j);
                ColoredPoint p = input.getPoints().get(i);
                ColoredPoint q = input.getPoints().get(j);
                if (warm.getNodemap().get(p).isNeighborOf(warm.getNodemap().get(q))) {
                    values[eij] = 1.0;
                } else {
                    values[eij] = 0.0;
                }
            }
        }

        cplex.addMIPStart(e, values);
    }

    private void initializeVariables(IloCplex cplex) throws IloException {
        // e_{uv} indicates if there is an edge between u and v
        e = cplex.boolVarArray(N * (N - 1) / 2);
        // for every color and edge, have a variable that indicates the colored
        // flow through it (this is directed, as opposed to e_{uv})
        f = cplex.intVarArray(N * (N - 1) * C, 0, N - 1);
        // for every pair of edges, an indicator for whether both are selected
        if (maxIntersections > 0 || (maxIntersections < 0 && intersectionWeight > DoubleUtil.EPS)) {
            g = cplex.boolVarArray(N * (N - 1) * N * (N - 1));
        } else {
            g = new IloIntVar[0];
        }
    }

    private void initializeObjective(IloCplex cplex) throws IloException {
        IloLinearNumExpr objective = cplex.linearNumExpr();
        int eij;
        for (int i = 0; i < N; ++i) {
            for (int j = i + 1; j < N; ++j) {
                eij = edgeIndex(i, j);
                objective.addTerm(points.get(i).distanceTo(points.get(j)), e[eij]);
            }
        }

        if (intersectionWeight > 0 && maxIntersections != 0) {
            for (int i = 0; i < N; ++i) {
                for (int j = i + 1; j < N; ++j) {

                    // NB: k starts at i+1 to avoid double-checking intersections
                    // in other words, we only check (i,j) with (k,l) if i<j, k<l AND i<k
                    for (int k = i + 1; k < N; ++k) {
                        for (int l = k + 1; l < N; ++l) {
                            // by construction, i is different from j,k,l, and k from l
                            // make sure that j is different from k and l as well
                            if (j == k || j == l) {
                                continue;
                            }
                            objective.addTerm(g[edgePairIndex(i, j, k, l)], intersectionWeight);
                        }
                    }
                }
            }
        }

        cplex.addMinimize(objective);
    }

    private void createEdges(IloCplex cplex) {
        try {
            //System.out.println("total edge length = " + cplex.getObjValue());
            double eps = cplex.getParam(IloCplex.DoubleParam.EpInt);
            double[] edges = cplex.getValues(e);
            Map<ColoredPoint, SupportNode> map = output.getNodemap();
            for (int i = 0; i < N; ++i) {
                for (int j = i + 1; j < N; ++j) {
                    if (edges[edgeIndex(i, j)] >= 1.0 - eps) {
                        output.addEdge(map.get(points.get(i)), map.get(points.get(j)));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void displaySettings(SideTab tab) {
        status = tab.addLabel("Not running.");
        status.setFont(status.getFont().deriveFont(Font.ITALIC));
        tab.addSpace(2);

        tab.makeSplit(2, 2);
        tab.addLabel("Max intersections");
        tab.addIntegerSpinner(maxIntersections, -1, Integer.MAX_VALUE, 1, (e, v) -> {
            maxIntersections = v;
        });
        tab.makeSplit(2, 2);
        tab.addLabel("Intersection weight");
        tab.addDoubleSpinner(intersectionWeight, 0, Double.MAX_VALUE, 1, (e, v) -> {
            intersectionWeight = v;
        });
        tab.addCheckbox("Force tree", forceTree, (e, v) -> {
            forceTree = v;
        });
        tab.addComboBox(LazyMode.values(), lazy, (e, v) -> {
            lazy = v;
        });
    }

    @Override
    public String getSolutionIdentifier() {
        return String.format("ILP%s W%.2f%s %s",
                (maxIntersections == 0 ? "" : " I" + (maxIntersections > 0 ? "<=" + maxIntersections : "")),
                intersectionWeight, (forceTree ? " T" : ""), lazy
        );
    }

    private void log(String message) {
        if (DEBUG) {
            System.err.println(message);
        }
    }

    public enum LazyMode {
        NO_LAZY,
        LAZY_FIRST,
        LAZY_ALLPAIRS,
        LAZY_PRECOMP_ALLPAIRS,
        LAZY_PRECOMP_ALLINCIDENT;

        boolean isLazy() {
            return this != NO_LAZY;
        }

        boolean firstOnly() {
            return this == LAZY_FIRST;
        }

        boolean allPairs() {
            return this == LAZY_ALLPAIRS || this == LAZY_PRECOMP_ALLPAIRS;
        }

        boolean allIncident() {
            return this == LAZY_PRECOMP_ALLINCIDENT;
        }

        boolean usePrecomp() {
            return this == LAZY_PRECOMP_ALLPAIRS || this == LAZY_PRECOMP_ALLINCIDENT;
        }
    }

    private class UnconstrainedIntersection extends DoublyLinkedListItem<UnconstrainedIntersection> {

        final int ij;
        final int kl;

        public UnconstrainedIntersection(int ij, int kl) {
            this.ij = ij;
            this.kl = kl;
        }

    }

    private class Edge {

        int i;
        int j;
        int ij;
        LineSegment ls;

        Edge(int i, int j) {
            this.i = i;
            this.j = j;
            this.ij = edgeIndex(i, j);
            this.ls = new LineSegment(points.get(i), points.get(j));
        }
    }

    private class LazyEvaluation extends IloCplex.LazyConstraintCallback {

        private IloCplex cplex;
        private DoublyLinkedList<UnconstrainedIntersection>[] intersections;
        private int uncheckedcount;
        private Edge[] segments;

        public LazyEvaluation(IloCplex cplex) {
            this.cplex = cplex;
        }

        @SuppressWarnings("unchecked")
        public boolean initializeConstraints() throws IloException {
            // NON LAZY CONSTRAINTS

            if (forceTree) {
                IloLinearNumExpr edgeSum = cplex.linearNumExpr();
                for (int i = 0; i < N; ++i) {
                    for (int j = i + 1; j < N; ++j) {
                        edgeSum.addTerm(1.0, e[edgeIndex(i, j)]);
                    }
                }
                cplex.addLe(edgeSum, N - 1);
            }

            // there can only be flow when an edge is selected
            for (int i = 0; i < N; ++i) {
                for (int j = i + 1; j < N; ++j) {
                    List<Integer> sharedColors = new ArrayList<>(points.get(i).colors);
                    sharedColors.retainAll(points.get(j).colors);
                    IloLinearNumExpr maxEdgeFlow = cplex.linearNumExpr();
                    maxEdgeFlow.addTerm(e[edgeIndex(i, j)], N - 1);
                    for (Integer c : sharedColors) {
                        // the following constraints apparently cause the model to not be solvable
                        cplex.addLe(f[flowIndex(c, i, j)], maxEdgeFlow);
                        cplex.addLe(f[flowIndex(c, j, i)], maxEdgeFlow);
                    }

                    // other colors cannot use that edge at all
                    for (Integer c : input.getColors()) {
                        if (!sharedColors.contains(c)) {
                            cplex.addEq(f[flowIndex(c, i, j)], 0.0);
                            cplex.addEq(f[flowIndex(c, j, i)], 0.0);
                        }
                    }
                }
            }

            for (Integer c : input.getColors()) {
                // everything flows to a sink
                int sinkIndex = points.indexOf(sinks.get(c));
                IloLinearNumExpr flowSum = cplex.linearNumExpr();
                for (ColoredPointSet.ColoredPoint p : input.iterateUnion(c)) {
                    int pIndex = points.indexOf(p);
                    if (pIndex != sinkIndex) {
                        flowSum.addTerm(1.0, f[flowIndex(c, pIndex, sinkIndex)]);
                    }
                }
                cplex.addEq(flowSum, N - 1);

                // all vertices have an outflow of 1, except the sinks
                for (ColoredPointSet.ColoredPoint v : input.iterateUnion(c)) {
                    int vIndex = points.indexOf(v);
                    if (vIndex == sinkIndex) {
                        continue;
                    }
                    IloLinearNumExpr flowThroughV = cplex.linearNumExpr();
                    for (ColoredPointSet.ColoredPoint w : input.iterateUnion(c)) {
                        if (v.equals(w)) {
                            continue;
                        }
                        int wIndex = points.indexOf(w);
                        flowThroughV.addTerm(1.0, f[flowIndex(c, vIndex, wIndex)]);
                        flowThroughV.addTerm(-1.0, f[flowIndex(c, wIndex, vIndex)]);
                    }
                    cplex.addEq(flowThroughV, 1.0);
                }
            }

            if (maxIntersections < 0 && intersectionWeight <= DoubleUtil.EPS) {
                // dont care about intersections
                return false;
            }

            // bound the number of intersections
            if (maxIntersections > 0) {
                IloLinearNumExpr intCnt = cplex.linearNumExpr();
                for (IloIntVar iiv : g) {
                    intCnt.addTerm(iiv, 1.0);
                }
                cplex.addLe(intCnt, maxIntersections);
            }

            if (lazy.usePrecomp()) {
                intersections = new DoublyLinkedList[e.length];
                uncheckedcount = 0;
            }

            if (lazy.usePrecomp() || !lazy.isLazy()) {
                for (int i = 0; i < N; ++i) {
                    for (int j = i + 1; j < N; ++j) {
                        // NB: k starts at i+1 to avoid double-checking intersections
                        // in other words, we only check (i,j) with (k,l) if i<j, k<l AND i<k
                        int ij = edgeIndex(i, j);
                        if (lazy.usePrecomp()) {
                            intersections[ij] = new DoublyLinkedList<>();
                        }

                        for (int k = i + 1; k < N; ++k) {
                            for (int l = k + 1; l < N; ++l) {
                                // by construction, i<j and i<k<lis different from j,k,l, and k from l
                                if (checkIntersection(i, j, k, l)) {
                                    if (!lazy.isLazy()) {
                                        addIntersectionConstraint(edgeIndex(i, j), edgeIndex(k, l), false);
                                    } else {
                                        intersections[ij].addLast(new UnconstrainedIntersection(edgeIndex(i, j), edgeIndex(k, l)));
                                        uncheckedcount++;
                                    }
                                }
                            }
                        }
                    }
                }
                return uncheckedcount > 0;
            } else {
                uncheckedcount = 0;
                segments = new Edge[e.length];
                for (int i = 0; i < N; ++i) {
                    for (int j = i + 1; j < N; ++j) {
                        int ij = edgeIndex(i, j);
                        segments[ij] = new Edge(i, j);
                    }
                }
                return true;
            }

        }

        private boolean checkIntersection(int i, int j, int k, int l) {
            // by assumption, i < j, and i < k < l, simply ensure that j doesn't coincide with k or l
            if (j == k || j == l) {
                return false;
            }

            LineSegment uv = new LineSegment(points.get(i), points.get(j));
            LineSegment wx = new LineSegment(points.get(k), points.get(l));

            return !uv.intersect(wx).isEmpty();
        }

        private void addIntersectionConstraint(int ij, int kl, boolean lazymode) throws IloException {
            // these edges cannot both be selected
            // 0 <= e_{uv} + e_{wx} <= 1
            IloLinearNumExpr edgeSum = cplex.linearNumExpr();
            edgeSum.addTerm(1.0, e[ij]);
            edgeSum.addTerm(1.0, e[kl]);
            if (g.length > 0) {
                edgeSum.addTerm(g[edgePairIndex(ij, kl)], -2);
            }
            if (lazymode) {
                add(cplex.le(edgeSum, 1));
            } else {
                cplex.addLe(edgeSum, 1);
            }
        }

        @Override
        public void main() {
            try {
                if (lazy.usePrecomp()) {
                    checkLazyFromPrecomp();
                } else {
                    checkLazyFromScratch();
                }
            } catch (IloException ie) {
                ie.printStackTrace();
            }
        }

        private void checkLazyFromScratch() throws IloException {

            double eps = cplex.getParam(IloCplex.DoubleParam.EpInt);
            double[] edges = getValues(e);
            double[] indicators;
            if (g.length > 0) {
                indicators = getValues(g);
            } else {
                indicators = new double[0];
            }

            List<Integer> selected = new ArrayList<Integer>(points.size() + 100);
            for (int ij = 0; ij < edges.length; ij++) {
                if (edges[ij] < 1 - eps) {
                    continue;
                }
                selected.add(ij);
            }

            int cnt = 0;
            mainloop:
            for (int a = 0; a < selected.size(); a++) {
                Edge ea = segments[selected.get(a)];
                for (int b = a + 1; b < selected.size(); b++) {
                    Edge eb = segments[selected.get(b)];
                    if (ea.i >= eb.i || ea.j == eb.i || ea.j == eb.j) {
                        // shared endpoint
                        continue;
                    }
                    if (!eb.ls.intersect(ea.ls).isEmpty() && (indicators.length == 0 || indicators[edgePairIndex(ea.ij, eb.ij)] < eps)) {
                        // edges intersect, and either no indicators or indicator is turned off
                        addIntersectionConstraint(ea.ij, eb.ij, true);
                        cnt++;
                        if (lazy.firstOnly()) {
                            break mainloop;
                        }
                    }
                }
            }

            if (cnt > 0) {
                uncheckedcount += cnt;
                log("Added " + cnt + " constraints (" + uncheckedcount + " in total)");
            } else {
                log("Nothing to add");
            }
        }

        private void checkLazyFromPrecomp() throws IloException {
            if (uncheckedcount == 0) {
                return;
            }

            double eps = cplex.getParam(IloCplex.DoubleParam.EpInt);
            double[] edges = getValues(e);

            int tolerance;
            if (maxIntersections == 0 || intersectionWeight > DoubleUtil.EPS) {
                // any intersection must introduce a constraint for validity
                tolerance = 0;
            } else {
                // no cost of intersection, so we can tolerate some intersections and still accept a solution
                tolerance = maxIntersections;
                double[] indicators = getValues(g);
                for (double d : indicators) {
                    if (d > eps) {
                        tolerance--;
                    }
                }
            }

            List<UnconstrainedIntersection> violated = new ArrayList<UnconstrainedIntersection>(tolerance);

            int removing = 0;
            HashSet<Integer> edgesViolated = lazy.allIncident() ? new HashSet<Integer>() : null;

            for (int ij = 0; ij < edges.length; ij++) {
                if (edges[ij] < 1 - eps) {
                    continue;
                }
                // NB: k starts at i+1 to avoid double-checking intersections
                // in other words, we only check (i,j) with (k,l) if i<j, k<l AND i<k

                Iterator<UnconstrainedIntersection> it = intersections[ij].iterator();
                while (it.hasNext()) {
                    UnconstrainedIntersection ui = it.next();
                    if (edges[ui.kl] < 1.0 - eps) {
                        // edge not selected
                        continue;
                    }
                    if (tolerance > 0) {
                        violated.add(ui);
                        tolerance--;
                    } else {
                        addIntersectionConstraint(ui.ij, ui.kl, true);
                        it.remove();
                        removing++;
                        if (edgesViolated != null) {
                            edgesViolated.add(ui.ij);
                            edgesViolated.add(ui.kl);
                        }
                        if (lazy.firstOnly()) {
                            break;
                        }
                    }
                }
            }

            if (removing > 0) {

                for (UnconstrainedIntersection ui : violated) {
                    addIntersectionConstraint(ui.ij, ui.kl, true);
                    intersections[ui.ij].remove(ui);
                    removing++;
                    if (edgesViolated != null) {
                        edgesViolated.add(ui.ij);
                        edgesViolated.add(ui.kl);
                    }
                }

                if (edgesViolated != null) {

                    for (int ij : edgesViolated) {
                        Iterator<UnconstrainedIntersection> it2 = intersections[ij].iterator();
                        while (it2.hasNext()) {
                            UnconstrainedIntersection ui = it2.next();
                            if (edgesViolated.contains(ui.kl)) {
                                addIntersectionConstraint(ui.ij, ui.kl, true);
                                it2.remove();
                                removing++;
                            }
                        }
                    }

                }

                uncheckedcount -= removing;
                log("Added " + removing + " lazy constraints (remaining: " + uncheckedcount + ")");
            } else {
                log("No lazy constraints added (remaining: " + uncheckedcount + ")");
            }
        }
    }
}
