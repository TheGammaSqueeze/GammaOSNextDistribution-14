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

package android.car.cts.builtin.activity;

import android.app.Activity;
import android.util.Log;

public abstract class ActivityManagerTestActivityBase extends Activity {
    public static final String TAG = ActivityManagerTestActivityBase.class.getSimpleName();

    private volatile boolean mIsVisible;
    private volatile boolean mHasFocus;

    public boolean isVisible() {
        return mIsVisible;
    }

    public boolean hasFocus() {
        return mHasFocus;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        mHasFocus = hasFocus;
        Log.d(TAG, "hasFocus: " + hasFocus);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mIsVisible = true;
    }

    @Override
    protected void onStop() {
        super.onStop();
        mIsVisible = false;
    }
}
