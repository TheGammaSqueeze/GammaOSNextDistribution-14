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

package com.android.server.bluetooth;

import static com.android.server.bluetooth.BluetoothAirplaneModeListener.BLUETOOTH_APM_STATE;

import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Process;
import android.os.UserHandle;
import android.provider.Settings;
import android.widget.Toast;

import com.android.internal.annotations.VisibleForTesting;

/**
 * Helper class that handles callout and callback methods without
 * complex logic.
 */
public class BluetoothModeChangeHelper {
    private static final String TAG = BluetoothModeChangeHelper.class.getSimpleName();

    private final BluetoothAdapter mAdapter;
    private final Context mContext;

    private String mBluetoothPackageName;

    BluetoothModeChangeHelper(Context context) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mContext = context;
    }

    @VisibleForTesting
    public boolean isBluetoothOn() {
        final BluetoothAdapter adapter = mAdapter;
        if (adapter == null) {
            return false;
        }
        return adapter.isLeEnabled();
    }

    @VisibleForTesting
    public int getSettingsInt(String name) {
        return Settings.Global.getInt(mContext.getContentResolver(),
                name, 0);
    }

    @VisibleForTesting
    public void setSettingsInt(String name, int value) {
        Settings.Global.putInt(mContext.getContentResolver(),
                name, value);
    }

    /**
     * Helper method to get Settings Secure Int value
     */
    public int getSettingsSecureInt(String name, int def) {
        Context userContext = mContext.createContextAsUser(
                UserHandle.of(ActivityManager.getCurrentUser()), 0);
        return Settings.Secure.getInt(userContext.getContentResolver(), name, def);
    }

    /**
     * Helper method to set Settings Secure Int value
     */
    public void setSettingsSecureInt(String name, int value) {
        Context userContext = mContext.createContextAsUser(
                UserHandle.of(ActivityManager.getCurrentUser()), 0);
        Settings.Secure.putInt(userContext.getContentResolver(), name, value);
    }

    @VisibleForTesting
    public void showToastMessage() {
        Resources r = mContext.getResources();
        final CharSequence text = r.getString(Resources.getSystem().getIdentifier(
                "bluetooth_airplane_mode_toast", "string", "android"));
        Toast.makeText(mContext, text, Toast.LENGTH_LONG).show();
    }

    /**
     * Helper method to check whether BT should be enabled on APM
     */
    public boolean isBluetoothOnAPM() {
        Context userContext = mContext.createContextAsUser(
                UserHandle.of(ActivityManager.getCurrentUser()), 0);
        return Settings.Secure.getInt(userContext.getContentResolver(),
                BLUETOOTH_APM_STATE, 0) == 1;
    }

    /**
     * Helper method to retrieve BT package name with APM resources
     */
    public String getBluetoothPackageName() {
        if (mBluetoothPackageName != null) {
            return mBluetoothPackageName;
        }
        var allPackages = mContext.getPackageManager().getPackagesForUid(Process.BLUETOOTH_UID);
        for (String candidatePackage : allPackages) {
            Resources resources;
            try {
                resources = mContext.getPackageManager()
                        .getResourcesForApplication(candidatePackage);
            } catch (PackageManager.NameNotFoundException e) {
                // ignore, try next package
                Log.e(TAG, "Could not find package " + candidatePackage);
                continue;
            } catch (Exception e) {
                Log.e(TAG, "Error while loading package" + e);
                continue;
            }
            if (resources.getIdentifier("bluetooth_and_wifi_stays_on_title",
                    "string", candidatePackage) == 0) {
                continue;
            }
            mBluetoothPackageName = candidatePackage;
        }
        return mBluetoothPackageName;
    }
}
