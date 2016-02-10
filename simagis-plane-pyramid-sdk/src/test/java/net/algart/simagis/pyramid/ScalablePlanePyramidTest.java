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

import net.algart.external.ExternalAlgorithmCaller;
import net.algart.external.MatrixToBufferedImageConverter;
import net.algart.simagis.pyramid.sources.ImageIOPlanePyramidSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class ScalablePlanePyramidTest {
    public static void main(String[] args) throws IOException {
        if (args.length < 7) {
            System.out.println("Usage: " + ScalablePlanePyramidTest.class.getName()
                + " result-image-file source-image-file fromX fromY toX toY compression [pyramidCompression]");
            return;
        }
        final File resultFile = new File(args[0]);
        final File sourceFile = new File(args[1]);
        final long fromX = Long.parseLong(args[2]);
        final long fromY = Long.parseLong(args[3]);
        final long toX = Long.parseLong(args[4]);
        final long toY = Long.parseLong(args[5]);
        final double compression = Double.parseDouble(args[6]);
        final int pyramidCompression = args.length > 7 ? Integer.parseInt(args[7]) : 0;
        final ScalablePlanePyramidSource pyramid = new ScalablePlanePyramidSource(
            new ImageIOPlanePyramidSource(sourceFile), pyramidCompression);
        final BufferedImage bufferedImage = pyramid.readBufferedImage(fromX, fromY, toX, toY, compression,
            new MatrixToBufferedImageConverter.Packed3DToPackedRGB(true));
        ImageIO.write(bufferedImage, ExternalAlgorithmCaller.getFileExtension(resultFile), resultFile);
    }
}