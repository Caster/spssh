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

import java.util.ArrayList;
import java.util.List;
import nl.tue.geometrycore.gui.sidepanel.SideTab;
import rbptrees.data.ColoredPointSet;
import rbptrees.data.SupportGraph.SupportNode;

/**
 *
 * @author wmeulema
 */
public class MSTApprox extends Algorithm {

    public MSTApprox() {
        super("MSTApprox");
    }

    @Override
    public boolean run() {
        for (int c : input.getColors()) {

            List<SupportNode> intree = new ArrayList();
            List<SupportNode> outtree = new ArrayList();
            for (ColoredPointSet.ColoredPoint p : input.iterateUnion(c)) {
                SupportNode n = output.getNodemap().get(p);
                outtree.add(n);
            }

            if (outtree.isEmpty()) {
                continue;
            }

            intree.add(outtree.remove(0));

            while (outtree.size() > 0) {

                SupportNode bin = null, bout = null;
                double dist = Double.POSITIVE_INFINITY;
                for (SupportNode in : intree) {
                    for (SupportNode out : outtree) {
                        if (in.squaredDistanceTo(out) < dist) {
                            dist = in.squaredDistanceTo(out);
                            bin = in;
                            bout = out;
                        }
                    }
                }

                outtree.remove(bout);
                intree.add(bout);

                output.addEdge(bin, bout);
            }
        }

        return true;
    }

    @Override
    public String getSolutionIdentifier() {
        return "MSTApprox";
    }

    @Override
    public void displaySettings(SideTab tab) {

    }

}
