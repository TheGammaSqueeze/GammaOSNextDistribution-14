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
package com.google.android.car.networking.preferenceupdater.components;

import static android.net.OemNetworkPreferences.OEM_NETWORK_PREFERENCE_OEM_PAID;
import static android.net.OemNetworkPreferences.OEM_NETWORK_PREFERENCE_OEM_PAID_NO_FALLBACK;
import static android.net.OemNetworkPreferences.OEM_NETWORK_PREFERENCE_OEM_PAID_ONLY;
import static android.net.OemNetworkPreferences.OEM_NETWORK_PREFERENCE_OEM_PRIVATE_ONLY;

import android.annotation.NonNull;
import android.content.Context;
import android.util.ArraySet;

import com.google.android.car.networking.preferenceupdater.R;

import java.util.Arrays;
import java.util.Set;

/** Maintains list of applications per OEM network preference. Singleton class. */
public final class OemAppsManager {
    public static Set<String> getDefaultAppsFor(@NonNull Context ctx, int type) {
        switch (type) {
            case OEM_NETWORK_PREFERENCE_OEM_PAID:
                return getRes(ctx, R.array.config_network_preference_oem_paid_apps);
            case OEM_NETWORK_PREFERENCE_OEM_PAID_NO_FALLBACK:
                return getRes(ctx, R.array.config_network_preference_oem_paid_no_fallback_apps);
            case OEM_NETWORK_PREFERENCE_OEM_PAID_ONLY:
                return getRes(ctx, R.array.config_network_preference_oem_paid_only);
            case OEM_NETWORK_PREFERENCE_OEM_PRIVATE_ONLY:
                return getRes(ctx, R.array.config_network_preference_oem_private_only);
            default:
                throw new IllegalArgumentException("Unknown OEM_NETWORK_PREFERENCE type: " + type);
        }
    }

    private static ArraySet<String> getRes(Context ctx, int resId) {
        return new ArraySet<String>(Arrays.asList(ctx.getResources().getStringArray(resId)));
    }
}
