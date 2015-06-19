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

        private boolean findIncidentFramesFlag = false;

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
        final List<BoundedRectangleSet.Link> containedBoundaryLinks =
            new ArrayList<BoundedRectangleSet.Link>();

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

    private final List<Frame> frames;
    private final List<HorizontalSide> horizontalSides = new ArrayList<HorizontalSide>();
    private final List<VerticalSide> verticalSides = new ArrayList<VerticalSide>();
    private final List<List<Frame>> framesOfConnectedComponents = new ArrayList<List<Frame>>();
    private final long[] allX;
    private final long[] allY;
    // - allX/allY provides little optimization and simplification
    //TODO!! remove horizontalSides/allY ?
    private final BoundedRectangleSet[] connectedComponents;

    RectangleSet(List<Frame> frames, boolean callForExctractingConnectedComponent) {
        this.frames = frames;
        long t1 = System.nanoTime();
        fillSideLists();
        this.allX = new long[verticalSides.size()];
        for (int k = 0; k < allX.length; k++) {
            allX[k] = verticalSides.get(k).frameSideCoord();
        }
        this.allY = new long[horizontalSides.size()];
        for (int k = 0; k < allY.length; k++) {
            allY[k] = horizontalSides.get(k).frameSideCoord();
        }
        long t2 = System.nanoTime();
        if (callForExctractingConnectedComponent) {
            framesOfConnectedComponents.add(frames);
        } else {
            findConnectedComponents();
        }
        this.connectedComponents = new BoundedRectangleSet[framesOfConnectedComponents.size()];
        // - filled by null by Java
        long t3 = System.nanoTime();
        AbstractPlanePyramidSource.debug(2, "%s %d rectangle set into %d connected components: "
            + "%.3f ms preprocess, %.3f ms scanning (%.3f mcs / rectangle)%n",
            callForExctractingConnectedComponent ? "Saving" : "Splitting",
            frames.size(), framesOfConnectedComponents.size(),
            (t2 - t1) * 1e-6, (t3 - t2) * 1e-6, (t3 - t1) * 1e-3 / (double) frames.size() );
    }

    public static RectangleSet newInstance(Collection<IRectangularArea> rectangles) {
        return new RectangleSet(checkAndConvertToFrames(rectangles), false);
    }

    public List<Frame> frames() {
        return Collections.unmodifiableList(frames);
    }

    public int connectedComponentCount() {
        return framesOfConnectedComponents.size();
    }

    public BoundedRectangleSet connectedComponent(int index) {
        BoundedRectangleSet result;
        synchronized (connectedComponents) {
            result = connectedComponents[index];
            if (result != null) {
                return result;
            }
        }
        result = new BoundedRectangleSet(framesOfConnectedComponents.get(index), true);
        // it is better to recalculate base optimization data like sides for the given component:
        // it will optimize their usage (data from other components will not be used)
        synchronized (connectedComponents) {
            connectedComponents[index] = result;
            return result;
        }
    }

    private void fillSideLists() {
        for (Frame frame : frames) {
            horizontalSides.add(frame.lessHorizontalSide);
            horizontalSides.add(frame.higherHorizontalSide);
            verticalSides.add(frame.lessVerticalSide);
            verticalSides.add(frame.higherVerticalSide);
        }
        Collections.sort(horizontalSides);
        Collections.sort(verticalSides);
    }

    private void findConnectedComponents() {
        if (frames.isEmpty()) {
            return;
        }
        final boolean[] frameVisited = new boolean[frames.size()];
        final Queue<Frame> queue = new LinkedList<Frame>();
        final List<Frame> neighbours = new ArrayList<Frame>();
        int index = 0;
        for (;;) {
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
                findIncidentFrames(frame, neighbours);
                for (Frame neighbour : neighbours) {
                    if (!frameVisited[neighbour.index]) {
                        queue.add(neighbour);
                        frameVisited[neighbour.index] = true;
                    }
                }
            }
            this.framesOfConnectedComponents.add(component);
        }
    }

    private void findIncidentFrames(Frame frame, List<Frame> result) {
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
            if (!other.findIncidentFramesFlag) {
                result.add(other);
                other.findIncidentFramesFlag = true;
                // the flag is necessary to avoid adding twice
            }
        }
        for (Frame other : result) {
            other.findIncidentFramesFlag = false;
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

