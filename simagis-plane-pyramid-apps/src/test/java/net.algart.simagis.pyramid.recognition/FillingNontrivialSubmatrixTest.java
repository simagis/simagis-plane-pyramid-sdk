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

package net.algart.simagis.pyramid.recognition;

import net.algart.arrays.Arrays;
import net.algart.arrays.Matrix;
import net.algart.arrays.UpdatablePArray;
import net.algart.external.ExternalAlgorithmCaller;
import net.algart.external.MatrixToBufferedImageConverter;
import net.algart.simagis.pyramid.PlanePyramidTools;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;

/**
 * <p>Some special test for {@link PlanePyramidTools#fillMatrix(Matrix, double[])} method.</p>
 */
public class FillingNontrivialSubmatrixTest {
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.printf("Usage: %s resultFile.bmp%n", FillingNontrivialSubmatrixTest.class.getName());
            return;
        }
        final File file = new File(args[0]);
        final File fileNoExt= ExternalAlgorithmCaller.removeFileExtension(file);
        final Matrix<? extends UpdatablePArray> matrix = Arrays.SMM.newByteMatrix(3, 256, 256);
        System.out.println("Filling my PlanePyramidTools...");
        PlanePyramidTools.fillMatrix(matrix.subMatrix(
                0, 140, 31, 3, 14440, 33338, Matrix.ContinuationMode.NULL_CONSTANT),
            new double[] {1.0, 1.0, 0.0});
        ImageIO.write(new MatrixToBufferedImageConverter.Packed3DToPackedRGB(false).toBufferedImage(matrix),
            "bmp", new File(fileNoExt.getPath() + ".sdk.bmp"));
        System.out.println("Filling my AlgART...");
        matrix.array().fill(0.0);
        matrix.subMatrix(
            0, 140, 31, 3, 14040, 33380, Matrix.ContinuationMode.NULL_CONSTANT).array().fill(
            matrix.array().maxPossibleValue(1.0));
        ImageIO.write(new MatrixToBufferedImageConverter.Packed3DToPackedRGB(false).toBufferedImage(matrix),
            "bmp", new File(fileNoExt.getPath() + ".algart.bmp"));
    }
}
