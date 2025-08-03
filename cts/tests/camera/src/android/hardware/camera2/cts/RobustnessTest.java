/*
 * Copyright 2014 The Android Open Source Project
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

import static android.hardware.camera2.cts.CameraTestUtils.SessionConfigSupport;
import static android.hardware.camera2.cts.CameraTestUtils.SimpleCaptureCallback;
import static android.hardware.camera2.cts.CameraTestUtils.SimpleImageReaderListener;
import static android.hardware.camera2.cts.CameraTestUtils.StreamCombinationTargets;
import static android.hardware.camera2.cts.CameraTestUtils.assertEquals;
import static android.hardware.camera2.cts.CameraTestUtils.assertNotNull;
import static android.hardware.camera2.cts.CameraTestUtils.assertNull;
import static android.hardware.camera2.cts.CameraTestUtils.checkSessionConfigurationSupported;
import static android.hardware.camera2.cts.CameraTestUtils.checkSessionConfigurationWithSurfaces;
import static android.hardware.camera2.cts.CameraTestUtils.configureReprocessableCameraSession;
import static android.hardware.camera2.cts.CameraTestUtils.fail;
import static android.hardware.camera2.cts.CameraTestUtils.isSessionConfigSupported;
import static android.hardware.camera2.cts.CameraTestUtils.MaxStreamSizes;
import static android.hardware.camera2.cts.CameraTestUtils.MaxStreamSizes.JPEG;
import static android.hardware.camera2.cts.CameraTestUtils.MaxStreamSizes.MAXIMUM;
import static android.hardware.camera2.cts.CameraTestUtils.MaxStreamSizes.MAX_RES;
import static android.hardware.camera2.cts.CameraTestUtils.MaxStreamSizes.PREVIEW;
import static android.hardware.camera2.cts.CameraTestUtils.MaxStreamSizes.PRIV;
import static android.hardware.camera2.cts.CameraTestUtils.MaxStreamSizes.RAW;
import static android.hardware.camera2.cts.CameraTestUtils.MaxStreamSizes.RECORD;
import static android.hardware.camera2.cts.CameraTestUtils.MaxStreamSizes.S1440P;
import static android.hardware.camera2.cts.CameraTestUtils.MaxStreamSizes.S720P;
import static android.hardware.camera2.cts.CameraTestUtils.MaxStreamSizes.USE_CASE_PREVIEW;
import static android.hardware.camera2.cts.CameraTestUtils.MaxStreamSizes.USE_CASE_PREVIEW_VIDEO_STILL;
import static android.hardware.camera2.cts.CameraTestUtils.MaxStreamSizes.USE_CASE_STILL_CAPTURE;
import static android.hardware.camera2.cts.CameraTestUtils.MaxStreamSizes.USE_CASE_VIDEO_CALL;
import static android.hardware.camera2.cts.CameraTestUtils.MaxStreamSizes.USE_CASE_VIDEO_RECORD;
import static android.hardware.camera2.cts.CameraTestUtils.MaxStreamSizes.USE_CASE_CROPPED_RAW;
import static android.hardware.camera2.cts.CameraTestUtils.MaxStreamSizes.VGA;
import static android.hardware.camera2.cts.CameraTestUtils.MaxStreamSizes.YUV;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.cts.helpers.StaticMetadata;
import android.hardware.camera2.cts.testcases.Camera2AndroidTestCase;
import android.hardware.camera2.params.DynamicRangeProfiles;
import android.hardware.camera2.params.InputConfiguration;
import android.hardware.camera2.params.MandatoryStreamCombination;
import android.hardware.camera2.params.MandatoryStreamCombination.MandatoryStreamInformation;
import android.hardware.camera2.params.OisSample;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageWriter;
import android.util.Log;
import android.util.Pair;
import android.util.Size;
import android.view.Surface;

import com.android.ex.camera2.blocking.BlockingSessionCallback;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Tests exercising edge cases in camera setup, configuration, and usage.
 */

@RunWith(Parameterized.class)
public class RobustnessTest extends Camera2AndroidTestCase {
    private static final String TAG = "RobustnessTest";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);

    private static final int CONFIGURE_TIMEOUT = 5000; //ms
    private static final int CAPTURE_TIMEOUT = 1500; //ms

    // For testTriggerInteractions
    private static final int PREVIEW_WARMUP_FRAMES = 60;
    private static final int MAX_RESULT_STATE_CHANGE_WAIT_FRAMES = 100;
    private static final int MAX_TRIGGER_SEQUENCE_FRAMES = 180; // 6 sec at 30 fps
    private static final int MAX_RESULT_STATE_POSTCHANGE_WAIT_FRAMES = 10;

    /**
     * Test that a {@link CameraCaptureSession} can be configured with a {@link Surface} containing
     * a dimension other than one of the supported output dimensions.  The buffers produced into
     * this surface are expected have the dimensions of the closest possible buffer size in the
     * available stream configurations for a surface with this format.
     */
    @Test
    public void testBadSurfaceDimensions() throws Exception {
        for (String id : getCameraIdsUnderTest()) {
            try {
                Log.i(TAG, "Testing Camera " + id);
                openDevice(id);

                List<Size> testSizes = null;
                int format = mStaticInfo.isColorOutputSupported() ?
                    ImageFormat.YUV_420_888 : ImageFormat.DEPTH16;

                testSizes = CameraTestUtils.getSortedSizesForFormat(id, mCameraManager,
                        format, null);

                // Find some size not supported by the camera
                Size weirdSize = new Size(643, 577);
                int count = 0;
                while(testSizes.contains(weirdSize)) {
                    // Really, they can't all be supported...
                    weirdSize = new Size(weirdSize.getWidth() + 1, weirdSize.getHeight() + 1);
                    count++;
                    assertTrue("Too many exotic YUV_420_888 resolutions supported.", count < 100);
                }

                // Setup imageReader with invalid dimension
                ImageReader imageReader = ImageReader.newInstance(weirdSize.getWidth(),
                        weirdSize.getHeight(), format, 3);

                // Setup ImageReaderListener
                SimpleImageReaderListener imageListener = new SimpleImageReaderListener();
                imageReader.setOnImageAvailableListener(imageListener, mHandler);

                Surface surface = imageReader.getSurface();
                List<Surface> surfaces = new ArrayList<>();
                surfaces.add(surface);

                // Setup a capture request and listener
                CaptureRequest.Builder request =
                        mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                request.addTarget(surface);

                // Check that correct session callback is hit.
                CameraCaptureSession.StateCallback sessionListener =
                        mock(CameraCaptureSession.StateCallback.class);
                CameraCaptureSession session = CameraTestUtils.configureCameraSession(mCamera,
                        surfaces, sessionListener, mHandler);

                verify(sessionListener, timeout(CONFIGURE_TIMEOUT).atLeastOnce()).
                        onConfigured(any(CameraCaptureSession.class));
                verify(sessionListener, timeout(CONFIGURE_TIMEOUT).atLeastOnce()).
                        onReady(any(CameraCaptureSession.class));
                verify(sessionListener, never()).onConfigureFailed(any(CameraCaptureSession.class));
                verify(sessionListener, never()).onActive(any(CameraCaptureSession.class));
                verify(sessionListener, never()).onClosed(any(CameraCaptureSession.class));

                CameraCaptureSession.CaptureCallback captureListener =
                        mock(CameraCaptureSession.CaptureCallback.class);
                session.capture(request.build(), captureListener, mHandler);

                verify(captureListener, timeout(CAPTURE_TIMEOUT).atLeastOnce()).
                        onCaptureCompleted(any(CameraCaptureSession.class),
                                any(CaptureRequest.class), any(TotalCaptureResult.class));
                verify(captureListener, never()).onCaptureFailed(any(CameraCaptureSession.class),
                        any(CaptureRequest.class), any(CaptureFailure.class));

                Image image = imageListener.getImage(CAPTURE_TIMEOUT);
                int imageWidth = image.getWidth();
                int imageHeight = image.getHeight();
                Size actualSize = new Size(imageWidth, imageHeight);

                assertTrue("Camera does not contain outputted image resolution " + actualSize,
                        testSizes.contains(actualSize));
                imageReader.close();
            } finally {
                closeDevice(id);
            }
        }
    }

    /**
     * Test for making sure the mandatory stream combinations work as expected.
     */
    @Test
    public void testMandatoryOutputCombinations() throws Exception {
        testMandatoryOutputCombinations(/*maxResolution*/false);
    }

    /**
     * Test for making sure the mandatory stream combinations work as expected.
     */
    private void testMandatoryOutputCombinations(boolean maxResolution) throws Exception {
        CameraCharacteristics.Key<MandatoryStreamCombination []> ck =
                CameraCharacteristics.SCALER_MANDATORY_STREAM_COMBINATIONS;

        if (maxResolution) {
            ck = CameraCharacteristics.SCALER_MANDATORY_MAXIMUM_RESOLUTION_STREAM_COMBINATIONS;
        }
        String[] cameraIdsUnderTest = getCameraIdsUnderTest();
        for (String id : cameraIdsUnderTest) {
            openDevice(id);
            MandatoryStreamCombination[] combinations = mStaticInfo.getCharacteristics().get(ck);

            if (combinations == null) {
                String maxResolutionStr = maxResolution ? " " : " maximum resolution ";
                Log.i(TAG, "No mandatory" + maxResolutionStr + "stream combinations for camera: " +
                        id + " skip test");
                closeDevice(id);
                continue;
            }

            try {
                for (MandatoryStreamCombination combination : combinations) {
                    if (!combination.isReprocessable()) {
                        if (maxResolution) {
                            testMandatoryStreamCombination(id, mStaticInfo,
                                    /*physicalCameraId*/ null, combination, /*substituteY8*/false,
                                    /*substituteHeic*/false, /*maxResolution*/true);
                        } else {
                            testMandatoryStreamCombination(id, mStaticInfo,
                                    null/*physicalCameraId*/, combination);
                        }
                    }
                }

                // Make sure mandatory stream combinations for each physical camera work
                // as expected.
                if (mStaticInfo.isLogicalMultiCamera()) {
                    Set<String> physicalCameraIds =
                            mStaticInfo.getCharacteristics().getPhysicalCameraIds();
                    for (String physicalId : physicalCameraIds) {
                        if (Arrays.asList(cameraIdsUnderTest).contains(physicalId)) {
                            // If physicalId is advertised in camera ID list, do not need to test
                            // its stream combination through logical camera.
                            continue;
                        }
                        StaticMetadata physicalStaticInfo = mAllStaticInfo.get(physicalId);

                        MandatoryStreamCombination[] phyCombinations =
                                physicalStaticInfo.getCharacteristics().get(ck);

                        if (phyCombinations == null) {
                            Log.i(TAG, "No mandatory stream combinations for physical camera device: " + id + " skip test");
                            continue;
                        }

                        for (MandatoryStreamCombination combination : phyCombinations) {
                            if (!combination.isReprocessable()) {
                                if (maxResolution) {
                                   testMandatoryStreamCombination(id, physicalStaticInfo,
                                           physicalId, combination, /*substituteY8*/false,
                                           /*substituteHeic*/false, /*maxResolution*/true);
                                } else {
                                    testMandatoryStreamCombination(id, physicalStaticInfo,
                                            physicalId, combination);
                                }
                            }
                        }
                    }
                }

            } finally {
                closeDevice(id);
            }
        }
    }


    /**
     * Test for making sure the mandatory stream combinations work as expected.
     */
    @Test
    public void testMandatoryMaximumResolutionOutputCombinations() throws Exception {
        testMandatoryOutputCombinations(/*maxResolution*/ true);
    }

    /**
     * Test for making sure the mandatory use case stream combinations work as expected.
     */
    @Test
    public void testMandatoryUseCaseOutputCombinations() throws Exception {
        for (String id : getCameraIdsUnderTest()) {
            StaticMetadata info = mAllStaticInfo.get(id);
            CameraCharacteristics chars = info.getCharacteristics();
            CameraCharacteristics.Key<MandatoryStreamCombination []> ck =
                    CameraCharacteristics.SCALER_MANDATORY_USE_CASE_STREAM_COMBINATIONS;
            MandatoryStreamCombination[] combinations = chars.get(ck);

            if (!info.isCapabilitySupported(
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_STREAM_USE_CASE)) {
                assertNull(combinations);
                Log.i(TAG, "Camera id " + id + " doesn't support stream use case, skip test");
                continue;
            }

            assertNotNull(combinations);
            openDevice(id);

            try {
                Rect preCorrectionActiveArrayRect = info.getPreCorrectedActiveArraySizeChecked();
                for (MandatoryStreamCombination combination : combinations) {
                    Log.i(TAG, "Testing fixed mandatory output combination with stream use case: " +
                            combination.getDescription() + " on camera: " + id);
                    CaptureRequest.Builder requestBuilder =
                            mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                    testMandatoryOutputCombinationWithPresetKeys(id, combination, requestBuilder,
                            preCorrectionActiveArrayRect);
                }
            } finally {
                closeDevice(id);
            }
        }
    }

    @Test
    public void testMandatoryPreviewStabilizationOutputCombinations() throws Exception {
        for (String id : getCameraIdsUnderTest()) {
            StaticMetadata info = mAllStaticInfo.get(id);
            boolean previewStabilizationSupported = isPreviewStabilizationSupported(info);
            CameraCharacteristics chars = info.getCharacteristics();
            CameraCharacteristics.Key<MandatoryStreamCombination []> ck =
                    CameraCharacteristics
                            .SCALER_MANDATORY_PREVIEW_STABILIZATION_OUTPUT_STREAM_COMBINATIONS;
            MandatoryStreamCombination[] combinations = chars.get(ck);

            if (combinations == null) {
                assertFalse("Preview stabilization supported by camera id: " + id
                        + " but null mandatory streams", previewStabilizationSupported);
                Log.i(TAG, "Camera id " + id + " doesn't support preview stabilization, skip test");
                continue;
            } else {
                assertTrue("Preview stabilization not supported by camera id: " + id
                        + " but non-null mandatory streams", previewStabilizationSupported);
            }

            openDevice(id);

            try {
                for (MandatoryStreamCombination combination : combinations) {
                    Log.i(TAG, "Testing fixed mandatory output combination with preview"
                            + "stabilization case: " + combination.getDescription() + " on camera: "
                                     + id);
                    CaptureRequest.Builder requestBuilder =
                            mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                    requestBuilder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                            CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION);
                    testMandatoryOutputCombinationWithPresetKeys(id, combination, requestBuilder,
                            /*preCorrectionActiveArrayRect*/null);
                }
            } finally {
                closeDevice(id);
            }
        }
    }

    private boolean isPreviewStabilizationSupported(StaticMetadata info) {
        int[] availableVideoStabilizationModes = info.getAvailableVideoStabilizationModesChecked();
        if (availableVideoStabilizationModes == null) {
            return false;
        }
        for (int mode : availableVideoStabilizationModes) {
            if (mode == CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION) {
                return true;
            }
        }
        return false;
    }

    private void testMandatoryOutputCombinationWithPresetKeys(String cameraId,
            MandatoryStreamCombination combination, CaptureRequest.Builder requestBuilderWithKeys,
            Rect preCorrectionActiveArrayRect) {
        final int TIMEOUT_FOR_RESULT_MS = 1000;
        final int MIN_RESULT_COUNT = 3;

        // Setup outputs
        List<OutputConfiguration> outputConfigs = new ArrayList<>();
        List<Surface> outputSurfaces = new ArrayList<Surface>();
        List<Surface> uhOutputSurfaces = new ArrayList<Surface>();
        StreamCombinationTargets targets = new StreamCombinationTargets();

        CameraTestUtils.setupConfigurationTargets(combination.getStreamsInformation(),
                targets, outputConfigs, outputSurfaces, uhOutputSurfaces, MIN_RESULT_COUNT,
                /*substituteY8*/ false, /*substituteHeic*/false,
                /*physicalCameraId*/ null,
                /*multiResStreamConfig*/null, mHandler,
                /*dynamicRangeProfiles*/ null);

        boolean haveSession = false;
        try {
            checkSessionConfigurationSupported(mCamera, mHandler, outputConfigs,
                    /*inputConfig*/ null, SessionConfiguration.SESSION_REGULAR,
                    true/*defaultSupport*/,
                    String.format("Session configuration query from combination: %s failed",
                            combination.getDescription()));

            createSessionByConfigs(outputConfigs);
            haveSession = true;
            for (Surface s : outputSurfaces) {
                requestBuilderWithKeys.addTarget(s);
            }
            boolean croppedRawUseCase = false;
            for (OutputConfiguration c : outputConfigs) {
                if (c.getStreamUseCase() ==
                        CameraMetadata.SCALER_AVAILABLE_STREAM_USE_CASES_CROPPED_RAW) {
                    croppedRawUseCase = true;
                    break;
                }
            }

            CaptureRequest request = requestBuilderWithKeys.build();
            CameraTestUtils.SimpleCaptureCallback captureCallback =
                    new CameraTestUtils.SimpleCaptureCallback();


            mCameraSession.setRepeatingRequest(request, captureCallback, mHandler);

            for (int i = 0; i < MIN_RESULT_COUNT; i++) {
                // Makes sure that we received an onCaptureCompleted and not an onCaptureFailed.
                TotalCaptureResult result =
                        captureCallback.getTotalCaptureResultForRequest(request,
                                /*numResultsWait*/ 0);
                validateResultMandatoryConditions(result, croppedRawUseCase,
                    preCorrectionActiveArrayRect);
            }
            if (captureCallback.hasMoreFailures()) {
                mCollector.addMessage("No capture failures expected, but there was a failure");
            }

        } catch (Throwable e) {
            mCollector.addMessage(
                    String.format("Closing down for combination: %s failed due to: %s",
                            combination.getDescription(), e.getMessage()));
        }

        if (haveSession) {
            try {
                Log.i(TAG, String.format("Done with camera %s, combination: %s, closing session",
                                cameraId, combination.getDescription()));
                stopCapture(/*fast*/false);
            } catch (Throwable e) {
                mCollector.addMessage(
                    String.format("Closing down for combination: %s failed due to: %s",
                            combination.getDescription(), e.getMessage()));
            }
        }

        targets.close();
    }

    private void validateResultMandatoryConditions(TotalCaptureResult result,
            boolean croppedRawUseCase, Rect preCorrectionActiveArrayRect) {
        // validate more conditions here
        if (croppedRawUseCase) {
            Rect rawCropRegion = result.get(CaptureResult.SCALER_RAW_CROP_REGION);
            if (rawCropRegion == null) {
                mCollector.addMessage("SCALER_RAW_CROP_REGION should not be null " +
                        "when CROPPED_RAW stream use case is used.");
            }
            if (!(preCorrectionActiveArrayRect.width() >= rawCropRegion.width()
                    && preCorrectionActiveArrayRect.height() >= rawCropRegion.height())) {
                mCollector.addMessage("RAW_CROP_REGION dimensions should be <= pre correction"
                        + " array dimensions. SCALER_RAW_CROP_REGION : "
                        + rawCropRegion.flattenToString() + " pre correction active array is "
                        + preCorrectionActiveArrayRect.flattenToString());
            }
        }
    }

    private void testMandatoryStreamCombination(String cameraId, StaticMetadata staticInfo,
            String physicalCameraId, MandatoryStreamCombination combination) throws Exception {
        // Check whether substituting YUV_888 format with Y8 format
        boolean substituteY8 = false;
        if (staticInfo.isMonochromeWithY8()) {
            List<MandatoryStreamInformation> streamsInfo = combination.getStreamsInformation();
            for (MandatoryStreamInformation streamInfo : streamsInfo) {
                if (streamInfo.getFormat() == ImageFormat.YUV_420_888) {
                    substituteY8 = true;
                    break;
                }
            }
        }

        // Check whether substituting JPEG format with HEIC format
        boolean substituteHeic = false;
        if (staticInfo.isHeicSupported()) {
            List<MandatoryStreamInformation> streamsInfo = combination.getStreamsInformation();
            for (MandatoryStreamInformation streamInfo : streamsInfo) {
                if (streamInfo.getFormat() == ImageFormat.JPEG) {
                    substituteHeic = true;
                    break;
                }
            }
        }

        // Test camera output combination
        String log = "Testing mandatory stream combination: " + combination.getDescription() +
                " on camera: " + cameraId;
        if (physicalCameraId != null) {
            log += ", physical sub-camera: " + physicalCameraId;
        }
        Log.i(TAG, log);
        testMandatoryStreamCombination(cameraId, staticInfo, physicalCameraId, combination,
                /*substituteY8*/false, /*substituteHeic*/false, /*maxResolution*/false);

        if (substituteY8) {
            Log.i(TAG, log + " with Y8");
            testMandatoryStreamCombination(cameraId, staticInfo, physicalCameraId, combination,
                    /*substituteY8*/true, /*substituteHeic*/false, /*maxResolution*/false);
        }

        if (substituteHeic) {
            Log.i(TAG, log + " with HEIC");
            testMandatoryStreamCombination(cameraId, staticInfo, physicalCameraId, combination,
                    /*substituteY8*/false, /*substituteHeic*/true, /**maxResolution*/ false);
        }
    }

    private void testMandatoryStreamCombination(String cameraId,
            StaticMetadata staticInfo, String physicalCameraId,
            MandatoryStreamCombination combination,
            boolean substituteY8, boolean substituteHeic, boolean ultraHighResolution)
            throws Exception {
        // Timeout is relaxed by 1 second for LEGACY devices to reduce false positive rate in CTS
        // TODO: This needs to be adjusted based on feedback
        final int TIMEOUT_MULTIPLIER = ultraHighResolution ? 2 : 1;
        final int TIMEOUT_FOR_RESULT_MS =
                ((staticInfo.isHardwareLevelLegacy()) ? 2000 : 1000) * TIMEOUT_MULTIPLIER;
        final int MIN_RESULT_COUNT = 3;

        // Set up outputs
        List<OutputConfiguration> outputConfigs = new ArrayList<>();
        List<Surface> outputSurfaces = new ArrayList<Surface>();
        List<Surface> uhOutputSurfaces = new ArrayList<Surface>();
        StreamCombinationTargets targets = new StreamCombinationTargets();

        CameraTestUtils.setupConfigurationTargets(combination.getStreamsInformation(),
                targets, outputConfigs, outputSurfaces, uhOutputSurfaces, MIN_RESULT_COUNT,
                substituteY8, substituteHeic, physicalCameraId, /*multiResStreamConfig*/null,
                mHandler);

        boolean haveSession = false;
        try {
            CaptureRequest.Builder requestBuilder =
                    mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            CaptureRequest.Builder uhRequestBuilder =
                    mCamera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);

            for (Surface s : outputSurfaces) {
                requestBuilder.addTarget(s);
            }

            for (Surface s : uhOutputSurfaces) {
                uhRequestBuilder.addTarget(s);
            }
            // We need to explicitly set the sensor pixel mode to default since we're mixing default
            // and max resolution requests in the same capture session.
            requestBuilder.set(CaptureRequest.SENSOR_PIXEL_MODE,
                    CameraMetadata.SENSOR_PIXEL_MODE_DEFAULT);
            if (ultraHighResolution) {
                uhRequestBuilder.set(CaptureRequest.SENSOR_PIXEL_MODE,
                        CameraMetadata.SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION);
            }
            CameraCaptureSession.CaptureCallback mockCaptureCallback =
                    mock(CameraCaptureSession.CaptureCallback.class);

            if (physicalCameraId == null) {
                checkSessionConfigurationSupported(mCamera, mHandler, outputConfigs,
                        /*inputConfig*/ null, SessionConfiguration.SESSION_REGULAR,
                        true/*defaultSupport*/, String.format(
                        "Session configuration query from combination: %s failed",
                        combination.getDescription()));
            } else {
                SessionConfigSupport sessionConfigSupport = isSessionConfigSupported(
                        mCamera, mHandler, outputConfigs, /*inputConfig*/ null,
                        SessionConfiguration.SESSION_REGULAR, false/*defaultSupport*/);
                assertTrue(
                        String.format("Session configuration query from combination: %s failed",
                        combination.getDescription()), !sessionConfigSupport.error);
                if (!sessionConfigSupport.callSupported) {
                    return;
                }
                assertTrue(
                        String.format("Session configuration must be supported for combination: " +
                        "%s", combination.getDescription()), sessionConfigSupport.configSupported);
            }

            createSessionByConfigs(outputConfigs);
            haveSession = true;
            CaptureRequest request = requestBuilder.build();
            CaptureRequest uhRequest = uhRequestBuilder.build();
            mCameraSession.setRepeatingRequest(request, mockCaptureCallback, mHandler);
            if (ultraHighResolution) {
                mCameraSession.capture(uhRequest, mockCaptureCallback, mHandler);
            }
            verify(mockCaptureCallback,
                    timeout(TIMEOUT_FOR_RESULT_MS * MIN_RESULT_COUNT).atLeast(MIN_RESULT_COUNT))
                    .onCaptureCompleted(
                        eq(mCameraSession),
                        eq(request),
                        isA(TotalCaptureResult.class));
           if (ultraHighResolution) {
                verify(mockCaptureCallback,
                        timeout(TIMEOUT_FOR_RESULT_MS).atLeast(1))
                        .onCaptureCompleted(
                            eq(mCameraSession),
                            eq(uhRequest),
                            isA(TotalCaptureResult.class));
            }

            verify(mockCaptureCallback, never()).
                    onCaptureFailed(
                        eq(mCameraSession),
                        eq(request),
                        isA(CaptureFailure.class));

        } catch (Throwable e) {
            mCollector.addMessage(String.format("Mandatory stream combination: %s failed due: %s",
                    combination.getDescription(), e.getMessage()));
        }
        if (haveSession) {
            try {
                Log.i(TAG, String.format("Done with camera %s, combination: %s, closing session",
                                cameraId, combination.getDescription()));
                stopCapture(/*fast*/false);
            } catch (Throwable e) {
                mCollector.addMessage(
                    String.format("Closing down for combination: %s failed due to: %s",
                            combination.getDescription(), e.getMessage()));
            }
        }

        targets.close();
    }

    /**
     * Test for making sure the required 10-bit stream combinations work as expected.
     * Since we have too many possible combinations between different 8-bit vs. 10-bit as well
     * as 10-bit dynamic profiles and in order to maximize the coverage within some reasonable
     * amount of iterations, the test case will configure 8-bit and 10-bit outputs randomly. In case
     * we have 10-bit output, then the dynamic range profile will also be randomly picked.
     */
    @Test
    public void testMandatory10BitStreamCombinations() throws Exception {
        for (String id : getCameraIdsUnderTest()) {
            openDevice(id);
            CameraCharacteristics chars = mStaticInfo.getCharacteristics();
            if (!CameraTestUtils.hasCapability(
                    chars, CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT)) {
                Log.i(TAG, "Camera id " + id + " doesn't support 10-bit output, skip test");
                closeDevice(id);
                continue;
            }
            CameraCharacteristics.Key<MandatoryStreamCombination []> ck =
                    CameraCharacteristics.SCALER_MANDATORY_TEN_BIT_OUTPUT_STREAM_COMBINATIONS;

            MandatoryStreamCombination[] combinations = chars.get(ck);
            assertNotNull(combinations);

            try {
                for (MandatoryStreamCombination combination : combinations) {
                    Log.i(TAG, "Testing fixed mandatory 10-bit output stream combination: " +
                            combination.getDescription() + " on camera: " + id);
                    DynamicRangeProfiles profiles = mStaticInfo.getCharacteristics().get(
                            CameraCharacteristics.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES);
                    assertNotNull(profiles);

                    // First we want to make sure that a fixed set of 10-bit streams
                    // is functional
                    for (Long profile : profiles.getSupportedProfiles()) {
                        if (profile != DynamicRangeProfiles.STANDARD) {
                            ArrayList<Long> testProfiles = new ArrayList<Long>();
                            testProfiles.add(profile);
                            testMandatory10BitStreamCombination(id, combination, profiles,
                                    testProfiles);
                        }
                    }

                    Log.i(TAG, "Testing random mandatory 10-bit output stream combination: " +
                            combination.getDescription() + " on camera: " + id);
                    // Next try out a random mix of standard 8-bit and 10-bit profiles.
                    // The number of possible combinations is quite big and testing them
                    // all on physical hardware can become unfeasible.
                    ArrayList<Long> testProfiles = new ArrayList<>(
                            profiles.getSupportedProfiles());
                    testMandatory10BitStreamCombination(id, combination, profiles, testProfiles);
                }
            } finally {
                closeDevice(id);
            }
        }
    }

    private void testMandatory10BitStreamCombination(String cameraId,
            MandatoryStreamCombination combination, DynamicRangeProfiles profiles,
            List<Long> testProfiles) {
        final int TIMEOUT_FOR_RESULT_MS = 1000;
        final int MIN_RESULT_COUNT = 3;

        // Setup outputs
        List<OutputConfiguration> outputConfigs = new ArrayList<>();
        List<Surface> outputSurfaces = new ArrayList<Surface>();
        List<Surface> uhOutputSurfaces = new ArrayList<Surface>();
        StreamCombinationTargets targets = new StreamCombinationTargets();

        CameraTestUtils.setupConfigurationTargets(combination.getStreamsInformation(),
                targets, outputConfigs, outputSurfaces, uhOutputSurfaces, MIN_RESULT_COUNT,
                /*substituteY8*/ false, /*substituteHeic*/false,
                /*physicalCameraId*/ null,
                /*multiResStreamConfig*/null, mHandler,
                testProfiles);

        try {
            checkSessionConfigurationSupported(mCamera, mHandler, outputConfigs,
                    /*inputConfig*/ null, SessionConfiguration.SESSION_REGULAR,
                    true/*defaultSupport*/,
                    String.format("Session configuration query from combination: %s failed",
                            combination.getDescription()));

            createSessionByConfigs(outputConfigs);

            boolean constraintPresent = false;
            List<Surface> constrainedOutputs = new ArrayList<>(outputSurfaces);

            while (!outputSurfaces.isEmpty()) {
                CaptureRequest.Builder requestBuilder =
                        mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                // Check to see how many outputs can be combined in to a single request including
                // the first output surface and respecting the advertised constraints
                Iterator<OutputConfiguration> it = outputConfigs.iterator();
                OutputConfiguration config = it.next();
                HashSet<Long> currentProfiles = new HashSet<>();
                currentProfiles.add(config.getDynamicRangeProfile());
                requestBuilder.addTarget(config.getSurface());
                outputSurfaces.remove(config.getSurface());
                it.remove();
                while (it.hasNext()) {
                    config = it.next();
                    Long currentProfile = config.getDynamicRangeProfile();
                    Set<Long> newLimitations = profiles.getProfileCaptureRequestConstraints(
                            currentProfile);
                    if (newLimitations.isEmpty() || (newLimitations.containsAll(currentProfiles))) {
                        currentProfiles.add(currentProfile);
                        requestBuilder.addTarget(config.getSurface());
                        outputSurfaces.remove(config.getSurface());
                        it.remove();
                    } else if (!constraintPresent && !newLimitations.isEmpty() &&
                            !newLimitations.containsAll(currentProfiles)) {
                        constraintPresent = true;
                    }
                }

                CaptureRequest request = requestBuilder.build();
                CameraCaptureSession.CaptureCallback mockCaptureCallback =
                        mock(CameraCaptureSession.CaptureCallback.class);
                mCameraSession.capture(request, mockCaptureCallback, mHandler);
                verify(mockCaptureCallback,
                        timeout(TIMEOUT_FOR_RESULT_MS).atLeastOnce())
                        .onCaptureCompleted(
                                eq(mCameraSession),
                                eq(request),
                                isA(TotalCaptureResult.class));

                verify(mockCaptureCallback, never()).
                        onCaptureFailed(
                                eq(mCameraSession),
                                eq(request),
                                isA(CaptureFailure.class));
            }

            if (constraintPresent) {
                // Capture requests that include output surfaces with dynamic range profiles that
                // cannot be combined must throw a corresponding exception
                CaptureRequest.Builder requestBuilder =
                        mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                for (Surface s : constrainedOutputs) {
                    requestBuilder.addTarget(s);
                }

                CaptureRequest request = requestBuilder.build();
                CameraCaptureSession.CaptureCallback mockCaptureCallback =
                        mock(CameraCaptureSession.CaptureCallback.class);
                try {
                    mCameraSession.capture(request, mockCaptureCallback, mHandler);
                    fail("Capture request to outputs with incompatible dynamic range profiles "
                            + "must always fail!");
                } catch (IllegalArgumentException e) {
                    // Expected
                }
            }

            Log.i(TAG, String.format("Done with camera %s, combination: %s, closing session",
                    cameraId, combination.getDescription()));
        } catch (Throwable e) {
            mCollector.addMessage(
                    String.format("Closing down for combination: %s failed due to: %s",
                            combination.getDescription(), e.getMessage()));
        }

        targets.close();
    }

    /**
     * Test for making sure the required reprocess input/output combinations for each hardware
     * level and capability work as expected.
     */
    @Test
    public void testMandatoryReprocessConfigurations() throws Exception {
        testMandatoryReprocessConfigurations(/*maxResolution*/false);
    }

    /**
     * Test for making sure the required reprocess input/output combinations for each hardware
     * level and capability work as expected.
     */
    @Test
    public void testMandatoryMaximumResolutionReprocessConfigurations() throws Exception {
        testMandatoryReprocessConfigurations(/*maxResolution*/true);
    }

    /**
     * Test for making sure the required reprocess input/output combinations for each hardware
     * level and capability work as expected.
     */
    public void testMandatoryReprocessConfigurations(boolean maxResolution) throws Exception {
        for (String id : getCameraIdsUnderTest()) {
            openDevice(id);
            CameraCharacteristics chars = mStaticInfo.getCharacteristics();
            if (maxResolution && !CameraTestUtils.hasCapability(
                  chars, CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_REMOSAIC_REPROCESSING)) {
                Log.i(TAG, "Camera id " + id + "doesn't support REMOSAIC_REPROCESSING, skip test");
                closeDevice(id);
                continue;
            }
            CameraCharacteristics.Key<MandatoryStreamCombination []> ck =
                    CameraCharacteristics.SCALER_MANDATORY_STREAM_COMBINATIONS;

            if (maxResolution) {
                ck = CameraCharacteristics.SCALER_MANDATORY_MAXIMUM_RESOLUTION_STREAM_COMBINATIONS;
            }

            MandatoryStreamCombination[] combinations = chars.get(ck);
            if (combinations == null) {
                Log.i(TAG, "No mandatory stream combinations for camera: " + id + " skip test");
                closeDevice(id);
                continue;
            }

            try {
                for (MandatoryStreamCombination combination : combinations) {
                    if (combination.isReprocessable()) {
                        Log.i(TAG, "Testing mandatory reprocessable stream combination: " +
                                combination.getDescription() + " on camera: " + id);
                        testMandatoryReprocessableStreamCombination(id, combination, maxResolution);
                    }
                }
            } finally {
                closeDevice(id);
            }
        }
    }

    private void testMandatoryReprocessableStreamCombination(String cameraId,
            MandatoryStreamCombination combination, boolean maxResolution)  throws Exception {
        // Test reprocess stream combination
        testMandatoryReprocessableStreamCombination(cameraId, combination,
                /*substituteY8*/false, /*substituteHeic*/false, maxResolution/*maxResolution*/);
        if (maxResolution) {
            // Maximum resolution mode doesn't guarantee HEIC and Y8 streams.
            return;
        }

        // Test substituting YUV_888 format with Y8 format in reprocess stream combination.
        if (mStaticInfo.isMonochromeWithY8()) {
            List<MandatoryStreamInformation> streamsInfo = combination.getStreamsInformation();
            boolean substituteY8 = false;
            for (MandatoryStreamInformation streamInfo : streamsInfo) {
                if (streamInfo.getFormat() == ImageFormat.YUV_420_888) {
                    substituteY8 = true;
                }
            }
            if (substituteY8) {
                testMandatoryReprocessableStreamCombination(cameraId, combination,
                        /*substituteY8*/true, /*substituteHeic*/false, false/*maxResolution*/);
            }
        }

        if (mStaticInfo.isHeicSupported()) {
            List<MandatoryStreamInformation> streamsInfo = combination.getStreamsInformation();
            boolean substituteHeic = false;
            for (MandatoryStreamInformation streamInfo : streamsInfo) {
                if (streamInfo.getFormat() == ImageFormat.JPEG) {
                    substituteHeic = true;
                }
            }
            if (substituteHeic) {
                testMandatoryReprocessableStreamCombination(cameraId, combination,
                        /*substituteY8*/false, /*substituteHeic*/true, false/*maxResolution*/);
            }
        }
    }

    private void testMandatoryReprocessableStreamCombination(String cameraId,
            MandatoryStreamCombination combination, boolean substituteY8,
            boolean substituteHeic, boolean maxResolution) throws Exception {

        final int TIMEOUT_MULTIPLIER = maxResolution ? 2 : 1;
        final int TIMEOUT_FOR_RESULT_MS = 5000 * TIMEOUT_MULTIPLIER;
        final int NUM_REPROCESS_CAPTURES_PER_CONFIG = 3;

        StreamCombinationTargets targets = new StreamCombinationTargets();
        ArrayList<Surface> defaultOutputSurfaces = new ArrayList<>();
        ArrayList<Surface> allOutputSurfaces = new ArrayList<>();
        List<OutputConfiguration> outputConfigs = new ArrayList<>();
        List<Surface> uhOutputSurfaces = new ArrayList<Surface>();
        ImageReader inputReader = null;
        ImageWriter inputWriter = null;
        SimpleImageReaderListener inputReaderListener = new SimpleImageReaderListener();
        SimpleCaptureCallback inputCaptureListener = new SimpleCaptureCallback();
        SimpleCaptureCallback reprocessOutputCaptureListener = new SimpleCaptureCallback();

        List<MandatoryStreamInformation> streamInfo = combination.getStreamsInformation();
        assertTrue("Reprocessable stream combinations should have at least 3 or more streams",
                (streamInfo != null) && (streamInfo.size() >= 3));

        assertTrue("The first mandatory stream information in a reprocessable combination must " +
                "always be input", streamInfo.get(0).isInput());

        List<Size> inputSizes = streamInfo.get(0).getAvailableSizes();
        int inputFormat = streamInfo.get(0).getFormat();
        if (substituteY8 && (inputFormat == ImageFormat.YUV_420_888)) {
            inputFormat = ImageFormat.Y8;
        }

        Log.i(TAG, "testMandatoryReprocessableStreamCombination: " +
                combination.getDescription() + ", substituteY8 = " + substituteY8 +
                ", substituteHeic = " + substituteHeic);
        try {
            // The second stream information entry is the ZSL stream, which is configured
            // separately.
            List<MandatoryStreamInformation> mandatoryStreamInfos = null;
            mandatoryStreamInfos = new ArrayList<MandatoryStreamInformation>();
            mandatoryStreamInfos = streamInfo.subList(2, streamInfo.size());
            CameraTestUtils.setupConfigurationTargets(mandatoryStreamInfos, targets,
                    outputConfigs, defaultOutputSurfaces, uhOutputSurfaces,
                    NUM_REPROCESS_CAPTURES_PER_CONFIG,
                    substituteY8, substituteHeic, null/*overridePhysicalCameraId*/,
                    /*multiResStreamConfig*/null, mHandler);
            allOutputSurfaces.addAll(defaultOutputSurfaces);
            allOutputSurfaces.addAll(uhOutputSurfaces);
            InputConfiguration inputConfig = new InputConfiguration(inputSizes.get(0).getWidth(),
                    inputSizes.get(0).getHeight(), inputFormat);

            // For each config, YUV and JPEG outputs will be tested. (For YUV/Y8 reprocessing,
            // the YUV/Y8 ImageReader for input is also used for output.)
            final boolean inputIsYuv = inputConfig.getFormat() == ImageFormat.YUV_420_888;
            final boolean inputIsY8 = inputConfig.getFormat() == ImageFormat.Y8;
            final boolean useYuv = inputIsYuv || targets.mYuvTargets.size() > 0;
            final boolean useY8 = inputIsY8 || targets.mY8Targets.size() > 0;
            final int totalNumReprocessCaptures =  NUM_REPROCESS_CAPTURES_PER_CONFIG *
                    (maxResolution ? 1 : (((inputIsYuv || inputIsY8) ? 1 : 0) +
                    (substituteHeic ? targets.mHeicTargets.size() : targets.mJpegTargets.size()) +
                    (useYuv ? targets.mYuvTargets.size() : targets.mY8Targets.size())));

            // It needs 1 input buffer for each reprocess capture + the number of buffers
            // that will be used as outputs.
            inputReader = ImageReader.newInstance(inputConfig.getWidth(), inputConfig.getHeight(),
                    inputConfig.getFormat(),
                    totalNumReprocessCaptures + NUM_REPROCESS_CAPTURES_PER_CONFIG);
            inputReader.setOnImageAvailableListener(inputReaderListener, mHandler);
            allOutputSurfaces.add(inputReader.getSurface());

            checkSessionConfigurationWithSurfaces(mCamera, mHandler, allOutputSurfaces,
                    inputConfig, SessionConfiguration.SESSION_REGULAR, /*defaultSupport*/ true,
                    String.format("Session configuration query %s failed",
                    combination.getDescription()));

            // Verify we can create a reprocessable session with the input and all outputs.
            BlockingSessionCallback sessionListener = new BlockingSessionCallback();
            CameraCaptureSession session = configureReprocessableCameraSession(mCamera,
                    inputConfig, allOutputSurfaces, sessionListener, mHandler);
            inputWriter = ImageWriter.newInstance(session.getInputSurface(),
                    totalNumReprocessCaptures);

            // Prepare a request for reprocess input
            CaptureRequest.Builder builder = mCamera.createCaptureRequest(
                    CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG);
            builder.addTarget(inputReader.getSurface());
            if (maxResolution) {
                builder.set(CaptureRequest.SENSOR_PIXEL_MODE,
                        CameraMetadata.SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION);
            }

            for (int i = 0; i < totalNumReprocessCaptures; i++) {
                session.capture(builder.build(), inputCaptureListener, mHandler);
            }

            List<CaptureRequest> reprocessRequests = new ArrayList<>();
            List<Surface> reprocessOutputs = new ArrayList<>();

            if (maxResolution) {
                if (uhOutputSurfaces.size() == 0) { // RAW -> RAW reprocessing
                    reprocessOutputs.add(inputReader.getSurface());
                } else {
                    for (Surface surface : uhOutputSurfaces) {
                        reprocessOutputs.add(surface);
                    }
                }
            } else {
                if (inputIsYuv || inputIsY8) {
                    reprocessOutputs.add(inputReader.getSurface());
                }

                for (ImageReader reader : targets.mJpegTargets) {
                    reprocessOutputs.add(reader.getSurface());
                }

                for (ImageReader reader : targets.mHeicTargets) {
                    reprocessOutputs.add(reader.getSurface());
                }

                for (ImageReader reader : targets.mYuvTargets) {
                    reprocessOutputs.add(reader.getSurface());
                }

                for (ImageReader reader : targets.mY8Targets) {
                    reprocessOutputs.add(reader.getSurface());
                }
            }

            for (int i = 0; i < NUM_REPROCESS_CAPTURES_PER_CONFIG; i++) {
                for (Surface output : reprocessOutputs) {
                    TotalCaptureResult result = inputCaptureListener.getTotalCaptureResult(
                            TIMEOUT_FOR_RESULT_MS);
                    builder =  mCamera.createReprocessCaptureRequest(result);
                    inputWriter.queueInputImage(
                            inputReaderListener.getImage(TIMEOUT_FOR_RESULT_MS));
                    builder.addTarget(output);
                    reprocessRequests.add(builder.build());
                }
            }

            session.captureBurst(reprocessRequests, reprocessOutputCaptureListener, mHandler);

            for (int i = 0; i < reprocessOutputs.size() * NUM_REPROCESS_CAPTURES_PER_CONFIG; i++) {
                TotalCaptureResult result = reprocessOutputCaptureListener.getTotalCaptureResult(
                        TIMEOUT_FOR_RESULT_MS);
            }
        } catch (Throwable e) {
            mCollector.addMessage(String.format("Reprocess stream combination %s failed due to: %s",
                    combination.getDescription(), e.getMessage()));
        } finally {
            inputReaderListener.drain();
            reprocessOutputCaptureListener.drain();
            targets.close();

            if (inputReader != null) {
                inputReader.close();
            }

            if (inputWriter != null) {
                inputWriter.close();
            }
        }
    }

    @Test
    public void testBasicTriggerSequence() throws Exception {

        for (String id : getCameraIdsUnderTest()) {
            Log.i(TAG, String.format("Testing Camera %s", id));

            try {
                // Legacy devices do not support precapture trigger; don't test devices that
                // can't focus
                StaticMetadata staticInfo = mAllStaticInfo.get(id);
                if (staticInfo.isHardwareLevelLegacy() || !staticInfo.hasFocuser()) {
                    continue;
                }
                // Depth-only devices won't support AE
                if (!staticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + id + " does not support color outputs, skipping");
                    continue;
                }

                openDevice(id);
                int[] availableAfModes = mStaticInfo.getAfAvailableModesChecked();
                int[] availableAeModes = mStaticInfo.getAeAvailableModesChecked();

                for (int afMode : availableAfModes) {
                    if (afMode == CameraCharacteristics.CONTROL_AF_MODE_OFF ||
                            afMode == CameraCharacteristics.CONTROL_AF_MODE_EDOF) {
                        // Only test AF modes that have meaningful trigger behavior
                        continue;
                    }

                    for (int aeMode : availableAeModes) {
                        if (aeMode ==  CameraCharacteristics.CONTROL_AE_MODE_OFF) {
                            // Only test AE modes that have meaningful trigger behavior
                            continue;
                        }

                        SurfaceTexture preview = new SurfaceTexture(/*random int*/ 1);

                        CaptureRequest.Builder previewRequest =
                                prepareTriggerTestSession(preview, aeMode, afMode);

                        SimpleCaptureCallback captureListener =
                                new CameraTestUtils.SimpleCaptureCallback();

                        mCameraSession.setRepeatingRequest(previewRequest.build(), captureListener,
                                mHandler);

                        // Cancel triggers

                        cancelTriggersAndWait(previewRequest, captureListener, afMode);

                        //
                        // Standard sequence - AF trigger then AE trigger

                        if (VERBOSE) {
                            Log.v(TAG, String.format("Triggering AF"));
                        }

                        previewRequest.set(CaptureRequest.CONTROL_AF_TRIGGER,
                                CaptureRequest.CONTROL_AF_TRIGGER_START);
                        previewRequest.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);

                        CaptureRequest triggerRequest = previewRequest.build();
                        mCameraSession.capture(triggerRequest, captureListener, mHandler);

                        CaptureResult triggerResult = captureListener.getCaptureResultForRequest(
                                triggerRequest, MAX_RESULT_STATE_CHANGE_WAIT_FRAMES);
                        int afState = triggerResult.get(CaptureResult.CONTROL_AF_STATE);
                        boolean focusComplete = false;

                        for (int i = 0;
                             i < MAX_TRIGGER_SEQUENCE_FRAMES && !focusComplete;
                             i++) {

                            focusComplete = verifyAfSequence(afMode, afState, focusComplete);

                            CaptureResult focusResult = captureListener.getCaptureResult(
                                    CameraTestUtils.CAPTURE_RESULT_TIMEOUT_MS);
                            afState = focusResult.get(CaptureResult.CONTROL_AF_STATE);
                        }

                        assertTrue("Focusing never completed!", focusComplete);

                        // Standard sequence - Part 2 AE trigger

                        if (VERBOSE) {
                            Log.v(TAG, String.format("Triggering AE"));
                        }

                        previewRequest.set(CaptureRequest.CONTROL_AF_TRIGGER,
                                CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
                        previewRequest.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);

                        triggerRequest = previewRequest.build();
                        mCameraSession.capture(triggerRequest, captureListener, mHandler);

                        triggerResult = captureListener.getCaptureResultForRequest(
                                triggerRequest, MAX_RESULT_STATE_CHANGE_WAIT_FRAMES);

                        int aeState = triggerResult.get(CaptureResult.CONTROL_AE_STATE);

                        boolean precaptureComplete = false;

                        for (int i = 0;
                             i < MAX_TRIGGER_SEQUENCE_FRAMES && !precaptureComplete;
                             i++) {

                            precaptureComplete = verifyAeSequence(aeState, precaptureComplete);

                            CaptureResult precaptureResult = captureListener.getCaptureResult(
                                CameraTestUtils.CAPTURE_RESULT_TIMEOUT_MS);
                            aeState = precaptureResult.get(CaptureResult.CONTROL_AE_STATE);
                        }

                        assertTrue("Precapture sequence never completed!", precaptureComplete);

                        for (int i = 0; i < MAX_RESULT_STATE_POSTCHANGE_WAIT_FRAMES; i++) {
                            CaptureResult postPrecaptureResult = captureListener.getCaptureResult(
                                CameraTestUtils.CAPTURE_RESULT_TIMEOUT_MS);
                            aeState = postPrecaptureResult.get(CaptureResult.CONTROL_AE_STATE);
                            assertTrue("Late transition to PRECAPTURE state seen",
                                    aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE);
                        }

                        // Done

                        stopCapture(/*fast*/ false);
                        preview.release();
                    }

                }

            } finally {
                closeDevice(id);
            }
        }

    }

    @Test
    public void testSimultaneousTriggers() throws Exception {
        for (String id : getCameraIdsUnderTest()) {
            Log.i(TAG, String.format("Testing Camera %s", id));

            try {
                // Legacy devices do not support precapture trigger; don't test devices that
                // can't focus
                StaticMetadata staticInfo = mAllStaticInfo.get(id);
                if (staticInfo.isHardwareLevelLegacy() || !staticInfo.hasFocuser()) {
                    continue;
                }
                // Depth-only devices won't support AE
                if (!staticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + id + " does not support color outputs, skipping");
                    continue;
                }

                openDevice(id);
                int[] availableAfModes = mStaticInfo.getAfAvailableModesChecked();
                int[] availableAeModes = mStaticInfo.getAeAvailableModesChecked();

                for (int afMode : availableAfModes) {
                    if (afMode == CameraCharacteristics.CONTROL_AF_MODE_OFF ||
                            afMode == CameraCharacteristics.CONTROL_AF_MODE_EDOF) {
                        // Only test AF modes that have meaningful trigger behavior
                        continue;
                    }

                    for (int aeMode : availableAeModes) {
                        if (aeMode ==  CameraCharacteristics.CONTROL_AE_MODE_OFF) {
                            // Only test AE modes that have meaningful trigger behavior
                            continue;
                        }

                        SurfaceTexture preview = new SurfaceTexture(/*random int*/ 1);

                        CaptureRequest.Builder previewRequest =
                                prepareTriggerTestSession(preview, aeMode, afMode);

                        SimpleCaptureCallback captureListener =
                                new CameraTestUtils.SimpleCaptureCallback();

                        mCameraSession.setRepeatingRequest(previewRequest.build(), captureListener,
                                mHandler);

                        // Cancel triggers

                        cancelTriggersAndWait(previewRequest, captureListener, afMode);

                        //
                        // Trigger AF and AE together

                        if (VERBOSE) {
                            Log.v(TAG, String.format("Triggering AF and AE together"));
                        }

                        previewRequest.set(CaptureRequest.CONTROL_AF_TRIGGER,
                                CaptureRequest.CONTROL_AF_TRIGGER_START);
                        previewRequest.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);

                        CaptureRequest triggerRequest = previewRequest.build();
                        mCameraSession.capture(triggerRequest, captureListener, mHandler);

                        CaptureResult triggerResult = captureListener.getCaptureResultForRequest(
                                triggerRequest, MAX_RESULT_STATE_CHANGE_WAIT_FRAMES);
                        int aeState = triggerResult.get(CaptureResult.CONTROL_AE_STATE);
                        int afState = triggerResult.get(CaptureResult.CONTROL_AF_STATE);

                        boolean precaptureComplete = false;
                        boolean focusComplete = false;

                        for (int i = 0;
                             i < MAX_TRIGGER_SEQUENCE_FRAMES &&
                                     !(focusComplete && precaptureComplete);
                             i++) {

                            focusComplete = verifyAfSequence(afMode, afState, focusComplete);
                            precaptureComplete = verifyAeSequence(aeState, precaptureComplete);

                            CaptureResult sequenceResult = captureListener.getCaptureResult(
                                    CameraTestUtils.CAPTURE_RESULT_TIMEOUT_MS);
                            afState = sequenceResult.get(CaptureResult.CONTROL_AF_STATE);
                            aeState = sequenceResult.get(CaptureResult.CONTROL_AE_STATE);
                        }

                        assertTrue("Precapture sequence never completed!", precaptureComplete);
                        assertTrue("Focus sequence never completed!", focusComplete);

                        // Done

                        stopCapture(/*fast*/ false);
                        preview.release();

                    }
                }
            } finally {
                closeDevice(id);
            }
        }
    }

    @Test
    public void testAfThenAeTrigger() throws Exception {
        for (String id : getCameraIdsUnderTest()) {
            Log.i(TAG, String.format("Testing Camera %s", id));

            try {
                // Legacy devices do not support precapture trigger; don't test devices that
                // can't focus
                StaticMetadata staticInfo = mAllStaticInfo.get(id);
                if (staticInfo.isHardwareLevelLegacy() || !staticInfo.hasFocuser()) {
                    continue;
                }
                // Depth-only devices won't support AE
                if (!staticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + id + " does not support color outputs, skipping");
                    continue;
                }

                openDevice(id);
                int[] availableAfModes = mStaticInfo.getAfAvailableModesChecked();
                int[] availableAeModes = mStaticInfo.getAeAvailableModesChecked();

                for (int afMode : availableAfModes) {
                    if (afMode == CameraCharacteristics.CONTROL_AF_MODE_OFF ||
                            afMode == CameraCharacteristics.CONTROL_AF_MODE_EDOF) {
                        // Only test AF modes that have meaningful trigger behavior
                        continue;
                    }

                    for (int aeMode : availableAeModes) {
                        if (aeMode ==  CameraCharacteristics.CONTROL_AE_MODE_OFF) {
                            // Only test AE modes that have meaningful trigger behavior
                            continue;
                        }

                        SurfaceTexture preview = new SurfaceTexture(/*random int*/ 1);

                        CaptureRequest.Builder previewRequest =
                                prepareTriggerTestSession(preview, aeMode, afMode);

                        SimpleCaptureCallback captureListener =
                                new CameraTestUtils.SimpleCaptureCallback();

                        mCameraSession.setRepeatingRequest(previewRequest.build(), captureListener,
                                mHandler);

                        // Cancel triggers

                        cancelTriggersAndWait(previewRequest, captureListener, afMode);

                        //
                        // AF with AE a request later

                        if (VERBOSE) {
                            Log.v(TAG, "Trigger AF, then AE trigger on next request");
                        }

                        previewRequest.set(CaptureRequest.CONTROL_AF_TRIGGER,
                                CaptureRequest.CONTROL_AF_TRIGGER_START);
                        previewRequest.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);

                        CaptureRequest triggerRequest = previewRequest.build();
                        mCameraSession.capture(triggerRequest, captureListener, mHandler);

                        previewRequest.set(CaptureRequest.CONTROL_AF_TRIGGER,
                                CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
                        previewRequest.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);

                        CaptureRequest triggerRequest2 = previewRequest.build();
                        mCameraSession.capture(triggerRequest2, captureListener, mHandler);

                        CaptureResult triggerResult = captureListener.getCaptureResultForRequest(
                                triggerRequest, MAX_RESULT_STATE_CHANGE_WAIT_FRAMES);
                        int afState = triggerResult.get(CaptureResult.CONTROL_AF_STATE);

                        boolean precaptureComplete = false;
                        boolean focusComplete = false;

                        focusComplete = verifyAfSequence(afMode, afState, focusComplete);

                        triggerResult = captureListener.getCaptureResultForRequest(
                                triggerRequest2, MAX_RESULT_STATE_CHANGE_WAIT_FRAMES);
                        afState = triggerResult.get(CaptureResult.CONTROL_AF_STATE);
                        int aeState = triggerResult.get(CaptureResult.CONTROL_AE_STATE);

                        for (int i = 0;
                             i < MAX_TRIGGER_SEQUENCE_FRAMES &&
                                     !(focusComplete && precaptureComplete);
                             i++) {

                            focusComplete = verifyAfSequence(afMode, afState, focusComplete);
                            precaptureComplete = verifyAeSequence(aeState, precaptureComplete);

                            CaptureResult sequenceResult = captureListener.getCaptureResult(
                                    CameraTestUtils.CAPTURE_RESULT_TIMEOUT_MS);
                            afState = sequenceResult.get(CaptureResult.CONTROL_AF_STATE);
                            aeState = sequenceResult.get(CaptureResult.CONTROL_AE_STATE);
                        }

                        assertTrue("Precapture sequence never completed!", precaptureComplete);
                        assertTrue("Focus sequence never completed!", focusComplete);

                        // Done

                        stopCapture(/*fast*/ false);
                        preview.release();

                    }
                }
            } finally {
                closeDevice(id);
            }
        }
    }

    @Test
    public void testAeThenAfTrigger() throws Exception {
        for (String id : getCameraIdsUnderTest()) {
            Log.i(TAG, String.format("Testing Camera %s", id));

            try {
                // Legacy devices do not support precapture trigger; don't test devices that
                // can't focus
                StaticMetadata staticInfo = mAllStaticInfo.get(id);
                if (staticInfo.isHardwareLevelLegacy() || !staticInfo.hasFocuser()) {
                    continue;
                }
                // Depth-only devices won't support AE
                if (!staticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + id + " does not support color outputs, skipping");
                    continue;
                }

                openDevice(id);
                int[] availableAfModes = mStaticInfo.getAfAvailableModesChecked();
                int[] availableAeModes = mStaticInfo.getAeAvailableModesChecked();

                for (int afMode : availableAfModes) {
                    if (afMode == CameraCharacteristics.CONTROL_AF_MODE_OFF ||
                            afMode == CameraCharacteristics.CONTROL_AF_MODE_EDOF) {
                        // Only test AF modes that have meaningful trigger behavior
                        continue;
                    }

                    for (int aeMode : availableAeModes) {
                        if (aeMode ==  CameraCharacteristics.CONTROL_AE_MODE_OFF) {
                            // Only test AE modes that have meaningful trigger behavior
                            continue;
                        }

                        SurfaceTexture preview = new SurfaceTexture(/*random int*/ 1);

                        CaptureRequest.Builder previewRequest =
                                prepareTriggerTestSession(preview, aeMode, afMode);

                        SimpleCaptureCallback captureListener =
                                new CameraTestUtils.SimpleCaptureCallback();

                        mCameraSession.setRepeatingRequest(previewRequest.build(), captureListener,
                                mHandler);

                        // Cancel triggers

                        cancelTriggersAndWait(previewRequest, captureListener, afMode);

                        //
                        // AE with AF a request later

                        if (VERBOSE) {
                            Log.v(TAG, "Trigger AE, then AF trigger on next request");
                        }

                        previewRequest.set(CaptureRequest.CONTROL_AF_TRIGGER,
                                CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
                        previewRequest.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);

                        CaptureRequest triggerRequest = previewRequest.build();
                        mCameraSession.capture(triggerRequest, captureListener, mHandler);

                        previewRequest.set(CaptureRequest.CONTROL_AF_TRIGGER,
                                CaptureRequest.CONTROL_AF_TRIGGER_START);
                        previewRequest.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);

                        CaptureRequest triggerRequest2 = previewRequest.build();
                        mCameraSession.capture(triggerRequest2, captureListener, mHandler);

                        CaptureResult triggerResult = captureListener.getCaptureResultForRequest(
                                triggerRequest, MAX_RESULT_STATE_CHANGE_WAIT_FRAMES);
                        int aeState = triggerResult.get(CaptureResult.CONTROL_AE_STATE);

                        boolean precaptureComplete = false;
                        boolean focusComplete = false;

                        precaptureComplete = verifyAeSequence(aeState, precaptureComplete);

                        triggerResult = captureListener.getCaptureResultForRequest(
                                triggerRequest2, MAX_RESULT_STATE_CHANGE_WAIT_FRAMES);
                        int afState = triggerResult.get(CaptureResult.CONTROL_AF_STATE);
                        aeState = triggerResult.get(CaptureResult.CONTROL_AE_STATE);

                        for (int i = 0;
                             i < MAX_TRIGGER_SEQUENCE_FRAMES &&
                                     !(focusComplete && precaptureComplete);
                             i++) {

                            focusComplete = verifyAfSequence(afMode, afState, focusComplete);
                            precaptureComplete = verifyAeSequence(aeState, precaptureComplete);

                            CaptureResult sequenceResult = captureListener.getCaptureResult(
                                    CameraTestUtils.CAPTURE_RESULT_TIMEOUT_MS);
                            afState = sequenceResult.get(CaptureResult.CONTROL_AF_STATE);
                            aeState = sequenceResult.get(CaptureResult.CONTROL_AE_STATE);
                        }

                        assertTrue("Precapture sequence never completed!", precaptureComplete);
                        assertTrue("Focus sequence never completed!", focusComplete);

                        // Done

                        stopCapture(/*fast*/ false);
                        preview.release();

                    }
                }
            } finally {
                closeDevice(id);
            }
        }
    }

    @Test
    public void testAeAndAfCausality() throws Exception {

        for (String id : getCameraIdsUnderTest()) {
            Log.i(TAG, String.format("Testing Camera %s", id));

            try {
                // Legacy devices do not support precapture trigger; don't test devices that
                // can't focus
                StaticMetadata staticInfo = mAllStaticInfo.get(id);
                if (staticInfo.isHardwareLevelLegacy() || !staticInfo.hasFocuser()) {
                    continue;
                }
                // Depth-only devices won't support AE
                if (!staticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + id + " does not support color outputs, skipping");
                    continue;
                }

                openDevice(id);
                int[] availableAfModes = mStaticInfo.getAfAvailableModesChecked();
                int[] availableAeModes = mStaticInfo.getAeAvailableModesChecked();
                final int maxPipelineDepth = mStaticInfo.getCharacteristics().get(
                        CameraCharacteristics.REQUEST_PIPELINE_MAX_DEPTH);

                for (int afMode : availableAfModes) {
                    if (afMode == CameraCharacteristics.CONTROL_AF_MODE_OFF ||
                            afMode == CameraCharacteristics.CONTROL_AF_MODE_EDOF) {
                        // Only test AF modes that have meaningful trigger behavior
                        continue;
                    }
                    for (int aeMode : availableAeModes) {
                        if (aeMode ==  CameraCharacteristics.CONTROL_AE_MODE_OFF) {
                            // Only test AE modes that have meaningful trigger behavior
                            continue;
                        }

                        SurfaceTexture preview = new SurfaceTexture(/*random int*/ 1);

                        CaptureRequest.Builder previewRequest =
                                prepareTriggerTestSession(preview, aeMode, afMode);

                        SimpleCaptureCallback captureListener =
                                new CameraTestUtils.SimpleCaptureCallback();

                        mCameraSession.setRepeatingRequest(previewRequest.build(), captureListener,
                                mHandler);

                        List<CaptureRequest> triggerRequests =
                                new ArrayList<CaptureRequest>(maxPipelineDepth+1);
                        for (int i = 0; i < maxPipelineDepth; i++) {
                            triggerRequests.add(previewRequest.build());
                        }

                        // Cancel triggers
                        cancelTriggersAndWait(previewRequest, captureListener, afMode);

                        //
                        // Standard sequence - Part 1 AF trigger

                        if (VERBOSE) {
                            Log.v(TAG, String.format("Triggering AF"));
                        }

                        previewRequest.set(CaptureRequest.CONTROL_AF_TRIGGER,
                                CaptureRequest.CONTROL_AF_TRIGGER_START);
                        previewRequest.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);
                        triggerRequests.add(previewRequest.build());

                        mCameraSession.captureBurst(triggerRequests, captureListener, mHandler);

                        TotalCaptureResult[] triggerResults =
                                captureListener.getTotalCaptureResultsForRequests(
                                triggerRequests, MAX_RESULT_STATE_CHANGE_WAIT_FRAMES);
                        for (int i = 0; i < maxPipelineDepth; i++) {
                            TotalCaptureResult triggerResult = triggerResults[i];
                            int afState = triggerResult.get(CaptureResult.CONTROL_AF_STATE);
                            int afTrigger = triggerResult.get(CaptureResult.CONTROL_AF_TRIGGER);

                            verifyStartingAfState(afMode, afState);
                            assertTrue(String.format("In AF mode %s, previous AF_TRIGGER must not "
                                    + "be START before TRIGGER_START",
                                    StaticMetadata.getAfModeName(afMode)),
                                    afTrigger != CaptureResult.CONTROL_AF_TRIGGER_START);
                        }

                        int afState =
                                triggerResults[maxPipelineDepth].get(CaptureResult.CONTROL_AF_STATE);
                        boolean focusComplete = false;
                        for (int i = 0;
                             i < MAX_TRIGGER_SEQUENCE_FRAMES && !focusComplete;
                             i++) {

                            focusComplete = verifyAfSequence(afMode, afState, focusComplete);

                            CaptureResult focusResult = captureListener.getCaptureResult(
                                    CameraTestUtils.CAPTURE_RESULT_TIMEOUT_MS);
                            afState = focusResult.get(CaptureResult.CONTROL_AF_STATE);
                        }

                        assertTrue("Focusing never completed!", focusComplete);

                        // Standard sequence - Part 2 AE trigger

                        if (VERBOSE) {
                            Log.v(TAG, String.format("Triggering AE"));
                        }
                        // Remove AF trigger request
                        triggerRequests.remove(maxPipelineDepth);

                        previewRequest.set(CaptureRequest.CONTROL_AF_TRIGGER,
                                CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
                        previewRequest.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
                        triggerRequests.add(previewRequest.build());

                        mCameraSession.captureBurst(triggerRequests, captureListener, mHandler);

                        triggerResults = captureListener.getTotalCaptureResultsForRequests(
                                triggerRequests, MAX_RESULT_STATE_CHANGE_WAIT_FRAMES);

                        for (int i = 0; i < maxPipelineDepth; i++) {
                            TotalCaptureResult triggerResult = triggerResults[i];
                            int aeState = triggerResult.get(CaptureResult.CONTROL_AE_STATE);
                            int aeTrigger = triggerResult.get(
                                    CaptureResult.CONTROL_AE_PRECAPTURE_TRIGGER);

                            assertTrue(String.format("In AE mode %s, previous AE_TRIGGER must not "
                                    + "be START before TRIGGER_START",
                                    StaticMetadata.getAeModeName(aeMode)),
                                    aeTrigger != CaptureResult.CONTROL_AE_PRECAPTURE_TRIGGER_START);
                            assertTrue(String.format("In AE mode %s, previous AE_STATE must not be"
                                    + " PRECAPTURE_TRIGGER before TRIGGER_START",
                                    StaticMetadata.getAeModeName(aeMode)),
                                    aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE);
                        }

                        // Stand sequence - Part 3 Cancel AF trigger
                        if (VERBOSE) {
                            Log.v(TAG, String.format("Cancel AF trigger"));
                        }
                        // Remove AE trigger request
                        triggerRequests.remove(maxPipelineDepth);
                        previewRequest.set(CaptureRequest.CONTROL_AF_TRIGGER,
                                CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
                        triggerRequests.add(previewRequest.build());

                        mCameraSession.captureBurst(triggerRequests, captureListener, mHandler);
                        triggerResults = captureListener.getTotalCaptureResultsForRequests(
                                triggerRequests, MAX_RESULT_STATE_CHANGE_WAIT_FRAMES);
                        for (int i = 0; i < maxPipelineDepth; i++) {
                            TotalCaptureResult triggerResult = triggerResults[i];
                            afState = triggerResult.get(CaptureResult.CONTROL_AF_STATE);
                            int afTrigger = triggerResult.get(CaptureResult.CONTROL_AF_TRIGGER);

                            assertTrue(
                                    String.format("In AF mode %s, previous AF_TRIGGER must not " +
                                    "be CANCEL before TRIGGER_CANCEL",
                                    StaticMetadata.getAfModeName(afMode)),
                                    afTrigger != CaptureResult.CONTROL_AF_TRIGGER_CANCEL);
                            assertTrue(
                                    String.format("In AF mode %s, previous AF_STATE must be LOCKED"
                                    + " before CANCEL, but is %s",
                                    StaticMetadata.getAfModeName(afMode),
                                    StaticMetadata.AF_STATE_NAMES[afState]),
                                    afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                                    afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED);
                        }

                        stopCapture(/*fast*/ false);
                        preview.release();
                    }

                }

            } finally {
                closeDevice(id);
            }
        }

    }

    @Test
    public void testAbandonRepeatingRequestSurface() throws Exception {
        for (String id : getCameraIdsUnderTest()) {
            Log.i(TAG, String.format(
                    "Testing Camera %s for abandoning surface of a repeating request", id));

            StaticMetadata staticInfo = mAllStaticInfo.get(id);
            if (!staticInfo.isColorOutputSupported()) {
                Log.i(TAG, "Camera " + id + " does not support color output, skipping");
                continue;
            }

            openDevice(id);

            try {

                SurfaceTexture preview = new SurfaceTexture(/*random int*/ 1);
                Surface previewSurface = new Surface(preview);

                CaptureRequest.Builder previewRequest = preparePreviewTestSession(preview);
                SimpleCaptureCallback captureListener = new CameraTestUtils.SimpleCaptureCallback();

                int sequenceId = mCameraSession.setRepeatingRequest(previewRequest.build(),
                        captureListener, mHandler);

                for (int i = 0; i < PREVIEW_WARMUP_FRAMES; i++) {
                    captureListener.getTotalCaptureResult(CAPTURE_TIMEOUT);
                }

                // Abandon preview surface.
                preview.release();

                // Check onCaptureSequenceCompleted is received.
                long sequenceLastFrameNumber = captureListener.getCaptureSequenceLastFrameNumber(
                        sequenceId, CAPTURE_TIMEOUT);

                mCameraSession.stopRepeating();

                // Find the last frame number received in results and failures.
                long lastFrameNumber = -1;
                while (captureListener.hasMoreResults()) {
                    TotalCaptureResult result = captureListener.getTotalCaptureResult(
                            CAPTURE_TIMEOUT);
                    if (lastFrameNumber < result.getFrameNumber()) {
                        lastFrameNumber = result.getFrameNumber();
                    }
                }

                while (captureListener.hasMoreFailures()) {
                    ArrayList<CaptureFailure> failures = captureListener.getCaptureFailures(
                            /*maxNumFailures*/ 1);
                    for (CaptureFailure failure : failures) {
                        if (lastFrameNumber < failure.getFrameNumber()) {
                            lastFrameNumber = failure.getFrameNumber();
                        }
                    }
                }

                // Verify the last frame number received from capture sequence completed matches the
                // the last frame number of the results and failures.
                assertEquals(String.format("Last frame number from onCaptureSequenceCompleted " +
                        "(%d) doesn't match the last frame number received from " +
                        "results/failures (%d)", sequenceLastFrameNumber, lastFrameNumber),
                        sequenceLastFrameNumber, lastFrameNumber);
            } finally {
                closeDevice(id);
            }
        }
    }

    @Test
    public void testConfigureInvalidSensorPixelModes() throws Exception {
        for (String id : getCameraIdsUnderTest()) {
            // Go through given, stream configuration map, add the incorrect sensor pixel mode
            // to an OutputConfiguration, make sure the session configuration fails.
            CameraCharacteristics chars = mCameraManager.getCameraCharacteristics(id);
            StreamConfigurationMap defaultStreamConfigMap =
                    chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            StreamConfigurationMap maxStreamConfigMap =
                    chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION);
            openDevice(id);
            try {
                verifyBasicSensorPixelModes(id, maxStreamConfigMap, defaultStreamConfigMap,
                        /*maxResolution*/ false);
                verifyBasicSensorPixelModes(id, maxStreamConfigMap, defaultStreamConfigMap,
                        /*maxResolution*/ true);
            } finally {
                closeDevice(id);
            }
        }
    }

    @Test
    public void testConfigureAbandonedSurface() throws Exception {
        for (String id : getCameraIdsUnderTest()) {
            Log.i(TAG, String.format(
                    "Testing Camera %s for configuring abandoned surface", id));

            openDevice(id);
            try {
                SurfaceTexture preview = new SurfaceTexture(/*random int*/ 1);
                Surface previewSurface = new Surface(preview);

                // Abandon preview SurfaceTexture.
                preview.release();

                try {
                    CaptureRequest.Builder previewRequest = preparePreviewTestSession(preview);
                    fail("Configuring abandoned surfaces must fail!");
                } catch (IllegalArgumentException e) {
                    // expected
                    Log.i(TAG, "normal session check passed");
                }

                // Try constrained high speed session/requests
                if (!mStaticInfo.isConstrainedHighSpeedVideoSupported()) {
                    continue;
                }

                List<Surface> surfaces = new ArrayList<>();
                surfaces.add(previewSurface);
                CameraCaptureSession.StateCallback sessionListener =
                        mock(CameraCaptureSession.StateCallback.class);

                try {
                    mCamera.createConstrainedHighSpeedCaptureSession(surfaces,
                            sessionListener, mHandler);
                    fail("Configuring abandoned surfaces in high speed session must fail!");
                } catch (IllegalArgumentException e) {
                    // expected
                    Log.i(TAG, "high speed session check 1 passed");
                }

                // Also try abandone the Surface directly
                previewSurface.release();

                try {
                    mCamera.createConstrainedHighSpeedCaptureSession(surfaces,
                            sessionListener, mHandler);
                    fail("Configuring abandoned surfaces in high speed session must fail!");
                } catch (IllegalArgumentException e) {
                    // expected
                    Log.i(TAG, "high speed session check 2 passed");
                }
            } finally {
                closeDevice(id);
            }
        }
    }

    @Test
    public void testAfSceneChange() throws Exception {
        final int NUM_FRAMES_VERIFIED = 3;

        for (String id : getCameraIdsUnderTest()) {
            Log.i(TAG, String.format("Testing Camera %s for AF scene change", id));

            StaticMetadata staticInfo =
                    new StaticMetadata(mCameraManager.getCameraCharacteristics(id));
            if (!staticInfo.isAfSceneChangeSupported()) {
                continue;
            }

            openDevice(id);

            try {
                SurfaceTexture preview = new SurfaceTexture(/*random int*/ 1);
                Surface previewSurface = new Surface(preview);

                CaptureRequest.Builder previewRequest = preparePreviewTestSession(preview);
                SimpleCaptureCallback previewListener = new CameraTestUtils.SimpleCaptureCallback();

                int[] availableAfModes = mStaticInfo.getAfAvailableModesChecked();

                // Test AF scene change in each AF mode.
                for (int afMode : availableAfModes) {
                    previewRequest.set(CaptureRequest.CONTROL_AF_MODE, afMode);

                    int sequenceId = mCameraSession.setRepeatingRequest(previewRequest.build(),
                            previewListener, mHandler);

                    // Verify that AF scene change is NOT_DETECTED or DETECTED.
                    for (int i = 0; i < NUM_FRAMES_VERIFIED; i++) {
                        TotalCaptureResult result =
                            previewListener.getTotalCaptureResult(CAPTURE_TIMEOUT);
                        mCollector.expectKeyValueIsIn(result,
                                CaptureResult.CONTROL_AF_SCENE_CHANGE,
                                CaptureResult.CONTROL_AF_SCENE_CHANGE_DETECTED,
                                CaptureResult.CONTROL_AF_SCENE_CHANGE_NOT_DETECTED);
                    }

                    mCameraSession.stopRepeating();
                    previewListener.getCaptureSequenceLastFrameNumber(sequenceId, CAPTURE_TIMEOUT);
                    previewListener.drain();
                }
            } finally {
                closeDevice(id);
            }
        }
    }

    @Test
    public void testOisDataMode() throws Exception {
        final int NUM_FRAMES_VERIFIED = 3;

        for (String id : getCameraIdsUnderTest()) {
            Log.i(TAG, String.format("Testing Camera %s for OIS mode", id));

            StaticMetadata staticInfo =
                    new StaticMetadata(mCameraManager.getCameraCharacteristics(id));
            if (!staticInfo.isOisDataModeSupported()) {
                continue;
            }

            openDevice(id);

            try {
                SurfaceTexture preview = new SurfaceTexture(/*random int*/ 1);
                Surface previewSurface = new Surface(preview);

                CaptureRequest.Builder previewRequest = preparePreviewTestSession(preview);
                SimpleCaptureCallback previewListener = new CameraTestUtils.SimpleCaptureCallback();

                int[] availableOisDataModes = staticInfo.getCharacteristics().get(
                        CameraCharacteristics.STATISTICS_INFO_AVAILABLE_OIS_DATA_MODES);

                // Test each OIS data mode
                for (int oisMode : availableOisDataModes) {
                    previewRequest.set(CaptureRequest.STATISTICS_OIS_DATA_MODE, oisMode);

                    int sequenceId = mCameraSession.setRepeatingRequest(previewRequest.build(),
                            previewListener, mHandler);

                    // Check OIS data in each mode.
                    for (int i = 0; i < NUM_FRAMES_VERIFIED; i++) {
                        TotalCaptureResult result =
                            previewListener.getTotalCaptureResult(CAPTURE_TIMEOUT);

                        OisSample[] oisSamples = result.get(CaptureResult.STATISTICS_OIS_SAMPLES);

                        if (oisMode == CameraCharacteristics.STATISTICS_OIS_DATA_MODE_OFF) {
                            mCollector.expectKeyValueEquals(result,
                                    CaptureResult.STATISTICS_OIS_DATA_MODE,
                                    CaptureResult.STATISTICS_OIS_DATA_MODE_OFF);
                            mCollector.expectTrue("OIS samples reported in OIS_DATA_MODE_OFF",
                                    oisSamples == null || oisSamples.length == 0);

                        } else if (oisMode == CameraCharacteristics.STATISTICS_OIS_DATA_MODE_ON) {
                            mCollector.expectKeyValueEquals(result,
                                    CaptureResult.STATISTICS_OIS_DATA_MODE,
                                    CaptureResult.STATISTICS_OIS_DATA_MODE_ON);
                            mCollector.expectTrue("OIS samples not reported in OIS_DATA_MODE_ON",
                                    oisSamples != null && oisSamples.length != 0);
                        } else {
                            mCollector.addMessage(String.format("Invalid OIS mode: %d", oisMode));
                        }
                    }

                    mCameraSession.stopRepeating();
                    previewListener.getCaptureSequenceLastFrameNumber(sequenceId, CAPTURE_TIMEOUT);
                    previewListener.drain();
                }
            } finally {
                closeDevice(id);
            }
        }
    }

    private CaptureRequest.Builder preparePreviewTestSession(SurfaceTexture preview)
            throws Exception {
        Surface previewSurface = new Surface(preview);

        preview.setDefaultBufferSize(640, 480);

        ArrayList<Surface> sessionOutputs = new ArrayList<>();
        sessionOutputs.add(previewSurface);

        createSession(sessionOutputs);

        CaptureRequest.Builder previewRequest =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

        previewRequest.addTarget(previewSurface);

        return previewRequest;
    }

    private CaptureRequest.Builder prepareTriggerTestSession(
            SurfaceTexture preview, int aeMode, int afMode) throws Exception {
        Log.i(TAG, String.format("Testing AE mode %s, AF mode %s",
                        StaticMetadata.getAeModeName(aeMode),
                        StaticMetadata.getAfModeName(afMode)));

        CaptureRequest.Builder previewRequest = preparePreviewTestSession(preview);
        previewRequest.set(CaptureRequest.CONTROL_AE_MODE, aeMode);
        previewRequest.set(CaptureRequest.CONTROL_AF_MODE, afMode);

        return previewRequest;
    }

    private void cancelTriggersAndWait(CaptureRequest.Builder previewRequest,
            SimpleCaptureCallback captureListener, int afMode) throws Exception {
        previewRequest.set(CaptureRequest.CONTROL_AF_TRIGGER,
                CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
        previewRequest.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL);

        CaptureRequest triggerRequest = previewRequest.build();
        mCameraSession.capture(triggerRequest, captureListener, mHandler);

        // Wait for a few frames to initialize 3A

        CaptureResult previewResult = null;
        int afState;
        int aeState;

        for (int i = 0; i < PREVIEW_WARMUP_FRAMES; i++) {
            previewResult = captureListener.getCaptureResult(
                    CameraTestUtils.CAPTURE_RESULT_TIMEOUT_MS);
            if (VERBOSE) {
                afState = previewResult.get(CaptureResult.CONTROL_AF_STATE);
                aeState = previewResult.get(CaptureResult.CONTROL_AE_STATE);
                Log.v(TAG, String.format("AF state: %s, AE state: %s",
                                StaticMetadata.AF_STATE_NAMES[afState],
                                StaticMetadata.AE_STATE_NAMES[aeState]));
            }
        }

        // Verify starting states

        afState = previewResult.get(CaptureResult.CONTROL_AF_STATE);
        aeState = previewResult.get(CaptureResult.CONTROL_AE_STATE);

        verifyStartingAfState(afMode, afState);

        // After several frames, AE must no longer be in INACTIVE state
        assertTrue(String.format("AE state must be SEARCHING, CONVERGED, " +
                        "or FLASH_REQUIRED, is %s", StaticMetadata.AE_STATE_NAMES[aeState]),
                aeState == CaptureResult.CONTROL_AE_STATE_SEARCHING ||
                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED ||
                aeState == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED);
    }

    private boolean configsContain(StreamConfigurationMap configs, int format, Size size) {
        Size[] sizes = configs.getOutputSizes(format);
        if (sizes == null) {
            return false;
        }
        return Arrays.asList(sizes).contains(size);
    }

    private void verifyBasicSensorPixelModes(String id, StreamConfigurationMap maxResConfigs,
            StreamConfigurationMap defaultConfigs, boolean maxResolution) throws Exception {
        // Go through StreamConfiguration map, set up OutputConfiguration and add the opposite
        // sensorPixelMode.
        final int MIN_RESULT_COUNT = 3;
        assertTrue("Default stream config map must be present for id: " + id,
            defaultConfigs != null);
        if (maxResConfigs == null) {
            Log.i(TAG, "camera id " + id + " has no StreamConfigurationMap for max resolution " +
                ", skipping verifyBasicSensorPixelModes");
            return;
        }
        StreamConfigurationMap chosenConfigs = maxResolution ? maxResConfigs : defaultConfigs;
        StreamConfigurationMap otherConfigs = maxResolution ? defaultConfigs : maxResConfigs;
        OutputConfiguration outputConfig = null;
        for (int format : chosenConfigs.getOutputFormats()) {
            Size targetSize = CameraTestUtils.getMaxSize(chosenConfigs.getOutputSizes(format));
            if (configsContain(otherConfigs, format, targetSize)) {
                // Since both max res and default stream configuration maps contain this size,
                // both sensor pixel modes are valid.
                Log.v(TAG, "camera id " + id + " 'other' configs with maxResolution" +
                    maxResolution + " contains the format: " + format + " size: " + targetSize +
                    " skipping");
                continue;
            }
            // Create outputConfiguration with this size and format
            SimpleImageReaderListener imageListener = new SimpleImageReaderListener();
            SurfaceTexture textureTarget = null;
            ImageReader readerTarget = null;
            if (format == ImageFormat.PRIVATE) {
                textureTarget = new SurfaceTexture(1);
                textureTarget.setDefaultBufferSize(targetSize.getWidth(), targetSize.getHeight());
                outputConfig = new OutputConfiguration(new Surface(textureTarget));
            } else {
                readerTarget = ImageReader.newInstance(targetSize.getWidth(),
                        targetSize.getHeight(), format, MIN_RESULT_COUNT);
                readerTarget.setOnImageAvailableListener(imageListener, mHandler);
                outputConfig = new OutputConfiguration(readerTarget.getSurface());
            }
            try {
                int invalidSensorPixelMode =
                        maxResolution ? CameraMetadata.SENSOR_PIXEL_MODE_DEFAULT :
                                CameraMetadata.SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION;

                outputConfig.addSensorPixelModeUsed(invalidSensorPixelMode);
                CameraCaptureSession.StateCallback sessionListener =
                        mock(CameraCaptureSession.StateCallback.class);
                List<OutputConfiguration> outputs = new ArrayList<>();
                outputs.add(outputConfig);
                CameraCaptureSession session =
                        CameraTestUtils.configureCameraSessionWithConfig(mCamera, outputs,
                                sessionListener, mHandler);
                String desc = "verifyBasicSensorPixelModes : Format : " + format + " size: " +
                        targetSize.toString() + " maxResolution : " + maxResolution;
                verify(sessionListener, timeout(CONFIGURE_TIMEOUT).atLeastOnce().description(desc)).
                        onConfigureFailed(any(CameraCaptureSession.class));
                verify(sessionListener, never().description(desc)).
                        onConfigured(any(CameraCaptureSession.class));

                // Remove the invalid sensor pixel mode, session configuration should succeed
                sessionListener = mock(CameraCaptureSession.StateCallback.class);
                outputConfig.removeSensorPixelModeUsed(invalidSensorPixelMode);
                CameraTestUtils.configureCameraSessionWithConfig(mCamera, outputs,
                        sessionListener, mHandler);
                verify(sessionListener, timeout(CONFIGURE_TIMEOUT).atLeastOnce().description(desc)).
                        onConfigured(any(CameraCaptureSession.class));
                verify(sessionListener, never().description(desc)).
                        onConfigureFailed(any(CameraCaptureSession.class));
            } finally {
                if (textureTarget != null) {
                    textureTarget.release();
                }

                if (readerTarget != null) {
                    readerTarget.close();
                }
            }
        }
    }

    private void verifyStartingAfState(int afMode, int afState) {
        switch (afMode) {
            case CaptureResult.CONTROL_AF_MODE_AUTO:
            case CaptureResult.CONTROL_AF_MODE_MACRO:
                assertTrue(String.format("AF state not INACTIVE, is %s",
                                StaticMetadata.AF_STATE_NAMES[afState]),
                        afState == CaptureResult.CONTROL_AF_STATE_INACTIVE);
                break;
            case CaptureResult.CONTROL_AF_MODE_CONTINUOUS_PICTURE:
            case CaptureResult.CONTROL_AF_MODE_CONTINUOUS_VIDEO:
                // After several frames, AF must no longer be in INACTIVE state
                assertTrue(String.format("In AF mode %s, AF state not PASSIVE_SCAN" +
                                ", PASSIVE_FOCUSED, or PASSIVE_UNFOCUSED, is %s",
                                StaticMetadata.getAfModeName(afMode),
                                StaticMetadata.AF_STATE_NAMES[afState]),
                        afState == CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN ||
                        afState == CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED ||
                        afState == CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED);
                break;
            default:
                fail("unexpected af mode");
        }
    }

    private boolean verifyAfSequence(int afMode, int afState, boolean focusComplete) {
        if (focusComplete) {
            assertTrue(String.format("AF Mode %s: Focus lock lost after convergence: AF state: %s",
                            StaticMetadata.getAfModeName(afMode),
                            StaticMetadata.AF_STATE_NAMES[afState]),
                    afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                    afState ==CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED);
            return focusComplete;
        }
        if (VERBOSE) {
            Log.v(TAG, String.format("AF mode: %s, AF state: %s",
                            StaticMetadata.getAfModeName(afMode),
                            StaticMetadata.AF_STATE_NAMES[afState]));
        }
        switch (afMode) {
            case CaptureResult.CONTROL_AF_MODE_AUTO:
            case CaptureResult.CONTROL_AF_MODE_MACRO:
                assertTrue(String.format("AF mode %s: Unexpected AF state %s",
                                StaticMetadata.getAfModeName(afMode),
                                StaticMetadata.AF_STATE_NAMES[afState]),
                        afState == CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN ||
                        afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                        afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED);
                focusComplete =
                        (afState != CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN);
                break;
            case CaptureResult.CONTROL_AF_MODE_CONTINUOUS_PICTURE:
                assertTrue(String.format("AF mode %s: Unexpected AF state %s",
                                StaticMetadata.getAfModeName(afMode),
                                StaticMetadata.AF_STATE_NAMES[afState]),
                        afState == CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN ||
                        afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                        afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED);
                focusComplete =
                        (afState != CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN);
                break;
            case CaptureResult.CONTROL_AF_MODE_CONTINUOUS_VIDEO:
                assertTrue(String.format("AF mode %s: Unexpected AF state %s",
                                StaticMetadata.getAfModeName(afMode),
                                StaticMetadata.AF_STATE_NAMES[afState]),
                        afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                        afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED);
                focusComplete = true;
                break;
            default:
                fail("Unexpected AF mode: " + StaticMetadata.getAfModeName(afMode));
        }
        return focusComplete;
    }

    private boolean verifyAeSequence(int aeState, boolean precaptureComplete) {
        if (precaptureComplete) {
            assertTrue("Precapture state seen after convergence",
                    aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE);
            return precaptureComplete;
        }
        if (VERBOSE) {
            Log.v(TAG, String.format("AE state: %s", StaticMetadata.AE_STATE_NAMES[aeState]));
        }
        switch (aeState) {
            case CaptureResult.CONTROL_AE_STATE_PRECAPTURE:
                // scan still continuing
                break;
            case CaptureResult.CONTROL_AE_STATE_CONVERGED:
            case CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED:
                // completed
                precaptureComplete = true;
                break;
            default:
                fail(String.format("Precapture sequence transitioned to "
                                + "state %s incorrectly!", StaticMetadata.AE_STATE_NAMES[aeState]));
                break;
        }
        return precaptureComplete;
    }

    /**
     * Test for making sure that all expected mandatory stream combinations are present and
     * advertised accordingly.
     */
    @Test
    public void testVerifyMandatoryOutputCombinationTables() throws Exception {
        final int[][] legacyCombinations = {
            // Simple preview, GPU video processing, or no-preview video recording
            {PRIV, MAXIMUM},
            // No-viewfinder still image capture
            {JPEG, MAXIMUM},
            // In-application video/image processing
            {YUV,  MAXIMUM},
            // Standard still imaging.
            {PRIV, PREVIEW,  JPEG, MAXIMUM},
            // In-app processing plus still capture.
            {YUV,  PREVIEW,  JPEG, MAXIMUM},
            // Standard recording.
            {PRIV, PREVIEW,  PRIV, PREVIEW},
            // Preview plus in-app processing.
            {PRIV, PREVIEW,  YUV,  PREVIEW},
            // Still capture plus in-app processing.
            {PRIV, PREVIEW,  YUV,  PREVIEW,  JPEG, MAXIMUM}
        };

        final int[][] limitedCombinations = {
            // High-resolution video recording with preview.
            {PRIV, PREVIEW,  PRIV, RECORD },
            // High-resolution in-app video processing with preview.
            {PRIV, PREVIEW,  YUV , RECORD },
            // Two-input in-app video processing.
            {YUV , PREVIEW,  YUV , RECORD },
            // High-resolution recording with video snapshot.
            {PRIV, PREVIEW,  PRIV, RECORD,   JPEG, RECORD  },
            // High-resolution in-app processing with video snapshot.
            {PRIV, PREVIEW,  YUV,  RECORD,   JPEG, RECORD  },
            // Two-input in-app processing with still capture.
            {YUV , PREVIEW,  YUV,  PREVIEW,  JPEG, MAXIMUM }
        };

        final int[][] burstCombinations = {
            // Maximum-resolution GPU processing with preview.
            {PRIV, PREVIEW,  PRIV, MAXIMUM },
            // Maximum-resolution in-app processing with preview.
            {PRIV, PREVIEW,  YUV,  MAXIMUM },
            // Maximum-resolution two-input in-app processing.
            {YUV,  PREVIEW,  YUV,  MAXIMUM },
        };

        final int[][] fullCombinations = {
            // Video recording with maximum-size video snapshot.
            {PRIV, PREVIEW,  PRIV, PREVIEW,  JPEG, MAXIMUM },
            // Standard video recording plus maximum-resolution in-app processing.
            {YUV,  VGA,      PRIV, PREVIEW,  YUV,  MAXIMUM },
            // Preview plus two-input maximum-resolution in-app processing.
            {YUV,  VGA,      YUV,  PREVIEW,  YUV,  MAXIMUM }
        };

        final int[][] rawCombinations = {
            // No-preview DNG capture.
            {RAW,  MAXIMUM },
            // Standard DNG capture.
            {PRIV, PREVIEW,  RAW,  MAXIMUM },
            // In-app processing plus DNG capture.
            {YUV,  PREVIEW,  RAW,  MAXIMUM },
            // Video recording with DNG capture.
            {PRIV, PREVIEW,  PRIV, PREVIEW,  RAW, MAXIMUM},
            // Preview with in-app processing and DNG capture.
            {PRIV, PREVIEW,  YUV,  PREVIEW,  RAW, MAXIMUM},
            // Two-input in-app processing plus DNG capture.
            {YUV,  PREVIEW,  YUV,  PREVIEW,  RAW, MAXIMUM},
            // Still capture with simultaneous JPEG and DNG.
            {PRIV, PREVIEW,  JPEG, MAXIMUM,  RAW, MAXIMUM},
            // In-app processing with simultaneous JPEG and DNG.
            {YUV,  PREVIEW,  JPEG, MAXIMUM,  RAW, MAXIMUM}
        };

        final int[][] level3Combinations = {
            // In-app viewfinder analysis with dynamic selection of output format
            {PRIV, PREVIEW, PRIV, VGA, YUV, MAXIMUM, RAW, MAXIMUM},
            // In-app viewfinder analysis with dynamic selection of output format
            {PRIV, PREVIEW, PRIV, VGA, JPEG, MAXIMUM, RAW, MAXIMUM}
        };

        final int[][] concurrentStreamCombinations = {
            //In-app video / image processing.
            {YUV, S1440P},
            // In-app viewfinder analysis.
            {PRIV, S1440P},
            // No viewfinder still image capture.
            {JPEG, S1440P},
            // Standard still imaging.
            {YUV, S720P, JPEG, S1440P},
            {PRIV, S720P, JPEG, S1440P},
            // In-app video / processing with preview.
            {YUV, S720P, YUV, S1440P},
            {YUV, S720P, PRIV, S1440P},
            {PRIV, S720P, YUV, S1440P},
            {PRIV, S720P, PRIV, S1440P}
        };

        final int[][] ultraHighResolutionsCombinations = {
            // Ultra high res still image capture with preview.
            {YUV, MAX_RES, PRIV, PREVIEW},
            {YUV, MAX_RES, YUV, PREVIEW},
            {JPEG, MAX_RES, PRIV, PREVIEW},
            {JPEG, MAX_RES, YUV, PREVIEW},
            {RAW, MAX_RES, PRIV, PREVIEW},
            {RAW, MAX_RES, YUV, PREVIEW},
            // Ultra high res still capture with preview + app based RECORD size analysis.
            {YUV, MAX_RES, PRIV, PREVIEW, PRIV, RECORD},
            {YUV, MAX_RES, PRIV, PREVIEW, YUV, RECORD},
            {JPEG, MAX_RES, PRIV, PREVIEW, PRIV, RECORD},
            {JPEG, MAX_RES, PRIV, PREVIEW, YUV, RECORD},
            {RAW, MAX_RES, PRIV, PREVIEW, PRIV, RECORD},
            {RAW, MAX_RES, PRIV, PREVIEW, YUV, RECORD},
            // Ultra high res still image capture with preview + default sensor pixel mode analysis
            // stream
            {YUV, MAX_RES, PRIV, PREVIEW, JPEG, MAXIMUM},
            {YUV, MAX_RES, PRIV, PREVIEW, YUV, MAXIMUM},
            {YUV, MAX_RES, PRIV, PREVIEW, RAW, MAXIMUM},
            {JPEG, MAX_RES, PRIV, PREVIEW, JPEG, MAXIMUM},
            {JPEG, MAX_RES, PRIV, PREVIEW, YUV, MAXIMUM},
            {JPEG, MAX_RES, PRIV, PREVIEW, RAW, MAXIMUM},
            {RAW, MAX_RES, PRIV, PREVIEW, JPEG, MAXIMUM},
            {RAW, MAX_RES, PRIV, PREVIEW, YUV, MAXIMUM},
            {RAW, MAX_RES, PRIV, PREVIEW, RAW, MAXIMUM},
        };

        final int[][] tenBitOutputCombinations = {
            // Simple preview, GPU video processing, or no-preview video recording.
            {PRIV, MAXIMUM},
            // In-application video/image processing.
            {YUV, MAXIMUM},
            // Standard still imaging.
            {PRIV, PREVIEW, JPEG, MAXIMUM},
            // Maximum-resolution in-app processing with preview.
            {PRIV, PREVIEW, YUV, MAXIMUM},
            // Maximum-resolution two-input in-app processing.
            {YUV, PREVIEW, YUV, MAXIMUM},
            // High-resolution video recording with preview.
            {PRIV, PREVIEW, PRIV, RECORD},
            // High-resolution recording with in-app snapshot.
            {PRIV, PREVIEW, PRIV, RECORD, YUV, RECORD},
            // High-resolution recording with video snapshot.
            {PRIV, PREVIEW, PRIV, RECORD, JPEG, RECORD}
        };

        final int[][] streamUseCaseCombinations = {
            // Simple preview or in-app image processing.
            {YUV, PREVIEW, USE_CASE_PREVIEW},
            {PRIV, PREVIEW, USE_CASE_PREVIEW},
            // Simple video recording or in-app video processing.
            {YUV, RECORD, USE_CASE_VIDEO_RECORD},
            {PRIV, RECORD, USE_CASE_VIDEO_RECORD},
            // Simple JPEG or YUV still image capture.
            {YUV, MAXIMUM, USE_CASE_STILL_CAPTURE},
            {JPEG, MAXIMUM, USE_CASE_STILL_CAPTURE},
            // Multi-purpose stream for preview, video and still image capture.
            {YUV, S1440P, USE_CASE_PREVIEW_VIDEO_STILL},
            {PRIV, S1440P, USE_CASE_PREVIEW_VIDEO_STILL},
            // Simple video call.
            {YUV, S1440P, USE_CASE_VIDEO_CALL},
            {PRIV, S1440P, USE_CASE_VIDEO_CALL},
            // Preview with JPEG or YUV still image capture.
            {PRIV, PREVIEW, USE_CASE_PREVIEW, YUV, MAXIMUM, USE_CASE_STILL_CAPTURE},
            {PRIV, PREVIEW, USE_CASE_PREVIEW, JPEG, MAXIMUM, USE_CASE_STILL_CAPTURE},
            // Preview with video recording or in-app video processing.
            {PRIV, PREVIEW, USE_CASE_PREVIEW, YUV, RECORD, USE_CASE_VIDEO_RECORD},
            {PRIV, PREVIEW, USE_CASE_PREVIEW, PRIV, RECORD, USE_CASE_VIDEO_RECORD},
            // Preview with in-application image processing.
            {PRIV, PREVIEW, USE_CASE_PREVIEW, YUV, PREVIEW, USE_CASE_PREVIEW},
            // Preview with video call.
            {PRIV, PREVIEW, USE_CASE_PREVIEW, YUV, S1440P, USE_CASE_VIDEO_CALL},
            {PRIV, PREVIEW, USE_CASE_PREVIEW, PRIV, S1440P, USE_CASE_VIDEO_CALL},
            // {Multi-purpose stream with JPEG or YUV still capture.
            {YUV, S1440P, USE_CASE_PREVIEW_VIDEO_STILL, YUV, MAXIMUM, USE_CASE_STILL_CAPTURE},
            {YUV, S1440P, USE_CASE_PREVIEW_VIDEO_STILL, JPEG, MAXIMUM, USE_CASE_STILL_CAPTURE},
            {PRIV, S1440P, USE_CASE_PREVIEW_VIDEO_STILL, YUV, MAXIMUM, USE_CASE_STILL_CAPTURE},
            {PRIV, S1440P, USE_CASE_PREVIEW_VIDEO_STILL, JPEG, MAXIMUM, USE_CASE_STILL_CAPTURE},
            // YUV and JPEG concurrent still image capture (for testing).
            {YUV, PREVIEW, USE_CASE_STILL_CAPTURE, JPEG, MAXIMUM, USE_CASE_STILL_CAPTURE},
            // Preview, video record and JPEG video snapshot.
            {PRIV, PREVIEW, USE_CASE_PREVIEW, YUV, RECORD, USE_CASE_VIDEO_RECORD, JPEG, RECORD,
                    USE_CASE_STILL_CAPTURE},
            {PRIV, PREVIEW, USE_CASE_PREVIEW, PRIV, RECORD, USE_CASE_VIDEO_RECORD, JPEG, RECORD,
                    USE_CASE_STILL_CAPTURE},
            // Preview, in-application image processing, and JPEG still image capture.
            {PRIV, PREVIEW, USE_CASE_PREVIEW, YUV, PREVIEW, USE_CASE_PREVIEW, JPEG, MAXIMUM,
                    USE_CASE_STILL_CAPTURE},
        };

        final int[][] streamUseCaseCroppedRawCombinations = {
            // Cropped RAW still image capture without preview
            {RAW, MAXIMUM, USE_CASE_CROPPED_RAW},

            // Preview / In-app processing with cropped RAW still image capture
            {PRIV, PREVIEW, USE_CASE_PREVIEW, RAW, MAXIMUM, USE_CASE_CROPPED_RAW},
            {YUV, PREVIEW, USE_CASE_PREVIEW, RAW, MAXIMUM, USE_CASE_CROPPED_RAW},

            // Preview / In-app processing with YUV and cropped RAW still image capture
            {PRIV, PREVIEW, USE_CASE_PREVIEW, YUV, MAXIMUM, USE_CASE_STILL_CAPTURE, RAW, MAXIMUM,
              USE_CASE_CROPPED_RAW},
            {YUV, PREVIEW, USE_CASE_PREVIEW, YUV, MAXIMUM, USE_CASE_STILL_CAPTURE, RAW, MAXIMUM,
              USE_CASE_CROPPED_RAW},

            // Preview / In-app processing with JPEG and cropped RAW still image capture
            {PRIV, PREVIEW, USE_CASE_PREVIEW, JPEG, MAXIMUM, USE_CASE_STILL_CAPTURE, RAW, MAXIMUM,
              USE_CASE_CROPPED_RAW},
            {YUV, PREVIEW, USE_CASE_PREVIEW, JPEG, MAXIMUM, USE_CASE_STILL_CAPTURE, RAW, MAXIMUM,
              USE_CASE_CROPPED_RAW},

            // Preview with in-app processing / video recording and cropped RAW snapshot
            {PRIV, PREVIEW, USE_CASE_PREVIEW, PRIV, PREVIEW, USE_CASE_VIDEO_RECORD, RAW, MAXIMUM,
              USE_CASE_CROPPED_RAW},
            {PRIV, PREVIEW, USE_CASE_PREVIEW, YUV, PREVIEW, USE_CASE_PREVIEW, RAW, MAXIMUM,
              USE_CASE_CROPPED_RAW},

            // Two input in-app processing with RAW
            {YUV, PREVIEW, USE_CASE_PREVIEW, YUV, PREVIEW, USE_CASE_PREVIEW, RAW, MAXIMUM,
              USE_CASE_CROPPED_RAW},
        };


        final int[][] previewStabilizationCombinations = {
            // Stabilized preview, GPU video processing, or no-preview stabilized video recording.
            {PRIV, S1440P},
            {YUV, S1440P},
            // Standard still imaging with stabilized preview.
            {PRIV, S1440P, JPEG, MAXIMUM},
            {PRIV, S1440P, YUV, MAXIMUM},
            {YUV, S1440P, JPEG, MAXIMUM},
            {YUV, S1440P, YUV, MAXIMUM},
            // High-resolution recording with stabilized preview and recording stream.
            {PRIV, PREVIEW, PRIV, S1440P},
            {PRIV, PREVIEW, YUV, S1440P},
            {YUV, PREVIEW, PRIV, S1440P},
            {YUV, PREVIEW, YUV, S1440P},
        };

        final int[][][] tables =
                {legacyCombinations, limitedCombinations, burstCombinations, fullCombinations,
                 rawCombinations, level3Combinations, concurrentStreamCombinations,
                 ultraHighResolutionsCombinations, tenBitOutputCombinations,
                 previewStabilizationCombinations};

        final int[][][] useCaseTables = {streamUseCaseCombinations,
                streamUseCaseCroppedRawCombinations};

        validityCheckConfigurationTables(tables);
        validityCheckConfigurationTables(useCaseTables, /*useCaseSpecified*/ true);

        for (String id : getCameraIdsUnderTest()) {
            openDevice(id);
            MandatoryStreamCombination[] combinations =
                    mStaticInfo.getCharacteristics().get(
                            CameraCharacteristics.SCALER_MANDATORY_STREAM_COMBINATIONS);
            if ((combinations == null) || (combinations.length == 0)) {
                Log.i(TAG, "No mandatory stream combinations for camera: " + id + " skip test");
                closeDevice(id);
                continue;
            }

            MaxStreamSizes maxSizes = new MaxStreamSizes(mStaticInfo, id, mContext);
            try {
                if (mStaticInfo.isColorOutputSupported()) {
                    for (int[] c : legacyCombinations) {
                        assertTrue(String.format("Expected static stream combination: %s not "
                                    + "found among the available mandatory combinations",
                                    maxSizes.combinationToString(c)),
                                isMandatoryCombinationAvailable(c, maxSizes, combinations));
                    }
                }

                if (!mStaticInfo.isHardwareLevelLegacy()) {
                    if (mStaticInfo.isColorOutputSupported()) {
                        for (int[] c : limitedCombinations) {
                            assertTrue(String.format("Expected static stream combination: %s not "
                                        + "found among the available mandatory combinations",
                                        maxSizes.combinationToString(c)),
                                    isMandatoryCombinationAvailable(c, maxSizes, combinations));
                        }
                    }

                    if (mStaticInfo.isCapabilitySupported(
                            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE)) {
                        for (int[] c : burstCombinations) {
                            assertTrue(String.format("Expected static stream combination: %s not "
                                        + "found among the available mandatory combinations",
                                        maxSizes.combinationToString(c)),
                                    isMandatoryCombinationAvailable(c, maxSizes, combinations));
                        }
                    }

                    if (mStaticInfo.isHardwareLevelAtLeastFull()) {
                        for (int[] c : fullCombinations) {
                            assertTrue(String.format("Expected static stream combination: %s not "
                                        + "found among the available mandatory combinations",
                                        maxSizes.combinationToString(c)),
                                    isMandatoryCombinationAvailable(c, maxSizes, combinations));
                        }
                    }

                    if (mStaticInfo.isCapabilitySupported(
                            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)) {
                        for (int[] c : rawCombinations) {
                            assertTrue(String.format("Expected static stream combination: %s not "
                                        + "found among the available mandatory combinations",
                                        maxSizes.combinationToString(c)),
                                    isMandatoryCombinationAvailable(c, maxSizes, combinations));
                        }
                    }

                    if (mStaticInfo.isHardwareLevelAtLeast(
                            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3)) {
                        for (int[] c: level3Combinations) {
                            assertTrue(String.format("Expected static stream combination: %s not "
                                        + "found among the available mandatory combinations ",
                                        maxSizes.combinationToString(c)),
                                    isMandatoryCombinationAvailable(c, maxSizes, combinations));
                        }
                    }
                }

                Set<Set<String>> concurrentCameraIdCombinations =
                        mCameraManager.getConcurrentCameraIds();
                boolean isConcurrentCamera = false;
                for (Set<String> concurrentCameraIdCombination : concurrentCameraIdCombinations) {
                    if (concurrentCameraIdCombination.contains(id)) {
                        isConcurrentCamera = true;
                        break;
                    }
                }

                if (isConcurrentCamera && mStaticInfo.isCapabilitySupported(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE)) {
                    MandatoryStreamCombination[] mandatoryConcurrentStreamCombinations =
                            mStaticInfo.getCharacteristics().get(
                                    CameraCharacteristics
                                            .SCALER_MANDATORY_CONCURRENT_STREAM_COMBINATIONS);
                    for (int[] c : concurrentStreamCombinations) {
                        assertTrue(String.format("Expected static stream combination: %s not "
                                    + "found among the available mandatory concurrent stream "
                                    + "combinations",
                                    maxSizes.combinationToString(c)),
                                isMandatoryCombinationAvailable(c, maxSizes,
                                        mandatoryConcurrentStreamCombinations));
                    }
                }

                if (mStaticInfo.isCapabilitySupported(
                        CameraCharacteristics
                                .REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR)) {
                    MandatoryStreamCombination[] maxResolutionStreamCombinations =
                        mStaticInfo.getCharacteristics().get(
                                CameraCharacteristics
                                        .SCALER_MANDATORY_MAXIMUM_RESOLUTION_STREAM_COMBINATIONS);
                    for (int[] c : ultraHighResolutionsCombinations) {
                        assertTrue(String.format("Expected static stream combination: %s not "
                                    + "found among the available mandatory max resolution stream "
                                    + "combinations",
                                    maxSizes.combinationToString(c)),
                                isMandatoryCombinationAvailable(c, maxSizes,
                                        maxResolutionStreamCombinations));
                    }
                }

                if (mStaticInfo.isCapabilitySupported(
                        CameraCharacteristics
                                .REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT)) {
                    MandatoryStreamCombination[] mandatoryTenBitOutputCombinations =
                        mStaticInfo.getCharacteristics().get(
                            CameraCharacteristics
                                    .SCALER_MANDATORY_TEN_BIT_OUTPUT_STREAM_COMBINATIONS);
                    for (int[] c : tenBitOutputCombinations) {
                        assertTrue(String.format("Expected static stream combination: %s not "
                                    + "found among the available mandatory 10 bit output "
                                    + "combinations",
                                    maxSizes.combinationToString(c)),
                                isMandatoryCombinationAvailable(c, maxSizes,
                                        mandatoryTenBitOutputCombinations));
                    }
                }

                if (mStaticInfo.isCapabilitySupported(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_STREAM_USE_CASE)) {
                    MandatoryStreamCombination[] mandatoryStreamUseCaseCombinations =
                        mStaticInfo.getCharacteristics().get(
                                CameraCharacteristics
                                        .SCALER_MANDATORY_USE_CASE_STREAM_COMBINATIONS);
                    for (int[] c : streamUseCaseCombinations) {
                        assertTrue(String.format("Expected static stream combination: %s not "
                                    + "found among the available mandatory stream use case "
                                    + "combinations",
                                    maxSizes.combinationToString(c, /*useCaseSpecified*/ true)),
                                isMandatoryCombinationAvailable(c, maxSizes,
                                        /*isInput*/ false,  mandatoryStreamUseCaseCombinations,
                                        /*useCaseSpecified*/ true));
                    }

                    if (mStaticInfo.isCroppedRawStreamUseCaseSupported()) {
                        for (int[] c : streamUseCaseCroppedRawCombinations) {
                            assertTrue(String.format("Expected static stream combination: %s not "
                                        + "found among the available mandatory cropped RAW stream"
                                        + " use case combinations",
                                        maxSizes.combinationToString(c, /*useCaseSpecified*/ true)),
                                    isMandatoryCombinationAvailable(c, maxSizes,
                                            /*isInput*/ false,  mandatoryStreamUseCaseCombinations,
                                            /*useCaseSpecified*/ true));
                        }
                    }
                }

                if (mStaticInfo.isPreviewStabilizationSupported()) {
                    MandatoryStreamCombination[] mandatoryPreviewStabilizationCombinations =
                        mStaticInfo.getCharacteristics().get(
                            CameraCharacteristics
                                .SCALER_MANDATORY_PREVIEW_STABILIZATION_OUTPUT_STREAM_COMBINATIONS);
                    for (int[] c : previewStabilizationCombinations) {
                        assertTrue(String.format("Expected static stream combination: %s not "
                                    + "found among the available mandatory preview stabilization"
                                    + "combinations",
                                    maxSizes.combinationToString(c)),
                                isMandatoryCombinationAvailable(c, maxSizes,
                                        mandatoryPreviewStabilizationCombinations));
                    }
                }
            } finally {
                closeDevice(id);
            }
        }
    }

    /**
     * Test for making sure that all expected reprocessable mandatory stream combinations are
     * present and advertised accordingly.
     */
    @Test
    public void testVerifyReprocessMandatoryOutputCombinationTables() throws Exception {
        final int[][] limitedCombinations = {
            // Input           Outputs
            {PRIV, MAXIMUM,    JPEG, MAXIMUM},
            {YUV , MAXIMUM,    JPEG, MAXIMUM},
            {PRIV, MAXIMUM,    PRIV, PREVIEW, JPEG, MAXIMUM},
            {YUV , MAXIMUM,    PRIV, PREVIEW, JPEG, MAXIMUM},
            {PRIV, MAXIMUM,    YUV , PREVIEW, JPEG, MAXIMUM},
            {YUV , MAXIMUM,    YUV , PREVIEW, JPEG, MAXIMUM},
            {PRIV, MAXIMUM,    YUV , PREVIEW, YUV , PREVIEW, JPEG, MAXIMUM},
            {YUV,  MAXIMUM,    YUV , PREVIEW, YUV , PREVIEW, JPEG, MAXIMUM},
        };

        final int[][] fullCombinations = {
            // Input           Outputs
            {YUV , MAXIMUM,    PRIV, PREVIEW},
            {YUV , MAXIMUM,    YUV , PREVIEW},
            {PRIV, MAXIMUM,    PRIV, PREVIEW, YUV , RECORD},
            {YUV , MAXIMUM,    PRIV, PREVIEW, YUV , RECORD},
            {PRIV, MAXIMUM,    PRIV, PREVIEW, YUV , MAXIMUM},
            {PRIV, MAXIMUM,    YUV , PREVIEW, YUV , MAXIMUM},
            {PRIV, MAXIMUM,    PRIV, PREVIEW, YUV , PREVIEW, JPEG, MAXIMUM},
            {YUV , MAXIMUM,    PRIV, PREVIEW, YUV , PREVIEW, JPEG, MAXIMUM},
        };

        final int[][] rawCombinations = {
            // Input           Outputs
            {PRIV, MAXIMUM,    YUV , PREVIEW, RAW , MAXIMUM},
            {YUV , MAXIMUM,    YUV , PREVIEW, RAW , MAXIMUM},
            {PRIV, MAXIMUM,    PRIV, PREVIEW, YUV , PREVIEW, RAW , MAXIMUM},
            {YUV , MAXIMUM,    PRIV, PREVIEW, YUV , PREVIEW, RAW , MAXIMUM},
            {PRIV, MAXIMUM,    YUV , PREVIEW, YUV , PREVIEW, RAW , MAXIMUM},
            {YUV , MAXIMUM,    YUV , PREVIEW, YUV , PREVIEW, RAW , MAXIMUM},
            {PRIV, MAXIMUM,    PRIV, PREVIEW, JPEG, MAXIMUM, RAW , MAXIMUM},
            {YUV , MAXIMUM,    PRIV, PREVIEW, JPEG, MAXIMUM, RAW , MAXIMUM},
            {PRIV, MAXIMUM,    YUV , PREVIEW, JPEG, MAXIMUM, RAW , MAXIMUM},
            {YUV , MAXIMUM,    YUV , PREVIEW, JPEG, MAXIMUM, RAW , MAXIMUM},
        };

        final int[][] level3Combinations = {
            // Input          Outputs
            // In-app viewfinder analysis with YUV->YUV ZSL and RAW
            {YUV , MAXIMUM,   PRIV, PREVIEW, PRIV, VGA, RAW, MAXIMUM},
            // In-app viewfinder analysis with PRIV->JPEG ZSL and RAW
            {PRIV, MAXIMUM,   PRIV, PREVIEW, PRIV, VGA, RAW, MAXIMUM, JPEG, MAXIMUM},
            // In-app viewfinder analysis with YUV->JPEG ZSL and RAW
            {YUV , MAXIMUM,   PRIV, PREVIEW, PRIV, VGA, RAW, MAXIMUM, JPEG, MAXIMUM},
        };

        final int[][] ultraHighResolutionCombinations = {
            // Input           Outputs
            // RAW remosaic reprocessing with separate preview
            {RAW, MAX_RES,     PRIV, PREVIEW},
            {RAW, MAX_RES,     YUV, PREVIEW},
            // Ultra high res RAW -> JPEG / YUV with separate preview
            {RAW, MAX_RES,     PRIV, PREVIEW, JPEG, MAX_RES},
            {RAW, MAX_RES,     PRIV, PREVIEW, YUV, MAX_RES},
            {RAW, MAX_RES,     YUV, PREVIEW, JPEG, MAX_RES},
            {RAW, MAX_RES,     YUV, PREVIEW, YUV, MAX_RES},
            // Ultra high res PRIV / YUV -> YUV / JPEG reprocessing with separate preview
            {YUV, MAX_RES,     YUV, PREVIEW, JPEG, MAX_RES},
            {YUV, MAX_RES,     PRIV, PREVIEW, JPEG, MAX_RES},
            {PRIV, MAX_RES,    YUV, PREVIEW, JPEG, MAX_RES},
            {PRIV, MAX_RES,    PRIV, PREVIEW, JPEG, MAX_RES},
        };

        final int[][][] TABLES =
                {limitedCombinations, fullCombinations, rawCombinations, level3Combinations,
                 ultraHighResolutionCombinations};

        validityCheckConfigurationTables(TABLES);

        for (String id : getCameraIdsUnderTest()) {
            openDevice(id);
            MandatoryStreamCombination[] cs = mStaticInfo.getCharacteristics().get(
                    CameraCharacteristics.SCALER_MANDATORY_STREAM_COMBINATIONS);
            if ((cs == null) || (cs.length == 0)) {
                Log.i(TAG, "No mandatory stream combinations for camera: " + id + " skip test");
                closeDevice(id);
                continue;
            }

            boolean supportYuvReprocess = mStaticInfo.isCapabilitySupported(
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING);
            boolean supportOpaqueReprocess = mStaticInfo.isCapabilitySupported(
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING);
            if (!supportYuvReprocess && !supportOpaqueReprocess) {
                Log.i(TAG, "No reprocess support for camera: " + id + " skip test");
                closeDevice(id);
                continue;
            }

            MaxStreamSizes maxSizes = new MaxStreamSizes(mStaticInfo, id, mContext);
            try {
                for (int[] c : limitedCombinations) {
                    assertTrue(String.format("Expected static reprocessable stream combination:" +
                                "%s not found among the available mandatory combinations",
                                maxSizes.reprocessCombinationToString(c)),
                            isMandatoryCombinationAvailable(c, maxSizes, /*isInput*/ true, cs));
                }

                if (mStaticInfo.isHardwareLevelAtLeastFull()) {
                    for (int[] c : fullCombinations) {
                        assertTrue(String.format(
                                    "Expected static reprocessable stream combination:" +
                                    "%s not found among the available mandatory combinations",
                                    maxSizes.reprocessCombinationToString(c)),
                                isMandatoryCombinationAvailable(c, maxSizes, /*isInput*/ true, cs));
                    }
                }

                if (mStaticInfo.isCapabilitySupported(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)) {
                    for (int[] c : rawCombinations) {
                        assertTrue(String.format(
                                    "Expected static reprocessable stream combination:" +
                                    "%s not found among the available mandatory combinations",
                                    maxSizes.reprocessCombinationToString(c)),
                                isMandatoryCombinationAvailable(c, maxSizes, /*isInput*/ true, cs));
                    }
                }

                if (mStaticInfo.isHardwareLevelAtLeast(
                            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3)) {
                    for (int[] c : level3Combinations) {
                        assertTrue(String.format(
                                    "Expected static reprocessable stream combination:" +
                                    "%s not found among the available mandatory combinations",
                                    maxSizes.reprocessCombinationToString(c)),
                                isMandatoryCombinationAvailable(c, maxSizes, /*isInput*/ true, cs));
                    }
                }

                if (mStaticInfo.isCapabilitySupported(
                        CameraCharacteristics
                                .REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR)) {
                    MandatoryStreamCombination[] maxResolutionCombinations =
                            mStaticInfo.getCharacteristics().get(
                                    CameraCharacteristics
                                        .SCALER_MANDATORY_MAXIMUM_RESOLUTION_STREAM_COMBINATIONS);
                    for (int[] c : ultraHighResolutionCombinations) {
                        assertTrue(String.format(
                                "Expected static reprocessable stream combination:"
                                    + "%s not found among the available mandatory max resolution"
                                    + "combinations",
                                    maxSizes.reprocessCombinationToString(c)),
                                isMandatoryCombinationAvailable(c, maxSizes, /*isInput*/ true,
                                        maxResolutionCombinations));
                    }
                }
            } finally {
                closeDevice(id);
            }
        }
    }

    private boolean isMandatoryCombinationAvailable(final int[] combination,
            final MaxStreamSizes maxSizes,
            final MandatoryStreamCombination[] availableCombinations) {
        return isMandatoryCombinationAvailable(combination, maxSizes, /*isInput*/ false,
                availableCombinations, /*useCaseSpecified*/ false);
    }

    private boolean isMandatoryCombinationAvailable(final int[] combination,
            final MaxStreamSizes maxSizes, boolean isInput,
            final MandatoryStreamCombination[] availableCombinations) {
        return isMandatoryCombinationAvailable(combination, maxSizes, isInput,
                availableCombinations, /*useCaseSpecified*/ false);
    }

    private boolean isMandatoryCombinationAvailable(final int[] combination,
            final MaxStreamSizes maxSizes, boolean isInput,
            final MandatoryStreamCombination[] availableCombinations, boolean useCaseSpecified) {
        boolean supportYuvReprocess = mStaticInfo.isCapabilitySupported(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING);
        boolean supportOpaqueReprocess = mStaticInfo.isCapabilitySupported(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING);
        // Static combinations to be verified can be composed of multiple entries
        // that have the following layout (format, size). In case "isInput" is set,
        // the first stream configuration entry will contain the input format and size
        // as well as the first matching output.
        // For combinations that contain streamUseCase, the layout will be (format, size, useCase).
        int streamCount = useCaseSpecified ? combination.length / 3 : combination.length / 2;

        List<Pair<Pair<Integer, Boolean>, Size>> currentCombination =
                new ArrayList<Pair<Pair<Integer, Boolean>, Size>>(streamCount);
        List<Integer> streamUseCases = new ArrayList<Integer>(streamCount);
        int i = 0;
        while (i < combination.length) {
            if (isInput && (i == 0)) {
                // Skip the combination if the format is not supported for reprocessing.
                if ((combination[i] == YUV && !supportYuvReprocess) ||
                        (combination[i] == PRIV && !supportOpaqueReprocess)) {
                    return true;
                }
                // Skip the combination if for MAX_RES size, the maximum resolution stream config
                // map doesn't have the given format in getInputFormats().
                if (combination[i + 1] == MAX_RES) {
                    StreamConfigurationMap maxResolutionStreamConfigMap =
                            mStaticInfo.getCharacteristics().get(
                                    CameraCharacteristics
                                            .SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION);
                    int[] inputFormats = maxResolutionStreamConfigMap.getInputFormats();
                    int type = combination[i];
                    if (!Arrays.stream(inputFormats).anyMatch(index -> index == type)) {
                        return true;
                    }
                }
                Size sz = maxSizes.getMaxInputSizeForFormat(combination[i], combination[i + 1]);
                currentCombination.add(Pair.create(Pair.create(new Integer(combination[i]),
                            new Boolean(true)), sz));
                currentCombination.add(Pair.create(Pair.create(new Integer(combination[i]),
                            new Boolean(false)), sz));
            } else {
                Size sz = maxSizes.getOutputSizeForFormat(combination[i], combination[i+1]);
                currentCombination.add(Pair.create(Pair.create(new Integer(combination[i]),
                            new Boolean(false)), sz));
                if (useCaseSpecified) {
                    streamUseCases.add(combination[i + 2]);
                }
            }
            i += 2;
            if (useCaseSpecified) {
                i += 1;
            }
        }

        for (MandatoryStreamCombination c : availableCombinations) {
            List<MandatoryStreamInformation> streamInfoList = c.getStreamsInformation();
            if ((streamInfoList.size() == currentCombination.size()) &&
                    (isInput == c.isReprocessable())) {
                ArrayList<Pair<Pair<Integer, Boolean>, Size>> expected =
                        new ArrayList<Pair<Pair<Integer, Boolean>, Size>>(currentCombination);
                ArrayList<Integer> expectedStreamUseCases = new ArrayList<Integer>(streamUseCases);

                for (MandatoryStreamInformation streamInfo : streamInfoList) {
                    Size maxSize = CameraTestUtils.getMaxSize(
                            streamInfo.getAvailableSizes().toArray(new Size[0]));
                    Pair p = Pair.create(Pair.create(new Integer(streamInfo.getFormat()),
                            new Boolean(streamInfo.isInput())), maxSize);
                    if (expected.contains(p)) {
                        expected.remove(p);
                    }
                    if (useCaseSpecified) {
                        int streamUseCase = (int) streamInfo.getStreamUseCase();
                        if (expectedStreamUseCases.contains(streamUseCase)) {
                            expectedStreamUseCases.remove(Integer.valueOf(streamUseCase));
                        }
                    }
                }

                if (expected.isEmpty() && (!useCaseSpecified || expectedStreamUseCases.isEmpty())) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Verify correctness of the configuration tables.
     */
    private void validityCheckConfigurationTables(final int[][][] tables) throws Exception {
        validityCheckConfigurationTables(tables, false);
    }

    private void validityCheckConfigurationTables(final int[][][] tables, boolean useCaseSpecified)
            throws Exception {
        int tableIdx = 0;
        for (int[][] table : tables) {
            int rowIdx = 0;
            for (int[] row : table) {
                if (!useCaseSpecified) {
                    assertTrue(String.format("Odd number of entries for table %d row %d: %s ",
                                    tableIdx, rowIdx, Arrays.toString(row)),
                            (row.length % 2) == 0);
                } else {
                    assertTrue(String.format("Incorrect number entries for table with use case "
                                             + "specified %d row %d: %s ",
                                    tableIdx, rowIdx, Arrays.toString(row)),
                            (row.length % 3) == 0);
                }

                int i = 0;
                while (i < row.length) {
                    int format = row[i];
                    int maxSize = row[i + 1];
                    assertTrue(String.format("table %d row %d index %d format not valid: %d",
                                    tableIdx, rowIdx, i, format),
                            format == PRIV || format == JPEG || format == YUV
                                    || format == RAW);
                    assertTrue(String.format("table %d row %d index %d max size not valid: %d",
                                    tableIdx, rowIdx, i + 1, maxSize),
                            maxSize == PREVIEW || maxSize == RECORD
                                    || maxSize == MAXIMUM || maxSize == VGA || maxSize == S720P
                                    || maxSize == S1440P || maxSize == MAX_RES);
                    if (useCaseSpecified) {
                        int useCase = row[i + 2];
                        assertTrue(String.format("table %d row %d index %d use case not valid: %d",
                                        tableIdx, rowIdx, i + 2, useCase),
                                useCase == USE_CASE_PREVIEW
                                        || useCase == USE_CASE_PREVIEW_VIDEO_STILL
                                        || useCase == USE_CASE_STILL_CAPTURE
                                        || useCase == USE_CASE_VIDEO_CALL
                                        || useCase == USE_CASE_VIDEO_RECORD
                                        || useCase == USE_CASE_CROPPED_RAW);
                        i += 3;
                    } else {
                        i += 2;
                    }
                }
                rowIdx++;
            }
            tableIdx++;
        }
    }
}
