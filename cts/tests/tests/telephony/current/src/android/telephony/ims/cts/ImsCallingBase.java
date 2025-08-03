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

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertEquals;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.telecom.Call;
import android.telecom.cts.TestUtils;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.cts.InCallServiceStateValidator;
import android.telephony.cts.InCallServiceStateValidator.InCallServiceCallbacks;
import android.telephony.cts.util.TelephonyUtils;
import android.telephony.ims.feature.ImsFeature;
import android.telephony.ims.feature.MmTelFeature.MmTelCapabilities;
import android.telephony.ims.stub.ImsFeatureConfiguration;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Base class for ImsCall test. */
public class ImsCallingBase {

    protected static ImsServiceConnector sServiceConnector;

    private static final String LOG_TAG = "ImsCallingBase";

    protected static final String PACKAGE = "android.telephony.ims.cts";
    protected static final String PACKAGE_CTS_DIALER = "android.telephony.cts";
    protected static final String COMMAND_SET_DEFAULT_DIALER = "telecom set-default-dialer ";
    protected static final String COMMAND_GET_DEFAULT_DIALER = "telecom get-default-dialer";
    protected static final String TEST_EMERGENCY_NUMBER = "5553637";
    protected static final Uri TEST_EMERGENCY_URI =
            Uri.fromParts("tel", TEST_EMERGENCY_NUMBER, null);
    protected static final String INCALL_COMPONENT =
            "android.telephony.cts/.InCallServiceStateValidator";

    // The timeout to wait in current state in milliseconds
    protected static final int WAIT_IN_CURRENT_STATE = 100;
    // The timeout to wait in current state after conference call merge failed in milliseconds
    protected static final int WAIT_IN_CURRENT_STATE_MERGE_FAILED = 500;

    public static final int WAIT_FOR_SERVICE_TO_UNBOUND = 40000;
    public static final int WAIT_FOR_CONDITION = 3000;
    public static final int WAIT_FOR_CALL_STATE = 10000;
    public static final int WAIT_FOR_CALL_STATE_ACTIVE = 15000;
    public static final int LATCH_INCALL_SERVICE_BOUND = 1;
    public static final int LATCH_INCALL_SERVICE_UNBOUND = 2;
    public static final int LATCH_IS_ON_CALL_ADDED = 3;
    public static final int LATCH_IS_ON_CALL_REMOVED = 4;
    public static final int LATCH_IS_CALL_DIALING = 5;
    public static final int LATCH_IS_CALL_ACTIVE = 6;
    public static final int LATCH_IS_CALL_DISCONNECTING = 7;
    public static final int LATCH_IS_CALL_DISCONNECTED = 8;
    public static final int LATCH_IS_CALL_RINGING = 9;
    public static final int LATCH_IS_CALL_HOLDING = 10;
    public static final int LATCH_IS_ON_CALL_REMOTELY_HELD = 11;
    public static final int LATCH_IS_ON_CALL_REMOTELY_UNHELD = 12;
    public static final int LATCH_IS_ON_CHILDREN_CHANGED = 13;
    public static final int LATCH_IS_ON_MERGE_START = 14;
    public static final int LATCH_IS_ON_MERGE_COMPLETE = 15;
    public static final int LATCH_IS_ON_CONFERENCE_CALL_ADDED = 16;
    public static final int LATCH_MAX = 17;
    public static final int TEST_RTP_THRESHOLD_PACKET_LOSS_RATE = 47;
    public static final int TEST_RTP_THRESHOLD_JITTER_MILLIS = 150;
    public static final long TEST_RTP_THRESHOLD_INACTIVITY_TIME_MILLIS = 3000;

    protected static boolean sIsBound = false;
    protected static int sCounter = 5553639;
    protected static int sTestSlot = 0;
    protected static int sTestSub = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    protected static long sPreviousOptInStatus = 0;
    protected static long sPreviousEn4GMode = 0;
    protected static String sPreviousDefaultDialer;
    private static CarrierConfigReceiver sReceiver;
    private static SubscriptionManager sSubscriptionManager;

    protected int mParticipantCount = 0;
    protected static final Object mLock = new Object();
    protected InCallServiceCallbacks mServiceCallBack;
    protected Context mContext;
    protected ConcurrentHashMap<String, Call> mCalls = new ConcurrentHashMap<String, Call>();
    protected String mCurrentCallId = null;

    protected static final CountDownLatch[] sLatches = new CountDownLatch[LATCH_MAX];
    private boolean mIsEmergencyCallingSetup = false;

    protected static void initializeLatches() {
        synchronized (mLock) {
            for (int i = 0; i < LATCH_MAX; i++) {
                sLatches[i] = new CountDownLatch(1);
            }
        }
    }

    protected static void overrideLatchCount(int latchIndex, int count) {
        synchronized (mLock) {
            sLatches[latchIndex] = new CountDownLatch(count);
        }
    }


    public boolean callingTestLatchCountdown(int latchIndex, int waitMs) {
        boolean complete = false;
        try {
            CountDownLatch latch;
            synchronized (mLock) {
                latch = sLatches[latchIndex];
            }
            complete = latch.await(waitMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // complete == false
        }
        synchronized (mLock) {
            sLatches[latchIndex] = new CountDownLatch(1);
        }
        return complete;
    }

    public void countDownLatch(int latchIndex) {
        synchronized (mLock) {
            sLatches[latchIndex].countDown();
        }
    }

    protected abstract static class BaseReceiver extends BroadcastReceiver {
        protected CountDownLatch mLatch = new CountDownLatch(1);

        void clearQueue() {
            mLatch = new CountDownLatch(1);
        }

        void waitForChanged() throws Exception {
            mLatch.await(5000, TimeUnit.MILLISECONDS);
        }
    }

    protected static class CarrierConfigReceiver extends BaseReceiver {
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

    public interface Condition {
        Object expected();
        Object actual();
    }

    protected void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (Exception e) {
            Log.d(LOG_TAG, "InterruptedException");
        }
    }

    protected void waitUntilConditionIsTrueOrTimeout(
            Condition condition, long timeout, String description) {
        final long start = System.currentTimeMillis();
        while (!Objects.equals(condition.expected(), condition.actual())
                && System.currentTimeMillis() - start < timeout) {
            sleep(50);
        }
        assertEquals(description, condition.expected(), condition.actual());
    }

    public static void beforeAllTestsBase() throws Exception {
        sServiceConnector = new ImsServiceConnector(InstrumentationRegistry.getInstrumentation());
        // Remove all live ImsServices until after these tests are done
        sServiceConnector.clearAllActiveImsServices(sTestSlot);

        sReceiver = new CarrierConfigReceiver(sTestSub);
        IntentFilter filter = new IntentFilter(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        // ACTION_CARRIER_CONFIG_CHANGED is sticky, so we will get a callback right away.
        InstrumentationRegistry.getInstrumentation()
                .getContext()
                .registerReceiver(sReceiver, filter);

        UiAutomation ui = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            ui.adoptShellPermissionIdentity();
            // Get the default dialer and save it to restore after test ends.
            sPreviousDefaultDialer = getDefaultDialer(InstrumentationRegistry.getInstrumentation());
            // Set dialer as "android.telephony.cts"
            setDefaultDialer(InstrumentationRegistry.getInstrumentation(), PACKAGE_CTS_DIALER);

            sSubscriptionManager =
                    InstrumentationRegistry.getInstrumentation()
                            .getContext()
                            .getSystemService(SubscriptionManager.class);
            // Get the default Subscription values and save it to restore after test ends.
            sPreviousOptInStatus =
                    sSubscriptionManager.getLongSubscriptionProperty(
                            sTestSub, SubscriptionManager.VOIMS_OPT_IN_STATUS, 0, getContext());
            sPreviousEn4GMode =
                    sSubscriptionManager.getLongSubscriptionProperty(
                            sTestSub,
                            SubscriptionManager.ENHANCED_4G_MODE_ENABLED,
                            0,
                            getContext());
            // Set the new Sunbscription values
            sSubscriptionManager.setSubscriptionProperty(
                    sTestSub, SubscriptionManager.VOIMS_OPT_IN_STATUS, String.valueOf(1));
            sSubscriptionManager.setSubscriptionProperty(
                    sTestSub, SubscriptionManager.ENHANCED_4G_MODE_ENABLED, String.valueOf(1));

            // Override the carrier configurartions
            CarrierConfigManager configurationManager =
                    InstrumentationRegistry.getInstrumentation()
                            .getContext()
                            .getSystemService(CarrierConfigManager.class);
            PersistableBundle bundle = new PersistableBundle(1);
            bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL, true);
            bundle.putBoolean(CarrierConfigManager.KEY_ENHANCED_4G_LTE_ON_BY_DEFAULT_BOOL, true);
            bundle.putBoolean(CarrierConfigManager.KEY_EDITABLE_ENHANCED_4G_LTE_BOOL, false);
            bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_VOLTE_TTY_SUPPORTED_BOOL, true);
            bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_IMS_GBA_REQUIRED_BOOL, false);

            sReceiver.clearQueue();
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    configurationManager, (m) -> m.overrideConfig(sTestSub, bundle));
        } finally {
            ui.dropShellPermissionIdentity();
        }
        sReceiver.waitForChanged();
    }

    public static void afterAllTestsBase() throws Exception {
        UiAutomation ui = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            ui.adoptShellPermissionIdentity();
            // Set the default Sunbscription values.
            sSubscriptionManager.setSubscriptionProperty(
                    sTestSub,
                    SubscriptionManager.VOIMS_OPT_IN_STATUS,
                    String.valueOf(sPreviousOptInStatus));
            sSubscriptionManager.setSubscriptionProperty(
                    sTestSub,
                    SubscriptionManager.ENHANCED_4G_MODE_ENABLED,
                    String.valueOf(sPreviousEn4GMode));
            // Set default dialer
            setDefaultDialer(InstrumentationRegistry.getInstrumentation(), sPreviousDefaultDialer);

            // Restore all ImsService configurations that existed before the test.
            if (sServiceConnector != null && sIsBound) {
                sServiceConnector.disconnectServices();
                sIsBound = false;
            }
            sServiceConnector = null;
            overrideCarrierConfig(null);

            if (sReceiver != null) {
                InstrumentationRegistry.getInstrumentation()
                        .getContext()
                        .unregisterReceiver(sReceiver);
                sReceiver = null;
            }
        } finally {
            ui.dropShellPermissionIdentity();
        }
    }

    public void bindImsService() throws Exception {
        bindImsService(ImsRegistrationImplBase.REGISTRATION_TECH_LTE);
    }

    public void bindImsService(int radioTech) throws Exception {
        MmTelCapabilities capabilities =
                new MmTelCapabilities(MmTelCapabilities.CAPABILITY_TYPE_VOICE);
        // Set Registered and VoLTE capable
        bindImsServiceForCapabilities(radioTech, capabilities);
    }

    public void bindImsServiceForCapabilities(int radioTech, MmTelCapabilities capabilities)
            throws Exception {
        // Connect to the ImsService with the MmTel feature.
        assertTrue(
                sServiceConnector.connectCarrierImsService(
                        new ImsFeatureConfiguration.Builder()
                                .addFeature(sTestSlot, ImsFeature.FEATURE_MMTEL)
                                .addFeature(sTestSlot, ImsFeature.FEATURE_EMERGENCY_MMTEL)
                                .build()));
        sIsBound = true;
        // The MmTelFeature is created when the ImsService is bound. If it wasn't created, then the
        // Framework did not call it.
        sServiceConnector
                .getCarrierService()
                .waitForLatchCountdown(TestImsService.LATCH_CREATE_MMTEL);
        assertNotNull(
                "ImsService created, but ImsService#createMmTelFeature was not called!",
                sServiceConnector.getCarrierService().getMmTelFeature());

        sServiceConnector
                .getCarrierService()
                .waitForLatchCountdown(TestImsService.LATCH_MMTEL_CAP_SET);

        // Set Registered with given capabilities
        sServiceConnector
                .getCarrierService()
                .getImsService()
                .getRegistrationForSubscription(sTestSlot, sTestSub)
                .onRegistered(radioTech);
        sServiceConnector.getCarrierService().getMmTelFeature().setCapabilities(capabilities);
        sServiceConnector
                .getCarrierService()
                .getMmTelFeature()
                .notifyCapabilitiesStatusChanged(capabilities);

        // Wait a second for the notifyCapabilitiesStatusChanged indication to be processed on the
        // main telephony thread - currently no better way of knowing that telephony has processed
        // this command. SmsManager#isImsSmsSupported() is @hide and must be updated to use new API.
        Thread.sleep(3000);
    }

    public void waitForUnboundService() {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        InCallServiceStateValidator inCallService = mServiceCallBack.getService();
                        return (inCallService.isServiceUnBound()) ? true : false;
                    }
                },
                WAIT_FOR_SERVICE_TO_UNBOUND,
                "Service Unbound");
    }

    public void isCallActive(Call call, TestImsCallSessionImpl callsession) {
        if (call.getDetails().getState() != Call.STATE_ACTIVE) {
            assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_ACTIVE, WAIT_FOR_CALL_STATE));
        }
        assertNotNull("Unable to get callSession, its null", callsession);

        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return (callsession.isInCall()
                                        && call.getDetails().getState() == Call.STATE_ACTIVE)
                                ? true
                                : false;
                    }
                },
                WAIT_FOR_CONDITION,
                "Call Active");
    }

    public void isCallDisconnected(Call call, TestImsCallSessionImpl callsession) {
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTED, WAIT_FOR_CALL_STATE));
        assertNotNull("Unable to get callSession, its null", callsession);

        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return (callsession.isInTerminated()
                                        && call.getDetails().getState() == Call.STATE_DISCONNECTED)
                                ? true
                                : false;
                    }
                }, WAIT_FOR_CONDITION,
                "session " + callsession.getState() + ", call "
                        + call.getDetails().getState() + ", Call Disconnected");
    }

    public void isCallHolding(Call call, TestImsCallSessionImpl callsession) {
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_HOLDING, WAIT_FOR_CALL_STATE));
        assertNotNull("Unable to get callSession, its null", callsession);
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return (callsession.isSessionOnHold()
                                && call.getDetails().getState() == Call.STATE_HOLDING) ? true
                                : false;
                    }
                }, WAIT_FOR_CONDITION, "Call Holding");
    }

    protected void setCallID(String callid) {
        assertNotNull("Call Id is set to null", callid);
        mCurrentCallId = callid;
    }

    public void addCall(Call call) {
        String callid = getCallId(call);
        setCallID(callid);
        synchronized (mCalls) {
            mCalls.put(callid, call);
        }
    }

    public String getCallId(Call call) {
        String str = call.toString();
        String[] arrofstr = str.split(",", 3);
        int index = arrofstr[0].indexOf(":");
        String callId = arrofstr[0].substring(index + 1);
        return callId;
    }

    public Call getCall(String callId) {
        synchronized (mCalls) {
            if (mCalls.isEmpty()) {
                return null;
            }

            for (Map.Entry<String, Call> entry : mCalls.entrySet()) {
                if (entry.getKey().equals(callId)) {
                    Call call = entry.getValue();
                    assertNotNull("Call is not added, its null", call);
                    return call;
                }
            }
        }
        return null;
    }

    protected void removeCall(Call call) {
        if (mCalls.isEmpty()) {
            return;
        }

        String callid = getCallId(call);
        Map.Entry<String, Call>[] entries = mCalls.entrySet().toArray(new Map.Entry[mCalls.size()]);
        for (Map.Entry<String, Call> entry : entries) {
            if (entry.getKey().equals(callid)) {
                mCalls.remove(entry.getKey());
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
            if (call.getDetails().hasProperty(Call.Details.PROPERTY_CONFERENCE)) {
                countDownLatch(LATCH_IS_ON_CONFERENCE_CALL_ADDED);
            }
        }

        @Override
        public void onCallRemoved(Call call, int numCalls) {
            Log.i(LOG_TAG, "onCallRemoved, Call: " + call + ", Num Calls: " + numCalls);
            removeCall(call);
            countDownLatch(LATCH_IS_ON_CALL_REMOVED);
        }

        @Override
        public void onCallStateChanged(Call call, int state) {
            Log.i(LOG_TAG, "onCallStateChanged " + state + "Call: " + call);

            switch (state) {
                case Call.STATE_DIALING:
                    countDownLatch(LATCH_IS_CALL_DIALING);
                    break;
                case Call.STATE_ACTIVE:
                    countDownLatch(LATCH_IS_CALL_ACTIVE);
                    break;
                case Call.STATE_DISCONNECTING:
                    countDownLatch(LATCH_IS_CALL_DISCONNECTING);
                    break;
                case Call.STATE_DISCONNECTED:
                    countDownLatch(LATCH_IS_CALL_DISCONNECTED);
                    break;
                case Call.STATE_RINGING:
                    countDownLatch(LATCH_IS_CALL_RINGING);
                    break;
                case Call.STATE_HOLDING:
                    countDownLatch(LATCH_IS_CALL_HOLDING);
                    break;
                default:
                    break;
            }
        }

        @Override
        public void onChildrenChanged(Call call, List<Call> children) {
            if (call.getDetails().hasProperty(Call.Details.PROPERTY_CONFERENCE)) {
                Log.i(LOG_TAG, "onChildrenChanged, Call: " + call + " , size " + children.size());
                mParticipantCount = children.size();
                countDownLatch(LATCH_IS_ON_CHILDREN_CHANGED);
            }
        }

        @Override
        public void onConnectionEvent(Call call, String event, Bundle extras) {
            Log.i(LOG_TAG, "onConnectionEvent, Call: " + call + " , event " + event);
            if (event.equals(android.telecom.Connection.EVENT_CALL_REMOTELY_HELD)) {
                countDownLatch(LATCH_IS_ON_CALL_REMOTELY_HELD);
            } else if (event.equals(android.telecom.Connection.EVENT_CALL_REMOTELY_UNHELD)) {
                countDownLatch(LATCH_IS_ON_CALL_REMOTELY_UNHELD);
            } else if (event.equals(android.telecom.Connection.EVENT_MERGE_START)) {
                countDownLatch(LATCH_IS_ON_MERGE_START);
            } else if (event.equals(android.telecom.Connection.EVENT_MERGE_COMPLETE)) {
                countDownLatch(LATCH_IS_ON_MERGE_COMPLETE);
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
        String str =
                TelephonyUtils.executeShellCommand(
                        instrumentation, COMMAND_SET_DEFAULT_DIALER + packageName);
        return str;
    }

    protected static String getDefaultDialer(Instrumentation instrumentation) throws Exception {
        String str =
                TelephonyUtils.executeShellCommand(instrumentation, COMMAND_GET_DEFAULT_DIALER);
        return str;
    }

    protected static void overrideCarrierConfig(PersistableBundle bundle) throws Exception {
        CarrierConfigManager carrierConfigManager =
                InstrumentationRegistry.getInstrumentation()
                        .getContext()
                        .getSystemService(CarrierConfigManager.class);
        sReceiver.clearQueue();
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                carrierConfigManager, (m) -> m.overrideConfig(sTestSub, bundle));
        sReceiver.waitForChanged();
    }

    protected void setupForEmergencyCalling(String testNumber) throws Exception {
        enableCarrierUseImsFirstForEmergency();
        TestUtils.setSystemDialerOverride(
                InstrumentationRegistry.getInstrumentation(), INCALL_COMPONENT);
        TestUtils.addTestEmergencyNumber(InstrumentationRegistry.getInstrumentation(), testNumber);
        mIsEmergencyCallingSetup = true;
    }

    protected void tearDownEmergencyCalling() throws Exception {
        if (!mIsEmergencyCallingSetup) return;
        mIsEmergencyCallingSetup = false;
        TestUtils.clearSystemDialerOverride(InstrumentationRegistry.getInstrumentation());
        TestUtils.clearTestEmergencyNumbers(InstrumentationRegistry.getInstrumentation());
        TelephonyUtils.endBlockSuppression(InstrumentationRegistry.getInstrumentation());
    }

    private static void enableCarrierUseImsFirstForEmergency() throws Exception {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_USE_IMS_FIRST_FOR_EMERGENCY_BOOL, true);
        overrideCarrierConfig(bundle);
    }
}
