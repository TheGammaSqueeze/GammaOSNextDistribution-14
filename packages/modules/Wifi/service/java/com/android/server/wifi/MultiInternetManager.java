/*
 * Copyright (C) 2021 The Android Open Source Project
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
import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_SECONDARY_LONG_LIVED;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.WorkSource;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;

/**
 * Manages STA + STA for multi internet networks.
 */
public class MultiInternetManager {
    private static final String TAG = "WifiMultiInternet";

    private final ActiveModeWarden mActiveModeWarden;
    private final FrameworkFacade mFrameworkFacade;
    private final Context mContext;
    private final ClientModeImplMonitor mCmiMonitor;
    private final WifiSettingsStore mSettingsStore;
    private final Handler mEventHandler;
    private final Clock mClock;
    private int mStaConcurrencyMultiInternetMode = WifiManager.WIFI_MULTI_INTERNET_MODE_DISABLED;
    @MultiInternetState
    private int mMultiInternetConnectionState = MULTI_INTERNET_STATE_NONE;
    private ConnectionStatusListener mConnectionStatusListener;

    private SparseArray<NetworkConnectionState> mNetworkConnectionStates = new SparseArray<>();
    private boolean mVerboseLoggingEnabled = false;

    /** No multi internet connection needed. */
    public static final int MULTI_INTERNET_STATE_NONE = 0;
    /** Multi internet connection is connecting. */
    public static final int MULTI_INTERNET_STATE_CONNECTION_REQUESTED = 1;
    /** No multi internet connection is connected. */
    public static final int MULTI_INTERNET_STATE_CONNECTED = 2;
    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = {"MULTI_INTERNET_STATE_"}, value = {
            MULTI_INTERNET_STATE_NONE,
            MULTI_INTERNET_STATE_CONNECTION_REQUESTED,
            MULTI_INTERNET_STATE_CONNECTED})
    public @interface MultiInternetState {}

    /** The internal network connection state per each Wi-Fi band. */
    class NetworkConnectionState {
        // If the supplicant connection is completed.
        private boolean mConnected;
        // If the internet has been validated.
        private boolean mValidated;
        // The connection start time in millisecond
        public long connectionStartTimeMillis;
        // The WorkSource of the connection requestor.
        public WorkSource requestorWorkSource;
        public String bssid;

        NetworkConnectionState(WorkSource workSource) {
            this(workSource, -1L);
        }

        NetworkConnectionState(WorkSource workSource, long connectionStartTime) {
            requestorWorkSource = workSource;
            connectionStartTimeMillis = connectionStartTime;

            mConnected = false;
            mValidated = false;
            bssid = null;
        }

        public NetworkConnectionState setConnected(boolean connected) {
            mConnected = connected;
            return this;
        }

        @VisibleForTesting
        public boolean isConnected() {
            return mConnected;
        }

        public NetworkConnectionState setValidated(boolean validated) {
            mValidated = validated;
            return this;
        }

        @VisibleForTesting
        public boolean isValidated() {
            return mValidated;
        }
    }

    @VisibleForTesting
    SparseArray<NetworkConnectionState> getNetworkConnectionState() {
        return mNetworkConnectionStates;
    }

    /** The Multi Internet Connection Status Listener. The registered listener will be notified
     * for the connection status change and scan needed. */
    public interface ConnectionStatusListener {
        /** Called when connection status changed */
        void onStatusChange(
                @MultiInternetManager.MultiInternetState int state,
                WorkSource requestorWs);
        /** Called when a scan is needed */
        void onStartScan(WorkSource requestorWs);
    }

    @Nullable private WorkSource getRequestorWorkSource(int band) {
        if (!mNetworkConnectionStates.contains(band)) {
            return null;
        }
        return mNetworkConnectionStates.get(band).requestorWorkSource;
    }

    private class ModeChangeCallback implements ActiveModeWarden.ModeChangeCallback {
        @Override
        public void onActiveModeManagerAdded(@NonNull ActiveModeManager activeModeManager) {
            if (!mActiveModeWarden.isStaStaConcurrencySupportedForMultiInternet()
                    || !isStaConcurrencyForMultiInternetEnabled()) {
                return;
            }
            if (!(activeModeManager instanceof ConcreteClientModeManager)) {
                return;
            }
            final ConcreteClientModeManager ccm = (ConcreteClientModeManager) activeModeManager;
            // TODO: b/197670907 : Add client role ROLE_CLIENT_SECONDARY_INTERNET
            if (ccm.getRole() != ROLE_CLIENT_SECONDARY_LONG_LIVED || !ccm.isSecondaryInternet()) {
                return;
            }
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "Secondary ClientModeManager created for internet, connecting!");
            }
            updateNetworkConnectionStates();
        }

        @Override
        public void onActiveModeManagerRemoved(@NonNull ActiveModeManager activeModeManager) {
            if (!mActiveModeWarden.isStaStaConcurrencySupportedForMultiInternet()
                    || !isStaConcurrencyForMultiInternetEnabled()) {
                return;
            }
            if (!(activeModeManager instanceof ConcreteClientModeManager)) {
                return;
            }
            final ConcreteClientModeManager ccm = (ConcreteClientModeManager) activeModeManager;
            // TODO: b/197670907 : Add client role ROLE_CLIENT_SECONDARY_INTERNET
            // Needs to call getPreviousRole here since the role is set to null after a CMM stops
            if (ccm.getPreviousRole() != ROLE_CLIENT_SECONDARY_LONG_LIVED
                    || !ccm.isSecondaryInternet()) {
                return;
            }
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "ClientModeManager for internet removed");
            }
            // A secondary cmm was removed because of the connection was lost, start scan
            // to find the new network connection.
            updateNetworkConnectionStates();
            if (hasPendingConnectionRequests()) {
                startConnectivityScan();
            }
        }

        @Override
        public void onActiveModeManagerRoleChanged(@NonNull ActiveModeManager activeModeManager) {
            if (!mActiveModeWarden.isStaStaConcurrencySupportedForMultiInternet()
                    || !isStaConcurrencyForMultiInternetEnabled()) {
                return;
            }
            if (!(activeModeManager instanceof ConcreteClientModeManager)) {
                return;
            }
            final ConcreteClientModeManager ccm = (ConcreteClientModeManager) activeModeManager;
            if (ccm.getPreviousRole() == ROLE_CLIENT_SECONDARY_LONG_LIVED
                    && ccm.isSecondaryInternet()) {
                Log.w(TAG, "Secondary client mode manager changed role to "
                        + ccm.getRole());
            }
            updateNetworkConnectionStates();
        }
    }

    private class ClientModeListenerInternal implements ClientModeImplListener {
        @Override
        public void onInternetValidated(@NonNull ConcreteClientModeManager clientModeManager) {
            if (!mActiveModeWarden.isStaStaConcurrencySupportedForMultiInternet()
                    || !isStaConcurrencyForMultiInternetEnabled()) {
                return;
            }
            final WifiInfo info = clientModeManager.getConnectionInfo();
            if (info != null) {
                final int band = ScanResult.toBand(info.getFrequency());
                if (mNetworkConnectionStates.contains(band)) {
                    mNetworkConnectionStates.get(band).setValidated(true);
                }
            }
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "ClientModeManager role " + clientModeManager.getRole()
                        + " internet validated for connection");
            }
            // If the primary role was connected and internet validated, update the connection state
            // immediately and issue scan for secondary network connection if needed.
            // If the secondary role was connected and internet validated, update the connection
            // state and notify connectivity manager.
            // TODO: b/197670907 : Add client role ROLE_CLIENT_SECONDARY_INTERNET
            if (clientModeManager.getRole() == ROLE_CLIENT_PRIMARY) {
                updateNetworkConnectionStates();
                final int band = findUnconnectedRequestBand();
                if (band != ScanResult.UNSPECIFIED) {
                    // Trigger the connectivity scan
                    mConnectionStatusListener.onStartScan(getRequestorWorkSource(band));
                }
            } else if (clientModeManager.getRole() == ROLE_CLIENT_SECONDARY_LONG_LIVED
                    && clientModeManager.isSecondaryInternet()) {
                updateNetworkConnectionStates();
            }
        }

        // TODO(b/175896748): not yet triggered by ClientModeImpl
        @Override
        public void onL3Connected(@NonNull ConcreteClientModeManager clientModeManager) {
            if (!mActiveModeWarden.isStaStaConcurrencySupportedForMultiInternet()
                    || !isStaConcurrencyForMultiInternetEnabled()) {
                return;
            }
            // TODO: b/197670907 : Add client role ROLE_CLIENT_SECONDARY_INTERNET
            if (clientModeManager.getRole() != ROLE_CLIENT_SECONDARY_LONG_LIVED
                    || !clientModeManager.isSecondaryInternet()) {
                return;
            }
            updateNetworkConnectionStates();
            // If no pending connection requests, update connection listener.
            if (!hasPendingConnectionRequests()) {
                final int band = getSecondaryConnectedNetworkBand();
                if (band == ScanResult.UNSPECIFIED) return;
                final long connectionTime = mClock.getElapsedSinceBootMillis()
                        - mNetworkConnectionStates.get(band).connectionStartTimeMillis;
                if (mVerboseLoggingEnabled) {
                    Log.v(TAG, "ClientModeManager for internet L3 connected for "
                            + connectionTime + " ms.");
                }
                handleConnectionStateChange(MULTI_INTERNET_STATE_CONNECTED,
                        getRequestorWorkSource(band));
            }
        }

        @Override
        public void onConnectionEnd(@NonNull ConcreteClientModeManager clientModeManager) {
            if (!mActiveModeWarden.isStaStaConcurrencySupportedForMultiInternet()
                    || !isStaConcurrencyForMultiInternetEnabled()) {
                return;
            }
            if (clientModeManager.getRole() == ROLE_CLIENT_PRIMARY) {
                if (mVerboseLoggingEnabled) {
                    Log.v(TAG, "Connection end on primary client mode manager");
                }
                // When the primary network connection is ended, disconnect the secondary network,
                // as the secondary network is opportunistic.
                // TODO: b/197670907 : Add client role ROLE_CLIENT_SECONDARY_INTERNET
                for (ConcreteClientModeManager cmm : mActiveModeWarden.getClientModeManagersInRoles(
                        ROLE_CLIENT_SECONDARY_LONG_LIVED)) {
                    if (cmm.isSecondaryInternet()) {
                        if (mVerboseLoggingEnabled) {
                            Log.v(TAG, "Disconnect secondary client mode manager");
                        }
                        cmm.disconnect();
                    }
                }
                // As the secondary network is disconnected, mark all bands as disconnected.
                for (int i = 0; i < mNetworkConnectionStates.size(); i++) {
                    mNetworkConnectionStates.valueAt(i).setConnected(false);
                }
            }
            updateNetworkConnectionStates();
        }
    }

    public MultiInternetManager(
            @NonNull ActiveModeWarden activeModeWarden,
            @NonNull FrameworkFacade frameworkFacade,
            @NonNull Context context,
            @NonNull ClientModeImplMonitor cmiMonitor,
            @NonNull WifiSettingsStore settingsStore,
            @NonNull Handler handler,
            @NonNull Clock clock) {
        mActiveModeWarden = activeModeWarden;
        mFrameworkFacade = frameworkFacade;
        mContext = context;
        mCmiMonitor = cmiMonitor;
        mSettingsStore = settingsStore;
        mEventHandler = handler;
        mClock = clock;
        mActiveModeWarden.registerModeChangeCallback(new ModeChangeCallback());
        cmiMonitor.registerListener(new ClientModeListenerInternal());
        mStaConcurrencyMultiInternetMode = mSettingsStore.getWifiMultiInternetMode();
    }

    /**
     * Check if Wi-Fi multi internet use case is enabled.
     *
     * @return true if Wi-Fi multi internet use case is enabled.
     */
    public boolean isStaConcurrencyForMultiInternetEnabled() {
        return mStaConcurrencyMultiInternetMode
            != WifiManager.WIFI_MULTI_INTERNET_MODE_DISABLED;
    }

    /**
     * Check if Wi-Fi multi internet use case allows multi AP.
     *
     * @return true if Wi-Fi multi internet use case allows multi AP.
     */
    public boolean isStaConcurrencyForMultiInternetMultiApAllowed() {
        return mStaConcurrencyMultiInternetMode
                == WifiManager.WIFI_MULTI_INTERNET_MODE_MULTI_AP;
    }

    /**
     * Return Wi-Fi multi internet use case mode.
     *
     * @return Current mode of Wi-Fi multi internet use case.
     */
    public @WifiManager.WifiMultiInternetMode int getStaConcurrencyForMultiInternetMode() {
        if (mActiveModeWarden.isStaStaConcurrencySupportedForMultiInternet()) {
            return mStaConcurrencyMultiInternetMode;
        }
        return WifiManager.WIFI_MULTI_INTERNET_MODE_DISABLED;
    }

    /**
     * Set the multi internet use case mode.
     * @return true if the mode set successfully, false if failed.
     */
    public boolean setStaConcurrencyForMultiInternetMode(
            @WifiManager.WifiMultiInternetMode int mode) {
        final boolean enabled = (mode != WifiManager.WIFI_MULTI_INTERNET_MODE_DISABLED);
        if (!mActiveModeWarden.isStaStaConcurrencySupportedForMultiInternet()) {
            return false;
        }

        if (mode == mStaConcurrencyMultiInternetMode) {
            return true;
        }
        mStaConcurrencyMultiInternetMode = mode;
        // If the STA+STA multi internet feature was disabled, disconnect the secondary cmm.
        // TODO: b/197670907 : Add client role ROLE_CLIENT_SECONDARY_INTERNET
        if (!enabled) {
            for (ConcreteClientModeManager cmm : mActiveModeWarden.getClientModeManagersInRoles(
                    ROLE_CLIENT_SECONDARY_LONG_LIVED)) {
                if (cmm.isSecondaryInternet()) {
                    cmm.disconnect();
                }
            }
            handleConnectionStateChange(MULTI_INTERNET_STATE_NONE, null);
        } else {
            updateNetworkConnectionStates();
            final int band = findUnconnectedRequestBand();
            if (band != ScanResult.UNSPECIFIED) {
                handleConnectionStateChange(MULTI_INTERNET_STATE_CONNECTION_REQUESTED,
                        getRequestorWorkSource(band));
            }
        }

        mSettingsStore.handleWifiMultiInternetMode(mode);
        // Check if there is already multi internet request then start scan for connection.
        if (hasPendingConnectionRequests()) {
            startConnectivityScan();
        }
        return true;
    }

    public void setVerboseLoggingEnabled(boolean enabled) {
        mVerboseLoggingEnabled = enabled;
    }

    /** Set the Multi Internet Connection Status listener.
     *
     * @param listener The Multi Internet Connection Status listener.
     */
    public void setConnectionStatusListener(ConnectionStatusListener listener) {
        mConnectionStatusListener = listener;
    }

    /** Notify the BSSID associated event from ClientModeImpl. Triggered by
     *  WifiMonitor.ASSOCIATED_BSSID_EVENT.
     *  @param clientModeManager the client mode manager with BSSID associated event.
     */
    public void notifyBssidAssociatedEvent(ConcreteClientModeManager clientModeManager) {
        if (clientModeManager.getRole() != ROLE_CLIENT_PRIMARY
                || !mActiveModeWarden.isStaStaConcurrencySupportedForMultiInternet()
                || !isStaConcurrencyForMultiInternetEnabled()) {
            return;
        }
        // If primary CMM has associated to a new BSSID, need to check if it is in a different band
        // of secondary CMM.
        final WifiInfo info = clientModeManager.getConnectionInfo();
        final ConcreteClientModeManager secondaryCcmm =
                mActiveModeWarden.getClientModeManagerInRole(ROLE_CLIENT_SECONDARY_LONG_LIVED);
        // If no secondary client mode manager then it's ok
        if (secondaryCcmm == null) return;
        // If secondary client mode manager is not connected or not for secondary internet
        if (!secondaryCcmm.isConnected() || !secondaryCcmm.isSecondaryInternet()) return;
        final WifiInfo info2 = secondaryCcmm.getConnectionInfo();
        // If secondary network is in same band as primary now
        if (ScanResult.toBand(info.getFrequency()) == ScanResult.toBand(info2.getFrequency())) {
            // Need to disconnect secondary network
            secondaryCcmm.disconnect();
            // As the secondary network is disconnected, mark all bands as disconnected.
            for (int i = 0; i < mNetworkConnectionStates.size(); i++) {
                mNetworkConnectionStates.valueAt(i).setConnected(false);
            }
            updateNetworkConnectionStates();
        }
    }

    /** Check if there is a connection request for multi internet
     * @return true if there is one or more connection request
     */
    public boolean hasPendingConnectionRequests() {
        return findUnconnectedRequestBand() != ScanResult.UNSPECIFIED;
    }

    /**
     * Check if there is unconnected network connection request.
     * @return the band of the connection request that is still not connected.
     */
    public int findUnconnectedRequestBand() {
        if (mStaConcurrencyMultiInternetMode == WifiManager.WIFI_MULTI_INTERNET_MODE_DISABLED) {
            return ScanResult.UNSPECIFIED;
        }
        for (int i = 0; i < mNetworkConnectionStates.size(); i++) {
            if (!mNetworkConnectionStates.valueAt(i).isConnected()) {
                return mNetworkConnectionStates.keyAt(i);
            }
        }
        return ScanResult.UNSPECIFIED;
    }

    /**
     * Return the mapping from band (one of ScanResult.WIFI_BAND_*) to the specified BSSID for that
     * band.
     */
    public Map<Integer, String> getSpecifiedBssids() {
        Map<Integer, String> result = new ArrayMap<>();
        for (int i = 0; i < mNetworkConnectionStates.size(); i++) {
            int band = mNetworkConnectionStates.keyAt(i);
            String bssid = mNetworkConnectionStates.valueAt(i).bssid;
            if (bssid != null) {
                result.put(band, bssid);
            }
        }
        return result;
    }

    /**
     * Traverse the client mode managers and update the internal connection states.
     */
    private void updateNetworkConnectionStates() {
        for (int i = 0; i < mNetworkConnectionStates.size(); i++) {
            mNetworkConnectionStates.valueAt(i).setConnected(false);
        }

        for (ClientModeManager clientModeManager :
                mActiveModeWarden.getInternetConnectivityClientModeManagers()) {
            // TODO: b/197670907 : Add client role ROLE_CLIENT_SECONDARY_INTERNET
            if (clientModeManager instanceof ConcreteClientModeManager
                    && (clientModeManager.getRole() == ROLE_CLIENT_PRIMARY
                    || clientModeManager.getRole() == ROLE_CLIENT_SECONDARY_LONG_LIVED)) {
                ConcreteClientModeManager ccmm = (ConcreteClientModeManager) clientModeManager;
                // Exclude the secondary client mode manager not for secondary internet.
                if (ccmm.getRole() == ROLE_CLIENT_SECONDARY_LONG_LIVED
                        && !ccmm.isSecondaryInternet()) {
                    continue;
                }
                WifiInfo info = clientModeManager.getConnectionInfo();
                // Exclude the network that is not connected or restricted.
                if (info == null || !clientModeManager.isConnected()
                        ||  info.isRestricted()) continue;
                // Exclude the network that is oem paid/private.
                if (SdkLevel.isAtLeastT() && (info.isOemPaid() || info.isOemPrivate())) continue;
                final int band = ScanResult.toBand(info.getFrequency());
                if (mNetworkConnectionStates.contains(band)) {
                    // Update the connected state
                    mNetworkConnectionStates.get(band).setConnected(true);
                }
                if (mVerboseLoggingEnabled) {
                    Log.v(TAG, "network band " + band + " role "
                            + clientModeManager.getRole().toString());
                }
            }
        }
        // Handle the state change and notify listener
        if (!hasPendingConnectionRequests()) {
            if (mNetworkConnectionStates.size() == 0) {
                handleConnectionStateChange(MULTI_INTERNET_STATE_NONE, null);
            } else {
                final int band = getSecondaryConnectedNetworkBand();
                if (band == ScanResult.UNSPECIFIED) return;
                handleConnectionStateChange(MULTI_INTERNET_STATE_CONNECTED,
                        getRequestorWorkSource(band));
            }
        } else {
            final int band = findUnconnectedRequestBand();
            handleConnectionStateChange(MULTI_INTERNET_STATE_CONNECTION_REQUESTED,
                    getRequestorWorkSource(band));
        }
    }

    /**
     * Set a network connection request from a requestor WorkSource for a specific band, or clear
     * the connection request if the WorkSource is null.
     * Triggered when {@link MultiInternetWifiNetworkFactory} has a pending network request.
     * @param band The band of the Wi-Fi network requested.
     * @param requestorWs The requestor's WorkSource. Null to clear a network request for a
     * a band.
     */
    public void setMultiInternetConnectionWorksource(int band, String targetBssid,
            WorkSource requestorWs) {
        if (!isStaConcurrencyForMultiInternetEnabled()) {
            Log.w(TAG, "MultInternet is not enabled.");
            return;
        }
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "setMultiInternetConnectionWorksource: band=" + band + ", bssid="
                    + targetBssid + ", requestorWs=" + requestorWs);
        }
        if (requestorWs == null) {
            // Disconnect secondary network if the request is removed.
            if (band == getSecondaryConnectedNetworkBand()) {
                for (ConcreteClientModeManager cmm : mActiveModeWarden.getClientModeManagersInRoles(
                        ROLE_CLIENT_SECONDARY_LONG_LIVED)) {
                    if (cmm.isSecondaryInternet()) {
                        cmm.disconnect();
                    }
                }
            }
            mNetworkConnectionStates.remove(band);
            updateNetworkConnectionStates();
            return;
        }
        if (mNetworkConnectionStates.contains(band)) {
            Log.w(TAG, "band " + band + " already requested.");
            if (TextUtils.equals(mNetworkConnectionStates.get(band).bssid, targetBssid)) {
                // No change in BSSID field, so early return to avoid unnecessary scanning.
                return;
            }
            disconnectSecondaryIfNeeded(band, targetBssid);
        }
        NetworkConnectionState connectionState = new NetworkConnectionState(requestorWs,
                mClock.getElapsedSinceBootMillis());
        connectionState.bssid = targetBssid;
        mNetworkConnectionStates.put(band, connectionState);
        startConnectivityScan();
    }

    /**
     * Disconnect the secondary that's connected to the same band but different bssid.
     */
    private void disconnectSecondaryIfNeeded(int band, String targetBssid) {
        if (targetBssid == null) {
            return;
        }
        for (ConcreteClientModeManager cmm : mActiveModeWarden.getClientModeManagersInRoles(
                ROLE_CLIENT_SECONDARY_LONG_LIVED)) {
            if (cmm.isSecondaryInternet()) {
                WifiInfo wifiInfo = cmm.getConnectionInfo();
                if (band != ScanResult.toBand(wifiInfo.getFrequency())) {
                    continue;
                }
                String connectingBssid = cmm.getConnectingBssid();
                String connectedBssid = cmm.getConnectedBssid();
                if ((connectingBssid != null && !connectingBssid.equals(targetBssid))
                        || (connectedBssid != null && !connectedBssid.equals(targetBssid))) {
                    if (mVerboseLoggingEnabled) {
                        Log.v(TAG, "Disconnect secondary client mode manager due to specified"
                                + " BSSID in the same band");
                    }
                    cmm.disconnect();
                    break;
                }
            }
        }
    }

    /** Returns the band of the secondary network connected. */
    private int getSecondaryConnectedNetworkBand() {
        final ConcreteClientModeManager secondaryCcmm =
                mActiveModeWarden.getClientModeManagerInRole(ROLE_CLIENT_SECONDARY_LONG_LIVED);
        if (secondaryCcmm == null) {
            return ScanResult.UNSPECIFIED;
        }
        final WifiInfo info = secondaryCcmm.getConnectionInfo();
        // Make sure secondary network is connected.
        if (info == null || !secondaryCcmm.isConnected() || !secondaryCcmm.isSecondaryInternet()) {
            return ScanResult.UNSPECIFIED;
        }
        return ScanResult.toBand(info.getFrequency());
    }

    /**
     * Handles the connection state change and notifies the status listener.
     * The listener will only be notified when the state changes. If the state remains the same
     * but with a different requestor WorkSource then the listener is not notified.
     *
     * @param state
     * @param workSource
     */
    private void handleConnectionStateChange(int state, WorkSource workSource) {
        if (mMultiInternetConnectionState == state) {
            return;
        }
        mMultiInternetConnectionState = state;
        mConnectionStatusListener.onStatusChange(state, workSource);
    }

    /**
     * Start a connectivity scan to trigger the network selection process and connect to
     * the requested multi internet networks.
     */
    private void startConnectivityScan() {
        if (!isStaConcurrencyForMultiInternetEnabled()) {
            return;
        }
        updateNetworkConnectionStates();

        final int band = findUnconnectedRequestBand();
        if (band == ScanResult.UNSPECIFIED) return;
        NetworkConnectionState state = mNetworkConnectionStates.get(band);
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Schedule connectivity scan for network request with band " + band
                    + " start time " + state.connectionStartTimeMillis + " now "
                    + mClock.getElapsedSinceBootMillis());
        }
        // Trigger the connectivity scan
        mConnectionStatusListener.onStartScan(getRequestorWorkSource(band));
    }

    /** Dump the internal states of MultiInternetManager */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("Dump of MultiInternetManager");
        pw.println(TAG + ": mStaConcurrencyMultiInternetMode "
                + mStaConcurrencyMultiInternetMode);
        for (int i = 0; i < mNetworkConnectionStates.size(); i++) {
            pw.println("band " + mNetworkConnectionStates.keyAt(i) + " bssid "
                    + mNetworkConnectionStates.valueAt(i).bssid + " connected "
                    + mNetworkConnectionStates.valueAt(i).isConnected()
                    + " validated " + mNetworkConnectionStates.valueAt(i).isValidated());
        }
    }

}
