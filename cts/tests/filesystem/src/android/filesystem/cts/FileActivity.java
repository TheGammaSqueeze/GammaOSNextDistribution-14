/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.filesystem.cts;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import androidx.test.InstrumentationRegistry;

/**
 * A simple activity to stay foreground context.
 */
public class FileActivity extends Activity {
    private static final String PACKAGE_NAME = "android.filesystem.cts";

    public static void startFileActivity(Context context) {
        final Intent intent = new Intent();
        intent.setComponent(new ComponentName(PACKAGE_NAME, FileActivity.class.getName()));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
