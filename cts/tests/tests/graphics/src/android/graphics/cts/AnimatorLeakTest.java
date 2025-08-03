/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.graphics.cts;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.support.test.uiautomator.UiDevice;

import androidx.test.filters.MediumTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.OverrideAnimationScaleRule;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class AnimatorLeakTest {

    @Rule
    public  ActivityTestRule<EmptyActivity> mActivityRule =
            new ActivityTestRule<>(EmptyActivity.class, false, true);
    public  ActivityTestRule<EmptyActivity2> mActivityRule2 =
            new ActivityTestRule<>(EmptyActivity2.class, false, false);

    // Ensure animators are enabled when these tests run
    @Rule
    public final OverrideAnimationScaleRule animationScaleRule =
            new OverrideAnimationScaleRule(1f);

    boolean mPaused = false;
    boolean mPausedSet = false;
    boolean mFinitePaused = false;
    boolean mFinitePausedSet = false;
    boolean mResumed = false;
    long mDefaultAnimatorPauseDelay = 10000L;

    @Before
    public void setup() {
        mDefaultAnimatorPauseDelay = Animator.getBackgroundPauseDelay();
    }

    @After
    public void cleanup() {
        Animator.setAnimatorPausingEnabled(true);
        Animator.setBackgroundPauseDelay(mDefaultAnimatorPauseDelay);
    }

    /**
     * The approach of this test is to start animators in the main activity for the test.
     * That activity is forced into the background and the test checks whether the animators
     * are paused appropriately. The activity is then forced back into the foreground again
     * and the test checks whether the animators previously paused are resumed. There are also
     * checks to make sure that animators which should not have been paused are handled
     * correctly.
     */
    @Test
    public void testPauseResume() {
        // Latches used to wait for each of the appropriate lifecycle events
        final CountDownLatch animatorStartedLatch = new CountDownLatch(1);
        // There are 2 animators which should be paused and resumed, thus a countdown of 2
        final CountDownLatch animatorPausedLatch = new CountDownLatch(2);
        final CountDownLatch animatorResumedLatch = new CountDownLatch(2);

        // The first of these (infinite) should get paused, the second (finite) should not
        ValueAnimator infiniteAnimator = ValueAnimator.ofFloat(0f, 1f).setDuration(1000);
        infiniteAnimator.setRepeatCount(ValueAnimator.INFINITE);
        ValueAnimator finiteAnimator = ValueAnimator.ofFloat(0f, 1f).setDuration(5000);

        // Now create infinite and finite AnimatorSets
        // As above, the infinite set should get paused, the finite one should not
        ValueAnimator infiniteAnimator1 = ValueAnimator.ofFloat(0f, 1f).setDuration(1000);
        infiniteAnimator1.setRepeatCount(ValueAnimator.INFINITE);
        AnimatorSet infiniteSet = new AnimatorSet();
        infiniteSet.play(infiniteAnimator1);
        ValueAnimator finiteAnimator1 = ValueAnimator.ofFloat(0f, 1f).setDuration(5000);
        AnimatorSet finiteSet = new AnimatorSet();
        finiteSet.play(finiteAnimator1);

        // This listener tracks which animators get paused and resumed
        AnimatorListenerAdapter listener = new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                // Wait until animators start to trigger the lifecycle changes
                animatorStartedLatch.countDown();
            }

            @Override
            public void onAnimationPause(Animator animation) {
                if (animation == infiniteAnimator) {
                    mPaused = true;
                } else if (animation == infiniteSet) {
                    mPausedSet = true;
                } else if (animation == finiteAnimator) {
                    mFinitePaused = true;
                    // end it to avoid having it interfere with future resume latch
                    animation.end();
                    return;
                } else if (animation == finiteSet) {
                    mFinitePausedSet = true;
                    // end it to avoid having it interfere with future resume latch
                    animation.end();
                    return;
                }
                animatorPausedLatch.countDown();
            }

            @Override
            public void onAnimationResume(Animator animation) {
                mResumed = true;
                animatorResumedLatch.countDown();
            }
        };
        infiniteAnimator.addListener(listener);
        infiniteAnimator.addPauseListener(listener);
        finiteAnimator.addPauseListener(listener);
        infiniteSet.addPauseListener(listener);
        finiteSet.addPauseListener(listener);

        getInstrumentation().runOnMainSync(new Runnable() {
            public void run() {
                Animator.setBackgroundPauseDelay(500);
                try {
                    infiniteAnimator.start();
                    finiteAnimator.start();
                    infiniteSet.start();
                    finiteSet.start();
                } catch (Throwable throwable) {
                }
            }
        });
        try {
            // Wait until the animators are running to start changing the activity lifecycle
            animatorStartedLatch.await(5, TimeUnit.SECONDS);

            // First, test that animators are *not* paused when an activity goes to the background
            // if there is another activity in the same process which is now in the foreground.
            mActivityRule2.launchActivity(null);
            animatorPausedLatch.await(1, TimeUnit.SECONDS);
            Assert.assertFalse("Animator was paused", mPaused);
            mActivityRule2.finishActivity();

            // Send the activity to the background. This should cause the animators to be paused
            // after Animator.getBackgroundPauseDelay()
            UiDevice uiDevice = UiDevice.getInstance(getInstrumentation());
            uiDevice.pressHome();

            animatorPausedLatch.await(5, TimeUnit.SECONDS);

            // It is not possible (or obvious) how to bring the activity back into the foreground.
            // However, AnimationHandler pauses/resumes all animators for the process based on
            // *any* visible activities in that process. So it is sufficient to launch a second
            // activity, which should resume the animators paused when the first activity went
            // into the background.
            mActivityRule2.launchActivity(null);
            animatorResumedLatch.await(5, TimeUnit.SECONDS);
        } catch (Exception e) { }
        Assert.assertTrue("Animator was not paused", mPaused);
        Assert.assertTrue("AnimatorSet was not paused", mPausedSet);
        Assert.assertFalse("Non-infinite Animator was paused", mFinitePaused);
        Assert.assertFalse("Non-infinite AnimatorSet was paused", mFinitePausedSet);
        Assert.assertTrue("Animator was not resumed", mResumed);
        Assert.assertTrue("AnimatorSet was not resumed", mResumed);
    }

    /**
     * The approach of this test is to start animators in the main activity for the test.
     * That activity is forced into the background and the test checks whether the animators
     * are paused appropriately. The activity is then forced back into the foreground again
     * and the test checks whether the animators previously paused are resumed. There are also
     * checks to make sure that animators which should not have been paused are handled
     * correctly.
     */
    @Test
    public void testPauseDisablement() {
        // Latches used to wait for each of the appropriate lifecycle events
        final CountDownLatch animatorStartedLatch = new CountDownLatch(1);
        // There are 2 animators which should be paused and resumed, thus a countdown of 2
        final CountDownLatch animatorPausedLatch = new CountDownLatch(1);
        final CountDownLatch animatorResumedLatch = new CountDownLatch(1);

        // The first of these (infinite) should get paused, the second (finite) should not
        ValueAnimator infiniteAnimator = ValueAnimator.ofFloat(0f, 1f).setDuration(1000);
        infiniteAnimator.setRepeatCount(ValueAnimator.INFINITE);

        // This listener tracks which animators get paused and resumed
        AnimatorListenerAdapter listener = new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                // Wait until animators start to trigger the lifecycle changes
                animatorStartedLatch.countDown();
            }

            @Override
            public void onAnimationPause(Animator animation) {
                mPaused = true;
                animatorPausedLatch.countDown();
            }

            @Override
            public void onAnimationResume(Animator animation) {
                mResumed = true;
                animatorResumedLatch.countDown();
            }
        };
        infiniteAnimator.addListener(listener);
        infiniteAnimator.addPauseListener(listener);

        getInstrumentation().runOnMainSync(new Runnable() {
            public void run() {
                Animator.setBackgroundPauseDelay(500);
                Animator.setAnimatorPausingEnabled(false);
                try {
                    infiniteAnimator.start();
                } catch (Throwable throwable) {
                }
            }
        });
        try {
            // Wait until the animators are running to start changing the activity lifecycle
            animatorStartedLatch.await(5, TimeUnit.SECONDS);

            // Send the activity to the background. This should cause the animators to be paused
            // after Animator.getBackgroundPauseDelay()
            UiDevice uiDevice = UiDevice.getInstance(getInstrumentation());
            uiDevice.pressHome();

            animatorPausedLatch.await(2, TimeUnit.SECONDS);

            // It is not possible (or obvious) how to bring the activity back into the foreground.
            // However, AnimationHandler pauses/resumes all animators for the process based on
            // *any* visible activities in that process. So it is sufficient to launch a second
            // activity, which should resume the animators paused when the first activity went
            // into the background.
            mActivityRule2.launchActivity(null);
            animatorResumedLatch.await(2, TimeUnit.SECONDS);
        } catch (Exception e) { }
        Assert.assertFalse("Animator paused when pausing disabled", mPaused);
        Assert.assertFalse("Animator resumed when pausing disabled", mResumed);
    }

}
