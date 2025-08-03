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
 * limitations under the License
 */

package android.server.wm.app;

import static android.server.wm.app.Components.HideOverlayWindowsActivity.ACTION;
import static android.server.wm.app.Components.HideOverlayWindowsActivity.MOTION_EVENT_EXTRA;
import static android.server.wm.app.Components.HideOverlayWindowsActivity.PONG;
import static android.server.wm.app.Components.HideOverlayWindowsActivity.REPORT_TOUCH;
import static android.server.wm.app.Components.HideOverlayWindowsActivity.SHOULD_HIDE;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.MotionEvent;

/**
 * Helper activity for HideApplicationOverlaysTest. Communication is handled through a pair of
 * broadcast receivers, this activity is receiving commands from the tests and emits a pong
 * message when the commands have been executed.
 */
public class HideOverlayWindowsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerReceiver(mBroadcastReceiver, new IntentFilter(ACTION), Context.RECEIVER_EXPORTED);
    }

    BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION.equals(intent.getAction())) {
                boolean booleanExtra = intent.getBooleanExtra(SHOULD_HIDE, false);
                getWindow().setHideOverlayWindows(booleanExtra);
                sendBroadcast(new Intent(PONG));
            }
        }
    };

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        sendBroadcast(new Intent(REPORT_TOUCH).putExtra(MOTION_EVENT_EXTRA, event));
        return super.dispatchTouchEvent(event);
    }
}
