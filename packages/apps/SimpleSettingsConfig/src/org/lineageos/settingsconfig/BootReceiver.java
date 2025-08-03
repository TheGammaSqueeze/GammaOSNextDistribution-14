/*
 * Copyright (C) 2020 The Proton AOSP Project
 * Copyright (C) 2024 The LineageOS Project
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

package org.lineageos.settingsconfig;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "SimpleSettingsConfig";

    @Override
    public void onReceive(Context context, Intent intent) {
        new Thread(() -> {
            Log.i(TAG, "Updating settings storage at boot");
            updateDefaultConfigs(context);
        }).start();
    }

    private void updateDefaultConfigs(Context context) {
        updateConfig(context, R.array.configs_base);
        updateConfig(context, R.array.configs_device);
    }

    private void updateConfig(Context context, int configArray) {
        // Set current properties
        String[] rawProperties = context.getResources().getStringArray(configArray);
        for (String property : rawProperties) {
            // Format: namespace/key=value
            String[] kv = property.split("=");
            String fullKey = kv[0];
            String[] nsKey = fullKey.split("/");

            String namespace = nsKey[0];
            String key = nsKey[1];
            String value = "";
            if (kv.length > 1) {
                value = kv[1];
            }

            switch (namespace) {
                case "global":
                    Settings.Global.putString(context.getContentResolver(), key, value);
                    break;

                case "secure":
                    Settings.Secure.putString(context.getContentResolver(), key, value);
                    break;

                case "system":
                    Settings.System.putString(context.getContentResolver(), key, value);
                    break;

                default:
                    Log.e(TAG, "Namespace '" + namespace + "' is invalid.");
            }
        }
    }
}
