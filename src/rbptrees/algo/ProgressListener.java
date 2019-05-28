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

public interface ProgressListener {

    /**
     * Called by CPLEX when solving was aborted.
     */
    public void onAbort();

    /**
     * Called by CPLEX when solving has finished.
     */
    public void onDone(double secondsRunning);

    /**
     * Called by CPLEX when there is progress to report.
     *
     * @param gap The integrality gap as a percentage.
     * @param secondsRunning Time in seconds that solver is running.
     */
    public void onProgress(double gap, double secondsRunning);

    /**
     * Called by CPLEX to know if solving should be aborted. Must return
     * {@code true} if solving should be aborted, and {@code false} if solving
     * may continue.
     *
     * @param gap The integrality gap as a percentage.
     * @param secondsRunning Time in seconds that solver is running.
     */
    public boolean shouldAbort(double gap, double secondsRunning);

}
