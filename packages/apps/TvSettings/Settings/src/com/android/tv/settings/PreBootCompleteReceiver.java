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

package com.android.tv.settings;

import static android.hardware.SensorPrivacyManager.TOGGLE_TYPE_SOFTWARE;
import static android.provider.Settings.Global.RECEIVE_EXPLICIT_USER_INTERACTION_AUDIO_ENABLED;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.SensorPrivacyManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;


/** The {@link BroadcastReceiver} for performing actions upon device boot after OTA. */
public class PreBootCompleteReceiver extends BroadcastReceiver {
    private static final String TAG = "PreBootCompleteReceiver";
    private static final boolean DEBUG = Build.IS_DEBUGGABLE;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_PRE_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "onReceive PRE_BOOT_COMPLETED");
        }
        try {
            Settings.Global.getInt(
                    context.getContentResolver(),
                    RECEIVE_EXPLICIT_USER_INTERACTION_AUDIO_ENABLED);
        } catch (Settings.SettingNotFoundException e) {
            // If there is no default value for "mic on remote" toggle, set the value as the
            // opposite of mic sensor privacy enable state.
            boolean micPrivacyEnabled = context.getSystemService(SensorPrivacyManager.class)
                    .isSensorPrivacyEnabled(
                            TOGGLE_TYPE_SOFTWARE, SensorPrivacyManager.Sensors.MICROPHONE);
            Settings.Global.putInt(
                    context.getContentResolver(),
                    RECEIVE_EXPLICIT_USER_INTERACTION_AUDIO_ENABLED,
                    micPrivacyEnabled ? 0 : 1);
            if (DEBUG) {
                Log.d(TAG,
                        "Set default state for RECEIVE_EXPLICIT_USER_INTERACTION_AUDIO_ENABLED: "
                                + (micPrivacyEnabled ? 0 : 1));

            }
        }
    }
}
