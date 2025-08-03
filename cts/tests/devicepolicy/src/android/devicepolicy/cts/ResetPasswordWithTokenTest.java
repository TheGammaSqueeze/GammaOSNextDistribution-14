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

package android.devicepolicy.cts;

import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_MEDIUM;
import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_NONE;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_COMPLEX;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_NUMERIC;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_SOMETHING;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED;
import static android.content.pm.PackageManager.FEATURE_AUTOMOTIVE;
import static android.content.pm.PackageManager.FEATURE_SECURE_LOCK_SCREEN;

import static com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject.assertThat;
import static com.android.bedstead.nene.users.UserType.MANAGED_PROFILE_TYPE_NAME;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;
import static org.testng.Assert.assertThrows;

import android.app.KeyguardManager;
import android.app.admin.RemoteDevicePolicyManager;
import android.content.Context;
import android.stats.devicepolicy.EventId;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireDoesNotHaveFeature;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.policies.PasswordQuality;
import com.android.bedstead.harrier.policies.ResetPasswordWithToken;
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder;
import com.android.bedstead.nene.TestApis;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;

// TODO(b/191640667): Parameterize the length limit tests with multiple limits
@RunWith(BedsteadJUnit4.class)
@RequireFeature(FEATURE_SECURE_LOCK_SCREEN)
public final class ResetPasswordWithTokenTest {

    private static final String NOT_COMPLEX_PIN = "1234";
    private static final String VALID_PIN = NOT_COMPLEX_PIN;
    private static final String NUMERIC_PIN_LENGTH_3 = "123";
    private static final String NUMERIC_PIN_REPEATING_LENGTH_4 = "4444";
    private static final String NUMERIC_PIN_RANDOM_LENGTH_4 = "3829";
    private static final String NUMERIC_PIN_LENGTH_4 = NOT_COMPLEX_PIN;
    private static final String NUMERIC_PIN_LENGTH_6 = "264828";
    private static final String ALPHABETIC_PASSWORD_LENGTH_4 = "abcd";
    private static final String ALPHABETIC_PASSWORD_ALL_UPPERCASE_LENGTH_4 = "ABCD";
    private static final String ALPHANUMERIC_PASSWORD_LENGTH_4 = "12ab";
    private static final String ALPHANUMERIC_PASSWORD_WITH_UPPERCASE_LENGTH_4 = "abC1";
    private static final String ALPHANUMERIC_PASSWORD_LENGTH_8 = "1a2b3c4e";
    private static final String COMPLEX_PASSWORD_WITH_SYMBOL_LENGTH_4 = "12a_";
    private static final String COMPLEX_PASSWORD_WITH_SYMBOL_LENGTH_7 = "abc123.";

    private static final byte[] TOKEN = "abcdefghijklmnopqrstuvwxyz0123456789".getBytes();
    private static final byte[] BAD_TOKEN = "abcdefghijklmnopqrstuvwxyz012345678*".getBytes();

    private static final String RESET_PASSWORD_TOKEN_DISABLED =
            "Cannot reset password token as it is disabled for the primary user";

    private static final Context sContext = TestApis.context().instrumentedContext();
    private final KeyguardManager sLocalKeyguardManager =
            sContext.getSystemService(KeyguardManager.class);

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = ResetPasswordWithToken.class)
    public void setResetPasswordToken_validToken_passwordTokenSet() {
        try {
            boolean possible = canSetResetPasswordToken(TOKEN);

            assertThat(sDeviceState.dpc().devicePolicyManager().isResetPasswordTokenActive(
                    sDeviceState.dpc().componentName()) || !possible).isTrue();
        } finally {
            // Remove password token
            sDeviceState.dpc().devicePolicyManager().clearResetPasswordToken(sDeviceState.dpc().componentName());
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = ResetPasswordWithToken.class)
    public void resetPasswordWithToken_validPasswordAndToken_success() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            assertThat(sDeviceState.dpc().devicePolicyManager().resetPasswordWithToken(
                    sDeviceState.dpc().componentName(), VALID_PIN, TOKEN, /* flags = */ 0)).isTrue();
        } finally {
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = ResetPasswordWithToken.class)
    public void resetPasswordWithToken_badToken_failure() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        assertThat(sDeviceState.dpc().devicePolicyManager().resetPasswordWithToken(
                sDeviceState.dpc().componentName(), VALID_PIN, BAD_TOKEN, /* flags = */ 0)).isFalse();
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = ResetPasswordWithToken.class)
    public void resetPasswordWithToken_noPassword_deviceIsNotSecure() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        sDeviceState.dpc().devicePolicyManager().resetPasswordWithToken(
                sDeviceState.dpc().componentName(), /* password = */ null, TOKEN, /* flags = */ 0);

        // Device is not secure when no password is set
        assertThat(sLocalKeyguardManager.isDeviceSecure()).isFalse();
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = ResetPasswordWithToken.class)
    public void resetPasswordWithToken_password_deviceIsSecure() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            sDeviceState.dpc().devicePolicyManager().resetPasswordWithToken(
                    sDeviceState.dpc().componentName(), VALID_PIN, TOKEN, /* flags = */ 0);

            // Device is secure when a password is set
            assertThat(sLocalKeyguardManager.isDeviceSecure()).isTrue();
        } finally {
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = PasswordQuality.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void resetPasswordWithToken_passwordDoesNotSatisfyRestriction_failure() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            // Add complex password restriction
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    sDeviceState.dpc().componentName(), PASSWORD_QUALITY_COMPLEX);
            setComplexPasswordRestrictions(/* minLength */ 6,
                    /* minSymbols */ 0,
                    /* minNonLetter */ 0,
                    /* minNumeric */ 0,
                    /* minLetters */ 0,
                    /* minLowerCase */ 0,
                    /* minUpperCase */ 0);

            // Password cannot be set as it does not satisfy the password restriction
            assertThat(sDeviceState.dpc().devicePolicyManager().resetPasswordWithToken(
                    sDeviceState.dpc().componentName(), NOT_COMPLEX_PIN, TOKEN, /* flags = */ 0)).isFalse();
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = PasswordQuality.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void resetPasswordWithToken_passwordSatisfiesRestriction_success() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            // Add complex password restriction
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    sDeviceState.dpc().componentName(), PASSWORD_QUALITY_COMPLEX);
            setComplexPasswordRestrictions(/* minLength */ 6,
                    /* minSymbols */ 0,
                    /* minNonLetter */ 0,
                    /* minNumeric */ 0,
                    /* minLetters */ 0,
                    /* minLowerCase */ 0,
                    /* minUpperCase */ 0);

            // Password can be set as it satisfies the password restriction
            assertThat(sDeviceState.dpc().devicePolicyManager().resetPasswordWithToken(
                    sDeviceState.dpc().componentName(), COMPLEX_PASSWORD_WITH_SYMBOL_LENGTH_7, TOKEN,
                    /* flags = */ 0)).isTrue();
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = ResetPasswordWithToken.class)
    public void resetPasswordWithToken_validPasswordAndToken_logged() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            sDeviceState.dpc().devicePolicyManager().resetPasswordWithToken(
                    sDeviceState.dpc().componentName(), VALID_PIN, TOKEN, /* flags = */ 0);

            assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.RESET_PASSWORD_WITH_TOKEN_VALUE)
                    .whereAdminPackageName().isEqualTo(
                            sDeviceState.dpc().packageName())).wasLogged();
        } finally {
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = PasswordQuality.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void isActivePasswordSufficient_passwordDoesNotSatisfyRestriction_false() {
        try {
            TestApis.users().instrumented().setPin(NOT_COMPLEX_PIN);

            // Add complex password restriction
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    sDeviceState.dpc().componentName(), PASSWORD_QUALITY_COMPLEX);
            setComplexPasswordRestrictions(/* minLength */ 6,
                    /* minSymbols */ 0,
                    /* minNonLetter */ 0,
                    /* minNumeric */ 0,
                    /* minLetters */ 0,
                    /* minLowerCase */ 0,
                    /* minUpperCase */ 0);

            // Password is insufficient because it does not satisfy the password restriction
            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .isActivePasswordSufficient()).isFalse();
        } finally {
            removeAllPasswordRestrictions();
            TestApis.users().instrumented().clearPin();
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = PasswordQuality.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void isActivePasswordSufficient_passwordSatisfiesRestriction_true() {
        try {
            TestApis.users().instrumented().setPassword(COMPLEX_PASSWORD_WITH_SYMBOL_LENGTH_7);
            // Add complex password restriction
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    sDeviceState.dpc().componentName(), PASSWORD_QUALITY_COMPLEX);
            setComplexPasswordRestrictions(/* minLength */ 6,
                    /* minSymbols */ 0,
                    /* minNonLetter */ 0,
                    /* minNumeric */ 0,
                    /* minLetters */ 0,
                    /* minLowerCase */ 0,
                    /* minUpperCase */ 0);

            // Password is sufficient because it satisfies the password restriction
            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .isActivePasswordSufficient()).isTrue();
        } finally {
            removeAllPasswordRestrictions();
            TestApis.users().instrumented().clearPassword();
        }
    }
    // TODO(281954641): Remove RESET_PASSWORD_TOKEN stuff where it's not needed (probably move
    //  these tests to a new class)

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = PasswordQuality.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void isActivePasswordSufficient_passwordNoLongerSatisfiesRestriction_false() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(sDeviceState.dpc().componentName(),
                    PASSWORD_QUALITY_COMPLEX);
            setComplexPasswordRestrictions(/* minLength */ 0,
                    /* minSymbols */ 1,
                    /* minNonLetter */ 0,
                    /* minNumeric */ 0,
                    /* minLetters */ 0,
                    /* minLowerCase */ 0,
                    /* minUpperCase */ 0);
            sDeviceState.dpc().devicePolicyManager().resetPasswordWithToken(sDeviceState.dpc().componentName(),
                    COMPLEX_PASSWORD_WITH_SYMBOL_LENGTH_7, TOKEN, /* flags = */ 0);
            // Set a slightly stronger password restriction
            sDeviceState.dpc().devicePolicyManager().setPasswordMinimumSymbols(
                    sDeviceState.dpc().componentName(), 2);

            // Password is no longer sufficient because it does not satisfy the new restriction
            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .isActivePasswordSufficient()).isFalse();
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = PasswordQuality.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setPasswordQuality_success() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    sDeviceState.dpc().componentName(), PASSWORD_QUALITY_SOMETHING);

            assertThat(sDeviceState.dpc().devicePolicyManager().getPasswordQuality(
                    sDeviceState.dpc().componentName())).isEqualTo(PASSWORD_QUALITY_SOMETHING);
        } finally {
            removeAllPasswordRestrictions();
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = PasswordQuality.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setPasswordQuality_something_passwordWithAMinLengthOfFourRequired() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    sDeviceState.dpc().componentName(), PASSWORD_QUALITY_SOMETHING);

            assertPasswordSucceeds(NUMERIC_PIN_LENGTH_4);
            assertPasswordSucceeds(ALPHABETIC_PASSWORD_LENGTH_4);
            assertPasswordSucceeds(ALPHANUMERIC_PASSWORD_LENGTH_4);
            assertPasswordFails(NUMERIC_PIN_LENGTH_3); // Password too short
            assertPasswordFails(/* password = */ null);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = PasswordQuality.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setPasswordQuality_numeric_passwordWithAtLeastOneNumberOrLetterRequired() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    sDeviceState.dpc().componentName(), PASSWORD_QUALITY_NUMERIC);

            assertPasswordSucceeds(NUMERIC_PIN_LENGTH_4);
            assertPasswordSucceeds(ALPHANUMERIC_PASSWORD_LENGTH_4);
            assertPasswordSucceeds(ALPHABETIC_PASSWORD_LENGTH_4);
            assertPasswordFails(NUMERIC_PIN_LENGTH_3); // Password too short
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = PasswordQuality.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setPasswordQuality_alphabetic_passwordWithAtLeastOneLetterRequired() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    sDeviceState.dpc().componentName(), PASSWORD_QUALITY_ALPHABETIC);

            assertPasswordSucceeds(ALPHABETIC_PASSWORD_LENGTH_4);
            assertPasswordSucceeds(ALPHANUMERIC_PASSWORD_LENGTH_4);
            assertPasswordFails(NUMERIC_PIN_LENGTH_4);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = PasswordQuality.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setPasswordQuality_alphanumeric_passwordWithBothALetterAndANumberRequired() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    sDeviceState.dpc().componentName(), PASSWORD_QUALITY_ALPHANUMERIC);

            assertPasswordSucceeds(ALPHANUMERIC_PASSWORD_LENGTH_4);
            assertPasswordFails(NUMERIC_PIN_LENGTH_4);
            assertPasswordFails(ALPHABETIC_PASSWORD_LENGTH_4);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = PasswordQuality.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setPasswordQuality_complex_passwordWithAMinLengthOfFourRequired() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    sDeviceState.dpc().componentName(), PASSWORD_QUALITY_COMPLEX);
            setComplexPasswordRestrictions(/* minLength */ 0,
                    /* minSymbols */ 0,
                    /* minNonLetter */ 0,
                    /* minNumeric */ 0,
                    /* minLetters */ 0,
                    /* minLowerCase */ 0,
                    /* minUpperCase */ 0);

            assertPasswordSucceeds(ALPHANUMERIC_PASSWORD_LENGTH_4);
            assertPasswordSucceeds(ALPHABETIC_PASSWORD_LENGTH_4);
            assertPasswordFails(NUMERIC_PIN_LENGTH_3); // Password too short
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = PasswordQuality.class)
    // setPasswordMinimumLength is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setPasswordMinimumLength_success() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            // The restriction is only imposed if PASSWORD_QUALITY_NUMERIC is set
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    sDeviceState.dpc().componentName(), PASSWORD_QUALITY_NUMERIC);
            sDeviceState.dpc().devicePolicyManager()
                    .setPasswordMinimumLength(sDeviceState.dpc().componentName(), 4);

            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .getPasswordMinimumLength(sDeviceState.dpc().componentName())).isEqualTo(4);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = PasswordQuality.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setPasswordMinimumLength_six_passwordWithAMinLengthOfSixRequired() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            // The restriction is only imposed if PASSWORD_QUALITY_COMPLEX is set
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    sDeviceState.dpc().componentName(), PASSWORD_QUALITY_COMPLEX);
            setComplexPasswordRestrictions(/* minLength */ 6,
                    /* minSymbols */ 0,
                    /* minNonLetter */ 0,
                    /* minNumeric */ 0,
                    /* minLetters */ 0,
                    /* minLowerCase */ 0,
                    /* minUpperCase */ 0);

            assertPasswordSucceeds(COMPLEX_PASSWORD_WITH_SYMBOL_LENGTH_7);
            assertPasswordFails(COMPLEX_PASSWORD_WITH_SYMBOL_LENGTH_4);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    // setPasswordMinimumUpperCase is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @CanSetPolicyTest(policy = PasswordQuality.class)
    public void setPasswordMinimumUpperCase_success() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            // The restriction is only imposed if PASSWORD_QUALITY_COMPLEX is set
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    sDeviceState.dpc().componentName(), PASSWORD_QUALITY_COMPLEX);
            sDeviceState.dpc().devicePolicyManager()
                    .setPasswordMinimumUpperCase(sDeviceState.dpc().componentName(), 1);

            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .getPasswordMinimumUpperCase(sDeviceState.dpc().componentName())).isEqualTo(1);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = PasswordQuality.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setPasswordMinimumUpperCase_one_passwordWithAtLeastOneUpperCaseLetterRequired() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            // The restriction is only imposed if PASSWORD_QUALITY_COMPLEX is set
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    sDeviceState.dpc().componentName(), PASSWORD_QUALITY_COMPLEX);
            setComplexPasswordRestrictions(/* minLength */ 0,
                    /* minSymbols */ 0,
                    /* minNonLetter */ 0,
                    /* minNumeric */ 0,
                    /* minLetters */ 0,
                    /* minLowerCase */ 0,
                    /* minUpperCase */ 1);

            assertPasswordSucceeds(ALPHANUMERIC_PASSWORD_WITH_UPPERCASE_LENGTH_4);
            assertPasswordFails(ALPHANUMERIC_PASSWORD_LENGTH_4);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    // setPasswordMinimumLowerCase is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @CanSetPolicyTest(policy = PasswordQuality.class)
    public void setPasswordMinimumLowerCase_success() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    sDeviceState.dpc().componentName(), PASSWORD_QUALITY_COMPLEX);
            sDeviceState.dpc().devicePolicyManager()
                    .setPasswordMinimumLowerCase(sDeviceState.dpc().componentName(), 1);

            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .getPasswordMinimumLowerCase(sDeviceState.dpc().componentName())).isEqualTo(1);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = PasswordQuality.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setPasswordMinimumLowerCase_one_passwordWithAtLeaseOneLowerCaseLetterRequired() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            // The restriction is only imposed if PASSWORD_QUALITY_COMPLEX is set
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    sDeviceState.dpc().componentName(), PASSWORD_QUALITY_COMPLEX);
            setComplexPasswordRestrictions(/* minLength */ 0,
                    /* minSymbols */ 0,
                    /* minNonLetter */ 0,
                    /* minNumeric */ 0,
                    /* minLetters */ 0,
                    /* minLowerCase */ 1,
                    /* minUpperCase */ 0);

            assertPasswordSucceeds(ALPHANUMERIC_PASSWORD_LENGTH_4);
            assertPasswordFails(ALPHABETIC_PASSWORD_ALL_UPPERCASE_LENGTH_4);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = PasswordQuality.class)
    public void setPasswordMinimumLetters_success() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            // The restriction is only imposed if PASSWORD_QUALITY_COMPLEX is set
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    sDeviceState.dpc().componentName(), PASSWORD_QUALITY_COMPLEX);
            sDeviceState.dpc().devicePolicyManager()
                    .setPasswordMinimumLetters(sDeviceState.dpc().componentName(), 1);

            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .getPasswordMinimumLetters(sDeviceState.dpc().componentName())).isEqualTo(1);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = PasswordQuality.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setPasswordMinimumLetters_one_passwordWithAtLeastOneLetterRequired() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            // The restriction is only imposed if PASSWORD_QUALITY_COMPLEX is set
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    sDeviceState.dpc().componentName(), PASSWORD_QUALITY_COMPLEX);
            setComplexPasswordRestrictions(/* minLength */ 0,
                    /* minSymbols */ 0,
                    /* minNonLetter */ 0,
                    /* minNumeric */ 0,
                    /* minLetters */ 1,
                    /* minLowerCase */ 0,
                    /* minUpperCase */ 0);

            assertPasswordSucceeds(ALPHANUMERIC_PASSWORD_LENGTH_4);
            assertPasswordFails(NUMERIC_PIN_LENGTH_4);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = PasswordQuality.class)
    public void setPasswordMinimumNumeric_success() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            // The restriction is only imposed if PASSWORD_QUALITY_COMPLEX is set
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    sDeviceState.dpc().componentName(), PASSWORD_QUALITY_COMPLEX);
            sDeviceState.dpc().devicePolicyManager()
                    .setPasswordMinimumNumeric(sDeviceState.dpc().componentName(), 1);

            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .getPasswordMinimumNumeric(sDeviceState.dpc().componentName())).isEqualTo(1);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = PasswordQuality.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setPasswordMinimumNumeric_one_passwordWithAtLeastOneNumberRequired() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            // The restriction is only imposed if PASSWORD_QUALITY_COMPLEX is set
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    sDeviceState.dpc().componentName(), PASSWORD_QUALITY_COMPLEX);
            setComplexPasswordRestrictions(/* minLength */ 0,
                    /* minSymbols */ 0,
                    /* minNonLetter */ 0,
                    /* minNumeric */ 1,
                    /* minLetters */ 0,
                    /* minLowerCase */ 0,
                    /* minUpperCase */ 0);

            assertPasswordSucceeds(ALPHANUMERIC_PASSWORD_LENGTH_4);
            assertPasswordFails(ALPHABETIC_PASSWORD_LENGTH_4);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = PasswordQuality.class)
    public void setPasswordMinimumSymbols_success() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            // The restriction is only imposed if PASSWORD_QUALITY_COMPLEX is set
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    sDeviceState.dpc().componentName(), PASSWORD_QUALITY_COMPLEX);
            sDeviceState.dpc().devicePolicyManager()
                    .setPasswordMinimumSymbols(sDeviceState.dpc().componentName(), 1);

            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .getPasswordMinimumSymbols(sDeviceState.dpc().componentName())).isEqualTo(1);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = PasswordQuality.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setPasswordMinimumSymbols_one_passwordWithAtLeastOneSymbolRequired() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            // The restriction is only imposed if PASSWORD_QUALITY_COMPLEX is set
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    sDeviceState.dpc().componentName(), PASSWORD_QUALITY_COMPLEX);
            setComplexPasswordRestrictions(/* minLength */ 0,
                    /* minSymbols */ 1,
                    /* minNonLetter */ 0,
                    /* minNumeric */ 0,
                    /* minLetters */ 0,
                    /* minLowerCase */ 0,
                    /* minUpperCase */ 0);

            assertPasswordSucceeds(COMPLEX_PASSWORD_WITH_SYMBOL_LENGTH_4);
            assertPasswordFails(ALPHANUMERIC_PASSWORD_LENGTH_4);
            assertPasswordFails(ALPHABETIC_PASSWORD_LENGTH_4);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    // setPasswordMinimumNonLetter is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @CanSetPolicyTest(policy = PasswordQuality.class)
    public void setPasswordMinimumNonLetter_success() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            // The restriction is only imposed if PASSWORD_QUALITY_COMPLEX is set
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    sDeviceState.dpc().componentName(), PASSWORD_QUALITY_COMPLEX);
            sDeviceState.dpc().devicePolicyManager()
                    .setPasswordMinimumNonLetter(sDeviceState.dpc().componentName(), 1);

            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .getPasswordMinimumNonLetter(sDeviceState.dpc().componentName())).isEqualTo(1);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = PasswordQuality.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setPasswordMinimumNonLetter_one_passwordWithAtLeastOneNonLetterRequired() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            // The restriction is only imposed if PASSWORD_QUALITY_COMPLEX is set
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    sDeviceState.dpc().componentName(), PASSWORD_QUALITY_COMPLEX);
            setComplexPasswordRestrictions(/* minLength */ 0,
                    /* minSymbols */ 0,
                    /* minNonLetter */ 1,
                    /* minNumeric */ 0,
                    /* minLetters */ 0,
                    /* minLowerCase */ 0,
                    /* minUpperCase */ 0);

            assertPasswordSucceeds(COMPLEX_PASSWORD_WITH_SYMBOL_LENGTH_4);
            assertPasswordSucceeds(ALPHANUMERIC_PASSWORD_LENGTH_4);
            assertPasswordFails(ALPHABETIC_PASSWORD_LENGTH_4);
        } finally {
            removeAllPasswordRestrictions();
            removePasswordAndToken(TOKEN);
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = PasswordQuality.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setRequiredPasswordComplexity_passwordQualityAlreadySet_clearsPasswordQuality() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    sDeviceState.dpc().componentName(), PASSWORD_QUALITY_COMPLEX);
            sDeviceState.dpc().devicePolicyManager().setRequiredPasswordComplexity(
                    PASSWORD_COMPLEXITY_MEDIUM);

            assertThat(sDeviceState.dpc().devicePolicyManager().getPasswordQuality(
                    sDeviceState.dpc().componentName())).isEqualTo(PASSWORD_QUALITY_UNSPECIFIED);
            assertThat(sDeviceState.dpc().devicePolicyManager().getRequiredPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_MEDIUM);
        } finally {
            removeAllPasswordRestrictions();
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = PasswordQuality.class)
    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    public void setPasswordQuality_passwordComplexityAlreadySet_clearsPasswordComplexity() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            sDeviceState.dpc().devicePolicyManager().setRequiredPasswordComplexity(
                    PASSWORD_COMPLEXITY_MEDIUM);
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(sDeviceState.dpc().componentName(),
                    PASSWORD_QUALITY_COMPLEX);

            assertThat(sDeviceState.dpc().devicePolicyManager().getPasswordQuality(
                    sDeviceState.dpc().componentName())).isEqualTo(PASSWORD_QUALITY_COMPLEX);
            assertThat(sDeviceState.dpc().devicePolicyManager().getRequiredPasswordComplexity())
                    .isEqualTo(PASSWORD_COMPLEXITY_NONE);
        } finally {
            removeAllPasswordRestrictions();
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = ResetPasswordWithToken.class)
    public void clearResetPasswordToken_passwordTokenIsResetAndUnableToSetNewPassword() {
        assumeTrue(RESET_PASSWORD_TOKEN_DISABLED, canSetResetPasswordToken(TOKEN));
        try {
            sDeviceState.dpc().devicePolicyManager().clearResetPasswordToken(sDeviceState.dpc().componentName());

            assertThat(sDeviceState.dpc().devicePolicyManager().isResetPasswordTokenActive(
                    sDeviceState.dpc().componentName())).isFalse();
            assertThat(sDeviceState.dpc().devicePolicyManager().resetPasswordWithToken(
                    sDeviceState.dpc().componentName(), VALID_PIN, TOKEN, /* flags = */ 0)).isFalse();
        } finally {
            removePasswordAndToken(TOKEN);
        }
    }

    @RequireFeature(FEATURE_AUTOMOTIVE)
    @PolicyAppliesTest(policy = PasswordQuality.class)
    @Postsubmit(reason = "new test")
    public void passwordMinimumLength_featureUnsupported_ignored() {
        int valueBefore = sDeviceState.dpc().devicePolicyManager().getPasswordMinimumLength(
                sDeviceState.dpc().componentName());

        // The restriction is only imposed if PASSWORD_QUALITY_COMPLEX is set
        sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                sDeviceState.dpc().componentName(), PASSWORD_QUALITY_NUMERIC);
        sDeviceState.dpc().devicePolicyManager().setPasswordMinimumLength(sDeviceState.dpc().componentName(), 42);

        assertWithMessage("getPasswordMinimumLength()")
                .that(sDeviceState.dpc().devicePolicyManager()
                        .getPasswordMinimumLength(sDeviceState.dpc().componentName()))
                .isEqualTo(valueBefore);
    }

    @RequireFeature(FEATURE_AUTOMOTIVE)
    @PolicyAppliesTest(policy = PasswordQuality.class)
    @Postsubmit(reason = "new test")
    public void passwordMinimumNumeric_ignored() {
        int valueBefore = sDeviceState.dpc().devicePolicyManager().getPasswordMinimumNumeric(
                sDeviceState.dpc().componentName());

        // The restriction is only imposed if PASSWORD_QUALITY_COMPLEX is set
        sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                sDeviceState.dpc().componentName(), PASSWORD_QUALITY_COMPLEX);
        sDeviceState.dpc().devicePolicyManager().setPasswordMinimumNumeric(sDeviceState.dpc().componentName(), 42);

        assertWithMessage("getPasswordMinimumNumeric()")
                .that(sDeviceState.dpc().devicePolicyManager().getPasswordMinimumNumeric(
                        sDeviceState.dpc().componentName()))
                .isEqualTo(valueBefore);
    }

    @RequireFeature(FEATURE_AUTOMOTIVE)
    @PolicyAppliesTest(policy = PasswordQuality.class)
    @Postsubmit(reason = "new test")
    public void passwordMinimumLowerCase_ignored() {
        int valueBefore = sDeviceState.dpc().devicePolicyManager().getPasswordMinimumLowerCase(
                sDeviceState.dpc().componentName());

        // The restriction is only imposed if PASSWORD_QUALITY_COMPLEX is set
        sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                sDeviceState.dpc().componentName(), PASSWORD_QUALITY_COMPLEX);
        sDeviceState.dpc().devicePolicyManager().setPasswordMinimumLowerCase(sDeviceState.dpc().componentName(),
                42);

        assertWithMessage("getPasswordMinimumLowerCase()")
                .that(sDeviceState.dpc().devicePolicyManager().getPasswordMinimumLowerCase(
                        sDeviceState.dpc().componentName()))
                .isEqualTo(valueBefore);
    }

    @RequireFeature(FEATURE_AUTOMOTIVE)
    @PolicyAppliesTest(policy = PasswordQuality.class)
    @Postsubmit(reason = "new test")
    public void passwordMinimumUpperCase_ignored() {
        int valueBefore = sDeviceState.dpc().devicePolicyManager().getPasswordMinimumUpperCase(
                sDeviceState.dpc().componentName());

        // The restriction is only imposed if PASSWORD_QUALITY_COMPLEX is set
        sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                sDeviceState.dpc().componentName(), PASSWORD_QUALITY_COMPLEX);
        sDeviceState.dpc().devicePolicyManager().setPasswordMinimumUpperCase(sDeviceState.dpc().componentName(),
                42);

        assertWithMessage("getPasswordMinimumUpperCase()")
                .that(sDeviceState.dpc().devicePolicyManager().getPasswordMinimumUpperCase(
                        sDeviceState.dpc().componentName()))
                .isEqualTo(valueBefore);
    }

    @RequireFeature(FEATURE_AUTOMOTIVE)
    @PolicyAppliesTest(policy = PasswordQuality.class)
    @Postsubmit(reason = "new test")
    public void passwordMinimumLetters_ignored() {
        int valueBefore = sDeviceState.dpc().devicePolicyManager().getPasswordMinimumLetters(
                sDeviceState.dpc().componentName());

        // The restriction is only imposed if PASSWORD_QUALITY_COMPLEX is set
        sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                sDeviceState.dpc().componentName(), PASSWORD_QUALITY_COMPLEX);
        sDeviceState.dpc().devicePolicyManager().setPasswordMinimumLetters(sDeviceState.dpc().componentName(), 42);

        assertWithMessage("getPasswordMinimumLetters()")
                .that(sDeviceState.dpc().devicePolicyManager().getPasswordMinimumLetters(
                        sDeviceState.dpc().componentName()))
                .isEqualTo(valueBefore);
    }

    @RequireFeature(FEATURE_AUTOMOTIVE)
    @PolicyAppliesTest(policy = PasswordQuality.class)
    @Postsubmit(reason = "new test")
    public void passwordMinimumSymbols_ignored() {
        int valueBefore = sDeviceState.dpc().devicePolicyManager().getPasswordMinimumSymbols(
                sDeviceState.dpc().componentName());

        // The restriction is only imposed if PASSWORD_QUALITY_COMPLEX is set
        sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                sDeviceState.dpc().componentName(), PASSWORD_QUALITY_COMPLEX);
        sDeviceState.dpc().devicePolicyManager().setPasswordMinimumSymbols(sDeviceState.dpc().componentName(), 42);

        assertWithMessage("getPasswordMinimumSymbols()")
                .that(sDeviceState.dpc().devicePolicyManager().getPasswordMinimumSymbols(
                        sDeviceState.dpc().componentName()))
                .isEqualTo(valueBefore);
    }

    @RequireFeature(FEATURE_AUTOMOTIVE)
    @PolicyAppliesTest(policy = PasswordQuality.class)
    @Postsubmit(reason = "new test")
    public void passwordMinimumNonLetter_ignored() {
        int valueBefore = sDeviceState.dpc().devicePolicyManager().getPasswordMinimumNonLetter(
                sDeviceState.dpc().componentName());

        // The restriction is only imposed if PASSWORD_QUALITY_COMPLEX is set
        sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                sDeviceState.dpc().componentName(), PASSWORD_QUALITY_COMPLEX);
        sDeviceState.dpc().devicePolicyManager().setPasswordMinimumNonLetter(sDeviceState.dpc().componentName(),
                42);

        assertWithMessage("getPasswordMinimumNonLetter()")
                .that(sDeviceState.dpc().devicePolicyManager().getPasswordMinimumNonLetter(
                        sDeviceState.dpc().componentName()))
                .isEqualTo(valueBefore);
    }

    @CannotSetPolicyTest(policy = ResetPasswordWithToken.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    public void setResetPasswordToken_notPermitted_throwsSecurityException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().setResetPasswordToken(
                        sDeviceState.dpc().componentName(), TOKEN));
    }

    @CannotSetPolicyTest(policy = ResetPasswordWithToken.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    public void resetPasswordWithToken_notPermitted_throwsSecurityException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().resetPasswordWithToken(
                        sDeviceState.dpc().componentName(), NOT_COMPLEX_PIN, TOKEN, 0));
    }

    @CannotSetPolicyTest(policy = ResetPasswordWithToken.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    public void clearResetPasswordToken_notPermitted_throwsSecurityException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().clearResetPasswordToken(
                        sDeviceState.dpc().componentName()));
    }

    @CannotSetPolicyTest(policy = ResetPasswordWithToken.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    public void isResetPasswordTokenActive_notPermitted_throwsSecurityException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().isResetPasswordTokenActive(
                        sDeviceState.dpc().componentName()));
    }

    private void assertPasswordSucceeds(String password) {
        assertThat(sDeviceState.dpc().devicePolicyManager().resetPasswordWithToken(
                sDeviceState.dpc().componentName(), password, TOKEN, /* flags = */ 0)).isTrue();
        assertThat(sDeviceState.dpc().devicePolicyManager().isActivePasswordSufficient()).isTrue();
    }

    private void assertPasswordFails(String password) {
        assertThat(sDeviceState.dpc().devicePolicyManager().resetPasswordWithToken(
                sDeviceState.dpc().componentName(), password, TOKEN, /* flags = */ 0)).isFalse();
    }

    private void removeAllPasswordRestrictions() {
        try {
            sDeviceState.dpc().devicePolicyManager().setPasswordQuality(
                    sDeviceState.dpc().componentName(), PASSWORD_QUALITY_UNSPECIFIED);
        } catch (SecurityException e) {
            if (
                    e.getMessage().contains(
                            "may not apply password quality requirements device-wide")) {
                // Fine as this is expected for profile owners acting on parent
            } else {
                throw e;
            }
        } finally {
            sDeviceState.dpc().devicePolicyManager().setRequiredPasswordComplexity(
                    PASSWORD_COMPLEXITY_NONE);
        }
    }

    private void setComplexPasswordRestrictions(int minLength, int minSymbols, int minNonLetter,
            int minNumeric, int minLetters, int minLowerCase, int minUpperCase) {
        RemoteDevicePolicyManager dpm = sDeviceState.dpc().devicePolicyManager();
        dpm.setPasswordMinimumLength(sDeviceState.dpc().componentName(), minLength);
        dpm.setPasswordMinimumSymbols(sDeviceState.dpc().componentName(), minSymbols);
        dpm.setPasswordMinimumNonLetter(sDeviceState.dpc().componentName(), minNonLetter);
        dpm.setPasswordMinimumNumeric(sDeviceState.dpc().componentName(), minNumeric);
        dpm.setPasswordMinimumLetters(sDeviceState.dpc().componentName(), minLetters);
        dpm.setPasswordMinimumLowerCase(sDeviceState.dpc().componentName(), minLowerCase);
        dpm.setPasswordMinimumUpperCase(sDeviceState.dpc().componentName(), minUpperCase);
    }

    private void removePasswordAndToken(byte[] token) {
        sDeviceState.dpc().devicePolicyManager().resetPasswordWithToken(
                sDeviceState.dpc().componentName(), /* password = */ null, token, /* flags = */ 0);
        sDeviceState.dpc().devicePolicyManager().clearResetPasswordToken(sDeviceState.dpc().componentName());
    }


    // If ResetPasswordWithTokenTest for managed profile is executed before device owner and
    // primary user profile owner tests, password reset token would have been disabled for the
    // primary user, so executing ResetPasswordWithTokenTest on user 0 would fail. We allow this
    // and do not fail the test in this case.
    private boolean canSetResetPasswordToken(byte[] token) {
        try {
            sDeviceState.dpc().devicePolicyManager().setResetPasswordToken(
                    sDeviceState.dpc().componentName(), token);
            return true;
        } catch (SecurityException e) {
            if (allowFailure(e)) {
                return false;
            } else {
                throw e;
            }
        }
    }

    // Password token is disabled for the primary user, allow failure.
    private static boolean allowFailure(SecurityException e) {
        return !sDeviceState.dpc().user().type().name().equals(MANAGED_PROFILE_TYPE_NAME)
                && e.getMessage().contains("Escrow token is disabled on the current user");
    }
}
