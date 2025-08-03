/*
 * iCopyright 2021 The Android Open Source Project
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

import static android.hardware.camera2.cts.CameraTestUtils.*;

import android.graphics.ImageFormat;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageWriter;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.MultiResolutionImageReader;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.cts.helpers.StaticMetadata;
import android.hardware.camera2.cts.helpers.StaticMetadata.CheckLevel;
import android.hardware.camera2.cts.testcases.Camera2AndroidTestCase;
import android.hardware.camera2.params.MandatoryStreamCombination;
import android.hardware.camera2.params.MandatoryStreamCombination.MandatoryStreamInformation;
import android.hardware.camera2.params.MultiResolutionStreamConfigurationMap;
import android.hardware.camera2.params.MultiResolutionStreamInfo;
import android.hardware.camera2.params.InputConfiguration;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.android.ex.camera2.blocking.BlockingSessionCallback;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.runners.Parameterized;
import org.junit.runner.RunWith;
import org.junit.Test;
import static org.mockito.Mockito.*;

/**
 * Tests for multi-resolution size reprocessing.
 */

@RunWith(Parameterized.class)
public class MultiResolutionReprocessCaptureTest extends Camera2AndroidTestCase  {
    private static final String TAG = "MultiResolutionReprocessCaptureTest";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    private static final int CAPTURE_TIMEOUT_FRAMES = 100;
    private static final int CAPTURE_TIMEOUT_MS = 3000;
    private static final int WAIT_FOR_SURFACE_CHANGE_TIMEOUT_MS = 1000;
    private static final int CAPTURE_TEMPLATE = CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG;
    private int mDumpFrameCount = 0;

    // The image reader for the regular captures
    private MultiResolutionImageReader mMultiResImageReader;
    // The image reader for the reprocess capture
    private MultiResolutionImageReader mSecondMultiResImageReader;
    // A flag indicating whether the regular capture and the reprocess capture share the same
    // multi-resolution image reader. If it's true, the mMultiResImageReader should be used for
    // both regular and reprocess outputs.
    private boolean mShareOneReader;
    private SimpleMultiResolutionImageReaderListener mMultiResImageReaderListener;
    private SimpleMultiResolutionImageReaderListener mSecondMultiResImageReaderListener;
    private Surface mInputSurface;
    private ImageWriter mImageWriter;
    private SimpleImageWriterListener mImageWriterListener;

    @Test
    public void testMultiResolutionReprocessCharacteristics() throws Exception {
        for (String id : getCameraIdsUnderTest()) {
            if (VERBOSE) {
                Log.v(TAG, "Testing multi-resolution reprocess characteristics for Camera " + id);
            }
            StaticMetadata info = mAllStaticInfo.get(id);
            CameraCharacteristics c = info.getCharacteristics();
            StreamConfigurationMap config = c.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            int[] inputFormats = config.getInputFormats();
            int[] capabilities = CameraTestUtils.getValueNotNull(
                    c, CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
            boolean isLogicalCamera = CameraTestUtils.contains(capabilities,
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA);
            boolean isUltraHighResCamera = info.isUltraHighResolutionSensor();
            Set<String> physicalCameraIds = c.getPhysicalCameraIds();

            MultiResolutionStreamConfigurationMap multiResolutionMap = c.get(
                    CameraCharacteristics.SCALER_MULTI_RESOLUTION_STREAM_CONFIGURATION_MAP);
            if (multiResolutionMap == null) {
                Log.i(TAG, "Camera " + id + " doesn't support multi-resolution reprocessing.");
                continue;
            }
            if (VERBOSE) {
                Log.v(TAG, "MULTI_RESOLUTION_STREAM_CONFIGURATION_MAP: "
                        + multiResolutionMap.toString());
            }

            // Find multi-resolution input and output formats
            int[] multiResolutionInputFormats = multiResolutionMap.getInputFormats();
            int[] multiResolutionOutputFormats = multiResolutionMap.getOutputFormats();

            assertTrue("Camera " + id + " must be a logical multi-camera or ultra high res camera "
                    + "to support multi-resolution reprocessing.",
                    isLogicalCamera || isUltraHighResCamera);

            for (int format : multiResolutionInputFormats) {
                assertTrue(String.format("Camera %s: multi-resolution input format %d "
                        + "isn't a supported format", id, format),
                        CameraTestUtils.contains(inputFormats, format));

                Collection<MultiResolutionStreamInfo> multiResolutionStreams =
                        multiResolutionMap.getInputInfo(format);
                assertTrue(String.format("Camera %s supports %d multi-resolution "
                        + "input stream info, expected at least 2", id,
                        multiResolutionStreams.size()),
                        multiResolutionStreams.size() >= 2);

                // Make sure that each multi-resolution input stream info has the maximum size
                // for that format.
                for (MultiResolutionStreamInfo streamInfo : multiResolutionStreams) {
                    String physicalCameraId = streamInfo.getPhysicalCameraId();
                    Size streamSize = new Size(streamInfo.getWidth(), streamInfo.getHeight());
                    if (!isLogicalCamera) {
                        assertTrue("Camera " + id + " is ultra high resolution camera, but "
                                + "the multi-resolution reprocessing stream info camera Id "
                                + physicalCameraId + " doesn't match",
                                physicalCameraId.equals(id));
                    } else {
                        assertTrue("Camera " + id + "'s multi-resolution input info "
                                + "physical camera id " + physicalCameraId + " isn't valid",
                                physicalCameraIds.contains(physicalCameraId));
                    }

                    StaticMetadata pInfo = mAllStaticInfo.get(physicalCameraId);
                    CameraCharacteristics pChar = pInfo.getCharacteristics();
                    StreamConfigurationMap pConfig = pChar.get(
                            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    Size[] sizes = pConfig.getInputSizes(format);

                    assertTrue(String.format("Camera %s must "
                            + "support at least one input size for multi-resolution input "
                            + "format %d.", physicalCameraId, format),
                             sizes != null && sizes.length > 0);

                    List<Size> maxSizes = new ArrayList<Size>();
                    maxSizes.add(CameraTestUtils.getMaxSize(sizes));
                    StreamConfigurationMap pMaxResConfig = pChar.get(CameraCharacteristics.
                            SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION);
                    if (pMaxResConfig != null) {
                        Size[] maxResSizes = pMaxResConfig.getInputSizes(format);
                        if (maxResSizes != null && maxResSizes.length > 0) {
                            maxSizes.add(CameraTestUtils.getMaxSize(maxResSizes));
                        }
                    }

                    assertTrue(String.format("Camera %s's supported multi-resolution"
                           + " input size %s for physical camera %s is not one of the largest "
                           + "supported input sizes %s for format %d", id, streamSize,
                           physicalCameraId, maxSizes, format), maxSizes.contains(streamSize));
                }
            }

            // YUV reprocessing capabilities check
            if (CameraTestUtils.contains(multiResolutionOutputFormats, ImageFormat.YUV_422_888) &&
                    CameraTestUtils.contains(multiResolutionInputFormats,
                    ImageFormat.YUV_420_888)) {
                assertTrue("The camera device must have YUV_REPROCESSING capability if it "
                        + "supports multi-resolution YUV input and YUV output",
                        CameraTestUtils.contains(capabilities,
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING));

                assertTrue("The camera device must supports multi-resolution JPEG output if "
                        + "supports multi-resolution YUV input and YUV output",
                        CameraTestUtils.contains(multiResolutionOutputFormats, ImageFormat.JPEG));
            }

            // OPAQUE reprocessing capabilities check
            if (CameraTestUtils.contains(multiResolutionOutputFormats, ImageFormat.PRIVATE) &&
                    CameraTestUtils.contains(multiResolutionInputFormats, ImageFormat.PRIVATE)) {
                assertTrue("The camera device must have PRIVATE_REPROCESSING capability if it "
                        + "supports multi-resolution PRIVATE input and PRIVATE output",
                        CameraTestUtils.contains(capabilities,
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING));

                assertTrue("The camera device must supports multi-resolution JPEG output if "
                        + "supports multi-resolution PRIVATE input and PRIVATE output",
                        CameraTestUtils.contains(multiResolutionOutputFormats, ImageFormat.JPEG));
                assertTrue("The camera device must supports multi-resolution YUV output if "
                        + "supports multi-resolution PRIVATE input and PRIVATE output",
                        CameraTestUtils.contains(multiResolutionOutputFormats,
                        ImageFormat.YUV_420_888));
            }
        }
    }

    /**
     * Test YUV_420_888 -> YUV_420_888 multi-resolution reprocessing
     */
    @Test
    public void testMultiResolutionYuvToYuvReprocessing() throws Exception {
        for (String id : getCameraIdsUnderTest()) {
            testMultiResolutionReprocessing(id, ImageFormat.YUV_420_888, ImageFormat.YUV_420_888);
        }
    }

    /**
     * Test YUV_420_888 -> JPEG multi-resolution reprocessing
     */
    @Test
    public void testMultiResolutionYuvToJpegReprocessing() throws Exception {
        for (String id : getCameraIdsUnderTest()) {
            testMultiResolutionReprocessing(id, ImageFormat.YUV_420_888, ImageFormat.JPEG);
        }
    }

    /**
     * Test OPAQUE -> YUV_420_888 multi-resolution reprocessing
     */
    @Test
    public void testMultiResolutionOpaqueToYuvReprocessing() throws Exception {
        for (String id : getCameraIdsUnderTest()) {
            // Opaque -> YUV_420_888 must be supported.
            testMultiResolutionReprocessing(id, ImageFormat.PRIVATE, ImageFormat.YUV_420_888);
        }
    }

    /**
     * Test OPAQUE -> JPEG multi-resolution reprocessing
     */
    @Test
    public void testMultiResolutionOpaqueToJpegReprocessing() throws Exception {
        for (String id : getCameraIdsUnderTest()) {
            // OPAQUE -> JPEG must be supported.
            testMultiResolutionReprocessing(id, ImageFormat.PRIVATE, ImageFormat.JPEG);
        }
    }

    /**
     * Test for making sure the mandatory stream combinations work for multi-resolution
     * reprocessing.
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
            int[] multiResolutionInputFormats = multiResolutionMap.getInputFormats();
            int[] multiResolutionOutputFormats = multiResolutionMap.getOutputFormats();
            if (multiResolutionInputFormats.length == 0
                    || multiResolutionOutputFormats.length == 0) {
                Log.i(TAG, "Camera " + id + " doesn't support multi-resolution reprocess "
                        + "input/output.");
                continue;
            }

            try {
                openDevice(id);
                for (MandatoryStreamCombination combination : combinations) {
                    if (!combination.isReprocessable()) {
                        continue;
                    }

                    MandatoryStreamCombination.MandatoryStreamInformation firstStreamInfo =
                            combination.getStreamsInformation().get(0);
                    int inputFormat = firstStreamInfo.getFormat();
                    boolean supportMultiResReprocess = firstStreamInfo.isInput() &&
                            CameraTestUtils.contains(multiResolutionOutputFormats, inputFormat) &&
                            CameraTestUtils.contains(multiResolutionInputFormats, inputFormat);
                    if (!supportMultiResReprocess)  {
                        continue;
                    }

                    testMultiResolutionMandatoryStreamCombination(id, info, combination,
                            multiResolutionMap);
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

        final int TIMEOUT_FOR_RESULT_MS = 5000;
        final int NUM_REPROCESS_CAPTURES_PER_CONFIG = 3;

        // Set up outputs
        List<OutputConfiguration> outputConfigs = new ArrayList<OutputConfiguration>();
        List<Surface> outputSurfaces = new ArrayList<Surface>();
        StreamCombinationTargets targets = new StreamCombinationTargets();
        MultiResolutionImageReader inputReader = null;
        ImageWriter inputWriter = null;
        SimpleImageReaderListener inputReaderListener = new SimpleImageReaderListener();
        SimpleCaptureCallback inputCaptureListener = new SimpleCaptureCallback();
        SimpleCaptureCallback reprocessOutputCaptureListener = new SimpleCaptureCallback();

        List<MandatoryStreamInformation> streamInfo = combination.getStreamsInformation();
        assertTrue("Reprocessable stream combinations should have at least 3 or more streams",
                    (streamInfo != null) && (streamInfo.size() >= 3));
        assertTrue("The first mandatory stream information in a reprocessable combination must " +
                "always be input", streamInfo.get(0).isInput());

        int inputFormat = streamInfo.get(0).getFormat();

        CameraTestUtils.setupConfigurationTargets(streamInfo.subList(2, streamInfo.size()),
                targets, outputConfigs, outputSurfaces, NUM_REPROCESS_CAPTURES_PER_CONFIG,
                /*substituteY8*/false, /*substituteHeic*/false, /*physicalCameraId*/null,
                multiResStreamConfig, mHandler);

        Collection<MultiResolutionStreamInfo> multiResInputs =
                multiResStreamConfig.getInputInfo(inputFormat);
        InputConfiguration inputConfig = new InputConfiguration(multiResInputs, inputFormat);

        try {
            // For each config, YUV and JPEG outputs will be tested. (For YUV reprocessing,
            // the YUV ImageReader for input is also used for output.)
            final boolean inputIsYuv = inputConfig.getFormat() == ImageFormat.YUV_420_888;
            final boolean useYuv = inputIsYuv || targets.mYuvTargets.size() > 0 ||
                    targets.mYuvMultiResTargets.size() > 0;
            final int totalNumReprocessCaptures =  NUM_REPROCESS_CAPTURES_PER_CONFIG * (
                    (inputIsYuv ? 1 : 0) + targets.mJpegMultiResTargets.size() +
                    targets.mJpegTargets.size() +
                    (useYuv ? targets.mYuvMultiResTargets.size() + targets.mYuvTargets.size() : 0));

            // It needs 1 input buffer for each reprocess capture + the number of buffers
            // that will be used as outputs.
            inputReader = new MultiResolutionImageReader(multiResInputs, inputFormat,
                    totalNumReprocessCaptures + NUM_REPROCESS_CAPTURES_PER_CONFIG);
            inputReader.setOnImageAvailableListener(
                    inputReaderListener, new HandlerExecutor(mHandler));
            outputConfigs.addAll(
                    OutputConfiguration.createInstancesForMultiResolutionOutput(inputReader));
            outputSurfaces.add(inputReader.getSurface());

            CameraCaptureSession.CaptureCallback mockCaptureCallback =
                    mock(CameraCaptureSession.CaptureCallback.class);

            checkSessionConfigurationSupported(mCamera, mHandler, outputConfigs,
                    inputConfig, SessionConfiguration.SESSION_REGULAR,
                    true/*defaultSupport*/, String.format(
                    "Session configuration query for multi-res combination: %s failed",
                    combination.getDescription()));

            // Verify we can create a reprocessable session with the input and all outputs.
            BlockingSessionCallback sessionListener = new BlockingSessionCallback();
            CameraCaptureSession session = configureReprocessableCameraSessionWithConfigurations(
                    mCamera, inputConfig, outputConfigs, sessionListener, mHandler);
            inputWriter = ImageWriter.newInstance(
                    session.getInputSurface(), totalNumReprocessCaptures);

            // Prepare a request for reprocess input
            CaptureRequest.Builder builder =
                    mCamera.createCaptureRequest(CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG);
            builder.addTarget(inputReader.getSurface());

            for (int i = 0; i < totalNumReprocessCaptures; i++) {
                session.capture(builder.build(), inputCaptureListener, mHandler);
            }

            List<CaptureRequest> reprocessRequests = new ArrayList<>();
            List<Surface> reprocessOutputs = new ArrayList<>();

            if (inputIsYuv) {
                reprocessOutputs.add(inputReader.getSurface());
            }
            for (MultiResolutionImageReader reader : targets.mJpegMultiResTargets) {
                reprocessOutputs.add(reader.getSurface());
            }
            for (ImageReader reader : targets.mJpegTargets) {
                reprocessOutputs.add(reader.getSurface());
            }
            for (MultiResolutionImageReader reader : targets.mYuvMultiResTargets) {
                reprocessOutputs.add(reader.getSurface());
            }
            for (ImageReader reader : targets.mYuvTargets) {
                reprocessOutputs.add(reader.getSurface());
            }

            for (int i = 0; i < NUM_REPROCESS_CAPTURES_PER_CONFIG; i++) {
                for (Surface output : reprocessOutputs) {
                    TotalCaptureResult result = inputCaptureListener.getTotalCaptureResult(
                            TIMEOUT_FOR_RESULT_MS);
                    Map<String, TotalCaptureResult> physicalResults =
                            result.getPhysicalCameraTotalResults();
                    for (Map.Entry<String, TotalCaptureResult> entry : physicalResults.entrySet()) {
                        String physicalCameraId = entry.getKey();
                        TotalCaptureResult physicalResult = entry.getValue();
                        String activePhysicalId = physicalResult.get(
                                CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID);
                        mCollector.expectEquals(String.format(
                                "Physical camera result metadata must contain activePhysicalId " +
                                "(%s) matching with physical camera Id (%s).", activePhysicalId,
                                physicalCameraId), physicalCameraId, activePhysicalId);
                    }

                    String activePhysicalCameraId = result.get(
                            CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID);
                    if (activePhysicalCameraId != null) {
                        result = physicalResults.get(activePhysicalCameraId);
                    }

                    builder = mCamera.createReprocessCaptureRequest(result);
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
            mCollector.addMessage(
                    String.format("Mandatory multi-res stream combination: %s failed due: %s",
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

    /**
     * Test multi-resolution reprocessing from the input format to the output format
     */
    private void testMultiResolutionReprocessing(String cameraId, int inputFormat,
            int outputFormat) throws Exception {
        if (VERBOSE) {
            Log.v(TAG, "testMultiResolutionReprocessing: cameraId: " + cameraId + " inputFormat: "
                    + inputFormat + " outputFormat: " + outputFormat);
        }

        Collection<MultiResolutionStreamInfo> inputStreamInfo =
                getMultiResReprocessInfo(cameraId, inputFormat, /*input*/ true);
        Collection<MultiResolutionStreamInfo> regularOutputStreamInfo =
                getMultiResReprocessInfo(cameraId, inputFormat, /*input*/ false);
        Collection<MultiResolutionStreamInfo> reprocessOutputStreamInfo =
                getMultiResReprocessInfo(cameraId, outputFormat, /*input*/ false);
        if (inputStreamInfo == null || regularOutputStreamInfo == null ||
                reprocessOutputStreamInfo == null) {
            return;
        }
        assertTrue("The multi-resolution stream info for format " + inputFormat
                + " must be equal between input and output",
                inputStreamInfo.containsAll(regularOutputStreamInfo)
                && regularOutputStreamInfo.containsAll(inputStreamInfo));

        try {
            openDevice(cameraId);

            testMultiResolutionReprocessWithStreamInfo(cameraId, inputFormat, inputStreamInfo,
                    outputFormat, reprocessOutputStreamInfo);
        } finally {
            closeDevice(cameraId);
        }
    }

    /**
     * Test multi-resolution reprocess with multi-resolution stream info lists for a particular
     * format combination.
     */
    private void testMultiResolutionReprocessWithStreamInfo(String cameraId,
            int inputFormat, Collection<MultiResolutionStreamInfo> inputInfo,
            int outputFormat, Collection<MultiResolutionStreamInfo> outputInfo)
            throws Exception {
        try {
            setupMultiResImageReaders(inputFormat, inputInfo, outputFormat, outputInfo,
                    /*maxImages*/1);
            setupReprocessableSession(inputFormat, inputInfo, outputInfo,
                    /*numImageWriterImages*/1);

            List<Float> zoomRatioList = CameraTestUtils.getCandidateZoomRatios(mStaticInfo);
            for (Float zoomRatio :  zoomRatioList) {
                ImageResultSizeHolder imageResultSizeHolder = null;

                try {
                    imageResultSizeHolder = doMultiResReprocessCapture(zoomRatio);
                    Image reprocessedImage = imageResultSizeHolder.getImage();
                    Size outputSize = imageResultSizeHolder.getExpectedSize();
                    TotalCaptureResult result = imageResultSizeHolder.getTotalCaptureResult();

                    mCollector.expectImageProperties("testMultiResolutionReprocess",
                            reprocessedImage, outputFormat, outputSize,
                            result.get(CaptureResult.SENSOR_TIMESTAMP));

                    if (DEBUG) {
                        Log.d(TAG, String.format("camera %s %d zoom %f out %dx%d %d",
                                cameraId, inputFormat, zoomRatio,
                                outputSize.getWidth(), outputSize.getHeight(),
                                outputFormat));

                        dumpImage(reprocessedImage,
                                "/testMultiResolutionReprocess_camera" + cameraId
                                + "_" + mDumpFrameCount);
                        mDumpFrameCount++;
                    }
                } finally {
                    if (imageResultSizeHolder != null) {
                        imageResultSizeHolder.getImage().close();
                    }
                }
            }
        } finally {
            closeReprossibleSession();
            closeMultiResImageReaders();
        }
    }

    /**
     * Set up multi-resolution image readers for regular and reprocess output
     *
     * <p>If the reprocess input format is equal to output format, share one multi-resolution
     * image reader.</p>
     */
    private void setupMultiResImageReaders(int inputFormat,
            Collection<MultiResolutionStreamInfo> inputInfo, int outputFormat,
            Collection<MultiResolutionStreamInfo> outputInfo, int maxImages) {

        mShareOneReader = false;
        // If the regular output and reprocess output have the same format,
        // they can share one MultiResolutionImageReader.
        if (inputFormat == outputFormat) {
            maxImages *= 2;
            mShareOneReader = true;
        }

        // create an MultiResolutionImageReader for the regular capture
        mMultiResImageReader = new MultiResolutionImageReader(inputInfo,
                inputFormat, maxImages);
        mMultiResImageReaderListener = new SimpleMultiResolutionImageReaderListener(
                mMultiResImageReader, 1, /*repeating*/false);
        mMultiResImageReader.setOnImageAvailableListener(mMultiResImageReaderListener,
                new HandlerExecutor(mHandler));

        if (!mShareOneReader) {
            // create an MultiResolutionImageReader for the reprocess capture
            mSecondMultiResImageReader = new MultiResolutionImageReader(
                    outputInfo, outputFormat, maxImages);
            mSecondMultiResImageReaderListener = new SimpleMultiResolutionImageReaderListener(
                    mSecondMultiResImageReader, maxImages, /*repeating*/ false);
            mSecondMultiResImageReader.setOnImageAvailableListener(
                    mSecondMultiResImageReaderListener, new HandlerExecutor(mHandler));
        }
    }

    /**
     * Close two multi-resolution image readers.
     */
    private void closeMultiResImageReaders() {
        mMultiResImageReader.close();
        mMultiResImageReader = null;

        if (!mShareOneReader) {
            mSecondMultiResImageReader.close();
            mSecondMultiResImageReader = null;
        }
    }

    /**
     * Get the MultiResolutionImageReader for reprocess output.
     */
    private MultiResolutionImageReader getOutputMultiResImageReader() {
        if (mShareOneReader) {
            return mMultiResImageReader;
        } else {
            return mSecondMultiResImageReader;
        }
    }

    /**
     * Get the MultiResolutionImageReaderListener for reprocess output.
     */
    private SimpleMultiResolutionImageReaderListener getOutputMultiResImageReaderListener() {
        if (mShareOneReader) {
            return mMultiResImageReaderListener;
        } else {
            return mSecondMultiResImageReaderListener;
        }
    }

    /**
     * Set up a reprocessable session and create an ImageWriter with the session's input surface.
     */
    private void setupReprocessableSession(int inputFormat,
            Collection<MultiResolutionStreamInfo> inputInfo,
            Collection<MultiResolutionStreamInfo> outputInfo,
            int numImageWriterImages) throws Exception {
        // create a reprocessable capture session
        Collection<OutputConfiguration> outConfigs =
                OutputConfiguration.createInstancesForMultiResolutionOutput(
                        mMultiResImageReader);
        ArrayList<OutputConfiguration> outputConfigsList = new ArrayList<OutputConfiguration>(
                outConfigs);

        if (!mShareOneReader) {
            Collection<OutputConfiguration> secondOutputConfigs =
                    OutputConfiguration.createInstancesForMultiResolutionOutput(
                            mSecondMultiResImageReader);
            outputConfigsList.addAll(secondOutputConfigs);
        }

        InputConfiguration inputConfig = new InputConfiguration(inputInfo, inputFormat);
        if (VERBOSE) {
            String inputConfigString = inputConfig.toString();
            Log.v(TAG, "InputConfiguration: " + inputConfigString);
        }

        mCameraSessionListener = new BlockingSessionCallback();
        mCameraSession = configureReprocessableCameraSessionWithConfigurations(
                mCamera, inputConfig, outputConfigsList, mCameraSessionListener, mHandler);

        // create an ImageWriter
        mInputSurface = mCameraSession.getInputSurface();
        mImageWriter = ImageWriter.newInstance(mInputSurface,
                numImageWriterImages);

        mImageWriterListener = new SimpleImageWriterListener(mImageWriter);
        mImageWriter.setOnImageReleasedListener(mImageWriterListener, mHandler);
    }

    /**
     * Close the reprocessable session and ImageWriter.
     */
    private void closeReprossibleSession() {
        mInputSurface = null;

        if (mCameraSession != null) {
            mCameraSession.close();
            mCameraSession = null;
        }

        if (mImageWriter != null) {
            mImageWriter.close();
            mImageWriter = null;
        }
    }

    /**
     * Do one multi-resolution reprocess capture for the specified zoom ratio
     */
    private ImageResultSizeHolder doMultiResReprocessCapture(float zoomRatio) throws Exception {
        // submit a regular capture and get the result
        TotalCaptureResult totalResult = submitCaptureRequest(
                zoomRatio, mMultiResImageReader.getSurface(), /*inputResult*/null);
        Map<String, TotalCaptureResult> physicalResults =
                totalResult.getPhysicalCameraTotalResults();

        ImageAndMultiResStreamInfo inputImageAndInfo =
                mMultiResImageReaderListener.getAnyImageAndInfoAvailable(CAPTURE_TIMEOUT_MS);
        assertNotNull("Failed to capture input image", inputImageAndInfo);
        Image inputImage = inputImageAndInfo.image;
        MultiResolutionStreamInfo inputStreamInfo = inputImageAndInfo.streamInfo;
        TotalCaptureResult inputSettings =
                physicalResults.get(inputStreamInfo.getPhysicalCameraId());
        assertTrue("Regular capture's TotalCaptureResult doesn't contain capture result for "
                + "physical camera id " + inputStreamInfo.getPhysicalCameraId(),
                inputSettings != null);

        // Submit a reprocess capture and get the result
        mImageWriter.queueInputImage(inputImage);

        TotalCaptureResult finalResult = submitCaptureRequest(zoomRatio,
                getOutputMultiResImageReader().getSurface(), inputSettings);

        ImageAndMultiResStreamInfo outputImageAndInfo =
                getOutputMultiResImageReaderListener().getAnyImageAndInfoAvailable(
                CAPTURE_TIMEOUT_MS);
        Image outputImage = outputImageAndInfo.image;
        MultiResolutionStreamInfo outputStreamInfo = outputImageAndInfo.streamInfo;

        assertTrue("The regular output and reprocess output's stream info must be the same",
                outputStreamInfo.equals(inputStreamInfo));

        ImageResultSizeHolder holder = new ImageResultSizeHolder(outputImageAndInfo.image,
                finalResult, new Size(outputStreamInfo.getWidth(), outputStreamInfo.getHeight()));

        return holder;
    }

    /**
     * Issue a capture request and return the result for a particular zoom ratio.
     *
     * <p>If inputResult is null, it's a regular request. Otherwise, it's a reprocess request.</p>
     */
    private TotalCaptureResult submitCaptureRequest(float zoomRatio,
            Surface output, TotalCaptureResult inputResult) throws Exception {

        SimpleCaptureCallback captureCallback = new SimpleCaptureCallback();

        // Prepare a list of capture requests. Whether it's a regular or reprocess capture request
        // is based on inputResult.
        CaptureRequest.Builder builder;
        boolean isReprocess = (inputResult != null);
        if (isReprocess) {
            builder = mCamera.createReprocessCaptureRequest(inputResult);
        } else {
            builder = mCamera.createCaptureRequest(CAPTURE_TEMPLATE);
            builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoomRatio);
        }
        builder.addTarget(output);
        CaptureRequest request = builder.build();
        assertTrue("Capture request reprocess type " + request.isReprocess() + " is wrong.",
            request.isReprocess() == isReprocess);

        mCameraSession.capture(request, captureCallback, mHandler);

        TotalCaptureResult result = captureCallback.getTotalCaptureResultForRequest(
                request, CAPTURE_TIMEOUT_FRAMES);

        // make sure all input surfaces are released.
        if (isReprocess) {
            mImageWriterListener.waitForImageReleased(CAPTURE_TIMEOUT_MS);
        }

        return result;
    }

    private Size getMaxSize(int format, StaticMetadata.StreamDirection direction) {
        Size[] sizes = mStaticInfo.getAvailableSizesForFormatChecked(format, direction);
        return getAscendingOrderSizes(Arrays.asList(sizes), /*ascending*/false).get(0);
    }

    private Collection<MultiResolutionStreamInfo> getMultiResReprocessInfo(String cameraId,
            int format, boolean input) throws Exception {
        StaticMetadata staticInfo = mAllStaticInfo.get(cameraId);
        CameraCharacteristics characteristics = staticInfo.getCharacteristics();
        MultiResolutionStreamConfigurationMap configs = characteristics.get(
                CameraCharacteristics.SCALER_MULTI_RESOLUTION_STREAM_CONFIGURATION_MAP);
        if (configs == null) {
            Log.i(TAG, "Camera " + cameraId + " doesn't support multi-resolution streams");
            return null;
        }

        String streamType = input ? "input" : "output";
        int[] formats = input ? configs.getInputFormats() :
                configs.getOutputFormats();
        if (!CameraTestUtils.contains(formats, format)) {
            Log.i(TAG, "Camera " + cameraId + " doesn't support multi-resolution "
                    + streamType + " stream for format " + format + ". Supported formats are "
                    + Arrays.toString(formats));
            return null;
        }
        Collection<MultiResolutionStreamInfo> streams =
                input ? configs.getInputInfo(format) : configs.getOutputInfo(format);
        mCollector.expectTrue(String.format("Camera %s supported 0 multi-resolution "
                + streamType + " stream info, expected at least 1", cameraId),
                streams.size() > 0);

        return streams;
    }

    private void dumpImage(Image image, String name) {
        String filename = mDebugFileNameBase + name;
        switch(image.getFormat()) {
            case ImageFormat.JPEG:
                filename += ".jpg";
                break;
            case ImageFormat.YUV_420_888:
                filename += ".yuv";
                break;
            default:
                filename += "." + image.getFormat();
                break;
        }

        Log.d(TAG, "dumping an image to " + filename);
        dumpFile(filename , getDataFromImage(image));
    }

    /**
     * A class that holds an Image, a TotalCaptureResult, and expected image size.
     */
    public static class ImageResultSizeHolder {
        private final Image mImage;
        private final TotalCaptureResult mResult;
        private final Size mExpectedSize;

        public ImageResultSizeHolder(Image image, TotalCaptureResult result, Size expectedSize) {
            mImage = image;
            mResult = result;
            mExpectedSize = expectedSize;
        }

        public Image getImage() {
            return mImage;
        }

        public TotalCaptureResult getTotalCaptureResult() {
            return mResult;
        }

        public Size getExpectedSize() {
            return mExpectedSize;
        }
    }

}
