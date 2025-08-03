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

package android.server.wm.activity;

import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.server.wm.app.Components.MAX_ASPECT_RATIO_ACTIVITY;
import static android.server.wm.app.Components.MAX_ASPECT_RATIO_RESIZABLE_ACTIVITY;
import static android.server.wm.app.Components.MAX_ASPECT_RATIO_UNSET_ACTIVITY;
import static android.server.wm.app.Components.META_DATA_MAX_ASPECT_RATIO_ACTIVITY;
import static android.server.wm.app.Components.MIN_ASPECT_RATIO_ACTIVITY;
import static android.server.wm.app.Components.MIN_ASPECT_RATIO_LANDSCAPE_ACTIVITY;
import static android.server.wm.app.Components.MIN_ASPECT_RATIO_PORTRAIT_ACTIVITY;
import static android.server.wm.app.Components.MIN_ASPECT_RATIO_UNSET_ACTIVITY;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeThat;

import android.platform.test.annotations.Presubmit;
import android.view.Display;

import org.junit.Test;

/** Build/Install/Run: atest CtsWindowManagerDeviceWindow:AspectRatioTests */
@Presubmit
public class AspectRatioTests extends AspectRatioTestsBase {

    // The max. aspect ratio the test activities are using.
    private static final float MAX_ASPECT_RATIO = 1.0f;

    // The min. aspect ratio the test activities are using.
    private static final float MIN_ASPECT_RATIO = 3.0f;

    @Test
    public void testMaxAspectRatio() {
        // Activity has a maxAspectRatio, assert that the actual ratio is less than that.
        runAspectRatioTest(
                MAX_ASPECT_RATIO_ACTIVITY,
                WINDOWING_MODE_FULLSCREEN,
                (actual, displayId, activitySize, displaySize) -> {
                    assertThat(actual, lessThanOrEqualTo(MAX_ASPECT_RATIO));
                });
    }

    @Test
    public void testMetaDataMaxAspectRatio() {
        // Activity has a maxAspectRatio, assert that the actual ratio is less than that.
        runAspectRatioTest(
                META_DATA_MAX_ASPECT_RATIO_ACTIVITY,
                WINDOWING_MODE_FULLSCREEN,
                (actual, displayId, activitySize, displaySize) -> {
                    assertThat(actual, lessThanOrEqualTo(MAX_ASPECT_RATIO));
                });
    }

    @Test
    public void testMaxAspectRatioResizeableActivity() {
        // Since this activity is resizeable, its max aspect ratio should be ignored.
        runAspectRatioTest(
                MAX_ASPECT_RATIO_RESIZABLE_ACTIVITY,
                WINDOWING_MODE_FULLSCREEN,
                (actual, displayId, activitySize, displaySize) -> {
                    // TODO(b/69982434): Add ability to get native aspect ratio non-default display.
                    assumeThat(displayId, is(Display.DEFAULT_DISPLAY));

                    final float defaultDisplayAspectRatio =
                            getDisplayAspectRatio(MAX_ASPECT_RATIO_RESIZABLE_ACTIVITY);
                    assertThat(actual, greaterThanOrEqualToInexact(defaultDisplayAspectRatio));
                });
    }

    @Test
    public void testMaxAspectRatioUnsetActivity() {
        // Since this activity didn't set an explicit maxAspectRatio, there should be no such
        // ratio enforced.
        runAspectRatioTest(
                MAX_ASPECT_RATIO_UNSET_ACTIVITY,
                WINDOWING_MODE_FULLSCREEN,
                (actual, displayId, activitySize, displaySize) -> {
                    // TODO(b/69982434): Add ability to get native aspect ratio non-default display.
                    assumeThat(displayId, is(Display.DEFAULT_DISPLAY));

                    assertThat(
                            actual,
                            greaterThanOrEqualToInexact(
                                    getDisplayAspectRatio(MAX_ASPECT_RATIO_UNSET_ACTIVITY)));
                });
    }

    @Test
    public void testMinAspectRatio() {
        // Activity has a minAspectRatio, assert the ratio is at least that.
        runAspectRatioTest(
                MIN_ASPECT_RATIO_ACTIVITY,
                WINDOWING_MODE_FULLSCREEN,
                (actual, displayId, activitySize, displaySize) -> {
                    assertThat(actual, greaterThanOrEqualToInexact(MIN_ASPECT_RATIO));
                });
    }

    @Test
    public void testMinAspectRatioUnsetActivity() {
        // Since this activity didn't set an explicit minAspectRatio, there should be no such
        // ratio enforced.
        runAspectRatioTest(
                MIN_ASPECT_RATIO_UNSET_ACTIVITY,
                WINDOWING_MODE_FULLSCREEN,
                (actual, displayId, activitySize, displaySize) -> {
                    // TODO(b/69982434): Add ability to get native aspect ratio non-default display.
                    assumeThat(displayId, is(Display.DEFAULT_DISPLAY));

                    assertThat(
                            actual,
                            lessThanOrEqualToInexact(
                                    getDisplayAspectRatio(MIN_ASPECT_RATIO_UNSET_ACTIVITY)));
                });
    }

    @Test
    public void testMinAspectLandscapeActivity() {
        // Activity has requested a fixed orientation, assert the orientation is that.
        runAspectRatioTest(
                MIN_ASPECT_RATIO_LANDSCAPE_ACTIVITY,
                WINDOWING_MODE_FULLSCREEN,
                (actual, displayId, activitySize, displaySize) -> {
                    assertThat(activitySize.x, greaterThan(activitySize.y));
                    // Since activities must fit within the bounds of the display and they should
                    // respect
                    // the minimal size, there is an aspect ratio limit that an activity cannot
                    // exceed even
                    // if set in the app manifest. In such scenarios, we won't expect the aspect
                    // ratio to
                    // be respected.
                    int maxAspectRatioForDisplay =
                            displaySize.x / getMinimalTaskSize(MIN_ASPECT_RATIO_LANDSCAPE_ACTIVITY);
                    if (MIN_ASPECT_RATIO <= maxAspectRatioForDisplay) {
                        // The display size is large enough to support the desired aspect ratio
                        // without violating the minimal size restriction.
                        assertThat(actual, greaterThanOrEqualToInexact(MIN_ASPECT_RATIO));
                    }
                });
    }

    @Test
    public void testMinAspectPortraitActivity() {
        runAspectRatioTest(
                MIN_ASPECT_RATIO_PORTRAIT_ACTIVITY,
                WINDOWING_MODE_FULLSCREEN,
                (actual, displayId, activitySize, displaySize) -> {
                    assertThat(activitySize.y, greaterThan(activitySize.x));
                    // Since activities must fit within the bounds of the display and they should
                    // respect
                    // the minimal size, there is an aspect ratio limit that an activity cannot
                    // exceed even
                    // if set in the app manifest. In such scenarios, we won't expect the aspect
                    // ratio to
                    // be respected.
                    int maxAspectRatioForDisplay =
                            displaySize.y / getMinimalTaskSize(MIN_ASPECT_RATIO_PORTRAIT_ACTIVITY);
                    if (MIN_ASPECT_RATIO <= maxAspectRatioForDisplay) {
                        // The display size is large enough to support the desired aspect ratio
                        // without violating the minimal size restriction.
                        assertThat(actual, greaterThanOrEqualToInexact(MIN_ASPECT_RATIO));
                    }
                });
    }
}
