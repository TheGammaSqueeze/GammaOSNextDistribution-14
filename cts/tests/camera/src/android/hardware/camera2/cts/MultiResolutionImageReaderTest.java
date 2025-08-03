/*
 * Copyright 2021 The Android Open Source Project
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

import android.graphics.ImageFormat;
import android.hardware.HardwareBuffer;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.MultiResolutionImageReader;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.cts.CameraTestUtils.HandlerExecutor;
import android.hardware.camera2.cts.CameraTestUtils.SimpleCaptureCallback;
import android.hardware.camera2.cts.helpers.StaticMetadata;
import android.hardware.camera2.cts.testcases.Camera2AndroidTestCase;
import android.hardware.camera2.params.MandatoryStreamCombination;
import android.hardware.camera2.params.MultiResolutionStreamConfigurationMap;
import android.hardware.camera2.params.MultiResolutionStreamInfo;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static android.hardware.camera2.cts.CameraTestUtils.ImageAndMultiResStreamInfo;
import static android.hardware.camera2.cts.CameraTestUtils.SimpleMultiResolutionImageReaderListener;
import static android.hardware.camera2.cts.CameraTestUtils.StreamCombinationTargets;
import static android.hardware.camera2.cts.CameraTestUtils.checkSessionConfigurationSupported;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import static org.mockito.Mockito.*;

/**
 * Basic test for MultiResolutionImageReader APIs.
 *
 * <p>Below image formats are tested:</p>
 *
 * <p>YUV_420_888: flexible YUV420, it is mandatory format for camera. </p>
 * <p>JPEG: used for JPEG still capture, also mandatory format. </p>
 * <p>PRIVATE: used for input for private reprocessing.</p>
 * <p>RAW: used for raw capture. </p>
 */

@RunWith(Parameterized.class)
public class MultiResolutionImageReaderTest extends Camera2AndroidTestCase {
    private static final String TAG = "MultiResolutionImageReaderTest";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);

    // Number of frame (for streaming requests) to be verified.
    private static final int NUM_FRAME_VERIFIED = 6;
    // Number of frame (for streaming requests) to be verified with log processing time.
    // Max number of images can be accessed simultaneously from ImageReader.
    private static final int MAX_NUM_IMAGES = 5;
    // Capture result timeout
    private static final int WAIT_FOR_RESULT_TIMEOUT_MS = 3000;
    private static final int CAPTURE_TIMEOUT = 1500; //ms

    private MultiResolutionImageReader mMultiResolutionImageReader;
    private SimpleMultiResolutionImageReaderListener mListener;

    @Test
    public void testMultiResolutionCaptureCharacteristics() throws Exception {
        for (String id : getCameraIdsUnderTest()) {
            if (VERBOSE) {
                Log.v(TAG, "Testing multi-resolution capture characteristics for Camera " + id);
            }
            StaticMetadata info = mAllStaticInfo.get(id);
            CameraCharacteristics c = info.getCharacteristics();
            StreamConfigurationMap config = c.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            int[] outputFormats = config.getOutputFormats();
            int[] capabilities = CameraTestUtils.getValueNotNull(
                    c, CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
            boolean isLogicalCamera = CameraTestUtils.contains(capabilities,
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA);
            boolean isUltraHighResCamera = info.isUltraHighResolutionSensor();
            Set<String> physicalCameraIds = c.getPhysicalCameraIds();

            MultiResolutionStreamConfigurationMap multiResolutionMap = c.get(
                    CameraCharacteristics.SCALER_MULTI_RESOLUTION_STREAM_CONFIGURATION_MAP);
            if (multiResolutionMap == null) {
                Log.i(TAG, "Camera " + id + " doesn't support multi-resolution capture.");
                continue;
            }
            if (VERBOSE) {
                Log.v(TAG, "MULTI_RESOLUTION_STREAM_CONFIGURATION_MAP: "
                        + multiResolutionMap.toString());
            }

            int[] multiResolutionOutputFormats = multiResolutionMap.getOutputFormats();
            assertTrue("Camera " + id + " must be a logical multi-camera or ultra high res camera "
                    + "to support multi-resolution capture.",
                    isLogicalCamera || isUltraHighResCamera);

            for (int format : multiResolutionOutputFormats) {
                // Multi-resolution output format must be one of the supports stream configuration
                // map formats, with the exception of RAW. It's valid for the camera device not to
                // support RAW, but the multi-resolution ImageReader does.
                if (format != ImageFormat.RAW_SENSOR && format != ImageFormat.RAW10
                        && format != ImageFormat.RAW12 && format != ImageFormat.RAW_PRIVATE) {
                    assertTrue(String.format("Camera %s: multi-resolution output format %d "
                            + "isn't a supported format", id, format),
                            CameraTestUtils.contains(outputFormats, format));
                }

                Collection<MultiResolutionStreamInfo> multiResolutionStreams =
                        multiResolutionMap.getOutputInfo(format);
                assertTrue(String.format("Camera %s supports %d multi-resolution "
                        + "outputInfo, expected at least 2", id,
                        multiResolutionStreams.size()),
                        multiResolutionStreams.size() >= 2);

                // Make sure that each multi-resolution output stream info has the maximum size
                // for that format.
                for (MultiResolutionStreamInfo streamInfo : multiResolutionStreams) {
                    String physicalCameraId = streamInfo.getPhysicalCameraId();
                    Size streamSize = new Size(streamInfo.getWidth(), streamInfo.getHeight());
                    if (!isLogicalCamera) {
                        assertTrue("Camera " + id + " is ultra high resolution camera, but " +
                                "the multi-resolution stream info camera Id  " + physicalCameraId +
                                " doesn't match", physicalCameraId.equals(id));
                    } else {
                        assertTrue("Camera " + id + "'s multi-resolution output info " +
                                "physical camera id " + physicalCameraId + " isn't valid",
                                physicalCameraIds.contains(physicalCameraId));
                    }

                    Size[] sizes = CameraTestUtils.getSupportedSizeForFormat(format,
                            physicalCameraId, mCameraManager);
                    assertTrue(String.format("Camera %s must "
                            + "support at least one output size for output "
                            + "format %d.", physicalCameraId, format),
                             sizes != null && sizes.length > 0);

                    List<Size> maxSizes = new ArrayList<Size>();
                    maxSizes.add(CameraTestUtils.getMaxSize(sizes));
                    Size[] maxResSizes = CameraTestUtils.getSupportedSizeForFormat(format,
                            physicalCameraId, mCameraManager, /*maxResolution*/true);
                    if (maxResSizes != null && maxResSizes.length > 0) {
                        maxSizes.add(CameraTestUtils.getMaxSize(maxResSizes));
                    }

                    assertTrue(String.format("Camera %s's supported multi-resolution"
                           + " size %s for physical camera %s is not one of the largest "
                           + "supported sizes %s for format %d", id, streamSize,
                           physicalCameraId, maxSizes, format),
                           maxSizes.contains(streamSize));
                }
            }
        }
    }

    @Test
    public void testMultiResolutionImageReaderJpeg() throws Exception {
        testMultiResolutionImageReaderForFormat(ImageFormat.JPEG, /*repeating*/false);
    }

    @Test
    public void testMultiResolutionImageReaderFlexibleYuv() throws Exception {
        testMultiResolutionImageReaderForFormat(ImageFormat.YUV_420_888, /*repeating*/false);
    }

    @Test
    public void testMultiResolutionImageReaderRaw() throws Exception {
        testMultiResolutionImageReaderForFormat(ImageFormat.RAW_SENSOR, /*repeating*/false);
    }

    @Test
    public void testMultiResolutionImageReaderPrivate() throws Exception {
        testMultiResolutionImageReaderForFormat(ImageFormat.PRIVATE, /*repeating*/false);
    }

    @Test
    public void testMultiResolutionImageReaderRepeatingJpeg() throws Exception {
        testMultiResolutionImageReaderForFormat(ImageFormat.JPEG, /*repeating*/true);
    }

    @Test
    public void testMultiResolutionImageReaderRepeatingFlexibleYuv() throws Exception {
        testMultiResolutionImageReaderForFormat(ImageFormat.YUV_420_888, /*repeating*/true);
    }

    @Test
    public void testMultiResolutionImageReaderRepeatingRaw() throws Exception {
        testMultiResolutionImageReaderForFormat(ImageFormat.RAW_SENSOR, /*repeating*/true);
    }

    @Test
    public void testMultiResolutionImageReaderRepeatingPrivate() throws Exception {
        testMultiResolutionImageReaderForFormat(ImageFormat.PRIVATE, /*repeating*/true);
    }

    /**
     * Test for making sure the mandatory stream combinations work for multi-resolution output.
     */
    @Test
    public void testMultiResolutionMandatoryStreamCombinationTest() throws Exception {
        for (String id : getCameraIdsUnderTest()) {
            StaticMetadata info = mAllStaticInfo.get(id);
            CameraCharacteristics c = info.getCharacteristics();
            MandatoryStreamCombination[] combinations = c.get(
                            CameraCharacteristics.SCALER_MANDATORY_STREAM_COMBINATIONS);
            if (combinations == null) {
                Log.i(TAG, "No mandatory stream combinations for camera: " + id + " skip test");
                continue;
            }
            MultiResolutionStreamConfigurationMap multiResolutionMap = c.get(
                    CameraCharacteristics.SCALER_MULTI_RESOLUTION_STREAM_CONFIGURATION_MAP);
            if (multiResolutionMap == null) {
                Log.i(TAG, "Camera " + id + " doesn't support multi-resolution capture.");
                continue;
            }
            int[] multiResolutionOutputFormats = multiResolutionMap.getOutputFormats();
            if (multiResolutionOutputFormats.length == 0) {
                Log.i(TAG, "Camera " + id + " doesn't support multi-resolution output capture.");
                continue;
            }

            try {
                openDevice(id);
                for (MandatoryStreamCombination combination : combinations) {
                    if (combination.isReprocessable()) {
                        continue;
                    }

                    List<MandatoryStreamCombination.MandatoryStreamInformation> streamsInfo =
                            combination.getStreamsInformation();
                    for (MandatoryStreamCombination.MandatoryStreamInformation mandateInfo :
                            streamsInfo) {
                        boolean supportMultiResOutput = CameraTestUtils.contains(
                                multiResolutionOutputFormats, mandateInfo.getFormat());
                        if (mandateInfo.isMaximumSize() && supportMultiResOutput)  {
                            testMultiResolutionMandatoryStreamCombination(id, info, combination,
                                    multiResolutionMap);
                            break;
                        }
                    }
                }
            } finally {
                closeDevice(id);
            }
        }
    }

    private void testMultiResolutionMandatoryStreamCombination(String cameraId,
            StaticMetadata staticInfo, MandatoryStreamCombination combination,
            MultiResolutionStreamConfigurationMap multiResStreamConfig) throws Exception {
        String log = "Testing multi-resolution mandatory stream combination: " +
                combination.getDescription() + " on camera: " + cameraId;
        Log.i(TAG, log);

        final int TIMEOUT_FOR_RESULT_MS = 1000;
        final int MIN_RESULT_COUNT = 3;

        // Set up outputs
        List<OutputConfiguration> outputConfigs = new ArrayList<OutputConfiguration>();
        List<Surface> outputSurfaces = new ArrayList<Surface>();
        StreamCombinationTargets targets = new StreamCombinationTargets();

        CameraTestUtils.setupConfigurationTargets(combination.getStreamsInformation(),
                targets, outputConfigs, outputSurfaces, MIN_RESULT_COUNT,
                /*substituteY8*/false, /*substituteHeic*/false, /*physicalCameraId*/null,
                multiResStreamConfig, mHandler);

        boolean haveSession = false;
        try {
            CaptureRequest.Builder requestBuilder =
                    mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            for (Surface s : outputSurfaces) {
                requestBuilder.addTarget(s);
            }

            CameraCaptureSession.CaptureCallback mockCaptureCallback =
                    mock(CameraCaptureSession.CaptureCallback.class);

            checkSessionConfigurationSupported(mCamera, mHandler, outputConfigs,
                    /*inputConfig*/ null, SessionConfiguration.SESSION_REGULAR,
                    true/*defaultSupport*/, String.format(
                    "Session configuration query for multi-res combination: %s failed",
                    combination.getDescription()));

            createSessionByConfigs(outputConfigs);
            haveSession = true;
            CaptureRequest request = requestBuilder.build();
            mCameraSession.setRepeatingRequest(request, mockCaptureCallback, mHandler);

            verify(mockCaptureCallback,
                    timeout(TIMEOUT_FOR_RESULT_MS * MIN_RESULT_COUNT).atLeast(MIN_RESULT_COUNT))
                    .onCaptureCompleted(
                        eq(mCameraSession),
                        eq(request),
                        isA(TotalCaptureResult.class));
            verify(mockCaptureCallback, never()).
                    onCaptureFailed(
                        eq(mCameraSession),
                        eq(request),
                        isA(CaptureFailure.class));

        } catch (Throwable e) {
            mCollector.addMessage(
                    String.format("Mandatory multi-res stream combination: %s failed due: %s",
                    combination.getDescription(), e.getMessage()));
        }
        if (haveSession) {
            try {
                Log.i(TAG, String.format(
                        "Done with camera %s, multi-res combination: %s, closing session",
                        cameraId, combination.getDescription()));
                stopCapture(/*fast*/false);
            } catch (Throwable e) {
                mCollector.addMessage(
                    String.format("Closing down for multi-res combination: %s failed due to: %s",
                            combination.getDescription(), e.getMessage()));
            }
        }

        targets.close();
    }

    private void testMultiResolutionImageReaderForFormat(int format, boolean repeating)
            throws Exception {
        for (String id : getCameraIdsUnderTest()) {
            try {
                if (VERBOSE) {
                    Log.v(TAG, "Testing multi-resolution capture for Camera " + id
                            + " format " + format + " repeating " + repeating);
                }
                StaticMetadata staticInfo = mAllStaticInfo.get(id);
                CameraCharacteristics c = staticInfo.getCharacteristics();

                // Find the supported multi-resolution output stream info for the specified format
                MultiResolutionStreamConfigurationMap multiResolutionMap = c.get(
                        CameraCharacteristics.SCALER_MULTI_RESOLUTION_STREAM_CONFIGURATION_MAP);
                if (multiResolutionMap == null) {
                    Log.i(TAG, "Camera " + id + " doesn't support multi-resolution image reader.");
                    continue;
                }
                int[] outputFormats = multiResolutionMap.getOutputFormats();
                if (!CameraTestUtils.contains(outputFormats, format)) {
                    Log.i(TAG, "Camera " + id + " doesn't support multi-resolution image reader "
                            + "for format " + format + " vs " + Arrays.toString(outputFormats));
                    continue;
                }
                Collection<MultiResolutionStreamInfo> multiResolutionStreams =
                        multiResolutionMap.getOutputInfo(format);

               /* Test the multi-resolution ImageReader at different zoom ratios
                 * to give the camera device best chance to switch between
                 * physical cameras.*/
                List<Float> zoomRatios = CameraTestUtils.getCandidateZoomRatios(staticInfo);

                openDevice(id);
                multiResolutionImageReaderFormatTestByCamera(format,
                        multiResolutionStreams, zoomRatios, repeating);
            } finally {
                closeDevice(id);
            }
        }
    }

    private void multiResolutionImageReaderFormatTestByCamera(int format,
            Collection<MultiResolutionStreamInfo> multiResolutionStreams, List<Float> zoomRatios,
            boolean repeating) throws Exception {
        try {
            int numFrameVerified = repeating ? NUM_FRAME_VERIFIED : 1;

            // Create multi-resolution ImageReader
            mMultiResolutionImageReader = new MultiResolutionImageReader(
                    multiResolutionStreams, format, MAX_NUM_IMAGES);
            mListener = new SimpleMultiResolutionImageReaderListener(
                    mMultiResolutionImageReader, MAX_NUM_IMAGES, repeating);
            mMultiResolutionImageReader.setOnImageAvailableListener(mListener,
                    new HandlerExecutor(mHandler));

            // Create session
            Collection<OutputConfiguration> outputConfigs =
                    OutputConfiguration.createInstancesForMultiResolutionOutput(
                    mMultiResolutionImageReader);
            ArrayList<OutputConfiguration> outputConfigsList = new ArrayList<OutputConfiguration>(
                    outputConfigs);
            createSessionByConfigs(outputConfigsList);

            CaptureRequest.Builder captureBuilder =
                    mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            assertNotNull("Failed to create captureRequest", captureBuilder);
            captureBuilder.addTarget(mMultiResolutionImageReader.getSurface());

            // Capture images at different zoom ratios
            SimpleCaptureCallback listener = new SimpleCaptureCallback();
            for (Float zoomRatio : zoomRatios) {
                captureBuilder.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoomRatio);
                CaptureRequest request = captureBuilder.build();

                int sequenceId = -1;
                if (repeating) {
                    sequenceId = mCameraSession.setRepeatingRequest(request, listener, mHandler);
                } else {
                    mCameraSession.capture(request, listener, mHandler);
                }

                // Validate  images
                validateImage(format, multiResolutionStreams, numFrameVerified, listener,
                        repeating);

                if (repeating) {
                    mCameraSession.stopRepeating();
                    listener.getCaptureSequenceLastFrameNumber(sequenceId, CAPTURE_TIMEOUT);
                    listener.drain();
                }

                // Return all pending images to the ImageReader as the validateImage may
                // take a while to return and there could be many images pending.
                mMultiResolutionImageReader.flush();
                mListener.reset();
            }
        } finally {
            // Close MultiResolutionImageReader
            if (mMultiResolutionImageReader != null) {
                mMultiResolutionImageReader.close();
            }
            mMultiResolutionImageReader = null;
        }
    }

    private void validateImage(int format, Collection<MultiResolutionStreamInfo> streams,
            int captureCount, SimpleCaptureCallback listener, boolean repeating) throws Exception {
        ImageAndMultiResStreamInfo imgAndStreamInfo;
        final int MAX_RETRY_COUNT = 20;
        int retryCount = 0;
        int numImageVerified = 0;
        while (numImageVerified < captureCount) {
            assertNotNull("Image listener is null", mListener);
            imgAndStreamInfo = mListener.getAnyImageAndInfoAvailable(CAPTURE_WAIT_TIMEOUT_MS);
            if (imgAndStreamInfo == null && retryCount < MAX_RETRY_COUNT) {
                // For acquireLatestImage, a null image may be returned.
                retryCount++;
                continue;
            }

            Image img = imgAndStreamInfo.image;
            MultiResolutionStreamInfo streamInfoForImage = imgAndStreamInfo.streamInfo;
            mCollector.expectEquals(String.format("Output image width %d doesn't match " +
                    " the expected width %d", img.getWidth(), streamInfoForImage.getWidth()),
                    img.getWidth(), streamInfoForImage.getWidth());
            mCollector.expectEquals(String.format("Output image height %d doesn't match " +
                    " the expected height %d", img.getHeight(), streamInfoForImage.getHeight()),
                    img.getHeight(), streamInfoForImage.getHeight());

            if (format != ImageFormat.PRIVATE) {
                CameraTestUtils.validateImage(img, img.getWidth(), img.getHeight(), format,
                        mDebugFileNameBase);
            } else {
                mCollector.expectEquals(String.format("Output image format %d doesn't match " +
                        "expected format %d", img.getFormat(), format), format, img.getFormat());
            }

            // Get active physical camera id in the capture result. Only do the correlation
            // between activePhysicalCameraId with image size for single request for simplicity
            // reasons.
            String activePhysicalCameraId = null;
            if (!repeating && mStaticInfo.isActivePhysicalCameraIdSupported()) {
                TotalCaptureResult result = listener.getCaptureResult(
                        WAIT_FOR_RESULT_TIMEOUT_MS, img.getTimestamp());
                activePhysicalCameraId =
                        result.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID);
                mCollector.expectNotNull(
                        "Camera's capture result should contain ACTIVE_PHYSICAL_ID",
                        activePhysicalCameraId);
                mCollector.expectEquals(String.format("Active physical camera id %s doesn't " +
                        "match the expected physical camera id %s for the image",
                        activePhysicalCameraId, streamInfoForImage.getPhysicalCameraId()),
                        activePhysicalCameraId, streamInfoForImage.getPhysicalCameraId());
            }

            // Make sure the image size is one within streams
            boolean validSize = false;
            for (MultiResolutionStreamInfo streamInfo : streams) {
                if (streamInfoForImage.getPhysicalCameraId().equals(
                        streamInfo.getPhysicalCameraId())
                        && streamInfo.getWidth() == img.getWidth()
                        && streamInfo.getHeight() == img.getHeight()) {
                    validSize = true;
                }
            }
            mCollector.expectTrue(String.format("Camera's physical camera id + image size " +
                    "[%s: %d, %d] must be the supported multi-resolution output streams " +
                    "for current physical camera", streamInfoForImage.getPhysicalCameraId(),
                    img.getWidth(), img.getHeight()), validSize);

            HardwareBuffer hwb = img.getHardwareBuffer();
            assertNotNull("Unable to retrieve the Image's HardwareBuffer", hwb);

            img.close();
            numImageVerified++;
            retryCount = 0;
        }
    }
}
