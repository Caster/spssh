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

import rbptrees.algo.Algorithm;
import rbptrees.algo.IntegerLinearProgram;
import rbptrees.algo.LocalSearch;
import rbptrees.algo.ProgressListener;
import rbptrees.experiments.DataGeneration.DegreeDistribution;

public class IlpExperiment extends Experiment {

    private boolean[] forcetrees = {false, true};
    private IntegerLinearProgram ilp = new IntegerLinearProgram();

    @Override
    public void setListener(ProgressListener listener) {
        super.setListener(listener);
        ilp.addListener(listener);
    }

    @Override
    protected int[] getColors() {
        return new int[]{2, 3};
    }

    @Override
    protected DegreeDistribution[] getDegreeDistributions() {
        return new DegreeDistribution[]{DegreeDistribution.GAUSS_LOW, DegreeDistribution.GAUSS_MID};
    }

    @Override
    protected int getNumberOfAlgorithms() {
        return 4 * forcetrees.length;
    }

    @Override
    protected int[] getNumberOfPoints() {
        return new int[]{20};
    }

    @Override
    public boolean hasIlp() {
        return true;
    }

    @Override
    public IntegerLinearProgram getIlp() {
        return ilp;
    }

    @Override
    protected void runAlgorithms() throws IOException {
        for (boolean forceTree : forcetrees) {
            Algorithm nonplanar = new LocalSearch(forceTree, false);
            if (!runAlgorithm(nonplanar)) {
                continue;
            }

            Algorithm planar = new LocalSearch(forceTree, true);
            if (!runAlgorithm(planar)) {
                continue;
            }

            ilp.setForceTree(forceTree);
            ilp.setMaxIntersections(-1);
            ilp.initialize(points, nonplanar.getOutput());

            if (!runAlgorithmNoInit(ilp)) {
                continue;
            }

            ilp.setMaxIntersections(0);
            ilp.initialize(points, planar.getOutput());

            runAlgorithmNoInit(ilp);
        }

    }

    @Override
    protected boolean needAllColorsPoint() {
        return true;
    }

}
