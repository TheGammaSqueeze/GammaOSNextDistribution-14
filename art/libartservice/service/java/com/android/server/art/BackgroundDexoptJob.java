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

package com.android.server.art;

import static com.android.server.art.ArtManagerLocal.ScheduleBackgroundDexoptJobCallback;
import static com.android.server.art.model.ArtFlags.BatchDexoptPass;
import static com.android.server.art.model.ArtFlags.ScheduleStatus;
import static com.android.server.art.model.Config.Callback;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.Log;
import android.util.Slog;

import androidx.annotation.RequiresApi;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.LocalManagerRegistry;
import com.android.server.art.model.ArtFlags;
import com.android.server.art.model.ArtServiceJobInterface;
import com.android.server.art.model.Config;
import com.android.server.art.model.DexoptResult;
import com.android.server.art.model.OperationProgress;
import com.android.server.pm.PackageManagerLocal;

import com.google.auto.value.AutoValue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** @hide */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
public class BackgroundDexoptJob implements ArtServiceJobInterface {
    private static final String TAG = ArtManagerLocal.TAG;

    /**
     * "android" is the package name for a <service> declared in
     * frameworks/base/core/res/AndroidManifest.xml
     */
    private static final String JOB_PKG_NAME = Utils.PLATFORM_PACKAGE_NAME;
    /** An arbitrary number. Must be unique among all jobs owned by the system uid. */
    public static final int JOB_ID = 27873780;

    @VisibleForTesting public static final long JOB_INTERVAL_MS = TimeUnit.DAYS.toMillis(1);

    @NonNull private final Injector mInjector;

    @GuardedBy("this") @Nullable private CompletableFuture<Result> mRunningJob = null;
    @GuardedBy("this") @Nullable private CancellationSignal mCancellationSignal = null;
    @GuardedBy("this") @NonNull private Optional<Integer> mLastStopReason = Optional.empty();

    public BackgroundDexoptJob(@NonNull Context context, @NonNull ArtManagerLocal artManagerLocal,
            @NonNull Config config) {
        this(new Injector(context, artManagerLocal, config));
    }

    @VisibleForTesting
    public BackgroundDexoptJob(@NonNull Injector injector) {
        mInjector = injector;
    }

    /** Handles {@link BackgroundDexoptJobService#onStartJob(JobParameters)}. */
    @Override
    public boolean onStartJob(
            @NonNull BackgroundDexoptJobService jobService, @NonNull JobParameters params) {
        start().thenAcceptAsync(result -> {
            try {
                writeStats(result);
            } catch (RuntimeException e) {
                // Not expected. Log wtf to surface it.
                Slog.wtf(TAG, "Failed to write stats", e);
            }

            // This is a periodic job, where the interval is specified in the `JobInfo`. "true"
            // means to execute again in the same interval with the default retry policy, while
            // "false" means not to execute again in the same interval but to execute again in the
            // next interval.
            // This call will be ignored if `onStopJob` is called.
            boolean wantsReschedule =
                    result instanceof CompletedResult && ((CompletedResult) result).isCancelled();
            jobService.jobFinished(params, wantsReschedule);
        });
        // "true" means the job will continue running until `jobFinished` is called.
        return true;
    }

    /** Handles {@link BackgroundDexoptJobService#onStopJob(JobParameters)}. */
    @Override
    public boolean onStopJob(@NonNull JobParameters params) {
        synchronized (this) {
            mLastStopReason = Optional.of(params.getStopReason());
        }
        cancel();
        // "true" means to execute again in the same interval with the default retry policy.
        return true;
    }

    /** Handles {@link ArtManagerLocal#scheduleBackgroundDexoptJob()}. */
    public @ScheduleStatus int schedule() {
        if (this != BackgroundDexoptJobService.getJob(JOB_ID)) {
            throw new IllegalStateException("This job cannot be scheduled");
        }

        if (SystemProperties.getBoolean("pm.dexopt.disable_bg_dexopt", false /* def */)) {
            Log.i(TAG, "Job is disabled by system property 'pm.dexopt.disable_bg_dexopt'");
            return ArtFlags.SCHEDULE_DISABLED_BY_SYSPROP;
        }

        JobInfo.Builder builder =
                new JobInfo
                        .Builder(JOB_ID,
                                new ComponentName(
                                        JOB_PKG_NAME, BackgroundDexoptJobService.class.getName()))
                        .setPeriodic(JOB_INTERVAL_MS)
                        .setRequiresDeviceIdle(true)
                        .setRequiresCharging(true)
                        .setRequiresBatteryNotLow(true);

        Callback<ScheduleBackgroundDexoptJobCallback, Void> callback =
                mInjector.getConfig().getScheduleBackgroundDexoptJobCallback();
        if (callback != null) {
            Utils.executeAndWait(
                    callback.executor(), () -> { callback.get().onOverrideJobInfo(builder); });
        }

        JobInfo info = builder.build();
        if (info.isRequireStorageNotLow()) {
            // See the javadoc of
            // `ArtManagerLocal.ScheduleBackgroundDexoptJobCallback.onOverrideJobInfo` for details.
            throw new IllegalStateException("'setRequiresStorageNotLow' must not be set");
        }

        return mInjector.getJobScheduler().schedule(info) == JobScheduler.RESULT_SUCCESS
                ? ArtFlags.SCHEDULE_SUCCESS
                : ArtFlags.SCHEDULE_JOB_SCHEDULER_FAILURE;
    }

    /** Handles {@link ArtManagerLocal#unscheduleBackgroundDexoptJob()}. */
    public void unschedule() {
        if (this != BackgroundDexoptJobService.getJob(JOB_ID)) {
            throw new IllegalStateException("This job cannot be unscheduled");
        }

        mInjector.getJobScheduler().cancel(JOB_ID);
    }

    @NonNull
    public synchronized CompletableFuture<Result> start() {
        if (mRunningJob != null) {
            Log.i(TAG, "Job is already running");
            return mRunningJob;
        }

        mCancellationSignal = new CancellationSignal();
        mLastStopReason = Optional.empty();
        mRunningJob = new CompletableFuture().supplyAsync(() -> {
            try (var tracing = new Utils.TracingWithTimingLogging(TAG, "jobExecution")) {
                return run(mCancellationSignal);
            } catch (RuntimeException e) {
                Log.e(TAG, "Fatal error", e);
                return new FatalErrorResult();
            } finally {
                synchronized (this) {
                    mRunningJob = null;
                    mCancellationSignal = null;
                }
            }
        });
        return mRunningJob;
    }

    public synchronized void cancel() {
        if (mRunningJob == null) {
            Log.i(TAG, "Job is not running");
            return;
        }

        mCancellationSignal.cancel();
        Log.i(TAG, "Job cancelled");
    }

    @Nullable
    public synchronized CompletableFuture<Result> get() {
        return mRunningJob;
    }

    @NonNull
    private CompletedResult run(@NonNull CancellationSignal cancellationSignal) {
        // Create callbacks to time each pass.
        Map<Integer, Long> startTimeMsByPass = new HashMap<>();
        Map<Integer, Long> durationMsByPass = new HashMap<>();
        Map<Integer, Consumer<OperationProgress>> progressCallbacks = new HashMap<>();
        for (@BatchDexoptPass int pass : ArtFlags.BATCH_DEXOPT_PASSES) {
            progressCallbacks.put(pass, progress -> {
                if (progress.getTotal() == 0) {
                    durationMsByPass.put(pass, 0l);
                } else if (progress.getCurrent() == 0) {
                    startTimeMsByPass.put(pass, SystemClock.uptimeMillis());
                } else if (progress.getCurrent() == progress.getTotal()) {
                    durationMsByPass.put(
                            pass, SystemClock.uptimeMillis() - startTimeMsByPass.get(pass));
                }
            });
        }

        Map<Integer, DexoptResult> dexoptResultByPass;
        try (var snapshot = mInjector.getPackageManagerLocal().withFilteredSnapshot()) {
            dexoptResultByPass = mInjector.getArtManagerLocal().dexoptPackages(snapshot,
                    ReasonMapping.REASON_BG_DEXOPT, cancellationSignal, Runnable::run,
                    progressCallbacks);

            // For simplicity, we don't support cancelling the following operation in the middle.
            // This is fine because it typically takes only a few seconds.
            if (!cancellationSignal.isCanceled()) {
                // We do the cleanup after dexopt so that it doesn't affect the `getSizeBeforeBytes`
                // field in the result that we send to callbacks. Admittedly, this will cause us to
                // lose some chance to dexopt when the storage is very low, but it's fine because we
                // can still dexopt in the next run.
                long freedBytes = mInjector.getArtManagerLocal().cleanup(snapshot);
                Log.i(TAG, String.format("Freed %d bytes", freedBytes));
            }
        }
        return CompletedResult.create(dexoptResultByPass, durationMsByPass);
    }

    private void writeStats(@NonNull Result result) {
        Optional<Integer> stopReason;
        synchronized (this) {
            stopReason = mLastStopReason;
        }
        if (result instanceof CompletedResult completedResult) {
            BackgroundDexoptJobStatsReporter.reportSuccess(completedResult, stopReason);
        } else if (result instanceof FatalErrorResult) {
            BackgroundDexoptJobStatsReporter.reportFailure();
        }
    }

    static abstract class Result {}
    static class FatalErrorResult extends Result {}

    @AutoValue
    @SuppressWarnings("AutoValueImmutableFields") // Can't use ImmutableMap because it's in Guava.
    static abstract class CompletedResult extends Result {
        abstract @NonNull Map<Integer, DexoptResult> dexoptResultByPass();
        abstract @NonNull Map<Integer, Long> durationMsByPass();

        @NonNull
        static CompletedResult create(@NonNull Map<Integer, DexoptResult> dexoptResultByPass,
                @NonNull Map<Integer, Long> durationMsByPass) {
            return new AutoValue_BackgroundDexoptJob_CompletedResult(
                    Collections.unmodifiableMap(dexoptResultByPass),
                    Collections.unmodifiableMap(durationMsByPass));
        }

        public boolean isCancelled() {
            return dexoptResultByPass().values().stream().anyMatch(
                    result -> result.getFinalStatus() == DexoptResult.DEXOPT_CANCELLED);
        }
    }

    /**
     * Injector pattern for testing purpose.
     *
     * @hide
     */
    @VisibleForTesting
    public static class Injector {
        @NonNull private final Context mContext;
        @NonNull private final ArtManagerLocal mArtManagerLocal;
        @NonNull private final Config mConfig;

        Injector(@NonNull Context context, @NonNull ArtManagerLocal artManagerLocal,
                @NonNull Config config) {
            mContext = context;
            mArtManagerLocal = artManagerLocal;
            mConfig = config;

            // Call the getters for various dependencies, to ensure correct initialization order.
            getPackageManagerLocal();
            getJobScheduler();
        }

        @NonNull
        public ArtManagerLocal getArtManagerLocal() {
            return mArtManagerLocal;
        }

        @NonNull
        public PackageManagerLocal getPackageManagerLocal() {
            return Objects.requireNonNull(
                    LocalManagerRegistry.getManager(PackageManagerLocal.class));
        }

        @NonNull
        public Config getConfig() {
            return mConfig;
        }

        @NonNull
        public JobScheduler getJobScheduler() {
            return Objects.requireNonNull(mContext.getSystemService(JobScheduler.class));
        }
    }
}
