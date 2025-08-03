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
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;

public final class SingleUseActivity extends Activity {
    private static final String TAG = SingleUseActivity.class.getSimpleName();

    private static @Nullable SingleUseActivity sInstance;

    public static @Nullable SingleUseActivity getInstance() {
        return sInstance;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (sInstance != null) {
            Log.wtf(TAG, "instance already set onCreate(): " + sInstance);
        }
        sInstance = this;
    }

    @Override
    protected void onDestroy() {
        if (this != sInstance) {
            Log.wtf(TAG, "someone else created an extra copy of the activity");
        }
        sInstance = null;
        super.onDestroy();
    }
}
