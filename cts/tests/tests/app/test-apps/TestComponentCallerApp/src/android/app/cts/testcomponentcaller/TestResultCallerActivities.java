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

import static android.app.cts.testcomponentcaller.Constants.TEST_SET_RESULT_ACTIVITY;

import android.app.ComponentCaller;
import android.content.Intent;
import android.util.Log;

public class TestResultCallerActivities {
    public static class TestResultGetCurrentCallerActivity extends TestInitialCallerActivity {
        private static final String TAG = "TestResultGetCurrentCallerActivity";
        @Override
        public void onStart() {
            super.onStart();
            Intent intent = getIntent();
            Log.i(TAG, "onStart: " + intent);
            Intent resultIntent = new Intent(intent);
            resultIntent.setComponent(TEST_SET_RESULT_ACTIVITY);
            resultIntent.setFlags(0);
            startActivityForResult(resultIntent, 0);
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent intent) {
            Log.i(TAG, "onActivityResult: " + intent);
            performTest(intent, getCurrentCaller());
        }
    }

    public static class TestResultOverloadCallerActivity
            extends TestResultGetCurrentCallerActivity {
        private static final String TAG = "TestResultOverloadCallerActivity";
        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent intent,
                ComponentCaller caller) {
            Log.i(TAG, "onActivityResult: " + intent);
            performTest(intent, caller);
        }
    }
}
