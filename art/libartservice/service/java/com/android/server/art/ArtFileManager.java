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

package com.android.server.art;

import static com.android.server.art.DexUseManagerLocal.SecondaryDexInfo;
import static com.android.server.art.PrimaryDexUtils.DetailedPrimaryDexInfo;
import static com.android.server.art.PrimaryDexUtils.PrimaryDexInfo;
import static com.android.server.art.Utils.Abi;

import android.annotation.NonNull;
import android.content.Context;
import android.os.Binder;
import android.os.Build;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.RequiresApi;

import com.android.internal.annotations.Immutable;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.LocalManagerRegistry;
import com.android.server.art.model.DetailedDexInfo;
import com.android.server.pm.pkg.AndroidPackage;
import com.android.server.pm.pkg.PackageState;

import dalvik.system.DexFile;

import com.google.auto.value.AutoValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A helper class to list files that ART Service consumes or produces.
 *
 * @hide
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
public class ArtFileManager {
    private static final String TAG = ArtManagerLocal.TAG;

    @NonNull private final Injector mInjector;

    public ArtFileManager(@NonNull Context context) {
        this(new Injector(context));
    }

    @VisibleForTesting
    public ArtFileManager(@NonNull Injector injector) {
        mInjector = injector;
    }

    /**
     * @param excludeObsoleteDexesAndLoaders If true, excludes secondary dex files and loaders based
     *         on file visibility. See details in {@link
     *         DexUseManagerLocal#getCheckedSecondaryDexInfo}.
     */
    @NonNull
    public List<Pair<DetailedDexInfo, Abi>> getDexAndAbis(@NonNull PackageState pkgState,
            @NonNull AndroidPackage pkg, boolean forPrimaryDex, boolean forSecondaryDex,
            boolean excludeObsoleteDexesAndLoaders) {
        List<Pair<DetailedDexInfo, Abi>> dexAndAbis = new ArrayList<>();
        if (forPrimaryDex) {
            for (DetailedPrimaryDexInfo dexInfo :
                    PrimaryDexUtils.getDetailedDexInfo(pkgState, pkg)) {
                for (Abi abi : Utils.getAllAbis(pkgState)) {
                    dexAndAbis.add(Pair.create(dexInfo, abi));
                }
            }
        }
        if (forSecondaryDex) {
            List<? extends SecondaryDexInfo> dexInfos = excludeObsoleteDexesAndLoaders
                    ? mInjector.getDexUseManager().getCheckedSecondaryDexInfo(
                            pkgState.getPackageName(), true /* excludeObsoleteDexesAndLoaders */)
                    : mInjector.getDexUseManager().getSecondaryDexInfo(pkgState.getPackageName());
            for (SecondaryDexInfo dexInfo : dexInfos) {
                if (!mInjector.isSystemOrRootOrShell()
                        && !mInjector.getCallingUserHandle().equals(dexInfo.userHandle())) {
                    continue;
                }
                for (Abi abi : Utils.getAllAbisForNames(dexInfo.abiNames(), pkgState)) {
                    dexAndAbis.add(Pair.create(dexInfo, abi));
                }
            }
        }
        return dexAndAbis;
    }

    /**
     * Returns the writable paths of artifacts, regardless of whether the artifacts exist or
     * whether they are usable.
     */
    @NonNull
    public WritableArtifactLists getWritableArtifacts(
            @NonNull PackageState pkgState, @NonNull AndroidPackage pkg) throws RemoteException {
        List<ArtifactsPath> artifacts = new ArrayList<>();
        List<RuntimeArtifactsPath> runtimeArtifacts = new ArrayList<>();

        boolean isInDalvikCache = Utils.isInDalvikCache(pkgState, mInjector.getArtd());
        for (PrimaryDexInfo dexInfo : PrimaryDexUtils.getDexInfo(pkg)) {
            for (Abi abi : Utils.getAllAbis(pkgState)) {
                artifacts.add(AidlUtils.buildArtifactsPath(
                        dexInfo.dexPath(), abi.isa(), isInDalvikCache));
                // Runtime images are only generated for primary dex files.
                runtimeArtifacts.add(AidlUtils.buildRuntimeArtifactsPath(
                        pkgState.getPackageName(), dexInfo.dexPath(), abi.isa()));
            }
        }

        for (SecondaryDexInfo dexInfo :
                mInjector.getDexUseManager().getSecondaryDexInfo(pkgState.getPackageName())) {
            for (Abi abi : Utils.getAllAbisForNames(dexInfo.abiNames(), pkgState)) {
                artifacts.add(AidlUtils.buildArtifactsPath(
                        dexInfo.dexPath(), abi.isa(), false /* isInDalvikCache */));
            }
        }

        return WritableArtifactLists.create(artifacts, runtimeArtifacts);
    }

    /** Returns artifacts that are usable, regardless of whether they are writable. */
    @NonNull
    public UsableArtifactLists getUsableArtifacts(
            @NonNull PackageState pkgState, @NonNull AndroidPackage pkg) throws RemoteException {
        List<ArtifactsPath> artifacts = new ArrayList<>();
        List<VdexPath> vdexFiles = new ArrayList<>();
        List<RuntimeArtifactsPath> runtimeArtifacts = new ArrayList<>();

        for (Pair<DetailedDexInfo, Abi> pair :
                getDexAndAbis(pkgState, pkg, true /* forPrimaryDex */, true /* forSecondaryDex */,
                        true /* excludeObsoleteDexesAndLoaders */)) {
            DetailedDexInfo dexInfo = pair.first;
            Abi abi = pair.second;
            try {
                GetDexoptStatusResult result = mInjector.getArtd().getDexoptStatus(
                        dexInfo.dexPath(), abi.isa(), dexInfo.classLoaderContext());
                if (result.artifactsLocation == ArtifactsLocation.DALVIK_CACHE
                        || result.artifactsLocation == ArtifactsLocation.NEXT_TO_DEX) {
                    ArtifactsPath thisArtifacts = AidlUtils.buildArtifactsPath(dexInfo.dexPath(),
                            abi.isa(), result.artifactsLocation == ArtifactsLocation.DALVIK_CACHE);
                    if (result.compilationReason.equals(ArtConstants.REASON_VDEX)) {
                        // Only the VDEX file is usable.
                        vdexFiles.add(VdexPath.artifactsPath(thisArtifacts));
                    } else {
                        artifacts.add(thisArtifacts);
                    }
                    // Runtime images are only generated for primary dex files.
                    if (dexInfo instanceof DetailedPrimaryDexInfo
                            && !DexFile.isOptimizedCompilerFilter(result.compilerFilter)) {
                        runtimeArtifacts.add(AidlUtils.buildRuntimeArtifactsPath(
                                pkgState.getPackageName(), dexInfo.dexPath(), abi.isa()));
                    }
                }
            } catch (ServiceSpecificException e) {
                Log.e(TAG,
                        String.format(
                                "Failed to get dexopt status [packageName = %s, dexPath = %s, "
                                        + "isa = %s, classLoaderContext = %s]",
                                pkgState.getPackageName(), dexInfo.dexPath(), abi.isa(),
                                dexInfo.classLoaderContext()),
                        e);
            }
        }

        return UsableArtifactLists.create(artifacts, vdexFiles, runtimeArtifacts);
    }

    /**
     * @param excludeForObsoleteDexesAndLoaders If true, excludes profiles for secondary dex files
     *         and loaders based on file visibility. See details in {@link
     *         DexUseManagerLocal#getCheckedSecondaryDexInfo}.
     */
    @NonNull
    public ProfileLists getProfiles(@NonNull PackageState pkgState, @NonNull AndroidPackage pkg,
            boolean alsoForSecondaryDex, boolean excludeForObsoleteDexesAndLoaders) {
        List<ProfilePath> refProfiles = new ArrayList<>();
        List<ProfilePath> curProfiles = new ArrayList<>();

        for (PrimaryDexInfo dexInfo : PrimaryDexUtils.getDexInfo(pkg)) {
            refProfiles.add(PrimaryDexUtils.buildRefProfilePath(pkgState, dexInfo));
            curProfiles.addAll(mInjector.isSystemOrRootOrShell()
                            ? PrimaryDexUtils.getCurProfiles(
                                    mInjector.getUserManager(), pkgState, dexInfo)
                            : PrimaryDexUtils.getCurProfiles(
                                    List.of(mInjector.getCallingUserHandle()), pkgState, dexInfo));
        }
        if (alsoForSecondaryDex) {
            List<? extends SecondaryDexInfo> dexInfos = excludeForObsoleteDexesAndLoaders
                    ? mInjector.getDexUseManager().getCheckedSecondaryDexInfo(
                            pkgState.getPackageName(), true /* excludeForObsoleteDexesAndLoaders */)
                    : mInjector.getDexUseManager().getSecondaryDexInfo(pkgState.getPackageName());
            for (SecondaryDexInfo dexInfo : dexInfos) {
                if (!mInjector.isSystemOrRootOrShell()
                        && !mInjector.getCallingUserHandle().equals(dexInfo.userHandle())) {
                    continue;
                }
                refProfiles.add(AidlUtils.buildProfilePathForSecondaryRef(dexInfo.dexPath()));
                curProfiles.add(AidlUtils.buildProfilePathForSecondaryCur(dexInfo.dexPath()));
            }
        }

        return ProfileLists.create(refProfiles, curProfiles);
    }

    @Immutable
    @AutoValue
    @SuppressWarnings("AutoValueImmutableFields") // Can't use ImmutableList because it's in Guava.
    public abstract static class WritableArtifactLists {
        protected WritableArtifactLists() {}

        public static @NonNull WritableArtifactLists create(@NonNull List<ArtifactsPath> artifacts,
                @NonNull List<RuntimeArtifactsPath> runtimeArtifacts) {
            return new AutoValue_ArtFileManager_WritableArtifactLists(
                    Collections.unmodifiableList(artifacts),
                    Collections.unmodifiableList(runtimeArtifacts));
        }

        public abstract @NonNull List<ArtifactsPath> artifacts();
        public abstract @NonNull List<RuntimeArtifactsPath> runtimeArtifacts();
    }

    @Immutable
    @AutoValue
    @SuppressWarnings("AutoValueImmutableFields") // Can't use ImmutableList because it's in Guava.
    public abstract static class UsableArtifactLists {
        protected UsableArtifactLists() {}

        public static @NonNull UsableArtifactLists create(@NonNull List<ArtifactsPath> artifacts,
                @NonNull List<VdexPath> vdexFiles,
                @NonNull List<RuntimeArtifactsPath> runtimeArtifacts) {
            return new AutoValue_ArtFileManager_UsableArtifactLists(
                    Collections.unmodifiableList(artifacts),
                    Collections.unmodifiableList(vdexFiles),
                    Collections.unmodifiableList(runtimeArtifacts));
        }

        public abstract @NonNull List<ArtifactsPath> artifacts();
        public abstract @NonNull List<VdexPath> vdexFiles();

        // Those not added to the list are definitely unusable, but those added to the list are not
        // necessarily usable. For example, runtime artifacts can be outdated when the corresponding
        // dex file is updated, but they may still show up in this list.
        //
        // However, this is not a severe problem. For `ArtManagerLocal.cleanup`, the worst result is
        // only that we are keeping more runtime artifacts than needed. For
        // `ArtManagerLocal.getArtManagedFileStats`, this is an edge case because the API call is
        // transitively initiated by the app itself, and the runtime refreshes unusable runtime
        // artifacts as soon as the app starts.
        //
        // TODO(jiakaiz): Improve this.
        public abstract @NonNull List<RuntimeArtifactsPath> runtimeArtifacts();
    }

    @Immutable
    @AutoValue
    @SuppressWarnings("AutoValueImmutableFields") // Can't use ImmutableList because it's in Guava.
    public abstract static class ProfileLists {
        protected ProfileLists() {}

        public static @NonNull ProfileLists create(
                @NonNull List<ProfilePath> refProfiles, @NonNull List<ProfilePath> curProfiles) {
            return new AutoValue_ArtFileManager_ProfileLists(
                    Collections.unmodifiableList(refProfiles),
                    Collections.unmodifiableList(curProfiles));
        }

        public abstract @NonNull List<ProfilePath> refProfiles();
        public abstract @NonNull List<ProfilePath> curProfiles();

        public @NonNull List<ProfilePath> allProfiles() {
            List<ProfilePath> profiles = new ArrayList<>();
            profiles.addAll(refProfiles());
            profiles.addAll(curProfiles());
            return profiles;
        }
    }

    /**Injector pattern for testing purpose. */
    @VisibleForTesting
    public static class Injector {
        @NonNull private final Context mContext;

        Injector(@NonNull Context context) {
            mContext = context;

            // Call the getters for the dependencies that aren't optional, to ensure correct
            // initialization order.
            ArtModuleServiceInitializer.getArtModuleServiceManager();
            getUserManager();
            getDexUseManager();
        }

        @NonNull
        public IArtd getArtd() {
            return ArtdRefCache.getInstance().getArtd();
        }

        @NonNull
        public UserManager getUserManager() {
            return Objects.requireNonNull(mContext.getSystemService(UserManager.class));
        }

        @NonNull
        public DexUseManagerLocal getDexUseManager() {
            return Objects.requireNonNull(
                    LocalManagerRegistry.getManager(DexUseManagerLocal.class));
        }

        public boolean isSystemOrRootOrShell() {
            // At the time of writing, this is only typically true unless called by an app through
            // {@link ArtManagerLocal#getArtManagedFileStats}.
            return Utils.isSystemOrRootOrShell();
        }

        @NonNull
        public UserHandle getCallingUserHandle() {
            return Binder.getCallingUserHandle();
        }
    }
}
