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
package com.android.provision;

import android.content.ContentResolver;
import android.provider.Settings;
import android.util.Log;

/**
 * Utility helpers.
 */
final class Utils {

    static final String TAG = "Provision";

    private static final String BASE_SETTINGS = "tmp_provision_";
    static final String SETTINGS_PROVISION_DO_MODE = BASE_SETTINGS + "set_do";
    static final String SETTINGS_PROVISION_DO_PKG = BASE_SETTINGS + "package";
    static final String SETTINGS_PROVISION_DO_RECEIVER = BASE_SETTINGS + "receiver";
    static final String SETTINGS_PROVISION_DO_CHECKSUM = BASE_SETTINGS + "checksum";
    static final String SETTINGS_PROVISION_DO_DOWNLOAD_URL = BASE_SETTINGS + "download_url";
    static final String SETTINGS_PROVISION_DO_DOWNLOAD_TRIGGER = BASE_SETTINGS + "trigger";

    // Values below should be merged as null / 0, but can be changed locally to make it easier
    // to trigger device owner provisioning (see example below).
    static final int DEFAULT_SETTINGS_PROVISION_DO_MODE = 0;
    static final String DEFAULT_SETTINGS_PROVISION_DO_PKG = null;
    static final String DEFAULT_SETTINGS_PROVISION_DO_RECEIVER = null;
    static final int DEFAULT_SETTINGS_PROVISION_DO_DOWNLOAD_TRIGGER = 0;
    static final String DEFAULT_SETTINGS_PROVISION_DO_CHECKSUM = null;
    static final String DEFAULT_SETTINGS_PROVISION_DO_DOWNLOAD_URL = null;

    // Use these constants to trigger device owner provisioning using the TestDPC app (notice that
    // it must be manually installed in the device).
//    static final int DEFAULT_SETTINGS_PROVISION_DO_MODE = 1;
//    static final String DEFAULT_SETTINGS_PROVISION_DO_PKG = "com.afwsamples.testdpc";
//    static final String DEFAULT_SETTINGS_PROVISION_DO_RECEIVER =
//            "com.afwsamples.testdpc.DeviceAdminReceiver";
//    static final int DEFAULT_SETTINGS_PROVISION_DO_DOWNLOAD_TRIGGER =
//            android.app.admin.DevicePolicyManager.PROVISIONING_TRIGGER_QR_CODE;


    static String getSettings(ContentResolver resolver, String property,
            String overriddenValue) {
        if (overriddenValue != null) {
            Log.w(TAG, "Using OVERRIDDEN value " + overriddenValue + " for property " + property);
            return overriddenValue;
        }
        String value = Settings.Secure.getString(resolver, property);
        Log.w(TAG, "Using value " + overriddenValue + " for property " + property);
        return value;
    }

    static int getSettings(ContentResolver resolver, String property,
            int overriddenValue) {
        if (overriddenValue != 0) {
            Log.w(TAG, "Using OVERRIDDEN value " + overriddenValue + " for property " + property);
            return overriddenValue;
        }
        int value = Settings.Secure.getInt(resolver, property, overriddenValue);
        Log.w(TAG, "Using value " + overriddenValue + " for property " + property);
        return value;
    }

    private Utils() {
        throw new UnsupportedOperationException("contains only static members");
    }
}
