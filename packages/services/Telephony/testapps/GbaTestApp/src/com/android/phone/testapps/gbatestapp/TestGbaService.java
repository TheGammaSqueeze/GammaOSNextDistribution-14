/*
 * Copyright 2020 The Android Open Source Project
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

package com.android.phone.testapps.gbatestapp;

import android.annotation.NonNull;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.telephony.gba.GbaService;
import android.util.Log;

/** test GbaService to be used for Gba api test */
public class TestGbaService extends GbaService {

    private static final String TAG = "TestGbaService";

    private Settings mSettings;

    @Override
    public void onCreate() {
        Log.i(TAG, "TestGbaService: onCreate");
        mSettings = Settings.getSettings(getApplicationContext());
    }

    @Override
    public void onAuthenticationRequest(int subId, int token, int appType,
            @NonNull Uri nafUrl, @NonNull byte[] securityProtocol, boolean forceBootStrapping) {
        boolean isSuccess = mSettings.getAuthResult();
        int reason = mSettings.getFailReason();
        String key = mSettings.getGbaKey();
        String btid = mSettings.getBTid();

        if (isSuccess) {
            reportKeysAvailable(token, key.getBytes(), btid);
        } else {
            reportAuthenticationFailure(token, reason);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind intent:" + intent);
        return super.onBind(intent);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy!");
        super.onDestroy();
    }
}
