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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import rbptrees.algo.ProgressListener;

public enum Experiments {

    // Experiment 1
    TIME_TRIAL("Time Trial", TimeTrial.class),
    TIME_TRIAL_SKELETON("Time Trial Skeleton", TimeTrialSkeleton.class),

    // Experiment 2
    OPT_BRANCH_BOUND("OPT (Branch and Bound)", OptBranchBoundExperiment.class),
    SIMPLE("Simple experiments", SimpleExperiments.class),
    SIMPLE_SMALL("Simple experiments n=10", SimpleExperimentsSmall.class),
    SIMPLE_BIG("Simple experiments n=20", SimpleExperimentsBig.class);

    /**
     * Change the listener that is added to newly started experiments.
     */
    public static void setListener(ProgressListener listener) {
        Experiments.listener = listener;
    }


    private static ProgressListener listener;


    private Class<? extends Experiment> experiment;
    private List<Experiment> experiments;
    private String name;

    private Experiments(String name, Class<? extends Experiment> experiment) {
        this.experiment = experiment;
        this.experiments = new ArrayList<>();
        this.name = name;
    }

    public Experiment getExperiment(int id) {
        return experiments.get(id);
    }

    public List<Experiment> getExperiments() {
        return experiments;
    }

    public Experiment newExperiment(int id) {
        try {
            Experiment exp = experiment.getConstructor().newInstance();
            exp.setListener(listener);
            while (experiments.size() <= id) {
                experiments.add(null);
            }
            experiments.set(id, exp);
            return exp;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Returns the short name of the experiment.
     */
    public String getName() {
        return experiment.getSimpleName();
    }

    /**
     * Construct a new instance of an experiment and run it.
     */
    public void run(Experiment exp, String nodeID, int trials, int trialsPerZip,
            String outputDirectory, int threadID) throws IOException {
        if (exp != null && exp.getClass() != experiment) {
            throw new RuntimeException("unexpected type of experiment");
        }

        if (exp == null) {
            exp = newExperiment(threadID - 1);
        } else if (!experiments.contains(exp)) {
            exp.setListener(listener);
            experiments.add(exp);
        }

        exp.threadID = threadID;
        exp.setOutputDirectory(outputDirectory);
        exp.run(nodeID, trials, trialsPerZip);
    }

    /**
     * Returns a human-readable name of the experiment.
     */
    @Override
    public String toString() {
        return name;
    }

}
