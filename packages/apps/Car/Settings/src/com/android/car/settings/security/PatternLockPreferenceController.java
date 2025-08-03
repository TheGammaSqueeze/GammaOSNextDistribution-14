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

package com.android.car.settings.security;

import android.app.admin.DevicePolicyManager;
import android.app.admin.PasswordMetrics;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;

import androidx.preference.Preference;

import com.android.car.settings.common.FragmentController;
import com.android.internal.widget.LockPatternUtils;

/** Business logic for the lock pattern picker preference. */
public class PatternLockPreferenceController extends LockTypeBasePreferenceController {

    private static final int[] ALLOWED_PASSWORD_QUALITIES =
            new int[]{DevicePolicyManager.PASSWORD_QUALITY_SOMETHING};

    private final LockPatternUtils mLockPatternUtils;

    public PatternLockPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mLockPatternUtils = new LockPatternUtils(context);
    }

    @Override
    protected Intent activityToOpen() {
        return new Intent(getContext(), ChooseLockPatternActivity.class);
    }

    @Override
    protected int[] allowedPasswordQualities() {
        return ALLOWED_PASSWORD_QUALITIES;
    }

    @Override
    protected void updateState(Preference preference) {
        super.updateState(preference);

        PasswordMetrics metrics = mLockPatternUtils.getRequestedPasswordMetrics(
                UserHandle.myUserId(), /* deviceWideOnly= */ false);
        preference.setEnabled(metrics.credType <= LockPatternUtils.CREDENTIAL_TYPE_PATTERN);
    }
}
