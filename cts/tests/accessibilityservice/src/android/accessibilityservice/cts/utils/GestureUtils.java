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
 * limitations under the License.
 */

package android.accessibilityservice.cts.utils;

import static org.junit.Assert.assertTrue;

import android.accessibility.cts.common.InstrumentedAccessibilityService;
import android.accessibilityservice.AccessibilityService.GestureResultCallback;
import android.accessibilityservice.GestureDescription;
import android.accessibilityservice.GestureDescription.StrokeDescription;
import android.graphics.Path;
import android.graphics.PointF;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import androidx.test.InstrumentationRegistry;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.rules.TestRule;
import org.junit.runners.model.Statement;

import java.util.Random;
import java.util.concurrent.CompletableFuture;

public class GestureUtils {

    public static final String LOG_TAG = "GestureUtils";
    public static final long STROKE_TIME_GAP_MS_MIN = 1;
    public static final long STROKE_TIME_GAP_MS_DEFAULT = 40;
    public static final long STROKE_TIME_GAP_MS_MAX = ViewConfiguration.getDoubleTapTimeout() - 1;

    public static final Matcher<MotionEvent> IS_ACTION_DOWN =
            new MotionEventActionMatcher(MotionEvent.ACTION_DOWN);
    public static final Matcher<MotionEvent> IS_ACTION_POINTER_DOWN =
            new MotionEventActionMatcher(MotionEvent.ACTION_POINTER_DOWN);
    public static final Matcher<MotionEvent> IS_ACTION_UP =
            new MotionEventActionMatcher(MotionEvent.ACTION_UP);
    public static final Matcher<MotionEvent> IS_ACTION_POINTER_UP =
            new MotionEventActionMatcher(MotionEvent.ACTION_POINTER_UP);
    public static final Matcher<MotionEvent> IS_ACTION_CANCEL =
            new MotionEventActionMatcher(MotionEvent.ACTION_CANCEL);
    public static final Matcher<MotionEvent> IS_ACTION_MOVE =
            new MotionEventActionMatcher(MotionEvent.ACTION_MOVE);

    private static Random sRandom = null;
    private static boolean sShouldRandomize = false;
    // We generate the random seed later in randomize() and store it here to enable users to
    // reproduce randomized test failures.
    private static long sRandomSeed = 0;
    private static long sStrokeGapTimeMs = STROKE_TIME_GAP_MS_DEFAULT;

    private GestureUtils() {}

    public static CompletableFuture<Void> dispatchGesture(
            InstrumentedAccessibilityService service, GestureDescription gesture) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        GestureResultCallback callback =
                new GestureResultCallback() {
                    @Override
                    public void onCompleted(GestureDescription gestureDescription) {
                        result.complete(null);
                    }

                    @Override
                    public void onCancelled(GestureDescription gestureDescription) {
                        result.cancel(false);
                    }
                };
        service.runOnServiceSync(
                () -> {
                    if (!service.dispatchGesture(gesture, callback, null)) {
                        result.completeExceptionally(new IllegalStateException());
                    }
                });
        return result;
    }

    public static StrokeDescription pointerDown(PointF point) {
        return new StrokeDescription(path(point), 0, ViewConfiguration.getTapTimeout(), true);
    }

    public static StrokeDescription pointerUp(StrokeDescription lastStroke) {
        return lastStroke.continueStroke(
                path(lastPointOf(lastStroke)),
                endTimeOf(lastStroke),
                ViewConfiguration.getTapTimeout(),
                false);
    }

    public static PointF lastPointOf(StrokeDescription stroke) {
        float[] p = stroke.getPath().approximate(0.3f);
        return new PointF(p[p.length - 2], p[p.length - 1]);
    }

    public static StrokeDescription click(PointF point) {
        return new StrokeDescription(path(point), 0, ViewConfiguration.getTapTimeout());
    }

    public static StrokeDescription longClick(PointF point) {
        return new StrokeDescription(path(point), 0, ViewConfiguration.getLongPressTimeout() * 3);
    }

    public static StrokeDescription swipe(PointF from, PointF to) {
        return swipe(from, to, ViewConfiguration.getTapTimeout());
    }

    public static StrokeDescription swipe(PointF from, PointF to, long duration) {
        return new StrokeDescription(path(from, to), 0, duration);
    }

    /**
     * Simulates a touch exploration swipe that is interrupted partway through for a specified
     * amount of time, and then continued.
     */
    public static GestureDescription interruptedSwipe(PointF from, PointF to, long duration) {
        GestureDescription.Builder builder = new GestureDescription.Builder();
        long time = 0;
        PointF midpoint = new PointF((from.x + to.x) / 2.0f, (from.y + to.y) / 2.0f);
        StrokeDescription swipe1 = new StrokeDescription(path(from, midpoint), 0, duration / 2);
        builder.addStroke(swipe1);
        time += swipe1.getDuration() + sStrokeGapTimeMs;
        StrokeDescription swipe2 = startingAt(time, swipe(midpoint, to, duration / 2));
        builder.addStroke(swipe2);
        return builder.build();
    }

    public static StrokeDescription drag(StrokeDescription from, PointF to) {
        return from.continueStroke(
                path(lastPointOf(from), to),
                endTimeOf(from),
                ViewConfiguration.getTapTimeout(),
                true);
    }

    public static Path path(PointF first, PointF... rest) {
        Path path = new Path();
        path.moveTo(first.x, first.y);
        for (PointF point : rest) {
            path.lineTo(point.x, point.y);
        }
        return path;
    }

    public static StrokeDescription startingAt(long timeMs, StrokeDescription prototype) {
        return new StrokeDescription(
                prototype.getPath(), timeMs, prototype.getDuration(), prototype.willContinue());
    }

    public static long endTimeOf(StrokeDescription stroke) {
        return stroke.getStartTime() + stroke.getDuration();
    }

    public static float distance(PointF a, PointF b) {
        if (a == null) throw new NullPointerException();
        if (b == null) throw new NullPointerException();
        return (float) Math.hypot(a.x - b.x, a.y - b.y);
    }

    public static PointF add(PointF a, float x, float y) {
        return new PointF(a.x + x, a.y + y);
    }

    public static PointF add(PointF a, PointF b) {
        return add(a, b.x, b.y);
    }

    public static PointF diff(PointF a, PointF b) {
        return add(a, -b.x, -b.y);
    }

    public static PointF negate(PointF p) {
        return times(-1, p);
    }

    public static PointF times(float mult, PointF p) {
        return new PointF(p.x * mult, p.y * mult);
    }

    public static float length(PointF p) {
        return (float) Math.hypot(p.x, p.y);
    }

    public static PointF ceil(PointF p) {
        return new PointF((float) Math.ceil(p.x), (float) Math.ceil(p.y));
    }

    public static GestureDescription doubleTap(PointF point) {
        return doubleTap(point, Display.DEFAULT_DISPLAY);
    }

    /** Generates a double-tap gesture. */
    public static GestureDescription doubleTap(PointF point, int displayId) {
        return multiTap(point, 2, 0, displayId);
    }

    public static GestureDescription tripleTap(PointF point) {
        return tripleTap(point, Display.DEFAULT_DISPLAY);
    }

    /** Generates a triple-tap gesture. */
    public static GestureDescription tripleTap(PointF point, int displayId) {
        return multiTap(point, 3, 0, displayId);
    }

    public static GestureDescription multiTap(PointF point, int taps) {
        return multiTap(point, taps, 0);
    }

    public static GestureDescription multiTap(PointF point, int taps, int slop) {
        return multiTap(point, taps, 0, Display.DEFAULT_DISPLAY);
    }

    /** Generates a single-finger multi-tap gesture. */
    public static GestureDescription multiTap(PointF point, int taps, int slop, int displayId) {
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.setDisplayId(displayId);
        long time = 0;
        if (taps > 0) {
            // Place first tap on the point itself.
            // Subsequent taps will be offset somewhere within slop radius.
            // If slop is 0 subsequent taps will also be on the point itself.
            StrokeDescription stroke = click(point);
            builder.addStroke(stroke);
            time += stroke.getDuration() + sStrokeGapTimeMs;
            for (int i = 1; i < taps; i++) {
                stroke = click(getPointWithinSlop(point, slop));
                builder.addStroke(startingAt(time, stroke));
                time += stroke.getDuration() + sStrokeGapTimeMs;
            }
        }
        return builder.build();
    }

    public static GestureDescription doubleTapAndHold(PointF point) {
        return doubleTapAndHold(point, Display.DEFAULT_DISPLAY);
    }

    /** Generates a single-finger double-tap and hold gesture. */
    public static GestureDescription doubleTapAndHold(PointF point, int displayId) {
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.setDisplayId(displayId);
        StrokeDescription tap1 = click(point);
        StrokeDescription tap2 = startingAt(endTimeOf(tap1) + sStrokeGapTimeMs, longClick(point));
        builder.addStroke(tap1);
        builder.addStroke(tap2);
        return builder.build();
    }

    public static GestureDescription.Builder getGestureBuilder(
            int displayId, StrokeDescription... strokes) {
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.setDisplayId(displayId);
        for (StrokeDescription s : strokes) builder.addStroke(s);
        return builder;
    }

    private static class MotionEventActionMatcher extends TypeSafeMatcher<MotionEvent> {
        int mAction;

        MotionEventActionMatcher(int action) {
            super();
            mAction = action;
        }

        @Override
        protected boolean matchesSafely(MotionEvent motionEvent) {
            return motionEvent.getActionMasked() == mAction;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("Matching to action " + MotionEvent.actionToString(mAction));
        }

        @Override
        public void describeMismatchSafely(MotionEvent event, Description description) {
            description.appendText(
                    "received " + MotionEvent.actionToString(event.getActionMasked()));
        }
    }

    public static Matcher<MotionEvent> isAtPoint(final PointF point) {
        return isAtPoint(point, 0.01f);
    }

    public static Matcher<MotionEvent> isAtPoint(final PointF point, final float tol) {
        return new TypeSafeMatcher<MotionEvent>() {
            @Override
            protected boolean matchesSafely(MotionEvent event) {
                return Math.hypot(event.getX() - point.x, event.getY() - point.y) <= tol;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("Matching to point " + point);
            }

            @Override
            public void describeMismatchSafely(MotionEvent event, Description description) {
                description.appendText("received (" + event.getX() + ", " + event.getY() + ")");
            }
        };
    }

    public static Matcher<MotionEvent> isRawAtPoint(final PointF point) {
        return isRawAtPoint(point, 0.01f);
    }

    public static Matcher<MotionEvent> isRawAtPoint(final PointF point, final float tol) {
        return new TypeSafeMatcher<MotionEvent>() {
            @Override
            protected boolean matchesSafely(MotionEvent event) {
                return Math.hypot(event.getRawX() - point.x, event.getRawY() - point.y) <= tol;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("Matching to point " + point);
            }

            @Override
            public void describeMismatchSafely(MotionEvent event, Description description) {
                description.appendText(
                        "received (" + event.getRawX() + ", " + event.getRawY() + ")");
            }
        };
    }

    public static PointF getPointWithinSlop(PointF point, int slop) {
        return add(point, slop / 2, 0);
    }

    /**
     * Simulates a user placing one finger on the screen for a specified amount of time and then
     * multi-tapping with a second finger.
     *
     * @param explorePoint Where to place the first finger.
     * @param tapPoint Where to tap with the second finger.
     * @param taps The number of second-finger taps.
     * @param waitTime How long to hold the first finger before tapping with the second finger.
     */
    public static GestureDescription secondFingerMultiTap(
            PointF explorePoint, PointF tapPoint, int taps, int waitTime) {
        GestureDescription.Builder builder = new GestureDescription.Builder();
        long time = waitTime;
        for (int i = 0; i < taps; i++) {
            StrokeDescription stroke = click(tapPoint);
            builder.addStroke(startingAt(time, stroke));
            time += stroke.getDuration();
            time += ViewConfiguration.getDoubleTapTimeout() / 3;
        }
        builder.addStroke(swipe(explorePoint, explorePoint, time));
        return builder.build();
    }

    /**
     * Simulates a user placing multiple fingers on the specified screen
     * and then multi-tapping with these fingers.
     *
     * The location of fingers based on <code>basePoint<code/> are shifted by <code>delta<code/>.
     * Like (baseX, baseY), (baseX + deltaX, baseY + deltaY), and so on.
     *
     * @param basePoint Where to place the first finger.
     * @param delta Offset to basePoint where to place the 2nd or 3rd finger.
     * @param fingerCount The number of fingers.
     * @param tapCount The number of taps to fingers.
     * @param slop Slop range the finger tapped.
     * @param displayId Which display to dispatch the gesture.
     */
    public static GestureDescription multiFingerMultiTap(
            PointF basePoint,
            PointF delta,
            int fingerCount,
            int tapCount,
            int slop,
            int displayId) {
        assertTrue(fingerCount >= 2);
        assertTrue(tapCount > 0);
        final int strokeCount = fingerCount * tapCount;
        final PointF[] pointers = new PointF[fingerCount];
        final StrokeDescription[] strokes = new StrokeDescription[strokeCount];

        // The first tap
        for (int i = 0; i < fingerCount; i++) {
            pointers[i] = add(basePoint, times(i, delta));
            strokes[i] = click(pointers[i]);
        }
        // The rest of taps
        for (int tapIndex = 1; tapIndex < tapCount; tapIndex++) {
            for (int i = 0; i < fingerCount; i++) {
                final StrokeDescription lastStroke = strokes[(tapIndex - 1) * fingerCount + i];
                final long nextStartTime = endTimeOf(lastStroke) + sStrokeGapTimeMs;
                final int nextIndex = tapIndex * fingerCount + i;
                pointers[i] = getPointWithinSlop(pointers[i], slop);
                strokes[nextIndex] = startingAt(nextStartTime, click(pointers[i]));
            }
        }
        return getGestureBuilder(displayId, strokes).build();
    }

    /**
     * Simulates a user placing multiple fingers on the specified screen
     * and then multi-tapping and holding with these fingers.
     *
     * The location of fingers based on <code>basePoint<code/> are shifted by <code>delta<code/>.
     * Like (baseX, baseY), (baseX + deltaX, baseY + deltaY), and so on.
     *
     * @param basePoint Where to place the first finger.
     * @param delta Offset to basePoint where to place the 2nd or 3rd finger.
     * @param fingerCount The number of fingers.
     * @param tapCount The number of taps to fingers.
     * @param slop Slop range the finger tapped.
     * @param displayId Which display to dispatch the gesture.
     */
    public static GestureDescription multiFingerMultiTapAndHold(
            PointF basePoint,
            PointF delta,
            int fingerCount,
            int tapCount,
            int slop,
            int displayId) {
        assertTrue(fingerCount >= 2);
        assertTrue(tapCount > 0);
        final int strokeCount = fingerCount * tapCount;
        final PointF[] pointers = new PointF[fingerCount];
        final StrokeDescription[] strokes = new StrokeDescription[strokeCount];

        // The first tap
        for (int i = 0; i < fingerCount; i++) {
            pointers[i] = add(basePoint, times(i, delta));
            if (tapCount == 1) {
                strokes[i] = longClick(pointers[i]);
            } else {
                strokes[i] = click(pointers[i]);
            }
        }
        // The rest of taps
        for (int tapIndex = 1; tapIndex < tapCount; tapIndex++) {
            for (int i = 0; i < fingerCount; i++) {
                final StrokeDescription lastStroke = strokes[(tapIndex - 1) * fingerCount + i];
                final long nextStartTime = endTimeOf(lastStroke) + sStrokeGapTimeMs;
                final int nextIndex = tapIndex * fingerCount + i;
                pointers[i] = getPointWithinSlop(pointers[i], slop);
                if (tapIndex + 1 == tapCount) {
                    // Last tap so do long click.
                    strokes[nextIndex] = startingAt(nextStartTime, longClick(pointers[i]));
                } else {
                    strokes[nextIndex] = startingAt(nextStartTime, click(pointers[i]));
                }
            }
        }
        return getGestureBuilder(displayId, strokes).build();
    }

    /** Randomizes the values governing gesture creation. */
    public static void randomize() {
        Bundle args = InstrumentationRegistry.getArguments();
        String shouldRandomize = args.getString("randomize");
        if (shouldRandomize == null) {
            return;
        }
        sShouldRandomize = Boolean.parseBoolean(shouldRandomize);
        if (!sShouldRandomize) {
            return;
        }
        String seed = args.getString("randomSeed");
        if (seed != null) {
            sRandomSeed = Long.parseLong(seed);
            sRandom = new Random(sRandomSeed);
        } else {
            // Generate a random seed then store it.
            sRandomSeed = new Random().nextLong();
            sRandom = new Random(sRandomSeed);
        }
        Log.d(LOG_TAG, "Randomized values. Seed = " + sRandomSeed);
        sStrokeGapTimeMs =
                sRandom.nextLong(STROKE_TIME_GAP_MS_MAX - STROKE_TIME_GAP_MS_MIN)
                        + STROKE_TIME_GAP_MS_MIN;
        Log.d(LOG_TAG, "Stroke gap time = " + sStrokeGapTimeMs);
    }

    /** Rule used to report values in the failure message for easier bug reporting. */
    public static class DumpOnFailureRule implements TestRule {

        @Override
        public Statement apply(Statement base, org.junit.runner.Description description) {
            return new DumpOnFailureStatement(base);
        }

        class DumpOnFailureStatement extends Statement {
            Statement mBase;

            DumpOnFailureStatement(Statement base) {
                mBase = base;
            }

            @Override
            public void evaluate() throws Throwable {
                try {
                    mBase.evaluate();
                } catch (Throwable throwable) {
                    if (sShouldRandomize) {
                        // Give instructions on how to reproduce this behavior
                        StringBuilder message = new StringBuilder();
                        message.append(throwable.getMessage());
                        message.append("\nFor convenience, the randomized values are as follows:");
                        message.append("\nStroke gap time: ");
                        message.append(sStrokeGapTimeMs);
                        message.append("\nTo reproduce this failure using atest,");
                        message.append(" run this test with the options");
                        message.append("\n-- --test-arg");
                        message.append(" com.android.tradefed.testtype.AndroidJUnitTest:");
                        message.append("instrumentation-arg:randomize:=");
                        message.append(sShouldRandomize);
                        message.append(" --test-arg");
                        message.append(" com.android.tradefed.testtype.AndroidJUnitTest:");
                        message.append("instrumentation-arg:randomSeed:=");
                        message.append(sRandomSeed);
                        throw new Exception(message.toString(), throwable);
                    } else {
                        throw throwable;
                    }
                }
            }
        }
    }
}
