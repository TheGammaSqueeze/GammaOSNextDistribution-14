/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.server.wm.insets;

import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static android.view.RoundedCorner.POSITION_BOTTOM_LEFT;
import static android.view.RoundedCorner.POSITION_BOTTOM_RIGHT;
import static android.view.RoundedCorner.POSITION_TOP_LEFT;
import static android.view.RoundedCorner.POSITION_TOP_RIGHT;
import static android.view.Surface.ROTATION_0;
import static android.view.Surface.ROTATION_180;
import static android.view.Surface.ROTATION_270;
import static android.view.Surface.ROTATION_90;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.platform.test.annotations.Presubmit;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.RotationSession;
import android.server.wm.WindowManagerStateHelper;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.RoundedCorner;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;

import androidx.annotation.NonNull;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@Presubmit
@RunWith(Parameterized.class)
public class RoundedCornerTests extends ActivityManagerTestBase {
    private static final String TAG = "RoundedCornerTests";
    private static final int POSITION_LENGTH = 4;
    private static final long TIMEOUT_IN_MILLISECONDS = 1000;

    @Parameterized.Parameters(name= "{1}({0})")
    public static Object[][] data() {
        return new Object[][]{
                {SCREEN_ORIENTATION_PORTRAIT, "SCREEN_ORIENTATION_PORTRAIT"},
                {SCREEN_ORIENTATION_LANDSCAPE, "SCREEN_ORIENTATION_LANDSCAPE"},
                {SCREEN_ORIENTATION_REVERSE_LANDSCAPE, "SCREEN_ORIENTATION_REVERSE_LANDSCAPE"},
                {SCREEN_ORIENTATION_REVERSE_PORTRAIT, "SCREEN_ORIENTATION_REVERSE_PORTRAIT"},
        };
    }

    @Parameterized.Parameter(0)
    public int orientation;

    @Parameterized.Parameter(1)
    public String orientationName;

    private final WindowManagerStateHelper mWindowManagerStateHelper =
            new WindowManagerStateHelper();

    @After
    public void tearDown() {
        mTestActivityRule.finishActivity();
        mWindowManagerStateHelper.waitForDisplayUnfrozen();
    }

    @Rule
    public final ActivityTestRule<TestActivity> mTestActivityRule =
            new ActivityTestRule<>(TestActivity.class, false /* initialTouchMode */,
                    false /* launchActivity */);

    @Test
    public void testRoundedCorner_fullscreen() {
        verifyRoundedCorners(false /* excludeRoundedCorners */);
    }

    @Test
    public void testRoundedCorner_excludeRoundedCorners() {
        verifyRoundedCorners(true /* excludeRoundedCorners */);
    }

    private void verifyRoundedCorners(boolean excludedRoundedCorners) {
        final TestActivity activity = mTestActivityRule.launchActivity(new Intent());

        if (excludedRoundedCorners && !activity.hasRoundedCorners()) {
            Log.d(TAG, "There is no rounded corner on the display. Skipped!!");
            return;
        }

        waitAndAssertResumedActivity(activity.getComponentName(), "Activity must be resumed.");

        int rotation = getRotation(activity, orientation);

        if (rotation != ROTATION_0) {
            // If the device doesn't support rotation, just verify the rounded corner with
            // the current orientation.
            if (!supportsRotation()) {
                return;
            }
            RotationSession rotationSession = createManagedRotationSession();
            rotationSession.set(rotation);

            mInstrumentation.getUiAutomation().syncInputTransactions();
        }

        runOnMainSync(() -> activity.addChildWindow(
                activity.calculateWindowBounds(excludedRoundedCorners)));
        try {
            // Make sure the child window has been laid out.
            PollingCheck.waitFor(TIMEOUT_IN_MILLISECONDS,
                    () -> activity.getDispatchedInsets() != null);
            final WindowInsets insets = activity.getDispatchedInsets();

            if (excludedRoundedCorners) {
                for (int i = 0; i < POSITION_LENGTH; i++) {
                    assertNull("The rounded corners should be null.",
                            insets.getRoundedCorner(i));
                }
            } else {
                final Display display = activity.getDisplay();
                for (int j = 0; j < POSITION_LENGTH; j++) {
                    assertEquals(insets.getRoundedCorner(j), display.getRoundedCorner(j));
                }
            }
        } finally {
            runOnMainSync(activity::removeChildWindow);
        }
    }

    /**
     * Returns the rotation based on {@code orientations}.
     */
    private static int getRotation(@NonNull Activity activity, int requestedOrientation) {
        // Not use Activity#getRequestedOrientation because the possible values are dozens and hard
        // to determine the rotation.
        int currentOrientation = activity.getResources().getConfiguration().orientation;
        if (currentOrientation == ORIENTATION_PORTRAIT) {
            switch (requestedOrientation) {
                case SCREEN_ORIENTATION_PORTRAIT: {
                    return ROTATION_0;
                }
                case SCREEN_ORIENTATION_LANDSCAPE: {
                    return ROTATION_90;
                }
                case SCREEN_ORIENTATION_REVERSE_PORTRAIT: {
                    return ROTATION_180;
                }
                case SCREEN_ORIENTATION_REVERSE_LANDSCAPE: {
                    return ROTATION_270;
                }
            }
        } else {
            switch (requestedOrientation) {
                case SCREEN_ORIENTATION_PORTRAIT: {
                    return ROTATION_90;
                }
                case SCREEN_ORIENTATION_LANDSCAPE: {
                    return ROTATION_0;
                }
                case SCREEN_ORIENTATION_REVERSE_PORTRAIT: {
                    return ROTATION_270;
                }
                case SCREEN_ORIENTATION_REVERSE_LANDSCAPE: {
                    return ROTATION_180;
                }
            }
        }
        throw new IllegalArgumentException("Unknown orientation value:" + requestedOrientation);
    }

    private void runOnMainSync(Runnable runnable) {
        getInstrumentation().runOnMainSync(runnable);
    }

    public static class TestActivity extends Activity {
        static final String EXTRA_ORIENTATION = "extra.orientation";

        private View mChildWindowRoot;
        private WindowInsets mDispatchedInsets;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getWindow().requestFeature(Window.FEATURE_NO_TITLE);
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            getWindow().getDecorView().getWindowInsetsController().hide(
                    WindowInsets.Type.systemBars());
            if (getIntent() != null) {
                setRequestedOrientation(getIntent().getIntExtra(
                        EXTRA_ORIENTATION, SCREEN_ORIENTATION_UNSPECIFIED));
            }
        }

        void addChildWindow(Rect bounds) {
            final WindowManager.LayoutParams attrs = new WindowManager.LayoutParams();
            attrs.x = bounds.left;
            attrs.y = bounds.top;
            attrs.width = bounds.width();
            attrs.height = bounds.height();
            attrs.gravity = Gravity.LEFT | Gravity.TOP;
            attrs.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            attrs.flags = FLAG_NOT_FOCUSABLE;
            attrs.setFitInsetsTypes(0);
            mChildWindowRoot = new View(this);
            mChildWindowRoot.setOnApplyWindowInsetsListener(
                    (v, insets) -> mDispatchedInsets = insets);
            getWindowManager().addView(mChildWindowRoot, attrs);
        }

        void removeChildWindow() {
            if (mChildWindowRoot != null) {
                getWindowManager().removeViewImmediate(mChildWindowRoot);
            }
        }

        WindowInsets getDispatchedInsets() {
            return mDispatchedInsets;
        }

        boolean hasRoundedCorners() {
            final Display display = getDisplay();
            return display.getRoundedCorner(POSITION_TOP_LEFT) != null
                    || display.getRoundedCorner(POSITION_TOP_RIGHT) != null
                    || display.getRoundedCorner(POSITION_BOTTOM_RIGHT) != null
                    || display.getRoundedCorner(POSITION_BOTTOM_LEFT) != null;
        }

        Rect calculateWindowBounds(boolean excludeRoundedCorners) {
            final Display display = getDisplay();
            final WindowMetrics windowMetrics = getWindowManager().getCurrentWindowMetrics();
            if (!excludeRoundedCorners) {
                return windowMetrics.getBounds();
            }
            final Rect bounds = new Rect();
            final int width = windowMetrics.getBounds().width();
            final int height = windowMetrics.getBounds().height();
            final RoundedCorner topLeft = display.getRoundedCorner(POSITION_TOP_LEFT);
            final RoundedCorner topRight = display.getRoundedCorner(POSITION_TOP_RIGHT);
            final RoundedCorner bottomRight = display.getRoundedCorner(POSITION_BOTTOM_RIGHT);
            final RoundedCorner bottomLeft = display.getRoundedCorner(POSITION_BOTTOM_LEFT);

            bounds.left = Math.max(topLeft != null ? topLeft.getCenter().x : 0,
                    bottomLeft != null ? bottomLeft.getCenter().x : 0);
            bounds.top = Math.max(topLeft != null ? topLeft.getCenter().y : 0,
                    bottomLeft != null ? bottomLeft.getCenter().y : 0);
            bounds.right = Math.min(topRight != null ? topRight.getCenter().x : width,
                    bottomRight != null ? bottomRight.getCenter().x : width);
            bounds.bottom = Math.min(bottomRight != null ? bottomRight.getCenter().y : height,
                    bottomLeft != null ? bottomLeft.getCenter().y : height);

            Log.d(TAG, "Window bounds with rounded corners excluded = " + bounds);
            return bounds;
        }
    }
}
