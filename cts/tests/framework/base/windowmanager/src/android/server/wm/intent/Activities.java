/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.server.wm.intent;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * A collection of activities with various launch modes used in the intent tests.
 * Each activity is named after the LaunchMode it has enabled.
 *
 * They reflect the activities present in the IntentPlayground app. The IntentPlayground app
 * resides in development/samples/IntentPlayground
 */
public class Activities {

    private static class BaseActivity extends Activity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setTitle(getClass().getSimpleName());
        }
    }

    public static class TrackerActivity extends BaseActivity {
    }

    public static class RegularActivity extends BaseActivity {
        public boolean mIsOnNewIntentCalled = false;

        @Override
        protected void onNewIntent(Intent intent) {
            super.onNewIntent(intent);
            mIsOnNewIntentCalled = true;
        }
    }

    public static class SingleTopActivity extends BaseActivity {
    }

    public static class SingleInstanceActivity extends BaseActivity {
    }

    public static class SingleInstanceActivity2 extends BaseActivity {
    }

    public static class SingleTaskActivity extends BaseActivity {
    }

    public static class SingleTaskActivity2 extends BaseActivity {
    }

    public static class SingleInstancePerTaskActivity extends BaseActivity {
    }

    public static class SingleInstancePerTaskDocumentNeverActivity extends BaseActivity {
    }

    public static class TaskAffinity1Activity extends BaseActivity {
    }

    public static class TaskAffinity1Activity2 extends BaseActivity {
    }

    public static class TaskAffinity1SingleTopActivity extends BaseActivity {
    }

    public static class TaskAffinity2Activity extends BaseActivity {
    }

    public static class TaskAffinity3Activity extends BaseActivity {
    }

    public static class ClearTaskOnLaunchActivity extends BaseActivity {
    }

    public static class DocumentLaunchIntoActivity extends BaseActivity {
    }

    public static class DocumentLaunchAlwaysActivity extends BaseActivity {
    }

    public static class DocumentLaunchNeverActivity extends BaseActivity {
    }

    public static class NoHistoryActivity extends BaseActivity {
    }

    public static class LauncherActivity extends BaseActivity {
    }

    public static class RelinquishTaskIdentityActivity extends BaseActivity {
    }

    public static class TaskAffinity1RelinquishTaskIdentityActivity extends BaseActivity {
    }
}
