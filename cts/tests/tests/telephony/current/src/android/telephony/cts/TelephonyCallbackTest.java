/*
 * Copyright (C) 2009 The Android Open Source Project
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

import static androidx.test.InstrumentationRegistry.getContext;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeTrue;

import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.Looper;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.telephony.Annotation.RadioPowerState;
import android.telephony.Annotation.SimActivationState;
import android.telephony.BarringInfo;
import android.telephony.CallState;
import android.telephony.CellIdentity;
import android.telephony.CellInfo;
import android.telephony.CellLocation;
import android.telephony.LinkCapacityEstimate;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.PhysicalChannelConfig;
import android.telephony.PreciseCallState;
import android.telephony.PreciseDataConnectionState;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SmsManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyDisplayInfo;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyManager.DataEnabledReason;
import android.telephony.TelephonyManager.EmergencyCallbackModeStopReason;
import android.telephony.TelephonyManager.EmergencyCallbackModeType;
import android.telephony.cts.util.TelephonyUtils;
import android.telephony.emergency.EmergencyNumber;
import android.telephony.ims.ImsReasonInfo;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.internal.telephony.flags.Flags;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

public class TelephonyCallbackTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    public static final long WAIT_TIME = 1000;

    private static final String TEST_EMERGENCY_NUMBER = "998877665544332211";

    private boolean mOnActiveDataSubscriptionIdChanged;
    private boolean mOnCallForwardingIndicatorChangedCalled;
    private boolean mOnCallStateChangedCalled;
    private boolean mOnCellLocationChangedCalled;
    private boolean mOnUserMobileDataStateChanged;
    private boolean mOnDataActivityCalled;
    private boolean mOnDataConnectionStateChangedCalled;
    private boolean mOnDataConnectionStateChangedWithNetworkTypeCalled;
    private boolean mOnMessageWaitingIndicatorChangedCalled;
    private boolean mOnCellInfoChangedCalled;
    private boolean mOnServiceStateChangedCalled;
    private boolean mOnPreciseCallStateChangedCalled;
    private boolean mOnCallStatesChangedCalled;
    private boolean mOnCallDisconnectCauseChangedCalled;
    private boolean mOnImsCallDisconnectCauseChangedCalled;
    private ImsReasonInfo mImsReasonInfo;
    private EmergencyNumber mOnOutgoingSmsEmergencyNumberChanged;
    private boolean mOnPreciseDataConnectionStateChanged;
    private boolean mOnRadioPowerStateChangedCalled;
    private boolean mVoiceActivationStateChangedCalled;
    private boolean mSrvccStateChangedCalled;
    private boolean mOnBarringInfoChangedCalled;
    private boolean mOnRegistrationFailedCalled;
    private boolean mOnTelephonyDisplayInfoChanged;
    private boolean mOnPhysicalChannelConfigCalled;
    private boolean mOnDataEnabledChangedCalled;
    private boolean mOnLinkCapacityEstimateChangedCalled;
    private boolean mOnEmergencyCallbackModeChangedCalled;
    @RadioPowerState
    private int mRadioPowerState;
    @SimActivationState
    private int mVoiceActivationState;
    private ServiceState mServiceState;
    private boolean mOnAllowedNetworkTypesChangedCalled;
    private int mAllowedNetworkTypeReason = -1;
    private long mAllowedNetworkTypeValue = -1;
    private BarringInfo mBarringInfo;
    private PreciseDataConnectionState mPreciseDataConnectionState;
    private PreciseCallState mPreciseCallState;
    private List<CallState> mCallStateList;
    private SignalStrength mSignalStrength;
    private TelephonyManager mTelephonyManager;
    private final Object mLock = new Object();
    private static final String TAG = "TelephonyCallbackTest";
    private static ConnectivityManager mCm;
    private PackageManager mPackageManager;
    private static final List<Integer> DATA_CONNECTION_STATE = Arrays.asList(
            TelephonyManager.DATA_CONNECTED,
            TelephonyManager.DATA_DISCONNECTED,
            TelephonyManager.DATA_CONNECTING,
            TelephonyManager.DATA_UNKNOWN,
            TelephonyManager.DATA_SUSPENDED
    );
    private static final List<Integer> PRECISE_CALL_STATE = Arrays.asList(
            PreciseCallState.PRECISE_CALL_STATE_ACTIVE,
            PreciseCallState.PRECISE_CALL_STATE_ALERTING,
            PreciseCallState.PRECISE_CALL_STATE_DIALING,
            PreciseCallState.PRECISE_CALL_STATE_DISCONNECTED,
            PreciseCallState.PRECISE_CALL_STATE_DISCONNECTING,
            PreciseCallState.PRECISE_CALL_STATE_HOLDING,
            PreciseCallState.PRECISE_CALL_STATE_IDLE,
            PreciseCallState.PRECISE_CALL_STATE_INCOMING,
            PreciseCallState.PRECISE_CALL_STATE_NOT_VALID,
            PreciseCallState.PRECISE_CALL_STATE_WAITING
    );

    private static final List<Integer> NETWORK_TYPES = Arrays.asList(
            TelephonyManager.NETWORK_TYPE_UNKNOWN,
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_CDMA,
            TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_1xRTT,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_HSPAP,
            TelephonyManager.NETWORK_TYPE_LTE,
            TelephonyManager.NETWORK_TYPE_EHRPD,
            TelephonyManager.NETWORK_TYPE_GSM,
            TelephonyManager.NETWORK_TYPE_IWLAN,
            TelephonyManager.NETWORK_TYPE_NR
    );

    private final Executor mSimpleExecutor = Runnable::run;

    @Before
    public void setUp() throws Exception {
        mPackageManager = getContext().getPackageManager();
        assumeTrue("Skipping test that requires FEATURE_TELEPHONY",
                mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY));

        mTelephonyManager =
                (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        try {
            mTelephonyManager.getHalVersion(TelephonyManager.HAL_SERVICE_RADIO);
        } catch (IllegalStateException e) {
            assumeNoException("Skipping tests because Telephony service is null", e);
        }
        mCm = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @Test
    public void testTelephonyCallback() {

        Looper.prepare();
        new TelephonyCallback();
    }

    private void registerTelephonyCallbackWithPermission(@NonNull TelephonyCallback callback) {
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                (tm) -> tm.registerTelephonyCallback(mSimpleExecutor, callback));
    }

    private void registerTelephonyCallback(@NonNull TelephonyCallback callback) {
        mTelephonyManager.registerTelephonyCallback(mSimpleExecutor, callback);
    }

    private void registerTelephonyCallback(@NonNull TelephonyCallback callback,
            boolean renounceFine, boolean renounceCoarse) {
        int includeLocationData = TelephonyManager.INCLUDE_LOCATION_DATA_FINE;
        if (renounceFine && renounceCoarse) {
            includeLocationData = TelephonyManager.INCLUDE_LOCATION_DATA_NONE;
        } else if (renounceFine) {
            includeLocationData = TelephonyManager.INCLUDE_LOCATION_DATA_COARSE;
        }
        mTelephonyManager.registerTelephonyCallback(includeLocationData, mSimpleExecutor,
                callback);
    }

    private void unRegisterTelephonyCallback(boolean condition,
                                             @NonNull TelephonyCallback callback) throws Exception {
        synchronized (mLock) {
            condition = false;
            mTelephonyManager.unregisterTelephonyCallback(callback);
            mLock.wait(WAIT_TIME);

            assertFalse(condition);
        }
    }

    private ServiceStateListener mServiceStateCallback;

    private class ServiceStateListener extends TelephonyCallback
            implements TelephonyCallback.ServiceStateListener {
        @Override
        public void onServiceStateChanged(ServiceState serviceState) {
            synchronized (mLock) {
                mOnServiceStateChangedCalled = true;
                mServiceState = serviceState;
                mLock.notify();
            }
        }
    }

    @Test
    public void testOnServiceStateChangedByRegisterTelephonyCallback() throws Throwable {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(mPackageManager.hasSystemFeature(
                    PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));
        }

        assertFalse(mOnServiceStateChangedCalled);


        mServiceStateCallback = new ServiceStateListener();
        registerTelephonyCallback(mServiceStateCallback);

        synchronized (mLock) {
            if (!mOnServiceStateChangedCalled) {
                mLock.wait(WAIT_TIME);
            }
        }

        assertTrue(mOnServiceStateChangedCalled);

        // Test unregister
        unRegisterTelephonyCallback(mOnServiceStateChangedCalled, mServiceStateCallback);
    }

    @Test
    public void testOnServiceStateChangedByRegisterTelephonyCallbackWithLocationRenounce()
            throws Throwable {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(mPackageManager.hasSystemFeature(
                    PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));
        }

        if (mCm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE) == null) {
            Log.d(TAG, "Skipping test that requires ConnectivityManager.TYPE_MOBILE");
            return;
        }

        assertFalse(mOnServiceStateChangedCalled);

        mServiceStateCallback = new ServiceStateListener();
        registerTelephonyCallback(mServiceStateCallback, true, true);

        synchronized (mLock) {
            if (!mOnServiceStateChangedCalled) {
                mLock.wait(WAIT_TIME);
            }
        }

        assertTrue(mOnServiceStateChangedCalled);
        assertServiceStateLocationSanitization(mServiceState);

        // Test unregister
        unRegisterTelephonyCallback(mOnServiceStateChangedCalled, mServiceStateCallback);
    }

    @Test
    public void testOnServiceStateChangedByRegisterTelephonyCallbackWithCoarseRenounce()
            throws Throwable {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(mPackageManager.hasSystemFeature(
                    PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));
        }

        if (mCm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE) == null) {
            Log.d(TAG, "Skipping test that requires ConnectivityManager.TYPE_MOBILE");
            return;
        }

        assertFalse(mOnServiceStateChangedCalled);

        mServiceStateCallback = new ServiceStateListener();
        registerTelephonyCallback(mServiceStateCallback, false, true);

        synchronized (mLock) {
            if (!mOnServiceStateChangedCalled) {
                mLock.wait(WAIT_TIME);
            }
        }

        assertTrue(mOnServiceStateChangedCalled);

        // Test unregister
        unRegisterTelephonyCallback(mOnServiceStateChangedCalled, mServiceStateCallback);
    }

    @Test
    public void testOnServiceStateChangedByRegisterTelephonyCallbackWithFineOnlyRenounce()
            throws Throwable {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(mPackageManager.hasSystemFeature(
                    PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));
        }

        if (mCm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE) == null) {
            Log.d(TAG, "Skipping test that requires ConnectivityManager.TYPE_MOBILE");
            return;
        }

        assertFalse(mOnServiceStateChangedCalled);

        mServiceStateCallback = new ServiceStateListener();
        registerTelephonyCallback(mServiceStateCallback, true, false);

        synchronized (mLock) {
            if (!mOnServiceStateChangedCalled) {
                mLock.wait(WAIT_TIME);
            }
        }

        assertTrue(mOnServiceStateChangedCalled);
        assertServiceStateFineLocationSanitization(mServiceState);

        // Test unregister
        unRegisterTelephonyCallback(mOnServiceStateChangedCalled, mServiceStateCallback);
    }

    private void assertServiceStateFineLocationSanitization(ServiceState state) {
        if (state == null) return;

        if (state.getNetworkRegistrationInfoList() != null) {
            for (NetworkRegistrationInfo nrs : state.getNetworkRegistrationInfoList()) {
                assertNull(nrs.getCellIdentity());
            }
        }
    }

    private void assertServiceStateLocationSanitization(ServiceState state) {
        if (state == null) return;
        assertServiceStateFineLocationSanitization(state);
        assertTrue(TextUtils.isEmpty(state.getOperatorAlphaLong()));
        assertTrue(TextUtils.isEmpty(state.getOperatorAlphaShort()));
        assertTrue(TextUtils.isEmpty(state.getOperatorNumeric()));
    }

    @Test
    public void testOnUnRegisterFollowedByRegisterTelephonyCallback() throws Throwable {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(mPackageManager.hasSystemFeature(
                    PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));
        }

        assertFalse(mOnServiceStateChangedCalled);

        mServiceStateCallback = new ServiceStateListener();
        registerTelephonyCallback(mServiceStateCallback);

        synchronized (mLock) {
            if (!mOnServiceStateChangedCalled) {
                mLock.wait(WAIT_TIME);
            }
        }

        assertTrue(mOnServiceStateChangedCalled);

        // un-register
        if (mServiceStateCallback != null) {
            // un-register the listener
            mTelephonyManager.unregisterTelephonyCallback(mServiceStateCallback);
        }
        // reset after un-register
        mOnServiceStateChangedCalled = false;
        synchronized (mLock) {
            if (!mOnServiceStateChangedCalled) {
                mLock.wait(WAIT_TIME);
            }
        }
        assertFalse(mOnServiceStateChangedCalled);

        // re-register the listener
        registerTelephonyCallback(mServiceStateCallback);
        synchronized (mLock) {
            if (!mOnServiceStateChangedCalled) {
                mLock.wait(WAIT_TIME);
            }
        }

        assertTrue(mOnServiceStateChangedCalled);

        // Test unregister
        unRegisterTelephonyCallback(mOnServiceStateChangedCalled, mServiceStateCallback);
    }

    private SignalStrengthsListener mSignalStrengthsCallback;

    private class SignalStrengthsListener extends TelephonyCallback
            implements TelephonyCallback.SignalStrengthsListener {
        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            synchronized (mLock) {
                mSignalStrength = signalStrength;
                mLock.notify();
            }
        }
    }

    private void getSignalStrength() {
        mSignalStrength.getCdmaDbm();
        mSignalStrength.getCdmaEcio();
        mSignalStrength.getEvdoDbm();
        mSignalStrength.getEvdoEcio();
        mSignalStrength.getEvdoSnr();
        mSignalStrength.getGsmBitErrorRate();
        mSignalStrength.getGsmSignalStrength();
        mSignalStrength.isGsm();
        mSignalStrength.getLevel();
    }

    @Test
    public void testOnSignalStrengthsChangedByRegisterTelephonyCallback() throws Throwable {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(mPackageManager.hasSystemFeature(
                    PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));
        }

        assertTrue(mSignalStrength == null);

        mSignalStrengthsCallback = new SignalStrengthsListener();
        registerTelephonyCallback(mSignalStrengthsCallback);

        synchronized (mLock) {
            if (mSignalStrength == null) {
                mLock.wait(WAIT_TIME);
            }
        }

        assertTrue(mSignalStrength != null);
        // Call SignalStrength methods to make sure they do not throw any exceptions
        getSignalStrength();

        // Test unregister
        unRegisterTelephonyCallback(mSignalStrength == null, mSignalStrengthsCallback);
    }

    private MessageWaitingIndicatorListener mMessageWaitingIndicatorCallback;

    private class MessageWaitingIndicatorListener extends TelephonyCallback
            implements TelephonyCallback.MessageWaitingIndicatorListener {
        @Override
        public void onMessageWaitingIndicatorChanged(boolean mwi) {
            synchronized (mLock) {
                mOnMessageWaitingIndicatorChangedCalled = true;
                mLock.notify();
            }
        }
    }

    @Test
    public void testOnMessageWaitingIndicatorChangedByRegisterTelephonyCallback()
            throws Throwable {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(mPackageManager.hasSystemFeature(
                    PackageManager.FEATURE_TELEPHONY_MESSAGING));
        }

        assertFalse(mOnMessageWaitingIndicatorChangedCalled);

        mMessageWaitingIndicatorCallback = new MessageWaitingIndicatorListener();
        registerTelephonyCallback(mMessageWaitingIndicatorCallback);

        synchronized (mLock) {
            if (!mOnMessageWaitingIndicatorChangedCalled) {
                mLock.wait(WAIT_TIME);
            }
        }

        assertTrue(mOnMessageWaitingIndicatorChangedCalled);

        // Test unregister
        unRegisterTelephonyCallback(mOnMessageWaitingIndicatorChangedCalled,
                mMessageWaitingIndicatorCallback);
    }

    private PreciseCallStateListener mPreciseCallStateCallback;

    private class PreciseCallStateListener extends TelephonyCallback
            implements TelephonyCallback.PreciseCallStateListener {
        @Override
        public void onPreciseCallStateChanged(PreciseCallState preciseCallState) {
            synchronized (mLock) {
                mOnPreciseCallStateChangedCalled = true;
                mPreciseCallState = preciseCallState;
                mLock.notify();
            }
        }
    }

    @Test
    public void testOnPreciseCallStateChangedByRegisterTelephonyCallback() throws Throwable {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_CALLING));
        }

        assertThat(mOnPreciseCallStateChangedCalled).isFalse();

        mPreciseCallStateCallback = new PreciseCallStateListener();
        registerTelephonyCallbackWithPermission(mPreciseCallStateCallback);

        synchronized (mLock) {
            if (!mOnPreciseCallStateChangedCalled) {
                mLock.wait(WAIT_TIME);
            }
        }
        Log.d(TAG, "testOnPreciseCallStateChangedByRegisterTelephonyCallback: "
                + mOnPreciseCallStateChangedCalled);

        assertThat(mOnPreciseCallStateChangedCalled).isTrue();
        assertThat(mPreciseCallState.getForegroundCallState()).isIn(PRECISE_CALL_STATE);
        assertThat(mPreciseCallState.getBackgroundCallState()).isIn(PRECISE_CALL_STATE);
        assertThat(mPreciseCallState.getRingingCallState()).isIn(PRECISE_CALL_STATE);

        // Test unregister
        unRegisterTelephonyCallback(mOnPreciseCallStateChangedCalled,
                mPreciseCallStateCallback);
    }

    private CallAttributesListener mCallAttributesListener;

    private class CallAttributesListener extends TelephonyCallback
            implements TelephonyCallback.CallAttributesListener {
        @Override
        public void onCallStatesChanged(List<CallState> callStateList) {
            synchronized (mLock) {
                mOnCallStatesChangedCalled = true;
                mCallStateList = callStateList;
                mLock.notify();
            }
        }
    }

    @Test
    public void testOnCallStatesChangedByRegisterTelephonyCallback() throws Throwable {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_CALLING));
        } else {
            if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
                Log.d(TAG, "Skipping test that requires FEATURE_TELEPHONY");
                return;
            }
        }

        assertThat(mOnCallStatesChangedCalled).isFalse();

        mCallAttributesListener = new CallAttributesListener();
        registerTelephonyCallbackWithPermission(mCallAttributesListener);

        synchronized (mLock) {
            if (!mOnCallStatesChangedCalled) {
                mLock.wait(WAIT_TIME);
            }
        }
        Log.d(TAG, "testOnCallStatesChangedByRegisterTelephonyCallback: "
                + mOnCallStatesChangedCalled);

        assertThat(mOnCallStatesChangedCalled).isTrue();
        assertNotNull(mCallStateList);
        if (mCallStateList.size() > 0) {
            assertThat(mCallStateList.get(0).getCallState()).isIn(PRECISE_CALL_STATE);
            assertThat(mCallStateList.get(0).getNetworkType()).isIn(NETWORK_TYPES);
        }

        // Test unregister
        unRegisterTelephonyCallback(mOnCallStatesChangedCalled,
                mCallAttributesListener);
    }

    private CallDisconnectCauseListener mCallDisconnectCauseCallback;

    private class CallDisconnectCauseListener extends TelephonyCallback
            implements TelephonyCallback.CallDisconnectCauseListener {
        @Override
        public void onCallDisconnectCauseChanged(int disconnectCause,
                                                 int preciseDisconnectCause) {
            synchronized (mLock) {
                mOnCallDisconnectCauseChangedCalled = true;
                mLock.notify();
            }
        }
    }

    @Test
    public void testOnCallDisconnectCauseChangedByRegisterTelephonyCallback() throws Throwable {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_CALLING));
        }

        assertThat(mOnCallDisconnectCauseChangedCalled).isFalse();

        mCallDisconnectCauseCallback = new CallDisconnectCauseListener();
        registerTelephonyCallbackWithPermission(mCallDisconnectCauseCallback);

        synchronized (mLock) {
            if (!mOnCallDisconnectCauseChangedCalled) {
                mLock.wait(WAIT_TIME);
            }
        }

        assertThat(mOnCallDisconnectCauseChangedCalled).isTrue();

        // Test unregister
        unRegisterTelephonyCallback(mOnCallDisconnectCauseChangedCalled,
                mCallDisconnectCauseCallback);
    }

    private ImsCallDisconnectCauseListener mImsCallDisconnectCauseCallback;

    private class ImsCallDisconnectCauseListener extends TelephonyCallback
            implements TelephonyCallback.ImsCallDisconnectCauseListener {
        @Override
        public void onImsCallDisconnectCauseChanged(ImsReasonInfo imsReason) {
            synchronized (mLock) {
                mOnImsCallDisconnectCauseChangedCalled = true;
                mImsReasonInfo = imsReason;
                mLock.notify();
            }
        }
    }

    @Test
    public void testOnImsCallDisconnectCauseChangedByRegisterTelephonyCallback() throws Throwable {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_CALLING));
        }

        assertThat(mOnImsCallDisconnectCauseChangedCalled).isFalse();

        mImsCallDisconnectCauseCallback = new ImsCallDisconnectCauseListener();
        registerTelephonyCallbackWithPermission(mImsCallDisconnectCauseCallback);

        synchronized (mLock) {
            if (!mOnImsCallDisconnectCauseChangedCalled) {
                mLock.wait(WAIT_TIME);
            }
        }

        assertThat(mOnImsCallDisconnectCauseChangedCalled).isTrue();
        assertNotNull(mImsReasonInfo);

        // Test unregister
        unRegisterTelephonyCallback(mOnImsCallDisconnectCauseChangedCalled,
                mImsCallDisconnectCauseCallback);
    }

    private SrvccStateListener mSrvccStateCallback;

    private class SrvccStateListener extends TelephonyCallback
            implements TelephonyCallback.SrvccStateListener {
        @Override
        public void onSrvccStateChanged(int state) {
            synchronized (mLock) {
                mSrvccStateChangedCalled = true;
                mLock.notify();
            }
        }
    }

    @Test
    public void testOnSrvccStateChangedByRegisterTelephonyCallback() throws Throwable {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_IMS));
        }

        assertThat(mSrvccStateChangedCalled).isFalse();

        mSrvccStateCallback = new SrvccStateListener();
        registerTelephonyCallbackWithPermission(mSrvccStateCallback);

        synchronized (mLock) {
            if (!mSrvccStateChangedCalled) {
                mLock.wait(WAIT_TIME);
            }
        }
        Log.d(TAG, "testOnSrvccStateChangedByRegisterTelephonyCallback");

        assertThat(mSrvccStateChangedCalled).isTrue();

        // Test unregister
        unRegisterTelephonyCallback(mSrvccStateChangedCalled, mSrvccStateCallback);
    }

    private RadioPowerStateListener mRadioPowerStateCallback;

    private class RadioPowerStateListener extends TelephonyCallback
            implements TelephonyCallback.RadioPowerStateListener {
        @Override
        public void onRadioPowerStateChanged(int state) {
            synchronized (mLock) {
                mRadioPowerState = state;
                mOnRadioPowerStateChangedCalled = true;
                mLock.notify();
            }
        }
    }

    @Test
    public void testOnRadioPowerStateChangedByRegisterTelephonyCallback() throws Throwable {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(mPackageManager.hasSystemFeature(
                    PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));
        }

        assertThat(mOnRadioPowerStateChangedCalled).isFalse();

        mRadioPowerStateCallback = new RadioPowerStateListener();
        registerTelephonyCallbackWithPermission(mRadioPowerStateCallback);

        synchronized (mLock) {
            if (!mOnRadioPowerStateChangedCalled) {
                mLock.wait(WAIT_TIME);
            }
        }
        Log.d(TAG, "testOnRadioPowerStateChangedByRegisterTelephonyCallback: "
                + mRadioPowerState);

        assertThat(mTelephonyManager.getRadioPowerState()).isEqualTo(mRadioPowerState);

        // Test unregister
        unRegisterTelephonyCallback(mOnRadioPowerStateChangedCalled,
                mRadioPowerStateCallback);
    }

    private VoiceActivationStateListener mVoiceActivationStateCallback;

    private class VoiceActivationStateListener extends TelephonyCallback
            implements TelephonyCallback.VoiceActivationStateListener {
        @Override
        public void onVoiceActivationStateChanged(int state) {
            synchronized (mLock) {
                mVoiceActivationState = state;
                mVoiceActivationStateChangedCalled = true;
                mLock.notify();
            }
        }
    }

    @Test
    public void testOnVoiceActivationStateChangedByRegisterTelephonyCallback() throws Throwable {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_CALLING));
        }

        assertThat(mVoiceActivationStateChangedCalled).isFalse();

        mVoiceActivationStateCallback = new VoiceActivationStateListener();
        registerTelephonyCallbackWithPermission(mVoiceActivationStateCallback);

        synchronized (mLock) {
            if (!mVoiceActivationStateChangedCalled) {
                mLock.wait(WAIT_TIME);
            }
        }
        Log.d(TAG, "testOnVoiceActivationStateChangedByRegisterTelephonyCallback: "
                + mVoiceActivationState);
        int state = ShellIdentityUtils.invokeMethodWithShellPermissions(mTelephonyManager,
                (tm) -> tm.getVoiceActivationState());

        assertEquals(state, mVoiceActivationState);

        // Test unregister
        unRegisterTelephonyCallback(mVoiceActivationStateChangedCalled,
                mVoiceActivationStateCallback);
    }

    private PreciseDataConnectionStateListener mPreciseDataConnectionStateCallback;

    private class PreciseDataConnectionStateListener extends TelephonyCallback
            implements TelephonyCallback.PreciseDataConnectionStateListener {
        @Override
        public void onPreciseDataConnectionStateChanged(
                PreciseDataConnectionState state) {
            synchronized (mLock) {
                mOnPreciseDataConnectionStateChanged = true;
                mPreciseDataConnectionState = state;
                mLock.notify();
            }
        }
    }

    private void getPreciseDataConnectionState() {
        // Ensure that no exceptions are thrown
        mPreciseDataConnectionState.getNetworkType();
        mPreciseDataConnectionState.getLinkProperties();
        mPreciseDataConnectionState.getLastCauseCode();
        mPreciseDataConnectionState.getLinkProperties();
        mPreciseDataConnectionState.getApnSetting();
        mPreciseDataConnectionState.getTransportType();
        mPreciseDataConnectionState.getId();

        // Deprecated in R
        assertEquals(mPreciseDataConnectionState.getDataConnectionState(),
                mPreciseDataConnectionState.getState());
        assertEquals(mPreciseDataConnectionState.getDataConnectionFailCause(),
                mPreciseDataConnectionState.getLastCauseCode());

        // Superseded in R by getApnSetting()
        mPreciseDataConnectionState.getDataConnectionApnTypeBitMask();
        mPreciseDataConnectionState.getDataConnectionApn();
    }

    @Test
    public void testOnPreciseDataConnectionStateChangedByRegisterTelephonyCallback()
            throws Throwable {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_DATA));
        }

        assertThat(mOnCallDisconnectCauseChangedCalled).isFalse();

        mPreciseDataConnectionStateCallback =
                new PreciseDataConnectionStateListener();
        registerTelephonyCallbackWithPermission(mPreciseDataConnectionStateCallback);

        synchronized (mLock) {
            if (!mOnPreciseDataConnectionStateChanged) {
                mLock.wait(WAIT_TIME);
            }
        }

        assertThat(mOnPreciseDataConnectionStateChanged).isTrue();
        assertThat(mPreciseDataConnectionState.getState())
                .isIn(DATA_CONNECTION_STATE);

        getPreciseDataConnectionState();
        // Test unregister
        unRegisterTelephonyCallback(mOnPreciseDataConnectionStateChanged,
                mPreciseDataConnectionStateCallback);
    }

    private DisplayInfoListener mDisplayInfoCallback;

    private class DisplayInfoListener extends TelephonyCallback
            implements TelephonyCallback.DisplayInfoListener {
        @Override
        public void onDisplayInfoChanged(TelephonyDisplayInfo displayInfo) {
            synchronized (mLock) {
                mOnTelephonyDisplayInfoChanged = true;
                mLock.notify();
            }
        }
    }

    @Test
    public void testOnDisplayInfoChangedByRegisterTelephonyCallback() throws Exception {
        assertThat(mOnTelephonyDisplayInfoChanged).isFalse();

        mDisplayInfoCallback = new DisplayInfoListener();
        registerTelephonyCallback(mDisplayInfoCallback);

        synchronized (mLock) {
            if (!mOnTelephonyDisplayInfoChanged) {
                mLock.wait(WAIT_TIME);
            }
        }
        assertTrue(mOnTelephonyDisplayInfoChanged);

        // Test unregister
        unRegisterTelephonyCallback(mOnTelephonyDisplayInfoChanged, mDisplayInfoCallback);
    }

    private CallForwardingIndicatorListener mCallForwardingIndicatorCallback;

    private class CallForwardingIndicatorListener extends TelephonyCallback
            implements TelephonyCallback.CallForwardingIndicatorListener {
        @Override
        public void onCallForwardingIndicatorChanged(boolean cfi) {
            synchronized (mLock) {
                mOnCallForwardingIndicatorChangedCalled = true;
                mLock.notify();
            }
        }
    }

    @Test
    public void testOnCallForwardingIndicatorChangedByRegisterTelephonyCallback()
            throws Throwable {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_CALLING));
        }

        assertFalse(mOnCallForwardingIndicatorChangedCalled);

        mCallForwardingIndicatorCallback = new CallForwardingIndicatorListener();
        registerTelephonyCallback(mCallForwardingIndicatorCallback);

        synchronized (mLock) {
            if (!mOnCallForwardingIndicatorChangedCalled) {
                mLock.wait(WAIT_TIME);
            }
        }

        assertTrue(mOnCallForwardingIndicatorChangedCalled);

        // Test unregister
        unRegisterTelephonyCallback(mOnCallForwardingIndicatorChangedCalled,
                mCallForwardingIndicatorCallback);
    }

    private CellLocationListener mCellLocationCallback;

    private class CellLocationListener extends TelephonyCallback
            implements TelephonyCallback.CellLocationListener {
        @Override
        public void onCellLocationChanged(CellLocation location) {
            synchronized (mLock) {
                mOnCellLocationChangedCalled = true;
                mLock.notify();
            }
        }
    }

    @Test
    public void testOnCellLocationChangedByRegisterTelephonyCallback() throws Throwable {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(mPackageManager.hasSystemFeature(
                    PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));
        }

        assertFalse(mOnCellLocationChangedCalled);

        TelephonyManagerTest.grantLocationPermissions();

        mCellLocationCallback = new CellLocationListener();
        registerTelephonyCallback(mCellLocationCallback);

        synchronized (mLock) {
            if (!mOnCellLocationChangedCalled) {
                mLock.wait(WAIT_TIME);
            }
        }

        assertTrue(mOnCellLocationChangedCalled);

        // Test unregister
        unRegisterTelephonyCallback(mOnCellLocationChangedCalled, mCellLocationCallback);
    }

    private CallStateListener mCallStateCallback;

    private class CallStateListener extends TelephonyCallback
            implements TelephonyCallback.CallStateListener {
        @Override
        public void onCallStateChanged(int state) {
            synchronized (mLock) {
                mOnCallStateChangedCalled = true;
                mLock.notify();
            }
        }
    }

    @Test
    public void testOnCallStateChangedByRegisterTelephonyCallback() throws Throwable {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_CALLING));
        }

        assertFalse(mOnCallStateChangedCalled);

        mCallStateCallback = new CallStateListener();
        registerTelephonyCallback(mCallStateCallback);

        synchronized (mLock) {
            if (!mOnCallStateChangedCalled) {
                mLock.wait(WAIT_TIME);
            }
        }

        assertTrue(mOnCallStateChangedCalled);

        // Test unregister
        unRegisterTelephonyCallback(mOnCallStateChangedCalled, mCallStateCallback);
    }

    private DataConnectionStateListener mDataConnectionStateCallback;

    private class DataConnectionStateListener extends TelephonyCallback
            implements TelephonyCallback.DataConnectionStateListener {
        @Override
        public void onDataConnectionStateChanged(int state, int networkType) {
            synchronized (mLock) {
                mOnDataConnectionStateChangedCalled = true;
                mOnDataConnectionStateChangedWithNetworkTypeCalled = true;
                if (mOnDataConnectionStateChangedCalled
                        && mOnDataConnectionStateChangedWithNetworkTypeCalled) {
                    mLock.notify();
                }
            }
        }
    }

    @Test
    public void testOnDataConnectionStateChangedByRegisterTelephonyCallback() throws Throwable {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_DATA));
        }

        assertFalse(mOnDataConnectionStateChangedCalled);
        assertFalse(mOnDataConnectionStateChangedWithNetworkTypeCalled);

        mDataConnectionStateCallback = new DataConnectionStateListener();
        registerTelephonyCallback(mDataConnectionStateCallback);

        synchronized (mLock) {
            if (!mOnDataConnectionStateChangedCalled ||
                    !mOnDataConnectionStateChangedWithNetworkTypeCalled) {
                mLock.wait(WAIT_TIME);
            }
        }

        assertTrue(mOnDataConnectionStateChangedCalled);
        assertTrue(mOnDataConnectionStateChangedWithNetworkTypeCalled);

        // Test unregister
        unRegisterTelephonyCallback(mOnDataConnectionStateChangedCalled,
                mDataConnectionStateCallback);
    }

    private DataActivityListener mDataActivityCallback;

    private class DataActivityListener extends TelephonyCallback
            implements TelephonyCallback.DataActivityListener {
        @Override
        public void onDataActivity(int direction) {
            synchronized (mLock) {
                mOnDataActivityCalled = true;
                mLock.notify();
            }
        }
    }

    @Test
    public void testOnDataActivityByRegisterTelephonyCallback() throws Throwable {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_DATA));
        }

        assertFalse(mOnDataActivityCalled);

        mDataActivityCallback = new DataActivityListener();
        registerTelephonyCallback(mDataActivityCallback);

        synchronized (mLock) {
            if (!mOnDataActivityCalled) {
                mLock.wait(WAIT_TIME);
            }
        }

        assertTrue(mOnDataActivityCalled);

        // Test unregister
        unRegisterTelephonyCallback(mOnDataActivityCalled, mDataActivityCallback);
    }

    private CellInfoListener mCellInfoCallback;

    private class CellInfoListener extends TelephonyCallback
            implements TelephonyCallback.CellInfoListener {
        @Override
        public void onCellInfoChanged(List<CellInfo> cellInfo) {
            synchronized (mLock) {
                mOnCellInfoChangedCalled = true;
                mLock.notify();
            }
        }
    }

    @Test
    public void testOnCellInfoChangedByRegisterTelephonyCallback() throws Throwable {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(mPackageManager.hasSystemFeature(
                    PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));
        }

        assertFalse(mOnDataActivityCalled);

        TelephonyManagerTest.grantLocationPermissions();

        mCellInfoCallback = new CellInfoListener();
        registerTelephonyCallback(mCellInfoCallback);

        synchronized (mLock) {
            if (!mOnCellInfoChangedCalled) {
                mLock.wait(WAIT_TIME);
            }
        }

        assertTrue(mOnCellInfoChangedCalled);

        // Test unregister
        unRegisterTelephonyCallback(mOnCellInfoChangedCalled, mCellInfoCallback);
    }

    private UserMobileDataStateListener mUserMobileDataStateCallback;

    private class UserMobileDataStateListener extends TelephonyCallback
            implements TelephonyCallback.UserMobileDataStateListener {
        @Override
        public void onUserMobileDataStateChanged(boolean state) {
            synchronized (mLock) {
                mOnUserMobileDataStateChanged = true;
                mLock.notify();
            }
        }
    }

    @Test
    public void testOnUserMobileDataStateChangedByRegisterTelephonyCallback() throws Throwable {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_DATA));
        }

        assertFalse(mOnUserMobileDataStateChanged);

        mUserMobileDataStateCallback = new UserMobileDataStateListener();
        registerTelephonyCallback(mUserMobileDataStateCallback);

        synchronized (mLock) {
            if (!mOnUserMobileDataStateChanged) {
                mLock.wait(WAIT_TIME);
            }
        }

        assertTrue(mOnUserMobileDataStateChanged);

        // Test unregister
        unRegisterTelephonyCallback(mOnUserMobileDataStateChanged, mUserMobileDataStateCallback);
    }

    private OutgoingEmergencySmsListener mOutgoingEmergencySmsCallback;

    private class OutgoingEmergencySmsListener extends TelephonyCallback
            implements TelephonyCallback.OutgoingEmergencySmsListener {
        @Override
        public void onOutgoingEmergencySms(EmergencyNumber emergencyNumber, int subId) {
            synchronized (mLock) {
                Log.i(TAG, "onOutgoingEmergencySms: emergencyNumber=" + emergencyNumber);
                mOnOutgoingSmsEmergencyNumberChanged = emergencyNumber;
                mLock.notify();
            }
        }
    }

    @Test
    public void testOnOutgoingSmsEmergencyNumberChangedByRegisterTelephonyCallback()
            throws Throwable {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(mPackageManager.hasSystemFeature(
                    PackageManager.FEATURE_TELEPHONY_MESSAGING));
        }

        TelephonyUtils.addTestEmergencyNumber(
                InstrumentationRegistry.getInstrumentation(), TEST_EMERGENCY_NUMBER);
        assertNull(mOnOutgoingSmsEmergencyNumberChanged);

        mOutgoingEmergencySmsCallback = new OutgoingEmergencySmsListener();
        registerTelephonyCallbackWithPermission(mOutgoingEmergencySmsCallback);
        SmsManager smsManager = SmsManager.getDefault();
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(smsManager,
                (sm) -> sm.sendTextMessage(
                        TEST_EMERGENCY_NUMBER, null,
                        "testOutgoingSmsListenerCtsByRegisterTelephonyCallback",
                        null, null));
        try {
            synchronized (mLock) {
                if (mOnOutgoingSmsEmergencyNumberChanged == null) {
                    mLock.wait(WAIT_TIME);
                }
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Operation interrupted.");
        } finally {
            TelephonyUtils.removeTestEmergencyNumber(
                    InstrumentationRegistry.getInstrumentation(), TEST_EMERGENCY_NUMBER);
        }

        assertNotNull(mOnOutgoingSmsEmergencyNumberChanged);
        assertEquals(mOnOutgoingSmsEmergencyNumberChanged.getNumber(), TEST_EMERGENCY_NUMBER);

        // Test unregister
        unRegisterTelephonyCallback(mOnOutgoingSmsEmergencyNumberChanged == null,
                mOutgoingEmergencySmsCallback);

        // Disable suppressing blocking.
        TelephonyUtils.endBlockSuppression(InstrumentationRegistry.getInstrumentation());
    }

    private ActiveDataSubscriptionIdListener mActiveDataSubscriptionIdCallback;

    private class ActiveDataSubscriptionIdListener extends TelephonyCallback
            implements TelephonyCallback.ActiveDataSubscriptionIdListener {
        @Override
        public void onActiveDataSubscriptionIdChanged(int subId) {
            synchronized (mLock) {
                mOnActiveDataSubscriptionIdChanged = true;
                mLock.notify();
            }
        }
    }

    @Test
    public void testOnActiveDataSubscriptionIdChangedByRegisterTelephonyCallback()
            throws Throwable {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(mPackageManager.hasSystemFeature(
                    PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));
        }

        assertFalse(mOnActiveDataSubscriptionIdChanged);

        mActiveDataSubscriptionIdCallback =
                new ActiveDataSubscriptionIdListener();
        registerTelephonyCallback(mActiveDataSubscriptionIdCallback);

        synchronized (mLock) {
            if (!mOnActiveDataSubscriptionIdChanged) {
                mLock.wait(WAIT_TIME);
            }
        }

        assertTrue(mOnActiveDataSubscriptionIdChanged);

        // Test unregister
        unRegisterTelephonyCallback(mOnActiveDataSubscriptionIdChanged,
                mActiveDataSubscriptionIdCallback);
    }

    private BarringInfoListener mBarringInfoCallback;

    private class BarringInfoListener extends TelephonyCallback
            implements TelephonyCallback.BarringInfoListener {
        @Override
        public void onBarringInfoChanged(BarringInfo barringInfo) {
            synchronized (mLock) {
                mOnBarringInfoChangedCalled = true;
                mBarringInfo = barringInfo;
                mLock.notify();
            }
        }
    }

    @Test
    public void testOnBarringInfoChangedByRegisterTelephonyCallback() throws Throwable {
        assertFalse(mOnBarringInfoChangedCalled);

        mBarringInfoCallback = new BarringInfoListener();
        registerTelephonyCallbackWithPermission(mBarringInfoCallback);

        synchronized (mLock) {
            if (!mOnBarringInfoChangedCalled) {
                mLock.wait(WAIT_TIME);
            }
        }
        assertTrue(mOnBarringInfoChangedCalled);

        assertBarringInfoSane(mBarringInfo);

        // Test unregister
        unRegisterTelephonyCallback(mOnBarringInfoChangedCalled, mBarringInfoCallback);
    }

    private static final int[] sBarringServiceInfoTypes = new int[]{
            BarringInfo.BARRING_SERVICE_TYPE_CS_SERVICE,
            BarringInfo.BARRING_SERVICE_TYPE_PS_SERVICE,
            BarringInfo.BARRING_SERVICE_TYPE_CS_VOICE,
            BarringInfo.BARRING_SERVICE_TYPE_MO_SIGNALLING,
            BarringInfo.BARRING_SERVICE_TYPE_MO_DATA,
            BarringInfo.BARRING_SERVICE_TYPE_CS_FALLBACK,
            BarringInfo.BARRING_SERVICE_TYPE_MMTEL_VOICE,
            BarringInfo.BARRING_SERVICE_TYPE_MMTEL_VIDEO,
            BarringInfo.BARRING_SERVICE_TYPE_EMERGENCY,
            BarringInfo.BARRING_SERVICE_TYPE_SMS
    };

    private static void assertBarringInfoSane(BarringInfo barringInfo) {
        assertNotNull(barringInfo);

        // Flags to track whether we have had unknown and known barring types reported
        boolean hasBarringTypeUnknown = false;
        boolean hasBarringTypeKnown = false;

        for (int bsiType : sBarringServiceInfoTypes) {
            BarringInfo.BarringServiceInfo bsi = barringInfo.getBarringServiceInfo(bsiType);
            assertNotNull(bsi);
            switch (bsi.getBarringType()) {
                case BarringInfo.BarringServiceInfo.BARRING_TYPE_UNKNOWN:
                    hasBarringTypeUnknown = true;
                    assertFalse(bsi.isConditionallyBarred());
                    assertEquals(0, bsi.getConditionalBarringFactor());
                    assertEquals(0, bsi.getConditionalBarringTimeSeconds());
                    assertFalse(bsi.isBarred());
                    break;

                case BarringInfo.BarringServiceInfo.BARRING_TYPE_NONE:
                    hasBarringTypeKnown = true;
                    // Unless conditional barring is active, all conditional barring fields
                    // should be "unset".
                    assertFalse(bsi.isConditionallyBarred());
                    assertEquals(0, bsi.getConditionalBarringFactor());
                    assertEquals(0, bsi.getConditionalBarringTimeSeconds());
                    assertFalse(bsi.isBarred());
                    break;

                case BarringInfo.BarringServiceInfo.BARRING_TYPE_UNCONDITIONAL:
                    hasBarringTypeKnown = true;
                    // Unless conditional barring is active, all conditional barring fields
                    // should be "unset".
                    assertFalse(bsi.isConditionallyBarred());
                    assertEquals(0, bsi.getConditionalBarringFactor());
                    assertEquals(0, bsi.getConditionalBarringTimeSeconds());
                    assertTrue(bsi.isBarred());
                    break;

                case BarringInfo.BarringServiceInfo.BARRING_TYPE_CONDITIONAL:
                    hasBarringTypeKnown = true;
                    // If conditional barring is active, then the barring time and factor must
                    // be known (set), but the device may or may not be barred at the moment,
                    // so isConditionallyBarred() can be either true or false (hence not checked).
                    assertNotEquals(0, bsi.getConditionalBarringFactor());
                    assertNotEquals(0, bsi.getConditionalBarringTimeSeconds());
                    assertEquals(bsi.isBarred(), bsi.isConditionallyBarred());
                    break;
            }
        }
        // If any barring type is unknown, then barring is not supported so all must be
        // unknown. If any type is known, then all that are not reported are assumed to
        // be not barred.
        assertNotEquals(hasBarringTypeUnknown, hasBarringTypeKnown);
    }

    private RegistrationFailedListener mRegistrationFailedCallback;

    private class RegistrationFailedListener extends TelephonyCallback
            implements TelephonyCallback.RegistrationFailedListener {
        @Override
        public void onRegistrationFailed(CellIdentity cid, String chosenPlmn,
                                         int domain, int causeCode, int additionalCauseCode) {
            synchronized (mLock) {
                mOnRegistrationFailedCalled = true;
                mLock.notify();
            }
        }
    }

    @Test
    public void testOnRegistrationFailedByRegisterTelephonyCallback() throws Throwable {

        assertFalse(mOnBarringInfoChangedCalled);

        mRegistrationFailedCallback = new RegistrationFailedListener();
        registerTelephonyCallbackWithPermission(mRegistrationFailedCallback);

        synchronized (mLock) {
            if (!mOnBarringInfoChangedCalled) {
                mLock.wait(WAIT_TIME);
            }
        }

        // Assert that in the WAIT_TIME interval, the listener wasn't invoked. While this is
        // **technically** a flaky test, in practice this flake should happen approximately never
        // as it would mean that a registered phone is failing to reselect during CTS at this
        // exact moment.
        //
        // What the test is verifying is that there is no "auto" callback for registration
        // failure because unlike other PSL registrants, this one is not called upon registration.
        assertFalse(mOnRegistrationFailedCalled);

        // Test unregister
        unRegisterTelephonyCallback(mOnRegistrationFailedCalled, mRegistrationFailedCallback);
    }

    private PhysicalChannelConfigListener mPhysicalChannelConfigCallback;

    private class PhysicalChannelConfigListener extends TelephonyCallback
            implements TelephonyCallback.PhysicalChannelConfigListener {
        @Override
        public void onPhysicalChannelConfigChanged(@NonNull List<PhysicalChannelConfig> configs) {
            synchronized (mLock) {
                mOnPhysicalChannelConfigCalled = true;
                mLock.notify();
            }
        }
    }

    @Test
    public void testOnPhysicalChannelConfigChanged() throws Throwable {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(mPackageManager.hasSystemFeature(
                    PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));
        }

        Pair<Integer, Integer> networkHalVersion =
                mTelephonyManager.getHalVersion(TelephonyManager.HAL_SERVICE_NETWORK);
        // 1.2+ or 1.6 with CAPABILITY_PHYSICAL_CHANNEL_CONFIG_1_6_SUPPORTED or 2.0+
        boolean physicalChannelConfigSupported;
        if (networkHalVersion.first == 1 && networkHalVersion.second == 6) {
            physicalChannelConfigSupported = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.isRadioInterfaceCapabilitySupported(
                            TelephonyManager.CAPABILITY_PHYSICAL_CHANNEL_CONFIG_1_6_SUPPORTED));
        } else {
            physicalChannelConfigSupported =
                    networkHalVersion.first > 1 || networkHalVersion.second >= 2;
        }
        if (!physicalChannelConfigSupported) {
            Log.d(TAG, "Skipping test because physical channel configs are not available.");
            return;
        }

        assertFalse(mOnPhysicalChannelConfigCalled);

        mPhysicalChannelConfigCallback = new PhysicalChannelConfigListener();
        registerTelephonyCallbackWithPermission(mPhysicalChannelConfigCallback);

        synchronized (mLock) {
            while (!mOnPhysicalChannelConfigCalled) {
                mLock.wait(WAIT_TIME);
            }
        }
        assertTrue(mOnPhysicalChannelConfigCalled);

        // Test unregister
        unRegisterTelephonyCallback(mOnPhysicalChannelConfigCalled,
                mPhysicalChannelConfigCallback);
    }

    private DataEnabledListener mDataEnabledCallback;

    private class DataEnabledListener extends TelephonyCallback
            implements TelephonyCallback.DataEnabledListener {
        @Override
        public void onDataEnabledChanged(boolean enabled, @DataEnabledReason int reason) {
            synchronized (mLock) {
                mOnDataEnabledChangedCalled = true;
                mLock.notify();
            }
        }
    }

    @Test
    public void testOnDataEnabledChangedByRegisterTelephonyCallback() throws Throwable {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_DATA));
        }

        assertFalse(mOnDataEnabledChangedCalled);

        mDataEnabledCallback = new DataEnabledListener();
        registerTelephonyCallbackWithPermission(mDataEnabledCallback);

        synchronized (mLock) {
            while (!mOnDataEnabledChangedCalled) {
                mLock.wait(WAIT_TIME);
            }
        }
        assertTrue(mOnDataEnabledChangedCalled);

        // Test unregister
        unRegisterTelephonyCallback(mOnDataEnabledChangedCalled, mDataEnabledCallback);
    }

    private AllowedNetworkTypesListener mAllowedNetworkTypesCallback;

    private class AllowedNetworkTypesListener extends TelephonyCallback
            implements TelephonyCallback.AllowedNetworkTypesListener {
        @Override
        public void onAllowedNetworkTypesChanged(int reason, long allowedNetworkType) {
            synchronized (mLock) {
                Log.d(TAG, "onAllowedNetworkTypesChanged");
                mAllowedNetworkTypeReason = reason;
                mAllowedNetworkTypeValue = allowedNetworkType;
                mOnAllowedNetworkTypesChangedCalled = true;

                mLock.notify();
            }
        }
    }

    @Test
    public void testOnAllowedNetworkTypesChangedByRegisterPhoneStateListener() throws Throwable {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(mPackageManager.hasSystemFeature(
                    PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));
        }

        long originalAllowedNetworkTypeUser = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> {
                    return tm.getAllowedNetworkTypesForReason(
                            TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER);
                });
        assertFalse(mOnAllowedNetworkTypesChangedCalled);

        long supportedNetworkTypes =
                ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> {
                        return tm.getSupportedRadioAccessFamily();
                    });

        mAllowedNetworkTypesCallback = new AllowedNetworkTypesListener();
        registerTelephonyCallbackWithPermission(mAllowedNetworkTypesCallback);
        long networkTypesToBeTested =
                (supportedNetworkTypes & TelephonyManager.NETWORK_TYPE_BITMASK_NR) == 0
                        ? TelephonyManager.NETWORK_TYPE_BITMASK_LTE
                        : TelephonyManager.NETWORK_TYPE_BITMASK_NR;
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mTelephonyManager,
                (tm) -> tm.setAllowedNetworkTypesForReason(
                        TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER,
                        networkTypesToBeTested));

        synchronized (mLock) {
            if (!mOnAllowedNetworkTypesChangedCalled) {
                mLock.wait(WAIT_TIME);
            }
        }

        long allowedNetworkTypeUser = ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, (tm) -> {
                    return tm.getAllowedNetworkTypesForReason(
                            TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER);
                });

        assertEquals(TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER, mAllowedNetworkTypeReason);
        assertEquals(allowedNetworkTypeUser, mAllowedNetworkTypeValue);
        // Test unregister
        unRegisterTelephonyCallback(mOnAllowedNetworkTypesChangedCalled,
                mAllowedNetworkTypesCallback);

        // Recover the allowed network type user settings.
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mTelephonyManager,
                (tm) -> tm.setAllowedNetworkTypesForReason(
                        TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER,
                        originalAllowedNetworkTypeUser));
    }

    private LinkCapacityEstimateChangedListener mLinkCapacityEstimateChangedListener;

    private class LinkCapacityEstimateChangedListener extends TelephonyCallback
            implements TelephonyCallback.LinkCapacityEstimateChangedListener {
        @Override
        public void onLinkCapacityEstimateChanged(
                List<LinkCapacityEstimate> linkCapacityEstimateList) {
            synchronized (mLock) {
                int lceType = linkCapacityEstimateList.get(0).getType();
                if (lceType == LinkCapacityEstimate.LCE_TYPE_COMBINED
                        || lceType == LinkCapacityEstimate.LCE_TYPE_PRIMARY
                        || lceType == LinkCapacityEstimate.LCE_TYPE_SECONDARY) {
                    mOnLinkCapacityEstimateChangedCalled = true;
                }
                mLock.notify();
            }
        }
    }

    @Test
    public void testOnLinkCapacityEstimateChangedByRegisterPhoneStateListener() throws Throwable {

        assertFalse(mOnLinkCapacityEstimateChangedCalled);

        mLinkCapacityEstimateChangedListener = new LinkCapacityEstimateChangedListener();
        registerTelephonyCallbackWithPermission(mLinkCapacityEstimateChangedListener);

        synchronized (mLock) {
            while (!mOnLinkCapacityEstimateChangedCalled) {
                mLock.wait(WAIT_TIME);
            }
        }
        assertTrue(mOnLinkCapacityEstimateChangedCalled);

        // Test unregister
        unRegisterTelephonyCallback(mOnLinkCapacityEstimateChangedCalled,
                mLinkCapacityEstimateChangedListener);
    }


    private EmergencyCallbackModeListener mEmergencyCallbackModeListener;

    private class EmergencyCallbackModeListener extends TelephonyCallback
            implements TelephonyCallback.EmergencyCallbackModeListener {
        @Override
        public void onCallBackModeStarted(@EmergencyCallbackModeType int type) {

        }
        @Override
        public void onCallBackModeStopped(@EmergencyCallbackModeType int type,
                @EmergencyCallbackModeStopReason int reason) {
            synchronized (mLock) {
                mOnEmergencyCallbackModeChangedCalled = true;
                mLock.notify();
            }
        }
    }

    @Test
    public void testOnEmergencyCallbackModeListener() throws Throwable {
        if (Flags.enforceTelephonyFeatureMappingForPublicApis()) {
            assumeTrue(mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_CALLING));
        }

        if (mCm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE) == null) {
            Log.d(TAG, "Skipping test that requires ConnectivityManager.TYPE_MOBILE");
            return;
        }

        assertFalse(mOnEmergencyCallbackModeChangedCalled);
        mEmergencyCallbackModeListener = new EmergencyCallbackModeListener();
        registerTelephonyCallbackWithPermission(mEmergencyCallbackModeListener);

        synchronized (mLock) {
            while (!mOnEmergencyCallbackModeChangedCalled) {
                mLock.wait(WAIT_TIME);
            }
        }
        assertTrue(mOnEmergencyCallbackModeChangedCalled);

        // Test unregister
        unRegisterTelephonyCallback(mOnEmergencyCallbackModeChangedCalled,
                mEmergencyCallbackModeListener);
    }
}
