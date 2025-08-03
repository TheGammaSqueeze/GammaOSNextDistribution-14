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

import static com.android.server.telecom.LogUtils.Events.STATE_TIMEOUT;

import android.provider.DeviceConfig;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.Log;
import android.util.LocalLog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.IndentingPrintWriter;
import com.android.server.telecom.stats.CallStateChangedAtomWriter;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Watchdog class responsible for detecting potential anomalous conditions for {@link Call}s.
 */
public class CallAnomalyWatchdog extends CallsManagerListenerBase implements Call.Listener {
    private final EmergencyCallDiagnosticLogger mEmergencyCallDiagnosticLogger;

    /**
     * Class used to track the call state as it pertains to the watchdog. The watchdog cares about
     * both the call state and whether a {@link ConnectionService} has finished creating the
     * connection.
     */
    public static class WatchdogCallState {
        public final int state;
        public final boolean isCreateConnectionComplete;
        public final long stateStartTimeMillis;

        public WatchdogCallState(int newState, boolean newIsCreateConnectionComplete,
                long newStateStartTimeMillis) {
            state = newState;
            isCreateConnectionComplete = newIsCreateConnectionComplete;
            stateStartTimeMillis = newStateStartTimeMillis;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof WatchdogCallState)) return false;
            WatchdogCallState that = (WatchdogCallState) o;
            // don't include the state timestamp in the equality check.
            return state == that.state
                    && isCreateConnectionComplete == that.isCreateConnectionComplete;
        }

        @Override
        public int hashCode() {
            return Objects.hash(state, isCreateConnectionComplete);
        }

        @Override
        public String toString() {
            return "[isCreateConnComplete=" + isCreateConnectionComplete + ", state="
                    + CallState.toString(state) + "]";
        }

        /**
         * Determines if the current call is in a transitory state.  A call is deemed to be in a
         * transitory state if either {@link CallState#isTransitoryState(int)} returns true, OR
         * if the call has been created but is not yet added to {@link CallsManager} (i.e. we are
         * still waiting for the {@link ConnectionService} to create the connection.
         * @return {@code true} if the call is in a transitory state, {@code false} otherwise.
         */
        public boolean isInTransitoryState() {
            return CallState.isTransitoryState(state)
                    // Consider it transitory if create connection hasn't completed, EXCEPT if we
                    // are in SELECT_PHONE_ACCOUNT state since that state will depend on user input.
                    || (!isCreateConnectionComplete && state != CallState.SELECT_PHONE_ACCOUNT);
        }

        /**
         * Determines if the current call is in an intermediate state.  A call is deemed to be in
         * an intermediate state if either {@link CallState#isIntermediateState(int)} returns true,
         * AND the call has been created to the connection.
         * @return {@code true} if the call is in a intermediate state, {@code false} otherwise.
         */
        public boolean isInIntermediateState() {
            return CallState.isIntermediateState(state) && isCreateConnectionComplete;
        }
    }

    // Handler for tracking pending timeouts.
    private final ScheduledExecutorService mScheduledExecutorService;
    private final TelecomSystem.SyncRoot mLock;
    private final Timeouts.Adapter mTimeoutAdapter;
    private final ClockProxy mClockProxy;
    private AnomalyReporterAdapter mAnomalyReporter = new AnomalyReporterAdapterImpl();
    // Pre-allocate space for 2 calls; realistically thats all we should ever need (tm)
    private final Map<Call, ScheduledFuture<?>> mScheduledFutureMap = new ConcurrentHashMap<>(2);
    private final Map<Call, WatchdogCallState> mWatchdogCallStateMap = new ConcurrentHashMap<>(2);
    // Track the calls which are pending destruction.
    // TODO: enhance to handle the case where a call never gets destroyed.
    private final Set<Call> mCallsPendingDestruction = Collections.newSetFromMap(
            new ConcurrentHashMap<>(2));
    private final LocalLog mLocalLog = new LocalLog(20);

    /**
     * Enables the action to disconnect the call when the Transitory state and Intermediate state
     * time expires.
     */
    private static final String ENABLE_DISCONNECT_CALL_ON_STUCK_STATE =
            "enable_disconnect_call_on_stuck_state";
    /**
     * Anomaly Report UUIDs and corresponding event descriptions specific to CallAnomalyWatchdog.
     */
    public static final UUID WATCHDOG_DISCONNECTED_STUCK_CALL_UUID =
            UUID.fromString("4b093985-c78f-45e3-a9fe-5319f397b025");
    public static final String WATCHDOG_DISCONNECTED_STUCK_CALL_MSG =
            "Telecom CallAnomalyWatchdog caught and disconnected a stuck/zombie call.";
    public static final UUID WATCHDOG_DISCONNECTED_STUCK_EMERGENCY_CALL_UUID =
            UUID.fromString("d57d8aab-d723-485e-a0dd-d1abb0f346c8");
    public static final String WATCHDOG_DISCONNECTED_STUCK_EMERGENCY_CALL_MSG =
            "Telecom CallAnomalyWatchdog caught and disconnected a stuck/zombie emergency call.";

    @VisibleForTesting
    public void setAnomalyReporterAdapter(AnomalyReporterAdapter mAnomalyReporterAdapter){
        mAnomalyReporter = mAnomalyReporterAdapter;
    }

    public CallAnomalyWatchdog(ScheduledExecutorService executorService,
            TelecomSystem.SyncRoot lock,
            Timeouts.Adapter timeoutAdapter, ClockProxy clockProxy,
            EmergencyCallDiagnosticLogger emergencyCallDiagnosticLogger) {
        mScheduledExecutorService = executorService;
        mLock = lock;
        mTimeoutAdapter = timeoutAdapter;
        mClockProxy = clockProxy;
        mEmergencyCallDiagnosticLogger = emergencyCallDiagnosticLogger;
    }

    /**
     * Start tracking a call that we're waiting for a ConnectionService to create.
     * @param call the call.
     */
    @Override
    public void onStartCreateConnection(Call call) {
        maybeTrackCall(call);
        call.addListener(this);
    }

    @Override
    public void onCallAdded(Call call) {
        maybeTrackCall(call);
    }

    /**
     * Override of {@link CallsManagerListenerBase} to track when calls have failed to be created by
     * a ConnectionService.  These calls should no longer be tracked by the CallAnomalyWatchdog.
     * @param call the call
     */
    @Override
    public void onCreateConnectionFailed(Call call) {
        Log.i(this, "onCreateConnectionFailed: call=%s", call.toString());
        stopTrackingCall(call);
    }

    /**
     * Override of {@link CallsManagerListenerBase} to track when calls are removed
     * @param call the call
     */
    @Override
    public void onCallRemoved(Call call) {
        Log.i(this, "onCallRemoved: call=%s", call.toString());
        stopTrackingCall(call);
    }

    /**
     * Override of {@link com.android.server.telecom.CallsManager.CallsManagerListener} to track
     * call state changes.
     * @param call the call
     * @param oldState its old state
     * @param newState the new state
     */
    @Override
    public void onCallStateChanged(Call call, int oldState, int newState) {
        Log.i(this, "onCallStateChanged: call=%s", call.toString());
        maybeTrackCall(call);
    }

    /**
     * Override of {@link Call.Listener} so we can capture successful creation of calls.
     * @param call the call
     * @param callState the state the call is now in
     */
    @Override
    public void onSuccessfulOutgoingCall(Call call, int callState) {
        maybeTrackCall(call);
    }

    /**
     * Override of {@link Call.Listener} so we can capture failed call creation.
     * @param call the call
     * @param disconnectCause the disconnect cause
     */
    @Override
    public void onFailedOutgoingCall(Call call, DisconnectCause disconnectCause) {
        Log.i(this, "onFailedOutgoingCall: call=%s", call.toString());
        stopTrackingCall(call);
    }

    /**
     * Override of {@link Call.Listener} so we can capture successful creation of calls
     * @param call the call
     */
    @Override
    public void onSuccessfulIncomingCall(Call call) {
        maybeTrackCall(call);
    }

    /**
     * Override of {@link Call.Listener} so we can capture failed call creation.
     * @param call the call
     */
    @Override
    public void onFailedIncomingCall(Call call) {
        Log.i(this, "onFailedIncomingCall: call=%s", call.toString());
        stopTrackingCall(call);
    }

    /**
     * Helper method used to stop CallAnomalyWatchdog from tracking or destroying the call.
     * @param call the call.
     */
    private void stopTrackingCall(Call call) {
        if (mScheduledFutureMap.containsKey(call)) {
            ScheduledFuture<?> existingTimeout = mScheduledFutureMap.get(call);
            existingTimeout.cancel(false /* cancelIfRunning */);
            mScheduledFutureMap.remove(call);
        }
        if (mCallsPendingDestruction.contains(call)) {
            mCallsPendingDestruction.remove(call);
        }
        if (mWatchdogCallStateMap.containsKey(call)) {
            mWatchdogCallStateMap.remove(call);
        }
        call.removeListener(this);
    }

    /**
     * Given a {@link Call}, potentially post a cleanup task to track when the call has been in a
     * transitory state too long.
     * @param call the call.
     */
    private void maybeTrackCall(Call call) {
        final WatchdogCallState currentState = mWatchdogCallStateMap.get(call);
        final WatchdogCallState newState = new WatchdogCallState(call.getState(),
                call.isCreateConnectionComplete(), mClockProxy.elapsedRealtime());
        if (Objects.equals(currentState, newState)) {
            // No state change; skip.
            return;
        }
        mWatchdogCallStateMap.put(call, newState);

        // The call's state has changed, so we will remove any existing state cleanup tasks.
        if (mScheduledFutureMap.containsKey(call)) {
            ScheduledFuture<?> existingTimeout = mScheduledFutureMap.get(call);
            existingTimeout.cancel(false /* cancelIfRunning */);
            mScheduledFutureMap.remove(call);
        }

        Log.i(this, "maybePostCleanupTask; callId=%s, state=%s, createConnComplete=%b",
                call.getId(), CallState.toString(call.getState()),
                call.isCreateConnectionComplete());

        long timeoutMillis = getTimeoutMillis(call, newState);
        boolean isEnabledDisconnect = isEnabledDisconnectForStuckCall();
        // If the call is now in a transitory or intermediate state, post a new cleanup task.
        if (timeoutMillis > 0) {
            Runnable cleanupRunnable = getCleanupRunnable(call, newState, timeoutMillis,
                    isEnabledDisconnect);

            // Post cleanup to the executor service and cache the future, so we can cancel it if
            // needed.
            ScheduledFuture<?> future = mScheduledExecutorService.schedule(cleanupRunnable,
                    timeoutMillis, TimeUnit.MILLISECONDS);
            mScheduledFutureMap.put(call, future);
        }
    }

    public long getTimeoutMillis(Call call, WatchdogCallState state) {
        boolean isVoip = call.getIsVoipAudioMode();
        boolean isEmergency = call.isEmergencyCall();

        if (state.isInTransitoryState()) {
            if (isVoip) {
                return (isEmergency) ?
                        mTimeoutAdapter.getVoipEmergencyCallTransitoryStateTimeoutMillis() :
                        mTimeoutAdapter.getVoipCallTransitoryStateTimeoutMillis();
            }

            return (isEmergency) ?
                    mTimeoutAdapter.getNonVoipEmergencyCallTransitoryStateTimeoutMillis() :
                    mTimeoutAdapter.getNonVoipCallTransitoryStateTimeoutMillis();
        }

        if (state.isInIntermediateState()) {
            if (isVoip) {
                return (isEmergency) ?
                        mTimeoutAdapter.getVoipEmergencyCallIntermediateStateTimeoutMillis() :
                        mTimeoutAdapter.getVoipCallIntermediateStateTimeoutMillis();
            }

            return (isEmergency) ?
                    mTimeoutAdapter.getNonVoipEmergencyCallIntermediateStateTimeoutMillis() :
                    mTimeoutAdapter.getNonVoipCallIntermediateStateTimeoutMillis();
        }

        return 0;
    }

    private Runnable getCleanupRunnable(Call call, WatchdogCallState newState, long timeoutMillis,
            boolean isEnabledDisconnect) {
        Runnable cleanupRunnable = new android.telecom.Logging.Runnable("CAW.mR", mLock) {
            @Override
            public void loggedRun() {
                // If we're already pending a cleanup due to a state violation for this call.
                if (mCallsPendingDestruction.contains(call)) {
                    return;
                }
                // Ensure that at timeout we are still in the original state when we posted the
                // timeout.
                final WatchdogCallState expiredState = new WatchdogCallState(call.getState(),
                        call.isCreateConnectionComplete(), mClockProxy.elapsedRealtime());
                if (expiredState.equals(newState)
                        && getDurationInCurrentStateMillis(newState) > timeoutMillis) {
                    // The call has been in this transitory or intermediate state too long,
                    // so disconnect it and destroy it.
                    Log.addEvent(call, STATE_TIMEOUT, newState);
                    mLocalLog.log("STATE_TIMEOUT; callId=" + call.getId() + " in state "
                            + newState);
                    if (call.isEmergencyCall()){
                        mAnomalyReporter.reportAnomaly(
                                WATCHDOG_DISCONNECTED_STUCK_EMERGENCY_CALL_UUID,
                                WATCHDOG_DISCONNECTED_STUCK_EMERGENCY_CALL_MSG);
                        mEmergencyCallDiagnosticLogger.reportStuckCall(call);
                    } else {
                        mAnomalyReporter.reportAnomaly(
                                WATCHDOG_DISCONNECTED_STUCK_CALL_UUID,
                                WATCHDOG_DISCONNECTED_STUCK_CALL_MSG);
                    }

                    if (isEnabledDisconnect) {
                        call.setOverrideDisconnectCauseCode(
                                new DisconnectCause(DisconnectCause.ERROR, "state_timeout"));
                        call.disconnect("State timeout");
                    } else {
                        writeCallStateChangedAtom(call);
                    }

                    mCallsPendingDestruction.add(call);
                    if (mWatchdogCallStateMap.containsKey(call)) {
                        mWatchdogCallStateMap.remove(call);
                    }
                }
                mScheduledFutureMap.remove(call);
            }
        }.prepare();
        return cleanupRunnable;
    }

    /**
     * Returns whether the action to disconnect the call when the Transitory state and
     * Intermediate state time expires is enabled or disabled.
     * @return {@code true} if the action is enabled, {@code false} if the action is disabled.
     */
    private boolean isEnabledDisconnectForStuckCall() {
        return DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_TELEPHONY,
                ENABLE_DISCONNECT_CALL_ON_STUCK_STATE, false);
    }

    /**
     * Determines how long a call has been in a specific state.
     * @param state the call state.
     * @return the time in the state, in millis.
     */
    private long getDurationInCurrentStateMillis(WatchdogCallState state) {
        return mClockProxy.elapsedRealtime() - state.stateStartTimeMillis;
    }

    private void writeCallStateChangedAtom(Call call) {
        new CallStateChangedAtomWriter()
                .setDisconnectCause(call.getDisconnectCause())
                .setSelfManaged(call.isSelfManaged())
                .setExternalCall(call.isExternalCall())
                .setEmergencyCall(call.isEmergencyCall())
                .write(call.getState());
    }

    /**
     * Dumps the state of the {@link CallAnomalyWatchdog}.
     *
     * @param pw The {@code IndentingPrintWriter} to write the state to.
     */
    public void dump(IndentingPrintWriter pw) {
        pw.println("Anomaly log:");
        pw.increaseIndent();
        mLocalLog.dump(pw);
        pw.decreaseIndent();
        pw.print("Pending timeouts: ");
        pw.println(mScheduledFutureMap.keySet().stream().map(c -> c.getId()).collect(
                Collectors.joining(",")));
        pw.print("Pending destruction: ");
        pw.println(mCallsPendingDestruction.stream().map(c -> c.getId()).collect(
                Collectors.joining(",")));
    }

    @VisibleForTesting
    public int getNumberOfScheduledTimeouts() {
        return mScheduledFutureMap.size();
    }
}
