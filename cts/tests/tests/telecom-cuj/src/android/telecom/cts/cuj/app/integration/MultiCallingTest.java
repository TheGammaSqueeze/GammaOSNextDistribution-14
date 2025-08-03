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
import static android.telecom.cts.apps.TelecomTestApp.ConnectionServiceVoipAppMain;
import static android.telecom.cts.apps.TelecomTestApp.ManagedConnectionServiceApp;
import static android.telecom.cts.apps.TelecomTestApp.TransactionalVoipAppMain;

import android.telecom.CallAttributes;
import android.telecom.VideoProfile;
import android.telecom.cts.apps.AppControlWrapper;
import android.telecom.cts.cuj.BaseAppVerifier;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;

/**
 * This test class should be used to test calling scenarios involving multiple calling applications
 */
@RunWith(JUnit4.class)
public class MultiCallingTest extends BaseAppVerifier {

    /**
     * Test the scenario where there is an <b>MANAGED</b> ongoing call and a new
     * <b>MANAGED</b> incoming call is answered on the <b>SAME</b> application.  The expectation is
     * the previous active call is placed on hold through a callback and the incoming call is
     * answered. There should never be two active calls at once.
     */
    @Test
    public void testMultiCalling_SingleApp_ManagedApp() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        CallAttributes outgoingAttributes = getDefaultAttributes(ManagedConnectionServiceApp,
                true /*isOutgoing*/);
        CallAttributes incomingAttributes = getDefaultAttributes(ManagedConnectionServiceApp,
                false /*isOutgoing*/);
        AppControlWrapper managedApp = null;

        try {
            managedApp = bindToApp(ManagedConnectionServiceApp);

            String mo = addCallAndVerify(managedApp, outgoingAttributes);
            setCallStateAndVerify(managedApp, mo, STATE_ACTIVE);

            String mt = addCallAndVerify(managedApp, incomingAttributes);
            answerViaInCallServiceAndVerify(mt, VideoProfile.STATE_AUDIO_ONLY);
            verifyCallIsInState(mo, STATE_HOLDING);

            setCallStateAndVerify(managedApp, mt, STATE_DISCONNECTED);
            setCallStateAndVerify(managedApp, mo, STATE_ACTIVE);
            setCallStateAndVerify(managedApp, mo, STATE_DISCONNECTED);
        } finally {
            tearDownApp(managedApp);
        }
    }

    /**
     * Test the scenario where there is an <b>SELF-MANAGED (backed by a ConnectionService) </b>
     * ongoing call and a new <b>SELF-MANAGED (backed by a ConnectionService) </b> incoming
     * call is answered on the <b>SAME</b> application.  The expectation is the previous active
     * call is placed on hold through a callback and the incoming call is answered. There should
     * never be two active calls at once.
     */
    @Test
    public void testMultiCalling_SingleApp_ConnectionServiceVoipAppMain() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        CallAttributes outgoingAttributes = getDefaultAttributes(ConnectionServiceVoipAppMain,
                true /*isOutgoing*/);
        CallAttributes incomingAttributes = getDefaultAttributes(ConnectionServiceVoipAppMain,
                false /*isOutgoing*/);
        AppControlWrapper voipCsApp = null;
        try {
            voipCsApp = bindToApp(ConnectionServiceVoipAppMain);

            String mo = addCallAndVerify(voipCsApp, outgoingAttributes);
            setCallStateAndVerify(voipCsApp, mo, STATE_ACTIVE);

            String mt = addCallAndVerify(voipCsApp, incomingAttributes);
            setCallStateAndVerify(voipCsApp, mt, STATE_ACTIVE);
            verifyCallIsInState(mo, STATE_HOLDING);

            setCallStateAndVerify(voipCsApp, mt, STATE_DISCONNECTED);
            setCallStateAndVerify(voipCsApp, mo, STATE_ACTIVE);
            setCallStateAndVerify(voipCsApp, mo, STATE_DISCONNECTED);
        } finally {
            tearDownApp(voipCsApp);
        }
    }

    /**
     * Test the scenario where there is an <b>SELF-MANAGED (backed by a TransactionalService) </b>
     * ongoing call and a new <b>SELF-MANAGED (backed by a TransactionalService) </b>
     * incoming call is answered on the <b>SAME</b> application.  The expectation is the previous
     * active call is placed on hold through a callback and the incoming call is answered. There
     * should never be two active calls at once.
     */
    @Test
    public void testMultiCalling_SingleApp_TransactionalApp() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        CallAttributes outgoingAttributes = getDefaultAttributes(TransactionalVoipAppMain,
                true /*isOutgoing*/);
        CallAttributes incomingAttributes = getDefaultAttributes(TransactionalVoipAppMain,
                false /*isOutgoing*/);
        AppControlWrapper transactionalApp = null;
        try {
            transactionalApp = bindToApp(TransactionalVoipAppMain);

            String mo = addCallAndVerify(transactionalApp, outgoingAttributes);
            setCallStateAndVerify(transactionalApp, mo, STATE_ACTIVE);

            String mt = addCallAndVerify(transactionalApp, incomingAttributes);
            setCallStateAndVerify(transactionalApp, mt, STATE_ACTIVE);
            verifyCallIsInState(mo, STATE_HOLDING);

            setCallStateAndVerify(transactionalApp, mt, STATE_DISCONNECTED);
            setCallStateAndVerify(transactionalApp, mo, STATE_ACTIVE);
            setCallStateAndVerify(transactionalApp, mo, STATE_DISCONNECTED);
        } finally {
            tearDownApp(transactionalApp);
        }
    }

    /*********************************************************************************************
     *                               Multiple Apps
     /*********************************************************************************************/

    /**
     * Test the scenario where there is an <b>MANAGED</b>  ongoing call and a new
     * <b>SELF-MANAGED (backed by a TransactionalService) </b> incoming call is answered on
     * <b>DIFFERENT</b> applications.  The expectation is the previous active call is placed on
     * hold through a callback and the incoming call is answered. There should never be two active
     * calls at once.
     */
    @Test
    public void testManagedAndTransactional() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        CallAttributes outgoingAttributes = getDefaultAttributes(ManagedConnectionServiceApp,
                true /*isOutgoing*/);
        CallAttributes incomingAttributes = getDefaultAttributes(TransactionalVoipAppMain,
                false /*isOutgoing*/);
        AppControlWrapper managedApp = null;
        AppControlWrapper transactionalApp = null;

        try {
            managedApp = bindToApp(ManagedConnectionServiceApp);
            String mo = addCallAndVerify(managedApp, outgoingAttributes);
            setCallStateAndVerify(managedApp, mo, STATE_ACTIVE);

            transactionalApp = bindToApp(TransactionalVoipAppMain);
            String mt = addCallAndVerify(transactionalApp, incomingAttributes);
            setCallStateAndVerify(transactionalApp, mt, STATE_ACTIVE);
            verifyCallIsInState(mo, STATE_HOLDING);

            setCallStateAndVerify(transactionalApp, mt, STATE_DISCONNECTED);
            setCallStateAndVerify(managedApp, mo, STATE_ACTIVE);
            setCallStateAndVerify(managedApp, mo, STATE_DISCONNECTED);
        } finally {
            List<AppControlWrapper> controls = new ArrayList<>();
            controls.add(managedApp);
            controls.add(transactionalApp);
            tearDownApps(controls);
        }
    }

    /**
     * Test the scenario where there is an <b>SELF-MANAGED (backed by a TransactionalService) </b>
     * ongoing call and a new <b>MANAGED</b> incoming call is answered on
     * <b>DIFFERENT</b> applications.  The expectation is the previous active call is placed on
     * hold through a callback and the incoming call is answered. There should never be two active
     * calls at once.
     */
    @Test
    public void testTransactionalAndManaged() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        CallAttributes outgoingAttributes = getDefaultAttributes(TransactionalVoipAppMain,
                true /*isOutgoing*/);
        CallAttributes incomingAttributes = getDefaultAttributes(ManagedConnectionServiceApp,
                false /*isOutgoing*/);
        AppControlWrapper transactionalApp = null;
        AppControlWrapper managedApp = null;

        try {
            transactionalApp = bindToApp(TransactionalVoipAppMain);
            String mo = addCallAndVerify(transactionalApp, outgoingAttributes);
            setCallStateAndVerify(transactionalApp, mo, STATE_ACTIVE);

            managedApp = bindToApp(ManagedConnectionServiceApp);
            String mt = addCallAndVerify(managedApp, incomingAttributes);
            answerViaInCallServiceAndVerify(mt, VideoProfile.STATE_AUDIO_ONLY);
            verifyCallIsInState(mo, STATE_HOLDING);

            setCallStateAndVerify(managedApp, mt, STATE_DISCONNECTED);
            setCallStateAndVerify(transactionalApp, mo, STATE_ACTIVE);
            setCallStateAndVerify(transactionalApp, mo, STATE_DISCONNECTED);
        } finally {
            List<AppControlWrapper> controls = new ArrayList<>();
            controls.add(transactionalApp);
            controls.add(managedApp);
            tearDownApps(controls);
        }
    }

    /**
     * Test the scenario where there is an <b>MANAGED</b>  ongoing call and a new
     * <b>SELF-MANAGED (backed by a ConnectionService) </b> incoming call is answered on
     * <b>DIFFERENT</b> applications.  The expectation is the previous active call is placed on
     * hold through a callback and the incoming call is answered. There should never be two active
     * calls at once.
     */
    @Test
    public void testManagedAndSelfManaged() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        CallAttributes outgoingAttributes = getDefaultAttributes(ManagedConnectionServiceApp,
                true /*isOutgoing*/);
        CallAttributes incomingAttributes = getDefaultAttributes(ConnectionServiceVoipAppMain,
                false /*isOutgoing*/);
        AppControlWrapper managedApp = null;
        AppControlWrapper csApp = null;

        try {
            managedApp = bindToApp(ManagedConnectionServiceApp);
            String mo = addCallAndVerify(managedApp, outgoingAttributes);
            setCallStateAndVerify(managedApp, mo, STATE_ACTIVE);

            csApp = bindToApp(ConnectionServiceVoipAppMain);
            String mt = addCallAndVerify(csApp, incomingAttributes);
            setCallStateAndVerify(csApp, mt, STATE_ACTIVE);
            verifyCallIsInState(mo, STATE_HOLDING);

            setCallStateAndVerify(csApp, mt, STATE_DISCONNECTED);
            setCallStateAndVerify(managedApp, mo, STATE_ACTIVE);
            setCallStateAndVerify(managedApp, mo, STATE_DISCONNECTED);
        } finally {
            List<AppControlWrapper> controls = new ArrayList<>();
            controls.add(managedApp);
            controls.add(csApp);
            tearDownApps(controls);
        }
    }

    /**
     * Test the scenario where there is a <b>SELF-MANAGED (backed by a ConnectionService) </b>
     * ongoing call and a new <b>MANAGED</b> incoming call is answered on
     * <b>DIFFERENT</b> applications.  The expectation is the previous active call is placed on
     * hold through a callback and the incoming call is answered. There should never be two active
     * calls at once.
     */
    @Test
    public void testSelfManagedAndManaged() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        CallAttributes outgoingAttributes = getDefaultAttributes(ConnectionServiceVoipAppMain,
                true /*isOutgoing*/);
        CallAttributes incomingAttributes = getDefaultAttributes(ManagedConnectionServiceApp,
                false /*isOutgoing*/);
        AppControlWrapper csApp = null;
        AppControlWrapper managedApp = null;

        try {
            csApp = bindToApp(ConnectionServiceVoipAppMain);
            String mo = addCallAndVerify(csApp, outgoingAttributes);
            setCallStateAndVerify(csApp, mo, STATE_ACTIVE);

            managedApp = bindToApp(ManagedConnectionServiceApp);
            String mt = addCallAndVerify(managedApp, incomingAttributes);
            answerViaInCallServiceAndVerify(mt, VideoProfile.STATE_AUDIO_ONLY);
            verifyCallIsInState(mo, STATE_HOLDING);

            setCallStateAndVerify(managedApp, mt, STATE_DISCONNECTED);
            setCallStateAndVerify(csApp, mo, STATE_ACTIVE);
            setCallStateAndVerify(csApp, mo, STATE_DISCONNECTED);
        } finally {
            List<AppControlWrapper> controls = new ArrayList<>();
            controls.add(csApp);
            controls.add(managedApp);
            tearDownApps(controls);
        }
    }
}
