/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.view.animation.cts;

import static android.view.flags.Flags.FLAG_EXPECTED_PRESENTATION_TIME_API;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.Activity;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.GridLayoutAnimationController;
import android.view.animation.Interpolator;
import android.view.animation.LayoutAnimationController;
import android.view.cts.R;

import androidx.test.filters.MediumTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class AnimationUtilsTest {
    private Activity mActivity;

    private static final long NANOS_PER_MS = 1000000;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule(order = 0)
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            androidx.test.platform.app.InstrumentationRegistry
                    .getInstrumentation().getUiAutomation(),
            Manifest.permission.START_ACTIVITIES_FROM_SDK_SANDBOX);

    @Rule(order = 1)
    public ActivityTestRule<AnimationTestCtsActivity> mActivityRule =
            new ActivityTestRule<>(AnimationTestCtsActivity.class);

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
    }

    @Test
    public void testLoad() {
        // XML file of android.view.cts.R.anim.anim_alpha
        // <alpha xmlns:android="http://schemas.android.com/apk/res/android"
        //      android:interpolator="@android:anim/accelerate_interpolator"
        //      android:fromAlpha="0.0"
        //      android:toAlpha="1.0"
        //      android:duration="500" />
        int duration = 500;
        Animation animation = AnimationUtils.loadAnimation(mActivity, R.anim.anim_alpha);
        assertEquals(duration, animation.getDuration());
        assertTrue(animation instanceof AlphaAnimation);

        // Load Interpolator from android.R.anim.accelerate_interpolator
        Interpolator interpolator = AnimationUtils.loadInterpolator(mActivity,
                android.R.anim.accelerate_interpolator);
        assertTrue(interpolator instanceof AccelerateInterpolator);

        // Load LayoutAnimationController from android.view.cts.R.anim.anim_gridlayout
        // <gridLayoutAnimation xmlns:android="http://schemas.android.com/apk/res/android"
        //      android:delay="10%"
        //      android:rowDelay="50%"
        //      android:directionPriority="column"
        //      android:animation="@anim/anim_alpha" />
        LayoutAnimationController controller = AnimationUtils.loadLayoutAnimation(mActivity,
                R.anim.anim_gridlayout);
        assertTrue(controller instanceof GridLayoutAnimationController);
        assertEquals(duration, controller.getAnimation().getDuration());
        assertEquals(0.1f, controller.getDelay(), 0.001f);
    }

    @Test
    public void testMakeAnimation() {
        Animation inAnimation = AnimationUtils.makeInAnimation(mActivity, true);
        assertNotNull(inAnimation);
        Animation outAnimation = AnimationUtils.makeOutAnimation(mActivity, true);
        assertNotNull(outAnimation);
        Animation bottomAnimation = AnimationUtils.makeInChildBottomAnimation(mActivity);
        assertNotNull(bottomAnimation);
        // TODO: How to assert these Animations.
    }

    @Test
    public void testCurrentAnimationTimeMillis() {
        long time1 = AnimationUtils.currentAnimationTimeMillis();
        assertTrue(time1 > 0);

        long time2 = 0L;
        for (int i = 0; i < 1000 && time1 >= time2; i++) {
            time2 = AnimationUtils.currentAnimationTimeMillis();
            try {
                Thread.sleep(1);
            } catch (java.lang.InterruptedException e) {
            }
            assertTrue(time2 > 0);
        }
        assertTrue(time2 > time1);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_EXPECTED_PRESENTATION_TIME_API)
    public void testGetExpectedPresentationTimeNanos() {
        long vsyncMillis = 1349311227921L;
        long expectedPresentationTimeNanos = 255073580723571L;
        AnimationUtils.lockAnimationClock(vsyncMillis, expectedPresentationTimeNanos);
        assertEquals(AnimationUtils.currentAnimationTimeMillis(), vsyncMillis);
        assertEquals(AnimationUtils.getExpectedPresentationTimeNanos(),
                expectedPresentationTimeNanos);
        assertEquals(AnimationUtils.getExpectedPresentationTimeMillis(),
                expectedPresentationTimeNanos / NANOS_PER_MS);
        AnimationUtils.unlockAnimationClock();
    }
}
