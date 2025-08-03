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

package com.android.ondevicepersonalization.libraries.plugin.internal;

import android.os.Bundle;

import com.android.ondevicepersonalization.libraries.plugin.internal.IPluginCallback;
import com.android.ondevicepersonalization.libraries.plugin.internal.IPluginStateCallback;
import com.android.ondevicepersonalization.libraries.plugin.internal.PluginInfoInternal;

/**
  * Service for loading & executing {@link Plugin} implementations.
  */
interface IPluginExecutorService {
  oneway void load(in PluginInfoInternal info, in IPluginCallback pluginCallback, in Bundle pluginContextInitData) = 0;
  oneway void execute(in String pluginName, in Bundle input, in IPluginCallback pluginCallback) = 1;
  oneway void unload(in String pluginName, in IPluginCallback pluginCallback) = 2;
  oneway void checkPluginState(in String pluginName, in IPluginStateCallback stateCallback) = 3;
}