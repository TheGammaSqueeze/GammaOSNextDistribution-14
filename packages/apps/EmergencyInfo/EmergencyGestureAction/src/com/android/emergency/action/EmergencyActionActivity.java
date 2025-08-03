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

package com.android.emergency.action;

import static com.android.settingslib.emergencynumber.EmergencyNumberUtils.EMERGENCY_SETTING_ON;
import static com.android.settingslib.emergencynumber.EmergencyNumberUtils.EMERGENCY_SETTING_OFF;

import android.annotation.Nullable;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

import com.android.emergency.action.EmergencyActionUtils;
import com.android.emergency.action.R;

/**
 * Activity for handling emergency action.
 */
public class EmergencyActionActivity extends FragmentActivity {

    private static final String TAG = "EmergencyAction";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.emergency_action_activity);
        if (Settings.Secure.getInt(getContentResolver(),
                Settings.Secure.EMERGENCY_GESTURE_ENABLED,
                EmergencyActionUtils.isDefaultEmergencyGestureEnabled(this) ?
                        EMERGENCY_SETTING_ON : EMERGENCY_SETTING_OFF)
                != EMERGENCY_SETTING_ON) {
            Log.w(TAG, "Emergency gesture is not enabled, exiting");
            finish();
            return;
        }
        getWindow().setDecorFitsSystemWindows(false);
        WindowInsetsController controller = getWindow().getInsetsController();
        if (controller != null) {
            controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            controller.setSystemBarsBehavior(
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }

        if (savedInstanceState == null) {
            EmergencyActionFragment albumFragment = new EmergencyActionFragment();
            getSupportFragmentManager().beginTransaction().add(android.R.id.content,
                    albumFragment).commit();
        }
    }

    @Override
    public void onBackPressed() {
        Log.i(TAG, "Not responding to back press intentionally");
    }
}
