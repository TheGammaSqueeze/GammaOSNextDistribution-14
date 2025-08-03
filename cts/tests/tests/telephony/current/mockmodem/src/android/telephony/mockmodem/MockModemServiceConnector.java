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

package android.telephony.mockmodem;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.telephony.cts.util.TelephonyUtils;
import android.text.TextUtils;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Connects Telephony Framework to MockModemService. */
class MockModemServiceConnector {

    private static final String TAG = "MockModemServiceConnector";

    private static final String COMMAND_BASE = "cmd phone ";
    private static final String COMMAND_SET_MODEM_SERVICE = "radio set-modem-service ";
    private static final String COMMAND_GET_MODEM_SERVICE = "radio get-modem-service ";
    private static final String COMMAND_SERVICE_IDENTIFIER = "-s ";
    private static final String COMMAND_MODEM_SERVICE_UNKNOWN = "unknown";
    private static final String COMMAND_MODEM_SERVICE_DEFAULT = "default";

    private static final int BIND_LOCAL_MOCKMODEM_SERVICE_TIMEOUT_MS = 25000;
    private static final int BIND_RADIO_INTERFACE_READY_TIMEOUT_MS = 5000;

    private class MockModemServiceConnection implements ServiceConnection {

        private final CountDownLatch mLatch;

        MockModemServiceConnection(CountDownLatch latch) {
            mLatch = latch;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            String serviceName;
            mMockModemService = ((MockModemService.LocalBinder) service).getService();
            serviceName = name.getPackageName() + "/" + name.getClassName();
            updateModemServiceName(serviceName);
            mLatch.countDown();
            Log.d(TAG, "MockModemServiceConnection - " + serviceName + " onServiceConnected");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mMockModemService = null;
            Log.d(TAG, "MockModemServiceConnection - onServiceDisconnected");
        }
    }

    private Instrumentation mInstrumentation;

    private MockModemService mMockModemService;
    private MockModemServiceConnection mMockModemServiceConn;
    private boolean mIsServiceOverridden;
    private String mModemServiceName;

    MockModemServiceConnector(Instrumentation instrumentation) {
        mInstrumentation = instrumentation;
    }

    private boolean setupLocalMockModemService() {
        Log.d(TAG, "setupLocalMockModemService");
        if (mMockModemService != null) {
            return true;
        }

        CountDownLatch latch = new CountDownLatch(1);
        if (mMockModemServiceConn == null) {
            mMockModemServiceConn = new MockModemServiceConnection(latch);
        }

        mInstrumentation
                .getContext()
                .bindService(
                        new Intent(mInstrumentation.getContext(), MockModemService.class),
                        mMockModemServiceConn,
                        Context.BIND_AUTO_CREATE);
        try {
            return latch.await(BIND_LOCAL_MOCKMODEM_SERVICE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            return false;
        }
    }

    private String constructSetModemServiceOverrideCommand() {
        return COMMAND_BASE
                + COMMAND_SET_MODEM_SERVICE
                + COMMAND_SERVICE_IDENTIFIER
                + mModemServiceName;
    }

    private String constructGetModemServiceCommand() {
        return COMMAND_BASE + COMMAND_GET_MODEM_SERVICE;
    }

    private String constructClearModemServiceOverrideCommand() {
        return COMMAND_BASE + COMMAND_SET_MODEM_SERVICE;
    }

    private boolean setModemService() throws Exception {
        String result =
                TelephonyUtils.executeShellCommand(
                        mInstrumentation, constructSetModemServiceOverrideCommand());
        Log.d(TAG, "setModemService result: " + result);
        return "true".equals(result);
    }

    private String getModemService() throws Exception {
        String result =
                TelephonyUtils.executeShellCommand(
                        mInstrumentation, constructGetModemServiceCommand());
        Log.d(TAG, "getModemService result: " + result);
        return result;
    }

    private boolean clearModemServiceOverride() throws Exception {
        String result =
                TelephonyUtils.executeShellCommand(
                        mInstrumentation, constructClearModemServiceOverrideCommand());
        Log.d(TAG, "clearModemServiceOverride result: " + result);
        return "true".equals(result);
    }

    private boolean isServiceTheSame(String serviceA, String serviceB) {
        if (TextUtils.isEmpty(serviceA) && TextUtils.isEmpty(serviceB)) {
            return true;
        }
        return TextUtils.equals(serviceA, serviceB);
    }

    private void updateModemServiceName(String serviceName) {
        mModemServiceName = serviceName;
    }

    private boolean overrideModemService() throws Exception {
        boolean result = setModemService();

        if (result) mIsServiceOverridden = true;

        return result;
    }

    /**
     * Bind to the local implementation of MockModemService.
     *
     * @return true if this request succeeded, false otherwise.
     */
    boolean connectMockModemServiceLocally() {
        if (!setupLocalMockModemService()) {
            Log.w(TAG, "connectMockModemService: couldn't set up service.");
            return false;
        }
        return true;
    }

    /**
     * Trigger the telephony framework to bind to the local MockModemService implementation.
     *
     * @return true if this request succeeded, false otherwise.
     */
    boolean switchFrameworkConnectionToMockModemService(int[] simprofiles) throws Exception {
        boolean isComplete = false;

        if (overrideModemService()) {
            isComplete =
                    mMockModemService.waitForLatchCountdown(
                            MockModemService.LATCH_RADIO_INTERFACES_READY,
                            BIND_RADIO_INTERFACE_READY_TIMEOUT_MS);

            if (isComplete) {
                isComplete = mMockModemService.initialize(simprofiles);
            } else {
                Log.e(TAG, "Timeout: Wait for radio proxy clients to bind failed!");
            }
        }

        return isComplete;
    }

    boolean checkDefaultModemServiceConnected(String serviceName) throws Exception {
        return isServiceTheSame(COMMAND_MODEM_SERVICE_DEFAULT, serviceName);
    }

    boolean checkModemServiceOverridden(String serviceName) throws Exception {
        return isServiceTheSame(mModemServiceName, serviceName);
    }

    boolean connectMockModemService(int[] simprofiles) throws Exception {
        boolean result = false;
        if (!connectMockModemServiceLocally()) return false;

        result = checkModemServiceOverridden(getModemService());
        if (result) mIsServiceOverridden = true;
        else result = switchFrameworkConnectionToMockModemService(simprofiles);

        return result;
    }

    boolean triggerFrameworkDisconnectionFromMockModemService() throws Exception {
        boolean result = false;
        if (!mIsServiceOverridden) {
            Log.d(TAG, "Service didn't override.");
            return true;
        }

        result = clearModemServiceOverride();
        if (result) mIsServiceOverridden = false;

        return result;
    }

    boolean disconnectMockModemService() throws Exception {
        boolean isComplete;
        isComplete = triggerFrameworkDisconnectionFromMockModemService();

        if (isComplete) {
            // waiting for binding to default modem service
            TimeUnit.SECONDS.sleep(5);
            String serviceName = getModemService();
            isComplete =
                    (!checkModemServiceOverridden(serviceName)
                            && checkDefaultModemServiceConnected(serviceName));
        }

        // Remove local connection
        Log.d(TAG, "disconnectMockModemService");
        if (mMockModemServiceConn != null) {
            mInstrumentation.getContext().unbindService(mMockModemServiceConn);
            mMockModemService = null;
            mMockModemServiceConn = null;
        }

        return isComplete;
    }

    MockModemService getMockModemService() {
        return mMockModemService;
    }
}
