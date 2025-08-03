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

import static android.content.pm.PackageManager.FEATURE_AUTOMOTIVE;
import static android.content.pm.PackageManager.FEATURE_SECURE_LOCK_SCREEN;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.app.admin.RemoteDevicePolicyManager;
import android.content.ComponentName;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireDoesNotHaveFeature;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.policies.LockscreenPolicyWithUnifiedChallenge;
import com.android.bedstead.harrier.policies.ScreenCaptureDisabled;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;

import org.junit.Assume;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.function.BiConsumer;

/**
 * Test that DevicePolicyManager getters that accept "ComponentName who" argument don't allow a
 * different app to probe for admins when policy is set: those getters should only allow either
 * calls where "who" is null or "who" is not null and belongs to caller. SecurityExceptions that are
 * thrown otherwise shouldn't leak that data either.
 */
// Password policies aren't supported on automotive
@RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
@RequireFeature(FEATURE_SECURE_LOCK_SCREEN)
@RunWith(BedsteadJUnit4.class)
public class NoAdminLeakingTest {
    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final TestApp sTestApp = sDeviceState.testApps().any();

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = LockscreenPolicyWithUnifiedChallenge.class)
    @Test
    public void testPasswordQuality_adminPolicyNotAvailableToNonAdmin() {
        assertOnlyAggregatePolicyAvailableToNonAdmin(
                (dpm, who) -> dpm.getPasswordQuality(who));
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = LockscreenPolicyWithUnifiedChallenge.class)
    @Test
    public void testPasswordMinimumLength_adminPolicyNotAvailableToNonAdmin() {
        assertOnlyAggregatePolicyAvailableToNonAdmin(
                (dpm, who) -> dpm.getPasswordMinimumLength(who));
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = LockscreenPolicyWithUnifiedChallenge.class)
    @Test
    public void testPasswordMinimumLetters_adminPolicyNotAvailableToNonAdmin() {
        assertOnlyAggregatePolicyAvailableToNonAdmin(
                (dpm, who) -> dpm.getPasswordMinimumLetters(who));
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = LockscreenPolicyWithUnifiedChallenge.class)
    @Test
    public void testPasswordMinimumNonLetter_adminPolicyNotAvailableToNonAdmin() {
        assertOnlyAggregatePolicyAvailableToNonAdmin(
                (dpm, who) -> dpm.getPasswordMinimumNonLetter(who));
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = LockscreenPolicyWithUnifiedChallenge.class)
    @Test
    public void testPasswordMinimumLowerCase_adminPolicyNotAvailableToNonAdmin() {
        assertOnlyAggregatePolicyAvailableToNonAdmin(
                (dpm, who) -> dpm.getPasswordMinimumLowerCase(who));
    }

    @Postsubmit(reason = "new test")
    @Test
    @CanSetPolicyTest(policy = LockscreenPolicyWithUnifiedChallenge.class)
    public void testPasswordMinimumUpperCase_adminPolicyNotAvailableToNonAdmin() {
        assertOnlyAggregatePolicyAvailableToNonAdmin(
                (dpm, who) -> dpm.getPasswordMinimumUpperCase(who));
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = LockscreenPolicyWithUnifiedChallenge.class)
    @Test
    public void testPasswordMinimumNumeric_adminPolicyNotAvailableToNonAdmin() {
        assertOnlyAggregatePolicyAvailableToNonAdmin(
                (dpm, who) -> dpm.getPasswordMinimumNumeric(who));
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = LockscreenPolicyWithUnifiedChallenge.class)
    @Test
    public void testPasswordMinimumSymbols_adminPolicyNotAvailableToNonAdmin() {
        assertOnlyAggregatePolicyAvailableToNonAdmin(
                (dpm, who) -> dpm.getPasswordMinimumSymbols(who));
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = LockscreenPolicyWithUnifiedChallenge.class)
    @Test
    public void testPasswordHistoryLength_adminPolicyNotAvailableToNonAdmin() {
        assertOnlyAggregatePolicyAvailableToNonAdmin(
                (dpm, who) -> dpm.getPasswordHistoryLength(who));
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = LockscreenPolicyWithUnifiedChallenge.class)
    @Test
    public void testPasswordExpiration_adminPolicyNotAvailableToNonAdmin() {
        assertOnlyAggregatePolicyAvailableToNonAdmin(
                (dpm, who) -> dpm.getPasswordExpiration(who));
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = LockscreenPolicyWithUnifiedChallenge.class)
    @Test
    public void testPasswordExpirationTimeout_adminPolicyNotAvailableToNonAdmin() {
        assertOnlyAggregatePolicyAvailableToNonAdmin(
                (dpm, who) -> dpm.getPasswordExpirationTimeout(who));
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = LockscreenPolicyWithUnifiedChallenge.class)
    @Test
    public void testMaximumFailedPasswordsForWipe_adminPolicyNotAvailableToNonAdmin() {
        assertOnlyAggregatePolicyAvailableToNonAdmin(
                (dpm, who) -> dpm.getMaximumFailedPasswordsForWipe(who));
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = LockscreenPolicyWithUnifiedChallenge.class)
    @Test
    public void testMaximumTimeToLock_adminPolicyNotAvailableToNonAdmin() {
        assertOnlyAggregatePolicyAvailableToNonAdmin(
                (dpm, who) -> dpm.getMaximumTimeToLock(who));
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = LockscreenPolicyWithUnifiedChallenge.class)
    @Test
    public void testRequiredStrongAuthTimeout_adminPolicyNotAvailableToNonAdmin() {
        assertOnlyAggregatePolicyAvailableToNonAdmin(
                (dpm, who) -> dpm.getRequiredStrongAuthTimeout(who));
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = ScreenCaptureDisabled.class)
    @Test
    public void testScreenCaptureDisabled_adminPolicyNotAvailableToNonAdmin() {
        Assume.assumeFalse("Test not suitable for non-deviceadmins",
                sDeviceState.dpc().componentName() == null);
        assertOnlyAggregatePolicyAvailableToNonAdmin(
                (dpm, who) -> dpm.getScreenCaptureDisabled(who));
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = LockscreenPolicyWithUnifiedChallenge.class)
    @Test
    public void testTrustAgentConfiguration_adminPolicyNotAvailableToNonAdmin() {
        assertOnlyAggregatePolicyAvailableToNonAdmin(
                (dpm, who) -> dpm.getTrustAgentConfiguration(who,
                        sDeviceState.dpc().componentName()
                        /* agent component, need to be non-null */));
    }

    // TODO(b/210996030): replace this with test method parametrization and separate "null" case.
    private void assertOnlyAggregatePolicyAvailableToNonAdmin(
            BiConsumer<RemoteDevicePolicyManager, ComponentName> accessor) {
        try (TestAppInstance testApp = sTestApp.install()) {
            // Invoking with null admin should not throw.
            accessor.accept(testApp.devicePolicyManager(), /* who= */ null);

            SecurityException adminPackageEx = null;
            try {
                // Requesting policy for an admin from a different app should throw.
                accessor.accept(testApp.devicePolicyManager(),
                        sDeviceState.dpc().componentName());
                fail("Checking particular admin policy shouldn't be allowed for non admin");
            } catch (SecurityException e) {
                adminPackageEx = e;
            }

            ComponentName nonexistentComponent = new ComponentName("bad_pkg_123", "bad_clz_456");
            SecurityException nonexistentPackageEx = null;
            try {
                // Requesting policy for a nonexistent admin should throw.
                accessor.accept(testApp.devicePolicyManager(), nonexistentComponent);
                fail("Querying policy for non-existent admin should have thrown an exception");
            } catch (SecurityException e) {
                nonexistentPackageEx = e;
            }

            // Both exceptions should have the same message (except package name) to avoid revealing
            // admin existence.
            String adminMessage = adminPackageEx.getMessage()
                    .replace(sDeviceState.dpc().componentName().toString(), "");
            String nonexistentMessage = nonexistentPackageEx.getMessage()
                    .replace(nonexistentComponent.toString(), "");
            assertThat(adminMessage).isEqualTo(nonexistentMessage);
        }
    }
}
