/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.app.Activity;
import android.app.Instrumentation;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.CtsKeyEventUtil;
import com.android.compatibility.common.util.PollingCheck;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * Test {@link View} backward navigation.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class BackwardNavigationTest {
    private static final String TAG = BackwardNavigationTest.class.getSimpleName();
    private Instrumentation mInstrumentation;
    private CtsKeyEventUtil mCtsKeyEventUtil;
    private Activity mActivity;
    private View mRoot;
    private View mOrderedButton1;
    private List<View> mFocusedViews;

    @Rule
    public ActivityTestRule<BackwardNavigationCtsActivity> mActivityRule =
            new ActivityTestRule<>(BackwardNavigationCtsActivity.class);

    @Before
    public void setUp() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mCtsKeyEventUtil = new CtsKeyEventUtil(mInstrumentation.getTargetContext());
        mActivity = mActivityRule.getActivity();
        mRoot = mActivity.findViewById(R.id.root);
        View orderedButtons = mRoot.findViewById(R.id.ordered_buttons);
        mOrderedButton1 = orderedButtons.findViewById(R.id.button1);
        mFocusedViews = new ArrayList<View>();
    }

    @Test
    public void testBackwardNavigation() throws Throwable {
        // Focus on the first button.
        mActivityRule.runOnUiThread(() -> {
            mOrderedButton1.requestFocus();

            assertEquals("mOrderedButton1 should be focused", mActivity.getCurrentFocus(),
                    mOrderedButton1);
        });

        // Wait for the window to gain focus. Otherwise, it can't receive the injected key events.
        PollingCheck.waitFor(() ->mOrderedButton1.hasWindowFocus(),
                "The window containing BackwardNavigationCtsActivity should be focused");

        // Press TAB to go through all the focusable Views.
        View focusedView = mOrderedButton1;
        do {
            mFocusedViews.add(focusedView);
            Log.v(TAG, "View " + focusedView + " is focused in order");
            mCtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mRoot, KeyEvent.KEYCODE_TAB);
            final View lastFocuseView = focusedView;

            PollingCheck.waitFor(() -> mActivity.getCurrentFocus() != lastFocuseView,
                    "The focus should change after pressing TAB");

            focusedView = mActivity.getCurrentFocus();
        } while (focusedView != mOrderedButton1);

        // Press SHIFT + TAB to go through them, and verify that they're focused in reversed order.
        for (int i = mFocusedViews.size() - 1; i >= 0; i--) {
            mCtsKeyEventUtil.sendKeyWhileHoldingModifier(mInstrumentation, mRoot,
                    KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_SHIFT_LEFT);
            final int index = i;

            PollingCheck.waitFor(() -> mFocusedViews.get(index).hasFocus(),
                    "Expected focused view " + mFocusedViews.get(index)
                            + ", actual focused view " + mActivity.getCurrentFocus());
        }
    }
}
