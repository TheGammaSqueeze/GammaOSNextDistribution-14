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

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.SuppressLint;
import android.annotation.SystemApi;
import android.app.job.JobScheduler;

import com.android.server.art.ArtManagerLocal;
import com.android.server.art.PriorityClass;
import com.android.server.art.ReasonMapping;
import com.android.server.pm.PackageManagerLocal;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

/** @hide */
@SystemApi(client = SystemApi.Client.SYSTEM_SERVER)
public class ArtFlags {
    // Common flags.

    /**
     * Whether the operation is applied for primary dex'es (all APKs that are installed as part of
     * the package, including the base APK and all other split APKs).
     */
    public static final int FLAG_FOR_PRIMARY_DEX = 1 << 0;
    /**
     * Whether the operation is applied for secondary dex'es (APKs/JARs that the app puts in its
     * own data directory at runtime and loads with custom classloaders).
     */
    public static final int FLAG_FOR_SECONDARY_DEX = 1 << 1;

    // Flags specific to `dexoptPackage`.

    /**
     * Whether to dexopt dependency libraries as well (dependencies that are declared by the app
     * with <uses-library> tags and transitive dependencies).
     */
    public static final int FLAG_SHOULD_INCLUDE_DEPENDENCIES = 1 << 2;
    /**
     * Whether the intention is to downgrade the compiler filter. If true, the dexopt will
     * be skipped if the target compiler filter is better than or equal to the compiler filter
     * of the existing dexopt artifacts, or dexopt artifacts do not exist.
     */
    public static final int FLAG_SHOULD_DOWNGRADE = 1 << 3;
    /**
     * Whether to force dexopt. If true, the dexopt will be performed regardless of
     * any existing dexopt artifacts.
     */
    public static final int FLAG_FORCE = 1 << 4;
    /**
     * If set, the dexopt will be performed for a single split. Otherwise, the dexopt
     * will be performed for all splits. {@link DexoptParams.Builder#setSplitName()} can be used
     * to specify the split to dexopt.
     *
     * When this flag is set, {@link #FLAG_FOR_PRIMARY_DEX} must be set, and {@link
     * #FLAG_FOR_SECONDARY_DEX} and {@link #FLAG_SHOULD_INCLUDE_DEPENDENCIES} must not be set.
     */
    public static final int FLAG_FOR_SINGLE_SPLIT = 1 << 5;
    /**
     * If set, skips the dexopt if the remaining storage space is low. The threshold is
     * controlled by the global settings {@code sys_storage_threshold_percentage} and {@code
     * sys_storage_threshold_max_bytes}.
     */
    public static final int FLAG_SKIP_IF_STORAGE_LOW = 1 << 6;
    /**
     * If set, no profile will be used by dexopt. I.e., if the compiler filter is a profile-guided
     * one, such as "speed-profile", it will be adjusted to "verify". This option is especially
     * useful when the compiler filter is not explicitly specified (i.e., is inferred from the
     * compilation reason).
     */
    @SuppressLint("UnflaggedApi") // Flag support for mainline is not available.
    public static final int FLAG_IGNORE_PROFILE = 1 << 7;
    /**
     * Whether to force merge profiles even if the difference between before and after the merge
     * is not significant.
     *
     * @hide
     */
    public static final int FLAG_FORCE_MERGE_PROFILE = 1 << 8;

    /**
     * Flags for {@link
     * ArtManagerLocal#getDexoptStatus(PackageManagerLocal.FilteredSnapshot, String, int)}.
     *
     * @hide
     */
    // clang-format off
    @IntDef(flag = true, prefix = "FLAG_", value = {
        FLAG_FOR_PRIMARY_DEX,
        FLAG_FOR_SECONDARY_DEX,
    })
    // clang-format on
    @Retention(RetentionPolicy.SOURCE)
    public @interface GetStatusFlags {}

    /**
     * Default flags that are used when {@link
     * ArtManagerLocal#getDexoptStatus(PackageManagerLocal.FilteredSnapshot, String)} is
     * called.
     * Value: {@link #FLAG_FOR_PRIMARY_DEX}, {@link #FLAG_FOR_SECONDARY_DEX}.
     */
    public static @GetStatusFlags int defaultGetStatusFlags() {
        return FLAG_FOR_PRIMARY_DEX | FLAG_FOR_SECONDARY_DEX;
    }

    /**
     * Flags for {@link DexoptParams}.
     *
     * @hide
     */
    // clang-format off
    @IntDef(flag = true, prefix = "FLAG_", value = {
        FLAG_FOR_PRIMARY_DEX,
        FLAG_FOR_SECONDARY_DEX,
        FLAG_SHOULD_INCLUDE_DEPENDENCIES,
        FLAG_SHOULD_DOWNGRADE,
        FLAG_FORCE,
        FLAG_FOR_SINGLE_SPLIT,
        FLAG_SKIP_IF_STORAGE_LOW,
        FLAG_IGNORE_PROFILE,
        FLAG_FORCE_MERGE_PROFILE,
    })
    // clang-format on
    @Retention(RetentionPolicy.SOURCE)
    public @interface DexoptFlags {}

    /**
     * Default flags that are used when
     * {@link DexoptParams.Builder#Builder(String)} is called.
     *
     * @hide
     */
    public static @DexoptFlags int defaultDexoptFlags(@NonNull String reason) {
        switch (reason) {
            case ReasonMapping.REASON_INSTALL:
            case ReasonMapping.REASON_INSTALL_FAST:
            case ReasonMapping.REASON_INSTALL_BULK:
            case ReasonMapping.REASON_INSTALL_BULK_SECONDARY:
            case ReasonMapping.REASON_INSTALL_BULK_DOWNGRADED:
            case ReasonMapping.REASON_INSTALL_BULK_SECONDARY_DOWNGRADED:
                return FLAG_FOR_PRIMARY_DEX;
            case ReasonMapping.REASON_INACTIVE:
                return FLAG_FOR_PRIMARY_DEX | FLAG_FOR_SECONDARY_DEX | FLAG_SHOULD_DOWNGRADE;
            case ReasonMapping.REASON_FIRST_BOOT:
            case ReasonMapping.REASON_BOOT_AFTER_OTA:
            case ReasonMapping.REASON_BOOT_AFTER_MAINLINE_UPDATE:
                return FLAG_FOR_PRIMARY_DEX | FLAG_SHOULD_INCLUDE_DEPENDENCIES;
            case ReasonMapping.REASON_BG_DEXOPT:
                return FLAG_FOR_PRIMARY_DEX | FLAG_FOR_SECONDARY_DEX
                        | FLAG_SHOULD_INCLUDE_DEPENDENCIES | FLAG_SKIP_IF_STORAGE_LOW;
            case ReasonMapping.REASON_CMDLINE:
            default:
                return FLAG_FOR_PRIMARY_DEX | FLAG_FOR_SECONDARY_DEX
                        | FLAG_SHOULD_INCLUDE_DEPENDENCIES;
        }
    }

    // Keep in sync with `PriorityClass` except for `PRIORITY_NONE`.
    // Keep this in sync with `ArtShellCommand.printHelp` except for 'PRIORITY_NONE'.

    /**
     * Initial value. Not expected.
     *
     * @hide
     */
    public static final int PRIORITY_NONE = -1;
    /** Indicates that the operation blocks boot. */
    public static final int PRIORITY_BOOT = PriorityClass.BOOT;
    /**
     * Indicates that a human is waiting on the result and the operation is more latency sensitive
     * than usual.
     */
    public static final int PRIORITY_INTERACTIVE_FAST = PriorityClass.INTERACTIVE_FAST;
    /** Indicates that a human is waiting on the result. */
    public static final int PRIORITY_INTERACTIVE = PriorityClass.INTERACTIVE;
    /** Indicates that the operation runs in background. */
    public static final int PRIORITY_BACKGROUND = PriorityClass.BACKGROUND;

    /**
     * Indicates the priority of an operation. The value affects the resource usage and the process
     * priority. A higher value may result in faster execution but may consume more resources and
     * compete for resources with other processes.
     *
     * @hide
     */
    // clang-format off
    @IntDef(prefix = "PRIORITY_", value = {
        PRIORITY_NONE,
        PRIORITY_BOOT,
        PRIORITY_INTERACTIVE_FAST,
        PRIORITY_INTERACTIVE,
        PRIORITY_BACKGROUND,
    })
    // clang-format on
    @Retention(RetentionPolicy.SOURCE)
    public @interface PriorityClassApi {}

    /** The job has been successfully scheduled. */
    public static final int SCHEDULE_SUCCESS = 0;

    /** @see JobScheduler#RESULT_FAILURE */
    public static final int SCHEDULE_JOB_SCHEDULER_FAILURE = 1;

    /** The job is disabled by the system property {@code pm.dexopt.disable_bg_dexopt}. */
    public static final int SCHEDULE_DISABLED_BY_SYSPROP = 2;

    /**
     * Indicates the result of scheduling a background dexopt job.
     *
     * @hide
     */
    // clang-format off
    @IntDef(prefix = "SCHEDULE_", value = {
        SCHEDULE_SUCCESS,
        SCHEDULE_JOB_SCHEDULER_FAILURE,
        SCHEDULE_DISABLED_BY_SYSPROP,
    })
    // clang-format on
    @Retention(RetentionPolicy.SOURCE)
    public @interface ScheduleStatus {}

    /**
     * The downgrade pass, run before the main pass. Only applicable to bg-dexopt.
     *
     * @hide
     */
    public static final int PASS_DOWNGRADE = 0;

    /**
     * The main pass.
     *
     * @hide
     */
    public static final int PASS_MAIN = 1;

    /**
     * The supplementary pass, run after the main pass, to take the opportunity to dexopt more
     * packages. Compared to the main pass, it uses different criteria to determine whether dexopt
     * is needed or not, but iterates over the same packages in the same order as the main pass (so
     * the logic in {@link ArtManagerLocal#getDefaultPackages} and {@link
     * ArtManagerLocal.BatchDexoptStartCallback} controls the packages here too.)
     *
     * Only applicable to bg-dexopt.
     *
     * @hide
     */
    public static final int PASS_SUPPLEMENTARY = 2;

    /**
     * Indicates the pass of a batch dexopt run.
     *
     * @hide
     */
    // clang-format off
    @IntDef(prefix = "PASS_", value = {
        PASS_DOWNGRADE,
        PASS_MAIN,
        PASS_SUPPLEMENTARY,
    })
    // clang-format on
    @Retention(RetentionPolicy.SOURCE)
    public @interface BatchDexoptPass {}

    /** @hide */
    public static final List<Integer> BATCH_DEXOPT_PASSES =
            List.of(PASS_DOWNGRADE, PASS_MAIN, PASS_SUPPLEMENTARY);

    private ArtFlags() {}
}
