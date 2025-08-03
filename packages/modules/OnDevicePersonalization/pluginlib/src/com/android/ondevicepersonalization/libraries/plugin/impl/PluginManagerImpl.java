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

package com.android.ondevicepersonalization.libraries.plugin.impl;

import android.content.Context;

import com.android.ondevicepersonalization.libraries.plugin.PluginController;
import com.android.ondevicepersonalization.libraries.plugin.PluginInfo;
import com.android.ondevicepersonalization.libraries.plugin.PluginManager;
import com.android.ondevicepersonalization.libraries.plugin.internal.PluginExecutorServiceProviderImpl;

/**
 * Used by clients to create new plugins and receive {@link PluginController} interfaces to control
 * them.
 */
public class PluginManagerImpl implements PluginManager {
    private final Context mApplicationContext;

    public PluginManagerImpl(Context applicationContext) {
        this.mApplicationContext = applicationContext;
    }

    @Override
    public PluginController createPluginController(PluginInfo info) {
        return new PluginControllerImpl(
                mApplicationContext,
                new PluginExecutorServiceProviderImpl(mApplicationContext),
                info);
    }
}
