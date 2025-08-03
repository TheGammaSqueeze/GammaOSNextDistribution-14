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

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/**
 * Receive pending intent and launch it
 */
public class StartPendingIntentActivity extends Activity {

    public static final String TAG = "StartPendingIntentActivity";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Components app = Components.get(getApplicationContext());

        Intent intent = getIntent();
        final PendingIntent pendingIntent = intent.getParcelableExtra(
                app.START_PENDING_INTENT_ACTIVITY_EXTRA.PENDING_INTENT);
        try {
            final Bundle bundle = intent.getBundleExtra(
                        app.START_PENDING_INTENT_ACTIVITY_EXTRA.START_BUNDLE);
            Log.i(TAG, "pendingIntent.send with bundle: " + bundle);
            pendingIntent.send(bundle);
        } catch (PendingIntent.CanceledException e) {
            throw new AssertionError(e);
        }
    }
}
