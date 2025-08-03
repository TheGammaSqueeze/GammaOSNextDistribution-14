/*
 * Copyright 2017 The Android Open Source Project
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

import android.annotation.Nullable;
import android.content.ComponentName;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.telecom.Log;
import android.telecom.Logging.Session;
import android.text.TextUtils;
import android.util.LocalLog;
import android.util.LogPrinter;
import android.util.Printer;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.telecom.flags.Flags;
import com.android.internal.util.IndentingPrintWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ConnectionServiceFocusManager {
    private static final String TAG = "ConnectionSvrFocusMgr";
    private static final int GET_CURRENT_FOCUS_TIMEOUT_MILLIS = 1000;
    private final LocalLog mLocalLog = new LocalLog(20);
    private final AnomalyReporterAdapter mAnomalyReporter = new AnomalyReporterAdapterImpl();
    public static final UUID WATCHDOG_GET_CALL_FOCUS_TIMEOUT_UUID =
            UUID.fromString("edd7334a-ef87-432b-a1d0-a2f23959c73e");
    public static final String WATCHDOG_GET_CALL_FOCUS_TIMEOUT_MSG =
            "Telecom CallAnomalyWatchdog detected a timeout while getting the call focus.";

    /** Factory interface used to create the {@link ConnectionServiceFocusManager} instance. */
    public interface ConnectionServiceFocusManagerFactory {
        ConnectionServiceFocusManager create(CallsManagerRequester requester);
    }

    /**
     * Interface used by ConnectionServiceFocusManager to communicate with
     * {@link ConnectionServiceWrapper}.
     */
    public interface ConnectionServiceFocus {
        /**
         * Notifies the {@link android.telecom.ConnectionService} that it has lose the connection
         * service focus. It should release all call resource i.e camera, audio once it lost the
         * focus.
         */
        void connectionServiceFocusLost();

        /**
         * Notifies the {@link android.telecom.ConnectionService} that it has gain the connection
         * service focus. It can request the call resource i.e camera, audio as they expected to be
         * free at the moment.
         */
        void connectionServiceFocusGained();

        /**
         * Sets the ConnectionServiceFocusListener.
         *
         * @see {@link ConnectionServiceFocusListener}.
         */
        void setConnectionServiceFocusListener(ConnectionServiceFocusListener listener);

        /**
         * Get the {@link ComponentName} of the ConnectionService for logging purposes.
         * @return the {@link ComponentName}.
         */
        ComponentName getComponentName();
    }

    /**
     * Interface used to receive the changed of {@link android.telecom.ConnectionService} that
     * ConnectionServiceFocusManager cares about.
     */
    public interface ConnectionServiceFocusListener {
        /**
         * Calls when {@link android.telecom.ConnectionService} has released the call resource. This
         * usually happen after the {@link android.telecom.ConnectionService} lost the focus.
         *
         * @param connectionServiceFocus the {@link android.telecom.ConnectionService} that released
         * the call resources.
         */
        void onConnectionServiceReleased(ConnectionServiceFocus connectionServiceFocus);

        /**
         * Calls when {@link android.telecom.ConnectionService} is disconnected.
         *
         * @param connectionServiceFocus the {@link android.telecom.ConnectionService} which is
         * disconnected.
         */
        void onConnectionServiceDeath(ConnectionServiceFocus connectionServiceFocus);
    }

    /**
     * Interface define to expose few information of {@link Call} that ConnectionServiceFocusManager
     * cares about.
     */
    public interface CallFocus {
        /**
         * Returns the ConnectionService associated with the call.
         */
        ConnectionServiceFocus getConnectionServiceWrapper();

        /**
         * Returns the state of the call.
         *
         * @see {@link CallState}
         */
        int getState();

        /**
         * @return {@code True} if this call can receive focus, {@code false} otherwise.
         */
        boolean isFocusable();

        /**
         * @return the ID of the focusable for debug purposes.
         */
        String getId();
    }

    /** Interface define a call back for focus request event. */
    public interface RequestFocusCallback {
        /**
         * Invokes after the focus request is done.
         *
         * @param call the call associated with the focus request.
         */
        void onRequestFocusDone(CallFocus call);
    }

    /**
     * Interface define to allow the ConnectionServiceFocusManager to communicate with
     * {@link CallsManager}.
     */
    public interface CallsManagerRequester {
        /**
         * Requests {@link CallsManager} to disconnect a {@link ConnectionServiceFocus}. This
         * usually happen when the connection service doesn't respond to focus lost event.
         */
        void releaseConnectionService(ConnectionServiceFocus connectionService);

        /**
         * Sets the {@link com.android.server.telecom.CallsManager.CallsManagerListener} to listen
         * the call event that ConnectionServiceFocusManager cares about.
         */
        void setCallsManagerListener(CallsManager.CallsManagerListener listener);
    }

    public static final Set<Integer> PRIORITY_FOCUS_CALL_STATE
            = Set.of(CallState.ACTIVE, CallState.CONNECTING, CallState.DIALING,
            CallState.AUDIO_PROCESSING, CallState.RINGING);

    private static final int MSG_REQUEST_FOCUS = 1;
    private static final int MSG_RELEASE_CONNECTION_FOCUS = 2;
    private static final int MSG_RELEASE_FOCUS_TIMEOUT = 3;
    private static final int MSG_CONNECTION_SERVICE_DEATH = 4;
    private static final int MSG_ADD_CALL = 5;
    private static final int MSG_REMOVE_CALL = 6;
    private static final int MSG_CALL_STATE_CHANGED = 7;

    @VisibleForTesting
    public static final int RELEASE_FOCUS_TIMEOUT_MS = 5000;

    private final List<CallFocus> mCalls;

    private final CallsManagerListenerBase mCallsManagerListener =
            new CallsManagerListenerBase() {
                @Override
                public void onCallAdded(Call call) {
                    if (callShouldBeIgnored(call)) {
                        return;
                    }

                    mEventHandler
                            .obtainMessage(MSG_ADD_CALL,
                                    new MessageArgs(
                                            Log.createSubsession(),
                                            "CSFM.oCA",
                                            call))
                            .sendToTarget();
                }

                @Override
                public void onCallRemoved(Call call) {
                    if (callShouldBeIgnored(call)) {
                        return;
                    }

                    mEventHandler
                            .obtainMessage(MSG_REMOVE_CALL,
                                    new MessageArgs(
                                            Log.createSubsession(),
                                            "CSFM.oCR",
                                            call))
                            .sendToTarget();
                }

                @Override
                public void onCallStateChanged(Call call, int oldState, int newState) {
                    if (callShouldBeIgnored(call)) {
                        return;
                    }

                    mEventHandler
                            .obtainMessage(MSG_CALL_STATE_CHANGED, oldState, newState,
                                    new MessageArgs(
                                            Log.createSubsession(),
                                            "CSFM.oCSS",
                                            call))
                            .sendToTarget();
                }

                @Override
                public void onExternalCallChanged(Call call, boolean isExternalCall) {
                    if (isExternalCall) {
                        mEventHandler
                                .obtainMessage(MSG_REMOVE_CALL,
                                        new MessageArgs(
                                                Log.createSubsession(),
                                                "CSFM.oECC",
                                                call))
                                .sendToTarget();
                    } else {
                        mEventHandler
                                .obtainMessage(MSG_ADD_CALL,
                                        new MessageArgs(
                                                Log.createSubsession(),
                                                "CSFM.oECC",
                                                call))
                                .sendToTarget();
                    }
                }

                boolean callShouldBeIgnored(Call call) {
                    return call.isExternalCall();
                }
            };

    private final ConnectionServiceFocusListener mConnectionServiceFocusListener =
            new ConnectionServiceFocusListener() {
                @Override
                public void onConnectionServiceReleased(
                        ConnectionServiceFocus connectionServiceFocus) {
                    mEventHandler
                            .obtainMessage(MSG_RELEASE_CONNECTION_FOCUS,
                                    new MessageArgs(
                                            Log.createSubsession(),
                                            "CSFM.oCSR",
                                            connectionServiceFocus))
                            .sendToTarget();
                }

                @Override
                public void onConnectionServiceDeath(
                        ConnectionServiceFocus connectionServiceFocus) {
                    mEventHandler
                            .obtainMessage(MSG_CONNECTION_SERVICE_DEATH,
                                    new MessageArgs(
                                            Log.createSubsession(),
                                            "CSFM.oCSD",
                                            connectionServiceFocus))
                            .sendToTarget();
                }
            };

    private ConnectionServiceFocus mCurrentFocus;
    private CallFocus mCurrentFocusCall;
    private CallsManagerRequester mCallsManagerRequester;
    private FocusRequest mCurrentFocusRequest;
    private FocusManagerHandler mEventHandler;

    public ConnectionServiceFocusManager(
            CallsManagerRequester callsManagerRequester) {
        mCallsManagerRequester = callsManagerRequester;
        mCallsManagerRequester.setCallsManagerListener(mCallsManagerListener);
        HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        mEventHandler = new FocusManagerHandler(handlerThread.getLooper());
        mCalls = new ArrayList<>();
    }

    /**
     * Requests the call focus for the given call. The {@code callback} will be invoked once
     * the request is done.
     * @param focus the call need to be focus.
     * @param callback the callback associated with this request.
     */
    public void requestFocus(CallFocus focus, RequestFocusCallback callback) {
        mEventHandler.obtainMessage(MSG_REQUEST_FOCUS,
                new MessageArgs(
                        Log.createSubsession(),
                        "CSFM.rF",
                        new FocusRequest(focus, callback)))
                .sendToTarget();
    }

    /**
     * Returns the current focus call. The {@link android.telecom.ConnectionService} of the focus
     * call is the current connection service focus. Also the state of the focus call must be one
     * of {@link #PRIORITY_FOCUS_CALL_STATE}.
     */
    public @Nullable CallFocus getCurrentFocusCall() {
        if (mEventHandler.getLooper().isCurrentThread()) {
            // return synchronously if we're on the same thread.
            return mCurrentFocusCall;
        }
        final BlockingQueue<Optional<CallFocus>> currentFocusedCallQueue =
                new LinkedBlockingQueue<>(1);
        mEventHandler.post(() -> {
            currentFocusedCallQueue.offer(
                    mCurrentFocusCall == null ? Optional.empty() : Optional.of(mCurrentFocusCall));
        });
        try {
            Optional<CallFocus> syncCallFocus = currentFocusedCallQueue.poll(
                    GET_CURRENT_FOCUS_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            if (syncCallFocus != null) {
                return syncCallFocus.orElse(null);
            } else {
                if (Flags.genAnomReportOnFocusTimeout()) {
                    Log.w(TAG, "Timed out waiting for synchronous current focus. Returning possibly"
                                    + " inaccurate result. returning currentFocusCall=[%s]",
                            mCurrentFocusCall);

                    // dump the state of the handler to better understand the timeout
                    mEventHandler.dump(
                            new LogPrinter(android.util.Log.INFO, TAG), "CsFocusMgr_timeout");

                    // report the timeout
                    mAnomalyReporter.reportAnomaly(
                            WATCHDOG_GET_CALL_FOCUS_TIMEOUT_UUID,
                            WATCHDOG_GET_CALL_FOCUS_TIMEOUT_MSG);
                } else {
                    Log.w(TAG, "Timed out waiting for synchronous current focus. Returning possibly"
                            + " inaccurate result");
                }
                return mCurrentFocusCall;
            }
        } catch (InterruptedException e) {
            Log.w(TAG, "Interrupted when waiting for synchronous current focus."
                    + " Returning possibly inaccurate result.");
            return mCurrentFocusCall;
        }
    }

    /** Returns the current connection service focus. */
    public ConnectionServiceFocus getCurrentFocusConnectionService() {
        return mCurrentFocus;
    }

    @VisibleForTesting
    public Handler getHandler() {
        return mEventHandler;
    }

    @VisibleForTesting
    public List<CallFocus> getAllCall() { return mCalls; }

    private void updateConnectionServiceFocus(ConnectionServiceFocus connSvrFocus) {
        Log.i(this, "updateConnectionServiceFocus connSvr = %s", connSvrFocus);
        if (!Objects.equals(mCurrentFocus, connSvrFocus)) {
            if (connSvrFocus != null) {
                connSvrFocus.setConnectionServiceFocusListener(mConnectionServiceFocusListener);
                connSvrFocus.connectionServiceFocusGained();
            }
            mCurrentFocus = connSvrFocus;
            Log.i(this, "updateConnectionServiceFocus connSvr = %s", connSvrFocus);
        }
    }

    private void updateCurrentFocusCall() {
        CallFocus previousFocus = mCurrentFocusCall;
        mCurrentFocusCall = null;

        if (mCurrentFocus == null) {
            Log.i(this, "updateCurrentFocusCall: mCurrentFocus is null");
            return;
        }

        List<CallFocus> calls = mCalls
                .stream()
                .filter(call -> mCurrentFocus.equals(call.getConnectionServiceWrapper())
                        && call.isFocusable())
                .collect(Collectors.toList());

        for (CallFocus call : calls) {
            if (PRIORITY_FOCUS_CALL_STATE.contains(call.getState())) {
                mCurrentFocusCall = call;
                if (previousFocus != call) {
                    mLocalLog.log(call.getId());
                }
                Log.i(this, "updateCurrentFocusCall %s", mCurrentFocusCall);
                return;
            }
        }
        if (previousFocus != null) {
            mLocalLog.log("<none>");
        }
        Log.i(this, "updateCurrentFocusCall = null");
    }

    private void onRequestFocusDone(FocusRequest focusRequest) {
        if (focusRequest.callback != null) {
            focusRequest.callback.onRequestFocusDone(focusRequest.call);
        }
    }

    private void handleRequestFocus(FocusRequest focusRequest) {
        Log.i(this, "handleRequestFocus req = %s", focusRequest);
        if (mCurrentFocus == null
                || mCurrentFocus.equals(focusRequest.call.getConnectionServiceWrapper())) {
            updateConnectionServiceFocus(focusRequest.call.getConnectionServiceWrapper());
            updateCurrentFocusCall();
            onRequestFocusDone(focusRequest);
        } else {
            mCurrentFocus.connectionServiceFocusLost();
            mCurrentFocusRequest = focusRequest;
            Message msg = mEventHandler.obtainMessage(
                    MSG_RELEASE_FOCUS_TIMEOUT,
                    new MessageArgs(
                            Log.createSubsession(),
                            "CSFM.hRF",
                            focusRequest));
            mEventHandler.sendMessageDelayed(msg, RELEASE_FOCUS_TIMEOUT_MS);
        }
    }

    private void handleReleasedFocus(ConnectionServiceFocus connectionServiceFocus) {
        Log.d(this, "handleReleasedFocus connSvr = %s", connectionServiceFocus);
        // The ConnectionService can call onConnectionServiceFocusReleased even if it's not the
        // current focus connection service, nothing will be changed in this case.
        if (Objects.equals(mCurrentFocus, connectionServiceFocus)) {
            mEventHandler.removeMessages(MSG_RELEASE_FOCUS_TIMEOUT);
            ConnectionServiceFocus newCSF = null;
            if (mCurrentFocusRequest != null) {
                newCSF = mCurrentFocusRequest.call.getConnectionServiceWrapper();
            }
            updateConnectionServiceFocus(newCSF);
            updateCurrentFocusCall();
            if (mCurrentFocusRequest != null) {
                onRequestFocusDone(mCurrentFocusRequest);
                mCurrentFocusRequest = null;
            }
        }
    }

    private void handleReleasedFocusTimeout(FocusRequest focusRequest) {
        Log.d(this, "handleReleasedFocusTimeout req = %s", focusRequest);
        mCallsManagerRequester.releaseConnectionService(mCurrentFocus);
        updateConnectionServiceFocus(focusRequest.call.getConnectionServiceWrapper());
        updateCurrentFocusCall();
        onRequestFocusDone(focusRequest);
        mCurrentFocusRequest = null;
    }

    private void handleConnectionServiceDeath(ConnectionServiceFocus connectionServiceFocus) {
        Log.d(this, "handleConnectionServiceDeath %s", connectionServiceFocus);
        if (Objects.equals(connectionServiceFocus, mCurrentFocus)) {
            updateConnectionServiceFocus(null);
            updateCurrentFocusCall();
        }
    }

    private void handleAddedCall(CallFocus call) {
        Log.d(this, "handleAddedCall %s", call);
        if (!mCalls.contains(call)) {
            mCalls.add(call);
        }
        if (Objects.equals(mCurrentFocus, call.getConnectionServiceWrapper())) {
            updateCurrentFocusCall();
        }
    }

    private void handleRemovedCall(CallFocus call) {
        Log.d(this, "handleRemovedCall %s", call);
        mCalls.remove(call);
        if (call.equals(mCurrentFocusCall)) {
            updateCurrentFocusCall();
        }
    }

    private void handleCallStateChanged(CallFocus call, int oldState, int newState) {
        Log.d(this,
                "handleCallStateChanged %s, oldState = %d, newState = %d",
                call,
                oldState,
                newState);
        if (mCalls.contains(call)
                && Objects.equals(mCurrentFocus, call.getConnectionServiceWrapper())) {
            updateCurrentFocusCall();
        }
    }

    public void dump(IndentingPrintWriter pw) {
        pw.println("Call Focus History:");
        mLocalLog.dump(pw);
    }

    private final class FocusManagerHandler extends Handler {
        FocusManagerHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            Session session = ((MessageArgs) msg.obj).logSession;
            String shortName = ((MessageArgs) msg.obj).shortName;
            if (TextUtils.isEmpty(shortName)) {
                shortName = "hM";
            }
            Log.continueSession(session, shortName);
            Object msgObj = ((MessageArgs) msg.obj).obj;

            try {
                switch (msg.what) {
                    case MSG_REQUEST_FOCUS:
                        handleRequestFocus((FocusRequest) msgObj);
                        break;
                    case MSG_RELEASE_CONNECTION_FOCUS:
                        handleReleasedFocus((ConnectionServiceFocus) msgObj);
                        break;
                    case MSG_RELEASE_FOCUS_TIMEOUT:
                        handleReleasedFocusTimeout((FocusRequest) msgObj);
                        break;
                    case MSG_CONNECTION_SERVICE_DEATH:
                        handleConnectionServiceDeath((ConnectionServiceFocus) msgObj);
                        break;
                    case MSG_ADD_CALL:
                        handleAddedCall((CallFocus) msgObj);
                        break;
                    case MSG_REMOVE_CALL:
                        handleRemovedCall((CallFocus) msgObj);
                        break;
                    case MSG_CALL_STATE_CHANGED:
                        handleCallStateChanged((CallFocus) msgObj, msg.arg1, msg.arg2);
                        break;
                }
            } finally {
                Log.endSession();
            }
        }
    }

    private static final class FocusRequest {
        CallFocus call;
        @Nullable RequestFocusCallback callback;

        FocusRequest(CallFocus call, RequestFocusCallback callback) {
            this.call = call;
            this.callback = callback;
        }
    }

    private static final class MessageArgs {
        Session logSession;
        String shortName;
        Object obj;

        MessageArgs(Session logSession, String shortName, Object obj) {
            this.logSession = logSession;
            this.shortName = shortName;
            this.obj = obj;
        }
    }
}
