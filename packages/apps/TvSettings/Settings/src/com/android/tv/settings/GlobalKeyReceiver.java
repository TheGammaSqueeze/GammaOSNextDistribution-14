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

package com.android.tv.settings;

import static com.android.tv.settings.accessories.AddAccessoryActivity.ACTION_CONNECT_INPUT;
import static com.android.tv.settings.accessories.AddAccessoryActivity.INTENT_EXTRA_NO_INPUT_MODE;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;

/**
 * Handles global keys.
 */
public class GlobalKeyReceiver extends BroadcastReceiver {
    private static final String TAG = "TvGlobalKeyReceiver";
    private static final boolean DEBUG = false;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_GLOBAL_BUTTON.equals(intent.getAction())) {
            KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent.class);
            if (DEBUG) {
                Log.d(TAG, "Received key event " + event.toString());
            }
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_PAIRING:
                    sendPairingIntent(context, event);
                    return;
                default:
                    if (DEBUG) {
                        Log.d(TAG, "Unhandled key " + event.getKeyCode());
                    }
                    break;
            }
        }
    }

    private static void sendPairingIntent(Context context, KeyEvent event) {
        Intent intent =
                new Intent(ACTION_CONNECT_INPUT).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).setPackage(
                        context.getPackageName());
        if (event != null) {
            intent.putExtra(INTENT_EXTRA_NO_INPUT_MODE, true)
                    .putExtra(Intent.EXTRA_KEY_EVENT, event);
        }
        context.startActivity(intent);
    }
}
