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

package com.android.server.connectivity;

import static android.telephony.SubscriptionManager.OnSubscriptionsChangedListener;

import android.annotation.NonNull;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkSpecifier;
import android.net.TelephonyNetworkSpecifier;
import android.net.TransportInfo;
import android.net.wifi.WifiInfo;
import android.os.Handler;
import android.os.SystemClock;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.IndentingPrintWriter;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.metrics.DailykeepaliveInfoReported;
import com.android.metrics.DurationForNumOfKeepalive;
import com.android.metrics.DurationPerNumOfKeepalive;
import com.android.metrics.KeepaliveLifetimeForCarrier;
import com.android.metrics.KeepaliveLifetimePerCarrier;
import com.android.modules.utils.BackgroundThread;
import com.android.modules.utils.build.SdkLevel;
import com.android.net.module.util.CollectionUtils;
import com.android.server.ConnectivityStatsLog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tracks carrier and duration metrics of automatic on/off keepalives.
 *
 * <p>This class follows AutomaticOnOffKeepaliveTracker closely and its on*Keepalive methods needs
 * to be called in a timely manner to keep the metrics accurate. It is also not thread-safe and all
 * public methods must be called by the same thread, namely the ConnectivityService handler thread.
 *
 * <p>In the case that the keepalive state becomes out of sync with the hardware, the tracker will
 * be disabled. e.g. Calling onStartKeepalive on a given network, slot pair twice without calling
 * onStopKeepalive is unexpected and will disable the tracker.
 */
public class KeepaliveStatsTracker {
    private static final String TAG = KeepaliveStatsTracker.class.getSimpleName();
    private static final int INVALID_KEEPALIVE_ID = -1;
    // 2 hour acceptable deviation in metrics collection duration time to account for the 1 hour
    // window of AlarmManager.
    private static final long MAX_EXPECTED_DURATION_MS =
            AutomaticOnOffKeepaliveTracker.METRICS_COLLECTION_DURATION_MS + 2 * 60 * 60 * 1_000L;

    @NonNull private final Handler mConnectivityServiceHandler;
    @NonNull private final Dependencies mDependencies;

    // Mapping of subId to carrierId. Updates are received from OnSubscriptionsChangedListener
    private final SparseIntArray mCachedCarrierIdPerSubId = new SparseIntArray();
    // The default subscription id obtained from SubscriptionManager.getDefaultSubscriptionId.
    // Updates are received from the ACTION_DEFAULT_SUBSCRIPTION_CHANGED broadcast.
    private int mCachedDefaultSubscriptionId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;

    // Boolean to track whether the KeepaliveStatsTracker is enabled.
    // Use a final AtomicBoolean to ensure initialization is seen on the handler thread.
    // Repeated fields in metrics are only supported on T+ so this is enabled only on T+.
    private final AtomicBoolean mEnabled = new AtomicBoolean(SdkLevel.isAtLeastT());

    // Class to store network information, lifetime durations and active state of a keepalive.
    private static final class KeepaliveStats {
        // The carrier ID for a keepalive, or TelephonyManager.UNKNOWN_CARRIER_ID(-1) if not set.
        public final int carrierId;
        // The transport types of the underlying network for each keepalive. A network may include
        // multiple transport types. Each transport type is represented by a different bit, defined
        // in NetworkCapabilities
        public final int transportTypes;
        // The keepalive interval in millis.
        public final int intervalMs;
        // The uid of the app that requested the keepalive.
        public final int appUid;
        // Indicates if the keepalive is an automatic keepalive.
        public final boolean isAutoKeepalive;

        // Snapshot of the lifetime stats
        public static class LifetimeStats {
            public final int lifetimeMs;
            public final int activeLifetimeMs;

            LifetimeStats(int lifetimeMs, int activeLifetimeMs) {
                this.lifetimeMs = lifetimeMs;
                this.activeLifetimeMs = activeLifetimeMs;
            }
        }

        // The total time since the keepalive is started until it is stopped.
        private int mLifetimeMs = 0;
        // The total time the keepalive is active (not suspended).
        private int mActiveLifetimeMs = 0;

        // A timestamp of the most recent time the lifetime metrics was updated.
        private long mLastUpdateLifetimeTimestamp;

        // A flag to indicate if the keepalive is active.
        private boolean mKeepaliveActive = true;

        /**
         * Gets the lifetime stats for the keepalive, updated to timeNow, and then resets it.
         *
         * @param timeNow a timestamp obtained using Dependencies.getElapsedRealtime
         */
        public LifetimeStats getAndResetLifetimeStats(long timeNow) {
            updateLifetimeStatsAndSetActive(timeNow, mKeepaliveActive);
            // Get a snapshot of the stats
            final LifetimeStats lifetimeStats = new LifetimeStats(mLifetimeMs, mActiveLifetimeMs);
            // Reset the stats
            resetLifetimeStats(timeNow);

            return lifetimeStats;
        }

        public boolean isKeepaliveActive() {
            return mKeepaliveActive;
        }

        KeepaliveStats(
                int carrierId,
                int transportTypes,
                int intervalSeconds,
                int appUid,
                boolean isAutoKeepalive,
                long timeNow) {
            this.carrierId = carrierId;
            this.transportTypes = transportTypes;
            this.intervalMs = intervalSeconds * 1000;
            this.appUid = appUid;
            this.isAutoKeepalive = isAutoKeepalive;
            mLastUpdateLifetimeTimestamp = timeNow;
        }

        /**
         * Updates the lifetime metrics to the given time and sets the active state. This should be
         * called whenever the active state of the keepalive changes.
         *
         * @param timeNow a timestamp obtained using Dependencies.getElapsedRealtime
         */
        public void updateLifetimeStatsAndSetActive(long timeNow, boolean keepaliveActive) {
            final int durationIncrease = (int) (timeNow - mLastUpdateLifetimeTimestamp);
            mLifetimeMs += durationIncrease;
            if (mKeepaliveActive) mActiveLifetimeMs += durationIncrease;

            mLastUpdateLifetimeTimestamp = timeNow;
            mKeepaliveActive = keepaliveActive;
        }

        /**
         * Resets the lifetime metrics but does not reset the active/stopped state of the keepalive.
         * This also updates the time to timeNow, ensuring stats will start from this time.
         *
         * @param timeNow a timestamp obtained using Dependencies.getElapsedRealtime
         */
        public void resetLifetimeStats(long timeNow) {
            mLifetimeMs = 0;
            mActiveLifetimeMs = 0;
            mLastUpdateLifetimeTimestamp = timeNow;
        }
    }

    // List of duration stats metric where the index is the number of concurrent keepalives.
    // Each DurationForNumOfKeepalive message stores a registered duration and an active duration.
    // Registered duration is the total time spent with mNumRegisteredKeepalive == index.
    // Active duration is the total time spent with mNumActiveKeepalive == index.
    private final List<DurationForNumOfKeepalive.Builder> mDurationPerNumOfKeepalive =
            new ArrayList<>();

    // Map of keepalives identified by the id from getKeepaliveId to their stats information.
    private final SparseArray<KeepaliveStats> mKeepaliveStatsPerId = new SparseArray<>();

    // Generate and return a unique integer using a given network's netId and the slot number.
    // This is possible because netId is a 16 bit integer, so an integer with the first 16 bits as
    // the netId and the last 16 bits as the slot number can be created. This allows slot numbers to
    // be up to 2^16.
    // Returns INVALID_KEEPALIVE_ID if the netId or slot is not as expected above.
    private int getKeepaliveId(@NonNull Network network, int slot) {
        final int netId = network.getNetId();
        // Since there is no enforcement that a Network's netId is valid check for it here.
        if (netId < 0 || netId >= (1 << 16)) {
            disableTracker("Unexpected netId value: " + netId);
            return INVALID_KEEPALIVE_ID;
        }
        if (slot < 0 || slot >= (1 << 16)) {
            disableTracker("Unexpected slot value: " + slot);
            return INVALID_KEEPALIVE_ID;
        }

        return (netId << 16) + slot;
    }

    // Class to act as the key to aggregate the KeepaliveLifetimeForCarrier stats.
    private static final class LifetimeKey {
        public final int carrierId;
        public final int transportTypes;
        public final int intervalMs;

        LifetimeKey(int carrierId, int transportTypes, int intervalMs) {
            this.carrierId = carrierId;
            this.transportTypes = transportTypes;
            this.intervalMs = intervalMs;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            final LifetimeKey that = (LifetimeKey) o;

            return carrierId == that.carrierId && transportTypes == that.transportTypes
                    && intervalMs == that.intervalMs;
        }

        @Override
        public int hashCode() {
            return carrierId + 3 * transportTypes + 5 * intervalMs;
        }
    }

    // Map to aggregate the KeepaliveLifetimeForCarrier stats using LifetimeKey as the key.
    final Map<LifetimeKey, KeepaliveLifetimeForCarrier.Builder> mAggregateKeepaliveLifetime =
            new HashMap<>();

    private final Set<Integer> mAppUids = new HashSet<Integer>();
    private int mNumKeepaliveRequests = 0;
    private int mNumAutomaticKeepaliveRequests = 0;

    private int mNumRegisteredKeepalive = 0;
    private int mNumActiveKeepalive = 0;

    // A timestamp of the most recent time the duration metrics was updated.
    private long mLastUpdateDurationsTimestamp;

    /** Dependency class */
    @VisibleForTesting
    public static class Dependencies {
        // Returns a timestamp with the time base of SystemClock.elapsedRealtime to keep durations
        // relative to start time and avoid timezone change, including time spent in deep sleep.
        public long getElapsedRealtime() {
            return SystemClock.elapsedRealtime();
        }

        /**
         * Writes a DAILY_KEEPALIVE_INFO_REPORTED to ConnectivityStatsLog.
         *
         * @param dailyKeepaliveInfoReported the proto to write to statsD.
         */
        public void writeStats(DailykeepaliveInfoReported dailyKeepaliveInfoReported) {
            ConnectivityStatsLog.write(
                    ConnectivityStatsLog.DAILY_KEEPALIVE_INFO_REPORTED,
                    dailyKeepaliveInfoReported.getDurationPerNumOfKeepalive().toByteArray(),
                    dailyKeepaliveInfoReported.getKeepaliveLifetimePerCarrier().toByteArray(),
                    dailyKeepaliveInfoReported.getKeepaliveRequests(),
                    dailyKeepaliveInfoReported.getAutomaticKeepaliveRequests(),
                    dailyKeepaliveInfoReported.getDistinctUserCount(),
                    CollectionUtils.toIntArray(dailyKeepaliveInfoReported.getUidList()));
        }
    }

    public KeepaliveStatsTracker(@NonNull Context context, @NonNull Handler handler) {
        this(context, handler, new Dependencies());
    }

    private final Context mContext;
    private final SubscriptionManager mSubscriptionManager;

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mCachedDefaultSubscriptionId =
                    intent.getIntExtra(
                            SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
                            SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        }
    };

    private final CompletableFuture<OnSubscriptionsChangedListener> mListenerFuture =
            new CompletableFuture<>();

    @VisibleForTesting
    public KeepaliveStatsTracker(
            @NonNull Context context,
            @NonNull Handler handler,
            @NonNull Dependencies dependencies) {
        mContext = Objects.requireNonNull(context);
        mDependencies = Objects.requireNonNull(dependencies);
        mConnectivityServiceHandler = Objects.requireNonNull(handler);

        mSubscriptionManager =
                Objects.requireNonNull(context.getSystemService(SubscriptionManager.class));

        mLastUpdateDurationsTimestamp = mDependencies.getElapsedRealtime();

        if (!isEnabled()) {
            return;
        }

        context.registerReceiver(
                mBroadcastReceiver,
                new IntentFilter(SubscriptionManager.ACTION_DEFAULT_SUBSCRIPTION_CHANGED),
                /* broadcastPermission= */ null,
                mConnectivityServiceHandler);

        // The default constructor for OnSubscriptionsChangedListener will always implicitly grab
        // the looper of the current thread. In the case the current thread does not have a looper,
        // this will throw. Therefore, post a runnable that creates it there.
        // When the callback is called on the BackgroundThread, post a message on the CS handler
        // thread to update the caches, which can only be touched there.
        BackgroundThread.getHandler().post(() -> {
            final OnSubscriptionsChangedListener listener =
                    new OnSubscriptionsChangedListener() {
                        @Override
                        public void onSubscriptionsChanged() {
                            final List<SubscriptionInfo> activeSubInfoList =
                                    mSubscriptionManager.getActiveSubscriptionInfoList();
                            // A null subInfo list here indicates the current state is unknown
                            // but not necessarily empty, simply ignore it. Another call to the
                            // listener will be invoked in the future.
                            if (activeSubInfoList == null) return;
                            mConnectivityServiceHandler.post(() -> {
                                mCachedCarrierIdPerSubId.clear();

                                for (final SubscriptionInfo subInfo : activeSubInfoList) {
                                    mCachedCarrierIdPerSubId.put(subInfo.getSubscriptionId(),
                                            subInfo.getCarrierId());
                                }
                            });
                        }
                    };
            mListenerFuture.complete(listener);
            mSubscriptionManager.addOnSubscriptionsChangedListener(r -> r.run(), listener);
        });
    }

    /** Ensures the list of duration metrics is large enough for number of registered keepalives. */
    private void ensureDurationPerNumOfKeepaliveSize() {
        if (mNumActiveKeepalive < 0 || mNumRegisteredKeepalive < 0) {
            disableTracker("Number of active or registered keepalives is negative");
            return;
        }
        if (mNumActiveKeepalive > mNumRegisteredKeepalive) {
            disableTracker("Number of active keepalives greater than registered keepalives");
            return;
        }

        while (mDurationPerNumOfKeepalive.size() <= mNumRegisteredKeepalive) {
            final DurationForNumOfKeepalive.Builder durationForNumOfKeepalive =
                    DurationForNumOfKeepalive.newBuilder();
            durationForNumOfKeepalive.setNumOfKeepalive(mDurationPerNumOfKeepalive.size());
            durationForNumOfKeepalive.setKeepaliveRegisteredDurationsMsec(0);
            durationForNumOfKeepalive.setKeepaliveActiveDurationsMsec(0);

            mDurationPerNumOfKeepalive.add(durationForNumOfKeepalive);
        }
    }

    /**
     * Updates the durations metrics to the given time. This should always be called before making a
     * change to mNumRegisteredKeepalive or mNumActiveKeepalive to keep the duration metrics
     * correct.
     *
     * @param timeNow a timestamp obtained using Dependencies.getElapsedRealtime
     */
    private void updateDurationsPerNumOfKeepalive(long timeNow) {
        if (mDurationPerNumOfKeepalive.size() < mNumRegisteredKeepalive) {
            Log.e(TAG, "Unexpected jump in number of registered keepalive");
        }
        ensureDurationPerNumOfKeepaliveSize();

        final int durationIncrease = (int) (timeNow - mLastUpdateDurationsTimestamp);
        final DurationForNumOfKeepalive.Builder durationForNumOfRegisteredKeepalive =
                mDurationPerNumOfKeepalive.get(mNumRegisteredKeepalive);

        durationForNumOfRegisteredKeepalive.setKeepaliveRegisteredDurationsMsec(
                durationForNumOfRegisteredKeepalive.getKeepaliveRegisteredDurationsMsec()
                        + durationIncrease);

        final DurationForNumOfKeepalive.Builder durationForNumOfActiveKeepalive =
                mDurationPerNumOfKeepalive.get(mNumActiveKeepalive);

        durationForNumOfActiveKeepalive.setKeepaliveActiveDurationsMsec(
                durationForNumOfActiveKeepalive.getKeepaliveActiveDurationsMsec()
                        + durationIncrease);

        mLastUpdateDurationsTimestamp = timeNow;
    }

    // TODO: Move this function to frameworks/libs/net/.../NetworkCapabilitiesUtils.java
    private static int getSubId(@NonNull NetworkCapabilities nc, int defaultSubId) {
        if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            final NetworkSpecifier networkSpecifier = nc.getNetworkSpecifier();
            if (networkSpecifier instanceof TelephonyNetworkSpecifier) {
                return ((TelephonyNetworkSpecifier) networkSpecifier).getSubscriptionId();
            }
            // Use the default subscriptionId.
            return defaultSubId;
        }
        if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            final TransportInfo info = nc.getTransportInfo();
            if (info instanceof WifiInfo) {
                return ((WifiInfo) info).getSubscriptionId();
            }
        }

        return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }

    private int getCarrierId(@NonNull NetworkCapabilities networkCapabilities) {
        // Try to get the correct subscription id.
        final int subId = getSubId(networkCapabilities, mCachedDefaultSubscriptionId);
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return TelephonyManager.UNKNOWN_CARRIER_ID;
        }
        return mCachedCarrierIdPerSubId.get(subId, TelephonyManager.UNKNOWN_CARRIER_ID);
    }

    private int getTransportTypes(@NonNull NetworkCapabilities networkCapabilities) {
        // Transport types are internally packed as bits starting from bit 0. Casting to int works
        // fine since for now and the foreseeable future, there will be less than 32 transports.
        return (int) networkCapabilities.getTransportTypesInternal();
    }

    /** Inform the KeepaliveStatsTracker a keepalive has just started and is active. */
    public void onStartKeepalive(
            @NonNull Network network,
            int slot,
            @NonNull NetworkCapabilities nc,
            int intervalSeconds,
            int appUid,
            boolean isAutoKeepalive) {
        ensureRunningOnHandlerThread();
        if (!isEnabled()) return;
        final int keepaliveId = getKeepaliveId(network, slot);
        if (keepaliveId == INVALID_KEEPALIVE_ID) return;
        if (mKeepaliveStatsPerId.contains(keepaliveId)) {
            disableTracker("Attempt to start keepalive stats on a known network, slot pair");
            return;
        }

        mNumKeepaliveRequests++;
        if (isAutoKeepalive) mNumAutomaticKeepaliveRequests++;
        mAppUids.add(appUid);

        final long timeNow = mDependencies.getElapsedRealtime();
        updateDurationsPerNumOfKeepalive(timeNow);

        mNumRegisteredKeepalive++;
        mNumActiveKeepalive++;

        final KeepaliveStats newKeepaliveStats =
                new KeepaliveStats(
                        getCarrierId(nc),
                        getTransportTypes(nc),
                        intervalSeconds,
                        appUid,
                        isAutoKeepalive,
                        timeNow);

        mKeepaliveStatsPerId.put(keepaliveId, newKeepaliveStats);
    }

    /**
     * Inform the KeepaliveStatsTracker that the keepalive with the given network, slot pair has
     * updated its active state to keepaliveActive.
     */
    private void onKeepaliveActive(
            @NonNull Network network, int slot, boolean keepaliveActive) {
        final long timeNow = mDependencies.getElapsedRealtime();
        onKeepaliveActive(network, slot, keepaliveActive, timeNow);
    }

    /**
     * Inform the KeepaliveStatsTracker that the keepalive with the given network, slot pair has
     * updated its active state to keepaliveActive.
     *
     * @param network the network of the keepalive
     * @param slot the slot number of the keepalive
     * @param keepaliveActive the new active state of the keepalive
     * @param timeNow a timestamp obtained using Dependencies.getElapsedRealtime
     */
    private void onKeepaliveActive(
            @NonNull Network network, int slot, boolean keepaliveActive, long timeNow) {
        final int keepaliveId = getKeepaliveId(network, slot);
        if (keepaliveId == INVALID_KEEPALIVE_ID) return;

        final KeepaliveStats keepaliveStats = mKeepaliveStatsPerId.get(keepaliveId, null);

        if (keepaliveStats == null) {
            disableTracker("Attempt to set active keepalive on an unknown network, slot pair");
            return;
        }
        updateDurationsPerNumOfKeepalive(timeNow);

        if (keepaliveActive != keepaliveStats.isKeepaliveActive()) {
            mNumActiveKeepalive += keepaliveActive ? 1 : -1;
        }

        keepaliveStats.updateLifetimeStatsAndSetActive(timeNow, keepaliveActive);
    }

    /** Inform the KeepaliveStatsTracker a keepalive has just been paused. */
    public void onPauseKeepalive(@NonNull Network network, int slot) {
        ensureRunningOnHandlerThread();
        if (!isEnabled()) return;
        onKeepaliveActive(network, slot, /* keepaliveActive= */ false);
    }

    /** Inform the KeepaliveStatsTracker a keepalive has just been resumed. */
    public void onResumeKeepalive(@NonNull Network network, int slot) {
        ensureRunningOnHandlerThread();
        if (!isEnabled()) return;
        onKeepaliveActive(network, slot, /* keepaliveActive= */ true);
    }

    /** Inform the KeepaliveStatsTracker a keepalive has just been stopped. */
    public void onStopKeepalive(@NonNull Network network, int slot) {
        ensureRunningOnHandlerThread();
        if (!isEnabled()) return;

        final int keepaliveId = getKeepaliveId(network, slot);
        if (keepaliveId == INVALID_KEEPALIVE_ID) return;
        final long timeNow = mDependencies.getElapsedRealtime();

        onKeepaliveActive(network, slot, /* keepaliveActive= */ false, timeNow);
        final KeepaliveStats keepaliveStats = mKeepaliveStatsPerId.get(keepaliveId, null);
        if (keepaliveStats == null) return;

        mNumRegisteredKeepalive--;

        // add to the aggregate since it will be removed.
        addToAggregateKeepaliveLifetime(keepaliveStats, timeNow);
        // free up the slot.
        mKeepaliveStatsPerId.remove(keepaliveId);
    }

    /**
     * Updates and adds the lifetime metric of keepaliveStats to the aggregate.
     *
     * @param keepaliveStats the stats to add to the aggregate
     * @param timeNow a timestamp obtained using Dependencies.getElapsedRealtime
     */
    private void addToAggregateKeepaliveLifetime(
            @NonNull KeepaliveStats keepaliveStats, long timeNow) {

        final KeepaliveStats.LifetimeStats lifetimeStats =
                keepaliveStats.getAndResetLifetimeStats(timeNow);

        final LifetimeKey key =
                new LifetimeKey(
                        keepaliveStats.carrierId,
                        keepaliveStats.transportTypes,
                        keepaliveStats.intervalMs);

        KeepaliveLifetimeForCarrier.Builder keepaliveLifetimeForCarrier =
                mAggregateKeepaliveLifetime.get(key);

        if (keepaliveLifetimeForCarrier == null) {
            keepaliveLifetimeForCarrier =
                    KeepaliveLifetimeForCarrier.newBuilder()
                            .setCarrierId(keepaliveStats.carrierId)
                            .setTransportTypes(keepaliveStats.transportTypes)
                            .setIntervalsMsec(keepaliveStats.intervalMs);
            mAggregateKeepaliveLifetime.put(key, keepaliveLifetimeForCarrier);
        }

        keepaliveLifetimeForCarrier.setLifetimeMsec(
                keepaliveLifetimeForCarrier.getLifetimeMsec() + lifetimeStats.lifetimeMs);
        keepaliveLifetimeForCarrier.setActiveLifetimeMsec(
                keepaliveLifetimeForCarrier.getActiveLifetimeMsec()
                        + lifetimeStats.activeLifetimeMs);
    }

    /**
     * Builds and returns DailykeepaliveInfoReported proto.
     *
     * @return the DailykeepaliveInfoReported proto that was built.
     */
    @VisibleForTesting
    public @NonNull DailykeepaliveInfoReported buildKeepaliveMetrics() {
        ensureRunningOnHandlerThread();
        final long timeNow = mDependencies.getElapsedRealtime();
        return buildKeepaliveMetrics(timeNow);
    }

    /**
     * Updates the metrics to timeNow and builds and returns DailykeepaliveInfoReported proto.
     *
     * @param timeNow a timestamp obtained using Dependencies.getElapsedRealtime
     */
    private @NonNull DailykeepaliveInfoReported buildKeepaliveMetrics(long timeNow) {
        updateDurationsPerNumOfKeepalive(timeNow);

        final DurationPerNumOfKeepalive.Builder durationPerNumOfKeepalive =
                DurationPerNumOfKeepalive.newBuilder();

        mDurationPerNumOfKeepalive.forEach(
                durationForNumOfKeepalive ->
                        durationPerNumOfKeepalive.addDurationForNumOfKeepalive(
                                durationForNumOfKeepalive));

        final KeepaliveLifetimePerCarrier.Builder keepaliveLifetimePerCarrier =
                KeepaliveLifetimePerCarrier.newBuilder();

        for (int i = 0; i < mKeepaliveStatsPerId.size(); i++) {
            final KeepaliveStats keepaliveStats = mKeepaliveStatsPerId.valueAt(i);
            addToAggregateKeepaliveLifetime(keepaliveStats, timeNow);
        }

        // Fill keepalive carrier stats to the proto
        mAggregateKeepaliveLifetime
                .values()
                .forEach(
                        keepaliveLifetimeForCarrier ->
                                keepaliveLifetimePerCarrier.addKeepaliveLifetimeForCarrier(
                                        keepaliveLifetimeForCarrier));

        final DailykeepaliveInfoReported.Builder dailyKeepaliveInfoReported =
                DailykeepaliveInfoReported.newBuilder();

        dailyKeepaliveInfoReported.setDurationPerNumOfKeepalive(durationPerNumOfKeepalive);
        dailyKeepaliveInfoReported.setKeepaliveLifetimePerCarrier(keepaliveLifetimePerCarrier);
        dailyKeepaliveInfoReported.setKeepaliveRequests(mNumKeepaliveRequests);
        dailyKeepaliveInfoReported.setAutomaticKeepaliveRequests(mNumAutomaticKeepaliveRequests);
        dailyKeepaliveInfoReported.setDistinctUserCount(mAppUids.size());
        dailyKeepaliveInfoReported.addAllUid(mAppUids);

        return dailyKeepaliveInfoReported.build();
    }

    /**
     * Builds and resets the stored metrics. Similar to buildKeepaliveMetrics but also resets the
     * metrics while maintaining the state of the keepalives.
     *
     * @return the DailykeepaliveInfoReported proto that was built.
     */
    @VisibleForTesting
    public @NonNull DailykeepaliveInfoReported buildAndResetMetrics() {
        ensureRunningOnHandlerThread();
        final long timeNow = mDependencies.getElapsedRealtime();

        final DailykeepaliveInfoReported metrics = buildKeepaliveMetrics(timeNow);

        mDurationPerNumOfKeepalive.clear();
        mAggregateKeepaliveLifetime.clear();
        mAppUids.clear();
        mNumKeepaliveRequests = 0;
        mNumAutomaticKeepaliveRequests = 0;

        // Update the metrics with the existing keepalives.
        ensureDurationPerNumOfKeepaliveSize();

        mAggregateKeepaliveLifetime.clear();
        // Reset the stats for existing keepalives
        for (int i = 0; i < mKeepaliveStatsPerId.size(); i++) {
            final KeepaliveStats keepaliveStats = mKeepaliveStatsPerId.valueAt(i);
            keepaliveStats.resetLifetimeStats(timeNow);
            mAppUids.add(keepaliveStats.appUid);
            mNumKeepaliveRequests++;
            if (keepaliveStats.isAutoKeepalive) mNumAutomaticKeepaliveRequests++;
        }

        return metrics;
    }

    private void disableTracker(String msg) {
        if (!mEnabled.compareAndSet(/* expectedValue= */ true, /* newValue= */ false)) {
            // already disabled
            return;
        }
        Log.wtf(TAG, msg + ". Disabling KeepaliveStatsTracker");
        mContext.unregisterReceiver(mBroadcastReceiver);
        // The returned future is ignored since it is void and the is never completed exceptionally.
        final CompletableFuture<Void> unused = mListenerFuture.thenAcceptAsync(
                listener -> mSubscriptionManager.removeOnSubscriptionsChangedListener(listener),
                BackgroundThread.getExecutor());
    }

    /** Whether this tracker is enabled. This method is thread safe. */
    public boolean isEnabled() {
        return mEnabled.get();
    }

    /**
     * Checks the DailykeepaliveInfoReported for the following:
     * 1. total active durations/lifetimes <= total registered durations/lifetimes.
     * 2. Total time in Durations == total time in Carrier lifetime stats
     * 3. The total elapsed real time spent is within expectations.
     */
    @VisibleForTesting
    public boolean allMetricsExpected(DailykeepaliveInfoReported dailyKeepaliveInfoReported) {
        int totalRegistered = 0;
        int totalActiveDurations = 0;
        int totalTimeSpent = 0;
        for (DurationForNumOfKeepalive durationForNumOfKeepalive: dailyKeepaliveInfoReported
                .getDurationPerNumOfKeepalive().getDurationForNumOfKeepaliveList()) {
            final int n = durationForNumOfKeepalive.getNumOfKeepalive();
            totalRegistered += durationForNumOfKeepalive.getKeepaliveRegisteredDurationsMsec() * n;
            totalActiveDurations += durationForNumOfKeepalive.getKeepaliveActiveDurationsMsec() * n;
            totalTimeSpent += durationForNumOfKeepalive.getKeepaliveRegisteredDurationsMsec();
        }
        int totalLifetimes = 0;
        int totalActiveLifetimes = 0;
        for (KeepaliveLifetimeForCarrier keepaliveLifetimeForCarrier: dailyKeepaliveInfoReported
                .getKeepaliveLifetimePerCarrier().getKeepaliveLifetimeForCarrierList()) {
            totalLifetimes += keepaliveLifetimeForCarrier.getLifetimeMsec();
            totalActiveLifetimes += keepaliveLifetimeForCarrier.getActiveLifetimeMsec();
        }
        return totalActiveDurations <= totalRegistered && totalActiveLifetimes <= totalLifetimes
                && totalLifetimes == totalRegistered && totalActiveLifetimes == totalActiveDurations
                && totalTimeSpent <= MAX_EXPECTED_DURATION_MS;
    }

    /** Writes the stored metrics to ConnectivityStatsLog and resets. */
    public void writeAndResetMetrics() {
        ensureRunningOnHandlerThread();
        // Keepalive stats use repeated atoms, which are only supported on T+. If written to statsd
        // on S- they will bootloop the system, so they must not be sent on S-. See b/289471411.
        if (!SdkLevel.isAtLeastT()) {
            Log.d(TAG, "KeepaliveStatsTracker is disabled before T, skipping write");
            return;
        }
        if (!isEnabled()) {
            Log.d(TAG, "KeepaliveStatsTracker is disabled, skipping write");
            return;
        }

        final DailykeepaliveInfoReported dailyKeepaliveInfoReported = buildAndResetMetrics();
        if (!allMetricsExpected(dailyKeepaliveInfoReported)) {
            Log.wtf(TAG, "Unexpected metrics values: " + dailyKeepaliveInfoReported.toString());
        }
        mDependencies.writeStats(dailyKeepaliveInfoReported);
    }

    /** Dump KeepaliveStatsTracker state. */
    public void dump(IndentingPrintWriter pw) {
        ensureRunningOnHandlerThread();
        pw.println("KeepaliveStatsTracker enabled: " + isEnabled());
        pw.increaseIndent();
        pw.println(buildKeepaliveMetrics().toString());
        pw.decreaseIndent();
    }

    private void ensureRunningOnHandlerThread() {
        if (mConnectivityServiceHandler.getLooper().getThread() != Thread.currentThread()) {
            throw new IllegalStateException(
                    "Not running on handler thread: " + Thread.currentThread().getName());
        }
    }
}
