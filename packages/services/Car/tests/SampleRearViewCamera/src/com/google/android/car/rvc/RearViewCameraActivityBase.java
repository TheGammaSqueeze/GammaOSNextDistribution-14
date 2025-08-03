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

package com.google.android.car.rvc;

import static android.hardware.display.DisplayManager.DisplayListener;

import android.app.Activity;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.View;

/**
 * The base class of SampleRearViewCameraActivity.
 * The Activity should have 'showWhenLocked' attribute to show it in KeyLocked state, but
 * the attribute also affects the lifecycle of Activity not to move to PAUSED state even if
 * display is off, so we install DisplayListener to monitor the display state change and it'll
 * change the visibility of the internal View depending on the display state.
 */
public class RearViewCameraActivityBase extends Activity {
    private static final String TAG = RearViewCameraActivityBase.class.getSimpleName();

    private DisplayManager mDisplayManager;

    // The Activity with showWhenLocked doesn't go to sleep even if the display sleeps.
    // So we'd like to monitor the display state and react on it manually.
    private final DisplayListener mDisplayListener = new DisplayListener() {
        @Override
        public void onDisplayAdded(int displayId) {}

        @Override
        public void onDisplayRemoved(int displayId) {}

        @Override
        public void onDisplayChanged(int displayId) {
            if (displayId != Display.DEFAULT_DISPLAY) {
                return;
            }
            decideViewVisibility();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // To show the Activity when KeyLock is shown.
        setShowWhenLocked(true);
        mDisplayManager = getSystemService(DisplayManager.class);
        mDisplayManager.registerDisplayListener(mDisplayListener, null);
        decideViewVisibility();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mDisplayManager.unregisterDisplayListener(mDisplayListener);
    }

    // Hides the view when the display is off to save the system resource, since this has
    // 'showWhenLocked' attribute, this will not go to PAUSED state even if the display turns off.
    private void decideViewVisibility() {
        Display defaultDisplay = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
        int state = defaultDisplay.getState();
        Log.d(TAG, "decideShowWhenLocked: displayState=" + state);
        getWindow().getDecorView().setVisibility(
                state == Display.STATE_ON ? View.VISIBLE : View.INVISIBLE);
    }
}
