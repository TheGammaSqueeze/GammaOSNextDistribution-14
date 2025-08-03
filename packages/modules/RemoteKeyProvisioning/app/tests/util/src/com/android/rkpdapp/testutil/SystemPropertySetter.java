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

package com.android.rkpdapp.testutil;

import android.os.SystemProperties;

public class SystemPropertySetter implements AutoCloseable {
    final String mKey;
    final String mOriginalValue;

    public static SystemPropertySetter setHostname(String value) {
        return new SystemPropertySetter("remote_provisioning.hostname", value);
    }

    public static SystemPropertySetter setRkpOnly(String instanceName) {
        switch (instanceName) {
            case "default":
                return new SystemPropertySetter("remote_provisioning.tee.rkp_only", "true");
            case "strongbox":
                return new SystemPropertySetter("remote_provisioning.strongbox.rkp_only", "true");
            default:
                throw new IllegalArgumentException("Unexpected instance: " + instanceName);
        }
    }

    private SystemPropertySetter(String key, String value) {
        mKey = key;
        mOriginalValue = SystemProperties.get(key, "");
        SystemProperties.set(key, value);
    }

    public void close() {
        SystemProperties.set(mKey, mOriginalValue);
    }
}
