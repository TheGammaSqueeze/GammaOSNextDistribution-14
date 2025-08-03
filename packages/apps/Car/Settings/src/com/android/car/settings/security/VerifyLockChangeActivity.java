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

import android.content.Intent;
import android.os.UserHandle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.android.car.settings.common.BaseCarSettingsActivity;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockscreenCredential;

/**
 * Activity for verifying current screen lock before allowing it to be changed.
 *
 * This activity differs from {@link CheckLockActivity}, as it provides screen lock data to the
 * caller. Because of this sensitive data passing, this activity should not be externally
 * accessible - external applications should use either {@link CheckLockActivity} or
 * {@link SettingsScreenLockActivity} (depending on their use case).
 */
public class VerifyLockChangeActivity extends BaseCarSettingsActivity implements CheckLockListener {
    private int mPasswordQuality;

    @Override
    @Nullable
    protected Fragment getInitialFragment() {
        Fragment currentFragment = getCurrentFragment();
        if (currentFragment != null) {
            return currentFragment;
        }

        mPasswordQuality = new LockPatternUtils(this).getKeyguardStoredPasswordQuality(
                UserHandle.myUserId());

        Fragment lockFragment = ConfirmPasswordFragmentFactory.getFragment(/* context= */ this);
        if (lockFragment == null) {
            // User has not set a password
            setResult(RESULT_OK);
            finish();
        }
        return lockFragment;
    }

    @Override
    public void onLockVerified(LockscreenCredential lock) {
        Intent data = new Intent();
        data.putExtra(PasswordHelper.EXTRA_CURRENT_SCREEN_LOCK, lock);
        data.putExtra(PasswordHelper.EXTRA_CURRENT_PASSWORD_QUALITY, mPasswordQuality);
        setResult(RESULT_OK, data);
        finish();
    }

    @Override
    public void onDestroy() {
        PasswordHelper.zeroizeCredentials();
        super.onDestroy();
    }
}
