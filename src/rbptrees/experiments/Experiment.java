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
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import rbptrees.algo.Algorithm;
import rbptrees.algo.IntegerLinearProgram;
import rbptrees.algo.ProgressListener;
import rbptrees.algo.ThreadableAlgorithm;
import rbptrees.data.ColoredPointSet;
import rbptrees.data.SupportGraph;
import rbptrees.experiments.DataGeneration.DegreeDistribution;
import rbptrees.experiments.DataGeneration.PositionDistribution;
import rbptrees.io.DataSetIO;

/**
 * An experiment is a full set of a number of trials that can be executed. Each
 * trial consists of running a set of algorithms with different parameters. The
 * parameters are:
 * <ul>
 * <li>number of points;</li>
 * <li>number of colors;</li>
 * <li>
 * distribution of colors over points (few/many points with high degree).
 * </li>
 * </ul>
 * One set of parameters is an <i>instance</i>. The result of running algorithms
 * on an instance is saved into a text file: this lists vertices and for every
 * algorithm the edges added by it. These files are zipped and written to a
 * given output directory.
 *
 * A single CSV file containing statistics about the results is produced in the
 * output directory as well. These results can be aggregated separately.
 */
public abstract class Experiment {

    public int threadID; // ID of thread on node

    protected String outputfolder;
    protected int[] ks;
    protected int[] ns;
    protected DegreeDistribution[] ds;
    protected PositionDistribution[] ps;

    protected ColoredPointSet points;
    protected BufferedWriter write;
    protected List<SupportGraph> supports;

    protected String nodeID; // ID of node experiment is running on
    protected DegreeDistribution d; // current distribution
    protected PositionDistribution p; // position distribution
    protected int k; // current number of colors
    protected int n; // current number of points
    protected int instanceNumber; // current instance
    protected int instanceNumberMax; // total number of instances in trial
    protected int trialNumber; // current trial
    protected int trialTotal; // total number of trials
    protected Algorithm algorithm; // currently executing algorithm

    protected ProgressListener listener = null;


    public Experiment() {
        this.ds = getDegreeDistributions();
        this.ps = getPositionDistributions();
        this.ks = getColors();
        this.ns = getNumberOfPoints();
        this.outputfolder = "Experiments/" + getClass().getSimpleName() + "/";
        this.trialTotal = -1;
    }

    public Algorithm getCurrentAlgorithm() {
        return algorithm;
    }

    public int getCurrentNumberOfColors() {
        return k;
    }

    public int getCurrentNumberOfPoints() {
        return n;
    }

    public DegreeDistribution getCurrentDegreeDistribution() {
        return d;
    }

    public PositionDistribution getCurrentPositionDistribution() {
        return p;
    }

    public int getNumberOfInstances() {
        return instanceNumberMax;
    }

    public int getNumberOfTrials() {
        return trialTotal;
    }

    /**
     * Returns whether this experiment includes an ILP algorithm.
     *
     * Must be overridden by child experiments that include ILP.
     */
    public boolean hasIlp() {
        return false;
    }

    /**
     * Returns the ILP algorithm used in this experiment, or {@code null} if
     * {@link #hasIlp()} {@code == false}.
     */
    public IntegerLinearProgram getIlp() {
        return null;
    }

    public void setListener(ProgressListener listener) {
        this.listener = listener;
    }

    public void setOutputDirectory(String outputFolder) {
        this.outputfolder = outputFolder;
        if (outputFolder.charAt(outputFolder.length() - 1) != '/') {
            this.outputfolder += "/";
        }
    }

    public void run(String nodeID, int trials, int trialsPerZip) throws IOException {
        this.trialTotal = trials;
        new File(outputfolder).mkdirs();

        this.nodeID = nodeID;
        File statsfile = new File(outputfolder + "stats.csv");

        int last = 0;
        if (statsfile.exists()) {
            BufferedReader read = new BufferedReader(new FileReader(statsfile));
            String line = read.readLine(); // headers
            if (line != null) {
                String lastLine = null;
                while ((line = read.readLine()) != null) {
                    lastLine = line;
                }
                if (lastLine != null) {
                    last = Integer.parseInt(lastLine.split(",")[4]);
                }
            }
            read.close();
            write = new BufferedWriter(new FileWriter(statsfile, true));
        } else {
            statsfile.getParentFile().mkdirs();
            write = new BufferedWriter(new FileWriter(statsfile));
            write.append("NodeID,DegreeDistribution,NumColors,PointDistribution,NumPoints,Trial,Algorithm,Length,Intersections,Time");
            write.newLine();
        }

        FileOutputStream fos = null;
        ZipOutputStream zos = null;

        int run = 1;
        mainloop:
        while (run <= trials && (listener == null || !listener.shouldAbort(-1, -1))) {
            trialNumber = last + run;
            if (run % trialsPerZip == 1 || trialsPerZip == 1) {
                if (fos != null) {
                    zos.close();
                    fos.close();
                }
                fos = new FileOutputStream(outputfolder + "output_" + trialNumber + ".zip");
                zos = new ZipOutputStream(fos);
            }

            instanceNumberMax = ps.length * ds.length * ks.length * ns.length * getNumberOfAlgorithms();
            instanceNumber = 0;
            for (int k : ks) {
                this.k = k;
                for (int n : ns) {
                    this.n = n;
                    for (DegreeDistribution d : ds) {
                        this.d = d;
                        for (PositionDistribution p : ps) {
                            this.p = p;

                            ZipEntry zipEntry = new ZipEntry(p + "/" + d + "/" + k + "/" + n + "/" + trialNumber + ".txt");
                            zos.putNextEntry(zipEntry);
                            if (listener != null) {
                                listener.onProgress(-100 * threadID - 3, 0);
                            }
                            runTrial(write, zos);
                            zos.closeEntry();


                            if (listener.shouldAbort(-1, -1)) {
                                break mainloop;
                            }
                        }
                    }
                }
            }

            if (listener != null) {
                listener.onProgress(-100 * threadID - 1, run);
            }
            run++;
        }

        zos.close();
        fos.close();
        write.close();

        if (listener != null) {
            if (listener.shouldAbort(-1, -1)) {
                File tmpfile = new File(statsfile.getParent(), "tmp.csv");
                statsfile.renameTo(tmpfile);

                statsfile = new File(outputfolder + "stats.csv");

                BufferedReader read = new BufferedReader(new FileReader(tmpfile));
                write = new BufferedWriter(new FileWriter(statsfile));
                String line = read.readLine(); // header!
                write.write(line + "\n");
                while ((line = read.readLine()) != null) {
                    String[] split = line.split(",");
                    if (split.length < 5) {
                        break;
                    } else if (Integer.parseInt(split[4]) == trialNumber) {
                        break;
                    }
                    write.write(line + "\n");
                }
                read.close();
                write.close();

                tmpfile.delete();
            }
            listener.onDone(-100 * threadID - 1);
        }
    }

    protected abstract int[] getColors();

    protected abstract DegreeDistribution[] getDegreeDistributions();

    /**
     * By default, we only use uniform random positioning.
     */
    protected PositionDistribution[] getPositionDistributions() {
        return new PositionDistribution[]{ PositionDistribution.UNIFORM_RANDOM };
    }

    protected abstract int getNumberOfAlgorithms();

    protected abstract int[] getNumberOfPoints();

    protected boolean runAlgorithm(Algorithm algo) throws IOException {
        algo.initialize(points);
        return runAlgorithmNoInit(algo);
    }

    protected boolean runAlgorithmNoInit(Algorithm algo) throws IOException {
        this.algorithm = algo;
        if (listener != null) {
            listener.onProgress(-100 * threadID - 4, 0);
        }

        long pre = System.nanoTime();
        boolean success = false;
        try {
            if (algo instanceof ThreadableAlgorithm) {
                success = ((ThreadableAlgorithm) algo).runSync();
            } else {
                success = algo.run();
            }
        } catch (Exception e) {
            System.err.println("Algo failure: " + algo.getName());
            e.printStackTrace();
            success = false;
        }
        long duration = System.nanoTime() - pre;
        if (success) {
            write.append(nodeID + "," + d + "," + k + "," + p + ","+ n + ","
                    + trialNumber + "," + algo.getSolutionIdentifier());
            SupportGraph support = algo.getOutput();
            double len = support.getTotalLength();
            int intersections = support.getIntersectionCount();
            write.append("," + len);
            write.append("," + intersections);
            write.append("," + duration);
            supports.add(support);
        } else {
            write.append(",,,,,,,,");
        }
        write.newLine();
        instanceNumber++;
        if (listener != null) {
            listener.onProgress(-100 * threadID - 2, instanceNumber);
        }
        return success;
    }

    protected void skipAlgorithm(String message) {
        if (listener != null) {
            listener.onProgress(-100 * threadID - 4, 0);
        }
        instanceNumber++;
        if (listener != null) {
            listener.onProgress(-100 * threadID - 2, instanceNumber);
        }
    }

    protected abstract void runAlgorithms() throws IOException;

    protected abstract boolean needAllColorsPoint();

    protected void runTrial(BufferedWriter write, ZipOutputStream zos) throws IOException {
        points = DataGeneration.generate(n, k, d, needAllColorsPoint(), p);
        supports = new ArrayList<>();

        runAlgorithms();

        String result = DataSetIO.write(null, points, supports);
        InputStream input = new ByteArrayInputStream(result.getBytes(StandardCharsets.UTF_8));
        byte[] bytes = new byte[1024];
        int length;
        while ((length = input.read(bytes)) >= 0) {
            zos.write(bytes, 0, length);
        }
    }

}
