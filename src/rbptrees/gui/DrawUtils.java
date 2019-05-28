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
import nl.tue.geometrycore.geometry.BaseGeometry;
import nl.tue.geometrycore.geometry.Vector;
import nl.tue.geometrycore.geometry.curved.Circle;
import nl.tue.geometrycore.geometry.linear.Polygon;
import nl.tue.geometrycore.geometry.linear.Rectangle;
import nl.tue.geometrycore.geometryrendering.glyphs.GlyphFillMode;
import nl.tue.geometrycore.geometryrendering.glyphs.GlyphStrokeMode;
import nl.tue.geometrycore.geometryrendering.glyphs.PointStyle;

/**
 *
 * @author wmeulema
 */
public class DrawUtils {

    static final PointStyle disk = new PointStyle() {
        @Override
        public BaseGeometry getGlyphShape() {
            return new Circle(Vector.origin(), 1);
        }

        @Override
        public GlyphFillMode getFillMode() {
            return GlyphFillMode.FILL;
        }

        @Override
        public Color getStrokeColor() {
            return null;
        }

        @Override
        public GlyphStrokeMode getStrokeMode() {
            return GlyphStrokeMode.CLEAR;
        }
    };

    static final PointStyle verticalbar = new PointStyle() {

        @Override
        public BaseGeometry getGlyphShape() {
            return new Rectangle(-.2, .2, -1, 1);
        }

        @Override
        public GlyphFillMode getFillMode() {
            return GlyphFillMode.STROKE;
        }
    };

    static final PointStyle horizontalbar = new PointStyle() {

        @Override
        public BaseGeometry getGlyphShape() {
            return new Rectangle(-1, 1, -.2, .2);
        }

        @Override
        public GlyphFillMode getFillMode() {
            return GlyphFillMode.STROKE;
        }
    };

    static final PointStyle plus = new PointStyle() {

        @Override
        public BaseGeometry getGlyphShape() {
            return new Polygon(
                    new Vector(1, -.2),
                    new Vector(1, .2),
                    new Vector(.2, .2),
                    new Vector(.2, 1),
                    new Vector(-.2, 1),
                    new Vector(-.2, .2),
                    new Vector(-1, .2),
                    new Vector(-1, -.2),
                    new Vector(-.2, -.2),
                    new Vector(-.2, -1),
                    new Vector(.2, -1),
                    new Vector(.2, -0.2)
            );
        }

        @Override
        public GlyphFillMode getFillMode() {
            return GlyphFillMode.STROKE;
        }
    };
}
