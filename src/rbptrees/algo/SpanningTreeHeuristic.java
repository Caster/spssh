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
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import nl.tue.geometrycore.geometry.linear.LineSegment;
import nl.tue.geometrycore.gui.sidepanel.SideTab;
import nl.tue.geometrycore.util.DoubleUtil;
import rbptrees.data.ColoredPointSet.ColoredPoint;
import rbptrees.data.SupportGraph.SupportLink;
import rbptrees.data.SupportGraph.SupportNode;

/**
 *
 * @author wmeulema
 */
public class SpanningTreeHeuristic extends Algorithm {

    private SupportLink[] towardsBackbone = null;
    private final boolean singularstar;
    private final boolean improveTree;

    public SpanningTreeHeuristic(boolean singleStar, boolean improveTree) {
        super(singleStar ? "Star" : (improveTree ? "SpanningTreeStar" : "SpanningTreeHeuristic"));
        this.singularstar = singleStar;
        this.improveTree = singleStar ? false : improveTree;
    }

    @Override
    public boolean run() {

        towardsBackbone = new SupportLink[output.getVertices().size()];
        if (singularstar) {
            return computeSingleStar();
        } else {
            if (!computeMSTheuristic()) {
                System.err.println("This method needs at least one node that is a member of all sets");
                return false;
            }

            if (!improveTree) {
                improveTree();
                for (int c : input.getColors()) {
                    if (!checkConnectivity(c)) {
                        System.err.println("Color " + c + " not connected after heuristic improvements");
                    }
                }
            }

            return true;
        }
    }

    private boolean computeMSTheuristic() {

        List<SupportNode> intree = new ArrayList();
        List<SupportNode> outtree = new ArrayList();
        for (ColoredPoint p : input.iterateExact(input.getColors())) {
            SupportNode n = output.getNodemap().get(p);
            towardsBackbone[n.getGraphIndex()] = null;
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

        for (ColoredPoint cp : input.iterate((ColoredPoint p) -> {
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
            towardsBackbone[n.getGraphIndex()] = output.addEdge(n, best);
        }

        return true;
    }

    private boolean checkConnectivity(int color) {
        Set<SupportNode> visited = new HashSet();

        Queue<SupportNode> q = new LinkedList();

        for (SupportNode n : output.getVertices()) {
            if (n.point.colors.contains(color)) {
                q.add(n);
                visited.add(n);
                break;
            }
        }

        while (q.size() > 0) {
            SupportNode e = q.poll();

            for (SupportNode f : e.getNeighbors()) {
                if (f.point.colors.contains(color)) {
                    if (!visited.contains(f)) {
                        visited.add(f);
                        q.add(f);
                    }
                }
            }
        }

        for (SupportNode n : output.getVertices()) {
            if (n.point.colors.contains(color) && !visited.contains(n)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public void displaySettings(SideTab tab) {
    }

    @Override
    public String getSolutionIdentifier() {
        if (improveTree) {
            return "Star";
        } else {
            return "Star++";
        }
    }

    private boolean improveTree() {
        boolean somethingatall = false;
        boolean didsomething;
        List<SupportNode> elts = new ArrayList();
        for (ColoredPoint p : input.iterate((ColoredPoint p) -> {
            return p.colors.size() < input.getColors().size();
        })) {
            elts.add(output.getNodemap().get(p));
        }
        List<SupportNode> elts2 = new ArrayList(elts);

        do {
            didsomething = false;

            SupportNode best_src = null;
            SupportNode best_tar = null;
            double improvement = DoubleUtil.EPS;

            for (final SupportNode e : elts2) {

                elts.sort((SupportNode o1, SupportNode o2) -> Double.compare(o1.squaredDistanceTo(e), o2.squaredDistanceTo(e)));

                for (SupportNode f : elts) {
                    if (!f.point.colors.containsAll(e.point.colors)) {
                        continue;
                    }

                    if (f == e || f.isNeighborOf(e)) {
                        continue;
                    }

                    // check for improvement
                    double imp = towardsBackbone[e.getGraphIndex()].toGeometry().length() - f.distanceTo(e);
                    if (imp < improvement) {
                        continue;
                    }

                    // check for intersections
                    LineSegment ls = new LineSegment(e, f);
                    boolean intersects = false;

                    for (SupportLink l : output.getEdges()) {
                        if (l.getStart() == e || l.getStart() == f) {
                            continue;
                        }
                        if (l.getEnd() == e || l.getEnd() == f) {
                            continue;
                        }
                        if (l.getGeometry().intersect(ls, DoubleUtil.EPS).size() > 0) {
                            intersects = true;
                            break;
                        }
                    }

                    if (intersects) {
                        continue;
                    }

                    // check for connectivity
                    SupportNode oldTar = towardsBackbone[e.getGraphIndex()].getOtherVertex(e);
                    output.removeEdge(towardsBackbone[e.getGraphIndex()]);
                    SupportLink l = output.addEdge(e, f);
                    towardsBackbone[e.getGraphIndex()] = l;

                    boolean successful = checkConnectivity(e.point.colors.iterator().next());

                    output.removeEdge(l);
                    l = output.addEdge(e, oldTar);
                    towardsBackbone[e.getGraphIndex()] = l;

                    if (successful) {
                        best_src = e;
                        best_tar = f;
                        improvement = imp;
                        break;
                    }
                }
            }

            if (best_tar != null) {
                somethingatall = true;
                didsomething = true;
                output.removeEdge(towardsBackbone[best_src.getGraphIndex()]);
                SupportLink l = output.addEdge(best_src, best_tar);
                towardsBackbone[best_src.getGraphIndex()] = l;
            }

        } while (didsomething);

        return somethingatall;
    }

    private boolean computeSingleStar() {
        List<SupportNode> candidates = new ArrayList();
        for (ColoredPoint p : input.iterateExact(input.getColors())) {
            SupportNode n = output.getNodemap().get(p);
            candidates.add(n);
        }

        if (candidates.isEmpty()) {
            return false;
        }

        Random R = new Random();
        SupportNode center = candidates.get(R.nextInt(candidates.size()));

        for (ColoredPoint p : input.iterate()) {
            SupportNode n = output.getNodemap().get(p);
            if (n != center) {
                output.addEdge(n, center);
            }
        }

        return true;
    }

}
