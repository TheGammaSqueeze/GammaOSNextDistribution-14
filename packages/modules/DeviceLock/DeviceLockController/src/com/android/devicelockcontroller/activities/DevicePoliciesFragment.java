/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.devicelockcontroller.activities;

import static com.android.devicelockcontroller.activities.DevicePoliciesViewModel.HEADER_DRAWABLE_ID;
import static com.android.devicelockcontroller.activities.DevicePoliciesViewModel.HEADER_TEXT_ID;

import static com.google.common.base.Preconditions.checkNotNull;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.android.devicelockcontroller.R;
import com.android.devicelockcontroller.policy.PolicyObjectsProvider;
import com.android.devicelockcontroller.policy.ProvisionHelper;
import com.android.devicelockcontroller.policy.ProvisionHelperImpl;

/**
 * A screen which lists the polies enforced on the device by the device provider.
 */
public final class DevicePoliciesFragment extends Fragment {

    private static final String TAG = "DevicePoliciesFragment";

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_device_policies, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        DevicePoliciesViewModel viewModel = new ViewModelProvider(this).get(
                DevicePoliciesViewModel.class);

        RecyclerView recyclerView = view.findViewById(R.id.recyclerview_device_policy_group);
        DevicePolicyGroupListAdapter adapter = new DevicePolicyGroupListAdapter();
        viewModel.mDevicePolicyGroupListLiveData.observe(getViewLifecycleOwner(),
                devicePolicyGroups -> {
                    adapter.setProviderName(viewModel.mProviderNameLiveData.getValue());
                    adapter.submitList(devicePolicyGroups);
                });
        checkNotNull(recyclerView);
        recyclerView.setAdapter(adapter);

        ImageView imageView = view.findViewById(R.id.header_icon);
        checkNotNull(imageView);
        imageView.setImageResource(HEADER_DRAWABLE_ID);

        TextView headerTextView = view.findViewById(R.id.header_text);
        checkNotNull(headerTextView);
        viewModel.mProviderNameLiveData.observe(getViewLifecycleOwner(),
                providerName -> headerTextView.setText(
                        getString(HEADER_TEXT_ID, providerName)));

        Context context = requireContext().getApplicationContext();
        ProvisionHelper provisionHelper = new ProvisionHelperImpl(context,
                ((PolicyObjectsProvider) context).getProvisionStateController());

        ProvisioningProgressViewModel provisioningProgressViewModel =
                new ViewModelProvider(requireActivity()).get(ProvisioningProgressViewModel.class);
        Button button = view.findViewById(R.id.button_next);
        checkNotNull(button);
        viewModel.getIsMandatoryLiveData().observe(this,
                isMandatory -> {
                    button.setOnClickListener(
                            v -> provisionHelper.scheduleKioskAppInstallation(requireActivity(),
                                    provisioningProgressViewModel,
                                    isMandatory));
                    button.setVisibility(View.VISIBLE);
                });
    }
}
