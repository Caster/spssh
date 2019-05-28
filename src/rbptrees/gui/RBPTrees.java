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

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JSplitPane;
import nl.tue.geometrycore.geometry.Vector;
import nl.tue.geometrycore.geometry.linear.Rectangle;
import nl.tue.geometrycore.io.ipe.IPEReader;
import nl.tue.geometrycore.io.ipe.IPEWriter;

import nl.tue.geometrycore.util.Pair;
import rbptrees.cli.BatchRunner;
import rbptrees.data.ColoredPointSet;
import rbptrees.data.SupportGraph;
import rbptrees.gui.DrawPanel.RenderMode;
import rbptrees.io.DataSetIO;

/**
 *
 * @author wmeulema
 */
public class RBPTrees {

    private static Data data;
    private static DrawPanel draw;
    private static JFrame frame;
    private static JFileChooser fc;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        if (args.length > 0) {
            try {
                (new BatchRunner(args)).run();
            } catch (FileNotFoundException fnfe) {
                // as BatchRunner checks for files to exist and be readable,
                // this will only happen is a file is deleted between that check
                // and being opened for reading, which is rare to say the least
                System.err.println("you deleted an important file, you silly");
            }
            return;
        }

        frame = new JFrame("RBPTrees");
        frame.setMinimumSize(new Dimension(600, 600));
        frame.setLayout(new BorderLayout());
        fc = new JFileChooser("../Inputs/");

        // draw and side panels
        data = new Data();
        draw = new DrawPanel(data);
        SidePanel side = new SidePanel(data);
        JSplitPane split = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                side,
                draw
        );
        split.setOneTouchExpandable(true);
        frame.add(split, BorderLayout.CENTER);

        // menu bar
        frame.setJMenuBar(new Menu());

        // show frame
        frame.setVisible(true);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
    }

    private static void open(Consumer<File> then) {
        int result = fc.showOpenDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            try {
                then.accept(fc.getSelectedFile());
            } catch (Exception e) {
                JOptionPane.showMessageDialog(frame, e.getMessage(),
                        e.getClass().getName(), JOptionPane.ERROR_MESSAGE);
            }
            draw.zoomToFit();
        }
    }

    private static void save(Consumer<File> then) {
        int result = fc.showSaveDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            try {
                then.accept(fc.getSelectedFile());
            } catch (Exception e) {
                JOptionPane.showMessageDialog(frame, e.getMessage(),
                        e.getClass().getName(), JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static class Menu extends JMenuBar {

        {
            JMenu fileMenu = new JMenu("File");
            JMenu openMenu = new JMenu("Open");
            openMenu.add(new MenuItem("Raw coordinates", (ActionEvent evt) -> {
                open((File file) -> {
                    Pair<ColoredPointSet, List<SupportGraph>> dataset = DataSetIO.read(file);
                    data.load(dataset.getFirst(), dataset.getSecond());
                });
            }));
            openMenu.add(new MenuItem("Ipe file", (ActionEvent evt) -> {
                open((File file) -> {
                    try (IPEReader read = IPEReader.fileReader(file)) {
                        data.readFrom(read.read());
                    } catch (IOException e) {
                        JOptionPane.showMessageDialog(frame, e.getMessage(),
                                e.getClass().getName(), JOptionPane.ERROR_MESSAGE);
                    }
                });
            }));
            openMenu.add(new MenuItem("Kelp-style Ipe file", (ActionEvent evt) -> {
                open((File file) -> {
                    try (IPEReader read = IPEReader.fileReader(file)) {
                        data.readIpeHypergraph(read.read());
                    } catch (IOException e) {
                        JOptionPane.showMessageDialog(frame, e.getMessage(),
                                e.getClass().getName(), JOptionPane.ERROR_MESSAGE);
                    }
                });
            }));
            openMenu.add(new MenuItem("CSV file Xiaoru", (ActionEvent evt) -> {
                open((File file) -> {
                    data.readCSV(file);
                });
            }));
            openMenu.add(new MenuItem("CSV file Mereke", (ActionEvent evt) -> {
                open((File file) -> {
                    data.readCSVmereke(file);
                });
            }));
            fileMenu.add(openMenu);
            JMenu saveMenu = new JMenu("Save As");
            saveMenu.add(new MenuItem("Raw coordinates", (ActionEvent evt) -> {
                save((File file) -> data.save(file));
            }));
            saveMenu.add(new MenuItem("Ipe file", (ActionEvent evt) -> {
                save((File file) -> {
                    try (IPEWriter write = IPEWriter.fileWriter(file)) {
                        write.initialize();
                        // hacky to not make page size change
                        write.setView(Rectangle.byCornerAndSize(new Vector(32, 32), 384, 384));
                        Rectangle world = draw.getBoundingRectangle();
                        world.grow(50);
                        write.setWorldview(world);
                        write.newPage("Support", "Points");
                        draw.write = write;
                        draw.render(write);
                    } catch (Exception e) {
                        JOptionPane.showMessageDialog(frame, e.getMessage(),
                                e.getClass().getName(), JOptionPane.ERROR_MESSAGE);
                    } finally {
                        draw.write = null;
                    }
                });
            }));
            saveMenu.add(new MenuItem("Ipe sequence", (ActionEvent evt) -> {
                save((File file) -> {
                    try (IPEWriter write = IPEWriter.fileWriter(file)) {
                        write.initialize();
                        // hacky to not make page size change
                        write.setView(Rectangle.byCornerAndSize(new Vector(32, 32), 384, 384));
                        Rectangle world = draw.getBoundingRectangle();
                        world.grow(50);
                        write.setWorldview(world);
                        RenderMode oldmode = draw.mode;
                        for (RenderMode mode : RenderMode.values()) {
                            write.newPage("Support", "Points");
                            draw.write = write;
                            draw.mode = mode;
                            draw.render(write);
                        }
                        draw.mode = oldmode;
                    } catch (Exception e) {
                        JOptionPane.showMessageDialog(frame, e.getMessage(),
                                e.getClass().getName(), JOptionPane.ERROR_MESSAGE);
                    } finally {
                        draw.write = null;
                    }
                });
            }));
            fileMenu.add(saveMenu);
            fileMenu.add(new MenuItem("Quit", (ActionEvent e) -> {
                frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
            }));
            add(fileMenu);
        }
    }

    private static class MenuItem extends JMenuItem {

        public MenuItem(String name, ActionListener onClick) {
            super(name);
            addActionListener(onClick);
        }
    }

}
