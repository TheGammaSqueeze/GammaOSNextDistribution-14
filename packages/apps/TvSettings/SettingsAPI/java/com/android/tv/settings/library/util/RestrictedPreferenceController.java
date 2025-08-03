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

package com.android.tv.settings.library.util;

import android.content.Context;
import android.os.UserHandle;
import android.text.TextUtils;

import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceCompatManager;
import com.android.tv.settings.library.settingslib.RestrictedLockUtils;
import com.android.tv.settings.library.settingslib.RestrictedLockUtilsInternal;

/** Abstract PreferenceController to handle restricted preference businesss logic. */
public abstract class RestrictedPreferenceController extends AbstractPreferenceController {
    protected boolean mDisabledByAdmin;
    private String mAttrUserRestriction;
    private RestrictedLockUtils.EnforcedAdmin mEnforcedAdmin;
    private boolean mUseAdminDisabledSummary = false;

    public RestrictedPreferenceController(Context context,
            UIUpdateCallback callback, int stateIdentifier,
            PreferenceCompatManager prefCompatManager) {
        super(context, callback, stateIdentifier, prefCompatManager);
        mAttrUserRestriction = getAttrUserRestriction();
        mUseAdminDisabledSummary = useAdminDisabledSummary();
        // If the system has set the user restriction, then we shouldn't add the padlock.
        if (RestrictedLockUtilsInternal.hasBaseUserRestriction(mContext, mAttrUserRestriction,
                UserHandle.myUserId())) {
            mAttrUserRestriction = null;
            return;
        }
    }

    public RestrictedPreferenceController(Context context,
            UIUpdateCallback callback, int stateIdentifier,
            PreferenceCompatManager prefCompatManager, String[] key) {
        super(context, callback, stateIdentifier, prefCompatManager, key);
        mAttrUserRestriction = getAttrUserRestriction();
        mUseAdminDisabledSummary = useAdminDisabledSummary();
        // If the system has set the user restriction, then we shouldn't add the padlock.
        if (RestrictedLockUtilsInternal.hasBaseUserRestriction(mContext, mAttrUserRestriction,
                UserHandle.myUserId())) {
            mAttrUserRestriction = null;
            return;
        }
    }

    @Override
    protected void init() {
        if (mAttrUserRestriction != null) {
            checkRestrictionAndSetDisabled(mAttrUserRestriction, UserHandle.myUserId());
        }
        update();
    }

    public void checkRestrictionAndSetDisabled(String userRestriction, int userId) {
        RestrictedLockUtils.EnforcedAdmin
                admin = RestrictedLockUtilsInternal.checkIfRestrictionEnforced(mContext,
                userRestriction, userId);
        setDisabledByAdmin(admin);
    }

    @Override
    protected void update() {
        if (mDisabledByAdmin) {
            mPreferenceCompat.setEnabled(true);
        }
        if (mUseAdminDisabledSummary) {
            String disabledText = ResourcesUtil.getString(
                    mContext, "disabled_by_admin_summary_text");
            if (mDisabledByAdmin) {
                mPreferenceCompat.setSummary(disabledText);
            } else if (TextUtils.equals(disabledText, mPreferenceCompat.getSummary())) {
                // It's previously set to disabled text, clear it.
                mPreferenceCompat.setSummary(null);
            }
        }
    }

    @Override
    public boolean handlePreferenceTreeClick(boolean status) {
        if (mDisabledByAdmin) {
            RestrictedLockUtils.sendShowAdminSupportDetailsIntent(mContext, mEnforcedAdmin);
            return true;
        }
        return false;
    }


    @Override
    public boolean isAvailable() {
        return true;
    }

    public void setEnabled(boolean enabled) {
        if (enabled && isDisabledByAdmin()) {
            boolean changed = setDisabledByAdmin(null);
            if (changed) {
                notifyChange();
            }
            return;
        }
    }

    /**
     * Disable this preference based on the enforce admin.
     *
     * @param admin details of the admin who enforced the restriction. If it is
     *              {@code null}, then this preference will be enabled. Otherwise, it will be
     *              disabled.
     *              Only gray out the preference which is not {@link RestrictedTopLevelPreference}.
     * @return whether to notify for update.
     */
    public boolean setDisabledByAdmin(RestrictedLockUtils.EnforcedAdmin admin) {
        final boolean disabled = (admin != null);
        mEnforcedAdmin = admin;
        boolean changed = false;
        if (mDisabledByAdmin != disabled) {
            mDisabledByAdmin = disabled;
            changed = true;
        }
        mPreferenceCompat.setEnabled(!disabled);
        mPreferenceCompat.setDisabledByAdmin(mDisabledByAdmin);
        return changed;
    }

    public boolean isDisabledByAdmin() {
        return mDisabledByAdmin;
    }

    public abstract boolean useAdminDisabledSummary();

    public void setUseAdminDisabledSummary(boolean useAdminDisabledSummary) {
        mUseAdminDisabledSummary = useAdminDisabledSummary;
    }

    public abstract String getAttrUserRestriction();

}
