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

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.android.car.settings.common.BaseCarSettingsActivity;

/**
 * Full screen wrapper activity for setting screen locks. Intended to be used by external intents
 * to set up or change the user's screen lock (not used by settings itself).
 */
public class SettingsScreenLockActivity extends BaseCarSettingsActivity {

    private static final int LOCK_CHECK = 14;

    @Override
    @Nullable
    protected Fragment getInitialFragment() {
        return null;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = new Intent(this, VerifyLockChangeActivity.class);
        startActivityForResult(intent, LOCK_CHECK);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != LOCK_CHECK) {
            return;
        }
        if (resultCode != Activity.RESULT_OK) {
            finish();
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
                    data.getIntExtra(PasswordHelper.EXTRA_CURRENT_PASSWORD_QUALITY,
                            DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED));
        }
        fragment.setArguments(bundle);
        launchFragmentInternal(fragment);
    }
}
