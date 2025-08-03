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

package com.android.systemui.car.userpicker;

import static android.os.UserHandle.USER_SYSTEM;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.UserHandle;
import android.util.Log;
import android.util.Slog;

final class ActivityHelper {
    private static final String TAG = ActivityHelper.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    static boolean startUserPickerAsUserSystem(Activity activity) {
        int userId = activity.getUserId();
        int displayId = activity.getDisplayId();

        // "Trampoline pattern": restarting itself as user 0 so the user picker can stay
        // when the user that launched the user picker logs out of the display.
        if (userId != USER_SYSTEM) {
            if (DEBUG) {
                Slog.d(TAG, "Calling user is not system, so use trampoline to go to system user, "
                        + "and stop this user(" + userId + ") for logout!");
            }
            Context context = activity.getApplicationContext();
            activity.finish();

            Intent intent = new Intent(context, UserPickerActivity.class)
                    .setData(Uri.parse("data://com.android.car/userpicker/display" + displayId));
            ActivityOptions options = ActivityOptions.makeBasic()
                    .setLaunchDisplayId(displayId);
            context.startActivityAsUser(intent, options.toBundle(), UserHandle.SYSTEM);
            return false;
        }
        return true;
    }
}
