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

        private Side(Frame frame, boolean first) {
            this.frame = frame;
            this.first = first;
        }


        public Frame frame() {
            return frame;
        }

        public abstract long coord();

        @Override
        public int compareTo(Side o) {
            final long thisCoord = coord();
            final long otherCoord = o.coord();
            return thisCoord < otherCoord ? -1 : thisCoord > otherCoord ? 1 : 0;
        }
    }

    private static class VerticalSide extends Side {
        private VerticalSide(Frame frame, boolean first) {
            super(frame, first);
        }

        @Override
        public long coord() {
            return first ? frame.minX : frame.maxX;
        }
    }

    private static class HorizontalSide extends Side {
        private HorizontalSide(Frame frame, boolean first) {
            super(frame, first);
        }

        @Override
        public long coord() {
            return first ? frame.minY : frame.maxY;
        }
    }

    private final List<Frame> frames;
    private final List<HorizontalSide> horizontalSides = new ArrayList<HorizontalSide>();
    private final List<VerticalSide> verticalSides = new ArrayList<VerticalSide>();
    private final List<List<Frame>> connectedComponents = new ArrayList<List<Frame>>();
    private final long[] allX;
    private final long[] allY;
    // - allX/allY provides little optimization and simplification
    //TODO!! remove horizontalSides/allY ?

    private RectangleSet(List<Frame> frames, boolean callForExctractingConnectedComponent) {
        this.frames = frames;
        long t1 = System.nanoTime();
        fillSideLists();
        this.allX = new long[verticalSides.size()];
        for (int k = 0; k < allX.length; k++) {
            allX[k] = verticalSides.get(k).coord();
        }
        this.allY = new long[horizontalSides.size()];
        for (int k = 0; k < allY.length; k++) {
            allY[k] = horizontalSides.get(k).coord();
        }
        long t2 = System.nanoTime();
        if (callForExctractingConnectedComponent) {
            connectedComponents.add(frames);
        } else {
            findConnectedComponents();
        }
        long t3 = System.nanoTime();
        AbstractPlanePyramidSource.debug(2, "%s %d rectangle set into %d connected components: "
            + "%.3f ms preprocess, %.3f ms scanning (%.3f mcs / rectangle)%n",
            callForExctractingConnectedComponent ? "Saving" : "Splitting",
            frames.size(), connectedComponents.size(),
            (t2 - t1) * 1e-6, (t3 - t2) * 1e-6, (t3 - t1) * 1e-3 / (double) frames.size() );
    }

    public static RectangleSet newInstance(Collection<IRectangularArea> rectangles) {
        return new RectangleSet(checkAndConvertToFrames(rectangles), false);
    }

    public List<Frame> frames() {
        return Collections.unmodifiableList(frames);
    }

    public int connectedComponentCount() {
        return connectedComponents.size();
    }

    public RectangleSet connectedComponent(int index) {
        return new RectangleSet(connectedComponents.get(index), true);
        // it is better to recalculate base optimization data like sides for the given component:
        // it will optimize their usage (data from other components will not be used)
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
            this.connectedComponents.add(component);
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

