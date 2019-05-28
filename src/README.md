This directory contains the source files of the experimental code accompanying "Short Plane Supports for Spatial Hypergraphs". Relevant parts of the code are documented using JavaDoc. This file is meant to guide the reader to entry points and key source files. It also explains how to run the code, and links entry points to experiments described in the paper.

There are four entry points into the program:

 1. `rbptrees.gui.RBPTrees`: this will launch a GUI that allows users to explore the various algorithms; create, open, edit and save data sets; create visualizations in different styles and export those.
 2. `rbptrees.experiments.ExperimentGUI`: this will, as the name suggests, launch a GUI that can be used to run experiments.
 3. `rbptrees.cli.IlpRunner`: this is a command line interface for running experiments. Useful on HPC.
 4. `rbptrees.experiments.MSTComputation`: used to transform old experiment results, not needed anymore normally.  

Entry points 1 and 2 can be called without arguments. We aimed to make the GUIs self-explanatory. The main GUI (entry point 1) has some shortcuts that are not listed explicitly in the menu, but those mostly pertain to changing the style of the visualization etc. We refer to the code (`rbptrees.gui.DrawPanel#keyPress(int, boolean, boolean, boolean)`) for an up-to-date overview of shortcuts.

Entry point 3 is used for Experiment 2 in the paper. It will print a brief usage when called without arguments, that should clarify how we called it to run the experiments. We executed the experiments on an HPC cluster, where every thread would invoke entry point 3 (a number of times, sequentially). To run experiments in parallel from the program, entry point 2 can be used. We used that entry point for Experiment 1.

When invoking entry point 1 with arguments, the `rbptrees.cli.BatchRunner` is invoked. This way of running the code can be used to rerun experiments, or run additional experiments, on data that was generated previously. The way it works is that a single argument is passed, the path to a YAML file describing what to do. Details can be found in `rbptrees.cli.BatchIO`, and an example file is included in `Experiments/batch-config.yml`. The code assumes a certain directory tree, where inputs can be found in a given directory `Inputs`, and results are written to files in `Experiments/Results`. Again, details can be found in the code. This entry point should normally not be needed, but may prove useful when more statistics are needed or experiments failed for external reasons.

The file `rbptrees.experiments.Experiments` is the container for experiments, and lists which experiments were executed in the paper.
