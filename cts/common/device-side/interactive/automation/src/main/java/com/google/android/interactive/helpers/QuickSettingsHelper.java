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

package com.google.android.interactive.helpers;

import android.util.Log;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.bedstead.nene.TestApis;

/**
 * Helper for working with the quick settings in automations.
 */
public final class QuickSettingsHelper {
    private static final String TAG = "QuickSettingsHelper";
    private static final int MAX_RETRY = 4;

    private QuickSettingsHelper() {

    }
    /**
     * Opens quick settings and finds the tile with the given label.
     */
    public static UiObject2 findTileWithLabel(String label) {
        UiDevice device = TestApis.ui().device();
        if (!openQuickSettings(device)) {
            throw new IllegalStateException("Could not open quick settings");
        }

        device.waitForIdle();
        UiObject2 count = device.wait(
                Until.findObject(By.res("com.android.systemui:id/footer_page_indicator")), 5000);
        int num = count.getChildren().size();
        UiObject2 quickSettings = device.findObject(By.res("com.android.systemui:id/qs_pager"));
        if (quickSettings == null) {
            throw new IllegalStateException("Could not find the quick settings pager");
        }

        for (int i = 0; i < num; i++) {
            quickSettings.swipe(Direction.LEFT, 1f);
            UiObject2 tile = device.findObject(By.text(label));
            if (tile != null) {
                return tile;
            }
        }
        return null;
    }

    private static boolean openQuickSettings(UiDevice device) {
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            if (device.openQuickSettings()) {
                return true;
            } else {
                Log.e(TAG, "Opening quick settings failed with attempt: " + attempt);
            }
        }
        return false;
    }
}
