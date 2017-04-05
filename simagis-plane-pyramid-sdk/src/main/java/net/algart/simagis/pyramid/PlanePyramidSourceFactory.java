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

package net.algart.simagis.pyramid;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Factory allowing to construct {@link PlanePyramidSource} instance on the base
 * of the path to an external resource, where the pyramid is stored,
 * and some additional configuration information.
 * This interface should have different implementations for different formats of image pyramids.
 */
public interface PlanePyramidSourceFactory {
    /**
     * Creates new plane pyramid source, providing access to the pyramid, stored in the given place,
     * with possible using additional recommendations, described in
     * <tt>pyramidConfiguration</tt> and <tt>renderingConfiguration</tt> arguments.
     *
     * <p>The <tt>pyramidPath</tt> can be any specifier of some external resource, like URL,
     * but usually it is a path to some disk file or subdirectory (for example, a path to .TIFF file).
     *
     * <p>The <tt>pyramidConfiguration</tt> and <tt>renderingConfiguration</tt> arguments can use any format,
     * but we recommend to use JSON format for this string.
     * Most existing implementations expect correct JSON format here.
     * Syntax errors in this file should be ignored or lead to <tt>IOException</tt>, like format errors
     * in the data file.
     *
     * @param pyramidPath            path to an external resource, where the pyramid is stored;
     *                               usually a disk path to some directory.
     * @param pyramidConfiguration   some additional information, describing the pyramid and necessary behaviour
     *                               of the resulting pyramid source, which relates to the given data and
     *                               cannot be changed dynamically.
     * @param renderingConfiguration some additional information for customizing behaviour of the resulting
     *                               pyramid source, which can vary in future for the same data file.
     * @return new pyramid source, providing access to the pyramid at the specified path.
     * @throws NullPointerException if one of the arguments is <tt>null</tt>.
     * @throws IOException          if some I/O problems occur while opening pyramid, and also in a case
     *                              of invalid format of the files containing the pyramid or
     *                              of the passed <tt>renderingConfiguration</tt> description.
     */
    PlanePyramidSource newPlanePyramidSource(
        String pyramidPath,
        String pyramidConfiguration,
        String renderingConfiguration)
        throws IOException;
}
