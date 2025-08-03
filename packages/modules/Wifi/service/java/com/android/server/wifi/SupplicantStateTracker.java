/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.server.wifi;

import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_PRIMARY;

import android.annotation.NonNull;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.BatteryStatsManager;
import android.os.Looper;
import android.os.Message;
import android.os.Parcelable;
import android.os.UserHandle;
import android.util.Log;

import com.android.internal.util.State;
import com.android.internal.util.StateMachine;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * Tracks the state changes in supplicant and provides functionality
 * that is based on these state changes:
 * - detect a failed WPA handshake that loops indefinitely
 * - authentication failure handling
 * TODO(b/159944009): Need to rework this class to handle make before break transition on STA +
 * STA devices (Apps will not see SUPPLICANT_STATE_CHANGED_ACTION when switching wifi networks)
 */
public class SupplicantStateTracker extends StateMachine {

    private static final String TAG = "SupplicantStateTracker";

    private final Context mContext;
    private final WifiConfigManager mWifiConfigManager;
    private final WifiMonitor mWifiMonitor;
    private final BatteryStatsManager mBatteryStatsManager;
    private final String mInterfaceName;
    private final ClientModeManager mClientModeManager;
    private final ClientModeManagerBroadcastQueue mBroadcastQueue;

    private boolean mVerboseLoggingEnabled = false;
    /* Indicates authentication failure in supplicant broadcast.
     * TODO: enhance auth failure reporting to include notification
     * for all type of failures: EAP, WPS & WPA networks */
    private boolean mAuthFailureInSupplicantBroadcast = false;

    /* Authentication failure reason
     * see {@link android.net.wifi.WifiManager#ERROR_AUTH_FAILURE_NONE},
     *     {@link android.net.wifi.WifiManager#ERROR_AUTH_FAILURE_TIMEOUT},
     *     {@link android.net.wifi.WifiManager#ERROR_AUTH_FAILURE_WRONG_PSWD},
     *     {@link android.net.wifi.WifiManager#ERROR_AUTH_FAILURE_EAP_FAILURE}
     */
    private int mAuthFailureReason;

    private final State mUninitializedState = new UninitializedState();
    private final State mDefaultState = new DefaultState();
    private final State mInactiveState = new InactiveState();
    private final State mDisconnectState = new DisconnectedState();
    private final State mScanState = new ScanState();
    private final State mHandshakeState = new HandshakeState();
    private final State mCompletedState = new CompletedState();
    private final State mDormantState = new DormantState();

    /** enable/disable verbose logging. */
    public void enableVerboseLogging(boolean verbose) {
        mVerboseLoggingEnabled = verbose;
    }

    private String getTag() {
        return TAG + "[" + (mInterfaceName == null ? "unknown" : mInterfaceName) + "]";
    }

    public SupplicantStateTracker(
            @NonNull Context context,
            @NonNull WifiConfigManager wifiConfigManager,
            @NonNull BatteryStatsManager batteryStatsManager,
            @NonNull Looper looper,
            @NonNull WifiMonitor wifiMonitor,
            @NonNull String interfaceName,
            @NonNull ClientModeManager clientModeManager,
            @NonNull ClientModeManagerBroadcastQueue broadcastQueue) {
        super(TAG, looper);
        mContext = context;
        mWifiConfigManager = wifiConfigManager;
        mBatteryStatsManager = batteryStatsManager;
        mWifiMonitor = wifiMonitor;
        mInterfaceName = interfaceName;
        mClientModeManager = clientModeManager;
        mBroadcastQueue = broadcastQueue;

        registerForWifiMonitorEvents();

        addState(mDefaultState); {
            // disconnected states
            addState(mUninitializedState, mDefaultState);
            addState(mInactiveState, mDefaultState);
            addState(mDisconnectState, mDefaultState);
            // connected states
            addState(mScanState, mDefaultState);
            addState(mHandshakeState, mDefaultState);
            addState(mCompletedState, mDefaultState);
            addState(mDormantState, mDefaultState);
        }

        setInitialState(mUninitializedState);
        setLogRecSize(50);
        setLogOnlyTransitions(true);
        // start the state machine
        start();
    }

    private static final int[] WIFI_MONITOR_EVENTS = {
            WifiMonitor.ASSOCIATION_REJECTION_EVENT,
            WifiMonitor.AUTHENTICATION_FAILURE_EVENT,
            WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT,
    };

    private void registerForWifiMonitorEvents() {
        for (int event : WIFI_MONITOR_EVENTS) {
            mWifiMonitor.registerHandler(mInterfaceName, event, getHandler());
        }
    }

    private void deregisterForWifiMonitorEvents() {
        for (int event : WIFI_MONITOR_EVENTS) {
            mWifiMonitor.deregisterHandler(mInterfaceName, event, getHandler());
        }
    }

    /**
     * Called when the owner ClientModeImpl is stopped. No more actions shall be performed on this
     * instance after it is stopped.
     */
    public void stop() {
        deregisterForWifiMonitorEvents();

        quitNow();
    }

    private void handleNetworkConnectionFailure(int netId, int disableReason) {
        if (mVerboseLoggingEnabled) {
            Log.d(getTag(), "handleNetworkConnectionFailure netId=" + netId
                    + " reason " + disableReason);
        }

        /* update network status */
        mWifiConfigManager.updateNetworkSelectionStatus(netId, disableReason);
    }

    private void transitionOnSupplicantStateChange(StateChangeResult stateChangeResult) {
        SupplicantState supState = stateChangeResult.state;

        if (mVerboseLoggingEnabled) {
            Log.d(getTag(), "Supplicant state: " + supState.toString() + "\n");
        }

        switch (supState) {
            case DISCONNECTED:
                transitionTo(mDisconnectState);
                break;
            case INTERFACE_DISABLED:
                //we should have received a disconnection already, do nothing
                break;
            case SCANNING:
                transitionTo(mScanState);
                break;
            case AUTHENTICATING:
            case ASSOCIATING:
            case ASSOCIATED:
            case FOUR_WAY_HANDSHAKE:
            case GROUP_HANDSHAKE:
                transitionTo(mHandshakeState);
                break;
            case COMPLETED:
                transitionTo(mCompletedState);
                break;
            case DORMANT:
                transitionTo(mDormantState);
                break;
            case INACTIVE:
                transitionTo(mInactiveState);
                break;
            case UNINITIALIZED:
            case INVALID:
                transitionTo(mUninitializedState);
                break;
            default:
                Log.e(getTag(), "Unknown supplicant state " + supState);
                break;
        }
    }

    private static int supplicantStateToBatteryStatsSupplicantState(SupplicantState state) {
        switch (state) {
            case DISCONNECTED:
                return BatteryStatsManager.WIFI_SUPPL_STATE_DISCONNECTED;
            case INTERFACE_DISABLED:
                return BatteryStatsManager.WIFI_SUPPL_STATE_INTERFACE_DISABLED;
            case INACTIVE:
                return BatteryStatsManager.WIFI_SUPPL_STATE_INACTIVE;
            case SCANNING:
                return BatteryStatsManager.WIFI_SUPPL_STATE_SCANNING;
            case AUTHENTICATING:
                return BatteryStatsManager.WIFI_SUPPL_STATE_AUTHENTICATING;
            case ASSOCIATING:
                return BatteryStatsManager.WIFI_SUPPL_STATE_ASSOCIATING;
            case ASSOCIATED:
                return BatteryStatsManager.WIFI_SUPPL_STATE_ASSOCIATED;
            case FOUR_WAY_HANDSHAKE:
                return BatteryStatsManager.WIFI_SUPPL_STATE_FOUR_WAY_HANDSHAKE;
            case GROUP_HANDSHAKE:
                return BatteryStatsManager.WIFI_SUPPL_STATE_GROUP_HANDSHAKE;
            case COMPLETED:
                return BatteryStatsManager.WIFI_SUPPL_STATE_COMPLETED;
            case DORMANT:
                return BatteryStatsManager.WIFI_SUPPL_STATE_DORMANT;
            case UNINITIALIZED:
                return BatteryStatsManager.WIFI_SUPPL_STATE_UNINITIALIZED;
            case INVALID:
                return BatteryStatsManager.WIFI_SUPPL_STATE_INVALID;
            default:
                Log.w(TAG, "Unknown supplicant state " + state);
                return BatteryStatsManager.WIFI_SUPPL_STATE_INVALID;
        }
    }

    private void sendSupplicantStateChangedBroadcast(
            SupplicantState state, boolean failedAuth, int reasonCode) {
        int supplState = supplicantStateToBatteryStatsSupplicantState(state);
        if (mClientModeManager.getRole() == ROLE_CLIENT_PRIMARY) {
            mBatteryStatsManager.reportWifiSupplicantStateChanged(supplState, failedAuth);
        }
        String summary = "broadcast=SUPPLICANT_STATE_CHANGED_ACTION"
                + " state=" + state
                + " failedAuth=" + failedAuth
                + " reasonCode=" + reasonCode;
        if (mVerboseLoggingEnabled) Log.d(TAG, "Queuing " + summary);
        mBroadcastQueue.queueOrSendBroadcast(
                mClientModeManager,
                () -> {
                    if (mVerboseLoggingEnabled) Log.d(TAG, "Sending " + summary);
                    sendSupplicantStateChangedBroadcast(mContext, state, failedAuth, reasonCode);
                });
    }

    private static void sendSupplicantStateChangedBroadcast(
            Context context, SupplicantState state, boolean failedAuth, int reasonCode) {
        Intent intent = new Intent(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT
                | Intent.FLAG_RECEIVER_REPLACE_PENDING);
        intent.putExtra(WifiManager.EXTRA_NEW_STATE, (Parcelable) state);
        if (failedAuth) {
            intent.putExtra(
                    WifiManager.EXTRA_SUPPLICANT_ERROR,
                    WifiManager.ERROR_AUTHENTICATING);
            intent.putExtra(
                    WifiManager.EXTRA_SUPPLICANT_ERROR_REASON,
                    reasonCode);
        }
        context.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    /********************************************************
     * HSM states
     *******************************************************/

    class DefaultState extends State {
        @Override
        public void enter() {
            if (mVerboseLoggingEnabled) Log.d(getTag(), getName() + "\n");
        }
        @Override
        public boolean processMessage(Message message) {
            if (mVerboseLoggingEnabled) Log.d(getTag(), getName() + message.toString() + "\n");
            switch (message.what) {
                case WifiMonitor.AUTHENTICATION_FAILURE_EVENT:
                    mAuthFailureInSupplicantBroadcast = true;
                    mAuthFailureReason = message.arg1;
                    break;
                case WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT:
                    StateChangeResult stateChangeResult = (StateChangeResult) message.obj;
                    SupplicantState state = stateChangeResult.state;
                    sendSupplicantStateChangedBroadcast(state, mAuthFailureInSupplicantBroadcast,
                            mAuthFailureReason);
                    mAuthFailureInSupplicantBroadcast = false;
                    mAuthFailureReason = WifiManager.ERROR_AUTH_FAILURE_NONE;
                    transitionOnSupplicantStateChange(stateChangeResult);
                    break;
                case WifiMonitor.ASSOCIATION_REJECTION_EVENT:
                default:
                    Log.e(getTag(), "Ignoring " + message);
                    break;
            }
            return HANDLED;
        }
    }

    /*
     * This indicates that the supplicant state as seen
     * by the framework is not initialized yet. We are
     * in this state right after establishing a control
     * channel connection before any supplicant events
     * or after we have lost the control channel
     * connection to the supplicant
     */
    class UninitializedState extends State {
        @Override
        public void enter() {
            if (mVerboseLoggingEnabled) Log.d(getTag(), getName() + "\n");
        }
    }

    class InactiveState extends State {
        @Override
        public void enter() {
            if (mVerboseLoggingEnabled) Log.d(getTag(), getName() + "\n");
        }
    }

    class DisconnectedState extends State {
        @Override
        public void enter() {
            if (mVerboseLoggingEnabled) Log.d(getTag(), getName() + "\n");
        }
    }

    class ScanState extends State {
        @Override
        public void enter() {
            if (mVerboseLoggingEnabled) Log.d(getTag(), getName() + "\n");
        }
    }

    class HandshakeState extends State {
        /**
         * The max number of the WPA supplicant loop iterations before we
         * decide that the loop should be terminated:
         */
        private static final int MAX_SUPPLICANT_LOOP_ITERATIONS = 4;
        private int mLoopDetectIndex;
        private int mLoopDetectCount;

        @Override
        public void enter() {
            if (mVerboseLoggingEnabled) Log.d(getTag(), getName() + "\n");
            mLoopDetectIndex = 0;
            mLoopDetectCount = 0;
        }
        @Override
        public boolean processMessage(Message message) {
            if (mVerboseLoggingEnabled) Log.d(getTag(), getName() + message.toString() + "\n");
            switch (message.what) {
                case WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT:
                    StateChangeResult stateChangeResult = (StateChangeResult) message.obj;
                    SupplicantState state = stateChangeResult.state;
                    if (SupplicantState.isHandshakeState(state)) {
                        if (mLoopDetectIndex > state.ordinal()) {
                            mLoopDetectCount++;
                        }
                        if (mLoopDetectCount > MAX_SUPPLICANT_LOOP_ITERATIONS) {
                            Log.d(getTag(), "Supplicant loop detected, disabling network "
                                    + stateChangeResult.networkId);
                            handleNetworkConnectionFailure(stateChangeResult.networkId,
                                    WifiConfiguration.NetworkSelectionStatus
                                            .DISABLED_AUTHENTICATION_FAILURE);
                        }
                        mLoopDetectIndex = state.ordinal();
                        sendSupplicantStateChangedBroadcast(state,
                                mAuthFailureInSupplicantBroadcast, mAuthFailureReason);
                    } else {
                        //Have the DefaultState handle the transition
                        return NOT_HANDLED;
                    }
                    break;
                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }
    }

    class CompletedState extends State {
        @Override
        public void enter() {
            if (mVerboseLoggingEnabled) Log.d(getTag(), getName() + "\n");
        }
        @Override
        public boolean processMessage(Message message) {
            if (mVerboseLoggingEnabled) Log.d(getTag(), getName() + message.toString() + "\n");
            switch (message.what) {
                case WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT:
                    StateChangeResult stateChangeResult = (StateChangeResult) message.obj;
                    SupplicantState state = stateChangeResult.state;
                    sendSupplicantStateChangedBroadcast(state, mAuthFailureInSupplicantBroadcast,
                            mAuthFailureReason);
                    /* Ignore any connecting state in completed state. Group re-keying
                     * events and other auth events that do not affect connectivity are
                     * ignored
                     */
                    if (SupplicantState.isConnecting(state)) {
                        break;
                    }
                    transitionOnSupplicantStateChange(stateChangeResult);
                    break;
                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }
    }

    //TODO: remove after getting rid of the state in supplicant
    class DormantState extends State {
        @Override
        public void enter() {
            if (mVerboseLoggingEnabled) Log.d(getTag(), getName() + "\n");
        }
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        super.dump(fd, pw, args);
        pw.println("mAuthFailureInSupplicantBroadcast " + mAuthFailureInSupplicantBroadcast);
        pw.println("mAuthFailureReason " + mAuthFailureReason);
        pw.println();
    }
}
