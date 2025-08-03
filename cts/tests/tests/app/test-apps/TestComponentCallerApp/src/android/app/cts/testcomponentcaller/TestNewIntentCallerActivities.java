/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.app.cts.testcomponentcaller;

import static android.app.cts.testcomponentcaller.Constants.TEST_NEW_INTENT_GET_CURRENT_CALLER_ACTIVITY;
import static android.app.cts.testcomponentcaller.Constants.TEST_NEW_INTENT_OVERLOAD_CALLER_ACTIVITY;

import android.app.ComponentCaller;
import android.content.ComponentName;
import android.content.Intent;
import android.util.Log;

public class TestNewIntentCallerActivities {
    public static class TestNewIntentGetCurrentCallerActivity extends TestInitialCallerActivity {
        private static final String TAG = "TestNewIntentGetCurrentCallerActivity";
        @Override
        public void onNewIntent(Intent intent) {
            Log.i(TAG, "in onNewIntent: " + intent);
            performTest(intent, getCurrentCaller());
            finish();
        }

        @Override
        public ComponentName getTestActivity() {
            return TEST_NEW_INTENT_GET_CURRENT_CALLER_ACTIVITY;
        }
    }

    public static class TestNewIntentOverloadCallerActivity extends TestInitialCallerActivity {
        private static final String TAG = "TestNewIntentOverloadCallerActivity";
        @Override
        public void onNewIntent(Intent intent, ComponentCaller caller) {
            Log.i(TAG, "in onNewIntent: " + intent);
            performTest(intent, caller);
            finish();
        }

        @Override
        public ComponentName getTestActivity() {
            return TEST_NEW_INTENT_OVERLOAD_CALLER_ACTIVITY;
        }
    }
}
