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

import net.algart.arrays.*;
import net.algart.external.MatrixToBufferedImageConverter;
import net.algart.math.IPoint;
import net.algart.math.IRectangularArea;
import net.algart.math.Range;
import net.algart.math.functions.LinearFunc;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Logger;

public class ScalablePlanePyramidSource implements PlanePyramidSource {
    static final int TIME_ENFORCING_GC =
        Arrays.SystemSettings.getIntProperty("net.algart.simagis.pyramid.timeEnforcingGc", 0);
    // - in milliseconds; not used if 0

    private static final Logger LOGGER = Logger.getLogger(ScalablePlanePyramidSource.class.getName());

    private final MemoryModel memoryModel;
    private final PlanePyramidSource parent;

    private final int numberOfActualResolutions;
    private final int numberOfVirtualResolutions;
    private final List<long[]> dimensions;
    private final long dimX;
    private final long dimY;
    private final int compression;
    private final int bandCount;

    private volatile Class<?> elementType = null;
    // cache for elementType() method

    private volatile AveragingMode averagingMode = AveragingMode.DEFAULT;

    private final SpeedInfo pyramidSourceSpeedInfo = new SpeedInfo();
    private final SpeedInfo readImageSpeedInfo = new SpeedInfo();
    private final SpeedInfo readBufferedImageSpeedInfo = new SpeedInfo();

    public ScalablePlanePyramidSource(final PlanePyramidSource parent, int compression) {
        Objects.requireNonNull(parent, "Null parent source");
        this.memoryModel = findMemoryModel(parent);
        this.parent = parent;
        for (int level = 0, n = parent.numberOfResolutions(); level < n; level++) {
            if (!this.parent.isResolutionLevelAvailable(level)) {
                throw new IllegalArgumentException("Pyramid source must contain all resolution levels, "
                    + "but there is no level #" + level);
            }
        }
        long[] dimensions0 = this.parent.dimensions(0);
        this.dimX = dimensions0[DIM_WIDTH];
        this.dimY = dimensions0[DIM_HEIGHT];

        if (compression == 0) {
            compression = parent.compression();
        }
        this.compression = compression;
        this.numberOfActualResolutions = parent.numberOfResolutions();
        this.numberOfVirtualResolutions = compression == 0 ? this.numberOfActualResolutions :
            PlanePyramidTools.numberOfResolutions(dimX, dimY, this.compression, DEFAULT_MINIMAL_PYRAMID_SIZE);
        this.bandCount = parent.bandCount();
        long levelDimX = dimX;
        long levelDimY = dimY;
        this.dimensions = new ArrayList<long[]>();
        for (int k = 0; k < numberOfVirtualResolutions; k++) {
            this.dimensions.add(new long[] {bandCount, levelDimX, levelDimY});
            levelDimX /= compression;
            levelDimY /= compression;
        }
    }

    // context is useful for getting large matrices: it defines the memory model
    public static ScalablePlanePyramidSource newInstance(
        final PlanePyramidSource parent,
        final int compression)
    {
        return new ScalablePlanePyramidSource(parent, compression);
    }

    @Override
    public int numberOfResolutions() {
        return numberOfVirtualResolutions;
    }

    @Override
    public int bandCount() {
        return bandCount;
    }

    @Override
    public boolean isResolutionLevelAvailable(int resolutionLevel) {
        return true;
    }

    @Override
    public boolean[] getResolutionLevelsAvailability() {
        boolean[] result = new boolean[numberOfResolutions()];
        JArrays.fillBooleanArray(result, true);
        return result;
    }

    @Override
    public long[] dimensions(int resolutionLevel) {
        return dimensions.get(resolutionLevel).clone();
    }

    @Override
    public boolean isElementTypeSupported() {
        return true;
    }

    @Override
    public Class<?> elementType() {
        if (elementType == null) { // synchronization not necessary: following code always leads to the same results
            if (parent.isElementTypeSupported()) {
                elementType = parent.elementType();
            } else {
                // the parent source does not "know" the element type:
                // let's try to get a little matrix and get its element type
                elementType = parent.readSubMatrix(0, 0, 0, 1, 1).elementType();
            }
        }
        return elementType;
    }

    @Override
    public java.util.List<IRectangularArea> zeroLevelActualRectangles() {
        return parent.zeroLevelActualRectangles();
    }

    @Override
    public java.util.List<java.util.List<java.util.List<IPoint>>> zeroLevelActualAreaBoundaries() {
        return parent.zeroLevelActualAreaBoundaries();
    }

    @Override
    public Matrix<? extends PArray> readSubMatrix(
        final int resolutionLevel,
        final long fromX, final long fromY,
        final long toX, final long toY)
    {
        //TODO!! own compression
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isFullMatrixSupported() {
        return parent.isFullMatrixSupported();
    }

    @Override
    public Matrix<? extends PArray> readFullMatrix(int resolutionLevel) {
        if (!isFullMatrixSupported()) {
            throw new UnsupportedOperationException("readFullMatrix method is not supported");
        }
        //TODO!! own compression
        return parent.readFullMatrix(resolutionLevel);
    }

    @Override
    public boolean isSpecialMatrixSupported(SpecialImageKind kind) {
        return parent.isSpecialMatrixSupported(kind);
    }

    @Override
    public Matrix<? extends PArray> readSpecialMatrix(SpecialImageKind kind) {
        return parent.readSpecialMatrix(kind);
    }

    @Override
    public boolean isDataReady() {
        return parent.isDataReady();
    }

    @Override
    public int compression() {
        return compression;
    }

    public PlanePyramidSource parent() {
        return parent;
    }

    public long dimX() {
        return dimX;
    }

    public long dimY() {
        return dimY;
    }

    public AveragingMode getAveragingMode() {
        return averagingMode;
    }

    public void setAveragingMode(AveragingMode averagingMode) {
        if (averagingMode == null) {
            throw new NullPointerException("Null averagingMode");
        }
        this.averagingMode = averagingMode;
    }

    // Recommended for viewers
    public void forceAveragingBits() {
        if (averagingMode == AveragingMode.DEFAULT) {
            averagingMode = AveragingMode.AVERAGING;
        }
    }


    public double compression(int level) {
        if (level < 0) {
            throw new IllegalArgumentException("Negative level");
        }
        double c = 1.0;
        for (int k = 0; k < level; k++) {
            c *= compression;
        }
        return c;
    }

    public int maxLevel(double compression) {
        if (compression < 1.0) {
            throw new IllegalArgumentException("Compression must not be be less than 1.0");
        }
        int level = 0;
        for (double c = this.compression; c <= compression; c *= this.compression) {
            level++;
        }
        return level;
    }

    @Override
    public String additionalMetadata() {
        return parent.additionalMetadata();
    }

    @Override
    public void loadResources() {
        parent.loadResources();
    }

    @Override
    public void freeResources(FlushMethod flushMethod) {
        parent.freeResources(flushMethod);
    }

    public Matrix<? extends PArray> readImage(
        final long fromX, final long fromY, final long toX, final long toY,
        // position at level 0
        final double compression)
    {
        checkFromAndTo(fromX, fromY, toX, toY);
        long t1 = System.nanoTime();
        final int level = Math.min(maxLevel(compression), numberOfVirtualResolutions - 1);
        // - this call also checks that compression >= 1
        final ImageScaling scaling = new ImageScaling(fromX, fromY, toX, toY, level, compression);
        long t2 = System.nanoTime();
        Matrix<? extends PArray> m = scaling.scaleImage();
        long t3 = System.nanoTime();
        final String averageSpeed = readImageSpeedInfo.update(Matrices.sizeOf(m), t3 - t1, true);
        if (DEBUG_LEVEL >= 2) {
            Runtime runtime = Runtime.getRuntime();
            LOGGER.info(scaling.scaleImageTiming());
            LOGGER.info(String.format(Locale.US,
                "%s has read image (%d-bit, %d CPU for AlgART, used memory %.3f/%.3f MB, compression %.2f): "
                    + "%d..%d x %d..%d (%d x %d%s) in %.3f ms (%.3f init + %.3f scaled reading), "
                    + "%.3f MB/sec, average %s (source: %s)%n",
                ScalablePlanePyramidSource.class.getSimpleName(),
                Arrays.SystemSettings.isJava32() ? 32 : 64,
                Arrays.SystemSettings.cpuCount(),
                (runtime.totalMemory() - runtime.freeMemory()) / 1048576.0, runtime.maxMemory() / 1048576.0,
                compression, fromX, toX, fromY, toY, toX - fromX, toY - fromY,
                Arrays.isNCopies(m.array()) ? ", CONSTANT" : "",
                (t3 - t1) * 1e-6, (t2 - t1) * 1e-6, (t3 - t2) * 1e-6,
                Matrices.sizeOf(m) / 1048576.0 / ((t3 - t1) * 1e-9), averageSpeed,
                parent.getClass().getSimpleName()
            ));
        }
        return m;
    }

    public BufferedImage readBufferedImage(
        final long fromX, final long fromY, final long toX, final long toY,
        // position at level 0
        final double compression,
        final MatrixToBufferedImageConverter converter)
    {
        Objects.requireNonNull(converter, "Null converter");
        checkFromAndTo(fromX, fromY, toX, toY);
        long t1 = System.nanoTime();
        final int level = Math.min(maxLevel(compression), numberOfVirtualResolutions - 1);
        // - this call also checks that compression >= 1
        final ImageScaling scaling = new ImageScaling(fromX, fromY, toX, toY, level, compression);
        long t2 = System.nanoTime();
        Matrix<? extends PArray> m = scaling.scaleImage();
        long t3 = System.nanoTime();
        if (converter.byteArrayRequired() && m.elementType() != byte.class) {
            double max = m.array().maxPossibleValue(1.0);
            m = Matrices.asFuncMatrix(LinearFunc.getInstance(0.0, 255.0 / max), ByteArray.class, m);
        }
        BufferedImage bufferedImage = converter.toBufferedImage(m);
        long t4 = System.nanoTime();
        final String averageSpeed = readBufferedImageSpeedInfo.update(Matrices.sizeOf(m), t4 - t1, true);
        if (DEBUG_LEVEL >= 2) {
            Runtime runtime = Runtime.getRuntime();
            LOGGER.info(scaling.scaleImageTiming());
            LOGGER.info(String.format(Locale.US,
                "%s has read buffered image (%d-bit, %d CPU for AlgART, used memory %.3f/%.3f MB, compression %.2f): "
                    + "%d..%d x %d..%d (%d x %d) in %.3f ms "
                    + "(%.3f init + %.3f scaled reading + %.3f conversion), "
                    + "%.3f MB/sec, average %s (source: %s)%n",
                ScalablePlanePyramidSource.class.getSimpleName(),
                Arrays.SystemSettings.isJava32() ? 32 : 64,
                Arrays.SystemSettings.cpuCount(),
                (runtime.totalMemory() - runtime.freeMemory()) / 1048576.0, runtime.maxMemory() / 1048576.0,
                compression, fromX, toX, fromY, toY, toX - fromX, toY - fromY,
                (t4 - t1) * 1e-6, (t2 - t1) * 1e-6, (t3 - t2) * 1e-6, (t4 - t3) * 1e-6,
                Matrices.sizeOf(m) / 1048576.0 / ((t4 - t1) * 1e-9), averageSpeed,
                parent.getClass().getSimpleName()
            ));
        }
        return bufferedImage;
    }

    public String toString() {
        return "ScalablePlanePyramid " + bandCount + "x" + dimX() + "x" + dimY()
            + ", " + numberOfVirtualResolutions + " levels, compression " + compression
            + ", based on " + parent;
    }

    private Matrix<? extends PArray> callAndCheckParentReadSubMatrix(
        int resolutionLevel,
        long fromX,
        long fromY,
        long toX,
        long toY)
    {
        long t1 = System.nanoTime();
        Matrix<? extends PArray> m = parent.readSubMatrix(resolutionLevel, fromX, fromY, toX, toY);
        long t2 = System.nanoTime();
        if (m == null || m.dimCount() != 3 || m.dim(0) != bandCount
            || m.dim(1) != toX - fromX || m.dim(2) != toY - fromY)
        {
            throw new AssertionError("Invalid implementation of " + parent.getClass()
                + ".readSubMatrix (fromX = "
                + fromX + ", fromY = " + fromY + ", toX = " + toX + ", toY = " + toY
                + "): incorrect dimensions of the returned matrix " + m);
        }
        final String averageSpeed = pyramidSourceSpeedInfo.update(Matrices.sizeOf(m), t2 - t1);
        if (DEBUG_LEVEL >= 2) {
            LOGGER.info(String.format(Locale.US,
                "%s.callAndCheckParentReadSubMatrix timing (level %d, %d..%d x %d..%d (%d x %d%s): "
                    + "%.3f ms, %.3f MB/sec, average %s (source: %s)%n",
                ScalablePlanePyramidSource.class.getSimpleName(),
                resolutionLevel, fromX, toX, fromY, toY, toX - fromX, toY - fromY,
                Arrays.isNCopies(m.array()) ? ", CONSTANT" : "",
                (t2 - t1) * 1e-6, Matrices.sizeOf(m) / 1048576.0 / ((t2 - t1) * 1e-9), averageSpeed,
                parent.getClass().getSimpleName()
            ));
        }
        return m;
    }

    private Matrix<UpdatablePArray> newResultMatrix(Class<?> elementType, long dimX, long dimY) {
        Matrix<UpdatablePArray> result = memoryModel.newMatrix(
            Arrays.SystemSettings.maxTempJavaMemory(),
            UpdatablePArray.class,
            elementType,
            bandCount(), dimX, dimY);
        if (!SimpleMemoryModel.isSimpleArray(result.array())) {
            result = result.tile(result.dim(0), 1024, 1024);
        }
        return result;
    }

    private static MemoryModel findMemoryModel(PlanePyramidSource parent) {
        ArrayContext arrayContext = parent instanceof ArrayProcessor ?
            ((ArrayProcessor) parent).context() :
            null;
        return arrayContext == null ? Arrays.SMM : arrayContext.getMemoryModel();
    }

    private static void checkFromAndTo(long fromX, long fromY, long toX, long toY) {
        if (fromX > toX || fromY > toY) {
            throw new IndexOutOfBoundsException("Illegal fromX..toX=" + fromX + ".." + toX
                + " or fromY..toY=" + fromY + ".." + toY + ": must be fromX<=toX, fromY<=toY");
        }
    }

    private class ImageScaling {
        // The following fields are filled by the constructor:
        final int level;
        final double totalCompression;
        final long roundedTotalCompression;
        final long levelCompression;
        final long zeroLevelFromX;
        final long zeroLevelFromY;
        final long zeroLevelToX;
        final long zeroLevelToY;
        final long levelFromX;
        final long levelFromY;
        long levelToX;
        long levelToY;
        long newDimX;
        long newDimY;
        final double additionalCompression;
        final boolean needAdditionalCompression;
        final boolean additionalCompressionIsInteger;

        private long scaleImageExtractingTime = 0;
        private long scaleImageCompressionTime = 0;

        ImageScaling(
            final long zeroLevelFromX,
            final long zeroLevelFromY,
            final long zeroLevelToX,
            final long zeroLevelToY,
            final int level,
            final double compression)
        {
            assert zeroLevelFromX <= zeroLevelToX;
            assert zeroLevelFromY <= zeroLevelToY;
            this.zeroLevelFromX = zeroLevelFromX;
            this.zeroLevelFromY = zeroLevelFromY;
            this.zeroLevelToX = zeroLevelToX;
            this.zeroLevelToY = zeroLevelToY;
            assert level >= 0;
            assert compression > 0;
            this.level = level;
            final double levelCompression = compression(level);
            assert levelCompression > 0 && levelCompression <= compression;
            this.totalCompression = compression;
            this.roundedTotalCompression = StrictMath.round(compression);
            this.levelFromX = safeFloor(zeroLevelFromX / levelCompression);
            this.levelFromY = safeFloor(zeroLevelFromY / levelCompression);
            this.levelToX = safeFloor(zeroLevelToX / levelCompression);
            this.levelToY = safeFloor(zeroLevelToY / levelCompression);
            this.additionalCompression = compression / levelCompression;
            this.levelCompression = Math.round(levelCompression);
            // - Math.round returns the same number, excepting a case of 63-bit overflow (levelCompression > 2^63)
            this.needAdditionalCompression = Math.abs(additionalCompression - 1.0) > 1e-4;
            this.newDimX = levelToX - levelFromX;
            this.newDimY = levelToY - levelFromY;
            if (needAdditionalCompression) {
                // we provide a guarantee that the sizes of the scaled matrix are always newDimX * newDimY;
                // so, we must retain previous newDimX and newDimY if we are not going to additionally scale
                newDimX = Math.min(newDimX, safeFloor(newDimX / additionalCompression));
                newDimY = Math.min(newDimY, safeFloor(newDimY / additionalCompression));
                // - "min" here is to be on the safe side
            }
            if (needAdditionalCompression) {
                assert additionalCompression > 1.0;
                final long intAdditionalCompression = Math.round(additionalCompression);
                this.additionalCompressionIsInteger =
                    Math.abs(additionalCompression - intAdditionalCompression) < 1e-7;
                if (additionalCompressionIsInteger) {
                    levelToX = Math.min(levelToX, levelFromX + intAdditionalCompression * newDimX);
                    levelToY = Math.min(levelToY, levelFromY + intAdditionalCompression * newDimY);
                    // in other words, we prefer to lose 1-2 last pixels, but provide strict
                    // integer compression: AlgART libraries are optimized for this situation
//                System.out.printf("Correcting area: ..%d x ..%d%n", this.levelToX, this.levelToY);
                }
            } else {
                this.additionalCompressionIsInteger = false;
            }
            if (DEBUG_LEVEL >= 4) {
                LOGGER.info(String.format(Locale.US,
                    "Resizing %d..%d x %d..%d (level %d) into %dx%d, additional compression %.3f"
                        + " (%.3f/X, %.3f/Y, %snecessary%s)%n",
                    levelFromX, levelToX - 1, levelFromY, levelToY - 1, level, newDimX, newDimY,
                    additionalCompression,
                    (double) (levelToX - levelFromX) / newDimX, (double) (levelToY - levelFromY) / newDimY,
                    needAdditionalCompression ? "" : "NOT ",
                    additionalCompressionIsInteger ? " and is integer" : ""));
            }
        }

        Matrix<? extends PArray> scaleImage() {
            long t1 = System.nanoTime();
            Matrix<? extends PArray> sourceData = callAndCheckParentReadSubMatrix(
                level, levelFromX, levelFromY, levelToX, levelToY);
            long t2 = System.nanoTime();
            scaleImageExtractingTime = t2 - t1;
            if (needAdditionalCompression) {
                final boolean convertBitToByte = needAdditionalCompression
                    && averagingMode == AveragingMode.AVERAGING
                    && elementType == boolean.class;
                if (convertBitToByte) {
                    Range srcRange = Range.valueOf(0.0, sourceData.array().maxPossibleValue(1.0));
                    Range destRange = Range.valueOf(0.0, Arrays.maxPossibleIntegerValue(ByteArray.class));
                    sourceData = Matrices.asFuncMatrix(LinearFunc.getInstance(destRange, srcRange),
                        ByteArray.class, sourceData);
                }
                Matrix<? extends UpdatablePArray> resized = newResultMatrix(
                    sourceData.elementType(), newDimX, newDimY);
                doResize(resized, sourceData);
                long t3 = System.nanoTime();
                scaleImageCompressionTime = t3 - t2;
//                System.out.printf("Additional compression in %.2f times: %.3f ms%n",
//                    additionalCompression, (t3 - t2) * 1e-6);
                return resized;
            } else {
                assert sourceData.dim(DIM_WIDTH) == newDimX;
                assert sourceData.dim(DIM_HEIGHT) == newDimY;
                return sourceData;
            }
        }

        String scaleImageTiming() {
            return String.format(Locale.US,
                "%s.scaleImage timing: "
                    + "%.3f ms = %.3f extracting data + %.3f compression",
                getClass().getSimpleName(),
                (scaleImageExtractingTime + scaleImageCompressionTime) * 1e-6,
                scaleImageExtractingTime * 1e-6,
                scaleImageCompressionTime * 1e-6
            ) + (
                needAdditionalCompression ?
                    String.format(Locale.US, " (additional compressing %dx%d to %dx%d in %.3f times)%n",
                        levelToX - levelFromX, levelToY - levelFromY, newDimX, newDimY, additionalCompression) :
                    String.format(" (additional compression skipped)%n"));
        }

        private void doResize(
            Matrix<? extends UpdatablePArray> resized,
            Matrix<? extends PArray> source)
        {
//            System.out.println("Resizing to " + resized + " from " + source);
            final Matrices.ResizingMethod resizingMethod = averagingMode.averagingMethod(source);
            if (additionalCompressionIsInteger) {
                Matrices.resize(null, resizingMethod, resized, source);
            } else {
                final double scale = 1.0 / additionalCompression;
                Matrices.copy(null, resized, Matrices.asResized(resizingMethod,
                    source, resized.dimensions(), new double[] {1.0, scale, scale}), 0, false);
            }
        }


        private long safeFloor(double value) {
            double result = Math.floor(value);
            if (Math.abs(value - (result + 1.0)) < 1e-7) {
                // almost integer: we provide better behaviour in a case of little rounding errors
                return (long) (result + 1.0);
            }
            return (long) result;
        }
    }
}
