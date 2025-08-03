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

package com.android.car.settings.qc;

import static android.os.UserManager.DISALLOW_BLUETOOTH;
import static android.os.UserManager.DISALLOW_CONFIG_BLUETOOTH;

import static com.android.car.qc.QCItem.QC_ACTION_TOGGLE_STATE;
import static com.android.car.qc.QCItem.QC_TYPE_ACTION_TOGGLE;
import static com.android.car.settings.qc.QCUtils.getActionDisabledDialogIntent;
import static com.android.car.settings.qc.QCUtils.getAvailabilityStatusForZoneFromXml;

import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemProperties;

import androidx.annotation.DrawableRes;
import androidx.annotation.VisibleForTesting;

import com.android.car.qc.QCActionItem;
import com.android.car.qc.QCItem;
import com.android.car.qc.QCList;
import com.android.car.qc.QCRow;
import com.android.car.settings.R;
import com.android.car.settings.bluetooth.BluetoothUtils;
import com.android.car.settings.common.Logger;
import com.android.car.settings.enterprise.EnterpriseUtils;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.HidProfile;
import com.android.settingslib.bluetooth.LocalBluetoothManager;
import com.android.settingslib.bluetooth.LocalBluetoothProfile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * QCItem for showing paired bluetooth devices.
 */
public class PairedBluetoothDevices extends SettingsQCItem {
    @VisibleForTesting
    static final String EXTRA_DEVICE_KEY = "BT_EXTRA_DEVICE_KEY";
    @VisibleForTesting
    static final String EXTRA_BUTTON_TYPE = "BT_EXTRA_BUTTON_TYPE";
    @VisibleForTesting
    static final String BLUETOOTH_BUTTON = "BLUETOOTH_BUTTON";
    @VisibleForTesting
    static final String PHONE_BUTTON = "PHONE_BUTTON";
    @VisibleForTesting
    static final String MEDIA_BUTTON = "MEDIA_BUTTON";
    private static final Logger LOG = new Logger(PairedBluetoothDevices.class);

    private final LocalBluetoothManager mBluetoothManager;
    private final int mDeviceLimit;
    private final boolean mShowDevicesWithoutNames;

    public PairedBluetoothDevices(Context context) {
        super(context);
        setAvailabilityStatusForZone(getAvailabilityStatusForZoneFromXml(context,
                R.xml.bluetooth_settings_fragment, R.string.pk_bluetooth_paired_devices));
        mBluetoothManager = LocalBluetoothManager.getInstance(context, /* onInitCallback= */ null);
        mDeviceLimit = context.getResources().getInteger(
                R.integer.config_qc_bluetooth_device_limit);
        mShowDevicesWithoutNames = SystemProperties.getBoolean(
                BluetoothUtils.BLUETOOTH_SHOW_DEVICES_WITHOUT_NAMES_PROPERTY, false);
    }

    @Override
    QCItem getQCItem() {
        if (!getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
                || EnterpriseUtils.hasUserRestrictionByDpm(getContext(), DISALLOW_BLUETOOTH)
                || mDeviceLimit == 0 || isHiddenForZone()) {
            return null;
        }

        QCList.Builder listBuilder = new QCList.Builder();

        if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
            listBuilder.addRow(new QCRow.Builder()
                    .setIcon(Icon.createWithResource(getContext(),
                            R.drawable.ic_settings_bluetooth_disabled))
                    .setTitle(getContext().getString(R.string.qc_bluetooth_off_devices_info))
                    .build());
            return listBuilder.build();
        }

        Collection<CachedBluetoothDevice> cachedDevices =
                mBluetoothManager.getCachedDeviceManager().getCachedDevicesCopy();

        //TODO(b/198339129): Use BluetoothDeviceFilter.BONDED_DEVICE_FILTER
        Set<BluetoothDevice> bondedDevices = mBluetoothManager.getBluetoothAdapter()
                .getBondedDevices();

        List<CachedBluetoothDevice> filteredDevices = new ArrayList<>();
        for (CachedBluetoothDevice cachedDevice : cachedDevices) {
            if (bondedDevices != null && bondedDevices.contains(cachedDevice.getDevice())) {
                filteredDevices.add(cachedDevice);
            }
        }
        filteredDevices.sort(Comparator.naturalOrder());

        if (filteredDevices.isEmpty()) {
            listBuilder.addRow(new QCRow.Builder()
                    .setIcon(Icon.createWithResource(getContext(),
                            R.drawable.ic_add))
                    .setTitle(getContext().getString(R.string.qc_bluetooth_on_no_devices_info))
                    .build());
            return listBuilder.build();
        }

        int i = 0;
        int deviceLimit = mDeviceLimit >= 0 ? Math.min(mDeviceLimit, filteredDevices.size())
                : filteredDevices.size();
        for (int j = 0; j < deviceLimit; j++) {
            CachedBluetoothDevice cachedDevice = filteredDevices.get(j);
            if (mShowDevicesWithoutNames || cachedDevice.hasHumanReadableName()) {
                listBuilder.addRow(new QCRow.Builder()
                        .setTitle(cachedDevice.getName())
                        .setSubtitle(cachedDevice.getCarConnectionSummary(/* shortSummary= */ true))
                        .setIcon(Icon.createWithResource(getContext(), getIconRes(cachedDevice)))
                        .addEndItem(createBluetoothButton(cachedDevice, i++))
                        .addEndItem(createPhoneButton(cachedDevice, i++))
                        .addEndItem(createMediaButton(cachedDevice, i++))
                        .build()
                );
            }
        }

        return listBuilder.build();
    }

    @Override
    Uri getUri() {
        return SettingsQCRegistry.PAIRED_BLUETOOTH_DEVICES_URI;
    }

    @Override
    void onNotifyChange(Intent intent) {
        String deviceKey = intent.getStringExtra(EXTRA_DEVICE_KEY);
        if (deviceKey == null) {
            return;
        }
        CachedBluetoothDevice device = null;
        Collection<CachedBluetoothDevice> cachedDevices =
                mBluetoothManager.getCachedDeviceManager().getCachedDevicesCopy();
        for (CachedBluetoothDevice cachedDevice : cachedDevices) {
            if (cachedDevice.getAddress().equals(deviceKey)) {
                device = cachedDevice;
                break;
            }
        }
        if (device == null) {
            return;
        }

        String buttonType = intent.getStringExtra(EXTRA_BUTTON_TYPE);
        boolean newState = intent.getBooleanExtra(QC_ACTION_TOGGLE_STATE, true);
        if (BLUETOOTH_BUTTON.equals(buttonType)) {
            if (newState) {
                LocalBluetoothProfile phoneProfile = getProfile(device,
                        BluetoothProfile.HEADSET_CLIENT);
                LocalBluetoothProfile mediaProfile = getProfile(device, BluetoothProfile.A2DP_SINK);
                // If trying to connect and both phone and media are disabled, connecting will
                // always fail. In this case force both profiles on.
                if (phoneProfile != null && mediaProfile != null
                        && !phoneProfile.isEnabled(device.getDevice())
                        && !mediaProfile.isEnabled(device.getDevice())) {
                    phoneProfile.setEnabled(device.getDevice(), true);
                    mediaProfile.setEnabled(device.getDevice(), true);
                }
                device.connect();
            } else if (device.isConnected()) {
                device.disconnect();
            }
        } else if (PHONE_BUTTON.equals(buttonType)) {
            LocalBluetoothProfile profile = getProfile(device, BluetoothProfile.HEADSET_CLIENT);
            if (profile != null) {
                profile.setEnabled(device.getDevice(), newState);
            }
        } else if (MEDIA_BUTTON.equals(buttonType)) {
            LocalBluetoothProfile profile = getProfile(device, BluetoothProfile.A2DP_SINK);
            if (profile != null) {
                profile.setEnabled(device.getDevice(), newState);
            }
        } else {
            LOG.d("Unknown button type: " + buttonType);
        }
    }

    @Override
    Class getBackgroundWorkerClass() {
        return PairedBluetoothDevicesWorker.class;
    }

    @DrawableRes
    private int getIconRes(CachedBluetoothDevice device) {
        BluetoothClass btClass = device.getBtClass();
        if (btClass != null) {
            switch (btClass.getMajorDeviceClass()) {
                case BluetoothClass.Device.Major.COMPUTER:
                    return com.android.internal.R.drawable.ic_bt_laptop;
                case BluetoothClass.Device.Major.PHONE:
                    return com.android.internal.R.drawable.ic_phone;
                case BluetoothClass.Device.Major.PERIPHERAL:
                    return HidProfile.getHidClassDrawable(btClass);
                case BluetoothClass.Device.Major.IMAGING:
                    return com.android.internal.R.drawable.ic_settings_print;
                default:
                    // unrecognized device class; continue
            }
        }

        List<LocalBluetoothProfile> profiles = device.getProfiles();
        for (LocalBluetoothProfile profile : profiles) {
            int resId = profile.getDrawableResource(btClass);
            if (resId != 0) {
                return resId;
            }
        }
        if (btClass != null) {
            if (btClass.doesClassMatch(BluetoothClass.PROFILE_HEADSET)) {
                return com.android.internal.R.drawable.ic_bt_headset_hfp;
            }
            if (btClass.doesClassMatch(BluetoothClass.PROFILE_A2DP)) {
                return com.android.internal.R.drawable.ic_bt_headphones_a2dp;
            }
        }
        return com.android.internal.R.drawable.ic_settings_bluetooth;
    }

    private QCActionItem createBluetoothButton(CachedBluetoothDevice device, int requestCode) {
        return createBluetoothDeviceToggle(device, requestCode, BLUETOOTH_BUTTON,
                Icon.createWithResource(getContext(), R.drawable.ic_qc_bluetooth),
                getContext().getString(
                        R.string.bluetooth_bonded_bluetooth_toggle_content_description),
                true, !device.isBusy(), false, device.isConnected());
    }

    private QCActionItem createPhoneButton(CachedBluetoothDevice device, int requestCode) {
        BluetoothProfileToggleState phoneState = getBluetoothProfileToggleState(device,
                BluetoothProfile.HEADSET_CLIENT);
        int iconRes = phoneState.mIsAvailable ? R.drawable.ic_qc_bluetooth_phone
                : R.drawable.ic_qc_bluetooth_phone_unavailable;
        return createBluetoothDeviceToggle(device, requestCode, PHONE_BUTTON,
                Icon.createWithResource(getContext(), iconRes),
                getContext().getString(R.string.bluetooth_bonded_phone_toggle_content_description),
                phoneState.mIsAvailable, phoneState.mIsEnabled,
                phoneState.mIsClickableWhileDisabled, phoneState.mIsChecked);
    }

    private QCActionItem createMediaButton(CachedBluetoothDevice device, int requestCode) {
        BluetoothProfileToggleState mediaState = getBluetoothProfileToggleState(device,
                BluetoothProfile.A2DP_SINK);
        int iconRes = mediaState.mIsAvailable ? R.drawable.ic_qc_bluetooth_media
                : R.drawable.ic_qc_bluetooth_media_unavailable;
        return createBluetoothDeviceToggle(device, requestCode, MEDIA_BUTTON,
                Icon.createWithResource(getContext(), iconRes),
                getContext().getString(R.string.bluetooth_bonded_media_toggle_content_description),
                mediaState.mIsAvailable, mediaState.mIsEnabled,
                mediaState.mIsClickableWhileDisabled, mediaState.mIsChecked);
    }

    private QCActionItem createBluetoothDeviceToggle(CachedBluetoothDevice device, int requestCode,
            String buttonType, Icon icon, String contentDescription, boolean available,
            boolean enabled,
            boolean clickableWhileDisabled, boolean checked) {
        Bundle extras = new Bundle();
        extras.putString(EXTRA_BUTTON_TYPE, buttonType);
        extras.putString(EXTRA_DEVICE_KEY, device.getAddress());
        PendingIntent action = getBroadcastIntent(extras, requestCode);

        boolean isReadOnlyForZone = isReadOnlyForZone();
        PendingIntent disabledPendingIntent = isReadOnlyForZone
                ? QCUtils.getDisabledToastBroadcastIntent(getContext())
                : getActionDisabledDialogIntent(getContext(), DISALLOW_CONFIG_BLUETOOTH);

        return new QCActionItem.Builder(QC_TYPE_ACTION_TOGGLE)
                .setAvailable(available)
                .setChecked(checked)
                .setEnabled(enabled && isWritableForZone())
                .setClickableWhileDisabled(clickableWhileDisabled | isReadOnlyForZone)
                .setAction(action)
                .setDisabledClickAction(disabledPendingIntent)
                .setIcon(icon)
                .setContentDescription(contentDescription)
                .build();
    }

    private LocalBluetoothProfile getProfile(CachedBluetoothDevice device, int profileId) {
        for (LocalBluetoothProfile profile : device.getProfiles()) {
            if (profile.getProfileId() == profileId) {
                return profile;
            }
        }
        return null;
    }

    private BluetoothProfileToggleState getBluetoothProfileToggleState(CachedBluetoothDevice device,
            int profileId) {
        LocalBluetoothProfile profile = getProfile(device, profileId);
        if (!device.isConnected() || profile == null) {
            return new BluetoothProfileToggleState(false, false, false, false);
        }
        boolean hasUmRestrictions = EnterpriseUtils.hasUserRestrictionByUm(getContext(),
                DISALLOW_CONFIG_BLUETOOTH);
        boolean hasDpmRestrictions = EnterpriseUtils.hasUserRestrictionByDpm(getContext(),
                DISALLOW_CONFIG_BLUETOOTH);
        return new BluetoothProfileToggleState(true, !hasDpmRestrictions && !hasUmRestrictions
                && !device.isBusy(), hasDpmRestrictions, profile.isEnabled(device.getDevice()));
    }

    private static class BluetoothProfileToggleState {
        final boolean mIsAvailable;
        final boolean mIsEnabled;
        final boolean mIsClickableWhileDisabled;
        final boolean mIsChecked;

        BluetoothProfileToggleState(boolean isAvailable, boolean isEnabled,
                boolean isClickableWhileDisabled, boolean isChecked) {
            mIsAvailable = isAvailable;
            mIsEnabled = isEnabled;
            mIsClickableWhileDisabled = isClickableWhileDisabled;
            mIsChecked = isChecked;
        }
    }
}
