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

package com.android.adservices.ui.notifications;

import static com.android.adservices.service.FlagsConstants.KEY_GA_UX_FEATURE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_PAS_UX_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_RECORD_MANUAL_INTERACTION_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_UI_OTA_STRINGS_FEATURE_ENABLED;
import static com.android.adservices.service.consent.ConsentManager.MANUAL_INTERACTIONS_RECORDED;
import static com.android.adservices.service.consent.ConsentManager.NO_MANUAL_INTERACTIONS_RECORDED;
import static com.android.adservices.ui.UxUtil.isUxStatesReady;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.android.adservices.api.R;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.stats.UiStatsLogger;
import com.android.adservices.service.ui.data.UxStatesManager;
import com.android.adservices.ui.OTAResourcesManager;
import com.android.adservices.ui.UxUtil;

/** Provides methods which can be used to display Privacy Sandbox consent notification. */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class ConsentNotificationTrigger {
    /* Random integer for NotificationCompat purposes. */
    public static final int NOTIFICATION_ID = 67920;
    private static final String CHANNEL_ID = "PRIVACY_SANDBOX_CHANNEL";
    private static final int NOTIFICATION_PRIORITY = NotificationCompat.PRIORITY_MAX;
    /**
     * Shows consent notification as the highest priority notification to the user.
     *
     * @param context Context which is used to display {@link NotificationCompat}
     */
    public static void showConsentNotification(@NonNull Context context, boolean isEuDevice) {
        UiStatsLogger.logRequestedNotification();

        boolean gaUxFeatureEnabled =
                UxStatesManager.getInstance().getFlag(KEY_GA_UX_FEATURE_ENABLED);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        ConsentManager consentManager = ConsentManager.getInstance();
        if (!notificationManager.areNotificationsEnabled()) {
            recordNotificationDisplayed(context, gaUxFeatureEnabled, consentManager);
            UiStatsLogger.logNotificationDisabled();
            return;
        }

        // Set OTA resources if it exists.
        if (UxStatesManager.getInstance().getFlag(KEY_UI_OTA_STRINGS_FEATURE_ENABLED)) {
            OTAResourcesManager.applyOTAResources(context.getApplicationContext(), true);
        }

        createNotificationChannel(context);
        Notification notification =
                getNotification(context, isEuDevice, gaUxFeatureEnabled, consentManager);

        notificationManager.notify(NOTIFICATION_ID, notification);

        setupConsents(context, isEuDevice, gaUxFeatureEnabled, consentManager);

        UiStatsLogger.logNotificationDisplayed();
        recordNotificationDisplayed(context, gaUxFeatureEnabled, consentManager);
    }

    private static void recordNotificationDisplayed(
            @NonNull Context context, boolean gaUxFeatureEnabled, ConsentManager consentManager) {
        if (UxStatesManager.getInstance().getFlag(KEY_RECORD_MANUAL_INTERACTION_ENABLED)
                && consentManager.getUserManualInteractionWithConsent()
                        != MANUAL_INTERACTIONS_RECORDED) {
            consentManager.recordUserManualInteractionWithConsent(NO_MANUAL_INTERACTIONS_RECORDED);
        }

        if (isUxStatesReady(context)) {
            switch (UxUtil.getUx(context)) {
                case GA_UX:
                    if (UxStatesManager.getInstance().getFlag(KEY_PAS_UX_ENABLED)) {
                        consentManager.recordPasNotificationDisplayed(true);
                        break;
                    }
                    consentManager.recordGaUxNotificationDisplayed(true);
                    break;
                // Both U18_UX and RVC_UX are showing U18 Notification
                case U18_UX:
                case RVC_UX:
                    consentManager.setU18NotificationDisplayed(true);
                    break;
                case BETA_UX:
                    consentManager.recordNotificationDisplayed(true);
                    break;
                default:
                    break;
            }
        } else {
            if (gaUxFeatureEnabled) {
                consentManager.recordGaUxNotificationDisplayed(true);
            }
            consentManager.recordNotificationDisplayed(true);
        }
    }

    @NonNull
    private static Notification getNotification(
            @NonNull Context context,
            boolean isEuDevice,
            boolean gaUxFeatureEnabled,
            ConsentManager consentManager) {
        Notification notification;
        if (isUxStatesReady(context)) {
            switch (UxUtil.getUx(context)) {
                case GA_UX:
                    if (UxStatesManager.getInstance().getFlag(KEY_PAS_UX_ENABLED)) {
                        notification = getPasConsentNotification(context, consentManager);
                        break;
                    }
                    notification = getGaV2ConsentNotification(context, isEuDevice);
                    break;
                // Both U18_UX and RVC_UX are showing U18 Notification
                case U18_UX:
                case RVC_UX:
                    notification = getU18ConsentNotification(context);
                    break;
                case BETA_UX:
                    notification = getConsentNotification(context, isEuDevice);
                    break;
                default:
                    notification = getGaV2ConsentNotification(context, isEuDevice);
            }
        } else {
            if (gaUxFeatureEnabled) {
                notification = getGaV2ConsentNotification(context, isEuDevice);
            } else {
                notification = getConsentNotification(context, isEuDevice);
            }
        }

        // make notification sticky (non-dismissible) for EuDevices when the GA UX feature is on
        if (gaUxFeatureEnabled && isEuDevice) {
            notification.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;
        }
        return notification;
    }

    // setup default consents based on information whether the device is EU or non-EU device and
    // GA UX feature flag is enabled.
    private static void setupConsents(
            @NonNull Context context,
            boolean isEuDevice,
            boolean gaUxFeatureEnabled,
            ConsentManager consentManager) {
        if (isUxStatesReady(context)) {
            switch (UxUtil.getUx(context)) {
                case GA_UX:
                    if (UxStatesManager.getInstance().getFlag(KEY_PAS_UX_ENABLED)
                            && (isFledgeOrMsmtEnabled(consentManager)
                                    || consentManager.getUserManualInteractionWithConsent()
                                            == MANUAL_INTERACTIONS_RECORDED)) {
                        // Not first time user, respect previous consents
                        break;
                    }
                    setUpGaConsent(context, isEuDevice, consentManager);
                    break;
                case U18_UX:
                    consentManager.recordMeasurementDefaultConsent(true);
                    consentManager.enable(context, AdServicesApiType.MEASUREMENTS);
                    break;
                case RVC_UX:
                    if (isEuDevice) {
                        consentManager.recordMeasurementDefaultConsent(false);
                        consentManager.disable(context, AdServicesApiType.MEASUREMENTS);
                    } else {
                        consentManager.recordMeasurementDefaultConsent(true);
                        consentManager.enable(context, AdServicesApiType.MEASUREMENTS);
                    }
                    break;
                case BETA_UX:
                    if (!isEuDevice) {
                        consentManager.enable(context);
                    } else {
                        consentManager.disable(context);
                    }
                    break;
                default:
                    // Default behavior is GA UX.
                    setUpGaConsent(context, isEuDevice, consentManager);
                    break;
            }
        } else {
            // Keep the feature flag at the upper level to make it easier to cleanup the code once
            // the beta functionality is fully deprecated and abandoned.
            if (gaUxFeatureEnabled) {
                setUpGaConsent(context, isEuDevice, consentManager);
            } else {
                // For the ROW devices, set the consent to GIVEN (enabled).
                // For the EU devices, set the consent to REVOKED (disabled)
                if (!isEuDevice) {
                    consentManager.enable(context);
                } else {
                    consentManager.disable(context);
                }
            }
        }
    }

    private static Notification getGaV2ConsentNotification(
            @NonNull Context context, boolean isEuDevice) {
        Intent intent = new Intent(context, ConsentNotificationActivity.class);

        PendingIntent pendingIntent =
                PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_IMMUTABLE);

        String bigText =
                isEuDevice
                        ? context.getString(R.string.notificationUI_notification_ga_content_eu_v2)
                        : context.getString(R.string.notificationUI_notification_ga_content_v2);

        NotificationCompat.BigTextStyle textStyle =
                new NotificationCompat.BigTextStyle().bigText(bigText);

        String contentTitle =
                context.getString(
                        isEuDevice
                                ? R.string.notificationUI_notification_ga_title_eu_v2
                                : R.string.notificationUI_notification_ga_title_v2);
        String contentText =
                context.getString(
                        isEuDevice
                                ? R.string.notificationUI_notification_ga_content_eu_v2
                                : R.string.notificationUI_notification_ga_content_v2);

        NotificationCompat.Builder notification =
                new NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_info_icon)
                        .setContentTitle(contentTitle)
                        .setContentText(contentText)
                        .setStyle(textStyle)
                        .setPriority(NOTIFICATION_PRIORITY)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent);
        return notification.build();
    }

    /**
     * Returns a {@link NotificationCompat.Builder} which can be used to display consent
     * notification to the user.
     *
     * @param context {@link Context} which is used to prepare a {@link NotificationCompat}.
     */
    private static Notification getConsentNotification(
            @NonNull Context context, boolean isEuDevice) {
        Intent intent = new Intent(context, ConsentNotificationActivity.class);

        PendingIntent pendingIntent =
                PendingIntent.getActivity(
                        context, 1, intent, PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.BigTextStyle textStyle =
                new NotificationCompat.BigTextStyle()
                        .bigText(
                                isEuDevice
                                        ? context.getString(
                                                R.string.notificationUI_notification_content_eu)
                                        : context.getString(
                                                R.string.notificationUI_notification_content));
        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_info_icon)
                .setContentTitle(
                        context.getString(
                                isEuDevice
                                        ? R.string.notificationUI_notification_title_eu
                                        : R.string.notificationUI_notification_title))
                .setContentText(
                        context.getString(
                                isEuDevice
                                        ? R.string.notificationUI_notification_content_eu
                                        : R.string.notificationUI_notification_content))
                .setStyle(textStyle)
                .setPriority(NOTIFICATION_PRIORITY)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build();
    }

    /**
     * Returns a {@link NotificationCompat.Builder} which can be used to display consent
     * notification to U18 users.
     *
     * @param context {@link Context} which is used to prepare a {@link NotificationCompat}.
     */
    private static Notification getU18ConsentNotification(@NonNull Context context) {
        Intent intent = new Intent(context, ConsentNotificationActivity.class);

        PendingIntent pendingIntent =
                PendingIntent.getActivity(
                        context, /* requestCode= */ 1, intent, PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.BigTextStyle textStyle =
                new NotificationCompat.BigTextStyle()
                        .bigText(
                                context.getString(
                                        R.string.notificationUI_u18_notification_content));
        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_info_icon)
                .setContentTitle(context.getString(R.string.notificationUI_u18_notification_title))
                .setContentText(context.getString(R.string.notificationUI_u18_notification_content))
                .setStyle(textStyle)
                .setPriority(NOTIFICATION_PRIORITY)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build();
    }

    private static Notification getPasConsentNotification(
            @NonNull Context context, ConsentManager consentManager) {
        boolean isRenotify = isFledgeOrMsmtEnabled(consentManager);
        Intent intent = new Intent(context, ConsentNotificationActivity.class);

        PendingIntent pendingIntent =
                PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_IMMUTABLE);

        String bigText =
                isRenotify
                        ? context.getString(R.string.notificationUI_pas_re_notification_content)
                        : context.getString(R.string.notificationUI_pas_notification_content);

        NotificationCompat.BigTextStyle textStyle =
                new NotificationCompat.BigTextStyle().bigText(bigText);

        String contentTitle =
                context.getString(
                        isRenotify
                                ? R.string.notificationUI_pas_re_notification_title
                                : R.string.notificationUI_pas_notification_title);
        String contentText =
                context.getString(
                        isRenotify
                                ? R.string.notificationUI_pas_re_notification_content
                                : R.string.notificationUI_pas_notification_content);

        NotificationCompat.Builder notification =
                new NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_info_icon)
                        .setContentTitle(contentTitle)
                        .setContentText(contentText)
                        .setStyle(textStyle)
                        .setPriority(NOTIFICATION_PRIORITY)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent);
        return notification.build();
    }

    private static boolean isFledgeOrMsmtEnabled(ConsentManager consentManager) {
        return consentManager.getConsent(AdServicesApiType.FLEDGE).isGiven()
                || consentManager.getConsent(AdServicesApiType.MEASUREMENTS).isGiven();
    }

    private static void createNotificationChannel(@NonNull Context context) {
        // TODO (b/230372892): styling -> adjust channels to use Android System labels.
        int importance = NotificationManager.IMPORTANCE_HIGH;
        NotificationChannel channel =
                new NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.settingsUI_main_view_title),
                        importance);
        channel.setDescription(context.getString(R.string.settingsUI_main_view_title));
        NotificationManager notificationManager =
                context.getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }

    private static void setUpGaConsent(
            @NonNull Context context, boolean isEuDevice, ConsentManager consentManager) {
        if (isEuDevice) {
            consentManager.recordTopicsDefaultConsent(false);
            consentManager.recordFledgeDefaultConsent(false);
            consentManager.recordMeasurementDefaultConsent(false);

            consentManager.disable(context, AdServicesApiType.TOPICS);
            consentManager.disable(context, AdServicesApiType.FLEDGE);
            consentManager.disable(context, AdServicesApiType.MEASUREMENTS);
        } else {
            consentManager.recordTopicsDefaultConsent(true);
            consentManager.recordFledgeDefaultConsent(true);
            consentManager.recordMeasurementDefaultConsent(true);

            consentManager.enable(context, AdServicesApiType.TOPICS);
            consentManager.enable(context, AdServicesApiType.FLEDGE);
            consentManager.enable(context, AdServicesApiType.MEASUREMENTS);
        }
    }
}
