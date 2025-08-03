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

import android.os.Build;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.RequiresApi;
import androidx.lifecycle.Observer;

import com.android.adservices.api.R;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.consent.App;
import com.android.adservices.service.stats.UiStatsLogger;
import com.android.adservices.ui.settings.DialogFragmentManager;
import com.android.adservices.ui.settings.DialogManager;
import com.android.adservices.ui.settings.activities.BlockedAppsActivity;
import com.android.adservices.ui.settings.fragments.AdServicesSettingsBlockedAppsFragment;
import com.android.adservices.ui.settings.viewmodels.BlockedAppsViewModel;
import com.android.adservices.ui.settings.viewmodels.BlockedAppsViewModel.BlockedAppsViewModelUiEvent;

import java.io.IOException;

/**
 * Delegate class that helps AdServices Settings fragments to respond to all view model/user events.
 */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class BlockedAppsActionDelegate {
    private final BlockedAppsActivity mBlockedAppsActivity;
    private final BlockedAppsViewModel mBlockedAppsViewModel;

    public BlockedAppsActionDelegate(
            BlockedAppsActivity blockedAppsActivity, BlockedAppsViewModel blockedAppsViewModel) {
        mBlockedAppsActivity = blockedAppsActivity;
        mBlockedAppsViewModel = blockedAppsViewModel;
        listenToBlockedAppsViewModelUiEvents();
    }

    private void listenToBlockedAppsViewModelUiEvents() {
        Observer<Pair<BlockedAppsViewModelUiEvent, App>> observer =
                eventAppPair -> {
                    if (eventAppPair == null) {
                        return;
                    }
                    BlockedAppsViewModelUiEvent event = eventAppPair.first;
                    App app = eventAppPair.second;
                    if (event == null) {
                        return;
                    }
                    try {
                        if (event == BlockedAppsViewModelUiEvent.RESTORE_APP) {
                            UiStatsLogger.logUnblockAppSelected();
                            mBlockedAppsViewModel.restoreAppConsent(app);
                            if (FlagsFactory.getFlags().getUiDialogsFeatureEnabled()) {
                                if (FlagsFactory.getFlags().getUiDialogFragmentEnabled()) {
                                    DialogFragmentManager.showUnblockAppDialog(
                                            mBlockedAppsActivity, app);
                                } else {
                                    DialogManager.showUnblockAppDialog(mBlockedAppsActivity, app);
                                }
                            }
                        } else {
                            Log.e("AdservicesUI", "Unknown Action for UI Logging");
                        }
                    } catch (IOException e) {
                        Log.e(
                                "AdServicesUI",
                                "Error while processing AppsViewModelUiEvent " + event + ":" + e);
                    } finally {
                        mBlockedAppsViewModel.uiEventHandled();
                    }
                };
        mBlockedAppsViewModel.getUiEvents().observe(mBlockedAppsActivity, observer);
    }

    /**
     * Configure all UI elements (except blocked apps list) in {@link
     * AdServicesSettingsBlockedAppsFragment} to handle user actions.
     */
    public void initBlockedAppsFragment() {
        if (FlagsFactory.getFlags().getGaUxFeatureEnabled()) {
            mBlockedAppsActivity.setTitle(R.string.settingsUI_blocked_apps_ga_title);
        } else {
            mBlockedAppsActivity.setTitle(R.string.settingsUI_blocked_apps_title);
        }
    }
}
