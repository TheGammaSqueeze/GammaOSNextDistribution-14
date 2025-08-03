/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.sensitivecontentprotection.cts;

import static android.view.flags.Flags.FLAG_SENSITIVE_CONTENT_APP_PROTECTION;

import static com.google.common.truth.Truth.assertThat;

import android.app.UiAutomation;
import android.graphics.Color;
import android.media.projection.MediaProjection;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.cts.surfacevalidator.BitmapPixelChecker;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ViewSensitiveContentTest {
    private final SensitiveContentMediaProjectionHelper mMediaProjectionHelper =
            new SensitiveContentMediaProjectionHelper();
    @Rule
    public TestName mName = new TestName();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
        DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    @RequiresFlagsEnabled(FLAG_SENSITIVE_CONTENT_APP_PROTECTION)
    public void testScreenCaptureIsBlocked() {
        UiAutomation uiAutomation = androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity();

        mMediaProjectionHelper.authorizeMediaProjection();
        MediaProjection mediaProjection = mMediaProjectionHelper.startMediaProjection();
        assertThat(mediaProjection).isNotNull();

        // SimpleActivity has a sensitive view, so screen capture should be blocked.
        try (ActivityScenario<SimpleActivity> activityScenario =
                ActivityScenario.launch(SimpleActivity.class)) {
            activityScenario.onActivity(activity -> {
                BitmapPixelChecker pixelChecker = new BitmapPixelChecker(Color.BLACK);
                BitmapPixelChecker.validateScreenshot(mName, activity, pixelChecker,
                        -1 /* expectedMatchingPixels */,
                        BitmapPixelChecker.getInsets(activity));
            });
        }
    }
}
