/*
 * Copyright 2023 The Android Open Source Project
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
package android.media.cts;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Messenger;
import android.util.Log;

/**
 * Helper class to interact with the {@link LocalMediaProjectionService}.
 */
public final class ForegroundServiceUtil {
    private static final String TAG = "LocalMediaProjectionServiceUtil";

    static final int MSG_START_FOREGROUND_DONE = 1;
    static final int MSG_SERVICE_DESTROYED = 2;
    static final String EXTRA_MESSENGER = "messenger";


    /**
     * Helper function to request to start the foreground service.
     */
    public static void requestStartForegroundService(Context context, ComponentName name,
            Runnable serviceStarted, Runnable serviceStopped) {
        final Messenger messenger = new Messenger(new Handler(Looper.getMainLooper(), msg -> {
            switch (msg.what) {
                case MSG_START_FOREGROUND_DONE:
                    if (serviceStarted != null) {
                        serviceStarted.run();
                    }
                    return true;
                case MSG_SERVICE_DESTROYED:
                    if (serviceStopped != null) {
                        serviceStopped.run();
                    }
                    return true;
            }
            Log.e(TAG, "Unknown message from the LocalMediaProjectionService: " + msg.what);
            return false;
        }));
        final Intent intent = new Intent().setComponent(name)
                .putExtra(EXTRA_MESSENGER, messenger);
        context.startForegroundService(intent);
    }
}
