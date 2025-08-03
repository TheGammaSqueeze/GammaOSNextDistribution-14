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

package android.telephony.cts;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertEquals;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.telecom.Call;
import android.telecom.cts.TestUtils;
import android.telephony.SubscriptionManager;
import android.telephony.cts.InCallServiceStateValidator.InCallServiceCallbacks;
import android.telephony.cts.util.TelephonyUtils;
import android.text.TextUtils;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Base class for DomainSelectionService test. */
public class DomainSelectionCallingBase {

    protected static DomainSelectionServiceConnector sServiceConnector;

    private static final String LOG_TAG = "DomainSelectionCallingBase";

    protected static final String PACKAGE_CTS_DIALER = "android.telephony.cts";
    protected static final String COMMAND_SET_DEFAULT_DIALER = "telecom set-default-dialer ";
    protected static final String COMMAND_GET_DEFAULT_DIALER = "telecom get-default-dialer";
    protected static final String TEST_EMERGENCY_NUMBER = "998877665544332211";
    protected static final String INCALL_COMPONENT =
            "android.telephony.cts/.InCallServiceStateValidator";

    // The timeout to wait in current state in milliseconds
    protected static final int WAIT_IN_CURRENT_STATE_MS = 100;

    public static final int WAIT_FOR_SERVICE_TO_UNBIND = 40 * 1000; // 40 seconds
    public static final int WAIT_FOR_CONDITION = 3 * 1000; // 3 seconds
    public static final int WAIT_FOR_CALL_STATE = 10 * 1000; // 10 seconds
    public static final int WAIT_FOR_CALL_STATE_ACTIVE = 15 * 1000; // 15 seconds

    public static final int LATCH_INCALL_SERVICE_BOUND = 1;
    public static final int LATCH_INCALL_SERVICE_UNBOUND = 2;
    public static final int LATCH_IS_ON_CALL_ADDED = 3;
    public static final int LATCH_IS_ON_CALL_REMOVED = 4;
    public static final int LATCH_IS_CALL_DIALING = 5;
    public static final int LATCH_IS_CALL_DISCONNECTING = 6;
    public static final int LATCH_IS_CALL_DISCONNECTED = 7;
    public static final int LATCH_MAX = 8;

    protected static int sTestSlot = 0;
    protected static int sTestSub = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    protected static String sPreviousDefaultDialer;

    protected static final CountDownLatch[] sLatches = new CountDownLatch[LATCH_MAX];
    protected static final Object sLock = new Object();

    protected InCallServiceCallbacks mServiceCallBack;
    protected Context mContext;

    private final ConcurrentHashMap<String, Call> mCalls = new ConcurrentHashMap<String, Call>();
    private String mCurrentCallId = null;
    private boolean mIsEmergencyCallingSetup = false;

    protected static void initializeLatches() {
        synchronized (sLock) {
            for (int i = 0; i < LATCH_MAX; i++) {
                sLatches[i] = new CountDownLatch(1);
            }
        }
    }

    protected static void overrideLatchCount(int latchIndex, int count) {
        synchronized (sLock) {
            sLatches[latchIndex] = new CountDownLatch(count);
        }
    }

    protected boolean callingTestLatchCountdown(int latchIndex, int waitMs) {
        boolean complete = false;
        try {
            CountDownLatch latch;
            synchronized (sLock) {
                latch = sLatches[latchIndex];
            }
            complete = latch.await(waitMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // complete == false
        }
        synchronized (sLock) {
            sLatches[latchIndex] = new CountDownLatch(1);
        }
        return complete;
    }

    private void countDownLatch(int latchIndex) {
        synchronized (sLock) {
            sLatches[latchIndex].countDown();
        }
    }

    private interface Condition {
        Object expected();
        Object actual();
    }

    protected void waitUntilConditionIsTrueOrTimeout(
            Condition condition, long timeout, String description) {
        final long start = System.currentTimeMillis();
        while (!Objects.equals(condition.expected(), condition.actual())
                && System.currentTimeMillis() - start < timeout) {
            waitInCurrentState(50);
        }
        assertEquals(description, condition.expected(), condition.actual());
    }

    protected static void waitInCurrentState(long ms) {
        try {
            TimeUnit.MILLISECONDS.sleep(ms);
        } catch (Exception e) {
            Log.d(LOG_TAG, "InterruptedException");
        }
    }

    protected static void beforeAllTestsBase() throws Exception {
        sServiceConnector = new DomainSelectionServiceConnector(
                InstrumentationRegistry.getInstrumentation());

        // Remove live DomainSelectionService until after these tests are done
        sServiceConnector.clearActiveDomainSelectionService();

        assertTrue(sServiceConnector.connectTestDomainSelectionServiceLocally());
        assertTrue(sServiceConnector.triggerFrameworkConnectionToTestDomainSelectionService());

        UiAutomation ui = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            ui.adoptShellPermissionIdentity();
            // Get the default dialer and save it to restore after test ends.
            sPreviousDefaultDialer = getDefaultDialer(InstrumentationRegistry.getInstrumentation());
            // Set dialer as "android.telephony.cts"
            setDefaultDialer(InstrumentationRegistry.getInstrumentation(), PACKAGE_CTS_DIALER);
        } finally {
            ui.dropShellPermissionIdentity();
        }
    }

    protected static void afterAllTestsBase() throws Exception {
        UiAutomation ui = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            ui.adoptShellPermissionIdentity();

            // Set default dialer
            setDefaultDialer(InstrumentationRegistry.getInstrumentation(), sPreviousDefaultDialer);

            // Restore DomainSelectionService configuration.
            if (sServiceConnector != null) {
                sServiceConnector.disconnectService();
            }
            sServiceConnector = null;
        } finally {
            ui.dropShellPermissionIdentity();
        }
    }

    protected void waitForUnboundService() {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        InCallServiceStateValidator inCallService = mServiceCallBack.getService();
                        return inCallService.isServiceUnBound();
                    }
                },
                WAIT_FOR_SERVICE_TO_UNBIND,
                "Service Unbound");
    }

    protected String getCurrentCallId() {
        synchronized (mCalls) {
            if (mCalls.isEmpty()) {
                mCurrentCallId = null;
            }
            return mCurrentCallId;
        }
    }

    protected void clearCalls() {
        synchronized (mCalls) {
            mCurrentCallId = null;
            mCalls.forEach((k, v) -> v.disconnect());
            mCalls.clear();
        }
    }

    protected Call getCall(String callId) {
        synchronized (mCalls) {
            if (mCalls.isEmpty() || callId == null) {
                return null;
            }

            return mCalls.get(callId);
        }
    }

    private static String getCallId(Call call) {
        String str = call.toString();
        String[] arrofstr = str.split(",", 3);
        int index = arrofstr[0].indexOf(":");
        String callId = arrofstr[0].substring(index + 1);
        return callId;
    }

    private void addCall(Call call) {
        synchronized (mCalls) {
            String callId = getCallId(call);
            assertNotNull("Call Id is null", callId);
            mCurrentCallId = callId;
            mCalls.put(callId, call);
        }
    }

    private void removeCall(Call call) {
        synchronized (mCalls) {
            if (mCalls.isEmpty()) {
                return;
            }

            String callId = getCallId(call);
            assertNotNull("Call Id is null", callId);
            mCalls.remove(callId);
            if (TextUtils.equals(mCurrentCallId, callId)) {
                mCurrentCallId = null;
            }
        }
    }

    protected class ServiceCallBack extends InCallServiceCallbacks {

        @Override
        public void onCallAdded(Call call, int numCalls) {
            Log.i(LOG_TAG, "onCallAdded, Call: " + call + ", Num Calls: " + numCalls);
            addCall(call);
            countDownLatch(LATCH_IS_ON_CALL_ADDED);
        }

        @Override
        public void onCallRemoved(Call call, int numCalls) {
            Log.i(LOG_TAG, "onCallRemoved, Call: " + call + ", Num Calls: " + numCalls);
            removeCall(call);
            countDownLatch(LATCH_IS_ON_CALL_REMOVED);
        }

        @Override
        public void onCallStateChanged(Call call, int state) {
            Log.i(LOG_TAG, "onCallStateChanged " + state + ", Call: " + call);

            switch (state) {
                case Call.STATE_DIALING:
                    countDownLatch(LATCH_IS_CALL_DIALING);
                    break;
                case Call.STATE_DISCONNECTED:
                    countDownLatch(LATCH_IS_CALL_DISCONNECTED);
                    break;
                default:
                    break;
            }
        }
    }

    protected static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }

    /** Checks whether the system feature is supported. */
    protected static boolean hasFeature(String feature) {
        final PackageManager pm = getContext().getPackageManager();
        if (!pm.hasSystemFeature(feature)) {
            Log.d(LOG_TAG, "Skipping test that requires " + feature);
            return false;
        }
        return true;
    }

    protected static String setDefaultDialer(Instrumentation instrumentation, String packageName)
            throws Exception {
        return TelephonyUtils.executeShellCommand(
                instrumentation, COMMAND_SET_DEFAULT_DIALER + packageName);
    }

    protected static String getDefaultDialer(Instrumentation instrumentation) throws Exception {
        return TelephonyUtils.executeShellCommand(instrumentation, COMMAND_GET_DEFAULT_DIALER);
    }

    protected void setupForEmergencyCalling() throws Exception {
        TestUtils.setSystemDialerOverride(
                InstrumentationRegistry.getInstrumentation(), INCALL_COMPONENT);
        mIsEmergencyCallingSetup = true;
    }

    protected void tearDownEmergencyCalling() throws Exception {
        if (!mIsEmergencyCallingSetup) return;
        mIsEmergencyCallingSetup = false;
        TestUtils.clearSystemDialerOverride(InstrumentationRegistry.getInstrumentation());
        TestUtils.clearTestEmergencyNumbers(InstrumentationRegistry.getInstrumentation());
        TelephonyUtils.endBlockSuppression(InstrumentationRegistry.getInstrumentation());
    }
}
