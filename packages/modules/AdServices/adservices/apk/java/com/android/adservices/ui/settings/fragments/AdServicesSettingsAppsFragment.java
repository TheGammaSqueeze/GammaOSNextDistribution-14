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
package com.android.adservices.ui.settings.fragments;

import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.adservices.api.R;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.consent.App;
import com.android.adservices.ui.settings.activities.AppsActivity;
import com.android.adservices.ui.settings.delegates.AppsActionDelegate;
import com.android.adservices.ui.settings.viewadatpors.AppsListViewAdapter;
import com.android.adservices.ui.settings.viewmodels.AppsViewModel;

import java.util.function.Function;

/** Fragment for the apps view of the AdServices Settings App. */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class AdServicesSettingsAppsFragment extends Fragment {

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.apps_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        setupViewModel(view);
    }

    @Override
    public void onResume() {
        super.onResume();
        AppsViewModel viewModel = new ViewModelProvider(requireActivity()).get(AppsViewModel.class);
        viewModel.refresh();
        initActionListeners();
    }

    // initialize all action listeners except for actions in apps list
    private void initActionListeners() {
        AppsActionDelegate actionDelegate = ((AppsActivity) requireActivity()).getActionDelegate();
        actionDelegate.initAppsFragment(this);
    }

    // initializes view model connection with apps list.
    // (Action listeners for each item in the list will be handled by the adapter)
    private void setupViewModel(View rootView) {
        // create adapter
        AppsViewModel viewModel = new ViewModelProvider(requireActivity()).get(AppsViewModel.class);
        Function<App, View.OnClickListener> getOnclickListener =
                app -> view -> viewModel.revokeAppConsentButtonClickHandler(app);
        AppsListViewAdapter adapter =
                new AppsListViewAdapter(
                        requireContext(), viewModel.getApps(), getOnclickListener, false);

        // set adapter for recyclerView
        RecyclerView recyclerView = rootView.findViewById(R.id.apps_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        View noAppsMessage = rootView.findViewById(R.id.no_apps_message);
        View emptyAppsHiddenSection = rootView.findViewById(R.id.empty_apps_hidden_section);
        View blockedAppsBtn = rootView.findViewById(R.id.blocked_apps_button);

        // "Empty State": the state when the non-blocked list of apps/topics is empty.
        // blocked_apps_when_empty_state_button is added to noAppsMessage
        // noAppsMessages is visible only when Empty State
        // blocked_apps_when_empty_state_button differs from blocked_apps_button
        // in style with rounded corners, centered, colored
        viewModel
                .getApps()
                .observe(
                        getViewLifecycleOwner(),
                        appsList -> {
                            if (appsList.isEmpty()) {
                                noAppsMessage.setVisibility(View.VISIBLE);
                                emptyAppsHiddenSection.setVisibility(View.GONE);
                                blockedAppsBtn.setVisibility(View.GONE);
                            } else {
                                noAppsMessage.setVisibility(View.GONE);
                                emptyAppsHiddenSection.setVisibility(View.VISIBLE);
                                blockedAppsBtn.setVisibility(View.VISIBLE);
                            }
                            adapter.notifyDataSetChanged();
                        });

        Button blockedAppsWhenEmptyStateButton =
                rootView.findViewById(R.id.blocked_apps_when_empty_state_button);
        viewModel
                .getBlockedApps()
                .observe(
                        getViewLifecycleOwner(),
                        blockedAppsList -> {
                            if (blockedAppsList.isEmpty()) {
                                blockedAppsWhenEmptyStateButton.setEnabled(false);
                                blockedAppsWhenEmptyStateButton.setAlpha(
                                        getResources().getFloat(R.dimen.disabled_button_alpha));
                                blockedAppsWhenEmptyStateButton.setText(
                                        FlagsFactory.getFlags().getGaUxFeatureEnabled()
                                                ? R.string.settingsUI_no_blocked_apps_ga_text
                                                : R.string
                                                        .settingsUI_apps_view_no_blocked_apps_text);
                            } else {
                                blockedAppsWhenEmptyStateButton.setEnabled(true);
                                blockedAppsWhenEmptyStateButton.setAlpha(
                                        getResources().getFloat(R.dimen.enabled_button_alpha));
                                blockedAppsWhenEmptyStateButton.setText(
                                        FlagsFactory.getFlags().getGaUxFeatureEnabled()
                                                ? R.string.settingsUI_view_blocked_apps_title
                                                : R.string.settingsUI_blocked_apps_title);
                            }
                        });
    }
}
