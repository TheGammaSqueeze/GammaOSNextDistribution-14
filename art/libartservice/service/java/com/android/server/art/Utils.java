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

import static com.android.server.art.ProfilePath.TmpProfilePath;

import android.R;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.role.RoleManager;
import android.apphibernation.AppHibernationManager;
import android.content.Context;
import android.os.Binder;
import android.os.Build;
import android.os.DeadObjectException;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.Trace;
import android.os.UserManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.util.Slog;
import android.util.SparseArray;

import androidx.annotation.RequiresApi;

import com.android.modules.utils.pm.PackageStateModulesUtils;
import com.android.server.art.model.DexoptParams;
import com.android.server.pm.PackageManagerLocal;
import com.android.server.pm.pkg.AndroidPackage;
import com.android.server.pm.pkg.PackageState;

import dalvik.system.DexFile;
import dalvik.system.VMRuntime;

import com.google.auto.value.AutoValue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** @hide */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
public final class Utils {
    public static final String TAG = ArtManagerLocal.TAG;
    public static final String PLATFORM_PACKAGE_NAME = "android";

    /** A copy of {@link android.os.Trace.TRACE_TAG_DALVIK}. */
    private static final long TRACE_TAG_DALVIK = 1L << 14;

    private Utils() {}

    /**
     * Checks if given array is null or has zero elements.
     */
    public static <T> boolean isEmpty(@Nullable Collection<T> array) {
        return array == null || array.isEmpty();
    }

    /**
     * Checks if given array is null or has zero elements.
     */
    public static <T> boolean isEmpty(@Nullable SparseArray<T> array) {
        return array == null || array.size() == 0;
    }

    /**
     * Checks if given array is null or has zero elements.
     */
    public static boolean isEmpty(@Nullable int[] array) {
        return array == null || array.length == 0;
    }

    /** Returns the ABI information for the package. The primary ABI comes first. */
    @NonNull
    public static List<Abi> getAllAbis(@NonNull PackageState pkgState) {
        List<Abi> abis = new ArrayList<>();
        abis.add(getPrimaryAbi(pkgState));
        String pkgPrimaryCpuAbi = pkgState.getPrimaryCpuAbi();
        String pkgSecondaryCpuAbi = pkgState.getSecondaryCpuAbi();
        if (pkgSecondaryCpuAbi != null) {
            Utils.check(pkgState.getPrimaryCpuAbi() != null);
            String isa = getTranslatedIsa(VMRuntime.getInstructionSet(pkgSecondaryCpuAbi));
            abis.add(Abi.create(nativeIsaToAbi(isa), isa, false /* isPrimaryAbi */));
        }
        // Primary and secondary ABIs should be guaranteed to have different ISAs.
        if (abis.size() == 2 && abis.get(0).isa().equals(abis.get(1).isa())) {
            throw new IllegalStateException(String.format(
                    "Duplicate ISA: primary ABI '%s' ('%s'), secondary ABI '%s' ('%s')",
                    pkgPrimaryCpuAbi, abis.get(0).name(), pkgSecondaryCpuAbi, abis.get(1).name()));
        }
        return abis;
    }

    /**
     * Returns the ABI information for the ABIs with the given names. The primary ABI comes first,
     * if given.
     */
    @NonNull
    public static List<Abi> getAllAbisForNames(
            @NonNull Set<String> abiNames, @NonNull PackageState pkgState) {
        Utils.check(abiNames.stream().allMatch(Utils::isNativeAbi));
        Abi pkgPrimaryAbi = getPrimaryAbi(pkgState);
        return abiNames.stream()
                .map(name
                        -> Abi.create(name, VMRuntime.getInstructionSet(name),
                                name.equals(pkgPrimaryAbi.name())))
                .sorted(Comparator.comparing(Abi::isPrimaryAbi).reversed())
                .collect(Collectors.toList());
    }

    @NonNull
    public static Abi getPrimaryAbi(@NonNull PackageState pkgState) {
        String primaryCpuAbi = pkgState.getPrimaryCpuAbi();
        if (primaryCpuAbi != null) {
            String isa = getTranslatedIsa(VMRuntime.getInstructionSet(primaryCpuAbi));
            return Abi.create(nativeIsaToAbi(isa), isa, true /* isPrimaryAbi */);
        }
        // This is the most common case. The package manager can't infer the ABIs, probably because
        // the package doesn't contain any native library. The app is launched with the device's
        // preferred ABI.
        String preferredAbi = Constants.getPreferredAbi();
        Utils.check(isNativeAbi(preferredAbi));
        return Abi.create(
                preferredAbi, VMRuntime.getInstructionSet(preferredAbi), true /* isPrimaryAbi */);
    }

    /**
     * If the given ISA isn't native to the device, returns the ISA that the native bridge
     * translates it to. Otherwise, returns the ISA as is. This is the ISA that the app is actually
     * launched with and therefore the ISA that should be used to compile the app.
     */
    @NonNull
    private static String getTranslatedIsa(@NonNull String isa) {
        String abi64 = Constants.getNative64BitAbi();
        String abi32 = Constants.getNative32BitAbi();
        if ((abi64 != null && isa.equals(VMRuntime.getInstructionSet(abi64)))
                || (abi32 != null && isa.equals(VMRuntime.getInstructionSet(abi32)))) {
            return isa;
        }
        String translatedIsa = SystemProperties.get("ro.dalvik.vm.isa." + isa);
        if (TextUtils.isEmpty(translatedIsa)) {
            throw new IllegalStateException(String.format("Unsupported isa '%s'", isa));
        }
        return translatedIsa;
    }

    @NonNull
    private static String nativeIsaToAbi(@NonNull String isa) {
        String abi64 = Constants.getNative64BitAbi();
        if (abi64 != null && isa.equals(VMRuntime.getInstructionSet(abi64))) {
            return abi64;
        }
        String abi32 = Constants.getNative32BitAbi();
        if (abi32 != null && isa.equals(VMRuntime.getInstructionSet(abi32))) {
            return abi32;
        }
        throw new IllegalStateException(String.format("Non-native isa '%s'", isa));
    }

    private static boolean isNativeAbi(@NonNull String abiName) {
        return abiName.equals(Constants.getNative64BitAbi())
                || abiName.equals(Constants.getNative32BitAbi());
    }

    /**
     * Returns whether the artifacts of the primary dex files should be in the global dalvik-cache
     * directory.
     *
     * This method is not needed for secondary dex files because they are always in writable
     * locations.
     */
    @NonNull
    public static boolean isInDalvikCache(@NonNull PackageState pkgState, @NonNull IArtd artd)
            throws RemoteException {
        try {
            return artd.isInDalvikCache(pkgState.getAndroidPackage().getSplits().get(0).getPath());
        } catch (ServiceSpecificException e) {
            // This should never happen. Ignore the error and conservatively use dalvik-cache to
            // minimize the risk.
            // TODO(jiakaiz): Throw the error instead of ignoring it.
            Log.e(TAG, "Failed to determine the location of the artifacts", e);
            return true;
        }
    }

    /** Returns true if the given string is a valid compiler filter. */
    public static boolean isValidArtServiceCompilerFilter(@NonNull String compilerFilter) {
        if (compilerFilter.equals(DexoptParams.COMPILER_FILTER_NOOP)) {
            return true;
        }
        return DexFile.isValidCompilerFilter(compilerFilter);
    }

    public static boolean implies(boolean cond1, boolean cond2) {
        return cond1 ? cond2 : true;
    }

    public static void check(boolean cond) {
        // This cannot be replaced with `assert` because `assert` is not enabled in Android.
        if (!cond) {
            throw new IllegalStateException("Check failed");
        }
    }

    @NonNull
    public static PackageState getPackageStateOrThrow(
            @NonNull PackageManagerLocal.FilteredSnapshot snapshot, @NonNull String packageName) {
        PackageState pkgState = snapshot.getPackageState(packageName);
        if (pkgState == null) {
            throw new IllegalArgumentException("Package not found: " + packageName);
        }
        return pkgState;
    }

    @NonNull
    public static AndroidPackage getPackageOrThrow(@NonNull PackageState pkgState) {
        AndroidPackage pkg = pkgState.getAndroidPackage();
        if (pkg == null) {
            throw new IllegalArgumentException(
                    "Unable to get package " + pkgState.getPackageName());
        }
        return pkg;
    }

    @NonNull
    public static String assertNonEmpty(@Nullable String str) {
        if (TextUtils.isEmpty(str)) {
            throw new IllegalArgumentException();
        }
        return str;
    }

    public static void executeAndWait(@NonNull Executor executor, @NonNull Runnable runnable) {
        getFuture(CompletableFuture.runAsync(runnable, executor));
    }

    public static <T> T executeAndWait(@NonNull Executor executor, @NonNull Supplier<T> supplier) {
        return getFuture(CompletableFuture.supplyAsync(supplier, executor));
    }

    public static <T> T getFuture(Future<T> future) {
        try {
            return future.get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new RuntimeException(cause);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns true if the given package is dexoptable.
     *
     * @param appHibernationManager the {@link AppHibernationManager} instance for checking
     *         hibernation status, or null to skip the check
     */
    public static boolean canDexoptPackage(
            @NonNull PackageState pkgState, @Nullable AppHibernationManager appHibernationManager) {
        if (!PackageStateModulesUtils.isDexoptable(pkgState)) {
            return false;
        }

        // We do not dexopt unused packages.
        // If `appHibernationManager` is null, the caller's intention is to skip the check.
        if (appHibernationManager != null
                && shouldSkipDexoptDueToHibernation(pkgState, appHibernationManager)) {
            return false;
        }

        return true;
    }

    public static boolean shouldSkipDexoptDueToHibernation(
            @NonNull PackageState pkgState, @NonNull AppHibernationManager appHibernationManager) {
        return appHibernationManager.isHibernatingGlobally(pkgState.getPackageName())
                && appHibernationManager.isOatArtifactDeletionEnabled();
    }

    public static long getPackageLastActiveTime(@NonNull PackageState pkgState,
            @NonNull DexUseManagerLocal dexUseManager, @NonNull UserManager userManager) {
        long lastUsedAtMs = dexUseManager.getPackageLastUsedAtMs(pkgState.getPackageName());
        // The time where the last user installed the package the first time.
        long lastFirstInstallTimeMs =
                userManager.getUserHandles(true /* excludeDying */)
                        .stream()
                        .map(handle -> pkgState.getStateForUser(handle))
                        .map(userState -> userState.getFirstInstallTimeMillis())
                        .max(Long::compare)
                        .orElse(0l);
        return Math.max(lastUsedAtMs, lastFirstInstallTimeMs);
    }

    public static void deleteIfExistsSafe(@Nullable File file) {
        if (file != null) {
            deleteIfExistsSafe(file.toPath());
        }
    }

    public static void deleteIfExistsSafe(@NonNull Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            Log.e(TAG, "Failed to delete file '" + path + "'", e);
        }
    }

    public static boolean isSystemUiPackage(@NonNull Context context, @NonNull String packageName) {
        return packageName.equals(context.getString(R.string.config_systemUi));
    }

    public static boolean isLauncherPackage(@NonNull Context context, @NonNull String packageName) {
        RoleManager roleManager = context.getSystemService(RoleManager.class);
        return roleManager.getRoleHolders(RoleManager.ROLE_HOME).contains(packageName);
    }

    /**
     * Gets the existing reference profile if one exists, or initializes a reference profile from an
     * external profile.
     *
     * If the reference profile is initialized from an external profile, the returned profile path
     * will be a {@link TmpProfilePath}. It's the callers responsibility to either commit it to the
     * final location by calling {@link IArtd#commitTmpProfile} or clean it up by calling {@link
     * IArtd#deleteProfile}.
     *
     * Note: "External profile" means profiles that are external to the device, as opposed to local
     * profiles, which are collected on the device. An embedded profile (a profile embedded in the
     * dex file) is also an external profile.
     *
     * @param dexPath the path to the dex file that the profile is checked against
     * @param refProfile the path where an existing reference profile would be found, if present
     * @param externalProfiles a list of external profiles to initialize the reference profile from,
     *         in the order of preference
     * @param initOutput the final location to initialize the reference profile to
     */
    @NonNull
    public static InitProfileResult getOrInitReferenceProfile(@NonNull IArtd artd,
            @NonNull String dexPath, @NonNull ProfilePath refProfile,
            @NonNull List<ProfilePath> externalProfiles, @NonNull OutputProfile initOutput)
            throws RemoteException {
        try {
            if (artd.isProfileUsable(refProfile, dexPath)) {
                boolean isOtherReadable =
                        artd.getProfileVisibility(refProfile) == FileVisibility.OTHER_READABLE;
                return InitProfileResult.create(
                        refProfile, isOtherReadable, List.of() /* externalProfileErrors */);
            }
        } catch (ServiceSpecificException e) {
            Log.e(TAG,
                    "Failed to use the existing reference profile "
                            + AidlUtils.toString(refProfile),
                    e);
        }

        return initReferenceProfile(artd, dexPath, externalProfiles, initOutput);
    }

    /**
     * Similar to above, but never uses an existing profile.
     *
     * The {@link InitProfileResult#isOtherReadable} field is always set to true. The profile
     * returned by this method is initialized from an external profile, meaning it has no user data,
     * so it's always readable by others.
     */
    @Nullable
    public static InitProfileResult initReferenceProfile(@NonNull IArtd artd,
            @NonNull String dexPath, @NonNull List<ProfilePath> externalProfiles,
            @NonNull OutputProfile output) throws RemoteException {
        // Each element is a pair of a profile name (for logging) and the corresponding initializer.
        // The order matters. Non-embedded profiles should take precedence.
        List<Pair<String, ProfileInitializer>> profileInitializers = new ArrayList<>();
        for (ProfilePath profile : externalProfiles) {
            // If the profile path is a PrebuiltProfilePath, and the APK is really a prebuilt
            // one, rewriting the profile is unnecessary because the dex location is known at
            // build time and is correctly set in the profile header. However, the APK can also
            // be an installed one, in which case partners may place a profile file next to the
            // APK at install time. Rewriting the profile in the latter case is necessary.
            profileInitializers.add(Pair.create(AidlUtils.toString(profile),
                    () -> artd.copyAndRewriteProfile(profile, output, dexPath)));
        }
        profileInitializers.add(Pair.create(
                "embedded profile", () -> artd.copyAndRewriteEmbeddedProfile(output, dexPath)));

        List<String> externalProfileErrors = new ArrayList<>();
        for (var pair : profileInitializers) {
            try {
                CopyAndRewriteProfileResult result = pair.second.get();
                if (result.status == CopyAndRewriteProfileResult.Status.SUCCESS) {
                    return InitProfileResult.create(ProfilePath.tmpProfilePath(output.profilePath),
                            true /* isOtherReadable */, externalProfileErrors);
                }
                if (result.status == CopyAndRewriteProfileResult.Status.BAD_PROFILE) {
                    externalProfileErrors.add(result.errorMsg);
                }
            } catch (ServiceSpecificException e) {
                Log.e(TAG, "Failed to initialize profile from " + pair.first, e);
            }
        }

        return InitProfileResult.create(
                null /* profile */, true /* isOtherReadable */, externalProfileErrors);
    }

    public static void logArtdException(@NonNull RemoteException e) {
        String message = "An error occurred when calling artd";
        if (e instanceof DeadObjectException) {
            // We assume that `DeadObjectException` only happens in two cases:
            // 1. artd crashed, in which case a native stack trace was logged.
            // 2. artd was killed before system server during device shutdown, in which case the
            //    exception is expected.
            // In either case, we don't need to surface the exception from here.
            // The Java stack trace is intentionally omitted because it's not helpful.
            Log.e(TAG, message);
        } else {
            // Not expected. Log wtf to surface it.
            Slog.wtf(TAG, message, e);
        }
    }

    public static boolean isSystemOrRootOrShell() {
        int uid = Binder.getCallingUid();
        return uid == Process.SYSTEM_UID || uid == Process.ROOT_UID || uid == Process.SHELL_UID;
    }

    @AutoValue
    public abstract static class Abi {
        static @NonNull Abi create(
                @NonNull String name, @NonNull String isa, boolean isPrimaryAbi) {
            return new AutoValue_Utils_Abi(name, isa, isPrimaryAbi);
        }

        // The ABI name. E.g., "arm64-v8a".
        abstract @NonNull String name();

        // The instruction set name. E.g., "arm64".
        abstract @NonNull String isa();

        abstract boolean isPrimaryAbi();
    }

    public static class Tracing implements AutoCloseable {
        public Tracing(@NonNull String methodName) {
            Trace.traceBegin(TRACE_TAG_DALVIK, methodName);
        }

        @Override
        public void close() {
            Trace.traceEnd(TRACE_TAG_DALVIK);
        }
    }

    public static class TracingWithTimingLogging extends Tracing {
        @NonNull private final String mTag;
        @NonNull private final String mMethodName;
        @NonNull private final long mStartTimeMs;

        public TracingWithTimingLogging(@NonNull String tag, @NonNull String methodName) {
            super(methodName);
            mTag = tag;
            mMethodName = methodName;
            mStartTimeMs = SystemClock.elapsedRealtime();
            Log.d(tag, methodName);
        }

        @Override
        public void close() {
            Log.d(mTag,
                    mMethodName + " took to complete: "
                            + (SystemClock.elapsedRealtime() - mStartTimeMs) + "ms");
            super.close();
        }
    }

    /** The result of {@link #getOrInitReferenceProfile} and {@link #initReferenceProfile}. */
    @AutoValue
    @SuppressWarnings("AutoValueImmutableFields") // Can't use ImmutableList because it's in Guava.
    public abstract static class InitProfileResult {
        static @NonNull InitProfileResult create(@Nullable ProfilePath profile,
                boolean isOtherReadable, @NonNull List<String> externalProfileErrors) {
            return new AutoValue_Utils_InitProfileResult(
                    profile, isOtherReadable, Collections.unmodifiableList(externalProfileErrors));
        }

        /**
         * The found or initialized profile, or null if there is no reference profile or external
         * profile to use.
         */
        abstract @Nullable ProfilePath profile();

        /**
         * Whether the profile is readable by others.
         *
         * If {@link #profile} returns null, this field is always true.
         */
        abstract boolean isOtherReadable();

        /** Errors encountered when initializing from external profiles. */
        abstract @NonNull List<String> externalProfileErrors();
    }

    @FunctionalInterface
    private interface ProfileInitializer {
        CopyAndRewriteProfileResult get() throws RemoteException;
    }
}
