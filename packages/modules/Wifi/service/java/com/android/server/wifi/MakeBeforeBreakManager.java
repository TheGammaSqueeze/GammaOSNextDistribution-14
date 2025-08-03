/*
 * Copyright (C) 2020 The Android Open Source Project
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
import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_SECONDARY_TRANSIENT;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.TestApi;
import android.content.Context;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.wifi.proto.nano.WifiMetricsProto;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages Make-Before-Break connection switching.
 */
public class MakeBeforeBreakManager {
    private static final String TAG = "WifiMbbManager";

    private final ActiveModeWarden mActiveModeWarden;
    private final FrameworkFacade mFrameworkFacade;
    private final Context mContext;
    private final ClientModeImplMonitor mCmiMonitor;
    private final ClientModeManagerBroadcastQueue mBroadcastQueue;
    private final WifiMetrics mWifiMetrics;

    private final List<Runnable> mOnAllSecondaryTransientCmmsStoppedListeners = new ArrayList<>();
    private boolean mVerboseLoggingEnabled = false;
    private @MbbInternalState int mInternalState = MBB_STATE_NONE;

    /** No MBB has been initiated. Package private. */
    @VisibleForTesting
    static final int MBB_STATE_NONE = 0;

    /** Client manager with ROLE_CLIENT_SECONDARY_TRANSIENT has been created for MBB and trying
     * to connect the new network. */
    @VisibleForTesting
    static final int MBB_STATE_SECONDARY_TRANSIENT_CREATED = 1;

    /** MBB has got the captive portal detected. */
    @VisibleForTesting
    static final int MBB_STATE_CAPTIVE_PORTAL_DETECTED = 2;

    /** MBB has got the internet validation on new network, start the role switch */
    @VisibleForTesting
    static final int MBB_STATE_INTERNET_VALIDATED = 3;

    /** MBB has got the internet validation failed notification. */
    @VisibleForTesting
    static final int MBB_STATE_VALIDATION_FAILED = 4;

    /** MBB has got the role changed event when both two roles are secondary transient. This
     * happens in the middle of the old/new primary role switch */
    @VisibleForTesting
    static final int MBB_STATE_ROLES_BEING_SWITCHED_BOTH_SECONDARY_TRANSIENT = 5;

    /** MBB has got the role changed event when one role is primary and anther is secondary
     * transient. This happens when the old/new primary role switch is done. */
    @VisibleForTesting
    static final int MBB_STATE_ROLE_SWITCH_COMPLETE = 6;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = {"MBB_STATE_"}, value = {
            MBB_STATE_NONE,
            MBB_STATE_SECONDARY_TRANSIENT_CREATED,
            MBB_STATE_CAPTIVE_PORTAL_DETECTED,
            MBB_STATE_INTERNET_VALIDATED,
            MBB_STATE_ROLES_BEING_SWITCHED_BOTH_SECONDARY_TRANSIENT,
            MBB_STATE_ROLE_SWITCH_COMPLETE})
    private @interface MbbInternalState {}

    private static class MakeBeforeBreakInfo {
        @NonNull
        public final ConcreteClientModeManager oldPrimary;
        @NonNull
        public final ConcreteClientModeManager newPrimary;

        MakeBeforeBreakInfo(
                @NonNull ConcreteClientModeManager oldPrimary,
                @NonNull ConcreteClientModeManager newPrimary) {
            this.oldPrimary = oldPrimary;
            this.newPrimary = newPrimary;
        }

        @Override
        public String toString() {
            return "MakeBeforeBreakInfo{"
                    + "oldPrimary=" + oldPrimary
                    + ", newPrimary=" + newPrimary
                    + '}';
        }
    }

    @Nullable
    private MakeBeforeBreakInfo mMakeBeforeBreakInfo = null;

    public MakeBeforeBreakManager(
            @NonNull ActiveModeWarden activeModeWarden,
            @NonNull FrameworkFacade frameworkFacade,
            @NonNull Context context,
            @NonNull ClientModeImplMonitor cmiMonitor,
            @NonNull ClientModeManagerBroadcastQueue broadcastQueue,
            @NonNull WifiMetrics wifiMetrics) {
        mActiveModeWarden = activeModeWarden;
        mFrameworkFacade = frameworkFacade;
        mContext = context;
        mCmiMonitor = cmiMonitor;
        mBroadcastQueue = broadcastQueue;
        mWifiMetrics = wifiMetrics;

        mActiveModeWarden.registerModeChangeCallback(new ModeChangeCallback());
        mCmiMonitor.registerListener(new ClientModeImplListener() {
            @Override
            public void onInternetValidated(@NonNull ConcreteClientModeManager clientModeManager) {
                MakeBeforeBreakManager.this.onInternetValidated(clientModeManager);
            }

            @Override
            public void onCaptivePortalDetected(
                    @NonNull ConcreteClientModeManager clientModeManager) {
                MakeBeforeBreakManager.this.onCaptivePortalDetected(clientModeManager);
            }

            @Override
            public void onInternetValidationFailed(
                    @NonNull ConcreteClientModeManager clientModeManager,
                    boolean currentConnectionDetectedCaptivePortal) {
                MakeBeforeBreakManager.this.onInternetValidationFailed(clientModeManager,
                        currentConnectionDetectedCaptivePortal);
            }
        });
    }

    public void setVerboseLoggingEnabled(boolean enabled) {
        mVerboseLoggingEnabled = enabled;
    }

    private class ModeChangeCallback implements ActiveModeWarden.ModeChangeCallback {
        @Override
        public void onActiveModeManagerAdded(@NonNull ActiveModeManager activeModeManager) {
            if (!mActiveModeWarden.isStaStaConcurrencySupportedForMbb()) {
                return;
            }
            if (!(activeModeManager instanceof ConcreteClientModeManager)) {
                return;
            }
            // just in case
            recoverPrimary();
            if (activeModeManager.getRole() == ROLE_CLIENT_SECONDARY_TRANSIENT) {
                transitionToState(MBB_STATE_SECONDARY_TRANSIENT_CREATED);
            } else {
                Log.w(TAG, " MBB expects ROLE_CLIENT_SECONDARY_TRANSIENT but got "
                        + activeModeManager.getRole());
            }
        }

        @Override
        public void onActiveModeManagerRemoved(@NonNull ActiveModeManager activeModeManager) {
            if (!mActiveModeWarden.isStaStaConcurrencySupportedForMbb()) {
                return;
            }
            if (!(activeModeManager instanceof ConcreteClientModeManager)) {
                return;
            }
            // if either the old or new primary stopped during MBB, abort the MBB attempt
            ConcreteClientModeManager clientModeManager =
                    (ConcreteClientModeManager) activeModeManager;
            if (mVerboseLoggingEnabled) {
                if (mInternalState == MBB_STATE_VALIDATION_FAILED) {
                    Log.w(TAG, " ClientModeManager " + clientModeManager + " removed because"
                            + " internet validation failed.");
                }
            }
            if (mMakeBeforeBreakInfo != null) {
                boolean oldPrimaryStopped = clientModeManager == mMakeBeforeBreakInfo.oldPrimary;
                boolean newPrimaryStopped = clientModeManager == mMakeBeforeBreakInfo.newPrimary;
                if (oldPrimaryStopped || newPrimaryStopped) {
                    Log.i(TAG, "MBB CMM stopped, aborting:"
                            + " oldPrimary=" + mMakeBeforeBreakInfo.oldPrimary
                            + " stopped=" + oldPrimaryStopped
                            + " newPrimary=" + mMakeBeforeBreakInfo.newPrimary
                            + " stopped=" + newPrimaryStopped);
                    mMakeBeforeBreakInfo = null;
                }
            }
            recoverPrimary();
            triggerOnStoppedListenersIfNoMoreSecondaryTransientCmms();
            transitionToState(MBB_STATE_NONE);
        }

        @Override
        public void onActiveModeManagerRoleChanged(@NonNull ActiveModeManager activeModeManager) {
            if (!mActiveModeWarden.isStaStaConcurrencySupportedForMbb()) {
                return;
            }
            if (!(activeModeManager instanceof ConcreteClientModeManager)) {
                return;
            }
            ConcreteClientModeManager clientModeManager =
                    (ConcreteClientModeManager) activeModeManager;
            recoverPrimary();
            maybeContinueMakeBeforeBreak(clientModeManager);
            triggerOnStoppedListenersIfNoMoreSecondaryTransientCmms();
        }
    }

    private String getInternalStateStr(@MbbInternalState int state) {
        switch (state) {
            case MBB_STATE_NONE:
                return "MBB_STATE_NONE";
            case MBB_STATE_SECONDARY_TRANSIENT_CREATED:
                return "MBB_STATE_SECONDARY_TRANSIENT_CREATED";
            case MBB_STATE_CAPTIVE_PORTAL_DETECTED:
                return "MBB_STATE_CAPTIVE_PORTAL_DETECTED";
            case MBB_STATE_INTERNET_VALIDATED:
                return "MBB_STATE_INTERNET_VALIDATED";
            case MBB_STATE_ROLES_BEING_SWITCHED_BOTH_SECONDARY_TRANSIENT:
                return "MBB_STATE_ROLES_BEING_SWITCHED_BOTH_SECONDARY_TRANSIENT";
            case MBB_STATE_ROLE_SWITCH_COMPLETE:
                return "MBB_STATE_ROLE_SWITCH_COMPLETE";
            default:
                return "UNKNOWN MBB_STATE";
        }
    }

    private void transitionToState(@MbbInternalState int state) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "MBB state transition: from = " + getInternalStateStr(mInternalState)
                    + ", to = " + getInternalStateStr(state));
        }
        mInternalState = state;
    }

    // Get internal MBB state. Package private.
    @TestApi
    @MbbInternalState int getInternalState() {
        return mInternalState;
    }

    /**
     * Failsafe: if there is no primary CMM but there exists exactly one CMM in
     * {@link ActiveModeManager#ROLE_CLIENT_SECONDARY_TRANSIENT}, or multiple and MBB is not
     * in progress (to avoid interfering with MBB), make it primary.
     */
    private void recoverPrimary() {
        // already have a primary, do nothing
        if (mActiveModeWarden.getPrimaryClientModeManagerNullable() != null) {
            return;
        }
        List<ConcreteClientModeManager> secondaryTransientCmms =
                mActiveModeWarden.getClientModeManagersInRoles(ROLE_CLIENT_SECONDARY_TRANSIENT);
        // exactly 1 secondary transient, or > 1 secondary transient and MBB is not in progress
        if (secondaryTransientCmms.size() == 1
                || (mMakeBeforeBreakInfo == null && secondaryTransientCmms.size() > 1)) {
            ConcreteClientModeManager manager = secondaryTransientCmms.get(0);
            manager.setRole(ROLE_CLIENT_PRIMARY, mFrameworkFacade.getSettingsWorkSource(mContext));
            Log.i(TAG, "recoveryPrimary kicking in, making " + manager + " primary and stopping"
                    + " all other SECONDARY_TRANSIENT ClientModeManagers");
            mWifiMetrics.incrementMakeBeforeBreakRecoverPrimaryCount();
            // tear down the extra secondary transient CMMs (if they exist)
            for (int i = 1; i < secondaryTransientCmms.size(); i++) {
                secondaryTransientCmms.get(i).stop();
            }
        }
    }

    /**
     * A ClientModeImpl instance has been validated to have internet connection. This will begin the
     * Make-Before-Break transition to make this the new primary network.
     *
     * Change the previous primary ClientModeManager to role
     * {@link ActiveModeManager#ROLE_CLIENT_SECONDARY_TRANSIENT} and change the new
     * primary to role {@link ActiveModeManager#ROLE_CLIENT_PRIMARY}.
     *
     * @param newPrimary the corresponding ConcreteClientModeManager instance for the ClientModeImpl
     *                   that had its internet connection validated.
     */
    private void onInternetValidated(@NonNull ConcreteClientModeManager newPrimary) {
        if (!mActiveModeWarden.isStaStaConcurrencySupportedForMbb()) {
            return;
        }
        if (newPrimary.getRole() != ROLE_CLIENT_SECONDARY_TRANSIENT) {
            return;
        }

        ConcreteClientModeManager currentPrimary =
                mActiveModeWarden.getPrimaryClientModeManagerNullable();

        if (currentPrimary == null) {
            Log.e(TAG, "changePrimaryClientModeManager(): current primary CMM is null!");
            newPrimary.setRole(
                    ROLE_CLIENT_PRIMARY, mFrameworkFacade.getSettingsWorkSource(mContext));
            return;
        }
        if (newPrimary.getPreviousRole() == ROLE_CLIENT_PRIMARY) {
            Log.i(TAG, "Don't start MBB when internet is validated on the lingering "
                    + "secondary.");
            return;
        }

        Log.i(TAG, "Starting MBB switch primary from " + currentPrimary + " to " + newPrimary
                + " by setting current primary's role to ROLE_CLIENT_SECONDARY_TRANSIENT");

        mWifiMetrics.incrementMakeBeforeBreakInternetValidatedCount();

        // Since role change is not atomic, we must first make the previous primary CMM into a
        // secondary transient CMM. Thus, after this call to setRole() completes, there is no
        // primary CMM and 2 secondary transient CMMs.
        currentPrimary.setRole(
                ROLE_CLIENT_SECONDARY_TRANSIENT, ActiveModeWarden.INTERNAL_REQUESTOR_WS);
        // immediately send fake disconnection broadcasts upon changing primary CMM's role to
        // SECONDARY_TRANSIENT, because as soon as the CMM becomes SECONDARY_TRANSIENT, its
        // broadcasts will never be sent out again (BroadcastQueue only sends broadcasts for the
        // current primary CMM). This is to preserve the legacy single STA behavior.
        mBroadcastQueue.fakeDisconnectionBroadcasts();
        mMakeBeforeBreakInfo = new MakeBeforeBreakInfo(currentPrimary, newPrimary);
        transitionToState(MBB_STATE_INTERNET_VALIDATED);
    }

    /**
     * Notify MBB manager the internet validation has failed. This may come before
     * onInternetValidated and mMakeBeforeBreakInfo is null.
     * @param clientModeManager client mode manager for current connection.
     * @param currentConnectionDetectedCaptivePortal whether current connection has detected
     *                                               captive portal.
     */
    private void onInternetValidationFailed(ConcreteClientModeManager clientModeManager,
            boolean currentConnectionDetectedCaptivePortal) {
        final ConcreteClientModeManager secondaryCcm = mActiveModeWarden.getClientModeManagerInRole(
                ROLE_CLIENT_SECONDARY_TRANSIENT);
        // There has to be at least one ROLE_CLIENT_SECONDARY_TRANSIENT.
        if (secondaryCcm == null) {
            Log.w(TAG, "No ClientModeManager with ROLE_CLIENT_SECONDARY_TRANSIENT exist!"
                    + " current state: " + getInternalStateStr(mInternalState));
            return;
        }

        Log.w(TAG, "Internet validation failed during MBB,"
                + " disconnecting ClientModeManager=" + clientModeManager);
        mWifiMetrics.logStaEvent(
                clientModeManager.getInterfaceName(),
                WifiMetricsProto.StaEvent.TYPE_FRAMEWORK_DISCONNECT,
                WifiMetricsProto.StaEvent.DISCONNECT_MBB_NO_INTERNET);
        mWifiMetrics.incrementMakeBeforeBreakNoInternetCount();

        // If the role has already switched, switch the roles back
        if (clientModeManager.getRole() == ROLE_CLIENT_PRIMARY) {
            clientModeManager.setRole(ROLE_CLIENT_SECONDARY_TRANSIENT,
                    mFrameworkFacade.getSettingsWorkSource(mContext));
            secondaryCcm.setRole(ROLE_CLIENT_PRIMARY,
                    mFrameworkFacade.getSettingsWorkSource(mContext));
        }
        // Disconnect the client mode manager.
        clientModeManager.disconnect();

        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "onInternetValidationFailed (" + getInternalStateStr(mInternalState)
                    + "), removeClientModeManager role: " + clientModeManager.getRole());
        }
        // This should trigger {@link ModeChangeCallback#onActiveModeManagerRemoved} to abort MBB.
        mActiveModeWarden.removeClientModeManager(clientModeManager);
        transitionToState(MBB_STATE_VALIDATION_FAILED);
    }

    private void onCaptivePortalDetected(@NonNull ConcreteClientModeManager newPrimary) {
        if (!mActiveModeWarden.isStaStaConcurrencySupportedForMbb()) {
            return;
        }
        if (newPrimary.getRole() != ROLE_CLIENT_SECONDARY_TRANSIENT) {
            return;
        }

        ConcreteClientModeManager currentPrimary =
                mActiveModeWarden.getPrimaryClientModeManagerNullable();

        if (currentPrimary == null) {
            Log.i(TAG, "onCaptivePortalDetected: Current primary is null, nothing to stop");
        } else {
            Log.i(TAG, "onCaptivePortalDetected: stopping current primary CMM");
            currentPrimary.setWifiStateChangeBroadcastEnabled(false);
            currentPrimary.stop();
        }
        // Once the currentPrimary teardown completes, recoverPrimary() will make the Captive
        // Portal CMM the new primary, because it is the only SECONDARY_TRANSIENT CMM and no
        // primary CMM exists.
        transitionToState(MBB_STATE_CAPTIVE_PORTAL_DETECTED);
    }

    private void maybeContinueMakeBeforeBreak(
            @NonNull ConcreteClientModeManager roleChangedClientModeManager) {
        // not in the middle of MBB
        if (mMakeBeforeBreakInfo == null) {
            // The new STA iface has been changed to primary.
            transitionToState(MBB_STATE_ROLE_SWITCH_COMPLETE);
            return;
        }
        // not the CMM we're looking for, keep monitoring
        if (roleChangedClientModeManager != mMakeBeforeBreakInfo.oldPrimary) {
            return;
        }
        try {
            // if old primary didn't transition to secondary transient, abort the MBB attempt
            if (mMakeBeforeBreakInfo.oldPrimary.getRole() != ROLE_CLIENT_SECONDARY_TRANSIENT) {
                Log.i(TAG, "old primary is no longer secondary transient, aborting MBB: "
                        + mMakeBeforeBreakInfo.oldPrimary);
                return;
            }

            // if somehow the next primary is no longer secondary transient, abort the MBB attempt
            if (mMakeBeforeBreakInfo.newPrimary.getRole() != ROLE_CLIENT_SECONDARY_TRANSIENT) {
                Log.i(TAG, "new primary is no longer secondary transient, abort MBB: "
                        + mMakeBeforeBreakInfo.newPrimary);
                return;
            }
            // The old primary has been changed to secondary transient.
            transitionToState(MBB_STATE_ROLES_BEING_SWITCHED_BOTH_SECONDARY_TRANSIENT);
            Log.i(TAG, "Continue MBB switch primary from " + mMakeBeforeBreakInfo.oldPrimary
                    + " to " + mMakeBeforeBreakInfo.newPrimary
                    + " by setting new Primary's role to ROLE_CLIENT_PRIMARY and reducing network"
                    + " score of old primary");

            // TODO(b/180974604): In theory, newPrimary.setRole() could still fail, but that would
            //  still count as a MBB success in the metrics. But we don't really handle that
            //  scenario well anyways, see TODO below.
            mWifiMetrics.incrementMakeBeforeBreakSuccessCount();

            // otherwise, actually set the new primary's role to primary.
            mMakeBeforeBreakInfo.newPrimary.setRole(
                    ROLE_CLIENT_PRIMARY, mFrameworkFacade.getSettingsWorkSource(mContext));

            // linger old primary
            // TODO(b/160346062): maybe do this after the new primary was fully transitioned to
            //  ROLE_CLIENT_PRIMARY (since setRole() is asynchronous)
            mMakeBeforeBreakInfo.oldPrimary.setShouldReduceNetworkScore(true);
        } finally {
            // end the MBB attempt
            mMakeBeforeBreakInfo = null;
        }
    }

    /** Dump fields for debugging. */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("Dump of MakeBeforeBreakManager");
        pw.println("mMakeBeforeBreakInfo=" + mMakeBeforeBreakInfo);
        pw.println("mInternalState " + getInternalStateStr(mInternalState));
    }

    /**
     * Stop all ClientModeManagers with role
     * {@link ActiveModeManager#ROLE_CLIENT_SECONDARY_TRANSIENT}.
     *
     * This is useful when an explicit connection was requested by an external caller
     * (e.g. Settings, legacy app calling {@link android.net.wifi.WifiManager#enableNetwork}).
     * We should abort any ongoing Make Before Break attempt to avoid interrupting the explicit
     * connection.
     *
     * @param onStoppedListener triggered when all secondary transient CMMs have been stopped.
     */
    public void stopAllSecondaryTransientClientModeManagers(Runnable onStoppedListener) {
        // no secondary transient CMM exists, trigger the callback immediately and return
        if (mActiveModeWarden.getClientModeManagerInRole(ROLE_CLIENT_SECONDARY_TRANSIENT) == null) {
            if (mVerboseLoggingEnabled) {
                Log.d(TAG, "No secondary transient CMM active, trigger callback immediately");
            }
            onStoppedListener.run();
            return;
        }

        // there exists at least 1 secondary transient CMM, but no primary
        // TODO(b/177692017): Since switching roles is not atomic, there is a short period of time
        //  during the Make Before Break transition when there are 2 SECONDARY_TRANSIENT CMMs and 0
        //  primary CMMs. If this method is called at that time, it will destroy all CMMs, resulting
        //  in no primary, and causing any subsequent connections to fail. Hopefully this does
        //  not occur frequently.
        if (mActiveModeWarden.getPrimaryClientModeManagerNullable() == null) {
            Log.wtf(TAG, "Called stopAllSecondaryTransientClientModeManagers with no primary CMM!");
        }

        mOnAllSecondaryTransientCmmsStoppedListeners.add(onStoppedListener);
        mActiveModeWarden.stopAllClientModeManagersInRole(ROLE_CLIENT_SECONDARY_TRANSIENT);
    }

    private void triggerOnStoppedListenersIfNoMoreSecondaryTransientCmms() {
        // not all secondary transient CMMs stopped, keep waiting
        if (mActiveModeWarden.getClientModeManagerInRole(ROLE_CLIENT_SECONDARY_TRANSIENT) != null) {
            return;
        }

        if (mVerboseLoggingEnabled) {
            Log.i(TAG, "All secondary transient CMMs stopped, triggering queued callbacks");
        }

        for (Runnable onStoppedListener : mOnAllSecondaryTransientCmmsStoppedListeners) {
            onStoppedListener.run();
        }
        mOnAllSecondaryTransientCmmsStoppedListeners.clear();
    }

}
