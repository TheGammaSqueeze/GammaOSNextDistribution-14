/*
 * Copyright (C) 2023 The Android Open Source Project
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
package android.devicepolicy.cts


import android.app.admin.DevicePolicyManager
import android.content.pm.PackageManager
import android.os.Build
import android.stats.devicepolicy.EventId
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.Defaults
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.annotations.EnsurePasswordNotSet
import com.android.bedstead.harrier.annotations.EnsurePasswordSet
import com.android.bedstead.harrier.annotations.EnsureUnlocked
import com.android.bedstead.harrier.annotations.Postsubmit
import com.android.bedstead.harrier.annotations.RequireDoesNotHaveFeature
import com.android.bedstead.harrier.annotations.RequireFeature
import com.android.bedstead.harrier.annotations.RequireTargetSdkVersion
import com.android.bedstead.harrier.annotations.enterprise.AdditionalQueryParameters
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest
import com.android.bedstead.harrier.annotations.enterprise.PolicyDoesNotApplyTest
import com.android.bedstead.harrier.policies.DeprecatedResetPassword
import com.android.bedstead.harrier.policies.FailedPasswordAttempts
import com.android.bedstead.harrier.policies.PasswordExpirationTimeout
import com.android.bedstead.harrier.policies.PasswordQuality
import com.android.bedstead.harrier.policies.PasswordSufficiency
import com.android.bedstead.harrier.policies.StrongAuthTimeout
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder
import com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject.assertThat
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.utils.Assert.assertThrows
import com.android.compatibility.common.util.ApiTest
import com.android.queryable.annotations.IntegerQuery
import com.android.queryable.annotations.Query
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Rule
import org.junit.runner.RunWith
import org.testng.Assert
import com.android.bedstead.nene.utils.Versions.R

@RunWith(BedsteadJUnit4::class)
class PasswordTest {

    @CannotSetPolicyTest(
        policy = [PasswordExpirationTimeout::class],
        includeNonDeviceAdminStates = false
    )
    @Postsubmit(reason = "New test")
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setPasswordExpirationTimeout"])
    fun setPasswordExpirationTimeout_notPermitted_throwsException() {
        assertThrows(SecurityException::class.java) {
            deviceState.dpc().devicePolicyManager()
                .setPasswordExpirationTimeout(deviceState.dpc().componentName(), TIMEOUT)
        }
    }

    @RequireFeature(PackageManager.FEATURE_SECURE_LOCK_SCREEN)
    @PolicyAppliesTest(policy = [PasswordExpirationTimeout::class])
    @Postsubmit(reason = "New test")
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setPasswordExpirationTimeout", "android.app.admin.DevicePolicyManager#getPasswordExpirationTimeout"])
    @Ignore("b/303206765 bug with propagation to clone profiles")
    fun setPasswordExpirationTimeout_passwordExpirationTimeoutIsSet() {
        val originalTimeout = deviceState.dpc().devicePolicyManager()
            .getPasswordExpirationTimeout(deviceState.dpc().componentName())
        try {
            deviceState.dpc().devicePolicyManager()
                .setPasswordExpirationTimeout(deviceState.dpc().componentName(), TIMEOUT)
            assertThat(TestApis.devicePolicy().getPasswordExpirationTimeout()).isEqualTo(
                TIMEOUT
            )
        } finally {
            deviceState.dpc().devicePolicyManager()
                .setPasswordExpirationTimeout(
                    deviceState.dpc().componentName(), originalTimeout
                )
        }
    }

    @PolicyDoesNotApplyTest(policy = [PasswordExpirationTimeout::class])
    @Postsubmit(reason = "New test")
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setPasswordExpirationTimeout", "android.app.admin.DevicePolicyManager#getPasswordExpirationTimeout"])
    fun setPasswordExpirationTimeout_doesNotApply_passwordExpirationTimeoutIsNotSet() {
        val originalTimeout = deviceState.dpc().devicePolicyManager()
            .getPasswordExpirationTimeout(deviceState.dpc().componentName())
        try {
            deviceState.dpc().devicePolicyManager()
                .setPasswordExpirationTimeout(deviceState.dpc().componentName(), TIMEOUT)
            Truth.assertThat(TestApis.devicePolicy().getPasswordExpirationTimeout())
                .isNotEqualTo(TIMEOUT)
        } finally {
            deviceState.dpc().devicePolicyManager()
                .setPasswordExpirationTimeout(
                    deviceState.dpc().componentName(), originalTimeout
                )
        }
    }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#getCurrentFailedPasswordAttempts"])
    @Postsubmit(reason = "New test")
    @CannotSetPolicyTest(
        policy = [FailedPasswordAttempts::class],
        includeNonDeviceAdminStates = false
    )
    fun getCurrentFailedPasswordAttempts_notPermitted_throwsException() {
        // TODO: Test effect of PasswordExpirationTimeout
            assertThrows(SecurityException::class.java) {
                deviceState.dpc()
                    .devicePolicyManager().currentFailedPasswordAttempts
            }
        }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#getCurrentFailedPasswordAttempts"])
    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = [FailedPasswordAttempts::class])
    fun getCurrentFailedPasswordAttempts_permitted_doesNotThrow() {
        deviceState.dpc().devicePolicyManager().currentFailedPasswordAttempts
    }

    // TODO: Create an interactive test to test functionality of getCurrentFailedPasswordAttempts
    @CannotSetPolicyTest(policy = [StrongAuthTimeout::class], includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setRequiredStrongAuthTimeout"])
    fun setRequiredStrongAuthTimeout_notPermitted_throwsException() {
        assertThrows(SecurityException::class.java) {
            deviceState.dpc().devicePolicyManager()
                .setRequiredStrongAuthTimeout(deviceState.dpc().componentName(), TIMEOUT)
        }
    }

    @RequireFeature(PackageManager.FEATURE_SECURE_LOCK_SCREEN)
    @PolicyAppliesTest(policy = [StrongAuthTimeout::class])
    @Postsubmit(reason = "New test")
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setRequiredStrongAuthTimeout", "android.app.admin.DevicePolicyManager#getRequiredStrongAuthTimeout"])
    fun setRequiredStrongAuthTimeout_strongAuthTimeoutIsSet() {
        val originalTimeout = deviceState.dpc().devicePolicyManager()
            .getPasswordExpirationTimeout(deviceState.dpc().componentName())
        try {
            deviceState.dpc().devicePolicyManager()
                .setPasswordExpirationTimeout(deviceState.dpc().componentName(), TIMEOUT)
            assertThat(TestApis.devicePolicy().getPasswordExpirationTimeout()).isEqualTo(
                TIMEOUT
            )
        } finally {
            deviceState.dpc().devicePolicyManager()
                .setPasswordExpirationTimeout(
                    deviceState.dpc().componentName(), originalTimeout
                )
        }
    }

    @PolicyDoesNotApplyTest(policy = [StrongAuthTimeout::class])
    @Postsubmit(reason = "New test")
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setRequiredStrongAuthTimeout", "android.app.admin.DevicePolicyManager#getRequiredStrongAuthTimeout"])
    fun setRequiredStrongAuthTimeout_doesNotApply_strongAuthTimeoutIsNotSet() {
        val originalTimeout = deviceState.dpc().devicePolicyManager()
            .getRequiredStrongAuthTimeout(deviceState.dpc().componentName())
        try {
            deviceState.dpc().devicePolicyManager()
                .setRequiredStrongAuthTimeout(deviceState.dpc().componentName(), TIMEOUT)
            Truth.assertThat(TestApis.devicePolicy().getRequiredStrongAuthTimeout()
            ).isNotEqualTo(TIMEOUT)
        } finally {
            deviceState.dpc().devicePolicyManager()
                .setRequiredStrongAuthTimeout(
                    deviceState.dpc().componentName(), originalTimeout
                )
        }
    }

    @RequireDoesNotHaveFeature(PackageManager.FEATURE_AUTOMOTIVE)
    @RequireTargetSdkVersion(max = Build.VERSION_CODES.N)
    @PolicyAppliesTest(policy = [DeprecatedResetPassword::class])
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#resetPassword"])
    fun resetPassword_targetBeforeN_returnsFalse() {
        Truth.assertThat(
            deviceState.dpc()
                .devicePolicyManager().resetPassword(Defaults.DEFAULT_PASSWORD,  /* flags= */0)
        ).isFalse()
    }

    @RequireFeature(PackageManager.FEATURE_SECURE_LOCK_SCREEN)
    @RequireDoesNotHaveFeature(
        PackageManager.FEATURE_AUTOMOTIVE
    )
    @RequireTargetSdkVersion(min = Build.VERSION_CODES.O)
    @PolicyAppliesTest(policy = [DeprecatedResetPassword::class])
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#resetPassword"])
    fun resetPassword_targetAfterO_throwsSecurityException() {
        assertThrows(
            SecurityException::class.java
        ) {
            deviceState.dpc().devicePolicyManager()
                .resetPassword(Defaults.DEFAULT_PASSWORD,  /* flags= */0)
        }
    }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#isActivePasswordSufficient"])
    @CanSetPolicyTest(policy = [PasswordSufficiency::class])
    @EnsureUnlocked
    @EnsurePasswordSet(password = PASSWORD_MEDIUM_COMPLEXITY)
    @RequireFeature(PackageManager.FEATURE_SECURE_LOCK_SCREEN)
    fun getIsActivePasswordSufficient_passwordDoesNotMeetRequirement_returnsFalse() {
            try {
                deviceState.dpc().devicePolicyManager().requiredPasswordComplexity =
                    DevicePolicyManager.PASSWORD_COMPLEXITY_HIGH
                Truth.assertThat(
                    deviceState.dpc().devicePolicyManager().isActivePasswordSufficient
                )
                    .isFalse()
            } finally {
                deviceState.dpc().devicePolicyManager().requiredPasswordComplexity =
                    DevicePolicyManager.PASSWORD_COMPLEXITY_NONE
            }
        }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#isActivePasswordSufficient"])
    @CanSetPolicyTest(policy = [PasswordSufficiency::class])
    @EnsureUnlocked
    @EnsurePasswordSet(password = PASSWORD_MEDIUM_COMPLEXITY)
    @RequireFeature(PackageManager.FEATURE_SECURE_LOCK_SCREEN)
    fun getIsActivePasswordSufficient_passwordMeetsRequirement_returnsTrue() {
            try {
                deviceState.dpc().devicePolicyManager().requiredPasswordComplexity =
                    DevicePolicyManager.PASSWORD_COMPLEXITY_MEDIUM
                Truth.assertThat(
                    deviceState.dpc().devicePolicyManager().isActivePasswordSufficient
                )
                    .isTrue()
            } finally {
                deviceState.dpc().devicePolicyManager().requiredPasswordComplexity =
                    DevicePolicyManager.PASSWORD_COMPLEXITY_NONE
            }
        }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#isActivePasswordSufficient"])
    @CanSetPolicyTest(policy = [PasswordSufficiency::class])
    @EnsureUnlocked
    @EnsurePasswordNotSet
    @RequireFeature(PackageManager.FEATURE_SECURE_LOCK_SCREEN)
    fun getIsActivePasswordSufficient_passwordNotSet_returnsTrue() {
        assertThat(deviceState.dpc().devicePolicyManager()
            .isActivePasswordSufficient).isTrue()
    }

//     setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(PackageManager.FEATURE_AUTOMOTIVE)
    @PolicyAppliesTest(
        policy = [PasswordQuality::class]
    )
    @Postsubmit(reason = "New test")
    @AdditionalQueryParameters(
        forTestApp = "dpc",
        query = Query(targetSdkVersion = IntegerQuery(isLessThan = R))
    )
    fun setPasswordQuality_setPasswordMinimumSymbols_resetWithLowerQuality_targetBeforeR_preservesValues() {
        val initialPasswordQuality = deviceState.dpc().devicePolicyManager().getPasswordQuality(
            deviceState.dpc().componentName()
        )
        val initialPasswordMinimumSymbols = deviceState.dpc().devicePolicyManager()
            .getPasswordMinimumSymbols(deviceState.dpc().componentName())
        try {
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                DevicePolicyManager.PASSWORD_QUALITY_COMPLEX
            )
            deviceState.dpc().devicePolicyManager().setPasswordMinimumSymbols(
                deviceState.dpc().componentName(),
                TEST_VALUE
            )

            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                DevicePolicyManager.PASSWORD_QUALITY_SOMETHING
            )

            assertThat(deviceState.dpc().devicePolicyManager()
                    .getPasswordMinimumSymbols(deviceState.dpc().componentName()))
                    .isEqualTo(TEST_VALUE)
        } finally {
            deviceState.dpc().devicePolicyManager().setPasswordMinimumSymbols(
                deviceState.dpc().componentName(),
                initialPasswordMinimumSymbols
            )
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                initialPasswordQuality
            )
        }
    }

    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(PackageManager.FEATURE_AUTOMOTIVE)
    @PolicyAppliesTest(
        policy = [PasswordQuality::class]
    )
    @Postsubmit(reason = "New test")
    @AdditionalQueryParameters(
        forTestApp = "dpc",
        query = Query(targetSdkVersion = IntegerQuery(isLessThan = R))
    )
    fun setPasswordQuality_setPasswordMinimumLength_resetWithLowerQuality_targetBeforeR_preservesValues() {
        val initialPasswordQuality = deviceState.dpc().devicePolicyManager().getPasswordQuality(
            deviceState.dpc().componentName()
        )
        val initialPasswordMinimumLength = deviceState.dpc().devicePolicyManager()
            .getPasswordMinimumLength(deviceState.dpc().componentName())
        try {
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                DevicePolicyManager.PASSWORD_QUALITY_COMPLEX
            )
            deviceState.dpc().devicePolicyManager().setPasswordMinimumLength(
                deviceState.dpc().componentName(),
                TEST_VALUE
            )

            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                DevicePolicyManager.PASSWORD_QUALITY_SOMETHING
            )

            assertThat(deviceState.dpc().devicePolicyManager()
                    .getPasswordMinimumLength(deviceState.dpc().componentName()))
                    .isEqualTo(TEST_VALUE)
        } finally {
            deviceState.dpc().devicePolicyManager().setPasswordMinimumLength(
                deviceState.dpc().componentName(),
                initialPasswordMinimumLength
            )
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                initialPasswordQuality
            )
        }
    }

    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(PackageManager.FEATURE_AUTOMOTIVE)
    @PolicyAppliesTest(
        policy = [PasswordQuality::class]
    )
    @Postsubmit(reason = "New test")
    @AdditionalQueryParameters(
        forTestApp = "dpc",
        query = Query(targetSdkVersion = IntegerQuery(isLessThan = R))
    )
    fun setPasswordQuality_setPasswordMinimumNumeric_resetWithLowerQuality_targetBeforeR_preservesValues() {
        val initialPasswordQuality = deviceState.dpc().devicePolicyManager().getPasswordQuality(
            deviceState.dpc().componentName()
        )
        val initialPasswordMinimumNumeric = deviceState.dpc().devicePolicyManager()
            .getPasswordMinimumNumeric(deviceState.dpc().componentName())
        try {
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                DevicePolicyManager.PASSWORD_QUALITY_COMPLEX
            )

            deviceState.dpc().devicePolicyManager().setPasswordMinimumNumeric(
                deviceState.dpc().componentName(),
                TEST_VALUE
            )

            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                DevicePolicyManager.PASSWORD_QUALITY_SOMETHING
            )

            assertThat(deviceState.dpc().devicePolicyManager()
                    .getPasswordMinimumNumeric(deviceState.dpc().componentName()))
                    .isEqualTo(TEST_VALUE)
        } finally {
            deviceState.dpc().devicePolicyManager().setPasswordMinimumNumeric(
                deviceState.dpc().componentName(),
                initialPasswordMinimumNumeric
            )
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                initialPasswordQuality
            )
        }
    }

    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(PackageManager.FEATURE_AUTOMOTIVE)
    @PolicyAppliesTest(
        policy = [PasswordQuality::class]
    )
    @Postsubmit(reason = "New test")
    @AdditionalQueryParameters(
        forTestApp = "dpc",
        query = Query(targetSdkVersion = IntegerQuery(isLessThan = R))
    )
    fun setPasswordQuality_setPasswordMinimumLetters_resetWithLowerQuality_targetBeforeR_preservesValues() {
        val initialPasswordQuality = deviceState.dpc().devicePolicyManager().getPasswordQuality(
            deviceState.dpc().componentName()
        )
        val initialPasswordMinimumLetters = deviceState.dpc().devicePolicyManager()
            .getPasswordMinimumLetters(deviceState.dpc().componentName())
        try {
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                DevicePolicyManager.PASSWORD_QUALITY_COMPLEX
            )

            deviceState.dpc().devicePolicyManager().setPasswordMinimumLetters(
                deviceState.dpc().componentName(),
                TEST_VALUE
            )

            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                DevicePolicyManager.PASSWORD_QUALITY_SOMETHING
            )

            assertThat(deviceState.dpc().devicePolicyManager()
                    .getPasswordMinimumLetters(deviceState.dpc().componentName()))
                    .isEqualTo(TEST_VALUE)
        } finally {
            deviceState.dpc().devicePolicyManager().setPasswordMinimumLetters(
                deviceState.dpc().componentName(),
                initialPasswordMinimumLetters
            )
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                initialPasswordQuality
            )
        }
    }

    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(PackageManager.FEATURE_AUTOMOTIVE)
    @PolicyAppliesTest(
        policy = [PasswordQuality::class]
    )
    @Postsubmit(reason = "New test")
    @AdditionalQueryParameters(
        forTestApp = "dpc",
        query = Query(targetSdkVersion = IntegerQuery(isLessThan = R))
    )
    fun setPasswordQuality_setPasswordMinimumUpperCase_resetWithLowerQuality_targetBeforeR_preservesValues() {
        val initialPasswordQuality = deviceState.dpc().devicePolicyManager().getPasswordQuality(
            deviceState.dpc().componentName()
        )
        val initialPasswordMinimumUpperCase = deviceState.dpc().devicePolicyManager()
            .getPasswordMinimumUpperCase(deviceState.dpc().componentName())
        try {
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                DevicePolicyManager.PASSWORD_QUALITY_COMPLEX
            )

            deviceState.dpc().devicePolicyManager().setPasswordMinimumUpperCase(
                deviceState.dpc().componentName(),
                TEST_VALUE
            )

            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                DevicePolicyManager.PASSWORD_QUALITY_SOMETHING
            )

            assertThat(deviceState.dpc().devicePolicyManager()
                    .getPasswordMinimumUpperCase(deviceState.dpc().componentName()))
                    .isEqualTo(TEST_VALUE)
        } finally {
            deviceState.dpc().devicePolicyManager().setPasswordMinimumUpperCase(
                deviceState.dpc().componentName(),
                initialPasswordMinimumUpperCase
            )
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                initialPasswordQuality
            )
        }
    }

    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(PackageManager.FEATURE_AUTOMOTIVE)
    @PolicyAppliesTest(
        policy = [PasswordQuality::class]
    )
    @Postsubmit(reason = "New test")
    @AdditionalQueryParameters(
        forTestApp = "dpc",
        query = Query(targetSdkVersion = IntegerQuery(isLessThan = R))
    )
    fun setPasswordQuality_setPasswordMinimumLowerCase_resetWithLowerQuality_targetBeforeR_preservesValues() {
        val initialPasswordQuality = deviceState.dpc().devicePolicyManager().getPasswordQuality(
            deviceState.dpc().componentName()
        )
        val initialPasswordMinimumLowerCase = deviceState.dpc().devicePolicyManager()
            .getPasswordMinimumLowerCase(deviceState.dpc().componentName())
        try {
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                DevicePolicyManager.PASSWORD_QUALITY_COMPLEX
            )

            deviceState.dpc().devicePolicyManager().setPasswordMinimumLowerCase(
                deviceState.dpc().componentName(),
                TEST_VALUE
            )

            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                DevicePolicyManager.PASSWORD_QUALITY_SOMETHING
            )

            assertThat(deviceState.dpc().devicePolicyManager()
                    .getPasswordMinimumLowerCase(deviceState.dpc().componentName()))
                    .isEqualTo(TEST_VALUE)
        } finally {
            deviceState.dpc().devicePolicyManager().setPasswordMinimumLowerCase(
                deviceState.dpc().componentName(),
                initialPasswordMinimumLowerCase
            )
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                initialPasswordQuality
            )
        }
    }

    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(PackageManager.FEATURE_AUTOMOTIVE)
    @PolicyAppliesTest(
        policy = [PasswordQuality::class]
    )
    @Postsubmit(reason = "New test")
    @AdditionalQueryParameters(
        forTestApp = "dpc",
        query = Query(targetSdkVersion = IntegerQuery(isLessThan = R))
    )
    fun setPasswordQuality_setPasswordMinimumNonLetter_resetWithLowerQuality_targetBeforeR_preservesValues() {
        val initialPasswordQuality = deviceState.dpc().devicePolicyManager().getPasswordQuality(
            deviceState.dpc().componentName()
        )
        val initialPasswordMinimumNonLetter = deviceState.dpc().devicePolicyManager()
            .getPasswordMinimumNonLetter(deviceState.dpc().componentName())

        try {
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                DevicePolicyManager.PASSWORD_QUALITY_COMPLEX
            )

            deviceState.dpc().devicePolicyManager().setPasswordMinimumNonLetter(
                deviceState.dpc().componentName(),
                TEST_VALUE
            )

            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                DevicePolicyManager.PASSWORD_QUALITY_SOMETHING
            )

            assertThat(deviceState.dpc().devicePolicyManager()
                    .getPasswordMinimumNonLetter(deviceState.dpc().componentName()))
                    .isEqualTo(TEST_VALUE)
        } finally {
            deviceState.dpc().devicePolicyManager().setPasswordMinimumNonLetter(
                deviceState.dpc().componentName(),
                initialPasswordMinimumNonLetter
            )
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                initialPasswordQuality
            )
        }
    }

    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(PackageManager.FEATURE_AUTOMOTIVE)
    @PolicyAppliesTest(
        policy = [PasswordQuality::class]
    )
    @Postsubmit(reason = "New test")
    @AdditionalQueryParameters(
        forTestApp = "dpc",
        query = Query(targetSdkVersion = IntegerQuery(isGreaterThan = R))
    )
    fun setPasswordNumericQuality_setPasswordMinimumSymbols_targetAfterR_throwsException() {
        val initialPasswordQuality = deviceState.dpc().devicePolicyManager().getPasswordQuality(
            deviceState.dpc().componentName()
        )
        try {
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                DevicePolicyManager.PASSWORD_QUALITY_NUMERIC
            )
            Assert.assertThrows(
                IllegalStateException::class.java
            ) {
                deviceState.dpc().devicePolicyManager().setPasswordMinimumSymbols(
                    deviceState.dpc().componentName(),
                    TEST_VALUE
                )
            }
        } finally {
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                initialPasswordQuality
            )
        }
    }

    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(PackageManager.FEATURE_AUTOMOTIVE)
    @PolicyAppliesTest(
        policy = [PasswordQuality::class]
    )
    @Postsubmit(reason = "New test")
    @AdditionalQueryParameters(
        forTestApp = "dpc",
        query = Query(targetSdkVersion = IntegerQuery(isGreaterThan = R))
    )
    fun setPasswordLowQuality_setPasswordMinimumLength_targetAfterR_throwsException() {
        val initialPasswordQuality = deviceState.dpc().devicePolicyManager().getPasswordQuality(
            deviceState.dpc().componentName()
        )
        try {
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                DevicePolicyManager.PASSWORD_QUALITY_SOMETHING
            )
            Assert.assertThrows(
                IllegalStateException::class.java
            ) {
                deviceState.dpc().devicePolicyManager().setPasswordMinimumLength(
                    deviceState.dpc().componentName(),
                    TEST_VALUE
                )
            }
        } finally {
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                initialPasswordQuality
            )
        }
    }

    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(PackageManager.FEATURE_AUTOMOTIVE)
    @PolicyAppliesTest(
        policy = [PasswordQuality::class]
    )
    @Postsubmit(reason = "New test")
    @AdditionalQueryParameters(
        forTestApp = "dpc",
        query = Query(targetSdkVersion = IntegerQuery(isGreaterThan = R))
    )
    fun setPasswordNumericQuality_setPasswordMinimumLength_targetAfterR_doesNotThrowException() {
        val initialPasswordQuality = deviceState.dpc().devicePolicyManager().getPasswordQuality(
            deviceState.dpc().componentName()
        )
        val initialPasswordMinimumLength = deviceState.dpc().devicePolicyManager()
            .getPasswordMinimumLength(deviceState.dpc().componentName())
        try {
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                DevicePolicyManager.PASSWORD_QUALITY_NUMERIC
            )

            deviceState.dpc().devicePolicyManager().setPasswordMinimumLength(
                deviceState.dpc().componentName(),
                TEST_VALUE
            )

            assertThat(deviceState.dpc().devicePolicyManager()
                    .getPasswordMinimumLength(deviceState.dpc().componentName()))
                    .isEqualTo(TEST_VALUE)

        } finally {
            deviceState.dpc().devicePolicyManager().setPasswordMinimumLength(
                deviceState.dpc().componentName(),
                initialPasswordMinimumLength
            )
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                initialPasswordQuality
            )
        }
    }

    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(PackageManager.FEATURE_AUTOMOTIVE)
    @PolicyAppliesTest(
        policy = [PasswordQuality::class]
    )
    @Postsubmit(reason = "New test")
    @AdditionalQueryParameters(
        forTestApp = "dpc",
        query = Query(targetSdkVersion = IntegerQuery(isGreaterThan = R))
    )
    fun setPasswordNumericQuality_setPasswordMinimumNumeric_targetAfterR_throwsException() {
        val initialPasswordQuality = deviceState.dpc().devicePolicyManager().getPasswordQuality(
            deviceState.dpc().componentName()
        )
        try {
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                DevicePolicyManager.PASSWORD_QUALITY_NUMERIC
            )
            Assert.assertThrows(
                IllegalStateException::class.java
            ) {
                deviceState.dpc().devicePolicyManager().setPasswordMinimumNumeric(
                    deviceState.dpc().componentName(),
                    TEST_VALUE
                )
            }
        } finally {
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                initialPasswordQuality
            )
        }
    }

    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(PackageManager.FEATURE_AUTOMOTIVE)
    @PolicyAppliesTest(
        policy = [PasswordQuality::class]
    )
    @Postsubmit(reason = "New test")
    @AdditionalQueryParameters(
        forTestApp = "dpc",
        query = Query(targetSdkVersion = IntegerQuery(isGreaterThan = R))
    )
    fun setPasswordNumericQuality_setPasswordMinimumLetters_targetAfterR_throwsException() {
        val initialPasswordQuality = deviceState.dpc().devicePolicyManager().getPasswordQuality(
            deviceState.dpc().componentName()
        )
        try {
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                DevicePolicyManager.PASSWORD_QUALITY_NUMERIC
            )
            Assert.assertThrows(
                IllegalStateException::class.java
            ) {
                deviceState.dpc().devicePolicyManager().setPasswordMinimumLetters(
                    deviceState.dpc().componentName(),
                    TEST_VALUE
                )
            }
        } finally {
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                initialPasswordQuality
            )
        }
    }

    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(PackageManager.FEATURE_AUTOMOTIVE)
    @PolicyAppliesTest(
        policy = [PasswordQuality::class]
    )
    @Postsubmit(reason = "New test")
    @AdditionalQueryParameters(
        forTestApp = "dpc",
        query = Query(targetSdkVersion = IntegerQuery(isGreaterThan = R))
    )
    fun setPasswordNumericQuality_setPasswordMinimumUpperCase_targetAfterR_throwsException() {
        val initialPasswordQuality = deviceState.dpc().devicePolicyManager().getPasswordQuality(
            deviceState.dpc().componentName()
        )
        try {
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                DevicePolicyManager.PASSWORD_QUALITY_NUMERIC
            )
            Assert.assertThrows(
                IllegalStateException::class.java
            ) {
                deviceState.dpc().devicePolicyManager().setPasswordMinimumUpperCase(
                    deviceState.dpc().componentName(),
                    TEST_VALUE
                )
            }
        } finally {
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                initialPasswordQuality
            )
        }
    }

    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(PackageManager.FEATURE_AUTOMOTIVE)
    @PolicyAppliesTest(
        policy = [PasswordQuality::class]
    )
    @Postsubmit(reason = "New test")
    @AdditionalQueryParameters(
        forTestApp = "dpc",
        query = Query(targetSdkVersion = IntegerQuery(isGreaterThan = R))
    )
    fun setPasswordNumericQuality_setPasswordMinimumLowerCase_targetAfterR_throwsException() {
        val initialPasswordQuality = deviceState.dpc().devicePolicyManager().getPasswordQuality(
            deviceState.dpc().componentName()
        )
        try {
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                DevicePolicyManager.PASSWORD_QUALITY_NUMERIC
            )
            Assert.assertThrows(
                IllegalStateException::class.java
            ) {
                deviceState.dpc().devicePolicyManager().setPasswordMinimumLowerCase(
                    deviceState.dpc().componentName(),
                    TEST_VALUE
                )
            }
        } finally {
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                initialPasswordQuality
            )
        }
    }

    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(PackageManager.FEATURE_AUTOMOTIVE)
    @PolicyAppliesTest(
        policy = [PasswordQuality::class]
    )
    @Postsubmit(reason = "New test")
    @AdditionalQueryParameters(
        forTestApp = "dpc",
        query = Query(targetSdkVersion = IntegerQuery(isGreaterThan = R))
    )
    fun setPasswordNumericQuality_setPasswordMinimumNonLetter_targetAfterR_throwsException() {
        val initialPasswordQuality = deviceState.dpc().devicePolicyManager().getPasswordQuality(
            deviceState.dpc().componentName()
        )
        try {
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                DevicePolicyManager.PASSWORD_QUALITY_NUMERIC
            )
            Assert.assertThrows(
                IllegalStateException::class.java
            ) {
                deviceState.dpc().devicePolicyManager().setPasswordMinimumNonLetter(
                    deviceState.dpc().componentName(),
                    TEST_VALUE
                )
            }
        } finally {
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                initialPasswordQuality
            )
        }
    }

    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(PackageManager.FEATURE_AUTOMOTIVE)
    @PolicyAppliesTest(
        policy = [PasswordQuality::class]
    )
    @Postsubmit(reason = "New test")
    @AdditionalQueryParameters(
        forTestApp = "dpc",
        query = Query(targetSdkVersion = IntegerQuery(isGreaterThan = R))
    )
    fun setPasswordHighQuality_setPasswordMinimumLength_setPasswordNumericQuality_targetAfterR_preservesValue() {

        val initialPasswordQuality = deviceState.dpc().devicePolicyManager().getPasswordQuality(
            deviceState.dpc().componentName()
        )

        try {
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                DevicePolicyManager.PASSWORD_QUALITY_COMPLEX
            )

            deviceState.dpc().devicePolicyManager().setPasswordMinimumLength(
                deviceState.dpc().componentName(),
                TEST_VALUE
            )

            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                DevicePolicyManager.PASSWORD_QUALITY_NUMERIC
            )

            assertThat(deviceState.dpc().devicePolicyManager()
                    .getPasswordMinimumLength(deviceState.dpc().componentName()))
                    .isEqualTo(TEST_VALUE)
        } finally {
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                initialPasswordQuality
            )
        }
    }

    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(PackageManager.FEATURE_AUTOMOTIVE)
    @PolicyAppliesTest(
        policy = [PasswordQuality::class]
    )
    @Postsubmit(reason = "New test")
    @AdditionalQueryParameters(
        forTestApp = "dpc",
        query = Query(targetSdkVersion = IntegerQuery(isGreaterThan = R))
    )
    fun setPasswordHighQuality_setPasswordMinimumLength_setPasswordLowQuality_targetAfterR_resetsValue() {

        val initialPasswordQuality = deviceState.dpc().devicePolicyManager().getPasswordQuality(
            deviceState.dpc().componentName())

        try {
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                DevicePolicyManager.PASSWORD_QUALITY_COMPLEX
            )

            deviceState.dpc().devicePolicyManager().setPasswordMinimumLength(
                deviceState.dpc().componentName(),
                TEST_VALUE
            )

            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                DevicePolicyManager.PASSWORD_QUALITY_SOMETHING
            )

            assertThat(deviceState.dpc().devicePolicyManager()
                    .getPasswordMinimumLength(deviceState.dpc().componentName()))
                    .isEqualTo(DEFAULT_LENGTH)
        } finally {
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                initialPasswordQuality
            )
        }
    }

    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(PackageManager.FEATURE_AUTOMOTIVE)
    @PolicyAppliesTest(
        policy = [PasswordQuality::class]
    )
    @Postsubmit(reason = "New test")
    @AdditionalQueryParameters(
        forTestApp = "dpc",
        query = Query(targetSdkVersion = IntegerQuery(isGreaterThan = R))
    )
    fun setPasswordHighQuality_setPasswordMinimumNumeric_setPasswordNumericQuality_targetAfterR_resetsValue() {

        val initialPasswordQuality = deviceState.dpc().devicePolicyManager().getPasswordQuality(
            deviceState.dpc().componentName())

        try {
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                DevicePolicyManager.PASSWORD_QUALITY_COMPLEX
            )

            deviceState.dpc().devicePolicyManager().setPasswordMinimumNumeric(
                deviceState.dpc().componentName(),
                TEST_VALUE
            )

            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                DevicePolicyManager.PASSWORD_QUALITY_NUMERIC
            )

            assertThat(deviceState.dpc().devicePolicyManager()
                    .getPasswordMinimumNumeric(deviceState.dpc().componentName()))
                    .isEqualTo(DEFAULT_NUMERIC)
        } finally {
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                initialPasswordQuality
            )
        }
    }

    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(PackageManager.FEATURE_AUTOMOTIVE)
    @PolicyAppliesTest(
        policy = [PasswordQuality::class]
    )
    @Postsubmit(reason = "New test")
    @AdditionalQueryParameters(
        forTestApp = "dpc",
        query = Query(targetSdkVersion = IntegerQuery(isGreaterThan = R))
    )
    fun setPasswordHighQuality_setPasswordMinimumLetters_setPasswordNumericQuality_targetAfterR_resetsValue() {

        val initialPasswordQuality = deviceState.dpc().devicePolicyManager().getPasswordQuality(
            deviceState.dpc().componentName()
        )

        try {
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                DevicePolicyManager.PASSWORD_QUALITY_COMPLEX
            )

            deviceState.dpc().devicePolicyManager().setPasswordMinimumLetters(
                deviceState.dpc().componentName(),
                TEST_VALUE
            )

            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                DevicePolicyManager.PASSWORD_QUALITY_NUMERIC
            )

            assertThat(deviceState.dpc().devicePolicyManager()
                    .getPasswordMinimumLetters(deviceState.dpc().componentName()))
                    .isEqualTo(DEFAULT_LETTERS)
        } finally {
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                initialPasswordQuality
            )
        }
    }

    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(PackageManager.FEATURE_AUTOMOTIVE)
    @PolicyAppliesTest(
        policy = [PasswordQuality::class]
    )
    @Postsubmit(reason = "New test")
    @AdditionalQueryParameters(
        forTestApp = "dpc",
        query = Query(targetSdkVersion = IntegerQuery(isGreaterThan = R))
    )
    fun setPasswordHighQuality_setPasswordMinimumUpperCase_setPasswordNumericQuality_targetAfterR_resetsValue() {

        val initialPasswordQuality = deviceState.dpc().devicePolicyManager().getPasswordQuality(
            deviceState.dpc().componentName()
        )

        try {
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                DevicePolicyManager.PASSWORD_QUALITY_COMPLEX
            )

            deviceState.dpc().devicePolicyManager().setPasswordMinimumUpperCase(
                deviceState.dpc().componentName(),
                TEST_VALUE
            )

            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                DevicePolicyManager.PASSWORD_QUALITY_NUMERIC
            )

            assertThat(deviceState.dpc().devicePolicyManager()
                    .getPasswordMinimumUpperCase(deviceState.dpc().componentName()))
                    .isEqualTo(DEFAULT_UPPERCASE)
        } finally {
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                initialPasswordQuality
            )
        }
    }

    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(PackageManager.FEATURE_AUTOMOTIVE)
    @PolicyAppliesTest(
        policy = [PasswordQuality::class]
    )
    @Postsubmit(reason = "New test")
    @AdditionalQueryParameters(
        forTestApp = "dpc",
        query = Query(targetSdkVersion = IntegerQuery(isGreaterThan = R))
    )
    fun setPasswordHighQuality_setPasswordMinimumLowerCase_setPasswordNumericQuality_targetAfterR_resetsValue() {

        val initialPasswordQuality = deviceState.dpc().devicePolicyManager().getPasswordQuality(
            deviceState.dpc().componentName()
        )
        try {
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                DevicePolicyManager.PASSWORD_QUALITY_COMPLEX
            )

            deviceState.dpc().devicePolicyManager().setPasswordMinimumLowerCase(
                deviceState.dpc().componentName(),
                TEST_VALUE
            )

            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                DevicePolicyManager.PASSWORD_QUALITY_NUMERIC
            )

            assertThat(deviceState.dpc().devicePolicyManager()
                    .getPasswordMinimumLowerCase(deviceState.dpc().componentName()))
                    .isEqualTo(DEFAULT_LOWERCASE)
        } finally {
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                initialPasswordQuality
            )
        }
    }

    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(PackageManager.FEATURE_AUTOMOTIVE)
    @PolicyAppliesTest(
        policy = [PasswordQuality::class]
    )
    @Postsubmit(reason = "New test")
    @AdditionalQueryParameters(
        forTestApp = "dpc",
        query = Query(targetSdkVersion = IntegerQuery(isGreaterThan = R))
    )
    fun setPasswordHighQuality_setPasswordMinimumNonLetter_setPasswordNumericQuality_targetAfterR_resetsValue() {

        val initialPasswordQuality = deviceState.dpc().devicePolicyManager().getPasswordQuality(
            deviceState.dpc().componentName()
        )

        try {
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                DevicePolicyManager.PASSWORD_QUALITY_COMPLEX
            )

            deviceState.dpc().devicePolicyManager().setPasswordMinimumNonLetter(
                deviceState.dpc().componentName(),
                TEST_VALUE
            )

            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                DevicePolicyManager.PASSWORD_QUALITY_NUMERIC
            )

            assertThat(deviceState.dpc().devicePolicyManager()
                    .getPasswordMinimumNonLetter(deviceState.dpc().componentName()))
                    .isEqualTo(DEFAULT_NON_LETTER)
        } finally {
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                initialPasswordQuality
            )
        }
    }

    // setPasswordQuality is unsupported on automotive
    @RequireDoesNotHaveFeature(PackageManager.FEATURE_AUTOMOTIVE)
    @PolicyAppliesTest(
        policy = [PasswordQuality::class]
    )
    @Postsubmit(reason = "New test")
    @AdditionalQueryParameters(
        forTestApp = "dpc",
        query = Query(targetSdkVersion = IntegerQuery(isGreaterThan = R))
    )
    fun setPasswordHighQuality_setPasswordMinimumSymbols_setPasswordNumericQuality_targetAfterR_resetsValue() {

        val initialPasswordQuality = deviceState.dpc().devicePolicyManager().getPasswordQuality(
            deviceState.dpc().componentName()
        )

        try {
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                DevicePolicyManager.PASSWORD_QUALITY_COMPLEX
            )

            deviceState.dpc().devicePolicyManager().setPasswordMinimumSymbols(
                deviceState.dpc().componentName(),
                TEST_VALUE
            )

            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                DevicePolicyManager.PASSWORD_QUALITY_NUMERIC
            )

            assertThat(deviceState.dpc().devicePolicyManager()
                    .getPasswordMinimumSymbols(deviceState.dpc().componentName()))
                    .isEqualTo(DEFAULT_SYMBOLS)
        } finally {
            deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                initialPasswordQuality
            )
        }
    }

    /** Setting password quality to PASSWORD_QUALITY_COMPLEX is required to set PasswordMinimumLength. */
    @RequireDoesNotHaveFeature(PackageManager.FEATURE_AUTOMOTIVE)
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(
            policy = [PasswordQuality::class]
    )
    fun setPasswordMinimumLength_isLogged() {
        val initialPasswordQuality = getInitialPasswordQuality()
        val initialMinimumLength = deviceState.dpc().devicePolicyManager().getPasswordMinimumLength(
                deviceState.dpc().componentName()
        )

        try {
            setPasswordQuality(DevicePolicyManager.PASSWORD_QUALITY_COMPLEX)

            EnterpriseMetricsRecorder.create().use { metrics ->
                deviceState.dpc().devicePolicyManager().setPasswordMinimumLength(
                        deviceState.dpc().componentName(),
                        PASSWORD_QUALITY_LOG_TEST_VALUE
                )

                assertThat(
                        metrics.query()
                                .whereType().isEqualTo(EventId.SET_PASSWORD_MINIMUM_LENGTH_VALUE)
                                .whereAdminPackageName().isEqualTo(deviceState.dpc().packageName())
                                .whereInteger().isEqualTo(PASSWORD_QUALITY_LOG_TEST_VALUE)
                )
                        .wasLogged()
            }
        } finally {
            deviceState.dpc().devicePolicyManager().setPasswordMinimumLength(
                    deviceState.dpc().componentName(),
                    initialMinimumLength
            )
            setPasswordQuality(initialPasswordQuality)
        }
    }

    /** Setting password quality to PASSWORD_QUALITY_COMPLEX is required to set MinimumNumeric. */
    @RequireDoesNotHaveFeature(PackageManager.FEATURE_AUTOMOTIVE)
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(
            policy = [PasswordQuality::class]
    )
    fun setPasswordMinimumNumeric_isLogged() {
        val initialPasswordQuality = getInitialPasswordQuality()
        val initialMinimumNumeric = deviceState.dpc().devicePolicyManager().getPasswordMinimumNumeric(
                deviceState.dpc().componentName()
        )

        try {
            setPasswordQuality(DevicePolicyManager.PASSWORD_QUALITY_COMPLEX)

            EnterpriseMetricsRecorder.create().use { metrics ->
                deviceState.dpc().devicePolicyManager().setPasswordMinimumNumeric(
                        deviceState.dpc().componentName(),
                        PASSWORD_QUALITY_LOG_TEST_VALUE
                )

                assertThat(
                        metrics.query()
                                .whereType().isEqualTo(EventId.SET_PASSWORD_MINIMUM_NUMERIC_VALUE)
                                .whereAdminPackageName().isEqualTo(deviceState.dpc().packageName())
                                .whereInteger().isEqualTo(PASSWORD_QUALITY_LOG_TEST_VALUE)
                )
                        .wasLogged()
            }
        } finally {
            deviceState.dpc().devicePolicyManager().setPasswordMinimumNumeric(
                    deviceState.dpc().componentName(),
                    initialMinimumNumeric
            )

            setPasswordQuality(initialPasswordQuality)
        }
    }

    /** Setting password quality to PASSWORD_QUALITY_COMPLEX is required to set PasswordMinimumNonLetter. */
    @RequireDoesNotHaveFeature(PackageManager.FEATURE_AUTOMOTIVE)
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(
            policy = [PasswordQuality::class]
    )
    fun setPasswordMinimumNonLetter_isLogged() {
        val initialPasswordQuality = getInitialPasswordQuality()
        val initialMinimumNonLetter = deviceState.dpc().devicePolicyManager().getPasswordMinimumNonLetter(
                deviceState.dpc().componentName()
        )

        try {
            setPasswordQuality(DevicePolicyManager.PASSWORD_QUALITY_COMPLEX)

            EnterpriseMetricsRecorder.create().use { metrics ->
                deviceState.dpc().devicePolicyManager().setPasswordMinimumNonLetter(
                        deviceState.dpc().componentName(),
                        PASSWORD_QUALITY_LOG_TEST_VALUE
                )

                assertThat(
                        metrics.query()
                                .whereType().isEqualTo(EventId.SET_PASSWORD_MINIMUM_NON_LETTER_VALUE)
                                .whereAdminPackageName().isEqualTo(deviceState.dpc().packageName())
                                .whereInteger().isEqualTo(PASSWORD_QUALITY_LOG_TEST_VALUE)
                )
                        .wasLogged()
            }
        } finally {
            deviceState.dpc().devicePolicyManager().setPasswordMinimumNonLetter(
                    deviceState.dpc().componentName(),
                    initialMinimumNonLetter
            )

            setPasswordQuality(initialPasswordQuality)
        }
    }

    /** Setting password quality to PASSWORD_QUALITY_COMPLEX is required to set PasswordMinimumLetters. */
    @RequireDoesNotHaveFeature(PackageManager.FEATURE_AUTOMOTIVE)
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(
            policy = [PasswordQuality::class]
    )
    fun setPasswordMinimumLetters_isLogged() {
        val initialPasswordQuality = getInitialPasswordQuality()
        val initialMinimumLetters = deviceState.dpc().devicePolicyManager().getPasswordMinimumLetters(
                deviceState.dpc().componentName()
        )

        try {
            setPasswordQuality(DevicePolicyManager.PASSWORD_QUALITY_COMPLEX)

            EnterpriseMetricsRecorder.create().use { metrics ->
                deviceState.dpc().devicePolicyManager().setPasswordMinimumLetters(
                        deviceState.dpc().componentName(),
                        PASSWORD_QUALITY_LOG_TEST_VALUE
                )

                assertThat(
                        metrics.query()
                                .whereType().isEqualTo(EventId.SET_PASSWORD_MINIMUM_LETTERS_VALUE)
                                .whereAdminPackageName().isEqualTo(deviceState.dpc().packageName())
                                .whereInteger().isEqualTo(PASSWORD_QUALITY_LOG_TEST_VALUE)
                )
                        .wasLogged()
            }
        } finally {
            deviceState.dpc().devicePolicyManager().setPasswordMinimumLetters(
                    deviceState.dpc().componentName(),
                    initialMinimumLetters
            )

            setPasswordQuality(initialPasswordQuality)
        }
    }

    /** Setting password quality to PASSWORD_QUALITY_COMPLEX is required to set PasswordMinimumLowerCase. */
    @RequireDoesNotHaveFeature(PackageManager.FEATURE_AUTOMOTIVE)
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(
            policy = [PasswordQuality::class]
    )
    fun setPasswordMinimumLowerCase_isLogged() {
        val initialPasswordQuality = getInitialPasswordQuality()
        val initialMinimumLowerCase = deviceState.dpc().devicePolicyManager().getPasswordMinimumLowerCase(
                deviceState.dpc().componentName()
        )

        try {
            setPasswordQuality(DevicePolicyManager.PASSWORD_QUALITY_COMPLEX)

            EnterpriseMetricsRecorder.create().use { metrics ->
                deviceState.dpc().devicePolicyManager().setPasswordMinimumLowerCase(
                        deviceState.dpc().componentName(),
                        PASSWORD_QUALITY_LOG_TEST_VALUE
                )

                assertThat(
                        metrics.query()
                                .whereType().isEqualTo(EventId.SET_PASSWORD_MINIMUM_LOWER_CASE_VALUE)
                                .whereAdminPackageName().isEqualTo(deviceState.dpc().packageName())
                                .whereInteger().isEqualTo(PASSWORD_QUALITY_LOG_TEST_VALUE)
                )
                        .wasLogged()
            }
        } finally {
            deviceState.dpc().devicePolicyManager().setPasswordMinimumLowerCase(
                    deviceState.dpc().componentName(),
                    initialMinimumLowerCase
            )

            setPasswordQuality(initialPasswordQuality)
        }
    }

    /** Setting password quality to PASSWORD_QUALITY_COMPLEX is required to set PasswordMinimumUpperCase. */
    @RequireDoesNotHaveFeature(PackageManager.FEATURE_AUTOMOTIVE)
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(
            policy = [PasswordQuality::class]
    )
    fun setPasswordMinimumUpperCase_isLogged() {
        val initialPasswordQuality = getInitialPasswordQuality()
        val initialMinimumUpperCase = deviceState.dpc().devicePolicyManager().getPasswordMinimumUpperCase(
                deviceState.dpc().componentName()
        )

        try {
            setPasswordQuality(DevicePolicyManager.PASSWORD_QUALITY_COMPLEX)

            EnterpriseMetricsRecorder.create().use { metrics ->
                deviceState.dpc().devicePolicyManager().setPasswordMinimumUpperCase(
                        deviceState.dpc().componentName(),
                        PASSWORD_QUALITY_LOG_TEST_VALUE
                )

                assertThat(
                        metrics.query()
                                .whereType().isEqualTo(EventId.SET_PASSWORD_MINIMUM_UPPER_CASE_VALUE)
                                .whereAdminPackageName().isEqualTo(deviceState.dpc().packageName())
                                .whereInteger().isEqualTo(PASSWORD_QUALITY_LOG_TEST_VALUE)
                )
                        .wasLogged()
            }
        } finally {
            deviceState.dpc().devicePolicyManager().setPasswordMinimumUpperCase(
                    deviceState.dpc().componentName(),
                    initialMinimumUpperCase
            )

            setPasswordQuality(initialPasswordQuality)
        }
    }

    /** Setting password quality to PASSWORD_QUALITY_COMPLEX is required to set PasswordMinimumSymbols. */
    @RequireDoesNotHaveFeature(PackageManager.FEATURE_AUTOMOTIVE)
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(
            policy = [PasswordQuality::class]
    )
    fun setPasswordMinimumSymbols_isLogged() {
        val initialPasswordQuality = getInitialPasswordQuality()
        val initialMinimumSymbols = deviceState.dpc().devicePolicyManager().getPasswordMinimumSymbols(
                deviceState.dpc().componentName()
        )

        try {
            setPasswordQuality(DevicePolicyManager.PASSWORD_QUALITY_COMPLEX)

            EnterpriseMetricsRecorder.create().use { metrics ->
                deviceState.dpc().devicePolicyManager().setPasswordMinimumSymbols(
                        deviceState.dpc().componentName(),
                        PASSWORD_QUALITY_LOG_TEST_VALUE
                )

                assertThat(
                        metrics.query()
                                .whereType().isEqualTo(EventId.SET_PASSWORD_MINIMUM_SYMBOLS_VALUE)
                                .whereAdminPackageName().isEqualTo(deviceState.dpc().packageName())
                                .whereInteger().isEqualTo(PASSWORD_QUALITY_LOG_TEST_VALUE)
                )
                        .wasLogged()
            }
        } finally {
            deviceState.dpc().devicePolicyManager().setPasswordMinimumSymbols(
                    deviceState.dpc().componentName(),
                    initialMinimumSymbols
            )

            setPasswordQuality(initialPasswordQuality)
        }
    }

    @RequireDoesNotHaveFeature(PackageManager.FEATURE_AUTOMOTIVE)
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(
            policy = [PasswordQuality::class]
    )
    fun setPasswordQuality_isLogged() {
        val initialPasswordQuality = getInitialPasswordQuality()

        try {
            EnterpriseMetricsRecorder.create().use { metrics ->
                setPasswordQuality(DevicePolicyManager.PASSWORD_QUALITY_COMPLEX)

                assertThat(
                        metrics.query()
                                .whereType().isEqualTo(EventId.SET_PASSWORD_QUALITY_VALUE)
                                .whereAdminPackageName().isEqualTo(deviceState.dpc().packageName())
                                .whereInteger().isEqualTo(DevicePolicyManager.PASSWORD_QUALITY_COMPLEX)
                                .whereStrings().contains(NOT_CALLED_FROM_PARENT)
                ).wasLogged()
            }
        } finally {
            setPasswordQuality(initialPasswordQuality)
        }
    }

    private fun getInitialPasswordQuality(): Int {
        return deviceState.dpc().devicePolicyManager().getPasswordQuality(
                deviceState.dpc().componentName()
        )
    }

    private fun setPasswordQuality(passwordQuality: Int) {
        deviceState.dpc().devicePolicyManager().setPasswordQuality(
                deviceState.dpc().componentName(),
                passwordQuality
        )
    }

    companion object {
        private const val TIMEOUT: Long = 51234
        private const val PASSWORD_MEDIUM_COMPLEXITY = "abc12"

        private const val TEST_VALUE: Int = 5
        private const val DEFAULT_LENGTH = 0
        private const val DEFAULT_NUMERIC = 1
        private const val DEFAULT_LETTERS = 1
        private const val DEFAULT_UPPERCASE = 0
        private const val DEFAULT_LOWERCASE = 0
        private const val DEFAULT_NON_LETTER = 0
        private const val DEFAULT_SYMBOLS = 1

        private const val NOT_CALLED_FROM_PARENT = "notCalledFromParent"
        private const val PASSWORD_QUALITY_LOG_TEST_VALUE = 13

        @JvmField
        @ClassRule
        @Rule
        val deviceState = DeviceState()
    }
}
