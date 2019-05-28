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
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import nl.tue.geometrycore.util.Pair;
import rbptrees.data.ColoredPointSet;
import rbptrees.data.ColoredPointSet.ColoredPoint;
import rbptrees.data.SupportGraph;
import rbptrees.data.SupportGraph.SupportLink;

/**
 *
 * @author wmeulema
 */
public class DataSetIO {

    public static void write(File f, ColoredPointSet points, SupportGraph... graphs) {
        write(f, points, Arrays.asList(graphs));
    }

    private static void write(StringBuilder build, BufferedWriter write, String s) throws IOException {
        if (build != null) {
            build.append(s);
        }
        if (write != null) {
            write.write(s);
        }
    }

    public static String write(File f, ColoredPointSet points, List<SupportGraph> graphs) {

        try {
            StringBuilder build;
            BufferedWriter write;
            if (f == null) {
                write = null;
                build = new StringBuilder();
            } else {
                build = null;
                write = new BufferedWriter(new FileWriter(f));
            }

            write(build, write, points.size() + "\n");
            for (ColoredPoint p : points.getPoints()) {
                write(build, write, p.getX() + "\t" + p.getY());
                for (int c : p.colors) {
                    write(build, write, "\t" + c);
                }
                write(build, write, "\n");
            }

            if (graphs != null) {
                for (SupportGraph graph : graphs) {
                    write(build, write, graph.getName() + "\n");
                    write(build, write, graph.getEdges().size() + "\n");

                    for (SupportLink l : graph.getEdges()) {
                        write(build, write, points.getPoints().indexOf(l.getStart().point) + "\t"
                                + points.getPoints().indexOf(l.getEnd().point) + "\n");
                    }
                }
            }

            if (build != null) {
                return build.toString();
            } else {
                return null;
            }
        } catch (IOException ex) {
            Logger.getLogger(DataSetIO.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }

    public static Pair<ColoredPointSet, List<SupportGraph>> read(File f) {
        ColoredPointSet points = new ColoredPointSet();
        List<SupportGraph> supports = new ArrayList<>();

        try (BufferedReader read = new BufferedReader(new FileReader(f))) {

            int numpoints = Integer.parseInt(read.readLine());

            while (numpoints > 0) {
                numpoints--;
                String[] parts = read.readLine().split("\t");

                double px = Double.parseDouble(parts[0]);
                double py = Double.parseDouble(parts[1]);
                int[] colors = new int[parts.length - 2];
                for (int i = 2; i < parts.length; i++) {
                    colors[i - 2] = Integer.parseInt(parts[i]);
                }
                points.addPoint(px, py, colors);
            }

            String name = read.readLine();
            while (name != null) {
                SupportGraph graph = new SupportGraph(points, name);
                supports.add(graph);
                int numedges = Integer.parseInt(read.readLine());

                while (numedges > 0) {
                    numedges--;

                    String[] parts = read.readLine().split("\t");
                    int indexA = Integer.parseInt(parts[0]);
                    int indexB = Integer.parseInt(parts[1]);

                    graph.addEdge(graph.getNodemap().get(points.getPoints().get(indexA)),
                            graph.getNodemap().get(points.getPoints().get(indexB)));
                }

                name = read.readLine();
            }

        } catch (IOException ex) {
            Logger.getLogger(DataSetIO.class.getName()).log(Level.SEVERE, null, ex);
        }

        return new Pair<>(points, supports);

    }
}
