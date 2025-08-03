/*
 * Copyright (C) 2012 The Android Open Source Project
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
package android.animation.cts;

import static com.android.compatibility.common.util.CtsMockitoUtils.within;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class AnimatorSetTest {
    private AnimationActivity mActivity;
    private AnimatorSet mAnimatorSet;
    private float mPreviousDurationScale = 1.0f;
    private long mDuration = 1000;
    private Object object;
    private ObjectAnimator yAnimator;
    private ObjectAnimator xAnimator;
    Set<Integer> identityHashes = new HashSet<>();
    private static final float EPSILON = 0.001f;

    @Rule
    public ActivityTestRule<AnimationActivity> mActivityRule =
            new ActivityTestRule<>(AnimationActivity.class);

    @Before
    public void setup() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false);
        mActivity = mActivityRule.getActivity();
        mPreviousDurationScale = ValueAnimator.getDurationScale();
        ValueAnimator.setDurationScale(1.0f);
        object = mActivity.view.newBall;
        yAnimator = getYAnimator(object);
        xAnimator = getXAnimator(object);
    }

    @After
    public void tearDown() {
        ValueAnimator.setDurationScale(mPreviousDurationScale);
    }

    @Test
    public void testPlaySequentially() throws Throwable {
        xAnimator.setRepeatCount(0);
        yAnimator.setRepeatCount(0);
        xAnimator.setDuration(50);
        yAnimator.setDuration(50);
        List<Animator> animators = new ArrayList<Animator>();
        animators.add(xAnimator);
        animators.add(yAnimator);
        mAnimatorSet = new AnimatorSet();
        mAnimatorSet.playSequentially(animators);
        verifySequentialPlayOrder(mAnimatorSet, new Animator[] {xAnimator, yAnimator});

        ValueAnimator anim1 = ValueAnimator.ofFloat(0f, 1f);
        ValueAnimator anim2 = ValueAnimator.ofInt(0, 100);
        anim1.setDuration(50);
        anim2.setDuration(50);
        AnimatorSet set = new AnimatorSet();
        set.playSequentially(anim1, anim2);
        verifySequentialPlayOrder(set, new Animator[] {anim1, anim2});
    }

    /**
     * Start the animator, and verify the animators are played sequentially in the order that is
     * defined in the array.
     *
     * @param set AnimatorSet to be started and verified
     * @param animators animators that we put in the AnimatorSet, in the order that they'll play
     */
    private void verifySequentialPlayOrder(final AnimatorSet set, Animator[] animators)
            throws Throwable {

        final MyListener[] listeners = new MyListener[animators.length];
        for (int i = 0; i < animators.length; i++) {
            if (i == 0) {
                listeners[i] = new MyListener();
            } else {
                final int current = i;
                listeners[i] = new MyListener() {
                    @Override
                    public void onAnimationStart(Animator anim) {
                        super.onAnimationStart(anim);
                        // Check that the previous animator has finished.
                        assertTrue(listeners[current - 1].mEndIsCalled);
                    }
                };
            }
            animators[i].addListener(listeners[i]);
        }

        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch endLatch = new CountDownLatch(1);

        set.addListener(new MyListener() {
            @Override
            public void onAnimationEnd(Animator anim) {
                endLatch.countDown();
            }
        });

        long totalDuration = set.getTotalDuration();
        assertFalse(set.isRunning());
        mActivityRule.runOnUiThread(() -> {
            set.start();
            startLatch.countDown();
        });

        // Set timeout to 100ms, if current count reaches 0 before the timeout, startLatch.await(...)
        // will return immediately.
        assertTrue(startLatch.await(100, TimeUnit.MILLISECONDS));
        assertTrue(set.isRunning());
        assertTrue(endLatch.await(totalDuration * 2, TimeUnit.MILLISECONDS));
        // Check that all the animators have finished.
        for (int i = 0; i < listeners.length; i++) {
            assertTrue(listeners[i].mEndIsCalled);
        }

        // Now reverse the animations and verify whether the play order is reversed.
        for (int i = 0; i < animators.length; i++) {
            if (i == animators.length - 1) {
                listeners[i] = new MyListener();
            } else {
                final int current = i;
                listeners[i] = new MyListener() {
                    @Override
                    public void onAnimationStart(Animator anim) {
                        super.onAnimationStart(anim);
                        // Check that the previous animator has finished.
                        assertTrue(listeners[current + 1].mEndIsCalled);
                    }
                };
            }
            animators[i].removeAllListeners();
            animators[i].addListener(listeners[i]);
        }

        mActivityRule.runOnUiThread(() -> {
            set.reverse();
            startLatch.countDown();
        });

        // Set timeout to 100ms, if current count reaches 0 before the timeout, startLatch.await(..)
        // will return immediately.
        assertTrue(startLatch.await(100, TimeUnit.MILLISECONDS));
        assertTrue(set.isRunning());
        assertTrue(endLatch.await(totalDuration * 2, TimeUnit.MILLISECONDS));

    }

    @Test
    public void testPlayTogether() throws Throwable {
        xAnimator.setRepeatCount(ValueAnimator.INFINITE);
        Animator[] animatorArray = {xAnimator, yAnimator};

        mAnimatorSet = new AnimatorSet();
        mAnimatorSet.playTogether(animatorArray);

        assertFalse(mAnimatorSet.isRunning());
        assertFalse(xAnimator.isRunning());
        assertFalse(yAnimator.isRunning());
        startAnimation(mAnimatorSet);
        SystemClock.sleep(100);
        assertTrue(mAnimatorSet.isRunning());
        assertTrue(xAnimator.isRunning());
        assertTrue(yAnimator.isRunning());

        // Now assemble another animator set
        ValueAnimator anim1 = ValueAnimator.ofFloat(0f, 100f);
        ValueAnimator anim2 = ValueAnimator.ofFloat(10f, 100f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(anim1, anim2);

        assertFalse(set.isRunning());
        assertFalse(anim1.isRunning());
        assertFalse(anim2.isRunning());
        startAnimation(set);
        SystemClock.sleep(100);
        assertTrue(set.isRunning());
        assertTrue(anim1.isRunning());
        assertTrue(anim2.isRunning());
    }

    @Test
    public void testPlayBeforeAfter() throws Throwable {
        xAnimator.setRepeatCount(0);
        yAnimator.setRepeatCount(0);
        final ValueAnimator zAnimator = ValueAnimator.ofFloat(0f, 100f);

        xAnimator.setDuration(50);
        yAnimator.setDuration(50);
        zAnimator.setDuration(50);

        AnimatorSet set = new AnimatorSet();
        set.play(yAnimator).before(zAnimator).after(xAnimator);

        verifySequentialPlayOrder(set, new Animator[] {xAnimator, yAnimator, zAnimator});
    }

    @Test
    public void testListenerCallbackOnEmptySet() throws Throwable {
        // Create an AnimatorSet that only contains one empty AnimatorSet, and checks the callback
        // sequence by checking the time stamps of the callbacks.
        final AnimatorSet emptySet = new AnimatorSet();
        final AnimatorSet set = new AnimatorSet();
        set.play(emptySet);
        MyListener listener = new MyListener() {
            long startTime = 0;
            long endTime = 0;
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                startTime = SystemClock.currentThreadTimeMillis();
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                endTime = SystemClock.currentThreadTimeMillis();
                assertTrue(endTime >= startTime);
                assertTrue(startTime != 0);
            }
        };
        set.addListener(listener);
        mActivityRule.runOnUiThread(() -> {
            set.start();
        });
        assertTrue(listener.mStartIsCalled);
        assertTrue(listener.mEndIsCalled);
    }

    @Test
    public void testPauseAndResume() throws Throwable {
        final AnimatorSet set = new AnimatorSet();
        ValueAnimator a1 = ValueAnimator.ofFloat(0f, 100f);
        a1.setDuration(50);
        ValueAnimator a2 = ValueAnimator.ofFloat(0f, 100f);
        a2.setDuration(50);
        a1.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                // Pause non-delayed set once the child animator starts
                set.pause();
            }
        });
        set.playTogether(a1, a2);

        final AnimatorSet delayedSet = new AnimatorSet();
        ValueAnimator a3 = ValueAnimator.ofFloat(0f, 100f);
        a3.setDuration(50);
        ValueAnimator a4 = ValueAnimator.ofFloat(0f, 100f);
        a4.setDuration(50);
        delayedSet.playSequentially(a3, a4);
        delayedSet.setStartDelay(50);

        MyListener l1 = new MyListener();
        MyListener l2 = new MyListener();
        set.addListener(l1);
        delayedSet.addListener(l2);

        mActivityRule.runOnUiThread(() -> {
            set.start();
            delayedSet.start();

            // Pause the delayed set during start delay
            delayedSet.pause();
        });

        // Sleep long enough so that if the sets are not properly paused, they would have
        // finished.
        SystemClock.sleep(300);
        // Verify that both sets have been paused and *not* finished.
        assertTrue(set.isPaused());
        assertTrue(delayedSet.isPaused());
        assertTrue(l1.mStartIsCalled);
        assertTrue(l2.mStartIsCalled);
        assertFalse(l1.mEndIsCalled);
        assertFalse(l2.mEndIsCalled);

        mActivityRule.runOnUiThread(() -> {
            set.resume();
            delayedSet.resume();
        });
        SystemClock.sleep(300);

        assertFalse(set.isPaused());
        assertFalse(delayedSet.isPaused());
        assertTrue(l1.mEndIsCalled);
        assertTrue(l2.mEndIsCalled);
    }

    @Test
    public void testPauseBeforeStart() throws Throwable {
        final AnimatorSet set = new AnimatorSet();
        ValueAnimator a1 = ValueAnimator.ofFloat(0f, 100f);
        a1.setDuration(50);
        ValueAnimator a2 = ValueAnimator.ofFloat(0f, 100f);
        a2.setDuration(50);
        set.setStartDelay(50);
        set.playSequentially(a1, a2);

        final MyListener listener = new MyListener();
        set.addListener(listener);

        mActivityRule.runOnUiThread(() -> {
            // Pause animator set before calling start()
            set.pause();
            // Verify that pause should have no effect on a not-yet-started animator.
            assertFalse(set.isPaused());
            set.start();
        });
        SystemClock.sleep(300);

        // Animator set should finish running by now since it's not paused.
        assertTrue(listener.mStartIsCalled);
        assertTrue(listener.mEndIsCalled);
    }

    @Test
    public void testSeekAfterPause() throws Throwable {
        final AnimatorSet set = new AnimatorSet();
        ValueAnimator a1 = ValueAnimator.ofFloat(0f, 50f);
        a1.setDuration(50);
        ValueAnimator a2 = ValueAnimator.ofFloat(50, 100f);
        a2.setDuration(50);
        set.playSequentially(a1, a2);
        set.setInterpolator(new LinearInterpolator());

        mActivityRule.runOnUiThread(() -> {
            set.start();
            set.pause();
            set.setCurrentPlayTime(60);
            assertEquals((long) set.getCurrentPlayTime(), 60);
            assertEquals((float) a1.getAnimatedValue(), 50f, EPSILON);
            assertEquals((float) a2.getAnimatedValue(), 60f, EPSILON);

            set.setCurrentPlayTime(40);
            assertEquals((long) set.getCurrentPlayTime(), 40);
            assertEquals((float) a1.getAnimatedValue(), 40f, EPSILON);
            assertEquals((float) a2.getAnimatedValue(), 50f, EPSILON);

            set.cancel();
        });
    }

    @Test
    public void testDuration() throws Throwable {
        xAnimator.setRepeatCount(ValueAnimator.INFINITE);
        Animator[] animatorArray = { xAnimator, yAnimator };

        mAnimatorSet = new AnimatorSet();
        mAnimatorSet.playTogether(animatorArray);
        mAnimatorSet.setDuration(1000);

        startAnimation(mAnimatorSet);
        SystemClock.sleep(100);
        assertEquals(mAnimatorSet.getDuration(), 1000);
    }

    @Test
    public void testStartDelay() throws Throwable {
        xAnimator.setRepeatCount(ValueAnimator.INFINITE);
        Animator[] animatorArray = { xAnimator, yAnimator };

        mAnimatorSet = new AnimatorSet();
        mAnimatorSet.playTogether(animatorArray);
        mAnimatorSet.setStartDelay(10);

        startAnimation(mAnimatorSet);
        SystemClock.sleep(100);
        assertEquals(mAnimatorSet.getStartDelay(), 10);
    }

    /**
     * This test sets up an AnimatorSet with start delay. One of the child animators also has
     * start delay. We then verify that start delay was handled correctly on both AnimatorSet
     * and individual animator level.
     */
    @Test
    public void testReverseWithStartDelay() throws Throwable {
        ValueAnimator a1 = ValueAnimator.ofFloat(0f, 1f);
        a1.setDuration(200);
        Animator.AnimatorListener listener1 = mock(AnimatorListenerAdapter.class);
        a1.addListener(listener1);

        ValueAnimator a2 = ValueAnimator.ofFloat(1f, 2f);
        a2.setDuration(200);
        // Set start delay on a2 so that the delay is passed 100ms after a1 is finished.
        a2.setStartDelay(300);
        Animator.AnimatorListener listener = mock(AnimatorListenerAdapter.class);
        a2.addListener(listener);

        a2.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation, boolean inReverse) {
                assertTrue(inReverse);
                // By the time a2 finishes reversing, a1 should not have started.
                assertFalse(a1.isStarted());
            }
        });

        AnimatorSet set = new AnimatorSet();
        set.playTogether(a1, a2);
        set.setStartDelay(1000);
        Animator.AnimatorListener setListener = mock(AnimatorListenerAdapter.class);
        set.addListener(setListener);
        mActivityRule.runOnUiThread(() -> {
            set.reverse();
            assertTrue(a2.isStarted());
            assertTrue(a2.isRunning());
        });

        // a2 should finish 200ms after reverse started
        verify(listener, within(300)).onAnimationEnd(a2, true);
        // When a2 finishes, a1 should not have started yet
        verify(listener1, never()).onAnimationStart(a1, true);

        // The whole set should finish within 500ms, i.e. 300ms after a2 is finished. This verifies
        // that the AnimatorSet didn't mistakenly use its start delay in the reverse run.
        verify(setListener, within(400)).onAnimationEnd(set, true);
        verify(listener1, times(1)).onAnimationEnd(a1, true);

    }

    /**
     * Test that duration scale is handled correctly in the AnimatorSet.
     */
    @Test
    public void testZeroDurationScale() throws Throwable {
        ValueAnimator.setDurationScale(0);

        ValueAnimator a1 = ValueAnimator.ofFloat(0f, 1f);
        a1.setDuration(200);
        Animator.AnimatorListener listener1 = mock(AnimatorListenerAdapter.class);
        a1.addListener(listener1);

        ValueAnimator a2 = ValueAnimator.ofFloat(1f, 2f);
        a2.setDuration(200);
        // Set start delay on a2 so that the delay is passed 100ms after a1 is finished.
        a2.setStartDelay(300);
        Animator.AnimatorListener listener2 = mock(AnimatorListenerAdapter.class);
        a2.addListener(listener2);

        AnimatorSet set = new AnimatorSet();
        set.playSequentially(a1, a2);
        set.setStartDelay(1000);
        Animator.AnimatorListener setListener = mock(AnimatorListenerAdapter.class);
        set.addListener(setListener);

        mActivityRule.runOnUiThread(() -> {
            set.start();
            verify(setListener, times(0)).onAnimationEnd(any(AnimatorSet.class),
                    any(boolean.class));
        });
        verify(setListener, within(100)).onAnimationEnd(set, false);
        verify(listener1, times(1)).onAnimationEnd(a1, false);
        verify(listener2, times(1)).onAnimationEnd(a2, false);
    }

    /**
     * Test that non-zero duration scale is handled correctly in the AnimatorSet.
     */
    @Test
    public void testDurationScale() throws Throwable {
        // Change the duration scale to 3
        ValueAnimator.setDurationScale(3f);

        ValueAnimator a1 = ValueAnimator.ofFloat(0f, 1f);
        a1.setDuration(100);
        Animator.AnimatorListener listener1 = mock(AnimatorListenerAdapter.class);
        a1.addListener(listener1);

        ValueAnimator a2 = ValueAnimator.ofFloat(1f, 2f);
        a2.setDuration(100);
        // Set start delay on a2 so that the delay is passed 100ms after a1 is finished.
        a2.setStartDelay(200);
        Animator.AnimatorListener listener2 = mock(AnimatorListenerAdapter.class);
        a2.addListener(listener2);

        AnimatorSet set = new AnimatorSet();
        set.playSequentially(a1, a2);
        Animator.AnimatorListener setListener = mock(AnimatorListenerAdapter.class);
        set.addListener(setListener);
        set.setStartDelay(200);

        mActivityRule.runOnUiThread(() -> {
            set.start();
        });

        // Sleep for part of the start delay and check that no child animator has started, to verify
        // that the duration scale has been properly scaled.
        SystemClock.sleep(400);
        // start delay of the set should be scaled to 600ms
        verify(listener1, never()).onAnimationStart(a1, false);
        verify(listener2, never()).onAnimationStart(a2, false);

        verify(listener1, within(400)).onAnimationStart(a1, false);
        // Verify that a1 finish in ~300ms (3x its defined duration)
        verify(listener1, within(500)).onAnimationEnd(a1, false);

        // a2 should be in the delayed stage after a1 is finished
        assertTrue(a2.isStarted());
        assertFalse(a2.isRunning());

        verify(listener2, within(800)).onAnimationStart(a2, false);
        assertTrue(a2.isRunning());

        // Verify that the AnimatorSet has finished within 1650ms since the start of the animation.
        // The duration of the set is 500ms, duration scale = 3.
        verify(setListener, within(500)).onAnimationEnd(set, false);
        verify(listener1, times(1)).onAnimationEnd(a1, false);
        verify(listener2, times(1)).onAnimationEnd(a2, false);
    }

    /**
     * This test sets up 10 animators playing together. We expect the start time for all animators
     * to be the same.
     */
    @Test
    public void testMultipleAnimatorsPlayTogether() throws Throwable {
        Animator[] animators = new Animator[10];
        for (int i = 0; i < 10; i++) {
            animators[i] = ValueAnimator.ofFloat(0f, 1f);
        }
        AnimatorSet set = new AnimatorSet();
        set.playTogether(animators);
        set.setStartDelay(80);

        Animator.AnimatorListener setListener = mock(AnimatorListenerAdapter.class);
        set.addListener(setListener);
        mActivityRule.runOnUiThread(() -> {
            set.start();
        });
        SystemClock.sleep(150);
        for (int i = 0; i < 10; i++) {
            assertTrue(animators[i].isRunning());
        }

        verify(setListener, within(400)).onAnimationEnd(set, false);
    }

    @Test
    public void testGetChildAnimations() throws Throwable {
        Animator[] animatorArray = { xAnimator, yAnimator };

        mAnimatorSet = new AnimatorSet();
        mAnimatorSet.getChildAnimations();
        assertEquals(0, mAnimatorSet.getChildAnimations().size());
        mAnimatorSet.playSequentially(animatorArray);
        assertEquals(2, mAnimatorSet.getChildAnimations().size());
    }

    @Test
    public void testSetInterpolator() throws Throwable {
        xAnimator.setRepeatCount(ValueAnimator.INFINITE);
        Animator[] animatorArray = {xAnimator, yAnimator};
        TimeInterpolator interpolator = new AccelerateDecelerateInterpolator();
        mAnimatorSet = new AnimatorSet();
        mAnimatorSet.playTogether(animatorArray);
        mAnimatorSet.setInterpolator(interpolator);

        assertFalse(mAnimatorSet.isRunning());
        startAnimation(mAnimatorSet);
        SystemClock.sleep(100);

        ArrayList<Animator> animatorList = mAnimatorSet.getChildAnimations();
        assertEquals(interpolator, animatorList.get(0).getInterpolator());
        assertEquals(interpolator, animatorList.get(1).getInterpolator());
    }

    private ObjectAnimator getXAnimator(Object object) {
        String propertyX = "x";
        float startX = mActivity.mStartX;
        float endX = mActivity.mStartX + mActivity.mDeltaX;
        ObjectAnimator xAnimator = ObjectAnimator.ofFloat(object, propertyX, startX, endX);
        xAnimator.setDuration(mDuration);
        xAnimator.setRepeatCount(ValueAnimator.INFINITE);
        xAnimator.setInterpolator(new AccelerateInterpolator());
        xAnimator.setRepeatMode(ValueAnimator.REVERSE);
        return xAnimator;
    }

    private ObjectAnimator getYAnimator(Object object) {
         String property = "y";
         float startY = mActivity.mStartY;
         float endY = mActivity.mStartY + mActivity.mDeltaY;
         ObjectAnimator yAnimator = ObjectAnimator.ofFloat(object, property, startY, endY);
         yAnimator.setDuration(mDuration);
         yAnimator.setRepeatCount(2);
         yAnimator.setInterpolator(new AccelerateInterpolator());
         yAnimator.setRepeatMode(ValueAnimator.REVERSE);
        return yAnimator;
    }

    private void startAnimation(final AnimatorSet animatorSet) throws Throwable {
        mActivityRule.runOnUiThread(() -> mActivity.startAnimatorSet(animatorSet));
    }

    private void assertUnique(Object object) {
        assertUnique(object, "");
    }

    private void assertUnique(Object object, String msg) {
        final int code = System.identityHashCode(object);
        assertTrue("object should be unique " + msg + ", obj:" + object, identityHashes.add(code));

    }

    @Test
    public void testClone() throws Throwable {
        final AnimatorSet set1 = new AnimatorSet();
        final AnimatorListenerAdapter setListener = new AnimatorListenerAdapter() {};
        set1.addListener(setListener);
        ObjectAnimator animator1 = new ObjectAnimator();
        animator1.setDuration(100);
        animator1.setPropertyName("x");
        animator1.setIntValues(5);
        animator1.setInterpolator(new LinearInterpolator());
        AnimatorListenerAdapter listener1 = new AnimatorListenerAdapter(){};
        AnimatorListenerAdapter listener2 = new AnimatorListenerAdapter(){};
        animator1.addListener(listener1);

        ObjectAnimator animator2 = new ObjectAnimator();
        animator2.setDuration(100);
        animator2.setInterpolator(new LinearInterpolator());
        animator2.addListener(listener2);
        animator2.setPropertyName("y");
        animator2.setIntValues(10);

        set1.playTogether(animator1, animator2);

        AnimateObject target = new AnimateObject();
        set1.setTarget(target);
        mActivityRule.runOnUiThread(set1::start);
        assertTrue(set1.isStarted());

        animator1.getListeners();
        AnimatorSet set2 = set1.clone();
        assertFalse(set2.isStarted());

        assertUnique(set1);
        assertUnique(animator1);
        assertUnique(animator2);

        assertUnique(set2);
        assertEquals(2, set2.getChildAnimations().size());

        Animator clone1 = set2.getChildAnimations().get(0);
        Animator clone2 = set2.getChildAnimations().get(1);

        for (Animator animator : set2.getChildAnimations()) {
            assertUnique(animator);
        }

        assertTrue(clone1.getListeners().contains(listener1));
        assertTrue(clone2.getListeners().contains(listener2));

        assertTrue(set2.getListeners().contains(setListener));

        for (Animator.AnimatorListener listener : set1.getListeners()) {
            assertTrue(set2.getListeners().contains(listener));
        }

        assertEquals(animator1.getDuration(), clone1.getDuration());
        assertEquals(animator2.getDuration(), clone2.getDuration());
        assertSame(animator1.getInterpolator(), clone1.getInterpolator());
        assertSame(animator2.getInterpolator(), clone2.getInterpolator());
    }

    /**
     * Testing seeking in an AnimatorSet containing sequential animators.
     */
    @Test
    public void testSeeking() throws Throwable {
        final AnimatorSet set = new AnimatorSet();
        final ValueAnimator a1 = ValueAnimator.ofFloat(0f, 150f);
        a1.setDuration(150);
        final ValueAnimator a2 = ValueAnimator.ofFloat(150f, 250f);
        a2.setDuration(100);
        final ValueAnimator a3 = ValueAnimator.ofFloat(250f, 300f);
        a3.setDuration(50);

        a1.setInterpolator(null);
        a2.setInterpolator(null);
        a3.setInterpolator(null);

        set.playSequentially(a1, a2, a3);

        set.setCurrentPlayTime(100);
        assertEquals(100f, (Float) a1.getAnimatedValue(), EPSILON);
        assertEquals(150f, (Float) a2.getAnimatedValue(), EPSILON);
        assertEquals(250f, (Float) a3.getAnimatedValue(), EPSILON);

        set.setCurrentPlayTime(280);
        assertEquals(150f, (Float) a1.getAnimatedValue(), EPSILON);
        assertEquals(250f, (Float) a2.getAnimatedValue(), EPSILON);
        assertEquals(280f, (Float) a3.getAnimatedValue(), EPSILON);

        AnimatorListenerAdapter setListener = new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                assertEquals(150f, (Float) a1.getAnimatedValue(), EPSILON);
                assertEquals(250f, (Float) a2.getAnimatedValue(), EPSILON);
                assertEquals(300f, (Float) a3.getAnimatedValue(), EPSILON);
            }
        };
        AnimatorListenerAdapter mockListener = mock(AnimatorListenerAdapter.class);
        set.addListener(setListener);
        set.addListener(mockListener);
        mActivityRule.runOnUiThread(() -> {
            set.start();
        });

        verify(mockListener, within(300)).onAnimationEnd(set, false);

        // Seek after a run to the middle-ish, and verify the first animator is at the end
        // value and the 3rd at beginning value, and the 2nd animator is at the seeked value.
        set.setCurrentPlayTime(200);
        assertEquals(150f, (Float) a1.getAnimatedValue(), EPSILON);
        assertEquals(200f, (Float) a2.getAnimatedValue(), EPSILON);
        assertEquals(250f, (Float) a3.getAnimatedValue(), EPSILON);
    }

    /**
     * Testing seeking in an AnimatorSet containing infinite animators.
     */
    @Test
    public void testSeekingInfinite() {
        final AnimatorSet set = new AnimatorSet();
        final ValueAnimator a1 = ValueAnimator.ofFloat(0f, 100f);
        a1.setDuration(100);
        final ValueAnimator a2 = ValueAnimator.ofFloat(100f, 200f);
        a2.setDuration(100);
        a2.setRepeatCount(ValueAnimator.INFINITE);
        a2.setRepeatMode(ValueAnimator.RESTART);

        final ValueAnimator a3 = ValueAnimator.ofFloat(100f, 200f);
        a3.setDuration(100);
        a3.setRepeatCount(ValueAnimator.INFINITE);
        a3.setRepeatMode(ValueAnimator.REVERSE);

        a1.setInterpolator(null);
        a2.setInterpolator(null);
        a3.setInterpolator(null);
        set.play(a1).before(a2);
        set.play(a1).before(a3);

        set.setCurrentPlayTime(50);
        assertEquals(50f, (Float) a1.getAnimatedValue(), EPSILON);
        assertEquals(100f, (Float) a2.getAnimatedValue(), EPSILON);
        assertEquals(100f, (Float) a3.getAnimatedValue(), EPSILON);

        set.setCurrentPlayTime(100);
        assertEquals(100f, (Float) a1.getAnimatedValue(), EPSILON);
        assertEquals(100f, (Float) a2.getAnimatedValue(), EPSILON);
        assertEquals(100f, (Float) a3.getAnimatedValue(), EPSILON);

        // Seek to the 1st iteration of the infinite repeat animators, and they should have the
        // same value.
        set.setCurrentPlayTime(180);
        assertEquals(100f, (Float) a1.getAnimatedValue(), EPSILON);
        assertEquals(180f, (Float) a2.getAnimatedValue(), EPSILON);
        assertEquals(180f, (Float) a3.getAnimatedValue(), EPSILON);

        // Seek to the 2nd iteration of the infinite repeat animators, and they should have
        // different values as they have different repeat mode.
        set.setCurrentPlayTime(280);
        assertEquals(100f, (Float) a1.getAnimatedValue(), EPSILON);
        assertEquals(180f, (Float) a2.getAnimatedValue(), EPSILON);
        assertEquals(120f, (Float) a3.getAnimatedValue(), EPSILON);

    }

    /**
     * This test verifies that getCurrentPlayTime() returns the right value.
     */
    @Test
    public void testGetCurrentPlayTime() throws Throwable {
        // Setup an AnimatorSet with start delay
        final AnimatorSet set = new AnimatorSet();
        final ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f).setDuration(300);
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation, boolean inReverse) {
                assertFalse(inReverse);
                assertEquals(200, set.getCurrentPlayTime());
            }
        });
        set.play(anim);
        set.setStartDelay(100);

        Animator.AnimatorListener setListener = mock(AnimatorListenerAdapter.class);
        set.addListener(setListener);

        // Set a seek time and verify, before start
        set.setCurrentPlayTime(20);
        assertEquals(20, set.getCurrentPlayTime());

        // Now start() should start right away from the seeked position, skipping the delay.
        mActivityRule.runOnUiThread(() -> {
            set.setCurrentPlayTime(200);
            set.start();
            assertEquals(200, set.getCurrentPlayTime());
        });

        // When animation is seeked to 200ms, it should take another 100ms to end.
        verify(setListener, within(200)).onAnimationEnd(set, false);
    }

    @Test
    public void testNotifiesAfterEnd() throws Throwable {
        final ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
        Animator.AnimatorListener listener = new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                assertTrue(animation.isStarted());
                assertTrue(animation.isRunning());
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                assertFalse(animation.isRunning());
                assertFalse(animation.isStarted());
                super.onAnimationEnd(animation);
            }
        };
        animator.addListener(listener);
        final AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(animator);
        animatorSet.addListener(listener);
        mActivityRule.runOnUiThread(() -> {
            animatorSet.start();
            animator.end();
            assertFalse(animator.isStarted());
        });
    }

    /**
     * Test that when a child animator is being manipulated outside of an AnimatorSet, by the time
     * AnimatorSet starts, it will not be affected, and all the child animators would start at their
     * scheduled start time.
     */
    @Test
    public void testManipulateChildOutsideOfSet() throws Throwable {
        final ValueAnimator fadeIn = ObjectAnimator.ofFloat(mActivity.view, View.ALPHA, 0f, 1f);
        fadeIn.setDuration(200);
        final ValueAnimator fadeOut = ObjectAnimator.ofFloat(mActivity.view, View.ALPHA, 1f, 0f);
        fadeOut.setDuration(200);

        ValueAnimator.AnimatorUpdateListener listener = mock(
                ValueAnimator.AnimatorUpdateListener.class);
        fadeIn.addUpdateListener(listener);

        AnimatorSet show = new AnimatorSet();
        show.play(fadeIn);

        AnimatorSet hideNShow = new AnimatorSet();
        hideNShow.play(fadeIn).after(fadeOut);

        mActivityRule.runOnUiThread(() ->
                show.start()
        );

        verify(listener, timeout(100).atLeast(2)).onAnimationUpdate(fadeIn);

        AnimatorListenerAdapter adapter = mock(AnimatorListenerAdapter.class);
        hideNShow.addListener(adapter);
        // Start hideNShow after fadeIn is started for 100ms
        mActivityRule.runOnUiThread(() ->
                hideNShow.start()
        );

        verify(adapter, timeout(800)).onAnimationEnd(hideNShow, false);
        // Now that the hideNShow finished we need to check whether the fadeIn animation ran again.
        assertEquals(1f, mActivity.view.getAlpha(), 0);

    }

    /**
     *
     * This test verifies that custom ValueAnimators will be start()'ed in a set.
     */
    @Test
    public void testChildAnimatorStartCalled() throws Throwable {
        MyValueAnimator a1 = new MyValueAnimator();
        MyValueAnimator a2 = new MyValueAnimator();
        AnimatorSet set = new AnimatorSet();
        set.playTogether(a1, a2);
        mActivityRule.runOnUiThread(() -> {
            set.start();
            assertTrue(a1.mStartCalled);
            assertTrue(a2.mStartCalled);
        });
    }

    @Test
    public void seekSequentially() {
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.setStartDelay(10);
        ValueAnimator animator1 = ValueAnimator.ofFloat(0f, 1f);
        animator1.setInterpolator(null);
        animator1.setStartDelay(5);
        ValueAnimator animator2 = ValueAnimator.ofFloat(0f, 1f);
        animator2.setInterpolator(null);
        animator2.setStartDelay(5);
        animatorSet.playSequentially(animator1, animator2);
        animatorSet.setCurrentPlayTime(1);
        assertEquals(1L, animatorSet.getCurrentPlayTime());
        assertEquals(0f, (float) animator1.getAnimatedValue(), EPSILON);
        assertEquals(0f, (float) animator2.getAnimatedValue(), EPSILON);
        animatorSet.setCurrentPlayTime(15);
        assertEquals(15L, animatorSet.getCurrentPlayTime());
        assertEquals(0f, (float) animator1.getAnimatedValue(), EPSILON);
        assertEquals(0f, (float) animator2.getAnimatedValue(), EPSILON);
        animatorSet.setCurrentPlayTime(17);
        assertEquals(17L, animatorSet.getCurrentPlayTime());
        assertEquals(2f / 300f, (float) animator1.getAnimatedValue(), EPSILON);
        assertEquals(0f, (float) animator2.getAnimatedValue(), EPSILON);
        animatorSet.setCurrentPlayTime(316);
        assertEquals(316, animatorSet.getCurrentPlayTime());
        assertEquals(1f, (float) animator1.getAnimatedValue(), EPSILON);
        assertEquals(0f, (float) animator2.getAnimatedValue(), EPSILON);
        animatorSet.setCurrentPlayTime(320);
        assertEquals(320, animatorSet.getCurrentPlayTime());
        assertEquals(1f, (float) animator1.getAnimatedValue(), EPSILON);
        assertEquals(0f, (float) animator2.getAnimatedValue(), EPSILON);
        animatorSet.setCurrentPlayTime(321);
        assertEquals(321, animatorSet.getCurrentPlayTime());
        assertEquals(1f, (float) animator1.getAnimatedValue(), EPSILON);
        assertEquals(1f / 300f, (float) animator2.getAnimatedValue(), EPSILON);
        animatorSet.setCurrentPlayTime(320);
        assertEquals(320, animatorSet.getCurrentPlayTime());
        assertEquals(1f, (float) animator1.getAnimatedValue(), EPSILON);
        assertEquals(0f, (float) animator2.getAnimatedValue(), EPSILON);
        animatorSet.setCurrentPlayTime(314);
        assertEquals(314, animatorSet.getCurrentPlayTime());
        assertEquals(299f / 300f, (float) animator1.getAnimatedValue(), EPSILON);
        assertEquals(0f, (float) animator2.getAnimatedValue(), EPSILON);
    }

    @Test
    public void seekSequentiallyWithRepeats() {
        AnimatorSet animatorSet = new AnimatorSet();
        ValueAnimator animator1 = ValueAnimator.ofFloat(0f, 1f);
        animator1.setInterpolator(null);
        animator1.setRepeatCount(1);
        ValueAnimator animator2 = ValueAnimator.ofFloat(0f, 1f);
        animator2.setInterpolator(null);
        animator2.setRepeatCount(ValueAnimator.INFINITE);
        animatorSet.playSequentially(animator1, animator2);
        animatorSet.setCurrentPlayTime(1);
        assertEquals(1L, animatorSet.getCurrentPlayTime());
        assertEquals(1f / 300f, (float) animator1.getAnimatedValue(), EPSILON);
        assertEquals(0f, (float) animator2.getAnimatedValue(), EPSILON);
        animatorSet.setCurrentPlayTime(301);
        assertEquals(301, animatorSet.getCurrentPlayTime());
        assertEquals(1f / 300f, (float) animator1.getAnimatedValue(), EPSILON);
        assertEquals(0f, (float) animator2.getAnimatedValue(), EPSILON);
        animatorSet.setCurrentPlayTime(699);
        assertEquals(699, animatorSet.getCurrentPlayTime());
        assertEquals(99f / 300f, (float) animator2.getAnimatedValue(), EPSILON);
        animatorSet.setCurrentPlayTime(601);
        assertEquals(601, animatorSet.getCurrentPlayTime());
        assertEquals(1f / 300f, (float) animator2.getAnimatedValue(), EPSILON);
        animatorSet.setCurrentPlayTime(599);
        assertEquals(599, animatorSet.getCurrentPlayTime());
        assertEquals(299f / 300f, (float) animator1.getAnimatedValue(), EPSILON);
        assertEquals(0f, (float) animator2.getAnimatedValue(), EPSILON);
    }

    @Test
    public void seekReverse() throws Throwable {
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.setStartDelay(10);
        ValueAnimator animator1 = ValueAnimator.ofFloat(0f, 1f);
        animator1.setInterpolator(null);
        animator1.setStartDelay(5);
        ValueAnimator animator2 = ValueAnimator.ofFloat(0f, 1f);
        animator2.setInterpolator(null);
        animator2.setStartDelay(5);
        animatorSet.playSequentially(animator1, animator2);
        mActivityRule.runOnUiThread(() -> {
            animatorSet.reverse();
            animatorSet.pause();
            assertEquals(1f, (float) animator1.getAnimatedValue(), EPSILON);
            assertEquals(1f, (float) animator2.getAnimatedValue(), EPSILON);
            animatorSet.setCurrentPlayTime(1);
            assertEquals(1f, (float) animator1.getAnimatedValue(), EPSILON);
            assertEquals(299f / 300f, (float) animator2.getAnimatedValue(), EPSILON);
            animatorSet.setCurrentPlayTime(300);
            assertEquals(1f, (float) animator1.getAnimatedValue(), EPSILON);
            assertEquals(0f, (float) animator2.getAnimatedValue(), EPSILON);
            animatorSet.setCurrentPlayTime(305);
            assertEquals(1f, (float) animator1.getAnimatedValue(), EPSILON);
            assertEquals(0f, (float) animator2.getAnimatedValue(), EPSILON);
            animatorSet.setCurrentPlayTime(306);
            assertEquals(299f / 300f, (float) animator1.getAnimatedValue(), EPSILON);
            assertEquals(0f, (float) animator2.getAnimatedValue(), EPSILON);
            animatorSet.setCurrentPlayTime(604);
            assertEquals(1f / 300f, (float) animator1.getAnimatedValue(), EPSILON);
            assertEquals(0f, (float) animator2.getAnimatedValue(), EPSILON);
            animatorSet.setCurrentPlayTime(610);
            assertEquals(0f, (float) animator1.getAnimatedValue(), EPSILON);
            assertEquals(0f, (float) animator2.getAnimatedValue(), EPSILON);
            animatorSet.setCurrentPlayTime(604);
            assertEquals(1f / 300f, (float) animator1.getAnimatedValue(), EPSILON);
            assertEquals(0f, (float) animator2.getAnimatedValue(), EPSILON);
            animatorSet.setCurrentPlayTime(305);
            assertEquals(1f, (float) animator1.getAnimatedValue(), EPSILON);
            assertEquals(0f, (float) animator2.getAnimatedValue(), EPSILON);
        });
    }

    @Test
    public void seekForwardOrder() {
        TargetObj target = new TargetObj();
        target.setVal(20f);
        ObjectAnimator animator1 = ObjectAnimator.ofFloat(target, "val", 0f, 100f);
        animator1.setInterpolator(null);
        ObjectAnimator animator2 = ObjectAnimator.ofFloat(target, "val", 100f, 0f);
        animator2.setInterpolator(null);
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playSequentially(animator1, animator2);

        animatorSet.setCurrentPlayTime(0);
        assertEquals(0f, target.value, EPSILON);

        animatorSet.setCurrentPlayTime(200);
        assertEquals(100f * 200f / 300f, target.value, EPSILON);

        animatorSet.setCurrentPlayTime(300);
        assertEquals(100f, target.value, EPSILON);

        animatorSet.setCurrentPlayTime(350);
        assertEquals(100f * 250f / 300f, target.value, EPSILON);

        animatorSet.setCurrentPlayTime(600);
        assertEquals(0f, target.value, EPSILON);

        animatorSet.setCurrentPlayTime(250);
        assertEquals(100f * 250f / 300f, target.value, EPSILON);

        animatorSet.setCurrentPlayTime(0);
        assertEquals(0f, target.value, EPSILON);
    }

    @Test
    public void seekBackwardOrder() throws Throwable {
        TargetObj target = new TargetObj();
        target.setVal(20f);
        ObjectAnimator animator1 = ObjectAnimator.ofFloat(target, "val", 0f, 100f);
        animator1.setInterpolator(null);
        ObjectAnimator animator2 = ObjectAnimator.ofFloat(target, "val", 100f, 0f);
        animator2.setInterpolator(null);
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playSequentially(animator1, animator2);

        mActivityRule.runOnUiThread(() -> {
            animatorSet.reverse();
            animatorSet.pause();

            animatorSet.setCurrentPlayTime(0);
            assertEquals(0f, target.value, EPSILON);

            animatorSet.setCurrentPlayTime(200);
            assertEquals(100f * 200f / 300f, target.value, EPSILON);

            animatorSet.setCurrentPlayTime(300);
            assertEquals(100f, target.value, EPSILON);

            animatorSet.setCurrentPlayTime(350);
            assertEquals(100f * 250f / 300f, target.value, EPSILON);

            animatorSet.setCurrentPlayTime(600);
            assertEquals(0f, target.value, EPSILON);

            animatorSet.setCurrentPlayTime(250);
            assertEquals(100f * 250f / 300f, target.value, EPSILON);

            animatorSet.setCurrentPlayTime(0);
            assertEquals(0f, target.value, EPSILON);
        });
    }

    @Test
    public void seekNestedSets() {
        TargetObj target = new TargetObj();
        target.setVal(20f);
        ObjectAnimator animator1 = ObjectAnimator.ofFloat(target, "val", 0f, 100f);
        animator1.setInterpolator(null);
        ObjectAnimator animator2 = ObjectAnimator.ofFloat(target, "val", 100f, 0f);
        animator2.setInterpolator(null);
        AnimatorSet animatorSet1 = new AnimatorSet();
        animatorSet1.playSequentially(animator1, animator2);
        ValueAnimator animator3 = ValueAnimator.ofFloat(0f, 1f);
        animator3.setInterpolator(null);
        animator3.setStartDelay(100);
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(animatorSet1, animator3);

        animatorSet.setCurrentPlayTime(0);
        assertEquals(0f, target.value, EPSILON);
        assertEquals(0f, (float) animator3.getAnimatedValue(), EPSILON);

        animatorSet.setCurrentPlayTime(200);
        assertEquals(100f * 200f / 300f, target.value, EPSILON);
        assertEquals(100f / 300f, (float) animator3.getAnimatedValue(), EPSILON);

        animatorSet.setCurrentPlayTime(300);
        assertEquals(100f, target.value, EPSILON);
        assertEquals(200f / 300f, (float) animator3.getAnimatedValue(), EPSILON);

        animatorSet.setCurrentPlayTime(350);
        assertEquals(100f * 250f / 300f, target.value, EPSILON);
        assertEquals(250f / 300f, (float) animator3.getAnimatedValue(), EPSILON);

        animatorSet.setCurrentPlayTime(600);
        assertEquals(0f, target.value, EPSILON);
        assertEquals(1f, (float) animator3.getAnimatedValue(), EPSILON);

        animatorSet.setCurrentPlayTime(250);
        assertEquals(100f * 250f / 300f, target.value, EPSILON);
        assertEquals(150f / 300f, (float) animator3.getAnimatedValue(), EPSILON);

        animatorSet.setCurrentPlayTime(100);
        assertEquals(100f * 100f / 300f, target.value, EPSILON);
        assertEquals(0f, (float) animator3.getAnimatedValue(), EPSILON);

        animatorSet.setCurrentPlayTime(0);
        assertEquals(0f, target.value, EPSILON);
        assertEquals(0f, (float) animator3.getAnimatedValue(), EPSILON);
    }

    @Test
    public void seekWithNestedAnimatorSetsAndDelays() {
        AnimatorSet animatorSet1 = new AnimatorSet();
        animatorSet1.setStartDelay(100);
        ValueAnimator animator1 = ValueAnimator.ofFloat(0f, 100f);
        animator1.setInterpolator(null);
        animator1.setStartDelay(200);
        animatorSet1.play(animator1);
        AnimatorSet animatorSet2 = new AnimatorSet();
        animatorSet2.setStartDelay(400);
        animatorSet2.play(animatorSet1);
        AnimatorSet animatorSet3 = new AnimatorSet();
        animatorSet3.play(animatorSet2);

        animatorSet3.setCurrentPlayTime(0);
        assertEquals(0f, (float) animator1.getAnimatedValue(), EPSILON);
        animatorSet3.setCurrentPlayTime(400);
        assertEquals(0f, (float) animator1.getAnimatedValue(), EPSILON);
        animatorSet3.setCurrentPlayTime(500);
        assertEquals(0f, (float) animator1.getAnimatedValue(), EPSILON);
        animatorSet3.setCurrentPlayTime(700);
        assertEquals(0f, (float) animator1.getAnimatedValue(), EPSILON);
        animatorSet3.setCurrentPlayTime(850);
        assertEquals(50f, (float) animator1.getAnimatedValue(), EPSILON);
        animatorSet3.setCurrentPlayTime(1000);
        assertEquals(100f, (float) animator1.getAnimatedValue(), EPSILON);
    }

    @Test
    public void seekReversedWithNestedAnimatorSetsAndDelays() throws Throwable {
        AnimatorSet animatorSet1 = new AnimatorSet();
        animatorSet1.setStartDelay(100);
        ValueAnimator animator1 = ValueAnimator.ofFloat(0f, 100f);
        animator1.setInterpolator(null);
        animator1.setStartDelay(200);
        animatorSet1.play(animator1);
        AnimatorSet animatorSet2 = new AnimatorSet();
        animatorSet2.setStartDelay(400);
        animatorSet2.play(animatorSet1);
        AnimatorSet animatorSet3 = new AnimatorSet();
        animatorSet3.play(animatorSet2);

        mActivityRule.runOnUiThread(() -> {
            animatorSet3.reverse();
            animatorSet3.pause();
            animatorSet3.setCurrentPlayTime(0);
            assertEquals(100f, (float) animator1.getAnimatedValue(), EPSILON);
            animatorSet3.setCurrentPlayTime(150);
            assertEquals(50f, (float) animator1.getAnimatedValue(), EPSILON);
            animatorSet3.setCurrentPlayTime(300);
            assertEquals(0f, (float) animator1.getAnimatedValue(), EPSILON);
            animatorSet3.setCurrentPlayTime(500);
            assertEquals(0f, (float) animator1.getAnimatedValue(), EPSILON);
            animatorSet3.setCurrentPlayTime(600);
            assertEquals(0f, (float) animator1.getAnimatedValue(), EPSILON);
            animatorSet3.setCurrentPlayTime(1000);
            assertEquals(0f, (float) animator1.getAnimatedValue(), EPSILON);
        });
    }

    /**
     * This test sets up an AnimatorSet that contains two sequential animations. The first animation
     * is infinite, the second animation therefore has an infinite start time. This test verifies
     * that the infinite start time is handled correctly.
     */
    @Test
    public void testInfiniteStartTime() throws Throwable {
        ValueAnimator a1 = ValueAnimator.ofFloat(0f, 1f);
        a1.setRepeatCount(ValueAnimator.INFINITE);
        ValueAnimator a2 = ValueAnimator.ofFloat(0f, 1f);

        AnimatorSet set = new AnimatorSet();
        set.playSequentially(a1, a2);

        mActivityRule.runOnUiThread(() -> {
            set.start();
        });

        assertEquals(Animator.DURATION_INFINITE, set.getTotalDuration());

        mActivityRule.runOnUiThread(() -> {
            set.end();
        });
    }

    @Test
    public void hugeDuration() throws Throwable {
        AnimatorSet animators = new AnimatorSet();
        TargetObj target = new TargetObj();
        ObjectAnimator animator = ObjectAnimator.ofFloat(target, "val", 1, 0);

        // The duration + repeat count will cause a total duration > MAXINT, which caused a
        // failure due to casting a resulting difference to an int (b/265674577)
        animator.setDuration(1000);
        animator.setRepeatCount(2147483);

        mActivityRule.runOnUiThread(() -> {
            animators.play(animator);
            animators.start();
        });
    }

    @Test
    public void childListenersCalledWhilePaused() throws Throwable {
        AnimationCountListener setListener1 = new AnimationCountListener();
        AnimationCountListener setListener2 = new AnimationCountListener();
        AnimatorSet animatorSet1 = new AnimatorSet();
        animatorSet1.addListener(setListener1);
        AnimatorSet animatorSet2 = new AnimatorSet();
        animatorSet2.addListener(setListener2);
        animatorSet2.setStartDelay(100);
        animatorSet1.play(animatorSet2);

        AnimatorSet animatorSet3 = new AnimatorSet();
        animatorSet2.play(animatorSet3);
        AnimationCountListener setListener3 = new AnimationCountListener();
        animatorSet3.addListener(setListener3);

        AnimationCountListener valueListener = new AnimationCountListener();
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 100f);
        animator.addListener(valueListener);
        animator.setStartDelay(50);
        animatorSet3.play(animator);

        mActivityRule.runOnUiThread(() -> {
            animatorSet1.start();
            // Just starting should trigger the starting animators
            setListener1.assertListenerCount(1, 0, 0, 0);
            setListener2.assertListenerCount(1, 0, 0, 0);
            setListener3.assertListenerCount(0, 0, 0, 0);
            valueListener.assertListenerCount(0, 0, 0, 0);

            animatorSet1.pause();

            // Setting the play time shouldn't trigger any more animators
            animatorSet1.setCurrentPlayTime(0);
            setListener1.assertListenerCount(1, 0, 0, 0);
            setListener2.assertListenerCount(1, 0, 0, 0);
            setListener3.assertListenerCount(0, 0, 0, 0);
            valueListener.assertListenerCount(0, 0, 0, 0);

            // We've passed the start delay, so other animators should start
            animatorSet1.setCurrentPlayTime(100);
            setListener1.assertListenerCount(1, 0, 0, 0);
            setListener2.assertListenerCount(1, 0, 0, 0);
            setListener3.assertListenerCount(1, 0, 0, 0);
            valueListener.assertListenerCount(1, 0, 0, 0);

            // Reached the end of the animators, so all should end in the forward direction.
            animatorSet1.setCurrentPlayTime(450);
            setListener1.assertListenerCount(1, 0, 1, 0);
            setListener2.assertListenerCount(1, 0, 1, 0);
            setListener3.assertListenerCount(1, 0, 1, 0);
            valueListener.assertListenerCount(1, 0, 1, 0);

            // Go back towards the start should cause the animators to start in reverse
            animatorSet1.setCurrentPlayTime(101);
            setListener1.assertListenerCount(1, 1, 1, 0);
            setListener2.assertListenerCount(1, 1, 1, 0);
            setListener3.assertListenerCount(1, 1, 1, 0);
            valueListener.assertListenerCount(1, 1, 1, 0);

            // Now we've reached the start delay, so some animators finish
            animatorSet1.setCurrentPlayTime(100);
            setListener1.assertListenerCount(1, 1, 1, 0);
            setListener2.assertListenerCount(1, 1, 1, 0);
            setListener3.assertListenerCount(1, 1, 1, 1);
            valueListener.assertListenerCount(1, 1, 1, 1);

            // Now we're back at the beginning, so all animators will finish in reverse
            animatorSet1.setCurrentPlayTime(0);
            setListener1.assertListenerCount(1, 1, 1, 1);
            setListener2.assertListenerCount(1, 1, 1, 1);
            setListener3.assertListenerCount(1, 1, 1, 1);
            valueListener.assertListenerCount(1, 1, 1, 1);

            // Go forward to the middle, we'll have another start in the forward direction
            animatorSet1.setCurrentPlayTime(300);
            setListener1.assertListenerCount(2, 1, 1, 1);
            setListener2.assertListenerCount(2, 1, 1, 1);
            setListener3.assertListenerCount(2, 1, 1, 1);
            valueListener.assertListenerCount(2, 1, 1, 1);

            // When we go back, it should end in the reverse direction
            animatorSet1.setCurrentPlayTime(0);
            setListener1.assertListenerCount(2, 1, 1, 2);
            setListener2.assertListenerCount(2, 1, 1, 2);
            setListener3.assertListenerCount(2, 1, 1, 2);
            valueListener.assertListenerCount(2, 1, 1, 2);
        });
    }

    @Test
    public void childListenersCalledReversed() throws Throwable {
        AnimationCountListener setListener = new AnimationCountListener();
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.addListener(setListener);

        AnimationCountListener valueListener = new AnimationCountListener();
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 100f);
        animator.addListener(valueListener);
        animator.setRepeatCount(1);
        animator.setStartDelay(50);
        animatorSet.play(animator);

        mActivityRule.runOnUiThread(() -> {
            animatorSet.reverse();
            // Just starting should trigger the starting animators
            setListener.assertListenerCount(0, 1, 0, 0);
            valueListener.assertListenerCount(0, 1, 0, 0);

            // Setting the play time shouldn't trigger any more animators
            animatorSet.setCurrentPlayTime(0);
            setListener.assertListenerCount(0, 1, 0, 0);
            valueListener.assertListenerCount(0, 1, 0, 0);

            // Middle of the ValueAnimator
            animatorSet.setCurrentPlayTime(300);
            setListener.assertListenerCount(0, 1, 0, 0);
            valueListener.assertListenerCount(0, 1, 0, 0);

            // Reached the end of the ValueAnimator, but not the start delay
            animatorSet.setCurrentPlayTime(600);
            setListener.assertListenerCount(0, 1, 0, 0);
            valueListener.assertListenerCount(0, 1, 0, 0);

            // End of the startDelay
            animatorSet.setCurrentPlayTime(650);
            setListener.assertListenerCount(0, 1, 0, 1);
            valueListener.assertListenerCount(0, 1, 0, 1);
        });
    }

    @Test
    public void childListenersCalledWithNoStart() {
        AnimationCountListener setListener1 = new AnimationCountListener();
        AnimationCountListener setListener2 = new AnimationCountListener();
        AnimatorSet animatorSet1 = new AnimatorSet();
        animatorSet1.addListener(setListener1);
        AnimatorSet animatorSet2 = new AnimatorSet();
        animatorSet2.addListener(setListener2);
        animatorSet2.setStartDelay(100);
        animatorSet1.play(animatorSet2);

        AnimatorSet animatorSet3 = new AnimatorSet();
        animatorSet2.play(animatorSet3);
        AnimationCountListener setListener3 = new AnimationCountListener();
        animatorSet3.addListener(setListener3);

        AnimationCountListener valueListener = new AnimationCountListener();
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 100f);
        animator.addListener(valueListener);
        animator.setStartDelay(50);
        animatorSet3.play(animator);

        // Nothing is started, so no listeners should be called
        setListener1.assertListenerCount(0, 0, 0, 0);
        setListener2.assertListenerCount(0, 0, 0, 0);
        setListener3.assertListenerCount(0, 0, 0, 0);
        valueListener.assertListenerCount(0, 0, 0, 0);

        // Just setting the play time should start some listeners
        animatorSet1.setCurrentPlayTime(0);

        setListener1.assertListenerCount(1, 0, 0, 0);
        setListener2.assertListenerCount(1, 0, 0, 0);
        setListener3.assertListenerCount(0, 0, 0, 0);
        valueListener.assertListenerCount(0, 0, 0, 0);

        // We've passed the start delay, so other animators should start
        animatorSet1.setCurrentPlayTime(100);
        setListener1.assertListenerCount(1, 0, 0, 0);
        setListener2.assertListenerCount(1, 0, 0, 0);
        setListener3.assertListenerCount(1, 0, 0, 0);
        valueListener.assertListenerCount(1, 0, 0, 0);

        // Reached the end of the animators, so all should end in the forward direction.
        animatorSet1.setCurrentPlayTime(450);
        setListener1.assertListenerCount(1, 0, 1, 0);
        setListener2.assertListenerCount(1, 0, 1, 0);
        setListener3.assertListenerCount(1, 0, 1, 0);
        valueListener.assertListenerCount(1, 0, 1, 0);

        // Go back towards the start should cause the animators to start in reverse
        animatorSet1.setCurrentPlayTime(101);
        setListener1.assertListenerCount(1, 1, 1, 0);
        setListener2.assertListenerCount(1, 1, 1, 0);
        setListener3.assertListenerCount(1, 1, 1, 0);
        valueListener.assertListenerCount(1, 1, 1, 0);

        // Now we've reached the start delay, so some animators finish
        animatorSet1.setCurrentPlayTime(100);
        setListener1.assertListenerCount(1, 1, 1, 0);
        setListener2.assertListenerCount(1, 1, 1, 0);
        setListener3.assertListenerCount(1, 1, 1, 1);
        valueListener.assertListenerCount(1, 1, 1, 1);

        // Now we're back at the beginning, so all animators will finish in reverse
        animatorSet1.setCurrentPlayTime(0);
        setListener1.assertListenerCount(1, 1, 1, 1);
        setListener2.assertListenerCount(1, 1, 1, 1);
        setListener3.assertListenerCount(1, 1, 1, 1);
        valueListener.assertListenerCount(1, 1, 1, 1);

        // Go forward to the middle, we'll have another start in the forward direction
        animatorSet1.setCurrentPlayTime(300);
        setListener1.assertListenerCount(2, 1, 1, 1);
        setListener2.assertListenerCount(2, 1, 1, 1);
        setListener3.assertListenerCount(2, 1, 1, 1);
        valueListener.assertListenerCount(2, 1, 1, 1);

        // When we go back, it should end in the reverse direction
        animatorSet1.setCurrentPlayTime(0);
        setListener1.assertListenerCount(2, 1, 1, 2);
        setListener2.assertListenerCount(2, 1, 1, 2);
        setListener3.assertListenerCount(2, 1, 1, 2);
        valueListener.assertListenerCount(2, 1, 1, 2);
    }

    static class AnimationCountListener implements Animator.AnimatorListener {
        public int startForward;
        public int startReversed;
        public int endForward;
        public int endReversed;

        @Override
        public void onAnimationStart(@NonNull Animator animation, boolean isReverse) {
            if (isReverse) {
                startReversed++;
            } else {
                startForward++;
            }
        }

        @Override
        public void onAnimationEnd(@NonNull Animator animation, boolean isReverse) {
            if (isReverse) {
                endReversed++;
            } else {
                endForward++;
            }
        }

        @Override
        public void onAnimationStart(@NonNull Animator animation) {
        }

        @Override
        public void onAnimationEnd(@NonNull Animator animation) {
        }

        @Override
        public void onAnimationCancel(@NonNull Animator animation) {
        }

        @Override
        public void onAnimationRepeat(@NonNull Animator animation) {
        }

        public void assertListenerCount(
                int startForward,
                int startReversed,
                int endForward,
                int endReversed
        ) {
            assertEquals(startForward, this.startForward);
            assertEquals(startReversed, this.startReversed);
            assertEquals(endForward, this.endForward);
            assertEquals(endReversed, this.endReversed);
        }
    }

    static class TargetObj {
        public float value = 0;

        public void setVal(float value) {
            this.value = value;
        }

        public float getVal() {
            return value;
        }
    }

    class AnimateObject {
        int x = 1;
        int y = 2;
    }

    static class MyListener extends AnimatorListenerAdapter {
        boolean mStartIsCalled = false;
        boolean mEndIsCalled = false;

        public void onAnimationStart(Animator animation) {
            mStartIsCalled = true;
        }

        public void onAnimationEnd(Animator animation) {
            mEndIsCalled = true;
        }
    }

    static class MyValueAnimator extends ValueAnimator {
        boolean mStartCalled = false;
        @Override
        public void start() {
            // Do not call super intentionally.
            mStartCalled = true;
        }
    }
}
