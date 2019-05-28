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

public abstract class Algorithm {

    private final String name;
    protected ColoredPointSet input;
    protected SupportGraph output;

    public Algorithm(String name) {
        this.name = name;
    }

    public void initialize(ColoredPointSet input) {
        this.input = input;
        output = new SupportGraph(input, getSolutionIdentifier());
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return  name;
    }

    /**
     * @return true iff a result has been computed
     */
    public abstract boolean run();

    public SupportGraph getOutput() {
        return output;
    }

    public abstract String getSolutionIdentifier();

    public abstract void displaySettings(SideTab tab);

}
