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
package android.app.cts.shortfgstesthelper;

import static android.app.cts.shortfgstesthelper.ShortFgsHelper.TAG;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

public class MyActivity extends Activity {
    private static volatile MyActivity sInstance;

    public static MyActivity getInstance() {
        return sInstance;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate: " + this.getClass().getName());
        super.onCreate(savedInstanceState);

        sInstance = this;

        setShowWhenLocked(true);
        setTurnScreenOn(true);

        ShortFgsHelper.sendBackMethodName(this.getClass(), "onCreate");
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy: " + this.getClass().getName());
        super.onDestroy();

        sInstance = null;
        ShortFgsHelper.sendBackMethodName(this.getClass(), "onDestroy");
    }

    @Override
    protected void onResume() {
        Log.i(TAG, "onResume: " + this.getClass().getName());
        super.onResume();

        // onResume/onPause are rather unpredictable if the screen is off, so let's not use it.
        // ShortFgsHelper.sendBackMethodName(this.getClass(), "onResume");
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "onPause: " + this.getClass().getName());
        super.onPause();

        // onResume/onPause are rather unpredictable if the screen is off, so let's not use it.
        // ShortFgsHelper.sendBackMethodName(this.getClass(), "onPause");
    }
}
