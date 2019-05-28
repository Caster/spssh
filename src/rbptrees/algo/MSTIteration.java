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

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JTextField;
import nl.tue.geometrycore.geometry.linear.LineSegment;
import nl.tue.geometrycore.graphs.simple.SimpleEdge;
import nl.tue.geometrycore.graphs.simple.SimpleGraph;
import nl.tue.geometrycore.graphs.simple.SimpleVertex;
import nl.tue.geometrycore.gui.sidepanel.SideTab;
import rbptrees.data.ColoredPointSet.ColoredPoint;
import rbptrees.data.SupportGraph.SupportNode;

/**
 *
 * @author wmeulema
 */
public class MSTIteration extends Algorithm {

    public MSTIteration() {
        super("MSTIteration");
    }

    RepetitionMode mode = RepetitionMode.AUTO;
    int fixed = 2;
    boolean skiplast = true;
    String custom = "ABA";

    @Override
    public boolean run() {

        int k = input.getColors().size();

        List<Integer> indices = new ArrayList();

        switch (mode) {
            case CUSTOM:
                for (char c : custom.toUpperCase().toCharArray()) {
                    indices.add(c - 'A');
                }
                break;
            case FIXED:
                for (int i = 0; i < fixed; i++) {
                    for (int j = 0; j < k - (skiplast && i == fixed - 1 ? 1 : 0); j++) {
                        indices.add(j);
                    }
                }
                break;
            case AUTO:
                for (int i = 0; i < k; i++) {
                    for (int j = 0; j < k - (skiplast && i == k - 1 ? 1 : 0); j++) {
                        indices.add(j);
                    }
                }
            default:
                break;
        }

        TreeUnion tu = new TreeUnion();
        tu.cpmap = new HashMap();
        for (SupportNode p : output.getVertices()) {
            TUVertex v = tu.addVertex(p);
            v.node = p;
            tu.cpmap.put(p.point, v);
        }

        int[] colors = new int[k];
        TUEdge[][] trees = new TUEdge[k][];
        {
            int i = 0;
            for (int c : input.getColors()) {
                colors[i] = c;
                int cnt = input.size((ColoredPoint p) -> p.colors.contains(c));
                if (cnt <= 0) {
                    cnt = 1;
                }
                trees[i] = new TUEdge[cnt - 1];
                i++;
            }
        }

        for (int i : indices) {
            int color = colors[i];
            computeMST(tu, color, trees[i]);
        }

        for (TUEdge e : tu.getEdges()) {
            output.addEdge(e.getStart().node, e.getEnd().node);
        }

        return true;
    }

    private void computeMST(TreeUnion tu, int color, TUEdge[] tree) {
        //System.out.println("COLOR: " + color);
        //System.out.println("unregistering");

        for (TUEdge e : tree) {
            if (e == null) {
                break;
            }
            tu.unregister(e);
        }

        List<TUVertex> intree = new ArrayList();
        List<TUVertex> outtree = new ArrayList();
        for (ColoredPoint p : input.iterateUnion(color)) {
            TUVertex n = tu.cpmap.get(p);
            outtree.add(n);
        }

        if (outtree.isEmpty()) {
            return;
        }

        intree.add(outtree.remove(outtree.size() - 1));

        //System.out.println("making tree");
        int i = 0;
        while (outtree.size() > 0) {

            TUVertex bin = null, bout = null;
            double dist = Double.POSITIVE_INFINITY;
            for (TUVertex in : intree) {
                for (TUVertex out : outtree) {
                    double d = tu.distance(in, out);
                    if (d < dist) {
                        dist = d;
                        bin = in;
                        bout = out;
                    }
                }
            }

            outtree.remove(bout);
            intree.add(bout);

            tree[i] = tu.register(bin, bout);

            //System.out.println("  adding " + bin.getGraphIndex() + " - " + bout.getGraphIndex());
            i++;
        }
    }

    @Override
    public String getSolutionIdentifier() {
        return "MSTIteration";
    }

    public enum RepetitionMode {
        AUTO, FIXED, CUSTOM
    }

    @Override
    public void displaySettings(SideTab tab) {

        tab.addComboBox(RepetitionMode.values(), mode, (e, v) -> {
            mode = v;
        });

        tab.makeSplit(2, 2);
        tab.addLabel("Fixed repetitions");
        tab.addIntegerSpinner(fixed, 1, Integer.MAX_VALUE, 1, (e, v) -> {
            fixed = v;
        });
        tab.addCheckbox("Skip very last", skiplast, (e, v) -> {
            skiplast = v;
        });

        final JLabel customLabel = tab.addLabel("Custom: " + custom);
        final JTextField customText = tab.addTextField(custom);
        customText.addActionListener((e) -> {
            custom = customText.getText();
            customLabel.setText("Custom: " + custom);
        });
        customText.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {

            }

            @Override
            public void keyPressed(KeyEvent e) {

            }

            @Override
            public void keyReleased(KeyEvent e) {
                custom = customText.getText();
                customLabel.setText("Custom: " + custom);
            }
        });
    }

    private class TreeUnion extends SimpleGraph<LineSegment, TUVertex, TUEdge> {

        HashMap<ColoredPoint, TUVertex> cpmap;

        @Override
        public TUVertex createVertex(double x, double y) {
            return new TUVertex(x, y);
        }

        @Override
        public TUEdge createEdge() {
            return new TUEdge();
        }

        public double distance(TUVertex a, TUVertex b) {
            if (a.isNeighborOf(b)) {
                return 0;
            } else {
                return a.squaredDistanceTo(b);
            }
        }

        public void unregister(TUEdge edge) {
            edge.cnt--;
            if (edge.cnt == 0) {
                //System.out.println("  remove " + edge.getStart().getGraphIndex() + " - " + edge.getEnd().getGraphIndex());
                removeEdge(edge);
            } else {
                //System.out.println("  keep " + edge.getStart().getGraphIndex() + " - " + edge.getEnd().getGraphIndex());
            }
        }

        public TUEdge register(TUVertex a, TUVertex b) {
            TUEdge e = addEdge(a, b, new LineSegment(a.clone(), b.clone()));
            e.cnt++;
            return e;
        }
    }

    private class TUVertex extends SimpleVertex<LineSegment, TUVertex, TUEdge> {

        SupportNode node;

        public TUVertex(double x, double y) {
            super(x, y);
        }
    }

    private class TUEdge extends SimpleEdge<LineSegment, TUVertex, TUEdge> {

        int cnt = 0;
    }

}
