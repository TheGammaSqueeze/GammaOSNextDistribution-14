/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.car.settings.system;

import static android.os.UserManager.DISALLOW_NETWORK_RESET;

import static com.android.car.settings.enterprise.ActionDisabledByAdminDialogFragment.DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG;
import static com.android.car.settings.enterprise.EnterpriseUtils.hasUserRestrictionByDpm;
import static com.android.car.settings.enterprise.EnterpriseUtils.hasUserRestrictionByUm;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.widget.Toast;

import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;
import com.android.car.settings.enterprise.EnterpriseUtils;

/** Controller which determines if network reset should be displayed based on user status. */
public class ResetNetworkEntryPreferenceController extends PreferenceController<Preference> {

    public ResetNetworkEntryPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    @Override
    protected void onCreateInternal() {
        super.onCreateInternal();
        setClickableWhileDisabled(getPreference(), /* clickable= */ true, p -> {
            if (hasUserRestrictionByDpm(getContext(), DISALLOW_NETWORK_RESET)) {
                showActionDisabledByAdminDialog();
            } else {
                // Currently, there is no use case when AVAILABLE_FOR_VIEWING other than restricted
                // by DPM
                Toast.makeText(getContext(), getContext().getString(R.string.action_unavailable),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public int getDefaultAvailabilityStatus() {
        if (!isAlwaysAvailableForUser()
                || hasUserRestrictionByUm(getContext(), DISALLOW_NETWORK_RESET)) {
            return DISABLED_FOR_PROFILE;
        }

        if (hasUserRestrictionByDpm(getContext(), DISALLOW_NETWORK_RESET)) {
            return AVAILABLE_FOR_VIEWING;
        }
        return AVAILABLE;
    }

    private boolean isAlwaysAvailableForUser() {
        return EnterpriseUtils.isAdminUser(getContext());
    }

    private void showActionDisabledByAdminDialog() {
        getFragmentController().showDialog(
                EnterpriseUtils.getActionDisabledByAdminDialog(getContext(),
                        DISALLOW_NETWORK_RESET),
                DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG);
    }
}
