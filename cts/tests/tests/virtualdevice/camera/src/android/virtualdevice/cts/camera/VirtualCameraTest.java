/*
 * Copyright 2023 The Android Open Source Project
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

package android.virtualdevice.cts.camera;

import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_CUSTOM;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_CAMERA;
import static android.companion.virtual.camera.VirtualCameraConfig.SENSOR_ORIENTATION_0;
import static android.companion.virtual.camera.VirtualCameraConfig.SENSOR_ORIENTATION_180;
import static android.companion.virtual.camera.VirtualCameraConfig.SENSOR_ORIENTATION_270;
import static android.companion.virtual.camera.VirtualCameraConfig.SENSOR_ORIENTATION_90;
import static android.graphics.ImageFormat.JPEG;
import static android.graphics.ImageFormat.RGB_565;
import static android.graphics.ImageFormat.YUV_420_888;
import static android.hardware.camera2.CameraMetadata.LENS_FACING_BACK;
import static android.hardware.camera2.CameraMetadata.LENS_FACING_FRONT;
import static android.opengl.EGL14.EGL_NO_DISPLAY;
import static android.opengl.EGL14.EGL_NO_SURFACE;
import static android.opengl.EGL14.eglCreateContext;
import static android.opengl.EGL14.eglGetDisplay;
import static android.opengl.EGL14.eglInitialize;
import static android.opengl.EGL14.eglMakeCurrent;
import static android.opengl.EGL14.eglTerminate;
import static android.opengl.GLES20.GL_EXTENSIONS;
import static android.opengl.GLES20.glGetString;
import static android.virtualdevice.cts.camera.VirtualCameraUtils.assertVirtualCameraConfig;
import static android.virtualdevice.cts.camera.VirtualCameraUtils.createVirtualCameraConfig;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import static java.lang.Byte.toUnsignedInt;

import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.camera.VirtualCamera;
import android.companion.virtual.camera.VirtualCameraCallback;
import android.companion.virtual.camera.VirtualCameraConfig;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.util.Size;
import android.view.Surface;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import com.android.compatibility.common.util.FeatureUtil;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

// TODO(b/310857520): Update tests to incorporate device awareness in camera framework.
@RequiresFlagsEnabled({android.companion.virtual.flags.Flags.FLAG_VIRTUAL_CAMERA,
        android.companion.virtualdevice.flags.Flags.FLAG_VIRTUAL_CAMERA_SERVICE_DISCOVERY})
@RunWith(JUnitParamsRunner.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class VirtualCameraTest {
    private static final long TIMEOUT_MILLIS = 2000L;
    private static final float EPSILON = 0.3f;
    private static final String CAMERA_NAME = "Virtual camera";
    private static final int CAMERA_WIDTH = 640;
    private static final int CAMERA_HEIGHT = 480;
    private static final int CAMERA_FORMAT = YUV_420_888;
    private static final int CAMERA_MAX_FPS = 30;
    private static final int CAMERA_SENSOR_ORIENTATION = SENSOR_ORIENTATION_0;
    private static final int CAMERA_LENS_FACING = LENS_FACING_FRONT;
    private static final int IMAGE_READER_MAX_IMAGES = 2;
    private static final String GL_EXT_YUV_target = "GL_EXT_YUV_target";

    private static final boolean hasGlExtYuvTarget = hasEGLExtension(GL_EXT_YUV_target);

    @Rule
    public VirtualDeviceRule mRule = VirtualDeviceRule.createDefault();

    @Mock
    private CameraManager.AvailabilityCallback mMockCameraAvailabilityCallback;

    @Mock
    private VirtualCameraCallback mVirtualCameraCallback;

    @Mock
    private CameraDevice.StateCallback mCameraStateCallback;

    @Mock
    private CameraCaptureSession.StateCallback mSessionStateCallback;

    @Mock
    private CameraCaptureSession.CaptureCallback mCaptureCallback;

    @Captor
    private ArgumentCaptor<CameraDevice> mCameraDeviceCaptor;

    @Captor
    private ArgumentCaptor<CameraCaptureSession> mCameraCaptureSessionCaptor;

    @Captor
    private ArgumentCaptor<Surface> mSurfaceCaptor;

    @Captor
    private ArgumentCaptor<Integer> mWidthCaptor;

    @Captor
    private ArgumentCaptor<Integer> mHeightCaptor;

    @Captor
    private ArgumentCaptor<Integer> mFormatCaptor;

    private CameraManager mCameraManager;
    private VirtualDevice mVirtualDevice;
    private VirtualCamera mVirtualCamera;
    private final Executor mExecutor = getApplicationContext().getMainExecutor();

    @Before
    public void setUp() {
        // Virtual Camera Service is not available in Auto build.
        assumeFalse(FeatureUtil.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE));
        // Virtual Camera currently requires GL_EXT_YUV_target extension to process input YUV
        // buffers and perform RGB -> YUV conversion.
        // TODO(b/316108033) Remove once there's workaround for systems without GL_EXT_YUV_target
        // extension.
        assumeTrue(hasGlExtYuvTarget);

        MockitoAnnotations.initMocks(this);
        Context context = getApplicationContext();
        mCameraManager = context.getSystemService(CameraManager.class);
        assertThat(mCameraManager).isNotNull();
        mCameraManager.registerAvailabilityCallback(mExecutor, mMockCameraAvailabilityCallback);
        mVirtualDevice = mRule.createManagedVirtualDevice(
                new VirtualDeviceParams.Builder()
                        .setDevicePolicy(POLICY_TYPE_CAMERA, DEVICE_POLICY_CUSTOM)
                        .build());
        VirtualCameraConfig config = createVirtualCameraConfig(CAMERA_WIDTH, CAMERA_HEIGHT,
                CAMERA_FORMAT, CAMERA_MAX_FPS, CAMERA_SENSOR_ORIENTATION, CAMERA_LENS_FACING,
                CAMERA_NAME, mExecutor, mVirtualCameraCallback);
        mVirtualCamera = mVirtualDevice.createVirtualCamera(config);
    }

    @After
    public void tearDown() {
        if (mCameraManager != null) {
            mCameraManager.unregisterAvailabilityCallback(mMockCameraAvailabilityCallback);
        }
    }

    @Test
    public void virtualCamera_getConfig_returnsCorrectConfig() {
        VirtualCameraConfig config = mVirtualCamera.getConfig();
        assertVirtualCameraConfig(config, CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_FORMAT,
                CAMERA_MAX_FPS, CAMERA_SENSOR_ORIENTATION, CAMERA_LENS_FACING, CAMERA_NAME);
    }

    @Test
    public void virtualCamera_triggersCameraAvailabilityCallbacks() {
        String virtualCameraId = mVirtualCamera.getId();
        verify(mMockCameraAvailabilityCallback, timeout(TIMEOUT_MILLIS))
                .onCameraAvailable(virtualCameraId);

        mVirtualCamera.close();
        verify(mMockCameraAvailabilityCallback, timeout(TIMEOUT_MILLIS))
                .onCameraUnavailable(virtualCameraId);
    }

    @Test
    public void virtualCamera_virtualDeviceCloseRemovesCamera() throws Exception {
        mVirtualDevice.close();

        verify(mMockCameraAvailabilityCallback, timeout(TIMEOUT_MILLIS))
                .onCameraUnavailable(mVirtualCamera.getId());
        assertThat(Arrays.stream(mCameraManager.getCameraIdListNoLazy()).toList())
                .doesNotContain(mVirtualCamera.getId());
    }

    @Test
    public void virtualCamera_presentInListOfCameras() throws Exception {
        assertThat(Arrays.stream(mCameraManager.getCameraIdListNoLazy()).toList())
                .contains(mVirtualCamera.getId());
    }

    @Test
    public void virtualCamera_close_notPresentInListOfCameras() throws Exception {
        mVirtualCamera.close();

        assertThat(Arrays.stream(mCameraManager.getCameraIdListNoLazy()).toList())
                .doesNotContain(mVirtualCamera.getId());
    }

    @Parameters(method = "getAllSensorOrientations")
    @Test
    public void virtualCamera_hasCorrectOrientation(int sensorOrientation) throws Exception {
        mVirtualCamera.close();
        VirtualCameraConfig config = createVirtualCameraConfig(CAMERA_WIDTH, CAMERA_HEIGHT,
                CAMERA_FORMAT, CAMERA_MAX_FPS, sensorOrientation, CAMERA_LENS_FACING, CAMERA_NAME,
                mExecutor, mVirtualCameraCallback);
        mVirtualCamera = mVirtualDevice.createVirtualCamera(config);

        CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(
                mVirtualCamera.getId(), /* overrideToPortrait= */ false);
        int orientationAngleDegrees = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        assertThat(orientationAngleDegrees).isEqualTo(sensorOrientation);
    }

    @Test
    public void virtualCamera_hasCorrectMinFrameDuration() throws Exception {
        long expectedMinFrameDuration = TimeUnit.SECONDS.toNanos(1) / CAMERA_MAX_FPS;
        CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(
                mVirtualCamera.getId());
        StreamConfigurationMap streamConfigurationMap =
                characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        int[] outputFormats = streamConfigurationMap.getOutputFormats();
        for (int format : outputFormats) {
            Size[] sizes = streamConfigurationMap.getOutputSizes(format);
            for (Size size : sizes) {
                long minFrameDuration =
                        streamConfigurationMap.getOutputMinFrameDuration(format, size);
                assertThat(minFrameDuration).isEqualTo(expectedMinFrameDuration);
            }
        }
    }

    @Parameters(method = "getAllLensFacingDirections")
    @Test
    public void virtualCamera_hasCorrectLensFacing(int lensFacing) throws Exception {
        // Close the existing camera first.
        mVirtualCamera.close();

        VirtualCameraConfig config = createVirtualCameraConfig(CAMERA_WIDTH, CAMERA_HEIGHT,
                CAMERA_FORMAT, CAMERA_MAX_FPS, CAMERA_SENSOR_ORIENTATION, lensFacing, CAMERA_NAME,
                mExecutor, mVirtualCameraCallback);
        mVirtualCamera = mVirtualDevice.createVirtualCamera(config);

        CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(
                mVirtualCamera.getId());
        int cameraLensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
        assertThat(cameraLensFacing).isEqualTo(lensFacing);
    }

    @Parameters(method = "getAllLensFacingDirections")
    @Test
    public void createMultipleVirtualCameras_withSameLensFacing_fails(int lensFacing) {
        // Close the existing camera first.
        mVirtualCamera.close();

        VirtualCameraConfig config = createVirtualCameraConfig(CAMERA_WIDTH, CAMERA_HEIGHT,
                CAMERA_FORMAT, CAMERA_MAX_FPS, CAMERA_SENSOR_ORIENTATION, lensFacing, CAMERA_NAME,
                mExecutor, mVirtualCameraCallback);
        mVirtualCamera = mVirtualDevice.createVirtualCamera(config);

        // Creating another camera with same lens facing should fail.
        assertThrows(IllegalArgumentException.class,
                () -> mVirtualDevice.createVirtualCamera(config));
    }

    @Parameters(method = "getAllLensFacingDirections")
    @Test
    public void createVirtualCamera_withDefaultPolicy_fails(int lensFacing) {
        // Create virtual device with default camera policy.
        mVirtualDevice = mRule.createManagedVirtualDevice();

        VirtualCameraConfig config = createVirtualCameraConfig(CAMERA_WIDTH, CAMERA_HEIGHT,
                CAMERA_FORMAT, CAMERA_MAX_FPS, CAMERA_SENSOR_ORIENTATION, lensFacing, CAMERA_NAME,
                mExecutor, mVirtualCameraCallback);
        assertThrows(IllegalArgumentException.class,
                () -> mVirtualDevice.createVirtualCamera(config));
    }

    @Test
    public void virtualCamera_openCamera_triggersOnOpenedCallback() throws Exception {
        mCameraManager.openCamera(mVirtualCamera.getId(), directExecutor(), mCameraStateCallback);

        verify(mCameraStateCallback, timeout(TIMEOUT_MILLIS)).onOpened(
                mCameraDeviceCaptor.capture());
        assertThat(mCameraDeviceCaptor.getValue().getId()).isEqualTo(mVirtualCamera.getId());
    }

    @Test
    public void virtualCamera_close_triggersOnDisconnectedCallback() throws Exception {
        mCameraManager.openCamera(mVirtualCamera.getId(), directExecutor(), mCameraStateCallback);
        mVirtualCamera.close();

        verify(mCameraStateCallback, timeout(TIMEOUT_MILLIS))
                .onDisconnected(mCameraDeviceCaptor.capture());
        assertThat(mCameraDeviceCaptor.getValue().getId()).isEqualTo(mVirtualCamera.getId());
    }

    @Test
    public void virtualCamera_cameraDeviceClose_triggersOnClosedCallback() throws Exception {
        mCameraManager.openCamera(mVirtualCamera.getId(), directExecutor(), mCameraStateCallback);
        verify(mCameraStateCallback, timeout(TIMEOUT_MILLIS)).onOpened(
                mCameraDeviceCaptor.capture());

        mCameraDeviceCaptor.getValue().close();

        verify(mCameraStateCallback, timeout(TIMEOUT_MILLIS)).onClosed(
                mCameraDeviceCaptor.capture());
        assertThat(mCameraDeviceCaptor.getValue().getId()).isEqualTo(mVirtualCamera.getId());
    }

    @Test
    public void virtualCamera_configureSessionForSupportedFormat_succeeds() throws Exception {
        mCameraManager.openCamera(mVirtualCamera.getId(), mExecutor, mCameraStateCallback);
        verify(mCameraStateCallback, timeout(TIMEOUT_MILLIS)).onOpened(
                mCameraDeviceCaptor.capture());

        CameraDevice cameraDevice = mCameraDeviceCaptor.getValue();

        try (ImageReader reader = createImageReader(YUV_420_888)) {
            cameraDevice.createCaptureSession(createSessionConfig(reader));

            verify(mVirtualCameraCallback, timeout(TIMEOUT_MILLIS)).onStreamConfigured(anyInt(),
                    mSurfaceCaptor.capture(), mWidthCaptor.capture(), mHeightCaptor.capture(),
                    mFormatCaptor.capture());
            assertThat(mSurfaceCaptor.getValue().isValid()).isTrue();
            assertThat(mWidthCaptor.getValue()).isEqualTo(CAMERA_WIDTH);
            assertThat(mHeightCaptor.getValue()).isEqualTo(CAMERA_HEIGHT);
            assertThat(mFormatCaptor.getValue()).isEqualTo(YUV_420_888);

            verify(mSessionStateCallback, timeout(TIMEOUT_MILLIS)).onConfigured(
                    mCameraCaptureSessionCaptor.capture());
            CameraCaptureSession cameraCaptureSession = mCameraCaptureSessionCaptor.getValue();

            cameraCaptureSession.close();
        }
        cameraDevice.close();

        verify(mVirtualCameraCallback, timeout(TIMEOUT_MILLIS)).onStreamClosed(anyInt());
    }

    @Test
    public void virtualCamera_configureSessionForUnsupportedFormat_fails() throws Exception {
        mCameraManager.openCamera(mVirtualCamera.getId(), mExecutor, mCameraStateCallback);
        verify(mCameraStateCallback, timeout(TIMEOUT_MILLIS)).onOpened(
                mCameraDeviceCaptor.capture());

        CameraDevice cameraDevice = mCameraDeviceCaptor.getValue();

        try (ImageReader reader = createImageReader(RGB_565)) {
            cameraDevice.createCaptureSession(createSessionConfig(reader));

            verify(mSessionStateCallback, timeout(TIMEOUT_MILLIS)).onConfigureFailed(any());
        }
    }

    @Test
    public void virtualCamera_captureYuv420_succeeds() throws Exception {
        try (ImageReader imageReader = createImageReader(YUV_420_888)) {
            Image image = captureImage(imageReader, VirtualCameraTest::paintInputSurfaceRed);

            assertThat(image.getFormat()).isEqualTo(YUV_420_888);
            assertThat(image.getWidth()).isEqualTo(CAMERA_WIDTH);
            assertThat(image.getHeight()).isEqualTo(CAMERA_HEIGHT);
            assertThat(imageHasColor(image, Color.RED)).isTrue();
        }
    }

    @Test
    public void virtualCamera_captureDownscaledYuv420_succeeds() throws Exception {
        try (ImageReader imageReader =
                     createImageReader(YUV_420_888, CAMERA_WIDTH / 2, CAMERA_HEIGHT / 2)) {
            Image image = captureImage(imageReader, VirtualCameraTest::paintInputSurfaceRed);

            assertThat(image.getFormat()).isEqualTo(YUV_420_888);
            assertThat(image.getWidth()).isEqualTo(CAMERA_WIDTH / 2);
            assertThat(image.getHeight()).isEqualTo(CAMERA_HEIGHT / 2);
            assertThat(imageHasColor(image, Color.RED)).isTrue();
        }
    }

    @Test
    public void virtualCamera_captureYuv420WithNoInput_capturesBlackImage() throws Exception {
        try (ImageReader imageReader = createImageReader(YUV_420_888)) {
            Image image = captureImage(imageReader);

            assertThat(image.getFormat()).isEqualTo(YUV_420_888);
            assertThat(image.getWidth()).isEqualTo(CAMERA_WIDTH);
            assertThat(image.getHeight()).isEqualTo(CAMERA_HEIGHT);
            assertThat(imageHasColor(image, Color.BLACK)).isTrue();
        }
    }

    @Test
    public void virtualCamera_captureJpeg_succeeds() throws Exception {
        try (ImageReader imageReader = createImageReader(JPEG)) {
            Image image = captureImage(imageReader, VirtualCameraTest::paintInputSurfaceRed);

            assertThat(image.getFormat()).isEqualTo(JPEG);
            assertThat(image.getWidth()).isEqualTo(CAMERA_WIDTH);
            assertThat(image.getHeight()).isEqualTo(CAMERA_HEIGHT);
            assertThat(imageHasColor(image, Color.RED)).isTrue();
        }
    }

    @Test
    public void virtualCamera_captureDownscaledJpeg_succeeds() throws Exception {
        try (ImageReader imageReader =
                     createImageReader(JPEG, CAMERA_WIDTH / 2, CAMERA_HEIGHT / 2)) {
            Image image = captureImage(imageReader, VirtualCameraTest::paintInputSurfaceRed);

            assertThat(image.getFormat()).isEqualTo(JPEG);
            assertThat(image.getWidth()).isEqualTo(CAMERA_WIDTH / 2);
            assertThat(image.getHeight()).isEqualTo(CAMERA_HEIGHT / 2);
            assertThat(imageHasColor(image, Color.RED)).isTrue();
        }
    }

    @Test
    public void virtualCamera_captureJpegWithNoInput_capturesBlackImage() throws Exception {
        try (ImageReader imageReader = createImageReader(JPEG)) {
            Image image = captureImage(imageReader);

            assertThat(image.getFormat()).isEqualTo(JPEG);
            assertThat(image.getWidth()).isEqualTo(CAMERA_WIDTH);
            assertThat(image.getHeight()).isEqualTo(CAMERA_HEIGHT);
            assertThat(imageHasColor(image, Color.BLACK)).isTrue();
        }
    }

    private Image captureImage(ImageReader reader, Consumer<Surface> inputSurfaceConsumer)
            throws CameraAccessException {
        mCameraManager.openCamera(mVirtualCamera.getId(), mExecutor, mCameraStateCallback);
        verify(mCameraStateCallback, timeout(TIMEOUT_MILLIS)).onOpened(
                mCameraDeviceCaptor.capture());

        try (CameraDevice cameraDevice = mCameraDeviceCaptor.getValue()) {
            cameraDevice.createCaptureSession(createSessionConfig(reader));

            verify(mSessionStateCallback, timeout(TIMEOUT_MILLIS)).onConfigured(
                    mCameraCaptureSessionCaptor.capture());
            verify(mVirtualCameraCallback, timeout(TIMEOUT_MILLIS)).onStreamConfigured(anyInt(),
                    mSurfaceCaptor.capture(), mWidthCaptor.capture(), mHeightCaptor.capture(),
                    mFormatCaptor.capture());

            Surface inputSurface = mSurfaceCaptor.getValue();
            assertThat(inputSurface.isValid()).isTrue();
            inputSurfaceConsumer.accept(inputSurface);

            try (CameraCaptureSession cameraCaptureSession =
                         mCameraCaptureSessionCaptor.getValue()) {
                CaptureRequest.Builder request = cameraDevice.createCaptureRequest(
                        CameraDevice.TEMPLATE_PREVIEW);
                request.addTarget(reader.getSurface());
                cameraCaptureSession.captureSingleRequest(request.build(), mExecutor,
                        mCaptureCallback);

                verify(mVirtualCameraCallback, timeout(TIMEOUT_MILLIS))
                        .onProcessCaptureRequest(anyInt(), anyLong());
                verify(mCaptureCallback, timeout(TIMEOUT_MILLIS)).onCaptureCompleted(any(),
                        any(),
                        any());
                Image image = reader.acquireLatestImage();
                assertThat(image).isNotNull();
                return image;
            }
        }
    }

    private Image captureImage(ImageReader reader) throws CameraAccessException {
        return captureImage(reader, (Surface surface) -> {});
    }

    private SessionConfiguration createSessionConfig(ImageReader reader) {
        OutputConfiguration outputConfiguration = new OutputConfiguration(reader.getSurface());
        return new SessionConfiguration(SessionConfiguration.SESSION_REGULAR,
                List.of(outputConfiguration), mExecutor, mSessionStateCallback);
    }

    private static void paintInputSurfaceRed(Surface surface) {
        Canvas canvas = surface.lockCanvas(null);
        canvas.drawColor(Color.RED);
        surface.unlockCanvasAndPost(canvas);
    }

    private static ImageReader createImageReader(@ImageFormat.Format int pixelFormat) {
        return ImageReader.newInstance(CAMERA_WIDTH, CAMERA_HEIGHT,
                pixelFormat, IMAGE_READER_MAX_IMAGES);
    }

    private static ImageReader createImageReader(
            @ImageFormat.Format int pixelFormat, int width, int height) {
        return ImageReader.newInstance(width, height,
                pixelFormat, IMAGE_READER_MAX_IMAGES);
    }

    // Converts YUV to ARGB int representation,
    // using BT601 full-range matrix.
    // See https://en.wikipedia.org/wiki/YCbCr#JPEG_conversion
    private static int yuv2rgb(int y, int u, int v) {
        int r = (int) (y + 1.402f * (v - 128f));
        int g = (int) (y - 0.344136f * (u - 128f) - 0.714136 * (v - 128f));
        int b = (int) (y + 1.772 * (u - 128f));
        return 0xff000000 | (r << 16) | (g << 8) | b;
    }

    // Compares two ARGB colors and returns true if they are approximately
    // the same color.
    private static boolean areColorsAlmostIdentical(int colorA, int colorB) {
        float a1 = ((colorA >> 24) & 0xff) / 255f;
        float r1 = ((colorA >> 16) & 0xff) / 255f;
        float g1 = ((colorA >> 4) & 0xff) / 255f;
        float b1 = (colorA & 0xff) / 255f;

        float a2 = ((colorB >> 24) & 0xff) / 255f;
        float r2 = ((colorB >> 16) & 0xff) / 255f;
        float g2 = ((colorB >> 4) & 0xff) / 255f;
        float b2 = (colorB & 0xff) / 255f;

        float mse = ((a1 - a2) * (a1 - a2)
                + (r1 - r2) * (r1 - r2)
                + (g1 - g2) * (g1 - g2)
                + (b1 - b2) * (b1 - b2)) / 4;

        return mse < EPSILON;
    }

    private static boolean yuv420ImageHasColor(Image image, int color) {
        final int width = image.getWidth();
        final int height = image.getHeight();
        final Image.Plane[] planes = image.getPlanes();
        for (int j = 0; j < height; ++j) {
            int jChroma = j / 2;
            for (int i = 0; i < width; ++i) {
                int iChroma = i / 2;
                int y = toUnsignedInt(planes[0].getBuffer().get(
                        j * planes[0].getRowStride() + i * planes[0].getPixelStride()));
                int u = toUnsignedInt(planes[1].getBuffer().get(
                        jChroma * planes[1].getRowStride() + iChroma * planes[1].getPixelStride()));
                int v = toUnsignedInt(planes[2].getBuffer().get(
                        jChroma * planes[2].getRowStride() + iChroma * planes[2].getPixelStride()));
                int argb = yuv2rgb(y, u, v);
                if (!areColorsAlmostIdentical(argb, color)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean jpegImageHasColor(Image image, int color) throws IOException {
        Bitmap bitmap = ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(image.getPlanes()[0].getBuffer())).copy(
                Bitmap.Config.ARGB_8888, false);
        final int width = bitmap.getWidth();
        final int height = bitmap.getHeight();
        for (int j = 0; j < height; ++j) {
            for (int i = 0; i < width; ++i) {
                if (!areColorsAlmostIdentical(bitmap.getColor(i, j).toArgb(), color)) {
                    return false;
                }
            }
        }
        return true;
    }

    // TODO(b/316326725) Turn this into proper custom matcher.
    private static boolean imageHasColor(Image image, int color) throws IOException {
        return switch (image.getFormat()) {
            case YUV_420_888 -> yuv420ImageHasColor(image, color);
            case JPEG -> jpegImageHasColor(image, color);
            default -> {
                fail("Encountered unsupported image format: " + image.getFormat());
                yield false;
            }
        };
    }

    private static boolean hasEGLExtension(String extension) {
        EGLDisplay eglDisplay = eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        assumeFalse(eglDisplay.equals(EGL_NO_DISPLAY));
        int[] version = new int[2];
        eglInitialize(eglDisplay, version, 0, version, 1);

        int[] attribList = {EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_NONE};

        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!EGL14.eglChooseConfig(
                eglDisplay, attribList, 0, configs, 0, configs.length, numConfigs, 0)) {
            return false;
        }

        int[] attrib2_list = {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE};
        EGLContext eglContext = eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT,
                attrib2_list, 0);
        eglMakeCurrent(eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, eglContext);

        String extensions = glGetString(GL_EXTENSIONS);
        eglTerminate(eglDisplay);
        return extensions.contains(extension);
    }

    private static Integer[] getAllSensorOrientations() {
        return new Integer[] {
                SENSOR_ORIENTATION_0,
                SENSOR_ORIENTATION_90,
                SENSOR_ORIENTATION_180,
                SENSOR_ORIENTATION_270
        };
    }

    private static Integer[] getAllLensFacingDirections() {
        return new Integer[] {
                LENS_FACING_BACK,
                LENS_FACING_FRONT,
        };
    }
}
