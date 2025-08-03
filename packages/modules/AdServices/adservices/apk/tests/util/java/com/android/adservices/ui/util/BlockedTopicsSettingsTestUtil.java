/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.ui.util;

import static com.google.common.truth.Truth.assertWithMessage;

import android.adservices.clients.topics.AdvertisingTopicsClient;
import android.adservices.topics.GetTopicsResponse;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;

import com.android.adservices.AdServicesCommon;
import com.android.adservices.LoggerFactory;
import com.android.adservices.api.R;
import com.android.compatibility.common.util.ShellUtils;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Util class for tests for CUJ that blocks/unblocks/resets topics with dialog enabled. */
public final class BlockedTopicsSettingsTestUtil {
    private static final String TOPICS_SERVICE_NAME = "android.adservices.TOPICS_SERVICE";
    private static final int EPOCH_JOB_ID = 2;
    // The epoch length to override. It would increase the test running time if it's too long. And
    // it would make the test flaky if it's too short -- it may have passed 3 epochs so that the
    // generated topic wouldn't take effect during the test.
    //
    // Set it to 10 seconds because AVD takes longer time to operate UI. Normally 3 seconds are
    // enough for a non-ui test.
    private static final long TEST_EPOCH_JOB_PERIOD_MS = 10_000;
    private static final String LOG_TAG = "adservices";
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getTopicsLogger();
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    // Private constructor to ensure noninstantiability
    private BlockedTopicsSettingsTestUtil() {}

    /** Enter Topics Consent view when GA UX is enabled. */
    public static void enterGaTopicsConsentView(UiDevice device) throws InterruptedException {
        ApkTestUtil.scrollToAndClick(device, R.string.settingsUI_topics_ga_title);
    }

    /** Block a topic when dialog is enabled. */
    public static void blockATopicWithDialog(UiDevice device, UiObject2 blockTopicButton) {
        // Verify the button is existed and click it.
        assertWithMessage("Block Topic button").that(blockTopicButton).isNotNull();
        blockTopicButton.click();

        // Handle dialog for blocking a topic
        UiObject2 dialogTitle =
                ApkTestUtil.getElement(device, R.string.settingsUI_dialog_block_topic_message);
        assertWithMessage(
                        "Object with title: %s",
                        ApkTestUtil.getString(R.string.settingsUI_dialog_block_topic_message))
                .that(dialogTitle)
                .isNotNull();
        UiObject2 positiveText =
                ApkTestUtil.getElement(
                        device, R.string.settingsUI_dialog_block_topic_positive_text);
        assertWithMessage(
                        "Object with title: %s",
                        ApkTestUtil.getString(R.string.settingsUI_dialog_block_topic_positive_text))
                .that(positiveText)
                .isNotNull();

        // Confirm to block.
        positiveText.click();
    }

    /** Unblock a blocked topic when dialog is enabled. */
    public static void unblockATopicWithDialog(UiDevice device) {
        // Get unblock topic button.
        UiObject2 unblockTopicButton =
                ApkTestUtil.getElement(device, R.string.settingsUI_unblock_topic_title, 0);
        assertWithMessage("Unblock Topic button").that(unblockTopicButton).isNotNull();

        // Click "Unblock" and UI should display text "no blocked topics".
        unblockTopicButton.click();

        // Handle dialog for unblocking a topic.
        UiObject2 dialogTitle =
                ApkTestUtil.getElement(device, R.string.settingsUI_dialog_unblock_topic_message);
        assertWithMessage(
                        "Object with title: %s",
                        ApkTestUtil.getString(R.string.settingsUI_dialog_unblock_topic_message))
                .that(dialogTitle)
                .isNotNull();
        UiObject2 positiveText =
                ApkTestUtil.getElement(
                        device, R.string.settingsUI_dialog_unblock_topic_positive_text);
        assertWithMessage(
                        "Object with title: %s",
                        ApkTestUtil.getString(
                                R.string.settingsUI_dialog_unblock_topic_positive_text))
                .that(positiveText)
                .isNotNull();

        // Confirm to unblock.
        positiveText.click();
    }

    /** Reset blocked topics. */
    public static void resetATopicWithDialog(UiDevice device, UiObject2 resetButton) {
        // Verify the button is existed and click it.
        assertWithMessage("Reset button").that(resetButton).isNotNull();
        resetButton.click();

        // Handle dialog for resetting topics.
        UiObject2 dialogTitle =
                ApkTestUtil.getElement(device, R.string.settingsUI_dialog_reset_topic_message);
        assertWithMessage(
                        "Object with title: %s",
                        ApkTestUtil.getString(R.string.settingsUI_dialog_reset_topic_message))
                .that(dialogTitle)
                .isNotNull();
        UiObject2 positiveText =
                ApkTestUtil.getElement(
                        device, R.string.settingsUI_dialog_reset_topic_positive_text);
        assertWithMessage(
                        "Object with title: %s",
                        ApkTestUtil.getString(R.string.settingsUI_dialog_reset_topic_positive_text))
                .that(positiveText)
                .isNotNull();

        // Confirm to reset.
        positiveText.click();
    }

    /** Call Topics API and run epoch computation so there will be a topic to block. */
    public static void generateATopicToBlock() throws ExecutionException, InterruptedException {
        // Generate a client and ask it to call Topics API.
        AdvertisingTopicsClient advertisingTopicsClient1 =
                new AdvertisingTopicsClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();
        GetTopicsResponse sdk1Result = advertisingTopicsClient1.getTopics().get();
        // There should be no past data to return a topic when Topics API is called for the first
        // time.
        assertWithMessage("Post data to return a topic when Topics API called for the first time")
                .that(sdk1Result.getTopics())
                .isEmpty();

        // Force epoch computation. Add a delay to allow background executor to finish the Topics
        // API invocation.
        // TODO(b/272376728): provide a "test API" to wait for the Epoch computation
        Thread.sleep(500);
        forceEpochComputationJob();

        // Move to the next epoch because the computed result takes effect from the next epoch.
        Thread.sleep(TEST_EPOCH_JOB_PERIOD_MS);
    }

    /** Forces JobScheduler to run the Epoch Computation job. */
    private static void forceEpochComputationJob() {
        ShellUtils.runShellCommand(
                "cmd jobscheduler run -f %s %d", getAdServicesPackageName(), EPOCH_JOB_ID);
    }

    /**
     * Gets the adservices package name. Copied over from com.android.adservices .AdServicesCommon.
     */
    public static String getAdServicesPackageName() {
        Intent intent = new Intent(TOPICS_SERVICE_NAME);
        List<ResolveInfo> resolveInfos =
                sContext.getPackageManager()
                        .queryIntentServices(intent, PackageManager.MATCH_SYSTEM_ONLY);
        ServiceInfo serviceInfo =
                AdServicesCommon.resolveAdServicesService(resolveInfos, TOPICS_SERVICE_NAME);
        if (serviceInfo == null) {
            sLogger.e(
                    "%s: Failed to find serviceInfo for adServices service for intent %s",
                    LOG_TAG, intent.getAction());
            return null;
        }

        return serviceInfo.packageName;
    }
}
