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
import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.cts.helpers.StaticMetadata;
import android.hardware.camera2.cts.testcases.Camera2AndroidTestCase;
import android.hardware.camera2.params.DynamicRangeProfiles;
import android.hardware.camera2.params.LensIntrinsicsSample;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;
import android.util.Range;
import android.util.Size;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.PropertyUtil;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.Test;

import static android.hardware.camera2.CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_SENSOR_CROP_REGION;
import static android.hardware.camera2.cts.CameraTestUtils.CAPTURE_RESULT_TIMEOUT_MS;
import static android.hardware.camera2.cts.CameraTestUtils.SimpleCaptureCallback;
import static junit.framework.Assert.*;

/**
 * <p>Basic test for image capture using CONTROL_ZOOM_RATIO. It uses CameraDevice as
 * producer, and camera sends image data to an imageReader. Image formats
 * being tested are JPEG and RAW.</p>
 */
@RunWith(Parameterized.class)
public class ZoomCaptureTest extends Camera2AndroidTestCase {
    private static final String TAG = "ZoomCaptureTest";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);

    private SimpleImageListener mListener;

    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @AppModeFull(reason = "Instant apps can't access Test API")
    public void testJpegZoomCapture() throws Exception {
        for (String id : getCameraIdsUnderTest()) {
            try {
                Log.v(TAG, "Testing jpeg zoom capture for Camera " + id);
                openDevice(id);
                bufferFormatZoomTestByCamera(ImageFormat.JPEG);
            } finally {
                closeDevice(id);
            }
        }
    }

    @Test
    @AppModeFull(reason = "Instant apps can't access Test API")
    public void testRawZoomCapture() throws Exception {
        for (String id : getCameraIdsUnderTest()) {
            try {
                Log.v(TAG, "Testing raw zoom capture for camera " + id);
                openDevice(id);

                bufferFormatZoomTestByCamera(ImageFormat.RAW_SENSOR);
            } finally {
                closeDevice(id);
            }
        }
    }

    /**
     * 10-bit output capable logical devices must also support switching between the same physical
     * cameras via the CONTROL_ZOOM_RATIO control, that the device supports switching between for
     * 8-bit outputs.
     */
    @CddTest(requirement="7.5/C-3-1")
    @Test
    @AppModeFull(reason = "Instant apps can't access Test API")
    public void test10bitLogicalZoomCapture() throws Exception {
        final int ZOOM_RATIO_STEPS = 100;
        for (String id : getCameraIdsUnderTest()) {
            try {
                Log.v(TAG, "Testing 10-bit logical camera zoom capture for id " + id);
                openDevice(id);

                boolean supports10BitOutput = mStaticInfo.isCapabilitySupported(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT);
                if (!supports10BitOutput) {
                    Log.v(TAG, "No 10-bit output support, skipping id " + id);
                    continue;
                }

                if (!mStaticInfo.isColorOutputSupported()) {
                    Log.v(TAG, "No color output support, skipping id " + id);
                    continue;
                }

                if (!mStaticInfo.isLogicalMultiCamera()) {
                    Log.v(TAG, "No logical device, skipping id " + id);
                    continue;
                }

                Range<Float> zoomRatioRange = mStaticInfo.getZoomRatioRangeChecked();
                ArrayList<Float> candidateZoomRatios = new ArrayList<>(ZOOM_RATIO_STEPS);
                Float zoomStep  =
                        (zoomRatioRange.getUpper() - zoomRatioRange.getLower()) / ZOOM_RATIO_STEPS;
                for (int step = 0; step < (ZOOM_RATIO_STEPS - 1); step++) {
                    candidateZoomRatios.add(step, zoomRatioRange.getLower() + step * zoomStep);
                }
                candidateZoomRatios.add(ZOOM_RATIO_STEPS - 1, zoomRatioRange.getUpper());

                Set<String> activePhysicalIdsSeen8bit = new HashSet<String>();
                bufferFormatZoomTestByCamera(ImageFormat.YUV_420_888, candidateZoomRatios,
                        activePhysicalIdsSeen8bit /*activePhysicalIdsSeen*/);
                Set<String> activePhysicalIdsSeen10bit = new HashSet<String>();
                bufferFormatZoomTestByCamera(ImageFormat.YCBCR_P010, candidateZoomRatios,
                        activePhysicalIdsSeen10bit /*activePhysicalIdsSeen*/);

                assertEquals("The physical ids seen when zooming with 8-bit output: " +
                        activePhysicalIdsSeen8bit + " must match with the 10-bit output: " +
                        activePhysicalIdsSeen10bit, activePhysicalIdsSeen8bit,
                        activePhysicalIdsSeen10bit);
            } finally {
                closeDevice(id);
            }
        }
    }

    private void bufferFormatZoomTestByCamera(int format) throws Exception {
        Set<String> activePhysicalIdsSeen = new HashSet<String>();
        bufferFormatZoomTestByCamera(format, CameraTestUtils.getCandidateZoomRatios(mStaticInfo),
                activePhysicalIdsSeen /*/activePhysicalIdSeen*/);
    }

    private void bufferFormatZoomTestByCamera(int format, List<Float> candidateZoomRatios,
            Set<String> activePhysicalIdsSeen) throws Exception {
        Size[] availableSizes = mStaticInfo.getAvailableSizesForFormatChecked(format,
                StaticMetadata.StreamDirection.Output);
        if (availableSizes.length == 0) {
            return;
        }

        Set<String> physicalCameraIds = null;
        if (mStaticInfo.isLogicalMultiCamera()) {
            physicalCameraIds = mStaticInfo.getCharacteristics().getPhysicalCameraIds();
        }
        try {
            // Pick the largest image size:
            Size maxSize = CameraTestUtils.getMaxSize(availableSizes);
            mListener  = new SimpleImageListener(format, maxSize);
            createDefaultImageReader(maxSize, format, 1, mListener);

            checkImageReaderSessionConfiguration(
                    "Camera capture session validation for format: " + format + "failed");

            ArrayList<OutputConfiguration> outputConfigs = new ArrayList<>();
            OutputConfiguration config = new OutputConfiguration(mReader.getSurface());
            if (format == ImageFormat.YCBCR_P010) {
                config.setDynamicRangeProfile(DynamicRangeProfiles.HLG10);
            }
            outputConfigs.add(config);

            CaptureRequest.Builder requestBuilder = prepareCaptureRequestForConfigs(
                    outputConfigs, CameraDevice.TEMPLATE_PREVIEW);

            boolean checkActivePhysicalIdConsistency =
                    PropertyUtil.getFirstApiLevel() >= Build.VERSION_CODES.S;
            List<CaptureRequest> requests = new ArrayList<CaptureRequest>();
            for (Float zoomRatio : candidateZoomRatios) {
                if (VERBOSE) {
                    Log.v(TAG, "Testing format " + format + " zoomRatio " + zoomRatio +
                            " for camera " + mCamera.getId());
                }

                requestBuilder.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoomRatio);
                requests.add(requestBuilder.build());
            }

            SimpleCaptureCallback listener = new SimpleCaptureCallback();
            int seqId = mCameraSession.captureBurst(requests, listener, mHandler);

            // onCaptureSequenceCompleted() trails all capture results. Upon its return,
            // we make sure we've received all results/errors.
            listener.getCaptureSequenceLastFrameNumber(
                    seqId, CAPTURE_WAIT_TIMEOUT_MS * candidateZoomRatios.size());

            float lastZoomRatio = Float.NaN;
            float lastFocalLength = Float.NaN;
            Rect lastActiveCropRegion = new Rect();
            String lastActivePhysicalId = new String();
            while (listener.hasMoreResults() && mStaticInfo.isActivePhysicalCameraIdSupported()) {
                // Validate capture result.
                TotalCaptureResult result = listener.getTotalCaptureResult(
                            CAPTURE_RESULT_TIMEOUT_MS);
                String activePhysicalId = result.get(
                        CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID);
                if (checkActivePhysicalIdConsistency) {
                    assertNotNull("Camera " + mCamera.getId() +
                            " result metadata must contain ACTIVE_PHYSICAL_ID",
                            activePhysicalId);
                    assertTrue("Camera " + mCamera.getId() + " must be logical " +
                            "camera if activePhysicalId exists in capture result",
                            physicalCameraIds != null && physicalCameraIds.size() != 0);
                    mCollector.expectTrue("Camera " + mCamera.getId() + "  activePhysicalId " +
                            activePhysicalId + "must be among valid physical Ids "  +
                            physicalCameraIds.toString(),
                            physicalCameraIds.contains(activePhysicalId));

                    activePhysicalIdsSeen.add(activePhysicalId);

                    // Ensure that the active physical crop region is updated correctly
                    // when iterating over the zoom ratio within the same active physical camera
                    Rect activeCropRegion = result.get(
                            LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_SENSOR_CROP_REGION);
                    if (activeCropRegion != null) {
                        Float zoomRatio = CameraTestUtils.getValueNotNull(result,
                                CaptureResult.CONTROL_ZOOM_RATIO);
                        float [] lensIntrinsics = result.get(
                                CaptureResult.LENS_INTRINSIC_CALIBRATION);
                        float focalLength = Float.NaN;
                        if (lensIntrinsics != null) {
                            focalLength = lensIntrinsics[0];
                        }
                        if ((!Float.isNaN(lastZoomRatio)) && (zoomRatio > lastZoomRatio)) {
                            if (lastActivePhysicalId.equals(activePhysicalId)) {
                                assertTrue(lastActiveCropRegion.contains(activeCropRegion));

                                if (!Float.isNaN(lastFocalLength)) {
                                    float digitalZoomApplied =
                                            ((float) lastActiveCropRegion.width()) /
                                                    activeCropRegion.width();
                                    float opticalZoomApplied = (focalLength / lastFocalLength);
                                    float combinedZoomApplied =
                                            digitalZoomApplied * opticalZoomApplied;
                                    float zoomReported = zoomRatio / lastZoomRatio;
                                    assertTrue("Combined zoom: " + combinedZoomApplied +
                                                    " too far apart from reported zoom: " +
                                            zoomReported, Math.abs(
                                            combinedZoomApplied - zoomReported) <= 0.0001);
                                }
                            }
                        }
                        lastActivePhysicalId = activePhysicalId;
                        lastZoomRatio = zoomRatio;
                        lastActiveCropRegion = activeCropRegion;
                        lastFocalLength = focalLength;
                    }

                    LensIntrinsicsSample [] samples = result.get(
                            CaptureResult.STATISTICS_LENS_INTRINSICS_SAMPLES);
                    if (samples != null) {
                        long previousTs = Long.MIN_VALUE;
                        float[] previousIntrinicArray = new float[5];
                        for (LensIntrinsicsSample sample : samples) {
                            assertTrue(previousTs < sample.getTimestampNanos());
                            assertTrue(Arrays.hashCode(previousIntrinicArray) !=
                                    Arrays.hashCode(sample.getLensIntrinsics()));
                            previousIntrinicArray = sample.getLensIntrinsics();
                            previousTs = sample.getTimestampNanos();
                        }
                    }
                }
            }
            // stop capture.
            stopCapture(/*fast*/false);

            if (activePhysicalIdsSeen.size() > 0 && format == ImageFormat.RAW_SENSOR) {
                mCollector.expectTrue("Logical camera's activePhysicalCamera should not " +
                        " change at different zoom levels.", activePhysicalIdsSeen.size() == 1);
            }

        } finally {
            closeDefaultImageReader();
        }
    }

    private final class SimpleImageListener implements ImageReader.OnImageAvailableListener {
        private final int mImageFormat;
        private final Size mMaxSize;
        public SimpleImageListener(int imageFormat, Size maxSize) {
            mImageFormat = imageFormat;
            mMaxSize = maxSize;
        }

        @Override
        public void onImageAvailable(ImageReader reader) {
            Image img = mReader.acquireNextImage();
            assertNotNull("Unable to acquire the latest image", img);
            CameraTestUtils.validateImage(img, mMaxSize.getWidth(), mMaxSize.getHeight(),
                    mImageFormat, mDebugFileNameBase);
            Log.e(TAG, "Image verification done");
            img.close();
        }
    }
}
