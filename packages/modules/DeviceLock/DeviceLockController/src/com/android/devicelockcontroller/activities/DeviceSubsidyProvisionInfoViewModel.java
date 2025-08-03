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

import com.android.devicelockcontroller.R;

import java.util.List;

/**
 * This class provides resources and data used for the device subsidy use case.
 */
public final class DeviceSubsidyProvisionInfoViewModel extends ProvisionInfoViewModel {

    private static final int MANDATORY_HEADER_TEXT_ID = R.string.subsidy_program_header;
    private static final int SUB_HEADER_TEXT_ID = R.string.enroll_your_device_subsidy_subheader;

    private static final ProvisionInfo[] PROVISION_INFOS = new ProvisionInfo[]{
            new ProvisionInfo(R.drawable.ic_file_download_24px,
                    R.string.download_kiosk_app,
                    ProvisionInfo.ProvisionInfoType.REGULAR),
            new ProvisionInfo(R.drawable.ic_lock_outline_24px,
                    R.string.restrict_device_if_dont_make_payment,
                    ProvisionInfo.ProvisionInfoType.TERMS_AND_CONDITIONS),
            new ProvisionInfo(R.drawable.ic_help_24px,
                    R.string.contact_provider_for_help,
                    ProvisionInfo.ProvisionInfoType.SUPPORT)};

    public DeviceSubsidyProvisionInfoViewModel() {
        mSubHeaderTextId = SUB_HEADER_TEXT_ID;
        mMandatoryHeaderTextId = MANDATORY_HEADER_TEXT_ID;
        mProvisionInfoList = List.of(PROVISION_INFOS);
        retrieveData();
    }
}
