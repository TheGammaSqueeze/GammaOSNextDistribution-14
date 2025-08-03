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

package com.android.bedstead.nene.bluetooth;

import static android.os.Build.VERSION_CODES.R;
import static android.os.Process.BLUETOOTH_UID;

import static com.android.bedstead.nene.permissions.CommonPermissions.BLUETOOTH;
import static com.android.bedstead.nene.permissions.CommonPermissions.BLUETOOTH_CONNECT;
import static com.android.bedstead.nene.permissions.CommonPermissions.BLUETOOTH_PRIVILEGED;
import static com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS;
import static com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS_FULL;
import static com.android.bedstead.nene.permissions.CommonPermissions.NETWORK_SETTINGS;
import static com.android.bedstead.nene.utils.Versions.T;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.annotations.Experimental;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.nene.utils.Versions;
import com.android.compatibility.common.util.BlockingBroadcastReceiver;

/** Test APIs related to bluetooth. */
public final class Bluetooth {
    public static final Bluetooth sInstance = new Bluetooth();

    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final BluetoothManager sBluetoothManager =
            sContext.getSystemService(BluetoothManager.class);
    private static final BluetoothAdapter sBluetoothAdapter = sBluetoothManager.getAdapter();


    public static final String OPP_LAUNCHER_CLASS =
            "com.android.bluetooth.opp.BluetoothOppLauncherActivity";

    private Bluetooth() {}

    /** Enable or disable bluetooth on the device. */
    public void setEnabled(boolean enabled) {
        if (isEnabled() == enabled) {
            return;
        }

        if (enabled) {
            enable();
        } else {
            disable();
        }
    }

    private void enable() {
        try (PermissionContext p = TestApis.permissions()
                                           .withPermission(BLUETOOTH_CONNECT,
                                                   INTERACT_ACROSS_USERS_FULL, BLUETOOTH_PRIVILEGED)
                                           .withPermissionOnVersionAtLeast(T, NETWORK_SETTINGS)) {
            BlockingBroadcastReceiver r =
                    BlockingBroadcastReceiver
                            .create(sContext, BluetoothAdapter.ACTION_STATE_CHANGED,
                                    this::isStateEnabled)
                            .register();

            try {
                boolean returnValue = sBluetoothAdapter.enable();

                r.awaitForBroadcast();
                Poll.forValue("Bluetooth Enabled", this::isEnabled)
                        .toBeEqualTo(true)
                        .errorOnFail("Waited for bluetooth to be enabled."
                                + " .enable() returned " + returnValue)
                        .await();
            } finally {
                r.unregisterQuietly();
            }
        }
    }

    private void disable() {
        try (PermissionContext p = TestApis.permissions()
                                           .withPermission(BLUETOOTH_CONNECT,
                                                   INTERACT_ACROSS_USERS_FULL, BLUETOOTH_PRIVILEGED)
                                           .withPermissionOnVersionAtLeast(T, NETWORK_SETTINGS)) {
            BlockingBroadcastReceiver r =
                    BlockingBroadcastReceiver
                            .create(sContext, BluetoothAdapter.ACTION_STATE_CHANGED,
                                    this::isStateDisabled)
                            .register();

            try {
                boolean returnValue = sBluetoothAdapter.disable();

                r.awaitForBroadcast();
                Poll.forValue("Bluetooth Enabled", this::isEnabled)
                        .toBeEqualTo(false)
                        .errorOnFail("Waited for bluetooth to be disabled."
                                + " .disable() returned " + returnValue)
                        .await();
            } finally {
                r.unregisterQuietly();
            }
        }
    }

    /** {@code true} if bluetooth is enabled. */
    public boolean isEnabled() {
        try (PermissionContext p =
                        TestApis.permissions().withPermissionOnVersionAtMost(R, BLUETOOTH)) {
            return sBluetoothAdapter.isEnabled();
        }
    }

    private boolean isStateEnabled(Intent intent) {
        return intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_ON;
    }

    private boolean isStateDisabled(Intent intent) {
        return intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_OFF;
    }

    /** The bluetooth UID is associated with multiple packages. Get the main one. */
    @Experimental
    public String findPackageName() {
        if (!Versions.meetsMinimumSdkVersionRequirement(T)) {
            return "com.android.bluetooth";
        }
        try (PermissionContext p = TestApis.permissions().withPermission(INTERACT_ACROSS_USERS)) {
            // this activity will always be in the package where the rest of Bluetooth lives
            var sentinelActivity = "com.android.bluetooth.opp.BluetoothOppLauncherActivity";
            var packageManager =
                    TestApis.context().androidContextAsUser(TestApis.users().system())
                            .getPackageManager();
            var allPackages = packageManager.getPackagesForUid(BLUETOOTH_UID);
            String matchedPackage = null;
            for (String candidatePackage : allPackages) {
                PackageInfo packageInfo;
                try {
                    packageInfo =
                            packageManager.getPackageInfo(
                                    candidatePackage,
                                    PackageManager.GET_ACTIVITIES
                                            | PackageManager.MATCH_ANY_USER
                                            | PackageManager.MATCH_UNINSTALLED_PACKAGES
                                            | PackageManager.MATCH_DISABLED_COMPONENTS);
                } catch (PackageManager.NameNotFoundException e) {
                    // rethrow
                    throw new NeneException(e);
                }
                if (packageInfo.activities == null) {
                    continue;
                }
                for (var activity : packageInfo.activities) {
                    if (sentinelActivity.equals(activity.name)) {
                        if (matchedPackage == null) {
                            matchedPackage = candidatePackage;
                        } else {
                            throw new NeneException("multiple main bluetooth packages found");
                        }
                    }
                }
            }
            if (matchedPackage != null) {
                return matchedPackage;
            }
            throw new NeneException("Could not find main bluetooth package");
        }
    }
}
