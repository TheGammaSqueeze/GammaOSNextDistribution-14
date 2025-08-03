/*
 * Copyright (C) 2021 The Android Open Source Project
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

/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.app.stubs;

import android.content.Intent;
import android.util.Log;

/**
 * Foreground Service with location type.
 */
public class LocalForegroundServiceSticky extends LocalForegroundService {
    private static final String TAG = "LocalForegroundServiceSticky";
    public static String STICKY_FLAG =
            "android.app.stubs.LocalForegroundServiceSticky.sticky_flag";
    public static String ACTION_RESTART_FGS_STICKY_RESULT =
            "android.app.stubs.LocalForegroundServiceSticky.STICKY_RESULT";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            Log.d(TAG, "LocalForegroundServiceSticky.onStartCommand: null intent");
            sendBroadcast(new Intent(ACTION_RESTART_FGS_STICKY_RESULT).setFlags(
                    Intent.FLAG_RECEIVER_FOREGROUND));
            return START_STICKY;
        } else {
            super.onStartCommand(intent, flags, startId);
            final int stickyFlag = intent.getIntExtra(STICKY_FLAG, START_NOT_STICKY);
            Log.d(TAG, "LocalForegroundServiceSticky.onStartCommand, return " + stickyFlag);
            return stickyFlag;
        }
    }
}
