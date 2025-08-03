/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.server.telecom.tests;

import static android.provider.CallLog.Calls.USER_MISSED_NOT_RUNNING;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.TestCase.fail;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyChar;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static java.lang.Thread.sleep;

import android.Manifest;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.OutcomeReceiver;
import android.os.Process;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.BlockedNumberContract;
import android.telecom.CallException;
import android.telecom.CallScreeningService;
import android.telecom.CallerInfo;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.GatewayInfo;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.telephony.CarrierConfigManager;
import android.telephony.PhoneCapability;
import android.telephony.TelephonyManager;
import android.util.ArraySet;
import android.util.Pair;
import android.widget.Toast;

import androidx.test.filters.MediumTest;
import androidx.test.filters.SmallTest;

import com.android.internal.telecom.IConnectionService;
import com.android.server.telecom.AnomalyReporterAdapter;
import com.android.server.telecom.AsyncRingtonePlayer;
import com.android.server.telecom.Call;
import com.android.server.telecom.CallAnomalyWatchdog;
import com.android.server.telecom.CallAudioCommunicationDeviceTracker;
import com.android.server.telecom.CallAudioManager;
import com.android.server.telecom.CallAudioModeStateMachine;
import com.android.server.telecom.CallAudioRouteStateMachine;
import com.android.server.telecom.CallDiagnosticServiceController;
import com.android.server.telecom.CallEndpointController;
import com.android.server.telecom.CallEndpointControllerFactory;
import com.android.server.telecom.CallState;
import com.android.server.telecom.CallerInfoLookupHelper;
import com.android.server.telecom.CallsManager;
import com.android.server.telecom.ClockProxy;
import com.android.server.telecom.ConnectionServiceFocusManager;
import com.android.server.telecom.ConnectionServiceFocusManager.ConnectionServiceFocusManagerFactory;
import com.android.server.telecom.ConnectionServiceWrapper;
import com.android.server.telecom.CreateConnectionResponse;
import com.android.server.telecom.DefaultDialerCache;
import com.android.server.telecom.EmergencyCallDiagnosticLogger;
import com.android.server.telecom.EmergencyCallHelper;
import com.android.server.telecom.HandoverState;
import com.android.server.telecom.HeadsetMediaButton;
import com.android.server.telecom.HeadsetMediaButtonFactory;
import com.android.server.telecom.InCallController;
import com.android.server.telecom.InCallControllerFactory;
import com.android.server.telecom.InCallTonePlayer;
import com.android.server.telecom.InCallWakeLockController;
import com.android.server.telecom.InCallWakeLockControllerFactory;
import com.android.server.telecom.MissedCallNotifier;
import com.android.server.telecom.PhoneAccountRegistrar;
import com.android.server.telecom.PhoneNumberUtilsAdapter;
import com.android.server.telecom.ProximitySensorManager;
import com.android.server.telecom.ProximitySensorManagerFactory;
import com.android.server.telecom.Ringer;
import com.android.server.telecom.RoleManagerAdapter;
import com.android.server.telecom.SystemStateHelper;
import com.android.server.telecom.TelecomSystem;
import com.android.server.telecom.Timeouts;
import com.android.server.telecom.WiredHeadsetManager;
import com.android.server.telecom.bluetooth.BluetoothDeviceManager;
import com.android.server.telecom.bluetooth.BluetoothRouteManager;
import com.android.server.telecom.bluetooth.BluetoothStateReceiver;
import com.android.server.telecom.callfiltering.BlockedNumbersAdapter;
import com.android.server.telecom.callfiltering.CallFilteringResult;
import com.android.server.telecom.flags.FeatureFlags;
import com.android.server.telecom.callfiltering.IncomingCallFilterGraph;
import com.android.server.telecom.ui.AudioProcessingNotification;
import com.android.server.telecom.ui.CallStreamingNotification;
import com.android.server.telecom.ui.DisconnectedCallNotifier;
import com.android.server.telecom.ui.ToastFactory;
import com.android.server.telecom.voip.TransactionManager;

import com.google.common.base.Objects;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RunWith(JUnit4.class)
public class CallsManagerTest extends TelecomTestCase {
    private static final int TEST_TIMEOUT = 5000;  // milliseconds
    private static final long STATE_TIMEOUT = 5000L;
    private static final int SECONDARY_USER_ID = 12;
    private static final UserHandle TEST_USER_HANDLE = UserHandle.of(123);
    private static final String TEST_PACKAGE_NAME = "GoogleMeet";
    private static final PhoneAccountHandle SIM_1_HANDLE = new PhoneAccountHandle(
            ComponentName.unflattenFromString("com.foo/.Blah"), "Sim1");
    private static final PhoneAccountHandle SIM_1_HANDLE_SECONDARY = new PhoneAccountHandle(
            ComponentName.unflattenFromString("com.foo/.Blah"), "Sim1",
            new UserHandle(SECONDARY_USER_ID));
    private static final PhoneAccountHandle SIM_2_HANDLE = new PhoneAccountHandle(
            ComponentName.unflattenFromString("com.foo/.Blah"), "Sim2");
    private static final PhoneAccountHandle SIM_3_HANDLE = new PhoneAccountHandle(
            ComponentName.unflattenFromString("com.foo/.Blah"), "Sim3");
    private static final PhoneAccountHandle CALL_PROVIDER_HANDLE = new PhoneAccountHandle(
            ComponentName.unflattenFromString("com.sip.foo/.Blah"), "sip1");
    private static final PhoneAccountHandle CONNECTION_MGR_1_HANDLE = new PhoneAccountHandle(
            ComponentName.unflattenFromString("com.bar/.Conn"), "Cm1");
    private static final PhoneAccountHandle CONNECTION_MGR_2_HANDLE = new PhoneAccountHandle(
            ComponentName.unflattenFromString("com.spa/.Conn"), "Cm2");
    private static final PhoneAccountHandle VOIP_1_HANDLE = new PhoneAccountHandle(
            ComponentName.unflattenFromString("com.voip/.Stuff"), "Voip1");
    private static final PhoneAccountHandle SELF_MANAGED_HANDLE = new PhoneAccountHandle(
            ComponentName.unflattenFromString("com.baz/.Self"), "Self");
    private static final PhoneAccountHandle SELF_MANAGED_2_HANDLE = new PhoneAccountHandle(
            ComponentName.unflattenFromString("com.baz/.Self2"), "Self2");
    private static final PhoneAccountHandle WORK_HANDLE = new PhoneAccountHandle(
            ComponentName.unflattenFromString("com.foo/.Blah"), "work", new UserHandle(10));
    private static final PhoneAccountHandle SELF_MANAGED_W_CUSTOM_HANDLE = new PhoneAccountHandle(
            new ComponentName(TEST_PACKAGE_NAME, "class"), "1", TEST_USER_HANDLE);
    private static final PhoneAccount SIM_1_ACCOUNT = new PhoneAccount.Builder(SIM_1_HANDLE, "Sim1")
            .setCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION
                    | PhoneAccount.CAPABILITY_CALL_PROVIDER
                    | PhoneAccount.CAPABILITY_PLACE_EMERGENCY_CALLS)
            .setIsEnabled(true)
            .build();
    private static final PhoneAccount SIM_1_ACCOUNT_SECONDARY = new PhoneAccount
            .Builder(SIM_1_HANDLE_SECONDARY, "Sim1")
            .setCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION
                    | PhoneAccount.CAPABILITY_CALL_PROVIDER
                    | PhoneAccount.CAPABILITY_PLACE_EMERGENCY_CALLS)
            .setIsEnabled(true)
            .build();
    private static final PhoneAccount SIM_2_ACCOUNT = new PhoneAccount.Builder(SIM_2_HANDLE, "Sim2")
            .setCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION
                    | PhoneAccount.CAPABILITY_CALL_PROVIDER
                    | PhoneAccount.CAPABILITY_SUPPORTS_VIDEO_CALLING)
            .setIsEnabled(true)
            .build();
    private static final PhoneAccount SIM_3_ACCOUNT = new PhoneAccount.Builder(SIM_3_HANDLE, "Sim3")
            .setCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION
                    | PhoneAccount.CAPABILITY_CALL_PROVIDER
                    | PhoneAccount.CAPABILITY_SUPPORTS_VIDEO_CALLING)
            .setIsEnabled(true)
            .build();
    private static final PhoneAccount CALL_PROVIDER_ACCOUNT =
            new PhoneAccount.Builder(CALL_PROVIDER_HANDLE, "sip1")
            .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER
                    | PhoneAccount.CAPABILITY_SUPPORTS_VIDEO_CALLING)
            .setIsEnabled(true)
            .build();
    private static final PhoneAccount SELF_MANAGED_ACCOUNT = new PhoneAccount.Builder(
            SELF_MANAGED_HANDLE, "Self")
            .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
            .setIsEnabled(true)
            .build();
    private static final PhoneAccount SELF_MANAGED_2_ACCOUNT = new PhoneAccount.Builder(
            SELF_MANAGED_2_HANDLE, "Self2")
            .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
            .setIsEnabled(true)
            .build();
    private static final PhoneAccount WORK_ACCOUNT = new PhoneAccount.Builder(
            WORK_HANDLE, "work")
            .setCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION
                    | PhoneAccount.CAPABILITY_CALL_PROVIDER
                    | PhoneAccount.CAPABILITY_PLACE_EMERGENCY_CALLS)
            .setIsEnabled(true)
            .build();
    private static final PhoneAccount SM_W_DIFFERENT_PACKAGE_AND_USER = new PhoneAccount.Builder(
            SELF_MANAGED_W_CUSTOM_HANDLE, "Self")
            .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
            .setIsEnabled(true)
            .build();

    private static final Uri TEST_ADDRESS = Uri.parse("tel:555-1212");
    private static final Uri TEST_ADDRESS2 = Uri.parse("tel:555-1213");
    private static final Uri TEST_ADDRESS3 = Uri.parse("tel:555-1214");
    private static final Map<Uri, PhoneAccountHandle> CONTACT_PREFERRED_ACCOUNT = Map.of(
            TEST_ADDRESS2, SIM_1_HANDLE,
            TEST_ADDRESS3, SIM_2_HANDLE);

    private static final String DEFAULT_CALL_SCREENING_APP = "com.foo.call_screen_app";

    private static int sCallId = 1;
    private final TelecomSystem.SyncRoot mLock = new TelecomSystem.SyncRoot() { };
    @Mock private CallerInfoLookupHelper mCallerInfoLookupHelper;
    @Mock private MissedCallNotifier mMissedCallNotifier;
    @Mock private DisconnectedCallNotifier.Factory mDisconnectedCallNotifierFactory;
    @Mock private DisconnectedCallNotifier mDisconnectedCallNotifier;
    @Mock private PhoneAccountRegistrar mPhoneAccountRegistrar;
    @Mock private HeadsetMediaButton mHeadsetMediaButton;
    @Mock private HeadsetMediaButtonFactory mHeadsetMediaButtonFactory;
    @Mock private ProximitySensorManager mProximitySensorManager;
    @Mock private ProximitySensorManagerFactory mProximitySensorManagerFactory;
    @Mock private InCallWakeLockController mInCallWakeLockController;
    @Mock private ConnectionServiceFocusManagerFactory mConnSvrFocusManagerFactory;
    @Mock private InCallWakeLockControllerFactory mInCallWakeLockControllerFactory;
    @Mock private CallAudioManager.AudioServiceFactory mAudioServiceFactory;
    @Mock private BluetoothRouteManager mBluetoothRouteManager;
    @Mock private WiredHeadsetManager mWiredHeadsetManager;
    @Mock private SystemStateHelper mSystemStateHelper;
    @Mock private DefaultDialerCache mDefaultDialerCache;
    @Mock private Timeouts.Adapter mTimeoutsAdapter;
    @Mock private AsyncRingtonePlayer mAsyncRingtonePlayer;
    @Mock private PhoneNumberUtilsAdapter mPhoneNumberUtilsAdapter;
    @Mock private EmergencyCallHelper mEmergencyCallHelper;
    @Mock private InCallTonePlayer.ToneGeneratorFactory mToneGeneratorFactory;
    @Mock private ClockProxy mClockProxy;
    @Mock private AudioProcessingNotification mAudioProcessingNotification;
    @Mock private InCallControllerFactory mInCallControllerFactory;
    @Mock private InCallController mInCallController;
    @Mock private CallEndpointControllerFactory mCallEndpointControllerFactory;
    @Mock private CallEndpointController mCallEndpointController;
    @Mock private ConnectionServiceFocusManager mConnectionSvrFocusMgr;
    @Mock private CallAudioRouteStateMachine mCallAudioRouteStateMachine;
    @Mock private CallAudioRouteStateMachine.Factory mCallAudioRouteStateMachineFactory;
    @Mock private CallAudioModeStateMachine mCallAudioModeStateMachine;
    @Mock private CallAudioModeStateMachine.Factory mCallAudioModeStateMachineFactory;
    @Mock private CallDiagnosticServiceController mCallDiagnosticServiceController;
    @Mock private BluetoothStateReceiver mBluetoothStateReceiver;
    @Mock private RoleManagerAdapter mRoleManagerAdapter;
    @Mock private ToastFactory mToastFactory;
    @Mock private Toast mToast;
    @Mock private CallAnomalyWatchdog mCallAnomalyWatchdog;

    @Mock private EmergencyCallDiagnosticLogger mEmergencyCallDiagnosticLogger;
    @Mock private AnomalyReporterAdapter mAnomalyReporterAdapter;
    @Mock private Ringer.AccessibilityManagerAdapter mAccessibilityManagerAdapter;
    @Mock private BlockedNumbersAdapter mBlockedNumbersAdapter;
    @Mock private PhoneCapability mPhoneCapability;
    @Mock private CallAudioCommunicationDeviceTracker mCommunicationDeviceTracker;
    @Mock private CallStreamingNotification mCallStreamingNotification;
    @Mock private BluetoothDeviceManager mBluetoothDeviceManager;
    @Mock private FeatureFlags mFeatureFlags;
    @Mock private com.android.internal.telephony.flags.FeatureFlags mTelephonyFlags;
    @Mock private IncomingCallFilterGraph mIncomingCallFilterGraph;
    @Mock private Context mMockCreateContextAsUser;
    @Mock private UserManager mMockCurrentUserManager;
    @Mock private IConnectionService mIConnectionService;
    private CallsManager mCallsManager;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
        when(mInCallWakeLockControllerFactory.create(any(), any())).thenReturn(
                mInCallWakeLockController);
        when(mHeadsetMediaButtonFactory.create(any(), any(), any())).thenReturn(
                mHeadsetMediaButton);
        when(mProximitySensorManagerFactory.create(any(), any())).thenReturn(
                mProximitySensorManager);
        when(mInCallControllerFactory.create(any(), any(), any(), any(), any(), any(),
                any())).thenReturn(mInCallController);
        when(mCallEndpointControllerFactory.create(any(), any(), any())).thenReturn(
                mCallEndpointController);
        when(mCallAudioRouteStateMachineFactory.create(any(), any(), any(), any(), any(), any(),
                anyInt(), any(), any(), any())).thenReturn(mCallAudioRouteStateMachine);
        when(mCallAudioModeStateMachineFactory.create(any(), any(), any(), any()))
                .thenReturn(mCallAudioModeStateMachine);
        when(mClockProxy.currentTimeMillis()).thenReturn(System.currentTimeMillis());
        when(mClockProxy.elapsedRealtime()).thenReturn(SystemClock.elapsedRealtime());
        when(mConnSvrFocusManagerFactory.create(any())).thenReturn(mConnectionSvrFocusMgr);
        doNothing().when(mRoleManagerAdapter).setCurrentUserHandle(any());
        when(mDisconnectedCallNotifierFactory.create(any(Context.class),any(CallsManager.class)))
                .thenReturn(mDisconnectedCallNotifier);
        when(mTimeoutsAdapter.getCallDiagnosticServiceTimeoutMillis(any(ContentResolver.class)))
                .thenReturn(2000L);
        when(mTimeoutsAdapter.getNonVoipCallTransitoryStateTimeoutMillis())
                .thenReturn(STATE_TIMEOUT);
        when(mClockProxy.elapsedRealtime()).thenReturn(0L);
        mCallsManager = new CallsManager(
                mComponentContextFixture.getTestDouble().getApplicationContext(),
                mLock,
                mCallerInfoLookupHelper,
                mMissedCallNotifier,
                mDisconnectedCallNotifierFactory,
                mPhoneAccountRegistrar,
                mHeadsetMediaButtonFactory,
                mProximitySensorManagerFactory,
                mInCallWakeLockControllerFactory,
                mConnSvrFocusManagerFactory,
                mAudioServiceFactory,
                mBluetoothRouteManager,
                mWiredHeadsetManager,
                mSystemStateHelper,
                mDefaultDialerCache,
                mTimeoutsAdapter,
                mAsyncRingtonePlayer,
                mPhoneNumberUtilsAdapter,
                mEmergencyCallHelper,
                mToneGeneratorFactory,
                mClockProxy,
                mAudioProcessingNotification,
                mBluetoothStateReceiver,
                mCallAudioRouteStateMachineFactory,
                mCallAudioModeStateMachineFactory,
                mInCallControllerFactory,
                mCallDiagnosticServiceController,
                mRoleManagerAdapter,
                mToastFactory,
                mCallEndpointControllerFactory,
                mCallAnomalyWatchdog,
                mAccessibilityManagerAdapter,
                // Just do async tasks synchronously to support testing.
                command -> command.run(),
                // For call audio tasks
                command -> command.run(),
                mBlockedNumbersAdapter,
                TransactionManager.getTestInstance(),
                mEmergencyCallDiagnosticLogger,
                mCommunicationDeviceTracker,
                mCallStreamingNotification,
                mBluetoothDeviceManager,
                mFeatureFlags,
                mTelephonyFlags,
                (call, listener, context, timeoutsAdapter, lock) -> mIncomingCallFilterGraph);

        when(mPhoneAccountRegistrar.getPhoneAccount(
                eq(SELF_MANAGED_HANDLE), any())).thenReturn(SELF_MANAGED_ACCOUNT);
        when(mPhoneAccountRegistrar.getPhoneAccount(
                eq(SIM_1_HANDLE), any())).thenReturn(SIM_1_ACCOUNT);
        when(mPhoneAccountRegistrar.getPhoneAccount(
                eq(SIM_2_HANDLE), any())).thenReturn(SIM_2_ACCOUNT);
        when(mPhoneAccountRegistrar.getPhoneAccount(
                eq(SIM_3_HANDLE), any())).thenReturn(SIM_3_ACCOUNT);
        when(mPhoneAccountRegistrar.getPhoneAccount(
                eq(CALL_PROVIDER_HANDLE), any())).thenReturn(CALL_PROVIDER_ACCOUNT);
        when(mPhoneAccountRegistrar.getPhoneAccount(
                eq(WORK_HANDLE), any())).thenReturn(WORK_ACCOUNT);
        when(mToastFactory.makeText(any(), anyInt(), anyInt())).thenReturn(mToast);
        when(mToastFactory.makeText(any(), any(), anyInt())).thenReturn(mToast);
        when(mFeatureFlags.separatelyBindToBtIncallService()).thenReturn(false);
        when(mFeatureFlags.telecomResolveHiddenDependencies()).thenReturn(true);
        when(mContext.createContextAsUser(any(UserHandle.class), eq(0)))
                .thenReturn(mMockCreateContextAsUser);
        when(mMockCreateContextAsUser.getSystemService(UserManager.class))
                .thenReturn(mMockCurrentUserManager);
        when(mIConnectionService.asBinder()).thenReturn(mock(IBinder.class));

        mComponentContextFixture.addConnectionService(
                SIM_1_ACCOUNT.getAccountHandle().getComponentName(), mIConnectionService);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        mComponentContextFixture.removeConnectionService(
                SIM_1_ACCOUNT.getAccountHandle().getComponentName(), mIConnectionService);
        super.tearDown();
    }

    @MediumTest
    @Test
    public void testConstructPossiblePhoneAccounts() throws Exception {
        when(mTelephonyFlags.simultaneousCallingIndications()).thenReturn(false);
        setupMsimAccounts();
        // Should be empty since the URI is null.
        assertEquals(0, mCallsManager.constructPossiblePhoneAccounts(null, null,
                false, false, false).size());
    }

    @MediumTest
    @Test
    public void testConstructPossiblePhoneAccounts_simulCalling() {
        when(mTelephonyFlags.simultaneousCallingIndications()).thenReturn(true);
        setupMsimAccounts();
        // Should be empty since the URI is null.
        assertEquals(0, mCallsManager.constructPossiblePhoneAccounts(null, null,
                false, false, false).size());
    }

    private Call constructOngoingCall(String callId, PhoneAccountHandle phoneAccountHandle) {
        Call ongoingCall = new Call(
                callId,
                mContext,
                mCallsManager,
                mLock,
                null /* ConnectionServiceRepository */,
                mPhoneNumberUtilsAdapter,
                TEST_ADDRESS,
                null /* GatewayInfo */,
                null /* connectionManagerPhoneAccountHandle */,
                phoneAccountHandle,
                Call.CALL_DIRECTION_INCOMING,
                false /* shouldAttachToExistingConnection*/,
                false /* isConference */,
                mClockProxy,
                mToastFactory,
                mFeatureFlags);
        ongoingCall.setState(CallState.ACTIVE, "just cuz");
        return ongoingCall;
    }

    /**
     * Verify behavior for multisim devices where we want to ensure that the active sim is used for
     * placing a new call.
     * @throws Exception
     */
    @MediumTest
    @Test
    public void testConstructPossiblePhoneAccountsMultiSimActive() throws Exception {
        when(mTelephonyFlags.simultaneousCallingIndications()).thenReturn(false);
        setupMsimAccounts();

        Call ongoingCall = constructOngoingCall("1", SIM_2_HANDLE);
        mCallsManager.addCall(ongoingCall);

        List<PhoneAccountHandle> phoneAccountHandles = mCallsManager.constructPossiblePhoneAccounts(
                TEST_ADDRESS, null, false, false, false);
        assertEquals(1, phoneAccountHandles.size());
        assertEquals(SIM_2_HANDLE, phoneAccountHandles.get(0));
    }

    /**
     * Verify behavior for multisim devices where we want to ensure that the active sim is used for
     * placing a new call when a restriction is set as well as other call providers from different
     * apps.
     */
    @MediumTest
    @Test
    public void testConstructPossiblePhoneAccountsMultiSimActive_simulCallingRestriction() {
        when(mTelephonyFlags.simultaneousCallingIndications()).thenReturn(true);
        setupAccountsWithCallingRestriction(Arrays.asList(SIM_1_HANDLE, SIM_2_HANDLE,
                CALL_PROVIDER_HANDLE), Collections.emptySet());

        Call ongoingCall = constructOngoingCall("1", SIM_2_HANDLE);
        mCallsManager.addCall(ongoingCall);

        List<PhoneAccountHandle> phoneAccountHandles = mCallsManager.constructPossiblePhoneAccounts(
                TEST_ADDRESS, null, false, false, false);
        assertEquals(Arrays.asList(SIM_2_HANDLE, CALL_PROVIDER_HANDLE), phoneAccountHandles);
    }

    /**
     * When we have 3 SIM PhoneAccounts on a device, but only 2 allow simultaneous calling, place a
     * call on a SIM that allows simultaneous calling and verify that the subset of PhoneAccounts
     * are available when in a call as well as the call provider.
     */
    @MediumTest
    @Test
    public void testConstructPossiblePhoneAccountsMultiSimActive_simulCallingRestrictionSubset() {
        when(mTelephonyFlags.simultaneousCallingIndications()).thenReturn(true);
        setupAccountsWithCallingRestriction(Arrays.asList(SIM_1_HANDLE, SIM_2_HANDLE, SIM_3_HANDLE,
                CALL_PROVIDER_HANDLE), new ArraySet<>(Arrays.asList(SIM_1_HANDLE, SIM_2_HANDLE)));

        Call ongoingCall = constructOngoingCall("1", SIM_2_HANDLE);
        mCallsManager.addCall(ongoingCall);

        List<PhoneAccountHandle> phoneAccountHandles = mCallsManager.constructPossiblePhoneAccounts(
                TEST_ADDRESS, null, false, false, false);
        assertEquals(Arrays.asList(SIM_1_HANDLE, SIM_2_HANDLE, CALL_PROVIDER_HANDLE),
                phoneAccountHandles);
    }

    /**
     * When we have 3 SIM PhoneAccounts on a device, but only 2 allow simultaneous calling, place a
     * call on the SIM that does not allow simultaneous calling and verify  that only that SIM and
     * the separate call provider are allowed to place a second call.
     */
    @MediumTest
    @Test
    public void testConstructPossiblePhoneAccountsMultiSimActive_simulCallingRestrictionSubset2() {
        when(mTelephonyFlags.simultaneousCallingIndications()).thenReturn(true);
        setupAccountsWithCallingRestriction(Arrays.asList(SIM_1_HANDLE, SIM_2_HANDLE, SIM_3_HANDLE,
                CALL_PROVIDER_HANDLE), new ArraySet<>(Arrays.asList(SIM_1_HANDLE, SIM_2_HANDLE)));

        Call ongoingCall = constructOngoingCall("1", SIM_3_HANDLE);
        mCallsManager.addCall(ongoingCall);

        List<PhoneAccountHandle> phoneAccountHandles = mCallsManager.constructPossiblePhoneAccounts(
                TEST_ADDRESS, null, false, false, false);
        assertEquals(Arrays.asList(SIM_3_HANDLE, CALL_PROVIDER_HANDLE),
                phoneAccountHandles);
    }

    /**
     * Verify behavior for multisim devices when there are no calls active; expect both accounts.
     * @throws Exception
     */
    @MediumTest
    @Test
    public void testConstructPossiblePhoneAccountsMultiSimIdle() throws Exception {
        when(mTelephonyFlags.simultaneousCallingIndications()).thenReturn(false);
        setupMsimAccounts();

        List<PhoneAccountHandle> phoneAccountHandles = mCallsManager.constructPossiblePhoneAccounts(
                TEST_ADDRESS, null, false, false, false);
        assertEquals(2, phoneAccountHandles.size());
    }

    /**
     * Verify behavior for multisim devices when there are no calls active and there are no calling
     * restrictions set; expect both accounts.
     */
    @MediumTest
    @Test
    public void testConstructPossiblePhoneAccountsMultiSimIdle_noSimulCallingRestriction() {
        when(mTelephonyFlags.simultaneousCallingIndications()).thenReturn(true);
        setupAccountsNoSimultaneousCallingRestriction();

        List<PhoneAccountHandle> phoneAccountHandles = mCallsManager.constructPossiblePhoneAccounts(
                TEST_ADDRESS, null, false, false, false);
        assertEquals(3, phoneAccountHandles.size());
    }

    /**
     * Verify behavior for multisim devices when there are no calls active and there are no calling
     * restrictions set; expect both accounts.
     */
    @MediumTest
    @Test
    public void testConstructPossiblePhoneAccountsMultiSimIdle_withSimulCallingRestriction() {
        when(mTelephonyFlags.simultaneousCallingIndications()).thenReturn(true);
        setupAccountsWithCallingRestriction(Arrays.asList(SIM_1_HANDLE, SIM_2_HANDLE,
                CALL_PROVIDER_HANDLE), Collections.emptySet());

        List<PhoneAccountHandle> phoneAccountHandles = mCallsManager.constructPossiblePhoneAccounts(
                TEST_ADDRESS, null, false, false, false);
        assertEquals(3, phoneAccountHandles.size());
    }

    /**
     * For DSDA-enabled multisim devices with an ongoing call, verify that both SIMs'
     * PhoneAccountHandles are constructed while placing a new call.
     * @throws Exception
     */
    @MediumTest
    @Test
    public void testConstructPossiblePhoneAccountsMultiSimActive_dsdaCallingPossible() throws
            Exception {
        when(mTelephonyFlags.simultaneousCallingIndications()).thenReturn(false);
        setupMsimAccounts();
        setMaxActiveVoiceSubscriptions(2);

        Call ongoingCall = constructOngoingCall("1", SIM_2_HANDLE);
        mCallsManager.addCall(ongoingCall);

        List<PhoneAccountHandle> phoneAccountHandles = mCallsManager.constructPossiblePhoneAccounts(
                TEST_ADDRESS, null, false, false, false);
        assertEquals(2, phoneAccountHandles.size());
    }

    /**
     * For multisim devices with an ongoing call, verify that all call capable PhoneAccounts are
     * available when creating a second call.
     */
    @MediumTest
    @Test
    public void testConstructPossiblePhoneAccountsMultiSimActive_simulCalling_dsdaPossible() {
        when(mTelephonyFlags.simultaneousCallingIndications()).thenReturn(true);
        setupAccountsNoSimultaneousCallingRestriction();

        Call ongoingCall = constructOngoingCall("1", SIM_2_HANDLE);
        mCallsManager.addCall(ongoingCall);

        List<PhoneAccountHandle> phoneAccountHandles = mCallsManager.constructPossiblePhoneAccounts(
                TEST_ADDRESS, null, false, false, false);
        assertEquals(3, phoneAccountHandles.size());
    }

    /**
     * For DSDA-enabled multisim devices with an ongoing call, verify that only the active SIMs'
     * PhoneAccountHandle is constructed while placing an emergency call.
     * @throws Exception
     */
    @MediumTest
    @Test
    public void testConstructPossiblePhoneAccountsMultiSimActive_dsdaCallingPossible_emergencyCall()
            throws Exception {
        when(mTelephonyFlags.simultaneousCallingIndications()).thenReturn(false);
        setupMsimAccounts();
        setMaxActiveVoiceSubscriptions(2);

        Call ongoingCall = constructOngoingCall("1", SIM_2_HANDLE);
        mCallsManager.addCall(ongoingCall);

        List<PhoneAccountHandle> phoneAccountHandles = mCallsManager.constructPossiblePhoneAccounts(
                TEST_ADDRESS, null, false, true /* isEmergency */, false);
        assertEquals(1, phoneAccountHandles.size());
        assertEquals(SIM_2_HANDLE, phoneAccountHandles.get(0));
    }

    /**
     * For multisim devices with an ongoing call, verify that only the active SIM's
     * PhoneAccountHandle is available if we have a calling restriction where only one SIM is
     * active at a time.
     */
    @MediumTest
    @Test
    public void testConstructPossiblePhoneAccountsMultiSimActive_simulCalling_emergencyCall() {
        when(mTelephonyFlags.simultaneousCallingIndications()).thenReturn(true);
        setupAccountsWithCallingRestriction(Arrays.asList(SIM_1_HANDLE, SIM_2_HANDLE,
                CALL_PROVIDER_HANDLE), Collections.emptySet());

        Call ongoingCall = constructOngoingCall("1", SIM_2_HANDLE);
        mCallsManager.addCall(ongoingCall);

        List<PhoneAccountHandle> phoneAccountHandles = mCallsManager.constructPossiblePhoneAccounts(
                TEST_ADDRESS, null, false, true /* isEmergency */, false);
        assertEquals(2, phoneAccountHandles.size());
        assertTrue("Candidate PAHs must contain the SIM account hosting the emergency call",
                phoneAccountHandles.contains(SIM_2_HANDLE));
        assertFalse("Candidate PAHs must not contain other SIM accounts",
                phoneAccountHandles.contains(SIM_1_HANDLE));
    }

    /**
     * For devices with an ongoing call on a call provider, verify that when an emergency
     * call is placed, all SIM accounts are still available for SIM selection.
     */
    @MediumTest
    @Test
    public void testConstructPossiblePhoneAccounts_callProvider_emergencyCall() {
        when(mTelephonyFlags.simultaneousCallingIndications()).thenReturn(true);
        setupAccountsWithCallingRestriction(Arrays.asList(SIM_1_HANDLE, SIM_2_HANDLE,
                CALL_PROVIDER_HANDLE), Collections.emptySet());

        Call ongoingCall = constructOngoingCall("1", CALL_PROVIDER_HANDLE);
        mCallsManager.addCall(ongoingCall);

        List<PhoneAccountHandle> phoneAccountHandles = mCallsManager.constructPossiblePhoneAccounts(
                TEST_ADDRESS, null, false, true /* isEmergency */, false);
        assertEquals(3, phoneAccountHandles.size());
    }

    /**
     * For multisim devices with an ongoing call, for backwards compatibility, only allow the
     * SIM with the active call to be chosen to place an emergency call, even if there is no
     * calling restriction.
     */
    @MediumTest
    @Test
    public void testConstructPossiblePhoneAccountsMultiSimActive_simulCallingRest_emergencyCall() {
        when(mTelephonyFlags.simultaneousCallingIndications()).thenReturn(true);
        setupAccountsNoSimultaneousCallingRestriction();

        Call ongoingCall = constructOngoingCall("1", SIM_2_HANDLE);
        mCallsManager.addCall(ongoingCall);

        List<PhoneAccountHandle> phoneAccountHandles = mCallsManager.constructPossiblePhoneAccounts(
                TEST_ADDRESS, null, false, true /* isEmergency */, false);
        assertEquals(2, phoneAccountHandles.size());
        assertTrue("Candidate PAHs must contain the SIM account hosting the emergency call",
                phoneAccountHandles.contains(SIM_2_HANDLE));
        assertFalse("Candidate PAHs must not contain other SIM accounts",
                phoneAccountHandles.contains(SIM_1_HANDLE));
    }

    /**
     * For multisim devices with an ongoing call on a call provider, it is still possible to place
     * a SIM call on any SIM account.
     */
    @MediumTest
    @Test
    public void testConstructPossiblePhoneAccounts_crossAccount_simulCalling() {
        when(mTelephonyFlags.simultaneousCallingIndications()).thenReturn(true);
        setupAccountsWithCallingRestriction(Arrays.asList(SIM_1_HANDLE, SIM_2_HANDLE,
                CALL_PROVIDER_HANDLE), Collections.emptySet());

        Call ongoingCall = constructOngoingCall("1", CALL_PROVIDER_HANDLE);
        mCallsManager.addCall(ongoingCall);

        List<PhoneAccountHandle> phoneAccountHandles = mCallsManager.constructPossiblePhoneAccounts(
                TEST_ADDRESS, null, false, false /* isEmergency */, false);
        assertEquals(3, phoneAccountHandles.size());
    }

    private void setupCallerInfoLookupHelper() {
        doAnswer(invocation -> {
            Uri handle = invocation.getArgument(0);
            CallerInfoLookupHelper.OnQueryCompleteListener listener = invocation.getArgument(1);
            CallerInfo info = new CallerInfo();
            if (CONTACT_PREFERRED_ACCOUNT.get(handle) != null) {
                PhoneAccountHandle pah = CONTACT_PREFERRED_ACCOUNT.get(handle);
                info.preferredPhoneAccountComponent = pah.getComponentName();
                info.preferredPhoneAccountId = pah.getId();
            }
            listener.onCallerInfoQueryComplete(handle, info);
            return null;
        }).when(mCallerInfoLookupHelper).startLookup(any(Uri.class),
                any(CallerInfoLookupHelper.OnQueryCompleteListener.class));
    }
    /**
     * Tests finding the outgoing call phone account where the call is being placed on a
     * self-managed ConnectionService.
     * @throws Exception
     */
    @MediumTest
    @Test
    public void testFindOutgoingCallPhoneAccountSelfManaged() throws Exception {
        setupCallerInfoLookupHelper();
        List<PhoneAccountHandle> accounts = mCallsManager.findOutgoingCallPhoneAccount(
                SELF_MANAGED_HANDLE, TEST_ADDRESS, false /* isVideo */, false /* isEmergency */, null /* userHandle */)
                .get();
        assertEquals(1, accounts.size());
        assertEquals(SELF_MANAGED_HANDLE, accounts.get(0));
    }

    /**
     * Tests finding the outgoing calling account where the call has no associated phone account,
     * but there is a user specified default which can be used.
     * @throws Exception
     */
    @MediumTest
    @Test
    public void testFindOutgoingCallAccountDefault() throws Exception {
        setupCallerInfoLookupHelper();
        when(mPhoneAccountRegistrar.getOutgoingPhoneAccountForScheme(any(), any())).thenReturn(
                SIM_1_HANDLE);
        when(mPhoneAccountRegistrar.getCallCapablePhoneAccounts(any(), anyBoolean(),
                any(), anyInt(), anyInt(), anyBoolean())).thenReturn(
                new ArrayList<>(Arrays.asList(SIM_1_HANDLE, SIM_2_HANDLE)));

        List<PhoneAccountHandle> accounts = mCallsManager.findOutgoingCallPhoneAccount(
                null /* phoneAcct */, TEST_ADDRESS, false /* isVideo */, false /* isEmergency */, null /* userHandle */)
                .get();

        // Should have found just the default.
        assertEquals(1, accounts.size());
        assertEquals(SIM_1_HANDLE, accounts.get(0));
    }

    /**
     * Tests finding the outgoing calling account where the call has no associated phone account,
     * but there is no user specified default which can be used.
     * @throws Exception
     */
    @MediumTest
    @Test
    public void testFindOutgoingCallAccountNoDefault() throws Exception {
        setupCallerInfoLookupHelper();
        when(mPhoneAccountRegistrar.getOutgoingPhoneAccountForScheme(any(), any())).thenReturn(
                null);
        when(mPhoneAccountRegistrar.getCallCapablePhoneAccounts(any(), anyBoolean(),
                any(), anyInt(), anyInt(), anyBoolean())).thenReturn(
                new ArrayList<>(Arrays.asList(SIM_1_HANDLE, SIM_2_HANDLE)));

        List<PhoneAccountHandle> accounts = mCallsManager.findOutgoingCallPhoneAccount(
                null /* phoneAcct */, TEST_ADDRESS, false /* isVideo */, false /* isEmergency */, null /* userHandle */)
                .get();

        assertEquals(2, accounts.size());
        assertTrue(accounts.contains(SIM_1_HANDLE));
        assertTrue(accounts.contains(SIM_2_HANDLE));
    }

    /**
     * Tests that we will default to a video capable phone account if one is available for a video
     * call.
     * @throws Exception
     */
    @MediumTest
    @Test
    public void testFindOutgoingCallAccountVideo() throws Exception {
        setupCallerInfoLookupHelper();
        when(mPhoneAccountRegistrar.getOutgoingPhoneAccountForScheme(any(), any())).thenReturn(
                null);
        when(mPhoneAccountRegistrar.getCallCapablePhoneAccounts(any(), anyBoolean(),
                any(), eq(PhoneAccount.CAPABILITY_VIDEO_CALLING), anyInt(), anyBoolean()))
                .thenReturn(new ArrayList<>(Arrays.asList(SIM_2_HANDLE)));

        List<PhoneAccountHandle> accounts = mCallsManager.findOutgoingCallPhoneAccount(
                null /* phoneAcct */, TEST_ADDRESS, true /* isVideo */, false /* isEmergency */, null /* userHandle */)
                .get();

        assertEquals(1, accounts.size());
        assertTrue(accounts.contains(SIM_2_HANDLE));
    }

    /**
     * Tests that we will default to a non-video capable phone account for a video call if no video
     * capable phone accounts are available.
     * @throws Exception
     */
    @MediumTest
    @Test
    public void testFindOutgoingCallAccountVideoNotAvailable() throws Exception {
        setupCallerInfoLookupHelper();
        when(mPhoneAccountRegistrar.getOutgoingPhoneAccountForScheme(any(), any())).thenReturn(
                null);
        // When querying for video capable accounts, return nothing.
        when(mPhoneAccountRegistrar.getCallCapablePhoneAccounts(any(), anyBoolean(),
                any(), eq(PhoneAccount.CAPABILITY_VIDEO_CALLING), anyInt(), anyBoolean())).
                thenReturn(Collections.emptyList());
        // When querying for non-video capable accounts, return one.
        when(mPhoneAccountRegistrar.getCallCapablePhoneAccounts(any(), anyBoolean(),
                any(), eq(0 /* none specified */), anyInt(), anyBoolean())).thenReturn(
                new ArrayList<>(Arrays.asList(SIM_1_HANDLE)));
        List<PhoneAccountHandle> accounts = mCallsManager.findOutgoingCallPhoneAccount(
                null /* phoneAcct */, TEST_ADDRESS, true /* isVideo */, false /* isEmergency */, null /* userHandle */)
                .get();

        // Should have found one.
        assertEquals(1, accounts.size());
        assertTrue(accounts.contains(SIM_1_HANDLE));
    }

    /**
     * Tests that we will use the provided target phone account if it exists.
     * @throws Exception
     */
    @MediumTest
    @Test
    public void testUseSpecifiedAccount() throws Exception {
        setupCallerInfoLookupHelper();
        when(mPhoneAccountRegistrar.getOutgoingPhoneAccountForScheme(any(), any())).thenReturn(
                null);
        when(mPhoneAccountRegistrar.getCallCapablePhoneAccounts(any(), anyBoolean(),
                any(), anyInt(), anyInt(), anyBoolean())).thenReturn(
                new ArrayList<>(Arrays.asList(SIM_1_HANDLE, SIM_2_HANDLE)));

        List<PhoneAccountHandle> accounts = mCallsManager.findOutgoingCallPhoneAccount(
                SIM_2_HANDLE, TEST_ADDRESS, false /* isVideo */, false /* isEmergency */, null /* userHandle */).get();

        assertEquals(1, accounts.size());
        assertTrue(accounts.contains(SIM_2_HANDLE));
    }

    /**
     * Tests that we will use the provided target phone account if it exists.
     * @throws Exception
     */
    @MediumTest
    @Test
    public void testUseContactSpecificAcct() throws Exception {
        setupCallerInfoLookupHelper();
        when(mPhoneAccountRegistrar.getOutgoingPhoneAccountForScheme(any(), any())).thenReturn(
                null);
        when(mPhoneAccountRegistrar.getCallCapablePhoneAccounts(any(), anyBoolean(),
                any(), anyInt(), anyInt(), anyBoolean())).thenReturn(
                new ArrayList<>(Arrays.asList(SIM_1_HANDLE, SIM_2_HANDLE)));

        List<PhoneAccountHandle> accounts = mCallsManager.findOutgoingCallPhoneAccount(
                null, TEST_ADDRESS2, false /* isVideo */, false /* isEmergency */, Process.myUserHandle()).get();

        assertEquals(1, accounts.size());
        assertTrue(accounts.contains(SIM_1_HANDLE));
    }

    /**
     * Verifies that an active call will result in playing a DTMF tone when requested.
     * @throws Exception
     */
    @MediumTest
    @Test
    public void testPlayDtmfWhenActive() throws Exception {
        Call callSpy = addSpyCall();
        mCallsManager.playDtmfTone(callSpy, '1');
        verify(callSpy).playDtmfTone(anyChar());
    }

    /**
     * Verifies that DTMF requests are suppressed when a call is held.
     * @throws Exception
     */
    @MediumTest
    @Test
    public void testSuppessDtmfWhenHeld() throws Exception {
        Call callSpy = addSpyCall();
        callSpy.setState(CallState.ON_HOLD, "test");

        mCallsManager.playDtmfTone(callSpy, '1');
        verify(callSpy, never()).playDtmfTone(anyChar());
    }

    /**
     * Verifies that DTMF requests are suppressed when a call is held.
     * @throws Exception
     */
    @MediumTest
    @Test
    public void testCancelDtmfWhenHeld() throws Exception {
        Call callSpy = addSpyCall();
        mCallsManager.playDtmfTone(callSpy, '1');
        mCallsManager.markCallAsOnHold(callSpy);
        verify(callSpy).stopDtmfTone();
    }

    @SmallTest
    @Test
    public void testUnholdCallWhenOngoingCallCanBeHeld() {
        // GIVEN a CallsManager with ongoing call, and this call can be held
        Call ongoingCall = addSpyCall();
        doReturn(true).when(ongoingCall).can(Connection.CAPABILITY_HOLD);
        doReturn(true).when(ongoingCall).can(Connection.CAPABILITY_SUPPORT_HOLD);
        when(mConnectionSvrFocusMgr.getCurrentFocusCall()).thenReturn(ongoingCall);

        // and a held call
        Call heldCall = addSpyCall();

        // WHEN unhold the held call
        mCallsManager.unholdCall(heldCall);

        // THEN the ongoing call is held, and the focus request for incoming call is sent
        verify(ongoingCall).hold(any());
        verifyFocusRequestAndExecuteCallback(heldCall);

        // and held call is unhold now
        verify(heldCall).unhold(any());
    }

    @SmallTest
    @Test
    public void testUnholdCallWhenOngoingCallCanNotBeHeldAndFromDifferentConnectionService() {
        // GIVEN a CallsManager with ongoing call, and this call can not be held
        Call ongoingCall = addSpyCall(SIM_1_HANDLE, CallState.ACTIVE);
        doReturn(false).when(ongoingCall).can(Connection.CAPABILITY_HOLD);
        doReturn(false).when(ongoingCall).can(Connection.CAPABILITY_SUPPORT_HOLD);
        when(mConnectionSvrFocusMgr.getCurrentFocusCall()).thenReturn(ongoingCall);

        // and a held call which has different ConnectionService
        Call heldCall = addSpyCall(VOIP_1_HANDLE, CallState.ON_HOLD);

        // WHEN unhold the held call
        mCallsManager.unholdCall(heldCall);

        // THEN the ongoing call is disconnected, and the focus request for incoming call is sent
        verify(ongoingCall).disconnect(any());
        verifyFocusRequestAndExecuteCallback(heldCall);

        // and held call is unhold now
        verify(heldCall).unhold(any());
    }

    /**
     * Ensures we don't auto-unhold a call from a different app when we locally disconnect a call.
     */
    @SmallTest
    @Test
    public void testDontUnholdCallsBetweenConnectionServices() {
        // GIVEN a CallsManager with ongoing call
        Call ongoingCall = addSpyCall(SIM_1_HANDLE, CallState.ACTIVE);
        when(ongoingCall.isDisconnectHandledViaFuture()).thenReturn(false);
        doReturn(true).when(ongoingCall).can(Connection.CAPABILITY_HOLD);
        doReturn(true).when(ongoingCall).can(Connection.CAPABILITY_SUPPORT_HOLD);
        when(mConnectionSvrFocusMgr.getCurrentFocusCall()).thenReturn(ongoingCall);

        // and a held call which has different ConnectionService
        Call heldCall = addSpyCall(VOIP_1_HANDLE, CallState.ON_HOLD);

        // Disconnect and cleanup the active ongoing call.
        mCallsManager.disconnectCall(ongoingCall);
        mCallsManager.markCallAsRemoved(ongoingCall);

        // Should not unhold the held call since its in another app.
        verify(heldCall, never()).unhold();
    }

    /**
     * Ensures we do auto-unhold a call from the same app when we locally disconnect a call.
     */
    @SmallTest
    @Test
    public void testUnholdCallWhenDisconnectingInSameApp() {
        // GIVEN a CallsManager with ongoing call
        Call ongoingCall = addSpyCall(SIM_1_HANDLE, CallState.ACTIVE);
        when(ongoingCall.isDisconnectHandledViaFuture()).thenReturn(false);
        doReturn(true).when(ongoingCall).can(Connection.CAPABILITY_HOLD);
        doReturn(true).when(ongoingCall).can(Connection.CAPABILITY_SUPPORT_HOLD);
        when(mConnectionSvrFocusMgr.getCurrentFocusCall()).thenReturn(ongoingCall);

        // and a held call which has same ConnectionService
        Call heldCall = addSpyCall(SIM_1_HANDLE, CallState.ON_HOLD);

        // Disconnect and cleanup the active ongoing call.
        mCallsManager.disconnectCall(ongoingCall);
        mCallsManager.markCallAsRemoved(ongoingCall);

        // Should auto-unhold the held call since its in the same app.
        verify(heldCall).unhold();
    }

    @SmallTest
    @Test
    public void testUnholdCallWhenOngoingEmergCallCanNotBeHeldAndFromDifferentConnectionService() {
        // GIVEN a CallsManager with ongoing call, and this call can not be held, but it also an
        // emergency call.
        Call ongoingCall = addSpyCall(SIM_1_HANDLE, CallState.ACTIVE);
        doReturn(false).when(ongoingCall).can(Connection.CAPABILITY_HOLD);
        doReturn(false).when(ongoingCall).can(Connection.CAPABILITY_SUPPORT_HOLD);
        doReturn(true).when(ongoingCall).isEmergencyCall();
        when(mConnectionSvrFocusMgr.getCurrentFocusCall()).thenReturn(ongoingCall);

        // and a held call which has different ConnectionService
        Call heldCall = addSpyCall(VOIP_1_HANDLE, CallState.ON_HOLD);

        // WHEN unhold the held call
        mCallsManager.unholdCall(heldCall);

        // THEN the ongoing call will not be disconnected (because its an emergency call)
        verify(ongoingCall, never()).disconnect(any());

        // and held call is not un-held
        verify(heldCall, never()).unhold(any());
    }

    @SmallTest
    @Test
    public void testUnholdCallWhenOngoingCallCanNotBeHeldAndHasSameConnectionService() {
        // GIVEN a CallsManager with ongoing call, and this call can not be held
        Call ongoingCall = addSpyCall(SIM_1_HANDLE, CallState.ACTIVE);
        doReturn(false).when(ongoingCall).can(Connection.CAPABILITY_HOLD);
        doReturn(true).when(ongoingCall).can(Connection.CAPABILITY_SUPPORT_HOLD);
        when(mConnectionSvrFocusMgr.getCurrentFocusCall()).thenReturn(ongoingCall);

        // and a held call which has the same ConnectionService
        Call heldCall = addSpyCall(SIM_2_HANDLE, CallState.ON_HOLD);

        // WHEN unhold the held call
        mCallsManager.unholdCall(heldCall);

        // THEN the ongoing call is held
        verify(ongoingCall).hold(any());
        verifyFocusRequestAndExecuteCallback(heldCall);

        // and held call is unhold now
        verify(heldCall).unhold(any());
    }

    @SmallTest
    @Test
    public void testDuplicateAnswerCall() {
        Call incomingCall = addSpyCall(CallState.RINGING);
        doAnswer(invocation -> {
            doReturn(CallState.ANSWERED).when(incomingCall).getState();
            return null;
        }).when(incomingCall).answer(anyInt());
        mCallsManager.answerCall(incomingCall, VideoProfile.STATE_AUDIO_ONLY);
        verifyFocusRequestAndExecuteCallback(incomingCall);
        reset(mConnectionSvrFocusMgr);
        mCallsManager.answerCall(incomingCall, VideoProfile.STATE_AUDIO_ONLY);
        verifyFocusRequestAndExecuteCallback(incomingCall);

        verify(incomingCall, times(2)).answer(anyInt());
    }

    @SmallTest
    @Test
    public void testAnswerCallWhenOngoingCallCanBeHeld() {
        // GIVEN a CallsManager with ongoing call, and this call can be held
        Call ongoingCall = addSpyCall();
        doReturn(true).when(ongoingCall).can(Connection.CAPABILITY_HOLD);
        doReturn(true).when(ongoingCall).can(Connection.CAPABILITY_SUPPORT_HOLD);
        when(mConnectionSvrFocusMgr.getCurrentFocusCall()).thenReturn(ongoingCall);

        // WHEN answer an incoming call
        Call incomingCall = addSpyCall(CallState.RINGING);
        mCallsManager.answerCall(incomingCall, VideoProfile.STATE_AUDIO_ONLY);

        // THEN the ongoing call is held and the focus request for incoming call is sent
        verify(ongoingCall).hold(anyString());
        verifyFocusRequestAndExecuteCallback(incomingCall);

        // and the incoming call is answered.
        verify(incomingCall).answer(VideoProfile.STATE_AUDIO_ONLY);
    }

    @SmallTest
    @Test
    public void testAnswerCallWhenOngoingHasSameConnectionService() {
        // GIVEN a CallsManager with ongoing call, and this call can not be held
        Call ongoingCall = addSpyCall(SIM_1_HANDLE, CallState.ACTIVE);
        doReturn(false).when(ongoingCall).can(Connection.CAPABILITY_HOLD);
        when(mConnectionSvrFocusMgr.getCurrentFocusCall()).thenReturn(ongoingCall);

        // WHEN answer an incoming call
        Call incomingCall = addSpyCall(VOIP_1_HANDLE, CallState.RINGING);
        mCallsManager.answerCall(incomingCall, VideoProfile.STATE_AUDIO_ONLY);

        // THEN nothing happened on the ongoing call and the focus request for incoming call is sent
        verifyFocusRequestAndExecuteCallback(incomingCall);

        // and the incoming call is answered.
        verify(incomingCall).answer(VideoProfile.STATE_AUDIO_ONLY);
    }

    @SmallTest
    @Test
    public void testAnswerCallWhenOngoingHasDifferentConnectionService() {
        // GIVEN a CallsManager with ongoing call, and this call can not be held
        Call ongoingCall = addSpyCall(SIM_1_HANDLE, CallState.ACTIVE);
        doReturn(false).when(ongoingCall).can(Connection.CAPABILITY_HOLD);
        doReturn(true).when(ongoingCall).can(Connection.CAPABILITY_SUPPORT_HOLD);
        when(mConnectionSvrFocusMgr.getCurrentFocusCall()).thenReturn(ongoingCall);

        // WHEN answer an incoming call
        Call incomingCall = addSpyCall(VOIP_1_HANDLE, CallState.RINGING);
        mCallsManager.answerCall(incomingCall, VideoProfile.STATE_AUDIO_ONLY);

        // THEN the ongoing call is disconnected and the focus request for incoming call is sent
        verify(ongoingCall).disconnect();
        verifyFocusRequestAndExecuteCallback(incomingCall);

        // and the incoming call is answered.
        verify(incomingCall).answer(VideoProfile.STATE_AUDIO_ONLY);
    }

    @SmallTest
    @Test
    public void testAnswerCallWhenOngoingHasDifferentConnectionServiceButIsEmerg() {
        // GIVEN a CallsManager with ongoing call, and this call can not be held
        Call ongoingCall = addSpyCall(SIM_1_HANDLE, CallState.ACTIVE);
        doReturn(false).when(ongoingCall).can(Connection.CAPABILITY_HOLD);
        doReturn(true).when(ongoingCall).can(Connection.CAPABILITY_SUPPORT_HOLD);
        doReturn(true).when(ongoingCall).isEmergencyCall();
        when(mConnectionSvrFocusMgr.getCurrentFocusCall()).thenReturn(ongoingCall);

        // WHEN answer an incoming call
        Call incomingCall = addSpyCall(VOIP_1_HANDLE, CallState.RINGING);
        mCallsManager.answerCall(incomingCall, VideoProfile.STATE_AUDIO_ONLY);

        // THEN the ongoing call is not disconnected
        verify(ongoingCall, never()).disconnect();

        // and the incoming call is not answered, but is rejected instead.
        verify(incomingCall, never()).answer(VideoProfile.STATE_AUDIO_ONLY);
        verify(incomingCall).reject(eq(false), any(), any());
    }

    @SmallTest
    @Test
    public void testAnswerCallWhenMultipleHeldCallsExisted() {
        // Given an ongoing call and held call with the ConnectionService connSvr1. The
        // ConnectionService connSvr1 can handle one held call
        Call ongoingCall = addSpyCall(SIM_1_HANDLE, CallState.ACTIVE);
        doReturn(false).when(ongoingCall).can(Connection.CAPABILITY_HOLD);
        doReturn(true).when(ongoingCall).can(Connection.CAPABILITY_SUPPORT_HOLD);
        doReturn(CallState.ACTIVE).when(ongoingCall).getState();
        when(mConnectionSvrFocusMgr.getCurrentFocusCall()).thenReturn(ongoingCall);

        Call heldCall = addSpyCall(SIM_1_HANDLE, CallState.ON_HOLD);
        doReturn(CallState.ON_HOLD).when(heldCall).getState();

        // and other held call has difference ConnectionService
        Call heldCall2 = addSpyCall(VOIP_1_HANDLE, CallState.ON_HOLD);
        doReturn(CallState.ON_HOLD).when(heldCall2).getState();

        // WHEN answer an incoming call which ConnectionService is connSvr1
        Call incomingCall = addSpyCall(SIM_1_HANDLE, CallState.RINGING);
        doReturn(true).when(incomingCall).can(Connection.CAPABILITY_SUPPORT_HOLD);
        mCallsManager.answerCall(incomingCall, VideoProfile.STATE_AUDIO_ONLY);

        // THEN the previous held call is disconnected
        verify(heldCall).disconnect();

        // and the ongoing call is held
        verify(ongoingCall).hold();

        // and the heldCall2 is not disconnected
        verify(heldCall2, never()).disconnect();

        // and the focus request is sent
        verifyFocusRequestAndExecuteCallback(incomingCall);

        // and the incoming call is answered
        verify(incomingCall).answer(VideoProfile.STATE_AUDIO_ONLY);
    }

    @SmallTest
    @Test
    public void testAnswerThirdCallWhenTwoCallsOnDifferentSims_disconnectsHeldCall() {
        // Given an ongoing call on SIM1
        Call ongoingCall = addSpyCall(SIM_1_HANDLE, CallState.ACTIVE);
        doReturn(true).when(ongoingCall).can(Connection.CAPABILITY_SUPPORT_HOLD);
        doReturn(CallState.ACTIVE).when(ongoingCall).getState();
        when(mConnectionSvrFocusMgr.getCurrentFocusCall()).thenReturn(ongoingCall);

        // And a held call on SIM2, which belongs to the same ConnectionService
        Call heldCall = addSpyCall(SIM_2_HANDLE, CallState.ON_HOLD);
        doReturn(CallState.ON_HOLD).when(heldCall).getState();

        // on answering an incoming call on SIM1, which belongs to the same ConnectionService
        Call incomingCall = addSpyCall(SIM_1_HANDLE, CallState.RINGING);
        mCallsManager.answerCall(incomingCall, VideoProfile.STATE_AUDIO_ONLY);

        // THEN the previous held call is disconnected
        verify(heldCall).disconnect();

        // and the ongoing call is held
        verify(ongoingCall).hold();

        // and the focus request is sent
        verifyFocusRequestAndExecuteCallback(incomingCall);
        // and the incoming call is answered
        verify(incomingCall).answer(VideoProfile.STATE_AUDIO_ONLY);
    }

    @SmallTest
    @Test
    public void testAnswerThirdCallDifferentSimWhenTwoCallsOnSameSim_disconnectsHeldCall() {
        // Given an ongoing call on SIM1
        Call ongoingCall = addSpyCall(SIM_1_HANDLE, CallState.ACTIVE);
        doReturn(true).when(ongoingCall).can(Connection.CAPABILITY_SUPPORT_HOLD);
        doReturn(CallState.ACTIVE).when(ongoingCall).getState();
        when(mConnectionSvrFocusMgr.getCurrentFocusCall()).thenReturn(ongoingCall);

        // And a held call on SIM1
        Call heldCall = addSpyCall(SIM_1_HANDLE, CallState.ON_HOLD);
        doReturn(CallState.ON_HOLD).when(heldCall).getState();

        // on answering an incoming call on SIM2, which belongs to the same ConnectionService
        Call incomingCall = addSpyCall(SIM_2_HANDLE, CallState.RINGING);
        mCallsManager.answerCall(incomingCall, VideoProfile.STATE_AUDIO_ONLY);

        // THEN the previous held call is disconnected
        verify(heldCall).disconnect();

        // and the ongoing call is held
        verify(ongoingCall).hold();

        // and the focus request is sent
        verifyFocusRequestAndExecuteCallback(incomingCall);
        // and the incoming call is answered
        verify(incomingCall).answer(VideoProfile.STATE_AUDIO_ONLY);
    }

    @SmallTest
    @Test
    public void testAnswerCallWhenNoOngoingCallExisted() {
        // GIVEN a CallsManager with no ongoing call.

        // WHEN answer an incoming call
        Call incomingCall = addSpyCall(CallState.RINGING);
        mCallsManager.answerCall(incomingCall, VideoProfile.STATE_AUDIO_ONLY);

        // THEN the focus request for incoming call is sent
        verifyFocusRequestAndExecuteCallback(incomingCall);

        // and the incoming call is answered.
        verify(incomingCall).answer(VideoProfile.STATE_AUDIO_ONLY);
    }

    @SmallTest
    @Test
    public void testAnswerAlreadyActiveCall() {
        // GIVEN a CallsManager with no ongoing call.

        // WHEN answer an already active call
        Call incomingCall = addSpyCall(CallState.RINGING);
        mCallsManager.answerCall(incomingCall, VideoProfile.STATE_AUDIO_ONLY);

        // THEN the focus request for incoming call is sent
        verifyFocusRequestAndExecuteCallback(incomingCall);

        // and the incoming call is answered.
        verify(incomingCall).answer(VideoProfile.STATE_AUDIO_ONLY);

        // and the incoming call's state is now ANSWERED
        assertEquals(CallState.ANSWERED, incomingCall.getState());
    }

    @SmallTest
    @Test
    public void testSetActiveCallWhenOngoingCallCanNotBeHeldAndFromDifferentConnectionService() {
        // GIVEN a CallsManager with ongoing call, and this call can not be held
        Call ongoingCall = addSpyCall(SIM_1_HANDLE, CallState.ACTIVE);
        doReturn(false).when(ongoingCall).can(Connection.CAPABILITY_HOLD);
        doReturn(false).when(ongoingCall).can(Connection.CAPABILITY_SUPPORT_HOLD);
        doReturn(ongoingCall).when(mConnectionSvrFocusMgr).getCurrentFocusCall();

        // and a new self-managed call which has different ConnectionService
        Call newCall = addSpyCall(VOIP_1_HANDLE, CallState.ACTIVE);
        doReturn(true).when(newCall).isSelfManaged();

        // WHEN active the new call
        mCallsManager.markCallAsActive(newCall);

        // THEN the ongoing call is disconnected, and the focus request for the new call is sent
        verify(ongoingCall).disconnect();
        verifyFocusRequestAndExecuteCallback(newCall);

        // and the new call is active
        assertEquals(CallState.ACTIVE, newCall.getState());
    }

    @SmallTest
    @Test
    public void testSetActiveCallWhenOngoingCallCanNotBeHeldAndHasSameConnectionService() {
        // GIVEN a CallsManager with ongoing call, and this call can not be held
        Call ongoingCall = addSpyCall(SIM_1_HANDLE, CallState.ACTIVE);
        doReturn(false).when(ongoingCall).can(Connection.CAPABILITY_HOLD);
        doReturn(false).when(ongoingCall).can(Connection.CAPABILITY_SUPPORT_HOLD);
        when(mConnectionSvrFocusMgr.getCurrentFocusCall()).thenReturn(ongoingCall);

        // and a new self-managed call which has the same ConnectionService
        Call newCall = addSpyCall(SIM_1_HANDLE, CallState.ACTIVE);
        doReturn(true).when(newCall).isSelfManaged();

        // WHEN active the new call
        mCallsManager.markCallAsActive(newCall);

        // THEN the ongoing call isn't disconnected
        verify(ongoingCall, never()).disconnect();
        verifyFocusRequestAndExecuteCallback(newCall);

        // and the new call is active
        assertEquals(CallState.ACTIVE, newCall.getState());
    }

    @SmallTest
    @Test
    public void testSetActiveCallWhenOngoingCallCanBeHeld() {
        // GIVEN a CallsManager with ongoing call, and this call can be held
        Call ongoingCall = addSpyCall();
        doReturn(true).when(ongoingCall).can(Connection.CAPABILITY_HOLD);
        doReturn(true).when(ongoingCall).can(Connection.CAPABILITY_SUPPORT_HOLD);
        doReturn(ongoingCall).when(mConnectionSvrFocusMgr).getCurrentFocusCall();

        // and a new self-managed call
        Call newCall = addSpyCall();
        doReturn(true).when(newCall).isSelfManaged();

        // WHEN active the new call
        mCallsManager.markCallAsActive(newCall);

        // THEN the ongoing call is held
        verify(ongoingCall).hold(anyString());
        verifyFocusRequestAndExecuteCallback(newCall);

        // and the new call is active
        assertEquals(CallState.ACTIVE, newCall.getState());
    }

    @SmallTest
    @Test
    public void testDisconnectDialingCallOnIncoming() {
        // GIVEN a CallsManager with a self-managed call which is dialing, and this call can be held
        Call ongoingCall = addSpyCall(SELF_MANAGED_HANDLE, CallState.DIALING);
        ongoingCall.setState(CallState.DIALING, "test");
        doReturn(true).when(ongoingCall).can(Connection.CAPABILITY_HOLD);
        doReturn(true).when(ongoingCall).can(Connection.CAPABILITY_SUPPORT_HOLD);
        doReturn(true).when(ongoingCall).isSelfManaged();
        doReturn(ongoingCall).when(mConnectionSvrFocusMgr).getCurrentFocusCall();

        // and a new incoming managed call
        Call newCall = addSpyCall();
        doReturn(false).when(newCall).isRespondViaSmsCapable();
        newCall.setState(CallState.RINGING, "test");

        // WHEN answering the new call
        mCallsManager.answerCall(newCall, VideoProfile.STATE_AUDIO_ONLY);

        // THEN the ongoing call is disconnected
        verify(ongoingCall).disconnect();

        // AND focus is requested for the new call
        ArgumentCaptor<CallsManager.RequestCallback> requestCaptor =
                ArgumentCaptor.forClass(CallsManager.RequestCallback.class);
        verify(mConnectionSvrFocusMgr).requestFocus(eq(newCall), requestCaptor.capture());
        // since we're mocking the focus manager, we'll just pretend it did its thing.
        requestCaptor.getValue().onRequestFocusDone(newCall);

        // and the new call is marked answered
        assertEquals(CallState.ANSWERED, newCall.getState());
    }

    @SmallTest
    @Test
    public void testNoFilteringOfSelfManagedCalls() {
        // GIVEN an incoming call which is self managed.
        Call incomingCall = addSpyCall(SELF_MANAGED_HANDLE, CallState.NEW);
        doReturn(false).when(incomingCall).can(Connection.CAPABILITY_HOLD);
        doReturn(false).when(incomingCall).can(Connection.CAPABILITY_SUPPORT_HOLD);
        doReturn(true).when(incomingCall).isSelfManaged();
        doReturn(true).when(incomingCall).setState(anyInt(), any());

        // WHEN the incoming call is successfully added.
        mCallsManager.onSuccessfulIncomingCall(incomingCall);

        // THEN the incoming call is not using call filtering
        verify(incomingCall).setIsUsingCallFiltering(eq(false));
    }

    @SmallTest
    @Test
    public void testNoFilteringOfNetworkIdentifiedEmergencyCalls() {
        // GIVEN an incoming call which is network identified as an emergency call.
        Call incomingCall = addSpyCall(CallState.NEW);
        incomingCall.setConnectionProperties(Connection.PROPERTY_NETWORK_IDENTIFIED_EMERGENCY_CALL);
        doReturn(false).when(incomingCall).can(Connection.CAPABILITY_HOLD);
        doReturn(false).when(incomingCall).can(Connection.CAPABILITY_SUPPORT_HOLD);
        doReturn(true).when(incomingCall)
                .hasProperty(Connection.PROPERTY_NETWORK_IDENTIFIED_EMERGENCY_CALL);
        doReturn(true).when(incomingCall).setState(anyInt(), any());

        // WHEN the incoming call is successfully added.
        mCallsManager.onSuccessfulIncomingCall(incomingCall);

        // THEN the incoming call is not using call filtering
        verify(incomingCall).setIsUsingCallFiltering(eq(false));
    }

    @SmallTest
    @Test
    public void testNoFilteringOfEmergencySmsModeCalls() {
        // GIVEN an incoming call which is network identified as an emergency call.
        Call incomingCall = addSpyCall(CallState.NEW);
        when(mComponentContextFixture.getTelephonyManager().isInEmergencySmsMode())
                .thenReturn(true);
        doReturn(false).when(incomingCall).can(Connection.CAPABILITY_HOLD);
        doReturn(false).when(incomingCall).can(Connection.CAPABILITY_SUPPORT_HOLD);
        doReturn(true).when(incomingCall).setState(anyInt(), any());

        // WHEN the incoming call is successfully added.
        mCallsManager.onSuccessfulIncomingCall(incomingCall);

        // THEN the incoming call is not using call filtering
        verify(incomingCall).setIsUsingCallFiltering(eq(false));
    }

    @SmallTest
    @Test
    public void testAcceptIncomingCallWhenHeadsetMediaButtonShortPress() {
        // GIVEN an incoming call
        Call incomingCall = addSpyCall();
        doReturn(CallState.RINGING).when(incomingCall).getState();

        // WHEN media button short press
        mCallsManager.onMediaButton(HeadsetMediaButton.SHORT_PRESS);

        // THEN the incoming call is answered
        ArgumentCaptor<CallsManager.RequestCallback> captor = ArgumentCaptor.forClass(
                CallsManager.RequestCallback.class);
        verify(mConnectionSvrFocusMgr).requestFocus(eq(incomingCall), captor.capture());
        captor.getValue().onRequestFocusDone(incomingCall);
        verify(incomingCall).answer(VideoProfile.STATE_AUDIO_ONLY);
    }

    @SmallTest
    @Test
    public void testRejectIncomingCallWhenHeadsetMediaButtonLongPress() {
        // GIVEN an incoming call
        Call incomingCall = addSpyCall();
        doReturn(CallState.RINGING).when(incomingCall).getState();

        // WHEN media button long press
        mCallsManager.onMediaButton(HeadsetMediaButton.LONG_PRESS);

        // THEN the incoming call is rejected
        verify(incomingCall).reject(false, null);
    }

    @SmallTest
    @Test
    public void testHangupOngoingCallWhenHeadsetMediaButtonShortPress() {
        // GIVEN an ongoing call
        Call ongoingCall = addSpyCall();
        doReturn(CallState.ACTIVE).when(ongoingCall).getState();

        // WHEN media button short press
        mCallsManager.onMediaButton(HeadsetMediaButton.SHORT_PRESS);

        // THEN the active call is disconnected
        verify(ongoingCall).disconnect();
    }

    @SmallTest
    @Test
    public void testToggleMuteWhenHeadsetMediaButtonLongPressDuringOngoingCall() {
        // GIVEN an ongoing call
        Call ongoingCall = addSpyCall();
        doReturn(CallState.ACTIVE).when(ongoingCall).getState();

        // WHEN media button long press
        mCallsManager.onMediaButton(HeadsetMediaButton.LONG_PRESS);

        // THEN the microphone toggle mute
        verify(mCallAudioRouteStateMachine)
                .sendMessageWithSessionInfo(CallAudioRouteStateMachine.TOGGLE_MUTE);
    }

    @SmallTest
    @Test
    public void testSwapCallsWhenHeadsetMediaButtonShortPressDuringTwoCalls() {
        // GIVEN an ongoing call, and this call can be held
        Call ongoingCall = addSpyCall();
        doReturn(CallState.ACTIVE).when(ongoingCall).getState();
        doReturn(true).when(ongoingCall).can(Connection.CAPABILITY_HOLD);
        doReturn(true).when(ongoingCall).can(Connection.CAPABILITY_SUPPORT_HOLD);
        when(mConnectionSvrFocusMgr.getCurrentFocusCall()).thenReturn(ongoingCall);

        // and a held call
        Call heldCall = addSpyCall();
        doReturn(CallState.ON_HOLD).when(heldCall).getState();

        // WHEN media button short press
        mCallsManager.onMediaButton(HeadsetMediaButton.SHORT_PRESS);

        // THEN the ongoing call is held, and the focus request for heldCall call is sent
        verify(ongoingCall).hold(nullable(String.class));
        verifyFocusRequestAndExecuteCallback(heldCall);

        // and held call is unhold now
        verify(heldCall).unhold(nullable(String.class));
    }

    @SmallTest
    @Test
    public void testHangupActiveCallWhenHeadsetMediaButtonLongPressDuringTwoCalls() {
        // GIVEN an ongoing call
        Call ongoingCall = addSpyCall();
        doReturn(CallState.ACTIVE).when(ongoingCall).getState();

        // and a held call
        Call heldCall = addSpyCall();
        doReturn(CallState.ON_HOLD).when(heldCall).getState();

        // WHEN media button long press
        mCallsManager.onMediaButton(HeadsetMediaButton.LONG_PRESS);

        // THEN the ongoing call is disconnected
        verify(ongoingCall).disconnect();
    }

    @SmallTest
    @Test
    public void testDndFilterAppliesOfCallsWhenPhoneAccountRequestsSkipped() {
        // GIVEN an incoming call which is from a PhoneAccount that requested to skip filtering.
        when(mFeatureFlags.skipFilterPhoneAccountPerformDndFilter()).thenReturn(true);
        Call incomingCall = addSpyCall(SIM_1_HANDLE, CallState.NEW);
        Bundle extras = new Bundle();
        extras.putBoolean(PhoneAccount.EXTRA_SKIP_CALL_FILTERING, true);
        PhoneAccount skipRequestedAccount = new PhoneAccount.Builder(SIM_2_HANDLE, "Skipper")
            .setCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION
                | PhoneAccount.CAPABILITY_CALL_PROVIDER)
            .setExtras(extras)
            .setIsEnabled(true)
            .build();
        when(mPhoneAccountRegistrar.getPhoneAccountUnchecked(SIM_1_HANDLE))
            .thenReturn(skipRequestedAccount);
        doReturn(false).when(incomingCall).can(Connection.CAPABILITY_HOLD);
        doReturn(false).when(incomingCall).can(Connection.CAPABILITY_SUPPORT_HOLD);
        doReturn(false).when(incomingCall).isSelfManaged();
        doReturn(true).when(incomingCall).setState(anyInt(), any());

        // WHEN the incoming call is successfully added.
        mCallsManager.onSuccessfulIncomingCall(incomingCall);

        // THEN the incoming call is still applying Dnd filter.
        verify(incomingCall).setIsUsingCallFiltering(eq(true));
    }

    @SmallTest
    @Test
    public void testNoFilterAppliesOfCallsWhenFlagNotEnabled() {
        // Flag is not enabled.
        when(mFeatureFlags.skipFilterPhoneAccountPerformDndFilter()).thenReturn(false);
        Call incomingCall = addSpyCall(SIM_1_HANDLE, CallState.NEW);
        Bundle extras = new Bundle();
        extras.putBoolean(PhoneAccount.EXTRA_SKIP_CALL_FILTERING, true);
        PhoneAccount skipRequestedAccount = new PhoneAccount.Builder(SIM_2_HANDLE, "Skipper")
                .setCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION
                        | PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .setExtras(extras)
                .setIsEnabled(true)
                .build();
        when(mPhoneAccountRegistrar.getPhoneAccountUnchecked(SIM_1_HANDLE))
                .thenReturn(skipRequestedAccount);
        doReturn(false).when(incomingCall).can(Connection.CAPABILITY_HOLD);
        doReturn(false).when(incomingCall).can(Connection.CAPABILITY_SUPPORT_HOLD);
        doReturn(false).when(incomingCall).isSelfManaged();
        doReturn(true).when(incomingCall).setState(anyInt(), any());

        // WHEN the incoming call is successfully added.
        mCallsManager.onSuccessfulIncomingCall(incomingCall);

        // THEN the incoming call is not applying filter.
        verify(incomingCall).setIsUsingCallFiltering(eq(false));
    }

    @SmallTest
    @Test
    public void testIsInEmergencyCallNetwork() {
        // Setup a call which the network identified as an emergency call.
        Call ongoingCall = addSpyCall();
        ongoingCall.setConnectionProperties(Connection.PROPERTY_NETWORK_IDENTIFIED_EMERGENCY_CALL);

        assertFalse(ongoingCall.isEmergencyCall());
        assertTrue(ongoingCall.isNetworkIdentifiedEmergencyCall());
        assertTrue(mCallsManager.isInEmergencyCall());
    }

    @SmallTest
    @Test
    public void testIsInEmergencyCallLocal() {
        // Setup a call which is considered emergency based on its phone number.
        Call ongoingCall = addSpyCall();
        when(mComponentContextFixture.getTelephonyManager().isEmergencyNumber(any()))
                .thenReturn(true);
        ongoingCall.setHandle(Uri.fromParts("tel", "5551212", null),
                TelecomManager.PRESENTATION_ALLOWED);

        assertTrue(ongoingCall.isEmergencyCall());
        assertFalse(ongoingCall.isNetworkIdentifiedEmergencyCall());
        assertTrue(mCallsManager.isInEmergencyCall());
    }

    @SmallTest
    @Test
    public void testIsInEmergencyCallLocalDisconnected() {
        // Setup a call which is considered emergency based on its phone number.
        Call ongoingCall = addSpyCall();
        when(mComponentContextFixture.getTelephonyManager().isEmergencyNumber(any()))
                .thenReturn(true);
        ongoingCall.setHandle(Uri.fromParts("tel", "5551212", null),
                TelecomManager.PRESENTATION_ALLOWED);

        // and then set it as disconnected.
        ongoingCall.setState(CallState.DISCONNECTED, "");
        assertTrue(ongoingCall.isEmergencyCall());
        assertFalse(ongoingCall.isNetworkIdentifiedEmergencyCall());
        assertFalse(mCallsManager.isInEmergencyCall());
    }


    @SmallTest
    @Test
    public void testBlockNonEmergencyCallDuringEmergencyCall() throws Exception {
        // Setup a call which the network identified as an emergency call.
        Call ongoingCall = addSpyCall();
        ongoingCall.setConnectionProperties(Connection.PROPERTY_NETWORK_IDENTIFIED_EMERGENCY_CALL);
        assertTrue(mCallsManager.isInEmergencyCall());

        Call newCall = addSpyCall(CallState.NEW);
        ConnectionServiceWrapper service = mock(ConnectionServiceWrapper.class);
        doReturn(SIM_2_HANDLE.getComponentName()).when(service).getComponentName();

        // Ensure contact info lookup succeeds
        doAnswer(invocation -> {
            Uri handle = invocation.getArgument(0);
            CallerInfo info = new CallerInfo();
            CompletableFuture<Pair<Uri, CallerInfo>> callerInfoFuture = new CompletableFuture<>();
            callerInfoFuture.complete(new Pair<>(handle, info));
            return callerInfoFuture;
        }).when(mCallerInfoLookupHelper).startLookup(any(Uri.class));

        // Ensure we have candidate phone account handle info.
        when(mPhoneAccountRegistrar.getOutgoingPhoneAccountForScheme(any(), any())).thenReturn(
                SIM_1_HANDLE);
        when(mPhoneAccountRegistrar.getCallCapablePhoneAccounts(any(), anyBoolean(),
                any(), anyInt(), anyInt(), anyBoolean())).thenReturn(
                new ArrayList<>(Arrays.asList(SIM_1_HANDLE, SIM_2_HANDLE)));
        mCallsManager.addConnectionServiceRepositoryCache(SIM_2_HANDLE.getComponentName(),
                SIM_2_HANDLE.getUserHandle(), service);

        CompletableFuture<Call> callFuture = mCallsManager.startOutgoingCall(
                newCall.getHandle(), newCall.getTargetPhoneAccount(), new Bundle(),
                UserHandle.CURRENT, new Intent(), "com.test.stuff");

        verify(service, timeout(TEST_TIMEOUT)).createConnectionFailed(any());
        Call result = callFuture.get(TEST_TIMEOUT, TimeUnit.MILLISECONDS);
        assertNull(result);
    }

    @SmallTest
    @Test
    public void testHasEmergencyCallIncomingCallPermitted() {
        // Setup a call which is considered emergency based on its phone number.
        Call ongoingCall = addSpyCall();
        when(mComponentContextFixture.getTelephonyManager().isEmergencyNumber(any()))
                .thenReturn(true);
        ongoingCall.setHandle(Uri.fromParts("tel", "5551212", null),
                TelecomManager.PRESENTATION_ALLOWED);
        when(mPhoneAccountRegistrar.getPhoneAccountUnchecked(SELF_MANAGED_HANDLE))
                .thenReturn(SELF_MANAGED_ACCOUNT);
        when(mPhoneAccountRegistrar.getPhoneAccountUnchecked(SIM_1_HANDLE))
                .thenReturn(SIM_1_ACCOUNT);

        assertFalse(mCallsManager.isIncomingCallPermitted(SELF_MANAGED_HANDLE));
        assertFalse(mCallsManager.isIncomingCallPermitted(SIM_1_HANDLE));
    }

    @MediumTest
    @Test
    public void testManagedIncomingCallPermitted() {
        when(mPhoneAccountRegistrar.getPhoneAccountUnchecked(SIM_1_HANDLE))
                .thenReturn(SIM_1_ACCOUNT);

        // Don't care
        Call selfManagedCall = addSpyCall(SELF_MANAGED_HANDLE, CallState.ACTIVE);
        when(selfManagedCall.isSelfManaged()).thenReturn(true);
        assertTrue(mCallsManager.isIncomingCallPermitted(SIM_1_HANDLE));

        Call existingCall = addSpyCall(SIM_1_HANDLE, CallState.NEW);
        when(existingCall.isSelfManaged()).thenReturn(false);

        when(existingCall.getState()).thenReturn(CallState.RINGING);
        assertFalse(mCallsManager.isIncomingCallPermitted(SIM_1_HANDLE));

        when(existingCall.getState()).thenReturn(CallState.ON_HOLD);
        assertFalse(mCallsManager.isIncomingCallPermitted(SIM_1_HANDLE));
    }

    @MediumTest
    @Test
    public void testSelfManagedIncomingCallPermitted() {
        when(mPhoneAccountRegistrar.getPhoneAccountUnchecked(SELF_MANAGED_HANDLE))
                .thenReturn(SELF_MANAGED_ACCOUNT);

        // Don't care
        Call managedCall = addSpyCall(SIM_1_HANDLE, CallState.ACTIVE);
        when(managedCall.isSelfManaged()).thenReturn(false);
        assertTrue(mCallsManager.isIncomingCallPermitted(SELF_MANAGED_HANDLE));

        Call existingCall = addSpyCall(SELF_MANAGED_HANDLE, CallState.RINGING);
        when(existingCall.isSelfManaged()).thenReturn(true);
        assertFalse(mCallsManager.isIncomingCallPermitted(SELF_MANAGED_HANDLE));

        when(existingCall.getState()).thenReturn(CallState.ACTIVE);
        assertTrue(mCallsManager.isIncomingCallPermitted(SELF_MANAGED_HANDLE));

        // Add self managed calls up to 10
        for (int i = 0; i < 9; i++) {
            Call selfManagedCall = addSpyCall(SELF_MANAGED_HANDLE, CallState.ON_HOLD);
            when(selfManagedCall.isSelfManaged()).thenReturn(true);
        }
        assertFalse(mCallsManager.isIncomingCallPermitted(SELF_MANAGED_HANDLE));
    }

    @SmallTest
    @Test
    public void testManagedOutgoingCallPermitted() {
        when(mPhoneAccountRegistrar.getPhoneAccountUnchecked(SIM_1_HANDLE))
                .thenReturn(SIM_1_ACCOUNT);

        // Don't care
        Call selfManagedCall = addSpyCall(SELF_MANAGED_HANDLE, CallState.ACTIVE);
        when(selfManagedCall.isSelfManaged()).thenReturn(true);
        assertTrue(mCallsManager.isOutgoingCallPermitted(SIM_1_HANDLE));

        Call existingCall = addSpyCall(SIM_1_HANDLE, CallState.NEW);
        when(existingCall.isSelfManaged()).thenReturn(false);

        when(existingCall.getState()).thenReturn(CallState.CONNECTING);
        assertFalse(mCallsManager.isOutgoingCallPermitted(SIM_1_HANDLE));

        when(existingCall.getState()).thenReturn(CallState.DIALING);
        assertFalse(mCallsManager.isOutgoingCallPermitted(SIM_1_HANDLE));

        when(existingCall.getState()).thenReturn(CallState.ACTIVE);
        assertFalse(mCallsManager.isOutgoingCallPermitted(SIM_1_HANDLE));

        when(existingCall.getState()).thenReturn(CallState.ON_HOLD);
        assertFalse(mCallsManager.isOutgoingCallPermitted(SIM_1_HANDLE));
    }

    @SmallTest
    @Test
    public void testSelfManagedOutgoingCallPermitted() {
        when(mPhoneAccountRegistrar.getPhoneAccountUnchecked(SELF_MANAGED_HANDLE))
                .thenReturn(SELF_MANAGED_ACCOUNT);

        // Don't care
        Call managedCall = addSpyCall(SIM_1_HANDLE, CallState.ACTIVE);
        when(managedCall.isSelfManaged()).thenReturn(false);
        assertTrue(mCallsManager.isOutgoingCallPermitted(SELF_MANAGED_HANDLE));

        Call ongoingCall = addSpyCall(SELF_MANAGED_HANDLE, CallState.ACTIVE);
        when(ongoingCall.isSelfManaged()).thenReturn(true);
        when(mConnectionSvrFocusMgr.getCurrentFocusCall()).thenReturn(ongoingCall);

        when(ongoingCall.can(Connection.CAPABILITY_HOLD)).thenReturn(false);
        assertFalse(mCallsManager.isOutgoingCallPermitted(SELF_MANAGED_HANDLE));

        when(ongoingCall.can(Connection.CAPABILITY_HOLD)).thenReturn(true);
        assertTrue(mCallsManager.isOutgoingCallPermitted(SELF_MANAGED_HANDLE));

        Call handoverCall = addSpyCall(SELF_MANAGED_HANDLE, CallState.NEW);
        when(handoverCall.isSelfManaged()).thenReturn(true);
        when(handoverCall.getHandoverSourceCall()).thenReturn(mock(Call.class));
        assertTrue(mCallsManager.isOutgoingCallPermitted(handoverCall, SELF_MANAGED_HANDLE));

        // Add self managed calls up to 10
        for (int i = 0; i < 8; i++) {
            Call selfManagedCall = addSpyCall(SELF_MANAGED_HANDLE, CallState.ON_HOLD);
            when(selfManagedCall.isSelfManaged()).thenReturn(true);
        }
        assertFalse(mCallsManager.isOutgoingCallPermitted(SELF_MANAGED_HANDLE));
    }

    @SmallTest
    @Test
    public void testSelfManagedOutgoingCallPermittedHasEmergencyCall() {
        when(mPhoneAccountRegistrar.getPhoneAccountUnchecked(SELF_MANAGED_HANDLE))
                .thenReturn(SELF_MANAGED_ACCOUNT);

        Call emergencyCall = addSpyCall();
        when(emergencyCall.isEmergencyCall()).thenReturn(true);
        assertFalse(mCallsManager.isOutgoingCallPermitted(SELF_MANAGED_HANDLE));
    }

    @SmallTest
    @Test
    public void testMakeRoomForEmergencyCallAudioProcessingInProgress() {
        Call ongoingCall = addSpyCall(SIM_2_HANDLE, CallState.AUDIO_PROCESSING);

        Call newEmergencyCall = createCall(SIM_1_HANDLE, CallState.NEW);
        when(mComponentContextFixture.getTelephonyManager().isEmergencyNumber(any()))
                .thenReturn(true);
        newEmergencyCall.setHandle(Uri.fromParts("tel", "5551213", null),
                TelecomManager.PRESENTATION_ALLOWED);

        assertTrue(mCallsManager.makeRoomForOutgoingEmergencyCall(newEmergencyCall));
        verify(ongoingCall).disconnect(anyLong(), anyString());
    }

    @SmallTest
    @Test
    public void testMakeRoomForEmergencyCallDuringIncomingCall() {
        Call ongoingCall = addSpyCall(SIM_2_HANDLE, CallState.RINGING);

        Call newEmergencyCall = createCall(SIM_1_HANDLE, CallState.NEW);
        when(mComponentContextFixture.getTelephonyManager().isEmergencyNumber(any()))
                .thenReturn(true);
        newEmergencyCall.setHandle(Uri.fromParts("tel", "5551213", null),
                TelecomManager.PRESENTATION_ALLOWED);

        assertTrue(mCallsManager.makeRoomForOutgoingEmergencyCall(newEmergencyCall));
        verify(ongoingCall).reject(anyBoolean(), any(), any());
    }

    @SmallTest
    @Test
    public void testMakeRoomForEmergencyCallSimulatedRingingInProgress() {
        Call ongoingCall = addSpyCall(SIM_2_HANDLE, CallState.SIMULATED_RINGING);

        Call newEmergencyCall = createCall(SIM_1_HANDLE, CallState.NEW);
        when(mComponentContextFixture.getTelephonyManager().isEmergencyNumber(any()))
                .thenReturn(true);
        newEmergencyCall.setHandle(Uri.fromParts("tel", "5551213", null),
                TelecomManager.PRESENTATION_ALLOWED);

        assertTrue(mCallsManager.makeRoomForOutgoingEmergencyCall(newEmergencyCall));
        verify(ongoingCall).disconnect(anyString());
    }

    @SmallTest
    @Test
    public void testMakeRoomForEmergencyCallSimulatedRingingInProgressHasBeenActive() {
        Call ongoingCall = addSpyCall(SIM_2_HANDLE, CallState.ACTIVE);
        ongoingCall.setState(CallState.SIMULATED_RINGING, "");

        Call newEmergencyCall = createCall(SIM_1_HANDLE, CallState.NEW);
        when(mComponentContextFixture.getTelephonyManager().isEmergencyNumber(any()))
                .thenReturn(true);
        newEmergencyCall.setHandle(Uri.fromParts("tel", "5551213", null),
                TelecomManager.PRESENTATION_ALLOWED);

        assertTrue(mCallsManager.makeRoomForOutgoingEmergencyCall(newEmergencyCall));
        verify(ongoingCall).reject(anyBoolean(), any(), any());
    }

    @SmallTest
    @Test
    public void testMakeRoomForEmergencyCallDuringActiveAndRingingCallDisconnectRinging() {
        when(mPhoneAccountRegistrar.getPhoneAccountUnchecked(SIM_1_HANDLE))
                .thenReturn(SIM_1_ACCOUNT);
        Call ongoingCall = addSpyCall(SIM_1_HANDLE, CallState.ACTIVE);
        doReturn(true).when(ongoingCall).can(Connection.CAPABILITY_HOLD);
        Call ringingCall = addSpyCall(SIM_1_HANDLE, CallState.RINGING);

        Call newEmergencyCall = createCall(SIM_1_HANDLE, CallState.NEW);
        when(mComponentContextFixture.getTelephonyManager().isEmergencyNumber(any()))
                .thenReturn(true);
        newEmergencyCall.setHandle(Uri.fromParts("tel", "5551213", null),
                TelecomManager.PRESENTATION_ALLOWED);

        assertTrue(mCallsManager.makeRoomForOutgoingEmergencyCall(newEmergencyCall));
        verify(ringingCall).reject(anyBoolean(), any(), any());
    }

    /**
     * Verifies that an anomaly report is triggered when a stuck/zombie call is found and force
     * disconnected when making room for an outgoing call.
     */
    @SmallTest
    @Test
    public void testAnomalyReportedWhenMakeRoomForOutgoingCallConnecting() {
        mCallsManager.setAnomalyReporterAdapter(mAnomalyReporterAdapter);
        Call ongoingCall = addSpyCall(SIM_2_HANDLE, CallState.CONNECTING);

        Call newCall = createCall(SIM_1_HANDLE, CallState.NEW);
        when(mComponentContextFixture.getTelephonyManager().isEmergencyNumber(any()))
                .thenReturn(false);
        newCall.setHandle(TEST_ADDRESS, TelecomManager.PRESENTATION_ALLOWED);

        // Make sure enough time has passed that we'd drop the connecting call.
        when(mClockProxy.elapsedRealtime()).thenReturn(STATE_TIMEOUT + 10L);
        assertTrue(mCallsManager.makeRoomForOutgoingCall(newCall));
        verify(mAnomalyReporterAdapter).reportAnomaly(
                CallsManager.LIVE_CALL_STUCK_CONNECTING_ERROR_UUID,
                CallsManager.LIVE_CALL_STUCK_CONNECTING_ERROR_MSG);
        verify(ongoingCall).disconnect(anyLong(), anyString());
    }

    /**
     * Verifies that we won't auto-disconnect an outgoing CONNECTING call unless it has timed out.
     */
    @SmallTest
    @Test
    public void testDontDisconnectConnectingCallWhenNotTimedOut() {
        mCallsManager.setAnomalyReporterAdapter(mAnomalyReporterAdapter);
        Call ongoingCall = addSpyCall(SIM_2_HANDLE, CallState.CONNECTING);

        Call newCall = createCall(SIM_1_HANDLE, CallState.NEW);
        when(mComponentContextFixture.getTelephonyManager().isEmergencyNumber(any()))
                .thenReturn(false);
        newCall.setHandle(TEST_ADDRESS, TelecomManager.PRESENTATION_ALLOWED);

        // Make sure it has been a short time so we don't try to disconnect the call
        when(mClockProxy.elapsedRealtime()).thenReturn(STATE_TIMEOUT / 2);
        assertFalse(mCallsManager.makeRoomForOutgoingCall(newCall));
        verify(ongoingCall, never()).disconnect(anyLong(), anyString());
    }

    @SmallTest
    @Test
    public void testMakeRoomForEmergencyCallHasOutgoingCall() {
        Call outgoingCall = addSpyCall(SIM_1_HANDLE, CallState.CONNECTING);
        when(outgoingCall.isEmergencyCall()).thenReturn(false);

        Call newEmergencyCall = createSpyCall(SIM_1_HANDLE, CallState.NEW);
        when(newEmergencyCall.isEmergencyCall()).thenReturn(true);

        assertTrue(mCallsManager.makeRoomForOutgoingEmergencyCall(newEmergencyCall));
        verify(outgoingCall).disconnect(anyString());
    }

    @SmallTest
    @Test
    public void testMakeRoomForEmergencyCallHasOutgoingEmergencyCall() {
        Call outgoingCall = addSpyCall(SIM_1_HANDLE, CallState.CONNECTING);
        when(outgoingCall.isEmergencyCall()).thenReturn(true);

        Call newEmergencyCall = createSpyCall(SIM_1_HANDLE, CallState.NEW);
        when(newEmergencyCall.isEmergencyCall()).thenReturn(true);

        assertFalse(mCallsManager.makeRoomForOutgoingEmergencyCall(newEmergencyCall));
        verify(outgoingCall, never()).disconnect(anyString());
    }

    @SmallTest
    @Test
    public void testMakeRoomForEmergencyCallHasUnholdableCallAndManagedCallInHold() {
        Call unholdableCall = addSpyCall(SIM_1_HANDLE, CallState.ACTIVE);
        when(unholdableCall.can(Connection.CAPABILITY_HOLD)).thenReturn(false);

        Call managedHoldingCall = addSpyCall(SIM_1_HANDLE, CallState.ON_HOLD);
        when(managedHoldingCall.isSelfManaged()).thenReturn(false);

        Call newEmergencyCall = createSpyCall(SIM_1_HANDLE, CallState.NEW);
        when(newEmergencyCall.isEmergencyCall()).thenReturn(true);

        assertTrue(mCallsManager.makeRoomForOutgoingEmergencyCall(newEmergencyCall));
        verify(unholdableCall).disconnect(anyString());
    }

    @SmallTest
    @Test
    public void testMakeRoomForEmergencyCallHasHoldableCall() {
        Call holdableCall = addSpyCall(null, CallState.ACTIVE);
        when(holdableCall.can(Connection.CAPABILITY_HOLD)).thenReturn(true);

        Call newEmergencyCall = createSpyCall(SIM_1_HANDLE, CallState.NEW);
        when(newEmergencyCall.isEmergencyCall()).thenReturn(true);

        assertTrue(mCallsManager.makeRoomForOutgoingEmergencyCall(newEmergencyCall));
        verify(holdableCall).hold(anyString());
    }

    @SmallTest
    @Test
    public void testMakeRoomForEmergencyCallHasUnholdableCall() {
        Call unholdableCall = addSpyCall(null, CallState.ACTIVE);
        when(unholdableCall.can(Connection.CAPABILITY_HOLD)).thenReturn(false);

        Call newEmergencyCall = createSpyCall(SIM_1_HANDLE, CallState.NEW);
        when(newEmergencyCall.isEmergencyCall()).thenReturn(true);

        assertFalse(mCallsManager.makeRoomForOutgoingEmergencyCall(newEmergencyCall));
    }

    @SmallTest
    @Test
    public void testMakeRoomForOutgoingCallHasConnectingCall() {
        Call ongoingCall = addSpyCall(SIM_2_HANDLE, CallState.CONNECTING);
        Call newCall = createCall(SIM_1_HANDLE, CallState.NEW);

        when(mClockProxy.elapsedRealtime()).thenReturn(STATE_TIMEOUT + 10L);
        assertTrue(mCallsManager.makeRoomForOutgoingCall(newCall));
        verify(ongoingCall).disconnect(anyLong(), anyString());
    }

    @SmallTest
    @Test
    public void testMakeRoomForOutgoingCallForSameCall() {
        addSpyCall(SIM_2_HANDLE, CallState.CONNECTING);
        Call ongoingCall2 = addSpyCall();
        when(mClockProxy.elapsedRealtime()).thenReturn(STATE_TIMEOUT + 10L);
        assertTrue(mCallsManager.makeRoomForOutgoingCall(ongoingCall2));
    }

    /**
     * Test where a VoIP app adds another new call and has one active already; ensure we hold the
     * active call.  This assumes same connection service in the same app.
     */
    @SmallTest
    @Test
    public void testMakeRoomForOutgoingCallForSameVoipApp() {
        Call activeCall = addSpyCall(SELF_MANAGED_HANDLE, null /* connMgr */,
                CallState.ACTIVE, Connection.CAPABILITY_HOLD | Connection.CAPABILITY_SUPPORT_HOLD,
                0 /* properties */);
        Call newDialingCall = createCall(SELF_MANAGED_HANDLE, CallState.DIALING);
        newDialingCall.setConnectionProperties(Connection.CAPABILITY_HOLD
                        | Connection.CAPABILITY_SUPPORT_HOLD);
        assertTrue(mCallsManager.makeRoomForOutgoingCall(newDialingCall));
        verify(activeCall).hold(anyString());
    }

    /**
     * Test where a VoIP app adds another new call and has one active already; ensure we hold the
     * active call.  This assumes different connection services in the same app.
     */
    @SmallTest
    @Test
    public void testMakeRoomForOutgoingCallForSameVoipAppDifferentConnectionService() {
        Call activeCall = addSpyCall(SELF_MANAGED_HANDLE, null /* connMgr */,
                CallState.ACTIVE, Connection.CAPABILITY_HOLD | Connection.CAPABILITY_SUPPORT_HOLD,
                0 /* properties */);
        Call newDialingCall = createCall(SELF_MANAGED_2_HANDLE, CallState.DIALING);
        newDialingCall.setConnectionProperties(Connection.CAPABILITY_HOLD
                | Connection.CAPABILITY_SUPPORT_HOLD);
        assertTrue(mCallsManager.makeRoomForOutgoingCall(newDialingCall));
        verify(activeCall).hold(anyString());
    }

    /**
     * Test where a VoIP app adds another new call and has one active already; ensure we hold the
     * active call.  This assumes different connection services in the same app.
     */
    @SmallTest
    @Test
    public void testMakeRoomForOutgoingCallForSameNonVoipApp() {
        Call activeCall = addSpyCall(SIM_1_HANDLE, null /* connMgr */,
                CallState.ACTIVE, Connection.CAPABILITY_HOLD | Connection.CAPABILITY_SUPPORT_HOLD,
                0 /* properties */);
        Call newDialingCall = createCall(SIM_1_HANDLE, CallState.DIALING);
        newDialingCall.setConnectionProperties(Connection.CAPABILITY_HOLD
                | Connection.CAPABILITY_SUPPORT_HOLD);
        assertTrue(mCallsManager.makeRoomForOutgoingCall(newDialingCall));
        verify(activeCall, never()).hold(anyString());
    }

    @SmallTest
    @Test
    public void testMakeRoomForOutgoingCallHasOutgoingCallSelectingAccount() {
        Call outgoingCall = addSpyCall(SIM_1_HANDLE, CallState.SELECT_PHONE_ACCOUNT);
        Call newCall = createSpyCall(SIM_1_HANDLE, CallState.NEW);

        assertTrue(mCallsManager.makeRoomForOutgoingCall(newCall));
        verify(outgoingCall).disconnect(anyString());
    }

    @SmallTest
    @Test
    public void testMakeRoomForOutgoingCallHasDialingCall() {
        addSpyCall(SIM_1_HANDLE, CallState.DIALING);
        Call newCall = createSpyCall(SIM_1_HANDLE, CallState.NEW);

        assertFalse(mCallsManager.makeRoomForOutgoingCall(newCall));
    }

    @MediumTest
    @Test
    public void testMakeRoomForOutgoingCallHasHoldableCall() {
        Call holdableCall = addSpyCall(SIM_1_HANDLE, CallState.ACTIVE);
        when(holdableCall.can(Connection.CAPABILITY_HOLD)).thenReturn(true);

        Call newCall = createSpyCall(CONNECTION_MGR_1_HANDLE, CallState.NEW);

        assertTrue(mCallsManager.makeRoomForOutgoingCall(newCall));
        verify(holdableCall).hold(anyString());
    }

    @SmallTest
    @Test
    public void testMakeRoomForOutgoingCallHasUnholdableCall() {
        Call holdableCall = addSpyCall(SIM_1_HANDLE, CallState.ACTIVE);
        when(holdableCall.can(Connection.CAPABILITY_HOLD)).thenReturn(false);

        Call newCall = createSpyCall(CONNECTION_MGR_1_HANDLE, CallState.NEW);

        assertFalse(mCallsManager.makeRoomForOutgoingCall(newCall));
    }

    /**
     * Verifies that changes to a {@link PhoneAccount}'s
     * {@link PhoneAccount#CAPABILITY_VIDEO_CALLING} capability will be reflected on a call.
     * @throws Exception
     */
    @SmallTest
    @Test
    public void testPhoneAccountVideoAvailability() throws InterruptedException {
        Call ongoingCall = addSpyCall(); // adds to SIM_2_ACCT
        LinkedBlockingQueue<Integer> capabilitiesQueue = new LinkedBlockingQueue<>(1);
        ongoingCall.addListener(new Call.ListenerBase() {
            @Override
            public void onConnectionCapabilitiesChanged(Call call) {
                try {
                    capabilitiesQueue.put(call.getConnectionCapabilities());
                } catch (InterruptedException e) {
                    fail();
                }
            }
        });

        // Lets make the phone account video capable.
        PhoneAccount videoCapableAccount = new PhoneAccount.Builder(SIM_2_ACCOUNT)
                .setCapabilities(SIM_2_ACCOUNT.getCapabilities()
                        | PhoneAccount.CAPABILITY_VIDEO_CALLING)
                .build();
        mCallsManager.getPhoneAccountListener().onPhoneAccountChanged(mPhoneAccountRegistrar,
                videoCapableAccount);
        // Absorb first update; it'll be from when phone account changed initially (since we force
        // a capabilities update.
        int newCapabilities = capabilitiesQueue.poll(TEST_TIMEOUT, TimeUnit.MILLISECONDS);

        // Lets pretend the ConnectionService made it video capable as well.
        ongoingCall.setConnectionCapabilities(
                Connection.CAPABILITY_SUPPORTS_VT_LOCAL_BIDIRECTIONAL);
        newCapabilities = capabilitiesQueue.poll(TEST_TIMEOUT, TimeUnit.MILLISECONDS);
        assertTrue((newCapabilities & Connection.CAPABILITY_SUPPORTS_VT_LOCAL_BIDIRECTIONAL)
                == Connection.CAPABILITY_SUPPORTS_VT_LOCAL_BIDIRECTIONAL);
        assertTrue(ongoingCall.isVideoCallingSupportedByPhoneAccount());
    }

    /**
     * Verifies that adding and removing a call triggers external calls to have capabilities
     * recalculated.
     */
    @SmallTest
    @Test
    public void testExternalCallCapabilitiesUpdated() throws InterruptedException {
        Call externalCall = addSpyCall(SIM_2_HANDLE, null, CallState.ACTIVE,
                Connection.CAPABILITY_CAN_PULL_CALL, Connection.PROPERTY_IS_EXTERNAL_CALL);
        LinkedBlockingQueue<Integer> capabilitiesQueue = new LinkedBlockingQueue<>(1);
        externalCall.addListener(new Call.ListenerBase() {
            @Override
            public void onConnectionCapabilitiesChanged(Call call) {
                try {
                    capabilitiesQueue.put(call.getConnectionCapabilities());
                } catch (InterruptedException e) {
                    fail();
                }
            }
        });

        Call call = createSpyCall(SIM_2_HANDLE, CallState.DIALING);
        doReturn(true).when(call).isEmergencyCall();
        mCallsManager.addCall(call);
        Integer result = capabilitiesQueue.poll(TEST_TIMEOUT, TimeUnit.MILLISECONDS);
        assertNotNull(result);
        assertEquals(0, Connection.CAPABILITY_CAN_PULL_CALL & result);

        mCallsManager.removeCall(call);
        result = capabilitiesQueue.poll(TEST_TIMEOUT, TimeUnit.MILLISECONDS);
        assertNotNull(result);
        assertEquals(Connection.CAPABILITY_CAN_PULL_CALL,
                Connection.CAPABILITY_CAN_PULL_CALL & result);
    }

    /**
     * Verifies that speakers is disabled when there's no video capabilities, even if a video call
     * tried to place.
     * @throws Exception
     */
    @SmallTest
    @Test
    public void testSpeakerDisabledWhenNoVideoCapabilities() throws Exception {
        Call outgoingCall = addSpyCall(CallState.NEW);
        when(mPhoneAccountRegistrar.getPhoneAccount(
                any(PhoneAccountHandle.class), any(UserHandle.class))).thenReturn(SIM_1_ACCOUNT);
        mCallsManager.placeOutgoingCall(outgoingCall, TEST_ADDRESS, null, true,
                VideoProfile.STATE_TX_ENABLED);
        assertFalse(outgoingCall.getStartWithSpeakerphoneOn());
    }

    /**
     * Verify that a parent call will inherit the connect time of its children.
     * @throws Exception
     */
    @SmallTest
    @Test
    public void testParentInheritsChildConnectTime() throws Exception {
        Call callSim1 = createCall(SIM_1_HANDLE, null, CallState.ACTIVE);
        Call callSim2 = createCall(SIM_1_HANDLE, null, CallState.ACTIVE);
        callSim1.setConnectTimeMillis(100);

        // Pretend it is a conference made later.
        callSim2.setConnectTimeMillis(0);

        // Make the first call a child of the second (pretend conference).
        callSim1.setChildOf(callSim2);

        assertEquals(100, callSim2.getConnectTimeMillis());

        // Add another later call.
        Call callSim3 = createCall(SIM_1_HANDLE, null, CallState.ACTIVE);
        callSim3.setConnectTimeMillis(200);
        callSim3.setChildOf(callSim2);

        // Later call shouldn't impact parent.
        assertEquals(100, callSim2.getConnectTimeMillis());
    }

    /**
     * Make sure that CallsManager handles a screening result that has both
     * silence and screen-further set to true as a request to screen further.
     * @throws Exception
     */
    @SmallTest
    @Test
    public void testHandleSilenceVsBackgroundScreeningOrdering() throws Exception {
        Call screenedCall = mock(Call.class);
        Bundle extra = new Bundle();
        when(screenedCall.getIntentExtras()).thenReturn(extra);
        when(screenedCall.getTargetPhoneAccount()).thenReturn(SIM_1_HANDLE);
        String appName = "blah";
        CallFilteringResult result = new CallFilteringResult.Builder()
                .setShouldAllowCall(true)
                .setShouldReject(false)
                .setShouldSilence(true)
                .setShouldScreenViaAudio(true)
                .setShouldAddToCallLog(true)
                .setShouldShowNotification(true)
                .setCallScreeningAppName(appName)
                .build();
        mCallsManager.onCallFilteringComplete(screenedCall, result, false);

        verify(mConnectionSvrFocusMgr).requestFocus(eq(screenedCall),
                nullable(ConnectionServiceFocusManager.RequestFocusCallback.class));
        verify(screenedCall).setAudioProcessingRequestingApp(appName);
    }

    /**
     * Verify the behavior of the {@link CallsManager#areFromSameSource(Call, Call)} method.
     * @throws Exception
     */
    @SmallTest
    @Test
    public void testAreFromSameSource() throws Exception {
        Call callSim1 = createCall(SIM_1_HANDLE, null, CallState.ACTIVE);
        Call callSim2 = createCall(SIM_2_HANDLE, null, CallState.ACTIVE);
        Call callVoip1 = createCall(VOIP_1_HANDLE, null, CallState.ACTIVE);
        assertTrue(CallsManager.areFromSameSource(callSim1, callSim1));
        assertTrue(CallsManager.areFromSameSource(callSim1, callSim2));
        assertFalse(CallsManager.areFromSameSource(callSim1, callVoip1));
        assertFalse(CallsManager.areFromSameSource(callSim2, callVoip1));

        Call callSim1ConnectionMgr1 = createCall(SIM_1_HANDLE, CONNECTION_MGR_1_HANDLE,
                CallState.ACTIVE);
        Call callSim2ConnectionMgr2 = createCall(SIM_2_HANDLE, CONNECTION_MGR_2_HANDLE,
                CallState.ACTIVE);
        assertFalse(CallsManager.areFromSameSource(callSim1ConnectionMgr1, callVoip1));
        assertFalse(CallsManager.areFromSameSource(callSim2ConnectionMgr2, callVoip1));
        // Even though the connection manager differs, the underlying telephony CS is the same
        // so hold/swap will still work as expected.
        assertTrue(CallsManager.areFromSameSource(callSim1ConnectionMgr1, callSim2ConnectionMgr2));

        // Sometimes connection managers have been known to also have calls
        Call callConnectionMgr = createCall(CONNECTION_MGR_2_HANDLE, CONNECTION_MGR_2_HANDLE,
                CallState.ACTIVE);
        assertTrue(CallsManager.areFromSameSource(callSim2ConnectionMgr2, callConnectionMgr));
    }

    /**
     * This test verifies a race condition seen with the new outgoing call broadcast.
     * The scenario occurs when an incoming call is handled by an app which receives the
     * NewOutgoingCallBroadcast.  That app cancels the call by modifying the new outgoing call
     * broadcast.  Meanwhile, it places that same call again, expecting that Telecom will reuse the
     * same same.  HOWEVER, if the system delays passing of the new outgoing call broadcast back to
     * Telecom, the app will have placed a new outgoing call BEFORE telecom is aware that the call
     * was cancelled.
     * The consequence of this is that in CallsManager#startOutgoingCall, when we first get the
     * call to reuse, it will come back empty.  Meanwhile, by the time we get into the various
     * completable futures, the call WILL be in the list of calls which can be reused.  Since the
     * reusable call was not found earlier on, we end up aborting the new outgoing call.
     * @throws Exception
     */
    @SmallTest
    @Test
    public void testReuseCallConcurrency() throws Exception {
        // Ensure contact info lookup succeeds
        doAnswer(invocation -> {
            Uri handle = invocation.getArgument(0);
            CallerInfo info = new CallerInfo();
            CompletableFuture<Pair<Uri, CallerInfo>> callerInfoFuture = new CompletableFuture<>();
            callerInfoFuture.complete(new Pair<>(handle, info));
            return callerInfoFuture;
        }).when(mCallerInfoLookupHelper).startLookup(any(Uri.class));

        // Ensure we have candidate phone account handle info.
        when(mPhoneAccountRegistrar.getOutgoingPhoneAccountForScheme(any(), any())).thenReturn(
                SIM_1_HANDLE);
        when(mPhoneAccountRegistrar.getCallCapablePhoneAccounts(any(), anyBoolean(),
                any(), anyInt(), anyInt(), anyBoolean())).thenReturn(
                new ArrayList<>(Arrays.asList(SIM_1_HANDLE, SIM_2_HANDLE)));

        // Let's add an existing call which is in connecting state; this emulates the case where
        // we have an outgoing call which we have not yet disconnected as a result of the new
        // outgoing call broadcast cancelling the call.
        Call outgoingCall = addSpyCall(CallState.CONNECTING);

        final CountDownLatch latch = new CountDownLatch(1);
        // Get the handler for the main looper, which is the same one the CallsManager will use.
        // We'll post a little something to block up the handler for now.  This prevents
        // startOutgoingCall from process it's completablefutures.
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> {
            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        // Now while the main handler is blocked up we'll start another outgoing call.
        CompletableFuture<Call> callFuture = mCallsManager.startOutgoingCall(
                outgoingCall.getHandle(), outgoingCall.getTargetPhoneAccount(), new Bundle(),
                UserHandle.CURRENT, new Intent(), "com.test.stuff");

        // And we'll add the initial outgoing call to the list of pending disconnects; this
        // emulates a scenario where the pending disconnect call came in AFTER this call began.
        mCallsManager.addToPendingCallsToDisconnect(outgoingCall);

        // And we'll unblock the handler; this will let all the startOutgoingCall futures to happen.
        latch.countDown();

        // Wait for the future to become the present.
        callFuture.join();

        // We should have gotten a call out of this; if we did not then it means the call was
        // aborted.
        assertNotNull(callFuture.get());

        // And the original call should be disconnected now.
        assertEquals(CallState.DISCONNECTED, outgoingCall.getState());
    }

    /**
     * Ensures that if we have two calls hosted by the same connection manager, but with
     * different target phone accounts, we can swap between them.
     * @throws Exception
     */
    @SmallTest
    @Test
    public void testSwapCallsWithSameConnectionMgr() throws Exception {
        // GIVEN a CallsManager with ongoing call, and this call can not be held
        Call ongoingCall = addSpyCall(SIM_1_HANDLE, CONNECTION_MGR_1_HANDLE, CallState.ACTIVE);
        doReturn(false).when(ongoingCall).can(Connection.CAPABILITY_HOLD);
        doReturn(true).when(ongoingCall).can(Connection.CAPABILITY_SUPPORT_HOLD);
        when(mConnectionSvrFocusMgr.getCurrentFocusCall()).thenReturn(ongoingCall);

        // and a held call which has the same connection manager, but a different target phone
        // account.  We have seen cases where a connection mgr adds its own calls and these can
        // be problematic for swapping.
        Call heldCall = addSpyCall(CONNECTION_MGR_1_HANDLE, CONNECTION_MGR_1_HANDLE,
                CallState.ON_HOLD);

        // WHEN unhold the held call
        mCallsManager.unholdCall(heldCall);

        // THEN the ongoing call is held
        verify(ongoingCall).hold(any());
        verifyFocusRequestAndExecuteCallback(heldCall);

        // and held call is unhold now
        verify(heldCall).unhold(any());
    }

    /**
     * Verifies we inform the InCallService on local disconnect.
     * @throws Exception
     */
    @SmallTest
    @Test
    public void testRequestDisconnect() throws Exception {
        CallsManager.CallsManagerListener listener = mock(CallsManager.CallsManagerListener.class);
        mCallsManager.addListener(listener);

        Call ongoingCall = addSpyCall(CallState.ACTIVE);
        mCallsManager.addCall(ongoingCall);

        mCallsManager.disconnectCall(ongoingCall);
        // Seems odd, but ultimately the call state is still active even though it is locally
        // disconnecting.
        verify(listener).onCallStateChanged(eq(ongoingCall), eq(CallState.ACTIVE),
                eq(CallState.ACTIVE));
    }

    /**
     * Verifies where a call diagnostic service is NOT in use that we don't try to relay to the
     * CallDiagnosticService and that we get a synchronous disconnect.
     * @throws Exception
     */
    @MediumTest
    @Test
    public void testDisconnectCallSynchronous() throws Exception {
        Call callSpy = addSpyCall();
        callSpy.setIsSimCall(true);
        when(mCallDiagnosticServiceController.isConnected()).thenReturn(false);
        mCallsManager.markCallAsDisconnected(callSpy, new DisconnectCause(DisconnectCause.ERROR));

        verify(mCallDiagnosticServiceController, never()).onCallDisconnected(any(Call.class),
                any(DisconnectCause.class));
        verify(callSpy).setDisconnectCause(any(DisconnectCause.class));
    }

    @MediumTest
    @Test
    public void testDisconnectCallAsynchronous() throws Exception {
        Call callSpy = addSpyCall();
        callSpy.setIsSimCall(true);
        when(mCallDiagnosticServiceController.isConnected()).thenReturn(true);
        when(mCallDiagnosticServiceController.onCallDisconnected(any(Call.class),
                any(DisconnectCause.class))).thenReturn(true);
        mCallsManager.markCallAsDisconnected(callSpy, new DisconnectCause(DisconnectCause.ERROR));

        verify(mCallDiagnosticServiceController).onCallDisconnected(any(Call.class),
                any(DisconnectCause.class));
        verify(callSpy, never()).setDisconnectCause(any(DisconnectCause.class));
    }

    @SmallTest
    @Test
    public void testCallStreamingStateChanged() throws Exception {
        Call call = createCall(SIM_1_HANDLE, CallState.NEW);
        call.setIsTransactionalCall(true);
        CountDownLatch streamingStarted = new CountDownLatch(1);
        CountDownLatch streamingStopped = new CountDownLatch(1);
        Call.Listener l = new Call.ListenerBase() {
            @Override
            public void onCallStreamingStateChanged(Call call, boolean isStreaming) {
                if (isStreaming) {
                    streamingStarted.countDown();
                } else {
                    streamingStopped.countDown();
                }
            }
        };
        call.addListener(l);

        // Start call streaming
        call.startStreaming();
        assertTrue(streamingStarted.await(TEST_TIMEOUT, TimeUnit.MILLISECONDS));

        // Stop call streaming
        call.stopStreaming();
        assertTrue(streamingStopped.await(TEST_TIMEOUT, TimeUnit.MILLISECONDS));
    }

    /**
     * Verifies that if call state goes from DIALING to DISCONNECTED, and a call diagnostic service
     * IS in use, it would call onCallDisconnected of the CallDiagnosticService
     * @throws Exception
     */
    @MediumTest
    @Test
    public void testDisconnectDialingCall() throws Exception {
        Call callSpy = addSpyCall(CallState.DIALING);
        callSpy.setIsSimCall(true);
        when(mCallDiagnosticServiceController.isConnected()).thenReturn(true);
        when(mCallDiagnosticServiceController.onCallDisconnected(any(Call.class),
                any(DisconnectCause.class))).thenReturn(true);
        mCallsManager.markCallAsDisconnected(callSpy, new DisconnectCause(DisconnectCause.ERROR));

        verify(mCallDiagnosticServiceController).onCallDisconnected(any(Call.class),
                any(DisconnectCause.class));
        verify(callSpy, never()).setDisconnectCause(any(DisconnectCause.class));
    }

    @Test
    public void testIsInSelfManagedCallOnlyManaged() {
        Call managedCall = createCall(SIM_1_HANDLE, CallState.ACTIVE);
        managedCall.setIsSelfManaged(false);
        mCallsManager.addCall(managedCall);

        // Certainly nothing from the self managed handle.
        assertFalse(mCallsManager.isInSelfManagedCall(
                SELF_MANAGED_HANDLE.getComponentName().getPackageName(),
                SELF_MANAGED_HANDLE.getUserHandle()));
        // And nothing in a random other package.
        assertFalse(mCallsManager.isInSelfManagedCall(
                "com.foo",
                SELF_MANAGED_HANDLE.getUserHandle()));
        // And this method is only checking self managed not managed.
        assertFalse(mCallsManager.isInSelfManagedCall(
                SIM_1_HANDLE.getComponentName().getPackageName(),
                SELF_MANAGED_HANDLE.getUserHandle()));
    }

    /**
     * Emulate the case where a new incoming call is created but the connection fails for a known
     * reason before being added to CallsManager. In this case, the listeners should be notified
     * properly.
     */
    @Test
    public void testIncomingCallCreatedButNotAddedNotifyListener() {
        //The call is created and a listener is added:
        Call incomingCall = createCall(SIM_2_HANDLE, null, CallState.NEW);
        CallsManager.CallsManagerListener listener = mock(CallsManager.CallsManagerListener.class);
        mCallsManager.addListener(listener);

        //The connection fails before being added to CallsManager for a known reason:
        incomingCall.handleCreateConnectionFailure(new DisconnectCause(DisconnectCause.CANCELED));

        //Ensure the listener is notified properly:
        verify(listener).onCreateConnectionFailed(incomingCall);
    }

    /**
     * Emulate the case where a new incoming call is created but the connection fails for a known
     * reason after being added to CallsManager. Since the call was added to CallsManager, the
     * listeners should not be notified via onCreateConnectionFailed().
     */
    @Test
    public void testIncomingCallCreatedAndAddedDoNotNotifyListener() {
        //The call is created and a listener is added:
        Call incomingCall = createCall(SIM_2_HANDLE, null, CallState.NEW);
        CallsManager.CallsManagerListener listener = mock(CallsManager.CallsManagerListener.class);
        mCallsManager.addListener(listener);

        //The call is added to CallsManager:
        mCallsManager.addCall(incomingCall);

        //The connection fails after being added to CallsManager for a known reason:
        incomingCall.handleCreateConnectionFailure(new DisconnectCause(DisconnectCause.CANCELED));

        //Since the call was added to CallsManager, onCreateConnectionFailed shouldn't be invoked:
        verify(listener, never()).onCreateConnectionFailed(incomingCall);
    }

    /**
     * Emulate the case where a new outgoing call is created but is aborted before being added to
     * CallsManager since there are no available phone accounts. In this case, the listeners
     * should be notified properly.
     */
    @Test
    public void testAbortOutgoingCallNoPhoneAccountsNotifyListeners() throws Exception {
        // Setup a new outgoing call and add a listener
        Call newCall = addSpyCall(CallState.NEW);
        CallsManager.CallsManagerListener listener = mock(CallsManager.CallsManagerListener.class);
        mCallsManager.addListener(listener);

        // Ensure contact info lookup succeeds but do not set the phone account info
        doAnswer(invocation -> {
            Uri handle = invocation.getArgument(0);
            CallerInfo info = new CallerInfo();
            CompletableFuture<Pair<Uri, CallerInfo>> callerInfoFuture = new CompletableFuture<>();
            callerInfoFuture.complete(new Pair<>(handle, info));
            return callerInfoFuture;
        }).when(mCallerInfoLookupHelper).startLookup(any(Uri.class));

        // Start the outgoing call
        CompletableFuture<Call> callFuture = mCallsManager.startOutgoingCall(
                newCall.getHandle(), newCall.getTargetPhoneAccount(), new Bundle(),
                UserHandle.CURRENT, new Intent(), "com.test.stuff");
        Call result = callFuture.get(TEST_TIMEOUT, TimeUnit.MILLISECONDS);

        //Ensure the listener is notified properly:
        verify(listener).onCreateConnectionFailed(any());
        assertNull(result);
    }

    @Test
    public void testIsInSelfManagedCallOnlySelfManaged() {
        Call selfManagedCall = createCall(SELF_MANAGED_HANDLE, CallState.ACTIVE);
        selfManagedCall.setIsSelfManaged(true);
        mCallsManager.addCall(selfManagedCall);

        assertTrue(mCallsManager.isInSelfManagedCall(
                SELF_MANAGED_HANDLE.getComponentName().getPackageName(),
                SELF_MANAGED_HANDLE.getUserHandle()));
        assertFalse(mCallsManager.isInSelfManagedCall(
                "com.foo",
                SELF_MANAGED_HANDLE.getUserHandle()));
        assertFalse(mCallsManager.isInSelfManagedCall(
                SIM_1_HANDLE.getComponentName().getPackageName(),
                SELF_MANAGED_HANDLE.getUserHandle()));

        Call managedCall = createCall(SIM_1_HANDLE, CallState.ACTIVE);
        managedCall.setIsSelfManaged(false);
        mCallsManager.addCall(managedCall);

        // Still not including managed
        assertFalse(mCallsManager.isInSelfManagedCall(
                SIM_1_HANDLE.getComponentName().getPackageName(),
                SELF_MANAGED_HANDLE.getUserHandle()));

        // Also shouldn't be something in another user's version of the same package.
        assertFalse(mCallsManager.isInSelfManagedCall(
                SELF_MANAGED_HANDLE.getComponentName().getPackageName(),
                new UserHandle(90210)));
    }

    /**
     * Verifies that if a {@link android.telecom.CallScreeningService} app can properly request
     * notification show for rejected calls.
     */
    @SmallTest
    @Test
    public void testCallScreeningServiceRequestShowNotification() {
        Call callSpy = addSpyCall(CallState.NEW);
        CallFilteringResult result = new CallFilteringResult.Builder()
                .setShouldAllowCall(false)
                .setShouldReject(true)
                .setCallScreeningComponentName("com.foo/.Blah")
                .setCallScreeningAppName("Blah")
                .setShouldAddToCallLog(true)
                .setShouldShowNotification(true).build();

        mCallsManager.onCallFilteringComplete(callSpy, result, false /* timeout */);
        verify(mMissedCallNotifier).showMissedCallNotification(
                any(MissedCallNotifier.CallInfo.class), /* uri= */ eq(null));
    }

    @Test
    public void testSetStateOnlyCalledOnce() {
        // GIVEN a new self-managed call
        Call newCall = addSpyCall();
        doReturn(true).when(newCall).isSelfManaged();
        newCall.setState(CallState.DISCONNECTED, "");

        // WHEN ActionSetCallState is given a disconnect call
        assertEquals(CallState.DISCONNECTED, newCall.getState());
        // attempt to set the call active
        mCallsManager.createActionSetCallStateAndPerformAction(newCall, CallState.ACTIVE, "");

        // THEN assert remains disconnected
        assertEquals(CallState.DISCONNECTED, newCall.getState());
    }

    @SmallTest
    @Test
    public void testCrossUserCallRedirectionEndEarlyForIncapablePhoneAccount() {
        when(mPhoneAccountRegistrar.getPhoneAccountUnchecked(eq(SIM_1_HANDLE_SECONDARY)))
                .thenReturn(SIM_1_ACCOUNT);
        mCallsManager.onUserSwitch(UserHandle.SYSTEM);

        Call callSpy = addSpyCall(CallState.NEW);
        mCallsManager.onCallRedirectionComplete(callSpy, TEST_ADDRESS, SIM_1_HANDLE_SECONDARY,
                new GatewayInfo("foo", TEST_ADDRESS2, TEST_ADDRESS), true /* speakerphoneOn */,
                VideoProfile.STATE_AUDIO_ONLY, false /* shouldCancelCall */, "" /* uiAction */);

        ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(callSpy).disconnect(argumentCaptor.capture());
        assertTrue(argumentCaptor.getValue().contains("Unavailable phoneAccountHandle"));
    }

    /**
     * Verifies that target phone account is set in startOutgoingCall. The multi-user functionality
     * is dependent on the call's phone account handle being present so this test ensures that
     * existing outgoing call flow does not break from future updates.
     * @throws Exception
     */
    @Test
    public void testStartOutgoingCall_TargetPhoneAccountSet() throws Exception {
        // Ensure contact info lookup succeeds
        doAnswer(invocation -> {
            Uri handle = invocation.getArgument(0);
            CallerInfo info = new CallerInfo();
            CompletableFuture<Pair<Uri, CallerInfo>> callerInfoFuture = new CompletableFuture<>();
            callerInfoFuture.complete(new Pair<>(handle, info));
            return callerInfoFuture;
        }).when(mCallerInfoLookupHelper).startLookup(any(Uri.class));

        // Ensure we have candidate phone account handle info.
        when(mPhoneAccountRegistrar.getOutgoingPhoneAccountForScheme(any(), any())).thenReturn(
                SIM_1_HANDLE);
        when(mPhoneAccountRegistrar.getCallCapablePhoneAccounts(any(), anyBoolean(),
                any(), anyInt(), anyInt(), anyBoolean())).thenReturn(
                new ArrayList<>(Arrays.asList(SIM_1_HANDLE, SIM_2_HANDLE)));

        // start an outgoing call
        CompletableFuture<Call> callFuture = mCallsManager.startOutgoingCall(
                TEST_ADDRESS, SIM_2_HANDLE, new Bundle(),
                UserHandle.CURRENT, new Intent(), "com.test.stuff");
        Call outgoingCall = callFuture.get();
        // assert call was created
        assertNotNull(outgoingCall);
        // assert target phone account was set
        assertNotNull(outgoingCall.getTargetPhoneAccount());
    }

    /**
     * Verifies that target phone account is set before call filtering occurs.
     * @throws Exception
     */
    @SmallTest
    @Test
    public void testOnSuccessfulIncomingCall_TargetPhoneAccountSet() throws Exception {
        Call incomingCall = addSpyCall(CallState.NEW);
        doReturn(false).when(incomingCall).can(Connection.CAPABILITY_HOLD);
        doReturn(false).when(incomingCall).can(Connection.CAPABILITY_SUPPORT_HOLD);
        doReturn(true).when(incomingCall).isSelfManaged();
        doReturn(true).when(incomingCall).setState(anyInt(), any());
        // assert phone account is present before onSuccessfulIncomingCall is called
        assertNotNull(incomingCall.getTargetPhoneAccount());
    }

    /**
     * Verifies that outgoing call's post call package name is set during
     * onSuccessfulOutgoingCall.
     * @throws Exception
     */
    @SmallTest
    @Test
    public void testPostCallPackageNameSetOnSuccessfulOutgoingCall() throws Exception {
        Call outgoingCall = addSpyCall(CallState.NEW);
        when(mCallsManager.getRoleManagerAdapter().getDefaultCallScreeningApp(
                outgoingCall.getAssociatedUser()))
                .thenReturn(DEFAULT_CALL_SCREENING_APP);
        assertNull(outgoingCall.getPostCallPackageName());
        mCallsManager.onSuccessfulOutgoingCall(outgoingCall, CallState.CONNECTING);
        assertEquals(DEFAULT_CALL_SCREENING_APP, outgoingCall.getPostCallPackageName());
    }

    /**
     * Verify the only call state set from calling onSuccessfulOutgoingCall is CallState.DIALING.
     */
    @SmallTest
    @Test
    public void testOutgoingCallStateIsSetToAPreviousStateAndIgnored() {
        when(mFeatureFlags.fixAudioFlickerForOutgoingCalls()).thenReturn(true);
        Call outgoingCall = addSpyCall(CallState.CONNECTING);
        mCallsManager.onSuccessfulOutgoingCall(outgoingCall, CallState.NEW);
        verify(outgoingCall, never()).setState(eq(CallState.NEW), any());
        verify(outgoingCall, times(1)).setState(eq(CallState.DIALING), any());
    }

    /**
     * Verify a ConnectionService can start the call in the active state and avoid the dialing state
     */
    @SmallTest
    @Test
    public void testOutgoingCallStateCanAvoidDialingAndGoStraightToActive() {
        when(mFeatureFlags.fixAudioFlickerForOutgoingCalls()).thenReturn(true);
        Call outgoingCall = addSpyCall(CallState.CONNECTING);
        mCallsManager.onSuccessfulOutgoingCall(outgoingCall, CallState.ACTIVE);
        verify(outgoingCall, never()).setState(eq(CallState.DIALING), any());
        verify(outgoingCall, times(1)).setState(eq(CallState.ACTIVE), any());
    }

    @SmallTest
    @Test
    public void testRejectIncomingCallOnPAHInactive_SecondaryUser() throws Exception {
        ConnectionServiceWrapper service = mock(ConnectionServiceWrapper.class);
        doReturn(WORK_HANDLE.getComponentName()).when(service).getComponentName();
        mCallsManager.addConnectionServiceRepositoryCache(WORK_HANDLE.getComponentName(),
                WORK_HANDLE.getUserHandle(), service);

        when(mMockCurrentUserManager.isAdminUser()).thenReturn(false);
        when(mMockCurrentUserManager.isQuietModeEnabled(eq(WORK_HANDLE.getUserHandle())))
                .thenReturn(false);
        when(mPhoneAccountRegistrar.getPhoneAccountUnchecked(eq(WORK_HANDLE)))
                .thenReturn(WORK_ACCOUNT);
        Call newCall = mCallsManager.processIncomingCallIntent(
                WORK_HANDLE, new Bundle(), false);

        verify(service, timeout(TEST_TIMEOUT)).createConnectionFailed(any());
        assertFalse(newCall.isInECBM());
        assertEquals(USER_MISSED_NOT_RUNNING, newCall.getMissedReason());
    }

    @SmallTest
    @Test
    public void testRejectIncomingCallOnPAHInactive_ProfilePaused() throws Exception {
        ConnectionServiceWrapper service = mock(ConnectionServiceWrapper.class);
        doReturn(WORK_HANDLE.getComponentName()).when(service).getComponentName();
        mCallsManager.addConnectionServiceRepositoryCache(WORK_HANDLE.getComponentName(),
                WORK_HANDLE.getUserHandle(), service);

        when(mMockCurrentUserManager.isAdminUser()).thenReturn(true);
        when(mMockCurrentUserManager.isQuietModeEnabled(any(UserHandle.class)))
                .thenReturn(true);
        when(mPhoneAccountRegistrar.getPhoneAccountUnchecked(eq(WORK_HANDLE)))
                .thenReturn(WORK_ACCOUNT);
        Call newCall = mCallsManager.processIncomingCallIntent(
                WORK_HANDLE, new Bundle(), false);

        verify(service, timeout(TEST_TIMEOUT)).createConnectionFailed(any());
        assertFalse(newCall.isInECBM());
        assertEquals(USER_MISSED_NOT_RUNNING, newCall.getMissedReason());
    }

    @SmallTest
    @Test
    public void testAcceptIncomingCallOnPAHInactiveAndECBMActive() throws Exception {
        ConnectionServiceWrapper service = mock(ConnectionServiceWrapper.class);
        doReturn(SIM_2_HANDLE.getComponentName()).when(service).getComponentName();
        mCallsManager.addConnectionServiceRepositoryCache(SIM_2_HANDLE.getComponentName(),
                SIM_2_HANDLE.getUserHandle(), service);

        when(mEmergencyCallHelper.isLastOutgoingEmergencyCallPAH(eq(SIM_2_HANDLE)))
                .thenReturn(true);
        when(mMockCurrentUserManager.isQuietModeEnabled(eq(SIM_2_HANDLE.getUserHandle())))
                .thenReturn(true);
        Call newCall = mCallsManager.processIncomingCallIntent(
                SIM_2_HANDLE, new Bundle(), false);

        assertTrue(newCall.isInECBM());
        verify(service, timeout(TEST_TIMEOUT).times(0)).createConnectionFailed(any());
    }

    @SmallTest
    @Test
    public void testAcceptIncomingCallOnPAHInactiveAndECBMActive_SecondaryUser() throws Exception {
        ConnectionServiceWrapper service = mock(ConnectionServiceWrapper.class);
        doReturn(WORK_HANDLE.getComponentName()).when(service).getComponentName();
        mCallsManager.addConnectionServiceRepositoryCache(SIM_2_HANDLE.getComponentName(),
                WORK_HANDLE.getUserHandle(), service);

        when(mEmergencyCallHelper.isLastOutgoingEmergencyCallPAH(eq(WORK_HANDLE)))
                .thenReturn(true);
        when(mMockCurrentUserManager.isAdminUser()).thenReturn(false);
        when(mMockCurrentUserManager.isQuietModeEnabled(eq(WORK_HANDLE.getUserHandle())))
                .thenReturn(false);
        when(mPhoneAccountRegistrar.getPhoneAccountUnchecked(eq(WORK_HANDLE)))
                .thenReturn(WORK_ACCOUNT);
        Call newCall = mCallsManager.processIncomingCallIntent(
                WORK_HANDLE, new Bundle(), false);

        assertTrue(newCall.isInECBM());
        verify(service, timeout(TEST_TIMEOUT).times(0)).createConnectionFailed(any());
    }

    @SmallTest
    @Test
    public void testAcceptIncomingEmergencyCallOnPAHInactive() throws Exception {
        ConnectionServiceWrapper service = mock(ConnectionServiceWrapper.class);
        doReturn(SIM_2_HANDLE.getComponentName()).when(service).getComponentName();
        mCallsManager.addConnectionServiceRepositoryCache(SIM_2_HANDLE.getComponentName(),
                SIM_2_HANDLE.getUserHandle(), service);

        Bundle extras = new Bundle();
        extras.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, TEST_ADDRESS);
        TelephonyManager tm = mContext.getSystemService(TelephonyManager.class);
        when(mMockCurrentUserManager.isQuietModeEnabled(eq(SIM_2_HANDLE.getUserHandle())))
                .thenReturn(true);
        when(tm.isEmergencyNumber(any(String.class))).thenReturn(true);
        Call newCall = mCallsManager.processIncomingCallIntent(
                SIM_2_HANDLE, extras, false);

        assertFalse(newCall.isInECBM());
        assertTrue(newCall.isEmergencyCall());
        verify(service, timeout(TEST_TIMEOUT).times(0)).createConnectionFailed(any());
    }

    public class LatchedOutcomeReceiver implements OutcomeReceiver<Boolean,
            CallException> {
        CountDownLatch mCountDownLatch;
        Boolean mIsOnResultExpected;

        public LatchedOutcomeReceiver(CountDownLatch latch, boolean isOnResultExpected){
            mCountDownLatch = latch;
            mIsOnResultExpected = isOnResultExpected;
        }

        @Override
        public void onResult(Boolean result) {
            if(mIsOnResultExpected) {
                mCountDownLatch.countDown();
            }
        }

        @Override
        public void onError(CallException error) {
            OutcomeReceiver.super.onError(error);
            if(!mIsOnResultExpected){
                mCountDownLatch.countDown();
            }
        }
    }

    @SmallTest
    @Test
    public void testCanHold() {
        Call newCall = addSpyCall();
        when(newCall.isTransactionalCall()).thenReturn(true);
        when(newCall.can(Connection.CAPABILITY_SUPPORT_HOLD)).thenReturn(false);
        assertFalse(mCallsManager.canHold(newCall));
        when(newCall.can(Connection.CAPABILITY_SUPPORT_HOLD)).thenReturn(true);
        assertTrue(mCallsManager.canHold(newCall));
    }

    @MediumTest
    @Test
    public void testOnFailedOutgoingCallRemovesCallImmediately() {
        Call call = addSpyCall();
        when(call.isDisconnectHandledViaFuture()).thenReturn(false);
        CompletableFuture future = CompletableFuture.completedFuture(true);
        when(mInCallController.getBindingFuture()).thenReturn(future);

        mCallsManager.onFailedOutgoingCall(call, new DisconnectCause(DisconnectCause.OTHER));

        future.join();
        waitForHandlerAction(new Handler(Looper.getMainLooper()), TEST_TIMEOUT);

        assertFalse(mCallsManager.getCalls().contains(call));
    }

    @MediumTest
    @Test
    public void testHoldTransactional() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Call newCall = addSpyCall();

        // case 1: no active call, no need to put the call on hold
        when(mConnectionSvrFocusMgr.getCurrentFocusCall()).thenReturn(null);
        mCallsManager.transactionHoldPotentialActiveCallForNewCall(newCall,
                new LatchedOutcomeReceiver(latch, true));
        waitForCountDownLatch(latch);

        // case 2: active call == new call, no need to put the call on hold
        latch = new CountDownLatch(1);
        when(mConnectionSvrFocusMgr.getCurrentFocusCall()).thenReturn(newCall);
        mCallsManager.transactionHoldPotentialActiveCallForNewCall(newCall,
                new LatchedOutcomeReceiver(latch, true));
        waitForCountDownLatch(latch);

        // case 3: cannot hold current active call early check
        Call cannotHoldCall = addSpyCall(SIM_1_HANDLE, null,
                CallState.ACTIVE, 0, 0);
        latch = new CountDownLatch(1);
        when(mConnectionSvrFocusMgr.getCurrentFocusCall()).thenReturn(cannotHoldCall);
        mCallsManager.transactionHoldPotentialActiveCallForNewCall(newCall,
                new LatchedOutcomeReceiver(latch, false));
        waitForCountDownLatch(latch);

        // case 4: activeCall != newCall && canHold(activeCall)
        Call canHoldCall = addSpyCall(SIM_1_HANDLE, null,
                CallState.ACTIVE, Connection.CAPABILITY_HOLD, 0);
        latch = new CountDownLatch(1);
        when(mConnectionSvrFocusMgr.getCurrentFocusCall()).thenReturn(canHoldCall);
        mCallsManager.transactionHoldPotentialActiveCallForNewCall(newCall,
                new LatchedOutcomeReceiver(latch, true));
        waitForCountDownLatch(latch);
    }

    @SmallTest
    @Test
    public void testGetNumCallsWithState_MultiUser() throws Exception {
        when(mContext.checkCallingOrSelfPermission(Manifest.permission.INTERACT_ACROSS_USERS))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        // Add call under secondary user
        Call call = addSpyCall(SIM_1_HANDLE_SECONDARY, CallState.ACTIVE);
        when(call.getPhoneAccountFromHandle()).thenReturn(SIM_1_ACCOUNT_SECONDARY);
        // Verify that call is visible to primary user
        assertEquals(mCallsManager.getNumCallsWithState(0, null,
                UserHandle.CURRENT_OR_SELF, true,
                null, CallState.ACTIVE), 1);
        // Verify that call is not visible to primary user
        // when a different phone account handle is specified.
        assertEquals(mCallsManager.getNumCallsWithState(0, null,
                UserHandle.CURRENT_OR_SELF, true,
                SIM_1_HANDLE, CallState.ACTIVE), 0);
        // Deny INTERACT_ACROSS_USERS permission and verify that call is not visible to primary user
        assertEquals(mCallsManager.getNumCallsWithState(0, null,
                UserHandle.CURRENT_OR_SELF, false,
                null, CallState.ACTIVE), 0);
    }

    public void waitForCountDownLatch(CountDownLatch latch) throws InterruptedException {
            boolean success = latch.await(5000, TimeUnit.MILLISECONDS);
            if (!success) {
                fail("assertOnResultWasReceived success failed");
            }
    }

    /**
     * When queryCurrentLocation is called, check whether the result is received through the
     * ResultReceiver.
     * @throws Exception if {@link CompletableFuture#get()} fails.
     */
    @Test
    public void testQueryCurrentLocationCheckOnReceiveResult() throws Exception {
        ConnectionServiceWrapper service = new ConnectionServiceWrapper(
                new ComponentName(mContext.getPackageName(),
                        mContext.getPackageName().getClass().getName()), null,
                        mPhoneAccountRegistrar, mCallsManager, mContext, mLock, null,
                        mFeatureFlags);

        CompletableFuture<String> resultFuture = new CompletableFuture<>();
        try {
            service.queryCurrentLocation(500L, "Test_provider",
                    new ResultReceiver(new Handler(Looper.getMainLooper())) {
                        @Override
                        protected void onReceiveResult(int resultCode, Bundle result) {
                            super.onReceiveResult(resultCode, result);
                            resultFuture.complete("onReceiveResult");
                        }
                    });
        } catch (Exception e) {
            resultFuture.complete("Exception : " + e);
        }

        String result = resultFuture.get(1000L, TimeUnit.MILLISECONDS);
        assertTrue(result.contains("onReceiveResult"));
    }

    @Test
    public void testConnectionServiceCreateConnectionTimeout() throws Exception {
        ConnectionServiceWrapper service = new ConnectionServiceWrapper(
                SIM_1_ACCOUNT.getAccountHandle().getComponentName(), null,
                mPhoneAccountRegistrar, mCallsManager, mContext, mLock, null, mFeatureFlags);
        TestScheduledExecutorService scheduledExecutorService = new TestScheduledExecutorService();
        service.setScheduledExecutorService(scheduledExecutorService);
        Call call = addSpyCall();
        service.addCall(call);
        when(call.isCreateConnectionComplete()).thenReturn(false);
        CreateConnectionResponse response = mock(CreateConnectionResponse.class);

        service.createConnection(call, response);
        waitUntilConditionIsTrueOrTimeout(new Condition() {
            @Override
            public Object expected() {
                return true;
            }

            @Override
            public Object actual() {
                return scheduledExecutorService.isRunnableScheduledAtTime(15000L);
            }
        }, 5000L, "Expected job failed to schedule");
        scheduledExecutorService.advanceTime(15000L);
        verify(response).handleCreateConnectionFailure(
                eq(new DisconnectCause(DisconnectCause.ERROR)));
    }

    @SmallTest
    @Test
    public void testOnFailedOutgoingCallUnholdsCallAfterLocallyDisconnect() {
        Call existingCall = addSpyCall();
        when(existingCall.getState()).thenReturn(CallState.ON_HOLD);

        Call call = addSpyCall();
        when(call.isDisconnectHandledViaFuture()).thenReturn(false);
        when(call.isDisconnectingChildCall()).thenReturn(false);
        CompletableFuture future = CompletableFuture.completedFuture(true);
        when(mInCallController.getBindingFuture()).thenReturn(future);

        mCallsManager.disconnectCall(call);
        mCallsManager.onFailedOutgoingCall(call, new DisconnectCause(DisconnectCause.OTHER));

        future.join();
        waitForHandlerAction(new Handler(Looper.getMainLooper()), TEST_TIMEOUT);

        verify(existingCall).unhold();
    }

    @MediumTest
    @Test
    public void testOnFailedOutgoingCallUnholdsCallIfNoHoldButton() {
        Call existingCall = addSpyCall();
        when(existingCall.can(Connection.CAPABILITY_SUPPORT_HOLD)).thenReturn(false);
        when(existingCall.getState()).thenReturn(CallState.ON_HOLD);

        Call call = addSpyCall();
        when(call.isDisconnectHandledViaFuture()).thenReturn(false);
        CompletableFuture future = CompletableFuture.completedFuture(true);
        when(mInCallController.getBindingFuture()).thenReturn(future);

        mCallsManager.disconnectCall(call);
        mCallsManager.onFailedOutgoingCall(call, new DisconnectCause(DisconnectCause.OTHER));

        future.join();
        waitForHandlerAction(new Handler(Looper.getMainLooper()), TEST_TIMEOUT);

        verify(existingCall).unhold();
    }

    @MediumTest
    @Test
    public void testOnCallFilteringCompleteRemovesUnwantedCallComposerAttachments() {
        Call call = addSpyCall(CallState.NEW);
        Bundle extras = mock(Bundle.class);
        when(call.getIntentExtras()).thenReturn(extras);

        final int attachmentDisabledMask = ~0
                ^ CallScreeningService.CallResponse.CALL_COMPOSER_ATTACHMENT_LOCATION
                ^ CallScreeningService.CallResponse.CALL_COMPOSER_ATTACHMENT_SUBJECT
                ^ CallScreeningService.CallResponse.CALL_COMPOSER_ATTACHMENT_PRIORITY;
        CallScreeningService.ParcelableCallResponse response =
                mock(CallScreeningService.ParcelableCallResponse.class);
        when(response.getCallComposerAttachmentsToShow()).thenReturn(attachmentDisabledMask);

        CallFilteringResult result = new CallFilteringResult.Builder()
                .setCallScreeningResponse(response, true)
                .build();

        mCallsManager.onCallFilteringComplete(call, result, false);

        verify(extras).remove(TelecomManager.EXTRA_LOCATION);
        verify(extras).remove(TelecomManager.EXTRA_CALL_SUBJECT);
        verify(extras).remove(TelecomManager.EXTRA_PRIORITY);
    }

    @SmallTest
    @Test
    public void testOnFailedIncomingCall() {
        Call call = createSpyCall(SIM_1_HANDLE, CallState.NEW);

        mCallsManager.onFailedIncomingCall(call);

        assertEquals(CallState.DISCONNECTED, call.getState());
        verify(call).removeListener(mCallsManager);
    }

    @SmallTest
    @Test
    public void testOnSuccessfulUnknownCall() {
        Call call = createSpyCall(SIM_1_HANDLE, CallState.NEW);

        final int newState = CallState.ACTIVE;
        mCallsManager.onSuccessfulUnknownCall(call, newState);

        assertEquals(newState, call.getState());
        assertTrue(mCallsManager.getCalls().contains(call));
    }

    @SmallTest
    @Test
    public void testOnFailedUnknownCall() {
        Call call = createSpyCall(SIM_1_HANDLE, CallState.NEW);

        mCallsManager.onFailedUnknownCall(call);

        assertEquals(CallState.DISCONNECTED, call.getState());
        verify(call).removeListener(mCallsManager);
    }

    @SmallTest
    @Test
    public void testOnRingbackRequested() {
        Call call = mock(Call.class);
        final boolean ringback = true;

        CallsManager.CallsManagerListener listener = mock(CallsManager.CallsManagerListener.class);
        mCallsManager.addListener(listener);

        mCallsManager.onRingbackRequested(call, ringback);

        verify(listener).onRingbackRequested(call, ringback);
    }

    @MediumTest
    @Test
    public void testSetCallDialingAndDontIncreaseVolume() {
        // Start with a non zero volume.
        mComponentContextFixture.getAudioManager().setStreamVolume(AudioManager.STREAM_VOICE_CALL,
                4, 0 /* flags */);

        Call call = mock(Call.class);
        mCallsManager.markCallAsDialing(call);

        // We set the volume to non-zero above, so expect 1
        verify(mComponentContextFixture.getAudioManager(), times(1)).setStreamVolume(
                eq(AudioManager.STREAM_VOICE_CALL), anyInt(), anyInt());
    }
    @MediumTest
    @Test
    public void testSetCallDialingAndIncreaseVolume() {
        // Start with a zero volume stream.
        mComponentContextFixture.getAudioManager().setStreamVolume(AudioManager.STREAM_VOICE_CALL,
                0, 0 /* flags */);

        Call call = mock(Call.class);
        mCallsManager.markCallAsDialing(call);

        // We set the volume to zero above, so expect 2
        verify(mComponentContextFixture.getAudioManager(), times(2)).setStreamVolume(
                eq(AudioManager.STREAM_VOICE_CALL), anyInt(), anyInt());
    }

    @MediumTest
    @Test
    public void testSetCallActiveAndDontIncreaseVolume() {
        // Start with a non-zero volume.
        mComponentContextFixture.getAudioManager().setStreamVolume(AudioManager.STREAM_VOICE_CALL,
                4, 0 /* flags */);

        Call call = mock(Call.class);
        mCallsManager.markCallAsActive(call);

        // We set the volume to non-zero above, so expect 1 only.
        verify(mComponentContextFixture.getAudioManager(), times(1)).setStreamVolume(
                eq(AudioManager.STREAM_VOICE_CALL), anyInt(), anyInt());
    }

    @MediumTest
    @Test
    public void testHandoverToIsAccepted() {
        Call sourceCall = addSpyCall(CONNECTION_MGR_1_HANDLE, CallState.NEW);
        Call call = addSpyCall(CONNECTION_MGR_1_HANDLE, CallState.NEW);
        when(call.getHandoverSourceCall()).thenReturn(sourceCall);
        when(call.getHandoverState()).thenReturn(HandoverState.HANDOVER_TO_STARTED);

        mCallsManager.createActionSetCallStateAndPerformAction(call, CallState.ACTIVE, "");

        verify(call).setHandoverState(HandoverState.HANDOVER_ACCEPTED);
        verify(call).onHandoverComplete();
        verify(sourceCall).setHandoverState(HandoverState.HANDOVER_ACCEPTED);
        verify(sourceCall).onHandoverComplete();
        verify(sourceCall).disconnect();
    }

    @MediumTest
    @Test
    public void testSelfManagedHandoverToIsAccepted() {
        Call sourceCall = addSpyCall(CONNECTION_MGR_1_HANDLE, CallState.NEW);
        Call call = addSpyCall(SELF_MANAGED_HANDLE, CallState.NEW);
        when(call.getHandoverSourceCall()).thenReturn(sourceCall);
        when(call.getHandoverState()).thenReturn(HandoverState.HANDOVER_TO_STARTED);
        when(call.isSelfManaged()).thenReturn(true);
        Call otherCall = addSpyCall(CONNECTION_MGR_1_HANDLE, CallState.ON_HOLD);

        mCallsManager.createActionSetCallStateAndPerformAction(call, CallState.ACTIVE, "");

        verify(call).setHandoverState(HandoverState.HANDOVER_ACCEPTED);
        verify(call).onHandoverComplete();
        verify(sourceCall).setHandoverState(HandoverState.HANDOVER_ACCEPTED);
        verify(sourceCall).onHandoverComplete();
        verify(sourceCall, times(2)).disconnect();
        verify(otherCall).disconnect();
    }

    @SmallTest
    @Test
    public void testHandoverToIsRejected() {
        Call sourceCall = addSpyCall(CONNECTION_MGR_1_HANDLE, CallState.NEW);
        Call call = addSpyCall(CONNECTION_MGR_1_HANDLE, CallState.NEW);
        when(call.getHandoverSourceCall()).thenReturn(sourceCall);
        when(call.getHandoverState()).thenReturn(HandoverState.HANDOVER_TO_STARTED);
        when(call.getConnectionService()).thenReturn(mock(ConnectionServiceWrapper.class));

        mCallsManager.createActionSetCallStateAndPerformAction(
                call, CallState.DISCONNECTED, "");

        verify(sourceCall).onHandoverFailed(
                    android.telecom.Call.Callback.HANDOVER_FAILURE_USER_REJECTED);

        verify(call).markFinishedHandoverStateAndCleanup(HandoverState.HANDOVER_FAILED);
    }

    @SmallTest
    @Test
    public void testHandoverFromIsStarted() {
        Call destinationCall = addSpyCall(CONNECTION_MGR_1_HANDLE, CallState.NEW);
        Call call = addSpyCall(CONNECTION_MGR_1_HANDLE, CallState.NEW);
        when(call.getHandoverDestinationCall()).thenReturn(destinationCall);
        when(call.getHandoverState()).thenReturn(HandoverState.HANDOVER_FROM_STARTED);
        mCallsManager.createActionSetCallStateAndPerformAction(
                call, CallState.DISCONNECTED, "");
    }

    @SmallTest
    @Test
    public void testHandoverFromIsAccepted() {
        Call destinationCall = addSpyCall(CONNECTION_MGR_1_HANDLE, CallState.NEW);
        Call call = addSpyCall(CONNECTION_MGR_1_HANDLE, CallState.NEW);
        when(call.getHandoverDestinationCall()).thenReturn(destinationCall);
        when(call.getHandoverState()).thenReturn(HandoverState.HANDOVER_ACCEPTED);

        mCallsManager.createActionSetCallStateAndPerformAction(
                call, CallState.DISCONNECTED, "");

        verify(call).onHandoverComplete();
        verify(call).markFinishedHandoverStateAndCleanup(HandoverState.HANDOVER_COMPLETE);
        verify(destinationCall).onHandoverComplete();
    }

    @SmallTest
    @Test
    public void testSelfManagedHandoverFromIsAccepted() {
        Call destinationCall = addSpyCall(SELF_MANAGED_HANDLE, CallState.NEW);
        when(destinationCall.isSelfManaged()).thenReturn(true);
        Call call = addSpyCall(CONNECTION_MGR_1_HANDLE, CallState.NEW);
        when(call.getHandoverDestinationCall()).thenReturn(destinationCall);
        when(call.getHandoverState()).thenReturn(HandoverState.HANDOVER_ACCEPTED);
        Call otherCall = addSpyCall(CONNECTION_MGR_1_HANDLE, CallState.ON_HOLD);

        mCallsManager.createActionSetCallStateAndPerformAction(
                call, CallState.DISCONNECTED, "");

        verify(call).onHandoverComplete();
        verify(call).markFinishedHandoverStateAndCleanup(HandoverState.HANDOVER_COMPLETE);
        verify(destinationCall).onHandoverComplete();
        verify(otherCall).disconnect();
    }

    @MediumTest
    @Test
    public void testGetNumUnholdableCallsForOtherConnectionService() {
        final int notDialingState = CallState.ACTIVE;
        final PhoneAccountHandle accountHande = SIM_1_HANDLE;
        assertFalse(mCallsManager.hasUnholdableCallsForOtherConnectionService(accountHande));

        Call unholdableCall = addSpyCall(accountHande, notDialingState);
        when(unholdableCall.can(Connection.CAPABILITY_HOLD)).thenReturn(false);
        assertFalse(mCallsManager.hasUnholdableCallsForOtherConnectionService(accountHande));

        Call holdableCall = addSpyCall(accountHande, notDialingState);
        when(holdableCall.can(Connection.CAPABILITY_HOLD)).thenReturn(true);
        assertFalse(mCallsManager.hasUnholdableCallsForOtherConnectionService(accountHande));

        Call dialingCall = addSpyCall(accountHande, CallState.DIALING);
        when(dialingCall.can(Connection.CAPABILITY_HOLD)).thenReturn(true);
        assertFalse(mCallsManager.hasUnholdableCallsForOtherConnectionService(accountHande));

        Call externalCall = addSpyCall(accountHande, notDialingState);
        when(externalCall.isExternalCall()).thenReturn(true);
        assertFalse(mCallsManager.hasUnholdableCallsForOtherConnectionService(accountHande));

        Call unholdableOtherCall = addSpyCall(VOIP_1_HANDLE, notDialingState);
        when(unholdableOtherCall.can(Connection.CAPABILITY_HOLD)).thenReturn(false);
        assertTrue(mCallsManager.hasUnholdableCallsForOtherConnectionService(accountHande));
        assertEquals(1, mCallsManager.getNumUnholdableCallsForOtherConnectionService(accountHande));
    }

    @SmallTest
    @Test
    public void testHasManagedCalls() {
        assertFalse(mCallsManager.hasManagedCalls());

        Call selfManagedCall = addSpyCall();
        when(selfManagedCall.isSelfManaged()).thenReturn(true);
        assertFalse(mCallsManager.hasManagedCalls());

        Call externalCall = addSpyCall();
        when(externalCall.isSelfManaged()).thenReturn(false);
        when(externalCall.isExternalCall()).thenReturn(true);
        assertFalse(mCallsManager.hasManagedCalls());

        Call managedCall = addSpyCall();
        when(managedCall.isSelfManaged()).thenReturn(false);
        assertTrue(mCallsManager.hasManagedCalls());
    }

    @SmallTest
    @Test
    public void testHasSelfManagedCalls() {
        Call managedCall = addSpyCall();
        when(managedCall.isSelfManaged()).thenReturn(false);
        assertFalse(mCallsManager.hasSelfManagedCalls());

        Call selfManagedCall = addSpyCall();
        when(selfManagedCall.isSelfManaged()).thenReturn(true);
        assertTrue(mCallsManager.hasSelfManagedCalls());
    }

    /**
     * Verifies when {@link CallsManager} receives a carrier config change it will trigger an
     * update of the emergency call notification.
     * Note: this test mocks out {@link BlockedNumbersAdapter} so does not actually test posting of
     * the notification.  Notification posting in the actual implementation is covered by
     * {@link BlockedNumbersUtilTests}.
     */
    @SmallTest
    @Test
    public void testUpdateEmergencyCallNotificationOnCarrierConfigChange() {
        when(mBlockedNumbersAdapter.shouldShowEmergencyCallNotification(any(Context.class)))
                .thenReturn(true);
        mComponentContextFixture.getBroadcastReceivers().forEach(c -> c.onReceive(mContext,
                new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED)));
        verify(mBlockedNumbersAdapter).updateEmergencyCallNotification(any(Context.class),
                eq(true));

        when(mBlockedNumbersAdapter.shouldShowEmergencyCallNotification(any(Context.class)))
                .thenReturn(false);
        mComponentContextFixture.getBroadcastReceivers().forEach(c -> c.onReceive(mContext,
                new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED)));
        verify(mBlockedNumbersAdapter).updateEmergencyCallNotification(any(Context.class),
                eq(false));
    }

    /**
     * Verifies when {@link CallsManager} receives a signal from the blocked number provider that
     * the call blocking enabled state changes, it will trigger an update of the emergency call
     * notification.
     * Note: this test mocks out {@link BlockedNumbersAdapter} so does not actually test posting of
     * the notification.  Notification posting in the actual implementation is covered by
     * {@link BlockedNumbersUtilTests}.
     */
    @SmallTest
    @Test
    public void testUpdateEmergencyCallNotificationOnNotificationVisibilityChange() {
        when(mBlockedNumbersAdapter.shouldShowEmergencyCallNotification(any(Context.class)))
                .thenReturn(true);
        mComponentContextFixture.getBroadcastReceivers().forEach(c -> c.onReceive(mContext,
                new Intent(
                        BlockedNumberContract.BlockedNumbers
                                .ACTION_BLOCK_SUPPRESSION_STATE_CHANGED)));
        verify(mBlockedNumbersAdapter).updateEmergencyCallNotification(any(Context.class),
                eq(true));

        when(mBlockedNumbersAdapter.shouldShowEmergencyCallNotification(any(Context.class)))
                .thenReturn(false);
        mComponentContextFixture.getBroadcastReceivers().forEach(c -> c.onReceive(mContext,
                new Intent(
                        BlockedNumberContract.BlockedNumbers
                                .ACTION_BLOCK_SUPPRESSION_STATE_CHANGED)));
        verify(mBlockedNumbersAdapter).updateEmergencyCallNotification(any(Context.class),
                eq(false));
    }

    /**
     * Verify CallsManager#isInSelfManagedCall(packageName, userHandle) returns true when
     * CallsManager is first made aware of the incoming call in processIncomingCallIntent.
     */
    @SmallTest
    @Test
    public void testAddNewIncomingCall_IsInSelfManagedCall() {
        // GIVEN
        assertEquals(0, mCallsManager.getSelfManagedCallsBeingSetup().size());
        assertFalse(mCallsManager.isInSelfManagedCall(TEST_PACKAGE_NAME, TEST_USER_HANDLE));

        // WHEN
        when(mPhoneAccountRegistrar.getPhoneAccountUnchecked(any()))
                .thenReturn(SM_W_DIFFERENT_PACKAGE_AND_USER);
        when(mMockCurrentUserManager.isAdminUser()).thenReturn(true);

        // THEN
        mCallsManager.processIncomingCallIntent(SELF_MANAGED_W_CUSTOM_HANDLE, new Bundle(), false);

        assertEquals(1, mCallsManager.getSelfManagedCallsBeingSetup().size());
        assertTrue(mCallsManager.isInSelfManagedCall(TEST_PACKAGE_NAME, TEST_USER_HANDLE));
        assertEquals(0, mCallsManager.getCalls().size());
    }

    /**
     * Verify CallsManager#isInSelfManagedCall(packageName, userHandle) returns true when
     * CallsManager is first made aware of the outgoing call in StartOutgoingCall.
     */
    @SmallTest
    @Test
    public void testStartOutgoing_IsInSelfManagedCall() {
        // GIVEN
        assertEquals(0, mCallsManager.getSelfManagedCallsBeingSetup().size());
        assertFalse(mCallsManager.isInSelfManagedCall(TEST_PACKAGE_NAME, TEST_USER_HANDLE));

        // WHEN
        when(mPhoneAccountRegistrar.getPhoneAccount(any(), any()))
                .thenReturn(SM_W_DIFFERENT_PACKAGE_AND_USER);
        // Ensure contact info lookup succeeds
        doAnswer(invocation -> {
            Uri handle = invocation.getArgument(0);
            CallerInfo info = new CallerInfo();
            CompletableFuture<Pair<Uri, CallerInfo>> callerInfoFuture = new CompletableFuture<>();
            callerInfoFuture.complete(new Pair<>(handle, info));
            return callerInfoFuture;
        }).when(mCallerInfoLookupHelper).startLookup(any(Uri.class));
        // Ensure we have candidate phone account handle info.
        when(mPhoneAccountRegistrar.getOutgoingPhoneAccountForScheme(any(), any())).thenReturn(
                SELF_MANAGED_W_CUSTOM_HANDLE);
        when(mPhoneAccountRegistrar.getCallCapablePhoneAccounts(any(), anyBoolean(),
                any(), anyInt(), anyInt(), anyBoolean())).thenReturn(
                new ArrayList<>(List.of(SELF_MANAGED_W_CUSTOM_HANDLE)));

        // THEN
        mCallsManager.startOutgoingCall(TEST_ADDRESS, SELF_MANAGED_W_CUSTOM_HANDLE, new Bundle(),
                TEST_USER_HANDLE, new Intent(), TEST_PACKAGE_NAME);

        assertEquals(1, mCallsManager.getSelfManagedCallsBeingSetup().size());
        assertTrue(mCallsManager.isInSelfManagedCall(TEST_PACKAGE_NAME, TEST_USER_HANDLE));
        assertEquals(0, mCallsManager.getCalls().size());
    }

    /**
     * Verify SelfManagedCallsBeingSetup is being cleaned up in CallsManager#addCall and
     * CallsManager#removeCall.  This ensures no memory leaks.
     */
    @SmallTest
    @Test
    public void testCallsBeingSetupCleanup() {
        Call spyCall = addSpyCall();
        assertEquals(0, mCallsManager.getSelfManagedCallsBeingSetup().size());
        // verify CallsManager#removeCall removes the call from SelfManagedCallsBeingSetup
        mCallsManager.addCallBeingSetup(spyCall);
        mCallsManager.removeCall(spyCall);
        assertEquals(0, mCallsManager.getSelfManagedCallsBeingSetup().size());
        // verify CallsManager#addCall removes the call from SelfManagedCallsBeingSetup
        mCallsManager.addCallBeingSetup(spyCall);
        mCallsManager.addCall(spyCall);
        assertEquals(0, mCallsManager.getSelfManagedCallsBeingSetup().size());
    }

    /**
     * Verify isInSelfManagedCall returns false if there is a self-managed call, but it is for a
     * different package and user
     */
    @SmallTest
    @Test
    public void testIsInSelfManagedCall_PackageUserQueryIsWorkingAsIntended() {
        // start an active call
        Call randomCall = createSpyCall(SELF_MANAGED_HANDLE, CallState.ACTIVE);
        mCallsManager.addCallBeingSetup(randomCall);
        assertEquals(1, mCallsManager.getSelfManagedCallsBeingSetup().size());
        // query isInSelfManagedCall for a package that is NOT in a call;  expect false
        assertFalse(mCallsManager.isInSelfManagedCall(TEST_PACKAGE_NAME, TEST_USER_HANDLE));
        // start another call
        Call targetCall = addSpyCall(SELF_MANAGED_W_CUSTOM_HANDLE, CallState.DIALING);
        when(targetCall.getTargetPhoneAccount()).thenReturn(SELF_MANAGED_W_CUSTOM_HANDLE);
        when(targetCall.isSelfManaged()).thenReturn(true);
        mCallsManager.addCallBeingSetup(targetCall);
        // query isInSelfManagedCall for a package that is in a call
        assertTrue(mCallsManager.isInSelfManagedCall(TEST_PACKAGE_NAME, TEST_USER_HANDLE));
    }

    @SmallTest
    @Test
    public void testBindToBtServiceSeparately() {
        when(mFeatureFlags.separatelyBindToBtIncallService()).thenReturn(true);
        Call call = addSpyCall(CallState.NEW);
        CallFilteringResult result = new CallFilteringResult.Builder()
                .setShouldAllowCall(true)
                .setShouldReject(false)
                .build();
        when(mInCallController.bindToBTService(eq(call))).thenReturn(
                CompletableFuture.completedFuture(true));
        when(mInCallController.isBoundAndConnectedToBTService(any(UserHandle.class)))
                .thenReturn(false);

        mCallsManager.onCallFilteringComplete(call, result, false);

        InOrder inOrder = inOrder(mInCallController, call, mInCallController);

        inOrder.verify(mInCallController).bindToBTService(eq(call));
        inOrder.verify(call).setState(eq(CallState.RINGING), anyString());
    }


    private Call addSpyCall() {
        return addSpyCall(SIM_2_HANDLE, CallState.ACTIVE);
    }

    private Call addSpyCall(int initialState) {
        return addSpyCall(SIM_2_HANDLE, initialState);
    }

    private Call addSpyCall(PhoneAccountHandle targetPhoneAccount, int initialState) {
        return addSpyCall(targetPhoneAccount, null, initialState, 0 /*caps*/, 0 /*props*/);
    }

    private Call addSpyCall(PhoneAccountHandle targetPhoneAccount,
            PhoneAccountHandle connectionMgrAcct, int initialState) {
        return addSpyCall(targetPhoneAccount, connectionMgrAcct, initialState, 0 /*caps*/,
                0 /*props*/);
    }

    private Call addSpyCall(PhoneAccountHandle targetPhoneAccount,
            PhoneAccountHandle connectionMgrAcct, int initialState,
            int connectionCapabilities, int connectionProperties) {
        Call ongoingCall = createCall(targetPhoneAccount, connectionMgrAcct, initialState);
        ongoingCall.setConnectionProperties(connectionProperties);
        ongoingCall.setConnectionCapabilities(connectionCapabilities);
        Call callSpy = Mockito.spy(ongoingCall);

        // Mocks some methods to not call the real method.
        doNothing().when(callSpy).unhold();
        doNothing().when(callSpy).hold();
        doNothing().when(callSpy).answer(ArgumentMatchers.anyInt());
        doNothing().when(callSpy).setStartWithSpeakerphoneOn(ArgumentMatchers.anyBoolean());

        mCallsManager.addCall(callSpy);
        return callSpy;
    }

    private Call createSpyCall(PhoneAccountHandle handle, int initialState) {
        Call ongoingCall = createCall(handle, initialState);
        Call callSpy = Mockito.spy(ongoingCall);

        // Mocks some methods to not call the real method.
        doNothing().when(callSpy).unhold();
        doNothing().when(callSpy).hold();
        doNothing().when(callSpy).disconnect();
        doNothing().when(callSpy).answer(ArgumentMatchers.anyInt());
        doNothing().when(callSpy).setStartWithSpeakerphoneOn(ArgumentMatchers.anyBoolean());

        return callSpy;
    }

    private Call createCall(PhoneAccountHandle targetPhoneAccount, int initialState) {
        return createCall(targetPhoneAccount, null /* connectionManager */, initialState);
    }

    private Call createCall(PhoneAccountHandle targetPhoneAccount,
            PhoneAccountHandle connectionManagerAccount, int initialState) {
        Call ongoingCall = new Call(String.format("TC@%d", sCallId++), /* callId */
                mContext,
                mCallsManager,
                mLock, /* ConnectionServiceRepository */
                null,
                mPhoneNumberUtilsAdapter,
                TEST_ADDRESS,
                null /* GatewayInfo */,
                connectionManagerAccount,
                targetPhoneAccount,
                Call.CALL_DIRECTION_INCOMING,
                false /* shouldAttachToExistingConnection*/,
                false /* isConference */,
                mClockProxy,
                mToastFactory,
                mFeatureFlags);
        ongoingCall.setState(initialState, "just cuz");
        if (targetPhoneAccount == SELF_MANAGED_HANDLE
                || targetPhoneAccount == SELF_MANAGED_2_HANDLE) {
            ongoingCall.setIsSelfManaged(true);
        }
        return ongoingCall;
    }

    private void verifyFocusRequestAndExecuteCallback(Call call) {
        ArgumentCaptor<CallsManager.RequestCallback> captor =
                ArgumentCaptor.forClass(CallsManager.RequestCallback.class);
        verify(mConnectionSvrFocusMgr).requestFocus(eq(call), captor.capture());
        CallsManager.RequestCallback callback = captor.getValue();
        callback.onRequestFocusDone(call);
    }

    /**
     * Set up 2 SIM accounts in DSDS mode, where only one SIM can be active at a time for calls.
     */
    private void setupMsimAccounts() {
        TelephonyManager mockTelephonyManager = mComponentContextFixture.getTelephonyManager();
        when(mockTelephonyManager.getMaxNumberOfSimultaneouslyActiveSims()).thenReturn(1);
        when(mPhoneAccountRegistrar.getCallCapablePhoneAccounts(any(), anyBoolean(),
                any(), anyInt(), anyInt(), anyBoolean())).thenReturn(
                new ArrayList<>(Arrays.asList(SIM_1_HANDLE, SIM_2_HANDLE)));
        when(mPhoneAccountRegistrar.getSimPhoneAccountsOfCurrentUser()).thenReturn(
                new ArrayList<>(Arrays.asList(SIM_1_HANDLE, SIM_2_HANDLE)));
    }

    /**
     * Set up 2 SIM accounts in DSDS mode and one call provider, where there is no restriction on
     * simultaneous calls across accounts.
     */
    private void setupAccountsNoSimultaneousCallingRestriction() {
        when(mPhoneAccountRegistrar.getCallCapablePhoneAccounts(any(), anyBoolean(),
                any(), anyInt(), anyInt(), anyBoolean())).thenReturn(
                new ArrayList<>(Arrays.asList(SIM_1_HANDLE, SIM_2_HANDLE, CALL_PROVIDER_HANDLE)));
        when(mPhoneAccountRegistrar.getSimPhoneAccountsOfCurrentUser()).thenReturn(
                new ArrayList<>(Arrays.asList(SIM_1_HANDLE, SIM_2_HANDLE)));
    }

    /**
     * Set up the call capable PhoneAccounts passed in here to have simultaneous calling
     * restrictions. If the callCapableHandle is a SIM account and it is in the restriction set, we
     * will set that callCapableHandle's restriction to the Set. If not, we will set the restriction
     * to allow no other simultaneous calls.
     */
    private void setupAccountsWithCallingRestriction(List<PhoneAccountHandle> callCapableHandles,
            Set<PhoneAccountHandle> restrictions) {
        when(mPhoneAccountRegistrar.getCallCapablePhoneAccounts(any(), anyBoolean(),
                any(), anyInt(), anyInt(), anyBoolean())).thenReturn(
                new ArrayList<>(callCapableHandles));
        List<PhoneAccountHandle> allSims = Arrays.asList(SIM_1_HANDLE, SIM_2_HANDLE,
                SIM_3_HANDLE);
        List<PhoneAccountHandle> simsToTest = callCapableHandles.stream()
                .filter(allSims::contains).toList();
        when(mPhoneAccountRegistrar.getSimPhoneAccountsOfCurrentUser()).thenReturn(simsToTest);
        // Remap the PhoneAccounts to inherit the restriction set
        for (PhoneAccountHandle callCapableHandle : callCapableHandles) {
            PhoneAccount pa = mPhoneAccountRegistrar.getPhoneAccount(
                    callCapableHandle, callCapableHandle.getUserHandle());
            assertNotNull("test setup error: could not find PA for PAH:" + callCapableHandle,
                    pa);
            // For simplicity, for testing only apply restrictions to SIM accounts
            if (!allSims.contains(callCapableHandle)) continue;
            if (restrictions.contains(callCapableHandle)) {
                pa = new PhoneAccount.Builder(pa)
                        .setSimultaneousCallingRestriction(restrictions).build();
            } else {
                pa = new PhoneAccount.Builder(pa)
                        .setSimultaneousCallingRestriction(Collections.emptySet()).build();
            }
            when(mPhoneAccountRegistrar.getPhoneAccount(eq(callCapableHandle),
                    any())).thenReturn(pa);
        }
    }

    private void setMaxActiveVoiceSubscriptions(int num) {
        TelephonyManager mockTelephonyManager = mComponentContextFixture.getTelephonyManager();
        when(mockTelephonyManager.getPhoneCapability()).thenReturn(mPhoneCapability);
        when(mPhoneCapability.getMaxActiveVoiceSubscriptions()).thenReturn(num);
    }

    private void waitUntilConditionIsTrueOrTimeout(Condition condition, long timeout,
            String description) throws InterruptedException {
        final long start = System.currentTimeMillis();
        while (!condition.expected().equals(condition.actual())
                && System.currentTimeMillis() - start < timeout) {
            sleep(50);
        }
        assertEquals(description, condition.expected(), condition.actual());
    }

    protected interface Condition {
        Object expected();
        Object actual();
    }
}
