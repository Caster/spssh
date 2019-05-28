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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import rbptrees.data.ColoredPointSet;
import rbptrees.data.Positioner;

/**
 * Class that can be used to generate <i>instances</i>.
 *
 * @see Experiment
 */
public class DataGeneration {

    public static double scale = 100;
    public static Random R = new Random();

    public enum DegreeDistribution {
        LOW_DEGREES, EVEN, HIGH_DEGREES,
        GAUSS_LOW, GAUSS_MID, GAUSS_HIGH
    }

    public enum PositionDistribution {
        UNIFORM_RANDOM, SKELETON
    }

    public static ColoredPointSet generate(int n, int k, DegreeDistribution dist,
            boolean forceCommon, PositionDistribution placement) {
        int[] pointsOfDegree = new int[k];
        Arrays.fill(pointsOfDegree, 0);

        switch (dist) {
            case EVEN: {
                int d = 0;
                while (n > 0) {
                    pointsOfDegree[d]++;
                    d = (d + 1) % k;
                    n--;
                }
                break;
            }
            case LOW_DEGREES: {
                int d = 0;
                int tod = 0;
                while (n > 0) {
                    pointsOfDegree[d]++;
                    if (d == tod) {
                        d = 0;
                        tod++;
                        if (tod == k) {
                            tod = 0;
                        }
                    } else {
                        d++;
                    }
                    n--;
                }
                break;
            }
            case HIGH_DEGREES: {
                int d = k - 1;
                int tod = k - 1;
                while (n > 0) {
                    pointsOfDegree[d]++;
                    if (d == tod) {
                        d = k - 1;
                        tod--;
                        if (tod == -1) {
                            tod = k - 1;
                        }
                    } else {
                        d--;
                    }
                    n--;
                }
                break;
            }
            case GAUSS_LOW: {
                while (n > 0) {
                    double g = R.nextGaussian();
                    int d = (int) Math.floor(Math.abs(k * g / 2.5));
                    pointsOfDegree[clip(d, 0, k)]++;
                    n--;
                }
                break;
            }
            case GAUSS_MID: {
                while (n > 0) {
                    double g = R.nextGaussian();
                    int d = (int) Math.floor(k / 2.0 + k * g / 4.5);
                    pointsOfDegree[clip(d, 0, k)]++;
                    n--;
                }
                break;
            }
            case GAUSS_HIGH: {
                while (n > 0) {
                    double g = R.nextGaussian();
                    int d = (int) Math.floor(Math.abs(k * g / 2.5));
                    pointsOfDegree[clip(k - d - 1, 0, k)]++;
                    n--;
                }
                break;
            }
        }

        if (forceCommon && pointsOfDegree[k - 1] == 0) {
            int i = k - 2;
            while (i >= 0) {
                if (pointsOfDegree[i] > 0) {
                    pointsOfDegree[i]--;
                    pointsOfDegree[k - 1]++;
                    break;
                }
                i--;
            }
            if (pointsOfDegree[k - 1] == 0) {
                System.err.println("No common point!");
            }
        }

        int sumOfDegrees = 0;
        for (int degree = 1; degree <= k; degree++) {
            sumOfDegrees += degree * pointsOfDegree[degree - 1];
        }
        while (sumOfDegrees < 2 * k) {
            int d = 1;
            while (pointsOfDegree[d - 1] == 0) {
                d++;
            }
            pointsOfDegree[d - 1]--;
            pointsOfDegree[d]++;

            sumOfDegrees++;
        }

        return generate(placement, pointsOfDegree);
    }

    public static ColoredPointSet generate(PositionDistribution placement, int... pointsOfDegree) {
        ColoredPointSet points = new ColoredPointSet();

        int colors = pointsOfDegree.length;
        int npoints = 0;
        for (int i = 0; i < colors; i++) {
            points.getColors().add(i);
            npoints += pointsOfDegree[i];
        }

        int[] cntColors = new int[colors];
        for (int i = 0; i < colors; i++) {
            cntColors[i] = 0;
        }

        Positioner pos = Positioner.buildFor(placement, scale, npoints);
        while (npoints > 0) {

            int d = pickRandomDegree(pointsOfDegree);
            pointsOfDegree[d - 1]--;

            pos.placeNext();
            points.addPoint(pos.getX(), pos.getY(), pickRandomSets(d, colors, cntColors));

            npoints--;
        }
        pos.cooldown(points);
        return points;
    }

    public static int[] pickRandomSets(int cnt, int colors, int[] cntColors) {
        int[] picked = new int[cnt];

        // first, pick random from colors < 2 nodes
        List<Integer> prefColors = new ArrayList<>();
        List<Integer> allColors = new ArrayList<>();
        for (int i = 0; i < colors; i++) {
            if (cntColors[i] < 2) {
                prefColors.add(i);
            }
            allColors.add(i);
        }

        int index = 0;
        while (index < cnt) {
            if (prefColors.isEmpty()) {
                picked[index] = allColors.remove(R.nextInt(allColors.size()));
            } else {
                picked[index] = prefColors.remove(R.nextInt(prefColors.size()));
                Iterator<Integer> it = allColors.iterator();
                while (it.hasNext()) {
                    int c = it.next();
                    if (c == picked[index]) {
                        it.remove();
                        break;
                    }
                }
            }
            cntColors[picked[index]]++;
            index++;
        }

        return picked;
    }

    private static int clip(int v, int min, int max) {
        if (v < min) {
            return min;
        } else if (v >= max) {
            return max - 1;
        } else {
            return v;
        }
    }

    private static int pickRandomDegree(int[] pointsOfDegree) {
        List<Integer> ds = new ArrayList<>();

        for (int degree = 1; degree <= pointsOfDegree.length; degree++) {
            if (pointsOfDegree[degree - 1] > 0) {
                ds.add(degree);
            }
        }

        return ds.get(R.nextInt(ds.size()));
    }
}
