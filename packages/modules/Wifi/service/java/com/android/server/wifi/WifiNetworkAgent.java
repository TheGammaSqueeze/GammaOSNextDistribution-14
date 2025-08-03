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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.net.KeepalivePacketData;
import android.net.LinkProperties;
import android.net.NetworkAgent;
import android.net.NetworkAgentConfig;
import android.net.NetworkCapabilities;
import android.net.NetworkProvider;
import android.net.Uri;
import android.os.Looper;

import java.time.Duration;

/** Created to facilitate mocking during unit testing. */
public class WifiNetworkAgent extends NetworkAgent {

    private static final String TAG = "WifiNetworkAgent";

    public interface Callback {
        void onNetworkUnwanted();
        void onValidationStatus(int status, @Nullable Uri redirectUri);
        void onSaveAcceptUnvalidated(boolean accept);
        void onStartSocketKeepalive(int slot, @NonNull Duration interval,
                @NonNull KeepalivePacketData packet);
        void onStopSocketKeepalive(int slot);
        void onAddKeepalivePacketFilter(int slot, @NonNull KeepalivePacketData packet);
        void onRemoveKeepalivePacketFilter(int slot);
        void onSignalStrengthThresholdsUpdated(@NonNull int[] thresholds);
        void onAutomaticReconnectDisabled();
        void onDscpPolicyStatusUpdated(int policyId, int status);
    }

    private final Callback mCallback;

    /** Cache the latest NetworkCapabilities update */
    @NonNull private NetworkCapabilities mCurrentNetworkCapabilities;

    /**
     * Create a new network agent.
     *
     * @param context  a {@link Context} to get system services from.
     * @param looper   the {@link Looper} on which to invoke the callbacks.
     * @param nc       the initial {@link NetworkCapabilities} of this network. Update with
     *                 sendNetworkCapabilities.
     * @param lp       the initial {@link LinkProperties} of this network. Update with
     *                 sendLinkProperties.
     * @param score    the initial score of this network. Update with sendNetworkScore.
     * @param config   an immutable {@link NetworkAgentConfig} for this agent.
     * @param provider the {@link NetworkProvider} managing this agent.
     * @param wifiNetworkAgentCallback implementation
     */
    public WifiNetworkAgent(
            @NonNull Context context,
            @NonNull Looper looper,
            @NonNull NetworkCapabilities nc,
            @NonNull LinkProperties lp,
            @NonNull NetworkAgentConfig config,
            @Nullable NetworkProvider provider,
            @NonNull Callback wifiNetworkAgentCallback) {
        super(context, looper, TAG, nc, lp, ConnectedScore.WIFI_INITIAL_SCORE, config, provider);
        mCurrentNetworkCapabilities = nc;
        mCallback = wifiNetworkAgentCallback;
        register();
    }

    @Override
    public void onNetworkUnwanted() {
        mCallback.onNetworkUnwanted();
    }

    @Override
    public void onValidationStatus(int status, @Nullable Uri redirectUri) {
        mCallback.onValidationStatus(status, redirectUri);
    }

    @Override
    public void onSaveAcceptUnvalidated(boolean accept) {
        mCallback.onSaveAcceptUnvalidated(accept);
    }

    @Override
    public void onStartSocketKeepalive(int slot, @NonNull Duration interval,
            @NonNull KeepalivePacketData packet) {
        mCallback.onStartSocketKeepalive(slot, interval, packet);
    }

    @Override
    public void onStopSocketKeepalive(int slot) {
        mCallback.onStopSocketKeepalive(slot);
    }

    @Override
    public void onAddKeepalivePacketFilter(int slot, @NonNull KeepalivePacketData packet) {
        mCallback.onAddKeepalivePacketFilter(slot, packet);
    }

    @Override
    public void onRemoveKeepalivePacketFilter(int slot) {
        mCallback.onRemoveKeepalivePacketFilter(slot);
    }

    @Override
    public void onSignalStrengthThresholdsUpdated(@NonNull int[] thresholds) {
        mCallback.onSignalStrengthThresholdsUpdated(thresholds);
    }

    @Override
    public void onAutomaticReconnectDisabled() {
        mCallback.onAutomaticReconnectDisabled();
    }

    @Override
    public void onDscpPolicyStatusUpdated(int policyId, int status) {
        mCallback.onDscpPolicyStatusUpdated(policyId, status);
    }

    @NonNull
    public Callback getCallback() {
        return mCallback;
    }

    /** sendNetworkCapabilities is final in NetworkAgent, so can't override that method directly */
    public void sendNetworkCapabilitiesAndCache(@NonNull NetworkCapabilities nc) {
        mCurrentNetworkCapabilities = nc;
        super.sendNetworkCapabilities(nc);
    }

    @NonNull
    public NetworkCapabilities getCurrentNetworkCapabilities() {
        return mCurrentNetworkCapabilities;
    }
}
