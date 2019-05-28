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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import nl.tue.geometrycore.geometry.Vector;
import nl.tue.geometrycore.geometry.linear.Rectangle;
import rbptrees.Utils;

public class ColoredPointSet {

    private List<ColoredPoint> points;
    private Set<Integer> colors;
    private Map<Integer, String> colornames;

    public ColoredPointSet() {
        points = new ArrayList<>();
        colors = new HashSet<>();
        colornames = new HashMap();
    }

    public ColoredPoint addPoint(double x, double y, int... colors) {
        ColoredPoint p = new ColoredPoint(x, y);
        p.setColors(colors);
        points.add(p);
        for (int c : colors) {
            this.colors.add(c);
        }
        return p;
    }

    public void minimizeColorNumbers() {
        List<Integer> cls = new ArrayList(colors);
        Collections.sort(cls);

        for (int i = 0; i < cls.size(); i++) {
            int k = cls.get(i);
            if (k != i) {
                for (ColoredPoint p : points) {
                    if (p.colors.remove(k)) {
                        p.colors.add(i);
                    }
                }
                colornames.put(i, colornames.remove(k));
                cls.set(i, i);
            }
        }
        colors.clear();
        colors.addAll(cls);
    }

    public Map<Integer, String> getColornames() {
        return colornames;
    }

    public ColoredPoint addPoint(Vector v, int... colors) {
        return addPoint(v.getX(), v.getY(), colors);
    }

    public Set<Integer> getColors() {
        return colors;
    }

    public void fit() {
        Rectangle r = new Rectangle(0, 100, 0, 100);

        Rectangle bb = Rectangle.byBoundingBox(points);

        Vector c = r.center();
        Vector delta = Vector.subtract(r.center(), bb.center());
        double scale = Math.min(r.width() / bb.width(), r.height() / bb.height());

        for (ColoredPoint p : points) {
            p.translate(delta);
            p.scale(scale, c);
        }

    }

    public Iterable<ColoredPoint> iterate() {
        return points;
    }

    public Iterable<ColoredPoint> iterate(ColorFilter filter) {
        return new Iterable<ColoredPointSet.ColoredPoint>() {
            @Override
            public Iterator<ColoredPoint> iterator() {
                return new Iterator<ColoredPointSet.ColoredPoint>() {
                    private int position = 0;

                    @Override
                    public boolean hasNext() {
                        // find next element that is accepted by our filter
                        while (position < points.size()
                                && !filter.test(points.get(position))) {
                            position++;
                        }
                        // check if element is in bounds
                        return (position < points.size());
                    }

                    @Override
                    public ColoredPoint next() {
                        if (!hasNext()) {
                            throw new NoSuchElementException();
                        }
                        return points.get(position++);
                    }
                };
            }
        };
    }

    /**
     * Iterate over all points of which the colors match the provided colors.
     */
    public Iterable<ColoredPoint> iterateExact(int... colors) {
        return iterateExact(Utils.intSet(colors));
    }

    /**
     * Iterate over all points of which the colors match the provided colors.
     */
    public Iterable<ColoredPoint> iterateExact(Set<Integer> colorsSet) {
        return iterate(new ColorFilter() {
            @Override
            public boolean test(ColoredPoint p) {
                return colorsSet.equals(p.colors);
            }
        });
    }

    /**
     * Iterate over all points of which the colors are a subset of the provided
     * colors.
     */
    public Iterable<ColoredPoint> iterateIntersection(int... colors) {
        return iterateIntersection(Utils.intSet(colors));
    }

    /**
     * Iterate over all points of which the colors are a subset of the provided
     * colors.
     */
    public Iterable<ColoredPoint> iterateIntersection(Set<Integer> colorsSet) {
        return iterate(new ColorFilter() {
            @Override
            public boolean test(ColoredPoint p) {
                return colorsSet.containsAll(p.colors);
            }
        });
    }

    /**
     * Iterate over all points which have at least one color of the provided
     * colors.
     */
    public Iterable<ColoredPoint> iterateUnion(int... colors) {
        return iterateUnion(Utils.intSet(colors));
    }

    /**
     * Iterate over all points which have at least one color of the provided
     * colors.
     */
    public Iterable<ColoredPoint> iterateUnion(Set<Integer> colorsSet) {
        return iterate(new ColorFilter() {
            @Override
            public boolean test(ColoredPoint p) {
                for (Integer c : colorsSet) {
                    if (p.colors.contains(c)) {
                        return true;
                    }
                }
                return false;
            }
        });
    }

    public int size() {
        return points.size();
    }

    public int size(ColorFilter filter) {
        return Utils.collectIterable(iterate(filter)).size();
    }

    public List<ColoredPoint> getPoints() {
        return points;
    }

    public void removeColor(int col) {
        colors.remove(col);
        colornames.remove(col);
        Iterator<ColoredPoint> it = points.iterator();
        while (it.hasNext()) {
            ColoredPoint p = it.next();
            p.colors.remove(col);
            if (p.colors.isEmpty()) {
                it.remove();
            }
        }
    }

    public class ColoredPoint extends Vector {

        public Set<Integer> colors;

        public ColoredPoint(double x, double y) {
            super(x, y);
            colors = new HashSet<>();
        }

        public void setColors(int[] colors) {
            this.colors.clear();
            for (int c : colors) {
                this.colors.add(c);
            }
        }

        @Override
        public String toString() {
            String s = "[ " + getX() + " " + getY() + " ; ";
            for (int col : colors) {
                s += col + " ";
            }
            return s + "]";
        }

        public boolean hasCommonColor(ColoredPoint point) {
            for (int c : colors) {
                if (point.colors.contains(c)) {
                    return true;
                }
            }
            return false;
        }

    }

    public interface ColorFilter {

        /**
         * Given a {@link ColoredPoint}, return whether it should be accepted
         * ({@code true}) or filtered out ({@code false}).
         */
        public boolean test(ColoredPoint p);
    }

}
