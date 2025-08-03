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

import static android.text.Html.FROM_HTML_MODE_LEGACY;

import android.app.admin.DeviceAdminInfo;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.text.Html;

import androidx.preference.Preference;

import com.android.car.settings.common.FragmentController;

/**
 * Controller for the screen that shows the device policies requested by a device admin app.
 */
public final class DeviceAdminAddPoliciesPreferenceController
        extends BaseDeviceAdminAddPreferenceController<Preference> {

    public DeviceAdminAddPoliciesPreferenceController(Context context, String preferenceKey,
                FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected int getDefaultAvailabilityStatus() {
        int superStatus = super.getDefaultAvailabilityStatus();
        if (superStatus != AVAILABLE) return superStatus;

        return isProfileOrDeviceOwner() ? DISABLED_FOR_PROFILE : AVAILABLE;
    }

    @Override
    protected void updateState(Preference preference) {
        preference.setTitle(getPolicyText());
    }

    private CharSequence getPolicyText() {
        Context context = getContext();
        boolean isSystemUser = mUm.isSystemUser();
        StringBuilder result = new StringBuilder();

        for (DeviceAdminInfo.PolicyInfo pi : mDeviceAdminInfo.getUsedPolicies()) {
            int labelId = isSystemUser ? pi.label : pi.labelForSecondaryUsers;
            CharSequence label = context.getText(labelId);
            mLogger.v("Adding policy: " + label);
            result.append("<li>&nbsp;").append(label).append("</li>");
        }

        return Html.fromHtml(result.toString(), FROM_HTML_MODE_LEGACY);
    }
}
