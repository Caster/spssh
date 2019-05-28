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

package rbptrees.io;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import rbptrees.data.ColoredPointSet;
import rbptrees.data.ColoredPointSet.ColoredPoint;

/**
 *
 * @author wmeulema
 */
public class CSV {

    public static ColoredPointSet load(File file) {
        return load(file, ",", "cell_id", "x", "y", "mover_id");
    }

    public static ColoredPointSet load(File file, String delim, String id, String x, String y, String set) {
        ColoredPointSet points = new ColoredPointSet();

        try (BufferedReader read = new BufferedReader(new FileReader(file))) {

            Map<String, ColoredPoint> pointmap = new HashMap();
            Map<String, Integer> colormap = new HashMap();
            int nextcolor = 0;

            String[] header = read.readLine().split(delim);
            int index_id = -1;
            int index_x = -1;
            int index_y = -1;
            int index_set = -1;

            for (int i = 0; i < header.length; i++) {
                if (header[i].toLowerCase().equals(id.toLowerCase())) {
                    index_id = i;
                } else if (header[i].toLowerCase().equals(x.toLowerCase())) {
                    index_x = i;
                } else if (header[i].toLowerCase().equals(y.toLowerCase())) {
                    index_y = i;
                } else if (header[i].toLowerCase().equals(set.toLowerCase())) {
                    index_set = i;
                }
            }

            String line = read.readLine();
            while (line != null) {
                String[] fields = line.split(delim);

                ColoredPoint p;
                if (pointmap.containsKey(fields[index_id])) {
                    p = pointmap.get(fields[index_id]);
                } else {
                    p = points.addPoint(Double.parseDouble(fields[index_x]), Double.parseDouble(fields[index_y]));
                    pointmap.put(fields[index_id], p);
                }
                int col;
                if (colormap.containsKey(fields[index_set])) {
                    col = colormap.get(fields[index_set]);
                } else {
                    col = nextcolor;
                    nextcolor++;
                    colormap.put(fields[index_set], col);
                    points.getColors().add(col);
                }

                p.colors.add(col);

                line = read.readLine();
            }

        } catch (IOException ex) {
            Logger.getLogger(CSV.class.getName()).log(Level.SEVERE, null, ex);
        }
        return points;
    }
}
