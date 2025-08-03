/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.android.server.art.ArtFileManager.ProfileLists;
import static com.android.server.art.ArtFileManager.UsableArtifactLists;
import static com.android.server.art.ArtFileManager.WritableArtifactLists;
import static com.android.server.art.PrimaryDexUtils.DetailedPrimaryDexInfo;
import static com.android.server.art.PrimaryDexUtils.PrimaryDexInfo;
import static com.android.server.art.ReasonMapping.BatchDexoptReason;
import static com.android.server.art.ReasonMapping.BootReason;
import static com.android.server.art.Utils.Abi;
import static com.android.server.art.Utils.InitProfileResult;
import static com.android.server.art.model.ArtFlags.GetStatusFlags;
import static com.android.server.art.model.ArtFlags.ScheduleStatus;
import static com.android.server.art.model.Config.Callback;
import static com.android.server.art.model.DexoptStatus.DexContainerFileDexoptStatus;

import android.annotation.CallbackExecutor;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.annotation.SystemApi;
import android.annotation.SystemService;
import android.app.job.JobInfo;
import android.apphibernation.AppHibernationManager;
import android.content.Context;
import android.os.Binder;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.storage.StorageManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.RequiresApi;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.LocalManagerRegistry;
import com.android.server.art.model.ArtFlags;
import com.android.server.art.model.ArtManagedFileStats;
import com.android.server.art.model.BatchDexoptParams;
import com.android.server.art.model.Config;
import com.android.server.art.model.DeleteResult;
import com.android.server.art.model.DetailedDexInfo;
import com.android.server.art.model.DexoptParams;
import com.android.server.art.model.DexoptResult;
import com.android.server.art.model.DexoptStatus;
import com.android.server.art.model.OperationProgress;
import com.android.server.pm.PackageManagerLocal;
import com.android.server.pm.pkg.AndroidPackage;
import com.android.server.pm.pkg.AndroidPackageSplit;
import com.android.server.pm.pkg.PackageState;

import dalvik.system.DexFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class provides a system API for functionality provided by the ART module.
 *
 * Note: Although this class is the entry point of ART services, this class is not a {@link
 * SystemService}, and it does not publish a binder. Instead, it is a module loaded by the
 * system_server process, registered in {@link LocalManagerRegistry}. {@link LocalManagerRegistry}
 * specifies that in-process module interfaces should be named with the suffix {@code ManagerLocal}
 * for consistency.
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.SYSTEM_SERVER)
public final class ArtManagerLocal {
    /** @hide */
    public static final String TAG = "ArtService";

    private static final String[] CLASSPATHS_FOR_BOOT_IMAGE_PROFILE = {
            "BOOTCLASSPATH", "SYSTEMSERVERCLASSPATH", "STANDALONE_SYSTEMSERVER_JARS"};

    /** @hide */
    @VisibleForTesting public static final long DOWNGRADE_THRESHOLD_ABOVE_LOW_BYTES = 500_000_000;

    @NonNull private final Injector mInjector;

    @Deprecated
    public ArtManagerLocal() {
        mInjector = new Injector();
    }

    /**
     * Creates an instance.
     *
     * Only {@code SystemServer} should create an instance and register it in {@link
     * LocalManagerRegistry}. Other API users should obtain the instance from {@link
     * LocalManagerRegistry}.
     *
     * @param context the system server context
     * @throws NullPointerException if required dependencies are missing
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public ArtManagerLocal(@NonNull Context context) {
        mInjector = new Injector(this, context);
    }

    /** @hide */
    @VisibleForTesting
    public ArtManagerLocal(@NonNull Injector injector) {
        mInjector = injector;
    }

    /**
     * Handles ART Service commands, which is a subset of `cmd package` commands.
     *
     * Note: This method is not an override of {@link Binder#handleShellCommand} because ART
     * services does not publish a binder. Instead, it handles the commands forwarded by the
     * `package` service. The semantics of the parameters are the same as {@link
     * Binder#handleShellCommand}.
     *
     * @return zero on success, non-zero on internal error (e.g., I/O error)
     * @throws SecurityException if the caller is not root
     * @throws IllegalArgumentException if the arguments are illegal
     * @see ArtShellCommand#printHelp(PrintWriter)
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public int handleShellCommand(@NonNull Binder target, @NonNull ParcelFileDescriptor in,
            @NonNull ParcelFileDescriptor out, @NonNull ParcelFileDescriptor err,
            @NonNull String[] args) {
        return new ArtShellCommand(this, mInjector.getPackageManagerLocal())
                .exec(target, in.getFileDescriptor(), out.getFileDescriptor(),
                        err.getFileDescriptor(), args);
    }

    /** Prints ART Service shell command help. */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void printShellCommandHelp(@NonNull PrintWriter pw) {
        ArtShellCommand.printHelp(pw);
    }

    /**
     * Deletes dexopt artifacts of a package, including the artifacts for primary dex files and the
     * ones for secondary dex files. This includes VDEX, ODEX, and ART files.
     *
     * Also deletes runtime artifacts of the package, though they are not dexopt artifacts.
     *
     * @throws IllegalArgumentException if the package is not found or the flags are illegal
     * @throws IllegalStateException if the operation encounters an error that should never happen
     *         (e.g., an internal logic error).
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @NonNull
    public DeleteResult deleteDexoptArtifacts(
            @NonNull PackageManagerLocal.FilteredSnapshot snapshot, @NonNull String packageName) {
        PackageState pkgState = Utils.getPackageStateOrThrow(snapshot, packageName);
        AndroidPackage pkg = Utils.getPackageOrThrow(pkgState);

        try (var pin = mInjector.createArtdPin()) {
            long freedBytes = 0;
            WritableArtifactLists list =
                    mInjector.getArtFileManager().getWritableArtifacts(pkgState, pkg);
            for (ArtifactsPath artifacts : list.artifacts()) {
                freedBytes += mInjector.getArtd().deleteArtifacts(artifacts);
            }
            for (RuntimeArtifactsPath runtimeArtifacts : list.runtimeArtifacts()) {
                freedBytes += mInjector.getArtd().deleteRuntimeArtifacts(runtimeArtifacts);
            }
            return DeleteResult.create(freedBytes);
        } catch (RemoteException e) {
            Utils.logArtdException(e);
            return DeleteResult.create(0 /* freedBytes */);
        }
    }

    /**
     * Returns the dexopt status of all known dex container files of a package, even if some of them
     * aren't readable.
     *
     * Uses the default flags ({@link ArtFlags#defaultGetStatusFlags()}).
     *
     * @throws IllegalArgumentException if the package is not found or the flags are illegal
     * @throws IllegalStateException if the operation encounters an error that should never happen
     *         (e.g., an internal logic error).
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @NonNull
    public DexoptStatus getDexoptStatus(
            @NonNull PackageManagerLocal.FilteredSnapshot snapshot, @NonNull String packageName) {
        return getDexoptStatus(snapshot, packageName, ArtFlags.defaultGetStatusFlags());
    }

    /**
     * Same as above, but allows to specify flags.
     *
     * @see #getDexoptStatus(PackageManagerLocal.FilteredSnapshot, String)
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @NonNull
    public DexoptStatus getDexoptStatus(@NonNull PackageManagerLocal.FilteredSnapshot snapshot,
            @NonNull String packageName, @GetStatusFlags int flags) {
        if ((flags & ArtFlags.FLAG_FOR_PRIMARY_DEX) == 0
                && (flags & ArtFlags.FLAG_FOR_SECONDARY_DEX) == 0) {
            throw new IllegalArgumentException("Nothing to check");
        }

        PackageState pkgState = Utils.getPackageStateOrThrow(snapshot, packageName);
        AndroidPackage pkg = Utils.getPackageOrThrow(pkgState);
        List<Pair<DetailedDexInfo, Abi>> dexAndAbis = mInjector.getArtFileManager().getDexAndAbis(
                pkgState, pkg, (flags & ArtFlags.FLAG_FOR_PRIMARY_DEX) != 0,
                (flags & ArtFlags.FLAG_FOR_SECONDARY_DEX) != 0,
                false /* excludeObsoleteDexesAndLoaders */);

        try (var pin = mInjector.createArtdPin()) {
            List<DexContainerFileDexoptStatus> statuses = new ArrayList<>();

            for (Pair<DetailedDexInfo, Abi> pair : dexAndAbis) {
                DetailedDexInfo dexInfo = pair.first;
                Abi abi = pair.second;
                try {
                    GetDexoptStatusResult result = mInjector.getArtd().getDexoptStatus(
                            dexInfo.dexPath(), abi.isa(), dexInfo.classLoaderContext());
                    statuses.add(DexContainerFileDexoptStatus.create(dexInfo.dexPath(),
                            dexInfo instanceof DetailedPrimaryDexInfo, abi.isPrimaryAbi(),
                            abi.name(), result.compilerFilter, result.compilationReason,
                            result.locationDebugString));
                } catch (ServiceSpecificException e) {
                    statuses.add(DexContainerFileDexoptStatus.create(dexInfo.dexPath(),
                            dexInfo instanceof DetailedPrimaryDexInfo, abi.isPrimaryAbi(),
                            abi.name(), "error", "error", e.getMessage()));
                }
            }

            return DexoptStatus.create(statuses);
        } catch (RemoteException e) {
            Utils.logArtdException(e);
            List<DexContainerFileDexoptStatus> statuses = new ArrayList<>();
            for (Pair<DetailedDexInfo, Abi> pair : dexAndAbis) {
                DetailedDexInfo dexInfo = pair.first;
                Abi abi = pair.second;
                statuses.add(DexContainerFileDexoptStatus.create(dexInfo.dexPath(),
                        dexInfo instanceof DetailedPrimaryDexInfo, abi.isPrimaryAbi(), abi.name(),
                        "error", "error", e.getMessage()));
            }
            return DexoptStatus.create(statuses);
        }
    }

    /**
     * Clear the profiles that are collected locally for the given package, including the profiles
     * for primary and secondary dex files. More specifically, it clears reference profiles and
     * current profiles. External profiles (e.g., cloud profiles) will be kept.
     *
     * @throws IllegalArgumentException if the package is not found or the flags are illegal
     * @throws IllegalStateException if the operation encounters an error that should never happen
     *         (e.g., an internal logic error).
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @NonNull
    public void clearAppProfiles(
            @NonNull PackageManagerLocal.FilteredSnapshot snapshot, @NonNull String packageName) {
        PackageState pkgState = Utils.getPackageStateOrThrow(snapshot, packageName);
        AndroidPackage pkg = Utils.getPackageOrThrow(pkgState);

        try (var pin = mInjector.createArtdPin()) {
            // We want to delete as many profiles as possible, so this deletes profiles of all known
            // secondary dex files. If there are unknown secondary dex files, their profiles will be
            // deleted by `cleanup`.
            ProfileLists list = mInjector.getArtFileManager().getProfiles(pkgState, pkg,
                    true /* alsoForSecondaryDex */, false /* excludeForObsoleteDexesAndLoaders */);
            for (ProfilePath profile : list.allProfiles()) {
                mInjector.getArtd().deleteProfile(profile);
            }
        } catch (RemoteException e) {
            Utils.logArtdException(e);
        }
    }

    /**
     * Dexopts a package. The time this operation takes ranges from a few milliseconds to several
     * minutes, depending on the params and the code size of the package.
     *
     * When dexopt is successfully performed for a dex container file, this operation also deletes
     * the corresponding runtime artifacts (the ART files in the package's data directory, which are
     * generated by the runtime, not by dexopt).
     *
     * When this operation ends (either completed or cancelled), callbacks added by {@link
     * #addDexoptDoneCallback(Executor, DexoptDoneCallback)} are called.
     *
     * @throws IllegalArgumentException if the package is not found or the params are illegal
     * @throws IllegalStateException if the operation encounters an error that should never happen
     *         (e.g., an internal logic error).
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @NonNull
    public DexoptResult dexoptPackage(@NonNull PackageManagerLocal.FilteredSnapshot snapshot,
            @NonNull String packageName, @NonNull DexoptParams params) {
        var cancellationSignal = new CancellationSignal();
        return dexoptPackage(snapshot, packageName, params, cancellationSignal);
    }

    /**
     * Same as above, but supports cancellation.
     *
     * @see #dexoptPackage(PackageManagerLocal.FilteredSnapshot, String, DexoptParams)
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @NonNull
    public DexoptResult dexoptPackage(@NonNull PackageManagerLocal.FilteredSnapshot snapshot,
            @NonNull String packageName, @NonNull DexoptParams params,
            @NonNull CancellationSignal cancellationSignal) {
        try (var pin = mInjector.createArtdPin()) {
            return mInjector.getDexoptHelper().dexopt(
                    snapshot, List.of(packageName), params, cancellationSignal, Runnable::run);
        }
    }

    /**
     * Resets the dexopt state of the package as if the package is newly installed.
     *
     * More specifically, it clears reference profiles, current profiles, any code compiled from
     * those local profiles, and runtime artifacts. If there is an external profile (e.g., a cloud
     * profile), the code compiled from that profile will be kept.
     *
     * For secondary dex files, it also clears all dexopt artifacts.
     *
     * @hide
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @NonNull
    public DexoptResult resetDexoptStatus(@NonNull PackageManagerLocal.FilteredSnapshot snapshot,
            @NonNull String packageName, @NonNull CancellationSignal cancellationSignal) {
        // We must delete the artifacts for primary dex files beforehand rather than relying on
        // `dexoptPackage` to replace them because:
        // - If dexopt is not needed after the deletion, then we shouldn't run dexopt at all. For
        //   example, when we have a DM file that contains a VDEX file but doesn't contain a cloud
        //   profile, this happens. Note that this is more about correctness rather than
        //   performance.
        // - We don't want the existing artifacts to affect dexopt. For example, the existing VDEX
        //   file should not be an input VDEX.
        //
        // We delete the artifacts for secondary dex files and `dexoptPackage` won't re-generate
        // them because `dexoptPackage` for `REASON_INSTALL` is for primary dex only. This is
        // intentional because secondary dex files are supposed to be unknown at install time.
        deleteDexoptArtifacts(snapshot, packageName);
        clearAppProfiles(snapshot, packageName);

        // Re-generate artifacts for primary dex files if needed.
        return dexoptPackage(snapshot, packageName,
                new DexoptParams.Builder(ReasonMapping.REASON_INSTALL).build(), cancellationSignal);
    }

    /**
     * Runs batch dexopt for the given reason.
     *
     * This is called by ART Service automatically during boot / background dexopt.
     *
     * The list of packages and options are determined by {@code reason}, and can be overridden by
     * {@link #setBatchDexoptStartCallback(Executor, BatchDexoptStartCallback)}.
     *
     * The dexopt is done in a thread pool. The number of packages being dexopted
     * simultaneously can be configured by system property {@code pm.dexopt.<reason>.concurrency}
     * (e.g., {@code pm.dexopt.bg-dexopt.concurrency=4}), and the number of threads for each {@code
     * dex2oat} invocation can be configured by system property {@code dalvik.vm.*dex2oat-threads}
     * (e.g., {@code dalvik.vm.background-dex2oat-threads=4}). I.e., the maximum number of
     * concurrent threads is the product of the two system properties. Note that the physical core
     * usage is always bound by {@code dalvik.vm.*dex2oat-cpu-set} regardless of the number of
     * threads.
     *
     * When this operation ends (either completed or cancelled), callbacks added by {@link
     * #addDexoptDoneCallback(Executor, DexoptDoneCallback)} are called.
     *
     * If the storage is nearly low, and {@code reason} is {@link ReasonMapping#REASON_BG_DEXOPT},
     * it may also downgrade some inactive packages to a less optimized compiler filter, specified
     * by the system property {@code pm.dexopt.inactive} (typically "verify"), to free up some
     * space. This feature is only enabled when the system property {@code
     * pm.dexopt.downgrade_after_inactive_days} is set. The space threshold to trigger this feature
     * is the Storage Manager's low space threshold plus {@link
     * #DOWNGRADE_THRESHOLD_ABOVE_LOW_BYTES}. The concurrency can be configured by system property
     * {@code pm.dexopt.bg-dexopt.concurrency}. The packages in the list provided by
     * {@link BatchDexoptStartCallback} for {@link ReasonMapping#REASON_BG_DEXOPT} are never
     * downgraded.
     *
     * @param snapshot the snapshot from {@link PackageManagerLocal} to operate on
     * @param reason determines the default list of packages and options
     * @param cancellationSignal provides the ability to cancel this operation
     * @param processCallbackExecutor the executor to call {@code progressCallback}
     * @param progressCallbacks a mapping from an integer, in {@link ArtFlags.BatchDexoptPass}, to
     *         the callback that is called repeatedly whenever there is an update on the progress
     * @return a mapping from an integer, in {@link ArtFlags.BatchDexoptPass}, to the dexopt result.
     * @throws IllegalStateException if the operation encounters an error that should never happen
     *         (e.g., an internal logic error), or the callback set by {@link
     *         #setBatchDexoptStartCallback(Executor, BatchDexoptStartCallback)} provides invalid
     *         params.
     *
     * @hide
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @NonNull
    public Map<Integer, DexoptResult> dexoptPackages(
            @NonNull PackageManagerLocal.FilteredSnapshot snapshot,
            @NonNull @BatchDexoptReason String reason,
            @NonNull CancellationSignal cancellationSignal,
            @Nullable @CallbackExecutor Executor progressCallbackExecutor,
            @Nullable Map<Integer, Consumer<OperationProgress>> progressCallbacks) {
        List<String> defaultPackages =
                Collections.unmodifiableList(getDefaultPackages(snapshot, reason));
        DexoptParams defaultDexoptParams = new DexoptParams.Builder(reason).build();
        var builder = new BatchDexoptParams.Builder(defaultPackages, defaultDexoptParams);
        Callback<BatchDexoptStartCallback, Void> callback =
                mInjector.getConfig().getBatchDexoptStartCallback();
        if (callback != null) {
            Utils.executeAndWait(callback.executor(), () -> {
                callback.get().onBatchDexoptStart(
                        snapshot, reason, defaultPackages, builder, cancellationSignal);
            });
        }
        BatchDexoptParams params = builder.build();
        Utils.check(params.getDexoptParams().getReason().equals(reason));

        ExecutorService dexoptExecutor =
                Executors.newFixedThreadPool(ReasonMapping.getConcurrencyForReason(reason));
        Map<Integer, DexoptResult> dexoptResults = new HashMap<>();
        try (var pin = mInjector.createArtdPin()) {
            if (reason.equals(ReasonMapping.REASON_BG_DEXOPT)) {
                DexoptResult downgradeResult = maybeDowngradePackages(snapshot,
                        new HashSet<>(params.getPackages()) /* excludedPackages */,
                        cancellationSignal, dexoptExecutor, progressCallbackExecutor,
                        progressCallbacks != null ? progressCallbacks.get(ArtFlags.PASS_DOWNGRADE)
                                                  : null);
                if (downgradeResult != null) {
                    dexoptResults.put(ArtFlags.PASS_DOWNGRADE, downgradeResult);
                }
            }
            Log.i(TAG,
                    "Dexopting " + params.getPackages().size() + " packages with reason=" + reason);
            DexoptResult mainResult = mInjector.getDexoptHelper().dexopt(snapshot,
                    params.getPackages(), params.getDexoptParams(), cancellationSignal,
                    dexoptExecutor, progressCallbackExecutor,
                    progressCallbacks != null ? progressCallbacks.get(ArtFlags.PASS_MAIN) : null);
            dexoptResults.put(ArtFlags.PASS_MAIN, mainResult);
            if (reason.equals(ReasonMapping.REASON_BG_DEXOPT)) {
                DexoptResult supplementaryResult = maybeDexoptPackagesSupplementaryPass(snapshot,
                        mainResult, params.getDexoptParams(), cancellationSignal, dexoptExecutor,
                        progressCallbackExecutor,
                        progressCallbacks != null
                                ? progressCallbacks.get(ArtFlags.PASS_SUPPLEMENTARY)
                                : null);
                if (supplementaryResult != null) {
                    dexoptResults.put(ArtFlags.PASS_SUPPLEMENTARY, supplementaryResult);
                }
            }
            return dexoptResults;
        } finally {
            dexoptExecutor.shutdown();
        }
    }

    /**
     * Overrides the default params for {@link #dexoptPackages}. This method is thread-safe.
     *
     * This method gives users the opportunity to change the behavior of {@link #dexoptPackages},
     * which is called by ART Service automatically during boot / background dexopt.
     *
     * If this method is not called, the default list of packages and options determined by {@code
     * reason} will be used.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void setBatchDexoptStartCallback(@NonNull @CallbackExecutor Executor executor,
            @NonNull BatchDexoptStartCallback callback) {
        mInjector.getConfig().setBatchDexoptStartCallback(executor, callback);
    }

    /**
     * Clears the callback set by {@link
     * #setBatchDexoptStartCallback(Executor, BatchDexoptStartCallback)}. This method is
     * thread-safe.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void clearBatchDexoptStartCallback() {
        mInjector.getConfig().clearBatchDexoptStartCallback();
    }

    /**
     * Schedules a background dexopt job. Does nothing if the job is already scheduled.
     *
     * Use this method if you want the system to automatically determine the best time to run
     * dexopt.
     *
     * The job will be run by the job scheduler. The job scheduling configuration can be overridden
     * by {@link
     * #setScheduleBackgroundDexoptJobCallback(Executor, ScheduleBackgroundDexoptJobCallback)}. By
     * default, it runs periodically (at most once a day) when all the following constraints are
     * meet.
     *
     * <ul>
     *   <li>The device is idling. (see {@link JobInfo.Builder#setRequiresDeviceIdle(boolean)})
     *   <li>The device is charging. (see {@link JobInfo.Builder#setRequiresCharging(boolean)})
     *   <li>The battery level is not low.
     *     (see {@link JobInfo.Builder#setRequiresBatteryNotLow(boolean)})
     * </ul>
     *
     * When the job is running, it may be cancelled by the job scheduler immediately whenever one of
     * the constraints above is no longer met or cancelled by the {@link
     * #cancelBackgroundDexoptJob()} API. The job scheduler retries it with the default retry policy
     * (30 seconds, exponential, capped at 5hrs).
     *
     * See {@link #dexoptPackages} for how to customize the behavior of the job.
     *
     * When the job ends (either completed or cancelled), the result is sent to the callbacks added
     * by {@link #addDexoptDoneCallback(Executor, DexoptDoneCallback)} with the
     * reason {@link ReasonMapping#REASON_BG_DEXOPT}.
     *
     * @throws RuntimeException if called during boot before the job scheduler service has started.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public @ScheduleStatus int scheduleBackgroundDexoptJob() {
        return mInjector.getBackgroundDexoptJob().schedule();
    }

    /**
     * Unschedules the background dexopt job scheduled by {@link #scheduleBackgroundDexoptJob()}.
     * Does nothing if the job is not scheduled.
     *
     * Use this method if you no longer want the system to automatically run dexopt.
     *
     * If the job is already started by the job scheduler and is running, it will be cancelled
     * immediately, and the result sent to the callbacks added by {@link
     * #addDexoptDoneCallback(Executor, DexoptDoneCallback)} will contain {@link
     * DexoptResult#DEXOPT_CANCELLED}. Note that a job started by {@link
     * #startBackgroundDexoptJob()} will not be cancelled by this method.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void unscheduleBackgroundDexoptJob() {
        mInjector.getBackgroundDexoptJob().unschedule();
    }

    /**
     * Overrides the configuration of the background dexopt job. This method is thread-safe.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void setScheduleBackgroundDexoptJobCallback(@NonNull @CallbackExecutor Executor executor,
            @NonNull ScheduleBackgroundDexoptJobCallback callback) {
        mInjector.getConfig().setScheduleBackgroundDexoptJobCallback(executor, callback);
    }

    /**
     * Clears the callback set by {@link
     * #setScheduleBackgroundDexoptJobCallback(Executor, ScheduleBackgroundDexoptJobCallback)}. This
     * method is thread-safe.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void clearScheduleBackgroundDexoptJobCallback() {
        mInjector.getConfig().clearScheduleBackgroundDexoptJobCallback();
    }

    /**
     * Manually starts a background dexopt job. Does nothing if a job is already started by this
     * method or by the job scheduler. This method is not blocking.
     *
     * Unlike the job started by job scheduler, the job started by this method does not respect
     * constraints described in {@link #scheduleBackgroundDexoptJob()}, and hence will not be
     * cancelled when they aren't met.
     *
     * See {@link #dexoptPackages} for how to customize the behavior of the job.
     *
     * When the job ends (either completed or cancelled), the result is sent to the callbacks added
     * by {@link #addDexoptDoneCallback(Executor, DexoptDoneCallback)} with the
     * reason {@link ReasonMapping#REASON_BG_DEXOPT}.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void startBackgroundDexoptJob() {
        mInjector.getBackgroundDexoptJob().start();
    }

    /**
     * Same as above, but also returns a {@link CompletableFuture}.
     *
     * @hide
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @NonNull
    public CompletableFuture<BackgroundDexoptJob.Result> startBackgroundDexoptJobAndReturnFuture() {
        return mInjector.getBackgroundDexoptJob().start();
    }

    /**
     * Returns the running background dexopt job, or null of no background dexopt job is running.
     *
     * @hide
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Nullable
    public CompletableFuture<BackgroundDexoptJob.Result> getRunningBackgroundDexoptJob() {
        return mInjector.getBackgroundDexoptJob().get();
    }

    /**
     * Cancels the running background dexopt job started by the job scheduler or by {@link
     * #startBackgroundDexoptJob()}. Does nothing if the job is not running. This method is not
     * blocking.
     *
     * The result sent to the callbacks added by {@link
     * #addDexoptDoneCallback(Executor, DexoptDoneCallback)} will contain {@link
     * DexoptResult#DEXOPT_CANCELLED}.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void cancelBackgroundDexoptJob() {
        mInjector.getBackgroundDexoptJob().cancel();
    }

    /**
     * Adds a global listener that listens to any result of dexopting package(s), no matter run
     * manually or automatically. Calling this method multiple times with different callbacks is
     * allowed. Callbacks are executed in the same order as the one in which they were added. This
     * method is thread-safe.
     *
     * @param onlyIncludeUpdates if true, the results passed to the callback will only contain
     *         packages that have any update, and the callback won't be called with results that
     *         don't have any update.
     * @throws IllegalStateException if the same callback instance is already added
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void addDexoptDoneCallback(boolean onlyIncludeUpdates,
            @NonNull @CallbackExecutor Executor executor, @NonNull DexoptDoneCallback callback) {
        mInjector.getConfig().addDexoptDoneCallback(onlyIncludeUpdates, executor, callback);
    }

    /**
     * Removes the listener added by {@link
     * #addDexoptDoneCallback(Executor, DexoptDoneCallback)}. Does nothing if the
     * callback was not added. This method is thread-safe.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void removeDexoptDoneCallback(@NonNull DexoptDoneCallback callback) {
        mInjector.getConfig().removeDexoptDoneCallback(callback);
    }

    /**
     * Snapshots the profile of the given app split. The profile snapshot is the aggregation of all
     * existing profiles of the app split (all current user profiles and the reference profile).
     *
     * @param snapshot the snapshot from {@link PackageManagerLocal} to operate on
     * @param packageName the name of the app that owns the profile
     * @param splitName see {@link AndroidPackageSplit#getName()}
     * @return the file descriptor of the snapshot. It doesn't have any path associated with it. The
     *         caller is responsible for closing it. Note that the content may be empty.
     * @throws IllegalArgumentException if the package or the split is not found
     * @throws IllegalStateException if the operation encounters an error that should never happen
     *         (e.g., an internal logic error).
     * @throws SnapshotProfileException if the operation encounters an error that the caller should
     *         handle (e.g., an I/O error, a sub-process crash).
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @NonNull
    public ParcelFileDescriptor snapshotAppProfile(
            @NonNull PackageManagerLocal.FilteredSnapshot snapshot, @NonNull String packageName,
            @Nullable String splitName) throws SnapshotProfileException {
        var options = new MergeProfileOptions();
        options.forceMerge = true;
        return snapshotOrDumpAppProfile(snapshot, packageName, splitName, options);
    }

    /**
     * Same as above, but outputs in text format.
     *
     * @hide
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @NonNull
    public ParcelFileDescriptor dumpAppProfile(
            @NonNull PackageManagerLocal.FilteredSnapshot snapshot, @NonNull String packageName,
            @Nullable String splitName, boolean dumpClassesAndMethods)
            throws SnapshotProfileException {
        var options = new MergeProfileOptions();
        options.dumpOnly = !dumpClassesAndMethods;
        options.dumpClassesAndMethods = dumpClassesAndMethods;
        return snapshotOrDumpAppProfile(snapshot, packageName, splitName, options);
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @NonNull
    private ParcelFileDescriptor snapshotOrDumpAppProfile(
            @NonNull PackageManagerLocal.FilteredSnapshot snapshot, @NonNull String packageName,
            @Nullable String splitName, @NonNull MergeProfileOptions options)
            throws SnapshotProfileException {
        try (var pin = mInjector.createArtdPin()) {
            PackageState pkgState = Utils.getPackageStateOrThrow(snapshot, packageName);
            AndroidPackage pkg = Utils.getPackageOrThrow(pkgState);
            PrimaryDexInfo dexInfo = PrimaryDexUtils.getDexInfoBySplitName(pkg, splitName);

            List<ProfilePath> profiles = new ArrayList<>();

            InitProfileResult result = Utils.getOrInitReferenceProfile(mInjector.getArtd(),
                    dexInfo.dexPath(), PrimaryDexUtils.buildRefProfilePath(pkgState, dexInfo),
                    PrimaryDexUtils.getExternalProfiles(dexInfo),
                    PrimaryDexUtils.buildOutputProfile(pkgState, dexInfo, Process.SYSTEM_UID,
                            Process.SYSTEM_UID, false /* isPublic */));
            if (!result.externalProfileErrors().isEmpty()) {
                Log.e(TAG,
                        "Error occurred when initializing from external profiles: "
                                + result.externalProfileErrors());
            }

            ProfilePath refProfile = result.profile();

            if (refProfile != null) {
                profiles.add(refProfile);
            }

            profiles.addAll(
                    PrimaryDexUtils.getCurProfiles(mInjector.getUserManager(), pkgState, dexInfo));

            OutputProfile output = PrimaryDexUtils.buildOutputProfile(pkgState, dexInfo,
                    Process.SYSTEM_UID, Process.SYSTEM_UID, false /* isPublic */);

            try {
                return mergeProfilesAndGetFd(profiles, output, List.of(dexInfo.dexPath()), options);
            } finally {
                if (refProfile != null && refProfile.getTag() == ProfilePath.tmpProfilePath) {
                    mInjector.getArtd().deleteProfile(refProfile);
                }
            }
        } catch (RemoteException e) {
            throw new SnapshotProfileException(e);
        }
    }

    /**
     * Snapshots the boot image profile
     * (https://source.android.com/docs/core/bootloader/boot-image-profiles). The profile snapshot
     * is the aggregation of all existing profiles on the device (all current user profiles and
     * reference profiles) of all apps and the system server filtered by applicable classpaths.
     *
     * @param snapshot the snapshot from {@link PackageManagerLocal} to operate on
     * @return the file descriptor of the snapshot. It doesn't have any path associated with it. The
     *         caller is responsible for closing it. Note that the content may be empty.
     * @throws IllegalStateException if the operation encounters an error that should never happen
     *         (e.g., an internal logic error).
     * @throws SnapshotProfileException if the operation encounters an error that the caller should
     *         handle (e.g., an I/O error, a sub-process crash).
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @NonNull
    public ParcelFileDescriptor snapshotBootImageProfile(
            @NonNull PackageManagerLocal.FilteredSnapshot snapshot)
            throws SnapshotProfileException {
        if (!Constants.isBootImageProfilingEnabled()) {
            throw new SnapshotProfileException("Boot image profiling not enabled");
        }

        List<ProfilePath> profiles = new ArrayList<>();

        // System server profiles.
        profiles.add(AidlUtils.buildProfilePathForPrimaryRef(
                Utils.PLATFORM_PACKAGE_NAME, PrimaryDexUtils.PROFILE_PRIMARY));
        for (UserHandle handle :
                mInjector.getUserManager().getUserHandles(true /* excludeDying */)) {
            profiles.add(AidlUtils.buildProfilePathForPrimaryCur(handle.getIdentifier(),
                    Utils.PLATFORM_PACKAGE_NAME, PrimaryDexUtils.PROFILE_PRIMARY));
        }

        // App profiles.
        snapshot.getPackageStates().forEach((packageName, appPkgState) -> {
            // Hibernating apps can still provide useful profile contents, so skip the hibernation
            // check.
            if (Utils.canDexoptPackage(appPkgState, null /* appHibernationManager */)) {
                AndroidPackage appPkg = Utils.getPackageOrThrow(appPkgState);
                ProfileLists list = mInjector.getArtFileManager().getProfiles(appPkgState, appPkg,
                        false /* alsoForSecondaryDex */,
                        true /* excludeForObsoleteDexesAndLoaders */);
                profiles.addAll(list.allProfiles());
            }
        });

        OutputProfile output = AidlUtils.buildOutputProfileForPrimary(Utils.PLATFORM_PACKAGE_NAME,
                PrimaryDexUtils.PROFILE_PRIMARY, Process.SYSTEM_UID, Process.SYSTEM_UID,
                false /* isPublic */);

        List<String> dexPaths = Arrays.stream(CLASSPATHS_FOR_BOOT_IMAGE_PROFILE)
                                        .map(envVar -> Constants.getenv(envVar))
                                        .filter(classpath -> !TextUtils.isEmpty(classpath))
                                        .flatMap(classpath -> Arrays.stream(classpath.split(":")))
                                        .collect(Collectors.toList());

        var options = new MergeProfileOptions();
        options.forceMerge = true;
        options.forBootImage = true;

        try (var pin = mInjector.createArtdPin()) {
            return mergeProfilesAndGetFd(profiles, output, dexPaths, options);
        }
    }

    /**
     * Notifies ART Service that this is a boot that falls into one of the categories listed in
     * {@link BootReason}. The current behavior is that ART Service goes through all recently used
     * packages and dexopts those that are not dexopted. This might change in the future.
     *
     * This method is blocking. It takes about 30 seconds to a few minutes. During execution, {@code
     * progressCallback} is repeatedly called whenever there is an update on the progress.
     *
     * See {@link #dexoptPackages} for how to customize the behavior.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void onBoot(@NonNull @BootReason String bootReason,
            @Nullable @CallbackExecutor Executor progressCallbackExecutor,
            @Nullable Consumer<OperationProgress> progressCallback) {
        try (var snapshot = mInjector.getPackageManagerLocal().withFilteredSnapshot()) {
            dexoptPackages(snapshot, bootReason, new CancellationSignal(), progressCallbackExecutor,
                    progressCallback != null ? Map.of(ArtFlags.PASS_MAIN, progressCallback) : null);
        }
    }

    /**
     * Notifies ART Service that there are apexes staged for installation on next reboot (see
     * <a href="https://source.android.com/docs/core/ota/apex#apex-manager">the update sequence of
     * an APEX</a>). ART Service may use this to schedule a pre-reboot dexopt job. This might change
     * in the future.
     *
     * This immediately returns after scheduling the job and doesn't wait for the job to run.
     *
     * @param stagedApexModuleNames The <b>module names</b> of the staged apexes, corresponding to
     *         the directory beneath /apex, e.g., {@code com.android.art} (not the <b>package
     *         names</b>, e.g., {@code com.google.android.art}).
     */
    @SuppressLint("UnflaggedApi") // Flag support for mainline is not available.
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void onApexStaged(@NonNull String[] stagedApexModuleNames) {
        // TODO(b/311377497): Check system requirements.
        mInjector.getPreRebootDexoptJob().schedule();
    }

    /**
     * Dumps the dexopt state of all packages in text format for debugging purposes.
     *
     * There are no stability guarantees for the output format.
     *
     * @throws IllegalStateException if the operation encounters an error that should never happen
     *         (e.g., an internal logic error).
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void dump(
            @NonNull PrintWriter pw, @NonNull PackageManagerLocal.FilteredSnapshot snapshot) {
        try (var pin = mInjector.createArtdPin()) {
            new DumpHelper(this).dump(pw, snapshot);
        }
    }

    /**
     * Dumps the dexopt state of the given package in text format for debugging purposes.
     *
     * There are no stability guarantees for the output format.
     *
     * @throws IllegalArgumentException if the package is not found
     * @throws IllegalStateException if the operation encounters an error that should never happen
     *         (e.g., an internal logic error).
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void dumpPackage(@NonNull PrintWriter pw,
            @NonNull PackageManagerLocal.FilteredSnapshot snapshot, @NonNull String packageName) {
        try (var pin = mInjector.createArtdPin()) {
            new DumpHelper(this).dumpPackage(
                    pw, snapshot, Utils.getPackageStateOrThrow(snapshot, packageName));
        }
    }

    /**
     * Returns the statistics of the files managed by ART of a package.
     *
     * @throws IllegalArgumentException if the package is not found
     * @throws IllegalStateException if the operation encounters an error that should never happen
     *         (e.g., an internal logic error).
     */
    @SuppressLint("UnflaggedApi") // Flag support for mainline is not available.
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @NonNull
    public ArtManagedFileStats getArtManagedFileStats(
            @NonNull PackageManagerLocal.FilteredSnapshot snapshot, @NonNull String packageName) {
        PackageState pkgState = Utils.getPackageStateOrThrow(snapshot, packageName);
        AndroidPackage pkg = Utils.getPackageOrThrow(pkgState);

        try (var pin = mInjector.createArtdPin()) {
            long artifactsSize = 0;
            long refProfilesSize = 0;
            long curProfilesSize = 0;
            IArtd artd = mInjector.getArtd();

            UsableArtifactLists artifactLists =
                    mInjector.getArtFileManager().getUsableArtifacts(pkgState, pkg);
            for (ArtifactsPath artifacts : artifactLists.artifacts()) {
                artifactsSize += artd.getArtifactsSize(artifacts);
            }
            for (VdexPath vdexFile : artifactLists.vdexFiles()) {
                artifactsSize += artd.getVdexFileSize(vdexFile);
            }
            for (RuntimeArtifactsPath runtimeArtifacts : artifactLists.runtimeArtifacts()) {
                artifactsSize += artd.getRuntimeArtifactsSize(runtimeArtifacts);
            }

            ProfileLists profileLists = mInjector.getArtFileManager().getProfiles(pkgState, pkg,
                    true /* alsoForSecondaryDex */, true /* excludeForObsoleteDexesAndLoaders */);
            for (ProfilePath profile : profileLists.refProfiles()) {
                refProfilesSize += artd.getProfileSize(profile);
            }
            for (ProfilePath profile : profileLists.curProfiles()) {
                curProfilesSize += artd.getProfileSize(profile);
            }

            return new ArtManagedFileStats(artifactsSize, refProfilesSize, curProfilesSize);
        } catch (RemoteException e) {
            Utils.logArtdException(e);
            return new ArtManagedFileStats(
                    0 /* artifactsSize */, 0 /* refProfilesSize */, 0 /* curProfilesSize */);
        }
    }

    /**
     * Overrides the compiler filter of a package. The callback is called whenever a package is
     * going to be dexopted. This method is thread-safe.
     */
    @SuppressLint("UnflaggedApi") // Flag support for mainline is not available.
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void setAdjustCompilerFilterCallback(@NonNull @CallbackExecutor Executor executor,
            @NonNull AdjustCompilerFilterCallback callback) {
        mInjector.getConfig().setAdjustCompilerFilterCallback(executor, callback);
    }

    /**
     * Clears the callback set by {@link
     * #setAdjustCompilerFilterCallback(Executor, AdjustCompilerFilterCallback)}. This
     * method is thread-safe.
     */
    @SuppressLint("UnflaggedApi") // Flag support for mainline is not available.
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void clearAdjustCompilerFilterCallback() {
        mInjector.getConfig().clearAdjustCompilerFilterCallback();
    }

    /**
     * Cleans up obsolete profiles and artifacts.
     *
     * This is done in a mark-and-sweep approach.
     *
     * @return The amount of the disk space freed by the cleanup, in bytes.
     * @hide
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public long cleanup(@NonNull PackageManagerLocal.FilteredSnapshot snapshot) {
        try (var pin = mInjector.createArtdPin()) {
            mInjector.getDexUseManager().cleanup();

            // For every primary dex container file or secondary dex container file of every app, if
            // it has code, we keep the following types of files:
            // - The reference profile and the current profiles, regardless of the hibernation state
            //   of the app.
            // - The dexopt artifacts, if they are up-to-date and the app is not hibernating.
            // - Only the VDEX part of the dexopt artifacts, if the dexopt artifacts are outdated
            //   but the VDEX part is still usable and the app is not hibernating.
            // - The runtime artifacts, if dexopt artifacts are fully or partially usable and the
            //   usable parts don't contain AOT-compiled code. (This logic must be aligned with the
            //   one that determines when runtime images can be loaded in
            //   `OatFileManager::OpenDexFilesFromOat` in `art/runtime/oat_file_manager.cc`.)
            List<ProfilePath> profilesToKeep = new ArrayList<>();
            List<ArtifactsPath> artifactsToKeep = new ArrayList<>();
            List<VdexPath> vdexFilesToKeep = new ArrayList<>();
            List<RuntimeArtifactsPath> runtimeArtifactsToKeep = new ArrayList<>();

            for (PackageState pkgState : snapshot.getPackageStates().values()) {
                if (!Utils.canDexoptPackage(pkgState, null /* appHibernationManager */)) {
                    continue;
                }
                AndroidPackage pkg = Utils.getPackageOrThrow(pkgState);
                ProfileLists profileLists = mInjector.getArtFileManager().getProfiles(pkgState, pkg,
                        true /* alsoForSecondaryDex */,
                        true /* excludeForObsoleteDexesAndLoaders */);
                profilesToKeep.addAll(profileLists.allProfiles());
                if (!Utils.shouldSkipDexoptDueToHibernation(
                            pkgState, mInjector.getAppHibernationManager())) {
                    UsableArtifactLists artifactLists =
                            mInjector.getArtFileManager().getUsableArtifacts(pkgState, pkg);
                    artifactsToKeep.addAll(artifactLists.artifacts());
                    vdexFilesToKeep.addAll(artifactLists.vdexFiles());
                    runtimeArtifactsToKeep.addAll(artifactLists.runtimeArtifacts());
                }
            }
            return mInjector.getArtd().cleanup(
                    profilesToKeep, artifactsToKeep, vdexFilesToKeep, runtimeArtifactsToKeep);
        } catch (RemoteException e) {
            Utils.logArtdException(e);
            return 0;
        }
    }

    /**
     * Should be used by {@link BackgroundDexoptJobService} ONLY.
     *
     * @hide
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @NonNull
    BackgroundDexoptJob getBackgroundDexoptJob() {
        return mInjector.getBackgroundDexoptJob();
    }

    /**
     * Should be used by {@link BackgroundDexoptJobService} ONLY.
     *
     * @hide
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @NonNull
    PreRebootDexoptJob getPreRebootDexoptJob() {
        return mInjector.getPreRebootDexoptJob();
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Nullable
    private DexoptResult maybeDowngradePackages(
            @NonNull PackageManagerLocal.FilteredSnapshot snapshot,
            @NonNull Set<String> excludedPackages, @NonNull CancellationSignal cancellationSignal,
            @NonNull Executor executor,
            @Nullable @CallbackExecutor Executor progressCallbackExecutor,
            @Nullable Consumer<OperationProgress> progressCallback) {
        if (shouldDowngrade()) {
            List<String> packages = getDefaultPackages(snapshot, ReasonMapping.REASON_INACTIVE)
                                            .stream()
                                            .filter(pkg -> !excludedPackages.contains(pkg))
                                            .collect(Collectors.toList());
            if (!packages.isEmpty()) {
                Log.i(TAG, "Storage is low. Downgrading " + packages.size() + " inactive packages");
                DexoptParams params =
                        new DexoptParams.Builder(ReasonMapping.REASON_INACTIVE).build();
                return mInjector.getDexoptHelper().dexopt(snapshot, packages, params,
                        cancellationSignal, executor, progressCallbackExecutor, progressCallback);
            } else {
                Log.i(TAG,
                        "Storage is low, but downgrading is disabled or there's nothing to "
                                + "downgrade");
            }
        }
        return null;
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private boolean shouldDowngrade() {
        try {
            return mInjector.getStorageManager().getAllocatableBytes(StorageManager.UUID_DEFAULT)
                    < DOWNGRADE_THRESHOLD_ABOVE_LOW_BYTES;
        } catch (IOException e) {
            Log.e(TAG, "Failed to check storage. Assuming storage not low", e);
            return false;
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Nullable
    private DexoptResult maybeDexoptPackagesSupplementaryPass(
            @NonNull PackageManagerLocal.FilteredSnapshot snapshot,
            @NonNull DexoptResult mainResult, @NonNull DexoptParams mainParams,
            @NonNull CancellationSignal cancellationSignal, @NonNull Executor dexoptExecutor,
            @Nullable @CallbackExecutor Executor progressCallbackExecutor,
            @Nullable Consumer<OperationProgress> progressCallback) {
        if ((mainParams.getFlags() & ArtFlags.FLAG_FORCE_MERGE_PROFILE) != 0) {
            return null;
        }

        // Only pick packages that used a profile-guided filter and were skipped in the main pass.
        // This is a very coarse filter to reduce unnecessary iterations on a best-effort basis.
        // Packages included in the list may still be skipped by dexopter if the profiles don't have
        // any change.
        List<String> packageNames =
                mainResult.getPackageDexoptResults()
                        .stream()
                        .filter(packageResult
                                -> packageResult.getDexContainerFileDexoptResults()
                                           .stream()
                                           .anyMatch(fileResult
                                                   -> DexFile.isProfileGuidedCompilerFilter(
                                                              fileResult.getActualCompilerFilter())
                                                           && fileResult.getStatus()
                                                                   == DexoptResult.DEXOPT_SKIPPED))
                        .map(packageResult -> packageResult.getPackageName())
                        .collect(Collectors.toList());

        DexoptParams dexoptParams = mainParams.toBuilder()
                                            .setFlags(ArtFlags.FLAG_FORCE_MERGE_PROFILE,
                                                    ArtFlags.FLAG_FORCE_MERGE_PROFILE)
                                            .build();

        Log.i(TAG,
                "Dexopting " + packageNames.size() + " packages with reason="
                        + dexoptParams.getReason() + " (supplementary pass)");
        return mInjector.getDexoptHelper().dexopt(snapshot, packageNames, dexoptParams,
                cancellationSignal, dexoptExecutor, progressCallbackExecutor, progressCallback);
    }

    /** Returns the list of packages to process for the given reason. */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @NonNull
    private List<String> getDefaultPackages(@NonNull PackageManagerLocal.FilteredSnapshot snapshot,
            @NonNull /* @BatchDexoptReason|REASON_INACTIVE */ String reason) {
        var appHibernationManager = mInjector.getAppHibernationManager();

        // Filter out hibernating packages even if the reason is REASON_INACTIVE. This is because
        // artifacts for hibernating packages are already deleted.
        Stream<PackageState> packages = snapshot.getPackageStates().values().stream().filter(
                pkgState -> Utils.canDexoptPackage(pkgState, appHibernationManager));

        switch (reason) {
            case ReasonMapping.REASON_BOOT_AFTER_MAINLINE_UPDATE:
                packages = packages.filter(pkgState
                        -> mInjector.isSystemUiPackage(pkgState.getPackageName())
                                || mInjector.isLauncherPackage(pkgState.getPackageName()));
                break;
            case ReasonMapping.REASON_INACTIVE:
                packages = filterAndSortByLastActiveTime(
                        packages, false /* keepRecent */, false /* descending */);
                break;
            default:
                // Actually, the sorting is only needed for background dexopt, but we do it for all
                // cases for simplicity.
                packages = filterAndSortByLastActiveTime(
                        packages, true /* keepRecent */, true /* descending */);
        }

        return packages.map(PackageState::getPackageName).collect(Collectors.toList());
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @NonNull
    private Stream<PackageState> filterAndSortByLastActiveTime(
            @NonNull Stream<PackageState> packages, boolean keepRecent, boolean descending) {
        // "pm.dexopt.downgrade_after_inactive_days" is repurposed to also determine whether to
        // dexopt a package.
        long inactiveMs = TimeUnit.DAYS.toMillis(SystemProperties.getInt(
                "pm.dexopt.downgrade_after_inactive_days", Integer.MAX_VALUE /* def */));
        long currentTimeMs = mInjector.getCurrentTimeMillis();
        long thresholdTimeMs = currentTimeMs - inactiveMs;
        return packages
                .map(pkgState
                        -> Pair.create(pkgState,
                                Utils.getPackageLastActiveTime(pkgState,
                                        mInjector.getDexUseManager(), mInjector.getUserManager())))
                .filter(keepRecent ? (pair -> pair.second > thresholdTimeMs)
                                   : (pair -> pair.second <= thresholdTimeMs))
                .sorted(descending ? Comparator.comparingLong(pair -> - pair.second)
                                   : Comparator.comparingLong(pair -> pair.second))
                .map(pair -> pair.first);
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @NonNull
    private ParcelFileDescriptor mergeProfilesAndGetFd(@NonNull List<ProfilePath> profiles,
            @NonNull OutputProfile output, @NonNull List<String> dexPaths,
            @NonNull MergeProfileOptions options) throws SnapshotProfileException {
        try {
            boolean hasContent = false;
            try {
                hasContent = mInjector.getArtd().mergeProfiles(
                        profiles, null /* referenceProfile */, output, dexPaths, options);
            } catch (ServiceSpecificException e) {
                throw new SnapshotProfileException(e);
            }

            String path;
            Path emptyFile = null;
            if (hasContent) {
                path = output.profilePath.tmpPath;
            } else {
                // We cannot use /dev/null because `ParcelFileDescriptor` have an API `getStatSize`,
                // which expects the file to be a regular file or a link, and apps may call that
                // API.
                emptyFile =
                        Files.createTempFile(Paths.get(mInjector.getTempDir()), "empty", ".tmp");
                path = emptyFile.toString();
            }
            ParcelFileDescriptor fd;
            try {
                fd = ParcelFileDescriptor.open(new File(path), ParcelFileDescriptor.MODE_READ_ONLY);
            } catch (FileNotFoundException e) {
                throw new IllegalStateException(
                        String.format("Failed to open profile snapshot '%s'", path), e);
            }

            // The deletion is done on the open file so that only the FD keeps a reference to the
            // file.
            if (hasContent) {
                mInjector.getArtd().deleteProfile(ProfilePath.tmpProfilePath(output.profilePath));
            } else {
                Files.delete(emptyFile);
            }

            return fd;
        } catch (IOException | RemoteException e) {
            throw new SnapshotProfileException(e);
        }
    }

    /** @hide */
    @SystemApi(client = SystemApi.Client.SYSTEM_SERVER)
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public interface BatchDexoptStartCallback {
        /**
         * Mutates {@code builder} to override the default params for {@link #dexoptPackages}. It
         * must ignore unknown reasons because more reasons may be added in the future.
         *
         * This is called before the start of any automatic package dexopt (i.e., not
         * including package dexopt initiated by the {@link #dexoptPackage} API call).
         *
         * If {@code builder.setPackages} is not called, {@code defaultPackages} will be used as the
         * list of packages to dexopt.
         *
         * If {@code builder.setDexoptParams} is not called, the default params built from {@code
         * new DexoptParams.Builder(reason)} will to used as the params for dexopting each
         * package.
         *
         * Additionally, {@code cancellationSignal.cancel()} can be called to cancel this operation.
         * If this operation is initiated by the job scheduler and the {@code reason} is {@link
         * ReasonMapping#REASON_BG_DEXOPT}, the job will be retried with the default retry policy
         * (30 seconds, exponential, capped at 5hrs).
         *
         * Changing the reason is not allowed. Doing so will result in {@link IllegalStateException}
         * when {@link #dexoptPackages} is called.
         */
        void onBatchDexoptStart(@NonNull PackageManagerLocal.FilteredSnapshot snapshot,
                @NonNull @BatchDexoptReason String reason, @NonNull List<String> defaultPackages,
                @NonNull BatchDexoptParams.Builder builder,
                @NonNull CancellationSignal cancellationSignal);
    }

    /** @hide */
    @SystemApi(client = SystemApi.Client.SYSTEM_SERVER)
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public interface ScheduleBackgroundDexoptJobCallback {
        /**
         * Mutates {@code builder} to override the configuration of the background dexopt job.
         *
         * The default configuration described in {@link
         * ArtManagerLocal#scheduleBackgroundDexoptJob()} is passed to the callback as the {@code
         * builder} argument.
         *
         * Setting {@link JobInfo.Builder#setBackoffCriteria} is not allowed. Doing so will result
         * in {@link IllegalArgumentException} when {@link #scheduleBackgroundDexoptJob()} is
         * called. The job is retried with the default retry policy (30 seconds, exponential, capped
         * at 5hrs). Unfortunately, due to the limitation of the job scheduler API, this retry
         * policy cannot be changed.
         *
         * Setting {@link JobInfo.Builder#setRequiresStorageNotLow(boolean)} is not allowed. Doing
         * so will result in {@link IllegalStateException} when {@link
         * #scheduleBackgroundDexoptJob()} is called. ART Service has its own storage check, which
         * skips package dexopt when the storage is low. The storage check is enabled by
         * default for background dexopt jobs. {@link
         * #setBatchDexoptStartCallback(Executor, BatchDexoptStartCallback)} can be used to disable
         * the storage check by clearing the {@link ArtFlags#FLAG_SKIP_IF_STORAGE_LOW} flag.
         */
        void onOverrideJobInfo(@NonNull JobInfo.Builder builder);
    }

    /** @hide */
    @SystemApi(client = SystemApi.Client.SYSTEM_SERVER)
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public interface DexoptDoneCallback {
        void onDexoptDone(@NonNull DexoptResult result);
    }

    /** @hide */
    @SuppressLint("UnflaggedApi") // Flag support for mainline is not available.
    @SystemApi(client = SystemApi.Client.SYSTEM_SERVER)
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public interface AdjustCompilerFilterCallback {
        /**
         * Returns the adjusted compiler filter for the given package. If a package doesn't need
         * adjustment, this callback must return {@code originalCompilerFilter}. The callback must
         * be able to handle unknown {@code originalCompilerFilter} and unknown {@code reason}
         * because more compiler filters and reasons may be added in the future.
         *
         * The returned compiler filter overrides any compiler filter set by {@link
         * DexoptParams.Builder#setCompilerFilter}, no matter the dexopt is initiated by a
         * {@link #dexoptPackage} API call or any automatic batch dexopt (e.g., dexopt on boot and
         * background dexopt).
         *
         * This callback is useful for:
         * - Consistently overriding the compiler filter regardless of the dexopt initiator, for
         *   some performance-sensitive packages.
         * - Providing a compiler filter for specific packages during batch dexopt.
         *
         * The actual compiler filter to be used for dexopt will be determined in the following
         * order:
         *
         * 1. The default compiler filter for the given reason.
         * 2. The compiler filter set explicitly by {@link DexoptParams.Builder#setCompilerFilter}.
         * 3. ART Service's internal adjustments to upgrade the compiler filter, based on whether
         *    the package is System UI, etc.
         * 4. The adjustments made by this callback.
         * 5. ART Service's internal adjustments to downgrade the compiler filter, based on whether
         *    the profile is available, etc.
         *
         * @param packageName the name of the package to be dexopted
         * @param originalCompilerFilter the compiler filter before adjustment. This is the result
         *         of step 3 described above. It would be the input to step 5 described above if
         *         it wasn't for this callback.
         * @param reason the compilation reason of this dexopt operation. It is a string defined in
         *         {@link ReasonMapping} or a custom string passed to {@link
         *         DexoptParams.Builder#Builder(String)}
         *
         * @return the compiler filter after adjustment. This will be the input to step 5 described
         *         above
         */
        @SuppressLint("UnflaggedApi") // Flag support for mainline is not available.
        @NonNull
        String onAdjustCompilerFilter(@NonNull String packageName,
                @NonNull String originalCompilerFilter, @NonNull String reason);
    }

    /**
     * Represents an error that happens when snapshotting profiles.
     *
     * @hide
     */
    @SystemApi(client = SystemApi.Client.SYSTEM_SERVER)
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public static class SnapshotProfileException extends Exception {
        /** @hide */
        public SnapshotProfileException(@NonNull Throwable cause) {
            super(cause);
        }

        /** @hide */
        public SnapshotProfileException(@NonNull String message) {
            super(message);
        }
    }

    /**
     * Injector pattern for testing purpose.
     *
     * @hide
     */
    @VisibleForTesting
    public static class Injector {
        @Nullable private final ArtManagerLocal mArtManagerLocal;
        @Nullable private final Context mContext;
        @Nullable private final PackageManagerLocal mPackageManagerLocal;
        @Nullable private final Config mConfig;
        @Nullable private BackgroundDexoptJob mBgDexoptJob = null;
        @Nullable private PreRebootDexoptJob mPrDexoptJob = null;

        /** For compatibility with S and T. New code should not use this. */
        @Deprecated
        Injector() {
            mArtManagerLocal = null;
            mContext = null;
            mPackageManagerLocal = null;
            mConfig = null;
        }

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        Injector(@NonNull ArtManagerLocal artManagerLocal, @Nullable Context context) {
            // We only need them on Android U and above, where a context is passed.
            mArtManagerLocal = artManagerLocal;
            mContext = context;
            mPackageManagerLocal = Objects.requireNonNull(
                    LocalManagerRegistry.getManager(PackageManagerLocal.class));
            mConfig = new Config();

            // Call the getters for the dependencies that aren't optional, to ensure correct
            // initialization order.
            getDexoptHelper();
            getUserManager();
            getDexUseManager();
            getStorageManager();
            ArtModuleServiceInitializer.getArtModuleServiceManager();
        }

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        @NonNull
        public Context getContext() {
            return Objects.requireNonNull(mContext);
        }

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        @NonNull
        public PackageManagerLocal getPackageManagerLocal() {
            return Objects.requireNonNull(mPackageManagerLocal);
        }

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        @NonNull
        public IArtd getArtd() {
            return ArtdRefCache.getInstance().getArtd();
        }

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        @NonNull
        public ArtdRefCache.Pin createArtdPin() {
            return ArtdRefCache.getInstance().new Pin();
        }

        /** Returns a new {@link DexoptHelper} instance. */
        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        @NonNull
        public DexoptHelper getDexoptHelper() {
            return new DexoptHelper(getContext(), getConfig());
        }

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        @NonNull
        public Config getConfig() {
            return mConfig;
        }

        /** Returns the registered {@link AppHibernationManager} instance. */
        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        @NonNull
        public AppHibernationManager getAppHibernationManager() {
            return Objects.requireNonNull(mContext.getSystemService(AppHibernationManager.class));
        }

        /**
         * Returns the {@link BackgroundDexoptJob} instance.
         *
         * @throws RuntimeException if called during boot before the job scheduler service has
         *         started.
         */
        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        @NonNull
        public synchronized BackgroundDexoptJob getBackgroundDexoptJob() {
            if (mBgDexoptJob == null) {
                mBgDexoptJob = new BackgroundDexoptJob(mContext, mArtManagerLocal, mConfig);
            }
            return mBgDexoptJob;
        }

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        @NonNull
        public synchronized PreRebootDexoptJob getPreRebootDexoptJob() {
            if (mPrDexoptJob == null) {
                mPrDexoptJob = new PreRebootDexoptJob(mContext);
            }
            return mPrDexoptJob;
        }

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        @NonNull
        public UserManager getUserManager() {
            return Objects.requireNonNull(mContext.getSystemService(UserManager.class));
        }

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        @NonNull
        public DexUseManagerLocal getDexUseManager() {
            return Objects.requireNonNull(
                    LocalManagerRegistry.getManager(DexUseManagerLocal.class));
        }

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        public boolean isSystemUiPackage(@NonNull String packageName) {
            return Utils.isSystemUiPackage(mContext, packageName);
        }

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        public boolean isLauncherPackage(@NonNull String packageName) {
            return Utils.isLauncherPackage(mContext, packageName);
        }

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        public long getCurrentTimeMillis() {
            return System.currentTimeMillis();
        }

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        @NonNull
        public StorageManager getStorageManager() {
            return Objects.requireNonNull(mContext.getSystemService(StorageManager.class));
        }

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        @NonNull
        public String getTempDir() {
            // This is a path that system_server is known to have full access to.
            return "/data/system";
        }

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        @NonNull
        public ArtFileManager getArtFileManager() {
            return new ArtFileManager(getContext());
        }
    }
}
