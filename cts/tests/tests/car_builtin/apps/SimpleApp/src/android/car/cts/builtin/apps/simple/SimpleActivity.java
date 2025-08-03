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

package android.car.cts.builtin.apps.simple;

import android.app.Activity;
import android.content.Intent;

/**
 * A very simple activity for testing PackageManagerHelper.
 */
public final class SimpleActivity extends Activity {
    private static final String ACTIVITY_LAUNCHED_ACTION =
            "android.car.cts.builtin.apps.simple.SimpleActivity.LAUNCHED_ACTION";

    @Override
    public void onStart() {
        super.onStart();
        Intent reply = new Intent(ACTIVITY_LAUNCHED_ACTION);
        sendBroadcast(reply);
    }
}
