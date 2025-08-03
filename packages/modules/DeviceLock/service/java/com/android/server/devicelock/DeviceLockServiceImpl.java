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

package com.android.server.devicelock;

import static android.app.AppOpsManager.OPSTR_SYSTEM_EXEMPT_FROM_HIBERNATION;
import static android.app.role.RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP;
import static android.content.IntentFilter.SYSTEM_HIGH_PRIORITY;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.devicelock.DeviceId.DEVICE_ID_TYPE_IMEI;
import static android.devicelock.DeviceId.DEVICE_ID_TYPE_MEID;
import static android.provider.Settings.Secure.USER_SETUP_COMPLETE;

import android.Manifest;
import android.Manifest.permission;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.AppOpsManager;
import android.app.role.RoleManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ServiceInfo;
import android.database.ContentObserver;
import android.devicelock.DeviceId.DeviceIdType;
import android.devicelock.DeviceLockManager;
import android.devicelock.IDeviceLockService;
import android.devicelock.IGetDeviceIdCallback;
import android.devicelock.IGetKioskAppsCallback;
import android.devicelock.IIsDeviceLockedCallback;
import android.devicelock.ILockUnlockDeviceCallback;
import android.devicelock.ParcelableException;
import android.net.NetworkPolicyManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.OutcomeReceiver;
import android.os.PowerExemptionManager;
import android.os.RemoteCallback;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Implementation of {@link android.devicelock.IDeviceLockService} binder service.
 */
final class DeviceLockServiceImpl extends IDeviceLockService.Stub {
    private static final String TAG = "DeviceLockServiceImpl";

    // Keep this in sync with NetworkPolicyManager#POLICY_NONE.
    private static final int POLICY_NONE = 0x0;
    //Keep this in sync with NetworkPolicyManager#POLICY_ALLOW_METERED_BACKGROUND.
    private static final int POLICY_ALLOW_METERED_BACKGROUND = 0x4;
    private static final String ACTION_DEVICE_LOCK_KEEPALIVE =
            "com.android.devicelock.action.KEEPALIVE";

    // Workaround for timeout while adding the kiosk app as role holder for financing.
    private static final int MAX_ADD_ROLE_HOLDER_TRIES = 4;

    private final Context mContext;
    private final ExecutorService mExecutorService = Executors.newCachedThreadPool();

    private final RoleManager mRoleManager;
    private final TelephonyManager mTelephonyManager;
    private final AppOpsManager mAppOpsManager;

    // Map user id -> DeviceLockControllerConnector
    @GuardedBy("this")
    private final ArrayMap<Integer, DeviceLockControllerConnector> mDeviceLockControllerConnectors;

    private final DeviceLockControllerConnectorStub mDeviceLockControllerConnectorStub =
            new DeviceLockControllerConnectorStub();

    private final DeviceLockControllerPackageUtils mPackageUtils;

    private final ServiceInfo mServiceInfo;

    // Map user id -> ServiceConnection for kiosk keepalive.
    private final ArrayMap<Integer, KeepaliveServiceConnection> mKioskKeepaliveServiceConnections;

    // Map user id -> ServiceConnection for controller keepalive.
    private final ArrayMap<Integer, KeepaliveServiceConnection>
            mControllerKeepaliveServiceConnections;

    private final DeviceLockPersistentStore mPersistentStore;

    private boolean mUseStubConnector = false;

    // The following string constants should be a SystemApi on AppOpsManager.
    @VisibleForTesting
    static final String OPSTR_SYSTEM_EXEMPT_FROM_ACTIVITY_BG_START_RESTRICTION =
            "android:system_exempt_from_activity_bg_start_restriction";
    @VisibleForTesting
    static final String OPSTR_SYSTEM_EXEMPT_FROM_DISMISSIBLE_NOTIFICATIONS =
            "android:system_exempt_from_dismissible_notifications";
    @VisibleForTesting
    static final String OPSTR_SYSTEM_EXEMPT_FROM_POWER_RESTRICTIONS =
            "android:system_exempt_from_power_restrictions";

    // Stopgap: this receiver should be replaced by an API on DeviceLockManager.
    private final class DeviceLockClearReceiver extends BroadcastReceiver {
        static final String ACTION_CLEAR = "com.android.devicelock.intent.action.CLEAR";
        static final int CLEAR_SUCCEEDED = 0;
        static final int CLEAR_FAILED = 1;

        @Override
        public void onReceive(Context context, Intent intent) {
            Slog.i(TAG, "Received request to clear device");

            // This receiver should be the only one.
            // The result will still be sent to the 'resultReceiver' of 'sendOrderedBroadcast'.
            abortBroadcast();

            final UserHandle userHandle = getSendingUser();

            final PendingResult pendingResult = goAsync();

            getDeviceLockControllerConnector(userHandle).clearDeviceRestrictions(
                    new OutcomeReceiver<>() {

                        private void setResult(int resultCode) {
                            pendingResult.setResultCode(resultCode);

                            pendingResult.finish();
                        }

                        @Override
                        public void onResult(Void ignored) {
                            Slog.i(TAG, "Device cleared ");

                            setResult(DeviceLockClearReceiver.CLEAR_SUCCEEDED);
                        }

                        @Override
                        public void onError(Exception ex) {
                            Slog.e(TAG, "Exception clearing device: ", ex);

                            setResult(DeviceLockClearReceiver.CLEAR_FAILED);
                        }
                    });
        }
    }

    // Last supported device id type
    private static final @DeviceIdType int LAST_DEVICE_ID_TYPE = DEVICE_ID_TYPE_MEID;

    @VisibleForTesting
    static final String MANAGE_DEVICE_LOCK_SERVICE_FROM_CONTROLLER =
            "com.android.devicelockcontroller.permission."
                    + "MANAGE_DEVICE_LOCK_SERVICE_FROM_CONTROLLER";

    @NonNull
    private DeviceLockControllerConnector getDeviceLockControllerConnector(UserHandle userHandle) {
        synchronized (this) {
            if (mUseStubConnector) {
                return mDeviceLockControllerConnectorStub;
            } else {
                final int userId = userHandle.getIdentifier();
                DeviceLockControllerConnector deviceLockControllerConnector =
                        mDeviceLockControllerConnectors.get(userId);
                if (deviceLockControllerConnector == null) {
                    final ComponentName componentName = new ComponentName(mServiceInfo.packageName,
                            mServiceInfo.name);
                    deviceLockControllerConnector = new DeviceLockControllerConnectorImpl(mContext,
                            componentName, userHandle);
                    mDeviceLockControllerConnectors.put(userId, deviceLockControllerConnector);
                }

                return deviceLockControllerConnector;
            }
        }
    }

    @NonNull
    private DeviceLockControllerConnector getDeviceLockControllerConnector() {
        final UserHandle userHandle = Binder.getCallingUserHandle();
        return getDeviceLockControllerConnector(userHandle);
    }

    DeviceLockServiceImpl(@NonNull Context context) {
        this(context, context.getSystemService(TelephonyManager.class));
    }

    @VisibleForTesting
    DeviceLockServiceImpl(@NonNull Context context, TelephonyManager telephonyManager) {
        mContext = context;
        mTelephonyManager = telephonyManager;

        mRoleManager = context.getSystemService(RoleManager.class);
        mAppOpsManager = context.getSystemService(AppOpsManager.class);

        mDeviceLockControllerConnectors = new ArrayMap<>();

        mKioskKeepaliveServiceConnections = new ArrayMap<>();
        mControllerKeepaliveServiceConnections = new ArrayMap<>();

        mPackageUtils = new DeviceLockControllerPackageUtils(context);

        mPersistentStore = new DeviceLockPersistentStore();

        final StringBuilder errorMessage = new StringBuilder();
        mServiceInfo = mPackageUtils.findService(errorMessage);

        if (mServiceInfo == null) {
            throw new RuntimeException(errorMessage.toString());
        }

        enforceDeviceLockControllerPackageEnabledState(UserHandle.SYSTEM);

        final IntentFilter intentFilter = new IntentFilter(DeviceLockClearReceiver.ACTION_CLEAR);
        // Run before any eventual app receiver (there should be none).
        intentFilter.setPriority(SYSTEM_HIGH_PRIORITY);
        context.registerReceiverForAllUsers(new DeviceLockClearReceiver(), intentFilter,
                Manifest.permission.MANAGE_DEVICE_LOCK_STATE, null /* scheduler */,
                Context.RECEIVER_EXPORTED);
    }

    void enforceDeviceLockControllerPackageEnabledState(@NonNull UserHandle userHandle) {
        mPersistentStore.readFinalizedState(
                isFinalized -> setDeviceLockControllerPackageEnabledState(userHandle, !isFinalized),
                mContext.getMainExecutor());
    }

    private void setDeviceLockControllerPackageEnabledState(UserHandle userHandle,
            boolean enabled) {
        final String controllerPackageName = mServiceInfo.packageName;

        Context controllerContext;
        try {
            controllerContext = mContext.createPackageContextAsUser(controllerPackageName,
                    0 /* flags */, userHandle);
        } catch (NameNotFoundException e) {
            Slog.e(TAG, "Cannot create package context for: " + userHandle, e);

            return;
        }

        final PackageManager controllerPackageManager = controllerContext.getPackageManager();

        final int enableState =
                enabled ? COMPONENT_ENABLED_STATE_DEFAULT : COMPONENT_ENABLED_STATE_DISABLED;
        // We cannot check if user control is disabled since
        // DevicePolicyManager.getUserControlDisabledPackages() acts on the calling user.
        // Additionally, we would have to catch SecurityException anyways to avoid TOCTOU bugs
        // since checking and setting is not atomic.
        try {
            controllerPackageManager.setApplicationEnabledSetting(controllerPackageName,
                    enableState, enabled ? DONT_KILL_APP : 0);
        } catch (SecurityException ex) {
            // This exception is thrown when Device Lock Controller has already enabled
            // package protection for itself. This is an expected behaviour.
            // Note: the exception description thrown by
            // PackageManager.setApplicationEnabledSetting() is somehow misleading because it says
            // that a protected package cannot be disabled (but we're actually trying to enable it).
        }
        if (!enabled) {
            synchronized (this) {
                mUseStubConnector = true;
                mDeviceLockControllerConnectors.clear();
            }
        }
    }

    void onUserSwitching(@NonNull UserHandle userHandle) {
        getDeviceLockControllerConnector(userHandle).onUserSwitching(new OutcomeReceiver<>() {
            @Override
            public void onResult(Void ignored) {
                Slog.i(TAG, "User switching reported for: " + userHandle);
            }

            @Override
            public void onError(Exception ex) {
                Slog.e(TAG, "Exception reporting user switching for: " + userHandle, ex);
            }
        });
    }

    void onUserUnlocked(@NonNull Context userContext, @NonNull UserHandle userHandle) {
        mExecutorService.execute(() -> {
            getDeviceLockControllerConnector(userHandle).onUserUnlocked(new OutcomeReceiver<>() {
                @Override
                public void onResult(Void ignored) {
                    Slog.i(TAG, "User unlocked reported for: " + userHandle);
                }

                @Override
                public void onError(Exception ex) {
                    Slog.e(TAG, "Exception reporting user unlocked for: " + userHandle, ex);
                }
            });
            // TODO(b/312521897): Add unit tests for this flow
            registerUserSetupCompleteListener(userContext, userHandle);
        });
    }

    private void registerUserSetupCompleteListener(Context userContext, UserHandle userHandle) {
        final ContentResolver contentResolver = userContext.getContentResolver();
        Uri setupCompleteUri = Settings.Secure.getUriFor(USER_SETUP_COMPLETE);
        contentResolver.registerContentObserver(setupCompleteUri,
                false /* notifyForDescendants */, new ContentObserver(null /* handler */) {
                    @Override
                    public void onChange(boolean selfChange, @Nullable Uri uri) {
                        if (setupCompleteUri.equals(uri)
                                && Settings.Secure.getInt(
                                contentResolver, USER_SETUP_COMPLETE, 0) != 0) {
                            onUserSetupCompleted(userHandle);
                        }
                    }
                });
    }

    void onUserSetupCompleted(UserHandle userHandle) {
        getDeviceLockControllerConnector(userHandle).onUserSetupCompleted(new OutcomeReceiver<>() {
            @Override
            public void onResult(Void ignored) {
                Slog.i(TAG, "User set up complete reported for: " + userHandle);
            }

            @Override
            public void onError(Exception ex) {
                Slog.e(TAG, "Exception reporting user setup complete for: " + userHandle, ex);
            }
        });
    }

    private boolean checkCallerPermission() {
        return mContext.checkCallingOrSelfPermission(Manifest.permission.MANAGE_DEVICE_LOCK_STATE)
                == PERMISSION_GRANTED;
    }

    private void reportDeviceLockedUnlocked(@NonNull ILockUnlockDeviceCallback callback,
            @Nullable Exception exception) {
        try {
            if (exception == null) {
                callback.onDeviceLockedUnlocked();
            } else {
                callback.onError(getParcelableException(exception));
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "Unable to send result to the callback", e);
        }
    }

    private OutcomeReceiver<Void, Exception> getLockUnlockOutcomeReceiver(
            @NonNull ILockUnlockDeviceCallback callback, @NonNull String successMessage) {
        return new OutcomeReceiver<>() {
            @Override
            public void onResult(Void ignored) {
                Slog.i(TAG, successMessage);
                reportDeviceLockedUnlocked(callback, /* exception= */ null);
            }

            @Override
            public void onError(Exception ex) {
                Slog.e(TAG, "Exception: ", ex);
                reportDeviceLockedUnlocked(callback, ex);
            }
        };
    }

    private ParcelableException getParcelableException(Exception exception) {
        return exception instanceof ParcelableException ? (ParcelableException) exception
                : new ParcelableException(exception);
    }

    @Override
    public void lockDevice(@NonNull ILockUnlockDeviceCallback callback) {
        if (!checkCallerPermission()) {
            try {
                callback.onError(new ParcelableException(new SecurityException()));
            } catch (RemoteException e) {
                Slog.e(TAG, "lockDevice() - Unable to send error to the callback", e);
            }
            return;
        }

        getDeviceLockControllerConnector().lockDevice(
                getLockUnlockOutcomeReceiver(callback, "Device locked"));
    }

    @Override
    public void unlockDevice(@NonNull ILockUnlockDeviceCallback callback) {
        if (!checkCallerPermission()) {
            try {
                callback.onError(new ParcelableException(new SecurityException()));
            } catch (RemoteException e) {
                Slog.e(TAG, "unlockDevice() - Unable to send error to the callback", e);
            }
            return;
        }

        getDeviceLockControllerConnector().unlockDevice(
                getLockUnlockOutcomeReceiver(callback, "Device unlocked"));
    }

    @Override
    public void isDeviceLocked(@NonNull IIsDeviceLockedCallback callback) {
        if (!checkCallerPermission()) {
            try {
                callback.onError(new ParcelableException(new SecurityException()));
            } catch (RemoteException e) {
                Slog.e(TAG, "isDeviceLocked() - Unable to send error to the callback", e);
            }
            return;
        }

        getDeviceLockControllerConnector().isDeviceLocked(new OutcomeReceiver<>() {
            @Override
            public void onResult(Boolean isLocked) {
                Slog.i(TAG, isLocked ? "Device is locked" : "Device is not locked");
                try {
                    callback.onIsDeviceLocked(isLocked);
                } catch (RemoteException e) {
                    Slog.e(TAG, "isDeviceLocked() - Unable to send result to the " + "callback", e);
                }
            }

            @Override
            public void onError(Exception ex) {
                Slog.e(TAG, "isDeviceLocked exception: ", ex);
                try {
                    callback.onError(getParcelableException(ex));
                } catch (RemoteException e) {
                    Slog.e(TAG, "isDeviceLocked() - Unable to send error to the " + "callback", e);
                }
            }
        });
    }

    @VisibleForTesting
    void getDeviceId(@NonNull IGetDeviceIdCallback callback, int deviceIdTypeBitmap) {
        try {
            if (deviceIdTypeBitmap < 0 || deviceIdTypeBitmap >= (1 << (LAST_DEVICE_ID_TYPE + 1))) {
                callback.onError(new ParcelableException("Invalid device type"));
                return;
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "getDeviceId() - Unable to send result to the callback", e);
        }

        int activeModemCount = mTelephonyManager.getActiveModemCount();
        List<String> imeiList = new ArrayList<String>();
        List<String> meidList = new ArrayList<String>();

        if ((deviceIdTypeBitmap & (1 << DEVICE_ID_TYPE_IMEI)) != 0) {
            for (int i = 0; i < activeModemCount; i++) {
                String imei = mTelephonyManager.getImei(i);
                if (!TextUtils.isEmpty(imei)) {
                    imeiList.add(imei);
                }
            }
        }

        if ((deviceIdTypeBitmap & (1 << DEVICE_ID_TYPE_MEID)) != 0) {
            for (int i = 0; i < activeModemCount; i++) {
                String meid = mTelephonyManager.getMeid(i);
                if (!TextUtils.isEmpty(meid)) {
                    meidList.add(meid);
                }
            }
        }

        getDeviceLockControllerConnector().getDeviceId(new OutcomeReceiver<>() {
            @Override
            public void onResult(String deviceId) {
                Slog.i(TAG, "Get Device ID ");
                try {
                    if (meidList.contains(deviceId)) {
                        callback.onDeviceIdReceived(DEVICE_ID_TYPE_MEID, deviceId);
                        return;
                    }
                    if (imeiList.contains(deviceId)) {
                        callback.onDeviceIdReceived(DEVICE_ID_TYPE_IMEI, deviceId);
                        return;
                    }
                    // When a device ID is returned from DLC App, but none of the IDs got from
                    // TelephonyManager matches that device ID.
                    //
                    // TODO(b/270392813): Send the device ID back to the callback with
                    //  UNSPECIFIED device ID type.
                    callback.onError(new ParcelableException("Unable to get device id"));
                } catch (RemoteException e) {
                    Slog.e(TAG, "getDeviceId() - Unable to send result to the callback", e);
                }
            }

            @Override
            public void onError(Exception ex) {
                Slog.e(TAG, "Exception: ", ex);
                try {
                    callback.onError(getParcelableException(ex));
                } catch (RemoteException e) {
                    Slog.e(TAG,
                            "getDeviceId() - " + "Unable to send error to" + " the " + "callback",
                            e);
                }
            }
        });
    }

    @Override
    public void getDeviceId(@NonNull IGetDeviceIdCallback callback) {
        if (!checkCallerPermission()) {
            try {
                callback.onError(new ParcelableException(new SecurityException()));
            } catch (RemoteException e) {
                Slog.e(TAG, "getDeviceId() - Unable to send error to the callback", e);
            }
            return;
        }

        final StringBuilder errorBuilder = new StringBuilder();

        final long identity = Binder.clearCallingIdentity();
        final int deviceIdTypeBitmap = mPackageUtils.getDeviceIdTypeBitmap(errorBuilder);
        Binder.restoreCallingIdentity(identity);

        if (deviceIdTypeBitmap < 0) {
            Slog.e(TAG, "getDeviceId: " + errorBuilder);
        }

        getDeviceId(callback, deviceIdTypeBitmap);
    }

    @Override
    public void getKioskApps(@NonNull IGetKioskAppsCallback callback) {
        // Caller is not necessarily a kiosk app, and no particular permission enforcing is needed.

        final ArrayMap kioskApps = new ArrayMap<Integer, String>();

        final UserHandle userHandle = Binder.getCallingUserHandle();
        final long identity = Binder.clearCallingIdentity();
        try {
            List<String> roleHolders = mRoleManager.getRoleHoldersAsUser(
                    RoleManager.ROLE_FINANCED_DEVICE_KIOSK, userHandle);

            if (!roleHolders.isEmpty()) {
                kioskApps.put(DeviceLockManager.DEVICE_LOCK_ROLE_FINANCING, roleHolders.get(0));
            }

            callback.onKioskAppsReceived(kioskApps);
        } catch (RemoteException e) {
            Slog.e(TAG, "getKioskApps() - Unable to send result to the callback", e);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    // For calls from Controller to System Service.

    private void reportErrorToCaller(@NonNull RemoteCallback remoteCallback) {
        final Bundle result = new Bundle();
        result.putBoolean(KEY_REMOTE_CALLBACK_RESULT, false);
        remoteCallback.sendResult(result);
    }

    private boolean checkDeviceLockControllerPermission(@NonNull RemoteCallback remoteCallback) {
        if (mContext.checkCallingOrSelfPermission(MANAGE_DEVICE_LOCK_SERVICE_FROM_CONTROLLER)
                != PERMISSION_GRANTED) {
            reportErrorToCaller(remoteCallback);
            return false;
        }

        return true;
    }

    private void reportResult(boolean accepted, long identity,
            @NonNull RemoteCallback remoteCallback) {
        Binder.restoreCallingIdentity(identity);

        final Bundle result = new Bundle();
        result.putBoolean(KEY_REMOTE_CALLBACK_RESULT, accepted);
        remoteCallback.sendResult(result);
    }

    private void addFinancedDeviceKioskRoleInternal(@NonNull String packageName,
            @NonNull RemoteCallback remoteCallback, @NonNull UserHandle userHandle, long identity,
            int remainingTries) {
        mRoleManager.addRoleHolderAsUser(RoleManager.ROLE_FINANCED_DEVICE_KIOSK, packageName,
                MANAGE_HOLDERS_FLAG_DONT_KILL_APP, userHandle, mContext.getMainExecutor(),
                accepted -> {
                    if (accepted || remainingTries == 1) {
                        reportResult(accepted, identity, remoteCallback);
                    } else {
                        final int retryNumber = MAX_ADD_ROLE_HOLDER_TRIES - remainingTries + 1;
                        Slog.w(TAG, "Retrying adding financed device role to kiosk app (retry "
                                + retryNumber + ")");
                        addFinancedDeviceKioskRoleInternal(packageName, remoteCallback, userHandle,
                                identity, remainingTries - 1);
                    }
                });
    }

    @Override
    public void addFinancedDeviceKioskRole(@NonNull String packageName,
            @NonNull RemoteCallback remoteCallback) {
        if (!checkDeviceLockControllerPermission(remoteCallback)) {
            return;
        }

        final UserHandle userHandle = Binder.getCallingUserHandle();
        final long identity = Binder.clearCallingIdentity();

        addFinancedDeviceKioskRoleInternal(packageName, remoteCallback, userHandle, identity,
                MAX_ADD_ROLE_HOLDER_TRIES);

        Binder.restoreCallingIdentity(identity);
    }

    @Override
    public void removeFinancedDeviceKioskRole(@NonNull String packageName,
            @NonNull RemoteCallback remoteCallback) {
        if (!checkDeviceLockControllerPermission(remoteCallback)) {
            return;
        }

        final UserHandle userHandle = Binder.getCallingUserHandle();
        final long identity = Binder.clearCallingIdentity();

        // Clear the FLAG_PERMISSION_GRANTED_BY_ROLE flag from POST_NOTIFICATIONS for the kiosk app
        // before removing the ROLE_FINANCED_DEVICE_KIOSK role, to prevent the app from being
        // killed.
        final PackageManager packageManager = mContext.getPackageManager();
        packageManager.updatePermissionFlags(permission.POST_NOTIFICATIONS, packageName,
                PackageManager.FLAG_PERMISSION_GRANTED_BY_ROLE, /* flagValues= */ 0, userHandle);

        mRoleManager.removeRoleHolderAsUser(RoleManager.ROLE_FINANCED_DEVICE_KIOSK, packageName,
                MANAGE_HOLDERS_FLAG_DONT_KILL_APP, userHandle, mContext.getMainExecutor(),
                accepted -> reportResult(accepted, identity, remoteCallback));

        Binder.restoreCallingIdentity(identity);
    }

    /**
     * @param uid         The uid whose AppOps mode needs to change.
     * @param packageName The name of the package whose AppOp mode needs to change.
     * @param appOps      A list of appOps to change
     * @param allowed     If true, the mode would be set to {@link AppOpsManager#MODE_ALLOWED};
     *                    false,
     *                    the mode would be set to {@link AppOpsManager#MODE_DEFAULT}.
     * @return a boolean value indicates whether the app ops modes have been changed to the
     * requested value.
     */
    private boolean setAppOpsModes(int uid, String packageName, String[] appOps, boolean allowed) {
        final int mode = allowed ? AppOpsManager.MODE_ALLOWED : AppOpsManager.MODE_DEFAULT;

        long identity = Binder.clearCallingIdentity();
        for (String appOp : appOps) {
            mAppOpsManager.setMode(appOp, uid, packageName, mode);
        }
        Binder.restoreCallingIdentity(identity);
        return true;
    }

    /**
     * Set the exemption state for activity background start restriction for the calling uid.
     * Caller must hold the {@link MANAGE_DEVICE_LOCK_SERVICE_FROM_CONTROLLER} permission.
     *
     * @param exempt if true, the calling uid will be set to exempt from activity background start
     *               restriction; false, the exemption state will be set to default.
     */
    @Override
    public void setCallerExemptFromActivityBgStartRestrictionState(boolean exempt,
            @NonNull RemoteCallback remoteCallback) {
        if (!checkDeviceLockControllerPermission(remoteCallback)) {
            return;
        }
        Bundle result = new Bundle();
        result.putBoolean(KEY_REMOTE_CALLBACK_RESULT,
                setAppOpsModes(Binder.getCallingUid(), mServiceInfo.packageName,
                        new String[]{OPSTR_SYSTEM_EXEMPT_FROM_ACTIVITY_BG_START_RESTRICTION},
                        exempt));
        remoteCallback.sendResult(result);
    }

    /**
     * Set whether the caller is allowed to send undismissible notifications.
     *
     * @param allowed true if the caller can send undismissible notifications, false otherwise
     */
    @Override
    public void setCallerAllowedToSendUndismissibleNotifications(boolean allowed,
            @NonNull RemoteCallback remoteCallback) {
        if (!checkDeviceLockControllerPermission(remoteCallback)) {
            return;
        }
        Bundle result = new Bundle();
        result.putBoolean(KEY_REMOTE_CALLBACK_RESULT,
                setAppOpsModes(Binder.getCallingUid(), mServiceInfo.packageName,
                        new String[]{OPSTR_SYSTEM_EXEMPT_FROM_DISMISSIBLE_NOTIFICATIONS}, allowed));
        remoteCallback.sendResult(result);
    }

    /**
     * @param uid   The uid whose network policy needs to change.
     * @param allow whether to allow background data usage in metered data mode.
     * @return a boolean value indicates whether the policy change is a success.
     */
    private boolean setNetworkPolicyForUid(int uid, boolean allow) {
        boolean result;
        long caller = Binder.clearCallingIdentity();
        try {
            // TODO(b/319266027): Figure out a long term solution instead of using reflection here.
            NetworkPolicyManager networkPolicyManager = mContext.getSystemService(
                    NetworkPolicyManager.class);
            NetworkPolicyManager.class.getDeclaredMethod("setUidPolicy", Integer.TYPE,
                    Integer.TYPE).invoke(networkPolicyManager, uid,
                    allow ? POLICY_ALLOW_METERED_BACKGROUND : POLICY_NONE);
            result = true;
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            Slog.e(TAG, "Failed to exempt data usage for given uid: " + uid, e);
            result = false;
        }
        Binder.restoreCallingIdentity(caller);
        return result;
    }

    private boolean setPowerExemptionForPackage(String packageName, boolean allow) {
        boolean result;
        long caller = Binder.clearCallingIdentity();
        try {
            // TODO(b/321539640): Figure out a long term solution instead of using reflection here.
            PowerExemptionManager powerExemptionManager = mContext.getSystemService(
                    PowerExemptionManager.class);
            String methodName = allow ? "addToPermanentAllowList" : "removeFromPermanentAllowList";
            PowerExemptionManager.class.getDeclaredMethod(methodName, String.class).invoke(
                    powerExemptionManager, packageName);
            result = true;
        } catch (InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
            Slog.e(TAG, "Failed to exempt power usage for given package: " + packageName, e);
            result = false;
        }
        Binder.restoreCallingIdentity(caller);
        return result;
    }

    /**
     * Set the exemption state for app restrictions(e.g. hibernation, battery and data usage
     * restriction) for the given uid
     * Caller must hold the {@link MANAGE_DEVICE_LOCK_SERVICE_FROM_CONTROLLER} permission.
     *
     * @param exempt if true, the given uid will be set to exempt from hibernation, battery and data
     *               usage restriction; false, the exemption state will be set to default.
     */
    @Override
    public void setUidExemptFromRestrictionsState(int uid, boolean exempt,
            @NonNull RemoteCallback remoteCallback) {
        if (!checkDeviceLockControllerPermission(remoteCallback)) {
            return;
        }
        boolean setAppOpsResult = false;
        boolean setPowerExemptionResult = false;

        String[] packageNames = mContext.getPackageManager().getPackagesForUid(uid);
        if (packageNames == null || packageNames.length < 1) {
            Slog.e(TAG, "Can not find package name for given uid: " + uid);
        } else {
            setAppOpsResult = setAppOpsModes(uid, packageNames[0],
                    new String[]{OPSTR_SYSTEM_EXEMPT_FROM_HIBERNATION,
                            OPSTR_SYSTEM_EXEMPT_FROM_POWER_RESTRICTIONS}, exempt);
            setPowerExemptionResult = setPowerExemptionForPackage(packageNames[0], exempt);
        }
        boolean setNetworkPolicyResult = setNetworkPolicyForUid(uid, exempt);
        Bundle result = new Bundle();
        result.putBoolean(KEY_REMOTE_CALLBACK_RESULT,
                setAppOpsResult && setPowerExemptionResult && setNetworkPolicyResult);
        remoteCallback.sendResult(result);
    }

    private class KeepaliveServiceConnection implements ServiceConnection {
        final boolean mIsKiosk;
        final String mPackageName;
        final UserHandle mUserHandle;

        final Intent mService;

        KeepaliveServiceConnection(boolean isKiosk, String packageName, UserHandle userHandle) {
            super();
            mIsKiosk = isKiosk;
            mPackageName = packageName;
            mUserHandle = userHandle;
            mService = new Intent(ACTION_DEVICE_LOCK_KEEPALIVE).setPackage(packageName);
        }

        private boolean bind() {
            return mContext.bindServiceAsUser(mService, this, Context.BIND_AUTO_CREATE,
                    mUserHandle);
        }

        private boolean rebind() {
            mContext.unbindService(this);
            boolean bound = bind();

            if (bound) {
                getDeviceLockControllerConnector(mUserHandle).onAppCrashed(mIsKiosk,
                        new OutcomeReceiver<>() {
                            @Override
                            public void onResult(Void result) {
                                Slog.i(TAG,
                                        "Notified controller about " + mPackageName + " crash");
                            }

                            @Override
                            public void onError(Exception ex) {
                                Slog.e(TAG, "On " + mPackageName + " crashed error: ", ex);
                            }
                        });
            }

            return bound;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Slog.i(TAG, mPackageName + " keepalive successful for user " + mUserHandle);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (rebind()) {
                Slog.i(TAG,
                        "onServiceDisconnected rebind successful for " + mPackageName + " user "
                                + mUserHandle);
            } else {
                Slog.e(TAG, "onServiceDisconnected rebind failed for " + mPackageName + " user "
                        + mUserHandle);
            }
        }

        @Override
        public void onBindingDied(ComponentName name) {
            ServiceConnection.super.onBindingDied(name);
            if (rebind()) {
                Slog.i(TAG, "onBindingDied rebind successful for " + mPackageName + " user "
                        + mUserHandle);
            } else {
                Slog.e(TAG,
                        "onBindingDied rebind failed for " + mPackageName + " user "
                                + mUserHandle);
            }
        }
    }

    @Override
    public void enableKioskKeepalive(String packageName, @NonNull RemoteCallback remoteCallback) {
        enableKeepalive(true /* forKiosk */, packageName, remoteCallback);
    }

    @Override
    public void disableKioskKeepalive(@NonNull RemoteCallback remoteCallback) {
        disableKeepalive(true /* forKiosk */, remoteCallback);
    }

    @Override
    public void enableControllerKeepalive(@NonNull RemoteCallback remoteCallback) {
        enableKeepalive(false /* forKiosk */, mServiceInfo.packageName, remoteCallback);
    }

    @Override
    public void disableControllerKeepalive(@NonNull RemoteCallback remoteCallback) {
        disableKeepalive(false /* forKiosk */, remoteCallback);
    }

    private void enableKeepalive(boolean forKiosk, String packageName,
            @NonNull RemoteCallback remoteCallback) {
        final UserHandle controllerUserHandle = Binder.getCallingUserHandle();
        final int controllerUserId = controllerUserHandle.getIdentifier();
        boolean keepaliveEnabled = false;
        final ArrayMap<Integer, KeepaliveServiceConnection> keepaliveServiceConnections =
                forKiosk ? mKioskKeepaliveServiceConnections
                        : mControllerKeepaliveServiceConnections;

        synchronized (this) {
            if (keepaliveServiceConnections.get(controllerUserId) == null) {
                final KeepaliveServiceConnection serviceConnection =
                        new KeepaliveServiceConnection(
                                forKiosk, packageName, controllerUserHandle);
                final long identity = Binder.clearCallingIdentity();
                if (serviceConnection.bind()) {
                    keepaliveServiceConnections.put(controllerUserId, serviceConnection);
                    keepaliveEnabled = true;
                } else {
                    Slog.w(TAG, "enableKeepalive: failed to bind to keepalive service "
                            + " for package: " + packageName + " user:" + controllerUserHandle);
                    mContext.unbindService(serviceConnection);
                }
                Binder.restoreCallingIdentity(identity);
            } else {
                // Consider success if we already have an entry for this user id.
                keepaliveEnabled = true;
            }
        }

        final Bundle result = new Bundle();
        result.putBoolean(KEY_REMOTE_CALLBACK_RESULT, keepaliveEnabled);
        remoteCallback.sendResult(result);
    }

    private void disableKeepalive(boolean isKiosk, @NonNull RemoteCallback remoteCallback) {
        final UserHandle controllerUserHandle = Binder.getCallingUserHandle();
        final int controllerUserId = controllerUserHandle.getIdentifier();
        final KeepaliveServiceConnection serviceConnection;
        final ArrayMap<Integer, KeepaliveServiceConnection> keepaliveServiceConnections =
                isKiosk ? mKioskKeepaliveServiceConnections
                        : mControllerKeepaliveServiceConnections;

        synchronized (this) {
            serviceConnection = keepaliveServiceConnections.remove(controllerUserId);
        }

        if (serviceConnection != null) {
            final long identity = Binder.clearCallingIdentity();
            mContext.unbindService(serviceConnection);
            Binder.restoreCallingIdentity(identity);
        } else {
            final String target = isKiosk ? "kiosk" : "controller";
            Slog.e(TAG,
                    "disableKeepalive: Service connection to " + target
                            + " not found for user: "
                            + controllerUserHandle);
        }

        final Bundle result = new Bundle();
        result.putBoolean(KEY_REMOTE_CALLBACK_RESULT, serviceConnection != null);
        remoteCallback.sendResult(result);
    }

    @Override
    public void setDeviceFinalized(boolean finalized, @NonNull RemoteCallback remoteCallback) {
        mPersistentStore.scheduleWrite(finalized);
        setDeviceLockControllerPackageEnabledState(getCallingUserHandle(), false /* enabled */);

        final Bundle result = new Bundle();
        result.putBoolean(KEY_REMOTE_CALLBACK_RESULT, true);
        remoteCallback.sendResult(result);
    }

    @Override
    public void setPostNotificationsSystemFixed(boolean systemFixed,
            @NonNull RemoteCallback remoteCallback) {
        final UserHandle userHandle = Binder.getCallingUserHandle();
        final PackageManager packageManager = mContext.getPackageManager();
        final int permissionFlags = PackageManager.FLAG_PERMISSION_SYSTEM_FIXED;
        final int newFlagValues = systemFixed ? permissionFlags : 0;
        final long identity = Binder.clearCallingIdentity();
        // Make sure permission hasn't been revoked.
        packageManager.grantRuntimePermission(mServiceInfo.packageName,
                permission.POST_NOTIFICATIONS, userHandle);
        packageManager.updatePermissionFlags(permission.POST_NOTIFICATIONS,
                mServiceInfo.packageName, permissionFlags, newFlagValues,
                userHandle);
        Binder.restoreCallingIdentity(identity);

        final Bundle result = new Bundle();
        result.putBoolean(KEY_REMOTE_CALLBACK_RESULT, true);
        remoteCallback.sendResult(result);
    }
}
