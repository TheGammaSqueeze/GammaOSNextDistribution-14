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

package android.net.wifi;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.net.DscpPolicy;
import android.os.Parcel;

import com.android.modules.utils.build.SdkLevel;

import org.junit.Before;
import org.junit.Test;

import java.net.InetAddress;

/**
 * Unit tests for {@link QosPolicyParams}.
 */
public class QosPolicyParamsTest {
    private static final int TEST_POLICY_ID = 127;
    private static final int TEST_DIRECTION = QosPolicyParams.DIRECTION_DOWNLINK;
    private static final int TEST_IP_VERSION = QosPolicyParams.IP_VERSION_4;
    private static final int TEST_DSCP = 7;
    private static final int TEST_USER_PRIORITY = QosPolicyParams.USER_PRIORITY_VIDEO_LOW;
    private static final int TEST_SOURCE_PORT = 15;
    private static final int TEST_PROTOCOL = QosPolicyParams.PROTOCOL_TCP;
    private static final int TEST_DESTINATION_PORT = 17;
    private static final String TEST_SOURCE_ADDRESS = "127.0.0.1";
    private static final String TEST_DESTINATION_ADDRESS = "127.0.0.2";

    @Before
    public void setUp() {
        assumeTrue(SdkLevel.isAtLeastU());
    }

    private InetAddress getInetAddress(String addr) {
        try {
            return InetAddress.getByName(addr);
        } catch (Exception e) {
            // Should not occur.
            return null;
        }
    }

    /**
     * Creates a QosCharacteristics object with all fields assigned to a default value.
     */
    private static QosCharacteristics createTestQosCharacteristics() {
        int minServiceIntervalMicros = 2000;
        int maxServiceIntervalMicros = 5000;
        int minDataRateKbps = 500;
        int delayBoundMicros = 200;
        return new QosCharacteristics.Builder(
                minServiceIntervalMicros, maxServiceIntervalMicros,
                minDataRateKbps, delayBoundMicros).build();
    }

    /**
     * Creates a QosPolicyParams object with all fields assigned to a default test value.
     */
    private QosPolicyParams createTestQosPolicyParams() {
        QosPolicyParams.Builder builder =
                new QosPolicyParams.Builder(TEST_POLICY_ID, TEST_DIRECTION)
                        .setUserPriority(TEST_USER_PRIORITY)
                        .setIpVersion(TEST_IP_VERSION)
                        .setDscp(TEST_DSCP)
                        .setSourcePort(TEST_SOURCE_PORT)
                        .setProtocol(TEST_PROTOCOL)
                        .setDestinationPort(TEST_DESTINATION_PORT)
                        .setSourceAddress(getInetAddress(TEST_SOURCE_ADDRESS))
                        .setDestinationAddress(getInetAddress(TEST_DESTINATION_ADDRESS));
        if (SdkLevel.isAtLeastV()) {
            builder.setQosCharacteristics(createTestQosCharacteristics());
        }
        return builder.build();
    }

    /**
     * Check that all fields in the provided QosPolicyParams object match the default test values.
     */
    private void verifyTestQosPolicyParams(QosPolicyParams params) {
        assertEquals(TEST_POLICY_ID, params.getPolicyId());
        assertEquals(TEST_DIRECTION, params.getDirection());
        assertEquals(TEST_IP_VERSION, params.getIpVersion());
        assertEquals(TEST_USER_PRIORITY, params.getUserPriority());
        assertEquals(TEST_DSCP, params.getDscp());
        assertEquals(TEST_SOURCE_PORT, params.getSourcePort());
        assertEquals(TEST_PROTOCOL, params.getProtocol());
        assertEquals(TEST_DESTINATION_PORT, params.getDestinationPort());
        assertTrue(getInetAddress(TEST_SOURCE_ADDRESS).equals(params.getSourceAddress()));
        assertTrue(getInetAddress(TEST_DESTINATION_ADDRESS).equals(params.getDestinationAddress()));
        if (SdkLevel.isAtLeastV()) {
            QosCharacteristics testQosCharacteristics = createTestQosCharacteristics();
            assertEquals(testQosCharacteristics, params.getQosCharacteristics());
        }
    }

    /**
     * Tests that the default parameters are set if they are not assigned by the user.
     */
    @Test
    public void testDefaultParamsSet() {
        QosPolicyParams params =
                new QosPolicyParams.Builder(TEST_POLICY_ID, QosPolicyParams.DIRECTION_DOWNLINK)
                        .setUserPriority(TEST_USER_PRIORITY)
                        .setIpVersion(TEST_IP_VERSION)
                        .build();
        assertEquals(QosPolicyParams.DSCP_ANY, params.getDscp());
        assertEquals(QosPolicyParams.PROTOCOL_ANY, params.getProtocol());
        assertEquals(DscpPolicy.SOURCE_PORT_ANY, params.getSourcePort());
    }

    /**
     * Test that if we set all the parameters in the Builder, the resulting QosPolicyParams
     * object contains the expected values.
     */
    @Test
    public void testSetAllParams() {
        QosPolicyParams params = createTestQosPolicyParams();
        verifyTestQosPolicyParams(params);
    }

    /**
     * Tests that the Builder throws an exception if an invalid parameter is set.
     */
    @Test
    public void testBuilderWithInvalidParam() {
        assertThrows(IllegalArgumentException.class, () ->
                new QosPolicyParams.Builder(TEST_POLICY_ID, QosPolicyParams.DIRECTION_DOWNLINK)
                        .setUserPriority(TEST_USER_PRIORITY)
                        .setDscp(120)   // DSCP should be <= 63
                        .build());
    }

    /**
     * Tests that the Builder throws an exception if a direction-specific error is found.
     */
    @Test
    public void testBuilderWithDirectionSpecificError() {
        assertThrows(IllegalArgumentException.class, () ->
                // Policies for downlink are required to have a User Priority and IP Version.
                new QosPolicyParams.Builder(TEST_POLICY_ID, QosPolicyParams.DIRECTION_DOWNLINK)
                        .build());
        assertThrows(IllegalArgumentException.class, () ->
                // Policies for uplink are required to have QoS characteristics.
                new QosPolicyParams.Builder(TEST_POLICY_ID, QosPolicyParams.DIRECTION_UPLINK)
                        .build());
    }

    /**
     * Tests that the parceling logic can properly read and write from a Parcel.
     */
    @Test
    public void testParcelReadWrite() {
        QosPolicyParams params = createTestQosPolicyParams();
        Parcel parcel = Parcel.obtain();
        params.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);    // Rewind data position back to the beginning for read.
        QosPolicyParams unparceledParams = QosPolicyParams.CREATOR.createFromParcel(parcel);
        verifyTestQosPolicyParams(unparceledParams);
    }

    /**
     * Tests that the overridden equality and hashCode operators properly compare two objects.
     */
    @Test
    public void testObjectComparison() {
        QosPolicyParams params1 = createTestQosPolicyParams();
        QosPolicyParams params2 = createTestQosPolicyParams();
        assertTrue(params1.equals(params2));
        assertEquals(params1.hashCode(), params2.hashCode());
    }
}
