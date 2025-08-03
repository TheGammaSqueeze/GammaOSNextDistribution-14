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

package android.server.wm.other;

import static android.server.wm.ComponentNameUtils.getWindowName;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowInsets.Type.systemBars;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;

import android.app.Activity;
import android.content.ComponentName;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.platform.test.annotations.Presubmit;
import android.provider.Settings;
import android.server.wm.DumpOnFailure;
import android.server.wm.WindowManagerState;
import android.server.wm.WindowManagerTestBase;
import android.server.wm.cts.R;
import android.server.wm.settings.SettingsSession;
import android.view.WindowInsets;
import android.view.WindowManager;

import androidx.test.rule.ActivityTestRule;
import androidx.test.uiautomator.UiDevice;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.ColorUtils;
import com.android.compatibility.common.util.PollingCheck;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.mockito.Mockito;

import java.util.function.Consumer;

@Presubmit
public class BlurTests extends WindowManagerTestBase {
    private static final int BACKGROUND_BLUR_PX = 80;
    private static final int BLUR_BEHIND_PX = 40;
    private static final int NO_BLUR_BACKGROUND_COLOR = 0xFF550055;
    private static final int BROADCAST_WAIT_TIMEOUT = 300;

    private Rect mBackgroundActivityBounds;

    private final DumpOnFailure mDumpOnFailure = new DumpOnFailure();

    private final TestRule mEnableBlurRule = SettingsSession.overrideForTest(
            Settings.Global.getUriFor(Settings.Global.DISABLE_WINDOW_BLURS),
            Settings.Global::getInt,
            Settings.Global::putInt,
            0);
    private final TestRule mDisableTransitionAnimationRule = SettingsSession.overrideForTest(
            Settings.Global.getUriFor(Settings.Global.TRANSITION_ANIMATION_SCALE),
            Settings.Global::getFloat,
            Settings.Global::putFloat,
            0f);

    private final ActivityTestRule<BackgroundActivity> mBackgroundActivity =
            new ActivityTestRule<>(BackgroundActivity.class);

    @Rule
    public final TestRule methodRules = RuleChain.outerRule(mDumpOnFailure)
            .around(mEnableBlurRule)
            .around(mDisableTransitionAnimationRule)
            .around(mBackgroundActivity);

    @Before
    public void setUp() {
        assumeTrue(supportsBlur());
        ComponentName cn = mBackgroundActivity.getActivity().getComponentName();
        waitAndAssertResumedActivity(cn, cn + " must be resumed");
        mBackgroundActivity.getActivity().waitAndAssertWindowFocusState(true);

        // Use the background activity's bounds when taking the device screenshot.
        // This is needed for multi-screen devices (foldables) where
        // the launched activity covers just one screen
        WindowManagerState.WindowState windowState = mWmState.getWindowState(cn);
        WindowManagerState.Activity act = mWmState.getActivity(cn);
        mBackgroundActivityBounds = act.getBounds();
        insetGivenFrame(windowState,
                insetsSource -> (insetsSource.is(WindowInsets.Type.captionBar())),
                mBackgroundActivityBounds);

        // Wait for the first frame *after* the splash screen is removed to take screenshots.
        // We don't currently have a definite event / callback for this.
        mWmState.waitForAppTransitionIdleOnDisplay(DEFAULT_DISPLAY);
        waitForActivityIdle(mBackgroundActivity.getActivity());

        // Basic checks common to all tests
        verifyOnlyBackgroundImageVisible();
        assertTrue(mContext.getSystemService(WindowManager.class).isCrossWindowBlurEnabled());
    }

    @Test
    @ApiTest(apis = {"android.view.Window#setBackgroundBlurRadius(int)"})
    public void testBackgroundBlurSimple() {
        final BlurActivity blurActivity = startTestActivity(BlurActivity.class);
        getInstrumentation().runOnMainSync(() -> {
            blurActivity.setBackgroundBlurRadius(BACKGROUND_BLUR_PX);
        });

        waitForActivityIdle(blurActivity);

        final Rect windowFrame = getFloatingWindowFrame(blurActivity);
        assertBackgroundBlur(takeScreenshotForBounds(mBackgroundActivityBounds), windowFrame);
    }

    @Test
    @ApiTest(apis = {"android.view.WindowManager.LayoutParams#setBlurBehindRadius",
                     "android.R.styleable#Window_windowBlurBehindEnabled"})
    public void testBlurBehindSimple() throws Exception {
        final BlurActivity blurActivity = startTestActivity(BlurActivity.class);
        getInstrumentation().runOnMainSync(() -> {
            blurActivity.setBlurBehindRadius(BLUR_BEHIND_PX);
        });
        waitForActivityIdle(blurActivity);
        final Rect windowFrame = getFloatingWindowFrame(blurActivity);

        Bitmap screenshot = takeScreenshotForBounds(mBackgroundActivityBounds);
        assertBlurBehind(screenshot, windowFrame);
        assertNoBackgroundBlur(screenshot, windowFrame);

        getInstrumentation().runOnMainSync(() -> {
            blurActivity.setBlurBehindRadius(0);
        });
        waitForActivityIdle(blurActivity);

        screenshot = takeScreenshotForBounds(mBackgroundActivityBounds);
        assertNoBlurBehind(screenshot, windowFrame);
        assertNoBackgroundBlur(screenshot, windowFrame);
    }

    @Test
    @ApiTest(apis = {"android.view.Window#setBackgroundBlurRadius"})
    public void testNoBackgroundBlurWhenBlurDisabled() {
        setAndAssertForceBlurDisabled(true);
        final BlurActivity blurActivity = startTestActivity(BlurActivity.class);
        getInstrumentation().runOnMainSync(() -> {
            blurActivity.setBackgroundBlurRadius(BACKGROUND_BLUR_PX);
            blurActivity.setBackgroundColor(Color.TRANSPARENT);
        });
        waitForActivityIdle(blurActivity);

        verifyOnlyBackgroundImageVisible();

        setAndAssertForceBlurDisabled(false, blurActivity.mBlurEnabledListener);
        waitForActivityIdle(blurActivity);

        final Rect windowFrame = getFloatingWindowFrame(blurActivity);
        assertBackgroundBlur(takeScreenshotForBounds(mBackgroundActivityBounds), windowFrame);
    }

    @Test
    @ApiTest(apis = {"android.view.Window#setBackgroundBlurRadius"})
    public void testNoBackgroundBlurForNonTranslucentWindow() {
        final BlurActivity blurActivity = startTestActivity(BadBlurActivity.class);
        getInstrumentation().runOnMainSync(() -> {
            blurActivity.setBackgroundBlurRadius(BACKGROUND_BLUR_PX);
            blurActivity.setBackgroundColor(Color.TRANSPARENT);
        });
        waitForActivityIdle(blurActivity);

        verifyOnlyBackgroundImageVisible();
    }

    @Test
    @ApiTest(apis = {"android.view.WindowManager.LayoutParams#setBlurBehindRadius",
                     "android.R.styleable#Window_windowBlurBehindEnabled"})
    public void testNoBlurBehindWhenBlurDisabled() {
        setAndAssertForceBlurDisabled(true);
        final BlurActivity blurActivity = startTestActivity(BlurActivity.class);
        getInstrumentation().runOnMainSync(() -> {
            blurActivity.setBlurBehindRadius(BLUR_BEHIND_PX);
            blurActivity.setBackgroundColor(Color.TRANSPARENT);
        });
        waitForActivityIdle(blurActivity);

        verifyOnlyBackgroundImageVisible();

        setAndAssertForceBlurDisabled(false, blurActivity.mBlurEnabledListener);
        waitForActivityIdle(blurActivity);

        final Rect windowFrame = getFloatingWindowFrame(blurActivity);
        final Bitmap screenshot = takeScreenshotForBounds(mBackgroundActivityBounds);
        assertBlurBehind(screenshot, windowFrame);
        assertNoBackgroundBlur(screenshot, windowFrame);
    }

    @Test
    @ApiTest(apis = {"android.view.WindowManager.LayoutParams#setBlurBehindRadius",
                     "android.R.styleable#Window_windowBlurBehindEnabled"})
    public void testNoBlurBehindWhenFlagNotSet() {
        final BlurActivity blurActivity = startTestActivity(BadBlurActivity.class);
        getInstrumentation().runOnMainSync(() -> {
            blurActivity.setBlurBehindRadius(BLUR_BEHIND_PX);
            blurActivity.setBackgroundColor(Color.TRANSPARENT);
        });
        waitForActivityIdle(blurActivity);

        verifyOnlyBackgroundImageVisible();
    }

    @Test
    @ApiTest(apis = {"android.view.Window#setBackgroundBlurRadius"})
    public void testBackgroundBlurActivatesFallbackDynamically() {
        final BlurActivity blurActivity = startTestActivity(BlurActivity.class);
        getInstrumentation().runOnMainSync(() -> {
            blurActivity.setBackgroundBlurRadius(BACKGROUND_BLUR_PX);
        });
        waitForActivityIdle(blurActivity);
        final Rect windowFrame = getFloatingWindowFrame(blurActivity);

        Bitmap screenshot = takeScreenshotForBounds(mBackgroundActivityBounds);
        assertBackgroundBlur(screenshot, windowFrame);
        assertNoBlurBehind(screenshot, windowFrame);

        setAndAssertForceBlurDisabled(true, blurActivity.mBlurEnabledListener);
        waitForActivityIdle(blurActivity);

        screenshot = takeScreenshotForBounds(mBackgroundActivityBounds);
        assertNoBackgroundBlur(screenshot, windowFrame);
        assertNoBlurBehind(screenshot, windowFrame);

        setAndAssertForceBlurDisabled(false, blurActivity.mBlurEnabledListener);
        waitForActivityIdle(blurActivity);

        screenshot = takeScreenshotForBounds(mBackgroundActivityBounds);
        assertBackgroundBlur(screenshot, windowFrame);
        assertNoBlurBehind(screenshot, windowFrame);
    }

    @Test
    @ApiTest(apis = {"android.view.WindowManager.LayoutParams#setBlurBehindRadius",
                     "android.R.styleable#Window_windowBlurBehindEnabled"})
    public void testBlurBehindDisabledDynamically() {
        final BlurActivity blurActivity = startTestActivity(BlurActivity.class);
        getInstrumentation().runOnMainSync(() -> {
            blurActivity.setBlurBehindRadius(BLUR_BEHIND_PX);
        });
        waitForActivityIdle(blurActivity);
        final Rect windowFrame = getFloatingWindowFrame(blurActivity);

        Bitmap screenshot = takeScreenshotForBounds(mBackgroundActivityBounds);
        assertBlurBehind(screenshot, windowFrame);
        assertNoBackgroundBlur(screenshot, windowFrame);

        getInstrumentation().runOnMainSync(() -> {
            blurActivity.setBlurBehindRadius(0);
        });
        waitForActivityIdle(blurActivity);

        screenshot = takeScreenshotForBounds(mBackgroundActivityBounds);
        assertNoBackgroundBlur(screenshot, windowFrame);
        assertNoBlurBehind(screenshot, windowFrame);

        getInstrumentation().runOnMainSync(() -> {
            blurActivity.setBlurBehindRadius(BLUR_BEHIND_PX);
        });
        waitForActivityIdle(blurActivity);

        screenshot = takeScreenshotForBounds(mBackgroundActivityBounds);
        assertBlurBehind(screenshot,  windowFrame);
        assertNoBackgroundBlur(screenshot, windowFrame);
    }

    @Test
    @ApiTest(apis = {"android.view.WindowManager.LayoutParams#setBlurBehindRadius",
                     "android.R.styleable#Window_windowBlurBehindEnabled",
                     "android.view.Window#setBackgroundBlurRadius"})
    public void testBlurBehindAndBackgroundBlur() {
        final BlurActivity blurActivity = startTestActivity(BlurActivity.class);
        getInstrumentation().runOnMainSync(() -> {
            blurActivity.setBlurBehindRadius(BLUR_BEHIND_PX);
            blurActivity.setBackgroundBlurRadius(BACKGROUND_BLUR_PX);
        });
        waitForActivityIdle(blurActivity);
        final Rect windowFrame = getFloatingWindowFrame(blurActivity);

        Bitmap screenshot = takeScreenshotForBounds(mBackgroundActivityBounds);
        assertBlurBehind(screenshot, windowFrame);
        assertBackgroundBlurOverBlurBehind(screenshot, windowFrame);

        getInstrumentation().runOnMainSync(() -> {
            blurActivity.setBlurBehindRadius(0);
            blurActivity.setBackgroundBlurRadius(0);
        });
        waitForActivityIdle(blurActivity);

        screenshot = takeScreenshotForBounds(mBackgroundActivityBounds);
        assertNoBackgroundBlur(screenshot, windowFrame);
        assertNoBlurBehind(screenshot, windowFrame);

        getInstrumentation().runOnMainSync(() -> {
            blurActivity.setBlurBehindRadius(BLUR_BEHIND_PX);
            blurActivity.setBackgroundBlurRadius(BACKGROUND_BLUR_PX);
        });
        waitForActivityIdle(blurActivity);

        screenshot = takeScreenshotForBounds(mBackgroundActivityBounds);
        assertBlurBehind(screenshot, windowFrame);
        assertBackgroundBlurOverBlurBehind(screenshot, windowFrame);
    }

    @Test
    @ApiTest(apis = {"android.R.styleable#Window_windowBackgroundBlurRadius",
                     "android.R.styleable#Window_windowBlurBehindRadius",
                     "android.R.styleable#Window_windowBlurBehindEnabled"})
    public void testBlurBehindAndBackgroundBlurSetWithAttributes() {
        final Activity blurAttrActivity = startTestActivity(BlurAttributesActivity.class);
        final Rect windowFrame = getFloatingWindowFrame(blurAttrActivity);
        final Bitmap screenshot = takeScreenshotForBounds(mBackgroundActivityBounds);

        assertBlurBehind(screenshot, windowFrame);
        assertBackgroundBlurOverBlurBehind(screenshot, windowFrame);
    }

    @Test
    @ApiTest(apis = {"android.view.WindowManager.LayoutParams#setBlurBehindRadius",
                     "android.R.styleable#Window_windowBlurBehindEnabled",
                     "android.view.Window#setBackgroundBlurRadius"})
    public void testAllBlurRemovedAndRestoredWhenToggleBlurDisabled() {
        final BlurActivity blurActivity = startTestActivity(BlurActivity.class);
        getInstrumentation().runOnMainSync(() -> {
            blurActivity.setBlurBehindRadius(BLUR_BEHIND_PX);
            blurActivity.setBackgroundBlurRadius(BACKGROUND_BLUR_PX);
        });
        waitForActivityIdle(blurActivity);
        final Rect windowFrame = getFloatingWindowFrame(blurActivity);

        Bitmap screenshot = takeScreenshotForBounds(mBackgroundActivityBounds);
        assertBlurBehind(screenshot, windowFrame);
        assertBackgroundBlurOverBlurBehind(screenshot, windowFrame);

        setAndAssertForceBlurDisabled(true, blurActivity.mBlurEnabledListener);
        waitForActivityIdle(blurActivity);

        screenshot = takeScreenshotForBounds(mBackgroundActivityBounds);
        assertNoBackgroundBlur(screenshot, windowFrame);
        assertNoBlurBehind(screenshot, windowFrame);

        getInstrumentation().runOnMainSync(() -> {
            blurActivity.setBackgroundColor(Color.TRANSPARENT);
        });
        waitForActivityIdle(blurActivity);
        verifyOnlyBackgroundImageVisible();

        setAndAssertForceBlurDisabled(false, blurActivity.mBlurEnabledListener);
        waitForActivityIdle(blurActivity);

        screenshot = takeScreenshotForBounds(mBackgroundActivityBounds);
        assertBlurBehind(screenshot, windowFrame);
        assertBackgroundBlurOverBlurBehind(screenshot, windowFrame);
    }

    @Test
    @ApiTest(apis = {"android.view.WindowManager.LayoutParams#setBlurBehindRadius",
                     "android.R.styleable#Window_windowBlurBehindEnabled",
                     "android.view.Window#setBackgroundBlurRadius"})
    public void testBlurDestroyedAfterActivityFinished() {
        final BlurActivity blurActivity = startTestActivity(BlurActivity.class);
        getInstrumentation().runOnMainSync(() -> {
            blurActivity.setBlurBehindRadius(BLUR_BEHIND_PX);
            blurActivity.setBackgroundBlurRadius(BACKGROUND_BLUR_PX);
        });
        waitForActivityIdle(blurActivity);

        final Rect windowFrame = getFloatingWindowFrame(blurActivity);
        Bitmap screenshot = takeScreenshotForBounds(mBackgroundActivityBounds);

        assertBlurBehind(screenshot, windowFrame);
        assertBackgroundBlurOverBlurBehind(screenshot, windowFrame);

        blurActivity.finish();
        mWmState.waitAndAssertActivityRemoved(blurActivity.getComponentName());
        waitForActivityIdle(blurActivity);

        verifyOnlyBackgroundImageVisible();
    }

    @Test
    @ApiTest(apis = {"android.view.WindowManager#isCrossWindowBlurEnabled"})
    public void testIsCrossWindowBlurEnabledUpdatedCorrectly() {
        setAndAssertForceBlurDisabled(true);
        setAndAssertForceBlurDisabled(false);
    }

    @Test
    @ApiTest(apis = {"android.view.WindowManager#addCrossWindowBlurEnabledListener",
                     "android.view.WindowManager#removeCrossWindowBlurEnabledListener"})
    public void testBlurListener() {
        final BlurActivity activity = startTestActivity(BlurActivity.class);
        Mockito.verify(activity.mBlurEnabledListener).accept(true);

        setAndAssertForceBlurDisabled(true, activity.mBlurEnabledListener);
        setAndAssertForceBlurDisabled(false, activity.mBlurEnabledListener);

        activity.finishAndRemoveTask();
        mWmState.waitAndAssertActivityRemoved(activity.getComponentName());

        Mockito.clearInvocations(activity.mBlurEnabledListener);
        setAndAssertForceBlurDisabled(true);
        Mockito.verifyNoMoreInteractions(activity.mBlurEnabledListener);
    }

    public static class BackgroundActivity extends FocusableActivity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getSplashScreen().setOnExitAnimationListener(view -> view.remove());

            setContentView(R.layout.background_image);

            getWindow().setDecorFitsSystemWindows(false);
            getWindow().getInsetsController().hide(systemBars());
        }
    }

    public static class BlurActivity extends FocusableActivity {
        public final Consumer<Boolean> mBlurEnabledListener = spy(new BlurListener());

        private int mBackgroundBlurRadius = 0;
        private int mBlurBehindRadius = 0;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.blur_activity);
            getWindow().setDecorFitsSystemWindows(false);
            getWindow().getInsetsController().hide(systemBars());
        }

        @Override
        public void onAttachedToWindow() {
            super.onAttachedToWindow();
            getWindowManager().addCrossWindowBlurEnabledListener(getMainExecutor(),
                    mBlurEnabledListener);
        }

        @Override
        public void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            getWindowManager().removeCrossWindowBlurEnabledListener(mBlurEnabledListener);
        }

        void setBackgroundBlurRadius(int backgroundBlurRadius) {
            mBackgroundBlurRadius = backgroundBlurRadius;
            getWindow().setBackgroundBlurRadius(mBackgroundBlurRadius);
            setBackgroundColor(
                        mBackgroundBlurRadius > 0 && getWindowManager().isCrossWindowBlurEnabled()
                        ? Color.TRANSPARENT : NO_BLUR_BACKGROUND_COLOR);
        }

        void setBlurBehindRadius(int blurBehindRadius) {
            mBlurBehindRadius = blurBehindRadius;
            getWindow().getAttributes().setBlurBehindRadius(mBlurBehindRadius);
            getWindow().setAttributes(getWindow().getAttributes());
            getWindowManager().updateViewLayout(getWindow().getDecorView(),
                    getWindow().getAttributes());
        }

        void setBackgroundColor(int color) {
            getWindow().getDecorView().setBackgroundColor(color);
            getWindowManager().updateViewLayout(getWindow().getDecorView(),
                    getWindow().getAttributes());
        }

        public class BlurListener implements Consumer<Boolean> {
            @Override
            public void accept(Boolean enabled) {
                setBackgroundBlurRadius(mBackgroundBlurRadius);
                setBlurBehindRadius(mBlurBehindRadius);
            }
        }
    }

    /**
     * This activity is used to test 2 things:
     * 1. Blur behind does not work if WindowManager.LayoutParams.FLAG_BLUR_BEHIND is not set,
     *    respectively if windowBlurBehindEnabled is not set.
     * 2. Background blur does not work for opaque activities (where windowIsTranslucent is false)
     *
     * In the style of this activity windowBlurBehindEnabled is false and windowIsTranslucent is
     * false. As a result, we expect that neither blur behind, nor background blur is rendered,
     * even though they are requested with setBlurBehindRadius and setBackgroundBlurRadius.
     */
    public static class BadBlurActivity extends BlurActivity {
    }

    public static class BlurAttributesActivity extends FocusableActivity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.blur_activity);
            getWindow().setDecorFitsSystemWindows(false);
            getWindow().getInsetsController().hide(systemBars());
        }
    }

    private <T extends FocusableActivity> T startTestActivity(Class<T> activityClass) {
        T activity = startActivity(activityClass);
        ComponentName activityName = activity.getComponentName();
        waitAndAssertResumedActivity(activityName, activityName + " must be resumed");
        waitForActivityIdle(activity);
        return activity;
    }

    private Rect getFloatingWindowFrame(Activity activity) {
        mWmState.computeState(activity.getComponentName());
        String windowName = getWindowName(activity.getComponentName());
        Rect windowFrame =
                new Rect(mWmState.getMatchingVisibleWindowState(windowName).get(0).getFrame());
        // Offset the frame of the BlurActivity to the coordinates of
        // mBackgroundActivityBounds, because we only take the screenshot in that area.
        windowFrame.offset(-mBackgroundActivityBounds.left, -mBackgroundActivityBounds.top);
        return windowFrame;
    }

    private void waitForActivityIdle(Activity activity) {
        // This helps with the test flakiness
        getInstrumentation().runOnMainSync(() -> {});
        UiDevice.getInstance(getInstrumentation()).waitForIdle();
        getInstrumentation().getUiAutomation().syncInputTransactions();
        mWmState.computeState(activity.getComponentName());
    }

    private void setAndAssertForceBlurDisabled(boolean disable) {
        setAndAssertForceBlurDisabled(disable, null);
    }

    private void setAndAssertForceBlurDisabled(boolean disable,
                Consumer<Boolean> blurEnabledListener) {
        if (blurEnabledListener != null) {
            Mockito.clearInvocations(blurEnabledListener);
        }
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.DISABLE_WINDOW_BLURS, disable ? 1 : 0);
        if (blurEnabledListener != null) {
            Mockito.verify(blurEnabledListener, timeout(BROADCAST_WAIT_TIMEOUT))
                .accept(!disable);
        } else {
            PollingCheck.waitFor(BROADCAST_WAIT_TIMEOUT, () -> {
                return disable != mContext.getSystemService(WindowManager.class)
                        .isCrossWindowBlurEnabled();
            });
            assertTrue(!disable == mContext.getSystemService(WindowManager.class)
                        .isCrossWindowBlurEnabled());
        }
    }

    private void verifyOnlyBackgroundImageVisible() {
        final Bitmap screenshot = takeScreenshotForBounds(mBackgroundActivityBounds);
        mDumpOnFailure.dumpOnFailure("verifyOnlyBackgroundImageVisible", screenshot);
        final int height = screenshot.getHeight();
        final int width = screenshot.getWidth();

        final int blueWidth = width / 2;

        final int[] row = new int[width];

        for (int y = 0; y < height; y++) {
            screenshot.getPixels(row, 0, width, 0, y, row.length, 1);
            for (int x = 0; x < width; x++) {
                final int actual = row[x];
                final int expected = (x < blueWidth ? Color.BLUE : Color.RED);

                if (actual != expected) {
                    ColorUtils.verifyColor("failed for pixel (x, y) = (" + x + ", " + y + ")",
                            expected, actual, 1);
                }
            }
        }
    }

    private void assertBlurBehind(Bitmap screenshot, Rect windowFrame) {
        mDumpOnFailure.dumpOnFailure("assertBlurBehind", screenshot);
        assertBlur(screenshot, BLUR_BEHIND_PX, 0, windowFrame.top);
        assertBlur(screenshot, BLUR_BEHIND_PX, windowFrame.bottom, screenshot.getHeight());
    }

    private void assertBackgroundBlur(Bitmap screenshot, Rect windowFrame) {
        mDumpOnFailure.dumpOnFailure("assertBackgroundBlur", screenshot);
        assertBlur(screenshot, BACKGROUND_BLUR_PX, windowFrame.top, windowFrame.bottom);
    }

    private void assertBackgroundBlurOverBlurBehind(Bitmap screenshot, Rect windowFrame) {
        mDumpOnFailure.dumpOnFailure("assertBackgroundBlurOverBlurBehind", screenshot);
        assertBlur(
                screenshot,
                (int) Math.hypot(BACKGROUND_BLUR_PX, BLUR_BEHIND_PX),
                windowFrame.top,
                windowFrame.bottom);
    }

    private void assertNoBlurBehind(Bitmap screenshot, Rect windowFrame) {
        mDumpOnFailure.dumpOnFailure("assertNoBlurBehind", screenshot);

        // Batch fetch pixels from each row of the bitmap to speed up the test.
        final int[] row = new int[screenshot.getWidth()];

        for (int y = 0; y < screenshot.getHeight(); y++) {
            screenshot.getPixels(row, 0, screenshot.getWidth(), 0, y, row.length, 1);
            for (int x = 0; x < screenshot.getWidth(); x++) {
                if (!windowFrame.contains(x, y)) {
                    final int actual = row[x];
                    final int expected = (x < screenshot.getWidth() / 2 ? Color.BLUE : Color.RED);

                    if (actual != expected) {
                        ColorUtils.verifyColor(
                               "failed for pixel (x, y) = (" + x + ", " + y + ")",
                                expected, actual, 1);
                    }
                }
            }
        }
    }

    private void assertNoBackgroundBlur(Bitmap screenshot, Rect windowFrame) {
        mDumpOnFailure.dumpOnFailure("assertNoBackgroundBlur", screenshot);

        // Batch fetch pixels from each row of the bitmap to speed up the test.
        final int[] row = new int[windowFrame.width()];

        for (int y = windowFrame.top; y < windowFrame.bottom; y++) {
            screenshot.getPixels(
                    row, 0, screenshot.getWidth(), windowFrame.left, y, row.length, 1);
            for (int x = windowFrame.left; x < windowFrame.right; x++) {
                final int actual = row[x - windowFrame.left];
                final int expected = NO_BLUR_BACKGROUND_COLOR;

                if (actual != expected) {
                    ColorUtils.verifyColor("failed for pixel (x, y) = (" + x + ", " + y + ")",
                            expected, actual, 1);
                }
            }
        }
    }

    private void assertBlur(Bitmap screenshot, int blurRadius, int startHeight,
            int endHeight) {
        final int width = screenshot.getWidth();

        // Adjust the test to check a smaller part of the blurred area in order to accept
        // various blur algorithm approximations used in RenderEngine
        final int stepSize = blurRadius / 4;
        final int blurAreaStartX = width / 2 - blurRadius + stepSize;
        final int blurAreaEndX = width / 2 + blurRadius;

        // At 2 * radius there should be no visible blur effects.
        final int unaffectedBluePixelX = width / 2 - blurRadius * 2 - 1;
        final int unaffectedRedPixelX = width / 2 + blurRadius * 2 + 1;

        for (int y = startHeight; y < endHeight; y++) {
            Color previousColor = Color.valueOf(Color.BLUE);
            for (int x = blurAreaStartX; x < blurAreaEndX; x += stepSize) {
                Color currentColor = screenshot.getColor(x, y);

                if (previousColor.blue() <= currentColor.blue()) {
                    assertTrue("assertBlur failed for blue for pixel (x, y) = ("
                            + x + ", " + y + ");"
                            + " previousColor blue: " + previousColor.blue()
                            + ", currentColor blue: " + currentColor.blue()
                            , previousColor.blue() > currentColor.blue());
                }
                if (previousColor.red() >= currentColor.red()) {
                    assertTrue("assertBlur failed for red for pixel (x, y) = ("
                           + x + ", " + y + ");"
                           + " previousColor red: " + previousColor.red()
                           + ", currentColor red: " + currentColor.red(),
                           previousColor.red() < currentColor.red());
                }
                previousColor = currentColor;
            }
        }

        for (int y = startHeight; y < endHeight; y++) {
            final int unaffectedBluePixel = screenshot.getPixel(unaffectedBluePixelX, y);
            if (unaffectedBluePixel != Color.BLUE) {
                ColorUtils.verifyColor(
                        "failed for pixel (x, y) = (" + unaffectedBluePixelX + ", " + y + ")",
                        Color.BLUE, unaffectedBluePixel, 1);
            }
            final int unaffectedRedPixel = screenshot.getPixel(unaffectedRedPixelX, y);
            if (unaffectedRedPixel != Color.RED) {
                ColorUtils.verifyColor(
                        "failed for pixel (x, y) = (" + unaffectedRedPixelX + ", " + y + ")",
                        Color.RED, unaffectedRedPixel, 1);
            }
        }
    }
}
