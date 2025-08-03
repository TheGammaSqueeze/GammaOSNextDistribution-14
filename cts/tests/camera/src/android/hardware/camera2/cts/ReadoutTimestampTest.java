/*
 * Copyright 2022 The Android Open Source Project
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

import static android.hardware.camera2.cts.CameraTestUtils.SimpleImageReaderListener;
import static android.hardware.camera2.cts.CameraTestUtils.assertNotNull;
import static android.hardware.camera2.cts.CameraTestUtils.configureCameraSessionWithConfig;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.cts.testcases.Camera2SurfaceViewTestCase;
import android.hardware.camera2.params.OutputConfiguration;
import android.media.Image;
import android.media.ImageReader;
import android.util.Log;
import android.util.Size;

import com.android.ex.camera2.blocking.BlockingSessionCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

public class ReadoutTimestampTest extends Camera2SurfaceViewTestCase {
    private static final String TAG = "ReadoutTimestampTest";

    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Test the camera readout timestamp work properly:
     *
     * If SENSOR_READOUT_TIMESTAMP is supported:
     * - Each onCaptureStarted() callback has a corresponding onReadoutStarted() callback.
     * - The readout timestamp is at least startOfExposure timestamp + exposure time.
     * - Image timestamp matches the onReadoutStarted timestamp
     */
    @Test
    public void testReadoutTimestamp() throws Exception {
        int[] timestampBases = new int[]{
                OutputConfiguration.TIMESTAMP_BASE_DEFAULT,
                OutputConfiguration.TIMESTAMP_BASE_SENSOR,
                OutputConfiguration.TIMESTAMP_BASE_MONOTONIC,
                OutputConfiguration.TIMESTAMP_BASE_REALTIME,
                OutputConfiguration.TIMESTAMP_BASE_CHOREOGRAPHER_SYNCED};

        for (String cameraId : getCameraIdsUnderTest()) {
            if (!mAllStaticInfo.get(cameraId).isColorOutputSupported()) {
                continue;
            }

            try {
                openDevice(cameraId);
                for (int timestampBase : timestampBases) {
                    testReadoutTimestamp(cameraId, timestampBase);
                }
            } finally {
                closeDevice();
            }
        }
    }

    private void testReadoutTimestamp(String cameraId, int timestampBase) throws Exception {
        Log.i(TAG, "testReadoutTimestamp for camera " + cameraId +
                " with timestampBase " + timestampBase);
        Integer sensorReadoutTimestamp = mStaticInfo.getCharacteristics().get(
                CameraCharacteristics.SENSOR_READOUT_TIMESTAMP);
        assertNotNull("Camera " + cameraId + ": READOUT_TIMESTAMP "
                + "must not be null", sensorReadoutTimestamp);
        if (CameraMetadata.SENSOR_READOUT_TIMESTAMP_NOT_SUPPORTED
                == sensorReadoutTimestamp) {
            return;
        }

        // Camera device supports readout timestamp
        final int NUM_CAPTURE = 5;
        final int WAIT_FOR_RESULT_TIMEOUT_MS = 3000;
        Integer timestampSource = mStaticInfo.getCharacteristics().get(
                CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE);

        // Create ImageReader as output
        Size maxPreviewSize = mOrderedPreviewSizes.get(0);
        SimpleImageReaderListener readerListener = new SimpleImageReaderListener();
        ImageReader reader = ImageReader.newInstance(maxPreviewSize.getWidth(),
                maxPreviewSize.getHeight(), ImageFormat.YUV_420_888, /*maxImage*/ NUM_CAPTURE);
        reader.setOnImageAvailableListener(readerListener, mHandler);

        // Create session
        List<OutputConfiguration> outputConfigs = new ArrayList<>();
        OutputConfiguration configuration = new OutputConfiguration(reader.getSurface());
        assertFalse("OutputConfiguration: Readout timestamp should be false by default",
                configuration.isReadoutTimestampEnabled());
        configuration.setTimestampBase(timestampBase);
        configuration.setReadoutTimestampEnabled(true);
        assertTrue("OutputConfiguration: Readout timestamp should be true",
                configuration.isReadoutTimestampEnabled());
        outputConfigs.add(configuration);

        BlockingSessionCallback sessionCallback = new BlockingSessionCallback();
        mSession = configureCameraSessionWithConfig(mCamera, outputConfigs,
                sessionCallback, mHandler);

        // Capture frames as well as shutter/result callbacks
        CaptureRequest.Builder previewRequest =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        previewRequest.addTarget(reader.getSurface());
        ReadoutCaptureCallback resultListener = new ReadoutCaptureCallback();
        List<CaptureRequest> burst = new ArrayList<>();
        for (int i = 0; i < NUM_CAPTURE; i++) {
            burst.add(previewRequest.build());
        }
        mSession.captureBurst(burst, resultListener, mHandler);

        ArrayList<CaptureResult> results = new ArrayList<>();
        for (int i = 0; i < NUM_CAPTURE; i++) {
            CaptureResult result = resultListener.getCaptureResult(WAIT_FOR_RESULT_TIMEOUT_MS);
            assertNotNull("Camera " + cameraId + ": Capture result must not be null", result);
            results.add(result);
        }

        ReadoutCaptureCallback.TimestampTuple[] timestamps = resultListener.getTimestamps();
        assertNotNull("Camera " + cameraId + ": No timestamps received by resultListener",
                timestamps);
        assertTrue("Camera " + cameraId + " timestampBase " + timestampBase
                + ": Not enough onCaptureStarted/onReadoutStarted "
                + "callbacks. Expected at least " + 2 * NUM_CAPTURE + ", actual "
                + timestamps.length, timestamps.length >= 2 * NUM_CAPTURE);
        for (int i = 0; i < NUM_CAPTURE; i++) {
            // Each capture result has corresponding onCaptureStarted and onReadoutStarted callbacks
            mCollector.expectTrue(String.format("Camera %s timestampBase %d: timestamps[%d] should "
                    + "be from onCaptureStarted", cameraId, timestampBase, i * 2),
                    timestamps[i * 2].mType == ReadoutCaptureCallback.CAPTURE_TIMESTAMP);
            mCollector.expectTrue(String.format("Camera %s timestampBase %d: timestamps[%d] should "
                    + "be from onReadoutStarted", cameraId, timestampBase, i * 2 + 1),
                    timestamps[i * 2 + 1].mType == ReadoutCaptureCallback.READOUT_TIMESTAMP);


            if (timestampBase == OutputConfiguration.TIMESTAMP_BASE_DEFAULT ||
                    timestampBase == OutputConfiguration.TIMESTAMP_BASE_SENSOR ||
                    (timestampBase == OutputConfiguration.TIMESTAMP_BASE_MONOTONIC &&
                    timestampSource == CameraMetadata.SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN) ||
                    (timestampBase == OutputConfiguration.TIMESTAMP_BASE_REALTIME &&
                    timestampSource == CameraMetadata.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME)) {
                // The readoutTime in onReadoutStarted must match that of the images
                Image image = readerListener.getImage(CameraTestUtils.CAPTURE_IMAGE_TIMEOUT_MS);
                Long imageTime = image.getTimestamp();
                mCollector.expectEquals("Camera " + cameraId + " timestampBase " + timestampBase
                        + " readoutTimestamp (" + timestamps[i * 2 + 1].mTimestamp
                        + ") should be equal to image timestamp (" + imageTime,
                        imageTime, timestamps[i * 2 + 1].mTimestamp);
                image.close();
            }

            // The exposure time must be at least readoutTime - captureTime
            Long exposureTime = results.get(i).get(CaptureResult.SENSOR_EXPOSURE_TIME);
            if (exposureTime == null) {
                // If exposureTime is null in CaptureResult, the camera device doesn't support
                // READ_SENOSR_SETTINGS capability.
                boolean hasReadSensorSettings = mStaticInfo.isCapabilitySupported(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS);
                assertFalse("Camera " + cameraId + ": ExposureTime must not be null if "
                        + "READ_SENSOR_SETTINGS is supported", hasReadSensorSettings);

                mCollector.expectTrue("Camera " + cameraId + " timestampBase " + timestampBase
                        + ": readoutTime (" + timestamps[i * 2 + 1].mTimestamp
                        + ") - captureStart time (" + timestamps[i * 2].mTimestamp
                        + ") should be > 0",
                        timestamps[i * 2 + 1].mTimestamp > timestamps[i * 2].mTimestamp);
            } else {
                mCollector.expectTrue("Camera " + cameraId + "timestampBase " + timestampBase
                        + ": readoutTime (" + timestamps[i * 2 + 1].mTimestamp
                        + ") - captureStart time (" + timestamps[i * 2].mTimestamp
                        + ") should be >= exposureTime (" + exposureTime + ")",
                        timestamps[i * 2 + 1].mTimestamp - timestamps[i * 2].mTimestamp >=
                        exposureTime);
            }
        }
    }

    public static class ReadoutCaptureCallback extends CameraCaptureSession.CaptureCallback {
        public static final int CAPTURE_TIMESTAMP = 0;
        public static final int READOUT_TIMESTAMP = 1;

        public static class TimestampTuple {
            public int mType;
            public Long mTimestamp;
            public TimestampTuple(int type, Long timestamp) {
                mType = type;
                mTimestamp = timestamp;
            }
        }

        private final LinkedBlockingQueue<TotalCaptureResult> mQueue =
                new LinkedBlockingQueue<TotalCaptureResult>();
        // TimestampTuple is a pair of CAPTURE/READOUT flag and timestamp.
        private final LinkedBlockingQueue<TimestampTuple> mTimestampQueue =
                new LinkedBlockingQueue<>();

        @Override
        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request,
                long timestamp, long frameNumber) {
            try {
                mTimestampQueue.put(new TimestampTuple(CAPTURE_TIMESTAMP, timestamp));
            } catch (InterruptedException e) {
                throw new UnsupportedOperationException(
                        "Can't handle InterruptedException in onCaptureStarted");
            }
        }

        public void onReadoutStarted(CameraCaptureSession session, CaptureRequest request,
                long timestamp, long frameNumber) {
            try {
                mTimestampQueue.put(new TimestampTuple(READOUT_TIMESTAMP, timestamp));
            } catch (InterruptedException e) {
                throw new UnsupportedOperationException(
                        "Can't handle InterruptedException in onReadoutStarted");
            }
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                TotalCaptureResult result) {
            try {
                mQueue.put(result);
            } catch (InterruptedException e) {
                throw new UnsupportedOperationException(
                        "Can't handle InterruptedException in onCaptureCompleted");
            }
        }

        public CaptureResult getCaptureResult(long timeout) {
            try {
                TotalCaptureResult result = mQueue.poll(timeout, TimeUnit.MILLISECONDS);
                assertNotNull("Wait for a capture result timed out in " + timeout + "ms", result);
                return result;
            } catch (InterruptedException e) {
                throw new UnsupportedOperationException("Unhandled InterruptedException", e);
            }
        }

        public TimestampTuple[] getTimestamps() {
            return mTimestampQueue.toArray(new TimestampTuple[0]);
        }
    }
}
