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

import net.algart.math.IRectangularArea;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class JoiningRectangles {
    private final List<IRectangularArea> source;
    private List<HorizontalSide> horizontalSides = null;
    private List<VerticalSide> verticalSides = null;
    private List<List<IRectangularArea>> connectedComponents = null;

    private JoiningRectangles(Collection<IRectangularArea> rectangles) {
        if (rectangles == null) {
            throw new NullPointerException("Null rectangles argument");
        }
        for (IRectangularArea rectangle : rectangles) {
            if (rectangle == null) {
                throw new NullPointerException("Null rectangle in a collection");
            }
            if (rectangle.coordCount() != 2) {
                throw new IllegalArgumentException("Only 2-dimensional rectangles can be joined");
            }
        }
        this.source = new ArrayList<IRectangularArea>(rectangles);
    }

    public JoiningRectangles getInstance(Collection<IRectangularArea> rectangles) {
        return new JoiningRectangles(rectangles);
    }

    public void findMaximalContainedRectangle() {
        preprocess();
        preprocessConnectedComponents();
        for (int k = 0, n = connectedComponents.size(); k < n; k++) {
            processConnectedComponent(k);
        }
    }

    public void preprocess() {
        //TODO!! find and sort horizontalSides and horizontalSides
    }

    public void preprocessConnectedComponents() {
        //TODO!!
    }

    public void processConnectedComponent(int componentIndex) {
        //TODO!!
    }

    private static abstract class Side {
        final IRectangularArea rectangle;
        final boolean first;

        Side(IRectangularArea rectangle, boolean first) {
            this.rectangle = rectangle;
            this.first = first;
        }


        abstract long coord();
    }

    private static class VerticalSide extends Side {
        VerticalSide(IRectangularArea rectangle, boolean first) {
            super(rectangle, first);
        }

        @Override
        long coord() {
            return first ? rectangle.min(0) : rectangle.max(0);
        }
    }

    private static class HorizontalSide extends Side {
        HorizontalSide(IRectangularArea rectangle, boolean first) {
            super(rectangle, first);
        }

        @Override
        long coord() {
            return first ? rectangle.min(1) : rectangle.max(1);
        }
    }
}
