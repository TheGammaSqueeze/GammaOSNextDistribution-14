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

package com.android.server.wifi.coex;

import static android.net.wifi.CoexUnsafeChannel.POWER_CAP_NONE;
import static android.net.wifi.WifiManager.COEX_RESTRICTION_SOFTAP;
import static android.net.wifi.WifiManager.COEX_RESTRICTION_WIFI_AWARE;
import static android.net.wifi.WifiManager.COEX_RESTRICTION_WIFI_DIRECT;
import static android.net.wifi.WifiScanner.WIFI_BAND_24_GHZ;
import static android.net.wifi.WifiScanner.WIFI_BAND_5_GHZ;
import static android.telephony.TelephonyManager.NETWORK_TYPE_LTE;
import static android.telephony.TelephonyManager.NETWORK_TYPE_NR;

import static com.android.server.wifi.coex.CoexUtils.CHANNEL_SET_5_GHZ;
import static com.android.server.wifi.coex.CoexUtils.CHANNEL_SET_5_GHZ_160_MHZ;
import static com.android.server.wifi.coex.CoexUtils.CHANNEL_SET_5_GHZ_20_MHZ;
import static com.android.server.wifi.coex.CoexUtils.CHANNEL_SET_5_GHZ_40_MHZ;
import static com.android.server.wifi.coex.CoexUtils.CHANNEL_SET_5_GHZ_80_MHZ;
import static com.android.server.wifi.coex.CoexUtils.NUM_24_GHZ_CHANNELS;
import static com.android.server.wifi.coex.CoexUtils.get2gHarmonicCoexUnsafeChannels;
import static com.android.server.wifi.coex.CoexUtils.get5gHarmonicCoexUnsafeChannels;
import static com.android.server.wifi.coex.CoexUtils.getCoexUnsafeChannelsForGpsL1;
import static com.android.server.wifi.coex.CoexUtils.getIntermodCoexUnsafeChannels;
import static com.android.server.wifi.coex.CoexUtils.getNeighboringCoexUnsafeChannels;

import android.annotation.AnyThread;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.wifi.CoexUnsafeChannel;
import android.net.wifi.ICoexCallback;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.CoexRestriction;
import android.os.Build;
import android.os.Handler;
import android.os.PersistableBundle;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.telephony.AccessNetworkConstants;
import android.telephony.CarrierConfigManager;
import android.telephony.PhysicalChannelConfig;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.util.SparseBooleanArray;

import androidx.annotation.RequiresApi;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.HandlerExecutor;
import com.android.server.wifi.WifiNative;
import com.android.wifi.resources.R;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import javax.annotation.concurrent.NotThreadSafe;


/**
 * This class handles Wi-Fi/Cellular coexistence by dynamically generating a set of Wi-Fi channels
 * that would cause interference to/receive interference from the active cellular channels. These
 * Wi-Fi channels are represented by {@link CoexUnsafeChannel} and may be retrieved through
 * {@link #getCoexUnsafeChannels()}.
 *
 * Clients may be notified of updates to the value of #getCoexUnsafeChannels by implementing an
 * {@link CoexListener} and listening on
 * {@link CoexListener#onCoexUnsafeChannelsChanged()}
 *
 * Note: This class is not thread-safe. It needs to be invoked from the main Wifi thread only.
 */
@NotThreadSafe
@RequiresApi(Build.VERSION_CODES.S)
public class CoexManager {
    private static final String TAG = "WifiCoexManager";
    private boolean mVerboseLoggingEnabled = false;

    // Delay in millis before updating cell channels to empty in case of a temporary idle.
    @VisibleForTesting
    static final int CELL_CHANNEL_IDLE_DELAY_MILLIS = 2_000;

    @NonNull
    private final Context mContext;
    @NonNull
    private final WifiNative mWifiNative;
    @NonNull
    private final TelephonyManager mDefaultTelephonyManager;
    @NonNull
    private final SubscriptionManager mSubscriptionManager;
    @NonNull
    private final CarrierConfigManager mCarrierConfigManager;
    @NonNull
    private final Handler mCallbackHandler;

    private final List<Pair<TelephonyManager, TelephonyCallback>> mTelephonyManagersAndCallbacks =
            new ArrayList<>();

    @VisibleForTesting
    /* package */ class CoexOnSubscriptionsChangedListener
            extends SubscriptionManager.OnSubscriptionsChangedListener {
        @java.lang.Override
        public void onSubscriptionsChanged() {
            final List<SubscriptionInfo> subInfoList =
                    mSubscriptionManager.getAvailableSubscriptionInfoList();
            updateCarrierConfigs(subInfoList);
            Set<Integer> newSubIds = Collections.emptySet();
            if (subInfoList != null) {
                newSubIds = subInfoList.stream()
                        .map(SubscriptionInfo::getSubscriptionId)
                        .collect(Collectors.toSet());
            }
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "onSubscriptionsChanged called with subIds: " + newSubIds);
            }
            // Unregister callbacks for removed subscriptions
            Iterator<Pair<TelephonyManager, TelephonyCallback>> iter =
                    mTelephonyManagersAndCallbacks.iterator();
            while (iter.hasNext()) {
                Pair<TelephonyManager, TelephonyCallback> managerCallbackPair = iter.next();
                final TelephonyManager telephonyManager = managerCallbackPair.first;
                final TelephonyCallback telephonyCallback = managerCallbackPair.second;
                final int subId = telephonyManager.getSubscriptionId();
                final boolean subIdRemoved = !newSubIds.remove(subId);
                if (subIdRemoved) {
                    mCellChannelsPerSubId.remove(subId);
                    telephonyManager.unregisterTelephonyCallback(telephonyCallback);
                    iter.remove();
                }
            }
            // Register callbacks for new subscriptions
            for (int subId : newSubIds) {
                final TelephonyManager telephonyManager =
                        mDefaultTelephonyManager.createForSubscriptionId(subId);
                if (telephonyManager == null) {
                    Log.e(TAG, "Could not create TelephonyManager for subId: " + subId);
                }
                final TelephonyCallback callback = new CoexTelephonyCallback(subId);
                telephonyManager.registerTelephonyCallback(
                        new HandlerExecutor(mCallbackHandler), callback);
                mTelephonyManagersAndCallbacks.add(new Pair<>(telephonyManager, callback));
            }
        }
    }

    @VisibleForTesting
    /* package */ class CoexTelephonyCallback extends TelephonyCallback
            implements TelephonyCallback.PhysicalChannelConfigListener {
        private final int mSubId;
        private boolean mIsEmpty = true;
        private boolean mIsPendingEmpty = false;
        private final Runnable mClearCellChannelsRunnable = () -> {
            mIsPendingEmpty = false;
            updateCellChannels(new ArrayList<>());
        };

        private CoexTelephonyCallback(int subId) {
            super();
            mSubId = subId;
        }

        @java.lang.Override
        public void onPhysicalChannelConfigChanged(
                @NonNull List<PhysicalChannelConfig> configs) {
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "onPhysicalChannelConfigChanged for subId: " + mSubId
                        + " called with configs: " + configs);
            }
            List<CoexUtils.CoexCellChannel> cellChannels = new ArrayList<>();
            for (PhysicalChannelConfig config : configs) {
                cellChannels.add(new CoexUtils.CoexCellChannel(config, mSubId));
            }
            // Delay updating an empty cell channel list in case this is a temporary idle to avoid
            // recalculating the unsafe channels and sending them to the driver again.
            if (configs.isEmpty()) {
                if (!mIsEmpty && !mIsPendingEmpty) {
                    mIsPendingEmpty = true;
                    mCallbackHandler.postDelayed(
                            mClearCellChannelsRunnable, CELL_CHANNEL_IDLE_DELAY_MILLIS);
                }
                return;
            }
            if (mIsPendingEmpty) {
                mIsPendingEmpty = false;
                mCallbackHandler.removeCallbacks(mClearCellChannelsRunnable);
            }
            updateCellChannels(cellChannels);
        }

        private void updateCellChannels(List<CoexUtils.CoexCellChannel> cellChannels) {
            mIsEmpty = cellChannels.isEmpty();
            if (cellChannels.equals(mCellChannelsPerSubId.get(mSubId))) {
                // No change to cell channels, so no need to recalculate
                return;
            }
            mCellChannelsPerSubId.put(mSubId, cellChannels);
            if (mIsUsingMockCellChannels) {
                return;
            }
            updateCoexUnsafeChannels(getCellChannelsForAllSubIds());
        }
    }

    private final SparseBooleanArray mAvoid5gSoftApForLaaPerSubId = new SparseBooleanArray();
    private final SparseBooleanArray mAvoid5gWifiDirectForLaaPerSubId = new SparseBooleanArray();
    private BroadcastReceiver mCarrierConfigChangedReceiver = new BroadcastReceiver() {
        @java.lang.Override
        public void onReceive(Context context, Intent intent) {
            if (CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED
                    .equals(intent.getAction())) {
                if (updateCarrierConfigs(mSubscriptionManager.getAvailableSubscriptionInfoList())) {
                    updateCoexUnsafeChannels(getCellChannelsForAllSubIds());
                }
            }
        }
    };

    @NonNull
    private final SparseArray<List<CoexUtils.CoexCellChannel>> mCellChannelsPerSubId =
            new SparseArray<>();
    @NonNull
    private final List<CoexUtils.CoexCellChannel> mMockCellChannels = new ArrayList<>();
    private boolean mIsUsingMockCellChannels = false;

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    @NonNull
    private final List<CoexUnsafeChannel> mCurrentCoexUnsafeChannels = new ArrayList<>();
    private int mCoexRestrictions;

    @NonNull
    private final SparseArray<Entry> mLteTableEntriesByBand = new SparseArray<>();
    @NonNull
    private final SparseArray<Entry> mNrTableEntriesByBand = new SparseArray<>();

    @NonNull
    private final Set<CoexListener> mListeners = new HashSet<>();
    @NonNull
    private final RemoteCallbackList<ICoexCallback> mRemoteCallbackList =
            new RemoteCallbackList<ICoexCallback>();

    public CoexManager(@NonNull Context context,
            @NonNull WifiNative wifiNative,
            @NonNull TelephonyManager defaultTelephonyManager,
            @NonNull SubscriptionManager subscriptionManager,
            @NonNull CarrierConfigManager carrierConfigManager,
            @NonNull Handler handler) {
        mContext = context;
        mWifiNative = wifiNative;
        mDefaultTelephonyManager = defaultTelephonyManager;
        mSubscriptionManager = subscriptionManager;
        mCarrierConfigManager = carrierConfigManager;
        mCallbackHandler = handler;
        if (!mContext.getResources().getBoolean(R.bool.config_wifiDefaultCoexAlgorithmEnabled)) {
            Log.i(TAG, "Default coex algorithm is disabled.");
            return;
        }
        readTableFromXml();
        IntentFilter filter = new IntentFilter();
        filter.addAction(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        mContext.registerReceiver(mCarrierConfigChangedReceiver, filter, null, mCallbackHandler);
        mSubscriptionManager.addOnSubscriptionsChangedListener(
                new HandlerExecutor(mCallbackHandler), new CoexOnSubscriptionsChangedListener());
    }

    /**
     * Returns the list of current {@link CoexUnsafeChannel} being used for Wi-Fi/Cellular coex
     * channel avoidance supplied in {@link #setCoexUnsafeChannels(List, int)}.
     *
     * If any {@link CoexRestriction} flags are set in {@link #getCoexRestrictions()}, then the
     * CoexUnsafeChannels should be totally avoided (i.e. not best effort) for the Wi-Fi modes
     * specified by the flags.
     *
     * @return Set of current CoexUnsafeChannels.
     */
    @AnyThread
    @NonNull
    public List<CoexUnsafeChannel> getCoexUnsafeChannels() {
        synchronized (mLock) {
            return new ArrayList<>(mCurrentCoexUnsafeChannels);
        }
    }

    /**
     * Returns the current coex restrictions being used for Wi-Fi/Cellular coex
     * channel avoidance supplied in {@link #setCoexUnsafeChannels(List, int)}.
     *
     * @return int containing a bitwise-OR combination of {@link CoexRestriction}.
     */
    public int getCoexRestrictions() {
        return mCoexRestrictions;
    }

    /**
     * Sets the current CoexUnsafeChannels and coex restrictions returned by
     * {@link #getCoexUnsafeChannels()} and {@link #getCoexRestrictions()} and notifies each
     * listener with {@link CoexListener#onCoexUnsafeChannelsChanged()} and each
     * remote callback with {@link ICoexCallback#onCoexUnsafeChannelsChanged()}.
     *
     * @param coexUnsafeChannels Set of CoexUnsafeChannels to return in
     *                           {@link #getCoexUnsafeChannels()}
     * @param coexRestrictions int to return in {@link #getCoexRestrictions()}
     */
    public void setCoexUnsafeChannels(@NonNull List<CoexUnsafeChannel> coexUnsafeChannels,
            int coexRestrictions) {
        if (coexUnsafeChannels == null) {
            Log.e(TAG, "setCoexUnsafeChannels called with null unsafe channel set");
            return;
        }
        if ((~(COEX_RESTRICTION_WIFI_DIRECT | COEX_RESTRICTION_SOFTAP
                | COEX_RESTRICTION_WIFI_AWARE) & coexRestrictions) != 0) {
            Log.e(TAG, "setCoexUnsafeChannels called with undefined restriction flags");
            return;
        }
        synchronized (mLock) {
            if (new HashSet(mCurrentCoexUnsafeChannels).equals(new HashSet(coexUnsafeChannels))
                    && mCoexRestrictions == coexRestrictions) {
                // Do not update if the unsafe channels haven't changed since the last time
                return;
            }
            mCurrentCoexUnsafeChannels.clear();
            mCurrentCoexUnsafeChannels.addAll(coexUnsafeChannels);
            mCoexRestrictions = coexRestrictions;
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "Current unsafe channels: " + mCurrentCoexUnsafeChannels
                        + ", restrictions: " + mCoexRestrictions);
            }
            mWifiNative.setCoexUnsafeChannels(mCurrentCoexUnsafeChannels, mCoexRestrictions);
        }
        notifyListeners();
        notifyRemoteCallbacks();
    }

    /**
     * Registers a {@link CoexListener} to be notified with updates.
     * @param listener CoexListener to be registered.
     */
    public void registerCoexListener(@NonNull CoexListener listener) {
        if (listener == null) {
            Log.e(TAG, "registerCoexListener called with null listener");
            return;
        }
        mListeners.add(listener);
    }

    /**
     * Unregisters a {@link CoexListener}.
     * @param listener CoexListener to be unregistered.
     */
    public void unregisterCoexListener(@NonNull CoexListener listener) {
        if (listener == null) {
            Log.e(TAG, "unregisterCoexListener called with null listener");
            return;
        }
        if (!mListeners.remove(listener)) {
            Log.e(TAG, "unregisterCoexListener called on listener that was not registered: "
                    + listener);
        }
    }

    /**
     * Registers a remote ICoexCallback from an external app and immediately notifies it.
     * see {@link WifiManager#registerCoexCallback(Executor, WifiManager.CoexCallback)}
     * @param callback ICoexCallback instance to register
     */
    public void registerRemoteCoexCallback(ICoexCallback callback) {
        mRemoteCallbackList.register(callback);
        try {
            synchronized (mLock) {
                callback.onCoexUnsafeChannelsChanged(mCurrentCoexUnsafeChannels, mCoexRestrictions);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "onCoexUnsafeChannelsChanged: remote exception -- " + e);
        }
    }

    /**
     * Unregisters a remote ICoexCallback from an external app.
     * see {@link WifiManager#unregisterCoexCallback(WifiManager.CoexCallback)}
     * @param callback ICoexCallback instance to unregister
     */
    public void unregisterRemoteCoexCallback(ICoexCallback callback) {
        mRemoteCallbackList.unregister(callback);
    }

    private void notifyListeners() {
        for (CoexListener listener : mListeners) {
            listener.onCoexUnsafeChannelsChanged();
        }
    }

    private void notifyRemoteCallbacks() {
        final int itemCount = mRemoteCallbackList.beginBroadcast();
        for (int i = 0; i < itemCount; i++) {
            try {
                synchronized (mLock) {
                    mRemoteCallbackList.getBroadcastItem(i).onCoexUnsafeChannelsChanged(
                            mCurrentCoexUnsafeChannels, mCoexRestrictions);
                }
            } catch (RemoteException e) {
                Log.e(TAG, "onCoexUnsafeChannelsChanged: remote exception -- " + e);
            }
        }
        mRemoteCallbackList.finishBroadcast();
    }

    /**
     * Listener interface for internal Wi-Fi clients to listen to updates to
     * {@link #getCoexUnsafeChannels()} and {@link #getCoexRestrictions()}
     */
    public interface CoexListener {
        /**
         * Called to notify the listener that the values of
         * {@link CoexManager#getCoexUnsafeChannels()} and/or
         * {@link CoexManager#getCoexRestrictions()} have changed and should be
         * retrieved again.
         */
        void onCoexUnsafeChannelsChanged();
    }

    private void updateCoexUnsafeChannels(@NonNull List<CoexUtils.CoexCellChannel> cellChannels) {
        if (cellChannels == null) {
            Log.e(TAG, "updateCoexUnsafeChannels called with null cell channel list");
            return;
        }
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "updateCoexUnsafeChannels called with cell channels: " + cellChannels);
        }
        int numUnsafe2gChannels = 0;
        int numUnsafe5gChannels = 0;
        int default2gChannel = Integer.MAX_VALUE;
        int default5gChannel = Integer.MAX_VALUE;
        int coexRestrictions = 0;
        Map<Pair<Integer, Integer>, CoexUnsafeChannel> coexUnsafeChannelsByBandChannelPair =
                new HashMap<>();
        // Gather all of the CoexUnsafeChannels calculated from each cell channel.
        for (CoexUtils.CoexCellChannel cellChannel : cellChannels) {
            final Entry entry;
            switch (cellChannel.getRat()) {
                case NETWORK_TYPE_LTE:
                    entry = mLteTableEntriesByBand.get(cellChannel.getBand());
                    break;
                case NETWORK_TYPE_NR:
                    entry = mNrTableEntriesByBand.get(cellChannel.getBand());
                    break;
                default:
                    entry = null;
            }
            final int downlinkFreqKhz = cellChannel.getDownlinkFreqKhz();
            final int downlinkBandwidthKhz = cellChannel.getDownlinkBandwidthKhz();
            final int uplinkFreqKhz = cellChannel.getUplinkFreqKhz();
            final int uplinkBandwidthKhz = cellChannel.getUplinkBandwidthKhz();
            final List<CoexUnsafeChannel> currentBandUnsafeChannels = new ArrayList<>();
            if (entry != null) {
                final int powerCapDbm;
                if (entry.hasPowerCapDbm()) {
                    powerCapDbm = entry.getPowerCapDbm();
                    if (mVerboseLoggingEnabled) {
                        Log.v(TAG, cellChannel + " sets wifi power cap " + powerCapDbm);
                    }
                } else {
                    powerCapDbm = POWER_CAP_NONE;
                }
                final Params params = entry.getParams();
                final Override override = entry.getOverride();
                if (params != null) {
                    // Add all of the CoexUnsafeChannels calculated with the given parameters.
                    final NeighborThresholds neighborThresholds = params.getNeighborThresholds();
                    final HarmonicParams harmonicParams2g = params.getHarmonicParams2g();
                    final HarmonicParams harmonicParams5g = params.getHarmonicParams5g();
                    final IntermodParams intermodParams2g = params.getIntermodParams2g();
                    final IntermodParams intermodParams5g = params.getIntermodParams2g();
                    final DefaultChannels defaultChannels = params.getDefaultChannels();
                    // Calculate interference from cell downlink.
                    if (downlinkFreqKhz >= 0 && downlinkBandwidthKhz > 0) {
                        if (neighborThresholds != null && neighborThresholds.hasCellVictimMhz()) {
                            final List<CoexUnsafeChannel> neighboringChannels =
                                    getNeighboringCoexUnsafeChannels(
                                            downlinkFreqKhz,
                                            downlinkBandwidthKhz,
                                            neighborThresholds.getCellVictimMhz() * 1000,
                                            powerCapDbm);
                            if (!neighboringChannels.isEmpty()) {
                                if (mVerboseLoggingEnabled) {
                                    Log.v(TAG, cellChannel + " is neighboring victim of "
                                            + neighboringChannels);
                                }
                                currentBandUnsafeChannels.addAll(neighboringChannels);
                            }
                        }
                    }
                    // Calculate interference from cell uplink
                    if (uplinkFreqKhz >= 0 && uplinkBandwidthKhz > 0) {
                        if (neighborThresholds != null && neighborThresholds.hasWifiVictimMhz()) {
                            final List<CoexUnsafeChannel> neighboringChannels =
                                    getNeighboringCoexUnsafeChannels(
                                            uplinkFreqKhz,
                                            uplinkBandwidthKhz,
                                            neighborThresholds.getWifiVictimMhz() * 1000,
                                            powerCapDbm);
                            if (!neighboringChannels.isEmpty()) {
                                if (mVerboseLoggingEnabled) {
                                    Log.v(TAG, cellChannel + " is neighboring aggressor to "
                                            + neighboringChannels);
                                }
                                currentBandUnsafeChannels.addAll(neighboringChannels);
                            }
                        }
                        if (harmonicParams2g != null) {
                            final List<CoexUnsafeChannel> harmonicChannels2g =
                                    get2gHarmonicCoexUnsafeChannels(
                                            uplinkFreqKhz,
                                            uplinkBandwidthKhz,
                                            harmonicParams2g.getN(),
                                            harmonicParams2g.getOverlap(),
                                            powerCapDbm);
                            if (!harmonicChannels2g.isEmpty()) {
                                if (mVerboseLoggingEnabled) {
                                    Log.v(TAG, cellChannel + " has harmonic interference with "
                                            + harmonicChannels2g);
                                }
                                currentBandUnsafeChannels.addAll(harmonicChannels2g);
                            }
                        }
                        if (harmonicParams5g != null) {
                            final List<CoexUnsafeChannel> harmonicChannels5g =
                                    get5gHarmonicCoexUnsafeChannels(
                                            uplinkFreqKhz,
                                            uplinkBandwidthKhz,
                                            harmonicParams5g.getN(),
                                            harmonicParams5g.getOverlap(),
                                            powerCapDbm);
                            if (!harmonicChannels5g.isEmpty()) {
                                if (mVerboseLoggingEnabled) {
                                    Log.v(TAG, cellChannel + " has harmonic interference with "
                                            + harmonicChannels5g);
                                }
                                currentBandUnsafeChannels.addAll(harmonicChannels5g);
                            }
                        }

                        if (intermodParams2g != null) {
                            for (CoexUtils.CoexCellChannel victimCellChannel : cellChannels) {
                                if (victimCellChannel.getDownlinkFreqKhz() >= 0
                                        && victimCellChannel.getDownlinkBandwidthKhz() > 0) {
                                    final List<CoexUnsafeChannel> intermodChannels2g =
                                            getIntermodCoexUnsafeChannels(
                                                    uplinkFreqKhz,
                                                    uplinkBandwidthKhz,
                                                    victimCellChannel.getDownlinkFreqKhz(),
                                                    victimCellChannel.getDownlinkBandwidthKhz(),
                                                    intermodParams2g.getN(),
                                                    intermodParams2g.getM(),
                                                    intermodParams2g.getOverlap(),
                                                    WIFI_BAND_24_GHZ,
                                                    powerCapDbm);
                                    if (!intermodChannels2g.isEmpty()) {
                                        if (mVerboseLoggingEnabled) {
                                            Log.v(TAG, cellChannel + " and " + intermodChannels2g
                                                    + " have intermod interference on "
                                                    + victimCellChannel);
                                        }
                                        currentBandUnsafeChannels.addAll(intermodChannels2g);
                                    }
                                }
                            }
                        }
                        if (intermodParams5g != null) {
                            for (CoexUtils.CoexCellChannel victimCellChannel : cellChannels) {
                                if (victimCellChannel.getDownlinkFreqKhz() >= 0
                                        && victimCellChannel.getDownlinkBandwidthKhz() > 0) {
                                    final List<CoexUnsafeChannel> intermodChannels5g =
                                            getIntermodCoexUnsafeChannels(
                                                    uplinkFreqKhz,
                                                    uplinkBandwidthKhz,
                                                    victimCellChannel.getDownlinkFreqKhz(),
                                                    victimCellChannel.getDownlinkBandwidthKhz(),
                                                    intermodParams5g.getN(),
                                                    intermodParams5g.getM(),
                                                    intermodParams5g.getOverlap(),
                                                    WIFI_BAND_5_GHZ,
                                                    powerCapDbm);
                                    if (!intermodChannels5g.isEmpty()) {
                                        if (mVerboseLoggingEnabled) {
                                            Log.v(TAG, cellChannel + " and " + intermodChannels5g
                                                    + " have intermod interference on "
                                                    + victimCellChannel);
                                        }
                                        currentBandUnsafeChannels.addAll(intermodChannels5g);
                                    }
                                }
                            }
                        }
                    }
                    // Collect the lowest number default channel for each band to extract from
                    // calculated set of CoexUnsafeChannels later.
                    if (defaultChannels != null) {
                        if (defaultChannels.hasDefault2g()) {
                            int channel = defaultChannels.getDefault2g();
                            if (channel < default2gChannel) {
                                default2gChannel = channel;
                            }
                        }
                        if (defaultChannels.hasDefault5g()) {
                            int channel = defaultChannels.getDefault5g();
                            if (channel < default5gChannel) {
                                default5gChannel = channel;
                            }
                        }
                    }
                } else if (override != null) {
                    // Add all of the CoexUnsafeChannels defined by the override lists.
                    final Override2g override2g = override.getOverride2g();
                    if (override2g != null) {
                        final List<Integer> channelList2g = override2g.getChannel();
                        for (OverrideCategory2g category : override2g.getCategory()) {
                            if (OverrideCategory2g.all.equals(category)) {
                                for (int i = 1; i <= 14; i++) {
                                    channelList2g.add(i);
                                }
                            }
                        }
                        if (!channelList2g.isEmpty()) {
                            if (mVerboseLoggingEnabled) {
                                Log.v(TAG, cellChannel + " sets override 2g channels "
                                        + channelList2g);
                            }
                            for (int channel : channelList2g) {
                                currentBandUnsafeChannels.add(new CoexUnsafeChannel(
                                        WIFI_BAND_24_GHZ, channel, powerCapDbm));
                            }
                        }
                    }
                    final Override5g override5g = override.getOverride5g();
                    if (override5g != null) {
                        final List<Integer> channelList5g = override5g.getChannel();
                        for (OverrideCategory5g category : override5g.getCategory()) {
                            if (OverrideCategory5g._20Mhz.equals(category)) {
                                channelList5g.addAll(CHANNEL_SET_5_GHZ_20_MHZ);
                            } else if (OverrideCategory5g._40Mhz.equals(category)) {
                                channelList5g.addAll(CHANNEL_SET_5_GHZ_40_MHZ);
                            } else if (OverrideCategory5g._80Mhz.equals(category)) {
                                channelList5g.addAll(CHANNEL_SET_5_GHZ_80_MHZ);
                            } else if (OverrideCategory5g._160Mhz.equals(category)) {
                                channelList5g.addAll(CHANNEL_SET_5_GHZ_160_MHZ);
                            } else if (OverrideCategory5g.all.equals(category)) {
                                channelList5g.addAll(CHANNEL_SET_5_GHZ);
                            }
                        }
                        if (!channelList5g.isEmpty()) {
                            if (mVerboseLoggingEnabled) {
                                Log.v(TAG, cellChannel + " sets override 5g channels "
                                        + channelList5g);
                            }
                            for (int channel : channelList5g) {
                                currentBandUnsafeChannels.add(new CoexUnsafeChannel(
                                        WIFI_BAND_5_GHZ, channel, powerCapDbm));
                            }
                        }
                    }
                }
            }
            // Set coex restrictions for LAA based on carrier config values.
            if (cellChannel.getRat() == NETWORK_TYPE_LTE
                    && cellChannel.getBand() == AccessNetworkConstants.EutranBand.BAND_46) {
                final boolean avoid5gSoftAp =
                        mAvoid5gSoftApForLaaPerSubId.get(cellChannel.getSubId());
                final boolean avoid5gWifiDirect =
                        mAvoid5gWifiDirectForLaaPerSubId.get(cellChannel.getSubId());
                if (avoid5gSoftAp || avoid5gWifiDirect) {
                    for (int channel : CHANNEL_SET_5_GHZ) {
                        currentBandUnsafeChannels.add(
                                new CoexUnsafeChannel(WIFI_BAND_5_GHZ, channel));
                    }
                    if (avoid5gSoftAp) {
                        if (mVerboseLoggingEnabled) {
                            Log.v(TAG, "Avoiding 5g softap due to LAA channel " + cellChannel);
                        }
                        coexRestrictions |= COEX_RESTRICTION_SOFTAP;
                    }
                    if (avoid5gWifiDirect) {
                        if (mVerboseLoggingEnabled) {
                            Log.v(TAG, "Avoiding 5g wifi direct due to LAA channel " + cellChannel);
                        }
                        coexRestrictions |= COEX_RESTRICTION_WIFI_DIRECT;
                    }
                }
            }
            // Add all of the CoexUnsafeChannels that cause intermod on GPS L1 with the current
            // uplink cell channels.
            Resources res = mContext.getResources();
            if (res.getBoolean(R.bool.config_wifiCoexForGpsL1)) {
                if (uplinkFreqKhz >= 0 && uplinkBandwidthKhz >= 0) {
                    currentBandUnsafeChannels.addAll(getCoexUnsafeChannelsForGpsL1(
                            uplinkFreqKhz, uplinkBandwidthKhz,
                            res.getInteger(R.integer.config_wifiCoexGpsL1ThresholdKhz)));
                }
            }
            // Add all of the CoexUnsafeChannels calculated from this cell channel to the total.
            // If the total already contains a CoexUnsafeChannel for the same band and channel,
            // keep the one that has the lower power cap.
            for (CoexUnsafeChannel unsafeChannel : currentBandUnsafeChannels) {
                final int band = unsafeChannel.getBand();
                final int channel = unsafeChannel.getChannel();
                final Pair<Integer, Integer> bandChannelPair = new Pair<>(band, channel);
                final CoexUnsafeChannel existingUnsafeChannel =
                        coexUnsafeChannelsByBandChannelPair.get(bandChannelPair);
                if (existingUnsafeChannel != null) {
                    if (unsafeChannel.getPowerCapDbm() == POWER_CAP_NONE) {
                        continue;
                    }
                    final int existingPowerCapDbm = existingUnsafeChannel.getPowerCapDbm();
                    if (existingPowerCapDbm != POWER_CAP_NONE
                            && existingPowerCapDbm < unsafeChannel.getPowerCapDbm()) {
                        continue;
                    }
                } else {
                    // Count the number of unsafe channels for each band to determine if we need to
                    // remove the default channels before returning.
                    if (band == WIFI_BAND_24_GHZ) {
                        numUnsafe2gChannels++;
                    } else if (band == WIFI_BAND_5_GHZ) {
                        numUnsafe5gChannels++;
                    }
                }
                coexUnsafeChannelsByBandChannelPair.put(bandChannelPair, unsafeChannel);
            }
        }
        // Omit the default channel from each band if the entire band is unsafe and there are
        // no coex restrictions set.
        if (coexRestrictions == 0) {
            if (numUnsafe2gChannels == NUM_24_GHZ_CHANNELS) {
                if (mVerboseLoggingEnabled) {
                    Log.v(TAG, "Omitting default 2g channel " + default2gChannel
                            + " from unsafe set.");
                }
                coexUnsafeChannelsByBandChannelPair.remove(
                        new Pair<>(WIFI_BAND_24_GHZ, default2gChannel));
            }
            if (numUnsafe5gChannels == CHANNEL_SET_5_GHZ.size()) {
                if (mVerboseLoggingEnabled) {
                    Log.v(TAG, "Omitting default 5g channel " + default5gChannel
                            + " from unsafe set.");
                }
                coexUnsafeChannelsByBandChannelPair.remove(
                        new Pair<>(WIFI_BAND_5_GHZ, default5gChannel));
            }
        }
        setCoexUnsafeChannels(
                new ArrayList<>(coexUnsafeChannelsByBandChannelPair.values()), coexRestrictions);
    }

    /**
     * Updates carrier config values and returns true if the values have changed, false otherwise.
     */
    private boolean updateCarrierConfigs(@Nullable List<SubscriptionInfo> subInfos) {
        final SparseBooleanArray oldAvoid5gSoftAp = mAvoid5gSoftApForLaaPerSubId.clone();
        final SparseBooleanArray oldAvoid5gWifiDirect = mAvoid5gWifiDirectForLaaPerSubId.clone();
        mAvoid5gSoftApForLaaPerSubId.clear();
        mAvoid5gWifiDirectForLaaPerSubId.clear();
        if (subInfos != null) {
            for (SubscriptionInfo subInfo : subInfos) {
                final int subId = subInfo.getSubscriptionId();
                PersistableBundle bundle = mCarrierConfigManager.getConfigForSubId(subId);
                if (bundle != null) {
                    mAvoid5gSoftApForLaaPerSubId.put(subId, bundle.getBoolean(
                            CarrierConfigManager.Wifi.KEY_AVOID_5GHZ_SOFTAP_FOR_LAA_BOOL));
                    mAvoid5gWifiDirectForLaaPerSubId.put(subId, bundle.getBoolean(
                            CarrierConfigManager.Wifi.KEY_AVOID_5GHZ_WIFI_DIRECT_FOR_LAA_BOOL));
                }
            }
        }
        return !mAvoid5gSoftApForLaaPerSubId.equals(oldAvoid5gSoftAp)
                || !mAvoid5gWifiDirectForLaaPerSubId.equals(oldAvoid5gWifiDirect);
    }

    /**
     * Parses a coex table xml from the specified File and populates the table entry maps.
     * Returns {@code true} if the file was found and read successfully, {@code false} otherwise.
     */
    @VisibleForTesting
    boolean readTableFromXml() {
        final String filepath = mContext.getResources().getString(
                R.string.config_wifiCoexTableFilepath);
        if (filepath == null) {
            Log.e(TAG, "Coex table filepath was null");
            return false;
        }
        final File file = new File(filepath);
        try (InputStream str = new BufferedInputStream(new FileInputStream(file))) {
            mLteTableEntriesByBand.clear();
            mNrTableEntriesByBand.clear();
            for (Entry entry : XmlParser.readTable(str).getEntry()) {
                if (RatType.LTE.equals(entry.getRat())) {
                    mLteTableEntriesByBand.put(entry.getBand(), entry);
                } else if (RatType.NR.equals(entry.getRat())) {
                    mNrTableEntriesByBand.put(entry.getBand(), entry);
                }
            }
            Log.i(TAG, "Successfully read coex table from file");
            return true;
        } catch (FileNotFoundException e) {
            Log.e(TAG, "No coex table file found at " + file);
        } catch (Exception e) {
            Log.e(TAG, "Failed to read coex table file: " + e);
        }
        return false;
    }

    /**
     * Sets the mock CoexCellChannels to use for coex calculations.
     * @param cellChannels list of mock cell channels
     */
    public void setMockCellChannels(@NonNull List<CoexUtils.CoexCellChannel> cellChannels) {
        mIsUsingMockCellChannels = true;
        mMockCellChannels.clear();
        mMockCellChannels.addAll(cellChannels);
        updateCoexUnsafeChannels(mMockCellChannels);
    }

    /**
     * Removes all added mock CoexCellChannels.
     */
    public void resetMockCellChannels() {
        mIsUsingMockCellChannels = false;
        mMockCellChannels.clear();
        updateCoexUnsafeChannels(getCellChannelsForAllSubIds());
    }

    /**
     * Returns all cell channels used for coex calculations.
     */
    public List<CoexUtils.CoexCellChannel> getCellChannels() {
        if (mIsUsingMockCellChannels) {
            return mMockCellChannels;
        }
        return getCellChannelsForAllSubIds();
    }

    private List<CoexUtils.CoexCellChannel> getCellChannelsForAllSubIds() {
        final List<CoexUtils.CoexCellChannel> cellChannels = new ArrayList<>();
        for (int i = 0, size = mCellChannelsPerSubId.size(); i < size; i++) {
            cellChannels.addAll(mCellChannelsPerSubId.valueAt(i));
        }
        return cellChannels;
    }

    /**
     * Enables verbose logging of the coex algorithm.
     */
    public void enableVerboseLogging(boolean verbose) {
        mVerboseLoggingEnabled = verbose;
    }
}
