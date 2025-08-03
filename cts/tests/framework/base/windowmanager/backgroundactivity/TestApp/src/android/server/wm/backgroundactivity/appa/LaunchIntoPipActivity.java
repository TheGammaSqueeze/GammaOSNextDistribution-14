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
import android.app.ActivityOptions;
import android.app.PictureInPictureParams;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

public class LaunchIntoPipActivity extends Activity {
    private Components mA;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ActivityOptions options = ActivityOptions.makeLaunchIntoPip(
                    new PictureInPictureParams.Builder().build());
            Intent pipIntent = new Intent(LaunchIntoPipActivity.this, BackgroundActivity.class);
            startActivity(pipIntent, options.toBundle());
        }
    };

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        mA = Components.get(getApplicationContext());

        IntentFilter filter = new IntentFilter();
        filter.addAction(mA.LAUNCH_INTO_PIP_ACTIONS.LAUNCH_INTO_PIP);
        registerReceiver(mReceiver, filter, Context.RECEIVER_EXPORTED);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }
}
