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

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.adservices.api.R;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.consent.App;
import com.android.adservices.ui.settings.activities.BlockedAppsActivity;
import com.android.adservices.ui.settings.delegates.BlockedAppsActionDelegate;
import com.android.adservices.ui.settings.viewadatpors.AppsListViewAdapter;
import com.android.adservices.ui.settings.viewmodels.BlockedAppsViewModel;

import java.util.function.Function;

/** Fragment for the blocked apps view of the AdServices Settings App. */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class AdServicesSettingsBlockedAppsFragment extends Fragment {

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.blocked_apps_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        setupViewModel(view);
        initActionListeners();
    }

    // initialize all action listeners except for actions in blocked apps list
    private void initActionListeners() {
        BlockedAppsActionDelegate actionDelegate =
                ((BlockedAppsActivity) requireActivity()).getActionDelegate();
        actionDelegate.initBlockedAppsFragment();
    }

    // initializes view model connection with blocked apps list.
    // (Action listeners for each item in the list will be handled by the adapter)
    private void setupViewModel(View rootView) {
        // create adapter
        BlockedAppsViewModel viewModel =
                new ViewModelProvider(requireActivity()).get(BlockedAppsViewModel.class);
        Function<App, View.OnClickListener> getOnclickListener =
                app -> view -> viewModel.restoreAppConsentButtonClickHandler(app);
        AppsListViewAdapter adapter =
                new AppsListViewAdapter(
                        requireContext(), viewModel.getBlockedApps(), getOnclickListener, true);

        // set adapter for recyclerView
        RecyclerView recyclerView = rootView.findViewById(R.id.blocked_apps_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        View noBlockedAppsMessage = rootView.findViewById(R.id.no_blocked_apps_message);
        View noBlockedAppsGaMessage = rootView.findViewById(R.id.no_blocked_apps_ga_message);
        viewModel
                .getBlockedApps()
                .observe(
                        getViewLifecycleOwner(),
                        blockedAppsList -> {
                            if (!FlagsFactory.getFlags().getGaUxFeatureEnabled()) {
                                noBlockedAppsGaMessage.setVisibility(View.GONE);
                                noBlockedAppsMessage.setVisibility(
                                        blockedAppsList.isEmpty() ? View.VISIBLE : View.GONE);
                            } else {
                                noBlockedAppsMessage.setVisibility(View.GONE);
                                noBlockedAppsGaMessage.setVisibility(
                                        blockedAppsList.isEmpty() ? View.VISIBLE : View.GONE);
                            }
                            adapter.notifyDataSetChanged();
                        });
    }
}
