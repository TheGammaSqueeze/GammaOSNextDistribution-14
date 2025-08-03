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

import static com.android.server.art.ArtManagerLocal.DexoptDoneCallback;
import static com.android.server.art.model.Config.Callback;
import static com.android.server.art.model.DexoptResult.DexContainerFileDexoptResult;
import static com.android.server.art.model.DexoptResult.PackageDexoptResult;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.apphibernation.AppHibernationManager;
import android.content.Context;
import android.os.Binder;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.RemoteException;
import android.os.WorkSource;

import androidx.annotation.RequiresApi;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.art.model.ArtFlags;
import com.android.server.art.model.Config;
import com.android.server.art.model.DexoptParams;
import com.android.server.art.model.DexoptResult;
import com.android.server.art.model.OperationProgress;
import com.android.server.pm.PackageManagerLocal;
import com.android.server.pm.pkg.AndroidPackage;
import com.android.server.pm.pkg.PackageState;
import com.android.server.pm.pkg.SharedLibrary;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A helper class to handle dexopt.
 *
 * It talks to other components (e.g., PowerManager) and dispatches tasks to dexopters.
 *
 * @hide
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
public class DexoptHelper {
    private static final String TAG = ArtManagerLocal.TAG;

    @NonNull private final Injector mInjector;

    public DexoptHelper(@NonNull Context context, @NonNull Config config) {
        this(new Injector(context, config));
    }

    @VisibleForTesting
    public DexoptHelper(@NonNull Injector injector) {
        mInjector = injector;
    }

    /**
     * DO NOT use this method directly. Use {@link ArtManagerLocal#dexoptPackage} or {@link
     * ArtManagerLocal#dexoptPackages}.
     */
    @NonNull
    public DexoptResult dexopt(@NonNull PackageManagerLocal.FilteredSnapshot snapshot,
            @NonNull List<String> packageNames, @NonNull DexoptParams params,
            @NonNull CancellationSignal cancellationSignal, @NonNull Executor dexoptExecutor) {
        return dexopt(snapshot, packageNames, params, cancellationSignal, dexoptExecutor,
                null /* progressCallbackExecutor */, null /* progressCallback */);
    }

    /**
     * DO NOT use this method directly. Use {@link ArtManagerLocal#dexoptPackage} or {@link
     * ArtManagerLocal#dexoptPackages}.
     */
    @NonNull
    public DexoptResult dexopt(@NonNull PackageManagerLocal.FilteredSnapshot snapshot,
            @NonNull List<String> packageNames, @NonNull DexoptParams params,
            @NonNull CancellationSignal cancellationSignal, @NonNull Executor dexoptExecutor,
            @Nullable Executor progressCallbackExecutor,
            @Nullable Consumer<OperationProgress> progressCallback) {
        return dexoptPackages(
                getPackageStates(snapshot, packageNames,
                        (params.getFlags() & ArtFlags.FLAG_SHOULD_INCLUDE_DEPENDENCIES) != 0),
                params, cancellationSignal, dexoptExecutor, progressCallbackExecutor,
                progressCallback);
    }

    /**
     * DO NOT use this method directly. Use {@link ArtManagerLocal#dexoptPackage} or {@link
     * ArtManagerLocal#dexoptPackages}.
     */
    @NonNull
    private DexoptResult dexoptPackages(@NonNull List<PackageState> pkgStates,
            @NonNull DexoptParams params, @NonNull CancellationSignal cancellationSignal,
            @NonNull Executor dexoptExecutor, @Nullable Executor progressCallbackExecutor,
            @Nullable Consumer<OperationProgress> progressCallback) {
        // TODO(jiakaiz): Find out whether this is still needed.
        long identityToken = Binder.clearCallingIdentity();

        try {
            List<CompletableFuture<PackageDexoptResult>> futures = new ArrayList<>();

            // Child threads will set their own listeners on the cancellation signal, so we must
            // create a separate cancellation signal for each of them so that the listeners don't
            // overwrite each other.
            List<CancellationSignal> childCancellationSignals =
                    pkgStates.stream()
                            .map(pkgState -> new CancellationSignal())
                            .collect(Collectors.toList());
            cancellationSignal.setOnCancelListener(() -> {
                for (CancellationSignal childCancellationSignal : childCancellationSignals) {
                    childCancellationSignal.cancel();
                }
            });

            for (int i = 0; i < pkgStates.size(); i++) {
                PackageState pkgState = pkgStates.get(i);
                CancellationSignal childCancellationSignal = childCancellationSignals.get(i);
                futures.add(CompletableFuture.supplyAsync(() -> {
                    return dexoptPackage(pkgState, params, childCancellationSignal);
                }, dexoptExecutor));
            }

            if (progressCallback != null) {
                CompletableFuture.runAsync(() -> {
                    progressCallback.accept(
                            OperationProgress.create(0 /* current */, futures.size()));
                }, progressCallbackExecutor);
                AtomicInteger current = new AtomicInteger(0);
                for (CompletableFuture<PackageDexoptResult> future : futures) {
                    future.thenRunAsync(() -> {
                        progressCallback.accept(OperationProgress.create(
                                current.incrementAndGet(), futures.size()));
                    }, progressCallbackExecutor);
                }
            }

            List<PackageDexoptResult> results =
                    futures.stream().map(Utils::getFuture).collect(Collectors.toList());

            var result =
                    DexoptResult.create(params.getCompilerFilter(), params.getReason(), results);

            for (Callback<DexoptDoneCallback, Boolean> doneCallback :
                    mInjector.getConfig().getDexoptDoneCallbacks()) {
                boolean onlyIncludeUpdates = doneCallback.extra();
                if (onlyIncludeUpdates) {
                    List<PackageDexoptResult> filteredResults =
                            results.stream()
                                    .filter(PackageDexoptResult::hasUpdatedArtifacts)
                                    .collect(Collectors.toList());
                    if (!filteredResults.isEmpty()) {
                        var resultForCallback = DexoptResult.create(
                                params.getCompilerFilter(), params.getReason(), filteredResults);
                        CompletableFuture.runAsync(() -> {
                            doneCallback.get().onDexoptDone(resultForCallback);
                        }, doneCallback.executor());
                    }
                } else {
                    CompletableFuture.runAsync(() -> {
                        doneCallback.get().onDexoptDone(result);
                    }, doneCallback.executor());
                }
            }

            return result;
        } finally {
            Binder.restoreCallingIdentity(identityToken);
            // Make sure nothing leaks even if the caller holds `cancellationSignal` forever.
            cancellationSignal.setOnCancelListener(null);
        }
    }

    /**
     * DO NOT use this method directly. Use {@link ArtManagerLocal#dexoptPackage} or {@link
     * ArtManagerLocal#dexoptPackages}.
     */
    @NonNull
    private PackageDexoptResult dexoptPackage(@NonNull PackageState pkgState,
            @NonNull DexoptParams params, @NonNull CancellationSignal cancellationSignal) {
        List<DexContainerFileDexoptResult> results = new ArrayList<>();
        Function<Integer, PackageDexoptResult> createResult = (packageLevelStatus)
                -> PackageDexoptResult.create(
                        pkgState.getPackageName(), results, packageLevelStatus);

        AndroidPackage pkg = Utils.getPackageOrThrow(pkgState);

        if (!canDexoptPackage(pkgState)) {
            return createResult.apply(null /* packageLevelStatus */);
        }

        if ((params.getFlags() & ArtFlags.FLAG_FOR_SINGLE_SPLIT) != 0) {
            // Throws if the split is not found.
            PrimaryDexUtils.getDexInfoBySplitName(pkg, params.getSplitName());
        }

        try (var tracing = new Utils.Tracing("dexopt")) {
            if ((params.getFlags() & ArtFlags.FLAG_FOR_PRIMARY_DEX) != 0) {
                if (cancellationSignal.isCanceled()) {
                    return createResult.apply(DexoptResult.DEXOPT_CANCELLED);
                }

                results.addAll(
                        mInjector.getPrimaryDexopter(pkgState, pkg, params, cancellationSignal)
                                .dexopt());
            }

            if ((params.getFlags() & ArtFlags.FLAG_FOR_SECONDARY_DEX) != 0) {
                if (cancellationSignal.isCanceled()) {
                    return createResult.apply(DexoptResult.DEXOPT_CANCELLED);
                }

                results.addAll(
                        mInjector.getSecondaryDexopter(pkgState, pkg, params, cancellationSignal)
                                .dexopt());
            }
        } catch (RemoteException e) {
            Utils.logArtdException(e);
            return createResult.apply(DexoptResult.DEXOPT_FAILED);
        }

        return createResult.apply(null /* packageLevelStatus */);
    }

    private boolean canDexoptPackage(@NonNull PackageState pkgState) {
        // getAppHibernationManager may return null here during boot time compilation, which will
        // make this function return true incorrectly for packages that shouldn't be dexopted due to
        // hibernation. Further discussion in comments in ArtManagerLocal.getDefaultPackages.
        return Utils.canDexoptPackage(pkgState, mInjector.getAppHibernationManager());
    }

    @NonNull
    private List<PackageState> getPackageStates(
            @NonNull PackageManagerLocal.FilteredSnapshot snapshot,
            @NonNull List<String> packageNames, boolean includeDependencies) {
        var pkgStates = new LinkedHashMap<String, PackageState>();
        Set<String> visitedLibraries = new HashSet<>();
        Queue<SharedLibrary> queue = new LinkedList<>();

        Consumer<SharedLibrary> maybeEnqueue = library -> {
            // The package name is not null if the library is an APK.
            // TODO(jiakaiz): Support JAR libraries.
            if (library.getPackageName() != null && !library.isNative()
                    && !visitedLibraries.contains(library.getName())) {
                visitedLibraries.add(library.getName());
                queue.add(library);
            }
        };

        for (String packageName : packageNames) {
            PackageState pkgState = Utils.getPackageStateOrThrow(snapshot, packageName);
            Utils.getPackageOrThrow(pkgState);
            pkgStates.put(packageName, pkgState);
            if (includeDependencies && canDexoptPackage(pkgState)) {
                for (SharedLibrary library : pkgState.getSharedLibraryDependencies()) {
                    maybeEnqueue.accept(library);
                }
            }
        }

        SharedLibrary library;
        while ((library = queue.poll()) != null) {
            String packageName = library.getPackageName();
            PackageState pkgState = Utils.getPackageStateOrThrow(snapshot, packageName);
            if (canDexoptPackage(pkgState)) {
                pkgStates.put(packageName, pkgState);

                // Note that `library.getDependencies()` is different from
                // `pkgState.getUsesLibraries()`. Different libraries can belong to the same
                // package. `pkgState.getUsesLibraries()` returns a union of dependencies of
                // libraries that belong to the same package, which is not what we want here.
                // Therefore, this loop cannot be unified with the one above.
                for (SharedLibrary dep : library.getDependencies()) {
                    maybeEnqueue.accept(dep);
                }
            }
        }

        // `LinkedHashMap` guarantees deterministic order.
        return new ArrayList<>(pkgStates.values());
    }

    /**
     * Injector pattern for testing purpose.
     *
     * @hide
     */
    @VisibleForTesting
    public static class Injector {
        @NonNull private final Context mContext;
        @NonNull private final Config mConfig;

        Injector(@NonNull Context context, @NonNull Config config) {
            mContext = context;
            mConfig = config;

            // Call the getters for the dependencies that aren't optional, to ensure correct
            // initialization order.
            getAppHibernationManager();
        }

        @NonNull
        PrimaryDexopter getPrimaryDexopter(@NonNull PackageState pkgState,
                @NonNull AndroidPackage pkg, @NonNull DexoptParams params,
                @NonNull CancellationSignal cancellationSignal) {
            return new PrimaryDexopter(
                    mContext, mConfig, pkgState, pkg, params, cancellationSignal);
        }

        @NonNull
        SecondaryDexopter getSecondaryDexopter(@NonNull PackageState pkgState,
                @NonNull AndroidPackage pkg, @NonNull DexoptParams params,
                @NonNull CancellationSignal cancellationSignal) {
            return new SecondaryDexopter(
                    mContext, mConfig, pkgState, pkg, params, cancellationSignal);
        }

        @NonNull
        public AppHibernationManager getAppHibernationManager() {
            return Objects.requireNonNull(mContext.getSystemService(AppHibernationManager.class));
        }

        @NonNull
        public Config getConfig() {
            return mConfig;
        }
    }
}
