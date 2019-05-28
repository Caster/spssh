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

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryCollection;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.MultiPolygon;
import java.util.ArrayList;
import java.util.List;
import nl.tue.geometrycore.geometry.BaseGeometry;
import nl.tue.geometrycore.geometry.Vector;
import nl.tue.geometrycore.geometry.linear.PolyLine;
import nl.tue.geometrycore.geometry.linear.Polygon;
import nl.tue.geometrycore.geometry.mix.GeometryGroup;
import rbptrees.data.SupportGraph;
import rbptrees.data.SupportGraph.SupportLink;
import rbptrees.data.SupportGraph.SupportNode;

/**
 *
 * @author wmeulema
 */
public class KelpRenderer {

    private Geometry g;
    private GeometryFactory gf;

    public KelpRenderer() {
        gf = new GeometryFactory();
        g = null;
    }

    public Geometry getJTSGeometry() {
        return g;
    }

    public GeometryFactory getJTSGeometryFactory() {
        return gf;
    }

    private Geometry geometrycore2JTS(Polygon sp) {
        Coordinate[] coords = new Coordinate[sp.vertexCount() + 1];
        //System.err.println("adding polygon");
        for (int i = 0; i < coords.length - 1; i++) {
            //System.err.println("  "+sp.vertex(i));
            coords[i] = new Coordinate(sp.vertex(i).getX(), sp.vertex(i).getY());
        }
        coords[sp.vertexCount()] = coords[0];
        Geometry spg = gf.createPolygon(gf.createLinearRing(coords), new LinearRing[0]);
        return spg;
    }

    public void addPolygon(Polygon... sps) {
        for (Polygon sp : sps) {
            Geometry spg = geometrycore2JTS(sp);
            if (g == null) {
                g = spg;
            } else {
                g = g.union(spg);
            }
        }
    }

    public void subtractPolygon(Polygon... sps) {
        //System.err.println("call subtract: ");
        if (g != null) {
            for (Polygon sp : sps) {
                Geometry spg = geometrycore2JTS(sp);

                //System.err.println("  spg: " + spg.getGeometryType());
                //System.err.println("  g: " + g.getGeometryType());
                g = g.difference(spg);
                if (g == null) {
                    break;
                }
                //System.err.println("  g: " + g.getGeometryType());
                if (g.getGeometryType().equals("GeometryCollection")) {
                    List<com.vividsolutions.jts.geom.Polygon> polygons = new ArrayList();
                    //System.err.println("  num geom: " + g.getNumGeometries());
                    for (int i = 0; i < g.getNumGeometries(); i++) {
                        //System.err.println("    " + g.getGeometryN(i).getGeometryType());
                        if (g.getGeometryN(i).getGeometryType().equals("Polygon")) {
                            polygons.add((com.vividsolutions.jts.geom.Polygon) g.getGeometryN(i));
                        }
                    }
                    if (polygons.isEmpty()) {
                        g = null;
                    } else if (polygons.size() == 1) {
                        g = polygons.get(0);
                    } else {
                        g = new MultiPolygon(polygons.toArray(new com.vividsolutions.jts.geom.Polygon[polygons.size()]), gf);
                    }
                }
            }
        }
    }

    public void smooth(double range) {
        g = g.buffer(range);
        g = g.buffer(-range);
    }

    public GeometryGroup getResult() {
        List<Polygon> sps = new ArrayList<>();
        breakDownToPolygons(g, sps);
        return new GeometryGroup(sps.toArray(new Polygon[0]));
    }

    private void breakDownToPolygons(Geometry g, List<Polygon> sps) {
        if (g instanceof LineString) {
            sps.add(ringToPolygon((LineString) g));
        } else if (g instanceof com.vividsolutions.jts.geom.Polygon) {
            com.vividsolutions.jts.geom.Polygon p = (com.vividsolutions.jts.geom.Polygon) g;
            sps.add(ringToPolygon(p.getExteriorRing()));
            for (int i = 0; i < p.getNumInteriorRing(); i++) {
                sps.add(ringToPolygon(p.getInteriorRingN(i)));
            }
        } else if (g instanceof GeometryCollection) {
            GeometryCollection gc = (GeometryCollection) g;
            for (int i = 0; i < gc.getNumGeometries(); i++) {
                breakDownToPolygons(gc.getGeometryN(i), sps);
            }
        } else {
            throw new UnsupportedOperationException("Geometry class: " + g.getClass().getSimpleName());
        }
    }

    private Polygon ringToPolygon(LineString r) {
        Vector[] vs = new Vector[r.getNumPoints()];
        for (int i = 0; i < vs.length; i++) {
            vs[i] = new Vector(r.getPointN(i).getX(), r.getPointN(i).getY());
        }
        return new Polygon(vs);
    }

    public boolean hasShape() {
        return g != null;
    }

    public void addBufferedLine(PolyLine r, double thick) {
        if (r.vertexCount() <= 1) {
            System.out.println("Empty?");
            return;
        }

        Coordinate[] coords = new Coordinate[r.vertexCount()];
        for (int i = 0; i < coords.length; i++) {
            coords[i] = new Coordinate(r.vertex(i).getX(), r.vertex(i).getY());
        }
        Geometry rg = gf.createLineString(coords);
        rg = rg.buffer(thick / 2.0);
        if (g == null) {
            g = rg;
        } else {
            g = g.union(rg);
        }
    }

    public void addBufferedPoint(Vector v, double noderadius) {
        Geometry pg = gf.createPoint(new Coordinate(v.getX(), v.getY()));
        pg = pg.buffer(noderadius);
        if (g == null) {
            g = pg;
        } else {
            g = g.union(pg);
        }
    }

    public void subtractBufferedPoint(Vector v, double noderadius) {
        Geometry pg = gf.createPoint(new Coordinate(v.getX(), v.getY()));
        pg = pg.buffer(noderadius);
        if (g == null) {
            g = pg;
        } else {
            g = g.difference(pg);
        }
    }

    BaseGeometry computeGeometry(SupportGraph support, int color, double vtxsize, double linkwidth, double smooth) {

        for (SupportNode n : support.getVertices()) {
            if (n.point.colors.contains(color)) {
                addBufferedPoint(n, vtxsize);
            }
        }

        for (SupportLink l : support.getEdges()) {
            if (l.getStart().point.colors.contains(color) && l.getEnd().point.colors.contains(color)) {
                addBufferedLine(new PolyLine(l.getStart(), l.getEnd()), linkwidth);
            }
        }

        if (hasShape()) {
            smooth(smooth);

//            for (SupportNode n : support.getVertices()) {
//                if (!n.point.colors.contains(color)) {
//                    subtractBufferedPoint(n, vtxsize);
//                }
//            }

            return getResult();
        } else {
            return null;
        }
    }
}
