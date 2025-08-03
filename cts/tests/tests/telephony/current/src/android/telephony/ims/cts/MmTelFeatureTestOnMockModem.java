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

package android.telephony.ims.cts;

import static android.telephony.AccessNetworkConstants.AccessNetworkType.EUTRAN;
import static android.telephony.AccessNetworkConstants.AccessNetworkType.IWLAN;
import static android.telephony.CarrierConfigManager.ImsSs.CALL_WAITING_SYNC_NONE;
import static android.telephony.CarrierConfigManager.ImsSs.KEY_TERMINAL_BASED_CALL_WAITING_SYNC_TYPE_INT;
import static android.telephony.CarrierConfigManager.ImsSs.KEY_UT_TERMINAL_BASED_SERVICES_INT_ARRAY;
import static android.telephony.CarrierConfigManager.ImsSs.SUPPLEMENTARY_SERVICE_CW;
import static android.telephony.CarrierConfigManager.KEY_USE_CALL_WAITING_USSD_BOOL;
import static android.telephony.TelephonyManager.SRVCC_STATE_HANDOVER_CANCELED;
import static android.telephony.TelephonyManager.SRVCC_STATE_HANDOVER_COMPLETED;
import static android.telephony.TelephonyManager.SRVCC_STATE_HANDOVER_FAILED;
import static android.telephony.TelephonyManager.SRVCC_STATE_HANDOVER_STARTED;
import static android.telephony.ims.feature.ConnectionFailureInfo.REASON_RF_BUSY;
import static android.telephony.ims.feature.ConnectionFailureInfo.REASON_RRC_TIMEOUT;
import static android.telephony.ims.feature.MmTelFeature.EPS_FALLBACK_REASON_INVALID;
import static android.telephony.ims.feature.MmTelFeature.EPS_FALLBACK_REASON_NO_NETWORK_RESPONSE;
import static android.telephony.ims.feature.MmTelFeature.EPS_FALLBACK_REASON_NO_NETWORK_TRIGGER;
import static android.telephony.ims.feature.MmTelFeature.IMS_TRAFFIC_DIRECTION_OUTGOING;
import static android.telephony.ims.feature.MmTelFeature.IMS_TRAFFIC_TYPE_EMERGENCY;
import static android.telephony.ims.feature.MmTelFeature.IMS_TRAFFIC_TYPE_REGISTRATION;
import static android.telephony.ims.feature.MmTelFeature.IMS_TRAFFIC_TYPE_SMS;
import static android.telephony.ims.feature.MmTelFeature.IMS_TRAFFIC_TYPE_VIDEO;
import static android.telephony.ims.feature.MmTelFeature.IMS_TRAFFIC_TYPE_VOICE;
import static android.telephony.mockmodem.MockImsService.LATCH_WAIT_FOR_START_IMS_TRAFFIC;
import static android.telephony.mockmodem.MockImsService.LATCH_WAIT_FOR_STOP_IMS_TRAFFIC;
import static android.telephony.mockmodem.MockImsService.LATCH_WAIT_FOR_TRIGGER_EPS_FALLBACK;
import static android.telephony.mockmodem.MockSimService.MOCK_SIM_PROFILE_ID_TWN_CHT;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.ims.ImsService;
import android.telephony.ims.feature.ConnectionFailureInfo;
import android.telephony.ims.feature.ImsFeature;
import android.telephony.ims.feature.ImsTrafficSessionCallback;
import android.telephony.ims.stub.ImsFeatureConfiguration;
import android.telephony.mockmodem.MockModemManager;
import android.util.Log;
import android.util.Pair;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * CTS tests for MmTelFeature API.
 */
@RunWith(AndroidJUnit4.class)
public class MmTelFeatureTestOnMockModem {
    private static final String LOG_TAG = "MmTelFeatureTestOnMockModem";
    private static final boolean VDBG = false;

    private static final int FEATURE_STATE_READY = 0;

    // the timeout to wait for result in milliseconds
    private static final int WAIT_UPDATE_TIMEOUT_MS = 2000;

    // the timeout to wait for request in milliseconds
    private static final int WAIT_REQUEST_TIMEOUT_MS = 1000;

    // the timeout to wait for sim state change in milliseconds
    private static final int WAIT_SIM_STATE_TIMEOUT_MS = 3000;

    private static ImsServiceConnector sServiceConnector;
    private static CarrierConfigReceiver sReceiver;
    private static MockModemManager sMockModemManager;

    private static int sTestSlot = 0;
    private static int sTestSub = SubscriptionManager.INVALID_SUBSCRIPTION_ID;

    private static boolean sSupportsImsHal = false;

    private abstract static class BaseReceiver extends BroadcastReceiver {
        protected CountDownLatch mLatch = new CountDownLatch(1);

        void clearQueue() {
            mLatch = new CountDownLatch(1);
        }

        void waitForChanged() throws Exception {
            mLatch.await(5000, TimeUnit.MILLISECONDS);
        }
    }

    private static class CarrierConfigReceiver extends BaseReceiver {
        private final int mSubId;

        CarrierConfigReceiver(int subId) {
            mSubId = subId;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED.equals(intent.getAction())) {
                int subId = intent.getIntExtra(CarrierConfigManager.EXTRA_SUBSCRIPTION_INDEX, -1);
                if (mSubId == subId) {
                    mLatch.countDown();
                }
            }
        }
    }

    @BeforeClass
    public static void beforeAllTests() throws Exception {
        if (VDBG) Log.d(LOG_TAG, "beforeAllTests");

        if (!hasFeature(PackageManager.FEATURE_TELEPHONY)) {
            return;
        }

        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        TelephonyManager tm = (TelephonyManager) getContext()
                .getSystemService(Context.TELEPHONY_SERVICE);

        Pair<Integer, Integer> halVersion = tm.getHalVersion(TelephonyManager.HAL_SERVICE_IMS);
        if (!(halVersion.equals(TelephonyManager.HAL_VERSION_UNKNOWN)
                || halVersion.equals(TelephonyManager.HAL_VERSION_UNSUPPORTED))) {
            sSupportsImsHal = true;
        }

        MockModemManager.enforceMockModemDeveloperSetting();
        sMockModemManager = new MockModemManager();
        assertNotNull(sMockModemManager);
        assertTrue(sMockModemManager.connectMockModemService(MOCK_SIM_PROFILE_ID_TWN_CHT));

        TimeUnit.MILLISECONDS.sleep(WAIT_SIM_STATE_TIMEOUT_MS);

        int simCardState = tm.getSimCardState();
        assertEquals(TelephonyManager.SIM_STATE_PRESENT, simCardState);

        TimeUnit.MILLISECONDS.sleep(WAIT_SIM_STATE_TIMEOUT_MS);

        // Check SIM state ready
        simCardState = tm.getSimState();
        assertEquals(TelephonyManager.SIM_STATE_READY, simCardState);

        sTestSub = ImsUtils.getPreferredActiveSubId();

        int subId = SubscriptionManager.getSubscriptionId(sTestSlot);
        assertTrue(SubscriptionManager.isValidSubscriptionId(subId));
        sTestSub = subId;

        sServiceConnector = new ImsServiceConnector(InstrumentationRegistry.getInstrumentation());

        // Remove all live ImsServices until after these tests are done
        sServiceConnector.clearAllActiveImsServices(sTestSlot);

        // Configure SMS receiver based on the Android version.
        sServiceConnector.setDefaultSmsApp();

        sReceiver = new CarrierConfigReceiver(sTestSub);
        IntentFilter filter = new IntentFilter(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        // ACTION_CARRIER_CONFIG_CHANGED is sticky, so we will get a callback right away.
        InstrumentationRegistry.getInstrumentation().getContext()
                .registerReceiver(sReceiver, filter);
    }

    @AfterClass
    public static void afterAllTests() throws Exception {
        if (VDBG) Log.d(LOG_TAG, "afterAllTests");

        if (!hasFeature(PackageManager.FEATURE_TELEPHONY)) {
            return;
        }

        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        // Restore all ImsService configurations that existed before the test.
        if (sServiceConnector != null) {
            sServiceConnector.disconnectServices();
        }
        sServiceConnector = null;

        // Ensure there are no CarrierConfig overrides as well as reset the ImsResolver in case the
        // ImsService override changed in CarrierConfig while we were overriding it.
        overrideCarrierConfig(null);

        if (sReceiver != null) {
            InstrumentationRegistry.getInstrumentation().getContext().unregisterReceiver(sReceiver);
            sReceiver = null;
        }

        // Rebind all interfaces which is binding to MockModemService to default.
        if (sMockModemManager != null) {
            //assertNotNull(sMockModemManager);
            assertTrue(sMockModemManager.disconnectMockModemService());
            sMockModemManager = null;

            TimeUnit.MILLISECONDS.sleep(WAIT_SIM_STATE_TIMEOUT_MS);
        }
    }

    @Before
    public void beforeTest() {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY));
        assumeTrue(ImsUtils.shouldTestImsService());

        if (sMockModemManager != null) {
            sMockModemManager.clearImsTrafficState();
            sMockModemManager.resetImsAllLatchCountdown();
        }
    }

    @After
    public void afterTest() throws Exception {
        if (VDBG) Log.d(LOG_TAG, "afterTest");

        // Unbind the ImsService after the test completes.
        if (sServiceConnector != null) {
            TestImsService imsService = sServiceConnector.getCarrierService();
            sServiceConnector.setSingleRegistrationTestModeEnabled(false);
            sServiceConnector.disconnectCarrierImsService();
            sServiceConnector.disconnectDeviceImsService();
            if (imsService != null) imsService.waitForExecutorFinish();
        }
    }

    @Test
    public void testSetTerminalBasedCallWaitingNotSupported() throws Exception {
        if (VDBG) Log.d(LOG_TAG, "testSetTerminalBasedCallWaitingNotSupported");

        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_CALLING));

        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        PersistableBundle b = new PersistableBundle();
        b.putIntArray(KEY_UT_TERMINAL_BASED_SERVICES_INT_ARRAY, new int[] {});
        overrideCarrierConfig(b);

        // Connect to device ImsService with MmTelFeature
        triggerFrameworkConnectToCarrierImsService(
                ImsService.CAPABILITY_TERMINAL_BASED_CALL_WAITING);

        sServiceConnector.getCarrierService().getMmTelFeature()
                .getTerminalBasedCallWaitingLatch().await(WAIT_UPDATE_TIMEOUT_MS,
                        TimeUnit.MILLISECONDS);

        assertFalse(sServiceConnector.getCarrierService()
                .getMmTelFeature().isTerminalBasedCallWaitingNotified());

        sServiceConnector.getCarrierService().getMmTelFeature()
                .resetTerminalBasedCallWaitingLatch();
    }

    @Test
    public void testSetTerminalBasedCallWaiting() throws Exception {
        if (VDBG) Log.d(LOG_TAG, "testSetTerminalBasedCallWaiting");

        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_CALLING));

        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        PersistableBundle b = new PersistableBundle();
        b.putBoolean(KEY_USE_CALL_WAITING_USSD_BOOL, false);
        b.putInt(KEY_TERMINAL_BASED_CALL_WAITING_SYNC_TYPE_INT, CALL_WAITING_SYNC_NONE);
        b.putIntArray(KEY_UT_TERMINAL_BASED_SERVICES_INT_ARRAY,
                new int[] { SUPPLEMENTARY_SERVICE_CW });
        overrideCarrierConfig(b);

        // Connect to device ImsService with MmTelFeature
        triggerFrameworkConnectToCarrierImsService(
                ImsService.CAPABILITY_TERMINAL_BASED_CALL_WAITING);

        sServiceConnector.getCarrierService().getMmTelFeature()
                .getTerminalBasedCallWaitingLatch().await(WAIT_UPDATE_TIMEOUT_MS,
                        TimeUnit.MILLISECONDS);

        assertTrue(sServiceConnector.getCarrierService()
                .getMmTelFeature().isTerminalBasedCallWaitingNotified());

        boolean enabled = sServiceConnector.getCarrierService()
                .getMmTelFeature().isTerminalBasedCallWaitingEnabled();

        sServiceConnector.getCarrierService().getMmTelFeature()
                .resetTerminalBasedCallWaitingLatch();

        TelephonyManager tm = (TelephonyManager) getContext()
                .getSystemService(Context.TELEPHONY_SERVICE);
        tm = tm.createForSubscriptionId(sTestSub);

        boolean expected = !enabled;
        final LinkedBlockingQueue<Integer> resultQueue = new LinkedBlockingQueue<>();
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(tm,
                (m) -> m.setCallWaitingEnabled(expected,
                        (r) -> r.run(), (result) -> resultQueue.offer(result)),
                "android.permission.MODIFY_PHONE_STATE");
        int result = waitForIntResult(resultQueue);

        if (result == TelephonyManager.CALL_WAITING_STATUS_ENABLED
                || result == TelephonyManager.CALL_WAITING_STATUS_DISABLED) {
            sServiceConnector.getCarrierService().getMmTelFeature()
                    .getTerminalBasedCallWaitingLatch().await(WAIT_UPDATE_TIMEOUT_MS,
                            TimeUnit.MILLISECONDS);

            assertTrue(sServiceConnector.getCarrierService()
                    .getMmTelFeature().isTerminalBasedCallWaitingNotified());

            enabled = sServiceConnector.getCarrierService()
                    .getMmTelFeature().isTerminalBasedCallWaitingEnabled();

            assertEquals(expected, enabled);

            sServiceConnector.getCarrierService().getMmTelFeature()
                    .resetTerminalBasedCallWaitingLatch();
        } else {
            fail("setCallWaitingEnabled failed");
        }
    }

    @Test
    public void testNotifySrvccState() throws Exception {
        if (VDBG) Log.d(LOG_TAG, "testNotifySrvccState");

        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_CALLING));

        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        // Connect to device ImsService with MmTelFeature
        triggerFrameworkConnectToCarrierImsService(0);

        verifySrvccStateChange(SRVCC_STATE_HANDOVER_STARTED);
        verifySrvccStateChange(SRVCC_STATE_HANDOVER_COMPLETED);

        verifySrvccStateChange(SRVCC_STATE_HANDOVER_STARTED);
        verifySrvccStateChange(SRVCC_STATE_HANDOVER_FAILED);

        verifySrvccStateChange(SRVCC_STATE_HANDOVER_STARTED);
        verifySrvccStateChange(SRVCC_STATE_HANDOVER_CANCELED);

        sServiceConnector.getCarrierService().getMmTelFeature().resetSrvccState();
    }

    @Ignore("Internal use only. Ignore this test until system API is added")
    @Test
    public void testTriggerEpsFallback() throws Exception {
        if (VDBG) Log.d(LOG_TAG, "testTriggerEpsFallback");

        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY_CALLING));

        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        assumeTrue(sSupportsImsHal);

        // Connect to device ImsService with MmTelFeature
        triggerFrameworkConnectToCarrierImsService(0);

        sMockModemManager.resetEpsFallbackReason(sTestSlot);

        assertEquals(EPS_FALLBACK_REASON_INVALID,
                sMockModemManager.getEpsFallbackReason(sTestSlot));

        sServiceConnector.getCarrierService().getMmTelFeature().triggerEpsFallback(
                EPS_FALLBACK_REASON_NO_NETWORK_TRIGGER);
        assertTrue(waitForMockImsStateLatchCountdown(LATCH_WAIT_FOR_TRIGGER_EPS_FALLBACK));

        assertEquals(EPS_FALLBACK_REASON_NO_NETWORK_TRIGGER,
                sMockModemManager.getEpsFallbackReason(sTestSlot));

        sServiceConnector.getCarrierService().getMmTelFeature().triggerEpsFallback(
                EPS_FALLBACK_REASON_NO_NETWORK_RESPONSE);
        assertTrue(waitForMockImsStateLatchCountdown(LATCH_WAIT_FOR_TRIGGER_EPS_FALLBACK));

        assertEquals(EPS_FALLBACK_REASON_NO_NETWORK_RESPONSE,
                sMockModemManager.getEpsFallbackReason(sTestSlot));
    }

    @Ignore("Internal use only. Ignore this test until system API is added")
    @Test
    public void testStartAndStopImsTrafficSession() throws Exception {
        if (VDBG) Log.d(LOG_TAG, "testStartAndStopImsTrafficSession");

        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        assumeTrue(sSupportsImsHal);

        sMockModemManager.blockStartImsTrafficResponse(sTestSlot, true);

        // Connect to device ImsService with MmTelFeature
        triggerFrameworkConnectToCarrierImsService(0);

        // Emergency call traffic
        LinkedBlockingQueue<ConnectionFailureInfo> resultQueue = new LinkedBlockingQueue<>();
        ImsTrafficSessionCallback callback = buildImsTrafficSessionCallback(resultQueue);

        sServiceConnector.getCarrierService().getMmTelFeature().startImsTrafficSession(
                IMS_TRAFFIC_TYPE_EMERGENCY, EUTRAN, IMS_TRAFFIC_DIRECTION_OUTGOING,
                getContext().getMainExecutor(), callback);
        assertTrue(waitForMockImsStateLatchCountdown(LATCH_WAIT_FOR_START_IMS_TRAFFIC));

        assertTrue(sMockModemManager.isImsTrafficStarted(sTestSlot, IMS_TRAFFIC_TYPE_EMERGENCY));

        assertTrue(sMockModemManager.sendStartImsTrafficResponse(sTestSlot,
                IMS_TRAFFIC_TYPE_EMERGENCY, 0, 0, 0));

        ConnectionFailureInfo result = waitForResult(resultQueue);

        assertNotNull(result);
        assertEquals(0, result.getReason());

        sServiceConnector.getCarrierService().getMmTelFeature().stopImsTrafficSession(callback);
        assertTrue(waitForMockImsStateLatchCountdown(LATCH_WAIT_FOR_STOP_IMS_TRAFFIC));

        assertFalse(sMockModemManager.isImsTrafficStarted(sTestSlot, IMS_TRAFFIC_TYPE_EMERGENCY));

        // Multiple traffic types simultaneously, voice and registration
        LinkedBlockingQueue<ConnectionFailureInfo> voiceResultQueue = new LinkedBlockingQueue<>();
        ImsTrafficSessionCallback voiceCallback = buildImsTrafficSessionCallback(voiceResultQueue);

        sServiceConnector.getCarrierService().getMmTelFeature().startImsTrafficSession(
                IMS_TRAFFIC_TYPE_VOICE, EUTRAN, IMS_TRAFFIC_DIRECTION_OUTGOING,
                getContext().getMainExecutor(), voiceCallback);
        assertTrue(waitForMockImsStateLatchCountdown(LATCH_WAIT_FOR_START_IMS_TRAFFIC));

        assertTrue(sMockModemManager.isImsTrafficStarted(sTestSlot, IMS_TRAFFIC_TYPE_VOICE));
        assertFalse(sMockModemManager.isImsTrafficStarted(sTestSlot,
                IMS_TRAFFIC_TYPE_REGISTRATION));

        // handover of voice traffic
        sServiceConnector.getCarrierService().getMmTelFeature().modifyImsTrafficSession(
                IWLAN, voiceCallback);
        assertTrue(waitForMockImsStateLatchCountdown(LATCH_WAIT_FOR_START_IMS_TRAFFIC));

        LinkedBlockingQueue<ConnectionFailureInfo> regResultQueue = new LinkedBlockingQueue<>();
        ImsTrafficSessionCallback regCallback = buildImsTrafficSessionCallback(regResultQueue);

        sServiceConnector.getCarrierService().getMmTelFeature().startImsTrafficSession(
                IMS_TRAFFIC_TYPE_REGISTRATION, EUTRAN, IMS_TRAFFIC_DIRECTION_OUTGOING,
                getContext().getMainExecutor(), regCallback);
        assertTrue(waitForMockImsStateLatchCountdown(LATCH_WAIT_FOR_START_IMS_TRAFFIC));

        assertTrue(sMockModemManager.isImsTrafficStarted(sTestSlot, IMS_TRAFFIC_TYPE_VOICE));
        assertTrue(sMockModemManager.isImsTrafficStarted(sTestSlot, IMS_TRAFFIC_TYPE_REGISTRATION));

        assertTrue(sMockModemManager.sendStartImsTrafficResponse(sTestSlot,
                IMS_TRAFFIC_TYPE_VOICE, 0, 0, 0));

        result = waitForResult(voiceResultQueue);

        assertNotNull(result);
        assertEquals(0, result.getReason());

        sServiceConnector.getCarrierService().getMmTelFeature()
                .stopImsTrafficSession(voiceCallback);
        assertTrue(waitForMockImsStateLatchCountdown(LATCH_WAIT_FOR_STOP_IMS_TRAFFIC));

        assertFalse(sMockModemManager.isImsTrafficStarted(sTestSlot, IMS_TRAFFIC_TYPE_VOICE));
        assertTrue(sMockModemManager.isImsTrafficStarted(sTestSlot, IMS_TRAFFIC_TYPE_REGISTRATION));

        assertTrue(sMockModemManager.sendStartImsTrafficResponse(sTestSlot,
                IMS_TRAFFIC_TYPE_REGISTRATION, 0, 0, 0));

        result = waitForResult(regResultQueue);

        assertNotNull(result);
        assertEquals(0, result.getReason());

        sServiceConnector.getCarrierService().getMmTelFeature().stopImsTrafficSession(regCallback);
        assertTrue(waitForMockImsStateLatchCountdown(LATCH_WAIT_FOR_STOP_IMS_TRAFFIC));

        assertFalse(sMockModemManager.isImsTrafficStarted(sTestSlot, IMS_TRAFFIC_TYPE_VOICE));
        assertFalse(sMockModemManager.isImsTrafficStarted(sTestSlot,
                IMS_TRAFFIC_TYPE_REGISTRATION));

        // startImsTrafficSession fails
        resultQueue = new LinkedBlockingQueue<>();
        callback = buildImsTrafficSessionCallback(resultQueue);

        sServiceConnector.getCarrierService().getMmTelFeature().startImsTrafficSession(
                IMS_TRAFFIC_TYPE_SMS, EUTRAN, IMS_TRAFFIC_DIRECTION_OUTGOING,
                getContext().getMainExecutor(), callback);
        assertTrue(waitForMockImsStateLatchCountdown(LATCH_WAIT_FOR_START_IMS_TRAFFIC));

        assertTrue(sMockModemManager.isImsTrafficStarted(sTestSlot, IMS_TRAFFIC_TYPE_SMS));

        assertTrue(sMockModemManager.sendStartImsTrafficResponse(sTestSlot,
                IMS_TRAFFIC_TYPE_SMS, REASON_RF_BUSY, 0, -1));

        result = waitForResult(resultQueue);

        assertNotNull(result);
        assertEquals(REASON_RF_BUSY, result.getReason());
        assertEquals(0, result.getCauseCode());
        assertEquals(-1, result.getWaitTimeMillis());

        sServiceConnector.getCarrierService().getMmTelFeature().stopImsTrafficSession(callback);
        assertTrue(waitForMockImsStateLatchCountdown(LATCH_WAIT_FOR_STOP_IMS_TRAFFIC));

        assertFalse(sMockModemManager.isImsTrafficStarted(sTestSlot, IMS_TRAFFIC_TYPE_SMS));

        // onConnectionFailureInfo notified
        resultQueue = new LinkedBlockingQueue<>();
        callback = buildImsTrafficSessionCallback(resultQueue);

        sServiceConnector.getCarrierService().getMmTelFeature().startImsTrafficSession(
                IMS_TRAFFIC_TYPE_VIDEO, EUTRAN, IMS_TRAFFIC_DIRECTION_OUTGOING,
                getContext().getMainExecutor(), callback);
        assertTrue(waitForMockImsStateLatchCountdown(LATCH_WAIT_FOR_START_IMS_TRAFFIC));

        assertTrue(sMockModemManager.isImsTrafficStarted(sTestSlot, IMS_TRAFFIC_TYPE_VIDEO));

        assertTrue(sMockModemManager.sendStartImsTrafficResponse(sTestSlot,
                IMS_TRAFFIC_TYPE_VIDEO, 0, 0, 0));

        result = waitForResult(resultQueue);

        assertNotNull(result);
        assertEquals(0, result.getReason());

        assertTrue(sMockModemManager.sendConnectionFailureInfo(sTestSlot, IMS_TRAFFIC_TYPE_VIDEO,
                REASON_RRC_TIMEOUT, 99, 1000));

        result = waitForResult(resultQueue);

        assertNotNull(result);
        assertEquals(REASON_RRC_TIMEOUT, result.getReason());
        assertEquals(99, result.getCauseCode());
        assertEquals(1000, result.getWaitTimeMillis());

        sServiceConnector.getCarrierService().getMmTelFeature().stopImsTrafficSession(callback);
        assertTrue(waitForMockImsStateLatchCountdown(LATCH_WAIT_FOR_STOP_IMS_TRAFFIC));

        assertFalse(sMockModemManager.isImsTrafficStarted(sTestSlot, IMS_TRAFFIC_TYPE_VIDEO));
    }

    @Ignore("Internal use only. Ignore this test until system API is added")
    @Test
    public void testStartAndStopImsTrafficSessionWhenServiceDisconnected() throws Exception {
        if (VDBG) Log.d(LOG_TAG, "testStartAndStopImsTrafficSessionWhenServiceDisconnected");

        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        assumeTrue(sSupportsImsHal);

        sMockModemManager.blockStartImsTrafficResponse(sTestSlot, true);

        // Connect to device ImsService with MmTelFeature
        triggerFrameworkConnectToCarrierImsService(0);

        // Emergency call traffic
        LinkedBlockingQueue<ConnectionFailureInfo> resultQueue = new LinkedBlockingQueue<>();
        ImsTrafficSessionCallback callback = buildImsTrafficSessionCallback(resultQueue);

        sServiceConnector.getCarrierService().getMmTelFeature().startImsTrafficSession(
                IMS_TRAFFIC_TYPE_EMERGENCY, EUTRAN, IMS_TRAFFIC_DIRECTION_OUTGOING,
                getContext().getMainExecutor(), callback);
        assertTrue(waitForMockImsStateLatchCountdown(LATCH_WAIT_FOR_START_IMS_TRAFFIC));

        // Voice call traffic
        LinkedBlockingQueue<ConnectionFailureInfo> voiceResultQueue = new LinkedBlockingQueue<>();
        ImsTrafficSessionCallback voiceCallback = buildImsTrafficSessionCallback(voiceResultQueue);

        sServiceConnector.getCarrierService().getMmTelFeature().startImsTrafficSession(
                IMS_TRAFFIC_TYPE_VOICE, EUTRAN, IMS_TRAFFIC_DIRECTION_OUTGOING,
                getContext().getMainExecutor(), voiceCallback);
        assertTrue(waitForMockImsStateLatchCountdown(LATCH_WAIT_FOR_START_IMS_TRAFFIC));

        // Registration traffic
        LinkedBlockingQueue<ConnectionFailureInfo> regResultQueue = new LinkedBlockingQueue<>();
        ImsTrafficSessionCallback regCallback = buildImsTrafficSessionCallback(regResultQueue);

        sServiceConnector.getCarrierService().getMmTelFeature().startImsTrafficSession(
                IMS_TRAFFIC_TYPE_REGISTRATION, EUTRAN, IMS_TRAFFIC_DIRECTION_OUTGOING,
                getContext().getMainExecutor(), regCallback);
        assertTrue(waitForMockImsStateLatchCountdown(LATCH_WAIT_FOR_START_IMS_TRAFFIC));

        assertTrue(sMockModemManager.isImsTrafficStarted(sTestSlot, IMS_TRAFFIC_TYPE_EMERGENCY));
        assertTrue(sMockModemManager.isImsTrafficStarted(sTestSlot, IMS_TRAFFIC_TYPE_VOICE));
        assertTrue(sMockModemManager.isImsTrafficStarted(sTestSlot, IMS_TRAFFIC_TYPE_REGISTRATION));

        // Unbind the GTS ImsService
        sServiceConnector.disconnectCarrierImsService();
        TimeUnit.MILLISECONDS.sleep(WAIT_REQUEST_TIMEOUT_MS);

        assertFalse(sMockModemManager.isImsTrafficStarted(sTestSlot, IMS_TRAFFIC_TYPE_EMERGENCY));
        assertFalse(sMockModemManager.isImsTrafficStarted(sTestSlot, IMS_TRAFFIC_TYPE_VOICE));
        assertFalse(sMockModemManager.isImsTrafficStarted(sTestSlot,
                IMS_TRAFFIC_TYPE_REGISTRATION));
    }

    private void triggerFrameworkConnectToCarrierImsService(long capabilities) throws Exception {
        assertTrue(sServiceConnector.connectCarrierImsServiceLocally());
        sServiceConnector.getCarrierService().addCapabilities(capabilities);
        // Connect to the ImsService with the MmTel feature.
        assertTrue(sServiceConnector.triggerFrameworkConnectionToCarrierImsService(
                new ImsFeatureConfiguration.Builder()
                .addFeature(sTestSlot, ImsFeature.FEATURE_MMTEL)
                .build()));
        // The MmTelFeature is created when the ImsService is bound. If it wasn't created, then the
        // Framework did not call it.
        assertTrue("Did not receive createMmTelFeature", sServiceConnector.getCarrierService()
                .waitForLatchCountdown(TestImsService.LATCH_CREATE_MMTEL));
        assertTrue("Did not receive MmTelFeature#onReady", sServiceConnector.getCarrierService()
                .waitForLatchCountdown(TestImsService.LATCH_MMTEL_READY));
        assertNotNull("ImsService created, but ImsService#createMmTelFeature was not called!",
                sServiceConnector.getCarrierService().getMmTelFeature());
        int serviceSlot = sServiceConnector.getCarrierService().getMmTelFeature().getSlotIndex();
        assertEquals("The slot specified for the test (" + sTestSlot + ") does not match the "
                        + "assigned slot (" + serviceSlot + "+ for the associated MmTelFeature",
                sTestSlot, serviceSlot);
    }

    private <T> T waitForResult(LinkedBlockingQueue<T> queue) throws Exception {
        return queue.poll(ImsUtils.TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private int waitForIntResult(LinkedBlockingQueue<Integer> queue) throws Exception {
        Integer result = queue.poll(ImsUtils.TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        return result != null ? result : Integer.MAX_VALUE;
    }

    private int waitForIntResult(LinkedBlockingQueue<Integer> queue, int timeout)
            throws Exception {
        Integer result = queue.poll(timeout, TimeUnit.MILLISECONDS);
        return result != null ? result : Integer.MAX_VALUE;
    }

    private static void overrideCarrierConfig(PersistableBundle bundle) throws Exception {
        CarrierConfigManager carrierConfigManager = InstrumentationRegistry.getInstrumentation()
                .getContext().getSystemService(CarrierConfigManager.class);
        if (sReceiver != null) sReceiver.clearQueue();
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(carrierConfigManager,
                (m) -> m.overrideConfig(sTestSub, bundle));
        if (sReceiver != null) sReceiver.waitForChanged();
    }

    private static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }

    /** Checks whether the system feature is supported. */
    private static boolean hasFeature(String feature) {
        final PackageManager pm = getContext().getPackageManager();
        if (!pm.hasSystemFeature(feature)) {
            Log.d(LOG_TAG, "Skipping test that requires " + feature);
            return false;
        }
        return true;
    }

    private void verifySrvccStateChange(int state) throws Exception {
        assertTrue(sMockModemManager.srvccStateNotify(sTestSlot, state));
        sServiceConnector.getCarrierService().getMmTelFeature()
                .getSrvccStateLatch().await(WAIT_UPDATE_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        assertEquals(state,
                sServiceConnector.getCarrierService().getMmTelFeature().getSrvccState());
    }

    private ImsTrafficSessionCallback buildImsTrafficSessionCallback(
            final LinkedBlockingQueue<ConnectionFailureInfo> resultQueue) {
        return new ImsTrafficSessionCallback() {
            @Override
            public void onReady() {
                resultQueue.offer(new ConnectionFailureInfo(0, 0, 0));
            }

            @Override
            public void onError(ConnectionFailureInfo info) {
                resultQueue.offer(info);
            }
        };
    }

    public boolean waitForMockImsStateLatchCountdown(int latchIndex) {
        return waitForMockImsStateLatchCountdown(latchIndex, WAIT_UPDATE_TIMEOUT_MS);
    }

    public boolean waitForMockImsStateLatchCountdown(int latchIndex, int waitMs) {
        return sMockModemManager.waitForImsLatchCountdown(latchIndex, waitMs);
    }
}
