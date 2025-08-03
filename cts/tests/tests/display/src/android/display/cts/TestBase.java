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

package android.display.cts;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;

import androidx.test.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import org.junit.After;

public class TestBase {

    private Activity mScreenOnActivity;

    @After
    public void tearDownBase() {
        if (mScreenOnActivity != null) {
            mScreenOnActivity.finish();
        }
    }

    protected void launchScreenOnActivity() {
        Class<ScreenOnActivity> clazz = ScreenOnActivity.class;
        String targetPackage =
                InstrumentationRegistry.getInstrumentation().getContext().getPackageName();
        Instrumentation.ActivityResult result =
                new Instrumentation.ActivityResult(0, new Intent());
        Instrumentation.ActivityMonitor monitor =
                new Instrumentation.ActivityMonitor(clazz.getName(), result, false);
        InstrumentationRegistry.getInstrumentation().addMonitor(monitor);
        launchActivity(targetPackage, clazz, null);
        mScreenOnActivity = monitor.waitForActivity();
    }

    protected <T extends Activity> T launchActivity(ActivityTestRule<T> activityRule) {
        final T activity = activityRule.launchActivity(null);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        return activity;
    }

    /**
     * Utility method for launching an activity. Copied from InstrumentationTestCase since
     * InstrumentationRegistry does not provide these APIs anymore.
     *
     * <p>The {@link Intent} used to launch the Activity is:
     *  action = {@link Intent#ACTION_MAIN}
     *  extras = null, unless a custom bundle is provided here
     * All other fields are null or empty.
     *
     * <p><b>NOTE:</b> The parameter <i>pkg</i> must refer to the package identifier of the
     * package hosting the activity to be launched, which is specified in the AndroidManifest.xml
     * file.  This is not necessarily the same as the java package name.
     *
     * @param pkg The package hosting the activity to be launched.
     * @param activityCls The activity class to launch.
     * @param extras Optional extra stuff to pass to the activity.
     * @return The activity, or null if non launched.
     */
    private <T extends Activity> T launchActivity(
            String pkg,
            Class<T> activityCls,
            Bundle extras) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        if (extras != null) {
            intent.putExtras(extras);
        }
        return launchActivityWithIntent(pkg, activityCls, intent);
    }

    /**
     * Utility method for launching an activity with a specific Intent.
     *
     * <p><b>NOTE:</b> The parameter <i>pkg</i> must refer to the package identifier of the
     * package hosting the activity to be launched, which is specified in the AndroidManifest.xml
     * file.  This is not necessarily the same as the java package name.
     *
     * @param pkg The package hosting the activity to be launched.
     * @param activityCls The activity class to launch.
     * @param intent The intent to launch with
     * @return The activity, or null if non launched.
     */
    @SuppressWarnings("unchecked")
    private <T extends Activity> T launchActivityWithIntent(
            String pkg,
            Class<T> activityCls,
            Intent intent) {
        intent.setClassName(pkg, activityCls.getName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        T activity = (T) InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        return activity;
    }
}
