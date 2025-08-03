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
package com.android.car.settings.datetime;

import static android.os.UserManager.DISALLOW_CONFIG_DATE_TIME;

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.AVAILABLE_FOR_VIEWING;
import static com.android.car.settings.enterprise.ActionDisabledByAdminDialogFragment.DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG;
import static com.android.car.settings.enterprise.EnterpriseUtils.hasUserRestrictionByDpm;

import android.content.Context;
import android.os.UserManager;
import android.widget.Toast;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.enterprise.EnterpriseUtils;

/**
 * Generic helper methods for this package.
 */
 // TODO(b/186905050): add unit tests for this class and {@code PreferenceController} that uses
 // this class's methods.
public final class DatetimeUtils {

    /**
     * Returns {@code PreferenceController.AVAILABLE_FOR_VIEWING} when there is
     * {@code DISALLOW_CONFIG_DATE_TIME} on the user. Otherwise, returns
     * {@code PreferenceController.AVAILABLE}.
     */
    public static int getAvailabilityStatus(Context context) {
        if (context.getSystemService(UserManager.class)
                .hasUserRestriction(DISALLOW_CONFIG_DATE_TIME)) {
            return AVAILABLE_FOR_VIEWING;
        }
        return AVAILABLE;
    }

    /**
     * Shows {@code ActionDisabledByAdminDialog} when the action is disallowed by
     * a device owner or a profile owner. Otherwise, a {@code Toast} will be shwon to inform the
     * user that the action is disabled.
     */
    public static void runClickableWhileDisabled(Context context,
            FragmentController fragmentController) {
        if (hasUserRestrictionByDpm(context, DISALLOW_CONFIG_DATE_TIME)) {
            showActionDisabledByAdminDialog(context, fragmentController);
        } else {
            Toast.makeText(context, context.getString(R.string.action_unavailable),
                    Toast.LENGTH_LONG).show();
        }
    }

    private static void showActionDisabledByAdminDialog(Context context,
            FragmentController fragmentController) {
        fragmentController.showDialog(
                EnterpriseUtils.getActionDisabledByAdminDialog(context,
                        DISALLOW_CONFIG_DATE_TIME),
                DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG);
    }

    private DatetimeUtils() {
        throw new UnsupportedOperationException("Provides only static methods");
    }
}
