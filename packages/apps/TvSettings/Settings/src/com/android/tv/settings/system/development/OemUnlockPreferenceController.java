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

package com.android.tv.settings.system.development;

import android.content.Context;
import android.os.Process;
import android.os.SystemProperties;
import android.os.UserManager;
import android.service.oemlock.OemLockManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.preference.TwoStatePreference;

import com.android.tv.settings.R;

public class OemUnlockPreferenceController implements OemUnlockDialog.Callback {

    private static final String TAG = OemUnlockPreferenceController.class.getSimpleName();

    private final OemLockManager mOemLockManager;
    private final UserManager mUserManager;
    private final TwoStatePreference mPreference;

    public OemUnlockPreferenceController(Context context, TwoStatePreference preference) {
        if (!TextUtils.equals(SystemProperties.get("ro.oem_unlock_supported", "0"), "1")) {
            mOemLockManager = null;
            Log.w(TAG, "oem_unlock not supported.");
        } else {
            mOemLockManager = (OemLockManager) context.getSystemService(Context.OEM_LOCK_SERVICE);
        }
        mUserManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
        mPreference = preference;
        updateState();
    }

    public TwoStatePreference getPreference() {
        return mPreference;
    }

    public boolean onPreferenceClick() {
        if (mOemLockManager == null) {
            return false;
        }
        final boolean allowed = mPreference.isChecked();
        if (allowed) {
            // Signal to caller to start the confirm dialog.
            mPreference.setChecked(false);
            return false;
        } else {
            mPreference.setChecked(false);
            mOemLockManager.setOemUnlockAllowedByUser(false);
            return true;
        }
    }

    @Override
    public void onOemUnlockConfirm() {
        if (shouldEnableOemUnlockPreference()) {
            mOemLockManager.setOemUnlockAllowedByUser(true);
            mPreference.setChecked(true);
        }
    }

    public void updateState() {
        mPreference.setEnabled(shouldEnableOemUnlockPreference());
        mPreference.setChecked(isOemUnlockedAllowed());
        mPreference.setSummary(getPreferenceSummary());
    }

    private int getPreferenceSummary() {
        if (isRestricted()) {
            return R.string.disabled_by_policy_title;
        } else if (isBootloaderUnlocked()) {
            return R.string.oem_unlock_enable_disabled_summary_bootloader_unlocked;
        } else if (!isOemUnlockAllowedByUserAndCarrier()) {
            return R.string.oem_unlock_enable_disabled_summary_connectivity_or_locked;
        } else {
            return R.string.oem_unlock_enable_summary;
        }
    }

    private boolean shouldEnableOemUnlockPreference() {
        return !isRestricted() && !isBootloaderUnlocked() && isOemUnlockAllowedByUserAndCarrier();
    }

    /**
     * Returns {@code true} if OEM unlocking has been disabled by device policy.
     */
    private boolean isRestricted() {
        return mUserManager.hasUserRestriction(UserManager.DISALLOW_OEM_UNLOCK)
                || mUserManager.hasUserRestriction(UserManager.DISALLOW_FACTORY_RESET);
    }

    /**
     * Returns {@code true} if the bootloader has been unlocked.
     */
    private boolean isBootloaderUnlocked() {
        return mOemLockManager != null && mOemLockManager.isDeviceOemUnlocked();
    }

    /**
     * Returns whether OEM unlock is allowed by the user and carrier.
     */
    private boolean isOemUnlockAllowedByUserAndCarrier() {
        return mOemLockManager != null
                && mOemLockManager.isOemUnlockAllowedByCarrier()
                && !mUserManager.hasBaseUserRestriction(
                        UserManager.DISALLOW_FACTORY_RESET,
                        Process.myUserHandle());
    }

    private boolean isOemUnlockedAllowed() {
        return mOemLockManager != null && mOemLockManager.isOemUnlockAllowed();
    }

}
