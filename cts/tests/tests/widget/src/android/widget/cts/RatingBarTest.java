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

package android.widget.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

import android.app.Instrumentation;
import android.content.Context;
import android.content.res.Configuration;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.RatingBar;

import androidx.test.InstrumentationRegistry;
import androidx.test.annotation.UiThreadTest;
import androidx.test.filters.SmallTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

/**
 * Test {@link RatingBar}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class RatingBarTest {
    private RatingBarCtsActivity mActivity;
    private RatingBar mRatingBar;
    private Instrumentation mInstrumentation;

    @Rule
    public ActivityTestRule<RatingBarCtsActivity> mActivityRule =
            new ActivityTestRule<>(RatingBarCtsActivity.class);

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mRatingBar = (RatingBar) mActivity.findViewById(R.id.ratingbar_constructor);
    }

    @Test
    public void testConstructor() {
        new RatingBar(mActivity);
        new RatingBar(mActivity, null);
        new RatingBar(mActivity, null, android.R.attr.ratingBarStyle);
        new RatingBar(mActivity, null, 0, android.R.style.Widget_DeviceDefault_RatingBar);
        new RatingBar(mActivity, null, 0, android.R.style.Widget_DeviceDefault_RatingBar_Indicator);
        new RatingBar(mActivity, null, 0, android.R.style.Widget_DeviceDefault_RatingBar_Small);
        new RatingBar(mActivity, null, 0, android.R.style.Widget_Material_RatingBar);
        new RatingBar(mActivity, null, 0, android.R.style.Widget_Material_RatingBar_Indicator);
        new RatingBar(mActivity, null, 0, android.R.style.Widget_Material_RatingBar_Small);
        new RatingBar(mActivity, null, 0, android.R.style.Widget_Material_Light_RatingBar);
        new RatingBar(mActivity, null, 0, android.R.style.Widget_Material_Light_RatingBar_Indicator);
        new RatingBar(mActivity, null, 0, android.R.style.Widget_Material_Light_RatingBar_Small);
    }

    @Test
    public void testAttributesFromLayout() {
        assertFalse(mRatingBar.isIndicator());
        assertEquals(50, mRatingBar.getNumStars());
        assertEquals(1.2f, mRatingBar.getRating(), 0.0f);
        assertEquals(0.2f, mRatingBar.getStepSize(), 0.0f);
    }

    @UiThreadTest
    @Test
    public void testAccessOnRatingBarChangeListener() {
        final RatingBar.OnRatingBarChangeListener listener =
                mock(RatingBar.OnRatingBarChangeListener.class);
        mRatingBar.setOnRatingBarChangeListener(listener);
        assertSame(listener, mRatingBar.getOnRatingBarChangeListener());
        verifyZeroInteractions(listener);

        // normal value
        mRatingBar.setRating(2.2f);
        verify(listener, times(1)).onRatingChanged(mRatingBar, 2.2f, false);

        // exceptional value
        mRatingBar.setOnRatingBarChangeListener(null);
        assertNull(mRatingBar.getOnRatingBarChangeListener());
        mRatingBar.setRating(1.2f);
        verifyNoMoreInteractions(listener);
    }

    @UiThreadTest
    @Test
    public void testAccessIndicator() {
        mRatingBar.setIsIndicator(true);
        assertTrue(mRatingBar.isIndicator());

        mRatingBar.setIsIndicator(false);
        assertFalse(mRatingBar.isIndicator());
    }

    @UiThreadTest
    @Test
    public void testAccessNumStars() {
        // normal value
        mRatingBar.setNumStars(20);
        assertEquals(20, mRatingBar.getNumStars());

        // invalid value - the currently set one stays
        mRatingBar.setNumStars(-10);
        assertEquals(20, mRatingBar.getNumStars());

        mRatingBar.setNumStars(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, mRatingBar.getNumStars());
    }

    @UiThreadTest
    @Test
    public void testAccessRating() {
        // normal value
        mRatingBar.setRating(2.0f);
        assertEquals(2.0f, mRatingBar.getRating(), 0.0f);

        // exceptional value
        mRatingBar.setRating(-2.0f);
        assertEquals(0f, mRatingBar.getRating(), 0.0f);

        mRatingBar.setRating(Float.MAX_VALUE);
        assertEquals((float) mRatingBar.getNumStars(), mRatingBar.getRating(), 0.0f);
    }

    @UiThreadTest
    @Test
    public void testSetMax() {
        // normal value
        mRatingBar.setMax(10);
        assertEquals(10, mRatingBar.getMax());

        mRatingBar.setProgress(10);

        // exceptional values
        mRatingBar.setMax(-10);
        assertEquals(10, mRatingBar.getMax());
        assertEquals(10, mRatingBar.getProgress());

        mRatingBar.setMax(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, mRatingBar.getMax());
    }

    @UiThreadTest
    @Test
    public void testAccessStepSize() {
        // normal value
        mRatingBar.setStepSize(1.5f);
        final float expectedMax = mRatingBar.getNumStars() / mRatingBar.getStepSize();
        final float expectedProgress = expectedMax / mRatingBar.getMax() * mRatingBar.getProgress();
        assertEquals((int) expectedMax, mRatingBar.getMax());
        assertEquals((int) expectedProgress, mRatingBar.getProgress());
        assertEquals((float) mRatingBar.getNumStars() / (int) (mRatingBar.getNumStars() / 1.5f),
                mRatingBar.getStepSize(), 0.0f);

        final int currentMax = mRatingBar.getMax();
        final int currentProgress = mRatingBar.getProgress();
        final float currentStepSize = mRatingBar.getStepSize();
        // exceptional value
        mRatingBar.setStepSize(-1.5f);
        assertEquals(currentMax, mRatingBar.getMax());
        assertEquals(currentProgress, mRatingBar.getProgress());
        assertEquals(currentStepSize, mRatingBar.getStepSize(), 0.0f);

        mRatingBar.setStepSize(0f);
        assertEquals(currentMax, mRatingBar.getMax());
        assertEquals(currentProgress, mRatingBar.getProgress());
        assertEquals(currentStepSize, mRatingBar.getStepSize(), 0.0f);

        mRatingBar.setStepSize(mRatingBar.getNumStars() + 0.1f);
        assertEquals(currentMax, mRatingBar.getMax());
        assertEquals(currentProgress, mRatingBar.getProgress());
        assertEquals(currentStepSize, mRatingBar.getStepSize(), 0.0f);

        mRatingBar.setStepSize(Float.MAX_VALUE);
        assertEquals(currentMax, mRatingBar.getMax());
        assertEquals(currentProgress, mRatingBar.getProgress());
        assertEquals(currentStepSize, mRatingBar.getStepSize(), 0.0f);
    }

    @Test
    @ApiTest(apis = {"android.widget.RatingBar#onInitializeAccessibilityNodeInfo"})
    public void testOnInitializeAccessibilityNodeInfo() throws Throwable {
        final Configuration configuration = new Configuration();
        configuration.setToDefaults();
        configuration.setLocale(Locale.ENGLISH);
        Context context = mActivity.createConfigurationContext(configuration);
        RatingBar ratingBar = new RatingBar(context);
        mInstrumentation.waitForIdleSync();
        ratingBar.setRating(1f);

        String expectedStateDescription = "One star out of 5";
        AccessibilityNodeInfo info = new AccessibilityNodeInfo();
        ratingBar.onInitializeAccessibilityNodeInfo(info);
        assertEquals(expectedStateDescription, info.getStateDescription());

        ratingBar.setNumStars(20);
        mInstrumentation.waitForIdleSync();
        ratingBar.setStepSize(0.5f);
        ratingBar.setRating(5.5f);

        expectedStateDescription = "5.5 stars out of 20";
        info = new AccessibilityNodeInfo();
        ratingBar.onInitializeAccessibilityNodeInfo(info);
        assertEquals(expectedStateDescription, info.getStateDescription());
    }
}
