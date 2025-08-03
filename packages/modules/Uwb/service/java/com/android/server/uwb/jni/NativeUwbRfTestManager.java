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

/* NativeUwbRfTestManager is unused now*/
/*package com.android.server.uwb.jni;

import android.util.Log;

import com.android.server.uwb.test.UwbTestLoopBackTestResult;
import com.android.server.uwb.test.UwbTestPeriodicTxResult;
import com.android.server.uwb.test.UwbTestRxPacketErrorRateResult;
import com.android.server.uwb.test.UwbTestRxResult;

public class NativeUwbRfTestManager {
    private static final String TAG = NativeUwbRfTestManager.class.getSimpleName();

    protected INativeUwbManager.RfTestNotification mRfTestListener;

    public NativeUwbRfTestManager() {
        nativeInit();
    }

    public void setDeviceListener(INativeUwbManager.RfTestNotification rftestListener) {
        mRfTestListener = rftestListener;
    }

    public void onPeriodicTxDataNotificationReceived(UwbTestPeriodicTxResult periodicTxTestResult) {
        Log.d(TAG, "onPeriodicTxDataNotificationReceived : " + periodicTxTestResult);
        mRfTestListener.onPeriodicTxDataNotificationReceived(periodicTxTestResult);
    }

    public void onPerRxDataNotificationReceived(UwbTestRxPacketErrorRateResult perRxTestResult) {
        Log.d(TAG, "onPerRxDataNotificationReceived : " + perRxTestResult);
        mRfTestListener.onPerRxDataNotificationReceived(perRxTestResult);
    }

    public void onLoopBackTestDataNotificationReceived(UwbTestLoopBackTestResult loopBackResult) {
        Log.d(TAG, "onLoopBackTestDataNotificationReceived : " + loopBackResult);
        mRfTestListener.onLoopBackTestDataNotificationReceived(loopBackResult);
    }

    public void onRxTestDataNotificationReceived(UwbTestRxResult rxTestResult) {
        Log.d(TAG, "onRxTestDataNotificationReceived : " + rxTestResult);
        mRfTestListener.onRxTestDataNotificationReceived(rxTestResult);
    }

    public synchronized byte[] setRfTestConfigurations(int sessionId, int noOfParams,
            int testConfigParamLen, byte[] testConfigParams) {
        return nativeSetTestConfigurations(
                sessionId, noOfParams, testConfigParamLen, testConfigParams);
    }

    public synchronized byte[] getRfTestConfigurations(int sessionId, int noOfParams,
            int testConfigParamLen, byte[] testConfigParams) {
        return nativeGetTestConfigurations(
                sessionId, noOfParams, testConfigParamLen, testConfigParams);
    }

    public synchronized byte startPeriodicTxTest(byte[] psduData) {
        return nativeStartPeriodicTxTest(psduData);
    }

    public synchronized byte startPerRxTest(byte[] refPsduData) {
        return nativeStartPerRxTest(refPsduData);
    }

    public synchronized byte startUwbLoopBackTest(byte[] psduData) {
        return nativeStartUwbLoopBackTest(psduData);
    }

    public synchronized byte startRxTest() {
        return nativeStartRxTest();
    }

    public synchronized byte stopRfTest() {
        return nativeStopRfTest();
    }

    private native boolean nativeInit();
    private native byte nativeStopRfTest();
    private native byte nativeStartPerRxTest(byte[] refPsduData);
    private native byte nativeStartPeriodicTxTest(byte[] psduData);
    private native byte nativeStartUwbLoopBackTest(byte[] psduData);
    private native byte nativeStartRxTest();
    private native byte[] nativeSetTestConfigurations(int sessionId, int noOfParams,
            int testConfigParamLen, byte[] testConfigParams);
    private native byte[] nativeGetTestConfigurations(int sessionId, int noOfParams,
            int testConfigParamLen, byte[] testConfigParams);
}*/
