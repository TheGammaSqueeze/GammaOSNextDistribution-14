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

package android.devicepolicy.cts;

import static android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
import static android.provider.Settings.Global.AIRPLANE_MODE_ON;

import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_AIRPLANE_MODE;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHaveUserRestriction;
import com.android.bedstead.harrier.annotations.EnsureHasUserRestriction;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.AdditionalQueryParameters;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.policies.DisallowAirplaneMode;
import com.android.bedstead.harrier.policies.DisallowAirplaneModePermissionBased;
import com.android.bedstead.nene.TestApis;
import com.android.compatibility.common.util.ApiTest;
import com.android.interactive.Step;
import com.android.interactive.annotations.Interactive;
import com.android.interactive.annotations.NotFullyAutomated;
import com.android.interactive.steps.enterprise.settings.NavigateToPersonalNetworkSettingsStep;
import com.android.interactive.steps.settings.CanYouTurnOnAirplaneModeStep;
import com.android.interactive.steps.settings.IsThereTextExplainingThatAnITAdminHasLimitedThisFunctionalityStep;
import com.android.queryable.annotations.IntegerQuery;
import com.android.queryable.annotations.Query;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class AirplaneModeTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @CannotSetPolicyTest(policy = DisallowAirplaneMode.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_AIRPLANE_MODE")
    public void addUserRestriction_disallowAirplaneMode_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), DISALLOW_AIRPLANE_MODE));
    }

    @PolicyAppliesTest(policy = DisallowAirplaneMode.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_AIRPLANE_MODE")
    public void addUserRestriction_disallowAirplaneMode_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_AIRPLANE_MODE);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_AIRPLANE_MODE))
                    .isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_AIRPLANE_MODE);
        }
    }

    @PolicyAppliesTest(policy = DisallowAirplaneModePermissionBased.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_AIRPLANE_MODE")
    // TODO: Add restriction for permission based targeting U+
    @AdditionalQueryParameters(
            forTestApp = "dpc",
            query = @Query(targetSdkVersion =
            @IntegerQuery(isGreaterThanOrEqualTo = UPSIDE_DOWN_CAKE))
    )
    public void addUserRestriction_disallowAirplaneMode_targetAtLeastU_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_AIRPLANE_MODE);
        });
    }

    @CanSetPolicyTest(policy = DisallowAirplaneModePermissionBased.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_AIRPLANE_MODE")
    // TODO: Add restriction for permission based targeting U+
    // TODO: Test that this is actually global
    public void addUserRestrictionGlobally_disallowAirplaneMode_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestrictionGlobally(
                    DISALLOW_AIRPLANE_MODE);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_AIRPLANE_MODE))
                    .isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_AIRPLANE_MODE);
        }
    }

    @CanSetPolicyTest(policy = DisallowAirplaneModePermissionBased.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_AIRPLANE_MODE")
    // TODO: Add restriction for permission based targeting U+
    // TODO: Test that this is actually global
    public void clearUserRestriction_disallowAirplaneMode_isNotSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestrictionGlobally(
                    DISALLOW_AIRPLANE_MODE);

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_AIRPLANE_MODE);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_AIRPLANE_MODE))
                    .isFalse();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_AIRPLANE_MODE);
        }
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_AIRPLANE_MODE)
    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_AIRPLANE_MODE")
    public void disallowAirplaneModeIsNotSet_canTurnOnAirplaneMode() throws Exception {
        try {
            TestApis.settings().global().putInt(AIRPLANE_MODE_ON, 1);

            assertThat(TestApis.settings().global().getInt(AIRPLANE_MODE_ON)).isEqualTo(1);
        } finally {
            TestApis.settings().global().putInt(AIRPLANE_MODE_ON, 0);
        }
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_AIRPLANE_MODE)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_AIRPLANE_MODE")
    @NotFullyAutomated(reason = "CanYouTurnOnAirplaneModeStep") // TODO: Automate
    public void disallowAirplaneModeIsNotSet_canTurnOnAirplaneModeInUi() throws Exception {
        Step.execute(NavigateToPersonalNetworkSettingsStep.class);

        assertThat(Step.execute(CanYouTurnOnAirplaneModeStep.class)).isTrue();
    }

    @EnsureHasUserRestriction(DISALLOW_AIRPLANE_MODE)
    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_AIRPLANE_MODE")
    public void disallowAirplaneModeIsSet_canNotTurnOnAirplaneMode() throws Exception {
        try {
            TestApis.settings().global().putInt(AIRPLANE_MODE_ON, 1);

            assertThat(TestApis.settings().global().getInt(AIRPLANE_MODE_ON)).isEqualTo(0);
        } finally {
            TestApis.settings().global().putInt(AIRPLANE_MODE_ON, 0);

        }
    }

    @EnsureHasUserRestriction(DISALLOW_AIRPLANE_MODE)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_AIRPLANE_MODE")
    @NotFullyAutomated(reason = "CanYouTurnOnAirplaneModeStep") // TODO: Automate
    public void disallowAirplaneModeIsSet_canNotTurnOnAirplaneModeInUi() throws Exception {
        Step.execute(NavigateToPersonalNetworkSettingsStep.class);

        assertThat(Step.execute(CanYouTurnOnAirplaneModeStep.class)).isFalse();
        assertThat(
                Step.execute(IsThereTextExplainingThatAnITAdminHasLimitedThisFunctionalityStep.class))
                .isTrue();
    }

}
