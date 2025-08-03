/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.Manifest;
import android.annotation.NonNull;
import android.app.role.OnRoleHoldersChangedListener;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.annotations.VisibleForTesting;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Tracks the uid of all the default messaging application which are role_sms role and
 * satellite_communication permission complaint and requests ConnectivityService to create multi
 * layer request with satellite internet access support for the default message application.
 * @hide
 */
public class SatelliteAccessController {
    private static final String TAG = SatelliteAccessController.class.getSimpleName();
    private final Context mContext;
    private final Dependencies mDeps;
    private final DefaultMessageRoleListener mDefaultMessageRoleListener;
    private final UserManager mUserManager;
    private final Consumer<Set<Integer>> mCallback;
    private final Handler mConnectivityServiceHandler;

    // At this sparseArray, Key is userId and values are uids of SMS apps that are allowed
    // to use satellite network as fallback.
    private final SparseArray<Set<Integer>> mAllUsersSatelliteNetworkFallbackUidCache =
            new SparseArray<>();

    /**
     *  Monitor {@link android.app.role.OnRoleHoldersChangedListener#onRoleHoldersChanged(String,
     *  UserHandle)},
     *
     */
    private final class DefaultMessageRoleListener
            implements OnRoleHoldersChangedListener {
        @Override
        public void onRoleHoldersChanged(String role, UserHandle userHandle) {
            if (RoleManager.ROLE_SMS.equals(role)) {
                Log.i(TAG, "ROLE_SMS Change detected ");
                onRoleSmsChanged(userHandle);
            }
        }

        public void register() {
            try {
                mDeps.addOnRoleHoldersChangedListenerAsUser(
                        mConnectivityServiceHandler::post, this, UserHandle.ALL);
            } catch (RuntimeException e) {
                Log.wtf(TAG, "Could not register satellite controller listener due to " + e);
            }
        }
    }

    public SatelliteAccessController(@NonNull final Context c,
            Consumer<Set<Integer>> callback,
            @NonNull final Handler connectivityServiceInternalHandler) {
        this(c, new Dependencies(c), callback, connectivityServiceInternalHandler);
    }

    public static class Dependencies {
        private final RoleManager mRoleManager;

        private Dependencies(Context context) {
            mRoleManager = context.getSystemService(RoleManager.class);
        }

        /** See {@link RoleManager#getRoleHoldersAsUser(String, UserHandle)} */
        public List<String> getRoleHoldersAsUser(String roleName, UserHandle userHandle) {
            return mRoleManager.getRoleHoldersAsUser(roleName, userHandle);
        }

        /** See {@link RoleManager#addOnRoleHoldersChangedListenerAsUser} */
        public void addOnRoleHoldersChangedListenerAsUser(@NonNull Executor executor,
                @NonNull OnRoleHoldersChangedListener listener, UserHandle user) {
            mRoleManager.addOnRoleHoldersChangedListenerAsUser(executor, listener, user);
        }
    }

    @VisibleForTesting
    SatelliteAccessController(@NonNull final Context c, @NonNull final Dependencies deps,
            Consumer<Set<Integer>> callback,
            @NonNull final Handler connectivityServiceInternalHandler) {
        mContext = c;
        mDeps = deps;
        mUserManager = mContext.getSystemService(UserManager.class);
        mDefaultMessageRoleListener = new DefaultMessageRoleListener();
        mCallback = callback;
        mConnectivityServiceHandler = connectivityServiceInternalHandler;
    }

    private Set<Integer> updateSatelliteNetworkFallbackUidListCache(List<String> packageNames,
            @NonNull UserHandle userHandle) {
        Set<Integer> fallbackUids = new ArraySet<>();
        PackageManager pm =
                mContext.createContextAsUser(userHandle, 0).getPackageManager();
        if (pm != null) {
            for (String packageName : packageNames) {
                // Check if SATELLITE_COMMUNICATION permission is enabled for default sms
                // application package before adding it part of satellite network fallback uid
                // cache list.
                if (isSatellitePermissionEnabled(pm, packageName)) {
                    int uid = getUidForPackage(pm, packageName);
                    if (uid != Process.INVALID_UID) {
                        fallbackUids.add(uid);
                    }
                }
            }
        } else {
            Log.wtf(TAG, "package manager found null");
        }
        return fallbackUids;
    }

    //Check if satellite communication is enabled for the package
    private boolean isSatellitePermissionEnabled(PackageManager packageManager,
            String packageName) {
        return packageManager.checkPermission(
                Manifest.permission.SATELLITE_COMMUNICATION, packageName)
                == PackageManager.PERMISSION_GRANTED;
    }

    private int getUidForPackage(PackageManager packageManager, String pkgName) {
        if (pkgName == null) {
            return Process.INVALID_UID;
        }
        try {
            ApplicationInfo applicationInfo = packageManager.getApplicationInfo(pkgName, 0);
            return applicationInfo.uid;
        } catch (PackageManager.NameNotFoundException exception) {
            Log.e(TAG, "Unable to find uid for package: " + pkgName);
        }
        return Process.INVALID_UID;
    }

    // on Role sms change triggered by OnRoleHoldersChangedListener()
    // TODO(b/326373613): using UserLifecycleListener, callback to be received when user removed for
    // user delete scenario. This to be used to update uid list and ML Layer request can also be
    // updated.
    private void onRoleSmsChanged(@NonNull UserHandle userHandle) {
        int userId = userHandle.getIdentifier();
        if (userId == Process.INVALID_UID) {
            Log.wtf(TAG, "Invalid User Id");
            return;
        }

        //Returns empty list if no package exists
        final List<String> packageNames =
                mDeps.getRoleHoldersAsUser(RoleManager.ROLE_SMS, userHandle);

        // Store previous satellite fallback uid available
        final Set<Integer> prevUidsForUser =
                mAllUsersSatelliteNetworkFallbackUidCache.get(userId, new ArraySet<>());

        Log.i(TAG, "currentUser : role_sms_packages: " + userId + " : " + packageNames);
        final Set<Integer> newUidsForUser = !packageNames.isEmpty()
                ? updateSatelliteNetworkFallbackUidListCache(packageNames, userHandle)
                : new ArraySet<>();
        Log.i(TAG, "satellite_fallback_uid: " + newUidsForUser);

        // on Role change, update the multilayer request at ConnectivityService with updated
        // satellite network fallback uid cache list of multiple users as applicable
        if (newUidsForUser.equals(prevUidsForUser)) {
            return;
        }

        mAllUsersSatelliteNetworkFallbackUidCache.put(userId, newUidsForUser);

        // Merge all uids of multiple users available
        Set<Integer> mergedSatelliteNetworkFallbackUidCache = new ArraySet<>();
        for (int i = 0; i < mAllUsersSatelliteNetworkFallbackUidCache.size(); i++) {
            mergedSatelliteNetworkFallbackUidCache.addAll(
                    mAllUsersSatelliteNetworkFallbackUidCache.valueAt(i));
        }
        Log.i(TAG, "merged uid list for multi layer request : "
                + mergedSatelliteNetworkFallbackUidCache);

        // trigger multiple layer request for satellite network fallback of multi user uids
        mCallback.accept(mergedSatelliteNetworkFallbackUidCache);
    }

    private List<String> getRoleSmsChangedPackageName(UserHandle userHandle) {
        try {
            return mDeps.getRoleHoldersAsUser(RoleManager.ROLE_SMS, userHandle);
        } catch (RuntimeException e) {
            Log.wtf(TAG, "Could not get package name at role sms change update due to: " + e);
            return null;
        }
    }

    /** Register OnRoleHoldersChangedListener */
    public void start() {
        mConnectivityServiceHandler.post(this::updateAllUserRoleSmsUids);
        mDefaultMessageRoleListener.register();
    }

    private void updateAllUserRoleSmsUids() {
        List<UserHandle> existingUsers = mUserManager.getUserHandles(true /* excludeDying */);
        // Iterate through the user handles and obtain their uids with role sms and satellite
        // communication permission
        for (UserHandle userHandle : existingUsers) {
            onRoleSmsChanged(userHandle);
        }
    }
}
