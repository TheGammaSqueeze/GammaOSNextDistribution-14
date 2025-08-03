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

package com.android.bedstead.nene.flags;

import static android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE;

import static com.android.bedstead.nene.permissions.CommonPermissions.READ_DEVICE_CONFIG;
import static com.android.bedstead.nene.permissions.CommonPermissions.WRITE_ALLOWLISTED_DEVICE_CONFIG;

import android.provider.DeviceConfig;

import androidx.annotation.Nullable;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.AdbException;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.utils.ShellCommand;
import com.android.bedstead.nene.utils.Versions;

/** Test APIs related to flags. */
public final class Flags {

    public static final Flags sInstance = new Flags();

    public static final String ENABLED_VALUE = "true";
    public static final String DISABLED_VALUE = null;

    private Flags() {
    }

    /**
     * Set whether bulk modifications to flags should be allowed.
     *
     * <p>This should generally be disabled before tests to avoid changing flags changing behavior
     * during tests.
     *
     * @see #getFlagSyncEnabled()
     */
    public void setFlagSyncEnabled(boolean enabled) {
        Versions.requireMinimumVersion(Versions.T);
        ShellCommand.builder("device_config")
                .addOperand("set_sync_disabled_for_tests")
                .addOperand(enabled ? "none" : "persistent")
                .allowEmptyOutput(true)
                .validate(String::isEmpty)
                .executeOrThrowNeneException("Error setting flag sync enabled");
    }

    /**
     * Get whether bulk modifications to flags are allowed.
     *
     * @see #setFlagSyncEnabled(boolean)
     */
    public boolean getFlagSyncEnabled() {
        if (!Versions.meetsMinimumSdkVersionRequirement(Versions.T)) {
            return true;
        }
        try {
            return ShellCommand.builder("device_config")
                    .addOperand("get_sync_disabled_for_tests")
                    .executeAndParseOutput(s -> "none".equals(s.strip()));
        } catch (AdbException e) {
            throw new NeneException("Error getting flag sync enabled", e);
        }
    }

    /**
     * Set the value of a flag.
     *
     * <p>Before doing this, you may want to use {@link #setFlagSyncEnabled(boolean)} so it is not
     * replaced by a bulk update.
     */
    public void set(String namespace, String key, @Nullable String value) {
        if (Versions.meetsMinimumSdkVersionRequirement(UPSIDE_DOWN_CAKE)) {
            try (PermissionContext p = TestApis.permissions()
                    .withPermission(WRITE_ALLOWLISTED_DEVICE_CONFIG)) {
                DeviceConfig.setProperty(namespace, key, value, /* makeDefault= */ false);
            }
        }
    }

    /**
     * Get the value of a flag.
     */
    @Nullable
    public String get(String namespace, String key) {
        try (PermissionContext p = TestApis.permissions().withPermission(READ_DEVICE_CONFIG)) {
            return DeviceConfig.getProperty(namespace, key);
        }
    }

    /**
     * Set a feature flag as enabled or disabled.
     *
     * <p>When enabled, the value is set to "true", when disabled it is set to {@code null}.
     */
    public void setEnabled(String namespace, String key, boolean enabled) {
        set(namespace, key, enabled ? ENABLED_VALUE : DISABLED_VALUE);
    }

    /**
     * Check if a feature flag is enabled.
     *
     * <p>A feature is considered enabled if the value is "true".
     */
    public boolean isEnabled(String namespace, String key) {
        return ENABLED_VALUE.equals(get(namespace, key));
    }
}
