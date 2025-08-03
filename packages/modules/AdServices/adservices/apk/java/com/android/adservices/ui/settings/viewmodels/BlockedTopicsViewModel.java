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
package com.android.adservices.ui.settings.viewmodels;

import android.app.Application;
import android.os.Build;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.android.adservices.data.topics.Topic;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.ui.settings.fragments.AdServicesSettingsBlockedTopicsFragment;
import com.android.adservices.ui.settings.fragments.AdServicesSettingsTopicsFragment;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableList;

/**
 * View model for the topics view and blocked topics view of the AdServices Settings App. This view
 * model is responsible for serving topics to the topics view and blocked topics view, and
 * interacting with the {@link ConsentManager} that persists and changes the topics data in a
 * storage.
 */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class BlockedTopicsViewModel extends AndroidViewModel {

    private final MutableLiveData<Pair<BlockedTopicsViewModelUiEvent, Topic>> mEventTrigger =
            new MutableLiveData<>();
    private final MutableLiveData<ImmutableList<Topic>> mBlockedTopics;
    private final ConsentManager mConsentManager;

    /** UI event triggered by view model */
    public enum BlockedTopicsViewModelUiEvent {
        RESTORE_TOPIC,
    }

    public BlockedTopicsViewModel(@NonNull Application application) {
        super(application);

        mConsentManager = ConsentManager.getInstance();
        mBlockedTopics = new MutableLiveData<>(getBlockedTopicsFromConsentManager());
    }

    @VisibleForTesting
    public BlockedTopicsViewModel(@NonNull Application application, ConsentManager consentManager) {
        super(application);

        mConsentManager = consentManager;
        mBlockedTopics = new MutableLiveData<>(getBlockedTopicsFromConsentManager());
    }

    /**
     * Provides the blocked topics displayed in {@link AdServicesSettingsBlockedTopicsFragment}.
     *
     * @return a list of topics that represents the user's blocked interests.
     */
    public LiveData<ImmutableList<Topic>> getBlockedTopics() {
        return mBlockedTopics;
    }

    /**
     * Restore the consent for the specified topic (i.e. unblock the topic).
     *
     * @param topic the topic to be restored.
     */
    public void restoreTopicConsent(Topic topic) {
        mConsentManager.restoreConsentForTopic(topic);
        refresh();
    }

    /**
     * Reads all the data from {@link ConsentManager}.
     *
     * <p>TODO(b/238387560): To be moved to private when is fixed.
     */
    public void refresh() {
        mBlockedTopics.postValue(getBlockedTopicsFromConsentManager());
    }

    /** Returns an observable but immutable event enum representing a view action on UI. */
    public LiveData<Pair<BlockedTopicsViewModelUiEvent, Topic>> getUiEvents() {
        return mEventTrigger;
    }

    /**
     * Sets the UI Event as handled so the action will not be handled again if activity is
     * recreated.
     */
    public void uiEventHandled() {
        mEventTrigger.postValue(new Pair<>(null, null));
    }

    /**
     * Triggers the block of the specified topic in the list of topics in {@link
     * AdServicesSettingsTopicsFragment}.
     *
     * @param topic the topic to be blocked.
     */
    public void restoreTopicConsentButtonClickHandler(Topic topic) {
        mEventTrigger.postValue(new Pair<>(BlockedTopicsViewModelUiEvent.RESTORE_TOPIC, topic));
    }

    private ImmutableList<Topic> getBlockedTopicsFromConsentManager() {
        return mConsentManager.getTopicsWithRevokedConsent();
    }
}
