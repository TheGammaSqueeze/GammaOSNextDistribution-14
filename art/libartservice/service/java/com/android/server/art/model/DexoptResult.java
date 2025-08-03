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

package com.android.server.art.model;

import android.annotation.DurationMillisLong;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;

import com.android.internal.annotations.Immutable;
import com.android.internal.annotations.VisibleForTesting;

import com.google.auto.value.AutoValue;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** @hide */
@SystemApi(client = SystemApi.Client.SYSTEM_SERVER)
@Immutable
@AutoValue
public abstract class DexoptResult {
    // Possible values of {@link #DexoptResultStatus}.
    // A larger number means a higher priority. If multiple dex container files are processed, the
    // final status will be the one with the highest priority.
    /** Dexopt is skipped because there is no need to do it. */
    public static final int DEXOPT_SKIPPED = 10;
    /** Dexopt is performed successfully. */
    public static final int DEXOPT_PERFORMED = 20;
    /** Dexopt is failed. */
    public static final int DEXOPT_FAILED = 30;
    /** Dexopt is cancelled. */
    public static final int DEXOPT_CANCELLED = 40;

    /** @hide */
    // clang-format off
    @IntDef(prefix = {"DEXOPT_"}, value = {
        DEXOPT_SKIPPED,
        DEXOPT_FAILED,
        DEXOPT_PERFORMED,
        DEXOPT_CANCELLED,
    })
    // clang-format on
    @Retention(RetentionPolicy.SOURCE)
    public @interface DexoptResultStatus {}

    // Possible values of {@link #DexoptResultExtendedStatusFlags}.
    /** Dexopt is skipped because the remaining storage space is low. */
    public static final int EXTENDED_SKIPPED_STORAGE_LOW = 1 << 0;
    /**
     * Dexopt is skipped because the dex container file has no dex code while the manifest declares
     * that it does.
     *
     * Note that this flag doesn't apply to dex container files that are not declared to have code.
     * Instead, those files are not listed in {@link
     * PackageDexoptResult#getDexContainerFileDexoptResults} in the first place.
     */
    public static final int EXTENDED_SKIPPED_NO_DEX_CODE = 1 << 1;
    /**
     * Dexopt encountered errors when processing the profiles that are external to the device,
     * including the profile in the DM file and the profile embedded in the dex container file.
     * Details of the errors can be found in {@link
     * DexContainerFileDexoptResult#getExternalProfileErrors}.
     *
     * This is not a critical error. Dexopt may still have succeeded after ignoring the bad external
     * profiles.
     */
    public static final int EXTENDED_BAD_EXTERNAL_PROFILE = 1 << 2;

    /** @hide */
    // clang-format off
    @IntDef(flag = true, prefix = {"EXTENDED_"}, value = {
        EXTENDED_SKIPPED_STORAGE_LOW,
        EXTENDED_SKIPPED_NO_DEX_CODE,
        EXTENDED_BAD_EXTERNAL_PROFILE,
    })
    // clang-format on
    @Retention(RetentionPolicy.SOURCE)
    public @interface DexoptResultExtendedStatusFlags {}

    /** @hide */
    protected DexoptResult() {}

    /** @hide */
    public static @NonNull DexoptResult create(@NonNull String requestedCompilerFilter,
            @NonNull String reason, @NonNull List<PackageDexoptResult> packageDexoptResult) {
        return new AutoValue_DexoptResult(requestedCompilerFilter, reason, packageDexoptResult);
    }

    /** @hide */
    @VisibleForTesting
    public static @NonNull DexoptResult create() {
        return new AutoValue_DexoptResult(
                "compiler-filter", "reason", List.of() /* packageDexoptResult */);
    }

    /**
     * The requested compiler filter. Note that the compiler filter might be adjusted before the
     * execution based on factors like dexopt flags, whether the profile is available, or whether
     * the app is used by other apps.
     *
     * @see DexoptParams.Builder#setCompilerFilter(String)
     * @see DexContainerFileDexoptResult#getActualCompilerFilter()
     */
    public abstract @NonNull String getRequestedCompilerFilter();

    /** The compilation reason. */
    public abstract @NonNull String getReason();

    /**
     * The result of each individual package.
     *
     * If the request is to dexopt a single package without dexopting dependencies, the only
     * element is the result of the requested package.
     *
     * If the request is to dexopt a single package with {@link
     * ArtFlags.FLAG_SHOULD_INCLUDE_DEPENDENCIES} set, the first element is the result of the
     * requested package, and the rest are the results of the dependency packages.
     *
     * If the request is to dexopt multiple packages, the list contains the results of all the
     * requested packages. The results of their dependency packages are also included if {@link
     * ArtFlags.FLAG_SHOULD_INCLUDE_DEPENDENCIES} is set.
     *
     * If the request is a batch dexopt operation that got cancelled, the list still has an entry
     * for every package that was requested to be optimized.
     */
    public abstract @NonNull List<PackageDexoptResult> getPackageDexoptResults();

    /** The final status. */
    public @DexoptResultStatus int getFinalStatus() {
        return getPackageDexoptResults()
                .stream()
                .mapToInt(result -> result.getStatus())
                .max()
                .orElse(DEXOPT_SKIPPED);
    }

    /** @hide */
    @NonNull
    public static String dexoptResultStatusToString(@DexoptResultStatus int status) {
        switch (status) {
            case DexoptResult.DEXOPT_SKIPPED:
                return "SKIPPED";
            case DexoptResult.DEXOPT_PERFORMED:
                return "PERFORMED";
            case DexoptResult.DEXOPT_FAILED:
                return "FAILED";
            case DexoptResult.DEXOPT_CANCELLED:
                return "CANCELLED";
        }
        throw new IllegalArgumentException("Unknown dexopt status " + status);
    }

    /** @hide */
    @NonNull
    public static String dexoptResultExtendedStatusFlagsToString(
            @DexoptResultExtendedStatusFlags int flags) {
        var strs = new ArrayList<String>();
        if ((flags & DexoptResult.EXTENDED_SKIPPED_STORAGE_LOW) != 0) {
            strs.add("EXTENDED_SKIPPED_STORAGE_LOW");
        }
        if ((flags & DexoptResult.EXTENDED_SKIPPED_NO_DEX_CODE) != 0) {
            strs.add("EXTENDED_SKIPPED_NO_DEX_CODE");
        }
        if ((flags & DexoptResult.EXTENDED_BAD_EXTERNAL_PROFILE) != 0) {
            strs.add("EXTENDED_BAD_EXTERNAL_PROFILE");
        }
        return String.join(", ", strs);
    }

    /**
     * Describes the result of a package.
     *
     * @hide
     */
    @SystemApi(client = SystemApi.Client.SYSTEM_SERVER)
    @Immutable
    @AutoValue
    public static abstract class PackageDexoptResult {
        /** @hide */
        protected PackageDexoptResult() {}

        /** @hide */
        public static @NonNull PackageDexoptResult create(@NonNull String packageName,
                @NonNull List<DexContainerFileDexoptResult> dexContainerFileDexoptResults,
                @Nullable @DexoptResultStatus Integer packageLevelStatus) {
            return new AutoValue_DexoptResult_PackageDexoptResult(
                    packageName, dexContainerFileDexoptResults, packageLevelStatus);
        }

        /** The package name. */
        public abstract @NonNull String getPackageName();

        /**
         * The results of dexopting dex container files. Note that there can be multiple entries
         * for the same dex container file, but for different ABIs.
         */
        public abstract @NonNull List<DexContainerFileDexoptResult>
        getDexContainerFileDexoptResults();

        /** @hide */
        @Nullable @DexoptResultStatus public abstract Integer getPackageLevelStatus();

        /** The overall status of the package. */
        public @DexoptResultStatus int getStatus() {
            return getPackageLevelStatus() != null ? getPackageLevelStatus()
                                                   : getDexContainerFileDexoptResults()
                                                             .stream()
                                                             .mapToInt(result -> result.getStatus())
                                                             .max()
                                                             .orElse(DEXOPT_SKIPPED);
        }

        /** True if the package has any artifacts updated by this operation. */
        public boolean hasUpdatedArtifacts() {
            return getDexContainerFileDexoptResults().stream().anyMatch(
                    result -> result.getStatus() == DEXOPT_PERFORMED);
        }
    }

    /**
     * Describes the result of dexopting a dex container file.
     *
     * @hide
     */
    @SystemApi(client = SystemApi.Client.SYSTEM_SERVER)
    @Immutable
    @AutoValue
    @SuppressWarnings("AutoValueImmutableFields") // Can't use ImmutableList because it's in Guava.
    public static abstract class DexContainerFileDexoptResult {
        /** @hide */
        protected DexContainerFileDexoptResult() {}

        /** @hide */
        public static @NonNull DexContainerFileDexoptResult create(@NonNull String dexContainerFile,
                boolean isPrimaryAbi, @NonNull String abi, @NonNull String compilerFilter,
                @DexoptResultStatus int status, long dex2oatWallTimeMillis,
                long dex2oatCpuTimeMillis, long sizeBytes, long sizeBeforeBytes,
                @DexoptResultExtendedStatusFlags int extendedStatusFlags,
                @NonNull List<String> externalProfileErrors) {
            return new AutoValue_DexoptResult_DexContainerFileDexoptResult(dexContainerFile,
                    isPrimaryAbi, abi, compilerFilter, status, dex2oatWallTimeMillis,
                    dex2oatCpuTimeMillis, sizeBytes, sizeBeforeBytes, extendedStatusFlags,
                    Collections.unmodifiableList(externalProfileErrors));
        }

        /** @hide */
        @VisibleForTesting
        public static @NonNull DexContainerFileDexoptResult create(@NonNull String dexContainerFile,
                boolean isPrimaryAbi, @NonNull String abi, @NonNull String compilerFilter,
                @DexoptResultStatus int status) {
            return create(dexContainerFile, isPrimaryAbi, abi, compilerFilter, status,
                    0 /* dex2oatWallTimeMillis */, 0 /* dex2oatCpuTimeMillis */, 0 /* sizeBytes */,
                    0 /* sizeBeforeBytes */, 0 /* extendedStatusFlags */,
                    List.of() /* externalProfileErrors */);
        }

        /** The absolute path to the dex container file. */
        public abstract @NonNull String getDexContainerFile();

        /**
         * If true, the dexopt is for the primary ABI of the package (the ABI that the
         * application is launched with). Otherwise, the dexopt is for an ABI that other
         * applications might be launched with when using this application's code.
         */
        public abstract boolean isPrimaryAbi();

        /**
         * Returns the ABI that the dexopt is for. Possible values are documented at
         * https://developer.android.com/ndk/guides/abis#sa.
         */
        public abstract @NonNull String getAbi();

        /**
         * The actual compiler filter.
         *
         * @see DexoptParams.Builder#setCompilerFilter(String)
         */
        public abstract @NonNull String getActualCompilerFilter();

        /** The status of dexopting this dex container file. */
        public abstract @DexoptResultStatus int getStatus();

        /**
         * The wall time of the dex2oat invocation, in milliseconds, if dex2oat succeeded or was
         * cancelled. Returns 0 if dex2oat failed or was not run, or if failed to get the value.
         */
        public abstract @DurationMillisLong long getDex2oatWallTimeMillis();

        /**
         * The CPU time of the dex2oat invocation, in milliseconds, if dex2oat succeeded or was
         * cancelled. Returns 0 if dex2oat failed or was not run, or if failed to get the value.
         */
        public abstract @DurationMillisLong long getDex2oatCpuTimeMillis();

        /**
         * The total size, in bytes, of the dexopt artifacts. Returns 0 if {@link #getStatus()}
         * is not {@link #DEXOPT_PERFORMED}.
         */
        public abstract long getSizeBytes();

        /**
         * The total size, in bytes, of the previous dexopt artifacts that has been replaced.
         * Returns 0 if there were no previous dexopt artifacts or {@link #getStatus()} is not
         * {@link #DEXOPT_PERFORMED}.
         */
        public abstract long getSizeBeforeBytes();

        /**
         * A bitfield of the extended status flags.
         *
         * Flags that starts with `EXTENDED_SKIPPED_` are a subset of the reasons why dexopt is
         * skipped. Note that they don't cover all possible reasons. At most one `EXTENDED_SKIPPED_`
         * flag will be set, even if the situation meets multiple `EXTENDED_SKIPPED_` flags. The
         * order of precedence of those flags is undefined.
         */
        public abstract @DexoptResultExtendedStatusFlags int getExtendedStatusFlags();

        /**
         * Details of errors occurred when processing external profiles, one error per profile file
         * that the dexopter tried to read.
         *
         * If the same dex container file is dexopted for multiple ABIs, the same profile errors
         * will be repeated for each ABI in the {@link DexContainerFileDexoptResult}s of the same
         * dex container file.
         *
         * The error messages are for logging only, and they include the paths to the profile files
         * that caused the errors.
         *
         * @see #EXTENDED_BAD_EXTERNAL_PROFILE.
         */
        public abstract @NonNull List<String> getExternalProfileErrors();

        @Override
        @NonNull
        public String toString() {
            return String.format("DexContainerFileDexoptResult{"
                            + "dexContainerFile=%s, "
                            + "primaryAbi=%b, "
                            + "abi=%s, "
                            + "actualCompilerFilter=%s, "
                            + "status=%s, "
                            + "dex2oatWallTimeMillis=%d, "
                            + "dex2oatCpuTimeMillis=%d, "
                            + "sizeBytes=%d, "
                            + "sizeBeforeBytes=%d, "
                            + "extendedStatusFlags=[%s]}",
                    getDexContainerFile(), isPrimaryAbi(), getAbi(), getActualCompilerFilter(),
                    DexoptResult.dexoptResultStatusToString(getStatus()),
                    getDex2oatWallTimeMillis(), getDex2oatCpuTimeMillis(), getSizeBytes(),
                    getSizeBeforeBytes(),
                    DexoptResult.dexoptResultExtendedStatusFlagsToString(getExtendedStatusFlags()));
        }
    }
}
