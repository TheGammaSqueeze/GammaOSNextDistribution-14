/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.google.android.chre.test.permissions;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.Instrumentation;
import android.content.Context;
import android.hardware.location.ContextHubClient;
import android.hardware.location.ContextHubClientCallback;
import android.hardware.location.ContextHubInfo;
import android.hardware.location.ContextHubManager;
import android.hardware.location.ContextHubTransaction;
import android.hardware.location.NanoAppBinary;
import android.hardware.location.NanoAppMessage;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import com.google.android.chre.nanoapp.proto.PingTest;
import com.google.android.utils.chre.ChreTestUtil;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A class that can execute the Context Hub framework permissions test.
 */
public class ContextHubFrameworkPermissionsTestExecutor extends ContextHubClientCallback {
    private static final String TAG = "ContextHubFrameworkPermissionsTestExecutor";

    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();

    private final NanoAppBinary mNanoAppBinary;

    private final long mNanoAppId;

    private final ContextHubClient mContextHubClient;

    private final ContextHubManager mContextHubManager;

    private final ContextHubInfo mContextHubInfo;

    private final Context mContext;

    private final String mPackageName;

    private AtomicBoolean mHubResetDuringTest = new AtomicBoolean();

    private LinkedBlockingDeque<NanoAppMessage> mMessageQueue = new LinkedBlockingDeque<>();

    private LinkedBlockingDeque<Integer> mAuthorizationUpdateQueue = new LinkedBlockingDeque<>();

    public ContextHubFrameworkPermissionsTestExecutor(
            ContextHubManager manager, ContextHubInfo info, NanoAppBinary binary) {
        mContextHubManager = manager;
        mContextHubInfo = info;
        mNanoAppBinary = binary;
        mNanoAppId = mNanoAppBinary.getNanoAppId();
        mContext = mInstrumentation.getTargetContext();
        mPackageName = mContext.getPackageName();

        mContextHubClient = mContextHubManager.createClient(mContextHubInfo, this);
        assertThat(mContextHubClient).isNotNull();
    }

    @Override
    public void onMessageFromNanoApp(ContextHubClient client, NanoAppMessage message) {
        if (message.getNanoAppId() == mNanoAppId) {
            Log.e(TAG, "Got message from nanoapp: type " + message.getMessageType());
            mMessageQueue.add(message);
        }
    }

    @Override
    public void onClientAuthorizationChanged(
            ContextHubClient client, long nanoAppId, int authorization) {
        if (nanoAppId == mNanoAppId) {
            Log.d(TAG, "onClientAuthorizationChanged: nanoapp ID " + nanoAppId + " auth state "
                    + authorization);
            mAuthorizationUpdateQueue.add(authorization);
        }
    }

    @Override
    public void onHubReset(ContextHubClient client) {
        mHubResetDuringTest.set(true);
    }

    /**
     * Should be invoked before run() is invoked to set up the test, e.g. in a @Before method.
     */
    public void init() {
        ChreTestUtil.loadNanoAppAssertSuccess(mContextHubManager, mContextHubInfo, mNanoAppBinary);
    }

    /**
     * Runs the test where an app is forced to the denied authentication state.
     */
    public void permissionsDisabledTest() throws Exception {
        PingTest.PingCommand command =
                PingTest.PingCommand.newBuilder().build();
        NanoAppMessage message = NanoAppMessage.createMessageToNanoApp(
                mNanoAppId, PingTest.MessageType.PING_COMMAND_VALUE,
                command.toByteArray());
        int result = mContextHubClient.sendMessageToNanoApp(message);
        assertWithMessage("Failed to send message: result = %s ", result)
                .that(result)
                .isEqualTo(ContextHubTransaction.RESULT_SUCCESS);

        NanoAppMessage msg = mMessageQueue.poll(2, TimeUnit.SECONDS);
        assertWithMessage("Timed out waiting for a message").that(msg).isNotNull();
        Log.d(TAG, "Got message from nanoapp: " + msg);

        // No need to grant our package again since the denial will be tied to
        // the current contexthubclient which is only used by this test.
        ChreTestUtil.executeShellCommandWithAppPerms(
                "cmd contexthub deny " + mContextHubInfo.getId()
                + " " + mContext.getPackageName() + " " + mNanoAppId);

        Integer authorization = mAuthorizationUpdateQueue.poll(2, TimeUnit.SECONDS);
        assertWithMessage("Timed out waiting on denied authorization update")
                .that(authorization)
                .isNotNull();
        assertThat(authorization).isEqualTo(ContextHubManager.AUTHORIZATION_DENIED);

        try {
            mContextHubClient.sendMessageToNanoApp(message);
            assertWithMessage("Sent message to nanoapp even though permissions were denied").fail();
        } catch (SecurityException e) {
            // Expected
        }

        assertThat(mAuthorizationUpdateQueue).isEmpty();
        assertThat(mHubResetDuringTest.get()).isFalse();
    }

    /**
     * Runs the test where an app attempts to send a message with data covered
     * by permissions.
     */
    public void messagePermissionsTest() throws Exception {
        // Defined in chre/util/system/napp_permissions.h
        final int audioPermission = 1;
        PingTest.PingCommand command =
                PingTest.PingCommand.newBuilder().setPermissions(audioPermission).build();
        NanoAppMessage message = NanoAppMessage.createMessageToNanoApp(
                mNanoAppId, PingTest.MessageType.PING_COMMAND_VALUE,
                command.toByteArray());
        int result = mContextHubClient.sendMessageToNanoApp(message);
        assertWithMessage("Failed to send message: result = %s ", result)
                .that(result)
                .isEqualTo(ContextHubTransaction.RESULT_SUCCESS);

        NanoAppMessage msg = mMessageQueue.poll(2, TimeUnit.SECONDS);
        assertWithMessage("Timed out waiting for a message").that(msg).isNotNull();
        Log.d(TAG, "Got message from nanoapp: " + msg);
    }

    /**
     * Cleans up the test, should be invoked in e.g. @After method.
     */
    public void deinit() {
        ChreTestUtil.unloadNanoApp(mContextHubManager, mContextHubInfo, mNanoAppId);
        mContextHubClient.close();
    }
}
