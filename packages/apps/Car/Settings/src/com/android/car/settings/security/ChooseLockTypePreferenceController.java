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

package com.android.car.settings.security;

import static android.app.Activity.RESULT_OK;

import static com.android.internal.widget.LockPatternUtils.CREDENTIAL_TYPE_NONE;
import static com.android.internal.widget.LockPatternUtils.CREDENTIAL_TYPE_PASSWORD;
import static com.android.internal.widget.LockPatternUtils.CREDENTIAL_TYPE_PATTERN;
import static com.android.internal.widget.LockPatternUtils.CREDENTIAL_TYPE_PIN;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.common.ActivityResultCallback;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.PreferenceController;
import com.android.internal.widget.LockPatternUtils;

/**
 * Business logic for the choose lock type preference. This preference will launch a
 * ScreenLockActivity to verify the current lock and then launch a new ChooseLockTypeFragment
 * instance with the provided result data.
 */
public class ChooseLockTypePreferenceController extends PreferenceController<Preference>
        implements ActivityResultCallback {

    private static final Logger LOG = new Logger(ChooseLockTypePreferenceController.class);
    @VisibleForTesting
    static final int LOCK_CHECK = 1;

    private final UserManager mUserManager;
    private LockPatternUtils mLockPatternUtils;

    public ChooseLockTypePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        this(context, preferenceKey, fragmentController, uxRestrictions, UserManager.get(context),
                new LockPatternUtils(context));
    }

    @VisibleForTesting
    ChooseLockTypePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions,
            UserManager userManager, LockPatternUtils lockPatternUtils) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mUserManager = userManager;
        mLockPatternUtils = lockPatternUtils;
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    @Override
    protected void updateState(Preference preference) {
        int passwordQuality = mLockPatternUtils.getCredentialTypeForUser(UserHandle.myUserId());

        switch (passwordQuality) {
            case CREDENTIAL_TYPE_NONE:
                preference.setSummary(R.string.security_lock_none);
                break;
            case CREDENTIAL_TYPE_PATTERN:
                preference.setSummary(R.string.security_lock_pattern);
                break;
            case CREDENTIAL_TYPE_PIN:
                preference.setSummary(R.string.security_lock_pin);
                break;
            case CREDENTIAL_TYPE_PASSWORD:
                preference.setSummary(R.string.security_lock_password);
                break;
            default:
                preference.setSummary("");
                break;
        }
    }

    @Override
    protected boolean handlePreferenceClicked(Preference preference) {
        Intent intent = new Intent(getContext(), VerifyLockChangeActivity.class);
        getFragmentController().startActivityForResult(intent, LOCK_CHECK, this);
        return true;
    }

    @Override
    public void processActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode != LOCK_CHECK) {
            LOG.e("Got unknown activity result");
            return;
        }
        if (resultCode != RESULT_OK) {
            return;
        }
        Fragment fragment = new ChooseLockTypeFragment();
        Bundle bundle = fragment.getArguments();
        if (bundle == null) {
            bundle = new Bundle();
        }
        if (data != null) {
            bundle.putParcelable(PasswordHelper.EXTRA_CURRENT_SCREEN_LOCK,
                    data.getParcelableExtra(PasswordHelper.EXTRA_CURRENT_SCREEN_LOCK));
            bundle.putInt(PasswordHelper.EXTRA_CURRENT_PASSWORD_QUALITY,
                    data.getIntExtra(PasswordHelper.EXTRA_CURRENT_PASSWORD_QUALITY, -1));
        }
        fragment.setArguments(bundle);
        getFragmentController().launchFragment(fragment);
    }

    @Override
    public int getDefaultAvailabilityStatus() {
        return mUserManager.isGuestUser() ? DISABLED_FOR_PROFILE : AVAILABLE;
    }
}
