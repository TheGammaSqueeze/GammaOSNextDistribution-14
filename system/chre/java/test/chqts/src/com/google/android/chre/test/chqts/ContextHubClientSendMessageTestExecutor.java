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

package com.google.android.chre.test.chqts;

import static com.google.android.utils.chre.ChreTestUtil.assertLatchCountedDown;
import static com.google.android.utils.chre.ContextHubServiceTestHelper.TIMEOUT_SECONDS_MESSAGE;
import static com.google.common.truth.Truth.assertWithMessage;

import android.hardware.location.ContextHubClient;
import android.hardware.location.ContextHubClientCallback;
import android.hardware.location.ContextHubInfo;
import android.hardware.location.ContextHubManager;
import android.hardware.location.ContextHubTransaction;
import android.hardware.location.NanoAppBinary;
import android.hardware.location.NanoAppMessage;
import android.os.SystemClock;
import android.util.Log;

import com.google.android.utils.chre.ContextHubClientMessageValidator;
import com.google.android.utils.chre.ContextHubServiceTestHelper;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * An executor running tests by sending messages to the nanoapp in different ways.
 *
 * <p>To use this executor, a test should run {@link #init()} as part of the set-up annotated by
 * {@code @Before} and {@link #deinit()} as part of tearing down annotated by {@code @After}.
 */
public class ContextHubClientSendMessageTestExecutor {
    private static final String TAG = "ContextHubClientSendMessageExecutor";
    private static final int MESSAGE_TYPE =
            ContextHubTestConstants.MessageType.SERVICE_MESSAGE.asInt();
    private final Random mRandom = new Random();
    private final NanoAppBinary mNanoAppBinary;
    private final ContextHubInfo mContextHubInfo;
    private final ContextHubServiceTestHelper mTestHelper;

    public ContextHubClientSendMessageTestExecutor(
            ContextHubManager contextHubManager,
            ContextHubInfo contextHubInfo,
            NanoAppBinary nanoAppBinary) {
        mNanoAppBinary = nanoAppBinary;
        mContextHubInfo = contextHubInfo;
        mTestHelper = new ContextHubServiceTestHelper(contextHubInfo, contextHubManager);
    }

    public void init() throws InterruptedException, TimeoutException {
        mTestHelper.init();
        mTestHelper.loadNanoAppAssertSuccess(mNanoAppBinary);
    }

    public void deinit() {
        mTestHelper.unloadNanoAppAssertSuccess(mNanoAppBinary.getNanoAppId());
        mTestHelper.deinit();
    }

    /** Generates a {@link NanoAppMessage} with randomized message body. */
    private NanoAppMessage createNanoAppMessage() {
        int maxMessageLength = mContextHubInfo.getMaxPacketLengthBytes();
        byte[] payload = new byte[maxMessageLength];
        mRandom.nextBytes(payload);
        Log.d(TAG, "Created a nanoapp message: " + Base64.getEncoder().encodeToString(payload));
        return NanoAppMessage.createMessageToNanoApp(
                mNanoAppBinary.getNanoAppId(), MESSAGE_TYPE, payload);
    }

    /** Generates a list of {@link NanoAppMessage} with randomized message bodies. */
    private List<NanoAppMessage> createNanoAppMessages(int numOfMessages) {
        List<NanoAppMessage> result = new ArrayList<>(numOfMessages);
        for (int i = 0; i < numOfMessages; i++) {
            result.add(createNanoAppMessage());
        }
        return result;
    }

    /**
     * Registers a client with a callback that verifies the incoming messages from nanoapps.
     *
     * @param latch the latch counted down when all the expected messages are received
     * @param messagesToTheApp list of messages that the nanoapp is expected to receive
     */
    private ContextHubClient registerMessageClient(
            CountDownLatch latch, List<NanoAppMessage> messagesToTheApp) {
        ContextHubClientMessageValidator validator =
                new ContextHubClientMessageValidator(
                        messagesToTheApp, /* onComplete= */ latch::countDown);
        ContextHubClientCallback callback =
                new ContextHubClientCallback() {
                    @Override
                    public void onMessageFromNanoApp(
                            ContextHubClient client, NanoAppMessage message) {
                        if (message.getNanoAppId() == mNanoAppBinary.getNanoAppId()) {
                            validator.assertMessageReceivedIsExpected(message);
                        }
                    }
                };
        return mTestHelper.createClient(callback);
    }

    /**
     * Sends a message to an echo_message nanoapp, and verify that the client receives the same
     * message back.
     */
    public void testSingleMessage(int numOfTestCycles) throws InterruptedException {
        NanoAppMessage mNanoAppMessage = createNanoAppMessage();
        for (int i = 0; i < numOfTestCycles; i++) {
            CountDownLatch latch = new CountDownLatch(1);
            ContextHubClient contextHubClient =
                    registerMessageClient(latch, ImmutableList.of(mNanoAppMessage));
            long startTimeMillis = SystemClock.elapsedRealtime();

            int result = contextHubClient.sendMessageToNanoApp(mNanoAppMessage);
            assertWithMessage("Send message failed with error code " + result)
                    .that(result)
                    .isEqualTo(ContextHubTransaction.RESULT_SUCCESS);
            assertLatchCountedDown(latch, TIMEOUT_SECONDS_MESSAGE);

            long roundTripTime = SystemClock.elapsedRealtime() - startTimeMillis;
            Log.d(TAG, String.format("RTT = %s ms for round %s", roundTripTime, i));
            unregisterMessageClient(contextHubClient);
        }
    }

    /**
     * Sends distinct messages from a ContextHubClient to the echo_message nanoapp, and verify that
     * the client receives all messages back.
     *
     * @param numofTestCycles number of times the test is run
     * @param numOfMessages number of messages sent to the nanoapp
     */
    public void testBurstMessages(int numofTestCycles, int numOfMessages)
            throws InterruptedException {
        for (int i = 0; i < numofTestCycles; i++) {
            List<NanoAppMessage> messagesToNanoapp = createNanoAppMessages(numOfMessages);
            CountDownLatch latch = new CountDownLatch(1);
            ContextHubClient client = registerMessageClient(latch, messagesToNanoapp);
            long startTimeMillis = SystemClock.elapsedRealtime();
            for (NanoAppMessage message : messagesToNanoapp) {
                int result = client.sendMessageToNanoApp(message);
                assertWithMessage("Send message failed with error code " + result)
                        .that(result)
                        .isEqualTo(ContextHubTransaction.RESULT_SUCCESS);
            }
            long timeoutThreshold = numOfMessages * TIMEOUT_SECONDS_MESSAGE;
            assertLatchCountedDown(latch, timeoutThreshold);
            Log.d(
                    TAG,
                    String.format(
                            "RTT (1 clients %s messages) is %s ms in round %s",
                            numOfMessages, SystemClock.elapsedRealtime() - startTimeMillis, i));
            unregisterMessageClient(client);
        }
    }

    /**
     * Creates a Runnable function to send messages for a ContextHubClient
     *
     * @param client the client to send message for
     * @param messages the list of message to send
     * @return the Runnable function
     */
    private static Runnable createMessageRunnable(
            ContextHubClient client, List<NanoAppMessage> messages) {
        return () -> {
            for (NanoAppMessage message : messages) {
                int result = client.sendMessageToNanoApp(message);
                assertWithMessage("Send message failed with error code " + result)
                        .that(result)
                        .isEqualTo(ContextHubTransaction.RESULT_SUCCESS);
            }
        };
    }

    /**
     * Sends different messages from multiple ContextHubClients concurrently to the nanoapp, and
     * verify that each client receives its own messages back.
     *
     * @param numofTestCycles number of times the test is run
     * @param numOfClients number of {@link ContextHubClient}
     * @param numOfMessages number of messages sent to the nanoapp
     */
    public void testConcurrentMessages(int numofTestCycles, int numOfClients, int numOfMessages)
            throws InterruptedException {
        for (int i = 0; i < numofTestCycles; i++) {
            CountDownLatch latch = new CountDownLatch(numOfClients);
            ExecutorService executorService = Executors.newCachedThreadPool();
            long startTimeMillis = SystemClock.elapsedRealtime();
            List<ContextHubClient> clients = new ArrayList<>(numOfClients);

            // for each ContextHubClient, create a runnable to send messages to the nanoapp
            for (int j = 0; j < numOfClients; j++) {
                List<NanoAppMessage> messagesToNanoapp = createNanoAppMessages(numOfMessages);
                ContextHubClient client = registerMessageClient(latch, messagesToNanoapp);
                clients.add(client);
                executorService.submit(createMessageRunnable(client, messagesToNanoapp));
            }
            executorService.shutdown();
            boolean isTerminated =
                    executorService.awaitTermination(TIMEOUT_SECONDS_MESSAGE, TimeUnit.SECONDS);
            assertWithMessage("ExecutorService is not terminated").that(isTerminated).isTrue();

            // wait for all the clients to finish
            long timeoutThreshold = numOfMessages * TIMEOUT_SECONDS_MESSAGE;
            assertLatchCountedDown(latch, timeoutThreshold);
            Log.d(
                    TAG,
                    String.format(
                            "RTT (%s clients %s messages) is %s ms in round %s",
                            numOfClients,
                            numOfMessages,
                            SystemClock.elapsedRealtime() - startTimeMillis,
                            i));
            // unregister all the clients
            for (ContextHubClient client : clients) {
                unregisterMessageClient(client);
            }
        }
    }

    private void unregisterMessageClient(ContextHubClient client) {
        if (client != null) {
            client.close();
            Log.d(TAG, "Sending message after closing client, "
                    + "expecting a error message from sendMessageToNanoApp");
            int result = client.sendMessageToNanoApp(createNanoAppMessage());
            assertWithMessage("Send message succeeded after client close")
                    .that(result)
                    .isNotEqualTo(ContextHubTransaction.RESULT_SUCCESS);
        }
    }
}
