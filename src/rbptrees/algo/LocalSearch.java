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
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import nl.tue.geometrycore.geometry.linear.LineSegment;
import nl.tue.geometrycore.gui.sidepanel.SideTab;
import nl.tue.geometrycore.util.DoubleUtil;
import rbptrees.data.ColoredPointSet;
import rbptrees.data.SupportGraph.SupportLink;
import rbptrees.data.SupportGraph.SupportNode;

/**
 *
 * @author wmeulema
 */
public class LocalSearch extends Algorithm {

    private boolean forceTree = true;
    private boolean forcePlanar = true;
    private int iterations = -1;

    public LocalSearch() {
        super("LocalSearch");
    }

    public LocalSearch(boolean forceTree, boolean forcePlanar) {
        this();
        this.forceTree = forceTree;
        this.forcePlanar = forcePlanar;
    }

    @Override
    public boolean run() {

        if (!computeMSTheuristic()) {
            System.err.println("This method needs at least one node that is a member of all sets");
            return false;
        }

        int rounds = hillClimb();
        //System.out.println("Rounds: " + rounds);

        return true;
    }

    private boolean computeMSTheuristic() {

        List<SupportNode> intree = new ArrayList();
        List<SupportNode> outtree = new ArrayList();
        for (ColoredPointSet.ColoredPoint p : input.iterateExact(input.getColors())) {
            SupportNode n = output.getNodemap().get(p);
            outtree.add(n);
        }

        if (outtree.isEmpty()) {
            return false;
        }

        intree.add(outtree.remove(0));

        while (outtree.size() > 0) {

            SupportNode bin = null, bout = null;
            double dist = Double.POSITIVE_INFINITY;
            for (SupportNode in : intree) {
                for (SupportNode out : outtree) {
                    if (in.squaredDistanceTo(out) < dist) {
                        dist = in.squaredDistanceTo(out);
                        bin = in;
                        bout = out;
                    }
                }
            }

            outtree.remove(bout);
            intree.add(bout);

            output.addEdge(bin, bout);
        }

        for (ColoredPointSet.ColoredPoint cp : input.iterate((ColoredPointSet.ColoredPoint p) -> {
            return p.colors.size() < input.getColors().size();
        })) {
            SupportNode n = output.getNodemap().get(cp);
            SupportNode best = null;
            double dist = Double.POSITIVE_INFINITY;
            for (SupportNode p : intree) {
                if (n.squaredDistanceTo(p) < dist) {
                    dist = n.squaredDistanceTo(p);
                    best = p;
                }
            }
            output.addEdge(n, best);
        }

        return true;
    }

    private class CandidateEdge {

        final SupportNode a, b;

        CandidateEdge(SupportNode a, SupportNode b) {
            this.a = a;
            this.b = b;
        }

        double sqrLength() {
            return a.squaredDistanceTo(b);
        }

        double length() {
            return a.distanceTo(b);
        }

        List<Integer> getColors() {
            List<Integer> cols = new ArrayList<>(a.point.colors);
            cols.retainAll(b.point.colors);
            return cols;
        }

        LineSegment geom() {
            return new LineSegment(a, b);
        }

        @Override
        public String toString() {
            return "Cand{" + a.getGraphIndex() + " " + b.getGraphIndex() + "}";
        }
    }

    private class Bridge implements Comparable<Bridge> {

        CandidateEdge ce;
        List<Integer> cols = new ArrayList();

        Bridge(CandidateEdge ce, int color) {
            this.ce = ce;
            cols.add(color);
        }

        @Override
        public int compareTo(Bridge o) {
            return Double.compare(ce.sqrLength(), o.ce.sqrLength());
        }

        @Override
        public String toString() {
            return "Bridge{" + ce.a.getGraphIndex() + " " + ce.b.getGraphIndex() + "}";
        }

    }

    private int hillClimb() {
        List<CandidateEdge> candidateEdges = new LinkedList();

        for (int i = 0; i < output.getVertices().size(); i++) {
            SupportNode a = output.getVertices().get(i);
            for (int j = i + 1; j < output.getVertices().size(); j++) {
                SupportNode b = output.getVertices().get(j);

                if (!a.isNeighborOf(b)) {
                    candidateEdges.add(new CandidateEdge(a, b));
                }
            }
        }

        int rounds = 0;

        //System.out.println("Nodes:");
        for (SupportNode sn : output.getVertices()) {
            //System.out.println("  " + sn.toString());
        }
        //System.out.println("Edges:");
        for (SupportLink sl : output.getEdges()) {
            //System.out.println("  " + sl.toString());
        }
        //System.out.println("Candidates:");
        for (CandidateEdge ce : candidateEdges) {
            //System.out.println("  " + ce);
        }

        while (iterations < 0 || rounds < iterations) {

            //System.out.println("Testing " + rounds);
            SupportLink take_out = null;
            List<Bridge> add_back = null;
            double improvement = DoubleUtil.EPS;

            for (SupportLink link : output.getEdges()) {

                //System.out.println("Candidates::" + candidateEdges.size());
                double len = link.getGeometry().length();
                //System.out.println("  " + link);
                if (len < improvement) {
                    //System.out.println("    Too long");
                    continue;
                }

                List<Integer> colors = link.getColors();
                List<Bridge> bridgingEdges = new ArrayList();
                Iterator<Integer> it = colors.iterator();
                while (it.hasNext()) {
                    int c = it.next();

                    if (checkConnectivity(c, link, bridgingEdges, candidateEdges)) {
                        it.remove();
                    }

                }

                //System.out.println("Bridges::" + bridgingEdges.size());
                if (colors.isEmpty()) {
                    //System.out.println("    No colors");
                    // no colors need to be fixed!
                    improvement = len;
                    take_out = link;
                    add_back = new ArrayList();

                } else if (!bridgingEdges.isEmpty()) {
                    // some colors need to be fixed and we have options

                    if (forceTree) {
                        Iterator<Bridge> itb = bridgingEdges.iterator();
                        while (itb.hasNext()) {
                            Bridge b = itb.next();
                            if (b.cols.size() != colors.size()) {
                                itb.remove();
                                candidateEdges.add(b.ce);
                            }
                        }
                    }

                    Collections.sort(bridgingEdges);

                    List<Bridge> replacements = new ArrayList();
                    double replacement_length = findBestReplacement(bridgingEdges, 0, new ArrayList(), 0, colors, replacements, len - improvement);

                    if (!replacements.isEmpty()) {
                        //System.out.println("    Replacement of " + replacement_length);
                        for (Bridge b : replacements) {
                            //System.out.println("      " + b);
                        }
                        improvement = len - replacement_length;
                        take_out = link;
                        add_back = replacements;
                    } else {
                        //System.out.println("    No replacement beats current best");
                    }
                } else {
                    //System.out.println("    Nothing");
                }

                //System.out.println("Bridges::" + bridgingEdges.size());
                for (Bridge b : bridgingEdges) {
                    candidateEdges.add(b.ce);
                }
                //System.out.println("Candidates::" + candidateEdges.size());

            }

            if (take_out == null) {
                break;
            }

            rounds++;

            Iterator<CandidateEdge> it = candidateEdges.iterator();
            while (it.hasNext()) {
                CandidateEdge ce = it.next();
                boolean contains = false;
                for (Bridge b : add_back) {
                    if (b.ce == ce) {
                        contains = true;
                        break;
                    }
                }
                if (contains) {
                    output.addEdge(ce.a, ce.b);
                    it.remove();
                }
            }

            output.removeEdge(take_out);
            CandidateEdge ce = new CandidateEdge(take_out.getStart(), take_out.getEnd());
            candidateEdges.add(ce);

        }

        return rounds;
    }

    private double findBestReplacement(List<Bridge> bridges, int index,
            List<Bridge> currSolution, double currLength, List<Integer> colorsToStillConnect,
            List<Bridge> bestSolution, double bestLength) {

        // found a new solution
        if (colorsToStillConnect.isEmpty()) {
            bestSolution.clear();
            bestSolution.addAll(currSolution);
            return currLength;
        }

        // ran out of options
        if (index >= bridges.size()) {
            return bestLength;
        }

        Bridge b = bridges.get(index);
        double len = b.ce.length();

        // bound
        if (len + currLength >= bestLength) {
            return bestLength;
        }

        List<Integer> cols = new ArrayList(b.cols);
        cols.retainAll(colorsToStillConnect);

        // branch
        if (!cols.isEmpty()) {
            boolean allow = true;
            if (forcePlanar) {
                LineSegment ls = b.ce.geom();
                for (Bridge curr : currSolution) {
                    if (curr.ce.a == b.ce.a || curr.ce.b == b.ce.a || curr.ce.a == b.ce.b || curr.ce.b == b.ce.b) {
                        continue;
                    }
                    if (!curr.ce.geom().intersect(ls).isEmpty()) {
                        allow = false;
                        break;
                    }
                }
            }

            if (allow) {
                currSolution.add(b);
                colorsToStillConnect.removeAll(cols);
                bestLength = findBestReplacement(bridges, index + 1, currSolution, currLength + len, colorsToStillConnect, bestSolution, bestLength);
                colorsToStillConnect.addAll(cols);
                currSolution.remove(b);
            }
        }

        return findBestReplacement(bridges, index + 1, currSolution, currLength, colorsToStillConnect, bestSolution, bestLength);

    }

    private boolean checkConnectivity(int color, SupportLink exclude, List<Bridge> bridges, List<CandidateEdge> candidateEdges) {

        //System.out.println("      TC " + color);
        Set<SupportNode> visited = new HashSet();

        Queue<SupportNode> q = new LinkedList();

        q.add(exclude.getStart());
        visited.add(exclude.getStart());

        //System.out.println("      +v " + exclude.getStart().getGraphIndex());
        while (q.size() > 0) {
            SupportNode n = q.poll();

            for (SupportLink l : n.getEdges()) {
                if (l == exclude) {
                    continue;
                }
                SupportNode f = l.getOtherVertex(n);
                if (!visited.contains(f) && f.point.colors.contains(color)) {
                    //System.out.println("      +v " + f.getGraphIndex());
                    visited.add(f);
                    q.add(f);
                }
            }
        }

        boolean connected = true;
        for (SupportNode n : output.getVertices()) {
            if (n.point.colors.contains(color) && !visited.contains(n)) {
                connected = false;
            }
        }

        if (!connected) {

            //System.out.println("      Not connected");
            for (Bridge b : bridges) {
                if (b.ce.a.point.colors.contains(color) && b.ce.b.point.colors.contains(color) && visited.contains(b.ce.a) != visited.contains(b.ce.b)) {
                    b.cols.add(color);
                }
            }

            Iterator<CandidateEdge> it = candidateEdges.iterator();
            mainloop:
            while (it.hasNext()) {
                CandidateEdge ce = it.next();
                //System.out.println("      Testing " + ce);
                if (ce.a.point.colors.contains(color) && ce.b.point.colors.contains(color) && visited.contains(ce.a) != visited.contains(ce.b)) {
                    // different components!

                    //System.out.println("        diff");
                    if (forcePlanar) {
                        // check for intersections with current tree
                        for (SupportLink sl : output.getEdges()) {
                            if (sl == exclude) {
                                continue;
                            }
                            if (sl.getStart() == ce.a || sl.getEnd() == ce.a || sl.getStart() == ce.b || sl.getEnd() == ce.b) {
                                continue;
                            }
                            if (!sl.toGeometry().intersect(ce.geom()).isEmpty()) {
                                //System.out.println("          int");
                                // intersection, not a valid bridge
                                continue mainloop;
                            }
                        }
                    }

                    //System.out.println("          bridge");
                    it.remove();
                    bridges.add(new Bridge(ce, color));
                }
            }
        }

        return connected;
    }

    @Override
    public void displaySettings(SideTab tab) {
        tab.addCheckbox("Force tree", forceTree, (e, v) -> {
            forceTree = v;
        });
        tab.addCheckbox("Force planar", forcePlanar, (e, v) -> {
            forcePlanar = v;
        });

        tab.addIntegerSpinner(iterations, -1, Integer.MAX_VALUE, 1, (e, v) -> {
            iterations = v;
        });
    }

    @Override
    public String getSolutionIdentifier() {
        return getName() + (forceTree ? " T" : "") + (forcePlanar ? " P" : "") + (iterations >= 0 ? " " + iterations : "");
    }
}
