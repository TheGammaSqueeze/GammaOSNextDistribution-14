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

package com.android.adservices.service;

import static com.android.adservices.service.CommonFlagsConstants.KEY_ADSERVICES_SHELL_COMMAND_ENABLED;

import android.annotation.Nullable;
import android.provider.DeviceConfig;

import java.io.PrintWriter;

/**
 * Common flags Implementation that delegates to DeviceConfig.
 *
 * @hide
 */
public abstract class CommonPhFlags implements CommonFlags {

    @Override
    public boolean getAdServicesShellCommandEnabled() {
        return getAdServicesShellCommandEnabledFromDeviceConfig();
    }

    static boolean getAdServicesShellCommandEnabledFromDeviceConfig() {
        return DeviceConfig.getBoolean(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_ADSERVICES_SHELL_COMMAND_ENABLED,
                /* defaultValue */ ADSERVICES_SHELL_COMMAND_ENABLED);
    }

    @Override
    public void dump(PrintWriter writer, @Nullable String[] args) {
        writer.println(
                "\t"
                        + KEY_ADSERVICES_SHELL_COMMAND_ENABLED
                        + " = "
                        + getAdServicesShellCommandEnabledFromDeviceConfig());
    }
}
