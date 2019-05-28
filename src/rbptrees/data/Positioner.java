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

package rbptrees.data;

import rbptrees.data.positioners.SkeletonPositioner;
import rbptrees.data.positioners.UniformRandomPositioner;
import rbptrees.experiments.DataGeneration.PositionDistribution;

public abstract class Positioner {

    /**
     * Number of points that will be positioned.
     */
    protected int numPoints;
    /**
     * Size of bounding box to position points in.
     */
    protected double scale;
    /**
     * X-coordinate of last placed point.
     */
    protected double x;
    /**
     * Y-coordinate of last placed point.
     */
    protected double y;


    public static Positioner buildFor(PositionDistribution placement, double scale, int numPoints) {
        Positioner result = null;
        switch (placement) {
        case SKELETON:
            result = new SkeletonPositioner(scale);
            break;
        case UNIFORM_RANDOM:
            result = new UniformRandomPositioner(scale);
            break;
        default:
            throw new IllegalArgumentException("unknown PositionDistribution " + placement);
        }
        result.warmup(numPoints);
        return result;
    }


    public Positioner(double scale) {
        this.scale = scale;
    }


    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }


    public abstract void placeNext();


    protected void warmup(int numPoints) {
        this.numPoints = numPoints;
    }

    public void cooldown(ColoredPointSet points) {

    }

}
