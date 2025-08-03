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

import static com.android.devicelockcontroller.policy.FinalizationControllerImpl.FinalizationState.FINALIZED;
import static com.android.devicelockcontroller.policy.FinalizationControllerImpl.FinalizationState.FINALIZED_UNREPORTED;
import static com.android.devicelockcontroller.policy.FinalizationControllerImpl.FinalizationState.UNFINALIZED;
import static com.android.devicelockcontroller.policy.FinalizationControllerImpl.FinalizationState.UNINITIALIZED;
import static com.android.devicelockcontroller.provision.worker.AbstractCheckInWorker.BACKOFF_DELAY;
import static com.android.devicelockcontroller.provision.worker.ReportDeviceLockProgramCompleteWorker.REPORT_DEVICE_LOCK_PROGRAM_COMPLETE_WORK_NAME;

import android.annotation.IntDef;
import android.app.AlarmManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.OutcomeReceiver;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ListenableWorker;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.Operation;
import androidx.work.WorkManager;

import com.android.devicelockcontroller.SystemDeviceLockManager;
import com.android.devicelockcontroller.SystemDeviceLockManagerImpl;
import com.android.devicelockcontroller.provision.grpc.DeviceFinalizeClient.ReportDeviceProgramCompleteResponse;
import com.android.devicelockcontroller.provision.worker.ReportDeviceLockProgramCompleteWorker;
import com.android.devicelockcontroller.receivers.FinalizationBootCompletedReceiver;
import com.android.devicelockcontroller.storage.GlobalParametersClient;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Implementation of {@link FinalizationController} that finalizes the device by reporting the
 * state to the server and effectively disabling this application entirely.
 */
public final class FinalizationControllerImpl implements FinalizationController {

    private static final String TAG = FinalizationControllerImpl.class.getSimpleName();

    @Target(ElementType.TYPE_USE)
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            UNFINALIZED,
            FINALIZED_UNREPORTED,
            FINALIZED,
            UNINITIALIZED
    })
    public @interface FinalizationState {
        /* Not finalized */
        int UNFINALIZED = 0;

        /* Device is finalized but still needs to report finalization to server */
        int FINALIZED_UNREPORTED = 1;

        /* Fully finalized. All bookkeeping is finished and okay to disable app. */
        int FINALIZED = 2;

        /* State has yet to be initialized */
        int UNINITIALIZED = -1;
    }

    /** Dispatch queue to guarantee state changes occur sequentially */
    private final FinalizationStateDispatchQueue mDispatchQueue;
    private final Executor mBgExecutor;
    private final Context mContext;
    private final SystemDeviceLockManager mSystemDeviceLockManager;
    private final Class<? extends ListenableWorker> mReportDeviceFinalizedWorkerClass;
    private final Object mLock = new Object();
    /** Future for after initial finalization state is set from disk */
    private volatile ListenableFuture<Void> mStateInitializedFuture;

    public FinalizationControllerImpl(Context context) {
        this(context,
                new FinalizationStateDispatchQueue(),
                Executors.newCachedThreadPool(),
                ReportDeviceLockProgramCompleteWorker.class,
                SystemDeviceLockManagerImpl.getInstance());
    }

    @VisibleForTesting
    public FinalizationControllerImpl(
            Context context,
            FinalizationStateDispatchQueue dispatchQueue,
            Executor bgExecutor,
            Class<? extends ListenableWorker> reportDeviceFinalizedWorkerClass,
            SystemDeviceLockManager systemDeviceLockManager) {
        mContext = context;
        mDispatchQueue = dispatchQueue;
        mDispatchQueue.init(this::onStateChanged);
        mBgExecutor = bgExecutor;
        mReportDeviceFinalizedWorkerClass = reportDeviceFinalizedWorkerClass;
        mSystemDeviceLockManager = systemDeviceLockManager;
    }

    @Override
    public ListenableFuture<Void> enforceInitialState() {
        ListenableFuture<Void> initializedFuture = mStateInitializedFuture;
        if (initializedFuture == null) {
            synchronized (mLock) {
                initializedFuture = mStateInitializedFuture;
                if (initializedFuture == null) {
                    ListenableFuture<Integer> initialStateFuture =
                            GlobalParametersClient.getInstance().getFinalizationState();
                    initializedFuture = Futures.transformAsync(initialStateFuture,
                            initialState -> {
                                LogUtil.d(TAG, "Enforcing initial state: " + initialState);
                                return mDispatchQueue.enqueueStateChange(initialState);
                            },
                            mBgExecutor);
                    mStateInitializedFuture = initializedFuture;
                }
            }
        }
        return initializedFuture;
    }

    @Override
    public ListenableFuture<Void> notifyRestrictionsCleared() {
        LogUtil.d(TAG, "Clearing restrictions");
        return Futures.transformAsync(enforceInitialState(),
                unused -> mDispatchQueue.enqueueStateChange(FINALIZED_UNREPORTED),
                mBgExecutor);
    }

    @Override
    public ListenableFuture<Void> notifyFinalizationReportResult(
            ReportDeviceProgramCompleteResponse response) {
        if (response.isSuccessful()) {
            LogUtil.d(TAG, "Successfully reported finalization to server. Finalizing...");
            return Futures.transformAsync(enforceInitialState(),
                    unused -> mDispatchQueue.enqueueStateChange(FINALIZED),
                    mBgExecutor);
        } else {
            // TODO(301320235): Determine how to handle an unrecoverable failure
            // response from the server
            LogUtil.e(TAG, "Unrecoverable failure in reporting finalization state: " + response);
            return Futures.immediateVoidFuture();
        }
    }

    @WorkerThread
    private ListenableFuture<Void> onStateChanged(@FinalizationState int oldState,
            @FinalizationState int newState) {
        final ListenableFuture<Void> persistStateFuture =
                GlobalParametersClient.getInstance().setFinalizationState(newState);
        if (oldState == UNFINALIZED) {
            // Enable boot receiver to check finalization state on disk
            PackageManager pm = mContext.getPackageManager();
            pm.setComponentEnabledSetting(
                    new ComponentName(mContext,
                            FinalizationBootCompletedReceiver.class),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
        }
        switch (newState) {
            case UNFINALIZED:
                return persistStateFuture;
            case FINALIZED_UNREPORTED:
                requestWorkToReportFinalized();
                return persistStateFuture;
            case FINALIZED:
                // Ensure disabling only happens after state is written to disk in case we somehow
                // exit the disabled state and need to disable again.
                return Futures.transformAsync(persistStateFuture,
                        unused -> disableEntireApplication(),
                        mBgExecutor);
            case UNINITIALIZED:
                throw new IllegalArgumentException("Tried to set state back to uninitialized!");
            default:
                throw new IllegalArgumentException("Unknown state " + newState);
        }
    }

    /**
     * Request work to report device is finalized.
     */
    private void requestWorkToReportFinalized() {
        WorkManager workManager =
                WorkManager.getInstance(mContext);
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest work =
                new OneTimeWorkRequest.Builder(mReportDeviceFinalizedWorkerClass)
                        .setConstraints(constraints)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY)
                        .build();
        ListenableFuture<Operation.State.SUCCESS> result =
                workManager.enqueueUniqueWork(REPORT_DEVICE_LOCK_PROGRAM_COMPLETE_WORK_NAME,
                        ExistingWorkPolicy.REPLACE, work).getResult();
        Futures.addCallback(result,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Operation.State.SUCCESS result) {
                        // no-op
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        // Don't reset the device in this case since the financing program is
                        // effectively over.
                        LogUtil.e(TAG, "Failed to enqueue 'device lock program complete' work",
                                t);
                    }
                },
                MoreExecutors.directExecutor()
        );
    }

    /**
     * Disables the entire device lock controller application.
     *
     * This will remove any work, alarms, receivers, etc., and this application should never run
     * on the device again after this point.
     *
     * This method returns a future but it is a bit of an odd case as the application itself
     * may end up disabled before/after the future is handled depending on when package manager
     * enforces the application is disabled.
     *
     * @return future for when this is done
     */
    private ListenableFuture<Void> disableEntireApplication() {
        WorkManager workManager = WorkManager.getInstance(mContext);
        workManager.cancelAllWork();
        AlarmManager alarmManager = mContext.getSystemService(AlarmManager.class);
        alarmManager.cancelAll();
        // This kills and disables the app
        ListenableFuture<Void> disableApplicationFuture = CallbackToFutureAdapter.getFuture(
                completer -> {
                        mSystemDeviceLockManager.setDeviceFinalized(true, mBgExecutor,
                                new OutcomeReceiver<>() {
                                    @Override
                                    public void onResult(Void result) {
                                        completer.set(null);
                                    }

                                    @Override
                                    public void onError(@NonNull Exception error) {
                                        LogUtil.e(TAG, "Failed to set device finalized in"
                                                + "system service.", error);
                                        completer.setException(error);
                                    }
                                });
                    return "Disable application future";
                }
        );
        return disableApplicationFuture;
    }
}
