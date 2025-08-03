/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.annotation.NonNull;
import android.content.Context;

import com.android.internal.widget.ILockSettings;
import com.android.internal.widget.LockPatternUtils;

public class LockPatternUtilsCompat {
    private static final String HIDDEN_CLASS = "com.android.internal.widget.LockPatternUtils";

    LockPatternUtils mLockPatternUtils;

    public LockPatternUtilsCompat(Context context) {
        mLockPatternUtils = new LockPatternUtils(context);
    }

    public boolean setLockCredential(@NonNull LockscreenCredentialCompat newCredentialCompat,
            @NonNull LockscreenCredentialCompat savedCredential, int userHandle) {
        return mLockPatternUtils.setLockCredential(newCredentialCompat.mLockscreenCredential,
                savedCredential.mLockscreenCredential, userHandle);
    }

    public boolean checkCredential(@NonNull
            LockscreenCredentialCompat credentialCompat, int userId) {
        try {
            return mLockPatternUtils.checkCredential(
                    credentialCompat.mLockscreenCredential, userId, null);
        } catch (LockPatternUtils.RequestThrottledException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean isSecure(int userId) {
        return mLockPatternUtils.isSecure(userId);
    }

    public boolean isLockPatternEnabled(int userId) {
        return mLockPatternUtils.isLockPatternEnabled(userId);
    }

    public boolean isLockPasswordEnabled(int userId) {
        return mLockPatternUtils.isLockPasswordEnabled(userId);
    }

    public static boolean isFileEncryptionEnabled() {
        return LockPatternUtils.isFileEncryptionEnabled();
    }

    public boolean isSeparateProfileChallengeEnabled(int userHandler) {
        return mLockPatternUtils.isSeparateProfileChallengeEnabled(userHandler);
    }

    public abstract class ILockSettingsCompat implements ILockSettings {

    }

    public ILockSettingsCompat getLockSettings() {
        return (ILockSettingsCompat) mLockPatternUtils.getLockSettings();
    }
}
