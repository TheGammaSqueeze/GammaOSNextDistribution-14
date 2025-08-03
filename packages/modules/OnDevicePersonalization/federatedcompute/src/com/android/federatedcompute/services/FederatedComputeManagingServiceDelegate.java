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

package com.android.federatedcompute.services;

import static android.federatedcompute.common.ClientConstants.STATUS_INTERNAL_ERROR;
import static android.federatedcompute.common.ClientConstants.STATUS_SUCCESS;

import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_API_CALLED__API_NAME__CANCEL;
import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_API_CALLED__API_NAME__SCHEDULE;

import android.annotation.NonNull;
import android.content.ComponentName;
import android.content.Context;
import android.federatedcompute.aidl.IFederatedComputeCallback;
import android.federatedcompute.aidl.IFederatedComputeService;
import android.federatedcompute.common.TrainingOptions;
import android.os.Binder;
import android.os.RemoteException;

import com.android.federatedcompute.internal.util.LogUtil;
import com.android.federatedcompute.services.common.Clock;
import com.android.federatedcompute.services.common.FederatedComputeExecutors;
import com.android.federatedcompute.services.common.FlagsFactory;
import com.android.federatedcompute.services.common.MonotonicClock;
import com.android.federatedcompute.services.scheduling.FederatedComputeJobManager;
import com.android.federatedcompute.services.statsd.ApiCallStats;
import com.android.federatedcompute.services.statsd.FederatedComputeStatsdLogger;

import com.google.common.annotations.VisibleForTesting;

import java.util.Objects;

/** Implementation of {@link IFederatedComputeService}. */
public class FederatedComputeManagingServiceDelegate extends IFederatedComputeService.Stub {
    private static final String TAG = "FcpServiceDelegate";
    @NonNull private final Context mContext;
    private final FederatedComputeStatsdLogger mFcStatsdLogger;
    private final Clock mClock;

    @VisibleForTesting
    static class Injector {
        FederatedComputeJobManager getJobManager(Context context) {
            return FederatedComputeJobManager.getInstance(context);
        }
    }

    @NonNull private final Injector mInjector;

    public FederatedComputeManagingServiceDelegate(
            @NonNull Context context, FederatedComputeStatsdLogger federatedComputeStatsdLogger) {
        this(context, new Injector(), federatedComputeStatsdLogger, MonotonicClock.getInstance());
    }

    @VisibleForTesting
    public FederatedComputeManagingServiceDelegate(
            @NonNull Context context,
            @NonNull Injector injector,
            FederatedComputeStatsdLogger federatedComputeStatsdLogger,
            Clock clock) {
        mContext = Objects.requireNonNull(context);
        mInjector = Objects.requireNonNull(injector);
        mClock = clock;
        this.mFcStatsdLogger = federatedComputeStatsdLogger;
    }

    @Override
    public void schedule(
            String callingPackageName,
            TrainingOptions trainingOptions,
            IFederatedComputeCallback callback) {
        // Use FederatedCompute instead of caller permission to read experiment flags. It requires
        // READ_DEVICE_CONFIG permission.
        try {
            long origId = Binder.clearCallingIdentity();
            if (FlagsFactory.getFlags().getGlobalKillSwitch()) {
                throw new IllegalStateException(
                        "FederatedComputeService skipped as the global kill switch is on.");
            }
            Binder.restoreCallingIdentity(origId);

            Objects.requireNonNull(callingPackageName);
            Objects.requireNonNull(callback);

            final long startServiceTime = mClock.elapsedRealtime();
            FederatedComputeJobManager jobManager = mInjector.getJobManager(mContext);
            FederatedComputeExecutors.getBackgroundExecutor()
                    .execute(
                            () -> {
                                int resultCode = STATUS_SUCCESS;
                                try {
                                    resultCode =
                                            jobManager.onTrainerStartCalled(
                                                    callingPackageName, trainingOptions);
                                } catch (Exception e) {
                                    resultCode = STATUS_INTERNAL_ERROR;
                                    LogUtil.e(TAG, e, "Got exception for schedule()");
                                } finally {
                                    sendResult(callback, resultCode);
                                    int serviceLatency =
                                            (int) (mClock.elapsedRealtime() - startServiceTime);
                                    mFcStatsdLogger.logApiCallStats(
                                            new ApiCallStats.Builder()
                                                    .setApiName(
                                                FEDERATED_COMPUTE_API_CALLED__API_NAME__SCHEDULE)
                                                    .setLatencyMillis(serviceLatency)
                                                    .setResponseCode(resultCode)
                                                    .build());
                                }
                            });
        } catch (NullPointerException | IllegalArgumentException | IllegalStateException ex) {
            LogUtil.e(TAG, ex, "Got exception for schedule()");
            throw ex;
        } catch (Exception e) {
            LogUtil.e(TAG, e, "Got exception for schedule()");
            sendResult(callback, STATUS_INTERNAL_ERROR);
        }
    }

    @Override
    public void cancel(
            ComponentName ownerComponent,
            String populationName,
            IFederatedComputeCallback callback) {
        // Use FederatedCompute instead of caller permission to read experiment flags. It requires
        // READ_DEVICE_CONFIG permission.
        try {
            long origId = Binder.clearCallingIdentity();
            if (FlagsFactory.getFlags().getGlobalKillSwitch()) {
                throw new IllegalStateException("Service skipped as the global kill switch is on.");
            }
            Binder.restoreCallingIdentity(origId);

            Objects.requireNonNull(ownerComponent);
            Objects.requireNonNull(callback);
            Objects.requireNonNull(populationName);

            final long startServiceTime = mClock.elapsedRealtime();
            FederatedComputeJobManager jobManager = mInjector.getJobManager(mContext);
            FederatedComputeExecutors.getBackgroundExecutor()
                    .execute(
                            () -> {
                                int resultCode = STATUS_SUCCESS;
                                try {
                                    resultCode =
                                            jobManager.onTrainerStopCalled(
                                                    ownerComponent, populationName);
                                } catch (Exception e) {
                                    resultCode = STATUS_INTERNAL_ERROR;
                                    LogUtil.e(
                                            TAG,
                                            e,
                                            "Got exception when call Cancel population: %s, "
                                                    + "owner: %s",
                                            populationName,
                                            ownerComponent.flattenToString());
                                } finally {
                                    sendResult(callback, resultCode);
                                    int serviceLatency =
                                            (int) (mClock.elapsedRealtime() - startServiceTime);
                                    mFcStatsdLogger.logApiCallStats(
                                            new ApiCallStats.Builder()
                                                    .setApiName(
                                                    FEDERATED_COMPUTE_API_CALLED__API_NAME__CANCEL)
                                                    .setLatencyMillis(serviceLatency)
                                                    .setResponseCode(resultCode)
                                                    .build());
                                }
                            });
        } catch (NullPointerException | IllegalArgumentException | IllegalStateException ex) {
            LogUtil.e(TAG, ex, "Got exception for schedule()");
            throw ex;
        } catch (Exception e) {
            LogUtil.e(TAG, e, "Got exception for schedule()");
            sendResult(callback, STATUS_INTERNAL_ERROR);
        }
    }

    private void sendResult(@NonNull IFederatedComputeCallback callback, int resultCode) {
        try {
            if (resultCode == STATUS_SUCCESS) {
                callback.onSuccess();
                return;
            }
            callback.onFailure(resultCode);
        } catch (RemoteException e) {
            LogUtil.e(TAG, e, "Callback error");
        }
    }
}
