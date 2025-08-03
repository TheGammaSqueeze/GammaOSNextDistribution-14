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

package com.android.car.settings.qc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.android.car.settings.CarSettingsApplication;

/** Shows toast message for qc disabled for zones */
public class DisabledQCToastBroadcastReceiver extends BroadcastReceiver {

    static final String DISABLED_QC_TOAST_KEY = "DISABLED_QC_TOAST_KEY";

    @Override
    public void onReceive(Context context, Intent i) {
        String message = i.getStringExtra(DISABLED_QC_TOAST_KEY);

        if (message != null && !message.isEmpty()) {
            int myDisplayId = ((CarSettingsApplication) context.getApplicationContext())
                    .getMyOccupantZoneDisplayId();
            // As a system context, is mapped to display 0 by default. Update to correct display.
            context.updateDisplay(myDisplayId);

            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        }
    }
}
