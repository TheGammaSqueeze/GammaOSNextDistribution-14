/*
 * Copyright 2018 The Android Open Source Project
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

package com.android.car.settings.bluetooth;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.SystemProperties;
import android.util.AttributeSet;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.common.MultiActionPreference;
import com.android.car.settings.common.ToggleButtonActionItem;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;

/**
 * Preference which represents a specific {@link CachedBluetoothDevice}. The title, icon, and
 * summary are kept in sync with the device when the preference is shown. When the device is busy,
 * the preference is disabled. The equality and sort order of this preference is determined by the
 * underlying cached device {@link CachedBluetoothDevice#equals(Object)} and {@link
 * CachedBluetoothDevice#compareTo(CachedBluetoothDevice)}. If two devices are considered equal, the
 * default preference sort ordering is used (see {@link #compareTo(Preference)}.
 */
public class BluetoothDevicePreference extends MultiActionPreference {

    private final CachedBluetoothDevice mCachedDevice;
    private final boolean mShowDevicesWithoutNames;
    private final boolean mShowDisconnectedStateSubtitle;
    private final CachedBluetoothDevice.Callback mDeviceCallback = this::refreshUi;

    private UpdateToggleButtonListener mUpdateToggleButtonListener;

    public BluetoothDevicePreference(Context context, CachedBluetoothDevice cachedDevice) {
        this(context, cachedDevice, /* showDisconnectedStateSubtitle= */ true);
    }

    public BluetoothDevicePreference(Context context, CachedBluetoothDevice cachedDevice,
            boolean showDisconnectedStateSubtitle) {
        super(context);
        mCachedDevice = cachedDevice;
        mShowDisconnectedStateSubtitle = showDisconnectedStateSubtitle;
        mShowDevicesWithoutNames = SystemProperties.getBoolean(
                BluetoothUtils.BLUETOOTH_SHOW_DEVICES_WITHOUT_NAMES_PROPERTY, false);
    }

    @Override
    protected void init(@Nullable AttributeSet attrs) {
        mActionItemArray[0] = new ToggleButtonActionItem(this);
        mActionItemArray[1] = new ToggleButtonActionItem(this);
        mActionItemArray[2] = new ToggleButtonActionItem(this);
        super.init(attrs);

        // Hide actions by default.
        mActionItemArray[0].setVisible(false);
        mActionItemArray[1].setVisible(false);
        mActionItemArray[2].setVisible(false);
    }

    /**
     * Returns the {@link CachedBluetoothDevice} represented by this preference.
     */
    public CachedBluetoothDevice getCachedDevice() {
        return mCachedDevice;
    }

    /**
     * Sets the {@link UpdateToggleButtonListener} that will be called when the toggle buttons
     * may need to change state.
     */
    public void setToggleButtonUpdateListener(UpdateToggleButtonListener listener) {
        mUpdateToggleButtonListener = listener;
    }

    @Override
    public void onAttached() {
        super.onAttached();
        mCachedDevice.registerCallback(mDeviceCallback);
        refreshUi();
    }

    @Override
    public void onDetached() {
        super.onDetached();
        mCachedDevice.unregisterCallback(mDeviceCallback);
    }

    private void refreshUi() {
        setTitle(mCachedDevice.getName());
        setSummary(mCachedDevice.getCarConnectionSummary(/* shortSummary= */ true,
                mShowDisconnectedStateSubtitle));

        Pair<Drawable, String> pair = com.android.settingslib.bluetooth.BluetoothUtils
                .getBtClassDrawableWithDescription(getContext(), mCachedDevice);
        if (pair.first != null) {
            setIcon(pair.first);
            getIcon().setTintList(getContext().getColorStateList(R.color.icon_color_default));
        }

        setEnabled(!mCachedDevice.isBusy());
        setVisible(mShowDevicesWithoutNames || mCachedDevice.hasHumanReadableName());

        if (mUpdateToggleButtonListener != null) {
            mUpdateToggleButtonListener.updateToggleButtonState(this);
        }
        // Notify since the ordering may have changed.
        notifyHierarchyChanged();
    }

    private CharSequence getConnectionSummary() {
        CharSequence summary = mCachedDevice.getCarConnectionSummary(/* shortSummary= */ true,
                mShowDisconnectedStateSubtitle);

        if (mCachedDevice.isConnected()) {
            Pair<Drawable, String> pair = com.android.settingslib.bluetooth.BluetoothUtils
                    .getBtClassDrawableWithDescription(getContext(), mCachedDevice);
            String connectedDeviceType = pair.second;

            if (connectedDeviceType != null && !connectedDeviceType.isEmpty()) {
                summary += " · " + connectedDeviceType;
            }
        }

        return summary;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BluetoothDevicePreference)) {
            return false;
        }
        return mCachedDevice.equals(((BluetoothDevicePreference) o).mCachedDevice);
    }

    @Override
    public int hashCode() {
        return mCachedDevice.hashCode();
    }

    @Override
    public int compareTo(@NonNull Preference another) {
        if (!(another instanceof BluetoothDevicePreference)) {
            // Rely on default sort.
            return super.compareTo(another);
        }

        return mCachedDevice
                .compareTo(((BluetoothDevicePreference) another).mCachedDevice);
    }

    @Override
    public ToggleButtonActionItem getActionItem(ActionItem actionItem) {
        switch(actionItem) {
            case ACTION_ITEM1:
                return (ToggleButtonActionItem) mActionItemArray[0];
            case ACTION_ITEM2:
                return (ToggleButtonActionItem) mActionItemArray[1];
            case ACTION_ITEM3:
                return (ToggleButtonActionItem) mActionItemArray[2];
            default:
                throw new IllegalArgumentException("Invalid button requested");
        }
    }

    /**
     * Callback for when toggle buttons may need to be updated
     */
    public interface UpdateToggleButtonListener {
        /**
         * Preference state has changed and toggle button changes should be handled.
         *
         * @param preference the preference that has been changed
         */
        void updateToggleButtonState(BluetoothDevicePreference preference);
    }
}
