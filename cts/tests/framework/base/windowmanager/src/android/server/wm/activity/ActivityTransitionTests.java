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

package android.server.wm.activity;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.server.wm.activity.ActivityTransitionTests.CustomBackgroundTransitionActivity.BACKGROUND_COLOR_KEY;
import static android.server.wm.activity.ActivityTransitionTests.CustomBackgroundTransitionActivity.ENTER_ANIM_KEY;
import static android.server.wm.activity.ActivityTransitionTests.CustomBackgroundTransitionActivity.EXIT_ANIM_KEY;
import static android.server.wm.activity.ActivityTransitionTests.EdgeExtensionActivity.BOTTOM;
import static android.server.wm.activity.ActivityTransitionTests.EdgeExtensionActivity.DIRECTION_KEY;
import static android.server.wm.activity.ActivityTransitionTests.EdgeExtensionActivity.LEFT;
import static android.server.wm.activity.ActivityTransitionTests.EdgeExtensionActivity.RIGHT;
import static android.server.wm.activity.ActivityTransitionTests.EdgeExtensionActivity.TOP;
import static android.server.wm.app.Components.TEST_ACTIVITY;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.RoundedCorner.POSITION_BOTTOM_RIGHT;
import static android.view.RoundedCorner.POSITION_TOP_LEFT;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.Insets;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import android.provider.Settings;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.Condition;
import android.server.wm.WindowManagerState;
import android.server.wm.cts.R;
import android.server.wm.settings.SettingsSession;
import android.util.Range;
import android.view.RoundedCorner;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;

import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * <p>Build/Install/Run:
 * atest CtsWindowManagerDeviceActivity:ActivityTransitionTests
 */
@Presubmit
public class ActivityTransitionTests extends ActivityManagerTestBase {
    // Duration of the R.anim.alpha animation.
    private static final long CUSTOM_ANIMATION_DURATION = 2000L;

    // Allowable range with error error for the R.anim.alpha animation duration.
    private static final Range<Long> CUSTOM_ANIMATION_DURATION_RANGE = new Range<>(
            CUSTOM_ANIMATION_DURATION - 200L, CUSTOM_ANIMATION_DURATION + 1000L);

    static final String TEST_METHOD_KEY = "test_method_key";
    static final String TRANSITION_TYPE_KEY = "transition_type_key";

    static final int TEST_METHOD_OVERRIDE_PENDING_TRANSITION = 1;
    static final int TEST_METHOD_OVERRIDE_ACTIVITY_TRANSITION = 2;
    static final int TEST_METHOD_CLEAR_OVERRIDE_ACTIVITY_TRANSITION = 3;

    static final int TRANSITION_TYPE_OPEN = 0x1;
    static final int TRANSITION_TYPE_CLOSE = 0x2;

    static final String ACTION_UPDATE =
            "android.server.wm.activity.ActivityTransitionTests.ACTION_UPDATE";
    static final String ACTION_FINISH =
            "android.server.wm.activity.ActivityTransitionTests.ACTION_FINISH";

    // We need to allow for some variation stemming from color conversions
    private static final float COLOR_VALUE_VARIANCE_TOLERANCE = 0.05f;

    @ClassRule
    public static DisableImmersiveModeConfirmationRule mDisableImmersiveModeConfirmationRule =
            new DisableImmersiveModeConfirmationRule();

    @ClassRule
    public static final TestRule enableWindowAnimationRule = SettingsSession.overrideForTest(
            Settings.Global.getUriFor(Settings.Global.WINDOW_ANIMATION_SCALE),
            Settings.Global::getFloat,
            Settings.Global::putFloat,
            1.0f);

    @ClassRule
    public static final TestRule enableTransitionAnimationRule = SettingsSession.overrideForTest(
            Settings.Global.getUriFor(Settings.Global.TRANSITION_ANIMATION_SCALE),
            Settings.Global::getFloat,
            Settings.Global::putFloat,
            1.0f);

    @ClassRule
    public static final TestRule enableAnimatorDurationRule = SettingsSession.overrideForTest(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            Settings.Global::getFloat,
            Settings.Global::putFloat,
            1.0f);

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mWmState.setSanityCheckWithFocusedWindow(false);
        mWmState.waitForDisplayUnfrozen();
    }

    @After
    public void tearDown() {
        mWmState.setSanityCheckWithFocusedWindow(true);
    }

    private LauncherActivity startLauncherActivity() {
        final Intent intent = new Intent(mContext, LauncherActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        return (LauncherActivity) instrumentation.startActivitySync(intent);
    }

    @Test
    public void testActivityTransitionOverride() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        AtomicLong transitionStartTime = new AtomicLong();
        AtomicLong transitionEndTime = new AtomicLong();

        final ActivityOptions.OnAnimationStartedListener startedListener = transitionStartTime::set;
        final ActivityOptions.OnAnimationFinishedListener finishedListener = (t) -> {
            transitionEndTime.set(t);
            latch.countDown();
        };

        final LauncherActivity launcherActivity = startLauncherActivity();

        final ActivityOptions options = ActivityOptions.makeCustomAnimation(mContext,
                R.anim.alpha, 0 /* exitResId */, 0 /* backgroundColor */,
                new Handler(Looper.getMainLooper()), startedListener, finishedListener);
        launcherActivity.startActivity(options, TransitionActivity.class);
        mWmState.waitForAppTransitionIdleOnDisplay(DEFAULT_DISPLAY);
        waitAndAssertTopResumedActivity(new ComponentName(mContext, TransitionActivity.class),
                DEFAULT_DISPLAY, "Activity must be launched");

        latch.await(5, TimeUnit.SECONDS);
        final long totalTime = transitionEndTime.get() - transitionStartTime.get();
        assertTrue("Actual transition duration should be in the range "
                + "<" + CUSTOM_ANIMATION_DURATION_RANGE.getLower() + ", "
                + CUSTOM_ANIMATION_DURATION_RANGE.getUpper() + "> ms, "
                + "actual=" + totalTime, CUSTOM_ANIMATION_DURATION_RANGE.contains(totalTime));
    }

    @Test
    public void testTaskTransitionOverrideDisabled() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        AtomicLong transitionStartTime = new AtomicLong();
        AtomicLong transitionEndTime = new AtomicLong();

        final ActivityOptions.OnAnimationStartedListener startedListener = transitionStartTime::set;
        final ActivityOptions.OnAnimationFinishedListener finishedListener = (t) -> {
            transitionEndTime.set(t);
            latch.countDown();
        };

        // Overriding task transit animation is disabled, so default wallpaper close animation
        // is played.
        final Bundle bundle = ActivityOptions.makeCustomAnimation(mContext,
                R.anim.alpha, 0 /* exitResId */, 0 /* backgroundColor */,
                new Handler(Looper.getMainLooper()), startedListener, finishedListener).toBundle();
        final Intent intent = new Intent().setComponent(TEST_ACTIVITY)
                .addFlags(FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent, bundle);
        mWmState.waitForAppTransitionIdleOnDisplay(DEFAULT_DISPLAY);
        waitAndAssertTopResumedActivity(TEST_ACTIVITY, DEFAULT_DISPLAY,
                "Activity must be launched");

        latch.await(5, TimeUnit.SECONDS);
        final long totalTime = transitionEndTime.get() - transitionStartTime.get();
        assertTrue("Actual transition duration should be out of the range "
                + "<" + CUSTOM_ANIMATION_DURATION_RANGE.getLower() + ", "
                + CUSTOM_ANIMATION_DURATION_RANGE.getUpper() + "> ms, "
                + "actual=" + totalTime, !CUSTOM_ANIMATION_DURATION_RANGE.contains(totalTime));
    }

    @Test
    public void testTaskWindowAnimationOverrideDisabled() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        AtomicLong transitionStartTime = new AtomicLong();
        AtomicLong transitionEndTime = new AtomicLong();

        final ActivityOptions.OnAnimationStartedListener startedListener = transitionStartTime::set;
        final ActivityOptions.OnAnimationFinishedListener finishedListener = (t) -> {
            transitionEndTime.set(t);
            latch.countDown();
        };

        // Overriding task transit animation is disabled, so default wallpaper close animation
        // is played.
        final Bundle bundle = ActivityOptions.makeCustomAnimation(mContext,
                R.anim.alpha, 0 /* exitResId */, 0 /* backgroundColor */,
                new Handler(Looper.getMainLooper()), startedListener, finishedListener).toBundle();

        final ComponentName customWindowAnimationActivity = new ComponentName(
                mContext, CustomWindowAnimationActivity.class);
        final Intent intent = new Intent().setComponent(customWindowAnimationActivity)
                .addFlags(FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent, bundle);
        mWmState.waitForAppTransitionIdleOnDisplay(DEFAULT_DISPLAY);
        waitAndAssertTopResumedActivity(customWindowAnimationActivity, DEFAULT_DISPLAY,
                "Activity must be launched");

        latch.await(5, TimeUnit.SECONDS);
        final long totalTime = transitionEndTime.get() - transitionStartTime.get();
        assertTrue("Actual transition duration should be out of the range "
                + "<" + CUSTOM_ANIMATION_DURATION_RANGE.getLower() + ", "
                + CUSTOM_ANIMATION_DURATION_RANGE.getUpper() + "> ms, "
                + "actual=" + totalTime, !CUSTOM_ANIMATION_DURATION_RANGE.contains(totalTime));
    }

    /**
     * Checks that the activity's theme's background color is used as the default animation's
     * background color when no override is specified.
     */
    @Test
    public void testThemeBackgroundColorShowsDuringActivityTransition() {
        final int backgroundColor = Color.WHITE;
        final TestBounds testBounds = getTestBounds();

        getTestBuilder().setClass(TransitionActivityWithWhiteBackground.class)
                .setTestFunction(createAssertAppRegionOfScreenIsColor(backgroundColor, testBounds))
                .run();
    }

    /**
     * Checks that the background color set in the animation definition is used as the animation's
     * background color instead of the theme's background color.
     *
     * @see R.anim.alpha_0_with_red_backdrop for animation defintition.
     */
    @Test
    public void testAnimationBackgroundColorIsUsedDuringActivityTransition() {
        // TODO (b/319637823): Find proper fix for background overriding in ActivityTransitionTests
        // This test expects the default task display area to show a red color during the
        // transition. But since in auto split screen UI, the launcher activity is always
        // shown, no screenshot of the test would return the red background color.
        assumeFalse(hasAutomotiveSplitscreenMultitaskingFeature());
        final int backgroundColor = Color.RED;
        final ActivityOptions activityOptions = ActivityOptions.makeCustomAnimation(mContext,
                R.anim.alpha_0_with_red_backdrop, R.anim.alpha_0_with_red_backdrop);
        final TestBounds testBounds = getTestBounds();

        getTestBuilder().setClass(TransitionActivityWithWhiteBackground.class)
                .setActivityOptions(activityOptions)
                .setTestFunction(createAssertAppRegionOfScreenIsColor(backgroundColor, testBounds))
                .run();
    }

    /**
     * Checks that we can override the default background color of the animation using the
     * CustomAnimation activityOptions.
     */
    @Test
    public void testCustomTransitionCanOverrideBackgroundColor() {
        // TODO (b/319637823): Find proper fix for background overriding in ActivityTransitionTests
        // This test expects the default task display area to show a green color during the
        // transition. But since in auto split screen UI, the launcher activity is always
        // shown, no screenshot of the test would return the green background color.
        assumeFalse(hasAutomotiveSplitscreenMultitaskingFeature());
        final int backgroundColor = Color.GREEN;
        final ActivityOptions activityOptions = ActivityOptions.makeCustomAnimation(mContext,
                R.anim.alpha_0_with_backdrop, R.anim.alpha_0_with_backdrop, backgroundColor
        );
        final TestBounds testBounds = getTestBounds();

        getTestBuilder().setClass(TransitionActivityWithWhiteBackground.class)
                .setActivityOptions(activityOptions)
                .setTestFunction(createAssertAppRegionOfScreenIsColor(backgroundColor, testBounds))
                .run();
    }

    /**
     * Checks that we can override the default background color of the animation through
     * overridePendingTransition.
     */
    @Test
    public void testPendingTransitionCanOverrideBackgroundColor() {
        // TODO (b/319637823): Find proper fix for background overriding in ActivityTransitionTests
        // This test expects the default task display area to show a green color during the
        // transition. But since in auto split screen UI, the launcher activity is always
        // shown, no screenshot of the test would return the green background color.
        assumeFalse(hasAutomotiveSplitscreenMultitaskingFeature());
        final int backgroundColor = Color.GREEN;

        final Bundle extras = new Bundle();
        extras.putInt(ENTER_ANIM_KEY, R.anim.alpha_0_with_backdrop);
        extras.putInt(EXIT_ANIM_KEY, R.anim.alpha_0_with_backdrop);
        extras.putInt(BACKGROUND_COLOR_KEY, backgroundColor);
        addTestMethodToExtras(TEST_METHOD_OVERRIDE_PENDING_TRANSITION, 0, extras);
        final TestBounds testBounds = getTestBounds();

        getTestBuilder().setClass(CustomBackgroundTransitionActivity.class).setExtras(extras)
                .setTestFunction(createAssertAppRegionOfScreenIsColor(backgroundColor, testBounds))
                .run();
    }

    @Test
    public void testSetTransitionCanOverrideBackgroundColor() {
        // TODO (b/319637823): Find proper fix for background overriding in ActivityTransitionTests
        // This test expects the default task display area to show a green color during the
        // transition. But since in auto split screen UI, the launcher activity is always
        // shown, no screenshot of the test would return the green background color.
        assumeFalse(hasAutomotiveSplitscreenMultitaskingFeature());
        final int backgroundColor = Color.GREEN;

        final Bundle extras = new Bundle();
        extras.putInt(ENTER_ANIM_KEY, R.anim.alpha_0_with_backdrop);
        extras.putInt(EXIT_ANIM_KEY, R.anim.alpha_0_with_backdrop);
        extras.putInt(BACKGROUND_COLOR_KEY, backgroundColor);
        addTestMethodToExtras(TEST_METHOD_OVERRIDE_ACTIVITY_TRANSITION,
                TRANSITION_TYPE_OPEN | TRANSITION_TYPE_CLOSE, extras);
        final TestBounds testBounds = getTestBounds();

        getTestBuilder().setClass(CustomBackgroundTransitionActivity.class).setExtras(extras)
                .setTestFunction(createAssertAppRegionOfScreenIsColor(backgroundColor, testBounds))
                .run();

        mWmState.waitForAppTransitionIdleOnDisplay(DEFAULT_DISPLAY);
        mContext.sendBroadcast(new Intent(ACTION_FINISH));
        runAndAssertActivityTransition(
                createAssertAppRegionOfScreenIsColor(backgroundColor, testBounds));
    }
    /**
     * Checks that when an activity transition with a left edge extension is run that the animating
     * activity is extended on the left side by clamping the edge pixels of the activity.
     *
     * The test runs an activity transition where the animating activities are X scaled to 50%,
     * positioned of the right side of the screen, and edge extended on the left. Because the
     * animating activities are half red half blue (split at the middle of the X axis of the
     * activity). We expect first 75% pixel columns of the screen to be red (50% from the edge
     * extension and the next 25% from from the activity) and the remaining 25% columns after that
     * to be blue (from the activity).
     *
     * @see R.anim.edge_extension_left for the transition applied.
     */
    @Test
    public void testLeftEdgeExtensionWorksDuringActivityTransition() {
        final Bundle extras = new Bundle();
        extras.putInt(DIRECTION_KEY, LEFT);
        addTestMethodToExtras(TEST_METHOD_OVERRIDE_PENDING_TRANSITION, 0, extras);
        final TestBounds testBounds = getTestBounds();
        final Rect transitionBounds = testBounds.transitionBounds;
        final int xIndex = transitionBounds.left
                + (transitionBounds.right - transitionBounds.left) * 3 / 4;
        getTestBuilder().setClass(EdgeExtensionActivity.class).setExtras(extras)
                .setTestFunction(createAssertColorChangeXIndex(xIndex, testBounds))
                .run();
    }

    /**
     * Checks that when an activity transition with a top edge extension is run that the animating
     * activity is extended on the left side by clamping the edge pixels of the activity.
     *
     * The test runs an activity transition where the animating activities are Y scaled to 50%,
     * positioned of the bottom of the screen, and edge extended on the top. Because the
     * animating activities are half red half blue (split at the middle of the X axis of the
     * activity). We expect first 50% pixel columns of the screen to be red (the top half from the
     * extension and the bottom half from the activity) and the remaining 50% columns after that
     * to be blue (the top half from the extension and the bottom half from the activity).
     *
     * @see R.anim.edge_extension_top for the transition applied.
     */
    @Test
    public void testTopEdgeExtensionWorksDuringActivityTransition() {
        final Bundle extras = new Bundle();
        extras.putInt(DIRECTION_KEY, TOP);
        addTestMethodToExtras(TEST_METHOD_OVERRIDE_PENDING_TRANSITION, 0, extras);
        final TestBounds testBounds = getTestBounds();
        final Rect transitionBounds = testBounds.transitionBounds;
        final int xIndex = (transitionBounds.left + transitionBounds.right) / 2;
        getTestBuilder().setClass(EdgeExtensionActivity.class).setExtras(extras)
                .setTestFunction(createAssertColorChangeXIndex(xIndex, testBounds))
                .run();
    }

    /**
     * Checks that when an activity transition with a right edge extension is run that the animating
     * activity is extended on the right side by clamping the edge pixels of the activity.
     *
     * The test runs an activity transition where the animating activities are X scaled to 50% and
     * edge extended on the right. Because the animating activities are half red half blue. We
     * expect first 25% pixel columns of the screen to be red (from the activity) and the remaining
     * 75% columns after that to be blue (25% from the activity and 50% from the edge extension
     * which should be extending the right edge pixel (so red pixels).
     *
     * @see R.anim.edge_extension_right for the transition applied.
     */
    @Test
    public void testRightEdgeExtensionWorksDuringActivityTransition() {
        final Bundle extras = new Bundle();
        extras.putInt(DIRECTION_KEY, RIGHT);
        addTestMethodToExtras(TEST_METHOD_OVERRIDE_PENDING_TRANSITION, 0, extras);
        final TestBounds testBounds = getTestBounds();
        final Rect transitionBounds = testBounds.transitionBounds;
        final int xIndex = transitionBounds.left
                + (transitionBounds.right - transitionBounds.left) / 4;
        getTestBuilder().setClass(EdgeExtensionActivity.class).setExtras(extras)
                .setTestFunction(createAssertColorChangeXIndex(xIndex, testBounds))
                .run();
    }

    /**
     * Borrow the test from testRightEdgeExtensionWorksDuringActivityTransition, mainly test for
     * API Activity#overrideActivityTransition.
     */
    @Test
    public void testOverrideActivityTransition() {
        final Bundle extras = new Bundle();
        extras.putInt(DIRECTION_KEY, RIGHT);
        addTestMethodToExtras(TEST_METHOD_OVERRIDE_ACTIVITY_TRANSITION,
                TRANSITION_TYPE_OPEN | TRANSITION_TYPE_CLOSE, extras);
        final TestBounds testBounds = getTestBounds();
        final Rect transitionBounds = testBounds.transitionBounds;
        final int xIndex = transitionBounds.left
                + (transitionBounds.right - transitionBounds.left) / 4;
        getTestBuilder().setClass(EdgeExtensionActivity.class).setExtras(extras)
                .setTestFunction(createAssertColorChangeXIndex(xIndex, testBounds))
                .run();

        mWmState.waitForAppTransitionIdleOnDisplay(DEFAULT_DISPLAY);
        mContext.sendBroadcast(new Intent(ACTION_FINISH));
        runAndAssertActivityTransition(createAssertColorChangeXIndex(xIndex, testBounds));
    }

    /**
     * Borrow the test from testRightEdgeExtensionWorksDuringActivityTransition, mainly test for
     * API Activity#clearOverrideActivityTransition.
     */
    @Test
    public void testClearOverrideActivityTransition() {
        final Bundle extras = new Bundle();
        extras.putInt(DIRECTION_KEY, RIGHT);
        addTestMethodToExtras(TEST_METHOD_OVERRIDE_ACTIVITY_TRANSITION,
                TRANSITION_TYPE_OPEN | TRANSITION_TYPE_CLOSE, extras);
        final TestBounds testBounds = getTestBounds();
        final LauncherActivity launcherActivity = startLauncherActivity();
        launcherActivity.startActivity(null, EdgeExtensionActivity.class, extras);

        mWmState.waitForAppTransitionIdleOnDisplay(DEFAULT_DISPLAY);
        final Intent update = new Intent(ACTION_UPDATE);
        update.putExtra(TEST_METHOD_KEY, TEST_METHOD_CLEAR_OVERRIDE_ACTIVITY_TRANSITION);
        update.putExtra(TRANSITION_TYPE_KEY, TRANSITION_TYPE_OPEN | TRANSITION_TYPE_CLOSE);
        mContext.sendBroadcast(update);
        mContext.sendBroadcast(new Intent(ACTION_FINISH));
        runAndAssertActivityTransition(
                createAssertAppRegionOfScreenIsColor(Color.CYAN, testBounds));
    }

    /**
     * Checks that when an activity transition with a bottom edge extension is run that the
     * animating activity is extended on the bottom side by clamping the edge pixels of the
     * activity.
     *
     * The test runs an activity transition where the animating activities are Y scaled to 50%,
     * positioned of the top of the screen, and edge extended on the bottom. Because the
     * animating activities are half red half blue (split at the middle of the X axis of the
     * activity). We expect first 50% pixel columns of the screen to be red (the top half from the
     * activity and the bottom half from gthe extensions) and the remaining 50% columns after that
     * to be blue (the top half from the activity and the bottom half from the extension).
     *
     * @see R.anim.edge_extension_bottom for the transition applied.
     */
    @Test
    public void testBottomEdgeExtensionWorksDuringActivityTransition() {
        final Bundle extras = new Bundle();
        extras.putInt(DIRECTION_KEY, BOTTOM);
        addTestMethodToExtras(TEST_METHOD_OVERRIDE_PENDING_TRANSITION, 0, extras);
        final TestBounds testBounds = getTestBounds();
        final Rect transitionBounds = testBounds.transitionBounds;
        final int xIndex = (transitionBounds.left + transitionBounds.right) / 2;
        getTestBuilder().setClass(EdgeExtensionActivity.class).setExtras(extras)
                .setTestFunction(createAssertColorChangeXIndex(xIndex, testBounds))
                .run();
    }

    private TestBuilder getTestBuilder() {
        return new TestBuilder();
    }

    private class TestBuilder {
        private ActivityOptions mActivityOptions = ActivityOptions.makeBasic();
        private Bundle mExtras = Bundle.EMPTY;
        private Class<?> mKlass;
        private Function<Bitmap, AssertionResult> mTestFunction;

        public TestBuilder setActivityOptions(ActivityOptions activityOptions) {
            this.mActivityOptions = activityOptions;
            return this;
        }

        public TestBuilder setExtras(Bundle extra) {
            this.mExtras = extra;
            return this;
        }

        public TestBuilder setClass(Class<?> klass) {
            this.mKlass = klass;
            return this;
        }

        public TestBuilder setTestFunction(Function<Bitmap, AssertionResult> testFunction) {
            this.mTestFunction = testFunction;
            return this;
        }

        public void run() {
            final LauncherActivity launcherActivity = startLauncherActivity();
            launcherActivity.startActivity(mActivityOptions, mKlass, mExtras);
            runAndAssertActivityTransition(mTestFunction);
        }
    }

    private static class TestBounds {
        // The region which transition will apply.
        public Rect transitionBounds;
        // The region which should be stable for verify.
        public Rect testableBounds;
    }

    private TestBounds getTestBounds() {
        final LauncherActivity activity = startLauncherActivity();
        final TestBounds bounds = new TestBounds();
        bounds.transitionBounds = getTransitionAppBounds();
        bounds.testableBounds = activity.getActivityTestableRegion();
        launchHomeActivityNoWait();
        removeRootTasksWithActivityTypes(ALL_ACTIVITY_TYPE_BUT_HOME);
        mWmState.waitForAppTransitionIdleOnDisplay(DEFAULT_DISPLAY);
        return bounds;
    }

    private void runAndAssertActivityTransition(Function<Bitmap, AssertionResult> assertFunction) {
        // Busy wait until we are running the transition to capture the screenshot
        // Set a limited time to wait for transition start since there can still miss the state.
        assertTrue(Condition.waitFor(new Condition<>("Wait for transition running", () -> {
            mWmState.computeState();
            return WindowManagerState.APP_STATE_RUNNING.equals(
                    mWmState.getDisplay(DEFAULT_DISPLAY).getAppTransitionState());
        }).setRetryIntervalMs(15).setRetryLimit(200)));

        // Because of differences in timing between devices we try the given assert function
        // by taking multiple screenshots approximately to ensure we capture at least one screenshot
        // around the beginning of the activity transition.
        // The Timing issue exists around the beginning, so we use a sleep duration that increases
        // exponentially. The total amount of sleep duration is between 5 and 10 seconds, which
        // matches the most common wait time in CTS (2^0 + 2^1 + ... + 2^13 = about 8000).
        final ArrayList<AssertionResult> failedResults = new ArrayList<>();
        int sleepDurationMilliseconds = 1;
        for (int i = 0; i < 13; i++) {
            final AssertionResult result = assertFunction.apply(
                    mInstrumentation.getUiAutomation().takeScreenshot());
            if (!result.isFailure) {
                return;
            }
            failedResults.add(result);
            SystemClock.sleep(sleepDurationMilliseconds);
            sleepDurationMilliseconds *= 2;
        }

        fail("No screenshot of the activity transition passed the assertions ::\n"
                + String.join(",\n", failedResults.stream().map(Object::toString)
                .toArray(String[]::new)));

    }

    private Function<Bitmap, AssertionResult> createAssertAppRegionOfScreenIsColor(int color,
            TestBounds testBounds) {
        return (screen) -> getIsAppRegionOfScreenOfColorResult(screen, color, testBounds);
    }

    private static class ColorCheckResult extends AssertionResult {
        public final Point firstWrongPixel;
        public final Color expectedColor;
        public final Color actualColor;

        private ColorCheckResult(boolean isFailure, Point firstWrongPixel, Color expectedColor,
                Color actualColor) {
            super(isFailure);
            this.firstWrongPixel = firstWrongPixel;
            this.expectedColor = expectedColor;
            this.actualColor = actualColor;
        }

        private ColorCheckResult(Point firstWrongPixel, Color expectedColor, Color actualColor) {
            this(true, firstWrongPixel, expectedColor, actualColor);
        }

        @Override
        public String toString() {
            return "ColorCheckResult{"
                    + "isFailure=" + isFailure
                    + ", firstWrongPixel=" + firstWrongPixel
                    + ", expectedColor=" + expectedColor
                    + ", actualColor=" + actualColor
                    + '}';
        }
    }

    private AssertionResult getIsAppRegionOfScreenOfColorResult(Bitmap screen, int color,
            TestBounds testBounds) {
        final int scaleWidth = testBounds.testableBounds.width() / 5;
        final int[] xSample = {
                (scaleWidth + testBounds.testableBounds.left),
                (scaleWidth * 2 + testBounds.testableBounds.left),
                (scaleWidth * 3 + testBounds.testableBounds.left),
                (scaleWidth * 4 + testBounds.testableBounds.left)};
        final int scaleHeight = testBounds.testableBounds.height() / 5;
        final int[] ySample = {
                (scaleHeight + testBounds.testableBounds.top),
                (scaleHeight * 2 + testBounds.testableBounds.top),
                (scaleHeight * 3 + testBounds.testableBounds.top),
                (scaleHeight * 4 + testBounds.testableBounds.top)};
        final Color verifyColor = Color.valueOf(color);
        for (int x = xSample.length - 1; x >= 0; --x) {
            final int sampleX = xSample[x];
            for (int y = ySample.length - 1; y >= 0; --y) {
                final int sampleY = ySample[y];
                final Color rawColor = screen.getColor(sampleX, sampleY);
                final Color sRgbColor;
                if (!rawColor.getColorSpace().equals(ColorSpace.get(ColorSpace.Named.SRGB))) {
                    // Conversion is required because the color space of the screenshot may be in
                    // the DCI-P3 color space or some other color space and we want to compare the
                    // color against once in the SRGB color space, so we must convert the color back
                    // to the SRGB color space.
                    sRgbColor = screen.getColor(sampleX, sampleY)
                            .convert(ColorSpace.get(ColorSpace.Named.SRGB));
                } else {
                    sRgbColor = rawColor;
                }
                if (arrayEquals(new float[]{
                                verifyColor.red(), verifyColor.green(), verifyColor.blue()},
                        new float[]{sRgbColor.red(), sRgbColor.green(), sRgbColor.blue()})) {
                    return new ColorCheckResult(new Point(sampleX, sampleY), verifyColor,
                            sRgbColor);
                }
            }
        }

        return AssertionResult.SUCCESS;
    }

    private boolean arrayEquals(float[] array1, float[] array2) {
        return arrayEquals(array1, array2, COLOR_VALUE_VARIANCE_TOLERANCE);
    }

    private boolean arrayEquals(float[] array1, float[] array2, float varianceTolerance) {
        if (array1.length != array2.length) {
            return true;
        }
        for (int i = 0; i < array1.length; i++) {
            if (Math.abs(array1[i] - array2[i]) > varianceTolerance) {
                return true;
            }
        }
        return false;
    }

    private Rect getTransitionAppBounds() {
        getWmState().computeState();
        final WindowManagerState.Activity activity = getWmState().getActivity(
                ComponentName.unflattenFromString(getWmState().getFocusedActivity()));
        return activity.getBounds();
    }

    private static class AssertionResult {
        public final boolean isFailure;
        public final String message;

        private AssertionResult(boolean isFailure, String message) {
            this.isFailure = isFailure;
            this.message = message;
        }

        private AssertionResult(boolean isFailure) {
            this(isFailure, null);
        }

        @Override
        public String toString() {
            return "AssertionResult{"
                    + "isFailure=" + isFailure
                    + ", message='" + message + '\''
                    + '}';
        }

        private static final AssertionResult SUCCESS = new AssertionResult(false);
        private static final AssertionResult FAILURE = new AssertionResult(true);
    }

    // The activity we are extending is a half red, half blue.
    // We are scaling the activity in the animation so if the extension doesn't work we should
    // have a blue, then red, then black section, and if it does work we should see on a blue,
    // followed by an extended red section.
    private Function<Bitmap, AssertionResult> createAssertColorChangeXIndex(int xIndex,
                                                                            TestBounds testBounds) {
        return (screen) -> assertColorChangeXIndex(
                screen, xIndex, testBounds, Color.BLUE, Color.RED);
    }

    private AssertionResult assertColorChangeXIndex(Bitmap screen, int splitX,
            TestBounds testBounds, int lessXColor, int largeXColor) {
        final int[] xSample = {
                (splitX - testBounds.testableBounds.left) / 2 + testBounds.testableBounds.left,
                splitX - 3,
                splitX + 3,
                (testBounds.testableBounds.right - splitX) / 2 + splitX};
        final int scaleHeight = testBounds.testableBounds.height() / 5;
        final int[] ySample = {
                (scaleHeight + testBounds.testableBounds.top),
                (scaleHeight * 2 + testBounds.testableBounds.top),
                (scaleHeight * 3 + testBounds.testableBounds.top),
                (scaleHeight * 4 + testBounds.testableBounds.top)};
        final Color lessXColorC = Color.valueOf(lessXColor);
        final Color largeXColorC = Color.valueOf(largeXColor);
        for (int xIndex = xSample.length - 1; xIndex >= 0; --xIndex) {
            final int sampleX = xSample[xIndex];
            for (int yIndex = ySample.length - 1; yIndex >= 0; --yIndex) {
                final int sampleY = ySample[yIndex];
                final Color expectedColor;
                if (sampleX < splitX) {
                    expectedColor = lessXColorC;
                } else {
                    expectedColor = largeXColorC;
                }
                final Color rawColor = screen.getColor(sampleX, sampleY);
                final Color sRgbColor;
                if (!rawColor.getColorSpace().equals(ColorSpace.get(ColorSpace.Named.SRGB))) {
                    // Conversion is required because the color space of the screenshot may be in
                    // the DCI-P3 color space or some other color space and we want to compare the
                    // color against once in the SRGB color space, so we must convert the color back
                    // to the SRGB color space.
                    sRgbColor = screen.getColor(sampleX, sampleY)
                            .convert(ColorSpace.get(ColorSpace.Named.SRGB));
                } else {
                    sRgbColor = rawColor;
                }
                if (arrayEquals(new float[]{
                                expectedColor.red(), expectedColor.green(), expectedColor.blue()},
                        new float[]{sRgbColor.red(), sRgbColor.green(), sRgbColor.blue()})) {
                    return new ColorCheckResult(new Point(sampleX, sampleY), expectedColor,
                            sRgbColor);
                }
            }
        }

        return AssertionResult.SUCCESS;
    }

    private static void addTestMethodToExtras(int apiType, int transitionType, Bundle extras) {
        extras.putInt(TEST_METHOD_KEY, apiType);
        extras.putInt(TRANSITION_TYPE_KEY, transitionType);
    }

    public static class LauncherActivity extends Activity {

        private WindowInsets mInsets;

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            // Ensure the activity is edge-to-edge
            // In tests we rely on the activity's content filling the entire window
            getWindow().setDecorFitsSystemWindows(false);

            View view = new View(this);
            view.setLayoutParams(new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));
            view.setOnApplyWindowInsetsListener((v, insets) -> mInsets = insets);
            view.setBackgroundColor(Color.CYAN);
            setContentView(view);
        }

        private Rect getActivityTestableRegion() {
            final Rect activityBounds = getWindowManager().getCurrentWindowMetrics().getBounds();
            final Insets insets = mInsets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout());
            activityBounds.inset(insets);
            cropRoundedCornersRegions(activityBounds);
            return new Rect(activityBounds);
        }

        private void cropRoundedCornersRegions(Rect activityBounds) {
            RoundedCorner topLeftCorner = mInsets.getRoundedCorner(POSITION_TOP_LEFT);
            RoundedCorner bottomRightCorner = mInsets.getRoundedCorner(POSITION_BOTTOM_RIGHT);

            final Rect innerRectangle = new Rect(activityBounds);
            // There will be no testable region if the radius of rounded corner equals to the
            // center of screen on a circle shape display. So instead of ignore rounded corner
            // areas, consider the internal rectangle of rounded corner region as testable region.
            // Where radius * cosine(45) == the projected length to x and y direction.
            final double projectionConst = Math.cos(45);
            if (topLeftCorner != null) {
                final Point center = topLeftCorner.getCenter();
                final int radius = topLeftCorner.getRadius();
                final double projectLength = Math.ceil(radius * projectionConst);
                innerRectangle.left = center.x - (int) projectLength;
                innerRectangle.top = center.y - (int) projectLength;
            }
            if (bottomRightCorner != null) {
                final Point center = bottomRightCorner.getCenter();
                final int radius = bottomRightCorner.getRadius();
                final double projectLength = Math.ceil(radius * projectionConst);
                innerRectangle.right = center.x + (int) projectLength;
                innerRectangle.bottom = center.y + (int) projectLength;
            }
            activityBounds.setIntersect(activityBounds, innerRectangle);
        }

        public void startActivity(ActivityOptions activityOptions, Class<?> klass) {
            startActivity(activityOptions, klass, new Bundle());
        }

        public void startActivity(ActivityOptions activityOptions, Class<?> klass,
                Bundle extras) {
            final Intent i = new Intent(this, klass);
            i.putExtras(extras);
            startActivity(i, activityOptions != null ? activityOptions.toBundle() : null);
        }
    }

    public static class TransitionActivity extends Activity { }

    public static class CustomBackgroundTransitionActivity extends Activity {
        static final String ENTER_ANIM_KEY = "enterAnim";
        static final String EXIT_ANIM_KEY = "enterAnim";
        static final String BACKGROUND_COLOR_KEY = "backgroundColor";

        private boolean mPendingOverrideTransition;
        private int mPendingEnterRes;
        private int mPendingExitRes;
        private int mBackgroundColor;

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            registerReceiver(mReceiver, new IntentFilter(ACTION_FINISH), Context.RECEIVER_EXPORTED);
            processIntent();
        }
        private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_FINISH.equals(intent.getAction())) {
                    finish();
                }
            }
        };

        private void processIntent() {
            Bundle extras = getIntent().getExtras();
            int testAPI = extras.getInt(TEST_METHOD_KEY);
            int enterAnim = extras.getInt(ENTER_ANIM_KEY);
            int exitAnim = extras.getInt(EXIT_ANIM_KEY);
            int backgroundColor = extras.getInt(BACKGROUND_COLOR_KEY);
            int transitionType = extras.getInt(TRANSITION_TYPE_KEY);
            if (testAPI == TEST_METHOD_OVERRIDE_PENDING_TRANSITION) {
                mPendingOverrideTransition = true;
                mPendingEnterRes = enterAnim;
                mPendingExitRes = exitAnim;
                mBackgroundColor = backgroundColor;
            } else if (testAPI == TEST_METHOD_OVERRIDE_ACTIVITY_TRANSITION) {
                if ((transitionType & TRANSITION_TYPE_OPEN) != 0) {
                    if (backgroundColor != 0) {
                        overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, enterAnim, exitAnim,
                                backgroundColor /* backgroundColor */);
                    } else {
                        overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, enterAnim, exitAnim);
                    }
                }
                if ((transitionType & TRANSITION_TYPE_CLOSE) != 0) {
                    if (backgroundColor != 0) {
                        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, enterAnim, exitAnim,
                                backgroundColor /* backgroundColor */);
                    } else {
                        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, enterAnim, exitAnim);
                    }
                }
            }
        }

        @Override
        protected void onResume() {
            super.onResume();

            if (mPendingOverrideTransition) {
                overridePendingTransition(mPendingEnterRes, mPendingExitRes, mBackgroundColor);
                mPendingOverrideTransition = false;
            }
        }

        @Override
        protected void onDestroy() {
            super.onDestroy();
            unregisterReceiver(mReceiver);
        }
    }

    public static class TransitionActivityWithWhiteBackground extends Activity { }

    public static class EdgeExtensionActivity extends Activity {
        static final String DIRECTION_KEY = "direction";
        static final int LEFT = 0;
        static final int TOP = 1;
        static final int RIGHT = 2;
        static final int BOTTOM = 3;

        private boolean mPendingOverrideTransition;
        private int mPendingEnterRes;
        private int mPendingExitRes;

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.vertical_color_split);

            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            // Ensure the activity is edge-to-edge
            // In tests we rely on the activity's content filling the entire window
            getWindow().setDecorFitsSystemWindows(false);

            // Hide anything that the decor view might add to the window to avoid extending that
            getWindow().getInsetsController()
                    .hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            final IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(ACTION_FINISH);
            intentFilter.addAction(ACTION_UPDATE);
            registerReceiver(mReceiver, intentFilter, Context.RECEIVER_EXPORTED);
            processIntent();
        }

        private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_UPDATE.equals(intent.getAction())) {
                    final int clearApi = intent.getIntExtra(TEST_METHOD_KEY, 0);
                    if (clearApi == TEST_METHOD_CLEAR_OVERRIDE_ACTIVITY_TRANSITION) {
                        final int clearType = intent.getIntExtra(TRANSITION_TYPE_KEY, 0);
                        if ((clearType & TRANSITION_TYPE_OPEN) != 0) {
                            clearOverrideActivityTransition(OVERRIDE_TRANSITION_OPEN);
                        }
                        if ((clearType & TRANSITION_TYPE_CLOSE) != 0) {
                            clearOverrideActivityTransition(OVERRIDE_TRANSITION_CLOSE);
                        }
                    }
                }
                if (ACTION_FINISH.equals(intent.getAction())) {
                    finish();
                }
            }
        };

        @Override
        protected void onDestroy() {
            super.onDestroy();
            unregisterReceiver(mReceiver);
        }

        @Override
        protected void onResume() {
            super.onResume();

            if (mPendingOverrideTransition) {
                overridePendingTransition(mPendingEnterRes, mPendingExitRes);
                mPendingOverrideTransition = false;
            }
        }

        private void processIntent() {
            Bundle extras = getIntent().getExtras();
            int direction = extras.getInt(DIRECTION_KEY);
            int testAPI = extras.getInt(TEST_METHOD_KEY);
            int transitionType = extras.getInt(TRANSITION_TYPE_KEY);
            int testAnim = 0;
            switch (direction) {
                case LEFT:
                    testAnim = R.anim.edge_extension_left;
                    break;
                case TOP:
                    testAnim = R.anim.edge_extension_top;
                    break;
                case RIGHT:
                    testAnim = R.anim.edge_extension_right;
                    break;
                case BOTTOM:
                    testAnim = R.anim.edge_extension_bottom;
                    break;
            }
            if (testAPI == TEST_METHOD_OVERRIDE_PENDING_TRANSITION) {
                mPendingOverrideTransition = true;
                mPendingEnterRes = testAnim;
                mPendingExitRes = R.anim.alpha_0;
            } else if (testAPI == TEST_METHOD_OVERRIDE_ACTIVITY_TRANSITION) {
                if ((transitionType & TRANSITION_TYPE_OPEN) != 0) {
                    overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, testAnim, R.anim.alpha_0,
                            0 /* backgroundColor */);
                }
                if ((transitionType & TRANSITION_TYPE_CLOSE) != 0) {
                    overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.alpha_0, testAnim,
                            0 /* backgroundColor */);
                }
            }
        }
    }

    public static class CustomWindowAnimationActivity extends Activity { }
}
