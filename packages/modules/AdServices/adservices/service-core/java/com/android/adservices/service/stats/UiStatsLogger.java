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

package com.android.adservices.service.stats;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__ACTION_UNSPECIFIED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__BLOCK_APP_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__BLOCK_TOPIC_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__CONFIRMATION_PAGE_DISMISSED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__CONFIRMATION_PAGE_DISPLAYED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__FLEDGE_OPT_IN_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__FLEDGE_OPT_OUT_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_CONFIRMATION_PAGE_DISMISSED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_LANDING_PAGE_ADDITIONAL_INFO_2_CLICKED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_LANDING_PAGE_ADDITIONAL_INFO_CLICKED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_LANDING_PAGE_DISMISSED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_LANDING_PAGE_GOT_IT_BUTTON_CLICKED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_LANDING_PAGE_MORE_BUTTON_CLICKED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_LANDING_PAGE_OPT_IN;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_LANDING_PAGE_OPT_OUT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_LANDING_PAGE_SCROLLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_LANDING_PAGE_SCROLLED_TO_BOTTOM;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_LANDING_PAGE_SETTINGS_BUTTON_CLICKED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_NOTIFICATION_CONFIRMATION_PAGE_DISPLAYED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_NOTIFICATION_DISABLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_NOTIFICATION_DISPLAYED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_NOTIFICATION_LANDING_PAGE_DISPLAYED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_NOTIFICATION_REQUESTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_OPT_IN_CONFIRMATION_PAGE_GOT_IT_CLICKED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_OPT_IN_CONFIRMATION_PAGE_MORE_INFO_CLICKED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_OPT_IN_CONFIRMATION_PAGE_SETTINGS_CLICKED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_OPT_OUT_CONFIRMATION_PAGE_GOT_IT_CLICKED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_OPT_OUT_CONFIRMATION_PAGE_MORE_INFO_CLICKED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_OPT_OUT_CONFIRMATION_PAGE_SETTINGS_CLICKED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__LANDING_PAGE_ADDITIONAL_INFO_CLICKED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__LANDING_PAGE_DISMISSED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__LANDING_PAGE_DISPLAYED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__LANDING_PAGE_GOT_IT_BUTTON_CLICKED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__LANDING_PAGE_MORE_BUTTON_CLICKED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__LANDING_PAGE_OPT_IN;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__LANDING_PAGE_OPT_OUT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__LANDING_PAGE_SCROLLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__LANDING_PAGE_SCROLLED_TO_BOTTOM;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__LANDING_PAGE_SETTINGS_BUTTON_CLICKED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__MANAGE_APPS_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__MANAGE_MEASUREMENT_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__MANAGE_TOPICS_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__MEASUREMENT_OPT_IN_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__MEASUREMENT_OPT_OUT_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__NOTIFICATION_DISABLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__NOTIFICATION_DISPLAYED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__NOTIFICATION_OPT_IN_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__NOTIFICATION_OPT_OUT_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__OPT_IN_CONFIRMATION_PAGE_GOT_IT_CLICKED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__OPT_IN_CONFIRMATION_PAGE_SETTINGS_CLICKED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__OPT_IN_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__OPT_OUT_CONFIRMATION_PAGE_GOT_IT_CLICKED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__OPT_OUT_CONFIRMATION_PAGE_SETTINGS_CLICKED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__OPT_OUT_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__PRIVACY_SANDBOX_ENTRY_POINT_CLICKED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__PRIVACY_SANDBOX_SETTINGS_PAGE_DISPLAYED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__REQUESTED_NOTIFICATION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__RESET_APP_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__RESET_MEASUREMENT_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__RESET_TOPIC_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__TOPICS_OPT_IN_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__TOPICS_OPT_OUT_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__UNBLOCK_APP_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__UNBLOCK_TOPIC_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_AD_ID_STATE__AD_ID_DISABLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_AD_ID_STATE__AD_ID_ENABLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_AD_ID_STATE__STATE_UNSPECIFIED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_CONSENT__CONSENT_UNSPECIFIED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_CONSENT__FLEDGE_DEFAULT_OPT_IN;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_CONSENT__FLEDGE_DEFAULT_OPT_OUT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_CONSENT__MEASUREMENT_DEFAULT_OPT_IN;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_CONSENT__MEASUREMENT_DEFAULT_OPT_OUT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_CONSENT__PP_API_DEFAULT_OPT_IN;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_CONSENT__PP_API_DEFAULT_OPT_OUT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_CONSENT__TOPICS_DEFAULT_OPT_IN;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_CONSENT__TOPICS_DEFAULT_OPT_OUT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ENROLLMENT_CHANNEL__ALREADY_ENROLLED_CHANNEL;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ENROLLMENT_CHANNEL__CONSENT_NOTIFICATION_DEBUG_CHANNEL;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ENROLLMENT_CHANNEL__FIRST_CONSENT_NOTIFICATION_CHANNEL;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ENROLLMENT_CHANNEL__PAS_FIRST_NOTIFICATION_CHANNEL;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ENROLLMENT_CHANNEL__PAS_RENOTIFY_NOTIFICATION_CHANNEL;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ENROLLMENT_CHANNEL__RVC_POST_OTA_NOTIFICATION_CHANNEL;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ENROLLMENT_CHANNEL__RECONSENT_NOTIFICATION_CHANNEL;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ENROLLMENT_CHANNEL__UNSPECIFIED_CHANNEL;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ENROLLMENT_CHANNEL__UNSUPPORTED_CHANNEL;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__EU;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__ROW;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__UX__BETA_UX;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__UX__GA_UX_WITH_PAS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__UX__RVC_UX;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__UX__GA_UX;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__UX__UNSPECIFIED_UX;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__UX__UNSUPPORTED_UX;
import static com.android.adservices.service.ui.constants.DebugMessages.PRIVACY_SANDBOX_UI_REQUEST_MESSAGE;
import static com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection.RVC_UX;

import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.LogUtil;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.consent.DeviceRegionProvider;
import com.android.adservices.service.ui.data.UxStatesManager;
import com.android.adservices.service.ui.enrollment.collection.BetaUxEnrollmentChannelCollection;
import com.android.adservices.service.ui.enrollment.collection.GaUxEnrollmentChannelCollection;
import com.android.adservices.service.ui.enrollment.collection.PrivacySandboxEnrollmentChannelCollection;
import com.android.adservices.service.ui.enrollment.collection.RvcUxEnrollmentChannelCollection;
import com.android.adservices.service.ui.enrollment.collection.U18UxEnrollmentChannelCollection;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.internal.annotations.VisibleForTesting;

/** Logger for UiStats. */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public final class UiStatsLogger {
    private static AdServicesLoggerImpl sLogger = AdServicesLoggerImpl.getInstance();

    /** Logs that a notification was displayed. */
    public static void logNotificationDisplayed() {
        UIStats uiStats = getBaseUiStats();

        uiStats.setAction(
                FlagsFactory.getFlags().getGaUxFeatureEnabled()
                        ? AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_NOTIFICATION_DISPLAYED
                        : AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__NOTIFICATION_DISPLAYED);

        getAdServicesLogger().logUIStats(uiStats);
    }

    /** Logs that the more button on the landing page was displayed. */
    public static void logLandingPageMoreButtonClicked() {
        UIStats uiStats = getBaseUiStats();

        uiStats.setAction(
                FlagsFactory.getFlags().getGaUxFeatureEnabled()
                        ? AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_LANDING_PAGE_MORE_BUTTON_CLICKED
                        : AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__LANDING_PAGE_MORE_BUTTON_CLICKED);

        getAdServicesLogger().logUIStats(uiStats);
    }

    /** Logs that the additional info dropdown on the landing page was displayed. */
    public static void logLandingPageAdditionalInfoClicked() {
        UIStats uiStats = getBaseUiStats();

        uiStats.setAction(
                FlagsFactory.getFlags().getGaUxFeatureEnabled()
                        ? AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_LANDING_PAGE_ADDITIONAL_INFO_CLICKED
                        : AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__LANDING_PAGE_ADDITIONAL_INFO_CLICKED);

        getAdServicesLogger().logUIStats(uiStats);
    }

    /** Logs that the user scrolled the landing page. */
    public static void logLandingPageScrolled() {
        UIStats uiStats = getBaseUiStats();

        uiStats.setAction(
                FlagsFactory.getFlags().getGaUxFeatureEnabled()
                        ? AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_LANDING_PAGE_SCROLLED
                        : AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__LANDING_PAGE_SCROLLED);

        getAdServicesLogger().logUIStats(uiStats);
    }

    /** Logs that the user scrolled to the bottom of the landing page. */
    public static void logLandingPageScrolledToBottom() {
        UIStats uiStats = getBaseUiStats();

        uiStats.setAction(
                FlagsFactory.getFlags().getGaUxFeatureEnabled()
                        ? AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_LANDING_PAGE_SCROLLED_TO_BOTTOM
                        : AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__LANDING_PAGE_SCROLLED_TO_BOTTOM);

        getAdServicesLogger().logUIStats(uiStats);
    }

    /** Logs that the user clicked the setting button on the landing page. */
    public static void logLandingPageSettingsButtonClicked() {
        UIStats uiStats = getBaseUiStats();

        uiStats.setAction(
                FlagsFactory.getFlags().getGaUxFeatureEnabled()
                        ? AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_LANDING_PAGE_SETTINGS_BUTTON_CLICKED
                        : AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__LANDING_PAGE_SETTINGS_BUTTON_CLICKED);

        getAdServicesLogger().logUIStats(uiStats);
    }

    /** Logs that the user dismissed the landing page. */
    public static void logLandingPageDismissed() {
        UIStats uiStats = getBaseUiStats();

        uiStats.setAction(
                FlagsFactory.getFlags().getGaUxFeatureEnabled()
                        ? AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_LANDING_PAGE_DISMISSED
                        : AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__LANDING_PAGE_DISMISSED);

        getAdServicesLogger().logUIStats(uiStats);
    }

    /** Logs that the user clicked the got it button on the landing page. */
    public static void logLandingPageGotItButtonClicked() {
        UIStats uiStats = getBaseUiStats();

        uiStats.setAction(
                FlagsFactory.getFlags().getGaUxFeatureEnabled()
                        ? AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_LANDING_PAGE_GOT_IT_BUTTON_CLICKED
                        : AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__LANDING_PAGE_GOT_IT_BUTTON_CLICKED);

        getAdServicesLogger().logUIStats(uiStats);
    }

    /** Logs that the user opt-in from the landing page. */
    public static void logLandingPageOptIn() {
        UIStats uiStats = getBaseUiStats();

        uiStats.setAction(
                FlagsFactory.getFlags().getGaUxFeatureEnabled()
                        ? AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_LANDING_PAGE_OPT_IN
                        : AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__LANDING_PAGE_OPT_IN);

        getAdServicesLogger().logUIStats(uiStats);
    }

    /** Logs that the user opt-out from the landing page. */
    public static void logLandingPageOptOut() {
        UIStats uiStats = getBaseUiStats();

        uiStats.setAction(
                FlagsFactory.getFlags().getGaUxFeatureEnabled()
                        ? AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_LANDING_PAGE_OPT_OUT
                        : AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__LANDING_PAGE_OPT_OUT);

        getAdServicesLogger().logUIStats(uiStats);
    }

    /** Logs that the user clicked settings on the opt-in confirmation page. */
    public static void logOptInConfirmationPageSettingsClicked() {
        UIStats uiStats = getBaseUiStats();

        uiStats.setAction(
                FlagsFactory.getFlags().getGaUxFeatureEnabled()
                        ? AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_OPT_IN_CONFIRMATION_PAGE_SETTINGS_CLICKED
                        : AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__OPT_IN_CONFIRMATION_PAGE_SETTINGS_CLICKED);

        getAdServicesLogger().logUIStats(uiStats);
    }

    /** Logs that the user clicked settings on the opt-out confirmation page. */
    public static void logOptOutConfirmationPageSettingsClicked() {
        UIStats uiStats = getBaseUiStats();

        uiStats.setAction(
                FlagsFactory.getFlags().getGaUxFeatureEnabled()
                        ? AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_OPT_OUT_CONFIRMATION_PAGE_SETTINGS_CLICKED
                        : AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__OPT_OUT_CONFIRMATION_PAGE_SETTINGS_CLICKED);

        getAdServicesLogger().logUIStats(uiStats);
    }

    /** Logs that the user clicked got it on the opt-in confirmation page. */
    public static void logOptInConfirmationPageGotItClicked() {
        UIStats uiStats = getBaseUiStats();

        uiStats.setAction(
                FlagsFactory.getFlags().getGaUxFeatureEnabled()
                        ? AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_OPT_IN_CONFIRMATION_PAGE_GOT_IT_CLICKED
                        : AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__OPT_IN_CONFIRMATION_PAGE_GOT_IT_CLICKED);

        getAdServicesLogger().logUIStats(uiStats);
    }

    /** Logs that the user clicked got it on the opt-out confirmation page. */
    public static void logOptOutConfirmationPageGotItClicked() {
        UIStats uiStats = getBaseUiStats();

        uiStats.setAction(
                FlagsFactory.getFlags().getGaUxFeatureEnabled()
                        ? AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_OPT_OUT_CONFIRMATION_PAGE_GOT_IT_CLICKED
                        : AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__OPT_OUT_CONFIRMATION_PAGE_GOT_IT_CLICKED);

        getAdServicesLogger().logUIStats(uiStats);
    }

    /** GA only. Logs that the user clicked more info on the opt-in confirmation page. */
    public static void logOptInConfirmationPageMoreInfoClicked() {
        UIStats uiStats = getBaseUiStats();

        uiStats.setAction(
                AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_OPT_IN_CONFIRMATION_PAGE_MORE_INFO_CLICKED);

        getAdServicesLogger().logUIStats(uiStats);
    }

    /** GA only. Logs that the user clicked more info on the opt-out confirmation page. */
    public static void logOptOutConfirmationPageMoreInfoClicked() {
        UIStats uiStats = getBaseUiStats();

        uiStats.setAction(
                AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_OPT_OUT_CONFIRMATION_PAGE_MORE_INFO_CLICKED);

        getAdServicesLogger().logUIStats(uiStats);
    }

    /** Logs that the user dismissed the confirmation page. */
    public static void logConfirmationPageDismissed() {
        UIStats uiStats = getBaseUiStats();

        uiStats.setAction(
                FlagsFactory.getFlags().getGaUxFeatureEnabled()
                        ? AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_CONFIRMATION_PAGE_DISMISSED
                        : AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__CONFIRMATION_PAGE_DISMISSED);

        getAdServicesLogger().logUIStats(uiStats);
    }

    /** Logs that a notification was requested. */
    public static void logRequestedNotification() {
        UIStats uiStats = getBaseUiStats();

        uiStats.setUx(getUx(true));
        uiStats.setAction(
                FlagsFactory.getFlags().getGaUxFeatureEnabled()
                        ? AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_NOTIFICATION_REQUESTED
                        : AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__REQUESTED_NOTIFICATION);

        getAdServicesLogger().logUIStats(uiStats);
    }

    /** Logs that notifications are disabled on a device. */
    public static void logNotificationDisabled() {
        UIStats uiStats = getBaseUiStats();

        uiStats.setUx(getUx(true));
        uiStats.setAction(
                FlagsFactory.getFlags().getGaUxFeatureEnabled()
                        ? AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_NOTIFICATION_DISABLED
                        : AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__NOTIFICATION_DISABLED);

        getAdServicesLogger().logUIStats(uiStats);
    }

    /** Logs that the landing page was shown to a user. */
    public static void logLandingPageDisplayed() {
        UIStats uiStats = getBaseUiStats();

        uiStats.setAction(
                FlagsFactory.getFlags().getGaUxFeatureEnabled()
                        ? AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_NOTIFICATION_LANDING_PAGE_DISPLAYED
                        : AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__LANDING_PAGE_DISPLAYED);

        getAdServicesLogger().logUIStats(uiStats);
    }

    /** Logs that the confirmation page was shown to a user. */
    public static void logConfirmationPageDisplayed() {
        UIStats uiStats = getBaseUiStats();

        uiStats.setAction(
                FlagsFactory.getFlags().getGaUxFeatureEnabled()
                        ? AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_NOTIFICATION_CONFIRMATION_PAGE_DISPLAYED
                        : AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__CONFIRMATION_PAGE_DISPLAYED);

        getAdServicesLogger().logUIStats(uiStats);
    }

    /** Logs user opt-in action for PP API. */
    public static void logOptInSelected() {
        UIStats uiStats = getBaseUiStats();

        uiStats.setAction(
                FlagsFactory.getFlags().getGaUxFeatureEnabled()
                        ? AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__NOTIFICATION_OPT_IN_SELECTED
                        : AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__OPT_IN_SELECTED);

        getAdServicesLogger().logUIStats(uiStats);
    }

    /** Logs user opt-out action for PP API. */
    public static void logOptOutSelected() {
        UIStats uiStats = getBaseUiStats();

        uiStats.setAction(
                FlagsFactory.getFlags().getGaUxFeatureEnabled()
                        ? AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__NOTIFICATION_OPT_OUT_SELECTED
                        : AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__OPT_OUT_SELECTED);

        getAdServicesLogger().logUIStats(uiStats);
    }

    /** Logs user opt-in action given an ApiType. */
    public static void logOptInSelected(AdServicesApiType apiType) {
        UIStats uiStats = getBaseUiStats(apiType);

        uiStats.setAction(getPerApiConsentAction(apiType, /* isOptIn */ true));

        getAdServicesLogger().logUIStats(uiStats);
    }

    /** Logs user opt-out action given an ApiType. */
    public static void logOptOutSelected(AdServicesApiType apiType) {
        UIStats uiStats = getBaseUiStats(apiType);

        uiStats.setAction(getPerApiConsentAction(apiType, /* isOptIn */ false));

        getAdServicesLogger().logUIStats(uiStats);
    }

    /** Logs that a user has opened the settings page. */
    public static void logSettingsPageDisplayed() {
        UIStats uiStats = getBaseUiStats();

        uiStats.setAction(
                AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__PRIVACY_SANDBOX_SETTINGS_PAGE_DISPLAYED);

        getAdServicesLogger().logUIStats(uiStats);
    }

    /** Logs that a user has clicked manage topics button. */
    public static void logManageTopicsSelected() {
        UIStats uiStats = getBaseUiStats();

        uiStats.setAction(AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__MANAGE_TOPICS_SELECTED);

        getAdServicesLogger().logUIStats(uiStats);
    }

    /** Logs that a user has clicked manage apps button. */
    public static void logManageAppsSelected() {
        UIStats uiStats = getBaseUiStats();

        uiStats.setAction(AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__MANAGE_APPS_SELECTED);

        getAdServicesLogger().logUIStats(uiStats);
    }

    /** Logs that a user has clicked reset topics button. */
    public static void logResetTopicSelected() {
        UIStats uiStats = getBaseUiStats();

        uiStats.setAction(AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__RESET_TOPIC_SELECTED);

        getAdServicesLogger().logUIStats(uiStats);
    }

    /** Logs that a user has clicked reset apps button. */
    public static void logResetAppSelected() {
        UIStats uiStats = getBaseUiStats();

        uiStats.setAction(AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__RESET_APP_SELECTED);

        getAdServicesLogger().logUIStats(uiStats);
    }

    /** Logs that a user has clicked block topic button. */
    public static void logBlockTopicSelected() {
        UIStats uiStats = getBaseUiStats();

        uiStats.setAction(AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__BLOCK_TOPIC_SELECTED);

        getAdServicesLogger().logUIStats(uiStats);
    }

    /** Logs that a user has clicked unblock topic button. */
    public static void logUnblockTopicSelected() {
        UIStats uiStats = getBaseUiStats();

        uiStats.setAction(AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__UNBLOCK_TOPIC_SELECTED);

        getAdServicesLogger().logUIStats(uiStats);
    }

    /** Logs that a user has clicked block app button. */
    public static void logBlockAppSelected() {
        UIStats uiStats = getBaseUiStats();

        uiStats.setAction(AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__BLOCK_APP_SELECTED);

        getAdServicesLogger().logUIStats(uiStats);
    }

    /** Logs that a user has clicked unblock app button. */
    public static void logUnblockAppSelected() {
        UIStats uiStats = getBaseUiStats();

        uiStats.setAction(AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__UNBLOCK_APP_SELECTED);

        getAdServicesLogger().logUIStats(uiStats);
    }

    /** Logs that a user has clicked manage measurement button. */
    public static void logManageMeasurementSelected() {
        UIStats uiStats = getBaseUiStats();

        uiStats.setAction(AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__MANAGE_MEASUREMENT_SELECTED);

        getAdServicesLogger().logUIStats(uiStats);
    }

    /** Logs that a user has clicked reset measurement button. */
    public static void logResetMeasurementSelected() {
        UIStats uiStats = getBaseUiStats();

        uiStats.setAction(AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__RESET_MEASUREMENT_SELECTED);

        getAdServicesLogger().logUIStats(uiStats);
    }

    /** Logs that the user has clicked the privacy sandbox entry point in the settings page. */
    public static void logEntryPointClicked() {
        UIStats uiStats = getBaseUiStats();

        uiStats.setAction(
                AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__PRIVACY_SANDBOX_ENTRY_POINT_CLICKED);

        getAdServicesLogger().logUIStats(uiStats);

        LogUtil.d(PRIVACY_SANDBOX_UI_REQUEST_MESSAGE);
    }

    /** Logs that the second additional info button on the landing page was clicked. */
    public static void logLandingPageSecondAdditionalInfoClicked() {
        UIStats uiStats = getBaseUiStats();

        uiStats.setAction(
                AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__GA_UX_LANDING_PAGE_ADDITIONAL_INFO_2_CLICKED);

        getAdServicesLogger().logUIStats(uiStats);
    }

    @VisibleForTesting
    public static AdServicesLogger getAdServicesLogger() {
        return sLogger;
    }
    /** Logs that the user enter an unspecified ux flow. */
    public static void logRequestedNotificationIneligible() {
        UIStats uiStats = getBaseUiStats();

        /* Reuse beta's notification here, so that this track won't affect GA metrics */
        uiStats.setAction(AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__REQUESTED_NOTIFICATION);
        uiStats.setUx(AD_SERVICES_SETTINGS_USAGE_REPORTED__UX__UNSUPPORTED_UX);
        sLogger.logUIStats(uiStats);

        LogUtil.d(PRIVACY_SANDBOX_UI_REQUEST_MESSAGE);
    }

    private static int getRegion() {
        Context context = getApplicationContext();
        return DeviceRegionProvider.isEuDevice(context)
                ? AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__EU
                : AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__ROW;
    }

    private static int getDefaultConsent() {
        if (UxStatesManager.getInstance().getUx() == RVC_UX) {
            return getDefaultConsent(AdServicesApiType.MEASUREMENTS);
        }
        Boolean defaultConsent = ConsentManager.getInstance().getDefaultConsent();
        // edge case where the user opens the settings pages before receiving consent notification.
        if (defaultConsent == null) {
            return AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_CONSENT__CONSENT_UNSPECIFIED;
        } else {
            return defaultConsent
                    ? AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_CONSENT__PP_API_DEFAULT_OPT_IN
                    : AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_CONSENT__PP_API_DEFAULT_OPT_OUT;
        }
    }

    private static int getDefaultAdIdState() {
        Boolean defaultAdIdState = ConsentManager.getInstance().getDefaultAdIdState();
        // edge case where the user opens the settings pages before receiving consent notification.
        if (defaultAdIdState == null) {
            return AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_AD_ID_STATE__STATE_UNSPECIFIED;
        } else {
            return defaultAdIdState
                    ? AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_AD_ID_STATE__AD_ID_ENABLED
                    : AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_AD_ID_STATE__AD_ID_DISABLED;
        }
    }

    private static int getDefaultConsent(AdServicesApiType apiType) {
        switch (apiType) {
            case TOPICS:
                Boolean topicsDefaultConsent =
                        ConsentManager.getInstance().getTopicsDefaultConsent();
                // edge case where the user checks topic consent before receiving consent
                // notification.
                if (topicsDefaultConsent == null) {
                    return AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_AD_ID_STATE__STATE_UNSPECIFIED;
                } else {
                    return topicsDefaultConsent
                            ? AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_CONSENT__TOPICS_DEFAULT_OPT_IN
                            : AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_CONSENT__TOPICS_DEFAULT_OPT_OUT;
                }
            case FLEDGE:
                Boolean fledgeDefaultConsent =
                        ConsentManager.getInstance().getFledgeDefaultConsent();
                // edge case where the user checks FLEDGE consent before receiving consent
                // notification.
                if (fledgeDefaultConsent == null) {
                    return AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_AD_ID_STATE__STATE_UNSPECIFIED;
                } else {
                    return fledgeDefaultConsent
                            ? AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_CONSENT__FLEDGE_DEFAULT_OPT_IN
                            : AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_CONSENT__FLEDGE_DEFAULT_OPT_OUT;
                }
            case MEASUREMENTS:
                Boolean measurementDefaultConsent =
                        ConsentManager.getInstance().getMeasurementDefaultConsent();
                // edge case where the user checks measurement consent before receiving consent
                // notification.
                if (measurementDefaultConsent == null) {
                    return AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_AD_ID_STATE__STATE_UNSPECIFIED;
                } else {
                    return measurementDefaultConsent
                            ? AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_CONSENT__MEASUREMENT_DEFAULT_OPT_IN
                            : AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_CONSENT__MEASUREMENT_DEFAULT_OPT_OUT;
                }
            default:
                return AD_SERVICES_SETTINGS_USAGE_REPORTED__DEFAULT_CONSENT__CONSENT_UNSPECIFIED;
        }
    }

    private static int getPerApiConsentAction(AdServicesApiType apiType, boolean isOptIn) {
        switch (apiType) {
            case TOPICS:
                return isOptIn
                        ? AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__TOPICS_OPT_IN_SELECTED
                        : AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__TOPICS_OPT_OUT_SELECTED;
            case FLEDGE:
                return isOptIn
                        ? AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__FLEDGE_OPT_IN_SELECTED
                        : AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__FLEDGE_OPT_OUT_SELECTED;
            case MEASUREMENTS:
                return isOptIn
                        ? AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__MEASUREMENT_OPT_IN_SELECTED
                        : AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__MEASUREMENT_OPT_OUT_SELECTED;
            default:
                return AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__ACTION_UNSPECIFIED;
        }
    }

    private static int getUx(boolean beforeNotificationShown) {
        switch (UxStatesManager.getInstance().getUx()) {
            case U18_UX:
                return AD_SERVICES_SETTINGS_USAGE_REPORTED__UX__UNSPECIFIED_UX;
            case RVC_UX:
                return AD_SERVICES_SETTINGS_USAGE_REPORTED__UX__RVC_UX;
            case GA_UX:
                if (UxStatesManager.getInstance().pasUxIsActive(beforeNotificationShown)) {
                    // UI views should be updated only once notification is sent (ROW).
                    return AD_SERVICES_SETTINGS_USAGE_REPORTED__UX__GA_UX_WITH_PAS;
                }
                return AD_SERVICES_SETTINGS_USAGE_REPORTED__UX__GA_UX;
            case BETA_UX:
                return AD_SERVICES_SETTINGS_USAGE_REPORTED__UX__BETA_UX;
            default:
                return AD_SERVICES_SETTINGS_USAGE_REPORTED__UX__UNSUPPORTED_UX;
        }
    }

    private static int getEnrollmentChannel() {
        PrivacySandboxEnrollmentChannelCollection enrollmentChannel =
                UxStatesManager.getInstance().getEnrollmentChannel();
        if (enrollmentChannel == GaUxEnrollmentChannelCollection.FIRST_CONSENT_NOTIFICATION_CHANNEL
                || enrollmentChannel
                        == BetaUxEnrollmentChannelCollection.FIRST_CONSENT_NOTIFICATION_CHANNEL
                || enrollmentChannel
                        == U18UxEnrollmentChannelCollection.FIRST_CONSENT_NOTIFICATION_CHANNEL
                || enrollmentChannel
                        == RvcUxEnrollmentChannelCollection.FIRST_CONSENT_NOTIFICATION_CHANNEL) {
            return AD_SERVICES_SETTINGS_USAGE_REPORTED__ENROLLMENT_CHANNEL__FIRST_CONSENT_NOTIFICATION_CHANNEL;
        } else if (enrollmentChannel
                == GaUxEnrollmentChannelCollection.PAS_FIRST_CONSENT_NOTIFICATION_CHANNEL) {
            return AD_SERVICES_SETTINGS_USAGE_REPORTED__ENROLLMENT_CHANNEL__PAS_FIRST_NOTIFICATION_CHANNEL;
        } else if (enrollmentChannel
                        == GaUxEnrollmentChannelCollection.CONSENT_NOTIFICATION_DEBUG_CHANNEL
                || enrollmentChannel
                        == BetaUxEnrollmentChannelCollection.CONSENT_NOTIFICATION_DEBUG_CHANNEL
                || enrollmentChannel
                        == U18UxEnrollmentChannelCollection.CONSENT_NOTIFICATION_DEBUG_CHANNEL
                || enrollmentChannel
                        == RvcUxEnrollmentChannelCollection.CONSENT_NOTIFICATION_DEBUG_CHANNEL) {
            return AD_SERVICES_SETTINGS_USAGE_REPORTED__ENROLLMENT_CHANNEL__CONSENT_NOTIFICATION_DEBUG_CHANNEL;
        } else if (enrollmentChannel == GaUxEnrollmentChannelCollection.ALREADY_ENROLLED_CHANNEL
                || enrollmentChannel == BetaUxEnrollmentChannelCollection.ALREADY_ENROLLED_CHANNEL
                || enrollmentChannel == U18UxEnrollmentChannelCollection.ALREADY_ENROLLED_CHANNEL
                || enrollmentChannel == RvcUxEnrollmentChannelCollection.ALREADY_ENROLLED_CHANNEL) {
            return AD_SERVICES_SETTINGS_USAGE_REPORTED__ENROLLMENT_CHANNEL__ALREADY_ENROLLED_CHANNEL;
        } else if (enrollmentChannel
                == GaUxEnrollmentChannelCollection.RECONSENT_NOTIFICATION_CHANNEL) {
            return AD_SERVICES_SETTINGS_USAGE_REPORTED__ENROLLMENT_CHANNEL__RECONSENT_NOTIFICATION_CHANNEL;
        } else if (enrollmentChannel
                == GaUxEnrollmentChannelCollection.PAS_RECONSENT_NOTIFICATION_CHANNEL) {
            return AD_SERVICES_SETTINGS_USAGE_REPORTED__ENROLLMENT_CHANNEL__PAS_RENOTIFY_NOTIFICATION_CHANNEL;
        } else if (enrollmentChannel == GaUxEnrollmentChannelCollection.GA_GRADUATION_CHANNEL) {
            return AD_SERVICES_SETTINGS_USAGE_REPORTED__ENROLLMENT_CHANNEL__UNSPECIFIED_CHANNEL;
        } else if (enrollmentChannel == U18UxEnrollmentChannelCollection.U18_DETENTION_CHANNEL) {
            return AD_SERVICES_SETTINGS_USAGE_REPORTED__ENROLLMENT_CHANNEL__UNSPECIFIED_CHANNEL;
        } else if (enrollmentChannel == GaUxEnrollmentChannelCollection.RVC_POST_OTA_CHANNEL) {
            return AD_SERVICES_SETTINGS_USAGE_REPORTED__ENROLLMENT_CHANNEL__RVC_POST_OTA_NOTIFICATION_CHANNEL;
        }
        return AD_SERVICES_SETTINGS_USAGE_REPORTED__ENROLLMENT_CHANNEL__UNSUPPORTED_CHANNEL;
    }

    private static UIStats getBaseUiStats() {
        return new UIStats.Builder()
                .setCode(AD_SERVICES_SETTINGS_USAGE_REPORTED)
                .setRegion(getRegion())
                .setDefaultConsent(getDefaultConsent())
                .setDefaultAdIdState(getDefaultAdIdState())
                .setUx(getUx(/* beforeNotificationShown */ false))
                .setEnrollmentChannel(getEnrollmentChannel())
                .build();
    }

    private static UIStats getBaseUiStats(AdServicesApiType apiType) {
        return new UIStats.Builder()
                .setCode(AD_SERVICES_SETTINGS_USAGE_REPORTED)
                .setRegion(getRegion())
                .setDefaultConsent(getDefaultConsent(apiType))
                .setDefaultAdIdState(getDefaultAdIdState())
                .setUx(getUx(/* beforeNotificationShown */ false))
                .setEnrollmentChannel(getEnrollmentChannel())
                .build();
    }

    private static Context getApplicationContext() {
        return ApplicationContextSingleton.get();
    }

    private UiStatsLogger() {
        throw new UnsupportedOperationException();
    }
}
