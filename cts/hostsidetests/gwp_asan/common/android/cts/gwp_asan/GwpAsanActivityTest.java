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

package android.cts.gwp_asan;

import android.content.Context;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.DropBoxReceiver;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class GwpAsanActivityTest {
    private Context mContext;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getTargetContext();
    }

    @Rule
    public ActivityTestRule<TestActivityLauncher> mTestActivityRule =
            new ActivityTestRule<>(
                    TestActivityLauncher.class,
                    false /*initialTouchMode*/,
                    false /*launchActivity*/);

    @Test
    public void testEnablement() throws Exception {
        TestActivityLauncher activity = mTestActivityRule.launchActivity(null);
        activity.callActivityAndCheckSuccess(
                GwpAsanEnabledActivity.class, Utils.TEST_IS_GWP_ASAN_ENABLED);
        activity.callActivityAndCheckSuccess(
                GwpAsanDefaultActivity.class, Utils.TEST_IS_GWP_ASAN_ENABLED);
        activity.callActivityAndCheckSuccess(
                GwpAsanDisabledActivity.class, Utils.TEST_IS_GWP_ASAN_DISABLED);
    }

    @Test
    public void testCrashToDropboxRecoverableEnabled() throws Exception {
        TestActivityLauncher activity = mTestActivityRule.launchActivity(null);
        DropBoxReceiver receiver = Utils.getDropboxReceiver(mContext, "gwp_asan_enabled");
        activity.callActivity(GwpAsanEnabledActivity.class, Utils.TEST_USE_AFTER_FREE);
        Assert.assertTrue(receiver.await());
    }

    @Test
    public void testCrashToDropboxRecoverableDefault() throws Exception {
        TestActivityLauncher activity = mTestActivityRule.launchActivity(null);
        DropBoxReceiver receiver =
                Utils.getDropboxReceiver(
                        mContext, "gwp_asan_default", Utils.DROPBOX_RECOVERABLE_TAG);
        // Ensure the recoverable mode recovers, and returns success.
        activity.callActivityAndCheckSuccess(
                GwpAsanDefaultActivity.class, Utils.TEST_USE_AFTER_FREE);
        Assert.assertTrue(receiver.await());
    }

    @Test
    public void testCrashToDropboxEnabled() throws Exception {
        TestActivityLauncher activity = mTestActivityRule.launchActivity(null);
        DropBoxReceiver receiver = Utils.getDropboxReceiver(mContext, "gwp_asan_enabled");
        activity.callActivity(GwpAsanEnabledActivity.class, Utils.TEST_USE_AFTER_FREE);
        Assert.assertTrue(receiver.await());
    }

    @Test
    public void testCrashToDropboxDefault() throws Exception {
        TestActivityLauncher activity = mTestActivityRule.launchActivity(null);
        DropBoxReceiver receiver = Utils.getDropboxReceiver(mContext, "gwp_asan_default");
        // Inherits from the app-wide property, which was `gwpAsanMode=always`. So, this should
        // crash.
        activity.callActivity(GwpAsanDefaultActivity.class, Utils.TEST_USE_AFTER_FREE);
        Assert.assertTrue(receiver.await());
    }

    @Test
    public void checkAppExitInfo() throws Exception {
        Assert.assertTrue(Utils.appExitInfoHasReport(mContext, null));
    }
}
