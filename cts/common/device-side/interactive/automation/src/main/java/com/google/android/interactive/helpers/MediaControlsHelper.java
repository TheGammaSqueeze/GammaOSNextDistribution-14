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

import android.graphics.Rect;
import android.util.Log;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.bedstead.nene.TestApis;

/**
 * Helper for working with media controls (UMO) in automations.
 */
public final class MediaControlsHelper {

    private static final String TAG = "MediaControlsHelper";

    private static final int MAX_RETRY = 4;
    private static final String PKG = "com.android.systemui";
    private static final int MAX_SWIPES = 12;
    private static final int SCROLL_SPEED = 1000;
    private static final int WAIT_TIME_MILLIS = 5000;

    private static final String SESSION_TITLE = "GTS-Interactive App";

    /**
     *  Opens Quick settings and find media controls under test.
     **/
    public static UiObject2 findMediaControls() {
        UiDevice device = TestApis.ui().device();
        if (!openQuickSettings(device)) {
            throw new IllegalStateException("Could not open quick settings");
        }

        // Find the carousel and set up margins so that swipes will not hit the seek bar, which is
        // unfortunately exactly in the middle of the player.
        device.waitForIdle();
        UiObject2 car = device.wait(
                Until.findObject(By.hasChild(By.res(PKG, "media_carousel"))),
                WAIT_TIME_MILLIS);
        if (car == null) throw new AssertionError("Didn't find media carousel");
        Rect carBounds = car.getVisibleBounds();
        final int width = carBounds.right - carBounds.left;
        car.setGestureMargins((int) (0.1 * width), 0, (int) (0.1 * width),
                (int) (0.5 * (carBounds.bottom - carBounds.top)));
        for (int i = 0; i < MAX_SWIPES; i++) {
            if (!car.scroll(Direction.LEFT, .5f, SCROLL_SPEED)) {
                break;
            }
        }

        // Now, scroll forward looking for the player under test.
        final BySelector playerSelector = By.res(PKG, "qs_media_controls").hasDescendant(
                By.res(PKG, "header_title").text(SESSION_TITLE));
        UiObject2 player = null;
        for (int i = 0; i < MAX_SWIPES; i++) {
            player = car.findObject(playerSelector);
            if (player != null) {
                break;
            }
            car.scroll(Direction.RIGHT, .5f, SCROLL_SPEED);
        }
        return player;
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

    /**
     * Finds child inside UiObject2 given its resource id.
     **/
    public static UiObject2 findChildObject(UiObject2 container, String resource) {
        return container.wait(Until.findObject(By.res(PKG, resource)), WAIT_TIME_MILLIS);
    }
}
