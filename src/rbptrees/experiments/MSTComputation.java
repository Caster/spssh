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

package rbptrees.experiments;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;
import nl.tue.geometrycore.algorithms.EdgeWeightInterface;
import nl.tue.geometrycore.algorithms.mst.MinimumSpanningTree;
import nl.tue.geometrycore.geometry.linear.LineSegment;
import nl.tue.geometrycore.graphs.simple.SimpleEdge;
import nl.tue.geometrycore.graphs.simple.SimpleGraph;
import nl.tue.geometrycore.graphs.simple.SimpleVertex;

/**
 *
 * @author wmeulema
 */
public class MSTComputation {

    public static void main(String[] args) throws IOException {

        if (args.length <= 0) {
//            args = new String[]{
//                "D:\\Repos\\AGA\\planarrbpgraphs\\Experiments\\Data\\Martin_revision\\CPLEX\\results_78\\stats.csv",
//                null,
//                "output_",
//                "true"
//            };
            args = new String[]{
                //"D:\\Repos\\AGA\\planarrbpgraphs\\Experiments\\Data\\Martin_revision\\results_10_15",
                //"D:\\Repos\\AGA\\planarrbpgraphs\\Experiments\\Data\\Martin_revision\\CPLEX\\",
                //"D:\\Repos\\AGA\\planarrbpgraphs\\Experiments\\Data\\Martin\\Experiments\\results_martin_3\\",
                //"D:\\Repos\\AGA\\planarrbpgraphs\\Experiments\\Data\\Wouter\\Experiments\\SimpleExperiments\\",
                "D:\\Repos\\AGA\\planarrbpgraphs\\Experiments\\Data\\Wouter\\Experiments\\SimpleExperimentsSmall\\",
                null,
                "output_",
                "true"
            };
//            args = new String[]{
//                "C:\\Users\\wmeulema\\Dropbox\\Research\\RBPTrees\\TimeTrial results\\stats_skeleton.csv",
//                "C:\\Users\\wmeulema\\Dropbox\\Research\\RBPTrees\\TimeTrial results\\skeleton",
//                //"C:\\Users\\wmeulema\\Dropbox\\Research\\RBPTrees\\TimeTrial results\\stats_uniform.csv",
//                //"C:\\Users\\wmeulema\\Dropbox\\Research\\RBPTrees\\TimeTrial results\\uniform",
//                "output_",
//               "true"
//            };
        }

        boolean override = Boolean.parseBoolean(args[3]);

        if (args[0].endsWith(".csv")) {
            run(args[0],
                    args[1] == null || args[1].equals("null") ? new File(args[0]).getParent() : args[1],
                    args[2],
                    override);
        } else {
            List<File> errors = new ArrayList();
            File dir = new File(args[0]);
            for (File f : dir.listFiles()) {
                if (!f.isDirectory()) {
                    continue;
                }
                try {
                    if (!run(new File(f, "stats.csv").getPath(), f.getPath(), args[2], override)) {
                        errors.add(f);
                    }
                } catch (Exception ex) {
                    errors.add(f);
                    ex.printStackTrace();
                }
            }

            if (!errors.isEmpty()) {
                System.err.println("Errors in " + errors.size() + " folders");
                for (File f : errors) {
                    System.err.println("  " + f.getName());
                }
            }
        }
    }

    private static boolean run(String inputcsv, String zipfolder, String zipprefix, boolean override) throws IOException {

        System.err.println("INPUT: " + inputcsv);

        if (!override && new File(inputcsv.replace(".csv", "_augmented.csv")).exists()) {
            System.err.println("  skipping");
            return true;
        }

        // 0: input csv file
        // 1: folder with zips
        // 2: zipname prefix
        int zipfilecount = (new File(zipfolder)).listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.startsWith(zipprefix) && name.endsWith(".zip");
            }
        }).length;

        File[] zips = new File[zipfilecount];
        int[] zipstarts = new int[zipfilecount];
        int found = 0;
        int i = 1;
        while (found < zipfilecount) {
            File f = new File(zipfolder + "\\" + zipprefix + i + ".zip");
            if (f.exists()) {
                zips[found] = f;
                zipstarts[found] = i;
                found++;
            }
            i++;
        }

        System.err.println("Zip files:");
        for (int zi = 0; zi < zipfilecount; zi++) {
            System.err.println(zi + ":" + zips[zi].getName() + "\t" + zipstarts[zi]);
        }

        BufferedReader read = new BufferedReader(new FileReader(inputcsv));
        BufferedWriter write = new BufferedWriter(new FileWriter(inputcsv.replace(".csv", "_augmented.csv")));

        // headers
        String line = read.readLine();

        write.write(line);
        write.write(",MSTlength");
        write.newLine();

        line = read.readLine();
        int prevtrial = -1;
        String prevpath = null;
        double prevmstlength = -1;
        ZipFile zip = null;
        int zipindex = -1;

        while (line != null) {

            String[] split = line.split(",");
            // NodeID,DegreeDistribution,NumColors,PointDistribution,NumPoints,Trial,Algorithm,Length,Intersections,Time
            // OR (older versions)
            // NodeID,DegreeDistribution,NumColors,NumPoints,Trial,Algorithm,Length,Intersections,Time
            int trial;
            String path;
            if (split.length == 0) {
                // failed trial
                write.write(line);
                write.write(",");
                write.newLine();

                line = read.readLine();

                continue;
            } else if (split.length == 10) {
                // new version
                trial = Integer.parseInt(split[5]);
                path = split[3] + "/" + split[1] + "/" + split[2] + "/" + split[4];
            } else if (split.length == 9) {
                // old version
                trial = Integer.parseInt(split[4]);
                path = split[1] + "/" + split[2] + "/" + split[3];
            } else {
                System.err.println("Unexpected split length");
                write.close();
                return false;
            }

            if (trial != prevtrial && trial % 20 == 1) {
                System.err.println("Starting trial " + trial);
            }
            double mstlength;
            if (trial == prevtrial && path.equals(prevpath)) {
                mstlength = prevmstlength;
            } else {
                while (zipindex + 1 < zipfilecount && trial >= zipstarts[zipindex + 1]) {
                    zipindex++;
                    System.err.println("Setting zipfile: " + zips[zipindex].getName());
                    try {
                        zip = new ZipFile(zips[zipindex]);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        write.close();
                        return false;
                    }
                }


                //System.err.println("Trying path ["+path + "/" + trial + ".txt"+"]");
                BufferedReader zipread = new BufferedReader(new InputStreamReader(zip.getInputStream(zip.getEntry(path + "/" + trial + ".txt"))));

                Graph g = new Graph();

                int cnt = Integer.parseInt(zipread.readLine());

                for (int q = 0; q < cnt; q++) {
                    String[] zipline = zipread.readLine().split("\t");
                    double x = Double.parseDouble(zipline[0]);
                    double y = Double.parseDouble(zipline[1]);
                    g.addVertex(x, y);
                }

                //DelaunayTriangulation<Graph, LineSegment, Vertex, Edge> dt = new DelaunayTriangulation<>(g, (LineSegment geometry) -> geometry.clone());
                //if (!dt.run()) {
                for (Vertex v : g.getVertices()) {
                    for (Vertex u : g.getVertices()) {
                        if (u.getGraphIndex() < v.getGraphIndex()) {
                            g.addEdge(u, v, new LineSegment(u.clone(), v.clone()));
                        }
                    }
                }
                //}

                MinimumSpanningTree<Graph, LineSegment, Vertex, Edge> mst = new MinimumSpanningTree<>(g, EdgeWeightInterface.LENGTH_WEIGHTS);
                mst.computeMinimumSpanningForest();
                mstlength = mst.getWeightOfLastQuery();

                prevtrial = trial;
                prevpath = path;
                prevmstlength = mstlength;
            }

            write.write(line);
            write.write("," + mstlength);
            write.newLine();

            line = read.readLine();
        }

        write.close();
        read.close();

        return true;
    }

    private static class Edge extends SimpleEdge<LineSegment, Vertex, Edge> {

    }

    private static class Vertex extends SimpleVertex<LineSegment, Vertex, Edge> {

        public Vertex(double x, double y) {
            super(x, y);
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
}
