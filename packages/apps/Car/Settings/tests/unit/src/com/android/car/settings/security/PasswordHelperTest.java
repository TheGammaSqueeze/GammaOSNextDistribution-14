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

package com.android.car.settings.security;

import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_HIGH;
import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_LOW;
import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_MEDIUM;
import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_NONE;

import static com.android.internal.widget.LockPatternUtils.CREDENTIAL_TYPE_NONE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import android.app.admin.DevicePolicyManager;
import android.app.admin.PasswordMetrics;
import android.content.Context;
import android.os.UserHandle;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.R;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockscreenCredential;
import com.android.settingslib.utils.StringUtil;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(AndroidJUnit4.class)
public final class PasswordHelperTest {
    // Never valid
    private static final String SHORT_SEQUENTIAL_PASSWORD = "111";
    private static final String LENGTH_3_PATTERN = "123";
    // Only valid when None/Low complexity
    private static final String MEDIUM_SEQUENTIAL_PASSWORD = "22222";
    private static final String LONG_SEQUENTIAL_PASSWORD = "111111111";
    // Valid for None/Low/Medium complexity
    private static final String MEDIUM_ALPHANUMERIC_PASSWORD = "a11r1";
    private static final String MEDIUM_PIN_PASSWORD = "11397";
    // Valid for all complexities
    private static final String LONG_ALPHANUMERIC_PASSWORD = "a11r1t131";
    private static final String LONG_PIN_PASSWORD = "113982125";
    private static final String LENGTH_4_PATTERN = "1234";

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private int mUserId;
    private PasswordMetrics mPasswordMetrics;

    @Mock
    LockPatternUtils mLockPatternUtils;
    @Mock
    LockscreenCredential mExistingCredential;
    @Rule
    public final MockitoRule rule = MockitoJUnit.rule();

    @Before
    public void setUp() {
        mUserId = UserHandle.myUserId();
        mPasswordMetrics = new PasswordMetrics(CREDENTIAL_TYPE_NONE);
        when(mLockPatternUtils.getPasswordHistoryHashFactor(any(), anyInt()))
                .thenReturn(new byte[0]);
    }

    private LockscreenCredential createPassword(String password) {
        return LockscreenCredential.createPassword(password);
    }

    private LockscreenCredential createPin(String pin) {
        return LockscreenCredential.createPin(pin);
    }

    private LockscreenCredential createPattern(String patternString) {
        return LockscreenCredential.createPattern(LockPatternUtils.byteArrayToPattern(
                    patternString.getBytes()));
    }

    private String passwordTooShort(int lengthRequired) {
        return StringUtil.getIcuPluralsString(mContext, lengthRequired,
                R.string.lockpassword_password_too_short);
    }

    private String pinTooShort(int lengthRequired) {
        return StringUtil.getIcuPluralsString(mContext, lengthRequired,
                R.string.lockpassword_pin_too_short);
    }

    private String patternTooShort(int lengthRequired) {
        return StringUtil.getIcuPluralsString(mContext, lengthRequired,
                R.string.lockpattern_recording_incorrect_too_short);
    }

    private PasswordHelper newPasswordHelper(
            @DevicePolicyManager.PasswordComplexity int complexity) {
        return new PasswordHelper(mContext, mUserId, mLockPatternUtils, mPasswordMetrics,
                complexity);
    }

    private void assertCredentialValid(@DevicePolicyManager.PasswordComplexity int minComplexity,
            LockscreenCredential credential) {
        PasswordHelper helper = newPasswordHelper(minComplexity);

        // Test the overload of validateCredential() that doesn't require the existing credential.
        assertThat(helper.validateCredential(credential)).isTrue();
        assertThat(helper.getCredentialValidationErrorMessages()).isEqualTo("");

        // Test the overload of validateCredential() that requires the existing credential.
        assertThat(helper.validateCredential(credential, mExistingCredential)).isTrue();
        assertThat(helper.getCredentialValidationErrorMessages()).isEqualTo("");

    }

    private void assertCredentialInvalid(@DevicePolicyManager.PasswordComplexity int minComplexity,
            LockscreenCredential credential, String expectedError) {
        PasswordHelper helper = newPasswordHelper(minComplexity);

        // Test the overload of validateCredential() that doesn't require the existing credential.
        assertThat(helper.validateCredential(credential)).isFalse();
        assertThat(helper.getCredentialValidationErrorMessages()).isEqualTo(expectedError);

        // Test the overload of validateCredential() that requires the existing credential.
        assertThat(helper.validateCredential(credential, mExistingCredential)).isFalse();
        assertThat(helper.getCredentialValidationErrorMessages()).isEqualTo(expectedError);
    }

    @Test
    public void passwordComplexityNone_shortSequentialPassword_invalid() {
        assertCredentialInvalid(
                PASSWORD_COMPLEXITY_NONE,
                createPassword(SHORT_SEQUENTIAL_PASSWORD),
                passwordTooShort(4));
    }

    @Test
    public void passwordComplexityNone_mediumSequentialPassword_valid() {
        assertCredentialValid(PASSWORD_COMPLEXITY_NONE, createPassword(MEDIUM_SEQUENTIAL_PASSWORD));
    }

    @Test
    public void passwordComplexityLow_shortSequentialPassword_invalid() {
        assertCredentialInvalid(
                PASSWORD_COMPLEXITY_LOW,
                createPassword(SHORT_SEQUENTIAL_PASSWORD),
                passwordTooShort(4));
    }

    @Test
    public void passwordComplexityLow_mediumSequentialPassword_valid() {
        assertCredentialValid(PASSWORD_COMPLEXITY_LOW, createPassword(MEDIUM_SEQUENTIAL_PASSWORD));
    }

    @Test
    public void passwordComplexityMedium_shortSequentialPassword_invalid() {
        assertCredentialInvalid(
                PASSWORD_COMPLEXITY_MEDIUM,
                createPassword(SHORT_SEQUENTIAL_PASSWORD),
                passwordTooShort(4));
    }

    @Test
    public void passwordComplexityMedium_mediumSequentialPassword_invalid() {
        assertCredentialInvalid(
                PASSWORD_COMPLEXITY_MEDIUM,
                createPassword(MEDIUM_SEQUENTIAL_PASSWORD),
                mContext.getString(R.string.lockpassword_pin_no_sequential_digits));
    }

    @Test
    public void passwordComplexityMedium_mediumAlphanumericPassword_valid() {
        assertCredentialValid(
                PASSWORD_COMPLEXITY_MEDIUM,
                createPassword(MEDIUM_ALPHANUMERIC_PASSWORD));
    }

    @Test
    public void passwordComplexityHigh_mediumSequentialPassword_invalid() {
        assertCredentialInvalid(
                PASSWORD_COMPLEXITY_HIGH,
                createPassword(MEDIUM_SEQUENTIAL_PASSWORD),
                passwordTooShort(6) + "\n"
                    + mContext.getString(R.string.lockpassword_pin_no_sequential_digits));
    }

    @Test
    public void passwordComplexityHigh_mediumAlphanumericPassword_invalid() {
        assertCredentialInvalid(
                PASSWORD_COMPLEXITY_HIGH,
                createPassword(MEDIUM_ALPHANUMERIC_PASSWORD),
                passwordTooShort(6));
    }

    @Test
    public void passwordComplexityHigh_longSequentialPassword_invalid() {
        assertCredentialInvalid(
                PASSWORD_COMPLEXITY_HIGH,
                createPassword(LONG_SEQUENTIAL_PASSWORD),
                mContext.getString(R.string.lockpassword_pin_no_sequential_digits));
    }

    @Test
    public void passwordComplexityHigh_longAlphanumericPassword_valid() {
        assertCredentialValid(PASSWORD_COMPLEXITY_HIGH, createPassword(LONG_ALPHANUMERIC_PASSWORD));
    }

    @Test
    public void pinComplexityNone_shortSequentialPassword_invalid() {
        assertCredentialInvalid(
                PASSWORD_COMPLEXITY_NONE,
                createPin(SHORT_SEQUENTIAL_PASSWORD),
                pinTooShort(4));
    }

    @Test
    public void pinComplexityNone_mediumSequentialPassword_valid() {
        assertCredentialValid(PASSWORD_COMPLEXITY_NONE, createPin(MEDIUM_SEQUENTIAL_PASSWORD));
    }

    @Test
    public void pinComplexityLow_shortSequentialPassword_invalid() {
        assertCredentialInvalid(
                PASSWORD_COMPLEXITY_LOW,
                createPin(SHORT_SEQUENTIAL_PASSWORD),
                pinTooShort(4));
    }

    @Test
    public void pinComplexityLow_mediumSequentialPassword_valid() {
        assertCredentialValid(PASSWORD_COMPLEXITY_LOW, createPin(MEDIUM_SEQUENTIAL_PASSWORD));
    }

    @Test
    public void pinComplexityMedium_shortSequentialPassword_invalid() {
        assertCredentialInvalid(
                PASSWORD_COMPLEXITY_MEDIUM,
                createPin(SHORT_SEQUENTIAL_PASSWORD),
                pinTooShort(4));
    }

    @Test
    public void pinComplexityMedium_mediumSequentialPassword_invalid() {
        assertCredentialInvalid(
                PASSWORD_COMPLEXITY_MEDIUM,
                createPin(MEDIUM_SEQUENTIAL_PASSWORD),
                mContext.getString(R.string.lockpassword_pin_no_sequential_digits));
    }

    @Test
    public void pinComplexityMedium_mediumPinPassword_valid() {
        assertCredentialValid(PASSWORD_COMPLEXITY_MEDIUM, createPin(MEDIUM_PIN_PASSWORD));
    }

    @Test
    public void pinComplexityHigh_mediumPinPassword_invalid() {
        assertCredentialInvalid(
                PASSWORD_COMPLEXITY_HIGH,
                createPin(MEDIUM_PIN_PASSWORD),
                pinTooShort(8));
    }

    @Test
    public void pinComplexityHigh_longSequentialPassword_invalid() {
        assertCredentialInvalid(
                PASSWORD_COMPLEXITY_HIGH,
                createPin(LONG_SEQUENTIAL_PASSWORD),
                mContext.getString(R.string.lockpassword_pin_no_sequential_digits));
    }

    @Test
    public void pinComplexityHigh_longPinPassword_valid() {
        assertCredentialValid(PASSWORD_COMPLEXITY_HIGH, createPin(LONG_PIN_PASSWORD));
    }

    @Test
    public void patternComplexityNone_length3Pattern_invalid() {
        assertCredentialInvalid(
                PASSWORD_COMPLEXITY_NONE,
                createPattern(LENGTH_3_PATTERN),
                patternTooShort(4));
    }

    @Test
    public void patternComplexityNone_length4Pattern_valid() {
        assertCredentialValid(PASSWORD_COMPLEXITY_NONE, createPattern(LENGTH_4_PATTERN));
    }
}
