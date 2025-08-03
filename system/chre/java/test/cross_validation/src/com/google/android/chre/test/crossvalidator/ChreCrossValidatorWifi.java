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

package com.google.android.chre.test.crossvalidator;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.location.ContextHubInfo;
import android.hardware.location.ContextHubManager;
import android.hardware.location.ContextHubTransaction;
import android.hardware.location.NanoAppBinary;
import android.hardware.location.NanoAppMessage;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import com.google.android.chre.nanoapp.proto.ChreCrossValidationWifi;
import com.google.android.chre.nanoapp.proto.ChreCrossValidationWifi.Step;
import com.google.android.chre.nanoapp.proto.ChreTestCommon;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.junit.Assert;
import org.junit.Assume;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class ChreCrossValidatorWifi extends ChreCrossValidatorBase {
    private static final long AWAIT_STEP_RESULT_MESSAGE_TIMEOUT_SEC = 7;
    private static final long AWAIT_WIFI_SCAN_RESULT_TIMEOUT_SEC = 30;

    private static final long NANO_APP_ID = 0x476f6f6754000005L;

    private static final int CHRE_SCAN_SIZE_DEFAULT = 100;

    /**
     * Wifi capabilities flags listed in
     * //system/chre/chre_api/include/chre_api/chre/wifi.h
     */
    private static final int WIFI_CAPABILITIES_SCAN_MONITORING = 1;
    private static final int WIFI_CAPABILITIES_ON_DEMAND_SCAN = 2;

    private static final int NUM_BYTES_IN_SCAN_RESULT_BSSID = 6;

    AtomicReference<Step> mStep = new AtomicReference<Step>(Step.INIT);
    AtomicBoolean mDidReceiveNanoAppMessage = new AtomicBoolean(false);

    private AtomicBoolean mApWifiScanSuccess = new AtomicBoolean(false);
    private CountDownLatch mAwaitApWifiSetupScan = new CountDownLatch(1);

    private WifiManager mWifiManager;
    private BroadcastReceiver mWifiScanReceiver;

    private AtomicReference<ChreCrossValidationWifi.WifiCapabilities> mWifiCapabilities =
            new AtomicReference<ChreCrossValidationWifi.WifiCapabilities>(null);

    private AtomicBoolean mWifiScanResultsCompareFinalResult = new AtomicBoolean(false);
    private AtomicReference<String> mWifiScanResultsCompareFinalErrorMessage =
            new AtomicReference<String>(null);

    public ChreCrossValidatorWifi(
            ContextHubManager contextHubManager, ContextHubInfo contextHubInfo,
            NanoAppBinary nanoAppBinary) {
        super(contextHubManager, contextHubInfo, nanoAppBinary);
        Assert.assertTrue("Nanoapp given to cross validator is not the designated chre cross"
                + " validation nanoapp.",
                nanoAppBinary.getNanoAppId() == NANO_APP_ID);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        mWifiScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                Log.i(TAG, "onReceive called");
                boolean success = intent.getBooleanExtra(
                        WifiManager.EXTRA_RESULTS_UPDATED, false);
                mApWifiScanSuccess.set(success);
                mAwaitApWifiSetupScan.countDown();
            }
        };
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        context.registerReceiver(mWifiScanReceiver, intentFilter);
    }

    @Override public void validate() throws AssertionError, InterruptedException {
        mCollectingData.set(true);
        sendStepStartMessage(Step.CAPABILITIES);
        waitForMessageFromNanoapp();
        mCollectingData.set(false);
        Assume.assumeTrue("Chre wifi is not enabled",
                          chreWifiHasCapabilities(mWifiCapabilities.get()));

        mCollectingData.set(true);
        sendSetupMessage(CHRE_SCAN_SIZE_DEFAULT);

        waitForMessageFromNanoapp();
        mCollectingData.set(false);

        mCollectingData.set(true);
        sendStepStartMessage(Step.VALIDATE);

        Assert.assertTrue("Wifi manager start scan failed", mWifiManager.startScan());
        waitForApScanResults();
        sendWifiScanResultsToChre();

        waitForMessageFromNanoapp();
        mCollectingData.set(false);
    }


    /**
     * Send step start message to nanoapp.
     */
    private void sendStepStartMessage(Step step) {
        sendStepStartMessage(step, makeStepStartMessage(step));
    }

    private void sendStepStartMessage(Step step, NanoAppMessage message) {
        mStep.set(step);
        sendMessageToNanoApp(message);
    }

    /**
     * Send message to the nanoapp.
     */
    private void sendMessageToNanoApp(NanoAppMessage message) {
        int result = mContextHubClient.sendMessageToNanoApp(message);
        if (result != ContextHubTransaction.RESULT_SUCCESS) {
            Assert.fail("Collect data from CHRE failed with result "
                    + contextHubTransactionResultToString(result)
                    + " while trying to send start message during "
                    + getCurrentStepName() + " phase.");
        }
    }

    /**
    * @return The nanoapp message used to start the data collection in chre.
    */
    private NanoAppMessage makeStepStartMessage(Step step) {
        int messageType = ChreCrossValidationWifi.MessageType.STEP_START_VALUE;
        ChreCrossValidationWifi.StepStartCommand stepStartCommand =
                ChreCrossValidationWifi.StepStartCommand.newBuilder()
                .setStep(step).build();
        return NanoAppMessage.createMessageToNanoApp(
                mNappBinary.getNanoAppId(), messageType, stepStartCommand.toByteArray());
    }

    /**
    * Send SETUP message to the nanoapp with the chre scan size
    */
    private void sendSetupMessage(int chreScanSize) {
        int messageType = ChreCrossValidationWifi.MessageType.STEP_START_VALUE;
        ChreCrossValidationWifi.StepStartCommand stepStartCommand =
                ChreCrossValidationWifi.StepStartCommand.newBuilder()
                .setStep(Step.SETUP).setChreScanCapacity(chreScanSize).build();
        NanoAppMessage message = NanoAppMessage.createMessageToNanoApp(
                mNappBinary.getNanoAppId(), messageType, stepStartCommand.toByteArray());
        sendStepStartMessage(Step.SETUP, message);
    }

    /**
     * Wait for a messaage from the nanoapp.
     */
    private void waitForMessageFromNanoapp() throws InterruptedException {
        boolean success =
                mAwaitDataLatch.await(AWAIT_STEP_RESULT_MESSAGE_TIMEOUT_SEC, TimeUnit.SECONDS);
        Assert.assertTrue("Timeout waiting for signal: wait for message from nanoapp", success);
        mAwaitDataLatch = new CountDownLatch(1);
        Assert.assertTrue("Timed out while waiting for step result in " + getCurrentStepName()
                + " step", mDidReceiveNanoAppMessage.get());
        mDidReceiveNanoAppMessage.set(false);
        if (mErrorStr.get() != null) {
            Assert.fail(mErrorStr.get());
        }
    }

    /**
     * @param capabilities The wifi capabilities message from CHRE.
     * @return true if CHRE wifi has the necessary capabilities to run the test.
     */
    private boolean chreWifiHasCapabilities(ChreCrossValidationWifi.WifiCapabilities capabilities) {
        return (capabilities.getWifiCapabilities() & WIFI_CAPABILITIES_SCAN_MONITORING) != 0
            && (capabilities.getWifiCapabilities() & WIFI_CAPABILITIES_ON_DEMAND_SCAN) != 0;
    }

    private void waitForApScanResults() throws InterruptedException {
        boolean success =
                mAwaitApWifiSetupScan.await(AWAIT_WIFI_SCAN_RESULT_TIMEOUT_SEC, TimeUnit.SECONDS);
        Assert.assertTrue("Timeout waiting for signal: wait for ap scan results", success);
        Assert.assertTrue("AP wifi scan result failed asynchronously", mApWifiScanSuccess.get());
    }

    private void sendWifiScanResultsToChre() {
        List<ScanResult> results = mWifiManager.getScanResults();
        Assert.assertTrue("No wifi scan results returned from AP", !results.isEmpty());

        // CHRE does not currently support 6 GHz results, so filter these results from the list
        int logsRemoved = 0;
        Iterator<ScanResult> iter = results.iterator();
        while (iter.hasNext()) {
            ScanResult current = iter.next();
            if (current.getBand() == ScanResult.WIFI_BAND_6_GHZ) {
                iter.remove();
                logsRemoved++;
            }
        }
        if (logsRemoved > 0) {
            Log.i(TAG, "Filtering out 6 GHz band scan result for CHRE, total=" + logsRemoved);
        }

        for (int i = 0; i < results.size(); i++) {
            sendMessageToNanoApp(makeWifiScanResultMessage(results.get(i), results.size(), i));
        }
    }

    private NanoAppMessage makeWifiScanResultMessage(ScanResult result, int totalNumResults,
                                                     int resultIndex) {
        int messageType = ChreCrossValidationWifi.MessageType.SCAN_RESULT_VALUE;
        ChreCrossValidationWifi.WifiScanResult scanResult = ChreCrossValidationWifi.WifiScanResult
                .newBuilder().setSsid(result.SSID)
                .setBssid(ByteString.copyFrom(bssidToBytes(result.BSSID)))
                .setTotalNumResults(totalNumResults).setResultIndex(resultIndex).build();
        NanoAppMessage message = NanoAppMessage.createMessageToNanoApp(
                mNappBinary.getNanoAppId(), messageType, scanResult.toByteArray());
        return message;
    }

    @Override
    protected void parseDataFromNanoAppMessage(NanoAppMessage message) {
        mDidReceiveNanoAppMessage.set(true);
        if (message.getMessageType()
                == ChreCrossValidationWifi.MessageType.STEP_RESULT_VALUE) {
            ChreTestCommon.TestResult testResult = null;
            try {
                testResult = ChreTestCommon.TestResult.parseFrom(message.getMessageBody());
            } catch (InvalidProtocolBufferException e) {
                setErrorStr("Error parsing protobuff: " + e);
                mAwaitDataLatch.countDown();
                return;
            }
            boolean success = getSuccessFromTestResult(testResult);
            if (mStep.get() == Step.SETUP || mStep.get() == Step.VALIDATE) {
                if (success) {
                    Log.i(TAG, getCurrentStepName() + " step success");
                } else {
                    setErrorStr(getCurrentStepName() + " step failed: "
                            + testResult.getErrorMessage().toStringUtf8());
                }
            } else {
                setErrorStr("Received a step result message during step " + getCurrentStepName());
            }
        } else if (message.getMessageType()
                == ChreCrossValidationWifi.MessageType.WIFI_CAPABILITIES_VALUE) {
            if (mStep.get() != Step.CAPABILITIES) {
                setErrorStr("Received a capabilities message during step " + getCurrentStepName());
            }
            ChreCrossValidationWifi.WifiCapabilities capabilities = null;
            try {
                capabilities = ChreCrossValidationWifi.WifiCapabilities.parseFrom(
                        message.getMessageBody());
            } catch (InvalidProtocolBufferException e) {
                setErrorStr("Error parsing protobuff: " + e);
                mAwaitDataLatch.countDown();
                return;
            }
            mWifiCapabilities.set(capabilities);
        } else {
            setErrorStr(String.format("Received message with unexpected type: %d",
                                      message.getMessageType()));
        }
        // Each message should countdown the latch no matter success or fail
        mAwaitDataLatch.countDown();
    }

    /**
     * @return The boolean indicating test result success or failure from TestResult proto message.
     */
    private boolean getSuccessFromTestResult(ChreTestCommon.TestResult testResult) {
        return testResult.getCode() == ChreTestCommon.TestResult.Code.PASSED;
    }

    /**
     * @return The string name of the current phase.
     */
    private String getCurrentStepName() {
        switch (mStep.get()) {
            case INIT:
                return "INIT";
            case SETUP:
                return "SETUP";
            case VALIDATE:
                return "VALIDATE";
            default:
                return "UNKNOWN";
        }
    }

    private static byte[] bssidToBytes(String bssid) {
        String expectedBssidFormat =
                String.join(":", Collections.nCopies(NUM_BYTES_IN_SCAN_RESULT_BSSID, "ff"));
        Assert.assertTrue(
                String.format("Bssid did not match expected format %s bssid = %s",
                expectedBssidFormat, bssid), verifyBssid(bssid));
        // the ScanResult.BSSID field comes in format ff:ff:ff:ff:ff:ff and needs to be converted to
        // bytes in order to be compared to CHRE bssid
        String hexStringNoColon = bssid.replace(":" , "");
        byte[] bytes = new byte[NUM_BYTES_IN_SCAN_RESULT_BSSID];
        for (int i = 0; i < 6; i++) {
            // Shift first byte digit left bitwise to raise value than add second digit of byte.
            bytes[i] =
                    (byte) ((Character.digit(hexStringNoColon.charAt(i * 2), 16) << 4)
                    + Character.digit(hexStringNoColon.charAt(i * 2 + 1), 16));
        }
        return bytes;
    }

    /**
     * Verify that the BSSID field from AP Wifi scan results is of the format:
     * ff:ff:ff:.. where the number of bytes should equal to NUM_BYTES_IN_SCAN_RESULTS_BSSID
     * and there should be a ':' between each byte.
     *
     * @param bssid The bssid field to verify.
     */
    private static boolean verifyBssid(String bssid) {
        boolean passedVerification = (bssid.length() == NUM_BYTES_IN_SCAN_RESULT_BSSID * 3 - 1);
        for (int i = 0; passedVerification && i < bssid.length(); i += 3) {
            if ((Character.digit(bssid.charAt(i), 16) == -1)
                    || (Character.digit(bssid.charAt(i + 1), 16) == -1)
                    || ((i + 2 < bssid.length()) && (bssid.charAt(i + 2) != ':'))) {
                passedVerification = false;
                break;
            }
        }
        return passedVerification;
    }

    // TODO: Implement this method
    @Override
    protected void unregisterApDataListener() {}
}
