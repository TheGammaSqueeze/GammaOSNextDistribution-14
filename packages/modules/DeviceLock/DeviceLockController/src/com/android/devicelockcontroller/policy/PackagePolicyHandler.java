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

package com.android.devicelockcontroller.policy;

import android.app.admin.DevicePolicyManager;
import android.content.Context;

import com.android.devicelockcontroller.storage.SetupParametersClient;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/** Enforces restrictions on Kiosk app and controller. */
final class PackagePolicyHandler implements PolicyHandler {
    private static final String TAG = "PackagePolicyHandler";

    private final Context mContext;
    private final DevicePolicyManager mDpm;
    private final Executor mBgExecutor;

    PackagePolicyHandler(Context context, DevicePolicyManager dpm, Executor bgExecutor) {
        mContext = context;
        mDpm = dpm;
        mBgExecutor = bgExecutor;
    }

    @Override
    public ListenableFuture<Boolean> onProvisioned() {
        return enablePackageProtection(/* enableForKiosk= */ true);
    }

    @Override
    public ListenableFuture<Boolean> onCleared() {
        return enablePackageProtection(/* enableForKiosk= */ false);
    }

    private ListenableFuture<Boolean> enablePackageProtection(boolean enableForKiosk) {
        return Futures.transform(SetupParametersClient.getInstance().getKioskPackage(),
                kioskPackageName -> {
                    if (kioskPackageName == null) {
                        LogUtil.d(TAG, "Kiosk package is not set");
                    } else {
                        try {
                            mDpm.setUninstallBlocked(null /* admin */, kioskPackageName,
                                    enableForKiosk);
                        } catch (SecurityException e) {
                            LogUtil.e(TAG, "Unable to set device policy", e);
                            return false;
                        }
                    }

                    final List<String> pkgList = new ArrayList<>();

                    // The controller itself should always have user control disabled
                    pkgList.add(mContext.getPackageName());

                    if (kioskPackageName != null && enableForKiosk) {
                        pkgList.add(kioskPackageName);
                    }

                    try {
                        mDpm.setUserControlDisabledPackages(null /* admin */, pkgList);
                    } catch (SecurityException e) {
                        LogUtil.e(TAG, "Failed to setUserControlDisabledPackages", e);
                        return false;
                    }

                    return true;
                }, mBgExecutor);
    }
}
