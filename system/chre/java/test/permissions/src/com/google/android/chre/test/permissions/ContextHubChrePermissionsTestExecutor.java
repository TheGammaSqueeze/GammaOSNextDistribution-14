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

import android.app.Instrumentation;
import android.hardware.location.ContextHubClient;
import android.hardware.location.ContextHubClientCallback;
import android.hardware.location.ContextHubInfo;
import android.hardware.location.ContextHubManager;
import android.hardware.location.ContextHubTransaction;
import android.hardware.location.NanoAppBinary;
import android.hardware.location.NanoAppMessage;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import com.google.android.chre.nanoapp.proto.ChreTestCommon;
import com.google.android.chre.nanoapp.proto.PermissionTest;
import com.google.android.utils.chre.ChreTestUtil;

import org.junit.Assert;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A class that can execute the permission gated API test.
 */
public class ContextHubChrePermissionsTestExecutor extends ContextHubClientCallback {
    private static final String TAG = "ContextHubChrePermissionsTestExecutor";

    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();

    private final NanoAppBinary mNanoAppBinary;

    private final long mNanoAppId;

    private final ContextHubClient mContextHubClient;

    private final ContextHubManager mContextHubManager;

    private final ContextHubInfo mContextHubInfo;

    private AtomicBoolean mHubResetDuringTest = new AtomicBoolean();

    private LinkedBlockingDeque<NanoAppMessage> mMessageQueue = new LinkedBlockingDeque<>();

    public ContextHubChrePermissionsTestExecutor(
            ContextHubManager manager, ContextHubInfo info, NanoAppBinary binary) {
        mContextHubManager = manager;
        mContextHubInfo = info;
        mNanoAppBinary = binary;
        mNanoAppId = mNanoAppBinary.getNanoAppId();

        mContextHubClient = mContextHubManager.createClient(mContextHubInfo, this);
    }

    @Override
    public void onMessageFromNanoApp(ContextHubClient client, NanoAppMessage message) {
        if (message.getNanoAppId() == mNanoAppId) {
            Log.d(TAG, "Got message from nanoapp: type " + message.getMessageType());
            mMessageQueue.add(message);
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
    public void checkPermissionGatedApisTest() throws Exception {
        NanoAppMessage message = NanoAppMessage.createMessageToNanoApp(
                mNanoAppId, PermissionTest.MessageType.TEST_COMMAND_VALUE,
                new byte[0] /* messageBody */);
        int result = mContextHubClient.sendMessageToNanoApp(message);
        if (result != ContextHubTransaction.RESULT_SUCCESS) {
            Assert.fail("Failed to send message: result = " + result);
        }

        NanoAppMessage rcvdMsg = null;
        try {
            rcvdMsg = mMessageQueue.poll(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Assert.fail(e.getMessage());
        }

        Assert.assertNotNull("Timed out waiting for a message", rcvdMsg);
        Log.d(TAG, "Got message from nanoapp: " + rcvdMsg);

        Assert.assertEquals(PermissionTest.MessageType.TEST_RESULT_VALUE, rcvdMsg.getMessageType());
        ChreTestCommon.TestResult resultProto =
                ChreTestCommon.TestResult.parseFrom(rcvdMsg.getMessageBody());
        if (resultProto.hasErrorMessage()) {
            Assert.assertEquals(new String(resultProto.getErrorMessage().toByteArray(),
                                           StandardCharsets.UTF_8),
                                ChreTestCommon.TestResult.Code.PASSED, resultProto.getCode());
        } else {
            Assert.assertEquals(ChreTestCommon.TestResult.Code.PASSED, resultProto.getCode());
        }
    }

    /**
     * Cleans up the test, should be invoked in e.g. @After method.
     */
    public void deinit() {
        ChreTestUtil.unloadNanoApp(mContextHubManager, mContextHubInfo, mNanoAppId);
        mContextHubClient.close();
        Assert.assertFalse(mHubResetDuringTest.get());
    }
}
