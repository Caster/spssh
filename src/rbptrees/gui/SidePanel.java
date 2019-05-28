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

package rbptrees.gui;

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JSpinner;

import nl.tue.geometrycore.gui.sidepanel.SideTab;
import nl.tue.geometrycore.gui.sidepanel.TabbedSidePanel;
import nl.tue.geometrycore.io.raster.RasterWriter;
import rbptrees.algo.Algorithm;
import rbptrees.algo.IntegerLinearProgram;
import rbptrees.algo.ProgressListener;
import rbptrees.algo.ThreadableAlgorithm;
import rbptrees.cli.BatchRunner;
import rbptrees.data.SupportGraph;
import rbptrees.experiments.DataGeneration;
import rbptrees.experiments.DataGeneration.DegreeDistribution;
import rbptrees.experiments.DataGeneration.PositionDistribution;

/**
 *
 * @author wmeulema
 */
public class SidePanel extends TabbedSidePanel {

    private final Data data;
    private SideTab setstab;
    private SideTab supporttab;
    private JButton cancel;
    private JComboBox<Algorithm> comboAlgo;
    private SideTab algoTab;
    private SideTab batchTab;
    private JLabel batchStatus;
    private SideTab tabData;
    private JSpinner spinNumColors;
    private List<SpinnerSpec> colorCountSpinners = new ArrayList<>();
    private JComboBox<DegreeDistribution> comboGenerateDistr;
    private JSpinner spinnerGenerateColors;
    private JSpinner spinnerGeneratePoints;
    private JCheckBox checkGenerateCommon;
    private JComboBox<PositionDistribution> comboPosDistr;

    private void generateBias() {
        data.clear();

        data.pointset = DataGeneration.generate(
                (int) spinnerGeneratePoints.getValue(),
                (int) spinnerGenerateColors.getValue(),
                (DegreeDistribution) comboGenerateDistr.getSelectedItem(),
                checkGenerateCommon.isSelected(),
                (PositionDistribution) comboPosDistr.getSelectedItem());

        data.newDataSet();

    }

    private class SpinnerSpec {

        JSpinner spin;
        int[] colors;
    }

    public SidePanel(Data data) {
        this.data = data;
        data.side = this;

        for (Algorithm alg : BatchRunner.ALGOS) {
            if (alg instanceof IntegerLinearProgram) {
                ThreadableAlgorithm thAlg = (ThreadableAlgorithm) alg;
                thAlg.addListener(new ProgressListener() {
                    @Override
                    public boolean shouldAbort(double gap, double secondsRunning) {
                        return false;
                    }

                    @Override
                    public void onProgress(double gap, double secondsRunning) {
                        thAlg.setStatus(String.format("Running for %.1fs. %.2f%% integrality gap.", secondsRunning, gap));
                    }

                    @Override
                    public void onDone(double secondsRunning) {
                        thAlg.setStatus(String.format("Not running. Previous: done in %.1fs.", secondsRunning));
                    }

                    @Override
                    public void onAbort() {
                        thAlg.setStatus("Not running. Previous: aborted.");
                    }
                });
            }
        }

        initDataTab();
        initSetsTab();
        initAlgorithmTab();
        initSupportTab();
    }

    private void generateData() {
        data.clear();

        for (SpinnerSpec sp : colorCountSpinners) {
            int cnt = (int) sp.spin.getValue();

            while (cnt > 0) {
                data.pointset.addPoint(Math.random() * 100, Math.random() * 100, sp.colors);
                cnt--;
            }
        }

        data.newDataSet();
    }

    private List<int[]> findAllChoose(int deg, int numColors, List<int[]> options) {
        List<int[]> result = new ArrayList<>();
        if (deg == 1) {
            for (int c = 0; c < numColors; c++) {
                result.add(new int[]{c});
            }
        } else {
            for (int[] opt : options) {
                for (int c = opt[deg - 2] + 1; c < numColors; c++) {
                    int[] newopt = new int[deg];
                    System.arraycopy(opt, 0, newopt, 0, deg - 1);
                    newopt[deg - 1] = c;
                    result.add(newopt);
                }

            }
        }
        return result;
    }

    private String makeLabel(int[] colors) {
        String res = "";
        for (int c : colors) {
            res += (char) ('A' + c);
        }
        return res;
    }

    private void updateColorCount() {
        tabData.revertUntil(spinNumColors);
        tabData.addSpace();

        colorCountSpinners.clear();
        int numColors = (int) spinNumColors.getValue();

        List<int[]> options = null;
        for (int deg = 1; deg <= numColors; deg++) {

            options = findAllChoose(deg, numColors, options);
            for (int[] colors : options) {
                SpinnerSpec spec = new SpinnerSpec();
                spec.colors = colors;
                tabData.makeCustomSplit(2, 0.5, 0.5);
                tabData.addLabel(makeLabel(colors));
                spec.spin = tabData.addIntegerSpinner(5, 0, Integer.MAX_VALUE, 5, null);
                colorCountSpinners.add(spec);
            }
        }
        tabData.invalidate();
    }

    private void initDataTab() {
        tabData = addTab("Data");

        comboPosDistr = tabData.addComboBox(PositionDistribution.values(), null);
        comboGenerateDistr = tabData.addComboBox(DegreeDistribution.values(), null);
        tabData.makeCustomSplit(2, 0.5, 0.5);
        tabData.addLabel("#colors");
        spinnerGenerateColors = tabData.addIntegerSpinner(2, 2, Integer.MAX_VALUE, 1, null);
        tabData.makeCustomSplit(2, 0.5, 0.5);
        tabData.addLabel("#points");
        spinnerGeneratePoints = tabData.addIntegerSpinner(2, 2, Integer.MAX_VALUE, 1, null);
        checkGenerateCommon = tabData.addCheckbox("Force common", true, null);

        tabData.addButton("Generate", (e) -> {
            generateBias();
        });

        tabData.addSeparator(2);

        tabData.addButton("Generate", (e) -> {
            generateData();
        });

        tabData.makeCustomSplit(2, 0.5, 0.5);
        tabData.addLabel("#colors");
        spinNumColors = tabData.addIntegerSpinner(2, 2, Integer.MAX_VALUE, 1, (e, v) -> {
            updateColorCount();
        });

        updateColorCount();
    }

    public void refresh() {
        supporttab.clearTab();

        supporttab.makeSplit(2, 2);
        JSpinner width = supporttab.addIntegerSpinner(1920, 100, 10000, 100, null);
        JSpinner height = supporttab.addIntegerSpinner(1080, 100, 10000, 100, null);
        supporttab.addButton("Screenshot!", (ActionEvent e) -> {

            try (RasterWriter write = RasterWriter.imageWriter(
                    data.draw.getWorldview(),
                    (Integer) width.getValue(), (Integer) height.getValue(),
                    new File("screen_" + System.currentTimeMillis() + ".png"))) {
                write.initialize();
                data.draw.render(write);
            } catch (IOException ex) {
                Logger.getLogger(SidePanel.class.getName()).log(Level.SEVERE, null, ex);
            }
        });

        supporttab.addSeparator(2);

        supporttab.addButton("< none >", (ActionEvent e) -> {
            data.activeSupport = null;
            data.draw.repaint();
        });

        for (SupportGraph supp : data.supports) {
            makeSupport(supp);
        }

        initSetsTab();
    }

    private void initSetsTab() {
        if (setstab == null) {
            setstab = addTab("Sets");
        } else {
            setstab.clearTab();
        }

        for (Integer c : data.pointset.getColors()) {
            setstab.makeCustomSplit(4, 0.2, 0.2, 0.2, 0.4);

            setstab.addButton("X", (e) -> {
                data.pointset.removeColor(c);
                data.pointset.minimizeColorNumbers();
                data.newDataSet();
            });
            setstab.addButton("", (e) -> {
            }).setBackground(ColorAssignment.color(c));
            setstab.addLabel(c + ":" + (char) ('A' + c));
            setstab.addLabel(data.pointset.getColornames().getOrDefault(c, ""));
        }
    }

    public void setCancelVisible(boolean visible) {
        cancel.setEnabled(visible);
        //cancel.setVisible(visible);
        //revalidate();
        //repaint();
    }

    private final DecimalFormat df = new DecimalFormat("#.##");

    public void makeSupport(final SupportGraph support) {
        if (support != data.starSupport) {
            supporttab.makeCustomSplit(2, 0.8, 0.2);
        }
        String name = support.getName() + " >> " + df.format(support.getTotalLength()) + " > " + support.getIntersectionCount();
        supporttab.addButton(name, (ActionEvent e) -> {
            data.activeSupport = support;
            data.draw.repaint();
        });
        if (support != data.starSupport) {
            supporttab.addButton("X", (ActionEvent e) -> {
                data.supports.remove(support);
                if (support == data.activeSupport) {
                    data.activeSupport = null;
                }
                data.refreshUI();
            });
        }
    }

    @SuppressWarnings("unchecked")
    private void initAlgorithmTab() {
        algoTab = addTab("Algorithm");

        comboAlgo = algoTab.addComboBox(BatchRunner.ALGOS, BatchRunner.DEFAULT, (e, v) -> {
            algorithmSelectionChanged();
        });

        algoTab.addButton("Run!", (ActionEvent e) -> {
            data.runAlgorithm((Algorithm) comboAlgo.getSelectedItem());
        });

        cancel = algoTab.addButton("Cancel", (ActionEvent e) -> {
            data.cancelAlgorithm();
        });
        setCancelVisible(false);

        algorithmSelectionChanged();
    }

    private void algorithmSelectionChanged() {
        algoTab.revertUntil(cancel);
        algoTab.addSpace(2);

        ((Algorithm) comboAlgo.getSelectedItem()).displaySettings(algoTab);
    }

    private void initSupportTab() {
        supporttab = addTab("Supports");
        refresh();
    }
}
