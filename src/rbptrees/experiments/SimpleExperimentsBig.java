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
import rbptrees.algo.MSTIteration;

/**
 *
 * @author wmeulema
 */
public class SimpleExperimentsBig extends Experiment {
    boolean[] forcetrees = {false};
    boolean[] forceplanar = {false, true};

    @Override
    protected int[] getColors() {
        return new int[]{2, 3};
    }

    @Override
    protected DataGeneration.DegreeDistribution[] getDegreeDistributions() {
        return new DataGeneration.DegreeDistribution[]{DataGeneration.DegreeDistribution.GAUSS_LOW, DataGeneration.DegreeDistribution.GAUSS_MID};
    }

    @Override
    protected int[] getNumberOfPoints() {
        return new int[]{20};
    }

    @Override
    protected void runAlgorithms() throws IOException {
        runAlgorithm(new MSTIteration());

        for (boolean forceTree : forcetrees) {
            for (boolean forcePlanar : forceplanar) {
                LocalSearch ls = new LocalSearch(forceTree, forcePlanar);
                runAlgorithm(ls);

                BruteForceSolver bfs = new BruteForceSolver(forceTree, forcePlanar);
                bfs.addListener(listener);
                bfs.initialize(points, ls.getOutput());
                runAlgorithmNoInit(bfs);
            }
        }

    }

    @Override
    protected boolean needAllColorsPoint() {
        return true;
    }

    @Override
    protected int getNumberOfAlgorithms() {
        return 2 * forcetrees.length * forceplanar.length + 1;
    }
}
