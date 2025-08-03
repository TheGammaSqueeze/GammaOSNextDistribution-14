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

package android.telecom.cts.apps.managedapp;

import static android.telecom.Call.STATE_ACTIVE;
import static android.telecom.Call.STATE_DIALING;
import static android.telecom.Call.STATE_DISCONNECTED;
import static android.telecom.Call.STATE_HOLDING;
import static android.telecom.Call.STATE_RINGING;
import static android.telecom.cts.apps.AssertOutcome.assertCountDownLatchWasCalled;
import static android.telecom.cts.apps.AttributesUtil.getExtrasWithPhoneAccount;
import static android.telecom.cts.apps.AttributesUtil.hasSetInactiveCapabilities;
import static android.telecom.cts.apps.AttributesUtil.isOutgoing;
import static android.telecom.cts.apps.TelecomTestApp.CONTROL_INTERFACE_ACTION;
import static android.telecom.cts.apps.NotificationUtils.isTargetNotificationPosted;
import static android.telecom.cts.apps.StackTraceUtil.appendStackTraceList;
import static android.telecom.cts.apps.StackTraceUtil.createStackTraceList;
import static android.telecom.cts.apps.WaitUntil.waitUntilAvailableEndpointsIsSet;
import static android.telecom.cts.apps.WaitUntil.waitUntilCallAudioStateIsSet;
import static android.telecom.cts.apps.WaitUntil.waitUntilConnectionIsNonNull;
import static android.telecom.cts.apps.WaitUntil.waitUntilIdIsSet;

import android.app.Service;
import android.content.Intent;
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
import android.telecom.cts.apps.ManagedConnection;
import android.telecom.cts.apps.NoDataTransaction;
import android.telecom.cts.apps.PhoneAccountTransaction;
import android.telecom.cts.apps.TestAppException;
import android.telecom.cts.apps.TestAppTransaction;
import android.telecom.cts.apps.WaitUntil;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class ManagedAppControl extends Service {
    private static final String TAG = ManagedAppControl.class.getSimpleName();
    private static final String CLASS_NAME = ManagedAppControl.class.getCanonicalName();
    private static final String PACKAGE_NAME = ManagedAppControl.class.getPackageName();
    private final HashMap<String, ManagedConnection> mIdToConnection = new HashMap<>();
    private boolean mIsBound = false;
    private TelecomManager mTelecomManager = null;

    private final WaitUntil.ConnectionServiceImpl
            mConnectionServiceImpl = () -> ManagedConnectionService.sLastConnection;

    private final IBinder mBinder = new IAppControl.Stub() {

        @Override
        public boolean isBound() {
            Log.i(TAG, String.format("isBound: [%b]", mIsBound));
            return mIsBound;
        }

        @Override
        public UserHandle getProcessUserHandle() {
            return Process.myUserHandle();
        }

        @Override
        public int getProcessUid() {
            return Process.myUid();
        }

        @Override
        public NoDataTransaction addCall(CallAttributes callAttributes) {
            Log.i(TAG, "addCall: enter");
            try {
                List<String> stackTrace = createStackTraceList(CLASS_NAME
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
                ManagedConnection connection = (ManagedConnection) waitUntilConnectionIsNonNull(
                        PACKAGE_NAME,
                        stackTrace,
                        mConnectionServiceImpl);
                // track the connection so it can be manipulated later in the test stage
                trackConnection(connection, callAttributes, stackTrace);
                // signal to the test process the call has been added successfully
                return new NoDataTransaction(TestAppTransaction.Success);
            } catch (TestAppException e) {
                return new NoDataTransaction(TestAppTransaction.Failure, e);
            }
        }

        private void trackConnection(
                ManagedConnection connection,
                CallAttributes callAttributes,
                List<String> stackTrace) {
            String id = waitUntilIdIsSet(PACKAGE_NAME,
                    appendStackTraceList(stackTrace, CLASS_NAME + ".trackConnection"),
                    connection);
            maybeClearHoldCapabilities(connection, callAttributes);
            Log.i(TAG, String.format("trackConnection: id=[%s], connection=[%s]", id, connection));
            mIdToConnection.put(id, connection);
            // clear out the last connection since it has been added to tracking
            ManagedConnectionService.sLastConnection = null;
        }

        private void maybeClearHoldCapabilities(ManagedConnection c,
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
            Log.i(TAG, "transitionCallStateTo: attempting to transition callId=" + id);
            try {
                List<String> stackTrace =
                        createStackTraceList(CLASS_NAME + ".transitionCallStateTo(" + (id) + ")");

                ManagedConnection connection = getConnectionOrThrow(id, stackTrace);

                switch (state) {
                    case STATE_DIALING -> {
                        connection.setCallToDialing();
                        Log.i(TAG, "transitionCallStateTo: setDialing");
                    }
                    case STATE_RINGING -> {
                        connection.setCallToRinging();
                        Log.i(TAG, "transitionCallStateTo: setRinging");
                    }
                    case STATE_ACTIVE -> {
                        connection.setCallToActive();
                        Log.i(TAG, "transitionCallStateTo: setActive");
                    }
                    case STATE_HOLDING -> {
                        connection.setCallToInactive();
                        Log.i(TAG, "transitionCallStateTo: setOnHold");
                    }
                    case STATE_DISCONNECTED -> {
                        connection.setCallToDisconnected(getApplicationContext());
                        Log.i(TAG, "transitionCallStateTo: setDisconnected");
                        mIdToConnection.remove(id);
                    }
                }
                Log.i(TAG, "transitionCallStateTo: done");
                return new CallExceptionTransaction(TestAppTransaction.Success);
            } catch (TestAppException e) {
                return new CallExceptionTransaction(TestAppTransaction.Failure, e);
            }
        }

        @Override
        public BooleanTransaction isMuted(String id) {
            Log.i(TAG, String.format("isMuted: id=[%s]", id));
            try {
                ManagedConnection connection = getConnectionOrThrow(id,
                        createStackTraceList(CLASS_NAME + ".transitionCallStateTo(" + (id) + ")"));
                return new BooleanTransaction(TestAppTransaction.Success,
                        connection.isMuted());
            } catch (TestAppException e) {
                return new BooleanTransaction(TestAppTransaction.Failure, e);
            }
        }

        @Override
        public NoDataTransaction setMuteState(String id, boolean isMuted) {
            Log.i(TAG, String.format("setMuteState: id=[%s]", id));
            try {
                ManagedConnection connection = getConnectionOrThrow(id,
                        createStackTraceList(CLASS_NAME + ".transitionCallStateTo(" + (id) + ")"));
                connection.onMuteStateChanged(isMuted);
                return new NoDataTransaction(TestAppTransaction.Success);
            } catch (TestAppException e) {
                return new NoDataTransaction(TestAppTransaction.Failure, e);
            }
        }

        @Override
        public CallEndpointTransaction getCurrentCallEndpoint(String id) {
            Log.i(TAG, String.format("getCurrentCallEndpoint: id=[%s]", id));
            try {
                ManagedConnection connection = getConnectionOrThrow(id,
                        createStackTraceList(CLASS_NAME + ".transitionCallStateTo(" + (id) + ")"));

                waitUntilCallAudioStateIsSet(PACKAGE_NAME,
                        createStackTraceList(CLASS_NAME + ".getCurrentCallEndpoint(id=" + id + ")"),
                        true /*isManagedConnection */,
                        connection);

                return new CallEndpointTransaction(
                        TestAppTransaction.Success,
                        connection.getCurrentCallEndpoint());
            } catch (TestAppException e) {
                return new CallEndpointTransaction(
                        TestAppTransaction.Failure,
                        e);
            }
        }

        @Override
        public AvailableEndpointsTransaction getAvailableCallEndpoints(String id) {
            Log.i(TAG, String.format("getAvailableCallEndpoints: id=[%s]", id));
            try {
                ManagedConnection connection = getConnectionOrThrow(id,
                        createStackTraceList(CLASS_NAME + ".transitionCallStateTo(" + (id) + ")"));

                waitUntilAvailableEndpointsIsSet(PACKAGE_NAME,
                        createStackTraceList(CLASS_NAME
                                + ".getAvailableCallEndpoints(id=" + id + ")"),
                        true /*isManagedConnection */,
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
            Log.i(TAG, String.format("requestCallEndpointChange: id=[%s]", id));
            try {
                List<String> stackTrace = createStackTraceList(CLASS_NAME
                        + ".requestCallEndpointChange(" + (id) + ")");

                ManagedConnection connection = getConnectionOrThrow(id, stackTrace);
                final CountDownLatch latch = new CountDownLatch(1);
                final LatchedEndpointOutcomeReceiver outcome = new LatchedEndpointOutcomeReceiver(
                        latch);

                // send the call control request
                connection.requestCallEndpointChange(callEndpoint, Runnable::run, outcome);
                // await a count down in the CountDownLatch to signify success
                assertCountDownLatchWasCalled(
                        PACKAGE_NAME,
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
            return new NoDataTransaction(TestAppTransaction.Failure, new TestAppException(
                    PACKAGE_NAME,
                    createStackTraceList(CLASS_NAME + "registerDefaultPhoneAccount"),
                    "ManagedAppControl does not implement registerDefaultPhoneAccount b/c the"
                            + " the account must be registered from the Test Process "
                            + " (ex. android.telecom.cts) or a security exception will occur."));
        }

        @Override
        public PhoneAccountTransaction getDefaultPhoneAccount() {
            return new PhoneAccountTransaction(TestAppTransaction.Failure, new TestAppException(
                    PACKAGE_NAME,
                    createStackTraceList(CLASS_NAME + "getDefaultPhoneAccount"),
                    "ManagedAppControl does not implement getDefaultPhoneAccount b/c the"
                            + " the account must be registered from the Test Process "
                            + " (ex. android.telecom.cts) or a security exception will occur."));
        }

        @Override
        public void registerCustomPhoneAccount(PhoneAccount account) {
            Log.i(TAG, String.format("registerCustomPhoneAccount: acct=[%s]", account));
            mTelecomManager.registerPhoneAccount(account);
        }

        @Override
        public void unregisterPhoneAccountWithHandle(PhoneAccountHandle handle) {
            Log.i(TAG, String.format("unregisterPhoneAccountWithHandle: handle=[%s]", handle));
            mTelecomManager.unregisterPhoneAccount(handle);
        }

        @Override
        public List<PhoneAccountHandle> getOwnAccountHandlesForApp() {
            Log.i(TAG, "getOwnAccountHandlesForApp");
            return mTelecomManager.getOwnSelfManagedPhoneAccounts();
        }

        @Override
        public List<PhoneAccount> getRegisteredPhoneAccounts() {
            Log.i(TAG, "getRegisteredPhoneAccounts");
            return mTelecomManager.getRegisteredPhoneAccounts();
        }

        public BooleanTransaction isNotificationPostedForCall(String callId) {
            List<String> stackTrace = createStackTraceList(CLASS_NAME
                    + ".isNotificationPostedForCall(" + (callId) + ")");
            return new BooleanTransaction(TestAppTransaction.Failure,
                    new TestAppException(PACKAGE_NAME, stackTrace,
                            "CallStyle notification functionality is not supported by the Managed"
                                    + " App"));
        }

        @Override
        public NoDataTransaction removeNotificationForCall(String callId) {
            List<String> stackTrace = createStackTraceList(CLASS_NAME
                    + ".removeNotificationForCall(" + (callId) + ")");
            return new NoDataTransaction(TestAppTransaction.Failure,
                    new TestAppException(PACKAGE_NAME, stackTrace,
                            "CallStyle notification functionality is not supported by the Managed"
                                    + " App"));
        }

        private ManagedConnection getConnectionOrThrow(String id, List<String> stackTrace) {
            if (!mIdToConnection.containsKey(id)) {
                throw new TestAppException(PACKAGE_NAME,
                        appendStackTraceList(stackTrace, CLASS_NAME + ".getConnectionOrThrow"),
                        "expect:<A Connection object in the mIdToConnection map>"
                                + "actual:<Missing Connection object for key=" + id + ">");
            }
            return mIdToConnection.get(id);
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        if (CONTROL_INTERFACE_ACTION.equals(intent.getAction())) {
            Log.i(TAG, "onBind: return control interface.");
            mIsBound = true;
            maybeInitTelecomManager();
            return mBinder;
        }
        Log.i(TAG, "onBind: invalid intent.");
        return null;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "ManagedAppControl: onUnbind");
        // disconnect all ongoing calls
        for (ManagedConnection connection : mIdToConnection.values()) {
            connection.setCallToDisconnected(getApplicationContext(),
                    new DisconnectCause(DisconnectCause.LOCAL, "Managed-App is Unbinding"));
        }
        // clear containers
        mIdToConnection.clear();
        // complete unbind
        mIsBound = false;
        return super.onUnbind(intent);
    }

    private void maybeInitTelecomManager() {
        if (mTelecomManager == null) {
            mTelecomManager = getSystemService(TelecomManager.class);
        }
    }
}
