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
package com.android.adservices.ui.settings.activitydelegates;

import android.content.Intent;
import android.icu.text.MessageFormat;
import android.os.Build;
import android.view.View;

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
public class MainActivityActionDelegate extends BaseActionDelegate {
    private static final int[] BetaOnlyElements =
            new int[] {
                R.id.main_switch_bar,
                R.id.above_pic_paragraph,
                R.id.main_view_pic,
                R.id.main_view_footer
            };
    private static final int[] GaOnlyElements =
            new int[] {R.id.main_view_ga_pic, R.id.main_view_ga_footer};
    private final MainViewModel mMainViewModel;

    public MainActivityActionDelegate(
            AdServicesSettingsMainActivity mainSettingsActivity, MainViewModel mainViewModel) {
        super(mainSettingsActivity);
        mMainViewModel = mainViewModel;
        initWithUx(mainSettingsActivity, mainSettingsActivity.getApplicationContext());
        listenToMainViewModelUiEvents();
    }

    @Override
    public void initBeta() {
        // hidden elements
        hideElements(GaOnlyElements);
        // show elements
        showElements(BetaOnlyElements);

        mActivity.setTitle(R.string.settingsUI_main_view_title);
        // privacy sandbox controls
        configureElement(
                R.id.privacy_sandbox_controls,
                mMainViewModel.getConsent(),
                controls ->
                        consent -> controls.setVisibility((consent ? View.VISIBLE : View.GONE)));
        // main consent switch
        configureElement(
                R.id.main_switch_bar,
                switchBar -> mMainViewModel.consentSwitchClickHandler((MainSwitchBar) switchBar),
                mMainViewModel.getConsent(),
                switchBar -> ((MainSwitchBar) switchBar)::setChecked);
        // topics button
        configureElement(
                R.id.topics_preference, button -> mMainViewModel.topicsButtonClickHandler());
        configureElement(R.id.topics_preference_title, R.string.settingsUI_topics_title);
        // apps button
        configureElement(R.id.apps_preference, button -> mMainViewModel.appsButtonClickHandler());
        configureElement(R.id.apps_preference_title, R.string.settingsUI_apps_title);
    }

    @Override
    public void initGA() {
        // hidden elements
        hideElements(BetaOnlyElements);
        // show elements
        showElements(GaOnlyElements);

        mActivity.setTitle(R.string.settingsUI_main_view_ga_title);

        // privacy sandbox controls
        mActivity.findViewById(R.id.privacy_sandbox_controls).setVisibility(View.VISIBLE);
        // topics button
        configureElement(
                R.id.topics_preference, button -> mMainViewModel.topicsButtonClickHandler());
        configureElement(R.id.topics_preference_title, R.string.settingsUI_topics_ga_title);
        if (mMainViewModel.getTopicsConsentFromConsentManager()) {
            configureElement(
                    R.id.topics_preference_subtitle,
                    getQuantityString(
                            mMainViewModel.getCountOfTopics(),
                            R.string.settingsUI_topics_subtitle_plural));
        } else {
            configureElement(
                    R.id.topics_preference_subtitle, R.string.settingsUI_subtitle_consent_off);
        }
        // apps button
        configureElement(R.id.apps_preference, button -> mMainViewModel.appsButtonClickHandler());
        configureElement(R.id.apps_preference_title, R.string.settingsUI_apps_ga_title);
        if (mMainViewModel.getAppsConsentFromConsentManager()) {
            configureElement(
                    R.id.apps_preference_subtitle,
                    getQuantityString(
                            mMainViewModel.getCountOfApps(),
                            R.string.settingsUI_apps_subtitle_plural));
        } else {
            configureElement(
                    R.id.apps_preference_subtitle, R.string.settingsUI_subtitle_consent_off);
        }
        // measurement button
        configureElement(
                R.id.measurement_preference, button -> mMainViewModel.measurementClickHandler());
        configureElement(
                R.id.measurement_preference_title, R.string.settingsUI_measurement_ga_title);
        configureElement(
                R.id.measurement_preference_subtitle,
                mMainViewModel.getMeasurementConsentFromConsentManager()
                        ? R.string.settingsUI_subtitle_consent_on
                        : R.string.settingsUI_subtitle_consent_off);
        configureLink(R.id.main_view_ga_footer_learn_more);
    }

    @Override
    public void initU18() {
        mActivity.setTitle(R.string.settingsUI_main_view_ga_title);
        // measurement button
        configureElement(
                R.id.measurement_preference, button -> mMainViewModel.measurementClickHandler());
        configureElement(
                R.id.measurement_preference_subtitle,
                mMainViewModel.getMeasurementConsentFromConsentManager()
                        ? R.string.settingsUI_subtitle_consent_on
                        : R.string.settingsUI_subtitle_consent_off);
    }

    @Override
    public void initRvc() {
        initU18();
    }

    @Override
    public void initGaUxWithPas() {
        initGA();
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
                                                mActivity, mMainViewModel);
                                    } else {
                                        DialogManager.showOptOutDialog(mActivity, mMainViewModel);
                                    }
                                } else {
                                    mMainViewModel.setConsent(false);
                                }
                                break;
                            case DISPLAY_APPS_FRAGMENT:
                                UiStatsLogger.logManageAppsSelected();
                                mActivity.startActivity(new Intent(mActivity, AppsActivity.class));
                                break;
                            case DISPLAY_TOPICS_FRAGMENT:
                                UiStatsLogger.logManageTopicsSelected();
                                mActivity.startActivity(
                                        new Intent(mActivity, TopicsActivity.class));
                                break;
                            case DISPLAY_MEASUREMENT_FRAGMENT:
                                UiStatsLogger.logManageMeasurementSelected();
                                mActivity.startActivity(
                                        new Intent(mActivity, MeasurementActivity.class));
                                break;
                        }
                    } finally {
                        mMainViewModel.uiEventHandled();
                    }
                };
        mMainViewModel.getUiEvents().removeObservers(mActivity);
        mMainViewModel.getUiEvents().observe(mActivity, observer);
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
                        mActivity.getResources().getString(stringId),
                        Locale.getDefault(Locale.Category.FORMAT));
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("count", count);
        return msgFormat.format(arguments);
    }
}
