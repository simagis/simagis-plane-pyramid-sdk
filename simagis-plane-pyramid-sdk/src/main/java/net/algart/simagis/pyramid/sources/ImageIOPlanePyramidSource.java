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

package net.algart.simagis.pyramid.sources;

import net.algart.simagis.pyramid.AbstractPlanePyramidSourceWrapper;
import net.algart.simagis.pyramid.PlanePyramidSource;
import net.algart.simagis.pyramid.PlanePyramidTools;
import net.algart.arrays.*;
import net.algart.contexts.Context;
import net.algart.contexts.DefaultArrayContext;
import net.algart.external.BufferedImageToMatrixConverter;
import net.algart.external.ExternalAlgorithmCaller;

import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class ImageIOPlanePyramidSource extends AbstractPlanePyramidSourceWrapper implements PlanePyramidSource {
    private static final String CACHE_READY_MARKER_FILE = ".ready";
    private static final int COMPRESSION = Math.max(2, Arrays.SystemSettings.getIntProperty(
        "net.algart.simagis.pyramid.sources.ImageIOPlanePyramidSource.compression", 2));
    private static final int MIN_PYRAMID_LEVEL_SIDE = Arrays.SystemSettings.getIntProperty(
        "net.algart.simagis.pyramid.sources.ImageIOPlanePyramidSource.minPyramidLevelSide", 512);

    private final DefaultPlanePyramidSource parent;

    public static class ImageIOReadingBehaviour implements Cloneable {
        protected int imageIndex = 0;
        private boolean addAlphaWhenExist = false;
        private boolean readPixelValuesViaColorModel =
            BufferedImageToMatrixConverter.ToPacked3D.DEFAULT_READ_PIXEL_VALUES_VIA_COLOR_MODEL;
        private boolean readPixelValuesViaGraphics2D =
            BufferedImageToMatrixConverter.ToPacked3D.DEFAULT_READ_PIXEL_VALUES_VIA_GRAPHICS_2D;
        // - ignored (as true value) for non-8-bit images
        private boolean dicomReader = false;

        private volatile int lastImageCount = -1;

        public int getImageIndex() {
            return imageIndex;
        }

        public void setImageIndex(int imageIndex) {
            this.imageIndex = imageIndex;
        }

        public boolean isAddAlphaWhenExist() {
            return addAlphaWhenExist;
        }

        public ImageIOReadingBehaviour setAddAlphaWhenExist(boolean addAlphaWhenExist) {
            this.addAlphaWhenExist = addAlphaWhenExist;
            return this;
        }

        public boolean isReadPixelValuesViaColorModel() {
            return readPixelValuesViaColorModel;
        }

        public ImageIOReadingBehaviour setReadPixelValuesViaColorModel(
            boolean readPixelValuesViaColorModel)
        {
            this.readPixelValuesViaColorModel = readPixelValuesViaColorModel;
            return this;
        }

        public boolean isReadPixelValuesViaGraphics2D() {
            return readPixelValuesViaGraphics2D;
        }

        public ImageIOReadingBehaviour setReadPixelValuesViaGraphics2D(
            boolean readPixelValuesViaGraphics2D)
        {
            this.readPixelValuesViaGraphics2D = readPixelValuesViaGraphics2D;
            return this;
        }

        public boolean isDicomReader() {
            return dicomReader;
        }

        public ImageIOReadingBehaviour setDicomReader(boolean dicomReader) {
            this.dicomReader = dicomReader;
            return this;
        }

        public int getLastImageCount() {
            return lastImageCount;
        }

        public BufferedImage read(File imageFile) throws IOException {
            final ImageInputStream iis = ImageIO.createImageInputStream(imageFile);
            try {
                ImageReader reader = getImageReader(iis);
                try {
                    ImageReadParam param = getReadParam(reader);
                    reader.setInput(iis, false);
                    final BufferedImage result = readBufferedImageByReader(reader, param);
                    this.lastImageCount = reader.getNumImages(false);
                    return result;
                } finally {
                    reader.dispose();
                }
            } finally {
                if (iis != null) {
                    iis.close();
                }
            }
        }

        public int imageCount(File imageFile) throws IOException {
            final ImageInputStream iis = ImageIO.createImageInputStream(imageFile);
            try {
                ImageReader reader = getImageReader(iis);
                try {
                    reader.setInput(iis, false);
                    return reader.getNumImages(true);
                } finally {
                    reader.dispose();
                }
            } finally {
                if (iis != null) {
                    iis.close();
                }
            }
        }

        public ImageIOReadingBehaviour clone() {
            try {
                return (ImageIOReadingBehaviour) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new InternalError(e.toString());
            }
        }

        @Override
        public String toString() {
            return "ImageIOReadingBehaviour{"
                + "imageIndex=" + getImageIndex()
                + ", addAlphaWhenExist=" + isAddAlphaWhenExist()
                + ", readPixelValuesViaColorModel=" + isReadPixelValuesViaColorModel()
                + ", readPixelValuesViaGraphics2D=" + isReadPixelValuesViaGraphics2D()
                + ", dicomReader=" + dicomReader
                + '}';
        }

        protected ImageReader getImageReader(ImageInputStream imageInputStream) throws IIOException {
            if (imageInputStream == null)
                throw new IIOException("Cannot create image input stream: no suitable ImageInputStreamSpi exists");
            final Iterator<ImageReader> iterator;
            if (dicomReader) {
                iterator = ImageIO.getImageReadersByFormatName("DICOM");
                if (!iterator.hasNext()) {
                    throw new IIOException("No available DICOM image reader");
                }
            } else {
                iterator = ImageIO.getImageReaders(imageInputStream);
                if (!iterator.hasNext())
                    throw new IIOException("Unknown image format: no suitable ImageIO readers");
            }
            return iterator.next();
        }

        protected ImageReadParam getReadParam(ImageReader imageReader) {
            return imageReader.getDefaultReadParam();
        }

        protected BufferedImage readBufferedImageByReader(ImageReader reader, ImageReadParam param)
            throws IOException
        {
            return reader.read(imageIndex, param);
        }
    }


    public ImageIOPlanePyramidSource(File imageFile) throws IOException {
        this(null, null, imageFile, null, new ImageIOReadingBehaviour());
    }

    public ImageIOPlanePyramidSource(
        Context context,
        File pyramidCacheDir,
        File imageFile,
        ImageIOReadingBehaviour imageIOReadingBehaviour)
        throws IOException
    {
        this(context, pyramidCacheDir, imageFile, null, imageIOReadingBehaviour);
    }

    public ImageIOPlanePyramidSource(
        Context context,
        File pyramidCacheDir,
        BufferedImage image,
        ImageIOReadingBehaviour imageIOReadingBehaviour)
        throws IOException
    {
        this(context, pyramidCacheDir, null, image, imageIOReadingBehaviour);
    }

    private ImageIOPlanePyramidSource(
        Context context,
        File pyramidCacheDir,
        File imageFile,
        BufferedImage image,
        ImageIOReadingBehaviour imageIOReadingBehaviour)
        throws IOException
    {
        if (imageFile == null && image == null)
            throw new NullPointerException("Null image or path to image");
        if (imageIOReadingBehaviour == null)
            throw new NullPointerException("Null imageIOReadingBehaviour");
        ArrayContext ac = context == null ? ArrayContext.DEFAULT : new DefaultArrayContext(context);
        long t1 = System.nanoTime();
        final List<Matrix<? extends PArray>> pyramid = openExistingPyramid(pyramidCacheDir);
        if (pyramid != null) {
            final int[] dimensions = image != null ?
                new int[]{image.getWidth(), image.getHeight()} :
                ExternalAlgorithmCaller.readImageDimensions(imageFile);
            if (pyramid.get(0).dim(1) != dimensions[0] || pyramid.get(0).dim(2) != dimensions[1])
                throw new IOException("Illegal or corrupted cache: the pyramid in cache has zero-level "
                    + pyramid.get(0).dim(1) + "x" + pyramid.get(0).dim(2) + "(x" + pyramid.get(0).dim(0)
                    + "), but the passed image is " + dimensions[0] + "x" + dimensions[1]);
            this.parent = new DefaultPlanePyramidSource(ac, pyramid);
            long t2 = System.nanoTime();
            if (DEBUG_LEVEL >= 1) {
                System.out.printf("ImageIOPlanePyramidSource opens image from cache %s: "
                    + "%dx%d, %d bands, %d levels (%.3f ms)%n",
                    pyramidCacheDir,
                    pyramid.get(0).dim(1), pyramid.get(0).dim(2), parent.bandCount(),
                    parent.numberOfResolutions(), (t2 - t1) * 1e-6);
            }
            return;
        }
        long t2 = System.nanoTime();
        if (image == null) {
            image = imageIOReadingBehaviour.read(imageFile);
        }
        int[] bitsPerElements = image.getSampleModel().getSampleSize();
        boolean depth8 = true;
        for (int sampleSize : bitsPerElements) {
            depth8 &= sampleSize == 8;
        }
        final Matrix<? extends PArray> matrixZero =
            new BufferedImageToMatrixConverter.ToPacked3D(imageIOReadingBehaviour.addAlphaWhenExist)
                .setReadPixelValuesViaColorModel(imageIOReadingBehaviour.readPixelValuesViaColorModel)
                .setReadPixelValuesViaGraphics2D(imageIOReadingBehaviour.readPixelValuesViaGraphics2D
                    || !depth8)
                .toMatrix(image);
        // !depth8: this class does not try to read 16/32/64-bit pictures (to be on the safe side),
        // it uses for them simples way via copying into 8-bit Graphics2D

        image = null; // attempt to help garbage collector to free memory
        if (pyramidCacheDir != null) { // it is AFTER ImageIO.read, for a case of some errors while reading
            if (!pyramidCacheDir.mkdir()) {
                if (pyramidCacheDir.exists())
                    throw new IOException("Cannot create " + pyramidCacheDir
                        + ": this directory already exists, and " + getClass() + " has no right to overwrite it");
                else
                    throw new FileNotFoundException("Cannot create " + pyramidCacheDir);
                // Important note: we must attempt to create pyramidCacheDir BEFORE checking its existence;
                // in other case, using this class from parallel threads can lead to attempt to create
                // this directory twice
            }
        }
        long t3 = System.nanoTime();
        final int numberOfResolutions = PlanePyramidTools.numberOfResolutions(
            matrixZero.dim(1), matrixZero.dim(2),
            COMPRESSION, MIN_PYRAMID_LEVEL_SIDE);
        final List<Matrix<? extends UpdatablePArray>> newPyramid = createNewPyramid(
            ac, pyramidCacheDir,
            matrixZero.elementType(), matrixZero.dim(0), matrixZero.dim(1), matrixZero.dim(2),
            COMPRESSION, numberOfResolutions);
        long t4 = System.nanoTime();
        buildNewPyramid(ac, newPyramid, matrixZero, COMPRESSION, Matrices.ResizingMethod.AVERAGING);
        long t5 = System.nanoTime();
        finishNewPyramid(pyramidCacheDir, newPyramid);
        this.parent = new DefaultPlanePyramidSource(ac, newPyramid);
        long t6 = System.nanoTime();
        if (DEBUG_LEVEL >= 1) {
            System.out.printf("ImageIOPlanePyramidSource created new plane pyramid %s "
                + "(source #%d/%d, [%s] bits/pixel): "
                + "%dx%d, %d bands, %d levels, compression in %d times"
                + " (%.3f ms = %.3f start + %.3f reading  + %.3f creating + %.3f compression + %.3f finish); "
                + " settings: %s%n",
                pyramidCacheDir == null ? "in temporary files" : "cached in " + pyramidCacheDir,
                imageIOReadingBehaviour.getImageIndex(), imageIOReadingBehaviour.getLastImageCount(),
                JArrays.toString(bitsPerElements, ",", 200),
                newPyramid.get(0).dim(1), newPyramid.get(0).dim(2), parent.bandCount(),
                parent.numberOfResolutions(), parent.compression(),
                (t6 - t1) * 1e-6,
                (t2 - t1) * 1e-6, (t3 - t2) * 1e-6, (t4 - t3) * 1e-6, (t5 - t4) * 1e-6, (t6 - t5) * 1e-6,
                imageIOReadingBehaviour);
        }
    }

    @Override
    protected PlanePyramidSource parent() {
        return parent;
    }

    public boolean isContinuationEnabled() {
        return parent.isContinuationEnabled();
    }

    public ImageIOPlanePyramidSource setContinuationEnabled(boolean continuationEnabled) {
        parent.setContinuationEnabled(continuationEnabled);
        return this;
    }

    private static List<Matrix<? extends PArray>> openExistingPyramid(
        File pyramidDir)
        throws IOException
    {
        if (pyramidDir == null) {
            return null;
        }
        if (pyramidDir.exists() && new File(pyramidDir, CACHE_READY_MARKER_FILE).exists()) {
            final List<Matrix<? extends PArray>> pyramid = new ArrayList<Matrix<? extends PArray>>();
            for (int level = 0; ; level++) {
                File matrixDir = new File(pyramidDir, "m" + level);
                if (level > 0 && !matrixDir.exists()) {
                    break;
                }
                String path = matrixDir.getAbsolutePath();
                if (!matrixDir.isDirectory())
                    throw new FileNotFoundException("Matrix path not found or not a directory: " + path);
                MatrixInfo mi = readMatrixInfo(matrixDir, "index");
                File matrixFile = new File(matrixDir, "matrix");
                final Matrix<? extends PArray> matrix;
                try {
                    matrix = LargeMemoryModel.getInstance(new StandardIODataFileModel()).asMatrix(
                        matrixFile.getAbsoluteFile(), mi);
                } catch (IllegalInfoSyntaxException e) {
                    IOException ex = new IOException(e.getMessage());
                    ex.initCause(e);
                    throw ex;
                }
                pyramid.add(matrix);
            }
            assert pyramid.size() > 0;
            return pyramid;
        } else {
            return null;
        }
    }

    private static List<Matrix<? extends UpdatablePArray>> createNewPyramid(
        ArrayContext context,
        File pyramidDir,
        Class<?> elementType,
        long bandCount, long dimX, long dimY,
        int compression,
        int numberOfResolutions)
        throws IOException
    {
        final List<Matrix<? extends UpdatablePArray>> result = new ArrayList<Matrix<? extends UpdatablePArray>>();
        for (int level = 0; level < numberOfResolutions; level++) {
            MemoryModel mm;
            if (pyramidDir != null) {
                File matrixDir = new File(pyramidDir, "m" + level);
                if (!matrixDir.mkdir())
                    throw new IOException("Cannot create " + matrixDir);
                mm = LargeMemoryModel.getInstance(new DefaultDataFileModel(new File(matrixDir, "matrix")));
            } else {
                mm = context == null ? Arrays.SMM : context.getMemoryModel();
            }
            Matrix<UpdatablePArray> m = mm.newMatrix(UpdatablePArray.class, elementType, bandCount, dimX, dimY);
            m = m.tile(bandCount, DEFAULT_TILE_DIM, DEFAULT_TILE_DIM);
            if (pyramidDir != null) {
                PArray a = LargeMemoryModel.getRawArrayForSavingInFile(m);
                if (LargeMemoryModel.isLargeArray(a)) {
                    LargeMemoryModel.setTemporary(a, false);
                }
            }
            result.add(m);
            dimX /= compression;
            dimY /= compression;
        }
        return result;
    }

    private static void buildNewPyramid(
        ArrayContext arrayContext,
        List<Matrix<? extends UpdatablePArray>> pyramid,
        Matrix<? extends PArray> matrixZero,
        int compression,
        Matrices.ResizingMethod resizingMethod)
    {
        final int numberOfResolutions = pyramid.size();
        Matrices.copy(
            arrayContext == null ? null : arrayContext.part(0, 1, numberOfResolutions),
            pyramid.get(0), matrixZero);
        for (int level = 1; level < numberOfResolutions; level++) {
            Matrix<? extends PArray> src = level == 1 ? matrixZero : pyramid.get(level - 1);
            Matrix<? extends UpdatablePArray> dest = pyramid.get(level);
            if (src.dim(1) != dest.dim(1) * compression || src.dim(2) != dest.dim(2) * compression) {
                src = src.subMatr(0, 0, 0, src.dim(0), dest.dim(1) * compression, dest.dim(2) * compression);
                // optimizing resizing
            }
            Matrices.resize(
                arrayContext == null ? null : arrayContext.part(level, level + 1, numberOfResolutions),
                resizingMethod, dest, src);
        }
    }

    private static void finishNewPyramid(
        File pyramidDir,
        List<Matrix<? extends UpdatablePArray>> pyramid)
        throws IOException
    {
        if (pyramidDir == null) {
            return; // nothing to do: cache in usual temporary files
        }
        for (Matrix<? extends PArray> m : pyramid) {
            final PArray array = LargeMemoryModel.getRawArrayForSavingInFile(m);
            File data = LargeMemoryModel.getInstance().getDataFilePath(array);
            File matrixDir = data.getParentFile();
            MatrixInfo mi = LargeMemoryModel.getMatrixInfoForSavingInFile(m, 0);
            File indexFile = new File(matrixDir, "index");
            assert !indexFile.exists();
            final FileOutputStream outputStream = new FileOutputStream(indexFile);
            try {
                outputStream.write(mi.toBytes());
            } finally {
                outputStream.close();
            }
            // but don't free resources: we need to stay them maximally alive
        }
        new File(pyramidDir, CACHE_READY_MARKER_FILE).createNewFile();
    }

    // A copy of SimpleMatrixContext.readMatrixInfo
    private static MatrixInfo readMatrixInfo(File directory, String indexFileName) throws IOException {
        File indexFile = new File(directory, indexFileName);
        try {
            final ByteArray byteArray = LargeMemoryModel.getInstance(
                new StandardIODataFileModel(false, false)).asByteArray(
                indexFile, 0, LargeMemoryModel.ALL_FILE, ByteOrder.nativeOrder());
            try {
                byte[] ja = new byte[MatrixInfo.MAX_SERIALIZED_MATRIX_INFO_LENGTH];
                byteArray.getData(0, ja);
                return MatrixInfo.valueOf(ja);
            } finally {
                byteArray.freeResources(null);
            }
        } catch (IllegalInfoSyntaxException e) {
            IOException ex = new IOException(e.getMessage());
            ex.initCause(e);
            throw ex;
        }
    }
}
