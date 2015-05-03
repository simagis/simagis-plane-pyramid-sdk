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
import net.algart.contexts.DefaultArrayContext;
import net.algart.external.BufferedImageToMatrixConverter;
import net.algart.external.ExternalAlgorithmCaller;
import net.algart.external.MatrixToBufferedImageConverter;
import net.algart.math.IPoint;
import net.algart.math.functions.Func;
import net.algart.math.functions.LinearFunc;
import net.algart.math.patterns.Patterns;
import net.algart.matrices.morphology.BasicMorphology;
import net.algart.matrices.morphology.IterativeErosion;
import net.algart.simagis.pyramid.AbstractPlanePyramidSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class SquaresAtImageTest {
    public static class MyContext extends AbstractArrayContext {
        @Override
        public MemoryModel getMemoryModel() {
            return Arrays.SMM;
        }

        @Override
        public ThreadPoolFactory getThreadPoolFactory() {
            return DefaultThreadPoolFactory.getDefaultThreadPoolFactory();
        }

        @Override
        public void checkInterruption() throws RuntimeException {
        }

        @Override
        public void updateProgress(Event event) {
            System.out.print(".");
//            System.out.printf("\n%d/%d\n", event.readyCount(), event.length());
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Usage:");
            System.out.println("    " + SquaresAtImageTest.class.getName() + " binary-image-file maxNumberOfSquares");
            return;
        }
        final File file = new File(args[0]);
        final String fileName = ExternalAlgorithmCaller.removeFileExtension(file).getName();
        final int maxNumberOfSquares = Integer.parseInt(args[1]);
        /* //TODO!! debug this
        final BufferedImage bufferedImage = ImageIO.read(file);
        if (bufferedImage == null) {
            throw new IOException("Cannot read " + file);
        }
        final Matrix<? extends UpdatablePArray> matrix3D = new BufferedImageToMatrixConverter.ToPacked3D(false)
            .toMatrix(bufferedImage);
            */
        final List<Matrix<? extends PArray>> bands = ExternalAlgorithmCaller.readImage(file);
        final Matrix<BitArray> binary = Matrices.asFuncMatrix(Func.IDENTITY, BitArray.class, bands.get(0));
        // - only 1st component

/*
        final IterativeErosion iterativeErosion = IterativeErosion.getInstance(
            BasicMorphology.getInstance(new MyContext()),
            UpdatableIntArray.class,
            // 31-bit precision guarangees correct work even for very large matrices;
            // in a very improbable case of overflow we just will not able to find the maximum exactly
            binary,
            Patterns.newRectangularIntegerPattern(
                IPoint.valueOfEqualCoordinates(binary.dimCount(), -1),
                IPoint.valueOfEqualCoordinates(binary.dimCount(), 1)));
        iterativeErosion.process();
        ImageIO.write(
            new MatrixToBufferedImageConverter.Packed3DToPackedRGB(false).toBufferedImage(
                Matrices.asFuncMatrix(Func.IDENTITY, ByteArray.class,
                    iterativeErosion.result())),
            "PNG", new File(file.getParentFile(), "result.erosion." + fileName + ".png"));
        System.out.println("EROSION done");
*/
        SquaresAtObject squaresAtObject = SquaresAtObject.getInstance(binary);
        ImageIO.write(
            new MatrixToBufferedImageConverter.Packed3DToPackedRGB(false).toBufferedImage(
                Matrices.asFuncMatrix(LinearFunc.getInstance(0, 255.0), ByteArray.class,
                    squaresAtObject.getMatrixWithoutFoundSquares())),
            "PNG", new File(file.getParentFile(), "result.original." + fileName + ".png"));

        squaresAtObject.setMaxNumberOfSquares(maxNumberOfSquares);
        squaresAtObject.findSquares(null);

        ImageIO.write(
            new MatrixToBufferedImageConverter.Packed3DToPackedRGB(false).toBufferedImage(
                Matrices.asFuncMatrix(LinearFunc.getInstance(0, 255.0), ByteArray.class,
                    squaresAtObject.getMatrixWithoutFoundSquares())),
            "PNG", new File(file.getParentFile(), "result.squares." + fileName + ".png"));
    }
}
