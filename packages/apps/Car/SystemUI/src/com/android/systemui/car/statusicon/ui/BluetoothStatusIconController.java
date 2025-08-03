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

package com.android.systemui.car.statusicon.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import androidx.annotation.VisibleForTesting;

import com.android.systemui.R;
import com.android.systemui.car.statusicon.StatusIconController;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.statusbar.policy.BluetoothController;

import javax.inject.Inject;

/**
 * A controller for Bluetooth status icon.
 */
public class BluetoothStatusIconController extends StatusIconController implements
        BluetoothController.Callback {
    private final Context mContext;
    private final BluetoothController mBluetoothController;

    private boolean mBluetoothEnabled;
    private boolean mBluetoothConnected;
    private Drawable mBluetoothOffDrawable;
    private Drawable mBluetoothOnDisconnectedDrawable;
    private Drawable mBluetoothOnConnectedDrawable;
    private String mBluetoothOffContentDescription;
    private String mBluetoothOnDisconnectedContentDescription;
    private String mBluetoothOnConnectedContentDescription;

    @Inject
    BluetoothStatusIconController(
            Context context,
            @Main Resources resources,
            BluetoothController bluetoothController) {
        mContext = context;
        mBluetoothController = bluetoothController;

        mBluetoothOffDrawable = resources.getDrawable(
                R.drawable.ic_bluetooth_status_off, /* theme= */ null);
        mBluetoothOnDisconnectedDrawable = resources.getDrawable(
                R.drawable.ic_bluetooth_status_on_disconnected, /* theme= */ null);
        mBluetoothOnConnectedDrawable = resources.getDrawable(
                R.drawable.ic_bluetooth_status_on_connected, /* theme= */ null);

        mBluetoothOffContentDescription = resources.getString(
                R.string.status_icon_bluetooth_off);
        mBluetoothOnDisconnectedContentDescription = resources.getString(
                R.string.status_icon_bluetooth_disconnected);
        mBluetoothOnConnectedContentDescription = resources.getString(
                R.string.status_icon_bluetooth_connected);

        mBluetoothConnected = !mBluetoothController.getConnectedDevices().isEmpty();
        mBluetoothController.addCallback(this);
    }

    @Override
    protected void onDestroy() {
        mBluetoothController.removeCallback(this);
    }

    @Override
    protected void updateStatus() {
        if (!mBluetoothEnabled) {
            setIconDrawableToDisplay(mBluetoothOffDrawable);
            setIconContentDescription(mBluetoothOffContentDescription);
        } else if (mBluetoothConnected) {
            setIconDrawableToDisplay(mBluetoothOnConnectedDrawable);
            setIconContentDescription(mBluetoothOnConnectedContentDescription);
        } else {
            setIconDrawableToDisplay(mBluetoothOnDisconnectedDrawable);
            setIconContentDescription(mBluetoothOnDisconnectedContentDescription);
        }
        onStatusUpdated();
    }

    @Override
    public void onBluetoothStateChange(boolean enabled) {
        mBluetoothEnabled = enabled;
        updateStatus();
    }

    @Override
    public void onBluetoothDevicesChanged() {
        mBluetoothConnected = !mBluetoothController.getConnectedDevices().isEmpty();
        updateStatus();
    }

    @VisibleForTesting
    Drawable getBluetoothOffDrawable() {
        return mBluetoothOffDrawable;
    }

    @Override
    protected int getPanelContentLayout() {
        return R.layout.qc_bluetooth_panel;
    }

    @VisibleForTesting
    Drawable getBluetoothOnDisconnectedDrawable() {
        return mBluetoothOnDisconnectedDrawable;
    }

    @VisibleForTesting
    Drawable getBluetoothOnConnectedDrawable() {
        return mBluetoothOnConnectedDrawable;
    }

    @Override
    protected int getId() {
        return R.id.qc_bluetooth_status_icon;
    }
}
