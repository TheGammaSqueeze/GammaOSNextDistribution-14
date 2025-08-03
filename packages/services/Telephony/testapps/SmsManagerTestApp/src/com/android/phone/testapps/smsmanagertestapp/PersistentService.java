/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.phone.testapps.smsmanagertestapp;

import android.content.Intent;
import android.service.carrier.CarrierMessagingClientService;
import android.util.Log;

/**
 * A test persistent service that should be started by the framework when this app becomes the
 * default SMS app and destroyed when it is removed. See {@link CarrierMessagingClientService} for
 * more information.
 */
public class PersistentService extends CarrierMessagingClientService {

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i("SmsTestApp", "onCreate");
    }

    @Override
    public void onDestroy() {
        Log.i("SmsTestApp", "onDestroy");
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i("SmsTestApp", "onUnbind");
        return false;
    }
}
