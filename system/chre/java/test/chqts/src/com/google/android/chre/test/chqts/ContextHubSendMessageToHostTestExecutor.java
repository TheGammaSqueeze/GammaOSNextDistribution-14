/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.chre.test.chqts;

import android.hardware.location.ContextHubInfo;
import android.hardware.location.ContextHubManager;
import android.hardware.location.NanoAppBinary;
import android.hardware.location.NanoAppMessage;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Check chreSendMessageToHost() works, along with an empty message from the
 * host to the nanoapp.
 *
 * Much of the logic for this test takes place within the nanoapp.  From
 * the Host side, we:
 * 1) Confirm we receive all of these message of various length
 * 2) Send one (edge-case) message after we've received everything
 * 3) Wait for the nanoapp to declare victory
 *
 * TODO(b/32114261): This code is a lot more complicated than it should
 * be.  Specifically, the standard workaround for this bug involves
 * putting more data within the 'message' to/from host/nanoapp.  But
 * since we're specifically testing that data, we can't use that
 * workaround.  When that workaround is gone, we can make this test
 * much simpler.
 *
 * Protocol:
 * Host:    SEND_MESSAGE_TO_HOST, no data
 * Nanoapp: <SMALL_DATA_LENGTH> bytes of <RAW_DATA_BYTE>
 * Nanoapp: <SMALL_DATA_LENGTH> bytes of <RAW_DATA_BYTE>
 * Nanoapp: <SMALL_DATA_LENGTH> bytes of <RAW_DATA_BYTE>
 * Nanoapp: <SMALL_DATA_LENGTH> bytes of <RAW_DATA_BYTE>
 * Nanoapp: 0 bytes
 * Nanoapp: CONTINUE, 4 bytes (little endian) with <MessageMaxSize>
 * Nanoapp: <MessageMaxSize> bytes of <RAW_DATA_BYTE>
 * Host:    0 bytes
 * Nanoapp: kSuccess
 */
public class ContextHubSendMessageToHostTestExecutor extends ContextHubGeneralTestExecutor {
    private static final int SMALL_DATA_LENGTH = 3;
    private static final byte RAW_DATA_BYTE = (byte) 0xFE;

    boolean mSentStartMessage = false;

    // This stage count is tracking the stage used within the nanoapp.  See
    // the nanoapp code for more details on this.  That will also make it
    // clearer why we skip one stage.
    private int mStageCount = 0;
    // We'll get this value from the nanoapp.
    private int mMessageMaxSize = 0;

    public ContextHubSendMessageToHostTestExecutor(ContextHubManager manager, ContextHubInfo info,
            NanoAppBinary binary) {
        super(manager, info, new GeneralTestNanoApp(binary,
                ContextHubTestConstants.TestNames.SEND_MESSAGE_TO_HOST));
    }

    @Override
    protected void handleMessageFromNanoApp(long nanoAppId,
            ContextHubTestConstants.MessageType type, byte[] data) {
        if (type != ContextHubTestConstants.MessageType.CONTINUE) {
            fail("Unexpected message type " + type);
            return;
        }

        switch (mStageCount) {
            case 0:  // fall-through
            case 1:  // fall-through
            case 2:  // fall-through
            case 3:
                checkSmallData(data);
                break;

            case 4:
                assertEquals("Expected zero length data", 0, data.length);
                break;

            case 5:
                readMessageMaxSize(data);
                // Note that we don't receive a message for stage 6, so we
                // skip it here.
                mStageCount++;
                break;

            case 7:
                checkLargeData(data);
                sendEmptyMessage(nanoAppId);
                break;

            case 6:  // fall-through
            default:
                fail("Unexpected stage " + mStageCount);
                break;
        }
        // We expect the next message to be at the next stage.
        mStageCount++;
    }

    private boolean isGeneralProtocolMessage(byte[] data) {
        if (data.length < 4) {
            return false;
        }
        for (int i = 0; i < 4; i++) {
            if (data[i] != RAW_DATA_BYTE) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected NanoAppMessage hackMessageToNanoApp(NanoAppMessage message) {
        if (!mSentStartMessage) {
            // Our start message, SEND_MESSAGE_TO_HOST, needs to
            // be sent via the standard workaround protocol.
            mSentStartMessage = true;
            return super.hackMessageToNanoApp(message);
        }
        // This should be our zero-length message, which we want to send raw.
        return NanoAppMessage.createMessageToNanoApp(
                message.getNanoAppId(), message.getMessageType(), message.getMessageBody());
    }

    @Override
    protected NanoAppMessage hackMessageFromNanoApp(NanoAppMessage message) {
        if (isGeneralProtocolMessage(message.getMessageBody())) {
            // This is going to be something like FAILURE or SUCCESS
            // which we want our standard infrastructure to handle.
            return super.hackMessageFromNanoApp(message);
        }
        // We'll use the CONTINUE message type for all messages from
        // the nanoapp, even though the nanoapp doesn't send that.
        return NanoAppMessage.createMessageFromNanoApp(
                message.getNanoAppId(), ContextHubTestConstants.MessageType.CONTINUE.asInt(),
                message.getMessageBody(), message.isBroadcastMessage());
    }

    private void checkSmallData(byte[] data) {
        assertEquals("Wrong small data length in stage " + mStageCount,
                SMALL_DATA_LENGTH, data.length);
        for (int i = 0; i < data.length; i++) {
            assertEquals("Bad data " + data[i] + " at index " + i
                            + " in stage " + mStageCount,
                    RAW_DATA_BYTE, data[i]);
        }
    }

    private void readMessageMaxSize(byte[] data) {
        mMessageMaxSize = ByteBuffer.wrap(data)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getInt();
        assertTrue("Given bad max message size: " + mMessageMaxSize,
                mMessageMaxSize > 0);
    }

    private void checkLargeData(byte[] data) {
        assertTrue("checkLargeData called without mMessageMaxSize",
                mMessageMaxSize != 0);
        assertEquals("checkLargeData has bad data length",
                mMessageMaxSize, data.length);
        for (int i = 0; i < data.length; i++) {
            assertEquals("Failed raw data check at index " + i,
                    RAW_DATA_BYTE, data[i]);
        }
    }

    private void sendEmptyMessage(long nanoAppId) {
        // Note that ContextHubManager/Client requires this to be non-null
        sendMessageToNanoAppOrFail(nanoAppId, ContextHubTestConstants.MessageType.CONTINUE.asInt(),
                new byte[0]);
    }
}
