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
package com.android.adservices.common;

import static android.os.Build.VERSION.SDK_INT;

import static com.android.compatibility.common.util.ShellIdentityUtils.invokeStaticMethodWithShellPermissions;

import android.provider.DeviceConfig;

import com.android.adservices.common.AndroidSdk.Level;
import com.android.compatibility.common.util.ShellUtils;
import com.android.modules.utils.build.SdkLevel;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import java.util.concurrent.Callable;

/** Device-side implementation of {@link DeviceConfigHelper.Interface}. */
final class DeviceSideDeviceConfigHelper extends DeviceConfigHelper.Interface {

    DeviceSideDeviceConfigHelper(String namespace) {
        super(namespace, AndroidLogger.getInstance());
    }

    @Override
    public String get(String name, String defaultValue) {
        return callWithDeviceConfigPermissions(
                () -> DeviceConfig.getString(mNamespace, name, /* defaultValue= */ null));
    }

    // TODO(b/300136201): override non-async methods to use a DeviceConfig listener

    @Override
    public boolean asyncSet(String name, String value) {
        mLog.v("asyncSet(%s=%s)", name, value);
        return callWithDeviceConfigPermissions(
                () -> DeviceConfigUtil.setDeviceConfigFlag(mNamespace, name, value));
    }

    @Override
    public boolean asyncDelete(String name) {
        mLog.v("asyncDelete(%s)", name);

        if (SdkLevel.isAtLeastT()) {
            return callWithDeviceConfigPermissions(
                    () -> DeviceConfig.deleteProperty(mNamespace, name));
        }
        // Use shell command instead
        return super.asyncDelete(name);
    }

    @Override
    public Level getDeviceApiLevel() {
        return Level.forLevel(SDK_INT);
    }

    @Override
    @FormatMethod
    protected String runShellCommand(@FormatString String cmdFmt, @Nullable Object... cmdArgs) {
        return ShellUtils.runShellCommand(cmdFmt, cmdArgs);
    }

    static <T> T callWithDeviceConfigPermissions(Callable<T> c) {
        return invokeStaticMethodWithShellPermissions(
                () -> {
                    try {
                        return c.call();
                    } catch (Exception e) {
                        throw new RuntimeException(
                                "Failed to call something with Shell permissions: " + e);
                    }
                });
    }
}
