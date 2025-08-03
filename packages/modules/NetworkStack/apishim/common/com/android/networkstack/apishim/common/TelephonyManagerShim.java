/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.networkstack.apishim.common;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Interface used to access API methods in {@link android.telephony.TelephonyManager}, with
 * appropriate fallbacks if the methods are not yet part of the released API.
 *
 * <p>This interface makes it easier for callers to use TelephonyManagerShimImpl, as it's more
 * obvious what methods must be implemented on each API level, and it abstracts from callers the
 * need to reference classes that have different implementations (which also does not work well
 * with IDEs).
 */
public interface TelephonyManagerShim {
    /** See android.telephony.TelephonyManager.CarrierPrivilegesListener */
    interface CarrierPrivilegesListenerShim {
        /** See android.telephony.TelephonyManager
         * .CarrierPrivilegesCallback#onCarrierPrivilegesChanged */
        default void onCarrierPrivilegesChanged(
                List<String> privilegedPackageNames,
                int[] privilegedUids) {}
        // TODO : remove the default implementation of this method once all changes are in
        /** See CarrierPrivilegesCallback#onCarrierServiceChanged */
        default void onCarrierServiceChanged(
                @Nullable String carrierServicePackageName,
                int carrierServiceUid) {}
    }

    /** See android.telephony.TelephonyManager#addCarrierPrivilegesListener */
    default void addCarrierPrivilegesListener(
            int logicalSlotIndex,
            Executor executor,
            CarrierPrivilegesListenerShim listener)
            throws UnsupportedApiLevelException {
        throw new UnsupportedApiLevelException("Only supported starting from API 33");
    }

    /** See android.telephony.TelephonyManager#addCarrierPrivilegesListener */
    default void removeCarrierPrivilegesListener(
            CarrierPrivilegesListenerShim listener)
            throws UnsupportedApiLevelException {
        throw new UnsupportedApiLevelException("Only supported starting from API 33");
    }

    // TODO : remove this method when all changes are in
    /** See android.telephony.TelephonyManager#getCarrierServicePackageNameForLogicalSlot */
    default String getCarrierServicePackageNameForLogicalSlot(int logicalSlotIndex)
            throws UnsupportedApiLevelException {
        throw new UnsupportedApiLevelException("Only supported starting from API 33");
    }
}
