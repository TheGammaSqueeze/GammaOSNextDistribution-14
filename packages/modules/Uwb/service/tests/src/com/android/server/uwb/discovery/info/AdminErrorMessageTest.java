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

import com.android.server.uwb.discovery.info.AdminErrorMessage.ErrorType;
import com.android.server.uwb.discovery.info.FiraConnectorMessage.InstructionCode;
import com.android.server.uwb.discovery.info.FiraConnectorMessage.MessageType;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

/** Unit test for {@link AdminErrorMessage} */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class AdminErrorMessageTest {
    private static final byte[] INVALID_PAYLOAD = new byte[] {(byte) 0xF4, 0x00, 0x40};
    private static final byte[] PAYLOAD =
            new byte[] {
                // Error number of DATA_PACKET_LENGTH_OVERFLOW in bytes.
                (byte) 0x80, 0x01
            };
    private static final FiraConnectorMessage CONNECTOR_MESSAGE =
            new FiraConnectorMessage(
                    MessageType.COMMAND_RESPOND, InstructionCode.ERROR_INDICATION, PAYLOAD);
    private static final AdminErrorMessage MESSAGE =
            new AdminErrorMessage(ErrorType.DATA_PACKET_LENGTH_OVERFLOW);
    private static final byte[] BYTES =
            new byte[] {
                /* message type */
                (byte)
                        (0x80
                                |
                                /* instruction code */
                                0x01),
                /* PAYLOAD */
                (byte) 0x80,
                0x01
            };

    @Test
    public void testIsAdminErrorMessage() {
        assertThat(
                        AdminErrorMessage.isAdminErrorMessage(
                                new FiraConnectorMessage(
                                        MessageType.EVENT,
                                        InstructionCode.DATA_EXCHANGE,
                                        INVALID_PAYLOAD)))
                .isFalse();
        assertThat(
                        AdminErrorMessage.isAdminErrorMessage(
                                new FiraConnectorMessage(
                                        MessageType.COMMAND,
                                        InstructionCode.DATA_EXCHANGE,
                                        INVALID_PAYLOAD)))
                .isFalse();
        assertThat(
                        AdminErrorMessage.isAdminErrorMessage(
                                new FiraConnectorMessage(
                                        MessageType.EVENT,
                                        InstructionCode.ERROR_INDICATION,
                                        INVALID_PAYLOAD)))
                .isFalse();
        assertThat(
                        AdminErrorMessage.isAdminErrorMessage(
                                new FiraConnectorMessage(
                                        MessageType.COMMAND,
                                        InstructionCode.ERROR_INDICATION,
                                        INVALID_PAYLOAD)))
                .isFalse();
        assertThat(
                        AdminErrorMessage.isAdminErrorMessage(
                                new FiraConnectorMessage(
                                        MessageType.COMMAND_RESPOND,
                                        InstructionCode.DATA_EXCHANGE,
                                        INVALID_PAYLOAD)))
                .isFalse();
        assertThat(
                        AdminErrorMessage.isAdminErrorMessage(
                                new FiraConnectorMessage(
                                        MessageType.COMMAND_RESPOND,
                                        InstructionCode.ERROR_INDICATION,
                                        INVALID_PAYLOAD)))
                .isFalse();
        assertThat(
                        AdminErrorMessage.isAdminErrorMessage(
                                new FiraConnectorMessage(
                                        MessageType.COMMAND_RESPOND,
                                        InstructionCode.ERROR_INDICATION,
                                        null)))
                .isFalse();
        assertThat(AdminErrorMessage.isAdminErrorMessage(CONNECTOR_MESSAGE)).isTrue();
    }

    @Test
    public void testConstructor_nullErrorType() {
        assertThrows(IllegalArgumentException.class, () -> new AdminErrorMessage(null));
    }

    private void testConstructwithBytes(ErrorType errorType, byte lastErrorByte) {
        byte[] bytes = Arrays.copyOf(BYTES, BYTES.length);
        byte[] payload = Arrays.copyOf(PAYLOAD, PAYLOAD.length);
        bytes[2] = lastErrorByte;
        payload[1] = lastErrorByte;
        AdminErrorMessage message = new AdminErrorMessage(errorType);
        assertThat(message).isNotNull();
        assertThat(message.messageType).isEqualTo(MessageType.COMMAND_RESPOND);
        assertThat(message.instructionCode).isEqualTo(InstructionCode.ERROR_INDICATION);
        assertThat(message.errorType).isEqualTo(errorType);
        assertThat(message.payload).isEqualTo(payload);
    }

    @Test
    public void testConstructor() {
        testConstructwithBytes(ErrorType.DATA_PACKET_LENGTH_OVERFLOW, (byte) 0x01);
        testConstructwithBytes(ErrorType.MESSAGE_LENGTH_OVERFLOW, (byte) 0x02);
        testConstructwithBytes(
                ErrorType.TOO_MANY_CONCURRENT_FRAGMENTED_MESSAGE_SESSIONS, (byte) 0x03);
        testConstructwithBytes(ErrorType.SECID_INVALID, (byte) 0x04);
        testConstructwithBytes(ErrorType.SECID_INVALID_FOR_RESPONSE, (byte) 0x05);
        testConstructwithBytes(ErrorType.SECID_BUSY, (byte) 0x06);
        testConstructwithBytes(ErrorType.SECID_PROTOCOL_ERROR, (byte) 0x07);
        testConstructwithBytes(ErrorType.SECID_INTERNAL_ERROR, (byte) 0x08);
    }

    @Test
    public void testConvertToAdminErrorMessage_nullMessage() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AdminErrorMessage.convertToAdminErrorMessage(null));
    }

    @Test
    public void testConvertToAdminErrorMessage_invalidMessage() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        AdminErrorMessage.convertToAdminErrorMessage(
                                new FiraConnectorMessage(
                                        MessageType.EVENT,
                                        InstructionCode.DATA_EXCHANGE,
                                        INVALID_PAYLOAD)));
    }

    @Test
    public void testConvertToAdminErrorMessage() {
        AdminErrorMessage message = AdminErrorMessage.convertToAdminErrorMessage(CONNECTOR_MESSAGE);
        assertThat(message).isNotNull();
        assertThat(message.toBytes()).isEqualTo(MESSAGE.toBytes());
        assertThat(message.toString()).isEqualTo(MESSAGE.toString());
    }

    @Test
    public void testToBytes() {
        assertThat(MESSAGE.toBytes()).isEqualTo(BYTES);
    }
}
