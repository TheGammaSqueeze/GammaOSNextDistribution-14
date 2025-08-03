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

import static android.content.pm.PackageManager.FEATURE_DEVICE_ADMIN;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.Manifest;
import android.app.admin.DevicePolicyManager;
import android.app.admin.PackagePolicy;
import android.app.admin.RemoteDevicePolicyManager;
import android.content.Context;
import android.util.ArraySet;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.IntTestParameter;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.policies.ManagedProfileContactAccess;
import com.android.bedstead.nene.TestApis;
import com.android.compatibility.common.util.ApiTest;
import com.android.internal.R;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.Set;

@RunWith(BedsteadJUnit4.class)
@RequireFeature(FEATURE_DEVICE_ADMIN)
public class ManagedProfileContactsAccessTest {
    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final String TEST_PACKAGE_ONE = "com.example.1";
    private static final String TEST_PACKAGE_TWO = "com.example.2";
    private static final String TEST_SYSTEM_PACKAGE =
            TestApis.context().androidContextAsUser(TestApis.users().current())
                    .getString(R.string.config_systemContacts);

    private static final Set<String> ONLY_PACKAGE_ONE =
            new ArraySet<>(Arrays.asList(TEST_PACKAGE_ONE));

    private RemoteDevicePolicyManager mRemoteDevicePolicyManager;

    @IntTestParameter({
            PackagePolicy.PACKAGE_POLICY_BLOCKLIST,
            PackagePolicy.PACKAGE_POLICY_ALLOWLIST_AND_SYSTEM,
            PackagePolicy.PACKAGE_POLICY_ALLOWLIST
    })
    @Retention(RetentionPolicy.RUNTIME)
    private @interface PackagePolicyParameters {
    }

    @Before
    public void setUp() {
        mRemoteDevicePolicyManager = sDeviceState.dpc().devicePolicyManager();
    }

    @After
    public void tearDown() {
        try {
            mRemoteDevicePolicyManager.setManagedProfileContactsAccessPolicy(null);
        } catch (SecurityException securityException) {
            // Ignore security exception on tearDown
        }
    }


    @CannotSetPolicyTest(policy = ManagedProfileContactAccess.class)
    @ApiTest(apis = "android.app.DevicePolicyManager#setManagedProfileContactsAccessPolicy")
    public void setManagedProfileContactsAccess_policyNotAllowedToBeSet_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> mRemoteDevicePolicyManager
                .setManagedProfileContactsAccessPolicy(null));
    }

    @CannotSetPolicyTest(policy = ManagedProfileContactAccess.class)
    @ApiTest(apis = "android.app.DevicePolicyManager#getManagedProfileContactsAccessPolicy")
    public void getManagedProfileContactsAccess_notAllowed_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> mRemoteDevicePolicyManager
                .getManagedProfileContactsAccessPolicy());
    }

    @CanSetPolicyTest(policy = ManagedProfileContactAccess.class)
    @ApiTest(apis = {
            "android.app.DevicePolicyManager#setManagedProfileContactsAccessPolicy",
            "android.app.DevicePolicyManager#getManagedProfileContactsAccessPolicy"
    })
    public void setManagedProfileContactsAccess_policySetToNull_works() {
        mRemoteDevicePolicyManager
                .setManagedProfileContactsAccessPolicy(null);

        assertThat(mRemoteDevicePolicyManager
                .getManagedProfileContactsAccessPolicy()).isNull();
    }

    @CanSetPolicyTest(policy = ManagedProfileContactAccess.class)
    @ApiTest(apis = {
            "android.app.DevicePolicyManager#setManagedProfileContactsAccessPolicy",
            "android.app.DevicePolicyManager#getManagedProfileContactsAccessPolicy",
    })
    public void setManagedProfileContactsAccess_validEmptyPolicy_works(
            @PackagePolicyParameters int testPolicy) {
        PackagePolicy policy = new PackagePolicy(testPolicy);

        mRemoteDevicePolicyManager
                .setManagedProfileContactsAccessPolicy(policy);

        assertThat(mRemoteDevicePolicyManager
                .getManagedProfileContactsAccessPolicy().getPolicyType()).isEqualTo(testPolicy);
        assertThat(mRemoteDevicePolicyManager
                .getManagedProfileContactsAccessPolicy().getPackageNames()).isEmpty();
        assertThat(mRemoteDevicePolicyManager
                .getManagedProfileContactsAccessPolicy()).isEqualTo(policy);
    }

    @CanSetPolicyTest(policy = ManagedProfileContactAccess.class)
    @ApiTest(apis = {
            "android.app.DevicePolicyManager#setManagedProfileContactsAccessPolicy",
            "android.app.DevicePolicyManager#getManagedProfileContactsAccessPolicy"
    })
    public void setManagedProfileContactsAccess_validPackageListPolicy_works(
            @PackagePolicyParameters int testPolicy
    ) {
        PackagePolicy policy = new PackagePolicy(testPolicy, ONLY_PACKAGE_ONE);

        mRemoteDevicePolicyManager
                .setManagedProfileContactsAccessPolicy(policy);

        assertThat(mRemoteDevicePolicyManager
                .getManagedProfileContactsAccessPolicy().getPolicyType()).isEqualTo(testPolicy);
        assertThat(mRemoteDevicePolicyManager
                .getManagedProfileContactsAccessPolicy().getPackageNames())
                .isEqualTo(ONLY_PACKAGE_ONE);
        assertThat(mRemoteDevicePolicyManager
                .getManagedProfileContactsAccessPolicy()).isEqualTo(policy);
    }

    @CanSetPolicyTest(policy = ManagedProfileContactAccess.class)
    @ApiTest(apis = {
            "android.app.DevicePolicyManager#setManagedProfileContactsSearchDisabled",
            "android.app.DevicePolicyManager#getManagedProfileContactsAccessPolicy",
    })
    public void setCrossProfileContactsDisabled_toFalse_setsAllAllowedPolicy() {
        mRemoteDevicePolicyManager.setCrossProfileContactsSearchDisabled(
                sDeviceState.dpc().componentName(),
                /*disabled = */ false);
        PackagePolicy expectedPolicy = new PackagePolicy(PackagePolicy.PACKAGE_POLICY_BLOCKLIST);

        assertThat(mRemoteDevicePolicyManager
                .getManagedProfileContactsAccessPolicy()).isEqualTo(expectedPolicy);
    }

    @CanSetPolicyTest(policy = ManagedProfileContactAccess.class)
    @ApiTest(apis = {
            "android.app.DevicePolicyManager#setManagedProfileContactsSearchDisabled",
            "android.app.DevicePolicyManager#getManagedProfileContactsAccessPolicy"
    })
    public void setCrossProfileContactsSearchDisabled_toTrue_setsAllBlockedPolicy() {
        mRemoteDevicePolicyManager.setCrossProfileContactsSearchDisabled(
                sDeviceState.dpc().componentName(),
                /*disabled = */ true);
        PackagePolicy expectedPolicy = new PackagePolicy(PackagePolicy.PACKAGE_POLICY_ALLOWLIST);

        assertThat(mRemoteDevicePolicyManager
                .getManagedProfileContactsAccessPolicy()).isEqualTo(expectedPolicy);
    }

    @PolicyAppliesTest(policy = ManagedProfileContactAccess.class)
    @EnsureHasPermission(value = Manifest.permission.INTERACT_ACROSS_USERS)
    @ApiTest(apis = {
            "android.app.DevicePolicyManager#setManagedProfileContactsAccessPolicy",
            "android.app.DevicePolicyManager#hasManagedProfileContactsAccess"
    })
    public void hasManagedProfileContactsAccess_blocklistPackages_hasProperAccess() {
        mRemoteDevicePolicyManager.setManagedProfileContactsAccessPolicy(
                new PackagePolicy(PackagePolicy.PACKAGE_POLICY_BLOCKLIST, ONLY_PACKAGE_ONE)
        );

        Context ctx = TestApis.context().instrumentedContext();
        DevicePolicyManager dpm = ctx.getSystemService(DevicePolicyManager.class);
        assertThat(dpm.hasManagedProfileContactsAccess(sDeviceState.workProfile().userHandle(),
                TEST_PACKAGE_ONE)).isFalse();
        assertThat(dpm.hasManagedProfileContactsAccess(sDeviceState.workProfile().userHandle(),
                TEST_PACKAGE_TWO)).isTrue();
        assertThat(dpm.hasManagedProfileContactsAccess(sDeviceState.workProfile().userHandle(),
                TEST_SYSTEM_PACKAGE)).isTrue();
    }

    @PolicyAppliesTest(policy = ManagedProfileContactAccess.class)
    @EnsureHasPermission(value = Manifest.permission.INTERACT_ACROSS_USERS)
    @ApiTest(apis = {
            "android.app.DevicePolicyManager#setManagedProfileContactsAccessPolicy",
            "android.app.DevicePolicyManager#hasManagedProfileContactsAccess"
    })
    public void hasManagedProfileContactsAccess_allowlistPackages_hasProperAccess() {
        mRemoteDevicePolicyManager.setManagedProfileContactsAccessPolicy(
                new PackagePolicy(PackagePolicy.PACKAGE_POLICY_ALLOWLIST, ONLY_PACKAGE_ONE)
        );

        Context ctx = TestApis.context().instrumentedContext();
        DevicePolicyManager dpm = ctx.getSystemService(DevicePolicyManager.class);
        assertThat(dpm.hasManagedProfileContactsAccess(sDeviceState.workProfile().userHandle(),
                TEST_PACKAGE_TWO)).isFalse();
        assertThat(dpm.hasManagedProfileContactsAccess(sDeviceState.workProfile().userHandle(),
                TEST_SYSTEM_PACKAGE)).isFalse();
        assertThat(dpm.hasManagedProfileContactsAccess(sDeviceState.workProfile().userHandle(),
                TEST_PACKAGE_ONE)).isTrue();
    }

    @PolicyAppliesTest(policy = ManagedProfileContactAccess.class)
    @EnsureHasPermission(value = Manifest.permission.INTERACT_ACROSS_USERS)
    @ApiTest(apis = {
            "android.app.DevicePolicyManager#setManagedProfileContactsAccessPolicy",
            "android.app.DevicePolicyManager#hasManagedProfileContactsAccess"
    })
    public void hasManagedProfileContactsAccess_allowlistAndSystemPackages_hasProperAccess() {
        mRemoteDevicePolicyManager.setManagedProfileContactsAccessPolicy(
                new PackagePolicy(
                        PackagePolicy.PACKAGE_POLICY_ALLOWLIST_AND_SYSTEM,
                        ONLY_PACKAGE_ONE)
        );

        Context ctx = TestApis.context().instrumentedContext();
        DevicePolicyManager dpm = ctx.getSystemService(DevicePolicyManager.class);
        assertThat(dpm.hasManagedProfileContactsAccess(sDeviceState.workProfile().userHandle(),
                TEST_PACKAGE_TWO)).isFalse();
        assertThat(dpm.hasManagedProfileContactsAccess(sDeviceState.workProfile().userHandle(),
                TEST_SYSTEM_PACKAGE)).isTrue();
        assertThat(dpm.hasManagedProfileContactsAccess(sDeviceState.workProfile().userHandle(),
                TEST_PACKAGE_ONE)).isTrue();
    }
}
