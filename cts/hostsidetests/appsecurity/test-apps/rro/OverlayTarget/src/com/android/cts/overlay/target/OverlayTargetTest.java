/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.cts.overlay.target;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.UserHandle;

import androidx.test.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class OverlayTargetTest {
    // overlay package
    private static final String OVERLAY_ALL_PACKAGE_NAME = "com.android.cts.overlay.all";

    // Overlay states
    private static final String STATE_DISABLED = "STATE_DISABLED";
    private static final String STATE_ENABLED = "STATE_ENABLED";

    // Default timeout value
    private static final long TIMEOUT_MS = TimeUnit.SECONDS.toMillis(5);

    // Keys for test arguments
    private static final String PARAM_START_SERVICE = "start_service";

    private Instrumentation mInstrumentation;

    @Rule
    public ActivityTestRule<OverlayTargetActivity> mActivityTestRule = new ActivityTestRule<>(
            OverlayTargetActivity.class, false /* initialTouchMode */, false /* launchActivity */);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        launchOverlayTargetActivity(InstrumentationRegistry.getArguments());
        assertThat(mActivityTestRule.getActivity()).isNotNull();
    }

    @Test
    public void overlayEnabled_activityInForeground() throws Exception {
        final OverlayTargetActivity targetActivity = mActivityTestRule.getActivity();
        final CountDownLatch latch = new CountDownLatch(1);
        targetActivity.setConfigurationChangedCallback((activity, config) -> {
            latch.countDown();
            activity.setConfigurationChangedCallback(null);
        });

        setOverlayEnabled(OVERLAY_ALL_PACKAGE_NAME, true /* enabled */);

        if (!latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Fail to wait configuration changes for the overlay target activity.");
        }
    }

    @Test
    public void overlayEnabled_activityInBackground_toForeground() throws Exception {
        final OverlayTargetActivity targetActivity = mActivityTestRule.getActivity();
        // Activity goes into background
        launchSimpleActivity();
        mInstrumentation.waitForIdleSync();
        final CountDownLatch latch = new CountDownLatch(1);
        targetActivity.setConfigurationChangedCallback((activity, config) -> {
            latch.countDown();
            activity.setConfigurationChangedCallback(null);
        });
        setOverlayEnabled(OVERLAY_ALL_PACKAGE_NAME, true /* enabled */);

        if (latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Activity in background should not receive configuration changes");
        }

        // Bring activity to foreground
        final Intent intent = new Intent(mInstrumentation.getTargetContext(),
                OverlayTargetActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        targetActivity.startActivity(intent);

        if (!latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Fail to wait configuration changes for the overlay target activity.");
        }
    }

    private void launchOverlayTargetActivity(Bundle testArgs) {
        final Intent intent = new Intent(mInstrumentation.getTargetContext(),
                OverlayTargetActivity.class);
        final boolean startService = (testArgs != null
                && "true".equalsIgnoreCase(testArgs.getString(PARAM_START_SERVICE)));
        intent.putExtra(OverlayTargetActivity.EXTRA_START_SERVICE, startService);
        mActivityTestRule.launchActivity(intent);
        mInstrumentation.waitForIdleSync();
    }

    private static void setOverlayEnabled(String overlayPackage, boolean enabled)
            throws Exception {
        final String current = getStateForOverlay(overlayPackage);
        final String expected = enabled ? STATE_ENABLED : STATE_DISABLED;
        assertThat(current).isNotEqualTo(expected);
        SystemUtil.runShellCommand("cmd overlay "
                + (enabled ? "enable" : "disable")
                + " --user current "
                + overlayPackage);
        PollingCheck.check("Fail to wait overlay enabled state " + expected
                        + " for " + overlayPackage, TIMEOUT_MS,
                () -> expected.equals(getStateForOverlay(overlayPackage)));
    }

    private void launchSimpleActivity() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClass(mInstrumentation.getTargetContext(), SimpleActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mInstrumentation.startActivitySync(intent);
    }

    private static String getStateForOverlay(String overlayPackage) {
        final String errorMsg = "Fail to parse the state of overlay package " + overlayPackage;
        final String result = SystemUtil.runShellCommand("cmd overlay dump");
        final String overlayPackageForCurrentUser = overlayPackage + ":" + UserHandle.myUserId();
        final int startIndex = result.indexOf(overlayPackageForCurrentUser);
        assertWithMessage(errorMsg).that(startIndex).isAtLeast(0);

        final int endIndex = result.indexOf('}', startIndex);
        assertWithMessage(errorMsg).that(endIndex).isGreaterThan(startIndex);

        final int stateIndex = result.indexOf("mState", startIndex);
        assertWithMessage(errorMsg).that(startIndex).isLessThan(stateIndex);
        assertWithMessage(errorMsg).that(stateIndex).isLessThan(endIndex);

        final int colonIndex = result.indexOf(':', stateIndex);
        assertWithMessage(errorMsg).that(stateIndex).isLessThan(colonIndex);
        assertWithMessage(errorMsg).that(colonIndex).isLessThan(endIndex);

        final int endLineIndex = result.indexOf('\n', colonIndex);
        assertWithMessage(errorMsg).that(colonIndex).isLessThan(endLineIndex);
        assertWithMessage(errorMsg).that(endLineIndex).isLessThan(endIndex);

        return result.substring(colonIndex + 2, endLineIndex);
    }
}
