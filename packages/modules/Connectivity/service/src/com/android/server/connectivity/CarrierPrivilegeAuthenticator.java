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

package com.android.server.connectivity;

import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;

import static com.android.server.connectivity.ConnectivityFlags.CARRIER_SERVICE_CHANGED_USE_CALLBACK;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.NetworkCapabilities;
import android.net.NetworkSpecifier;
import android.net.TelephonyNetworkSpecifier;
import android.net.TransportInfo;
import android.net.wifi.WifiInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.IndentingPrintWriter;
import com.android.modules.utils.HandlerExecutor;
import com.android.modules.utils.build.SdkLevel;
import com.android.net.module.util.DeviceConfigUtils;
import com.android.networkstack.apishim.TelephonyManagerShimImpl;
import com.android.networkstack.apishim.common.TelephonyManagerShim;
import com.android.networkstack.apishim.common.TelephonyManagerShim.CarrierPrivilegesListenerShim;
import com.android.networkstack.apishim.common.UnsupportedApiLevelException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

/**
 * Tracks the uid of the carrier privileged app that provides the carrier config.
 * Authenticates if the caller has same uid as
 * carrier privileged app that provides the carrier config
 * @hide
 */
public class CarrierPrivilegeAuthenticator {
    private static final String TAG = CarrierPrivilegeAuthenticator.class.getSimpleName();
    private static final boolean DBG = true;

    // The context is for the current user (system server)
    private final Context mContext;
    private final TelephonyManagerShim mTelephonyManagerShim;
    private final TelephonyManager mTelephonyManager;
    @GuardedBy("mLock")
    private final SparseArray<CarrierServiceUidWithSubId> mCarrierServiceUidWithSubId =
            new SparseArray<>(2 /* initialCapacity */);
    @GuardedBy("mLock")
    private int mModemCount = 0;
    private final Object mLock = new Object();
    private final Handler mHandler;
    @NonNull
    private final List<PrivilegeListener> mCarrierPrivilegesChangedListeners = new ArrayList<>();
    private final boolean mUseCallbacksForServiceChanged;
    private final boolean mRequestRestrictedWifiEnabled;
    @NonNull
    private final BiConsumer<Integer, Integer> mListener;

    public CarrierPrivilegeAuthenticator(@NonNull final Context c,
            @NonNull final Dependencies deps,
            @NonNull final TelephonyManager t,
            @NonNull final TelephonyManagerShim telephonyManagerShim,
            final boolean requestRestrictedWifiEnabled,
            @NonNull BiConsumer<Integer, Integer> listener) {
        mContext = c;
        mTelephonyManager = t;
        mTelephonyManagerShim = telephonyManagerShim;
        final HandlerThread thread = deps.makeHandlerThread();
        thread.start();
        mHandler = new Handler(thread.getLooper());
        mUseCallbacksForServiceChanged = deps.isFeatureEnabled(
                c, CARRIER_SERVICE_CHANGED_USE_CALLBACK);
        mRequestRestrictedWifiEnabled = requestRestrictedWifiEnabled;
        mListener = listener;
        final IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyManager.ACTION_MULTI_SIM_CONFIG_CHANGED);
        synchronized (mLock) {
            // Never unregistered because the system server never stops
            c.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(final Context context, final Intent intent) {
                    switch (intent.getAction()) {
                        case TelephonyManager.ACTION_MULTI_SIM_CONFIG_CHANGED:
                            simConfigChanged();
                            break;
                        default:
                            Log.d(TAG, "Unknown intent received, action: " + intent.getAction());
                    }
                }
            }, filter, null, mHandler);
            simConfigChanged();
        }
    }

    public CarrierPrivilegeAuthenticator(@NonNull final Context c,
            @NonNull final TelephonyManager t, final boolean requestRestrictedWifiEnabled,
            @NonNull BiConsumer<Integer, Integer> listener) {
        this(c, new Dependencies(), t, TelephonyManagerShimImpl.newInstance(t),
                requestRestrictedWifiEnabled, listener);
    }

    public static class Dependencies {
        /**
         * Create a HandlerThread to use in CarrierPrivilegeAuthenticator.
         */
        public HandlerThread makeHandlerThread() {
            return new HandlerThread(TAG);
        }

        /**
         * @see DeviceConfigUtils#isTetheringFeatureEnabled
         */
        public boolean isFeatureEnabled(Context context, String name) {
            return DeviceConfigUtils.isTetheringFeatureEnabled(context, name);
        }
    }

    private void simConfigChanged() {
        synchronized (mLock) {
            unregisterCarrierPrivilegesListeners();
            mModemCount = mTelephonyManager.getActiveModemCount();
            registerCarrierPrivilegesListeners(mModemCount);
            if (!mUseCallbacksForServiceChanged) updateCarrierServiceUid();
        }
    }

    private static class CarrierServiceUidWithSubId {
        final int mUid;
        final int mSubId;

        CarrierServiceUidWithSubId(int uid, int subId) {
            mUid = uid;
            mSubId = subId;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof CarrierServiceUidWithSubId)) {
                return false;
            }
            CarrierServiceUidWithSubId compare = (CarrierServiceUidWithSubId) obj;
            return (mUid == compare.mUid && mSubId == compare.mSubId);
        }

        @Override
        public int hashCode() {
            return mUid * 31 + mSubId;
        }
    }
    private class PrivilegeListener implements CarrierPrivilegesListenerShim {
        public final int mLogicalSlot;

        PrivilegeListener(final int logicalSlot) {
            mLogicalSlot = logicalSlot;
        }

        @Override
        public void onCarrierPrivilegesChanged(
                @NonNull List<String> privilegedPackageNames,
                @NonNull int[] privilegedUids) {
            if (mUseCallbacksForServiceChanged) return;
            // Re-trigger the synchronous check (which is also very cheap due
            // to caching in CarrierPrivilegesTracker). This allows consistency
            // with the onSubscriptionsChangedListener and broadcasts.
            updateCarrierServiceUid();
        }

        @Override
        public void onCarrierServiceChanged(@Nullable final String carrierServicePackageName,
                final int carrierServiceUid) {
            if (!mUseCallbacksForServiceChanged) {
                // Re-trigger the synchronous check (which is also very cheap due
                // to caching in CarrierPrivilegesTracker). This allows consistency
                // with the onSubscriptionsChangedListener and broadcasts.
                updateCarrierServiceUid();
                return;
            }
            synchronized (mLock) {
                CarrierServiceUidWithSubId oldPair =
                        mCarrierServiceUidWithSubId.get(mLogicalSlot);
                int subId = getSubId(mLogicalSlot);
                mCarrierServiceUidWithSubId.put(
                        mLogicalSlot,
                        new CarrierServiceUidWithSubId(carrierServiceUid, subId));
                if (oldPair != null
                        && oldPair.mUid != Process.INVALID_UID
                        && oldPair.mSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID
                        && !oldPair.equals(mCarrierServiceUidWithSubId.get(mLogicalSlot))) {
                    mListener.accept(oldPair.mUid, oldPair.mSubId);
                }
            }
        }
    }

    private void registerCarrierPrivilegesListeners(final int modemCount) {
        final HandlerExecutor executor = new HandlerExecutor(mHandler);
        try {
            for (int i = 0; i < modemCount; i++) {
                PrivilegeListener carrierPrivilegesListener = new PrivilegeListener(i);
                addCarrierPrivilegesListener(executor, carrierPrivilegesListener);
                mCarrierPrivilegesChangedListeners.add(carrierPrivilegesListener);
            }
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Encountered exception registering carrier privileges listeners", e);
        }
    }

    @GuardedBy("mLock")
    private void unregisterCarrierPrivilegesListeners() {
        for (PrivilegeListener carrierPrivilegesListener : mCarrierPrivilegesChangedListeners) {
            removeCarrierPrivilegesListener(carrierPrivilegesListener);
            CarrierServiceUidWithSubId oldPair =
                    mCarrierServiceUidWithSubId.get(carrierPrivilegesListener.mLogicalSlot);
            mCarrierServiceUidWithSubId.remove(carrierPrivilegesListener.mLogicalSlot);
            if (oldPair != null
                    && oldPair.mUid != Process.INVALID_UID
                    && oldPair.mSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                mListener.accept(oldPair.mUid, oldPair.mSubId);
            }
        }
        mCarrierPrivilegesChangedListeners.clear();
    }

    private String getCarrierServicePackageNameForLogicalSlot(int logicalSlotIndex) {
        try {
            return mTelephonyManagerShim.getCarrierServicePackageNameForLogicalSlot(
                    logicalSlotIndex);
        } catch (UnsupportedApiLevelException unsupportedApiLevelException) {
            // Should not happen since CarrierPrivilegeAuthenticator is only used on T+
            Log.e(TAG, "getCarrierServicePackageNameForLogicalSlot API is not available");
        }
        return null;
    }

    /**
     * Check if a UID is the carrier service app of the subscription ID in the provided capabilities
     *
     * This returns whether the passed UID is the carrier service package for the subscription ID
     * stored in the telephony network specifier in the passed network capabilities.
     * If the capabilities don't code for a cellular or Wi-Fi network, or if they don't have the
     * subscription ID in their specifier, this returns false.
     *
     * This method can be used to check that a network request that requires the UID to be
     * the carrier service UID is indeed called by such a UID. An example of such a network could
     * be a network with the  {@link android.net.NetworkCapabilities#NET_CAPABILITY_CBS}
     * capability.
     * It can also be used to check that a factory is entitled to grant access to a given network
     * to a given UID on grounds that it is the carrier service package.
     *
     * @param callingUid uid of the app claimed to be the carrier service package.
     * @param networkCapabilities the network capabilities for which carrier privilege is checked.
     * @return true if uid provides the relevant carrier config else false.
     */
    public boolean isCarrierServiceUidForNetworkCapabilities(int callingUid,
            @NonNull NetworkCapabilities networkCapabilities) {
        if (callingUid == Process.INVALID_UID) {
            return false;
        }
        int subId = getSubIdFromNetworkCapabilities(networkCapabilities);
        if (SubscriptionManager.INVALID_SUBSCRIPTION_ID == subId) {
            return false;
        }
        return callingUid == getCarrierServiceUidForSubId(subId);
    }

    /**
     * Extract the SubscriptionId from the NetworkCapabilities.
     *
     * @param networkCapabilities the network capabilities which may contains the SubscriptionId.
     * @return the SubscriptionId.
     */
    public int getSubIdFromNetworkCapabilities(@NonNull NetworkCapabilities networkCapabilities) {
        int subId;
        if (networkCapabilities.hasSingleTransportBesidesTest(TRANSPORT_CELLULAR)) {
            subId = getSubIdFromTelephonySpecifier(networkCapabilities.getNetworkSpecifier());
        } else if (networkCapabilities.hasSingleTransportBesidesTest(TRANSPORT_WIFI)) {
            subId = getSubIdFromWifiTransportInfo(networkCapabilities.getTransportInfo());
        } else {
            subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID
                && mRequestRestrictedWifiEnabled
                && networkCapabilities.getSubscriptionIds().size() == 1) {
            subId = networkCapabilities.getSubscriptionIds().toArray(new Integer[0])[0];
        }

        if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID
                && !networkCapabilities.getSubscriptionIds().contains(subId)) {
            // Ideally, the code above should just use networkCapabilities.getSubscriptionIds()
            // for simplicity and future-proofing. However, this is not the historical behavior,
            // and there is no enforcement that they do not differ, so log a terrible failure if
            // they do not match to gain confidence this never happens.
            // TODO : when there is confidence that this never happens, rewrite the code above
            // with NetworkCapabilities#getSubscriptionIds.
            Log.wtf(TAG, "NetworkCapabilities subIds are inconsistent between "
                    + "specifier/transportInfo and mSubIds : " + networkCapabilities);
        }
        return subId;
    }

    @VisibleForTesting
    protected int getSubId(int slotIndex) {
        if (SdkLevel.isAtLeastU()) {
            return SubscriptionManager.getSubscriptionId(slotIndex);
        } else {
            SubscriptionManager sm = mContext.getSystemService(SubscriptionManager.class);
            int[] subIds = sm.getSubscriptionIds(slotIndex);
            if (subIds != null && subIds.length > 0) {
                return subIds[0];
            }
            return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }
    }

    @VisibleForTesting
    void updateCarrierServiceUid() {
        synchronized (mLock) {
            SparseArray<CarrierServiceUidWithSubId> copy = mCarrierServiceUidWithSubId.clone();
            mCarrierServiceUidWithSubId.clear();
            for (int i = 0; i < mModemCount; i++) {
                int subId = getSubId(i);
                mCarrierServiceUidWithSubId.put(
                        i,
                        new CarrierServiceUidWithSubId(
                                getCarrierServicePackageUidForSlot(i), subId));
            }
            for (int i = 0; i < copy.size(); ++i) {
                CarrierServiceUidWithSubId oldPair = copy.valueAt(i);
                CarrierServiceUidWithSubId newPair = mCarrierServiceUidWithSubId.get(copy.keyAt(i));
                if (oldPair.mUid != Process.INVALID_UID
                        && oldPair.mSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID
                        && !oldPair.equals(newPair)) {
                    mListener.accept(oldPair.mUid, oldPair.mSubId);
                }
            }
        }
    }

    @VisibleForTesting
    int getCarrierServiceUidForSubId(int subId) {
        synchronized (mLock) {
            for (int i = 0; i < mCarrierServiceUidWithSubId.size(); ++i) {
                if (mCarrierServiceUidWithSubId.valueAt(i).mSubId == subId) {
                    return mCarrierServiceUidWithSubId.valueAt(i).mUid;
                }
            }
            return Process.INVALID_UID;
        }
    }

    @VisibleForTesting
    int getUidForPackage(String pkgName) {
        if (pkgName == null) {
            return Process.INVALID_UID;
        }
        try {
            PackageManager pm = mContext.getPackageManager();
            if (pm != null) {
                ApplicationInfo applicationInfo = pm.getApplicationInfo(pkgName, 0);
                if (applicationInfo != null) {
                    return applicationInfo.uid;
                }
            }
        } catch (PackageManager.NameNotFoundException exception) {
            // Didn't find package. Try other users
            Log.i(TAG, "Unable to find uid for package " + pkgName);
        }
        return Process.INVALID_UID;
    }

    @VisibleForTesting
    int getCarrierServicePackageUidForSlot(int slotId) {
        return getUidForPackage(getCarrierServicePackageNameForLogicalSlot(slotId));
    }

    @VisibleForTesting
    int getSubIdFromTelephonySpecifier(@Nullable final NetworkSpecifier specifier) {
        if (specifier instanceof TelephonyNetworkSpecifier) {
            return ((TelephonyNetworkSpecifier) specifier).getSubscriptionId();
        }
        return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }

    int getSubIdFromWifiTransportInfo(@Nullable final TransportInfo info) {
        if (info instanceof WifiInfo) {
            return ((WifiInfo) info).getSubscriptionId();
        }
        return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }

    // Helper methods to avoid having to deal with UnsupportedApiLevelException.
    private void addCarrierPrivilegesListener(@NonNull final Executor executor,
            @NonNull final PrivilegeListener listener) {
        try {
            mTelephonyManagerShim.addCarrierPrivilegesListener(listener.mLogicalSlot, executor,
                    listener);
        } catch (UnsupportedApiLevelException unsupportedApiLevelException) {
            // Should not happen since CarrierPrivilegeAuthenticator is only used on T+
            Log.e(TAG, "addCarrierPrivilegesListener API is not available");
        }
    }

    private void removeCarrierPrivilegesListener(PrivilegeListener listener) {
        try {
            mTelephonyManagerShim.removeCarrierPrivilegesListener(listener);
        } catch (UnsupportedApiLevelException unsupportedApiLevelException) {
            // Should not happen since CarrierPrivilegeAuthenticator is only used on T+
            Log.e(TAG, "removeCarrierPrivilegesListener API is not available");
        }
    }

    public void dump(IndentingPrintWriter pw) {
        pw.println("CarrierPrivilegeAuthenticator:");
        pw.println("mRequestRestrictedWifiEnabled = " + mRequestRestrictedWifiEnabled);
        synchronized (mLock) {
            for (int i = 0; i < mCarrierServiceUidWithSubId.size(); ++i) {
                final int logicalSlot = mCarrierServiceUidWithSubId.keyAt(i);
                final int serviceUid = mCarrierServiceUidWithSubId.valueAt(i).mUid;
                final int subId = mCarrierServiceUidWithSubId.valueAt(i).mSubId;
                pw.println("Logical slot = " + logicalSlot + " : uid = " + serviceUid
                        + " : subId = " + subId);
            }
        }
    }
}
