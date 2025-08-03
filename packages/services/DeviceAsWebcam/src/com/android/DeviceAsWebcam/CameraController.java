/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.DeviceAsWebcam;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.HardwareBuffer;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.display.DisplayManager;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageWriter;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.ArrayMap;
import android.util.Log;
import android.util.Range;
import android.util.Rational;
import android.util.Size;
import android.view.Display;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.DeviceAsWebcam.utils.UserPrefs;
import com.android.deviceaswebcam.flags.Flags;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * This class controls the operation of the camera - primarily through the public calls
 * - startPreviewStreaming
 * - startWebcamStreaming
 * - stopPreviewStreaming
 * - stopWebcamStreaming
 * These calls do what they suggest - that is start / stop preview and webcam streams. They
 * internally book-keep whether they need to start a preview stream alongside a webcam stream or
 * by itself, and vice-versa.
 * For the webcam stream, it delegates the job of interacting with the native service
 * code - used for encoding ImageReader image callbacks, to the Foreground service (it stores a weak
 * reference to the foreground service during construction).
 */
public class CameraController {
    private static final String TAG = "CameraController";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);

    // Camera session state - when camera is actually being used
    enum CameraStreamingState {
        NO_STREAMING,
        WEBCAM_STREAMING,
        PREVIEW_STREAMING,
        PREVIEW_AND_WEBCAM_STREAMING
    };

    // Camera availability states
    enum CameraAvailabilityState {
        AVAILABLE,
        UNAVAILABLE
    };

    private static final int MAX_BUFFERS = 4;
    // The ratio to the active array size that will be used to determine the metering rectangle
    // size.
    private static final float METERING_RECTANGLE_SIZE_RATIO = 0.15f;

    @Nullable
    private CameraId mBackCameraId = null;
    @Nullable
    private CameraId mFrontCameraId = null;

    // Tracks if Webcam should drop performance optimizations to get the best quality.
    private boolean mHighQualityModeEnabled = false;

    private ImageReader mImgReader;
    private Object mImgReaderLock = new Object();
    private ImageWriter mImageWriter;

    // current camera session state
    private CameraStreamingState mCurrentState = CameraStreamingState.NO_STREAMING;

    // current camera availability state - to be accessed only from camera related callbacks which
    // execute on mCameraCallbacksExecutor. This isn't a part of mCameraInfo since that is static
    // information about a camera and has looser thread access requirements.
    private ArrayMap<String, CameraAvailabilityState> mCameraAvailabilityState = new ArrayMap<>();

    private Context mContext;
    private WeakReference<DeviceAsWebcamFgService> mServiceWeak;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CameraManager mCameraManager;
    private CameraDevice mCameraDevice;
    private Handler mImageReaderHandler;
    private Executor mCameraCallbacksExecutor;
    private Executor mServiceEventsExecutor;
    private SurfaceTexture mPreviewSurfaceTexture;
    /**
     * Registered by the Preview Activity, and called by CameraController when preview size changes
     * as a result of the webcam stream changing.
     */
    private Consumer<Size> mPreviewSizeChangeListener;
    private Surface mPreviewSurface;
    private Size mDisplaySize;
    private Size mPreviewSize;
    // Executor for ImageWriter thread - used when camera is evicted and webcam is streaming.
    private ScheduledExecutorService mImageWriterEventsExecutor;

    // This is set up only when we need to show the camera access blocked logo and reset
    // when camera is available again - since its going to be a rare occurrence that camera is
    // actually evicted when webcam is streaming.
    private byte[] mCombinedBitmapBytes;

    private OutputConfiguration mPreviewOutputConfiguration;
    private OutputConfiguration mWebcamOutputConfiguration;
    private List<OutputConfiguration> mOutputConfigurations;
    private CameraCaptureSession mCaptureSession;
    private ConditionVariable mReadyToStream = new ConditionVariable();
    private ConditionVariable mCaptureSessionReady = new ConditionVariable();
    private AtomicBoolean mStartCaptureWebcamStream = new AtomicBoolean(false);
    private final Object mSerializationLock = new Object();
    // timestamp -> Image
    private ConcurrentHashMap<Long, ImageAndBuffer> mImageMap = new ConcurrentHashMap<>();
    private List<CameraId> mAvailableCameraIds = new ArrayList<>();
    @Nullable
    private CameraId mCameraId = null;
    private ArrayMap<CameraId, CameraInfo> mCameraInfoMap = new ArrayMap<>();
    @Nullable
    private float[] mTapToFocusPoints = null;
    private static class StreamConfigs {
        StreamConfigs(boolean mjpegP, int widthP, int heightP, int fpsP) {
            isMjpeg = mjpegP;
            width = widthP;
            height = heightP;
            fps = fpsP;
        }

        boolean isMjpeg;
        int width;
        int height;
        int fps;
    };
    private StreamConfigs mStreamConfigs;
    private CameraDevice.StateCallback mCameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            if (VERBOSE) {
                Log.v(TAG, "Camera device opened, creating capture session now");
            }
            mCameraDevice = cameraDevice;
            mReadyToStream.open();
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            if (VERBOSE) {
                Log.v(TAG, "onDisconnected: " + cameraDevice.getId() +
                        " camera available state " +
                        mCameraAvailabilityState.get(cameraDevice.getId()));
            }
            handleDisconnected();
        }

        private void handleDisconnected() {
            mServiceEventsExecutor.execute(() -> {
                synchronized (mSerializationLock) {
                    mCameraDevice = null;
                    stopStreamingAltogetherLocked(/*closeImageReader*/false);
                    if (mStartCaptureWebcamStream.get()) {
                        startShowingCameraUnavailableLogo();
                    }
                }
            });
        }
        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            if (VERBOSE) {
                Log.e(TAG, "Camera id  " + cameraDevice.getId() + ": onError " + error);
            }
            mReadyToStream.open();
            if (mStartCaptureWebcamStream.get()) {
                startShowingCameraUnavailableLogo();
            }
        }
    };
    private CameraCaptureSession.CaptureCallback mCaptureCallback =
            new CameraCaptureSession.CaptureCallback() {};

    private CameraCaptureSession.StateCallback mCameraCaptureSessionCallback =
            new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    if (mCameraDevice == null) {
                        return;
                    }
                    mCaptureSession = cameraCaptureSession;
                    try {
                        mCaptureSession.setSingleRepeatingRequest(
                                mPreviewRequestBuilder.build(), mCameraCallbacksExecutor,
                                mCaptureCallback);
                    } catch (CameraAccessException e) {
                        Log.e(TAG, "setSingleRepeatingRequest failed", e);
                    }
                    mCaptureSessionReady.open();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession captureSession) {
                    Log.e(TAG, "Failed to configure CameraCaptureSession");
                }
            };

    private CameraManager.AvailabilityCallback mCameraAvailabilityCallbacks =
            new CameraManager.AvailabilityCallback() {
        @Override
        public void onCameraAvailable(String cameraId) {
            mCameraAvailabilityState.put(cameraId, CameraAvailabilityState.AVAILABLE);
            if (VERBOSE) {
                Log.v(TAG, "onCameraAvailable: " + cameraId);
            }
            // We want to attempt to start webcam streaming when :
            // webcam was already streaming and the camera that was streaming became available.
            // The attempt to start streaming the camera may succeed or fail. If it fails,
            // (for example: if the camera is available but another client is using a camera which
            // cannot be opened concurrently with mCameraId), it'll be handled by the onError
            // callback.
            if (mStartCaptureWebcamStream.get() &&
                    mCameraAvailabilityState.get(mCameraId.mainCameraId) ==
                            CameraAvailabilityState.AVAILABLE) {
                if (VERBOSE) {
                    Log.v(TAG, "Camera available : try starting webcam stream for camera id "
                            + mCameraId.mainCameraId);
                }
                handleOnCameraAvailable();
            }

        }

        @Override
        public void onCameraUnavailable(String cameraId) {
            // We're unconditionally waiting for available - mStartCaptureWebcamStream will decide
            // whether we need to do anything about it.
            if (VERBOSE) {
                Log.v(TAG, "Camera id " + cameraId + " unavailable");
            }
            mCameraAvailabilityState.put(cameraId, CameraAvailabilityState.UNAVAILABLE);
        }
    };

    private ImageReader.OnImageAvailableListener mOnImageAvailableListener =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image;
                    HardwareBuffer hardwareBuffer;
                    long ts;
                    DeviceAsWebcamFgService service = mServiceWeak.get();
                    synchronized (mImgReaderLock) {
                        if (reader != mImgReader) {
                            return;
                        }
                        if (service == null) {
                            Log.e(TAG, "Service is dead, what ?");
                            return;
                        }
                        if (mImageMap.size() >= MAX_BUFFERS) {
                            Log.w(TAG, "Too many buffers acquired in onImageAvailable, returning");
                            return;
                        }
                        // Get native HardwareBuffer from the next image (we should never
                        // accumulate images since we're not doing any compute work on the
                        // imageReader thread) and
                        // send it to the native layer for the encoder to process.
                        // Acquire latest Image and get the HardwareBuffer
                        image = reader.acquireNextImage();
                        if (VERBOSE) {
                            Log.v(TAG, "Got acquired Image in onImageAvailable callback for reader "
                                    + reader);
                        }
                        if (image == null) {
                            if (VERBOSE) {
                                Log.e(TAG, "More images than MAX acquired ?");
                            }
                            return;
                        }
                        ts = image.getTimestamp();
                        hardwareBuffer = image.getHardwareBuffer();
                    }
                    mImageMap.put(ts, new ImageAndBuffer(image, hardwareBuffer));
                    // Callback into DeviceAsWebcamFgService to encode image
                    if ((!mStartCaptureWebcamStream.get()) || (service.nativeEncodeImage(
                            hardwareBuffer, ts, getCurrentRotation()) != 0)) {
                        if (VERBOSE) {
                            Log.v(TAG,
                                    "Couldn't get buffer immediately, returning image images. "
                                            + "acquired size "
                                            + mImageMap.size());
                        }
                        returnImage(ts);
                    }
                }
            };

    private volatile float mZoomRatio;
    private RotationProvider mRotationProvider;
    private RotationUpdateListener mRotationUpdateListener = null;
    private CameraInfo mCameraInfo = null;
    private UserPrefs mUserPrefs;
    VendorCameraPrefs mRroCameraInfo;

    public CameraController(Context context, WeakReference<DeviceAsWebcamFgService> serviceWeak) {
        mContext = context;
        mServiceWeak = serviceWeak;
        if (mContext == null) {
            Log.e(TAG, "Application context is null!, something is going to go wrong");
            return;
        }
        startBackgroundThread();
        mCameraManager = mContext.getSystemService(CameraManager.class);
        mDisplaySize = getDisplayPreviewSize();
        mCameraManager.registerAvailabilityCallback(
                mCameraCallbacksExecutor, mCameraAvailabilityCallbacks);
        mUserPrefs = new UserPrefs(mContext);
        mHighQualityModeEnabled = Flags.highQualityToggle() &&
                mUserPrefs.fetchHighQualityModeEnabled(/*defaultValue*/ false);
        mRroCameraInfo = createVendorCameraPrefs(mHighQualityModeEnabled);
        refreshAvailableCameraIdList();
        refreshLensFacingCameraIds();

        mCameraId = fetchCameraIdFromUserPrefs(/*defaultCameraId*/ mBackCameraId);
        mCameraInfo = getOrCreateCameraInfo(mCameraId);
        mZoomRatio = mUserPrefs.fetchZoomRatio(mCameraId.toString(), /*defaultZoom*/ 1.0f);

        mRotationProvider = new RotationProvider(context.getApplicationContext(),
                mCameraInfo.getSensorOrientation(), mCameraInfo.getLensFacing());
        // Adds a listener to enable the RotationProvider so that we can get the rotation
        // degrees info to rotate the webcam stream images.
        mRotationProvider.addListener(mCameraCallbacksExecutor, rotation -> {
            if (mRotationUpdateListener != null) {
                mRotationUpdateListener.onRotationUpdated(rotation);
            }
        });
    }

    @Nullable
    private CameraId fetchCameraIdFromUserPrefs(@Nullable CameraId defaultCameraId) {
        String cameraIdString = mUserPrefs.fetchCameraId(null);
        CameraId cameraId = convertAndValidateCameraIdString(cameraIdString);
        return cameraId != null ? cameraId : defaultCameraId;
    }

    @Nullable
    private CameraId fetchBackCameraIdFromUserPrefs(@Nullable CameraId defaultCameraId) {
        String cameraIdString = mUserPrefs.fetchBackCameraId(null);
        CameraId cameraId = convertAndValidateCameraIdString(cameraIdString);
        return cameraId != null ? cameraId : defaultCameraId;
    }

    @Nullable
    private CameraId fetchFrontCameraIdFromUserPrefs(@Nullable CameraId defaultCameraId) {
        String cameraIdString = mUserPrefs.fetchFrontCameraId(null);
        CameraId cameraId = convertAndValidateCameraIdString(cameraIdString);
        return cameraId != null ? cameraId : defaultCameraId;
    }

    /**
     * Converts the camera id string to {@link CameraId} and returns it only when it is includes in
     * the available camera id list.
     */
    @Nullable
    private CameraId convertAndValidateCameraIdString(@Nullable String cameraIdString) {
        CameraId cameraId = CameraId.fromCameraIdString(cameraIdString);
        if (cameraId != null && !mAvailableCameraIds.contains(cameraId)) {
            cameraId = null;
        }
        return cameraId;
    }

    private void convertARGBToRGBA(ByteBuffer argb) {
        // Android Bitmap.Config.ARGB_8888 is laid out as RGBA in an int and java ByteBuffer by
        // default is big endian.
        for (int i = 0; i < argb.capacity(); i+= 4) {
            byte r = argb.get(i);
            byte g = argb.get(i + 1);
            byte b = argb.get(i + 2);
            byte a = argb.get(i + 3);

            //libyuv expects BGRA
            argb.put(i, b);
            argb.put(i + 1, g);
            argb.put(i + 2, r);
            argb.put(i + 3, a);
        }
    }

    private void setupBitmaps(int width, int height) {
        // Initialize logoBitmap. Should fit 'in' enclosed by any webcam stream
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        // We want 1/2 of the screen being covered by the camera blocked logo
        Bitmap logoBitmap =
                BitmapFactory.decodeResource(mContext.getResources(),
                        R.drawable.camera_access_blocked, options);
        int scaledWidth, scaledHeight;
        if (logoBitmap.getWidth() > logoBitmap.getHeight()) {
            scaledWidth = (int)(0.5 * width);
            scaledHeight =
                    (int)(scaledWidth * (float)logoBitmap.getHeight() / logoBitmap.getWidth());
        } else {
            scaledHeight = (int)(0.5 * height);
            scaledWidth =
                    (int)(scaledHeight * (float)logoBitmap.getWidth() / logoBitmap.getHeight());
        }
        // Combined Bitmap which will hold background + camera access blocked image
        Bitmap combinedBitmap =
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(combinedBitmap);
        // Offsets to start composed image from
        int offsetX = (width - scaledWidth) / 2;
        int offsetY = (height - scaledHeight)/ 2;
        int endX = offsetX + scaledWidth;
        int endY = offsetY + scaledHeight;
        canvas.drawBitmap(logoBitmap,
                new Rect(0, 0, logoBitmap.getWidth(), logoBitmap.getHeight()),
                new Rect(offsetX, offsetY, endX, endY), null);
        ByteBuffer byteBuffer = ByteBuffer.allocate(combinedBitmap.getByteCount());
        combinedBitmap.copyPixelsToBuffer(byteBuffer);
        convertARGBToRGBA(byteBuffer);
        mCombinedBitmapBytes = byteBuffer.array();
    }

    private void refreshAvailableCameraIdList() {
        mAvailableCameraIds.clear();
        String[] cameraIdList;
        try {
            cameraIdList = mCameraManager.getCameraIdList();
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to retrieve the camera id list from CameraManager!", e);
            return;
        }

        List<String> ignoredCameraList = mRroCameraInfo.getIgnoredCameraList();

        for (String cameraId : cameraIdList) {
            // Skips the ignored cameras
            if (ignoredCameraList.contains(cameraId)) {
                continue;
            }

            CameraCharacteristics characteristics = getCameraCharacteristicsOrNull(cameraId);

            if (characteristics == null) {
                continue;
            }

            // Only lists backward compatible cameras
            if (!isBackwardCompatible(characteristics)) {
                continue;
            }

            List<VendorCameraPrefs.PhysicalCameraInfo> physicalCameraInfos =
                    mRroCameraInfo.getPhysicalCameraInfos(cameraId);

            if (physicalCameraInfos == null || physicalCameraInfos.isEmpty()) {
                mAvailableCameraIds.add(new CameraId(cameraId, null));
                continue;
            }

            for (VendorCameraPrefs.PhysicalCameraInfo physicalCameraInfo :
                    physicalCameraInfos) {
                // Only lists backward compatible cameras
                CameraCharacteristics physChars = getCameraCharacteristicsOrNull(
                        physicalCameraInfo.physicalCameraId);
                if (isBackwardCompatible(physChars)) {
                    mAvailableCameraIds.add(
                            new CameraId(cameraId, physicalCameraInfo.physicalCameraId));
                }
            }
        }
    }

    private void refreshLensFacingCameraIds() {
        // Loads the default back and front camera from the user prefs.
        mBackCameraId = fetchBackCameraIdFromUserPrefs(null);
        mFrontCameraId = fetchFrontCameraIdFromUserPrefs(null);

        if (mBackCameraId != null && mFrontCameraId != null) {
            return;
        }

        for (CameraId cameraId : mAvailableCameraIds) {
            CameraCharacteristics characteristics = getCameraCharacteristicsOrNull(
                    cameraId.mainCameraId);
            if (characteristics == null) {
                continue;
            }

            Integer lensFacing = getCameraCharacteristic(characteristics,
                    CameraCharacteristics.LENS_FACING);
            if (lensFacing == null) {
                continue;
            }
            if (mBackCameraId == null && lensFacing == CameraMetadata.LENS_FACING_BACK) {
                mBackCameraId = cameraId;
            } else if (mFrontCameraId == null
                    && lensFacing == CameraMetadata.LENS_FACING_FRONT) {
                mFrontCameraId = cameraId;
            }
        }
    }

    /**
     * Returns the available {@link CameraId} list.
     */
    public List<CameraId> getAvailableCameraIds() {
        return mAvailableCameraIds;
    }

    public CameraInfo getOrCreateCameraInfo(CameraId cameraId) {
        CameraInfo cameraInfo = mCameraInfoMap.get(cameraId);
        if (cameraInfo != null) {
            return cameraInfo;
        }

        cameraInfo = createCameraInfo(cameraId);
        mCameraInfoMap.put(cameraId, cameraInfo);
        return cameraInfo;
    }

    private CameraInfo createCameraInfo(CameraId cameraId) {
        CameraCharacteristics chars = getCameraCharacteristicsOrNull(cameraId.mainCameraId);
        CameraCharacteristics physicalChars = getCameraCharacteristicsOrNull(
                cameraId.physicalCameraId != null ? cameraId.physicalCameraId
                        : cameraId.mainCameraId);
        // Retrieves the physical camera zoom ratio range from the vendor camera prefs.
        Range<Float> zoomRatioRange = mRroCameraInfo.getPhysicalCameraZoomRatioRange(cameraId);
        // Retrieves the physical camera zoom ratio range if no custom data is found.
        if (zoomRatioRange == null) {
            zoomRatioRange = getCameraCharacteristic(physicalChars,
                    CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
        }

        // Logical cameras will be STANDARD category by default. For physical cameras, their
        // categories should be specified by the vendor. If the category is not provided, use
        // focal lengths to determine the physical camera's category.
        CameraCategory cameraCategory = CameraCategory.STANDARD;
        if (cameraId.physicalCameraId != null) {
            cameraCategory = mRroCameraInfo.getCameraCategory(cameraId);
            if (cameraCategory == CameraCategory.UNKNOWN) {
                if (physicalChars != null) {
                    cameraCategory = calculateCameraCategoryByFocalLengths(physicalChars);
                }
            }
        }
        // We should consider using a builder pattern here if the parameters grow a lot.
        return new CameraInfo(
                new CameraId(cameraId.mainCameraId, cameraId.physicalCameraId),
                getCameraCharacteristic(chars, CameraCharacteristics.LENS_FACING),
                getCameraCharacteristic(chars, CameraCharacteristics.SENSOR_ORIENTATION),
                zoomRatioRange,
                getCameraCharacteristic(chars,
                        CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE),
                isFacePrioritySupported(chars),
                isStreamUseCaseSupported(chars),
                cameraCategory
        );
    }

    private CameraCategory calculateCameraCategoryByFocalLengths(
            CameraCharacteristics characteristics) {
        float[] focalLengths = characteristics.get(
                CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);

        if (focalLengths == null) {
            return CameraCategory.UNKNOWN;
        }

        final int standardCamera = 0x1;
        final int telephotoCamera = 0x2;
        final int wideAngleCamera = 0x4;
        final int ultraWideCamera = 0x8;

        int cameraCategory = 0;

        for (float focalLength : focalLengths) {
            if (focalLength >= 50) {
                cameraCategory |= telephotoCamera;
            } else if (focalLength >= 30) {
                cameraCategory |= standardCamera;
            } else if (focalLength >= 20) {
                cameraCategory |= wideAngleCamera;
            } else {
                cameraCategory |= ultraWideCamera;
            }
        }

        return switch (cameraCategory) {
            case telephotoCamera -> CameraCategory.TELEPHOTO;
            case wideAngleCamera -> CameraCategory.WIDE_ANGLE;
            case ultraWideCamera -> CameraCategory.ULTRA_WIDE;
            default -> CameraCategory.STANDARD;
        };
    }

    @Nullable
    private static <T> T getCameraCharacteristic(CameraCharacteristics chars,
            CameraCharacteristics.Key<T> key) {
        return chars.get(key);
    }

    @Nullable
    private CameraCharacteristics getCameraCharacteristicsOrNull(String cameraId) {
        try {
            CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(
                    cameraId);
            return characteristics;
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to get characteristics for camera " + cameraId
                    + ".", e);
        }
      return null;
    }

    @Nullable
    private <T> T getCameraCharacteristic(String cameraId, CameraCharacteristics.Key<T> key) {
        CameraCharacteristics chars = getCameraCharacteristicsOrNull(cameraId);
        if (chars != null) {
            return chars.get(key);
        }
        return null;
    }

    public void setWebcamStreamConfig(boolean mjpeg, int width, int height, int fps) {
        if (VERBOSE) {
            Log.v(TAG, "Set stream config service : mjpeg  ? " + mjpeg + " width" + width +
                    " height " + height + " fps " + fps);
        }
        synchronized (mSerializationLock) {
            long usage = HardwareBuffer.USAGE_CPU_READ_OFTEN | HardwareBuffer.USAGE_VIDEO_ENCODE;
            mStreamConfigs = new StreamConfigs(mjpeg, width, height, fps);
            synchronized (mImgReaderLock) {
                if (mImgReader != null) {
                    mImgReader.close();
                }
                mImgReader = new ImageReader.Builder(width, height)
                        .setMaxImages(MAX_BUFFERS)
                        .setDefaultHardwareBufferFormat(HardwareBuffer.YCBCR_420_888)
                        .setUsage(usage)
                        .build();
                mImgReader.setOnImageAvailableListener(mOnImageAvailableListener,
                        mImageReaderHandler);
            }
        }
    }

    private void fillImageWithCameraAccessBlockedLogo(Image img) {
        Image.Plane[] planes = img.getPlanes();

        ByteBuffer rgbaBuffer = planes[0].getBuffer();
        // Copy the bitmap array
        rgbaBuffer.put(mCombinedBitmapBytes);
    }

    private void handleOnCameraAvailable() {
        // Offload to mServiceEventsExecutor since any camera operations which require
        // mSerializationLock should be performed on mServiceEventsExecutor thread.
        mServiceEventsExecutor.execute(() -> {
            synchronized (mSerializationLock) {
                if (mCameraDevice != null) {
                    return;
                }
                stopShowingCameraUnavailableLogo();
                setWebcamStreamConfig(mStreamConfigs.isMjpeg, mStreamConfigs.width,
                        mStreamConfigs.height, mStreamConfigs.fps);
                startWebcamStreamingNoOffload();
            }
        });
    }

    /**
     * Stops showing the camera unavailable logo. Should only be called on the
     * mServiceEventsExecutor thread
     */
    private void stopShowingCameraUnavailableLogo() {
        // destroy the executor since camera getting evicted would be a rare occurrence
        synchronized (mSerializationLock) {
            if (mImageWriterEventsExecutor != null) {
                mImageWriterEventsExecutor.shutdown();
            }
            mImageWriterEventsExecutor = null;
            mImageWriter = null;
            mCombinedBitmapBytes = null;
        }
    }

    private void startShowingCameraUnavailableLogo() {
        mServiceEventsExecutor.execute(() -> {
           startShowingCameraUnavailableLogoNoOffload();
        });
    }

    /**
     * Starts showing the camera unavailable logo. Should only be called on the
     * mServiceEventsExecutor thread
     */
    private void startShowingCameraUnavailableLogoNoOffload() {
        synchronized (mSerializationLock) {
            setupBitmaps(mStreamConfigs.width, mStreamConfigs.height);
            long usage = HardwareBuffer.USAGE_CPU_READ_OFTEN;
            synchronized (mImgReaderLock) {
                if (mImgReader != null) {
                    mImgReader.close();
                }
                mImgReader = new ImageReader.Builder(
                         mStreamConfigs.width, mStreamConfigs.height)
                        .setMaxImages(MAX_BUFFERS)
                        .setDefaultHardwareBufferFormat(HardwareBuffer.RGBA_8888)
                        .setUsage(usage)
                        .build();

                mImgReader.setOnImageAvailableListener(mOnImageAvailableListener,
                        mImageReaderHandler);
            }
            mImageWriter = ImageWriter.newInstance(mImgReader.getSurface(), MAX_BUFFERS);
            // In effect, the webcam stream has started
            mImageWriterEventsExecutor = Executors.newScheduledThreadPool(1);
            mImageWriterEventsExecutor.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    Image img = mImageWriter.dequeueInputImage();
                    // Fill in image
                    fillImageWithCameraAccessBlockedLogo(img);
                    mImageWriter.queueInputImage(img);
                }
            }, /*initialDelay*/0, /*fps period ms*/1000 / mStreamConfigs.fps,
                    TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Must be called with mSerializationLock held on mServiceExecutor thread.
     */
    private void openCameraBlocking() {
        if (mCameraManager == null) {
            Log.e(TAG, "CameraManager is not initialized, aborting");
            return;
        }
        if (mCameraId == null) {
            Log.e(TAG, "No camera is found on the device, aborting");
            return;
        }
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        try {
            mCameraManager.openCamera(mCameraId.mainCameraId, mCameraCallbacksExecutor,
                    mCameraStateCallback);
        } catch (CameraAccessException e) {
            Log.e(TAG, "openCamera failed for cameraId : " + mCameraId.mainCameraId, e);
            startShowingCameraUnavailableLogo();
        }
        mReadyToStream.block();
        mReadyToStream.close();
    }

    private void setupPreviewOnlyStreamLocked(SurfaceTexture previewSurfaceTexture) {
        setupPreviewOnlyStreamLocked(new Surface(previewSurfaceTexture));
    }

    private void setupPreviewOnlyStreamLocked(Surface previewSurface) {
        mPreviewSurface = previewSurface;
        openCameraBlocking();
        mPreviewRequestBuilder = createInitialPreviewRequestBuilder(mPreviewSurface);
        if (mPreviewRequestBuilder == null) {
            return;
        }
        mPreviewOutputConfiguration = new OutputConfiguration(mPreviewSurface);
        if (mCameraInfo.isStreamUseCaseSupported() && shouldUseStreamUseCase()) {
            mPreviewOutputConfiguration.setStreamUseCase(
                    CameraMetadata.SCALER_AVAILABLE_STREAM_USE_CASES_PREVIEW);
        }

        // So that we don't have to reconfigure if / when the preview activity is turned off /
        // on again.
        mWebcamOutputConfiguration = null;
        mOutputConfigurations = Arrays.asList(mPreviewOutputConfiguration);
        mCurrentState = CameraStreamingState.PREVIEW_STREAMING;
        createCaptureSessionBlocking();
    }

    private CaptureRequest.Builder createInitialPreviewRequestBuilder(Surface targetSurface) {
        CaptureRequest.Builder captureRequestBuilder;
        try {
            captureRequestBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        } catch (CameraAccessException e) {
            Log.e(TAG, "createCaptureRequest failed", e);
            stopStreamingAltogetherLocked();
            startShowingCameraUnavailableLogoNoOffload();
            return null;
        }

        int currentFps = 30;
        if (mStreamConfigs != null) {
            currentFps = mStreamConfigs.fps;
        }
        Range<Integer> fpsRange;
        if (currentFps != 0) {
            fpsRange = new Range<>(currentFps, currentFps);
        } else {
            fpsRange = new Range<>(30, 30);
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange);
        captureRequestBuilder.set(CaptureRequest.CONTROL_ZOOM_RATIO, mZoomRatio);
        captureRequestBuilder.addTarget(targetSurface);
        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
        if (mCameraInfo.isFacePrioritySupported()) {
            captureRequestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE,
                    CaptureRequest.CONTROL_SCENE_MODE_FACE_PRIORITY);
        }

        return captureRequestBuilder;
    }

    private static boolean checkArrayContains(@Nullable int[] array, int value) {
        if (array == null) {
            return false;
        }
        for (int val : array) {
            if (val == value) {
                return true;
            }
        }

        return false;
    }

    private static boolean isBackwardCompatible(CameraCharacteristics chars) {
        int[] availableCapabilities = getCameraCharacteristic(chars,
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
        return checkArrayContains(availableCapabilities,
                CaptureRequest.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE);
    }

    private static boolean isFacePrioritySupported(CameraCharacteristics chars) {
        int[] availableSceneModes = getCameraCharacteristic(chars,
                CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES);
        return checkArrayContains(
                availableSceneModes, CaptureRequest.CONTROL_SCENE_MODE_FACE_PRIORITY);
    }

    private static boolean isStreamUseCaseSupported(CameraCharacteristics chars) {
        int[] caps = getCameraCharacteristic(chars,
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
        return checkArrayContains(
                caps, CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_STREAM_USE_CASE);
    }

    // CameraManager which populates the mandatory streams uses the same computation.
    private Size getDisplayPreviewSize() {
        Size ret = new Size(1920, 1080);
        DisplayManager displayManager =
                mContext.getSystemService(DisplayManager.class);
        Display display = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        if (display != null) {
            Point sz = new Point();
            display.getRealSize(sz);
            int width = sz.x;
            int height = sz.y;

            if (height > width) {
                height = width;
                width = sz.y;
            }
            ret = new Size(width, height);
        } else {
            Log.e(TAG, "Invalid default display!");
        }
        return ret;
    }

    // Check whether we satisfy mandatory stream combinations for stream use use case
    private boolean shouldUseStreamUseCase() {
        if (mHighQualityModeEnabled) {
            // Do not use streamusecase if high quality mode is enabled.
            return false;
        }
        // Webcam stream - YUV should be <= 1440p
        // Preview stream should be <= PREVIEW - which is already guaranteed by
        // getSuitablePreviewSize()
        if (mWebcamOutputConfiguration != null && mStreamConfigs != null &&
                (mStreamConfigs.width * mStreamConfigs.height) > (1920 * 1440)) {
            return false;
        }
        return true;
    }

    private void setupPreviewStreamAlongsideWebcamStreamLocked(
            SurfaceTexture previewSurfaceTexture) {
        setupPreviewStreamAlongsideWebcamStreamLocked(new Surface(previewSurfaceTexture));
    }

    private void setupPreviewStreamAlongsideWebcamStreamLocked(Surface previewSurface) {
        if (VERBOSE) {
            Log.v(TAG, "setupPreviewAlongsideWebcam");
        }
        mPreviewSurface = previewSurface;
        mPreviewOutputConfiguration = new OutputConfiguration(mPreviewSurface);
        if (mCameraInfo.isStreamUseCaseSupported() && shouldUseStreamUseCase()) {
            mPreviewOutputConfiguration.setStreamUseCase(
                    CameraMetadata.SCALER_AVAILABLE_STREAM_USE_CASES_PREVIEW);
        }

        mPreviewRequestBuilder.addTarget(mPreviewSurface);
        mOutputConfigurations = Arrays.asList(mPreviewOutputConfiguration,
                mWebcamOutputConfiguration);

        mCurrentState = CameraStreamingState.PREVIEW_AND_WEBCAM_STREAMING;
        createCaptureSessionBlocking();
    }

    public void startPreviewStreaming(SurfaceTexture surfaceTexture, Size previewSize,
            Consumer<Size> previewSizeChangeListener) {
        // Started on a background thread since we don't want to be blocking either the activity's
        // or the service's main thread (we call blocking camera open in these methods internally)
        mServiceEventsExecutor.execute(new Runnable() {
            @Override
            public void run() {
                synchronized (mSerializationLock) {
                    mPreviewSurfaceTexture = surfaceTexture;
                    mPreviewSize = previewSize;
                    mPreviewSizeChangeListener = previewSizeChangeListener;
                    switch (mCurrentState) {
                        case NO_STREAMING:
                            setupPreviewOnlyStreamLocked(surfaceTexture);
                            break;
                        case WEBCAM_STREAMING:
                            setupPreviewStreamAlongsideWebcamStreamLocked(surfaceTexture);
                            break;
                        case PREVIEW_STREAMING:
                        case PREVIEW_AND_WEBCAM_STREAMING:
                            Log.e(TAG, "Incorrect current state for startPreviewStreaming " +
                                    mCurrentState);
                    }
                }
            }
        });
    }

    private void setupWebcamOnlyStreamAndOpenCameraLocked() {
        // Setup outputs
        if (VERBOSE) {
            Log.v(TAG, "setupWebcamOnly");
        }
        Surface surface = mImgReader.getSurface();
        openCameraBlocking();
        mCurrentState = CameraStreamingState.WEBCAM_STREAMING;
        if (mCameraDevice != null) {
            mPreviewRequestBuilder = createInitialPreviewRequestBuilder(surface);
            if (mPreviewRequestBuilder == null) {
                Log.e(TAG, "Failed to create the webcam stream.");
                return;
            }
            mWebcamOutputConfiguration = new OutputConfiguration(surface);
            if (mCameraInfo.isStreamUseCaseSupported() && shouldUseStreamUseCase()) {
                mWebcamOutputConfiguration.setStreamUseCase(
                        CameraMetadata.SCALER_AVAILABLE_STREAM_USE_CASES_VIDEO_CALL);
            }
            mOutputConfigurations = Arrays.asList(mWebcamOutputConfiguration);
            createCaptureSessionBlocking();
        }
    }

    private void setupWebcamStreamAndReconfigureSessionLocked() {
        // Setup outputs
        if (VERBOSE) {
            Log.v(TAG, "setupWebcamStreamAndReconfigureSession");
        }
        Surface surface = mImgReader.getSurface();
        mPreviewRequestBuilder.addTarget(surface);
        mWebcamOutputConfiguration = new OutputConfiguration(surface);
        if (mCameraInfo.isStreamUseCaseSupported() && shouldUseStreamUseCase()) {
            mWebcamOutputConfiguration.setStreamUseCase(
                    CameraMetadata.SCALER_AVAILABLE_STREAM_USE_CASES_VIDEO_CALL);
        }
        mCurrentState = CameraStreamingState.PREVIEW_AND_WEBCAM_STREAMING;
        mOutputConfigurations =
                Arrays.asList(mWebcamOutputConfiguration, mPreviewOutputConfiguration);
        createCaptureSessionBlocking();
    }

    /**
     * Adjust preview output configuration when preview size is changed.
     */
    private void adjustPreviewOutputConfiguration() {
        if (mPreviewSurfaceTexture == null || mPreviewSurface == null) {
            return;
        }

        Size suitablePreviewSize = getSuitablePreviewSize();
        // If the required preview size is the same, don't need to adjust the output configuration
        if (Objects.equals(suitablePreviewSize, mPreviewSize)) {
            return;
        }

        // Removes the original preview surface
        mPreviewRequestBuilder.removeTarget(mPreviewSurface);
        // Adjusts the SurfaceTexture default buffer size to match the new preview size
        mPreviewSurfaceTexture.setDefaultBufferSize(suitablePreviewSize.getWidth(),
                suitablePreviewSize.getHeight());
        mPreviewSize = suitablePreviewSize;
        mPreviewRequestBuilder.addTarget(mPreviewSurface);
        mPreviewOutputConfiguration = new OutputConfiguration(mPreviewSurface);
        if (mCameraInfo.isStreamUseCaseSupported()) {
                mPreviewOutputConfiguration.setStreamUseCase(
                        CameraMetadata.SCALER_AVAILABLE_STREAM_USE_CASES_PREVIEW);
        }

        mOutputConfigurations = mWebcamOutputConfiguration != null ? Arrays.asList(
                mWebcamOutputConfiguration, mPreviewOutputConfiguration) : Arrays.asList(
                mPreviewOutputConfiguration);

        // Invokes the preview size change listener so that the preview activity can adjust its
        // size and scale to match the new size.
        if (mPreviewSizeChangeListener != null) {
            mPreviewSizeChangeListener.accept(suitablePreviewSize);
        }
    }
    public void startWebcamStreaming() {
        mServiceEventsExecutor.execute(() -> {
            // Started on a background thread since we don't want to be blocking the service's main
            // thread (we call blocking camera open in these methods internally)
            startWebcamStreamingNoOffload();
        });
    }

    /**
     * Starts webcam streaming. This should only be called on the service events executor thread.
     */
    public void startWebcamStreamingNoOffload() {
        mStartCaptureWebcamStream.set(true);
        synchronized (mSerializationLock) {
            synchronized (mImgReaderLock) {
                if (mImgReader == null) {
                    Log.e(TAG,
                            "Webcam streaming requested without ImageReader initialized");
                    return;
                }
            }
            switch (mCurrentState) {
                // Our current state could also be webcam streaming and we want to start the
                // camera again - example : we never had the camera and were streaming the
                // camera unavailable logo - when camera becomes available we actually want to
                // start streaming camera frames.
                case WEBCAM_STREAMING:
                case NO_STREAMING:
                    setupWebcamOnlyStreamAndOpenCameraLocked();
                    break;
                case PREVIEW_STREAMING:
                    adjustPreviewOutputConfiguration();
                    // Its okay to recreate an already running camera session with
                    // preview since the 'glitch' that we see will not be on the webcam
                    // stream.
                    setupWebcamStreamAndReconfigureSessionLocked();
                    break;
                case PREVIEW_AND_WEBCAM_STREAMING:
                    if (mCameraDevice == null) {
                        // We had been evicted and were streaming fake webcam streams,
                        // preview activity  was selected, and then camera became available.
                        setupWebcamOnlyStreamAndOpenCameraLocked();
                        if (mPreviewSurface != null) {
                            setupPreviewStreamAlongsideWebcamStreamLocked(mPreviewSurface);
                        }
                    } else {
                        Log.e(TAG, "Incorrect current state for startWebcamStreaming "
                                + mCurrentState + " since webcam and preview already streaming");
                    }
            }
        }
    }

    private void stopPreviewStreamOnlyLocked() {
        mPreviewRequestBuilder.removeTarget(mPreviewSurface);
        mOutputConfigurations = Arrays.asList(mWebcamOutputConfiguration);
        createCaptureSessionBlocking();
        mPreviewSurfaceTexture = null;
        mPreviewSizeChangeListener = null;
        mPreviewSurface = null;
        mPreviewSize = null;
        mCurrentState = CameraStreamingState.WEBCAM_STREAMING;
    }

    public void stopPreviewStreaming() {
        // Started on a background thread since we don't want to be blocking either the activity's
        // or the service's main thread (we call blocking camera open in these methods internally)
        mServiceEventsExecutor.execute(new Runnable() {
            @Override
            public void run() {
                synchronized (mSerializationLock) {
                    switch (mCurrentState) {
                        case PREVIEW_AND_WEBCAM_STREAMING:
                            stopPreviewStreamOnlyLocked();
                            break;
                        case PREVIEW_STREAMING:
                            stopStreamingAltogetherLocked();
                            break;
                        case NO_STREAMING:
                        case WEBCAM_STREAMING:
                            Log.e(TAG,
                                    "Incorrect current state for stopPreviewStreaming " +
                                            mCurrentState);
                    }
                }
            }
        });
    }

    private void stopWebcamStreamOnlyLocked() {
        // Re-configure session to have only the preview stream
        // Setup outputs
        mPreviewRequestBuilder.removeTarget(mImgReader.getSurface());
        mOutputConfigurations =
                Arrays.asList(mPreviewOutputConfiguration);
        mCurrentState = CameraStreamingState.PREVIEW_STREAMING;
        mWebcamOutputConfiguration = null;
        createCaptureSessionBlocking();
    }

    private void stopStreamingAltogetherLocked() {
        stopStreamingAltogetherLocked(/*closeImageReader*/true);
    }

    private void stopStreamingAltogetherLocked(boolean closeImageReader) {
        if (VERBOSE) {
            Log.v(TAG, "StopStreamingAltogether");
        }
        mCurrentState = CameraStreamingState.NO_STREAMING;
        synchronized (mImgReaderLock) {
            if (closeImageReader && mImgReader != null) {
                mImgReader.close();
                mImgReader = null;
            }
        }
        if (mCameraDevice != null) {
            mCameraDevice.close();
        }
        mCameraDevice = null;
        mWebcamOutputConfiguration = null;
        mPreviewOutputConfiguration = null;
        mTapToFocusPoints = null;
        mReadyToStream.close();
    }

    public void stopWebcamStreaming() {
        // Started on a background thread since we don't want to be blocking the service's main
        // thread (we call blocking camera open in these methods internally)
        mServiceEventsExecutor.execute(new Runnable() {
            @Override
            public void run() {
                mStartCaptureWebcamStream.set(false);
                synchronized (mSerializationLock) {
                    switch (mCurrentState) {
                        case PREVIEW_AND_WEBCAM_STREAMING:
                            stopWebcamStreamOnlyLocked();
                            break;
                        case WEBCAM_STREAMING:
                            stopStreamingAltogetherLocked();
                            break;
                        case PREVIEW_STREAMING:
                            Log.e(TAG,
                                    "Incorrect current state for stopWebcamStreaming " +
                                            mCurrentState);
                            return;
                    }

                    if (mImageWriterEventsExecutor != null) {
                        stopShowingCameraUnavailableLogo();
                    }
                }
            }
        });
    }

    private void startBackgroundThread() {
        HandlerThread imageReaderThread = new HandlerThread("SdkCameraFrameProviderThread");
        imageReaderThread.start();
        mImageReaderHandler = new Handler(imageReaderThread.getLooper());
        // We need two executor threads since the surface texture add / remove calls from the fg
        // service are going to be served on the main thread. To not wait on capture session
        // creation, onCaptureSequenceCompleted we need a new thread to cater to preview surface
        // addition / removal.
        // b/277099495 has additional context.
        mCameraCallbacksExecutor = Executors.newSingleThreadExecutor();
        mServiceEventsExecutor = Executors.newSingleThreadExecutor();
    }

    private void createCaptureSessionBlocking() {
        if (mCameraId.physicalCameraId != null) {
            for (OutputConfiguration config : mOutputConfigurations) {
                config.setPhysicalCameraId(mCameraId.physicalCameraId);
            }
        }
        // In case we're fake streaming camera frames.
        if (mCameraDevice == null) {
            return;
        }
        try {
            mCameraDevice.createCaptureSession(
                    new SessionConfiguration(
                            SessionConfiguration.SESSION_REGULAR, mOutputConfigurations,
                            mCameraCallbacksExecutor, mCameraCaptureSessionCallback));
            mCaptureSessionReady.block();
            mCaptureSessionReady.close();
        } catch (CameraAccessException e) {
            Log.e(TAG, "createCaptureSession failed", e);
            stopStreamingAltogetherLocked();
            startShowingCameraUnavailableLogoNoOffload();
        }
    }

    public void returnImage(long timestamp) {
        ImageAndBuffer imageAndBuffer = mImageMap.get(timestamp);
        if (imageAndBuffer == null) {
            Log.e(TAG, "Image with timestamp " + timestamp +
                    " was never encoded / already returned");
            return;
        }
        imageAndBuffer.buffer.close();
        imageAndBuffer.image.close();
        mImageMap.remove(timestamp);
        if (VERBOSE) {
            Log.v(TAG, "Returned image " + timestamp);
        }
    }

    /**
     * Returns the {@link CameraInfo} of the working camera.
     */
    public CameraInfo getCameraInfo() {
        return mCameraInfo;
    }

    /**
     * Sets the new zoom ratio setting to the working camera.
     */
    public void setZoomRatio(float zoomRatio) {
        mZoomRatio = zoomRatio;
        mServiceEventsExecutor.execute(() -> {
            synchronized (mSerializationLock) {
                if (mCameraDevice == null || mCaptureSession == null) {
                    return;
                }

                try {
                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoomRatio);
                    mCaptureSession.setSingleRepeatingRequest(mPreviewRequestBuilder.build(),
                            mCameraCallbacksExecutor, mCaptureCallback);
                    mUserPrefs.storeZoomRatio(mCameraId.toString(), mZoomRatio);
                } catch (CameraAccessException e) {
                    Log.e(TAG, "Failed to set zoom ratio to the working camera.", e);
                }
            }
        });
    }

    /**
     * Returns current zoom ratio setting.
     */
    public float getZoomRatio() {
        return mZoomRatio;
    }

    /**
     * Returns true if High Quality Mode is enabled, false otherwise.
     */
    public boolean isHighQualityModeEnabled() {
        return mHighQualityModeEnabled;
    }

    /**
     * Toggles camera between the back and front cameras.
     *
     * The new camera is set up and configured asynchronously, but the camera state (as queried by
     * other methods in {@code CameraController}) is updated synchronously. So querying camera
     * state and metadata immediately after this method returns, returns values associated with the
     * new camera, even if the new camera hasn't started streaming.
     */
    public void toggleCamera() {
        synchronized (mSerializationLock) {
            CameraId newCameraId;

            if (Objects.equals(mCameraId, mBackCameraId)) {
                newCameraId = mFrontCameraId;
            } else {
                newCameraId = mBackCameraId;
            }

            switchCamera(newCameraId);
        }
    }

    /**
     * Switches current working camera to specific one.
     */
    public void switchCamera(CameraId cameraId) {
        synchronized (mSerializationLock) {
            mCameraId = cameraId;
            mUserPrefs.storeCameraId(cameraId.toString());
            mCameraInfo = getOrCreateCameraInfo(mCameraId);
            mZoomRatio = mUserPrefs.fetchZoomRatio(mCameraId.toString(), /*defaultZoom*/ 1.0f);
            mTapToFocusPoints = null;

            // Stores the preferred back or front camera options
            if (mCameraInfo.getLensFacing() == CameraCharacteristics.LENS_FACING_BACK) {
                mBackCameraId = mCameraId;
                mUserPrefs.storeBackCameraId(mBackCameraId.toString());
            } else if (mCameraInfo.getLensFacing() == CameraCharacteristics.LENS_FACING_FRONT) {
                mFrontCameraId = mCameraId;
                mUserPrefs.storeFrontCameraId(mFrontCameraId.toString());
            }
        }
        mServiceEventsExecutor.execute(() -> {
            synchronized (mSerializationLock) {
                if (mCameraDevice == null) {
                    // Its possible the preview screen is up before the camera device is opened.
                    return;
                }
                mCaptureSession.close();
                if (mCameraInfo != null) {
                    mRotationProvider.updateSensorOrientation(mCameraInfo.getSensorOrientation(),
                            mCameraInfo.getLensFacing());
                }
                switch (mCurrentState) {
                    case WEBCAM_STREAMING:
                        setupWebcamOnlyStreamAndOpenCameraLocked();
                        break;
                    case PREVIEW_STREAMING:
                        // Preview size might change after toggling the camera.
                        adjustPreviewOutputConfiguration();
                        setupPreviewOnlyStreamLocked(mPreviewSurface);
                        break;
                    case PREVIEW_AND_WEBCAM_STREAMING:
                        setupWebcamOnlyStreamAndOpenCameraLocked();
                        // Preview size might change after toggling the camera.
                        adjustPreviewOutputConfiguration();
                        setupPreviewStreamAlongsideWebcamStreamLocked(mPreviewSurface);
                        break;
                }
            }
        });
    }

    /**
     * Sets a {@link RotationUpdateListener} to monitor the rotation changes.
     */
    public void setRotationUpdateListener(RotationUpdateListener listener) {
        mRotationUpdateListener = listener;
    }

    /**
     * Returns current rotation degrees value.
     */
    public int getCurrentRotation() {
        return mRotationProvider.getRotation();
    }

    /**
     * Returns the best suitable output size for preview.
     *
     * <p>If the webcam stream doesn't exist, find the largest 16:9 supported output size which is
     * not larger than 1080p. If the webcam stream exists, find the largest supported output size
     * which matches the aspect ratio of the webcam stream size and is not larger than the
     * display size, 1080p, or the webcam stream resolution, whichever is smallest.
     */
    public Size getSuitablePreviewSize() {
        if (mCameraId == null) {
            Log.e(TAG, "No camera is found on the device.");
            return null;
        }

        final Size s1080p = new Size(1920, 1080);
        Size maxPreviewSize = s1080p;

        // For PREVIEW, choose the smallest of webcam stream size, display size, and 1080p. This
        // is guaranteed to be supported with a YUV stream.
        if (mImgReader != null) {
            maxPreviewSize = new Size(mImgReader.getWidth(), mImgReader.getHeight());
        }

        if (numPixels(maxPreviewSize) > numPixels(s1080p)) {
            maxPreviewSize = s1080p;
        }

        if (numPixels(maxPreviewSize) > numPixels(mDisplaySize)) {
            maxPreviewSize = mDisplaySize;
        }

        // If webcam stream exists, find an output size matching its aspect ratio. Otherwise, find
        // an output size with 16:9 aspect ratio.
        final Rational targetAspectRatio;
        if (mImgReader != null) {
            targetAspectRatio = new Rational(mImgReader.getWidth(), mImgReader.getHeight());
        } else {
            targetAspectRatio = new Rational(s1080p.getWidth(), s1080p.getHeight());
        }

        StreamConfigurationMap map = getCameraCharacteristic(mCameraId.mainCameraId,
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        if (map == null) {
            Log.e(TAG, "Failed to retrieve StreamConfigurationMap. Return null preview size.");
            return null;
        }

        Size[] outputSizes = map.getOutputSizes(SurfaceTexture.class);

        if (outputSizes == null || outputSizes.length == 0) {
            Log.e(TAG, "Empty output sizes. Return null preview size.");
            return null;
        }

        Size finalMaxPreviewSize = maxPreviewSize;
        Size previewSize = Arrays.stream(outputSizes)
                .filter(size -> targetAspectRatio.equals(
                        new Rational(size.getWidth(), size.getHeight())))
                .filter(size -> numPixels(size) <= numPixels(finalMaxPreviewSize))
                .max(Comparator.comparingInt(CameraController::numPixels))
                .orElse(null);

        Log.d(TAG, "Suitable preview size is " + previewSize);
        return previewSize;
    }

    private static int numPixels(Size size) {
        return size.getWidth() * size.getHeight();
    }

    /**
     * Trigger tap-to-focus operation for the specified normalized points mapping to the FOV.
     *
     * <p>The specified normalized points will be used to calculate the corresponding metering
     * rectangles that will be applied for AF, AE and AWB.
     */
    public void tapToFocus(float[] normalizedPoint) {
        mServiceEventsExecutor.execute(() -> {
            synchronized (mSerializationLock) {
                if (mCameraDevice == null || mCaptureSession == null) {
                    return;
                }

                try {
                    mTapToFocusPoints = normalizedPoint;
                    MeteringRectangle[] meteringRectangles =
                            new MeteringRectangle[]{calculateMeteringRectangle(normalizedPoint)};
                    // Updates the metering rectangles to the repeating request
                    updateTapToFocusParameters(mPreviewRequestBuilder, meteringRectangles,
                            /* afTriggerStart */ false);
                    mCaptureSession.setSingleRepeatingRequest(mPreviewRequestBuilder.build(),
                            mCameraCallbacksExecutor, mCaptureCallback);

                    // Creates a capture request to trigger AF start for the metering rectangles.
                    CaptureRequest.Builder builder = mCameraDevice.createCaptureRequest(
                            CameraDevice.TEMPLATE_PREVIEW);
                    CaptureRequest previewCaptureRequest = mPreviewRequestBuilder.build();

                    for (CaptureRequest.Key<?> key : previewCaptureRequest.getKeys()) {
                        builder.set((CaptureRequest.Key) key, previewCaptureRequest.get(key));
                    }

                    if (mImgReader != null && previewCaptureRequest.containsTarget(
                            mImgReader.getSurface())) {
                        builder.addTarget(mImgReader.getSurface());
                    }

                    if (mPreviewSurface != null && previewCaptureRequest.containsTarget(
                            mPreviewSurface)) {
                        builder.addTarget(mPreviewSurface);
                    }

                    updateTapToFocusParameters(builder, meteringRectangles,
                            /* afTriggerStart */ true);

                    mCaptureSession.captureSingleRequest(builder.build(),
                            mCameraCallbacksExecutor, mCaptureCallback);
                } catch (CameraAccessException e) {
                    Log.e(TAG, "Failed to execute tap-to-focus to the working camera.", e);
                }
            }
        });
    }

    /**
     * Enables or disables HighQuality mode. This will likely perform slow operations to commit the
     * changes. {@code callback} will be called once the changes have been committed.
     *
     * Note that there is no guarantee that {@code callback} will be called on the UI thread
     * and {@code callback} should not block the calling thread.
     *
     * @param enabled true if HighQualityMode should be enabled, false otherwise
     * @param callback Callback to be called once the session has been reconfigured.
     */
    public void setHighQualityModeEnabled(boolean enabled, Runnable callback) {
        synchronized (mSerializationLock) {
            if (enabled == mHighQualityModeEnabled) {
                callback.run();
                return;
            }

            mHighQualityModeEnabled = enabled;
            mUserPrefs.storeHighQualityModeEnabled(mHighQualityModeEnabled);
        }
        mServiceEventsExecutor.execute(() -> {
            synchronized (mSerializationLock) {
                int currentCameraFacing = getCameraInfo().getLensFacing();
                mRroCameraInfo = createVendorCameraPrefs(mHighQualityModeEnabled);
                refreshAvailableCameraIdList();
                refreshLensFacingCameraIds();

                // Choose a camera that faces the same way as the current camera.
                CameraId targetCameraId = mBackCameraId;
                if (currentCameraFacing == CameraCharacteristics.LENS_FACING_FRONT) {
                    targetCameraId = mFrontCameraId;
                }

                switchCamera(targetCameraId);
                // Let the caller know that the changes have been committed.
                callback.run();
            }
        });
    }

    /**
     * Resets to the auto-focus mode.
     */
    public void resetToAutoFocus() {
        mServiceEventsExecutor.execute(() -> {
            synchronized (mSerializationLock) {
                if (mCameraDevice == null || mCaptureSession == null) {
                    return;
                }
                mTapToFocusPoints = null;

                // Resets to CONTINUOUS_VIDEO mode
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
                // Clears the Af/Ae/Awb regions
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, null);
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, null);
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_REGIONS, null);

                try {
                    mCaptureSession.setSingleRepeatingRequest(mPreviewRequestBuilder.build(),
                            mCameraCallbacksExecutor, mCaptureCallback);
                } catch (CameraAccessException e) {
                    Log.e(TAG, "Failed to reset to auto-focus mode to the working camera.", e);
                }
            }
        });
    }

    /**
     * Retrieves current tap-to-focus points.
     *
     * @return the normalized points or {@code null} if it is auto-focus mode currently.
     */
    public float[] getTapToFocusPoints() {
        synchronized (mSerializationLock) {
            return mTapToFocusPoints == null ? null
                    : new float[]{mTapToFocusPoints[0], mTapToFocusPoints[1]};
        }
    }

    /**
     * Calculates the metering rectangle according to the normalized point.
     */
    private MeteringRectangle calculateMeteringRectangle(float[] normalizedPoint) {
        CameraInfo cameraInfo = getCameraInfo();
        Rect activeArraySize = cameraInfo.getActiveArraySize();
        float halfMeteringRectWidth = (METERING_RECTANGLE_SIZE_RATIO * activeArraySize.width()) / 2;
        float halfMeteringRectHeight =
                (METERING_RECTANGLE_SIZE_RATIO * activeArraySize.height()) / 2;

        Matrix matrix = new Matrix();
        matrix.postRotate(-cameraInfo.getSensorOrientation(), 0.5f, 0.5f);
        // Flips if current working camera is front camera
        if (cameraInfo.getLensFacing() == CameraCharacteristics.LENS_FACING_FRONT) {
            matrix.postScale(1, -1, 0.5f, 0.5f);
        }
        matrix.postScale(activeArraySize.width(), activeArraySize.height());
        float[] mappingPoints = new float[]{normalizedPoint[0], normalizedPoint[1]};
        matrix.mapPoints(mappingPoints);

        Rect meteringRegion = new Rect(
                clamp((int) (mappingPoints[0] - halfMeteringRectWidth), 0,
                        activeArraySize.width()),
                clamp((int) (mappingPoints[1] - halfMeteringRectHeight), 0,
                        activeArraySize.height()),
                clamp((int) (mappingPoints[0] + halfMeteringRectWidth), 0,
                        activeArraySize.width()),
                clamp((int) (mappingPoints[1] + halfMeteringRectHeight), 0,
                        activeArraySize.height())
        );

        return new MeteringRectangle(meteringRegion, MeteringRectangle.METERING_WEIGHT_MAX);
    }

    private int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    /**
     * Updates tap-to-focus parameters to the capture request builder.
     *
     * @param builder            the capture request builder to apply the parameters
     * @param meteringRectangles the metering rectangles to apply to the capture request builder
     * @param afTriggerStart     sets CONTROL_AF_TRIGGER as CONTROL_AF_TRIGGER_START if this
     *                           parameter is {@code true}. Otherwise, sets nothing to
     *                           CONTROL_AF_TRIGGER.
     */
    private void updateTapToFocusParameters(CaptureRequest.Builder builder,
            MeteringRectangle[] meteringRectangles, boolean afTriggerStart) {
        builder.set(CaptureRequest.CONTROL_AF_REGIONS, meteringRectangles);
        builder.set(CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_AUTO);
        builder.set(CaptureRequest.CONTROL_AE_REGIONS, meteringRectangles);
        builder.set(CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON);
        builder.set(CaptureRequest.CONTROL_AWB_REGIONS, meteringRectangles);

        if (afTriggerStart) {
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_START);
        }
    }

    private static class ImageAndBuffer {
        public Image image;
        public HardwareBuffer buffer;
        public ImageAndBuffer(Image i, HardwareBuffer b) {
            image = i;
            buffer = b;
        }
    }

    private VendorCameraPrefs createVendorCameraPrefs(boolean highQualityMode) {
        return highQualityMode ?
                VendorCameraPrefs.createEmptyVendorCameraPrefs(mContext) :
                VendorCameraPrefs.getVendorCameraPrefsFromJson(mContext);
    }

    /**
     * An interface to monitor the rotation changes.
     */
    interface RotationUpdateListener {
        /**
         * Called when the physical rotation of the device changes to cause the corresponding
         * rotation degrees value is changed.
         *
         * @param rotation the updated rotation degrees value.
         */
        void onRotationUpdated(int rotation);
    }
}
