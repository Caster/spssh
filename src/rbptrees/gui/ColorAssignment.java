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
import nl.tue.geometrycore.geometryrendering.styling.ExtendedColors;

/**
 *
 * @author wmeulema
 */
public class ColorAssignment {

    private static Color[] colors = new Color[]{ExtendedColors.darkOrange, ExtendedColors.darkBlue, ExtendedColors.darkPurple, ExtendedColors.darkGreen, ExtendedColors.darkRed,
        ExtendedColors.lightOrange, ExtendedColors.lightBlue, ExtendedColors.lightPurple, ExtendedColors.lightGreen, ExtendedColors.lightRed,
        Color.yellow, Color.magenta, Color.cyan, Color.pink, Color.red, Color.blue, Color.green};

    public static Color color(int c) {
        return colors[c % colors.length];
    }
}
