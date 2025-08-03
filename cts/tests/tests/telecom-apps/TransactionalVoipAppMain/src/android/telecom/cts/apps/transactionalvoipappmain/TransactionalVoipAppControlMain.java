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

package android.telecom.cts.apps.transactionalvoipappmain;

import static android.telecom.Call.STATE_ACTIVE;
import static android.telecom.Call.STATE_DISCONNECTED;
import static android.telecom.Call.STATE_HOLDING;
import static android.telecom.cts.apps.AssertOutcome.assertCountDownLatchWasCalled;
import static android.telecom.cts.apps.NotificationUtils.isTargetNotificationPosted;
import static android.telecom.cts.apps.StackTraceUtil.appendStackTraceList;
import static android.telecom.cts.apps.StackTraceUtil.createStackTraceList;
import static android.telecom.cts.apps.TelecomTestApp.TRANSACTIONAL_CLONE_ACCOUNT;
import static android.telecom.cts.apps.TelecomTestApp.TRANSACTIONAL_CLONE_PACKAGE_NAME;
import static android.telecom.cts.apps.TelecomTestApp.TRANSACTIONAL_MAIN_DEFAULT_ACCOUNT;
import static android.telecom.cts.apps.TelecomTestApp.T_CONTROL_INTERFACE_ACTION;
import static android.telecom.cts.apps.WaitUntil.waitUntilAvailableEndpointAreSet;
import static android.telecom.cts.apps.WaitUntil.waitUntilCurrentCallEndpointIsSet;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.OutcomeReceiver;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.telecom.CallAttributes;
import android.telecom.CallControl;
import android.telecom.CallEndpoint;
import android.telecom.CallException;
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
import android.telecom.cts.apps.LatchedOutcomeReceiver;
import android.telecom.cts.apps.NoDataTransaction;
import android.telecom.cts.apps.NotificationUtils;
import android.telecom.cts.apps.PhoneAccountTransaction;
import android.telecom.cts.apps.TestAppException;
import android.telecom.cts.apps.TestAppTransaction;
import android.telecom.cts.apps.TransactionalCall;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class TransactionalVoipAppControlMain extends Service {
    private String mTag = TransactionalVoipAppControlMain.class.getSimpleName();
    private String mPackageName = TransactionalVoipAppControlMain.class.getPackageName();
    private String mClassName = TransactionalVoipAppControlMain.class.getCanonicalName();
    private static int sNextNotificationId = 200;
    private TelecomManager mTelecomManager = null;
    private NotificationManager mNotificationManager = null;
    private boolean mIsBound = false;
    public PhoneAccount mPhoneAccount = TRANSACTIONAL_MAIN_DEFAULT_ACCOUNT;
    private final String NOTIFICATION_CHANNEL_ID = mTag;
    private final String NOTIFICATION_CHANNEL_NAME = mPackageName + " Notification Channel";
    private final HashMap<String, TransactionalCall> mIdToControl = new HashMap<>();

    private final IBinder mBinder = new IAppControl.Stub() {

        @Override
        public boolean isBound() throws RemoteException {
            Log.i(mTag, String.format("isBound: [%b]", mIsBound));
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
        public NoDataTransaction addCall(CallAttributes callAttributes) throws RemoteException {
            Log.i(mTag, String.format("addCall: w/ attributes=[%s]", callAttributes));
            try {
                List<String> stackTrace = createStackTraceList(mClassName
                        + ".addCall(" + callAttributes + ")");
                maybeInitTelecomManager();
                final CountDownLatch latch = new CountDownLatch(1);
                final TransactionalCall call = new TransactionalCall(getApplicationContext(),
                        new CallResources(
                                getApplicationContext(),
                                callAttributes,
                                NOTIFICATION_CHANNEL_ID,
                                sNextNotificationId++));

                mTelecomManager.addCall(callAttributes, Runnable::run, new OutcomeReceiver<>() {
                    @Override
                    public void onResult(CallControl callControl) {
                        Log.i(mTag, "onResult: adding callControl to callObject");
                        verifyCallControlIsNonNull(callControl, stackTrace);
                        call.setCallControlAndId(callControl);
                        mIdToControl.put(call.getId(), call);
                        latch.countDown();
                    }

                    @Override
                    public void onError(@NonNull CallException exception) {
                        Log.i(mTag, "onError: exception: " + exception);
                        throw new TestAppException(mPackageName, stackTrace,
                                "expected:<CallControl transaction to complete with onResult()>"
                                        + "actual:<Failed with CallException=["
                                        + exception.getMessage() + "]>");
                    }
                }, call.mHandshakes, call.mEvents);

                assertCountDownLatchWasCalled(
                        mPackageName,
                        stackTrace,
                        "expected: TelecomManager#addCall to return the CallControl"
                                + " object within <time> window  actual: timeout",
                        latch);

                return new NoDataTransaction(TestAppTransaction.Success);
            } catch (TestAppException e) {
                return new NoDataTransaction(TestAppTransaction.Failure, e);
            }
        }

        private void verifyCallControlIsNonNull(CallControl callControl, List<String> stackTrace)
                throws TestAppException {
            if (callControl == null) {
                throw new TestAppException(mPackageName, stackTrace,
                        "expected:<CallControl to be non-null>"
                                + "actual:<The CallControl object is Null>");
            }
        }

        @Override
        public CallExceptionTransaction transitionCallStateTo(String id,
                int state,
                boolean expectSuccess,
                Bundle extras) throws RemoteException {
            Log.i(mTag, "transitionCallStateTo: attempting to transition callId=" + id);
            List<String> stackTrace = createStackTraceList(mClassName
                    + ".transitionCallStateTo(" + (id) + ")");
            try {
                final CountDownLatch latch = new CountDownLatch(1);
                final LatchedOutcomeReceiver outcome =
                        new LatchedOutcomeReceiver(latch);

                TransactionalCall call = getCallOrThrowError(id, stackTrace);
                CallControl callControl = call.getCallControl();

                if (callControl == null) {
                    throw new TestAppException(mPackageName, stackTrace,
                            String.format("CalControl is NULL for callId=[%s]", id));
                }

                switch (state) {
                    case STATE_ACTIVE -> {
                        call.setActive(outcome, extras);
                    }
                    case STATE_HOLDING -> {
                        call.setInactive(outcome);
                    }
                    case STATE_DISCONNECTED -> {
                        call.disconnect(outcome, extras);
                        mIdToControl.remove(id);
                    }
                }
                Log.i(mTag, "transitionCallStateTo: done");

                // execution should be paused until the OutcomeReceiver#onResult or
                // OutcomeReceiver#onError is called for the given transaction. If a timeout occurs,
                // bubble up the timeout exception
                assertCountDownLatchWasCalled(mPackageName, stackTrace,
                        "expected:<CountDownLatch to count down within the time window>"
                                + "  actual:<Timeout; Inspect the Transactions to determine cause>",
                        latch);

                // if the call control transaction should have resulted in OutcomeReceiver#onResult
                // being called instead of the OutcomeReceiver#onError, fail the test and
                // bubble up the exception
                verifyCallControlTransactionWasSuccessful(expectSuccess, stackTrace, outcome);

                // There may be times when it is EXPECTED (from the Test) that the CallControl
                // transaction fails via OutcomeReceiver#onError. In these cases, return the
                // CallException to the test.
                if (testExpectsOnErrorAndShouldReturnCallException(expectSuccess, outcome)) {
                    return new CallExceptionTransaction(TestAppTransaction.Success,
                            outcome.getmCallException());
                }

                // otherwise, the CallControl transaction completed successfully!
                return new CallExceptionTransaction(TestAppTransaction.Success);
            } catch (TestAppException e) {
                return new CallExceptionTransaction(TestAppTransaction.Failure, e);
            }
        }

        private void verifyCallControlTransactionWasSuccessful(boolean expectSuccess,
                List<String> stackTrace,
                LatchedOutcomeReceiver outcome)
                throws TestAppException {
            if (expectSuccess && !outcome.wasSuccessful()) {
                throw new TestAppException(mPackageName, stackTrace,
                        "expected:<CallControl transaction to complete with onResult()>"
                                + "actual:<Failed with CallException=["
                                + outcome.getmCallException().getMessage() + "]>");
            }
        }

        private boolean testExpectsOnErrorAndShouldReturnCallException(boolean expectSuccess,
                LatchedOutcomeReceiver outcome) {
            return !expectSuccess && !outcome.wasSuccessful();
        }

        @Override
        public BooleanTransaction isMuted(String id) throws RemoteException {
            Log.i(mTag, String.format("isMuted: id=[%s]", id));
            try {
                TransactionalCall call = getCallOrThrowError(id,
                        createStackTraceList(mClassName + ".isMuted(" + (id) + ")"));

                return new BooleanTransaction(TestAppTransaction.Success,
                        call.mEvents.getIsMuted());
            } catch (TestAppException e) {
                return new BooleanTransaction(TestAppTransaction.Failure, e);
            }
        }

        @Override
        public NoDataTransaction setMuteState(String id, boolean isMuted) {
            Log.i(mTag, String.format("setMuteState: id=[%s], isMuted=[%b]", id, isMuted));
            // TODO:: b/310669304
            return new NoDataTransaction(TestAppTransaction.Failure,
                    new TestAppException(
                            mPackageName,
                            createStackTraceList(mClassName + "setMuteState"),
                            "TransactionalVoipApp* does not implement setMuteState b/c there is"
                                    + " no existing API in android.telecom.CallControl!")
            );
        }

        @Override
        public CallEndpointTransaction getCurrentCallEndpoint(String id) throws RemoteException {
            Log.i(mTag, String.format("getCurrentCallEndpoint: id=[%s]", id));
            try {
                TransactionalCall call = getCallOrThrowError(id,
                        createStackTraceList(mClassName + ".getCurrentCallEndpoint(" + (id) + ")"));

                waitUntilCurrentCallEndpointIsSet(
                        mPackageName,
                        createStackTraceList(mClassName
                                + ".getCurrentCallEndpoint(id=" + id + ")"),
                        call.mEvents);

                return new CallEndpointTransaction(
                        TestAppTransaction.Success,
                        call.mEvents.getCurrentCallEndpoint());
            } catch (TestAppException e) {
                return new CallEndpointTransaction(TestAppTransaction.Failure, e);
            }
        }

        @Override
        public AvailableEndpointsTransaction getAvailableCallEndpoints(String id)
                throws RemoteException {
            Log.i(mTag, String.format("getAvailableCallEndpoints: id=[%s]", id));
            try {
                TransactionalCall call = getCallOrThrowError(id,
                        createStackTraceList(mClassName
                                + ".getAvailableCallEndpoints(" + (id) + ")"));

                waitUntilAvailableEndpointAreSet(
                        mPackageName,
                        createStackTraceList(mClassName
                                + ".getAvailableCallEndpoints(id=" + id + ")"),
                        call.mEvents);

                return new AvailableEndpointsTransaction(
                        TestAppTransaction.Success,
                        call.mEvents.getCallEndpoints());
            } catch (TestAppException e) {
                return new AvailableEndpointsTransaction(TestAppTransaction.Failure, e);
            }
        }

        @Override
        public NoDataTransaction requestCallEndpointChange(String id, CallEndpoint callEndpoint)
                throws RemoteException {
            Log.i(mTag, String.format("requestCallEndpointChange:"
                    + " id=[%s], callEndpoint=[%s]", id, callEndpoint));
            try {
                List<String> stackTrace = createStackTraceList(mClassName
                        + ".requestCallEndpointChange(" + (id) + ")");

                TransactionalCall call = getCallOrThrowError(id, stackTrace);

                final CountDownLatch latch = new CountDownLatch(1);
                final LatchedOutcomeReceiver outcome = new LatchedOutcomeReceiver(latch);

                // send the call control request
                call.getCallControl().requestCallEndpointChange(callEndpoint, Runnable::run,
                        outcome);
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
            Log.i(mTag, String.format("registerDefaultPhoneAccount: pa=[%s]", mPhoneAccount));
            mTelecomManager.registerPhoneAccount(mPhoneAccount);
            return new NoDataTransaction(TestAppTransaction.Success);
        }

        @Override
        public PhoneAccountTransaction getDefaultPhoneAccount() {
            Log.i(mTag, String.format("getDefaultPhoneAccount: pa=[%s]", mPhoneAccount));
            return new PhoneAccountTransaction(TestAppTransaction.Success, mPhoneAccount);
        }

        @Override
        public void registerCustomPhoneAccount(PhoneAccount account) {
            Log.i(mTag, String.format("registerCustomPhoneAccount: account=[%s]", account));
            mTelecomManager.registerPhoneAccount(account);
        }

        @Override
        public void unregisterPhoneAccountWithHandle(PhoneAccountHandle handle) {
            Log.i(mTag, String.format("unregisterPhoneAccountWithHandle: handle=[%s]", handle));
            mTelecomManager.unregisterPhoneAccount(handle);
        }

        @Override
        public List<PhoneAccountHandle> getOwnAccountHandlesForApp() {
            Log.i(mTag, "getOwnAccountHandlesForApp");
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
                int targetNotificationId = getCallOrThrowError(callId,
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
                TransactionalCall call = getCallOrThrowError(callId, stackTrace);
                CallResources callResources = call.getCallResources();
                callResources.clearCallNotification(getApplicationContext());
                return new NoDataTransaction(TestAppTransaction.Success);
            } catch (TestAppException e) {
                return new NoDataTransaction(TestAppTransaction.Failure, e);
            }
        }

        private TransactionalCall getCallOrThrowError(String id, List<String> stackTrace) {
            if (!mIdToControl.containsKey(id)) {
                throw new TestAppException(mPackageName,
                        appendStackTraceList(stackTrace, mClassName + ".getCallOrThrowError"),
                        "expect:<A TransactionalCall object in the mIdToControl map>"
                                + "actual: missing TransactionalCall object for key=" + id);
            }
            return mIdToControl.get(id);
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        if (T_CONTROL_INTERFACE_ACTION.equals(intent.getAction())) {
            Log.i(mTag, String.format("onBind: return control interface w/ intent=[%s]", intent));
            mIsBound = true;
            maybeInitTelecomManager();
            initNotificationChannel();
            setDefaultPhoneAccountBasedOffIntent(intent);
            mTelecomManager.registerPhoneAccount(mPhoneAccount);
            return mBinder;
        }
        Log.i(mTag, "onBind: return control interface=" + intent);
        return null;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(mTag, String.format("onUnbind: with intent=[%s]", intent));
        for (TransactionalCall call : mIdToControl.values()) {
            call.getCallControl().disconnect(
                    new DisconnectCause(
                            DisconnectCause.LOCAL, "onUnbind for TransactionalApp"),
                    Runnable::run,
                    result -> {
                    });
            call.getCallResources().destroyResources(getApplicationContext());
        }
        mIdToControl.clear();
        Log.i(mTag, String.format("onUnbind: mPhoneAccount=[%s]", mPhoneAccount));
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

    private void initNotificationChannel() {
        Log.d(mTag, "initNotificationChannel:");
        if (mNotificationManager == null) {
            mNotificationManager = getSystemService(NotificationManager.class);
            mNotificationManager.createNotificationChannel(new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    NOTIFICATION_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT));
        }
    }

    public void setDefaultPhoneAccountBasedOffIntent(Intent intent) {
        if (intent.getPackage().equals(TRANSACTIONAL_CLONE_PACKAGE_NAME)) {
            mPhoneAccount = TRANSACTIONAL_CLONE_ACCOUNT;
            mTag = "TransactionalVoipAppControlClone";
            mClassName = TRANSACTIONAL_CLONE_PACKAGE_NAME + "." + mTag;
            mPackageName = TRANSACTIONAL_CLONE_PACKAGE_NAME;
        } else {
            mPhoneAccount = TRANSACTIONAL_MAIN_DEFAULT_ACCOUNT;
        }
        Log.i(mTag, String.format("setDefaultPhoneAccountBasedOffIntent:"
                + " mPhoneAccount=[%s], intent=[%s]", mPhoneAccount, intent));
    }
}
