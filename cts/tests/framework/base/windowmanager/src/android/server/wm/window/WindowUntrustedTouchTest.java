/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.server.wm.window;

import static android.Manifest.permission.ACCESS_SURFACE_FLINGER;
import static android.app.AppOpsManager.MODE_ALLOWED;
import static android.app.AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW;
import static android.server.wm.BuildUtils.HW_TIMEOUT_MULTIPLIER;
import static android.server.wm.UiDeviceUtils.pressUnlockButton;
import static android.server.wm.UiDeviceUtils.pressWakeupButton;
import static android.server.wm.WindowManagerState.STATE_RESUMED;
import static android.server.wm.overlay.Components.OverlayActivity.EXTRA_TOKEN;
import static android.view.WindowInsets.Type.navigationBars;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.Instrumentation;
import android.app.NotificationManager;
import android.app.WindowConfiguration;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Rect;
import android.hardware.input.InputManager;
import android.hardware.input.InputSettings;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.ComponentNameUtils;
import android.server.wm.CtsWindowInfoUtils;
import android.server.wm.FutureConnection;
import android.server.wm.TouchHelper;
import android.server.wm.WindowManagerState;
import android.server.wm.WindowManagerStateHelper;
import android.server.wm.overlay.Components;
import android.server.wm.overlay.R;
import android.server.wm.shared.BlockingResultReceiver;
import android.server.wm.shared.IUntrustedTouchTestService;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.Toast;

import androidx.annotation.AnimRes;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.filters.FlakyTest;

import com.android.compatibility.common.util.AppOpsUtils;
import com.android.compatibility.common.util.FeatureUtil;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Presubmit
public class WindowUntrustedTouchTest {
    private static final String TAG = "WindowUntrustedTouchTest";

    /**
     * Opacity (or alpha) is represented as a half-precision floating point number (16b) in surface
     * flinger and the conversion from the single-precision float provided to window manager happens
     * in Layer::setAlpha() by android::half::ftoh(). So, many small non-zero values provided to
     * window manager end up becoming zero due to loss of precision (this is fine as long as the
     * zeros are also used to render the pixels on the screen). So, the minimum opacity possible is
     * actually the minimum positive value representable in half-precision float, which is
     * 0_00001_0000000000, whose equivalent in float is 0_01110001_00000000000000000000000.
     *
     * Note that from float -> half conversion code we don't produce any subnormal half-precision
     * floats during conversion.
     */
    public static final float MIN_POSITIVE_OPACITY =
            Float.intBitsToFloat(0b00111000100000000000000000000000);

    private static final float MAXIMUM_OBSCURING_OPACITY = .8f;
    private static final long TIMEOUT_MS = 3000L;
    private static final long MAX_ANIMATION_DURATION_MS = 3000L;
    private static final long ANIMATION_DURATION_TOLERANCE_MS = 500L;

    private static final int OVERLAY_COLOR = 0xFFFF0000;
    private static final int ACTIVITY_COLOR = 0xFFFFFFFF;

    private static final String APP_SELF = "android.server.wm.cts";
    private static final String APP_A =
            android.server.wm.second.Components.class.getPackage().getName();
    private static final String APP_B =
            android.server.wm.third.Components.class.getPackage().getName();
    private static final String WINDOW_1 = "W1";
    private static final String WINDOW_2 = "W2";

    private static final String[] APPS = {APP_A, APP_B};

    private static final String SETTING_MAXIMUM_OBSCURING_OPACITY =
            "maximum_obscuring_opacity_for_touch";

    private final WindowManagerStateHelper mWmState = new WindowManagerStateHelper();
    private final Map<String, FutureConnection<IUntrustedTouchTestService>> mConnections =
            new ArrayMap<>();
    private Instrumentation mInstrumentation;
    private Context mContext;
    private Resources mResources;
    private ContentResolver mContentResolver;
    private TouchHelper mTouchHelper;
    private Handler mMainHandler;
    private InputManager mInputManager;
    private WindowManager mWindowManager;
    private ActivityManager mActivityManager;
    private NotificationManager mNotificationManager;
    private TestActivity mActivity;
    private View mContainer;
    private Toast mToast;
    private float mPreviousTouchOpacity;
    private int mPreviousSawAppOp;
    private final Set<String> mSawWindowsAdded = new ArraySet<>();
    private final AtomicInteger mTouchesReceived = new AtomicInteger(0);

    @ClassRule
    public static ActivityManagerTestBase.DisableImmersiveModeConfirmationRule
            mDisableImmersiveModeConfirmationRule =
            new ActivityManagerTestBase.DisableImmersiveModeConfirmationRule();

    @Rule
    public TestName testNameRule = new TestName();

    @Rule
    public ActivityScenarioRule<TestActivity> activityRule =
            new ActivityScenarioRule<>(TestActivity.class, createLaunchActivityOptionsBundle());

    @Before
    public void setUp() throws Exception {
        activityRule.getScenario().onActivity(activity -> {
            mActivity = activity;
            mContainer = mActivity.view;
            // On ARC++, text toast is fixed on the screen. Its position may overlays the navigation
            // bar. Hide it to ensure the text toast overlays the app. b/191075641
            mContainer.getWindowInsetsController().hide(navigationBars());
            mContainer.setOnTouchListener(this::onTouchEvent);
        });
        mInstrumentation = getInstrumentation();
        mContext = mInstrumentation.getContext();
        mResources = mContext.getResources();
        mContentResolver = mContext.getContentResolver();
        mTouchHelper = new TouchHelper(mInstrumentation, mWmState);
        mMainHandler = new Handler(Looper.getMainLooper());
        mInputManager = mContext.getSystemService(InputManager.class);
        mWindowManager = mContext.getSystemService(WindowManager.class);
        mActivityManager = mContext.getSystemService(ActivityManager.class);
        mNotificationManager = mContext.getSystemService(NotificationManager.class);

        mPreviousSawAppOp = AppOpsUtils.getOpMode(APP_SELF, OPSTR_SYSTEM_ALERT_WINDOW);
        AppOpsUtils.setOpMode(APP_SELF, OPSTR_SYSTEM_ALERT_WINDOW, MODE_ALLOWED);
        mPreviousTouchOpacity = setMaximumObscuringOpacityForTouch(MAXIMUM_OBSCURING_OPACITY);
        SystemUtil.runWithShellPermissionIdentity(
                () -> mNotificationManager.setToastRateLimitingEnabled(false));

        pressWakeupButton();
        pressUnlockButton();
    }

    @After
    public void tearDown() throws Throwable {
        mWmState.waitForAppTransitionIdleOnDisplay(Display.DEFAULT_DISPLAY);
        mTouchesReceived.set(0);
        removeOverlays();
        for (FutureConnection<IUntrustedTouchTestService> connection : mConnections.values()) {
            mContext.unbindService(connection);
        }
        mConnections.clear();
        for (String app : APPS) {
            stopPackage(app);
        }
        SystemUtil.runWithShellPermissionIdentity(
                () -> mNotificationManager.setToastRateLimitingEnabled(true));
        setMaximumObscuringOpacityForTouch(mPreviousTouchOpacity);
        AppOpsUtils.setOpMode(APP_SELF, OPSTR_SYSTEM_ALERT_WINDOW, mPreviousSawAppOp);
    }

    @Test
    public void testMaximumObscuringOpacity() throws Throwable {
        // Setting the previous value since we override this on setUp()
        setMaximumObscuringOpacityForTouch(mPreviousTouchOpacity);

        assertEquals(0.8f, mInputManager.getMaximumObscuringOpacityForTouch());
    }

    @Test
    public void testAfterSettingThreshold_returnsThresholdSet()
            throws Throwable {
        float threshold = .123f;
        setMaximumObscuringOpacityForTouch(threshold);

        assertEquals(threshold, mInputManager.getMaximumObscuringOpacityForTouch());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAfterSettingThresholdLessThan0_throws() throws Throwable {
        setMaximumObscuringOpacityForTouch(-.5f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAfterSettingThresholdGreaterThan1_throws() throws Throwable {
        setMaximumObscuringOpacityForTouch(1.5f);
    }

    /** SAWs */

    @Test
    public void testWhenOneSawWindowAboveThreshold_allowsTouch() throws Throwable {
        addSawOverlay(APP_A, WINDOW_1, .9f);

        mTouchHelper.tapOnViewCenter(mContainer);

        // Opacity will be automatically capped and touches will pass through.
        assertTouchReceived();
    }

    @Test
    public void testWhenOneSawWindowBelowThreshold_allowsTouch() throws Throwable {
        addSawOverlay(APP_A, WINDOW_1, .7f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    @Test
    public void testWhenOneSawWindowWithZeroOpacity_allowsTouch() throws Throwable {
        addSawOverlay(APP_A, WINDOW_1, 0f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    @Test
    public void testWhenOneSawWindowAtThreshold_allowsTouch() throws Throwable {
        addSawOverlay(APP_A, WINDOW_1, MAXIMUM_OBSCURING_OPACITY);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    @Test
    public void testWhenTwoSawWindowsFromSameAppTogetherBelowThreshold_allowsTouch()
            throws Throwable {
        // Resulting opacity = 1 - (1 - 0.5)*(1 - 0.5) = .75
        addSawOverlay(APP_A, WINDOW_1, .5f);
        addSawOverlay(APP_A, WINDOW_2, .5f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    @Test
    public void testWhenTwoSawWindowsFromSameAppTogetherAboveThreshold_blocksTouch()
            throws Throwable {
        // Resulting opacity = 1 - (1 - 0.7)*(1 - 0.7) = .91
        addSawOverlay(APP_A, WINDOW_1, .7f);
        addSawOverlay(APP_A, WINDOW_2, .7f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenTwoSawWindowsFromDifferentAppsEachBelowThreshold_allowsTouch()
            throws Throwable {
        addSawOverlay(APP_A, WINDOW_1, .7f);
        addSawOverlay(APP_B, WINDOW_2, .7f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    @Test
    public void testWhenOneSawWindowAboveThresholdAndSelfSawWindow_allowsTouch()
            throws Throwable {
        addSawOverlay(APP_A, WINDOW_1, .9f);
        addSawOverlay(APP_SELF, WINDOW_1, .7f);

        mTouchHelper.tapOnViewCenter(mContainer);

        // Opacity will be automatically capped and touches will pass through.
        assertTouchReceived();
    }

    @Test
    public void testWhenOneSawWindowBelowThresholdAndSelfSawWindow_allowsTouch()
            throws Throwable {
        addSawOverlay(APP_A, WINDOW_1, .7f);
        addSawOverlay(APP_SELF, WINDOW_1, .7f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    @Test
    public void testWhenTwoSawWindowsTogetherBelowThresholdAndSelfSawWindow_allowsTouch()
            throws Throwable {
        // Resulting opacity for A = 1 - (1 - 0.5)*(1 - 0.5) = .75
        addSawOverlay(APP_A, WINDOW_1, .5f);
        addSawOverlay(APP_A, WINDOW_1, .5f);
        addSawOverlay(APP_SELF, WINDOW_1, .7f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    @Test
    public void testWhenThresholdIs0AndSawWindowAtThreshold_allowsTouch()
            throws Throwable {
        setMaximumObscuringOpacityForTouch(0);
        addSawOverlay(APP_A, WINDOW_1, 0);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    @Test
    public void testWhenThresholdIs0AndSawWindowAboveThreshold_allowsTouch()
            throws Throwable {
        setMaximumObscuringOpacityForTouch(0);
        addSawOverlay(APP_A, WINDOW_1, .1f);

        mTouchHelper.tapOnViewCenter(mContainer);

        // Opacity will be automatically capped and touches will pass through.
        assertTouchReceived();
    }

    @Test
    public void testWhenThresholdIs1AndSawWindowAtThreshold_allowsTouch()
            throws Throwable {
        setMaximumObscuringOpacityForTouch(1);
        addSawOverlay(APP_A, WINDOW_1, 1);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    @Test
    public void testWhenThresholdIs1AndSawWindowBelowThreshold_allowsTouch()
            throws Throwable {
        setMaximumObscuringOpacityForTouch(1);
        addSawOverlay(APP_A, WINDOW_1, .9f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    /** Activity windows */

    @Test
    public void testWhenOneActivityWindowBelowThreshold_blocksTouch()
            throws Throwable {
        addActivityOverlay(APP_A, /* opacity */ .5f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenOneActivityWindowAboveThreshold_blocksTouch()
            throws Throwable {
        addActivityOverlay(APP_A, /* opacity */ .9f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenOneActivityWindowWithZeroOpacity_allowsTouch()
            throws Throwable {
        addActivityOverlay(APP_A, /* opacity */ 0f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    @Test
    public void testWhenOneActivityWindowWithMinPositiveOpacity_blocksTouch()
            throws Throwable {
        addActivityOverlay(APP_A, /* opacity */ MIN_POSITIVE_OPACITY);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenOneActivityWindowWithSmallOpacity_blocksTouch()
            throws Throwable {
        addActivityOverlay(APP_A, /* opacity */ .01f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenOneSelfActivityWindow_allowsTouch() throws Throwable {
        addActivityOverlay(APP_SELF, /* opacity */ .9f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    @Test
    public void testWhenTwoActivityWindowsFromDifferentAppsTogetherBelowThreshold_blocksTouch()
            throws Throwable {
        addActivityOverlay(APP_A, /* opacity */ .7f);
        addActivityOverlay(APP_B, /* opacity */ .7f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenOneActivityWindowAndOneSawWindowTogetherBelowThreshold_blocksTouch()
            throws Throwable {
        addActivityOverlay(APP_A, /* opacity */ .5f);
        addSawOverlay(APP_A, WINDOW_1, .5f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenOneActivityWindowAndOneSelfCustomToastWindow_blocksTouch()
            throws Throwable {
        // Toast has to be before otherwise it would be blocked from background
        addToastOverlay(APP_SELF, /* custom */ true);
        addActivityOverlay(APP_A, /* opacity */ .5f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenOneActivityWindowAndOneSelfSawWindow_blocksTouch()
            throws Throwable {
        addActivityOverlay(APP_A, /* opacity */ .5f);
        addSawOverlay(APP_SELF, WINDOW_1, .5f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenOneActivityWindowAndOneSawWindowBelowThreshold_blocksTouch()
            throws Throwable {
        addActivityOverlay(APP_A, /* opacity */ .5f);
        addSawOverlay(APP_A, WINDOW_1, .5f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenOneActivityWindowAndOneSawWindowBelowThresholdFromDifferentApp_blocksTouch()
            throws Throwable {
        addActivityOverlay(APP_A, /* opacity */ .5f);
        addSawOverlay(APP_B, WINDOW_1, .5f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    /** Activity-type child windows on same activity */

    @Test
    public void testWhenActivityChildWindowWithSameTokenFromDifferentApp_allowsTouch()
            throws Exception {
        IBinder token = mActivity.getWindow().getAttributes().token;
        addActivityChildWindow(APP_A, WINDOW_1, token);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    @Test
    public void testWhenActivityChildWindowWithDifferentTokenFromDifferentApp_blocksTouch()
            throws Exception {
        // Creates a new activity with 0 opacity
        BlockingResultReceiver receiver = new BlockingResultReceiver();
        addActivityOverlay(APP_A, /* opacity */ 0f, receiver);
        // Verify it allows touches
        mTouchHelper.tapOnViewCenter(mContainer);
        assertTouchReceived();
        // Now get its token and put a child window from another app with it
        IBinder token = receiver.getData(TIMEOUT_MS).getBinder(EXTRA_TOKEN);
        addActivityChildWindow(APP_B, WINDOW_1, token);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenActivityChildWindowWithDifferentTokenFromSameApp_allowsTouch()
            throws Exception {
        // Creates a new activity with 0 opacity
        BlockingResultReceiver receiver = new BlockingResultReceiver();
        addActivityOverlay(APP_A, /* opacity */ 0f, receiver);
        // Now get its token and put a child window owned by us
        IBinder token = receiver.getData(TIMEOUT_MS).getBinder(EXTRA_TOKEN);
        addActivityChildWindow(APP_SELF, WINDOW_1, token);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    /** Activity transitions */

    @Test
    public void testLongEnterAnimations_areLimited() {
        long durationSet = mResources.getInteger(R.integer.long_animation_duration);
        assertThat(durationSet).isGreaterThan(
                MAX_ANIMATION_DURATION_MS + ANIMATION_DURATION_TOLERANCE_MS);
        addAnimatedActivityOverlay(APP_A, /* touchable */ false, R.anim.long_alpha_0_7,
                R.anim.long_alpha_1);
        assertTrue(mWmState.waitForAppTransitionRunningOnDisplay(Display.DEFAULT_DISPLAY));
        long start = SystemClock.elapsedRealtime();

        assertTrue(mWmState.waitForAppTransitionIdleOnDisplay(Display.DEFAULT_DISPLAY));
        long duration = SystemClock.elapsedRealtime() - start;
        assertThat(duration).isAtMost(MAX_ANIMATION_DURATION_MS + ANIMATION_DURATION_TOLERANCE_MS);
    }

    @Test
    public void testLongExitAnimations_areLimited() {
        long durationSet = mResources.getInteger(R.integer.long_animation_duration);
        assertThat(durationSet).isGreaterThan(
                MAX_ANIMATION_DURATION_MS + ANIMATION_DURATION_TOLERANCE_MS);
        addExitAnimationActivity(APP_A);

        // Wait for ExitAnimationActivity open transition to complete to avoid counting this
        // transition in the duration of the exit animation below. Otherwise
        // waitForAppTransitionRunningOnDisplay might return immediately if this transition is not
        // done by then instead of waiting for the exit animation to start running.
        assertTrue(mWmState.waitForAppTransitionIdleOnDisplay(Display.DEFAULT_DISPLAY));

        sendFinishToExitAnimationActivity(APP_A,
                Components.ExitAnimationActivityReceiver.EXTRA_VALUE_LONG_ANIMATION_0_7);
        assertTrue(mWmState.waitForAppTransitionRunningOnDisplay(Display.DEFAULT_DISPLAY));
        long start = SystemClock.elapsedRealtime();

        assertTrue(mWmState.waitForAppTransitionIdleOnDisplay(Display.DEFAULT_DISPLAY));
        long duration = SystemClock.elapsedRealtime() - start;
        assertThat(duration).isAtMost(MAX_ANIMATION_DURATION_MS + ANIMATION_DURATION_TOLERANCE_MS);
    }

    @Test
    public void testWhenEnterAnimationAboveThresholdAndNewActivityNotTouchable_blocksTouch() {
        addAnimatedActivityOverlay(APP_A, /* touchable */ false, R.anim.alpha_0_9, R.anim.alpha_1);
        assertTrue(mWmState.waitForAppTransitionRunningOnDisplay(Display.DEFAULT_DISPLAY));

        mTouchHelper.tapOnViewCenter(mContainer, /* waitAnimations*/ false);

        assertAnimationRunning();
        assertTouchNotReceived();
    }

    @Test
    public void testWhenEnterAnimationBelowThresholdAndNewActivityNotTouchable_allowsTouch() {
        addAnimatedActivityOverlay(APP_A, /* touchable */ false, R.anim.alpha_0_7, R.anim.alpha_1);
        assertTrue(mWmState.waitForAppTransitionRunningOnDisplay(Display.DEFAULT_DISPLAY));

        mTouchHelper.tapOnViewCenter(mContainer, /* waitAnimations*/ false);

        assertAnimationRunning();
        assertTouchReceived();
    }

    @Test
    public void testWhenEnterAnimationBelowThresholdAndNewActivityTouchable_blocksTouch() {
        addAnimatedActivityOverlay(APP_A, /* touchable */ true, R.anim.alpha_0_7, R.anim.alpha_1);
        assertTrue(mWmState.waitForAppTransitionRunningOnDisplay(Display.DEFAULT_DISPLAY));

        mTouchHelper.tapOnViewCenter(mContainer, /* waitAnimations*/ false);

        assertAnimationRunning();
        assertTouchNotReceived();
    }

    @Test
    public void testWhenExitAnimationBelowThreshold_allowsTouch() {
        addExitAnimationActivity(APP_A);

        // Wait for ExitAnimationActivity open transition to complete to avoid
        // waitForAppTransitionRunningOnDisplay returning immediately if this transition is not
        // done by then instead of waiting for the exit animation to start running.
        assertTrue(mWmState.waitForAppTransitionIdleOnDisplay(Display.DEFAULT_DISPLAY));

        sendFinishToExitAnimationActivity(APP_A,
                Components.ExitAnimationActivityReceiver.EXTRA_VALUE_ANIMATION_0_7);
        assertTrue(mWmState.waitForAppTransitionRunningOnDisplay(Display.DEFAULT_DISPLAY));

        mTouchHelper.tapOnViewCenter(mContainer, /* waitAnimations*/ false);

        assertAnimationRunning();
        assertTouchReceived();
    }

    @Test
    public void testWhenExitAnimationAboveThreshold_blocksTouch() {
        addExitAnimationActivity(APP_A);
        sendFinishToExitAnimationActivity(APP_A,
                Components.ExitAnimationActivityReceiver.EXTRA_VALUE_ANIMATION_0_9);
        assertTrue(mWmState.waitForAppTransitionRunningOnDisplay(Display.DEFAULT_DISPLAY));

        mTouchHelper.tapOnViewCenter(mContainer, /* waitAnimations*/ false);

        assertAnimationRunning();
        assertTouchNotReceived();
    }

    @Test
    public void testWhenExitAnimationAboveThresholdFromSameUid_allowsTouch() {
        addExitAnimationActivity(APP_SELF);
        sendFinishToExitAnimationActivity(APP_SELF,
                Components.ExitAnimationActivityReceiver.EXTRA_VALUE_ANIMATION_0_9);
        assertTrue(mWmState.waitForAppTransitionRunningOnDisplay(Display.DEFAULT_DISPLAY));

        mTouchHelper.tapOnViewCenter(mContainer, /* waitAnimations*/ false);

        assertAnimationRunning();
        assertTouchReceived();
    }

    /** Toast windows */
    @FlakyTest(bugId = 293267005)
    @Test
    public void testWhenSelfTextToastWindow_allowsTouch() throws Throwable {
        addToastOverlay(APP_SELF, /* custom */ false);
        Rect toast = mWmState.waitForResult("toast bounds",
                state -> state.findFirstWindowWithType(LayoutParams.TYPE_TOAST).getFrame());
        int[] viewXY = new int[2];
        mContainer.getLocationOnScreen(viewXY);
        Rect containerRect = new Rect(viewXY[0], viewXY[1], viewXY[0] + mContainer.getWidth(),
                viewXY[1] + mContainer.getHeight());
        assumeTrue("Toast displayed outside of activity bounds.",
                containerRect.contains(toast.centerX(), toast.centerY()));

        mTouchHelper.tapOnCenter(toast, mActivity.getDisplayId());

        assertTouchReceived();
    }

    @Test
    public void testWhenTextToastWindow_allowsTouch() throws Throwable {
        assumeFalse("Watch does not support new Toast behavior yet.", FeatureUtil.isWatch());
        addToastOverlay(APP_A, /* custom */ false);
        Rect toast = mWmState.waitForResult("toast bounds",
                state -> state.findFirstWindowWithType(LayoutParams.TYPE_TOAST).getFrame());

        mTouchHelper.tapOnCenter(toast, mActivity.getDisplayId());

        assertTouchReceived();
    }

    @Test
    public void testWhenOneCustomToastWindow_blocksTouch() throws Throwable {
        addToastOverlay(APP_A, /* custom */ true);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenOneSelfCustomToastWindow_allowsTouch() throws Throwable {
        addToastOverlay(APP_SELF, /* custom */ true);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    @Test
    public void testWhenOneCustomToastWindowAndOneSelfSawWindow_blocksTouch()
            throws Throwable {
        addSawOverlay(APP_SELF, WINDOW_1, .9f);
        addToastOverlay(APP_A, /* custom */ true);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenOneCustomToastWindowAndOneSawWindowBelowThreshold_blocksTouch()
            throws Throwable {
        addSawOverlay(APP_A, WINDOW_1, .5f);
        addToastOverlay(APP_A, /* custom */ true);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenOneCustomToastWindowAndOneSawWindowBelowThresholdFromDifferentApp_blocksTouch()
            throws Throwable {
        addSawOverlay(APP_A, WINDOW_1, .5f);
        addToastOverlay(APP_B, /* custom */ true);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenOneSelfCustomToastWindowOneSelfActivityWindowAndOneSawBelowThreshold_allowsTouch()
            throws Throwable {
        addActivityOverlay(APP_SELF, /* opacity */ .9f);
        addSawOverlay(APP_A, WINDOW_1, .5f);
        addToastOverlay(APP_SELF, /* custom */ true);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    private boolean onTouchEvent(View view, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            mTouchesReceived.incrementAndGet();
        }
        return true;
    }

    private void assertTouchReceived() {
        mInstrumentation.waitForIdleSync();
        assertThat(mTouchesReceived.get()).isEqualTo(1);
        mTouchesReceived.set(0);
    }

    private void assertTouchNotReceived() {
        mInstrumentation.waitForIdleSync();
        assertThat(mTouchesReceived.get()).isEqualTo(0);
        mTouchesReceived.set(0);
    }

    private void assertAnimationRunning() {
        assertThat(mWmState.getDisplay(Display.DEFAULT_DISPLAY).getAppTransitionState()).isEqualTo(
                WindowManagerStateHelper.APP_STATE_RUNNING);
    }

    private void addToastOverlay(String packageName, boolean custom) throws Exception {
        // Making sure there are no toasts currently since we can only check for the presence of
        // *any* toast afterwards and we don't want to be in a situation where this method returned
        // because another toast was being displayed.
        waitForNoToastOverlays();
        if (custom) {
            if (packageName.equals(APP_SELF)) {
                // We add the custom toast here because we already have foreground status due to
                // the activity rule, so no need to start another activity.
                addMyCustomToastOverlay();
            } else {
                // We have to use an activity that will display the toast then finish itself because
                // custom toasts cannot be posted from the background.
                Intent intent = new Intent();
                intent.setComponent(repackage(packageName, Components.ToastActivity.COMPONENT));
                mActivity.startActivity(intent);
            }
        } else {
            getService(packageName).showToast();
        }
        String message = "Toast from app " + packageName + " did not appear on time";
        // TODO: WindowStateProto does not have package/UID information from the window, the current
        //  package test relies on the window name, which is not how toast windows are named. We
        //  should ideally incorporate that information in WindowStateProto and use here.
        if (!mWmState.waitFor("toast window", this::hasVisibleToast)) {
            fail(message);
        }
    }

    private boolean hasVisibleToast(WindowManagerState state) {
        return !state.getMatchingWindowType(LayoutParams.TYPE_TOAST).isEmpty()
                && state.findFirstWindowWithType(LayoutParams.TYPE_TOAST).isSurfaceShown();
    }

    private void addMyCustomToastOverlay() {
        mActivity.runOnUiThread(() -> {
            mToast = new Toast(mContext);
            View view = new View(mContext);
            view.setBackgroundColor(OVERLAY_COLOR);
            mToast.setView(view);
            mToast.setGravity(Gravity.FILL, 0, 0);
            mToast.setDuration(Toast.LENGTH_LONG);
            mToast.show();
        });
        mInstrumentation.waitForIdleSync();
    }

    private void removeMyCustomToastOverlay() {
        mActivity.runOnUiThread(() -> {
            if (mToast != null) {
                mToast.cancel();
                mToast = null;
            }
        });
        mInstrumentation.waitForIdleSync();
    }

    private void waitForNoToastOverlays() {
        waitForNoToastOverlays("Toast windows did not hide on time");
    }

    private void waitForNoToastOverlays(String message) {
        if (!mWmState.waitFor("no toast windows",
                state -> state.getMatchingWindowType(LayoutParams.TYPE_TOAST).isEmpty())) {
            fail(message + " Still visible toasts: " + mWmState.getMatchingWindowType(
                    LayoutParams.TYPE_TOAST));
        }
    }

    private void addExitAnimationActivity(String packageName) {
        // This activity responds to broadcasts to exit with animations and it's opaque (translucent
        // activities don't honor custom exit animations).
        addActivity(repackage(packageName, Components.ExitAnimationActivity.COMPONENT),
                /* extras */ null, /* options */ null);
    }

    private void sendFinishToExitAnimationActivity(String packageName, int exitAnimation) {
        Intent intent = new Intent(Components.ExitAnimationActivityReceiver.ACTION_FINISH);
        intent.setPackage(packageName);
        intent.putExtra(Components.ExitAnimationActivityReceiver.EXTRA_ANIMATION, exitAnimation);
        mContext.sendBroadcast(intent);
    }

    private void addAnimatedActivityOverlay(String packageName, boolean touchable,
            @AnimRes int enterAnim, @AnimRes int exitAnim) {
        ConditionVariable animationsStarted = new ConditionVariable(false);
        ActivityOptions options = ActivityOptions.makeCustomAnimation(mContext, enterAnim, exitAnim,
                0, mMainHandler, (t) -> animationsStarted.open(), /* finishedListener */ null);
        // We're testing the opacity coming from the animation here, not the one declared in the
        // activity, so we set its opacity to 1
        addActivityOverlay(packageName, /* opacity */ 1, touchable, options.toBundle());
        animationsStarted.block();
    }

    private void addActivityChildWindow(String packageName, String windowSuffix, IBinder token)
            throws Exception {
        String name = getWindowName(packageName, windowSuffix);
        getService(packageName).showActivityChildWindow(name, token);
        if (!mWmState.waitFor("activity child window " + name,
                state -> state.isWindowVisible(name) && state.isWindowSurfaceShown(name))) {
            fail("Activity child window " + name + " did not appear on time");
        }
    }

    private void addActivityOverlay(String packageName, float opacity) {
        addActivityOverlay(packageName, opacity, /* touchable */ false, /* options */ null);
    }

    private void addActivityOverlay(String packageName, float opacity, boolean touchable,
            @Nullable Bundle options) {
        Bundle extras = new Bundle();
        extras.putFloat(Components.OverlayActivity.EXTRA_OPACITY, opacity);
        extras.putBoolean(Components.OverlayActivity.EXTRA_TOUCHABLE, touchable);
        addActivityOverlay(packageName, extras, options);
    }

    private void addActivityOverlay(String packageName, float opacity,
            BlockingResultReceiver tokenReceiver) {
        Bundle extras = new Bundle();
        extras.putFloat(Components.OverlayActivity.EXTRA_OPACITY, opacity);
        extras.putParcelable(Components.OverlayActivity.EXTRA_TOKEN_RECEIVER, tokenReceiver);
        addActivityOverlay(packageName, extras, /* options */ null);
    }

    private void addActivityOverlay(String packageName, @Nullable Bundle extras,
            @Nullable Bundle options) {
        addActivity(repackage(packageName, Components.OverlayActivity.COMPONENT), extras, options);
    }

    private void addActivity(ComponentName component, @Nullable Bundle extras,
            @Nullable Bundle options) {
        Intent intent = new Intent();
        intent.setComponent(component);
        if (extras != null) {
            intent.putExtras(extras);
        }
        mActivity.startActivity(intent, options);
        String packageName = component.getPackageName();
        String activity = ComponentNameUtils.getActivityName(component);
        if (!mWmState.waitFor("activity window " + activity,
                state -> activity.equals(state.getFocusedActivity())
                        && state.hasActivityState(component, STATE_RESUMED)
                        && state.isWindowSurfaceShown(activity))) {
            fail("Activity from app " + packageName + " did not appear on time");
        }

        // We need to make sure that InputFlinger has populated window info with correct bounds
        // before proceeding.
        // Note that com.android.server.wm.WindowState computes InputWindowHandle's name by
        // concatenating its hash and title.
        WindowManagerState.WindowState focusedWindowState = mWmState.getWindowState(component);
        Rect expectedBounds = mWmState.getActivity(component).getBounds();
        SystemUtil.runWithShellPermissionIdentity(() -> {
            if (!CtsWindowInfoUtils.waitForWindowOnTop(5 * HW_TIMEOUT_MULTIPLIER, TimeUnit.SECONDS,
                    window -> window.name.contains(focusedWindowState.getToken())
                            && window.name.contains(focusedWindowState.getName()))) {
                fail("Window " + focusedWindowState.getName() + " did not appear in InputFlinger "
                        + "with an expected bounds " + expectedBounds);
            }
        }, ACCESS_SURFACE_FLINGER);
    }

    private void removeActivityOverlays() {
        Intent intent = new Intent(mContext, mActivity.getClass());
        // Will clear any activity on top of it and it will become the new top
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        mActivity.startActivity(intent);
    }

    private void waitForNoActivityOverlays(String message) {
        // Base activity focused means no activities on top
        ComponentName component = mActivity.getComponentName();
        String name = ComponentNameUtils.getActivityName(component);
        if (!mWmState.waitFor("test rule activity focused",
                state -> name.equals(state.getFocusedActivity())
                        && state.hasActivityState(component, STATE_RESUMED))) {
            fail(message);
        }
    }

    private void addSawOverlay(String packageName, String windowSuffix, float opacity)
            throws Throwable {
        String name = getWindowName(packageName, windowSuffix);
        int[] viewXY = new int[2];
        mContainer.getLocationOnScreen(viewXY);
        getService(packageName).showSystemAlertWindow(name, opacity, viewXY[0], viewXY[1]);
        mSawWindowsAdded.add(name);
        if (!mWmState.waitFor("saw window " + name,
                state -> state.isWindowVisible(name) && state.isWindowSurfaceShown(name))) {
            fail("Saw window " + name + " did not appear on time");
        }
    }

    private void waitForNoSawOverlays(String message) {
        if (!mWmState.waitFor("no SAW windows",
                state -> mSawWindowsAdded.stream().allMatch(w -> !state.isWindowVisible(w)))) {
            fail(message);
        }
        mSawWindowsAdded.clear();
    }

    private void removeOverlays() throws Throwable {
        for (FutureConnection<IUntrustedTouchTestService> connection : mConnections.values()) {
            connection.getCurrent().removeOverlays();
        }
        // We need to stop the app because not every overlay is created via the service (eg.
        // activity overlays and custom toasts)
        for (String app : APPS) {
            stopPackage(app);
        }
        waitForNoSawOverlays("SAWs not removed on time");
        removeActivityOverlays();
        waitForNoActivityOverlays("Activities not removed on time");
        removeMyCustomToastOverlay();
        waitForNoToastOverlays("Toasts not removed on time");
    }

    private void stopPackage(String packageName) {
        SystemUtil.runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(packageName));
    }

    private float setMaximumObscuringOpacityForTouch(float opacity) throws Exception {
        return SystemUtil.callWithShellPermissionIdentity(() -> {
            float previous = mInputManager.getMaximumObscuringOpacityForTouch();
            InputSettings.setMaximumObscuringOpacityForTouch(mContext, opacity);
            return previous;
        });
    }

    private IUntrustedTouchTestService getService(String packageName) throws Exception {
        return mConnections.computeIfAbsent(packageName, this::connect).get(TIMEOUT_MS);
    }

    private FutureConnection<IUntrustedTouchTestService> connect(String packageName) {
        FutureConnection<IUntrustedTouchTestService> connection =
                new FutureConnection<>(IUntrustedTouchTestService.Stub::asInterface);
        Intent intent = new Intent();
        intent.setComponent(repackage(packageName, Components.UntrustedTouchTestService.COMPONENT));
        assertTrue(mContext.bindService(intent, connection, Context.BIND_AUTO_CREATE));
        return connection;
    }

    private static String getWindowName(String packageName, String windowSuffix) {
        return packageName + "." + windowSuffix;
    }

    private static ComponentName repackage(String packageName, ComponentName baseComponent) {
        return new ComponentName(packageName, baseComponent.getClassName());
    }

    private static Bundle createLaunchActivityOptionsBundle() {
        final ActivityOptions options = ActivityOptions.makeBasic();
        // Launch test in the fullscreen mode with navigation bar hidden,
        // in order to ensure text toast is tappable and overlays above the test app
        // on freeform first devices. b/191075641.
        options.setLaunchWindowingMode(WindowConfiguration.WINDOWING_MODE_FULLSCREEN);
        return options.toBundle();
    }

    public static class TestActivity extends Activity {
        public View view;

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            view = new View(this);
            view.setBackgroundColor(ACTIVITY_COLOR);
            setContentView(view);
        }
    }
}
