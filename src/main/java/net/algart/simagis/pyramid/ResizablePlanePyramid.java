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
import net.algart.arrays.Arrays;
import net.algart.external.MatrixToBufferedImageConverter;
import net.algart.math.IPoint;
import net.algart.math.IRectangularArea;
import net.algart.math.Range;
import net.algart.math.functions.LinearFunc;

import java.awt.*;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.List;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

//TODO!!!!
public class ResizablePlanePyramid implements PlanePyramidSource {
    static final int TIME_ENFORCING_GC =
        Arrays.SystemSettings.getIntProperty("com.simagis.pyramid.timeEnforcingGc", 0);
    // - in milliseconds; not used if 0

    private static final boolean MANUAL_OPTIMIZATION_FLUSH_TILE_ROW = false;
    // - not necessary after optimization of copying submatrices via net.algart.arrays.ArraysSubMatrixCopier

    private static final boolean OPTIMIZATION_OF_COMPRESSION_OF_LARGE_BACKGROUND = true; // - should be true


    public interface TileProcessor {
        public void process(Matrix<? extends UpdatablePArray> tileMatrix);
    }

    public static class ReadingImageSettings {
        private Color backgroundColor = new Color(0, 0, 0, 0); // transparent
        private int borderWidth = 0;
        private Color borderColor = Color.WHITE;
        private boolean nullWhenBackgroundOnly = false;

        public Color getBackgroundColor() {
            return backgroundColor;
        }

        public ReadingImageSettings setBackgroundColor(Color backgroundColor) {
            if (backgroundColor == null) {
                throw new NullPointerException("Null background color");
            }
            this.backgroundColor = backgroundColor;
            return this;
        }

        public int getBorderWidth() {
            return borderWidth;
        }

        public ReadingImageSettings setBorderWidth(int borderWidth) {
            if (borderWidth < 0) {
                throw new IllegalArgumentException("Negative border width");
            }
            this.borderWidth = borderWidth;
            return this;
        }

        public Color getBorderColor() {
            return borderColor;
        }

        public ReadingImageSettings setBorderColor(Color borderColor) {
            if (borderColor == null) {
                throw new NullPointerException("Null border color");
            }
            this.borderColor = borderColor;
            return this;
        }

        public boolean isNullWhenBackgroundOnly() {
            return nullWhenBackgroundOnly;
        }

        public ReadingImageSettings setNullWhenBackgroundOnly(boolean nullWhenBackgroundOnly) {
            this.nullWhenBackgroundOnly = nullWhenBackgroundOnly;
            return this;
        }

        @Override
        public String toString() {
            return "ReadingImageSettings: "
                + "backgroundColor=#" + Integer.toHexString(backgroundColor.getRGB()).toUpperCase()
                + ", borderWidth=" + borderWidth
                + ", borderColor=#" + Integer.toHexString(borderColor.getRGB()).toUpperCase()
                + ", nullWhenBackgroundOnly=" + nullWhenBackgroundOnly;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(ResizablePlanePyramid.class.getName());

    private static final LinkedList<IRectangularArea> EMPTY_LIST = new LinkedList<IRectangularArea>();
    private static final byte TILE_ROW_NEVER_ACCESSED = 0;
    private static final byte TILE_ROW_IN_CACHE = 1;
    private static final byte TILE_ROW_IN_PYRAMID = 2;

    private final MemoryModel recommendedMemoryModel;
    private final PlanePyramidSource parent;

    private final int numberOfResolutions;
    private final List<long[]> dimensions;
    private final IRectangularArea allArea; // always [0..dimX-1] x [0..dimY-1]
    private final int compression;
    private final int bandCount;

    private volatile Class<?> elementType = null; // cache for elementType() method

    private volatile AveragingMode averagingMode = AveragingMode.DEFAULT;
    private volatile long maxOverlap = 0;
    private volatile long maxCheckedDistance = 0;
    private volatile long maxVariationInCheckedDistance = 0;
    private volatile long maxBackgroundWidthAtBounds = 0;
    private volatile int maxTileRowCacheSize = 0;
    private volatile boolean correctEdgeEffectsWhileScaling = false;
    private volatile TileProcessor tileProcessor = null;

    private long copyingToZeroLevelTime = 0;
    private long scalingTime = 0;
    private long cacheWriteHitCounter = 0;
    private long cacheWriteMissCounter = 0;
    private long cacheReadHitCounter = 0;
    private long cacheReadMissCounter = 0;
    private long cacheFlushTime = 0;
    private double cacheFlushSize = 0.0;
    private long extraTileScalingCounter = 0;
    private long partialTileScalingCounter = 0;
    private final SpeedInfo pyramidSourceSpeedInfo = new SpeedInfo();
    private final SpeedInfo readImageSpeedInfo = new SpeedInfo();
    private final SpeedInfo readBufferedImageSpeedInfo = new SpeedInfo();
    private final Object counterLock = new Object();

    private ResizablePlanePyramid(final PlanePyramidSource parent, final int compression) {
        if (parent == null) {
            throw new NullPointerException("Null parentSource");
        }
        this.recommendedMemoryModel = Arrays.SMM;
        this.parent = parent;
        for (int level = 0, n = parent.numberOfResolutions(); level < n; level++) {
            if (!this.parent.isResolutionLevelAvailable(level)) {
                throw new IllegalArgumentException("Pyramid source must contain all resolution levels, "
                    + "but there is no level #" + level);
            }
        }
        long[] dimensions0 = this.parent.dimensions(0);
        final long dimX = dimensions0[1];
        final long dimY = dimensions0[2];

        this.compression = compression == 0 ? parent.compression() : compression;
        this.numberOfResolutions = compression == 0 ? parent.numberOfResolutions() :
            PlanePyramidTools.numberOfResolutions(dimX, dimY, this.compression, DEFAULT_MINIMAL_PYRAMID_SIZE);
        this.bandCount = parent.bandCount();
        this.allArea = IRectangularArea.valueOf(
            IPoint.valueOf(0, 0),
            IPoint.valueOf(dimX - 1, dimY - 1));
        long levelDimX = dimX;
        long levelDimY = dimY;
        this.dimensions = new ArrayList<long[]>();
        for (int k = 0; k < numberOfResolutions; k++) {
            this.dimensions.add(new long[] {bandCount, levelDimX, levelDimY});
            levelDimX /= compression;
            levelDimY /= compression;
        }
    }

    public static ResizablePlanePyramid newInstance(final PlanePyramidSource parentSource, final int compression) {
        return new ResizablePlanePyramid(parentSource, compression);
    }

    public static byte[] getTransparentPng1x1Content() {
        return TransparentPng1x1Holder.TRANSPARENT_PNG_1_X_1_CONTENT.clone();
    }

    public static void writeTransparentPng1x1Content(OutputStream stream, boolean closeAtFinish) throws IOException {
        try {
            stream.write(TransparentPng1x1Holder.TRANSPARENT_PNG_1_X_1_CONTENT);
        } finally {
            if (closeAtFinish) {
                stream.close();
            }
        }
    }
    public PlanePyramidSource parent() {
        return parent;
    }

    @Override
    public int numberOfResolutions() {
        return numberOfResolutions;
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
        return new SubMatrixExtracting(resolutionLevel, fromX, fromY, toX, toY, 1).extractSubMatrix(null).fullData;
    }

    @Override
    public boolean isFullMatrixSupported() {
        return parent.isFullMatrixSupported();
    }

    @Override
    public Matrix<? extends PArray> readFullMatrix(int resolutionLevel) {
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

    public long dimX() {
        return allArea.max(0) + 1;
    }

    public long dimY() {
        return allArea.max(1) + 1;
    }

    @Override
    public int compression() {
        return compression;
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

    public void forceAveragingBits() { // recommended for viewers
        if (averagingMode == AveragingMode.DEFAULT) {
            averagingMode = AveragingMode.AVERAGING;
        }
    }

    public long getMaxOverlap() {
        return maxOverlap;
    }

    public void setMaxOverlap(long maxOverlap) {
        if (maxOverlap < 0) {
            throw new IllegalArgumentException("Negative maxOverlap");
        }
        this.maxOverlap = maxOverlap;
    }

    public long getMaxCheckedDistance() {
        return maxCheckedDistance;
    }

    public void setMaxCheckedDistance(long maxCheckedDistance) {
        if (maxCheckedDistance < 0) {
            throw new IllegalArgumentException("Negative maxCheckedDistance");
        }
        this.maxCheckedDistance = maxCheckedDistance;
    }

    public long getMaxVariationInCheckedDistance() {
        return maxVariationInCheckedDistance;
    }

    public void setMaxVariationInCheckedDistance(long maxVariationInCheckedDistance) {
        if (maxVariationInCheckedDistance < 0) {
            throw new IllegalArgumentException("Negative maxVariationInCheckedDistance");
        }
        this.maxVariationInCheckedDistance = maxVariationInCheckedDistance;
    }

    public long getMaxBackgroundWidthAtBounds() {
        return maxBackgroundWidthAtBounds;
    }

    public void setMaxBackgroundWidthAtBounds(long maxBackgroundWidthAtBounds) {
        if (maxBackgroundWidthAtBounds < 0) {
            throw new IllegalArgumentException("Negative maxBackgroundWidthAtBounds");
        }
        this.maxBackgroundWidthAtBounds = maxBackgroundWidthAtBounds;
    }

    public boolean isCorrectEdgeEffectsWhileScaling() {
        return correctEdgeEffectsWhileScaling;
    }

    public void setCorrectEdgeEffectsWhileScaling(boolean correctEdgeEffectsWhileScaling) {
        this.correctEdgeEffectsWhileScaling = correctEdgeEffectsWhileScaling;
    }

    public TileProcessor getTileProcessor() {
        return tileProcessor;
    }

    public void setTileProcessor(TileProcessor tileProcessor) {
        this.tileProcessor = tileProcessor;
    }

    public void resetCacheCounters() {
        synchronized (counterLock) {
            copyingToZeroLevelTime = 0;
            scalingTime = 0;
            cacheWriteHitCounter = 0;
            cacheWriteMissCounter = 0;
            cacheReadHitCounter = 0;
            cacheReadMissCounter = 0;
            cacheFlushTime = 0;
            cacheFlushSize = 0;
            extraTileScalingCounter = 0;
            partialTileScalingCounter = 0;
        }
    }

    public long copyingToZeroLevelTime() {
        synchronized (counterLock) {
            return copyingToZeroLevelTime;
        }
    }

    public long scalingTime() {
        synchronized (counterLock) {
            return scalingTime;
        }
    }

    public long cacheWriteHitCounter() {
        synchronized (counterLock) {
            return cacheWriteHitCounter;
        }
    }

    public long cacheWriteMissCounter() {
        synchronized (counterLock) {
            return cacheWriteMissCounter;
        }
    }

    public long cacheReadHitCounter() {
        synchronized (counterLock) {
            return cacheReadHitCounter;
        }
    }

    public long cacheReadMissCounter() {
        synchronized (counterLock) {
            return cacheReadMissCounter;
        }
    }

    public long cacheFlushTime() {
        synchronized (counterLock) {
            return cacheFlushTime;
        }
    }

    public double cacheFlushSize() {
        synchronized (counterLock) {
            return cacheFlushSize;
        }
    }

    public long extraTileScalingCounter() {
        synchronized (counterLock) {
            return extraTileScalingCounter;
        }
    }

    public long partialTileScalingCounter() {
        synchronized (counterLock) {
            return partialTileScalingCounter;
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
        ArrayContext context,
        // may be null, but useful to specify for large fragments
        final long fromX, final long fromY, final long toX, final long toY,
        // position at level 0
        final double compression)
    {
        /*
        checkFromAndTo(fromX, fromY, toX, toY);
        long t1 = System.nanoTime();
        int readyLevels = numberOfResolutions;
        int level = Math.min(maxLevel(compression), readyLevels - 1); // also checks that compression >= 1
        final ImageScaling scaling = new ImageScaling(fromX, fromY, toX, toY, level, compression);
        scaling.findActualAndBackgroundAreas();

        long t2 = System.nanoTime();
        Matrix<? extends PArray> m = scaling.scaleImage(context);
        if (m.size() == 0) {
            return m;
        }

        long t3 = System.nanoTime();
        if (!(scaling.backgroundAreas.isEmpty())) {
            Matrix<? extends UpdatablePArray> updatable = m.matrix(scaling.newlyAllocated ?
                (UpdatablePArray) m.array() :
                m.array().updatableClone(findMemoryModel(context, Matrices.sizeOf(m))));
            IPoint areasShift = IPoint.valueOf(-fromX, -fromY);
            Object backgroundPatternCache = null;
            for (IRectangularArea area : scaling.backgroundAreas) {
                //            System.out.println("Filling " + area + " >> " + areasShift);
                backgroundPatternCache = fillBackgroundInPackedMatrix3DWithCompression(
                    updatable, area, areasShift, compression, level == 0, backgroundPatternCache);
            }
            m = updatable;
        }

        long t4 = System.nanoTime();
        final String averageSpeed = readImageSpeedInfo.update(Matrices.sizeOf(m), t4 - t1, true);
        if (DEBUG_LEVEL >= 2) {
            Runtime runtime = Runtime.getRuntime();
            System.out.print(scaling.scaleImageTiming());
            System.out.printf(Locale.US,
                "%s has read image (%d-bit, %d CPU for AlgART, used memory %.3f/%.3f MB, compression %.2f): "
                    + "%d..%d x %d..%d (%d x %d%s) in %.3f ms (%.3f init + %.3f scaled reading + %.3f finishing), "
                    + "%.3f MB/sec, average %s (source: %s)%n",
                ResizablePlanePyramid.class.getSimpleName(),
                Arrays.SystemSettings.isJava32() ? 32 : 64,
                context == null ?
                    Arrays.SystemSettings.cpuCount() :
                    context.getThreadPoolFactory().recommendedNumberOfTasks(),
                (runtime.totalMemory() - runtime.freeMemory()) / 1048576.0, runtime.maxMemory() / 1048576.0,
                compression, fromX, toX, fromY, toY, toX - fromX, toY - fromY,
                Arrays.isNCopies(m.array()) ? ", CONSTANT" : "",
                (t4 - t1) * 1e-6, (t2 - t1) * 1e-6, (t3 - t2) * 1e-6, (t4 - t3) * 1e-6,
                Matrices.sizeOf(m) / 1048576.0 / ((t4 - t1) * 1e-9), averageSpeed,
                parent == null ? "none" : parent.getClass().getSimpleName()
            );
        }
        return m;
        */
        throw new UnsupportedOperationException();
    }

    public BufferedImage readBufferedImage(
        ArrayContext context,
        // may be null, but useful to specify for large fragments
        final long fromX, final long fromY, final long toX, final long toY,
        // position at level 0
        final double compression,
        final MatrixToBufferedImageConverter converter,
        final ReadingImageSettings settings)
    {
        throw new UnsupportedOperationException();
        /*
        if (converter == null) {
            throw new NullPointerException("Null converter");
        }
        if (settings == null) {
            throw new NullPointerException("Null reading image settings");
        }
        checkFromAndTo(fromX, fromY, toX, toY);
        long t1 = System.nanoTime();
        final int readyLevels = ready ? numberOfResolutions : nImmediatelyAvailable;
        final int level = Math.min(maxLevel(compression), readyLevels - 1); // also checks that compression >= 1
        if (settings.nullWhenBackgroundOnly) {
            // optimization of most frequent case, when all the returned image will be background
            if (surelyOutsideBorderedActualArea(
                dimX(), dimY(), fromX, fromY, toX, toY, compression,
                settings.borderWidth))
            {
                return null;
            }
        }

        final ImageScaling scaling = new ImageScaling(fromX, fromY, toX, toY, level, compression);
        scaling.findActualAndBackgroundAreas();

        boolean noActualDataOrBorder = scaling.actualAreas.isEmpty();
        final IPoint shift = IPoint.valueOf(-fromX, -fromY);
        final Collection<IRectangularArea> borderAreas = settings.borderWidth > 0 ?
            getBorderInMatrix2DWithCompression(scaling.newDimX, scaling.newDimY,
                allArea, shift, compression, settings.borderWidth, level == 0) :
            new ArrayList<IRectangularArea>();
        if (scaling.newDimX == 0 || scaling.newDimY == 0) {
            noActualDataOrBorder = true;
            // to be on the safe side: in a case if nullWhenBackgroundOnly, it is the most reasonable answer
        } else if (!borderAreas.isEmpty()) {
            noActualDataOrBorder = false;
        }
        if (settings.nullWhenBackgroundOnly && noActualDataOrBorder) {
            return null;
        }

        long t2 = System.nanoTime();
        Matrix<? extends PArray> m = scaling.scaleImage(context);

        long t3 = System.nanoTime();
        if (converter.byteArrayRequired() && m.elementType() != byte.class) {
            double max = m.array().maxPossibleValue(1.0);
            m = Matrices.asFuncMatrix(LinearFunc.getInstance(0.0, 255.0 / max), ByteArray.class, m);
        }
        final int width = converter.getWidth(m); // must be after conversion to byte, to avoid IllegalArgumentException
        final int height = converter.getHeight(m);
        if (width != scaling.newDimX || height != scaling.newDimY) {
            throw new AssertionError("Invalid implementation of MatrixToBufferedImageConverter: it returns "
                + width + "x" + height + " dimensions for matrix " + m);
        }
        java.awt.image.DataBuffer dataBuffer = converter.toDataBuffer(m);
        for (int bankIndex = 0; bankIndex < dataBuffer.getNumBanks(); bankIndex++) {
            Matrix<? extends UpdatablePArray> bankMatrix = Matrices.matrix(
                (UpdatablePArray) SimpleMemoryModel.asUpdatableArray(
                    MatrixToBufferedImageConverter.getDataArray(dataBuffer, bankIndex)),
                width, height
            );
            long filler = converter.colorValue(m, settings.backgroundColor, bankIndex);
            for (IRectangularArea a : scaling.backgroundAreas) {
                fillBackgroundInMatrix2DWithCompression(bankMatrix, a, shift, compression, level == 0, filler);
            }
            fillAreasInMatrix2D(bankMatrix, borderAreas, converter.colorValue(m, settings.borderColor, bankIndex));
        }
        long t4 = System.nanoTime();
        BufferedImage bufferedImage = converter.toBufferedImage(m, dataBuffer);
        long t5 = System.nanoTime();
        final String averageSpeed = readBufferedImageSpeedInfo.update(Matrices.sizeOf(m), t5 - t1, true);
        if (DEBUG_LEVEL >= 2) {
            Runtime runtime = Runtime.getRuntime();
            System.out.print(scaling.scaleImageTiming());
            System.out.printf(Locale.US,
                "%s has read buffered image (%d-bit, %d CPU for AlgART, used memory %.3f/%.3f MB, compression %.2f): "
                    + "%d..%d x %d..%d (%d x %d) in %.3f ms "
                    + "(%.3f init + %.3f scaled reading + %.3f finishing + %.3f conversion), "
                    + "%.3f MB/sec, average %s (source: %s)%n",
                ResizablePlanePyramid.class.getSimpleName(),
                Arrays.SystemSettings.isJava32() ? 32 : 64,
                context == null ?
                    Arrays.SystemSettings.cpuCount() :
                    context.getThreadPoolFactory().recommendedNumberOfTasks(),
                (runtime.totalMemory() - runtime.freeMemory()) / 1048576.0, runtime.maxMemory() / 1048576.0,
                compression, fromX, toX, fromY, toY, toX - fromX, toY - fromY,
                (t5 - t1) * 1e-6, (t2 - t1) * 1e-6, (t3 - t2) * 1e-6, (t4 - t3) * 1e-6, (t5 - t4) * 1e-6,
                Matrices.sizeOf(m) / 1048576.0 / ((t4 - t1) * 1e-9), averageSpeed,
                parent == null ? "none" : parent.getClass().getSimpleName()
            );
        }
        return bufferedImage;
        */
    }

    public String toString() {
        return "ResizablePlanePyramid " + bandCount + "x" + dimX() + "x" + dimY()
            + ", " + numberOfResolutions + " levels, compression " + compression
            + (parent == null ? "" : ", based on " + parent);
    }

    private Matrix<? extends PArray> callAndCheckParentReadSubMatrix(
        ArrayContext context,
        int resolutionLevel,
        long fromX, long fromY, long toX, long toY)
    {
        PlanePyramidSource parentWithContext = parent;
        if (context != null && parentWithContext instanceof ArrayProcessorWithContextSwitching) {
            parentWithContext = (PlanePyramidSource)
                ((ArrayProcessorWithContextSwitching) parentWithContext).context(context);
        }
        long t1 = System.nanoTime();
        Matrix<? extends PArray> m = parentWithContext.readSubMatrix(resolutionLevel, fromX, fromY, toX, toY);
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
            System.out.printf(Locale.US,
                "%s.callAndCheckParentReadSubMatrix timing (level %d, %d..%d x %d..%d (%d x %d%s): "
                    + "%.3f ms, %.3f MB/sec, average %s (source: %s)%n",
                ResizablePlanePyramid.class.getSimpleName(),
                resolutionLevel, fromX, toX, fromY, toY, toX - fromX, toY - fromY,
                Arrays.isNCopies(m.array()) ? ", CONSTANT" : "",
                (t2 - t1) * 1e-6, Matrices.sizeOf(m) / 1048576.0 / ((t2 - t1) * 1e-9), averageSpeed,
                parent.getClass().getSimpleName()
            );
        }
        return m;
    }

    private static void checkFromAndTo(long fromX, long fromY, long toX, long toY) {
        if (fromX > toX || fromY > toY) {
            throw new IndexOutOfBoundsException("Illegal fromX..toX=" + fromX + ".." + toX
                + " or fromY..toY=" + fromY + ".." + toY + ": must be fromX<=toX, fromY<=toY");
        }
    }

    private static IRectangularArea joinToTheRight(IRectangularArea previous, IRectangularArea next) {
        assert previous.coordCount() == 2 && next.coordCount() == 2;
        return previous.range(1).equals(next.range(1)) && previous.max(0) == next.min(0) - 1 ?
            IRectangularArea.valueOf(previous.min(), next.max()) :
            null;
    }

    private static void addToTheRight(java.util.List<IRectangularArea> allPrevious, IRectangularArea next) {
        if (!allPrevious.isEmpty()) {
            IRectangularArea r = joinToTheRight(allPrevious.get(allPrevious.size() - 1), next);
            if (r != null) {
                allPrevious.set(allPrevious.size() - 1, r); // joining to the last rectangle
                return;
            }
        }
        allPrevious.add(next);
    }

    public Collection<IRectangularArea> getNonFilledAreasInRectangle(
        long fromX, long fromY, long toX, long toY, int level)
    {
        throw new UnsupportedOperationException();
        /*
        synchronized (lock) {
            List<IRectangularArea> result = new ArrayList<IRectangularArea>();
            if (toX <= fromX || toY <= fromY) {
                return result;
            }
            IRectangularArea area = IRectangularArea.valueOf(
                IPoint.valueOf(fromX, fromY),
                IPoint.valueOf(toX - 1, toY - 1));
            area.difference(result, allArea);
            area = area.intersection(allArea);
            if (area == null) {
                return result;
            }
            final long minXIndex = area.min(0) / tileDimX;
            final long minYIndex = area.min(1) / tileDimY;
            final long maxXIndex = area.max(0) / tileDimX;
            final long maxYIndex = area.max(1) / tileDimY;
            for (long yIndex = minYIndex; yIndex <= maxYIndex; yIndex++) {
                for (long xIndex = minXIndex; xIndex <= maxXIndex; xIndex++) {
                    if (!coveringMap.inside(xIndex, yIndex)) {
                        continue;
                    }
                    if (level > 0) {
                        boolean ready = level < nImmediatelyAvailable
                            && isTileReadyForImmediatelyAvailableResolutions(xIndex, yIndex);
                        if (!ready) {
                            addToTheRight(result, tile(xIndex, yIndex));
                            continue;
                        }
                    }
                    Collection<IRectangularArea> tileAreas = getTileNonFilledAreas(xIndex, yIndex, false);
                    if (tileAreas.isEmpty()) {
                        continue;
                    }
                    IRectangularArea tileRectIntersection = tile(xIndex, yIndex).intersection(area);
                    if (tileRectIntersection == null) { // to be on the safe side: should not occur
                        continue;
                    }
                    for (IRectangularArea tileArea : tileAreas) {
                        tileArea = tileArea.intersection(tileRectIntersection);
                        if (tileArea != null) {
                            addToTheRight(result, tileArea);
                        }
                    }
                }
            }
            return result;
        }
        */
    }

    public Collection<IRectangularArea> getFilledAreasInRectangle(
        long fromX, long fromY, long toX, long toY, int level)
    {
        return getFilledAreasInRectangle(fromX, fromY, toX, toY,
            getNonFilledAreasInRectangle(fromX, fromY, toX, toY, level));
    }

    public Collection<IRectangularArea> getFilledAreasInRectangle(
        long fromX, long fromY, long toX, long toY,
        Collection<IRectangularArea> nonFilledAreasInRectangle) // this argument can be corrected (simplified)
    {
        if (nonFilledAreasInRectangle == null) {
            throw new NullPointerException("Null nonFilledAreasInRectangle");
        }
        Queue<IRectangularArea> result = new LinkedList<IRectangularArea>();
        if (toX <= fromX || toY <= fromY) {
            return result;
        }
        final IRectangularArea area = IRectangularArea.valueOf(
            IPoint.valueOf(fromX, fromY),
            IPoint.valueOf(toX - 1, toY - 1));
        result.add(area);
        IRectangularArea.subtractCollection(result, nonFilledAreasInRectangle);
        if (result.isEmpty()) {
            // simplification: if the area is fully covered by non-filled areas, then
            // the list of non-filled areas can be replaced with this area itself
            nonFilledAreasInRectangle.clear();
            nonFilledAreasInRectangle.add(area);
        }
        return result;
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
        boolean additionalCompressionIsInteger;

        Collection<IRectangularArea> backgroundAreas = null;
        Collection<IRectangularArea> actualAreas = null;

        // The following field is filled by scaleImage:
        boolean newlyAllocated = false;

        private long scaleImageExtractingTime = 0;
        private long scaleImageDetectingElementTypeTime = 0;
        private long scaleImageExtractingPostprocessingTime = 0;
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
            final double levelCompressionInv = 1.0 / levelCompression;
            this.levelFromX = safeFloor(zeroLevelFromX * levelCompressionInv);
            this.levelFromY = safeFloor(zeroLevelFromY * levelCompressionInv);
            this.levelToX = safeFloor(zeroLevelToX * levelCompressionInv);
            this.levelToY = safeFloor(zeroLevelToY * levelCompressionInv);
            this.additionalCompression = compression * levelCompressionInv;
            this.levelCompression = Math.round(levelCompression);
            // - the same number, excepting a case of 63-bit overflow (levelCompression > 2^63)
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
//        System.out.printf("Resizing %d..%d x %d..%d (level %d) into %dx%d, additional compression %.3f"
//            + " (%.3f/X, %.3f/Y, " + (needAdditionalCompression ? "" : "not ") + "necessary)%n",
//            levelFromX, levelToX - 1, levelFromY, levelToY - 1, level, newDimX, newDimY, additionalCompression,
//            (double) (levelToX - levelFromX) / newDimX, (double) (levelToY - levelFromY) / newDimY);
            this.additionalCompressionIsInteger = false;
            if (needAdditionalCompression) {
                assert additionalCompression > 1.0;
                final long intAdditionalCompression = Math.round(additionalCompression);
                additionalCompressionIsInteger = Math.abs(additionalCompression - intAdditionalCompression) < 1e-7;
                if (additionalCompressionIsInteger) {
                    levelToX = Math.min(levelToX, levelFromX + intAdditionalCompression * newDimX);
                    levelToY = Math.min(levelToY, levelFromY + intAdditionalCompression * newDimY);
                    // in other words, we prefer to lose 1-2 last pixels, but provide strict
                    // integer compression: AlgART libraries are optimized for this situation
//                System.out.printf("Correcting area: ..%d x ..%d%n", this.levelToX, this.levelToY);
                }
            }
        }

        void findActualAndBackgroundAreas() {
            this.backgroundAreas = getNonFilledAreasInRectangle(
                zeroLevelFromX, zeroLevelFromY, zeroLevelToX, zeroLevelToY,
                level);
            this.actualAreas = getFilledAreasInRectangle(
                zeroLevelFromX, zeroLevelFromY, zeroLevelToX, zeroLevelToY,
                backgroundAreas);
        }

        Matrix<? extends PArray> scaleImage(ArrayContext context) {
            if (actualAreas == null || backgroundAreas == null) {
                throw new AssertionError("findActualAndBackgroundAreas() must be called before");
            }
            long t1 = System.nanoTime();
            final boolean skipActualReading = actualAreas.isEmpty();
            final SubMatrixExtracting extracting;
            if (skipActualReading) {
                extracting = null;
            } else {
                extracting = new SubMatrixExtracting(
                    level, levelFromX, levelFromY, levelToX, levelToY, roundedTotalCompression);
                extracting.extractSubMatrix(
                    context == null || !needAdditionalCompression ? context : context.part(0.0, 0.5));
            }
            long t2 = System.nanoTime();
            scaleImageExtractingTime = t2 - t1;
            final Class<?> elementType = skipActualReading ?
                ResizablePlanePyramid.this.elementType() : // detecting element type
                extracting.fullData.elementType();
            boolean convertBitToByte = needAdditionalCompression
                && averagingMode == AveragingMode.AVERAGING
                && elementType == boolean.class;
            long t3 = System.nanoTime();
            scaleImageDetectingElementTypeTime = t3 - t2;
            if (skipActualReading
                || newDimX == 0 || newDimY == 0 // avoiding a case of extremely high compression
                || extracting.fullData.size() == 0) // checking size() - to be on the safe side
            {
                return Matrices.constantMatrix(0.0,
                    Arrays.type(PArray.class, convertBitToByte ? byte.class : elementType),
                    bandCount, newDimX, newDimY);
            }
            final MemoryModel mm = Arrays.SMM;
            if (!needAdditionalCompression) {
                newlyAllocated = false;
                return extracting.fullData;
            }
            // so, needAdditionalCompression is true
            extracting.cloneDataWhenEnoughMemory();
            double memory = extracting.memoryForClone
                + (double) bandCount * (double) newDimX * (double) newDimY
                * (convertBitToByte ? 1.0 : Arrays.sizeOf(elementType));
            long t4 = System.nanoTime();
            scaleImageExtractingPostprocessingTime = t4 - t3;
            newlyAllocated = true;
            if (needAdditionalCompression) {
                if (convertBitToByte) {
                    extracting.convertBitToByte();
                }
                Matrix<? extends UpdatablePArray> resized = mm.newMatrix(UpdatablePArray.class,
                    extracting.fullData.elementType(), bandCount, newDimX, newDimY);
                context = context == null ? null : context.part(0.5, 1.0);
                final boolean compressOnlyPart = OPTIMIZATION_OF_COMPRESSION_OF_LARGE_BACKGROUND
                    && !extracting.allDataActual
                    && Math.abs(totalCompression - roundedTotalCompression) < 1e-6;
                if (compressOnlyPart) {
                    Matrix<? extends UpdatablePArray> partInResized = compressedPartInResized(resized, extracting);
                    doResize(context, partInResized, extracting.dataForCompression);
//                    System.out.println("Optimized resizing");
                } else {
                    doResize(context, resized, extracting.fullData);
                }
                long t5 = System.nanoTime();
                scaleImageCompressionTime = t5 - t4;
//                System.out.printf("Additional compression in %.2f times: %.3f ms, compressOnlyPart: %s%n",
//                    additionalCompression, (t5 - t4) * 1e-6, compressOnlyPart);
                return resized;
            } else {
                assert extracting.fullData.dim(1) == newDimX;
                assert extracting.fullData.dim(2) == newDimY;
                return extracting.fullData;
            }
        }

        String scaleImageTiming() {
            return String.format(Locale.US,
                "%s.scaleImage timing: "
                    + "%.3f ms = %.3f ms extracting data + %.3f ms detecting element type + "
                    + "%.3f ms extracting postprocessing + %.3f compression",
                getClass().getSimpleName(),
                (scaleImageExtractingTime + scaleImageDetectingElementTypeTime
                    + scaleImageExtractingPostprocessingTime + scaleImageCompressionTime) * 1e-6,
                scaleImageExtractingTime * 1e-6,
                scaleImageDetectingElementTypeTime * 1e-6,
                scaleImageExtractingPostprocessingTime * 1e-6,
                scaleImageCompressionTime * 1e-6
            ) + (
                needAdditionalCompression ?
                    String.format(Locale.US, " (additional compressing %dx%d to %dx%d in %.3f times)%n",
                        levelToX - levelFromX, levelToY - levelFromY, newDimX, newDimY, additionalCompression) :
                    String.format(" (additional compression skipped)%n"));
        }

        private Matrix<? extends UpdatablePArray> compressedPartInResized(
            Matrix<? extends UpdatablePArray> resized,
            SubMatrixExtracting extracting)
        {
            final long fromXInFull = extracting.roundedFromX - extracting.levelFromX;
            final long fromYInFull = extracting.roundedFromY - extracting.levelFromY;
            final long toXInFull = extracting.roundedToX - extracting.levelFromX;
            final long toYInFull = extracting.roundedToY - extracting.levelFromY;
            assert fromXInFull % roundedTotalCompression == 0;
            assert fromYInFull % roundedTotalCompression == 0;
            assert toXInFull % roundedTotalCompression == 0;
            assert toYInFull % roundedTotalCompression == 0;
            // In this branch, there should be additionalCompression ~= roundedTotalCompression / levelCompression
            final long fromXInResized = fromXInFull / roundedTotalCompression * levelCompression;
            final long fromYInResized = fromYInFull / roundedTotalCompression * levelCompression;
            final long toXInResized = toXInFull / roundedTotalCompression * levelCompression;
            final long toYInResized = toYInFull / roundedTotalCompression * levelCompression;
            return resized.subMatrix(0, fromXInResized, fromYInResized, bandCount, toXInResized, toYInResized,
                Matrix.ContinuationMode.NAN_CONSTANT);
        }

        private void doResize(
            ArrayContext context,
            Matrix<? extends UpdatablePArray> resized,
            Matrix<? extends PArray> source)
        {
//            System.out.println("Resizing to " + resized + " from " + source);
            final Matrices.ResizingMethod resizingMethod = averagingMode.averagingMethod(source);
            if (additionalCompressionIsInteger) {
                Matrices.resize(context, resizingMethod, resized, source);
            } else {
                final double scale = 1.0 / additionalCompression;
                Matrices.copy(context, resized, Matrices.asResized(resizingMethod,
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

    private class SubMatrixExtracting {
        // The following fields are filled by the constructor:
        final int level;
        final long levelDimX;
        final long levelDimY;
        final long levelFromX;
        final long levelFromY;
        final long compression;
        final long levelToX;
        final long levelToY;
        final long actualFromX;
        final long actualFromY;
        final long actualToX;
        final long actualToY;
        final long roundedFromX;
        final long roundedFromY;
        final long roundedToX;
        final long roundedToY;
        boolean allDataActual;

        // The following fields are filled by extractSubMatrix:
        Matrix<? extends PArray> fullData = null;
        Matrix<? extends PArray> actualData = null;
        Matrix<? extends PArray> dataForCompression = null;

        // The following field is filled by cloneDataWhenEnoughMemory:
        long memoryForClone = 0;

        SubMatrixExtracting(
            final int level,
            final long levelFromX,
            final long levelFromY,
            final long levelToX,
            final long levelToY,
            final long compression)
        {
            assert compression >= 1;
            if (levelFromX > levelToX) {
                throw new IndexOutOfBoundsException("fromX = " + levelFromX + " > toX = " + levelToX);
            }
            if (levelFromY > levelToY) {
                throw new IndexOutOfBoundsException("fromY = " + levelFromY + " > toY = " + levelToY);
            }
            if (levelFromX < -Long.MAX_VALUE / 4 || levelToX > Long.MAX_VALUE / 4 - compression) {
                throw new IllegalArgumentException("Too large absolute values of fromX="
                    + levelFromX + " or toX=" + levelToX);
            }
            if (levelFromY < -Long.MAX_VALUE / 4 || levelToY > Long.MAX_VALUE / 4 - compression) {
                throw new IllegalArgumentException("Too large absolute values of fromX="
                    + levelFromY + " or toX=" + levelToY);
            }
            // - little restriction for abnormally large values allows to guarantee, that there will be no overflows
            this.level = level;
            this.levelFromX = levelFromX;
            this.levelFromY = levelFromY;
            this.levelToX = levelToX;
            this.levelToY = levelToY;
            this.compression = compression;
            long[] dimensions = ResizablePlanePyramid.this.dimensions(level);
            this.levelDimX = dimensions[1];
            this.levelDimY = dimensions[2];
            this.actualFromX = levelFromX < 0 ? 0 : levelFromX > levelDimX ? levelDimX : levelFromX;
            this.actualFromY = levelFromY < 0 ? 0 : levelFromY > levelDimY ? levelDimY : levelFromY;
            this.actualToX = levelToX < 0 ? 0 : levelToX > levelDimX ? levelDimX : levelToX;
            this.actualToY = levelToY < 0 ? 0 : levelToY > levelDimY ? levelDimY : levelToY;
            this.allDataActual = actualFromX == levelFromX && actualFromY == levelFromY
                && actualToX == levelToX && actualToY == levelToY;
            this.roundedFromX = roundDown(levelFromX, actualFromX);
            this.roundedFromY = roundDown(levelFromY, actualFromY);
            this.roundedToX = roundUp(levelFromX, actualToX);
            this.roundedToY = roundUp(levelFromY, actualToY);
            assert (roundedFromX - levelFromX) % compression == 0;
            assert (roundedToX - levelFromX) % compression == 0;
            assert (roundedFromY - levelFromY) % compression == 0;
            assert (roundedToY - levelFromY) % compression == 0;
        }

        SubMatrixExtracting extractSubMatrix(ArrayContext context) {
            long t1 = System.nanoTime();
            this.actualData = callAndCheckParentReadSubMatrix(
                context, level, actualFromX, actualFromY, actualToX, actualToY);
            extendActual();
            // sometimes fullData and dataForCompression are not really necessary, but their creation is very quick
            long t2 = System.nanoTime();
//            System.out.printf(Locale.US,
//                "%s.extractSubMatrix timing: %.3f ms%n",
//                getClass().getSimpleName(), (t2 - t1) * 1e-6);
            return this;
        }

        void setFullDataAndRemoveOthers(Matrix<? extends PArray> fullData) {
            assert fullData != null;
            this.fullData = this.actualData = this.dataForCompression = fullData;
            this.allDataActual = true;
        }

        void cloneDataWhenEnoughMemory() {
            assert actualData != null;
            final PArray array = actualData.array();
            if (!SimpleMemoryModel.isSimpleArray(array)
                && Arrays.sizeOf(array) <= Arrays.SystemSettings.maxTempJavaMemory())
            {
                actualData = actualData.matrix(array.updatableClone(Arrays.SMM));
                memoryForClone = Matrices.sizeOf(actualData);
                extendActual();
            } else {
                memoryForClone = 0;
            }
        }

        void convertBitToByte() {
            assert actualData != null;
            if (actualData.elementType() != boolean.class) {
                throw new AssertionError("Must be called for bit matrices only, but called for " + actualData);
            }
            Range srcRange = Range.valueOf(0.0, actualData.array().maxPossibleValue(1.0));
            Range destRange = Range.valueOf(0.0, Arrays.maxPossibleIntegerValue(ByteArray.class));
            actualData = Matrices.asFuncMatrix(LinearFunc.getInstance(destRange, srcRange),
                ByteArray.class, actualData);
            extendActual();
        }

        private void extendActual() {
            assert actualData != null;
            if (allDataActual) {
                fullData = actualData;
                dataForCompression = actualData;
            } else {
                // providing better behaviour than the parent source: allowing an area outside the matrix
                fullData = actualData.subMatr(
                    0, levelFromX - actualFromX, levelFromY - actualFromY,
                    bandCount, levelToX - levelFromX, levelToY - levelFromY,
                    Matrix.ContinuationMode.NAN_CONSTANT);
                // providing reduced fullData for efficient compression
                dataForCompression = actualData.subMatr(
                    0, roundedFromX - actualFromX, roundedFromY - actualFromY,
                    bandCount, roundedToX - roundedFromX, roundedToY - roundedFromY,
                    Matrix.ContinuationMode.NAN_CONSTANT);
            }
        }

        private long roundDown(long base, long value) {
            value -= base;
            value = value - value % compression;
            value += base;
            return value;
        }

        private long roundUp(long base, long value) {
            value -= base;
            long rest = value % compression;
            if (rest != 0) {
                value = value - rest + compression;
            }
            value += base;
            return value;
        }
    }

    private static class TransparentPng1x1Holder {
        private static final byte[] TRANSPARENT_PNG_1_X_1_CONTENT = {
            -119, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82, 0, 0, 0, 1,
            0, 0, 0, 1, 8, 6, 0, 0, 0, 31, 21, -60, -119, 0, 0, 0, 1, 115, 82, 71,
            66, 0, -82, -50, 28, -23, 0, 0, 0, 13, 73, 68, 65, 84, 8, -41, 99, 96, 96, 96,
            96, 0, 0, 0, 5, 0, 1, 94, -13, 42, 58, 0, 0, 0, 0, 73, 69, 78, 68, -82,
            66, 96, -126,
        };

        public static void main(String[] args) throws IOException {
            if (args.length == 0) {
                System.out.println("Usage: " + TransparentPng1x1Holder.class.getName() + " transparent1x1.png");
                System.out.println("This file should contain a little fully transparent 1x1 image");
                return;
            }
            File transparentPng1x1File = new File(args[0]);
            final FileInputStream stream = new FileInputStream(transparentPng1x1File);
            System.out.print("private static final byte[] TRANSPARENT_PNG_1_X_1_CONTENT = {");

            long count = 0;
            int b;
            while ((b = stream.read()) != -1) {
                if (count++ % 20 == 0) {
                    System.out.printf("%n   ");
                }
                System.out.printf(" %d,", (byte) b);
            }
            System.out.printf("%n};%n");
            stream.close();
        }
    }
}
