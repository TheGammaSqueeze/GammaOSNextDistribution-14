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

package com.android.tv.settings.accounts;

import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import androidx.annotation.IntDef;

import com.android.settingslib.RestrictedLockUtils;
import com.android.settingslib.RestrictedLockUtilsInternal;
import com.android.tv.settings.MainFragment;
import com.android.tv.settings.R;
import com.android.tv.settings.overlay.FlavorUtils;
import com.android.tv.settings.util.SliceUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Utility methods for accounts settings */
public class AccountsUtil {

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({ACCOUNTS_FRAGMENT_DEFAULT,
            ACCOUNTS_SLICE_FRAGMENT,
            ACCOUNTS_BASIC_MODE_FRAGMENT,
            ACCOUNTS_SYSTEM_INTENT,
            ACCOUNTS_FRAGMENT_RESTRICTED})
    public @interface AccountsFragmentType {}
    public static final int ACCOUNTS_FRAGMENT_DEFAULT = 0;
    public static final int ACCOUNTS_SLICE_FRAGMENT = 1;
    public static final int ACCOUNTS_BASIC_MODE_FRAGMENT = 2;
    public static final int ACCOUNTS_SYSTEM_INTENT = 3;
    public static final int ACCOUNTS_FRAGMENT_RESTRICTED = 4;

    private static final String ACTION_ACCOUNTS = "com.android.tv.settings.ACCOUNTS";
    private static final String TAG = "AccountsUtil";

    /**
     *  Get the correct accounts settings fragment based on restrictions and other features.
     * @param context
     * @return the accounts fragment to launch
     */
    public static @AccountsFragmentType int getAccountsFragmentToLaunch(Context context) {
        if (AccountsUtil.isAdminRestricted(context)) {
            return ACCOUNTS_FRAGMENT_RESTRICTED;
        }

        try {
            if (FlavorUtils.getFeatureFactory(context).getBasicModeFeatureProvider()
                .isBasicMode(context)) {
                return ACCOUNTS_BASIC_MODE_FRAGMENT;
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to determine basic mode", e);
        }

        // If the intent can be handled, use it.
        Intent accountsIntent = new Intent(ACTION_ACCOUNTS);
        if (MainFragment.systemIntentIsHandled(context, accountsIntent) != null) {
            return ACCOUNTS_SYSTEM_INTENT;
        }

        // If a slice is available, use it to display the accounts settings, otherwise fall back to
        // use AccountsFragment.
        String uri = context.getString(R.string.account_slice_uri);
        if (SliceUtils.isSliceProviderValid(context, uri)) {
            return ACCOUNTS_SLICE_FRAGMENT;
        }

        return ACCOUNTS_FRAGMENT_DEFAULT;
    }

    /**
     * Verifies if the no_modify_accounts restriction is active
     * @param context
     * @return if the no_modify_accounts restriction is active
     */
    public static boolean isAdminRestricted(Context context) {
        RestrictedLockUtils.EnforcedAdmin admin =
                RestrictedLockUtilsInternal.checkIfRestrictionEnforced(context,
                        UserManager.DISALLOW_MODIFY_ACCOUNTS, UserHandle.myUserId());
        return (admin != null);
    }
}
