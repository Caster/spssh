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

package rbptrees.algo.cplex;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ilog.concert.IloException;
import ilog.cplex.IloCplex;
import ilog.cplex.IloCplex.MIPInfoCallback;
import rbptrees.algo.ProgressListener;

public class Callback extends MIPInfoCallback {

    private boolean aborted;
    private boolean done;
    private IloCplex cplex;
    private List<ProgressListener> listeners;
    private Set<ProgressListener> toBeRemoved;
    private double timeStart;

    public Callback(IloCplex cplex) throws IloException {
        listeners = new ArrayList<>();
        toBeRemoved = new HashSet<>();
        reset();
        timeStart = 0;
        this.cplex = cplex;
        if (cplex != null) {
            setTimeStart();
        }
    }

    public void reset() {
        aborted = false;
        done = false;
    }

    public boolean isAborted() {
        return aborted;
    }

    public boolean isDone() {
        return done;
    }

    public void done() throws IloException {
        if (!done) {
            done = true;
            synchronized(listeners) {
                for (ProgressListener l : listeners) {
                    l.onDone(cplex.getCplexTime() - timeStart);
                }
            }
            cleanup();
        }
    }

    public void addListener(ProgressListener listener) {
        synchronized(listeners) {
            listeners.add(listener);
            cleanup();
        }
    }

    private void cleanup() {
        synchronized (listeners) {
            for (ProgressListener listener : toBeRemoved) {
                listeners.remove(listener);
            }
            toBeRemoved.clear();
        }
    }

    public void clearListeners() {
        synchronized(listeners) {
            toBeRemoved.clear();
            listeners.clear();
        }
    }

    public void removeListener(ProgressListener listener) {
        synchronized(listeners) {
            toBeRemoved.add(listener);
        }
    }

    public void setCplex(IloCplex cplex) throws IloException {
        this.cplex = cplex;
        setTimeStart();
    }

    protected void setTimeStart() throws IloException {
        timeStart = cplex.getCplexTime();
    }

    @Override
    protected void main() throws IloException {
        if (!aborted && hasIncumbent()) {
            double gap = 100.0 * getMIPRelativeGap();
            double time = cplex.getCplexTime() - timeStart;
            synchronized(listeners) {
                for (ProgressListener l : listeners) {
                    l.onProgress(gap, time);
                    if (l.shouldAbort(gap, time)) {
                        aborted = true;
                    }
                }
            }
            // abort if any listener wants us to
            if (aborted) {
                abort();
                synchronized(listeners) {
                    for (ProgressListener l : listeners) {
                        l.onAbort();
                    }
                }
            }
        }
    }

}
