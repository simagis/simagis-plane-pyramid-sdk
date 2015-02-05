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

package com.simagis.pyramid.sources;

import com.simagis.pyramid.PlanePyramidSource;
import net.algart.arrays.*;
import net.algart.math.functions.Func;
import net.algart.math.functions.LinearOperator;

import java.util.Locale;

public final class RotatingPlanePyramidSource
    extends AbstractArrayProcessorWithContextSwitching
    implements PlanePyramidSource
{
    private static final boolean DEBUG_MODE = true;
    private static final int DEBUG_LEVEL = 1;

    public static enum RotationMode {
        NONE(1, 0, 0, 0, false),

        CLOCKWISE_90(0, 1, 0, 1, true),

        CLOCKWISE_180(-1, 0, 1, 1, false),

        CLOCKWISE_270(0, -1, 1, 0, true);

        final long cos; // long field necessary for precise calculation of the bounds of the rectangular area
        final long sin;
        final long bX;
        final long bY;
        final boolean switchWidthAndHeight;

        private RotationMode(long cos, long sin, long bX, long bY, boolean switchWidthAndHeight) {
            this.cos = cos;
            this.sin = sin;
            this.bX = bX;
            this.bY = bY;
            this.switchWidthAndHeight = switchWidthAndHeight;
        }

        public static RotationMode valueOf(int clockwiseRotationInDegrees) {
            switch (clockwiseRotationInDegrees) {
                case 0:
                    return NONE;
                case 90:
                    return CLOCKWISE_90;
                case 180:
                    return CLOCKWISE_180;
                case 270:
                    return CLOCKWISE_270;
                default:
                    throw new IllegalArgumentException(RotationMode.class.getCanonicalName()
                        + " does not support rotation by " + clockwiseRotationInDegrees + " degree");
            }
        }

        public static boolean isAngleSupported(int clockwiseRotationInDegrees) {
            switch (clockwiseRotationInDegrees) {
                case 0:
                case 90:
                case 180:
                case 270:
                    return true;
                default:
                    return false;
            }
        }

        public final LinearOperator operator(long width, long height) {
            return LinearOperator.getInstance(a(), b(width, height));
        }

        public final long[] correctDimensions(long[] dimensionsToCorrect) {
            if (switchWidthAndHeight) {
                dimensionsToCorrect = dimensionsToCorrect.clone();
                long temp = dimensionsToCorrect[2];
                dimensionsToCorrect[2] = dimensionsToCorrect[1];
                dimensionsToCorrect[1] = temp;
            }
            return dimensionsToCorrect;
        }

        public final long[] correctFromAndTo(long width, long height, long fromX, long fromY, long toX, long toY) {
            long rotatedFromX = cos * fromX + sin * fromY + bX(width);
            long rotatedFromY = -sin * fromX + cos * fromY + bY(height);
            long rotatedDimX = cos * (toX - fromX) + sin * (toY - fromY);
            long rotatedDimY = -sin * (toX - fromX) + cos * (toY - fromY);
            long newFromX = rotatedDimX >= 0 ? rotatedFromX : rotatedFromX + rotatedDimX + 1;
            long newFromY = rotatedDimY >= 0 ? rotatedFromY : rotatedFromY + rotatedDimY + 1;
            long newToX = newFromX + Math.abs(rotatedDimX);
            long newToY = newFromY + Math.abs(rotatedDimY);
            return new long[] {newFromX, newFromY, newToX, newToY};
        }

        public Matrix<? extends PArray> asRotated(Matrix<? extends PArray> m) {
            final long[] newDimensions = correctDimensions(m.dimensions());
            if (Arrays.isNCopies(m.array())) {
                assert Arrays.longMul(newDimensions) == m.size();
                // - it is convenient that it is so, because allows simply to use the same array
                return Matrices.matrix(m.array(), newDimensions);
            }
            final LinearOperator operator = operator(m.dim(1), m.dim(2));
            final Func source = Matrices.asInterpolationFunc(m, Matrices.InterpolationMethod.STEP_FUNCTION, DEBUG_MODE);
            final Func rotated = operator.apply(source);
            return Matrices.asCoordFuncMatrix(rotated, m.type(PArray.class), newDimensions);
        }

        private double[] a() {
            return new double[] {
                1.0, 0.0, 0.0, // coordinate #0 is 0,1,2 for R,G,B
                0.0, cos, sin,
                0.0, -sin, cos,
            };
        }

        private double[] b(long width, long height) {
            return new double[] {
                0.0,
                bX(width),
                bY(height),
            };
        }

        private long bX(long width) {
            return bX * (width - 1);
        }

        private long bY(long height) {
            return bY * (height - 1);
        }
    }

    private PlanePyramidSource parent;
    private final RotationMode rotationMode;

    private RotatingPlanePyramidSource(PlanePyramidSource parent, RotationMode rotationMode) {
        super(parent instanceof ArrayProcessor && ((ArrayProcessor) parent).context() != null ?
            ((ArrayProcessor) parent).context() : null);
        if (parent == null) {
            throw new NullPointerException("Null parent");
        }
        if (rotationMode == null) {
            throw new NullPointerException("Null rotationMode");
        }
        assert rotationMode != RotationMode.NONE;
        this.parent = parent;
        this.rotationMode = rotationMode;
    }

    public static PlanePyramidSource newInstance(PlanePyramidSource parent, RotationMode rotationMode) {
        if (parent == null) {
            throw new NullPointerException("Null parent");
        }
        if (rotationMode == RotationMode.NONE) {
            return parent;
        }
        return new RotatingPlanePyramidSource(parent, rotationMode);
    }

    @Override
    public ArrayProcessorWithContextSwitching context(ArrayContext newContext) {
        RotatingPlanePyramidSource result = (RotatingPlanePyramidSource) super.context(newContext);
        if (result.parent instanceof ArrayProcessorWithContextSwitching) {
            result.parent = (PlanePyramidSource)
                ((ArrayProcessorWithContextSwitching) result.parent).context(newContext);
        }
        return result;
    }

    public int numberOfResolutions() {
        return parent.numberOfResolutions();
    }

    public int compression() {
        return parent.compression();
    }

    public int bandCount() {
        return parent.bandCount();
    }

    public boolean isResolutionLevelAvailable(int resolutionLevel) {
        return parent.isResolutionLevelAvailable(resolutionLevel);
    }

    public boolean[] getResolutionLevelsAvailability() {
        return parent.getResolutionLevelsAvailability();
    }

    public long[] dimensions(int resolutionLevel) {
        return rotationMode.correctDimensions(parent.dimensions(resolutionLevel));
    }

    public boolean isElementTypeSupported() {
        return parent.isElementTypeSupported();
    }

    public Class<?> elementType() throws UnsupportedOperationException {
        return parent.elementType();
    }

    public Matrix<? extends PArray> readSubMatrix(int resolutionLevel, long fromX, long fromY, long toX, long toY) {
        long t1 = System.nanoTime();
        long[] parentDim = parent.dimensions(resolutionLevel);
        long[] fromAndTo = rotationMode.correctFromAndTo(parentDim[1], parentDim[2], fromX, fromY, toX, toY);
        Matrix<? extends PArray> parentSubMatrix = parentWithSubtaskContext().readSubMatrix(
            resolutionLevel, fromAndTo[0], fromAndTo[1], fromAndTo[2], fromAndTo[3]);
        long t2 = System.nanoTime();
        Matrix<? extends PArray> rotated = rotated(parentSubMatrix);
        long t3 = System.nanoTime();
        if (DEBUG_LEVEL >= 2) {
            System.out.printf(Locale.US,
                "%s completed reading %d..%d x %d..%d, level %d in %.3f ms (%.3f parent reading + %.3f rotation)%n",
                ((Object) this).getClass().getSimpleName(), fromX, toX, fromY, toY, resolutionLevel,
                (t3 - t1) * 1e-6, (t2 - t1) * 1e-6, (t3 - t2) * 1e-6);
        }
        return rotated;
    }

    public boolean isFullMatrixSupported() {
        return parent.isFullMatrixSupported();
    }

    public Matrix<? extends PArray> readFullMatrix(int resolutionLevel)
        throws UnsupportedOperationException
    {
        return rotated(parentWithSubtaskContext().readFullMatrix(resolutionLevel));
    }

    public boolean isSpecialMatrixSupported(SpecialImageKind kind) {
        return parent.isSpecialMatrixSupported(kind);
    }

    public Matrix<? extends PArray> readSpecialMatrix(SpecialImageKind kind) {
        return rotated(parentWithSubtaskContext().readSpecialMatrix(kind));
    }

    public boolean isDataReady() {
        return parent.isDataReady();
    }

    public void loadResources() {
        parent.loadResources();
    }

    public void freeResources(FlushMethod flushMethod) {
        parent.freeResources(flushMethod);
    }

    private PlanePyramidSource parentWithSubtaskContext() {
        PlanePyramidSource parentWithContext = parent;
        ArrayContext context = context();
        if (context != null && parentWithContext instanceof ArrayProcessorWithContextSwitching) {
            parentWithContext = (PlanePyramidSource)
                ((ArrayProcessorWithContextSwitching) parentWithContext).context(context.part(0.0, 0.5));
        }
        return parentWithContext;
    }

    private Matrix<? extends PArray> rotated(Matrix<? extends PArray> m) {
        final long[] newDimensions = rotationMode.correctDimensions(m.dimensions());
        if (Arrays.isNCopies(m.array())) {
            assert Arrays.longMul(newDimensions) == m.size();
            // - it is convenient, because allows simply to use the same array
            return Matrices.matrix(m.array(), newDimensions);
        }
        final Matrix<? extends PArray> lazy = rotationMode.asRotated(m);
        final Matrix<? extends UpdatablePArray> actual = memoryModel().newMatrix(
            Arrays.SystemSettings.maxTempJavaMemory(),
            UpdatablePArray.class,
            lazy.elementType(),
            lazy.dimensions());
        Matrices.copy(context() == null ? null : context().part(0.5, 1.0), actual, lazy, 0, false);
        return actual;
    }
}
