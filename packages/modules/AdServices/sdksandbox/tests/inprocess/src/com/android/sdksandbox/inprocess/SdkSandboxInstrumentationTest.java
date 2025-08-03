/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.sdksandbox.inprocess;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.sdksandbox.testutils.EmptyActivity;
import android.content.Context;
import android.os.Process;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.sdksandbox.flags.Flags;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for the instrumentation running the Sdk sanbdox tests. */
@RunWith(JUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_SDK_SANDBOX_INSTRUMENTATION_INFO)
public class SdkSandboxInstrumentationTest {

    private Context mContext;
    private Context mTargetContext;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    public final ActivityTestRule mActivityRule =
            new ActivityTestRule<>(
                    EmptyActivity.class,
                    /* initialTouchMode= */ false,
                    /* launchActivity= */ false);

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mTargetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void testInstrumentationContextVsTargetContext() throws Exception {
        assumeFalse(isSdkInSandbox());
        assertWithMessage("getContext and getTargetContext should be different objects.")
                .that(mContext)
                .isNotSameInstanceAs(mTargetContext);
    }

    @Test
    public void testInstrumentationContextVsTargetContext_sdkInSandbox() throws Exception {
        assumeTrue(isSdkInSandbox());
        assertWithMessage("getContext and getTargetContext should return the same object.")
                .that(mContext)
                .isSameInstanceAs(mTargetContext);
    }

    @Test
    public void testLaunchEmptyActivity() throws Exception {
        Activity activity = null;

        try {
            activity = mActivityRule.launchActivity(/* intent */ null);
        } catch (Exception e) {
            // The activity launch should fail.
        }

        assertWithMessage("Launching activities not allowed without shell permission.")
                .that(activity)
                .isNull();
    }

    @Test
    public void testLaunchEmptyActivity_sdkInSandbox() throws Exception {
        assumeTrue(isSdkInSandbox());

        Activity activity =
                runWithShellPermissionIdentity(
                        () -> {
                            return mActivityRule.launchActivity(/* intent */ null);
                        },
                        android.Manifest.permission.START_ACTIVITIES_FROM_SDK_SANDBOX);

        assertWithMessage("Activity should be launched successfully.").that(activity).isNotNull();
    }

    private boolean isSdkInSandbox() {
        // Tests running in sdk-in-sandbox mode use an Sdk Uid in the instrumentation context.
        // TODO: find a better wy to check sdk-in-sandbox.
        return Process.isSdkSandboxUid(mContext.getApplicationInfo().uid);
    }
}
