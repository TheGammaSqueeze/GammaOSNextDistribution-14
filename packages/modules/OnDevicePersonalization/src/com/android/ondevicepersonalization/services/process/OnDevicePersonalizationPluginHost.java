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

package com.android.ondevicepersonalization.services.process;

import android.content.Context;

import com.android.ondevicepersonalization.libraries.plugin.PluginHost;

import com.google.common.collect.ImmutableSet;

/** Plugin Support code shared between the managing process and the isolated process. */
public class OnDevicePersonalizationPluginHost implements PluginHost {
    public OnDevicePersonalizationPluginHost(Context applicationContext) {}

    @Override
    public ImmutableSet<String> getClassLoaderAllowedPackages(String pluginId) {
        return ImmutableSet.of(
                "com.android.ondevicepersonalization.services",
                "com.android.ondevicepersonalization.libraries");
    }
}
