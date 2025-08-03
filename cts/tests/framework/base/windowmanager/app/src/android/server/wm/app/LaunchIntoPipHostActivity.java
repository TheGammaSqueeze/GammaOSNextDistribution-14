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

import static android.server.wm.app.Components.PipActivity.ACTION_FINISH_LAUNCH_INTO_PIP_HOST;
import static android.server.wm.app.Components.PipActivity.ACTION_START_LAUNCH_INTO_PIP_CONTAINER;

import android.app.ActivityOptions;
import android.app.PictureInPictureParams;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Rational;

/**
 * Host activity of launch-into-pip test.
 */
public class LaunchIntoPipHostActivity extends AbstractLifecycleLogActivity {
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                switch (intent.getAction()) {
                    case ACTION_START_LAUNCH_INTO_PIP_CONTAINER:
                        startLaunchIntoPipContainerActivity();
                        break;
                    case ACTION_FINISH_LAUNCH_INTO_PIP_HOST:
                        finish();
                        break;
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_START_LAUNCH_INTO_PIP_CONTAINER);
        filter.addAction(ACTION_FINISH_LAUNCH_INTO_PIP_HOST);
        registerReceiver(mReceiver, filter, Context.RECEIVER_EXPORTED);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }

    private void startLaunchIntoPipContainerActivity() {
        final Intent intent = new Intent(this, LaunchIntoPipContainerActivity.class);
        final Rect bounds = new Rect(0, 0, 100, 100);
        final PictureInPictureParams params = new PictureInPictureParams.Builder()
                .setSourceRectHint(bounds)
                .setAspectRatio(new Rational(bounds.width(), bounds.height()))
                .build();
        final ActivityOptions opts = ActivityOptions.makeLaunchIntoPip(params);
        startActivity(intent, opts.toBundle());
    }
}
