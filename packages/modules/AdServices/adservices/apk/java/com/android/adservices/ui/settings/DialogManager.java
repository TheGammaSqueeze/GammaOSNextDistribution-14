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
package com.android.adservices.ui.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.os.Build;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.android.adservices.api.R;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.service.consent.App;
import com.android.adservices.service.topics.TopicsMapper;
import com.android.adservices.ui.settings.viewmodels.AppsViewModel;
import com.android.adservices.ui.settings.viewmodels.MainViewModel;
import com.android.adservices.ui.settings.viewmodels.TopicsViewModel;

import java.io.IOException;
import java.util.concurrent.Semaphore;

/** Creates and displays dialogs for the Privacy Sandbox application. */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class DialogManager {
    public static Semaphore sSemaphore = new Semaphore(1);

    /**
     * Shows the dialog for opting out of Privacy Sandbox.
     *
     * @param context Application context.
     * @param mainViewModel {@link MainViewModel}
     */
    public static void showOptOutDialog(@NonNull Context context, MainViewModel mainViewModel) {
        if (!sSemaphore.tryAcquire()) return;
        OnClickListener positiveOnClickListener =
                (dialogInterface, buttonId) -> {
                    mainViewModel.setConsent(false);
                    sSemaphore.release();
                };
        new AlertDialog.Builder(context)
                .setTitle(R.string.settingsUI_dialog_opt_out_title)
                .setMessage(R.string.settingsUI_dialog_opt_out_message)
                .setPositiveButton(
                        R.string.settingsUI_dialog_opt_out_positive_text, positiveOnClickListener)
                .setNegativeButton(
                        R.string.settingsUI_dialog_negative_text, getNegativeOnClickListener())
                .setOnDismissListener(getOnDismissListener())
                .show();
    }

    /**
     * Shows the dialog for blocking a topic.
     *
     * @param context Application context.
     * @param topicsViewModel {@link TopicsViewModel}
     * @param topic topic to block.
     */
    public static void showBlockTopicDialog(
            @NonNull Context context, TopicsViewModel topicsViewModel, Topic topic) {
        if (!sSemaphore.tryAcquire()) return;
        OnClickListener positiveOnClickListener =
                (dialogInterface, buttonId) -> {
                    topicsViewModel.revokeTopicConsent(topic);
                    sSemaphore.release();
                };
        String topicName = context.getString(TopicsMapper.getResourceIdByTopic(topic, context));
        new AlertDialog.Builder(context)
                .setTitle(
                        context.getString(R.string.settingsUI_dialog_block_topic_title, topicName))
                .setMessage(R.string.settingsUI_dialog_block_topic_message)
                .setPositiveButton(
                        R.string.settingsUI_dialog_block_topic_positive_text,
                        positiveOnClickListener)
                .setNegativeButton(
                        R.string.settingsUI_dialog_negative_text, getNegativeOnClickListener())
                .setOnDismissListener(getOnDismissListener())
                .show();
    }

    /**
     * Shows the dialog for unblocking a topic.
     *
     * @param context Application context.
     * @param topic topic to unblock.
     */
    public static void showUnblockTopicDialog(@NonNull Context context, Topic topic) {
        if (!sSemaphore.tryAcquire()) return;
        String topicName = context.getString(TopicsMapper.getResourceIdByTopic(topic, context));
        new AlertDialog.Builder(context)
                .setTitle(
                        context.getString(
                                R.string.settingsUI_dialog_unblock_topic_title, topicName))
                .setMessage(R.string.settingsUI_dialog_unblock_topic_message)
                .setPositiveButton(
                        R.string.settingsUI_dialog_unblock_topic_positive_text,
                        getNegativeOnClickListener())
                .setOnDismissListener(getOnDismissListener())
                .show();
    }

    /**
     * Shows the dialog for resetting topics. (reset does not reset blocked topics
     *
     * @param context Application context.
     * @param topicsViewModel {@link TopicsViewModel}
     */
    public static void showResetTopicDialog(
            @NonNull Context context, TopicsViewModel topicsViewModel) {
        if (!sSemaphore.tryAcquire()) return;
        OnClickListener positiveOnClickListener =
                (dialogInterface, buttonId) -> {
                    topicsViewModel.resetTopics();
                    sSemaphore.release();
                    Toast.makeText(
                                    context,
                                    R.string.settingsUI_topics_are_reset,
                                    Toast.LENGTH_SHORT)
                            .show();
                };
        new AlertDialog.Builder(context)
                .setTitle(R.string.settingsUI_dialog_reset_topic_title)
                .setMessage(R.string.settingsUI_dialog_reset_topic_message)
                .setPositiveButton(
                        R.string.settingsUI_dialog_reset_topic_positive_text,
                        positiveOnClickListener)
                .setNegativeButton(
                        R.string.settingsUI_dialog_negative_text, getNegativeOnClickListener())
                .setOnDismissListener(getOnDismissListener())
                .show();
    }

    /**
     * Shows the dialog for blocking an app from using FLEDGE.
     *
     * @param context Application context.
     * @param appsViewModel {@link AppsViewModel}
     * @param app the {@link App} to block.
     */
    public static void showBlockAppDialog(
            @NonNull Context context, AppsViewModel appsViewModel, App app) {
        if (!sSemaphore.tryAcquire()) return;
        OnClickListener positiveOnClickListener =
                (dialogInterface, buttonId) -> {
                    try {
                        appsViewModel.revokeAppConsent(app);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    sSemaphore.release();
                };
        String appName = app.getAppDisplayName(context.getPackageManager());
        new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.settingsUI_dialog_block_app_title, appName))
                .setMessage(R.string.settingsUI_dialog_block_app_message)
                .setPositiveButton(
                        R.string.settingsUI_dialog_block_app_positive_text, positiveOnClickListener)
                .setNegativeButton(
                        R.string.settingsUI_dialog_negative_text, getNegativeOnClickListener())
                .setOnDismissListener(getOnDismissListener())
                .show();
    }

    /**
     * Shows the dialog for unblocking an app from using FLEDGE.
     *
     * @param context Application context.
     * @param app the {@link App} to unblock.
     */
    public static void showUnblockAppDialog(@NonNull Context context, App app) {
        if (!sSemaphore.tryAcquire()) return;
        String appName = app.getAppDisplayName(context.getPackageManager());
        new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.settingsUI_dialog_unblock_app_title, appName))
                .setMessage(R.string.settingsUI_dialog_unblock_app_message)
                .setPositiveButton(
                        R.string.settingsUI_dialog_unblock_app_positive_text,
                        getNegativeOnClickListener())
                .setOnDismissListener(getOnDismissListener())
                .show();
    }

    /**
     * Shows the dialog for resetting FLEDGE data. (reset does not reset blocked apps)
     *
     * @param context Application context.
     * @param appsViewModel {@link AppsViewModel}
     */
    public static void showResetAppDialog(@NonNull Context context, AppsViewModel appsViewModel) {
        if (!sSemaphore.tryAcquire()) return;
        OnClickListener positiveOnClickListener =
                (dialogInterface, buttonId) -> {
                    try {
                        appsViewModel.resetApps();
                        Toast.makeText(
                                        context,
                                        R.string.settingsUI_apps_are_reset,
                                        Toast.LENGTH_SHORT)
                                .show();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    sSemaphore.release();
                };
        new AlertDialog.Builder(context)
                .setTitle(R.string.settingsUI_dialog_reset_app_title)
                .setMessage(R.string.settingsUI_dialog_reset_app_message)
                .setPositiveButton(
                        R.string.settingsUI_dialog_reset_app_positive_text, positiveOnClickListener)
                .setNegativeButton(
                        R.string.settingsUI_dialog_negative_text, getNegativeOnClickListener())
                .setOnDismissListener(getOnDismissListener())
                .show();
    }

    private static OnClickListener getNegativeOnClickListener() {
        return (dialogInterface, buttonId) -> sSemaphore.release();
    }

    private static OnDismissListener getOnDismissListener() {
        return dialogInterface -> sSemaphore.release();
    }
}
