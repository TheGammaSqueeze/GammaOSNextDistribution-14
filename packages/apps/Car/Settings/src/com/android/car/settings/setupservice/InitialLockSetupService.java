/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.car.settings.setupservice;

import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.UserHandle;

import com.android.car.settings.R;
import com.android.car.settings.common.Logger;
import com.android.car.settings.security.PasswordHelper;
import com.android.car.setupwizardlib.IInitialLockSetupService;
import com.android.car.setupwizardlib.InitialLockSetupConstants;
import com.android.car.setupwizardlib.InitialLockSetupConstants.LockTypes;
import com.android.car.setupwizardlib.InitialLockSetupConstants.SetLockCodes;
import com.android.car.setupwizardlib.InitialLockSetupConstants.ValidateLockFlags;
import com.android.car.setupwizardlib.LockConfig;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockPatternView;
import com.android.internal.widget.LockscreenCredential;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Service that is used by Setup Wizard (exclusively) to set the initial lock screen.
 *
 * <p>This service provides functionality to get the lock config state, check if a password is
 * valid based on the Settings defined password criteria, and save a lock if there is not one
 * already saved. The interface for these operations is found in the {@link
 * IInitialLockSetupService}.
 */
public class InitialLockSetupService extends Service {

    private static final Logger LOG = new Logger(InitialLockSetupService.class);
    private static final String SET_LOCK_PERMISSION = "com.android.car.settings.SET_INITIAL_LOCK";

    private final InitialLockSetupServiceImpl mIInitialLockSetupService =
            new InitialLockSetupServiceImpl();

    /**
     * Will return an {@link IBinder} for the service unless either the caller does not have the
     * appropriate permissions or a lock has already been set on the device. In this case, the
     * service will return {@code null}.
     */
    @Override
    public IBinder onBind(Intent intent) {
        LOG.v("onBind");
        if (checkCallingOrSelfPermission(SET_LOCK_PERMISSION)
                != PackageManager.PERMISSION_GRANTED) {
            // Check permission as a failsafe.
            return null;
        }
        int userId = UserHandle.myUserId();
        LockPatternUtils lockPatternUtils = new LockPatternUtils(getApplicationContext());
        // Deny binding if there is an existing lock.
        if (lockPatternUtils.getKeyguardStoredPasswordQuality(userId)
                != DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED) {
            LOG.v("Rejecting binding, lock exists");
            return null;
        }
        return mIInitialLockSetupService;
    }

    // Implementation of the service binder interface.
    private class InitialLockSetupServiceImpl extends IInitialLockSetupService.Stub {

        @Override
        public int getServiceVersion() {
            return InitialLockSetupConstants.LIBRARY_VERSION;
        }

        @Override
        public LockConfig getLockConfig(@LockTypes int lockType) {
            // All lock types currently are configured the same.
            switch (lockType) {
                case LockTypes.PASSWORD:
                    // fall through
                case LockTypes.PIN:
                    // fall through
                case LockTypes.PATTERN:
                    return new LockConfig(/* enabled= */ true,
                            LockPatternUtils.MIN_LOCK_PATTERN_SIZE);
            }
            return null;
        }

        private LockscreenCredential createLockscreenCredential(
                @LockTypes int lockType, byte[] password) {
            switch (lockType) {
                case LockTypes.PASSWORD:
                    String passwordStr = new String(password, StandardCharsets.UTF_8);
                    return LockscreenCredential.createPassword(passwordStr);
                case LockTypes.PIN:
                    String pinStr = new String(password, StandardCharsets.UTF_8);
                    return LockscreenCredential.createPin(pinStr);
                case LockTypes.PATTERN:
                    List<LockPatternView.Cell> pattern =
                            LockPatternUtils.byteArrayToPattern(password,
                                    LockPatternUtils.PATTERN_SIZE_DEFAULT);
                    return LockscreenCredential.createPattern(pattern,
                            LockPatternUtils.PATTERN_SIZE_DEFAULT);
                default:
                    LOG.e("Unrecognized lockscreen credential type: " + lockType);
                    return null;
            }
        }

        @Override
        @ValidateLockFlags
        public int checkValidLock(@LockTypes int lockType, byte[] password) {
            try (LockscreenCredential credential = createLockscreenCredential(lockType, password)) {
                if (credential == null) {
                    return ValidateLockFlags.INVALID_GENERIC;
                }
                PasswordHelper helper = new PasswordHelper(getApplicationContext(), getUserId());
                if (!helper.validateCredential(credential)) {
                    return ValidateLockFlags.INVALID_GENERIC;
                }
                return 0;
            }
        }

        @Override
        @SetLockCodes
        public int setLock(@LockTypes int lockType, byte[] password) {
            int userId = UserHandle.myUserId();
            LockPatternUtils lockPatternUtils = new LockPatternUtils(
                    InitialLockSetupService.this.getApplicationContext());
            int currentPassword = lockPatternUtils.getKeyguardStoredPasswordQuality(userId);
            if (currentPassword != DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED) {
                LOG.v("Credential already set, rejecting call to setLock");
                return SetLockCodes.FAIL_LOCK_EXISTS;
            }
            try (LockscreenCredential credential = createLockscreenCredential(lockType, password)) {
                if (credential == null) {
                    return SetLockCodes.FAIL_LOCK_INVALID;
                }
                PasswordHelper helper = new PasswordHelper(getApplicationContext(), userId);
                if (!helper.validateCredential(credential)) {
                    LOG.v("Credential is not valid, rejecting call to setLock");
                    return SetLockCodes.FAIL_LOCK_INVALID;
                }
                if (!lockPatternUtils.setLockCredential(credential,
                            /* savedCredential= */ LockscreenCredential.createNone(), userId)) {
                    return SetLockCodes.FAIL_LOCK_GENERIC;
                }
                return SetLockCodes.SUCCESS;
            } catch (Exception e) {
                LOG.e("Save lock exception", e);
                return SetLockCodes.FAIL_LOCK_GENERIC;
            }
        }

        @Override
        public String checkValidLockAndReturnError(@LockTypes int lockType,
                byte[] credentialBytes) {
            try (LockscreenCredential credential =
                    createLockscreenCredential(lockType, credentialBytes)) {
                if (credential == null) {
                    return getApplicationContext().getString(R.string.locktype_unavailable);
                }
                PasswordHelper helper = new PasswordHelper(getApplicationContext(), getUserId());
                helper.validateCredential(credential);
                return helper.getCredentialValidationErrorMessages();
            }
        }
    }
}
