/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.view.cts;

import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.makeMeasureSpec;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.View;
import android.view.ViewGroup;
import android.view.flags.Flags;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.google.common.truth.Expect;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test the behaviour of {@link View#requestLayout()} and the FORCE_LAYOUT flag.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class View_ForceLayout {
    private MockViewGroup mParent;
    private MockView mChild1;
    private MockView mChild2;

    @Rule
    public final Expect mExpect = Expect.create();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setup() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mParent = new MockViewGroup(context);
        mChild1 = new MockView(context);
        mChild2 = new MockView(context);

        mParent.addView(mChild1);
        mParent.addView(mChild2);
    }

    @Test
    public void requestLayout_onParent_notPropagatedToChildren() {
        measureAndLayoutForTest(mParent, /* width= */ 100, /* height= */ 100);

        mParent.requestLayout();

        mExpect.that(mParent.isLayoutRequested()).isTrue();
        mExpect.that(mChild1.isLayoutRequested()).isFalse();
        mExpect.that(mChild2.isLayoutRequested()).isFalse();
    }

    @Test
    public void requestLayout_onChild_propagatedToParentButNotSibling() {
        measureAndLayoutForTest(mParent, /* width= */ 100, /* height= */ 100);

        mChild1.requestLayout();

        mExpect.that(mParent.isLayoutRequested()).isTrue();
        mExpect.that(mChild1.isLayoutRequested()).isTrue();
        mExpect.that(mChild2.isLayoutRequested()).isFalse();
    }

    @Test
    public void traversal_afterParentRequestLayout_onlyParentRemeasured() {
        measureAndLayoutForTest(mParent, /* width= */ 100, /* height= */ 100);
        mParent.requestLayout();
        resetAllMocks();

        measureAndLayoutForTest(mParent, /* width= */ 100, /* height= */ 100);

        mExpect.that(mParent.hasCalledOnMeasure()).isTrue();
        mExpect.that(mParent.hasCalledOnLayout()).isTrue();
        // The following hit measure caches
        mExpect.that(mChild1.hasCalledOnMeasure()).isFalse();
        mExpect.that(mChild1.hasCalledOnLayout()).isFalse();
        mExpect.that(mChild2.hasCalledOnMeasure()).isFalse();
        mExpect.that(mChild2.hasCalledOnLayout()).isFalse();
    }

    @Test
    public void traversal_afterChildRequestLayout_onlyAncestorsRemeasured() {
        measureAndLayoutForTest(mParent, /* width= */ 100, /* height= */ 100);
        mChild1.requestLayout();
        resetAllMocks();

        measureAndLayoutForTest(mParent, /* width= */ 100, /* height= */ 100);

        mExpect.that(mChild1.hasCalledOnMeasure()).isTrue();
        mExpect.that(mChild1.hasCalledOnLayout()).isTrue();
        mExpect.that(mParent.hasCalledOnMeasure()).isTrue();
        mExpect.that(mParent.hasCalledOnLayout()).isTrue();
        // mChild2 will hit caches
        mExpect.that(mChild2.hasCalledOnMeasure()).isFalse();
        mExpect.that(mChild2.hasCalledOnLayout()).isFalse();
    }

    @Test
    public void traversal_afterAllChildrenRequestedLayut_everythingRemeasured() {
        measureAndLayoutForTest(mParent, /* width= */ 100, /* height= */ 100);
        mParent.requestLayout();
        mChild1.requestLayout();
        mChild2.requestLayout();
        resetAllMocks();

        measureAndLayoutForTest(mParent, /* width= */ 100, /* height= */ 100);

        mExpect.that(mChild1.hasCalledOnMeasure()).isTrue();
        mExpect.that(mChild1.hasCalledOnLayout()).isTrue();
        mExpect.that(mChild2.hasCalledOnMeasure()).isTrue();
        mExpect.that(mChild2.hasCalledOnLayout()).isTrue();
        mExpect.that(mParent.hasCalledOnMeasure()).isTrue();
        mExpect.that(mParent.hasCalledOnLayout()).isTrue();
    }


    @Test
    public void traversal_sameMeasureSpecsWithoutRequestLayout_nothingRemeasured() {
        measureAndLayoutForTest(mParent, /* width= */ 100, /* height= */ 100);
        resetAllMocks();

        // This should hit caches for the parent, so that children are not being remeasured.
        measureAndLayoutForTest(mParent, /* width= */ 100, /* height= */ 100);

        mExpect.that(mChild1.hasCalledOnMeasure()).isFalse();
        mExpect.that(mChild1.hasCalledOnLayout()).isFalse();
        mExpect.that(mChild2.hasCalledOnMeasure()).isFalse();
        mExpect.that(mChild2.hasCalledOnLayout()).isFalse();
        mExpect.that(mParent.hasCalledOnMeasure()).isFalse();
        mExpect.that(mParent.hasCalledOnLayout()).isFalse();
    }

    @Test
    public void traversal_differentMeasureSpecsWithoutRequestLayout_everythingRemeasured() {
        measureAndLayoutForTest(mParent, /* width= */ 100, /* height= */ 100);

        resetAllMocks();

        measureAndLayoutForTest(mParent, /* width= */ 99, /* height= */ 99);

        mExpect.that(mChild1.hasCalledOnMeasure()).isTrue();
        mExpect.that(mChild1.hasCalledOnLayout()).isTrue();
        mExpect.that(mChild2.hasCalledOnMeasure()).isTrue();
        mExpect.that(mChild2.hasCalledOnLayout()).isTrue();
        mExpect.that(mParent.hasCalledOnMeasure()).isTrue();
        mExpect.that(mParent.hasCalledOnLayout()).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_USE_MEASURE_CACHE_DURING_FORCE_LAYOUT)
    public void measure_optimizationFlagOn_measuredTwiceWhileForceLayout_childMeasuredOnce() {
        mParent.requestLayout();

        mParent.measure(makeMeasureSpec(100, EXACTLY), makeMeasureSpec(100, EXACTLY));
        assertThat(mChild1.hasCalledOnMeasure()).isTrue();
        assertThat(mParent.hasCalledOnMeasure()).isTrue();
        mChild1.reset();
        mParent.reset();
        mParent.measure(makeMeasureSpec(100, EXACTLY), makeMeasureSpec(100, EXACTLY));

        mExpect.that(mChild1.hasCalledOnMeasure()).isFalse();
        mExpect.that(mParent.hasCalledOnMeasure()).isFalse();
    }

    private void measureAndLayoutForTest(View view, int width, int height) {
        view.measure(makeMeasureSpec(width, EXACTLY), makeMeasureSpec(height, EXACTLY));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }

    private void resetAllMocks() {
        mChild1.reset();
        mChild2.reset();
        mParent.reset();
        assertThat(mChild1.hasCalledOnMeasure()).isFalse();
        assertThat(mChild1.hasCalledOnLayout()).isFalse();
        assertThat(mChild2.hasCalledOnMeasure()).isFalse();
        assertThat(mChild2.hasCalledOnLayout()).isFalse();
        assertThat(mParent.hasCalledOnMeasure()).isFalse();
        assertThat(mParent.hasCalledOnLayout()).isFalse();
    }

    private static class MockViewGroup extends ViewGroup {

        private boolean mOnMeasureCalled;
        private boolean mOnLayoutCalled;

        private MockViewGroup(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            mOnMeasureCalled = true;
            int childrenCount = getChildCount();
            for (int i = 0; i < childrenCount; i++) {
                getChildAt(i).measure(widthMeasureSpec, heightMeasureSpec);
            }
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            mOnLayoutCalled = true;
            int childrenCount = getChildCount();
            for (int i = 0; i < childrenCount; i++) {
                getChildAt(i).layout(l, t, r, b);
            }
        }

        private boolean hasCalledOnMeasure() {
            return mOnMeasureCalled;
        }

        private boolean hasCalledOnLayout() {
            return mOnLayoutCalled;
        }

        public void reset() {
            mOnMeasureCalled = false;
            mOnLayoutCalled = false;
        }
    }
}
