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

import static com.android.devicelockcontroller.common.DeviceLockConstants.ProvisioningType.TYPE_FINANCED;
import static com.android.devicelockcontroller.common.DeviceLockConstants.ProvisioningType.TYPE_SUBSIDY;

import android.util.Pair;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.android.devicelockcontroller.R;
import com.android.devicelockcontroller.storage.SetupParametersClient;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * ViewModel class which provides data for the {@link DeviceInfoSettingsFragment}.
 */
public final class DeviceInfoSettingsViewModel extends ViewModel {

    private static final String TAG = "DeviceInfoSettingsViewModel";
    private final List<Pair<Integer, Integer>> mPreferenceWithProviderNameKeyTitlePairs =
            new ArrayList<>(Arrays.asList(
                    new Pair<>(
                            R.string.settings_credit_provider_capabilities_category_preference_key,
                            R.string.settings_credit_provider_capabilities_category),
                    new Pair<>(R.string.settings_allowlisted_apps_preference_key,
                            R.string.settings_allowlisted_apps),
                    new Pair<>(R.string.settings_restrictions_removed_preference_key,
                            R.string.settings_restrictions_removed),
                    new Pair<>(R.string.settings_uninstall_kiosk_app_preference_key,
                            R.string.settings_uninstall_kiosk_app)));

    private String mSupportUrl;
    private final MutableLiveData<String> mProviderNameLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> mInstallFromUnknownSourcesDisallowedLiveData =
            new MutableLiveData<>();

    public DeviceInfoSettingsViewModel() {

        SetupParametersClient setupParametersClient = SetupParametersClient.getInstance();
        ListenableFuture<Integer> getProvisioningTypeFuture =
                setupParametersClient.getProvisioningType();
        ListenableFuture<String> getKioskAppProviderNameFuture =
                setupParametersClient.getKioskAppProviderName();
        ListenableFuture<Boolean> isInstallFromUnknownSourcesDisallowedFuture =
                setupParametersClient.isInstallingFromUnknownSourcesDisallowed();
        ListenableFuture<String> getSupportUrlFuture = setupParametersClient.getSupportUrl();
        Futures.addCallback(isInstallFromUnknownSourcesDisallowedFuture,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Boolean result) {
                        mInstallFromUnknownSourcesDisallowedLiveData.postValue(result);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LogUtil.e(TAG,
                                "Failed to get isInstallingFromUnknownSourcesDisallowed from "
                                        + "setup parameters",
                                t);
                    }
                }, MoreExecutors.directExecutor());

        Futures.addCallback(
                Futures.whenAllSucceed(getProvisioningTypeFuture, getKioskAppProviderNameFuture,
                                getSupportUrlFuture)
                        .call(() -> {
                            Integer provisioningType = Futures.getDone(
                                    getProvisioningTypeFuture);
                            switch (provisioningType) {
                                case TYPE_FINANCED:
                                    mPreferenceWithProviderNameKeyTitlePairs.add(
                                            new Pair<>(
                                                    R.string.settings_intro_preference_key,
                                                    R.string.settings_intro_device_financing));
                                    break;
                                case TYPE_SUBSIDY:
                                    mPreferenceWithProviderNameKeyTitlePairs.add(
                                            new Pair<>(
                                                    R.string.settings_intro_preference_key,
                                                    R.string.settings_intro_device_subsidy));
                                    break;
                                default:
                                    throw new IllegalStateException(
                                            "Invalid provisioning type");
                            }
                            mSupportUrl = Futures.getDone(getSupportUrlFuture);
                            mProviderNameLiveData.postValue(
                                    Futures.getDone(getKioskAppProviderNameFuture));
                            return null;
                        }, MoreExecutors.directExecutor()),
                new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        LogUtil.d(TAG, "Successfully retrieved setup parameters");
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LogUtil.e(TAG, "Failed to retrieve setup parameters", t);
                    }
                }, MoreExecutors.directExecutor());
    }

    public MutableLiveData<String> getProviderNameLiveData() {
        return mProviderNameLiveData;
    }

    public MutableLiveData<Boolean> getInstallFromUnknownSourcesDisallowedLiveData() {
        return mInstallFromUnknownSourcesDisallowedLiveData;
    }

    public String getSupportUrl() {
        return mSupportUrl;
    }

    public List<Pair<Integer, Integer>> getPreferenceWithProviderNameKeyTitlePairs() {
        return mPreferenceWithProviderNameKeyTitlePairs;
    }
}
