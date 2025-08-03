/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.compatibility.common.util;

import static org.junit.Assert.assertTrue;

import android.provider.DeviceConfig;
import android.util.ArrayMap;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.compatibility.common.util.TestUtils.RunnableWithThrow;

import java.util.Objects;

/**
 * Helper to automatically save multiple existing DeviceConfig values, change them during tests, and
 * restore the original values after the test.
 */
public class DeviceConfigStateHelper implements AutoCloseable {
    private final String mNamespace;
    @GuardedBy("mOriginalValues")
    private final ArrayMap<String, String> mOriginalValues = new ArrayMap<>();

    /**
     * @param namespace DeviceConfig namespace.
     */
    public DeviceConfigStateHelper(@NonNull String namespace) {
        mNamespace = Objects.requireNonNull(namespace);
    }

    private void maybeCacheOriginalValueLocked(String key) {
        if (!mOriginalValues.containsKey(key)) {
            // Only save the current value if we haven't changed it.
            final String ogValue = SystemUtil.runWithShellPermissionIdentity(
                    () -> DeviceConfig.getProperty(mNamespace, key));
            mOriginalValues.put(key, ogValue);
        }
    }

    public void set(@NonNull String key, @Nullable String value) {
        synchronized (mOriginalValues) {
            maybeCacheOriginalValueLocked(key);
        }
        SystemUtil.runWithShellPermissionIdentity(
                () -> assertTrue(
                        DeviceConfig.setProperty(mNamespace, key, value, /* makeDefault */false)));
    }

    /**
     * Run a Runnable, with DeviceConfig.setSyncDisabledMode(SYNC_DISABLED_MODE_NONE),
     * with all the shell permissions.
     */
    public static void callWithSyncEnabledWithShellPermissions(RunnableWithThrow r) {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            final String originalSyncMode = ShellUtils.runShellCommand(
                    "device_config get_sync_disabled_for_tests");
            try {
                // TODO: Use DeviceConfig.setSyncDisabledMode, once the SYNC_* constants
                // are exposed.
                ShellUtils.runShellCommand("cmd device_config set_sync_disabled_for_tests none");

                r.run();
            } finally {
                ShellUtils.runShellCommand(
                        "device_config set_sync_disabled_for_tests %s", originalSyncMode);
            }
        });
    }

    public void set(@NonNull DeviceConfig.Properties properties) {
        synchronized (mOriginalValues) {
            for (String key : properties.getKeyset()) {
                maybeCacheOriginalValueLocked(key);
            }
        }
        callWithSyncEnabledWithShellPermissions(
                () -> assertTrue(DeviceConfig.setProperties(properties)));
    }

    public void restoreOriginalValues() {
        final DeviceConfig.Properties.Builder builder =
                new DeviceConfig.Properties.Builder(mNamespace);
        synchronized (mOriginalValues) {
            for (int i = 0; i < mOriginalValues.size(); ++i) {
                builder.setString(mOriginalValues.keyAt(i), mOriginalValues.valueAt(i));
            }
            mOriginalValues.clear();
        }
        callWithSyncEnabledWithShellPermissions(
                () -> assertTrue(DeviceConfig.setProperties(builder.build())));
    }

    @Override
    public void close() throws Exception {
        restoreOriginalValues();
    }
}
