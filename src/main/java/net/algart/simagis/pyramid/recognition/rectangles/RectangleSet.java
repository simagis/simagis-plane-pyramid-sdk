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
import net.algart.simagis.pyramid.AbstractPlanePyramidSource;

import java.util.*;

public class RectangleSet {
    public static class Frame {
        private final IRectangularArea rectangle;
        private final RectangleSet.HorizontalSide lessHorizontalSide;
        private final RectangleSet.HorizontalSide higherHorizontalSide;
        private final RectangleSet.VerticalSide lessVerticalSide;
        private final RectangleSet.VerticalSide higherVerticalSide;
        private final long minX;
        private final long maxX;
        private final long minY;
        private final long maxY;
        private final int index;

        private Frame(IRectangularArea rectangle, int index) {
            assert rectangle != null;
            this.rectangle = rectangle;
            this.lessHorizontalSide = new RectangleSet.HorizontalSide(this, true);
            this.higherHorizontalSide = new RectangleSet.HorizontalSide(this, false);
            this.lessVerticalSide = new RectangleSet.VerticalSide(this, true);
            this.higherVerticalSide = new RectangleSet.VerticalSide(this, false);
            this.minX = rectangle.min(0);
            this.maxX = rectangle.max(0);
            this.minY = rectangle.min(1);
            this.maxY = rectangle.max(1);
            this.index = index;
        }

        public IRectangularArea rectangle() {
            return rectangle;
        }
    }

    public static abstract class Side implements Comparable<Side> {
        final Frame frame;
        final boolean first;
        final List<BoundaryLink> containedBoundaryLinks =
            new ArrayList<BoundaryLink>();

        private Side(Frame frame, boolean first) {
            assert frame != null;
            this.frame = frame;
            this.first = first;
        }


        public Frame frame() {
            return frame;
        }

        public boolean isFirstOfTwoParallelSides() {
            return first;
        }

        public abstract boolean isHorizontal();

        public abstract long frameSideCoord();

        public long boundCoordPlusHalf() {
            return first ? frameSideCoord() : frameSideCoord() + 1;
        }

        public abstract long boundFromPlusHalf();

        public abstract long boundToPlusHalf();

        @Override
        public int compareTo(Side o) {
            final long thisCoord = frameSideCoord();
            final long otherCoord = o.frameSideCoord();
            return thisCoord < otherCoord ? -1 : thisCoord > otherCoord ? 1 : 0;
        }
    }

    static class HorizontalSide extends Side {
        private HorizontalSide(Frame frame, boolean first) {
            super(frame, first);
        }

        @Override
        public boolean isHorizontal() {
            return true;
        }

        @Override
        public long frameSideCoord() {
            return first ? frame.minY : frame.maxY;
        }

        @Override
        public long boundFromPlusHalf() {
            return frame.minX;
        }

        @Override
        public long boundToPlusHalf() {
            return frame.maxX + 1;
        }
    }

    static class VerticalSide extends Side {
        private VerticalSide(Frame frame, boolean first) {
            super(frame, first);
        }

        @Override
        public boolean isHorizontal() {
            return false;
        }

        @Override
        public long frameSideCoord() {
            return first ? frame.minX : frame.maxX;
        }

        @Override
        public long boundFromPlusHalf() {
            return frame.minY;
        }

        @Override
        public long boundToPlusHalf() {
            return frame.maxY + 1;
        }
    }

    public static abstract class BoundaryLink {
        final Side containingSide;
        final Side firstTransveralSide;
        final Side secondTransveralSide;
        final long from;
        final long to;

        private BoundaryLink(
            Side containingSide,
            Side firstTransveralSide,
            Side secondTransveralSide,
            long from,
            long to)
        {
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

    public static class HorizontalBoundaryLink extends BoundaryLink {
        private HorizontalBoundaryLink(
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

    public static class VerticalBoundaryLink extends BoundaryLink {
        private VerticalBoundaryLink(
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


    private final List<Frame> frames;

    private volatile List<HorizontalSide> horizontalSides = null;
    private volatile List<VerticalSide> verticalSides = null;
    private volatile List<List<Frame>> connectedComponents = null;
    private final Object lock = new Object();

    RectangleSet(List<Frame> frames) {
        this.frames = frames;
    }

    public static RectangleSet newInstance(Collection<IRectangularArea> rectangles) {
        return new RectangleSet(checkAndConvertToFrames(rectangles));
    }

    public List<Frame> frames() {
        return Collections.unmodifiableList(frames);
    }


    public int connectedComponentCount() {
        findConnectedComponents();
        synchronized (lock) {
            return connectedComponents.size();
        }
    }

    public RectangleSet connectedComponent(int index) {
        findConnectedComponents();
        final List<Frame> resultFrames;
        synchronized (lock) {
            resultFrames = connectedComponents.get(index);
        }
        final RectangleSet result = new RectangleSet(resultFrames);
        result.connectedComponents = Collections.singletonList(resultFrames);
        return result;
    }

    public void findConnectedComponents() {
        fillSideLists();
        synchronized (lock) {
            if (this.connectedComponents != null) {
                return;
            }
        }
        long t1 = System.nanoTime();
        List<List<Frame>> connectedComponents = doFindConnectedComponents();
        long t2 = System.nanoTime();
        synchronized (lock) {
            this.connectedComponents = connectedComponents;
        }
        AbstractPlanePyramidSource.debug(2, "Rectangle set (%d rectangles), finding %d connected components: "
                + "%.3f ms (%.3f mcs / rectangle)%n",
            frames.size(), connectedComponents.size(),
            (t2 - t1) * 1e-6, (t2 - t1) * 1e-3 / (double) frames.size());
    }

    @Override
    public String toString() {
        synchronized (lock) {
            return "set of " + frames.size() + " rectangles"
                + (connectedComponents == null ? "" : ", " + connectedComponents.size() + " connected components");
        }
    }

    private void fillSideLists() {
        synchronized (lock) {
            if (this.horizontalSides != null) {
                return;
            }
        }
        long t1 = System.nanoTime();
        final List<HorizontalSide> horizontalSides = new ArrayList<HorizontalSide>();
        final List<VerticalSide> verticalSides = new ArrayList<VerticalSide>();
        for (Frame frame : frames) {
            horizontalSides.add(frame.lessHorizontalSide);
            horizontalSides.add(frame.higherHorizontalSide);
            verticalSides.add(frame.lessVerticalSide);
            verticalSides.add(frame.higherVerticalSide);
        }
        Collections.sort(horizontalSides);
        Collections.sort(verticalSides);
        long t2 = System.nanoTime();
        synchronized (lock) {
            this.horizontalSides = horizontalSides;
            this.verticalSides = verticalSides;
        }
        AbstractPlanePyramidSource.debug(2, "Rectangle set (%d rectangles), sorting sides: %.3f ms%n",
            frames.size(), (t2 - t1) * 1e-6);
    }

    private List<List<Frame>> doFindConnectedComponents() {
        final List<List<Frame>> result = new ArrayList<List<Frame>>();
        final long[] allX = new long[verticalSides.size()];
        for (int k = 0; k < allX.length; k++) {
            allX[k] = verticalSides.get(k).frameSideCoord();
        }
        final boolean[] frameVisited = new boolean[frames.size()];
        final boolean[] added = new boolean[frames.size()];
        // - arrays are filled by false by Java
        final Queue<Frame> queue = new LinkedList<Frame>();
        final List<Frame> neighbours = new ArrayList<Frame>();
        int index = 0;
        for (; ; ) {
            while (index < frameVisited.length && frameVisited[index]) {
                index++;
            }
            if (index >= frameVisited.length) {
                break;
            }
            final List<Frame> component = new ArrayList<Frame>();
            // Breadth-first search:
            queue.add(frames.get(index));
            frameVisited[index] = true;
            while (!queue.isEmpty()) {
                final Frame frame = queue.poll();
                component.add(frame);
                findIncidentFrames(neighbours, frame, allX, added);
                for (Frame neighbour : neighbours) {
                    if (!frameVisited[neighbour.index]) {
                        queue.add(neighbour);
                        frameVisited[neighbour.index] = true;
                    }
                }
            }
            result.add(component);
        }
        return result;
    }

    private void findIncidentFrames(List<Frame> result, Frame frame, long[] allX, boolean added[]) {
        result.clear();
        int left = Arrays.binarySearch(allX, frame.minX);
        assert left >= 0;
        // - we should find at least this frame itself
        assert allX[left] == frame.minX;
        while (left > 0 && allX[left - 1] == frame.minX) {
            left--;
        }
        int right = Arrays.binarySearch(allX, frame.maxX);
        assert right >= 0;
        // - we should find at least this frame itself
        assert allX[right] == frame.maxX;
        while (right + 1 < allX.length && allX[right + 1] == frame.maxX) {
            right++;
        }
        for (int k = left; k <= right; k++) {
            final Frame other = verticalSides.get(k).frame;
            assert other.maxX >= frame.minX && other.minX <= frame.maxX : "Binary search in allX failed";
            if (other.maxY < frame.minY || other.minY > frame.maxY) {
                continue;
            }
            if (other == frame) {
                continue;
            }
            // they intersects!
            if (!added[other.index]) {
                result.add(other);
                added[other.index] = true;
                // this flag is necessary to avoid adding twice (for left and right sides)
            }
        }
        for (Frame other : result) {
            added[other.index] = false;
        }
    }

    private static List<Frame> checkAndConvertToFrames(Collection<IRectangularArea> rectangles) {
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
        List<Frame> frames = new ArrayList<Frame>();
        int index = 0;
        for (IRectangularArea rectangle : rectangles) {
            frames.add(new Frame(rectangle, index++));
        }
        return frames;
    }
}

