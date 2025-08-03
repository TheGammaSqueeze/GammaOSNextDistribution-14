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

import android.annotation.Nullable;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.text.format.DateUtils;

import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;

import java.util.Date;

/**
 * Base class for controllers that shows the last time the device owner executed an action.
 */
abstract class BaseAdminActionReporterPreferenceController<P extends Preference>
        extends BaseEnterprisePrivacyPreferenceController<P> {

    protected BaseAdminActionReporterPreferenceController(Context context,
            String preferenceKey, FragmentController fragmentController,
            CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    public void updateState(Preference preference) {
        Date timestamp = getAdminActionTimestamp();

        if (timestamp == null) {
            preference.setSummary(R.string.enterprise_privacy_none);
        } else {
            preference.setSummary(DateUtils.formatDateTime(getContext(), timestamp.getTime(),
                        DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE));
        }
    }

    @Override
    protected int getDefaultAvailabilityStatus() {
        return isEnabled() ? AVAILABLE : CONDITIONALLY_UNAVAILABLE;
    }

    @Nullable
    protected abstract Date getAdminActionTimestamp();

    protected abstract boolean isEnabled();
}
