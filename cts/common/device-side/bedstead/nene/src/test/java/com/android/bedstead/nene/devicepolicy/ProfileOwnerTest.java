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

package com.android.bedstead.nene.devicepolicy;

import static android.os.Build.VERSION_CODES.TIRAMISU;

import static com.android.bedstead.harrier.UserType.SECONDARY_USER;

import static com.google.common.truth.Truth.assertThat;

import android.content.ComponentName;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.UserType;
import com.android.bedstead.harrier.annotations.EnsureHasSecondaryUser;
import com.android.bedstead.harrier.annotations.RequireRunNotOnSecondaryUser;
import com.android.bedstead.harrier.annotations.RequireRunOnInitialUser;
import com.android.bedstead.harrier.annotations.RequireRunOnWorkProfile;
import com.android.bedstead.harrier.annotations.RequireSdkVersion;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasNoDpc;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasProfileOwner;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.remotedpc.RemoteDpc;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public class ProfileOwnerTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final ComponentName DPC_COMPONENT_NAME = new ComponentName(
            RemoteDpc.REMOTE_DPC_APP_PACKAGE_NAME_OR_PREFIX,
            "com.android.bedstead.testapp.BaseTestAppDeviceAdminReceiver"
    );
    private static final TestApp sNonTestOnlyDpc = sDeviceState.testApps().query()
            .whereIsDeviceAdmin().isTrue()
            .whereTestOnly().isFalse()
            .get();
    private static final ComponentName NON_TEST_ONLY_DPC_COMPONENT_NAME = new ComponentName(
            sNonTestOnlyDpc.packageName(),
            "com.android.bedstead.testapp.DeviceAdminTestApp.DeviceAdminReceiver"
    );

    private static UserReference sProfile;

    @Before
    public void setUp() {
        sProfile = TestApis.users().instrumented();
    }

    @Test
    @EnsureHasProfileOwner
    public void user_returnsUser() {
        assertThat(sDeviceState.profileOwner().devicePolicyController().user()).isEqualTo(sProfile);
    }

    @Test
    @EnsureHasProfileOwner
    public void pkg_returnsPackage() {
        assertThat(sDeviceState.profileOwner().devicePolicyController().pkg()).isNotNull();
    }

    @Test
    @EnsureHasProfileOwner
    public void componentName_returnsComponentName() {
        assertThat(sDeviceState.profileOwner().devicePolicyController().componentName())
                .isEqualTo(DPC_COMPONENT_NAME);
    }

    @Test
    @EnsureHasProfileOwner
    public void remove_removesProfileOwner() {
        sDeviceState.profileOwner().devicePolicyController().remove();
        try {
            assertThat(TestApis.devicePolicy().getProfileOwner(sProfile)).isNull();
        } finally {
            TestApis.devicePolicy().setProfileOwner(sProfile, DPC_COMPONENT_NAME);
        }
    }

    @Test
    @EnsureHasNoDpc
    public void remove_nonTestOnlyDpc_removesProfileOwner() {
        try (TestAppInstance dpc = sNonTestOnlyDpc.install()) {
            ProfileOwner profileOwner = TestApis.devicePolicy().setProfileOwner(
                    TestApis.users().instrumented(), NON_TEST_ONLY_DPC_COMPONENT_NAME);

            profileOwner.remove();

            assertThat(TestApis.devicePolicy().getProfileOwner()).isNull();
        }
    }

    @Test
    @EnsureHasNoDpc
    @RequireRunOnInitialUser
    public void setAndRemoveProfileOwnerRepeatedly_doesNotThrowError() {
        try (UserReference profile = TestApis.users().createUser().createAndStart()) {
            try (TestAppInstance dpc = sNonTestOnlyDpc.install()) {
                for (int i = 0; i < 100; i++) {
                    ProfileOwner profileOwner = TestApis.devicePolicy().setProfileOwner(
                            TestApis.users().instrumented(), NON_TEST_ONLY_DPC_COMPONENT_NAME);
                    profileOwner.remove();
                }
            }
        }
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireRunNotOnSecondaryUser
    @EnsureHasProfileOwner(onUser = SECONDARY_USER)
    public void remove_onOtherUser_removesProfileOwner() {
            TestApis.devicePolicy().getProfileOwner(sDeviceState.secondaryUser()).remove();

            assertThat(TestApis.devicePolicy().getProfileOwner(sDeviceState.secondaryUser()))
                    .isNull();
    }

    @Test
    @RequireRunOnWorkProfile
    public void remove_onWorkProfile_testDpc_removesProfileOwner() {
        TestApis.devicePolicy().getProfileOwner().remove();

        assertThat(TestApis.devicePolicy().getProfileOwner()).isNull();
    }

    @Test
    @RequireSdkVersion(min = TIRAMISU)
    @RequireRunOnWorkProfile
    public void setIsOrganizationOwned_becomesOrganizationOwned() {
        ProfileOwner profileOwner = (ProfileOwner) sDeviceState.profileOwner(
                sDeviceState.workProfile()).devicePolicyController();

        profileOwner.setIsOrganizationOwned(true);

        assertThat(profileOwner.isOrganizationOwned()).isTrue();
    }

    @Test
    @RequireSdkVersion(min = TIRAMISU)
    @RequireRunOnWorkProfile
    public void unsetIsOrganizationOwned_becomesNotOrganizationOwned() {
        ProfileOwner profileOwner = (ProfileOwner) sDeviceState.profileOwner(
                sDeviceState.workProfile()).devicePolicyController();
        profileOwner.setIsOrganizationOwned(true);

        profileOwner.setIsOrganizationOwned(false);

        assertThat(profileOwner.isOrganizationOwned()).isFalse();
    }
}
