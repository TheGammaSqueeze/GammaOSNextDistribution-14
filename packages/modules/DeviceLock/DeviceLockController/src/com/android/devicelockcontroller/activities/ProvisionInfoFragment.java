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

import static com.android.devicelockcontroller.common.DeviceLockConstants.ACTION_START_DEVICE_FINANCING_PROVISIONING;
import static com.android.devicelockcontroller.common.DeviceLockConstants.ACTION_START_DEVICE_SUBSIDY_PROVISIONING;

import static com.google.common.base.Preconditions.checkNotNull;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.android.devicelockcontroller.R;
import com.android.devicelockcontroller.policy.PolicyObjectsProvider;
import com.android.devicelockcontroller.policy.ProvisionHelper;
import com.android.devicelockcontroller.policy.ProvisionHelperImpl;
import com.android.devicelockcontroller.util.LogUtil;

import java.util.Objects;

/**
 * The screen that provides information about the provision.
 */
public final class ProvisionInfoFragment extends Fragment {

    private static final String TAG = "ProvisionInfoFragment";
    private ActivityResultLauncher<String> mResultLauncher;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_provision_info, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ProvisionInfoViewModel viewModel;
        ViewModelProvider viewModelProvider = new ViewModelProvider(this);
        switch (Objects.requireNonNull(getActivity()).getIntent().getAction()) {
            case ACTION_START_DEVICE_FINANCING_PROVISIONING:
                viewModel = viewModelProvider.get(DeviceFinancingProvisionInfoViewModel.class);
                break;
            case ACTION_START_DEVICE_SUBSIDY_PROVISIONING:
                viewModel = viewModelProvider.get(DeviceSubsidyProvisionInfoViewModel.class);
                break;
            default:
                LogUtil.e(TAG, "Unknown action is received, exiting");
                return;
        }

        RecyclerView recyclerView = view.findViewById(R.id.recyclerview_provision_info);
        if (recyclerView == null) {
            LogUtil.e(TAG, "Could not find provision info RecyclerView, should not reach here.");
            return;
        }
        ProvisionInfoListAdapter adapter = new ProvisionInfoListAdapter();
        viewModel.mProvisionInfoListLiveData.observe(getViewLifecycleOwner(),
                adapter::submitList);
        recyclerView.setAdapter(adapter);
        ImageView imageView = view.findViewById(R.id.header_icon);
        if (imageView == null) {
            LogUtil.e(TAG, "Could not find header ImageView, should not reach here.");
            return;
        }
        imageView.setImageResource(ProvisionInfoViewModel.HEADER_DRAWABLE_ID);

        TextView headerTextView = view.findViewById(R.id.header_text);
        if (headerTextView == null) {
            LogUtil.e(TAG, "Could not find header TextView, should not reach here.");
            return;
        }
        viewModel.mHeaderTextLiveData.observe(getViewLifecycleOwner(),
                pair -> {
                    if (pair.first > 0 && !TextUtils.isEmpty(pair.second)) {
                        headerTextView.setText(getString(pair.first, pair.second));
                    }
                });

        TextView subheaderTextView = view.findViewById(R.id.subheader_text);
        if (subheaderTextView == null) {
            LogUtil.e(TAG, "Could not find subheader TextView, should not reach here.");
            return;
        }
        viewModel.mSubHeaderTextLiveData.observe(getViewLifecycleOwner(),
                pair -> {
                    if (pair.first > 0 && !TextUtils.isEmpty(pair.second)) {
                        subheaderTextView.setText(getString(pair.first, pair.second));
                    }
                });
        Button next = view.findViewById(R.id.button_next);
        checkNotNull(next);
        Context context = requireContext().getApplicationContext();
        viewModel.mIsMandatoryLiveData.observe(this,
                isMandatory -> {
                    if (!isMandatory) {
                        next.setText(R.string.start);
                        DeviceLockNotificationManager.cancelDeferredProvisioningNotification(
                                context);
                    }
                    next.setVisibility(View.VISIBLE);
                });
        next.setOnClickListener(
                v -> startActivity(new Intent(context, ProvisioningActivity.class)));

        ProvisionHelper provisionHelper = new ProvisionHelperImpl(context,
                ((PolicyObjectsProvider) context).getProvisionStateController());
        mResultLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                isGranted -> provisionHelper.pauseProvision());
        updatePreviousButton(checkNotNull(view.findViewById(R.id.button_previous)), viewModel,
                provisionHelper);
    }

    private void updatePreviousButton(Button previous, ProvisionInfoViewModel viewModel,
            ProvisionHelper provisionHelper) {
        viewModel.mIsMandatoryLiveData.observe(this,
                isMandatory -> {
                    if (!isMandatory) {
                        previous.setText(R.string.do_it_in_one_hour);
                        previous.setVisibility(View.VISIBLE);
                    }
                });


        viewModel.mIsProvisionForcedLiveData.observe(getViewLifecycleOwner(),
                isProvisionForced -> {
                    // Allow the user to defer provisioning only when provisioning is not forced.
                    previous.setEnabled(!isProvisionForced);
                    if (!isProvisionForced) {
                        previous.setOnClickListener(
                                v -> {
                                    int notificationPermission =
                                            ContextCompat.checkSelfPermission(requireContext(),
                                                    Manifest.permission.POST_NOTIFICATIONS);
                                    if (PackageManager.PERMISSION_GRANTED
                                            == notificationPermission) {
                                        provisionHelper.pauseProvision();
                                    } else {
                                        mResultLauncher.launch(
                                                Manifest.permission.POST_NOTIFICATIONS);
                                    }
                                });
                    }
                });
    }
}
