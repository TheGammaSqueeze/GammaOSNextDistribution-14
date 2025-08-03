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

package com.android.adservices.ui.settings;

import static com.android.adservices.service.FlagsConstants.KEY_BLOCKED_TOPICS_SOURCE_OF_TRUTH;
import static com.android.adservices.service.FlagsConstants.KEY_CLASSIFIER_FORCE_USE_BUNDLED_FILES;
import static com.android.adservices.service.FlagsConstants.KEY_CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_CONSENT_SOURCE_OF_TRUTH;
import static com.android.adservices.service.FlagsConstants.KEY_DEBUG_UX;
import static com.android.adservices.service.FlagsConstants.KEY_DISABLE_TOPICS_ENROLLMENT_CHECK;
import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_AD_SERVICES_SYSTEM_API;
import static com.android.adservices.service.FlagsConstants.KEY_GA_UX_FEATURE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_TOPICS_EPOCH_JOB_PERIOD_MS;
import static com.android.adservices.service.FlagsConstants.KEY_U18_UX_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_UI_DIALOGS_FEATURE_ENABLED;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.uiautomator.UiObject2;

import com.android.adservices.api.R;
import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.ui.util.AdServicesUiTestCase;
import com.android.adservices.ui.util.ApkTestUtil;
import com.android.adservices.ui.util.BlockedTopicsSettingsTestUtil;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

/** Class to test the CUJ that blocks/unblocks/resets topics with dialog enabled. */
public final class BlockedTopicsSettingsUxSelectorUiAutomatorTest extends AdServicesUiTestCase {
    // Time out to start UI launcher.
    private static final int LAUNCHER_LAUNCH_TIMEOUT = 3000;
    // The epoch length to override. It would increase the test running time if it's too long. And
    // it would make the test flaky if it's too short -- it may have passed 3 epochs so that the
    // generated topic wouldn't take effect during the test.
    //
    // Set it to 10 seconds because AVD takes longer time to operate UI. Normally 3 seconds are
    // enough for a non-ui test.
    private static final long TEST_EPOCH_JOB_PERIOD_MS = 10000;

    @Rule(order = 11)
    public final AdServicesFlagsSetterRule flags =
            AdServicesFlagsSetterRule.forGlobalKillSwitchDisabledTests()
                    .setTopicsKillSwitch(false)
                    .setFlag(KEY_CONSENT_SOURCE_OF_TRUTH, 2)
                    .setFlag(KEY_BLOCKED_TOPICS_SOURCE_OF_TRUTH, 2)
                    .setFlag(KEY_UI_DIALOGS_FEATURE_ENABLED, true)
                    .setFlag(KEY_DISABLE_TOPICS_ENROLLMENT_CHECK, true)
                    .setFlag(KEY_CLASSIFIER_FORCE_USE_BUNDLED_FILES, true)
                    .setFlag(KEY_ENABLE_AD_SERVICES_SYSTEM_API, true)
                    .setFlag(KEY_CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE, true)
                    .setFlag(KEY_U18_UX_ENABLED, true)
                    .setFlag(KEY_GA_UX_FEATURE_ENABLED, true)
                    .setFlag(KEY_DEBUG_UX, "GA_UX")
                    .setFlag(KEY_TOPICS_EPOCH_JOB_PERIOD_MS, TEST_EPOCH_JOB_PERIOD_MS)
                    .setCompatModeFlags();

    @Ignore("b/296642754")
    @Test
    public void topicBlockUnblockTest() throws Exception {
        // Launch main view of Privacy Sandbox Settings.
        ApkTestUtil.launchSettingView(mDevice, LAUNCHER_LAUNCH_TIMEOUT);

        // Enter Topics Consent view.
        BlockedTopicsSettingsTestUtil.enterGaTopicsConsentView(mDevice);

        UiObject2 consentSwitch = ApkTestUtil.getConsentSwitch(mDevice);
        if (!consentSwitch.isChecked()) {
            consentSwitch.click();
        }

        // Navigate back to main view. This allows to refresh the topics list by re-entering Topics
        // consent view after a topic is generated . (There is no real-time listener)
        mDevice.pressBack();

        // Generate a topic to block.
        BlockedTopicsSettingsTestUtil.generateATopicToBlock();

        // Re-enter Topics Consent View.
        BlockedTopicsSettingsTestUtil.enterGaTopicsConsentView(mDevice);

        // Verify there is a topic to be blocked.
        UiObject2 blockTopicButton =
                ApkTestUtil.getElement(mDevice, R.string.settingsUI_block_topic_title, 0);
        BlockedTopicsSettingsTestUtil.blockATopicWithDialog(mDevice, blockTopicButton);

        // When there is no topic available to be blocked, it will display "no topics" text and the
        // "Block" button will not be displayed.
        assertThat(blockTopicButton).isNull();
        ApkTestUtil.scrollTo(mDevice, R.string.settingsUI_topics_view_no_topics_ga_text);
        UiObject2 noTopicsText =
                ApkTestUtil.getElement(mDevice, R.string.settingsUI_topics_view_no_topics_ga_text);
        assertThat(noTopicsText).isNotNull();

        // Click viewBlockedTopicsButton to view topics being blocked.
        ApkTestUtil.scrollToAndClick(mDevice, R.string.settingsUI_view_blocked_topics_title);

        // There is 1 topic being blocked and "Unblock" button should be visible. Unblock it.
        BlockedTopicsSettingsTestUtil.unblockATopicWithDialog(mDevice);

        // Verify there is no blocked topic.
        UiObject2 noUnblockedTopicsText =
                ApkTestUtil.getElement(mDevice, R.string.settingsUI_no_blocked_topics_ga_text);
        assertThat(noUnblockedTopicsText).isNotNull();
    }
}
