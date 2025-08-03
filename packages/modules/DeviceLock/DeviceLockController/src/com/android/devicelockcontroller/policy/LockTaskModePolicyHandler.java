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

package com.android.devicelockcontroller.policy;

import static android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS;

import static com.android.devicelockcontroller.policy.StartLockTaskModeWorker.START_LOCK_TASK_MODE_WORK_NAME;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.UserManager;
import android.provider.Settings;
import android.provider.Settings.Secure;
import android.telecom.TelecomManager;
import android.util.ArraySet;

import androidx.work.WorkManager;

import com.android.devicelockcontroller.R;
import com.android.devicelockcontroller.storage.SetupParametersClient;
import com.android.devicelockcontroller.storage.UserParameters;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executor;

/** Handles lock task mode features. */
final class LockTaskModePolicyHandler implements PolicyHandler {
    static final int DEFAULT_LOCK_TASK_FEATURES_FOR_DLC =
            (DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO
                    | DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD
                    | DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS
                    | DevicePolicyManager.LOCK_TASK_FEATURE_BLOCK_ACTIVITY_START_IN_TASK);
    static final int DEFAULT_LOCK_TASK_FEATURES_FOR_KIOSK =
            DEFAULT_LOCK_TASK_FEATURES_FOR_DLC | DevicePolicyManager.LOCK_TASK_FEATURE_HOME;
    private static final String TAG = "LockTaskModePolicyHandler";
    private final Context mContext;
    private final DevicePolicyManager mDpm;
    private final Executor mBgExecutor;
    private final UserManager mUserManager;

    LockTaskModePolicyHandler(Context context, DevicePolicyManager dpm, Executor bgExecutor) {
        mContext = context;
        mDpm = dpm;
        mBgExecutor = bgExecutor;
        mUserManager = Objects.requireNonNull(mContext.getSystemService(UserManager.class));
    }

    @Override
    public ListenableFuture<Boolean> onProvisionInProgress() {
        return enableLockTaskModeSafely(/* forController= */ true);
    }

    @Override
    public ListenableFuture<Boolean> onProvisioned() {
        return enableLockTaskModeSafely(/* forController= */ false);
    }

    @Override
    public ListenableFuture<Boolean> onProvisionPaused() {
        return disableLockTaskMode();
    }

    @Override
    public ListenableFuture<Boolean> onProvisionFailed() {
        return disableLockTaskMode();
    }

    @Override
    public ListenableFuture<Boolean> onLocked() {
        return enableLockTaskModeSafely(/* forController= */ false);
    }

    @Override
    public ListenableFuture<Boolean> onUnlocked() {
        return disableLockTaskMode();
    }

    @Override
    public ListenableFuture<Boolean> onCleared() {
        return disableLockTaskMode();
    }

    /**
     * Updates the allowlist for lock task mode
     *
     * @param includeKiosk true if the kiosk app and the kiosk-specified allowlist should be added
     * @return future for when the lock task mode allowlist has been updated
     */
    private ListenableFuture<Void> updateAllowlist(boolean includeKiosk) {
        return Futures.transform(composeAllowlist(includeKiosk),
                allowlist -> {
                    TelecomManager telecomManager = mContext.getSystemService(
                            TelecomManager.class);
                    String defaultDialer = telecomManager.getDefaultDialerPackage();
                    if (defaultDialer != null && !allowlist.contains(defaultDialer)) {
                        LogUtil.i(TAG,
                                String.format(Locale.US, "Adding default dialer %s to allowlist",
                                        defaultDialer));
                        allowlist.add(defaultDialer);
                    }
                    String[] allowlistPackages = allowlist.toArray(new String[0]);
                    mDpm.setLockTaskPackages(null /* admin */, allowlistPackages);
                    LogUtil.i(TAG, String.format(Locale.US, "Update Lock task allowlist %s",
                            Arrays.toString(allowlistPackages)));
                    return null;
                }, mBgExecutor);
    }

    /**
     * Safely initiate Lock Task Mode
     *
     * @param forController Whether the Device Lock Controller itself (true) or the Kiosk (false)
     *                      is in charge of this instance of Lock Task Mode
     */
    private ListenableFuture<Boolean> enableLockTaskModeSafely(boolean forController) {
        // Disabling lock task mode before enabling it prevents vulnerabilities if another app
        // has already initiated lock task mode
        return Futures.transformAsync(disableLockTaskMode(),
                unused -> {
                    if (forController) {
                        return enableLockTaskModeForController();
                    }
                    return enableLockTaskModeForKiosk();
                }, mBgExecutor);
    }

    private ListenableFuture<Boolean> enableLockTaskModeForController() {
        return Futures.transform(updateAllowlist(/* includeKiosk= */ false),
                unused -> {
                    mDpm.setLockTaskFeatures(/* admin= */ null, DEFAULT_LOCK_TASK_FEATURES_FOR_DLC);
                    return true;
                }, mBgExecutor);
    }

    private ListenableFuture<Boolean> enableLockTaskModeForKiosk() {
        ListenableFuture<Boolean> notificationsInLockTaskModeEnabled =
                SetupParametersClient.getInstance().isNotificationsInLockTaskModeEnabled();
        return Futures.whenAllSucceed(
                        notificationsInLockTaskModeEnabled,
                        updateAllowlist(/* includeKiosk= */ true))
                .call(() -> {
                    int flags = DEFAULT_LOCK_TASK_FEATURES_FOR_KIOSK;
                    if (Futures.getDone(notificationsInLockTaskModeEnabled)) {
                        flags |= LOCK_TASK_FEATURE_NOTIFICATIONS;
                    }
                    mDpm.setLockTaskFeatures(/* admin= */ null, flags);
                    return true;
                }, mBgExecutor);
    }

    private ListenableFuture<Boolean> disableLockTaskMode() {
        return Futures.submit(() -> {
            if (mUserManager.isUserUnlocked()) {
                WorkManager.getInstance(mContext).cancelUniqueWork(START_LOCK_TASK_MODE_WORK_NAME);
            }
            final String currentPackage = UserParameters.getPackageOverridingHome(mContext);
            // Device Policy Engine treats lock task features and packages as one policy and
            // therefore we need to set both lock task features (to LOCK_TASK_FEATURE_NONE) and
            // lock task packages (to an empty string array).
            mDpm.setLockTaskFeatures(null /* admin */, DevicePolicyManager.LOCK_TASK_FEATURE_NONE);
            // This is a hacky workaround to stop the lock task mode by enforcing that no apps
            // can be in lock task mode
            // TODO(b/288886570): Fix this in the framework so we don't have to do this workaround
            mDpm.setLockTaskPackages(null /* admin */, new String[]{""});
            // This will remove the DLC policy and allow other admins to enforce their policy
            mDpm.setLockTaskPackages(null /* admin */, new String[0]);
            if (currentPackage != null) {
                mDpm.clearPackagePersistentPreferredActivities(null /* admin */, currentPackage);
                UserParameters.setPackageOverridingHome(mContext, null /* packageName */);
            }
            return true;
        }, mBgExecutor);
    }

    /*
     * The allowlist for lock task mode is composed by the following rules.
     *   1. Add the packages from lock_task_allowlist array from config.xml. These packages are
     *      required for essential services to work.
     *   2. Find the default app used as dialer (should be a System App).
     *   3. Find the default app used for Settings (should be a System App).
     *   4. Find the default app used for permissions (should be a System App).
     *   5. Find the default InputMethod.
     *   6. Add the DLC app.
     *   7. Append the kiosk and packages allow-listed through setup parameters if applicable.
     */
    private ListenableFuture<ArraySet<String>> composeAllowlist(boolean includeKiosk) {
        return Futures.submit(() -> {
            String[] allowlistArray =
                    mContext.getResources().getStringArray(R.array.lock_task_allowlist);
            ArraySet<String> allowlistPackages = new ArraySet<>(allowlistArray);
            allowlistSystemAppForAction(Intent.ACTION_DIAL, allowlistPackages);
            allowlistSystemAppForAction(Settings.ACTION_SETTINGS, allowlistPackages);
            allowlistSystemAppForAction(PackageManager.ACTION_REQUEST_PERMISSIONS,
                    allowlistPackages);
            allowlistInputMethod(allowlistPackages);
            allowlistCellBroadcastReceiver(allowlistPackages);
            allowlistPackages.add(mContext.getPackageName());
            if (includeKiosk) {
                SetupParametersClient setupParametersClient = SetupParametersClient.getInstance();
                allowlistPackages.add(
                        Futures.getUnchecked(setupParametersClient.getKioskPackage()));
                allowlistPackages.addAll(
                        Futures.getUnchecked(setupParametersClient.getKioskAllowlist()));
            }
            return allowlistPackages;
        }, mBgExecutor);
    }

    private void allowlistSystemAppForAction(String action, ArraySet<String> allowlistPackages) {
        final PackageManager pm = mContext.getPackageManager();
        final Intent intent = new Intent(action);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        final List<ResolveInfo> resolveInfoList =
                pm.queryIntentActivities(intent, PackageManager.MATCH_SYSTEM_ONLY);
        if (resolveInfoList.isEmpty()) {
            LogUtil.e(TAG,
                    String.format(Locale.US, "Could not find the system app for %s", action));

            return;
        }
        final String packageName = resolveInfoList.get(0).activityInfo.packageName;
        LogUtil.i(TAG, String.format(Locale.US, "Using %s for %s", packageName, action));
        allowlistPackages.add(packageName);
    }

    private void allowlistInputMethod(ArraySet<String> allowlistPackages) {
        final String defaultIme = Secure.getString(mContext.getContentResolver(),
                Secure.DEFAULT_INPUT_METHOD);
        if (defaultIme == null) {
            LogUtil.e(TAG, "Could not find the default IME");

            return;
        }

        final ComponentName imeComponent = ComponentName.unflattenFromString(defaultIme);
        if (imeComponent == null) {
            LogUtil.e(TAG, String.format(Locale.US, "Invalid input method: %s", defaultIme));

            return;
        }
        allowlistPackages.add(imeComponent.getPackageName());
    }

    private void allowlistCellBroadcastReceiver(ArraySet<String> allowlistPackages) {
        final String packageName =
                CellBroadcastUtils.getDefaultCellBroadcastReceiverPackageName(mContext);
        if (packageName == null) {
            LogUtil.e(TAG, "Could not find the default cell broadcast receiver");

            return;
        }
        allowlistPackages.add(packageName);
    }
}
