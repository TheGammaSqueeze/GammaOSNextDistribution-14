/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.car.voiceassistinput.sample;

import android.app.ActivityOptions;
import android.car.CarOccupantZoneManager;
import android.car.input.CarInputManager;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;

import androidx.annotation.NonNull;

import java.net.URISyntaxException;
import java.util.List;

public class VoiceAssistEventHandler implements
        CarInputManager.CarInputCaptureCallback {

    private static final String TAG
            = VoiceAssistEventHandler.class.getSimpleName();

    private Context mContext;
    private SampleVoiceAssistInputService mService;
    private CarOccupantZoneManager mCarOccupantZoneManager;

    public VoiceAssistEventHandler(Context context, SampleVoiceAssistInputService service,
            CarOccupantZoneManager carOccupantZoneManager) {
        mContext = context;
        mService = service;
        mCarOccupantZoneManager = carOccupantZoneManager;
    }

    @Override
    public void onKeyEvents(int targetDisplayType,
            @NonNull List<KeyEvent> events) {
        Log.d(TAG, "Received a key event");
        for (KeyEvent event : events) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_VOICE_ASSIST
                    && event.getAction() == KeyEvent.ACTION_UP) {
                launchUserTosRedirectActivity();
            }
        }
    }

    private void launchUserTosRedirectActivity() {
        // Launching assistant is only supported on the main display
        int targetDisplayId = getDisplayIdForDisplayType(CarOccupantZoneManager.DISPLAY_TYPE_MAIN);

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Launching user TOS on display id {" + targetDisplayId + "}");
        }

        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(targetDisplayId);
        Intent tosIntent;
        try {
            tosIntent = Intent.parseUri(
                    mContext.getString(R.string.config_userTosActivityIntentUri),
                    Intent.URI_INTENT_SCHEME);
        } catch (URISyntaxException use) {
            Log.e(TAG, "Could not parse URI for User ToS redirect activity", use);
            return;
        }
        tosIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        mService.startActivity(tosIntent, options.toBundle());
    }


    private int getDisplayIdForDisplayType(int targetDisplayType) {
        int displayId = mCarOccupantZoneManager.getDisplayIdForDriver(targetDisplayType);
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Resolved display id {" + displayId + "} for display type {"
                    + targetDisplayType + "}");
        }
        return displayId;
    }
}
