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
import static android.telecom.Call.STATE_RINGING;
import static android.telecom.cts.apps.TelecomTestApp.ConnectionServiceVoipAppMain;
import static android.telecom.cts.apps.TelecomTestApp.ManagedConnectionServiceApp;
import static android.telecom.cts.apps.TelecomTestApp.TransactionalVoipAppMain;

import android.media.AudioManager;
import android.telecom.cts.apps.AppControlWrapper;
import android.telecom.cts.cuj.BaseAppVerifier;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AudioModeTest extends BaseAppVerifier {

    /**
     * Verify that outgoing managed calls are in {@link AudioManager#MODE_IN_CALL} during the
     * duration of a managed call.
     */
    @Test
    public void testOutgoingAudioMode_ManagedConnectionServiceApp() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper managedApp = null;
        try {
            managedApp = bindToApp(ManagedConnectionServiceApp);
            verifyOutgoingAudioMode(managedApp, AudioManager.MODE_IN_CALL);
        } finally {
            tearDownApp(managedApp);
        }
    }

    /**
     * Verify that incoming managed calls are in {@link AudioManager#MODE_IN_CALL} during the
     * duration of a managed call.
     */
    @Test
    public void testIncomingAudioMode_ManagedConnectionServiceApp() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper managedApp = null;
        try {
            managedApp = bindToApp(ManagedConnectionServiceApp);
            verifyIncomingAudioMode(managedApp, AudioManager.MODE_IN_CALL);
        } finally {
            tearDownApp(managedApp);
        }
    }

    /**
     * Verify that outgoing self-managed (ConnectionService) calls are in
     * {@link AudioManager#MODE_IN_COMMUNICATION} during the duration of a self-managed call.
     */
    @Test
    public void testOutgoingAudioMode_ConnectionServiceVoipAppMain() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper csVoipApp = null;
        try {
            csVoipApp = bindToApp(ConnectionServiceVoipAppMain);
            verifyOutgoingAudioMode(csVoipApp, AudioManager.MODE_IN_COMMUNICATION);
        } finally {
            tearDownApp(csVoipApp);
        }
    }

    /**
     * Verify that incoming self-managed (ConnectionService) calls are in
     * {@link AudioManager#MODE_IN_COMMUNICATION} during the duration of a self-managed call.
     */
    @Test
    public void testIncomingAudioMode_ConnectionServiceVoipAppMain() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper csVoipApp = null;
        try {
            csVoipApp = bindToApp(ConnectionServiceVoipAppMain);
            verifyIncomingAudioMode(csVoipApp, AudioManager.MODE_IN_COMMUNICATION);
        } finally {
            tearDownApp(csVoipApp);
        }
    }

    /**
     * Verify that outgoing self-managed (Transactional) calls are in
     * {@link AudioManager#MODE_IN_COMMUNICATION} during the duration of a self-managed call.
     */
    @Test
    public void testAudioMode_TransactionalVoipAppMain() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper transactionalApp = null;
        try {
            transactionalApp = bindToApp(TransactionalVoipAppMain);
            verifyOutgoingAudioMode(transactionalApp, AudioManager.MODE_IN_COMMUNICATION);
        } finally {
            tearDownApp(transactionalApp);
        }
    }

    /**
     * Verify that incoming self-managed (Transactional) calls are in
     * {@link AudioManager#MODE_IN_COMMUNICATION} during the duration of a self-managed call.
     */
    @Test
    public void testIncomingAudioMode_TransactionalVoipAppMain() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper transactionalApp = null;
        try {
            transactionalApp = bindToApp(TransactionalVoipAppMain);
            verifyIncomingAudioMode(transactionalApp, AudioManager.MODE_IN_COMMUNICATION);
        } finally {
            tearDownApp(transactionalApp);
        }
    }

    /*********************************************************************************************
     *                           Helpers
     /*********************************************************************************************/

    private void verifyOutgoingAudioMode(AppControlWrapper appControlWrapper,
                                        int expectedAudioMode) throws Exception {
        String mo = addOutgoingCallAndVerify(appControlWrapper);
        if (appControlWrapper.isTransactionalControl()) {
            verifyCallIsInState(mo, STATE_CONNECTING);
        } else {
            verifyCallIsInState(mo, STATE_DIALING);
        }
        assertAudioMode(expectedAudioMode);
        setCallStateAndVerify(appControlWrapper, mo, STATE_ACTIVE);
        assertAudioMode(expectedAudioMode);
        setCallStateAndVerify(appControlWrapper, mo, STATE_DISCONNECTED);
    }

    private void verifyIncomingAudioMode(AppControlWrapper appControlWrapper,
                                        int expectedAudioMode) throws Exception {
        String mt = addIncomingCallAndVerify(appControlWrapper);
        verifyCallIsInState(mt, STATE_RINGING);
        setCallStateAndVerify(appControlWrapper, mt, STATE_ACTIVE);
        assertAudioMode(expectedAudioMode);
        setCallStateAndVerify(appControlWrapper, mt, STATE_DISCONNECTED);
    }
}
