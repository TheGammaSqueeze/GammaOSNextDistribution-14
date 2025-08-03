/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.emulatorprovisionlib;

import android.app.Activity;
import android.app.StatusBarManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.input.InputManagerGlobal;
import android.hardware.input.KeyboardLayout;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiConfiguration;
import android.provider.Settings;
import android.os.Bundle;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.InputDevice;

import com.android.internal.widget.LockPatternUtils;

public abstract class ProvisionActivity extends Activity {
    protected abstract String TAG();
    private StatusBarManager mStatusBarManager;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        if (Settings.Global.getInt(getContentResolver(), Settings.Global.DEVICE_PROVISIONED, 0) != 1) {
            preProvivion();
            doProvision();
            postProvision();
        } else {
            Log.w(TAG(), "Already provisioned, remove itself.");
            removeSelf();
        }

        finish();  // terminate the activity.
    }

    protected void preProvivion() {
        final Context appContext = getApplicationContext();
        mStatusBarManager = appContext.getSystemService(StatusBarManager.class);

        mStatusBarManager.setDisabledForSetup(true);
    }

    protected void postProvision() {
        mStatusBarManager.setDisabledForSetup(false);

        removeSelf();

        // Add a persistent setting to allow other apps to know the device has been provisioned.
        Settings.Secure.putInt(getContentResolver(), Settings.Secure.USER_SETUP_COMPLETE, 1);
        Settings.Global.putInt(getContentResolver(), Settings.Global.DEVICE_PROVISIONED, 1);
    }

    // remove this activity from the package manager.
    protected void removeSelf() {
        getPackageManager().setComponentEnabledSetting(
                new ComponentName(this, this.getClass()),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    protected void doProvision() {
        provisionWifi("AndroidWifi");
        provisionKeyboard("qwerty2");
        provisionDisplay();
        provisionTelephony();
        provisionLocation();
        provisionAdb();

        Settings.Secure.putInt(getContentResolver(), Settings.Secure.INSTALL_NON_MARKET_APPS, 1);
    }

    protected void provisionWifi(final String ssid) {
        Settings.Global.putInt(getContentResolver(), Settings.Global.TETHER_OFFLOAD_DISABLED, 1);

        final int ADD_NETWORK_FAIL = -1;
        final String quotedSsid = "\"" + ssid + "\"";

        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = quotedSsid;
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_OPEN);

        final WifiManager mWifiManager = getApplicationContext().getSystemService(WifiManager.class);
        final int netId = mWifiManager.addNetwork(config);

        if (netId == ADD_NETWORK_FAIL || !mWifiManager.enableNetwork(netId, true)) {
            Log.e(TAG(), "Unable to add Wi-Fi network " + quotedSsid + ".");
        }
    }

    // Set physical keyboard layout based on the system property set by emulator host.
    protected void provisionKeyboard(final String deviceName) {
        final String layoutName = SystemProperties.get("vendor.qemu.keyboard_layout");
        final InputDevice device = getKeyboardDevice(deviceName);
        if (device != null && !layoutName.isEmpty()) {
            setKeyboardLayout(device, layoutName);
        }
    }

    protected void provisionDisplay() {
        Settings.Global.putInt(getContentResolver(), Settings.Global.STAY_ON_WHILE_PLUGGED_IN, 1);

        final int screen_off_timeout =
            SystemProperties.getInt("ro.boot.qemu.settings.system.screen_off_timeout", 0);
        if (screen_off_timeout > 0) {
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, screen_off_timeout);
            Log.i(TAG(), "Setting system screen_off_timeout to be " + screen_off_timeout + " ms");
        }

        (new LockPatternUtils(this)).setLockScreenDisabled(true,
            android.os.Process.myUserHandle().getIdentifier());

        final String displaySettingsProp = "ro.boot.qemu.display.settings.xml";
        final String displaySettingsName = SystemProperties.get(displaySettingsProp);
        if ("freeform".equals(displaySettingsName)) {
            Settings.Global.putInt(getContentResolver(), "sf", 1);
            Settings.Global.putString(getContentResolver(),
                                      Settings.Global.DEVELOPMENT_ENABLE_FREEFORM_WINDOWS_SUPPORT, "1");
            Settings.Global.putString(getContentResolver(),
                                      Settings.Global.DEVELOPMENT_FORCE_RESIZABLE_ACTIVITIES, "1");
            Settings.Global.putString(getContentResolver(),
                                      Settings.Global.DEVELOPMENT_WM_DISPLAY_SETTINGS_PATH,
                                      "vendor/etc/display_settings_freeform.xml");
        } else if ("resizable".equals(displaySettingsName)) {
            // Enable auto rotate for resizable AVD
            Settings.System.putString(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, "1");
        } else if (!displaySettingsName.isEmpty()) {
            Log.e(TAG(), "Unexpected value `" + displaySettingsName + "` in " + displaySettingsProp);
        }
        final String autoRotateProp = "ro.boot.qemu.autorotate";
        final String autoRotateSetting = SystemProperties.get(autoRotateProp);
        if (!autoRotateSetting.isEmpty()) {
            Settings.System.putString(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, autoRotateSetting);
        }
    }

    // required for CTS which uses the mock modem
    private void provisionMockModem() {
        String value = SystemProperties.get("ro.boot.radio.allow_mock_modem");
        if (!value.isEmpty()) {
            if (value.equals("1")) {
                value = "true";
            } else if (value.equals("0")) {
                value = "false";
            }

            SystemProperties.set("persist.radio.allow_mock_modem", value);
        }
    }

    protected void provisionTelephony() {
        // b/193418404
        // the following blocks, TODO: find out why and fix it. disable this for now.
        // TelephonyManager mTelephony = getApplicationContext().getSystemService(TelephonyManager.class);
        // mTelephony.setPreferredNetworkTypeBitmask(TelephonyManager.NETWORK_TYPE_BITMASK_NR);

        provisionMockModem();
    }

    protected void provisionLocation() {
        final LocationManager lm = getSystemService(LocationManager.class);
        lm.setLocationEnabledForUser(true, Process.myUserHandle());

        // Enable the GPS.
        // Not needed since this SDK will contain the Settings app.
        Settings.Secure.putString(getContentResolver(), Settings.Secure.LOCATION_PROVIDERS_ALLOWED,
                LocationManager.GPS_PROVIDER);
    }

    protected void provisionAdb() {
        Settings.Global.putInt(getContentResolver(), Settings.Global.ADB_ENABLED, 1);
        Settings.Global.putInt(getContentResolver(), Settings.Global.PACKAGE_VERIFIER_INCLUDE_ADB, 0);
    }

    protected InputDevice getKeyboardDevice(final String keyboardDeviceName) {
        final int[] deviceIds = InputDevice.getDeviceIds();

        for (int deviceId : deviceIds) {
            InputDevice inputDevice = InputDevice.getDevice(deviceId);
            if (inputDevice != null
                    && inputDevice.supportsSource(InputDevice.SOURCE_KEYBOARD)
                    && inputDevice.getName().equals(keyboardDeviceName)) {
                return inputDevice;
            }
        }

        return null;
    }

    protected void setKeyboardLayout(final InputDevice keyboardDevice, final String layoutName) {
        final InputManagerGlobal im = InputManagerGlobal.getInstance();

        final KeyboardLayout[] keyboardLayouts =
                im.getKeyboardLayoutsForInputDevice(keyboardDevice.getIdentifier());

        for (KeyboardLayout keyboardLayout : keyboardLayouts) {
            if (keyboardLayout.getDescriptor().endsWith(layoutName)) {
                im.setCurrentKeyboardLayoutForInputDevice(
                        keyboardDevice.getIdentifier(), keyboardLayout.getDescriptor());
                return;
            }
        }
    }
}
