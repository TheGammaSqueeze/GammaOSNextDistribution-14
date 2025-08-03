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

import com.android.adservices.service.consent.App;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.ui.settings.fragments.AdServicesSettingsAppsFragment;
import com.android.adservices.ui.settings.fragments.AdServicesSettingsBlockedAppsFragment;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableList;

import java.io.IOException;

/**
 * View model for the apps view and blocked apps view of the AdServices Settings App. This view
 * model is responsible for serving apps to the apps view and blocked apps view, and interacting
 * with the {@link ConsentManager} that persists and changes the apps data in a storage.
 */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class BlockedAppsViewModel extends AndroidViewModel {

    private final MutableLiveData<Pair<BlockedAppsViewModelUiEvent, App>> mEventTrigger =
            new MutableLiveData<>();
    private final MutableLiveData<ImmutableList<App>> mBlockedApps;
    private final ConsentManager mConsentManager;

    /** UI event triggered by view model */
    public enum BlockedAppsViewModelUiEvent {
        RESTORE_APP,
    }

    public BlockedAppsViewModel(@NonNull Application application) {
        super(application);

        mConsentManager = ConsentManager.getInstance();
        mBlockedApps = new MutableLiveData<>(getBlockedAppsFromConsentManager());
    }

    @VisibleForTesting
    public BlockedAppsViewModel(@NonNull Application application, ConsentManager consentManager) {
        super(application);

        mConsentManager = consentManager;
        mBlockedApps = new MutableLiveData<>(getBlockedAppsFromConsentManager());
    }

    /**
     * Provides the blocked apps displayed in {@link AdServicesSettingsBlockedAppsFragment}.
     *
     * @return a list of apps that represents the user's blocked interests.
     */
    public LiveData<ImmutableList<App>> getBlockedApps() {
        return mBlockedApps;
    }

    /**
     * Restore the consent for the specified app (i.e. unblock the app).
     *
     * @param app the app to be restored.
     */
    public void restoreAppConsent(App app) throws IOException {
        mConsentManager.restoreConsentForApp(app);
        refresh();
    }

    /**
     * Reads all the data from {@link ConsentManager}.
     *
     * <p>TODO(b/238387560): To be moved to private when is fixed.
     */
    public void refresh() {
        mBlockedApps.postValue(getBlockedAppsFromConsentManager());
    }

    /** Returns an observable but immutable event enum representing a view action on UI. */
    public LiveData<Pair<BlockedAppsViewModelUiEvent, App>> getUiEvents() {
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
     * Triggers the block of the specified app in the list of apps in {@link
     * AdServicesSettingsAppsFragment}.
     *
     * @param app the app to be blocked.
     */
    public void restoreAppConsentButtonClickHandler(App app) {
        mEventTrigger.postValue(new Pair<>(BlockedAppsViewModelUiEvent.RESTORE_APP, app));
    }

    private ImmutableList<App> getBlockedAppsFromConsentManager() {
        return mConsentManager.getAppsWithRevokedConsent();
    }
}
