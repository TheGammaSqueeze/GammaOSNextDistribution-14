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

package com.android.networkstack.apishim.api33;

import static com.android.modules.utils.build.SdkLevel.isAtLeastT;
import static com.android.net.module.util.CollectionUtils.toIntArray;

import android.os.Build;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyManager.CarrierPrivilegesCallback;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.android.networkstack.apishim.common.TelephonyManagerShim;
import com.android.networkstack.apishim.common.UnsupportedApiLevelException;

import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Implementation of {@link TelephonyManagerShim} for API 33.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
public class TelephonyManagerShimImpl extends
        com.android.networkstack.apishim.api31.TelephonyManagerShimImpl {
    private HashMap<CarrierPrivilegesListenerShim, CarrierPrivilegesCallback> mListenerMap =
            new HashMap<>();
    protected TelephonyManagerShimImpl(TelephonyManager telephonyManager) {
        super(telephonyManager);
    }

    /** Get a new instance of {@link TelephonyManagerShim}. */
    @RequiresApi(Build.VERSION_CODES.Q)
    public static TelephonyManagerShim newInstance(TelephonyManager tm) {
        if (!isAtLeastT()) {
            return com.android.networkstack.apishim.api31.TelephonyManagerShimImpl.newInstance(tm);
        }
        return new TelephonyManagerShimImpl(tm);
    }

    /** See android.telephony.TelephonyManager#registerCarrierPrivilegesCallback */
    public void addCarrierPrivilegesListener(
            int logicalSlotIndex,
            Executor executor,
            CarrierPrivilegesListenerShim listener)
            throws UnsupportedApiLevelException {
        CarrierPrivilegesCallback carrierPrivilegesCallback = new CarrierPrivilegesCallback() {
            public void onCarrierPrivilegesChanged(
                    Set<String> privilegedPackageNames,
                    Set<Integer> privilegedUids) {
                // TODO(b/221306368): Rebuild thoroughly based on onCarrierServiceChanged interface
                // This is the minimum change to remove the dependency on the obsoleted API in
                // CarrierPrivilegesListener. A follow-up CL should refactor Connectivity modules
                // with carrier service change API instead.
                List<String> pkgNames = List.copyOf(privilegedPackageNames);
                int[] pkgUids = toIntArray(privilegedUids);
                listener.onCarrierPrivilegesChanged(pkgNames, pkgUids);
            }

            @Override
            public void onCarrierServiceChanged(@Nullable final String carrierServicePackageName,
                    final int carrierServiceUid) {
                listener.onCarrierServiceChanged(carrierServicePackageName, carrierServiceUid);
            }
        };
        mTm.registerCarrierPrivilegesCallback(logicalSlotIndex, executor,
                carrierPrivilegesCallback);
        mListenerMap.put(listener, carrierPrivilegesCallback);
    }

    /** See android.telephony.TelephonyManager#unregisterCarrierPrivilegesCallback */
    public void removeCarrierPrivilegesListener(
            CarrierPrivilegesListenerShim listener)
            throws UnsupportedApiLevelException {
        mTm.unregisterCarrierPrivilegesCallback(mListenerMap.get(listener));
        mListenerMap.remove(listener);
    }

    // TODO : remove this method when all changes are in
    /** See android.telephony.TelephonyManager#getCarrierServicePackageNameForLogicalSlot */
    public String getCarrierServicePackageNameForLogicalSlot(int logicalSlotIndex) {
        return mTm.getCarrierServicePackageNameForLogicalSlot(logicalSlotIndex);
    }
}
