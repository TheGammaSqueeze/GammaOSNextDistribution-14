/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.platform.test.annotations.Presubmit;
import android.util.IntProperty;
import android.util.Property;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.LinearInterpolator;
import android.view.cts.surfacevalidator.AnimationFactory;
import android.view.cts.surfacevalidator.AnimationTestCase;
import android.view.cts.surfacevalidator.CapturedActivityWithResource;
import android.view.cts.surfacevalidator.PixelChecker;
import android.widget.FrameLayout;

import androidx.test.filters.SmallTest;
import androidx.test.rule.ActivityTestRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

@SmallTest
@Presubmit
public class AttachedSurfaceControlSyncTest {
    private static final String TAG = "AttachedSurfaceControlSyncTests";

    @Rule
    public ActivityTestRule<CapturedActivityWithResource> mActivityRule =
            new ActivityTestRule<>(CapturedActivityWithResource.class);

    @Rule
    public TestName mName = new TestName();

    private CapturedActivityWithResource mActivity;

      private static ValueAnimator makeInfinite(ValueAnimator a) {
        a.setRepeatMode(ObjectAnimator.REVERSE);
        a.setRepeatCount(ObjectAnimator.INFINITE);
        a.setDuration(200);
        a.setInterpolator(new LinearInterpolator());
        return a;
    }

    private static class GreenSurfaceAnchorView extends View {
        SurfaceControl mSurfaceControl;
        final Surface mSurface;
        final int[] mLocation = new int[2];

        boolean mChildScAttached;

        final Runnable mUpdatePositionRunnable = new Runnable() {
            @Override
            public void run() {
                SurfaceControl.Transaction t = new SurfaceControl.Transaction();
                getLocationInWindow(mLocation);
                t.setPosition(mSurfaceControl, mLocation[0], mLocation[1]);
                getRootSurfaceControl().applyTransactionOnDraw(t);
            }
        };

        GreenSurfaceAnchorView(Context c) {
            super(c, null, 0, 0);
            mSurfaceControl = new SurfaceControl.Builder()
                              .setName("SurfaceAnchorView")
                              .setBufferSize(100, 100)
                              .build();
            mSurface = new Surface(mSurfaceControl);
            Canvas canvas = mSurface.lockHardwareCanvas();
            canvas.drawColor(Color.GREEN);
            mSurface.unlockCanvasAndPost(canvas);
            setBackgroundColor(Color.BLACK);

            getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    attachChildSc();
                    getViewTreeObserver().removeOnPreDrawListener(this);
                    return true;
                }
            });
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            attachChildSc();
        }

        private void attachChildSc() {
            if (mChildScAttached) {
                return;
            }

            SurfaceControl.Transaction t =
                    getRootSurfaceControl().buildReparentTransaction(mSurfaceControl);

            if (t == null) {
                // TODO (b/286406553) SurfaceControl was not yet setup. Wait until the draw request
                // to attach since the SurfaceControl will be created by that point. This can be
                // cleaned up when the bug is fixed.
                return;
            }

            // Add the SC on top of the view, which is colored black. If the SC moves out of sync
            // from the view, the black view behind should show.
            t.setLayer(mSurfaceControl, 1)
                    .setVisibility(mSurfaceControl, true)
                    .apply();
            mChildScAttached = true;
        }

        @Override
        protected void onDetachedFromWindow() {
            new SurfaceControl.Transaction().reparent(mSurfaceControl, null).apply();
            mSurfaceControl.release();
            mSurface.release();
            mChildScAttached = false;

            super.onDetachedFromWindow();
        }
    }

    private static class GreenSurfaceAnchorViewOnPreDraw extends GreenSurfaceAnchorView {
        private final ViewTreeObserver.OnPreDrawListener mOnPreDrawListener = () -> {
            mUpdatePositionRunnable.run();
            return true;
        };

        GreenSurfaceAnchorViewOnPreDraw(Context c) {
            super(c);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            ViewTreeObserver observer = getViewTreeObserver();
            observer.addOnPreDrawListener(mOnPreDrawListener);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            ViewTreeObserver observer = getViewTreeObserver();
            observer.removeOnPreDrawListener(mOnPreDrawListener);
        }
    }

    private static class GreenSurfaceAnchorViewOnDraw extends GreenSurfaceAnchorView {
        private final ViewTreeObserver.OnDrawListener mDrawListener = mUpdatePositionRunnable::run;

        GreenSurfaceAnchorViewOnDraw(Context c) {
            super(c);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            ViewTreeObserver observer = getViewTreeObserver();
            observer.addOnDrawListener(mDrawListener);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            ViewTreeObserver observer = getViewTreeObserver();
            observer.removeOnDrawListener(mDrawListener);
        }
    }

    private static final AnimationFactory sTranslateAnimationFactory = view -> {
        Property<View, Integer> translationX = new IntProperty<>("translationX") {
            @Override
            public void setValue(View object, int value) {
                object.setTranslationX(value);
            }

            @Override
            public Integer get(View object) {
                return (int) object.getTranslationX();
            }
        };

        Property<View, Integer> translationY = new IntProperty<View>("translationY") {
            @Override
            public void setValue(View object, int value) {
                object.setTranslationY(value);
            }

            @Override
            public Integer get(View object) {
                return (int) object.getTranslationY();
            }
        };

        PropertyValuesHolder pvhX = PropertyValuesHolder.ofInt(translationX, 10, 30);
        PropertyValuesHolder pvhY = PropertyValuesHolder.ofInt(translationY, 10, 30);
        return makeInfinite(ObjectAnimator.ofPropertyValuesHolder(view, pvhX, pvhY));
    };

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
    }

    /** Draws a moving 10x10 green rectangle with hole punch, make sure we don't get any sync errors */
    @Test
    public void testSync() throws Throwable {
        mActivity.verifyTest(new AnimationTestCase(
                GreenSurfaceAnchorViewOnPreDraw::new,
                new FrameLayout.LayoutParams(100, 100, Gravity.LEFT | Gravity.TOP),
                sTranslateAnimationFactory,
                new PixelChecker() {
                    @Override
                    public boolean checkPixels(int blackishPixelCount, int width, int height) {
                        return blackishPixelCount == 0;
                    }
                }), mName);
    }

    @Test
    public void testSyncFromDrawCallback() throws Throwable {
        mActivity.verifyTest(new AnimationTestCase(
                GreenSurfaceAnchorViewOnDraw::new,
                new FrameLayout.LayoutParams(100, 100, Gravity.LEFT | Gravity.TOP),
                sTranslateAnimationFactory,
                new PixelChecker() {
                    @Override
                    public boolean checkPixels(int blackishPixelCount, int width, int height) {
                        return blackishPixelCount == 0;
                    }
                }), mName);
    }

}
