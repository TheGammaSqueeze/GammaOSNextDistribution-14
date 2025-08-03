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
import com.android.adservices.data.topics.Topic;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.ui.settings.activities.BlockedTopicsActivity;
import com.android.adservices.ui.settings.delegates.BlockedTopicsActionDelegate;
import com.android.adservices.ui.settings.viewadatpors.TopicsListViewAdapter;
import com.android.adservices.ui.settings.viewmodels.BlockedTopicsViewModel;

import java.util.function.Function;

/** Fragment for the blocked topics view of the AdServices Settings App. */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class AdServicesSettingsBlockedTopicsFragment extends Fragment {

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.blocked_topics_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        setupViewModel(view);
        initActionListeners();
    }

    // initialize all action listeners except for actions in blocked topics list
    private void initActionListeners() {
        BlockedTopicsActionDelegate actionDelegate =
                ((BlockedTopicsActivity) requireActivity()).getActionDelegate();
        actionDelegate.initBlockedTopicsFragment();
    }

    // initializes view model connection with blocked topics list.
    // (Action listeners for each item in the list will be handled by the adapter)
    private void setupViewModel(View rootView) {
        // create adapter
        BlockedTopicsViewModel viewModel =
                new ViewModelProvider(requireActivity()).get(BlockedTopicsViewModel.class);
        Function<Topic, View.OnClickListener> getOnclickListener =
                topic -> view -> viewModel.restoreTopicConsentButtonClickHandler(topic);
        TopicsListViewAdapter adapter =
                new TopicsListViewAdapter(
                        requireContext(), viewModel.getBlockedTopics(), getOnclickListener, true);

        // set adapter for recyclerView
        RecyclerView recyclerView = rootView.findViewById(R.id.blocked_topics_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        View noBlockedTopicsMessage = rootView.findViewById(R.id.no_blocked_topics_message);
        View noBlockedTopicsGaMessage = rootView.findViewById(R.id.no_blocked_topics_ga_message);
        viewModel
                .getBlockedTopics()
                .observe(
                        getViewLifecycleOwner(),
                        blockedTopicsList -> {
                            if (!FlagsFactory.getFlags().getGaUxFeatureEnabled()) {
                                noBlockedTopicsGaMessage.setVisibility(View.GONE);
                                noBlockedTopicsMessage.setVisibility(
                                        blockedTopicsList.isEmpty() ? View.VISIBLE : View.GONE);
                            } else {
                                noBlockedTopicsMessage.setVisibility(View.GONE);
                                noBlockedTopicsGaMessage.setVisibility(
                                        blockedTopicsList.isEmpty() ? View.VISIBLE : View.GONE);
                            }
                            adapter.notifyDataSetChanged();
                        });
    }
}
