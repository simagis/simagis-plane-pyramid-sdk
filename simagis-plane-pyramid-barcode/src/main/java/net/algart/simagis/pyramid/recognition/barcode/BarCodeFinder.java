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

package net.algart.simagis.pyramid.recognition.barcode;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class BarCodeFinder {
    private final Result result;

    public BarCodeFinder(BufferedImage image) {
        this(image, defaultHints());
    }

    public BarCodeFinder(BufferedImage image, Map<DecodeHintType, ?> hints) {
        final BinaryBitmap binary = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        final MultiFormatReader barCodeReader = new MultiFormatReader();
        Result result = null;
        try {
            result = barCodeReader.decode(binary, hints);
        } catch (NotFoundException e) {
            // stay null
        }
        this.result = result;
    }

    public boolean isFound() {
        return result != null;
    }

    public String getBarCodeText() {
        if (result == null) {
            throw new IllegalStateException("Bar code is not found");
        }
        return result.getText();
    }

    public BarcodeFormat getBarCodeFormat() {
        if (result == null) {
            throw new IllegalStateException("Bar code is not found");
        }
        return result.getBarcodeFormat();
    }

    private static Map<DecodeHintType, ?> defaultHints() {
        final Map<DecodeHintType, Object> hints = new HashMap<DecodeHintType, Object>();
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        return hints;
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.printf("Usage: %s image1.png image2.png...%n", BarCodeFinder.class.getName());
            return;
        }
        int successfullCount = 0;
        for (String arg : args) {
            final File file = new File(arg);
            System.out.printf("Loading image from %s...%n", file);
            final BufferedImage image = ImageIO.read(file);
            if (image == null) {
                throw new IIOException("Image cannot be read");
            }
            System.out.printf("Recognizing bar code in %s...%n", file);
            long t1 = System.nanoTime();
            final BarCodeFinder finder = new BarCodeFinder(image);
            long t2 = System.nanoTime();
            if (finder.isFound()) {
                System.out.printf("BAR CODE FOUND (format %s): %s (%.3f ms)%n",
                    finder.getBarCodeFormat(), finder.getBarCodeText(), (t2 - t1) * 1e-6);
                successfullCount++;
            } else {
                System.out.printf("Bar code not found! (%.3f ms)%n", (t2 - t1) * 1e-6);
            }
            System.out.println();
        }
        System.out.printf("%d successfull recognitions from %d%n", successfullCount, args.length);
    }
}
