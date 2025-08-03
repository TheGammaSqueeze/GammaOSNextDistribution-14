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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Build;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;

import androidx.annotation.RequiresApi;

import com.android.internal.annotations.Immutable;
import com.android.server.art.model.DetailedDexInfo;
import com.android.server.pm.pkg.AndroidPackage;
import com.android.server.pm.pkg.AndroidPackageSplit;
import com.android.server.pm.pkg.PackageState;
import com.android.server.pm.pkg.PackageUserState;
import com.android.server.pm.pkg.SharedLibrary;

import dalvik.system.DelegateLastClassLoader;
import dalvik.system.DexClassLoader;
import dalvik.system.PathClassLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** @hide */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
public class PrimaryDexUtils {
    public static final String PROFILE_PRIMARY = "primary";
    private static final String SHARED_LIBRARY_LOADER_TYPE = PathClassLoader.class.getName();

    /**
     * Returns the basic information about all primary dex files belonging to the package, excluding
     * files that have no code.
     */
    @NonNull
    public static List<PrimaryDexInfo> getDexInfo(@NonNull AndroidPackage pkg) {
        return getDexInfoImpl(pkg)
                .stream()
                .map(builder -> builder.build())
                .filter(info -> info.hasCode())
                .collect(Collectors.toList());
    }

    /**
     * Same as above, but requires {@link PackageState} in addition, and returns the detailed
     * information, including the class loader context.
     */
    @NonNull
    public static List<DetailedPrimaryDexInfo> getDetailedDexInfo(
            @NonNull PackageState pkgState, @NonNull AndroidPackage pkg) {
        return getDetailedDexInfoImpl(pkgState, pkg)
                .stream()
                .map(builder -> builder.buildDetailed())
                .filter(info -> info.hasCode())
                .collect(Collectors.toList());
    }

    /** Returns the basic information about a dex file specified by {@code splitName}. */
    @NonNull
    public static PrimaryDexInfo getDexInfoBySplitName(
            @NonNull AndroidPackage pkg, @Nullable String splitName) {
        PrimaryDexInfo dexInfo =
                findDexInfo(pkg, info -> Objects.equals(info.splitName(), splitName));
        if (dexInfo == null) {
            throw new IllegalArgumentException(String.format("Split '%s' not found", splitName));
        }
        return dexInfo;
    }

    /**
     * Returns the basic information about the first dex file matching {@code predicate}, or null if
     * not found.
     */
    @Nullable
    public static PrimaryDexInfo findDexInfo(
            @NonNull AndroidPackage pkg, Predicate<PrimaryDexInfo> predicate) {
        return getDexInfoImpl(pkg)
                .stream()
                .map(builder -> builder.build())
                .filter(predicate)
                .findFirst()
                .orElse(null);
    }

    @NonNull
    private static List<PrimaryDexInfoBuilder> getDexInfoImpl(@NonNull AndroidPackage pkg) {
        List<PrimaryDexInfoBuilder> dexInfos = new ArrayList<>();

        for (var split : pkg.getSplits()) {
            dexInfos.add(new PrimaryDexInfoBuilder(split));
        }

        return dexInfos;
    }

    @NonNull
    private static List<PrimaryDexInfoBuilder> getDetailedDexInfoImpl(
            @NonNull PackageState pkgState, @NonNull AndroidPackage pkg) {
        List<PrimaryDexInfoBuilder> dexInfos = getDexInfoImpl(pkg);

        PrimaryDexInfoBuilder baseApk = dexInfos.get(0);
        baseApk.mClassLoaderName = baseApk.mSplit.getClassLoaderName();
        File baseDexFile = new File(baseApk.mSplit.getPath());
        baseApk.mRelativeDexPath = baseDexFile.getName();

        // Shared libraries are the dependencies of the base APK.
        baseApk.mSharedLibrariesContext =
                encodeSharedLibraries(pkgState.getSharedLibraryDependencies());

        boolean isIsolatedSplitLoading = isIsolatedSplitLoading(pkg);

        for (int i = 1; i < dexInfos.size(); i++) {
            var dexInfoBuilder = dexInfos.get(i);
            File splitDexFile = new File(dexInfoBuilder.mSplit.getPath());
            if (!splitDexFile.getParent().equals(baseDexFile.getParent())) {
                throw new IllegalStateException(
                        "Split APK and base APK are in different directories: "
                        + splitDexFile.getParent() + " != " + baseDexFile.getParent());
            }
            dexInfoBuilder.mRelativeDexPath = splitDexFile.getName();
            if (isIsolatedSplitLoading && dexInfoBuilder.mSplit.isHasCode()) {
                dexInfoBuilder.mClassLoaderName = dexInfoBuilder.mSplit.getClassLoaderName();

                List<AndroidPackageSplit> dependencies = dexInfoBuilder.mSplit.getDependencies();
                if (!Utils.isEmpty(dependencies)) {
                    // We only care about the first dependency because it is the parent split. The
                    // rest are configuration splits, which we don't care.
                    AndroidPackageSplit dependency = dependencies.get(0);
                    for (var dexInfo : dexInfos) {
                        if (Objects.equals(dexInfo.mSplit, dependency)) {
                            dexInfoBuilder.mSplitDependency = dexInfo;
                            break;
                        }
                    }

                    if (dexInfoBuilder.mSplitDependency == null) {
                        throw new IllegalStateException(
                                "Split dependency not found for " + splitDexFile);
                    }
                }
            }
        }

        if (isIsolatedSplitLoading) {
            computeClassLoaderContextsIsolated(dexInfos);
        } else {
            computeClassLoaderContexts(dexInfos);
        }

        return dexInfos;
    }

    /**
     * Computes class loader context for an app that didn't request isolated split loading. Stores
     * the results in {@link PrimaryDexInfoBuilder#mClassLoaderContext}.
     *
     * In this case, all the splits will be loaded in the base apk class loader (in the order of
     * their definition).
     *
     * The CLC for the base APK is `CLN[]{shared-libraries}`; the CLC for the n-th split APK is
     * `CLN[base.apk, split_0.apk, ..., split_n-1.apk]{shared-libraries}`; where `CLN` is the
     * class loader name for the base APK.
     */
    private static void computeClassLoaderContexts(@NonNull List<PrimaryDexInfoBuilder> dexInfos) {
        String baseClassLoaderName = dexInfos.get(0).mClassLoaderName;
        String sharedLibrariesContext = dexInfos.get(0).mSharedLibrariesContext;
        List<String> classpath = new ArrayList<>();
        for (PrimaryDexInfoBuilder dexInfo : dexInfos) {
            if (dexInfo.mSplit.isHasCode()) {
                dexInfo.mClassLoaderContext = encodeClassLoader(baseClassLoaderName, classpath,
                        null /* parentContext */, sharedLibrariesContext);
            }
            // Note that the splits with no code are not removed from the classpath computation.
            // I.e., split_n might get the split_n-1 in its classpath dependency even if split_n-1
            // has no code.
            // The splits with no code do not matter for the runtime which ignores APKs without code
            // when doing the classpath checks. As such we could actually filter them but we don't
            // do it in order to keep consistency with how the apps are loaded.
            classpath.add(dexInfo.mRelativeDexPath);
        }
    }

    /**
     * Computes class loader context for an app that requested for isolated split loading. Stores
     * the results in {@link PrimaryDexInfoBuilder#mClassLoaderContext}.
     *
     * In this case, each split will be loaded with a separate class loader, whose context is a
     * chain formed from inter-split dependencies.
     *
     * The CLC for the base APK is `CLN[]{shared-libraries}`; the CLC for the n-th split APK that
     * depends on the base APK is `CLN_n[];CLN[base.apk]{shared-libraries}`; the CLC for the n-th
     * split APK that depends on the m-th split APK is
     * `CLN_n[];CLN_m[split_m.apk];...;CLN[base.apk]{shared-libraries}`; where `CLN` is the base
     * class loader name for the base APK, `CLN_i` is the class loader name for the i-th split APK,
     * and `...` represents the ancestors along the dependency chain.
     *
     * Specially, if a split does not have any dependency, the CLC for it is `CLN_n[]`.
     */
    private static void computeClassLoaderContextsIsolated(
            @NonNull List<PrimaryDexInfoBuilder> dexInfos) {
        for (PrimaryDexInfoBuilder dexInfo : dexInfos) {
            if (dexInfo.mSplit.isHasCode()) {
                dexInfo.mClassLoaderContext = encodeClassLoader(dexInfo.mClassLoaderName,
                        null /* classpath */, getParentContextRecursive(dexInfo),
                        dexInfo.mSharedLibrariesContext);
            }
        }
    }

    /**
     * Computes the parent class loader context, recursively. Caches results in {@link
     * PrimaryDexInfoBuilder#mContextForChildren}.
     */
    @Nullable
    private static String getParentContextRecursive(@NonNull PrimaryDexInfoBuilder dexInfo) {
        if (dexInfo.mSplitDependency == null) {
            return null;
        }
        PrimaryDexInfoBuilder parent = dexInfo.mSplitDependency;
        if (parent.mContextForChildren == null) {
            parent.mContextForChildren =
                    encodeClassLoader(parent.mClassLoaderName, List.of(parent.mRelativeDexPath),
                            getParentContextRecursive(parent), parent.mSharedLibrariesContext);
        }
        return parent.mContextForChildren;
    }

    /**
     * Returns class loader context in the format of
     * `CLN[classpath...]{share-libraries};parent-context`, where `CLN` is the class loader name.
     */
    @NonNull
    private static String encodeClassLoader(@Nullable String classLoaderName,
            @Nullable List<String> classpath, @Nullable String parentContext,
            @Nullable String sharedLibrariesContext) {
        StringBuilder classLoaderContext = new StringBuilder();

        classLoaderContext.append(encodeClassLoaderName(classLoaderName));

        classLoaderContext.append(
                "[" + (classpath != null ? String.join(":", classpath) : "") + "]");

        if (!TextUtils.isEmpty(sharedLibrariesContext)) {
            classLoaderContext.append(sharedLibrariesContext);
        }

        if (!TextUtils.isEmpty(parentContext)) {
            classLoaderContext.append(";" + parentContext);
        }

        return classLoaderContext.toString();
    }

    @NonNull
    private static String encodeClassLoaderName(@Nullable String classLoaderName) {
        // `PathClassLoader` and `DexClassLoader` are grouped together because they have the same
        // behavior. For null values we default to "PCL". This covers the case where a package does
        // not specify any value for its class loader.
        if (classLoaderName == null || PathClassLoader.class.getName().equals(classLoaderName)
                || DexClassLoader.class.getName().equals(classLoaderName)) {
            return "PCL";
        } else if (DelegateLastClassLoader.class.getName().equals(classLoaderName)) {
            return "DLC";
        } else {
            throw new IllegalStateException("Unsupported classLoaderName: " + classLoaderName);
        }
    }

    /**
     * Returns shared libraries context in the format of
     * `{PCL[library_1_dex_1.jar:library_1_dex_2.jar:...]{library_1-dependencies}#PCL[
     *     library_1_dex_2.jar:library_2_dex_2.jar:...]{library_2-dependencies}#...}`.
     */
    @Nullable
    private static String encodeSharedLibraries(@Nullable List<SharedLibrary> sharedLibraries) {
        if (Utils.isEmpty(sharedLibraries)) {
            return null;
        }
        return sharedLibraries.stream()
                .filter(library -> !library.isNative())
                .map(library
                        -> encodeClassLoader(SHARED_LIBRARY_LOADER_TYPE, library.getAllCodePaths(),
                                null /* parentContext */,
                                encodeSharedLibraries(library.getDependencies())))
                .collect(Collectors.joining("#", "{", "}"));
    }

    public static boolean isIsolatedSplitLoading(@NonNull AndroidPackage pkg) {
        return pkg.isIsolatedSplitLoading() && pkg.getSplits().size() > 1;
    }

    @NonNull
    public static ProfilePath buildRefProfilePath(
            @NonNull PackageState pkgState, @NonNull PrimaryDexInfo dexInfo) {
        String profileName = getProfileName(dexInfo.splitName());
        return AidlUtils.buildProfilePathForPrimaryRef(pkgState.getPackageName(), profileName);
    }

    @NonNull
    public static OutputProfile buildOutputProfile(@NonNull PackageState pkgState,
            @NonNull PrimaryDexInfo dexInfo, int uid, int gid, boolean isPublic) {
        String profileName = getProfileName(dexInfo.splitName());
        return AidlUtils.buildOutputProfileForPrimary(
                pkgState.getPackageName(), profileName, uid, gid, isPublic);
    }

    @NonNull
    public static List<ProfilePath> getCurProfiles(@NonNull UserManager userManager,
            @NonNull PackageState pkgState, @NonNull PrimaryDexInfo dexInfo) {
        return getCurProfiles(
                userManager.getUserHandles(true /* excludeDying */), pkgState, dexInfo);
    }

    @NonNull
    public static List<ProfilePath> getCurProfiles(@NonNull List<UserHandle> userHandles,
            @NonNull PackageState pkgState, @NonNull PrimaryDexInfo dexInfo) {
        List<ProfilePath> profiles = new ArrayList<>();
        for (UserHandle handle : userHandles) {
            int userId = handle.getIdentifier();
            PackageUserState userState = pkgState.getStateForUser(handle);
            if (userState.isInstalled()) {
                profiles.add(AidlUtils.buildProfilePathForPrimaryCur(
                        userId, pkgState.getPackageName(), getProfileName(dexInfo.splitName())));
            }
        }
        return profiles;
    }

    @NonNull
    public static String getProfileName(@Nullable String splitName) {
        return splitName == null ? PROFILE_PRIMARY : splitName + ".split";
    }

    @NonNull
    public static List<ProfilePath> getExternalProfiles(@NonNull PrimaryDexInfo dexInfo) {
        return List.of(AidlUtils.buildProfilePathForPrebuilt(dexInfo.dexPath()),
                AidlUtils.buildProfilePathForDm(dexInfo.dexPath()));
    }

    /** Basic information about a primary dex file (either the base APK or a split APK). */
    @Immutable
    public static class PrimaryDexInfo {
        private final @NonNull AndroidPackageSplit mSplit;

        PrimaryDexInfo(@NonNull AndroidPackageSplit split) {
            mSplit = split;
        }

        /** The path to the dex file. */
        public @NonNull String dexPath() {
            return mSplit.getPath();
        }

        /** True if the dex file has code. */
        public boolean hasCode() {
            return mSplit.isHasCode();
        }

        /** The name of the split, or null for base APK. */
        public @Nullable String splitName() {
            return mSplit.getName();
        }
    }

    /**
     * Detailed information about a primary dex file (either the base APK or a split APK). It
     * contains the class loader context in addition to what is in {@link PrimaryDexInfo}, but
     * producing it requires {@link PackageState}.
     */
    @Immutable
    public static class DetailedPrimaryDexInfo extends PrimaryDexInfo implements DetailedDexInfo {
        private final @Nullable String mClassLoaderContext;

        DetailedPrimaryDexInfo(
                @NonNull AndroidPackageSplit split, @Nullable String classLoaderContext) {
            super(split);
            mClassLoaderContext = classLoaderContext;
        }

        /**
         * A string describing the structure of the class loader that the dex file is loaded with.
         */
        public @Nullable String classLoaderContext() {
            return mClassLoaderContext;
        }
    }

    private static class PrimaryDexInfoBuilder {
        @NonNull AndroidPackageSplit mSplit;
        @Nullable String mRelativeDexPath = null;
        @Nullable String mClassLoaderContext = null;
        @Nullable String mClassLoaderName = null;
        @Nullable PrimaryDexInfoBuilder mSplitDependency = null;
        /** The class loader context of the shared libraries. Only applicable for the base APK. */
        @Nullable String mSharedLibrariesContext = null;
        /** The class loader context for children to use when this dex file is used as a parent. */
        @Nullable String mContextForChildren = null;

        PrimaryDexInfoBuilder(@NonNull AndroidPackageSplit split) {
            mSplit = split;
        }

        PrimaryDexInfo build() {
            return new PrimaryDexInfo(mSplit);
        }

        DetailedPrimaryDexInfo buildDetailed() {
            return new DetailedPrimaryDexInfo(mSplit, mClassLoaderContext);
        }
    }
}
