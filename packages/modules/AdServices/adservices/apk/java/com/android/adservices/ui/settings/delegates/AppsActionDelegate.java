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
import android.os.Build;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.lifecycle.Observer;

import com.android.adservices.api.R;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.consent.App;
import com.android.adservices.service.stats.UiStatsLogger;
import com.android.adservices.ui.settings.DialogFragmentManager;
import com.android.adservices.ui.settings.DialogManager;
import com.android.adservices.ui.settings.activities.AppsActivity;
import com.android.adservices.ui.settings.activities.BlockedAppsActivity;
import com.android.adservices.ui.settings.fragments.AdServicesSettingsAppsFragment;
import com.android.adservices.ui.settings.viewmodels.AppsViewModel;
import com.android.adservices.ui.settings.viewmodels.AppsViewModel.AppsViewModelUiEvent;
import com.android.settingslib.widget.MainSwitchBar;

import java.io.IOException;

/**
 * Delegate class that helps AdServices Settings fragments to respond to all view model/user events.
 */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class AppsActionDelegate {
    private final AppsActivity mAppsActivity;
    private final AppsViewModel mAppsViewModel;

    public AppsActionDelegate(AppsActivity appsActivity, AppsViewModel appsViewModel) {
        mAppsActivity = appsActivity;
        mAppsViewModel = appsViewModel;
        listenToAppsViewModelUiEvents();
    }

    private void listenToAppsViewModelUiEvents() {
        Observer<Pair<AppsViewModelUiEvent, App>> observer =
                eventAppPair -> {
                    if (eventAppPair == null) {
                        return;
                    }
                    AppsViewModelUiEvent event = eventAppPair.first;
                    App app = eventAppPair.second;
                    if (event == null) {
                        return;
                    }
                    try {
                        switch (event) {
                            case SWITCH_ON_APPS:
                                if (FlagsFactory.getFlags().getToggleSpeedBumpEnabled()) {
                                    DialogFragmentManager.showOptInAppsDialog(mAppsActivity);
                                }
                                mAppsViewModel.setAppsConsent(true);
                                mAppsViewModel.refresh();
                                break;
                            case SWITCH_OFF_APPS:
                                if (FlagsFactory.getFlags().getToggleSpeedBumpEnabled()) {
                                    DialogFragmentManager.showOptOutAppsDialog(
                                            mAppsActivity, mAppsViewModel);
                                } else {
                                    mAppsViewModel.setAppsConsent(false);
                                    mAppsViewModel.refresh();
                                }

                                break;
                            case BLOCK_APP:
                                UiStatsLogger.logBlockAppSelected();
                                if (FlagsFactory.getFlags().getUiDialogsFeatureEnabled()) {
                                    if (FlagsFactory.getFlags().getUiDialogFragmentEnabled()) {
                                        DialogFragmentManager.showBlockAppDialog(
                                                mAppsActivity, mAppsViewModel, app);
                                    } else {
                                        DialogManager.showBlockAppDialog(
                                                mAppsActivity, mAppsViewModel, app);
                                    }
                                } else {
                                    mAppsViewModel.revokeAppConsent(app);
                                }
                                break;
                            case RESET_APPS:
                                UiStatsLogger.logResetAppSelected();
                                if (FlagsFactory.getFlags().getUiDialogsFeatureEnabled()) {
                                    if (FlagsFactory.getFlags().getUiDialogFragmentEnabled()) {
                                        DialogFragmentManager.showResetAppDialog(
                                                mAppsActivity, mAppsViewModel);
                                    } else {
                                        DialogManager.showResetAppDialog(
                                                mAppsActivity, mAppsViewModel);
                                    }
                                } else {
                                    mAppsViewModel.resetApps();
                                }
                                break;
                            case DISPLAY_BLOCKED_APPS_FRAGMENT:
                                Intent intent =
                                        new Intent(mAppsActivity, BlockedAppsActivity.class);
                                mAppsActivity.startActivity(intent);
                                break;
                            default:
                                Log.e("AdservicesUI", "Unknown Action for UI Logging");
                        }
                    } catch (IOException e) {
                        Log.e(
                                "AdServicesUI",
                                "Error while processing AppsViewModelUiEvent " + event + ":" + e);
                    } finally {
                        mAppsViewModel.uiEventHandled();
                    }
                };
        mAppsViewModel.getUiEvents().observe(mAppsActivity, observer);
    }

    /**
     * Configure all UI elements (except apps list) in {@link AdServicesSettingsAppsFragment} to
     * handle user actions.
     */
    public void initAppsFragment(AdServicesSettingsAppsFragment fragment) {
        if (FlagsFactory.getFlags().getGaUxFeatureEnabled()) {
            mAppsActivity.setTitle(R.string.settingsUI_apps_ga_title);

            configureAppsConsentSwitch(fragment);
            setGaUxLayoutVisibilities(View.VISIBLE);
            setBetaLayoutVisibilities(View.GONE);

            setGaUxAppsViewText();
        } else {
            mAppsActivity.setTitle(R.string.settingsUI_apps_view_title);

            setGaUxLayoutVisibilities(View.GONE);
            setBetaLayoutVisibilities(View.VISIBLE);

            setBetaAppsViewText();
        }
        configureBlockedAppsFragmentButton(fragment);
        configureResetAppsButton(fragment);
    }

    private void setGaUxAppsViewText() {
        ((TextView) mAppsActivity.findViewById(R.id.reset_apps_button_child))
                .setText(R.string.settingsUI_reset_apps_ga_title);
        ((TextView) mAppsActivity.findViewById(R.id.no_apps_state))
                .setText(R.string.settingsUI_apps_view_no_apps_ga_text);
        ((TextView) mAppsActivity.findViewById(R.id.no_apps_state))
                .setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void setBetaAppsViewText() {
        ((TextView) mAppsActivity.findViewById(R.id.reset_apps_button_child))
                .setText(R.string.settingsUI_reset_apps_title);
        ((TextView) mAppsActivity.findViewById(R.id.no_apps_state))
                .setText(R.string.settingsUI_apps_view_no_apps_text);
    }

    private void setBetaLayoutVisibilities(int visibility) {
        mAppsActivity.findViewById(R.id.apps_introduction).setVisibility(visibility);
        mAppsActivity.findViewById(R.id.apps_view_footer).setVisibility(visibility);
    }

    private void setGaUxLayoutVisibilities(int visibility) {
        mAppsActivity.findViewById(R.id.apps_ga_introduction).setVisibility(visibility);
        mAppsActivity.findViewById(R.id.apps_view_ga_footer).setVisibility(visibility);
    }

    private void configureAppsConsentSwitch(AdServicesSettingsAppsFragment fragment) {
        MainSwitchBar appsSwitchBar = mAppsActivity.findViewById(R.id.apps_switch_bar);
        appsSwitchBar.setVisibility(View.VISIBLE);

        mAppsViewModel.getAppsConsent().observe(fragment, appsSwitchBar::setChecked);
        appsSwitchBar.setOnClickListener(
                switchBar -> mAppsViewModel.consentSwitchClickHandler((MainSwitchBar) switchBar));
    }

    private void configureBlockedAppsFragmentButton(AdServicesSettingsAppsFragment fragment) {
        View blockedAppsButton = fragment.requireView().findViewById(R.id.blocked_apps_button);
        View blockedAppsWhenEmptyListButton =
                fragment.requireView().findViewById(R.id.blocked_apps_when_empty_state_button);

        blockedAppsButton.setOnClickListener(
                view -> {
                    mAppsViewModel.blockedAppsFragmentButtonClickHandler();
                });
        blockedAppsWhenEmptyListButton.setOnClickListener(
                view -> {
                    mAppsViewModel.blockedAppsFragmentButtonClickHandler();
                });
    }

    private void configureResetAppsButton(AdServicesSettingsAppsFragment fragment) {
        View resetAppsButton = fragment.requireView().findViewById(R.id.reset_apps_button);

        resetAppsButton.setOnClickListener(view -> mAppsViewModel.resetAppsButtonClickHandler());
    }
}
