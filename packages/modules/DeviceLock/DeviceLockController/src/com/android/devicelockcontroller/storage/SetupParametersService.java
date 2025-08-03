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

package com.android.devicelockcontroller.storage;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

import com.android.devicelockcontroller.common.DeviceLockConstants.ProvisioningType;
import com.android.devicelockcontroller.util.LogUtil;

import java.util.List;

/**
 * A class exposing Setup Parameters as a service.
 */
public final class SetupParametersService extends Service {
    private static final String TAG = "SetupParametersService";
    private Context mContext;

    private final ISetupParametersService.Stub mBinder =
            new ISetupParametersService.Stub() {
                @Override
                public void overridePrefs(Bundle bundle) {
                    SetupParameters.overridePrefs(mContext, bundle);
                }

                @Override
                public void createPrefs(Bundle bundle) {
                    SetupParameters.createPrefs(mContext, bundle);
                }

                @Override
                public void dump() {
                    SetupParameters.dumpParameters(mContext);
                }

                @Override
                public void clear() {
                    SetupParameters.clear(mContext);
                }

                @Override
                public String getKioskPackage() {
                    return SetupParameters.getKioskPackage(mContext);
                }

                @Override
                public boolean getOutgoingCallsDisabled() {
                    return SetupParameters.getOutgoingCallsDisabled(mContext);
                }

                @Override
                public List<String> getKioskAllowlist() {
                    return SetupParameters.getKioskAllowlist(mContext);
                }

                @Override
                public boolean isNotificationsInLockTaskModeEnabled() {
                    return SetupParameters.isNotificationsInLockTaskModeEnabled(mContext);
                }

                @Override
                @ProvisioningType
                public int getProvisioningType() {
                    return SetupParameters.getProvisioningType(mContext);
                }

                @Override
                public boolean isProvisionMandatory() {
                    return SetupParameters.isProvisionMandatory(mContext);
                }

                @Override
                public String getKioskAppProviderName() {
                    return SetupParameters.getKioskAppProviderName(mContext);
                }

                @Override
                public boolean isInstallingFromUnknownSourcesDisallowed() {
                    return SetupParameters.isInstallingFromUnknownSourcesDisallowed(mContext);
                }

                @Override
                public String getTermsAndConditionsUrl() {
                    return SetupParameters.getTermsAndConditionsUrl(mContext);
                }

                @Override
                public String getSupportUrl() {
                    return SetupParameters.getSupportUrl(mContext);
                }
            };

    @Override
    public void onCreate() {
        LogUtil.d(TAG, "onCreate");
        mContext = this;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}
