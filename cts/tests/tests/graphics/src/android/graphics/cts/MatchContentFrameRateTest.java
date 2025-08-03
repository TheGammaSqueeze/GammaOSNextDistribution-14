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

package android.graphics.cts;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.Context;
import android.hardware.display.DisplayManager;

import androidx.test.filters.MediumTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.compatibility.common.util.DisplayUtil;
import com.android.compatibility.common.util.MediaUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class MatchContentFrameRateTest {

    @Rule
    public ActivityTestRule<FrameRateCtsActivity> mActivityRule =
            new ActivityTestRule<>(FrameRateCtsActivity.class);

    @Rule
    public final AdoptShellPermissionsRule mShellPermissionsRule =
            new AdoptShellPermissionsRule(getInstrumentation().getUiAutomation(),
                    Manifest.permission.OVERRIDE_DISPLAY_MODE_REQUESTS,
                    Manifest.permission.MODIFY_REFRESH_RATE_SWITCHING_TYPE,
                    Manifest.permission.HDMI_CEC);

    private int mInitialRefreshRateSwitchingType;
    private DisplayManager mDisplayManager;

    @Before
    public void setUp() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        assertTrue("Physical display is expected.", DisplayUtil.isDisplayConnected(context)
                || MediaUtils.onCuttlefish());

        FrameRateCtsActivity activity = mActivityRule.getActivity();

        // Prevent DisplayManager from limiting the allowed refresh rate range based on
        // non-app policies (e.g. low battery, user settings, etc).
        mDisplayManager = activity.getSystemService(DisplayManager.class);
        mDisplayManager.setShouldAlwaysRespectAppRequestedMode(true);

        mInitialRefreshRateSwitchingType = DisplayUtil.getRefreshRateSwitchingType(mDisplayManager);
    }

    @After
    public void tearDown() {
        if (mDisplayManager != null) {
            mDisplayManager.setRefreshRateSwitchingType(mInitialRefreshRateSwitchingType);
            mDisplayManager.setShouldAlwaysRespectAppRequestedMode(false);
        }
    }

    @Test
    public void testMatchContentFramerate_None() throws InterruptedException {
        mDisplayManager.setRefreshRateSwitchingType(DisplayManager.SWITCHING_TYPE_NONE);
        assertEquals(DisplayManager.MATCH_CONTENT_FRAMERATE_NEVER,
                mDisplayManager.getMatchContentFrameRateUserPreference());

        FrameRateCtsActivity activity = mActivityRule.getActivity();
        activity.testMatchContentFramerate_None();
    }

    @Test
    public void testMatchContentFramerate_Auto() throws InterruptedException {
        mDisplayManager.setRefreshRateSwitchingType(DisplayManager.SWITCHING_TYPE_WITHIN_GROUPS);
        assertEquals(DisplayManager.MATCH_CONTENT_FRAMERATE_SEAMLESSS_ONLY,
                mDisplayManager.getMatchContentFrameRateUserPreference());

        FrameRateCtsActivity activity = mActivityRule.getActivity();
        activity.testMatchContentFramerate_Auto();
    }

    @Test
    public void testMatchContentFramerate_Always() throws InterruptedException {
        mDisplayManager.setRefreshRateSwitchingType(
                DisplayManager.SWITCHING_TYPE_ACROSS_AND_WITHIN_GROUPS);
        assertEquals(DisplayManager.MATCH_CONTENT_FRAMERATE_ALWAYS,
                mDisplayManager.getMatchContentFrameRateUserPreference());
        FrameRateCtsActivity activity = mActivityRule.getActivity();
        activity.testMatchContentFramerate_Always();
    }
}
