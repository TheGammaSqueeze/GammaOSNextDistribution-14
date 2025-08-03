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

package com.android.systemui.car.drivemode;

import android.content.Context;
import android.content.om.OverlayInfo;
import android.content.om.OverlayManager;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.systemui.dagger.SysUISingleton;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

/**
 * A class that activates and deactivates RROs based on the selected drive mode.
 */
@SysUISingleton
public class DriveModeThemeSwitcher implements DriveModeManager.Callback {

    private static final boolean DEBUG = false;
    private static final String TAG = "DriveModeThemeSwitcher";
    private static final String RRO_TARGET_PACKAGE = "android";
    private static final String RRO_PACKAGE = "drivemode.modes";

    private final OverlayManager mOverlayManager;
    private final Map<String, String> mDriveModeOverlays;

    @Inject
    DriveModeThemeSwitcher(Context context, DriveModeManager driveModeManager) {
        mOverlayManager = context.getSystemService(OverlayManager.class);
        if (mOverlayManager != null) {
            mDriveModeOverlays = createDriveModeToRROPackageMap();
            driveModeManager.addCallback(this);
        } else {
            mDriveModeOverlays = new HashMap<>();
            if (DEBUG) Log.e(TAG, "No overlay manager");
        }
    }

    @Override
    public void onDriveModeChanged(@NonNull String newDriveMode) {
        String overlayName = mDriveModeOverlays.getOrDefault(newDriveMode, null);
        if (isOverlayActive(overlayName)) {
            return;
        }
        disableActiveOverlays();
        enableOverlay(mDriveModeOverlays.get(newDriveMode));
    }

    private boolean isOverlayActive(String packageName) {
        if (mOverlayManager == null || TextUtils.isEmpty(packageName)) {
            return false;
        }

        OverlayInfo overlayInfo = mOverlayManager.getOverlayInfo(packageName, UserHandle.CURRENT);
        return overlayInfo != null && overlayInfo.isEnabled();
    }

    private void enableOverlay(String packageName) {
        if (mOverlayManager == null || packageName == null) {
            return;
        }
        mOverlayManager.setEnabled(packageName, true, UserHandle.CURRENT);
    }

    private void disableActiveOverlays() {
        if (mOverlayManager == null) {
            return;
        }

        mDriveModeOverlays.forEach((overlayName, overlayPackage) -> {
            if (mOverlayManager.getOverlayInfo(overlayPackage, UserHandle.CURRENT).isEnabled()) {
                mOverlayManager.setEnabled(overlayPackage, false, UserHandle.CURRENT);
            }
        });
    }

    /*
     * Method that initializes a map of <DriveMode, RRO Package> pairs. For convenience, the drive
     * mode is extracted directly from the RRO package in this iteration. In a real world scenario
     * there would be a separate mapping of the DriveMode to its RRO package
     * (e.g. an array in Resources).
     */
    private HashMap<String, String> createDriveModeToRROPackageMap() {
        List<OverlayInfo> rroList = mOverlayManager.getOverlayInfosForTarget(
                RRO_TARGET_PACKAGE,
                UserHandle.CURRENT
        );

        HashMap<String, String> driveModeOverlays = new HashMap<>();
        rroList.forEach(rro -> {
            if (rro.packageName.contains(RRO_PACKAGE)) {
                driveModeOverlays.put(extractRROName(rro.packageName), rro.packageName);
            }
        });
        return driveModeOverlays;
    }

    private String extractRROName(String overlayPackage) {
        String name;
        try {
            String[] split = overlayPackage.split("\\.");
            String rawName = split[split.length - 2];
            name =  rawName.substring(0, 1).toUpperCase() + rawName.substring(1);
        } catch (IndexOutOfBoundsException e) {
            Log.e(TAG, "Error extracting the name from the RRO " + overlayPackage);
            name = "Name not found";
        } catch (NullPointerException e) {
            Log.e(TAG, "Error extracting the name from the RRO, overlayPackage is null.");
            name = "Name not found";
        }

        return name;
    }
}
