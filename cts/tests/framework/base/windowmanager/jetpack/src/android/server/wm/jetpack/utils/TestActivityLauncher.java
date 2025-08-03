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

package android.server.wm.jetpack.utils;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class TestActivityLauncher<T extends Activity> {
    /** Key for string extra, ID to track an Activity that is launched. */
    public static final String KEY_ACTIVITY_ID = "ActivityID";

    /**
     * Options that will be passed to the instrumentation.
     * @see TestActivityLauncher#launch(Instrumentation)
     */
    private final ActivityOptions mOptions = ActivityOptions.makeBasic();

    /**
     * The class for the {@link Activity} that you are launching.
     */
    private final Class<T> mActivityClass;

    /**
     * The intent that will be used to launch the {@link Activity}.
     */
    private final Intent mIntent;

    public TestActivityLauncher(@NonNull Context context, @NonNull Class<T> activityClass) {
        mActivityClass = activityClass;
        mIntent = new Intent(context, activityClass);
    }

    public TestActivityLauncher<T> addIntentFlag(int flag) {
        mIntent.addFlags(flag);
        return this;
    }

    public TestActivityLauncher<T> setActivityId(@Nullable String id) {
        mIntent.putExtra(KEY_ACTIVITY_ID, id);
        return this;
    }

    public TestActivityLauncher<T> setWindowingMode(int windowingMode) {
        mOptions.setLaunchWindowingMode(windowingMode);
        return this;
    }

    public TestActivityLauncher<T> setLaunchDisplayId(int displayId) {
        mOptions.setLaunchDisplayId(displayId);
        return this;
    }

    public Intent getIntent() {
        return mIntent;
    }

    public T launch(@NonNull Instrumentation instrumentation) {
        return mActivityClass.cast(instrumentation.startActivitySync(mIntent, mOptions.toBundle()));
    }
}
