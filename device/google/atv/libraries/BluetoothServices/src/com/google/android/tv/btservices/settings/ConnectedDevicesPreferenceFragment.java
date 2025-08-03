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

package com.google.android.tv.btservices.settings;

import android.app.FragmentManager;
import android.app.admin.DevicePolicyManager;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.leanback.preference.LeanbackPreferenceFragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;

import com.android.settingslib.RestrictedLockUtils;
import com.android.settingslib.RestrictedLockUtilsInternal;

import com.google.android.tv.btservices.BluetoothUtils;
import com.google.android.tv.btservices.Configuration;
import com.google.android.tv.btservices.R;
import com.google.android.tv.btservices.SettingsUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public class ConnectedDevicesPreferenceFragment extends LeanbackPreferenceFragment {

    private static final String TAG = "Atv.DevsPrefFragment";

    public interface Provider {
        boolean isCecEnabled();
        List<BluetoothDevice> getBluetoothDevices();
        BluetoothDeviceProvider getBluetoothDeviceProvider();
    }

    private interface BtPreferenceCreator {
        Preference create(Context context, BluetoothDeviceProvider provider,
                BluetoothDevice device);
    }

    static final String KEY_ACCESSORIES = "accessories";
    static final String KEY_OFFICIAL_REMOTES = "official_remotes";
    static final String KEY_CONNECTED_DEVICES = "connected_devices";
    static final String KEY_PAIRED_DEVICES = "paired_devices";
    static final String KEY_PAIR_REMOTE = "pair_remote";
    static final String KEY_PAIR_PHONE = "pair_phone";
    static final String KEY_DEVICE_CONTROL = "device_control";
    static final String KEY_CEC_TOGGLE = "cec_toggle";
    static final String KEY_AXEL_TOGGLE = "axel_toggle";
    static final String KEY_FIND_MY_REMOTE_TOGGLE = "fmr_toggle";

    private static final Set<String> NON_BT_PREFERENCES =
            new HashSet<>(Arrays.asList(KEY_PAIR_REMOTE));

    private static final int PAIR_REMOTE_ORDER = 0;
    private static final int PAIR_PHONE_ORDER = 1;
    private static final int CONNECTED_DEVICES_ORDER = 2;
    private static final int PAIRED_DEVICES_ORDER = 3;
    private static final int DEVICE_CONTROL_ORDER = 4;

    // Assuming we won't have that many BT devices connected.
    private static final int LAST_DEVICE_ORDER = 10000;
    private static final int UPDATE_DELAY_MS = 500;

    private PreferenceGroup mPrefGroup;
    private static final int MSG_POP_DEVICE_FRAGMENT = 1;
    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message m) {
            if (m.what == MSG_POP_DEVICE_FRAGMENT) {
                popDevicePreferenceImpl((String) m.obj);
            }
        }
    };

    // Add a local provider proxy to enable custom events on certain actions.
    private BluetoothDeviceProvider mLocalBluetoothDeviceProvider =
            new LocalBluetoothDeviceProvider() {
                final BluetoothDeviceProvider getHostBluetoothDeviceProvider() {
                    Provider provider = getProvider();
                    if (provider != null) {
                        return provider.getBluetoothDeviceProvider();
                    }
                    return null;
                }

                @Override
                public void forgetDevice(BluetoothDevice device) {
                    popDevicePreference(device);
                    super.forgetDevice(device);
                }

                @Override
                public void renameDevice(BluetoothDevice device, String newName) {
                    popDevicePreference(device);
                    super.renameDevice(device, newName);
                }
            };

    public static ConnectedDevicesPreferenceFragment newInstance() {
        return new ConnectedDevicesPreferenceFragment();
    }

    private static PreferenceCategory findOrCreateCategory(Context context, PreferenceGroup group,
            String key, int title, boolean defaultShow) {
        PreferenceCategory category = (PreferenceCategory) group.findPreference(key);
        if (category == null) {
            category = new PreferenceCategory(context);
            category.setKey(key);
            category.setTitle(title);
            category.setLayoutResource(R.layout.preference_category_compact_layout);
            category.setVisible(defaultShow);
            group.addPreference(category);
        }
        return category;
    }

    private static Set<String> setComplement(Set<String> a, Set<String> b) {
        Set<String> c = new HashSet<>();
        for (String s : a) {
            if (b.contains(s)) {
                continue;
            }
            c.add(s);
        }
        return c;
    }

    private static Set<String> getBtDevices(PreferenceCategory category) {
        int count = category.getPreferenceCount();
        Set<String> oldDevices = new HashSet<>();
        for (int i = 0; i < count; i++) {
            Preference pref = category.getPreference(i);
            if (NON_BT_PREFERENCES.contains(pref.getKey())) {
                continue;
            }
            oldDevices.add(pref.getKey());
        }
        return oldDevices;
    }

    private static void updateBtDevicePreference(Context context, BluetoothDeviceProvider provider,
            BluetoothDevice device, Preference pref) {
        int batteryLevel = provider.getBatteryLevel(device);
        pref.setKey(device.getAddress());
        pref.setTitle(BluetoothUtils.getName(device));
        if (provider.hasUpgrade(device)) {
            pref.setSummary(R.string.settings_bt_update_available);
        } else {
            if (batteryLevel != BluetoothDevice.BATTERY_LEVEL_UNKNOWN) {
                if (provider.isBatteryLow(device)) {
                    pref.setSummary(R.string.settings_bt_battery_low_warning);
                } else {
                    pref.setSummary(context.getString(R.string.settings_remote_battery_level,
                        String.valueOf(batteryLevel)));
                }
            } else {
                pref.setSummary(null);
            }
        }
        pref.setIcon(BluetoothUtils.getIcon(context, device));
    }

    private static Preference createConnectedBtPreference(Context context,
            BluetoothDeviceProvider provider, BluetoothDevice device) {
        Preference pref = new Preference(context);
        pref.setKey(device.getAddress());
        pref.setLayoutResource(R.layout.preference_item_layout);
        updateBtDevicePreference(context, provider, device, pref);
        pref.setVisible(true);
        pref.setSelectable(true);
        BluetoothDevicePreferenceFragment.buildArgs(pref.getExtras(), device);
        RestrictedLockUtils.EnforcedAdmin admin =
                RestrictedLockUtilsInternal.checkIfRestrictionEnforced(context,
                        UserManager.DISALLOW_CONFIG_BLUETOOTH, UserHandle.myUserId());
        if (admin == null) {
            pref.setFragment(BluetoothDevicePreferenceFragment.class.getCanonicalName());
        } else {
            Intent intent = RestrictedLockUtils.getShowAdminSupportDetailsIntent(context, admin);
            intent.putExtra(DevicePolicyManager.EXTRA_RESTRICTION,
                    UserManager.DISALLOW_CONFIG_BLUETOOTH);
            pref.setIntent(intent);
        }

        return pref;
    }

    private static Preference createPairedBtPreference(Context context,
            BluetoothDeviceProvider provider, BluetoothDevice device) {
        Preference pref = createConnectedBtPreference(context, provider, device);
        // Do not show any metadata.
        pref.setSummary(null);
        return pref;
    }

    private Provider getProvider() {
        return getActivity() instanceof Provider ? (Provider) getActivity() : null;
    }

    public BluetoothDeviceProvider getBluetoothDeviceProvider() {
        return mLocalBluetoothDeviceProvider;
    }

    private int updateBtDevices(Context context, PreferenceCategory category,
            Predicate<BluetoothDevice> filterFunc, BtPreferenceCreator prefCreator) {
        Provider stateProvider = getProvider();
        BluetoothDeviceProvider btProvider = getBluetoothDeviceProvider();
        if (stateProvider == null) {
            return 0;
        }

        Set<String> oldDevices = getBtDevices(category);
        Set<String> currentDevices = new HashSet<>();
        List<BluetoothDevice> btDevices = stateProvider.getBluetoothDevices();
        HashMap<String, BluetoothDevice> addressToDevice = new HashMap<>();
        for (BluetoothDevice device : btDevices) {
            if (!filterFunc.test(device)) {
                continue;
            }
            addressToDevice.put(device.getAddress(), device);
            currentDevices.add(device.getAddress());
        }

        final Set<String> lostDevices = setComplement(oldDevices, currentDevices);
        final Set<String> newDevices = setComplement(currentDevices, oldDevices);
        final Set<String> updatingDevices = setComplement(oldDevices, lostDevices);
        for (String s : lostDevices) {
            Preference pref = category.findPreference(s);
            category.removePreference(pref);
        }

        int count = updatingDevices.size();
        for (String s : newDevices) {
            BluetoothDevice device = addressToDevice.get(s);
            Preference pref = prefCreator.create(context, btProvider, device);
            pref.setOrder(count++);
            category.addPreference(pref);
            if (!BluetoothUtils.isConnected(device)) {
                popDevicePreference(device);
            }
        }

        for (String s : updatingDevices) {
            BluetoothDevice device = addressToDevice.get(s);
            Preference pref = category.findPreference(s);
            pref.setVisible(true);
            updateBtDevicePreference(context, btProvider, device, pref);
            if (!BluetoothUtils.isConnected(device)) {
                popDevicePreference(device);
            }
        }

        // Lexicographically order the devices
        final List<String> allDevices = new ArrayList<>(currentDevices);
        Collections.sort(allDevices);
        for (int i = 0; i < allDevices.size(); i++) {
            category.findPreference(allDevices.get(i)).setOrder(i);
        }
        return allDevices.size();
    }

    public void updatePairedDevices() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        PreferenceCategory category = findOrCreateCategory(context, mPrefGroup, KEY_PAIRED_DEVICES,
                R.string.settings_devices_paired, true);
        category.setOrder(PAIRED_DEVICES_ORDER);
        category.setOrderingAsAdded(true);
        int updatedDevices = updateBtDevices(context, category, BluetoothUtils::isBonded,
                ConnectedDevicesPreferenceFragment::createPairedBtPreference);
        category.setVisible(updatedDevices > 0);
    }

    public void updateConnectedDevices() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        PreferenceCategory category = findOrCreateCategory(context, mPrefGroup,
                KEY_CONNECTED_DEVICES, R.string.settings_devices_connected, false);
        category.setOrder(CONNECTED_DEVICES_ORDER);

        Provider stateProvider = getActivity() instanceof Provider ? (Provider) getActivity() :
                null;

        int updatedDevices = updateBtDevices(context, category, BluetoothUtils::isConnected,
                ConnectedDevicesPreferenceFragment::createConnectedBtPreference);
        category.setVisible(updatedDevices > 0);
    }

    protected static boolean isCecSettingsEnabled(Context context) {
        return Configuration.get(context).isEnabled(R.bool.cec_settings_enabled);
    }

    protected static boolean isAxelSettingsEnabled(Context context) {
        return Configuration.get(context).isEnabled(R.bool.axel_settings_enabled);
    }

    public void updateCec(Context context) {
        if (!isCecSettingsEnabled(context)) {
            return;
        }

        PreferenceCategory category = findOrCreateCategory(context, mPrefGroup,
                KEY_DEVICE_CONTROL, R.string.settings_devices_control, true);
        category.setOrder(DEVICE_CONTROL_ORDER);
        Preference cecPref = category.findPreference(KEY_CEC_TOGGLE);
        if (cecPref == null) {
            cecPref = new Preference(context);
            cecPref.setKey(KEY_CEC_TOGGLE);
            cecPref.setTitle(R.string.settings_hdmi_cec);
            cecPref.setSummary(R.string.settings_enabled);
            final String cecPrefClassName = CecPreferenceFragment.class.getCanonicalName();
            cecPref.setFragment(cecPrefClassName);
            category.addPreference(cecPref);
        }
        Provider stateProvider = getActivity() instanceof Provider ? (Provider) getActivity() :
                null;
        if (stateProvider != null && !stateProvider.isCecEnabled()) {
            cecPref.setSummary(R.string.settings_disabled);
        } else {
            cecPref.setSummary(R.string.settings_enabled);
        }
    }

    private void updateAll(Context context) {
        updateConnectedDevices();
        updatePairedDevices();
        updateCec(context);
    }

    private void popDevicePreference(BluetoothDevice device) {
        mHandler.removeMessages(MSG_POP_DEVICE_FRAGMENT);
        Message msg = mHandler.obtainMessage(MSG_POP_DEVICE_FRAGMENT, device.getAddress());
        // We need to add a delay to make sure we are not popping the top fragment and updating the
        // fragment underneath at the same time.
        mHandler.sendMessageDelayed(msg, UPDATE_DELAY_MS);
    }

    private void popDevicePreferenceImpl(String address) {
        FragmentManager fragmentManager = getFragmentManager();
        if (fragmentManager == null) {
            Log.w(TAG, "popDevicePreferenceImpl: fragmentManager is null");
            return;
        }
        boolean shouldPop = false;
        for (android.app.Fragment frag : fragmentManager.getFragments()) {
            if (frag instanceof BluetoothDevicePreferenceFragment) {
                BluetoothDevice device = ((BluetoothDevicePreferenceFragment) frag).getDevice();
                shouldPop = device != null && TextUtils.equals(device.getAddress(), address);
                break;
            }
        }
        if (shouldPop) {
            fragmentManager.popBackStackImmediate();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Delay the update to avoid jank when the panel is sliding in.
        mHandler.postDelayed(() -> updateAll(getPreferenceManager().getContext()), UPDATE_DELAY_MS);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        final Context preferenceContext = getPreferenceManager().getContext();
        mPrefGroup = getPreferenceManager().createPreferenceScreen(preferenceContext);
        mPrefGroup.setTitle(R.string.connected_devices_pref_title);
        mPrefGroup.setOrderingAsAdded(false);

        RestrictedLockUtils.EnforcedAdmin admin =
                RestrictedLockUtilsInternal.checkIfRestrictionEnforced(getPreferenceManager().getContext(),
                        UserManager.DISALLOW_CONFIG_BLUETOOTH, UserHandle.myUserId());
        Preference pairRemotePref = mPrefGroup.findPreference(KEY_PAIR_REMOTE);

        if (pairRemotePref == null) {
            pairRemotePref = new Preference(preferenceContext);
            pairRemotePref.setLayoutResource(R.layout.preference_item_layout);
            pairRemotePref.setKey(KEY_PAIR_REMOTE);
            pairRemotePref.setTitle(R.string.settings_pair_remote);
            pairRemotePref.setIcon(R.drawable.ic_baseline_add_24dp);
            pairRemotePref.setOrder(PAIR_REMOTE_ORDER);
            mPrefGroup.addPreference(pairRemotePref);
        }
        if (admin != null) {
            Intent intent = RestrictedLockUtils.getShowAdminSupportDetailsIntent(preferenceContext,
                    admin);
            intent.putExtra(DevicePolicyManager.EXTRA_RESTRICTION,
                    UserManager.DISALLOW_CONFIG_BLUETOOTH);
            pairRemotePref.setIntent(intent);
        }
        setPreferenceScreen((PreferenceScreen) mPrefGroup);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        final String key = preference.getKey();
        if (KEY_PAIR_REMOTE.equals(key) && preference.getIntent() == null) {
            SettingsUtils.sendPairingIntent(getContext(), null);
        }
        return super.onPreferenceTreeClick(preference);
    }
}
