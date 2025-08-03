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

package com.android.tv.settings;

import android.content.Context;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;

import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.settingslib.RestrictedLockUtils.EnforcedAdmin;
import com.android.settingslib.RestrictedLockUtilsInternal;
import com.android.settingslib.RestrictedPreference;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Adapter that wraps a regular Preference, and restricts the preference if user restrictions apply.
 *
 * <p>
 * If the user is restricted by an admin, the preference is replaced by a RestrictedPreference.
 * Some state from the original preference is copied, such as title, summary, and visibility.
 * The restricted preference shows an explanatory message dialog on click, and does not show a
 * preview panel.
 *
 * <p>
 * If a base user restriction applies, the original preference is disabled.
 *
 * <p>
 * Updates to the preference should be made through {@link #updatePreference(Consumer)}, so that the
 * restricted preference can be updated after changes, or the original preference can be kept
 * disabled. Otherwise, {@link #updatePreference()} can be called after direct edits to
 * the underlying preference.
 *
 * @param <T> the type of the wrapped preference
 */
public class RestrictedPreferenceAdapter<T extends Preference> {
    private final Context mContext;
    private final T mOriginalPreference;
    private final List<String> mUserRestrictions;
    private boolean mRestricted;
    private EnforcedAdmin mEnforcingAdmin;
    private RestrictedPreference mRestrictedPreference;
    private boolean mIsApSaved;

    public RestrictedPreferenceAdapter(Context context, T originalPreference,
            List<String> userRestrictions) {
        this.mContext = context;
        this.mOriginalPreference = originalPreference;
        this.mUserRestrictions = userRestrictions;

        this.mRestricted = isRestricted();
        this.mEnforcingAdmin = isRestrictedByAdmin();

        if (mEnforcingAdmin != null) {
            mRestrictedPreference = new RestrictedPreference(mContext);
        } else {
            mRestrictedPreference = null;
        }
    }

    public RestrictedPreferenceAdapter(Context context, T originalPreference,
            String userRestriction) {
        this(context, originalPreference, Collections.singletonList(userRestriction));
    }

    /**
     * Create a RestrictedPreferenceAdapter from a preference.
     *
     * @param <T> the type of the preference
     * @param preference the preference to adapt
     * @param userRestriction the user restriction to enforce for this preference
     */
    public static <T extends Preference> RestrictedPreferenceAdapter<T> adapt(T preference,
            String userRestriction) {
        RestrictedPreferenceAdapter<T> adapter = new RestrictedPreferenceAdapter<T>(
                preference.getContext(), preference, userRestriction);
        adapter.setup();
        return adapter;
    }

    private void setup() {
        if (mRestricted) {
            mOriginalPreference.setEnabled(false);

            if (mEnforcingAdmin != null) {
                updateRestrictedPreference();
                replacePreference();
            }
        }
    }

    /**
     * Returns {@code true} if a restriction applies to this preference, {@code false} otherwise.
     */
    public boolean isRestricted() {
        if (mUserRestrictions == null) {
            return false;
        }

        UserManager userManager = UserManager.get(mContext);
        for (String userRestriction : mUserRestrictions) {
            if (userManager.hasUserRestriction(userRestriction)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns {@code true} if given restriction applies to this preference,
     * {@code false} otherwise.
     */
    public boolean isRestricted(String restriction) {
        UserManager userManager = UserManager.get(mContext);
        return userManager.hasUserRestriction(restriction);
    }

    private EnforcedAdmin isRestrictedByAdmin() {
        if (mUserRestrictions == null) {
            return null;
        }

        for (String userRestriction : mUserRestrictions) {
            EnforcedAdmin admin = RestrictedLockUtilsInternal.checkIfRestrictionEnforced(mContext,
                    userRestriction, UserHandle.myUserId());
            if (admin != null) {
                return admin;
            }
        }
        return null;
    }

    private void replacePreference() {
        if (mRestrictedPreference == null) {
            return;
        }

        final String key = mOriginalPreference.getKey();
        if (TextUtils.isEmpty(key)) {
            throw new IllegalArgumentException("Can't replace a preference without a key");
        }
        final PreferenceGroup screen = mOriginalPreference.getParent();
        final int order = mOriginalPreference.getOrder();
        mRestrictedPreference.setOrder(order);
        screen.removePreference(mOriginalPreference);
        screen.addPreference(mRestrictedPreference);
    }

    /** Set access point saved or not. */
    public void setApSaved(boolean saved) {
        mIsApSaved = saved;
    }

    /**
     * Returns the preference to be inserted into the preference screen.
     *
     * If the preference is unrestricted, this returns the underlying original preference.
     * If the preference is restricted, this returns the RestrictedPreference that replaces
     * the original preference.
     */
    public Preference getPreference() {
        if (mRestrictedPreference != null && (isRestricted(UserManager.DISALLOW_CONFIG_WIFI)
                || (isRestricted(UserManager.DISALLOW_ADD_WIFI_CONFIG) && !mIsApSaved))) {
            return mRestrictedPreference;
        }

        return mOriginalPreference;
    }

    /** Returns the original preference. */
    public Preference getOriginalPreference() {
        return mOriginalPreference;
    }

    /**
     * Update the preference.
     * The update function will be called with the adapted preference, changes will be reflected
     * on the restricted preference.
     *
     * @param updateFn function that is passed the original preference to make updates to
     */
    public void updatePreference(Consumer<T> updateFn) {
        updateFn.accept(mOriginalPreference);
        updatePreference();
    }

    /**
     * Update the preference.
     * This copies some attributes from the original preference to the restricted preference.
     * Call this after making direct changes to the original preference.
     */
    public void updatePreference() {
        mRestricted = isRestricted();
        mEnforcingAdmin = isRestrictedByAdmin();
        if (mEnforcingAdmin != null) {
            if (mRestrictedPreference == null) {
                mRestrictedPreference = new RestrictedPreference(mContext);
            }
        } else {
            mRestrictedPreference = null;
        }
        if (mRestricted && isRestricted(UserManager.DISALLOW_CONFIG_WIFI)) {
            mOriginalPreference.setEnabled(false);
        } else {
            mOriginalPreference.setEnabled(true);
        }
        updateRestrictedPreference();
    }

    private void updateRestrictedPreference() {
        if (mRestrictedPreference == null) {
            return;
        }
        mRestrictedPreference.setKey(mOriginalPreference.getKey());
        mRestrictedPreference.setOrder(mOriginalPreference.getOrder());
        mRestrictedPreference.setTitle(mOriginalPreference.getTitle());
        mRestrictedPreference.setSummary(mOriginalPreference.getSummary());
        mRestrictedPreference.setIcon(mOriginalPreference.getIcon());
        mRestrictedPreference.setVisible(mOriginalPreference.isVisible());
        mRestrictedPreference.setDisabledByAdmin(isRestrictedByAdmin());
    }
}
