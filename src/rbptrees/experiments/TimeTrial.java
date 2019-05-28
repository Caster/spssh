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

import rbptrees.algo.LocalSearch;
import rbptrees.algo.MSTApprox;
import rbptrees.algo.MSTIteration;
import rbptrees.experiments.DataGeneration.DegreeDistribution;

/**
 * Experiment that compares the MSTApprox, MSTIter and LocalSearch algorithms.
 */
public class TimeTrial extends Experiment {

    protected boolean[] forceplanar = {false, true};
    protected boolean[] forcetree = {false, true};

    @Override
    protected int getNumberOfAlgorithms() {
        return 2 + forceplanar.length * forcetree.length;
    }

    @Override
    protected void runAlgorithms() throws IOException {
        runAlgorithm(new MSTApprox());
        runAlgorithm(new MSTIteration());
        for (boolean force : forceplanar) {
            for (boolean tree : forcetree) {
                runAlgorithm(new LocalSearch(tree, force));
            }
        }
    }

    @Override
    protected int[] getColors() {
        return new int[]{2, 3, 4, 5, 6, 7};
    }

    @Override
    protected DegreeDistribution[] getDegreeDistributions() {
        return new DegreeDistribution[]{DegreeDistribution.EVEN, DegreeDistribution.GAUSS_LOW, DegreeDistribution.GAUSS_MID, DegreeDistribution.GAUSS_HIGH};
    }

    @Override
    protected int[] getNumberOfPoints() {
        return new int[]{20, 40, 60, 80, 100};
    }

    @Override
    protected boolean needAllColorsPoint() {
        return true;
    }
}
