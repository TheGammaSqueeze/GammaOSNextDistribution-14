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
import android.util.Pair;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.lifecycle.Observer;

import com.android.adservices.api.R;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.stats.UiStatsLogger;
import com.android.adservices.ui.settings.DialogFragmentManager;
import com.android.adservices.ui.settings.DialogManager;
import com.android.adservices.ui.settings.activities.BlockedTopicsActivity;
import com.android.adservices.ui.settings.activities.TopicsActivity;
import com.android.adservices.ui.settings.fragments.AdServicesSettingsTopicsFragment;
import com.android.adservices.ui.settings.viewmodels.TopicsViewModel;
import com.android.adservices.ui.settings.viewmodels.TopicsViewModel.TopicsViewModelUiEvent;
import com.android.settingslib.widget.MainSwitchBar;

/**
 * Delegate class that helps AdServices Settings fragments to respond to all view model/user events.
 */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class TopicsActionDelegate {
    private final TopicsActivity mTopicsActivity;
    private final TopicsViewModel mTopicsViewModel;

    public TopicsActionDelegate(TopicsActivity topicsActivity, TopicsViewModel topicsViewModel) {
        mTopicsActivity = topicsActivity;
        mTopicsViewModel = topicsViewModel;
        listenToTopicsViewModelUiEvents();
    }

    private void listenToTopicsViewModelUiEvents() {
        Observer<Pair<TopicsViewModelUiEvent, Topic>> observer =
                eventTopicPair -> {
                    if (eventTopicPair == null) {
                        return;
                    }
                    TopicsViewModelUiEvent event = eventTopicPair.first;
                    Topic topic = eventTopicPair.second;
                    if (event == null) {
                        return;
                    }
                    try {
                        switch (event) {
                            case SWITCH_ON_TOPICS:
                                if (FlagsFactory.getFlags().getToggleSpeedBumpEnabled()) {
                                    DialogFragmentManager.showOptInTopicsDialog(mTopicsActivity);
                                }
                                mTopicsViewModel.setTopicsConsent(true);
                                mTopicsViewModel.refresh();
                                break;
                            case SWITCH_OFF_TOPICS:
                                if (FlagsFactory.getFlags().getToggleSpeedBumpEnabled()) {
                                    DialogFragmentManager.showOptOutTopicsDialog(
                                            mTopicsActivity, mTopicsViewModel);
                                } else {
                                    mTopicsViewModel.setTopicsConsent(false);
                                    mTopicsViewModel.refresh();
                                }
                                break;
                            case BLOCK_TOPIC:
                                UiStatsLogger.logBlockTopicSelected();
                                if (FlagsFactory.getFlags().getUiDialogsFeatureEnabled()) {
                                    if (FlagsFactory.getFlags().getUiDialogFragmentEnabled()) {
                                        DialogFragmentManager.showBlockTopicDialog(
                                                mTopicsActivity, mTopicsViewModel, topic);
                                    } else {
                                        DialogManager.showBlockTopicDialog(
                                                mTopicsActivity, mTopicsViewModel, topic);
                                    }
                                } else {
                                    mTopicsViewModel.revokeTopicConsent(topic);
                                }
                                break;
                            case RESET_TOPICS:
                                UiStatsLogger.logResetTopicSelected();
                                if (FlagsFactory.getFlags().getUiDialogsFeatureEnabled()) {
                                    if (FlagsFactory.getFlags().getUiDialogFragmentEnabled()) {
                                        DialogFragmentManager.showResetTopicDialog(
                                                mTopicsActivity, mTopicsViewModel);
                                    } else {
                                        DialogManager.showResetTopicDialog(
                                                mTopicsActivity, mTopicsViewModel);
                                    }
                                } else {
                                    mTopicsViewModel.resetTopics();
                                }
                                break;
                            case DISPLAY_BLOCKED_TOPICS_FRAGMENT:
                                Intent intent =
                                        new Intent(mTopicsActivity, BlockedTopicsActivity.class);
                                mTopicsActivity.startActivity(intent);
                                break;
                        }
                    } finally {
                        mTopicsViewModel.uiEventHandled();
                    }
                };
        mTopicsViewModel.getUiEvents().observe(mTopicsActivity, observer);
    }

    /**
     * Configure all UI elements (except topics list) in {@link AdServicesSettingsTopicsFragment} to
     * handle user actions.
     */
    public void initTopicsFragment(AdServicesSettingsTopicsFragment fragment) {
        if (FlagsFactory.getFlags().getGaUxFeatureEnabled()) {
            mTopicsActivity.setTitle(R.string.settingsUI_topics_ga_title);

            configureTopicsConsentSwitch(fragment);

            setGaUxLayoutVisibilities(View.VISIBLE);
            setBetaLayoutVisibilities(View.GONE);
            setGaUxTopicsViewText();
        } else {
            mTopicsActivity.setTitle(R.string.settingsUI_topics_view_title);

            setGaUxLayoutVisibilities(View.GONE);
            setBetaLayoutVisibilities(View.VISIBLE);
            setBetaTopicsViewText();
        }
        configureBlockedTopicsFragmentButton(fragment);
        configureResetTopicsButton(fragment);
    }

    private void setGaUxTopicsViewText() {
        ((TextView) mTopicsActivity.findViewById(R.id.blocked_topics_button_child))
                .setText(R.string.settingsUI_blocked_topics_ga_title);
        ((TextView) mTopicsActivity.findViewById(R.id.reset_topics_button_child))
                .setText(R.string.settingsUI_reset_topics_ga_title);
        ((TextView) mTopicsActivity.findViewById(R.id.no_topics_state))
                .setText(R.string.settingsUI_topics_view_no_topics_ga_text);
        ((TextView) mTopicsActivity.findViewById(R.id.no_topics_state))
                .setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void setBetaTopicsViewText() {
        ((TextView) mTopicsActivity.findViewById(R.id.blocked_topics_button_child))
                .setText(R.string.settingsUI_blocked_topics_title);
        ((TextView) mTopicsActivity.findViewById(R.id.reset_topics_button_child))
                .setText(R.string.settingsUI_reset_topics_title);
        ((TextView) mTopicsActivity.findViewById(R.id.no_topics_state))
                .setText(R.string.settingsUI_topics_view_no_topics_text);
    }

    private void setBetaLayoutVisibilities(int visibility) {
        mTopicsActivity.findViewById(R.id.topics_introduction).setVisibility(visibility);
        mTopicsActivity.findViewById(R.id.topics_view_footer).setVisibility(visibility);
    }

    private void setGaUxLayoutVisibilities(int visibility) {
        mTopicsActivity.findViewById(R.id.topics_ga_introduction).setVisibility(visibility);
        mTopicsActivity.findViewById(R.id.topics_view_ga_footer).setVisibility(visibility);
    }

    private void configureTopicsConsentSwitch(AdServicesSettingsTopicsFragment fragment) {
        MainSwitchBar topicsSwitchBar = mTopicsActivity.findViewById(R.id.topics_switch_bar);
        topicsSwitchBar.setVisibility(View.VISIBLE);

        mTopicsViewModel.getTopicsConsent().observe(fragment, topicsSwitchBar::setChecked);
        topicsSwitchBar.setOnClickListener(
                switchBar -> mTopicsViewModel.consentSwitchClickHandler((MainSwitchBar) switchBar));
    }

    private void configureBlockedTopicsFragmentButton(AdServicesSettingsTopicsFragment fragment) {
        View blockedTopicsButton = fragment.requireView().findViewById(R.id.blocked_topics_button);
        View blockedTopicsWhenEmptyListButton =
                fragment.requireView().findViewById(R.id.blocked_topics_when_empty_state_button);
        blockedTopicsButton.setOnClickListener(
                view -> mTopicsViewModel.blockedTopicsFragmentButtonClickHandler());
        blockedTopicsWhenEmptyListButton.setOnClickListener(
                view -> mTopicsViewModel.blockedTopicsFragmentButtonClickHandler());
    }

    private void configureResetTopicsButton(AdServicesSettingsTopicsFragment fragment) {
        View resetTopicsButton = fragment.requireView().findViewById(R.id.reset_topics_button);
        resetTopicsButton.setOnClickListener(
                view -> mTopicsViewModel.resetTopicsButtonClickHandler());
    }
}
