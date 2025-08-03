/*
 * Copyright 2019 The Android Open Source Project
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

import static android.server.wm.ActivityManagerTestBase.createFullscreenActivityScenarioRule;
import static android.server.wm.BuildUtils.HW_TIMEOUT_MULTIPLIER;
import static android.view.cts.surfacevalidator.BitmapPixelChecker.getInsets;
import static android.view.cts.surfacevalidator.BitmapPixelChecker.validateScreenshot;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.server.wm.CtsWindowInfoUtils;
import android.view.Gravity;
import android.view.SurfaceControl;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.cts.surfacevalidator.BitmapPixelChecker;
import android.widget.FrameLayout;

import androidx.test.ext.junit.rules.ActivityScenarioRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SurfaceViewSurfaceValidatorTest {
    private static final int DEFAULT_LAYOUT_WIDTH = 100;
    private static final int DEFAULT_LAYOUT_HEIGHT = 100;

    private static final long WAIT_TIMEOUT_S = 5L * HW_TIMEOUT_MULTIPLIER;

    @Rule
    public final ActivityScenarioRule<TestActivity> mActivityRule =
                createFullscreenActivityScenarioRule(TestActivity.class);

    @Rule
    public TestName mName = new TestName();
    private TestActivity mActivity;

    private ViewGroup mRootView;

    @Before
    public void setup() {
        mActivityRule.getScenario().onActivity(activity -> mActivity = activity);
    }

    @After
    public void tearDown() {
        mActivity.runOnUiThread(() -> {
            if (mRootView != null) {
                mRootView.removeAllViews();
            }
        });
    }

    private static class SurfaceFiller implements SurfaceHolder.Callback {
        final SurfaceView mSurfaceView;
        private final CountDownLatch mCountDownLatch;

        SurfaceFiller(Context c, CountDownLatch countDownLatch) {
            mSurfaceView = new SurfaceView(c);
            mSurfaceView.getHolder().addCallback(this);
            mCountDownLatch = countDownLatch;
        }

        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            SurfaceControl.Transaction t = new SurfaceControl.Transaction();
            t.addTransactionCommittedListener(Runnable::run, mCountDownLatch::countDown);
            mSurfaceView.applyTransactionToFrame(t);
            Canvas canvas = holder.lockHardwareCanvas();
            canvas.drawColor(Color.GREEN);
            holder.unlockCanvasAndPost(canvas);
        }

        public void surfaceCreated(SurfaceHolder holder) {
        }

        public void surfaceDestroyed(SurfaceHolder holder) {
        }
    }

    /**
     * Verify that showing a SurfaceView on top but not drawing in to it will not produce a background.
     */
    @Test
    public void testOnTopHasNoBackground() throws Throwable {
        // Wait for a frame that contains the SurfaceView to be sent to SF
        CountDownLatch readyLatch = new CountDownLatch(1);
        mActivity.runOnUiThread(() -> addSurfaceView(readyLatch));

        assertTrue("Failed to wait for content to be added",
                readyLatch.await(WAIT_TIMEOUT_S, TimeUnit.SECONDS));
        assertTrue("Failed to wait for content to become visible",
                CtsWindowInfoUtils.waitForWindowVisible(mRootView));

        BitmapPixelChecker pixelChecker = new BitmapPixelChecker(Color.BLACK,
                new Rect() /* boundsToLog */);
        validateScreenshot(mName, mActivity, pixelChecker, 0 /* expectedMatchingPixels */,
                getInsets(mActivity));
    }

    // Here we add a second translucent surface view and verify that the background
    // is behind all SurfaceView (e.g. the first is not obscured)
    @Test
    public void testBackgroundIsBehindAllSurfaceView() throws Throwable {
        // Wait for 2 things before proceeding with the test
        // 1. Frame to be drawn for main window that includes the SurfaceView.
        // 2. Submit buffer for second SurfaceView
        CountDownLatch readyLatch = new CountDownLatch(2);
        mActivity.runOnUiThread(() -> {
            addSurfaceView(readyLatch);
            SurfaceFiller sc = new SurfaceFiller(mActivity, readyLatch);
            mRootView.addView(sc.mSurfaceView,
                    new FrameLayout.LayoutParams(DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                            Gravity.LEFT | Gravity.TOP));
        });
        assertTrue("Failed to wait for content to be added",
                readyLatch.await(WAIT_TIMEOUT_S, TimeUnit.SECONDS));

        assertTrue("Failed to wait for content to become visible",
                CtsWindowInfoUtils.waitForWindowVisible(mRootView));

        BitmapPixelChecker pixelChecker = new BitmapPixelChecker(Color.BLACK,
                new Rect() /* boundsToLog */);
        validateScreenshot(mName, mActivity, pixelChecker, 0 /* expectedMatchingPixels */,
                getInsets(mActivity));
    }

    private void addSurfaceView(CountDownLatch countDownLatch) {
        SurfaceView surfaceView = new SurfaceView(mActivity);
        surfaceView.setZOrderOnTop(true);
        mRootView = new FrameLayout(mActivity);
        mRootView.setBackgroundColor(Color.RED);
        mRootView.addView(surfaceView,
                new FrameLayout.LayoutParams(DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                        Gravity.LEFT | Gravity.TOP));
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        mActivity.setContentView(mRootView, layoutParams);

        Runnable runnable = () -> {
            SurfaceControl.Transaction t = new SurfaceControl.Transaction();
            t.addTransactionCommittedListener(Runnable::run, countDownLatch::countDown);
            mRootView.getRootSurfaceControl().applyTransactionOnDraw(t);
        };

        if (mRootView.isAttachedToWindow()) {
            runnable.run();
        } else {
            mRootView.getViewTreeObserver().addOnWindowAttachListener(
                    new ViewTreeObserver.OnWindowAttachListener() {
                        @Override
                        public void onWindowAttached() {
                            runnable.run();
                        }

                        @Override
                        public void onWindowDetached() {
                        }
                    });
        }
    }
}
