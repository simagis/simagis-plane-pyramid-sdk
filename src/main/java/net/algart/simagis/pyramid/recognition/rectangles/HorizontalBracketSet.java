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

import java.util.*;

// This class should be used in a single thread.
class HorizontalBracketSet {
    private static final boolean DEBUG_MODE = true;

    private final List<RectangleSet.HorizontalSide> allHorizontalSides;
    private final List<RectangleSet.VerticalSide> allVerticalSides;
    // - for debug needs
    private final int numberOfHorizontals;
    int horizontalIndex;
    RectangleSet.HorizontalSide horizontal;
    private long currentY;
    private NavigableSet<RectangleSet.Bracket> intersectingSides = new TreeSet<RectangleSet.Bracket>();

    HorizontalBracketSet(
        List<RectangleSet.HorizontalSide> allHorizontalSides,
        List<RectangleSet.VerticalSide> allVerticalSides)
    {
        assert allHorizontalSides != null;
        assert allVerticalSides != null;
        assert !allHorizontalSides.isEmpty();
        assert !allVerticalSides.isEmpty();
        // - checked in the calling method to simplify the logic
        this.allHorizontalSides = allHorizontalSides;
        this.allVerticalSides = allVerticalSides;
        this.numberOfHorizontals = allHorizontalSides.size();
        this.horizontalIndex = -1;
        this.horizontal = null;
        if (DEBUG_MODE) {
            for (int k = 1, n = allHorizontalSides.size(); k < n; k++) {
                assert allHorizontalSides.get(k).boundCoord() >= allHorizontalSides.get(k - 1).boundCoord();
            }
            for (int k = 1, n = allVerticalSides.size(); k < n; k++) {
                assert allVerticalSides.get(k).boundCoord() >= allVerticalSides.get(k - 1).boundCoord();
            }
        }
    }

    boolean next() {
        if (horizontalIndex == numberOfHorizontals) {
            throw new IllegalArgumentException(getClass() + " should not be used more");
        }
        assert horizontalIndex < numberOfHorizontals;
        final RectangleSet.HorizontalSide newHorizontal = horizontalIndex + 1 < numberOfHorizontals ?
            allHorizontalSides.get(horizontalIndex + 1) :
            null;
        final long newY = newHorizontal == null ? -157 : newHorizontal.boundCoord();
        if (horizontal == null || newHorizontal == null
            || newHorizontal.boundCoord() != horizontal.boundCoord())
        {
            if (horizontal != null) {
                int index = horizontalIndex;
                while (index > 0 && allHorizontalSides.get(--index).boundCoord() == currentY) {
                    ;
                }
                while (index <= horizontalIndex) {
                    RectangleSet.HorizontalSide h = allHorizontalSides.get(index);
                    if (!h.isFirstOfTwoParallelSides()) {
                        removeHorizontal(h);
                    }
                    index++;
                }
            }
            if (newHorizontal != null) {
                int index = horizontalIndex + 1;
                RectangleSet.HorizontalSide h;
                while (index < numberOfHorizontals && (h = allHorizontalSides.get(index)).boundCoord() == newY) {
                    if (h.isFirstOfTwoParallelSides()) {
                        addHorizontal(h);
                    }
                    index++;
                }
            }
        }
        horizontalIndex++;
        horizontal = newHorizontal;
        currentY = newY;
        if (DEBUG_MODE) {
            System.out.printf("  Horizontal #%d, y=%d: brackets %s%n", horizontalIndex, currentY, intersectingSides);
        }
        return horizontal != null;
    }

    Collection<RectangleSet.Bracket> currentIntersections() {
        final RectangleSet.Bracket bracketFrom = new RectangleSet.Bracket(horizontal, true);
        final RectangleSet.Bracket bracketTo = new RectangleSet.Bracket(horizontal, false);
        if (DEBUG_MODE) {
            assert intersectingSides.contains(bracketFrom);
            assert intersectingSides.contains(bracketTo);
        }
        final NavigableSet<RectangleSet.Bracket> result = intersectingSides.subSet(bracketFrom, true, bracketTo, true);
        if (DEBUG_MODE) {
            System.out.printf("  Intersections with %s: brackets %s%n", horizontal, result);
        }
        return result;
    }

    private void addHorizontal(RectangleSet.HorizontalSide h) {
        final RectangleSet.Bracket bracketFrom = new RectangleSet.Bracket(h, true);
        final RectangleSet.Bracket bracketTo = new RectangleSet.Bracket(h, false);
        // Note: theoretically it could be faster not to allocate brackets here,
        // but create them together with Side instance and store there as its fields.
        // But it is a bad idea, because can lead to problems with multithreading when
        // two threads modify Bracket.rightNestingDepth fields.
        // In any case, this solution requires comparable time for allocation
        // while the single pass of the scanning by the horizontal.
        final RectangleSet.Bracket previousBracket = intersectingSides.lower(bracketFrom);
        int nesting = previousBracket == null ? 1 : previousBracket.followingNestingDepth + 1;
        bracketFrom.followingNestingDepth = nesting;
        for (RectangleSet.Bracket bracket : intersectingSides.subSet(bracketFrom, false, bracketTo, false)) {
            // It is not the ideal O(log N) algorithm, but close to it, because this subset is usually very little
            nesting = ++bracket.followingNestingDepth;
        }
        bracketTo.followingNestingDepth = nesting - 1;
        intersectingSides.add(bracketTo);
        intersectingSides.add(bracketFrom);
    }

    private void removeHorizontal(RectangleSet.HorizontalSide h) {
        final RectangleSet.Bracket bracketFrom = new RectangleSet.Bracket(h, true);
        final RectangleSet.Bracket bracketTo = new RectangleSet.Bracket(h, false);
        for (RectangleSet.Bracket bracket : intersectingSides.subSet(bracketFrom, false, bracketTo, false)) {
            --bracket.followingNestingDepth;
        }
        final boolean containedFrom = intersectingSides.remove(bracketFrom);
        final boolean containedTo = intersectingSides.remove(bracketTo);
        assert containedFrom;
        assert containedTo;
    }

}
