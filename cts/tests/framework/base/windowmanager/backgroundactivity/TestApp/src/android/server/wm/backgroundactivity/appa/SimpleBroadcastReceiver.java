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

package android.server.wm.backgroundactivity.appa;

import static android.server.wm.backgroundactivity.common.CommonComponents.EVENT_NOTIFIER_EXTRA;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.ResultReceiver;
import android.server.wm.backgroundactivity.common.CommonComponents.Event;
import android.util.Log;

/**
 * A class to help test case to start background activity.
 */
public class SimpleBroadcastReceiver extends BroadcastReceiver {

    public static final String TAG = "StartBackgroundActivityReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String app = context.getPackageName() + "@" + context.getApplicationInfo().targetSdkVersion;
        Log.i(TAG, "onReceive " + app + " " + intent);

        ResultReceiver eventNotifier = intent.getParcelableExtra(EVENT_NOTIFIER_EXTRA);
        if (eventNotifier != null) {
            eventNotifier.send(Event.BROADCAST_RECEIVED, null);
        }
    }
}
