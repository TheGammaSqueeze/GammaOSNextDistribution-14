/*
 * Copyright (C) 2015 The Android Open Source Project
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
package android.assist.cts;

import static android.assist.common.Utils.SHOW_SESSION_FLAGS_TO_SET;
import static android.service.voice.VoiceInteractionSession.KEY_FOREGROUND_ACTIVITIES;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeFalse;

import android.assist.common.AutoResetLatch;
import android.assist.common.Utils;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class ExtraAssistDataTest extends AssistTestBase {
    private static final String TAG = "ExtraAssistDataTest";
    private static final String TEST_CASE_TYPE = Utils.EXTRA_ASSIST;

    @Override
    protected void customSetup() throws Exception {
        startTestActivity(TEST_CASE_TYPE);
    }

    @Test
    public void testAssistContentAndAssistData() throws Exception {
        assumeIsNotLowRamDevice();
        startTest(TEST_CASE_TYPE);
        waitForAssistantToBeReady();
        start3pApp(TEST_CASE_TYPE);
        final AutoResetLatch latch = startSession();
        waitForContext(latch);
        verifyAssistDataNullness(false, false, false, false);

        Log.i(TAG, "assist bundle is: " + Utils.toBundleString(mAssistBundle));

        // first tests that the assist content's structured data is the expected
        assertWithMessage(
                "AssistContent structured data did not match data in onProvideAssistContent").that(
                        mAssistContent.getStructuredData()).isEqualTo(Utils.getStructuredJSON());
        Bundle extraExpectedBundle = Utils.getExtraAssistBundle();
        Bundle extraAssistBundle = mAssistBundle.getBundle(Intent.EXTRA_ASSIST_CONTEXT);
        for (String key : extraExpectedBundle.keySet()) {
            assertWithMessage("Assist bundle does not contain expected extra context key: %s", key)
                    .that(extraAssistBundle.containsKey(key)).isTrue();
            assertWithMessage("Extra assist context bundle values do not match for key: %s", key)
                    .that(extraAssistBundle.get(key)).isEqualTo(extraExpectedBundle.get(key));
        }

        // then test the EXTRA_ASSIST_UID
        int expectedUid = Utils.getExpectedUid(extraAssistBundle);
        int actualUid = mAssistBundle.getInt(Intent.EXTRA_ASSIST_UID);
        assertWithMessage("Wrong value for EXTRA_ASSIST_UID").that(actualUid)
                .isEqualTo(expectedUid);

        // Verify KEY_FOREGROUND_ACTIVITIES was correctly provided in onShow args
        assertThat(mOnShowArgs.containsKey(KEY_FOREGROUND_ACTIVITIES)).isTrue();
        ArrayList<ComponentName> foregroundApps =
                mOnShowArgs.getParcelableArrayList(KEY_FOREGROUND_ACTIVITIES);
        Log.i(TAG, "ForegroundActivityComponent:  " + foregroundApps);
        assertWithMessage("Foregrounded apps").that(foregroundApps).isNotEmpty();
        List<String> foregroundAppPackageNames = foregroundApps.stream()
                .map(ComponentName::getPackageName).toList();
        assertWithMessage("Foregrounded test assistant app")
                .that(foregroundAppPackageNames).contains("android.assist.testapp");
    }

    @Test
    public void testAssistContentAndDataNullWhenNoFlagsToShowSession() throws Exception {
        assumeIsNotLowRamDevice();
        // TODO(b/299988169): Fix multi/secure displays for automotive
        // Currently automotive uses multi-display and/or secure displays
        // and sending null data is not supported due to the lack of information in main voice
        // interaction service.
        assumeIsNotAutomotive();
        startTest(TEST_CASE_TYPE);
        waitForAssistantToBeReady();
        start3pApp(TEST_CASE_TYPE);

        Bundle bundle = new Bundle();
        bundle.putInt(SHOW_SESSION_FLAGS_TO_SET, 0);
        final AutoResetLatch latch = startSession(bundle);
        waitForContext(latch);

        verifyActivityIdNullness(/* isActivityIdNull = */ false);
        verifyAssistDataNullness(true, true, true, true);
        assertThat(mOnShowArgs.containsKey(KEY_FOREGROUND_ACTIVITIES)).isFalse();
    }

    private void assumeIsNotAutomotive() {
        assumeFalse("Test not supported in automotive", Utils.isAutomotive(mContext));
    }

    private void assumeIsNotLowRamDevice() {
        assumeFalse("Test not supported for low-RAM devices", mActivityManager.isLowRamDevice());
    }
}
