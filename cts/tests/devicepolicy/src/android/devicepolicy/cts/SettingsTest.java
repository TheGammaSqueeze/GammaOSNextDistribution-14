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

package android.devicepolicy.cts;

import static android.os.Build.VERSION_CODES.Q;
import static android.os.Build.VERSION_CODES.R;
import static android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
import static android.provider.Settings.Global.AIRPLANE_MODE_ON;
import static android.provider.Settings.Global.AUTO_TIME;
import static android.provider.Settings.Global.BLUETOOTH_ON;
import static android.provider.Settings.Secure.LOCATION_MODE;
import static android.provider.Settings.Secure.SKIP_FIRST_USE_HINTS;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.os.Build;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureNotDemoMode;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireTargetSdkVersion;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.policies.SetDeviceOwnerSecureSetting;
import com.android.bedstead.harrier.policies.SetGlobalSetting;
import com.android.bedstead.harrier.policies.SetSecureSetting;
import com.android.bedstead.nene.TestApis;
import com.android.queryable.annotations.IntegerQuery;
import com.android.queryable.annotations.Query;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class SettingsTest {
    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    // Deprecated in M
    private static final String DEPRECATED_GLOBAL_SETTING = BLUETOOTH_ON;
    private static final String UNSUPPORTED_GLOBAL_SETTING = AIRPLANE_MODE_ON;
    private static final String SUPPORTED_GLOBAL_SETTING = AUTO_TIME;

    //Deprecated in R
    private static final String DEPRECATED_DEVICE_OWNER_ONLY_SECURE_SETTING = LOCATION_MODE;

    private static final String SECURE_SETTING = SKIP_FIRST_USE_HINTS;

    @CanSetPolicyTest(policy = SetGlobalSetting.class)
    @Postsubmit(reason = "new test")
    public void setGlobalSetting_settingIsDeprecated_doesNotChangeSetting() {
        int originalValue = TestApis.settings().global().getInt(DEPRECATED_GLOBAL_SETTING);
        int newValue = originalValue + 1;

        try {
            sDeviceState.dpc().devicePolicyManager().setGlobalSetting(
                    sDeviceState.dpc().componentName(),
                    DEPRECATED_GLOBAL_SETTING, String.valueOf(newValue));

            assertThat(TestApis.settings().global().getInt(DEPRECATED_GLOBAL_SETTING))
                    .isEqualTo(originalValue);
        } finally {
            TestApis.settings().global().putInt(DEPRECATED_GLOBAL_SETTING, originalValue);
        }
    }

    @CannotSetPolicyTest(policy = SetGlobalSetting.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    public void setGlobalSetting_invalidAdmin_throwsSecurityException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().setGlobalSetting(
                        sDeviceState.dpc().componentName(),
                        SUPPORTED_GLOBAL_SETTING, "1"));
    }

    @CanSetPolicyTest(policy = SetGlobalSetting.class)
    @Postsubmit(reason = "new test")
    @EnsureNotDemoMode // retail demo mode bypasses global setting allowlist
    public void setGlobalSetting_unsupported_throwsSecurityException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().setGlobalSetting(
                        sDeviceState.dpc().componentName(),
                        UNSUPPORTED_GLOBAL_SETTING, "1"));
    }

    @CanSetPolicyTest(policy = SetGlobalSetting.class)
    @Postsubmit(reason = "new test")
    public void setGlobalSetting_supported_changesValue() {
        int originalValue = TestApis.settings().global().getInt(SUPPORTED_GLOBAL_SETTING);
        int newValue = originalValue + 1;

        try {
            sDeviceState.dpc().devicePolicyManager().setGlobalSetting(
                    sDeviceState.dpc().componentName(),
                    SUPPORTED_GLOBAL_SETTING, String.valueOf(newValue));

            assertThat(TestApis.settings().global().getInt(SUPPORTED_GLOBAL_SETTING))
                    .isEqualTo(newValue);
        } finally {
            TestApis.settings().global().putInt(SUPPORTED_GLOBAL_SETTING, originalValue);
        }
    }

    @PolicyAppliesTest(policy = SetSecureSetting.class)
    @Postsubmit(reason = "new test")
    public void setSecureSetting_sets() {
        int originalValue = TestApis.settings().secure()
                .getInt(SECURE_SETTING, /* defaultValue= */ 0);
        int newValue = originalValue + 1;

        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setSecureSetting(sDeviceState.dpc().componentName(),
                            SECURE_SETTING, String.valueOf(newValue));

            assertThat(TestApis.settings().secure()
                    .getInt(SECURE_SETTING, /* defaultValue= */  0)).isEqualTo(newValue);
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .setSecureSetting(sDeviceState.dpc().componentName(),
                            SECURE_SETTING, String.valueOf(originalValue));

        }
    }

    @CanSetPolicyTest(policy = SetDeviceOwnerSecureSetting.class)
    @Postsubmit(reason = "new test")
    @com.android.bedstead.harrier.annotations.enterprise.AdditionalQueryParameters(
            forTestApp = "dpc",
            query = @Query(targetSdkVersion = @IntegerQuery(isLessThan = Build.VERSION_CODES.R))
    )
    public void setSecureSetting_deviceOwnerOnly_sets() {
        int originalValue = TestApis.settings().secure()
                .getInt(DEPRECATED_DEVICE_OWNER_ONLY_SECURE_SETTING, /* defaultValue= */ 0);
        int newValue = originalValue + 1;

        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setSecureSetting(sDeviceState.dpc().componentName(),
                            DEPRECATED_DEVICE_OWNER_ONLY_SECURE_SETTING, String.valueOf(newValue));

            assertThat(TestApis.settings().secure().getInt(
                    DEPRECATED_DEVICE_OWNER_ONLY_SECURE_SETTING)).isEqualTo(newValue);
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .setSecureSetting(sDeviceState.dpc().componentName(),
                            DEPRECATED_DEVICE_OWNER_ONLY_SECURE_SETTING,
                            String.valueOf(originalValue));

        }
    }

    @CannotSetPolicyTest(
            policy = SetDeviceOwnerSecureSetting.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    public void setSecureSetting_deviceOwnerOnly_isNotDeviceOwner_throwsSecurityException() {
        int originalValue = TestApis.settings().secure()
                .getInt(DEPRECATED_DEVICE_OWNER_ONLY_SECURE_SETTING, /* defaultValue= */ 0);
        int newValue = originalValue + 1;

        assertThrows(SecurityException.class, () -> sDeviceState.dpc().devicePolicyManager()
                .setSecureSetting(sDeviceState.dpc().componentName(),
                        DEPRECATED_DEVICE_OWNER_ONLY_SECURE_SETTING, String.valueOf(newValue)));
    }

    @CanSetPolicyTest(policy = SetDeviceOwnerSecureSetting.class)
    @Postsubmit(reason = "new test")
    @com.android.bedstead.harrier.annotations.enterprise.AdditionalQueryParameters(
            forTestApp = "dpc",
            query = @Query(targetSdkVersion = @IntegerQuery(isGreaterThanOrEqualTo = R))
    )
    public void setSecureSetting_deviceOwnerOnly_settingIsDeprecated_throwsException() {
        int originalValue = TestApis.settings().secure()
                .getInt(DEPRECATED_DEVICE_OWNER_ONLY_SECURE_SETTING, /* defaultValue= */ 0);
        int newValue = originalValue + 1;

        assertThrows(UnsupportedOperationException.class, () -> sDeviceState.dpc()
                .devicePolicyManager().setSecureSetting(sDeviceState.dpc().componentName(),
                        DEPRECATED_DEVICE_OWNER_ONLY_SECURE_SETTING, String.valueOf(newValue)));
    }

    @PolicyDoesNotApplyTest(policy = SetSecureSetting.class)
    @Postsubmit(reason = "new test")
    public void setSecureSetting_doesNotApplyToUser_isNotSet() {
        int originalValue = TestApis.settings().secure()
                .getInt(SECURE_SETTING, /* defaultValue= */  0);
        int originalDpcValue = TestApis.settings().secure().getInt(sDeviceState.dpc().user(),
                SECURE_SETTING, /* defaultValue= */ 0);
        int newValue = originalValue + 1;

        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setSecureSetting(sDeviceState.dpc().componentName(),
                            SECURE_SETTING, String.valueOf(newValue));

            assertThat(TestApis.settings().secure().getInt(SECURE_SETTING, /* defaultValue= */  0))
                    .isEqualTo(originalValue);
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .setSecureSetting(sDeviceState.dpc().componentName(),
                            SECURE_SETTING, String.valueOf(originalDpcValue));

        }
    }
}
