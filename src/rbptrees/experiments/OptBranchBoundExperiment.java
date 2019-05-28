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

import rbptrees.algo.BruteForceSolver;
import rbptrees.algo.LocalSearch;
import rbptrees.experiments.DataGeneration.DegreeDistribution;
import rbptrees.experiments.DataGeneration.PositionDistribution;

public class OptBranchBoundExperiment extends Experiment {

    boolean[] forcetrees = {false, true};
    boolean[] forceplanar = {false, true};

    @Override
    protected int[] getColors() {
        return new int[]{2, 3};
    }

    @Override
    protected DegreeDistribution[] getDegreeDistributions() {
        return new DegreeDistribution[]{DegreeDistribution.GAUSS_LOW, DegreeDistribution.GAUSS_MID};
    }

    @Override
    protected int[] getNumberOfPoints() {
        return new int[]{10, 15, 20};
    }

    @Override
    protected int getNumberOfAlgorithms() {
        return 2 * forcetrees.length * forceplanar.length;
    }

    @Override
    protected PositionDistribution[] getPositionDistributions() {
        return new PositionDistribution[]{PositionDistribution.SKELETON};
    }

    @Override
    protected void runAlgorithms() throws IOException {
        for (boolean forceTree : forcetrees) {
            for (boolean forcePlanar : forceplanar) {
                LocalSearch ls = new LocalSearch(forceTree, forcePlanar);
                runAlgorithm(ls);

                if (getCurrentNumberOfPoints() < 20) {
                    BruteForceSolver bfs = new BruteForceSolver(forceTree, forcePlanar);
                    bfs.addListener(listener);
                    bfs.initialize(points, ls.getOutput());
                    runAlgorithmNoInit(bfs);
                } else {
                    skipAlgorithm("skipping BruteForce for n = 20");
                }
            }
        }
    }

    @Override
    protected boolean needAllColorsPoint() {
        return true;
    }

}
