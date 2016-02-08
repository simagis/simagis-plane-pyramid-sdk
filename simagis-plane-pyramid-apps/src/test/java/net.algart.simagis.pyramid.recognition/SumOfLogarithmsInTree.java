/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2016 Daniel Alievsky, AlgART Laboratory (http://algart.net)
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

public class SumOfLogarithmsInTree {
    public static void main(String[] args) {
        System.out.println("This test describes complexity of an algorithm, that can be used for finding");
        System.out.println("the maximum rectangle under a function f(x), consisting of several constant intervals");
        System.out.println("(horizontal segments). To do this, we need to quickly find minimums in subranges of");
        System.out.println("y[1..M] array; let's suppose that we can find any such minimum in log(L) operations,");
        System.out.println("where L is the length of a subrange. Then the total number of operations, in the best");
        System.out.println("case, is estimated as log M + 2 * log(M/2) + 4 * log(M/4) + ...");
        for (int log = 1; log < 30; log++) {
            double sum = 0.0;
            for (int k = 0; k < log; k++) {
                final long mDivPowerOf2 = 1L << k;
                sum += mDivPowerOf2 * (log - k);
            }
            long m = 1L << log;
            System.out.printf("%d: %d, %.1f, %.5f%n", log, m, sum, sum / m);
        }

    }
}
