/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.car.settings.security;

import static android.os.UserManager.DISALLOW_CONFIG_CREDENTIALS;

import static com.android.car.settings.enterprise.ActionDisabledByAdminDialogFragment.DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.os.UserManager;

import androidx.preference.Preference;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;
import com.android.car.settings.enterprise.EnterpriseUtils;

/** Controls whether the option to reset credentials is shown to the user. */
public class CredentialsResetPreferenceController extends PreferenceController<Preference> {

    private final UserManager mUserManager;

    public CredentialsResetPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mUserManager = UserManager.get(context);
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    @Override
    protected void onCreateInternal() {
        super.onCreateInternal();
        setClickableWhileDisabled(getPreference(), /* clickable= */ true, p -> {
            if (EnterpriseUtils
                    .hasUserRestrictionByDpm(getContext(), DISALLOW_CONFIG_CREDENTIALS)) {
                showActionDisabledByAdminDialog();
            }
        });
    }

    @Override
    public int getDefaultAvailabilityStatus() {
        if (EnterpriseUtils.hasUserRestrictionByUm(getContext(), DISALLOW_CONFIG_CREDENTIALS)) {
            return DISABLED_FOR_PROFILE;
        }
        if (EnterpriseUtils.hasUserRestrictionByDpm(getContext(), DISALLOW_CONFIG_CREDENTIALS)) {
            return AVAILABLE_FOR_VIEWING;
        }
        return AVAILABLE;
    }

    private void showActionDisabledByAdminDialog() {
        getFragmentController().showDialog(
                EnterpriseUtils.getActionDisabledByAdminDialog(getContext(),
                        DISALLOW_CONFIG_CREDENTIALS),
                DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG);
    }
}
