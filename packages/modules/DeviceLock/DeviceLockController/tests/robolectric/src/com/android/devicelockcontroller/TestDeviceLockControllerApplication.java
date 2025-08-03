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

package com.android.devicelockcontroller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;

import com.android.devicelockcontroller.policy.DevicePolicyController;
import com.android.devicelockcontroller.policy.DeviceStateController;
import com.android.devicelockcontroller.policy.FinalizationController;
import com.android.devicelockcontroller.policy.PolicyObjectsProvider;
import com.android.devicelockcontroller.policy.ProvisionStateController;
import com.android.devicelockcontroller.schedule.DeviceLockControllerScheduler;
import com.android.devicelockcontroller.schedule.DeviceLockControllerSchedulerProvider;
import com.android.devicelockcontroller.stats.StatsLogger;
import com.android.devicelockcontroller.stats.StatsLoggerProvider;
import com.android.devicelockcontroller.storage.GlobalParametersClient;
import com.android.devicelockcontroller.storage.GlobalParametersService;
import com.android.devicelockcontroller.storage.SetupParametersClient;
import com.android.devicelockcontroller.storage.SetupParametersService;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.testing.TestingExecutors;

import org.robolectric.Robolectric;
import org.robolectric.TestLifecycleApplication;

import java.lang.reflect.Method;

/**
 * Application class that provides mock objects for tests.
 */
public final class TestDeviceLockControllerApplication extends Application implements
        PolicyObjectsProvider,
        TestLifecycleApplication,
        DeviceLockControllerSchedulerProvider,
        FcmRegistrationTokenProvider,
        PlayInstallPackageTaskClassProvider,
        StatsLoggerProvider {

    private DevicePolicyController mPolicyController;
    private DeviceStateController mStateController;
    private ProvisionStateController mProvisionStateController;
    private FinalizationController mFinalizationController;
    private DeviceLockControllerScheduler mDeviceLockControllerScheduler;
    private SetupParametersClient mSetupParametersClient;
    private GlobalParametersClient mGlobalParametersClient;
    private StatsLogger mStatsLogger;

    @Override
    public DeviceStateController getDeviceStateController() {
        if (mStateController == null) {
            mStateController = mock(DeviceStateController.class);
        }
        return mStateController;
    }

    @Override
    public ProvisionStateController getProvisionStateController() {
        if (mProvisionStateController == null) {
            mProvisionStateController = mock(ProvisionStateController.class);
            when(mProvisionStateController.getDevicePolicyController()).thenReturn(
                    getPolicyController());
            when(mProvisionStateController.getDeviceStateController()).thenReturn(
                    getDeviceStateController());
        }
        return mProvisionStateController;
    }

    @Override
    public DevicePolicyController getPolicyController() {
        if (mPolicyController == null) {
            mPolicyController = mock(DevicePolicyController.class);
        }
        return mPolicyController;
    }

    @Override
    public FinalizationController getFinalizationController() {
        if (mFinalizationController == null) {
            mFinalizationController = mock(FinalizationController.class);
        }
        return mFinalizationController;
    }

    @Override
    public synchronized StatsLogger getStatsLogger() {
        if (null == mStatsLogger) {
            mStatsLogger = mock(StatsLogger.class);
        }
        return mStatsLogger;
    }

    @Override
    @NonNull
    public ListenableFuture<String> getFcmRegistrationToken() {
        return Futures.immediateFuture(null);
    }

    @Override
    public void destroyObjects() {
        mPolicyController = null;
        mStateController = null;
    }


    @Override
    public void beforeTest(Method method) {
        mSetupParametersClient = SetupParametersClient.getInstance(this,
                TestingExecutors.sameThreadScheduledExecutor());
        mSetupParametersClient.setService(
                Robolectric.setupService(SetupParametersService.class).onBind(/* intent= */ null));

        mGlobalParametersClient = GlobalParametersClient.getInstance(
                this, TestingExecutors.sameThreadScheduledExecutor());
        mGlobalParametersClient.setService(
                Robolectric.setupService(GlobalParametersService.class).onBind(/* intent= */ null));
    }

    @Override
    public void prepareTest(Object test) {
    }

    @Override
    public void afterTest(Method method) {
        GlobalParametersClient.reset();
        SetupParametersClient.reset();
    }

    @Override
    public DeviceLockControllerScheduler getDeviceLockControllerScheduler() {
        if (mDeviceLockControllerScheduler == null) {
            mDeviceLockControllerScheduler = mock(DeviceLockControllerScheduler.class);
        }
        return mDeviceLockControllerScheduler;
    }

    @Nullable
    @Override
    public Class<? extends ListenableWorker> getPlayInstallPackageTaskClass() {
        return PlayInstallPackageWorker.class;
    }

    /**
     * A stub class for play install worker.
     */
    public static final class PlayInstallPackageWorker extends ListenableWorker {

        public PlayInstallPackageWorker(@NonNull Context appContext,
                @NonNull WorkerParameters workerParameters) {
            super(appContext, workerParameters);
        }

        @NonNull
        @Override
        public ListenableFuture<Result> startWork() {
            return Futures.immediateFuture(Result.success());
        }
    }
}
