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

import com.android.adservices.api.R;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.ui.settings.activities.AdServicesSettingsMainActivity;
import com.android.adservices.ui.settings.delegates.MainActionDelegate;
import com.android.adservices.ui.settings.viewmodels.MainViewModel;
import com.android.settingslib.widget.MainSwitchBar;

import java.util.Objects;

/** Fragment for the main view of the AdServices Settings App. */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class AdServicesSettingsMainFragment extends Fragment {

    public static final String ERROR_MESSAGE_VIEW_MODEL_EXCEPTION_WHILE_GET_CONSENT =
            "getConsent method failed. Will not change consent value in view model.";
    public static final String PRIVACY_SANDBOX_BETA_SWITCH_KEY = "privacy_sandbox_beta_switch";

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.main_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        initActionListeners();
    }

    // initialize all action listeners
    private void initActionListeners() {
        MainActionDelegate actionDelegate =
                ((AdServicesSettingsMainActivity) requireActivity()).getActionDelegate();
        actionDelegate.initMainFragment(this);
        setupViewModel();
    }

    private void setupViewModel() {
        if (FlagsFactory.getFlags().getGaUxFeatureEnabled()) {
            // the entry point of Apps, Topics, Measurement should be visible all the time
            requireView().findViewById(R.id.privacy_sandbox_controls).setVisibility(View.VISIBLE);
            return;
        }

        MainViewModel model = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        MainSwitchBar mainSwitchBar =
                Objects.requireNonNull(requireView().findViewById(R.id.main_switch_bar));

        View privacySandboxControls = requireView().findViewById(R.id.privacy_sandbox_controls);
        model.getConsent()
                .observe(
                        getViewLifecycleOwner(),
                        consentGiven -> {
                            mainSwitchBar.setChecked(consentGiven);
                            privacySandboxControls.setVisibility(
                                    consentGiven ? View.VISIBLE : View.GONE);
                        });
    }
}
