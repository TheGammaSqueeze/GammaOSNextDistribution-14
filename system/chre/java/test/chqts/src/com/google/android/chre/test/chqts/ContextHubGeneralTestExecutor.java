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

import android.app.Instrumentation;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.location.ContextHubClient;
import android.hardware.location.ContextHubClientCallback;
import android.hardware.location.ContextHubInfo;
import android.hardware.location.ContextHubManager;
import android.hardware.location.ContextHubTransaction;
import android.hardware.location.NanoAppBinary;
import android.hardware.location.NanoAppMessage;
import android.util.Log;

import com.google.android.utils.chre.ChreTestUtil;
import com.google.android.utils.chre.SettingsUtil;

import org.junit.Assert;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A class that can execute the CHQTS "general" tests. Nanoapps using this "general" test framework
 * have the name "general_test".
 *
 * A test successfully passes in one of two ways:
 * - MessageType.SUCCESS received from the Nanoapp by this infrastructure.
 * - A call to ContextHubGeneralTestExecutor.pass() by the test code.
 *
 * NOTE: A test must extend this class and define the handleNanoappMessage() function to handle
 * specific messages for the test.
 *
 * TODO: Refactor this class to be able to be invoked for < P builds.
 */
public abstract class ContextHubGeneralTestExecutor extends ContextHubClientCallback {
    public static final String TAG = "ContextHubGeneralTestExecutor";

    private final List<GeneralTestNanoApp> mGeneralTestNanoAppList;

    private final Set<Long> mNanoAppIdSet;

    private ContextHubClient mContextHubClient;

    private final ContextHubManager mContextHubManager;

    private final ContextHubInfo mContextHubInfo;

    private CountDownLatch mCountDownLatch;

    private boolean mInitialized = false;

    private AtomicReference<String> mErrorString = new AtomicReference<>(null);

    private long mThreadId;

    private final Instrumentation mInstrumentation =
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation();

    private final Context mContext = mInstrumentation.getTargetContext();

    private final SettingsUtil mSettingsUtil;

    private boolean mInitialBluetoothEnabled = false;

    public static class BluetoothUpdateListener {
        public CountDownLatch mBluetoothLatch = new CountDownLatch(1);

        public BroadcastReceiver mBluetoothUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                    mBluetoothLatch.countDown();
                }
            }
        };
    }

    /**
     * A container class to describe a general_test nanoapp.
     */
    public static class GeneralTestNanoApp {
        private final NanoAppBinary mNanoAppBinary;
        private final ContextHubTestConstants.TestNames mTestName;

        // Set to false if the nanoapp should not be loaded at init. An example of why this may be
        // needed are for nanoapps that are loaded in the middle of the test execution, but still
        // needs to be included in this test executor (e.g. deliver messages from it).
        private final boolean mLoadAtInit;

        // Set to false if the nanoapp should not send a start message at init. An example of where
        // this is not needed is for test nanoapps that use the general_test protocol, but do not
        // require a start message (e.g. starts on load like the busy_startup nanoapp).
        private final boolean mSendStartMessage;

        public GeneralTestNanoApp(NanoAppBinary nanoAppBinary,
                ContextHubTestConstants.TestNames testName) {
            mTestName = testName;
            mNanoAppBinary = nanoAppBinary;
            mLoadAtInit = true;
            mSendStartMessage = true;
        }

        public GeneralTestNanoApp(NanoAppBinary nanoAppBinary,
                ContextHubTestConstants.TestNames testName, boolean loadAtInit) {
            mTestName = testName;
            mNanoAppBinary = nanoAppBinary;
            mLoadAtInit = loadAtInit;
            mSendStartMessage = true;
        }

        public GeneralTestNanoApp(NanoAppBinary nanoAppBinary,
                ContextHubTestConstants.TestNames testName,
                boolean loadAtInit, boolean sendStartMessage) {
            mTestName = testName;
            mNanoAppBinary = nanoAppBinary;
            mLoadAtInit = loadAtInit;
            mSendStartMessage = sendStartMessage;
        }

        public NanoAppBinary getNanoAppBinary() {
            return mNanoAppBinary;
        }

        public ContextHubTestConstants.TestNames getTestName() {
            return mTestName;
        }

        public boolean loadAtInit() {
            return mLoadAtInit;
        }

        public boolean sendStartMessage() {
            return mSendStartMessage;
        }
    }

    /**
     * Note that this constructor accepts multiple general_test nanoapps to test.
     */
    public ContextHubGeneralTestExecutor(ContextHubManager manager, ContextHubInfo info,
            GeneralTestNanoApp... tests) {
        mContextHubManager = manager;
        mContextHubInfo = info;
        mGeneralTestNanoAppList = new ArrayList<>(Arrays.asList(tests));
        mNanoAppIdSet = new HashSet<>();
        for (GeneralTestNanoApp test : mGeneralTestNanoAppList) {
            mNanoAppIdSet.add(test.getNanoAppBinary().getNanoAppId());
        }
        mSettingsUtil = new SettingsUtil(mContext);
    }

    @Override
    public void onMessageFromNanoApp(ContextHubClient client, NanoAppMessage message) {
        if (mNanoAppIdSet.contains(message.getNanoAppId())) {
            NanoAppMessage realMessage = hackMessageFromNanoApp(message);

            int messageType = realMessage.getMessageType();
            ContextHubTestConstants.MessageType messageEnum =
                    ContextHubTestConstants.MessageType.fromInt(messageType, "");
            byte[] data = realMessage.getMessageBody();

            switch (messageEnum) {
                case INVALID_MESSAGE_TYPE:  // fall-through
                case FAILURE:  // fall-through
                case INTERNAL_FAILURE:
                    // These are univeral failure conditions for all tests.
                    // If they have data, it's expected to be an ASCII string.
                    String errorString = new String(data, Charset.forName("US-ASCII"));
                    fail(errorString);
                    break;

                case SKIPPED:
                    // TODO: Use junit Assume
                    String reason = new String(data, Charset.forName("US-ASCII"));
                    Log.w(TAG, "SKIPPED " + ":" + reason);
                    pass();
                    break;

                case SUCCESS:
                    // This is a universal success for the test.  We ignore
                    // 'data'.
                    pass();
                    break;

                default:
                    handleMessageFromNanoApp(message.getNanoAppId(), messageEnum, data);
            }
        }
    }

    /**
     * Should be invoked before run() is invoked to set up the test, e.g. in a @Before method.
     */
    public void init() {
        Assert.assertFalse("init() must not be invoked when already initialized", mInitialized);

        mInitialized = true;

        // Initialize the CountDownLatch before run() since some nanoapps will start on load.
        mCountDownLatch = new CountDownLatch(1);

        mContextHubClient = mContextHubManager.createClient(mContextHubInfo, this);

        for (GeneralTestNanoApp test : mGeneralTestNanoAppList) {
            if (test.getTestName() == ContextHubTestConstants.TestNames.BASIC_BLE_TEST) {
                handleBleTestSetup();
            }
            if (test.loadAtInit()) {
                ChreTestUtil.loadNanoAppAssertSuccess(mContextHubManager, mContextHubInfo,
                        test.getNanoAppBinary());
            }
        }

        mErrorString.set(null);
    }

    private void handleBleTestSetup() {
        mInitialBluetoothEnabled = mSettingsUtil.isBluetoothEnabled();
        Log.i(TAG, "Initial bluetooth setting enabled: " + mInitialBluetoothEnabled);
        if (mInitialBluetoothEnabled) {
            return;
        }
        BluetoothUpdateListener bluetoothUpdateListener = new BluetoothUpdateListener();
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        mContext.registerReceiver(bluetoothUpdateListener.mBluetoothUpdateReceiver, filter);
        mSettingsUtil.setBluetooth(true /* enable */);
        try {
            bluetoothUpdateListener.mBluetoothLatch.await(10, TimeUnit.SECONDS);
            Assert.assertTrue(mSettingsUtil.isBluetoothEnabled());
            Log.i(TAG, "Bluetooth enabled successfully");
            // Wait a few seconds to ensure setting is propagated to CHRE path
            // TODO(b/302018530): Remove Thread.sleep calls for CHRE settings propagation
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Assert.fail(e.getMessage());
        }
    }

    /**
     * Run the test.
     */
    public void run(long timeoutSeconds) throws InterruptedException {
        mThreadId = Thread.currentThread().getId();

        for (GeneralTestNanoApp test : mGeneralTestNanoAppList) {
            if (test.loadAtInit() && test.sendStartMessage()) {
                sendMessageToNanoAppOrFail(test.getNanoAppBinary().getNanoAppId(),
                        test.getTestName().asInt(), new byte[0] /* data */);
            }
        }

        boolean success = mCountDownLatch.await(timeoutSeconds, TimeUnit.SECONDS);
        Assert.assertTrue("Test timed out", success);
    }

    /**
     * Invoke to indicate that the test has passed.
     */
    public void pass() {
        mCountDownLatch.countDown();
    }

    /**
     * Cleans up the test, should be invoked in e.g. @After method.
     */
    public void deinit() {
        Assert.assertTrue("deinit() must be invoked after init()", mInitialized);

        // TODO: If the nanoapp aborted (i.e. test failed), wait for CHRE reset or nanoapp abort
        // callback, and otherwise assert unload success.
        for (GeneralTestNanoApp test : mGeneralTestNanoAppList) {
            if (test.getTestName() == ContextHubTestConstants.TestNames.BASIC_BLE_TEST
                    && !mInitialBluetoothEnabled) {
                mSettingsUtil.setBluetooth(mInitialBluetoothEnabled);
            }
            ChreTestUtil.unloadNanoApp(mContextHubManager, mContextHubInfo,
                    test.getNanoAppBinary().getNanoAppId());
        }

        mContextHubClient.close();
        mContextHubClient = null;

        mInitialized = false;

        if (mErrorString.get() != null) {
            Assert.fail(mErrorString.get());
        }
    }

    /**
     * Sends a message to the test nanoapp.
     *
     * @param nanoAppId The 64-bit ID of the nanoapp to send the message to.
     * @param type      The message type.
     * @param data      The message payload.
     */
    protected void sendMessageToNanoAppOrFail(long nanoAppId, int type, byte[] data) {
        NanoAppMessage message = NanoAppMessage.createMessageToNanoApp(
                nanoAppId, type, data);

        int result = mContextHubClient.sendMessageToNanoApp(hackMessageToNanoApp(message));
        if (result != ContextHubTransaction.RESULT_SUCCESS) {
            fail("Failed to send message: result = " + result);
        }
    }

    /**
     * @param errorMessage The error message to display
     */
    protected void fail(String errorMessage) {
        assertTrue(errorMessage, false /* condition */);
    }

    /**
     * Semantics the same as Assert.assertEquals.
     */
    protected <T> void assertEquals(String errorMessage, T expected, T actual) {
        if (Thread.currentThread().getId() == mThreadId) {
            Assert.assertEquals(errorMessage, expected, actual);
        } else if ((expected == null && actual != null) || (expected != null && !expected.equals(
                actual))) {
            mErrorString.set(errorMessage + ": " + expected + " != " + actual);
            mCountDownLatch.countDown();
        }
    }

    /**
     * Semantics the same as Assert.assertTrue.
     */
    protected void assertTrue(String errorMessage, boolean condition) {
        if (Thread.currentThread().getId() == mThreadId) {
            Assert.assertTrue(errorMessage, condition);
        } else if (!condition) {
            mErrorString.set(errorMessage);
            mCountDownLatch.countDown();
        }
    }

    /**
     * Semantics are the same as Assert.assertFalse.
     */
    protected void assertFalse(String errorMessage, boolean condition) {
        assertTrue(errorMessage, !condition);
    }

    protected ContextHubManager getContextHubManager() {
        return mContextHubManager;
    }

    protected ContextHubInfo getContextHubInfo() {
        return mContextHubInfo;
    }

    /**
     * Handles a message specific for a test.
     *
     * @param nanoAppId The 64-bit ID of the nanoapp sending the message.
     * @param type      The message type.
     * @param data      The message body.
     */
    protected abstract void handleMessageFromNanoApp(
            long nanoAppId, ContextHubTestConstants.MessageType type, byte[] data);

    // TODO: Remove this hack
    protected NanoAppMessage hackMessageToNanoApp(NanoAppMessage message) {
        // For NYC, we are not able to assume that the messageType correctly
        // makes it to the nanoapp.  So we put it, in little endian, as the
        // first four bytes of the message.
        byte[] origData = message.getMessageBody();
        ByteBuffer newData = ByteBuffer.allocate(4 + origData.length);
        newData.order(ByteOrder.LITTLE_ENDIAN);
        newData.putInt(message.getMessageType());
        newData.put(origData);
        return NanoAppMessage.createMessageToNanoApp(
                message.getNanoAppId(), message.getMessageType(), newData.array());
    }

    // TODO: Remove this hack
    protected NanoAppMessage hackMessageFromNanoApp(NanoAppMessage message) {
        // For now, our nanohub HAL and JNI code end up not sending across the
        // message type of the user correctly.  So our testing protocol hacks
        // around this by putting the message type in the first four bytes of
        // the data payload, in little endian.
        ByteBuffer origData = ByteBuffer.wrap(message.getMessageBody());
        origData.order(ByteOrder.LITTLE_ENDIAN);
        int newMessageType = origData.getInt();
        // The new data is the remainder of this array (which could be empty).
        byte[] newData = new byte[origData.remaining()];
        origData.get(newData);
        return NanoAppMessage.createMessageFromNanoApp(
                message.getNanoAppId(), newMessageType, newData,
                message.isBroadcastMessage());
    }
}
