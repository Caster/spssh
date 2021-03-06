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

import rbptrees.experiments.DataGeneration.PositionDistribution;

/**
 * Experiment that compares the MSTApprox, MSTIter and LocalSearch algorithms.
 */
public class TimeTrialSkeleton extends TimeTrial {

    @Override
    protected PositionDistribution[] getPositionDistributions() {
        return new PositionDistribution[]{PositionDistribution.SKELETON};
    }


}
