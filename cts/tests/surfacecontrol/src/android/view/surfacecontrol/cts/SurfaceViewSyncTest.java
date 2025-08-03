/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static android.server.wm.BuildUtils.HW_TIMEOUT_MULTIPLIER;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.MediaPlayer;
import android.view.Gravity;
import android.view.SurfaceControl;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.view.cts.surfacevalidator.AnimationFactory;
import android.view.cts.surfacevalidator.AnimationTestCase;
import android.view.cts.surfacevalidator.CapturedActivityWithResource;
import android.view.cts.surfacevalidator.PixelChecker;
import android.view.cts.surfacevalidator.ViewFactory;
import android.widget.FrameLayout;

import androidx.test.filters.LargeTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class SurfaceViewSyncTest {
    private static final String TAG = "SurfaceViewSyncTests";

    private static final long WAIT_TIMEOUT_S = 5L * HW_TIMEOUT_MULTIPLIER;

    @Rule
    public ActivityTestRule<CapturedActivityWithResource> mActivityRule =
            new ActivityTestRule<>(CapturedActivityWithResource.class);

    @Rule
    public TestName mName = new TestName();

    private CapturedActivityWithResource mActivity;
    private MediaPlayer mMediaPlayer;

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
        mMediaPlayer = mActivity.getMediaPlayer();
    }

    private static ValueAnimator makeInfinite(ValueAnimator a) {
        a.setRepeatMode(ObjectAnimator.REVERSE);
        a.setRepeatCount(ObjectAnimator.INFINITE);
        a.setDuration(200);
        a.setInterpolator(new LinearInterpolator());
        return a;
    }

    ///////////////////////////////////////////////////////////////////////////
    // ViewFactories
    ///////////////////////////////////////////////////////////////////////////

    private final ViewFactory mEmptySurfaceViewFactory = context -> {
        SurfaceView surfaceView = new SurfaceView(context);

        // prevent transparent region optimization, which is invalid for a SurfaceView moving around
        surfaceView.setWillNotDraw(false);

        return surfaceView;
    };

    private final ViewFactory mGreenSurfaceViewFactory = new ViewFactory() {
        private final CountDownLatch mCountDownLatch = new CountDownLatch(1);

        @Override
        public View createView(Context context) {
            SurfaceView surfaceView = new SurfaceView(context);

            // prevent transparent region optimization, which is invalid for a SurfaceView moving
            // around
            surfaceView.setWillNotDraw(false);

            surfaceView.getHolder().setFixedSize(640, 480);
            surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {}

                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width,
                        int height) {
                    Canvas canvas = holder.lockCanvas();
                    canvas.drawColor(Color.GREEN);
                    holder.unlockCanvasAndPost(canvas);
                    mCountDownLatch.countDown();
                }

                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {}
            });
            return surfaceView;
        }

        @Override
        public boolean waitForReady() {
            try {
                return mCountDownLatch.await(WAIT_TIMEOUT_S, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                return false;
            }
        }
    };

    private final ViewFactory mVideoViewFactory = new ViewFactory() {
        private final CountDownLatch mCountDownLatch = new CountDownLatch(1);

        @Override
        public View createView(Context context) {
            SurfaceView surfaceView = new SurfaceView(context);

            // prevent transparent region optimization, which is invalid for a SurfaceView moving
            // around
            surfaceView.setWillNotDraw(false);

            surfaceView.getHolder().setFixedSize(640, 480);
            surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    // When using MediaPlayer, we need to wait until the first frame is drawn since
                    // it can be rendered asynchronously. Merge a commit callback transaction so
                    // the next draw into the SV gets the callback invoked when content is rendered
                    // on screen. This is a good signal to know when the test can start.
                    SurfaceControl.Transaction t = new SurfaceControl.Transaction();
                    t.addTransactionCommittedListener(Runnable::run, mCountDownLatch::countDown);
                    surfaceView.applyTransactionToFrame(t);

                    mMediaPlayer.setSurface(holder.getSurface());
                    mMediaPlayer.start();
                }

                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width,
                        int height) {
                }

                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {
                    mMediaPlayer.pause();
                    mMediaPlayer.setSurface(null);
                }
            });
            return surfaceView;
        }

        @Override
        public boolean waitForReady() {
            try {
                return mCountDownLatch.await(WAIT_TIMEOUT_S, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                return false;
            }
        }
    };

    private final PixelChecker mBlackPixelChecker = new PixelChecker() {
        @Override
        public boolean checkPixels(int blackishPixelCount, int width, int height) {
            return blackishPixelCount == 0;
        }
    };

    ///////////////////////////////////////////////////////////////////////////
    // AnimationFactories
    ///////////////////////////////////////////////////////////////////////////

    private final AnimationFactory mSmallScaleAnimationFactory = view -> {
        view.setPivotX(0);
        view.setPivotY(0);
        PropertyValuesHolder pvhX = PropertyValuesHolder.ofFloat(View.SCALE_X, 0.01f, 1f);
        PropertyValuesHolder pvhY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.01f, 1f);
        return makeInfinite(ObjectAnimator.ofPropertyValuesHolder(view, pvhX, pvhY));
    };

    private final AnimationFactory mBigScaleAnimationFactory = view -> {
        view.setTranslationX(10);
        view.setTranslationY(10);
        view.setPivotX(0);
        view.setPivotY(0);
        PropertyValuesHolder pvhX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 3f);
        PropertyValuesHolder pvhY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 3f);
        return makeInfinite(ObjectAnimator.ofPropertyValuesHolder(view, pvhX, pvhY));
    };

    private static final AnimationFactory sFixedSizeWithViewSizeAnimationFactory = view -> {
        ValueAnimator anim = ValueAnimator.ofInt(0, 100);
        anim.addUpdateListener(valueAnimator -> {
            ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
            layoutParams.width++;
            if (layoutParams.height++ > 1500) {
                layoutParams.height = 320;
                layoutParams.width = 240;
            }
            view.setLayoutParams(layoutParams);

            if ((Integer) valueAnimator.getAnimatedValue() % 3 == 0) {
                ((SurfaceView) view).getHolder().setFixedSize(320, 240);
            } else if ((Integer) valueAnimator.getAnimatedValue() % 7 == 0) {
                ((SurfaceView) view).getHolder().setFixedSize(1280, 960);
            }
        });
        return makeInfinite(anim);
    };

    private static final AnimationFactory sFixedSizeAnimationFactory = view -> {
        ValueAnimator anim = ValueAnimator.ofInt(0, 100);
        anim.addUpdateListener(valueAnimator -> {
            if ((Integer) valueAnimator.getAnimatedValue() % 2 == 0) {
                ((SurfaceView) view).getHolder().setFixedSize(320, 240);
            } else {
                ((SurfaceView) view).getHolder().setFixedSize(1280, 960);
            }
        });
        return makeInfinite(anim);
    };

    private final AnimationFactory mTranslateAnimationFactory = view -> {
        PropertyValuesHolder pvhX = PropertyValuesHolder.ofFloat(View.TRANSLATION_X, 10f, 30f);
        PropertyValuesHolder pvhY = PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 10f, 30f);
        return makeInfinite(ObjectAnimator.ofPropertyValuesHolder(view, pvhX, pvhY));
    };

    ///////////////////////////////////////////////////////////////////////////
    // Tests
    ///////////////////////////////////////////////////////////////////////////

    /** Draws a moving 10x10 black rectangle, validates 100 pixels of black are seen each frame */
    @Test
    public void testSmallRect() throws Throwable {
        mActivity.verifyTest(new AnimationTestCase(context -> new View(context) {
            // draw a single pixel
            final Paint mBlackPaint = new Paint();

            @Override
            protected void onDraw(Canvas canvas) {
                canvas.drawRect(0, 0, 10, 10, mBlackPaint);
            }

            @SuppressWarnings("unused")
            void setOffset(int offset) {
                // Note: offset by integer values, to ensure no rounding
                // is done in rendering layer, as that may be brittle
                setTranslationX(offset);
                setTranslationY(offset);
            }
        }, new FrameLayout.LayoutParams(100, 100, Gravity.LEFT | Gravity.TOP),
                view -> makeInfinite(ObjectAnimator.ofInt(view, "offset", 10, 30)),
                new PixelChecker() {
                    @Override
                    public boolean checkPixels(int blackishPixelCount, int width, int height) {
                        return blackishPixelCount >= 90 && blackishPixelCount <= 110;
                    }
                }), mName);
    }

    /**
     * Verifies that a SurfaceView without a surface is entirely black, with pixel count being
     * approximate to avoid rounding brittleness.
     */
    @Test
    public void testEmptySurfaceView() throws Throwable {
        mActivity.verifyTest(new AnimationTestCase(
                mEmptySurfaceViewFactory,
                new FrameLayout.LayoutParams(100, 100, Gravity.LEFT | Gravity.TOP),
                mTranslateAnimationFactory,
                new PixelChecker() {
                    @Override
                    public boolean checkPixels(int blackishPixelCount, int width, int height) {
                        return blackishPixelCount > 9000 && blackishPixelCount < 11000;
                    }
                }), mName);
    }

    @Test
    public void testSurfaceViewSmallScale() throws Throwable {
        mActivity.verifyTest(new AnimationTestCase(
                mGreenSurfaceViewFactory,
                new FrameLayout.LayoutParams(320, 240, Gravity.LEFT | Gravity.TOP),
                mSmallScaleAnimationFactory,
                new PixelChecker() {
                    @Override
                    public boolean checkPixels(int blackishPixelCount, int width, int height) {
                        return blackishPixelCount == 0;
                    }
                }), mName);
    }

    @Test
    public void testSurfaceViewBigScale() throws Throwable {
        mActivity.verifyTest(new AnimationTestCase(
                mGreenSurfaceViewFactory,
                new FrameLayout.LayoutParams(640, 480, Gravity.LEFT | Gravity.TOP),
                mBigScaleAnimationFactory, mBlackPixelChecker), mName);
    }


    /**
     * Change requested surface size and SurfaceView size and verify buffers always fill to
     * SurfaceView size. b/190449942
     */
    @Test
    public void testSurfaceViewFixedSizeWithViewSizeChanges() throws Throwable {
        mActivity.verifyTest(new AnimationTestCase(
                mVideoViewFactory,
                new FrameLayout.LayoutParams(640, 480, Gravity.LEFT | Gravity.TOP),
                sFixedSizeWithViewSizeAnimationFactory, mBlackPixelChecker), mName);
    }

    /**
     * Change requested surface size and verify buffers always fill to SurfaceView size.
     * b/194458377
     */
    @Test
    public void testSurfaceViewFixedSizeChanges() throws Throwable {
        mActivity.verifyTest(new AnimationTestCase(
                mVideoViewFactory,
                new FrameLayout.LayoutParams(640, 480, Gravity.LEFT | Gravity.TOP),
                sFixedSizeAnimationFactory, mBlackPixelChecker), mName);
    }

    @Test
    public void testVideoSurfaceViewTranslate() throws Throwable {
        mActivity.verifyTest(new AnimationTestCase(
                mVideoViewFactory,
                new FrameLayout.LayoutParams(640, 480, Gravity.LEFT | Gravity.TOP),
                mTranslateAnimationFactory, mBlackPixelChecker), mName);
    }

    @Test
    public void testVideoSurfaceViewRotated() throws Throwable {
        mActivity.verifyTest(new AnimationTestCase(
                mVideoViewFactory,
                new FrameLayout.LayoutParams(100, 100, Gravity.LEFT | Gravity.TOP),
                view -> makeInfinite(ObjectAnimator.ofPropertyValuesHolder(view,
                        PropertyValuesHolder.ofFloat(View.TRANSLATION_X, 10f, 30f),
                        PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 10f, 30f),
                        PropertyValuesHolder.ofFloat(View.ROTATION, 45f, 45f))),
                mBlackPixelChecker), mName);
    }

    @Test
    public void testVideoSurfaceViewEdgeCoverage() throws Throwable {
        mActivity.verifyTest(new AnimationTestCase(
                mVideoViewFactory,
                new FrameLayout.LayoutParams(640, 480, Gravity.CENTER),
                view -> {
                    ViewGroup parent = (ViewGroup) view.getParent();
                    final int x = parent.getWidth() / 2;
                    final int y = parent.getHeight() / 2;

                    // Animate from left, to top, to right, to bottom
                    return makeInfinite(ObjectAnimator.ofPropertyValuesHolder(view,
                            PropertyValuesHolder.ofFloat(View.TRANSLATION_X, -x, 0, x, 0, -x),
                            PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0, -y, 0, y, 0)));
                }, mBlackPixelChecker), mName);
    }

    @Test
    public void testVideoSurfaceViewCornerCoverage() throws Throwable {
        mActivity.verifyTest(new AnimationTestCase(
                mVideoViewFactory,
                new FrameLayout.LayoutParams(640, 480, Gravity.CENTER),
                view -> {
                    ViewGroup parent = (ViewGroup) view.getParent();
                    final int x = parent.getWidth() / 2;
                    final int y = parent.getHeight() / 2;

                    // Animate from top left, to top right, to bottom right, to bottom left
                    return makeInfinite(ObjectAnimator.ofPropertyValuesHolder(view,
                            PropertyValuesHolder.ofFloat(View.TRANSLATION_X, -x, x, x, -x, -x),
                            PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, -y, -y, y, y, -y)));
                }, mBlackPixelChecker), mName);
    }
}
