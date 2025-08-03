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

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.android.utils.chre.ContextHubServiceTestHelper.TIMEOUT_SECONDS_LOAD;
import static com.google.android.utils.chre.ContextHubServiceTestHelper.TIMEOUT_SECONDS_MESSAGE;
import static com.google.android.utils.chre.ContextHubServiceTestHelper.TIMEOUT_SECONDS_UNLOAD;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.location.ContextHubClient;
import android.hardware.location.ContextHubInfo;
import android.hardware.location.ContextHubIntentEvent;
import android.hardware.location.ContextHubManager;
import android.hardware.location.ContextHubTransaction;
import android.hardware.location.NanoAppBinary;
import android.hardware.location.NanoAppMessage;
import android.os.Build;
import android.util.Log;

import com.google.android.utils.chre.ContextHubBroadcastReceiver;
import com.google.android.utils.chre.ContextHubServiceTestHelper;

import org.junit.Assert;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ContextHubPendingIntentTestExecutor {
    public static final String ACTION = "com.google.android.chre.test.chqts.ACTION";

    private static final String TAG = "ContextHubPendingIntentTest";
    // Additional timeout to delay receiving Intent events.
    private static final int TIMEOUT_INTENT_EVENT_SECONDS = 5;
    private static final int MESSAGE_TYPE =
            ContextHubTestConstants.MessageType.SERVICE_MESSAGE.asInt();

    private final long mNanoAppId;
    private final NanoAppBinary mNanoAppBinary;
    private final Context mContext = getInstrumentation().getTargetContext();
    private final BroadcastReceiver mReceiver = new ContextHubBroadcastReceiver();
    private final PendingIntent mPendingIntent;
    private final ContextHubInfo mContextHubInfo;
    private final ContextHubManager mContextHubManager;
    private final ContextHubServiceTestHelper mTestHelper;

    private ContextHubClient mContextHubClient = null;

    public ContextHubPendingIntentTestExecutor(ContextHubManager contextHubManager,
            ContextHubInfo contextHubInfo, NanoAppBinary nanoAppBinary) {
        mNanoAppBinary = nanoAppBinary;
        mContextHubInfo = contextHubInfo;
        mContextHubManager = contextHubManager;
        mTestHelper = new ContextHubServiceTestHelper(contextHubInfo, contextHubManager);
        mNanoAppId = mNanoAppBinary.getNanoAppId();
        IntentFilter filter = new IntentFilter(ACTION);
        mContext.registerReceiver(mReceiver, filter, Context.RECEIVER_EXPORTED/*UNAUDITED*/);
        Intent intent = new Intent(ACTION).setPackage(mContext.getPackageName());
        mPendingIntent = PendingIntent.getBroadcast(mContext, 0 /* requestCode */, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
    }

    public void init() throws InterruptedException, TimeoutException {
        mTestHelper.initAndUnloadAllNanoApps();
    }

    /**
     * This test does the following:
     * - Loads the who_am_i nanoapp, creates a ContextHubClient, and asks the nanoapp for the host
     * endpoint ID.
     * - Regenerates the ContextHubClient and asks the host endpoint ID, and checks if they are the
     * same.
     * - Unloads the nanoapp and closes the ContextHubClient.
     * - Creates a ContextHubClient associated with a different nanoapp, and checks that Intent
     * events are not received for the who_am_i nanoapp.
     */
    public void basicPendingIntentTest(long sampleNanoAppId) {
        createClient(mNanoAppId);
        checkLoadNanoApp();

        for (int i = 0; i < 10; i++) {
            short hostEndpointId = getIdFromNanoApp();
            Log.d(TAG, "My host endpoint ID is " + hostEndpointId);

            mContextHubClient = mTestHelper.createClient(mPendingIntent, mNanoAppId);
            assertWithMessage("Failed to regenerate PendingIntent client").that(
                    mContextHubClient).isNotNull();
            assertThat(getIdFromNanoApp()).isEqualTo(hostEndpointId);
            // ContextHubClient.getId() was introduced in Android T. Check version before calling.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Assert.assertEquals(mContextHubClient.getId(), hostEndpointId);
            }
        }

        checkUnloadNanoApp();
        mContextHubClient.close();

        mContextHubClient = mTestHelper.createClient(mPendingIntent, sampleNanoAppId);
        mTestHelper.loadNanoAppAssertSuccess(mNanoAppBinary);

        NanoAppMessage message = NanoAppMessage.createMessageToNanoApp(mNanoAppId, MESSAGE_TYPE,
                new byte[0]);
        int result = mContextHubClient.sendMessageToNanoApp(message);
        assertThat(result).isEqualTo(ContextHubTransaction.RESULT_SUCCESS);

        mTestHelper.unloadNanoAppAssertSuccess(mNanoAppBinary.getNanoAppId());
        ContextHubBroadcastReceiver.assertNoIntentEventReceived(
                TIMEOUT_SECONDS_MESSAGE + TIMEOUT_INTENT_EVENT_SECONDS, TimeUnit.SECONDS);
    }

    public void deinit() {
        if (mContextHubClient != null) {
            mContextHubClient.close();
            mContextHubClient = null;
        }
        mContext.unregisterReceiver(mReceiver);
        mTestHelper.deinit();
    }

    /** Loads a nanoapp and asserts that a load Intent event is received. */
    private void checkLoadNanoApp() {
        mContextHubManager.loadNanoApp(mContextHubInfo, mNanoAppBinary);
        ContextHubBroadcastReceiver.pollIntentEvent(
                TIMEOUT_SECONDS_LOAD + TIMEOUT_INTENT_EVENT_SECONDS, TimeUnit.SECONDS,
                ContextHubManager.EVENT_NANOAPP_LOADED, mContextHubInfo, mNanoAppId);
    }

    /** Asks the who_am_i nanoapp for a ContextHubClient's host endpoint ID via an Intent event. */
    public short getIdFromNanoApp() {
        NanoAppMessage message = NanoAppMessage.createMessageToNanoApp(mNanoAppId, MESSAGE_TYPE,
                new byte[0]);
        int result = mContextHubClient.sendMessageToNanoApp(message);
        assertThat(result).isEqualTo(ContextHubTransaction.RESULT_SUCCESS);

        return waitForIdFromNanoApp();
    }

    /**
     * Waits for a Intent event message from the who_am_i nanoapp indicating the host endpoint ID.
     */
    public short waitForIdFromNanoApp() {
        ContextHubIntentEvent event = ContextHubBroadcastReceiver.pollIntentEvent(
                TIMEOUT_SECONDS_MESSAGE + TIMEOUT_INTENT_EVENT_SECONDS, TimeUnit.SECONDS,
                ContextHubManager.EVENT_NANOAPP_MESSAGE, mContextHubInfo, mNanoAppId);
        byte[] appMessage = event.getNanoAppMessage().getMessageBody();
        return ByteBuffer.wrap(appMessage).order(ByteOrder.BIG_ENDIAN).asShortBuffer().get(0);
    }

    public ContextHubClient createClient(long nanoAppId) {
        mContextHubClient = mTestHelper.createClient(mPendingIntent, nanoAppId);
        Assert.assertNotNull("Failed to register PendingIntent client", mContextHubClient);
        return mContextHubClient;
    }

    /** Unloads a nanoapp and asserts that an unload Intent event is received. */
    private void checkUnloadNanoApp() {
        mTestHelper.unloadNanoApp(mNanoAppId);
        ContextHubBroadcastReceiver.pollIntentEvent(
                TIMEOUT_SECONDS_UNLOAD + TIMEOUT_INTENT_EVENT_SECONDS, TimeUnit.SECONDS,
                ContextHubManager.EVENT_NANOAPP_UNLOADED, mContextHubInfo, mNanoAppId);
    }
}
