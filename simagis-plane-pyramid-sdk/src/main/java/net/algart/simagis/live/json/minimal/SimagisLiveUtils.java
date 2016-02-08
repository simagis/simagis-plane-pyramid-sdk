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

package net.algart.simagis.live.json.minimal;

import net.algart.simagis.pyramid.AbstractPlanePyramidSource;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SimagisLiveUtils {
    public static final PrintStream OUT = System.out;

    private SimagisLiveUtils() {
    }

    public static JSONObject parseArgs(String... args) {
        final Pattern[] opts = new Pattern[] {
            Pattern.compile("^--([\\w\\d\\-]++)"),
            Pattern.compile("^-([\\w\\d\\-]++)"),
            Pattern.compile("^--([\\w\\d\\-]++)=(.*)"),
            Pattern.compile("^-([\\w\\d\\-]++)=(.*)")
        };
        final JSONObject result = new JSONObject();
        final JSONArray resultArgs = openArray(result, "args");
        final JSONArray resultValues = openArray(result, "values");
        final JSONObject options = openObject(result, "options");
        args:
        for (final String arg : args) {
            resultArgs.put(arg);
            for (final Pattern opt : opts) {
                final Matcher matcher = opt.matcher(arg);
                if (matcher.matches()) {
                    try {
                        switch (matcher.groupCount()) {
                            case 1:
                                options.put(matcher.group(1), true);
                                break;
                            case 2:
                                options.put(matcher.group(1), matcher.group(2));
                                break;
                        }
                    } catch (JSONException e) {
                        throw new AssertionError(e);
                    }
                    continue args;
                }
            }
            resultValues.put(arg);
        }
        return result;
    }

    /**
     * Fully disable any output via <tt>System.out</tt>.
     * Can be useful to disable possible output of third-party libraries.
     */
    public static void disableStandardOutput() {
        System.setOut(new PrintStream(new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                // no operations
            }
        }));
    }

    public static void putRowAttribute(
        JSONArray attributesJson,
        String caption,
        Object value,
        boolean advanced)
        throws JSONException
    {
        putAttribute(attributesJson, "Row", caption, value, advanced);
    }

    public static void putSectionAttribute(
        JSONArray attributesJson,
        String caption,
        Object value,
        boolean advanced)
        throws JSONException
    {
        putAttribute(attributesJson, "Section", caption, value, advanced);
    }

    public static void putImageThumbnailAttribute(
        JSONArray attributesJson,
        String caption,
        JSONObject value,
        boolean advanced)
        throws JSONException
    {
        putAttribute(attributesJson, "ImageThumbnail", caption, value, advanced);
    }

    public static JSONObject putError(JSONObject object, Throwable x) {
        try {
            object.put("error", toJSONError(x, null));
        } catch (JSONException e) {
            throw new AssertionError(e);
        }
        return object;
    }

    public static void printJsonError(Throwable throwable) {
        String error;
        try {
            error = putError(new JSONObject(), throwable).toString(4);
        } catch (JSONException e) {
            throwable.addSuppressed(e);
            error = putError(new JSONObject(), throwable).toString();
        }
        OUT.print(error);
    }

    public static JSONObject createThumbnail(
        File projectDir,
        File pyramidDir,
        String imageName,
        BufferedImage refImage)
        throws IOException, JSONException
    {
        final Path projectScope = projectDir.toPath();
        final BufferedImage tmbImage = toThumbnailSize(refImage);

        final File thumbnailDir = new File(pyramidDir.getParentFile(), ".thumbnail");
        thumbnailDir.mkdir();
        final File refFile = File.createTempFile(imageName + ".", ".jpg", thumbnailDir);
        final File tmbFile = tmbImage != refImage
            ? File.createTempFile(imageName + ".tmb.", ".jpg", thumbnailDir)
            : refFile;

        final JSONObject thumbnail = new JSONObject();
        thumbnail.put("scope", "Project");
        thumbnail.put("width", tmbImage.getWidth());
        thumbnail.put("height", tmbImage.getHeight());
        thumbnail.put("src", toRef(projectScope, tmbFile.toPath()));
        ImageIO.write(refImage, "JPEG", refFile);
        if (tmbFile != refFile) {
            thumbnail.put("ref", toRef(projectScope, refFile.toPath()));
            ImageIO.write(tmbImage, "JPEG", tmbFile);
        }
        return thumbnail;
    }

    public static JSONObject configurationStringToJson(String configuration) throws IOException {
        try {
            return new JSONObject(configuration);
        } catch (JSONException e) {
            throw new IOException("Illegal configuration string", e);
        }
    }

    public static void standardCustomizePlanePyramidSourceRendering(
        AbstractPlanePyramidSource source,
        String renderingConfiguration)
        throws IOException
    {
        standardCustomizePlanePyramidSourceRendering(source, configurationStringToJson(renderingConfiguration));
    }

    public static void standardCustomizePlanePyramidSourceRendering(
        AbstractPlanePyramidSource source,
        JSONObject renderingJson)
    {
        final JSONObject coarseData = openObject(renderingJson, "coarseData");
        source.setSkipCoarseData(coarseData.optBoolean("skip"));
        source.setSkippingFiller(coarseData.optDouble("filler", 0.0));
    }


    public static Set<String> getKeySet(JSONObject jsonObject) {
        if (jsonObject == null) {
            return Collections.emptySet();
        }
        final TreeSet<String> result = new TreeSet<String>();
        final Iterator keys = jsonObject.keys();
        while (keys.hasNext()) {
            result.add((String) keys.next());
        }
        return Collections.unmodifiableSortedSet(result);
    }


    public static JSONArray openArray(JSONObject object, String key) {
        JSONArray result;
        try {
            result = object.optJSONArray(key);
            if (result == null) {
                result = new JSONArray();
                object.put(key, result);
            }
        } catch (JSONException e) {
            throw new AssertionError(e);
        }
        return result;
    }

    public static JSONObject openObject(JSONObject object, String key) {
        JSONObject result;
        try {
            result = object.optJSONObject(key);
            if (result == null) {
                result = new JSONObject();
                object.put(key, result);
            }
        } catch (JSONException e) {
            throw new AssertionError(e);
        }
        return result;
    }

    public static JSONObject openObject(JSONObject object, String... keys) {
        JSONObject result = Objects.requireNonNull(object);
        for (final String k : keys) {
            result = openObject(result, k);
        }
        return result;
    }

    private static void putAttribute(
        JSONArray attributesJson,
        String type,
        String caption,
        Object value,
        boolean advanced) throws JSONException
    {
        final JSONObject attribute = new JSONObject();
        attribute.put("type", type);
        attribute.put("caption", caption);
        attribute.put("value", value);
        attribute.put("advanced", advanced);
        attributesJson.put(attribute);
    }

    private static BufferedImage toThumbnailSize(BufferedImage image) {
        final int w = image.getWidth();
        final int h = image.getHeight();
        final int max = Math.max(w, h);
        if (max <= 256) {
            return image;
        }
        final double s = max / 256d;
        final BufferedImage result = new BufferedImage(
            Math.max((int) Math.min(w / s, 256), 1),
            Math.max((int) Math.min(h / s, 256), 1),
            BufferedImage.TYPE_INT_BGR);
        final Graphics2D graphics = result.createGraphics();
        try {
            graphics.drawImage(image.getScaledInstance(
                result.getWidth(), result.getHeight(), Image.SCALE_SMOOTH), 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return result;
    }

    private static String toRef(Path scope, Path path) {
        return scope.relativize(path).toString().replace('\\', '/');
    }

    private static JSONObject toJSONError(Throwable x, JSONObject log) {
        final StringWriter stackTrace = new StringWriter();
        x.printStackTrace(new PrintWriter(stackTrace));
        final JSONObject error = new JSONObject();
        try {
            error.put("error", x.getClass().getSimpleName());
            error.put("exception", x.getClass().getName());
            error.put("message", x.getLocalizedMessage());
            error.put("stackTrace", stackTrace.toString());
            if (log != null) {
                error.put("log", log);
            }
        } catch (JSONException e) {
            throw new AssertionError(e);
        }
        return error;
    }

}
