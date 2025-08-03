/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.android.devicelockcontroller.policy.DevicePolicyControllerImpl.ACTION_DEVICE_LOCK_KIOSK_SETUP;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;

import com.android.devicelockcontroller.stats.StatsLoggerProvider;
import com.android.devicelockcontroller.storage.UserParameters;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.time.Duration;
import java.util.Objects;

/**
 * A worker class dedicated to start lock task mode when device is locked.
 */
public final class StartLockTaskModeWorker extends ListenableWorker {

    private static final String TAG = "StartLockTaskModeWorker";
    static final String START_LOCK_TASK_MODE_WORK_NAME = "start-lock-task-mode";
    private final Context mContext;
    private final ListeningExecutorService mExecutorService;

    static final Duration START_LOCK_TASK_MODE_WORKER_RETRY_INTERVAL_SECONDS =
            Duration.ofSeconds(30);
    private final ActivityManager mAm;
    private final DevicePolicyManager mDpm;

    public StartLockTaskModeWorker(
            @NonNull Context context,
            @NonNull WorkerParameters workerParams,
            ListeningExecutorService executorService) {
        this(context,
                context.getSystemService(DevicePolicyManager.class),
                context.getSystemService(ActivityManager.class),
                workerParams,
                executorService);
    }

    @VisibleForTesting
    StartLockTaskModeWorker(
            @NonNull Context context,
            @NonNull DevicePolicyManager dpm,
            @NonNull ActivityManager am,
            @NonNull WorkerParameters workerParams,
            ListeningExecutorService executorService) {
        super(context, workerParams);
        mContext = context;
        mExecutorService = executorService;
        mDpm = Objects.requireNonNull(dpm);
        mAm = Objects.requireNonNull(am);
    }

    @NonNull
    @Override
    public ListenableFuture<Result> startWork() {
        DevicePolicyController devicePolicyController =
                ((PolicyObjectsProvider) mContext.getApplicationContext())
                        .getProvisionStateController().getDevicePolicyController();
        ListenableFuture<Boolean> isInLockTaskModeFuture =
                Futures.submit(
                        () -> mAm.getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_LOCKED,
                        mExecutorService);
        final ListenableFuture<Result> lockTaskFuture =
                Futures.transformAsync(isInLockTaskModeFuture, isInLockTaskMode -> {
            if (isInLockTaskMode) {
                LogUtil.i(TAG, "Lock task mode is active now");
                return Futures.immediateFuture(Result.success());
            }

            return Futures.transform(
                    devicePolicyController.getLaunchIntentForCurrentState(),
                    launchIntent -> {
                        if (launchIntent == null) {
                            LogUtil.e(TAG, "Failed to enter lock task mode: no intent to launch");
                            return Result.failure();
                        }
                        ComponentName launchIntentComponent = launchIntent.getComponent();
                        String packageName = launchIntentComponent.getPackageName();
                        if (!Objects.requireNonNull(mDpm).isLockTaskPermitted(packageName)) {
                            LogUtil.e(TAG, packageName + " is not permitted in lock task mode");
                            return Result.failure();
                        }
                        setPreferredActivityForHome(launchIntentComponent);
                        launchIntent.addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        LogUtil.i(TAG, "Launching activity for intent: " + launchIntent);
                        mContext.startActivity(launchIntent,
                                ActivityOptions.makeBasic().setLockTaskEnabled(true).toBundle());
                        // If the intent of the activity to be launched is the Kiosk app, we treat
                        // this as the end of the provisioning time.
                        if (launchIntent.getAction() == ACTION_DEVICE_LOCK_KIOSK_SETUP) {
                            long provisioningStartTime = UserParameters
                                    .getProvisioningStartTimeMillis(mContext);
                            if (provisioningStartTime > 0) {
                                ((StatsLoggerProvider) mContext.getApplicationContext())
                                        .getStatsLogger()
                                        .logProvisioningComplete(SystemClock.elapsedRealtime()
                                                - provisioningStartTime);
                            }
                        }
                        if (mAm.getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_LOCKED) {
                            LogUtil.i(TAG, "Successfully entered lock task mode");
                            return Result.success();
                        } else {
                            LogUtil.i(TAG, "Retry entering lock task mode");
                            return Result.retry();
                        }
                    }, mExecutorService);
        }, mExecutorService);
        return Futures.catchingAsync(lockTaskFuture, Exception.class,
                ex -> Futures.transform(devicePolicyController
                        .enforceCurrentPoliciesForCriticalFailure(),
                        unused -> {
                            LogUtil.e(TAG, "Failed to lock task: ", ex);
                            return Result.failure();
                        }, mExecutorService),
                mExecutorService);
    }

    private void setPreferredActivityForHome(ComponentName activity) {

        final String currentPackage = UserParameters.getPackageOverridingHome(mContext);
        if (currentPackage != null && !currentPackage.equals(activity.getPackageName())) {
            mDpm.clearPackagePersistentPreferredActivities(null /* admin */, currentPackage);
        } else {
            mDpm.addPersistentPreferredActivity(null /* admin */, getHomeIntentFilter(), activity);
            UserParameters.setPackageOverridingHome(mContext, activity.getPackageName());
        }
    }

    private static IntentFilter getHomeIntentFilter() {
        final IntentFilter filter = new IntentFilter(Intent.ACTION_MAIN);
        filter.addCategory(Intent.CATEGORY_HOME);
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        return filter;
    }
}
