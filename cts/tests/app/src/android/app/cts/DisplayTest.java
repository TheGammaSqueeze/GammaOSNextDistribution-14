/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package android.app.cts;

import static com.android.compatibility.common.util.PackageUtil.supportsRotation;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.ActivityOptions;
import android.app.stubs.DisplayTestActivity;
import android.app.stubs.OrientationTestUtils;
import android.content.Intent;
import android.graphics.Point;
import android.server.wm.SetRequestedOrientationRule;
import android.util.Pair;
import android.view.Display;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Rule;
import org.junit.Test;

/**
 * Tests to verify functionality of {@link Display}.
 */
public class DisplayTest {
    @Rule public SetRequestedOrientationRule mSetRequestedOrientationRule =
            new SetRequestedOrientationRule();

    /**
     * Tests that the underlying {@link android.view.DisplayAdjustments} in {@link Display} updates.
     * The method {@link DisplayTestActivity#getDisplay()} fetches the Display directly from the
     * {@link android.view.WindowManager}. A display fetched before the rotation should have the
     * updated adjustments after a rotation.
     */
    @Test
    @ApiTest(apis = {"android.content.Context#getDisplay"})
    public void testRotation() throws Throwable {
        if (!supportsRotation()) {
            // Skip rotation test if device doesn't support it.
            return;
        }

        final Pair<Intent, ActivityOptions> launchArgs =
                SetRequestedOrientationRule.buildFullScreenLaunchArgs(DisplayTestActivity.class);

        final DisplayTestActivity activity = (DisplayTestActivity) InstrumentationRegistry
                .getInstrumentation()
                .startActivitySync(launchArgs.first, launchArgs.second.toBundle());
        try {
            // Get a {@link Display} instance before rotation.
            final Display origDisplay = activity.getDisplay();

            // Capture the originally reported width and heights
            final Point origSize = new Point();
            origDisplay.getRealSize(origSize);

            // Change orientation
            activity.configurationChangeObserver.startObserving();
            OrientationTestUtils.switchOrientation(activity);

            final boolean closeToSquareBounds =
                    OrientationTestUtils.isCloseToSquareBounds(activity);

            // Don't wait for the configuration to change if
            // the display is square. In many cases it won't.
            if (!closeToSquareBounds) {
                activity.configurationChangeObserver.await();
            }

            final Point newOrigSize = new Point();
            origDisplay.getRealSize(newOrigSize);

            // Get a {@link Display} instance after rotation.
            final Display updatedDisplay = activity.getDisplay();
            final Point updatedSize = new Point();
            updatedDisplay.getRealSize(updatedSize);

            // For square screens the following assertions do not make sense and will always
            // fail.
            if (!closeToSquareBounds) {
                // Ensure that the width and height of the original instance no longer are the
                // same.
                // Note that this will be false if the device width and height are identical.
                // Note there are cases where width and height may not all be updated, such as
                // on docked devices where the app is letterboxed. However, at least one
                // dimension needs to be updated.
                assertWithMessage(
                        "size from original display instance should have changed")
                        .that(origSize).isNotEqualTo(newOrigSize);
            }

            // Ensure that the width and height of the original instance have been updated to
            // match the values that would be found in a new instance.
            assertWithMessage(
                    "size from original display instance should match current")
                    .that(newOrigSize).isEqualTo(updatedSize);
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(activity::finish);
        }
    }
}
