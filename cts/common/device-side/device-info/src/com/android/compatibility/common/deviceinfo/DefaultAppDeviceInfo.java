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
package com.android.compatibility.common.deviceinfo;

import android.content.ComponentName;

import com.android.compatibility.common.util.DeviceInfoStore;

/** DefaultAppDeviceInfo collector. */
public final class DefaultAppDeviceInfo extends DeviceInfo {

    private static final String CONFIG_QR_CODE_COMPONENT = "config_defaultQrCodeComponent";
    private static final String DEFAULT_QR_CODE_COMPONENT_CLASS_NAME =
            "default_qr_code_component_class_name";

    @Override
    protected void collectDeviceInfo(DeviceInfoStore store) throws Exception {
        final ComponentName defaultQrCodeComponent = getDefaultQrCodeComponent();
        String defaultQrCodeComponentClassName = "";
        if (defaultQrCodeComponent != null) {
            defaultQrCodeComponentClassName = defaultQrCodeComponent.getClassName();
        }
        store.addResult(DEFAULT_QR_CODE_COMPONENT_CLASS_NAME, defaultQrCodeComponentClassName);
    }

    private ComponentName getDefaultQrCodeComponent() {
        try {
            final String defaultQrCodeComponent = getRawDeviceConfig(CONFIG_QR_CODE_COMPONENT);
            return ComponentName.unflattenFromString(defaultQrCodeComponent);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Returns the value of a device configuration setting available in android.internal.R.* */
    private String getRawDeviceConfig(String name) {
        return getContext().getResources().getString(getDeviceResourcesIdentifier(name, "string"));
    }

    private int getDeviceResourcesIdentifier(String name, String type) {
        return getContext().getResources().getIdentifier(name, type, "android");
    }
}
