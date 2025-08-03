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

package android.telecom.cts.cuj.app.integration;

import static android.telecom.Call.STATE_ACTIVE;
import static android.telecom.Call.STATE_DISCONNECTED;
import static android.telecom.Call.STATE_HOLDING;
import static android.telecom.Call.STATE_RINGING;
import static android.telecom.cts.apps.TelecomTestApp.ConnectionServiceVoipAppMain;
import static android.telecom.cts.apps.TelecomTestApp.ManagedConnectionServiceApp;
import static android.telecom.cts.apps.TelecomTestApp.TransactionalVoipAppMain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.net.Uri;
import android.os.Bundle;
import android.os.OutcomeReceiver;
import android.telecom.CallAttributes;
import android.telecom.CallControlCallback;
import android.telecom.CallEventCallback;
import android.telecom.CallException;
import android.telecom.Connection;
import android.telecom.cts.apps.AppControlWrapper;
import android.telecom.cts.cuj.BaseAppVerifier;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * This class should test scenarios where an app declares a call as unholdable (ex. missing the
 * proper capabilities) but calls a telecom API that attempts to place the call to the HOLDING
 * state.
 */
@RunWith(JUnit4.class)
public class UnholdableCallTest extends BaseAppVerifier {

    /*********************************************************************************************
     *                               Single Call
     /*********************************************************************************************/

    /**
     * Test the scenario where a managed call that declares it cannot be held requests Telecom to
     * place the call on hold.
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a managed call that is backed by a {@link android.telecom.ConnectionService }
     * <p>
     *  2. remove the {@link android.telecom.Connection#CAPABILITY_HOLD} and
     *                 {@link android.telecom.Connection#CAPABILITY_SUPPORT_HOLD} capabilities
     * <p>
     *  3. request the call be placed on hold via {@link Connection#setOnHold()}
     *  </ul>
     *  Assert the call to remains active.
     */
    @Test
    public void testSetHoldOnUnholdableCall_ManagedApp() throws Exception {
        if (!mShouldTestTelecom || S_IS_TEST_DISABLED) {
            return;
        }
        AppControlWrapper csApp = null;
        try {
            csApp = bindToApp(ManagedConnectionServiceApp);
            verifyHoldFails_ConnectionService(csApp);
        } finally {
            tearDownApp(csApp);
        }
    }

    /**
     * Test the scenario where a self-managed call that declares it cannot be held requests Telecom
     * to place the call on hold.
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a managed call that is backed by a {@link android.telecom.ConnectionService }
     * <p>
     *  2. remove the {@link android.telecom.Connection#CAPABILITY_HOLD} and
     *                 {@link android.telecom.Connection#CAPABILITY_SUPPORT_HOLD} capabilities
     * <p>
     *  3. request the call be placed on hold via {@link Connection#setOnHold()}
     *  </ul>
     *  Assert the call to remains active.
     */
    @Test
    public void testSetHoldOnUnholdableCall_ConnectionServiceVoipAppMain() throws Exception {
        if (!mShouldTestTelecom || S_IS_TEST_DISABLED) {
            return;
        }
        AppControlWrapper csApp = null;
        try {
            csApp = bindToApp(ConnectionServiceVoipAppMain);
            verifyHoldFails_ConnectionService(csApp);
        } finally {
            tearDownApp(csApp);
        }
    }

    /**
     * Test the scenario where a transactional call that declares it cannot be held requests Telecom
     * to place the call on hold.
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create {@link CallAttributes} without the {@link CallAttributes#SUPPORTS_SET_INACTIVE}
     *  capability.
     * <p>
     *  2. create a transactional call via
     *  {@link android.telecom.TelecomManager#addCall(CallAttributes,
     *                                                Executor,
     *                                                OutcomeReceiver,
     *                                                CallControlCallback,
     *                                                CallEventCallback)}
     * <p>
     *  3. request the call be placed on hold via {@link android.telecom.CallControl#setInactive(
     *Executor, OutcomeReceiver)}
     *  </ul>
     *  Assert the {@link android.telecom.CallControl#setInactive(Executor, OutcomeReceiver)}
     *  fails via the {@link OutcomeReceiver#onError(Throwable)}. The call should remain active.
     */
    @Test
    public void testSetHoldOnUnholdableCall_TransactionalApp() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper transactionalApp = null;
        try {
            transactionalApp = bindToApp(TransactionalVoipAppMain);
            String mo = addOutgoingCallAndVerify(transactionalApp, false /* isHoldable */);
            setCallStateAndVerify(transactionalApp, mo, STATE_ACTIVE);
            CallException e = setCallStateButExpectOnError(transactionalApp, mo, STATE_HOLDING);
            assertNotNull(e);
            assertEquals(CallException.CODE_CANNOT_HOLD_CURRENT_ACTIVE_CALL, e.getCode());
            verifyCallIsInState(mo, STATE_ACTIVE);
            setCallStateAndVerify(transactionalApp, mo, STATE_DISCONNECTED);
        } finally {
            tearDownApp(transactionalApp);
        }
    }

    /*********************************************************************************************
     *                               Multiple Calls, Same App
     /*********************************************************************************************/


    /**
     * Test the scenario where a transactional service creates 2 calls that cannot be held resulting
     * in a failure to set the second call active.
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create an outgoing and incoming call via {@link CallAttributes}s without the
     *  {@link CallAttributes#SUPPORTS_SET_INACTIVE} capability.
     * <p>
     *  2. add the outgoing transactional call via
     *  {@link android.telecom.TelecomManager#addCall(CallAttributes,
     *                                                Executor,
     *                                                OutcomeReceiver,
     *                                                CallControlCallback,
     *                                                CallEventCallback)}
     * <p>
     *  3. set the outgoing call to active via {@link android.telecom.CallControl#setActive(
     *Executor, OutcomeReceiver)}
     * <p>
     *  4. add the incoming transactional call via
     *  {@link android.telecom.TelecomManager#addCall(CallAttributes,
     *                                                Executor,
     *                                                OutcomeReceiver,
     *                                                CallControlCallback,
     *                                                CallEventCallback)}
     * <p>
     *  5. attempt to answer the incoming call via {@link android.telecom.CallControl#answer(int,
     *  Executor, OutcomeReceiver)}
     *  </ul>
     *  Assert the {@link android.telecom.CallControl#answer(int, Executor, OutcomeReceiver)}
     *  fails via the {@link OutcomeReceiver#onError(Throwable)}. The first call should remain
     *  active and the second call should be in the RINGING state.
     */
    @Test
    public void testAnswerIncomingWithOngoingUnholdableCall_TransactionalApp() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper transactionalApp = null;
        try {
            transactionalApp = bindToApp(TransactionalVoipAppMain);
            String mo = addOutgoingCallAndVerify(transactionalApp, false /* isHoldable */);
            setCallStateAndVerify(transactionalApp, mo, STATE_ACTIVE);
            String mt = addIncomingCallAndVerify(transactionalApp, false /* isHoldable */);

            CallException e = setCallStateButExpectOnError(transactionalApp, mt, STATE_ACTIVE);
            assertNotNull(e);
            // assertEquals(
            //              CallException.CODE_CANNOT_HOLD_CURRENT_ACTIVE_CALL,
            //               e.getCode());  TODO::b/313461479
            assertEquals(CallException.CODE_ERROR_UNKNOWN, e.getCode());
            verifyCallIsInState(mo, STATE_ACTIVE);

            setCallStateAndVerify(transactionalApp, mt, STATE_DISCONNECTED);
            setCallStateAndVerify(transactionalApp, mo, STATE_DISCONNECTED);
        } finally {
            tearDownApp(transactionalApp);
        }
    }

    /**
     * Test the scenario where a {@link android.telecom.ConnectionService} creates 2 self-managed
     * calls that cannot be held resulting in a failure to set the second call active.
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create an outgoing {@link android.telecom.Connection} via
     *  {@link android.telecom.TelecomManager#placeCall(Uri, Bundle)}
     * <p>
     *  2. remove the {@link android.telecom.Connection#CAPABILITY_HOLD} and
     *                 {@link android.telecom.Connection#CAPABILITY_SUPPORT_HOLD} capabilities
     * <p>
     *  3. set the outgoing {@link Connection} to active via {@link Connection#setActive()}
     * <p>
     * 4. create an incoming {@link android.telecom.Connection} via
     *  {@link android.telecom.TelecomManager#placeCall(Uri, Bundle)}
     * <p>
     *  5. attempt to answer the incoming call via {@link Connection#setActive()}
     *  </ul>
     *  Assert the outgoing call is still active and the incoming call is still in the RINGING
     *  state.
     */
    @Test
    public void testAnswerIncomingWithOngoingUnholdableCall_ConnectionServiceVoipAppMain()
            throws Exception {
        if (!mShouldTestTelecom || S_IS_TEST_DISABLED) {
            return;
        }
        AppControlWrapper csApp = null;
        try {
            csApp = bindToApp(ConnectionServiceVoipAppMain);
            String mo = addOutgoingCallAndVerify(csApp, false /* isHoldable */);
            setCallStateAndVerify(csApp, mo, STATE_ACTIVE);
            String mt = addIncomingCallAndVerify(csApp, false /* isHoldable */);

            setCallState(csApp, mt, STATE_ACTIVE);
            verifyCallIsInState(mt, STATE_RINGING);
        } finally {
            tearDownApp(csApp);
        }
    }


    /*********************************************************************************************
     *                               Multiple Apps
     /*********************************************************************************************/


    /**
     * Test the scenario where a {@link android.telecom.ConnectionService} creates a self-managed
     * call that cannot be held and a new incoming transactional call attempts to be answered.
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create an outgoing {@link android.telecom.Connection} via
     *  {@link android.telecom.TelecomManager#placeCall(Uri, Bundle)}
     * <p>
     *  2. remove the {@link android.telecom.Connection#CAPABILITY_HOLD} and
     *                 {@link android.telecom.Connection#CAPABILITY_SUPPORT_HOLD} capabilities
     * <p>
     *  3. set the outgoing {@link Connection} to active via {@link Connection#setActive()}
     * <p>
     *  4. add the incoming transactional call via
     *  {@link android.telecom.TelecomManager#addCall(CallAttributes,
     *                                                Executor,
     *                                                OutcomeReceiver,
     *                                                CallControlCallback,
     *                                                CallEventCallback)}
     * <p>
     *  5. attempt to answer the incoming call via {@link android.telecom.CallControl#answer(int,
     *  Executor, OutcomeReceiver)}
     *  </ul>
     *  Assert the {@link android.telecom.CallControl#answer(int, Executor, OutcomeReceiver)}
     *  fails via the {@link OutcomeReceiver#onError(Throwable)}. The first call should remain
     *  active and the second call should be in the RINGING state.
     */
    @Test
    public void testAnswerIncomingWithOngoingUnholdable_ManagedTransactionalApp() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper managedApp = null;
        AppControlWrapper transactionalApp = null;
        try {
            managedApp = bindToApp(ManagedConnectionServiceApp);
            String mo = addOutgoingCallAndVerify(managedApp, false /* isHoldable */);
            setCallStateAndVerify(managedApp, mo, STATE_ACTIVE);

            transactionalApp = bindToApp(TransactionalVoipAppMain);
            String mt = addIncomingCallAndVerify(transactionalApp, false /* isHoldable */);

            CallException e = setCallStateButExpectOnError(transactionalApp, mt, STATE_ACTIVE);
            assertNotNull(e);
            // assertEquals(
            //              CallException.CODE_CANNOT_HOLD_CURRENT_ACTIVE_CALL,
            //               e.getCode());  TODO::b/313461479
            assertEquals(CallException.CODE_ERROR_UNKNOWN, e.getCode());
            verifyCallIsInState(mo, STATE_ACTIVE);

            setCallStateAndVerify(transactionalApp, mt, STATE_DISCONNECTED);
            setCallStateAndVerify(managedApp, mo, STATE_DISCONNECTED);
        } finally {
            List<AppControlWrapper> controls = new ArrayList<>();
            controls.add(transactionalApp);
            controls.add(managedApp);
            tearDownApps(controls);
        }
    }

    /*********************************************************************************************
     *                           Helpers
     /*********************************************************************************************/

    private void verifyHoldFails_ConnectionService(AppControlWrapper appControlWrapper)
            throws Exception {
        String mo = addOutgoingCallAndVerify(appControlWrapper, false /* isHoldable */);
        setCallStateAndVerify(appControlWrapper, mo, STATE_ACTIVE);
        setCallState(appControlWrapper, mo, STATE_HOLDING);
        verifyCallIsInState(mo, STATE_ACTIVE); // TODO:: b/313461258
        setCallStateAndVerify(appControlWrapper, mo, STATE_DISCONNECTED);
    }
}
