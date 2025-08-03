/*
 * Copyright (C) 2014 The Android Open Source Project
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

import static android.Manifest.permission.CHANGE_NETWORK_STATE;
import static android.Manifest.permission.CONNECTIVITY_USE_RESTRICTED_NETWORKS;
import static android.Manifest.permission.INTERNET;
import static android.Manifest.permission.NETWORK_STACK;
import static android.Manifest.permission.UPDATE_DEVICE_STATS;
import static android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED;
import static android.content.pm.PackageManager.GET_PERMISSIONS;
import static android.net.ConnectivitySettingsManager.UIDS_ALLOWED_ON_RESTRICTED_NETWORKS;
import static android.net.INetd.PERMISSION_INTERNET;
import static android.net.INetd.PERMISSION_NETWORK;
import static android.net.INetd.PERMISSION_NONE;
import static android.net.INetd.PERMISSION_SYSTEM;
import static android.net.INetd.PERMISSION_UNINSTALLED;
import static android.net.INetd.PERMISSION_UPDATE_DEVICE_STATS;
import static android.net.NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK;
import static android.os.Process.INVALID_UID;
import static android.os.Process.SYSTEM_UID;

import static com.android.net.module.util.CollectionUtils.toIntArray;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.ContentObserver;
import android.net.ConnectivitySettingsManager;
import android.net.INetd;
import android.net.UidRange;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.os.SystemConfigManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseIntArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.IndentingPrintWriter;
import com.android.modules.utils.build.SdkLevel;
import com.android.net.module.util.CollectionUtils;
import com.android.net.module.util.SharedLog;
import com.android.networkstack.apishim.ProcessShimImpl;
import com.android.networkstack.apishim.common.ProcessShim;
import com.android.server.BpfNetMaps;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A utility class to inform Netd of UID permissions.
 * Does a mass update at boot and then monitors for app install/remove.
 *
 * @hide
 */
public class PermissionMonitor {
    private static final String TAG = "PermissionMonitor";
    private static final boolean DBG = true;
    private static final int VERSION_Q = Build.VERSION_CODES.Q;

    private final PackageManager mPackageManager;
    private final UserManager mUserManager;
    private final SystemConfigManager mSystemConfigManager;
    private final INetd mNetd;
    private final Dependencies mDeps;
    private final Context mContext;
    private final BpfNetMaps mBpfNetMaps;
    private final HandlerThread mThread;

    private static final ProcessShim sProcessShim = ProcessShimImpl.newInstance();

    @GuardedBy("this")
    private final Set<UserHandle> mUsers = new HashSet<>();

    // Keys are uids. Values are netd network permissions.
    @GuardedBy("this")
    private final SparseIntArray mUidToNetworkPerm = new SparseIntArray();

    // NonNull keys are active non-bypassable and fully-routed VPN's interface name, Values are uid
    // ranges for apps under the VPNs which enable interface filtering.
    // If key is null, Values are uid ranges for apps under the VPNs which are connected but do not
    // enable interface filtering.
    @GuardedBy("this")
    private final Map<String, Set<UidRange>> mVpnInterfaceUidRanges = new ArrayMap<>();

    // Items are uid ranges for apps under the VPN Lockdown
    // Ranges were given through ConnectivityManager#setRequireVpnForUids, and ranges are allowed to
    // have duplicates. Also, it is allowed to give ranges that are already subject to lockdown.
    // So we need to maintain uid range with multiset.
    @GuardedBy("this")
    private final MultiSet<UidRange> mVpnLockdownUidRanges = new MultiSet<>();

    // A set of appIds for apps across all users on the device. We track appIds instead of uids
    // directly to reduce its size and also eliminate the need to update this set when user is
    // added/removed.
    @GuardedBy("this")
    private final Set<Integer> mAllApps = new HashSet<>();

    // A set of uids which are allowed to use restricted networks. The packages of these uids can't
    // hold the CONNECTIVITY_USE_RESTRICTED_NETWORKS permission because they can't be
    // signature|privileged apps. However, these apps should still be able to use restricted
    // networks under certain conditions (e.g. government app using emergency services). So grant
    // netd system permission to these uids which is listed in UIDS_ALLOWED_ON_RESTRICTED_NETWORKS.
    @GuardedBy("this")
    private final Set<Integer> mUidsAllowedOnRestrictedNetworks = new ArraySet<>();

    // Store PackageManager for each user.
    // Keys are users, Values are PackageManagers which get from each user.
    @GuardedBy("this")
    private final Map<UserHandle, PackageManager> mUsersPackageManager = new ArrayMap<>();

    // Store appIds traffic permissions for each user.
    // Keys are users, Values are SparseArrays where each entry maps an appId to the permissions
    // that appId has within that user. The permissions are a bitmask of PERMISSION_INTERNET and
    // PERMISSION_UPDATE_DEVICE_STATS, or 0 (PERMISSION_NONE) if the app has neither of those
    // permissions. They can never be PERMISSION_UNINSTALLED.
    @GuardedBy("this")
    private final Map<UserHandle, SparseIntArray> mUsersTrafficPermissions = new ArrayMap<>();

    private static final int SYSTEM_APPID = SYSTEM_UID;

    private static final int MAX_PERMISSION_UPDATE_LOGS = 40;
    private final SharedLog mPermissionUpdateLogs = new SharedLog(MAX_PERMISSION_UPDATE_LOGS, TAG);

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (Intent.ACTION_PACKAGE_ADDED.equals(action)) {
                final int uid = intent.getIntExtra(Intent.EXTRA_UID, -1);
                final Uri packageData = intent.getData();
                final String packageName =
                        packageData != null ? packageData.getSchemeSpecificPart() : null;
                onPackageAdded(packageName, uid);
            } else if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
                final int uid = intent.getIntExtra(Intent.EXTRA_UID, -1);
                final Uri packageData = intent.getData();
                final String packageName =
                        packageData != null ? packageData.getSchemeSpecificPart() : null;
                onPackageRemoved(packageName, uid);
            } else if (Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE.equals(action)) {
                final String[] pkgList =
                        intent.getStringArrayExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST);
                onExternalApplicationsAvailable(pkgList);
            } else if (Intent.ACTION_USER_ADDED.equals(action)) {
                final UserHandle user = intent.getParcelableExtra(Intent.EXTRA_USER);
                // User should be filled for below intents, check the existence.
                if (user == null) {
                    Log.wtf(TAG, action + " broadcast without EXTRA_USER");
                    return;
                }
                onUserAdded(user);
            } else if (Intent.ACTION_USER_REMOVED.equals(action)) {
                final UserHandle user = intent.getParcelableExtra(Intent.EXTRA_USER);
                // User should be filled for below intents, check the existence.
                if (user == null) {
                    Log.wtf(TAG, action + " broadcast without EXTRA_USER");
                    return;
                }
                onUserRemoved(user);
            } else {
                Log.wtf(TAG, "received unexpected intent: " + action);
            }
        }
    };

    /**
     * Dependencies of PermissionMonitor, for injection in tests.
     */
    @VisibleForTesting
    public static class Dependencies {
        /**
         * Get device first sdk version.
         */
        public int getDeviceFirstSdkInt() {
            return Build.VERSION.DEVICE_INITIAL_SDK_INT;
        }

        /**
         * Get uids allowed to use restricted networks via ConnectivitySettingsManager.
         */
        public Set<Integer> getUidsAllowedOnRestrictedNetworks(@NonNull Context context) {
            return ConnectivitySettingsManager.getUidsAllowedOnRestrictedNetworks(context);
        }

        /**
         * Register ContentObserver for given Uri.
         */
        public void registerContentObserver(@NonNull Context context, @NonNull Uri uri,
                boolean notifyForDescendants, @NonNull ContentObserver observer) {
            context.getContentResolver().registerContentObserver(
                    uri, notifyForDescendants, observer);
        }
    }

    private static class MultiSet<T> {
        private final Map<T, Integer> mMap = new ArrayMap<>();

        /**
         * Returns the number of key in the set before this addition.
         */
        public int add(T key) {
            final int oldCount = mMap.getOrDefault(key, 0);
            mMap.put(key, oldCount + 1);
            return oldCount;
        }

        /**
         * Return the number of key in the set before this removal.
         */
        public int remove(T key) {
            final int oldCount = mMap.getOrDefault(key, 0);
            if (oldCount == 0) {
                Log.wtf(TAG, "Attempt to remove non existing key = " + key.toString());
            } else if (oldCount == 1) {
                mMap.remove(key);
            } else {
                mMap.put(key, oldCount - 1);
            }
            return oldCount;
        }

        public Set<T> getSet() {
            return mMap.keySet();
        }
    }

    public PermissionMonitor(@NonNull final Context context, @NonNull final INetd netd,
            @NonNull final BpfNetMaps bpfNetMaps, @NonNull final HandlerThread thread) {
        this(context, netd, bpfNetMaps, new Dependencies(), thread);
    }

    @VisibleForTesting
    PermissionMonitor(@NonNull final Context context, @NonNull final INetd netd,
            @NonNull final BpfNetMaps bpfNetMaps,
            @NonNull final Dependencies deps,
            @NonNull final HandlerThread thread) {
        mPackageManager = context.getPackageManager();
        mUserManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
        mSystemConfigManager = context.getSystemService(SystemConfigManager.class);
        mNetd = netd;
        mDeps = deps;
        mContext = context;
        mBpfNetMaps = bpfNetMaps;
        mThread = thread;
    }

    private void ensureRunningOnHandlerThread() {
        if (mThread.getLooper().getThread() != Thread.currentThread()) {
            throw new IllegalStateException(
                    "Not running on Handler thread: " + Thread.currentThread().getName());
        }
    }

    private int getPackageNetdNetworkPermission(@NonNull final PackageInfo app) {
        if (hasRestrictedNetworkPermission(app)) {
            return PERMISSION_SYSTEM;
        }
        if (hasNetworkPermission(app)) {
            return PERMISSION_NETWORK;
        }
        return PERMISSION_NONE;
    }

    static boolean isHigherNetworkPermission(final int targetPermission,
            final int currentPermission) {
        // This is relied on strict order of network permissions (SYSTEM > NETWORK > NONE), and it
        // is enforced in tests.
        return targetPermission > currentPermission;
    }

    private List<PackageInfo> getInstalledPackagesAsUser(final UserHandle user) {
        return mPackageManager.getInstalledPackagesAsUser(GET_PERMISSIONS, user.getIdentifier());
    }

    private synchronized void updateAllApps(final List<PackageInfo> apps) {
        for (PackageInfo app : apps) {
            final int appId = app.applicationInfo != null
                    ? UserHandle.getAppId(app.applicationInfo.uid) : INVALID_UID;
            if (appId < 0) {
                continue;
            }
            mAllApps.add(appId);
        }
    }

    private static boolean hasSdkSandbox(final int uid) {
        return SdkLevel.isAtLeastT() && Process.isApplicationUid(uid);
    }

    // Return the network permission for the passed list of apps. Note that this depends on the
    // current settings of the device (See isUidAllowedOnRestrictedNetworks).
    private SparseIntArray makeUidsNetworkPerm(final List<PackageInfo> apps) {
        final SparseIntArray uidsPerm = new SparseIntArray();
        for (PackageInfo app : apps) {
            final int uid = app.applicationInfo != null ? app.applicationInfo.uid : INVALID_UID;
            if (uid < 0) {
                continue;
            }
            final int permission = getPackageNetdNetworkPermission(app);
            if (isHigherNetworkPermission(permission, uidsPerm.get(uid, PERMISSION_NONE))) {
                uidsPerm.put(uid, permission);
                if (hasSdkSandbox(uid)) {
                    int sdkSandboxUid = sProcessShim.toSdkSandboxUid(uid);
                    uidsPerm.put(sdkSandboxUid, permission);
                }
            }
        }
        return uidsPerm;
    }

    private static SparseIntArray makeAppIdsTrafficPerm(final List<PackageInfo> apps) {
        final SparseIntArray appIdsPerm = new SparseIntArray();
        for (PackageInfo app : apps) {
            final int appId = app.applicationInfo != null
                    ? UserHandle.getAppId(app.applicationInfo.uid) : INVALID_UID;
            if (appId < 0) {
                continue;
            }
            final int otherNetdPerms = getNetdPermissionMask(app.requestedPermissions,
                    app.requestedPermissionsFlags);
            final int permission = appIdsPerm.get(appId) | otherNetdPerms;
            appIdsPerm.put(appId, permission);
            if (hasSdkSandbox(appId)) {
                appIdsPerm.put(sProcessShim.toSdkSandboxUid(appId), permission);
            }
        }
        return appIdsPerm;
    }

    private synchronized void updateUidsNetworkPermission(final SparseIntArray uids) {
        for (int i = 0; i < uids.size(); i++) {
            mUidToNetworkPerm.put(uids.keyAt(i), uids.valueAt(i));
        }
        sendUidsNetworkPermission(uids, true /* add */);
    }

    /**
     * Calculates permissions for appIds.
     * Maps each appId to the union of all traffic permissions that the appId has in all users.
     *
     * @return The appIds traffic permissions.
     */
    private synchronized SparseIntArray makeAppIdsTrafficPermForAllUsers() {
        final SparseIntArray appIds = new SparseIntArray();
        // Check appIds permissions from each user.
        for (UserHandle user : mUsersTrafficPermissions.keySet()) {
            final SparseIntArray userAppIds = mUsersTrafficPermissions.get(user);
            for (int i = 0; i < userAppIds.size(); i++) {
                final int appId = userAppIds.keyAt(i);
                final int permission = userAppIds.valueAt(i);
                appIds.put(appId, appIds.get(appId) | permission);
            }
        }
        return appIds;
    }

    private SparseIntArray getSystemTrafficPerm() {
        final SparseIntArray appIdsPerm = new SparseIntArray();
        for (final int uid : mSystemConfigManager.getSystemPermissionUids(INTERNET)) {
            final int appId = UserHandle.getAppId(uid);
            final int permission = appIdsPerm.get(appId) | PERMISSION_INTERNET;
            appIdsPerm.put(appId, permission);
            if (hasSdkSandbox(appId)) {
                appIdsPerm.put(sProcessShim.toSdkSandboxUid(appId), permission);
            }
        }
        for (final int uid : mSystemConfigManager.getSystemPermissionUids(UPDATE_DEVICE_STATS)) {
            final int appId = UserHandle.getAppId(uid);
            final int permission = appIdsPerm.get(appId) | PERMISSION_UPDATE_DEVICE_STATS;
            appIdsPerm.put(appId, permission);
            if (hasSdkSandbox(appId)) {
                appIdsPerm.put(sProcessShim.toSdkSandboxUid(appId), permission);
            }
        }
        return appIdsPerm;
    }

    // Intended to be called only once at startup, after the system is ready. Installs a broadcast
    // receiver to monitor ongoing UID changes, so this shouldn't/needn't be called again.
    public synchronized void startMonitoring() {
        log("Monitoring");

        final Handler handler = new Handler(mThread.getLooper());
        final Context userAllContext = mContext.createContextAsUser(UserHandle.ALL, 0 /* flags */);
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        intentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        intentFilter.addDataScheme("package");
        userAllContext.registerReceiver(
                mIntentReceiver, intentFilter, null /* broadcastPermission */, handler);

        // Listen to EXTERNAL_APPLICATIONS_AVAILABLE is that an app becoming available means it may
        // need to gain a permission. But an app that becomes unavailable can neither gain nor lose
        // permissions on that account, it just can no longer run. Thus, doesn't need to listen to
        // EXTERNAL_APPLICATIONS_UNAVAILABLE.
        final IntentFilter externalIntentFilter =
                new IntentFilter(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
        userAllContext.registerReceiver(
                mIntentReceiver, externalIntentFilter, null /* broadcastPermission */, handler);

        // Listen for user add/remove.
        final IntentFilter userIntentFilter = new IntentFilter();
        userIntentFilter.addAction(Intent.ACTION_USER_ADDED);
        userIntentFilter.addAction(Intent.ACTION_USER_REMOVED);
        userAllContext.registerReceiver(
                mIntentReceiver, userIntentFilter, null /* broadcastPermission */, handler);

        // Register UIDS_ALLOWED_ON_RESTRICTED_NETWORKS setting observer
        mDeps.registerContentObserver(
                userAllContext,
                Settings.Global.getUriFor(UIDS_ALLOWED_ON_RESTRICTED_NETWORKS),
                false /* notifyForDescendants */,
                new ContentObserver(handler) {
                    @Override
                    public void onChange(boolean selfChange) {
                        onSettingChanged();
                    }
                });

        // Read UIDS_ALLOWED_ON_RESTRICTED_NETWORKS setting and update
        // mUidsAllowedOnRestrictedNetworks.
        updateUidsAllowedOnRestrictedNetworks(mDeps.getUidsAllowedOnRestrictedNetworks(mContext));

        // Read system traffic permissions when a user removed and put them to USER_ALL because they
        // are not specific to any particular user.
        mUsersTrafficPermissions.put(UserHandle.ALL, getSystemTrafficPerm());

        final List<UserHandle> usrs = mUserManager.getUserHandles(true /* excludeDying */);
        // Update netd permissions for all users.
        for (UserHandle user : usrs) {
            onUserAdded(user);
        }
        log("Users: " + mUsers.size() + ", UidToNetworkPerm: " + mUidToNetworkPerm.size());
    }

    @VisibleForTesting
    synchronized void updateUidsAllowedOnRestrictedNetworks(final Set<Integer> uids) {
        mUidsAllowedOnRestrictedNetworks.clear();
        mUidsAllowedOnRestrictedNetworks.addAll(uids);
    }

    @VisibleForTesting
    static boolean isVendorApp(@NonNull ApplicationInfo appInfo) {
        return appInfo.isVendor() || appInfo.isOem() || appInfo.isProduct();
    }

    @VisibleForTesting
    boolean isCarryoverPackage(final ApplicationInfo appInfo) {
        if (appInfo == null) return false;
        return (appInfo.targetSdkVersion < VERSION_Q && isVendorApp(appInfo))
                // Backward compatibility for b/114245686, on devices that launched before Q daemons
                // and apps running as the system UID are exempted from this check.
                || (UserHandle.getAppId(appInfo.uid) == SYSTEM_APPID
                        && mDeps.getDeviceFirstSdkInt() < VERSION_Q);
    }

    @VisibleForTesting
    synchronized boolean isUidAllowedOnRestrictedNetworks(final ApplicationInfo appInfo) {
        if (appInfo == null) return false;
        // Check whether package's uid is in allowed on restricted networks uid list. If so, this
        // uid can have netd system permission.
        return isUidAllowedOnRestrictedNetworks(appInfo.uid);
    }

    /**
     * Returns whether the given uid is in allowed on restricted networks list.
     */
    public synchronized boolean isUidAllowedOnRestrictedNetworks(final int uid) {
        return mUidsAllowedOnRestrictedNetworks.contains(uid);
    }

    @VisibleForTesting
    boolean hasPermission(@NonNull final PackageInfo app, @NonNull final String permission) {
        if (app.requestedPermissions == null || app.requestedPermissionsFlags == null) {
            return false;
        }
        final int index = CollectionUtils.indexOf(app.requestedPermissions, permission);
        if (index < 0 || index >= app.requestedPermissionsFlags.length) return false;
        return (app.requestedPermissionsFlags[index] & REQUESTED_PERMISSION_GRANTED) != 0;
    }

    @VisibleForTesting
    boolean hasNetworkPermission(@NonNull final PackageInfo app) {
        return hasPermission(app, CHANGE_NETWORK_STATE);
    }

    @VisibleForTesting
    boolean hasRestrictedNetworkPermission(@NonNull final PackageInfo app) {
        // TODO : remove carryover package check in the future(b/31479477). All apps should just
        //  request the appropriate permission for their use case since android Q.
        return isCarryoverPackage(app.applicationInfo)
                || isUidAllowedOnRestrictedNetworks(app.applicationInfo)
                || hasPermission(app, PERMISSION_MAINLINE_NETWORK_STACK)
                || hasPermission(app, NETWORK_STACK)
                || hasPermission(app, CONNECTIVITY_USE_RESTRICTED_NETWORKS);
    }

    /** Returns whether the given uid has using background network permission. */
    public synchronized boolean hasUseBackgroundNetworksPermission(final int uid) {
        // Apps with any of the CHANGE_NETWORK_STATE, NETWORK_STACK, CONNECTIVITY_INTERNAL or
        // CONNECTIVITY_USE_RESTRICTED_NETWORKS permission has the permission to use background
        // networks. mUidToNetworkPerm contains the result of checks for hasNetworkPermission and
        // hasRestrictedNetworkPermission, as well as the list of UIDs allowed on restricted
        // networks. If uid is in the mUidToNetworkPerm list that means uid has one of permissions
        // at least.
        return mUidToNetworkPerm.get(uid, PERMISSION_NONE) != PERMISSION_NONE;
    }

    /**
     * Returns whether the given uid has permission to use restricted networks.
     */
    public synchronized boolean hasRestrictedNetworksPermission(int uid) {
        return PERMISSION_SYSTEM == mUidToNetworkPerm.get(uid, PERMISSION_NONE);
    }

    private void sendUidsNetworkPermission(SparseIntArray uids, boolean add) {
        ensureRunningOnHandlerThread();
        List<Integer> network = new ArrayList<>();
        List<Integer> system = new ArrayList<>();
        for (int i = 0; i < uids.size(); i++) {
            final int permission = uids.valueAt(i);
            if (PERMISSION_NONE == permission) {
                continue; // Normally NONE is not stored in this map, but just in case
            }
            List<Integer> list = (PERMISSION_SYSTEM == permission) ? system : network;
            list.add(uids.keyAt(i));
        }
        try {
            if (add) {
                mNetd.networkSetPermissionForUser(PERMISSION_NETWORK, toIntArray(network));
                mNetd.networkSetPermissionForUser(PERMISSION_SYSTEM, toIntArray(system));
            } else {
                mNetd.networkClearPermissionForUser(toIntArray(network));
                mNetd.networkClearPermissionForUser(toIntArray(system));
            }
        } catch (RemoteException e) {
            loge("Exception when updating permissions: " + e);
        }
    }

    /**
     * Called when a user is added. See {link #ACTION_USER_ADDED}.
     *
     * @param user The integer userHandle of the added user. See {@link #EXTRA_USER_HANDLE}.
     *
     * @hide
     */
    @VisibleForTesting
    synchronized void onUserAdded(@NonNull UserHandle user) {
        mUsers.add(user);

        final List<PackageInfo> apps = getInstalledPackagesAsUser(user);

        // Save all apps
        updateAllApps(apps);

        // Uids network permissions
        final SparseIntArray uids = makeUidsNetworkPerm(apps);
        updateUidsNetworkPermission(uids);

        // Add new user appIds permissions.
        final SparseIntArray addedUserAppIds = makeAppIdsTrafficPerm(apps);
        mUsersTrafficPermissions.put(user, addedUserAppIds);
        // Generate appIds from all users and send result to netd.
        final SparseIntArray appIds = makeAppIdsTrafficPermForAllUsers();
        sendAppIdsTrafficPermission(appIds);

        // Log user added
        mPermissionUpdateLogs.log("New user(" + user.getIdentifier() + ") added: nPerm uids="
                + uids + ", tPerm appIds=" + addedUserAppIds);
    }

    /**
     * Called when an user is removed. See {link #ACTION_USER_REMOVED}.
     *
     * @param user The integer userHandle of the removed user. See {@link #EXTRA_USER_HANDLE}.
     *
     * @hide
     */
    @VisibleForTesting
    synchronized void onUserRemoved(@NonNull UserHandle user) {
        mUsers.remove(user);

        // Remove uids network permissions that belongs to the user.
        final SparseIntArray removedUids = new SparseIntArray();
        final SparseIntArray allUids = mUidToNetworkPerm.clone();
        for (int i = 0; i < allUids.size(); i++) {
            final int uid = allUids.keyAt(i);
            if (user.equals(UserHandle.getUserHandleForUid(uid))) {
                mUidToNetworkPerm.delete(uid);
                removedUids.put(uid, allUids.valueAt(i));
            }
        }
        sendUidsNetworkPermission(removedUids, false /* add */);

        // Remove appIds traffic permission that belongs to the user
        final SparseIntArray removedUserAppIds = mUsersTrafficPermissions.remove(user);
        // Generate appIds from the remaining users.
        final SparseIntArray appIds = makeAppIdsTrafficPermForAllUsers();

        if (removedUserAppIds == null) {
            Log.wtf(TAG, "onUserRemoved: Receive unknown user=" + user);
            return;
        }

        // Clear permission on those appIds belong to this user only, set the permission to
        // PERMISSION_UNINSTALLED.
        for (int i = 0; i < removedUserAppIds.size(); i++) {
            final int appId = removedUserAppIds.keyAt(i);
            // Need to clear permission if the removed appId is not found in the array.
            if (appIds.indexOfKey(appId) < 0) {
                appIds.put(appId, PERMISSION_UNINSTALLED);
            }
        }
        sendAppIdsTrafficPermission(appIds);

        // Log user removed
        mPermissionUpdateLogs.log("User(" + user.getIdentifier() + ") removed: nPerm uids="
                + removedUids + ", tPerm appIds=" + removedUserAppIds);
    }

    /**
     * Compare the current network permission and the given package's permission to find out highest
     * permission for the uid.
     *
     * @param uid The target uid
     * @param currentPermission Current uid network permission
     * @param name The package has same uid that need compare its permission to update uid network
     *             permission.
     */
    @VisibleForTesting
    protected int highestPermissionForUid(int uid, int currentPermission, String name) {
        // If multiple packages share a UID (cf: android:sharedUserId) and ask for different
        // permissions, don't downgrade (i.e., if it's already SYSTEM, leave it as is).
        if (currentPermission == PERMISSION_SYSTEM) {
            return currentPermission;
        }
        final PackageInfo app = getPackageInfoAsUser(name, UserHandle.getUserHandleForUid(uid));
        if (app == null) return currentPermission;

        final int permission = getPackageNetdNetworkPermission(app);
        if (isHigherNetworkPermission(permission, currentPermission)) {
            return permission;
        }
        return currentPermission;
    }

    private int getTrafficPermissionForUid(final int uid) {
        int permission = PERMISSION_NONE;
        // Check all the packages for this UID. The UID has the permission if any of the
        // packages in it has the permission.
        final String[] packages = mPackageManager.getPackagesForUid(uid);
        if (packages != null && packages.length > 0) {
            for (String name : packages) {
                final PackageInfo app = getPackageInfoAsUser(name,
                        UserHandle.getUserHandleForUid(uid));
                if (app != null && app.requestedPermissions != null) {
                    permission |= getNetdPermissionMask(app.requestedPermissions,
                            app.requestedPermissionsFlags);
                }
            }
        } else {
            // The last package of this uid is removed from device. Clean the package up.
            permission = PERMISSION_UNINSTALLED;
        }
        return permission;
    }

    private synchronized void updateVpnUid(int uid, boolean add) {
        // Apps that can use restricted networks can always bypass VPNs.
        if (hasRestrictedNetworksPermission(uid)) {
            return;
        }
        for (Map.Entry<String, Set<UidRange>> vpn : mVpnInterfaceUidRanges.entrySet()) {
            if (UidRange.containsUid(vpn.getValue(), uid)) {
                final Set<Integer> changedUids = new HashSet<>();
                changedUids.add(uid);
                updateVpnUidsInterfaceRules(vpn.getKey(), changedUids, add);
            }
        }
    }

    private synchronized void updateLockdownUid(int uid, boolean add) {
        // Apps that can use restricted networks can always bypass VPNs.
        if (hasRestrictedNetworksPermission(uid)) {
            return;
        }

        if (UidRange.containsUid(mVpnLockdownUidRanges.getSet(), uid)) {
            updateLockdownUidRule(uid, add);
        }
    }

    /**
     * This handles both network and traffic permission, because there is no overlap in actual
     * values, where network permission is NETWORK or SYSTEM, and traffic permission is INTERNET
     * or UPDATE_DEVICE_STATS
     */
    private String permissionToString(int permission) {
        switch (permission) {
            case PERMISSION_NONE:
                return "NONE";
            case PERMISSION_NETWORK:
                return "NETWORK";
            case PERMISSION_SYSTEM:
                return "SYSTEM";
            case PERMISSION_INTERNET:
                return "INTERNET";
            case PERMISSION_UPDATE_DEVICE_STATS:
                return "UPDATE_DEVICE_STATS";
            case (PERMISSION_INTERNET | PERMISSION_UPDATE_DEVICE_STATS):
                return "ALL";
            case PERMISSION_UNINSTALLED:
                return "UNINSTALLED";
            default:
                return "UNKNOWN";
        }
    }

    private synchronized void updateAppIdTrafficPermission(int uid) {
        final int uidTrafficPerm = getTrafficPermissionForUid(uid);
        final SparseIntArray userTrafficPerms =
                mUsersTrafficPermissions.get(UserHandle.getUserHandleForUid(uid));
        if (userTrafficPerms == null) {
            Log.wtf(TAG, "Can't get user traffic permission from uid=" + uid);
            return;
        }
        // Do not put PERMISSION_UNINSTALLED into the array. If no package left on the uid
        // (PERMISSION_UNINSTALLED), remove the appId from the array. Otherwise, update the latest
        // permission to the appId.
        final int appId = UserHandle.getAppId(uid);
        if (uidTrafficPerm == PERMISSION_UNINSTALLED) {
            userTrafficPerms.delete(appId);
        } else {
            userTrafficPerms.put(appId, uidTrafficPerm);
        }
    }

    private synchronized int getAppIdTrafficPermission(int appId) {
        int permission = PERMISSION_NONE;
        boolean installed = false;
        for (UserHandle user : mUsersTrafficPermissions.keySet()) {
            final SparseIntArray userApps = mUsersTrafficPermissions.get(user);
            final int appIdx = userApps.indexOfKey(appId);
            if (appIdx >= 0) {
                permission |= userApps.valueAt(appIdx);
                installed = true;
            }
        }
        return installed ? permission : PERMISSION_UNINSTALLED;
    }

    /**
     * Called when a package is added.
     *
     * @param packageName The name of the new package.
     * @param uid The uid of the new package.
     *
     * @hide
     */
    @VisibleForTesting
    synchronized void onPackageAdded(@NonNull final String packageName, final int uid) {
        // Update uid permission.
        updateAppIdTrafficPermission(uid);
        // Get the appId permission from all users then send the latest permission to netd.
        final int appId = UserHandle.getAppId(uid);
        final int appIdTrafficPerm = getAppIdTrafficPermission(appId);
        sendPackagePermissionsForAppId(appId, appIdTrafficPerm);

        final int currentPermission = mUidToNetworkPerm.get(uid, PERMISSION_NONE);
        final int permission = highestPermissionForUid(uid, currentPermission, packageName);
        if (permission != currentPermission) {
            mUidToNetworkPerm.put(uid, permission);

            SparseIntArray apps = new SparseIntArray();
            apps.put(uid, permission);

            if (hasSdkSandbox(uid)) {
                int sdkSandboxUid = sProcessShim.toSdkSandboxUid(uid);
                mUidToNetworkPerm.put(sdkSandboxUid, permission);
                apps.put(sdkSandboxUid, permission);
            }
            sendUidsNetworkPermission(apps, true /* add */);
        }

        // If the newly-installed package falls within some VPN's uid range, update Netd with it.
        // This needs to happen after the mUidToNetworkPerm update above, since
        // hasRestrictedNetworksPermission() in updateVpnUid() and updateLockdownUid() depends on
        // mUidToNetworkPerm to check if the package can bypass VPN.
        updateVpnUid(uid, true /* add */);
        updateLockdownUid(uid, true /* add */);
        mAllApps.add(appId);

        // Log package added.
        mPermissionUpdateLogs.log("Package add: uid=" + uid
                + ", nPerm=(" + permissionToString(permission) + "/"
                + permissionToString(currentPermission) + ")"
                + ", tPerm=" + permissionToString(appIdTrafficPerm));
    }

    private int highestUidNetworkPermission(int uid) {
        int permission = PERMISSION_NONE;
        final String[] packages = mPackageManager.getPackagesForUid(uid);
        if (!CollectionUtils.isEmpty(packages)) {
            for (String name : packages) {
                // If multiple packages have the same UID, give the UID all permissions that
                // any package in that UID has.
                permission = highestPermissionForUid(uid, permission, name);
                if (permission == PERMISSION_SYSTEM) {
                    break;
                }
            }
        }
        return permission;
    }

    /**
     * Called when a package is removed.
     *
     * @param packageName The name of the removed package or null.
     * @param uid containing the integer uid previously assigned to the package.
     *
     * @hide
     */
    @VisibleForTesting
    synchronized void onPackageRemoved(@NonNull final String packageName, final int uid) {
        // Update uid permission.
        updateAppIdTrafficPermission(uid);
        // Get the appId permission from all users then send the latest permission to netd.
        final int appId = UserHandle.getAppId(uid);
        final int appIdTrafficPerm = getAppIdTrafficPermission(appId);
        sendPackagePermissionsForAppId(appId, appIdTrafficPerm);

        // If the newly-removed package falls within some VPN's uid range, update Netd with it.
        // This needs to happen before the mUidToNetworkPerm update below, since
        // hasRestrictedNetworksPermission() in updateVpnUid() and updateLockdownUid() depends on
        // mUidToNetworkPerm to check if the package can bypass VPN.
        updateVpnUid(uid, false /* add */);
        updateLockdownUid(uid, false /* add */);
        // If the package has been removed from all users on the device, clear it form mAllApps.
        if (mPackageManager.getNameForUid(uid) == null) {
            mAllApps.remove(appId);
        }

        final int currentPermission = mUidToNetworkPerm.get(uid, PERMISSION_NONE);
        final int permission = highestUidNetworkPermission(uid);

        // Log package removed.
        mPermissionUpdateLogs.log("Package remove: uid=" + uid
                + ", nPerm=(" + permissionToString(permission) + "/"
                + permissionToString(currentPermission) + ")"
                + ", tPerm=" + permissionToString(appIdTrafficPerm));

        if (permission != currentPermission) {
            final SparseIntArray apps = new SparseIntArray();
            int sdkSandboxUid = -1;
            if (hasSdkSandbox(uid)) {
                sdkSandboxUid = sProcessShim.toSdkSandboxUid(uid);
            }
            if (permission == PERMISSION_NONE) {
                mUidToNetworkPerm.delete(uid);
                apps.put(uid, PERMISSION_NETWORK);  // doesn't matter which permission we pick here
                if (sdkSandboxUid != -1) {
                    mUidToNetworkPerm.delete(sdkSandboxUid);
                    apps.put(sdkSandboxUid, PERMISSION_NETWORK);
                }
                sendUidsNetworkPermission(apps, false);
            } else {
                mUidToNetworkPerm.put(uid, permission);
                apps.put(uid, permission);
                if (sdkSandboxUid != -1) {
                    mUidToNetworkPerm.put(sdkSandboxUid, permission);
                    apps.put(sdkSandboxUid, permission);
                }
                sendUidsNetworkPermission(apps, true);
            }
        }
    }

    private static int getNetdPermissionMask(String[] requestedPermissions,
                                             int[] requestedPermissionsFlags) {
        int permissions = PERMISSION_NONE;
        if (requestedPermissions == null || requestedPermissionsFlags == null) return permissions;
        for (int i = 0; i < requestedPermissions.length; i++) {
            if (requestedPermissions[i].equals(INTERNET)
                    && ((requestedPermissionsFlags[i] & REQUESTED_PERMISSION_GRANTED) != 0)) {
                permissions |= PERMISSION_INTERNET;
            }
            if (requestedPermissions[i].equals(UPDATE_DEVICE_STATS)
                    && ((requestedPermissionsFlags[i] & REQUESTED_PERMISSION_GRANTED) != 0)) {
                permissions |= PERMISSION_UPDATE_DEVICE_STATS;
            }
        }
        return permissions;
    }

    private synchronized PackageManager getPackageManagerAsUser(UserHandle user) {
        PackageManager pm = mUsersPackageManager.get(user);
        if (pm == null) {
            pm = mContext.createContextAsUser(user, 0 /* flag */).getPackageManager();
            mUsersPackageManager.put(user, pm);
        }
        return pm;
    }

    private PackageInfo getPackageInfoAsUser(String packageName, UserHandle user) {
        try {
            final PackageInfo info = getPackageManagerAsUser(user)
                    .getPackageInfo(packageName, GET_PERMISSIONS);
            return info;
        } catch (NameNotFoundException e) {
            // App not found.
            loge("NameNotFoundException " + packageName);
            return null;
        }
    }

    /**
     * Called when a new set of UID ranges are added to an active VPN network
     *
     * @param iface The active VPN network's interface name. Null iface indicates that the app is
     *              allowed to receive packets on all interfaces.
     * @param rangesToAdd The new UID ranges to be added to the network
     * @param vpnAppUid The uid of the VPN app
     */
    public synchronized void onVpnUidRangesAdded(@Nullable String iface, Set<UidRange> rangesToAdd,
            int vpnAppUid) {
        // Calculate the list of new app uids under the VPN due to the new UID ranges and update
        // Netd about them. Because mAllApps only contains appIds instead of uids, the result might
        // be an overestimation if an app is not installed on the user on which the VPN is running,
        // but that's safe: if an app is not installed, it cannot receive any packets, so dropping
        // packets to that UID is fine.
        final Set<Integer> changedUids = intersectUids(rangesToAdd, mAllApps);
        removeBypassingUids(changedUids, vpnAppUid);
        removeVpnLockdownUids(iface, changedUids);
        updateVpnUidsInterfaceRules(iface, changedUids, true /* add */);
        if (mVpnInterfaceUidRanges.containsKey(iface)) {
            mVpnInterfaceUidRanges.get(iface).addAll(rangesToAdd);
        } else {
            mVpnInterfaceUidRanges.put(iface, new HashSet<UidRange>(rangesToAdd));
        }
    }

    /**
     * Called when a set of UID ranges are removed from an active VPN network
     *
     * @param iface The VPN network's interface name. Null iface indicates that the app is allowed
     *              to receive packets on all interfaces.
     * @param rangesToRemove Existing UID ranges to be removed from the VPN network
     * @param vpnAppUid The uid of the VPN app
     */
    public synchronized void onVpnUidRangesRemoved(@Nullable String iface,
            Set<UidRange> rangesToRemove, int vpnAppUid) {
        // Calculate the list of app uids that are no longer under the VPN due to the removed UID
        // ranges and update Netd about them.
        final Set<Integer> changedUids = intersectUids(rangesToRemove, mAllApps);
        removeBypassingUids(changedUids, vpnAppUid);
        removeVpnLockdownUids(iface, changedUids);
        updateVpnUidsInterfaceRules(iface, changedUids, false /* add */);
        Set<UidRange> existingRanges = mVpnInterfaceUidRanges.getOrDefault(iface, null);
        if (existingRanges == null) {
            loge("Attempt to remove unknown vpn uid Range iface = " + iface);
            return;
        }
        existingRanges.removeAll(rangesToRemove);
        if (existingRanges.size() == 0) {
            mVpnInterfaceUidRanges.remove(iface);
        }
    }

    /**
     * Called when a set of UID ranges are added/removed from an active VPN network and when
     * UID ranges under VPN Lockdown are updated
     *
     * @param iface The VPN network's interface name. Null iface indicates that the interface is not
     *              available.
     * @param rangesToModify Existing UID ranges to be modified on the VPN network
     * @param add {@code true} to add the UID rules, {@code false} to remove them.
     * @param vpnAppUid The uid of the VPN app
     */
    public synchronized void updateVpnLockdownUidInterfaceRules(@Nullable String iface,
            Set<UidRange> rangesToModify, int vpnAppUid, boolean add) {
        if (iface != null) {
            Set<Integer> uidsToModify = intersectUids(rangesToModify, mAllApps);
            removeBypassingUids(uidsToModify, vpnAppUid);
            Set<Integer> vpnLockdownUids = intersectUids(mVpnLockdownUidRanges.getSet(), mAllApps);
            uidsToModify.retainAll(vpnLockdownUids);
            updateVpnUidsInterfaceRules(iface, uidsToModify, add);
        }
    }

    /**
     * Called when UID ranges under VPN Lockdown are updated
     *
     * @param add {@code true} if the uids are to be added to the Lockdown, {@code false} if they
     *        are to be removed from the Lockdown.
     * @param ranges The updated UID ranges under VPN Lockdown. This function does not treat the VPN
     *               app's UID in any special way. The caller is responsible for excluding the VPN
     *               app UID from the passed-in ranges.
     *               Ranges can have duplications, overlaps, and/or contain the range that is
     *               already subject to lockdown.
     */
    public synchronized void updateVpnLockdownUidRanges(boolean add, UidRange[] ranges) {
        final Set<UidRange> affectedUidRanges = new HashSet<>();

        for (final UidRange range : ranges) {
            if (add) {
                // Rule will be added if mVpnLockdownUidRanges does not have this uid range entry
                // currently.
                if (mVpnLockdownUidRanges.add(range) == 0) {
                    affectedUidRanges.add(range);
                }
            } else {
                // Rule will be removed if the number of the range in the set is 1 before the
                // removal.
                if (mVpnLockdownUidRanges.remove(range) == 1) {
                    affectedUidRanges.add(range);
                }
            }
        }

        // mAllApps only contains appIds instead of uids. So the generated uid list might contain
        // apps that are installed only on some users but not others. But that's safe: if an app is
        // not installed, it cannot receive any packets, so dropping packets to that UID is fine.
        final Set<Integer> affectedUids = intersectUids(affectedUidRanges, mAllApps);

        // We skip adding rule to privileged apps and allow them to bypass incoming packet
        // filtering. The behaviour is consistent with how lockdown works for outgoing packets, but
        // the implementation is different: while ConnectivityService#setRequireVpnForUids does not
        // exclude privileged apps from the prohibit routing rules used to implement outgoing packet
        // filtering, privileged apps can still bypass outgoing packet filtering because the
        // prohibit rules observe the protected from VPN bit.
        // If removing a UID, we ensure it is not present anywhere in the set first.
        for (final int uid: affectedUids) {
            if (!hasRestrictedNetworksPermission(uid)
                    && (add || !UidRange.containsUid(mVpnLockdownUidRanges.getSet(), uid))) {
                updateLockdownUidRule(uid, add);
            }
        }
    }

    /**
     * Compute the intersection of a set of UidRanges and appIds. Returns a set of uids
     * that satisfies:
     *   1. falls into one of the UidRange
     *   2. matches one of the appIds
     */
    private Set<Integer> intersectUids(Set<UidRange> ranges, Set<Integer> appIds) {
        Set<Integer> result = new HashSet<>();
        for (UidRange range : ranges) {
            for (int userId = range.getStartUser(); userId <= range.getEndUser(); userId++) {
                for (int appId : appIds) {
                    final UserHandle handle = UserHandle.of(userId);
                    if (handle == null) continue;

                    final int uid = handle.getUid(appId);
                    if (range.contains(uid)) {
                        result.add(uid);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Remove all apps which can elect to bypass the VPN from the list of uids
     *
     * An app can elect to bypass the VPN if it holds SYSTEM permission, or if it's the active VPN
     * app itself.
     *
     * @param uids The list of uids to operate on
     * @param vpnAppUid The uid of the VPN app
     */
    private void removeBypassingUids(Set<Integer> uids, int vpnAppUid) {
        uids.remove(vpnAppUid);
        uids.removeIf(this::hasRestrictedNetworksPermission);
    }

    /**
     * Remove all apps which are under VPN Lockdown from the list of uids
     *
     * @param iface The interface name of the active VPN connection
     * @param uids The list of uids to operate on
     */
    private void removeVpnLockdownUids(@Nullable String iface, Set<Integer> uids) {
        if (iface == null) {
            uids.removeAll(intersectUids(mVpnLockdownUidRanges.getSet(), mAllApps));
        }
    }

    /**
     * Update netd about the list of uids that are under an active VPN connection which they cannot
     * bypass.
     *
     * This is to instruct netd to set up appropriate filtering rules for these uids, such that they
     * can only receive ingress packets from the VPN's tunnel interface (and loopback).
     * Null iface set up a wildcard rule that allow app to receive packets on all interfaces.
     *
     * @param iface the interface name of the active VPN connection
     * @param add {@code true} if the uids are to be added to the interface, {@code false} if they
     *        are to be removed from the interface.
     */
    private void updateVpnUidsInterfaceRules(String iface, Set<Integer> uids, boolean add) {
        if (uids.size() == 0) {
            return;
        }
        try {
            if (add) {
                mBpfNetMaps.addUidInterfaceRules(iface, toIntArray(uids));
            } else {
                mBpfNetMaps.removeUidInterfaceRules(toIntArray(uids));
            }
        } catch (RemoteException | ServiceSpecificException e) {
            loge("Exception when updating permissions: ", e);
        }
    }

    private void updateLockdownUidRule(int uid, boolean add) {
        try {
            mBpfNetMaps.updateUidLockdownRule(uid, add);
        } catch (ServiceSpecificException e) {
            loge("Failed to " + (add ? "add" : "remove") + " Lockdown rule: " + e);
        }
    }

    /**
     * Send the updated permission information to netd. Called upon package install/uninstall.
     *
     * @param appId the appId of the package installed
     * @param permissions the permissions the app requested and netd cares about.
     *
     * @hide
     */
    @VisibleForTesting
    void sendPackagePermissionsForAppId(int appId, int permissions) {
        SparseIntArray netdPermissionsAppIds = new SparseIntArray();
        netdPermissionsAppIds.put(appId, permissions);
        if (hasSdkSandbox(appId)) {
            int sdkSandboxAppId = sProcessShim.toSdkSandboxUid(appId);
            netdPermissionsAppIds.put(sdkSandboxAppId, permissions);
        }
        sendAppIdsTrafficPermission(netdPermissionsAppIds);
    }

    /**
     * Grant or revoke the INTERNET and/or UPDATE_DEVICE_STATS permission of the appIds in array.
     *
     * @param netdPermissionsAppIds integer pairs of appIds and the permission granted to it. If the
     * permission is 0, revoke all permissions of that appId.
     *
     * @hide
     */
    @VisibleForTesting
    void sendAppIdsTrafficPermission(SparseIntArray netdPermissionsAppIds) {
        ensureRunningOnHandlerThread();
        final ArrayList<Integer> allPermissionAppIds = new ArrayList<>();
        final ArrayList<Integer> internetPermissionAppIds = new ArrayList<>();
        final ArrayList<Integer> updateStatsPermissionAppIds = new ArrayList<>();
        final ArrayList<Integer> noPermissionAppIds = new ArrayList<>();
        final ArrayList<Integer> uninstalledAppIds = new ArrayList<>();
        for (int i = 0; i < netdPermissionsAppIds.size(); i++) {
            int permissions = netdPermissionsAppIds.valueAt(i);
            switch(permissions) {
                case (PERMISSION_INTERNET | PERMISSION_UPDATE_DEVICE_STATS):
                    allPermissionAppIds.add(netdPermissionsAppIds.keyAt(i));
                    break;
                case PERMISSION_INTERNET:
                    internetPermissionAppIds.add(netdPermissionsAppIds.keyAt(i));
                    break;
                case PERMISSION_UPDATE_DEVICE_STATS:
                    updateStatsPermissionAppIds.add(netdPermissionsAppIds.keyAt(i));
                    break;
                case PERMISSION_NONE:
                    noPermissionAppIds.add(netdPermissionsAppIds.keyAt(i));
                    break;
                case PERMISSION_UNINSTALLED:
                    uninstalledAppIds.add(netdPermissionsAppIds.keyAt(i));
                    break;
                default:
                    Log.e(TAG, "unknown permission type: " + permissions + "for uid: "
                            + netdPermissionsAppIds.keyAt(i));
            }
        }
        try {
            // TODO: add a lock inside netd to protect IPC trafficSetNetPermForUids()
            if (allPermissionAppIds.size() != 0) {
                mBpfNetMaps.setNetPermForUids(
                        PERMISSION_INTERNET | PERMISSION_UPDATE_DEVICE_STATS,
                        toIntArray(allPermissionAppIds));
            }
            if (internetPermissionAppIds.size() != 0) {
                mBpfNetMaps.setNetPermForUids(PERMISSION_INTERNET,
                        toIntArray(internetPermissionAppIds));
            }
            if (updateStatsPermissionAppIds.size() != 0) {
                mBpfNetMaps.setNetPermForUids(PERMISSION_UPDATE_DEVICE_STATS,
                        toIntArray(updateStatsPermissionAppIds));
            }
            if (noPermissionAppIds.size() != 0) {
                mBpfNetMaps.setNetPermForUids(PERMISSION_NONE,
                        toIntArray(noPermissionAppIds));
            }
            if (uninstalledAppIds.size() != 0) {
                mBpfNetMaps.setNetPermForUids(PERMISSION_UNINSTALLED,
                        toIntArray(uninstalledAppIds));
            }
        } catch (RemoteException | ServiceSpecificException e) {
            Log.e(TAG, "Pass appId list of special permission failed." + e);
        }
    }

    private synchronized void onSettingChanged() {
        // Step1. Update uids allowed to use restricted networks and compute the set of uids to
        // update.
        final Set<Integer> uidsToUpdate = new ArraySet<>(mUidsAllowedOnRestrictedNetworks);
        updateUidsAllowedOnRestrictedNetworks(mDeps.getUidsAllowedOnRestrictedNetworks(mContext));
        uidsToUpdate.addAll(mUidsAllowedOnRestrictedNetworks);

        final SparseIntArray updatedUids = new SparseIntArray();
        final SparseIntArray removedUids = new SparseIntArray();

        // Step2. For each uid to update, find out its new permission.
        for (Integer uid : uidsToUpdate) {
            final int permission = highestUidNetworkPermission(uid);

            if (PERMISSION_NONE == permission) {
                // Doesn't matter which permission is set here.
                removedUids.put(uid, PERMISSION_NETWORK);
                mUidToNetworkPerm.delete(uid);
                if (hasSdkSandbox(uid)) {
                    int sdkSandboxUid = sProcessShim.toSdkSandboxUid(uid);
                    removedUids.put(sdkSandboxUid, PERMISSION_NETWORK);
                    mUidToNetworkPerm.delete(sdkSandboxUid);
                }
            } else {
                updatedUids.put(uid, permission);
                mUidToNetworkPerm.put(uid, permission);
                if (hasSdkSandbox(uid)) {
                    int sdkSandboxUid = sProcessShim.toSdkSandboxUid(uid);
                    updatedUids.put(sdkSandboxUid, permission);
                    mUidToNetworkPerm.put(sdkSandboxUid, permission);
                }
            }
        }

        // Step3. Update or revoke permission for uids with netd.
        sendUidsNetworkPermission(updatedUids, true /* add */);
        sendUidsNetworkPermission(removedUids, false /* add */);
        mPermissionUpdateLogs.log("Setting change: update=" + updatedUids
                + ", remove=" + removedUids);
    }

    private synchronized void onExternalApplicationsAvailable(String[] pkgList) {
        if (CollectionUtils.isEmpty(pkgList)) {
            Log.e(TAG, "No available external application.");
            return;
        }

        for (String app : pkgList) {
            for (UserHandle user : mUsers) {
                final PackageInfo info = getPackageInfoAsUser(app, user);
                if (info == null || info.applicationInfo == null) continue;

                final int uid = info.applicationInfo.uid;
                onPackageAdded(app, uid); // Use onPackageAdded to add package one by one.
            }
        }
    }

    /** Dump info to dumpsys */
    public void dump(IndentingPrintWriter pw) {
        pw.println("Interface filtering rules:");
        pw.increaseIndent();
        for (Map.Entry<String, Set<UidRange>> vpn : mVpnInterfaceUidRanges.entrySet()) {
            pw.println("Interface: " + vpn.getKey());
            pw.println("UIDs: " + vpn.getValue().toString());
            pw.println();
        }
        pw.decreaseIndent();

        pw.println();
        pw.println("Lockdown filtering rules:");
        pw.increaseIndent();
        synchronized (this) {
            for (final UidRange range : mVpnLockdownUidRanges.getSet()) {
                pw.println("UIDs: " + range);
            }
        }
        pw.decreaseIndent();

        pw.println();
        pw.println("Update logs:");
        pw.increaseIndent();
        mPermissionUpdateLogs.reverseDump(pw);
        pw.decreaseIndent();
    }

    private static void log(String s) {
        if (DBG) {
            Log.d(TAG, s);
        }
    }

    private static void loge(String s) {
        Log.e(TAG, s);
    }

    private static void loge(String s, Throwable e) {
        Log.e(TAG, s, e);
    }
}
