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

import static com.android.adservices.ui.UxUtil.isUxStatesReady;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.FragmentActivity;

import com.android.adservices.LogUtil;
import com.android.adservices.api.R;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.stats.UiStatsLogger;
import com.android.adservices.ui.OTAResourcesManager;
import com.android.adservices.ui.UxSelector;

/**
 * Android application activity for controlling settings related to PP (Privacy Preserving) APIs.
 */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class ConsentNotificationActivity extends FragmentActivity implements UxSelector {
    public enum NotificationFragmentEnum {
        LANDING_PAGE_DISPLAYED,
        LANDING_PAGE_DISMISSED,
        LANDING_PAGE_SCROLLED,
        LANDING_PAGE_SCROLLED_TO_BOTTOM,
        LANDING_PAGE_ADDITIONAL_INFO_CLICKED,
        LANDING_PAGE_ADDITIONAL_INFO_2_CLICKED,
        LANDING_PAGE_MORE_BUTTON_CLICKED,
        LANDING_PAGE_SETTINGS_BUTTON_CLICKED,
        LANDING_PAGE_OPT_IN_CLICKED,
        LANDING_PAGE_OPT_OUT_CLICKED,
        LANDING_PAGE_GOT_IT_CLICKED,
        CONFIRMATION_PAGE_DISPLAYED,
        CONFIRMATION_PAGE_DISMISSED,
        CONFIRMATION_PAGE_OPT_IN_MORE_INFO_CLICKED,
        CONFIRMATION_PAGE_OPT_OUT_MORE_INFO_CLICKED,
        CONFIRMATION_PAGE_OPT_IN_SETTINGS_CLICKED,
        CONFIRMATION_PAGE_OPT_OUT_SETTINGS_CLICKED,
        CONFIRMATION_PAGE_OPT_IN_GOT_IT_BUTTON_CLICKED,
        CONFIRMATION_PAGE_OPT_OUT_GOT_IT_BUTTON_CLICKED,
    }

    private static NotificationFragmentEnum sCurrentFragment;
    private static boolean sLandingPageDismissed;
    private static boolean sConfirmationPageDismissed;
    private static boolean sLandingPageDisplayed;
    private static boolean sLandingPageMoreButtonClicked;
    private static boolean sLandingPageSettingsButtonClicked;
    private static boolean sConfirmationPageDisplayed;
    private static boolean sLandingPageScrolled;
    private static boolean sLandingPageScrolledToBottom;
    private static boolean sLandingPageAdditionalInfoClicked;
    private static boolean sLandingPageSecondAdditionalInfoClicked;
    private static boolean sLandingPageOptInClicked;
    private static boolean sLandingPageOptOutClicked;
    private static boolean sLandingPageGotItClicked;
    private static boolean sConfirmationPageOptInMoreInfoClicked;
    private static boolean sConfirmationPageOptOutMoreInfoClicked;
    private static boolean sConfirmationPageOptInSettingsClicked;
    private static boolean sConfirmationPageOptOutSettingsClicked;
    private static boolean sConfirmationPageOptInGotItClicked;
    private static boolean sConfirmationPageOptOutGotItClicked;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context context = getApplicationContext();

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        if (FlagsFactory.getFlags().getUiOtaStringsFeatureEnabled()) {
            OTAResourcesManager.applyOTAResources(context, true);
        }

        if (FlagsFactory.getFlags().getConsentNotificationActivityDebugMode()
                || isUxStatesReady(this)) {
            initWithUx(this, context);
        } else {
            initFragment();
        }
    }

    @Override
    public void initBeta() {
        setContentView(R.layout.consent_notification_activity);
    }

    @Override
    public void initGA() {
        setContentView(R.layout.consent_notification_ga_v2_activity);
    }

    @Override
    public void initU18() {
        setContentView(R.layout.consent_notification_u18_activity);
    }

    @Override
    public void initRvc() {
        initU18();
    }

    @Override
    public void initGaUxWithPas() {
        setContentView(R.layout.consent_notification_pas_activity);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outBundle) {
        super.onSaveInstanceState(outBundle);
    }

    private void initFragment() {
        if (FlagsFactory.getFlags().getGaUxFeatureEnabled()) {
            setContentView(R.layout.consent_notification_ga_v2_activity);
        } else {
            setContentView(R.layout.consent_notification_activity);
        }
    }

    /**
     * Notification fragments should call this when view is created. Used to keep track of user's
     * current page and log correct page exit if user exits.
     */
    public static void handleAction(NotificationFragmentEnum fragmentAction, Context context) {
        switch (fragmentAction) {
            case LANDING_PAGE_DISPLAYED:
                sCurrentFragment = fragmentAction;
                if (sLandingPageDisplayed) {
                    return;
                }
                LogUtil.v("LANDING_PAGE_DISPLAYED logged!");
                sLandingPageDisplayed = true;
                UiStatsLogger.logLandingPageDisplayed();
                break;
            case LANDING_PAGE_DISMISSED:
                if (sLandingPageDismissed
                        || sCurrentFragment != NotificationFragmentEnum.LANDING_PAGE_DISPLAYED) {
                    return;
                }
                sLandingPageDismissed = true;
                LogUtil.v("LANDING_PAGE_DISMISSED logged!");
                UiStatsLogger.logLandingPageDismissed();
                break;
            case LANDING_PAGE_SCROLLED:
                if (sLandingPageScrolled) {
                    return;
                }
                sLandingPageScrolled = true;
                LogUtil.v("LANDING_PAGE_SCROLLED logged!");
                UiStatsLogger.logLandingPageScrolled();
                break;
            case LANDING_PAGE_SCROLLED_TO_BOTTOM:
                if (sLandingPageScrolledToBottom) {
                    return;
                }
                sLandingPageScrolledToBottom = true;
                LogUtil.v("LANDING_PAGE_SCROLLED_TO_BOTTOM logged!");
                UiStatsLogger.logLandingPageScrolledToBottom();
                break;
            case LANDING_PAGE_ADDITIONAL_INFO_CLICKED:
                if (sLandingPageAdditionalInfoClicked) {
                    return;
                }
                sLandingPageAdditionalInfoClicked = true;
                LogUtil.v("LANDING_PAGE_ADDITIONAL_INFO_CLICKED logged!");
                UiStatsLogger.logLandingPageAdditionalInfoClicked();
                break;
            case LANDING_PAGE_ADDITIONAL_INFO_2_CLICKED:
                if (sLandingPageSecondAdditionalInfoClicked) {
                    return;
                }
                sLandingPageSecondAdditionalInfoClicked = true;
                LogUtil.v("LANDING_PAGE_ADDITIONAL_INFO_2_CLICKED logged!");
                UiStatsLogger.logLandingPageSecondAdditionalInfoClicked();
                break;
            case LANDING_PAGE_MORE_BUTTON_CLICKED:
                if (sLandingPageMoreButtonClicked) {
                    return;
                }
                sLandingPageMoreButtonClicked = true;
                LogUtil.v("LANDING_PAGE_MORE_BUTTON_CLICKED logged!");
                UiStatsLogger.logLandingPageMoreButtonClicked();
                break;
            case LANDING_PAGE_SETTINGS_BUTTON_CLICKED:
                if (sLandingPageSettingsButtonClicked) {
                    return;
                }
                sLandingPageSettingsButtonClicked = true;
                LogUtil.v("LANDING_PAGE_SETTINGS_BUTTON_CLICKED logged!");
                UiStatsLogger.logLandingPageSettingsButtonClicked();
                break;
            case LANDING_PAGE_OPT_IN_CLICKED:
                if (sLandingPageOptInClicked) {
                    return;
                }
                sLandingPageOptInClicked = true;
                LogUtil.v("LANDING_PAGE_OPT_IN_CLICKED logged!");
                UiStatsLogger.logLandingPageOptIn();
                break;
            case LANDING_PAGE_OPT_OUT_CLICKED:
                if (sLandingPageOptOutClicked) {
                    return;
                }
                sLandingPageOptOutClicked = true;
                LogUtil.v("LANDING_PAGE_OPT_OUT_CLICKED logged!");
                UiStatsLogger.logLandingPageOptOut();
                break;
            case LANDING_PAGE_GOT_IT_CLICKED:
                if (sLandingPageGotItClicked) {
                    return;
                }
                sLandingPageGotItClicked = true;
                LogUtil.v("LANDING_PAGE_GOT_IT_CLICKED logged!");
                UiStatsLogger.logLandingPageGotItButtonClicked();
                break;
            case CONFIRMATION_PAGE_DISPLAYED:
                sCurrentFragment = fragmentAction;
                if (sConfirmationPageDisplayed) {
                    return;
                }
                LogUtil.v("CONFIRMATION_PAGE_DISPLAYED logged!");
                sConfirmationPageDisplayed = true;
                UiStatsLogger.logConfirmationPageDisplayed();
                break;
            case CONFIRMATION_PAGE_DISMISSED:
                if (sConfirmationPageDismissed
                        || sCurrentFragment
                                != NotificationFragmentEnum.CONFIRMATION_PAGE_DISPLAYED) {
                    return;
                }
                sConfirmationPageDismissed = true;
                LogUtil.v("CONFIRMATION_PAGE_DISMISSED logged!");
                UiStatsLogger.logConfirmationPageDismissed();
                break;
            case CONFIRMATION_PAGE_OPT_IN_MORE_INFO_CLICKED:
                if (sConfirmationPageOptInMoreInfoClicked) {
                    return;
                }
                sConfirmationPageOptInMoreInfoClicked = true;
                LogUtil.v("CONFIRMATION_PAGE_OPT_IN_MORE_INFO_CLICKED logged!");
                UiStatsLogger.logOptInConfirmationPageMoreInfoClicked();
                break;
            case CONFIRMATION_PAGE_OPT_OUT_MORE_INFO_CLICKED:
                if (sConfirmationPageOptOutMoreInfoClicked) {
                    return;
                }
                sConfirmationPageOptOutMoreInfoClicked = true;
                LogUtil.v("CONFIRMATION_PAGE_OPT_OUT_MORE_INFO_CLICKED logged!");
                UiStatsLogger.logOptOutConfirmationPageMoreInfoClicked();
                break;
            case CONFIRMATION_PAGE_OPT_IN_SETTINGS_CLICKED:
                if (sConfirmationPageOptInSettingsClicked) {
                    return;
                }
                sConfirmationPageOptInSettingsClicked = true;
                LogUtil.v("CONFIRMATION_PAGE_OPT_IN_SETTINGS_CLICKED logged!");
                UiStatsLogger.logOptInConfirmationPageSettingsClicked();
                break;
            case CONFIRMATION_PAGE_OPT_OUT_SETTINGS_CLICKED:
                if (sConfirmationPageOptOutSettingsClicked) {
                    return;
                }
                sConfirmationPageOptOutSettingsClicked = true;
                LogUtil.v("CONFIRMATION_PAGE_OPT_OUT_SETTINGS_CLICKED logged!");
                UiStatsLogger.logOptOutConfirmationPageSettingsClicked();
                break;
            case CONFIRMATION_PAGE_OPT_IN_GOT_IT_BUTTON_CLICKED:
                if (sConfirmationPageOptInGotItClicked) {
                    return;
                }
                sConfirmationPageOptInGotItClicked = true;
                LogUtil.v("CONFIRMATION_PAGE_OPT_IN_GOT_IT_BUTTON_CLICKED logged!");
                UiStatsLogger.logOptInConfirmationPageGotItClicked();
                break;
            case CONFIRMATION_PAGE_OPT_OUT_GOT_IT_BUTTON_CLICKED:
                if (sConfirmationPageOptOutGotItClicked) {
                    return;
                }
                sConfirmationPageOptOutGotItClicked = true;
                LogUtil.v("CONFIRMATION_PAGE_OPT_OUT_GOT_IT_BUTTON_CLICKED logged!");
                UiStatsLogger.logOptOutConfirmationPageGotItClicked();
                break;
        }
    }
}
