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

import static com.android.devicelockcontroller.common.DeviceLockConstants.MANDATORY_PROVISION_DEVICE_RESET_COUNTDOWN_MINUTE;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionEvent.PROVISION_FAILURE;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.PROVISION_FAILED;

import static com.google.common.base.Preconditions.checkNotNull;

import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.work.WorkManager;

import com.android.devicelockcontroller.R;
import com.android.devicelockcontroller.activities.util.UrlUtils;
import com.android.devicelockcontroller.policy.PolicyObjectsProvider;
import com.android.devicelockcontroller.policy.ProvisionHelper;
import com.android.devicelockcontroller.policy.ProvisionHelperImpl;
import com.android.devicelockcontroller.policy.ProvisionStateController;
import com.android.devicelockcontroller.provision.worker.ReportDeviceProvisionStateWorker;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

import java.util.concurrent.TimeUnit;

/**
 * A screen which always displays a progress bar.
 */
public final class ProgressFragment extends Fragment {

    private static final String TAG = ProgressFragment.class.getSimpleName();

    private ProvisionHelper mProvisionHelper;

    public ProgressFragment() {
        super();
    }

    @VisibleForTesting
    ProgressFragment(ProvisionHelper provisionHelper) {
        super();
        mProvisionHelper = provisionHelper;
    }

    @Nullable
    @Override
    public View onCreateView(
            LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_progress, container, false);

        ImageView headerIconImageView = v.findViewById(R.id.header_icon);
        checkNotNull(headerIconImageView);

        TextView headerTextView = v.findViewById(R.id.header_text);
        checkNotNull(headerTextView);

        TextView subheaderTextView = v.findViewById(R.id.subheader_text);
        checkNotNull(subheaderTextView);

        ProgressBar progressBar = v.findViewById(R.id.progress_bar);
        checkNotNull(progressBar);

        View bottomView = v.findViewById(R.id.bottom);
        checkNotNull(bottomView);

        Chronometer countDownTimeView = v.findViewById(R.id.countdown_text);
        checkNotNull(countDownTimeView);

        ProvisioningProgressViewModel provisioningProgressViewModel =
                new ViewModelProvider(requireActivity()).get(ProvisioningProgressViewModel.class);
        provisioningProgressViewModel.getProvisioningProgressLiveData().observe(
                getViewLifecycleOwner(), provisioningProgress -> {
                    if (provisioningProgress.mIconId != 0) {
                        headerIconImageView.setImageResource(provisioningProgress.mIconId);
                    }
                    Context context = requireContext();
                    if (provisioningProgress.mHeaderId != 0) {
                        headerTextView.setText(
                                context.getString(provisioningProgress.mHeaderId,
                                        provisioningProgressViewModel
                                                .mProviderNameLiveData.getValue()));
                    }
                    if (provisioningProgress.mSubheaderId != 0) {
                        UrlUtils.setUrlText(subheaderTextView,
                                context.getString(provisioningProgress.mSubheaderId,
                                        provisioningProgressViewModel
                                                .mSupportUrlLiveData
                                                .getValue()));
                    }
                    if (provisioningProgress.mProgressBarVisible) {
                        progressBar.setVisibility(View.VISIBLE);
                    } else {
                        progressBar.setVisibility(View.GONE);
                    }
                    if (provisioningProgress.mBottomViewVisible) {
                        bottomView.setVisibility(View.VISIBLE);
                        Button retryButton = bottomView.findViewById(R.id.button_retry);
                        checkNotNull(retryButton);
                        PolicyObjectsProvider policyObjects =
                                (PolicyObjectsProvider) context.getApplicationContext();
                        ProvisionStateController provisionStateController =
                                policyObjects.getProvisionStateController();
                        if (mProvisionHelper == null) {
                            mProvisionHelper = new ProvisionHelperImpl(
                                    context,
                                    provisionStateController);
                        }
                        retryButton.setOnClickListener(
                                view -> mProvisionHelper.scheduleKioskAppInstallation(
                                        requireActivity(),
                                        provisioningProgressViewModel,
                                        /* isProvisionMandatory= */ false));

                        Button exitButton = bottomView.findViewById(R.id.button_exit);
                        checkNotNull(exitButton);
                        FutureCallback<Integer> getProvisionStateCallback =
                                new FutureCallback<>() {
                                    @Override
                                    public void onSuccess(Integer result) {
                                        if (result == PROVISION_FAILED) {
                                            // Already reported set up failure. Finish normally
                                            getActivity().finish();
                                            return;
                                        }
                                        ReportDeviceProvisionStateWorker.reportSetupFailed(
                                                WorkManager.getInstance(requireContext()),
                                                provisioningProgress.mFailureReason);
                                        provisionStateController.postSetNextStateForEventRequest(
                                                PROVISION_FAILURE);
                                    }

                                    @Override
                                    public void onFailure(Throwable t) {
                                        LogUtil.e(TAG, "Failed to get provision state", t);
                                    }
                                };
                        exitButton.setOnClickListener(
                                view -> Futures.addCallback(provisionStateController.getState(),
                                        getProvisionStateCallback,
                                        context.getMainExecutor()));
                    } else {
                        bottomView.setVisibility(View.GONE);
                    }
                    if (provisioningProgress.mCountDownTimerVisible) {
                        countDownTimeView.setBase(
                                SystemClock.elapsedRealtime() + TimeUnit.MINUTES.toMillis(
                                        MANDATORY_PROVISION_DEVICE_RESET_COUNTDOWN_MINUTE));
                        countDownTimeView.start();
                        countDownTimeView.setVisibility(View.VISIBLE);
                    } else {
                        countDownTimeView.setVisibility(View.GONE);
                    }
                });
        return v;
    }
}
