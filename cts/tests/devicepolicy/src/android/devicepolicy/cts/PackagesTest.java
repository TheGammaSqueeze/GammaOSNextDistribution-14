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

import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.ENSURE_VERIFY_APPS;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.util.Log;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.AfterClass;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.policies.EnsureVerifyApps;
import com.android.bedstead.harrier.policies.SuspendPackage;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.compatibility.common.util.ApiTest;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class PackagesTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final TestApp sTestApp = sDeviceState.testApps().any();
    private static TestAppInstance sTestAppInstance = sTestApp.install();

    @AfterClass
    public static void teardownClass() {
        sTestAppInstance.uninstall();
    }

    @Before
    public void setup() {
        // TODO(279404339): For some reason this is sometimes uninstalled
        sTestAppInstance =
                sTestApp.install(TestApis.users().instrumented());
    }

    @CanSetPolicyTest(policy = SuspendPackage.class)
    @Postsubmit(reason = "new test")
    public void isPackageSuspended_packageIsSuspended_returnsTrue() throws Exception {
        try {
            sDeviceState.dpc().devicePolicyManager().setPackagesSuspended(
                    sDeviceState.dpc().componentName(), new String[]{sTestApp.packageName()}, true);

            assertThat(sDeviceState.dpc().devicePolicyManager().isPackageSuspended(
                    sDeviceState.dpc().componentName(), sTestApp.packageName())).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPackagesSuspended(
                    sDeviceState.dpc().componentName(), new String[]{sTestApp.packageName()},
                    false);
        }
    }

    @CanSetPolicyTest(policy = SuspendPackage.class)
    @Postsubmit(reason = "new test")
    public void isPackageSuspended_packageIsNotSuspended_returnFalse() throws Exception {
        sDeviceState.dpc().devicePolicyManager().setPackagesSuspended(
                sDeviceState.dpc().componentName(), new String[]{sTestApp.packageName()}, false);

        assertThat(sDeviceState.dpc().devicePolicyManager().isPackageSuspended(
                sDeviceState.dpc().componentName(), sTestApp.packageName())).isFalse();
    }

    @CannotSetPolicyTest(policy = SuspendPackage.class)
    @Postsubmit(reason = "new test")
    public void isPackageSuspended_notAllowed_throwsException() {
        assertThrows(SecurityException.class, () ->
                sDeviceState.dpc().devicePolicyManager()
                        .isPackageSuspended(
                                sDeviceState.dpc().componentName(), sTestApp.packageName()));
    }

    @CannotSetPolicyTest(policy = SuspendPackage.class)
    @Postsubmit(reason = "new test")
    public void setPackageSuspended_notAllowed_throwsException() {
        try {
            assertThrows(SecurityException.class, () ->
                    sDeviceState.dpc().devicePolicyManager()
                            .setPackagesSuspended(
                                    sDeviceState.dpc().componentName(),
                                    new String[]{sTestApp.packageName()}, true));
        } finally {
            try {
                sDeviceState.dpc().devicePolicyManager()
                        .setPackagesSuspended(
                                sDeviceState.dpc().componentName(),
                                new String[]{sTestApp.packageName()}, false);
            } catch (SecurityException ex) {
                // Expected
            }
        }
    }

    @CannotSetPolicyTest(policy = EnsureVerifyApps.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#ENSURE_VERIFY_APPS")
    public void setUserRestriction_ensureVerifyApps_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), ENSURE_VERIFY_APPS));
    }

    @PolicyAppliesTest(policy = EnsureVerifyApps.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#ENSURE_VERIFY_APPS")
    public void setUserRestriction_ensureVerifyApps_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), ENSURE_VERIFY_APPS);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(ENSURE_VERIFY_APPS))
                    .isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), ENSURE_VERIFY_APPS);
        }
    }

    @PolicyDoesNotApplyTest(policy = EnsureVerifyApps.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#ENSURE_VERIFY_APPS")
    public void setUserRestriction_ensureVerifyApps_isNotSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), ENSURE_VERIFY_APPS);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(ENSURE_VERIFY_APPS))
                    .isFalse();
        } finally {

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), ENSURE_VERIFY_APPS);
        }
    }

    // TODO: Add (interactive?) test of ENSURE_VERIFY_APPS behaviour
}
