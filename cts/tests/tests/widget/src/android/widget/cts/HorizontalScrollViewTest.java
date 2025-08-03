/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.widget.cts;

import static android.widget.cts.util.StretchEdgeUtil.dragAndHoldExecute;
import static android.widget.cts.util.StretchEdgeUtil.fling;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Xml;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.widget.EdgeEffect;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.TextView;
import android.widget.cts.util.NoReleaseEdgeEffect;
import android.widget.cts.util.StretchEdgeUtil;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;
import androidx.test.annotation.UiThreadTest;
import androidx.test.filters.LargeTest;
import androidx.test.filters.MediumTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.WidgetTestUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;

/**
 * Test {@link HorizontalScrollView}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class HorizontalScrollViewTest {
    private static final int ITEM_WIDTH  = 500;
    private static final int ITEM_HEIGHT = 200;
    private static final int ITEM_COUNT  = 15;
    private static final int PAGE_WIDTH  = 200;
    private static final int PAGE_HEIGHT = 200;
    private static final int SCROLL_RIGHT = ITEM_WIDTH * ITEM_COUNT - PAGE_WIDTH;

    private Instrumentation mInstrumentation;
    private Activity mActivity;
    private HorizontalScrollView mScrollViewRegular;
    private HorizontalScrollView mScrollViewCustom;
    private MyHorizontalScrollView mScrollViewCustomEmpty;
    private MyHorizontalScrollView mScrollViewStretch;

    private SurfaceView mSurfaceView;
    private float mDurationScale = 1f;

    @Rule
    public ActivityTestRule<HorizontalScrollViewCtsActivity> mActivityRule =
            new ActivityTestRule<>(HorizontalScrollViewCtsActivity.class);

    @Before
    public void setup() {
        mDurationScale = ValueAnimator.getDurationScale();
        ValueAnimator.setDurationScale(1f);
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
        mScrollViewRegular = (HorizontalScrollView) mActivity.findViewById(
                R.id.horizontal_scroll_view_regular);
        mScrollViewCustom = (MyHorizontalScrollView) mActivity.findViewById(
                R.id.horizontal_scroll_view_custom);
        mScrollViewCustomEmpty = (MyHorizontalScrollView) mActivity.findViewById(
                R.id.horizontal_scroll_view_custom_empty);
        mScrollViewStretch = (MyHorizontalScrollView) mActivity.findViewById(
                R.id.horizontal_scroll_view_stretch);
        mSurfaceView = mActivity.findViewById(R.id.surfaceview_stretch_target);
    }

    @After
    public void teardown() {
        ValueAnimator.setDurationScale(mDurationScale);
    }

    @Test
    public void testConstructor() {
        XmlPullParser parser = mActivity.getResources().getLayout(R.layout.horizontal_scrollview);
        AttributeSet attrs = Xml.asAttributeSet(parser);
        new HorizontalScrollView(mActivity);

        new HorizontalScrollView(mActivity, attrs);

        new HorizontalScrollView(mActivity, attrs, 0);
    }

    @UiThreadTest
    @Test
    public void testGetMaxScrollAmount() {
        mScrollViewRegular.layout(0, 0, 100, 200);
        assertEquals((100 - 0) / 2, mScrollViewRegular.getMaxScrollAmount());

        mScrollViewRegular.layout(0, 0, 150, 100);
        assertEquals((150 - 0) / 2, mScrollViewRegular.getMaxScrollAmount());
    }

    @UiThreadTest
    @Test
    public void testAddView() {
        TextView child0 = new TextView(mActivity);
        mScrollViewRegular.addView(child0);
        assertSame(child0, mScrollViewRegular.getChildAt(0));

        assertEquals(1, mScrollViewRegular.getChildCount());
        TextView child1 = new TextView(mActivity);
        try {
            mScrollViewRegular.addView(child1);
            fail("did not throw IllegalStateException when add more than one child");
        } catch (IllegalStateException e) {
            // expected
        }
        assertEquals(1, mScrollViewRegular.getChildCount());
    }

    @UiThreadTest
    @Test
    public void testAddViewWithIndex() {
        TextView child0 = new TextView(mActivity);
        mScrollViewRegular.addView(child0, 0);
        assertSame(child0, mScrollViewRegular.getChildAt(0));

        assertEquals(1, mScrollViewRegular.getChildCount());
        TextView child1 = new TextView(mActivity);
        try {
            mScrollViewRegular.addView(child1, 1);
            fail("did not throw IllegalStateException when add more than one child");
        } catch (IllegalStateException e) {
            // expected
        }
        assertEquals(1, mScrollViewRegular.getChildCount());

        mScrollViewRegular.removeAllViews();

        mScrollViewRegular.addView(child0, -1);
        assertSame(child0, mScrollViewRegular.getChildAt(0));

        assertEquals(1, mScrollViewRegular.getChildCount());
        child1 = new TextView(mActivity);
        try {
            mScrollViewRegular.addView(child1, -1);
            fail("did not throw IllegalStateException when add more than one child");
        } catch (IllegalStateException e) {
            // expected
        }
        assertEquals(1, mScrollViewRegular.getChildCount());

        mScrollViewRegular.removeAllViews();

        try {
            mScrollViewRegular.addView(child0, 1);
            fail("did not throw IndexOutOfBoundsException when index is larger than 0");
        } catch (IndexOutOfBoundsException e) {
            // expected
        }
    }

    @UiThreadTest
    @Test
    public void testAddViewWithLayoutParams() {
        TextView child0 = new TextView(mActivity);
        mScrollViewRegular.addView(child0, new ViewGroup.LayoutParams(200, 100));
        assertSame(child0, mScrollViewRegular.getChildAt(0));
        assertEquals(200, child0.getLayoutParams().width);
        assertEquals(100, child0.getLayoutParams().height);

        assertEquals(1, mScrollViewRegular.getChildCount());
        TextView child1 = new TextView(mActivity);
        try {
            mScrollViewRegular.addView(child1, new ViewGroup.LayoutParams(200, 100));
            fail("did not throw IllegalStateException when add more than one child");
        } catch (IllegalStateException e) {
            // expected
        }
        assertEquals(1, mScrollViewRegular.getChildCount());

        mScrollViewRegular.removeAllViews();
        child0 = new TextView(mActivity);

        try {
            mScrollViewRegular.addView(child0, null);
            fail("did not throw NullPointerException when LayoutParams is null.");
        } catch (NullPointerException e) {
            // expected
        }
    }

    @UiThreadTest
    @Test
    public void testAddViewWithIndexAndLayoutParams() {
        TextView child0 = new TextView(mActivity);
        mScrollViewRegular.addView(child0, 0, new ViewGroup.LayoutParams(200, 100));
        assertSame(child0, mScrollViewRegular.getChildAt(0));
        assertEquals(200, child0.getLayoutParams().width);
        assertEquals(100, child0.getLayoutParams().height);

        assertEquals(1, mScrollViewRegular.getChildCount());
        TextView child1 = new TextView(mActivity);
        try {
            mScrollViewRegular.addView(child1, 0, new ViewGroup.LayoutParams(200, 100));
            fail("did not throw IllegalStateException when add more than one child");
        } catch (IllegalStateException e) {
            // expected
        }
        assertEquals(1, mScrollViewRegular.getChildCount());

        mScrollViewRegular.removeAllViews();

        child0 = new TextView(mActivity);
        try {
            mScrollViewRegular.addView(child0, null);
            fail("did not throw NullPointerException when LayoutParams is null.");
        } catch (NullPointerException e) {
            // expected
        }

        mScrollViewRegular.removeAllViews();

        mScrollViewRegular.addView(child0, -1, new ViewGroup.LayoutParams(300, 150));
        assertSame(child0, mScrollViewRegular.getChildAt(0));
        assertEquals(300, child0.getLayoutParams().width);
        assertEquals(150, child0.getLayoutParams().height);

        assertEquals(1, mScrollViewRegular.getChildCount());
        child1 = new TextView(mActivity);
        try {
            mScrollViewRegular.addView(child1, -1, new ViewGroup.LayoutParams(200, 100));
            fail("did not throw IllegalStateException when add more than one child");
        } catch (IllegalStateException e) {
            // expected
        }
        assertEquals(1, mScrollViewRegular.getChildCount());

        mScrollViewRegular.removeAllViews();

        try {
            mScrollViewRegular.addView(child0, 1, new ViewGroup.LayoutParams(200, 100));
            fail("did not throw IndexOutOfBoundsException when index is larger than 0");
        } catch (IndexOutOfBoundsException e) {
            // expected
        }
    }

    @UiThreadTest
    @Test
    public void testAccessFillViewport() {
        assertFalse(mScrollViewRegular.isFillViewport());
        mScrollViewRegular.layout(0, 0, 100, 100);
        assertFalse(mScrollViewRegular.isLayoutRequested());

        mScrollViewRegular.setFillViewport(false);
        assertFalse(mScrollViewRegular.isFillViewport());
        assertFalse(mScrollViewRegular.isLayoutRequested());

        mScrollViewRegular.setFillViewport(true);
        assertTrue(mScrollViewRegular.isFillViewport());
        assertTrue(mScrollViewRegular.isLayoutRequested());

        mScrollViewRegular.layout(0, 0, 100, 100);
        assertFalse(mScrollViewCustom.isLayoutRequested());

        mScrollViewRegular.setFillViewport(false);
        assertFalse(mScrollViewRegular.isFillViewport());
        assertTrue(mScrollViewRegular.isLayoutRequested());
    }

    @Test
    public void testAccessSmoothScrollingEnabled() throws Throwable {
        assertTrue(mScrollViewCustom.isSmoothScrollingEnabled());

        // scroll immediately
        mScrollViewCustom.setSmoothScrollingEnabled(false);
        assertFalse(mScrollViewCustom.isSmoothScrollingEnabled());

        mActivityRule.runOnUiThread(() -> mScrollViewCustom.fullScroll(View.FOCUS_RIGHT));
        assertEquals(SCROLL_RIGHT, mScrollViewCustom.getScrollX());

        mActivityRule.runOnUiThread(() -> mScrollViewCustom.fullScroll(View.FOCUS_LEFT));
        assertEquals(0, mScrollViewCustom.getScrollX());

        // smooth scroll
        mScrollViewCustom.setSmoothScrollingEnabled(true);
        assertTrue(mScrollViewCustom.isSmoothScrollingEnabled());

        mActivityRule.runOnUiThread(() -> mScrollViewCustom.fullScroll(View.FOCUS_RIGHT));
        pollingCheckSmoothScrolling(0, SCROLL_RIGHT, 0, 0);
        assertEquals(SCROLL_RIGHT, mScrollViewCustom.getScrollX());

        mActivityRule.runOnUiThread(() -> mScrollViewCustom.fullScroll(View.FOCUS_LEFT));
        pollingCheckSmoothScrolling(SCROLL_RIGHT, 0, 0, 0);
        assertEquals(0, mScrollViewCustom.getScrollX());
    }

    @UiThreadTest
    @Test
    public void testMeasureChild() {
        MyView child = new MyView(mActivity);
        child.setBackgroundDrawable(null);
        child.setPadding(0, 0, 0, 0);
        child.setMinimumWidth(30);
        child.setLayoutParams(new ViewGroup.LayoutParams(100, 100));
        child.measure(MeasureSpec.makeMeasureSpec(100, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(100, MeasureSpec.EXACTLY));

        assertEquals(100, child.getMeasuredHeight());
        assertEquals(100, child.getMeasuredWidth());

        ((MyHorizontalScrollView) mScrollViewCustom).measureChild(
                child, MeasureSpec.makeMeasureSpec(100, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(100, MeasureSpec.EXACTLY));

        assertEquals(100, child.getMeasuredHeight());
        assertEquals(30, child.getMeasuredWidth());
    }

    @UiThreadTest
    @Test
    public void testMeasureChildWithMargins() {
        MyView child = new MyView(mActivity);
        child.setBackgroundDrawable(null);
        child.setPadding(0, 0, 0, 0);
        child.setMinimumWidth(30);
        child.setLayoutParams(new ViewGroup.MarginLayoutParams(100, 100));
        child.measure(MeasureSpec.makeMeasureSpec(100, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(100, MeasureSpec.EXACTLY));

        assertEquals(100, child.getMeasuredHeight());
        assertEquals(100, child.getMeasuredWidth());

        ((MyHorizontalScrollView) mScrollViewCustom).measureChildWithMargins(child,
                MeasureSpec.makeMeasureSpec(100, MeasureSpec.EXACTLY), 5,
                MeasureSpec.makeMeasureSpec(100, MeasureSpec.EXACTLY), 5);

        assertEquals(100, child.getMeasuredHeight());
        assertEquals(30, child.getMeasuredWidth());
    }

    @UiThreadTest
    @Test
    public void testMeasureSpecs() {
        MyView child = spy(new MyView(mActivity));
        mScrollViewCustomEmpty.addView(child);

        mScrollViewCustomEmpty.measureChild(child,
                MeasureSpec.makeMeasureSpec(100, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(150, MeasureSpec.EXACTLY));
        verify(child).onMeasure(
                eq(MeasureSpec.makeMeasureSpec(100, MeasureSpec.UNSPECIFIED)),
                eq(MeasureSpec.makeMeasureSpec(150, MeasureSpec.EXACTLY)));
    }

    @UiThreadTest
    @Test
    public void testMeasureSpecsWithPadding() {
        MyView child = spy(new MyView(mActivity));
        mScrollViewCustomEmpty.setPadding(3, 5, 7, 11);
        mScrollViewCustomEmpty.addView(child);

        mScrollViewCustomEmpty.measureChild(child,
                MeasureSpec.makeMeasureSpec(100, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(150, MeasureSpec.EXACTLY));
        verify(child).onMeasure(
                eq(MeasureSpec.makeMeasureSpec(90, MeasureSpec.UNSPECIFIED)),
                eq(MeasureSpec.makeMeasureSpec(134, MeasureSpec.EXACTLY)));
    }

    @UiThreadTest
    @Test
    public void testMeasureSpecsWithMargins() {
        MyView child = spy(new MyView(mActivity));
        mScrollViewCustomEmpty.addView(child);

        mScrollViewCustomEmpty.measureChildWithMargins(child,
                MeasureSpec.makeMeasureSpec(100, MeasureSpec.EXACTLY), 15,
                MeasureSpec.makeMeasureSpec(150, MeasureSpec.EXACTLY), 20);
        verify(child).onMeasure(
                eq(MeasureSpec.makeMeasureSpec(85, MeasureSpec.UNSPECIFIED)),
                eq(MeasureSpec.makeMeasureSpec(130, MeasureSpec.EXACTLY)));
    }

    @UiThreadTest
    @Test
    public void testMeasureSpecsWithMarginsAndPadding() {
        MyView child = spy(new MyView(mActivity));
        mScrollViewCustomEmpty.setPadding(3, 5, 7, 11);
        mScrollViewCustomEmpty.addView(child);

        mScrollViewCustomEmpty.measureChildWithMargins(child,
                MeasureSpec.makeMeasureSpec(100, MeasureSpec.EXACTLY), 15,
                MeasureSpec.makeMeasureSpec(150, MeasureSpec.EXACTLY), 20);
        verify(child).onMeasure(
                eq(MeasureSpec.makeMeasureSpec(75, MeasureSpec.UNSPECIFIED)),
                eq(MeasureSpec.makeMeasureSpec(114, MeasureSpec.EXACTLY)));
    }

    @UiThreadTest
    @Test
    public void testMeasureSpecsWithMarginsAndNoHintWidth() {
        MyView child = spy(new MyView(mActivity));
        mScrollViewCustomEmpty.addView(child);

        mScrollViewCustomEmpty.measureChildWithMargins(child,
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), 15,
                MeasureSpec.makeMeasureSpec(150, MeasureSpec.EXACTLY), 20);
        verify(child).onMeasure(
                eq(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)),
                eq(MeasureSpec.makeMeasureSpec(130, MeasureSpec.EXACTLY)));
    }

    @UiThreadTest
    @Test
    public void testFillViewport() {
        MyView child = new MyView(mActivity);
        mScrollViewRegular.setFillViewport(true);
        child.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        mScrollViewRegular.addView(child);
        mScrollViewRegular.measure(MeasureSpec.makeMeasureSpec(150, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(100, MeasureSpec.EXACTLY));

        assertEquals(150, child.getMeasuredWidth());
        assertEquals(100, child.getMeasuredHeight());

        mScrollViewRegular.layout(0, 0, 150, 100);
        assertEquals(0, child.getLeft());
    }

    @UiThreadTest
    @Test
    public void testFillViewportWithScrollViewPadding() {
        mScrollViewRegular.setFillViewport(true);
        mScrollViewRegular.setPadding(3, 10, 5, 7);

        MyView child = new MyView(mActivity);
        child.setLayoutParams(new ViewGroup.LayoutParams(10,10));
        child.setDesiredWidth(30);

        mScrollViewRegular.addView(child);
        mScrollViewRegular.measure(MeasureSpec.makeMeasureSpec(100, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(150, MeasureSpec.EXACTLY));

        assertEquals(92, child.getMeasuredWidth());
        assertEquals(10, child.getMeasuredHeight());

        mScrollViewRegular.layout(0, 0, 100, 150);
        assertEquals(3, child.getLeft());
    }

    @UiThreadTest
    @Test
    public void testFillViewportWithChildMargins() {
        mScrollViewRegular.setFillViewport(true);

        MyView child = new MyView(mActivity);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(10, 10);
        lp.leftMargin = 3;
        lp.topMargin = 10;
        lp.rightMargin = 5;
        lp.bottomMargin = 7;
        child.setDesiredWidth(30);
        child.setLayoutParams(lp);

        mScrollViewRegular.addView(child);
        mScrollViewRegular.measure(MeasureSpec.makeMeasureSpec(100, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(150, MeasureSpec.EXACTLY));

        assertEquals(92, child.getMeasuredWidth());
        assertEquals(10, child.getMeasuredHeight());

        mScrollViewRegular.layout(0, 0, 100, 150);
        assertEquals(3, child.getLeft());
    }

    @UiThreadTest
    @Test
    public void testFillViewportWithScrollViewPaddingAlreadyFills() {
        mScrollViewRegular.setFillViewport(true);
        mScrollViewRegular.setPadding(3, 10, 5, 7);

        MyView child = new MyView(mActivity);
        child.setDesiredWidth(175);

        mScrollViewRegular.addView(child);
        mScrollViewRegular.measure(MeasureSpec.makeMeasureSpec(100, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(150, MeasureSpec.EXACTLY));


        assertEquals(175, child.getMeasuredWidth());
        assertEquals(133, child.getMeasuredHeight());

        mScrollViewRegular.layout(0, 0, 100, 150);
        assertEquals(3, child.getLeft());
    }

    @UiThreadTest
    @Test
    public void testFillViewportWithChildMarginsAlreadyFills() {
        mScrollViewRegular.setFillViewport(true);
        MyView child = new MyView(mActivity);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);

        lp.leftMargin = 3;
        lp.topMargin = 10;
        lp.rightMargin = 5;
        lp.bottomMargin = 7;
        child.setLayoutParams(lp);
        child.setDesiredWidth(175);

        mScrollViewRegular.addView(child);
        mScrollViewRegular.measure(MeasureSpec.makeMeasureSpec(100, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(150, MeasureSpec.EXACTLY));

        assertEquals(175, child.getMeasuredWidth());
        assertEquals(133, child.getMeasuredHeight());

        mScrollViewRegular.layout(0, 0, 100, 150);
        assertEquals(3, child.getLeft());
    }

    @UiThreadTest
    @Test
    public void testPageScroll() {
        mScrollViewCustom.setSmoothScrollingEnabled(false);
        assertEquals(0, mScrollViewCustom.getScrollX());

        assertTrue(mScrollViewCustom.pageScroll(View.FOCUS_RIGHT));
        assertEquals(PAGE_WIDTH, mScrollViewCustom.getScrollX());

        mScrollViewCustom.scrollTo(SCROLL_RIGHT, PAGE_HEIGHT);
        assertFalse(mScrollViewCustom.pageScroll(View.FOCUS_RIGHT));
        assertEquals(SCROLL_RIGHT, mScrollViewCustom.getScrollX());

        assertTrue(mScrollViewCustom.pageScroll(View.FOCUS_LEFT));
        assertEquals(SCROLL_RIGHT - PAGE_WIDTH, mScrollViewCustom.getScrollX());

        mScrollViewCustom.scrollTo(0, PAGE_HEIGHT);
        assertFalse(mScrollViewCustom.pageScroll(View.FOCUS_LEFT));
        assertEquals(0, mScrollViewCustom.getScrollX());
    }

    @UiThreadTest
    @Test
    public void testFullScroll() {
        mScrollViewCustom.setSmoothScrollingEnabled(false);
        assertEquals(0, mScrollViewCustom.getScrollX());

        assertTrue(mScrollViewCustom.fullScroll(View.FOCUS_RIGHT));
        assertEquals(SCROLL_RIGHT, mScrollViewCustom.getScrollX());

        assertFalse(mScrollViewCustom.fullScroll(View.FOCUS_RIGHT));
        assertEquals(SCROLL_RIGHT, mScrollViewCustom.getScrollX());

        assertTrue(mScrollViewCustom.fullScroll(View.FOCUS_LEFT));
        assertEquals(0, mScrollViewCustom.getScrollX());

        assertFalse(mScrollViewCustom.fullScroll(View.FOCUS_LEFT));
        assertEquals(0, mScrollViewCustom.getScrollX());
    }

    @UiThreadTest
    @Test
    public void testArrowScroll() {
        mScrollViewCustom.setSmoothScrollingEnabled(false);
        assertEquals(0, mScrollViewCustom.getScrollX());

        int x = mScrollViewCustom.getScrollX();
        while (SCROLL_RIGHT != x) {
            assertTrue(mScrollViewCustom.arrowScroll(View.FOCUS_RIGHT));
            assertTrue(x <= mScrollViewCustom.getScrollX());
            x = mScrollViewCustom.getScrollX();
        }

        assertFalse(mScrollViewCustom.arrowScroll(View.FOCUS_RIGHT));
        assertEquals(SCROLL_RIGHT, mScrollViewCustom.getScrollX());

        x = mScrollViewCustom.getScrollX();
        while (0 != x) {
            assertTrue(mScrollViewCustom.arrowScroll(View.FOCUS_LEFT));
            assertTrue(x >= mScrollViewCustom.getScrollX());
            x = mScrollViewCustom.getScrollX();
        }

        assertFalse(mScrollViewCustom.arrowScroll(View.FOCUS_LEFT));
        assertEquals(0, mScrollViewCustom.getScrollX());
    }

    @Test
    public void testSmoothScrollBy() throws Throwable {
        assertEquals(0, mScrollViewCustom.getScrollX());
        assertEquals(0, mScrollViewCustom.getScrollY());

        mActivityRule.runOnUiThread(() -> mScrollViewCustom.smoothScrollBy(SCROLL_RIGHT, 0));
        pollingCheckSmoothScrolling(0, SCROLL_RIGHT, 0, 0);
        assertEquals(SCROLL_RIGHT, mScrollViewCustom.getScrollX());
        assertEquals(0, mScrollViewCustom.getScrollY());

        mActivityRule.runOnUiThread(() -> mScrollViewCustom.smoothScrollBy(-SCROLL_RIGHT, 0));
        pollingCheckSmoothScrolling(SCROLL_RIGHT, 0, 0, 0);
        assertEquals(0, mScrollViewCustom.getScrollX());
        assertEquals(0, mScrollViewCustom.getScrollY());
    }

    @Test
    public void testSmoothScrollTo() throws Throwable {
        assertEquals(0, mScrollViewCustom.getScrollX());
        assertEquals(0, mScrollViewCustom.getScrollY());

        mActivityRule.runOnUiThread(() -> mScrollViewCustom.smoothScrollTo(SCROLL_RIGHT, 0));
        pollingCheckSmoothScrolling(0, SCROLL_RIGHT, 0, 0);
        assertEquals(SCROLL_RIGHT, mScrollViewCustom.getScrollX());
        assertEquals(0, mScrollViewCustom.getScrollY());

        mActivityRule.runOnUiThread(() -> mScrollViewCustom.smoothScrollTo(0, 0));
        pollingCheckSmoothScrolling(SCROLL_RIGHT, 0, 0, 0);
        assertEquals(0, mScrollViewCustom.getScrollX());
        assertEquals(0, mScrollViewCustom.getScrollY());
    }

    @Test
    public void testComputeScrollDeltaToGetChildRectOnScreen() {
        mScrollViewCustom.setSmoothScrollingEnabled(false);
        int edge = mScrollViewCustom.getHorizontalFadingEdgeLength();

        MyHorizontalScrollView myScrollViewCustom = (MyHorizontalScrollView) mScrollViewCustom;

        // Rect's width is smaller than scroll view
        Rect rect = new Rect(0, 0, 0, 0);
        assertEquals(0, myScrollViewCustom.computeScrollDeltaToGetChildRectOnScreen(rect));

        rect = new Rect(edge, 0, PAGE_WIDTH, 0);
        assertEquals(0, myScrollViewCustom.computeScrollDeltaToGetChildRectOnScreen(rect));

        mScrollViewCustom.scrollTo(0, 0);
        rect = new Rect(edge + 1, 0, PAGE_WIDTH, 0);
        assertEquals(edge, myScrollViewCustom.computeScrollDeltaToGetChildRectOnScreen(rect));
    }

    @Test
    public void testComputeHorizontalScrollRange() {
        assertTrue(mScrollViewCustom.getChildCount() > 0);
        assertEquals(ITEM_WIDTH * ITEM_COUNT,
                ((MyHorizontalScrollView) mScrollViewCustom).computeHorizontalScrollRange());

        MyHorizontalScrollView scrollView = new MyHorizontalScrollView(mActivity);
        assertEquals(0, scrollView.getChildCount());
        assertEquals(0, scrollView.computeHorizontalScrollRange());
    }

    @UiThreadTest
    @Test
    public void testRequestChildFocus() {
        mScrollViewCustom.setSmoothScrollingEnabled(false);

        View firstChild = mScrollViewCustom.findViewById(R.id.first_horizontal_child);
        View lastChild = mScrollViewCustom.findViewById(R.id.last_horizontal_child);
        firstChild.requestFocus();

        int scrollX = mScrollViewCustom.getScrollX();
        mScrollViewCustom.requestChildFocus(lastChild, lastChild);
        // check scrolling to the child which wants focus
        assertTrue(mScrollViewCustom.getScrollX() > scrollX);

        scrollX = mScrollViewCustom.getScrollX();
        mScrollViewCustom.requestChildFocus(firstChild, firstChild);
        // check scrolling to the child which wants focus
        assertTrue(mScrollViewCustom.getScrollX() < scrollX);
    }

    @UiThreadTest
    @Test
    public void testRequestChildRectangleOnScreen() {
        mScrollViewCustom.setSmoothScrollingEnabled(false);
        int edge = mScrollViewCustom.getHorizontalFadingEdgeLength();

        View child = mScrollViewCustom.findViewById(R.id.first_horizontal_child);
        final Rect originalRect = new Rect(0, 0, 10, 10);
        final Rect newRect = new Rect(ITEM_WIDTH - 10, ITEM_HEIGHT - 10, ITEM_WIDTH, ITEM_HEIGHT);

        assertFalse(mScrollViewCustom.requestChildRectangleOnScreen(child, originalRect, true));
        assertEquals(0, mScrollViewCustom.getScrollX());
        assertEquals(0, mScrollViewCustom.getScrollY());

        assertTrue(mScrollViewCustom.requestChildRectangleOnScreen(child, newRect, true));
        assertEquals(ITEM_WIDTH - mScrollViewCustom.getWidth() + edge, mScrollViewCustom.getScrollX());
        assertEquals(0, mScrollViewCustom.getScrollY());
    }

    @UiThreadTest
    @Test
    public void testRequestLayout() {
        mScrollViewCustom.requestLayout();

        assertTrue(mScrollViewCustom.isLayoutRequested());
    }

    @Test
    public void testFling() throws Throwable {
        mScrollViewCustom.setSmoothScrollingEnabled(true);
        assertEquals(0, mScrollViewCustom.getScrollX());

        final int velocityX = WidgetTestUtils.convertDipToPixels(mActivity, 2000);

        // fling towards right
        mActivityRule.runOnUiThread(() -> mScrollViewCustom.fling(velocityX));
        pollingCheckFling(0, true);

        final int currentX = mScrollViewCustom.getScrollX();
        // fling towards left
        mActivityRule.runOnUiThread(() -> mScrollViewCustom.fling(-velocityX));
        pollingCheckFling(currentX, false);
    }

    @UiThreadTest
    @Test
    public void testScrollTo() {
        mScrollViewCustom.setSmoothScrollingEnabled(false);

        mScrollViewCustom.scrollTo(10, 10);
        assertEquals(0, mScrollViewCustom.getScrollY());
        assertEquals(10, mScrollViewCustom.getScrollX());

        mScrollViewCustom.scrollTo(PAGE_WIDTH, PAGE_HEIGHT);
        assertEquals(0, mScrollViewCustom.getScrollY());
        assertEquals(PAGE_WIDTH, mScrollViewCustom.getScrollX());

        mScrollViewCustom.scrollTo(SCROLL_RIGHT, 0);
        assertEquals(0, mScrollViewCustom.getScrollY());
        assertEquals(SCROLL_RIGHT, mScrollViewCustom.getScrollX());

        // reach the top and left
        mScrollViewCustom.scrollTo(-10, -10);
        assertEquals(0, mScrollViewCustom.getScrollY());
        assertEquals(0, mScrollViewCustom.getScrollX());
    }

    @Test
    public void testGetHorizontalFadingEdgeStrengths() {
        MyHorizontalScrollView myScrollViewCustom = (MyHorizontalScrollView) mScrollViewCustom;

        assertTrue(mScrollViewCustom.getChildCount() > 0);
        assertTrue(myScrollViewCustom.getLeftFadingEdgeStrength() <= 1.0f);
        assertTrue(myScrollViewCustom.getLeftFadingEdgeStrength() >= 0.0f);
        assertTrue(myScrollViewCustom.getRightFadingEdgeStrength() <= 1.0f);
        assertTrue(myScrollViewCustom.getRightFadingEdgeStrength() >= 0.0f);

        MyHorizontalScrollView myScrollView = new MyHorizontalScrollView(mActivity);
        assertEquals(0, myScrollView.getChildCount());
        assertTrue(myScrollViewCustom.getLeftFadingEdgeStrength() <= 1.0f);
        assertTrue(myScrollViewCustom.getLeftFadingEdgeStrength() >= 0.0f);
        assertTrue(myScrollViewCustom.getRightFadingEdgeStrength() <= 1.0f);
        assertTrue(myScrollViewCustom.getRightFadingEdgeStrength() >= 0.0f);
    }

    @UiThreadTest
    @Test
    public void testEdgeEffectColors() {
        int defaultColor = new EdgeEffect(mScrollViewRegular.getContext()).getColor();
        assertEquals(mScrollViewRegular.getLeftEdgeEffectColor(), defaultColor);
        assertEquals(mScrollViewRegular.getRightEdgeEffectColor(), defaultColor);

        mScrollViewRegular.setEdgeEffectColor(Color.BLUE);
        assertEquals(mScrollViewRegular.getLeftEdgeEffectColor(), Color.BLUE);
        assertEquals(mScrollViewRegular.getRightEdgeEffectColor(), Color.BLUE);

        mScrollViewRegular.setLeftEdgeEffectColor(Color.RED);
        assertEquals(mScrollViewRegular.getLeftEdgeEffectColor(), Color.RED);
        assertEquals(mScrollViewRegular.getRightEdgeEffectColor(), Color.BLUE);

        mScrollViewRegular.setRightEdgeEffectColor(Color.GREEN);
        assertEquals(mScrollViewRegular.getLeftEdgeEffectColor(), Color.RED);
        assertEquals(mScrollViewRegular.getRightEdgeEffectColor(), Color.GREEN);
    }

    @Test
    public void testStretchAtLeft() throws Throwable {
        // Make sure that the scroll view we care about is on screen and at the left:
        showOnlyStretch();

        NoReleaseEdgeEffect edgeEffect = new NoReleaseEdgeEffect(mActivity);
        mScrollViewStretch.mEdgeGlowLeft = edgeEffect;
        assertTrue(StretchEdgeUtil.dragStretches(
                mActivityRule,
                mScrollViewStretch,
                edgeEffect,
                300,
                0
        ));
    }

    @Test
    public void testStretchAtLeftAndCatch() throws Throwable {
        // Make sure that the scroll view we care about is on screen and at the top:
        showOnlyStretch();

        NoReleaseEdgeEffect edgeEffect = new NoReleaseEdgeEffect(mActivity);
        mScrollViewStretch.mEdgeGlowLeft = edgeEffect;
        assertTrue(StretchEdgeUtil.dragAndHoldKeepsStretch(
                mActivityRule,
                mScrollViewStretch,
                edgeEffect,
                300,
                0
        ));
    }

    @Test
    public void testStretchAtRight() throws Throwable {
        // Make sure that the scroll view we care about is on screen and at the left:
        showOnlyStretch();

        mActivityRule.runOnUiThread(() -> {
            // Scroll all the way to the right
            mScrollViewStretch.scrollToEnd();
        });

        NoReleaseEdgeEffect edgeEffect = new NoReleaseEdgeEffect(mActivity);
        mScrollViewStretch.mEdgeGlowRight = edgeEffect;
        assertTrue(StretchEdgeUtil.dragStretches(
                mActivityRule,
                mScrollViewStretch,
                edgeEffect,
                -300,
                0
        ));
    }

    @LargeTest
    @Test
    public void testStretchAtRightAndCatch() throws Throwable {
        // Make sure that the scroll view we care about is on screen and at the top:
        showOnlyStretch();

        mActivityRule.runOnUiThread(() -> {
            // Scroll all the way to the bottom
            mScrollViewStretch.scrollToEnd();
        });

        NoReleaseEdgeEffect edgeEffect = new NoReleaseEdgeEffect(mActivity);
        mScrollViewStretch.mEdgeGlowRight = edgeEffect;
        assertTrue(StretchEdgeUtil.dragAndHoldKeepsStretch(
                mActivityRule,
                mScrollViewStretch,
                edgeEffect,
                -300,
                0
        ));
    }

    @LargeTest
    @Test
    public void testRequestDisallowInterceptTouchEventNotCalled() throws Throwable {
        // Make sure that the scroll view we care about is on screen and at the top:
        showOnlyStretch();

        InterceptView interceptView = mActivity.findViewById(R.id.wrapped_stretch);

        NoReleaseEdgeEffect edgeEffect = new NoReleaseEdgeEffect(mActivity);
        mScrollViewStretch.mEdgeGlowLeft = edgeEffect;

        dragAndHoldExecute(
                mActivityRule,
                mScrollViewStretch,
                edgeEffect,
                300,
                0,
                () -> interceptView.requestDisallowInterceptCalled = false,
                null
        );

        mActivityRule.runOnUiThread(
                () -> assertFalse(interceptView.requestDisallowInterceptCalled)
        );

        mActivityRule.runOnUiThread(
                () -> assertFalse(interceptView.requestDisallowInterceptCalled)
        );
    }

    /**
     * Verify that the SurfaceView is at the correct location during the overscroll stretch
     */
    @Test
    public void testSurfaceViewStretchAtEnd() throws Throwable {
        showOnlyStretch();

        mActivityRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                SurfaceView surfaceView = mActivity.findViewById(R.id.surfaceview_stretch_target);
                surfaceView.setBackgroundColor(Color.RED);
                surfaceView.setVisibility(View.VISIBLE);
            }
        });

        mActivityRule.runOnUiThread(() -> {
            // Scroll all the way to the end
            mScrollViewStretch.scrollToEnd();
        });

        NoReleaseEdgeEffect edgeEffect = new NoReleaseEdgeEffect(mActivity);

        mScrollViewStretch.mEdgeGlowRight = edgeEffect;

        StretchEdgeUtil.dragAndHoldExecute(
                mActivityRule,
                mScrollViewStretch,
                edgeEffect,
                -3000,
                0,
                null,
                () -> {
                    int[] coords = new int[2];
                    mScrollViewStretch.getLocationInWindow(coords);
                    Bitmap screenshot = getInstrumentation().getUiAutomation().takeScreenshot(
                            mActivityRule.getActivity().getWindow());

                    assertEquals(Color.RED, screenshot.getPixel(
                            coords[0] + mScrollViewStretch.getWidth() / 2,
                            coords[1] + mScrollViewStretch.getHeight() / 2));
                }
        );
    }

    @Test
    public void testSurfaceViewStretchAtEndWithScale() throws Throwable {
        showOnlyStretch();

        float scaleX = 2.0f;

        mActivityRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                SurfaceView surfaceView = mActivity.findViewById(R.id.surfaceview_stretch_target);
                surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
                    @Override
                    public void surfaceCreated(@NonNull SurfaceHolder holder) {
                        // no-op
                    }

                    @Override
                    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width,
                            int height) {
                        surfaceView.setScaleX(scaleX);
                        surfaceView.setPivotX(width);
                        surfaceView.setPivotY(height / 2f);
                        Surface surface = holder.getSurface();
                        Paint paint = new Paint();
                        Canvas canvas = surface.lockHardwareCanvas();
                        paint.setColor(Color.RED);
                        canvas.drawRect(0f, 0f, width / 2f, height / 2f, paint);
                        paint.setColor(Color.YELLOW);
                        canvas.drawRect(width / 2f, 0f, width, height / 2f, paint);
                        paint.setColor(Color.BLUE);
                        canvas.drawRect(0f, height / 2f, width / 2f, height, paint);
                        paint.setColor(Color.BLACK);
                        canvas.drawRect(width / 2f, height / 2f, width, height, paint);
                        surface.unlockCanvasAndPost(canvas);

                    }

                    @Override
                    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                        // no-op
                    }
                });
                surfaceView.setVisibility(View.VISIBLE);

            }
        });

        mActivityRule.runOnUiThread(() -> {
            // Scroll all the way to the end
            mScrollViewStretch.scrollToEnd();
        });

        NoReleaseEdgeEffect edgeEffect = new NoReleaseEdgeEffect(mActivity);

        mScrollViewStretch.mEdgeGlowRight = edgeEffect;

        StretchEdgeUtil.dragAndHoldExecute(
                mActivityRule,
                mScrollViewStretch,
                edgeEffect,
                -3000,
                0,
                null,
                () -> {
                    int[] coords = new int[2];
                    mScrollViewStretch.getLocationInWindow(coords);
                    Bitmap screenshot = getInstrumentation().getUiAutomation().takeScreenshot(
                            mActivityRule.getActivity().getWindow());

                    int containerWidth = mScrollViewStretch.getWidth();
                    int containerHeight = mScrollViewStretch.getHeight();

                    int topLeftColor = screenshot.getPixel(
                            coords[0] + containerWidth / 4,
                            coords[1] + containerHeight / 4);
                    int topRightColor = screenshot.getPixel(
                            coords[0] + containerWidth / 2 + containerWidth / 4,
                            coords[1] + containerHeight / 4);
                    int bottomLeftColor = screenshot.getPixel(
                            coords[0] + containerWidth / 4,
                            coords[1] + containerHeight / 2 + containerHeight / 4);
                    int bottomRightColor = screenshot.getPixel(
                            coords[0] + containerWidth / 2 + containerWidth / 4,
                            coords[1] + containerHeight / 2 + containerHeight / 4);

                    assertEquals(Color.RED, topLeftColor);
                    assertEquals(Color.YELLOW, topRightColor);
                    assertEquals(Color.BLUE, bottomLeftColor);
                    assertEquals(Color.BLACK, bottomRightColor);
                }
        );
    }

    @Test
    public void testFlingWhileStretchedLeft() throws Throwable {
        // Make sure that the scroll view we care about is on screen and at the top:
        showOnlyStretch();

        ScrollViewTest.CaptureOnAbsorbEdgeEffect
                edgeEffect = new ScrollViewTest.CaptureOnAbsorbEdgeEffect(mActivity);
        mScrollViewStretch.mEdgeGlowLeft = edgeEffect;
        fling(mActivityRule, mScrollViewStretch, 300, 0);
        assertTrue("Expecting greater than 0, but was " + edgeEffect.onAbsorbVelocity,
                edgeEffect.onAbsorbVelocity > 0);
    }

    @Test
    public void testFlingWhileStretchedRight() throws Throwable {
        // Make sure that the scroll view we care about is on screen and at the top:
        showOnlyStretch();

        mActivityRule.runOnUiThread(() -> {
            // Scroll all the way to the bottom
            mScrollViewStretch.scrollToEnd();
        });

        ScrollViewTest.CaptureOnAbsorbEdgeEffect
                edgeEffect = new ScrollViewTest.CaptureOnAbsorbEdgeEffect(mActivity);
        mScrollViewStretch.mEdgeGlowRight = edgeEffect;
        fling(mActivityRule, mScrollViewStretch, -300, 0);
        assertTrue("Expecting greater than 0, but was " + edgeEffect.onAbsorbVelocity,
                edgeEffect.onAbsorbVelocity > 0);
    }

    @Test
    public void scrollFromRotaryStretchesLeft() throws Throwable {
        showOnlyStretch();

        ScrollViewTest.CaptureOnReleaseEdgeEffect
                edgeEffect = new ScrollViewTest.CaptureOnReleaseEdgeEffect(mActivity);
        mScrollViewStretch.mEdgeGlowLeft = edgeEffect;

        mActivityRule.runOnUiThread(() -> {
            assertTrue(mScrollViewStretch.dispatchGenericMotionEvent(
                    createScrollEvent(-2f, InputDevice.SOURCE_ROTARY_ENCODER)));
            assertFalse(edgeEffect.isFinished());
            assertTrue(edgeEffect.getDistance() > 0f);
            assertTrue(edgeEffect.onReleaseCalled);
        });
    }

    @Test
    public void scrollFromMouseDoesNotStretchLeft() throws Throwable {
        showOnlyStretch();

        ScrollViewTest.CaptureOnReleaseEdgeEffect
                edgeEffect = new ScrollViewTest.CaptureOnReleaseEdgeEffect(mActivity);
        mScrollViewStretch.mEdgeGlowLeft = edgeEffect;

        mActivityRule.runOnUiThread(() -> {
            assertFalse(mScrollViewStretch.dispatchGenericMotionEvent(
                    createScrollEvent(-2f, InputDevice.SOURCE_MOUSE)));
            assertTrue(edgeEffect.isFinished());
            assertFalse(edgeEffect.onReleaseCalled);
        });
    }

    @Test
    public void scrollFromRotaryStretchesRight() throws Throwable {
        showOnlyStretch();

        mActivityRule.runOnUiThread(() -> {
            // Scroll all the way to the Right
            mScrollViewStretch.scrollToEnd();
        });

        ScrollViewTest.CaptureOnReleaseEdgeEffect
                edgeEffect = new ScrollViewTest.CaptureOnReleaseEdgeEffect(mActivity);
        mScrollViewStretch.mEdgeGlowRight = edgeEffect;

        mActivityRule.runOnUiThread(() -> {
            assertTrue(mScrollViewStretch.dispatchGenericMotionEvent(
                    createScrollEvent(2f, InputDevice.SOURCE_ROTARY_ENCODER)));
            assertFalse(edgeEffect.isFinished());
            assertTrue(edgeEffect.getDistance() > 0f);
            assertTrue(edgeEffect.onReleaseCalled);
        });
    }

    @Test
    public void scrollFromMouseDoesNotStretchRight() throws Throwable {
        showOnlyStretch();

        mActivityRule.runOnUiThread(() -> {
            // Scroll all the way to the Right
            mScrollViewStretch.scrollToEnd();
        });

        ScrollViewTest.CaptureOnReleaseEdgeEffect
                edgeEffect = new ScrollViewTest.CaptureOnReleaseEdgeEffect(mActivity);
        mScrollViewStretch.mEdgeGlowRight = edgeEffect;

        mActivityRule.runOnUiThread(() -> {
            assertFalse(mScrollViewStretch.dispatchGenericMotionEvent(
                    createScrollEvent(2f, InputDevice.SOURCE_MOUSE)));
            assertTrue(edgeEffect.isFinished());
            assertFalse(edgeEffect.onReleaseCalled);
        });
    }

    @Test
    public void flingLeftWhileStretchedAtLeft() throws Throwable {
        showOnlyStretch();
        NoReleaseEdgeEffect edgeEffect = new NoReleaseEdgeEffect(mActivity);

        mScrollViewStretch.mEdgeGlowLeft = edgeEffect;

        StretchEdgeUtil.dragAndHoldExecute(
                mActivityRule,
                mScrollViewStretch,
                edgeEffect,
                3000,
                0,
                null,
                () -> assertNotEquals(0f, edgeEffect.getDistance())
        );

        mActivityRule.runOnUiThread(() -> {
            edgeEffect.setOnReleaseCalled(false);
            assertEquals(0, mScrollViewStretch.getScrollX());
            mScrollViewStretch.fling(10000);
            assertFalse(edgeEffect.getOnReleaseCalled());
            assertNotEquals(0f, edgeEffect.getDistance());
            assertEquals(0, mScrollViewStretch.getScrollX());
        });

        PollingCheck.waitFor(1000L, () -> edgeEffect.getDistance() == 0);
        PollingCheck.waitFor(1000L, () -> mScrollViewStretch.getScrollX() != 0);
    }

    @Test
    public void flingRightWhileStretchedAtRight() throws Throwable {
        showOnlyStretch();
        mActivityRule.runOnUiThread(() -> {
            // Scroll all the way to the bottom
            mScrollViewStretch.scrollToEnd();
            assertEquals(420, mScrollViewStretch.getScrollX());
        });

        NoReleaseEdgeEffect edgeEffect = new NoReleaseEdgeEffect(mActivity);

        mScrollViewStretch.mEdgeGlowRight = edgeEffect;

        StretchEdgeUtil.dragAndHoldExecute(
                mActivityRule,
                mScrollViewStretch,
                edgeEffect,
                -3000,
                0,
                null,
                () -> assertNotEquals(0f, edgeEffect.getDistance())
        );

        mActivityRule.runOnUiThread(() -> {
            edgeEffect.setOnReleaseCalled(false);
            assertEquals(420, mScrollViewStretch.getScrollX());
            mScrollViewStretch.fling(-10000);
            assertFalse(edgeEffect.getOnReleaseCalled());
            assertNotEquals(0f, edgeEffect.getDistance());
            assertEquals(420, mScrollViewStretch.getScrollX());
        });

        PollingCheck.waitFor(1000L, () -> edgeEffect.getDistance() == 0);
        PollingCheck.waitFor(1000L, () -> mScrollViewStretch.getScrollX() != 420);
    }

    private MotionEvent createScrollEvent(float scrollAmount, int source) {
        MotionEvent.PointerProperties pointerProperties = new MotionEvent.PointerProperties();
        pointerProperties.toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords pointerCoords = new MotionEvent.PointerCoords();
        int axis = source == InputDevice.SOURCE_ROTARY_ENCODER ? MotionEvent.AXIS_SCROLL
                : MotionEvent.AXIS_HSCROLL;
        pointerCoords.setAxisValue(axis, scrollAmount);

        return MotionEvent.obtain(
                0, /* downTime */
                0, /* eventTime */
                MotionEvent.ACTION_SCROLL, /* action */
                1, /* pointerCount */
                new MotionEvent.PointerProperties[] { pointerProperties },
                new MotionEvent.PointerCoords[] { pointerCoords },
                0, /* metaState */
                0, /* buttonState */
                0f, /* xPrecision */
                0f, /* yPrecision */
                0, /* deviceId */
                0, /* edgeFlags */
                source, /* source */
                0 /* flags */
        );
    }

    private void showOnlyStretch() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            mScrollViewCustom.setVisibility(View.GONE);
            mScrollViewCustomEmpty.setVisibility(View.GONE);
            mScrollViewRegular.setVisibility(View.GONE);
            mSurfaceView.setVisibility(View.GONE);
            // The stretch HorizontalScrollView is 180x180 pixels
            Rect exclusionRect = new Rect(0, 0, 180, 180);
            ArrayList exclusionRects = new ArrayList();
            exclusionRects.add(exclusionRect);
            mScrollViewStretch.setSystemGestureExclusionRects(exclusionRects);
            mActivity.findViewById(R.id.wrapped_stretch)
                    .setSystemGestureExclusionRects(exclusionRects);
        });
    }

    private boolean isInRange(int current, int from, int to) {
        if (from < to) {
            return current >= from && current <= to;
        }
        return current <= from && current >= to;
    }

    private void pollingCheckSmoothScrolling(final int fromX, final int toX,
            final int fromY, final int toY) {

        if (fromX == toX && fromY == toY) {
            return;
        }

        if (fromY != toY) {
            PollingCheck.waitFor(() -> isInRange(mScrollViewCustom.getScrollY(), fromY, toY));
        }

        if (fromX != toX) {
            PollingCheck.waitFor(() -> isInRange(mScrollViewCustom.getScrollX(), fromX, toX));
        }

        PollingCheck.waitFor(
                () -> toX == mScrollViewCustom.getScrollX() && toY == mScrollViewCustom.getScrollY());
    }

    private void pollingCheckFling(final int startPosition, final boolean movingRight) {
        PollingCheck.waitFor(() -> {
            if (movingRight) {
                return mScrollViewCustom.getScrollX() > startPosition;
            }
            return mScrollViewCustom.getScrollX() < startPosition;
        });

        final int[] previousScrollX = new int[] { mScrollViewCustom.getScrollX() };
        PollingCheck.waitFor(() -> {
            if (mScrollViewCustom.getScrollX() == previousScrollX[0]) {
                return true;
            } else {
                previousScrollX[0] = mScrollViewCustom.getScrollX();
                return false;
            }
        });
    }

    public static class MyView extends View {
        // measure in this height if set
        private Integer mDesiredWidth;
        public MyView(Context context) {
            super(context);
        }

        public void setDesiredWidth(Integer desiredWidth) {
            mDesiredWidth = desiredWidth;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            if (mDesiredWidth != null) {
                int mode = MeasureSpec.getMode(widthMeasureSpec);
                int size = MeasureSpec.getSize(widthMeasureSpec);
                int newWidth = size;
                if (mode == MeasureSpec.AT_MOST) {
                    newWidth = Math.max(size, mDesiredWidth);
                } else if (mode == MeasureSpec.UNSPECIFIED) {
                    newWidth = mDesiredWidth;
                }
                setMeasuredDimension(newWidth, getMeasuredHeight());
            }
        }
    }

    public static class MyHorizontalScrollView extends HorizontalScrollView {
        public MyHorizontalScrollView(Context context) {
            super(context);
        }

        public MyHorizontalScrollView(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public MyHorizontalScrollView(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
        }

        @Override
        protected int computeHorizontalScrollRange() {
            return super.computeHorizontalScrollRange();
        }

        @Override
        protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
            return super.computeScrollDeltaToGetChildRectOnScreen(rect);
        }

        @Override
        protected float getLeftFadingEdgeStrength() {
            return super.getLeftFadingEdgeStrength();
        }

        @Override
        protected float getRightFadingEdgeStrength() {
            return super.getRightFadingEdgeStrength();
        }

        @Override
        protected void measureChild(View child, int parentWidthMeasureSpec,
                int parentHeightMeasureSpec) {
            super.measureChild(child, parentWidthMeasureSpec, parentHeightMeasureSpec);
        }

        @Override
        protected void measureChildWithMargins(View child, int parentWidthMeasureSpec, int widthUsed,
                int parentHeightMeasureSpec, int heightUsed) {
            super.measureChildWithMargins(child, parentWidthMeasureSpec,
                    widthUsed, parentHeightMeasureSpec, heightUsed);
        }

        @Override
        public int computeVerticalScrollRange() {
            return super.computeVerticalScrollRange();
        }

        @Override
        public int computeVerticalScrollOffset() {
            return super.computeVerticalScrollOffset();
        }

        @Override
        public int computeVerticalScrollExtent() {
            return super.computeVerticalScrollExtent();
        }

        public void scrollToEnd() {
            scrollTo(computeHorizontalScrollRange(), 0);
        }
    }

    public static class InterceptView extends FrameLayout {
        public boolean requestDisallowInterceptCalled = false;

        public InterceptView(Context context) {
            super(context);
        }

        public InterceptView(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public InterceptView(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
        }

        @Override
        public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            requestDisallowInterceptCalled = true;
            super.requestDisallowInterceptTouchEvent(disallowIntercept);
        }
    }
}
