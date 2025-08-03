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

package com.android.fakesystemapp.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import java.util.Objects;

/**
 * Home Activity that gets launched before user has unlocked device.
 *
 * Effectively replaces and mirrors logic in  com.android.settings.FallbackHome, so device
 * boots when Settings app is removed from image.
 */
public final class FallbackHome extends Activity {

    private static final String TAG = "FallbackHome";

    private Handler mHandler;
    private BroadcastReceiver mReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                maybeFinish();
            }
        };

        mHandler = new Handler(getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                maybeFinish();
            }
        };

        // TODO: consider adding logic to make screen black if not provisioned

        registerReceiver(mReceiver, new IntentFilter(Intent.ACTION_USER_UNLOCKED));
        maybeFinish();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }

    private void maybeFinish() {
        if (getSystemService(UserManager.class).isUserUnlocked()) {
            final Intent homeIntent = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME);
            final ResolveInfo homeInfo = getPackageManager().resolveActivity(homeIntent, 0);
            if (Objects.equals(this.getClass().getName(), homeInfo.activityInfo.name)) {
                Log.d(TAG, "User unlocked but no home; let's hope someone enables one soon?");
                mHandler.sendEmptyMessageDelayed(0, 500);
            } else {
                Log.d(TAG, String.format("User unlocked and real home %s found; let's go!", homeInfo.activityInfo.name));
                getSystemService(PowerManager.class).userActivity(
                        SystemClock.uptimeMillis(), false);
                finish();
            }
        }
    }
}
