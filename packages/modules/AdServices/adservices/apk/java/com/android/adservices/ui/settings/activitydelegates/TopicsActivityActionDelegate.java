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
import android.os.Build;
import android.util.Pair;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

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
import com.android.adservices.ui.settings.viewadatpors.TopicsListViewAdapter;
import com.android.adservices.ui.settings.viewmodels.TopicsViewModel;
import com.android.adservices.ui.settings.viewmodels.TopicsViewModel.TopicsViewModelUiEvent;
import com.android.settingslib.widget.MainSwitchBar;

import com.google.common.collect.ImmutableList;

import java.util.function.Function;

/**
 * Delegate class that helps AdServices Settings fragments to respond to all view model/user events.
 */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class TopicsActivityActionDelegate extends BaseActionDelegate {
    private static final int[] BetaOnlyElements =
            new int[] {R.id.topics_introduction, R.id.topics_view_footer};
    private static final int[] GaOnlyElements =
            new int[] {R.id.topics_ga_introduction, R.id.topics_view_ga_footer};

    private final TopicsViewModel mTopicsViewModel;

    public TopicsActivityActionDelegate(
            TopicsActivity topicsActivity, TopicsViewModel topicsViewModel) {
        super(topicsActivity);
        mTopicsViewModel = topicsViewModel;
        initWithUx(topicsActivity, topicsActivity.getApplicationContext());
        listenToTopicsViewModelUiEvents();
    }

    @Override
    public void initBeta() {
        // hidden elements
        hideElements(GaOnlyElements);
        // show elements
        showElements(BetaOnlyElements);

        // set title
        mActivity.setTitle(R.string.settingsUI_topics_ga_title);
        // set text
        configureElement(
                R.id.blocked_topics_button_child, R.string.settingsUI_blocked_topics_title);
        configureElement(R.id.reset_topics_button_child, R.string.settingsUI_reset_topics_title);
        configureElement(R.id.no_topics_state, R.string.settingsUI_topics_view_no_topics_text);
        // empty state blocked topics button
        Function<View, Observer<ImmutableList<Topic>>> observerProvider = controls -> list -> {
            if (list.isEmpty()) {
                controls.setEnabled(false);
                controls.setAlpha(mActivity.getResources().getFloat(R.dimen.disabled_button_alpha));
                ((Button) controls).setText(R.string.settingsUI_topics_view_no_blocked_topics_text);
            } else {
                controls.setEnabled(true);
                controls.setAlpha(mActivity.getResources().getFloat(R.dimen.enabled_button_alpha));
                ((Button) controls).setText(R.string.settingsUI_blocked_topics_title);
            }
        };
        configureElement(
                R.id.blocked_topics_when_empty_state_button,
                mTopicsViewModel.getBlockedTopics(),
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
        mActivity.setTitle(R.string.settingsUI_topics_ga_title);
        // consent switch
        configureElement(
                R.id.topics_switch_bar,
                switchBar -> mTopicsViewModel.consentSwitchClickHandler((MainSwitchBar) switchBar),
                mTopicsViewModel.getTopicsConsent(),
                switchBar -> ((MainSwitchBar) switchBar)::setChecked);
        // text
        configureElement(R.id.reset_topics_button_child, R.string.settingsUI_reset_topics_ga_title);
        configureElement(R.id.no_topics_state, R.string.settingsUI_topics_view_no_topics_ga_text);
        configureLink(R.id.no_topics_state);
        // empty state blocked topics button
        Function<View, Observer<ImmutableList<Topic>>> observerProvider =
                controls ->
                        list -> {
                            if (list.isEmpty()) {
                                controls.setEnabled(false);
                                controls.setAlpha(
                                        mActivity
                                                .getResources()
                                                .getFloat(R.dimen.disabled_button_alpha));
                                ((Button) controls)
                                        .setText(R.string.settingsUI_no_blocked_topics_ga_text);
                            } else {
                                controls.setEnabled(true);
                                controls.setAlpha(
                                        mActivity
                                                .getResources()
                                                .getFloat(R.dimen.enabled_button_alpha));
                                ((Button) controls)
                                        .setText(R.string.settingsUI_view_blocked_topics_title);
                            }
                        };
        configureElement(
                R.id.blocked_topics_when_empty_state_button,
                mTopicsViewModel.getBlockedTopics(),
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
        configureElement(
                R.id.topics_view_ga_footer_text, R.string.settingsUI_pas_topics_view_footer);
        configureLink(R.id.topics_view_ga_footer_text);
    }

    private void configureSharedElements() {
        // recycler view (topics list)
        Function<Topic, OnClickListener> getOnclickListener =
                topic -> view -> mTopicsViewModel.revokeTopicConsentButtonClickHandler(topic);
        TopicsListViewAdapter adapter =
                new TopicsListViewAdapter(
                        mActivity, mTopicsViewModel.getTopics(), getOnclickListener, false);
        configureRecyclerView(R.id.topics_list, adapter);
        // blocked topics and reset button
        configureElement(
                R.id.empty_topics_hidden_section,
                mTopicsViewModel.getTopics(),
                controls ->
                        list ->
                                controls.setVisibility(
                                        (list.isEmpty() ? View.GONE : View.VISIBLE)));
        // no topics message
        configureElement(
                R.id.no_topics_message,
                mTopicsViewModel.getTopics(),
                controls ->
                        list ->
                                controls.setVisibility(
                                        (list.isEmpty() ? View.VISIBLE : View.GONE)));
        // blocked topics button
        configureElement(
                R.id.blocked_topics_button,
                button -> mTopicsViewModel.blockedTopicsFragmentButtonClickHandler());
        configureElement(
                R.id.blocked_topics_when_empty_state_button,
                button -> mTopicsViewModel.blockedTopicsFragmentButtonClickHandler());
        // reset topics button
        configureElement(
                R.id.reset_topics_button,
                button -> mTopicsViewModel.resetTopicsButtonClickHandler());

        configureNotifyAdapterDataChange(mTopicsViewModel.getTopics(), adapter);
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
                                    DialogFragmentManager.showOptInTopicsDialog(mActivity);
                                }
                                mTopicsViewModel.setTopicsConsent(true);
                                mTopicsViewModel.refresh();
                                break;
                            case SWITCH_OFF_TOPICS:
                                if (FlagsFactory.getFlags().getToggleSpeedBumpEnabled()) {
                                    DialogFragmentManager.showOptOutTopicsDialog(
                                            mActivity, mTopicsViewModel);
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
                                                mActivity, mTopicsViewModel, topic);
                                    } else {
                                        DialogManager.showBlockTopicDialog(
                                                mActivity, mTopicsViewModel, topic);
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
                                                mActivity, mTopicsViewModel);
                                    } else {
                                        DialogManager.showResetTopicDialog(
                                                mActivity, mTopicsViewModel);
                                    }
                                } else {
                                    mTopicsViewModel.resetTopics();
                                }
                                break;
                            case DISPLAY_BLOCKED_TOPICS_FRAGMENT:
                                Intent intent = new Intent(mActivity, BlockedTopicsActivity.class);
                                mActivity.startActivity(intent);
                                break;
                        }
                    } finally {
                        mTopicsViewModel.uiEventHandled();
                    }
                };
        mTopicsViewModel.getUiEvents().observe(mActivity, observer);
    }
}
