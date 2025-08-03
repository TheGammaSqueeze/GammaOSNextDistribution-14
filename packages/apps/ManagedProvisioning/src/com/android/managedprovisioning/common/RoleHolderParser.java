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

package com.android.managedprovisioning.common;

import android.annotation.Nullable;
import android.text.TextUtils;

/**
 * Parser for the device manager role holder config.
 */
class RoleHolderParser {
    /**
     * Retrieves the package name for a given {@code deviceManagerConfig}.
     *
     * <p>Valid configs look like:
     * <ul>
     *     <li>{@code com.package.name}</li>
     *     <li>{@code com.package.name:<SHA256 checksum>}</li>
     * </ul>
     *
     * <p>If the supplied {@code deviceManagerConfig} is {@code null} or empty, returns
     * {@code null}.
     */
    static @Nullable String getRoleHolderPackage(@Nullable String deviceManagerConfig) {
        if (TextUtils.isEmpty(deviceManagerConfig)) {
            return null;
        }
        if (deviceManagerConfig.contains(":")) {
            return deviceManagerConfig.split(":")[0];
        }
        return deviceManagerConfig;
    }
}
