/*
 * Copyright (C) 2023 The Android Open Source Project
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


import static android.telephony.TelephonyManager.EmergencyCallDiagnosticData;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.net.Uri;
import android.os.BugreportManager;
import android.os.DropBoxManager;
import android.os.UserHandle;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telephony.TelephonyManager;

import com.android.server.telecom.Call;
import com.android.server.telecom.CallState;
import com.android.server.telecom.CallerInfoLookupHelper;
import com.android.server.telecom.CallsManager;
import com.android.server.telecom.ClockProxy;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(JUnit4.class)
public class EmergencyCallDiagnosticLoggerTest extends TelecomTestCase {

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
    private static final String DROP_BOX_TAG = "ecall_diagnostic_data";

    private static final long EMERGENCY_CALL_ACTIVE_TIME_THRESHOLD_MILLIS = 100L;

    private static final long EMERGENCY_CALL_TIME_BEFORE_USER_DISCONNECT_THRESHOLD_MILLIS = 120L;

    private static final int DAYS_BACK_TO_SEARCH_EMERGENCY_DIAGNOSTIC_ENTRIES = 1;
    private final TelecomSystem.SyncRoot mLock = new TelecomSystem.SyncRoot() {
    };
    EmergencyCallDiagnosticLogger mEmergencyCallDiagnosticLogger;
    @Mock
    private Timeouts.Adapter mTimeouts;
    @Mock
    private CallsManager mMockCallsManager;
    @Mock
    private CallerInfoLookupHelper mMockCallerInfoLookupHelper;
    @Mock
    private PhoneAccountRegistrar mMockPhoneAccountRegistrar;
    @Mock
    private ClockProxy mMockClockProxy;
    @Mock
    private ToastFactory mMockToastProxy;
    @Mock
    private PhoneNumberUtilsAdapter mMockPhoneNumberUtilsAdapter;

    @Mock
    private TelephonyManager mTm;
    @Mock
    private BugreportManager mBrm;
    @Mock
    private DropBoxManager mDbm;

    @Mock
    private ClockProxy mClockProxy;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        doReturn(mMockCallerInfoLookupHelper).when(mMockCallsManager).getCallerInfoLookupHelper();
        doReturn(mMockPhoneAccountRegistrar).when(mMockCallsManager).getPhoneAccountRegistrar();
        doReturn(SIM_1_ACCOUNT).when(mMockPhoneAccountRegistrar).getPhoneAccountUnchecked(
                eq(SIM_1_HANDLE));
        when(mTimeouts.getEmergencyCallActiveTimeThresholdMillis()).
                thenReturn(EMERGENCY_CALL_ACTIVE_TIME_THRESHOLD_MILLIS);
        when(mTimeouts.getEmergencyCallTimeBeforeUserDisconnectThresholdMillis()).
                thenReturn(EMERGENCY_CALL_TIME_BEFORE_USER_DISCONNECT_THRESHOLD_MILLIS);
        when(mTimeouts.getDaysBackToSearchEmergencyDiagnosticEntries()).
                thenReturn(DAYS_BACK_TO_SEARCH_EMERGENCY_DIAGNOSTIC_ENTRIES);
        when(mClockProxy.currentTimeMillis()).thenReturn(System.currentTimeMillis());
        when(mMockCallsManager.getCurrentUserHandle()).thenReturn(UserHandle.CURRENT);

        mEmergencyCallDiagnosticLogger = new EmergencyCallDiagnosticLogger(mTm, mBrm,
                mTimeouts, mDbm, Runnable::run, mClockProxy);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        //reset(mTm);
    }

    /**
     * Helper function that creates the call being tested.
     * Also invokes onStartCreateConnection
     */
    private Call createCall(boolean isEmergencyCall, int direction) {
        Call call = getCall();
        call.setCallDirection(direction);
        call.setIsEmergencyCall(isEmergencyCall);
        mEmergencyCallDiagnosticLogger.onStartCreateConnection(call);
        return call;
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
                Call.CALL_DIRECTION_OUTGOING,
                false /* shouldAttachToExistingConnection*/,
                false /* isConference */,
                mMockClockProxy,
                mMockToastProxy,
                mFeatureFlags);
    }

    /**
     * Test that only outgoing emergency calls are tracked
     */
    @Test
    public void testNonEmergencyCallNotTracked() {
        //should not be tracked
        createCall(false, Call.CALL_DIRECTION_OUTGOING);
        assertEquals(0, mEmergencyCallDiagnosticLogger.getEmergencyCallsMap().size());

        //should not be tracked (not in scope)
        createCall(false, Call.CALL_DIRECTION_INCOMING);
        assertEquals(0, mEmergencyCallDiagnosticLogger.getEmergencyCallsMap().size());
    }

    /**
     * Test that incoming emergency calls are not tracked (not in scope right now)
     */
    @Test
    public void testIncomingEmergencyCallsNotTracked() {
        //should not be tracked
        createCall(true, Call.CALL_DIRECTION_INCOMING);
        assertEquals(0, mEmergencyCallDiagnosticLogger.getEmergencyCallsMap().size());
    }


    /**
     * Test getDataCollectionTypes(reason)
     */
    @Test
    public void testCollectionTypeForReasonDoesNotReturnUnreasonableValues() {
        int reason = EmergencyCallDiagnosticLogger.REPORT_REASON_RANGE_START + 1;
        while (reason < EmergencyCallDiagnosticLogger.REPORT_REASON_RANGE_END) {
            List<Integer> ctypes = EmergencyCallDiagnosticLogger.getDataCollectionTypes(reason);
            assertNotNull(ctypes);
            Set<Integer> ctypesSet = new HashSet<>(ctypes);

            //assert that list is not empty
            assertNotEquals(0, ctypes.size());

            //assert no repeated values
            assertEquals(ctypes.size(), ctypesSet.size());

            //if bugreport type is present, that should be the only collection type
            if (ctypesSet.contains(EmergencyCallDiagnosticLogger.COLLECTION_TYPE_BUGREPORT)) {
                assertEquals(1, ctypes.size());
            }
            reason++;
        }
    }


    /**
     * Test emergency call reported stuck
     */
    @Test
    public void testStuckEmergencyCall() {
        Call call = createCall(true, Call.CALL_DIRECTION_OUTGOING);
        mEmergencyCallDiagnosticLogger.onCallAdded(call);
        mEmergencyCallDiagnosticLogger.reportStuckCall(call);

        //for stuck calls, we should always be persisting some data
        ArgumentCaptor<EmergencyCallDiagnosticData> captor =
                ArgumentCaptor.forClass(EmergencyCallDiagnosticData.class);
        verify(mTm, times(1)).persistEmergencyCallDiagnosticData(eq(DROP_BOX_TAG),
                captor.capture());
        EmergencyCallDiagnosticData ecdData = captor.getValue();

        assertNotNull(ecdData);
        assertTrue(
                ecdData.isLogcatCollectionEnabled() || ecdData.isTelecomDumpsysCollectionEnabled()
                        || ecdData.isTelephonyDumpsysCollectionEnabled());

        //tracking should end
        assertEquals(0, mEmergencyCallDiagnosticLogger.getEmergencyCallsMap().size());
    }

    @Test
    public void testEmergencyCallNeverWentActiveWithNonLocalDisconnectCause() {
        Call call = createCall(true, Call.CALL_DIRECTION_OUTGOING);
        mEmergencyCallDiagnosticLogger.onCallAdded(call);

        //call is tracked
        assertEquals(1, mEmergencyCallDiagnosticLogger.getEmergencyCallsMap().size());

        call.setDisconnectCause(new DisconnectCause(DisconnectCause.REJECTED));
        mEmergencyCallDiagnosticLogger.onCallRemoved(call);

        //for non-local disconnect of non-active call,  we should always be persisting some data
        ArgumentCaptor<EmergencyCallDiagnosticData> captor =
                ArgumentCaptor.forClass(EmergencyCallDiagnosticData.class);
        verify(mTm, times(1)).persistEmergencyCallDiagnosticData(eq(DROP_BOX_TAG),
                captor.capture());
        EmergencyCallDiagnosticData ecdData = captor.getValue();

        assertNotNull(ecdData);
        assertTrue(
                ecdData.isLogcatCollectionEnabled() || ecdData.isTelecomDumpsysCollectionEnabled()
                        || ecdData.isTelephonyDumpsysCollectionEnabled());

        //tracking should end
        assertEquals(0, mEmergencyCallDiagnosticLogger.getEmergencyCallsMap().size());
    }

    @Test
    public void testEmergencyCallWentActiveForLongDuration_shouldNotCollectDiagnostics()
            throws Exception {
        Call call = createCall(true, Call.CALL_DIRECTION_OUTGOING);
        mEmergencyCallDiagnosticLogger.onCallAdded(call);

        //call went active
        mEmergencyCallDiagnosticLogger.onCallStateChanged(call, CallState.DIALING,
                CallState.ACTIVE);

        //return large value for time when call is disconnected
        when(mClockProxy.currentTimeMillis()).thenReturn(System.currentTimeMillis() + 10000L);

        call.setDisconnectCause(new DisconnectCause(DisconnectCause.ERROR));
        mEmergencyCallDiagnosticLogger.onCallRemoved(call);

        //no diagnostic data should be persisted
        verify(mTm, never()).persistEmergencyCallDiagnosticData(eq(DROP_BOX_TAG),
                any());

        //tracking should end
        assertEquals(0, mEmergencyCallDiagnosticLogger.getEmergencyCallsMap().size());
    }

}
