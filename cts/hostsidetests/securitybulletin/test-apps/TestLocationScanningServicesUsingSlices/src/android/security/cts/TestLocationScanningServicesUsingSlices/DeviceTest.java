/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.security.cts.TestLocationScanningServicesUsingSlices;

import static android.os.UserManager.DISALLOW_CONFIG_LOCATION;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.app.slice.SliceProvider;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.UserManager;
import android.provider.Settings;

import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
public class DeviceTest {
    private final int waitMS = 5_000;
    private final String mSettingsPackage = "com.android.settings";
    private final String mScanningTextResId = mSettingsPackage + ":id/switch_text";
    private final String mScanningWidgetResId = "android:id/switch_widget";
    private final String mBaseUri = "content://" + mSettingsPackage + ".slices/action/";

    private Context mContext;
    private ContentResolver mContentResolver;
    private UiDevice mDevice;
    private Resources mSettingsResources;

    @Before
    public void setUp() {
        try {
            Instrumentation instrumentation = getInstrumentation();
            mContext = instrumentation.getTargetContext();
            mContentResolver = mContext.getContentResolver();
            mDevice = UiDevice.getInstance(instrumentation);
            mSettingsResources =
                    mContext.getPackageManager().getResourcesForApplication(mSettingsPackage);

            // Set DISALLOW_CONFIG_LOCATION restriction for the current user
            mContext.getSystemService(DevicePolicyManager.class)
                    .addUserRestriction(
                            new ComponentName(mContext, PocDeviceAdminReceiver.class),
                            DISALLOW_CONFIG_LOCATION);
            assumeTrue(
                    "Failed to set user restriction DISALLOW_CONFIG_LOCATION",
                    mContext.getSystemService(UserManager.class)
                            .getUserRestrictions()
                            .getBoolean(DISALLOW_CONFIG_LOCATION));
            Object intent =
                    SliceProvider.createPermissionIntent(
                            mContext,
                            Uri.parse(mBaseUri),
                            "android.security.cts.TestLocationScanningServicesUsingSlices");

            // For Android SC and SC-V2 createPermissionIntent returns an instance of
            // PendingIntent
            if (intent instanceof PendingIntent) {
                ((PendingIntent) intent).send();
            }

            // For Android T and above createPermissionIntent returns an instance of
            // Intent
            if (intent instanceof Intent) {
                mContext.startActivity(((Intent) intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }

            // Grant SLICE_PERMISSION by clicking on 'Allow'
            Pattern allowPattern = Pattern.compile("Allow", Pattern.CASE_INSENSITIVE);
            assumeTrue(mDevice.wait(Until.hasObject(By.text(allowPattern)), waitMS));
            UiObject2 allowButton = mDevice.findObject(By.text(allowPattern));
            allowButton.click();
        } catch (Exception e) {
            assumeNoException(e);
        }
    }

    @Test
    public void testPocCVE_2023_21247() {
        try {
            final int initialBleScanGlobalSetting =
                    Settings.Global.getInt(
                            mContentResolver, Settings.Global.BLE_SCAN_ALWAYS_AVAILABLE, -1);
            assumeTrue(initialBleScanGlobalSetting != -1);

            // Launching PocActivity to launch "bluetooth_always_scanning_switch" settings
            // slice using slice-uri
            mContext.startActivity(
                    new Intent(mContext, PocActivity.class)
                            .setData(Uri.parse(mBaseUri + "bluetooth_always_scanning_switch"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));

            // Wait for the slice that contains option to toggle "Bluetooth scanning"
            String btScanningText =
                    mSettingsResources.getString(
                            mSettingsResources.getIdentifier(
                                    "location_scanning_bluetooth_always_scanning_title",
                                    "string",
                                    mSettingsPackage));
            boolean btScanningFound =
                    mDevice.wait(
                            Until.hasObject(By.text(btScanningText).res(mScanningTextResId)),
                            waitMS);
            assumeTrue(
                    "Timed out waiting on the text \'" + btScanningText + "\' to appear",
                    btScanningFound);

            // Try to toggle "Bluetooth scanning" button, it is supposed to be disabled by the
            // Device Admin in presence of fix
            UiObject2 btScanningToggle = mDevice.findObject(By.res(mScanningWidgetResId));
            btScanningToggle.click();

            // Value of isChecked is whether "Bluetooth scanning" button should be checked or
            // unchecked after click()
            assumeTrue(
                    "Timed out waiting on the button \'" + btScanningToggle + "\' to toggle",
                    btScanningToggle.wait(
                                    Until.checked(initialBleScanGlobalSetting == 0 /* isChecked */),
                                    waitMS)
                            || !btScanningToggle.isEnabled());

            final int finalBleScanGlobalSetting =
                    Settings.Global.getInt(
                            mContentResolver, Settings.Global.BLE_SCAN_ALWAYS_AVAILABLE, -1);
            assumeTrue(finalBleScanGlobalSetting != -1);

            assertFalse(
                    "Device is vulnerable to b/277333781 !!",
                    finalBleScanGlobalSetting != initialBleScanGlobalSetting);
        } catch (Exception e) {
            assumeNoException(e);
        }
    }

    @Test
    public void testPocCVE_2023_21248() {
        try {
            final int initialWifiScanGlobalSetting =
                    Settings.Global.getInt(
                            mContentResolver, Settings.Global.WIFI_SCAN_ALWAYS_AVAILABLE, -1);
            assumeTrue(initialWifiScanGlobalSetting != -1);

            // Launching PocActivity to launch "wifi_always_scanning_switch" settings
            // slice using slice-uri
            mContext.startActivity(
                    new Intent(mContext, PocActivity.class)
                            .setData(Uri.parse(mBaseUri + "wifi_always_scanning_switch"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));

            // Wait for the slice that contains option to toggle "Wifi scanning"
            String wifiScanningText =
                    mSettingsResources.getString(
                            mSettingsResources.getIdentifier(
                                    "location_scanning_wifi_always_scanning_title",
                                    "string",
                                    mSettingsPackage));
            boolean wifiScanningFound =
                    mDevice.wait(
                            Until.hasObject(By.text(wifiScanningText).res(mScanningTextResId)),
                            waitMS);
            assumeTrue(
                    "Timed out waiting on the text \'" + wifiScanningText + "\' to appear",
                    wifiScanningFound);

            // Try to toggle "Wifi scanning" button, it is supposed to be disabled by the
            // Device Admin in presence of fix
            UiObject2 wifiScanningToggle = mDevice.findObject(By.res(mScanningWidgetResId));
            wifiScanningToggle.click();

            // Value of isChecked is whether "Wifi scanning" button should be checked or
            // unchecked after click()
            assumeTrue(
                    "Timed out waiting on the button \'" + wifiScanningToggle + "\' to toggle",
                    wifiScanningToggle.wait(
                                    Until.checked(
                                            initialWifiScanGlobalSetting == 0 /* isChecked */),
                                    waitMS)
                            || !wifiScanningToggle.isEnabled());

            final int finalWifiScanGlobalSetting =
                    Settings.Global.getInt(
                            mContentResolver, Settings.Global.WIFI_SCAN_ALWAYS_AVAILABLE, -1);
            assumeTrue(finalWifiScanGlobalSetting != -1);

            assertFalse(
                    "Device is vulnerable to b/277333746 !!",
                    finalWifiScanGlobalSetting != initialWifiScanGlobalSetting);
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
