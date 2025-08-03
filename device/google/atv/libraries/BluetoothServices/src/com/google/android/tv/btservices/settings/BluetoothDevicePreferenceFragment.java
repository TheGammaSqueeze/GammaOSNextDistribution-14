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

import static com.google.android.tv.btservices.pairing.BluetoothPairer.STATUS_CANCELLED;
import static com.google.android.tv.btservices.pairing.BluetoothPairer.STATUS_CONNECTING;
import static com.google.android.tv.btservices.pairing.BluetoothPairer.STATUS_DONE;
import static com.google.android.tv.btservices.pairing.BluetoothPairer.STATUS_ERROR;
import static com.google.android.tv.btservices.pairing.BluetoothPairer.STATUS_PAIRING;
import static com.google.android.tv.btservices.pairing.BluetoothPairer.STATUS_TIMEOUT;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.leanback.preference.LeanbackPreferenceFragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;

import com.google.android.tv.btservices.BluetoothUtils;
import com.google.android.tv.btservices.R;
import com.google.android.tv.btservices.pairing.BluetoothPairingService;

public class BluetoothDevicePreferenceFragment extends LeanbackPreferenceFragment
        implements ResponseFragment.Listener, BluetoothDeviceProvider.Listener,
        BluetoothPairingService.PairingListener {

    private static final String TAG = "Atv.BtDevPrefFragment";

    public interface ServiceProvider {
        BluetoothPairingService.LocalBinder getBluetoothPairingServiceBinder();
    }

    private PreferenceGroup mPrefGroup;
    private BluetoothDevice mDevice;

    private static final String ARG_BT_DEVICE = "bt_device";

    public static final String KEY_BT_DEVICE_PREF = "key_bt_device";
    static final String KEY_RENAME = "key_rename";
    static final String KEY_CONNECT = "key_connect";
    static final String KEY_DISCONNECT = "key_disconnect";
    static final String KEY_FORGET = "key_forget";
    static final String KEY_UPDATE= "key_update";
    private static final String KEY_RECONNECT = "key_reconnect";
    static final int YES = R.string.settings_choices_yes;
    static final int NO = R.string.settings_choices_no;
    static final int CONTINUE = R.string.settings_continue;
    static final int CANCEL = R.string.settings_cancel;

    static final boolean ENABLE_DISCONNECT_OPTION = false;
    private static final int UPDATE_AFTER_RENAME_MS = 1000;

    static final int[] YES_NO_ARGS = {YES, NO};
    static final int[] CONT_CANCEL_ARGS = {CONTINUE, CANCEL};

    private static final int ORDER_UPDATE = 9;
    private static final int ORDER_RECONNECT = 10;
    private static final int ORDER_RENAME = 11;
    private static final int ORDER_DISCONNECT = 12;
    private static final int ORDER_FORGET= 13;
    private static final int ORDER_INFO = 14;

    private boolean mVisible = false;

    private final Handler mHandler = new Handler();

    private BluetoothDeviceInfoPreference mDeviceInfoPreference;

    public static void buildArgs(Bundle bundle, BluetoothDevice device) {
        bundle.putParcelable(ARG_BT_DEVICE, device);
    }

    public static BluetoothDevicePreferenceFragment newInstance(BluetoothDevice device) {
        BluetoothDevicePreferenceFragment fragment = new BluetoothDevicePreferenceFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_BT_DEVICE, device);
        fragment.setArguments(args);
        return fragment;
    }

    private void update() {
        if (!mVisible) {
            return;
        }

        if (mDevice != null) {
            mPrefGroup.setTitle(BluetoothUtils.getName(mDevice));
        }

        updateRemoteUpdate();
        updateReconnect();

        if (mDeviceInfoPreference != null) {
            mDeviceInfoPreference.update();
        }
    }

    private void updateRemoteUpdate() {
        final Context preferenceContext = getPreferenceManager().getContext();
        BluetoothUtils.isRemoteClass(mDevice);
        if (!BluetoothUtils.isRemote(preferenceContext, mDevice)) {
            return;
        }
        BluetoothDeviceProvider btDeviceProvider = getBluetoothDeviceProvider();
        Preference pref = mPrefGroup.findPreference(KEY_UPDATE);
        if (pref == null) {
            pref = new Preference(preferenceContext);
            pref.setKey(KEY_UPDATE);
            pref.setOrder(ORDER_UPDATE);

            ResponseFragment.prepareArgs(
                pref.getExtras(),
                KEY_UPDATE,
                R.string.settings_bt_update,
                R.string.settings_bt_update_summary,
                0,
                CONT_CANCEL_ARGS,
                null,
                ResponseFragment.DEFAULT_CHOICE_UNDEFINED
            );
            pref.setFragment(ResponseFragment.class.getCanonicalName());
            mPrefGroup.addPreference(pref);
        }

        if (btDeviceProvider.hasUpgrade(mDevice)) {
            pref.setEnabled(true);
            pref.setSelectable(true);
            pref.setTitle(R.string.settings_bt_update);
            if (btDeviceProvider.isBatteryLow(mDevice)) {
                pref.setSummary(R.string.settings_bt_battery_low);
                pref.setEnabled(false);
            } else {
                pref.setSummary(R.string.settings_bt_update_software_available);
            }
        } else {
            pref.setTitle(R.string.settings_bt_update_not_necessary);
            pref.setSummary(null);
            pref.setEnabled(false);
            pref.setSelectable(false);
        }
    }

    private void updateReconnect() {
        final Context preferenceContext = getPreferenceManager().getContext();
        if (mDevice == null || BluetoothUtils.isRemote(preferenceContext, mDevice)) {
            return;
        }
        Preference reconnectPref = mPrefGroup.findPreference(KEY_RECONNECT);
        if (getPairingServiceBinder() != null && BluetoothUtils.isBonded(mDevice)) {
            if (reconnectPref == null) {
                reconnectPref = new Preference(preferenceContext);
                reconnectPref.setKey(KEY_RECONNECT);
                reconnectPref.setTitle(R.string.bluetooth_connect);
                reconnectPref.setOrder(ORDER_RECONNECT);
                reconnectPref.setOnPreferenceClickListener((pref) -> {
                    BluetoothPairingService.LocalBinder pairingService = getPairingServiceBinder();
                    if (pairingService != null) {
                        pairingService.connectPairedDevice(mDevice);
                        pref.setEnabled(false);
                        pref.setTitle(R.string.settings_bt_pair_status_connecting);
                    }
                    return true;
                });
                mPrefGroup.addPreference(reconnectPref);
            }
            reconnectPref.setEnabled(true);
            reconnectPref.setVisible(true);
        } else if (reconnectPref != null) {
            reconnectPref.setEnabled(false);
            reconnectPref.setVisible(false);
        }
    }

    private void updatePairingStatusImpl(int status) {
        if (!mVisible) {
            return;
        }
        Preference pref = mPrefGroup.findPreference(KEY_RECONNECT);
        String resStr;
        String text;
        switch (status) {
            case STATUS_PAIRING:
            case STATUS_CONNECTING:
                pref.setEnabled(false);
                pref.setTitle(R.string.settings_bt_pair_status_connecting);
                break;
            case STATUS_CANCELLED:
            case STATUS_TIMEOUT:
            case STATUS_ERROR:
                pref.setEnabled(true);
                pref.setTitle(R.string.bluetooth_connect);
                resStr = getResources().getString(R.string.settings_bt_pair_toast_fail);
                text = String.format(resStr, mDevice.getName());
                Toast.makeText(getActivity(), text, Toast.LENGTH_LONG).show();
                break;
            case STATUS_DONE:
                pref.setEnabled(true);
                pref.setTitle(R.string.bluetooth_connect);
                resStr = getResources().getString(R.string.settings_bt_pair_toast_connected);
                text = String.format(resStr, mDevice.getName());
                Toast.makeText(getActivity(), text, Toast.LENGTH_LONG).show();
                break;
        }
    }

    private BluetoothDeviceProvider getBluetoothDeviceProvider() {
        if (!(getTargetFragment() instanceof ConnectedDevicesPreferenceFragment)) {
            return null;
        }
        return ((ConnectedDevicesPreferenceFragment) getTargetFragment())
                .getBluetoothDeviceProvider();
    }

    private BluetoothPairingService.LocalBinder getPairingServiceBinder() {
        if (!(getActivity() instanceof ServiceProvider)) {
            return null;
        }
        return ((ServiceProvider) getActivity()).getBluetoothPairingServiceBinder();
    }

    public BluetoothDevice getDevice() {
        return mDevice;
    }

    // ResponseFragment.Listener
    @Override
    public void onChoice(String key, int choice) {
        BluetoothDeviceProvider provider = getBluetoothDeviceProvider();
        getFragmentManager().popBackStackImmediate();

        if (provider == null) {
            return;
        }
        if (KEY_DISCONNECT.equals(key)) {
            if (choice == YES) {
                // TODO: disconnect device.
            }
        } else if (KEY_FORGET.equals(key)) {
            if (choice == YES) {
                provider.forgetDevice(mDevice);
            }
        } else if (KEY_UPDATE.equals(key)) {
            if (choice == CONTINUE && mDevice != null) {
                Context context = getContext();
                Intent intent = new Intent(context, RemoteDfuActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra(RemoteDfuActivity.EXTRA_BT_ADDRESS, mDevice.getAddress());
                context.startActivity(intent);
            }
        }
    }

    // ResponseFragment.Listener
    @Override
    public void onText(String key, String text) {
        getFragmentManager().popBackStackImmediate();
        BluetoothDeviceProvider provider = getBluetoothDeviceProvider();
        if (provider == null) {
            return;
        }
        if (KEY_RENAME.equals(key)) {
            if (mDevice != null) {
                provider.renameDevice(mDevice, text);
                mHandler.postDelayed(this::update, UPDATE_AFTER_RENAME_MS);
            }
        }
    }

    // BluetoothDeviceProvider.Listener
    @Override
    public void onDeviceUpdated(BluetoothDevice device) {
        if (mDevice == null || !TextUtils.equals(mDevice.getAddress(), device.getAddress())) {
            return;
        }
        mHandler.post(this::update);
    }

    // BluetoothPairingService.PairingListener implementation
    @Override
    public void updatePairingStatus(BluetoothDevice device, int status) {
        if (!TextUtils.equals(mDevice.getAddress(), device.getAddress())) {
            return;
        }
        mHandler.post(() -> this.updatePairingStatusImpl(status));
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BluetoothDeviceProvider provider = getBluetoothDeviceProvider();
        if (provider != null) {
            provider.addListener(this);
        }
        BluetoothPairingService.LocalBinder pairingService = getPairingServiceBinder();
        if (pairingService != null) {
            pairingService.addPairingListener(this);
        }
    }

    @Override
    public void onDestroy() {
        BluetoothDeviceProvider provider = getBluetoothDeviceProvider();
        if (provider != null) {
            provider.removeListener(this);
        }
        BluetoothPairingService.LocalBinder pairingService = getPairingServiceBinder();
        if (pairingService != null) {
            pairingService.removePairingListener(this);
        }
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        mVisible = true;
        update();
    }

    @Override
    public void onPause() {
        mVisible = false;
        super.onPause();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        final Context preferenceContext = getPreferenceManager().getContext();
        BluetoothDeviceProvider btDeviceProvider = getBluetoothDeviceProvider();

        Bundle args = getArguments();
        mDevice = args.getParcelable(ARG_BT_DEVICE);
        mPrefGroup = getPreferenceManager().createPreferenceScreen(preferenceContext);
        mPrefGroup.setOrderingAsAdded(false);
        mPrefGroup.setKey(KEY_BT_DEVICE_PREF);

        String deviceName = "";
        if (mDevice != null) {
            deviceName = BluetoothUtils.getName(mDevice);
            mPrefGroup.setTitle(deviceName);
        }

        updateRemoteUpdate();
        updateReconnect();

        Preference renamePref = new Preference(preferenceContext);
        renamePref.setKey(KEY_RENAME);
        renamePref.setTitle(R.string.bluetooth_rename);
        renamePref.setOrder(ORDER_RENAME);
        ResponseFragment.prepareArgs(
                renamePref.getExtras(),
                KEY_RENAME,
                R.string.settings_bt_rename,
                0,
                R.drawable.ic_baseline_bluetooth_searching_large,
                null,
                deviceName,
                ResponseFragment.DEFAULT_CHOICE_UNDEFINED
        );
        renamePref.setFragment(ResponseFragment.class.getCanonicalName());
        mPrefGroup.addPreference(renamePref);

        if (ENABLE_DISCONNECT_OPTION) {
            Preference disconnectPref = new Preference(preferenceContext);
            disconnectPref.setKey(KEY_DISCONNECT);
            disconnectPref.setTitle(R.string.bluetooth_disconnect);
            disconnectPref.setOrder(ORDER_DISCONNECT);
            ResponseFragment.prepareArgs(
                    disconnectPref.getExtras(),
                    KEY_DISCONNECT,
                    R.string.settings_bt_disconnect,
                    0,
                    R.drawable.ic_baseline_bluetooth_searching_large,
                    YES_NO_ARGS,
                    deviceName,
                    1 /* default to NO (index 1) */
            );
            disconnectPref.setFragment(ResponseFragment.class.getCanonicalName());
            mPrefGroup.addPreference(disconnectPref);
        }

        Preference forgetPref = new Preference(preferenceContext);
        forgetPref.setKey(KEY_FORGET);
        forgetPref.setTitle(R.string.bluetooth_forget);
        forgetPref.setOrder(ORDER_FORGET);
        ResponseFragment.prepareArgs(
                forgetPref.getExtras(),
                KEY_FORGET,
                R.string.settings_bt_forget,
                0,
                R.drawable.ic_baseline_bluetooth_searching_large,
                YES_NO_ARGS,
                deviceName,
                1 /* default to NO (index 1) */
        );
        forgetPref.setFragment(ResponseFragment.class.getCanonicalName());
        mPrefGroup.addPreference(forgetPref);

        mDeviceInfoPreference =
                new BluetoothDeviceInfoPreference(preferenceContext, btDeviceProvider, mDevice);
        mDeviceInfoPreference.setOrder(ORDER_INFO);
        mPrefGroup.addPreference(mDeviceInfoPreference);

        setPreferenceScreen((PreferenceScreen) mPrefGroup);
    }
}
