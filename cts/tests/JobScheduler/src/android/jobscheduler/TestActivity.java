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

package android.jobscheduler;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;

public class TestActivity extends Activity {
    private static final String TAG = TestActivity.class.getSimpleName();
    private static final String PACKAGE_NAME = "android.jobscheduler";

    public static final String ACTION_FINISH_ACTIVITY = PACKAGE_NAME + ".action.FINISH_ACTIVITY";

    final BroadcastReceiver mFinishReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Finishing test activity: " + TestActivity.class.getCanonicalName());
            unregisterReceiver(mFinishReceiver);
            finish();
        }
    };

    @Override
    public void onCreate(Bundle savedInstance) {
        Log.d(TAG, "Started test activity: " + TestActivity.class.getCanonicalName());
        super.onCreate(savedInstance);
        registerReceiver(mFinishReceiver, new IntentFilter(ACTION_FINISH_ACTIVITY),
                Context.RECEIVER_EXPORTED);
    }
}
