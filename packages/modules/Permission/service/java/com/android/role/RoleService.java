/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.role;

import android.Manifest;
import android.annotation.AnyThread;
import android.annotation.MainThread;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.annotation.WorkerThread;
import android.app.AppOpsManager;
import android.app.admin.DevicePolicyManager;
import android.app.role.IOnRoleHoldersChangedListener;
import android.app.role.IRoleManager;
import android.app.role.RoleControllerManager;
import android.app.role.RoleManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.os.RemoteCallback;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.permission.flags.Flags;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.IndentingPrintWriter;
import android.util.Log;
import android.util.SparseArray;
import android.util.proto.ProtoOutputStream;

import androidx.annotation.Keep;
import androidx.annotation.RequiresApi;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.infra.AndroidFuture;
import com.android.internal.util.Preconditions;
import com.android.internal.util.dump.DualDumpOutputStream;
import com.android.modules.utils.build.SdkLevel;
import com.android.permission.compat.UserHandleCompat;
import com.android.permission.util.ArrayUtils;
import com.android.permission.util.CollectionUtils;
import com.android.permission.util.ForegroundThread;
import com.android.permission.util.PackageUtils;
import com.android.permission.util.ThrottledRunnable;
import com.android.permission.util.UserUtils;
import com.android.server.LocalManagerRegistry;
import com.android.server.SystemService;
import com.android.server.role.RoleServicePlatformHelper;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Service for role management.
 *
 * @see RoleManager
 */
@Keep
@RequiresApi(Build.VERSION_CODES.S)
public class RoleService extends SystemService implements RoleUserState.Callback {
    private static final String LOG_TAG = RoleService.class.getSimpleName();

    private static final boolean DEBUG = false;

    private static final long GRANT_DEFAULT_ROLES_INTERVAL_MILLIS = 1000;

    private static final String[] DEFAULT_APPLICATION_ROLES;

    static {
        List<String> defaultApplicationRoles = new ArrayList<>();
        defaultApplicationRoles.add(RoleManager.ROLE_ASSISTANT);
        defaultApplicationRoles.add(RoleManager.ROLE_BROWSER);
        defaultApplicationRoles.add(RoleManager.ROLE_CALL_REDIRECTION);
        defaultApplicationRoles.add(RoleManager.ROLE_CALL_SCREENING);
        defaultApplicationRoles.add(RoleManager.ROLE_DIALER);
        defaultApplicationRoles.add(RoleManager.ROLE_HOME);
        defaultApplicationRoles.add(RoleManager.ROLE_SMS);
        if (SdkLevel.isAtLeastV()) {
            defaultApplicationRoles.add(RoleManager.ROLE_WALLET);
        }
        DEFAULT_APPLICATION_ROLES = defaultApplicationRoles.toArray(new String[0]);
    }

    @NonNull
    private final AppOpsManager mAppOpsManager;

    @NonNull
    private final Object mLock = new Object();

    @NonNull
    private final RoleServicePlatformHelper mPlatformHelper;

    /**
     * Maps user id to its state.
     */
    @GuardedBy("mLock")
    @NonNull
    private final SparseArray<RoleUserState> mUserStates = new SparseArray<>();

    /**
     * Maps user id to its controller.
     */
    @GuardedBy("mLock")
    @NonNull
    private final SparseArray<RoleController> mControllers = new SparseArray<>();

    /**
     * Maps user id to its list of listeners.
     */
    @GuardedBy("mLock")
    @NonNull
    private final SparseArray<RemoteCallbackList<IOnRoleHoldersChangedListener>> mListeners =
            new SparseArray<>();

    @NonNull
    private final Handler mListenerHandler = ForegroundThread.getHandler();

    @GuardedBy("mLock")
    private boolean mBypassingRoleQualification;

    /**
     * Maps user id to its throttled runnable for granting default roles.
     */
    @GuardedBy("mLock")
    @NonNull
    private final SparseArray<ThrottledRunnable> mGrantDefaultRolesThrottledRunnables =
            new SparseArray<>();

    public RoleService(@NonNull Context context) {
        super(context);

        mPlatformHelper = LocalManagerRegistry.getManager(RoleServicePlatformHelper.class);

        RoleControllerManager.initializeRemoteServiceComponentName(context);

        mAppOpsManager = context.getSystemService(AppOpsManager.class);

        LocalManagerRegistry.addManager(RoleManagerLocal.class, new Local());

        registerUserRemovedReceiver();
    }

    private void registerUserRemovedReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_USER_REMOVED);
        getContext().registerReceiverForAllUsers(new BroadcastReceiver() {
            @Override
            public void onReceive(@NonNull Context context, @NonNull Intent intent) {
                if (TextUtils.equals(intent.getAction(), Intent.ACTION_USER_REMOVED)) {
                    int userId = intent.<UserHandle>getParcelableExtra(Intent.EXTRA_USER)
                            .getIdentifier();
                    onRemoveUser(userId);
                }
            }
        }, intentFilter, null, null);
    }

    @Override
    public void onStart() {
        publishBinderService(Context.ROLE_SERVICE, new Stub());

        Context context = getContext();
        IntentFilter packageIntentFilter = new IntentFilter();
        packageIntentFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        packageIntentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        packageIntentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packageIntentFilter.addDataScheme("package");
        packageIntentFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        context.registerReceiverForAllUsers(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int userId = UserHandleCompat.getUserId(intent.getIntExtra(Intent.EXTRA_UID, -1));
                if (DEBUG) {
                    Log.i(LOG_TAG, "Packages changed - re-running initial grants for user "
                            + userId);
                }
                if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())
                        && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                    // Package is being upgraded - we're about to get ACTION_PACKAGE_ADDED
                    return;
                }
                maybeGrantDefaultRolesAsync(userId);
            }
        }, packageIntentFilter, null, null);

        if (SdkLevel.isAtLeastV()) {
            IntentFilter devicePolicyIntentFilter = new IntentFilter();
            devicePolicyIntentFilter.addAction(
                    DevicePolicyManager.ACTION_DEVICE_OWNER_CHANGED);
            devicePolicyIntentFilter.addAction(
                    DevicePolicyManager.ACTION_PROFILE_OWNER_CHANGED);
            devicePolicyIntentFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
            context.registerReceiverForAllUsers(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    int userId = getSendingUser().getIdentifier();
                    if (DEBUG) {
                        Log.i(LOG_TAG, "Device policy changed (" + intent.getAction()
                            + ") - re-running initial grants for user " + userId);
                    }
                    maybeGrantDefaultRolesAsync(userId);
                }
            }, devicePolicyIntentFilter, null, null);

            context.getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.DEVICE_DEMO_MODE), false,
                    new ContentObserver(ForegroundThread.getHandler()) {
                        public void onChange(boolean selfChange, Uri uri) {
                            if (DEBUG) {
                                Log.i(LOG_TAG, "Settings.Global.DEVICE_DEMO_MODE changed.");
                            }
                            UserManager userManager =
                                    context.getSystemService(UserManager.class);
                            List<UserHandle> users = userManager.getUserHandles(true);
                            int usersSize = users.size();
                            for (int i = 0; i < usersSize; i++) {
                                maybeGrantDefaultRolesAsync(users.get(i).getIdentifier());
                            }
                        }
                    });
        }
    }

    @Override
    public void onUserStarting(@NonNull TargetUser user) {
        if (SdkLevel.isAtLeastV() && Flags.systemServerRoleControllerEnabled()) {
            upgradeLegacyFallbackEnabledRolesIfNeeded(user);
        }

        maybeGrantDefaultRolesSync(user.getUserHandle().getIdentifier());
    }

    private void upgradeLegacyFallbackEnabledRolesIfNeeded(@NonNull TargetUser user) {
        int userId = user.getUserHandle().getIdentifier();
        RoleUserState userState = getOrCreateUserState(userId);
        if (!userState.isVersionUpgradeNeeded()) {
            return;
        }
        List<String> legacyFallbackDisabledRoles = getLegacyFallbackDisabledRolesSync(userId);
        if (legacyFallbackDisabledRoles == null) {
            return;
        }
        userState.upgradeVersion(legacyFallbackDisabledRoles);
    }

    @MainThread
    private List<String> getLegacyFallbackDisabledRolesSync(@UserIdInt int userId) {
        AndroidFuture<List<String>> future = new AndroidFuture<>();
        RoleController controller = new RemoteRoleController(UserHandle.of(userId), getContext());
        controller.getLegacyFallbackDisabledRoles(ForegroundThread.getExecutor(), future::complete);
        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            Log.e(LOG_TAG, "Failed to get the legacy role fallback disabled state for user "
                    + userId, e);
            return null;
        }
    }

    @MainThread
    private void maybeGrantDefaultRolesSync(@UserIdInt int userId) {
        AndroidFuture<Void> future = maybeGrantDefaultRolesInternal(userId);
        try {
            future.get(30, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            Log.e(LOG_TAG, "Failed to grant default roles for user " + userId, e);
        }
    }

    private void maybeGrantDefaultRolesAsync(@UserIdInt int userId) {
        ThrottledRunnable runnable;
        synchronized (mLock) {
            runnable = mGrantDefaultRolesThrottledRunnables.get(userId);
            if (runnable == null) {
                runnable = new ThrottledRunnable(ForegroundThread.getHandler(),
                        GRANT_DEFAULT_ROLES_INTERVAL_MILLIS,
                        () -> maybeGrantDefaultRolesInternal(userId));
                mGrantDefaultRolesThrottledRunnables.put(userId, runnable);
            }
        }
        runnable.run();
    }

    @AnyThread
    @NonNull
    private AndroidFuture<Void> maybeGrantDefaultRolesInternal(@UserIdInt int userId) {
        RoleUserState userState = getOrCreateUserState(userId);
        String oldPackagesHash = userState.getPackagesHash();
        String newPackagesHash = mPlatformHelper.computePackageStateHash(userId);
        if (Objects.equals(oldPackagesHash, newPackagesHash)) {
            if (DEBUG) {
                Log.i(LOG_TAG, "Already granted default roles for packages hash "
                        + newPackagesHash);
            }
            return AndroidFuture.completedFuture(null);
        }

        // Some package state has changed, so grant default roles again.
        Log.i(LOG_TAG, "Granting default roles...");
        AndroidFuture<Void> future = new AndroidFuture<>();
        getOrCreateController(userId).grantDefaultRoles(ForegroundThread.getExecutor(),
                successful -> {
                    if (successful) {
                        userState.setPackagesHash(newPackagesHash);
                        future.complete(null);
                    } else {
                        future.completeExceptionally(new RuntimeException());
                    }
                });
        return future;
    }

    @NonNull
    private RoleUserState getOrCreateUserState(@UserIdInt int userId) {
        synchronized (mLock) {
            RoleUserState userState = mUserStates.get(userId);
            if (userState == null) {
                userState = new RoleUserState(userId, mPlatformHelper, this,
                        mBypassingRoleQualification);
                mUserStates.put(userId, userState);
            }
            return userState;
        }
    }

    @NonNull
    private RoleController getOrCreateController(@UserIdInt int userId) {
        synchronized (mLock) {
            RoleController controller = mControllers.get(userId);
            if (controller == null) {
                UserHandle user = UserHandle.of(userId);
                Context context = getContext();
                if (SdkLevel.isAtLeastV() && Flags.systemServerRoleControllerEnabled()) {
                    controller = new LocalRoleController(user, context);
                } else {
                    controller = new RemoteRoleController(user, context);
                }
                mControllers.put(userId, controller);
            }
            return controller;
        }
    }

    @Nullable
    private RemoteCallbackList<IOnRoleHoldersChangedListener> getListeners(@UserIdInt int userId) {
        synchronized (mLock) {
            return mListeners.get(userId);
        }
    }

    @NonNull
    private RemoteCallbackList<IOnRoleHoldersChangedListener> getOrCreateListeners(
            @UserIdInt int userId) {
        synchronized (mLock) {
            RemoteCallbackList<IOnRoleHoldersChangedListener> listeners = mListeners.get(userId);
            if (listeners == null) {
                listeners = new RemoteCallbackList<>();
                mListeners.put(userId, listeners);
            }
            return listeners;
        }
    }

    private void onRemoveUser(@UserIdInt int userId) {
        RemoteCallbackList<IOnRoleHoldersChangedListener> listeners;
        RoleUserState userState;
        synchronized (mLock) {
            mGrantDefaultRolesThrottledRunnables.remove(userId);
            listeners = mListeners.get(userId);
            mListeners.remove(userId);
            mControllers.remove(userId);
            userState = mUserStates.get(userId);
            mUserStates.remove(userId);
        }
        if (listeners != null) {
            listeners.kill();
        }
        if (userState != null) {
            userState.destroy();
        }
    }

    @Override
    public void onRoleHoldersChanged(@NonNull String roleName, @UserIdInt int userId) {
        mListenerHandler.post(() -> notifyRoleHoldersChanged(roleName, userId));
    }

    @WorkerThread
    private void notifyRoleHoldersChanged(@NonNull String roleName, @UserIdInt int userId) {
        RemoteCallbackList<IOnRoleHoldersChangedListener> listeners = getListeners(userId);
        if (listeners != null) {
            notifyRoleHoldersChangedForListeners(listeners, roleName, userId);
        }

        RemoteCallbackList<IOnRoleHoldersChangedListener> allUsersListeners = getListeners(
                UserHandleCompat.USER_ALL);
        if (allUsersListeners != null) {
            notifyRoleHoldersChangedForListeners(allUsersListeners, roleName, userId);
        }
    }

    @WorkerThread
    private void notifyRoleHoldersChangedForListeners(
            @NonNull RemoteCallbackList<IOnRoleHoldersChangedListener> listeners,
            @NonNull String roleName, @UserIdInt int userId) {
        int broadcastCount = listeners.beginBroadcast();
        try {
            for (int i = 0; i < broadcastCount; i++) {
                IOnRoleHoldersChangedListener listener = listeners.getBroadcastItem(i);
                try {
                    listener.onRoleHoldersChanged(roleName, userId);
                } catch (RemoteException e) {
                    Log.e(LOG_TAG, "Error calling OnRoleHoldersChangedListener", e);
                }
            }
        } finally {
            listeners.finishBroadcast();
        }
    }

    private class Stub extends IRoleManager.Stub {

        @Override
        public boolean isRoleAvailableAsUser(@NonNull String roleName, @UserIdInt int userId) {
            UserUtils.enforceCrossUserPermission(userId, false, "isRoleAvailableAsUser",
                    getContext());
            if (!UserUtils.isUserExistent(userId, getContext())) {
                Log.e(LOG_TAG, "user " + userId + " does not exist");
                return false;
            }

            Preconditions.checkStringNotEmpty(roleName, "roleName cannot be null or empty");

            return getOrCreateUserState(userId).isRoleAvailable(roleName);
        }

        @Override
        public boolean isRoleHeldAsUser(@NonNull String roleName, @NonNull String packageName,
                @UserIdInt int userId) {
            mAppOpsManager.checkPackage(getCallingUid(), packageName);

            UserUtils.enforceCrossUserPermission(userId, false, "isRoleHeldAsUser", getContext());
            if (!UserUtils.isUserExistent(userId, getContext())) {
                Log.e(LOG_TAG, "user " + userId + " does not exist");
                return false;
            }

            Preconditions.checkStringNotEmpty(roleName, "roleName cannot be null or empty");
            Preconditions.checkStringNotEmpty(packageName, "packageName cannot be null or empty");

            ArraySet<String> roleHolders = getOrCreateUserState(userId).getRoleHolders(roleName);
            if (roleHolders == null) {
                return false;
            }
            return roleHolders.contains(packageName);
        }

        @NonNull
        @Override
        public List<String> getRoleHoldersAsUser(@NonNull String roleName, @UserIdInt int userId) {
            UserUtils.enforceCrossUserPermission(userId, false, "getRoleHoldersAsUser",
                    getContext());
            if (!UserUtils.isUserExistent(userId, getContext())) {
                Log.e(LOG_TAG, "user " + userId + " does not exist");
                return Collections.emptyList();
            }

            getContext().enforceCallingOrSelfPermission(Manifest.permission.MANAGE_ROLE_HOLDERS,
                    "getRoleHoldersAsUser");

            Preconditions.checkStringNotEmpty(roleName, "roleName cannot be null or empty");

            ArraySet<String> roleHolders = getOrCreateUserState(userId).getRoleHolders(roleName);
            if (roleHolders == null) {
                return Collections.emptyList();
            }
            return new ArrayList<>(roleHolders);
        }

        @Override
        public void addRoleHolderAsUser(@NonNull String roleName, @NonNull String packageName,
                @RoleManager.ManageHoldersFlags int flags, @UserIdInt int userId,
                @NonNull RemoteCallback callback) {
            UserUtils.enforceCrossUserPermission(userId, false, "addRoleHolderAsUser",
                    getContext());
            if (!UserUtils.isUserExistent(userId, getContext())) {
                Log.e(LOG_TAG, "user " + userId + " does not exist");
                return;
            }

            getContext().enforceCallingOrSelfPermission(Manifest.permission.MANAGE_ROLE_HOLDERS,
                    "addRoleHolderAsUser");

            Preconditions.checkStringNotEmpty(roleName, "roleName cannot be null or empty");
            Preconditions.checkStringNotEmpty(packageName, "packageName cannot be null or empty");
            Objects.requireNonNull(callback, "callback cannot be null");

            getOrCreateController(userId).onAddRoleHolder(roleName, packageName, flags, callback);
        }

        @Override
        public void removeRoleHolderAsUser(@NonNull String roleName, @NonNull String packageName,
                @RoleManager.ManageHoldersFlags int flags, @UserIdInt int userId,
                @NonNull RemoteCallback callback) {
            UserUtils.enforceCrossUserPermission(userId, false, "removeRoleHolderAsUser",
                    getContext());
            if (!UserUtils.isUserExistent(userId, getContext())) {
                Log.e(LOG_TAG, "user " + userId + " does not exist");
                return;
            }

            getContext().enforceCallingOrSelfPermission(Manifest.permission.MANAGE_ROLE_HOLDERS,
                    "removeRoleHolderAsUser");

            Preconditions.checkStringNotEmpty(roleName, "roleName cannot be null or empty");
            Preconditions.checkStringNotEmpty(packageName, "packageName cannot be null or empty");
            Objects.requireNonNull(callback, "callback cannot be null");

            getOrCreateController(userId).onRemoveRoleHolder(roleName, packageName, flags,
                    callback);
        }

        @Override
        public void clearRoleHoldersAsUser(@NonNull String roleName,
                @RoleManager.ManageHoldersFlags int flags, @UserIdInt int userId,
                @NonNull RemoteCallback callback) {
            UserUtils.enforceCrossUserPermission(userId, false, "clearRoleHoldersAsUser",
                    getContext());
            if (!UserUtils.isUserExistent(userId, getContext())) {
                Log.e(LOG_TAG, "user " + userId + " does not exist");
                return;
            }

            getContext().enforceCallingOrSelfPermission(Manifest.permission.MANAGE_ROLE_HOLDERS,
                    "clearRoleHoldersAsUser");

            Preconditions.checkStringNotEmpty(roleName, "roleName cannot be null or empty");
            Objects.requireNonNull(callback, "callback cannot be null");

            getOrCreateController(userId).onClearRoleHolders(roleName, flags, callback);
        }

        @Override
        @Nullable
        public String getDefaultApplicationAsUser(@NonNull String roleName, @UserIdInt int userId) {
            // The MANAGE_DEFAULT_APPLICATIONS permission is only available on U+
            Preconditions.checkState(SdkLevel.isAtLeastU(),
                    "This API is only available on Android 34 and above");
            UserUtils.enforceCrossUserPermission(userId, false, "getDefaultApplicationAsUser",
                    getContext());
            if (!UserUtils.isUserExistent(userId, getContext())) {
                Log.e(LOG_TAG, "user " + userId + " does not exist");
                return null;
            }

            getContext().enforceCallingOrSelfPermission(
                    Manifest.permission.MANAGE_DEFAULT_APPLICATIONS, "getDefaultApplicationAsUser");

            Preconditions.checkStringNotEmpty(roleName, "roleName cannot be null or empty");
            Preconditions.checkArgumentIsSupported(DEFAULT_APPLICATION_ROLES, roleName);

            ArraySet<String> roleHolders = getOrCreateUserState(
                    userId).getRoleHolders(roleName);
            if (CollectionUtils.isEmpty(roleHolders)) {
                return null;
            }
            return roleHolders.valueAt(0);
        }

        @Override
        public void setDefaultApplicationAsUser(@NonNull String roleName,
                @Nullable String packageName, @RoleManager.ManageHoldersFlags int flags,
                @UserIdInt int userId, @NonNull RemoteCallback callback) {
            // The MANAGE_DEFAULT_APPLICATIONS permission is only available on U+
            Preconditions.checkState(SdkLevel.isAtLeastU(),
                    "This API is only available on Android 34 and above");
            UserUtils.enforceCrossUserPermission(userId, false, "setDefaultApplicationAsUser",
                    getContext());
            if (!UserUtils.isUserExistent(userId, getContext())) {
                Log.e(LOG_TAG, "user " + userId + " does not exist");
                return;
            }

            getContext().enforceCallingOrSelfPermission(
                    Manifest.permission.MANAGE_DEFAULT_APPLICATIONS, "setDefaultApplicationAsUser");

            Preconditions.checkStringNotEmpty(roleName, "roleName cannot be null or empty");
            Preconditions.checkArgumentIsSupported(DEFAULT_APPLICATION_ROLES, roleName);
            Objects.requireNonNull(callback, "callback cannot be null");

            RoleController roleController = getOrCreateController(userId);
            if (packageName != null) {
                roleController.onAddRoleHolder(roleName, packageName, flags, callback);
            } else {
                roleController.onClearRoleHolders(roleName, flags, callback);
            }
        }

        @Override
        public void addOnRoleHoldersChangedListenerAsUser(
                @NonNull IOnRoleHoldersChangedListener listener, @UserIdInt int userId) {
            UserUtils.enforceCrossUserPermission(userId, true,
                    "addOnRoleHoldersChangedListenerAsUser", getContext());
            if (userId != UserHandleCompat.USER_ALL && !UserUtils.isUserExistent(userId,
                    getContext())) {
                Log.e(LOG_TAG, "user " + userId + " does not exist");
                return;
            }

            getContext().enforceCallingOrSelfPermission(Manifest.permission.OBSERVE_ROLE_HOLDERS,
                    "addOnRoleHoldersChangedListenerAsUser");

            Objects.requireNonNull(listener, "listener cannot be null");

            RemoteCallbackList<IOnRoleHoldersChangedListener> listeners = getOrCreateListeners(
                    userId);
            listeners.register(listener);
        }

        @Override
        public void removeOnRoleHoldersChangedListenerAsUser(
                @NonNull IOnRoleHoldersChangedListener listener, @UserIdInt int userId) {
            UserUtils.enforceCrossUserPermission(userId, true,
                    "removeOnRoleHoldersChangedListenerAsUser", getContext());
            if (userId != UserHandleCompat.USER_ALL && !UserUtils.isUserExistent(userId,
                    getContext())) {
                Log.e(LOG_TAG, "user " + userId + " does not exist");
                return;
            }

            getContext().enforceCallingOrSelfPermission(Manifest.permission.OBSERVE_ROLE_HOLDERS,
                    "removeOnRoleHoldersChangedListenerAsUser");

            Objects.requireNonNull(listener, "listener cannot be null");

            RemoteCallbackList<IOnRoleHoldersChangedListener> listeners = getListeners(userId);
            if (listener == null) {
                return;
            }
            listeners.unregister(listener);
        }

        @Override
        public boolean isBypassingRoleQualification() {
            getContext().enforceCallingOrSelfPermission(Manifest.permission.MANAGE_ROLE_HOLDERS,
                    "isBypassingRoleQualification");

            synchronized (mLock) {
                return mBypassingRoleQualification;
            }
        }

        @Override
        public void setBypassingRoleQualification(boolean bypassRoleQualification) {
            getContext().enforceCallingOrSelfPermission(
                    Manifest.permission.BYPASS_ROLE_QUALIFICATION, "setBypassingRoleQualification");

            synchronized (mLock) {
                if (mBypassingRoleQualification == bypassRoleQualification) {
                    return;
                }
                mBypassingRoleQualification = bypassRoleQualification;

                final int userStatesSize = mUserStates.size();
                for (int i = 0; i < userStatesSize; i++) {
                    final RoleUserState userState = mUserStates.valueAt(i);

                    userState.setBypassingRoleQualification(bypassRoleQualification);
                }
            }
        }

        @Override
        public boolean isRoleFallbackEnabledAsUser(@NonNull String roleName,
                @UserIdInt int userId) {
            UserUtils.enforceCrossUserPermission(userId, false, "isRoleFallbackEnabledAsUser",
                    getContext());
            if (!UserUtils.isUserExistent(userId, getContext())) {
                Log.e(LOG_TAG, "user " + userId + " does not exist");
                return false;
            }

            getContext().enforceCallingOrSelfPermission(Manifest.permission.MANAGE_ROLE_HOLDERS,
                    "isRoleFallbackEnabledAsUser");

            Preconditions.checkStringNotEmpty(roleName, "roleName cannot be null or empty");

            return getOrCreateUserState(userId).isFallbackEnabled(roleName);
        }

        @Override
        public void setRoleFallbackEnabledAsUser(@NonNull String roleName, boolean fallbackEnabled,
                @UserIdInt int userId) {
            UserUtils.enforceCrossUserPermission(userId, false, "setRoleFallbackEnabledAsUser",
                    getContext());
            if (!UserUtils.isUserExistent(userId, getContext())) {
                Log.e(LOG_TAG, "user " + userId + " does not exist");
                return;
            }

            getContext().enforceCallingOrSelfPermission(Manifest.permission.MANAGE_ROLE_HOLDERS,
                    "setRoleFallbackEnabledAsUser");

            Preconditions.checkStringNotEmpty(roleName, "roleName cannot be null or empty");

            getOrCreateUserState(userId).setFallbackEnabled(roleName, fallbackEnabled);
        }

        @Override
        public void setRoleNamesFromControllerAsUser(@NonNull List<String> roleNames,
                @UserIdInt int userId) {
            UserUtils.enforceCrossUserPermission(userId, false, "setRoleNamesFromControllerAsUser",
                    getContext());
            if (!UserUtils.isUserExistent(userId, getContext())) {
                Log.e(LOG_TAG, "user " + userId + " does not exist");
                return;
            }

            getContext().enforceCallingOrSelfPermission(
                    RoleManager.PERMISSION_MANAGE_ROLES_FROM_CONTROLLER,
                    "setRoleNamesFromControllerAsUser");

            Objects.requireNonNull(roleNames, "roleNames cannot be null");

            getOrCreateUserState(userId).setRoleNames(roleNames);
        }

        @Override
        public boolean addRoleHolderFromControllerAsUser(@NonNull String roleName,
                @NonNull String packageName, @UserIdInt int userId) {
            UserUtils.enforceCrossUserPermission(userId, false,
                    "addRoleHolderFromControllerAsUser", getContext());
            if (!UserUtils.isUserExistent(userId, getContext())) {
                Log.e(LOG_TAG, "user " + userId + " does not exist");
                return false;
            }

            getContext().enforceCallingOrSelfPermission(
                    RoleManager.PERMISSION_MANAGE_ROLES_FROM_CONTROLLER,
                    "addRoleHolderFromControllerAsUser");

            Preconditions.checkStringNotEmpty(roleName, "roleName cannot be null or empty");
            Preconditions.checkStringNotEmpty(packageName, "packageName cannot be null or empty");

            return getOrCreateUserState(userId).addRoleHolder(roleName, packageName);
        }

        @Override
        public boolean removeRoleHolderFromControllerAsUser(@NonNull String roleName,
                @NonNull String packageName, @UserIdInt int userId) {
            UserUtils.enforceCrossUserPermission(userId, false,
                    "removeRoleHolderFromControllerAsUser", getContext());
            if (!UserUtils.isUserExistent(userId, getContext())) {
                Log.e(LOG_TAG, "user " + userId + " does not exist");
                return false;
            }

            getContext().enforceCallingOrSelfPermission(
                    RoleManager.PERMISSION_MANAGE_ROLES_FROM_CONTROLLER,
                    "removeRoleHolderFromControllerAsUser");

            Preconditions.checkStringNotEmpty(roleName, "roleName cannot be null or empty");
            Preconditions.checkStringNotEmpty(packageName, "packageName cannot be null or empty");

            return getOrCreateUserState(userId).removeRoleHolder(roleName, packageName);
        }

        @Override
        public List<String> getHeldRolesFromControllerAsUser(@NonNull String packageName,
                @UserIdInt int userId) {
            UserUtils.enforceCrossUserPermission(userId, false,
                    "getHeldRolesFromControllerAsUser", getContext());
            if (!UserUtils.isUserExistent(userId, getContext())) {
                Log.e(LOG_TAG, "user " + userId + " does not exist");
                return Collections.emptyList();
            }

            getContext().enforceCallingOrSelfPermission(
                    RoleManager.PERMISSION_MANAGE_ROLES_FROM_CONTROLLER,
                    "getHeldRolesFromControllerAsUser");

            Preconditions.checkStringNotEmpty(packageName, "packageName cannot be null or empty");

            return getOrCreateUserState(userId).getHeldRoles(packageName);
        }

        @Override
        public int handleShellCommand(@NonNull ParcelFileDescriptor in,
                @NonNull ParcelFileDescriptor out, @NonNull ParcelFileDescriptor err,
                @NonNull String[] args) {
            return new RoleShellCommand(this).exec(this, in.getFileDescriptor(),
                    out.getFileDescriptor(), err.getFileDescriptor(), args);
        }

        @Nullable
        @Override
        public String getBrowserRoleHolder(@UserIdInt int userId) {
            final int callingUid = Binder.getCallingUid();
            if (UserHandleCompat.getUserId(callingUid) != userId) {
                getContext().enforceCallingOrSelfPermission(
                        android.Manifest.permission.INTERACT_ACROSS_USERS_FULL, null);
            }
            if (isInstantApp(callingUid)) {
                return null;
            }

            final long identity = Binder.clearCallingIdentity();
            try {
                return CollectionUtils.firstOrNull(getRoleHoldersAsUser(RoleManager.ROLE_BROWSER,
                        userId));
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        private boolean isInstantApp(int uid) {
            final long identity = Binder.clearCallingIdentity();
            try {
                final UserHandle user = UserHandle.getUserHandleForUid(uid);
                final Context userContext = getContext().createContextAsUser(user, 0);
                final PackageManager userPackageManager = userContext.getPackageManager();
                // Instant apps can not have shared UID, so it's safe to check only the first
                // package name here.
                final String packageName = ArrayUtils.firstOrNull(
                        userPackageManager.getPackagesForUid(uid));
                if (packageName == null) {
                    return false;
                }
                return userPackageManager.isInstantApp(packageName);
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        @Override
        public boolean setBrowserRoleHolder(@Nullable String packageName, @UserIdInt int userId) {
            final Context context = getContext();
            context.enforceCallingOrSelfPermission(
                    android.Manifest.permission.SET_PREFERRED_APPLICATIONS, null);
            if (UserHandleCompat.getUserId(Binder.getCallingUid()) != userId) {
                context.enforceCallingOrSelfPermission(
                        android.Manifest.permission.INTERACT_ACROSS_USERS_FULL, null);
            }

            if (!UserUtils.isUserExistent(userId, context)) {
                return false;
            }

            final AndroidFuture<Void> future = new AndroidFuture<>();
            final RemoteCallback callback = new RemoteCallback(result -> {
                boolean successful = result != null;
                if (successful) {
                    future.complete(null);
                } else {
                    future.completeExceptionally(new RuntimeException());
                }
            });
            final long identity = Binder.clearCallingIdentity();
            try {
                if (packageName != null) {
                    addRoleHolderAsUser(RoleManager.ROLE_BROWSER, packageName, 0, userId, callback);
                } else {
                    clearRoleHoldersAsUser(RoleManager.ROLE_BROWSER, 0, userId, callback);
                }
                try {
                    future.get(5, TimeUnit.SECONDS);
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    Log.e(LOG_TAG, "Exception while setting default browser: " + packageName, e);
                    return false;
                }
            } finally {
                Binder.restoreCallingIdentity(identity);
            }

            return true;
        }

        @Override
        public String getSmsRoleHolder(int userId) {
            final Context context = getContext();
            UserUtils.enforceCrossUserPermission(userId, false, "getSmsRoleHolder", context);
            if (!UserUtils.isUserExistent(userId, getContext())) {
                Log.e(LOG_TAG, "user " + userId + " does not exist");
                return null;
            }

            final String packageName;
            final long identity = Binder.clearCallingIdentity();
            try {
                packageName = CollectionUtils.firstOrNull(getRoleHoldersAsUser(RoleManager.ROLE_SMS,
                        userId));
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
            if (packageName != null && !PackageUtils.canCallingOrSelfPackageQuery(packageName,
                    userId, context)) {
                return null;
            }
            return packageName;
        }

        @Override
        public String getEmergencyRoleHolder(int userId) {
            final Context context = getContext();
            UserUtils.enforceCrossUserPermission(userId, false, "getEmergencyRoleHolder", context);
            if (!UserUtils.isUserExistent(userId, getContext())) {
                Log.e(LOG_TAG, "user " + userId + " does not exist");
                return null;
            }

            getContext().enforceCallingOrSelfPermission(
                    Manifest.permission.READ_PRIVILEGED_PHONE_STATE, "getEmergencyRoleHolder");

            final String packageName;
            final long identity = Binder.clearCallingIdentity();
            try {
                packageName = CollectionUtils.firstOrNull(getRoleHoldersAsUser(
                        RoleManager.ROLE_EMERGENCY, userId));
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
            if (packageName != null && !PackageUtils.canCallingOrSelfPackageQuery(packageName,
                    userId, context)) {
                return null;
            }
            return packageName;
        }

        @Override
        public boolean isRoleVisibleAsUser(@NonNull String roleName, @UserIdInt int userId) {
            UserUtils.enforceCrossUserPermission(userId, false, "isRoleVisibleAsUser",
                    getContext());
            if (!UserUtils.isUserExistent(userId, getContext())) {
                Log.e(LOG_TAG, "user " + userId + " does not exist");
                return false;
            }

            getContext().enforceCallingOrSelfPermission(Manifest.permission.MANAGE_ROLE_HOLDERS,
                    "isRoleVisibleAsUser");

            Preconditions.checkStringNotEmpty(roleName, "roleName cannot be null or empty");

            return getOrCreateController(userId).isRoleVisible(roleName);
        }

        @Override
        public boolean isApplicationVisibleForRoleAsUser(@NonNull String roleName,
                @NonNull String packageName, @UserIdInt int userId) {
            UserUtils.enforceCrossUserPermission(userId, false,
                    "isApplicationVisibleForRoleAsUser", getContext());
            if (!UserUtils.isUserExistent(userId, getContext())) {
                Log.e(LOG_TAG, "user " + userId + " does not exist");
                return false;
            }

            getContext().enforceCallingOrSelfPermission(Manifest.permission.MANAGE_ROLE_HOLDERS,
                    "isApplicationVisibleForRoleAsUser");

            Preconditions.checkStringNotEmpty(roleName, "roleName cannot be null or empty");
            Preconditions.checkStringNotEmpty(packageName, "packageName cannot be null or empty");

            return getOrCreateController(userId).isApplicationVisibleForRole(roleName, packageName);
        }

        @Override
        protected void dump(@NonNull FileDescriptor fd, @NonNull PrintWriter fout,
                @Nullable String[] args) {
            if (!checkDumpPermission("role", fout)) {
                return;
            }

            boolean dumpAsProto = args != null && ArrayUtils.contains(args, "--proto");
            DualDumpOutputStream dumpOutputStream;
            if (dumpAsProto) {
                dumpOutputStream = new DualDumpOutputStream(new ProtoOutputStream(
                        new FileOutputStream(fd)));
            } else {
                fout.println("ROLE STATE (dumpsys role):");
                dumpOutputStream = new DualDumpOutputStream(new IndentingPrintWriter(fout, "  "));
            }

            synchronized (mLock) {
                final int userStatesSize = mUserStates.size();
                for (int i = 0; i < userStatesSize; i++) {
                    final RoleUserState userState = mUserStates.valueAt(i);

                    userState.dump(dumpOutputStream, "user_states",
                            RoleServiceDumpProto.USER_STATES);
                }
            }

            dumpOutputStream.flush();
        }

        private boolean checkDumpPermission(@NonNull String serviceName,
                @NonNull PrintWriter writer) {
            if (getContext().checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                    != PackageManager.PERMISSION_GRANTED) {
                writer.println("Permission Denial: can't dump " + serviceName + " from from pid="
                        + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid()
                        + " due to missing " + android.Manifest.permission.DUMP + " permission");
                return false;
            } else {
                return true;
            }
        }
    }

    private class Local implements RoleManagerLocal {
        @NonNull
        @Override
        public Map<String, Set<String>> getRolesAndHolders(@UserIdInt int userId) {
            // Convert ArrayMap<String, ArraySet<String>> to Map<String, Set<String>> for the API.
            //noinspection unchecked
            return (Map<String, Set<String>>) (Map<String, ?>)
                    getOrCreateUserState(userId).getRolesAndHolders();
        }
    }
}
