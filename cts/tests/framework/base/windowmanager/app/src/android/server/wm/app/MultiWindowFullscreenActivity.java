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

package android.server.wm.app;

import static android.server.wm.app.Components.MultiWindowFullscreenActivity.ACTION_REQUEST_FULLSCREEN;
import static android.server.wm.app.Components.MultiWindowFullscreenActivity.ACTION_RESTORE_FREEFORM;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

public class MultiWindowFullscreenActivity extends Activity {
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                switch (intent.getAction()) {
                    case ACTION_REQUEST_FULLSCREEN:
                        requestFullscreenMode(FULLSCREEN_MODE_REQUEST_ENTER, null);
                        break;
                    case ACTION_RESTORE_FREEFORM:
                        requestFullscreenMode(FULLSCREEN_MODE_REQUEST_EXIT, null);
                        break;
                }
            }
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        final IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_REQUEST_FULLSCREEN);
        filter.addAction(ACTION_RESTORE_FREEFORM);
        registerReceiver(mReceiver, filter, RECEIVER_EXPORTED);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(mReceiver);
    }
}
