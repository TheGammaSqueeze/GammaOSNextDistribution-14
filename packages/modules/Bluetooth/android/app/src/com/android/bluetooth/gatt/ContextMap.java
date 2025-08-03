/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.bluetooth.gatt;

import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertisingSetParameters;
import android.bluetooth.le.PeriodicAdvertisingParameters;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.WorkSource;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.android.bluetooth.BluetoothMethodProxy;
import com.android.bluetooth.le_scan.AppScanStats;
import com.android.internal.annotations.GuardedBy;

import com.google.common.collect.EvictingQueue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * Helper class that keeps track of registered GATT applications.
 * This class manages application callbacks and keeps track of GATT connections.
 * @hide
 */
public class ContextMap<C, T> {
    private static final String TAG = GattServiceConfig.TAG_PREFIX + "ContextMap";

    /**
     * Connection class helps map connection IDs to device addresses.
     */
    public static class Connection {
        public int connId;
        public String address;
        public int appId;
        public long startTime;

        Connection(int connId, String address, int appId) {
            this.connId = connId;
            this.address = address;
            this.appId = appId;
            this.startTime = SystemClock.elapsedRealtime();
        }
    }

    /**
     * Application entry mapping UUIDs to appIDs and callbacks.
     */
    public class App {
        /** The UUID of the application */
        public UUID uuid;

        /** The id of the application */
        public int id;

        /** The package name of the application */
        public String name;

        /** Statistics for this app */
        public AppScanStats appScanStats;

        /** Application callbacks */
        public C callback;

        /** Context information */
        public T info;
        /** Death receipient */
        private IBinder.DeathRecipient mDeathRecipient;

        /** Flag to signal that transport is congested */
        public Boolean isCongested = false;

        /** Whether the calling app has location permission */
        boolean hasLocationPermission;

        /** Whether the calling app has bluetooth privileged permission */
        boolean hasBluetoothPrivilegedPermission;

        /** The user handle of the app that started the scan */
        UserHandle mUserHandle;

        /** Whether the calling app has the network settings permission */
        boolean mHasNetworkSettingsPermission;

        /** Whether the calling app has the network setup wizard permission */
        boolean mHasNetworkSetupWizardPermission;

        /** Whether the calling app has the network setup wizard permission */
        boolean mHasScanWithoutLocationPermission;

        /** Whether the calling app has disavowed the use of bluetooth for location */
        boolean mHasDisavowedLocation;

        boolean mEligibleForSanitizedExposureNotification;

        public List<String> mAssociatedDevices;

        /** Internal callback info queue, waiting to be send on congestion clear */
        private List<CallbackInfo> mCongestionQueue = new ArrayList<CallbackInfo>();

        /**
         * Creates a new app context.
         */
        App(UUID uuid, C callback, T info, String name, AppScanStats appScanStats) {
            this.uuid = uuid;
            this.callback = callback;
            this.info = info;
            this.name = name;
            this.appScanStats = appScanStats;
        }

        /**
         * Creates a new app context for advertiser.
         */
        App(int id, C callback, String name) {
            this.id = id;
            this.callback = callback;
            this.name = name;
        }

        /**
         * Link death recipient
         */
        void linkToDeath(IBinder.DeathRecipient deathRecipient) {
            // It might not be a binder object
            if (callback == null) {
                return;
            }
            try {
                IBinder binder = ((IInterface) callback).asBinder();
                binder.linkToDeath(deathRecipient, 0);
                mDeathRecipient = deathRecipient;
            } catch (RemoteException e) {
                Log.e(TAG, "Unable to link deathRecipient for app id " + id);
            }
        }

        /**
         * Unlink death recipient
         */
        void unlinkToDeath() {
            if (mDeathRecipient != null) {
                try {
                    IBinder binder = ((IInterface) callback).asBinder();
                    binder.unlinkToDeath(mDeathRecipient, 0);
                } catch (NoSuchElementException e) {
                    Log.e(TAG, "Unable to unlink deathRecipient for app id " + id);
                }
            }
        }

        void queueCallback(CallbackInfo callbackInfo) {
            mCongestionQueue.add(callbackInfo);
        }

        CallbackInfo popQueuedCallback() {
            if (mCongestionQueue.size() == 0) {
                return null;
            }
            return mCongestionQueue.remove(0);
        }
    }

    /** Our internal application list */
    private final Object mAppsLock = new Object();
    @GuardedBy("mAppsLock")
    private List<App> mApps = new ArrayList<App>();

    /** Internal map to keep track of logging information by app name */
    private HashMap<Integer, AppScanStats> mAppScanStats = new HashMap<Integer, AppScanStats>();

    /** Internal map to keep track of logging information by advertise id */
    private final Map<Integer, AppAdvertiseStats> mAppAdvertiseStats =
            new HashMap<Integer, AppAdvertiseStats>();

    private static final int ADVERTISE_STATE_MAX_SIZE = 5;

    private final EvictingQueue<AppAdvertiseStats> mLastAdvertises =
            EvictingQueue.create(ADVERTISE_STATE_MAX_SIZE);

    /** Internal list of connected devices **/
    private Set<Connection> mConnections = new HashSet<Connection>();
    private final Object mConnectionsLock = new Object();

    /** Add an entry to the application context list. */
    protected App add(
            UUID uuid,
            WorkSource workSource,
            C callback,
            GattService.PendingIntentInfo piInfo,
            GattService service) {
        int appUid;
        String appName = null;
        if (piInfo != null) {
            appUid = piInfo.callingUid;
            appName = piInfo.callingPackage;
        } else {
            appUid = Binder.getCallingUid();
            appName = service.getPackageManager().getNameForUid(appUid);
        }
        if (appName == null) {
            // Assign an app name if one isn't found
            appName = "Unknown App (UID: " + appUid + ")";
        }
        synchronized (mAppsLock) {
            AppScanStats appScanStats = mAppScanStats.get(appUid);
            if (appScanStats == null) {
                appScanStats = new AppScanStats(appName, workSource, this, service);
                mAppScanStats.put(appUid, appScanStats);
            }
            App app = new App(uuid, callback, (T) piInfo, appName, appScanStats);
            mApps.add(app);
            appScanStats.isRegistered = true;
            return app;
        }
    }

    /**
     * Add an entry to the application context list for advertiser.
     */
    App add(int id, C callback, GattService service) {
        int appUid = Binder.getCallingUid();
        String appName = service.getPackageManager().getNameForUid(appUid);
        if (appName == null) {
            // Assign an app name if one isn't found
            appName = "Unknown App (UID: " + appUid + ")";
        }

        synchronized (mAppsLock) {
            synchronized (this) {
                if (!mAppAdvertiseStats.containsKey(id)) {
                    AppAdvertiseStats appAdvertiseStats = BluetoothMethodProxy.getInstance()
                            .createAppAdvertiseStats(appUid, id, appName, this, service);
                    mAppAdvertiseStats.put(id, appAdvertiseStats);
                }
            }
            App app = getById(appUid);
            if (app == null) {
                app = new App(appUid, callback, appName);
                mApps.add(app);
            }
            return app;
        }
    }

    /**
     * Remove the context for a given UUID
     */
    void remove(UUID uuid) {
        synchronized (mAppsLock) {
            Iterator<App> i = mApps.iterator();
            while (i.hasNext()) {
                App entry = i.next();
                if (entry.uuid.equals(uuid)) {
                    entry.unlinkToDeath();
                    entry.appScanStats.isRegistered = false;
                    i.remove();
                    break;
                }
            }
        }
    }

    /**
     * Remove the context for a given application ID.
     */
    protected void remove(int id) {
        boolean find = false;
        synchronized (mAppsLock) {
            Iterator<App> i = mApps.iterator();
            while (i.hasNext()) {
                App entry = i.next();
                if (entry.id == id) {
                    find = true;
                    entry.unlinkToDeath();
                    entry.appScanStats.isRegistered = false;
                    i.remove();
                    break;
                }
            }
        }
        if (find) {
            removeConnectionsByAppId(id);
        }
    }

    protected List<Integer> getAllAppsIds() {
        List<Integer> appIds = new ArrayList();
        synchronized (mAppsLock) {
            Iterator<App> i = mApps.iterator();
            while (i.hasNext()) {
                App entry = i.next();
                appIds.add(entry.id);
            }
        }
        return appIds;
    }

    /**
     * Add a new connection for a given application ID.
     */
    void addConnection(int id, int connId, String address) {
        synchronized (mConnectionsLock) {
            App entry = getById(id);
            if (entry != null) {
                mConnections.add(new Connection(connId, address, id));
            }
        }
    }

    /**
     * Remove a connection with the given ID.
     */
    void removeConnection(int id, int connId) {
        synchronized (mConnectionsLock) {
            Iterator<Connection> i = mConnections.iterator();
            while (i.hasNext()) {
                Connection connection = i.next();
                if (connection.connId == connId) {
                    i.remove();
                    break;
                }
            }
        }
    }

    /**
     * Remove all connections for a given application ID.
     */
    void removeConnectionsByAppId(int appId) {
        synchronized (mConnectionsLock) {
            Iterator<Connection> i = mConnections.iterator();
            while (i.hasNext()) {
                Connection connection = i.next();
                if (connection.appId == appId) {
                    i.remove();
                }
            }
        }
    }

    /**
     * Get an application context by ID.
     */
    protected App getById(int id) {
        synchronized (mAppsLock) {
            Iterator<App> i = mApps.iterator();
            while (i.hasNext()) {
                App entry = i.next();
                if (entry.id == id) {
                    return entry;
                }
            }
        }
        Log.e(TAG, "Context not found for ID " + id);
        return null;
    }

    /**
     * Get an application context by UUID.
     */
    protected App getByUuid(UUID uuid) {
        synchronized (mAppsLock) {
            Iterator<App> i = mApps.iterator();
            while (i.hasNext()) {
                App entry = i.next();
                if (entry.uuid.equals(uuid)) {
                    return entry;
                }
            }
        }
        Log.e(TAG, "Context not found for UUID " + uuid);
        return null;
    }

    /**
     * Get an application context by the calling Apps name.
     */
    public App getByName(String name) {
        synchronized (mAppsLock) {
            Iterator<App> i = mApps.iterator();
            while (i.hasNext()) {
                App entry = i.next();
                if (entry.name.equals(name)) {
                    return entry;
                }
            }
        }
        Log.e(TAG, "Context not found for name " + name);
        return null;
    }

    /**
     * Get an application context by the context info object.
     */
    protected App getByContextInfo(T contextInfo) {
        synchronized (mAppsLock) {
            Iterator<App> i = mApps.iterator();
            while (i.hasNext()) {
                App entry = i.next();
                if (entry.info != null && entry.info.equals(contextInfo)) {
                    return entry;
                }
            }
        }
        Log.e(TAG, "Context not found for info " + contextInfo);
        return null;
    }

    /**
     * Get Logging info by ID
     */
    protected AppScanStats getAppScanStatsById(int id) {
        App temp = getById(id);
        if (temp != null) {
            return temp.appScanStats;
        }
        return null;
    }

    /**
     * Get Logging info by application UID
     */
    public AppScanStats getAppScanStatsByUid(int uid) {
        return mAppScanStats.get(uid);
    }

    /**
     * Remove the context for a given application ID.
     */
    void removeAppAdvertiseStats(int id) {
        synchronized (this) {
            mAppAdvertiseStats.remove(id);
        }
    }

    /**
     * Get Logging info by ID
     */
    AppAdvertiseStats getAppAdvertiseStatsById(int id) {
        synchronized (this) {
            return mAppAdvertiseStats.get(id);
        }
    }

    /**
     * update the advertiser ID by the regiseter ID
     */
    void setAdvertiserIdByRegId(int regId, int advertiserId) {
        synchronized (this) {
            AppAdvertiseStats stats = mAppAdvertiseStats.get(regId);
            if (stats == null) {
                return;
            }
            stats.setId(advertiserId);
            mAppAdvertiseStats.remove(regId);
            mAppAdvertiseStats.put(advertiserId, stats);
        }
    }

    void recordAdvertiseStart(int id, AdvertisingSetParameters parameters,
            AdvertiseData advertiseData, AdvertiseData scanResponse,
            PeriodicAdvertisingParameters periodicParameters, AdvertiseData periodicData,
            int duration, int maxExtAdvEvents) {
        synchronized (this) {
            AppAdvertiseStats stats = mAppAdvertiseStats.get(id);
            if (stats == null) {
                return;
            }
            stats.recordAdvertiseStart(parameters, advertiseData, scanResponse,
                    periodicParameters, periodicData, duration, maxExtAdvEvents);
            int advertiseInstanceCount = mAppAdvertiseStats.size();
            Log.d(TAG, "advertiseInstanceCount is " + advertiseInstanceCount);
            AppAdvertiseStats.recordAdvertiseInstanceCount(advertiseInstanceCount);
        }
    }

    void recordAdvertiseStop(int id) {
        synchronized (this) {
            AppAdvertiseStats stats = mAppAdvertiseStats.get(id);
            if (stats == null) {
                return;
            }
            stats.recordAdvertiseStop();
            mAppAdvertiseStats.remove(id);
            mLastAdvertises.add(stats);
        }
    }

    void enableAdvertisingSet(int id, boolean enable, int duration, int maxExtAdvEvents) {
        synchronized (this) {
            AppAdvertiseStats stats = mAppAdvertiseStats.get(id);
            if (stats == null) {
                return;
            }
            stats.enableAdvertisingSet(enable, duration, maxExtAdvEvents);
        }
    }

    void setAdvertisingData(int id, AdvertiseData data) {
        synchronized (this) {
            AppAdvertiseStats stats = mAppAdvertiseStats.get(id);
            if (stats == null) {
                return;
            }
            stats.setAdvertisingData(data);
        }
    }

    void setScanResponseData(int id, AdvertiseData data) {
        synchronized (this) {
            AppAdvertiseStats stats = mAppAdvertiseStats.get(id);
            if (stats == null) {
                return;
            }
            stats.setScanResponseData(data);
        }
    }

    void setAdvertisingParameters(int id, AdvertisingSetParameters parameters) {
        synchronized (this) {
            AppAdvertiseStats stats = mAppAdvertiseStats.get(id);
            if (stats == null) {
                return;
            }
            stats.setAdvertisingParameters(parameters);
        }
    }

    void setPeriodicAdvertisingParameters(int id, PeriodicAdvertisingParameters parameters) {
        synchronized (this) {
            AppAdvertiseStats stats = mAppAdvertiseStats.get(id);
            if (stats == null) {
                return;
            }
            stats.setPeriodicAdvertisingParameters(parameters);
        }
    }

    void setPeriodicAdvertisingData(int id, AdvertiseData data) {
        synchronized (this) {
            AppAdvertiseStats stats = mAppAdvertiseStats.get(id);
            if (stats == null) {
                return;
            }
            stats.setPeriodicAdvertisingData(data);
        }
    }

    void onPeriodicAdvertiseEnabled(int id, boolean enable) {
        synchronized (this) {
            AppAdvertiseStats stats = mAppAdvertiseStats.get(id);
            if (stats == null) {
                return;
            }
            stats.onPeriodicAdvertiseEnabled(enable);
        }
    }

    /**
     * Get the device addresses for all connected devices
     */
    Set<String> getConnectedDevices() {
        Set<String> addresses = new HashSet<String>();
        synchronized (mConnectionsLock) {
            Iterator<Connection> i = mConnections.iterator();
            while (i.hasNext()) {
                Connection connection = i.next();
                addresses.add(connection.address);
            }
        }
        return addresses;
    }

    /**
     * Get an application context by a connection ID.
     */
    App getByConnId(int connId) {
        int appId = -1;
        synchronized (mConnectionsLock) {
            Iterator<Connection> ii = mConnections.iterator();
            while (ii.hasNext()) {
                Connection connection = ii.next();
                if (connection.connId == connId) {
                    appId = connection.appId;
                    break;
                }
            }
        }
        if (appId >= 0) {
            return getById(appId);
        }
        return null;
    }

    /**
     * Returns a connection ID for a given device address.
     */
    Integer connIdByAddress(int id, String address) {
        App entry = getById(id);
        if (entry == null) {
            return null;
        }
        synchronized (mConnectionsLock) {
            Iterator<Connection> i = mConnections.iterator();
            while (i.hasNext()) {
                Connection connection = i.next();
                if (connection.address.equalsIgnoreCase(address) && connection.appId == id) {
                    return connection.connId;
                }
            }
        }
        return null;
    }

    /**
     * Returns the device address for a given connection ID.
     */
    String addressByConnId(int connId) {
        synchronized (mConnectionsLock) {
            Iterator<Connection> i = mConnections.iterator();
            while (i.hasNext()) {
                Connection connection = i.next();
                if (connection.connId == connId) {
                    return connection.address;
                }
            }
        }
        return null;
    }

    public List<Connection> getConnectionByApp(int appId) {
        List<Connection> currentConnections = new ArrayList<Connection>();
        synchronized (mConnectionsLock) {
            Iterator<Connection> i = mConnections.iterator();
            while (i.hasNext()) {
                Connection connection = i.next();
                if (connection.appId == appId) {
                    currentConnections.add(connection);
                }
            }
        }
        return currentConnections;
    }

    /**
     * Erases all application context entries.
     */
    protected void clear() {
        synchronized (mAppsLock) {
            Iterator<App> i = mApps.iterator();
            while (i.hasNext()) {
                App entry = i.next();
                entry.unlinkToDeath();
                if (entry.appScanStats != null) {
                    entry.appScanStats.isRegistered = false;
                }
                i.remove();
            }
        }

        synchronized (mConnectionsLock) {
            mConnections.clear();
        }

        synchronized (this) {
            mAppAdvertiseStats.clear();
            mLastAdvertises.clear();
        }
    }

    /**
     * Returns connect device map with addr and appid
     */
    Map<Integer, String> getConnectedMap() {
        Map<Integer, String> connectedmap = new HashMap<Integer, String>();
        synchronized (mConnectionsLock) {
            for (Connection conn : mConnections) {
                connectedmap.put(conn.appId, conn.address);
            }
        }
        return connectedmap;
    }

    /**
     * Logs debug information.
     */
    protected void dump(StringBuilder sb) {
        sb.append("  Entries: " + mAppScanStats.size() + "\n\n");

        Iterator<Map.Entry<Integer, AppScanStats>> it = mAppScanStats.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, AppScanStats> entry = it.next();

            AppScanStats appScanStats = entry.getValue();
            appScanStats.dumpToString(sb);
        }
    }

    /**
     * Logs advertiser debug information.
     */
    void dumpAdvertiser(StringBuilder sb) {
        synchronized (this) {
            if (!mLastAdvertises.isEmpty()) {
                sb.append("\n  last " + mLastAdvertises.size() + " advertising:");
                for (AppAdvertiseStats stats : mLastAdvertises) {
                    AppAdvertiseStats.dumpToString(sb, stats);
                }
                sb.append("\n");
            }

            if (!mAppAdvertiseStats.isEmpty()) {
                sb.append("  Total number of ongoing advertising                   : "
                        + mAppAdvertiseStats.size());
                sb.append("\n  Ongoing advertising:");
                for (Integer key : mAppAdvertiseStats.keySet()) {
                    AppAdvertiseStats stats = mAppAdvertiseStats.get(key);
                    AppAdvertiseStats.dumpToString(sb, stats);
                }
            }
            sb.append("\n");
        }
        Log.d(TAG, sb.toString());
    }
}
