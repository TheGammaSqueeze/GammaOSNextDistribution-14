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

package android.server.wm.backgroundactivity.appa;

import android.app.Activity;
import android.content.Intent;
import android.os.SystemClock;

public class RelaunchingActivity extends Activity {

    @Override
    public void onPause() {
        super.onPause();

        // Try to start background activity once it's onPause(), like after pressing home button.
        final Intent intent = new Intent();
        intent.setClass(this, BackgroundActivity.class);
        startActivity(intent);

        // Start activity again after 6s to ensure it's really blocked and can't be resumed.
        new Thread() {
            public void run() {
                SystemClock.sleep(1000 * 6);
                startActivity(intent);
            }
        }.start();
    }
}