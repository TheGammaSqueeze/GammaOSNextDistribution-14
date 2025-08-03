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

import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.view.Surface;

import androidx.test.filters.MediumTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.DisplayUtil;
import com.android.compatibility.common.util.MediaUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class SetFrameRateTest {
    private static String TAG = "SetFrameRateTest";

    @Rule
    public ActivityTestRule<FrameRateCtsActivity> mActivityRule =
            new ActivityTestRule<>(FrameRateCtsActivity.class);

    @Rule
    public final AdoptShellPermissionsRule mShellPermissionsRule =
            new AdoptShellPermissionsRule(getInstrumentation().getUiAutomation(),
                    Manifest.permission.HDMI_CEC,
                    Manifest.permission.OVERRIDE_DISPLAY_MODE_REQUESTS,
                    Manifest.permission.MODIFY_REFRESH_RATE_SWITCHING_TYPE);

    private DisplayManager mDisplayManager;
    private int mInitialRefreshRateSwitchingType;

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
        mDisplayManager.setRefreshRateSwitchingType(
                DisplayManager.SWITCHING_TYPE_ACROSS_AND_WITHIN_GROUPS);
    }

    @After
    public void tearDown() {
        if (mDisplayManager != null) {
            mDisplayManager.setRefreshRateSwitchingType(mInitialRefreshRateSwitchingType);
            mDisplayManager.setShouldAlwaysRespectAppRequestedMode(false);
        }
    }

    @Test
    public void testExactFrameRateMatch_Seamless() throws InterruptedException {
        FrameRateCtsActivity activity = mActivityRule.getActivity();
        activity.testExactFrameRateMatch(Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS);
    }

    @Test
    public void testExactFrameRateMatch_NonSeamless() throws InterruptedException {
        FrameRateCtsActivity activity = mActivityRule.getActivity();
        activity.testExactFrameRateMatch(Surface.CHANGE_FRAME_RATE_ALWAYS);
    }

    @Test
    public void testFixedSource_Seamless() throws InterruptedException {
        FrameRateCtsActivity activity = mActivityRule.getActivity();
        activity.testFixedSource(Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS);
    }

    @Test
    public void testFixedSource_NonSeamless() throws InterruptedException {
        FrameRateCtsActivity activity = mActivityRule.getActivity();
        activity.testFixedSource(Surface.CHANGE_FRAME_RATE_ALWAYS);
    }

    @Test
    public void testInvalidParams() throws InterruptedException {
        FrameRateCtsActivity activity = mActivityRule.getActivity();
        activity.testInvalidParams();
    }

    @Test
    @ApiTest(apis = {"android.view.Surface#clearFrameRate"})
    public void testClearFrameRate() throws InterruptedException {
        FrameRateCtsActivity activity = mActivityRule.getActivity();
        activity.testClearFrameRate();
    }
}
