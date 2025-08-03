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
package com.android.adservices.ui.settings.activitydelegates;

import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

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
import com.android.adservices.ui.settings.viewadatpors.AppsListViewAdapter;
import com.android.adservices.ui.settings.viewmodels.AppsViewModel;
import com.android.adservices.ui.settings.viewmodels.AppsViewModel.AppsViewModelUiEvent;
import com.android.settingslib.widget.MainSwitchBar;

import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.util.function.Function;

/**
 * Delegate class that helps AdServices Settings fragments to respond to all view model/user events.
 */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class AppsActivityActionDelegate extends BaseActionDelegate {

    private static final int[] BetaOnlyElements =
            new int[] {R.id.apps_introduction, R.id.apps_view_footer};
    private static final int[] GaOnlyElements =
            new int[] {R.id.apps_ga_introduction, R.id.apps_view_ga_footer};
    private final AppsViewModel mAppsViewModel;

    public AppsActivityActionDelegate(AppsActivity appsActivity, AppsViewModel appsViewModel) {
        super(appsActivity);
        mAppsViewModel = appsViewModel;
        initWithUx(appsActivity, appsActivity.getApplicationContext());
        listenToAppsViewModelUiEvents();
    }

    @Override
    public void initBeta() {
        // hidden elements
        hideElements(GaOnlyElements);
        // show elements
        showElements(BetaOnlyElements);

        // set title
        mActivity.setTitle(R.string.settingsUI_apps_view_title);
        // reset button
        configureElement(R.id.reset_apps_button_child, R.string.settingsUI_reset_apps_title);
        // zero-state text
        configureElement(R.id.no_apps_state, R.string.settingsUI_apps_view_no_apps_text);
        // empty state blocked apps button
        Function<View, Observer<ImmutableList<App>>> observerProvider =
                controls ->
                        list -> {
                            if (list.isEmpty()) {
                                controls.setEnabled(false);
                                controls.setAlpha(
                                        mActivity
                                                .getResources()
                                                .getFloat(R.dimen.disabled_button_alpha));
                                ((Button) controls)
                                        .setText(
                                                R.string.settingsUI_apps_view_no_blocked_apps_text);
                            } else {
                                controls.setEnabled(true);
                                controls.setAlpha(
                                        mActivity
                                                .getResources()
                                                .getFloat(R.dimen.enabled_button_alpha));
                                ((Button) controls).setText(R.string.settingsUI_blocked_apps_title);
                            }
                        };
        configureElement(
                R.id.blocked_apps_when_empty_state_button,
                mAppsViewModel.getBlockedApps(),
                observerProvider);
        // buttons
        configureSharedElements();
    }

    @Override
    public void initGA() {
        // hidden elements
        hideElements(BetaOnlyElements);
        // show elements
        showElements(GaOnlyElements);

        // set title
        mActivity.setTitle(R.string.settingsUI_apps_ga_title);
        // consent switch
        configureElement(
                R.id.apps_switch_bar,
                switchBar -> mAppsViewModel.consentSwitchClickHandler((MainSwitchBar) switchBar),
                mAppsViewModel.getAppsConsent(),
                switchBar -> ((MainSwitchBar) switchBar)::setChecked);
        // text
        configureElement(R.id.reset_apps_button_child, R.string.settingsUI_reset_apps_ga_title);
        configureElement(R.id.no_apps_state, R.string.settingsUI_apps_view_no_apps_ga_text);
        configureLink(R.id.no_apps_state);
        // empty state blocked apps button
        Function<View, Observer<ImmutableList<App>>> observerProvider =
                controls ->
                        list -> {
                            if (list.isEmpty()) {
                                controls.setEnabled(false);
                                controls.setAlpha(
                                        mActivity
                                                .getResources()
                                                .getFloat(R.dimen.disabled_button_alpha));
                                ((Button) controls)
                                        .setText(R.string.settingsUI_no_blocked_apps_ga_text);
                            } else {
                                controls.setEnabled(true);
                                controls.setAlpha(
                                        mActivity
                                                .getResources()
                                                .getFloat(R.dimen.enabled_button_alpha));
                                ((Button) controls)
                                        .setText(R.string.settingsUI_view_blocked_apps_title);
                            }
                        };
        configureElement(
                R.id.blocked_apps_when_empty_state_button,
                mAppsViewModel.getBlockedApps(),
                observerProvider);
        // buttons
        configureSharedElements();
    }

    @Override
    public void initU18() {}

    @Override
    public void initRvc() {}

    @Override
    public void initGaUxWithPas() {
        initGA();
        configureElement(R.id.apps_ga_introduction, R.string.settingsUI_pas_apps_view_body_text);
        configureElement(R.id.apps_view_ga_footer_text, R.string.settingsUI_pas_apps_view_footer);
        configureLink(R.id.apps_view_ga_footer_text);
    }

    private void configureSharedElements() {
        // recycler view (apps list)
        Function<App, OnClickListener> getOnclickListener =
                app -> view -> mAppsViewModel.revokeAppConsentButtonClickHandler(app);
        AppsListViewAdapter adapter =
                new AppsListViewAdapter(
                        mActivity, mAppsViewModel.getApps(), getOnclickListener, false);
        configureRecyclerView(R.id.apps_list, adapter);
        // blocked apps and reset button
        configureElement(
                R.id.empty_apps_hidden_section,
                mAppsViewModel.getApps(),
                controls ->
                        list ->
                                controls.setVisibility(
                                        (list.isEmpty() ? View.GONE : View.VISIBLE)));
        // no apps message
        configureElement(
                R.id.no_apps_message,
                mAppsViewModel.getApps(),
                controls ->
                        list ->
                                controls.setVisibility(
                                        (list.isEmpty() ? View.VISIBLE : View.GONE)));
        // blocked apps button
        configureElement(
                R.id.blocked_apps_button,
                button -> mAppsViewModel.blockedAppsFragmentButtonClickHandler());
        configureElement(
                R.id.blocked_apps_when_empty_state_button,
                button -> mAppsViewModel.blockedAppsFragmentButtonClickHandler());
        // reset apps button
        configureElement(
                R.id.reset_apps_button, button -> mAppsViewModel.resetAppsButtonClickHandler());

        configureNotifyAdapterDataChange(mAppsViewModel.getApps(), adapter);
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
                                    DialogFragmentManager.showOptInAppsDialog(mActivity);
                                }
                                mAppsViewModel.setAppsConsent(true);
                                mAppsViewModel.refresh();
                                break;
                            case SWITCH_OFF_APPS:
                                if (FlagsFactory.getFlags().getToggleSpeedBumpEnabled()) {
                                    DialogFragmentManager.showOptOutAppsDialog(
                                            mActivity, mAppsViewModel);
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
                                                mActivity, mAppsViewModel, app);
                                    } else {
                                        DialogManager.showBlockAppDialog(
                                                mActivity, mAppsViewModel, app);
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
                                                mActivity, mAppsViewModel);
                                    } else {
                                        DialogManager.showResetAppDialog(mActivity, mAppsViewModel);
                                    }
                                } else {
                                    mAppsViewModel.resetApps();
                                }
                                break;
                            case DISPLAY_BLOCKED_APPS_FRAGMENT:
                                Intent intent = new Intent(mActivity, BlockedAppsActivity.class);
                                mActivity.startActivity(intent);
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
        mAppsViewModel.getUiEvents().observe(mActivity, observer);
    }
}
