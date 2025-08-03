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
package com.android.adservices.experimental;

import static com.android.compatibility.common.util.ShellIdentityUtils.invokeStaticMethodWithShellPermissions;

import android.provider.DeviceConfig;
import android.provider.DeviceConfig.Properties;
import android.util.ArrayMap;
import android.util.Log;

import com.android.adservices.common.Nullable;
import com.android.adservices.experimental.AbstractFlagsRouletteRunner.FlagState;
import com.android.adservices.experimental.AbstractFlagsRouletteRunner.FlagsManager;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;

// TODO(b/284971005): add unit test and/or move to module-utils
/** Implementation of {@link FlagsManager} that maps flags to {@link DeviceConfig} settings. */
public final class DeviceConfigFlagsManager implements FlagsManager {

    private static final String TAG = DeviceConfigFlagsManager.class.getSimpleName();

    // TODO(b/284971005): use DeviceConfigStateHelper instead of DeviceConfig directly - need to
    // fix its reset() method first

    private final String mNamespace;
    private final @Nullable Properties mInitialProperties;
    private final @Nullable Map<String, String> mInitialValues;

    // TODO(b/284971005): ideally reset() should just restore the initial properties, but that's not
    // working (i.e., they are not properly restored - need to figure out why)
    private static final boolean USE_PROPERTIES = false;

    public DeviceConfigFlagsManager(String namespace) {
        mNamespace = Objects.requireNonNull(namespace);
        if (USE_PROPERTIES) {
            mInitialProperties =
                    callWithDeviceConfigPermissions(
                            () -> DeviceConfig.getProperties(mNamespace, new String[] {}));
            mInitialValues = null;
        } else {
            mInitialProperties = null;
            mInitialValues = new ArrayMap<>();
        }
        Log.v(
                TAG,
                "DeviceConfigFlagsManager("
                        + namespace
                        + "): mInitialProperties="
                        + toString(mInitialProperties)
                        + ", mInitialValues="
                        + mInitialValues);
    }

    @Override
    public void setFlags(Collection<FlagState> flags) {
        if (USE_PROPERTIES) {
            // TODO(b/284971005): use stream below
            Properties.Builder builder = new Properties.Builder(mNamespace);
            for (FlagState flag : flags) {
                builder.setBoolean(flag.name, flag.value);
            }
            setProperties(builder.build());
            return;
        }

        for (FlagState flag : flags) {
            String oldValue = getStringFlag(flag.name);
            Log.v(TAG, "setFlags(): saving " + flag.name + "=" + oldValue);
            mInitialValues.put(flag.name, oldValue);
            setFlag(flag.name, flag.value);
        }
        Log.v(TAG, "setFlags(): mInitialValues=" + mInitialValues);
        return;
    }

    private void setFlag(String name, boolean value) {
        setFlag(name, Boolean.toString(value));
    }

    private void setFlag(String name, String value) {
        Log.v(TAG, "setFlag(): " + name + "->" + value);
        callWithDeviceConfigPermissions(
                () -> DeviceConfig.setProperty(mNamespace, name, value, /* makeDefault= */ false));
    }

    private void setOrDeleteFlag(String name, String value) {
        if (value == null) {
            deleteFlag(name);
            return;
        }
        setFlag(name, value);
    }

    private void deleteFlag(String name) {
        Log.v(TAG, "deleteFlag(): " + name);
        callWithDeviceConfigPermissions(() -> DeviceConfig.deleteProperty(mNamespace, name));
    }

    private String getStringFlag(String name) {
        return callWithDeviceConfigPermissions(
                () -> DeviceConfig.getString(mNamespace, name, /* defaultValue= */ null));
    }

    @Override
    public void resetFlags() {
        Log.v(TAG, "resetFlags()");

        if (USE_PROPERTIES) {
            setProperties(mInitialProperties);
            return;
        }
        mInitialValues
                .entrySet()
                .forEach((flag) -> setOrDeleteFlag(flag.getKey(), flag.getValue()));
    }

    private void setProperties(Properties properties) {
        Log.v(TAG, "setProperties(): " + toString(properties));
        boolean set = callWithDeviceConfigPermissions(() -> DeviceConfig.setProperties(properties));
        if (!set) {
            throw new IllegalStateException(
                    "Failed to set properties (" + toString(properties) + ")");
        }
    }

    private static String toString(@Nullable Properties properties) {
        if (properties == null) {
            return "null";
        }
        Set<String> keySet = properties.getKeyset();
        StringBuilder string =
                new StringBuilder("Properties[namespace=")
                        .append(properties.getNamespace())
                        .append("size=")
                        .append(keySet.size());
        if (!keySet.isEmpty()) {
            string.append(":");
            keySet.forEach(
                    k ->
                            string.append(' ')
                                    .append(k)
                                    .append('=')
                                    .append(properties.getString(k, "N/A"))
                                    .append(", "));
        }

        return string.append(']').toString();
    }

    public static <T> T callWithDeviceConfigPermissions(Callable<T> c) {
        return invokeStaticMethodWithShellPermissions(
                () -> {
                    try {
                        return c.call();
                    } catch (Exception e) {
                        throw new RuntimeException(
                                "Failed to call something with shell permissions: " + e);
                    }
                });
    }
}
