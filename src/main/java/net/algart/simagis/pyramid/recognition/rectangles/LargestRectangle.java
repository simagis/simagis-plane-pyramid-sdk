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

package net.algart.simagis.pyramid.recognition.rectangles;

import net.algart.math.IRectangularArea;

public abstract class LargestRectangle {

    final RectangleSet rectangleSet;
    final int connectedComponentIndex;

    LargestRectangle(RectangleSet rectangleSet, int connectedComponentIndex) {
        if (rectangleSet == null) {
            throw new NullPointerException("Null rectangleSet");
        }
        if (connectedComponentIndex < 0 || connectedComponentIndex >= rectangleSet.connectedComponentCount()) {
            throw new IllegalArgumentException("Illegal connected component index " + connectedComponentIndex);
        }
        this.rectangleSet = rectangleSet;
        this.connectedComponentIndex = connectedComponentIndex;
    }

    public LargestRectangle newInstanceWithPossibleHoles(RectangleSet rectangleSet, int connectedComponentIndex) {
        return new LargestRectangleWithPossibleHoles(rectangleSet, connectedComponentIndex);
    }

    public abstract IRectangularArea result();

    public boolean resultContainsHole() {
        return false;
    }
}