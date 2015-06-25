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

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RectangleSetTest {
    public static void main(String[] args) throws IOException, JSONException {
        if (args.length < 3) {
            System.out.println("Usage:");
            System.out.println("    " + RectangleSetTest.class.getName()
                + " numberOfTests rectangles-description.json demo-files-folder [coordinate-divider]");
            return;
        }
        final int numberOfTests = Integer.parseInt(args[0]);
        final File rectanglesFile = new File(args[1]);
        final File demoFolder = new File(args[2]);
        final double coordinateDivider;
        demoFolder.mkdirs();
        final JSONObject rectanglesJson = new JSONObject(ExternalProcessor.readUTF8(rectanglesFile));
        final JSONArray rectanglesArray = rectanglesJson.has("origins") ?
            rectanglesJson.optJSONArray("origins") :
            rectanglesJson.optJSONArray("rectangles");
        final String algorithm = rectanglesJson.optString("algorithm", null);
        Long imageWidth = null;
        Long imageHeight = null;
        final List<IRectangularArea> rectangles = new ArrayList<IRectangularArea>();
        if (rectanglesArray != null) {
            System.out.println("Reading rectangles...");
            coordinateDivider = args.length >= 4 ? Double.parseDouble(args[3]) : 1.0;
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
            coordinateDivider = 1.0;
            if (algorithm.equals("regular")) {
                final int frameMinWidth, frameMaxWidth;
                if (rectanglesJson.has("frameWidth")) {
                    frameMinWidth = frameMaxWidth = rectanglesJson.getInt("frameWidth");
                } else {
                    frameMinWidth = rectanglesJson.getInt("frameMinWidth");
                    frameMaxWidth = rectanglesJson.getInt("frameMaxWidth");
                }
                final int frameMinHeight, frameMaxHeight;
                if (rectanglesJson.has("frameHeight")) {
                    frameMinHeight = frameMaxHeight = rectanglesJson.getInt("frameHeight");
                } else {
                    frameMinHeight = rectanglesJson.getInt("frameMinHeight");
                    frameMaxHeight = rectanglesJson.getInt("frameMaxHeight");
                }
                if (frameMinWidth > frameMaxWidth) {
                    throw new JSONException("frameMinWidth > frameMaxHeight");
                }
                if (frameMinHeight > frameMaxHeight) {
                    throw new JSONException("frameMinHeight > frameMaxHeight");
                }
                if (rectanglesJson.has("imageWidth")) {
                    imageWidth = rectanglesJson.getLong("imageWidth");
                }
                if (rectanglesJson.has("imageHeight")) {
                    imageHeight = rectanglesJson.getLong("imageHeight");
                }
                final int horizontalCount = rectanglesJson.getInt("horizontalCount");
                final int verticalCount = rectanglesJson.getInt("verticalCount");
                final int overlap = rectanglesJson.getInt("overlap");
                final int maxError = rectanglesJson.getInt("maxError");
                final Random rnd = new Random(rectanglesJson.optLong("randSeed", new Random().nextLong()));
                for (int i = 0; i < verticalCount; i++) {
                    for (int j = 0; j < horizontalCount; j++) {
                        final int frameWidth = frameMinWidth + rnd.nextInt(frameMaxWidth - frameMinWidth + 1);
                        final int frameHeight = frameMinHeight + rnd.nextInt(frameMaxHeight - frameMinHeight + 1);
                        final long x = (j + 1) * (frameWidth - overlap) + rnd.nextInt(maxError + 1) - maxError / 2;
                        final long y = (i + 1) * (frameHeight - overlap) + rnd.nextInt(maxError + 1) - maxError / 2;
                        final IRectangularArea r = IRectangularArea.valueOf(
                            x, y, x + frameWidth - 1, y + frameHeight - 1);
                        if (rectangles.size() < 10) {
                            System.out.printf("Frame #%d %dx%d: %s%n", rectangles.size() + 1, r.size(0), r.size(1), r);
                        } else if (rectangles.size() == 10) {
                            System.out.println("...");
                        }
                        rectangles.add(r);
                    }
                }
            } else {
                throw new JSONException("Unknown generating algorithm " + algorithm);
            }
        } else {
            throw new JSONException("JSON file \"" + rectanglesFile
                + "\" must contain either the list of rectangles or description of the generating algorithm");
        }
        RectangleSet rectangleSet = RectangleSet.newInstance(rectangles);
        if (imageWidth == null) {
            imageWidth = rectangleSet.circumscribedRectangle().max(0) + 100;
        }
        if (imageHeight == null) {
            imageHeight = rectangleSet.circumscribedRectangle().max(1) + 100;
        }
        List<Matrix<? extends UpdatablePArray>> demo = newImage(imageWidth, imageHeight);
        draw(demo, rectangleSet.circumscribedRectangle(), coordinateDivider, Color.YELLOW, Color.BLUE);
        for (IRectangularArea area : rectangles) {
            draw(demo, area, coordinateDivider, Color.LIGHT_GRAY, Color.DARK_GRAY);
        }
        final File sourceFile = new File(demoFolder, rectanglesFile.getName() + ".source.bmp");
        System.out.printf("Writing source image %dx%d into %s: %d rectangles%n",
            imageWidth, imageHeight, sourceFile, rectangles.size());
        ExternalAlgorithmCaller.writeImage(sourceFile, demo);

        for (int testIndex = 0; testIndex < numberOfTests; testIndex++) {
            System.out.printf("%nTest #%d%n", testIndex + 1);
            rectangleSet = RectangleSet.newInstance(rectangles);
            rectangleSet.findConnectedComponents();
            for (int k = 0; k < Math.min(10, rectangleSet.connectedComponentCount()); k++) {
                demo = newImage(imageWidth, imageHeight);
                final RectangleSet connectedSet = rectangleSet.connectedComponent(k);
                connectedSet.findBoundaries();
                if (testIndex == 0) {
                    for (RectangleSet.Frame frame : connectedSet.frames()) {
                        draw(demo, frame.rectangle(), coordinateDivider, Color.WHITE, Color.DARK_GRAY);
                    }
                    for (RectangleSet.Side side : connectedSet.horizontalSides()) {
                        for (RectangleSet.BoundaryLink link : side.containedBoundaryLinks()) {
                            draw(demo, link.sidePart(), coordinateDivider, Color.GREEN, Color.BLACK);
                        }
                    }
                    for (RectangleSet.Side side : connectedSet.verticalSides()) {
                        for (RectangleSet.BoundaryLink link : side.containedBoundaryLinks()) {
                            draw(demo, link.sidePart(), coordinateDivider, Color.YELLOW, Color.BLACK);
                        }
                    }
                    final File f = new File(demoFolder, rectanglesFile.getName() + ".component" + (k + 1) + ".bmp");
                    System.out.printf("Writing component #%d into %s: %s%n%n", k + 1, f, connectedSet);
                    ExternalAlgorithmCaller.writeImage(f, demo);
                }
            }
        }
    }

    private static List<Matrix<? extends UpdatablePArray>> newImage(long width, long height) {
        ArrayList<Matrix<? extends UpdatablePArray>> result = new ArrayList<Matrix<? extends UpdatablePArray>>();
        result.add(Arrays.SMM.newByteMatrix(width, height));
        result.add(Arrays.SMM.newByteMatrix(width, height));
        result.add(Arrays.SMM.newByteMatrix(width, height));
        return result;
    }

    private static void draw(
        List<Matrix<? extends UpdatablePArray>> demo,
        IRectangularArea area,
        double coordinateDivider,
        Color borderColor,
        Color innerColor)
    {
        final IRectangularArea divided = IRectangularArea.valueOf(
            area.min().multiply(1.0 / coordinateDivider),
            area.max().multiply(1.0 / coordinateDivider));
        for (int k = 0; k < demo.size(); k++) {
            int borderValue = k == 0 ? borderColor.getRed() : k == 1 ? borderColor.getGreen() : borderColor.getBlue();
            int innerValue = k == 0 ? innerColor.getRed() : k == 1 ? innerColor.getGreen() : innerColor.getBlue();
            demo.get(k).subMatrix(divided, Matrix.ContinuationMode.NULL_CONSTANT).array().fill(borderValue);
            if (divided.size(0) > 2 && divided.size(1) > 2) {
                final IRectangularArea inner = IRectangularArea.valueOf(
                    divided.min().addToAllCoordinates(1),
                    divided.max().addToAllCoordinates(-1));
                demo.get(k).subMatrix(inner, Matrix.ContinuationMode.NULL_CONSTANT).array().fill(innerValue);
            }
        }
    }
}
