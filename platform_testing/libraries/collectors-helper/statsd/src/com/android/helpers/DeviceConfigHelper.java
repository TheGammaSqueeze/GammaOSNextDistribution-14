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

package com.android.helpers;

import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import java.io.IOException;

/** Basic utilities to configure metric collection in tests through Device Config API. */
public class DeviceConfigHelper {
    /** Runs a shell command to set the configuration value in Device Config. */
    public static String setConfigValue(String namespace, String setting, String value) {
        try {
            UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
            String command = String.format("device_config put %s %s %s", namespace, setting, value);
            return device.executeShellCommand(command);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
