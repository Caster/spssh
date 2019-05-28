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

package rbptrees.cli;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nl.tue.geometrycore.io.ipe.IPEWriter;

import nl.tue.geometrycore.util.Pair;
import rbptrees.algo.Algorithm;
import rbptrees.algo.BruteForceSolver;
import rbptrees.algo.BruteForceWithInit;
import rbptrees.algo.IntegerLinearProgram;
import rbptrees.algo.LocalSearch;
import rbptrees.algo.MSTApprox;
import rbptrees.algo.MSTIteration;
import rbptrees.algo.SpanningTreeHeuristic;
import rbptrees.algo.ThreadableAlgorithm;
import rbptrees.data.ColoredPointSet;
import rbptrees.data.SupportGraph;
import rbptrees.gui.Data;
import rbptrees.gui.DrawPanel;
import rbptrees.io.DataSetIO;

public class BatchRunner {

    public static final Algorithm[] ALGOS = new Algorithm[] {
        new SpanningTreeHeuristic(true, false),
        new SpanningTreeHeuristic(false, false),
        new IntegerLinearProgram(),
        new MSTApprox(),
        new MSTIteration(),
        new LocalSearch(),
        new BruteForceSolver(false, false),
        new BruteForceWithInit()
    };
    public static final Algorithm DEFAULT = ALGOS[1];


    private List<File> inputs;


    /**
     * Construct a new runner that will assume the given arguments to contain
     * one or more paths to files that contain instructions on what to do.
     *
     * @param args Commandline arguments.
     * @see BatchIO
     */
    public BatchRunner(String[] args) {
        this.inputs = new ArrayList<>();
        for (String arg : args) {
            File input = new File(arg);
            if (input.exists() && input.isFile() && input.canRead()) {
                this.inputs.add(input);
            } else if (input.exists() && input.isDirectory()) {
                for (File inputFile : input.listFiles()) {
                    this.inputs.add(inputFile);
                }
            } else {
                System.err.println(String.format(
                    "Cannot find, open or read '%s'", arg));
            }
        }
    }

    public void run() throws FileNotFoundException {
        DrawPanel draw = new DrawPanel(new Data());
        draw.setSize(500, 500);
        for (File input : inputs) {
            BatchIO io = new BatchIO(input);
            File outDir = new File(input.getParent() + File.separator +
                    "Results");

            // preparation
            List<Algorithm> algos = io.getAlgorithms();
            List<String> pointSets = io.getInputs();
            System.out.println(String.format("running %d algorithm%s on %d input%s",
                    algos.size(), (algos.size() == 1 ? "" : "s"),
                    pointSets.size(), (pointSets.size() == 1 ? "" : "s")));
            int maxLen = Integer.MIN_VALUE;
            for (Algorithm algo : algos) {
                String name = algo.getSolutionIdentifier();
                if (name.length() > maxLen) {
                    maxLen = name.length();
                }
            }

            // run algorithms on given inputs
            String inputDir = input.getParentFile().getParent() +
                    File.separator + "Inputs" + File.separator;
            Map<Pair<Algorithm, String>, Result> results = new HashMap<>();
            for (Algorithm algo : algos) {
                System.out.print(String.format("%-" + maxLen + "s ..",
                        algo.getSolutionIdentifier()));
                for (String pointSetPath : pointSets) {
                    File[] pointSetFiles = new File[] {new File(inputDir + pointSetPath)};
                    if (pointSetFiles[0].exists() && pointSetFiles[0].isDirectory()) {
                        pointSetFiles = pointSetFiles[0].listFiles();
                    }
                    for (File pointSetFile : pointSetFiles) {
                        Pair<ColoredPointSet, List<SupportGraph>> ds =
                                DataSetIO.read(pointSetFile);
                        algo.initialize(ds.getFirst());
                        long time = System.currentTimeMillis();
                        if (algo instanceof ThreadableAlgorithm) {
                            ((ThreadableAlgorithm) algo).runSync();
                        } else {
                            algo.run();
                        }
                        long took = System.currentTimeMillis() - time;
                        System.out.print(".");
                        results.put(new Pair<>(algo, pointSetPath),
                                new Result(algo.getOutput(), took));
                        // write an Ipe file
                        try (IPEWriter write = IPEWriter.fileWriter(
                                new File(outDir, pointSetPath + " - " +
                                algo.getSolutionIdentifier() + ".ipe"))) {
                            write.initialize();
                            // hacky to not make page size change
                            //write.setView(Rectangle.byCenterAndSize(new Vector(32, 32), 384, 384));
                            //write.setWorldview(getBoundingRectangle());
                            write.newPage("Support", "Points");
                            draw.render(write);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                System.out.println(" done.");
            }

            // produce output file
            File output = new File(outDir, io.getOutput());
            io.writeTo(output, results);
        }
    }


    /**
     * Result of running an algorithm on an input.
     */
    public static class Result {

        public long runningTimeMs;
        public int numIntersections;
        public double totalEdgeLength;

        public Result(SupportGraph output, long runningTimeMs) {
            this.runningTimeMs = runningTimeMs;
            this.numIntersections = output.getIntersectionCount();
            this.totalEdgeLength = output.getTotalLength();
        }

    }

}
