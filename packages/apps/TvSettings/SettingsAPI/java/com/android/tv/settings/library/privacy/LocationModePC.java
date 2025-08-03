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

package com.android.tv.settings.library.privacy;

import android.content.Context;
import android.location.LocationManager;
import android.os.UserHandle;
import android.os.UserManager;

import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceCompatManager;
import com.android.tv.settings.library.settingslib.RestrictedLockUtils;
import com.android.tv.settings.library.settingslib.RestrictedLockUtilsInternal;
import com.android.tv.settings.library.util.ResourcesUtil;
import com.android.tv.settings.library.util.RestrictedPreferenceController;

/**
 * Preference controller to handle location mode preference.
 */
public class LocationModePC extends RestrictedPreferenceController {
    private static final String LOCATION_MODE_WIFI = "wifi";
    private static final String LOCATION_MODE_OFF = "off";

    public LocationModePC(Context context,
            UIUpdateCallback callback, int stateIdentifier,
            PreferenceCompatManager preferenceCompatManager) {
        super(context, callback, stateIdentifier, preferenceCompatManager);
    }

    @Override
    public String[] getPreferenceKey() {
        return new String[]{LocationState.KEY_LOCATION_MODE};
    }

    @Override
    protected void init() {
        final RestrictedLockUtils.EnforcedAdmin admin = checkIfUserRestrictionEnforcedByAdmin();
        if (admin == null) {
            mPreferenceCompat.setHasOnPreferenceChangeListener(true);
            mPreferenceCompat.setType(PreferenceCompat.TYPE_LIST);
            mPreferenceCompat.setTitle(ResourcesUtil.getString(mContext, "location_status"));
            mPreferenceCompat.setEntries(new CharSequence[]{
                    ResourcesUtil.getString(mContext, "location_mode_wifi_description"),
                    ResourcesUtil.getString(mContext, "off")});
            mPreferenceCompat.setEntryValues(new CharSequence[]{
                    LOCATION_MODE_WIFI,
                    LOCATION_MODE_OFF
            });
            mPreferenceCompat.setEnabled(!hasUserRestriction());
        } else {
            mPreferenceCompat.setTitle(ResourcesUtil.getString(mContext, "location_status"));
            setDisabledByAdmin(admin);
        }
        update();
    }

    private RestrictedLockUtils.EnforcedAdmin checkIfUserRestrictionEnforcedByAdmin() {
        final RestrictedLockUtils.EnforcedAdmin admin = RestrictedLockUtilsInternal
                .checkIfRestrictionEnforced(mContext,
                        UserManager.DISALLOW_SHARE_LOCATION, UserHandle.myUserId());

        if (admin != null) {
            return admin;
        }

        return RestrictedLockUtilsInternal.checkIfRestrictionEnforced(mContext,
                UserManager.DISALLOW_CONFIG_LOCATION, UserHandle.myUserId());
    }

    @Override
    public boolean useAdminDisabledSummary() {
        return false;
    }


    @Override
    public String getAttrUserRestriction() {
        return null;
    }


    @Override
    protected void update() {
        boolean locationEnabled = mContext.getSystemService(LocationManager.class)
                .isLocationEnabled();
        if (!mDisabledByAdmin) {
            if (locationEnabled) {
                mPreferenceCompat.setValue(LOCATION_MODE_WIFI);
            } else {
                mPreferenceCompat.setValue(LOCATION_MODE_OFF);
            }
        } else {
            if (locationEnabled) {
                mPreferenceCompat.setSummary(
                        ResourcesUtil.getString(mContext, "location_mode_wifi_description"));
            } else {
                mPreferenceCompat.setSummary(
                        ResourcesUtil.getString(mContext, "off"));
            }
        }
    }

    private boolean hasUserRestriction() {
        final UserManager um = UserManager.get(mContext);
        return um.hasUserRestriction(UserManager.DISALLOW_SHARE_LOCATION)
                || um.hasUserRestriction(UserManager.DISALLOW_CONFIG_LOCATION);
    }
}
