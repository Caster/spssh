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

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;

import nl.tue.geometrycore.geometry.BaseGeometry;
import nl.tue.geometrycore.geometry.Vector;
import nl.tue.geometrycore.geometry.curved.CircularArc;
import nl.tue.geometrycore.geometry.linear.LineSegment;
import nl.tue.geometrycore.geometry.linear.Polygon;
import nl.tue.geometrycore.geometry.linear.Rectangle;
import nl.tue.geometrycore.geometry.mix.GeometryCycle;
import nl.tue.geometrycore.geometryrendering.GeometryPanel;
import nl.tue.geometrycore.geometryrendering.glyphs.PointStyle;
import nl.tue.geometrycore.geometryrendering.styling.Dashing;
import nl.tue.geometrycore.geometryrendering.styling.ExtendedColors;
import nl.tue.geometrycore.geometryrendering.styling.Hashures;
import nl.tue.geometrycore.geometryrendering.styling.SizeMode;
import nl.tue.geometrycore.geometryrendering.styling.TextAnchor;
import nl.tue.geometrycore.io.ipe.IPEWriter;
import rbptrees.data.ColoredPointSet.ColoredPoint;
import rbptrees.data.SupportGraph.SupportLink;
import rbptrees.data.SupportGraph.SupportNode;

/**
 *
 * @author wmeulema
 */
public class DrawPanel extends GeometryPanel {

    final Data data;

    public DrawPanel(Data data) {
        this.data = data;
        data.draw = this;
    }

    public enum RenderMode {
        PARALLEL, KELP, STRIPED_KELP, MATRIX;
        private static RenderMode[] vals = values();

        public RenderMode next() {
            return vals[(this.ordinal() + 1) % vals.length];
        }
    }

    RenderMode mode = RenderMode.PARALLEL;

    ColoredPoint drag = null;
    int dragset = -1;
    int dragontoset = -1;
    double s = 2.5;

    double pointsize = 6;
    double linkwidth = 3;

    boolean labels = false;
    boolean first = true;
    IPEWriter write = null;

    private double writeConvert(double d) {
        if (write == null) {
            return d;
        } else {
            return convertViewToWorld(d);
        }
    }

    private void drawColoredPoint(ColoredPoint p) {

        setFill(ExtendedColors.white, Hashures.SOLID);
        setPointStyle(DrawUtils.disk, writeConvert(s * (pointsize + 1)));
        draw(p);

        int n = p.colors.size();
        if (n == 1) {

            setFill(ColorAssignment.color(p.colors.iterator().next()), Hashures.SOLID);
            setPointStyle(DrawUtils.disk, writeConvert(s * pointsize));
            draw(p);

        } else {
            double l = convertViewToWorld(pointsize * s);

            double sweep = Math.toRadians(360.0 / n);
            Vector arm = new Vector(l, 0);
            Vector p1 = Vector.add(p, arm);
            arm.rotate(sweep);
            Vector p2 = Vector.add(p, arm);

            @SuppressWarnings({"unchecked", "rawtypes"})
            BaseGeometry geom = new GeometryCycle(
                    new LineSegment(p, p1),
                    new CircularArc(p, p1, p2, true),
                    new LineSegment(p2, p)
            );

            for (int c : p.colors) {
                setStroke(null, 1, Dashing.SOLID);
                setFill(ColorAssignment.color(c), Hashures.SOLID);
                draw(geom);
                p1.rotate(sweep, p);
                p2.rotate(sweep, p);
            }
        }

        if (data.activeSupport != null && labels) {
            SupportNode sn = data.activeSupport.findVertex(p);
            if (sn != null) {
                setStroke(Color.black, 1, Dashing.SOLID);
                setTextStyle(TextAnchor.LEFT, 12);
                draw(Vector.add(p, new Vector(0.5 * pointsize + 0.1, 0)), "" + sn.getGraphIndex());
            }
        }
    }

    private void drawColoredLink(SupportLink link) {
        List<Integer> cols = link.getColors();

        int n = cols.size();

        setFill(null, Hashures.SOLID);

        if (n == 0) {
            setStroke(Color.black, writeConvert(s * linkwidth), Dashing.SOLID);
            draw(link);
        } else if (n == 1) {
            setStroke(ColorAssignment.color(cols.get(0)), writeConvert(s * linkwidth), Dashing.SOLID);
            draw(link);
        } else {
            LineSegment ls = link.toGeometry().clone();
            Vector delta = ls.getDirection();
            delta.rotate90DegreesClockwise();
            double d = convertViewToWorld(s * linkwidth);
            delta.scale(d);
            ls.translate(Vector.multiply(-0.5 * (n - 1), delta));

            for (int col : cols) {
                setStroke(ColorAssignment.color(col), writeConvert(s * linkwidth), Dashing.SOLID);
                draw(ls);
                ls.translate(delta);
            }
        }
    }


     private void drawColoredLinkStriped(SupportLink link) {
        List<Integer> cols = link.getColors();

        int n = cols.size();

        setFill(null, Hashures.SOLID);

        if (n == 0) {
            setStroke(Color.black, writeConvert(s * linkwidth), Dashing.SOLID);
            draw(link);
        } else if (n == 1) {
            setStroke(ColorAssignment.color(cols.get(0)), writeConvert(s * linkwidth), Dashing.SOLID);
            draw(link);
        } else {
            int f = 4;
            LineSegment ls = link.toGeometry().clone();
            Vector delta = Vector.subtract(ls.getEnd(), ls.getStart());
            delta.scale(1.0/(f*n));

            int c = 0;
            for (int i = 0; i < f*n; i++) {
                setStroke(ColorAssignment.color(c), writeConvert(s * linkwidth), Dashing.SOLID);
                draw(LineSegment.byStartAndOffset(Vector.add(ls.getStart(), Vector.multiply(i, delta)), delta));
                c = (c+1)%cols.size();
            }
        }
    }


    @Override
    protected void drawScene() {

        if (first) {
            zoomToFit();
            first = false;
        }

        if (write != null) {
            setSizeMode(SizeMode.WORLD);
        } else {
            setSizeMode(SizeMode.VIEW);
        }

        if (write != null) {
            write.setLayer("Support");
        }
        if (data.activeSupport != null) {
            switch (mode) {
                default:
                case PARALLEL:
                    for (SupportLink l : data.activeSupport.getEdges()) {
                        drawColoredLink(l);
                    }
                    break;
                case STRIPED_KELP:
                    for (SupportLink l : data.activeSupport.getEdges()) {
                        drawColoredLinkStriped(l);
                    }
                    break;
                case KELP:
                    int cnt = data.pointset.getColors().size();
                    double colnum = 0;
                    for (int c : data.pointset.getColors()) {
                        double f = Math.sqrt(1 - colnum / cnt);

                        double vtxsize;
                        double linksize;
                        double smoothradius;

                        vtxsize = convertViewToWorld(s * 2 * pointsize * f);
                        linksize = convertViewToWorld(s * 3 * linkwidth * f);
                        smoothradius = convertViewToWorld(s * pointsize * 0.5);

                        KelpRenderer render = new KelpRenderer();
                        BaseGeometry<?> geom = render.computeGeometry(data.activeSupport, c, vtxsize, linksize, smoothradius);

                        setStroke(null, s * 0.05, Dashing.SOLID);
                        setFill(ColorAssignment.color(c), Hashures.SOLID);
                        setAlpha(0.8);
                        draw(geom);

                        setStroke(ExtendedColors.white, writeConvert(s * 0.05), Dashing.SOLID);
                        setFill(null, Hashures.SOLID);
                        setAlpha(1);
                        draw(geom);

                        colnum++;
                    }
                    break;
                case MATRIX:
                    break;
            }
        }

        if (drag != null && write == null) {
            setStroke(ExtendedColors.lightRed, writeConvert(s * 1), Dashing.SOLID);
            setPointStyle(PointStyle.CIRCLE_SOLID, writeConvert(s * (pointsize + 4)));
            draw(drag);
        }

        if (write != null) {
            write.setLayer("Points");
        }

        switch (mode) {
            default:
            case STRIPED_KELP:
            case PARALLEL:
                for (ColoredPoint p : data.pointset.getPoints()) {
                    drawColoredPoint(p);
                }
                break;
            case KELP:
                setStroke(ExtendedColors.black, writeConvert(s * 1), Dashing.SOLID);
                setPointStyle(PointStyle.CIRCLE_SOLID, writeConvert(s * pointsize * 0.25));
                draw(data.pointset.getPoints());
                break;
            case MATRIX:
                int y = 0;
                for (ColoredPoint p : data.pointset.getPoints()) {
                    setStroke(Color.white, 0.5, Dashing.SOLID);
                    for (int col : data.pointset.getColors()) {

                        Vector v = new Vector(12 * col, y);
                        if (p.colors.contains(col)) {
                            setFill(ColorAssignment.color(col), Hashures.SOLID);
                            if (col == dragset) {
                                draw(new Polygon(Vector.add(v, new Vector(10, 10)), Vector.add(v, new Vector(10, 0)), Vector.add(v, new Vector(0, 10))));
                            } else {
                                draw(Rectangle.byCornerAndSize(v, 10, 10));
                            }
                        }

                        if (col == dragontoset && p.colors.contains(dragset)) {
                            setFill(ColorAssignment.color(dragset), Hashures.SOLID);
                            draw(new Polygon(v, Vector.add(v, new Vector(10, 0)), Vector.add(v, new Vector(0, 10))));
                        }
                    }
                    y += 12;
                }

//                Vector v = new Vector(0, 0);
//                setStroke(null, 1, Dashing.SOLID);
//                for (int col : data.pointset.getColors()) {
//                    setFill(ColorAssignment.color(col), Hashures.SOLID);
//                    draw(Rectangle.byCornerAndSize(v, 10, 10));
//
//                    v.translate(12, 0);
//                }
                break;
        }
    }

    @Override
    public Rectangle getBoundingRectangle() {
        if (mode == RenderMode.MATRIX) {
            return Rectangle.byCornerAndSize(Vector.origin(), 12 * data.pointset.getColors().size(), 12 * data.pointset.getPoints().size());
        } else {
            return Rectangle.byBoundingBox(data.box);
        }
    }

    @Override
    protected void mousePress(Vector loc, int button, boolean ctrl, boolean shift, boolean alt) {
        if (button == MouseEvent.BUTTON1) {
            switch (mode) {
                case MATRIX:
                    dragset = (int) Math.floor(loc.getX() / 12);
                    if (!data.pointset.getColors().contains(dragset)) {
                        dragset = -1;
                    }
                    dragontoset = dragset;
                    break;
                default:
                    double thres = this.convertViewToWorld(s * pointsize);
                    drag = null;
                    for (ColoredPoint e : data.pointset.getPoints()) {
                        if (e.distanceTo(loc) < thres) {
                            drag = e;
                        }
                    }

                    repaint();
                    break;
            }
        }
    }

    @Override
    protected void mouseDrag(Vector loc, Vector prevloc, int button, boolean ctrl, boolean shift, boolean alt) {
        if (drag != null) {
            drag.set(loc);
            if (shift) {
                resetSupports();
            }
            repaint();
        } else if (dragset >= 0) {
            dragontoset = (int) Math.floor(loc.getX() / 12);
            if (!data.pointset.getColors().contains(dragontoset)) {
                dragontoset = -1;
            }
            repaint();
        }
    }

    @Override
    protected void mouseRelease(Vector loc, int button, boolean ctrl, boolean shift, boolean alt) {
        if (drag != null) {
            drag = null;
            resetSupports();
            repaint();
        } else if (dragset >= 0) {
            data.performSetOperation(dragset, dragontoset);
            dragset = -1;
            dragontoset = -1;
            repaint();
        }
    }

    @Override
    protected void keyPress(int keycode, boolean ctrl, boolean shift, boolean alt) {
        switch (keycode) {
            case KeyEvent.VK_M:
                mode = mode.next();
                zoomToFit();
                break;

            case KeyEvent.VK_R:
                data.generateRandom(10, 10, 10);
                zoomToFit();
                break;

            // plus key in Java... ouch
            case KeyEvent.VK_EQUALS:
                if (!shift) {
                    break;
                }
            case KeyEvent.VK_ADD:
            case KeyEvent.VK_PLUS:
                s += 0.5;
                repaint();
                break;

            case KeyEvent.VK_MINUS:
                s = Math.max(0.5, s - 0.5);
                repaint();
                break;
            case KeyEvent.VK_L:
                labels = !labels;
                repaint();
                break;
        }
    }

    private void resetSupports() {
        for (int i = data.supports.size() - 1; i >= 1; --i) {
            data.supports.remove(i);
        }
        data.keepMSTstar();
        data.refreshUI();
    }

}
