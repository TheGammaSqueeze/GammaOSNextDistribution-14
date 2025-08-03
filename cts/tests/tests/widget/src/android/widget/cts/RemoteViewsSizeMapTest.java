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

package android.widget.cts;

import static org.junit.Assert.assertEquals;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Parcel;
import android.util.SizeF;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import android.widget.TextView;

import androidx.test.InstrumentationRegistry;
import androidx.test.annotation.UiThreadTest;
import androidx.test.filters.MediumTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test {@link RemoteViews#RemoteViews(Map<SizeF, RemoteViews>)}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class RemoteViewsSizeMapTest {
    private static final String PACKAGE_NAME = "android.widget.cts";

    private static final int INVALID_ID = -1;

    private static final long TEST_TIMEOUT = 5000;

    @Rule
    public ActivityTestRule<RemoteViewsCtsActivity> mActivityRule =
            new ActivityTestRule<>(RemoteViewsCtsActivity.class);

    @Rule
    public ExpectedException mExpectedException = ExpectedException.none();

    private Instrumentation mInstrumentation;

    private Context mContext;

    private RemoteViews mRemoteViews;

    private View mResult;

    private List<SizeF> mSizes;
    private Map<SizeF, RemoteViews> mRemoteViewsSizeMap;

    @UiThreadTest
    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getTargetContext();

        mSizes = new ArrayList<>();
        mSizes.add(new SizeF(100, 100));
        mSizes.add(new SizeF(100, 150));
        mSizes.add(new SizeF(150, 130));
        mSizes.add(new SizeF(200, 200));

        mRemoteViewsSizeMap = new HashMap<>();
        for (int i = 0; i < mSizes.size(); i++) {
            RemoteViews remoteViews = new RemoteViews(PACKAGE_NAME,
                    i == 0 ? R.layout.remoteviews_small : R.layout.remoteviews_good);
            remoteViews.addView(
                    R.id.remoteView_linear,
                    new RemoteViews(PACKAGE_NAME, R.layout.remoteviews_small)
            );
            remoteViews.setLightBackgroundLayoutId(
                    i == 0 ? R.layout.remoteviews_good : R.layout.remoteviews_small);
            remoteViews.setTextViewText(R.id.remoteView_text, Integer.toString(i + 1));
            mRemoteViewsSizeMap.put(mSizes.get(i), remoteViews);
        }

        mRemoteViews = new RemoteViews(mRemoteViewsSizeMap);
    }

    @Test
    public void constructor_defaultIsSmallest() {
        assertEquals(R.layout.remoteviews_small, mRemoteViews.getLayoutId());

        mRemoteViews.addFlags(RemoteViews.FLAG_USE_LIGHT_BACKGROUND_LAYOUT);
        assertEquals(R.layout.remoteviews_good, mRemoteViews.getLayoutId());
    }

    private void applyRemoteViewOnUiThread(SizeF initialSize) {
        mResult = mRemoteViews.apply(mContext, null, null, initialSize);

        // Add our host view to the activity behind this test. This is similar to how launchers
        // add widgets to the on-screen UI.
        ViewGroup root = (ViewGroup) mActivityRule.getActivity().findViewById(R.id.remoteView_host);
        FrameLayout.MarginLayoutParams lp = new FrameLayout.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        mResult.setLayoutParams(lp);

        root.addView(mResult);
    }

    private void applyRemoteView(SizeF initialSize) throws Throwable {
        mActivityRule.runOnUiThread(() -> applyRemoteViewOnUiThread(initialSize));
    }

    @Test
    public void apply_withoutSize_shouldReturnSmallestLayout() throws Throwable {
        applyRemoteView(null);

        assertEquals("1", mResult.<TextView>findViewById(R.id.remoteView_text).getText());
    }

    @Test
    public void apply_withSmallSize_shouldReturnSmallLayout() throws Throwable {
        applyRemoteView(new SizeF(50, 50));

        assertEquals("1", mResult.<TextView>findViewById(R.id.remoteView_text).getText());
    }

    @Test
    public void apply_withLargeSize_shouldReturnLargestLayout() throws Throwable {
        applyRemoteView(new SizeF(500, 500));

        assertEquals("4", mResult.<TextView>findViewById(R.id.remoteView_text).getText());
    }

    @Test
    public void apply_withSize_shouldReturnClosestFittingLayoutWithMargin() throws Throwable {
        applyRemoteView(new SizeF(99.7f, 150));

        assertEquals("2", mResult.<TextView>findViewById(R.id.remoteView_text).getText());
    }

    @Test
    public void apply_withSize_shouldReturnClosestFittingLayout() throws Throwable {
        applyRemoteView(new SizeF(160, 150));

        assertEquals("3", mResult.<TextView>findViewById(R.id.remoteView_text).getText());
    }

    // Note about reapply: it should only be called if the size didn't change, as it cannot show a
    // new layout.
    @Test
    public void reapply_withoutSize_shouldReturnSmallestLayout() throws Throwable {
        applyRemoteView(null);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));

        assertEquals("1", mResult.<TextView>findViewById(R.id.remoteView_text).getText());
    }

    @Test
    public void reapply_withSmallSize_shouldReturnSmallLayout() throws Throwable {
        applyRemoteView(new SizeF(50, 50));
        mActivityRule.runOnUiThread(
                () -> mRemoteViews.reapply(mContext, mResult, null /* handler */,
                        new SizeF(50, 50), null /* colorResources */));

        assertEquals("1", mResult.<TextView>findViewById(R.id.remoteView_text).getText());
    }

    @Test
    public void reapply_witLargeSize_shouldReturnLargestLayout() throws Throwable {
        applyRemoteView(new SizeF(500, 500));
        mActivityRule.runOnUiThread(
                () -> mRemoteViews.reapply(mContext, mResult, null /* handler */,
                        new SizeF(500, 500), null /* colorResources */));

        assertEquals(mResult.<TextView>findViewById(R.id.remoteView_text).getText(), "4");
    }

    @Test
    public void reapply_withSize_shouldReturnClosestFittingLayoutWithMargin() throws Throwable {
        applyRemoteView(new SizeF(99.7f, 150));
        mActivityRule.runOnUiThread(
                () -> mRemoteViews.reapply(mContext, mResult, null /* handler */,
                        new SizeF(99.7f, 150), null /* colorResources */));

        assertEquals("2", mResult.<TextView>findViewById(R.id.remoteView_text).getText());
    }

    @Test
    public void reapply_withSize_shouldReturnClosestFittingLayout() throws Throwable {
        applyRemoteView(new SizeF(160, 150));
        mActivityRule.runOnUiThread(
                () -> mRemoteViews.reapply(mContext, mResult, null /* handler */,
                        new SizeF(160, 150), null /* colorResources */));

        assertEquals("3", mResult.<TextView>findViewById(R.id.remoteView_text).getText());
    }

    @Test
    public void writeToParcel() throws Throwable {
        Parcel p = Parcel.obtain();
        mRemoteViews.writeToParcel(p, 0);
        p.setDataPosition(0);
        mRemoteViews = new RemoteViews(p);
        p.recycle();
        applyRemoteView(new SizeF(160, 150));

        assertEquals("3", mResult.<TextView>findViewById(R.id.remoteView_text).getText());
    }
}
