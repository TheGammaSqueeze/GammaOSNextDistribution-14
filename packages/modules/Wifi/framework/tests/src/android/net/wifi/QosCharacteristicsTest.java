/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.net.wifi;

import static android.net.wifi.QosCharacteristics.DELIVERY_RATIO_95;
import static android.net.wifi.QosCharacteristics.DELIVERY_RATIO_99_9999;

import static junit.framework.Assert.assertFalse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.os.Parcel;

import com.android.modules.utils.build.SdkLevel;

import org.junit.Before;
import org.junit.Test;

public class QosCharacteristicsTest {
    private static final int TEST_MIN_SERVICE_INTERVAL_MICROS = 2000;
    private static final int TEST_MAX_SERVICE_INTERVAL_MICROS = 5000;
    private static final int TEST_MIN_DATA_RATE_KBPS = 500;
    private static final int TEST_BURST_SIZE_OCTETS = 2;
    private static final int TEST_DELAY_BOUND_MICROS = 200;
    private static final int TEST_MAX_MSDU_SIZE_OCTETS = 4;
    private static final int TEST_SERVICE_START_TIME_MICROS = 250;
    private static final int TEST_SERVICE_START_TIME_LINK_ID = 0x5;
    private static final int TEST_MEAN_DATA_RATE_KBPS = 1500;
    private static final int TEST_MSDU_LIFETIME_MILLIS = 400;
    private static final int TEST_DELIVERY_RATIO = QosCharacteristics.DELIVERY_RATIO_99;
    private static final int TEST_COUNT_EXPONENT = 5;

    @Before
    public void setUp() {
        assumeTrue(SdkLevel.isAtLeastV());
    }

    /**
     * Get a Builder with the mandatory fields set to the default test values.
     */
    private static QosCharacteristics.Builder getDefaultBuilder() {
        return new QosCharacteristics.Builder(
                TEST_MIN_SERVICE_INTERVAL_MICROS, TEST_MAX_SERVICE_INTERVAL_MICROS,
                TEST_MIN_DATA_RATE_KBPS, TEST_DELAY_BOUND_MICROS);
    }

    /**
     * Get a QosCharacteristics with all fields set to the default test values.
     */
    private static QosCharacteristics getDefaultQosCharacteristics() {
        return getDefaultBuilder()
                .setMaxMsduSizeOctets(TEST_MAX_MSDU_SIZE_OCTETS)
                .setServiceStartTimeInfo(
                        TEST_SERVICE_START_TIME_MICROS, TEST_SERVICE_START_TIME_LINK_ID)
                .setMeanDataRateKbps(TEST_MEAN_DATA_RATE_KBPS)
                .setBurstSizeOctets(TEST_BURST_SIZE_OCTETS)
                .setMsduLifetimeMillis(TEST_MSDU_LIFETIME_MILLIS)
                .setMsduDeliveryInfo(TEST_DELIVERY_RATIO, TEST_COUNT_EXPONENT)
                .build();
    }

    /**
     * Verify that all fields in the QosCharacteristics object contain the default test values.
     */
    private static void validateDefaultFields(QosCharacteristics qosCharacteristics) {
        assertEquals(TEST_MIN_SERVICE_INTERVAL_MICROS,
                qosCharacteristics.getMinServiceIntervalMicros());
        assertEquals(TEST_MAX_SERVICE_INTERVAL_MICROS,
                qosCharacteristics.getMaxServiceIntervalMicros());
        assertEquals(TEST_MIN_DATA_RATE_KBPS, qosCharacteristics.getMinDataRateKbps());
        assertEquals(TEST_DELAY_BOUND_MICROS, qosCharacteristics.getDelayBoundMicros());

        assertTrue(qosCharacteristics.containsOptionalField(QosCharacteristics.MAX_MSDU_SIZE));
        assertTrue(qosCharacteristics.containsOptionalField(QosCharacteristics.SERVICE_START_TIME));
        assertTrue(qosCharacteristics.containsOptionalField(QosCharacteristics.MEAN_DATA_RATE));
        assertTrue(qosCharacteristics.containsOptionalField(QosCharacteristics.BURST_SIZE));
        assertTrue(qosCharacteristics.containsOptionalField(QosCharacteristics.MSDU_LIFETIME));
        assertTrue(qosCharacteristics.containsOptionalField(QosCharacteristics.MSDU_DELIVERY_INFO));

        assertEquals(TEST_MAX_MSDU_SIZE_OCTETS, qosCharacteristics.getMaxMsduSizeOctets());
        assertEquals(TEST_SERVICE_START_TIME_MICROS,
                qosCharacteristics.getServiceStartTimeMicros());
        assertEquals(TEST_SERVICE_START_TIME_LINK_ID,
                qosCharacteristics.getServiceStartTimeLinkId());
        assertEquals(TEST_MEAN_DATA_RATE_KBPS, qosCharacteristics.getMeanDataRateKbps());
        assertEquals(TEST_BURST_SIZE_OCTETS, qosCharacteristics.getBurstSizeOctets());
        assertEquals(TEST_MSDU_LIFETIME_MILLIS, qosCharacteristics.getMsduLifetimeMillis());
        assertEquals(TEST_DELIVERY_RATIO, qosCharacteristics.getDeliveryRatio());
        assertEquals(TEST_COUNT_EXPONENT, qosCharacteristics.getCountExponent());
    }

    /**
     * Test that the builder works correctly when provided valid values.
     */
    @Test
    public void testBuilderValid() {
        QosCharacteristics qosCharacteristics = getDefaultQosCharacteristics();
        validateDefaultFields(qosCharacteristics);
    }

    /**
     * Test that an exception is thrown if any of the mandatory fields are assigned an
     * invalid value.
     */
    @Test
    public void testMandatoryFieldsInvalid() {
        // All mandatory fields must be positive.
        assertThrows(IllegalArgumentException.class, () ->
                new QosCharacteristics.Builder(
                        0, TEST_MAX_SERVICE_INTERVAL_MICROS,
                        TEST_MIN_DATA_RATE_KBPS, TEST_DELAY_BOUND_MICROS).build());
        assertThrows(IllegalArgumentException.class, () ->
                new QosCharacteristics.Builder(
                        TEST_MIN_SERVICE_INTERVAL_MICROS, 0,
                        TEST_MIN_DATA_RATE_KBPS, TEST_DELAY_BOUND_MICROS).build());
        assertThrows(IllegalArgumentException.class, () ->
                new QosCharacteristics.Builder(
                        TEST_MIN_SERVICE_INTERVAL_MICROS, TEST_MAX_SERVICE_INTERVAL_MICROS,
                        0, TEST_DELAY_BOUND_MICROS).build());
        assertThrows(IllegalArgumentException.class, () ->
                new QosCharacteristics.Builder(
                        TEST_MIN_SERVICE_INTERVAL_MICROS, TEST_MAX_SERVICE_INTERVAL_MICROS,
                        TEST_MIN_DATA_RATE_KBPS, 0).build());

        // Min service interval must be less than or equal to the max service interval.
        assertThrows(IllegalArgumentException.class, () ->
                new QosCharacteristics.Builder(
                        5000 /* minServiceInterval */, 3000 /* maxServiceInterval */,
                        TEST_MIN_DATA_RATE_KBPS, 0).build());
    }

    /**
     * Test that an exception is thrown if any optional values that expect a positive value
     * are assigned a zero value.
     */
    @Test
    public void testOptionalFieldsInvalid_zeroValue() {
        assertThrows(IllegalArgumentException.class, () ->
                getDefaultBuilder().setMaxMsduSizeOctets(0).build());
        assertThrows(IllegalArgumentException.class, () ->
                getDefaultBuilder().setMeanDataRateKbps(0).build());
        assertThrows(IllegalArgumentException.class, () ->
                getDefaultBuilder().setBurstSizeOctets(0).build());
        assertThrows(IllegalArgumentException.class, () ->
                getDefaultBuilder().setMsduLifetimeMillis(0).build());
    }

    /**
     * Test that an exception is thrown if any optional values that expect a <32-bit value are
     * assigned a value that exceeds the expected bit size.
     */
    @Test
    public void testOptionalFieldsInvalid_exceedUpperBound() {
        // Expects 16-bit value
        assertThrows(IllegalArgumentException.class, () ->
                getDefaultBuilder().setMaxMsduSizeOctets(0x1FFFF).build());

        // Expects 16-bit value
        assertThrows(IllegalArgumentException.class, () ->
                getDefaultBuilder().setMsduLifetimeMillis(0x1FFFF).build());

        // Expects 4-bit link ID
        assertThrows(IllegalArgumentException.class, () ->
                getDefaultBuilder()
                        .setServiceStartTimeInfo(TEST_SERVICE_START_TIME_MICROS, 0x1F).build());
    }

    /**
     * Test that an exception is thrown if any additional constraints on the optional fields
     * are broken.
     */
    @Test
    public void testOptionalFieldsInvalid_additionalConstraints() {
        // MSDU lifetime should be >= the delay bound
        int delayBoundUs = 30_000;  // 30 ms
        int msduLifetimeMs = 20;
        assertThrows(IllegalArgumentException.class, () ->
                new QosCharacteristics.Builder(
                        TEST_MIN_SERVICE_INTERVAL_MICROS, TEST_MAX_SERVICE_INTERVAL_MICROS,
                        TEST_MIN_DATA_RATE_KBPS, delayBoundUs)
                        .setMsduLifetimeMillis(msduLifetimeMs)
                        .build());

        // MSDU delivery ratio should be a valid enum
        assertThrows(IllegalArgumentException.class, () ->
                getDefaultBuilder()
                        .setMsduDeliveryInfo(DELIVERY_RATIO_95 - 1, TEST_COUNT_EXPONENT)
                        .build());
        assertThrows(IllegalArgumentException.class, () ->
                getDefaultBuilder()
                        .setMsduDeliveryInfo(DELIVERY_RATIO_99_9999 + 1, TEST_COUNT_EXPONENT)
                        .build());

        // MSDU count exponent should be between 0 and 15 (inclusive)
        assertThrows(IllegalArgumentException.class, () ->
                getDefaultBuilder()
                        .setMsduDeliveryInfo(TEST_DELIVERY_RATIO, -1)
                        .build());
        assertThrows(IllegalArgumentException.class, () ->
                getDefaultBuilder()
                        .setMsduDeliveryInfo(TEST_DELIVERY_RATIO, 16)
                        .build());
    }

    /**
     * Test that an exception is thrown if the caller attempts to get an optional field
     * that was not assigned a value.
     */
    @Test
    public void testOptionalFields_unsetException() {
        assertThrows(IllegalStateException.class, () ->
                getDefaultBuilder().build().getMaxMsduSizeOctets());
        assertThrows(IllegalStateException.class, () ->
                getDefaultBuilder().build().getServiceStartTimeMicros());
        assertThrows(IllegalStateException.class, () ->
                getDefaultBuilder().build().getServiceStartTimeLinkId());
        assertThrows(IllegalStateException.class, () ->
                getDefaultBuilder().build().getMeanDataRateKbps());
        assertThrows(IllegalStateException.class, () ->
                getDefaultBuilder().build().getBurstSizeOctets());
        assertThrows(IllegalStateException.class, () ->
                getDefaultBuilder().build().getMsduLifetimeMillis());
        assertThrows(IllegalStateException.class, () ->
                getDefaultBuilder().build().getDeliveryRatio());
        assertThrows(IllegalStateException.class, () ->
                getDefaultBuilder().build().getCountExponent());
    }

    /**
     * Tests that the parceling logic can properly read and write from a Parcel.
     */
    @Test
    public void testParcelReadWrite() {
        QosCharacteristics qosCharacteristics = getDefaultQosCharacteristics();
        Parcel parcel = Parcel.obtain();
        qosCharacteristics.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);    // Rewind data position back to the beginning for read.
        QosCharacteristics unparceledQosCharacteristics =
                QosCharacteristics.CREATOR.createFromParcel(parcel);
        validateDefaultFields(unparceledQosCharacteristics);
    }

    /**
     * Tests that the overridden equality and hashCode operators properly compare the same object.
     */
    @Test
    public void testSameObjectComparison() {
        QosCharacteristics qosCharacteristics1 = getDefaultQosCharacteristics();
        QosCharacteristics qosCharacteristics2 = getDefaultQosCharacteristics();
        assertTrue(qosCharacteristics1.equals(qosCharacteristics2));
        assertEquals(qosCharacteristics1.hashCode(), qosCharacteristics2.hashCode());
    }

    /**
     * Tests that the overridden equality and hashCode operators properly compare different objects.
     */
    @Test
    public void testDifferentObjectComparison() {
        QosCharacteristics qosCharacteristics1 = getDefaultQosCharacteristics();
        QosCharacteristics qosCharacteristics2 = getDefaultBuilder().build();
        assertFalse(qosCharacteristics1.equals(qosCharacteristics2));
        assertNotEquals(qosCharacteristics1.hashCode(), qosCharacteristics2.hashCode());
    }
}
