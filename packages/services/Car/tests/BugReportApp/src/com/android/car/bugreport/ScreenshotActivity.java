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

package com.android.car.bugreport;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.google.common.base.Preconditions;

/**
 * Activity that starts ScreenshotService to take screenshots.
 *
 * <p>This is a simple activity which does not have UI and starts ScreenshotService service only to
 * take screenshots via a system bar button.
 */
public class ScreenshotActivity extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Preconditions.checkState(Config.isBugReportEnabled(), "BugReport is disabled.");

        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onStart() {
        super.onStart();

        startScreenshotService();
    }

    private void startScreenshotService() {
        Intent intent = new Intent(this, ScreenshotService.class);
        startForegroundService(intent);
        setResult(Activity.RESULT_OK);
        finish();
    }
}
