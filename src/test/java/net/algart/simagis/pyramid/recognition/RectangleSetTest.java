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
        demoFolder.mkdirs();
        final JSONObject rectanglesJson = new JSONObject(ExternalProcessor.readUTF8(rectanglesFile));
        final JSONArray rectanglesArray = rectanglesJson.has("origins") ?
            rectanglesJson.getJSONArray("origins") :
            rectanglesJson.getJSONArray("rectangles");
        final List<IRectangularArea> rectangles = new ArrayList<IRectangularArea>();
        for (int k = 0, n = rectanglesArray.length(); k < n; k++) {
            final JSONObject rectangleJson = rectanglesArray.getJSONObject(k);
            rectangles.add(IRectangularArea.valueOf(
                IPoint.valueOf(
                    Math.round(rectangleJson.getInt("minX") / coordinateDivider),
                    Math.round(rectangleJson.getInt("minY") / coordinateDivider)),
                IPoint.valueOf(
                    Math.round(rectangleJson.getInt("maxX") / coordinateDivider),
                    Math.round(rectangleJson.getInt("maxY") / coordinateDivider))));
        }
        Matrix<? extends UpdatablePArray> demo = Arrays.SMM.newByteMatrix(width, height);
        for (IRectangularArea area : rectangles) {
            demo.subMatrix(area, Matrix.ContinuationMode.NULL_CONSTANT).array().fill(200);
        }
        ExternalAlgorithmCaller.writeImage(
            new File(demoFolder, rectanglesFile.getName() + ".source.png"),
            Collections.singletonList(demo));
        RectangleSet rectangleSet = null;
        for (int testIndex = 0; testIndex < numberOfTests; testIndex++) {
            System.out.printf("Test #%d%n", testIndex);
            rectangleSet = RectangleSet.newInstance(rectangles);
        }
        for (int k = 0; k < rectangleSet.connectedComponentCount(); k++) {
            demo = Arrays.SMM.newByteMatrix(width, height);
            final RectangleSet connectedSet = rectangleSet.connectedComponent(k);
            for (RectangleSet.Frame frame : connectedSet.frames()) {
                demo.subMatrix(frame.rectangle(), Matrix.ContinuationMode.NULL_CONSTANT).array().fill(255);
            }
            ExternalAlgorithmCaller.writeImage(
                new File(demoFolder, rectanglesFile.getName() + ".component" + k + ".png"),
                Collections.singletonList(demo));
        }
    }
}
