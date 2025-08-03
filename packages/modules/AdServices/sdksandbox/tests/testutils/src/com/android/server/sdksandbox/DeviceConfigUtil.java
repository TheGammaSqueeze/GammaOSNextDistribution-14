/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.sdksandbox;

import android.provider.DeviceConfig;
import android.util.ArrayMap;

import java.util.Map;

/** Util class for {@link DeviceConfig} */
public class DeviceConfigUtil {
    private final SdkSandboxSettingsListener mSdkSandboxSettingsListener;

    DeviceConfigUtil(SdkSandboxSettingsListener sdkSandboxSettingsListener) {
        mSdkSandboxSettingsListener = sdkSandboxSettingsListener;
    }

    /** Set value for a {@link DeviceConfig.Properties} */
    public void setDeviceConfigProperty(String property, String value) {
        // Explicitly calling the onPropertiesChanged method to avoid race conditions
        if (value == null) {
            // Map.of() does not handle null, so we need to use an ArrayMap to delete a property
            ArrayMap<String, String> properties = new ArrayMap<>();
            properties.put(property, null);
            mSdkSandboxSettingsListener.onPropertiesChanged(
                    new DeviceConfig.Properties(DeviceConfig.NAMESPACE_ADSERVICES, properties));
        } else {
            mSdkSandboxSettingsListener.onPropertiesChanged(
                    new DeviceConfig.Properties(
                            DeviceConfig.NAMESPACE_ADSERVICES, Map.of(property, value)));
        }
    }
}
