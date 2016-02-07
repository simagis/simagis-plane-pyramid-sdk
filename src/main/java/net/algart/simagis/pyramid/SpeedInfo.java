/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2016 Daniel Alievsky, AlgART Laboratory (http://algart.net)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.algart.simagis.pyramid;

import java.util.Locale;

class SpeedInfo {
    double totalMemory = 0.0;
    double elapsedTime = 0.0;
    long lastGcTime = System.currentTimeMillis();

    public String update(long memory, long time) {
        return update(memory, time, false);
    }

    public String update(long memory, long time, boolean enforceGc) {
        final String result;
        boolean doGc = false;
        synchronized (this) {
            totalMemory += memory;
            elapsedTime += time;
            final long t = System.currentTimeMillis();
            if (enforceGc) {
                doGc = ScalablePlanePyramidSource.TIME_ENFORCING_GC > 0
                    && (t - lastGcTime) > ScalablePlanePyramidSource.TIME_ENFORCING_GC;
                if (doGc) {
                    lastGcTime = t;
                }
            }
            result = String.format(Locale.US,
                "%.1f MB / %.3f sec = %.3f MB/sec%s",
                totalMemory / 1048576.0,
                elapsedTime * 1e-9,
                totalMemory / 1048576.0 / (elapsedTime * 1e-9),
                doGc ? " [GC enforced by " + this + "]" : "");
        }
        if (doGc) {
            System.gc();
        }
        return result;
    }
}
