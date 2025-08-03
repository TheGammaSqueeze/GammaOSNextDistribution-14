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

package com.android.server.telecom.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.net.Uri;
import android.os.UserHandle;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;

import com.android.server.telecom.AnomalyReporterAdapter;
import com.android.server.telecom.Call;
import com.android.server.telecom.CallAnomalyWatchdog;
import com.android.server.telecom.CallState;
import com.android.server.telecom.CallerInfoLookupHelper;
import com.android.server.telecom.CallsManager;
import com.android.server.telecom.ClockProxy;
import com.android.server.telecom.ConnectionServiceWrapper;
import com.android.server.telecom.EmergencyCallDiagnosticLogger;
import com.android.server.telecom.PhoneAccountRegistrar;
import com.android.server.telecom.PhoneNumberUtilsAdapter;
import com.android.server.telecom.TelecomSystem;
import com.android.server.telecom.Timeouts;
import com.android.server.telecom.ui.ToastFactory;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;

@RunWith(JUnit4.class)
public class CallAnomalyWatchdogTest extends TelecomTestCase {
    private static final ComponentName COMPONENT_NAME_1 = ComponentName
            .unflattenFromString("com.foo/.Blah");
    private static final PhoneAccountHandle SIM_1_HANDLE = new PhoneAccountHandle(
            COMPONENT_NAME_1, "Sim1");
    private static final PhoneAccount SIM_1_ACCOUNT = new PhoneAccount.
            Builder(SIM_1_HANDLE, "Sim1")
            .setCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION
                    | PhoneAccount.CAPABILITY_CALL_PROVIDER)
            .setIsEnabled(true)
            .build();

    private final static long TEST_VOIP_TRANSITORY_MILLIS = 100L;
    private final static long TEST_VOIP_EMERGENCY_TRANSITORY_MILLIS = 150L;
    private final static long TEST_NON_VOIP_TRANSITORY_MILLIS = 200L;
    private final static long TEST_NON_VOIP_EMERGENCY_TRANSITORY_MILLIS = 250L;
    private final static long TEST_VOIP_INTERMEDIATE_MILLIS = 300L;
    private final static long TEST_VOIP_EMERGENCY_INTERMEDIATE_MILLIS = 350L;
    private final static long TEST_NON_VOIP_INTERMEDIATE_MILLIS = 400L;
    private final static long TEST_NON_VOIP_EMERGENCY_INTERMEDIATE_MILLIS = 450L;

    private CallAnomalyWatchdog mCallAnomalyWatchdog;
    private TestScheduledExecutorService mTestScheduledExecutorService;
    private final TelecomSystem.SyncRoot mLock = new TelecomSystem.SyncRoot() { };
    @Mock private Timeouts.Adapter mTimeouts;
    @Mock private CallsManager mMockCallsManager;
    @Mock private CallerInfoLookupHelper mMockCallerInfoLookupHelper;
    @Mock private PhoneAccountRegistrar mMockPhoneAccountRegistrar;
    @Mock private ClockProxy mMockClockProxy;
    @Mock private ToastFactory mMockToastProxy;
    @Mock private PhoneNumberUtilsAdapter mMockPhoneNumberUtilsAdapter;
    @Mock private ConnectionServiceWrapper mMockConnectionService;
    @Mock private AnomalyReporterAdapter mAnomalyReporterAdapter;

    @Mock private EmergencyCallDiagnosticLogger mMockEmergencyCallDiagnosticLogger;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        doReturn(mMockCallerInfoLookupHelper).when(mMockCallsManager).getCallerInfoLookupHelper();
        doReturn(mMockPhoneAccountRegistrar).when(mMockCallsManager).getPhoneAccountRegistrar();
        doReturn(SIM_1_ACCOUNT).when(mMockPhoneAccountRegistrar).getPhoneAccountUnchecked(
                eq(SIM_1_HANDLE));
        mTestScheduledExecutorService = new TestScheduledExecutorService();

        when(mTimeouts.getVoipCallTransitoryStateTimeoutMillis()).
                thenReturn(TEST_VOIP_TRANSITORY_MILLIS);
        when(mTimeouts.getVoipEmergencyCallTransitoryStateTimeoutMillis()).
                thenReturn(TEST_VOIP_EMERGENCY_TRANSITORY_MILLIS);
        when(mTimeouts.getNonVoipCallTransitoryStateTimeoutMillis()).
                thenReturn(TEST_NON_VOIP_TRANSITORY_MILLIS);
        when(mTimeouts.getNonVoipEmergencyCallTransitoryStateTimeoutMillis()).
                thenReturn(TEST_NON_VOIP_EMERGENCY_TRANSITORY_MILLIS);
        when(mTimeouts.getVoipCallIntermediateStateTimeoutMillis()).
                thenReturn(TEST_VOIP_INTERMEDIATE_MILLIS);
        when(mTimeouts.getVoipEmergencyCallIntermediateStateTimeoutMillis()).
                thenReturn(TEST_VOIP_EMERGENCY_INTERMEDIATE_MILLIS);
        when(mTimeouts.getNonVoipCallIntermediateStateTimeoutMillis()).
                thenReturn(TEST_NON_VOIP_INTERMEDIATE_MILLIS);
        when(mTimeouts.getNonVoipEmergencyCallIntermediateStateTimeoutMillis()).
                thenReturn(TEST_NON_VOIP_EMERGENCY_INTERMEDIATE_MILLIS);

        when(mMockClockProxy.elapsedRealtime()).thenReturn(0L);
        doReturn(new ComponentName(mContext, CallTest.class))
                .when(mMockConnectionService).getComponentName();
        mCallAnomalyWatchdog = new CallAnomalyWatchdog(mTestScheduledExecutorService, mLock,
                mTimeouts, mMockClockProxy, mMockEmergencyCallDiagnosticLogger);
        mCallAnomalyWatchdog.setAnomalyReporterAdapter(mAnomalyReporterAdapter);
        when(mMockCallsManager.getCurrentUserHandle()).thenReturn(UserHandle.CURRENT);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Helper function that setups the call being tested.
     */
    private Call setupCallHelper(int callState, boolean isCreateConnectionComplete,
            ConnectionServiceWrapper service, boolean isVoipAudioMode, boolean isEmergencyCall) {
        Call call = getCall();
        call.setState(callState, "foo");
        call.setIsCreateConnectionComplete(isCreateConnectionComplete);
        if (service != null) call.setConnectionService(service);
        call.setIsVoipAudioMode(isVoipAudioMode);
        call.setIsEmergencyCall(isEmergencyCall);
        mCallAnomalyWatchdog.onCallAdded(call);
        return call;
    }

    /**
     * Test that the anomaly call state class correctly reports whether the state is transitory or
     * not for the purposes of the call anomaly watchdog.
     */
    @Test
    public void testAnomalyCallStateIsTransitory() {
        // When connection creation isn't complete, most states are transitory from the anomaly
        // tracker's point of view.
        assertTrue(new CallAnomalyWatchdog.WatchdogCallState(CallState.NEW,
                false /* isCreateConnectionComplete */, 0L).isInTransitoryState());
        assertTrue(new CallAnomalyWatchdog.WatchdogCallState(CallState.CONNECTING,
                false /* isCreateConnectionComplete */, 0L).isInTransitoryState());
        assertTrue(new CallAnomalyWatchdog.WatchdogCallState(CallState.DIALING,
                false /* isCreateConnectionComplete */, 0L).isInTransitoryState());
        assertTrue(new CallAnomalyWatchdog.WatchdogCallState(CallState.RINGING,
                false /* isCreateConnectionComplete */, 0L).isInTransitoryState());
        assertTrue(new CallAnomalyWatchdog.WatchdogCallState(CallState.ACTIVE,
                false /* isCreateConnectionComplete */, 0L).isInTransitoryState());
        assertTrue(new CallAnomalyWatchdog.WatchdogCallState(CallState.ON_HOLD,
                false /* isCreateConnectionComplete */, 0L).isInTransitoryState());
        assertTrue(new CallAnomalyWatchdog.WatchdogCallState(CallState.DISCONNECTED,
                false /* isCreateConnectionComplete */, 0L).isInTransitoryState());
        assertTrue(new CallAnomalyWatchdog.WatchdogCallState(CallState.ABORTED,
                false /* isCreateConnectionComplete */, 0L).isInTransitoryState());
        assertTrue(new CallAnomalyWatchdog.WatchdogCallState(CallState.DISCONNECTING,
                false /* isCreateConnectionComplete */, 0L).isInTransitoryState());
        assertTrue(new CallAnomalyWatchdog.WatchdogCallState(CallState.PULLING,
                false /* isCreateConnectionComplete */, 0L).isInTransitoryState());
        assertTrue(new CallAnomalyWatchdog.WatchdogCallState(CallState.ANSWERED,
                false /* isCreateConnectionComplete */, 0L).isInTransitoryState());
        assertTrue(new CallAnomalyWatchdog.WatchdogCallState(CallState.AUDIO_PROCESSING,
                false /* isCreateConnectionComplete */, 0L).isInTransitoryState());
        assertTrue(new CallAnomalyWatchdog.WatchdogCallState(CallState.SIMULATED_RINGING,
                false /* isCreateConnectionComplete */, 0L).isInTransitoryState());
        // When create connection is complete, these few are considered to be transitory.
        assertTrue(new CallAnomalyWatchdog.WatchdogCallState(CallState.NEW,
                true /* isCreateConnectionComplete */, 0L).isInTransitoryState());
        assertTrue(new CallAnomalyWatchdog.WatchdogCallState(CallState.CONNECTING,
                true /* isCreateConnectionComplete */, 0L).isInTransitoryState());
        assertTrue(new CallAnomalyWatchdog.WatchdogCallState(CallState.DISCONNECTING,
                true /* isCreateConnectionComplete */, 0L).isInTransitoryState());
        assertTrue(new CallAnomalyWatchdog.WatchdogCallState(CallState.ANSWERED,
                true /* isCreateConnectionComplete */, 0L).isInTransitoryState());

        // These are never considered transitory
        assertFalse(new CallAnomalyWatchdog.WatchdogCallState(CallState.SELECT_PHONE_ACCOUNT,
                false /* isCreateConnectionComplete */, 0L).isInTransitoryState());
        assertFalse(new CallAnomalyWatchdog.WatchdogCallState(CallState.SELECT_PHONE_ACCOUNT,
                true /* isCreateConnectionComplete */, 0L).isInTransitoryState());
        assertFalse(new CallAnomalyWatchdog.WatchdogCallState(CallState.DIALING,
                true /* isCreateConnectionComplete */, 0L).isInTransitoryState());
        assertFalse(new CallAnomalyWatchdog.WatchdogCallState(CallState.RINGING,
                true /* isCreateConnectionComplete */, 0L).isInTransitoryState());
        assertFalse(new CallAnomalyWatchdog.WatchdogCallState(CallState.ACTIVE,
                true /* isCreateConnectionComplete */, 0L).isInTransitoryState());
        assertFalse(new CallAnomalyWatchdog.WatchdogCallState(CallState.ON_HOLD,
                true /* isCreateConnectionComplete */, 0L).isInTransitoryState());
        assertFalse(new CallAnomalyWatchdog.WatchdogCallState(CallState.DISCONNECTED,
                true /* isCreateConnectionComplete */, 0L).isInTransitoryState());
        assertFalse(new CallAnomalyWatchdog.WatchdogCallState(CallState.ABORTED,
                true /* isCreateConnectionComplete */, 0L).isInTransitoryState());
        assertFalse(new CallAnomalyWatchdog.WatchdogCallState(CallState.PULLING,
                true /* isCreateConnectionComplete */, 0L).isInTransitoryState());
        assertFalse(new CallAnomalyWatchdog.WatchdogCallState(CallState.AUDIO_PROCESSING,
                true /* isCreateConnectionComplete */, 0L).isInTransitoryState());
        assertFalse(new CallAnomalyWatchdog.WatchdogCallState(CallState.SIMULATED_RINGING,
                true /* isCreateConnectionComplete */, 0L).isInTransitoryState());
    }

    /**
     * Test that the anomaly call state class correctly reports whether the state is intermediate or
     * not for the purposes of the call anomaly watchdog.
     */
    @Test
    public void testAnomalyCallStateIsIntermediate() {
        // When connection creation isn't complete, most states are not intermediate from
        // the anomaly tracker's point of view.
        assertFalse(new CallAnomalyWatchdog.WatchdogCallState(CallState.NEW,
                false /* isCreateConnectionComplete */, 0L).isInIntermediateState());
        assertFalse(new CallAnomalyWatchdog.WatchdogCallState(CallState.CONNECTING,
                false /* isCreateConnectionComplete */, 0L).isInIntermediateState());
        assertFalse(new CallAnomalyWatchdog.WatchdogCallState(CallState.SELECT_PHONE_ACCOUNT,
                false /* isCreateConnectionComplete */, 0L).isInIntermediateState());
        assertFalse(new CallAnomalyWatchdog.WatchdogCallState(CallState.DIALING,
                false /* isCreateConnectionComplete */, 0L).isInIntermediateState());
        assertFalse(new CallAnomalyWatchdog.WatchdogCallState(CallState.RINGING,
                false /* isCreateConnectionComplete */, 0L).isInIntermediateState());
        assertFalse(new CallAnomalyWatchdog.WatchdogCallState(CallState.ACTIVE,
                false /* isCreateConnectionComplete */, 0L).isInIntermediateState());
        assertFalse(new CallAnomalyWatchdog.WatchdogCallState(CallState.ON_HOLD,
                false /* isCreateConnectionComplete */, 0L).isInIntermediateState());
        assertFalse(new CallAnomalyWatchdog.WatchdogCallState(CallState.DISCONNECTED,
                false /* isCreateConnectionComplete */, 0L).isInIntermediateState());
        assertFalse(new CallAnomalyWatchdog.WatchdogCallState(CallState.ABORTED,
                false /* isCreateConnectionComplete */, 0L).isInIntermediateState());
        assertFalse(new CallAnomalyWatchdog.WatchdogCallState(CallState.DISCONNECTING,
                false /* isCreateConnectionComplete */, 0L).isInIntermediateState());
        assertFalse(new CallAnomalyWatchdog.WatchdogCallState(CallState.PULLING,
                false /* isCreateConnectionComplete */, 0L).isInIntermediateState());
        assertFalse(new CallAnomalyWatchdog.WatchdogCallState(CallState.ANSWERED,
                false /* isCreateConnectionComplete */, 0L).isInIntermediateState());
        assertFalse(new CallAnomalyWatchdog.WatchdogCallState(CallState.AUDIO_PROCESSING,
                false /* isCreateConnectionComplete */, 0L).isInIntermediateState());
        assertFalse(new CallAnomalyWatchdog.WatchdogCallState(CallState.SIMULATED_RINGING,
                false /* isCreateConnectionComplete */, 0L).isInIntermediateState());

        // If it is not in DIALING and RINGING state, it is not considered as an intermediate state.
        assertFalse(new CallAnomalyWatchdog.WatchdogCallState(CallState.NEW,
                true /* isCreateConnectionComplete */, 0L).isInIntermediateState());
        assertFalse(new CallAnomalyWatchdog.WatchdogCallState(CallState.CONNECTING,
                true /* isCreateConnectionComplete */, 0L).isInIntermediateState());
        assertFalse(new CallAnomalyWatchdog.WatchdogCallState(CallState.SELECT_PHONE_ACCOUNT,
                true /* isCreateConnectionComplete */, 0L).isInIntermediateState());
        assertFalse(new CallAnomalyWatchdog.WatchdogCallState(CallState.ACTIVE,
                true /* isCreateConnectionComplete */, 0L).isInIntermediateState());
        assertFalse(new CallAnomalyWatchdog.WatchdogCallState(CallState.ON_HOLD,
                true /* isCreateConnectionComplete */, 0L).isInIntermediateState());
        assertFalse(new CallAnomalyWatchdog.WatchdogCallState(CallState.DISCONNECTED,
                true /* isCreateConnectionComplete */, 0L).isInIntermediateState());
        assertFalse(new CallAnomalyWatchdog.WatchdogCallState(CallState.ABORTED,
                true /* isCreateConnectionComplete */, 0L).isInIntermediateState());
        assertFalse(new CallAnomalyWatchdog.WatchdogCallState(CallState.DISCONNECTING,
                true /* isCreateConnectionComplete */, 0L).isInIntermediateState());
        assertFalse(new CallAnomalyWatchdog.WatchdogCallState(CallState.PULLING,
                true /* isCreateConnectionComplete */, 0L).isInIntermediateState());
        assertFalse(new CallAnomalyWatchdog.WatchdogCallState(CallState.ANSWERED,
                true /* isCreateConnectionComplete */, 0L).isInIntermediateState());
        assertFalse(new CallAnomalyWatchdog.WatchdogCallState(CallState.AUDIO_PROCESSING,
                true /* isCreateConnectionComplete */, 0L).isInIntermediateState());
        assertFalse(new CallAnomalyWatchdog.WatchdogCallState(CallState.SIMULATED_RINGING,
                true /* isCreateConnectionComplete */, 0L).isInIntermediateState());

        // These are considered as an intermediate state.
        assertTrue(new CallAnomalyWatchdog.WatchdogCallState(CallState.DIALING,
                true /* isCreateConnectionComplete */, 0L).isInIntermediateState());
        assertTrue(new CallAnomalyWatchdog.WatchdogCallState(CallState.RINGING,
                true /* isCreateConnectionComplete */, 0L).isInIntermediateState());
    }

    /**
     * Emulate the case where a new incoming VoIP call is added to the watchdog.
     * CallsManager creates calls in a ringing state before they're even created by the underlying
     * ConnectionService.  The call is added by the connection service before the timeout expires,
     * so we verify that the call does not get disconnected.
     */
     @Test
    public void testAddVoipRingingCall() {
        Call call = setupCallHelper(CallState.RINGING, false, null, true, false);

        // Newly created call which hasn't been added; should schedule timeout.
        assertEquals(1, mTestScheduledExecutorService.getNumberOfScheduledRunnables());
        assertTrue(mTestScheduledExecutorService.
                isRunnableScheduledAtTime(TEST_VOIP_TRANSITORY_MILLIS));
        assertEquals(1, mCallAnomalyWatchdog.getNumberOfScheduledTimeouts());

        // Gets added to connection service; this moves it to an intermediate state,
        // so timeouts should be scheduled at this point.
        call.setIsCreateConnectionComplete(true);
        call.setConnectionService(mMockConnectionService);
        mCallAnomalyWatchdog.onCallAdded(call);

        assertEquals(1, mTestScheduledExecutorService.getNumberOfScheduledRunnables());
        assertTrue(mTestScheduledExecutorService.
                isRunnableScheduledAtTime(TEST_VOIP_INTERMEDIATE_MILLIS));
        assertEquals(1, mCallAnomalyWatchdog.getNumberOfScheduledTimeouts());

        // Move the clock forward; we'll confirm that no timeout took place.
        when(mMockClockProxy.elapsedRealtime()).thenReturn(TEST_VOIP_INTERMEDIATE_MILLIS + 1);
        mTestScheduledExecutorService.advanceTime(TEST_VOIP_INTERMEDIATE_MILLIS + 1);
        // Should still be ringing.
        assertEquals(CallState.RINGING, call.getState());
    }

    /**
     * Emulate the case where a new incoming VoIP emergency call is added to the watchdog.
     * CallsManager creates calls in a ringing state before they're even created by the underlying
     * ConnectionService.  The call is added by the connection service before the timeout expires,
     * so we verify that the call does not get disconnected.
     */
    @Test
    public void testAddVoipEmergencyRingingCall() {
        Call call = setupCallHelper(CallState.RINGING, false, null, true, true);

        // Newly created call which hasn't been added; should schedule timeout.
        assertEquals(1, mTestScheduledExecutorService.getNumberOfScheduledRunnables());
        assertTrue(mTestScheduledExecutorService.
                isRunnableScheduledAtTime(TEST_VOIP_EMERGENCY_TRANSITORY_MILLIS));
        assertEquals(1, mCallAnomalyWatchdog.getNumberOfScheduledTimeouts());

        // Gets added to connection service; this moves it to an intermediate state,
        // so timeouts should be scheduled at this point.
        call.setIsCreateConnectionComplete(true);
        call.setConnectionService(mMockConnectionService);
        mCallAnomalyWatchdog.onCallAdded(call);

        assertEquals(1, mTestScheduledExecutorService.getNumberOfScheduledRunnables());
        assertTrue(mTestScheduledExecutorService.
                isRunnableScheduledAtTime(TEST_VOIP_EMERGENCY_INTERMEDIATE_MILLIS));
        assertEquals(1, mCallAnomalyWatchdog.getNumberOfScheduledTimeouts());

        // Move the clock forward; we'll confirm that no timeout took place.
        when(mMockClockProxy.elapsedRealtime()).
                thenReturn(TEST_VOIP_EMERGENCY_INTERMEDIATE_MILLIS + 1);
        mTestScheduledExecutorService.advanceTime(TEST_VOIP_EMERGENCY_INTERMEDIATE_MILLIS + 1);
        // Should still be ringing.
        assertEquals(CallState.RINGING, call.getState());
    }

    /**
     * Emulate the case where a new incoming non-VoIP call is added to the watchdog.
     * CallsManager creates calls in a ringing state before they're even created by the underlying
     * ConnectionService.  The call is added by the connection service before the timeout expires,
     * so we verify that the call does not get disconnected.
     */
    @Test
    public void testAddNonVoipRingingCall() {
        Call call = setupCallHelper(CallState.RINGING, false, null, false, false);

        // Newly created call which hasn't been added; should schedule timeout.
        assertEquals(1, mTestScheduledExecutorService.getNumberOfScheduledRunnables());
        assertTrue(mTestScheduledExecutorService.
                isRunnableScheduledAtTime(TEST_NON_VOIP_TRANSITORY_MILLIS));
        assertEquals(1, mCallAnomalyWatchdog.getNumberOfScheduledTimeouts());

        // Gets added to connection service; this moves it to an intermediate state,
        // so timeouts should be scheduled at this point.
        call.setIsCreateConnectionComplete(true);
        call.setConnectionService(mMockConnectionService);
        mCallAnomalyWatchdog.onCallAdded(call);

        assertEquals(1, mTestScheduledExecutorService.getNumberOfScheduledRunnables());
        assertTrue(mTestScheduledExecutorService.
                isRunnableScheduledAtTime(TEST_NON_VOIP_INTERMEDIATE_MILLIS));
        assertEquals(1, mCallAnomalyWatchdog.getNumberOfScheduledTimeouts());

        // Move the clock forward; we'll confirm that no timeout took place.
        when(mMockClockProxy.elapsedRealtime()).
                thenReturn(TEST_NON_VOIP_INTERMEDIATE_MILLIS + 1);
        mTestScheduledExecutorService.advanceTime(TEST_NON_VOIP_INTERMEDIATE_MILLIS + 1);
        // Should still be ringing.
        assertEquals(CallState.RINGING, call.getState());
    }

    /**
     * Emulate the case where a new incoming non-VoIP emergency call is added to the watchdog.
     * CallsManager creates calls in a ringing state before they're even created by the underlying
     * ConnectionService.  The call is added by the connection service before the timeout expires,
     * so we verify that the call does not get disconnected.
     */
    @Test
    public void testAddNonVoipEmergencyRingingCall() {
        Call call = setupCallHelper(CallState.RINGING, false, null, false, true);

        // Newly created call which hasn't been added; should schedule timeout.
        assertEquals(1, mTestScheduledExecutorService.getNumberOfScheduledRunnables());
        assertTrue(mTestScheduledExecutorService.
                isRunnableScheduledAtTime(TEST_NON_VOIP_EMERGENCY_TRANSITORY_MILLIS));
        assertEquals(1, mCallAnomalyWatchdog.getNumberOfScheduledTimeouts());

        // Gets added to connection service; this moves it to an intermediate state,
        // so timeouts should be scheduled at this point.
        call.setIsCreateConnectionComplete(true);
        call.setConnectionService(mMockConnectionService);
        mCallAnomalyWatchdog.onCallAdded(call);

        assertEquals(1, mTestScheduledExecutorService.getNumberOfScheduledRunnables());
        assertTrue(mTestScheduledExecutorService.
                isRunnableScheduledAtTime(TEST_NON_VOIP_EMERGENCY_INTERMEDIATE_MILLIS));
        assertEquals(1, mCallAnomalyWatchdog.getNumberOfScheduledTimeouts());

        // Move the clock forward; we'll confirm that no timeout took place.
        when(mMockClockProxy.elapsedRealtime()).
                thenReturn(TEST_NON_VOIP_EMERGENCY_INTERMEDIATE_MILLIS + 1);
        mTestScheduledExecutorService.advanceTime(TEST_NON_VOIP_EMERGENCY_INTERMEDIATE_MILLIS + 1);
        // Should still be ringing.
        assertEquals(CallState.RINGING, call.getState());
    }

    /**
     * Emulate the case where a new incoming VoIP call is added to the watchdog.
     * In this case, the ConnectionService doesn't respond promptly and the timeout will fire.
     */
    @Test
    public void testAddVoipRingingCallTimeoutWithoutConnection() {
        setupCallHelper(CallState.RINGING, false, null, true, false);

        // Newly created call which hasn't been added; should schedule timeout.
        assertEquals(1, mTestScheduledExecutorService.getNumberOfScheduledRunnables());
        assertTrue(mTestScheduledExecutorService.
                isRunnableScheduledAtTime(TEST_VOIP_TRANSITORY_MILLIS));
        assertEquals(1, mCallAnomalyWatchdog.getNumberOfScheduledTimeouts());

        // Move the clock to fire the timeout.
        when(mMockClockProxy.elapsedRealtime()).thenReturn(TEST_VOIP_TRANSITORY_MILLIS + 1);
        mTestScheduledExecutorService.advanceTime(TEST_VOIP_TRANSITORY_MILLIS + 1);

        // No timeouts should be pending at this point since the timeout fired.
        assertEquals(0, mTestScheduledExecutorService.getNumberOfScheduledRunnables());
        assertEquals(0, mCallAnomalyWatchdog.getNumberOfScheduledTimeouts());
    }

    /**
     * Emulate the case where a new incoming VoIP emergency call is added to the watchdog.
     * In this case, the ConnectionService doesn't respond promptly and the timeout will fire.
     */
    @Test
    public void testAddVoipEmergencyRingingCallTimeoutWithoutConnection() {
        setupCallHelper(CallState.RINGING, false, null, true, true);

        // Newly created call which hasn't been added; should schedule timeout.
        assertEquals(1, mTestScheduledExecutorService.getNumberOfScheduledRunnables());
        assertTrue(mTestScheduledExecutorService.
                isRunnableScheduledAtTime(TEST_VOIP_EMERGENCY_TRANSITORY_MILLIS));
        assertEquals(1, mCallAnomalyWatchdog.getNumberOfScheduledTimeouts());

        // Move the clock to fire the timeout.
        when(mMockClockProxy.elapsedRealtime()).thenReturn(TEST_VOIP_EMERGENCY_TRANSITORY_MILLIS +
                1);
        mTestScheduledExecutorService.advanceTime(TEST_VOIP_EMERGENCY_TRANSITORY_MILLIS + 1);

        // No timeouts should be pending at this point since the timeout fired.
        assertEquals(0, mTestScheduledExecutorService.getNumberOfScheduledRunnables());
        assertEquals(0, mCallAnomalyWatchdog.getNumberOfScheduledTimeouts());
    }

    /**
     * Emulate the case where a new incoming non-VoIP call is added to the watchdog.
     * In this case, the ConnectionService doesn't respond promptly and the timeout will fire.
     */
    @Test
    public void testAddNonVoipRingingCallTimeoutWithoutConnection() {
        setupCallHelper(CallState.RINGING, false, null, false, false);;

        // Newly created call which hasn't been added; should schedule timeout.
        assertEquals(1, mTestScheduledExecutorService.getNumberOfScheduledRunnables());
        assertTrue(mTestScheduledExecutorService.
                isRunnableScheduledAtTime(TEST_NON_VOIP_TRANSITORY_MILLIS));
        assertEquals(1, mCallAnomalyWatchdog.getNumberOfScheduledTimeouts());

        // Move the clock to fire the timeout.
        when(mMockClockProxy.elapsedRealtime()).thenReturn(TEST_NON_VOIP_TRANSITORY_MILLIS + 1);
        mTestScheduledExecutorService.advanceTime(TEST_NON_VOIP_TRANSITORY_MILLIS + 1);

        // No timeouts should be pending at this point since the timeout fired.
        assertEquals(0, mTestScheduledExecutorService.getNumberOfScheduledRunnables());
        assertEquals(0, mCallAnomalyWatchdog.getNumberOfScheduledTimeouts());
    }

    /**
     * Emulate the case where a new incoming non-VoIP emergency call is added to the watchdog.
     * In this case, the ConnectionService doesn't respond promptly and the timeout will fire.
     */
    @Test
    public void testAddNonVoipEmergencyRingingCallTimeoutWithoutConnection() {
        setupCallHelper(CallState.RINGING, false, null, false, true);

        // Newly created call which hasn't been added; should schedule timeout.
        assertEquals(1, mTestScheduledExecutorService.getNumberOfScheduledRunnables());
        assertTrue(mTestScheduledExecutorService.
                isRunnableScheduledAtTime(TEST_NON_VOIP_EMERGENCY_TRANSITORY_MILLIS));
        assertEquals(1, mCallAnomalyWatchdog.getNumberOfScheduledTimeouts());

        // Move the clock to fire the timeout.
        when(mMockClockProxy.elapsedRealtime()).
                thenReturn(TEST_NON_VOIP_EMERGENCY_TRANSITORY_MILLIS + 1);
        mTestScheduledExecutorService.advanceTime(TEST_NON_VOIP_EMERGENCY_TRANSITORY_MILLIS + 1);

        // No timeouts should be pending at this point since the timeout fired.
        assertEquals(0, mTestScheduledExecutorService.getNumberOfScheduledRunnables());
        assertEquals(0, mCallAnomalyWatchdog.getNumberOfScheduledTimeouts());
    }

    /**
     * Emulate the case where a new incoming VoIP call is added to the watchdog.
     * In this case, the timeout will fire in intermediate state.
     */
    @Test
    public void testAddVoipRingingCallTimeoutWithConnection() {
        setupCallHelper(CallState.RINGING, true, mMockConnectionService, true, false);

        // Newly created call which hasn't been added; should schedule timeout.
        assertEquals(1, mTestScheduledExecutorService.getNumberOfScheduledRunnables());
        assertTrue(mTestScheduledExecutorService.
                isRunnableScheduledAtTime(TEST_VOIP_INTERMEDIATE_MILLIS));
        assertEquals(1, mCallAnomalyWatchdog.getNumberOfScheduledTimeouts());

        // Move the clock to fire the timeout.
        when(mMockClockProxy.elapsedRealtime()).thenReturn(TEST_VOIP_INTERMEDIATE_MILLIS + 1);
        mTestScheduledExecutorService.advanceTime(TEST_VOIP_INTERMEDIATE_MILLIS + 1);

        // No timeouts should be pending at this point since the timeout fired.
        assertEquals(0, mTestScheduledExecutorService.getNumberOfScheduledRunnables());
        assertEquals(0, mCallAnomalyWatchdog.getNumberOfScheduledTimeouts());
    }

    /**
     * Emulate the case where a new incoming VoIP emergency call is added to the watchdog.
     * In this case, the timeout will fire in intermediate state.
     */
    @Test
    public void testAddVoipEmergencyRingingCallTimeoutWithConnection() {
        setupCallHelper(CallState.RINGING, true, mMockConnectionService, true, true);

        // Newly created call which hasn't been added; should schedule timeout.
        assertEquals(1, mTestScheduledExecutorService.getNumberOfScheduledRunnables());
        assertTrue(mTestScheduledExecutorService.
                isRunnableScheduledAtTime(TEST_VOIP_EMERGENCY_INTERMEDIATE_MILLIS));
        assertEquals(1, mCallAnomalyWatchdog.getNumberOfScheduledTimeouts());

        // Move the clock to fire the timeout.
        when(mMockClockProxy.elapsedRealtime()).
                thenReturn(TEST_VOIP_EMERGENCY_INTERMEDIATE_MILLIS + 1);
        mTestScheduledExecutorService.advanceTime(TEST_VOIP_EMERGENCY_INTERMEDIATE_MILLIS + 1);

        // No timeouts should be pending at this point since the timeout fired.
        assertEquals(0, mTestScheduledExecutorService.getNumberOfScheduledRunnables());
        assertEquals(0, mCallAnomalyWatchdog.getNumberOfScheduledTimeouts());
    }

    /**
     * Emulate the case where a new incoming non-VoIP call is added to the watchdog.
     * In this case, the timeout will fire in intermediate state.
     */
    @Test
    public void testAddNonVoipRingingCallTimeoutWithConnection() {
        setupCallHelper(CallState.RINGING, true, mMockConnectionService, false, false);

        // Newly created call which hasn't been added; should schedule timeout.
        assertEquals(1, mTestScheduledExecutorService.getNumberOfScheduledRunnables());
        assertTrue(mTestScheduledExecutorService.
                isRunnableScheduledAtTime(TEST_NON_VOIP_INTERMEDIATE_MILLIS));
        assertEquals(1, mCallAnomalyWatchdog.getNumberOfScheduledTimeouts());

        // Move the clock to fire the timeout.
        when(mMockClockProxy.elapsedRealtime()).thenReturn(TEST_NON_VOIP_INTERMEDIATE_MILLIS + 1);
        mTestScheduledExecutorService.advanceTime(TEST_NON_VOIP_INTERMEDIATE_MILLIS + 1);

        // No timeouts should be pending at this point since the timeout fired.
        assertEquals(0, mTestScheduledExecutorService.getNumberOfScheduledRunnables());
        assertEquals(0, mCallAnomalyWatchdog.getNumberOfScheduledTimeouts());
    }

    /**
     * Emulate the case where a new incoming non-VoIP emergency call is added to the watchdog.
     * In this case, the timeout will fire in intermediate state.
     */
    @Test
    public void testAddNonVoipEmergencyRingingCallTimeoutWithConnection() {
        setupCallHelper(CallState.RINGING, true, mMockConnectionService, false, true);

        // Newly created call which hasn't been added; should schedule timeout.
        assertEquals(1, mTestScheduledExecutorService.getNumberOfScheduledRunnables());
        assertTrue(mTestScheduledExecutorService.
                isRunnableScheduledAtTime(TEST_NON_VOIP_EMERGENCY_INTERMEDIATE_MILLIS));
        assertEquals(1, mCallAnomalyWatchdog.getNumberOfScheduledTimeouts());

        // Move the clock to fire the timeout.
        when(mMockClockProxy.elapsedRealtime()).
                thenReturn(TEST_NON_VOIP_EMERGENCY_INTERMEDIATE_MILLIS + 1);
        mTestScheduledExecutorService.advanceTime(TEST_NON_VOIP_EMERGENCY_INTERMEDIATE_MILLIS + 1);

        // No timeouts should be pending at this point since the timeout fired.
        assertEquals(0, mTestScheduledExecutorService.getNumberOfScheduledRunnables());
        assertEquals(0, mCallAnomalyWatchdog.getNumberOfScheduledTimeouts());
    }

    /**
     * Emulate the case where a new outgoing VoIP call is added to the watchdog.
     * In this case, the timeout will fire in transitory state.
     */
    @Test
    public void testVoipPlaceCallTimeout() {
        // Call will start in connecting state
        Call call = setupCallHelper(CallState.CONNECTING, false, null, true, false);

        // Assume it is created but the app never sets it to a proper state
        call.setIsCreateConnectionComplete(false);
        mCallAnomalyWatchdog.onCallAdded(call);

        // Its transitory, so should schedule timeout.
        assertEquals(1, mTestScheduledExecutorService.getNumberOfScheduledRunnables());
        assertTrue(mTestScheduledExecutorService.
                isRunnableScheduledAtTime(TEST_VOIP_TRANSITORY_MILLIS));
        assertEquals(1, mCallAnomalyWatchdog.getNumberOfScheduledTimeouts());

        // Move the clock to fire the timeout.
        when(mMockClockProxy.elapsedRealtime()).thenReturn(TEST_VOIP_TRANSITORY_MILLIS + 1);
        mTestScheduledExecutorService.advanceTime(TEST_VOIP_TRANSITORY_MILLIS + 1);
    }

    /**
     * Emulate the case where a new outgoing VoIP call is added to the watchdog.
     * In this case, the timeout will fire in transitory state and should report an anomaly.
     */
    @Test
    public void testVoipPlaceCallTimeoutReportAnomaly() {
        // Call will start in connecting state
        Call call = setupCallHelper(CallState.CONNECTING, false, null, true, false);

        // Assume it is created but the app never sets it to a proper state
        call.setIsCreateConnectionComplete(false);
        mCallAnomalyWatchdog.onCallAdded(call);

        // Move the clock to fire the timeout.
        when(mMockClockProxy.elapsedRealtime()).thenReturn(TEST_VOIP_TRANSITORY_MILLIS + 1);
        mTestScheduledExecutorService.advanceTime(TEST_VOIP_TRANSITORY_MILLIS + 1);

        //Ensure an anomaly was reported
        verify(mAnomalyReporterAdapter).reportAnomaly(
                CallAnomalyWatchdog.WATCHDOG_DISCONNECTED_STUCK_CALL_UUID,
                CallAnomalyWatchdog.WATCHDOG_DISCONNECTED_STUCK_CALL_MSG);
    }

    /**
     * Emulate the case where a new outgoing VoIP emergency call is added to the watchdog.
     * In this case, the timeout will fire in transitory state and should report an emergency
     * anomaly.
     */
    @Test
    public void testVoipEmergencyPlaceCallTimeoutReportAnomaly() {
        // Call will start in connecting state
        Call call = setupCallHelper(CallState.CONNECTING, false, null, true, true);

        // Assume it is created but the app never sets it to a proper state
        call.setIsCreateConnectionComplete(false);
        mCallAnomalyWatchdog.onCallAdded(call);

        // Move the clock to fire the timeout.
        when(mMockClockProxy.elapsedRealtime()).
                thenReturn(TEST_VOIP_EMERGENCY_TRANSITORY_MILLIS + 1);
        mTestScheduledExecutorService.advanceTime(TEST_VOIP_EMERGENCY_TRANSITORY_MILLIS + 1);

        //Ensure an anomaly was reported
        verify(mAnomalyReporterAdapter).reportAnomaly(
                CallAnomalyWatchdog.WATCHDOG_DISCONNECTED_STUCK_EMERGENCY_CALL_UUID,
                CallAnomalyWatchdog.WATCHDOG_DISCONNECTED_STUCK_EMERGENCY_CALL_MSG);
    }

    /**
     * Emulate the case where a new outgoing VoIP emergency call is added to the watchdog.
     * In this case, the timeout will fire in transitory state.
     */
    @Test
    public void testVoipEmergencyPlaceCallTimeout() {
        // Call will start in connecting state
        Call call = setupCallHelper(CallState.CONNECTING, false, null, true, true);

        // Assume it is created but the app never sets it to a proper state
        call.setIsCreateConnectionComplete(false);
        mCallAnomalyWatchdog.onCallAdded(call);

        // Its transitory, so should schedule timeout.
        assertEquals(1, mTestScheduledExecutorService.getNumberOfScheduledRunnables());
        assertTrue(mTestScheduledExecutorService.
                isRunnableScheduledAtTime(TEST_VOIP_EMERGENCY_TRANSITORY_MILLIS));
        assertEquals(1, mCallAnomalyWatchdog.getNumberOfScheduledTimeouts());

        // Move the clock to fire the timeout.
        when(mMockClockProxy.elapsedRealtime()).
                thenReturn(TEST_VOIP_EMERGENCY_TRANSITORY_MILLIS + 1);
        mTestScheduledExecutorService.advanceTime(TEST_VOIP_EMERGENCY_TRANSITORY_MILLIS + 1);
    }

    /**
     * Emulate the case where a new outgoing non-VoIP call is added to the watchdog.
     * In this case, the timeout will fire in transitory state.
     */
    @Test
    public void testNonVoipPlaceCallTimeout() {
        // Call will start in connecting state
        Call call = setupCallHelper(CallState.CONNECTING, false, null, false, false);

        // Assume it is created but the app never sets it to a proper state
        call.setIsCreateConnectionComplete(false);
        mCallAnomalyWatchdog.onCallAdded(call);

        // Its transitory, so should schedule timeout.
        assertEquals(1, mTestScheduledExecutorService.getNumberOfScheduledRunnables());
        assertTrue(mTestScheduledExecutorService.
                isRunnableScheduledAtTime(TEST_NON_VOIP_TRANSITORY_MILLIS));
        assertEquals(1, mCallAnomalyWatchdog.getNumberOfScheduledTimeouts());

        // Move the clock to fire the timeout.
        when(mMockClockProxy.elapsedRealtime()).thenReturn(TEST_NON_VOIP_TRANSITORY_MILLIS + 1);
        mTestScheduledExecutorService.advanceTime(TEST_NON_VOIP_TRANSITORY_MILLIS + 1);
    }

    /**
     * Emulate the case where a new outgoing non-VoIP emergency call is added to the watchdog.
     * In this case, the timeout will fire in transitory state.
     */
    @Test
    public void testNonVoipEmergencyPlaceCallTimeout() {
        // Call will start in connecting state
        Call call = setupCallHelper(CallState.CONNECTING, false, null, false, true);

        // Assume it is created but the app never sets it to a proper state
        call.setIsCreateConnectionComplete(false);
        mCallAnomalyWatchdog.onCallAdded(call);

        // Its transitory, so should schedule timeout.
        assertEquals(1, mTestScheduledExecutorService.getNumberOfScheduledRunnables());
        assertTrue(mTestScheduledExecutorService.
                isRunnableScheduledAtTime(TEST_NON_VOIP_EMERGENCY_TRANSITORY_MILLIS));
        assertEquals(1, mCallAnomalyWatchdog.getNumberOfScheduledTimeouts());

        // Move the clock to fire the timeout.
        when(mMockClockProxy.elapsedRealtime()).
                thenReturn(TEST_NON_VOIP_EMERGENCY_TRANSITORY_MILLIS + 1);
        mTestScheduledExecutorService.
                advanceTime(TEST_NON_VOIP_EMERGENCY_TRANSITORY_MILLIS + 1);
    }

    /**
     * Emulate the case where a new incoming call is created but the connection fails for a known
     * reason before being added to CallsManager. In this case, the watchdog should stop tracking
     * the call and not trigger an anomaly report.
     */
    @Test
    public void testIncomingCallCreatedButNotAddedNoAnomalyReport() {
        //The call is created:
        Call call = getCall();
        call.setState(CallState.NEW, "foo");
        call.setIsCreateConnectionComplete(false);
        mCallAnomalyWatchdog.onStartCreateConnection(call);

        //The connection fails before being added to CallsManager for a known reason:
        call.handleCreateConnectionFailure(new DisconnectCause(DisconnectCause.CANCELED));

        // Move the clock forward:
        when(mMockClockProxy.elapsedRealtime()).
                thenReturn(TEST_NON_VOIP_INTERMEDIATE_MILLIS + 1);
        mTestScheduledExecutorService.advanceTime(TEST_NON_VOIP_INTERMEDIATE_MILLIS + 1);

        //Ensure an anomaly report is not generated:
        verify(mAnomalyReporterAdapter, never()).reportAnomaly(
                CallAnomalyWatchdog.WATCHDOG_DISCONNECTED_STUCK_CALL_UUID,
                CallAnomalyWatchdog.WATCHDOG_DISCONNECTED_STUCK_CALL_MSG);
    }

    /**
     * Emulate the case where a new outgoing call is created but the connection fails for a known
     * reason before being added to CallsManager. In this case, the watchdog should stop tracking
     * the call and not trigger an anomaly report.
     */
    @Test
    public void testOutgoingCallCreatedButNotAddedNoAnomalyReport() {
        //The call is created:
        Call call = getCall();
        call.setCallDirection(Call.CALL_DIRECTION_OUTGOING);
        call.setState(CallState.NEW, "foo");
        call.setIsCreateConnectionComplete(false);
        mCallAnomalyWatchdog.onStartCreateConnection(call);

        //The connection fails before being added to CallsManager for a known reason.
        call.handleCreateConnectionFailure(new DisconnectCause(DisconnectCause.CANCELED));

        // Move the clock forward:
        when(mMockClockProxy.elapsedRealtime()).
                thenReturn(TEST_NON_VOIP_INTERMEDIATE_MILLIS + 1);
        mTestScheduledExecutorService.advanceTime(TEST_NON_VOIP_INTERMEDIATE_MILLIS + 1);

        //Ensure an anomaly report is not generated:
        verify(mAnomalyReporterAdapter, never()).reportAnomaly(
                CallAnomalyWatchdog.WATCHDOG_DISCONNECTED_STUCK_CALL_UUID,
                CallAnomalyWatchdog.WATCHDOG_DISCONNECTED_STUCK_CALL_MSG);
    }

    /**
     * Emulate the case where a new incoming call is created but the connection fails for a known
     * reason before being added to CallsManager and CallsManager notifies the watchdog by invoking
     * {@link CallsManager.CallsManagerListener#onCreateConnectionFailed(Call)}.
     * In this case, the watchdog should stop tracking the call and not trigger an anomaly report.
     */
    @Test
    public void testCallCreatedButNotAddedPreventsAnomalyReport() {
        //The call is created:
        Call call = getCall();
        call.setState(CallState.NEW, "foo");
        call.setIsCreateConnectionComplete(false);
        mCallAnomalyWatchdog.onStartCreateConnection(call);

        //Telecom cancels the connection before adding it to CallsManager:
        mCallAnomalyWatchdog.onCreateConnectionFailed(call);

        // Move the clock forward:
        when(mMockClockProxy.elapsedRealtime()).
                thenReturn(TEST_NON_VOIP_INTERMEDIATE_MILLIS + 1);
        mTestScheduledExecutorService.advanceTime(TEST_NON_VOIP_INTERMEDIATE_MILLIS + 1);

        //Ensure an anomaly report is not generated:
        verify(mAnomalyReporterAdapter, never()).reportAnomaly(
                CallAnomalyWatchdog.WATCHDOG_DISCONNECTED_STUCK_CALL_UUID,
                CallAnomalyWatchdog.WATCHDOG_DISCONNECTED_STUCK_CALL_MSG);
    }


    /**
     * @return an instance of {@link Call} for testing purposes.
     */
    private Call getCall() {
        return new Call(
                "1", /* callId */
                mContext,
                mMockCallsManager,
                mLock,
                null /* ConnectionServiceRepository */,
                mMockPhoneNumberUtilsAdapter,
                Uri.parse("tel:6505551212"),
                null /* GatewayInfo */,
                null /* connectionManagerPhoneAccountHandle */,
                SIM_1_HANDLE,
                Call.CALL_DIRECTION_INCOMING,
                false /* shouldAttachToExistingConnection*/,
                false /* isConference */,
                mMockClockProxy,
                mMockToastProxy,
                mFeatureFlags);
    }
}