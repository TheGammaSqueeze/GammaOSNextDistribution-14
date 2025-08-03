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

package com.android.server.uwb.discovery.info;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.google.uwb.support.fira.FiraProtocolVersion;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

/** Unit test for {@link FiraConnectorCapabilities} */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class FiraConnectorCapabilitiesTest {
    private static final byte[] TEST_BYTES =
            new byte[] {
                /* FiRa Connector Protocol Version */
                0x05,
                (byte) 0xA1,
                /* Optimized Data Packet size */
                0x00,
                0x28,
                /* Maximum Message buffer size */
                0x04,
                0x18,
                /* Maximum number of concurrent fragmented Message session supported */
                0x0A,
                /* First SECID */
                (byte) 0xF1,
                0x11
            };
    private static final FiraProtocolVersion PROTOCOL_VERSION =
            new FiraProtocolVersion(/*major=*/ 5, /*minor=*/ 161);
    private static final int OPTIMIZED_DATA_PACKET_SIZE = 40;
    private static final int MAXIMUM_MESSAGE_BUFFER_SIZE = 1048;
    private static final int MAXIMUM_CONCURRENT_FRAGMENTED_MESSAGE_SESSION_SUPPORTED = 10;

    private static final boolean STATIC_INDICATION = true;
    private static final int SECID = 113;
    private static final SecureComponentInfo.SecureComponentType SC_TYPE =
            SecureComponentInfo.SecureComponentType.ESE_NONREMOVABLE;
    private static final SecureComponentInfo.SecureComponentProtocolType SC_PROTOCOL_TYPE =
            SecureComponentInfo.SecureComponentProtocolType.FIRA_OOB_ADMINISTRATIVE_PROTOCOL;

    @Test
    public void fromBytes_emptyData() {
        assertThat(FiraConnectorCapabilities.fromBytes(new byte[] {})).isNull();
    }

    @Test
    public void fromBytes_dataTooShort() {
        assertThat(FiraConnectorCapabilities.fromBytes(new byte[] {0x00, 0X01})).isNull();
    }

    @Test
    public void fromBytes_invalidProtocolVersion() {
        byte[] bytes = Arrays.copyOf(TEST_BYTES, TEST_BYTES.length);
        bytes[0] = 0x00;
        assertThrows(
                IllegalArgumentException.class, () -> FiraConnectorCapabilities.fromBytes(bytes));
    }

    @Test
    public void fromBytes_invalidOptimizedDataPacketSize() {
        byte[] bytes = Arrays.copyOf(TEST_BYTES, TEST_BYTES.length);
        bytes[2] = 0x00;
        bytes[3] = 0x00;
        assertThrows(
                IllegalArgumentException.class, () -> FiraConnectorCapabilities.fromBytes(bytes));
    }

    @Test
    public void fromBytes_invalidMaxMessageBufferSize() {
        byte[] bytes = Arrays.copyOf(TEST_BYTES, TEST_BYTES.length);
        bytes[4] = 0x01;
        bytes[5] = 0x03;
        assertThrows(
                IllegalArgumentException.class, () -> FiraConnectorCapabilities.fromBytes(bytes));
    }

    @Test
    public void fromBytes_invalidMaxConcurrentFragmentedMessageSessionSupported() {
        byte[] bytes = Arrays.copyOf(TEST_BYTES, TEST_BYTES.length);
        bytes[6] = 0x00;
        assertThrows(
                IllegalArgumentException.class, () -> FiraConnectorCapabilities.fromBytes(bytes));
    }

    @Test
    public void fromBytes_invalidSecureComponent() {
        byte[] bytes = Arrays.copyOf(TEST_BYTES, TEST_BYTES.length);
        // Invalid Secure Component Protocol Type.
        bytes[7] = 0x02;
        bytes[8] = (byte) 0x014;
        assertThrows(
                IllegalArgumentException.class, () -> FiraConnectorCapabilities.fromBytes(bytes));
    }

    @Test
    public void fromBytes_succeed() {
        FiraConnectorCapabilities capabilites = FiraConnectorCapabilities.fromBytes(TEST_BYTES);
        assertThat(capabilites).isNotNull();

        assertThat(capabilites.protocolVersion).isEqualTo(PROTOCOL_VERSION);
        assertThat(capabilites.optimizedDataPacketSize).isEqualTo(OPTIMIZED_DATA_PACKET_SIZE);
        assertThat(capabilites.maxMessageBufferSize).isEqualTo(MAXIMUM_MESSAGE_BUFFER_SIZE);
        assertThat(capabilites.maxConcurrentFragmentedMessageSessionSupported)
                .isEqualTo(MAXIMUM_CONCURRENT_FRAGMENTED_MESSAGE_SESSION_SUPPORTED);
        assertThat(capabilites.secureComponentInfos.size()).isEqualTo(1);

        SecureComponentInfo info = capabilites.secureComponentInfos.get(0);
        assertThat(info.staticIndication).isEqualTo(STATIC_INDICATION);
        assertThat(info.secid).isEqualTo(SECID);
        assertThat(info.secureComponentType).isEqualTo(SC_TYPE);
        assertThat(info.secureComponentProtocolType).isEqualTo(SC_PROTOCOL_TYPE);
    }

    @Test
    public void toBytes_succeed() {
        FiraConnectorCapabilities capabilites =
                new FiraConnectorCapabilities.Builder()
                        .setProtocolVersion(PROTOCOL_VERSION)
                        .setOptimizedDataPacketSize(OPTIMIZED_DATA_PACKET_SIZE)
                        .setMaxMessageBufferSize(MAXIMUM_MESSAGE_BUFFER_SIZE)
                        .setMaxConcurrentFragmentedMessageSessionSupported(
                                MAXIMUM_CONCURRENT_FRAGMENTED_MESSAGE_SESSION_SUPPORTED)
                        .addSecureComponentInfo(
                                new SecureComponentInfo(
                                        STATIC_INDICATION, SECID, SC_TYPE, SC_PROTOCOL_TYPE))
                        .build();

        assertThat(capabilites).isNotNull();

        byte[] result = capabilites.toBytes();
        assertThat(result.length).isEqualTo(TEST_BYTES.length);
        assertThat(result).isEqualTo(TEST_BYTES);
    }
}
