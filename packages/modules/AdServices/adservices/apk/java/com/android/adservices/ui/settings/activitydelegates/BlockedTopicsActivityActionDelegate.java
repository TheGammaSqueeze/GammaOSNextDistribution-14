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

import android.os.Build;
import android.util.Log;
import android.util.Pair;
import android.view.View;

import androidx.annotation.RequiresApi;
import androidx.lifecycle.Observer;

import com.android.adservices.api.R;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.stats.UiStatsLogger;
import com.android.adservices.ui.settings.DialogFragmentManager;
import com.android.adservices.ui.settings.DialogManager;
import com.android.adservices.ui.settings.activities.BlockedTopicsActivity;
import com.android.adservices.ui.settings.viewadatpors.TopicsListViewAdapter;
import com.android.adservices.ui.settings.viewmodels.BlockedTopicsViewModel;
import com.android.adservices.ui.settings.viewmodels.BlockedTopicsViewModel.BlockedTopicsViewModelUiEvent;

import java.util.function.Function;

/**
 * Delegate class that helps AdServices Settings fragments to respond to all view model/user events.
 */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class BlockedTopicsActivityActionDelegate extends BaseActionDelegate {
    private final BlockedTopicsViewModel mBlockedTopicsViewModel;

    public BlockedTopicsActivityActionDelegate(
            BlockedTopicsActivity blockedTopicsActivity,
            BlockedTopicsViewModel blockedTopicsViewModel) {
        super(blockedTopicsActivity);
        mBlockedTopicsViewModel = blockedTopicsViewModel;
        initWithUx(blockedTopicsActivity, blockedTopicsActivity.getApplicationContext());
        listenToBlockedTopicsViewModelUiEvents();
    }

    @Override
    public void initBeta() {
        mActivity.setTitle(R.string.settingsUI_blocked_topics_title);
        configureSharedElements(/* isGA */ false);
    }

    @Override
    public void initGA() {
        mActivity.setTitle(R.string.settingsUI_blocked_topics_ga_title);
        configureSharedElements(/* isGA */ true);
    }

    @Override
    public void initU18() {}

    @Override
    public void initRvc() {}

    @Override
    public void initGaUxWithPas() {
        initGA();
    }

    private void configureSharedElements(Boolean isGA) {
        // no blocked topics message
        configureElement(
                isGA ? R.id.no_blocked_topics_ga_message : R.id.no_blocked_topics_message,
                mBlockedTopicsViewModel.getBlockedTopics(),
                controls ->
                        list ->
                                controls.setVisibility(
                                        (list.isEmpty() ? View.VISIBLE : View.GONE)));
        // recycler view (topics list)
        Function<Topic, View.OnClickListener> getOnclickListener =
                topic ->
                        view ->
                                mBlockedTopicsViewModel.restoreTopicConsentButtonClickHandler(
                                        topic);
        TopicsListViewAdapter adapter =
                new TopicsListViewAdapter(
                        mActivity,
                        mBlockedTopicsViewModel.getBlockedTopics(),
                        getOnclickListener,
                        true);
        configureRecyclerView(R.id.blocked_topics_list, adapter);
        configureNotifyAdapterDataChange(mBlockedTopicsViewModel.getBlockedTopics(), adapter);
    }

    private void listenToBlockedTopicsViewModelUiEvents() {
        Observer<Pair<BlockedTopicsViewModelUiEvent, Topic>> observer =
                eventTopicPair -> {
                    if (eventTopicPair == null) {
                        return;
                    }
                    BlockedTopicsViewModelUiEvent event = eventTopicPair.first;
                    Topic topic = eventTopicPair.second;
                    if (event == null) {
                        return;
                    }
                    try {
                        if (event == BlockedTopicsViewModelUiEvent.RESTORE_TOPIC) {
                            UiStatsLogger.logUnblockTopicSelected();
                            mBlockedTopicsViewModel.restoreTopicConsent(topic);
                            if (FlagsFactory.getFlags().getUiDialogsFeatureEnabled()) {
                                if (FlagsFactory.getFlags().getUiDialogFragmentEnabled()) {
                                    DialogFragmentManager.showUnblockTopicDialog(mActivity, topic);
                                } else {
                                    DialogManager.showUnblockTopicDialog(mActivity, topic);
                                }
                            }
                        } else {
                            Log.e("AdservicesUI", "Unknown Action for UI Logging");
                        }
                    } finally {
                        mBlockedTopicsViewModel.uiEventHandled();
                    }
                };
        mBlockedTopicsViewModel.getUiEvents().observe(mActivity, observer);
    }
}
