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

import static com.android.devicelockcontroller.activities.ProvisioningActivity.EXTRA_SHOW_CRITICAL_PROVISION_FAILED_UI_ON_START;
import static com.android.devicelockcontroller.common.DeviceLockConstants.ACTION_START_DEVICE_FINANCING_PROVISIONING;
import static com.android.devicelockcontroller.common.DeviceLockConstants.ACTION_START_DEVICE_SUBSIDY_PROVISIONING;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.CLEARED;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.LOCKED;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.UNDEFINED;
import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.UNLOCKED;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.KIOSK_PROVISIONED;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.PROVISION_FAILED;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.PROVISION_IN_PROGRESS;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.PROVISION_PAUSED;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.PROVISION_SUCCEEDED;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.UNPROVISIONED;
import static com.android.devicelockcontroller.policy.StartLockTaskModeWorker.START_LOCK_TASK_MODE_WORKER_RETRY_INTERVAL_SECONDS;
import static com.android.devicelockcontroller.policy.StartLockTaskModeWorker.START_LOCK_TASK_MODE_WORK_NAME;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.sqlite.SQLiteException;
import android.os.Build;
import android.os.UserManager;

import androidx.annotation.GuardedBy;
import androidx.annotation.VisibleForTesting;
import androidx.work.BackoffPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.Operation;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkManager;

import com.android.devicelockcontroller.SystemDeviceLockManager;
import com.android.devicelockcontroller.activities.LandingActivity;
import com.android.devicelockcontroller.activities.ProvisioningActivity;
import com.android.devicelockcontroller.common.DeviceLockConstants;
import com.android.devicelockcontroller.common.DeviceLockConstants.ProvisioningType;
import com.android.devicelockcontroller.policy.DeviceStateController.DeviceState;
import com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState;
import com.android.devicelockcontroller.provision.worker.ReportDeviceProvisionStateWorker;
import com.android.devicelockcontroller.schedule.DeviceLockControllerScheduler;
import com.android.devicelockcontroller.schedule.DeviceLockControllerSchedulerProvider;
import com.android.devicelockcontroller.storage.GlobalParametersClient;
import com.android.devicelockcontroller.storage.SetupParametersClient;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * An implementation of {@link DevicePolicyController}. This class guarantees thread safety by
 * synchronizing policies enforcement on background threads in the order of when the API calls
 * happen. That is, a pre-exist enforcement request will always blocks a incoming enforcement
 * request until the former completes.
 */
public final class DevicePolicyControllerImpl implements DevicePolicyController {
    private static final String TAG = "DevicePolicyControllerImpl";

    private final List<PolicyHandler> mPolicyList = new ArrayList<>();
    private final Context mContext;
    private final DevicePolicyManager mDpm;
    private final ProvisionStateController mProvisionStateController;
    // A future that returns the current lock task type for the current provision/device state
    // after policies enforcement are done.
    @GuardedBy("this")
    private ListenableFuture<@LockTaskType Integer> mCurrentEnforcedLockTaskTypeFuture =
            Futures.immediateFuture(LockTaskType.UNDEFINED);
    private final Executor mBgExecutor;
    static final String ACTION_DEVICE_LOCK_KIOSK_SETUP =
            "com.android.devicelock.action.KIOSK_SETUP";
    private final UserManager mUserManager;

    /**
     * Create a new policy controller.
     *
     * @param context The context used by this policy controller.
     * @param devicePolicyManager  The device policy manager.
     * @param userManager The user manager.
     * @param systemDeviceLockManager The system device lock manager.
     * @param provisionStateController The provision state controller.
     * @param bgExecutor The background executor.
     */
    public DevicePolicyControllerImpl(Context context,
            DevicePolicyManager devicePolicyManager,
            UserManager userManager,
            SystemDeviceLockManager systemDeviceLockManager,
            ProvisionStateController provisionStateController,
            Executor bgExecutor) {
        this(context,
                devicePolicyManager,
                userManager,
                new UserRestrictionsPolicyHandler(devicePolicyManager, userManager,
                        Build.isDebuggable(),
                        bgExecutor),
                new AppOpsPolicyHandler(systemDeviceLockManager, bgExecutor),
                new LockTaskModePolicyHandler(context, devicePolicyManager, bgExecutor),
                new PackagePolicyHandler(context, devicePolicyManager, bgExecutor),
                new RolePolicyHandler(systemDeviceLockManager, bgExecutor),
                new KioskKeepAlivePolicyHandler(systemDeviceLockManager, bgExecutor),
                new ControllerKeepAlivePolicyHandler(systemDeviceLockManager, bgExecutor),
                new NotificationsPolicyHandler(systemDeviceLockManager, bgExecutor),
                provisionStateController,
                bgExecutor);
    }

    @VisibleForTesting
    DevicePolicyControllerImpl(Context context,
            DevicePolicyManager devicePolicyManager,
            UserManager userManager,
            UserRestrictionsPolicyHandler userRestrictionsPolicyHandler,
            AppOpsPolicyHandler appOpsPolicyHandler,
            LockTaskModePolicyHandler lockTaskModePolicyHandler,
            PackagePolicyHandler packagePolicyHandler,
            RolePolicyHandler rolePolicyHandler,
            KioskKeepAlivePolicyHandler kioskKeepAlivePolicyHandler,
            ControllerKeepAlivePolicyHandler controllerKeepAlivePolicyHandler,
            NotificationsPolicyHandler notificationsPolicyHandler,
            ProvisionStateController provisionStateController,
            Executor bgExecutor) {
        mContext = context;
        mProvisionStateController = provisionStateController;
        mBgExecutor = bgExecutor;
        mDpm = devicePolicyManager;
        mUserManager = userManager;
        mPolicyList.add(userRestrictionsPolicyHandler);
        mPolicyList.add(appOpsPolicyHandler);
        mPolicyList.add(lockTaskModePolicyHandler);
        mPolicyList.add(packagePolicyHandler);
        mPolicyList.add(rolePolicyHandler);
        mPolicyList.add(kioskKeepAlivePolicyHandler);
        mPolicyList.add(controllerKeepAlivePolicyHandler);
        mPolicyList.add(notificationsPolicyHandler);
    }

    @Override
    public boolean wipeDevice() {
        LogUtil.i(TAG, "Wiping device");
        try {
            mDpm.wipeDevice(DevicePolicyManager.WIPE_SILENTLY
                    | DevicePolicyManager.WIPE_RESET_PROTECTION_DATA);
        } catch (SecurityException e) {
            LogUtil.e(TAG, "Cannot wipe device", e);
            return false;
        }
        return true;
    }

    @Override
    public ListenableFuture<Void> enforceCurrentPolicies() {
        return Futures.transform(enforceCurrentPoliciesAndResolveLockTaskType(
                        /* failure= */ false),
                mode -> {
                    startLockTaskModeIfNeeded(mode);
                    return null;
                },
                mBgExecutor);
    }

    @Override
    public ListenableFuture<Void> enforceCurrentPoliciesForCriticalFailure() {
        return Futures.transform(enforceCurrentPoliciesAndResolveLockTaskType(
                        /* failure= */ true),
                mode -> {
                    startLockTaskModeIfNeeded(mode);
                    handlePolicyEnforcementFailure();
                    return null;
                },
                mBgExecutor);
    }

    private void handlePolicyEnforcementFailure() {
        final DeviceLockControllerSchedulerProvider schedulerProvider =
                (DeviceLockControllerSchedulerProvider) mContext.getApplicationContext();
        final DeviceLockControllerScheduler scheduler =
                schedulerProvider.getDeviceLockControllerScheduler();
        // Hard failure due to policy enforcement, treat it as mandatory reset device alarm.
        scheduler.scheduleMandatoryResetDeviceAlarm();

        ReportDeviceProvisionStateWorker.reportSetupFailed(WorkManager.getInstance(mContext),
                DeviceLockConstants.ProvisionFailureReason.POLICY_ENFORCEMENT_FAILED);
    }

    /**
     * Enforce current policies and then return the resulting lock task type
     *
     * @param failure true if this enforcement is due to resetting policies in case of failure.
     * @return A future for the lock task type corresponding to the current policies.
     */
    private ListenableFuture<@LockTaskType Integer> enforceCurrentPoliciesAndResolveLockTaskType(
            boolean failure) {
        synchronized (this) {
            // current lock task type must be assigned to a local variable; otherwise, if
            // retrieved down the execution flow, it will be returning the new type after execution.
            ListenableFuture<@LockTaskType Integer> currentLockTaskType =
                    mCurrentEnforcedLockTaskTypeFuture;
            ListenableFuture<@LockTaskType Integer> policiesEnforcementFuture =
                    Futures.transformAsync(
                            currentLockTaskType,
                            unused -> {
                                final ListenableFuture<@ProvisionState Integer> provisionState =
                                        mProvisionStateController.getState();
                                final ListenableFuture<@DeviceState Integer> deviceState =
                                        GlobalParametersClient.getInstance().getDeviceState();
                                return Futures.whenAllSucceed(provisionState, deviceState)
                                        .callAsync(
                                                () -> enforcePoliciesForCurrentStates(
                                                        Futures.getDone(provisionState),
                                                        Futures.getDone(deviceState)),
                                                mBgExecutor
                                );
                            },
                            mBgExecutor);
            if (failure) {
                mCurrentEnforcedLockTaskTypeFuture = Futures.immediateFuture(
                        LockTaskType.CRITICAL_ERROR);
                return mCurrentEnforcedLockTaskTypeFuture;
            } else {
                // To prevent exception propagate to future policies enforcement, catch any
                // exceptions that might happen during the execution and fallback to previous type
                // if exception happens.
                mCurrentEnforcedLockTaskTypeFuture = Futures.catchingAsync(
                        policiesEnforcementFuture,
                        Exception.class, unused -> currentLockTaskType,
                        MoreExecutors.directExecutor());
            }
            return policiesEnforcementFuture;
        }
    }

    private ListenableFuture<@LockTaskType Integer> enforcePoliciesForCurrentStates(
            @ProvisionState int provisionState, @DeviceState int deviceState) {
        LogUtil.i(TAG, "Enforcing policies for provision state " + provisionState
                + " and device state " + deviceState);
        if (provisionState == UNPROVISIONED) {
            return Futures.immediateFuture(resolveLockTaskType(provisionState, deviceState));
        }
        List<ListenableFuture<Boolean>> futures = new ArrayList<>();
        if (deviceState == CLEARED) {
            // If device is cleared, then ignore provision state and add cleared policies
            for (int i = 0, policyLen = mPolicyList.size(); i < policyLen; i++) {
                PolicyHandler policy = mPolicyList.get(i);
                futures.add(policy.onCleared());
            }
        } else if (provisionState == PROVISION_SUCCEEDED) {
            // If provisioning has succeeded, then ignore provision state and add device state
            // policies
            for (int i = 0, policyLen = mPolicyList.size(); i < policyLen; i++) {
                PolicyHandler policy = mPolicyList.get(i);
                switch (deviceState) {
                    case UNLOCKED:
                        futures.add(policy.onUnlocked());
                        break;
                    case LOCKED:
                        futures.add(policy.onLocked());
                        break;
                    case UNDEFINED:
                        // No policies to enforce in this state.
                        break;
                    default:
                        throw new IllegalArgumentException(
                                "Invalid device state to enforce: " + deviceState);
                }
            }
        } else {
            for (int i = 0, policyLen = mPolicyList.size(); i < policyLen; i++) {
                PolicyHandler policy = mPolicyList.get(i);
                switch (provisionState) {
                    case PROVISION_IN_PROGRESS:
                        futures.add(policy.onProvisionInProgress());
                        break;
                    case KIOSK_PROVISIONED:
                        futures.add(policy.onProvisioned());
                        break;
                    case PROVISION_PAUSED:
                        futures.add(policy.onProvisionPaused());
                        break;
                    case PROVISION_FAILED:
                        futures.add(policy.onProvisionFailed());
                        break;
                    default:
                        throw new IllegalArgumentException(
                                "Invalid provision state to enforce: " + provisionState);
                }
            }
        }
        return Futures.transform(Futures.allAsList(futures),
                results -> {
                    if (results.stream().reduce(true, (a, r) -> a && r)) {
                        return resolveLockTaskType(provisionState, deviceState);
                    } else {
                        throw new IllegalStateException(
                                "Failed to enforce policies for provision state " + provisionState
                                        + " and device state " + deviceState);
                    }
                },
                MoreExecutors.directExecutor());
    }

    /**
     * Determines the lock task type based on the current provision and device state
     */
    private @LockTaskType int resolveLockTaskType(int provisionState, int deviceState) {
        if (provisionState == UNPROVISIONED || deviceState == CLEARED) {
            return LockTaskType.NOT_IN_LOCK_TASK;
        }
        if (provisionState == PROVISION_IN_PROGRESS) {
            return LockTaskType.LANDING_ACTIVITY;
        }
        if (provisionState == KIOSK_PROVISIONED) {
            return LockTaskType.KIOSK_SETUP_ACTIVITY;
        }
        if (provisionState == PROVISION_SUCCEEDED && deviceState == LOCKED) {
            return LockTaskType.KIOSK_LOCK_ACTIVITY;
        }
        return LockTaskType.NOT_IN_LOCK_TASK;
    }

    private ListenableFuture<Intent> getLockScreenActivityIntent() {
        return Futures.transform(
                SetupParametersClient.getInstance().getKioskPackage(),
                kioskPackage -> {
                    if (kioskPackage == null) {
                        throw new IllegalStateException("Missing kiosk package parameter!");
                    }
                    Intent homeIntent = new Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_HOME)
                            .setPackage(kioskPackage);
                    PackageManager pm = mContext.getPackageManager();
                    ResolveInfo resolvedInfo = pm.resolveActivity(homeIntent,
                            PackageManager.MATCH_DEFAULT_ONLY);
                    if (resolvedInfo != null && resolvedInfo.activityInfo != null) {
                        return homeIntent.setComponent(
                                new ComponentName(kioskPackage,
                                        resolvedInfo.activityInfo.name));
                    }
                    // Kiosk app does not have an activity to handle the default
                    // home intent. Fall back to the launch activity.
                    // Note that in this case, Kiosk App can't be effectively set as
                    // the default home activity.
                    Intent launchIntent = pm.getLaunchIntentForPackage(kioskPackage);
                    if (launchIntent == null) {
                        throw new IllegalStateException(
                                "Failed to get launch intent for kiosk app!");
                    }
                    return launchIntent;
                }, mBgExecutor);
    }

    private ListenableFuture<Intent> getLandingActivityIntent() {
        SetupParametersClient client = SetupParametersClient.getInstance();
        ListenableFuture<@ProvisioningType Integer> provisioningType =
                client.getProvisioningType();
        return Futures.transform(provisioningType,
                type -> {
                    Intent resultIntent = new Intent(mContext, LandingActivity.class);
                    switch (type) {
                        case ProvisioningType.TYPE_FINANCED:
                            // TODO(b/288923554) this used to return an intent with action
                            // ACTION_START_DEVICE_FINANCING_SECONDARY_USER_PROVISIONING
                            // for secondary users. Rework once a decision has been made about
                            // what to show to users.
                            return resultIntent.setAction(
                                    ACTION_START_DEVICE_FINANCING_PROVISIONING);
                        case ProvisioningType.TYPE_SUBSIDY:
                            return resultIntent.setAction(ACTION_START_DEVICE_SUBSIDY_PROVISIONING);
                        case ProvisioningType.TYPE_UNDEFINED:
                        default:
                            throw new IllegalArgumentException("Provisioning type is unknown!");
                    }
                }, mBgExecutor);
    }

    private ListenableFuture<Intent> getKioskSetupActivityIntent() {
        return Futures.transform(SetupParametersClient.getInstance().getKioskPackage(),
                kioskPackageName -> {
                    if (kioskPackageName == null) {
                        throw new IllegalStateException("Missing kiosk package parameter!");
                    }
                    final Intent kioskSetupIntent = new Intent(ACTION_DEVICE_LOCK_KIOSK_SETUP);
                    kioskSetupIntent.setPackage(kioskPackageName);
                    final ResolveInfo resolveInfo = mContext.getPackageManager()
                            .resolveActivity(kioskSetupIntent, PackageManager.MATCH_DEFAULT_ONLY);
                    if (resolveInfo == null || resolveInfo.activityInfo == null) {
                        throw new IllegalStateException(
                                "Failed to get setup activity intent for kiosk app!");
                    }
                    return kioskSetupIntent.setComponent(new ComponentName(kioskPackageName,
                            resolveInfo.activityInfo.name));
                }, mBgExecutor);
    }

    private ListenableFuture<Intent> getProvisioningActivityIntentForCriticalFailure() {
        final Intent intent = new Intent(mContext, ProvisioningActivity.class)
                .putExtra(EXTRA_SHOW_CRITICAL_PROVISION_FAILED_UI_ON_START, true);
        return Futures.immediateFuture(intent);
    }


    @Override
    public ListenableFuture<Intent> getLaunchIntentForCurrentState() {
        return Futures.transformAsync(getCurrentEnforcedLockTaskType(),
                type -> {
                    switch (type) {
                        case LockTaskType.NOT_IN_LOCK_TASK:
                            return Futures.immediateFuture(null);
                        case LockTaskType.LANDING_ACTIVITY:
                            return getLandingActivityIntent();
                        case LockTaskType.CRITICAL_ERROR:
                            return getProvisioningActivityIntentForCriticalFailure();
                        case LockTaskType.KIOSK_SETUP_ACTIVITY:
                            return getKioskSetupActivityIntent();
                        case LockTaskType.KIOSK_LOCK_ACTIVITY:
                            return getLockScreenActivityIntent();
                        default:
                            throw new IllegalArgumentException("Invalid lock task type!");
                    }
                }, mBgExecutor);
    }

    /**
     * Gets the currently enforced lock task type, enforcing current policies if they haven't been
     * enforced yet.
     */
    private ListenableFuture<@LockTaskType Integer> getCurrentEnforcedLockTaskType() {
        synchronized (this) {
            return Futures.transformAsync(
                    mCurrentEnforcedLockTaskTypeFuture,
                    type -> type == LockTaskType.UNDEFINED
                            ? enforceCurrentPoliciesAndResolveLockTaskType(/* failure= */ false)
                            : Futures.immediateFuture(type),
                    mBgExecutor);
        }
    }

    @Override
    public ListenableFuture<Void> onUserUnlocked() {
        return Futures.transformAsync(mProvisionStateController.onUserUnlocked(),
                unused -> Futures.transform(getCurrentEnforcedLockTaskType(),
                        mode -> {
                            startLockTaskModeIfNeeded(mode);
                            return null;
                        },
                        mBgExecutor),
                mBgExecutor);
    }

    @Override
    public ListenableFuture<Void> onUserSetupCompleted() {
        return mProvisionStateController.onUserSetupCompleted();
    }

    @Override
    public ListenableFuture<Void> onAppCrashed(boolean isKiosk) {
        final String crashedApp = isKiosk ? "kiosk" : "dlc";
        LogUtil.i(TAG, "Controller notified about " + crashedApp
                + " having crashed while in lock task mode");
        return Futures.transform(getCurrentEnforcedLockTaskType(),
                mode -> {
                    startLockTaskModeIfNeeded(mode);
                    return null;
                },
                mBgExecutor);
    }

    private void startLockTaskModeIfNeeded(@LockTaskType Integer type) {
        if (type == LockTaskType.NOT_IN_LOCK_TASK || !mUserManager.isUserUnlocked()) {
            return;
        }
        WorkManager workManager = WorkManager.getInstance(mContext);
        OneTimeWorkRequest startLockTask = new OneTimeWorkRequest.Builder(
                StartLockTaskModeWorker.class)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(BackoffPolicy.LINEAR,
                        START_LOCK_TASK_MODE_WORKER_RETRY_INTERVAL_SECONDS)
                .build();
        final ListenableFuture<Operation.State.SUCCESS> enqueueResult =
                workManager.enqueueUniqueWork(START_LOCK_TASK_MODE_WORK_NAME,
                        ExistingWorkPolicy.REPLACE, startLockTask).getResult();
        Futures.addCallback(enqueueResult, new FutureCallback<>() {
            @Override
            public void onSuccess(Operation.State.SUCCESS result) {
                // Enqueued
            }

            @Override
            public void onFailure(Throwable t) {
                LogUtil.e(TAG, "Failed to enqueue 'start lock task mode' work", t);
                if (t instanceof SQLiteException) {
                    wipeDevice();
                } else {
                    LogUtil.e(TAG, "Not wiping device (non SQL exception)");
                }
            }
        }, mBgExecutor);
    }
}
