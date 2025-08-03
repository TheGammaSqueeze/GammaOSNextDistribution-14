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

import static com.android.server.art.DexUseManagerLocal.CheckedSecondaryDexInfo;
import static com.android.server.art.DexUseManagerLocal.DexLoader;
import static com.android.server.art.model.DexoptStatus.DexContainerFileDexoptStatus;

import android.annotation.NonNull;
import android.os.Build;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.LocalManagerRegistry;
import com.android.server.pm.PackageManagerLocal;
import com.android.server.pm.pkg.PackageState;

import dalvik.system.VMRuntime;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A helper class to handle dump.
 *
 * @hide
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
public class DumpHelper {
    private static final String TAG = ArtManagerLocal.TAG;

    @NonNull private final Injector mInjector;

    public DumpHelper(@NonNull ArtManagerLocal artManagerLocal) {
        this(new Injector(artManagerLocal));
    }

    @VisibleForTesting
    public DumpHelper(@NonNull Injector injector) {
        mInjector = injector;
    }

    /** Handles {@link ArtManagerLocal#dump(PrintWriter, PackageManagerLocal.FilteredSnapshot)}. */
    public void dump(
            @NonNull PrintWriter pw, @NonNull PackageManagerLocal.FilteredSnapshot snapshot) {
        snapshot.getPackageStates()
                .values()
                .stream()
                .sorted(Comparator.comparing(PackageState::getPackageName))
                .forEach(pkgState -> dumpPackage(pw, snapshot, pkgState));
        pw.printf("\nCurrent GC: %s\n", ArtJni.getGarbageCollector());
    }

    /**
     * Handles {@link
     * ArtManagerLocal#dumpPackage(PrintWriter, PackageManagerLocal.FilteredSnapshot, String)}.
     */
    public void dumpPackage(@NonNull PrintWriter pw,
            @NonNull PackageManagerLocal.FilteredSnapshot snapshot,
            @NonNull PackageState pkgState) {
        // An APEX has a uid of -1.
        // TODO(b/256637152): Consider using `isApex` instead.
        if (pkgState.getAppId() <= 0 || pkgState.getAndroidPackage() == null) {
            return;
        }

        var ipw = new IndentingPrintWriter(pw);

        String packageName = pkgState.getPackageName();
        ipw.printf("[%s]\n", packageName);

        List<DexContainerFileDexoptStatus> statuses =
                mInjector.getArtManagerLocal()
                        .getDexoptStatus(snapshot, packageName)
                        .getDexContainerFileDexoptStatuses();
        Map<String, CheckedSecondaryDexInfo> secondaryDexInfoByDexPath =
                mInjector.getDexUseManager()
                        .getCheckedSecondaryDexInfo(
                                packageName, false /* excludeObsoleteDexesAndLoaders */)
                        .stream()
                        .collect(Collectors.toMap(
                                CheckedSecondaryDexInfo::dexPath, Function.identity()));

        // Use LinkedHashMap to keep the order. They are ordered by their split indexes.
        var primaryStatusesByDexPath =
                new LinkedHashMap<String, List<DexContainerFileDexoptStatus>>();
        // Use TreeMap to force lexicographical order.
        var secondaryStatusesByDexPath = new TreeMap<String, List<DexContainerFileDexoptStatus>>();
        for (DexContainerFileDexoptStatus fileStatus : statuses) {
            if (fileStatus.isPrimaryDex()) {
                primaryStatusesByDexPath
                        .computeIfAbsent(fileStatus.getDexContainerFile(), k -> new ArrayList<>())
                        .add(fileStatus);
            } else if (secondaryDexInfoByDexPath.containsKey(fileStatus.getDexContainerFile())) {
                // The condition above is false only if a change occurs between
                // `getDexoptStatus` and `getSecondaryDexInfo`, which is an edge case.
                secondaryStatusesByDexPath
                        .computeIfAbsent(fileStatus.getDexContainerFile(), k -> new ArrayList<>())
                        .add(fileStatus);
            }
        }

        ipw.increaseIndent();
        for (List<DexContainerFileDexoptStatus> fileStatuses : primaryStatusesByDexPath.values()) {
            dumpPrimaryDex(ipw, snapshot, fileStatuses, packageName);
        }
        if (!secondaryStatusesByDexPath.isEmpty()) {
            ipw.println("known secondary dex files:");
            ipw.increaseIndent();
            for (Map.Entry<String, List<DexContainerFileDexoptStatus>> entry :
                    secondaryStatusesByDexPath.entrySet()) {
                dumpSecondaryDex(ipw, snapshot, entry.getValue(), packageName,
                        secondaryDexInfoByDexPath.get(entry.getKey()));
            }
            ipw.decreaseIndent();
        }
        ipw.decreaseIndent();
    }

    private void dumpPrimaryDex(@NonNull IndentingPrintWriter ipw,
            @NonNull PackageManagerLocal.FilteredSnapshot snapshot,
            List<DexContainerFileDexoptStatus> fileStatuses, @NonNull String packageName) {
        String dexPath = fileStatuses.get(0).getDexContainerFile();
        ipw.printf("path: %s\n", dexPath);
        ipw.increaseIndent();
        dumpFileStatuses(ipw, fileStatuses);
        dumpUsedByOtherApps(ipw, snapshot,
                mInjector.getDexUseManager().getPrimaryDexLoaders(packageName, dexPath),
                packageName);
        ipw.decreaseIndent();
    }

    private void dumpSecondaryDex(@NonNull IndentingPrintWriter ipw,
            @NonNull PackageManagerLocal.FilteredSnapshot snapshot,
            List<DexContainerFileDexoptStatus> fileStatuses, @NonNull String packageName,
            @NonNull CheckedSecondaryDexInfo info) {
        String dexPath = fileStatuses.get(0).getDexContainerFile();
        @FileVisibility int visibility = info.fileVisibility();
        ipw.println(dexPath
                + (visibility == FileVisibility.NOT_FOUND
                                ? " (removed)"
                                : (visibility == FileVisibility.OTHER_READABLE ? " (public)"
                                                                               : "")));
        ipw.increaseIndent();
        dumpFileStatuses(ipw, fileStatuses);
        ipw.printf("class loader context: %s\n", info.displayClassLoaderContext());
        TreeMap<DexLoader, String> classLoaderContexts =
                info.loaders().stream().collect(Collectors.toMap(loader
                        -> loader,
                        loader
                        -> mInjector.getDexUseManager().getSecondaryClassLoaderContext(
                                packageName, dexPath, loader),
                        (a, b) -> a, TreeMap::new));
        // We should print all class loader contexts even if `info.displayClassLoaderContext()` is
        // not `VARYING_CLASS_LOADER_CONTEXTS`. This is because `info.displayClassLoaderContext()`
        // may show the only supported class loader context while other apps have unsupported ones.
        if (classLoaderContexts.values().stream().distinct().count() >= 2) {
            ipw.increaseIndent();
            for (var entry : classLoaderContexts.entrySet()) {
                // entry.getValue() may be null due to a race, but it's an edge case.
                ipw.printf("%s: %s\n", entry.getKey(), entry.getValue());
            }
            ipw.decreaseIndent();
        }
        dumpUsedByOtherApps(ipw, snapshot, info.loaders(), packageName);
        ipw.decreaseIndent();
    }

    private void dumpFileStatuses(
            @NonNull IndentingPrintWriter ipw, List<DexContainerFileDexoptStatus> fileStatuses) {
        for (DexContainerFileDexoptStatus fileStatus : fileStatuses) {
            ipw.printf("%s: [status=%s] [reason=%s]%s\n",
                    VMRuntime.getInstructionSet(fileStatus.getAbi()),
                    fileStatus.getCompilerFilter(), fileStatus.getCompilationReason(),
                    fileStatus.isPrimaryAbi() ? " [primary-abi]" : "");
            ipw.increaseIndent();
            ipw.printf("[location is %s]\n", fileStatus.getLocationDebugString());
            ipw.decreaseIndent();
        }
    }

    private void dumpUsedByOtherApps(@NonNull IndentingPrintWriter ipw,
            @NonNull PackageManagerLocal.FilteredSnapshot snapshot,
            @NonNull Set<DexLoader> dexLoaders, @NonNull String packageName) {
        List<DexLoader> otherApps =
                dexLoaders.stream()
                        .filter(loader -> DexUseManagerLocal.isLoaderOtherApp(loader, packageName))
                        .collect(Collectors.toList());
        if (!otherApps.isEmpty()) {
            ipw.printf("used by other apps: [%s]\n",
                    otherApps.stream()
                            .sorted()
                            .map(loader -> getLoaderState(snapshot, loader))
                            .collect(Collectors.joining(", ")));
        }
    }

    @NonNull
    private String getLoaderState(
            @NonNull PackageManagerLocal.FilteredSnapshot snapshot, @NonNull DexLoader loader) {
        var result = new StringBuilder(loader.toString());
        PackageState loadingPkgState = snapshot.getPackageState(loader.loadingPackageName());
        if (loadingPkgState == null) {
            // This can happen because the information held by DexUseManagerLocal can be outdated.
            // We don't want to clean up the entry at this point because we don't want the dump
            // operation to have an side effect.
            result.append(" (removed)");
            return result.toString();
        }
        Utils.Abi abi = Utils.getPrimaryAbi(loadingPkgState);
        result.append(String.format(" (isa=%s)", abi.isa()));
        return result.toString();
    }

    /** Injector pattern for testing purpose. */
    @VisibleForTesting
    public static class Injector {
        @NonNull private final ArtManagerLocal mArtManagerLocal;

        Injector(@NonNull ArtManagerLocal artManagerLocal) {
            mArtManagerLocal = artManagerLocal;
        }

        @NonNull
        public ArtManagerLocal getArtManagerLocal() {
            return mArtManagerLocal;
        }

        @NonNull
        public DexUseManagerLocal getDexUseManager() {
            return Objects.requireNonNull(
                    LocalManagerRegistry.getManager(DexUseManagerLocal.class));
        }
    }
}
