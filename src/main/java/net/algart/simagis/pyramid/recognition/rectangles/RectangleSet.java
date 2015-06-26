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
import java.util.Arrays;

public class RectangleSet {
    static final int DEBUG_LEVEL = net.algart.arrays.Arrays.SystemSettings.getIntProperty(
        "net.algart.simagis.pyramid.recognition.rectangles.debugLevel", 0);

    public static class Frame {
        final RectangleSet.HorizontalSide lessHorizontalSide;
        final RectangleSet.HorizontalSide higherHorizontalSide;
        final RectangleSet.VerticalSide lessVerticalSide;
        final RectangleSet.VerticalSide higherVerticalSide;
        private final IRectangularArea rectangle;
        private final long fromX;
        private final long toX;
        private final long fromY;
        private final long toY;
        final int index;

        private Frame(IRectangularArea rectangle, int index) {
            assert rectangle != null;
            this.rectangle = rectangle;
            this.lessHorizontalSide = new RectangleSet.HorizontalSide(this, true);
            this.higherHorizontalSide = new RectangleSet.HorizontalSide(this, false);
            this.lessVerticalSide = new RectangleSet.VerticalSide(this, true);
            this.higherVerticalSide = new RectangleSet.VerticalSide(this, false);
            this.fromX = rectangle.min(0);
            this.toX = rectangle.max(0) + 1;
            this.fromY = rectangle.min(1);
            this.toY = rectangle.max(1) + 1;
            this.index = index;
        }

        public IRectangularArea rectangle() {
            return rectangle;
        }
    }

    public static abstract class Side implements Comparable<Side> {
        final Frame frame;
        final boolean first;
        int indexInSortedList = -1;
        List<? extends BoundaryLink> containedBoundaryLinks = null;

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

        public boolean isSecondOfTwoParallelSides() {
            return !first;
        }

        public abstract boolean isHorizontal();

        public abstract long frameSideCoord();

        /**
         * Returns the coordinate of this frame side along the coordinate axis,
         * to which this side is perpendicular, increased by 0.5
         * (the sides always have half-integer coordinates).
         *
         * @return the perpendicular coordinate of this side + 0.5
         */
        public abstract long boundCoord();

        /**
         * Returns the starting coordinate of this frame side along the coordinate axis,
         * to which this link is parallel, increased by 0.5
         * (the sides always have half-integer coordinates).
         *
         * @return the starting coordinate of this side + 0.5
         */
        public abstract long boundFrom();

        /**
         * Returns the ending coordinate of this frame side along the coordinate axis,
         * to which this link is parallel, increased by 0.5
         * (the sides always have half-integer coordinates).
         *
         * @return the ending coordinate of this side + 0.5
         */
        public abstract long boundTo();

        public List<BoundaryLink> containedBoundaryLinks() {
            return Collections.unmodifiableList(containedBoundaryLinks);
        }

        @Override
        public int compareTo(Side o) {
            final long thisCoord = this.boundCoord();
            final long otherCoord = o.boundCoord();
            if (thisCoord < otherCoord) {
                return -1;
            }
            if (thisCoord > otherCoord) {
                return 1;
            }
            // Lets we have two adjacent rectangles:
            // AAAAAAAAAAAA
            // AAAAAAAAAAAA
            // AAAAAAAAAAAA
            //      BBBBBBBBBBB
            //      BBBBBBBBBBB
            //      BBBBBBBBBBB
            // where the top side of B lies at the same horizontal as the bottom side of A.
            // The following checks provide the sorting order, when "opening" top side of B
            // will be BEFORE the "closing" bottom side of A:
            //      A top side (A start)
            //      B top side (B start)
            //      A bottom side (A finish)
            //      B bottom side (B finish)
            // It is important to consider such rectangles intersecting.
            // Note: the Bracket class uses REVERSE logic.
            if (this.first && !o.first) {
                return -1;
            }
            if (!this.first && o.first) {
                return 1;
            }
            // In principle, we can return 0 here;
            // but sorting along another coordinate may lead to better algorithms behaviour
            // and (if necessary) better visualization.
            final long thisFrom = this.boundFrom();
            final long otherFrom = o.boundFrom();
            if (thisFrom < otherFrom) {
                return -1;
            }
            if (thisFrom > otherFrom) {
                return 1;
            }
            return 0;
        }

        @Override
        public String toString() {
            return (isHorizontal() ? (first ? "top" : "bottom") : (first ? "left" : "right"))
                + " side of frame #" + frame.index + ": " + boundFrom() + ".." + boundTo() + " at " + boundCoord();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Side side = (Side) o;

            if (first != side.first) {
                return false;
            }
            return frame.equals(side.frame);
            // Note: we do not try to implement equals/hashCode in Frame class
        }

        @Override
        public int hashCode() {
            int result = frame.hashCode();
            result = 31 * result + (first ? 1 : 0);
            return result;
        }
    }

    public static class HorizontalSide extends Side {
        private HorizontalSide(Frame frame, boolean first) {
            super(frame, first);
        }

        @Override
        public boolean isHorizontal() {
            return true;
        }

        @Override
        public long frameSideCoord() {
            return first ? frame.fromY : frame.toY - 1;
        }

        @Override
        public long boundCoord() {
            return first ? frame.fromY : frame.toY;
        }

        @Override
        public long boundFrom() {
            return frame.fromX;
        }

        @Override
        public long boundTo() {
            return frame.toX;
        }
    }

    public static class VerticalSide extends Side {
        private VerticalSide(Frame frame, boolean first) {
            super(frame, first);
        }

        @Override
        public boolean isHorizontal() {
            return false;
        }

        @Override
        public long frameSideCoord() {
            return first ? frame.fromX : frame.toX - 1;
        }

        @Override
        public long boundCoord() {
            return first ? frame.fromX : frame.toX;
        }

        @Override
        public long boundFrom() {
            return frame.fromY;
        }

        @Override
        public long boundTo() {
            return frame.toY;
        }
    }

    public static abstract class BoundaryLink implements Comparable<BoundaryLink> {
        final Side containingSide;
        final long coord;
        final long from;
        final long to;

        private BoundaryLink(
            Side containingSide,
            long from,
            long to)
        {
            assert containingSide != null;
            this.containingSide = containingSide;
            this.coord = containingSide.boundCoord();
            assert from >= containingSide.boundFrom();
            assert to <= containingSide.boundTo();
            assert from <= to;
            this.from = from;
            this.to = to;
        }

        public Side containingSide() {
            return containingSide;
        }

        /**
         * Returns the coordinate of this boundary element (link) along the coordinate axis,
         * to which this link is perpendicular, increased by 0.5
         * (the bounrady always has half-integer coordinate).
         *
         * @return the perpendicular coordinate of this link + 0.5
         */
        public long coord() {
            return coord;
        }

        /**
         * Returns the starting coordinate of this boundary element (link) along the coordinate axis,
         * to which this link is parallel, increased by 0.5
         * (the bounrady always has half-integer coordinate).
         *
         * @return the starting coordinate of this link + 0.5
         */
        public long from() {
            return from;
        }

        /**
         * Returns the ending coordinate of this boundary element (link) along the coordinate axis,
         * to which this link is parallel, increased by 0.5
         * (the bounrady always has half-integer coordinate).
         *
         * @return the ending coordinate of this link + 0.5
         */
        public long to() {
            return to;
        }

        public abstract IRectangularArea sidePart();

        @Override
        public int compareTo(BoundaryLink o) {
            if (this.coord < o.coord) {
                return -1;
            }
            if (this.coord > o.coord) {
                return 1;
            }
            if (this.from < o.from) {
                return -1;
            }
            if (this.from > o.from) {
                return 1;
            }
            return 0;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            BoundaryLink that = (BoundaryLink) o;
            if (from != that.from) {
                return false;
            }
            if (to != that.to) {
                return false;
            }
            return containingSide.equals(that.containingSide);

        }

        @Override
        public int hashCode() {
            int result = containingSide.hashCode();
            result = 31 * result + (int) (from ^ (from >>> 32));
            result = 31 * result + (int) (to ^ (to >>> 32));
            return result;
        }
    }

    private static class HorizontalBoundaryLink extends BoundaryLink {
        final Side firstTransversalSide;
        final Side secondTransversalSide;
        // - these two fields are necessary only while constructing the boundary
        BoundaryLink previousNeighbour = null;
        BoundaryLink nextNeighbour = null;
        HorizontalBoundaryLink previousHorizontalNeighbour = null;
        HorizontalBoundaryLink nextHorizontalNeighbour = null;

        private HorizontalBoundaryLink(
            HorizontalSide containingSide,
            VerticalSide firstTransversalSide,
            VerticalSide secondTransversalSide)
        {
            super(containingSide, firstTransversalSide.boundCoord(), secondTransversalSide.boundCoord());
            this.firstTransversalSide = firstTransversalSide;
            this.secondTransversalSide = secondTransversalSide;
        }

        @Override
        public IRectangularArea sidePart() {
            return IRectangularArea.valueOf(
                from, containingSide.frameSideCoord(),
                to - 1, containingSide.frameSideCoord());
        }
    }

    public static class VerticalBoundaryLink extends BoundaryLink {
        BoundaryLink previousNeighbour;
        BoundaryLink nextNeighbour;
        VerticalBoundaryLink previousVerticalNeighbour = null;
        VerticalBoundaryLink nextVerticalNeighbour = null;

        private VerticalBoundaryLink(
            VerticalSide containingSide,
            long from,
            long to,
            BoundaryLink previousNeighbour,
            BoundaryLink nextNeighbour)
        {
            super(containingSide, from, to);
            this.previousNeighbour = previousNeighbour;
            this.nextNeighbour = nextNeighbour;
        }

        @Override
        public IRectangularArea sidePart() {
            return IRectangularArea.valueOf(
                containingSide.frameSideCoord(), from,
                containingSide.frameSideCoord(), to - 1);
        }
    }


    private final List<Frame> frames;
    private final IRectangularArea circumscribedRectangle;

    private volatile List<HorizontalSide> horizontalSides = null;
    private volatile List<VerticalSide> verticalSides = null;
    private volatile List<List<Frame>> connectedComponents = null;
    private volatile List<HorizontalSide> horizontalSidesAtBoundary = null;
    private volatile List<VerticalSide> verticalSidesAtBoundary = null;
    private volatile List<List<BoundaryLink>> allBoundaries = null;
    private final Object lock = new Object();

    RectangleSet(List<Frame> frames) {
        this.frames = frames;
        if (frames.isEmpty()) {
            circumscribedRectangle = null;
        } else {
            long minX = Long.MAX_VALUE;
            long minY = Long.MAX_VALUE;
            long maxX = Long.MIN_VALUE;
            long maxY = Long.MIN_VALUE;
            for (Frame frame : frames) {
                minX = Math.min(minX, frame.fromX);
                minY = Math.min(minY, frame.fromY);
                maxX = Math.max(maxX, frame.toX - 1);
                maxY = Math.max(maxY, frame.toY - 1);
            }
            circumscribedRectangle = IRectangularArea.valueOf(minX, minY, maxX, maxY);
        }
    }

    public static RectangleSet newInstance(Collection<IRectangularArea> rectangles) {
        return new RectangleSet(checkAndConvertToFrames(rectangles));
    }

    public List<Frame> frames() {
        return Collections.unmodifiableList(frames);
    }

    public IRectangularArea circumscribedRectangle() {
        return circumscribedRectangle;
    }

    public List<HorizontalSide> horizontalSides() {
        return Collections.unmodifiableList(horizontalSides);
    }

    public List<VerticalSide> verticalSides() {
        return Collections.unmodifiableList(verticalSides);
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
        final List<List<Frame>> result = new ArrayList<List<Frame>>();
        if (!frames.isEmpty()) {
            doFindConnectedComponents(result);
        }
        long t2 = System.nanoTime();
        synchronized (lock) {
            this.connectedComponents = result;
        }
        AbstractPlanePyramidSource.debug(2, "Rectangle set (%d rectangles), finding %d connected components: "
                + "%.3f ms (%.3f mcs / rectangle)%n",
            frames.size(), result.size(),
            (t2 - t1) * 1e-6, (t2 - t1) * 1e-3 / (double) frames.size());
    }

    public void findBoundaries() {
        fillSideLists();
        synchronized (lock) {
            if (this.allBoundaries != null) {
                return;
            }
            if (frames.isEmpty()) {
                this.allBoundaries = new ArrayList<List<BoundaryLink>>();
                return;
            }
        }
        long t1 = System.nanoTime();
        final List<List<HorizontalBoundaryLink>> containedBoundaryLinksForHorizontalSides =
            createListOfLists(horizontalSides.size());
        final List<List<VerticalBoundaryLink>> containedBoundaryLinksForVerticalSides =
            createListOfLists(verticalSides.size());
        long t2 = System.nanoTime();
        findHorizontalBoundaries(containedBoundaryLinksForHorizontalSides);
        long t3 = System.nanoTime();
//        convertHorizontalLinkInfoToAllBoundaryLinkLists(
//            containedBoundaryLinksForHorizontalSides,
//            containedBoundaryLinksForVerticalSides);
        long t4 = System.nanoTime();
        final List<List<BoundaryLink>> result =
            doJoinBoundaries(containedBoundaryLinksForHorizontalSides, containedBoundaryLinksForVerticalSides);
        long t5 = System.nanoTime();
        synchronized (lock) {
            List<HorizontalSide> horizontalSidesAtBoundary = new ArrayList<HorizontalSide>();
            for (int k = 0, n = horizontalSides.size(); k < n; k++) {
                final HorizontalSide side = horizontalSides.get(k);
                side.containedBoundaryLinks = containedBoundaryLinksForHorizontalSides.get(k);
                if (!side.containedBoundaryLinks.isEmpty()) {
                    horizontalSidesAtBoundary.add(side);
                }
            }
            List<VerticalSide> verticalSidesAtBoundary = new ArrayList<VerticalSide>();
            for (int k = 0, n = verticalSides.size(); k < n; k++) {
                final VerticalSide side = verticalSides.get(k);
                side.containedBoundaryLinks = containedBoundaryLinksForVerticalSides.get(k);
                if (!side.containedBoundaryLinks.isEmpty()) {
                    verticalSidesAtBoundary.add(side);
                }
            }
            this.horizontalSidesAtBoundary = horizontalSidesAtBoundary;
            this.verticalSidesAtBoundary = verticalSidesAtBoundary;
            this.allBoundaries = result;
        }
        long t6 = System.nanoTime();
        long totalLinkCount = 0;
        for (List<BoundaryLink> boundary : result) {
            totalLinkCount += boundary.size();
        }
        AbstractPlanePyramidSource.debug(2, "Rectangle set (%d rectangles), finding %d boundaries with %d links: "
                + "%.3f ms = %.3f initializing + %.3f horizontal links + %.3f vertical links + "
                + "%.3f joining links + %.3f correcting data structures (%.3f mcs / rectangle)%n",
            frames.size(), result.size(), totalLinkCount,
            (t6 - t1) * 1e-6, (t2 - t1) * 1e-6, (t3 - t2) * 1e-6, (t4 - t3) * 1e-6,
            (t5 - t4) * 1e-6, (t6 - t5) * 1e-6,
            (t6 - t1) * 1e-3 / (double) frames.size());
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
            for (int k = 0, n = horizontalSides.size(); k < n; k++) {
                horizontalSides.get(k).indexInSortedList = k;
            }
            for (int k = 0, n = verticalSides.size(); k < n; k++) {
                verticalSides.get(k).indexInSortedList = k;
            }
            this.horizontalSides = horizontalSides;
            this.verticalSides = verticalSides;
        }
        AbstractPlanePyramidSource.debug(2, "Rectangle set (%d rectangles), sorting sides: %.3f ms%n",
            frames.size(), (t2 - t1) * 1e-6);
    }

    private void doFindConnectedComponents(List<List<Frame>> result) {
        final long[] allX = new long[verticalSides.size()];
        assert allX.length > 0;
        // - checked in the calling method
        for (int k = 0; k < allX.length; k++) {
            allX[k] = verticalSides.get(k).boundCoord();
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
    }

    private void findIncidentFrames(List<Frame> result, Frame frame, long[] allX, boolean added[]) {
        result.clear();
        int left = Arrays.binarySearch(allX, frame.fromX);
        assert left >= 0;
        // - we should find at least this frame itself
        assert allX[left] == frame.fromX;
        while (left > 0 && allX[left - 1] == frame.fromX) {
            left--;
        }
        int right = Arrays.binarySearch(allX, frame.toX);
        assert right >= 0;
        // - we should find at least this frame itself
        assert allX[right] == frame.toX;
        while (right + 1 < allX.length && allX[right + 1] == frame.toX) {
            right++;
        }
        for (int k = left; k <= right; k++) {
            final Frame other = verticalSides.get(k).frame;
            assert other.toX >= frame.fromX && other.fromX <= frame.toX : "Binary search in allX failed";
            if (other.toY < frame.fromY || other.fromY > frame.toY) {
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

    private void findHorizontalBoundaries(
        List<List<HorizontalBoundaryLink>> resultingContainedBoundaryLinksForHorizontalSides)
    {
        assert !frames.isEmpty();
        final HorizontalBracketSet bracketSet = new HorizontalBracketSet(horizontalSides, true);
        while (bracketSet.next()) {
            final Frame frame = bracketSet.horizontal.frame;
            final NavigableSet<Bracket> brackets = bracketSet.currentIntersections();
            final Bracket lastBefore = bracketSet.lastIntersectionBeforeLeft();
            boolean lastRightAtBoundary = lastBefore == null || lastBefore.followingCoveringDepth == 0;
            VerticalSide lastLeftVertical = lastRightAtBoundary ? frame.lessVerticalSide : null;
            for (Bracket bracket : brackets) {
                assert bracket.covers(bracketSet.y);
                boolean rightAtBoundary = bracket.followingCoveringDepth == 0;
                if (rightAtBoundary == lastRightAtBoundary) {
                    continue;
                }
                if (rightAtBoundary) {
                    lastLeftVertical = (VerticalSide) bracket.intersectingSide;
                } else {
                    addHorizontalLink(bracketSet, lastLeftVertical, (VerticalSide) bracket.intersectingSide,
                        resultingContainedBoundaryLinksForHorizontalSides);
                }
                lastRightAtBoundary = rightAtBoundary;
            }
            if (lastRightAtBoundary) {
                addHorizontalLink(bracketSet, lastLeftVertical, frame.higherVerticalSide,
                    resultingContainedBoundaryLinksForHorizontalSides);
            }
        }
    }

    private void convertHorizontalLinkInfoToAllBoundaryLinkLists(
        List<List<HorizontalBoundaryLink>> completedContainedBoundaryLinksForHorizontalSides,
        List<List<VerticalBoundaryLink>> resultingContainedBoundaryLinksForVerticalSides)
    {
        assert !frames.isEmpty();
        List<List<HorizontalBoundaryLink>> intersectingHorizontals = createListOfLists(verticalSides.size());
        for (List<HorizontalBoundaryLink> linksOnSide : completedContainedBoundaryLinksForHorizontalSides) {
            HorizontalSide side = null;
            for (HorizontalBoundaryLink link : linksOnSide) {
                if (side == null) {
                    side = (HorizontalSide) link.containingSide;
                } else {
                    assert side == link.containingSide;
                }
                intersectingHorizontals.get(link.firstTransversalSide.indexInSortedList).add(link);
                intersectingHorizontals.get(link.secondTransversalSide.indexInSortedList).add(link);
            }
        }
        for (int verticalIndex = 0, n = intersectingHorizontals.size(); verticalIndex < n; verticalIndex++) {
            //TODO!! more correct processing by joning vertical sides, which are continuations of each other
            List<HorizontalBoundaryLink> horizontalsAcrossSide = intersectingHorizontals.get(verticalIndex);
            final HorizontalBoundaryLink[] horizontals = horizontalsAcrossSide.toArray(
                new HorizontalBoundaryLink[horizontalsAcrossSide.size()]);
            Arrays.sort(horizontals);
            long last = 157;
            for (int k = 0; k < horizontals.length; k += 2) {
                //TODO!! process vertical neighbours
                long from = horizontals[k].coord;
                long to = horizontals[k + 1].coord;
                assert k == 0 || from > last :
                    "Two horizontal links with the same ordinate " + from + "(" + last
                        + ") are incident with the same vertical side";
                assert from < to :
                    "Empty vertical link #" + (k / 2) + ": " + from + ".." + to + ", vertical index " + verticalIndex;
                final VerticalBoundaryLink link = new VerticalBoundaryLink(
                    verticalSides.get(verticalIndex), from, to, horizontals[k], horizontals[k + 1]);
                resultingContainedBoundaryLinksForVerticalSides.get(verticalIndex).add(link);
                last = to;
            }
        }
        //TODO!! complete horizontal links
    }

    private List<List<BoundaryLink>> doJoinBoundaries(
        List<List<HorizontalBoundaryLink>> containedBoundaryLinksForHorizontalSides,
        List<List<VerticalBoundaryLink>> containedBoundaryLinksForVerticalSides)
    {
        assert !frames.isEmpty();
        //TODO!!
        return new ArrayList<List<BoundaryLink>>();
    }

    private static void addHorizontalLink(
        HorizontalBracketSet bracketSet,
        VerticalSide firstTransveralSide,
        VerticalSide secondTransveralSide,
        List<List<HorizontalBoundaryLink>> resultingContainedBoundaryLinksForHorizontalSides)
    {
        final HorizontalBoundaryLink link = new HorizontalBoundaryLink(
            bracketSet.horizontal,
            firstTransveralSide,
            secondTransveralSide);
        if (link.from < link.to) {
            resultingContainedBoundaryLinksForHorizontalSides.get(bracketSet.horizontalIndex).add(link);
        }
    }

    private static <T> List<List<T>> createListOfLists(int n) {
        final List<List<T>> result = new ArrayList<List<T>>();
        for (int k = 0; k < n; k++) {
            result.add(new ArrayList<T>());
        }
        return result;
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

