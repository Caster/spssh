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

package rbptrees.data.positioners;

import static rbptrees.experiments.DataGeneration.R;

import java.util.ArrayList;
import java.util.List;

import nl.tue.geometrycore.algorithms.delaunay.DelaunayTriangulation;
import nl.tue.geometrycore.algorithms.dsp.DijkstrasShortestPath;
import nl.tue.geometrycore.algorithms.mst.DirectedTreeNode;
import nl.tue.geometrycore.algorithms.mst.MinimumSpanningTree;
import nl.tue.geometrycore.geometry.Vector;
import nl.tue.geometrycore.geometry.linear.LineSegment;
import nl.tue.geometrycore.geometry.linear.Rectangle;
import nl.tue.geometrycore.graphs.simple.SimpleEdge;
import nl.tue.geometrycore.graphs.simple.SimpleGraph;
import nl.tue.geometrycore.graphs.simple.SimpleVertex;
import rbptrees.data.ColoredPointSet;
import rbptrees.data.ColoredPointSet.ColoredPoint;
import rbptrees.data.Positioner;

public class SkeletonPositioner extends Positioner {

    protected List<LineSegment> skeleton = null;


    public SkeletonPositioner(double scale) {
        super(scale);
    }


    @Override
    public void placeNext() {
        LineSegment skel = skeleton.get(R.nextInt(skeleton.size()));

        double along = (-0.1 + 1.2 * R.nextDouble()) * skel.length();
        double offset = 0.2 * R.nextGaussian() * skel.length();

        Vector pos = Vector.addSeq(
                skel.getStart(),
                Vector.multiply(along, skel.getDirection()),
                Vector.multiply(offset, Vector.rotate(skel.getDirection(), Math.PI / 2.0))
            );

        x = pos.getX();
        y = pos.getY();
    }


    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    protected void warmup(int numPoints) {
        super.warmup(numPoints);

        int nSkeleton = 5;
        int mSkeleton = 1;

        Graph mst = new Graph();
        while (nSkeleton > 0) {
            double x = scale * R.nextDouble();
            double y = scale * R.nextDouble();
            mst.addVertex(x, y);
            nSkeleton--;
        }
        if (!(new DelaunayTriangulation(mst, (ls) -> ls)).run()) {
            // make complete graph...
            for (int i = 0; i < mst.getVertices().size(); i++) {
                Vertex v = mst.getVertices().get(i);
                for (int j = i + 1; j < mst.getVertices().size(); j++) {
                    Vertex u = mst.getVertices().get(j);
                    mst.addEdge(u, v, new LineSegment(u.clone(), v.clone()));
                }
            }
        }
        MinimumSpanningTree<Graph, LineSegment, Vertex, Edge> mstcomp = new MinimumSpanningTree(mst, (e) -> {
            return e.getStart().squaredDistanceTo(e.getEnd());
        });
        DirectedTreeNode<Graph, LineSegment, Vertex, Edge> node = mstcomp.computeMinimumSpanningTree(mst.getVertices().get(0));

        skeleton = new ArrayList();
        List<Edge> mstedges = node.getAllEdgesInSubtree();
        for (Edge e : mstedges) {
            skeleton.add(e.toGeometry().clone());
        }
        if (mSkeleton > 0) {
            List<Edge> candidates = new ArrayList();
            for (Edge e : mst.getEdges()) {
                if (mstedges.contains(e)) {
                    // skip
                } else {
                    candidates.add(e);
                }
            }
            for (Edge e : candidates) {
                mst.removeEdge(e);
            }
            DijkstrasShortestPath dsp = new DijkstrasShortestPath(mst);

            while (mSkeleton > 0) {
                // find best dilation
                Edge best = null;
                double opt = 0;
                for (Edge e : candidates) {
                    double len = dsp.computeShortestPathLength(e.getStart(), e.getEnd());
                    double dil = len / e.getStart().distanceTo(e.getEnd());
                    if (dil > opt) {
                        opt = dil;
                        best = e;
                    }
                }
                if (best == null) {
                    break;
                }
                Edge e = mst.addEdge(best.getStart(), best.getEnd(), new LineSegment(best.getStart().clone(), best.getEnd().clone()));
                skeleton.add(e.toGeometry().clone());
                mSkeleton--;
            }
        }
    }

    @Override
    public void cooldown(ColoredPointSet points) {
        super.cooldown(points);

        // fit to scale/scale box
        Rectangle bb = Rectangle.byBoundingBox(points.getPoints());
        double s = scale / Math.max(bb.width(), bb.height());
        Vector d = bb.leftBottom();
        d.invert();

        for (ColoredPoint p : points.getPoints()) {
            p.translate(d);
            p.scale(s);
        }
    }




    private static class Graph extends SimpleGraph<LineSegment, Vertex, Edge> {

        @Override
        public Vertex createVertex(double x, double y) {
            return new Vertex(x, y);
        }

        @Override
        public Edge createEdge() {
            return new Edge();
        }

    }


    private static class Vertex extends SimpleVertex<LineSegment, Vertex, Edge> {

        public Vertex(double x, double y) {
            super(x, y);
        }

    }


    private static class Edge extends SimpleEdge<LineSegment, Vertex, Edge> {}

}
