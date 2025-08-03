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

import android.adservices.ondevicepersonalization.aidl.IOnDevicePersonalizationManagingService;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Trace;

/** Implementation of OnDevicePersonalization Service */
public class OnDevicePersonalizationManagingServiceImpl extends Service {
    private IOnDevicePersonalizationManagingService.Stub mBinder;

    @Override
    public void onCreate() {
        Trace.beginSection("OdpManagingService#Initialization");
        mBinder = new OnDevicePersonalizationManagingServiceDelegate(this);
        Trace.endSection();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}
