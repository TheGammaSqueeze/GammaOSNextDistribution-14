/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tv.settings.accessories;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.DrawableRes;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settingslib.RestrictedLockUtils;
import com.android.settingslib.RestrictedLockUtilsInternal;
import com.android.settingslib.bluetooth.LocalBluetoothAdapter;
import com.android.settingslib.bluetooth.LocalBluetoothManager;
import com.android.tv.settings.R;
import com.android.tv.settings.SettingsPreferenceFragment;

import java.util.Set;

/**
 * The "Remotes and Accessories" screen in TV settings.
 */
@Keep
public class AccessoriesFragment extends SettingsPreferenceFragment {
    private static final String TAG = "AccessoriesFragment";
    private static final String KEY_ADD_ACCESSORY = "add_accessory";

    private LocalBluetoothManager mLocalBtManager;
    private Preference mAddAccessory;
    private final BroadcastReceiver mBCMReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateAccessories();
        }
    };

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.remotes_and_accessories, null);
        mAddAccessory = findPreference(KEY_ADD_ACCESSORY);
    }

    private void updateAccessories() {
        PreferenceScreen preferenceScreen = getPreferenceScreen();
        if (preferenceScreen == null) {
            return;
        }

        LocalBluetoothAdapter btAdapter = mLocalBtManager.getBluetoothAdapter();
        if (btAdapter == null) {
            preferenceScreen.removeAll();
            return;
        }

        final Set<BluetoothDevice> bondedDevices = btAdapter.getBondedDevices();
        if (bondedDevices == null) {
            preferenceScreen.removeAll();
            return;
        }

        final Context themedContext = getPreferenceManager().getContext();

        final Set<String> touchedKeys = new ArraySet<>(bondedDevices.size() + 1);
        if (mAddAccessory != null) {
            touchedKeys.add(mAddAccessory.getKey());
        }

        for (final BluetoothDevice device : bondedDevices) {
            final String deviceAddress = device.getAddress();
            if (TextUtils.isEmpty(deviceAddress)) {
                Log.w(TAG, "Skipping mysteriously empty bluetooth device");
                continue;
            }

            final String desc = device.isConnected() ? getString(R.string.accessory_connected) :
                    null;
            final String key = "BluetoothDevice:" + deviceAddress;
            touchedKeys.add(key);
            Preference preference = preferenceScreen.findPreference(key);
            if (preference == null) {
                preference = new Preference(themedContext);
                preference.setKey(key);
            }
            final String deviceName = device.getAlias();
            preference.setTitle(deviceName);
            preference.setSummary(desc);
            final int deviceImgId = getImageIdForDevice(device, false);
            preference.setIcon(deviceImgId);

            RestrictedLockUtils.EnforcedAdmin admin =
                    RestrictedLockUtilsInternal.checkIfRestrictionEnforced(preference.getContext(),
                            UserManager.DISALLOW_CONFIG_BLUETOOTH, UserHandle.myUserId());
            if (admin == null) {
                preference.setFragment(BluetoothAccessoryFragment.class.getName());
            } else {
                RestrictedLockUtils.sendShowAdminSupportDetailsIntent(getContext(), admin);
            }

            BluetoothAccessoryFragment.prepareArgs(
                    preference.getExtras(),
                    deviceAddress,
                    deviceName,
                    deviceImgId);
            preferenceScreen.addPreference(preference);
        }

        for (int i = 0; i < preferenceScreen.getPreferenceCount();) {
            final Preference preference = preferenceScreen.getPreference(i);
            if (touchedKeys.contains(preference.getKey())) {
                i++;
            } else {
                preferenceScreen.removePreference(preference);
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        IntentFilter btChangeFilter = new IntentFilter();
        btChangeFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        btChangeFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        btChangeFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        getContext().registerReceiver(mBCMReceiver, btChangeFilter);
    }

    @Override
    public void onStop() {
        super.onStop();
        getContext().unregisterReceiver(mBCMReceiver);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateAccessories();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mLocalBtManager = AccessoryUtils.getLocalBluetoothManager(getContext());
        super.onCreate(savedInstanceState);
    }


    /**
     * @param dev             the bluetooth device
     * @param willBeProcessed whether the icon will be processed by Slice
     * @return the resource id for a bluetooth device's icon based on its device type
     */
    static @DrawableRes int getImageIdForDevice(
        @NonNull BluetoothDevice dev, boolean willBeProcessed) {
        if (dev == null || dev.getBluetoothClass() == null) {
            return willBeProcessed ? R.drawable.ic_bluetooth_raw : R.drawable.ic_bluetooth;
        }

        final int devClass = dev.getBluetoothClass().getDeviceClass();
        // The order is critical
        if (devClass == BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET) {
            return willBeProcessed ? R.drawable.ic_headset_mic_raw : R.drawable.ic_headset_mic;
        } else if (devClass == BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES
            || devClass == BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER
            || devClass == BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO
            || devClass == BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO) {
            return willBeProcessed ? R.drawable.ic_headset_raw : R.drawable.ic_headset;
        } else if ((devClass & InputDeviceCriteria.MINOR_DEVICE_CLASS_POINTING) != 0) {
            return willBeProcessed ? R.drawable.ic_mouse_raw : R.drawable.ic_mouse;
        } else if (AccessoryUtils.isA2dpSource(dev) && willBeProcessed) {
            return R.drawable.ic_a2dp_raw;
        } else if ((devClass & InputDeviceCriteria.MINOR_DEVICE_CLASS_REMOTE) != 0) {
            return willBeProcessed ? R.drawable.ic_games_raw : R.drawable.ic_games;
        } else if ((devClass & InputDeviceCriteria.MINOR_DEVICE_CLASS_JOYSTICK) != 0) {
            return willBeProcessed ? R.drawable.ic_games_raw : R.drawable.ic_games;
        } else if ((devClass & InputDeviceCriteria.MINOR_DEVICE_CLASS_GAMEPAD) != 0) {
            return willBeProcessed ? R.drawable.ic_games_raw : R.drawable.ic_games;
        } else if ((devClass & InputDeviceCriteria.MINOR_DEVICE_CLASS_KEYBOARD) != 0) {
            return willBeProcessed ? R.drawable.ic_keyboard_raw : R.drawable.ic_keyboard;
        }

        // Default for now
        return willBeProcessed ? R.drawable.ic_bluetooth_raw : R.drawable.ic_bluetooth;
    }
}
