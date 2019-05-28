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
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import ilog.concert.IloException;
import ilog.cplex.IloCplex;
import rbptrees.algo.ProgressListener;
import rbptrees.experiments.DataGeneration.PositionDistribution;
import rbptrees.experiments.Experiment;
import rbptrees.experiments.IlpExperiment;
import rbptrees.experiments.LocalSearchExperiment;
import rbptrees.experiments.OptBranchBoundExperiment;

public class IlpRunner {

    private enum Task { CPLEX, BNB, LS };

    private File deleteMeToAbort = new File("./deleteMeToAbort");
    private int instancesCompleted = 0;
    private boolean printed = false;


    public static void main(String[] args) {
        // parse arguments
        int i = 0;
        String nodeId = null;
        int[] n = null;
        String outputDirectory = "./results";
        int trials = 1000;
        int trialsPerZip = 100;
        int cplexMaxMem = 2_048;
        boolean cplexCompress = true;
        int cplexTreeLim = 10_240;
        PositionDistribution pos = PositionDistribution.UNIFORM_RANDOM;
        Task task = Task.CPLEX;

        if (args.length > i) { // arg[0]: nodeId
            nodeId = args[i++];
            if (args.length > i) { // arg[1]: points
                try {
                     String[] nl = args[i++].split(",");
                     n = new int[nl.length];
                     for (int j = 0; j < nl.length; ++j) {
                         n[j] = Integer.parseInt(nl[j]);
                     }
                } catch (NumberFormatException nfe) {
                    usage();
                    return;
                }
                if (args.length > i) { // arg[2]: outputDirectory
                    outputDirectory = args[i++];
                    if (args.length > i) { // arg[3]: trials
                        try {
                            trials = Integer.parseInt(args[i++]);
                        } catch (NumberFormatException nfe) {
                            usage();
                            return;
                        }
                        if (args.length > i) { // arg[4]: trialsPerZip
                            try {
                                trialsPerZip = Integer.parseInt(args[i++]);
                            } catch (NumberFormatException nfe) {
                                usage();
                                return;
                            }
                            if (args.length > i) { // arg[5]: maxMem
                                try {
                                    cplexMaxMem = Integer.parseInt(args[i++]);
                                } catch (NumberFormatException nfe) {
                                    usage();
                                    return;
                                }
                                if (args.length > i) { // arg[6]: compress
                                    try {
                                        cplexCompress = args[i++].equals("true");
                                    } catch (NumberFormatException nfe) {
                                        usage();
                                        return;
                                    }
                                    if (args.length > i) { // arg[7]: treeLim
                                        try {
                                            cplexTreeLim = Integer.parseInt(args[i++]);
                                        } catch (NumberFormatException nfe) {
                                            usage();
                                            return;
                                        }
                                        if (args.length > i) { // arg[8]: position distribution
                                            try {
                                                pos = PositionDistribution.valueOf(args[i++]);
                                            } catch (IllegalArgumentException iae) {
                                                usage();
                                                return;
                                            }
                                            if (args.length > i) { // arg[9]: task
                                                try {
                                                    task = Task.valueOf(args[i++]);
                                                } catch (Exception e) {
                                                    usage();
                                                    return;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (nodeId == null || n == null) {
            usage();
            return;
        }

        IlpRunner runner = new IlpRunner();
        if (task == Task.CPLEX) {
            runner.run(nodeId, n, outputDirectory, trials, trialsPerZip,
                    cplexMaxMem, cplexCompress, cplexTreeLim, pos);
        } else {
            runner.run(nodeId, n, outputDirectory, trials, trialsPerZip, pos, task);
        }
    }

    public static void usage() {
        System.err.println("Usage: IlpRunner nodeId points [outputDirectory] [trials] "
                + "[trialsPerZip] [maxMem] [compress] [treeLim] [pos. dist.] [task]");
        System.err.println("");
        System.err.println("  nodeId");
        System.err.println("    The ID of the node the experiment runs on (can be any string).");
        System.err.println("  points");
        System.err.println("    Number of points to run experiments on; can be a comma-separated list of integers.");
        System.err.println("  outputDirectory");
        System.err.println("    Where to write output to. Defaults to './results'.");
        System.err.println("  trials");
        System.err.println("    The number of trials to execute. Defaults to 10.");
        System.err.println("  trialsPerZip");
        System.err.println("    How many trials are output in one zip archive. Defaults to 10.");
        System.err.println("  maxMem");
        System.err.println("    How much RAM CPLEX can use, in MB. Defaults to 2048.");
        System.err.println("  compress");
        System.err.println("    Whether CPLEX should compress swap files ('true' or 'false'). Defaults to true.");
        System.err.println("  treeLim");
        System.err.println("    Limit on total tree size in MB. Defaults to 10240.");
        System.err.println("  position distribution");
        System.err.println("    Strategy for positioning points. Can be UNIFORM_RANDOM or SKELETON. Defaults to UNIFORM_RANDOM.");
        System.err.println("  task");
        System.err.println("    Task to execute, can be CPLEX, BNB (for branch and bound), or LS. Defaults to CPLEX.");
        System.err.println("    CPLEX will run the IlpExperiment with the given CPLEX parameters for memory usage etc.");
        System.err.println("    BNB will run the OptBranchBoundExperiment.");
        System.err.println("    LS will run the LocalSearchExperiment.");
    }


    public void deleteDirectoryIfExists(String path) {
        File dir = new File(path);
        if (dir.exists() && dir.isDirectory()) {
            try {
                Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                            throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                            throws IOException {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Execute {@link OptBranchBoundExperiment}.
     */
    public void run(String nodeId, int[] pointsPerExperiment,
            String outputDirectory, int trials, int trialsPerZip,
            PositionDistribution pos, Task task) {
        Experiment experiment;
        if (task == Task.BNB) {
            experiment = new OptBranchBoundExperiment() {
                @Override
                protected PositionDistribution[] getPositionDistributions() {
                    return new PositionDistribution[] {pos};
                }

                @Override
                protected int[] getNumberOfPoints() {
                    return pointsPerExperiment;
                }
            };
        } else {
            experiment = new LocalSearchExperiment() {
                @Override
                protected PositionDistribution[] getPositionDistributions() {
                    return new PositionDistribution[] {pos};
                }

                @Override
                protected int[] getNumberOfPoints() {
                    return pointsPerExperiment;
                }
            };
        }

        runExperiment(experiment, trials, trialsPerZip, nodeId, outputDirectory);
    }

    /**
     * Execute {@link IlpExperiment}.
     */
    public void run(String nodeId, int[] pointsPerExperiment,
            String outputDirectory, int trials, int trialsPerZip, int cplexMaxMem,
            boolean cplexCompress, int cplexTreeLim, PositionDistribution pos) {
        IlpExperiment experiment = new IlpExperiment() {
            @Override
            protected PositionDistribution[] getPositionDistributions() {
                return new PositionDistribution[] {pos};
            }

            @Override
            protected int[] getNumberOfPoints() {
                return pointsPerExperiment;
            }
        };
        IloCplex cplex = experiment.getIlp().getCplex();
        try {
            cplex.setParam(IloCplex.DoubleParam.WorkMem, cplexMaxMem);
            cplex.setParam(IloCplex.IntParam.NodeFileInd, (cplexCompress ? 3 : 2));
            cplex.setParam(IloCplex.DoubleParam.TreLim, cplexTreeLim);
        } catch (IloException e) {
            e.printStackTrace();
            return;
        }

        runExperiment(experiment, trials, trialsPerZip, nodeId, outputDirectory);
    }


    private void runExperiment(Experiment experiment, int trials, int trialsPerZip,
            String nodeId, String outputDirectory) {
        try {
            if (!deleteMeToAbort.createNewFile()) {
                System.err.println("Another experiment appears to be in progress.");
                return;
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return;
        }

        experiment.setListener(new ProgressListener() {
            @Override
            public boolean shouldAbort(double gap, double secondsRunning) {
                return !deleteMeToAbort.exists();
            }

            @Override
            public void onProgress(double gap, double secondsRunning) {
                if (gap >= 0) {
                    return; // ignore CPLEX progress information
                }

                //int threadId = (int) Math.floor(-gap / 100) - 1;
                int type = (int) (-gap % 100);
                switch (type) {
                case 2: // instance completed
                    if (!printed) {
                        System.out.println(String.format("Running %d trials with %d instances "
                                + "each. A dot is printed upon completion of each instance.",
                                trials, experiment.getNumberOfInstances()));
                        printed = true;
                    }
                    if (instancesCompleted % 10 == 0 && instancesCompleted > 0) {
                        System.out.println(" " + instancesCompleted + " / " +
                                experiment.getNumberOfInstances() *
                                experiment.getNumberOfTrials());
                    }
                    instancesCompleted++;
                    System.out.print(".");
                    break;
                }
            }

            @Override
            public void onDone(double secondsRunning) {
                if (secondsRunning <= 0) { // ignore CPLEX being done
                    int i = instancesCompleted;
                    while (i % 10 != 0) {
                        System.out.print(" ");
                        i++;
                    }
                    System.out.println(" " + instancesCompleted + " / " +
                            experiment.getNumberOfInstances() *
                            experiment.getNumberOfTrials());
                    if (!deleteMeToAbort.delete()) {
                        System.out.println("aborted");
                    }
                }
            }

            @Override
            public void onAbort() {}
        });
        experiment.threadID = 1;
        experiment.setOutputDirectory(outputDirectory);
        deleteDirectoryIfExists(outputDirectory);
        try {
            experiment.run(nodeId, trials, trialsPerZip);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
