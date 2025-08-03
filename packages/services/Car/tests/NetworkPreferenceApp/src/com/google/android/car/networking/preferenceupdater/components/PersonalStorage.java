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
import static android.net.OemNetworkPreferences.OEM_NETWORK_PREFERENCE_UNINITIALIZED;

import static com.google.android.car.networking.preferenceupdater.components.OemNetworkPreferencesAdapter.OEM_NETWORK_PREFERENCE_ARRAY;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.SparseArray;

import java.util.HashSet;
import java.util.Set;

/** This class will abstragate storage where OEM network policies are persisted */
public final class PersonalStorage {
    private static final String KEY_PREFERENCE_APP = "key_preference_app";

    private static final String KEY_REAPPLY_PANS_ON_BOOT_COMPLETE =
            "key_reapply_pans_on_boot_complete";
    private static final String KEY_REAPPLY_WIFI_ON_BOOT_COMPLETE =
            "key_reapply_wifi_on_boot_complete";
    private static final String KEY_OEM_PAID_WIFI_SSIDS = "key_oem_paid_wifi_ssids";
    private static final String KEY_OEM_PRIVATE_WIFI_SSIDS = "key_oem_private_wifi_ssids";

    private final SharedPreferences mSharedPrefs;
    private final Context mContext;

    public PersonalStorage(Context ctx) {
        mContext = ctx;
        mSharedPrefs = ctx.getSharedPreferences(KEY_PREFERENCE_APP, Context.MODE_PRIVATE);
    }

    public Set<String> get(int type) {
        return mSharedPrefs.getStringSet(
                oemNetworkPreferenceToString(type), getPrefApps(type));
    }

    public void store(SparseArray<Set<String>> preference) {
        SharedPreferences.Editor editor = mSharedPrefs.edit();
        for (int type : OEM_NETWORK_PREFERENCE_ARRAY) {
            if (preference.contains(type)) {
                editor.putStringSet(
                        oemNetworkPreferenceToString(type),
                        preference.get(type));
            }
        }
        editor.apply();
    }

    public void storeWifi(Set<String> paidSsids, Set<String> privateSsids) {
        SharedPreferences.Editor editor = mSharedPrefs.edit();
        if (paidSsids != null) {
            editor.putStringSet(KEY_OEM_PAID_WIFI_SSIDS, paidSsids);
        } else {
            editor.remove(KEY_OEM_PAID_WIFI_SSIDS);
        }
        if (privateSsids != null) {
            editor.putStringSet(KEY_OEM_PRIVATE_WIFI_SSIDS, privateSsids);
        } else {
            editor.remove(KEY_OEM_PRIVATE_WIFI_SSIDS);
        }
        editor.apply();
    }

    public Set<String> getOemPaidWifiSsids() {
        return mSharedPrefs.getStringSet(KEY_OEM_PAID_WIFI_SSIDS, new HashSet<>());
    }

    public Set<String> getOemPrivateWifiSsids() {
        return mSharedPrefs.getStringSet(KEY_OEM_PRIVATE_WIFI_SSIDS, new HashSet<>());
    }

    public boolean getReapplyPansOnBootCompleteState() {
        return mSharedPrefs.getBoolean(KEY_REAPPLY_PANS_ON_BOOT_COMPLETE, false);
    }

    public boolean getReapplyWifiOnBootCompleteState() {
        return mSharedPrefs.getBoolean(KEY_REAPPLY_WIFI_ON_BOOT_COMPLETE, false);
    }

    public SparseArray<Set<String>> getAllPrefApps() {
        SparseArray<Set<String>> prefs = new SparseArray<>();
        for (int type : OEM_NETWORK_PREFERENCE_ARRAY) {
            prefs.put(type, getPrefApps(type));
        }
        return prefs;
    }

    /** This will reset values to default and save it to the personal storage. */
    public void resetNetworkPreferences() {
        SparseArray<Set<String>> prefs = new SparseArray<>();
        for (int type : OEM_NETWORK_PREFERENCE_ARRAY) {
            prefs.put(type, OemAppsManager.getDefaultAppsFor(mContext, type));
        }
        store(prefs);
        saveReapplyPansOnBootCompleteState(false);
    }

    private Set<String> getPrefApps(int type) {
        return mSharedPrefs.getStringSet(
                oemNetworkPreferenceToString(type),
                OemAppsManager.getDefaultAppsFor(mContext, type));
    }

    private static String oemNetworkPreferenceToString(int value) {
        switch (value) {
            case OEM_NETWORK_PREFERENCE_UNINITIALIZED:
                return "OEM_NETWORK_PREFERENCE_UNINITIALIZED";
            case OEM_NETWORK_PREFERENCE_OEM_PAID:
                return "OEM_NETWORK_PREFERENCE_OEM_PAID";
            case OEM_NETWORK_PREFERENCE_OEM_PAID_NO_FALLBACK:
                return "OEM_NETWORK_PREFERENCE_OEM_PAID_NO_FALLBACK";
            case OEM_NETWORK_PREFERENCE_OEM_PAID_ONLY:
                return "OEM_NETWORK_PREFERENCE_OEM_PAID_ONLY";
            case OEM_NETWORK_PREFERENCE_OEM_PRIVATE_ONLY:
                return "OEM_NETWORK_PREFERENCE_OEM_PRIVATE_ONLY";
            default:
                return Integer.toHexString(value);
        }
    }

    public void saveReapplyPansOnBootCompleteState(boolean isChecked) {
        SharedPreferences.Editor editor = mSharedPrefs.edit();
        editor.putBoolean(KEY_REAPPLY_PANS_ON_BOOT_COMPLETE, isChecked);
        editor.apply();
    }

    public void saveReapplyWifiOnBootCompleteState(boolean isChecked) {
        SharedPreferences.Editor editor = mSharedPrefs.edit();
        editor.putBoolean(KEY_REAPPLY_WIFI_ON_BOOT_COMPLETE, isChecked);
        editor.apply();
    }
}
