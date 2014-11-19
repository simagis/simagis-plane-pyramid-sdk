/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Daniel Alievsky, AlgART Laboratory (http://algart.net)
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

package com.simagis.pyramid;

import net.algart.arrays.Arrays;
import net.algart.arrays.Matrix;
import net.algart.arrays.PArray;

import java.nio.channels.NotYetConnectedException;
import java.util.NoSuchElementException;

public interface PlanePyramidSource {
    public static final int DIM_BAND = 0;
    public static final int DIM_WIDTH = 1;
    public static final int DIM_HEIGHT = 2;

    public static final int DEFAULT_COMPRESSION = Math.max(2,
        Arrays.SystemSettings.getIntProperty("com.simagis.pyramid.defaultPyramidCompression", 4));
    public static final long DEFAULT_TILE_DIM = Math.max(16,
        Arrays.SystemSettings.getLongProperty("com.simagis.pyramid.tile", 1024));
    public static final int DEBUG_LEVEL = Math.max(
        Arrays.SystemSettings.getIntProperty("com.simagis.pyramid.debugLevel", 1),
        Arrays.SystemSettings.getIntEnv("COM_SIMAGIS_PYRAMID_DEBUGLEVEL", 1));

    public static enum SpecialImageKind {
        /**
         * Coarse image of the full slide. Usually contains the {@link #LABEL_ONLY_IMAGE label} as a part.
         *
         * <p>Some implementations of {@link PlanePyramidSource} can use it automatically
         * as the background for the pyramid data.
         *
         * <p>If the format does not provide such an image, it is built automatically as the last available level
         * of the full image, returned by {@link PlanePyramidSource#readFullMatrix(int resolutionLevel)}.
         */
        WHOLE_SLIDE,

        /**
         * Coarse image with all existing pyramid data. Never contains the {@link #LABEL_ONLY_IMAGE label}.
         * May be used, for example, for visual navigation goals.
         *
         * <p>If the format does not provide such an image, it is built automatically as the last available level
         * of the full image, returned by {@link PlanePyramidSource#readFullMatrix(int resolutionLevel)}.
         */
        MAP_IMAGE,

        /**
         * The label image: a little photo of some paper on the scan
         * with printed or written information about the slide.
         *
         * <p>If the format does not provide such an image, it is built automatically as the last available level
         * of the full image, returned by {@link PlanePyramidSource#readFullMatrix(int resolutionLevel)}.
         * Of course, in this case it is not the best solution, but you can use
         * {@link PlanePyramidSource#isSpecialMatrixSupported(PlanePyramidSource.SpecialImageKind)}
         * method to check this.
         */
        LABEL_ONLY_IMAGE,

        /**
         * The thumbnail: something like {@link #MAP_IMAGE}, but containing the same information as a ordinary
         * pyramid (while {@link #MAP_IMAGE} can contain other coarse data). Usually generated automatically
         * by scanner software and looks like last available levels.
         *
         * <p>If the format does not provide such an image, it is built automatically as the last available level
         * of the full image, returned by {@link PlanePyramidSource#readFullMatrix(int resolutionLevel)}.
         */
        THUMBNAIL_IMAGE,

        /**
         * Some format-specific special image.
         *
         * <p>If the format does not provide such an image, it is built automatically as the last available level
         * of the full image, returned by {@link PlanePyramidSource#readFullMatrix(int resolutionLevel)}.
         */
        CUSTOM_KIND_1,

        /**
         * Some format-specific special image.
         *
         * <p>If the format does not provide such an image, it is built automatically as the last available level
         * of the full image, returned by {@link PlanePyramidSource#readFullMatrix(int resolutionLevel)}.
         */
        CUSTOM_KIND_2,
        /**
         * Impossible image kind, that should never returned for correct special images, but can be returned
         * by some methods to indicate special situation (for example, when it is not a special image).
         *
         * <p>When passed to {@link #readSpecialMatrix(PlanePyramidSource.SpecialImageKind)} method,
         * the result is always built automatically as the last available level
         * of the full image, returned by {@link PlanePyramidSource#readFullMatrix(int resolutionLevel)}.
         */
        INVALID
    }

    public static enum FlushMethod {
        QUICK_WITH_POSSIBLE_LOSS_OF_DATA() {
            public boolean dataMustBeFlushed() {
                return false;
            }

            public boolean forcePhysicalWriting() {
                return false;
            }
        },

        STANDARD() {
            public boolean dataMustBeFlushed() {
                return true;
            }

            public boolean forcePhysicalWriting() {
                return false;
            }
        },

        FORCE_PHYSICAL_FLUSH() {
            public boolean dataMustBeFlushed() {
                return true;
            }

            public boolean forcePhysicalWriting() {
                return true;
            }
        };

        public abstract boolean dataMustBeFlushed();

        public abstract boolean forcePhysicalWriting();
    }

    public int numberOfResolutions();

    // Relation of sizes of the level #k and the level #k+1
    public int compression();

    // Usually 1, 3, 4; must be positive; works very quickly
    public int bandCount();

    public boolean isResolutionLevelAvailable(int resolutionLevel);

    public boolean[] getResolutionLevelsAvailability();

    // First element is always equal to bandCount(); number of elements is always 3.
    // Always returns new Java array (never returns a reference to an internal field).
    public long[] dimensions(int resolutionLevel)
        throws NoSuchElementException;
    // throws if !isResolutionLevelAvailable(resolutionLevel)

    /**
     * Returns <tt>true</tt> if {@link #elementType()} method works properly.
     * In other case, that method throws <tt>UnsupportedOperationException</tt>.
     * In particular, returns <tt>true</tt> in <tt>com.simagis.pyramid.standard.PlanePyramid</tt>
     * and <tt>com.simagis.pyramid.standard.DefaultPlanePyramidSource</tt>.
     *
     * @return whether {@link #elementType()} is supported.
     */
    public boolean isElementTypeSupported();

    public Class<?> elementType() throws UnsupportedOperationException;

    public Matrix<? extends PArray> readSubMatrix(int resolutionLevel, long fromX, long fromY, long toX, long toY)
        throws NoSuchElementException, NotYetConnectedException;
    // throws if !isResolutionLevelAvailable(resolutionLevel), if !isDataReady()

    public boolean isFullMatrixSupported();

    // Works faster than equivalent readSubMatrix call if possible;
    // but may work very slowly in compressed implementations
    public Matrix<? extends PArray> readFullMatrix(int resolutionLevel)
        throws NoSuchElementException, NotYetConnectedException, UnsupportedOperationException;
    // throws if !isResolutionLevelAvailable(resolutionLevel),
    // if !isDataReady(), if !isFullMatrixSupported()

    /**
     * Returns <tt>true</tt> if this special image kind is provided by current format.
     * <p>Default implementation in <tt>AbstractPlanePyramidSource</tt> return <tt>false</tt>.
     *
     * @param kind the kind of special image.
     * @return whether this kind is supported; <tt>false</tt> by default.
     */
    public boolean isSpecialMatrixSupported(SpecialImageKind kind);

    // If there is no appropriate image, equivalent to readFullMatrix(numberOfResolutions()-1)
    public Matrix<? extends PArray> readSpecialMatrix(SpecialImageKind kind)
        throws NotYetConnectedException;

    /**
     * Returns true if the data at all available levels of this source are available.
     * In other case, {@link #readSubMatrix(int, long, long, long, long)} and {@link #readFullMatrix(int)}
     * method can throw <tt>NotYetConnectedException</tt> (but also, maybe, work properly),
     * <p>Note: all other methods, including {@link #dimensions(int)}, must work without
     * <tt>NotYetConnectedException</tt> even if this method returns <tt>false</tt>.
     *
     * @return whether the data at all available levels of this source are available.
     */
    public boolean isDataReady();

    /**
     * Reinitializes object and loads all necessary resources.
     * <p>This method should be called before using this object, if
     * {@link #freeResources(PlanePyramidSource.FlushMethod)} was called before.
     * If you will not directly call this method, all will work normally,
     * but some classes as <tt>com.simagis.pyramid.standard.PlanePyramid</tt> may work more slowly.
     * For example, <tt>com.simagis.pyramid.standard.PlanePyramid</tt> always works with clones of this object,
     * so, if it is not initialized, then all its clones will be also non-initialized
     * and will be reinitialized while every usage.
     */
    public void loadResources();

    public void freeResources(FlushMethod flushMethod);
}
