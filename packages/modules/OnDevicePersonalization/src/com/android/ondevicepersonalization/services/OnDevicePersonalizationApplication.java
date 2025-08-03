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

package com.android.ondevicepersonalization.services;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Application;
import android.content.Context;

import com.android.ondevicepersonalization.libraries.plugin.PluginApplication;
import com.android.ondevicepersonalization.libraries.plugin.PluginHost;
import com.android.ondevicepersonalization.services.process.OnDevicePersonalizationPluginHost;

/** The Application class for OnDevicePersonalization. */
public final class OnDevicePersonalizationApplication
        extends Application implements PluginApplication {
    @NonNull private static Context sApplicationContext;

    @Override public @Nullable PluginHost getPluginHost() {
        return new OnDevicePersonalizationPluginHost(this);
    }

    @Override public void onCreate() {
        super.onCreate();
        sApplicationContext = getApplicationContext();
    }

    @NonNull public static Context getAppContext() {
        return sApplicationContext;
    }
}
