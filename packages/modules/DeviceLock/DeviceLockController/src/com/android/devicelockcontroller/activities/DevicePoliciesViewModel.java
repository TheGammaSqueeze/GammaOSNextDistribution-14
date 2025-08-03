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

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.android.devicelockcontroller.R;
import com.android.devicelockcontroller.storage.SetupParametersClient;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.Arrays;
import java.util.List;

/**
 * This class provides resources and data used to display the polices the device provider enforces
 * on this device.
 */
public final class DevicePoliciesViewModel extends ViewModel {

    static final int HEADER_DRAWABLE_ID = R.drawable.ic_info_24px;

    static final int HEADER_TEXT_ID = R.string.setup_info_title_text;

    private static final DevicePolicyGroup CONTROL_POLICY_GROUP =
            new DevicePolicyGroup.Builder()
                    .setTitleTextId(R.string.control_section_title)
                    .addDevicePolicy(R.drawable.ic_lock_outline_24px,
                            R.string.control_lock_device_text)
                    .addDevicePolicy(R.drawable.ic_file_download_24px,
                            R.string.control_download_text)
                    .addDevicePolicy(R.drawable.ic_bug_report_24px,
                            R.string.control_disable_debug_text)
                    .build();

    private static final DevicePolicyGroup LOCK_POLICY_GROUP =
            new DevicePolicyGroup.Builder()
                    .setTitleTextId(R.string.locked_section_title)
                    .addDevicePolicy(R.drawable.ic_local_hospital_24px,
                            R.string.locked_emergency_text)
                    .addDevicePolicy(R.drawable.ic_phone_callback_outlined_24px,
                            R.string.locked_phone_usage_text)
                    .addDevicePolicy(R.drawable.ic_settings_applications_24px,
                            R.string.locked_settings_usage_text)
                    .addDevicePolicy(R.drawable.ic_settings_backup_restore_24px,
                            R.string.locked_backup_and_restore_text)
                    .build();

    private static final DevicePolicyGroup EXPOSURE_POLICY_GROUP =
            new DevicePolicyGroup.Builder()
                    .setTitleTextId(R.string.exposure_section_title)
                    .addDevicePolicy(R.drawable.ic_delete_24px,
                            R.string.exposure_install_text)
                    .addDevicePolicy(R.drawable.ic_lock_open_24px,
                            R.string.exposure_lock_unlock_text)
                    .addDevicePolicy(R.drawable.ic_block_24px,
                            R.string.exposure_disable_dlc_text)
                    .build();

    private static final List<DevicePolicyGroup> DEVICE_POLICY_GROUPS = Arrays.asList(
            CONTROL_POLICY_GROUP, LOCK_POLICY_GROUP, EXPOSURE_POLICY_GROUP);
    public static final String TAG = "DevicePoliciesViewModel";

    final MutableLiveData<String> mProviderNameLiveData;
    final MediatorLiveData<List<DevicePolicyGroup>> mDevicePolicyGroupListLiveData;

    public LiveData<Boolean> getIsMandatoryLiveData() {
        return mIsMandatoryLiveData;
    }

    private final MutableLiveData<Boolean> mIsMandatoryLiveData = new MutableLiveData<>();

    public DevicePoliciesViewModel() {
        mProviderNameLiveData = new MutableLiveData<>();
        Futures.addCallback(SetupParametersClient.getInstance().getKioskAppProviderName(),
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(String result) {
                        mProviderNameLiveData.postValue(result);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LogUtil.e(TAG, "Failed to get Device Provider name!", t);
                    }
                }, MoreExecutors.directExecutor());

        Futures.addCallback(SetupParametersClient.getInstance().isProvisionMandatory(),
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Boolean result) {
                        mIsMandatoryLiveData.postValue(result);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LogUtil.e(TAG, "Failed to know if provision is mandatory!", t);
                    }
                }, MoreExecutors.directExecutor());
        mDevicePolicyGroupListLiveData = new MediatorLiveData<>();
        mDevicePolicyGroupListLiveData.addSource(mProviderNameLiveData,
                unused -> mDevicePolicyGroupListLiveData.setValue(DEVICE_POLICY_GROUPS));
    }
}
