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

package com.android.tv.settings.accessories;


import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static android.content.Intent.FLAG_RECEIVER_FOREGROUND;

import static com.android.tv.settings.accessories.AddAccessoryActivity.ACTION_CONNECT_INPUT;
import static com.android.tv.settings.accessories.ConnectedDevicesSliceBroadcastReceiver.ACTION_FIND_MY_REMOTE;
import static com.android.tv.settings.accessories.ConnectedDevicesSliceBroadcastReceiver.ACTION_TOGGLE_CHANGED;
import static com.android.tv.settings.accessories.ConnectedDevicesSliceBroadcastReceiver.ACTIVE_AUDIO_OUTPUT;
import static com.android.tv.settings.accessories.ConnectedDevicesSliceBroadcastReceiver.BLUETOOTH_ON;
import static com.android.tv.settings.accessories.ConnectedDevicesSliceBroadcastReceiver.EXTRA_TOGGLE_STATE;
import static com.android.tv.settings.accessories.ConnectedDevicesSliceBroadcastReceiver.EXTRA_TOGGLE_TYPE;
import static com.android.tv.settings.accessories.ConnectedDevicesSliceUtils.EXTRAS_SLICE_URI;
import static com.android.tv.settings.accessories.ConnectedDevicesSliceUtils.EXTRAS_DIRECTION;
import static com.android.tv.settings.accessories.ConnectedDevicesSliceUtils.DIRECTION_BACK;
import static com.android.tv.settings.accessories.ConnectedDevicesSliceUtils.FIND_MY_REMOTE_PHYSICAL_BUTTON_ENABLED_SETTING;
import static com.android.tv.settings.accessories.ConnectedDevicesSliceUtils.isFindMyRemoteButtonEnabled;

import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.app.tvsettings.TvSettingsEnums;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.StrictMode;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;

import androidx.annotation.IntegerRes;
import androidx.core.graphics.drawable.IconCompat;
import androidx.slice.Slice;
import androidx.slice.SliceProvider;

import com.android.settingslib.RestrictedLockUtils;
import com.android.settingslib.RestrictedLockUtilsInternal;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.media.flags.Flags;
import com.android.tv.settings.R;
import com.android.tv.twopanelsettings.slices.builders.PreferenceSliceBuilder;
import com.android.tv.twopanelsettings.slices.builders.PreferenceSliceBuilder.RowBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The SliceProvider for "connected devices" settings */
public class ConnectedDevicesSliceProvider extends SliceProvider implements
        BluetoothDeviceProvider.Listener {

    private static final String TAG = "ConnectedDevices";
    private static final boolean DEBUG = false;
    private static final boolean DISCONNECT_PREFERENCE_ENABLED = false;
    private static final int ACTIVE_AUDIO_OUTPUT_INTENT_REQUEST_CODE = 9;
    private final Map<Uri, Integer> mPinnedUris = new ArrayMap<>();
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private boolean mBtDeviceServiceBound;
    private BluetoothDevicesService.LocalBinder mBtDeviceServiceBinder;

    private final BluetoothDeviceProvider mLocalBluetoothDeviceProvider =
            new LocalBluetoothDeviceProvider() {
                @Override
                BluetoothDeviceProvider getHostBluetoothDeviceProvider() {
                    return getBluetoothDeviceProvider();
                }
    };

    private final ServiceConnection mBtDeviceServiceConnection =
            new SimplifiedConnection() {

                @Override
                public void onServiceConnected(ComponentName className, IBinder service) {
                    mBtDeviceServiceBinder = (BluetoothDevicesService.LocalBinder) service;
                    mBtDeviceServiceBinder.addListener(ConnectedDevicesSliceProvider.this);
                    getContext().getContentResolver()
                            .notifyChange(ConnectedDevicesSliceUtils.GENERAL_SLICE_URI, null);
                }

                @Override
                protected void cleanUp() {
                    if (mBtDeviceServiceBinder != null) {
                        mBtDeviceServiceBinder.removeListener(ConnectedDevicesSliceProvider.this);
                    }
                    mBtDeviceServiceBinder = null;
                }
            };

    static final String KEY_BLUETOOTH_TOGGLE = "bluetooth_toggle";
    static final String KEY_PAIR_REMOTE = "pair_remote";
    static final String KEY_ACCESSORIES = "accessories";
    static final String KEY_OFFICIAL_REMOTES_CATEGORY = "official_remotes_category";
    // Preference key for the remote bundled with the device (Bluetooth based)
    static final String KEY_OFFICIAL_REMOTE = "official_remote";
    // Preference key for the remote bundled with the device (IR based)
    static final String KEY_IR = "ir";
    static final String KEY_CONNECT = "connect";
    static final String KEY_DISCONNECT = "disconnect";
    static final String KEY_RENAME = "rename";
    static final String KEY_FORGET = "forget";
    static final String KEY_EXTRAS_DEVICE = "extra_devices";
    static final String KEY_BLUETOOTH_DEVICE_INFO = "bluetooth_device_info";
    static final String KEY_FIND_MY_REMOTE_TOGGLE = "fmr_toggle";
    static final String KEY_TOGGLE_ACTIVE_AUDIO_OUTPUT = "toggle_active_audio_output";

    static final int YES = R.string.general_action_yes;
    static final int NO = R.string.general_action_no;
    static final int[] YES_NO_ARGS = {YES, NO};

    @Override
    public boolean onCreateSliceProvider() {
        return true;
    }

    @Override
    public PendingIntent onCreatePermissionRequest(Uri sliceUri, String callingPackage) {
        final Intent settingsIntent = new Intent(Settings.ACTION_SETTINGS);
        final PendingIntent noOpIntent = PendingIntent.getActivity(
                getContext(), 0, settingsIntent, PendingIntent.FLAG_IMMUTABLE);
        return noOpIntent;
    }

    @Override
    public void onSlicePinned(Uri sliceUri) {
        mHandler.post(() -> {
            if (DEBUG) {
                Log.d(TAG, "Slice pinned: " + sliceUri);
            }
            Context context = getContext();
            if (!mBtDeviceServiceBound && context.bindService(
                    new Intent(context, AccessoryUtils.getBluetoothDeviceServiceClass()),
                    mBtDeviceServiceConnection,
                    Context.BIND_AUTO_CREATE)) {
                mBtDeviceServiceBound = true;
            }
            if (!mPinnedUris.containsKey(sliceUri)) {
                mPinnedUris.put(sliceUri, 0);
            }
            mPinnedUris.put(sliceUri, mPinnedUris.get(sliceUri) + 1);
        });
    }

    @Override
    public Slice onBindSlice(Uri sliceUri) {
        if (DEBUG) {
            Log.d(TAG, "onBindSlice: " + sliceUri);
        }
        if (getBluetoothDevices().isEmpty()) {
            sliceUri = ConnectedDevicesSliceUtils.GENERAL_SLICE_URI;
        }
        StrictMode.ThreadPolicy oldPolicy = StrictMode.getThreadPolicy();
        try {
            // Prevent StrictMode from throwing on access to shared preferences.
            StrictMode.setThreadPolicy(
                new StrictMode.ThreadPolicy.Builder(oldPolicy).permitDiskReads().build());
            if (ConnectedDevicesSliceUtils.isGeneralPath(sliceUri)) {
                return createGeneralSlice(sliceUri);
            } else if (ConnectedDevicesSliceUtils.isBluetoothDevicePath(sliceUri)) {
                return createBluetoothDeviceSlice(sliceUri);
            } else if (ConnectedDevicesSliceUtils.isFindMyRemotePath(sliceUri)) {
                return createFindMyRemoteSlice(sliceUri);
            }
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }
        return null;
    }

    @Override
    public void onSliceUnpinned(Uri sliceUri) {
        mHandler.post(() -> {
            if (DEBUG) {
                Log.d(TAG, "Slice unpinned: " + sliceUri);
            }
            Context context = getContext();
            // If at this point there is only one slice pinned, we need to unbind the service as
            // there won't be any slice pinned after handleSliceUnpinned is called.
            if (mPinnedUris.containsKey(sliceUri)) {
                int newCount = mPinnedUris.get(sliceUri) - 1;
                mPinnedUris.put(sliceUri, newCount);
                if (newCount == 0) {
                    mPinnedUris.remove(sliceUri);
                }
            }
            if (mPinnedUris.isEmpty() && mBtDeviceServiceBound) {
                context.unbindService(mBtDeviceServiceConnection);
                mBtDeviceServiceBound = false;
            }
        });
    }

    // BluetoothDeviceProvider.Listener implementation
    @Override
    public void onDeviceUpdated(BluetoothDevice device) {
        getContext().getContentResolver()
                .notifyChange(ConnectedDevicesSliceUtils.GENERAL_SLICE_URI, null);
        notifyDeviceSlice(device);
    }

    // The initial slice in the Connected Device flow.
    private Slice createGeneralSlice(Uri sliceUri) {
        PreferenceSliceBuilder psb = new PreferenceSliceBuilder(getContext(), sliceUri);
        psb.addScreenTitle(
                new RowBuilder()
                        .setTitle(getString(R.string.connected_devices_slice_pref_title))
                        .setPageId(TvSettingsEnums.CONNECTED_SLICE));
        updateBluetoothToggle(psb);
        updatePairingButton(psb);
        updateConnectedDevices(psb);
        updateOfficialRemoteSettings(psb);
        updateFmr(psb);
        return psb.build();
    }

    // The slice page that shows detail information of a particular device.
    private Slice createBluetoothDeviceSlice(Uri sliceUri) {
        Context context = getContext();
        String deviceAddr = ConnectedDevicesSliceUtils.getDeviceAddr(sliceUri);
        BluetoothDevice device = BluetoothDevicesService.findDevice(deviceAddr);
        CachedBluetoothDevice cachedDevice =
                AccessoryUtils.getCachedBluetoothDevice(getContext(), device);
        String deviceName = "";
        if (device != null) {
            deviceName = AccessoryUtils.getLocalName(device);
        }

        PreferenceSliceBuilder psb = new PreferenceSliceBuilder(getContext(), sliceUri);
        psb.addScreenTitle(
                new RowBuilder()
                        .setTitle(deviceName)
                        .setPageId(TvSettingsEnums.CONNECTED_SLICE_DEVICE_ENTRY));

        Bundle extras;
        Intent i;
        // Update "Use for TV audio".
        // Set as active audio output device only connected devices that have audio capabilities
        if (Flags.enableTvMediaOutputDialog()
                && cachedDevice != null && !cachedDevice.isBusy()
                && AccessoryUtils.isConnected(device) && cachedDevice.isConnected()
                && (AccessoryUtils.isBluetoothHeadset(device)
                || AccessoryUtils.hasAudioProfile(cachedDevice))) {
            boolean isActive = AccessoryUtils.isActiveAudioOutput(device);

            Intent intent = new Intent(ACTION_TOGGLE_CHANGED);
            intent.setClass(context, ConnectedDevicesSliceBroadcastReceiver.class);
            intent.putExtra(EXTRA_TOGGLE_TYPE, ACTIVE_AUDIO_OUTPUT);
            intent.putExtra(EXTRA_TOGGLE_STATE, !isActive);
            intent.putExtra(KEY_EXTRAS_DEVICE, device);

            PendingIntent pendingIntent = PendingIntent.getBroadcast(context,
                    ACTIVE_AUDIO_OUTPUT_INTENT_REQUEST_CODE, intent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

            // Update set/unset active audio output preference
            RowBuilder activeAudioOutputPref = new RowBuilder()
                    .setKey(KEY_TOGGLE_ACTIVE_AUDIO_OUTPUT)
                    .setTitle(getString(R.string.bluetooth_toggle_active_audio_output_title))
                    .setActionId(
                            TvSettingsEnums.CONNECTED_SLICE_DEVICE_ENTRY_TOGGLE_ACTIVE_AUDIO_OUTPUT)
                    .addSwitch(pendingIntent,
                            context.getText(R.string.bluetooth_toggle_active_audio_output_title),
                            isActive);

            psb.addPreference(activeAudioOutputPref);
        }

        // Update "connect/disconnect preference"
        if (cachedDevice != null && !cachedDevice.isBusy()) {
            // Whether the device is actually connected from CachedBluetoothDevice's perspective.
            boolean isConnected = AccessoryUtils.isConnected(device) && cachedDevice.isConnected();

            if (!isConnected || showDisconnectButton(device, context)) {
                RowBuilder connectionActionPref = new RowBuilder()
                        .setKey(isConnected ? KEY_DISCONNECT : KEY_CONNECT)
                        .setTitle(getString((isConnected
                                ? R.string.bluetooth_disconnect_action_title
                                : R.string.bluetooth_connect_action_title)));
                extras = new Bundle();
                i = new Intent(context, BluetoothActionActivity.class);
                BluetoothActionFragment.prepareArgs(
                        extras,
                        isConnected ? KEY_DISCONNECT : KEY_CONNECT,
                        R.drawable.ic_baseline_bluetooth_searching_large,
                        isConnected
                                ? R.string.bluetooth_disconnect_confirm
                                : R.string.bluetooth_connect_confirm,
                        0,
                        YES_NO_ARGS,
                        deviceName,
                        isConnected ? 1 /* default to NO (index 1) */ : 0 /* default to YES */
                );
                i.putExtras(extras);
                i.putExtra(KEY_EXTRAS_DEVICE, device);
                PendingIntent pendingIntent = PendingIntent.getActivity(
                        context, 3, i,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                Intent followUpIntent =
                        new Intent(context, ConnectedDevicesSliceBroadcastReceiver.class);
                followUpIntent.putExtra(EXTRAS_SLICE_URI, sliceUri.toString());
                PendingIntent followupIntent = PendingIntent.getBroadcast(
                        context, 4, followUpIntent,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                connectionActionPref.setPendingIntent(pendingIntent);
                connectionActionPref.setFollowupPendingIntent(followupIntent);
                psb.addPreference(connectionActionPref);
            }
        }

        // Update "rename preference".
        RowBuilder renamePref = new RowBuilder()
                .setKey(KEY_RENAME)
                .setTitle(getString(R.string.bluetooth_rename_action_title))
                .setActionId(TvSettingsEnums.CONNECTED_SLICE_DEVICE_ENTRY_RENAME);
        extras = new Bundle();
        BluetoothActionFragment.prepareArgs(
                extras,
                KEY_RENAME,
                R.drawable.ic_baseline_bluetooth_searching_large,
                R.string.bluetooth_rename,
                0,
                null,
                deviceName,
                BluetoothActionFragment.DEFAULT_CHOICE_UNDEFINED
        );
        i = new Intent(context, BluetoothActionActivity.class);
        i.putExtra(KEY_EXTRAS_DEVICE, device);
        i.putExtras(extras);
        PendingIntent renamePendingIntent = PendingIntent.getActivity(
                context, 5, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent followUpIntent = new Intent(context, ConnectedDevicesSliceBroadcastReceiver.class);
        followUpIntent.putExtra(EXTRAS_SLICE_URI, sliceUri.toString());
        PendingIntent renameFollowupIntent = PendingIntent.getBroadcast(
                context, 6, followUpIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        renamePref.setFollowupPendingIntent(renameFollowupIntent);
        renamePref.setPendingIntent(renamePendingIntent);
        psb.addPreference(renamePref);

        // Update "forget preference".
        RowBuilder forgetPref = new RowBuilder()
                .setKey(KEY_FORGET)
                .setTitle(getString(R.string.bluetooth_forget_action_title))
                .setActionId(TvSettingsEnums.CONNECTED_SLICE_DEVICE_ENTRY_FORGET);
        extras = new Bundle();
        i = new Intent(context, BluetoothActionActivity.class);
        BluetoothActionFragment.prepareArgs(
                extras,
                KEY_FORGET,
                R.drawable.ic_baseline_bluetooth_searching_large,
                R.string.bluetooth_forget_confirm,
                0,
                YES_NO_ARGS,
                deviceName,
                1 /* default to NO (index 1) */
        );
        i.putExtras(extras);
        i.putExtra(KEY_EXTRAS_DEVICE, device);
        PendingIntent disconnectPendingIntent = PendingIntent.getActivity(
                context, 7, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        followUpIntent = new Intent(context, ConnectedDevicesSliceBroadcastReceiver.class);
        followUpIntent.putExtra(EXTRAS_SLICE_URI, sliceUri.toString());
        followUpIntent.putExtra(EXTRAS_DIRECTION, DIRECTION_BACK);
        PendingIntent forgetFollowupIntent = PendingIntent.getBroadcast(
                context, 8, followUpIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        forgetPref.setPendingIntent(disconnectPendingIntent);
        forgetPref.setFollowupPendingIntent(forgetFollowupIntent);
        psb.addPreference(forgetPref);

        // Update "bluetooth device info preference".
        RowBuilder infoPref = new RowBuilder()
                .setKey(KEY_BLUETOOTH_DEVICE_INFO)
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_baseline_info_24dp));

        infoPref.addInfoItem(getString(R.string.bluetooth_serial_number_label), deviceAddr);
        psb.addPreference(infoPref);
        return psb.build();
    }

    private void updateBluetoothToggle(PreferenceSliceBuilder psb) {
        if (showBluetoothToggle()) {
            Intent bluetoothToggleIntent;
            if (AccessoryUtils.isBluetoothEnabled()) {
                bluetoothToggleIntent = new Intent(getContext(), BluetoothActionActivity.class);
                Bundle extras = new Bundle();
                BluetoothActionFragment.prepareArgs(
                        extras,
                        KEY_BLUETOOTH_TOGGLE,
                        R.drawable.ic_baseline_bluetooth_searching_large,
                        R.string.bluetooth_toggle_confirmation_dialog_title,
                        R.string.bluetooth_toggle_confirmation_dialog_summary,
                        YES_NO_ARGS,
                        null,
                        0 /* default to YES */
                );
                bluetoothToggleIntent.putExtras(extras);
            } else {
                bluetoothToggleIntent = new Intent(ACTION_TOGGLE_CHANGED);
                bluetoothToggleIntent.setClass(
                        getContext(), ConnectedDevicesSliceBroadcastReceiver.class);
                bluetoothToggleIntent.putExtra(EXTRA_TOGGLE_TYPE, BLUETOOTH_ON);
            }
            psb.addPreference(
                    new RowBuilder()
                            .setKey(KEY_BLUETOOTH_TOGGLE)
                            .setIcon(IconCompat.createWithResource(
                                    getContext(), R.drawable.ic_bluetooth_raw))
                            .setIconNeedsToBeProcessed(true)
                            .setTitle(getString(R.string.bluetooth_toggle_title))
                            .addSwitch(
                                    AccessoryUtils.isBluetoothEnabled()
                                            ? PendingIntent.getActivity(
                                                    getContext(), 1, bluetoothToggleIntent,
                                                    PendingIntent.FLAG_IMMUTABLE)
                                            : PendingIntent.getBroadcast(
                                                    getContext(), 2, bluetoothToggleIntent,
                                                    PendingIntent.FLAG_IMMUTABLE),
                                    AccessoryUtils.isBluetoothEnabled())
            );
        }
    }

    private void updatePairingButton(PreferenceSliceBuilder psb) {
        RestrictedLockUtils.EnforcedAdmin admin =
                RestrictedLockUtilsInternal.checkIfRestrictionEnforced(getContext(),
                        UserManager.DISALLOW_CONFIG_BLUETOOTH, UserHandle.myUserId());
        if (AccessoryUtils.isBluetoothEnabled()) {
            PendingIntent pendingIntent;
            if (admin == null) {
                Intent i = new Intent(ACTION_CONNECT_INPUT).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                pendingIntent = PendingIntent
                        .getActivity(getContext(), 3, i, PendingIntent.FLAG_IMMUTABLE);
            } else {
                Intent intent = RestrictedLockUtils.getShowAdminSupportDetailsIntent(getContext(),
                        admin);
                intent.putExtra(DevicePolicyManager.EXTRA_RESTRICTION,
                        UserManager.DISALLOW_CONFIG_BLUETOOTH);
                pendingIntent = PendingIntent.getActivity(getContext(), 0, intent,
                        PendingIntent.FLAG_IMMUTABLE);
            }
            psb.addPreference(new RowBuilder()
                    .setKey(KEY_PAIR_REMOTE)
                    .setTitle(getString(R.string.bluetooth_pair_accessory))
                    .setActionId(TvSettingsEnums.CONNECTED_SLICE_CONNECT_NEW_DEVICES)
                    .setIcon(IconCompat.createWithResource(getContext(),
                            R.drawable.ic_baseline_add_24dp))
                    .setIconNeedsToBeProcessed(true)
                    .setPendingIntent(pendingIntent)
            );
        }
    }

    private void updateConnectedDevices(PreferenceSliceBuilder psb) {
        // Overall BT devices maps
        HashMap<String, BluetoothDevice> addressToDevice = new HashMap<>();
        // Sets for BT devices that are not official remotes:
        // - activeAccessories: they are considered connected from both BluetoothDevice and
        //       CachedBluetoothDevice's perceptive.
        // - inactiveAccessories: they are considered connected from BluetoothDevice's perceptive
        //       but disconnected from CachedBluetoothDevice's perceptive. They can be easily
        //       reconnected.
        // - bondedAccessories: they are considered merely bonded but not connected from
        //       BluetoothDevice's perceptive.
        Set<String> activeAccessories = new HashSet<>();
        Set<String> inactiveAccessories = new HashSet<>();
        Set<String> bondedAccessories = new HashSet<>();

        // Bucketing all BT devices
        for (BluetoothDevice device : getBluetoothDevices()) {
            CachedBluetoothDevice cachedDevice =
                    AccessoryUtils.getCachedBluetoothDevice(getContext(), device);
            if (!AccessoryUtils.isKnownDevice(getContext(), device)) {
                if (AccessoryUtils.isConnected(device)) {
                    addressToDevice.put(device.getAddress(), device);
                    if (cachedDevice != null && cachedDevice.isConnected()) {
                        activeAccessories.add(device.getAddress());
                    } else {
                        inactiveAccessories.add(device.getAddress());
                    }
                } else if (AccessoryUtils.isBonded(device)) {
                    addressToDevice.put(device.getAddress(), device);
                    bondedAccessories.add(device.getAddress());
                }
            }
        }

        // "Accessories" category
        if (activeAccessories.size() + inactiveAccessories.size() + bondedAccessories.size()
                > 0) {
            psb.addPreferenceCategory(new RowBuilder()
                    .setTitle(getContext().getString(R.string.bluetooth_known_devices_category))
                    .setKey(KEY_ACCESSORIES));
            // Add accessories following the ranking of: active, inactive, bonded.
            createAndAddBtDeviceSlicePreferenceFromSet(psb, activeAccessories, addressToDevice);
            createAndAddBtDeviceSlicePreferenceFromSet(psb, inactiveAccessories, addressToDevice);
            createAndAddBtDeviceSlicePreferenceFromSet(psb, bondedAccessories, addressToDevice);
        }
    }

    private void updateOfficialRemoteSettings(PreferenceSliceBuilder psb) {
        String officialRemoteSettingsUri =
                getString(R.string.bluetooth_official_remote_entry_slice_uri);
        String irSettingsUri =
                getString(R.string.bluetooth_ir_entry_slice_uri);
        boolean isOfficialRemoteSettingsUriValid = isSliceProviderValid(officialRemoteSettingsUri);
        boolean isIrSettingsUriValid = isSliceProviderValid(irSettingsUri);
        if (isOfficialRemoteSettingsUriValid || isIrSettingsUriValid) {
            psb.addPreferenceCategory(new RowBuilder()
                    .setKey(KEY_OFFICIAL_REMOTES_CATEGORY)
                    .setTitle(getString(R.string.bluetooth_official_remote_category)));
        }
        if (isIrSettingsUriValid) {
            psb.addPreference(new RowBuilder()
                    .setKey(KEY_IR)
                    .setTitle(getString(R.string.bluetooth_ir_entry_title))
                    .setSubtitle(getString(R.string.bluetooth_ir_entry_subtitle))
                    .setTargetSliceUri(irSettingsUri));
        }
        if (isOfficialRemoteSettingsUriValid) {
            psb.addPreference(new RowBuilder()
                    .setKey(KEY_OFFICIAL_REMOTE)
                    .setTitle(getString(R.string.bluetooth_official_remote_entry_title))
                    .setTargetSliceUri(officialRemoteSettingsUri));
        }
    }

    private void updateFmr(PreferenceSliceBuilder psb) {
        List<ResolveInfo> receivers = getContext().getPackageManager().queryBroadcastReceivers(
                new Intent(ACTION_FIND_MY_REMOTE), 0);
        if (receivers.isEmpty()) {
            return;
        }

        psb.addPreference(new RowBuilder()
                .setKey(KEY_FIND_MY_REMOTE_TOGGLE)
                .setTitle(getString(R.string.settings_find_my_remote_title))
                .setSubtitle(getString(R.string.settings_find_my_remote_description))
                .setTargetSliceUri(ConnectedDevicesSliceUtils.FIND_MY_REMOTE_SLICE_URI.toString()));
    }

    private void createAndAddBtDeviceSlicePreferenceFromSet(
            PreferenceSliceBuilder psb,
            Set<String> addresses,
            HashMap<String, BluetoothDevice> addressesToBtDeviceMap) {
        if (psb == null || addresses == null || addresses.isEmpty()
                || addressesToBtDeviceMap == null || addressesToBtDeviceMap.isEmpty()) {
            return;
        }
        final List<String> devicesAddressesList = new ArrayList<>(addresses);
        Collections.sort(devicesAddressesList);
        for (String deviceAddr : devicesAddressesList) {
            psb.addPreference(
                    createBtDeviceSlicePreference(
                            getContext(),
                            addressesToBtDeviceMap.get(deviceAddr)));
        }
    }

    private PreferenceSliceBuilder.RowBuilder createBtDeviceSlicePreference(
            Context context, BluetoothDevice device) {
        PreferenceSliceBuilder.RowBuilder pref = new PreferenceSliceBuilder.RowBuilder();
        boolean isConnected = AccessoryUtils.isConnected(device)
                && AccessoryUtils.getCachedBluetoothDevice(getContext(), device) != null
                && AccessoryUtils.getCachedBluetoothDevice(getContext(), device).isConnected();
        pref.setKey(device.getAddress());
        pref.setTitle(AccessoryUtils.getLocalName(device));
        pref.setSubtitle(
                isConnected
                        ? getString(R.string.bluetooth_connected_status)
                        : getString(R.string.bluetooth_disconnected_status));
        pref.setIcon(IconCompat.createWithResource(
                context, AccessoriesFragment.getImageIdForDevice(device, true)));
        pref.setIconNeedsToBeProcessed(true);

        RestrictedLockUtils.EnforcedAdmin admin =
                RestrictedLockUtilsInternal.checkIfRestrictionEnforced(getContext(),
                        UserManager.DISALLOW_CONFIG_BLUETOOTH, UserHandle.myUserId());
        if (admin == null) {
            Uri targetSliceUri = ConnectedDevicesSliceUtils
                    .getDeviceUri(device.getAddress(), device.getAlias());
            pref.setTargetSliceUri(targetSliceUri.toString());
        } else {
            Intent intent = RestrictedLockUtils.getShowAdminSupportDetailsIntent(getContext(),
                    admin);
            intent.putExtra(DevicePolicyManager.EXTRA_RESTRICTION,
                    UserManager.DISALLOW_CONFIG_BLUETOOTH);
            pref.setPendingIntent(PendingIntent.getActivity(getContext(), 0, intent,
                    PendingIntent.FLAG_IMMUTABLE));
        }
        return pref;
    }

    private List<BluetoothDevice> getBluetoothDevices() {
        if (mBtDeviceServiceBinder != null) {
            return mBtDeviceServiceBinder.getDevices();
        }
        return new ArrayList<>();
    }

    private BluetoothDeviceProvider getBluetoothDeviceProvider() {
        return mBtDeviceServiceBinder;
    }

    private void notifyDeviceSlice(BluetoothDevice device) {
        String addr = device.getAddress();
        mHandler.post(() -> {
            if (device != null) {
                getContext().getContentResolver().notifyChange(
                        ConnectedDevicesSliceUtils.getDeviceUri(addr, device.getAlias()), null);
            }
        });
    }

    private boolean showBluetoothToggle() {
        return getContext().getResources().getBoolean(R.bool.show_bluetooth_toggle);
    }

    private String getString(@IntegerRes int resId) {
        return getContext().getString(resId);
    }

    private boolean isSliceProviderValid(String uri) {
        return !TextUtils.isEmpty(uri)
                && ConnectedDevicesSliceUtils.isSliceProviderValid(getContext(), uri);
    }

    private boolean showDisconnectButton(BluetoothDevice device, Context context) {
        if (DISCONNECT_PREFERENCE_ENABLED) {
            return true;
        }
        return !AccessoryUtils.isRemoteClass(device)
                && !AccessoryUtils.isKnownDevice(context, device);
    }

    private Slice createFindMyRemoteSlice(Uri sliceUri) {
        Context context = getContext();
        final PreferenceSliceBuilder psb = new PreferenceSliceBuilder(context, sliceUri);
        psb.addScreenTitle(new RowBuilder()
                .setTitle(getString(R.string.settings_find_my_remote_title))
                .setSubtitle(getString(R.string.find_my_remote_slice_description)));

        if (context.getResources().getBoolean(R.bool.config_find_my_remote_integration_enabled)) {
            boolean isButtonEnabled = isFindMyRemoteButtonEnabled(context);
            Intent intent = new Intent(ACTION_TOGGLE_CHANGED);
            intent.putExtra(EXTRA_TOGGLE_TYPE, FIND_MY_REMOTE_PHYSICAL_BUTTON_ENABLED_SETTING);
            intent.putExtra(EXTRA_TOGGLE_STATE, !isButtonEnabled);
            intent.setClass(context, ConnectedDevicesSliceBroadcastReceiver.class);
            psb.addPreference(new RowBuilder()
                    .setKey(FIND_MY_REMOTE_PHYSICAL_BUTTON_ENABLED_SETTING)
                    .setTitle(getString(R.string.find_my_remote_integration_title))
                    .setSubtitle(getString(R.string.find_my_remote_integration_hint))
                    .addSwitch(
                            PendingIntent.getBroadcast(
                                    context, 0, intent, FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT),
                            !isButtonEnabled));
        }

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 0,
                new Intent(context, ConnectedDevicesSliceBroadcastReceiver.class)
                        .setAction(ACTION_FIND_MY_REMOTE)
                        .setFlags(FLAG_RECEIVER_FOREGROUND),
                FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT);

        psb.addPreference(new RowBuilder()
                .setKey(ACTION_FIND_MY_REMOTE)
                .setTitle(getString(R.string.find_my_remote_play_sound))
                .setPendingIntent(pendingIntent)
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_play_arrow))
                .setIconNeedsToBeProcessed(true));
        return psb.build();
    }
}
