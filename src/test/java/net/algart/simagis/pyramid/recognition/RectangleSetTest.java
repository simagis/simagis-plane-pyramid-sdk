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

import net.algart.arrays.Arrays;
import net.algart.arrays.ExternalProcessor;
import net.algart.arrays.Matrix;
import net.algart.arrays.UpdatablePArray;
import net.algart.external.ExternalAlgorithmCaller;
import net.algart.math.IPoint;
import net.algart.math.IRectangularArea;
import net.algart.simagis.pyramid.recognition.rectangles.RectangleSet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class RectangleSetTest {
    public static void main(String[] args) throws IOException, JSONException {
        if (args.length < 6) {
            System.out.println("Usage:");
            System.out.println("    " + RectangleSetTest.class.getName()
                + " numberOfTests rectangles-description.json width height coordinate-divider demo-files-folder");
            return;
        }
        final int numberOfTests = Integer.parseInt(args[0]);
        final File rectanglesFile = new File(args[1]);
        final long width = Long.parseLong(args[2]);
        final long height = Long.parseLong(args[3]);
        final double coordinateDivider = Double.parseDouble(args[4]);
        final File demoFolder = new File(args[5]);
        final Random random = new Random(157);
        demoFolder.mkdirs();
        final JSONObject rectanglesJson = new JSONObject(ExternalProcessor.readUTF8(rectanglesFile));
        final JSONArray rectanglesArray = rectanglesJson.has("origins") ?
            rectanglesJson.optJSONArray("origins") :
            rectanglesJson.optJSONArray("rectangles");
        final String algorithm = rectanglesJson.optString("algorithm", null);
        final List<IRectangularArea> rectangles = new ArrayList<IRectangularArea>();
        if (rectanglesArray != null) {
            System.out.println("Reading rectangles...");
            for (int k = 0, n = rectanglesArray.length(); k < n; k++) {
                final JSONObject rectangleJson = rectanglesArray.getJSONObject(k);
                rectangles.add(IRectangularArea.valueOf(
                    IPoint.valueOf(
                        rectangleJson.getInt("minX"),
                        rectangleJson.getInt("minY")),
                    IPoint.valueOf(
                        rectangleJson.getInt("maxX"),
                        rectangleJson.getInt("maxY"))));
            }
        } else if (algorithm != null) {
            System.out.println("Creating rectangles...");
            if (algorithm.equals("regular")) {
                final int frameWidth = rectanglesJson.getInt("frameWidth");
                final int frameHeight = rectanglesJson.getInt("frameHeight");
                final int horizontalCount = rectanglesJson.getInt("horizontalCount");
                final int verticalCount = rectanglesJson.getInt("verticalCount");
                final int overlap = rectanglesJson.getInt("overlap");
                final int maxError = rectanglesJson.getInt("maxError");
                final Random rnd = new Random(rectanglesJson.optLong("randSeed", new Random().nextLong()));
                for (int i = 0; i < horizontalCount; i++) {
                    for (int j = 0; j < verticalCount; j++) {
                        final long x = j * (frameWidth - overlap) + rnd.nextInt(maxError) - maxError / 2;
                        final long y = i * (frameHeight - overlap) + rnd.nextInt(maxError) - maxError / 2;
                        rectangles.add(IRectangularArea.valueOf(
                            IPoint.valueOf(x, y),
                            IPoint.valueOf(x + frameWidth - 1, y + frameHeight - 1)));
                    }
                }
            } else {
                throw new JSONException("Unknown generating algorithm " + algorithm);
            }
        } else {
            throw new JSONException("JSON file \"" + rectanglesFile
                + "\" must contain either the list of rectangles or description of the generating algorithm");
        }
        Matrix<? extends UpdatablePArray> demo = Arrays.SMM.newByteMatrix(width, height);
        for (IRectangularArea area : rectangles) {
            demo.subMatrix(divide(area, coordinateDivider),
                Matrix.ContinuationMode.NULL_CONSTANT).array().fill(100 + random.nextInt(100));
        }
        final File sourceFile = new File(demoFolder, rectanglesFile.getName() + ".source.bmp");
        System.out.printf("Writing source image into %s: %d rectangles%n", sourceFile, rectangles.size());
        ExternalAlgorithmCaller.writeImage(sourceFile, Collections.singletonList(demo));

        RectangleSet rectangleSet = null;
        for (int testIndex = 0; testIndex < numberOfTests; testIndex++) {
            System.out.printf("Test #%d%n", testIndex);
            rectangleSet = RectangleSet.newInstance(rectangles);
            rectangleSet.findConnectedComponents();
        }
        if (rectangleSet == null) {
            throw new IllegalArgumentException("Zero or negative number of tests");
        }
        for (int k = 0; k < Math.min(10, rectangleSet.connectedComponentCount()); k++) {
            demo = Arrays.SMM.newByteMatrix(width, height);
            final RectangleSet connectedSet = rectangleSet.connectedComponent(k);
            for (RectangleSet.Frame frame : connectedSet.frames()) {
                demo.subMatrix(divide(frame.rectangle(), coordinateDivider),
                    Matrix.ContinuationMode.NULL_CONSTANT).array().fill(100 + random.nextInt(100));
            }
            final File f = new File(demoFolder, rectanglesFile.getName() + ".component" + k + ".bmp");
            System.out.printf("Writing component #%d into %s: %s%n", k + 1, f, connectedSet);
            ExternalAlgorithmCaller.writeImage(f, Collections.singletonList(demo));
        }
    }

    private static IRectangularArea divide(IRectangularArea area, double coordinateDivider) {
        return IRectangularArea.valueOf(
            area.min().multiply(1.0 / coordinateDivider),
            area.max().multiply(1.0 / coordinateDivider));
    }
}
