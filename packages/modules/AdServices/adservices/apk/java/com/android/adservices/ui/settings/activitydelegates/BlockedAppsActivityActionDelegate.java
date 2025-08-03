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
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.consent.App;
import com.android.adservices.service.stats.UiStatsLogger;
import com.android.adservices.ui.settings.DialogFragmentManager;
import com.android.adservices.ui.settings.DialogManager;
import com.android.adservices.ui.settings.activities.BlockedAppsActivity;
import com.android.adservices.ui.settings.viewadatpors.AppsListViewAdapter;
import com.android.adservices.ui.settings.viewmodels.BlockedAppsViewModel;
import com.android.adservices.ui.settings.viewmodels.BlockedAppsViewModel.BlockedAppsViewModelUiEvent;

import java.io.IOException;
import java.util.function.Function;

/**
 * Delegate class that helps AdServices Settings fragments to respond to all view model/user events.
 */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class BlockedAppsActivityActionDelegate extends BaseActionDelegate {
    private final BlockedAppsViewModel mBlockedAppsViewModel;

    public BlockedAppsActivityActionDelegate(
            BlockedAppsActivity blockedAppsActivity, BlockedAppsViewModel blockedAppsViewModel) {
        super(blockedAppsActivity);
        mBlockedAppsViewModel = blockedAppsViewModel;
        initWithUx(blockedAppsActivity, blockedAppsActivity.getApplicationContext());
        listenToBlockedAppsViewModelUiEvents();
    }

    @Override
    public void initBeta() {
        mActivity.setTitle(R.string.settingsUI_blocked_apps_title);
        configureSharedElements(/* isGA */ false);
    }

    @Override
    public void initGA() {
        mActivity.setTitle(R.string.settingsUI_blocked_apps_ga_title);
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
        // no blocked apps message
        configureElement(
                isGA ? R.id.no_blocked_apps_ga_message : R.id.no_blocked_apps_message,
                mBlockedAppsViewModel.getBlockedApps(),
                controls ->
                        list ->
                                controls.setVisibility(
                                        (list.isEmpty() ? View.VISIBLE : View.GONE)));
        // recycler view (apps list)
        Function<App, View.OnClickListener> getOnclickListener =
                app -> view -> mBlockedAppsViewModel.restoreAppConsentButtonClickHandler(app);
        AppsListViewAdapter adapter =
                new AppsListViewAdapter(
                        mActivity,
                        mBlockedAppsViewModel.getBlockedApps(),
                        getOnclickListener,
                        true);
        configureRecyclerView(R.id.blocked_apps_list, adapter);

        configureNotifyAdapterDataChange(mBlockedAppsViewModel.getBlockedApps(), adapter);
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
                                    DialogFragmentManager.showUnblockAppDialog(mActivity, app);
                                } else {
                                    DialogManager.showUnblockAppDialog(mActivity, app);
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
        mBlockedAppsViewModel.getUiEvents().observe(mActivity, observer);
    }
}
