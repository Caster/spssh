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

package rbptrees.data;

import java.util.ArrayList;
import rbptrees.data.ColoredPointSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nl.tue.geometrycore.geometry.linear.LineSegment;
import nl.tue.geometrycore.graphs.simple.SimpleEdge;
import nl.tue.geometrycore.graphs.simple.SimpleGraph;
import nl.tue.geometrycore.graphs.simple.SimpleVertex;
import rbptrees.data.ColoredPointSet.ColoredPoint;
import rbptrees.data.SupportGraph.SupportLink;
import rbptrees.data.SupportGraph.SupportNode;

public class SupportGraph extends SimpleGraph<LineSegment, SupportNode, SupportLink> {

    private final ColoredPointSet pointset;
    private final Map<ColoredPoint, SupportNode> nodemap;
    private final String name;

    public SupportGraph(ColoredPointSet pointset, String name) {
        this.pointset = pointset;
        this.nodemap = new HashMap<>();
        this.name = name;

        for (ColoredPoint p : pointset.iterate()) {
            nodemap.put(p, addVertex(p));
        }
    }

    public String getName() {
        return name;
    }

    public double getTotalLength() {
        double l = 0;
        for (SupportLink e : getEdges()) {
            l += e.toGeometry().length();
        }
        return l;
    }

    public int getIntersectionCount() {
        int cnt = 0;
        for (SupportLink e : getEdges()) {
            for (SupportLink f : getEdges()) {
                if (e.getGraphIndex() >= f.getGraphIndex()) {
                    continue;
                }
                if (e.getCommonVertex(f) != null) {
                    continue;
                }
                if (!e.toGeometry().intersect(f.toGeometry()).isEmpty()) {
                    cnt++;
                }
            }
        }
        return cnt;
    }

    public ColoredPointSet getPointset() {
        return pointset;
    }

    public Map<ColoredPoint, SupportNode> getNodemap() {
        return nodemap;
    }

    @Override
    public SupportNode createVertex(double x, double y) {
        return new SupportNode(x, y);
    }

    public SupportNode addVertex(ColoredPoint point) {
        SupportNode node = addVertex(point.getX(), point.getY());
        node.point = point;
        return node;
    }

    @Override
    public SupportLink createEdge() {
        return new SupportLink();
    }

    public SupportLink addEdge(SupportNode from, SupportNode to) {
        return this.addEdge(from, to, new LineSegment(from.clone(), to.clone()));
    }

    public class SupportNode extends SimpleVertex<LineSegment, SupportNode, SupportLink> {

        public ColoredPoint point;

        public SupportNode(double x, double y) {
            super(x, y);
        }

        @Override
        public String toString() {
            return "Node{" + getGraphIndex() + " " + point.toString() + "}";
        }

    }

    public class SupportLink extends SimpleEdge<LineSegment, SupportNode, SupportLink> {

        public List<Integer> getColors() {
            List<Integer> cols = new ArrayList<>(getStart().point.colors);
            cols.retainAll(getEnd().point.colors);
            return cols;
        }

        @Override
        public String toString() {
            String s = " ";
            for (int col : getColors()) {
                s += col + " ";
            }
            return "Link{" + getStart().getGraphIndex() + " " + getEnd().getGraphIndex() + " ; " + s + "}";
        }
    }

}
