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

import nl.tue.geometrycore.gui.sidepanel.SideTab;
import rbptrees.data.ColoredPointSet;
import rbptrees.data.SupportGraph;

/**
 *
 * @author wmeulema
 */
public class BruteForceWithInit extends ThreadableAlgorithm {

    private final BruteForceSolver bfs = new BruteForceSolver(false, false);

    public BruteForceWithInit() {
        super("LocalSearch to BruteForce");
    }

    @Override
    public void initialize(ColoredPointSet input) {
        LocalSearch ls = new LocalSearch(bfs.forceTree, bfs.forcePlanar);
        ls.initialize(input);
        ls.run();
        bfs.initialize(input, ls.getOutput());
        output = bfs.output;
    }

    @Override
    public void addListener(ProgressListener listener) {
        bfs.addListener(listener);
    }

    @Override
    public void removeListener(ProgressListener listener) {
        bfs.removeListener(listener);
    }

    @Override
    public boolean runSync() {
        return bfs.runSync();
    }

    @Override
    public void setStatus(String text) {
        bfs.setStatus(text);
    }

    @Override
    public String getSolutionIdentifier() {
        return "LS"+bfs.getSolutionIdentifier();
    }

    @Override
    public void displaySettings(SideTab tab) {
        bfs.displaySettings(tab);
    }

    @Override
    public SupportGraph getOutput() {
        return bfs.getOutput();
    }



}
