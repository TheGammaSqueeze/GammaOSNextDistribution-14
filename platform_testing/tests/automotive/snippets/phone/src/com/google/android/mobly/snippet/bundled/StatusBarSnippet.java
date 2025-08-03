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

package com.google.android.mobly.snippet.bundled;

import android.platform.helpers.HelperAccessor;
import android.platform.helpers.IAutoStatusBarHelper;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.rpc.Rpc;

/** Snippet class for exposing Status Bar App APIs. */
public class StatusBarSnippet implements Snippet {

    private final HelperAccessor<IAutoStatusBarHelper> mStatusBarHelper;

    public StatusBarSnippet() {
        mStatusBarHelper = new HelperAccessor<>(IAutoStatusBarHelper.class);
    }

    @Rpc(description = "Is Bluetooth Button Enabled")
    public boolean isBluetoothButtonEnabled() {
        return mStatusBarHelper.get().isBluetoothButtonEnabled();
    }

    @Rpc(description = "Is Bluetooth Palette Phone Button Enabled")
    public boolean isBluetoothPhoneButtonEnabled() {
        return mStatusBarHelper.get().isBluetoothPhoneButtonEnabled();
    }

    @Rpc(description = "Is Bluetooth Palette PMedia Button Enabled")
    public boolean isBluetoothMediaButtonEnabled() {
        return mStatusBarHelper.get().isBluetoothMediaButtonEnabled();
    }

    /** Clicks on Media Button available on the Bluetooth Palette */
    @Rpc(description = "Click on Bluetooth Palette Media Button")
    public void clickOnBluetoothPaletteMediaButton() {
        mStatusBarHelper.get().clickOnBluetoothPaletteMediaButton();
    }

    @Rpc(description = "is Mobile Connected")
    public boolean isBluetoothConnectedToMobile() {
        return mStatusBarHelper.get().isBluetoothConnectedToMobile();
    }

    @Rpc(description = "is Mobile Disconnected")
    public boolean isBluetoothDisconnected() {
        return mStatusBarHelper.get().isBluetoothDisconnected();
    }

    @Rpc(description = "Open Bluetooth Palette")
    public void openBluetoothPalette() {
        mStatusBarHelper.get().openBluetoothPalette();
    }

    @Rpc(description = "Click Bluetooth Button on the status bar")
    public void clickBluetoothButton() {
        mStatusBarHelper.get().clickBluetoothButton();
    }

    @Rpc(description = "is Bluetooth Connected")
    public boolean isBluetoothConnected() {
        return mStatusBarHelper.get().isBluetoothConnected();
    }

    @Rpc(description = "Press the Home icon on the status bar")
    public void pressHome() {
        mStatusBarHelper.get().open();
    }

    /** Verify device name in Bluetooth Palette */
    @Rpc(description = "Verify Device Name")
    public boolean verifyDeviceName() {
        return mStatusBarHelper.get().verifyDeviceName();
    }

    /** has Bluetooth Button in Bluetooth Palette */
    @Rpc(description = "Verify Bluetooth Button")
    public boolean hasBluetoothButton() {
        return mStatusBarHelper.get().hasBluetoothButton();
    }

    /** has Phone Button in Bluetooth Palette */
    @Rpc(description = "Has Phone Button ")
    public boolean hasBluetoothPalettePhoneButton() {
        return mStatusBarHelper.get().hasBluetoothPalettePhoneButton();
    }
    /** has Media Button in Bluetooth Palette */
    @Rpc(description = "Verify Media Button")
    public boolean hasBluetoothPaletteMediaButton() {
        return mStatusBarHelper.get().hasBluetoothPaletteMediaButton();
    }

    @Override
    public void shutdown() {}
}
