/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.google.android.car.kitchensink.drivemode;

import android.annotation.Nullable;
import android.content.Context;
import android.content.om.OverlayInfo;
import android.content.om.OverlayManager;
import android.os.UserHandle;
import android.util.Log;
import android.widget.Toast;

/**
 * A class that provides methods to enable and disable the DriveMode Switch on SystemUI.
 * The Drive Mode switch is a button in the Quick Control Area that allows the user to switch
 * between pre-defined driving modes (e.g. Comfort, Eco and Sport).
 */
public final class DriveModeSwitchController {

    private static final String DRIVE_MODE_ON_RRO_PACKAGE = "com.android.systemui.drivemode.on.rro";
    private static final String TAG = DriveModeSwitchController.class.getSimpleName();

    @Nullable
    private final OverlayManager mOverlayManager;
    private final Context mContext;

    public DriveModeSwitchController(Context context) {
        mContext = context;
        mOverlayManager = context.getSystemService(OverlayManager.class);
        if (overlayNotSupported()) {
            showToast("OverlayManager not available");
        }
    }

    /**
     * Returns the current state of the DriveMode Switch
     */
    boolean isDriveModeActive() {
        if (overlayNotSupported()) {
            return false;
        }

        OverlayInfo overlayInfo = mOverlayManager.getOverlayInfo(DRIVE_MODE_ON_RRO_PACKAGE,
                UserHandle.SYSTEM);
        return overlayInfo != null && overlayInfo.isEnabled();
    }

    /**
     * Enables or disables the DriveMode Switch
     */
    public void setDriveMode(boolean newState) {
        if (overlayNotSupported()) {
            return;
        }

        mOverlayManager.setEnabled(DRIVE_MODE_ON_RRO_PACKAGE, newState, UserHandle.SYSTEM);
        showToast("New state: %s. Please reboot to apply changes.", newState);
    }

    private boolean overlayNotSupported() {
        return mOverlayManager == null;
    }

    private void showToast(String msg, Object... args) {
        Toast.makeText(mContext, String.format(msg, args), Toast.LENGTH_LONG).show();
        Log.i(TAG, String.format(msg, args));
    }
}
