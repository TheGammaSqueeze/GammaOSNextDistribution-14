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

package com.android.devicelockcontroller;

import android.app.Application;
import android.content.Context;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Configuration;
import androidx.work.DelegatingWorkerFactory;
import androidx.work.ListenableWorker;

import com.android.devicelockcontroller.policy.DevicePolicyController;
import com.android.devicelockcontroller.policy.DeviceStateController;
import com.android.devicelockcontroller.policy.FinalizationController;
import com.android.devicelockcontroller.policy.FinalizationControllerImpl;
import com.android.devicelockcontroller.policy.PolicyObjectsProvider;
import com.android.devicelockcontroller.policy.ProvisionStateController;
import com.android.devicelockcontroller.policy.ProvisionStateControllerImpl;
import com.android.devicelockcontroller.schedule.DeviceLockControllerScheduler;
import com.android.devicelockcontroller.schedule.DeviceLockControllerSchedulerImpl;
import com.android.devicelockcontroller.schedule.DeviceLockControllerSchedulerProvider;
import com.android.devicelockcontroller.stats.StatsLogger;
import com.android.devicelockcontroller.stats.StatsLoggerImpl;
import com.android.devicelockcontroller.stats.StatsLoggerProvider;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Application class for Device Lock Controller.
 */
public class DeviceLockControllerApplication extends Application implements
        PolicyObjectsProvider,
        Configuration.Provider,
        DeviceLockControllerSchedulerProvider,
        FcmRegistrationTokenProvider,
        PlayInstallPackageTaskClassProvider,
        StatsLoggerProvider {
    private static final String TAG = "DeviceLockControllerApplication";

    private static Context sApplicationContext;
    @GuardedBy("this")
    private ProvisionStateController mProvisionStateController;
    @GuardedBy("this")
    private FinalizationController mFinalizationController;
    @GuardedBy("this")
    private DeviceLockControllerScheduler mDeviceLockControllerScheduler;
    @GuardedBy("this")
    private StatsLogger mStatsLogger;

    private WorkManagerExceptionHandler mWorkManagerExceptionHandler;

    public DeviceLockControllerApplication() {
        super();

        mWorkManagerExceptionHandler = WorkManagerExceptionHandler.getInstance(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sApplicationContext = getApplicationContext();
        LogUtil.i(TAG, "onCreate");
    }

    @Override
    public DeviceStateController getDeviceStateController() {
        return getProvisionStateController().getDeviceStateController();
    }

    @Override
    public synchronized ProvisionStateController getProvisionStateController() {
        if (mProvisionStateController == null) {
            mProvisionStateController = new ProvisionStateControllerImpl(this);
        }
        return mProvisionStateController;
    }

    @Override
    public DevicePolicyController getPolicyController() {
        return getProvisionStateController().getDevicePolicyController();
    }

    @Override
    public synchronized FinalizationController getFinalizationController() {
        if (mFinalizationController == null) {
            mFinalizationController = new FinalizationControllerImpl(this);
        }
        return mFinalizationController;
    }

    @Override
    public synchronized StatsLogger getStatsLogger() {
        if (null == mStatsLogger) {
            mStatsLogger = new StatsLoggerImpl();
        }
        return mStatsLogger;
    }

    @Override
    public synchronized void destroyObjects() {
        mProvisionStateController = null;
        mFinalizationController = null;
    }

    public static Context getAppContext() {
        return sApplicationContext;
    }

    //b/267355744: Required to initialize WorkManager on-demand.
    @Override
    public Configuration getWorkManagerConfiguration() {
        final DelegatingWorkerFactory factory = new DelegatingWorkerFactory();
        factory.addFactory(new DeviceLockControllerWorkerFactory());
        return new Configuration.Builder()
                .setWorkerFactory(factory)
                .setMinimumLoggingLevel(android.util.Log.INFO)
                .setInitializationExceptionHandler(
                        (t) -> mWorkManagerExceptionHandler
                                .initializationExceptionHandler(this, t))
                .setTaskExecutor(mWorkManagerExceptionHandler.getWorkManagerTaskExecutor())
                .build();
    }

    @Override
    @Nullable
    public Class<? extends ListenableWorker> getPlayInstallPackageTaskClass() {
        return null;
    }

    @Override
    @NonNull
    public ListenableFuture<String> getFcmRegistrationToken() {
        return Futures.immediateFuture(null);
    }

    @Override
    public synchronized DeviceLockControllerScheduler getDeviceLockControllerScheduler() {
        if (mDeviceLockControllerScheduler == null) {
            mDeviceLockControllerScheduler = new DeviceLockControllerSchedulerImpl(this,
                    getProvisionStateController());
        }
        return mDeviceLockControllerScheduler;
    }
}
