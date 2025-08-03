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

import com.android.server.uwb.discovery.info.AdminEventMessage.EventType;
import com.android.server.uwb.discovery.info.FiraConnectorMessage.InstructionCode;
import com.android.server.uwb.discovery.info.FiraConnectorMessage.MessageType;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link AdminEventMessage} */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class AdminEventMessageTest {
    private static final byte[] INVALID_PAYLOAD = new byte[] {(byte) 0xF4, 0x00, 0x40};
    private static final byte[] PAYLOAD =
            new byte[] {
                // Error number of DATA_PACKET_LENGTH_OVERFLOW in bytes.
                0x00, 0x01,
            };
    private static final FiraConnectorMessage CONNECTOR_MESSAGE =
            new FiraConnectorMessage(MessageType.EVENT, InstructionCode.DATA_EXCHANGE, PAYLOAD);
    private static final AdminEventMessage MESSAGE =
            new AdminEventMessage(EventType.CAPABILITIES_CHANGED, new byte[] {});
    private static final byte[] BYTES =
            new byte[] {
                /* message type */
                (byte)
                        (0x40
                                |
                                /* instruction code */
                                0x00),
                /* PAYLOAD */
                0x00,
                0x01,
            };

    @Test
    public void testIsAdminEventMessage() {
        assertThat(
                        AdminEventMessage.isAdminEventMessage(
                                new FiraConnectorMessage(
                                        MessageType.COMMAND,
                                        InstructionCode.DATA_EXCHANGE,
                                        INVALID_PAYLOAD)))
                .isFalse();
        assertThat(
                        AdminEventMessage.isAdminEventMessage(
                                new FiraConnectorMessage(
                                        MessageType.EVENT,
                                        InstructionCode.ERROR_INDICATION,
                                        INVALID_PAYLOAD)))
                .isFalse();
        assertThat(
                        AdminEventMessage.isAdminEventMessage(
                                new FiraConnectorMessage(
                                        MessageType.COMMAND,
                                        InstructionCode.ERROR_INDICATION,
                                        INVALID_PAYLOAD)))
                .isFalse();
        assertThat(
                        AdminEventMessage.isAdminEventMessage(
                                new FiraConnectorMessage(
                                        MessageType.COMMAND_RESPOND,
                                        InstructionCode.DATA_EXCHANGE,
                                        INVALID_PAYLOAD)))
                .isFalse();
        assertThat(
                        AdminEventMessage.isAdminEventMessage(
                                new FiraConnectorMessage(
                                        MessageType.COMMAND_RESPOND,
                                        InstructionCode.ERROR_INDICATION,
                                        INVALID_PAYLOAD)))
                .isFalse();
        assertThat(
                        AdminEventMessage.isAdminEventMessage(
                                new FiraConnectorMessage(
                                        MessageType.EVENT,
                                        InstructionCode.DATA_EXCHANGE,
                                        INVALID_PAYLOAD)))
                .isFalse();
        assertThat(
                        AdminEventMessage.isAdminEventMessage(
                                new FiraConnectorMessage(
                                        MessageType.EVENT, InstructionCode.DATA_EXCHANGE, null)))
                .isFalse();
        assertThat(AdminEventMessage.isAdminEventMessage(CONNECTOR_MESSAGE)).isTrue();
    }

    @Test
    public void testConstructor_nullEventType() {
        assertThrows(IllegalArgumentException.class, () -> new AdminEventMessage(null, null));
    }

    @Test
    public void testConstructor() {
        AdminEventMessage message =
                new AdminEventMessage(EventType.CAPABILITIES_CHANGED, new byte[] {});
        assertThat(message).isNotNull();
        assertThat(message.messageType).isEqualTo(MessageType.EVENT);
        assertThat(message.instructionCode).isEqualTo(InstructionCode.DATA_EXCHANGE);
        assertThat(message.eventType).isEqualTo(EventType.CAPABILITIES_CHANGED);
        assertThat(message.payload).isEqualTo(PAYLOAD);
    }

    @Test
    public void testConvertToAdminEventMessage_nullMessage() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AdminEventMessage.convertToAdminEventMessage(null));
    }

    @Test
    public void testConvertToAdminEventMessage_invalidMessage() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        AdminEventMessage.convertToAdminEventMessage(
                                new FiraConnectorMessage(
                                        MessageType.EVENT,
                                        InstructionCode.DATA_EXCHANGE,
                                        INVALID_PAYLOAD)));
    }

    @Test
    public void testConvertToAdminEventMessage() {
        AdminEventMessage message = AdminEventMessage.convertToAdminEventMessage(CONNECTOR_MESSAGE);
        assertThat(message).isNotNull();
        assertThat(message.toBytes()).isEqualTo(MESSAGE.toBytes());
        assertThat(message.toString()).isEqualTo(MESSAGE.toString());
    }

    @Test
    public void testToBytes() {
        assertThat(MESSAGE.toBytes()).isEqualTo(BYTES);
    }
}
