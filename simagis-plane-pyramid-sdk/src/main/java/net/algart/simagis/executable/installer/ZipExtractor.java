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

package net.algart.simagis.executable.installer;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ZipExtractor {
    private static void extractFile(File zipFile, String entryName, File resultFile) throws IOException {
        final ZipFile zip = new ZipFile(zipFile);
        final ZipEntry entry = zip.getEntry(entryName);
        if (entry == null) {
            throw new FileNotFoundException("No entry " + entryName + " in " + zipFile);
        }
        final InputStream in = zip.getInputStream(entry);
        final OutputStream out = new FileOutputStream(resultFile);
        final byte[] buffer = new byte[1048576];
        for (;;)  {
            int n = in.read(buffer);
            if (n <= 0) {
                break;
            }
            out.write(buffer, 0, n);
        }
        out.flush();
        out.close();
        in.close();
    }

    public static void main(String[] args) throws IOException {
        int argStartIndex = 0;
        if (args.length >= 1 && args[0].equals("-os")) {
            System.out.println("OS name: " + System.getProperty("os.name"));
            System.out.println("OS architecture: " + System.getProperty("os.arch"));
            argStartIndex = 1;
        }
        if (args.length < argStartIndex + 3) {
            System.out.println("Usage:");
            System.out.println("    " + ZipExtractor.class.getName() + " zip/jar-file entry-name result-file-name");
            return;
        }
        final File jarFile = new File(args[argStartIndex]);
        final String entryName = args[argStartIndex + 1];
        final File resultFile = new File(args[argStartIndex + 2]);
        final File parentDir = resultFile.getParentFile();
        if (parentDir != null) {
            parentDir.mkdirs();
        }
        extractFile(jarFile, entryName, resultFile);
        System.out.println(entryName + " successfully extracted from "
            + jarFile.getAbsolutePath() + " to "
            + resultFile.getAbsolutePath());
    }
}
