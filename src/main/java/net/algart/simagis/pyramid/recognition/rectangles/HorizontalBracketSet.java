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

import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

class HorizontalBracketSet {
    private final List<RectangleSet.VerticalSide> allVerticalSides;
    private final SortedSet<Bracket> intersectingSides = new TreeSet<Bracket>();

    HorizontalBracketSet(
        List<RectangleSet.VerticalSide> allVerticalSides,
        RectangleSet.HorizontalSide firstHorizontal)
    {
        assert allVerticalSides != null;
        assert !allVerticalSides.isEmpty();
        // - checked in the calling method to simplify the logic
        this.allVerticalSides = allVerticalSides;
        intersectingSides.add(new Bracket(firstHorizontal.frame.lessVerticalSide, 1));
        intersectingSides.add(new Bracket(firstHorizontal.frame.higherVerticalSide, 0));
    }

    void next(
        RectangleSet.HorizontalSide currentHorizontal,
        RectangleSet.HorizontalSide nextHorizontal)
    {
        //TODO!!
    }

    void currentIntersections(List<RectangleSet.Frame> result) {
        //TODO!!
    }

    private static class Bracket implements Comparable<Bracket> {
        private final RectangleSet.VerticalSide verticalSide;
        private int rightNestingDepth;

        public Bracket(RectangleSet.VerticalSide verticalSide, int rightNestingDepth) {
            assert verticalSide != null;
            this.verticalSide = verticalSide;
            this.rightNestingDepth = rightNestingDepth;
        }

        @Override
        public int compareTo(Bracket o) {
            return this.verticalSide.compareTo(o.verticalSide);
        }
    }
}
