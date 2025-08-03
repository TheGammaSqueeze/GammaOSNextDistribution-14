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

import static com.android.server.art.model.ArtFlags.PriorityClassApi;

import android.annotation.NonNull;
import android.annotation.StringDef;
import android.annotation.SystemApi;
import android.os.Build;
import android.os.SystemProperties;
import android.text.TextUtils;

import androidx.annotation.RequiresApi;

import com.android.server.art.model.ArtFlags;
import com.android.server.pm.PackageManagerLocal;

import dalvik.system.DexFile;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Set;

/**
 * Maps a compilation reason to a compiler filter and a priority class.
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.SYSTEM_SERVER)
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
public class ReasonMapping {
    private ReasonMapping() {}

    // Keep this in sync with `ArtShellCommand.printHelp` except for 'inactive'.

    /** Dexopting apps on the first boot after flashing or factory resetting the device. */
    public static final String REASON_FIRST_BOOT = "first-boot";
    /** Dexopting apps on the next boot after an OTA. */
    public static final String REASON_BOOT_AFTER_OTA = "boot-after-ota";
    /** Dexopting apps on the next boot after a mainline update. */
    public static final String REASON_BOOT_AFTER_MAINLINE_UPDATE = "boot-after-mainline-update";
    /** Installing an app after user presses the "install"/"update" button. */
    public static final String REASON_INSTALL = "install";
    /** Dexopting apps in the background. */
    public static final String REASON_BG_DEXOPT = "bg-dexopt";
    /** Invoked by cmdline. */
    public static final String REASON_CMDLINE = "cmdline";
    /** Downgrading the compiler filter when an app is not used for a long time. */
    public static final String REASON_INACTIVE = "inactive";

    // Reasons for Play Install Hints (go/install-hints).
    public static final String REASON_INSTALL_FAST = "install-fast";
    public static final String REASON_INSTALL_BULK = "install-bulk";
    public static final String REASON_INSTALL_BULK_SECONDARY = "install-bulk-secondary";
    public static final String REASON_INSTALL_BULK_DOWNGRADED = "install-bulk-downgraded";
    public static final String REASON_INSTALL_BULK_SECONDARY_DOWNGRADED =
            "install-bulk-secondary-downgraded";

    /** @hide */
    public static final Set<String> REASONS_FOR_INSTALL = Set.of(REASON_INSTALL,
            REASON_INSTALL_FAST, REASON_INSTALL_BULK, REASON_INSTALL_BULK_SECONDARY,
            REASON_INSTALL_BULK_DOWNGRADED, REASON_INSTALL_BULK_SECONDARY_DOWNGRADED);

    // Keep this in sync with `ArtShellCommand.printHelp`.
    /** @hide */
    public static final Set<String> BATCH_DEXOPT_REASONS = Set.of(REASON_FIRST_BOOT,
            REASON_BOOT_AFTER_OTA, REASON_BOOT_AFTER_MAINLINE_UPDATE, REASON_BG_DEXOPT);

    /**
     * Reasons for {@link ArtManagerLocal#dexoptPackages}.
     *
     * @hide
     */
    // clang-format off
    @StringDef(prefix = "REASON_", value = {
        REASON_FIRST_BOOT,
        REASON_BOOT_AFTER_OTA,
        REASON_BOOT_AFTER_MAINLINE_UPDATE,
        REASON_BG_DEXOPT,
    })
    // clang-format on
    @Retention(RetentionPolicy.SOURCE)
    public @interface BatchDexoptReason {}

    /**
     * Reasons for {@link ArtManagerLocal#onBoot(String, Executor, Consumer<OperationProgress>)}.
     *
     * @hide
     */
    // clang-format off
    @StringDef(prefix = "REASON_", value = {
        REASON_FIRST_BOOT,
        REASON_BOOT_AFTER_OTA,
        REASON_BOOT_AFTER_MAINLINE_UPDATE,
    })
    // clang-format on
    @Retention(RetentionPolicy.SOURCE)
    public @interface BootReason {}

    /**
     * Loads the compiler filter from the system property for the given reason and checks for
     * validity.
     *
     * @throws IllegalArgumentException if the reason is invalid
     * @throws IllegalStateException if the system property value is invalid
     *
     * @hide
     */
    @NonNull
    public static String getCompilerFilterForReason(@NonNull String reason) {
        String value = SystemProperties.get("pm.dexopt." + reason);
        if (TextUtils.isEmpty(value)) {
            throw new IllegalArgumentException("No compiler filter for reason '" + reason + "'");
        }
        if (!Utils.isValidArtServiceCompilerFilter(value)) {
            throw new IllegalStateException(
                    "Got invalid compiler filter '" + value + "' for reason '" + reason + "'");
        }
        return value;
    }

    /**
     * Loads the compiler filter from the system property for:
     * - shared libraries
     * - apps used by other apps without a dex metadata file
     *
     * @throws IllegalStateException if the system property value is invalid
     *
     * @hide
     */
    @NonNull
    public static String getCompilerFilterForShared() {
        // "shared" is technically not a compilation reason, but the compiler filter is defined as a
        // system property as if "shared" is a reason.
        String value = getCompilerFilterForReason("shared");
        if (DexFile.isProfileGuidedCompilerFilter(value)) {
            throw new IllegalStateException(
                    "Compiler filter for 'shared' must not be profile guided, got '" + value + "'");
        }
        return value;
    }

    /**
     * Returns the priority for the given reason.
     *
     * @throws IllegalArgumentException if the reason is invalid
     * @see PriorityClassApi
     *
     * @hide
     */
    public static @PriorityClassApi byte getPriorityClassForReason(@NonNull String reason) {
        switch (reason) {
            case REASON_FIRST_BOOT:
            case REASON_BOOT_AFTER_OTA:
            case REASON_BOOT_AFTER_MAINLINE_UPDATE:
                return ArtFlags.PRIORITY_BOOT;
            case REASON_INSTALL_FAST:
                return ArtFlags.PRIORITY_INTERACTIVE_FAST;
            case REASON_INSTALL:
            case REASON_CMDLINE:
                return ArtFlags.PRIORITY_INTERACTIVE;
            case REASON_BG_DEXOPT:
            case REASON_INACTIVE:
            case REASON_INSTALL_BULK:
            case REASON_INSTALL_BULK_SECONDARY:
            case REASON_INSTALL_BULK_DOWNGRADED:
            case REASON_INSTALL_BULK_SECONDARY_DOWNGRADED:
                return ArtFlags.PRIORITY_BACKGROUND;
            default:
                throw new IllegalArgumentException("No priority class for reason '" + reason + "'");
        }
    }

    /**
     * Loads the concurrency from the system property, for batch dexopt ({@link
     * ArtManagerLocal#dexoptPackages}). The default is tuned to strike a good balance between
     * device load and dexopt coverage, depending on the situation.
     *
     * @hide
     */
    public static int getConcurrencyForReason(@NonNull @BatchDexoptReason String reason) {
        return SystemProperties.getInt("persist.device_config.runtime." + reason + "_concurrency",
                SystemProperties.getInt("pm.dexopt." + reason + ".concurrency",
                        reason.equals(REASON_BG_DEXOPT) ? 4 : 1 /* def */));
    }
}
