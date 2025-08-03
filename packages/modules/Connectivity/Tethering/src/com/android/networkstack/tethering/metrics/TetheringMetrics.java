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

package com.android.networkstack.tethering.metrics;

import static android.net.NetworkCapabilities.TRANSPORT_BLUETOOTH;
import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_ETHERNET;
import static android.net.NetworkCapabilities.TRANSPORT_LOWPAN;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI_AWARE;
import static android.net.TetheringManager.TETHERING_BLUETOOTH;
import static android.net.TetheringManager.TETHERING_ETHERNET;
import static android.net.TetheringManager.TETHERING_NCM;
import static android.net.TetheringManager.TETHERING_USB;
import static android.net.TetheringManager.TETHERING_WIFI;
import static android.net.TetheringManager.TETHERING_WIFI_P2P;
import static android.net.TetheringManager.TETHER_ERROR_DHCPSERVER_ERROR;
import static android.net.TetheringManager.TETHER_ERROR_DISABLE_FORWARDING_ERROR;
import static android.net.TetheringManager.TETHER_ERROR_ENABLE_FORWARDING_ERROR;
import static android.net.TetheringManager.TETHER_ERROR_ENTITLEMENT_UNKNOWN;
import static android.net.TetheringManager.TETHER_ERROR_IFACE_CFG_ERROR;
import static android.net.TetheringManager.TETHER_ERROR_INTERNAL_ERROR;
import static android.net.TetheringManager.TETHER_ERROR_NO_ACCESS_TETHERING_PERMISSION;
import static android.net.TetheringManager.TETHER_ERROR_NO_CHANGE_TETHERING_PERMISSION;
import static android.net.TetheringManager.TETHER_ERROR_NO_ERROR;
import static android.net.TetheringManager.TETHER_ERROR_PROVISIONING_FAILED;
import static android.net.TetheringManager.TETHER_ERROR_SERVICE_UNAVAIL;
import static android.net.TetheringManager.TETHER_ERROR_TETHER_IFACE_ERROR;
import static android.net.TetheringManager.TETHER_ERROR_UNAVAIL_IFACE;
import static android.net.TetheringManager.TETHER_ERROR_UNKNOWN_IFACE;
import static android.net.TetheringManager.TETHER_ERROR_UNSUPPORTED;
import static android.net.TetheringManager.TETHER_ERROR_UNTETHER_IFACE_ERROR;

import android.annotation.Nullable;
import android.net.NetworkCapabilities;
import android.stats.connectivity.DownstreamType;
import android.stats.connectivity.ErrorCode;
import android.stats.connectivity.UpstreamType;
import android.stats.connectivity.UserType;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.networkstack.tethering.UpstreamNetworkState;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Collection of utilities for tethering metrics.
 *
 * To see if the logs are properly sent to statsd, execute following commands
 *
 * $ adb shell cmd stats print-logs
 * $ adb logcat | grep statsd OR $ adb logcat -b stats
 *
 * @hide
 */
public class TetheringMetrics {
    private static final String TAG = TetheringMetrics.class.getSimpleName();
    private static final boolean DBG = false;
    private static final String SETTINGS_PKG_NAME = "com.android.settings";
    private static final String SYSTEMUI_PKG_NAME = "com.android.systemui";
    private static final String GMS_PKG_NAME = "com.google.android.gms";
    private final SparseArray<NetworkTetheringReported.Builder> mBuilderMap = new SparseArray<>();
    private final SparseArray<Long> mDownstreamStartTime = new SparseArray<Long>();
    private final ArrayList<RecordUpstreamEvent> mUpstreamEventList = new ArrayList<>();
    private UpstreamType mCurrentUpstream = null;
    private Long mCurrentUpStreamStartTime = 0L;


    /**
     * Return the current system time in milliseconds.
     * @return the current system time in milliseconds.
     */
    public long timeNow() {
        return System.currentTimeMillis();
    }

    private static class RecordUpstreamEvent {
        public final long mStartTime;
        public final long mStopTime;
        public final UpstreamType mUpstreamType;

        RecordUpstreamEvent(final long startTime, final long stopTime,
                final UpstreamType upstream) {
            mStartTime = startTime;
            mStopTime = stopTime;
            mUpstreamType = upstream;
        }
    }

    /**
     * Creates a |NetworkTetheringReported.Builder| object to update the tethering stats for the
     * specified downstream type and caller's package name. Initializes the upstream events, error
     * code, and duration to default values. Sets the start time for the downstream type in the
     * |mDownstreamStartTime| map.
     * @param downstreamType The type of downstream connection (e.g. Wifi, USB, Bluetooth).
     * @param callerPkg The package name of the caller.
     */
    public void createBuilder(final int downstreamType, final String callerPkg) {
        NetworkTetheringReported.Builder statsBuilder = NetworkTetheringReported.newBuilder()
                .setDownstreamType(downstreamTypeToEnum(downstreamType))
                .setUserType(userTypeToEnum(callerPkg))
                .setUpstreamType(UpstreamType.UT_UNKNOWN)
                .setErrorCode(ErrorCode.EC_NO_ERROR)
                .setUpstreamEvents(UpstreamEvents.newBuilder())
                .setDurationMillis(0);
        mBuilderMap.put(downstreamType, statsBuilder);
        mDownstreamStartTime.put(downstreamType, timeNow());
    }

    /**
     * Update the error code of the given downstream type in the Tethering stats.
     * @param downstreamType The downstream type whose error code to update.
     * @param errCode The error code to set.
     */
    public void updateErrorCode(final int downstreamType, final int errCode) {
        NetworkTetheringReported.Builder statsBuilder = mBuilderMap.get(downstreamType);
        if (statsBuilder == null) {
            Log.e(TAG, "Given downstreamType does not exist, this is a bug!");
            return;
        }
        statsBuilder.setErrorCode(errorCodeToEnum(errCode));
    }

    /**
     * Update the list of upstream types and their duration whenever the current upstream type
     * changes.
     * @param ns The UpstreamNetworkState object representing the current upstream network state.
     */
    public void maybeUpdateUpstreamType(@Nullable final UpstreamNetworkState ns) {
        UpstreamType upstream = transportTypeToUpstreamTypeEnum(ns);
        if (upstream.equals(mCurrentUpstream)) return;

        final long newTime = timeNow();
        if (mCurrentUpstream != null) {
            mUpstreamEventList.add(new RecordUpstreamEvent(mCurrentUpStreamStartTime, newTime,
                    mCurrentUpstream));
        }
        mCurrentUpstream = upstream;
        mCurrentUpStreamStartTime = newTime;
    }

    /**
     * Updates the upstream events builder with a new upstream event.
     * @param upstreamEventsBuilder the builder for the upstream events list
     * @param start the start time of the upstream event
     * @param stop the stop time of the upstream event
     * @param upstream the type of upstream type (e.g. Wifi, Cellular, Bluetooth, ...)
     */
    private void addUpstreamEvent(final UpstreamEvents.Builder upstreamEventsBuilder,
            final long start, final long stop, @Nullable final UpstreamType upstream,
            final long txBytes, final long rxBytes) {
        final UpstreamEvent.Builder upstreamEventBuilder = UpstreamEvent.newBuilder()
                .setUpstreamType(upstream == null ? UpstreamType.UT_NO_NETWORK : upstream)
                .setDurationMillis(stop - start)
                .setTxBytes(txBytes)
                .setRxBytes(rxBytes);
        upstreamEventsBuilder.addUpstreamEvent(upstreamEventBuilder);
    }

    /**
     * Updates the |NetworkTetheringReported.Builder| with relevant upstream events associated with
     * the downstream event identified by the given downstream start time.
     *
     * This method iterates through the list of upstream events and adds any relevant events to a
     * |UpstreamEvents.Builder|. Upstream events are considered relevant if their stop time is
     * greater than or equal to the given downstream start time. The method also adds the last
     * upstream event that occurred up until the current time.
     *
     * The resulting |UpstreamEvents.Builder| is then added to the
     * |NetworkTetheringReported.Builder|, along with the duration of the downstream event
     * (i.e., stop time minus downstream start time).
     *
     * @param statsBuilder the builder for the NetworkTetheringReported message
     * @param downstreamStartTime the start time of the downstream event to find relevant upstream
     * events for
     */
    private void noteDownstreamStopped(final NetworkTetheringReported.Builder statsBuilder,
                    final long downstreamStartTime) {
        UpstreamEvents.Builder upstreamEventsBuilder = UpstreamEvents.newBuilder();

        for (RecordUpstreamEvent event : mUpstreamEventList) {
            if (downstreamStartTime > event.mStopTime) continue;

            final long startTime = Math.max(downstreamStartTime, event.mStartTime);
            // Handle completed upstream events.
            addUpstreamEvent(upstreamEventsBuilder, startTime, event.mStopTime,
                    event.mUpstreamType, 0L /* txBytes */, 0L /* rxBytes */);
        }
        final long startTime = Math.max(downstreamStartTime, mCurrentUpStreamStartTime);
        final long stopTime = timeNow();
        // Handle the last upstream event.
        addUpstreamEvent(upstreamEventsBuilder, startTime, stopTime, mCurrentUpstream,
                0L /* txBytes */, 0L /* rxBytes */);
        statsBuilder.setUpstreamEvents(upstreamEventsBuilder);
        statsBuilder.setDurationMillis(stopTime - downstreamStartTime);
    }

    /**
     * Removes tethering statistics for the given downstream type. If there are any stats to write
     * for the downstream event associated with the type, they are written before removing the
     * statistics.
     *
     * If the given downstream type does not exist in the map, an error message is logged and the
     * method returns without doing anything.
     *
     * @param downstreamType the type of downstream event to remove statistics for
     */
    public void sendReport(final int downstreamType) {
        final NetworkTetheringReported.Builder statsBuilder = mBuilderMap.get(downstreamType);
        if (statsBuilder == null) {
            Log.e(TAG, "Given downstreamType does not exist, this is a bug!");
            return;
        }

        noteDownstreamStopped(statsBuilder, mDownstreamStartTime.get(downstreamType));
        write(statsBuilder.build());

        mBuilderMap.remove(downstreamType);
        mDownstreamStartTime.remove(downstreamType);
    }

    /**
     * Collects tethering statistics and writes them to the statsd pipeline. This method takes in a
     * NetworkTetheringReported object, extracts its fields and uses them to write statistics data
     * to the statsd pipeline.
     *
     * @param reported a NetworkTetheringReported object containing statistics to write
     */
    @VisibleForTesting
    public void write(@NonNull final NetworkTetheringReported reported) {
        final byte[] upstreamEvents = reported.getUpstreamEvents().toByteArray();

        TetheringStatsLog.write(
                TetheringStatsLog.NETWORK_TETHERING_REPORTED,
                reported.getErrorCode().getNumber(),
                reported.getDownstreamType().getNumber(),
                reported.getUpstreamType().getNumber(),
                reported.getUserType().getNumber(),
                upstreamEvents,
                reported.getDurationMillis());
        if (DBG) {
            Log.d(
                    TAG,
                    "Write errorCode: "
                    + reported.getErrorCode().getNumber()
                    + ", downstreamType: "
                    + reported.getDownstreamType().getNumber()
                    + ", upstreamType: "
                    + reported.getUpstreamType().getNumber()
                    + ", userType: "
                    + reported.getUserType().getNumber()
                    + ", upstreamTypes: "
                    + Arrays.toString(upstreamEvents)
                    + ", durationMillis: "
                    + reported.getDurationMillis());
        }
    }

    /**
     * Cleans up the variables related to upstream events when tethering is turned off.
     */
    public void cleanup() {
        mUpstreamEventList.clear();
        mCurrentUpstream = null;
        mCurrentUpStreamStartTime = 0L;
    }

    private DownstreamType downstreamTypeToEnum(final int ifaceType) {
        switch(ifaceType) {
            case TETHERING_WIFI:
                return DownstreamType.DS_TETHERING_WIFI;
            case TETHERING_WIFI_P2P:
                return DownstreamType.DS_TETHERING_WIFI_P2P;
            case TETHERING_USB:
                return DownstreamType.DS_TETHERING_USB;
            case TETHERING_BLUETOOTH:
                return DownstreamType.DS_TETHERING_BLUETOOTH;
            case TETHERING_NCM:
                return DownstreamType.DS_TETHERING_NCM;
            case TETHERING_ETHERNET:
                return DownstreamType.DS_TETHERING_ETHERNET;
            default:
                return DownstreamType.DS_UNSPECIFIED;
        }
    }

    private ErrorCode errorCodeToEnum(final int lastError) {
        switch(lastError) {
            case TETHER_ERROR_NO_ERROR:
                return ErrorCode.EC_NO_ERROR;
            case TETHER_ERROR_UNKNOWN_IFACE:
                return ErrorCode.EC_UNKNOWN_IFACE;
            case TETHER_ERROR_SERVICE_UNAVAIL:
                return ErrorCode.EC_SERVICE_UNAVAIL;
            case TETHER_ERROR_UNSUPPORTED:
                return ErrorCode.EC_UNSUPPORTED;
            case TETHER_ERROR_UNAVAIL_IFACE:
                return ErrorCode.EC_UNAVAIL_IFACE;
            case TETHER_ERROR_INTERNAL_ERROR:
                return ErrorCode.EC_INTERNAL_ERROR;
            case TETHER_ERROR_TETHER_IFACE_ERROR:
                return ErrorCode.EC_TETHER_IFACE_ERROR;
            case TETHER_ERROR_UNTETHER_IFACE_ERROR:
                return ErrorCode.EC_UNTETHER_IFACE_ERROR;
            case TETHER_ERROR_ENABLE_FORWARDING_ERROR:
                return ErrorCode.EC_ENABLE_FORWARDING_ERROR;
            case TETHER_ERROR_DISABLE_FORWARDING_ERROR:
                return ErrorCode.EC_DISABLE_FORWARDING_ERROR;
            case TETHER_ERROR_IFACE_CFG_ERROR:
                return ErrorCode.EC_IFACE_CFG_ERROR;
            case TETHER_ERROR_PROVISIONING_FAILED:
                return ErrorCode.EC_PROVISIONING_FAILED;
            case TETHER_ERROR_DHCPSERVER_ERROR:
                return ErrorCode.EC_DHCPSERVER_ERROR;
            case TETHER_ERROR_ENTITLEMENT_UNKNOWN:
                return ErrorCode.EC_ENTITLEMENT_UNKNOWN;
            case TETHER_ERROR_NO_CHANGE_TETHERING_PERMISSION:
                return ErrorCode.EC_NO_CHANGE_TETHERING_PERMISSION;
            case TETHER_ERROR_NO_ACCESS_TETHERING_PERMISSION:
                return ErrorCode.EC_NO_ACCESS_TETHERING_PERMISSION;
            default:
                return ErrorCode.EC_UNKNOWN_TYPE;
        }
    }

    private UserType userTypeToEnum(final String callerPkg) {
        if (callerPkg.equals(SETTINGS_PKG_NAME)) {
            return UserType.USER_SETTINGS;
        } else if (callerPkg.equals(SYSTEMUI_PKG_NAME)) {
            return UserType.USER_SYSTEMUI;
        } else if (callerPkg.equals(GMS_PKG_NAME)) {
            return UserType.USER_GMS;
        } else {
            return UserType.USER_UNKNOWN;
        }
    }

    private UpstreamType transportTypeToUpstreamTypeEnum(final UpstreamNetworkState ns) {
        final NetworkCapabilities nc = (ns != null) ? ns.networkCapabilities : null;
        if (nc == null) return UpstreamType.UT_NO_NETWORK;

        final int typeCount = nc.getTransportTypes().length;
        // It's possible for a VCN network to be mapped to UT_UNKNOWN, as it may consist of both
        // Wi-Fi and cellular transport.
        // TODO: It's necessary to define a new upstream type for VCN, which can be identified by
        // NET_CAPABILITY_NOT_VCN_MANAGED.
        if (typeCount > 1) return UpstreamType.UT_UNKNOWN;

        if (nc.hasTransport(TRANSPORT_CELLULAR)) return UpstreamType.UT_CELLULAR;
        if (nc.hasTransport(TRANSPORT_WIFI)) return UpstreamType.UT_WIFI;
        if (nc.hasTransport(TRANSPORT_BLUETOOTH)) return UpstreamType.UT_BLUETOOTH;
        if (nc.hasTransport(TRANSPORT_ETHERNET)) return UpstreamType.UT_ETHERNET;
        if (nc.hasTransport(TRANSPORT_WIFI_AWARE)) return UpstreamType.UT_WIFI_AWARE;
        if (nc.hasTransport(TRANSPORT_LOWPAN)) return UpstreamType.UT_LOWPAN;

        return UpstreamType.UT_UNKNOWN;
    }
}
