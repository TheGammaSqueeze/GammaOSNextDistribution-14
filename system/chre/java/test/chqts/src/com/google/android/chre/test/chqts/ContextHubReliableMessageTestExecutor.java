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

package com.google.android.chre.test.chqts;

import static com.google.common.truth.Truth.assertWithMessage;

import android.hardware.location.ContextHubClient;
import android.hardware.location.ContextHubClientCallback;
import android.hardware.location.ContextHubInfo;
import android.hardware.location.ContextHubManager;
import android.hardware.location.ContextHubTransaction;
import android.hardware.location.NanoAppBinary;
import android.hardware.location.NanoAppMessage;
import android.util.Log;

import com.google.android.chre.nanoapp.proto.ChreReliableMessageTest;
import com.google.android.chre.nanoapp.proto.ChreTestCommon;
import com.google.android.utils.chre.ContextHubServiceTestHelper;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tests sending reliable messages across the host and a nanoapp.
 *
 * <p>To use this executor, a test should run {@link #init()} as part of the set-up annotated by
 * {@code @Before} and {@link #deinit()} as part of tearing down annotated by {@code @After}.
 */
public class ContextHubReliableMessageTestExecutor {
    /**
     * The default message size to send.
     */
    public static final int DEFAULT_MESSAGE_SIZE = 1000;

    /**
     * The maximum message size when reliable messaging is supported.
     */
    public static final int MAX_MESSAGE_SIZE = 32000;

    /**
     * The number of messages to send during a multiple message test.
     */
    public static final int NUM_MESSAGES_TO_SEND = 5;

    private static final String TAG = "ContextHubReliableMessageExecutor";
    private final NanoAppBinary mNanoAppBinary;
    private final ContextHubServiceTestHelper mTestHelper;
    private final ContextHubInfo mContextHubInfo;

    public ContextHubReliableMessageTestExecutor(ContextHubManager contextHubManager,
            ContextHubInfo contextHubInfo, NanoAppBinary nanoAppBinary) {
        mContextHubInfo = contextHubInfo;
        mNanoAppBinary = nanoAppBinary;
        mTestHelper = new ContextHubServiceTestHelper(contextHubInfo, contextHubManager);
    }

    /** Loads the nanopp */
    public void init() throws InterruptedException, TimeoutException {
        mTestHelper.init();
        mTestHelper.loadNanoAppAssertSuccess(mNanoAppBinary);
    }

    /** Unloads the nanoapp */
    public void deinit() {
        mTestHelper.unloadNanoAppAssertSuccess(mNanoAppBinary.getNanoAppId());
        mTestHelper.deinit();
    }

    /**
     * Makes sure that the maximum message size for reliable messages is at least MAX_MESSAGE_SIZE
     * bytes.
     */
    public void maximumMessageSizeTest() {
        assertWithMessage("The maximum message size should be at least " + MAX_MESSAGE_SIZE)
                .that(mContextHubInfo.getMaxPacketLengthBytes())
                .isAtLeast(MAX_MESSAGE_SIZE);
    }

    /**
     * Test the nanoapp sending messages to the host.
     *
     * @param numMessages Number of messages to send.
     * @param messageSize Size of the messages.
     */
    public void messageToHostTest(int numMessages, int messageSize) {
        AtomicBoolean testSuccess = new AtomicBoolean(false);
        AtomicReference<String> testMessage = new AtomicReference<>("");
        CountDownLatch testLatch = new CountDownLatch(1);
        ContextHubClient client;

        ContextHubClientCallback callback = new ContextHubClientCallback() {
            @Override
            public void onMessageFromNanoApp(ContextHubClient client, NanoAppMessage message) {
                if (message.getNanoAppId() == mNanoAppBinary.getNanoAppId()) {
                    switch (message.getMessageType()) {
                        case ChreReliableMessageTest.MessageType.TEST_RESULT_VALUE:
                            try {
                                ChreTestCommon.TestResult result =
                                        ChreTestCommon.TestResult.parseFrom(
                                                message.getMessageBody());
                                testSuccess.set(
                                        result.getCode() == ChreTestCommon.TestResult.Code.PASSED);
                                testMessage.set(result.getErrorMessage().toString());
                                testLatch.countDown();
                            } catch (InvalidProtocolBufferException e) {
                                testSuccess.set(false);
                                testMessage.set(e.getMessage());
                                testLatch.countDown();
                            }
                            break;
                        case ChreReliableMessageTest.MessageType.HOST_ECHO_MESSAGE_VALUE:
                            NanoAppMessage echo = NanoAppMessage.createMessageToNanoApp(
                                    message.getNanoAppId(), message.getMessageType(),
                                    message.getMessageBody());

                            client.sendReliableMessageToNanoApp(echo);
                            break;
                    }
                }
            }
        };

        client = mTestHelper.createClient(callback);

        ChreReliableMessageTest.SendMessagesCommand command =
                ChreReliableMessageTest.SendMessagesCommand.newBuilder().setNumMessages(
                        numMessages).setMessageSize(messageSize).build();

        NanoAppMessage startMsg = NanoAppMessage.createMessageToNanoApp(
                mNanoAppBinary.getNanoAppId(),
                ChreReliableMessageTest.MessageType.SEND_MESSAGES_VALUE,
                command.toByteArray());

        assertWithMessage("Failed to send the message")
                .that(client.sendMessageToNanoApp(startMsg))
                .isEqualTo(ContextHubTransaction.RESULT_SUCCESS);

        try {
            assertWithMessage("Timeout")
                    .that(testLatch.await(5, TimeUnit.SECONDS))
                    .isTrue();
            assertWithMessage(testMessage.get())
                    .that(testSuccess.get())
                    .isTrue();
        } catch (InterruptedException e) {
            assertWithMessage(e.toString()).fail();
        }
    }

    /**
     * Test the host sending messages to the nanoapp.
     *
     * @param numMessages Number of messages to send.
     * @param messageSize Size of the messages.
     */
    public void messageToNanoappTest(int numMessages, int messageSize) {
        Log.i(TAG, "Sending %d messages of size %d".formatted(numMessages, messageSize));

        CountDownLatch messageLatch = new CountDownLatch(numMessages);
        CountDownLatch statusLatch = new CountDownLatch(numMessages);

        ContextHubClientCallback callback = new ContextHubClientCallback() {
            @Override
            public void onMessageFromNanoApp(ContextHubClient client, NanoAppMessage message) {
                if (message.getNanoAppId() == mNanoAppBinary.getNanoAppId()) {
                    if (message.getMessageType()
                            == ChreReliableMessageTest.MessageType.NANOAPP_ECHO_MESSAGE_VALUE) {
                        if (verifyMessagePayload(message.getMessageBody(), messageSize)) {
                            messageLatch.countDown();
                        } else {
                            Log.e(TAG, "Unexpected message from nanoapp");
                        }
                    }
                }
            }
        };

        ContextHubClient client = mTestHelper.createClient(callback);

        NanoAppMessage echoMsg = NanoAppMessage.createMessageToNanoApp(
                mNanoAppBinary.getNanoAppId(),
                ChreReliableMessageTest.MessageType.NANOAPP_ECHO_MESSAGE_VALUE,
                createMessagePayload(messageSize));

        ContextHubTransaction.OnCompleteListener<Void> completeListener =
                (ContextHubTransaction<Void> transaction,
                        ContextHubTransaction.Response<Void> response) -> {
                    if (response.getResult() == ContextHubTransaction.RESULT_SUCCESS) {
                        statusLatch.countDown();
                    } else {
                        Log.e(TAG, "Transaction completed with status %d".formatted(
                                response.getResult()));
                    }
                };

        for (int i = 0; i < numMessages; ++i) {
            ContextHubTransaction<Void> transaction = client.sendReliableMessageToNanoApp(echoMsg);
            transaction.setOnCompleteListener(completeListener);
        }

        try {
            assertWithMessage("Timeout waiting for status")
                    .that(statusLatch.await(5, TimeUnit.SECONDS))
                    .isTrue();
            assertWithMessage("Timeout waiting for messages")
                    .that(messageLatch.await(5, TimeUnit.SECONDS))
                    .isTrue();
        } catch (InterruptedException e) {
            assertWithMessage(e.toString()).fail();
        }

    }

    private byte[] createMessagePayload(int messageSize) {
        byte[] bytes = new byte[messageSize];
        for (int i = 0; i < messageSize; ++i) {
            bytes[i] = (byte) i;
        }
        return bytes;
    }

    private boolean verifyMessagePayload(byte[] bytes, int expectedSize) {
        if (bytes.length != expectedSize) {
            Log.e(TAG, "Message length does not match expected size: length: "
                    + bytes.length + ", expected length: " + expectedSize);
            return false;
        }
        for (int i = 0; i < expectedSize; ++i) {
            if (bytes[i] != (byte) i) {
                Log.e(TAG, "Byte mismatch at index: " + i + "byte: "
                        + String.format("%02x", bytes[i]) + ", expected byte: "
                        + String.format("%02x", i));
                return false;
            }
        }
        return true;
    }

}
