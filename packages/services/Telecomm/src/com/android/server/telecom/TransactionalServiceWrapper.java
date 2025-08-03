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

package com.android.server.telecom;

import static android.telecom.CallException.CODE_CALL_IS_NOT_BEING_TRACKED;
import static android.telecom.CallException.TRANSACTION_EXCEPTION_KEY;
import static android.telecom.TelecomManager.TELECOM_TRANSACTION_SUCCESS;

import android.content.ComponentName;
import android.os.Bundle;
import android.os.IBinder;
import android.os.OutcomeReceiver;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.telecom.CallEndpoint;
import android.telecom.CallException;
import android.telecom.CallStreamingService;
import android.telecom.DisconnectCause;
import android.telecom.Log;
import android.telecom.PhoneAccountHandle;
import android.text.TextUtils;

import androidx.annotation.VisibleForTesting;

import com.android.internal.telecom.ICallControl;
import com.android.internal.telecom.ICallEventCallback;
import com.android.server.telecom.voip.CallEventCallbackAckTransaction;
import com.android.server.telecom.voip.EndpointChangeTransaction;
import com.android.server.telecom.voip.HoldCallTransaction;
import com.android.server.telecom.voip.EndCallTransaction;
import com.android.server.telecom.voip.MaybeHoldCallForNewCallTransaction;
import com.android.server.telecom.voip.RequestNewActiveCallTransaction;
import com.android.server.telecom.voip.SerialTransaction;
import com.android.server.telecom.voip.SetMuteStateTransaction;
import com.android.server.telecom.voip.RequestVideoStateTransaction;
import com.android.server.telecom.voip.TransactionManager;
import com.android.server.telecom.voip.VoipCallTransaction;
import com.android.server.telecom.voip.VoipCallTransactionResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implements {@link android.telecom.CallEventCallback} and {@link android.telecom.CallControl}
 * on a per-client basis which is tied to a {@link PhoneAccountHandle}
 */
public class TransactionalServiceWrapper implements
        ConnectionServiceFocusManager.ConnectionServiceFocus {
    private static final String TAG = TransactionalServiceWrapper.class.getSimpleName();

    // CallControl : Client (ex. voip app) --> Telecom
    public static final String SET_ACTIVE = "SetActive";
    public static final String SET_INACTIVE = "SetInactive";
    public static final String ANSWER = "Answer";
    public static final String DISCONNECT = "Disconnect";
    public static final String START_STREAMING = "StartStreaming";
    public static final String REQUEST_VIDEO_STATE = "RequestVideoState";

    // CallEventCallback : Telecom --> Client (ex. voip app)
    public static final String ON_SET_ACTIVE = "onSetActive";
    public static final String ON_SET_INACTIVE = "onSetInactive";
    public static final String ON_ANSWER = "onAnswer";
    public static final String ON_DISCONNECT = "onDisconnect";
    public static final String ON_STREAMING_STARTED = "onStreamingStarted";

    private final CallsManager mCallsManager;
    private final ICallEventCallback mICallEventCallback;
    private final PhoneAccountHandle mPhoneAccountHandle;
    private final TransactionalServiceRepository mRepository;
    private ConnectionServiceFocusManager.ConnectionServiceFocusListener mConnSvrFocusListener;
    // init when constructor is called
    private final ConcurrentHashMap<String, Call> mTrackedCalls = new ConcurrentHashMap<>();
    private final TelecomSystem.SyncRoot mLock;
    private final String mPackageName;
    // needs to be non-final for testing
    private TransactionManager mTransactionManager;
    private CallStreamingController mStreamingController;


    // Each TransactionalServiceWrapper should have their own Binder.DeathRecipient to clean up
    // any calls in the event the application crashes or is force stopped.
    private final IBinder.DeathRecipient mAppDeathListener = new IBinder.DeathRecipient() {
        @Override
        public void binderDied() {
            Log.i(TAG, "binderDied: for package=[%s]; cleaning calls", mPackageName);
            cleanupTransactionalServiceWrapper();
            mICallEventCallback.asBinder().unlinkToDeath(this, 0);
        }
    };

    public TransactionalServiceWrapper(ICallEventCallback callEventCallback,
            CallsManager callsManager, PhoneAccountHandle phoneAccountHandle, Call call,
            TransactionalServiceRepository repo) {
        // passed args
        mICallEventCallback = callEventCallback;
        mCallsManager = callsManager;
        mPhoneAccountHandle = phoneAccountHandle;
        mTrackedCalls.put(call.getId(), call); // service is now tracking its first call
        mRepository = repo;
        // init instance vars
        mPackageName = phoneAccountHandle.getComponentName().getPackageName();
        mTransactionManager = TransactionManager.getInstance();
        mStreamingController = mCallsManager.getCallStreamingController();
        mLock = mCallsManager.getLock();
        setDeathRecipient(callEventCallback);
    }

    @VisibleForTesting
    public void setTransactionManager(TransactionManager transactionManager) {
        mTransactionManager = transactionManager;
    }

    public TransactionManager getTransactionManager() {
        return mTransactionManager;
    }

    @VisibleForTesting
    public PhoneAccountHandle getPhoneAccountHandle() {
        return mPhoneAccountHandle;
    }

    public void trackCall(Call call) {
        synchronized (mLock) {
            if (call != null) {
                mTrackedCalls.put(call.getId(), call);
            }
        }
    }

    @VisibleForTesting
    public boolean untrackCall(Call call) {
        Call removedCall = null;
        synchronized (mLock) {
            if (call != null) {
                removedCall = mTrackedCalls.remove(call.getId());
                if (mTrackedCalls.size() == 0) {
                    mRepository.removeServiceWrapper(mPhoneAccountHandle);
                }
            }
        }
        Log.i(TAG, "removedCall call=" + removedCall);
        return removedCall != null;
    }

    @VisibleForTesting
    public int getNumberOfTrackedCalls() {
        int callCount = 0;
        synchronized (mLock) {
            callCount = mTrackedCalls.size();
        }
        return callCount;
    }

    private void cleanupTransactionalServiceWrapper() {
        for (Call call : mTrackedCalls.values()) {
            mCallsManager.markCallAsDisconnected(call,
                    new DisconnectCause(DisconnectCause.ERROR, "process died"));
            mCallsManager.removeCall(call); // This will clear mTrackedCalls && ClientTWS
        }
    }

    /***
     *********************************************************************************************
     **                        ICallControl: Client --> Server                                **
     **********************************************************************************************
     */
    private final ICallControl mICallControl = new ICallControl.Stub() {
        @Override
        public void setActive(String callId, android.os.ResultReceiver callback)
                throws RemoteException {
            try {
                Log.startSession("TSW.sA");
                createTransactions(callId, callback, SET_ACTIVE);
            } finally {
                Log.endSession();
            }
        }

        @Override
        public void answer(int videoState, String callId, android.os.ResultReceiver callback)
                throws RemoteException {
            try {
                Log.startSession("TSW.a");
                createTransactions(callId, callback, ANSWER, videoState);
            } finally {
                Log.endSession();
            }
        }

        @Override
        public void setInactive(String callId, android.os.ResultReceiver callback)
                throws RemoteException {
            try {
                Log.startSession("TSW.sI");
                createTransactions(callId, callback, SET_INACTIVE);
            } finally {
                Log.endSession();
            }
        }

        @Override
        public void disconnect(String callId, DisconnectCause disconnectCause,
                android.os.ResultReceiver callback)
                throws RemoteException {
            try {
                Log.startSession("TSW.d");
                createTransactions(callId, callback, DISCONNECT, disconnectCause);
            } finally {
                Log.endSession();
            }
        }

        @Override
        public void setMuteState(boolean isMuted, android.os.ResultReceiver callback)
                throws RemoteException {
            try {
                Log.startSession("TSW.sMS");
                addTransactionsToManager(
                        new SetMuteStateTransaction(mCallsManager, isMuted), callback);
            } finally {
                Log.endSession();
            }
        }

        @Override
        public void startCallStreaming(String callId, android.os.ResultReceiver callback)
                throws RemoteException {
            try {
                Log.startSession("TSW.sCS");
                createTransactions(callId, callback, START_STREAMING);
            } finally {
                Log.endSession();
            }
        }

        @Override
        public void requestVideoState(int videoState, String callId, ResultReceiver callback)
                throws RemoteException {
            try {
                Log.startSession("TSW.rVS");
                createTransactions(callId, callback, REQUEST_VIDEO_STATE, videoState);
            } finally {
                Log.endSession();
            }
        }

        private void createTransactions(String callId, ResultReceiver callback, String action,
                Object... objects) {
            Log.d(TAG, "createTransactions: callId=" + callId);
            Call call = mTrackedCalls.get(callId);
            if (call != null) {
                switch (action) {
                    case SET_ACTIVE:
                        handleCallControlNewCallFocusTransactions(call, SET_ACTIVE,
                                false /* isAnswer */, 0/*VideoState (ignored)*/, callback);
                        break;
                    case ANSWER:
                        handleCallControlNewCallFocusTransactions(call, ANSWER,
                                true /* isAnswer */, (int) objects[0] /*VideoState*/, callback);
                        break;
                    case DISCONNECT:
                        addTransactionsToManager(new EndCallTransaction(mCallsManager,
                                (DisconnectCause) objects[0], call), callback);
                        break;
                    case SET_INACTIVE:
                        addTransactionsToManager(
                                new HoldCallTransaction(mCallsManager, call), callback);
                        break;
                    case START_STREAMING:
                        addTransactionsToManager(mStreamingController.getStartStreamingTransaction(mCallsManager,
                                TransactionalServiceWrapper.this, call, mLock), callback);
                        break;
                    case REQUEST_VIDEO_STATE:
                        addTransactionsToManager(
                                new RequestVideoStateTransaction(mCallsManager, call,
                                        (int) objects[0]), callback);
                        break;
                }
            } else {
                Bundle exceptionBundle = new Bundle();
                exceptionBundle.putParcelable(TRANSACTION_EXCEPTION_KEY,
                        new CallException(TextUtils.formatSimple(
                        "Telecom cannot process [%s] because the call with id=[%s] is no longer "
                                + "being tracked. This is most likely a result of the call "
                                + "already being disconnected and removed. Try re-adding the call"
                                + " via TelecomManager#addCall", action, callId),
                                CODE_CALL_IS_NOT_BEING_TRACKED));
                callback.send(CODE_CALL_IS_NOT_BEING_TRACKED, exceptionBundle);
            }
        }

        // The client is request their VoIP call state go ACTIVE/ANSWERED.
        // This request is originating from the VoIP application.
        private void handleCallControlNewCallFocusTransactions(Call call, String action,
                boolean isAnswer, int potentiallyNewVideoState, ResultReceiver callback) {
            mTransactionManager.addTransaction(createSetActiveTransactions(call),
                    new OutcomeReceiver<>() {
                        @Override
                        public void onResult(VoipCallTransactionResult result) {
                            Log.i(TAG, String.format(Locale.US,
                                    "%s: onResult: callId=[%s]", action, call.getId()));
                            if (isAnswer) {
                                call.setVideoState(potentiallyNewVideoState);
                            }
                            callback.send(TELECOM_TRANSACTION_SUCCESS, new Bundle());
                        }

                        @Override
                        public void onError(CallException exception) {
                            Bundle extras = new Bundle();
                            extras.putParcelable(TRANSACTION_EXCEPTION_KEY, exception);
                            callback.send(exception == null ? CallException.CODE_ERROR_UNKNOWN :
                                    exception.getCode(), extras);
                        }
                    });
        }

        @Override
        public void requestCallEndpointChange(CallEndpoint endpoint, ResultReceiver callback) {
            try {
                Log.startSession("TSW.rCEC");
                addTransactionsToManager(new EndpointChangeTransaction(endpoint, mCallsManager),
                        callback);
            } finally {
                Log.endSession();
            }
        }

        /**
         * Application would like to inform InCallServices of an event
         */
        @Override
        public void sendEvent(String callId, String event, Bundle extras) {
            try {
                Log.startSession("TSW.sE");
                Call call = mTrackedCalls.get(callId);
                if (call != null) {
                    call.onConnectionEvent(event, extras);
                } else {
                    Log.i(TAG,
                            "sendEvent: was called but there is no call with id=[%s] cannot be "
                                    + "found. Most likely the call has been disconnected");
                }
            } finally {
                Log.endSession();
            }
        }
    };

    private void addTransactionsToManager(VoipCallTransaction transaction,
            ResultReceiver callback) {
        Log.d(TAG, "addTransactionsToManager");

        mTransactionManager.addTransaction(transaction, new OutcomeReceiver<>() {
            @Override
            public void onResult(VoipCallTransactionResult result) {
                Log.d(TAG, "addTransactionsToManager: onResult:");
                callback.send(TELECOM_TRANSACTION_SUCCESS, new Bundle());
            }

            @Override
            public void onError(CallException exception) {
                Log.d(TAG, "addTransactionsToManager: onError");
                Bundle extras = new Bundle();
                extras.putParcelable(TRANSACTION_EXCEPTION_KEY, exception);
                callback.send(exception == null ? CallException.CODE_ERROR_UNKNOWN :
                        exception.getCode(), extras);
            }
        });
    }

    public ICallControl getICallControl() {
        return mICallControl;
    }

    /***
     *********************************************************************************************
     **                    ICallEventCallback: Server --> Client                                **
     **********************************************************************************************
     */

    public void onSetActive(Call call) {
        try {
            Log.startSession("TSW.oSA");
            Log.d(TAG, String.format(Locale.US, "onSetActive: callId=[%s]", call.getId()));
            handleCallEventCallbackNewFocus(call, ON_SET_ACTIVE, false /*isAnswerRequest*/,
                    0 /*VideoState*/);
        } finally {
            Log.endSession();
        }
    }

    public void onAnswer(Call call, int videoState) {
        try {
            Log.startSession("TSW.oA");
            Log.d(TAG, String.format(Locale.US, "onAnswer: callId=[%s]", call.getId()));
            handleCallEventCallbackNewFocus(call, ON_ANSWER, true /*isAnswerRequest*/,
                    videoState /*VideoState*/);
        } finally {
            Log.endSession();
        }
    }

    // handle a CallEventCallback to set a call ACTIVE/ANSWERED. Must get ack from client since the
    // request has come from another source (ex. Android Auto is requesting a call to go active)
    private void handleCallEventCallbackNewFocus(Call call, String action, boolean isAnswerRequest,
            int potentiallyNewVideoState) {
        // save CallsManager state before sending client state changes
        Call foregroundCallBeforeSwap = mCallsManager.getForegroundCall();
        boolean wasActive = foregroundCallBeforeSwap != null && foregroundCallBeforeSwap.isActive();

        SerialTransaction serialTransactions = createSetActiveTransactions(call);
        // 3. get ack from client (that the requested call can go active)
        if (isAnswerRequest) {
            serialTransactions.appendTransaction(
                    new CallEventCallbackAckTransaction(mICallEventCallback,
                            action, call.getId(), potentiallyNewVideoState, mLock));
        } else {
            serialTransactions.appendTransaction(
                    new CallEventCallbackAckTransaction(mICallEventCallback,
                            action, call.getId(), mLock));
        }

        // do CallsManager workload before asking client and
        //   reset CallsManager state if client does NOT ack
        mTransactionManager.addTransaction(serialTransactions,
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(VoipCallTransactionResult result) {
                        Log.i(TAG, String.format(Locale.US,
                                "%s: onResult: callId=[%s]", action, call.getId()));
                        if (isAnswerRequest) {
                            call.setVideoState(potentiallyNewVideoState);
                        }
                    }

                    @Override
                    public void onError(CallException exception) {
                        if (isAnswerRequest) {
                            // This also sends the signal to untrack from TSW and the client_TSW
                            removeCallFromCallsManager(call,
                                    new DisconnectCause(DisconnectCause.REJECTED,
                                            "client rejected to answer the call;"
                                                    + " force disconnecting"));
                        } else {
                            mCallsManager.markCallAsOnHold(call);
                        }
                        maybeResetForegroundCall(foregroundCallBeforeSwap, wasActive);
                    }
                });
    }


    public void onSetInactive(Call call) {
        try {
            Log.startSession("TSW.oSI");
            Log.i(TAG, String.format(Locale.US, "onSetInactive: callId=[%s]", call.getId()));
            mTransactionManager.addTransaction(
                    new CallEventCallbackAckTransaction(mICallEventCallback,
                            ON_SET_INACTIVE, call.getId(), mLock), new OutcomeReceiver<>() {
                        @Override
                        public void onResult(VoipCallTransactionResult result) {
                            mCallsManager.markCallAsOnHold(call);
                        }

                        @Override
                        public void onError(CallException exception) {
                            Log.w(TAG, "onSetInactive: onError: e.code=[%d], e.msg=[%s]",
                                    exception.getCode(), exception.getMessage());
                        }
                    });
        } finally {
            Log.endSession();
        }
    }

    public void onDisconnect(Call call, DisconnectCause cause) {
        try {
            Log.startSession("TSW.oD");
            Log.d(TAG, String.format(Locale.US, "onDisconnect: callId=[%s]", call.getId()));

            mTransactionManager.addTransaction(
                    new CallEventCallbackAckTransaction(mICallEventCallback, ON_DISCONNECT,
                            call.getId(), cause, mLock), new OutcomeReceiver<>() {
                        @Override
                        public void onResult(VoipCallTransactionResult result) {
                            removeCallFromCallsManager(call, cause);
                        }

                        @Override
                        public void onError(CallException exception) {
                            removeCallFromCallsManager(call, cause);
                        }
                    }
            );
        } finally {
            Log.endSession();
        }
    }

    public void onCallStreamingStarted(Call call) {
        try {
            Log.startSession("TSW.oCSS");
            Log.d(TAG, String.format(Locale.US, "onCallStreamingStarted: callId=[%s]",
                    call.getId()));

            mTransactionManager.addTransaction(
                    new CallEventCallbackAckTransaction(mICallEventCallback, ON_STREAMING_STARTED,
                            call.getId(), mLock), new OutcomeReceiver<>() {
                        @Override
                        public void onResult(VoipCallTransactionResult result) {
                        }

                        @Override
                        public void onError(CallException exception) {
                            Log.w(TAG, "onCallStreamingStarted: onError: "
                                            + "e.code=[%d], e.msg=[%s]",
                                    exception.getCode(), exception.getMessage());
                            stopCallStreaming(call);
                        }
                    }
            );
        } finally {
            Log.endSession();
        }
    }

    public void onCallStreamingFailed(Call call,
            @CallStreamingService.StreamingFailedReason int streamingFailedReason) {
        if (call != null) {
            try {
                mICallEventCallback.onCallStreamingFailed(call.getId(), streamingFailedReason);
            } catch (RemoteException e) {
            }
        }
    }

    public void onCallEndpointChanged(Call call, CallEndpoint endpoint) {
        if (call != null) {
            try {
                mICallEventCallback.onCallEndpointChanged(call.getId(), endpoint);
            } catch (RemoteException e) {
            }
        }
    }

    public void onAvailableCallEndpointsChanged(Call call, Set<CallEndpoint> endpoints) {
        if (call != null) {
            try {
                mICallEventCallback.onAvailableCallEndpointsChanged(call.getId(),
                        endpoints.stream().toList());
            } catch (RemoteException e) {
            }
        }
    }

    public void onMuteStateChanged(Call call, boolean isMuted) {
        if (call != null) {
            try {
                mICallEventCallback.onMuteStateChanged(call.getId(), isMuted);
            } catch (RemoteException e) {
            }
        }
    }

    public void onVideoStateChanged(Call call, int videoState) {
        if (call != null) {
            try {
                mICallEventCallback.onVideoStateChanged(call.getId(), videoState);
            } catch (RemoteException e) {
            }
        }
    }

    public void removeCallFromWrappers(Call call) {
        if (call != null) {
            try {
                // remove the call from frameworks wrapper (client side)
                mICallEventCallback.removeCallFromTransactionalServiceWrapper(call.getId());
            } catch (RemoteException e) {
            }
            // remove the call from this class/wrapper (server side)
            untrackCall(call);
        }
    }

    public void onEvent(Call call, String event, Bundle extras) {
        if (call != null) {
            try {
                mICallEventCallback.onEvent(call.getId(), event, extras);
            } catch (RemoteException e) {
            }
        }
    }

    /***
     *********************************************************************************************
     **                                Helpers                                                  **
     **********************************************************************************************
     */
    private void maybeResetForegroundCall(Call foregroundCallBeforeSwap, boolean wasActive) {
        if (foregroundCallBeforeSwap == null) {
            return;
        }
        if (wasActive && !foregroundCallBeforeSwap.isActive()) {
            mCallsManager.markCallAsActive(foregroundCallBeforeSwap);
        }
    }

    private void removeCallFromCallsManager(Call call, DisconnectCause cause) {
        if (cause.getCode() != DisconnectCause.REJECTED) {
            mCallsManager.markCallAsDisconnected(call, cause);
        }
        mCallsManager.removeCall(call);
    }

    private SerialTransaction createSetActiveTransactions(Call call) {
        // create list for multiple transactions
        List<VoipCallTransaction> transactions = new ArrayList<>();

        // potentially hold the current active call in order to set a new call (active/answered)
        transactions.add(new MaybeHoldCallForNewCallTransaction(mCallsManager, call));
        // And request a new focus call update
        transactions.add(new RequestNewActiveCallTransaction(mCallsManager, call));

        return new SerialTransaction(transactions, mLock);
    }

    private void setDeathRecipient(ICallEventCallback callEventCallback) {
        try {
            callEventCallback.asBinder().linkToDeath(mAppDeathListener, 0);
        } catch (Exception e) {
            Log.w(TAG, "setDeathRecipient: hit exception=[%s] trying to link binder to death",
                    e.toString());
        }
    }

    /***
     *********************************************************************************************
     **                    FocusManager                                                       **
     **********************************************************************************************
     */

    @Override
    public void connectionServiceFocusLost() {
        if (mConnSvrFocusListener != null) {
            mConnSvrFocusListener.onConnectionServiceReleased(this);
        }
        Log.i(TAG, String.format(Locale.US, "connectionServiceFocusLost for package=[%s]",
                mPackageName));
    }

    @Override
    public void connectionServiceFocusGained() {
        Log.i(TAG, String.format(Locale.US, "connectionServiceFocusGained for package=[%s]",
                mPackageName));
    }

    @Override
    public void setConnectionServiceFocusListener(
            ConnectionServiceFocusManager.ConnectionServiceFocusListener listener) {
        mConnSvrFocusListener = listener;
    }

    @Override
    public ComponentName getComponentName() {
        return mPhoneAccountHandle.getComponentName();
    }

    /***
     *********************************************************************************************
     **                    CallStreaming                                                        **
     *********************************************************************************************
     */

    public void stopCallStreaming(Call call) {
        Log.i(this, "stopCallStreaming; callid=%s", call.getId());
        if (call != null && call.isStreaming()) {
            VoipCallTransaction stopStreamingTransaction = mStreamingController
                    .getStopStreamingTransaction(call, mLock);
            addTransactionsToManager(stopStreamingTransaction, new ResultReceiver(null));
        }
    }
}
