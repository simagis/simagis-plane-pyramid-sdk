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

import net.algart.math.IPoint;
import net.algart.math.IRectangularArea;

import java.util.List;

public class BoundedRectangleSet extends RectangleSet {
    // Note. We could make the boundary as an internal property of every RectangleSet, calculated
    // on demand by some method line "getBoundary". However, it involves modification of the structure
    // of frame sides (Side class) - adding there the list of boundary links. This operation is not thread-safe,
    // and in this case we need either to use synchronization, or to specify that the class is not thread-safe.
    // Unlike this, in our implementation all calculations are performed in constructors,
    // that provides thread safety automatically.

    public static abstract class Link {
        final Side containingSide;
        final Side firstTransveralSide;
        final Side secondTransveralSide;
        final long from;
        final long to;

        private Link(Side containingSide, Side firstTransveralSide, Side secondTransveralSide, long from, long to) {
            assert containingSide != null && firstTransveralSide != null && secondTransveralSide != null;
            assert from >= containingSide.boundFromPlusHalf();
            assert to <= containingSide.boundToPlusHalf();
            assert from < to;
            this.containingSide = containingSide;
            this.firstTransveralSide = firstTransveralSide;
            this.secondTransveralSide = secondTransveralSide;
            this.from = from;
            this.to = to;
            containingSide.containedBoundaryLinks.add(this);
        }

        public Side containingSide() {
            return containingSide;
        }

        /**
         * Returns the starting coordinate of this boundary element (link) along the coordinate axis,
         * to which this link is parallel, increased by 0.5
         * (the bounrady always has half-integer coordinates).
         *
         * @return the starting coordinate of this link + 0.5
         */
        public long from() {
            return from;
        }

        /**
         * Returns the ending coordinate of this boundary element (link) along the coordinate axis,
         * to which this link is parallel, increased by 0.5
         * (the bounrady always has half-integer coordinates).
         *
         * @return the ending coordinate of this link + 0.5
         */
        public long to() {
            return to;
        }

        /**
         * Returns the coordinate of this boundary element (link) along the coordinate axis,
         * to which this link is perpendicular, increased by 0.5
         * (the bounrady always has half-integer coordinates).
         *
         * @return the perpendicular coordinate of this link + 0.5
         */
        public long transversal() {
            return containingSide.boundCoordPlusHalf();
        }

        public abstract IRectangularArea sidePart();
    }

    public static class HorizontalLink extends Link {
        private HorizontalLink(
            HorizontalSide containedSide,
            VerticalSide firstTransveralSide,
            VerticalSide secondTransveralSide,
            long from,
            long to)
        {
            super(containedSide, firstTransveralSide, secondTransveralSide, from, to);
        }

        @Override
        public IRectangularArea sidePart() {
            return IRectangularArea.valueOf(
                IPoint.valueOf(from, containingSide.frameSideCoord()),
                IPoint.valueOf(to - 1, containingSide.frameSideCoord()));
        }
    }

    public static class VerticalLink extends Link {
        private VerticalLink(
            VerticalSide containedSide,
            HorizontalSide firstTransveralSide,
            HorizontalSide secondTransveralSide,
            long from,
            long to) {
            super(containedSide, firstTransveralSide, secondTransveralSide, from, to);
        }

        @Override
        public IRectangularArea sidePart() {
            return IRectangularArea.valueOf(
                IPoint.valueOf(containingSide.frameSideCoord(), from),
                IPoint.valueOf(containingSide.frameSideCoord(), to - 1));
        }
    }

    BoundedRectangleSet(List<Frame> frames, boolean callForExctractingConnectedComponent) {
        super(frames, callForExctractingConnectedComponent);
        buildBounrady();
    }

    private void buildBounrady() {
        //TODO!!
    }
}
