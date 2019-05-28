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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import nl.tue.geometrycore.geometry.linear.LineSegment;
import nl.tue.geometrycore.gui.sidepanel.SideTab;
import rbptrees.data.ColoredPointSet;
import rbptrees.data.SupportGraph;
import rbptrees.data.SupportGraph.SupportLink;
import rbptrees.data.SupportGraph.SupportNode;

/**
 *
 * @author wmeulema
 */
public class BruteForceSolver extends ThreadableAlgorithm {

    private ProgressListener listener = null;
    boolean forceTree = false, forcePlanar = true;

    // opt
    private double upperbound;
    private List<CandidateEdge> upperboundEdges;
    private List<CandidateEdge> candidateEdges;
    // state
    private List<CandidateEdge> solution;
    private double currlength;
    private int numSplitColors;
    private int[] componentCountPerColor;
    private int[][] componentIdsPerColor;
    private int edgeUpperbound;
    private int edgeLowerbound;
    // memory
    private List<Memory> memories;
    private List<CandidateEdge> deactivated;

    private void clearState() {

        upperboundEdges = null;
        candidateEdges = null;
        solution = null;
        currlength = 0;
        numSplitColors = 0;
        edgeUpperbound = 0;
        edgeLowerbound = 0;
        componentCountPerColor = null;
        componentIdsPerColor = null;
        memories = null;
        deactivated = null;
    }

    private void printState(int next) {
        System.out.println("STATE " + next);
        System.out.println("  split: " + numSplitColors);
        System.out.print("  component count");
        int index = 0;
        for (int c : componentCountPerColor) {
            System.out.print("  " + index + ":" + c);
            index++;
        }
        System.out.println("");
        System.out.print("  ids: ");
        index = 0;
        for (int[] cs : componentIdsPerColor) {
            if (index >= 1) {
                System.out.print("       ");
            }
            System.out.print(index + "::");
            for (int id : cs) {
                System.out.print("  " + id);
            }
            System.out.println("");
            index++;
        }
        System.out.println("");
    }

    public BruteForceSolver(boolean forceTree, boolean forcePlanar) {
        super("BruteForce");
        this.forceTree = forceTree;
        this.forcePlanar = forcePlanar;
    }

    @Override
    public void initialize(ColoredPointSet input) {
        super.initialize(input);
        upperbound = Double.POSITIVE_INFINITY;
    }

    public void initialize(ColoredPointSet input, SupportGraph upperbound) {
        super.initialize(input);
        this.upperbound = upperbound.getTotalLength();
        for (SupportLink e : upperbound.getEdges()) {
            output.addEdge(output.getNodemap().get(e.getStart().point), output.getNodemap().get(e.getEnd().point));
        }
    }

    @Override
    public void addListener(ProgressListener listener) {
        if (this.listener == null) {
            this.listener = listener;
        } else if (this.listener != listener) {
            System.err.println("Trying to add multiple listeners to BruteForce algorithm");
        }
    }

    @Override
    public void removeListener(ProgressListener listener) {
        if (this.listener == listener) {
            this.listener = null;
        }
    }

    private boolean prune(SupportNode u, SupportNode v) {
        return false;
    }

    @Override
    public boolean runSync() {
        clearState();

        int pruned = 0;
        candidateEdges = new ArrayList(1 + input.getPoints().size() * input.getPoints().size() / 2);
        for (SupportNode u : output.getVertices()) {
            for (SupportNode v : output.getVertices()) {
                if (v.getGraphIndex() <= u.getGraphIndex()) {
                    continue;
                }
                if (!v.point.hasCommonColor(u.point)) {
                    continue;
                }

                if (!prune(u, v)) {
                    candidateEdges.add(new CandidateEdge(u, v));
                } else {
                    pruned++;
                }
            }
        }
        candidateEdges.sort((CandidateEdge o1, CandidateEdge o2) -> {
            return Double.compare(o1.length, o2.length);
        });

        solution = new ArrayList();
        currlength = 0;
        int k = input.getColors().size();
        int n = input.size();
        componentCountPerColor = new int[k];
        componentIdsPerColor = new int[k][];

        numSplitColors = 0;
        for (int c = 0; c < k; c++) {
            componentIdsPerColor[c] = new int[n];
            componentCountPerColor[c] = 0;
            for (int i = 0; i < n; i++) {
                if (output.getVertices().get(i).point.colors.contains(c)) {
                    componentIdsPerColor[c][i] = i;
                    componentCountPerColor[c]++;
                } else {
                    componentIdsPerColor[c][i] = -1;
                }
            }
            if (componentCountPerColor[c] > 1) {
                numSplitColors++;
            }
        }

        edgeLowerbound = input.size() - countComponents();
        if (forceTree) {
            edgeUpperbound = edgeLowerbound;
        } else {
            edgeUpperbound = 0;
            for (int c = 0; c < k; c++) {
                edgeUpperbound += (componentCountPerColor[c] - 1);
            }
        }

        memories = new ArrayList((edgeUpperbound + 1) * k);
        deactivated = new ArrayList(candidateEdges.size());

        recurse(0);

        boolean success = Double.isFinite(upperbound);

        if (upperboundEdges != null) {
            while (output.getEdges().size() > 0) {
                output.removeEdge(output.getEdges().get(output.getEdges().size() - 1));
            }

            for (CandidateEdge e : upperboundEdges) {
                output.addEdge(e.u, e.v);
            }
        }

        clearState();

        if (listener != null) {
            listener.onDone(1);
        }
        return success;
    }

    private void recurse(int next) {

        //printState(next);
        if (listener != null && listener.shouldAbort(0, 0)) {
            return;
        }

        if (currlength >= upperbound) {
            return;
        }
        if (numSplitColors == 0) {
            // valid solution and better than previous
            upperboundEdges = new ArrayList(solution);
            upperbound = currlength;
            //printState(next);
            return;
        }

        if (next >= candidateEdges.size()) {
            return;
        }

        CandidateEdge e = candidateEdges.get(next);
        if (e.active) {
            int minToConnectMaxColor = 0;
            int maxcolor = -1;
            for (int c = 0; c < componentCountPerColor.length; c++) {
                int cnt = componentCountPerColor[c] - 1;
                if (cnt > minToConnectMaxColor) {
                    minToConnectMaxColor = cnt;
                    maxcolor = c;
                }
            }
            if (solution.size() + minToConnectMaxColor > edgeUpperbound) {
                return;
            }
            if (next + minToConnectMaxColor - 1 >= candidateEdges.size()) {
                return;
            }
            double forecast = currlength;
            int pick = next;
            int counter = minToConnectMaxColor;
            while (counter > 0) {
                if (pick == candidateEdges.size()) {
                    return;
                }

                CandidateEdge f = candidateEdges.get(pick);
                while (!f.active || !f.colors.contains(maxcolor)) {
                    pick++;
                    if (pick == candidateEdges.size()) {
                        return;
                    }
                    f = candidateEdges.get(pick);
                }
                forecast += f.length;
                pick++;
                counter--;
            }
            int others = edgeLowerbound - solution.size() - minToConnectMaxColor;
            pick = next;
            while (others > 0) {
                if (pick == candidateEdges.size()) {
                    return;
                }

                CandidateEdge f = candidateEdges.get(pick);
                while (!f.active || candidateEdges.get(pick).colors.contains(maxcolor)) {
                    pick++;
                    if (pick == candidateEdges.size()) {
                        return;
                    }
                    f = candidateEdges.get(pick);
                }
                forecast += f.length;
                pick++;
                others--;
            }
            if (forecast >= upperbound) {
                return;
            }

            // check if it connects new components
            boolean passCheck = false;
            List<Integer> alreadyConnected = new ArrayList();
            for (int c : e.colors) {
                if (componentIdsPerColor[c][e.u.getGraphIndex()] == componentIdsPerColor[c][e.v.getGraphIndex()]) {
                    alreadyConnected.add(c);
                } else {
                    passCheck = true;
                }
            }

            if (passCheck && forceTree && !alreadyConnected.isEmpty()) {
                // check if it doesn't make a cycle (if tree forced)
                passCheck = false;
            }

            if (passCheck && !alreadyConnected.isEmpty() && Double.isFinite(upperbound)) {
                // check if there is no shorter edge that can now be avoided
                for (CandidateEdge f : solution) {
                    if (alreadyConnected.containsAll(f.colors) && checkConnectivity(e, f)) {
                        passCheck = false;
                        //System.out.println("skipping at next = " + next);
                        break;
                    }
                }
            }

            if (passCheck) {
                // book keeping

                solution.add(e);
                double oldlen = currlength;
                currlength += e.length;

                int n = input.size();

                int old_deactivate_count = deactivated.size();
                if (forcePlanar) {
                    // check if it doesn't make an intersection (if planar forced)
                    for (int i = next + 1; i < candidateEdges.size(); i++) {
                        CandidateEdge f = candidateEdges.get(i);
                        if (f.u == e.u || f.u == e.v || f.v == e.u || f.v == e.v) {
                            continue;
                        }

                        if (f.segment.intersect(e.segment).isEmpty()) {
                            continue;
                        }

                        f.active = false;
                        deactivated.add(f);
                    }
                }

                int old_memory_count = memories.size();
                for (int c : e.colors) {
                    int[] id = componentIdsPerColor[c];
                    if (id[e.u.getGraphIndex()] == id[e.v.getGraphIndex()]) {
                        // nothing to update
                    } else {
                        // update component
                        Memory memory = new Memory();
                        memories.add(memory);
                        memory.color = c;
                        memory.oldnumber = id[e.u.getGraphIndex()];
                        for (int i = 0; i < n; i++) {
                            if (id[i] == memory.oldnumber) {
                                id[i] = id[e.v.getGraphIndex()];
                                memory.changed.add(i);
                            }
                        }
                        componentCountPerColor[c]--;
                        if (componentCountPerColor[c] <= 1) {
                            numSplitColors--;
                        }
                    }
                }

                recurse(next + 1);

                while (deactivated.size() > old_deactivate_count) {
                    CandidateEdge f = deactivated.remove(deactivated.size() - 1);
                    f.active = true;
                }

                while (memories.size() > old_memory_count) {
                    Memory memory = memories.remove(memories.size() - 1);
                    for (int i : memory.changed) {
                        componentIdsPerColor[memory.color][i] = memory.oldnumber;
                    }
                    if (componentCountPerColor[memory.color] <= 1) {
                        numSplitColors++;
                    }
                    componentCountPerColor[memory.color]++;
                }

                currlength = oldlen;
                solution.remove(solution.size() - 1);
            }
        }
        recurse(next + 1);
    }

    @Override
    public void setStatus(String text) {

    }

    @Override
    public String getSolutionIdentifier() {
        return "BruteForce" + (forceTree ? " T" : "") + (forcePlanar ? " P" : "");
    }

    @Override
    public void displaySettings(SideTab tab) {
        tab.addCheckbox("Force tree", forceTree, (e, v) -> {
            forceTree = v;
        });
        tab.addCheckbox("Force planar", forcePlanar, (e, v) -> {
            forcePlanar = v;
        });
    }

    private int countComponents() {
        boolean[] visited = new boolean[input.getPoints().size()];
        Arrays.fill(visited, false);

        int index = 0;
        int cnt = 0;
        while (index < visited.length) {
            if (!visited[index]) {
                cnt++;
                dfs(visited, output.getVertices().get(index));
            }
            index++;
        }
        return cnt;
    }

    private void dfs(boolean[] visited, SupportNode n) {
        if (visited[n.getGraphIndex()]) {
            return;
        }

        visited[n.getGraphIndex()] = true;
        for (CandidateEdge e : candidateEdges) {
            if (e.u == n) {
                dfs(visited, e.v);
            } else if (e.v == n) {
                dfs(visited, e.u);
            }
        }
    }

    private boolean checkConnectivity(CandidateEdge add, CandidateEdge ignore) {
        // check if solution is still connected, if we imagine including "add" and remove "ignore"

        int[] visited = new int[input.size()];
        // -1 unreached
        // 0 reached from u
        // 1 reached from v

        int index = solution.indexOf(ignore);
        solution.set(index, add);

        boolean result = true;

        // bidirectional bfs
        colorloop:
        for (int c : ignore.colors) {

            Arrays.fill(visited, -1);

            LinkedList<SupportNode> queue = new LinkedList();
            visited[ignore.u.getGraphIndex()] = 0;
            queue.add(ignore.u);

            visited[ignore.v.getGraphIndex()] = 1;
            queue.add(ignore.v);

            while (!queue.isEmpty()) {
                SupportNode n = queue.removeFirst();

                for (CandidateEdge e : solution) {
                    if (!e.colors.contains(c)) {
                        continue;
                    }
                    SupportNode other;
                    if (e.u == n) {
                        other = e.v;
                    } else if (e.v == n) {
                        other = e.u;
                    } else {
                        continue;
                    }
                    switch (visited[other.getGraphIndex()]) {
                        case -1:
                            visited[other.getGraphIndex()] = visited[n.getGraphIndex()];
                            queue.addLast(other);
                            break;
                        case 0:
                        case 1:
                            if (visited[other.getGraphIndex()] == visited[n.getGraphIndex()]) {
                                // same component
                            } else {
                                // the u and v component meet, found a path
                                continue colorloop;
                            }
                            break;
                    }
                }
            }

            // cant reach
            result = false;
            break;
        }

        solution.set(index, ignore);
        return result;
    }

    private class Memory {

        int color;
        int oldnumber;
        List<Integer> changed = new ArrayList();
    }

    private class CandidateEdge {

        double length;
        LineSegment segment;
        SupportNode u, v;
        Set<Integer> colors;
        boolean active;

        public CandidateEdge(SupportNode u, SupportNode v) {
            this.u = u;
            this.v = v;
            this.segment = new LineSegment(u, v);
            this.length = segment.length();
            this.colors = new HashSet(u.point.colors);
            this.colors.retainAll(v.point.colors);
            this.active = true;
        }

    }
}
