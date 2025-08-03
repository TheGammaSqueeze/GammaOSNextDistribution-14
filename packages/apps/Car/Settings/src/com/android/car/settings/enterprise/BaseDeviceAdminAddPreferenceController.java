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

package com.android.car.settings.enterprise;

import android.app.admin.DeviceAdminInfo;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;

import androidx.preference.Preference;

import com.android.car.settings.common.FragmentController;

import java.util.Objects;

/**
 * Base class for controllers in the device admin details screen.
 */
abstract class BaseDeviceAdminAddPreferenceController<P extends Preference>
        extends BaseEnterprisePreferenceController<P> {

    protected DeviceAdminInfo mDeviceAdminInfo;

    protected BaseDeviceAdminAddPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected int getDefaultAvailabilityStatus() {
        int superStatus = super.getDefaultAvailabilityStatus();
        if (superStatus != AVAILABLE) return superStatus;

        return mDeviceAdminInfo == null ? CONDITIONALLY_UNAVAILABLE : AVAILABLE;
    }

    final <T extends BaseDeviceAdminAddPreferenceController<P>> T setDeviceAdmin(
            DeviceAdminInfo deviceAdminInfo) {
        mDeviceAdminInfo = Objects.requireNonNull(deviceAdminInfo);
        @SuppressWarnings("unchecked")
        T safeCast = (T) this;
        return safeCast;
    }

    final boolean isProfileOrDeviceOwner() {
        return mDeviceAdminInfo == null
                ? false : isProfileOrDeviceOwner(mDeviceAdminInfo.getComponent());
    }
}
