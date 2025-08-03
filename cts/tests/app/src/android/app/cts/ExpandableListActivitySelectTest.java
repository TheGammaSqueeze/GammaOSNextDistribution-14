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

package android.app.cts;

import static android.app.stubs.ExpandableListTestActivity.NUMBER_OF_CHILDREN;
import static android.app.stubs.ExpandableListTestActivity.NUMBER_OF_GROUPS;

import static com.google.common.truth.Truth.assertThat;

import android.app.Instrumentation;
import android.app.stubs.ExpandableListTestActivity;
import android.widget.ExpandableListView;

import androidx.test.annotation.UiThreadTest;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.WindowUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class ExpandableListActivitySelectTest {

    private static final long TOUCH_MODE_PROPAGATION_TIMEOUT_MILLIS = 5_000;

    @Rule
    public ActivityScenarioRule<ExpandableListTestActivity> mActivityRule =
            new ActivityScenarioRule<>(ExpandableListTestActivity.class);

    private Instrumentation mInstrumentation;
    private ExpandableListTestActivity mActivity;

    @Before
    public void setUp() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivityRule.getScenario().onActivity(a -> mActivity = a);
        WindowUtil.waitForFocus(mActivity);
        // Make sure the touch mode is disabled since selection doesn't work in touch mode.
        mInstrumentation.setInTouchMode(false);
        PollingCheck.waitFor(TOUCH_MODE_PROPAGATION_TIMEOUT_MILLIS,
                () -> !mActivity.getWindow().getDecorView().isInTouchMode());
    }

    @After
    public void tearDown() {
        mInstrumentation.setInTouchMode(true);
    }

    @Test
    @UiThreadTest
    public void testSelect() {
        ExpandableListView listView = mActivity.getExpandableListView();
        for (int i = 0; i < NUMBER_OF_GROUPS; i++) {
            listView.expandGroup(i);
            mActivity.setSelectedGroup(i);
            for (int k = 0; k < NUMBER_OF_CHILDREN; k++) {
                mActivity.setSelectedChild(i, k, false);
                assertThat(ExpandableListView.getPackedPositionForChild(i, k)).isEqualTo(
                        mActivity.getSelectedPosition());
            }
            for (int k = 0; k < NUMBER_OF_CHILDREN; k++) {
                mActivity.setSelectedChild(i, k, true);
                assertThat(ExpandableListView.getPackedPositionForChild(i, k)).isEqualTo(
                        mActivity.getSelectedPosition());
            }
            listView.collapseGroup(i);
        }
    }
}

