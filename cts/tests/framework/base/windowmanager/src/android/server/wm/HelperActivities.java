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

package android.server.wm;

import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;

import android.app.Activity;
import android.os.Bundle;
import android.server.wm.app.AbstractLifecycleLogActivity;

public class HelperActivities {
    public static class ResizeablePortraitActivity extends AbstractLifecycleLogActivity {}

    public static class ResponsiveActivity extends AbstractLifecycleLogActivity {}

    public static class NonResizeablePortraitActivity extends AbstractLifecycleLogActivity {}

    public static class NonResizeableLandscapeActivity extends AbstractLifecycleLogActivity {}

    public static class NonResizeableNonFixedOrientationActivity
            extends AbstractLifecycleLogActivity {}

    public static class NonResizeableAspectRatioActivity extends AbstractLifecycleLogActivity {}

    public static class NonResizeableLargeAspectRatioActivity
            extends AbstractLifecycleLogActivity {}

    public static class SupportsSizeChangesPortraitActivity extends AbstractLifecycleLogActivity {}

    public static class ResizeableLeftActivity extends AbstractLifecycleLogActivity {}

    public static class ResizeableRightActivity extends AbstractLifecycleLogActivity {}

    public static class StandardActivity extends Activity {}

    // Test activity
    public static class SecondStandardActivity extends Activity {}

    public static class NoPropertyChangeOrientationWhileRelaunchingActivity
            extends AbstractLifecycleLogActivity {

        private static boolean sHasChangeOrientationInOnResume;

        @Override
        protected void onCreate(Bundle instance) {
            super.onCreate(instance);
            // When OVERRIDE_ENABLE_COMPAT_IGNORE_REQUESTED_ORIENTATION is enabled this request
            // should be ignored if sHasChangeOrientationInOnResume is true.
            setRequestedOrientation(SCREEN_ORIENTATION_LANDSCAPE);
        }

        @Override
        protected void onResume() {
            super.onResume();
            if (!sHasChangeOrientationInOnResume) {
                setRequestedOrientation(SCREEN_ORIENTATION_PORTRAIT);
                sHasChangeOrientationInOnResume = true;
            }
        }
    }
}
