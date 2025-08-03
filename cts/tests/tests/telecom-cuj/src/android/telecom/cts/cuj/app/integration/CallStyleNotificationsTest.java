/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static android.telecom.Call.STATE_DISCONNECTED;
import static android.telecom.cts.apps.TelecomTestApp.ConnectionServiceVoipAppMain;
import static android.telecom.cts.apps.TelecomTestApp.TransactionalVoipAppMain;

import android.app.Notification;
import android.app.NotificationManager;
import android.os.Bundle;
import android.os.OutcomeReceiver;
import android.telecom.Call;
import android.telecom.CallAttributes;
import android.telecom.CallControlCallback;
import android.telecom.CallEventCallback;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;
import android.telecom.cts.apps.AppControlWrapper;
import android.telecom.cts.cuj.BaseAppVerifier;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.Executor;


/**
 * This test class should test common calling scenarios that involve
 * {@link android.app.Notification.CallStyle} notifications.
 */
@RunWith(JUnit4.class)
public class CallStyleNotificationsTest extends BaseAppVerifier {

    /**
     * Assert SELF-MANAGED (backed by a {@link android.telecom.ConnectionService} calling
     * applications can post a {@link android.app.Notification.CallStyle} notification.
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. register a {@link android.telecom.PhoneAccount} with
     *  {@link android.telecom.PhoneAccount#CAPABILITY_SELF_MANAGED}
     *  2. create an incoming SELF-MANAGED call that is backed by a
     *  {@link android.telecom.ConnectionService } via
     *  {@link android.telecom.TelecomManager#addNewIncomingCall(PhoneAccountHandle, Bundle)}
     *  and verify the call is added by using an
     *  {@link android.telecom.InCallService#onCallAdded(Call)}
     * <p>
     *  3. post a {@link android.app.Notification.CallStyle} via
     *  {@link android.app.NotificationManager#notify(int, Notification)} and verify using
     *  {@link NotificationManager#getActiveNotifications()}
     * <p>
     *  4. set the call to disconnected via {@link Connection#setDisconnected(DisconnectCause)}
     *  <p>
     *  </ul>
     */
    @Test
    public void testCallStyleNotificationBehavior_ConnectionServiceVoipAppMain() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper csVoipApp = null;
        try {
            csVoipApp = bindToApp(ConnectionServiceVoipAppMain);
            String mt = addIncomingCallAndVerify(csVoipApp);
            verifyNotificationIsPostedForCall(csVoipApp, mt);
            setCallStateAndVerify(csVoipApp, mt, STATE_DISCONNECTED);
        } finally {
            tearDownApp(csVoipApp);
        }
    }

    /**
     * Assert SELF-MANAGED calling applications that use {@link
     * android.telecom.TelecomManager#addCall(CallAttributes, Executor, OutcomeReceiver,
     * CallControlCallback, CallEventCallback) } can post a
     * {@link android.app.Notification.CallStyle} notification.
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. register a {@link android.telecom.PhoneAccount} with
     *  {@link android.telecom.PhoneAccount#CAPABILITY_SUPPORTS_TRANSACTIONAL_OPERATIONS}
     *  <p>
     *  2. create an incoming call via {@link android.telecom.TelecomManager#addCall(CallAttributes,
     *  Executor, OutcomeReceiver, CallControlCallback, CallEventCallback)} and verify the call is
     *  added by using an {@link android.telecom.InCallService#onCallAdded(Call)}
     * <p>
     *  3. post a {@link android.app.Notification.CallStyle} via
     *  {@link android.app.NotificationManager#notify(int, Notification)} and verify using
     *  {@link NotificationManager#getActiveNotifications()}
     * <p>
     *  4. disconnect the call via {@link android.telecom.CallControl#disconnect(DisconnectCause,
     *  Executor, OutcomeReceiver)}
     *  <p>
     *  </ul>
     */
    @Test
    public void testCallStyleNotificationBehavior_TransactionalVoipAppMain() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper transactionalVoipApp = null;
        try {
            transactionalVoipApp = bindToApp(TransactionalVoipAppMain);
            String mt = addIncomingCallAndVerify(transactionalVoipApp);
            verifyNotificationIsPostedForCall(transactionalVoipApp, mt);
            setCallStateAndVerify(transactionalVoipApp, mt, STATE_DISCONNECTED);
        } finally {
            tearDownApp(transactionalVoipApp);
        }
    }
}
