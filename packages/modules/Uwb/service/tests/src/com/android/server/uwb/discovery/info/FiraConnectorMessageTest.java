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

import com.android.server.uwb.discovery.info.FiraConnectorMessage.InstructionCode;
import com.android.server.uwb.discovery.info.FiraConnectorMessage.MessageType;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

/** Unit test for {@link FiraConnectorMessage} */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class FiraConnectorMessageTest {
    private static final byte[] TEST_BYTES =
            new byte[] {
                /* message type */
                (byte)
                        (0x40
                                |
                                /* instruction code */
                                0x00),
                /* PAYLOAD */
                (byte) 0xF4,
                0x00,
                0x40
            };

    private static final MessageType MESSAGE_TYPE = MessageType.EVENT;
    private static final InstructionCode INSTRUCTION_CODE = InstructionCode.DATA_EXCHANGE;
    private static final byte[] PAYLOAD = new byte[] {(byte) 0xF4, 0x00, 0x40};

    @Test
    public void fromBytes_emptyData() {
        assertThat(FiraConnectorMessage.fromBytes(new byte[] {})).isNull();
    }

    @Test
    public void fromBytes_failedInvalidMessageType() {
        byte[] bytes = Arrays.copyOf(TEST_BYTES, TEST_BYTES.length);
        // Sets mesaage type to 3.
        bytes[0] = (byte) 0xC0;
        assertThrows(IllegalArgumentException.class, () -> FiraConnectorMessage.fromBytes(bytes));
    }

    @Test
    public void fromBytes_failedInvalidInstructionCode() {
        byte[] bytes = Arrays.copyOf(TEST_BYTES, TEST_BYTES.length);
        // Sets instruction code to 15.
        bytes[0] = (byte) 0x4F;
        assertThrows(IllegalArgumentException.class, () -> FiraConnectorMessage.fromBytes(bytes));
    }

    private void testHeaderFromBytes(
            byte header, MessageType expectedType, InstructionCode expectedCode) {
        byte[] bytes = Arrays.copyOf(TEST_BYTES, TEST_BYTES.length);
        bytes[0] = header;
        FiraConnectorMessage message = FiraConnectorMessage.fromBytes(bytes);
        assertThat(message).isNotNull();

        assertThat(message.messageType).isEqualTo(expectedType);
        assertThat(message.instructionCode).isEqualTo(expectedCode);
        assertThat(message.payload).isEqualTo(PAYLOAD);
    }

    @Test
    public void fromBytes_succeed() {
        testHeaderFromBytes((byte) 0x00, MessageType.COMMAND, InstructionCode.DATA_EXCHANGE);
        testHeaderFromBytes((byte) 0x01, MessageType.COMMAND, InstructionCode.ERROR_INDICATION);
        testHeaderFromBytes((byte) 0x41, MessageType.EVENT, InstructionCode.ERROR_INDICATION);
        testHeaderFromBytes(
                (byte) 0x81, MessageType.COMMAND_RESPOND, InstructionCode.ERROR_INDICATION);
    }

    @Test
    public void toBytes_succeed() {
        FiraConnectorMessage message =
                new FiraConnectorMessage(MESSAGE_TYPE, INSTRUCTION_CODE, PAYLOAD);
        assertThat(message).isNotNull();

        byte[] result = message.toBytes();
        assertThat(result.length).isEqualTo(TEST_BYTES.length);
        assertThat(result).isEqualTo(TEST_BYTES);
    }
}
