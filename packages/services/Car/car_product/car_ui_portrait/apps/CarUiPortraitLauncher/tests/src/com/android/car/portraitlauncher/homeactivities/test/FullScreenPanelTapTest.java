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

package com.android.car.portraitlauncher.homeactivities.test;

import android.graphics.Rect;
import android.util.Log;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class FullScreenPanelTapTest extends TapTestBase {
    private static final String TAG = FullScreenPanelTapTest.class.getSimpleName();
    @Rule
    public ActivityScenarioRule<TestFullScreenActivity> mActivityRule = new ActivityScenarioRule<>(
            TestFullScreenActivity.class);
    private ActivityScenario<TestFullScreenActivity> mScenario;

    @Before
    public void setUp() {
        mScenario = mActivityRule.getScenario();
    }

    @Test
    public void clickView_fullScreen_test() {
        mScenario.onActivity(activity -> mTestActivity = activity);
        mUiDevice.waitForIdle();
        Rect testRect = getTestContainerRect();
        Log.i(TAG, "testRect = " + testRect);

        for (int x = testRect.left; x <= testRect.right; x += CLICK_DELTA) {
            for (int y = testRect.top; y <= testRect.bottom; y += CLICK_DELTA) {
                verifyClick(x, y, 0, 0, mTestActivity);
            }
        }
    }
}
