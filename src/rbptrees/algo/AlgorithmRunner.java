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

package rbptrees.algo;

import java.util.Iterator;
import java.util.NoSuchElementException;

import rbptrees.data.ColoredPointSet;
import rbptrees.data.SupportGraph;

public class AlgorithmRunner implements Iterable<SupportGraph> {

    private Algorithm[] algos;
    private ColoredPointSet input;

    public AlgorithmRunner(ColoredPointSet input, Algorithm... algorithms) {
        this.input = input;
        algos = algorithms;
    }

    @Override
    public Iterator<SupportGraph> iterator() {
        return new AlgorithmIterator();
    }

    public class AlgorithmIterator implements Iterator<SupportGraph> {

        private int current = -1;

        @Override
        public boolean hasNext() {
            return algos.length > current + 1;
        }

        @Override
        public SupportGraph next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            current++;
            Algorithm next = algos[current];
            next.initialize(input);
            boolean result = false;
            if (next instanceof ThreadableAlgorithm) {
                result = ((ThreadableAlgorithm) next).runSync();
            } else {
                result = next.run();
            }
            return (result ? next.getOutput() : null);
        }

    }

}
