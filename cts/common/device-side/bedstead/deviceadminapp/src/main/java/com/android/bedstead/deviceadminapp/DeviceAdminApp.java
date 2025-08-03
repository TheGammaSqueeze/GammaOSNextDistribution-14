/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.bedstead.deviceadminapp;

import android.app.admin.DelegatedAdminReceiver;
import android.app.admin.DeviceAdminReceiver;
import android.content.ComponentName;
import android.content.Context;

import com.android.eventlib.premade.EventLibDelegatedAdminReceiver;
import com.android.eventlib.premade.EventLibDeviceAdminReceiver;

/**
 * Entry point for Device Admin App.
 */
public class DeviceAdminApp {

    /** Get the {@link ComponentName} for the {@link DeviceAdminReceiver} subclass. */
    public static ComponentName deviceAdminComponentName(Context context) {
        return new ComponentName(
                context.getPackageName(), EventLibDeviceAdminReceiver.class.getName());
    }

    /** Get the {@link ComponentName} for the {@link DelegatedAdminReceiver} subclass. */
    public static ComponentName delegatedAdminComponentName(Context context) {
        return new ComponentName(
                context.getPackageName(), EventLibDelegatedAdminReceiver.class.getName());
    }
}
