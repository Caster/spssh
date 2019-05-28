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

package rbptrees.gui;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import nl.tue.geometrycore.geometry.BaseGeometry;
import static nl.tue.geometrycore.geometry.GeometryType.POLYLINE;
import static nl.tue.geometrycore.geometry.GeometryType.VECTOR;

import nl.tue.geometrycore.geometry.Vector;
import nl.tue.geometrycore.geometry.linear.LineSegment;
import nl.tue.geometrycore.geometry.linear.PolyLine;
import nl.tue.geometrycore.geometry.linear.Rectangle;
import nl.tue.geometrycore.geometry.mix.GeometryGroup;
import nl.tue.geometrycore.io.ReadItem;
import rbptrees.algo.Algorithm;
import rbptrees.algo.ProgressListener;
import rbptrees.algo.SpanningTreeHeuristic;
import rbptrees.algo.ThreadableAlgorithm;
import rbptrees.data.ColoredPointSet;
import rbptrees.data.ColoredPointSet.ColoredPoint;
import rbptrees.data.SupportGraph;
import rbptrees.io.CSV;
import rbptrees.io.DataSetIO;

/**
 *
 * @author wmeulema
 */
public class Data {

    public static final int DEFAULT_NUM_POINTS = 5;

    public DrawPanel draw = null;
    public SidePanel side = null;

    public ColoredPointSet pointset = null;
    public final List<SupportGraph> supports = new ArrayList<>();
    public SupportGraph activeSupport = null;
    public Rectangle box = null;

    SupportGraph starSupport = null;

    private ProgressListener algListener = new ProgressListener() {
        @Override
        public boolean shouldAbort(double gap, double secondsRunning) {
            return shouldAbort;
        }

        @Override
        public void onProgress(double gap, double secondsRunning) {
        }

        @Override
        public void onDone(double secondsRunning) {
            if (runningAlgorithm.getOutput() != null) {
                addSupport(runningAlgorithm.getOutput());
            }
            onAbort();
        }

        @Override
        public void onAbort() {
            if (runningAlgorithm instanceof ThreadableAlgorithm) {
                ((ThreadableAlgorithm) runningAlgorithm).removeListener(algListener);
                side.setCancelVisible(false);
            }
            runningAlgorithm = null;
            shouldAbort = false;
        }
    };
    private Algorithm runningAlgorithm = null;
    private boolean shouldAbort = false;

    public Data() {
        generateRandom(DEFAULT_NUM_POINTS, DEFAULT_NUM_POINTS, DEFAULT_NUM_POINTS);
    }

    public void addSupport(SupportGraph support) {
        activeSupport = support;
        supports.add(activeSupport);
        draw.repaint();
        side.makeSupport(activeSupport);
    }

    public void runAlgorithm(Algorithm alg) {
        alg.initialize(pointset);
        if (alg instanceof ThreadableAlgorithm) {
            if (runningAlgorithm != null) {
                // TODO: this could be nicer... but shouldn't really happen in practice anyway
                System.err.println("Cannot run algorithm, another one is still running.");
            } else {
                runningAlgorithm = alg;
                ((ThreadableAlgorithm) alg).addListener(algListener);
                ((ThreadableAlgorithm) alg).setStatus("Started algorithm");
                side.setCancelVisible(true);
                alg.run();
            }
        } else /* synchronous */ if (alg.run()) {
            runningAlgorithm = alg;
            algListener.onDone(-1);
        }
    }

    public void cancelAlgorithm() {
        if (runningAlgorithm != null) {
            shouldAbort = true;
        }
    }

    public void clear() {
        pointset = new ColoredPointSet();
        supports.clear();
        activeSupport = null;
        box = null;
    }

    public void refreshUI() {
        if (draw != null) {
            draw.repaint();
        }
        if (side != null) {
            side.refresh();
        }
    }

    public void readFrom(List<ReadItem> items) {

        clear();

        for (ReadItem item : items) {
            Vector v = Rectangle.byBoundingBox(item).center();
            if (item.getLayer().equalsIgnoreCase("red")) {
                //System.err.println("r");
                pointset.addPoint(v, 0);
            } else if (item.getLayer().equalsIgnoreCase("blue")) {
                //System.err.println("b");
                pointset.addPoint(v, 1);
            } else {
                //System.err.println("P");
                pointset.addPoint(v, 0, 1);
            }
        }

        newDataSet();
    }

    public void readIpeHypergraph(List<ReadItem> items) {

        clear();

        List<String> layers = new ArrayList();

        for (ReadItem item : items) {
            String layer = item.getLayer();
            //System.err.println("Layer: "+l);
            if (layer.toLowerCase().equals("nodes") || layer.toLowerCase().equals("map") || layer.toLowerCase().equals("background") || layer.toLowerCase().equals("backdrop")) {
                continue;
            }

            //System.out.println("layer: "+layer);
            int colorindex = layers.indexOf(layer);
            if (colorindex < 0) {
                colorindex = layers.size();
                layers.add(layer);
                pointset.getColors().add(colorindex);
                pointset.getColornames().put(colorindex, layer);
            }
            //System.err.println("  Color: "+colorindex);
            List<Vector> vtcs = new ArrayList();
            decompose(vtcs, item.toGeometry());
            nextvtx:
            for (Vector v : vtcs) {
                //System.out.println("  "+v);
                for (ColoredPoint p : pointset.getPoints()) {
                    if (p.isApproximately(v, 0.001)) {
                        //System.out.println("    exists");
                        p.colors.add(colorindex);
                        continue nextvtx;
                    }
                }

                //System.out.println("    new");
                pointset.addPoint(v, colorindex);
            }
        }

        //System.err.println("Pointset: "+pointset.size());
        pointset.fit();

        newDataSet();
    }

    private void decompose(List<Vector> vtcs, BaseGeometry geom) {

        switch (geom.getGeometryType()) {
            case POLYLINE:
                vtcs.addAll(((PolyLine) geom).vertices());
                break;
            case VECTOR:
                vtcs.add((Vector) geom);
                break;
            case LINESEGMENT:
                vtcs.add(((LineSegment) geom).getStart());
                vtcs.add(((LineSegment) geom).getEnd());
                break;
            case GEOMETRYGROUP:
                for (BaseGeometry<?> part : ((GeometryGroup<?>) geom).getParts()) {
                    decompose(vtcs, part);
                }
                break;
            default:
                System.err.println("  geom: " + geom.getGeometryType());
                break;
        }
    }

    public void generateRandom(int R, int B, int P) {

        clear();

        double w = 100;
        double h = 100;

        while (R > 0) {
            pointset.addPoint(w * Math.random(), h * Math.random(), 0);
            R--;
        }

        while (B > 0) {
            pointset.addPoint(w * Math.random(), h * Math.random(), 1);
            B--;
        }

        while (P > 0) {
            pointset.addPoint(w * Math.random(), h * Math.random(), 0, 1);
            P--;
        }

        newDataSet();
    }

    public void newDataSet() {

        box = Rectangle.byBoundingBox(pointset.getPoints());
        keepMSTstar();
        refreshUI();
    }

    public void keepMSTstar() {

        Algorithm alg = new SpanningTreeHeuristic(false,true);
        alg.initialize(pointset);
        if (alg.run()) {
            activeSupport = alg.getOutput();

            if (activeSupport != null) {
                if (starSupport != null && supports.size() > 0) {
                    supports.set(0, activeSupport);
                } else {
                    supports.add(0, activeSupport);
                }
            }
            starSupport = activeSupport;

        } else if (starSupport != null) {
            supports.remove(starSupport);
            starSupport = null;
        }

    }

    public void load(ColoredPointSet cps, List<SupportGraph> sgs) {
        pointset = cps;
        supports.clear();
        activeSupport = null;
        starSupport = null;
        for (SupportGraph sg : sgs) {
            supports.add(sg);
        }
        box = Rectangle.byBoundingBox(pointset.getPoints());
        draw.zoomToFit();
        refreshUI();
    }

    public void save(File f) {
        DataSetIO.write(f, pointset, supports);
    }

    public void readCSV(File file) {
        clear();
        pointset = CSV.load(file);
        pointset.fit();
        newDataSet();
    }

    public void readCSVmereke(File file) {
        clear();
        pointset = CSV.load(file, ",", "\"id\"", "\"lon\"", "\"lat\"", "\"set\"");
        pointset.fit();
        newDataSet();
    }

    public void performSetOperation(int dragset, int dragontoset) {
        if (!pointset.getColors().contains(dragset) || dragset == dragontoset) {
            return;
        }
        supports.clear();
        activeSupport = null;
        if (pointset.getColors().contains(dragontoset)) {
            // merge!
            for (ColoredPoint p : pointset.getPoints()) {
                if (p.colors.remove(dragset)) {
                    p.colors.add(dragontoset);
                }
            }
        }
        pointset.removeColor(dragset);
        pointset.minimizeColorNumbers();
        newDataSet();
    }

}
