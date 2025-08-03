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

package com.android.tv.settings.enterprise;

import android.content.Context;
import android.text.format.DateUtils;

import androidx.preference.Preference;

import com.android.settingslib.core.AbstractPreferenceController;
import com.android.tv.settings.R;
import com.android.tv.settings.overlay.FlavorUtils;

import java.util.Date;

/**
 * Forked from:
 * Settings/src/com/android/settings/enterprise/AdminActionPreferenceControllerBase.java
 */
public abstract class AdminActionPreferenceControllerBase extends AbstractPreferenceController {

    protected final EnterprisePrivacyFeatureProvider mFeatureProvider;

    public AdminActionPreferenceControllerBase(Context context) {
        super(context);
        mFeatureProvider = FlavorUtils.getFeatureFactory(
                context).getEnterprisePrivacyFeatureProvider(context);
    }

    protected abstract Date getAdminActionTimestamp();

    @Override
    public void updateState(Preference preference) {
        final Date timestamp = getAdminActionTimestamp();
        preference.setSummary(
                timestamp == null ? mContext.getString(R.string.enterprise_privacy_none)
                        : DateUtils.formatDateTime(mContext, timestamp.getTime(),
                                DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE));
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
