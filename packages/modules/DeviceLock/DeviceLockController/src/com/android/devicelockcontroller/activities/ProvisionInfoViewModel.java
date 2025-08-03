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

import android.util.Pair;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.android.devicelockcontroller.R;
import com.android.devicelockcontroller.storage.GlobalParametersClient;
import com.android.devicelockcontroller.storage.SetupParametersClient;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.List;

/**
 * This class provides the resources and {@link ProvisionInfo} to render the
 * {@link ProvisionInfoFragment}.
 */
public abstract class ProvisionInfoViewModel extends ViewModel {

    private static final String TAG = "ProvisionInfoViewModel";
    static final int HEADER_DRAWABLE_ID = R.drawable.ic_info_24px;
    static final int HEADER_TEXT_ID = R.string.enroll_your_device_header;
    int mMandatoryHeaderTextId;
    int mSubHeaderTextId;
    List<ProvisionInfo> mProvisionInfoList;
    final MutableLiveData<Boolean> mIsMandatoryLiveData = new MutableLiveData<>();
    final MutableLiveData<Boolean> mIsProvisionForcedLiveData = new MutableLiveData<>();
    final MutableLiveData<Pair<Integer, String>> mHeaderTextLiveData = new MutableLiveData<>();
    final MutableLiveData<Pair<Integer, String>> mSubHeaderTextLiveData = new MutableLiveData<>();
    final MutableLiveData<List<ProvisionInfo>> mProvisionInfoListLiveData = new MutableLiveData<>();

    void retrieveData() {
        SetupParametersClient setupParametersClient = SetupParametersClient.getInstance();
        ListenableFuture<String> kioskAppProviderNameFuture =
                setupParametersClient.getKioskAppProviderName();
        ListenableFuture<Boolean> isMandatoryFuture = setupParametersClient.isProvisionMandatory();
        ListenableFuture<String> termsAndConditionsUrlFuture =
                setupParametersClient.getTermsAndConditionsUrl();
        ListenableFuture<String> supportUrlFuture = setupParametersClient.getSupportUrl();
        ListenableFuture<Boolean> isProvisionForcedFuture =
                GlobalParametersClient.getInstance().isProvisionForced();
        ListenableFuture<Void> result = Futures.whenAllSucceed(isMandatoryFuture,
                        kioskAppProviderNameFuture, termsAndConditionsUrlFuture, supportUrlFuture,
                        isProvisionForcedFuture)
                .call(() -> {
                    boolean isMandatory = Futures.getDone(isMandatoryFuture);
                    String providerName = Futures.getDone(kioskAppProviderNameFuture);
                    String termsAndConditionUrl = Futures.getDone(termsAndConditionsUrlFuture);
                    String supportUrl = Futures.getDone(supportUrlFuture);
                    for (int i = 0, size = mProvisionInfoList.size(); i < size; ++i) {
                        ProvisionInfo provisionInfo = mProvisionInfoList.get(i);
                        switch (provisionInfo.getType()) {
                            case ProvisionInfo.ProvisionInfoType.REGULAR -> provisionInfo.setUrl(
                                    "");
                            case ProvisionInfo.ProvisionInfoType.TERMS_AND_CONDITIONS ->
                                    provisionInfo.setUrl(termsAndConditionUrl);
                            case ProvisionInfo.ProvisionInfoType.SUPPORT -> provisionInfo.setUrl(
                                    supportUrl);
                            default -> throw new IllegalStateException(
                                    "Unexpected value: " + provisionInfo.getType());
                        }
                        provisionInfo.setProviderName(providerName);
                    }
                    mProvisionInfoListLiveData.postValue(mProvisionInfoList);
                    mIsMandatoryLiveData.postValue(isMandatory);
                    mHeaderTextLiveData.postValue(
                            new Pair<>(isMandatory ? mMandatoryHeaderTextId : HEADER_TEXT_ID,
                                    providerName));
                    mSubHeaderTextLiveData.postValue(
                            new Pair<>(isMandatory ? 0 : mSubHeaderTextId,
                                    providerName));
                    mIsProvisionForcedLiveData.postValue(Futures.getDone(isProvisionForcedFuture));
                    return null;
                }, MoreExecutors.directExecutor());
        Futures.addCallback(result, new FutureCallback<>() {
            @Override
            public void onSuccess(Void result) {
                LogUtil.i(TAG, "Successfully updated live data");
            }

            @Override
            public void onFailure(Throwable t) {
                throw new RuntimeException(t);
            }
        }, MoreExecutors.directExecutor());
    }
}
