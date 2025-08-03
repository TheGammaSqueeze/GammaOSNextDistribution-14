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

package android.alarmmanager.alarmtestapp.cts.common;

import android.app.ForegroundServiceStartNotAllowedException;
import android.content.Context;
import android.content.Intent;

public class FgsTester {
    public static final String EXTRA_FGS_START_RESULT =
            "android.alarmmanager.alarmtestapp.cts.common.extra.FGS_START_RESULT";

    private FgsTester() {
    }

    public static String tryStartingFgs(Context context) {
        String result;
        try {
            // Try starting a foreground service.
            Intent i = new Intent(context, TestService.class);
            context.startForegroundService(i);

            result = ""; // Indicates success
        } catch (ForegroundServiceStartNotAllowedException e) {
            result = "ForegroundServiceStartNotAllowedException was thrown";
        } catch (Exception e) {
            result = "Unexpected exception was thrown: " + e;
        }
        return result;
    }
}
