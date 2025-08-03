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
package com.android.server.adservices;

import android.annotation.NonNull;
import android.provider.DeviceConfig;

import com.android.adservices.service.CommonPhFlags;

/**
 * Flags Implementation that delegates to DeviceConfig.
 *
 * @hide
 */
public final class PhFlags extends CommonPhFlags implements Flags {

    private static final PhFlags sSingleton = new PhFlags();

    /** Returns the singleton instance of the PhFlags. */
    @NonNull
    public static PhFlags getInstance() {
        return sSingleton;
    }

    /*
     * Keys for ALL the flags stored in DeviceConfig.
     */
    // Adservices System Service enable status keys.
    static final String KEY_ADSERVICES_SYSTEM_SERVICE_ENABLED = "adservice_system_service_enabled";

    @Override
    public boolean getAdServicesSystemServiceEnabled() {
        // The priority of applying the flag values: PH (DeviceConfig) and then hard-coded value.
        return DeviceConfig.getBoolean(
                DeviceConfig.NAMESPACE_ADSERVICES,
                /* flagName */ KEY_ADSERVICES_SYSTEM_SERVICE_ENABLED,
                /* defaultValue */ ADSERVICES_SYSTEM_SERVICE_ENABLED);
    }
}
