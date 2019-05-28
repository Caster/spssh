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

import java.lang.Thread.UncaughtExceptionHandler;

public abstract class ThreadableAlgorithm extends Algorithm {

    public ThreadableAlgorithm(String name) {
        super(name);
    }

    public abstract void addListener(ProgressListener listener);

    public abstract void removeListener(ProgressListener listener);

    @Override
    public boolean run() {
        runAsync();
        // nothing changed yet, so return false
        // when used properly, UI updating should be done in the appropriate callbacks
        return false;
    }

    public void runAsync() {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                ThreadableAlgorithm.this.runSync();
            }
        });
        t.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                e.printStackTrace(System.err);
            }
        });
        t.start();
    }

    public abstract boolean runSync();

    public abstract void setStatus(String text);

}
