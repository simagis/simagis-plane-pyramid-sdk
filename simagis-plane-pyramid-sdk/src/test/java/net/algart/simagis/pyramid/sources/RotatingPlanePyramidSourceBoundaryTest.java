/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Daniel Alievsky, AlgART Laboratory (http://algart.net)
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

package net.algart.simagis.pyramid.sources;

import net.algart.arrays.Arrays;
import net.algart.arrays.TooLargeArrayException;
import net.algart.math.IPoint;

import java.util.Random;

public class RotatingPlanePyramidSourceBoundaryTest {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.printf("Usage:%n%s dimX dimY%n", RotatingPlanePyramidSourceBoundaryTest.class.getName());
            return;
        }
        final int dimX = Integer.parseInt(args[0]);
        final int dimY = Integer.parseInt(args[1]);
        final int numberOfTests = 10000000;
        System.out.printf("Testing random areas in %d x %d%n", dimX, dimY);
        Random rnd = new Random(157);
        for (RotatingPlanePyramidSource.RotationMode mode : RotatingPlanePyramidSource.RotationMode.values()) {
            System.out.printf("%nTesting %s...        %n", mode);
            for (int k = 0; k < numberOfTests; k++) {
                final long[] rotatedDim = mode.correctDimensions(new long[] {1, dimX, dimY});
                long fromX = Math.max(0, rnd.nextInt(dimX + 100) - 50);
                long fromY = Math.max(0, rnd.nextInt(dimY + 100) - 50);
                if (fromX > dimX) {
                    fromX = dimX;
                }
                if (fromY > dimY) {
                    fromY = dimY;
                }
                long toX, toY;
                for (; ; ) {
                    toX = Math.max(0, rnd.nextInt(dimX + 100) - 50);
                    toY = Math.max(0, rnd.nextInt(dimY + 100) - 50);
                    if (toX > dimX) {
                        toX = dimX;
                    }
                    if (toY > dimY) {
                        toY = dimY;
                    }
                    if (toX >= fromX && toY >= fromY) {
                        break;
                    }
                }
                if (k % 1000 == 0 || k == numberOfTests - 1) {
                    System.out.printf("\rTest #%d (%.1f%%)...",
                        k, 100.0 * (double) (k + 1) / (double) numberOfTests);
                }
                final long newDimX = rotatedDim[1];
                final long newDimY = rotatedDim[2];
                final IPoint newFrom = mode.correctPoint(newDimX, newDimY, IPoint.valueOf(fromX, fromY));
                if (newFrom.x() < 0 || newFrom.y() < 0 || newFrom.x() > newDimX || newFrom.y() > newDimY)
                {
                    mode.correctPoint(newDimX, newDimY, IPoint.valueOf(fromX, fromY)); // - for debugging
                    throw new AssertionError("Illegal rotated point " + newFrom
                        + ": must be in ranges 0.." + newDimX + ", 0.." + newDimY + "; "
                        + "source point " + IPoint.valueOf(fromX, fromY));
                }

                final long[] newFromAndTo = mode.correctFromAndTo(newDimX, newDimY, fromX, fromY, toX, toY);
                final long newFromX = newFromAndTo[0];
                final long newFromY = newFromAndTo[1];
                final long newToX = newFromAndTo[2];
                final long newToY = newFromAndTo[3];
                if (Arrays.longMul(rotatedDim) == Long.MIN_VALUE) {
                    throw new TooLargeArrayException("Product of all dimensions >Long.MAX_VALUE");
                }
                if (newFromX < 0 || newFromY < 0
                    || newFromX > newToX || newFromY > newToY || newToX > newDimX || newToY > newDimY)
                {
                    mode.correctFromAndTo(newDimX, newDimY, fromX, fromY, toX, toY); // - for debugging
                    throw new AssertionError("Illegal rotated fromX..toX=" + newFromX + ".." + newToX
                        + " or fromY..toY=" + newFromY + ".." + newToY + ": must be in ranges 0.."
                        + newDimX + ", 0.." + newDimY + ", fromX<=toX, fromY<=toY; "
                        + "source rectangle " + fromX + ".." + toX + " x " + fromY + ".." + toY);
                }
            }
        }
    }
}
