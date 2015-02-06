package com.simagis.pyramid.access;

import com.simagis.images.color.Image2D;
import com.simagis.images.color.ImageContext;
import com.simagis.images.color.SimpleImageContext;
import net.algart.simagis.pyramid.AbstractPlanePyramidSource;
import net.algart.simagis.pyramid.PlanePyramidSource;
import net.algart.simagis.pyramid.PlanePyramidTools;
import com.simagis.pyramid.io.DefaultPlanePyramidIO;
import net.algart.simagis.pyramid.sources.ImageIOPlanePyramidSource;
import net.algart.simagis.pyramid.sources.RotatingPlanePyramidSource;
import net.algart.arrays.Arrays;
import net.algart.contexts.ArrayMemoryContext;
import net.algart.contexts.Context;
import net.algart.contexts.DefaultArrayContext;
import net.algart.contexts.StatusUpdater;
import net.algart.external.ImageConversions;
import net.algart.math.IRectangularArea;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.logging.Logger;

public class PlanePyramidStitcher {
    private static final boolean DEBUG_DELAYED_SOURCE = false;
    private static final double WHOLE_SLIDE_CODE = 0.0;
    private static final double MAP_IMAGE_CODE = -1.0;
    private static final double LABEL_ONLY_IMAGE_CODE = -2.0;
    private static final double THUMBNAIL_IMAGE_CODE = -3.0;
    private static final double CUSTOM_KIND_1_CODE = -4.0;
    private static final double CUSTOM_KIND_2_CODE = -5.0;

    public static enum SourceFormat {
        ImageIO("ImageIO", "data.jpg", "data.jpeg", "data.png", "data.bmp"),
        SWS("SWS", "data.sws"),
        SVS("SVS", "data.svs"),
        SCN("SCN", "data.scn"),
        BIF("BIF", "data.bif"),
        VSI("VSI", "data.vsi"),
        NDPRead("NDPRead", "data.ndpi"),
        HisTech("3DHISTECH", "data.mrxs"),
        Phillips("Phillips", "data.tif"),
        REMOTE("remote", "data.remote");

        final String requiredModeSubString;
        final String[] requiredFileNames;

        private SourceFormat(String requiredModeSubString, String... requiredFileNames) {
            this.requiredModeSubString = requiredModeSubString;
            this.requiredFileNames = requiredFileNames;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(PlanePyramidStitcher.class.getName());

    public static PlanePyramidContainer makePlanePyramid(
        Context context,
        @Name("image") Image2D image,
        @Name("config") @UI(description = "Custom settings in JSON format", category = "text") String config,
        @Name("backgroundColor") @UI(defaultValue = "0,0,0", description = "Background color; "
            + "number of components defines the number of bands in the result") double[] backgroundColor)
        throws JSONException
    {
        final PlanePyramid pyramid = newPlanePyramid(context, image, config, backgroundColor);
        PlanePyramidContainer result = new PlanePyramidContainer(context, pyramid);
        result.toPlanePyramid(); // for additional testing
        return result;
    }

    public static PlanePyramidContainer makeFollowingResolutions(
        Context context,
        @Name("pyramidDirectory") String pyramidDirectory,
        @Name("initialResolutionLevel") int initialResolutionLevel,
        @Name("config") @UI(description = "Custom settings in JSON format", category = "text") String config,
        @Name("mode") @UI(category = "text") String mode)
        throws IOException, JSONException
    {
        if (mode == null) {
            mode = "";
        }
        JSONObject cfg = config == null ? new JSONObject() : new JSONObject(config);
        PlanePyramidSource source = getPyramidSource(context, pyramidDirectory, mode);
        long t1 = System.nanoTime();
        int compression = cfg.optInt("compression", PlanePyramid.DEFAULT_COMPRESSION);

        DefaultFollowingResolutionsBuilder builder = new DefaultFollowingResolutionsBuilder(
            source, initialResolutionLevel, compression);
        long dimX = builder.getDimX();
        long dimY = builder.getDimY();
        long totalNumberOfElements = builder.getTotalNumberOfElements();
        if (cfg.has("minimalPyramidSize")) {
            builder.setMinimalNewResolutionLayerSize(cfg.getLong("minimalPyramidSize"));
        }
        if (cfg.has("numberOfNewResolutions")) {
            builder.setNumberOfNewResolutions(cfg.getInt("numberOfNewResolutions"));
        }
        if (cfg.has("processingTileDim")) {
            builder.setProcessingTileDim(cfg.getLong("processingTileDim"));
        }
        builder.process(new DefaultArrayContext(context));
        List<Matrix<? extends UpdatablePArray>> followingResolutions = builder.getResults();
/*
        long[] dimensions = source.dimensions(initialResolutionLevel);
        long dimX = dimensions[1];
        long dimY = dimensions[2];
        long totalNumberOfElements = Arrays.longMul(dimensions);
        long minimalPyramidSize = cfg.optLong("minimalPyramidSize", PlanePyramid.DEFAULT_MINIMAL_PYRAMID_SIZE);
        int numberOfNewResolutions = cfg.has("numberOfNewResolutions") ?
            cfg.getInt("numberOfNewResolutions") :
            PlanePyramid.numberOfResolutions(dimensions[1], dimensions[2], compression, minimalPyramidSize);
        long processingTileDim = cfg.optLong("processingTileDim",
            PlanePyramid.RECOMMENDED_TILE_DIM_FOR_MAKING_FOLLOWING_RESOLUTIONS);
        List<Matrix<? extends UpdatablePArray>> followingResolutions =
            PlanePyramid.makeFollowingResolutions(new DefaultArrayContext(context),
                source, initialResolutionLevel, compression, numberOfNewResolutions,
                PlanePyramid.AveragingMode.DEFAULT,
                processingTileDim);
*/
        List<Matrix<? extends UpdatablePArray>> pyramid = new ArrayList<Matrix<? extends UpdatablePArray>>();
        pyramid.addAll(Collections.<Matrix<? extends UpdatablePArray>>nCopies(initialResolutionLevel + 1, null));
        pyramid.addAll(followingResolutions);
        long t2 = System.nanoTime();
        PlanePyramidContainer result = new PlanePyramidContainer(context, pyramid);
        String msg = String.format(Locale.US,
            "Image %dx%d appended by %d resolution levels in %.5f s: scaling speed %.5f MB/s",
            dimX, dimY, pyramid.size(),
            (t2 - t1) * 1e-9,
            followingResolutions.isEmpty() ? Double.NaN : Arrays.sizeOf(followingResolutions.get(0).elementType(),
                totalNumberOfElements) / 1048576.0 / ((t2 - t1) * 1e-9)
        );
        if (Arrays.SystemSettings.profilingMode()) {
            if (context.is(StatusUpdater.class)) {
                context.as(StatusUpdater.class).updateStatus(msg);
            }
        }
        LOGGER.config(msg);
        return result;
    }

    public static Image2D getImage(
        Context context,
        @Name("pyramid") PlanePyramidContainer pyramid,
        @Name("fromX") long fromX,
        @Name("fromY") long fromY,
        @Name("toX") long toX,
        @Name("toY") long toY,
        @Name("compression") double compression)
        throws JSONException
    {
        PlanePyramid planePyramid = pyramid.toPlanePyramid(context);
        final Image2D result = getImage(context, planePyramid, fromX, fromY, toX, toY, compression);
        planePyramid.freeResources(PlanePyramidSource.FlushMethod.STANDARD);
        return result;
    }

    public static Image2D getImage(
        Context context,
        @Name("pyramidDirectory") String pyramidDirectory,
        @Name("fromX") long fromX,
        @Name("fromY") long fromY,
        @Name("toX") long toX,
        @Name("toY") long toY,
        @Name("compression") double compression,
        @Name("mode") @UI(category = "text") String mode)
        throws IOException, JSONException
    {
        if (mode == null) {
            mode = "";
        }
        PlanePyramid pyramid = getPyramid(null, pyramidDirectory, mode);
        final Image2D result = getImage(context, pyramid, fromX, fromY, toX, toY, compression);
        pyramid.freeResources(PlanePyramidSource.FlushMethod.STANDARD);
        return result;
    }

    public static Image2D getWholeImage(
        Context context,
        @Name("pyramidDirectory") String pyramidDirectory,
        @Name("compression") double compression,
        @Name("mode") @UI(category = "text") String mode)
        throws IOException, JSONException
    {
        if (mode == null) {
            mode = "";
        }
        PlanePyramid pyramid = getPyramid(null, pyramidDirectory, mode);
        final Image2D result = getImage(context, pyramid, 0, 0, pyramid.dimX(), pyramid.dimY(), compression);
        pyramid.freeResources(PlanePyramidSource.FlushMethod.STANDARD);
        return result;
    }

    public static String sharePyramid(
        Context context,
        @Name("pyramid") PlanePyramidContainer pyramid,
        @Name("directory") String directory)
        throws IOException
    {
//        context = null; // debugging
        pyramid.share(context, new File(directory));
        return directory;
    }

    public static PlanePyramidContainer openPyramid(
        Context context,
        @Name("directory") String directory)
        throws IOException
    {
//        context = null; // debugging
        return PlanePyramidContainer.open(context, new File(directory));
    }

    public static PlanePyramid newPlanePyramid(
        Context context,
        Image2D image,
        String config,
        double[] backgroundColor)
        throws JSONException
    {
        JSONObject cfg = config == null ? new JSONObject() : new JSONObject(config);
        JSONObject debug = cfg.optJSONObject("debug");
        MemoryModel mm = context.as(ArrayMemoryContext.class).getMemoryModel(cfg.optString("memoryModel", ""));
//        System.out.println(mm);
        DefaultArrayContext arrayContext = new DefaultArrayContext(context);
        ArrayContext ac = arrayContext.part(0.0, 0.9);
        long t1 = System.nanoTime();
        double multiplier = 1.0;
        long randomShift = 0;
        Random rnd = new Random(157);
        if (debug != null) {
            multiplier = debug.optDouble("multiplier", multiplier);
            randomShift = debug.optLong("randomShift", randomShift);
        }
        int compression = cfg.optInt("compression", PlanePyramid.DEFAULT_COMPRESSION);
        long minimalPyramidSize = cfg.optLong("minimalPyramidSize", PlanePyramid.DEFAULT_MINIMAL_PYRAMID_SIZE);
        int numberOfResolution = cfg.has("numberOfResolutions") ?
            cfg.optInt("numberOfResolutions", 0) :
            PlanePyramidTools.numberOfResolutions(image.dimX(), image.dimY(), compression, minimalPyramidSize);

        PlanePyramid.AveragingCastMode averagingMode = PlanePyramid.AveragingCastMode.valueOf(
            cfg.optString("averagingCastMode", PlanePyramid.AveragingCastMode.NONE.name()));
        PlanePyramid pyramid = PlanePyramid.newInstance(mm,
            image.i().elementType(),
            image.dimX(), image.dimY(),
            compression,
            numberOfResolution,
            cfg.has("tileDim") ? new Dimension(cfg.getInt("tileDim"), cfg.getInt("tileDim")) : null,
            backgroundColor,
            averagingMode);
        DefaultPlanePyramidIO.setOptionsFromJSON(pyramid, cfg);
        long t2 = System.nanoTime();
        final long tileMapSize = pyramid.tileMapDimX() * pyramid.tileMapDimY();
        long count = 0;
        UpdatablePArray buffer = Arrays.SMM.newMatrix( // important: force using SimpleMemoryModel for work buffer!
            UpdatablePArray.class,
            image.i().elementType(),
            pyramid.bandCount(), pyramid.tileDimX(), pyramid.tileDimY()).array();
        long tLoad = 0, tAdd = 0, tt1 = 0, tt2 = 0, tt3 = 0;
        double memory = 0.0;
        for (long yIndex = 0; yIndex < pyramid.tileMapDimY(); yIndex++) {
            for (long xIndex = 0; xIndex < pyramid.tileMapDimX(); xIndex++, count++) {
                tt1 = System.nanoTime();
                List<Matrix<? extends PArray>> rgbi = new ArrayList<Matrix<? extends PArray>>();
                IRectangularArea tile = pyramid.tile(xIndex, yIndex).intersection(pyramid.allArea());
                for (Matrix<? extends PArray> m : image.rgbi()) {
                    rgbi.add(m.subMatrix(tile));
                }
                Matrix<? extends UpdatablePArray> m3D = Matrices.matrixAtSubArray(buffer, 0,
                    pyramid.bandCount(), rgbi.get(0).dimX(), rgbi.get(0).dimY());
                ImageConversions.pack2DBandsIntoSequentialSamples(
                    ac.part(count, count + 1, tileMapSize),
                    rgbi, m3D);
                tt2 = System.nanoTime();
                long x = xIndex * pyramid.tileDimX();
                long y = yIndex * pyramid.tileDimY();
                if (debug != null) {
                    x *= multiplier;
                    y *= multiplier;
                    x += randomShift * (2.0 * rnd.nextDouble() - 1.0);
                    y += randomShift * (2.0 * rnd.nextDouble() - 1.0);
                    System.out.printf("Stitched frame #%d/%d at (%d,%d): %s%n", count, tileMapSize, x, y, tile);
                }
                pyramid.addImage(m3D, x, y);
                tt3 = System.nanoTime();
                memory += Matrices.sizeOf(m3D);
                tLoad += tt2 - tt1;
                tAdd += tt3 - tt2;
            }
            String msg = String.format(Locale.US,
                "Stitching (%.1f%%): average speed %.3f MB/sec, "
                    + "loading/packing speed %.3f MB/sec, adding speed %.3f MB/sec; "
                    + "cached %d rows (%d/%d hits/misses writing, %d/%d hits/misses reading), "
                    + "%d extra tile scaling, %d partial tile scaling",
                (yIndex + 1) * 100.0 / pyramid.tileMapDimY(),
                memory / 1048576.0 / ((tt3 - t2) * 1e-9),
                memory / 1048576.0 / (tLoad * 1e-9),
                memory / 1048576.0 / (tAdd * 1e-9),
                pyramid.currentTileRowCacheSize(),
                pyramid.cacheWriteHitCounter(), pyramid.cacheWriteMissCounter(),
                pyramid.cacheReadHitCounter(), pyramid.cacheReadMissCounter(),
                pyramid.extraTileScalingCounter(),
                pyramid.partialTileScalingCounter()
            );
            if (Arrays.SystemSettings.profilingMode()) {
                if (context.is(StatusUpdater.class)) {
                    context.as(StatusUpdater.class).updateStatus(msg);
                }
            }
            LOGGER.config(msg);
        }
        long t3 = System.nanoTime();
        LOGGER.config("Number of partially available pyramid layers: "
            + pyramid.numberOfImmediatelyAvailableResolutions());
        pyramid.finish(arrayContext.part(0.9, 1.0));
        pyramid.freeResources(PlanePyramidSource.FlushMethod.FORCE_PHYSICAL_FLUSH);
        long t4 = System.nanoTime();
        memory = Matrices.sizeOf(image.rgbi());
        String msg = String.format(Locale.US,
            "%d tiles stitched in %.5f s: "
                + "%.5f allocation, %.5f stitching, %.5f finishing "
                + "(stitching/finishing include %.5f copying and %.5f scaling); "
                + "stitching speed %.5f MB/s (without finishing %.5f MB/s, "
                + "copying %.5f MB/s, scaling %.5f MB/s), "
                + "cached %d rows (%d/%d hits/misses writing, %d/%d hits/misses reading), "
                + "%d extra tile scaling, %d partial tile scaling",
            count, (t4 - t1) * 1e-9,
            (t2 - t1) * 1e-9, (t3 - t2) * 1e-9, (t4 - t3) * 1e-9,
            pyramid.copyingToZeroLevelTime() * 1e-9, pyramid.scalingTime() * 1e-9,
            memory / 1048576.0 / ((t4 - t2) * 1e-9), memory / 1048576.0 / ((t3 - t2) * 1e-9),
            memory / 1048576.0 / (pyramid.copyingToZeroLevelTime() * 1e-9),
            memory / 1048576.0 / (pyramid.scalingTime() * 1e-9),
            pyramid.currentTileRowCacheSize(),
            pyramid.cacheWriteHitCounter(), pyramid.cacheWriteMissCounter(),
            pyramid.cacheReadHitCounter(), pyramid.cacheReadMissCounter(),
            pyramid.extraTileScalingCounter(),
            pyramid.partialTileScalingCounter()
        );
        if (Arrays.SystemSettings.profilingMode()) {
            if (context.is(StatusUpdater.class)) {
                context.as(StatusUpdater.class).updateStatus(msg);
            }
        }
        LOGGER.config(msg);
        return pyramid;
    }

    public static PlanePyramidSource getPyramidSource(
        Context context, // usually null
        String pyramidPath,
        String mode)
        throws IOException, JSONException
    {
        ArrayContext ac = context == null ? null : new DefaultArrayContext(context);
        File path = new File(pyramidPath);
        if (!path.exists()) {
            throw new FileNotFoundException("No file or directory " + path.getAbsolutePath());
        }
        if (new File(path, "index.json").exists()) {
            return PlanePyramidContainer.open(null, path).toPlanePyramidSource(context);
        }
        SourceFormat sourceFormat = null;
        for (SourceFormat format : SourceFormat.values()) {
            if (mode.contains(format.requiredModeSubString)) {
                sourceFormat = format;
            }
        }
        File f = path;
        if (sourceFormat == null) {
            search:
            for (SourceFormat format : SourceFormat.values()) {
                for (String fileName : format.requiredFileNames) {
                    File g;
                    if (path.getName().equalsIgnoreCase(fileName)) {
                        sourceFormat = format;
                        break search;
                    } else if ((g = new File(path, fileName)).exists()) {
                        f = g;
                        sourceFormat = format;
                        break search;
                    }
                }
            }
        }
        if (sourceFormat == null) {
            throw new FileNotFoundException("Cannot detect format of " + path
                + ": no required files or mode specification found");
        }

        JSONObject modeJson;
        try {
            modeJson = new JSONObject(mode);
        } catch (JSONException e) {
            modeJson = new JSONObject();
        }
        switch (sourceFormat) {
            case ImageIO:
                final boolean dicom = modeJson.optBoolean("DICOM", false);
                ImageIOPlanePyramidSource.ImageIOReadingBehaviour behaviour;
                if (dicom) {
                    try {
                        behaviour = (ImageIOPlanePyramidSource.ImageIOReadingBehaviour) Class.forName(
                            "com.simagis.pyramid.imageio.dicom.server.DICOMImageIOReadingBehaviour").newInstance();
                    } catch (Exception e) {
                        throw new IOException(e);
                    }
                } else {
                    behaviour = new ImageIOPlanePyramidSource.ImageIOReadingBehaviour();
                }
                if (modeJson.has("imageIndex")) {
                    behaviour.setImageIndex(modeJson.getInt("imageIndex"));
                }
                if (modeJson.has("addAlphaWhenExist")) {
                    behaviour.setAddAlphaWhenExist(modeJson.getBoolean("addAlphaWhenExist"));
                }
                if (modeJson.has("readPixelValuesViaColorModel")) {
                    behaviour.setReadPixelValuesViaColorModel(modeJson.getBoolean("readPixelValuesViaColorModel"));
                }
                if (modeJson.has("readPixelValuesViaGraphics2D")) {
                    behaviour.setReadPixelValuesViaGraphics2D(modeJson.getBoolean("readPixelValuesViaGraphics2D"));
                }
                if (dicom) {
                    JSONObject cw = modeJson.optJSONObject("cw");
                    try {
                        if (modeJson.has("rawRasterForMonochrome")) {
                            behaviour.getClass().getMethod("setRawRasterForMonochrome", boolean.class).invoke(
                                behaviour, modeJson.getBoolean("rawRasterForMonochrome"));
                        }
                        if (cw != null && cw.has("autoWindowing")) {
                            behaviour.getClass().getMethod("setAutoWindowing", boolean.class).invoke(
                                behaviour, cw.getBoolean("autoWindowing"));
                        }
                        if (cw != null && cw.has("center")) {
                            behaviour.getClass().getMethod("setCenter", double.class).invoke(
                                behaviour, cw.getDouble("center"));
                        }
                        if (cw != null && cw.has("width")) {
                            behaviour.getClass().getMethod("setWidth", double.class).invoke(
                                behaviour, cw.getDouble("width"));
                        }
                    } catch (Exception e) {
                        throw new IOException(e);
                    }
                }
                ImageIOPlanePyramidSource source = new ImageIOPlanePyramidSource(
                    context,
                    mode.contains("CACHE") ?
                        new File(f.getParent(), f.getName() + ".ppsImageIOCache_" + behaviour.getImageIndex()) :
                        null,
                    f,
                    behaviour
                );
                return source;
            case SWS:
                try {
                    Class<?> sourceClass = Class.forName("com.simagis.pyramid.sws.SWSPlanePyramidSource");
                    Constructor<?> constructor = sourceClass.getConstructor(
                        ArrayContext.class, File.class, boolean.class);
                    PlanePyramidSource result = (PlanePyramidSource) constructor.newInstance(
                        ac, f, modeJson.optBoolean("onSlide", false));
                    if (modeJson.has("backgroundColor")) {
                        sourceClass.getMethod("setBackgroundColor", Color.class).invoke(
                            result, Color.decode(modeJson.getString("backgroundColor")));
                    }
                    if (modeJson.has("preload")) {
                        sourceClass.getMethod("setPreload", boolean.class).invoke(
                            result, Boolean.parseBoolean(modeJson.getString("preload")));
                    }
                    if (modeJson.has("multithreadingPreload")) {
                        sourceClass.getMethod("setMultithreadingPreload", boolean.class).invoke(
                            result, Boolean.parseBoolean(modeJson.getString("multithreadingPreload")));
                    }
                    if (modeJson.has("dataBorderColor")) {
                        sourceClass.getMethod("setDataBorderColor", Color.class).invoke(
                            result, Color.decode(modeJson.getString("dataBorderColor")));
                    }
                    if (modeJson.has("dataBorderWidth")) {
                        sourceClass.getMethod("setDataBorderWidth", int.class).invoke(
                            result, modeJson.getInt("dataBorderWidth"));
                    }
                    return result;
                } catch (InvocationTargetException e) {
                    if (e.getCause() instanceof IOException) {
                        throw (IOException) e.getCause();
                    }
                    throw new IOException(e);
                } catch (Exception e) {
                    throw new IOException(e);
                }
            case SVS:
                try {
                    Class<?> sourceClass = Class.forName(
                        modeJson.optBoolean("legacyVersion") ?
                            "com.simagis.live.tms.SVSPlanePyramidSource" :
                            "com.simagis.pyramid.aperio.SVSPlanePyramidSource");
                    Constructor<?> constructor = sourceClass.getConstructor(ArrayContext.class, File.class);
                    return (PlanePyramidSource) constructor.newInstance(ac, f);
                } catch (InvocationTargetException e) {
                    if (e.getCause() instanceof IOException) {
                        throw (IOException) e.getCause();
                    }
                    throw new IOException(e);
                } catch (Exception e) {
                    throw new IOException(e);
                }
            case SCN:
                Long requiredZ = modeJson.has("z") ? modeJson.getLong("z") : null;
                try {
                    Class<?> sourceClass = Class.forName("com.simagis.live.tms.SCNPlanePyramidSource");
                    Constructor<?> constructor = sourceClass.getConstructor(
                        ArrayContext.class, File.class, int.class, Long.class);
                    return (PlanePyramidSource) constructor.newInstance(
                        ac, f, 0, requiredZ); // 0: using default compression
                } catch (InvocationTargetException e) {
                    if (e.getCause() instanceof IOException) {
                        throw (IOException) e.getCause();
                    }
                    throw new IOException(e);
                } catch (Exception e) {
                    throw new IOException(e);
                }
            case BIF:
                try {
                    Class<?> sourceClass = Class.forName("com.simagis.live.tms.BIFPlanePyramidSource");
                    Constructor<?> constructor = sourceClass.getConstructor(ArrayContext.class, File.class, int.class);
                    return (PlanePyramidSource) constructor.newInstance(ac, f, 0); // 0: auto-detecting compression
                } catch (InvocationTargetException e) {
                    if (e.getCause() instanceof IOException) {
                        throw (IOException) e.getCause();
                    }
                    throw new IOException(e);
                } catch (Exception e) {
                    throw new IOException(e);
                }
            case VSI:
                Integer requiredPyramidIndex = modeJson.has("pyramidIndex") ? modeJson.getInt("pyramidIndex") : null;
                int compression = modeJson.has("compression") ? modeJson.getInt("compression") : 0;
                try {
                    Class<?> sourceClass = Class.forName("com.simagis.pyramid.olympus.VSIPlanePyramidSource");
                    Constructor<?> constructor = sourceClass.getConstructor(
                        ArrayContext.class, File.class, int.class, Integer.class);
                    PlanePyramidSource result = (PlanePyramidSource) constructor.newInstance(
                        ac, f, compression, requiredPyramidIndex);
                    if (modeJson.has("layerBorderColor")) {
                        sourceClass.getMethod("setLayerBorderColor", Color.class).invoke(
                            result, Color.decode(modeJson.getString("layerBorderColor")));
                    }
                    if (modeJson.has("layerBorderWidth")) {
                        sourceClass.getMethod("setLayerBorderWidth", int.class).invoke(
                            result, modeJson.getInt("layerBorderWidth"));
                    }
                    return result;
                } catch (InvocationTargetException e) {
                    if (e.getCause() instanceof IOException) {
                        throw (IOException) e.getCause();
                    }
                    throw new IOException(e);
                } catch (Exception e) {
                    throw new IOException(e);
                }
            case NDPRead:
                try {
                    Class<?> sourceClass = Class.forName("com.simagis.pyramid.ndpread.NDPReadPlanePyramidSource");
                    Constructor<?> constructor = sourceClass.getConstructor(
                        ArrayContext.class, File.class, boolean.class);
                    PlanePyramidSource result = (PlanePyramidSource) constructor.newInstance(
                        ac, f, modeJson.optBoolean("onSlide", false));
                    if (modeJson.has("combiningBorderColor")) {
                        sourceClass.getMethod("setCombiningBorderColor", Color.class).invoke(
                            result, Color.decode(modeJson.getString("combiningBorderColor")));
                    }
                    if (modeJson.has("combiningBorderWidth")) {
                        sourceClass.getMethod("setCombiningBorderWidth", int.class).invoke(
                            result, modeJson.getInt("combiningBorderWidth"));
                    }
                    return result;
                } catch (InvocationTargetException e) {
                    if (e.getCause() instanceof IOException) {
                        throw (IOException) e.getCause();
                    }
                    throw new IOException(e);
                } catch (Exception e) {
                    throw new IOException(e);
                }
            case HisTech:
                try {
                    Class<?> sourceClass = Class.forName("com.simagis.pyramid.histech.HisTechPlanePyramidSource");
                    Constructor<?> constructor = sourceClass.getConstructor(
                        ArrayContext.class, File.class, boolean.class);
                    return (PlanePyramidSource) constructor.newInstance(
                        ac, f, mode.contains("ROI"));
                } catch (InvocationTargetException e) {
                    if (e.getCause() instanceof IOException) {
                        throw (IOException) e.getCause();
                    }
                    throw new IOException(e);
                } catch (Exception e) {
                    throw new IOException(e);
                }
            case Phillips:
                try {
                    Class<?> sourceClass = Class.forName("com.simagis.pyramid.phillips.PhillipsPlanePyramidSource");
                    Constructor<?> constructor = sourceClass.getConstructor(
                        ArrayContext.class, File.class, boolean.class);
                    final PlanePyramidSource result = (PlanePyramidSource) constructor.newInstance(
                        ac, f, modeJson.optBoolean("onSlide", false));
                    if (modeJson.has("wholeSlideForLowResolutions")) {
                        sourceClass.getMethod("setWholeSlideForLowResolutions", boolean.class).invoke(
                            result, modeJson.getBoolean("wholeSlideForLowResolutions"));
                    }
                    if (modeJson.has("transparencyWithoutRoi")) {
                        Class<?> modeClass = Class.forName(sourceClass.getName() + "$TransparencyMode");
                        final Field field = modeClass.getDeclaredField(modeJson.optString("transparencyWithoutRoi"));
                        final Object value = field.get(null);
                        sourceClass.getMethod("setTransparencyWithoutRoi", modeClass).invoke(result, value);
                    }
                    if (modeJson.has("transparencyWithRoi")) {
                        Class<?> modeClass = Class.forName(sourceClass.getName() + "$TransparencyMode");
                        final Field field = modeClass.getDeclaredField(modeJson.optString("transparencyWithRoi"));
                        final Object value = field.get(null);
                        sourceClass.getMethod("setTransparencyWithRoi", modeClass).invoke(result, value);
                    }
                    if (modeJson.has("roiUsedForTransparency")) {
                        sourceClass.getMethod("setRoiUsedForTransparency", boolean.class).invoke(
                            result, modeJson.getBoolean("roiUsedForTransparency"));
                    }
                    return result;
                } catch (InvocationTargetException e) {
                    if (e.getCause() instanceof IOException) {
                        throw (IOException) e.getCause();
                    }
                    throw new IOException(e);
                } catch (Exception e) {
                    throw new IOException(e);
                }
            case REMOTE:
                compression = modeJson.has("compression") ? modeJson.getInt("compression") : 0;
                try {
                    Class<?> sourceClass = Class.forName("com.simagis.pyramid.rmi.RMIBasedPlanePyramidSource");
                    Constructor<?> constructor = sourceClass.getConstructor(
                        ArrayContext.class, File.class, int.class);
                    return (PlanePyramidSource) constructor.newInstance(
                        ac, f, compression);
                } catch (InvocationTargetException e) {
                    if (e.getCause() instanceof IOException) {
                        throw (IOException) e.getCause();
                    }
                    throw new IOException(e);
                } catch (Exception e) {
                    throw new IOException(e);
                }
            default:
                throw new AssertionError("Unsupported format " + sourceFormat);
        }
    }

    static PlanePyramid getPyramid(
        Context context, // usually null
        String pyramidPaths,
        String mode)
        throws IOException, JSONException
    {
        return pyramidPaths.contains("|") ?
            getPyramid(context, pyramidPaths.split("\\|"), mode) :
            getPyramid(context, new String[] {pyramidPaths}, mode);
    }

    static PlanePyramid getPyramid(
        Context context, // usually null
        String[] pyramidPaths,
        String mode)
        throws IOException, JSONException
    {
        PlanePyramidContainer container = null;
        PlanePyramidSource combinedSource = null;
        DelayedPlanePyramidSource debuggingDelayedSource = null; // DEBUGGING
        PlanePyramidSource debuggingParentSourceForDelay = null; // DEBUGGING
        JSONObject modeJson;
        try {
            modeJson = new JSONObject(mode);
        } catch (JSONException e) {
            modeJson = new JSONObject();
        }
        for (String pyramidPath : pyramidPaths) {
            File path = new File(pyramidPath);
            if (!path.exists()) {
                throw new FileNotFoundException("No file or directory " + path.getAbsolutePath());
            }
            PlanePyramidSource source;
            if (new File(path, "index.json").exists() && container == null) {
                container = PlanePyramidContainer.open(null, path);
                source = container.toPlanePyramidSource(context);
                // let's remember the first container
            } else {
                source = getPyramidSource(context, pyramidPath, mode);
            }
            if (source instanceof AbstractPlanePyramidSource) {
                if (modeJson.has("skipCoarseData")) {
                    ((AbstractPlanePyramidSource) source).setSkipCoarseData(modeJson.getBoolean("skipCoarseData"));
                }
                if (modeJson.has("skippingFiller")) {
                    ((AbstractPlanePyramidSource) source).setSkippingFiller(modeJson.getDouble("skippingFiller"));
                }
                if (modeJson.has("labelRotation")) {
                    final int rotation = modeJson.optInt("labelRotation", 0);
                    ((AbstractPlanePyramidSource) source).setLabelRotation(
                        RotatingPlanePyramidSource.RotationMode.valueOf(rotation));
                }
                if (modeJson.has("tileCacheDirection")) {
                    ((AbstractPlanePyramidSource) source).enableTileCaching(
                        AbstractPlanePyramidSource.TileDirection.valueOf(modeJson.getString("tileCacheDirection")));
                    LOGGER.config("Enabling tile caching for direction "
                        + ((AbstractPlanePyramidSource) source).getTileCacheDirection());
                } else {
                    LOGGER.config("Tile caching is "
                        + (((AbstractPlanePyramidSource) source).isTileCachingEnabled() ? "" : "not ") + "enabled");
                }
            }
            if (DEBUG_DELAYED_SOURCE) {
                if (combinedSource != null) {
                    debuggingParentSourceForDelay = source;
                    source = debuggingDelayedSource = DelayedPlanePyramidSource.newInstance(
                        combinedSource.dimensions(0),
                        PlanePyramid.DEFAULT_COMPRESSION,
                        source.getResolutionLevelsAvailability());
                }
            }
            if (modeJson.has("rotation")) {
                for (RotatingPlanePyramidSource.RotationMode rm : RotatingPlanePyramidSource.RotationMode.values()) {
                    if (modeJson.getString("rotation").equals(rm.name())) {
                        container = null; // meta-information becomes incorrect after rotations
                        // Note: as a result, information about background is also lost
                        source = RotatingPlanePyramidSource.newInstance(source, rm);
                        break;
                    }
                }
            }
            String extendingArguments = modeJson.optString("extending", null);
            if (extendingArguments != null) {
                String[] numbers = extendingArguments.split("[, ]+");
                if (numbers.length < 5) {
                    throw new NumberFormatException("Syntax error: >=5 values are expected after EXTENDING:\"");
                }
                long[] a = new long[4];
                for (int k = 0; k < 4; k++) {
                    a[k] = Long.parseLong(numbers[k]);
                }
                double[] backgroundColor = new double[numbers.length - 4];
                for (int k = 0; k < backgroundColor.length; k++) {
                    backgroundColor[k] = Double.parseDouble(numbers[4 + k]);
                }
                ArrayContext ac = context == null ? null : new DefaultArrayContext(context);
                source = ExtendingPlanePyramidSource.newInstance(ac, source,
                    a[0], a[1], a[2], a[3], backgroundColor);
                if (modeJson.has("extendingBorderColor")) {
                    ((ExtendingPlanePyramidSource) source).setExtendingBorderColor(
                        Color.decode(modeJson.getString("extendingBorderColor")));
                }
                if (modeJson.has("extendingBorderWidth")) {
                    ((ExtendingPlanePyramidSource) source).setExtendingBorderWidth(
                        modeJson.getInt("extendingBorderWidth"));
                }
            }
            combinedSource = CombinedPlanePyramidSource.newInstance(combinedSource, source);
        }
        if (combinedSource == null) {
            throw new IllegalArgumentException("Empty list of pyramid paths");
        }
        PlanePyramid result = null;
        if (container != null) {
            // let's try to use the container of our format: maybe, it has additional meta-information
            try {
                result = container.toPlanePyramid(combinedSource);
            } catch (JSONException e) {
                // "index.json" is not built by PlanePyramid and does not contain necessary fields
            }
        }
        if (result == null) {
            result = PlanePyramid.valueOf(combinedSource, null, combinedSource.compression(),
                157, 157, new double[combinedSource.bandCount()]);
        }
//            customSource.freeResources(false); // - reduces speed for little images (debugging)
        if (debuggingDelayedSource != null) {
            debuggingDelayedSource.setParent(debuggingParentSourceForDelay); // - comment it to view exception
        }
//        System.out.println(result.elementType()); // forcing access to pyramid data
        return result;
    }

    static Image2D getImage(
        Context context, // should not be null, excepting the goals of debugging
        PlanePyramid pyramid,
        long fromX,
        long fromY,
        long toX,
        long toY,
        double compression)
    {
        ArrayContext ac = context == null ? null : new DefaultArrayContext(context);
        long t1 = System.nanoTime(), t1Dim = Long.MIN_VALUE, t2Dim = Long.MIN_VALUE;
        Matrix<? extends PArray> m;
        if (compression == WHOLE_SLIDE_CODE) {
            m = pyramid.readSpecialMatrix(PlanePyramidSource.SpecialImageKind.WHOLE_SLIDE);
        } else if (compression == MAP_IMAGE_CODE) {
            m = pyramid.readSpecialMatrix(PlanePyramidSource.SpecialImageKind.MAP_IMAGE);
        } else if (compression == LABEL_ONLY_IMAGE_CODE) {
            m = pyramid.readSpecialMatrix(PlanePyramidSource.SpecialImageKind.LABEL_ONLY_IMAGE);
        } else if (compression == THUMBNAIL_IMAGE_CODE) {
            m = pyramid.readSpecialMatrix(PlanePyramidSource.SpecialImageKind.THUMBNAIL_IMAGE);
        } else if (compression == CUSTOM_KIND_1_CODE) {
            m = pyramid.readSpecialMatrix(PlanePyramidSource.SpecialImageKind.CUSTOM_KIND_1);
        } else if (compression == CUSTOM_KIND_2_CODE) {
            m = pyramid.readSpecialMatrix(PlanePyramidSource.SpecialImageKind.CUSTOM_KIND_2);
        } else {
            m = pyramid.readImage(ac == null ? null : ac.part(0.0, 0.8), fromX, fromY, toX, toY, compression);
            t1Dim = System.nanoTime();
            long[] imageDimensions = pyramid.getImageDimensions(fromX, fromY, toX, toY, compression);
            t2Dim = System.nanoTime();
            if (!m.dimEquals(imageDimensions)) {
                throw new AssertionError("Internal bug in PlanePyramid: dimensions "
                    + JArrays.toString(imageDimensions, "x", 100) + " for " + m);
            }
        }
        long t2 = System.nanoTime();
        List<Matrix<? extends PArray>> matrices = ImageConversions.unpack2DBandsFromSequentialSamples(
            ac == null ? null : ac.part(0.8, 1.0), m);
        long t3 = System.nanoTime();
        if (context == null) {
            context = new SimpleImageContext();
        }
        Image2D result = context.as(ImageContext.class).newImage2D(context, matrices);
        long t4 = System.nanoTime();
        String msg = String.format(Locale.US,
            "Image loaded in %.5f s (+%.5f conversion into Image2D, +%.5f saving results); "
                + "reading speed %.5f MB/s",
            (t2 - t1) * 1e-9, (t3 - t2) * 1e-9, (t4 - t3) * 1e-9,
            Matrices.sizeOf(matrices) / 1048576.0 / ((t2 - t1) * 1e-9)
        )
            + (t1Dim == Long.MIN_VALUE ? "" : " (detecting dimensions only " + (t2Dim - t1Dim) + " ns)");
        if (Arrays.SystemSettings.profilingMode()) {
            if (context.is(StatusUpdater.class)) {
                context.as(StatusUpdater.class).updateStatus(msg);
            }
        }
        LOGGER.config(msg);
        return result;
    }
}
