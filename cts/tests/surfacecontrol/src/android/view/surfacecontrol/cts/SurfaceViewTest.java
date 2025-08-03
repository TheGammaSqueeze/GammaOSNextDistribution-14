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

package android.view.surfacecontrol.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.Region;
import android.platform.test.annotations.Presubmit;
import android.server.wm.CtsWindowInfoUtils;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;

import androidx.test.annotation.UiThreadTest;
import androidx.test.filters.MediumTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.WidgetTestUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

@Presubmit
@MediumTest
@RunWith(AndroidJUnit4.class)
public class SurfaceViewTest {
    private SurfaceViewCtsActivity mActivity;
    private SurfaceViewCtsActivity.MockSurfaceView mMockSurfaceView;

    @Rule
    public ActivityTestRule<SurfaceViewCtsActivity> mActivityRule =
            new ActivityTestRule<>(SurfaceViewCtsActivity.class);

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
        mMockSurfaceView = mActivity.getSurfaceView();
        CtsWindowInfoUtils.waitForWindowFocus(mMockSurfaceView, true);
    }

    @UiThreadTest
    @Test
    public void testConstructor() {
        new SurfaceView(mActivity);
        new SurfaceView(mActivity, null);
        new SurfaceView(mActivity, null, 0);
    }

    @Test
    public void testSurfaceView() {
        final int left = 40;
        final int top = 30;
        final int right = 320;
        final int bottom = 240;

        assertTrue(mMockSurfaceView.isDraw());
        assertTrue(mMockSurfaceView.isOnAttachedToWindow());
        assertTrue(mMockSurfaceView.isDispatchDraw());
        assertTrue(mMockSurfaceView.isSurfaceCreatedCalled());
        assertTrue(mMockSurfaceView.isSurfaceChanged());

        assertTrue(mMockSurfaceView.isOnWindowVisibilityChanged());
        int expectedVisibility = mMockSurfaceView.getVisibility();
        int actualVisibility = mMockSurfaceView.getVInOnWindowVisibilityChanged();
        assertEquals(expectedVisibility, actualVisibility);

        assertTrue(mMockSurfaceView.isOnMeasureCalled());
        int expectedWidth = mMockSurfaceView.getMeasuredWidth();
        int expectedHeight = mMockSurfaceView.getMeasuredHeight();
        int actualWidth = mMockSurfaceView.getWidthInOnMeasure();
        int actualHeight = mMockSurfaceView.getHeightInOnMeasure();
        assertEquals(expectedWidth, actualWidth);
        assertEquals(expectedHeight, actualHeight);

        Region region = new Region();
        region.set(left, top, right, bottom);
        assertTrue(mMockSurfaceView.gatherTransparentRegion(region));

        mMockSurfaceView.setFormat(PixelFormat.TRANSPARENT);
        assertFalse(mMockSurfaceView.gatherTransparentRegion(region));

        SurfaceHolder actual = mMockSurfaceView.getHolder();
        assertNotNull(actual);
        assertTrue(actual instanceof SurfaceHolder);
    }

    /**
     * check point:
     * check surfaceView size before and after layout
     */
    @UiThreadTest
    @Test
    public void testOnSizeChanged() {
        final int left = 40;
        final int top = 30;
        final int right = 320;
        final int bottom = 240;

        // change the SurfaceView size
        int beforeLayoutWidth = mMockSurfaceView.getWidth();
        int beforeLayoutHeight = mMockSurfaceView.getHeight();
        mMockSurfaceView.resetOnSizeChangedFlag(false);
        assertFalse(mMockSurfaceView.isOnSizeChangedCalled());
        mMockSurfaceView.layout(left, top, right, bottom);
        assertTrue(mMockSurfaceView.isOnSizeChangedCalled());
        assertEquals(beforeLayoutWidth, mMockSurfaceView.getOldWidth());
        assertEquals(beforeLayoutHeight, mMockSurfaceView.getOldHeight());
        assertEquals(right - left, mMockSurfaceView.getWidth());
        assertEquals(bottom - top, mMockSurfaceView.getHeight());
    }

    /**
     * check point:
     * check surfaceView scroll X and y before and after scrollTo
     */
    @UiThreadTest
    @Test
    public void testOnScrollChanged() {
        final int scrollToX = 200;
        final int scrollToY = 200;

        int oldHorizontal = mMockSurfaceView.getScrollX();
        int oldVertical = mMockSurfaceView.getScrollY();
        assertFalse(mMockSurfaceView.isOnScrollChanged());
        mMockSurfaceView.scrollTo(scrollToX, scrollToY);
        assertTrue(mMockSurfaceView.isOnScrollChanged());
        assertEquals(oldHorizontal, mMockSurfaceView.getOldHorizontal());
        assertEquals(oldVertical, mMockSurfaceView.getOldVertical());
        assertEquals(scrollToX, mMockSurfaceView.getScrollX());
        assertEquals(scrollToY, mMockSurfaceView.getScrollY());
    }

    @Test
    public void testOnDetachedFromWindow() {
        assertFalse(mMockSurfaceView.isDetachedFromWindow());
        assertTrue(mMockSurfaceView.isShown());
        mActivityRule.finishActivity();
        PollingCheck.waitFor(() -> mMockSurfaceView.isDetachedFromWindow() &&
                !mMockSurfaceView.isShown());
    }

    @Test
    public void surfaceInvalidatedWhileDetaching() throws Throwable {
        assertTrue(mMockSurfaceView.mSurface.isValid());
        assertFalse(mMockSurfaceView.isDetachedFromWindow());
        WidgetTestUtils.runOnMainAndLayoutSync(mActivityRule, () -> {
            ((ViewGroup)mMockSurfaceView.getParent()).removeView(mMockSurfaceView);
        }, false);
        assertTrue(mMockSurfaceView.isDetachedFromWindow());
        assertFalse(mMockSurfaceView.mSurface.isValid());
    }

    @Test
    public void testSurfaceRemainsValidWhileCanvasLocked() throws Throwable {
        assertFalse(mMockSurfaceView.isDetachedFromWindow());
        assertTrue(mMockSurfaceView.isShown());
        mMockSurfaceView.awaitSurfaceCreated(3, TimeUnit.SECONDS);
        assertTrue(mMockSurfaceView.isSurfaceCreatedCalled());
        Canvas canvas = mMockSurfaceView.getHolder().lockCanvas();
        assertNotNull(canvas);
        // Try to detach the surfaceview. Since the surface is locked by the lock canvas called,
        // the surface will remain valid.
        mActivityRule.getActivity().runOnUiThread(() ->
                ((ViewGroup) mMockSurfaceView.getParent()).removeView(mMockSurfaceView));
        assertTrue(mMockSurfaceView.mSurface.isValid());
        mMockSurfaceView.getHolder().unlockCanvasAndPost(canvas);
        PollingCheck.waitFor(() -> mMockSurfaceView.isDetachedFromWindow()
                && !mMockSurfaceView.isShown());
    }
}
