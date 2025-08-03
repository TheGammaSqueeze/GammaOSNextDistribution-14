/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.accessibilityservice.cts;

import static android.accessibilityservice.cts.utils.AsyncUtils.await;
import static android.accessibilityservice.cts.utils.GestureUtils.IS_ACTION_DOWN;
import static android.accessibilityservice.cts.utils.GestureUtils.IS_ACTION_UP;
import static android.accessibilityservice.cts.utils.GestureUtils.add;
import static android.accessibilityservice.cts.utils.GestureUtils.click;
import static android.accessibilityservice.cts.utils.GestureUtils.dispatchGesture;
import static android.accessibilityservice.cts.utils.GestureUtils.doubleTap;
import static android.accessibilityservice.cts.utils.GestureUtils.doubleTapAndHold;
import static android.accessibilityservice.cts.utils.GestureUtils.isRawAtPoint;
import static android.accessibilityservice.cts.utils.GestureUtils.multiTap;
import static android.accessibilityservice.cts.utils.GestureUtils.secondFingerMultiTap;
import static android.accessibilityservice.cts.utils.GestureUtils.swipe;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_HOVER_ENTER;
import static android.view.MotionEvent.ACTION_HOVER_EXIT;
import static android.view.MotionEvent.ACTION_HOVER_MOVE;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_POINTER_DOWN;
import static android.view.MotionEvent.ACTION_POINTER_UP;
import static android.view.MotionEvent.ACTION_UP;
import static android.view.accessibility.AccessibilityEvent.TYPE_GESTURE_DETECTION_END;
import static android.view.accessibility.AccessibilityEvent.TYPE_GESTURE_DETECTION_START;
import static android.view.accessibility.AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_END;
import static android.view.accessibility.AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START;
import static android.view.accessibility.AccessibilityEvent.TYPE_TOUCH_INTERACTION_END;
import static android.view.accessibility.AccessibilityEvent.TYPE_TOUCH_INTERACTION_START;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED;

import static com.google.common.truth.TruthJUnit.assume;

import static org.hamcrest.CoreMatchers.both;
import static org.hamcrest.MatcherAssert.assertThat;

import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.accessibility.cts.common.InstrumentedAccessibilityService;
import android.accessibility.cts.common.InstrumentedAccessibilityServiceTestRule;
import android.accessibility.cts.common.ShellCommandBuilder;
import android.accessibilityservice.GestureDescription;
import android.accessibilityservice.GestureDescription.StrokeDescription;
import android.accessibilityservice.cts.AccessibilityGestureDispatchTest.GestureDispatchActivity;
import android.accessibilityservice.cts.utils.ActivityLaunchUtils;
import android.accessibilityservice.cts.utils.EventCapturingClickListener;
import android.accessibilityservice.cts.utils.EventCapturingLongClickListener;
import android.accessibilityservice.cts.utils.EventCapturingMotionEventListener;
import android.accessibilityservice.cts.utils.GestureUtils;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.pm.PackageManager;
import android.graphics.PointF;
import android.graphics.Region;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.test.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.CddTest;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * A set of tests for testing touch exploration. Each test dispatches a gesture and checks for the
 * appropriate hover and/or touch events followed by the appropriate accessibility events. Some
 * tests will then check for events from the view.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull
@CddTest(requirements = {"3.10/C-1-1,C-1-2"})
@Presubmit
public class TouchExplorerTest {
    // Constants
    private static final float GESTURE_LENGTH_MMS = 15.0f;
    private static final float MIN_SCREEN_WIDTH_MM = 40.0f;

    private static String sEnabledServices;
    private static Instrumentation sInstrumentation;
    private static UiAutomation sUiAutomation;

    private TouchExplorationStubAccessibilityService mService;
    private boolean mHasTouchscreen;
    private boolean mScreenBigEnough;
    private long mSwipeTimeMillis;
    private EventCapturingMotionEventListener mMotionEventListener =
            new EventCapturingMotionEventListener(false);
    private EventCapturingClickListener mClickListener = new EventCapturingClickListener();
    private EventCapturingLongClickListener mLongClickListener =
            new EventCapturingLongClickListener();

    private ActivityTestRule<GestureDispatchActivity> mActivityRule =
            new ActivityTestRule<>(GestureDispatchActivity.class, false, false);

    private InstrumentedAccessibilityServiceTestRule<TouchExplorationStubAccessibilityService>
            mServiceRule =
                    new InstrumentedAccessibilityServiceTestRule<>(
                            TouchExplorationStubAccessibilityService.class, false);

    private AccessibilityDumpOnFailureRule mDumpOnFailureRule =
            new AccessibilityDumpOnFailureRule();

    private GestureUtils.DumpOnFailureRule mGestureUtilsDumpOnFailureRule =
            new GestureUtils.DumpOnFailureRule();

    @Rule
    public final RuleChain mRuleChain =
            RuleChain.outerRule(mActivityRule)
                    .around(mServiceRule)
                    .around(mDumpOnFailureRule)
                    .around(mGestureUtilsDumpOnFailureRule);

    PointF mTapLocation; // Center of activity. Gestures all start from around this point.
    float mSwipeDistance;
    View mView;

    @BeforeClass
    public static void oneTimeSetup() {
        sInstrumentation = InstrumentationRegistry.getInstrumentation();
        // Save enabled accessibility services before disabling them so they can be re-enabled after
        // the test.
        sEnabledServices =
                Settings.Secure.getString(
                        sInstrumentation.getContext().getContentResolver(),
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        // Disable all services before enabling Accessibility service to prevent flakiness
        // that depends on which services are enabled.
        InstrumentedAccessibilityService.disableAllServices();
        sUiAutomation =
                sInstrumentation.getUiAutomation(
                        UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        GestureUtils.randomize();
    }

    @AfterClass
    public static void postTestTearDown() {
        ShellCommandBuilder.create(sInstrumentation)
                .putSecureSetting(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, sEnabledServices)
                .run();
        sUiAutomation.destroy();
    }

    @Before
    public void setUp() throws Exception {
        ActivityLaunchUtils.homeScreenOrBust(sInstrumentation.getContext(), sUiAutomation);

        mActivityRule.launchActivity(null);
        PackageManager pm = sInstrumentation.getContext().getPackageManager();
        mHasTouchscreen =
                pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
                        || pm.hasSystemFeature(PackageManager.FEATURE_FAKETOUCH);
        // Find window size, check that it is big enough for gestures.
        // Gestures will start in the center of the window, so we need enough horiz/vert space.
        mService = mServiceRule.enableService();
        mView = mActivityRule.getActivity().findViewById(R.id.full_screen_text_view);
        WindowManager windowManager =
                sInstrumentation.getContext().getSystemService(WindowManager.class);
        final DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        mScreenBigEnough =
                mView.getWidth()
                        > TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_MM, MIN_SCREEN_WIDTH_MM, metrics);
        mView.setOnHoverListener(mMotionEventListener);
        mView.setOnTouchListener(mMotionEventListener);
        assume().that(mHasTouchscreen).isTrue();
        assume().that(mScreenBigEnough).isTrue();
        sInstrumentation.runOnMainSync(
                () -> {
                    int[] viewLocation = new int[2];
                    mView = mActivityRule.getActivity().findViewById(R.id.full_screen_text_view);
                    final int midX = mView.getWidth() / 2;
                    final int midY = mView.getHeight() / 2;
                    mView.getLocationOnScreen(viewLocation);
                    mTapLocation = new PointF(viewLocation[0] + midX, viewLocation[1] + midY);
                    mSwipeDistance =
                            TypedValue.applyDimension(
                                    TypedValue.COMPLEX_UNIT_MM, GESTURE_LENGTH_MMS, metrics);
                    // This must be slower than 10mm per 150ms to be detected as touch exploration.
                    final double swipeDistanceMm = mSwipeDistance / metrics.xdpi * 25.4;
                    mSwipeTimeMillis = (long) swipeDistanceMm * 20;

                    mView.setOnClickListener(mClickListener);
                    mView.setOnLongClickListener(mLongClickListener);
                });
    }

    /** Test a slow swipe which should initiate touch exploration. */
    @Test
    @AppModeFull
    public void testSlowSwipe_initiatesTouchExploration() {
        PointF endPoint = add(mTapLocation, mSwipeDistance, 0);
        dispatch(swipe(mTapLocation, add(mTapLocation, mSwipeDistance, 0), mSwipeTimeMillis));
        mMotionEventListener.assertPropagated(
                ACTION_HOVER_ENTER, ACTION_HOVER_MOVE, ACTION_HOVER_EXIT);
        mService.assertPropagated(
                TYPE_TOUCH_INTERACTION_START,
                TYPE_TOUCH_EXPLORATION_GESTURE_START,
                TYPE_TOUCH_EXPLORATION_GESTURE_END,
                TYPE_TOUCH_INTERACTION_END);
    }

    /** Test a fast swipe which should not initiate touch exploration. */
    @Test
    @AppModeFull
    public void testFastSwipe_doesNotInitiateTouchExploration() {
        PointF endPoint = add(mTapLocation, mSwipeDistance, 0);
        dispatch(swipe(mTapLocation, endPoint));
        mMotionEventListener.assertNonePropagated();
        mService.assertPropagated(
                TYPE_TOUCH_INTERACTION_START,
                TYPE_GESTURE_DETECTION_START,
                TYPE_GESTURE_DETECTION_END,
                TYPE_TOUCH_INTERACTION_END);
        List<MotionEvent> motionEvents = getMotionEventsForLastGesture();
        assertThat(motionEvents.get(0), both(IS_ACTION_DOWN).and(isRawAtPoint(mTapLocation, 1.0f)));
        assertThat(
                motionEvents.get(motionEvents.size() - 1),
                both(IS_ACTION_UP).and(isRawAtPoint(endPoint, 1.0f)));
    }

    /**
     * Test a two finger drag. TouchExplorer would perform a drag gesture when two fingers moving in
     * the same direction.
     */
    @Test
    @AppModeFull
    public void testTwoFingerDrag_dispatchesEventsBetweenFingers() {
        // A two point moving that are in the same direction can perform a drag gesture by
        // TouchExplorer while one point moving can not perform a drag gesture. We use two swipes
        // to emulate a two finger drag gesture.
        final int twoFingerOffset = (int) mSwipeDistance;
        final PointF dragStart = mTapLocation;
        final PointF dragEnd = add(dragStart, 0, mSwipeDistance);
        final PointF finger1Start = add(dragStart, twoFingerOffset, 0);
        final PointF finger1End = add(finger1Start, 0, mSwipeDistance);
        final PointF finger2Start = add(dragStart, -twoFingerOffset, 0);
        final PointF finger2End = add(finger2Start, 0, mSwipeDistance);
        dispatch(
                swipe(finger1Start, finger1End, mSwipeTimeMillis),
                swipe(finger2Start, finger2End, mSwipeTimeMillis));
        mMotionEventListener.assertPropagated(ACTION_DOWN, ACTION_MOVE, ACTION_UP);
    }

    /** Test a basic single tap which should initiate touch exploration. */
    @Test
    @AppModeFull
    public void testSingleTap_initiatesTouchExploration() {
        dispatch(click(mTapLocation));
        mMotionEventListener.assertPropagated(ACTION_HOVER_ENTER, ACTION_HOVER_EXIT);
        mService.assertPropagated(
                TYPE_TOUCH_INTERACTION_START,
                TYPE_TOUCH_EXPLORATION_GESTURE_START,
                TYPE_TOUCH_EXPLORATION_GESTURE_END,
                TYPE_TOUCH_INTERACTION_END);
    }

    /**
     * Test the case where we execute a "sloppy" double tap, meaning that the second tap isn't
     * exactly in the same location as the first but still within tolerances. It should behave the
     * same as a standard double tap. Note that this test does not request that double tap be
     * dispatched to the accessibility service, meaning that it will be handled by the framework and
     * the view will be clicked.
     */
    @Test
    @AppModeFull
    public void testSloppyDoubleTapAccessibilityFocus_performsClick() {
        syncAccessibilityFocusToInputFocus();
        int slop = ViewConfiguration.get(sInstrumentation.getContext()).getScaledDoubleTapSlop();
        dispatch(multiTap(mTapLocation, 2, slop));
        // The click should not be delivered via touch events in this case.
        mMotionEventListener.assertNonePropagated();
        mService.assertPropagated(
                TYPE_VIEW_ACCESSIBILITY_FOCUSED,
                TYPE_TOUCH_INTERACTION_START,
                TYPE_TOUCH_INTERACTION_END);
        mClickListener.assertClicked(mView);
    }

    /**
     * Test the case where we want to click on the item that has accessibility focus by using
     * AccessibilityNodeInfo.performAction. Note that this test does not request that double tap be
     * dispatched to the accessibility service, meaning that it will be handled by the framework and
     * the view will be clicked.
     */
    @Test
    @AppModeFull
    public void testDoubleTapAccessibilityFocus_performsClick() {
        syncAccessibilityFocusToInputFocus();
        dispatch(doubleTap(mTapLocation));
        // The click should not be delivered via touch events in this case.
        mMotionEventListener.assertNonePropagated();
        mService.assertPropagated(
                TYPE_VIEW_ACCESSIBILITY_FOCUSED,
                TYPE_TOUCH_INTERACTION_START,
                TYPE_TOUCH_INTERACTION_END);
        mClickListener.assertClicked(mView);
    }

    /**
     * Test the case where we double tap but there is no accessibility focus. Nothing should happen.
     */
    @Test
    @AppModeFull
    public void testDoubleTapNoFocus_doesNotPerformClick() {
        dispatch(doubleTap(mTapLocation));
        mMotionEventListener.assertNonePropagated();
        mService.assertPropagated(TYPE_TOUCH_INTERACTION_START, TYPE_TOUCH_INTERACTION_END);
        mService.clearEvents();
        mClickListener.assertNoneClicked();
        List<MotionEvent> motionEvents = getMotionEventsForLastGesture();
        assertThat(motionEvents.get(0), both(IS_ACTION_DOWN).and(isRawAtPoint(mTapLocation, 1.0f)));
        assertThat(motionEvents.get(1), both(IS_ACTION_UP).and(isRawAtPoint(mTapLocation, 1.0f)));
        assertThat(motionEvents.get(2), both(IS_ACTION_DOWN).and(isRawAtPoint(mTapLocation, 1.0f)));
        assertThat(motionEvents.get(3), both(IS_ACTION_UP).and(isRawAtPoint(mTapLocation, 1.0f)));
    }

    /**
     * Test the case where we double tap and hold but there is no accessibility focus. Nothing
     * should happen.
     */
    @Test
    @AppModeFull
    public void testDoubleTapAndHoldNoFocus_doesNotPerformLongClick() {
        dispatch(doubleTap(mTapLocation));
        mMotionEventListener.assertNonePropagated();
        mService.assertPropagated(TYPE_TOUCH_INTERACTION_START, TYPE_TOUCH_INTERACTION_END);
        mService.clearEvents();
        mLongClickListener.assertNoneLongClicked();
    }

    /**
     * Test the case where we want to double tap using a second finger while the first finger is
     * touch exploring.
     */
    @Test
    @AppModeFull
    public void testSecondFingerDoubleTapTouchExploring_performsClick() {

        syncAccessibilityFocusToInputFocus();
        // hold the first finger for long enough to trigger touch exploration before double-tapping.
        // Touch exploration is triggered after the double tap timeout.
        dispatch(
                secondFingerMultiTap(
                        mTapLocation,
                        add(mTapLocation, mSwipeDistance, 0),
                        2,
                        ViewConfiguration.getDoubleTapTimeout() + 50));
        mMotionEventListener.assertPropagated(ACTION_HOVER_ENTER, ACTION_HOVER_EXIT);
        mService.assertPropagated(
                TYPE_VIEW_ACCESSIBILITY_FOCUSED,
                TYPE_TOUCH_INTERACTION_START,
                TYPE_TOUCH_EXPLORATION_GESTURE_START,
                TYPE_TOUCH_EXPLORATION_GESTURE_END,
                TYPE_TOUCH_INTERACTION_END);
        mClickListener.assertClicked(mView);
    }

    /**
     * Test the case where we double tap and no item has accessibility focus, so TouchExplorer sends
     * touch events to the last touch-explored coordinates to simulate a click.
     */
    @Test
    @AppModeFull
    public void testDoubleTapNoAccessibilityFocus_sendsTouchEvents() {

        // Do a single tap so there is a valid last touch-explored location.
        dispatch(click(mTapLocation));
        mMotionEventListener.assertPropagated(ACTION_HOVER_ENTER, ACTION_HOVER_EXIT);
        // We don't really care about these events but we need to make sure all the events we want
        // to clear have arrived before we clear them.
        mService.assertPropagated(
                TYPE_TOUCH_INTERACTION_START,
                TYPE_TOUCH_EXPLORATION_GESTURE_START,
                TYPE_TOUCH_EXPLORATION_GESTURE_END,
                TYPE_TOUCH_INTERACTION_END);
        mService.clearEvents();
        dispatch(doubleTap(mTapLocation));
        // The click gets delivered as a series of touch events.
        mMotionEventListener.assertPropagated(ACTION_DOWN, ACTION_UP);
        mService.assertPropagated(TYPE_TOUCH_INTERACTION_START, TYPE_TOUCH_INTERACTION_END);
        mClickListener.assertClicked(mView);
    }

    /**
     * Test the case where we double tap and hold and no item has accessibility focus, so
     * TouchExplorer sends touch events to the last touch-explored coordinates to simulate a long
     * click.
     */
    @Test
    @AppModeFull
    public void testDoubleTapAndHoldNoAccessibilityFocus_sendsTouchEvents() {
        // Do a single tap so there is a valid last touch-explored location.
        dispatch(click(mTapLocation));
        mMotionEventListener.assertPropagated(ACTION_HOVER_ENTER, ACTION_HOVER_EXIT);
        // We don't really care about these events but we need to make sure all the events we want
        // to clear have arrived before we clear them.
        mService.assertPropagated(
                TYPE_TOUCH_INTERACTION_START,
                TYPE_TOUCH_EXPLORATION_GESTURE_START,
                TYPE_TOUCH_EXPLORATION_GESTURE_END,
                TYPE_TOUCH_INTERACTION_END);
        mService.clearEvents();
        dispatch(doubleTapAndHold(mTapLocation));
        // The click gets delivered as a series of touch events.
        mMotionEventListener.assertPropagated(ACTION_DOWN, ACTION_UP);
        mService.assertPropagated(TYPE_TOUCH_INTERACTION_START, TYPE_TOUCH_INTERACTION_END);
        mLongClickListener.assertLongClicked(mView);
    }

    /**
     * Test the case where we want to double tap using a second finger without triggering touch
     * exploration.
     */
    @Test
    @AppModeFull
    public void testSecondFingerDoubleTapNotTouchExploring_performsClick() {
        syncAccessibilityFocusToInputFocus();
        // Hold the first finger for less than the double tap timeout which will not trigger touch
        // exploration.
        // Touch exploration is triggered after the double tap timeout.
        dispatch(
                secondFingerMultiTap(
                        mTapLocation,
                        add(mTapLocation, mSwipeDistance, 0),
                        2,
                        ViewConfiguration.getDoubleTapTimeout() / 3));
        mMotionEventListener.assertNonePropagated();
        mService.assertPropagated(
                TYPE_VIEW_ACCESSIBILITY_FOCUSED,
                TYPE_TOUCH_INTERACTION_START,
                TYPE_TOUCH_INTERACTION_END);
        mClickListener.assertClicked(mView);
    }

    /**
     * This method tests a three-finger swipe. The gesture will be delegated to the view as-is. This
     * is distinct from dragging, where two fingers are delegated to the view as one finger. Note
     * that because multi-finger gestures are disabled this gesture will not be handled by the
     * gesture detector.
     */
    @Test
    @AppModeFull
    public void testThreeFingerMovement_shouldDelegate() {
        // Move three fingers down the screen slowly.
        PointF finger1Start = add(mTapLocation, -mSwipeDistance, 0);
        PointF finger1End = add(mTapLocation, -mSwipeDistance, mSwipeDistance);
        PointF finger2Start = mTapLocation;
        PointF finger2End = add(mTapLocation, 0, mSwipeDistance);
        PointF finger3Start = add(mTapLocation, mSwipeDistance, 0);
        PointF finger3End = add(mTapLocation, mSwipeDistance, mSwipeDistance);
        StrokeDescription swipe1 = swipe(finger1Start, finger1End, mSwipeTimeMillis);
        StrokeDescription swipe2 = swipe(finger2Start, finger2End, mSwipeTimeMillis);
        StrokeDescription swipe3 = swipe(finger3Start, finger3End, mSwipeTimeMillis);
        dispatch(swipe1, swipe2, swipe3);
        mMotionEventListener.assertPropagated(
                ACTION_DOWN,
                ACTION_POINTER_DOWN,
                ACTION_POINTER_DOWN,
                ACTION_MOVE,
                ACTION_POINTER_UP,
                ACTION_POINTER_UP,
                ACTION_UP);
    }

    /**
     * This method tests the case where two fingers are moving independently. The gesture will be
     * delegated to the view as-is. This is distinct from dragging, where two fingers are delegated
     * to the view as one finger.
     */
    @Test
    @AppModeFull
    public void testTwoFingersMovingIndependently_shouldDelegate() {
        // Move two fingers towards eacher slowly.
        PointF finger1Start = add(mTapLocation, -mSwipeDistance, 0);
        PointF finger1End = add(mTapLocation, -10, 0);
        StrokeDescription swipe1 = swipe(finger1Start, finger1End, mSwipeTimeMillis);
        PointF finger2Start = add(mTapLocation, mSwipeDistance, 0);
        PointF finger2End = add(mTapLocation, 10, 0);
        StrokeDescription swipe2 = swipe(finger2Start, finger2End, mSwipeTimeMillis);
        dispatch(swipe1, swipe2);
        mMotionEventListener.assertPropagated(
                ACTION_DOWN, ACTION_POINTER_DOWN, ACTION_MOVE, ACTION_POINTER_UP, ACTION_UP);
    }

    /**
     * Test the gesture detection passthrough by performing a fast swipe in the passthrough region.
     * It should bypass the gesture detector entirely.
     */
    @Test
    @AppModeFull
    public void testGestureDetectionPassthrough_initiatesTouchExploration() {
        setRightSideOfActivityWindowGestureDetectionPassthrough();
        // Swipe in the passthrough region. This should generate hover events.
        dispatch(swipe(mTapLocation, add(mTapLocation, mSwipeDistance, 0)));
        mMotionEventListener.assertPropagated(
                ACTION_HOVER_ENTER, ACTION_HOVER_MOVE, ACTION_HOVER_EXIT);
        mService.assertPropagated(
                TYPE_TOUCH_INTERACTION_START,
                TYPE_TOUCH_EXPLORATION_GESTURE_START,
                TYPE_TOUCH_EXPLORATION_GESTURE_END,
                TYPE_TOUCH_INTERACTION_END);
        mService.clearEvents();
        // Swipe starting inside the passthrough region but ending outside of it. This should still
        // behave as a passthrough interaction.
        dispatch(swipe(mTapLocation, add(mTapLocation, -mSwipeDistance, 0)));
        mMotionEventListener.assertPropagated(
                ACTION_HOVER_ENTER, ACTION_HOVER_MOVE, ACTION_HOVER_EXIT);
        mService.assertPropagated(
                TYPE_TOUCH_INTERACTION_START,
                TYPE_TOUCH_EXPLORATION_GESTURE_START,
                TYPE_TOUCH_EXPLORATION_GESTURE_END,
                TYPE_TOUCH_INTERACTION_END);
        mService.clearEvents();
        // Swipe outside the passthrough region. This should not generate hover events.
        dispatch(swipe(add(mTapLocation, -1, 0), add(mTapLocation, -mSwipeDistance, 0)));
        mMotionEventListener.assertNonePropagated();
        mService.assertPropagated(
                TYPE_TOUCH_INTERACTION_START,
                TYPE_GESTURE_DETECTION_START,
                TYPE_GESTURE_DETECTION_END,
                TYPE_TOUCH_INTERACTION_END);
        mService.clearEvents();
        // There should be no touch events in this test.
        mMotionEventListener.assertNonePropagated();
        clearPassthroughRegions();
    }

    /**
     * Test the touch exploration passthrough by performing a fast swipe in the passthrough region.
     * It should generate touch events.
     */
    @Test
    @AppModeFull
    public void testTouchExplorationPassthrough_sendsTouchEvents() {
        setRightSideOfActivityWindowTouchExplorationPassthrough();
        // Swipe in the passthrough region. This should generate  touch events.
        dispatch(swipe(mTapLocation, add(mTapLocation, mSwipeDistance, 0)));
        mMotionEventListener.assertPropagated(ACTION_DOWN, ACTION_MOVE, ACTION_UP);
        // We still want accessibility events to tell us when the gesture starts and ends.
        mService.assertPropagated(TYPE_TOUCH_INTERACTION_START, TYPE_TOUCH_INTERACTION_END);
        mService.clearEvents();
        // Swipe starting inside the passthrough region but ending outside of it. This should still
        // behave as a passthrough interaction.
        dispatch(swipe(mTapLocation, add(mTapLocation, -mSwipeDistance, 0)));
        mMotionEventListener.assertPropagated(ACTION_DOWN, ACTION_MOVE, ACTION_UP);
        mService.assertPropagated(TYPE_TOUCH_INTERACTION_START, TYPE_TOUCH_INTERACTION_END);
        mService.clearEvents();
        // Swipe outside the passthrough region. This should not generate touch events.
        dispatch(swipe(add(mTapLocation, -1, 0), add(mTapLocation, -mSwipeDistance, 0)));
        mMotionEventListener.assertNonePropagated();
        mService.assertPropagated(
                TYPE_TOUCH_INTERACTION_START,
                TYPE_GESTURE_DETECTION_START,
                TYPE_GESTURE_DETECTION_END,
                TYPE_TOUCH_INTERACTION_END);
        // There should be no hover events in this test.
        mMotionEventListener.assertNonePropagated();
        clearPassthroughRegions();
    }

    public void dispatch(StrokeDescription firstStroke, StrokeDescription... rest) {
        GestureDescription.Builder builder =
                new GestureDescription.Builder().addStroke(firstStroke);
        for (StrokeDescription stroke : rest) {
            builder.addStroke(stroke);
        }
        dispatch(builder.build());
    }

    public void dispatch(GestureDescription gesture) {
        await(dispatchGesture(mService, gesture));
    }

    /** Set the accessibility focus to the element that has input focus. */
    private void syncAccessibilityFocusToInputFocus() {
        try {
            syncAccessibilityFocusToInputFocusInternal();
        } catch (Exception e) {
            // Try again in case it's a transient issue.
            syncAccessibilityFocusToInputFocusInternal();
        }
    }

    /** Do the actual accessibility focus / input focus syncing. */
    private void syncAccessibilityFocusToInputFocusInternal() {
        mService.runOnServiceSync(
                () -> {
                    AccessibilityNodeInfo focus =
                            mService.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                    focus.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
                    focus.recycle();
                });
        mService.waitForAccessibilityFocus();
    }

    private void setRightSideOfActivityWindowGestureDetectionPassthrough() {
        Region region = getRightSideOfActivityWindowRegion();
        mService.runOnServiceSync(
                () -> {
                    mService.setGestureDetectionPassthroughRegion(Display.DEFAULT_DISPLAY, region);
                });
    }

    private void setRightSideOfActivityWindowTouchExplorationPassthrough() {
        Region region = getRightSideOfActivityWindowRegion();
        mService.runOnServiceSync(
                () -> {
                    mService.setTouchExplorationPassthroughRegion(Display.DEFAULT_DISPLAY, region);
                });
    }

    private void clearPassthroughRegions() {
        mService.runOnServiceSync(
                () -> {
                    mService.setGestureDetectionPassthroughRegion(
                            Display.DEFAULT_DISPLAY, new Region());
                    mService.setTouchExplorationPassthroughRegion(
                            Display.DEFAULT_DISPLAY, new Region());
                });
    }

    private Region getRightSideOfActivityWindowRegion() {
        int[] viewLocation = new int[2];
        mView.getLocationOnScreen(viewLocation);

        int top = viewLocation[1];
        int left = viewLocation[0] + mView.getWidth() / 2;
        int right = viewLocation[0] + mView.getWidth();
        int bottom = viewLocation[1] + mView.getHeight();
        Region region = new Region(left, top, right, bottom);
        return region;
    }

    private List<MotionEvent> getMotionEventsForLastGesture() {
        return mService.getGestureInfo(mService.getGestureInfoSize() - 1).getMotionEvents();
    }
}
