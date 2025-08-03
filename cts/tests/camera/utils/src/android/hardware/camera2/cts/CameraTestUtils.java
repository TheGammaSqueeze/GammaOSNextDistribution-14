/*
 * Copyright 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.hardware.camera2.cts;

import static org.mockito.Mockito.*;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ColorSpace;
import android.graphics.Gainmap;
import android.graphics.ImageFormat;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraConstrainedHighSpeedCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.MultiResolutionImageReader;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.cts.helpers.CameraErrorCollector;
import android.hardware.camera2.cts.helpers.StaticMetadata;
import android.hardware.camera2.params.DynamicRangeProfiles;
import android.hardware.camera2.params.InputConfiguration;
import android.hardware.camera2.params.MandatoryStreamCombination.MandatoryStreamInformation;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.MultiResolutionStreamConfigurationMap;
import android.hardware.camera2.params.MultiResolutionStreamInfo;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.cts.helpers.CameraUtils;
import android.location.Location;
import android.location.LocationManager;
import android.media.CamcorderProfile;
import android.media.ExifInterface;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.ImageWriter;
import android.os.Build;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import android.view.WindowManager;
import android.view.WindowMetrics;

import androidx.annotation.NonNull;

import com.android.ex.camera2.blocking.BlockingCameraManager.BlockingOpenException;
import com.android.ex.camera2.blocking.BlockingSessionCallback;
import com.android.ex.camera2.blocking.BlockingStateCallback;
import com.android.ex.camera2.exceptions.TimeoutRuntimeException;

import junit.framework.Assert;

import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A package private utility class for wrapping up the camera2 cts test common utility functions
 */
public class CameraTestUtils extends Assert {
    private static final String TAG = "CameraTestUtils";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    public static final Size SIZE_BOUND_720P = new Size(1280, 720);
    public static final Size SIZE_BOUND_1080P = new Size(1920, 1088);
    public static final Size SIZE_BOUND_2K = new Size(2048, 1088);
    public static final Size SIZE_BOUND_QHD = new Size(2560, 1440);
    public static final Size SIZE_BOUND_2160P = new Size(3840, 2160);
    // Only test the preview size that is no larger than 1080p.
    public static final Size PREVIEW_SIZE_BOUND = SIZE_BOUND_1080P;
    // Default timeouts for reaching various states
    public static final int CAMERA_OPEN_TIMEOUT_MS = 3000;
    public static final int CAMERA_CLOSE_TIMEOUT_MS = 3000;
    public static final int CAMERA_IDLE_TIMEOUT_MS = 3000;
    public static final int CAMERA_ACTIVE_TIMEOUT_MS = 1000;
    public static final int CAMERA_BUSY_TIMEOUT_MS = 1000;
    public static final int CAMERA_UNCONFIGURED_TIMEOUT_MS = 1000;
    public static final int CAMERA_CONFIGURE_TIMEOUT_MS = 3000;
    public static final int CAPTURE_RESULT_TIMEOUT_MS = 3000;
    public static final int CAPTURE_IMAGE_TIMEOUT_MS = 3000;

    public static final int SESSION_CONFIGURE_TIMEOUT_MS = 3000;
    public static final int SESSION_CLOSE_TIMEOUT_MS = 3000;
    public static final int SESSION_READY_TIMEOUT_MS = 5000;
    public static final int SESSION_ACTIVE_TIMEOUT_MS = 1000;

    public static final int MAX_READER_IMAGES = 5;

    public static final int INDEX_ALGORITHM_AE = 0;
    public static final int INDEX_ALGORITHM_AWB = 1;
    public static final int INDEX_ALGORITHM_AF = 2;
    public static final int NUM_ALGORITHMS = 3; // AE, AWB and AF

    // Compensate for the loss of "sensitivity" and "sensitivityBoost"
    public static final int MAX_ISO_MISMATCH = 3;

    public static final String OFFLINE_CAMERA_ID = "offline_camera_id";
    public static final String REPORT_LOG_NAME = "CtsCameraTestCases";

    private static final int EXIF_DATETIME_LENGTH = 19;
    private static final int EXIF_DATETIME_ERROR_MARGIN_SEC = 60;
    private static final float EXIF_FOCAL_LENGTH_ERROR_MARGIN = 0.001f;
    private static final float EXIF_EXPOSURE_TIME_ERROR_MARGIN_RATIO = 0.05f;
    private static final float EXIF_EXPOSURE_TIME_MIN_ERROR_MARGIN_SEC = 0.002f;
    private static final float EXIF_APERTURE_ERROR_MARGIN = 0.001f;

    private static final float ZOOM_RATIO_THRESHOLD = 0.01f;

    // Set such that 1920x1080 and 1920x1088 be treated as the same aspect ratio.
    private static final float ASPECT_RATIO_MATCH_THRESHOLD = 0.014f;

    private static final int AVAILABILITY_TIMEOUT_MS = 10;

    private static final Location sTestLocation0 = new Location(LocationManager.GPS_PROVIDER);
    private static final Location sTestLocation1 = new Location(LocationManager.GPS_PROVIDER);
    private static final Location sTestLocation2 = new Location(LocationManager.NETWORK_PROVIDER);

    static {
        sTestLocation0.setTime(1199145600000L);
        sTestLocation0.setLatitude(37.736071);
        sTestLocation0.setLongitude(-122.441983);
        sTestLocation0.setAltitude(21.0);

        sTestLocation1.setTime(1199145601000L);
        sTestLocation1.setLatitude(0.736071);
        sTestLocation1.setLongitude(0.441983);
        sTestLocation1.setAltitude(1.0);

        sTestLocation2.setTime(1199145602000L);
        sTestLocation2.setLatitude(-89.736071);
        sTestLocation2.setLongitude(-179.441983);
        sTestLocation2.setAltitude(100000.0);
    }

    // Exif test data vectors.
    public static final ExifTestData[] EXIF_TEST_DATA = {
            new ExifTestData(
                    /*gpsLocation*/ sTestLocation0,
                    /* orientation */90,
                    /* jpgQuality */(byte) 80,
                    /* thumbQuality */(byte) 75),
            new ExifTestData(
                    /*gpsLocation*/ sTestLocation1,
                    /* orientation */180,
                    /* jpgQuality */(byte) 90,
                    /* thumbQuality */(byte) 85),
            new ExifTestData(
                    /*gpsLocation*/ sTestLocation2,
                    /* orientation */270,
                    /* jpgQuality */(byte) 100,
                    /* thumbQuality */(byte) 80)
    };

    /**
     * Create an {@link android.media.ImageReader} object and get the surface.
     *
     * @param size The size of this ImageReader to be created.
     * @param format The format of this ImageReader to be created
     * @param maxNumImages The max number of images that can be acquired simultaneously.
     * @param listener The listener used by this ImageReader to notify callbacks.
     * @param handler The handler to use for any listener callbacks.
     */
    public static ImageReader makeImageReader(Size size, int format, int maxNumImages,
            ImageReader.OnImageAvailableListener listener, Handler handler) {
        ImageReader reader;
        reader = ImageReader.newInstance(size.getWidth(), size.getHeight(), format,
                maxNumImages);
        reader.setOnImageAvailableListener(listener, handler);
        if (VERBOSE) Log.v(TAG, "Created ImageReader size " + size);
        return reader;
    }

    /**
     * Create an ImageWriter and hook up the ImageListener.
     *
     * @param inputSurface The input surface of the ImageWriter.
     * @param maxImages The max number of Images that can be dequeued simultaneously.
     * @param listener The listener used by this ImageWriter to notify callbacks
     * @param handler The handler to post listener callbacks.
     * @return ImageWriter object created.
     */
    public static ImageWriter makeImageWriter(
            Surface inputSurface, int maxImages,
            ImageWriter.OnImageReleasedListener listener, Handler handler) {
        ImageWriter writer = ImageWriter.newInstance(inputSurface, maxImages);
        writer.setOnImageReleasedListener(listener, handler);
        return writer;
    }

    /**
     * Utility class to store the targets for mandatory stream combination test.
     */
    public static class StreamCombinationTargets {
        public List<SurfaceTexture> mPrivTargets = new ArrayList<>();
        public List<ImageReader> mJpegTargets = new ArrayList<>();
        public List<ImageReader> mYuvTargets = new ArrayList<>();
        public List<ImageReader> mY8Targets = new ArrayList<>();
        public List<ImageReader> mRawTargets = new ArrayList<>();
        public List<ImageReader> mHeicTargets = new ArrayList<>();
        public List<ImageReader> mDepth16Targets = new ArrayList<>();
        public List<ImageReader> mP010Targets = new ArrayList<>();


        public List<MultiResolutionImageReader> mPrivMultiResTargets = new ArrayList<>();
        public List<MultiResolutionImageReader> mJpegMultiResTargets = new ArrayList<>();
        public List<MultiResolutionImageReader> mYuvMultiResTargets = new ArrayList<>();
        public List<MultiResolutionImageReader> mRawMultiResTargets = new ArrayList<>();

        public void close() {
            for (SurfaceTexture target : mPrivTargets) {
                target.release();
            }
            for (ImageReader target : mJpegTargets) {
                target.close();
            }
            for (ImageReader target : mYuvTargets) {
                target.close();
            }
            for (ImageReader target : mY8Targets) {
                target.close();
            }
            for (ImageReader target : mRawTargets) {
                target.close();
            }
            for (ImageReader target : mHeicTargets) {
                target.close();
            }
            for (ImageReader target : mDepth16Targets) {
                target.close();
            }
            for (ImageReader target : mP010Targets) {
                target.close();
            }

            for (MultiResolutionImageReader target : mPrivMultiResTargets) {
                target.close();
            }
            for (MultiResolutionImageReader target : mJpegMultiResTargets) {
                target.close();
            }
            for (MultiResolutionImageReader target : mYuvMultiResTargets) {
                target.close();
            }
            for (MultiResolutionImageReader target : mRawMultiResTargets) {
                target.close();
            }
        }
    }

    private static void configureTarget(StreamCombinationTargets targets,
            List<OutputConfiguration> outputConfigs, List<Surface> outputSurfaces,
            int format, Size targetSize, int numBuffers, String overridePhysicalCameraId,
            MultiResolutionStreamConfigurationMap multiResStreamConfig,
            boolean createMultiResiStreamConfig, ImageDropperListener listener, Handler handler,
            long dynamicRangeProfile, long streamUseCase) {
        if (createMultiResiStreamConfig) {
            Collection<MultiResolutionStreamInfo> multiResolutionStreams =
                    multiResStreamConfig.getOutputInfo(format);
            MultiResolutionImageReader multiResReader = new MultiResolutionImageReader(
                    multiResolutionStreams, format, numBuffers);
            multiResReader.setOnImageAvailableListener(listener, new HandlerExecutor(handler));
            Collection<OutputConfiguration> configs =
                    OutputConfiguration.createInstancesForMultiResolutionOutput(multiResReader);
            outputConfigs.addAll(configs);
            outputSurfaces.add(multiResReader.getSurface());
            switch (format) {
                case ImageFormat.PRIVATE:
                    targets.mPrivMultiResTargets.add(multiResReader);
                    break;
                case ImageFormat.JPEG:
                    targets.mJpegMultiResTargets.add(multiResReader);
                    break;
                case ImageFormat.YUV_420_888:
                    targets.mYuvMultiResTargets.add(multiResReader);
                    break;
                case ImageFormat.RAW_SENSOR:
                    targets.mRawMultiResTargets.add(multiResReader);
                    break;
                default:
                    fail("Unknown/Unsupported output format " + format);
            }
        } else {
            if (format == ImageFormat.PRIVATE) {
                SurfaceTexture target = new SurfaceTexture(/*random int*/1);
                target.setDefaultBufferSize(targetSize.getWidth(), targetSize.getHeight());
                OutputConfiguration config = new OutputConfiguration(new Surface(target));
                if (overridePhysicalCameraId != null) {
                    config.setPhysicalCameraId(overridePhysicalCameraId);
                }
                config.setDynamicRangeProfile(dynamicRangeProfile);
                config.setStreamUseCase(streamUseCase);
                outputConfigs.add(config);
                outputSurfaces.add(config.getSurface());
                targets.mPrivTargets.add(target);
            } else {
                ImageReader target = ImageReader.newInstance(targetSize.getWidth(),
                        targetSize.getHeight(), format, numBuffers);
                target.setOnImageAvailableListener(listener, handler);
                OutputConfiguration config = new OutputConfiguration(target.getSurface());
                if (overridePhysicalCameraId != null) {
                    config.setPhysicalCameraId(overridePhysicalCameraId);
                }
                config.setDynamicRangeProfile(dynamicRangeProfile);
                config.setStreamUseCase(streamUseCase);
                outputConfigs.add(config);
                outputSurfaces.add(config.getSurface());

                switch (format) {
                    case ImageFormat.JPEG:
                      targets.mJpegTargets.add(target);
                      break;
                    case ImageFormat.YUV_420_888:
                      targets.mYuvTargets.add(target);
                      break;
                    case ImageFormat.Y8:
                      targets.mY8Targets.add(target);
                      break;
                    case ImageFormat.RAW_SENSOR:
                      targets.mRawTargets.add(target);
                      break;
                    case ImageFormat.HEIC:
                      targets.mHeicTargets.add(target);
                      break;
                    case ImageFormat.DEPTH16:
                      targets.mDepth16Targets.add(target);
                      break;
                    case ImageFormat.YCBCR_P010:
                      targets.mP010Targets.add(target);
                      break;
                    default:
                      fail("Unknown/Unsupported output format " + format);
                }
            }
        }
    }

    public static void setupConfigurationTargets(List<MandatoryStreamInformation> streamsInfo,
            StreamCombinationTargets targets,
            List<OutputConfiguration> outputConfigs,
            List<Surface> outputSurfaces, int numBuffers,
            boolean substituteY8, boolean substituteHeic, String overridenPhysicalCameraId,
            MultiResolutionStreamConfigurationMap multiResStreamConfig, Handler handler) {
            List<Surface> uhSurfaces = new ArrayList<Surface>();
        setupConfigurationTargets(streamsInfo, targets, outputConfigs, outputSurfaces, uhSurfaces,
            numBuffers, substituteY8, substituteHeic, overridenPhysicalCameraId,
            multiResStreamConfig, handler);
    }

    public static void setupConfigurationTargets(List<MandatoryStreamInformation> streamsInfo,
            StreamCombinationTargets targets,
            List<OutputConfiguration> outputConfigs,
            List<Surface> outputSurfaces, List<Surface> uhSurfaces, int numBuffers,
            boolean substituteY8, boolean substituteHeic, String overridePhysicalCameraId,
            MultiResolutionStreamConfigurationMap multiResStreamConfig, Handler handler) {
        setupConfigurationTargets(streamsInfo, targets, outputConfigs, outputSurfaces, uhSurfaces,
                numBuffers, substituteY8, substituteHeic, overridePhysicalCameraId,
                multiResStreamConfig, handler, /*dynamicRangeProfiles*/ null);
    }

    public static void setupConfigurationTargets(List<MandatoryStreamInformation> streamsInfo,
            StreamCombinationTargets targets,
            List<OutputConfiguration> outputConfigs,
            List<Surface> outputSurfaces, List<Surface> uhSurfaces, int numBuffers,
            boolean substituteY8, boolean substituteHeic, String overridePhysicalCameraId,
            MultiResolutionStreamConfigurationMap multiResStreamConfig, Handler handler,
            List<Long> dynamicRangeProfiles) {

        Random rnd = new Random();
        // 10-bit output capable streams will use a fixed dynamic range profile in case
        // dynamicRangeProfiles.size() == 1 or random in case dynamicRangeProfiles.size() > 1
        boolean use10BitRandomProfile = (dynamicRangeProfiles != null) &&
                (dynamicRangeProfiles.size() > 1);
        if (use10BitRandomProfile) {
            Long seed = rnd.nextLong();
            Log.i(TAG, "Random seed used for selecting 10-bit output: " + seed);
            rnd.setSeed(seed);
        }
        ImageDropperListener imageDropperListener = new ImageDropperListener();
        List<Surface> chosenSurfaces;
        for (MandatoryStreamInformation streamInfo : streamsInfo) {
            if (streamInfo.isInput()) {
                continue;
            }
            chosenSurfaces = outputSurfaces;
            if (streamInfo.isUltraHighResolution()) {
                chosenSurfaces = uhSurfaces;
            }
            int format = streamInfo.getFormat();
            if (substituteY8 && (format == ImageFormat.YUV_420_888)) {
                format = ImageFormat.Y8;
            } else if (substituteHeic && (format == ImageFormat.JPEG)) {
                format = ImageFormat.HEIC;
            }

            long dynamicRangeProfile = DynamicRangeProfiles.STANDARD;
            if (streamInfo.is10BitCapable() && use10BitRandomProfile) {
                boolean override10bit = rnd.nextBoolean();
                if (!override10bit) {
                    dynamicRangeProfile = dynamicRangeProfiles.get(rnd.nextInt(
                            dynamicRangeProfiles.size()));
                    format = streamInfo.get10BitFormat();
                }
            } else if (streamInfo.is10BitCapable() && (dynamicRangeProfiles != null)) {
                dynamicRangeProfile = dynamicRangeProfiles.get(0);
                format = streamInfo.get10BitFormat();
            }
            Size[] availableSizes = new Size[streamInfo.getAvailableSizes().size()];
            availableSizes = streamInfo.getAvailableSizes().toArray(availableSizes);
            Size targetSize = CameraTestUtils.getMaxSize(availableSizes);
            boolean createMultiResReader =
                    (multiResStreamConfig != null &&
                     !multiResStreamConfig.getOutputInfo(format).isEmpty() &&
                     streamInfo.isMaximumSize());
            switch (format) {
                case ImageFormat.PRIVATE:
                case ImageFormat.JPEG:
                case ImageFormat.YUV_420_888:
                case ImageFormat.YCBCR_P010:
                case ImageFormat.Y8:
                case ImageFormat.HEIC:
                case ImageFormat.DEPTH16:
                {
                    configureTarget(targets, outputConfigs, chosenSurfaces, format,
                            targetSize, numBuffers, overridePhysicalCameraId, multiResStreamConfig,
                            createMultiResReader, imageDropperListener, handler,
                            dynamicRangeProfile, streamInfo.getStreamUseCase());
                    break;
                }
                case ImageFormat.RAW_SENSOR: {
                    // targetSize could be null in the logical camera case where only
                    // physical camera supports RAW stream.
                    if (targetSize != null) {
                        configureTarget(targets, outputConfigs, chosenSurfaces, format,
                                targetSize, numBuffers, overridePhysicalCameraId,
                                multiResStreamConfig, createMultiResReader, imageDropperListener,
                                handler, dynamicRangeProfile, streamInfo.getStreamUseCase());
                    }
                    break;
                }
                default:
                    fail("Unknown output format " + format);
            }
        }
    }

    /**
     * Close pending images and clean up an {@link android.media.ImageReader} object.
     * @param reader an {@link android.media.ImageReader} to close.
     */
    public static void closeImageReader(ImageReader reader) {
        if (reader != null) {
            reader.close();
        }
    }

    /**
     * Close the pending images then close current active {@link ImageReader} objects.
     */
    public static void closeImageReaders(ImageReader[] readers) {
        if ((readers != null) && (readers.length > 0)) {
            for (ImageReader reader : readers) {
                CameraTestUtils.closeImageReader(reader);
            }
        }
    }

    /**
     * Close pending images and clean up an {@link android.media.ImageWriter} object.
     * @param writer an {@link android.media.ImageWriter} to close.
     */
    public static void closeImageWriter(ImageWriter writer) {
        if (writer != null) {
            writer.close();
        }
    }

    /**
     * Placeholder listener that release the image immediately once it is available.
     *
     * <p>
     * It can be used for the case where we don't care the image data at all.
     * </p>
     */
    public static class ImageDropperListener implements ImageReader.OnImageAvailableListener {
        @Override
        public synchronized void onImageAvailable(ImageReader reader) {
            Image image = null;
            try {
                image = reader.acquireNextImage();
            } finally {
                if (image != null) {
                    image.close();
                    mImagesDropped++;
                }
            }
        }

        public synchronized int getImageCount() {
            return mImagesDropped;
        }

        public synchronized void resetImageCount() {
            mImagesDropped = 0;
        }

        private int mImagesDropped = 0;
    }

    /**
     * Image listener that release the image immediately after validating the image
     */
    public static class ImageVerifierListener implements ImageReader.OnImageAvailableListener {
        private Size mSize;
        private int mFormat;
        // Whether the parent ImageReader is valid or not. If the parent ImageReader
        // is destroyed, the acquired Image may become invalid.
        private boolean mReaderIsValid;

        public ImageVerifierListener(Size sz, int format) {
            mSize = sz;
            mFormat = format;
            mReaderIsValid = true;
        }

        public synchronized void onReaderDestroyed() {
            mReaderIsValid = false;
        }

        @Override
        public synchronized void onImageAvailable(ImageReader reader) {
            Image image = null;
            try {
                image = reader.acquireNextImage();
            } finally {
                if (image != null) {
                    // Should only do some quick validity checks in callback, as the ImageReader
                    // could be closed asynchronously, which will close all images acquired from
                    // this ImageReader.
                    checkImage(image, mSize.getWidth(), mSize.getHeight(), mFormat);
                    // checkAndroidImageFormat calls into underlying Image object, which could
                    // become invalid if the ImageReader is destroyed.
                    if (mReaderIsValid) {
                        checkAndroidImageFormat(image);
                    }
                    image.close();
                }
            }
        }
    }

    public static class SimpleImageReaderListener
            implements ImageReader.OnImageAvailableListener {
        private final LinkedBlockingQueue<Image> mQueue =
                new LinkedBlockingQueue<Image>();
        // Indicate whether this listener will drop images or not,
        // when the queued images reaches the reader maxImages
        private final boolean mAsyncMode;
        // maxImages held by the queue in async mode.
        private final int mMaxImages;

        /**
         * Create a synchronous SimpleImageReaderListener that queues the images
         * automatically when they are available, no image will be dropped. If
         * the caller doesn't call getImage(), the producer will eventually run
         * into buffer starvation.
         */
        public SimpleImageReaderListener() {
            mAsyncMode = false;
            mMaxImages = 0;
        }

        /**
         * Create a synchronous/asynchronous SimpleImageReaderListener that
         * queues the images automatically when they are available. For
         * asynchronous listener, image will be dropped if the queued images
         * reach to maxImages queued. If the caller doesn't call getImage(), the
         * producer will not be blocked. For synchronous listener, no image will
         * be dropped. If the caller doesn't call getImage(), the producer will
         * eventually run into buffer starvation.
         *
         * @param asyncMode If the listener is operating at asynchronous mode.
         * @param maxImages The max number of images held by this listener.
         */
        /**
         *
         * @param asyncMode
         */
        public SimpleImageReaderListener(boolean asyncMode, int maxImages) {
            mAsyncMode = asyncMode;
            mMaxImages = maxImages;
        }

        @Override
        public void onImageAvailable(ImageReader reader) {
            try {
                Image imge = reader.acquireNextImage();
                if (imge == null) {
                    return;
                }
                mQueue.put(imge);
                if (mAsyncMode && mQueue.size() >= mMaxImages) {
                    Image img = mQueue.poll();
                    img.close();
                }
            } catch (InterruptedException e) {
                throw new UnsupportedOperationException(
                        "Can't handle InterruptedException in onImageAvailable");
            }
        }

        /**
         * Get an image from the image reader.
         *
         * @param timeout Timeout value for the wait.
         * @return The image from the image reader.
         */
        public Image getImage(long timeout) throws InterruptedException {
            Image image = mQueue.poll(timeout, TimeUnit.MILLISECONDS);
            assertNotNull("Wait for an image timed out in " + timeout + "ms", image);
            return image;
        }

        /**
         * Drain the pending images held by this listener currently.
         *
         */
        public void drain() {
            while (!mQueue.isEmpty()) {
                Image image = mQueue.poll();
                assertNotNull("Unable to get an image", image);
                image.close();
            }
        }
    }

    public static class SimpleImageWriterListener implements ImageWriter.OnImageReleasedListener {
        private final Semaphore mImageReleasedSema = new Semaphore(0);
        private final ImageWriter mWriter;
        @Override
        public void onImageReleased(ImageWriter writer) {
            if (writer != mWriter) {
                return;
            }

            if (VERBOSE) {
                Log.v(TAG, "Input image is released");
            }
            mImageReleasedSema.release();
        }

        public SimpleImageWriterListener(ImageWriter writer) {
            if (writer == null) {
                throw new IllegalArgumentException("writer cannot be null");
            }
            mWriter = writer;
        }

        public void waitForImageReleased(long timeoutMs) throws InterruptedException {
            if (!mImageReleasedSema.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
                fail("wait for image available timed out after " + timeoutMs + "ms");
            }
        }
    }

    public static class ImageAndMultiResStreamInfo {
        public final Image image;
        public final MultiResolutionStreamInfo streamInfo;

        public ImageAndMultiResStreamInfo(Image image, MultiResolutionStreamInfo streamInfo) {
            this.image = image;
            this.streamInfo = streamInfo;
        }
    }

    public static class SimpleMultiResolutionImageReaderListener
            implements ImageReader.OnImageAvailableListener {
        public SimpleMultiResolutionImageReaderListener(MultiResolutionImageReader owner,
                int maxBuffers, boolean acquireLatest) {
            mOwner = owner;
            mMaxBuffers = maxBuffers;
            mAcquireLatest = acquireLatest;
        }

        @Override
        public void onImageAvailable(ImageReader reader) {
            if (VERBOSE) Log.v(TAG, "new image available from reader " + reader.toString());

            if (mAcquireLatest) {
                synchronized (mLock) {
                    // If there is switch of image readers, acquire and releases all images
                    // from the previous image reader
                    if (mLastReader != reader) {
                        if (mLastReader != null) {
                            Image image = mLastReader.acquireLatestImage();
                            if (image != null) {
                                image.close();
                            }
                        }
                        mLastReader = reader;
                    }
                }
                mImageAvailable.open();
            } else {
                if (mQueue.size() < mMaxBuffers) {
                    Image image = reader.acquireNextImage();
                    MultiResolutionStreamInfo multiResStreamInfo =
                            mOwner.getStreamInfoForImageReader(reader);
                    mQueue.offer(new ImageAndMultiResStreamInfo(image, multiResStreamInfo));
                }
            }
        }

        public ImageAndMultiResStreamInfo getAnyImageAndInfoAvailable(long timeoutMs)
                throws Exception {
            if (mAcquireLatest) {
                Image image = null;
                if (mImageAvailable.block(timeoutMs)) {
                    synchronized (mLock) {
                        if (mLastReader != null) {
                            image = mLastReader.acquireLatestImage();
                            if (VERBOSE) Log.v(TAG, "acquireLatestImage from "
                                    + mLastReader.toString() + " produces " + image);
                        } else {
                            fail("invalid image reader");
                        }
                    }
                    mImageAvailable.close();
                } else {
                    fail("wait for image available time out after " + timeoutMs + "ms");
                }
                return image == null ? null : new ImageAndMultiResStreamInfo(image,
                        mOwner.getStreamInfoForImageReader(mLastReader));
            } else {
                ImageAndMultiResStreamInfo imageAndInfo = mQueue.poll(timeoutMs,
                        java.util.concurrent.TimeUnit.MILLISECONDS);
                if (imageAndInfo == null) {
                    fail("wait for image available timed out after " + timeoutMs + "ms");
                }
                return imageAndInfo;
            }
        }

        public void reset() {
            while (!mQueue.isEmpty()) {
                ImageAndMultiResStreamInfo imageAndInfo = mQueue.poll();
                assertNotNull("Acquired image is not valid", imageAndInfo.image);
                imageAndInfo.image.close();
            }
            mImageAvailable.close();
            mLastReader = null;
        }

        private LinkedBlockingQueue<ImageAndMultiResStreamInfo> mQueue =
                new LinkedBlockingQueue<ImageAndMultiResStreamInfo>();
        private final MultiResolutionImageReader mOwner;
        private final int mMaxBuffers;
        private final boolean mAcquireLatest;
        private ConditionVariable mImageAvailable = new ConditionVariable();
        private ImageReader mLastReader = null;
        private final Object mLock = new Object();
    }

    public static class SimpleCaptureCallback extends CameraCaptureSession.CaptureCallback {
        private final LinkedBlockingQueue<TotalCaptureResult> mQueue =
                new LinkedBlockingQueue<TotalCaptureResult>();
        private final LinkedBlockingQueue<CaptureFailure> mFailureQueue =
                new LinkedBlockingQueue<>();
        // (Surface, framenumber) pair for lost buffers
        private final LinkedBlockingQueue<Pair<Surface, Long>> mBufferLostQueue =
                new LinkedBlockingQueue<>();
        private final LinkedBlockingQueue<Integer> mAbortQueue =
                new LinkedBlockingQueue<>();
        // Pair<CaptureRequest, Long> is a pair of capture request and start of exposure timestamp.
        private final LinkedBlockingQueue<Pair<CaptureRequest, Long>> mCaptureStartQueue =
                new LinkedBlockingQueue<>();
        // Pair<CaptureRequest, Long> is a pair of capture request and readout timestamp.
        private final LinkedBlockingQueue<Pair<CaptureRequest, Long>> mReadoutStartQueue =
                new LinkedBlockingQueue<>();
        // Pair<Int, Long> is a pair of sequence id and frame number
        private final LinkedBlockingQueue<Pair<Integer, Long>> mCaptureSequenceCompletedQueue =
                new LinkedBlockingQueue<>();

        private AtomicLong mNumFramesArrived = new AtomicLong(0);

        @Override
        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request,
                long timestamp, long frameNumber) {
            try {
                mCaptureStartQueue.put(new Pair(request, timestamp));
            } catch (InterruptedException e) {
                throw new UnsupportedOperationException(
                        "Can't handle InterruptedException in onCaptureStarted");
            }
        }

        @Override
        public void onReadoutStarted(CameraCaptureSession session, CaptureRequest request,
                long timestamp, long frameNumber) {
            try {
                mReadoutStartQueue.put(new Pair(request, timestamp));
            } catch (InterruptedException e) {
                throw new UnsupportedOperationException(
                        "Can't handle InterruptedException in onReadoutStarted");
            }
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                TotalCaptureResult result) {
            try {
                mNumFramesArrived.incrementAndGet();
                mQueue.put(result);
            } catch (InterruptedException e) {
                throw new UnsupportedOperationException(
                        "Can't handle InterruptedException in onCaptureCompleted");
            }
        }

        @Override
        public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request,
                CaptureFailure failure) {
            try {
                mFailureQueue.put(failure);
            } catch (InterruptedException e) {
                throw new UnsupportedOperationException(
                        "Can't handle InterruptedException in onCaptureFailed");
            }
        }

        @Override
        public void onCaptureSequenceAborted(CameraCaptureSession session, int sequenceId) {
            try {
                mAbortQueue.put(sequenceId);
            } catch (InterruptedException e) {
                throw new UnsupportedOperationException(
                        "Can't handle InterruptedException in onCaptureAborted");
            }
        }

        @Override
        public void onCaptureSequenceCompleted(CameraCaptureSession session, int sequenceId,
                long frameNumber) {
            try {
                mCaptureSequenceCompletedQueue.put(new Pair(sequenceId, frameNumber));
            } catch (InterruptedException e) {
                throw new UnsupportedOperationException(
                        "Can't handle InterruptedException in onCaptureSequenceCompleted");
            }
        }

        @Override
        public void onCaptureBufferLost(CameraCaptureSession session,
                CaptureRequest request, Surface target, long frameNumber) {
            try {
                mBufferLostQueue.put(new Pair<>(target, frameNumber));
            } catch (InterruptedException e) {
                throw new UnsupportedOperationException(
                        "Can't handle InterruptedException in onCaptureBufferLost");
            }
        }

        public long getTotalNumFrames() {
            return mNumFramesArrived.get();
        }

        public CaptureResult getCaptureResult(long timeout) {
            return getTotalCaptureResult(timeout);
        }

        public TotalCaptureResult getCaptureResult(long timeout, long timestamp) {
            try {
                long currentTs = -1L;
                TotalCaptureResult result;
                while (true) {
                    result = mQueue.poll(timeout, TimeUnit.MILLISECONDS);
                    if (result == null) {
                        throw new RuntimeException(
                                "Wait for a capture result timed out in " + timeout + "ms");
                    }
                    currentTs = result.get(CaptureResult.SENSOR_TIMESTAMP);
                    if (currentTs == timestamp) {
                        return result;
                    }
                }

            } catch (InterruptedException e) {
                throw new UnsupportedOperationException("Unhandled interrupted exception", e);
            }
        }

        public TotalCaptureResult getTotalCaptureResult(long timeout) {
            try {
                TotalCaptureResult result = mQueue.poll(timeout, TimeUnit.MILLISECONDS);
                assertNotNull("Wait for a capture result timed out in " + timeout + "ms", result);
                return result;
            } catch (InterruptedException e) {
                throw new UnsupportedOperationException("Unhandled interrupted exception", e);
            }
        }

        /**
         * Get the {@link #CaptureResult capture result} for a given
         * {@link #CaptureRequest capture request}.
         *
         * @param myRequest The {@link #CaptureRequest capture request} whose
         *            corresponding {@link #CaptureResult capture result} was
         *            being waited for
         * @param numResultsWait Number of frames to wait for the capture result
         *            before timeout.
         * @throws TimeoutRuntimeException If more than numResultsWait results are
         *            seen before the result matching myRequest arrives, or each
         *            individual wait for result times out after
         *            {@value #CAPTURE_RESULT_TIMEOUT_MS}ms.
         */
        public CaptureResult getCaptureResultForRequest(CaptureRequest myRequest,
                int numResultsWait) {
            return getTotalCaptureResultForRequest(myRequest, numResultsWait);
        }

        /**
         * Get the {@link #TotalCaptureResult total capture result} for a given
         * {@link #CaptureRequest capture request}.
         *
         * @param myRequest The {@link #CaptureRequest capture request} whose
         *            corresponding {@link #TotalCaptureResult capture result} was
         *            being waited for
         * @param numResultsWait Number of frames to wait for the capture result
         *            before timeout.
         * @throws TimeoutRuntimeException If more than numResultsWait results are
         *            seen before the result matching myRequest arrives, or each
         *            individual wait for result times out after
         *            {@value #CAPTURE_RESULT_TIMEOUT_MS}ms.
         */
        public TotalCaptureResult getTotalCaptureResultForRequest(CaptureRequest myRequest,
                int numResultsWait) {
            return getTotalCaptureResultForRequest(myRequest, numResultsWait,
                    CAPTURE_RESULT_TIMEOUT_MS);
        }

        /**
         * Get the {@link #TotalCaptureResult total capture result} for a given
         * {@link #CaptureRequest capture request}.
         *
         * @param myRequest The {@link #CaptureRequest capture request} whose
         *            corresponding {@link #TotalCaptureResult capture result} was
         *            being waited for
         * @param numResultsWait Number of frames to wait for the capture result
         *            before timeout.
         * @param timeoutForResult Timeout to wait for each capture result.
         * @throws TimeoutRuntimeException If more than numResultsWait results are
         *            seen before the result matching myRequest arrives, or each
         *            individual wait for result times out after
         *            timeoutForResult ms.
         */
        public TotalCaptureResult getTotalCaptureResultForRequest(CaptureRequest myRequest,
                int numResultsWait, int timeoutForResult) {
            ArrayList<CaptureRequest> captureRequests = new ArrayList<>(1);
            captureRequests.add(myRequest);
            return getTotalCaptureResultsForRequests(
                    captureRequests, numResultsWait, timeoutForResult)[0];
        }

        /**
         * Get an array of {@link #TotalCaptureResult total capture results} for a given list of
         * {@link #CaptureRequest capture requests}. This can be used when the order of results
         * may not the same as the order of requests.
         *
         * @param captureRequests The list of {@link #CaptureRequest capture requests} whose
         *            corresponding {@link #TotalCaptureResult capture results} are
         *            being waited for.
         * @param numResultsWait Number of frames to wait for the capture results
         *            before timeout.
         * @throws TimeoutRuntimeException If more than numResultsWait results are
         *            seen before all the results matching captureRequests arrives.
         */
        public TotalCaptureResult[] getTotalCaptureResultsForRequests(
                List<CaptureRequest> captureRequests, int numResultsWait) {
            return getTotalCaptureResultsForRequests(captureRequests, numResultsWait,
                    CAPTURE_RESULT_TIMEOUT_MS);
        }

        /**
         * Get an array of {@link #TotalCaptureResult total capture results} for a given list of
         * {@link #CaptureRequest capture requests}. This can be used when the order of results
         * may not the same as the order of requests.
         *
         * @param captureRequests The list of {@link #CaptureRequest capture requests} whose
         *            corresponding {@link #TotalCaptureResult capture results} are
         *            being waited for.
         * @param numResultsWait Number of frames to wait for the capture results
         *            before timeout.
         * @param timeoutForResult Timeout to wait for each capture result.
         * @throws TimeoutRuntimeException If more than numResultsWait results are
         *            seen before all the results matching captureRequests arrives.
         */
        public TotalCaptureResult[] getTotalCaptureResultsForRequests(
                List<CaptureRequest> captureRequests, int numResultsWait, int timeoutForResult) {
            if (numResultsWait < 0) {
                throw new IllegalArgumentException("numResultsWait must be no less than 0");
            }
            if (captureRequests == null || captureRequests.size() == 0) {
                throw new IllegalArgumentException("captureRequests must have at least 1 request.");
            }

            // Create a request -> a list of result indices map that it will wait for.
            HashMap<CaptureRequest, ArrayList<Integer>> remainingResultIndicesMap = new HashMap<>();
            for (int i = 0; i < captureRequests.size(); i++) {
                CaptureRequest request = captureRequests.get(i);
                ArrayList<Integer> indices = remainingResultIndicesMap.get(request);
                if (indices == null) {
                    indices = new ArrayList<>();
                    remainingResultIndicesMap.put(request, indices);
                }
                indices.add(i);
            }

            TotalCaptureResult[] results = new TotalCaptureResult[captureRequests.size()];
            int i = 0;
            do {
                TotalCaptureResult result = getTotalCaptureResult(timeoutForResult);
                CaptureRequest request = result.getRequest();
                ArrayList<Integer> indices = remainingResultIndicesMap.get(request);
                if (indices != null) {
                    results[indices.get(0)] = result;
                    indices.remove(0);

                    // Remove the entry if all results for this request has been fulfilled.
                    if (indices.isEmpty()) {
                        remainingResultIndicesMap.remove(request);
                    }
                }

                if (remainingResultIndicesMap.isEmpty()) {
                    return results;
                }
            } while (i++ < numResultsWait);

            throw new TimeoutRuntimeException("Unable to get the expected capture result after "
                    + "waiting for " + numResultsWait + " results");
        }

        /**
         * Get an array list of {@link #CaptureFailure capture failure} with maxNumFailures entries
         * at most. If it times out before maxNumFailures failures are received, return the failures
         * received so far.
         *
         * @param maxNumFailures The maximal number of failures to return. If it times out before
         *                       the maximal number of failures are received, return the received
         *                       failures so far.
         * @throws UnsupportedOperationException If an error happens while waiting on the failure.
         */
        public ArrayList<CaptureFailure> getCaptureFailures(long maxNumFailures) {
            ArrayList<CaptureFailure> failures = new ArrayList<>();
            try {
                for (int i = 0; i < maxNumFailures; i++) {
                    CaptureFailure failure = mFailureQueue.poll(CAPTURE_RESULT_TIMEOUT_MS,
                            TimeUnit.MILLISECONDS);
                    if (failure == null) {
                        // If waiting on a failure times out, return the failures so far.
                        break;
                    }
                    failures.add(failure);
                }
            }  catch (InterruptedException e) {
                throw new UnsupportedOperationException("Unhandled interrupted exception", e);
            }

            return failures;
        }

        /**
         * Get an array list of lost buffers with maxNumLost entries at most.
         * If it times out before maxNumLost buffer lost callbacks are received, return the
         * lost callbacks received so far.
         *
         * @param maxNumLost The maximal number of buffer lost failures to return. If it times out
         *                   before the maximal number of failures are received, return the received
         *                   buffer lost failures so far.
         * @throws UnsupportedOperationException If an error happens while waiting on the failure.
         */
        public ArrayList<Pair<Surface, Long>> getLostBuffers(long maxNumLost) {
            ArrayList<Pair<Surface, Long>> failures = new ArrayList<>();
            try {
                for (int i = 0; i < maxNumLost; i++) {
                    Pair<Surface, Long> failure = mBufferLostQueue.poll(CAPTURE_RESULT_TIMEOUT_MS,
                            TimeUnit.MILLISECONDS);
                    if (failure == null) {
                        // If waiting on a failure times out, return the failures so far.
                        break;
                    }
                    failures.add(failure);
                }
            }  catch (InterruptedException e) {
                throw new UnsupportedOperationException("Unhandled interrupted exception", e);
            }

            return failures;
        }

        /**
         * Get an array list of aborted capture sequence ids with maxNumAborts entries
         * at most. If it times out before maxNumAborts are received, return the aborted sequences
         * received so far.
         *
         * @param maxNumAborts The maximal number of aborted sequences to return. If it times out
         *                     before the maximal number of aborts are received, return the received
         *                     failed sequences so far.
         * @throws UnsupportedOperationException If an error happens while waiting on the failed
         *                                       sequences.
         */
        public ArrayList<Integer> geAbortedSequences(long maxNumAborts) {
            ArrayList<Integer> abortList = new ArrayList<>();
            try {
                for (int i = 0; i < maxNumAborts; i++) {
                    Integer abortSequence = mAbortQueue.poll(CAPTURE_RESULT_TIMEOUT_MS,
                            TimeUnit.MILLISECONDS);
                    if (abortSequence == null) {
                        break;
                    }
                    abortList.add(abortSequence);
                }
            }  catch (InterruptedException e) {
                throw new UnsupportedOperationException("Unhandled interrupted exception", e);
            }

            return abortList;
        }

        /**
         * Wait until the capture start of a request and expected timestamp arrives or it times
         * out after a number of capture starts.
         *
         * @param request The request for the capture start to wait for.
         * @param timestamp The timestamp for the capture start to wait for.
         * @param numCaptureStartsWait The number of capture start events to wait for before timing
         *                             out.
         */
        public void waitForCaptureStart(CaptureRequest request, Long timestamp,
                int numCaptureStartsWait) throws Exception {
            Pair<CaptureRequest, Long> expectedShutter = new Pair<>(request, timestamp);

            int i = 0;
            do {
                Pair<CaptureRequest, Long> shutter = mCaptureStartQueue.poll(
                        CAPTURE_RESULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);

                if (shutter == null) {
                    throw new TimeoutRuntimeException("Unable to get any more capture start " +
                            "event after waiting for " + CAPTURE_RESULT_TIMEOUT_MS + " ms.");
                } else if (expectedShutter.equals(shutter)) {
                    return;
                }

            } while (i++ < numCaptureStartsWait);

            throw new TimeoutRuntimeException("Unable to get the expected capture start " +
                    "event after waiting for " + numCaptureStartsWait + " capture starts");
        }

        /**
         * Wait until it receives capture sequence completed callback for a given squence ID.
         *
         * @param sequenceId The sequence ID of the capture sequence completed callback to wait for.
         * @param timeoutMs Time to wait for each capture sequence complete callback before
         *                  timing out.
         */
        public long getCaptureSequenceLastFrameNumber(int sequenceId, long timeoutMs) {
            try {
                while (true) {
                    Pair<Integer, Long> completedSequence =
                            mCaptureSequenceCompletedQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
                    assertNotNull("Wait for a capture sequence completed timed out in " +
                            timeoutMs + "ms", completedSequence);

                    if (completedSequence.first.equals(sequenceId)) {
                        return completedSequence.second.longValue();
                    }
                }
            } catch (InterruptedException e) {
                throw new UnsupportedOperationException("Unhandled interrupted exception", e);
            }
        }

        public boolean hasMoreResults()
        {
            return !mQueue.isEmpty();
        }

        public boolean hasMoreFailures()
        {
            return !mFailureQueue.isEmpty();
        }

        public int getNumLostBuffers()
        {
            return mBufferLostQueue.size();
        }

        public boolean hasMoreAbortedSequences()
        {
            return !mAbortQueue.isEmpty();
        }

        public List<Long> getCaptureStartTimestamps(int count) {
            List<Long> timestamps = new ArrayList<Long>();
            try {
                while (timestamps.size() < count) {
                    Pair<CaptureRequest, Long> captureStart = mCaptureStartQueue.poll(
                            CAPTURE_RESULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    assertNotNull("Wait for a capture start timed out in "
                            + CAPTURE_RESULT_TIMEOUT_MS + "ms", captureStart);

                    timestamps.add(captureStart.second);
                }
                return timestamps;
            } catch (InterruptedException e) {
                throw new UnsupportedOperationException("Unhandled interrupted exception", e);
            }
        }

        /**
         * Get start of readout timestamps
         *
         * @param count The number of captures
         * @return The list of start of readout timestamps
         */
        public List<Long> getReadoutStartTimestamps(int count) {
            List<Long> timestamps = new ArrayList<Long>();
            try {
                while (timestamps.size() < count) {
                    Pair<CaptureRequest, Long> readoutStart = mReadoutStartQueue.poll(
                            CAPTURE_RESULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    assertNotNull("Wait for a readout start timed out in "
                            + CAPTURE_RESULT_TIMEOUT_MS + "ms", readoutStart);

                    timestamps.add(readoutStart.second);
                }
                return timestamps;
            } catch (InterruptedException e) {
                throw new UnsupportedOperationException("Unhandled interrupted exception", e);
            }
        }

        public void drain() {
            mQueue.clear();
            mNumFramesArrived.getAndSet(0);
            mFailureQueue.clear();
            mBufferLostQueue.clear();
            mCaptureStartQueue.clear();
            mReadoutStartQueue.clear();
            mAbortQueue.clear();
        }
    }

    private static class BlockingCameraManager
            extends com.android.ex.camera2.blocking.BlockingCameraManager {

        BlockingCameraManager(CameraManager manager) {
            super(manager);
        }

        public CameraDevice openCamera(String cameraId, boolean overrideToPortrait,
                CameraDevice.StateCallback listener, Handler handler)
                throws CameraAccessException, BlockingOpenException {
            if (handler == null) {
                throw new IllegalArgumentException("handler must not be null");
            } else if (handler.getLooper() == Looper.myLooper()) {
                throw new IllegalArgumentException(
                        "handler's looper must not be the current looper");
            }

            return (new OpenListener(mManager, cameraId, overrideToPortrait, listener, handler))
                    .blockUntilOpen();
        }

        protected class OpenListener
                extends com.android.ex.camera2.blocking.BlockingCameraManager.OpenListener {
            OpenListener(CameraManager manager, String cameraId, boolean overrideToPortrait,
                    CameraDevice.StateCallback listener, Handler handler)
                    throws CameraAccessException {
                super(cameraId, listener);
                manager.openCamera(cameraId, overrideToPortrait, handler, this);
            }
        }
    }

    public static boolean hasCapability(CameraCharacteristics characteristics, int capability) {
        int [] capabilities =
                characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
        for (int c : capabilities) {
            if (c == capability) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSystemCamera(CameraManager manager, String cameraId)
            throws CameraAccessException {
        CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
        return hasCapability(characteristics,
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_SYSTEM_CAMERA);
    }

    public static String[] getCameraIdListForTesting(CameraManager manager,
            boolean getSystemCameras)
            throws CameraAccessException {
        String [] ids = manager.getCameraIdListNoLazy();
        List<String> idsForTesting = new ArrayList<String>();
        for (String id : ids) {
            boolean isSystemCamera = isSystemCamera(manager, id);
            if (getSystemCameras == isSystemCamera) {
                idsForTesting.add(id);
            }
        }
        return idsForTesting.toArray(new String[idsForTesting.size()]);
    }

    public static Set<Set<String>> getConcurrentCameraIds(CameraManager manager,
            boolean getSystemCameras)
            throws CameraAccessException {
        Set<String> cameraIds = new HashSet<String>(Arrays.asList(getCameraIdListForTesting(manager, getSystemCameras)));
        Set<Set<String>> combinations =  manager.getConcurrentCameraIds();
        Set<Set<String>> correctComb = new HashSet<Set<String>>();
        for (Set<String> comb : combinations) {
            Set<String> filteredIds = new HashSet<String>();
            for (String id : comb) {
                if (cameraIds.contains(id)) {
                    filteredIds.add(id);
                }
            }
            if (filteredIds.isEmpty()) {
                continue;
            }
            correctComb.add(filteredIds);
        }
        return correctComb;
    }

    /**
     * Block until the camera is opened.
     *
     * <p>Don't use this to test #onDisconnected/#onError since this will throw
     * an AssertionError if it fails to open the camera device.</p>
     *
     * @return CameraDevice opened camera device
     *
     * @throws IllegalArgumentException
     *            If the handler is null, or if the handler's looper is current.
     * @throws CameraAccessException
     *            If open fails immediately.
     * @throws BlockingOpenException
     *            If open fails after blocking for some amount of time.
     * @throws TimeoutRuntimeException
     *            If opening times out. Typically unrecoverable.
     */
    public static CameraDevice openCamera(CameraManager manager, String cameraId,
            CameraDevice.StateCallback listener, Handler handler) throws CameraAccessException,
            BlockingOpenException {

        /**
         * Although camera2 API allows 'null' Handler (it will just use the current
         * thread's Looper), this is not what we want for CTS.
         *
         * In CTS the default looper is used only to process events in between test runs,
         * so anything sent there would not be executed inside a test and the test would fail.
         *
         * In this case, BlockingCameraManager#openCamera performs the check for us.
         */
        return (new CameraTestUtils.BlockingCameraManager(manager))
                .openCamera(cameraId, listener, handler);
    }

    /**
     * Block until the camera is opened.
     *
     * <p>Don't use this to test #onDisconnected/#onError since this will throw
     * an AssertionError if it fails to open the camera device.</p>
     *
     * @throws IllegalArgumentException
     *            If the handler is null, or if the handler's looper is current.
     * @throws CameraAccessException
     *            If open fails immediately.
     * @throws BlockingOpenException
     *            If open fails after blocking for some amount of time.
     * @throws TimeoutRuntimeException
     *            If opening times out. Typically unrecoverable.
     */
    public static CameraDevice openCamera(CameraManager manager, String cameraId,
            boolean overrideToPortrait, CameraDevice.StateCallback listener, Handler handler)
            throws CameraAccessException, BlockingOpenException {
        return (new CameraTestUtils.BlockingCameraManager(manager))
                .openCamera(cameraId, overrideToPortrait, listener, handler);
    }


    /**
     * Block until the camera is opened.
     *
     * <p>Don't use this to test #onDisconnected/#onError since this will throw
     * an AssertionError if it fails to open the camera device.</p>
     *
     * @throws IllegalArgumentException
     *            If the handler is null, or if the handler's looper is current.
     * @throws CameraAccessException
     *            If open fails immediately.
     * @throws BlockingOpenException
     *            If open fails after blocking for some amount of time.
     * @throws TimeoutRuntimeException
     *            If opening times out. Typically unrecoverable.
     */
    public static CameraDevice openCamera(CameraManager manager, String cameraId, Handler handler)
            throws CameraAccessException,
            BlockingOpenException {
        return openCamera(manager, cameraId, /*listener*/null, handler);
    }

    /**
     * Configure a new camera session with output surfaces and type.
     *
     * @param camera The CameraDevice to be configured.
     * @param outputSurfaces The surface list that used for camera output.
     * @param listener The callback CameraDevice will notify when capture results are available.
     */
    public static CameraCaptureSession configureCameraSession(CameraDevice camera,
            List<Surface> outputSurfaces, boolean isHighSpeed,
            CameraCaptureSession.StateCallback listener, Handler handler)
            throws CameraAccessException {
        BlockingSessionCallback sessionListener = new BlockingSessionCallback(listener);
        if (isHighSpeed) {
            camera.createConstrainedHighSpeedCaptureSession(outputSurfaces,
                    sessionListener, handler);
        } else {
            camera.createCaptureSession(outputSurfaces, sessionListener, handler);
        }
        CameraCaptureSession session =
                sessionListener.waitAndGetSession(SESSION_CONFIGURE_TIMEOUT_MS);
        assertFalse("Camera session should not be a reprocessable session",
                session.isReprocessable());
        String sessionType = isHighSpeed ? "High Speed" : "Normal";
        assertTrue("Capture session type must be " + sessionType,
                isHighSpeed ==
                CameraConstrainedHighSpeedCaptureSession.class.isAssignableFrom(session.getClass()));

        return session;
    }

    /**
     * Build a new constrained camera session with output surfaces, type and recording session
     * parameters.
     *
     * @param camera The CameraDevice to be configured.
     * @param outputSurfaces The surface list that used for camera output.
     * @param listener The callback CameraDevice will notify when capture results are available.
     * @param initialRequest Initial request settings to use as session parameters.
     */
    public static CameraCaptureSession buildConstrainedCameraSession(CameraDevice camera,
            List<Surface> outputSurfaces, CameraCaptureSession.StateCallback listener,
            Handler handler, CaptureRequest initialRequest) throws CameraAccessException {
        BlockingSessionCallback sessionListener = new BlockingSessionCallback(listener);

        List<OutputConfiguration> outConfigurations = new ArrayList<>(outputSurfaces.size());
        for (Surface surface : outputSurfaces) {
            outConfigurations.add(new OutputConfiguration(surface));
        }
        SessionConfiguration sessionConfig = new SessionConfiguration(
                SessionConfiguration.SESSION_HIGH_SPEED, outConfigurations,
                new HandlerExecutor(handler), sessionListener);
        sessionConfig.setSessionParameters(initialRequest);
        camera.createCaptureSession(sessionConfig);

        CameraCaptureSession session =
                sessionListener.waitAndGetSession(SESSION_CONFIGURE_TIMEOUT_MS);
        assertFalse("Camera session should not be a reprocessable session",
                session.isReprocessable());
        assertTrue("Capture session type must be High Speed",
                CameraConstrainedHighSpeedCaptureSession.class.isAssignableFrom(
                        session.getClass()));

        return session;
    }

    /**
     * Configure a new camera session with output configurations.
     *
     * @param camera The CameraDevice to be configured.
     * @param outputs The OutputConfiguration list that is used for camera output.
     * @param listener The callback CameraDevice will notify when capture results are available.
     */
    public static CameraCaptureSession configureCameraSessionWithConfig(CameraDevice camera,
            List<OutputConfiguration> outputs,
            CameraCaptureSession.StateCallback listener, Handler handler)
            throws CameraAccessException {
        BlockingSessionCallback sessionListener = new BlockingSessionCallback(listener);
        camera.createCaptureSessionByOutputConfigurations(outputs, sessionListener, handler);
        CameraCaptureSession session =
                sessionListener.waitAndGetSession(SESSION_CONFIGURE_TIMEOUT_MS);
        assertFalse("Camera session should not be a reprocessable session",
                session.isReprocessable());
        return session;
    }

    /**
     * Configure a new camera session with output configurations / a session color space.
     *
     * @param camera The CameraDevice to be configured.
     * @param outputs The OutputConfiguration list that is used for camera output.
     * @param listener The callback CameraDevice will notify when capture results are available.
     * @param colorSpace The ColorSpace for this session.
     */
    public static CameraCaptureSession configureCameraSessionWithColorSpace(CameraDevice camera,
            List<OutputConfiguration> outputs,
            CameraCaptureSession.StateCallback listener, Handler handler,
            ColorSpace.Named colorSpace) throws CameraAccessException {
        BlockingSessionCallback sessionListener = new BlockingSessionCallback(listener);
        SessionConfiguration sessionConfiguration = new SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR, outputs,
                new HandlerExecutor(handler), sessionListener);
        sessionConfiguration.setColorSpace(colorSpace);
        camera.createCaptureSession(sessionConfiguration);
        CameraCaptureSession session =
                sessionListener.waitAndGetSession(SESSION_CONFIGURE_TIMEOUT_MS);
        assertFalse("Camera session should not be a reprocessable session",
                session.isReprocessable());
        return session;
    }

    /**
     * Try configure a new camera session with output configurations.
     *
     * @param camera The CameraDevice to be configured.
     * @param outputs The OutputConfiguration list that is used for camera output.
     * @param initialRequest The session parameters passed in during stream configuration
     * @param listener The callback CameraDevice will notify when capture results are available.
     */
    public static CameraCaptureSession tryConfigureCameraSessionWithConfig(CameraDevice camera,
            List<OutputConfiguration> outputs, CaptureRequest initialRequest,
            CameraCaptureSession.StateCallback listener, Handler handler)
            throws CameraAccessException {
        BlockingSessionCallback sessionListener = new BlockingSessionCallback(listener);
        SessionConfiguration sessionConfig = new SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR, outputs, new HandlerExecutor(handler),
                sessionListener);
        sessionConfig.setSessionParameters(initialRequest);
        camera.createCaptureSession(sessionConfig);

        Integer[] sessionStates = {BlockingSessionCallback.SESSION_READY,
                                   BlockingSessionCallback.SESSION_CONFIGURE_FAILED};
        int state = sessionListener.getStateWaiter().waitForAnyOfStates(
                Arrays.asList(sessionStates), SESSION_CONFIGURE_TIMEOUT_MS);

        CameraCaptureSession session = null;
        if (state == BlockingSessionCallback.SESSION_READY) {
            session = sessionListener.waitAndGetSession(SESSION_CONFIGURE_TIMEOUT_MS);
            assertFalse("Camera session should not be a reprocessable session",
                    session.isReprocessable());
        }
        return session;
    }

    /**
     * Configure a new camera session with output surfaces and initial session parameters.
     *
     * @param camera The CameraDevice to be configured.
     * @param outputSurfaces The surface list that used for camera output.
     * @param listener The callback CameraDevice will notify when session is available.
     * @param handler The handler used to notify callbacks.
     * @param initialRequest Initial request settings to use as session parameters.
     */
    public static CameraCaptureSession configureCameraSessionWithParameters(CameraDevice camera,
            List<Surface> outputSurfaces, BlockingSessionCallback listener,
            Handler handler, CaptureRequest initialRequest) throws CameraAccessException {
        List<OutputConfiguration> outConfigurations = new ArrayList<>(outputSurfaces.size());
        for (Surface surface : outputSurfaces) {
            outConfigurations.add(new OutputConfiguration(surface));
        }
        SessionConfiguration sessionConfig = new SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR, outConfigurations,
                new HandlerExecutor(handler), listener);
        sessionConfig.setSessionParameters(initialRequest);
        camera.createCaptureSession(sessionConfig);

        CameraCaptureSession session = listener.waitAndGetSession(SESSION_CONFIGURE_TIMEOUT_MS);
        assertFalse("Camera session should not be a reprocessable session",
                session.isReprocessable());
        assertFalse("Capture session type must be regular",
                CameraConstrainedHighSpeedCaptureSession.class.isAssignableFrom(
                        session.getClass()));

        return session;
    }

    /**
     * Configure a new camera session with output surfaces.
     *
     * @param camera The CameraDevice to be configured.
     * @param outputSurfaces The surface list that used for camera output.
     * @param listener The callback CameraDevice will notify when capture results are available.
     */
    public static CameraCaptureSession configureCameraSession(CameraDevice camera,
            List<Surface> outputSurfaces,
            CameraCaptureSession.StateCallback listener, Handler handler)
            throws CameraAccessException {

        return configureCameraSession(camera, outputSurfaces, /*isHighSpeed*/false,
                listener, handler);
    }

    public static CameraCaptureSession configureReprocessableCameraSession(CameraDevice camera,
            InputConfiguration inputConfiguration, List<Surface> outputSurfaces,
            CameraCaptureSession.StateCallback listener, Handler handler)
            throws CameraAccessException {
        List<OutputConfiguration> outputConfigs = new ArrayList<OutputConfiguration>();
        for (Surface surface : outputSurfaces) {
            outputConfigs.add(new OutputConfiguration(surface));
        }
        CameraCaptureSession session = configureReprocessableCameraSessionWithConfigurations(
                camera, inputConfiguration, outputConfigs, listener, handler);

        return session;
    }

    public static CameraCaptureSession configureReprocessableCameraSessionWithConfigurations(
            CameraDevice camera, InputConfiguration inputConfiguration,
            List<OutputConfiguration> outputConfigs, CameraCaptureSession.StateCallback listener,
            Handler handler) throws CameraAccessException {
        BlockingSessionCallback sessionListener = new BlockingSessionCallback(listener);
        SessionConfiguration sessionConfig = new SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR, outputConfigs, new HandlerExecutor(handler),
                sessionListener);
        sessionConfig.setInputConfiguration(inputConfiguration);
        camera.createCaptureSession(sessionConfig);

        Integer[] sessionStates = {BlockingSessionCallback.SESSION_READY,
                                   BlockingSessionCallback.SESSION_CONFIGURE_FAILED};
        int state = sessionListener.getStateWaiter().waitForAnyOfStates(
                Arrays.asList(sessionStates), SESSION_CONFIGURE_TIMEOUT_MS);

        assertTrue("Creating a reprocessable session failed.",
                state == BlockingSessionCallback.SESSION_READY);
        CameraCaptureSession session =
                sessionListener.waitAndGetSession(SESSION_CONFIGURE_TIMEOUT_MS);
        assertTrue("Camera session should be a reprocessable session", session.isReprocessable());

        return session;
    }

    /**
     * Create a reprocessable camera session with input and output configurations.
     *
     * @param camera The CameraDevice to be configured.
     * @param inputConfiguration The input configuration used to create this session.
     * @param outputs The output configurations used to create this session.
     * @param listener The callback CameraDevice will notify when capture results are available.
     * @param handler The handler used to notify callbacks.
     * @return The session ready to use.
     * @throws CameraAccessException
     */
    public static CameraCaptureSession configureReprocCameraSessionWithConfig(CameraDevice camera,
            InputConfiguration inputConfiguration, List<OutputConfiguration> outputs,
            CameraCaptureSession.StateCallback listener, Handler handler)
            throws CameraAccessException {
        BlockingSessionCallback sessionListener = new BlockingSessionCallback(listener);
        camera.createReprocessableCaptureSessionByConfigurations(inputConfiguration, outputs,
                sessionListener, handler);

        Integer[] sessionStates = {BlockingSessionCallback.SESSION_READY,
                                   BlockingSessionCallback.SESSION_CONFIGURE_FAILED};
        int state = sessionListener.getStateWaiter().waitForAnyOfStates(
                Arrays.asList(sessionStates), SESSION_CONFIGURE_TIMEOUT_MS);

        assertTrue("Creating a reprocessable session failed.",
                state == BlockingSessionCallback.SESSION_READY);

        CameraCaptureSession session =
                sessionListener.waitAndGetSession(SESSION_CONFIGURE_TIMEOUT_MS);
        assertTrue("Camera session should be a reprocessable session", session.isReprocessable());

        return session;
    }

    public static <T> void assertArrayNotEmpty(T arr, String message) {
        assertTrue(message, arr != null && Array.getLength(arr) > 0);
    }

    /**
     * Check if the format is a legal YUV format camera supported.
     */
    public static void checkYuvFormat(int format) {
        if ((format != ImageFormat.YUV_420_888) &&
                (format != ImageFormat.NV21) &&
                (format != ImageFormat.YV12)) {
            fail("Wrong formats: " + format);
        }
    }

    /**
     * Check if image size and format match given size and format.
     */
    public static void checkImage(Image image, int width, int height, int format) {
        checkImage(image, width, height, format, /*colorSpace*/null);
    }

    /**
     * Check if image size and format match given size and format.
     */
    public static void checkImage(Image image, int width, int height, int format,
            ColorSpace colorSpace) {
        // Image reader will wrap YV12/NV21 image by YUV_420_888
        if (format == ImageFormat.NV21 || format == ImageFormat.YV12) {
            format = ImageFormat.YUV_420_888;
        }
        assertNotNull("Input image is invalid", image);
        assertEquals("Format doesn't match", format, image.getFormat());
        assertEquals("Width doesn't match", width, image.getWidth());
        assertEquals("Height doesn't match", height, image.getHeight());

        if (colorSpace != null && format != ImageFormat.JPEG && format != ImageFormat.JPEG_R
                && format != ImageFormat.HEIC) {
            int dataSpace = image.getDataSpace();
            ColorSpace actualColorSpace = ColorSpace.getFromDataSpace(dataSpace);
            assertNotNull("getFromDataSpace() returned null for format "
                    + format + ", dataSpace " + dataSpace, actualColorSpace);
            assertEquals("colorSpace " + actualColorSpace.getId()
                    + " does not match expected color space "
                    + colorSpace.getId(), colorSpace.getId(), actualColorSpace.getId());
        }
    }

    /**
     * <p>Read data from all planes of an Image into a contiguous unpadded, unpacked
     * 1-D linear byte array, such that it can be write into disk, or accessed by
     * software conveniently. It supports YUV_420_888/NV21/YV12 and JPEG input
     * Image format.</p>
     *
     * <p>For YUV_420_888/NV21/YV12/Y8/Y16, it returns a byte array that contains
     * the Y plane data first, followed by U(Cb), V(Cr) planes if there is any
     * (xstride = width, ystride = height for chroma and luma components).</p>
     *
     * <p>For JPEG, it returns a 1-D byte array contains a complete JPEG image.</p>
     *
     * <p>For YUV P010, it returns a byte array that contains Y plane first, followed
     * by the interleaved U(Cb)/V(Cr) plane.</p>
     */
    public static byte[] getDataFromImage(Image image) {
        assertNotNull("Invalid image:", image);
        int format = image.getFormat();
        int width = image.getWidth();
        int height = image.getHeight();
        int rowStride, pixelStride;
        byte[] data = null;

        // Read image data
        Plane[] planes = image.getPlanes();
        assertTrue("Fail to get image planes", planes != null && planes.length > 0);

        // Check image validity
        checkAndroidImageFormat(image);

        ByteBuffer buffer = null;
        // JPEG doesn't have pixelstride and rowstride, treat it as 1D buffer.
        // Same goes for DEPTH_POINT_CLOUD, RAW_PRIVATE, DEPTH_JPEG, and HEIC
        if (format == ImageFormat.JPEG || format == ImageFormat.DEPTH_POINT_CLOUD ||
                format == ImageFormat.RAW_PRIVATE || format == ImageFormat.DEPTH_JPEG ||
                format == ImageFormat.HEIC || format == ImageFormat.JPEG_R) {
            buffer = planes[0].getBuffer();
            assertNotNull("Fail to get jpeg/depth/heic ByteBuffer", buffer);
            data = new byte[buffer.remaining()];
            buffer.get(data);
            buffer.rewind();
            return data;
        } else if (format == ImageFormat.YCBCR_P010) {
            // P010 samples are stored within 16 bit values
            int offset = 0;
            int bytesPerPixelRounded = (ImageFormat.getBitsPerPixel(format) + 7) / 8;
            data = new byte[width * height * bytesPerPixelRounded];
            assertTrue("Unexpected number of planes, expected " + 3 + " actual " + planes.length,
                    planes.length == 3);
            for (int i = 0; i < 2; i++) {
                buffer = planes[i].getBuffer();
                assertNotNull("Fail to get bytebuffer from plane", buffer);
                buffer.rewind();
                rowStride = planes[i].getRowStride();
                if (VERBOSE) {
                    Log.v(TAG, "rowStride " + rowStride);
                    Log.v(TAG, "width " + width);
                    Log.v(TAG, "height " + height);
                }
                int h = (i == 0) ? height : height / 2;
                for (int row = 0; row < h; row++) {
                    // Each 10-bit pixel occupies 2 bytes
                    int length = 2 * width;
                    buffer.get(data, offset, length);
                    offset += length;
                    if (row < h - 1) {
                        buffer.position(buffer.position() + rowStride - length);
                    }
                }
                if (VERBOSE) Log.v(TAG, "Finished reading data from plane " + i);
                buffer.rewind();
            }
            return data;
        }

        int offset = 0;
        data = new byte[width * height * ImageFormat.getBitsPerPixel(format) / 8];
        int maxRowSize = planes[0].getRowStride();
        for (int i = 0; i < planes.length; i++) {
            if (maxRowSize < planes[i].getRowStride()) {
                maxRowSize = planes[i].getRowStride();
            }
        }
        byte[] rowData = new byte[maxRowSize];
        if(VERBOSE) Log.v(TAG, "get data from " + planes.length + " planes");
        for (int i = 0; i < planes.length; i++) {
            buffer = planes[i].getBuffer();
            assertNotNull("Fail to get bytebuffer from plane", buffer);
            buffer.rewind();
            rowStride = planes[i].getRowStride();
            pixelStride = planes[i].getPixelStride();
            assertTrue("pixel stride " + pixelStride + " is invalid", pixelStride > 0);
            if (VERBOSE) {
                Log.v(TAG, "pixelStride " + pixelStride);
                Log.v(TAG, "rowStride " + rowStride);
                Log.v(TAG, "width " + width);
                Log.v(TAG, "height " + height);
            }
            // For multi-planar yuv images, assuming yuv420 with 2x2 chroma subsampling.
            int w = (i == 0) ? width : width / 2;
            int h = (i == 0) ? height : height / 2;
            assertTrue("rowStride " + rowStride + " should be >= width " + w , rowStride >= w);
            for (int row = 0; row < h; row++) {
                int bytesPerPixel = ImageFormat.getBitsPerPixel(format) / 8;
                int length;
                if (pixelStride == bytesPerPixel) {
                    // Special case: optimized read of the entire row
                    length = w * bytesPerPixel;
                    buffer.get(data, offset, length);
                    offset += length;
                } else {
                    // Generic case: should work for any pixelStride but slower.
                    // Use intermediate buffer to avoid read byte-by-byte from
                    // DirectByteBuffer, which is very bad for performance
                    length = (w - 1) * pixelStride + bytesPerPixel;
                    buffer.get(rowData, 0, length);
                    for (int col = 0; col < w; col++) {
                        data[offset++] = rowData[col * pixelStride];
                    }
                }
                // Advance buffer the remainder of the row stride
                if (row < h - 1) {
                    buffer.position(buffer.position() + rowStride - length);
                }
            }
            if (VERBOSE) Log.v(TAG, "Finished reading data from plane " + i);
            buffer.rewind();
        }
        return data;
    }

    /**
     * <p>Check android image format validity for an image, only support below formats:</p>
     *
     * <p>YUV_420_888/NV21/YV12, can add more for future</p>
     */
    public static void checkAndroidImageFormat(Image image) {
        int format = image.getFormat();
        Plane[] planes = image.getPlanes();
        switch (format) {
            case ImageFormat.YUV_420_888:
            case ImageFormat.NV21:
            case ImageFormat.YV12:
            case ImageFormat.YCBCR_P010:
                assertEquals("YUV420 format Images should have 3 planes", 3, planes.length);
                break;
            case ImageFormat.JPEG:
            case ImageFormat.RAW_SENSOR:
            case ImageFormat.RAW_PRIVATE:
            case ImageFormat.DEPTH16:
            case ImageFormat.DEPTH_POINT_CLOUD:
            case ImageFormat.DEPTH_JPEG:
            case ImageFormat.Y8:
            case ImageFormat.HEIC:
            case ImageFormat.JPEG_R:
                assertEquals("JPEG/RAW/depth/Y8 Images should have one plane", 1, planes.length);
                break;
            default:
                fail("Unsupported Image Format: " + format);
        }
    }

    public static void dumpFile(String fileName, Bitmap data) {
        FileOutputStream outStream;
        try {
            Log.v(TAG, "output will be saved as " + fileName);
            outStream = new FileOutputStream(fileName);
        } catch (IOException ioe) {
            throw new RuntimeException("Unable to create debug output file " + fileName, ioe);
        }

        try {
            data.compress(Bitmap.CompressFormat.JPEG, /*quality*/90, outStream);
            outStream.close();
        } catch (IOException ioe) {
            throw new RuntimeException("failed writing data to file " + fileName, ioe);
        }
    }

    public static void dumpFile(String fileName, byte[] data) {
        FileOutputStream outStream;
        try {
            Log.v(TAG, "output will be saved as " + fileName);
            outStream = new FileOutputStream(fileName);
        } catch (IOException ioe) {
            throw new RuntimeException("Unable to create debug output file " + fileName, ioe);
        }

        try {
            outStream.write(data);
            outStream.close();
        } catch (IOException ioe) {
            throw new RuntimeException("failed writing data to file " + fileName, ioe);
        }
    }

    /**
     * Get the available output sizes for the user-defined {@code format}.
     *
     * <p>Note that implementation-defined/hidden formats are not supported.</p>
     */
    public static Size[] getSupportedSizeForFormat(int format, String cameraId,
            CameraManager cameraManager) throws CameraAccessException {
        return getSupportedSizeForFormat(format, cameraId, cameraManager,
                /*maxResolution*/false);
    }

    public static Size[] getSupportedSizeForFormat(int format, String cameraId,
            CameraManager cameraManager, boolean maxResolution) throws CameraAccessException {
        CameraCharacteristics properties = cameraManager.getCameraCharacteristics(cameraId);
        assertNotNull("Can't get camera characteristics!", properties);
        if (VERBOSE) {
            Log.v(TAG, "get camera characteristics for camera: " + cameraId);
        }
        CameraCharacteristics.Key<StreamConfigurationMap> configMapTag = maxResolution ?
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION :
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP;
        StreamConfigurationMap configMap = properties.get(configMapTag);
        if (configMap == null) {
            assertTrue("SCALER_STREAM_CONFIGURATION_MAP is null!", maxResolution);
            return null;
        }

        Size[] availableSizes = configMap.getOutputSizes(format);
        if (!maxResolution) {
            assertArrayNotEmpty(availableSizes, "availableSizes should not be empty for format: "
                    + format);
        }
        Size[] highResAvailableSizes = configMap.getHighResolutionOutputSizes(format);
        if (highResAvailableSizes != null && highResAvailableSizes.length > 0) {
            Size[] allSizes = new Size[availableSizes.length + highResAvailableSizes.length];
            System.arraycopy(availableSizes, 0, allSizes, 0,
                    availableSizes.length);
            System.arraycopy(highResAvailableSizes, 0, allSizes, availableSizes.length,
                    highResAvailableSizes.length);
            availableSizes = allSizes;
        }
        if (VERBOSE) Log.v(TAG, "Supported sizes are: " + Arrays.deepToString(availableSizes));
        return availableSizes;
    }

    /**
     * Get the available output sizes for the given class.
     *
     */
    public static Size[] getSupportedSizeForClass(Class klass, String cameraId,
            CameraManager cameraManager) throws CameraAccessException {
        CameraCharacteristics properties = cameraManager.getCameraCharacteristics(cameraId);
        assertNotNull("Can't get camera characteristics!", properties);
        if (VERBOSE) {
            Log.v(TAG, "get camera characteristics for camera: " + cameraId);
        }
        StreamConfigurationMap configMap =
                properties.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] availableSizes = configMap.getOutputSizes(klass);
        assertArrayNotEmpty(availableSizes, "availableSizes should not be empty for class: "
                + klass);
        Size[] highResAvailableSizes = configMap.getHighResolutionOutputSizes(ImageFormat.PRIVATE);
        if (highResAvailableSizes != null && highResAvailableSizes.length > 0) {
            Size[] allSizes = new Size[availableSizes.length + highResAvailableSizes.length];
            System.arraycopy(availableSizes, 0, allSizes, 0,
                    availableSizes.length);
            System.arraycopy(highResAvailableSizes, 0, allSizes, availableSizes.length,
                    highResAvailableSizes.length);
            availableSizes = allSizes;
        }
        if (VERBOSE) Log.v(TAG, "Supported sizes are: " + Arrays.deepToString(availableSizes));
        return availableSizes;
    }

    /**
     * Size comparator that compares the number of pixels it covers.
     *
     * <p>If two the areas of two sizes are same, compare the widths.</p>
     */
    public static class SizeComparator implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            return CameraUtils
                    .compareSizes(lhs.getWidth(), lhs.getHeight(), rhs.getWidth(), rhs.getHeight());
        }
    }

    /**
     * Get sorted size list in descending order. Remove the sizes larger than
     * the bound. If the bound is null, don't do the size bound filtering.
     */
    static public List<Size> getSupportedPreviewSizes(String cameraId,
            CameraManager cameraManager, Size bound) throws CameraAccessException {

        Size[] rawSizes = getSupportedSizeForClass(android.view.SurfaceHolder.class, cameraId,
                cameraManager);
        assertArrayNotEmpty(rawSizes,
                "Available sizes for SurfaceHolder class should not be empty");
        if (VERBOSE) {
            Log.v(TAG, "Supported sizes are: " + Arrays.deepToString(rawSizes));
        }

        if (bound == null) {
            return getAscendingOrderSizes(Arrays.asList(rawSizes), /*ascending*/false);
        }

        List<Size> sizes = new ArrayList<Size>();
        for (Size sz: rawSizes) {
            if (sz.getWidth() <= bound.getWidth() && sz.getHeight() <= bound.getHeight()) {
                sizes.add(sz);
            }
        }
        return getAscendingOrderSizes(sizes, /*ascending*/false);
    }

    /**
     * Get a sorted list of sizes from a given size list.
     *
     * <p>
     * The size is compare by area it covers, if the areas are same, then
     * compare the widths.
     * </p>
     *
     * @param sizeList The input size list to be sorted
     * @param ascending True if the order is ascending, otherwise descending order
     * @return The ordered list of sizes
     */
    static public List<Size> getAscendingOrderSizes(final List<Size> sizeList, boolean ascending) {
        if (sizeList == null) {
            throw new IllegalArgumentException("sizeList shouldn't be null");
        }

        Comparator<Size> comparator = new SizeComparator();
        List<Size> sortedSizes = new ArrayList<Size>();
        sortedSizes.addAll(sizeList);
        Collections.sort(sortedSizes, comparator);
        if (!ascending) {
            Collections.reverse(sortedSizes);
        }

        return sortedSizes;
    }
    /**
     * Get sorted (descending order) size list for given format. Remove the sizes larger than
     * the bound. If the bound is null, don't do the size bound filtering.
     */
    static public List<Size> getSortedSizesForFormat(String cameraId,
            CameraManager cameraManager, int format, Size bound) throws CameraAccessException {
        return getSortedSizesForFormat(cameraId, cameraManager, format, /*maxResolution*/false,
                bound);
    }

    /**
     * Get sorted (descending order) size list for given format (with an option to get sizes from
     * the maximum resolution stream configuration map). Remove the sizes larger than
     * the bound. If the bound is null, don't do the size bound filtering.
     */
    static public List<Size> getSortedSizesForFormat(String cameraId,
            CameraManager cameraManager, int format, boolean maxResolution, Size bound)
            throws CameraAccessException {
        Comparator<Size> comparator = new SizeComparator();
        Size[] sizes = getSupportedSizeForFormat(format, cameraId, cameraManager, maxResolution);
        List<Size> sortedSizes = null;
        if (bound != null) {
            sortedSizes = new ArrayList<Size>(/*capacity*/1);
            for (Size sz : sizes) {
                if (comparator.compare(sz, bound) <= 0) {
                    sortedSizes.add(sz);
                }
            }
        } else {
            sortedSizes = Arrays.asList(sizes);
        }
        assertTrue("Supported size list should have at least one element",
                sortedSizes.size() > 0);

        Collections.sort(sortedSizes, comparator);
        // Make it in descending order.
        Collections.reverse(sortedSizes);
        return sortedSizes;
    }

    /**
     * Get supported video size list for a given camera device.
     *
     * <p>
     * Filter out the sizes that are larger than the bound. If the bound is
     * null, don't do the size bound filtering.
     * </p>
     */
    static public List<Size> getSupportedVideoSizes(String cameraId,
            CameraManager cameraManager, Size bound) throws CameraAccessException {

        Size[] rawSizes = getSupportedSizeForClass(android.media.MediaRecorder.class,
                cameraId, cameraManager);
        assertArrayNotEmpty(rawSizes,
                "Available sizes for MediaRecorder class should not be empty");
        if (VERBOSE) {
            Log.v(TAG, "Supported sizes are: " + Arrays.deepToString(rawSizes));
        }

        if (bound == null) {
            return getAscendingOrderSizes(Arrays.asList(rawSizes), /*ascending*/false);
        }

        List<Size> sizes = new ArrayList<Size>();
        for (Size sz: rawSizes) {
            if (sz.getWidth() <= bound.getWidth() && sz.getHeight() <= bound.getHeight()) {
                sizes.add(sz);
            }
        }
        return getAscendingOrderSizes(sizes, /*ascending*/false);
    }

    /**
     * Get supported video size list (descending order) for a given camera device.
     *
     * <p>
     * Filter out the sizes that are larger than the bound. If the bound is
     * null, don't do the size bound filtering.
     * </p>
     */
    static public List<Size> getSupportedStillSizes(String cameraId,
            CameraManager cameraManager, Size bound) throws CameraAccessException {
        return getSortedSizesForFormat(cameraId, cameraManager, ImageFormat.JPEG, bound);
    }

    static public List<Size> getSupportedHeicSizes(String cameraId,
            CameraManager cameraManager, Size bound) throws CameraAccessException {
        return getSortedSizesForFormat(cameraId, cameraManager, ImageFormat.HEIC, bound);
    }

    static public Size getMinPreviewSize(String cameraId, CameraManager cameraManager)
            throws CameraAccessException {
        List<Size> sizes = getSupportedPreviewSizes(cameraId, cameraManager, null);
        return sizes.get(sizes.size() - 1);
    }

    /**
     * Get max supported preview size for a camera device.
     */
    static public Size getMaxPreviewSize(String cameraId, CameraManager cameraManager)
            throws CameraAccessException {
        return getMaxPreviewSize(cameraId, cameraManager, /*bound*/null);
    }

    /**
     * Get max preview size for a camera device in the supported sizes that are no larger
     * than the bound.
     */
    static public Size getMaxPreviewSize(String cameraId, CameraManager cameraManager, Size bound)
            throws CameraAccessException {
        List<Size> sizes = getSupportedPreviewSizes(cameraId, cameraManager, bound);
        return sizes.get(0);
    }

    /**
     * Get max depth size for a camera device.
     */
    static public Size getMaxDepthSize(String cameraId, CameraManager cameraManager)
            throws CameraAccessException {
        List<Size> sizes = getSortedSizesForFormat(cameraId, cameraManager, ImageFormat.DEPTH16,
                /*bound*/ null);
        return sizes.get(0);
    }

    /**
     * Return the lower size
     * @param a first size
     *
     * @param b second size
     *
     * @return Size the smaller size
     *
     * @throws IllegalArgumentException if either param was null.
     *
     */
    @NonNull public static Size getMinSize(Size a, Size b) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("sizes was empty");
        }
        if (a.getWidth() * a.getHeight() < b.getHeight() * b.getWidth()) {
            return a;
        }
        return b;
    }

    /**
     * Get the largest size by area.
     *
     * @param sizes an array of sizes, must have at least 1 element
     *
     * @return Largest Size
     *
     * @throws IllegalArgumentException if sizes was null or had 0 elements
     */
    public static Size getMaxSize(Size... sizes) {
        return getMaxSize(sizes, -1 /*aspectRatio*/);
    }

    /**
     * Get the largest size by area, and with given aspect ratio.
     *
     * @param sizes an array of sizes, must have at least 1 element
     *
     * @return Largest Size
     *
     * @throws IllegalArgumentException if sizes was null or had 0 elements
     */
    public static Size getMaxSize(Size[] sizes, float aspectRatio) {
        if (sizes == null || sizes.length == 0) {
            throw new IllegalArgumentException("sizes was empty");
        }

        Size sz = sizes[0];
        for (Size size : sizes) {
            float ar = 1.0f * size.getWidth() / size.getHeight();
            if (aspectRatio > 0 && Math.abs(ar - aspectRatio) > ASPECT_RATIO_MATCH_THRESHOLD) {
                continue;
            }

            if (size.getWidth() * size.getHeight() > sz.getWidth() * sz.getHeight()) {
                sz = size;
            }
        }

        return sz;
    }

    /**
     * Get the largest size by area within (less than) bound
     *
     * @param sizes an array of sizes, must have at least 1 element
     *
     * @return Largest Size. Null if no such size exists within bound.
     *
     * @throws IllegalArgumentException if sizes was null or had 0 elements, or bound is invalid.
     */
    public static Size getMaxSizeWithBound(Size[] sizes, int bound) {
        if (sizes == null || sizes.length == 0) {
            throw new IllegalArgumentException("sizes was empty");
        }
        if (bound <= 0) {
            throw new IllegalArgumentException("bound is invalid");
        }

        Size sz = null;
        for (Size size : sizes) {
            if (size.getWidth() * size.getHeight() >= bound) {
                continue;
            }

            if (sz == null ||
                    size.getWidth() * size.getHeight() > sz.getWidth() * sz.getHeight()) {
                sz = size;
            }
        }

        return sz;
    }

    /**
     * Get maximum size in list that's equal or smaller to than the bound.
     *
     * Returns null if no size is smaller than or equal to the bound.
     */
    private static Size getMaxSizeWithBound(Size[] sizes, Size bound) {
        return getMaxSizeWithBound(sizes, bound, -1 /*aspectRatio*/);
    }

    /**
     * Get maximum size in list that's equal or smaller to than the bound and matching
     * the aspect ratio.
     *
     * Returns null if no size is smaller than or equal to the bound while matching aspect
     * ratio.
     */
    private static Size getMaxSizeWithBound(Size[] sizes, Size bound, float aspectRatio) {
        if (sizes == null || sizes.length == 0) {
            throw new IllegalArgumentException("sizes was empty");
        }

        Size sz = null;
        for (Size size : sizes) {
            // If matching aspect ratio is needed, check aspect ratio
            float ar = 1.0f * size.getWidth() / size.getHeight();
            if (aspectRatio > 0 && Math.abs(ar - aspectRatio) > ASPECT_RATIO_MATCH_THRESHOLD) {
                continue;
            }

            if (size.getWidth() <= bound.getWidth() && size.getHeight() <= bound.getHeight()) {

                if (sz == null) {
                    sz = size;
                } else {
                    long curArea = sz.getWidth() * (long) sz.getHeight();
                    long newArea = size.getWidth() * (long) size.getHeight();
                    if (newArea > curArea) {
                        sz = size;
                    }
                }
            }
        }

        assertTrue("No size under bound found: " + Arrays.toString(sizes) + " bound " + bound,
                sz != null);

        return sz;
    }

    /**
     * Returns true if the given {@code array} contains the given element.
     *
     * @param array {@code array} to check for {@code elem}
     * @param elem {@code elem} to test for
     * @return {@code true} if the given element is contained
     */
    public static boolean contains(int[] array, int elem) {
        if (array == null) return false;
        for (int i = 0; i < array.length; i++) {
            if (elem == array[i]) return true;
        }
        return false;
    }

    public static boolean contains(long[] array, long elem) {
        if (array == null) return false;
        for (int i = 0; i < array.length; i++) {
            if (elem == array[i]) return true;
        }
        return false;
    }

    /**
     * Get object array from byte array.
     *
     * @param array Input byte array to be converted
     * @return Byte object array converted from input byte array
     */
    public static Byte[] toObject(byte[] array) {
        return convertPrimitiveArrayToObjectArray(array, Byte.class);
    }

    /**
     * Get object array from int array.
     *
     * @param array Input int array to be converted
     * @return Integer object array converted from input int array
     */
    public static Integer[] toObject(int[] array) {
        return convertPrimitiveArrayToObjectArray(array, Integer.class);
    }

    /**
     * Get object array from float array.
     *
     * @param array Input float array to be converted
     * @return Float object array converted from input float array
     */
    public static Float[] toObject(float[] array) {
        return convertPrimitiveArrayToObjectArray(array, Float.class);
    }

    /**
     * Get object array from double array.
     *
     * @param array Input double array to be converted
     * @return Double object array converted from input double array
     */
    public static Double[] toObject(double[] array) {
        return convertPrimitiveArrayToObjectArray(array, Double.class);
    }

    /**
     * Convert a primitive input array into its object array version (e.g. from int[] to Integer[]).
     *
     * @param array Input array object
     * @param wrapperClass The boxed class it converts to
     * @return Boxed version of primitive array
     */
    private static <T> T[] convertPrimitiveArrayToObjectArray(final Object array,
            final Class<T> wrapperClass) {
        // getLength does the null check and isArray check already.
        int arrayLength = Array.getLength(array);
        if (arrayLength == 0) {
            throw new IllegalArgumentException("Input array shouldn't be empty");
        }

        @SuppressWarnings("unchecked")
        final T[] result = (T[]) Array.newInstance(wrapperClass, arrayLength);
        for (int i = 0; i < arrayLength; i++) {
            Array.set(result, i, Array.get(array, i));
        }
        return result;
    }

    /**
     * Update one 3A region in capture request builder if that region is supported. Do nothing
     * if the specified 3A region is not supported by camera device.
     * @param requestBuilder The request to be updated
     * @param algoIdx The index to the algorithm. (AE: 0, AWB: 1, AF: 2)
     * @param regions The 3A regions to be set
     * @param staticInfo static metadata characteristics
     */
    public static void update3aRegion(
            CaptureRequest.Builder requestBuilder, int algoIdx, MeteringRectangle[] regions,
            StaticMetadata staticInfo)
    {
        int maxRegions;
        CaptureRequest.Key<MeteringRectangle[]> key;

        if (regions == null || regions.length == 0 || staticInfo == null) {
            throw new IllegalArgumentException("Invalid input 3A region!");
        }

        switch (algoIdx) {
            case INDEX_ALGORITHM_AE:
                maxRegions = staticInfo.getAeMaxRegionsChecked();
                key = CaptureRequest.CONTROL_AE_REGIONS;
                break;
            case INDEX_ALGORITHM_AWB:
                maxRegions = staticInfo.getAwbMaxRegionsChecked();
                key = CaptureRequest.CONTROL_AWB_REGIONS;
                break;
            case INDEX_ALGORITHM_AF:
                maxRegions = staticInfo.getAfMaxRegionsChecked();
                key = CaptureRequest.CONTROL_AF_REGIONS;
                break;
            default:
                throw new IllegalArgumentException("Unknown 3A Algorithm!");
        }

        if (maxRegions >= regions.length) {
            requestBuilder.set(key, regions);
        }
    }

    /**
     * Validate one 3A region in capture result equals to expected region if that region is
     * supported. Do nothing if the specified 3A region is not supported by camera device.
     * @param result The capture result to be validated
     * @param partialResults The partial results to be validated
     * @param algoIdx The index to the algorithm. (AE: 0, AWB: 1, AF: 2)
     * @param expectRegions The 3A regions expected in capture result
     * @param scaleByZoomRatio whether to scale the error threshold by zoom ratio
     * @param staticInfo static metadata characteristics
     */
    public static void validate3aRegion(
            CaptureResult result, List<CaptureResult> partialResults, int algoIdx,
            MeteringRectangle[] expectRegions, boolean scaleByZoomRatio, StaticMetadata staticInfo)
    {
        // There are multiple cases where result 3A region could be slightly different than the
        // request:
        // 1. Distortion correction,
        // 2. Adding smaller 3a region in the test exposes existing devices' offset is larger
        //    than 1.
        // 3. Precision loss due to converting to HAL zoom ratio and back
        // 4. Error magnification due to active array scale-up when zoom ratio API is used.
        //
        // To handle all these scenarios, make the threshold larger, and scale the threshold based
        // on zoom ratio. The scaling factor should be relatively tight, and shouldn't be smaller
        // than 1x.
        final int maxCoordOffset = 5;
        int maxRegions;
        CaptureResult.Key<MeteringRectangle[]> key;
        MeteringRectangle[] actualRegion;

        switch (algoIdx) {
            case INDEX_ALGORITHM_AE:
                maxRegions = staticInfo.getAeMaxRegionsChecked();
                key = CaptureResult.CONTROL_AE_REGIONS;
                break;
            case INDEX_ALGORITHM_AWB:
                maxRegions = staticInfo.getAwbMaxRegionsChecked();
                key = CaptureResult.CONTROL_AWB_REGIONS;
                break;
            case INDEX_ALGORITHM_AF:
                maxRegions = staticInfo.getAfMaxRegionsChecked();
                key = CaptureResult.CONTROL_AF_REGIONS;
                break;
            default:
                throw new IllegalArgumentException("Unknown 3A Algorithm!");
        }

        int maxDist = maxCoordOffset;
        if (scaleByZoomRatio) {
            Float zoomRatio = result.get(CaptureResult.CONTROL_ZOOM_RATIO);
            for (CaptureResult partialResult : partialResults) {
                Float zoomRatioInPartial = partialResult.get(CaptureResult.CONTROL_ZOOM_RATIO);
                if (zoomRatioInPartial != null) {
                    assertEquals("CONTROL_ZOOM_RATIO in partial result must match"
                            + " that in final result", zoomRatio, zoomRatioInPartial);
                }
            }
            maxDist = (int)Math.ceil(maxDist * Math.max(zoomRatio / 2, 1.0f));
        }

        if (maxRegions > 0)
        {
            actualRegion = getValueNotNull(result, key);
            for (CaptureResult partialResult : partialResults) {
                MeteringRectangle[] actualRegionInPartial = partialResult.get(key);
                if (actualRegionInPartial != null) {
                    assertEquals("Key " + key.getName() + " in partial result must match"
                            + " that in final result", actualRegionInPartial, actualRegion);
                }
            }

            for (int i = 0; i < actualRegion.length; i++) {
                // If the expected region's metering weight is 0, allow the camera device
                // to override it.
                if (expectRegions[i].getMeteringWeight() == 0) {
                    continue;
                }

                Rect a = actualRegion[i].getRect();
                Rect e = expectRegions[i].getRect();

                if (VERBOSE) {
                    Log.v(TAG, "Actual region " + actualRegion[i].toString() +
                            ", expected region " + expectRegions[i].toString() +
                            ", maxDist " + maxDist);
                }
                assertTrue(
                    "Expected 3A regions: " + Arrays.toString(expectRegions) +
                    " are not close enough to the actual one: " + Arrays.toString(actualRegion),
                    maxDist >= Math.abs(a.left - e.left));

                assertTrue(
                    "Expected 3A regions: " + Arrays.toString(expectRegions) +
                    " are not close enough to the actual one: " + Arrays.toString(actualRegion),
                    maxDist >= Math.abs(a.right - e.right));

                assertTrue(
                    "Expected 3A regions: " + Arrays.toString(expectRegions) +
                    " are not close enough to the actual one: " + Arrays.toString(actualRegion),
                    maxDist >= Math.abs(a.top - e.top));
                assertTrue(
                    "Expected 3A regions: " + Arrays.toString(expectRegions) +
                    " are not close enough to the actual one: " + Arrays.toString(actualRegion),
                    maxDist >= Math.abs(a.bottom - e.bottom));
            }
        }
    }

    public static void validateImage(Image image, int width, int height, int format,
            String filePath) {
        validateImage(image, width, height, format, filePath, /*colorSpace*/ null);
    }


    /**
     * Validate image based on format and size.
     *
     * @param image The image to be validated.
     * @param width The image width.
     * @param height The image height.
     * @param format The image format.
     * @param filePath The debug dump file path, null if don't want to dump to
     *            file.
     * @param colorSpace The expected color space of the image, if desired (null otherwise).
     * @throws UnsupportedOperationException if calling with an unknown format
     */
    public static void validateImage(Image image, int width, int height, int format,
            String filePath, ColorSpace colorSpace) {
        checkImage(image, width, height, format, colorSpace);

        if (format == ImageFormat.PRIVATE) {
            return;
        }

        /**
         * TODO: validate timestamp:
         * 1. capture result timestamp against the image timestamp (need
         * consider frame drops)
         * 2. timestamps should be monotonically increasing for different requests
         */
        if(VERBOSE) Log.v(TAG, "validating Image");
        byte[] data = getDataFromImage(image);
        assertTrue("Invalid image data", data != null && data.length > 0);

        switch (format) {
            // Clients must be able to process and handle depth jpeg images like any other
            // regular jpeg.
            case ImageFormat.DEPTH_JPEG:
            case ImageFormat.JPEG:
                validateJpegData(data, width, height, filePath, colorSpace);
                break;
            case ImageFormat.JPEG_R:
                validateJpegData(data, width, height, filePath, null /*colorSpace*/,
                        true /*gainMapPresent*/);
                break;
            case ImageFormat.YCBCR_P010:
                validateP010Data(data, width, height, format, image.getTimestamp(), filePath);
                break;
            case ImageFormat.YUV_420_888:
            case ImageFormat.YV12:
                validateYuvData(data, width, height, format, image.getTimestamp(), filePath);
                break;
            case ImageFormat.RAW_SENSOR:
                validateRaw16Data(data, width, height, format, image.getTimestamp(), filePath);
                break;
            case ImageFormat.DEPTH16:
                validateDepth16Data(data, width, height, format, image.getTimestamp(), filePath);
                break;
            case ImageFormat.DEPTH_POINT_CLOUD:
                validateDepthPointCloudData(data, width, height, format, image.getTimestamp(), filePath);
                break;
            case ImageFormat.RAW_PRIVATE:
                validateRawPrivateData(data, width, height, image.getTimestamp(), filePath);
                break;
            case ImageFormat.Y8:
                validateY8Data(data, width, height, format, image.getTimestamp(), filePath);
                break;
            case ImageFormat.HEIC:
                validateHeicData(data, width, height, filePath);
                break;
            default:
                throw new UnsupportedOperationException("Unsupported format for validation: "
                        + format);
        }
    }

    public static class HandlerExecutor implements Executor {
        private final Handler mHandler;

        public HandlerExecutor(Handler handler) {
            assertNotNull("handler must be valid", handler);
            mHandler = handler;
        }

        @Override
        public void execute(Runnable runCmd) {
            mHandler.post(runCmd);
        }
    }

    /**
     * Provide a mock for {@link CameraDevice.StateCallback}.
     *
     * <p>Only useful because mockito can't mock {@link CameraDevice.StateCallback} which is an
     * abstract class.</p>
     *
     * <p>
     * Use this instead of other classes when needing to verify interactions, since
     * trying to spy on {@link BlockingStateCallback} (or others) will cause unnecessary extra
     * interactions which will cause false test failures.
     * </p>
     *
     */
    public static class MockStateCallback extends CameraDevice.StateCallback {

        @Override
        public void onOpened(CameraDevice camera) {
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
        }

        @Override
        public void onError(CameraDevice camera, int error) {
        }

        private MockStateCallback() {}

        /**
         * Create a Mockito-ready mocked StateCallback.
         */
        public static MockStateCallback mock() {
            return Mockito.spy(new MockStateCallback());
        }
    }

    public static void validateJpegData(byte[] jpegData, int width, int height, String filePath) {
        validateJpegData(jpegData, width, height, filePath, /*colorSpace*/ null);
    }

    public static void validateJpegData(byte[] jpegData, int width, int height, String filePath,
            ColorSpace colorSpace) {
        validateJpegData(jpegData, width, height, filePath, colorSpace, false /*gainMapPresent*/);
    }

    public static void validateJpegData(byte[] jpegData, int width, int height, String filePath,
            ColorSpace colorSpace, boolean gainMapPresent) {
        BitmapFactory.Options bmpOptions = new BitmapFactory.Options();
        // DecodeBound mode: only parse the frame header to get width/height.
        // it doesn't decode the pixel.
        bmpOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length, bmpOptions);
        assertEquals(width, bmpOptions.outWidth);
        assertEquals(height, bmpOptions.outHeight);

        // Pixel decoding mode: decode whole image. check if the image data
        // is decodable here.
        Bitmap bitmapImage = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length);
        assertNotNull("Decoding jpeg failed", bitmapImage);
        if (colorSpace != null) {
            ColorSpace bitmapColorSpace = bitmapImage.getColorSpace();
            boolean matchingColorSpace = colorSpace.equals(bitmapColorSpace);
            if (!matchingColorSpace) {
                Log.e(TAG, "Expected color space:\n\t" + colorSpace);
                Log.e(TAG, "Bitmap color space:\n\t" + bitmapColorSpace);
            }
            assertTrue("Color space mismatch in decoded jpeg!", matchingColorSpace);
        }
        if (gainMapPresent) {
            Gainmap gainMap = bitmapImage.getGainmap();
            assertNotNull(gainMap);
            assertNotNull(gainMap.getGainmapContents());
        }
        if (DEBUG && filePath != null) {
            String fileName =
                    filePath + "/" + width + "x" + height + ".jpeg";
            dumpFile(fileName, jpegData);
        }
    }

    private static void validateYuvData(byte[] yuvData, int width, int height, int format,
            long ts, String filePath) {
        checkYuvFormat(format);
        if (VERBOSE) Log.v(TAG, "Validating YUV data");
        int expectedSize = width * height * ImageFormat.getBitsPerPixel(format) / 8;
        assertEquals("Yuv data doesn't match", expectedSize, yuvData.length);

        // TODO: Can add data validation for test pattern.

        if (DEBUG && filePath != null) {
            String fileName =
                    filePath + "/" + width + "x" + height + "_" + ts / 1e6 + ".yuv";
            dumpFile(fileName, yuvData);
        }
    }

    private static void validateP010Data(byte[] p010Data, int width, int height, int format,
            long ts, String filePath) {
        if (VERBOSE) Log.v(TAG, "Validating P010 data");
        // The P010 10 bit samples are stored in two bytes so the size needs to be adjusted
        // accordingly.
        int bytesPerPixelRounded = (ImageFormat.getBitsPerPixel(format) + 7) / 8;
        int expectedSize = width * height * bytesPerPixelRounded;
        assertEquals("P010 data doesn't match", expectedSize, p010Data.length);

        if (DEBUG && filePath != null) {
            String fileName =
                    filePath + "/" + width + "x" + height + "_" + ts / 1e6 + ".p010";
            dumpFile(fileName, p010Data);
        }
    }
    private static void validateRaw16Data(byte[] rawData, int width, int height, int format,
            long ts, String filePath) {
        if (VERBOSE) Log.v(TAG, "Validating raw data");
        int expectedSize = width * height * ImageFormat.getBitsPerPixel(format) / 8;
        assertEquals("Raw data doesn't match", expectedSize, rawData.length);

        // TODO: Can add data validation for test pattern.

        if (DEBUG && filePath != null) {
            String fileName =
                    filePath + "/" + width + "x" + height + "_" + ts / 1e6 + ".raw16";
            dumpFile(fileName, rawData);
        }

        return;
    }

    private static void validateY8Data(byte[] rawData, int width, int height, int format,
            long ts, String filePath) {
        if (VERBOSE) Log.v(TAG, "Validating Y8 data");
        int expectedSize = width * height * ImageFormat.getBitsPerPixel(format) / 8;
        assertEquals("Y8 data doesn't match", expectedSize, rawData.length);

        // TODO: Can add data validation for test pattern.

        if (DEBUG && filePath != null) {
            String fileName =
                    filePath + "/" + width + "x" + height + "_" + ts / 1e6 + ".y8";
            dumpFile(fileName, rawData);
        }

        return;
    }

    private static void validateRawPrivateData(byte[] rawData, int width, int height,
            long ts, String filePath) {
        if (VERBOSE) Log.v(TAG, "Validating private raw data");
        // Expect each RAW pixel should occupy at least one byte and no more than 30 bytes
        int expectedSizeMin = width * height;
        int expectedSizeMax = width * height * 30;

        assertTrue("Opaque RAW size " + rawData.length + "out of normal bound [" +
                expectedSizeMin + "," + expectedSizeMax + "]",
                expectedSizeMin <= rawData.length && rawData.length <= expectedSizeMax);

        if (DEBUG && filePath != null) {
            String fileName =
                    filePath + "/" + width + "x" + height + "_" + ts / 1e6 + ".rawPriv";
            dumpFile(fileName, rawData);
        }

        return;
    }

    private static void validateDepth16Data(byte[] depthData, int width, int height, int format,
            long ts, String filePath) {

        if (VERBOSE) Log.v(TAG, "Validating depth16 data");
        int expectedSize = width * height * ImageFormat.getBitsPerPixel(format) / 8;
        assertEquals("Depth data doesn't match", expectedSize, depthData.length);


        if (DEBUG && filePath != null) {
            String fileName =
                    filePath + "/" + width + "x" + height + "_" + ts / 1e6 + ".depth16";
            dumpFile(fileName, depthData);
        }

        return;

    }

    private static void validateDepthPointCloudData(byte[] depthData, int width, int height, int format,
            long ts, String filePath) {

        if (VERBOSE) Log.v(TAG, "Validating depth point cloud data");

        // Can't validate size since it is variable

        if (DEBUG && filePath != null) {
            String fileName =
                    filePath + "/" + width + "x" + height + "_" + ts / 1e6 + ".depth_point_cloud";
            dumpFile(fileName, depthData);
        }

        return;

    }

    private static void validateHeicData(byte[] heicData, int width, int height, String filePath) {
        BitmapFactory.Options bmpOptions = new BitmapFactory.Options();
        // DecodeBound mode: only parse the frame header to get width/height.
        // it doesn't decode the pixel.
        bmpOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(heicData, 0, heicData.length, bmpOptions);
        assertEquals(width, bmpOptions.outWidth);
        assertEquals(height, bmpOptions.outHeight);

        // Pixel decoding mode: decode whole image. check if the image data
        // is decodable here.
        assertNotNull("Decoding heic failed",
                BitmapFactory.decodeByteArray(heicData, 0, heicData.length));
        if (DEBUG && filePath != null) {
            String fileName =
                    filePath + "/" + width + "x" + height + ".heic";
            dumpFile(fileName, heicData);
        }
    }

    public static <T> T getValueNotNull(CaptureResult result, CaptureResult.Key<T> key) {
        if (result == null) {
            throw new IllegalArgumentException("Result must not be null");
        }

        T value = result.get(key);
        assertNotNull("Value of Key " + key.getName() + "shouldn't be null", value);
        return value;
    }

    public static <T> T getValueNotNull(CameraCharacteristics characteristics,
            CameraCharacteristics.Key<T> key) {
        if (characteristics == null) {
            throw new IllegalArgumentException("Camera characteristics must not be null");
        }

        T value = characteristics.get(key);
        assertNotNull("Value of Key " + key.getName() + "shouldn't be null", value);
        return value;
    }

    /**
     * Get a crop region for a given zoom factor and center position.
     * <p>
     * The center position is normalized position in range of [0, 1.0], where
     * (0, 0) represents top left corner, (1.0. 1.0) represents bottom right
     * corner. The center position could limit the effective minimal zoom
     * factor, for example, if the center position is (0.75, 0.75), the
     * effective minimal zoom position becomes 2.0. If the requested zoom factor
     * is smaller than 2.0, a crop region with 2.0 zoom factor will be returned.
     * </p>
     * <p>
     * The aspect ratio of the crop region is maintained the same as the aspect
     * ratio of active array.
     * </p>
     *
     * @param zoomFactor The zoom factor to generate the crop region, it must be
     *            >= 1.0
     * @param center The normalized zoom center point that is in the range of [0, 1].
     * @param maxZoom The max zoom factor supported by this device.
     * @param activeArray The active array size of this device.
     * @return crop region for the given normalized center and zoom factor.
     */
    public static Rect getCropRegionForZoom(float zoomFactor, final PointF center,
            final float maxZoom, final Rect activeArray) {
        if (zoomFactor < 1.0) {
            throw new IllegalArgumentException("zoom factor " + zoomFactor + " should be >= 1.0");
        }
        if (center.x > 1.0 || center.x < 0) {
            throw new IllegalArgumentException("center.x " + center.x
                    + " should be in range of [0, 1.0]");
        }
        if (center.y > 1.0 || center.y < 0) {
            throw new IllegalArgumentException("center.y " + center.y
                    + " should be in range of [0, 1.0]");
        }
        if (maxZoom < 1.0) {
            throw new IllegalArgumentException("max zoom factor " + maxZoom + " should be >= 1.0");
        }
        if (activeArray == null) {
            throw new IllegalArgumentException("activeArray must not be null");
        }

        float minCenterLength = Math.min(Math.min(center.x, 1.0f - center.x),
                Math.min(center.y, 1.0f - center.y));
        float minEffectiveZoom =  0.5f / minCenterLength;
        if (minEffectiveZoom > maxZoom) {
            throw new IllegalArgumentException("Requested center " + center.toString() +
                    " has minimal zoomable factor " + minEffectiveZoom + ", which exceeds max"
                            + " zoom factor " + maxZoom);
        }

        if (zoomFactor < minEffectiveZoom) {
            Log.w(TAG, "Requested zoomFactor " + zoomFactor + " < minimal zoomable factor "
                    + minEffectiveZoom + ". It will be overwritten by " + minEffectiveZoom);
            zoomFactor = minEffectiveZoom;
        }

        int cropCenterX = (int)(activeArray.width() * center.x);
        int cropCenterY = (int)(activeArray.height() * center.y);
        int cropWidth = (int) (activeArray.width() / zoomFactor);
        int cropHeight = (int) (activeArray.height() / zoomFactor);

        return new Rect(
                /*left*/cropCenterX - cropWidth / 2,
                /*top*/cropCenterY - cropHeight / 2,
                /*right*/ cropCenterX + cropWidth / 2,
                /*bottom*/cropCenterY + cropHeight / 2);
    }

    /**
     * Get AeAvailableTargetFpsRanges and sort them in descending order by max fps
     *
     * @param staticInfo camera static metadata
     * @return AeAvailableTargetFpsRanges in descending order by max fps
     */
    public static Range<Integer>[] getDescendingTargetFpsRanges(StaticMetadata staticInfo) {
        Range<Integer>[] fpsRanges = staticInfo.getAeAvailableTargetFpsRangesChecked();
        Arrays.sort(fpsRanges, new Comparator<Range<Integer>>() {
            public int compare(Range<Integer> r1, Range<Integer> r2) {
                return r2.getUpper() - r1.getUpper();
            }
        });
        return fpsRanges;
    }

    /**
     * Get AeAvailableTargetFpsRanges with max fps not exceeding 30
     *
     * @param staticInfo camera static metadata
     * @return AeAvailableTargetFpsRanges with max fps not exceeding 30
     */
    public static List<Range<Integer>> getTargetFpsRangesUpTo30(StaticMetadata staticInfo) {
        Range<Integer>[] fpsRanges = staticInfo.getAeAvailableTargetFpsRangesChecked();
        ArrayList<Range<Integer>> fpsRangesUpTo30 = new ArrayList<Range<Integer>>();
        for (Range<Integer> fpsRange : fpsRanges) {
            if (fpsRange.getUpper() <= 30) {
                fpsRangesUpTo30.add(fpsRange);
            }
        }
        return fpsRangesUpTo30;
    }

    /**
     * Get AeAvailableTargetFpsRanges with max fps greater than 30
     *
     * @param staticInfo camera static metadata
     * @return AeAvailableTargetFpsRanges with max fps greater than 30
     */
    public static List<Range<Integer>> getTargetFpsRangesGreaterThan30(StaticMetadata staticInfo) {
        Range<Integer>[] fpsRanges = staticInfo.getAeAvailableTargetFpsRangesChecked();
        ArrayList<Range<Integer>> fpsRangesGreaterThan30 = new ArrayList<Range<Integer>>();
        for (Range<Integer> fpsRange : fpsRanges) {
            if (fpsRange.getUpper() > 30) {
                fpsRangesGreaterThan30.add(fpsRange);
            }
        }
        return fpsRangesGreaterThan30;
    }

    /**
     * Calculate output 3A region from the intersection of input 3A region and cropped region.
     *
     * @param requestRegions The input 3A regions
     * @param cropRect The cropped region
     * @return expected 3A regions output in capture result
     */
    public static MeteringRectangle[] getExpectedOutputRegion(
            MeteringRectangle[] requestRegions, Rect cropRect){
        MeteringRectangle[] resultRegions = new MeteringRectangle[requestRegions.length];
        for (int i = 0; i < requestRegions.length; i++) {
            Rect requestRect = requestRegions[i].getRect();
            Rect resultRect = new Rect();
            boolean intersect = resultRect.setIntersect(requestRect, cropRect);
            resultRegions[i] = new MeteringRectangle(
                    resultRect,
                    intersect ? requestRegions[i].getMeteringWeight() : 0);
        }
        return resultRegions;
    }

    /**
     * Copy source image data to destination image.
     *
     * @param src The source image to be copied from.
     * @param dst The destination image to be copied to.
     * @throws IllegalArgumentException If the source and destination images have
     *             different format, size, or one of the images is not copyable.
     */
    public static void imageCopy(Image src, Image dst) {
        if (src == null || dst == null) {
            throw new IllegalArgumentException("Images should be non-null");
        }
        if (src.getFormat() != dst.getFormat()) {
            throw new IllegalArgumentException("Src and dst images should have the same format");
        }
        if (src.getFormat() == ImageFormat.PRIVATE ||
                dst.getFormat() == ImageFormat.PRIVATE) {
            throw new IllegalArgumentException("PRIVATE format images are not copyable");
        }

        Size srcSize = new Size(src.getWidth(), src.getHeight());
        Size dstSize = new Size(dst.getWidth(), dst.getHeight());
        if (!srcSize.equals(dstSize)) {
            throw new IllegalArgumentException("source image size " + srcSize + " is different"
                    + " with " + "destination image size " + dstSize);
        }

        // TODO: check the owner of the dst image, it must be from ImageWriter, other source may
        // not be writable. Maybe we should add an isWritable() method in image class.

        Plane[] srcPlanes = src.getPlanes();
        Plane[] dstPlanes = dst.getPlanes();
        ByteBuffer srcBuffer = null;
        ByteBuffer dstBuffer = null;
        for (int i = 0; i < srcPlanes.length; i++) {
            srcBuffer = srcPlanes[i].getBuffer();
            dstBuffer = dstPlanes[i].getBuffer();
            int srcPos = srcBuffer.position();
            srcBuffer.rewind();
            dstBuffer.rewind();
            int srcRowStride = srcPlanes[i].getRowStride();
            int dstRowStride = dstPlanes[i].getRowStride();
            int srcPixStride = srcPlanes[i].getPixelStride();
            int dstPixStride = dstPlanes[i].getPixelStride();

            if (srcPixStride > 2 || dstPixStride > 2) {
                throw new IllegalArgumentException("source pixel stride " + srcPixStride +
                        " with destination pixel stride " + dstPixStride +
                        " is not supported");
            }

            if (srcRowStride == dstRowStride && srcPixStride == dstPixStride &&
                    srcPixStride == 1) {
                // Fast path, just copy the content in the byteBuffer all together.
                dstBuffer.put(srcBuffer);
            } else {
                Size effectivePlaneSize = getEffectivePlaneSizeForImage(src, i);
                int srcRowByteCount = srcRowStride;
                int dstRowByteCount = dstRowStride;
                byte[] srcDataRow = new byte[Math.max(srcRowStride, dstRowStride)];

                if (srcPixStride == dstPixStride && srcPixStride == 1) {
                    // Row by row copy case
                    for (int row = 0; row < effectivePlaneSize.getHeight(); row++) {
                        if (row == effectivePlaneSize.getHeight() - 1) {
                            // Special case for interleaved planes: need handle the last row
                            // carefully to avoid memory corruption. Check if we have enough bytes
                            // to copy.
                            srcRowByteCount = Math.min(srcRowByteCount, srcBuffer.remaining());
                            dstRowByteCount = Math.min(dstRowByteCount, dstBuffer.remaining());
                        }
                        srcBuffer.get(srcDataRow, /*offset*/0, srcRowByteCount);
                        dstBuffer.put(srcDataRow, /*offset*/0, dstRowByteCount);
                    }
                } else {
                    // Row by row per pixel copy case
                    byte[] dstDataRow = new byte[dstRowByteCount];
                    for (int row = 0; row < effectivePlaneSize.getHeight(); row++) {
                        if (row == effectivePlaneSize.getHeight() - 1) {
                            // Special case for interleaved planes: need handle the last row
                            // carefully to avoid memory corruption. Check if we have enough bytes
                            // to copy.
                            int remainingBytes = srcBuffer.remaining();
                            if (srcRowByteCount > remainingBytes) {
                                srcRowByteCount = remainingBytes;
                            }
                            remainingBytes = dstBuffer.remaining();
                            if (dstRowByteCount > remainingBytes) {
                                dstRowByteCount = remainingBytes;
                            }
                        }
                        srcBuffer.get(srcDataRow, /*offset*/0, srcRowByteCount);
                        int pos = dstBuffer.position();
                        dstBuffer.get(dstDataRow, /*offset*/0, dstRowByteCount);
                        dstBuffer.position(pos);
                        for (int x = 0; x < effectivePlaneSize.getWidth(); x++) {
                            dstDataRow[x * dstPixStride] = srcDataRow[x * srcPixStride];
                        }
                        dstBuffer.put(dstDataRow, /*offset*/0, dstRowByteCount);
                    }
                }
            }
            srcBuffer.position(srcPos);
            dstBuffer.rewind();
        }
    }

    private static Size getEffectivePlaneSizeForImage(Image image, int planeIdx) {
        switch (image.getFormat()) {
            case ImageFormat.YUV_420_888:
                if (planeIdx == 0) {
                    return new Size(image.getWidth(), image.getHeight());
                } else {
                    return new Size(image.getWidth() / 2, image.getHeight() / 2);
                }
            case ImageFormat.JPEG:
            case ImageFormat.RAW_SENSOR:
            case ImageFormat.RAW10:
            case ImageFormat.RAW12:
            case ImageFormat.DEPTH16:
                return new Size(image.getWidth(), image.getHeight());
            case ImageFormat.PRIVATE:
                return new Size(0, 0);
            default:
                throw new UnsupportedOperationException(
                        String.format("Invalid image format %d", image.getFormat()));
        }
    }

    /**
     * <p>
     * Checks whether the two images are strongly equal.
     * </p>
     * <p>
     * Two images are strongly equal if and only if the data, formats, sizes,
     * and timestamps are same. For {@link ImageFormat#PRIVATE PRIVATE} format
     * images, the image data is not accessible thus the data comparison is
     * effectively skipped as the number of planes is zero.
     * </p>
     * <p>
     * Note that this method compares the pixel data even outside of the crop
     * region, which may not be necessary for general use case.
     * </p>
     *
     * @param lhsImg First image to be compared with.
     * @param rhsImg Second image to be compared with.
     * @return true if the two images are equal, false otherwise.
     * @throws IllegalArgumentException If either of image is null.
     */
    public static boolean isImageStronglyEqual(Image lhsImg, Image rhsImg) {
        if (lhsImg == null || rhsImg == null) {
            throw new IllegalArgumentException("Images should be non-null");
        }

        if (lhsImg.getFormat() != rhsImg.getFormat()) {
            Log.i(TAG, "lhsImg format " + lhsImg.getFormat() + " is different with rhsImg format "
                    + rhsImg.getFormat());
            return false;
        }

        if (lhsImg.getWidth() != rhsImg.getWidth()) {
            Log.i(TAG, "lhsImg width " + lhsImg.getWidth() + " is different with rhsImg width "
                    + rhsImg.getWidth());
            return false;
        }

        if (lhsImg.getHeight() != rhsImg.getHeight()) {
            Log.i(TAG, "lhsImg height " + lhsImg.getHeight() + " is different with rhsImg height "
                    + rhsImg.getHeight());
            return false;
        }

        if (lhsImg.getTimestamp() != rhsImg.getTimestamp()) {
            Log.i(TAG, "lhsImg timestamp " + lhsImg.getTimestamp()
                    + " is different with rhsImg timestamp " + rhsImg.getTimestamp());
            return false;
        }

        if (!lhsImg.getCropRect().equals(rhsImg.getCropRect())) {
            Log.i(TAG, "lhsImg crop rect " + lhsImg.getCropRect()
                    + " is different with rhsImg crop rect " + rhsImg.getCropRect());
            return false;
        }

        // Compare data inside of the image.
        Plane[] lhsPlanes = lhsImg.getPlanes();
        Plane[] rhsPlanes = rhsImg.getPlanes();
        ByteBuffer lhsBuffer = null;
        ByteBuffer rhsBuffer = null;
        for (int i = 0; i < lhsPlanes.length; i++) {
            lhsBuffer = lhsPlanes[i].getBuffer();
            rhsBuffer = rhsPlanes[i].getBuffer();
            lhsBuffer.rewind();
            rhsBuffer.rewind();
            // Special case for YUV420_888 buffer with different layout or
            // potentially differently interleaved U/V planes.
            if (lhsImg.getFormat() == ImageFormat.YUV_420_888 &&
                    (lhsPlanes[i].getPixelStride() != rhsPlanes[i].getPixelStride() ||
                     lhsPlanes[i].getRowStride() != rhsPlanes[i].getRowStride() ||
                     (lhsPlanes[i].getPixelStride() != 1))) {
                int width = getEffectivePlaneSizeForImage(lhsImg, i).getWidth();
                int height = getEffectivePlaneSizeForImage(lhsImg, i).getHeight();
                int rowSizeL = lhsPlanes[i].getRowStride();
                int rowSizeR = rhsPlanes[i].getRowStride();
                byte[] lhsRow = new byte[rowSizeL];
                byte[] rhsRow = new byte[rowSizeR];
                int pixStrideL = lhsPlanes[i].getPixelStride();
                int pixStrideR = rhsPlanes[i].getPixelStride();
                for (int r = 0; r < height; r++) {
                    if (r == height -1) {
                        rowSizeL = lhsBuffer.remaining();
                        rowSizeR = rhsBuffer.remaining();
                    }
                    lhsBuffer.get(lhsRow, /*offset*/0, rowSizeL);
                    rhsBuffer.get(rhsRow, /*offset*/0, rowSizeR);
                    for (int c = 0; c < width; c++) {
                        if (lhsRow[c * pixStrideL] != rhsRow[c * pixStrideR]) {
                            Log.i(TAG, String.format(
                                    "byte buffers for plane %d row %d col %d don't match.",
                                    i, r, c));
                            return false;
                        }
                    }
                }
            } else {
                // Compare entire buffer directly
                if (!lhsBuffer.equals(rhsBuffer)) {
                    Log.i(TAG, "byte buffers for plane " +  i + " don't match.");
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Set jpeg related keys in a capture request builder.
     *
     * @param builder The capture request builder to set the keys inl
     * @param exifData The exif data to set.
     * @param thumbnailSize The thumbnail size to set.
     * @param collector The camera error collector to collect errors.
     */
    public static void setJpegKeys(CaptureRequest.Builder builder, ExifTestData exifData,
            Size thumbnailSize, CameraErrorCollector collector) {
        builder.set(CaptureRequest.JPEG_THUMBNAIL_SIZE, thumbnailSize);
        builder.set(CaptureRequest.JPEG_GPS_LOCATION, exifData.gpsLocation);
        builder.set(CaptureRequest.JPEG_ORIENTATION, exifData.jpegOrientation);
        builder.set(CaptureRequest.JPEG_QUALITY, exifData.jpegQuality);
        builder.set(CaptureRequest.JPEG_THUMBNAIL_QUALITY,
                exifData.thumbnailQuality);

        // Validate request set and get.
        collector.expectEquals("JPEG thumbnail size request set and get should match",
                thumbnailSize, builder.get(CaptureRequest.JPEG_THUMBNAIL_SIZE));
        collector.expectTrue("GPS locations request set and get should match.",
                areGpsFieldsEqual(exifData.gpsLocation,
                builder.get(CaptureRequest.JPEG_GPS_LOCATION)));
        collector.expectEquals("JPEG orientation request set and get should match",
                exifData.jpegOrientation,
                builder.get(CaptureRequest.JPEG_ORIENTATION));
        collector.expectEquals("JPEG quality request set and get should match",
                exifData.jpegQuality, builder.get(CaptureRequest.JPEG_QUALITY));
        collector.expectEquals("JPEG thumbnail quality request set and get should match",
                exifData.thumbnailQuality,
                builder.get(CaptureRequest.JPEG_THUMBNAIL_QUALITY));
    }

    /**
     * Simple validation of JPEG image size and format.
     * <p>
     * Only validate the image object basic correctness. It is fast, but doesn't actually
     * check the buffer data. Assert is used here as it make no sense to
     * continue the test if the jpeg image captured has some serious failures.
     * </p>
     *
     * @param image The captured JPEG/HEIC image
     * @param expectedSize Expected capture JEPG/HEIC size
     * @param format JPEG/HEIC image format
     */
    public static void basicValidateBlobImage(Image image, Size expectedSize, int format) {
        Size imageSz = new Size(image.getWidth(), image.getHeight());
        assertTrue(
                String.format("Image size doesn't match (expected %s, actual %s) ",
                        expectedSize.toString(), imageSz.toString()), expectedSize.equals(imageSz));
        assertEquals("Image format should be " + ((format == ImageFormat.HEIC) ? "HEIC" : "JPEG"),
                format, image.getFormat());
        assertNotNull("Image plane shouldn't be null", image.getPlanes());
        assertEquals("Image plane number should be 1", 1, image.getPlanes().length);

        // Jpeg/Heic decoding validate was done in ImageReaderTest,
        // no need to duplicate the test here.
    }

    /**
     * Verify the EXIF and JPEG related keys in a capture result are expected.
     * - Capture request get values are same as were set.
     * - capture result's exif data is the same as was set by
     *   the capture request.
     * - new tags in the result set by the camera service are
     *   present and semantically correct.
     *
     * @param image The output JPEG/HEIC image to verify.
     * @param captureResult The capture result to verify.
     * @param expectedSize The expected JPEG/HEIC size.
     * @param expectedThumbnailSize The expected thumbnail size.
     * @param expectedExifData The expected EXIF data
     * @param staticInfo The static metadata for the camera device.
     * @param allStaticInfo The camera Id to static metadata map for all cameras.
     * @param blobFilename The filename to dump the jpeg/heic to.
     * @param collector The camera error collector to collect errors.
     * @param format JPEG/HEIC format
     */
    public static void verifyJpegKeys(Image image, CaptureResult captureResult, Size expectedSize,
            Size expectedThumbnailSize, ExifTestData expectedExifData, StaticMetadata staticInfo,
            HashMap<String, StaticMetadata> allStaticInfo, CameraErrorCollector collector,
            String debugFileNameBase, int format) throws Exception {

        basicValidateBlobImage(image, expectedSize, format);

        byte[] blobBuffer = getDataFromImage(image);
        // Have to dump into a file to be able to use ExifInterface
        String filePostfix = (format == ImageFormat.HEIC ? ".heic" : ".jpeg");
        String blobFilename = debugFileNameBase + "/verifyJpegKeys" + filePostfix;
        dumpFile(blobFilename, blobBuffer);
        ExifInterface exif = new ExifInterface(blobFilename);

        if (expectedThumbnailSize.equals(new Size(0,0))) {
            collector.expectTrue("Jpeg shouldn't have thumbnail when thumbnail size is (0, 0)",
                    !exif.hasThumbnail());
        } else {
            collector.expectTrue("Jpeg must have thumbnail for thumbnail size " +
                    expectedThumbnailSize, exif.hasThumbnail());
        }

        // Validate capture result vs. request
        Size resultThumbnailSize = captureResult.get(CaptureResult.JPEG_THUMBNAIL_SIZE);
        int orientationTested = expectedExifData.jpegOrientation;
        // Legacy shim always doesn't rotate thumbnail size
        if ((orientationTested == 90 || orientationTested == 270) &&
                staticInfo.isHardwareLevelAtLeastLimited()) {
            int exifOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    /*defaultValue*/-1);
            if (exifOrientation == ExifInterface.ORIENTATION_UNDEFINED) {
                // Device physically rotated image+thumbnail data
                // Expect thumbnail size to be also rotated
                resultThumbnailSize = new Size(resultThumbnailSize.getHeight(),
                        resultThumbnailSize.getWidth());
            }
        }

        collector.expectEquals("JPEG thumbnail size result and request should match",
                expectedThumbnailSize, resultThumbnailSize);
        if (collector.expectKeyValueNotNull(captureResult, CaptureResult.JPEG_GPS_LOCATION) !=
                null) {
            collector.expectTrue("GPS location result and request should match.",
                    areGpsFieldsEqual(expectedExifData.gpsLocation,
                    captureResult.get(CaptureResult.JPEG_GPS_LOCATION)));
        }
        collector.expectEquals("JPEG orientation result and request should match",
                expectedExifData.jpegOrientation,
                captureResult.get(CaptureResult.JPEG_ORIENTATION));
        collector.expectEquals("JPEG quality result and request should match",
                expectedExifData.jpegQuality, captureResult.get(CaptureResult.JPEG_QUALITY));
        collector.expectEquals("JPEG thumbnail quality result and request should match",
                expectedExifData.thumbnailQuality,
                captureResult.get(CaptureResult.JPEG_THUMBNAIL_QUALITY));

        // Validate other exif tags for all non-legacy devices
        if (!staticInfo.isHardwareLevelLegacy()) {
            verifyJpegExifExtraTags(exif, expectedSize, captureResult, staticInfo, allStaticInfo,
                    collector, expectedExifData);
        }
    }

    public static Optional<Long> getSurfaceUsage(Surface s) {
        if (s == null || !s.isValid()) {
            Log.e(TAG, "Invalid Surface!");
            return Optional.empty();
        }

        long usage = 0;
        ImageWriter writer = ImageWriter.newInstance(s, /*maxImages*/1, ImageFormat.YUV_420_888);
        try {
            Image img = writer.dequeueInputImage();
            if (img != null) {
                usage = img.getHardwareBuffer().getUsage();
                img.close();
            } else {
                Log.e(TAG, "Unable to dequeue ImageWriter buffer!");
                return Optional.empty();
            }
        } finally {
            writer.close();
        }

        return Optional.of(usage);
    }

    /**
     * Get the degree of an EXIF orientation.
     */
    private static int getExifOrientationInDegree(int exifOrientation,
            CameraErrorCollector collector) {
        switch (exifOrientation) {
            case ExifInterface.ORIENTATION_NORMAL:
                return 0;
            case ExifInterface.ORIENTATION_ROTATE_90:
                return 90;
            case ExifInterface.ORIENTATION_ROTATE_180:
                return 180;
            case ExifInterface.ORIENTATION_ROTATE_270:
                return 270;
            default:
                collector.addMessage("It is impossible to get non 0, 90, 180, 270 degress exif" +
                        "info based on the request orientation range");
                return 0;
        }
    }

    /**
     * Get all of the supported focal lengths for capture result.
     *
     * If the camera is a logical camera, return the focal lengths of the logical camera
     * and its active physical camera.
     *
     * If the camera isn't a logical camera, return the focal lengths supported by the
     * single camera.
     */
    public static Set<Float> getAvailableFocalLengthsForResult(CaptureResult result,
            StaticMetadata staticInfo,
            HashMap<String, StaticMetadata> allStaticInfo) {
        Set<Float> focalLengths = new HashSet<Float>();
        float[] supportedFocalLengths = staticInfo.getAvailableFocalLengthsChecked();
        for (float focalLength : supportedFocalLengths) {
            focalLengths.add(focalLength);
        }

        if (staticInfo.isLogicalMultiCamera()) {
            boolean activePhysicalCameraIdSupported =
                    staticInfo.isActivePhysicalCameraIdSupported();
            Set<String> physicalCameraIds;
            if (activePhysicalCameraIdSupported) {
                String activePhysicalCameraId = result.get(
                        CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID);
                physicalCameraIds = new HashSet<String>();
                physicalCameraIds.add(activePhysicalCameraId);
            } else {
                physicalCameraIds = staticInfo.getCharacteristics().getPhysicalCameraIds();
            }

            for (String physicalCameraId : physicalCameraIds) {
                StaticMetadata physicalStaticInfo = allStaticInfo.get(physicalCameraId);
                if (physicalStaticInfo != null) {
                    float[] focalLengthsArray =
                            physicalStaticInfo.getAvailableFocalLengthsChecked();
                    for (float focalLength: focalLengthsArray) {
                        focalLengths.add(focalLength);
                    }
                }
            }
        }

        return focalLengths;
    }

    /**
     * Validate and return the focal length.
     *
     * @param result Capture result to get the focal length
     * @param supportedFocalLengths Valid focal lengths to check the result focal length against
     * @param collector The camera error collector
     * @return Focal length from capture result or -1 if focal length is not available.
     */
    private static float validateFocalLength(CaptureResult result,
            Set<Float> supportedFocalLengths, CameraErrorCollector collector) {
        Float resultFocalLength = result.get(CaptureResult.LENS_FOCAL_LENGTH);
        if (collector.expectTrue("Focal length is invalid",
                resultFocalLength != null && resultFocalLength > 0)) {
            collector.expectTrue("Focal length should be one of the available focal length",
                    supportedFocalLengths.contains(resultFocalLength));
            return resultFocalLength;
        }
        return -1;
    }

    /**
     * Get all of the supported apertures for capture result.
     *
     * If the camera is a logical camera, return the apertures of the logical camera
     * and its active physical camera.
     *
     * If the camera isn't a logical camera, return the apertures supported by the
     * single camera.
     */
    private static Set<Float> getAvailableAperturesForResult(CaptureResult result,
            StaticMetadata staticInfo, HashMap<String, StaticMetadata> allStaticInfo) {
        Set<Float> allApertures = new HashSet<Float>();
        float[] supportedApertures = staticInfo.getAvailableAperturesChecked();
        for (float aperture : supportedApertures) {
            allApertures.add(aperture);
        }

        if (staticInfo.isLogicalMultiCamera()) {
            boolean activePhysicalCameraIdSupported =
                    staticInfo.isActivePhysicalCameraIdSupported();
            Set<String> physicalCameraIds;
            if (activePhysicalCameraIdSupported) {
                String activePhysicalCameraId = result.get(
                        CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID);
                physicalCameraIds = new HashSet<String>();
                physicalCameraIds.add(activePhysicalCameraId);
            } else {
                physicalCameraIds = staticInfo.getCharacteristics().getPhysicalCameraIds();
            }

            for (String physicalCameraId : physicalCameraIds) {
                StaticMetadata physicalStaticInfo = allStaticInfo.get(physicalCameraId);
                if (physicalStaticInfo != null) {
                    float[] apertures = physicalStaticInfo.getAvailableAperturesChecked();
                    for (float aperture: apertures) {
                        allApertures.add(aperture);
                    }
                }
            }
        }

        return allApertures;
    }

    /**
     * Validate and return the aperture.
     *
     * @param result Capture result to get the aperture
     * @return Aperture from capture result or -1 if aperture is not available.
     */
    private static float validateAperture(CaptureResult result,
            Set<Float> supportedApertures, CameraErrorCollector collector) {
        Float resultAperture = result.get(CaptureResult.LENS_APERTURE);
        if (collector.expectTrue("Capture result aperture is invalid",
                resultAperture != null && resultAperture > 0)) {
            collector.expectTrue("Aperture should be one of the available apertures",
                    supportedApertures.contains(resultAperture));
            return resultAperture;
        }
        return -1;
    }

    /**
     * Return the closest value in a Set of floats.
     */
    private static float getClosestValueInSet(Set<Float> values, float target) {
        float minDistance = Float.MAX_VALUE;
        float closestValue = -1.0f;
        for(float value : values) {
            float distance = Math.abs(value - target);
            if (minDistance > distance) {
                minDistance = distance;
                closestValue = value;
            }
        }

        return closestValue;
    }

    /**
     * Return if two Location's GPS field are the same.
     */
    private static boolean areGpsFieldsEqual(Location a, Location b) {
        if (a == null || b == null) {
            return false;
        }

        return a.getTime() == b.getTime() && a.getLatitude() == b.getLatitude() &&
                a.getLongitude() == b.getLongitude() && a.getAltitude() == b.getAltitude() &&
                a.getProvider() == b.getProvider();
    }

    /**
     * Verify extra tags in JPEG EXIF
     */
    private static void verifyJpegExifExtraTags(ExifInterface exif, Size jpegSize,
            CaptureResult result, StaticMetadata staticInfo,
            HashMap<String, StaticMetadata> allStaticInfo,
            CameraErrorCollector collector, ExifTestData expectedExifData)
            throws ParseException {
        /**
         * TAG_IMAGE_WIDTH and TAG_IMAGE_LENGTH and TAG_ORIENTATION.
         * Orientation and exif width/height need to be tested carefully, two cases:
         *
         * 1. Device rotate the image buffer physically, then exif width/height may not match
         * the requested still capture size, we need swap them to check.
         *
         * 2. Device use the exif tag to record the image orientation, it doesn't rotate
         * the jpeg image buffer itself. In this case, the exif width/height should always match
         * the requested still capture size, and the exif orientation should always match the
         * requested orientation.
         *
         */
        int exifWidth = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, /*defaultValue*/0);
        int exifHeight = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, /*defaultValue*/0);
        Size exifSize = new Size(exifWidth, exifHeight);
        // Orientation could be missing, which is ok, default to 0.
        int exifOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                /*defaultValue*/-1);
        // Get requested orientation from result, because they should be same.
        if (collector.expectKeyValueNotNull(result, CaptureResult.JPEG_ORIENTATION) != null) {
            int requestedOrientation = result.get(CaptureResult.JPEG_ORIENTATION);
            final int ORIENTATION_MIN = ExifInterface.ORIENTATION_UNDEFINED;
            final int ORIENTATION_MAX = ExifInterface.ORIENTATION_ROTATE_270;
            boolean orientationValid = collector.expectTrue(String.format(
                    "Exif orientation must be in range of [%d, %d]",
                    ORIENTATION_MIN, ORIENTATION_MAX),
                    exifOrientation >= ORIENTATION_MIN && exifOrientation <= ORIENTATION_MAX);
            if (orientationValid) {
                /**
                 * Device captured image doesn't respect the requested orientation,
                 * which means it rotates the image buffer physically. Then we
                 * should swap the exif width/height accordingly to compare.
                 */
                boolean deviceRotatedImage = exifOrientation == ExifInterface.ORIENTATION_UNDEFINED;

                if (deviceRotatedImage) {
                    // Case 1.
                    boolean needSwap = (requestedOrientation % 180 == 90);
                    if (needSwap) {
                        exifSize = new Size(exifHeight, exifWidth);
                    }
                } else {
                    // Case 2.
                    collector.expectEquals("Exif orientaiton should match requested orientation",
                            requestedOrientation, getExifOrientationInDegree(exifOrientation,
                            collector));
                }
            }
        }

        /**
         * Ideally, need check exifSize == jpegSize == actual buffer size. But
         * jpegSize == jpeg decode bounds size(from jpeg jpeg frame
         * header, not exif) was validated in ImageReaderTest, no need to
         * validate again here.
         */
        collector.expectEquals("Exif size should match jpeg capture size", jpegSize, exifSize);

        // TAG_DATETIME, it should be local time
        long currentTimeInMs = System.currentTimeMillis();
        long currentTimeInSecond = currentTimeInMs / 1000;
        Date date = new Date(currentTimeInMs);
        String localDatetime = new SimpleDateFormat("yyyy:MM:dd HH:").format(date);
        String dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME);
        if (collector.expectTrue("Exif TAG_DATETIME shouldn't be null", dateTime != null)) {
            collector.expectTrue("Exif TAG_DATETIME is wrong",
                    dateTime.length() == EXIF_DATETIME_LENGTH);
            long exifTimeInSecond =
                    new SimpleDateFormat("yyyy:MM:dd HH:mm:ss").parse(dateTime).getTime() / 1000;
            long delta = currentTimeInSecond - exifTimeInSecond;
            collector.expectTrue("Capture time deviates too much from the current time",
                    Math.abs(delta) < EXIF_DATETIME_ERROR_MARGIN_SEC);
            // It should be local time.
            collector.expectTrue("Exif date time should be local time",
                    dateTime.startsWith(localDatetime));
        }

        boolean isExternalCamera = staticInfo.isExternalCamera();
        if (!isExternalCamera) {
            // TAG_FOCAL_LENGTH.
            Set<Float> focalLengths = getAvailableFocalLengthsForResult(
                    result, staticInfo, allStaticInfo);
            float exifFocalLength = (float)exif.getAttributeDouble(
                        ExifInterface.TAG_FOCAL_LENGTH, -1);
            collector.expectEquals("Focal length should match",
                    getClosestValueInSet(focalLengths, exifFocalLength),
                    exifFocalLength, EXIF_FOCAL_LENGTH_ERROR_MARGIN);
            // More checks for focal length.
            collector.expectEquals("Exif focal length should match capture result",
                    validateFocalLength(result, focalLengths, collector),
                    exifFocalLength, EXIF_FOCAL_LENGTH_ERROR_MARGIN);

            // TAG_EXPOSURE_TIME
            // ExifInterface API gives exposure time value in the form of float instead of rational
            String exposureTime = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME);
            collector.expectNotNull("Exif TAG_EXPOSURE_TIME shouldn't be null", exposureTime);
            if (staticInfo.areKeysAvailable(CaptureResult.SENSOR_EXPOSURE_TIME)) {
                if (exposureTime != null) {
                    double exposureTimeValue = Double.parseDouble(exposureTime);
                    long expTimeResult = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
                    double expected = expTimeResult / 1e9;
                    double tolerance = expected * EXIF_EXPOSURE_TIME_ERROR_MARGIN_RATIO;
                    tolerance = Math.max(tolerance, EXIF_EXPOSURE_TIME_MIN_ERROR_MARGIN_SEC);
                    collector.expectEquals("Exif exposure time doesn't match", expected,
                            exposureTimeValue, tolerance);
                }
            }

            // TAG_APERTURE
            // ExifInterface API gives aperture value in the form of float instead of rational
            String exifAperture = exif.getAttribute(ExifInterface.TAG_APERTURE);
            collector.expectNotNull("Exif TAG_APERTURE shouldn't be null", exifAperture);
            if (staticInfo.areKeysAvailable(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)) {
                Set<Float> apertures = getAvailableAperturesForResult(
                        result, staticInfo, allStaticInfo);
                if (exifAperture != null) {
                    float apertureValue = Float.parseFloat(exifAperture);
                    collector.expectEquals("Aperture value should match",
                            getClosestValueInSet(apertures, apertureValue),
                            apertureValue, EXIF_APERTURE_ERROR_MARGIN);
                    // More checks for aperture.
                    collector.expectEquals("Exif aperture length should match capture result",
                            validateAperture(result, apertures, collector),
                            apertureValue, EXIF_APERTURE_ERROR_MARGIN);
                }
            }

            // TAG_MAKE
            String make = exif.getAttribute(ExifInterface.TAG_MAKE);
            collector.expectEquals("Exif TAG_MAKE is incorrect", Build.MANUFACTURER, make);

            // TAG_MODEL
            String model = exif.getAttribute(ExifInterface.TAG_MODEL);
            collector.expectEquals("Exif TAG_MODEL is incorrect", Build.MODEL, model);


            // TAG_ISO
            int iso = exif.getAttributeInt(ExifInterface.TAG_ISO, /*defaultValue*/-1);
            if (staticInfo.areKeysAvailable(CaptureResult.SENSOR_SENSITIVITY) ||
                    staticInfo.areKeysAvailable(CaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST)) {
                int expectedIso = 100;
                if (staticInfo.areKeysAvailable(CaptureResult.SENSOR_SENSITIVITY)) {
                    expectedIso = result.get(CaptureResult.SENSOR_SENSITIVITY);
                }
                if (staticInfo.areKeysAvailable(CaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST)) {
                    expectedIso = expectedIso *
                            result.get(CaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST);
                } else {
                    expectedIso *= 100;
                }
                collector.expectInRange("Exif TAG_ISO is incorrect", iso,
                        expectedIso/100,((expectedIso + 50)/100) + MAX_ISO_MISMATCH);
            }
        } else {
            // External camera specific checks
            // TAG_MAKE
            String make = exif.getAttribute(ExifInterface.TAG_MAKE);
            collector.expectNotNull("Exif TAG_MAKE is null", make);

            // TAG_MODEL
            String model = exif.getAttribute(ExifInterface.TAG_MODEL);
            collector.expectNotNull("Exif TAG_MODEL is nuill", model);
        }


        /**
         * TAG_FLASH. TODO: For full devices, can check a lot more info
         * (http://www.sno.phy.queensu.ca/~phil/exiftool/TagNames/EXIF.html#Flash)
         */
        String flash = exif.getAttribute(ExifInterface.TAG_FLASH);
        collector.expectNotNull("Exif TAG_FLASH shouldn't be null", flash);

        /**
         * TAG_WHITE_BALANCE. TODO: For full devices, with the DNG tags, we
         * should be able to cross-check android.sensor.referenceIlluminant.
         */
        String whiteBalance = exif.getAttribute(ExifInterface.TAG_WHITE_BALANCE);
        collector.expectNotNull("Exif TAG_WHITE_BALANCE shouldn't be null", whiteBalance);

        // TAG_DATETIME_DIGITIZED (a.k.a Create time for digital cameras).
        String digitizedTime = exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED);
        collector.expectNotNull("Exif TAG_DATETIME_DIGITIZED shouldn't be null", digitizedTime);
        if (digitizedTime != null) {
            String expectedDateTime = exif.getAttribute(ExifInterface.TAG_DATETIME);
            collector.expectNotNull("Exif TAG_DATETIME shouldn't be null", expectedDateTime);
            if (expectedDateTime != null) {
                collector.expectEquals("dataTime should match digitizedTime",
                        expectedDateTime, digitizedTime);
            }
        }

        /**
         * TAG_SUBSEC_TIME. Since the sub second tag strings are truncated to at
         * most 9 digits in ExifInterface implementation, use getAttributeInt to
         * sanitize it. When the default value -1 is returned, it means that
         * this exif tag either doesn't exist or is a non-numerical invalid
         * string. Same rule applies to the rest of sub second tags.
         */
        int subSecTime = exif.getAttributeInt(ExifInterface.TAG_SUBSEC_TIME, /*defaultValue*/-1);
        collector.expectTrue("Exif TAG_SUBSEC_TIME value is null or invalid!", subSecTime >= 0);

        // TAG_SUBSEC_TIME_ORIG
        int subSecTimeOrig = exif.getAttributeInt(ExifInterface.TAG_SUBSEC_TIME_ORIG,
                /*defaultValue*/-1);
        collector.expectTrue("Exif TAG_SUBSEC_TIME_ORIG value is null or invalid!",
                subSecTimeOrig >= 0);

        // TAG_SUBSEC_TIME_DIG
        int subSecTimeDig = exif.getAttributeInt(ExifInterface.TAG_SUBSEC_TIME_DIG,
                /*defaultValue*/-1);
        collector.expectTrue(
                "Exif TAG_SUBSEC_TIME_DIG value is null or invalid!", subSecTimeDig >= 0);

        /**
         * TAG_GPS_DATESTAMP & TAG_GPS_TIMESTAMP.
         * The GPS timestamp information should be in seconds UTC time.
         */
        String gpsDatestamp = exif.getAttribute(ExifInterface.TAG_GPS_DATESTAMP);
        collector.expectNotNull("Exif TAG_GPS_DATESTAMP shouldn't be null", gpsDatestamp);
        String gpsTimestamp = exif.getAttribute(ExifInterface.TAG_GPS_TIMESTAMP);
        collector.expectNotNull("Exif TAG_GPS_TIMESTAMP shouldn't be null", gpsTimestamp);

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy:MM:dd hh:mm:ss z");
        String gpsExifTimeString = gpsDatestamp + " " + gpsTimestamp + " UTC";
        Date gpsDateTime = dateFormat.parse(gpsExifTimeString);
        Date expected = new Date(expectedExifData.gpsLocation.getTime());
        collector.expectEquals("Jpeg EXIF GPS time should match", expected, gpsDateTime);
    }


    /**
     * Immutable class wrapping the exif test data.
     */
    public static class ExifTestData {
        public final Location gpsLocation;
        public final int jpegOrientation;
        public final byte jpegQuality;
        public final byte thumbnailQuality;

        public ExifTestData(Location location, int orientation,
                byte jpgQuality, byte thumbQuality) {
            gpsLocation = location;
            jpegOrientation = orientation;
            jpegQuality = jpgQuality;
            thumbnailQuality = thumbQuality;
        }
    }

    public static Size getPreviewSizeBound(WindowManager windowManager, Size bound) {
        WindowMetrics windowMetrics = windowManager.getCurrentWindowMetrics();
        Rect windowBounds = windowMetrics.getBounds();

        int windowHeight = windowBounds.height();
        int windowWidth = windowBounds.width();

        if (windowHeight > windowWidth) {
            windowHeight = windowWidth;
            windowWidth = windowBounds.height();
        }

        if (bound.getWidth() <= windowWidth
                && bound.getHeight() <= windowHeight) {
            return bound;
        } else {
            return new Size(windowWidth, windowHeight);
        }
    }

    /**
     * Check if a particular stream configuration is supported by configuring it
     * to the device.
     */
    public static boolean isStreamConfigurationSupported(CameraDevice camera,
            List<Surface> outputSurfaces,
            CameraCaptureSession.StateCallback listener, Handler handler) {
        try {
            configureCameraSession(camera, outputSurfaces, listener, handler);
            return true;
        } catch (Exception e) {
            Log.i(TAG, "This stream configuration is not supported due to " + e.getMessage());
            return false;
        }
    }

    public final static class SessionConfigSupport {
        public final boolean error;
        public final boolean callSupported;
        public final boolean configSupported;

        public SessionConfigSupport(boolean error,
                boolean callSupported, boolean configSupported) {
            this.error = error;
            this.callSupported = callSupported;
            this.configSupported = configSupported;
        }
    }

    /**
     * Query whether a particular stream combination is supported.
     */
    public static void checkSessionConfigurationWithSurfaces(CameraDevice camera,
            Handler handler, List<Surface> outputSurfaces, InputConfiguration inputConfig,
            int operatingMode, boolean defaultSupport, String msg) {
        List<OutputConfiguration> outConfigurations = new ArrayList<>(outputSurfaces.size());
        for (Surface surface : outputSurfaces) {
            outConfigurations.add(new OutputConfiguration(surface));
        }

        checkSessionConfigurationSupported(camera, handler, outConfigurations,
                inputConfig, operatingMode, defaultSupport, msg);
    }

    public static void checkSessionConfigurationSupported(CameraDevice camera,
            Handler handler, List<OutputConfiguration> outputConfigs,
            InputConfiguration inputConfig, int operatingMode, boolean defaultSupport,
            String msg) {
        SessionConfigSupport sessionConfigSupported =
                isSessionConfigSupported(camera, handler, outputConfigs, inputConfig,
                operatingMode, defaultSupport);

        assertTrue(msg, !sessionConfigSupported.error && sessionConfigSupported.configSupported);
    }

    /**
     * Query whether a particular stream combination is supported.
     */
    public static SessionConfigSupport isSessionConfigSupported(CameraDevice camera,
            Handler handler, List<OutputConfiguration> outputConfigs,
            InputConfiguration inputConfig, int operatingMode, boolean defaultSupport) {
        boolean ret;
        BlockingSessionCallback sessionListener = new BlockingSessionCallback();

        SessionConfiguration sessionConfig = new SessionConfiguration(operatingMode, outputConfigs,
                new HandlerExecutor(handler), sessionListener);
        if (inputConfig != null) {
            sessionConfig.setInputConfiguration(inputConfig);
        }

        try {
            ret = camera.isSessionConfigurationSupported(sessionConfig);
        } catch (UnsupportedOperationException e) {
            // Camera doesn't support session configuration query
            return new SessionConfigSupport(false/*error*/,
                    false/*callSupported*/, defaultSupport/*configSupported*/);
        } catch (IllegalArgumentException e) {
            return new SessionConfigSupport(true/*error*/,
                    false/*callSupported*/, false/*configSupported*/);
        } catch (android.hardware.camera2.CameraAccessException e) {
            return new SessionConfigSupport(true/*error*/,
                    false/*callSupported*/, false/*configSupported*/);
        }

        return new SessionConfigSupport(false/*error*/,
                true/*callSupported*/, ret/*configSupported*/);
    }

    public static boolean isSessionConfigWithParamsSupported(
            CameraDevice.CameraDeviceSetup cameraDeviceSetup,
            Handler handler, List<OutputConfiguration> outputConfigs,
            int operatingMode, CaptureRequest request) throws CameraAccessException {
        BlockingSessionCallback sessionListener = new BlockingSessionCallback();
        SessionConfiguration sessionConfig = new SessionConfiguration(operatingMode, outputConfigs,
                new HandlerExecutor(handler), sessionListener);
        sessionConfig.setSessionParameters(request);

        return cameraDeviceSetup.isSessionConfigurationSupported(sessionConfig);
    }


    /**
     * Wait for numResultWait frames
     *
     * @param resultListener The capture listener to get capture result back.
     * @param numResultsWait Number of frame to wait
     * @param timeout Wait timeout in ms.
     *
     * @return the last result, or {@code null} if there was none
     */
    public static CaptureResult waitForNumResults(SimpleCaptureCallback resultListener,
            int numResultsWait, int timeout) {
        if (numResultsWait < 0 || resultListener == null) {
            throw new IllegalArgumentException(
                    "Input must be positive number and listener must be non-null");
        }

        CaptureResult result = null;
        for (int i = 0; i < numResultsWait; i++) {
            result = resultListener.getCaptureResult(timeout);
        }

        return result;
    }

    /**
     * Wait for any expected result key values available in a certain number of results.
     *
     * <p>
     * Check the result immediately if numFramesWait is 0.
     * </p>
     *
     * @param listener The capture listener to get capture result.
     * @param resultKey The capture result key associated with the result value.
     * @param expectedValues The list of result value need to be waited for,
     * return immediately if the list is empty.
     * @param numResultsWait Number of frame to wait before times out.
     * @param timeout result wait time out in ms.
     * @throws TimeoutRuntimeException If more than numResultsWait results are.
     * seen before the result matching myRequest arrives, or each individual wait
     * for result times out after 'timeout' ms.
     */
    public static <T> void waitForAnyResultValue(SimpleCaptureCallback listener,
            CaptureResult.Key<T> resultKey, List<T> expectedValues, int numResultsWait,
            int timeout) {
        if (numResultsWait < 0 || listener == null || expectedValues == null) {
            throw new IllegalArgumentException(
                    "Input must be non-negative number and listener/expectedValues "
                    + "must be non-null");
        }

        int i = 0;
        CaptureResult result;
        do {
            result = listener.getCaptureResult(timeout);
            T value = result.get(resultKey);
            for ( T expectedValue : expectedValues) {
                if (VERBOSE) {
                    Log.v(TAG, "Current result value for key " + resultKey.getName() + " is: "
                            + value.toString());
                }
                if (value.equals(expectedValue)) {
                    return;
                }
            }
        } while (i++ < numResultsWait);

        throw new TimeoutRuntimeException(
                "Unable to get the expected result value " + expectedValues + " for key " +
                        resultKey.getName() + " after waiting for " + numResultsWait + " results");
    }

    /**
     * Wait for expected result key value available in a certain number of results.
     *
     * <p>
     * Check the result immediately if numFramesWait is 0.
     * </p>
     *
     * @param listener The capture listener to get capture result
     * @param resultKey The capture result key associated with the result value
     * @param expectedValue The result value need to be waited for
     * @param numResultsWait Number of frame to wait before times out
     * @param timeout Wait time out.
     * @throws TimeoutRuntimeException If more than numResultsWait results are
     * seen before the result matching myRequest arrives, or each individual wait
     * for result times out after 'timeout' ms.
     */
    public static <T> void waitForResultValue(SimpleCaptureCallback listener,
            CaptureResult.Key<T> resultKey, T expectedValue, int numResultsWait, int timeout) {
        List<T> expectedValues = new ArrayList<T>();
        expectedValues.add(expectedValue);
        waitForAnyResultValue(listener, resultKey, expectedValues, numResultsWait, timeout);
    }

    /**
     * Wait for AE to be stabilized before capture: CONVERGED or FLASH_REQUIRED.
     *
     * <p>Waits for {@code android.sync.maxLatency} number of results first, to make sure
     * that the result is synchronized (or {@code numResultWaitForUnknownLatency} if the latency
     * is unknown.</p>
     *
     * <p>This is a no-op for {@code LEGACY} devices since they don't report
     * the {@code aeState} result.</p>
     *
     * @param resultListener The capture listener to get capture result back.
     * @param numResultWaitForUnknownLatency Number of frame to wait if camera device latency is
     *                                       unknown.
     * @param staticInfo corresponding camera device static metadata.
     * @param settingsTimeout wait timeout for settings application in ms.
     * @param resultTimeout wait timeout for result in ms.
     * @param numResultsWait Number of frame to wait before times out.
     */
    public static void waitForAeStable(SimpleCaptureCallback resultListener,
            int numResultWaitForUnknownLatency, StaticMetadata staticInfo,
            int settingsTimeout, int numResultWait) {
        waitForSettingsApplied(resultListener, numResultWaitForUnknownLatency, staticInfo,
                settingsTimeout);

        if (!staticInfo.isHardwareLevelAtLeastLimited()) {
            // No-op for metadata
            return;
        }
        List<Integer> expectedAeStates = new ArrayList<Integer>();
        expectedAeStates.add(new Integer(CaptureResult.CONTROL_AE_STATE_CONVERGED));
        expectedAeStates.add(new Integer(CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED));
        waitForAnyResultValue(resultListener, CaptureResult.CONTROL_AE_STATE, expectedAeStates,
                numResultWait, settingsTimeout);
    }

    /**
     * Wait for enough results for settings to be applied
     *
     * @param resultListener The capture listener to get capture result back.
     * @param numResultWaitForUnknownLatency Number of frame to wait if camera device latency is
     *                                       unknown.
     * @param staticInfo corresponding camera device static metadata.
     * @param timeout wait timeout in ms.
     */
    public static void waitForSettingsApplied(SimpleCaptureCallback resultListener,
            int numResultWaitForUnknownLatency, StaticMetadata staticInfo, int timeout) {
        int maxLatency = staticInfo.getSyncMaxLatency();
        if (maxLatency == CameraMetadata.SYNC_MAX_LATENCY_UNKNOWN) {
            maxLatency = numResultWaitForUnknownLatency;
        }
        // Wait for settings to take effect
        waitForNumResults(resultListener, maxLatency, timeout);
    }

    public static Range<Integer> getSuitableFpsRangeForDuration(String cameraId,
            long frameDuration, StaticMetadata staticInfo) {
        // Add 0.05 here so Fps like 29.99 evaluated to 30
        int minBurstFps = (int) Math.floor(1e9 / frameDuration + 0.05f);
        boolean foundConstantMaxYUVRange = false;
        boolean foundYUVStreamingRange = false;
        boolean isExternalCamera = staticInfo.isExternalCamera();
        boolean isNIR = staticInfo.isNIRColorFilter();

        // Find suitable target FPS range - as high as possible that covers the max YUV rate
        // Also verify that there's a good preview rate as well
        List<Range<Integer> > fpsRanges = Arrays.asList(
                staticInfo.getAeAvailableTargetFpsRangesChecked());
        Range<Integer> targetRange = null;
        for (Range<Integer> fpsRange : fpsRanges) {
            if (fpsRange.getLower() == minBurstFps && fpsRange.getUpper() == minBurstFps) {
                foundConstantMaxYUVRange = true;
                targetRange = fpsRange;
            } else if (isExternalCamera && fpsRange.getUpper() == minBurstFps) {
                targetRange = fpsRange;
            }
            if (fpsRange.getLower() <= 15 && fpsRange.getUpper() == minBurstFps) {
                foundYUVStreamingRange = true;
            }

        }

        if (!isExternalCamera) {
            assertTrue(String.format("Cam %s: Target FPS range of (%d, %d) must be supported",
                    cameraId, minBurstFps, minBurstFps), foundConstantMaxYUVRange);
        }

        if (!isNIR) {
            assertTrue(String.format(
                    "Cam %s: Target FPS range of (x, %d) where x <= 15 must be supported",
                    cameraId, minBurstFps), foundYUVStreamingRange);
        }
        return targetRange;
    }
    /**
     * Get the candidate supported zoom ratios for testing
     *
     * <p>
     * This function returns the bounary values of supported zoom ratio range in addition to 1.0x
     * zoom ratio.
     * </p>
     */
    public static List<Float> getCandidateZoomRatios(StaticMetadata staticInfo) {
        List<Float> zoomRatios = new ArrayList<Float>();
        Range<Float> zoomRatioRange = staticInfo.getZoomRatioRangeChecked();
        zoomRatios.add(zoomRatioRange.getLower());
        if (zoomRatioRange.contains(1.0f) &&
                1.0f - zoomRatioRange.getLower() > ZOOM_RATIO_THRESHOLD &&
                zoomRatioRange.getUpper() - 1.0f > ZOOM_RATIO_THRESHOLD) {
            zoomRatios.add(1.0f);
        }
        zoomRatios.add(zoomRatioRange.getUpper());

        return zoomRatios;
    }

    /**
     * Get the primary rear facing camera from an ID list
     */
    public static String getPrimaryRearCamera(CameraManager manager, String[] cameraIds)
            throws Exception {
        return getPrimaryCamera(manager, cameraIds, CameraCharacteristics.LENS_FACING_BACK);
    }

    /**
     * Get the primary front facing camera from an ID list
     */
    public static String getPrimaryFrontCamera(CameraManager manager, String[] cameraIds)
            throws Exception {
        return getPrimaryCamera(manager, cameraIds, CameraCharacteristics.LENS_FACING_FRONT);
    }

    private static String getPrimaryCamera(CameraManager manager,
            String[] cameraIds, Integer facing) throws Exception {
        if (cameraIds == null) {
            return null;
        }

        for (String id : cameraIds) {
            if (isPrimaryCamera(manager, id, facing)) {
                return id;
            }
        }

        return null;
    }

    /**
     * Check whether a camera Id is a primary rear facing camera
     */
    public static boolean isPrimaryRearFacingCamera(CameraManager manager, String cameraId)
            throws Exception {
        return isPrimaryCamera(manager, cameraId, CameraCharacteristics.LENS_FACING_BACK);
    }

    /**
     * Check whether a camera Id is a primary front facing camera
     */
    public static boolean isPrimaryFrontFacingCamera(CameraManager manager, String cameraId)
            throws Exception {
        return isPrimaryCamera(manager, cameraId, CameraCharacteristics.LENS_FACING_FRONT);
    }

    private static boolean isPrimaryCamera(CameraManager manager, String cameraId,
            Integer lensFacing) throws Exception {
        CameraCharacteristics characteristics;
        Integer facing;

        String [] ids = manager.getCameraIdList();
        for (String id : ids) {
            characteristics = manager.getCameraCharacteristics(id);
            facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            if (lensFacing.equals(facing)) {
                if (cameraId.equals(id)) {
                    return true;
                } else {
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * Verifies the camera in this listener was opened and then unconfigured exactly once.
     *
     * <p>This assumes that no other action to the camera has been done (e.g.
     * it hasn't been configured, or closed, or disconnected). Verification is
     * performed immediately without any timeouts.</p>
     *
     * <p>This checks that the state has previously changed first for opened and then unconfigured.
     * Any other state transitions will fail. A test failure is thrown if verification fails.</p>
     *
     * @param cameraId Camera identifier
     * @param listener Listener which was passed to {@link CameraManager#openCamera}
     *
     * @return The camera device (non-{@code null}).
     */
    public static CameraDevice verifyCameraStateOpened(String cameraId,
            MockStateCallback listener) {
        ArgumentCaptor<CameraDevice> argument =
                ArgumentCaptor.forClass(CameraDevice.class);
        InOrder inOrder = inOrder(listener);

        /**
         * State transitions (in that order):
         *  1) onOpened
         *
         * No other transitions must occur for successful #openCamera
         */
        inOrder.verify(listener)
                .onOpened(argument.capture());

        CameraDevice camera = argument.getValue();
        assertNotNull(
                String.format("Failed to open camera device ID: %s", cameraId),
                camera);

        // Do not use inOrder here since that would skip anything called before onOpened
        verifyNoMoreInteractions(listener);

        return camera;
    }

    public static void verifySingleAvailabilityCbsReceived(
            LinkedBlockingQueue<String> expectedEventQueue,
            LinkedBlockingQueue<String> unExpectedEventQueue, String expectedId,
            String expectedStr, String unExpectedStr) throws Exception {
        String candidateId = expectedEventQueue.poll(AVAILABILITY_TIMEOUT_MS,
                java.util.concurrent.TimeUnit.MILLISECONDS);
        assertNotNull("No " + expectedStr + " notice for expected ID " + expectedId, candidateId);
        assertTrue("Received " + expectedStr + " notice for wrong ID, " + "expected "
                + expectedId + ", got " + candidateId, expectedId.equals(candidateId));
        assertTrue("Received >  1 " + expectedStr + " callback for id " + expectedId,
                expectedEventQueue.size() == 0);
        assertTrue(unExpectedStr + " events received unexpectedly",
                unExpectedEventQueue.size() == 0);
    }

    public static <T> void verifyAvailabilityCbsReceived(HashSet<T> expectedCameras,
            LinkedBlockingQueue<T> expectedEventQueue, LinkedBlockingQueue<T> unExpectedEventQueue,
            boolean available) throws Exception {
        while (expectedCameras.size() > 0) {
            T id = expectedEventQueue.poll(AVAILABILITY_TIMEOUT_MS,
                    java.util.concurrent.TimeUnit.MILLISECONDS);
            assertTrue("Did not receive initial " + (available ? "available" : "unavailable")
                    + " notices for some cameras", id != null);
            assertTrue("Received initial " + (available ? "available" : "unavailable")
                    + " notice for wrong camera " + id, expectedCameras.contains(id));
            expectedCameras.remove(id);
        }
        // Verify no unexpected unavailable/available cameras were reported
        if (unExpectedEventQueue != null) {
            assertTrue("Received unexpected initial "
                    + (available ? "unavailable" : "available"),
                    unExpectedEventQueue.size() == 0);
        }
    }

    /**
     * This function polls on the event queue to get unavailable physical camera IDs belonging
     * to a particular logical camera. The event queue is drained before the function returns.
     *
     * @param queue The event queue capturing unavailable physical cameras
     * @param cameraId The logical camera ID
     *
     * @return The currently unavailable physical cameras
     */
    private static Set<String> getUnavailablePhysicalCamerasAndDrain(
            LinkedBlockingQueue<Pair<String, String>> queue, String cameraId) throws Exception {
        Set<String> unavailablePhysicalCameras = new HashSet<String>();

        while (true) {
            Pair<String, String> unavailableIdCombo = queue.poll(
                    AVAILABILITY_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (unavailableIdCombo == null) {
                // No more entries in the queue. Break out of the loop and return.
                break;
            }

            if (cameraId.equals(unavailableIdCombo.first)) {
                unavailablePhysicalCameras.add(unavailableIdCombo.second);
            }
        }

        return unavailablePhysicalCameras;
    }

    public static void testPhysicalCameraAvailabilityConsistencyHelper(
            String[] cameraIds, CameraManager manager,
            Handler handler, boolean expectInitialCallbackAfterOpen) throws Throwable {
        final LinkedBlockingQueue<String> availableEventQueue = new LinkedBlockingQueue<>();
        final LinkedBlockingQueue<String> unavailableEventQueue = new LinkedBlockingQueue<>();
        final LinkedBlockingQueue<Pair<String, String>> unavailablePhysicalCamEventQueue =
                new LinkedBlockingQueue<>();
        CameraManager.AvailabilityCallback ac = new CameraManager.AvailabilityCallback() {
            @Override
            public void onCameraAvailable(String cameraId) {
                super.onCameraAvailable(cameraId);
                availableEventQueue.offer(cameraId);
            }

            @Override
            public void onCameraUnavailable(String cameraId) {
                super.onCameraUnavailable(cameraId);
                unavailableEventQueue.offer(cameraId);
            }

            @Override
            public void onPhysicalCameraAvailable(String cameraId, String physicalCameraId) {
                super.onPhysicalCameraAvailable(cameraId, physicalCameraId);
                unavailablePhysicalCamEventQueue.remove(new Pair<>(cameraId, physicalCameraId));
            }

            @Override
            public void onPhysicalCameraUnavailable(String cameraId, String physicalCameraId) {
                super.onPhysicalCameraUnavailable(cameraId, physicalCameraId);
                unavailablePhysicalCamEventQueue.offer(new Pair<>(cameraId, physicalCameraId));
            }
        };

        String[] cameras = cameraIds;
        if (cameras.length == 0) {
            Log.i(TAG, "Skipping testPhysicalCameraAvailabilityConsistency, no cameras");
            return;
        }

        for (String cameraId : cameras) {
            CameraCharacteristics ch = manager.getCameraCharacteristics(cameraId);
            StaticMetadata staticInfo = new StaticMetadata(ch);
            if (!staticInfo.isLogicalMultiCamera()) {
                // Test is only applicable for logical multi-camera.
                continue;
            }

            // Get initial physical unavailable callbacks without opening camera
            manager.registerAvailabilityCallback(ac, handler);
            Set<String> unavailablePhysicalCameras = getUnavailablePhysicalCamerasAndDrain(
                    unavailablePhysicalCamEventQueue, cameraId);

            // Open camera
            MockStateCallback mockListener = MockStateCallback.mock();
            BlockingStateCallback cameraListener = new BlockingStateCallback(mockListener);
            manager.openCamera(cameraId, cameraListener, handler);
            // Block until opened
            cameraListener.waitForState(BlockingStateCallback.STATE_OPENED,
                    CameraTestUtils.CAMERA_IDLE_TIMEOUT_MS);
            // Then verify only open happened, and get the camera handle
            CameraDevice camera = CameraTestUtils.verifyCameraStateOpened(cameraId, mockListener);

            // The camera should be in available->unavailable state.
            String candidateUnavailableId = unavailableEventQueue.poll(AVAILABILITY_TIMEOUT_MS,
                    java.util.concurrent.TimeUnit.MILLISECONDS);
            assertNotNull("No unavailable notice for expected ID " + cameraId,
                    candidateUnavailableId);
            assertTrue("Received unavailable notice for wrong ID, "
                    + "expected " + cameraId + ", got " + candidateUnavailableId,
                    cameraId.equals(candidateUnavailableId));
            assertTrue("Received >  1 unavailable callback for id " + cameraId,
                    unavailableEventQueue.size() == 0);
            availableEventQueue.clear();
            unavailableEventQueue.clear();

            manager.unregisterAvailabilityCallback(ac);
            // Get physical unavailable callbacks while camera is open
            manager.registerAvailabilityCallback(ac, handler);
            HashSet<String> expectedAvailableCameras = new HashSet<String>(Arrays.asList(cameras));
            expectedAvailableCameras.remove(cameraId);
            HashSet<String> expectedUnavailableCameras =
                    new HashSet<String>(Arrays.asList(cameraId));
            CameraTestUtils.verifyAvailabilityCbsReceived(expectedAvailableCameras,
                    availableEventQueue, null, /*available*/ true);
            CameraTestUtils.verifyAvailabilityCbsReceived(expectedUnavailableCameras,
                    unavailableEventQueue, null, /*available*/ false);
            Set<String> unavailablePhysicalCamerasWhileOpen = getUnavailablePhysicalCamerasAndDrain(
                    unavailablePhysicalCamEventQueue, cameraId);
            if (expectInitialCallbackAfterOpen) {
                assertTrue("The unavailable physical cameras must be the same between before open "
                        + unavailablePhysicalCameras.toString()  + " and after open "
                        + unavailablePhysicalCamerasWhileOpen.toString(),
                        unavailablePhysicalCameras.equals(unavailablePhysicalCamerasWhileOpen));
            } else {
                assertTrue("The physical camera unavailability callback must not be called when "
                        + "the logical camera is open",
                        unavailablePhysicalCamerasWhileOpen.isEmpty());
            }

            // Close camera device
            camera.close();
            cameraListener.waitForState(BlockingStateCallback.STATE_CLOSED,
                    CameraTestUtils.CAMERA_CLOSE_TIMEOUT_MS);
            CameraTestUtils.verifySingleAvailabilityCbsReceived(availableEventQueue,
                    unavailableEventQueue, cameraId, "availability", "Unavailability");

            // Get physical unavailable callbacks after opening and closing camera
            Set<String> unavailablePhysicalCamerasAfterClose =
                    getUnavailablePhysicalCamerasAndDrain(
                            unavailablePhysicalCamEventQueue, cameraId);

            assertTrue("The unavailable physical cameras must be the same between before open "
                    + unavailablePhysicalCameras.toString()  + " and after close "
                    + unavailablePhysicalCamerasAfterClose.toString(),
                    unavailablePhysicalCameras.equals(unavailablePhysicalCamerasAfterClose));

            manager.unregisterAvailabilityCallback(ac);
        }
    }

    /**
     * Simple holder for resolutions to use for different camera outputs and size limits.
     */
    static class MaxStreamSizes {

        enum AspectRatio {
            ARBITRARY, AR_4_3, AR_16_9
        }

        // Format shorthands
        static final int PRIV = ImageFormat.PRIVATE;
        static final int JPEG = ImageFormat.JPEG;
        static final int YUV  = ImageFormat.YUV_420_888;
        static final int RAW  = ImageFormat.RAW_SENSOR;
        static final int Y8   = ImageFormat.Y8;
        static final int HEIC = ImageFormat.HEIC;

        // Max resolution output indices
        static final int PREVIEW = 0;
        static final int RECORD  = 1;
        static final int MAXIMUM = 2;
        static final int VGA = 3;
        static final int VGA_FULL_FOV = 4;
        static final int MAX_30FPS = 5;
        static final int S720P = 6;
        static final int S1440P = 7;
        static final int MAX_RES = 8;
        static final int S1080P = 9;
        static final int RESOLUTION_COUNT = 10;

        // Max resolution input indices
        static final int INPUT_MAXIMUM = 0;
        static final int INPUT_MAX_RES = 1;
        static final int INPUT_RESOLUTION_COUNT = 2;

        static final Size S_1280_720 = new Size(1280, 720);   // 16:9
        static final Size S_960_720 = new Size(960, 720);     // 4:3

        static final Size S_1920_1080 = new Size(1920, 1080); // 16:9
        static final Size S_1440_1080 = new Size(1440, 1080); // 4:3

        static final Size S_2560_1440 = new Size(2560, 1440); // 16:9
        static final Size S_1920_1440 = new Size(1920, 1440); // 4:3

        static final long FRAME_DURATION_30FPS_NSEC = (long) 1e9 / 30;

        static final int USE_CASE_PREVIEW =
                CameraMetadata.SCALER_AVAILABLE_STREAM_USE_CASES_PREVIEW;
        static final int USE_CASE_VIDEO_RECORD =
                CameraMetadata.SCALER_AVAILABLE_STREAM_USE_CASES_VIDEO_RECORD;
        static final int USE_CASE_STILL_CAPTURE =
                CameraMetadata.SCALER_AVAILABLE_STREAM_USE_CASES_STILL_CAPTURE;
        static final int USE_CASE_PREVIEW_VIDEO_STILL =
                CameraMetadata.SCALER_AVAILABLE_STREAM_USE_CASES_PREVIEW_VIDEO_STILL;
        static final int USE_CASE_VIDEO_CALL =
                CameraMetadata.SCALER_AVAILABLE_STREAM_USE_CASES_VIDEO_CALL;
        static final int USE_CASE_CROPPED_RAW =
                CameraMetadata.SCALER_AVAILABLE_STREAM_USE_CASES_CROPPED_RAW;

        private final Size[] mMaxPrivSizes = new Size[RESOLUTION_COUNT];
        private final Size[] mMaxJpegSizes = new Size[RESOLUTION_COUNT];
        private final Size[] mMaxYuvSizes = new Size[RESOLUTION_COUNT];
        private final Size[] mMaxY8Sizes = new Size[RESOLUTION_COUNT];
        private final Size[] mMaxHeicSizes = new Size[RESOLUTION_COUNT];
        private final Size mMaxRawSize;
        private final Size mMaxResolutionRawSize;

        private final Size[] mMaxPrivInputSizes = new Size[INPUT_RESOLUTION_COUNT];
        private final Size[] mMaxYuvInputSizes = new Size[INPUT_RESOLUTION_COUNT];
        private final Size mMaxInputY8Size;

        private final AspectRatio mAspectRatio;

        MaxStreamSizes(StaticMetadata sm, String cameraId, Context context) {
            this(sm, cameraId, context, AspectRatio.ARBITRARY);
        }

        MaxStreamSizes(StaticMetadata sm, String cameraId, Context context,
                AspectRatio aspectRatio) {
            mAspectRatio = aspectRatio;
            float aspectRatioValue = -1;
            if (aspectRatio == AspectRatio.AR_4_3) {
                aspectRatioValue = 1.0f * 4 / 3;
            } else if (aspectRatio == AspectRatio.AR_16_9) {
                aspectRatioValue = 1.0f * 16 / 9;
            }

            Size[] privSizes = sm.getAvailableSizesForFormatChecked(ImageFormat.PRIVATE,
                    StaticMetadata.StreamDirection.Output, /*fastSizes*/true, /*slowSizes*/false);
            Size[] yuvSizes = sm.getAvailableSizesForFormatChecked(ImageFormat.YUV_420_888,
                    StaticMetadata.StreamDirection.Output, /*fastSizes*/true, /*slowSizes*/false);

            Size[] y8Sizes = sm.getAvailableSizesForFormatChecked(ImageFormat.Y8,
                    StaticMetadata.StreamDirection.Output, /*fastSizes*/true, /*slowSizes*/false);
            Size[] jpegSizes = sm.getAvailableSizesForFormatChecked(ImageFormat.JPEG,
                    StaticMetadata.StreamDirection.Output, /*fastSizes*/true, /*slowSizes*/false);
            Size[] rawSizes = sm.getAvailableSizesForFormatChecked(ImageFormat.RAW_SENSOR,
                    StaticMetadata.StreamDirection.Output, /*fastSizes*/true, /*slowSizes*/false);
            Size[] heicSizes = sm.getAvailableSizesForFormatChecked(ImageFormat.HEIC,
                    StaticMetadata.StreamDirection.Output, /*fastSizes*/true, /*slowSizes*/false);

            Size maxPreviewSize = getMaxPreviewSize(context, cameraId);

            StreamConfigurationMap configs = sm.getCharacteristics().get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            StreamConfigurationMap maxResConfigs = sm.getCharacteristics().get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION);

            mMaxRawSize = (rawSizes.length != 0) ? CameraTestUtils.getMaxSize(rawSizes) : null;
            mMaxResolutionRawSize = sm.isUltraHighResolutionSensor()
                    ? CameraTestUtils.getMaxSize(
                            maxResConfigs.getOutputSizes(ImageFormat.RAW_SENSOR))
                    : null;

            if (sm.isColorOutputSupported()) {
                // We don't include JPEG sizes capped at PREVIEW since for MPC 12+ devices, JPEG
                // sizes are necessarily > 1080p. Also the mandatory stream combinations have no
                // JPEG streams capped at PREVIEW.
                mMaxPrivSizes[PREVIEW] = CameraTestUtils.getMaxSizeWithBound(privSizes,
                        maxPreviewSize);
                mMaxYuvSizes[PREVIEW]  = CameraTestUtils.getMaxSizeWithBound(yuvSizes,
                        maxPreviewSize);

                if (sm.isExternalCamera()) {
                    mMaxPrivSizes[RECORD] = getMaxExternalRecordingSize(cameraId, configs);
                    mMaxYuvSizes[RECORD]  = getMaxExternalRecordingSize(cameraId, configs);
                    mMaxJpegSizes[RECORD] = getMaxExternalRecordingSize(cameraId, configs);
                } else {
                    mMaxPrivSizes[RECORD] = getMaxRecordingSize(cameraId);
                    mMaxYuvSizes[RECORD]  = getMaxRecordingSize(cameraId);
                    mMaxJpegSizes[RECORD] = getMaxRecordingSize(cameraId);
                }

                if (sm.isUltraHighResolutionSensor()) {
                    mMaxYuvSizes[MAX_RES] = CameraTestUtils.getMaxSize(
                            maxResConfigs.getOutputSizes(ImageFormat.YUV_420_888));
                    mMaxJpegSizes[MAX_RES] = CameraTestUtils.getMaxSize(
                            maxResConfigs.getOutputSizes(ImageFormat.JPEG));
                }

                mMaxPrivSizes[MAXIMUM] = CameraTestUtils.getMaxSize(privSizes, aspectRatioValue);
                mMaxYuvSizes[MAXIMUM] = CameraTestUtils.getMaxSize(yuvSizes, aspectRatioValue);
                mMaxJpegSizes[MAXIMUM] = CameraTestUtils.getMaxSize(jpegSizes, aspectRatioValue);

                // Must always be supported, add unconditionally
                final Size vgaSize = new Size(640, 480);
                mMaxPrivSizes[VGA] = vgaSize;
                mMaxYuvSizes[VGA] = vgaSize;
                mMaxJpegSizes[VGA] = vgaSize;

                // Check for 720p size for PRIVATE and YUV
                // 720p is not mandatory for JPEG so it is not checked
                final Size s720pSize =
                        (mAspectRatio == AspectRatio.AR_4_3) ? S_960_720 : S_1280_720;
                if (mAspectRatio == AspectRatio.ARBITRARY) {
                    mMaxPrivSizes[S720P] = CameraTestUtils.getMaxSizeWithBound(
                            configs.getOutputSizes(ImageFormat.PRIVATE), s720pSize);
                    mMaxYuvSizes[S720P] = CameraTestUtils.getMaxSizeWithBound(
                            configs.getOutputSizes(ImageFormat.YUV_420_888), s720pSize);
                } else {
                    mMaxPrivSizes[S720P] = s720pSize;
                    mMaxYuvSizes[S720P] = s720pSize;
                    mMaxJpegSizes[S720P] = s720pSize;
                }

                final Size s1440pSize =
                        (mAspectRatio == AspectRatio.AR_16_9) ? S_2560_1440 : S_1920_1440;
                if (mAspectRatio == AspectRatio.ARBITRARY) {

                    mMaxPrivSizes[S1440P] = CameraTestUtils.getMaxSizeWithBound(
                            configs.getOutputSizes(ImageFormat.PRIVATE), s1440pSize);
                    mMaxYuvSizes[S1440P] = CameraTestUtils.getMaxSizeWithBound(
                            configs.getOutputSizes(ImageFormat.YUV_420_888), s1440pSize);
                    mMaxJpegSizes[S1440P] = CameraTestUtils.getMaxSizeWithBound(
                            configs.getOutputSizes(ImageFormat.JPEG), s1440pSize);
                } else {
                    mMaxPrivSizes[S1440P] = s1440pSize;
                    mMaxYuvSizes[S1440P] = s1440pSize;
                    mMaxJpegSizes[S1440P] = s1440pSize;
                }

                final Size s1080pSize =
                        (mAspectRatio == AspectRatio.AR_4_3) ? S_1440_1080 : S_1920_1080;
                if (mAspectRatio == AspectRatio.ARBITRARY) {
                    mMaxPrivSizes[S1080P] = CameraTestUtils.getMaxSizeWithBound(
                            configs.getOutputSizes(ImageFormat.PRIVATE), s1080pSize);
                    mMaxYuvSizes[S1080P] = CameraTestUtils.getMaxSizeWithBound(
                            configs.getOutputSizes(ImageFormat.YUV_420_888), s1080pSize);
                    mMaxJpegSizes[S1080P] = CameraTestUtils.getMaxSizeWithBound(
                            configs.getOutputSizes(ImageFormat.JPEG), s1080pSize);
                } else {
                    mMaxPrivSizes[S1080P] = s1080pSize;
                    mMaxYuvSizes[S1080P] = s1080pSize;
                    mMaxJpegSizes[S1080P] = s1080pSize;
                }

                if (sm.isMonochromeWithY8()) {
                    mMaxY8Sizes[PREVIEW]  = CameraTestUtils.getMaxSizeWithBound(
                            y8Sizes, maxPreviewSize);
                    if (sm.isExternalCamera()) {
                        mMaxY8Sizes[RECORD]  = getMaxExternalRecordingSize(cameraId, configs);
                    } else {
                        mMaxY8Sizes[RECORD]  = getMaxRecordingSize(cameraId);
                    }
                    mMaxY8Sizes[MAXIMUM] = CameraTestUtils.getMaxSize(y8Sizes);
                    mMaxY8Sizes[VGA] = vgaSize;
                    mMaxY8Sizes[S720P] = CameraTestUtils.getMaxSizeWithBound(
                            configs.getOutputSizes(ImageFormat.Y8), s720pSize);
                    mMaxY8Sizes[S1440P] = CameraTestUtils.getMaxSizeWithBound(
                            configs.getOutputSizes(ImageFormat.Y8), s1440pSize);
                }

                if (sm.isHeicSupported()) {
                    mMaxHeicSizes[PREVIEW] = CameraTestUtils.getMaxSizeWithBound(
                            heicSizes, maxPreviewSize);
                    mMaxHeicSizes[RECORD] = getMaxRecordingSize(cameraId);
                    mMaxHeicSizes[MAXIMUM] = CameraTestUtils.getMaxSize(heicSizes);
                    mMaxHeicSizes[VGA] = vgaSize;
                    mMaxHeicSizes[S720P] = CameraTestUtils.getMaxSizeWithBound(
                            configs.getOutputSizes(ImageFormat.HEIC), s720pSize);
                    mMaxHeicSizes[S1440P] = CameraTestUtils.getMaxSizeWithBound(
                            configs.getOutputSizes(ImageFormat.HEIC), s1440pSize);
                }
            }
            if (sm.isColorOutputSupported() && !sm.isHardwareLevelLegacy()) {
                // VGA resolution, but with aspect ratio matching full res FOV
                float fullFovAspect = mMaxYuvSizes[MAXIMUM].getWidth()
                        / (float) mMaxYuvSizes[MAXIMUM].getHeight();
                Size vgaFullFovSize = new Size(640, (int) (640 / fullFovAspect));

                mMaxPrivSizes[VGA_FULL_FOV] = vgaFullFovSize;
                mMaxYuvSizes[VGA_FULL_FOV] = vgaFullFovSize;
                mMaxJpegSizes[VGA_FULL_FOV] = vgaFullFovSize;
                if (sm.isMonochromeWithY8()) {
                    mMaxY8Sizes[VGA_FULL_FOV] = vgaFullFovSize;
                }

                // Max resolution that runs at 30fps

                Size maxPriv30fpsSize = null;
                Size maxYuv30fpsSize = null;
                Size maxY830fpsSize = null;
                Size maxJpeg30fpsSize = null;
                Comparator<Size> comparator = new SizeComparator();
                for (Map.Entry<Size, Long> e :
                             sm.getAvailableMinFrameDurationsForFormatChecked(ImageFormat.PRIVATE)
                             .entrySet()) {
                    Size s = e.getKey();
                    Long minDuration = e.getValue();
                    Log.d(TAG, String.format("Priv Size: %s, duration %d limit %d", s, minDuration,
                                FRAME_DURATION_30FPS_NSEC));
                    if (minDuration <= FRAME_DURATION_30FPS_NSEC) {
                        if (maxPriv30fpsSize == null
                                || comparator.compare(maxPriv30fpsSize, s) < 0) {
                            maxPriv30fpsSize = s;
                        }
                    }
                }
                assertTrue("No PRIVATE resolution available at 30fps!", maxPriv30fpsSize != null);

                for (Map.Entry<Size, Long> e :
                             sm.getAvailableMinFrameDurationsForFormatChecked(
                                     ImageFormat.YUV_420_888)
                             .entrySet()) {
                    Size s = e.getKey();
                    Long minDuration = e.getValue();
                    Log.d(TAG, String.format("YUV Size: %s, duration %d limit %d", s, minDuration,
                                FRAME_DURATION_30FPS_NSEC));
                    if (minDuration <= FRAME_DURATION_30FPS_NSEC) {
                        if (maxYuv30fpsSize == null
                                || comparator.compare(maxYuv30fpsSize, s) < 0) {
                            maxYuv30fpsSize = s;
                        }
                    }
                }
                assertTrue("No YUV_420_888 resolution available at 30fps!",
                        maxYuv30fpsSize != null);

                if (sm.isMonochromeWithY8()) {
                    for (Map.Entry<Size, Long> e :
                                 sm.getAvailableMinFrameDurationsForFormatChecked(
                                         ImageFormat.Y8)
                                 .entrySet()) {
                        Size s = e.getKey();
                        Long minDuration = e.getValue();
                        Log.d(TAG, String.format("Y8 Size: %s, duration %d limit %d",
                                s, minDuration, FRAME_DURATION_30FPS_NSEC));
                        if (minDuration <= FRAME_DURATION_30FPS_NSEC) {
                            if (maxY830fpsSize == null
                                    || comparator.compare(maxY830fpsSize, s) < 0) {
                                maxY830fpsSize = s;
                            }
                        }
                    }
                    assertTrue("No Y8 resolution available at 30fps!", maxY830fpsSize != null);
                }

                for (Map.Entry<Size, Long> e :
                             sm.getAvailableMinFrameDurationsForFormatChecked(ImageFormat.JPEG)
                             .entrySet()) {
                    Size s = e.getKey();
                    Long minDuration = e.getValue();
                    Log.d(TAG, String.format("JPEG Size: %s, duration %d limit %d", s, minDuration,
                                FRAME_DURATION_30FPS_NSEC));
                    if (minDuration <= FRAME_DURATION_30FPS_NSEC) {
                        if (maxJpeg30fpsSize == null
                                || comparator.compare(maxJpeg30fpsSize, s) < 0) {
                            maxJpeg30fpsSize = s;
                        }
                    }
                }
                assertTrue("No JPEG resolution available at 30fps!", maxJpeg30fpsSize != null);

                mMaxPrivSizes[MAX_30FPS] = maxPriv30fpsSize;
                mMaxYuvSizes[MAX_30FPS] = maxYuv30fpsSize;
                mMaxY8Sizes[MAX_30FPS] = maxY830fpsSize;
                mMaxJpegSizes[MAX_30FPS] = maxJpeg30fpsSize;
            }

            Size[] privInputSizes = configs.getInputSizes(ImageFormat.PRIVATE);
            mMaxPrivInputSizes[INPUT_MAXIMUM] = privInputSizes != null
                    ? CameraTestUtils.getMaxSize(privInputSizes)
                    : null;
            Size[] maxResPrivInputSizes =
                    sm.isUltraHighResolutionSensor()
                    ?  maxResConfigs.getInputSizes(ImageFormat.PRIVATE)
                    : null;
            mMaxPrivInputSizes[INPUT_MAX_RES] = maxResPrivInputSizes != null
                    ? CameraTestUtils.getMaxSize(maxResPrivInputSizes)
                    : null;

            Size[] yuvInputSizes = configs.getInputSizes(ImageFormat.YUV_420_888);
            mMaxYuvInputSizes[INPUT_MAXIMUM] = yuvInputSizes != null
                    ? CameraTestUtils.getMaxSize(yuvInputSizes)
                    : null;
            Size[] maxResYuvInputSizes = sm.isUltraHighResolutionSensor()
                    ?  maxResConfigs.getInputSizes(ImageFormat.YUV_420_888)
                    : null;
            mMaxYuvInputSizes[INPUT_MAX_RES] = maxResYuvInputSizes != null
                    ? CameraTestUtils.getMaxSize(maxResYuvInputSizes)
                    : null;

            Size[] y8InputSizes = configs.getInputSizes(ImageFormat.Y8);
            mMaxInputY8Size = y8InputSizes != null
                    ? CameraTestUtils.getMaxSize(y8InputSizes)
                    : null;
        }

        public final Size getOutputSizeForFormat(int format, int resolutionIndex) {
            if (resolutionIndex >= RESOLUTION_COUNT) {
                return new Size(0, 0);
            }

            switch (format) {
                case PRIV:
                    return mMaxPrivSizes[resolutionIndex];
                case YUV:
                    return mMaxYuvSizes[resolutionIndex];
                case JPEG:
                    return mMaxJpegSizes[resolutionIndex];
                case Y8:
                    return mMaxY8Sizes[resolutionIndex];
                case HEIC:
                    return mMaxHeicSizes[resolutionIndex];
                case RAW:
                    if (resolutionIndex == MAX_RES) {
                        return mMaxResolutionRawSize;
                    }
                    return mMaxRawSize;
                default:
                    return new Size(0, 0);
            }
        }

        public final Size getMaxInputSizeForFormat(int format, int resolutionIndex) {
            int inputResolutionIndex = getInputResolutionIndex(resolutionIndex);
            if (inputResolutionIndex >= INPUT_RESOLUTION_COUNT || inputResolutionIndex == -1) {
                return new Size(0, 0);
            }
            switch (format) {
                case PRIV:
                    return mMaxPrivInputSizes[inputResolutionIndex];
                case YUV:
                    return mMaxYuvInputSizes[inputResolutionIndex];
                case Y8:
                    return mMaxInputY8Size;
                case RAW:
                    return mMaxResolutionRawSize;
                default:
                    return new Size(0, 0);
            }
        }

        public static String combinationToString(int[] combination) {
            return combinationToString(combination, /*useCaseSpecified*/ false);
        }

        public static String combinationToString(int[] combination, boolean useCaseSpecified) {
            StringBuilder b = new StringBuilder("{ ");
            int i = 0;
            while (i < combination.length) {
                int format = combination[i];
                int sizeLimit = combination[i + 1];

                appendFormatSize(b, format, sizeLimit);
                if (useCaseSpecified) {
                    int streamUseCase = combination[i + 2];
                    appendStreamUseCase(b, streamUseCase);
                    i += 1;
                }
                i += 2;
                b.append(" ");
            }
            b.append("}");
            return b.toString();
        }

        public static String reprocessCombinationToString(int[] reprocessCombination) {
            // reprocessConfig[0..1] is the input configuration
            StringBuilder b = new StringBuilder("Input: ");
            appendFormatSize(b, reprocessCombination[0], reprocessCombination[1]);

            // reprocessCombnation[0..1] is also output combination to be captured as reprocess
            // input.
            b.append(", Outputs: { ");
            for (int i = 0; i < reprocessCombination.length; i += 2) {
                int format = reprocessCombination[i];
                int sizeLimit = reprocessCombination[i + 1];

                appendFormatSize(b, format, sizeLimit);
                b.append(" ");
            }
            b.append("}");
            return b.toString();
        }

        int getInputResolutionIndex(int resolutionIndex) {
            switch (resolutionIndex) {
                case MAXIMUM:
                    return INPUT_MAXIMUM;
                case MAX_RES:
                    return INPUT_MAX_RES;
            }
            return -1;
        }

        private static void appendFormatSize(StringBuilder b, int format, int size) {
            switch (format) {
                case PRIV:
                    b.append("[PRIV, ");
                    break;
                case JPEG:
                    b.append("[JPEG, ");
                    break;
                case YUV:
                    b.append("[YUV, ");
                    break;
                case Y8:
                    b.append("[Y8, ");
                    break;
                case RAW:
                    b.append("[RAW, ");
                    break;
                default:
                    b.append("[UNK, ");
                    break;
            }

            switch (size) {
                case PREVIEW:
                    b.append("PREVIEW]");
                    break;
                case RECORD:
                    b.append("RECORD]");
                    break;
                case MAXIMUM:
                    b.append("MAXIMUM]");
                    break;
                case VGA:
                    b.append("VGA]");
                    break;
                case VGA_FULL_FOV:
                    b.append("VGA_FULL_FOV]");
                    break;
                case MAX_30FPS:
                    b.append("MAX_30FPS]");
                    break;
                case S720P:
                    b.append("S720P]");
                    break;
                case S1440P:
                    b.append("S1440P]");
                    break;
                case MAX_RES:
                    b.append("MAX_RES]");
                    break;
                case S1080P:
                    b.append("S1080P]");
                    break;
                default:
                    b.append("UNK]");
                    break;
            }
        }

        private static void appendStreamUseCase(StringBuilder b, int streamUseCase) {
            b.append(", ");
            switch (streamUseCase) {
                case USE_CASE_PREVIEW:
                    b.append("USE_CASE_PREVIEW");
                    break;
                case USE_CASE_PREVIEW_VIDEO_STILL:
                    b.append("USE_CASE_PREVIEW_VIDEO_STILL");
                    break;
                case USE_CASE_STILL_CAPTURE:
                    b.append("USE_CASE_STILL_CAPTURE");
                    break;
                case USE_CASE_VIDEO_CALL:
                    b.append("USE_CASE_VIDEO_CALL");
                    break;
                case USE_CASE_VIDEO_RECORD:
                    b.append("USE_CASE_VIDEO_RECORD");
                    break;
                case USE_CASE_CROPPED_RAW:
                    b.append("USE_CASE_CROPPED_RAW");
                    break;
                default:
                    b.append("UNK STREAM_USE_CASE");
                    break;
            }
            b.append(";");
        }
    }

    private static Size getMaxRecordingSize(String cameraId) {
        int id = Integer.valueOf(cameraId);

        int quality =
                CamcorderProfile.hasProfile(id, CamcorderProfile.QUALITY_2160P)
                    ?  CamcorderProfile.QUALITY_2160P :
                CamcorderProfile.hasProfile(id, CamcorderProfile.QUALITY_1080P)
                    ?  CamcorderProfile.QUALITY_1080P :
                CamcorderProfile.hasProfile(id, CamcorderProfile.QUALITY_720P)
                    ?  CamcorderProfile.QUALITY_720P :
                CamcorderProfile.hasProfile(id, CamcorderProfile.QUALITY_480P)
                    ?  CamcorderProfile.QUALITY_480P :
                CamcorderProfile.hasProfile(id, CamcorderProfile.QUALITY_QVGA)
                    ?  CamcorderProfile.QUALITY_QVGA :
                CamcorderProfile.hasProfile(id, CamcorderProfile.QUALITY_CIF)
                    ?  CamcorderProfile.QUALITY_CIF :
                CamcorderProfile.hasProfile(id, CamcorderProfile.QUALITY_QCIF)
                    ?  CamcorderProfile.QUALITY_QCIF :
                    -1;

        assertTrue("No recording supported for camera id " + cameraId, quality != -1);

        CamcorderProfile maxProfile = CamcorderProfile.get(id, quality);
        return new Size(maxProfile.videoFrameWidth, maxProfile.videoFrameHeight);
    }

    private static Size getMaxExternalRecordingSize(
            String cameraId, StreamConfigurationMap config) {
        final Size fullHD = new Size(1920, 1080);

        Size[] videoSizeArr = config.getOutputSizes(android.media.MediaRecorder.class);
        List<Size> sizes = new ArrayList<Size>();
        for (Size sz: videoSizeArr) {
            if (sz.getWidth() <= fullHD.getWidth() && sz.getHeight() <= fullHD.getHeight()) {
                sizes.add(sz);
            }
        }
        List<Size> videoSizes = getAscendingOrderSizes(sizes, /*ascending*/false);
        for (Size sz : videoSizes) {
            long minFrameDuration = config.getOutputMinFrameDuration(
                    android.media.MediaRecorder.class, sz);
            // Give some margin for rounding error
            if (minFrameDuration < (1e9 / 29.9)) {
                Log.i(TAG, "External camera " + cameraId + " has max video size:" + sz);
                return sz;
            }
        }
        fail("Camera " + cameraId + " does not support any 30fps video output");
        return fullHD; // doesn't matter what size is returned here
    }

    private static Size getMaxPreviewSize(Context context, String cameraId) {
        try {
            WindowManager windowManager = context.getSystemService(WindowManager.class);
            assertNotNull("Could not find WindowManager service.", windowManager);

            WindowMetrics windowMetrics = windowManager.getCurrentWindowMetrics();
            Rect windowBounds = windowMetrics.getBounds();

            int width = windowBounds.width();
            int height = windowBounds.height();

            if (height > width) {
                height = width;
                width = windowBounds.height();
            }

            CameraManager camMgr = context.getSystemService(CameraManager.class);
            List<Size> orderedPreviewSizes = CameraTestUtils.getSupportedPreviewSizes(
                    cameraId, camMgr, PREVIEW_SIZE_BOUND);

            if (orderedPreviewSizes != null) {
                for (Size size : orderedPreviewSizes) {
                    if (width >= size.getWidth()
                            && height >= size.getHeight()) {
                        return size;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getMaxPreviewSize Failed. " + e);
        }
        return PREVIEW_SIZE_BOUND;
    }

    /**
     * Use the external feature flag to check if external camera is supported.
     * If it is, iterate through the camera ids under test to verify that an
     * external camera is connected.
     *
     * @param cameraIds list of camera ids under test
     * @param packageManager package manager instance for checking feature flag
     * @param cameraManager camera manager for getting camera characteristics
     *
     */
    public static void verifyExternalCameraConnected(String[] cameraIds,
            PackageManager packageManager, CameraManager cameraManager) throws Exception {
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_EXTERNAL)) {
            boolean externalCameraConnected = false;
            for (int i = 0; i < cameraIds.length; i++) {
                CameraCharacteristics props =
                        cameraManager.getCameraCharacteristics(cameraIds[i]);
                assertNotNull("Can't get camera characteristics for camera "
                        + cameraIds[i], props);
                Integer lensFacing = props.get(CameraCharacteristics.LENS_FACING);
                assertNotNull("Can't get lens facing info", lensFacing);
                if (lensFacing == CameraCharacteristics.LENS_FACING_EXTERNAL) {
                    externalCameraConnected = true;
                }
            }
            assertTrue("External camera is not connected on device with FEATURE_CAMERA_EXTERNAL",
                    externalCameraConnected);
        }
    }

    /**
     * Verifies the presence of keys in the supportedKeys list.
     *
     * @param keys list of keys to be checked
     * @param supportedKeys list utilized to verify presence of keys
     * @param expectedResult true if keys should be present, false if not
     *
     */
    public static <T> void checkKeysAreSupported(T[] keys, Set<T> supportedKeys,
            boolean expectedResult) {
        String errorMsg = expectedResult ? " key should be present "
                : " key should not be present ";
        for (T currKey : keys) {
            assertTrue(currKey + errorMsg
                    + " among the supported keys!",
                    supportedKeys.contains(currKey) == expectedResult);
        }
    }


    /**
     * Verifies the presence of keys in the supportedKeys list.
     *
     * @param keys list of keys to be checked
     * @param supportedKeys list utilized to verify presence of keys
     * @param expectedResult true if keys should be present, false if not
     *
     */
    public static <T> void checkKeysAreSupported(List<T[]> keys, Set<T> supportedKeys,
            boolean expectedResult) {
        for (T[] k : keys) {
            checkKeysAreSupported(k, supportedKeys, expectedResult);
        }
    }

    /**
     * Check if the camera device keeps stabilization off
     *
     * @param result The capture request builder
     * @return true if stabilization is OFF
     */
    public static boolean isStabilizationOff(CaptureRequest request) {
        Integer stabilizationMode = request.get(
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE);
        assertNotNull("Value of Key CONTROL_VIDEO_STABILIZATION_MODE shouldn't be null",
                stabilizationMode);

        return (stabilizationMode == CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF);
    }
}
