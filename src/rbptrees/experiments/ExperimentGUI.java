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

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import ilog.concert.IloException;
import ilog.cplex.IloCplex;
import rbptrees.algo.ProgressListener;
import sun.management.VMManagement;

/**
 * Simple GUI to execute experiments.
 */
public class ExperimentGUI extends JFrame implements FocusListener, MouseListener, ProgressListener {

    public static void main(String[] args) throws IOException, InterruptedException, URISyntaxException {
        new ExperimentGUI().setVisible(true);
    }

    private enum Mode {
        IDLE, RUNNING, ABORTING;
    }

    private static final Pattern ILP_PATTERN = Pattern.compile("I<=(\\d+)");

    // components
    private JButton abortButton;
    private JCheckBox cplexCompressFilesCheckBox;
    private JSpinner cplexMaxMemSpinner;
    private JSpinner cplexTreeLimitSpinner;
    private JComboBox<Experiments> experimentComboBox;
    private JLabel memoryLabel;
    private JTextField nodeIdField;
    private JSpinner numTrialsSpinner;
    private JSpinner numTrialsPerZipSpinner;
    private JTextField outputDirectoryField;
    private JSpinner parallelSpinner;
    private List<JLabel> progressLabels;
    private List<JLabel> progressDetailLabels;
    private JButton startButton;
    private JButton quitButton;

    // other members
    /**
     * Timestamp of last time {@link #outputDirectoryField} file chooser has
     * been opened. Used to not open it immediately after closing it.
     */
    private long focus = 0;
    private Mode mode = Mode.IDLE; // overall mode
    private boolean[] running; // whether thread is running
    private Experiments[] exps; // experiments that each thread is working on

    public ExperimentGUI() throws IOException, InterruptedException, URISyntaxException {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setTitle("Spatially Informative SetVis - Experiments");

        setLayout(new BorderLayout());

        add(new JPanel() {{
            GridBagLayout layout = new GridBagLayout();
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.insets = new Insets(3, 5, 3, 5);
            constraints.anchor = GridBagConstraints.LINE_END;
            constraints.fill = GridBagConstraints.HORIZONTAL;
            setLayout(layout);

            // node ID
            constraints.gridy = 0;
            String hostname = "My Computer";
            Process hostnameProc = Runtime.getRuntime().exec("hostname");
            if (hostnameProc.waitFor() == 0) {
                hostname = new Scanner(hostnameProc.getInputStream()).nextLine();
            }
            nodeIdField = new JTextField(hostname);
            nodeIdField.setBorder(BorderFactory.createCompoundBorder(
                    nodeIdField.getBorder(),
                    BorderFactory.createEmptyBorder(2, 5, 2, 5)));
            constraints.gridx = 0;
            add(new JLabel("Node Identifier:", SwingConstants.RIGHT), constraints);
            constraints.gridx = 1;
            add(nodeIdField, constraints);

            // experiment selection
            constraints.gridy++;
            experimentComboBox = new JComboBox<>(Experiments.values());
            experimentComboBox.addActionListener((e) -> updateOutputDirectoryField());
            constraints.gridx = 0;
            add(new JLabel("Experiment:", SwingConstants.RIGHT), constraints);
            constraints.gridx = 1;
            add(experimentComboBox, constraints);

            // number of trials
            constraints.gridy++;
            numTrialsSpinner = new JSpinner(new SpinnerNumberModel(
                    1000, 1, 10_000, 1));
            constraints.gridx = 0;
            add(new JLabel("Number of trials:", SwingConstants.RIGHT),
                    constraints);
            constraints.gridx = 1;
            add(numTrialsSpinner, constraints);

            // number of trials per zip
            constraints.gridy++;
            numTrialsPerZipSpinner = new JSpinner(new SpinnerNumberModel(
                    250, 1, 10_000, 1));
            constraints.gridx = 0;
            add(new JLabel("Number of trials per ZIP:", SwingConstants.RIGHT),
                    constraints);
            constraints.gridx = 1;
            add(numTrialsPerZipSpinner, constraints);

            // output directory
            constraints.gridy++;
            outputDirectoryField = new JTextField("");
            outputDirectoryField.setColumns(42);
            updateOutputDirectoryField();
            outputDirectoryField.setBorder(BorderFactory.createCompoundBorder(
                    outputDirectoryField.getBorder(),
                    BorderFactory.createEmptyBorder(2, 5, 2, 5)));
            outputDirectoryField.addMouseListener(ExperimentGUI.this);
            outputDirectoryField.addFocusListener(ExperimentGUI.this);
            constraints.gridx = 0;
            add(new JLabel("Output directory:", SwingConstants.RIGHT),
                    constraints);
            constraints.gridx = 1;
            add(outputDirectoryField, constraints);

            // CPLEX settings
            constraints.gridy++;
            addSeparator(this, constraints, "CPLEX Settings");

            // maximum work memory
            constraints.gridy++;
            cplexMaxMemSpinner = new JSpinner(new SpinnerNumberModel(
                    2048, 128, 32768, 1));
            constraints.gridx = 0;
            add(new JLabel("Maximum working memory (MB):", SwingConstants.RIGHT),
                    constraints);
            constraints.gridx = 1;
            add(cplexMaxMemSpinner, constraints);

            // tree size limit
            constraints.gridy++;
            cplexTreeLimitSpinner = new JSpinner(new SpinnerNumberModel(
                    10240, 128, 32768, 1));
            constraints.gridx = 0;
            add(new JLabel("Tree size limit (MB):", SwingConstants.RIGHT),
                    constraints);
            constraints.gridx = 1;
            add(cplexTreeLimitSpinner, constraints);

            // compress files?
            constraints.gridy++;
            cplexCompressFilesCheckBox = new JCheckBox();
            constraints.gridx = 0;
            add(new JLabel("Compress swap files:", SwingConstants.RIGHT),
                    constraints);
            constraints.gridx = 1;
            add(cplexCompressFilesCheckBox, constraints);

            // Parallelism settings
            constraints.gridy++;
            addSeparator(this, constraints, "Parallelism Settings");

            // number of threads/parallel experiments
            constraints.gridy++;
            parallelSpinner = new JSpinner(new SpinnerNumberModel(
                    1, 1, Runtime.getRuntime().availableProcessors(), 1));
            constraints.gridx = 0;
            add(new JLabel("Number of parallel experiments:", SwingConstants.RIGHT),
                    constraints);
            constraints.gridx = 1;
            add(parallelSpinner, constraints);

            // start/stop buttons
            constraints.gridy++;
            constraints.gridx = 1;
            add(new JPanel() {
                {
                    add(startButton = new JButton("Start"));
                    startButton.addActionListener(ExperimentGUI.this::onStart);
                    add(abortButton = new JButton("Abort"));
                    abortButton.setEnabled(false);
                    abortButton.addActionListener(ExperimentGUI.this::onAbort);
                    add(quitButton = new JButton("Quit"));
                    quitButton.addActionListener(ExperimentGUI.this::onQuit);
                }
            }, constraints);
        }}, BorderLayout.CENTER);

        add(new JPanel() {{
            GridBagLayout layout = new GridBagLayout();
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.insets = new Insets(3, 5, 3, 5);
            constraints.anchor = GridBagConstraints.LINE_END;
            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.weightx = 1;
            constraints.gridx = 0;
            constraints.gridy = -1;
            setLayout(layout);

            // progress labels
            progressLabels = new ArrayList<>();
            progressDetailLabels = new ArrayList<>();
            int maxThreads = Runtime.getRuntime().availableProcessors();
            for (int i = 0; i < maxThreads; ++i) {
                JLabel label = new JLabel("Idle.");
                progressLabels.add(label);
                JLabel detailLabel = new JLabel("");
                progressDetailLabels.add(detailLabel);
                constraints.gridy++;
                constraints.gridx = 0;
                add(label, constraints);
                constraints.gridx = 1;
                add(detailLabel, constraints);
            }
            running = new boolean[maxThreads];
            Arrays.fill(running, false);
            exps = new Experiments[maxThreads];
            Arrays.fill(exps, null);

            // memory label
            constraints.gridy++;
            memoryLabel = new JLabel("", SwingConstants.RIGHT);
            constraints.gridx = 1;
            add(memoryLabel, constraints);
            updateMemoryLabelPeriodically();
        }}, BorderLayout.SOUTH);

        pack();
        Dimension size = getSize();
        size.width = (size.width - size.width % 100) + 200;
        size.height = (size.height - size.height % 100) + 200;
        setSize(size);

        Experiments.setListener(this);
    }

    /**
     * Add a horizontal separator with title to the given panel.
     */
    private void addSeparator(JPanel panel, GridBagConstraints constraints, String title) {
        constraints.gridx = 0;
        constraints.gridwidth = 2;
        panel.add(Box.createVerticalStrut(10), constraints);

        constraints.gridy++;
        constraints.gridx = 1;
        constraints.gridwidth = 1;
        JLabel label = new JLabel(title == null ? " " : title);
        label.setFont(label.getFont().deriveFont(Font.ITALIC));
        panel.add(label, constraints);

        constraints.gridx = 0;
    }

    /**
     * Check if experiment has been executed previously.
     */
    private void checkPreviousState() {
        checkPreviousState(false);
    }

    /**
     * Check if experiment has been executed previously, or clean up old results.
     *
     * @param cleanup Whether results should be detected only ({@code false}),
     *            or actively deleted ({@code true}).
     */
    private void checkPreviousState(boolean cleanup) {
        File outputDir = new File(outputDirectoryField.getText());
        if (outputDir.exists() && outputDir.isDirectory()) {
            for (File inOutDir : outputDir.listFiles()) {
                if ((inOutDir.isFile() && (inOutDir.getName().matches("output_\\d+.zip") ||
                        inOutDir.getName().equals("stats.csv"))) ||
                        (inOutDir.isDirectory() && inOutDir.getName().matches("t\\d+"))) {
                    if (cleanup) {
                        if (inOutDir.isDirectory()) {
                            // the below is the equivalent of `rm -rf inOutDir` ...
                            try {
                                Files.walkFileTree(inOutDir.toPath(), new SimpleFileVisitor<Path>() {
                                    @Override
                                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                                            throws IOException {
                                        Files.delete(file);
                                        return FileVisitResult.CONTINUE;
                                    }

                                    @Override
                                    public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                                            throws IOException {
                                        Files.delete(dir);
                                        return FileVisitResult.CONTINUE;
                                    }
                                });
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        } else {
                            inOutDir.delete();
                        }
                    } else {
                        int choice = JOptionPane.showOptionDialog(this,
                                "Previous experiment results have been detected. "
                                + "What do you want to do?", "Previous results "
                                + "detected", JOptionPane.YES_NO_OPTION,
                                JOptionPane.QUESTION_MESSAGE, null, new String[] {
                                "Continue", "Delete results and start over"}, null
                            );
                        if (choice == 1) {
                            checkPreviousState(true);
                        }
                        return;
                    }
                }
            }
        }
    }

    private String formatBytes(double bytes) {
        String[] postfixes = new String[] {"B", "KB", "MB", "GB"};
        for (int i = 0; i < postfixes.length; ++i) {
            if (bytes > 1024) {
                bytes /= 1024.0;
            } else {
                return String.format("%.1f%s", bytes, postfixes[i]);
            }
        }
        return String.format("%.1f%s", bytes, postfixes[postfixes.length - 1]);
    }

    private int getFirstFreeThreadID() {
        int i;
        for (i = 0; i < running.length; ++i) {
            if (!running[i]) {
                break;
            }
        }
        if (i == running.length) {
            JOptionPane.showMessageDialog(this, "All threads are busy, "
                    + "cannot start another experiment now.",
                    "You're too busy", JOptionPane.ERROR_MESSAGE);
            return -1;
        }
        return i;
    }

    private int getPID() {
        try {
            RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
            Field jvm = runtime.getClass().getDeclaredField("jvm");
            jvm.setAccessible(true);
            VMManagement mgmt = (VMManagement) jvm.get(runtime);
            Method pid_method = mgmt.getClass().getDeclaredMethod("getProcessId");
            pid_method.setAccessible(true);
            return (Integer) pid_method.invoke(mgmt);
        } catch (Exception e) {
            return -1;
        }
    }

    private String getRealMemoryUsage(Runtime rt) {
        if (System.getProperty("os.name").equals("Linux")) {
            int pid = getPID();
            if (pid > 0) {
                try {
                    @SuppressWarnings("resource")
                    Scanner s = new Scanner(rt.exec(new String[] {
                            "/bin/sh", "-c", "pmap -x " + pid + " | "
                            + "awk '/total/ { print $4 }'"})
                                .getInputStream());
                    if (s.hasNext()) {
                        return "; " + formatBytes(
                            Double.parseDouble(s.next()) * 1024);
                    }
                } catch (IOException e) {
                    // ignore
                }
            }
        }
        return "";
    }

    private void setFormEnabled(boolean enabled) {
        startButton.setEnabled(enabled);
        quitButton.setEnabled(enabled);

        nodeIdField.setEnabled(enabled);
        experimentComboBox.setEnabled(enabled);
        numTrialsSpinner.setEnabled(enabled);
        numTrialsPerZipSpinner.setEnabled(enabled);
        outputDirectoryField.setEnabled(enabled);

        cplexCompressFilesCheckBox.setEnabled(enabled);
        cplexMaxMemSpinner.setEnabled(enabled);
        cplexTreeLimitSpinner.setEnabled(enabled);

        parallelSpinner.setEnabled(enabled);
    }

    private void onAbort(ActionEvent e) {
        abortButton.setEnabled(false);
        setFormEnabled(false);
        mode = Mode.ABORTING;
        for (int i = 0; i < running.length; ++i) {
            if (running[i]) {
                progressLabels.get(i).setText("Aborting ...");
            }
        }
    }

    private void onStart(ActionEvent e) {
        checkPreviousState();
        abortButton.setEnabled(true);
        mode = Mode.RUNNING;
        runAsync();
        for (boolean run : running) {
            if (!run) {
                return;
            }
        }
        // if all threads are running, disable starting more experiments
        setFormEnabled(false);
    }

    private void onQuit(ActionEvent e) {
        dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
    }

    private void runAsync() {
        int tID = getFirstFreeThreadID();
        if (tID < 0) {
            return;
        }
        Experiments experimentWrapper = ((Experiments) experimentComboBox
                .getSelectedItem());
        Experiment experiment = experimentWrapper.newExperiment(tID);
        int numThreads = (Integer) parallelSpinner.getValue();
        if (experiment.hasIlp()) {
            numThreads = 1;
        }

        for (int t = 0; t < numThreads; ++t) {
            final int threadId = getFirstFreeThreadID() + 1;
            if (threadId == 0) {
                break; // no free threads
            }
            Thread backgroundThread = new Thread(() -> {
                try {
                    // possibly set CPLEX parameters
                    if (experiment.hasIlp()) {
                        IloCplex cplex = experiment.getIlp().getCplex();
                        try {
                            cplex.setParam(IloCplex.DoubleParam.WorkMem,
                                    (Integer) cplexMaxMemSpinner.getValue());
                            cplex.setParam(IloCplex.IntParam.NodeFileInd,
                                    (cplexCompressFilesCheckBox.isSelected() ? 3 : 2));
                            cplex.setParam(IloCplex.DoubleParam.TreLim,
                                    (Integer) cplexTreeLimitSpinner.getValue());
                        } catch (IloException e) {
                            e.printStackTrace();
                        }
                    }

                    // run the experiment; ensure that every thread uses its own,
                    // freshly created experiment instance - the first thread can
                    // use the already created instance
                    experimentWrapper.run(
                            (threadId == 1 ? experiment : null),
                            nodeIdField.getText(),
                            (Integer) numTrialsSpinner.getValue(),
                            (Integer) numTrialsPerZipSpinner.getValue(),
                            outputDirectoryField.getText() +
                                File.separator + "t" + threadId,
                            threadId);
                } catch (IOException ioe) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(ExperimentGUI.this,
                                ioe.getMessage(), "IOException",
                                JOptionPane.ERROR_MESSAGE);
                    });
                }
            });
            backgroundThread.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread t, Throwable e) {
                    e.printStackTrace();
                }
            });
            backgroundThread.start();
            exps[threadId - 1] = experimentWrapper;
            running[threadId - 1] = true;
        }
    }

    private void updateMemoryLabelPeriodically() {
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    SwingUtilities.invokeLater(() -> {
                        Runtime rt = Runtime.getRuntime();
                        double mem = rt.totalMemory() - rt.freeMemory();
                        String real = getRealMemoryUsage(rt);
                        memoryLabel.setText(formatBytes(mem) +
                                " / " + formatBytes(rt.maxMemory()) + real);
                    });
                }
            }, 100, 2000);
    }

    private void updateOutputDirectoryField() {
        outputDirectoryField.setText(new File("Experiments/"
                + ((Experiments) experimentComboBox.getSelectedItem()).getName())
                .getAbsolutePath());
    }

    // --- FocusListener methods -----------------------------------------------
    @Override
    public void focusGained(FocusEvent e) {
        // called when outputDirectoryField gains focus
        if (outputDirectoryField.isEnabled()) {
            if (System.currentTimeMillis() - focus > 100) {
                JFileChooser fc = new JFileChooser(outputDirectoryField.getText());
                fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                if (fc.showDialog(this, "Pick") == JFileChooser.APPROVE_OPTION) {
                    outputDirectoryField.setText(fc.getSelectedFile().getAbsolutePath());
                }
            }
            focus = System.currentTimeMillis();
        }
    }

    @Override
    public void focusLost(FocusEvent e) {
    }

    // --- ProgressListener methods --------------------------------------------
    @Override
    public void onAbort() {}

    @Override
    public void onDone(double secondsRunning) {
        if (secondsRunning < 0) {
            int tID = (int) Math.floor(-secondsRunning / 100) - 1;
            running[tID] = false;
            progressLabels.get(tID).setText("Idle.");
            progressDetailLabels.get(tID).setText("");
            for (boolean run : running) {
                if (run) {
                    return;
                }
            }
            // if no more threads are running, update GUI
            setFormEnabled(true);
            abortButton.setEnabled(false);
            mode = Mode.IDLE;
        }
    }

    /**
     * Notifications of progress are displayed in GUI.
     *
     * WARNING: HAKCY CODE AHEAD. PROCEED WITH CAUTION.
     *
     * @param gap Interpreted as the type of progress that is to be reported.
     *            Consists of thread ID multiplied by a 100, added to the type
     *            of progress identifier, negative.
     *
     *            Types of progress:
     *
     *             1. A trial has been completed. `secondsRunning` is number of
     *                trials completed so far.
     *             2. An instance has been completed. `secondsRunning` is number
     *                of instances completed so far.
     *             3. A new trial is about to start. (Other param ignored.)
     *             4. A new algorithm is about to start. (Other param ignored.)
     *
     *            Example: a new trial is about to start in thread 2, then `gap`
     *            will have a value of `-203`.
     */
    @Override
    public void onProgress(double gap, double secondsRunning) {
        // ignore CPLEX progress information, ignore progress when aborting
        if (gap >= 0 || mode != Mode.RUNNING) {
            return;
        }

        int tID = (int) Math.floor(-gap / 100) - 1;
        Experiments exp = exps[tID];
        Experiment curExperiment = exp.getExperiment(tID);
        gap = (int) (gap % 100);
        if (gap == -1) {
            // interpret secondsRunning as number of trials completed
            progressLabels.get(tID).setText(String.format("Running %s ... "
                    + "completed %d / %d trials.", exp.getName(),
                    (int) secondsRunning, curExperiment.getNumberOfTrials()));
        } else if (gap == -2) {
            // interpret secondsRunning as number of instances completed
            progressLabels.get(tID).setText(String.format("Running %s ... "
                    + "completed %d / %d instances of trial %d.", exp.getName(),
                    (int) secondsRunning, curExperiment.getNumberOfInstances(),
                    curExperiment.trialNumber));
        } else if (gap == -3 || gap == -4) {
            // this means a new trial is about to start (-3)
            // or a new algorithm is about to start (-4)
            String curAlgo = (gap == -3 ? "initializing" : curExperiment
                    .getCurrentAlgorithm().getSolutionIdentifier());
            if (curAlgo.startsWith("ILP")) {
                Matcher m = ILP_PATTERN.matcher(curAlgo);
                if (m.matches()) {
                    curAlgo = "ILP " + m.group(1);
                } else {
                    curAlgo = "ILP 0";
                }
            }
            progressDetailLabels.get(tID).setText(String.format("Currently: %d "
                    + "colors / %d points / %s / %s.",
                    curExperiment.getCurrentNumberOfColors(),
                    curExperiment.getCurrentNumberOfPoints(),
                    curExperiment.getCurrentDegreeDistribution().name(),
                    curAlgo));
        }
    }

    @Override
    public boolean shouldAbort(double gap, double secondsRunning) {
        return (mode != Mode.RUNNING);
    }

    // --- MouseListener methods -----------------------------------------------
    @Override
    public void mouseClicked(MouseEvent e) {
        // called when outputDirectoryField is clicked
        focusGained(null);
    }

    @Override
    public void mousePressed(MouseEvent e) {}

    @Override
    public void mouseReleased(MouseEvent e) {}

    @Override
    public void mouseEntered(MouseEvent e) {}

    @Override
    public void mouseExited(MouseEvent e) {}

}
