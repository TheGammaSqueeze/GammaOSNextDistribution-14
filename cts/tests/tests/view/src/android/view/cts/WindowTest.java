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

import static android.view.flags.Flags.FLAG_TOOLKIT_SET_FRAME_RATE_READ_ONLY;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.Window;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class WindowTest {
    private ViewTestCtsActivity mActivity;
    private Window mWindow;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule(order = 0)
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            Manifest.permission.START_ACTIVITIES_FROM_SDK_SANDBOX);

    @Rule(order = 1)
    public ActivityTestRule<ViewTestCtsActivity> mActivityRule =
            new ActivityTestRule<>(ViewTestCtsActivity.class);

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
        mWindow = mActivity.getWindow();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_TOOLKIT_SET_FRAME_RATE_READ_ONLY)
    public void testFrameRateBoostOnTouchEnabled() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            assertTrue(mWindow.getFrameRateBoostOnTouchEnabled());
            mWindow.setFrameRateBoostOnTouchEnabled(false);
            assertFalse(mWindow.getFrameRateBoostOnTouchEnabled());
        });
    }

}
