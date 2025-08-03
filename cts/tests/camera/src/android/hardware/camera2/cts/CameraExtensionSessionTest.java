/*
 * Copyright 2020 The Android Open Source Project
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

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.android.compatibility.common.util.DeviceReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.compatibility.common.util.Stat;
import com.android.ex.camera2.blocking.BlockingSessionCallback;
import com.android.ex.camera2.blocking.BlockingExtensionSessionCallback;
import com.android.ex.camera2.blocking.BlockingStateCallback;
import com.android.ex.camera2.exceptions.TimeoutRuntimeException;
import com.android.ex.camera2.pos.AutoFocusStateMachine;

import android.graphics.ImageFormat;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.HardwareBuffer;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraExtensionCharacteristics;
import android.hardware.camera2.CameraExtensionSession;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.ExtensionCaptureRequest;
import android.hardware.camera2.ExtensionCaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.cts.helpers.CameraErrorCollector;
import android.hardware.camera2.cts.helpers.StaticMetadata;
import android.hardware.camera2.cts.testcases.Camera2AndroidTestRule;
import android.hardware.camera2.params.ExtensionSessionConfiguration;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.media.ExifInterface;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.SystemClock;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.util.Range;
import android.util.Size;

import static android.hardware.camera2.cts.CameraTestUtils.*;
import static android.hardware.cts.helpers.CameraUtils.*;

import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RunWith(Parameterized.class)
public class CameraExtensionSessionTest extends Camera2ParameterizedTestCase {
    private static final String TAG = "CameraExtensionSessionTest";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);
    private static final long WAIT_FOR_COMMAND_TO_COMPLETE_MS = 5000;
    private static final long REPEATING_REQUEST_TIMEOUT_MS = 5000;
    private static final int MULTI_FRAME_CAPTURE_IMAGE_TIMEOUT_MS = 10000;
    private static final float ZOOM_ERROR_MARGIN = 0.05f;

    private static final CaptureRequest.Key[] FOCUS_CAPTURE_REQUEST_SET = {
            CaptureRequest.CONTROL_AF_MODE,
            CaptureRequest.CONTROL_AF_REGIONS,
            CaptureRequest.CONTROL_AF_TRIGGER};
    private static final CaptureResult.Key[] FOCUS_CAPTURE_RESULT_SET = {
            CaptureResult.CONTROL_AF_MODE,
            CaptureResult.CONTROL_AF_REGIONS,
            CaptureResult.CONTROL_AF_TRIGGER,
            CaptureResult.CONTROL_AF_STATE};
    private static final CaptureRequest.Key[] ZOOM_CAPTURE_REQUEST_SET = {
            CaptureRequest.CONTROL_ZOOM_RATIO};
    private static final CaptureResult.Key[] ZOOM_CAPTURE_RESULT_SET = {
            CaptureResult.CONTROL_ZOOM_RATIO};
    private static final CaptureRequest.Key[] EYES_FREE_AUTO_ZOOM_REQUEST_SET = {
            ExtensionCaptureRequest.EFV_AUTO_ZOOM,
            ExtensionCaptureRequest.EFV_MAX_PADDING_ZOOM_FACTOR};
    private static final CaptureResult.Key[] EYES_FREE_AUTO_ZOOM_RESULT_SET = {
            ExtensionCaptureResult.EFV_AUTO_ZOOM,
            ExtensionCaptureResult.EFV_AUTO_ZOOM_PADDING_REGION};
    private static final CaptureRequest.Key[] EYES_FREE_REQUEST_SET = {
            ExtensionCaptureRequest.EFV_PADDING_ZOOM_FACTOR,
            ExtensionCaptureRequest.EFV_STABILIZATION_MODE,
            ExtensionCaptureRequest.EFV_TRANSLATE_VIEWPORT,
            ExtensionCaptureRequest.EFV_ROTATE_VIEWPORT};
    private static final CaptureResult.Key[] EYES_FREE_RESULT_SET = {
            ExtensionCaptureResult.EFV_PADDING_REGION,
            ExtensionCaptureResult.EFV_TARGET_COORDINATES,
            ExtensionCaptureResult.EFV_PADDING_ZOOM_FACTOR,
            ExtensionCaptureResult.EFV_STABILIZATION_MODE,
            ExtensionCaptureResult.EFV_ROTATE_VIEWPORT,
            ExtensionCaptureResult.EFV_TRANSLATE_VIEWPORT};

    private SurfaceTexture mSurfaceTexture = null;
    private Camera2AndroidTestRule mTestRule = null;
    private CameraErrorCollector mCollector =  null;

    private DeviceReportLog mReportLog;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mTestRule = new Camera2AndroidTestRule(mContext);
        mTestRule.before();
        mCollector = new CameraErrorCollector();
    }

    @Override
    public void tearDown() throws Exception {
        if (mTestRule != null) {
            mTestRule.after();
        }
        if (mSurfaceTexture != null) {
            mSurfaceTexture.release();
            mSurfaceTexture = null;
        }
        if (mCollector != null) {
            try {
                mCollector.verify();
            } catch (Throwable e) {
                throw new Exception(e.getMessage());
            }
        }
        super.tearDown();
    }

    @Rule
    public ActivityTestRule<CameraExtensionTestActivity> mActivityRule =
            new ActivityTestRule<>(CameraExtensionTestActivity.class);

    private void updatePreviewSurfaceTexture() {
        if (mSurfaceTexture != null) {
            return;
        }

        TextureView textureView = mActivityRule.getActivity().getTextureView();
        mSurfaceTexture = getAvailableSurfaceTexture(WAIT_FOR_COMMAND_TO_COMPLETE_MS, textureView);
        assertNotNull("Failed to acquire valid preview surface texture!", mSurfaceTexture);
    }

    // Verify that camera extension sessions can be created and closed as expected.
    @Test
    public void testBasicExtensionLifecycle() throws Exception {
        for (String id : getCameraIdsUnderTest()) {
            StaticMetadata staticMeta =
                    new StaticMetadata(mTestRule.getCameraManager().getCameraCharacteristics(id));
            if (!staticMeta.isColorOutputSupported()) {
                continue;
            }
            updatePreviewSurfaceTexture();
            CameraExtensionCharacteristics extensionChars =
                    mTestRule.getCameraManager().getCameraExtensionCharacteristics(id);
            List<Integer> supportedExtensions = extensionChars.getSupportedExtensions();
            for (Integer extension : supportedExtensions) {
                List<Size> extensionSizes = extensionChars.getExtensionSupportedSizes(extension,
                        mSurfaceTexture.getClass());
                Size maxSize = CameraTestUtils.getMaxSize(extensionSizes.toArray(new Size[0]));
                mSurfaceTexture.setDefaultBufferSize(maxSize.getWidth(), maxSize.getHeight());
                OutputConfiguration outputConfig = new OutputConfiguration(
                        new Surface(mSurfaceTexture));
                List<OutputConfiguration> outputConfigs = new ArrayList<>();
                outputConfigs.add(outputConfig);

                BlockingExtensionSessionCallback sessionListener =
                        new BlockingExtensionSessionCallback(
                                mock(CameraExtensionSession.StateCallback.class));
                ExtensionSessionConfiguration configuration =
                        new ExtensionSessionConfiguration(extension, outputConfigs,
                                new HandlerExecutor(mTestRule.getHandler()), sessionListener);

                try {
                    mTestRule.openDevice(id);
                    CameraDevice camera = mTestRule.getCamera();
                    camera.createExtensionSession(configuration);
                    CameraExtensionSession extensionSession = sessionListener.waitAndGetSession(
                            SESSION_CONFIGURE_TIMEOUT_MS);

                    extensionSession.close();
                    sessionListener.getStateWaiter().waitForState(
                            BlockingExtensionSessionCallback.SESSION_CLOSED,
                            SESSION_CLOSE_TIMEOUT_MS);
                } finally {
                    mTestRule.closeDevice(id);
                }
            }
        }
    }

    // Verify that regular camera sessions close as expected after creating a camera extension
    // session.
    @Test
    public void testCloseCaptureSession() throws Exception {
        for (String id : getCameraIdsUnderTest()) {
            StaticMetadata staticMeta =
                    new StaticMetadata(mTestRule.getCameraManager().getCameraCharacteristics(id));
            if (!staticMeta.isColorOutputSupported()) {
                continue;
            }
            updatePreviewSurfaceTexture();
            CameraExtensionCharacteristics extensionChars =
                    mTestRule.getCameraManager().getCameraExtensionCharacteristics(id);
            List<Integer> supportedExtensions = extensionChars.getSupportedExtensions();
            for (Integer extension : supportedExtensions) {
                List<Size> extensionSizes = extensionChars.getExtensionSupportedSizes(extension,
                        mSurfaceTexture.getClass());
                Size maxSize = CameraTestUtils.getMaxSize(extensionSizes.toArray(new Size[0]));
                mSurfaceTexture.setDefaultBufferSize(maxSize.getWidth(), maxSize.getHeight());
                Surface repeatingSurface = new Surface(mSurfaceTexture);
                OutputConfiguration textureOutput = new OutputConfiguration(repeatingSurface);
                List<OutputConfiguration> outputs = new ArrayList<>();
                outputs.add(textureOutput);
                BlockingSessionCallback regularSessionListener = new BlockingSessionCallback(
                        mock(CameraCaptureSession.StateCallback.class));
                SessionConfiguration regularConfiguration = new SessionConfiguration(
                        SessionConfiguration.SESSION_REGULAR, outputs,
                        new HandlerExecutor(mTestRule.getHandler()), regularSessionListener);

                BlockingExtensionSessionCallback sessionListener =
                        new BlockingExtensionSessionCallback(mock(
                                CameraExtensionSession.StateCallback.class));
                ExtensionSessionConfiguration configuration =
                        new ExtensionSessionConfiguration(extension, outputs,
                                new HandlerExecutor(mTestRule.getHandler()), sessionListener);

                try {
                    mTestRule.openDevice(id);
                    mTestRule.getCamera().createCaptureSession(regularConfiguration);

                    CameraCaptureSession session =
                            regularSessionListener
                                    .waitAndGetSession(SESSION_CONFIGURE_TIMEOUT_MS);
                    assertNotNull(session);

                    CameraDevice camera = mTestRule.getCamera();
                    camera.createExtensionSession(configuration);
                    CameraExtensionSession extensionSession = sessionListener.waitAndGetSession(
                            SESSION_CONFIGURE_TIMEOUT_MS);
                    assertNotNull(extensionSession);

                    regularSessionListener.getStateWaiter().waitForState(
                            BlockingExtensionSessionCallback.SESSION_CLOSED,
                            SESSION_CLOSE_TIMEOUT_MS);

                    extensionSession.close();
                    sessionListener.getStateWaiter().waitForState(
                            BlockingExtensionSessionCallback.SESSION_CLOSED,
                            SESSION_CLOSE_TIMEOUT_MS);
                } finally {
                    mTestRule.closeDevice(id);
                }
            }
        }
    }

    // Verify that camera extension sessions close as expected when creating a regular capture
    // session.
    @Test
    public void testCloseExtensionSession() throws Exception {
        for (String id : getCameraIdsUnderTest()) {
            StaticMetadata staticMeta =
                    new StaticMetadata(mTestRule.getCameraManager().getCameraCharacteristics(id));
            if (!staticMeta.isColorOutputSupported()) {
                continue;
            }
            updatePreviewSurfaceTexture();
            CameraExtensionCharacteristics extensionChars =
                    mTestRule.getCameraManager().getCameraExtensionCharacteristics(id);
            List<Integer> supportedExtensions = extensionChars.getSupportedExtensions();
            for (Integer extension : supportedExtensions) {
                List<Size> extensionSizes = extensionChars.getExtensionSupportedSizes(extension,
                        mSurfaceTexture.getClass());
                Size maxSize = CameraTestUtils.getMaxSize(extensionSizes.toArray(new Size[0]));
                mSurfaceTexture.setDefaultBufferSize(maxSize.getWidth(), maxSize.getHeight());
                Surface surface = new Surface(mSurfaceTexture);
                OutputConfiguration textureOutput = new OutputConfiguration(surface);
                List<OutputConfiguration> outputs = new ArrayList<>();
                outputs.add(textureOutput);
                BlockingSessionCallback regularSessionListener = new BlockingSessionCallback(
                        mock(CameraCaptureSession.StateCallback.class));
                SessionConfiguration regularConfiguration = new SessionConfiguration(
                        SessionConfiguration.SESSION_REGULAR, outputs,
                        new HandlerExecutor(mTestRule.getHandler()), regularSessionListener);

                BlockingExtensionSessionCallback sessionListener =
                        new BlockingExtensionSessionCallback(mock(
                                CameraExtensionSession.StateCallback.class));
                ExtensionSessionConfiguration configuration =
                        new ExtensionSessionConfiguration(extension, outputs,
                                new HandlerExecutor(mTestRule.getHandler()), sessionListener);

                try {
                    mTestRule.openDevice(id);
                    CameraDevice camera = mTestRule.getCamera();
                    camera.createExtensionSession(configuration);
                    CameraExtensionSession extensionSession = sessionListener.waitAndGetSession(
                            SESSION_CONFIGURE_TIMEOUT_MS);
                    assertNotNull(extensionSession);

                    mTestRule.getCamera().createCaptureSession(regularConfiguration);
                    sessionListener.getStateWaiter().waitForState(
                            BlockingExtensionSessionCallback.SESSION_CLOSED,
                            SESSION_CLOSE_TIMEOUT_MS);

                    CameraCaptureSession session =
                            regularSessionListener.waitAndGetSession(
                                    SESSION_CONFIGURE_TIMEOUT_MS);
                    session.close();
                    regularSessionListener.getStateWaiter().waitForState(
                            BlockingSessionCallback.SESSION_CLOSED, SESSION_CLOSE_TIMEOUT_MS);
                } finally {
                    mTestRule.closeDevice(id);
                }
            }
        }
    }

    // Verify camera device query
    @Test
    public void testGetDevice() throws Exception {
        for (String id : getCameraIdsUnderTest()) {
            StaticMetadata staticMeta =
                    new StaticMetadata(mTestRule.getCameraManager().getCameraCharacteristics(id));
            if (!staticMeta.isColorOutputSupported()) {
                continue;
            }
            updatePreviewSurfaceTexture();
            CameraExtensionCharacteristics extensionChars =
                    mTestRule.getCameraManager().getCameraExtensionCharacteristics(id);
            List<Integer> supportedExtensions = extensionChars.getSupportedExtensions();
            for (Integer extension : supportedExtensions) {
                List<Size> extensionSizes = extensionChars.getExtensionSupportedSizes(extension,
                        mSurfaceTexture.getClass());
                Size maxSize = CameraTestUtils.getMaxSize(extensionSizes.toArray(new Size[0]));
                mSurfaceTexture.setDefaultBufferSize(maxSize.getWidth(), maxSize.getHeight());
                OutputConfiguration privateOutput = new OutputConfiguration(
                        new Surface(mSurfaceTexture));
                List<OutputConfiguration> outputConfigs = new ArrayList<>();
                outputConfigs.add(privateOutput);

                BlockingExtensionSessionCallback sessionListener =
                        new BlockingExtensionSessionCallback(
                                mock(CameraExtensionSession.StateCallback.class));
                ExtensionSessionConfiguration configuration =
                        new ExtensionSessionConfiguration(extension, outputConfigs,
                                new HandlerExecutor(mTestRule.getHandler()), sessionListener);

                try {
                    mTestRule.openDevice(id);
                    CameraDevice camera = mTestRule.getCamera();
                    camera.createExtensionSession(configuration);
                    CameraExtensionSession extensionSession = sessionListener.waitAndGetSession(
                            SESSION_CONFIGURE_TIMEOUT_MS);

                    assertEquals("Unexpected/Invalid camera device", mTestRule.getCamera(),
                            extensionSession.getDevice());
                } finally {
                    mTestRule.closeDevice(id);
                }

                try {
                    sessionListener.getStateWaiter().waitForState(
                            BlockingExtensionSessionCallback.SESSION_CLOSED,
                            SESSION_CLOSE_TIMEOUT_MS);
                    fail("should get TimeoutRuntimeException due to previously closed camera "
                            + "device");
                } catch (TimeoutRuntimeException e) {
                    // Expected, per API spec we should not receive any further session callbacks
                    // besides the device state 'onClosed' callback.
                }
            }
        }
    }

    // Test case for repeating/stopRepeating on all supported extensions and expected state/capture
    // callbacks.
    @Test
    public void testRepeatingCapture() throws Exception {
        for (String id : getCameraIdsUnderTest()) {
            StaticMetadata staticMeta =
                    new StaticMetadata(mTestRule.getCameraManager().getCameraCharacteristics(id));
            if (!staticMeta.isColorOutputSupported()) {
                continue;
            }
            updatePreviewSurfaceTexture();
            CameraExtensionCharacteristics extensionChars =
                    mTestRule.getCameraManager().getCameraExtensionCharacteristics(id);
            List<Integer> supportedExtensions = extensionChars.getSupportedExtensions();
            for (Integer extension : supportedExtensions) {
                List<Size> extensionSizes = extensionChars.getExtensionSupportedSizes(extension,
                        mSurfaceTexture.getClass());
                Size maxSize =
                        CameraTestUtils.getMaxSize(extensionSizes.toArray(new Size[0]));
                mSurfaceTexture.setDefaultBufferSize(maxSize.getWidth(),
                        maxSize.getHeight());
                Surface texturedSurface = new Surface(mSurfaceTexture);

                List<OutputConfiguration> outputConfigs = new ArrayList<>();
                outputConfigs.add(new OutputConfiguration(texturedSurface));

                BlockingExtensionSessionCallback sessionListener =
                        new BlockingExtensionSessionCallback(mock(
                                CameraExtensionSession.StateCallback.class));
                ExtensionSessionConfiguration configuration =
                        new ExtensionSessionConfiguration(extension, outputConfigs,
                                new HandlerExecutor(mTestRule.getHandler()),
                                sessionListener);

                boolean captureResultsSupported =
                        !extensionChars.getAvailableCaptureResultKeys(extension).isEmpty();

                try {
                    mTestRule.openDevice(id);
                    CameraDevice camera = mTestRule.getCamera();
                    camera.createExtensionSession(configuration);
                    CameraExtensionSession extensionSession =
                            sessionListener.waitAndGetSession(
                                    SESSION_CONFIGURE_TIMEOUT_MS);
                    assertNotNull(extensionSession);

                    CaptureRequest.Builder captureBuilder =
                            mTestRule.getCamera().createCaptureRequest(
                                    android.hardware.camera2.CameraDevice.TEMPLATE_PREVIEW);
                    captureBuilder.addTarget(texturedSurface);
                    CameraExtensionSession.ExtensionCaptureCallback captureCallbackMock =
                            mock(CameraExtensionSession.ExtensionCaptureCallback.class);
                    SimpleCaptureCallback simpleCaptureCallback =
                            new SimpleCaptureCallback(extension, captureCallbackMock,
                                    extensionChars.getAvailableCaptureResultKeys(extension),
                                    mCollector);
                    CaptureRequest request = captureBuilder.build();
                    int sequenceId = extensionSession.setRepeatingRequest(request,
                            new HandlerExecutor(mTestRule.getHandler()), simpleCaptureCallback);

                    verify(captureCallbackMock,
                            timeout(REPEATING_REQUEST_TIMEOUT_MS).atLeastOnce())
                            .onCaptureStarted(eq(extensionSession), eq(request), anyLong());
                    verify(captureCallbackMock,
                            timeout(REPEATING_REQUEST_TIMEOUT_MS).atLeastOnce())
                            .onCaptureProcessStarted(extensionSession, request);
                    if (captureResultsSupported) {
                        verify(captureCallbackMock,
                                timeout(MULTI_FRAME_CAPTURE_IMAGE_TIMEOUT_MS).atLeastOnce())
                                .onCaptureResultAvailable(eq(extensionSession), eq(request),
                                        any(TotalCaptureResult.class));
                    }

                    extensionSession.stopRepeating();

                    verify(captureCallbackMock,
                            timeout(MULTI_FRAME_CAPTURE_IMAGE_TIMEOUT_MS).times(1))
                            .onCaptureSequenceCompleted(extensionSession, sequenceId);

                    verify(captureCallbackMock, times(0))
                            .onCaptureSequenceAborted(any(CameraExtensionSession.class),
                                    anyInt());

                    extensionSession.close();

                    sessionListener.getStateWaiter().waitForState(
                            BlockingExtensionSessionCallback.SESSION_CLOSED,
                            SESSION_CLOSE_TIMEOUT_MS);

                    assertTrue("The sum of onCaptureProcessStarted and onCaptureFailed" +
                                    " callbacks must be greater or equal than the number of calls" +
                                    " to onCaptureStarted!",
                            simpleCaptureCallback.getTotalFramesArrived() +
                                    simpleCaptureCallback.getTotalFramesFailed() >=
                                    simpleCaptureCallback.getTotalFramesStarted());
                    assertTrue(String.format("The last repeating request surface timestamp " +
                                    "%d must be less than or equal to the last " +
                                    "onCaptureStarted " +
                                    "timestamp %d", mSurfaceTexture.getTimestamp(),
                            simpleCaptureCallback.getLastTimestamp()),
                            mSurfaceTexture.getTimestamp() <=
                                    simpleCaptureCallback.getLastTimestamp());
                } finally {
                    mTestRule.closeDevice(id);
                    texturedSurface.release();
                }
            }
        }
    }

    // Test for postview of still capture on all supported extensions
    @Test
    public void testPostviewAndCapture() throws Exception {
        final int IMAGE_COUNT = 10;
        final int SUPPORTED_CAPTURE_OUTPUT_FORMATS[] = {
                ImageFormat.YUV_420_888,
                ImageFormat.JPEG,
                ImageFormat.JPEG_R
        };
        for (String id : getCameraIdsUnderTest()) {
            StaticMetadata staticMeta =
                    new StaticMetadata(mTestRule.getCameraManager().getCameraCharacteristics(id));
            if (!staticMeta.isColorOutputSupported()) {
                continue;
            }
            updatePreviewSurfaceTexture();
            CameraExtensionCharacteristics extensionChars =
                    mTestRule.getCameraManager().getCameraExtensionCharacteristics(id);
            List<Integer> supportedExtensions = extensionChars.getSupportedExtensions();
            for (Integer extension : supportedExtensions) {
                if (!extensionChars.isPostviewAvailable(extension)) {
                    continue;
                }

                for (int captureFormat : SUPPORTED_CAPTURE_OUTPUT_FORMATS) {
                    boolean captureProgressSupported =
                            extensionChars.isCaptureProcessProgressAvailable(extension);

                    List<Size> extensionSizes = extensionChars.getExtensionSupportedSizes(extension,
                            captureFormat);
                    if (extensionSizes.isEmpty()) {
                        continue;
                    }

                    Size maxSize = CameraTestUtils.getMaxSize(extensionSizes.toArray(new Size[0]));
                    List<Size> postviewSizes = extensionChars.getPostviewSupportedSizes(extension,
                            maxSize, captureFormat);
                    if (postviewSizes.isEmpty()) {
                        continue;
                    }

                    SimpleImageReaderListener imageListener = new SimpleImageReaderListener(false,
                            1);
                    ImageReader extensionImageReader = CameraTestUtils.makeImageReader(maxSize,
                            captureFormat, /*maxImages*/ 1, imageListener,
                            mTestRule.getHandler());
                    Surface imageReaderSurface = extensionImageReader.getSurface();
                    OutputConfiguration readerOutput = new OutputConfiguration(imageReaderSurface);
                    List<OutputConfiguration> outputConfigs = new ArrayList<>();
                    outputConfigs.add(readerOutput);

                    Size postviewSize =
                            CameraTestUtils.getMaxSize(postviewSizes.toArray(new Size[0]));
                    SimpleImageReaderListener imageListenerPostview =
                            new SimpleImageReaderListener(false, 1);
                    ImageReader postviewImageReader = CameraTestUtils.makeImageReader(postviewSize,
                            captureFormat, /*maxImages*/ 1, imageListenerPostview,
                            mTestRule.getHandler());
                    Surface postviewImageReaderSurface = postviewImageReader.getSurface();
                    OutputConfiguration postviewReaderOutput =
                            new OutputConfiguration(postviewImageReaderSurface);

                    BlockingExtensionSessionCallback sessionListener =
                            new BlockingExtensionSessionCallback(mock(
                                    CameraExtensionSession.StateCallback.class));
                    ExtensionSessionConfiguration configuration =
                            new ExtensionSessionConfiguration(extension, outputConfigs,
                                    new HandlerExecutor(mTestRule.getHandler()),
                                    sessionListener);
                    configuration.setPostviewOutputConfiguration(postviewReaderOutput);
                    assertNotNull(configuration.getPostviewOutputConfiguration());

                    String streamName = "test_extension_postview_capture";
                    mReportLog = new DeviceReportLog(REPORT_LOG_NAME, streamName);
                    mReportLog.addValue("camera_id", id, ResultType.NEUTRAL, ResultUnit.NONE);
                    mReportLog.addValue("extension_id", extension, ResultType.NEUTRAL,
                            ResultUnit.NONE);
                    double[] captureTimes = new double[IMAGE_COUNT];
                    double[] postviewCaptureTimes = new double[IMAGE_COUNT];
                    boolean captureResultsSupported =
                            !extensionChars.getAvailableCaptureResultKeys(extension).isEmpty();

                    try {
                        mTestRule.openDevice(id);
                        CameraDevice camera = mTestRule.getCamera();
                        camera.createExtensionSession(configuration);
                        CameraExtensionSession extensionSession =
                                sessionListener.waitAndGetSession(
                                        SESSION_CONFIGURE_TIMEOUT_MS);
                        assertNotNull(extensionSession);

                        CaptureRequest.Builder captureBuilder =
                                mTestRule.getCamera().createCaptureRequest(
                                        CameraDevice.TEMPLATE_STILL_CAPTURE);
                        captureBuilder.addTarget(imageReaderSurface);
                        captureBuilder.addTarget(postviewImageReaderSurface);
                        CameraExtensionSession.ExtensionCaptureCallback captureMockCallback =
                                mock(CameraExtensionSession.ExtensionCaptureCallback.class);
                        SimpleCaptureCallback captureCallback =
                                new SimpleCaptureCallback(extension, captureMockCallback,
                                        extensionChars.getAvailableCaptureResultKeys(extension),
                                        mCollector);

                        for (int i = 0; i < IMAGE_COUNT; i++) {
                            int jpegOrientation = (i * 90) % 360; // degrees [0..270]
                            if (captureFormat == ImageFormat.JPEG
                                    || captureFormat == ImageFormat.JPEG_R) {
                                captureBuilder.set(CaptureRequest.JPEG_ORIENTATION,
                                        jpegOrientation);
                            }
                            CaptureRequest request = captureBuilder.build();
                            long startTimeMs = SystemClock.elapsedRealtime();
                            captureCallback.resetCaptureProgress();
                            int sequenceId = extensionSession.capture(request,
                                    new HandlerExecutor(mTestRule.getHandler()), captureCallback);

                            Image imgPostview  =
                                    imageListenerPostview
                                    .getImage(MULTI_FRAME_CAPTURE_IMAGE_TIMEOUT_MS);
                            postviewCaptureTimes[i] = SystemClock.elapsedRealtime() - startTimeMs;

                            if (captureFormat == ImageFormat.JPEG
                                    || captureFormat == ImageFormat.JPEG_R) {
                                verifyJpegOrientation(imgPostview, postviewSize,
                                        jpegOrientation, captureFormat);
                            } else {
                                validateImage(imgPostview, postviewSize.getWidth(),
                                        postviewSize.getHeight(), captureFormat, null);
                            }

                            Long imgTsPostview = imgPostview.getTimestamp();
                            imgPostview.close();

                            Image img =
                                    imageListener.getImage(MULTI_FRAME_CAPTURE_IMAGE_TIMEOUT_MS);
                            captureTimes[i] = SystemClock.elapsedRealtime() - startTimeMs;

                            if (captureFormat == ImageFormat.JPEG
                                    || captureFormat == ImageFormat.JPEG_R) {
                                verifyJpegOrientation(img, maxSize,
                                        jpegOrientation, captureFormat);
                            } else {
                                validateImage(img, maxSize.getWidth(),
                                        maxSize.getHeight(), captureFormat, null);
                            }

                            Long imgTs = img.getTimestamp();
                            img.close();

                            assertEquals("Still capture timestamp does not match its "
                                    + "postview timestamp", imgTsPostview, imgTs);

                            verify(captureMockCallback,
                                    timeout(MULTI_FRAME_CAPTURE_IMAGE_TIMEOUT_MS).times(1))
                                    .onCaptureStarted(eq(extensionSession), eq(request), eq(imgTs));
                            verify(captureMockCallback, times(1))
                                    .onCaptureStarted(eq(extensionSession), eq(request), anyLong());
                            verify(captureMockCallback,
                                    timeout(MULTI_FRAME_CAPTURE_IMAGE_TIMEOUT_MS).times(1))
                                    .onCaptureProcessStarted(extensionSession, request);
                            verify(captureMockCallback,
                                    timeout(MULTI_FRAME_CAPTURE_IMAGE_TIMEOUT_MS).times(1))
                                    .onCaptureSequenceCompleted(extensionSession, sequenceId);
                            if (captureResultsSupported) {
                                verify(captureMockCallback,
                                        timeout(MULTI_FRAME_CAPTURE_IMAGE_TIMEOUT_MS).times(1))
                                        .onCaptureResultAvailable(eq(extensionSession), eq(request),
                                                any(TotalCaptureResult.class));
                            }
                            if (captureProgressSupported) {
                                verify(captureMockCallback,
                                        timeout(MULTI_FRAME_CAPTURE_IMAGE_TIMEOUT_MS).times(1))
                                        .onCaptureProcessProgressed(eq(extensionSession),
                                        eq(request), eq(100));
                            }
                        }

                        mReportLog.addValue("width", maxSize.getWidth(), ResultType.NEUTRAL,
                                ResultUnit.NONE);
                        mReportLog.addValue("height", maxSize.getHeight(),
                                ResultType.NEUTRAL, ResultUnit.NONE);
                        mReportLog.addValue("format", captureFormat, ResultType.NEUTRAL,
                                ResultUnit.NONE);
                        long avgPostviewLatency = (long) Stat.getAverage(postviewCaptureTimes);
                        mReportLog.addValue("avg_postview_latency", avgPostviewLatency,
                                ResultType.LOWER_BETTER, ResultUnit.MS);
                        long avgCaptureLatency = (long) Stat.getAverage(captureTimes);
                        mReportLog.addValue("avg_capture_latency", avgCaptureLatency,
                                ResultType.LOWER_BETTER, ResultUnit.MS);

                        verify(captureMockCallback, times(0))
                                .onCaptureSequenceAborted(any(CameraExtensionSession.class),
                                        anyInt());
                        verify(captureMockCallback, times(0))
                                .onCaptureFailed(any(CameraExtensionSession.class),
                                        any(CaptureRequest.class));
                        verify(captureMockCallback, times(0))
                                .onCaptureFailed(any(CameraExtensionSession.class),
                                        any(CaptureRequest.class), anyInt());
                        Range<Long> latencyRange =
                                extensionChars.getEstimatedCaptureLatencyRangeMillis(extension,
                                        maxSize, captureFormat);
                        if (latencyRange != null) {
                            String msg = String.format("Camera [%s]: The measured average "
                                            + "capture latency of %d ms. for extension type %d  "
                                            + "with image format: %d and size: %dx%d must be "
                                            + "within the advertised range of [%d, %d] ms.",
                                    id, avgCaptureLatency, extension, captureFormat,
                                    maxSize.getWidth(), maxSize.getHeight(),
                                    latencyRange.getLower(), latencyRange.getUpper());
                            assertTrue(msg, latencyRange.contains(avgCaptureLatency));
                        }

                        extensionSession.close();

                        sessionListener.getStateWaiter().waitForState(
                                BlockingExtensionSessionCallback.SESSION_CLOSED,
                                SESSION_CLOSE_TIMEOUT_MS);
                    } finally {
                        mTestRule.closeDevice(id);
                        extensionImageReader.close();
                        mReportLog.submit(InstrumentationRegistry.getInstrumentation());
                    }
                }
            }
        }
    }

    // Test case for multi-frame only capture on all supported extensions and expected state
    // callbacks. Verify still frame output, measure the average capture latency and if possible
    // ensure that the value is within the reported range.
    @Test
    public void testMultiFrameCapture() throws Exception {
        final int IMAGE_COUNT = 10;
        final int SUPPORTED_CAPTURE_OUTPUT_FORMATS[] = {
                ImageFormat.YUV_420_888,
                ImageFormat.JPEG,
                ImageFormat.JPEG_R
        };
        for (String id : getCameraIdsUnderTest()) {
            StaticMetadata staticMeta =
                    new StaticMetadata(mTestRule.getCameraManager().getCameraCharacteristics(id));
            if (!staticMeta.isColorOutputSupported()) {
                continue;
            }
            updatePreviewSurfaceTexture();
            CameraExtensionCharacteristics extensionChars =
                    mTestRule.getCameraManager().getCameraExtensionCharacteristics(id);
            List<Integer> supportedExtensions = extensionChars.getSupportedExtensions();
            for (Integer extension : supportedExtensions) {
                boolean captureProgressSupported = extensionChars.isCaptureProcessProgressAvailable(
                        extension);
                for (int captureFormat : SUPPORTED_CAPTURE_OUTPUT_FORMATS) {
                    List<Size> extensionSizes = extensionChars.getExtensionSupportedSizes(extension,
                            captureFormat);
                    if (extensionSizes.isEmpty()) {
                        continue;
                    }
                    Size maxSize = CameraTestUtils.getMaxSize(extensionSizes.toArray(new Size[0]));
                    SimpleImageReaderListener imageListener = new SimpleImageReaderListener(false,
                            1);
                    ImageReader extensionImageReader = CameraTestUtils.makeImageReader(maxSize,
                            captureFormat, /*maxImages*/ 1, imageListener,
                            mTestRule.getHandler());
                    Surface imageReaderSurface = extensionImageReader.getSurface();
                    OutputConfiguration readerOutput = new OutputConfiguration(imageReaderSurface);
                    List<OutputConfiguration> outputConfigs = new ArrayList<>();
                    outputConfigs.add(readerOutput);

                    BlockingExtensionSessionCallback sessionListener =
                            new BlockingExtensionSessionCallback(mock(
                                    CameraExtensionSession.StateCallback.class));
                    ExtensionSessionConfiguration configuration =
                            new ExtensionSessionConfiguration(extension, outputConfigs,
                                    new HandlerExecutor(mTestRule.getHandler()),
                                    sessionListener);
                    String streamName = "test_extension_capture";
                    mReportLog = new DeviceReportLog(REPORT_LOG_NAME, streamName);
                    mReportLog.addValue("camera_id", id, ResultType.NEUTRAL, ResultUnit.NONE);
                    mReportLog.addValue("extension_id", extension, ResultType.NEUTRAL,
                            ResultUnit.NONE);
                    double[] captureTimes = new double[IMAGE_COUNT];
                    boolean captureResultsSupported =
                            !extensionChars.getAvailableCaptureResultKeys(extension).isEmpty();

                    try {
                        mTestRule.openDevice(id);
                        CameraDevice camera = mTestRule.getCamera();
                        camera.createExtensionSession(configuration);
                        CameraExtensionSession extensionSession =
                                sessionListener.waitAndGetSession(
                                        SESSION_CONFIGURE_TIMEOUT_MS);
                        assertNotNull(extensionSession);

                        CaptureRequest.Builder captureBuilder =
                                mTestRule.getCamera().createCaptureRequest(
                                        CameraDevice.TEMPLATE_STILL_CAPTURE);
                        captureBuilder.addTarget(imageReaderSurface);
                        CameraExtensionSession.ExtensionCaptureCallback captureMockCallback =
                                mock(CameraExtensionSession.ExtensionCaptureCallback.class);
                        SimpleCaptureCallback captureCallback =
                                new SimpleCaptureCallback(extension, captureMockCallback,
                                        extensionChars.getAvailableCaptureResultKeys(extension),
                                        mCollector);

                        for (int i = 0; i < IMAGE_COUNT; i++) {
                            int jpegOrientation = (i * 90) % 360; // degrees [0..270]
                            if (captureFormat == ImageFormat.JPEG
                                    || captureFormat == ImageFormat.JPEG_R) {
                                captureBuilder.set(CaptureRequest.JPEG_ORIENTATION,
                                        jpegOrientation);
                            }
                            CaptureRequest request = captureBuilder.build();
                            long startTimeMs = SystemClock.elapsedRealtime();
                            captureCallback.resetCaptureProgress();
                            int sequenceId = extensionSession.capture(request,
                                    new HandlerExecutor(mTestRule.getHandler()), captureCallback);

                            Image img =
                                    imageListener.getImage(MULTI_FRAME_CAPTURE_IMAGE_TIMEOUT_MS);
                            captureTimes[i] = SystemClock.elapsedRealtime() - startTimeMs;

                            if (captureFormat == ImageFormat.JPEG
                                    || captureFormat == ImageFormat.JPEG_R) {
                                verifyJpegOrientation(img, maxSize,
                                        jpegOrientation, captureFormat);
                            } else {
                                validateImage(img, maxSize.getWidth(),
                                        maxSize.getHeight(), captureFormat, null);
                            }

                            Long imgTs = img.getTimestamp();
                            img.close();

                            verify(captureMockCallback,
                                    timeout(MULTI_FRAME_CAPTURE_IMAGE_TIMEOUT_MS).times(1))
                                    .onCaptureStarted(eq(extensionSession), eq(request), eq(imgTs));
                            verify(captureMockCallback, times(1))
                                    .onCaptureStarted(eq(extensionSession), eq(request), anyLong());
                            verify(captureMockCallback,
                                    timeout(MULTI_FRAME_CAPTURE_IMAGE_TIMEOUT_MS).times(1))
                                    .onCaptureProcessStarted(extensionSession, request);
                            verify(captureMockCallback,
                                    timeout(MULTI_FRAME_CAPTURE_IMAGE_TIMEOUT_MS).times(1))
                                    .onCaptureSequenceCompleted(extensionSession, sequenceId);
                            if (captureResultsSupported) {
                                verify(captureMockCallback,
                                        timeout(MULTI_FRAME_CAPTURE_IMAGE_TIMEOUT_MS).times(1))
                                        .onCaptureResultAvailable(eq(extensionSession), eq(request),
                                                any(TotalCaptureResult.class));
                            }
                            if (captureProgressSupported) {
                                verify(captureMockCallback,
                                        timeout(MULTI_FRAME_CAPTURE_IMAGE_TIMEOUT_MS).times(1))
                                    .onCaptureProcessProgressed(eq(extensionSession),
                                            eq(request), eq(100));
                            }
                        }

                        mReportLog.addValue("width", maxSize.getWidth(), ResultType.NEUTRAL,
                                ResultUnit.NONE);
                        mReportLog.addValue("height", maxSize.getHeight(),
                                ResultType.NEUTRAL, ResultUnit.NONE);
                        mReportLog.addValue("format", captureFormat, ResultType.NEUTRAL,
                                ResultUnit.NONE);
                        long avgCaptureLatency = (long) Stat.getAverage(captureTimes);
                        mReportLog.addValue("avg_latency", avgCaptureLatency,
                                ResultType.LOWER_BETTER, ResultUnit.MS);

                        verify(captureMockCallback, times(0))
                                .onCaptureSequenceAborted(any(CameraExtensionSession.class),
                                        anyInt());
                        verify(captureMockCallback, times(0))
                                .onCaptureFailed(any(CameraExtensionSession.class),
                                        any(CaptureRequest.class));
                        verify(captureMockCallback, times(0))
                                .onCaptureFailed(any(CameraExtensionSession.class),
                                        any(CaptureRequest.class), anyInt());
                        Range<Long> latencyRange =
                                extensionChars.getEstimatedCaptureLatencyRangeMillis(extension,
                                        maxSize, captureFormat);
                        if (latencyRange != null) {
                            String msg = String.format("Camera [%s]: The measured average "
                                            + "capture latency of %d ms. for extension type %d  "
                                            + "with image format: %d and size: %dx%d must be "
                                            + "within the advertised range of [%d, %d] ms.",
                                    id, avgCaptureLatency, extension, captureFormat,
                                    maxSize.getWidth(), maxSize.getHeight(),
                                    latencyRange.getLower(), latencyRange.getUpper());
                            assertTrue(msg, latencyRange.contains(avgCaptureLatency));
                        }

                        extensionSession.close();

                        sessionListener.getStateWaiter().waitForState(
                                BlockingExtensionSessionCallback.SESSION_CLOSED,
                                SESSION_CLOSE_TIMEOUT_MS);
                    } finally {
                        mTestRule.closeDevice(id);
                        extensionImageReader.close();
                        mReportLog.submit(InstrumentationRegistry.getInstrumentation());
                    }
                }
            }
        }
    }

    // Verify concurrent extension sessions behavior
    @Test
    public void testConcurrentSessions() throws Exception {
        Set<Set<String>> concurrentCameraIdSet =
                mTestRule.getCameraManager().getConcurrentCameraIds();
        if (concurrentCameraIdSet.isEmpty()) {
            return;
        }

        for (String id : getCameraIdsUnderTest()) {
            StaticMetadata staticMeta =
                    new StaticMetadata(mTestRule.getCameraManager().getCameraCharacteristics(id));
            if (!staticMeta.isColorOutputSupported()) {
                continue;
            }
            CameraExtensionCharacteristics extensionChars =
                    mTestRule.getCameraManager().getCameraExtensionCharacteristics(id);
            List<Integer> supportedExtensions = extensionChars.getSupportedExtensions();
            if (supportedExtensions.isEmpty()) {
                continue;
            }

            Set<String> concurrentCameraIds = null;
            for (Set<String> entry : concurrentCameraIdSet) {
                if (entry.contains(id)) {
                    concurrentCameraIds = entry;
                    break;
                }
            }
            if (concurrentCameraIds == null) {
                continue;
            }

            String concurrentCameraId = null;
            CameraExtensionCharacteristics concurrentExtensionChars = null;
            for (String entry : concurrentCameraIds) {
                if (entry.equals(id)) {
                    continue;
                }
                if (!(new StaticMetadata(mTestRule.getCameraManager().getCameraCharacteristics(
                        entry))).isColorOutputSupported()) {
                    continue;
                }
                CameraExtensionCharacteristics chars =
                        mTestRule.getCameraManager().getCameraExtensionCharacteristics(entry);
                if (chars.getSupportedExtensions().isEmpty()) {
                    continue;
                }
                concurrentCameraId = entry;
                concurrentExtensionChars = chars;
                break;
            }
            if ((concurrentCameraId == null) || (concurrentExtensionChars == null)) {
                continue;
            }

            updatePreviewSurfaceTexture();
            int extensionId = supportedExtensions.get(0);
            List<Size> extensionSizes = extensionChars.getExtensionSupportedSizes(extensionId,
                    mSurfaceTexture.getClass());
            Size maxSize = CameraTestUtils.getMaxSize(extensionSizes.toArray(new Size[0]));
            mSurfaceTexture.setDefaultBufferSize(maxSize.getWidth(), maxSize.getHeight());
            OutputConfiguration outputConfig = new OutputConfiguration(
                    new Surface(mSurfaceTexture));
            List<OutputConfiguration> outputConfigs = new ArrayList<>();
            outputConfigs.add(outputConfig);

            BlockingExtensionSessionCallback sessionListener =
                    new BlockingExtensionSessionCallback(
                            mock(CameraExtensionSession.StateCallback.class));
            ExtensionSessionConfiguration configuration =
                    new ExtensionSessionConfiguration(extensionId, outputConfigs,
                            new HandlerExecutor(mTestRule.getHandler()), sessionListener);

            CameraDevice concurrentCameraDevice = null;
            ImageReader extensionImageReader = null;
            try {
                mTestRule.openDevice(id);
                concurrentCameraDevice = CameraTestUtils.openCamera(mTestRule.getCameraManager(),
                        concurrentCameraId, new BlockingStateCallback(), mTestRule.getHandler());
                CameraDevice camera = mTestRule.getCamera();
                camera.createExtensionSession(configuration);
                CameraExtensionSession extensionSession = sessionListener.waitAndGetSession(
                        SESSION_CONFIGURE_TIMEOUT_MS);
                assertNotNull(extensionSession);

                assertNotNull(concurrentCameraDevice);
                int concurrentExtensionId =
                        concurrentExtensionChars.getSupportedExtensions().get(0);
                List<Size> captureSizes = concurrentExtensionChars.getExtensionSupportedSizes(
                        concurrentExtensionId, mSurfaceTexture.getClass());
                assertFalse("No SurfaceTexture output supported", captureSizes.isEmpty());
                Size captureMaxSize =
                        CameraTestUtils.getMaxSize(captureSizes.toArray(new Size[0]));

                extensionImageReader = ImageReader.newInstance(
                        captureMaxSize.getWidth(), captureMaxSize.getHeight(), ImageFormat.PRIVATE,
                        /*maxImages*/ 1, HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE);
                Surface imageReaderSurface = extensionImageReader.getSurface();
                OutputConfiguration readerOutput = new OutputConfiguration(imageReaderSurface);
                outputConfigs = new ArrayList<>();
                outputConfigs.add(readerOutput);
                CameraExtensionSession.StateCallback mockSessionListener =
                        mock(CameraExtensionSession.StateCallback.class);
                ExtensionSessionConfiguration concurrentConfiguration =
                        new ExtensionSessionConfiguration(concurrentExtensionId, outputConfigs,
                                new HandlerExecutor(mTestRule.getHandler()),
                                mockSessionListener);
                concurrentCameraDevice.createExtensionSession(concurrentConfiguration);
                // Trying to initialize multiple concurrent extension sessions is expected to fail
                verify(mockSessionListener, timeout(SESSION_CONFIGURE_TIMEOUT_MS).times(1))
                        .onConfigureFailed(any(CameraExtensionSession.class));
                verify(mockSessionListener, times(0)).onConfigured(
                        any(CameraExtensionSession.class));

                extensionSession.close();
                sessionListener.getStateWaiter().waitForState(
                        BlockingExtensionSessionCallback.SESSION_CLOSED,
                        SESSION_CLOSE_TIMEOUT_MS);

                // Initialization of another extension session must now be possible
                BlockingExtensionSessionCallback concurrentSessionListener =
                        new BlockingExtensionSessionCallback(
                                mock(CameraExtensionSession.StateCallback.class));
                concurrentConfiguration = new ExtensionSessionConfiguration(concurrentExtensionId,
                        outputConfigs, new HandlerExecutor(mTestRule.getHandler()),
                        concurrentSessionListener);
                concurrentCameraDevice.createExtensionSession(concurrentConfiguration);
                extensionSession = concurrentSessionListener.waitAndGetSession(
                        SESSION_CONFIGURE_TIMEOUT_MS);
                assertNotNull(extensionSession);
                extensionSession.close();
                concurrentSessionListener.getStateWaiter().waitForState(
                        BlockingExtensionSessionCallback.SESSION_CLOSED,
                        SESSION_CLOSE_TIMEOUT_MS);
            } finally {
                mTestRule.closeDevice(id);
                if (concurrentCameraDevice != null) {
                    concurrentCameraDevice.close();
                }
                if (extensionImageReader != null) {
                    extensionImageReader.close();
                }
            }
        }
    }

    // Test case combined repeating with multi frame capture on all supported extensions.
    // Verify still frame output.
    @Test
    public void testRepeatingAndCaptureCombined() throws Exception {
        final double LATENCY_MARGIN = .1f; // Account for system load, capture call duration etc.
        for (String id : getCameraIdsUnderTest()) {
            StaticMetadata staticMeta =
                    new StaticMetadata(mTestRule.getCameraManager().getCameraCharacteristics(id));
            if (!staticMeta.isColorOutputSupported()) {
                continue;
            }
            updatePreviewSurfaceTexture();
            CameraExtensionCharacteristics extensionChars =
                    mTestRule.getCameraManager().getCameraExtensionCharacteristics(id);
            List<Integer> supportedExtensions = extensionChars.getSupportedExtensions();
            for (Integer extension : supportedExtensions) {

                Set<CaptureRequest.Key> supportedRequestKeys =
                        extensionChars.getAvailableCaptureRequestKeys(extension);
                boolean supportsStrengthControl = supportedRequestKeys.contains(
                        CaptureRequest.EXTENSION_STRENGTH);

                Set<CaptureResult.Key> supportedResultKeys =
                        extensionChars.getAvailableCaptureResultKeys(extension);
                if (supportsStrengthControl) {
                    assertTrue(supportedResultKeys.contains(CaptureResult.EXTENSION_STRENGTH));
                }

                int captureFormat = ImageFormat.JPEG;
                List<Size> captureSizes = extensionChars.getExtensionSupportedSizes(extension,
                        captureFormat);
                assertFalse("No Jpeg output supported", captureSizes.isEmpty());
                Size captureMaxSize =
                        CameraTestUtils.getMaxSize(captureSizes.toArray(new Size[0]));

                SimpleImageReaderListener imageListener = new SimpleImageReaderListener(false
                        , 1);
                ImageReader extensionImageReader = CameraTestUtils.makeImageReader(
                        captureMaxSize, captureFormat, /*maxImages*/ 1, imageListener,
                        mTestRule.getHandler());
                Surface imageReaderSurface = extensionImageReader.getSurface();
                OutputConfiguration readerOutput = new OutputConfiguration(imageReaderSurface);
                List<OutputConfiguration> outputConfigs = new ArrayList<>();
                outputConfigs.add(readerOutput);

                // Pick a supported preview/repeating size with aspect ratio close to the
                // multi-frame capture size
                List<Size> repeatingSizes = extensionChars.getExtensionSupportedSizes(extension,
                        mSurfaceTexture.getClass());
                Size maxRepeatingSize =
                        CameraTestUtils.getMaxSize(repeatingSizes.toArray(new Size[0]));
                List<Size> previewSizes = getSupportedPreviewSizes(id,
                        mTestRule.getCameraManager(),
                        getPreviewSizeBound(mTestRule.getWindowManager(), PREVIEW_SIZE_BOUND));
                List<Size> supportedPreviewSizes =
                        previewSizes.stream().filter(repeatingSizes::contains).collect(
                                Collectors.toList());
                if (!supportedPreviewSizes.isEmpty()) {
                    float targetAr =
                            ((float) captureMaxSize.getWidth()) / captureMaxSize.getHeight();
                    for (Size s : supportedPreviewSizes) {
                        float currentAr = ((float) s.getWidth()) / s.getHeight();
                        if (Math.abs(targetAr - currentAr) < 0.01) {
                            maxRepeatingSize = s;
                            break;
                        }
                    }
                }

                boolean captureResultsSupported =
                        !extensionChars.getAvailableCaptureResultKeys(extension).isEmpty();

                mSurfaceTexture.setDefaultBufferSize(maxRepeatingSize.getWidth(),
                        maxRepeatingSize.getHeight());
                Surface texturedSurface = new Surface(mSurfaceTexture);
                outputConfigs.add(new OutputConfiguration(texturedSurface));

                BlockingExtensionSessionCallback sessionListener =
                        new BlockingExtensionSessionCallback(mock(
                                CameraExtensionSession.StateCallback.class));
                ExtensionSessionConfiguration configuration =
                        new ExtensionSessionConfiguration(extension, outputConfigs,
                                new HandlerExecutor(mTestRule.getHandler()),
                                sessionListener);
                try {
                    mTestRule.openDevice(id);
                    CameraDevice camera = mTestRule.getCamera();
                    camera.createExtensionSession(configuration);
                    CameraExtensionSession extensionSession =
                            sessionListener.waitAndGetSession(
                                    SESSION_CONFIGURE_TIMEOUT_MS);
                    assertNotNull(extensionSession);

                    CaptureRequest.Builder captureBuilder =
                            mTestRule.getCamera().createCaptureRequest(
                                    android.hardware.camera2.CameraDevice.TEMPLATE_PREVIEW);
                    captureBuilder.addTarget(texturedSurface);
                    CameraExtensionSession.ExtensionCaptureCallback repeatingCallbackMock =
                            mock(CameraExtensionSession.ExtensionCaptureCallback.class);
                    SimpleCaptureCallback repeatingCaptureCallback =
                            new SimpleCaptureCallback(extension, repeatingCallbackMock,
                                    extensionChars.getAvailableCaptureResultKeys(extension),
                                    mCollector);

                    if (supportsStrengthControl) {
                        captureBuilder.set(CaptureRequest.EXTENSION_STRENGTH, 100);
                    }

                    CaptureRequest repeatingRequest = captureBuilder.build();
                    int repeatingSequenceId =
                            extensionSession.setRepeatingRequest(repeatingRequest,
                                    new HandlerExecutor(mTestRule.getHandler()),
                                    repeatingCaptureCallback);

                    Thread.sleep(REPEATING_REQUEST_TIMEOUT_MS);

                    verify(repeatingCallbackMock, atLeastOnce())
                            .onCaptureStarted(eq(extensionSession), eq(repeatingRequest),
                                    anyLong());
                    verify(repeatingCallbackMock, atLeastOnce())
                            .onCaptureProcessStarted(extensionSession, repeatingRequest);
                    if (captureResultsSupported) {
                        verify(repeatingCallbackMock,
                                timeout(MULTI_FRAME_CAPTURE_IMAGE_TIMEOUT_MS).atLeastOnce())
                                .onCaptureResultAvailable(eq(extensionSession),
                                        eq(repeatingRequest), any(TotalCaptureResult.class));
                    }
                    verify(repeatingCallbackMock, times(0)).onCaptureProcessProgressed(
                            any(CameraExtensionSession.class), any(CaptureRequest.class), anyInt());

                    captureBuilder = mTestRule.getCamera().createCaptureRequest(
                            android.hardware.camera2.CameraDevice.TEMPLATE_STILL_CAPTURE);
                    captureBuilder.addTarget(imageReaderSurface);
                    CameraExtensionSession.ExtensionCaptureCallback captureCallback =
                            mock(CameraExtensionSession.ExtensionCaptureCallback.class);

                    CameraExtensionSession.StillCaptureLatency stillCaptureLatency =
                            extensionSession.getRealtimeStillCaptureLatency();
                    CaptureRequest captureRequest = captureBuilder.build();
                    long startTimeMs = SystemClock.elapsedRealtime();
                    int captureSequenceId = extensionSession.capture(captureRequest,
                            new HandlerExecutor(mTestRule.getHandler()), captureCallback);

                    Image img =
                            imageListener.getImage(MULTI_FRAME_CAPTURE_IMAGE_TIMEOUT_MS);
                    long captureTime = SystemClock.elapsedRealtime() - startTimeMs;
                    validateImage(img, captureMaxSize.getWidth(),
                            captureMaxSize.getHeight(), captureFormat, null);
                    Long imgTs = img.getTimestamp();
                    img.close();

                    if (stillCaptureLatency != null) {
                        assertTrue("Still capture frame latency must be positive!",
                                stillCaptureLatency.getCaptureLatency() > 0);
                        assertTrue("Processing capture latency must be non-negative!",
                                stillCaptureLatency.getProcessingLatency() >= 0);
                        long estimatedTotalLatency = stillCaptureLatency.getCaptureLatency() +
                                stillCaptureLatency.getProcessingLatency();
                        long estimatedTotalLatencyMin =
                                (long) (estimatedTotalLatency * (1.f - LATENCY_MARGIN));
                        long estimatedTotalLatencyMax =
                                (long) (estimatedTotalLatency * (1.f + LATENCY_MARGIN));
                        assertTrue(String.format("Camera %s: Measured still capture latency " +
                                                "doesn't match: %d ms, expected [%d,%d]ms.", id,
                                        captureTime, estimatedTotalLatencyMin,
                                        estimatedTotalLatencyMax),
                                (captureTime <= estimatedTotalLatencyMax) &&
                                        (captureTime >= estimatedTotalLatencyMin));
                    }

                    verify(captureCallback, times(1))
                            .onCaptureStarted(eq(extensionSession), eq(captureRequest), eq(imgTs));
                    verify(captureCallback, timeout(MULTI_FRAME_CAPTURE_IMAGE_TIMEOUT_MS).times(1))
                            .onCaptureProcessStarted(extensionSession, captureRequest);
                    if (captureResultsSupported) {
                        verify(captureCallback,
                                timeout(MULTI_FRAME_CAPTURE_IMAGE_TIMEOUT_MS).times(1))
                                .onCaptureResultAvailable(eq(extensionSession),
                                        eq(captureRequest), any(TotalCaptureResult.class));
                    }
                    verify(captureCallback, timeout(MULTI_FRAME_CAPTURE_IMAGE_TIMEOUT_MS).times(1))
                            .onCaptureSequenceCompleted(extensionSession,
                                    captureSequenceId);
                    verify(captureCallback, times(0))
                            .onCaptureSequenceAborted(any(CameraExtensionSession.class),
                                    anyInt());
                    verify(captureCallback, times(0))
                            .onCaptureFailed(any(CameraExtensionSession.class),
                                    any(CaptureRequest.class));
                    verify(captureCallback, times(0))
                            .onCaptureFailed(any(CameraExtensionSession.class),
                                    any(CaptureRequest.class), anyInt());

                    extensionSession.stopRepeating();

                    verify(repeatingCallbackMock,
                            timeout(MULTI_FRAME_CAPTURE_IMAGE_TIMEOUT_MS).times(1))
                            .onCaptureSequenceCompleted(extensionSession, repeatingSequenceId);

                    verify(repeatingCallbackMock, times(0))
                            .onCaptureSequenceAborted(any(CameraExtensionSession.class),
                                    anyInt());

                    extensionSession.close();

                    sessionListener.getStateWaiter().waitForState(
                            BlockingExtensionSessionCallback.SESSION_CLOSED,
                            SESSION_CLOSE_TIMEOUT_MS);

                    assertTrue("The sum of onCaptureProcessStarted and onCaptureFailed" +
                                    " callbacks must be greater or equal than the number of calls" +
                                    " to onCaptureStarted!",
                            repeatingCaptureCallback.getTotalFramesArrived() +
                                    repeatingCaptureCallback.getTotalFramesFailed() >=
                            repeatingCaptureCallback.getTotalFramesStarted());
                    assertTrue(String.format("The last repeating request surface timestamp " +
                                    "%d must be less than or equal to the last " +
                                    "onCaptureStarted " +
                                    "timestamp %d", mSurfaceTexture.getTimestamp(),
                            repeatingCaptureCallback.getLastTimestamp()),
                            mSurfaceTexture.getTimestamp() <=
                                    repeatingCaptureCallback.getLastTimestamp());

                } finally {
                    mTestRule.closeDevice(id);
                    texturedSurface.release();
                    extensionImageReader.close();
                }
            }
        }
    }

    // Test case for eyes free videography extension mode
    @Test
    @RequiresFlagsEnabled(com.android.internal.camera.flags.Flags.FLAG_CONCERT_MODE)
    public void testEyesFreeExtension() throws Exception {
        for (String id : getCameraIdsUnderTest()) {
            StaticMetadata staticMeta =
                    new StaticMetadata(mTestRule.getCameraManager().getCameraCharacteristics(id));
            if (!staticMeta.isColorOutputSupported()) {
                continue;
            }
            updatePreviewSurfaceTexture();
            CameraExtensionCharacteristics extensionChars =
                    mTestRule.getCameraManager().getCameraExtensionCharacteristics(id);
            List<Integer> supportedExtensions = extensionChars.getSupportedExtensions();

            if (!supportedExtensions.contains(
                    CameraExtensionCharacteristics.EXTENSION_EYES_FREE_VIDEOGRAPHY)) {
                continue;
            }

            int extension = CameraExtensionCharacteristics.EXTENSION_EYES_FREE_VIDEOGRAPHY;
            List<Size> extensionSizes = extensionChars.getExtensionSupportedSizes(extension,
                    mSurfaceTexture.getClass());
            Size maxSize =
                    CameraTestUtils.getMaxSize(extensionSizes.toArray(new Size[0]));
            mSurfaceTexture.setDefaultBufferSize(maxSize.getWidth(),
                    maxSize.getHeight());
            Surface texturedSurface = new Surface(mSurfaceTexture);

            List<OutputConfiguration> outputConfigs = new ArrayList<>();
            outputConfigs.add(new OutputConfiguration(texturedSurface));

            BlockingExtensionSessionCallback sessionListener =
                    new BlockingExtensionSessionCallback(mock(
                            CameraExtensionSession.StateCallback.class));
            ExtensionSessionConfiguration configuration =
                    new ExtensionSessionConfiguration(extension, outputConfigs,
                            new HandlerExecutor(mTestRule.getHandler()),
                            sessionListener);

            Set<CaptureResult.Key> supportedResultKeys =
                    extensionChars.getAvailableCaptureResultKeys(extension);
            Set<CaptureRequest.Key> supportedRequestKeys =
                    extensionChars.getAvailableCaptureRequestKeys(extension);

            CameraTestUtils.checkKeysAreSupported(Arrays.asList(
                    EYES_FREE_REQUEST_SET, FOCUS_CAPTURE_REQUEST_SET,
                    ZOOM_CAPTURE_REQUEST_SET), supportedRequestKeys, true);
            CameraTestUtils.checkKeysAreSupported(Arrays.asList(
                    EYES_FREE_RESULT_SET, FOCUS_CAPTURE_RESULT_SET,
                    ZOOM_CAPTURE_RESULT_SET), supportedResultKeys, true);

            if (supportedRequestKeys.contains(ExtensionCaptureRequest.EFV_AUTO_ZOOM)) {
                CameraTestUtils.checkKeysAreSupported(EYES_FREE_AUTO_ZOOM_REQUEST_SET,
                        supportedRequestKeys, true);
                CameraTestUtils.checkKeysAreSupported(EYES_FREE_AUTO_ZOOM_RESULT_SET,
                        supportedResultKeys, true);
            }

            try {
                mTestRule.openDevice(id);
                CameraDevice camera = mTestRule.getCamera();
                camera.createExtensionSession(configuration);
                CameraExtensionSession extensionSession =
                        sessionListener.waitAndGetSession(
                            SESSION_CONFIGURE_TIMEOUT_MS);
                assertNotNull(extensionSession);

                CameraExtensionSession.ExtensionCaptureCallback captureCallbackMock =
                        mock(CameraExtensionSession.ExtensionCaptureCallback.class);
                SimpleCaptureCallback simpleCaptureCallback =
                        new SimpleCaptureCallback(extension, captureCallbackMock,
                                extensionChars.getAvailableCaptureResultKeys(extension),
                                mCollector, staticMeta);

                if (supportedRequestKeys.contains(ExtensionCaptureRequest.EFV_AUTO_ZOOM)) {
                    CaptureRequest.Builder captureBuilder =
                            mTestRule.getCamera().createCaptureRequest(
                                android.hardware.camera2.CameraDevice.TEMPLATE_PREVIEW);
                    captureBuilder.addTarget(texturedSurface);
                    captureBuilder.set(ExtensionCaptureRequest.EFV_AUTO_ZOOM, true);
                    captureBuilder.set(ExtensionCaptureRequest.EFV_STABILIZATION_MODE,
                            ExtensionCaptureRequest.EFV_STABILIZATION_MODE_LOCKED);
                    CaptureRequest request = captureBuilder.build();

                    int seqId = extensionSession.setRepeatingRequest(request,
                            new HandlerExecutor(mTestRule.getHandler()),
                            simpleCaptureCallback);
                    assertTrue(seqId > 0);

                    verify(captureCallbackMock,
                            timeout(REPEATING_REQUEST_TIMEOUT_MS).atLeastOnce())
                            .onCaptureResultAvailable(eq(extensionSession), eq(request),
                            any(TotalCaptureResult.class));

                    captureBuilder.set(ExtensionCaptureRequest.EFV_AUTO_ZOOM, false);
                    request = captureBuilder.build();

                    seqId = extensionSession.setRepeatingRequest(request,
                            new HandlerExecutor(mTestRule.getHandler()),
                            simpleCaptureCallback);
                    assertTrue(seqId > 0);

                    verify(captureCallbackMock,
                            timeout(REPEATING_REQUEST_TIMEOUT_MS).atLeastOnce())
                            .onCaptureResultAvailable(eq(extensionSession), eq(request),
                            any(TotalCaptureResult.class));
                }

                CaptureRequest.Builder captureBuilder =
                        mTestRule.getCamera().createCaptureRequest(
                                android.hardware.camera2.CameraDevice.TEMPLATE_PREVIEW);
                captureBuilder.addTarget(texturedSurface);

                Range<Float> paddingZoomFactorRange =
                        extensionChars.get(extension,
                        CameraExtensionCharacteristics.EFV_PADDING_ZOOM_FACTOR_RANGE);
                Float paddingZoomFactor = (paddingZoomFactorRange.getUpper()
                        + paddingZoomFactorRange.getLower()) / 2;
                captureBuilder.set(ExtensionCaptureRequest.EFV_PADDING_ZOOM_FACTOR,
                        paddingZoomFactor);
                captureBuilder.set(ExtensionCaptureRequest.EFV_STABILIZATION_MODE,
                        ExtensionCaptureRequest.EFV_STABILIZATION_MODE_LOCKED);
                CaptureRequest request = captureBuilder.build();

                int seqId = extensionSession.setRepeatingRequest(request,
                        new HandlerExecutor(mTestRule.getHandler()),
                        simpleCaptureCallback);
                assertTrue(seqId > 0);

                verify(captureCallbackMock,
                        timeout(REPEATING_REQUEST_TIMEOUT_MS).atLeastOnce())
                        .onCaptureResultAvailable(eq(extensionSession), eq(request),
                                any(TotalCaptureResult.class));

                verify(captureCallbackMock,
                        timeout(REPEATING_REQUEST_TIMEOUT_MS).atLeastOnce())
                        .onCaptureStarted(eq(extensionSession), eq(request), anyLong());
                verify(captureCallbackMock,
                        timeout(REPEATING_REQUEST_TIMEOUT_MS).atLeastOnce())
                        .onCaptureProcessStarted(extensionSession, request);

                extensionSession.stopRepeating();

                verify(captureCallbackMock,
                        timeout(MULTI_FRAME_CAPTURE_IMAGE_TIMEOUT_MS).times(1))
                        .onCaptureSequenceCompleted(extensionSession, seqId);

                verify(captureCallbackMock, times(0))
                        .onCaptureSequenceAborted(any(CameraExtensionSession.class),
                        anyInt());

                extensionSession.close();

                sessionListener.getStateWaiter().waitForState(
                        BlockingExtensionSessionCallback.SESSION_CLOSED,
                        SESSION_CLOSE_TIMEOUT_MS);

                assertTrue("The sum of onCaptureProcessStarted and onCaptureFailed"
                                + " callbacks must be greater or equal than the number of calls"
                                + " to onCaptureStarted!",
                        simpleCaptureCallback.getTotalFramesArrived()
                        + simpleCaptureCallback.getTotalFramesFailed()
                        >= simpleCaptureCallback.getTotalFramesStarted());
                assertTrue(String.format("The last repeating request surface timestamp "
                                + "%d must be less than or equal to the last "
                                + "onCaptureStarted "
                                + "timestamp %d", mSurfaceTexture.getTimestamp(),
                        simpleCaptureCallback.getLastTimestamp()),
                        mSurfaceTexture.getTimestamp()
                        <= simpleCaptureCallback.getLastTimestamp());
            } finally {
                mTestRule.closeDevice(id);
                texturedSurface.release();
            }
        }
    }

    private void verifyJpegOrientation(Image img, Size jpegSize, int requestedOrientation,
                int captureFormat)
            throws IOException {
        byte[] blobBuffer = getDataFromImage(img);
        String blobFilename = mTestRule.getDebugFileNameBase() + "/verifyJpegKeys.jpeg";
        dumpFile(blobFilename, blobBuffer);
        ExifInterface exif = new ExifInterface(blobFilename);
        int exifWidth = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, /*defaultValue*/0);
        int exifHeight = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, /*defaultValue*/0);
        Size exifSize = new Size(exifWidth, exifHeight);
        int exifOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                /*defaultValue*/ ExifInterface.ORIENTATION_UNDEFINED);
        final int ORIENTATION_MIN = ExifInterface.ORIENTATION_UNDEFINED;
        final int ORIENTATION_MAX = ExifInterface.ORIENTATION_ROTATE_270;
        assertTrue(String.format("Exif orientation must be in range of [%d, %d]",
                ORIENTATION_MIN, ORIENTATION_MAX),
                exifOrientation >= ORIENTATION_MIN && exifOrientation <= ORIENTATION_MAX);

        /**
         * Device captured image doesn't respect the requested orientation,
         * which means it rotates the image buffer physically. Then we
         * should swap the jpegSize width/height accordingly to compare.
         */
        boolean deviceRotatedImage = exifOrientation == ExifInterface.ORIENTATION_UNDEFINED;

        if (deviceRotatedImage) {
            // Case 1.
            boolean needSwap = (requestedOrientation % 180 == 90);
            if (needSwap) {
                jpegSize = new Size(jpegSize.getHeight(), jpegSize.getWidth());
            }
        } else {
            // Case 2.
            assertEquals("Exif orientation should match requested orientation",
                    requestedOrientation, getExifOrientationInDegree(exifOrientation));
        }

        assertEquals("Exif size should match jpeg capture size", jpegSize, exifSize);

        byte[] data = getDataFromImage(img);
        assertTrue("Invalid image data", data != null && data.length > 0);

        switch (captureFormat) {
            case ImageFormat.JPEG:
                validateJpegData(data, jpegSize.getWidth(), jpegSize.getHeight(),
                        null /*filePath*/);
                break;
            case ImageFormat.JPEG_R:
                validateJpegData(data, jpegSize.getWidth(), jpegSize.getHeight(),
                        null /*filePath*/, null /*colorSpace*/, true /*gainMapPresent*/);
                break;
            default:
                throw new UnsupportedOperationException("Unsupported format for JPEG validation: "
                        + captureFormat);
        }
    }

    private static int getExifOrientationInDegree(int exifOrientation) {
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
                fail("It is impossible to get non 0, 90, 180, 270 degress exif" +
                        "info based on the request orientation range");
                return -1;
        }
    }

    public static class SimpleCaptureCallback
            extends CameraExtensionSession.ExtensionCaptureCallback {
        private long mLastTimestamp = -1;
        private int mNumFramesArrived = 0;
        private int mNumFramesStarted = 0;
        private int mNumFramesFailed = 0;
        private int mLastProgressValue = -1;
        private boolean mNonIncreasingTimestamps = false;
        private HashSet<Long> mExpectedResultTimestamps = new HashSet<>();
        private StaticMetadata mStaticInfo;
        private final CameraExtensionSession.ExtensionCaptureCallback mProxy;
        private final AutoFocusStateMachine mAFStateMachine;
        private final FlashStateListener mFlashStateListener;
        private final AutoExposureStateListener mAEStateListener;
        private final HashSet<CaptureResult.Key> mSupportedResultKeys;
        private final CameraErrorCollector mCollector;
        private final boolean mPerFrameControl;
        private final int mExtensionType;

        public SimpleCaptureCallback(int extensionType,
                CameraExtensionSession.ExtensionCaptureCallback proxy,
                Set<CaptureResult.Key> supportedResultKeys, CameraErrorCollector errorCollector) {
            this(extensionType, proxy, supportedResultKeys, errorCollector, null /*afListener*/,
                    null /*flashState*/, null /*aeState*/, false /*perFrameControl*/);
        }

        public SimpleCaptureCallback(int extensionType,
                CameraExtensionSession.ExtensionCaptureCallback proxy,
                Set<CaptureResult.Key> supportedResultKeys, CameraErrorCollector errorCollector,
                StaticMetadata staticInfo) {
            this(extensionType, proxy, supportedResultKeys, errorCollector, null /*afListener*/,
                    null /*flashState*/, null /*aeState*/, false /*perFrameControl*/);
            mStaticInfo = staticInfo;
        }

        public SimpleCaptureCallback(int extensionType,
                CameraExtensionSession.ExtensionCaptureCallback proxy,
                Set<CaptureResult.Key> supportedResultKeys, CameraErrorCollector errorCollector,
                AutoFocusStateMachine afState, FlashStateListener flashState,
                AutoExposureStateListener aeState, boolean perFrameControl) {
            mProxy = proxy;
            mSupportedResultKeys = new HashSet<>(supportedResultKeys);
            mCollector = errorCollector;
            mAFStateMachine = afState;
            mFlashStateListener = flashState;
            mAEStateListener = aeState;
            mPerFrameControl = perFrameControl;
            mExtensionType = extensionType;
        }

        public void resetCaptureProgress() {
            mLastProgressValue = -1;
        }

        @Override
        public void onCaptureStarted(CameraExtensionSession session,
                                     CaptureRequest request, long timestamp) {
            mExpectedResultTimestamps.add(timestamp);
            if (timestamp < mLastTimestamp) {
                mNonIncreasingTimestamps = true;
            }
            mLastTimestamp = timestamp;
            mNumFramesStarted++;
            if (mProxy != null) {
                mProxy.onCaptureStarted(session, request, timestamp);
            }
        }

        @Override
        public void onCaptureProcessStarted(CameraExtensionSession session,
                                            CaptureRequest request) {
            mNumFramesArrived++;
            if (mProxy != null) {
                mProxy.onCaptureProcessStarted(session, request);
            }
        }

        @Override
        public void onCaptureFailed(CameraExtensionSession session,
                                    CaptureRequest request) {
            mNumFramesFailed++;
            if (mProxy != null) {
                mProxy.onCaptureFailed(session, request);
            }
        }

        @Override
        public void onCaptureFailed(CameraExtensionSession session,
                CaptureRequest request, int failure) {
            mNumFramesFailed++;
            if (mProxy != null) {
                mProxy.onCaptureFailed(session, request, failure);
            }
        }

        @Override
        public void onCaptureSequenceAborted(CameraExtensionSession session,
                                             int sequenceId) {
            if (mProxy != null) {
                mProxy.onCaptureSequenceAborted(session, sequenceId);
            }
        }

        @Override
        public void onCaptureSequenceCompleted(CameraExtensionSession session,
                                               int sequenceId) {
            if (mProxy != null) {
                mProxy.onCaptureSequenceCompleted(session, sequenceId);
            }
        }

        @Override
        public void onCaptureProcessProgressed(CameraExtensionSession session,
                CaptureRequest request, int progress) {
            if ((progress < 0) || (progress > 100)) {
                mCollector.addMessage("Capture progress invalid value: " + progress);
                return;
            }
            if (mLastProgressValue >= progress) {
                mCollector.addMessage("Unexpected progress value: " + progress +
                        " last progress value: " + mLastProgressValue);
                return;
            }
            mLastProgressValue = progress;
            if (mProxy != null) {
                mProxy.onCaptureProcessProgressed(session, request, progress);
            }
        }

        @Override
        public void  onCaptureResultAvailable(CameraExtensionSession session,
                CaptureRequest request, TotalCaptureResult result) {
            final float METERING_REGION_ERROR_PERCENT_DELTA = 0.05f;
            if (mSupportedResultKeys.isEmpty()) {
                mCollector.addMessage("Capture results not supported, but " +
                        "onCaptureResultAvailable still got triggered!");
                return;
            }

            List<CaptureResult.Key<?>> resultKeys = result.getKeys();
            for (CaptureResult.Key<?> resultKey : resultKeys) {
                mCollector.expectTrue("Capture result " + resultKey + " is not among the"
                        + " supported result keys!", mSupportedResultKeys.contains(resultKey));
            }

            Long timeStamp = result.get(CaptureResult.SENSOR_TIMESTAMP);
            assertNotNull(timeStamp);
            assertTrue("Capture result sensor timestamp: " + timeStamp + " must match "
                            + " with the timestamp passed to onCaptureStarted!",
                    mExpectedResultTimestamps.contains(timeStamp));

            Integer currentType = result.get(CaptureResult.EXTENSION_CURRENT_TYPE);
            if (currentType != null) {
                mCollector.expectNotEquals("The reported extension type cannot be set to AUTO!",
                        CameraExtensionCharacteristics.EXTENSION_AUTOMATIC, currentType);
                if (mExtensionType == CameraExtensionCharacteristics.EXTENSION_AUTOMATIC) {
                    Integer expectedValues[] = {
                            CameraExtensionCharacteristics.EXTENSION_BOKEH,
                            CameraExtensionCharacteristics.EXTENSION_HDR,
                            CameraExtensionCharacteristics.EXTENSION_NIGHT,
                            CameraExtensionCharacteristics.EXTENSION_FACE_RETOUCH};
                    mCollector.expectContains("Unexpected extension type result: "
                            + currentType, expectedValues, currentType);
                } else {
                    mCollector.expectEquals("Unexpected extension type result: " + currentType
                            + " expected: " + mExtensionType, mExtensionType, currentType);
                }
            }

            Integer strength = request.get(CaptureRequest.EXTENSION_STRENGTH);
            if (strength != null) {
                Integer resultStrength = result.get(CaptureResult.EXTENSION_STRENGTH);
                mCollector.expectTrue("Request extension strength: " + strength +
                                " doesn't match with result: " + resultStrength,
                        strength.equals(resultStrength));
            }

            Integer jpegOrientation = request.get(CaptureRequest.JPEG_ORIENTATION);
            if (jpegOrientation != null) {
                Integer resultJpegOrientation = result.get(CaptureResult.JPEG_ORIENTATION);
                mCollector.expectTrue("Request Jpeg orientation: " + jpegOrientation +
                        " doesn't match with result: " + resultJpegOrientation,
                        jpegOrientation.equals(resultJpegOrientation));
            }

            Byte jpegQuality = request.get(CaptureRequest.JPEG_QUALITY);
            if (jpegQuality != null) {
                Byte resultJpegQuality = result.get(CaptureResult.JPEG_QUALITY);
                mCollector.expectTrue("Request Jpeg quality: " + jpegQuality +
                        " doesn't match with result: " + resultJpegQuality,
                        jpegQuality.equals(resultJpegQuality));
            }

            if (resultKeys.contains(CaptureResult.CONTROL_ZOOM_RATIO) && mPerFrameControl) {
                Float zoomRatio = request.get(CaptureRequest.CONTROL_ZOOM_RATIO);
                if (zoomRatio != null) {
                    Float resultZoomRatio = result.get(CaptureResult.CONTROL_ZOOM_RATIO);
                    mCollector.expectTrue(
                            String.format("Request and result zoom ratio should be similar " +
                                    "(requested = %f, result = %f", zoomRatio, resultZoomRatio),
                            Math.abs(zoomRatio - resultZoomRatio) / zoomRatio <= ZOOM_ERROR_MARGIN);
                }
            }

            if (mFlashStateListener != null) {
                Integer flashMode = request.get(CaptureRequest.FLASH_MODE);
                if ((flashMode != null) && mPerFrameControl) {
                    Integer resultFlashMode = result.get(CaptureResult.FLASH_MODE);
                    mCollector.expectTrue("Request flash mode: " + flashMode +
                                    " doesn't match with result: " + resultFlashMode,
                            flashMode.equals(resultFlashMode));
                }

                Integer flashState = result.get(CaptureResult.FLASH_STATE);
                if (flashState != null) {
                    switch (flashState) {
                        case CaptureResult.FLASH_STATE_UNAVAILABLE:
                            mFlashStateListener.onUnavailable();
                            break;
                        case CaptureResult.FLASH_STATE_FIRED:
                            mFlashStateListener.onFired();
                            break;
                        case CaptureResult.FLASH_STATE_CHARGING:
                            mFlashStateListener.onCharging();
                            break;
                        case CaptureResult.FLASH_STATE_PARTIAL:
                            mFlashStateListener.onPartial();
                            break;
                        case CaptureResult.FLASH_STATE_READY:
                            mFlashStateListener.onReady();
                            break;
                        default:
                            mCollector.addMessage("Unexpected flash state: " + flashState);
                    }
                }
            }

            if (mAEStateListener != null) {
                Integer aeMode = request.get(CaptureRequest.CONTROL_AE_MODE);
                if ((aeMode != null) && mPerFrameControl) {
                    Integer resultAeMode = result.get(CaptureResult.CONTROL_AE_MODE);
                    mCollector.expectTrue("Request AE mode: " + aeMode +
                                    " doesn't match with result: " + resultAeMode,
                            aeMode.equals(resultAeMode));
                }

                Boolean aeLock = request.get(CaptureRequest.CONTROL_AE_LOCK);
                if ((aeLock != null) && mPerFrameControl) {
                    Boolean resultAeLock = result.get(CaptureResult.CONTROL_AE_LOCK);
                    mCollector.expectTrue("Request AE lock: " + aeLock +
                                    " doesn't match with result: " + resultAeLock,
                            aeLock.equals(resultAeLock));
                }

                Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                if (aeState != null) {
                    switch (aeState) {
                        case CaptureResult.CONTROL_AE_STATE_CONVERGED:
                            mAEStateListener.onConverged();
                            break;
                        case CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED:
                            mAEStateListener.onFlashRequired();
                            break;
                        case CaptureResult.CONTROL_AE_STATE_INACTIVE:
                            mAEStateListener.onInactive();
                            break;
                        case CaptureResult.CONTROL_AE_STATE_LOCKED:
                            mAEStateListener.onLocked();
                            break;
                        case CaptureResult.CONTROL_AE_STATE_PRECAPTURE:
                            mAEStateListener.onPrecapture();
                            break;
                        case CaptureResult.CONTROL_AE_STATE_SEARCHING:
                            mAEStateListener.onSearching();
                            break;
                        default:
                            mCollector.addMessage("Unexpected AE state: " + aeState);
                    }
                }
            }

            if (mAFStateMachine != null) {
                Integer afMode = request.get(CaptureRequest.CONTROL_AF_MODE);
                if ((afMode != null) && mPerFrameControl) {
                    Integer resultAfMode = result.get(CaptureResult.CONTROL_AF_MODE);
                    mCollector.expectTrue("Request AF mode: " + afMode +
                                    " doesn't match with result: " + resultAfMode,
                            afMode.equals(resultAfMode));
                }

                MeteringRectangle[] afRegions = request.get(CaptureRequest.CONTROL_AF_REGIONS);
                if ((afRegions != null) && mPerFrameControl) {
                    MeteringRectangle[] resultAfRegions = result.get(
                            CaptureResult.CONTROL_AF_REGIONS);
                    mCollector.expectMeteringRegionsAreSimilar(
                            "AF regions in result and request should be similar",
                            afRegions, resultAfRegions, METERING_REGION_ERROR_PERCENT_DELTA);
                }

                Integer afTrigger = request.get(CaptureRequest.CONTROL_AF_TRIGGER);
                if ((afTrigger != null) && mPerFrameControl) {
                    Integer resultAfTrigger = result.get(CaptureResult.CONTROL_AF_TRIGGER);
                    mCollector.expectTrue("Request AF trigger: " + afTrigger +
                                    " doesn't match with result: " + resultAfTrigger,
                            afTrigger.equals(resultAfTrigger));
                }

                mAFStateMachine.onCaptureCompleted(result);
            }

            verifyExtensionSpecificCaptureResults(result, request);

            if (mProxy != null) {
                mProxy.onCaptureResultAvailable(session, request, result);
            }
        }

        public int getTotalFramesArrived() {
            return mNumFramesArrived;
        }

        public int getTotalFramesStarted() {
            return mNumFramesStarted;
        }

        public int getTotalFramesFailed() {
            return mNumFramesFailed;
        }

        public long getLastTimestamp() throws IllegalStateException {
            if (mNonIncreasingTimestamps) {
                throw new IllegalStateException("Non-monotonically increasing timestamps!");
            }
            return mLastTimestamp;
        }

        private void verifyExtensionSpecificCaptureResults(TotalCaptureResult result,
                CaptureRequest request) {
            if (mExtensionType != CameraExtensionCharacteristics.EXTENSION_EYES_FREE_VIDEOGRAPHY) {
                return;
            }

            Boolean enableAutoZoomRequest = request.get(ExtensionCaptureRequest.EFV_AUTO_ZOOM);
            if (enableAutoZoomRequest != null) {
                Boolean enableAutoZoomResult = result.get(ExtensionCaptureResult.EFV_AUTO_ZOOM);
                mCollector.expectTrue("Request auto zoom doesn't match with "
                        + " result auto zoom mode ",
                        enableAutoZoomRequest.equals(enableAutoZoomResult));

                if (!enableAutoZoomRequest) {
                    int[] autoZoomPaddingRegion =
                            result.get(ExtensionCaptureResult.EFV_AUTO_ZOOM_PADDING_REGION);
                    mCollector.expectTrue("Unexpected EFV_AUTO_ZOOM_PADDING_REGION when "
                            + "auto zoom is disabled ", autoZoomPaddingRegion == null);
                }
            }

            Integer stabilizationModeRequest =
                    request.get(ExtensionCaptureRequest.EFV_STABILIZATION_MODE);
            if (stabilizationModeRequest != null) {
                Integer stabilizationModeResult =
                        result.get(ExtensionCaptureResult.EFV_STABILIZATION_MODE);
                mCollector.expectTrue("Request stabilization mode doesn't match with "
                        + " result stabilization mode ",
                        stabilizationModeRequest.equals(stabilizationModeResult));

                if (stabilizationModeRequest
                        == ExtensionCaptureRequest.EFV_STABILIZATION_MODE_LOCKED) {
                    int[] paddingRegion =
                            result.get(ExtensionCaptureResult.EFV_PADDING_REGION);
                    mCollector.expectTrue("Expected EFV_PADDING_REGION when "
                            + " stabilization is in locked mode ", paddingRegion != null);
                    PointF[] targetCoordinates =
                            result.get(ExtensionCaptureResult.EFV_TARGET_COORDINATES);
                    mCollector.expectTrue("Expected EFV_TARGET_COORDINATES when "
                            + " stabilization is in locked mode ", targetCoordinates != null);

                    if (enableAutoZoomRequest != null && enableAutoZoomRequest) {
                        int[] autoZoomPaddingRegion =
                                result.get(ExtensionCaptureResult.EFV_AUTO_ZOOM_PADDING_REGION);
                        mCollector.expectTrue("Expected EFV_AUTO_ZOOM_PADDING_REGION when "
                                + "auto zoom is enabled and stabilization is in locked mode ",
                                autoZoomPaddingRegion != null);
                    }
                }
            }

            Float paddingZoomFactor = result.get(ExtensionCaptureResult.EFV_PADDING_ZOOM_FACTOR);
            if (paddingZoomFactor != null) {
                mCollector.expectTrue("Expected EFV_PADDING_ZOOM_FACTOR > 1 ",
                        paddingZoomFactor > 1);
            }

            if (mStaticInfo == null) {
                return;
            }

            Rect activeArraySize = mStaticInfo.getActiveArraySizeChecked();
            PointF[] targetCoordinates = result.get(ExtensionCaptureResult.EFV_TARGET_COORDINATES);
            if (targetCoordinates != null) {
                for (PointF point: targetCoordinates) {
                    mCollector.expectTrue("Target coordinate not within active array region",
                            activeArraySize.contains((int) Math.ceil(point.x),
                            (int) Math.ceil(point.y)));
                }
            }
        }
    }

    public interface AutoFocusStateListener {
        void onDone(boolean success);
        void onScan();
        void onInactive();
    }

    private class TestAutoFocusProxy implements AutoFocusStateMachine.AutoFocusStateListener {
        private final AutoFocusStateListener mListener;

        TestAutoFocusProxy(AutoFocusStateListener listener) {
            mListener = listener;
        }

        @Override
        public void onAutoFocusSuccess(CaptureResult result, boolean locked) {
            mListener.onDone(true);
        }

        @Override
        public void onAutoFocusFail(CaptureResult result, boolean locked) {
            mListener.onDone(false);
        }

        @Override
        public void onAutoFocusScan(CaptureResult result) {
            mListener.onScan();
        }

        @Override
        public void onAutoFocusInactive(CaptureResult result) {
            mListener.onInactive();
        }
    }

    // Verify that camera extension sessions can support AF and AF metering controls. The test
    // goal is to check that AF related controls and results are supported and can be used to
    // lock the AF state and not to do an exhaustive check of the AF state transitions or manual AF.
    @Test
    public void testAFMetering() throws Exception {
        final int METERING_REGION_SCALE_RATIO = 8;
        final int WAIT_FOR_FOCUS_DONE_TIMEOUT_MS = 6000;
        for (String id : getCameraIdsUnderTest()) {
            StaticMetadata staticMeta =
                    new StaticMetadata(mTestRule.getCameraManager().getCameraCharacteristics(id));
            if (!staticMeta.isColorOutputSupported()) {
                continue;
            }
            if (!staticMeta.hasFocuser()) {
                continue;
            }

            boolean perFrameControl = staticMeta.isPerFrameControlSupported();
            final Rect activeArraySize = staticMeta.getActiveArraySizeChecked();
            int regionWidth = activeArraySize.width() / METERING_REGION_SCALE_RATIO - 1;
            int regionHeight = activeArraySize.height() / METERING_REGION_SCALE_RATIO - 1;
            int centerX = activeArraySize.width() / 2;
            int centerY = activeArraySize.height() / 2;

            // Center region
            MeteringRectangle[] afMeteringRects = {new MeteringRectangle(
                    centerX - regionWidth / 2, centerY - regionHeight / 2,
                    regionWidth, regionHeight,
                    MeteringRectangle.METERING_WEIGHT_MAX)};

            updatePreviewSurfaceTexture();
            CameraExtensionCharacteristics extensionChars =
                    mTestRule.getCameraManager().getCameraExtensionCharacteristics(id);
            List<Integer> supportedExtensions = extensionChars.getSupportedExtensions();
            for (Integer extension : supportedExtensions) {
                Set<CaptureRequest.Key> supportedRequestKeys =
                        extensionChars.getAvailableCaptureRequestKeys(extension);
                // The night extension is required to support AF controls starting with Android V
                if ((Build.VERSION.DEVICE_INITIAL_SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM)
                    && (extension == CameraExtensionCharacteristics.EXTENSION_NIGHT)) {
                    assertTrue(supportedRequestKeys.containsAll(
                            Arrays.asList(FOCUS_CAPTURE_REQUEST_SET)));
                } else if (!supportedRequestKeys.containsAll(
                        Arrays.asList(FOCUS_CAPTURE_REQUEST_SET))) {
                    continue;
                }
                Set<CaptureResult.Key> supportedResultKeys =
                        extensionChars.getAvailableCaptureResultKeys(extension);
                assertTrue(supportedResultKeys.containsAll(
                        Arrays.asList(FOCUS_CAPTURE_RESULT_SET)));

                List<Size> extensionSizes = extensionChars.getExtensionSupportedSizes(extension,
                        mSurfaceTexture.getClass());
                Size maxSize =
                        CameraTestUtils.getMaxSize(extensionSizes.toArray(new Size[0]));
                mSurfaceTexture.setDefaultBufferSize(maxSize.getWidth(),
                        maxSize.getHeight());
                Surface texturedSurface = new Surface(mSurfaceTexture);

                List<OutputConfiguration> outputConfigs = new ArrayList<>();
                outputConfigs.add(new OutputConfiguration(texturedSurface));

                BlockingExtensionSessionCallback sessionListener =
                        new BlockingExtensionSessionCallback(mock(
                                CameraExtensionSession.StateCallback.class));
                ExtensionSessionConfiguration configuration =
                        new ExtensionSessionConfiguration(extension, outputConfigs,
                                new HandlerExecutor(mTestRule.getHandler()),
                                sessionListener);

                try {
                    mTestRule.openDevice(id);
                    CameraDevice camera = mTestRule.getCamera();
                    camera.createExtensionSession(configuration);
                    CameraExtensionSession extensionSession = sessionListener.waitAndGetSession(
                            SESSION_CONFIGURE_TIMEOUT_MS);
                    assertNotNull(extensionSession);

                    // Check passive AF
                    AutoFocusStateListener mockAFListener =
                            mock(AutoFocusStateListener.class);
                    AutoFocusStateMachine afState = new AutoFocusStateMachine(
                            new TestAutoFocusProxy(mockAFListener));
                    CameraExtensionSession.ExtensionCaptureCallback repeatingCallbackMock =
                            mock(CameraExtensionSession.ExtensionCaptureCallback.class);
                    SimpleCaptureCallback repeatingCaptureCallback =
                            new SimpleCaptureCallback(extension, repeatingCallbackMock,
                                    extensionChars.getAvailableCaptureResultKeys(extension),
                                    mCollector, afState, null /*flashState*/,
                                    null /*aeState*/, perFrameControl);

                    CaptureRequest.Builder captureBuilder =
                            mTestRule.getCamera().createCaptureRequest(
                                    android.hardware.camera2.CameraDevice.TEMPLATE_PREVIEW);
                    captureBuilder.addTarget(texturedSurface);
                    afState.setPassiveAutoFocus(true /*picture*/, captureBuilder);
                    captureBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, afMeteringRects);
                    CaptureRequest request = captureBuilder.build();
                    int passiveSequenceId = extensionSession.setRepeatingRequest(request,
                            new HandlerExecutor(mTestRule.getHandler()), repeatingCaptureCallback);
                    assertTrue(passiveSequenceId > 0);

                    verify(repeatingCallbackMock,
                            timeout(REPEATING_REQUEST_TIMEOUT_MS).atLeastOnce())
                            .onCaptureResultAvailable(eq(extensionSession), eq(request),
                                    any(TotalCaptureResult.class));

                    verify(mockAFListener,
                            timeout(WAIT_FOR_FOCUS_DONE_TIMEOUT_MS).atLeastOnce())
                            .onDone(anyBoolean());

                    // Check active AF
                    mockAFListener = mock(AutoFocusStateListener.class);
                    CameraExtensionSession.ExtensionCaptureCallback callbackMock = mock(
                            CameraExtensionSession.ExtensionCaptureCallback.class);
                    AutoFocusStateMachine activeAFState = new AutoFocusStateMachine(
                            new TestAutoFocusProxy(mockAFListener));
                    CameraExtensionSession.ExtensionCaptureCallback captureCallback =
                            new SimpleCaptureCallback(extension, callbackMock,
                                    extensionChars.getAvailableCaptureResultKeys(extension),
                                    mCollector, activeAFState, null /*flashState*/,
                                    null /*aeState*/, perFrameControl);

                    CaptureRequest.Builder triggerBuilder =
                            mTestRule.getCamera().createCaptureRequest(
                                    android.hardware.camera2.CameraDevice.TEMPLATE_PREVIEW);
                    triggerBuilder.addTarget(texturedSurface);
                    afState.setActiveAutoFocus(captureBuilder, triggerBuilder);
                    afState.unlockAutoFocus(captureBuilder, triggerBuilder);
                    triggerBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, afMeteringRects);
                    request = captureBuilder.build();
                    int activeSequenceId = extensionSession.setRepeatingRequest(request,
                            new HandlerExecutor(mTestRule.getHandler()), captureCallback);
                    assertTrue((activeSequenceId > 0) &&
                            (activeSequenceId != passiveSequenceId));

                    verify(callbackMock,
                            timeout(REPEATING_REQUEST_TIMEOUT_MS).atLeastOnce())
                            .onCaptureResultAvailable(eq(extensionSession), eq(request),
                                    any(TotalCaptureResult.class));

                    CaptureRequest triggerRequest = triggerBuilder.build();
                    reset(mockAFListener);
                    int triggerSequenceId = extensionSession.capture(triggerRequest,
                            new HandlerExecutor(mTestRule.getHandler()), captureCallback);
                    assertTrue((triggerSequenceId > 0) &&
                            (activeSequenceId != triggerSequenceId) &&
                            (triggerSequenceId != passiveSequenceId));

                    verify(callbackMock,
                            timeout(REPEATING_REQUEST_TIMEOUT_MS).atLeastOnce())
                            .onCaptureResultAvailable(eq(extensionSession), eq(triggerRequest),
                                    any(TotalCaptureResult.class));

                    afState.lockAutoFocus(captureBuilder, triggerBuilder);
                    triggerRequest = triggerBuilder.build();
                    reset(mockAFListener);
                    extensionSession.capture(triggerRequest,
                            new HandlerExecutor(mTestRule.getHandler()), captureCallback);

                    verify(callbackMock,
                            timeout(REPEATING_REQUEST_TIMEOUT_MS).atLeastOnce())
                            .onCaptureResultAvailable(eq(extensionSession), eq(triggerRequest),
                                    any(TotalCaptureResult.class));

                    verify(mockAFListener, timeout(WAIT_FOR_FOCUS_DONE_TIMEOUT_MS)
                            .atLeast(1)).onDone(anyBoolean());

                    extensionSession.stopRepeating();

                    extensionSession.close();

                    sessionListener.getStateWaiter().waitForState(
                            BlockingExtensionSessionCallback.SESSION_CLOSED,
                            SESSION_CLOSE_TIMEOUT_MS);
                } finally {
                    mTestRule.closeDevice(id);
                    texturedSurface.release();
                }
            }
        }
    }

    // Verify that camera extension sessions can support the zoom ratio control.
    @Test
    public void testZoomRatio() throws Exception {
        final int ZOOM_RATIO_STEPS = 10;
        for (String id : getCameraIdsUnderTest()) {
            StaticMetadata staticMeta =
                    new StaticMetadata(mTestRule.getCameraManager().getCameraCharacteristics(id));
            if (!staticMeta.isColorOutputSupported()) {
                continue;
            }

            CameraExtensionCharacteristics extensionChars =
                    mTestRule.getCameraManager().getCameraExtensionCharacteristics(id);

            updatePreviewSurfaceTexture();
            List<Integer> supportedExtensions = extensionChars.getSupportedExtensions();
            for (Integer extension : supportedExtensions) {
                final Range<Float> zoomRatioRange;
                final float maxZoom;
                if (Build.VERSION.DEVICE_INITIAL_SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    zoomRatioRange =
                            extensionChars.get(extension,
                                    CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
                    assertNotNull(
                            "Zoom ratio range must be present in CameraExtensionCharacteristics",
                            zoomRatioRange);
                    maxZoom = zoomRatioRange.getUpper();
                } else {
                    zoomRatioRange = staticMeta.getZoomRatioRangeChecked();
                    maxZoom = staticMeta.getAvailableMaxDigitalZoomChecked();
                }

                if (zoomRatioRange.getUpper().equals(zoomRatioRange.getLower())) {
                    continue;
                }

                if (Math.abs(maxZoom - 1.0f) < ZOOM_ERROR_MARGIN) {
                    return;
                }

                Float zoomStep  =
                        (zoomRatioRange.getUpper() - zoomRatioRange.getLower()) / ZOOM_RATIO_STEPS;
                if (zoomStep < ZOOM_ERROR_MARGIN) {
                    continue;
                }

                ArrayList<Float> candidateZoomRatios = new ArrayList<>(ZOOM_RATIO_STEPS);
                for (int step = 0; step < (ZOOM_RATIO_STEPS - 1); step++) {
                    candidateZoomRatios.add(step, zoomRatioRange.getLower() + step * zoomStep);
                }
                candidateZoomRatios.add(ZOOM_RATIO_STEPS - 1, zoomRatioRange.getUpper());

                Set<CaptureRequest.Key> supportedRequestKeys =
                        extensionChars.getAvailableCaptureRequestKeys(extension);
                // The Night extension is required to support zoom controls start with Android V
                if ((Build.VERSION.DEVICE_INITIAL_SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM)
                        && (extension == CameraExtensionCharacteristics.EXTENSION_NIGHT)) {
                    assertTrue(supportedRequestKeys.containsAll(
                            Arrays.asList(ZOOM_CAPTURE_REQUEST_SET)));
                } else if (!supportedRequestKeys.containsAll(
                        Arrays.asList(ZOOM_CAPTURE_REQUEST_SET))) {
                    continue;
                }
                Set<CaptureResult.Key> supportedResultKeys =
                        extensionChars.getAvailableCaptureResultKeys(extension);
                assertTrue(supportedResultKeys.containsAll(
                        Arrays.asList(ZOOM_CAPTURE_RESULT_SET)));

                List<Size> extensionSizes = extensionChars.getExtensionSupportedSizes(extension,
                        mSurfaceTexture.getClass());
                Size maxSize =
                        CameraTestUtils.getMaxSize(extensionSizes.toArray(new Size[0]));
                mSurfaceTexture.setDefaultBufferSize(maxSize.getWidth(),
                        maxSize.getHeight());
                Surface texturedSurface = new Surface(mSurfaceTexture);

                List<OutputConfiguration> outputConfigs = new ArrayList<>();
                outputConfigs.add(new OutputConfiguration(texturedSurface));

                BlockingExtensionSessionCallback sessionListener =
                        new BlockingExtensionSessionCallback(mock(
                                CameraExtensionSession.StateCallback.class));
                ExtensionSessionConfiguration configuration =
                        new ExtensionSessionConfiguration(extension, outputConfigs,
                                new HandlerExecutor(mTestRule.getHandler()),
                                sessionListener);

                try {
                    mTestRule.openDevice(id);
                    CameraDevice camera = mTestRule.getCamera();
                    camera.createExtensionSession(configuration);
                    CameraExtensionSession extensionSession = sessionListener.waitAndGetSession(
                            SESSION_CONFIGURE_TIMEOUT_MS);
                    assertNotNull(extensionSession);

                    CameraExtensionSession.ExtensionCaptureCallback repeatingCallbackMock =
                            mock(CameraExtensionSession.ExtensionCaptureCallback.class);
                    SimpleCaptureCallback repeatingCaptureCallback =
                            new SimpleCaptureCallback(extension, repeatingCallbackMock,
                                    extensionChars.getAvailableCaptureResultKeys(extension),
                                    mCollector);

                    CaptureRequest.Builder captureBuilder =
                            mTestRule.getCamera().createCaptureRequest(
                                    android.hardware.camera2.CameraDevice.TEMPLATE_PREVIEW);
                    captureBuilder.addTarget(texturedSurface);
                    for (Float currentZoomRatio : candidateZoomRatios) {
                        captureBuilder.set(CaptureRequest.CONTROL_ZOOM_RATIO, currentZoomRatio);
                        CaptureRequest request = captureBuilder.build();

                        int seqId = extensionSession.setRepeatingRequest(request,
                                new HandlerExecutor(mTestRule.getHandler()),
                                repeatingCaptureCallback);
                        assertTrue(seqId > 0);

                        verify(repeatingCallbackMock,
                                timeout(REPEATING_REQUEST_TIMEOUT_MS).atLeastOnce())
                                .onCaptureResultAvailable(eq(extensionSession), eq(request),
                                        any(TotalCaptureResult.class));
                    }

                    extensionSession.stopRepeating();

                    extensionSession.close();

                    sessionListener.getStateWaiter().waitForState(
                            BlockingExtensionSessionCallback.SESSION_CLOSED,
                            SESSION_CLOSE_TIMEOUT_MS);
                } finally {
                    mTestRule.closeDevice(id);
                    texturedSurface.release();
                }
            }
        }
    }

    public interface FlashStateListener {
        public void onFired();
        public void onReady();
        public void onCharging();
        public void onPartial();
        public void onUnavailable();
    }

    public interface AutoExposureStateListener {
      public void onInactive();
      public void onSearching();
      public void onConverged();
      public void onLocked();
      public void onFlashRequired();
      public void onPrecapture();
    }

    // Verify that camera extension sessions can support Flash related controls. The test
    // goal is to check that Flash controls and results are supported and can be used to
    // turn on torch, run the pre-capture sequence and active the main flash.
    @Test
    public void testFlash() throws Exception {
        final CaptureRequest.Key[] FLASH_CAPTURE_REQUEST_SET = {CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_LOCK,
                CaptureRequest.FLASH_MODE};
        final CaptureResult.Key[] FLASH_CAPTURE_RESULT_SET = {CaptureResult.CONTROL_AE_MODE,
                CaptureResult.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureResult.CONTROL_AE_LOCK,
                CaptureResult.CONTROL_AE_STATE, CaptureResult.FLASH_MODE,
                CaptureResult.FLASH_STATE};
        for (String id : getCameraIdsUnderTest()) {
            StaticMetadata staticMeta =
                    new StaticMetadata(mTestRule.getCameraManager().getCameraCharacteristics(id));
            if (!staticMeta.isColorOutputSupported()) {
                continue;
            }
            if (!staticMeta.hasFlash()) {
                continue;
            }

            boolean perFrameControl = staticMeta.isPerFrameControlSupported();
            updatePreviewSurfaceTexture();
            CameraExtensionCharacteristics extensionChars =
                    mTestRule.getCameraManager().getCameraExtensionCharacteristics(id);
            List<Integer> supportedExtensions = extensionChars.getSupportedExtensions();
            for (Integer extension : supportedExtensions) {
                Set<CaptureRequest.Key> supportedRequestKeys =
                        extensionChars.getAvailableCaptureRequestKeys(extension);
                if (!supportedRequestKeys.containsAll(Arrays.asList(FLASH_CAPTURE_REQUEST_SET))) {
                    continue;
                }
                Set<CaptureResult.Key> supportedResultKeys =
                        extensionChars.getAvailableCaptureResultKeys(extension);
                assertTrue(supportedResultKeys.containsAll(
                        Arrays.asList(FLASH_CAPTURE_RESULT_SET)));

                int captureFormat = ImageFormat.JPEG;
                List<Size> captureSizes = extensionChars.getExtensionSupportedSizes(extension,
                        captureFormat);
                assertFalse("No Jpeg output supported", captureSizes.isEmpty());
                Size captureMaxSize = captureSizes.get(0);

                SimpleImageReaderListener imageListener = new SimpleImageReaderListener(false, 1);
                ImageReader extensionImageReader = CameraTestUtils.makeImageReader(
                        captureMaxSize, captureFormat, /*maxImages*/ 1, imageListener,
                        mTestRule.getHandler());
                Surface imageReaderSurface = extensionImageReader.getSurface();
                OutputConfiguration readerOutput = new OutputConfiguration(imageReaderSurface);
                List<OutputConfiguration> outputConfigs = new ArrayList<>();
                outputConfigs.add(readerOutput);

                List<Size> repeatingSizes = extensionChars.getExtensionSupportedSizes(extension,
                        mSurfaceTexture.getClass());
                Size previewSize = repeatingSizes.get(0);

                mSurfaceTexture.setDefaultBufferSize(previewSize.getWidth(),
                        previewSize.getHeight());
                Surface texturedSurface = new Surface(mSurfaceTexture);
                outputConfigs.add(new OutputConfiguration(texturedSurface));

                BlockingExtensionSessionCallback sessionListener =
                        new BlockingExtensionSessionCallback(mock(
                                CameraExtensionSession.StateCallback.class));
                ExtensionSessionConfiguration configuration =
                        new ExtensionSessionConfiguration(extension, outputConfigs,
                                new HandlerExecutor(mTestRule.getHandler()),
                                sessionListener);
                try {
                    mTestRule.openDevice(id);
                    CameraDevice camera = mTestRule.getCamera();
                    camera.createExtensionSession(configuration);
                    CameraExtensionSession extensionSession =
                            sessionListener.waitAndGetSession(
                                    SESSION_CONFIGURE_TIMEOUT_MS);
                    assertNotNull(extensionSession);

                    // Test torch on and off
                    CaptureRequest.Builder captureBuilder =
                            mTestRule.getCamera().createCaptureRequest(
                                    android.hardware.camera2.CameraDevice.TEMPLATE_PREVIEW);
                    captureBuilder.addTarget(texturedSurface);
                    captureBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                            CameraMetadata.CONTROL_AE_MODE_ON);
                    captureBuilder.set(CaptureRequest.CONTROL_AE_LOCK, false);
                    captureBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
                    FlashStateListener mockFlashListener = mock(FlashStateListener.class);
                    AutoExposureStateListener mockAEStateListener =
                            mock(AutoExposureStateListener.class);
                    CameraExtensionSession.ExtensionCaptureCallback repeatingCallbackMock =
                            mock(CameraExtensionSession.ExtensionCaptureCallback.class);
                    SimpleCaptureCallback repeatingCaptureCallback =
                            new SimpleCaptureCallback(extension, repeatingCallbackMock,
                                    extensionChars.getAvailableCaptureResultKeys(extension),
                                    mCollector, null /*afState*/, mockFlashListener,
                                    mockAEStateListener, perFrameControl);
                    CaptureRequest repeatingRequest = captureBuilder.build();
                    int repeatingSequenceId =
                            extensionSession.setRepeatingRequest(repeatingRequest,
                                    new HandlerExecutor(mTestRule.getHandler()),
                                    repeatingCaptureCallback);
                    assertTrue(repeatingSequenceId > 0);

                    verify(repeatingCallbackMock,
                            timeout(REPEATING_REQUEST_TIMEOUT_MS).atLeastOnce())
                            .onCaptureResultAvailable(eq(extensionSession),
                                    eq(repeatingRequest), any(TotalCaptureResult.class));
                    verify(mockFlashListener,
                            timeout(REPEATING_REQUEST_TIMEOUT_MS).atLeastOnce()).onFired();

                    captureBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                            CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                    captureBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
                    repeatingRequest = captureBuilder.build();
                    reset(mockFlashListener);
                    repeatingSequenceId = extensionSession.setRepeatingRequest(repeatingRequest,
                                    new HandlerExecutor(mTestRule.getHandler()),
                                    repeatingCaptureCallback);
                    assertTrue(repeatingSequenceId > 0);

                    verify(repeatingCallbackMock,
                            timeout(REPEATING_REQUEST_TIMEOUT_MS).atLeastOnce())
                            .onCaptureResultAvailable(eq(extensionSession),
                                    eq(repeatingRequest), any(TotalCaptureResult.class));
                    verify(mockFlashListener,
                            timeout(REPEATING_REQUEST_TIMEOUT_MS).atLeastOnce()).onReady();

                    // Test AE pre-capture sequence
                    captureBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
                    captureBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                            CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START);
                    CaptureRequest triggerRequest = captureBuilder.build();
                    int triggerSeqId = extensionSession.capture(triggerRequest,
                            new HandlerExecutor(mTestRule.getHandler()), repeatingCaptureCallback);
                    assertTrue(triggerSeqId > 0);

                    verify(repeatingCallbackMock,
                            timeout(REPEATING_REQUEST_TIMEOUT_MS).atLeastOnce())
                            .onCaptureResultAvailable(eq(extensionSession),
                                    eq(triggerRequest), any(TotalCaptureResult.class));
                    verify(mockAEStateListener,
                            timeout(REPEATING_REQUEST_TIMEOUT_MS).atLeastOnce()).onPrecapture();
                    verify(mockAEStateListener,
                            timeout(REPEATING_REQUEST_TIMEOUT_MS).atLeastOnce()).onConverged();

                    // Test main flash
                    captureBuilder = mTestRule.getCamera().createCaptureRequest(
                            android.hardware.camera2.CameraDevice.TEMPLATE_STILL_CAPTURE);
                    captureBuilder.addTarget(imageReaderSurface);
                    captureBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                            CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                    captureBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true);
                    captureBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_SINGLE);
                    CameraExtensionSession.ExtensionCaptureCallback mockCaptureCallback =
                            mock(CameraExtensionSession.ExtensionCaptureCallback.class);
                    reset(mockFlashListener);
                    SimpleCaptureCallback captureCallback =
                            new SimpleCaptureCallback(extension, mockCaptureCallback,
                                    extensionChars.getAvailableCaptureResultKeys(extension),
                                    mCollector, null /*afState*/, mockFlashListener,
                                    mockAEStateListener, perFrameControl);

                    CaptureRequest captureRequest = captureBuilder.build();
                    int captureSequenceId = extensionSession.capture(captureRequest,
                            new HandlerExecutor(mTestRule.getHandler()), captureCallback);
                    assertTrue(captureSequenceId > 0);

                    Image img =
                            imageListener.getImage(MULTI_FRAME_CAPTURE_IMAGE_TIMEOUT_MS);
                    validateImage(img, captureMaxSize.getWidth(),
                            captureMaxSize.getHeight(), captureFormat, null);
                    long imgTs = img.getTimestamp();
                    img.close();

                    verify(mockCaptureCallback,
                            timeout(MULTI_FRAME_CAPTURE_IMAGE_TIMEOUT_MS).times(1))
                            .onCaptureStarted(eq(extensionSession), eq(captureRequest), eq(imgTs));
                    verify(mockCaptureCallback,
                            timeout(MULTI_FRAME_CAPTURE_IMAGE_TIMEOUT_MS).times(1))
                            .onCaptureResultAvailable(eq(extensionSession),
                                    eq(captureRequest), any(TotalCaptureResult.class));
                    verify(mockFlashListener,
                            timeout(REPEATING_REQUEST_TIMEOUT_MS).atLeastOnce()).onFired();

                    extensionSession.stopRepeating();

                    extensionSession.close();

                    sessionListener.getStateWaiter().waitForState(
                            BlockingExtensionSessionCallback.SESSION_CLOSED,
                            SESSION_CLOSE_TIMEOUT_MS);
                } finally {
                    mTestRule.closeDevice(id);
                    texturedSurface.release();
                    extensionImageReader.close();
                }
            }
        }
    }

    // Verify 'CameraExtensionSession.StillCaptureLatency' behavior
    @Test
    public void testSessionStillCaptureLatency() throws Exception {
        final long CAPTURE_LATENCY_MS = 100;
        final long PROCESSING_LATENCY_MS = 200;
        final long DIFFERENT_PROCESSING_LATENCY_MS = 201;
        CameraExtensionSession.StillCaptureLatency stillCaptureLatency =
                new CameraExtensionSession.StillCaptureLatency(CAPTURE_LATENCY_MS,
                        PROCESSING_LATENCY_MS);
        assertEquals(stillCaptureLatency.getCaptureLatency(), CAPTURE_LATENCY_MS);
        assertEquals(stillCaptureLatency.getProcessingLatency(), PROCESSING_LATENCY_MS);
        assertNotNull(stillCaptureLatency.toString());
        CameraExtensionSession.StillCaptureLatency differentStillCaptureLatency =
                new CameraExtensionSession.StillCaptureLatency(CAPTURE_LATENCY_MS,
                        DIFFERENT_PROCESSING_LATENCY_MS);
        assertFalse(stillCaptureLatency.equals(differentStillCaptureLatency));
        assertFalse(stillCaptureLatency.hashCode() ==
                differentStillCaptureLatency.hashCode());
    }

    @Test
    public void testIllegalArguments() throws Exception {
        for (String id : getCameraIdsUnderTest()) {
            StaticMetadata staticMeta =
                    new StaticMetadata(mTestRule.getCameraManager().getCameraCharacteristics(id));
            if (!staticMeta.isColorOutputSupported()) {
                continue;
            }
            updatePreviewSurfaceTexture();
            CameraExtensionCharacteristics extensionChars =
                    mTestRule.getCameraManager().getCameraExtensionCharacteristics(id);
            List<Integer> supportedExtensions = extensionChars.getSupportedExtensions();
            for (Integer extension : supportedExtensions) {
                List<OutputConfiguration> outputConfigs = new ArrayList<>();
                BlockingExtensionSessionCallback sessionListener =
                        new BlockingExtensionSessionCallback(mock(
                                CameraExtensionSession.StateCallback.class));
                ExtensionSessionConfiguration configuration =
                        new ExtensionSessionConfiguration(extension, outputConfigs,
                                new HandlerExecutor(mTestRule.getHandler()),
                                sessionListener);

                try {
                    mTestRule.openDevice(id);
                    CameraDevice camera = mTestRule.getCamera();
                    try {
                        camera.createExtensionSession(configuration);
                        fail("should get IllegalArgumentException due to absent output surfaces");
                    } catch (IllegalArgumentException e) {
                        // Expected, we can proceed further
                    }

                    int captureFormat = ImageFormat.YUV_420_888;
                    List<Size> captureSizes = extensionChars.getExtensionSupportedSizes(extension,
                            captureFormat);
                    if (captureSizes.isEmpty()) {
                        captureFormat = ImageFormat.JPEG;
                        captureSizes = extensionChars.getExtensionSupportedSizes(extension,
                                captureFormat);
                    }
                    Size captureMaxSize =
                            CameraTestUtils.getMaxSize(captureSizes.toArray(new Size[0]));

                    mSurfaceTexture.setDefaultBufferSize(1, 1);
                    Surface texturedSurface = new Surface(mSurfaceTexture);
                    outputConfigs.add(new OutputConfiguration(texturedSurface));
                    configuration = new ExtensionSessionConfiguration(extension, outputConfigs,
                            new HandlerExecutor(mTestRule.getHandler()), sessionListener);

                    try {
                        camera.createExtensionSession(configuration);
                        fail("should get IllegalArgumentException due to illegal repeating request"
                                + " output surface");
                    } catch (IllegalArgumentException e) {
                        // Expected, we can proceed further
                    } finally {
                        outputConfigs.clear();
                    }

                    SimpleImageReaderListener imageListener = new SimpleImageReaderListener(false,
                            1);
                    Size invalidCaptureSize = new Size(1, 1);
                    ImageReader extensionImageReader = CameraTestUtils.makeImageReader(
                            invalidCaptureSize, captureFormat, /*maxImages*/ 1,
                            imageListener, mTestRule.getHandler());
                    Surface imageReaderSurface = extensionImageReader.getSurface();
                    OutputConfiguration readerOutput = new OutputConfiguration(imageReaderSurface);
                    outputConfigs.add(readerOutput);
                    configuration = new ExtensionSessionConfiguration(extension, outputConfigs,
                            new HandlerExecutor(mTestRule.getHandler()), sessionListener);

                    try{
                        camera.createExtensionSession(configuration);
                        fail("should get IllegalArgumentException due to illegal multi-frame"
                                + " request output surface");
                    } catch (IllegalArgumentException e) {
                        // Expected, we can proceed further
                    } finally {
                        outputConfigs.clear();
                        extensionImageReader.close();
                    }

                    // Pick a supported preview/repeating size with aspect ratio close to the
                    // multi-frame capture size
                    List<Size> repeatingSizes = extensionChars.getExtensionSupportedSizes(extension,
                            mSurfaceTexture.getClass());
                    Size maxRepeatingSize =
                            CameraTestUtils.getMaxSize(repeatingSizes.toArray(new Size[0]));
                    List<Size> previewSizes = getSupportedPreviewSizes(id,
                            mTestRule.getCameraManager(),
                            getPreviewSizeBound(mTestRule.getWindowManager(), PREVIEW_SIZE_BOUND));
                    List<Size> supportedPreviewSizes =
                            previewSizes.stream().filter(repeatingSizes::contains).collect(
                                    Collectors.toList());
                    if (!supportedPreviewSizes.isEmpty()) {
                        float targetAr =
                                ((float) captureMaxSize.getWidth()) / captureMaxSize.getHeight();
                        for (Size s : supportedPreviewSizes) {
                            float currentAr = ((float) s.getWidth()) / s.getHeight();
                            if (Math.abs(targetAr - currentAr) < 0.01) {
                                maxRepeatingSize = s;
                                break;
                            }
                        }
                    }

                    imageListener = new SimpleImageReaderListener(false, 1);
                    extensionImageReader = CameraTestUtils.makeImageReader(captureMaxSize,
                            captureFormat, /*maxImages*/ 1, imageListener, mTestRule.getHandler());
                    imageReaderSurface = extensionImageReader.getSurface();
                    readerOutput = new OutputConfiguration(imageReaderSurface);
                    outputConfigs.add(readerOutput);

                    mSurfaceTexture.setDefaultBufferSize(maxRepeatingSize.getWidth(),
                            maxRepeatingSize.getHeight());
                    texturedSurface = new Surface(mSurfaceTexture);
                    outputConfigs.add(new OutputConfiguration(texturedSurface));

                    configuration = new ExtensionSessionConfiguration(extension, outputConfigs,
                            new HandlerExecutor(mTestRule.getHandler()), sessionListener);
                    camera.createExtensionSession(configuration);
                    CameraExtensionSession extensionSession =
                            sessionListener.waitAndGetSession(
                                    SESSION_CONFIGURE_TIMEOUT_MS);
                    assertNotNull(extensionSession);

                    CaptureRequest.Builder captureBuilder =
                            mTestRule.getCamera().createCaptureRequest(
                                    android.hardware.camera2.CameraDevice.TEMPLATE_PREVIEW);
                    captureBuilder.addTarget(imageReaderSurface);
                    CameraExtensionSession.ExtensionCaptureCallback repeatingCallbackMock =
                            mock(CameraExtensionSession.ExtensionCaptureCallback.class);
                    SimpleCaptureCallback repeatingCaptureCallback =
                            new SimpleCaptureCallback(extension, repeatingCallbackMock,
                                    extensionChars.getAvailableCaptureResultKeys(extension),
                                    mCollector);
                    CaptureRequest repeatingRequest = captureBuilder.build();
                    try {
                        extensionSession.setRepeatingRequest(repeatingRequest,
                                new HandlerExecutor(mTestRule.getHandler()),
                                repeatingCaptureCallback);
                        fail("should get IllegalArgumentException due to illegal repeating request"
                                + " output target");
                    } catch (IllegalArgumentException e) {
                        // Expected, we can proceed further
                    }

                    extensionSession.close();

                    sessionListener.getStateWaiter().waitForState(
                            BlockingExtensionSessionCallback.SESSION_CLOSED,
                            SESSION_CLOSE_TIMEOUT_MS);

                    texturedSurface.release();
                    extensionImageReader.close();

                    captureBuilder = mTestRule.getCamera().createCaptureRequest(
                            CameraDevice.TEMPLATE_PREVIEW);
                    captureBuilder.addTarget(texturedSurface);
                    CameraExtensionSession.ExtensionCaptureCallback captureCallback =
                            mock(CameraExtensionSession.ExtensionCaptureCallback.class);

                    CaptureRequest captureRequest = captureBuilder.build();
                    try {
                        extensionSession.setRepeatingRequest(captureRequest,
                                new HandlerExecutor(mTestRule.getHandler()), captureCallback);
                        fail("should get IllegalStateException due to closed session");
                    } catch (IllegalStateException e) {
                        // Expected, we can proceed further
                    }

                    try {
                        extensionSession.stopRepeating();
                        fail("should get IllegalStateException due to closed session");
                    } catch (IllegalStateException e) {
                        // Expected, we can proceed further
                    }

                    try {
                        extensionSession.capture(captureRequest,
                                new HandlerExecutor(mTestRule.getHandler()), captureCallback);
                        fail("should get IllegalStateException due to closed session");
                    } catch (IllegalStateException e) {
                        // Expected, we can proceed further
                    }
                } finally {
                    mTestRule.closeDevice(id);
                }
            }
        }
    }
}
