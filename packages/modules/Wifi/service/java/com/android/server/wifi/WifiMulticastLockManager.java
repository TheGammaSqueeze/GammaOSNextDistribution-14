/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.annotation.Nullable;
import android.os.BatteryStatsManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.WorkSource;
import android.util.Log;

import com.android.server.wifi.proto.WifiStatsLog;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * WifiMulticastLockManager tracks holders of multicast locks and
 * triggers enabling and disabling of filtering.
 */
public class WifiMulticastLockManager {
    private static final String TAG = "WifiMulticastLockManager";
    private final List<Multicaster> mMulticasters = new ArrayList<>();
    private int mMulticastEnabled = 0;
    private int mMulticastDisabled = 0;
    private final Handler mHandler;
    private boolean mVerboseLoggingEnabled = false;
    private final BatteryStatsManager mBatteryStats;
    private final ActiveModeWarden mActiveModeWarden;

    /** Delegate for handling state change events for multicast filtering. */
    public interface FilterController {
        /** Called when multicast filtering should be enabled */
        void startFilteringMulticastPackets();

        /** Called when multicast filtering should be disabled */
        void stopFilteringMulticastPackets();
    }

    public WifiMulticastLockManager(
            ActiveModeWarden activeModeWarden,
            BatteryStatsManager batteryStats,
            Looper looper) {
        mBatteryStats = batteryStats;
        mActiveModeWarden = activeModeWarden;
        mHandler = new Handler(looper);

        mActiveModeWarden.registerPrimaryClientModeManagerChangedCallback(
                new PrimaryClientModeManagerChangedCallback());
    }

    private class Multicaster implements IBinder.DeathRecipient {
        String mTag;
        int mUid;
        IBinder mBinder;

        Multicaster(String tag, IBinder binder) {
            mTag = tag;
            mUid = Binder.getCallingUid();
            mBinder = binder;
            try {
                mBinder.linkToDeath(this, 0);
            } catch (RemoteException e) {
                binderDied();
            }
        }

        @Override
        public void binderDied() {
            mHandler.post(() -> {
                Log.e(TAG, "Multicaster binderDied");
                synchronized (mMulticasters) {
                    int i = mMulticasters.indexOf(this);
                    if (i != -1) {
                        removeMulticasterLocked(i, mUid, mTag);
                    }
                }
            });
        }

        void unlinkDeathRecipient() {
            mBinder.unlinkToDeath(this, 0);
        }

        public int getUid() {
            return mUid;
        }

        public String getTag() {
            return mTag;
        }

        public String toString() {
            return "Multicaster{" + mTag + " uid=" + mUid  + "}";
        }
    }

    protected void dump(PrintWriter pw) {
        pw.println("mMulticastEnabled " + mMulticastEnabled);
        pw.println("mMulticastDisabled " + mMulticastDisabled);
        pw.println("Multicast Locks held:");
        synchronized (mMulticasters) {
            for (Multicaster l : mMulticasters) {
                pw.print("    ");
                pw.println(l);
            }
        }
    }

    protected void enableVerboseLogging(boolean verboseEnabled) {
        mVerboseLoggingEnabled = verboseEnabled;
    }

    /** Start filtering if  no multicasters exist. */
    public void initializeFiltering() {
        synchronized (mMulticasters) {
            // if anybody had requested filters be off, leave off
            if (mMulticasters.size() == 0) {
                mActiveModeWarden.getPrimaryClientModeManager()
                        .getMcastLockManagerFilterController()
                        .startFilteringMulticastPackets();
            }
        }
    }

    /**
     * Acquire a multicast lock.
     * @param binder a binder used to ensure caller is still alive
     * @param tag string name of the caller.
     */
    public void acquireLock(IBinder binder, String tag) {
        synchronized (mMulticasters) {
            mMulticastEnabled++;
            mMulticasters.add(new Multicaster(tag, binder));
            // Note that we could call stopFilteringMulticastPackets only when
            // our new size == 1 (first call), but this function won't
            // be called often and by making the stopPacket call each
            // time we're less fragile and self-healing.
            mActiveModeWarden.getPrimaryClientModeManager()
                    .getMcastLockManagerFilterController()
                    .stopFilteringMulticastPackets();
        }

        int uid = Binder.getCallingUid();
        final long ident = Binder.clearCallingIdentity();
        mBatteryStats.reportWifiMulticastEnabled(new WorkSource(uid));
        WifiStatsLog.write_non_chained(
                WifiStatsLog.WIFI_MULTICAST_LOCK_STATE_CHANGED, uid, null,
                WifiStatsLog.WIFI_MULTICAST_LOCK_STATE_CHANGED__STATE__ON, tag);
        Binder.restoreCallingIdentity(ident);
    }

    /** Releases a multicast lock */
    public void releaseLock(String tag) {
        int uid = Binder.getCallingUid();
        synchronized (mMulticasters) {
            mMulticastDisabled++;
            int size = mMulticasters.size();
            for (int i = size - 1; i >= 0; i--) {
                Multicaster m = mMulticasters.get(i);
                if ((m != null) && (m.getUid() == uid) && (m.getTag().equals(tag))) {
                    removeMulticasterLocked(i, uid, tag);
                    break;
                }
            }
        }
    }

    private void removeMulticasterLocked(int i, int uid, String tag) {
        Multicaster removed = mMulticasters.remove(i);

        if (removed != null) {
            removed.unlinkDeathRecipient();
        }
        if (mMulticasters.size() == 0) {
            mActiveModeWarden.getPrimaryClientModeManager()
                    .getMcastLockManagerFilterController()
                    .startFilteringMulticastPackets();
        }

        final long ident = Binder.clearCallingIdentity();
        mBatteryStats.reportWifiMulticastDisabled(new WorkSource(uid));
        WifiStatsLog.write_non_chained(
                WifiStatsLog.WIFI_MULTICAST_LOCK_STATE_CHANGED, uid, null,
                WifiStatsLog.WIFI_MULTICAST_LOCK_STATE_CHANGED__STATE__OFF, tag);
        Binder.restoreCallingIdentity(ident);
    }

    /** Returns whether multicast should be allowed (filtering disabled). */
    public boolean isMulticastEnabled() {
        synchronized (mMulticasters) {
            return mMulticasters.size() > 0;
        }
    }

    private class PrimaryClientModeManagerChangedCallback
            implements ActiveModeWarden.PrimaryClientModeManagerChangedCallback {

        @Override
        public void onChange(
                @Nullable ConcreteClientModeManager prevPrimaryClientModeManager,
                @Nullable ConcreteClientModeManager newPrimaryClientModeManager) {
            if (prevPrimaryClientModeManager != null) {
                // no longer primary => start filtering out multicast packets
                prevPrimaryClientModeManager.getMcastLockManagerFilterController()
                        .startFilteringMulticastPackets();
            }
            if (newPrimaryClientModeManager != null
                    && isMulticastEnabled()) { // this call is synchronized
                // new primary and multicast enabled => stop filtering out multicast packets
                newPrimaryClientModeManager.getMcastLockManagerFilterController()
                        .stopFilteringMulticastPackets();
            }
        }
    }
}
