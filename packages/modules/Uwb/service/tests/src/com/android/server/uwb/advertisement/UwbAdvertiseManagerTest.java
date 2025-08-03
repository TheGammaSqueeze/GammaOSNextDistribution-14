/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.uwb.advertisement;

import static com.android.server.uwb.util.DataTypeConversionUtil.macAddressByteArrayToLong;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import android.platform.test.annotations.Presubmit;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.uwb.DeviceConfigFacade;
import com.android.server.uwb.UwbInjector;
import com.android.server.uwb.data.UwbOwrAoaMeasurement;
import com.android.server.uwb.util.UwbUtil;

import com.google.uwb.support.fira.FiraParams;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.ByteBuffer;

/**
 * Tests for {@link UwbAdvertiseManager}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class UwbAdvertiseManagerTest {
    private static final byte[] TEST_MAC_ADDRESS_A = {0x11, 0x13};
    private static final long TEST_MAC_ADDRESS_A_LONG =
            macAddressByteArrayToLong(TEST_MAC_ADDRESS_A);
    private static final byte[] TEST_MAC_ADDRESS_B = {0x15, 0x17};
    private static final int TEST_MAC_ADDRESS_B_INT =
            ByteBuffer.wrap(TEST_MAC_ADDRESS_B).getShort();
    private static final byte[] TEST_MAC_ADDRESS_C = {0x12, 0x14};
    private static final int TEST_STATUS = FiraParams.STATUS_CODE_OK;
    private static final int TEST_LOS = 3;
    private static final int TEST_BLOCK_INDEX = 5;
    private static final int TEST_FRAME_SEQ_NUMBER = 1;

    private static final int TEST_AOA_AZIMUTH = 7;
    private static final float TEST_AOA_AZIMUTH_FLOAT =
            UwbUtil.convertQFormatToFloat(UwbUtil.twos_compliment(TEST_AOA_AZIMUTH, 16), 9, 7);
    private static final int TEST_AOA_AZIMUTH_Q97_FORMAT =
            UwbUtil.convertFloatToQFormat(TEST_AOA_AZIMUTH_FLOAT, 9, 7);
    private static final int TEST_AOA_AZIMUTH_FOM = 50;

    private static final int TEST_AOA_ELEVATION = 5;
    private static final float TEST_AOA_ELEVATION_FLOAT =
            UwbUtil.convertQFormatToFloat(UwbUtil.twos_compliment(TEST_AOA_ELEVATION, 16), 9, 7);
    private static final int TEST_AOA_ELEVATION_Q97_FORMAT =
            UwbUtil.convertFloatToQFormat(TEST_AOA_ELEVATION_FLOAT, 9, 7);
    private static final int TEST_AOA_ELEVATION_FOM = 90;

    // Constants for the values that will be returned by the overlays (via DeviceConfigFacade).
    private static final int OVERLAY_CRITERIA_ANGLE = 10;
    private static final int OVERLAY_TIME_THRESHOLD = 5000;
    private static final int OVERLAY_ARRAY_SIZE_TO_CHECK = 10;
    private static final int OVERLAY_ARRAY_START_INDEX_TO_CAL_VARIANCE = 2;
    private static final int OVERLAY_ARRAY_END_INDEX_TO_CAL_VARIANCE = 8;
    private static final int OVERLAY_TRUSTED_VALUE_OF_VARIANCE = 5;

    // Setup the AoA Azimuth and Elevation variances, such that the device will be considered as
    // being pointed or not.
    private static final int TEST_DELTA_AOA_INSIDE_VARIANCE =
            (int) Math.sqrt(OVERLAY_TRUSTED_VALUE_OF_VARIANCE) - 1;
    private static final int TEST_DELTA_AOA_OUTSIDE_VARIANCE =
            (int) Math.sqrt(OVERLAY_TRUSTED_VALUE_OF_VARIANCE) + 1;

    // Required minimum number of OwR AoA Measurements to determine the pointing behavior.
    private static final int NUM_REQUIRED_OWR_AOA_MEASUREMENTS = OVERLAY_ARRAY_SIZE_TO_CHECK;

    // Setup the starting time instant for the first measurement, a fixed time interval between
    // two measurements, and a delayed time instance which is outside the validity window of the
    // last measurement.
    private static final long FIRST_OWR_AOA_MEASUREMENT_TIME_MILLIS = 1000;
    private static final long OWR_AOA_MEASUREMENT_INTERVAL_MILLIS = 100;
    private static final long LAST_OWR_AOA_MEASUREMENT_TIME_MILLIS =
            FIRST_OWR_AOA_MEASUREMENT_TIME_MILLIS
                    + (OWR_AOA_MEASUREMENT_INTERVAL_MILLIS * NUM_REQUIRED_OWR_AOA_MEASUREMENTS);
    private static final long OWR_AOA_MEASUREMENT_TIME_OUTSIDE_THRESHOLD_MILLIS =
            LAST_OWR_AOA_MEASUREMENT_TIME_MILLIS + OVERLAY_TIME_THRESHOLD + 1;

    // Setup multiple devices (with different MacAddress) for testing multi remote device scenario,
    // and validating that the class can handle ranging with multiple devices simultaneously.
    private static final UwbOwrAoaMeasurement UWB_OWR_AOA_MEASUREMENT_DEVICE_A =
            new UwbOwrAoaMeasurement(TEST_MAC_ADDRESS_A, TEST_STATUS, TEST_LOS,
                    TEST_FRAME_SEQ_NUMBER, TEST_BLOCK_INDEX,
                    TEST_AOA_AZIMUTH_Q97_FORMAT, TEST_AOA_AZIMUTH_FOM,
                    TEST_AOA_ELEVATION_Q97_FORMAT, TEST_AOA_ELEVATION_FOM);

    @Mock private UwbInjector mUwbInjector;
    @Mock private DeviceConfigFacade mDeviceConfigFacade;

    private UwbAdvertiseManager mUwbAdvertiseManager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mUwbAdvertiseManager = new UwbAdvertiseManager(mUwbInjector, mDeviceConfigFacade);
        when(mUwbInjector.getElapsedSinceBootMillis()).thenReturn(
                FIRST_OWR_AOA_MEASUREMENT_TIME_MILLIS);

        when(mDeviceConfigFacade.getAdvertiseAoaCriteriaAngle()).thenReturn(OVERLAY_CRITERIA_ANGLE);
        when(mDeviceConfigFacade.getAdvertiseTimeThresholdMillis())
                .thenReturn(OVERLAY_TIME_THRESHOLD);
        when(mDeviceConfigFacade.getAdvertiseTrustedVarianceValue())
                .thenReturn(OVERLAY_TRUSTED_VALUE_OF_VARIANCE);
        when(mDeviceConfigFacade.getAdvertiseArraySizeToCheck())
                .thenReturn(OVERLAY_ARRAY_SIZE_TO_CHECK);
        when(mDeviceConfigFacade.getAdvertiseArrayStartIndexToCalVariance())
                .thenReturn(OVERLAY_ARRAY_START_INDEX_TO_CAL_VARIANCE);
        when(mDeviceConfigFacade.getAdvertiseArrayEndIndexToCalVariance())
                .thenReturn(OVERLAY_ARRAY_END_INDEX_TO_CAL_VARIANCE);
    }

    @Test
    public void testIsTarget_success() throws Exception {
        setupOwrAoaMeasurements(TEST_MAC_ADDRESS_A, NUM_REQUIRED_OWR_AOA_MEASUREMENTS,
                TEST_AOA_AZIMUTH_Q97_FORMAT, TEST_DELTA_AOA_INSIDE_VARIANCE,
                TEST_AOA_ELEVATION_Q97_FORMAT, TEST_DELTA_AOA_INSIDE_VARIANCE);
        assertTrue(mUwbAdvertiseManager.isPointedTarget(TEST_MAC_ADDRESS_A));
    }

    // Confirm that UwbAdvertiseManager is able to store OwrAoaMeasurement(s) for multiple devices
    // simultaneously, and return the appropriate response to isPointedTarget(). We test this by
    // using 3 devices - Device A and B which are pointed targets, and a Device C which is not.
    @Test
    public void testIsTarget_successMultipleDevices() throws Exception {
        // First process the OWR AoA Measurements for all the devices.
        setupOwrAoaMeasurements(TEST_MAC_ADDRESS_A, NUM_REQUIRED_OWR_AOA_MEASUREMENTS,
                TEST_AOA_AZIMUTH_Q97_FORMAT, TEST_DELTA_AOA_INSIDE_VARIANCE,
                TEST_AOA_ELEVATION_Q97_FORMAT, TEST_DELTA_AOA_INSIDE_VARIANCE);
        setupOwrAoaMeasurements(TEST_MAC_ADDRESS_B, NUM_REQUIRED_OWR_AOA_MEASUREMENTS,
                TEST_AOA_AZIMUTH_Q97_FORMAT, TEST_DELTA_AOA_INSIDE_VARIANCE,
                TEST_AOA_ELEVATION_Q97_FORMAT, TEST_DELTA_AOA_INSIDE_VARIANCE);
        setupOwrAoaMeasurements(TEST_MAC_ADDRESS_C, NUM_REQUIRED_OWR_AOA_MEASUREMENTS,
                TEST_AOA_AZIMUTH_Q97_FORMAT, TEST_DELTA_AOA_OUTSIDE_VARIANCE,
                TEST_AOA_ELEVATION_Q97_FORMAT, TEST_DELTA_AOA_OUTSIDE_VARIANCE);

        // Confirm the behavior, based on the recorded measurements for each device.
        assertTrue(mUwbAdvertiseManager.isPointedTarget(TEST_MAC_ADDRESS_A));
        assertTrue(mUwbAdvertiseManager.isPointedTarget(TEST_MAC_ADDRESS_B));
        assertFalse(mUwbAdvertiseManager.isPointedTarget(TEST_MAC_ADDRESS_C));
    }

    // Call isPointedTarget() before any calls to updateAdvertiseTarget() for the device. Confirm
    // that it returns "false" as there is no saved data for that device (MacAddress A).
    @Test
    public void testIsTarget_beforeUpdate() throws Exception {
        assertFalse(mUwbAdvertiseManager.isPointedTarget(TEST_MAC_ADDRESS_A));
        assertNull(mUwbAdvertiseManager.getAdvertiseTarget(TEST_MAC_ADDRESS_A_LONG));
    }

    // Call isPointedTarget() with a different MacAddress (device B), after a call to
    // updateAdvertiseTarget() for device A. This should not use the data saved for the device A.
    @Test
    public void testIsTarget_differentMacAddress() throws Exception {
        mUwbAdvertiseManager.updateAdvertiseTarget(UWB_OWR_AOA_MEASUREMENT_DEVICE_A);
        assertFalse(mUwbAdvertiseManager.isPointedTarget(TEST_MAC_ADDRESS_B));
        assertNotNull(mUwbAdvertiseManager.getAdvertiseTarget(TEST_MAC_ADDRESS_A_LONG));
    }

    // Confirm the device is not considered to be a target when there aren't sufficient OWR AoA
    // measurements.
    @Test
    public void testIsTarget_tooFewOwrAoaMeasurements() throws Exception {
        setupOwrAoaMeasurements(TEST_MAC_ADDRESS_A, NUM_REQUIRED_OWR_AOA_MEASUREMENTS - 1,
                TEST_AOA_AZIMUTH_Q97_FORMAT, TEST_DELTA_AOA_INSIDE_VARIANCE,
                TEST_AOA_ELEVATION_Q97_FORMAT, TEST_DELTA_AOA_INSIDE_VARIANCE);
        assertFalse(mUwbAdvertiseManager.isPointedTarget(TEST_MAC_ADDRESS_A));
    }

    // Confirm the device continues to be a target when there are extra OWR AoA measurements.
    @Test
    public void testIsTarget_tooManyOwrAoaMeasurements() throws Exception {
        setupOwrAoaMeasurements(TEST_MAC_ADDRESS_A, NUM_REQUIRED_OWR_AOA_MEASUREMENTS + 1,
                TEST_AOA_AZIMUTH_Q97_FORMAT, TEST_DELTA_AOA_INSIDE_VARIANCE,
                TEST_AOA_ELEVATION_Q97_FORMAT, TEST_DELTA_AOA_INSIDE_VARIANCE);
        assertTrue(mUwbAdvertiseManager.isPointedTarget(TEST_MAC_ADDRESS_A));
    }

    // Confirm the behavior when the AoA Azimuth in some of the stored OWR AoA Measurement(s) are
    // outside the expected criterion angle range.
    @Test
    public void testIsTarget_previousOwrAoAMeasurement_outsideCriterionAngleOfAzimuth()
            throws Exception {
        UwbOwrAoaMeasurement uwbOwrAoaMeasurement = setupOwrAoaMeasurements(TEST_MAC_ADDRESS_A,
                NUM_REQUIRED_OWR_AOA_MEASUREMENTS,
                OVERLAY_CRITERIA_ANGLE + 1, TEST_DELTA_AOA_INSIDE_VARIANCE,
                TEST_AOA_ELEVATION_Q97_FORMAT, TEST_DELTA_AOA_INSIDE_VARIANCE);
        uwbOwrAoaMeasurement.mAoaAzimuth = TEST_AOA_AZIMUTH_Q97_FORMAT;
        assertFalse(mUwbAdvertiseManager.isPointedTarget(TEST_MAC_ADDRESS_A));
    }

    // Confirm the behavior when the AoA Elevation in some of the stored OWR AoA Measurement(s) are
    // outside the expected criterion angle range.
    @Test
    public void testIsTarget_previousOwrAoAMeasurement_outsideCriterionAngleOfElevation()
            throws Exception {
        UwbOwrAoaMeasurement uwbOwrAoaMeasurement = setupOwrAoaMeasurements(TEST_MAC_ADDRESS_A,
                NUM_REQUIRED_OWR_AOA_MEASUREMENTS,
                TEST_AOA_AZIMUTH_Q97_FORMAT, TEST_DELTA_AOA_INSIDE_VARIANCE,
                OVERLAY_CRITERIA_ANGLE + 1, TEST_DELTA_AOA_INSIDE_VARIANCE);
        uwbOwrAoaMeasurement.mAoaElevation = TEST_AOA_ELEVATION_Q97_FORMAT;
        assertFalse(mUwbAdvertiseManager.isPointedTarget(TEST_MAC_ADDRESS_A));
    }

    // Confirm the behavior when the AoA Azimuth in the last stored OWR AoA Measurement is
    // outside the expected criterion angle range.
    @Test
    public void testIsTarget_lastOwrAoAMeasurement_outsideCriterionAngleOfAzimuth()
            throws Exception {
        UwbOwrAoaMeasurement uwbOwrAoaMeasurement = setupOwrAoaMeasurements(TEST_MAC_ADDRESS_A,
                NUM_REQUIRED_OWR_AOA_MEASUREMENTS - 1,
                TEST_AOA_AZIMUTH_Q97_FORMAT, TEST_DELTA_AOA_INSIDE_VARIANCE,
                TEST_AOA_ELEVATION_Q97_FORMAT, TEST_DELTA_AOA_INSIDE_VARIANCE);

        // Setup the last OwrAoaMeasurement with an AoaAzimuth outside the valid criteria angle.
        uwbOwrAoaMeasurement.mFrameSequenceNumber++;
        uwbOwrAoaMeasurement.mAoaAzimuth = OVERLAY_CRITERIA_ANGLE + 1;
        when(mUwbInjector.getElapsedSinceBootMillis()).thenReturn(
                LAST_OWR_AOA_MEASUREMENT_TIME_MILLIS);
        mUwbAdvertiseManager.updateAdvertiseTarget(uwbOwrAoaMeasurement);

        assertFalse(mUwbAdvertiseManager.isPointedTarget(TEST_MAC_ADDRESS_A));
    }

    // Confirm the behavior when the AoA Elevation in the last stored OWR AoA Measurement is
    // outside the expected criterion angle range.
    @Test
    public void testIsTarget_lastOwrAoAMeasurement_outsideCriterionAngleOfElevation()
            throws Exception {
        UwbOwrAoaMeasurement uwbOwrAoaMeasurement = setupOwrAoaMeasurements(TEST_MAC_ADDRESS_A,
                NUM_REQUIRED_OWR_AOA_MEASUREMENTS - 1,
                TEST_AOA_AZIMUTH_Q97_FORMAT, TEST_DELTA_AOA_INSIDE_VARIANCE,
                TEST_AOA_ELEVATION_Q97_FORMAT, TEST_DELTA_AOA_INSIDE_VARIANCE);

        // Setup the last OwrAoaMeasurement with an AoaElevation outside the valid criteria angle.
        uwbOwrAoaMeasurement.mFrameSequenceNumber++;
        uwbOwrAoaMeasurement.mAoaElevation = OVERLAY_CRITERIA_ANGLE + 1;
        when(mUwbInjector.getElapsedSinceBootMillis()).thenReturn(
                LAST_OWR_AOA_MEASUREMENT_TIME_MILLIS);
        mUwbAdvertiseManager.updateAdvertiseTarget(uwbOwrAoaMeasurement);

        assertFalse(mUwbAdvertiseManager.isPointedTarget(TEST_MAC_ADDRESS_A));
    }

    @Test
    public void testIsTarget_storedOwrAoAMeasurement_outsideCriterionVarianceOfAzimuth()
            throws Exception {
        setupOwrAoaMeasurements(TEST_MAC_ADDRESS_A, NUM_REQUIRED_OWR_AOA_MEASUREMENTS,
                TEST_AOA_AZIMUTH_Q97_FORMAT, TEST_DELTA_AOA_OUTSIDE_VARIANCE,
                TEST_AOA_ELEVATION_Q97_FORMAT, TEST_DELTA_AOA_INSIDE_VARIANCE);
        assertFalse(mUwbAdvertiseManager.isPointedTarget(TEST_MAC_ADDRESS_A));
    }

    @Test
    public void testIsTarget_storedOwrAoAMeasurement_outsideCriterionVarianceOfElevation()
            throws Exception {
        setupOwrAoaMeasurements(TEST_MAC_ADDRESS_A, NUM_REQUIRED_OWR_AOA_MEASUREMENTS,
                TEST_AOA_AZIMUTH_Q97_FORMAT, TEST_DELTA_AOA_INSIDE_VARIANCE,
                TEST_AOA_ELEVATION_Q97_FORMAT, TEST_DELTA_AOA_OUTSIDE_VARIANCE);
        assertFalse(mUwbAdvertiseManager.isPointedTarget(TEST_MAC_ADDRESS_A));
    }

    @Test
    public void testIsTarget_outsideTimeThreshold() throws Exception {
        setupOwrAoaMeasurements(TEST_MAC_ADDRESS_A, NUM_REQUIRED_OWR_AOA_MEASUREMENTS,
                TEST_AOA_AZIMUTH_Q97_FORMAT, TEST_DELTA_AOA_INSIDE_VARIANCE,
                TEST_AOA_ELEVATION_Q97_FORMAT, TEST_DELTA_AOA_INSIDE_VARIANCE);

        when(mUwbInjector.getElapsedSinceBootMillis()).thenReturn(
                OWR_AOA_MEASUREMENT_TIME_OUTSIDE_THRESHOLD_MILLIS);
        assertFalse(mUwbAdvertiseManager.isPointedTarget(TEST_MAC_ADDRESS_A));
    }

    @Test
    public void testUpdateAdvertiseTarget_outsideTimeThreshold() throws Exception {
        // Setup OwR AoA Measurements such that the device is a pointed target.
        UwbOwrAoaMeasurement uwbOwrAoaMeasurement = setupOwrAoaMeasurements(TEST_MAC_ADDRESS_A,
                NUM_REQUIRED_OWR_AOA_MEASUREMENTS,
                TEST_AOA_AZIMUTH_Q97_FORMAT, TEST_DELTA_AOA_INSIDE_VARIANCE,
                TEST_AOA_ELEVATION_Q97_FORMAT, TEST_DELTA_AOA_INSIDE_VARIANCE);
        assertTrue(mUwbAdvertiseManager.isPointedTarget(TEST_MAC_ADDRESS_A));
        UwbAdvertiseManager.UwbAdvertiseTarget uwbAdvertiseTarget =
                mUwbAdvertiseManager.getAdvertiseTarget(TEST_MAC_ADDRESS_A_LONG);
        assertTrue(uwbAdvertiseTarget.isVarianceCalculated());

        // Fake the current time such that the stored OwR AoA Measurements now seem to be stale, and
        // record one more OwR AoA Measurement.
        uwbOwrAoaMeasurement.mFrameSequenceNumber++;
        when(mUwbInjector.getElapsedSinceBootMillis()).thenReturn(
                OWR_AOA_MEASUREMENT_TIME_OUTSIDE_THRESHOLD_MILLIS);
        mUwbAdvertiseManager.updateAdvertiseTarget(uwbOwrAoaMeasurement);

        // Check that the variance is not calculated (as a proxy for the number of stored OwR AoA
        // measurements for the target, which should now be just 1).
        uwbAdvertiseTarget = mUwbAdvertiseManager.getAdvertiseTarget(TEST_MAC_ADDRESS_A_LONG);
        assertFalse(uwbAdvertiseTarget.isVarianceCalculated());
        assertFalse(mUwbAdvertiseManager.isPointedTarget(TEST_MAC_ADDRESS_A));
    }

    @Test
    public void testRemoveAdvertiseTarget() throws Exception {
        // Call updateAdvertiseTarget() with a OwR AoA Measurement and verify that a
        // UwbAdvertiseTarget gets created for it (but not for another random device).
        UwbOwrAoaMeasurement uwbOwrAoaMeasurement = setupOwrAoaMeasurements(TEST_MAC_ADDRESS_A,
                NUM_REQUIRED_OWR_AOA_MEASUREMENTS,
                TEST_AOA_AZIMUTH_Q97_FORMAT, TEST_DELTA_AOA_INSIDE_VARIANCE,
                TEST_AOA_ELEVATION_Q97_FORMAT, TEST_DELTA_AOA_INSIDE_VARIANCE);
        mUwbAdvertiseManager.updateAdvertiseTarget(uwbOwrAoaMeasurement);

        assertNotNull(mUwbAdvertiseManager.getAdvertiseTarget(TEST_MAC_ADDRESS_A_LONG));
        assertNull(mUwbAdvertiseManager.getAdvertiseTarget(TEST_MAC_ADDRESS_B_INT));

        // Call removeAdvertiseTarget() for the device and verify that it has been removed.
        mUwbAdvertiseManager.removeAdvertiseTarget(TEST_MAC_ADDRESS_A_LONG);
        assertNull(mUwbAdvertiseManager.getAdvertiseTarget(TEST_MAC_ADDRESS_A_LONG));

        // Call removeAdvertiseTarget() for a device that doesn't exist and verify no exceptions.
        mUwbAdvertiseManager.removeAdvertiseTarget(TEST_MAC_ADDRESS_B_INT);
        assertNull(mUwbAdvertiseManager.getAdvertiseTarget(TEST_MAC_ADDRESS_B_INT));
    }

    private UwbOwrAoaMeasurement setupOwrAoaMeasurements(byte[] macAddress, int numMeasurements,
            int aoaAzimuth, int aoaAzimuthVariance,
            int aoaElevation, int aoaElevationVariance) {
        UwbOwrAoaMeasurement uwbOwrAoaMeasurement = new UwbOwrAoaMeasurement(macAddress,
                TEST_STATUS, TEST_LOS, TEST_FRAME_SEQ_NUMBER, TEST_BLOCK_INDEX,
                aoaAzimuth, TEST_AOA_AZIMUTH_FOM, aoaElevation, TEST_AOA_ELEVATION_FOM);

        long currentTimeMillis = FIRST_OWR_AOA_MEASUREMENT_TIME_MILLIS;

        // Create multiple OwR AoA Measurements with some variations - incrementing the frame
        // sequence number, the Aoa Azimuth & Elevation using a given variance and the timestamp.
        // These parameters will create scenarios for which we confirm behavior in above tests.
        for (int i = 0; i < numMeasurements; i++) {
            uwbOwrAoaMeasurement.mFrameSequenceNumber++;
            uwbOwrAoaMeasurement.mAoaAzimuth = (i % 2 == 0)
                    ? aoaAzimuth + aoaAzimuthVariance : aoaAzimuth - aoaAzimuthVariance;
            uwbOwrAoaMeasurement.mAoaElevation = (i % 2 == 0)
                    ? aoaElevation + aoaElevationVariance : aoaElevation - aoaElevationVariance;

            when(mUwbInjector.getElapsedSinceBootMillis()).thenReturn(currentTimeMillis);
            currentTimeMillis += OWR_AOA_MEASUREMENT_INTERVAL_MILLIS;

            mUwbAdvertiseManager.updateAdvertiseTarget(uwbOwrAoaMeasurement);
        }
        return uwbOwrAoaMeasurement;
    }
}
