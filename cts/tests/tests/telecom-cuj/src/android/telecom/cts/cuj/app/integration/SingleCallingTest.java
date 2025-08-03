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
import static android.telecom.Call.STATE_CONNECTING;
import static android.telecom.Call.STATE_DIALING;
import static android.telecom.Call.STATE_DISCONNECTED;
import static android.telecom.Call.STATE_HOLDING;
import static android.telecom.Call.STATE_RINGING;
import static android.telecom.cts.apps.TelecomTestApp.ConnectionServiceVoipAppClone;
import static android.telecom.cts.apps.TelecomTestApp.ConnectionServiceVoipAppMain;
import static android.telecom.cts.apps.TelecomTestApp.ManagedConnectionServiceApp;
import static android.telecom.cts.apps.TelecomTestApp.TransactionalVoipAppClone;
import static android.telecom.cts.apps.TelecomTestApp.TransactionalVoipAppMain;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.net.Uri;
import android.os.Bundle;
import android.os.OutcomeReceiver;
import android.telecom.CallAttributes;
import android.telecom.CallControlCallback;
import android.telecom.CallEndpoint;
import android.telecom.CallEventCallback;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;
import android.telecom.VideoProfile;
import android.telecom.cts.apps.AppControlWrapper;
import android.telecom.cts.cuj.BaseAppVerifier;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * This test class should test common calling scenarios that involve only a single application
 */
@RunWith(JUnit4.class)
public class SingleCallingTest extends BaseAppVerifier {

    /*********************************************************************************************
     *                                ManagedConnectionServiceApp
     /*********************************************************************************************/

    /**
     * Test the scenario where a new managed outgoing call is created and transitions to the ACTIVE
     * and DISCONNECTED states.
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a managed call that is backed by a {@link android.telecom.ConnectionService }
     *  via {@link android.telecom.TelecomManager#placeCall(Uri, Bundle)}
     * <p>
     *  2. transition the call to ACTIVE via {@link Connection#setActive()}
     * <p>
     *  3.  transition the call to DISCONNECTED via
     *  {@link Connection#setDisconnected(DisconnectCause)} ()}
     *  </ul>
     *  Assert the call was successfully added and transitioned to the ACTIVE state without errors
     */
    @Test
    public void testOutgoingCall_ManagedConnectionServiceApp() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper managedApp = null;
        try {
            managedApp = bindToApp(ManagedConnectionServiceApp);
            verifyOutgoingCallStateTransitions(managedApp);
        } finally {
            tearDownApp(managedApp);
        }
    }

    /**
     * Test the scenario where a new MANAGED incoming call is created and transitions to the ACTIVE
     * and DISCONNECTED states.
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a managed call that is backed by a {@link android.telecom.ConnectionService }
     *  via {@link android.telecom.TelecomManager#addNewIncomingCall(PhoneAccountHandle, Bundle)}
     * <p>
     *  2. transition the call to ACTIVE via {@link Connection#setActive()}
     * <p>
     *  3.  transition the call to DISCONNECTED via
     *  {@link Connection#setDisconnected(DisconnectCause)} ()}
     *  </ul>
     *  Assert the call was successfully added and transitioned to the ACTIVE state without errors
     */
    @Test
    public void testIncomingCall_ManagedConnectionServiceApp() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper managedApp = null;
        try {
            managedApp = bindToApp(ManagedConnectionServiceApp);
            String mt = addIncomingCallAndVerify(managedApp);
            verifyCallIsInState(mt, STATE_RINGING);
            answerViaInCallServiceAndVerify(mt, VideoProfile.STATE_AUDIO_ONLY);
            setCallStateAndVerify(managedApp, mt, STATE_HOLDING);
            setCallStateAndVerify(managedApp, mt, STATE_DISCONNECTED);
        } finally {
            tearDownApp(managedApp);
        }
    }

    /**
     * Test the scenario where an ongoing MANAGED call requests to change the mute state from
     * unmuted to muted multiple times.
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a managed call that is backed by a {@link android.telecom.ConnectionService }
     *  via {@link android.telecom.TelecomManager#addNewIncomingCall(PhoneAccountHandle, Bundle)}
     * <p>
     *  2. transition the call to ACTIVE via {@link Connection#setActive()}
     * <p>
     *  3. request mute by passing {@link Boolean#TRUE} to
     *  {@link Connection#onMuteStateChanged(boolean)}
     * <p>
     *  4. request mute by passing {@link Boolean#FALSE} to
     *  {@link Connection#onMuteStateChanged(boolean)}
     *  </ul>
     *  Assert the call was successfully muted and unmuted without errors.
     */
    @Test
    public void testToggleMuteState_ManagedConnectionServiceApp() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper managedApp = null;
        try {
            managedApp = bindToApp(ManagedConnectionServiceApp);
            verifyToggleMute(managedApp);
        } finally {
            tearDownApp(managedApp);
        }
    }

    /**
     * Test a client application can determine the current audio route also known as the
     * current {@link android.telecom.CallEndpoint}
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a managed call that is backed by a {@link android.telecom.ConnectionService }
     *  via {@link android.telecom.TelecomManager#addNewIncomingCall(PhoneAccountHandle, Bundle)}
     * <p>
     *  2. collect the current {@link CallEndpoint}
     *  {@link android.telecom.CallEventCallback#onCallEndpointChanged(CallEndpoint)}
     *  </ul>
     *  Assert the current {@link CallEndpoint} is non-null.
     */
    @Test
    public void testGetCurrentEndpoint_ManagedConnectionServiceApp() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper managedApp = null;
        try {
            managedApp = bindToApp(ManagedConnectionServiceApp);
            verifyGetCurrentEndpoint(managedApp);
        } finally {
            tearDownApp(managedApp);
        }
    }

    /**
     * Test a client application can determine all the available audio routes also known as the
     * available {@link android.telecom.CallEndpoint}s
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a managed call that is backed by a {@link android.telecom.ConnectionService }
     *  via {@link android.telecom.TelecomManager#addNewIncomingCall(PhoneAccountHandle, Bundle)}
     * <p>
     *  2. collect the current {@link CallEndpoint}
     *  {@link android.telecom.CallEventCallback#onAvailableCallEndpointsChanged(List)}
     *  </ul>
     *  Assert the available {@link CallEndpoint}s are non-null.
     */
    @Test
    public void testAvailableEndpoints_ManagedConnectionServiceApp() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper managedApp = null;
        try {
            managedApp = bindToApp(ManagedConnectionServiceApp);
            verifyGetAvailableEndpoints(managedApp);
        } finally {
            tearDownApp(managedApp);
        }
    }

    /**
     * Test the scenario where a client application requests to switch the current {@link
     * android.telecom.CallEndpoint}
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a managed call that is backed by a {@link android.telecom.ConnectionService }
     *  via {@link android.telecom.TelecomManager#addNewIncomingCall(PhoneAccountHandle, Bundle)}
     * <p>
     *  2. collect the current {@link CallEndpoint} via
     *  {@link android.telecom.CallEventCallback#onCallEndpointChanged(CallEndpoint)}
     *  <p>
     *  3.  collect the available {@link CallEndpoint}s via
     *   {@link android.telecom.CallEventCallback#onAvailableCallEndpointsChanged(List)}
     *  <p>
     *  4. find another endpoint that is not the current endpoint and request an audio endpoint
     *  switch via {@link android.telecom.CallControl#requestCallEndpointChange(CallEndpoint,
     *                                                                Executor,
     *                                                                OutcomeReceiver)}
     *  <p>
     *  </ul>
     *  Assert the current {@link CallEndpoint} is switched successfully
     */
    @Test
    public void testBasicAudioSwitchTest_ManagedConnectionServiceApp() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper managedApp = null;
        try {
            managedApp = bindToApp(ManagedConnectionServiceApp);
            verifySwitchEndpoints(managedApp);
        } finally {
            tearDownApp(managedApp);
        }
    }

    /*********************************************************************************************
     *                           ConnectionServiceVoipAppMain
     /*********************************************************************************************/

    /**
     * Test the scenario where a new SELF-MANAGED outgoing call is created and transitions to the
     * ACTIVE and DISCONNECTED states.
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a self-managed call that is backed by a {@link android.telecom.ConnectionService }
     *  via {@link android.telecom.TelecomManager#placeCall(Uri, Bundle)}
     * <p>
     *  2. transition the call to ACTIVE via {@link Connection#setActive()}
     * <p>
     *  3.  transition the call to DISCONNECTED via
     *  {@link Connection#setDisconnected(DisconnectCause)} ()}
     *  </ul>
     *  Assert the call was successfully added and transitioned to the ACTIVE state without errors
     */
    @Test
    public void testOutgoingCall_ConnectionServiceVoipAppMain() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper voipCsApp = null;

        try {
            voipCsApp = bindToApp(ConnectionServiceVoipAppMain);
            verifyOutgoingCallStateTransitions(voipCsApp);
        } finally {
            tearDownApp(voipCsApp);
        }
    }

    /**
     * Test the scenario where a new SELF_MANAGED incoming call is created and transitions to the
     * ACTIVE and DISCONNECTED states.
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a managed call that is backed by a {@link android.telecom.ConnectionService }
     *  via {@link android.telecom.TelecomManager#addNewIncomingCall(PhoneAccountHandle, Bundle)}
     * <p>
     *  2. transition the call to ACTIVE via {@link Connection#setActive()}
     * <p>
     *  3.  transition the call to DISCONNECTED via
     *  {@link Connection#setDisconnected(DisconnectCause)} ()}
     *  </ul>
     *  Assert the call was successfully added and transitioned to the ACTIVE state without errors
     */
    @Test
    public void testIncomingCall_ConnectionServiceVoipAppMain() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper voipCsApp = null;

        try {
            voipCsApp = bindToApp(ConnectionServiceVoipAppMain);
            verifyIncomingCallStateTransitions(voipCsApp);
        } finally {
            tearDownApp(voipCsApp);
        }
    }

    /**
     * Test the scenario where an ongoing SELF-MANAGED call requests to change the mute state from
     * unmuted to muted multiple times.
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a self-managed call that is backed by a {@link android.telecom.ConnectionService }
     *  via {@link android.telecom.TelecomManager#addNewIncomingCall(PhoneAccountHandle, Bundle)}
     * <p>
     *  2. transition the call to ACTIVE via {@link Connection#setActive()}
     * <p>
     *  3. request mute by passing {@link Boolean#TRUE} to
     *  {@link Connection#onMuteStateChanged(boolean)}
     * <p>
     *  4. request mute by passing {@link Boolean#FALSE} to
     *  {@link Connection#onMuteStateChanged(boolean)}
     *  </ul>
     *  Assert the call was successfully muted and unmuted without errors.
     */
    @Test
    public void testToggleMuteState_ConnectionServiceVoipAppMain() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper voipCs = null;
        try {
            voipCs = bindToApp(ConnectionServiceVoipAppMain);
            verifyToggleMute(voipCs);
        } finally {
            tearDownApp(voipCs);
        }
    }

    /**
     * Test a client application can determine the current audio route also known as the
     * current {@link android.telecom.CallEndpoint}
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a self-managed call that is backed by a {@link android.telecom.ConnectionService }
     *  via {@link android.telecom.TelecomManager#addNewIncomingCall(PhoneAccountHandle, Bundle)}
     * <p>
     * <p>
     *  2. collect the current {@link CallEndpoint}
     *  {@link android.telecom.CallEventCallback#onCallEndpointChanged(CallEndpoint)}
     *  </ul>
     *  Assert the current {@link CallEndpoint} is non-null.
     */
    @Test
    public void testGetCurrentEndpoint_ConnectionServiceVoipAppMain() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper voipCs = null;
        try {
            voipCs = bindToApp(ConnectionServiceVoipAppMain);
            verifyGetCurrentEndpoint(voipCs);
        } finally {
            tearDownApp(voipCs);
        }
    }

    /**
     * Test a client application can determine all the available audio routes also known as the
     * available {@link android.telecom.CallEndpoint}s
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a self-managed call that is backed by a {@link android.telecom.ConnectionService }
     *  via {@link android.telecom.TelecomManager#addNewIncomingCall(PhoneAccountHandle, Bundle)}
     * <p>
     * <p>
     *  2. collect the current {@link CallEndpoint}
     *  {@link android.telecom.CallEventCallback#onAvailableCallEndpointsChanged(List)}
     *  </ul>
     *  Assert the available {@link CallEndpoint}s are non-null.
     */
    @Test
    public void testAvailableEndpoints_ConnectionServiceVoipAppMain() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper voipCs = null;
        try {
            voipCs = bindToApp(ConnectionServiceVoipAppMain);
            verifyGetAvailableEndpoints(voipCs);
        } finally {
            tearDownApp(voipCs);
        }
    }


    /**
     * Test the scenario where a client application requests to switch the current {@link
     * android.telecom.CallEndpoint}
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a self-managed call that is backed by a {@link android.telecom.ConnectionService}
     *  via {@link android.telecom.TelecomManager#addNewIncomingCall(PhoneAccountHandle, Bundle)}
     * <p>
     *  2. collect the current {@link CallEndpoint} via
     *  {@link android.telecom.CallEventCallback#onCallEndpointChanged(CallEndpoint)}
     *  <p>
     *  3.  collect the available {@link CallEndpoint}s via
     *   {@link android.telecom.CallEventCallback#onAvailableCallEndpointsChanged(List)}
     *  <p>
     *  4. find another endpoint that is not the current endpoint and request an audio endpoint
     *  switch via {@link android.telecom.CallControl#requestCallEndpointChange(CallEndpoint,
     *                                                                Executor,
     *                                                                OutcomeReceiver)}
     *  <p>
     *  </ul>
     *  Assert the current {@link CallEndpoint} is switched successfully
     */
    @Test
    public void testBasicAudioSwitchTest_ConnectionServiceVoipAppMain() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper voipCsApp = null;
        try {
            voipCsApp = bindToApp(ConnectionServiceVoipAppMain);
            verifySwitchEndpoints(voipCsApp);
        } finally {
            tearDownApp(voipCsApp);
        }
    }

    /*********************************************************************************************
     *                           ConnectionServiceVoipAppClone
     /*********************************************************************************************/

    /**
     * Test the scenario where a new SELF-MANAGED outgoing call is created and transitions to the
     * ACTIVE and DISCONNECTED states.
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a self-managed call that is backed by a {@link android.telecom.ConnectionService }
     *  via {@link android.telecom.TelecomManager#placeCall(Uri, Bundle)}
     * <p>
     *  2. transition the call to ACTIVE via {@link Connection#setActive()}
     * <p>
     *  3.  transition the call to DISCONNECTED via
     *  {@link Connection#setDisconnected(DisconnectCause)} ()}
     *  </ul>
     *  Assert the call was successfully added and transitioned to the ACTIVE state without errors
     */
    @Test
    public void testOutgoingCall_ConnectionServiceVoipAppClone() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper voipCsApp = null;

        try {
            voipCsApp = bindToApp(ConnectionServiceVoipAppClone);
            verifyOutgoingCallStateTransitions(voipCsApp);
        } finally {
            tearDownApp(voipCsApp);
        }
    }

    /**
     * Test the scenario where a new SELF_MANAGED incoming call is created and transitions to the
     * ACTIVE and DISCONNECTED states.
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a managed call that is backed by a {@link android.telecom.ConnectionService }
     *  via {@link android.telecom.TelecomManager#addNewIncomingCall(PhoneAccountHandle, Bundle)}
     * <p>
     *  2. transition the call to ACTIVE via {@link Connection#setActive()}
     * <p>
     *  3.  transition the call to DISCONNECTED via
     *  {@link Connection#setDisconnected(DisconnectCause)} ()}
     *  </ul>
     *  Assert the call was successfully added and transitioned to the ACTIVE state without errors
     */
    @Test
    public void testIncomingCall_ConnectionServiceVoipAppClone() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper voipCsApp = null;

        try {
            voipCsApp = bindToApp(ConnectionServiceVoipAppClone);
            verifyIncomingCallStateTransitions(voipCsApp);
        } finally {
            tearDownApp(voipCsApp);
        }
    }

    /**
     * Test the scenario where an ongoing SELF-MANAGED call requests to change the mute state from
     * unmuted to muted multiple times.
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a self-managed call that is backed by a {@link android.telecom.ConnectionService }
     *  via {@link android.telecom.TelecomManager#addNewIncomingCall(PhoneAccountHandle, Bundle)}
     * <p>
     *  2. transition the call to ACTIVE via {@link Connection#setActive()}
     * <p>
     *  3. request mute by passing {@link Boolean#TRUE} to
     *  {@link Connection#onMuteStateChanged(boolean)}
     * <p>
     *  4. request mute by passing {@link Boolean#FALSE} to
     *  {@link Connection#onMuteStateChanged(boolean)}
     *  </ul>
     *  Assert the call was successfully muted and unmuted without errors.
     */
    @Test
    public void testToggleMuteState_ConnectionServiceVoipAppClone() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper voipCs = null;
        try {
            voipCs = bindToApp(ConnectionServiceVoipAppClone);
            verifyToggleMute(voipCs);
        } finally {
            tearDownApp(voipCs);
        }
    }

    /**
     * Test a client application can determine the current audio route also known as the
     * current {@link android.telecom.CallEndpoint}
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a self-managed call that is backed by a {@link android.telecom.ConnectionService }
     *  via {@link android.telecom.TelecomManager#addNewIncomingCall(PhoneAccountHandle, Bundle)}
     * <p>
     * <p>
     *  2. collect the current {@link CallEndpoint}
     *  {@link android.telecom.CallEventCallback#onCallEndpointChanged(CallEndpoint)}
     *  </ul>
     *  Assert the current {@link CallEndpoint} is non-null.
     */
    @Test
    public void testGetCurrentEndpoint_ConnectionServiceVoipAppClone() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper voipCs = null;
        try {
            voipCs = bindToApp(ConnectionServiceVoipAppClone);
            verifyGetCurrentEndpoint(voipCs);
        } finally {
            tearDownApp(voipCs);
        }
    }

    /**
     * Test a client application can determine all the available audio routes also known as the
     * available {@link android.telecom.CallEndpoint}s
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a self-managed call that is backed by a {@link android.telecom.ConnectionService }
     *  via {@link android.telecom.TelecomManager#addNewIncomingCall(PhoneAccountHandle, Bundle)}
     * <p>
     * <p>
     *  2. collect the current {@link CallEndpoint}
     *  {@link android.telecom.CallEventCallback#onAvailableCallEndpointsChanged(List)}
     *  </ul>
     *  Assert the available {@link CallEndpoint}s are non-null.
     */
    @Test
    public void testAvailableEndpoints_ConnectionServiceVoipAppClone() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper voipCs = null;
        try {
            voipCs = bindToApp(ConnectionServiceVoipAppClone);
            verifyGetAvailableEndpoints(voipCs);
        } finally {
            tearDownApp(voipCs);
        }
    }

    /**
     * Test the scenario where a client application requests to switch the current {@link
     * android.telecom.CallEndpoint}
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a self-managed call that is backed by a {@link android.telecom.ConnectionService}
     *  via {@link android.telecom.TelecomManager#addNewIncomingCall(PhoneAccountHandle, Bundle)}
     * <p>
     *  2. collect the current {@link CallEndpoint} via
     *  {@link android.telecom.CallEventCallback#onCallEndpointChanged(CallEndpoint)}
     *  <p>
     *  3.  collect the available {@link CallEndpoint}s via
     *   {@link android.telecom.CallEventCallback#onAvailableCallEndpointsChanged(List)}
     *  <p>
     *  4. find another endpoint that is not the current endpoint and request an audio endpoint
     *  switch via {@link android.telecom.CallControl#requestCallEndpointChange(CallEndpoint,
     *                                                                Executor,
     *                                                                OutcomeReceiver)}
     *  <p>
     *  </ul>
     *  Assert the current {@link CallEndpoint} is switched successfully
     */
    @Test
    public void testBasicAudioSwitchTest_ConnectionServiceVoipAppClone() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper voipCsApp = null;

        try {
            voipCsApp = bindToApp(ConnectionServiceVoipAppClone);
            verifySwitchEndpoints(voipCsApp);
        } finally {
            tearDownApp(voipCsApp);
        }
    }

    /*********************************************************************************************
     *                           TransactionalVoipAppMain
     /*********************************************************************************************/

    /**
     * Test the scenario where a new SELF-MANAGED outgoing call is created and transitions to the
     * ACTIVE and DISCONNECTED states.
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a self-managed call that is backed by a {@link android.telecom.ConnectionService }
     *  via {@link android.telecom.TelecomManager#addCall(CallAttributes,
     *                                                    Executor,
     *                                                    OutcomeReceiver,
     *                                                    CallControlCallback,
     *                                                    CallEventCallback)}
     * <p>
     *  2. transition the call to ACTIVE via {@link Connection#setActive()}
     * <p>
     *  3.  transition the call to DISCONNECTED via
     *  {@link Connection#setDisconnected(DisconnectCause)} ()}
     *  </ul>
     *  Assert the call was successfully added and transitioned to the ACTIVE state without errors
     */
    @Test
    public void testOutgoingCall_TransactionalVoipAppMain() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper transactionalApp = null;

        try {
            transactionalApp = bindToApp(TransactionalVoipAppMain);
            verifyOutgoingCallStateTransitions(transactionalApp);
        } finally {
            tearDownApp(transactionalApp);
        }
    }

    /**
     * Test the scenario where an incoming <b>AUDIO</b> call is created and transitions to the
     * ACTIVE and DISCONNECTED states.
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a self-managed call that is backed by a {@link android.telecom.ConnectionService }
     *  via {@link android.telecom.TelecomManager#addCall(CallAttributes,
     *                                                    Executor,
     *                                                    OutcomeReceiver,
     *                                                    CallControlCallback,
     *                                                    CallEventCallback)}
     * <p>
     *  2. transition the call to ACTIVE via {@link Connection#setActive()}
     * <p>
     *  3.  transition the call to DISCONNECTED via
     *  {@link Connection#setDisconnected(DisconnectCause)} ()}
     *  </ul>
     *  Assert the call was successfully added and transitioned to the ACTIVE state without errors
     */
    @Test
    public void testIncomingCall_TransactionalVoipAppMain() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper transactionalApp = null;

        try {
            transactionalApp = bindToApp(TransactionalVoipAppMain);
            verifyIncomingCallStateTransitions(transactionalApp);
        } finally {
            tearDownApp(transactionalApp);
        }
    }


    /**
     * Test the scenario where an incoming <b>VIDEO</b> call is created and transitions to the
     * ACTIVE and DISCONNECTED states.
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a self-managed call that is backed by a {@link android.telecom.ConnectionService }
     *  via {@link android.telecom.TelecomManager#addCall(CallAttributes,
     *                                                    Executor,
     *                                                    OutcomeReceiver,
     *                                                    CallControlCallback,
     *                                                    CallEventCallback)}
     * <p>
     *  2. transition the call to ACTIVE via {@link Connection#setActive()}
     * <p>
     *  3.  transition the call to DISCONNECTED via
     *  {@link Connection#setDisconnected(DisconnectCause)} ()}
     *  </ul>
     *  Assert the call was successfully added and transitioned to the ACTIVE state without errors.
     */
    @Test
    public void testIncomingVideoCall_TransactionalVoipAppMain() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        CallAttributes incomingAttributes = getDefaultAttributes(TransactionalVoipAppMain,
                false /*isOutgoing*/);
        AppControlWrapper transactionalApp = null;

        try {
            transactionalApp = bindToApp(TransactionalVoipAppMain);
            String mt = addCallAndVerify(transactionalApp, incomingAttributes);
            verifyCallIsInState(mt, STATE_RINGING);
            setCallStateAndVerify(transactionalApp, mt, STATE_ACTIVE, CallAttributes.VIDEO_CALL);
            setCallStateAndVerify(transactionalApp, mt, STATE_HOLDING);
            setCallStateAndVerify(transactionalApp, mt, STATE_DISCONNECTED, DisconnectCause.MISSED);
        } finally {
            tearDownApp(transactionalApp);
        }
    }

    /**
     * Test a client application can determine the current audio route also known as the
     * current {@link android.telecom.CallEndpoint}
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a managed call that is backed by a {@link android.telecom.ConnectionService }
     *  via {@link android.telecom.TelecomManager#addCall(CallAttributes,
     *                                                    Executor,
     *                                                    OutcomeReceiver,
     *                                                    CallControlCallback,
     *                                                    CallEventCallback)}
     * <p>
     *  2. collect the current {@link CallEndpoint}
     *  {@link android.telecom.CallEventCallback#onCallEndpointChanged(CallEndpoint)}
     *  </ul>
     *  Assert the current {@link CallEndpoint} is non-null.
     */
    @Test
    public void testGetCurrentEndpoint_TransactionalVoipAppMain() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper voipCs = null;
        try {
            voipCs = bindToApp(TransactionalVoipAppMain);
            verifyGetCurrentEndpoint(voipCs);
        } finally {
            tearDownApp(voipCs);
        }
    }

    /**
     * Test a client application can determine all the available audio routes also known as the
     * available {@link android.telecom.CallEndpoint}s
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a managed call that is backed by a {@link android.telecom.ConnectionService }
     *  via {@link android.telecom.TelecomManager#addCall(CallAttributes,
     *                                                    Executor,
     *                                                    OutcomeReceiver,
     *                                                    CallControlCallback,
     *                                                    CallEventCallback)}
     * <p>
     *  2. collect the current {@link CallEndpoint}
     *  {@link android.telecom.CallEventCallback#onAvailableCallEndpointsChanged(List)}
     *  </ul>
     *  Assert the available {@link CallEndpoint}s are non-null.
     */
    @Test
    public void testAvailableEndpoints_TransactionalVoipAppMain() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper voipCs = null;
        try {
            voipCs = bindToApp(TransactionalVoipAppMain);
            verifyGetAvailableEndpoints(voipCs);
        } finally {
            tearDownApp(voipCs);
        }
    }

    /**
     * Test the scenario where a client application requests to switch the current {@link
     * android.telecom.CallEndpoint}
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a self-managed call that is backed by a {@link android.telecom.ConnectionService }
     *  via {@link android.telecom.TelecomManager#addCall(CallAttributes,
     *                                                    Executor,
     *                                                    OutcomeReceiver,
     *                                                    CallControlCallback,
     *                                                    CallEventCallback)}
     * <p>
     *  2. collect the current {@link CallEndpoint} via
     *  {@link android.telecom.CallEventCallback#onCallEndpointChanged(CallEndpoint)}
     *  <p>
     *  3.  collect the available {@link CallEndpoint}s via
     *   {@link android.telecom.CallEventCallback#onAvailableCallEndpointsChanged(List)}
     *  <p>
     *  4. find another endpoint that is not the current endpoint and request an audio endpoint
     *  switch via {@link android.telecom.CallControl#requestCallEndpointChange(CallEndpoint,
     *                                                                Executor,
     *                                                                OutcomeReceiver)}
     *  <p>
     *  </ul>
     *  Assert the current {@link CallEndpoint} is switched successfully
     */
    @Test
    public void testBasicAudioSwitchTest_TransactionalVoipAppMain() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper transactionalApp = null;
        try {
            transactionalApp = bindToApp(TransactionalVoipAppMain);
            verifySwitchEndpoints(transactionalApp);
        } finally {
            tearDownApp(transactionalApp);
        }
    }

    /*********************************************************************************************
     *                           TransactionalVoipAppClone
     /*********************************************************************************************/

    /**
     * Test the scenario where a new SELF-MANAGED outgoing call is created and transitions to the
     * ACTIVE and DISCONNECTED states.
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a self-managed call that is backed by a {@link android.telecom.ConnectionService }
     *  via {@link android.telecom.TelecomManager#addCall(CallAttributes,
     *                                                    Executor,
     *                                                    OutcomeReceiver,
     *                                                    CallControlCallback,
     *                                                    CallEventCallback)}
     * <p>
     *  2. transition the call to ACTIVE via {@link Connection#setActive()}
     * <p>
     *  3.  transition the call to DISCONNECTED via
     *  {@link Connection#setDisconnected(DisconnectCause)} ()}
     *  </ul>
     *  Assert the call was successfully added and transitioned to the ACTIVE state without errors
     */
    @Test
    public void testOutgoingCall_TransactionalVoipAppClone() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper transactionalApp = null;
        try {
            transactionalApp = bindToApp(TransactionalVoipAppClone);
            verifyOutgoingCallStateTransitions(transactionalApp);
        } finally {
            tearDownApp(transactionalApp);
        }
    }

    /**
     * Test the scenario where a new SELF_MANAGED incoming call is created and transitions to the
     * ACTIVE and DISCONNECTED states.
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a managed call that is backed by a {@link android.telecom.ConnectionService }
     *  via {@link android.telecom.TelecomManager#addCall(CallAttributes,
     *                                                    Executor,
     *                                                    OutcomeReceiver,
     *                                                    CallControlCallback,
     *                                                    CallEventCallback)}
     * <p>
     *  2. transition the call to ACTIVE via {@link Connection#setActive()}
     * <p>
     *  3.  transition the call to DISCONNECTED via
     *  {@link Connection#setDisconnected(DisconnectCause)} ()}
     *  </ul>
     *  Assert the call was successfully added and transitioned to the ACTIVE state without errors
     */
    @Test
    public void testIncomingCall_TransactionalVoipAppClone() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper transactionalApp = null;
        try {
            transactionalApp = bindToApp(TransactionalVoipAppClone);
            verifyIncomingCallStateTransitions(transactionalApp);
        } finally {
            tearDownApp(transactionalApp);
        }
    }

    /**
     * Test a client application can determine the current audio route also known as the
     * current {@link android.telecom.CallEndpoint}
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a managed call that is backed by a {@link android.telecom.ConnectionService }
     *  via {@link android.telecom.TelecomManager#addCall(CallAttributes,
     *                                                    Executor,
     *                                                    OutcomeReceiver,
     *                                                    CallControlCallback,
     *                                                    CallEventCallback)}
     * <p>
     *  2. collect the current {@link CallEndpoint}
     *  {@link android.telecom.CallEventCallback#onCallEndpointChanged(CallEndpoint)}
     *  </ul>
     *  Assert the current {@link CallEndpoint} is non-null.
     */
    @Test
    public void testGetCurrentEndpoint_TransactionalVoipAppClone() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper voipCs = null;
        try {
            voipCs = bindToApp(TransactionalVoipAppClone);
            verifyGetCurrentEndpoint(voipCs);
        } finally {
            tearDownApp(voipCs);
        }
    }

    /**
     * Test a client application can determine all the available audio routes also known as the
     * available {@link android.telecom.CallEndpoint}s
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a managed call that is backed by a {@link android.telecom.ConnectionService }
     *  via {@link android.telecom.TelecomManager#addCall(CallAttributes,
     *                                                    Executor,
     *                                                    OutcomeReceiver,
     *                                                    CallControlCallback,
     *                                                    CallEventCallback)}
     * <p>
     *  2. collect the current {@link CallEndpoint}
     *  {@link android.telecom.CallEventCallback#onAvailableCallEndpointsChanged(List)}
     *  </ul>
     *  Assert the available {@link CallEndpoint}s are non-null.
     */
    @Test
    public void testAvailableEndpoints_TransactionalVoipAppClone() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper voipCs = null;
        try {
            voipCs = bindToApp(TransactionalVoipAppClone);
            verifyGetAvailableEndpoints(voipCs);
        } finally {
            tearDownApp(voipCs);
        }
    }

    /**
     * Test the scenario where a client application requests to switch the current {@link
     * android.telecom.CallEndpoint}
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a self-managed call that is backed by a {@link android.telecom.ConnectionService }
     *  via {@link android.telecom.TelecomManager#addCall(CallAttributes,
     *                                                    Executor,
     *                                                    OutcomeReceiver,
     *                                                    CallControlCallback,
     *                                                    CallEventCallback)}
     * <p>
     *  2. collect the current {@link CallEndpoint} via
     *  {@link android.telecom.CallEventCallback#onCallEndpointChanged(CallEndpoint)}
     *  <p>
     *  3.  collect the available {@link CallEndpoint}s via
     *   {@link android.telecom.CallEventCallback#onAvailableCallEndpointsChanged(List)}
     *  <p>
     *  4. find another endpoint that is not the current endpoint and request an audio endpoint
     *  switch via {@link android.telecom.CallControl#requestCallEndpointChange(CallEndpoint,
     *                                                                Executor,
     *                                                                OutcomeReceiver)}
     *  <p>
     *  </ul>
     *  Assert the current {@link CallEndpoint} is switched successfully
     */
    @Test
    public void testBasicAudioSwitchTest_TransactionalVoipAppClone() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper transactionalApp = null;
        try {
            transactionalApp = bindToApp(TransactionalVoipAppClone);
            verifySwitchEndpoints(transactionalApp);
        } finally {
            tearDownApp(transactionalApp);
        }
    }

    /*********************************************************************************************
     *                           Helpers
     /*********************************************************************************************/

    private void verifyOutgoingCallStateTransitions(AppControlWrapper appControlWrapper)
            throws Exception {
        String mo = addOutgoingCallAndVerify(appControlWrapper);
        if (appControlWrapper.isTransactionalControl()) {
            verifyCallIsInState(mo, STATE_CONNECTING);
        } else {
            verifyCallIsInState(mo, STATE_DIALING);
        }
        setCallStateAndVerify(appControlWrapper, mo, STATE_ACTIVE);
        setCallStateAndVerify(appControlWrapper, mo, STATE_HOLDING);
        setCallStateAndVerify(appControlWrapper, mo, STATE_DISCONNECTED);
    }

    private void verifyIncomingCallStateTransitions(AppControlWrapper appControlWrapper)
            throws Exception {
        String mt = addIncomingCallAndVerify(appControlWrapper);
        verifyCallIsInState(mt, STATE_RINGING);
        setCallStateAndVerify(appControlWrapper, mt, STATE_ACTIVE);
        setCallStateAndVerify(appControlWrapper, mt, STATE_HOLDING);
        setCallStateAndVerify(appControlWrapper, mt, STATE_DISCONNECTED);
    }

    private void verifyToggleMute(AppControlWrapper appControlWrapper) throws Exception {
        String mo = addOutgoingCallAndVerify(appControlWrapper);
        setCallStateAndVerify(appControlWrapper, mo, STATE_ACTIVE);
        assertFalse(isMuted(appControlWrapper, mo));
        setMuteState(appControlWrapper, mo, true /* isMuted */);
        assertTrue(isMuted(appControlWrapper, mo));
        setMuteState(appControlWrapper, mo, false /* isMuted */);
        assertFalse(isMuted(appControlWrapper, mo));
        setCallStateAndVerify(appControlWrapper, mo, STATE_DISCONNECTED);
    }

    private void verifyGetCurrentEndpoint(AppControlWrapper appControlWrapper) throws Exception {
        String mo = addOutgoingCallAndVerify(appControlWrapper);
        assertNotNull(getCurrentCallEndpoint(appControlWrapper, mo));
        setCallStateAndVerify(appControlWrapper, mo, STATE_DISCONNECTED);
    }

    private void verifyGetAvailableEndpoints(AppControlWrapper appControlWrapper) throws Exception {
        String mo = addOutgoingCallAndVerify(appControlWrapper);
        assertNotNull(getAvailableCallEndpoints(appControlWrapper, mo));
        setCallStateAndVerify(appControlWrapper, mo, STATE_DISCONNECTED);
    }

    private void verifySwitchEndpoints(AppControlWrapper appControlWrapper) throws Exception {
        String mo = addOutgoingCallAndVerify(appControlWrapper);
        setCallStateAndVerify(appControlWrapper, mo, STATE_ACTIVE);
        switchToAnotherCallEndpoint(appControlWrapper, mo);
        setCallStateAndVerify(appControlWrapper, mo, STATE_DISCONNECTED);
    }
}
