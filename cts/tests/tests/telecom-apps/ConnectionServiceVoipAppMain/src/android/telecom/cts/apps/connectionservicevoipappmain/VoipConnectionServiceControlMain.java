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

package android.telecom.cts.apps.connectionservicevoipappmain;

import static android.telecom.Call.STATE_ACTIVE;
import static android.telecom.Call.STATE_DIALING;
import static android.telecom.Call.STATE_DISCONNECTED;
import static android.telecom.Call.STATE_HOLDING;
import static android.telecom.Call.STATE_RINGING;
import static android.telecom.cts.apps.AssertOutcome.assertCountDownLatchWasCalled;
import static android.telecom.cts.apps.AttributesUtil.getExtrasWithPhoneAccount;
import static android.telecom.cts.apps.AttributesUtil.hasSetInactiveCapabilities;
import static android.telecom.cts.apps.AttributesUtil.isOutgoing;
import static android.telecom.cts.apps.StackTraceUtil.appendStackTraceList;
import static android.telecom.cts.apps.StackTraceUtil.createStackTraceList;
import static android.telecom.cts.apps.TelecomTestApp.SELF_MANAGED_CS_CLONE_ACCOUNT;
import static android.telecom.cts.apps.TelecomTestApp.SELF_MANAGED_CS_CLONE_PACKAGE_NAME;
import static android.telecom.cts.apps.TelecomTestApp.SELF_MANAGED_CS_MAIN_ACCOUNT;
import static android.telecom.cts.apps.TelecomTestApp.VOIP_CS_CONTROL_INTERFACE_ACTION;
import static android.telecom.cts.apps.NotificationUtils.isTargetNotificationPosted;
import static android.telecom.cts.apps.WaitUntil.waitUntilAvailableEndpointsIsSet;
import static android.telecom.cts.apps.WaitUntil.waitUntilCallAudioStateIsSet;
import static android.telecom.cts.apps.WaitUntil.waitUntilConnectionIsNonNull;
import static android.telecom.cts.apps.WaitUntil.waitUntilIdIsSet;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.telecom.CallAttributes;
import android.telecom.CallEndpoint;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.cts.apps.AvailableEndpointsTransaction;
import android.telecom.cts.apps.BooleanTransaction;
import android.telecom.cts.apps.CallEndpointTransaction;
import android.telecom.cts.apps.CallExceptionTransaction;
import android.telecom.cts.apps.CallResources;
import android.telecom.cts.apps.IAppControl;
import android.telecom.cts.apps.LatchedEndpointOutcomeReceiver;
import android.telecom.cts.apps.NoDataTransaction;
import android.telecom.cts.apps.NotificationUtils;
import android.telecom.cts.apps.PhoneAccountTransaction;
import android.telecom.cts.apps.VoipConnection;
import android.telecom.cts.apps.TestAppException;
import android.telecom.cts.apps.TestAppTransaction;
import android.telecom.cts.apps.WaitUntil;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class VoipConnectionServiceControlMain extends Service {
    private String mTag = VoipConnectionServiceControlMain.class.getSimpleName();
    private String mPackageName = VoipConnectionServiceControlMain.class.getPackageName();
    private String mClassName = VoipConnectionServiceControlMain.class.getCanonicalName();
    private final HashMap<String, VoipConnection> mIdToConnection = new HashMap<>();
    private boolean mIsBound = false;
    private TelecomManager mTelecomManager = null;
    private PhoneAccount mPhoneAccount = SELF_MANAGED_CS_MAIN_ACCOUNT;
    private static int sNextNotificationId = 100;
    private NotificationManager mNotificationManager = null;
    private final String NOTIFICATION_CHANNEL_ID = mTag;
    private final String NOTIFICATION_CHANNEL_NAME = mTag + " Notification Channel";

    private final WaitUntil.ConnectionServiceImpl
            mConnectionServiceImpl = () -> VoipConnectionServiceMain.sLastConnection;

    private final IBinder mBinder = new IAppControl.Stub() {

        @Override
        public boolean isBound() {
            Log.i(mTag, String.format("isBound: [%b]", mIsBound));
            Binder.getCallingUid();
            return mIsBound;
        }

        @Override
        public UserHandle getProcessUserHandle(){
            return Process.myUserHandle();
        }

        @Override
        public int getProcessUid(){
            return Process.myUid();
        }

        @Override
        public NoDataTransaction addCall(CallAttributes callAttributes) {
            Log.i(mTag, "addCall: enter");
            try {
                List<String> stackTrace = createStackTraceList(mClassName
                        + ".addCall(" + callAttributes + ")");
                maybeInitTelecomManager();
                if (isOutgoing(callAttributes)) {
                    mTelecomManager.placeCall(callAttributes.getAddress(),
                            getExtrasWithPhoneAccount(callAttributes));
                } else {
                    mTelecomManager.addNewIncomingCall(callAttributes.getPhoneAccountHandle(),
                            getExtrasWithPhoneAccount(callAttributes));
                }
                // TelecomManager#placeCall and TelecomManager#addNewIncomingCall do not
                // return the call object directly! Instead, a ConnectionService callback will
                // populate a new connection.  The app process will wait via a while loop until
                // a new connection is populated. If a connection is not added in the given time
                // window, a TestAppException will be thrown!
                VoipConnection c = (VoipConnection) waitUntilConnectionIsNonNull(mPackageName,
                        stackTrace,
                        mConnectionServiceImpl);
                // track the connection so it can be manipulated later in the test stage
                trackConnection(c, callAttributes, stackTrace);
                // finally, return signal to the test process a call was successfully added
                return new NoDataTransaction(TestAppTransaction.Success);
            } catch (TestAppException e) {
                return new NoDataTransaction(TestAppTransaction.Failure, e);
            }
        }

        private void trackConnection(
                VoipConnection connection,
                CallAttributes callAttributes,
                List<String> stackTrace) {
            String id = waitUntilIdIsSet(mPackageName,
                    appendStackTraceList(stackTrace, mClassName + ".trackConnection"),
                    connection);
            maybeClearHoldCapabilities(connection, callAttributes);
            Log.i(mTag, String.format("trackConnection: id=[%s], connection=[%s]", id, connection));
            mIdToConnection.put(id, connection);
            connection.setIdAndResources(id,
                    new CallResources(
                            getApplicationContext(),
                            callAttributes,
                            NOTIFICATION_CHANNEL_ID,
                            sNextNotificationId++));
            // clear out the last connection since it has been added to tracking
            VoipConnectionServiceMain.sLastConnection = null;
        }

        private void maybeClearHoldCapabilities(VoipConnection c,
                CallAttributes callAttributes) {
            if (!hasSetInactiveCapabilities(callAttributes)) {
                c.clearHoldCapabilities();
            }
        }

        @Override
        public CallExceptionTransaction transitionCallStateTo(String id,
                int state,
                boolean expectSuccess,
                Bundle extras) {
            Log.i(mTag, "transitionCallStateTo: attempting to transition callId=" + id);
            try {
                VoipConnection connection = getConnectionOrThrow(id,
                        createStackTraceList(mClassName + ".transitionCallStateTo(" + (id) + ")"));

                switch (state) {
                    case STATE_DIALING -> {
                        connection.setCallToDialing();
                        Log.i(mTag, "transitionCallStateTo: setDialing");
                    }
                    case STATE_RINGING -> {
                        connection.setCallToRinging();
                        Log.i(mTag, "transitionCallStateTo: setRinging");
                    }
                    case STATE_ACTIVE -> {

                        connection.setCallToActive();
                        Log.i(mTag, "transitionCallStateTo: setActive");
                    }
                    case STATE_HOLDING -> {
                        connection.setCallToInactive();
                        Log.i(mTag, "transitionCallStateTo: setOnHold");
                    }
                    case STATE_DISCONNECTED -> {
                        connection.setCallToDisconnected(getApplicationContext());
                        Log.i(mTag, "transitionCallStateTo: setDisconnected");
                        mIdToConnection.remove(id);
                    }
                }
                Log.i(mTag, "transitionCallStateTo: done");
                return new CallExceptionTransaction(TestAppTransaction.Success);
            } catch (TestAppException e) {
                return new CallExceptionTransaction(TestAppTransaction.Failure, e);
            }
        }

        @Override
        public BooleanTransaction isMuted(String id) {
            Log.i(mTag, String.format("isMuted: id=[%s]", id));
            try {
                VoipConnection connection = getConnectionOrThrow(id,
                        createStackTraceList(mClassName + ".isMuted(" + (id) + ")"));
                return new BooleanTransaction(TestAppTransaction.Success,
                        connection.isMuted());
            } catch (TestAppException e) {
                return new BooleanTransaction(TestAppTransaction.Failure, e);
            }
        }

        @Override
        public NoDataTransaction setMuteState(String id, boolean isMuted) {
            Log.i(mTag, String.format("setMuteState: id=[%s]", id));
            try {
                VoipConnection connection = getConnectionOrThrow(id,
                        createStackTraceList(mClassName + ".setMuteState(" + (id) + ")"));
                connection.onMuteStateChanged(isMuted);
                return new NoDataTransaction(TestAppTransaction.Success);
            } catch (TestAppException e) {
                return new NoDataTransaction(TestAppTransaction.Failure, e);
            }
        }

        @Override
        public CallEndpointTransaction getCurrentCallEndpoint(String id) {
            Log.i(mTag, String.format("getCurrentCallEndpoint: id=[%s]", id));

            try {
                VoipConnection connection = getConnectionOrThrow(id,
                        createStackTraceList(mClassName + ".getCurrentCallEndpoint(" + (id) + ")"));

                waitUntilCallAudioStateIsSet(
                        mPackageName,
                        createStackTraceList(mClassName
                                + ".getCurrentCallEndpoint(id=" + id + ")"),
                        false /*isManagedConnection*/,
                        connection);

                return new CallEndpointTransaction(
                        TestAppTransaction.Success,
                        connection.getCurrentCallEndpoint()
                );
            } catch (TestAppException e) {
                return new CallEndpointTransaction(
                        TestAppTransaction.Failure,
                        e);
            }

        }

        @Override
        public AvailableEndpointsTransaction getAvailableCallEndpoints(String id) {
            Log.i(mTag, String.format("getAvailableCallEndpoints: id=[%s]", id));
            try {
                VoipConnection connection = getConnectionOrThrow(id,
                        createStackTraceList(
                                mClassName + ".getAvailableCallEndpoints(" + (id) + ")"));

                waitUntilAvailableEndpointsIsSet(
                        mPackageName,
                        createStackTraceList(mClassName
                                + ".getAvailableCallEndpoints(id=" + id + ")"),
                        false /*isManagedConnection*/,
                        connection);

                return new AvailableEndpointsTransaction(
                        TestAppTransaction.Success,
                        connection.getCallEndpoints()
                );
            } catch (TestAppException e) {
                return new AvailableEndpointsTransaction(
                        TestAppTransaction.Failure,
                        e);
            }
        }

        @Override
        public NoDataTransaction requestCallEndpointChange(String id,
                CallEndpoint callEndpoint) throws RemoteException {
            Log.i(mTag, String.format("requestCallEndpointChange: id=[%s]", id));
            try {
                List<String> stackTrace = createStackTraceList(mClassName
                        + ".requestCallEndpointChange(" + (id) + ")");

                VoipConnection connection = getConnectionOrThrow(id, stackTrace);
                final CountDownLatch latch = new CountDownLatch(1);
                final LatchedEndpointOutcomeReceiver outcome = new LatchedEndpointOutcomeReceiver(
                        latch);

                connection.requestCallEndpointChange(callEndpoint, Runnable::run, outcome);
                // await a count down in the CountDownLatch to signify success
                assertCountDownLatchWasCalled(
                        mPackageName,
                        stackTrace,
                        "expected:<CallControl#requestCallEndpointChange to complete via"
                                + " onResult or onError>  "
                                + "actual<Timeout waiting for the CountDownLatch to complete.>",
                        latch);

                return new NoDataTransaction(TestAppTransaction.Success);
            } catch (TestAppException e) {
                return new NoDataTransaction(TestAppTransaction.Failure, e);
            }
        }

        @Override
        public NoDataTransaction registerDefaultPhoneAccount() {
            Log.i(mTag, "registerDefaultPhoneAccount:");
            mTelecomManager.registerPhoneAccount(mPhoneAccount);
            return new NoDataTransaction(TestAppTransaction.Success);
        }

        @Override
        public PhoneAccountTransaction getDefaultPhoneAccount() {
            Log.i(mTag, "getDefaultPhoneAccount:");
            return new PhoneAccountTransaction(TestAppTransaction.Success, mPhoneAccount);
        }

        @Override
        public void registerCustomPhoneAccount(PhoneAccount account) {
            Log.i(mTag, String.format("registerCustomPhoneAccount: account=[%s]", account));
            mTelecomManager.registerPhoneAccount(account);
        }

        @Override
        public void unregisterPhoneAccountWithHandle(PhoneAccountHandle handle) {
            Log.i(mTag, String.format("registerCustomPhoneAccount: handle=[%s]", handle));
            mTelecomManager.unregisterPhoneAccount(handle);
        }

        @Override
        public List<PhoneAccountHandle> getOwnAccountHandlesForApp() {
            Log.i(mTag, "getOwnAccountHandlesForApp:");
            return mTelecomManager.getOwnSelfManagedPhoneAccounts();
        }

        @Override
        public List<PhoneAccount> getRegisteredPhoneAccounts() {
            Log.i(mTag, "getRegisteredPhoneAccounts");
            return mTelecomManager.getRegisteredPhoneAccounts();
        }

        public BooleanTransaction isNotificationPostedForCall(String callId) {
            List<String> stackTrace = createStackTraceList(mClassName
                    + ".isNotificationPostedForCall(" + (callId) + ")");
            try {
                int targetNotificationId = getConnectionOrThrow(callId,
                        stackTrace).getCallResources()
                        .getNotificationId();

                return new BooleanTransaction(TestAppTransaction.Success,
                        isTargetNotificationPosted(getApplicationContext(),
                                targetNotificationId));
            } catch (TestAppException e) {
                return new BooleanTransaction(TestAppTransaction.Failure, e);
            }
        }

        @Override
        public NoDataTransaction removeNotificationForCall(String callId) {
            List<String> stackTrace = createStackTraceList(mClassName
                    + ".removeNotificationForCall(" + (callId) + ")");
            try {
                CallResources callResources =
                        getConnectionOrThrow(callId, stackTrace).getCallResources();
                callResources.clearCallNotification(getApplicationContext());
                return new NoDataTransaction(TestAppTransaction.Success);
            } catch (TestAppException e) {
                return new NoDataTransaction(TestAppTransaction.Failure, e);
            }
        }

        private VoipConnection getConnectionOrThrow(String id, List<String> stackTrace) {
            if (!mIdToConnection.containsKey(id)) {
                throw new TestAppException(mPackageName,
                        appendStackTraceList(stackTrace, mClassName + ".getConnectionOrThrow"),
                        "expect:<A Connection object in the mIdToConnection map>"
                                + "actual: missing Connection object for key=" + id);
            }
            return mIdToConnection.get(id);
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        if (VOIP_CS_CONTROL_INTERFACE_ACTION.equals(intent.getAction())) {
            Log.i(mTag, "onBind: return control interface.");
            mIsBound = true;
            maybeInitTelecomManager();
            maybeInitNotificationChannel();
            setDefaultPhoneAccountBasedOffIntent(intent);
            mTelecomManager.registerPhoneAccount(mPhoneAccount);
            return mBinder;
        }
        Log.i(mTag, "onBind: invalid intent.");
        return null;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(mTag, "VoipConnectionServiceControlClone: onUnbind");
        for (VoipConnection connection : mIdToConnection.values()) {
            connection.setDisconnected(
                    new DisconnectCause(DisconnectCause.LOCAL, "VoipCS is Unbinding"));
        }
        mIdToConnection.clear();
        mTelecomManager.unregisterPhoneAccount(mPhoneAccount.getAccountHandle());
        // delete the call channel
        NotificationUtils.deleteNotificationChannel(
                getApplicationContext(),
                NOTIFICATION_CHANNEL_ID);
        mIsBound = false;
        return super.onUnbind(intent);
    }

    private void maybeInitTelecomManager() {
        Log.d(mTag, "maybeInitTelecomManager:");
        if (mTelecomManager == null) {
            mTelecomManager = getSystemService(TelecomManager.class);
        }
    }

    private void maybeInitNotificationChannel() {
        Log.d(mTag, "maybeInitNotificationChannel:");
        if (mNotificationManager == null) {
            mNotificationManager = getSystemService(NotificationManager.class);
            mNotificationManager.createNotificationChannel(new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    NOTIFICATION_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT));
        }
    }

    private void setDefaultPhoneAccountBasedOffIntent(Intent intent) {
        if (intent.getPackage().equals(SELF_MANAGED_CS_CLONE_PACKAGE_NAME)) {
            mPhoneAccount = SELF_MANAGED_CS_CLONE_ACCOUNT;
            mTag = "VoipConnectionServiceControlClone";
            mClassName = SELF_MANAGED_CS_CLONE_PACKAGE_NAME + "." + mTag;
            mPackageName = SELF_MANAGED_CS_CLONE_PACKAGE_NAME;
        } else {
            mPhoneAccount = SELF_MANAGED_CS_MAIN_ACCOUNT;
        }
        Log.i(mTag, String.format("setDefaultPhoneAccountBasedOffIntent:"
                + " mPhoneAccount=[%s], intent=[%s]", mPhoneAccount, intent));
    }
}
