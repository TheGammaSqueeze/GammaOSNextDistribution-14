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

package android.view.cts;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.CancellationSignal;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.platform.test.annotations.Presubmit;
import android.view.ScrollCaptureCallback;
import android.view.ScrollCaptureSession;
import android.view.ScrollCaptureTarget;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.test.filters.MediumTest;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Exercises Scroll Capture (long screenshot) APIs in {@link ViewGroup}.
 */
@Presubmit
@SmallTest
@RunWith(MockitoJUnitRunner.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class ViewGroup_ScrollCaptureTest {

    private static class Receiver<T> implements Consumer<T> {
        private final List<T> mValues = new ArrayList<>();

        @Override
        public void accept(T target) {
            mValues.add(target);
        }

        public List<T> getAllValues() {
            return mValues;
        }

        public T getValue() {
            if (mValues.isEmpty()) {
                throw new IllegalStateException("No values received");
            }
            return mValues.get(mValues.size() - 1);
        }

        public boolean hasValue() {
            return !mValues.isEmpty();
        }
    }

    /** Make sure the hint flags are saved and loaded correctly. */
    @Test
    public void testSetScrollCaptureHint() {
        final Context context = getInstrumentation().getContext();
        final MockViewGroup viewGroup = new MockViewGroup(context);

        assertNotNull(viewGroup);
        assertEquals("Default scroll capture hint flags should be [SCROLL_CAPTURE_HINT_AUTO]",
                ViewGroup.SCROLL_CAPTURE_HINT_AUTO, viewGroup.getScrollCaptureHint());

        viewGroup.setScrollCaptureHint(View.SCROLL_CAPTURE_HINT_INCLUDE);
        assertEquals("The scroll capture hint was not stored correctly.",
                ViewGroup.SCROLL_CAPTURE_HINT_INCLUDE, viewGroup.getScrollCaptureHint());

        viewGroup.setScrollCaptureHint(ViewGroup.SCROLL_CAPTURE_HINT_EXCLUDE);
        assertEquals("The scroll capture hint was not stored correctly.",
                ViewGroup.SCROLL_CAPTURE_HINT_EXCLUDE, viewGroup.getScrollCaptureHint());

        viewGroup.setScrollCaptureHint(ViewGroup.SCROLL_CAPTURE_HINT_EXCLUDE_DESCENDANTS);
        assertEquals("The scroll capture hint was not stored correctly.",
                ViewGroup.SCROLL_CAPTURE_HINT_EXCLUDE_DESCENDANTS,
                viewGroup.getScrollCaptureHint());

        viewGroup.setScrollCaptureHint(ViewGroup.SCROLL_CAPTURE_HINT_INCLUDE
                | ViewGroup.SCROLL_CAPTURE_HINT_EXCLUDE_DESCENDANTS);
        assertEquals("The scroll capture hint was not stored correctly.",
                ViewGroup.SCROLL_CAPTURE_HINT_INCLUDE
                        | ViewGroup.SCROLL_CAPTURE_HINT_EXCLUDE_DESCENDANTS,
                viewGroup.getScrollCaptureHint());

        viewGroup.setScrollCaptureHint(ViewGroup.SCROLL_CAPTURE_HINT_EXCLUDE
                | ViewGroup.SCROLL_CAPTURE_HINT_EXCLUDE_DESCENDANTS);
        assertEquals("The scroll capture hint was not stored correctly.",
                ViewGroup.SCROLL_CAPTURE_HINT_EXCLUDE
                        | ViewGroup.SCROLL_CAPTURE_HINT_EXCLUDE_DESCENDANTS,
                viewGroup.getScrollCaptureHint());
    }

    /** Make sure the hint flags are saved and loaded correctly. */
    @Test
    public void testSetScrollCaptureHint_mutuallyExclusiveFlags() {
        final Context context = getInstrumentation().getContext();
        final MockViewGroup viewGroup = new MockViewGroup(context);

        viewGroup.setScrollCaptureHint(
                View.SCROLL_CAPTURE_HINT_INCLUDE | View.SCROLL_CAPTURE_HINT_EXCLUDE);
        assertEquals("Mutually exclusive flags were not resolved correctly",
                ViewGroup.SCROLL_CAPTURE_HINT_EXCLUDE, viewGroup.getScrollCaptureHint());
    }

    /**
     * No target is returned because MockViewGroup does not emulate a scrolling container.
     */
    @SmallTest
    @Test
    public void testDispatchScrollCaptureSearch() {
        final Context context = getInstrumentation().getContext();
        final MockViewGroup viewGroup =
                new MockViewGroup(context, 0, 0, 200, 200, View.SCROLL_CAPTURE_HINT_AUTO);

        Rect localVisibleRect = new Rect(0, 0, 200, 200);
        Point windowOffset = new Point();

        Receiver<ScrollCaptureTarget> receiver = new Receiver<>();
        viewGroup.dispatchScrollCaptureSearch(localVisibleRect, windowOffset, receiver);
        assertFalse("No target was expected", receiver.hasValue());
    }

    /**
     * Ensure that a ViewGroup with 'scrollCaptureHint=auto', and a scroll capture callback
     * produces a correct target for that handler.
     */
    @MediumTest
    @Test
    public void testDispatchScrollCaptureSearch_withCallback() {
        final Context context = getInstrumentation().getContext();
        MockViewGroup viewGroup =
                new MockViewGroup(context, 0, 0, 200, 200, View.SCROLL_CAPTURE_HINT_AUTO);

        MockScrollCaptureCallback callback = new MockScrollCaptureCallback();
        viewGroup.setScrollCaptureCallback(callback);

        Rect localVisibleRect = new Rect(0, 0, 200, 200);
        Point windowOffset = new Point();

        Receiver<ScrollCaptureTarget> receiver = new Receiver<>();
        viewGroup.dispatchScrollCaptureSearch(localVisibleRect, windowOffset, receiver);
        callOnScrollCaptureSearch(receiver);
        callback.completeSearchRequest(new Rect(1, 2, 3, 4));
        assertTrue("A target was expected", receiver.hasValue());

        ScrollCaptureTarget target = receiver.getValue();
        assertNotNull("Target not found", target);
        assertSame("Target has the wrong callback", callback, target.getCallback());
        assertEquals("Target has the wrong bounds", new Rect(1, 2, 3, 4), target.getScrollBounds());

        assertSame("Target has the wrong View", viewGroup, target.getContainingView());
        assertEquals("Target hint is incorrect", View.SCROLL_CAPTURE_HINT_AUTO,
                target.getContainingView().getScrollCaptureHint());
    }

    /**
     * Dispatch skips this view entirely due to the exclude hint, despite a callback being set.
     * Exclude takes precedence.
     */
    @MediumTest
    @Test
    public void testDispatchScrollCaptureSearch_withCallback_hintExclude() {
        final Context context = getInstrumentation().getContext();
        final MockViewGroup viewGroup =
                new MockViewGroup(context, 0, 0, 200, 200, View.SCROLL_CAPTURE_HINT_EXCLUDE);

        MockScrollCaptureCallback callback = new MockScrollCaptureCallback();
        viewGroup.setScrollCaptureCallback(callback);

        Rect localVisibleRect = new Rect(0, 0, 200, 200);
        Point windowOffset = new Point();

        Receiver<ScrollCaptureTarget> receiver = new Receiver<>();
        viewGroup.dispatchScrollCaptureSearch(localVisibleRect, windowOffset, receiver);
        callback.verifyZeroInteractions();
        assertFalse("No target expected.", receiver.hasValue());
    }

    private static boolean nullOrEmpty(Rect r) {
        return r == null || r.isEmpty();
    }

    /**
     * Test scroll capture search dispatch to child views.
     * <p>
     * Verifies computation of child visible bounds.
     * TODO: with scrollX / scrollY, split up into discrete tests
     */
    @MediumTest
    @Test
    public void testDispatchScrollCaptureSearch_toChildren() throws Exception {
        final Context context = getInstrumentation().getContext();
        final MockViewGroup viewGroup = new MockViewGroup(context, 0, 0, 200, 200);

        Rect localVisibleRect = new Rect(25, 50, 175, 150);
        Point windowOffset = new Point(0, 0);

        //        visible area
        //       |<- l=25,    |
        //       |    r=175 ->|
        // +--------------------------+
        // | view1 (0, 0, 200, 25)    |
        // +---------------+----------+
        // |               |          |
        // | view2         | view4    | --+
        // | (0, 25,       |    (inv) |   | visible area
        // |      150, 100)|          |   |
        // +---------------+----------+   | t=50, b=150
        // | view3         | view5    |   |
        // | (0, 100       |(150, 100 | --+
        // |     200, 200) | 200, 200)|
        // |               |          |
        // |               |          |
        // +---------------+----------+ (200,200)

        // View 1 is fully clipped and not visible.
        final MockView view1 = new MockView(context, 0, 0, 200, 25);
        viewGroup.addView(view1);

        // View 2 is partially visible. (75x75), but not scrollable
        final MockView view2 = new MockView(context, 0, 25, 150, 100);
        viewGroup.addView(view2);

        // View 3 is partially visible (175x50)
        // Pretend View3 can scroll by providing a callback to handle it here
        MockScrollCaptureCallback view3Callback = new MockScrollCaptureCallback();
        final MockView view3 = new MockView(context, 0, 100, 200, 200);
        view3.setScrollCaptureCallback(view3Callback);
        viewGroup.addView(view3);

        // View 4 is invisible and should be ignored.
        final MockView view4 = new MockView(context, 150, 25, 200, 100, View.INVISIBLE);
        viewGroup.addView(view4);

        MockScrollCaptureCallback view5Callback = new MockScrollCaptureCallback();

        // View 5 is partially visible and explicitly included via flag. (25x50)
        final MockView view5 = new MockView(context, 150, 100, 200, 200);
        view5.setScrollCaptureCallback(view5Callback);
        view5.setScrollCaptureHint(View.SCROLL_CAPTURE_HINT_INCLUDE);
        viewGroup.addView(view5);

        Receiver<ScrollCaptureTarget> receiver = new Receiver<>();

        // Dispatch to the ViewGroup
        viewGroup.dispatchScrollCaptureSearch(localVisibleRect, windowOffset, receiver);
        callOnScrollCaptureSearch(receiver);
        view3Callback.completeSearchRequest(new Rect(0, 0, 200, 100));
        view5Callback.completeSearchRequest(new Rect(0, 0, 50, 100));

        // View 1 is entirely clipped by the parent and not visible, dispatch
        // skips this view entirely.
        view1.assertDispatchScrollCaptureSearchCount(0);

        // View 2, verify the computed localVisibleRect and windowOffset are correctly transformed
        // to the child coordinate space
        view2.assertDispatchScrollCaptureSearchCount(1);
        view2.assertDispatchScrollCaptureSearchLastArgs(
                new Rect(25, 25, 150, 75), new Point(0, 25));

        // View 3, verify the computed localVisibleRect and windowOffset are correctly transformed
        // to the child coordinate space
        view3.assertDispatchScrollCaptureSearchCount(1);
        view3.assertDispatchScrollCaptureSearchLastArgs(
                new Rect(25, 0, 175, 50), new Point(0, 100));

        // view4 is invisible, so it should be skipped entirely.
        view4.assertDispatchScrollCaptureSearchCount(0);

        // view5 is partially visible
        view5.assertDispatchScrollCaptureSearchCount(1);
        view5.assertDispatchScrollCaptureSearchLastArgs(
                new Rect(0, 0, 25, 50), new Point(150, 100));

        assertTrue(receiver.hasValue());
        assertEquals("expected two targets", 2, receiver.getAllValues().size());
    }

    /**
     * Test stand-in for ScrollCaptureSearchResults which is not part the public API. This
     * dispatches a request each potential target's handler and collects the results
     * synchronously on the calling thread. Use with caution!
     *
     * @param receiver the result consumer
     */
    private void callOnScrollCaptureSearch(Receiver<ScrollCaptureTarget> receiver) {
        CancellationSignal signal = new CancellationSignal();
        receiver.getAllValues().forEach(target ->
                target.getCallback().onScrollCaptureSearch(signal, (scrollBounds) -> {
                    if (!nullOrEmpty(scrollBounds)) {
                        target.setScrollBounds(scrollBounds);
                        target.updatePositionInWindow();
                    }
                }));
    }

    /**
     * Tests the effect of padding on scroll capture search dispatch.
     * <p>
     * Verifies computation of child visible bounds with padding.
     */
    @MediumTest
    @Test
    public void testOnScrollCaptureSearch_withPadding() {
        final Context context = getInstrumentation().getContext();

        Rect windowBounds = new Rect(0, 0, 200, 200);
        Point windowOffset = new Point(0, 0);

        final MockViewGroup parent = new MockViewGroup(context, 0, 0, 200, 200);
        parent.setPadding(25, 50, 25, 50);
        parent.setClipToPadding(true); // (default)

        final MockView view1 = new MockView(context, 0, -100, 200, 100);
        parent.addView(view1);

        final MockView view2 = new MockView(context, 0, 0, 200, 200);
        parent.addView(view2);

        final MockViewGroup view3 = new MockViewGroup(context, 0, 100, 200, 300);
        parent.addView(view3);
        view3.setPadding(25, 25, 25, 25);
        view3.setClipToPadding(true);

        // Where targets are added
        Receiver<ScrollCaptureTarget> receiver = new Receiver<>();

        // Dispatch to the ViewGroup
        parent.dispatchScrollCaptureSearch(windowBounds, windowOffset, receiver);

        // Verify padding (with clipToPadding) is subtracted from visibleBounds
        parent.assertOnScrollCaptureSearchLastArgs(new Rect(25, 50, 175, 150), new Point(0, 0));

        view1.assertOnScrollCaptureSearchLastArgs(
                new Rect(25, 150, 175, 200), new Point(0, -100));

        view2.assertOnScrollCaptureSearchLastArgs(
                new Rect(25, 50, 175, 150), new Point(0, 0));

        // Account for padding on view3 as well (top == 25px)
        view3.assertOnScrollCaptureSearchLastArgs(
                new Rect(25, 25, 175, 50), new Point(0, 100));
    }

    public static final class MockView extends View {

        private int mDispatchScrollCaptureSearchNumCalls;
        private Rect mDispatchScrollCaptureSearchLastLocalVisibleRect;
        private Point mDispatchScrollCaptureSearchLastWindowOffset;
        private int mCreateScrollCaptureCallbackInternalCount;
        private Rect mOnScrollCaptureSearchLastLocalVisibleRect;
        private Point mOnScrollCaptureSearchLastWindowOffset;

        MockView(Context context) {
            this(context, /* left */ 0, /* top */0, /* right */ 0, /* bottom */0);
        }

        MockView(Context context, int left, int top, int right, int bottom) {
            this(context, left, top, right, bottom, View.VISIBLE);
        }

        MockView(Context context, int left, int top, int right, int bottom, int visibility) {
            super(context);
            setVisibility(visibility);
            setLeftTopRightBottom(left, top, right, bottom);
        }

        void assertDispatchScrollCaptureSearchCount(int count) {
            assertEquals("Unexpected number of calls to dispatchScrollCaptureSearch",
                    count, mDispatchScrollCaptureSearchNumCalls);
        }

        void assertDispatchScrollCaptureSearchLastArgs(Rect localVisibleRect, Point windowOffset) {
            assertEquals("arg localVisibleRect was incorrect.",
                    localVisibleRect, mDispatchScrollCaptureSearchLastLocalVisibleRect);
            assertEquals("arg windowOffset was incorrect.",
                    windowOffset, mDispatchScrollCaptureSearchLastWindowOffset);
        }

        void reset() {
            mDispatchScrollCaptureSearchNumCalls = 0;
            mDispatchScrollCaptureSearchLastWindowOffset = null;
            mDispatchScrollCaptureSearchLastLocalVisibleRect = null;
            mCreateScrollCaptureCallbackInternalCount = 0;

        }

        @Override
        public void onScrollCaptureSearch(Rect localVisibleRect, Point windowOffset,
                Consumer<ScrollCaptureTarget> targets) {
            super.onScrollCaptureSearch(localVisibleRect, windowOffset, targets);
            mOnScrollCaptureSearchLastLocalVisibleRect = new Rect(localVisibleRect);
            mOnScrollCaptureSearchLastWindowOffset = new Point(windowOffset);
        }

        void assertOnScrollCaptureSearchLastArgs(Rect localVisibleRect, Point windowOffset) {
            assertEquals("arg localVisibleRect was incorrect.",
                    localVisibleRect, mOnScrollCaptureSearchLastLocalVisibleRect);
            assertEquals("arg windowOffset was incorrect.",
                    windowOffset, mOnScrollCaptureSearchLastWindowOffset);
        }

        @Override
        public void dispatchScrollCaptureSearch(Rect localVisibleRect, Point windowOffset,
                Consumer<ScrollCaptureTarget> results) {
            mDispatchScrollCaptureSearchNumCalls++;
            mDispatchScrollCaptureSearchLastLocalVisibleRect = new Rect(localVisibleRect);
            mDispatchScrollCaptureSearchLastWindowOffset = new Point(windowOffset);
            super.dispatchScrollCaptureSearch(localVisibleRect, windowOffset, results);
        }
    }

    static class CallbackStub implements ScrollCaptureCallback {

        @Override
        public void onScrollCaptureSearch(@NonNull CancellationSignal signal,
                @NonNull Consumer<Rect> onReady) {
        }

        @Override
        public void onScrollCaptureStart(@NonNull ScrollCaptureSession session,
                @NonNull CancellationSignal signal, @NonNull Runnable onReady) {
        }

        @Override
        public void onScrollCaptureImageRequest(@NonNull ScrollCaptureSession session,
                @NonNull CancellationSignal signal, @NonNull Rect captureArea,
                Consumer<Rect> onComplete) {
        }

        @Override
        public void onScrollCaptureEnd(@NonNull Runnable onReady) {
        }
    };

    public static final class MockViewGroup extends ViewGroup {
        private Rect mOnScrollCaptureSearchLastLocalVisibleRect;
        private Point mOnScrollCaptureSearchLastWindowOffset;

        MockViewGroup(Context context) {
            this(context, /* left */ 0, /* top */0, /* right */ 0, /* bottom */0);
        }

        MockViewGroup(Context context, int left, int top, int right, int bottom) {
            this(context, left, top, right, bottom, View.SCROLL_CAPTURE_HINT_AUTO);
        }

        MockViewGroup(Context context, int left, int top, int right, int bottom,
                int scrollCaptureHint) {
            super(context);
            setScrollCaptureHint(scrollCaptureHint);
            setLeftTopRightBottom(left, top, right, bottom);
        }

        @Override
        public void onScrollCaptureSearch(Rect localVisibleRect, Point windowOffset,
                Consumer<ScrollCaptureTarget> targets) {
            super.onScrollCaptureSearch(localVisibleRect, windowOffset, targets);
            mOnScrollCaptureSearchLastLocalVisibleRect = new Rect(localVisibleRect);
            mOnScrollCaptureSearchLastWindowOffset = new Point(windowOffset);
        }

        void assertOnScrollCaptureSearchLastArgs(Rect localVisibleRect, Point windowOffset) {
            assertEquals("arg localVisibleRect was incorrect.",
                    localVisibleRect, mOnScrollCaptureSearchLastLocalVisibleRect);
            assertEquals("arg windowOffset was incorrect.",
                    windowOffset, mOnScrollCaptureSearchLastWindowOffset);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            // We don't layout this view.
        }
    }
}
