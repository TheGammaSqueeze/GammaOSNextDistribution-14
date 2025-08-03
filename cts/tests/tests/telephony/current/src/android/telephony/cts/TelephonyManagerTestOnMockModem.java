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
package android.telephony.cts;

import static android.telephony.PreciseDisconnectCause.NO_DISCONNECT_CAUSE_AVAILABLE;
import static android.telephony.PreciseDisconnectCause.TEMPORARY_FAILURE;
import static android.telephony.mockmodem.MockSimService.MOCK_SIM_PROFILE_ID_TWN_CHT;
import static android.telephony.mockmodem.MockSimService.MOCK_SIM_PROFILE_ID_TWN_FET;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_RADIO_POWER;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.hardware.radio.network.Domain;
import android.hardware.radio.sim.Carrier;
import android.hardware.radio.sim.CarrierRestrictions;
import android.hardware.radio.sim.SimLockMultiSimPolicy;
import android.hardware.radio.voice.LastCallFailCause;
import android.hardware.radio.voice.UusInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemProperties;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.telecom.PhoneAccount;
import android.telecom.TelecomManager;
import android.telephony.AccessNetworkConstants;
import android.telephony.CarrierInfo;
import android.telephony.CarrierRestrictionRules;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.telephony.cts.util.TelephonyUtils;
import android.telephony.mockmodem.MockCallControlInfo;
import android.telephony.mockmodem.MockModemConfigInterface;
import android.telephony.mockmodem.MockModemManager;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SdkSuppress;

import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.internal.telephony.flags.Flags;
import com.android.internal.telephony.uicc.IccUtils;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Test MockModemService interfaces. */
public class TelephonyManagerTestOnMockModem {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String TAG = "TelephonyManagerTestOnMockModem";
    private static final long WAIT_TIME_MS = 20000;
    private static MockModemManager sMockModemManager;
    private static TelephonyManager sTelephonyManager;
    private static final String ALLOW_MOCK_MODEM_PROPERTY = "persist.radio.allow_mock_modem";
    private static final String BOOT_ALLOW_MOCK_MODEM_PROPERTY = "ro.boot.radio.allow_mock_modem";
    private static final boolean DEBUG = !"user".equals(Build.TYPE);
    private static final String RESOURCE_PACKAGE_NAME = "android";
    private static boolean sIsMultiSimDevice;
    private static HandlerThread sServiceStateChangeCallbackHandlerThread;
    private static Handler sServiceStateChangeCallbackHandler;
    private static HandlerThread sCallDisconnectCauseCallbackHandlerThread;
    private static Handler sCallDisconnectCauseCallbackHandler;
    private static HandlerThread sCallStateChangeCallbackHandlerThread;
    private static Handler sCallStateChangeCallbackHandler;
    private static ServiceStateListener sServiceStateCallback;
    private static CallDisconnectCauseListener sCallDisconnectCauseCallback;
    private static CallStateListener sCallStateCallback;
    private int mServiceState;
    private int mExpectedRegState;
    private int mExpectedRegFailCause;
    private int mPreciseCallDisconnectCause;
    private int mCallState;
    private final Object mServiceStateChangeLock = new Object();
    private final Object mCallDisconnectCauseLock = new Object();
    private final Object mCallStateChangeLock = new Object();
    private final Executor mServiceStateChangeExecutor = Runnable::run;
    private final Executor mCallDisconnectCauseExecutor = Runnable::run;
    private final Executor mCallStateChangeExecutor = Runnable::run;
    private boolean mResetCarrierStatusInfo;
    private static String mShaId;
    private static final int TIMEOUT_IN_SEC_FOR_MODEM_CB = 10;
    @BeforeClass
    public static void beforeAllTests() throws Exception {
        Log.d(TAG, "TelephonyManagerTestOnMockModem#beforeAllTests()");

        if (!hasTelephonyFeature()) {
            return;
        }

        enforceMockModemDeveloperSetting();
        sTelephonyManager =
                (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);

        sIsMultiSimDevice = isMultiSim(sTelephonyManager);

        sMockModemManager = new MockModemManager();
        assertNotNull(sMockModemManager);
        assertTrue(sMockModemManager.connectMockModemService());

        sServiceStateChangeCallbackHandlerThread =
                new HandlerThread("TelephonyManagerServiceStateChangeCallbackTest");
        sServiceStateChangeCallbackHandlerThread.start();
        sServiceStateChangeCallbackHandler =
                new Handler(sServiceStateChangeCallbackHandlerThread.getLooper());

        sCallDisconnectCauseCallbackHandlerThread =
                new HandlerThread("TelephonyManagerCallDisconnectCauseCallbackTest");
        sCallDisconnectCauseCallbackHandlerThread.start();
        sCallDisconnectCauseCallbackHandler =
                new Handler(sCallDisconnectCauseCallbackHandlerThread.getLooper());

        sCallStateChangeCallbackHandlerThread =
                new HandlerThread("TelephonyManagerCallStateChangeCallbackTest");
        sCallStateChangeCallbackHandlerThread.start();
        sCallStateChangeCallbackHandler =
                new Handler(sCallStateChangeCallbackHandlerThread.getLooper());
        mShaId = getShaId(TelephonyUtils.CTS_APP_PACKAGE);

        final PackageManager pm = getContext().getPackageManager();
        if (pm.hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            //Wait for Telephony FW and RIL initialization are done
            TimeUnit.SECONDS.sleep(4);
        }
    }

    @AfterClass
    public static void afterAllTests() throws Exception {
        Log.d(TAG, "TelephonyManagerTestOnMockModem#afterAllTests()");

        if (!hasTelephonyFeature()) {
            return;
        }

        if (sServiceStateChangeCallbackHandlerThread != null) {
            sServiceStateChangeCallbackHandlerThread.quitSafely();
            sServiceStateChangeCallbackHandlerThread = null;
        }

        if (sCallDisconnectCauseCallbackHandlerThread != null) {
            sCallDisconnectCauseCallbackHandlerThread.quitSafely();
            sCallDisconnectCauseCallbackHandlerThread = null;
        }

        if (sCallStateChangeCallbackHandlerThread != null) {
            sCallStateChangeCallbackHandlerThread.quitSafely();
            sCallStateChangeCallbackHandlerThread = null;
        }

        if (sServiceStateCallback != null) {
            sTelephonyManager.unregisterTelephonyCallback(sServiceStateCallback);
            sServiceStateCallback = null;
        }

        if (sCallDisconnectCauseCallback != null) {
            sTelephonyManager.unregisterTelephonyCallback(sCallDisconnectCauseCallback);
            sCallDisconnectCauseCallback = null;
        }

        if (sCallStateCallback != null) {
            sTelephonyManager.unregisterTelephonyCallback(sCallStateCallback);
            sCallStateCallback = null;
        }

        // Rebind all interfaces which is binding to MockModemService to default.
        assertNotNull(sMockModemManager);
        // Reset the modified error response of RIL_REQUEST_RADIO_POWER to the original behavior
        // and -1 means to disable the modifed mechanism in mock modem
        sMockModemManager.forceErrorResponse(0, RIL_REQUEST_RADIO_POWER, -1);
        assertTrue(sMockModemManager.disconnectMockModemService());
        sMockModemManager = null;
        mShaId = null;
    }

    @Before
    public void beforeTest() {
        assumeTrue(hasTelephonyFeature());
        try {
            sTelephonyManager.getHalVersion(TelephonyManager.HAL_SERVICE_RADIO);
        } catch (IllegalStateException e) {
            assumeNoException("Skipping tests because Telephony service is null", e);
        }
    }

    @After
    public void afterTest() {
        if (mResetCarrierStatusInfo) {
            try {
                TelephonyUtils.resetCarrierRestrictionStatusAllowList(
                        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation());
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                mResetCarrierStatusInfo = false;
            }
        }
    }

    private static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }

    private static String getShaId(String packageName) {
        try {
            final PackageManager packageManager = getContext().getPackageManager();
            MessageDigest sha1MDigest = MessageDigest.getInstance("SHA1");
            final PackageInfo packageInfo = packageManager.getPackageInfo(packageName,
                    PackageManager.GET_SIGNATURES);
            for (Signature signature : packageInfo.signatures) {
                final byte[] signatureSha1 = sha1MDigest.digest(signature.toByteArray());
                return IccUtils.bytesToHexString(signatureSha1);
            }
        } catch (NoSuchAlgorithmException | PackageManager.NameNotFoundException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    private static boolean hasTelephonyFeature() {
        final PackageManager pm = getContext().getPackageManager();
        if (!pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            Log.d(TAG, "Skipping test that requires FEATURE_TELEPHONY");
            return false;
        }
        return true;
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENFORCE_TELEPHONY_FEATURE_MAPPING_FOR_PUBLIC_APIS)
    private static boolean hasTelephonyFeature(String featureName) {
        final PackageManager pm = getContext().getPackageManager();
        if (!pm.hasSystemFeature(featureName)) {
            Log.d(TAG, "Skipping test that requires " + featureName);
            return false;
        }
        return true;
    }

    private static boolean isMultiSim(TelephonyManager tm) {
        return tm != null && tm.getPhoneCount() > 1;
    }

    private static boolean isSimHotSwapCapable() {
        boolean isSimHotSwapCapable = false;
        int resourceId =
                getContext()
                        .getResources()
                        .getIdentifier("config_hotswapCapable", "bool", RESOURCE_PACKAGE_NAME);

        if (resourceId > 0) {
            isSimHotSwapCapable = getContext().getResources().getBoolean(resourceId);
        } else {
            Log.d(TAG, "Fail to get the resource Id, using default.");
        }

        Log.d(TAG, "isSimHotSwapCapable = " + (isSimHotSwapCapable ? "true" : "false"));

        return isSimHotSwapCapable;
    }

    private static void enforceMockModemDeveloperSetting() throws Exception {
        boolean isAllowed = SystemProperties.getBoolean(ALLOW_MOCK_MODEM_PROPERTY, false);
        boolean isAllowedForBoot =
                SystemProperties.getBoolean(BOOT_ALLOW_MOCK_MODEM_PROPERTY, false);
        // Check for developer settings for user build. Always allow for debug builds
        if (!(isAllowed || isAllowedForBoot) && !DEBUG) {
            throw new IllegalStateException(
                    "!! Enable Mock Modem before running this test !! "
                            + "Developer options => Allow Mock Modem");
        }
    }

    private int getActiveSubId(int phoneId) {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity("android.permission.READ_PRIVILEGED_PHONE_STATE");

        int[] allSubs =
                getContext()
                        .getSystemService(SubscriptionManager.class)
                        .getActiveSubscriptionIdList();
        int subsLength = allSubs.length;
        Log.d(TAG, " Active Sub length is " + subsLength);

        return (phoneId < subsLength) ? allSubs[phoneId] : -1;
    }

    private NetworkRegistrationInfo getNri(int domain, int subId) {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity("android.permission.READ_PHONE_STATE");

        ServiceState ss = sTelephonyManager.createForSubscriptionId(subId).getServiceState();
        assertNotNull(ss);

        NetworkRegistrationInfo nri =
                ss.getNetworkRegistrationInfo(domain, AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        assertNotNull(nri);
        return nri;
    }

    private int getRegState(int domain, int subId) {
        int reg;

        NetworkRegistrationInfo nri = getNri(domain, subId);

        reg = nri.getRegistrationState();
        Log.d(TAG, "SS: " + nri.registrationStateToString(reg));

        return reg;
    }

    private int getRegFailCause(int domain, int subId) {
        return getNri(domain, subId).getRejectCause();
    }

    private void waitForCondition(BooleanSupplier condition, Object lock, long maxWaitMillis)
            throws Exception {
        long now = System.currentTimeMillis();
        long deadlineTime = now + maxWaitMillis;
        while (!condition.getAsBoolean() && now < deadlineTime) {
            lock.wait(deadlineTime - now);
            now = System.currentTimeMillis();
        }
    }

    @Test
    public void testSimStateChange() throws Throwable {
        Log.d(TAG, "TelephonyManagerTestOnMockModem#testSimStateChange");

        assumeTrue(isSimHotSwapCapable());

        int slotId = 0;

        // Remove the SIM for initial state
        sMockModemManager.removeSimCard(slotId);

        int simCardState = sTelephonyManager.getSimCardState();
        Log.d(TAG, "Current SIM card state: " + simCardState);

        assertTrue(
                Arrays.asList(TelephonyManager.SIM_STATE_UNKNOWN, TelephonyManager.SIM_STATE_ABSENT)
                        .contains(simCardState));

        // Insert a SIM
        assertTrue(sMockModemManager.insertSimCard(slotId, MOCK_SIM_PROFILE_ID_TWN_CHT));
        simCardState = sTelephonyManager.getSimCardState();
        assertEquals(TelephonyManager.SIM_STATE_PRESENT, simCardState);

        // Check SIM state ready
        simCardState = sTelephonyManager.getSimState();
        assertEquals(TelephonyManager.SIM_STATE_READY, simCardState);

        // Remove the SIM
        assertTrue(sMockModemManager.removeSimCard(slotId));
        simCardState = sTelephonyManager.getSimCardState();
        assertEquals(TelephonyManager.SIM_STATE_ABSENT, simCardState);
    }

    @Test
    public void testServiceStateChange() throws Throwable {
        Log.d(TAG, "TelephonyManagerTestOnMockModem#testServiceStateChange");

        assumeTrue(isSimHotSwapCapable());

        int slotId = 0;
        int subId;

        // Insert a SIM
        sMockModemManager.insertSimCard(slotId, MOCK_SIM_PROFILE_ID_TWN_CHT);

        TimeUnit.SECONDS.sleep(2);
        subId = getActiveSubId(slotId);
        assertTrue(subId > 0);

        // Register service state change callback
        synchronized (mServiceStateChangeLock) {
            mServiceState = ServiceState.STATE_OUT_OF_SERVICE;
            mExpectedRegState = ServiceState.STATE_IN_SERVICE;
        }

        sServiceStateChangeCallbackHandler.post(
                () -> {
                    sServiceStateCallback = new ServiceStateListener();
                    ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                            sTelephonyManager,
                            (tm) ->
                                    tm.registerTelephonyCallback(
                                            mServiceStateChangeExecutor, sServiceStateCallback));
                });

        // Enter Service
        Log.d(TAG, "testServiceStateChange: Enter Service");
        sMockModemManager.changeNetworkService(slotId, MOCK_SIM_PROFILE_ID_TWN_CHT, true);

        // Expect: Home State
        synchronized (mServiceStateChangeLock) {
            if (mServiceState != ServiceState.STATE_IN_SERVICE) {
                Log.d(TAG, "Wait for service state change to in service");
                waitForCondition(
                        () -> (ServiceState.STATE_IN_SERVICE == mServiceState),
                        mServiceStateChangeLock,
                        WAIT_TIME_MS);
            }
        }
        assertEquals(
                getRegState(NetworkRegistrationInfo.DOMAIN_CS, subId),
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME);

        // Leave Service
        synchronized (mServiceStateChangeLock) {
            mExpectedRegState = ServiceState.STATE_OUT_OF_SERVICE;
        }

        Log.d(TAG, "testServiceStateChange: Leave Service");
        sMockModemManager.changeNetworkService(slotId, MOCK_SIM_PROFILE_ID_TWN_CHT, false);

        // Expect: Seaching State
        synchronized (mServiceStateChangeLock) {
            if (mServiceState != ServiceState.STATE_OUT_OF_SERVICE) {
                Log.d(TAG, "Wait for service state change to out of service");
                waitForCondition(
                        () -> (ServiceState.STATE_OUT_OF_SERVICE == mServiceState),
                        mServiceStateChangeLock,
                        WAIT_TIME_MS);
            }
        }
        assertEquals(
                getRegState(NetworkRegistrationInfo.DOMAIN_CS, subId),
                NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_SEARCHING);

        // Unregister service state change callback
        sTelephonyManager.unregisterTelephonyCallback(sServiceStateCallback);
        sServiceStateCallback = null;

        // Remove the SIM
        sMockModemManager.removeSimCard(slotId);
    }

    private class ServiceStateListener extends TelephonyCallback
            implements TelephonyCallback.ServiceStateListener {
        @Override
        public void onServiceStateChanged(ServiceState serviceState) {
            Log.d(TAG, "Callback: service state = " + serviceState.getVoiceRegState());
            synchronized (mServiceStateChangeLock) {
                mServiceState = serviceState.getVoiceRegState();
                if (serviceState.getVoiceRegState() == mExpectedRegState) {
                    mServiceStateChangeLock.notify();
                }
            }
        }
    }

    @Test
    public void testRegistrationFailed() throws Throwable {
        Log.d(TAG, "TelephonyManagerTestOnMockModem#testRegistrationFailed");

        assumeTrue(isSimHotSwapCapable());

        int slotId = 0;
        int subId;

        // Insert a SIM
        sMockModemManager.insertSimCard(slotId, MOCK_SIM_PROFILE_ID_TWN_CHT);

        TimeUnit.SECONDS.sleep(2);
        subId = getActiveSubId(slotId);
        assertTrue(subId > 0);

        // Register service state change callback
        synchronized (mServiceStateChangeLock) {
            mServiceState = ServiceState.STATE_IN_SERVICE;
            mExpectedRegState = ServiceState.STATE_OUT_OF_SERVICE;
            mExpectedRegFailCause = (int) (Math.random() * (double) 256);
        }

        sServiceStateChangeCallbackHandler.post(
                () -> {
                    sServiceStateCallback = new ServiceStateListener();
                    ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                            sTelephonyManager,
                            (tm) ->
                                    tm.registerTelephonyCallback(
                                            mServiceStateChangeExecutor, sServiceStateCallback));
                });

        sMockModemManager.changeNetworkService(
                slotId, MOCK_SIM_PROFILE_ID_TWN_CHT, false);

        // Expect: Searching
        synchronized (mServiceStateChangeLock) {
            if (mServiceState != ServiceState.STATE_OUT_OF_SERVICE) {
                Log.d(TAG, "Wait for service state change to searching");
                waitForCondition(
                        () -> (ServiceState.STATE_OUT_OF_SERVICE == mServiceState),
                        mServiceStateChangeLock,
                        WAIT_TIME_MS);
            }
        }
        assertEquals(
                getRegState(NetworkRegistrationInfo.DOMAIN_CS, subId),
                NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_SEARCHING);


        // Expect: Registration Failed
        synchronized (mServiceStateChangeLock) {
            Log.d(TAG, "Wait for service state change to denied");
            sMockModemManager.changeNetworkService(
                    slotId, MOCK_SIM_PROFILE_ID_TWN_CHT, false, Domain.CS, mExpectedRegFailCause);
            mServiceStateChangeLock.wait(WAIT_TIME_MS);
        }

        assertEquals(
                getRegState(NetworkRegistrationInfo.DOMAIN_CS, subId),
                NetworkRegistrationInfo.REGISTRATION_STATE_DENIED);
        assertEquals(
                getRegFailCause(NetworkRegistrationInfo.DOMAIN_CS, subId),
                mExpectedRegFailCause);

        // Unregister service state change callback
        sTelephonyManager.unregisterTelephonyCallback(sServiceStateCallback);
        sServiceStateCallback = null;

        // Remove the SIM
        sMockModemManager.removeSimCard(slotId);
    }

    private class CallDisconnectCauseListener extends TelephonyCallback
            implements TelephonyCallback.CallDisconnectCauseListener {
        @Override
        public void onCallDisconnectCauseChanged(int disconnectCause, int preciseDisconnectCause) {
            synchronized (mCallDisconnectCauseLock) {
                mPreciseCallDisconnectCause = preciseDisconnectCause;
                Log.d(TAG, "Callback: call disconnect cause = " + mPreciseCallDisconnectCause);
                mCallDisconnectCauseLock.notify();
            }
        }
    }

    private class CallStateListener extends TelephonyCallback
            implements TelephonyCallback.CallStateListener {
        @Override
        public void onCallStateChanged(int state) {
            synchronized (mCallStateChangeLock) {
                mCallState = state;
                mCallStateChangeLock.notify();
            }
        }
    }

    @Test
    public void testVoiceCallState() throws Throwable {
        Log.d(TAG, "TelephonyManagerTestOnMockModem#testVoiceCallState");

        // Skip the test if it is a data-only device
        final PackageManager pm = getContext().getPackageManager();
        if (!pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_CALLING)) {
            Log.d(TAG, "Skipping test: Not test on data-only device");
            return;
        }

        assumeTrue(isSimHotSwapCapable());

        int slotId = 0;
        int subId;
        TelecomManager telecomManager =
                (TelecomManager) getContext().getSystemService(Context.TELECOM_SERVICE);

        // Insert a SIM
        Log.d(TAG, "Start to insert a SIM");
        sMockModemManager.insertSimCard(slotId, MOCK_SIM_PROFILE_ID_TWN_FET);
        TimeUnit.SECONDS.sleep(1);

        // Register service state change callback
        synchronized (mServiceStateChangeLock) {
            mServiceState = ServiceState.STATE_OUT_OF_SERVICE;
            mExpectedRegState = ServiceState.STATE_IN_SERVICE;
        }

        sServiceStateChangeCallbackHandler.post(
                () -> {
                    sServiceStateCallback = new ServiceStateListener();
                    ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                            sTelephonyManager,
                            (tm) ->
                                    tm.registerTelephonyCallback(
                                            mServiceStateChangeExecutor, sServiceStateCallback));
                });

        // In service
        Log.d(TAG, "Start to register CS only network");
        sMockModemManager.changeNetworkService(
                slotId, MOCK_SIM_PROFILE_ID_TWN_FET, true, Domain.CS);

        // Verify service state
        synchronized (mServiceStateChangeLock) {
            if (mServiceState != ServiceState.STATE_IN_SERVICE) {
                Log.d(TAG, "Wait for service state change to in service");
                waitForCondition(() -> (
                        ServiceState.STATE_IN_SERVICE == mServiceState),
                        mServiceStateChangeLock,
                        WAIT_TIME_MS);
            }
        }

        subId = getActiveSubId(slotId);
        assertTrue(subId > 0);
        assertEquals(
                getRegState(NetworkRegistrationInfo.DOMAIN_PS, subId),
                NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_SEARCHING);

        assertEquals(
                getRegState(NetworkRegistrationInfo.DOMAIN_CS, subId),
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME);

        // Register call state change callback
        mCallState = TelephonyManager.CALL_STATE_IDLE;
        sCallStateChangeCallbackHandler.post(
                () -> {
                    sCallStateCallback = new CallStateListener();
                    ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                            sTelephonyManager,
                            (tm) ->
                                    tm.registerTelephonyCallback(
                                            mCallStateChangeExecutor, sCallStateCallback));
                });

        //Disable Ims to make sure the call would go thorugh with a CS call
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                sTelephonyManager,  (tm) -> tm.disableIms(slotId));
        // Sleep 3s to make sure ims is disabled
        TimeUnit.SECONDS.sleep(3);

        // Dial a CS voice call
        Log.d(TAG, "Start dialing call");
        String phoneNumber = "+886987654321";
        final Uri address = Uri.fromParts(PhoneAccount.SCHEME_TEL, phoneNumber, null);

        // Place outgoing call
        telecomManager.placeCall(address, null);
        TimeUnit.SECONDS.sleep(1);

        // Verify call state
        synchronized (mCallStateChangeLock) {
            if (mCallState == TelephonyManager.CALL_STATE_IDLE) {
                Log.d(TAG, "Wait for call state change to offhook");
                mCallStateChangeLock.wait(WAIT_TIME_MS);
            }
            assertEquals(TelephonyManager.CALL_STATE_OFFHOOK, mCallState);
        }

        // Verify the call is a CS call
        assertTrue(sMockModemManager.getNumberOfOngoingCSCalls(slotId) > 0);

        // Hang up the call
        Log.d(TAG, "Hangup call");
        telecomManager.endCall();
        TimeUnit.SECONDS.sleep(1);

        // Verify call state
        synchronized (mCallStateChangeLock) {
            if (mCallState == TelephonyManager.CALL_STATE_OFFHOOK) {
                Log.d(TAG, "Wait for call state change to idle");
                mCallStateChangeLock.wait(WAIT_TIME_MS);
            }
            assertEquals(TelephonyManager.CALL_STATE_IDLE, mCallState);
        }

        // Register call disconnect cause callback
        mPreciseCallDisconnectCause = NO_DISCONNECT_CAUSE_AVAILABLE;

        sCallDisconnectCauseCallbackHandler.post(
                () -> {
                    sCallDisconnectCauseCallback = new CallDisconnectCauseListener();
                    ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                            sTelephonyManager,
                            (tm) ->
                                    tm.registerTelephonyCallback(
                                            mCallDisconnectCauseExecutor,
                                            sCallDisconnectCauseCallback));
                });

        // Trigger an incoming call
        Log.d(TAG, "Trigger an incoming call.");
        UusInfo[] uusInfo = new UusInfo[0];
        MockCallControlInfo callControlInfo = new MockCallControlInfo();
        callControlInfo.setActiveDurationInMs(1000);
        callControlInfo.setCallEndInfo(
                LastCallFailCause.TEMPORARY_FAILURE, "cts-test-call-failure");
        sMockModemManager.triggerIncomingVoiceCall(
                slotId, phoneNumber, uusInfo, null, callControlInfo);

        // Verify call state
        synchronized (mCallStateChangeLock) {
            if (mCallState == TelephonyManager.CALL_STATE_IDLE) {
                Log.d(TAG, "Wait for call state change to ringing");
                mCallStateChangeLock.wait(WAIT_TIME_MS);
            }
            assertEquals(TelephonyManager.CALL_STATE_RINGING, mCallState);
        }

        Log.d(TAG, "Answer the call");
        telecomManager.acceptRingingCall();

        // Verify call state
        synchronized (mCallStateChangeLock) {
            if (mCallState == TelephonyManager.CALL_STATE_RINGING) {
                Log.d(TAG, "Wait for call state change to offhook");
                mCallStateChangeLock.wait(WAIT_TIME_MS);
            }
            assertEquals(TelephonyManager.CALL_STATE_OFFHOOK, mCallState);
        }

        synchronized (mCallDisconnectCauseLock) {
            if (mPreciseCallDisconnectCause == NO_DISCONNECT_CAUSE_AVAILABLE) {
                Log.d(TAG, "Wait for disconnect cause to TEMPORARY_FAILURE");
                mCallDisconnectCauseLock.wait(WAIT_TIME_MS);
            }
            assertEquals(TEMPORARY_FAILURE, mPreciseCallDisconnectCause);
        }

        // Unregister service state change callback
        sTelephonyManager.unregisterTelephonyCallback(sServiceStateCallback);
        sServiceStateCallback = null;

        // Unregister call disconnect cause callback
        sTelephonyManager.unregisterTelephonyCallback(sCallDisconnectCauseCallback);
        sCallDisconnectCauseCallback = null;

        // Unregister call state change callback
        sTelephonyManager.unregisterTelephonyCallback(sCallStateCallback);
        sCallStateCallback = null;

        // Out of service
        sMockModemManager.changeNetworkService(
                slotId, MOCK_SIM_PROFILE_ID_TWN_FET, false, Domain.CS);

        // Remove the SIM
        sMockModemManager.removeSimCard(slotId);
    }

    @Test
    public void testDsdsServiceStateChange() throws Throwable {
        Log.d(TAG, "TelephonyManagerTestOnMockModem#testDsdsServiceStateChange");
        assumeTrue("Skip test: Not test on single SIM device", sIsMultiSimDevice);

        int slotId_0 = 0;
        int slotId_1 = 1;
        int subId_0;
        int subId_1;

        // Insert a SIM
        sMockModemManager.insertSimCard(slotId_0, MOCK_SIM_PROFILE_ID_TWN_CHT);
        sMockModemManager.insertSimCard(slotId_1, MOCK_SIM_PROFILE_ID_TWN_FET);

        TimeUnit.SECONDS.sleep(2);
        subId_0 = getActiveSubId(slotId_0);
        subId_1 = getActiveSubId(slotId_1);

        // Register service state change callback
        synchronized (mServiceStateChangeLock) {
            mServiceState = ServiceState.STATE_OUT_OF_SERVICE;
            mExpectedRegState = ServiceState.STATE_IN_SERVICE;
        }

        sServiceStateChangeCallbackHandler.post(
                () -> {
                    sServiceStateCallback = new ServiceStateListener();
                    ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                            sTelephonyManager,
                            (tm) ->
                                    tm.registerTelephonyCallback(
                                            mServiceStateChangeExecutor, sServiceStateCallback));
                });

        // Enter Service
        Log.d(TAG, "testDsdsServiceStateChange: Enter Service");
        sMockModemManager.changeNetworkService(slotId_0, MOCK_SIM_PROFILE_ID_TWN_CHT, true);
        sMockModemManager.changeNetworkService(slotId_1, MOCK_SIM_PROFILE_ID_TWN_FET, true);

        // Expect: Home State
        synchronized (mServiceStateChangeLock) {
            if (mServiceState != ServiceState.STATE_IN_SERVICE) {
                Log.d(TAG, "Wait for service state change to in service");
                waitForCondition(
                        () -> (ServiceState.STATE_IN_SERVICE == mServiceState),
                        mServiceStateChangeLock,
                        WAIT_TIME_MS);
            }
        }
        if (subId_0 > 0) {
            assertEquals(
                    getRegState(NetworkRegistrationInfo.DOMAIN_CS, subId_0),
                    NetworkRegistrationInfo.REGISTRATION_STATE_HOME);
        }
        if (subId_1 > 0) {
            TimeUnit.SECONDS.sleep(2);
            assertEquals(
                    getRegState(NetworkRegistrationInfo.DOMAIN_CS, subId_1),
                    NetworkRegistrationInfo.REGISTRATION_STATE_HOME);
        }

        assertTrue(subId_0 > 0 || subId_1 > 0);

        // Leave Service
        synchronized (mServiceStateChangeLock) {
            mExpectedRegState = ServiceState.STATE_OUT_OF_SERVICE;
        }

        Log.d(TAG, "testDsdsServiceStateChange: Leave Service");
        sMockModemManager.changeNetworkService(slotId_0, MOCK_SIM_PROFILE_ID_TWN_CHT, false);
        sMockModemManager.changeNetworkService(slotId_1, MOCK_SIM_PROFILE_ID_TWN_FET, false);

        // Expect: Seaching State
        synchronized (mServiceStateChangeLock) {
            if (mServiceState != ServiceState.STATE_OUT_OF_SERVICE) {
                Log.d(TAG, "Wait for service state change to out of service");
                waitForCondition(
                        () -> (ServiceState.STATE_OUT_OF_SERVICE == mServiceState),
                        mServiceStateChangeLock,
                        WAIT_TIME_MS);
            }
        }
        if (subId_0 > 0) {
            assertEquals(
                    getRegState(NetworkRegistrationInfo.DOMAIN_CS, subId_0),
                    NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_SEARCHING);
        }
        if (subId_1 > 0) {
            assertEquals(
                    getRegState(NetworkRegistrationInfo.DOMAIN_CS, subId_1),
                    NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_SEARCHING);
        }

        // Unregister service state change callback
        sTelephonyManager.unregisterTelephonyCallback(sServiceStateCallback);
        sServiceStateCallback = null;

        // Remove the SIM
        sMockModemManager.removeSimCard(slotId_0);
        sMockModemManager.removeSimCard(slotId_1);
    }

    /**
     * Verify the NotRestricted status of the device with READ_PHONE_STATE permission granted.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Test
    public void getCarrierRestrictionStatus_ReadPhoneState_NotRestricted() throws Exception {
        LinkedBlockingQueue<Integer> carrierRestrictionStatusResult = new LinkedBlockingQueue<>(1);
        try {
            sMockModemManager.updateCarrierRestrictionInfo(null,
                    CarrierRestrictions.CarrierRestrictionStatus.NOT_RESTRICTED);
            TelephonyUtils.addCarrierRestrictionStatusAllowList(
                    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(),
                    TelephonyUtils.CTS_APP_PACKAGE, 10110, mShaId);
            mResetCarrierStatusInfo = true;
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(sTelephonyManager,
                    tm -> tm.getCarrierRestrictionStatus(getContext().getMainExecutor(),
                            carrierRestrictionStatusResult::offer),
                    Manifest.permission.READ_PHONE_STATE);
        } catch (SecurityException ex) {
            fail();
        }
        Integer value = carrierRestrictionStatusResult.poll(TIMEOUT_IN_SEC_FOR_MODEM_CB,
                TimeUnit.SECONDS);
        assertNotNull(value);
        assertEquals(TelephonyManager.CARRIER_RESTRICTION_STATUS_NOT_RESTRICTED, value.intValue());
    }

    /**
     * Verify the Restricted status of the device with READ_PHONE_STATE permission granted.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Test
    public void getCarrierRestrictionStatus_ReadPhoneState_Restricted() throws Exception {
        LinkedBlockingQueue<Integer> carrierRestrictionStatusResult = new LinkedBlockingQueue<>(1);
        try {
            sMockModemManager.updateCarrierRestrictionInfo(getCarrierList(false),
                    CarrierRestrictions.CarrierRestrictionStatus.RESTRICTED);
            TelephonyUtils.addCarrierRestrictionStatusAllowList(
                    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(),
                    TelephonyUtils.CTS_APP_PACKAGE, 10110, mShaId);
            mResetCarrierStatusInfo = true;
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(sTelephonyManager,
                    tm -> tm.getCarrierRestrictionStatus(getContext().getMainExecutor(),
                            carrierRestrictionStatusResult::offer),
                    Manifest.permission.READ_PHONE_STATE);
        } catch (SecurityException ex) {
            fail();
        }
        Integer value = carrierRestrictionStatusResult.poll(TIMEOUT_IN_SEC_FOR_MODEM_CB,
                TimeUnit.SECONDS);
        assertNotNull(value);
        assertEquals(TelephonyManager.CARRIER_RESTRICTION_STATUS_RESTRICTED, value.intValue());
    }

    /**
     * Verify the Restricted To Caller status of the device with READ_PHONE_STATE permission
     * granted.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Test
    public void getCarrierRestrictionStatus_ReadPhoneState_RestrictedToCaller_MNO() throws Exception {
        LinkedBlockingQueue<Integer> carrierRestrictionStatusResult = new LinkedBlockingQueue<>(1);
        try {
            sMockModemManager.updateCarrierRestrictionInfo(getCarrierList(false),
                    CarrierRestrictions.CarrierRestrictionStatus.RESTRICTED);
            TelephonyUtils.addCarrierRestrictionStatusAllowList(
                    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(),
                    TelephonyUtils.CTS_APP_PACKAGE, 1839, mShaId);
            mResetCarrierStatusInfo = true;
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(sTelephonyManager,
                    tm -> tm.getCarrierRestrictionStatus(getContext().getMainExecutor(),
                            carrierRestrictionStatusResult::offer),
                    Manifest.permission.READ_PHONE_STATE);
        } catch (SecurityException ex) {
            fail();
        }
        Integer value = carrierRestrictionStatusResult.poll(TIMEOUT_IN_SEC_FOR_MODEM_CB,
                TimeUnit.SECONDS);
        assertNotNull(value);
        assertEquals(TelephonyManager.CARRIER_RESTRICTION_STATUS_RESTRICTED_TO_CALLER,
                value.intValue());
    }

    /**
     * Verify the Restricted status of the device with READ_PHONE_STATE permission granted.
     * MVNO operator reference without GID
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Test
    public void getCarrierRestrictionStatus_ReadPhoneState_RestrictedToCaller_MNO1() throws Exception {
        LinkedBlockingQueue<Integer> carrierRestrictionStatusResult = new LinkedBlockingQueue<>(1);
        try {
            sMockModemManager.updateCarrierRestrictionInfo(getCarrierList(false),
                    CarrierRestrictions.CarrierRestrictionStatus.RESTRICTED);
            TelephonyUtils.addCarrierRestrictionStatusAllowList(
                    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(),
                    TelephonyUtils.CTS_APP_PACKAGE, 2032, mShaId);
            mResetCarrierStatusInfo = true;
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(sTelephonyManager,
                    tm -> tm.getCarrierRestrictionStatus(getContext().getMainExecutor(),
                            carrierRestrictionStatusResult::offer),
                    Manifest.permission.READ_PHONE_STATE);
        } catch (SecurityException ex) {
            fail();
        }
        Integer value = carrierRestrictionStatusResult.poll(TIMEOUT_IN_SEC_FOR_MODEM_CB,
                TimeUnit.SECONDS);
        assertNotNull(value);
        assertEquals(TelephonyManager.CARRIER_RESTRICTION_STATUS_RESTRICTED,
                value.intValue());
    }

    /**
     * Verify the Restricted To Caller status of the device with READ_PHONE_STATE permission
     * granted.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Test
    public void getCarrierRestrictionStatus_ReadPhoneState_RestrictedToCaller_MVNO() throws Exception {
        LinkedBlockingQueue<Integer> carrierRestrictionStatusResult = new LinkedBlockingQueue<>(1);
        try {
            sMockModemManager.updateCarrierRestrictionInfo(getCarrierList(true),
                    CarrierRestrictions.CarrierRestrictionStatus.RESTRICTED);
            TelephonyUtils.addCarrierRestrictionStatusAllowList(
                    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(),
                    TelephonyUtils.CTS_APP_PACKAGE, 2032, mShaId);
            mResetCarrierStatusInfo = true;
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(sTelephonyManager,
                    tm -> tm.getCarrierRestrictionStatus(getContext().getMainExecutor(),
                            carrierRestrictionStatusResult::offer),
                    Manifest.permission.READ_PHONE_STATE);
        } catch (SecurityException ex) {
            fail();
        }
        Integer value = carrierRestrictionStatusResult.poll(TIMEOUT_IN_SEC_FOR_MODEM_CB,
                TimeUnit.SECONDS);
        assertNotNull(value);
        assertEquals(TelephonyManager.CARRIER_RESTRICTION_STATUS_RESTRICTED_TO_CALLER,
                value.intValue());
    }

    /**
     * Verify the Unknown status of the device with READ_PHONE_STATE permission granted.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Test
    public void getCarrierRestrictionStatus_ReadPhoneState_Unknown() throws Exception {
        LinkedBlockingQueue<Integer> carrierRestrictionStatusResult = new LinkedBlockingQueue<>(1);
        try {
            sMockModemManager.updateCarrierRestrictionInfo(null,
                    CarrierRestrictions.CarrierRestrictionStatus.UNKNOWN);
            TelephonyUtils.addCarrierRestrictionStatusAllowList(
                    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(),
                    TelephonyUtils.CTS_APP_PACKAGE, 10110, mShaId);
            mResetCarrierStatusInfo = true;
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(sTelephonyManager,
                    tm -> tm.getCarrierRestrictionStatus(getContext().getMainExecutor(),
                            carrierRestrictionStatusResult::offer),
                    Manifest.permission.READ_PHONE_STATE);
        } catch (SecurityException ex) {
            fail();
        }
        Integer value = carrierRestrictionStatusResult.poll(TIMEOUT_IN_SEC_FOR_MODEM_CB,
                TimeUnit.SECONDS);
        assertNotNull(value);
        assertEquals(TelephonyManager.CARRIER_RESTRICTION_STATUS_UNKNOWN, value.intValue());
    }

    private Carrier[] getCarrierList(boolean isGidRequired) {
        android.hardware.radio.sim.Carrier carrier
                = new android.hardware.radio.sim.Carrier();
        carrier.mcc = "310";
        carrier.mnc = "599";
        if (isGidRequired) {
            carrier.matchType = Carrier.MATCH_TYPE_GID1;
            carrier.matchData = "BA01450000000000";
        }
        Carrier[] carrierList = new Carrier[1];
        carrierList[0] = carrier;
        return carrierList;
    }

    /**
     * Test for primaryImei will return the IMEI that is set through mockModem
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Test
    public void testGetPrimaryImei() {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(hasTelephonyFeature(PackageManager.FEATURE_TELEPHONY_GSM)
                    && sTelephonyManager.getActiveModemCount() > 0);
        } else {
            assumeTrue(sTelephonyManager.getActiveModemCount() > 0);
        }

        String primaryImei = ShellIdentityUtils.invokeMethodWithShellPermissions(sTelephonyManager,
                (tm) -> tm.getPrimaryImei());
        assertNotNull(primaryImei);
        assertEquals(MockModemConfigInterface.DEFAULT_PHONE1_IMEI, primaryImei);
    }

    /**
     * Verify the change of PrimaryImei with respect to sim slot.
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    @Test
    public void onImeiMappingChanged() {
        // As first step verify the primary Imei against the default allocation
        assumeTrue(sTelephonyManager.getActiveModemCount() > 1);
        String primaryImei = ShellIdentityUtils.invokeMethodWithShellPermissions(sTelephonyManager,
                (tm) -> tm.getPrimaryImei());
        assertNotNull(primaryImei);
        assertEquals(MockModemConfigInterface.DEFAULT_PHONE1_IMEI, primaryImei);
        String slot0Imei = ShellIdentityUtils.invokeMethodWithShellPermissions(sTelephonyManager,
                (tm) -> tm.getImei(0));
        assertEquals(slot0Imei, primaryImei);

        // Second step is change the PrimaryImei to slot2 and verify the same.
        if (sMockModemManager.changeImeiMapping()) {
            Log.d(TAG, "Verifying primary IMEI after change in IMEI mapping");
            primaryImei = ShellIdentityUtils.invokeMethodWithShellPermissions(sTelephonyManager,
                    (tm) -> tm.getPrimaryImei());
            assertNotNull(primaryImei);
            assertEquals(MockModemConfigInterface.DEFAULT_PHONE1_IMEI, primaryImei);
            String slot1Imei = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    sTelephonyManager,
                    (tm) -> tm.getImei(1));
            assertEquals(slot1Imei, primaryImei);
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    @Test
    public void getAllowedCarriers_ReadPhoneState_Restricted() throws Exception {
        assumeTrue(isCarrierLockEnabled());
        sMockModemManager.updateCarrierRestrictionInfo(getCarrierList(false),
                CarrierRestrictions.CarrierRestrictionStatus.RESTRICTED);
        CarrierRestrictionRules rules =runWithShellPermissionIdentity(() -> {
            return sTelephonyManager.getCarrierRestrictionRules();
        }, android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        assertEquals(TelephonyManager.CARRIER_RESTRICTION_STATUS_RESTRICTED,
                rules.getCarrierRestrictionStatus());
    }

    @RequiresFlagsEnabled(Flags.FLAG_CARRIER_RESTRICTION_RULES_ENHANCEMENT)
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM,
            codeName = "VanillaIceCream")
    @Test
    public void getCarrierRestrictionRules() {
        assumeTrue(isCarrierLockEnabled());
        assumeTrue(Flags.carrierRestrictionRulesEnhancement());
        // settings the data in MockModem
        android.hardware.radio.sim.CarrierRestrictions carrierRestrictions =
                new android.hardware.radio.sim.CarrierRestrictions();
        android.hardware.radio.sim.CarrierInfo carrierInfo = getCarrierInfo("321", "654", "Airtel",
                null, null, null, null, null, null);
        carrierRestrictions.allowedCarrierInfoList = new android.hardware.radio.sim.CarrierInfo[1];
        carrierRestrictions.allowedCarrierInfoList[0] = carrierInfo;
        sMockModemManager.setCarrierRestrictionRules(carrierRestrictions,
                android.hardware.radio.sim.SimLockMultiSimPolicy.NO_MULTISIM_POLICY);

        // calling TM API with shell permissions.
        CarrierRestrictionRules carrierRules = ShellIdentityUtils.invokeMethodWithShellPermissions(
                sTelephonyManager, tm -> tm.getCarrierRestrictionRules());

        // Verify the received CarrierRestrictionRules
        assertTrue(carrierRules != null);
        assertTrue(carrierRules.getAllowedCarriersInfoList() != null);
        assertEquals(1, carrierRules.getAllowedCarriersInfoList().size());
        CarrierInfo carrierInfo1 = carrierRules.getAllowedCarriersInfoList().get(0);
        assertTrue(carrierInfo1 != null);
        assertEquals(carrierInfo1.getMcc(), "321");
        assertEquals(carrierInfo1.getMnc(), "654");
        assertEquals(carrierInfo1.getSpn(), "Airtel");
        assertEquals(carrierRules.getMultiSimPolicy(),
                CarrierRestrictionRules.MULTISIM_POLICY_NONE);
    }

    @RequiresFlagsEnabled(Flags.FLAG_CARRIER_RESTRICTION_RULES_ENHANCEMENT)
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM,
            codeName = "VanillaIceCream")
    @Test
    public void getCarrierRestrictionRules_WithEphlmnList() {
        assumeTrue(isCarrierLockEnabled());
        assumeTrue(Flags.carrierRestrictionRulesEnhancement());
        // settings the data in MockModem
        android.hardware.radio.sim.CarrierRestrictions carrierRestrictions =
                new android.hardware.radio.sim.CarrierRestrictions();
        List<android.hardware.radio.sim.Plmn> plmnList = new ArrayList<>();
        android.hardware.radio.sim.Plmn plmn1 = new android.hardware.radio.sim.Plmn();
        plmn1.mcc = "*";
        plmn1.mnc = "546";

        android.hardware.radio.sim.Plmn plmn2 = new android.hardware.radio.sim.Plmn();
        plmn2.mcc = "132";
        plmn2.mnc = "***";

        plmnList.add(plmn1);
        plmnList.add(plmn2);

        android.hardware.radio.sim.CarrierInfo carrierInfo = getCarrierInfo("*21", "**1", "Jio",
                null, null, null, plmnList, null, null);
        carrierRestrictions.allowedCarrierInfoList = new android.hardware.radio.sim.CarrierInfo[1];
        carrierRestrictions.allowedCarrierInfoList[0] = carrierInfo;
        sMockModemManager.setCarrierRestrictionRules(carrierRestrictions,
                SimLockMultiSimPolicy.ACTIVE_SERVICE_ON_ANY_SLOT_TO_UNBLOCK_OTHER_SLOTS);

        // calling TM API with shell permissions.
        CarrierRestrictionRules carrierRules = ShellIdentityUtils.invokeMethodWithShellPermissions(
                sTelephonyManager, tm -> tm.getCarrierRestrictionRules());

        // Verify the received CarrierRestrictionRules
        assertTrue(carrierRules != null);
        Log.d("TestonMockModem", "CTS carrierRules = " +carrierRules);
        assertTrue(carrierRules.getAllowedCarriersInfoList() != null);
        assertEquals(1, carrierRules.getAllowedCarriersInfoList().size());
        CarrierInfo carrierInfo1 = carrierRules.getAllowedCarriersInfoList().get(0);
        assertTrue(carrierInfo1 != null);
        assertEquals(carrierInfo1.getMcc(), "*21");
        assertEquals(carrierInfo1.getMnc(), "**1");
        assertEquals(carrierInfo1.getSpn(), "Jio");
        assertTrue(carrierInfo1.getEhplmn() != null);
        assertTrue(carrierInfo1.getEhplmn().size() == 2);
        String ehplmn1 = carrierInfo1.getEhplmn().get(0);
        String ehplmn2 = carrierInfo1.getEhplmn().get(1);
        String[] ehplmn1Tokens = ehplmn1.split(",");
        String[] ehplmn2Tokens = ehplmn2.split(",");
        assertEquals(ehplmn1Tokens[0], "*");
        assertEquals(ehplmn1Tokens[1], "546");
        assertEquals(ehplmn2Tokens[0], "132");
        assertEquals(ehplmn2Tokens[1], "***");
        assertEquals(carrierRules.getMultiSimPolicy(),
                CarrierRestrictionRules.
                        MULTISIM_POLICY_ACTIVE_SERVICE_ON_ANY_SLOT_TO_UNBLOCK_OTHER_SLOTS);
    }

    private android.hardware.radio.sim.CarrierInfo getCarrierInfo(String mcc, String mnc,
            String spn, String gid1, String gid2, String imsi,
            List<android.hardware.radio.sim.Plmn> ehplmn, String iccid, String impi) {
        android.hardware.radio.sim.CarrierInfo carrierInfo =
                new android.hardware.radio.sim.CarrierInfo();
        carrierInfo.mcc = mcc;
        carrierInfo.mnc = mnc;
        carrierInfo.spn = spn;
        carrierInfo.gid1 = gid1;
        carrierInfo.gid2 = gid2;
        carrierInfo.imsiPrefix = imsi;
        carrierInfo.ehplmn = ehplmn;
        carrierInfo.iccid = iccid;
        carrierInfo.impi = impi;
        return carrierInfo;
    }

    private boolean isCarrierLockEnabled() {
        return InstrumentationRegistry.getInstrumentation().getContext()
                .getPackageManager().hasSystemFeature(
                        PackageManager.FEATURE_TELEPHONY_CARRIERLOCK);
    }
}
