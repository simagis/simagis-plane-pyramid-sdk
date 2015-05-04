/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2015 Daniel Alievsky, AlgART Laboratory (http://algart.net)
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

package net.algart.simagis.pyramid.recognition;

import net.algart.arrays.*;
import net.algart.math.IPoint;
import net.algart.math.IRectangularArea;
import net.algart.math.functions.Func;
import net.algart.math.patterns.Patterns;
import net.algart.math.patterns.UniformGridPattern;
import net.algart.matrices.morphology.BasicMorphology;
import net.algart.matrices.morphology.IterativeErosion;
import net.algart.simagis.pyramid.AbstractPlanePyramidSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class SquaresAtObject {

    private final Matrix<? extends BitArray> sourceMatrix;
    private final Matrix<UpdatableBitArray> workMatrix;
    private final int dimCount;

    private final List<IRectangularArea> foundSquares = new ArrayList<IRectangularArea>();
    private volatile int maxNumberOfSquares = 10000;
    private volatile long fixedSquareSide = 100;
    private volatile long overlapOfSquares = 0;

    private SquaresAtObject(Matrix<? extends BitArray> sourceMatrix) {
        Objects.requireNonNull(sourceMatrix, "Null source matrix");
        this.sourceMatrix = sourceMatrix;
        this.workMatrix = Arrays.SMM.newBitMatrix(sourceMatrix.dimensions());
        this.dimCount = workMatrix.dimCount();
        long[] from = new long[dimCount];
        long[] to = new long[dimCount];
        for (int k = 0; k < dimCount; k++) {
            from[k] = 1;
            to[k] = workMatrix.dim(k) - 1;
            if (from[k] >= to[k]) {
                return;
                // to small matrix, less than 3x3x...
            }
        }
        workMatrix.subMatrix(from, to).array().copy(sourceMatrix.subMatrix(from, to).array());
        // - the edge of matrix stays zero for correct behaviour of the erosion
    }

    public static SquaresAtObject getInstance(Matrix<? extends BitArray> sourceMatrix) {
        return new SquaresAtObject(sourceMatrix);
    }

    public Matrix<? extends BitArray> getSourceMatrix() {
        return sourceMatrix;
    }

    public Matrix<UpdatableBitArray> getWorkMatrix() {
        return workMatrix;
    }

    public List<IRectangularArea> getFoundSquares() {
        return Collections.unmodifiableList(foundSquares);
    }

    public int getMaxNumberOfSquares() {
        return maxNumberOfSquares;
    }

    public void setMaxNumberOfSquares(int maxNumberOfSquares) {
        if (maxNumberOfSquares < 0) {
            throw new IllegalArgumentException("Negative maxNumberOfSquares");
        }
        this.maxNumberOfSquares = maxNumberOfSquares;
    }

    public long getFixedSquareSide() {
        return fixedSquareSide;
    }

    public void setFixedSquareSide(long fixedSquareSide) {
        if (fixedSquareSide <= 0) {
            throw new IllegalArgumentException("Zero or negative fixedSquareSide");
        }
        this.fixedSquareSide = fixedSquareSide;
    }

    public long getOverlapOfSquares() {
        return overlapOfSquares;
    }

    public void setOverlapOfSquares(long overlapOfSquares) {
        this.overlapOfSquares = overlapOfSquares;
    }

    public void findSquaresWithFixedSizes(ArrayContext arrayContext) {
        if (overlapOfSquares >= fixedSquareSide) {
            throw new IllegalStateException("Cannot find squares of fixed sizes with overlap " + overlapOfSquares
                + " >= square side" + fixedSquareSide);
        }
        for (int squareIndex = 0; squareIndex < maxNumberOfSquares; squareIndex++) {
            final ArrayContext ac = arrayContext == null ? null :
                arrayContext.part(squareIndex, squareIndex + 1, maxNumberOfSquares);
            long t1 = System.nanoTime();
            final BasicMorphology morphology = BasicMorphology.getInstance(ac);
            final UniformGridPattern requiredSquarePattern = Patterns.newRectangularIntegerPattern(
                IPoint.valueOfEqualCoordinates(dimCount, -fixedSquareSide / 2),
                IPoint.valueOfEqualCoordinates(dimCount, -fixedSquareSide / 2 + fixedSquareSide - 1));
            // - the side of such pattern is fixedSquareSide
            final Matrix<? extends UpdatablePArray> erosion = morphology.erosion(workMatrix, requiredSquarePattern);
            long t2 = System.nanoTime();
            final long firstNonZero = ((BitArray) erosion.array()).indexOf(0, Long.MAX_VALUE, true);
            final IRectangularArea square, reducedSquare;
            if (firstNonZero != -1) {
                final IPoint center = IPoint.valueOf(workMatrix.coordinates(firstNonZero, null));
                square = IRectangularArea.valueOf(
                    center.add(IPoint.valueOfEqualCoordinates(dimCount, -fixedSquareSide / 2)),
                    center.add(IPoint.valueOfEqualCoordinates(dimCount, -fixedSquareSide / 2 + fixedSquareSide - 1)));
                // - Note: this square is an inversion of the pattern
                final long reducedSide = fixedSquareSide - overlapOfSquares;
                reducedSquare = IRectangularArea.valueOf(
                    center.add(IPoint.valueOfEqualCoordinates(dimCount, -reducedSide / 2)),
                    center.add(IPoint.valueOfEqualCoordinates(dimCount, -reducedSide / 2 + reducedSide - 1)));
                foundSquares.add(square);
            } else {
                square = reducedSquare = null;
            }
            long t3 = System.nanoTime();
            debug(2, "Square #%d/%d %s in %.3f ms (%.3f erosion + %.3f finding center)%n",
                squareIndex + 1, maxNumberOfSquares,
                square == null ? "not found" : "found (" + square + ")",
                (t3 - t1) * 1e-6, (t2 - t1) * 1e-6, (t3 - t2) * 1e-6);
            if (square == null) {
                debug(2, "Finishing loop: all squares are already found%n");
                break;
            }
            Matrix<UpdatableBitArray> squareSubMatrix = workMatrix.subMatrix(reducedSquare,
                Matrix.ContinuationMode.NULL_CONSTANT);
            // - contination mode is set only to be on the safe side:
            // correct algorithm should not go outside the matrix
            squareSubMatrix.array().fill(false);
        }
    }

    public void findSquaresWithDecreasingSizes(ArrayContext arrayContext) {
        for (int squareIndex = 0; squareIndex < maxNumberOfSquares; squareIndex++) {
            final ArrayContext ac = arrayContext == null ? null :
                arrayContext.part(squareIndex, squareIndex + 1, maxNumberOfSquares);
            long t1 = System.nanoTime();
            final IterativeErosion iterativeErosion = IterativeErosion.getInstance(
                BasicMorphology.getInstance(ac),
                UpdatableIntArray.class,
                // 31-bit precision guarangees correct work even for very large matrices;
                // in a very improbable case of overflow we just will not able to find the maximum exactly
                workMatrix,
                Patterns.newRectangularIntegerPattern(
                    IPoint.valueOfEqualCoordinates(dimCount, -1),
                    IPoint.valueOfEqualCoordinates(dimCount, 1)));
            long t2 = System.nanoTime();
            iterativeErosion.process();
            long t3 = System.nanoTime();
            final Arrays.MinMaxInfo minMaxInfo = new Arrays.MinMaxInfo();
            final UpdatablePIntegerArray resultArray = (UpdatablePIntegerArray) iterativeErosion.result().array();
            Arrays.rangeOf(null, resultArray, minMaxInfo);
            final IPoint center = IPoint.valueOf(workMatrix.coordinates(minMaxInfo.indexOfMax(), null));
            final long distanceToEdge = resultArray.getLong(minMaxInfo.indexOfMax());
            final IRectangularArea square = IRectangularArea.valueOf(
                center.add(IPoint.valueOfEqualCoordinates(dimCount, -distanceToEdge)),
                center.add(IPoint.valueOfEqualCoordinates(dimCount, distanceToEdge)));
            final long reducedSide = 2 * distanceToEdge + 1 - overlapOfSquares;
            long t4 = System.nanoTime();
            debug(2, "Square #%d/%d found (%s) in %.3f ms (%.3f preparig + %.3f erosion + %.3f finding center)%n",
                squareIndex + 1, maxNumberOfSquares, square,
                (t4 - t1) * 1e-6, (t2 - t1) * 1e-6, (t3 - t2) * 1e-6, (t4 - t3) * 1e-6);
            if (reducedSide <= 1) {
                debug(2, "Finishing loop: all squares are already found%n");
                break;
            }
            foundSquares.add(square);
            final IRectangularArea reducedSquare = IRectangularArea.valueOf(
                center.add(IPoint.valueOfEqualCoordinates(dimCount, -reducedSide / 2)),
                center.add(IPoint.valueOfEqualCoordinates(dimCount, -reducedSide / 2 + reducedSide - 1)));
            Matrix<UpdatableBitArray> squareSubMatrix = workMatrix.subMatrix(reducedSquare,
                Matrix.ContinuationMode.NULL_CONSTANT);
            // - contination mode is set only to be on the safe side:
            // correct algorithm should not go outside the matrix
            squareSubMatrix.array().fill(false);
        }
    }

    private static void debug(int level, String format, Object... args) {
        AbstractPlanePyramidSource.debug(level, format, args);
    }

}
