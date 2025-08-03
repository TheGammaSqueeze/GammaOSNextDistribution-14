/**
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

package com.android.telephony.testimsmediahal;

import android.app.Service;
import android.content.Intent;
import android.hardware.radio.ims.media.IImsMedia;
import android.os.IBinder;
import android.util.Log;

/**
 * Creating ImeMedia service for implementation of ImsMedia HAL APIs.
 * Service used to have connection with {@link AudioOffloadService}.
 */

public class ImsMediaHALtestService extends Service {
    private static final String SERVICE_TAG = "ImsMediaHALtestService";
    private static IImsMedia.Stub sIImsMediaBinder;

    public static IImsMedia.Stub getInstance() {
        if (sIImsMediaBinder == null) {
            sIImsMediaBinder = new IImsMediaImpl();
        }
        return sIImsMediaBinder;
    }

    @Override
    public void onCreate() {
        Log.d(SERVICE_TAG, "onCreate");

    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(SERVICE_TAG, Thread.currentThread().getName() + " onBind");
        return getInstance();
    }

    @Override
    public void onDestroy() {
        Log.d(SERVICE_TAG, "onDestroy");
    }
}
