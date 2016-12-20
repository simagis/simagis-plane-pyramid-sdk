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
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

public class BarCodeRecognizer {
    private static final Logger LOGGER = Logger.getLogger(BarCodeRecognizer.class.getName());
    private static final double[] CONTRASTS_TO_TRY = {3.0, 10.0, 20.0};

    private static final double STANDARD_WHOLE_SLIDE_ASPECT_RATIO = 2.8846153846153846153846153846154;
    // - 75000/26000, typical value for medicine
    private static final double ALLOWED_WHOLE_SLIDE_ASPECT_RATION_DEVIATION = 0.2;
    private static final double ESTIMATED_LABEL_ASPECT_RATIO = 1.0;
    // - must be not less than real ratio; some real ratios: 0.84, 0.86, 0.95, 0.91, 1.0, 0.56, 0.75, 0.90

    private final BufferedImage sourceImage;
    private final BinaryBitmap binary;
    private final Result result;

    BarCodeRecognizer(BufferedImage image) {
        this(image, defaultHints());
    }

    BarCodeRecognizer(BufferedImage image, Map<DecodeHintType, ?> hints) {
        this.sourceImage = image;
        this.binary = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        final MultiFormatReader barCodeReader = new MultiFormatReader();
        barCodeReader.setHints(hints);
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

    public String getBarCodeFormatName() {
        return getBarCodeFormat().toString();
    }

    public static BarCodeRecognizer recognize(BufferedImage image) {
        final BufferedImage label = extractLabel(image);
        BarCodeRecognizer result = recognizeLabel(label);
        if (!result.isFound() && label != image) {
            LOGGER.info("Recognizing bar code on label area failed; trying full slide...");
            result = recognizeLabel(image);
        }
        return result;
    }

    // Returns the argument "image" if label was not extracted.
    private static BarCodeRecognizer recognizeLabel(BufferedImage image) {
        long t1 = System.nanoTime();
        BarCodeRecognizer result = new BarCodeRecognizer(image);
        double resultScale = 1.0;
        if (!result.isFound()) {
            final BufferedImage clone = cloneBufferedImage(image);
            for (double scale : CONTRASTS_TO_TRY) {
                resultScale = scale;
                contrastOp(10).filter(image, clone);
                result = new BarCodeRecognizer(clone);
                if (result.isFound()) {
                    break;
                }
            }
        }
        long t2 = System.nanoTime();
        LOGGER.info(String.format(Locale.US, "Image %dx%d processed in %.3f ms, bar code %s",
            image.getWidth(), image.getHeight(), (t2 - t1) * 1e-6, result.isFound() ? "found" : "NOT FOUND"));
        if (result.isFound()) {
            LOGGER.info(String.format("BarCode: %s (format: %s)%s",
                result.getBarCodeText(), result.getBarCodeFormat(),
                resultScale == 1.0 ? "" : ", found after contrasting with scale " + resultScale));
        }
        return result;
    }

    private static BufferedImage extractLabel(BufferedImage slide) {
        final int width = slide.getWidth();
        final int height = slide.getHeight();
        final double ratio = (double) width / (double) height;
        // We suppose that the slide is oriented correctly: horizontally with the label on the left side
        if (ratio > STANDARD_WHOLE_SLIDE_ASPECT_RATIO * (1.0 - ALLOWED_WHOLE_SLIDE_ASPECT_RATION_DEVIATION)
            && ratio < STANDARD_WHOLE_SLIDE_ASPECT_RATIO / (1.0 - ALLOWED_WHOLE_SLIDE_ASPECT_RATION_DEVIATION))
        {
            // probably it is a standard medical slide
            LOGGER.info(String.format(Locale.US, "Image is a whole slide; extracting label...",
                slide.getWidth(), slide.getHeight()));
            int labelWidth = (int) Math.min(width, ESTIMATED_LABEL_ASPECT_RATIO * height);
            return cloneBufferedImage(slide.getSubimage(0, 0, labelWidth, height));
        } else {
            return slide;
        }
    }

    private static RescaleOp contrastOp(double scale) {
        return new RescaleOp((float) scale, (float) (-128 * (scale - 1.0)), null);
    }

    private static final BufferedImage cloneBufferedImage(BufferedImage image) {
        BufferedImage clone = new BufferedImage(image.getWidth(),
            image.getHeight(), image.getType());
        Graphics2D g2d = clone.createGraphics();
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();
        return clone;
    }

    private static Map<DecodeHintType, ?> defaultHints() {
        final Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, EnumSet.allOf(BarcodeFormat.class));
//        hints.put(DecodeHintType.PURE_BARCODE, Boolean.FALSE);
        return hints;
    }

    public static void main(String[] args) throws IOException, NotFoundException {
        if (args.length == 0) {
            System.out.printf("Usage: %s image1.png image2.png...%n", BarCodeRecognizer.class.getName());
            return;
        }
        int successfulCount = 0;
        for (String arg : args) {
            final File file = new File(arg);
            System.out.printf("Loading image from %s...%n", file);
            final BufferedImage image = ImageIO.read(file);
            if (image == null) {
                final String message = "Image cannot be read from " + file;
                new IIOException(message).printStackTrace();
                Files.write(Paths.get(file.getPath() + ".error"), message.getBytes(StandardCharsets.UTF_8));
                continue;
            }
            System.out.printf("Recognizing bar code in %s...%n", file);
            final BarCodeRecognizer finder = BarCodeRecognizer.recognize(image);

            ImageIO.write(finder.sourceImage, "bmp",
                new File(file.getPath() + ".contrasted.bmp"));
//            ImageIO.write(MatrixToImageWriter.toBufferedImage(finder.binary.getBlackMatrix()), "bmp",
//                new File(file.getPath() + ".binary.bmp"));

            if (finder.isFound()) {
                Files.write(Paths.get(file.getPath() + ".barcode"),
                    String.format("BarCode: %s%nFormat: %s%n", finder.getBarCodeText(), finder.getBarCodeFormat())
                        .getBytes(StandardCharsets.UTF_8));
                successfulCount++;
            }
            System.out.println();
        }
        System.out.printf("%d successfull recognitions from %d%n", successfulCount, args.length);
    }
}
