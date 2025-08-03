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
public class BackgroundPanelTapTest extends TapTestBase {
    private static final String TAG = BackgroundPanelTapTest.class.getSimpleName();
    @Rule
    public ActivityScenarioRule<TestBackgroundActivity> mActivityRule = new ActivityScenarioRule<>(
            TestBackgroundActivity.class);
    private ActivityScenario<TestBackgroundActivity> mScenario;

    @Before
    public void setUp() {
        mScenario = mActivityRule.getScenario();
    }

    @Test
    public void clickView_background_test() {
        pressHome();
        mScenario.onActivity(activity -> mTestActivity = activity);
        mUiDevice.waitForIdle();
        Rect testRect = getTestContainerRect();
        Log.i(TAG, "testRect = " + testRect);

        Rect blocked = getControlBarRect();
        Log.i(TAG, "blocked = " + blocked);

        for (int x = testRect.left; x <= testRect.right; x += CLICK_DELTA) {
            for (int y = testRect.top; y <= testRect.bottom; y += CLICK_DELTA) {
                if (blocked.contains(x, y)) {
                    Log.i(TAG, "touch blocked skip = " + x + " " + y);
                    continue;
                }
                verifyClick(x, y, 0, 0, mTestActivity);
            }
        }
    }

    @Test
    public void clickView_background_OpenState_test() {
        mScenario.onActivity(activity -> mTestActivity = activity);
        mUiDevice.waitForIdle();
        openAppGrid();
        mUiDevice.waitForIdle();

        Rect blocked = getControlBarRect();
        Log.i(TAG, "blocked = " + blocked);

        getGripBarRect();
        Rect testRect = getTestContainerRect();
        Log.i(TAG, "testRect = " + testRect);

        for (int x = testRect.left; x <= testRect.right; x += CLICK_DELTA) {
            for (int y = testRect.top; y <= testRect.bottom; y += CLICK_DELTA) {
                if (blocked.contains(x, y)) {
                    Log.i(TAG, "touch blocked skip = " + x + " " + y);
                    continue;
                }
                verifyClick(x, y, 0, 0, mTestActivity);
            }
        }
    }
}
