/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tv.settings.connectivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * The {@link BroadcastReceiver} for detecting and recording ethernet connections
 */
public class EthernetDetectionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!NetworkChangeDetectionConfigs.ACTION_ETHERNET_DETECTED.equals(intent.getAction())) {
            return;
        }
        SharedPreferences networkChangePref = context.getSharedPreferences(
                NetworkChangeDetectionConfigs.SHARED_PREFERENCES_NAME,
                Context.MODE_PRIVATE);
        boolean isEthernetRecorded = networkChangePref.getBoolean(
                NetworkChangeDetectionConfigs.ETHERNET_RECORD_PREFERENCE_KEY, false);

        if (!isEthernetRecorded) {
            SharedPreferences.Editor networkChangePrefEditor = networkChangePref.edit();
            int currentCount = networkChangePref.getInt(
                    NetworkChangeDetectionConfigs.PREFERENCE_KEY, 0);
            networkChangePrefEditor.putInt(NetworkChangeDetectionConfigs.PREFERENCE_KEY,
                    currentCount + 1);
            networkChangePrefEditor.putBoolean(
                    NetworkChangeDetectionConfigs.ETHERNET_RECORD_PREFERENCE_KEY, true);
            networkChangePrefEditor.commit();

            // Notify NetworkChange observers of change.
            context.getContentResolver().notifyChange(
                    NetworkChangeDetectionConfigs.CONTENT_URI, null);
        }
    }
}
