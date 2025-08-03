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
package com.android.adservices.ui.settings.delegates;

import android.content.Intent;
import android.icu.text.MessageFormat;
import android.os.Build;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.lifecycle.Observer;

import com.android.adservices.api.R;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.stats.UiStatsLogger;
import com.android.adservices.ui.settings.DialogFragmentManager;
import com.android.adservices.ui.settings.DialogManager;
import com.android.adservices.ui.settings.activities.AdServicesSettingsMainActivity;
import com.android.adservices.ui.settings.activities.AppsActivity;
import com.android.adservices.ui.settings.activities.MeasurementActivity;
import com.android.adservices.ui.settings.activities.TopicsActivity;
import com.android.adservices.ui.settings.fragments.AdServicesSettingsMainFragment;
import com.android.adservices.ui.settings.viewmodels.MainViewModel;
import com.android.settingslib.widget.MainSwitchBar;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Delegate class that helps AdServices Settings fragments to respond to all view model/user events.
 */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class MainActionDelegate {
    private final AdServicesSettingsMainActivity mAdServicesSettingsMainActivity;
    private final MainViewModel mMainViewModel;

    public MainActionDelegate(
            AdServicesSettingsMainActivity mainSettingsActivity, MainViewModel mainViewModel) {
        mAdServicesSettingsMainActivity = mainSettingsActivity;
        mMainViewModel = mainViewModel;
        listenToMainViewModelUiEvents();
    }

    private void listenToMainViewModelUiEvents() {
        Observer<MainViewModel.MainViewModelUiEvent> observer =
                event -> {
                    if (event == null) {
                        return;
                    }
                    try {
                        switch (event) {
                            case SWITCH_ON_PRIVACY_SANDBOX_BETA:
                                mMainViewModel.setConsent(true);
                                break;
                            case SWITCH_OFF_PRIVACY_SANDBOX_BETA:
                                if (FlagsFactory.getFlags().getUiDialogsFeatureEnabled()) {
                                    if (FlagsFactory.getFlags().getUiDialogFragmentEnabled()) {
                                        DialogFragmentManager.showOptOutDialogFragment(
                                                mAdServicesSettingsMainActivity, mMainViewModel);
                                    } else {
                                        DialogManager.showOptOutDialog(
                                                mAdServicesSettingsMainActivity, mMainViewModel);
                                    }
                                } else {
                                    mMainViewModel.setConsent(false);
                                }
                                break;
                            case DISPLAY_APPS_FRAGMENT:
                                UiStatsLogger.logManageAppsSelected();
                                mAdServicesSettingsMainActivity.startActivity(
                                        new Intent(
                                                mAdServicesSettingsMainActivity,
                                                AppsActivity.class));
                                break;
                            case DISPLAY_TOPICS_FRAGMENT:
                                UiStatsLogger.logManageTopicsSelected();
                                mAdServicesSettingsMainActivity.startActivity(
                                        new Intent(
                                                mAdServicesSettingsMainActivity,
                                                TopicsActivity.class));
                                break;
                            case DISPLAY_MEASUREMENT_FRAGMENT:
                                UiStatsLogger.logManageMeasurementSelected();
                                mAdServicesSettingsMainActivity.startActivity(
                                        new Intent(
                                                mAdServicesSettingsMainActivity,
                                                MeasurementActivity.class));
                                break;
                        }
                    } finally {
                        mMainViewModel.uiEventHandled();
                    }
                };
        mMainViewModel.getUiEvents().observe(mAdServicesSettingsMainActivity, observer);
    }

    /**
     * Configure all UI elements in {@link AdServicesSettingsMainFragment} to handle user actions.
     *
     * @param fragment the fragment to be initialized.
     */
    public void initMainFragment(AdServicesSettingsMainFragment fragment) {
        // Hide the main toggle and the entry point of Measurement
        // in Main page behind the GaUxFeature Flag

        int[] betaLayout =
                new int[] {
                    R.id.main_switch_bar,
                    R.id.above_pic_paragraph,
                    R.id.main_view_pic,
                    R.id.main_view_footer
                };

        int[] gaUxLayout = new int[] {R.id.main_view_ga_pic, R.id.main_view_ga_footer};

        if (FlagsFactory.getFlags().getGaUxFeatureEnabled()) {
            mAdServicesSettingsMainActivity.setTitle(R.string.settingsUI_main_view_ga_title);
            setLayoutVisibility(betaLayout, View.GONE);
            setLayoutVisibility(gaUxLayout, View.VISIBLE);
        } else {
            mAdServicesSettingsMainActivity.setTitle(R.string.settingsUI_main_view_title);
            setLayoutVisibility(betaLayout, View.VISIBLE);
            setLayoutVisibility(gaUxLayout, View.GONE);
        }

        configureConsentSwitch(fragment);
        configureMeasurementButton(fragment);
        configureTopicsButton(fragment);
        configureAppsButton(fragment);
        configureSubtitles(fragment);
        configureLearnMore(fragment);
    }

    private void configureLearnMore(AdServicesSettingsMainFragment fragment) {
        if (FlagsFactory.getFlags().getGaUxFeatureEnabled()) {
            ((TextView) fragment.requireView().findViewById(R.id.main_view_ga_footer_learn_more))
                    .setMovementMethod(LinkMovementMethod.getInstance());
        }
    }

    private void setLayoutVisibility(int[] layoutList, int visibility) {
        for (int each : layoutList) {
            mAdServicesSettingsMainActivity.findViewById(each).setVisibility(visibility);
        }
    }

    private void configureConsentSwitch(AdServicesSettingsMainFragment fragment) {
        MainSwitchBar mainSwitchBar =
                mAdServicesSettingsMainActivity.findViewById(R.id.main_switch_bar);

        if (FlagsFactory.getFlags().getGaUxFeatureEnabled()) {
            mainSwitchBar.setVisibility(View.GONE);
            return;
        } else {
            mainSwitchBar.setVisibility(View.VISIBLE);
        }

        mMainViewModel.getConsent().observe(fragment, mainSwitchBar::setChecked);

        mainSwitchBar.setOnClickListener(
                switchBar -> mMainViewModel.consentSwitchClickHandler((MainSwitchBar) switchBar));
    }

    private void configureTopicsButton(AdServicesSettingsMainFragment fragment) {
        TextView topicsPreferenceTitle =
                fragment.requireView().findViewById(R.id.topics_preference_title);
        if (FlagsFactory.getFlags().getGaUxFeatureEnabled()) {
            topicsPreferenceTitle.setText(R.string.settingsUI_topics_ga_title);
        } else {
            topicsPreferenceTitle.setText(R.string.settingsUI_topics_title);
        }

        View topicsButton = fragment.requireView().findViewById(R.id.topics_preference);
        topicsButton.setOnClickListener(preference -> mMainViewModel.topicsButtonClickHandler());
    }

    private void configureAppsButton(AdServicesSettingsMainFragment fragment) {
        TextView appsPreferenceTitle =
                fragment.requireView().findViewById(R.id.apps_preference_title);
        if (FlagsFactory.getFlags().getGaUxFeatureEnabled()) {
            appsPreferenceTitle.setText(R.string.settingsUI_apps_ga_title);
        } else {
            appsPreferenceTitle.setText(R.string.settingsUI_apps_title);
        }

        View appsButton = fragment.requireView().findViewById(R.id.apps_preference);
        appsButton.setOnClickListener(preference -> mMainViewModel.appsButtonClickHandler());
    }

    private void configureMeasurementButton(AdServicesSettingsMainFragment fragment) {
        View measurementButton = fragment.requireView().findViewById(R.id.measurement_preference);
        if (FlagsFactory.getFlags().getGaUxFeatureEnabled()) {
            measurementButton.setVisibility(View.VISIBLE);
        } else {
            measurementButton.setVisibility(View.GONE);
            return;
        }
        measurementButton.setOnClickListener(
                preference -> mMainViewModel.measurementClickHandler());
    }

    /**
     * Configure the subtitles of topics/apps/measurement that can display the state of their
     * preferences (ON or OFF of the consent of topics/apps/measurement and the number of
     * topics/apps with consent) on the Settings main page
     *
     * @param fragment the fragment to be initialized.
     */
    public void configureSubtitles(AdServicesSettingsMainFragment fragment) {
        configureMeasurementSubtitle(fragment);
        configureAppsSubtitle(fragment);
        configureTopicsSubtitle(fragment);
    }

    /**
     * Configure the subtitle of measurement that can display the state (ON or OFF) of the
     * measurement consent on the Settings main page
     *
     * @param fragment the fragment to be initialized.
     */
    private void configureMeasurementSubtitle(AdServicesSettingsMainFragment fragment) {
        TextView measurementSubtitle =
                fragment.requireView().findViewById(R.id.measurement_preference_subtitle);
        if (FlagsFactory.getFlags().getGaUxFeatureEnabled()) {
            measurementSubtitle.setVisibility(View.VISIBLE);
        } else {
            measurementSubtitle.setVisibility(View.GONE);
            return;
        }

        if (mMainViewModel.getMeasurementConsentFromConsentManager()) {
            measurementSubtitle.setText(R.string.settingsUI_subtitle_consent_on);
        } else {
            measurementSubtitle.setText(R.string.settingsUI_subtitle_consent_off);
        }
    }

    /**
     * Configure the subtitle of topics that can display the state of topics preference (ON or OFF
     * of the topic consent and the number of topics with consent) on the Settings main page
     *
     * @param fragment the fragment to be initialized.
     */
    private void configureTopicsSubtitle(AdServicesSettingsMainFragment fragment) {
        TextView topicsSubtitle =
                fragment.requireView().findViewById(R.id.topics_preference_subtitle);
        if (FlagsFactory.getFlags().getGaUxFeatureEnabled()) {
            topicsSubtitle.setVisibility(View.VISIBLE);
        } else {
            topicsSubtitle.setVisibility(View.GONE);
            return;
        }
        if (mMainViewModel.getTopicsConsentFromConsentManager()) {
            topicsSubtitle.setText(
                    getQuantityString(
                            mMainViewModel.getCountOfTopics(),
                            R.string.settingsUI_topics_subtitle_plural));
        } else {
            topicsSubtitle.setText(R.string.settingsUI_subtitle_consent_off);
        }
    }

    /**
     * Configure the subtitle of apps that can display the state of apps preference (ON or OFF of
     * the topic consent and the number of topics with consent) on the Settings main page
     *
     * @param fragment the fragment to be initialized.
     */
    private void configureAppsSubtitle(AdServicesSettingsMainFragment fragment) {
        TextView appsSubtitle = fragment.requireView().findViewById(R.id.apps_preference_subtitle);
        if (FlagsFactory.getFlags().getGaUxFeatureEnabled()) {
            appsSubtitle.setVisibility(View.VISIBLE);
        } else {
            appsSubtitle.setVisibility(View.GONE);
            return;
        }

        if (mMainViewModel.getAppsConsentFromConsentManager()) {
            appsSubtitle.setText(
                    getQuantityString(
                            mMainViewModel.getCountOfApps(),
                            R.string.settingsUI_apps_subtitle_plural));
        } else {
            appsSubtitle.setText(R.string.settingsUI_subtitle_consent_off);
        }
    }

    /**
     * An alternative getQuantity method of Android <plurals> using
     * Locale.getDefault(Locale.Category.FORMAT)
     *
     * @param count the count that determines the format
     * @param stringId the id of the quantity string
     * @return String in format (plural or singular) according to the count
     */
    private String getQuantityString(int count, int stringId) {
        MessageFormat msgFormat =
                new MessageFormat(
                        mAdServicesSettingsMainActivity.getResources().getString(stringId),
                        Locale.getDefault(Locale.Category.FORMAT));
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("count", count);
        return msgFormat.format(arguments);
    }
}
