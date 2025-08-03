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

package com.android.bedstead.remotedpc;

import static android.os.Build.VERSION_CODES.Q;
import static android.os.UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.content.ComponentName;
import android.os.UserHandle;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.AfterClass;
import com.android.bedstead.harrier.annotations.BeforeClass;
import com.android.bedstead.harrier.annotations.EnsureHasNoSecondaryUser;
import com.android.bedstead.harrier.annotations.EnsureHasNoWorkProfile;
import com.android.bedstead.harrier.annotations.RequireRunOnInitialUser;
import com.android.bedstead.harrier.annotations.RequireRunOnSystemUser;
import com.android.bedstead.harrier.annotations.RequireSdkVersion;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDeviceOwner;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasNoDeviceOwner;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasNoProfileOwner;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasProfileOwner;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.devicepolicy.DeviceOwner;
import com.android.bedstead.nene.devicepolicy.ProfileOwner;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.users.UserType;
import com.android.bedstead.testapp.TestApp;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public class RemoteDpcTest {
    //  TODO(180478924): We shouldn't need to hardcode this
    private static final String DEVICE_ADMIN_TESTAPP_PACKAGE_NAME =
            "com.android.bedstead.testapp.DeviceAdminTestApp";
    private static final ComponentName NON_REMOTE_DPC_COMPONENT =
            new ComponentName(DEVICE_ADMIN_TESTAPP_PACKAGE_NAME,
                    "com.android.bedstead.testapp.DeviceAdminTestApp.DeviceAdminReceiver");

    @ClassRule @Rule
    public static DeviceState sDeviceState = new DeviceState();

    private static TestApp sNonRemoteDpcTestApp = sDeviceState.testApps().query()
    // TODO(180478924): Query by feature not package name
            .wherePackageName().isEqualTo(DEVICE_ADMIN_TESTAPP_PACKAGE_NAME)
            .get();
    private static final UserReference sUser = TestApis.users().instrumented();
    private static final UserReference NON_EXISTING_USER_REFERENCE =
            TestApis.users().find(99999);
    private static final UserHandle NON_EXISTING_USER_HANDLE =
            NON_EXISTING_USER_REFERENCE.userHandle();

    @BeforeClass
    public static void setupClass() {
        sNonRemoteDpcTestApp.install();
        sNonRemoteDpcTestApp.install(TestApis.users().system());
    }

    @AfterClass
    public static void teardownClass() {
        sNonRemoteDpcTestApp.uninstallFromAllUsers();
    }

    @Test
    @EnsureHasNoDeviceOwner
    public void deviceOwner_noDeviceOwner_returnsNull() {
        assertThat(RemoteDpc.deviceOwner()).isNull();
    }

    @Test
    @RequireRunOnSystemUser
    @EnsureHasNoDeviceOwner
    @EnsureHasNoProfileOwner
    @EnsureHasNoSecondaryUser // TODO(b/202832720): Replace with @EnsureHasOnlyInstrumentedUser
    @EnsureHasNoWorkProfile // TODO(b/202832720): Replace with @EnsureHasOnlyInstrumentedUser
    public void deviceOwner_nonRemoteDpcDeviceOwner_returnsNull() {
        DeviceOwner deviceOwner =
                TestApis.devicePolicy().setDeviceOwner(NON_REMOTE_DPC_COMPONENT);
        try {
            assertThat(RemoteDpc.deviceOwner()).isNull();
        } finally {
            deviceOwner.remove();
        }
    }

    @Test
    @EnsureHasDeviceOwner
    public void deviceOwner_remoteDpcDeviceOwner_returnsInstance() {
        assertThat(RemoteDpc.deviceOwner()).isNotNull();
    }

    @Test
    @EnsureHasNoProfileOwner
    public void profileOwner_noProfileOwner_returnsNull() {
        assertThat(RemoteDpc.profileOwner()).isNull();
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoProfileOwner
    public void profileOwner_nonRemoteDpcProfileOwner_returnsNull() {
        ProfileOwner profileOwner =
                TestApis.devicePolicy().setProfileOwner(sUser, NON_REMOTE_DPC_COMPONENT);
        try {
            assertThat(RemoteDpc.profileOwner()).isNull();
        } finally {
            profileOwner.remove();
        }
    }

    @Test
    @EnsureHasProfileOwner
    public void profileOwner_remoteDpcProfileOwner_returnsInstance() {
        assertThat(RemoteDpc.profileOwner()).isNotNull();
    }

    @Test
    public void profileOwner_userHandle_null_throwsException() {
        assertThrows(NullPointerException.class, () -> RemoteDpc.profileOwner((UserHandle) null));
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoWorkProfile
    @RequireRunOnInitialUser
    public void profileOwner_userHandle_noProfileOwner_returnsNull() {
        UserReference profile = TestApis.users().createUser()
                .parent(sUser)
                .type(TestApis.users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME))
                .createAndStart();
        try {
            assertThat(RemoteDpc.profileOwner(profile.userHandle())).isNull();
        } finally {
            profile.remove();
        }
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoWorkProfile
    @RequireRunOnInitialUser
    @RequireSdkVersion(min = Q, reason = "Cannot use RemoteDPC cross-user prior to Q")
    public void profileOwner_userHandle_nonRemoteDpcProfileOwner_returnsNull() {
        UserReference profile = TestApis.users().createUser()
                .parent(sUser)
                .type(TestApis.users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME))
                .createAndStart();
        sNonRemoteDpcTestApp.install(profile);
        try {
            TestApis.devicePolicy().setProfileOwner(profile, NON_REMOTE_DPC_COMPONENT);

            assertThat(RemoteDpc.profileOwner(profile.userHandle())).isNull();
        } finally {
            profile.remove();
        }
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoWorkProfile
    @RequireRunOnInitialUser
    @RequireSdkVersion(min = Q, reason = "Cannot use RemoteDPC cross-user prior to Q")
    public void profileOwner_userHandle_remoteDpcProfileOwner_returnsInstance() {
        UserReference profile = TestApis.users().createUser()
                .parent(sUser)
                .type(TestApis.users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME))
                .createAndStart();
        RemoteDpc.setAsProfileOwner(profile);
        try {
            assertThat(RemoteDpc.profileOwner(profile.userHandle())).isNotNull();
        } finally {
            profile.remove();
        }
    }

    @Test
    public void profileOwner_userReference_null_throwsException() {
        assertThrows(NullPointerException.class,
                () -> RemoteDpc.profileOwner((UserReference) null));
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoWorkProfile
    @RequireRunOnInitialUser
    public void profileOwner_userReference_noProfileOwner_returnsNull() {
        UserReference profile = TestApis.users().createUser()
                .parent(sUser)
                .type(TestApis.users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME))
                .createAndStart();
        try {
            assertThat(RemoteDpc.profileOwner(profile)).isNull();
        } finally {
            profile.remove();
        }
    }

    @Test
    @EnsureHasNoDeviceOwner
    @RequireRunOnInitialUser
    @EnsureHasNoWorkProfile
    public void profileOwner_userReference_nonRemoteDpcProfileOwner_returnsNull() {
        UserReference profile = TestApis.users().createUser()
                .parent(sUser)
                .type(TestApis.users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME))
                .createAndStart();
        sNonRemoteDpcTestApp.install(profile);
        try {
            TestApis.devicePolicy().setProfileOwner(profile, NON_REMOTE_DPC_COMPONENT);

            assertThat(RemoteDpc.profileOwner(profile)).isNull();
        } finally {
            profile.remove();
        }
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoWorkProfile
    @RequireRunOnInitialUser
    @RequireSdkVersion(min = Q, reason = "Cannot use RemoteDPC cross-user prior to Q")
    public void profileOwner_userReference_remoteDpcProfileOwner_returnsInstance() {
        UserReference profile = TestApis.users().createUser()
                .parent(sUser)
                .type(TestApis.users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME))
                .createAndStart();
        RemoteDpc.setAsProfileOwner(profile);
        try {
            assertThat(RemoteDpc.profileOwner(profile)).isNotNull();
        } finally {
            profile.remove();
        }
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoProfileOwner
    public void any_noDeviceOwner_noProfileOwner_returnsNull() {
        assertThat(RemoteDpc.any()).isNull();
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoProfileOwner
    public void any_noDeviceOwner_nonRemoteDpcProfileOwner_returnsNull() {
        ProfileOwner profileOwner = TestApis.devicePolicy().setProfileOwner(sUser,
                NON_REMOTE_DPC_COMPONENT);

        try {
            assertThat(RemoteDpc.any()).isNull();
        } finally {
            profileOwner.remove();
        }
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoProfileOwner
    @RequireRunOnSystemUser
    @EnsureHasNoSecondaryUser // TODO(b/202832720): Replace with @EnsureHasOnlyInstrumentedUser
    @EnsureHasNoWorkProfile // TODO(b/202832720): Replace with @EnsureHasOnlyInstrumentedUser
    public void any_nonRemoteDpcDeviceOwner_noProfileOwner_returnsNull() {
        DeviceOwner deviceOwner = TestApis.devicePolicy().setDeviceOwner(
                NON_REMOTE_DPC_COMPONENT);

        try {
            assertThat(RemoteDpc.any()).isNull();
        } finally {
            deviceOwner.remove();
        }
    }

    @Test
    @EnsureHasDeviceOwner
    public void any_remoteDpcDeviceOwner_returnsDeviceOwner() {
        assertThat(RemoteDpc.any()).isNotNull();
    }

    @Test
    @EnsureHasProfileOwner
    public void any_remoteDpcProfileOwner_returnsProfileOwner() {
        assertThat(RemoteDpc.any()).isNotNull();
    }

    @Test
    public void any_userHandle_null_throwsException() {
        assertThrows(NullPointerException.class, () -> RemoteDpc.any((UserHandle) null));
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoProfileOwner
    public void any_userHandle_noDeviceOwner_noProfileOwner_returnsNull() {
        assertThat(RemoteDpc.any(sUser.userHandle())).isNull();
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoWorkProfile
    @RequireRunOnInitialUser
    public void any_userHandle_noDeviceOwner_nonRemoteDpcProfileOwner_returnsNull() {
        UserReference profile = TestApis.users().createUser()
                .parent(sUser)
                .type(TestApis.users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME))
                .createAndStart();
        sNonRemoteDpcTestApp.install(profile);
        try {
            TestApis.devicePolicy().setProfileOwner(profile, NON_REMOTE_DPC_COMPONENT);

            assertThat(RemoteDpc.any(profile.userHandle())).isNull();
        } finally {
            profile.remove();
        }
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoProfileOwner
    @RequireRunOnSystemUser
    @EnsureHasNoSecondaryUser // TODO(b/202832720): Replace with @EnsureHasOnlyInstrumentedUser
    @EnsureHasNoWorkProfile // TODO(b/202832720): Replace with @EnsureHasOnlyInstrumentedUser
    public void any_userHandle_nonRemoteDpcDeviceOwner_noProfileOwner_returnsNull() {
        DeviceOwner deviceOwner = TestApis.devicePolicy().setDeviceOwner(
                NON_REMOTE_DPC_COMPONENT);

        try {
            assertThat(RemoteDpc.any(sUser.userHandle())).isNull();
        } finally {
            deviceOwner.remove();
        }
    }

    @Test
    @EnsureHasDeviceOwner
    @EnsureHasNoProfileOwner
    public void any_userHandle_remoteDpcDeviceOwner_returnsDeviceOwner() {
        RemoteDpc deviceOwner = RemoteDpc.deviceOwner();

        assertThat(RemoteDpc.any(sUser.userHandle())).isEqualTo(deviceOwner);
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoWorkProfile
    @RequireRunOnInitialUser
    @RequireSdkVersion(min = Q, reason = "Cannot use RemoteDPC cross-user prior to Q")
    public void any_userHandle_remoteDpcProfileOwner_returnsProfileOwner() {
        UserReference profile = TestApis.users().createUser()
                .parent(sUser)
                .type(TestApis.users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME))
                .createAndStart();
        try {
            RemoteDpc profileOwner = RemoteDpc.setAsProfileOwner(profile);

            assertThat(RemoteDpc.any(profile.userHandle())).isEqualTo(profileOwner);
        } finally {
            profile.remove();
        }
    }

    @Test
    public void any_userReference_null_throwsException() {
        assertThrows(NullPointerException.class, () -> RemoteDpc.any((UserReference) null));
    }

    @Test
    public void any_userReference_noDeviceOwner_noProfileOwner_returnsNull() {
        assertThat(RemoteDpc.any(sUser)).isNull();
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoWorkProfile
    @RequireRunOnInitialUser
    public void any_userReference_noDeviceOwner_nonRemoteDpcProfileOwner_returnsNull() {
        UserReference profile = TestApis.users().createUser()
                .parent(sUser)
                .type(TestApis.users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME))
                .createAndStart();
        sNonRemoteDpcTestApp.install(profile);
        try {
            TestApis.devicePolicy().setProfileOwner(profile, NON_REMOTE_DPC_COMPONENT);

            assertThat(RemoteDpc.any(profile)).isNull();
        } finally {
            profile.remove();
        }
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoProfileOwner
    @RequireRunOnSystemUser
    @EnsureHasNoSecondaryUser // TODO(b/202832720): Replace with @EnsureHasOnlyInstrumentedUser
    @EnsureHasNoWorkProfile // TODO(b/202832720): Replace with @EnsureHasOnlyInstrumentedUser
    public void any_userReference_nonRemoteDpcDeviceOwner_noProfileOwner_returnsNull() {
        DeviceOwner deviceOwner = TestApis.devicePolicy().setDeviceOwner(
                NON_REMOTE_DPC_COMPONENT);

        try {
            assertThat(RemoteDpc.any(sUser)).isNull();
        } finally {
            deviceOwner.remove();
        }
    }

    @Test
    @EnsureHasDeviceOwner
    @EnsureHasNoProfileOwner
    public void any_userReference_remoteDpcDeviceOwner_returnsDeviceOwner() {
        RemoteDpc deviceOwner = RemoteDpc.deviceOwner();

        assertThat(RemoteDpc.any(sUser)).isEqualTo(deviceOwner);
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoWorkProfile
    @RequireRunOnInitialUser
    @RequireSdkVersion(min = Q, reason = "Cannot use RemoteDPC cross-user prior to Q")
    public void any_userReference_remoteDpcProfileOwner_returnsProfileOwner() {
        UserReference profile = TestApis.users().createUser()
                .parent(sUser)
                .type(TestApis.users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME))
                .createAndStart();
        try {
            RemoteDpc profileOwner = RemoteDpc.setAsProfileOwner(profile);

            assertThat(RemoteDpc.any(profile)).isEqualTo(profileOwner);
        } finally {
            profile.remove();
        }
    }

    @Test
    public void setAsDeviceOwner_withoutTestAppQuery_setsDefault() {
        RemoteDpc.setAsDeviceOwner();

        DeviceOwner deviceOwner = TestApis.devicePolicy().getDeviceOwner();
        assertThat(deviceOwner.pkg().packageName()).isEqualTo("com.android.cts.RemoteDPC");
    }

    @Test
    @EnsureHasDeviceOwner
    public void setAsDeviceOwner_alreadySet_doesNothing() {
        RemoteDpc.setAsDeviceOwner();

        DeviceOwner deviceOwner = TestApis.devicePolicy().getDeviceOwner();
        assertThat(deviceOwner).isNotNull();
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoProfileOwner
    @RequireRunOnSystemUser
    @EnsureHasNoSecondaryUser // TODO(b/202832720): Replace with @EnsureHasOnlyInstrumentedUser
    @EnsureHasNoWorkProfile // TODO(b/202832720): Replace with @EnsureHasOnlyInstrumentedUser
    public void setAsDeviceOwner_alreadyHasDeviceOwner_replacesDeviceOwner() {
        TestApis.devicePolicy().setDeviceOwner(NON_REMOTE_DPC_COMPONENT);

        try {
            RemoteDpc remoteDPC = RemoteDpc.setAsDeviceOwner();

            DeviceOwner deviceOwner = TestApis.devicePolicy().getDeviceOwner();
            assertThat(deviceOwner).isEqualTo(remoteDPC.devicePolicyController());
        } finally {
            TestApis.devicePolicy().getDeviceOwner().remove();
        }
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoProfileOwner
    public void setAsDeviceOwner_doesNotHaveDeviceOwner_setsDeviceOwner() {
        RemoteDpc.setAsDeviceOwner();

        DeviceOwner deviceOwner = TestApis.devicePolicy().getDeviceOwner();
        try {
            assertThat(deviceOwner).isNotNull();
        } finally {
            if (deviceOwner != null) {
                deviceOwner.remove();
            }
        }
    }

    @Test
    public void setAsProfileOwner_userHandle_null_throwsException() {
        assertThrows(NullPointerException.class,
                () -> RemoteDpc.setAsProfileOwner((UserHandle) null));
    }

    @Test
    public void setAsProfileOwner_userHandle_nonExistingUser_throwsException() {
        assertThrows(NeneException.class,
                () -> RemoteDpc.setAsProfileOwner(NON_EXISTING_USER_HANDLE));
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoWorkProfile
    @RequireRunOnInitialUser
    @RequireSdkVersion(min = Q, reason = "Cannot use RemoteDPC cross-user prior to Q")
    public void setAsProfileOwner_userHandle_alreadySet_doesNothing() {
        UserReference profile = TestApis.users().createUser()
                .parent(sUser)
                .type(TestApis.users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME))
                .createAndStart();
        try {
            RemoteDpc.setAsProfileOwner(profile.userHandle());

            RemoteDpc.setAsProfileOwner(profile.userHandle());

            assertThat(TestApis.devicePolicy().getProfileOwner(profile)).isNotNull();
        } finally {
            profile.remove();
        }
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoWorkProfile
    @RequireRunOnInitialUser
    public void setAsProfileOwner_userHandle_alreadyHasProfileOwner_replacesProfileOwner() {
        sNonRemoteDpcTestApp.install();
        RemoteDpc remoteDPC = null;
        try {
            TestApis.devicePolicy()
                    .setProfileOwner(TestApis.users().instrumented(), NON_REMOTE_DPC_COMPONENT);

            remoteDPC = RemoteDpc.setAsProfileOwner(TestApis.users().instrumented().userHandle());

            assertThat(TestApis.devicePolicy().getProfileOwner(TestApis.users().instrumented()))
                    .isEqualTo(remoteDPC.devicePolicyController());
        } finally {
            if (remoteDPC != null) {
                remoteDPC.remove();
            }
        }
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoWorkProfile
    @RequireRunOnInitialUser
    @RequireSdkVersion(min = Q, reason = "Cannot use RemoteDPC cross-user prior to Q")
    public void setAsProfileOwner_userHandle_doesNotHaveProfileOwner_setsProfileOwner() {
        UserReference profile = TestApis.users().createUser()
                .parent(sUser)
                .type(TestApis.users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME))
                .createAndStart();
        try {
            RemoteDpc.setAsProfileOwner(profile.userHandle());

            assertThat(TestApis.devicePolicy().getProfileOwner(profile)).isNotNull();
        } finally {
            profile.remove();
        }
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoWorkProfile
    @RequireRunOnInitialUser
    @RequireSdkVersion(min = Q, reason = "Cannot use RemoteDPC cross-user prior to Q")
    public void setAsProfileOwner_disallowInstallUnknownSourcesIsDisabled() {
        // This is a temp fix for an issue where DISALLOW_INSTALL_UNKNOWN_SOURCES causes
        // verification failures
        UserReference profile = TestApis.users().createUser()
                .parent(sUser)
                .type(TestApis.users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME))
                .createAndStart();
        try {
            RemoteDpc profileOwner = RemoteDpc.setAsProfileOwner(profile.userHandle());

            assertThat(profileOwner.userManager()
                    .hasUserRestriction(DISALLOW_INSTALL_UNKNOWN_SOURCES))
                    .isFalse();
        } finally {
            profile.remove();
        }
    }

    @Test
    public void setAsProfileOwner_userReference_null_throwsException() {
        assertThrows(NullPointerException.class,
                () -> RemoteDpc.setAsProfileOwner((UserReference) null));
    }

    @Test
    public void setAsProfileOwner_userReference_nonExistingUser_throwsException() {
        assertThrows(NeneException.class,
                () -> RemoteDpc.setAsProfileOwner(NON_EXISTING_USER_REFERENCE));
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoWorkProfile
    @RequireRunOnInitialUser
    @RequireSdkVersion(min = Q, reason = "Cannot use RemoteDPC cross-user prior to Q")
    public void setAsProfileOwner_userReference_alreadySet_doesNothing() {
        UserReference profile = TestApis.users().createUser()
                .parent(sUser)
                .type(TestApis.users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME))
                .createAndStart();
        try {
            RemoteDpc.setAsProfileOwner(profile);

            RemoteDpc.setAsProfileOwner(profile);

            assertThat(TestApis.devicePolicy().getProfileOwner(profile)).isNotNull();
        } finally {
            profile.remove();
        }
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoWorkProfile
    @RequireRunOnInitialUser
    public void setAsProfileOwner_userReference_alreadyHasProfileOwner_replacesProfileOwner() {
        sNonRemoteDpcTestApp.install();
        RemoteDpc remoteDPC = null;
        try {
            TestApis.devicePolicy()
                    .setProfileOwner(TestApis.users().instrumented(), NON_REMOTE_DPC_COMPONENT);

            remoteDPC = RemoteDpc.setAsProfileOwner(TestApis.users().instrumented());

            assertThat(TestApis.devicePolicy().getProfileOwner(TestApis.users().instrumented()))
                    .isEqualTo(remoteDPC.devicePolicyController());
        } finally {
            if (remoteDPC != null) {
                remoteDPC.remove();
            }
        }
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoWorkProfile
    @RequireRunOnInitialUser
    @RequireSdkVersion(min = Q, reason = "Cannot use RemoteDPC cross-user prior to Q")
    public void setAsProfileOwner_userReference_doesNotHaveProfileOwner_setsProfileOwner() {
        UserReference profile = TestApis.users().createUser()
                .parent(sUser)
                .type(TestApis.users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME))
                .createAndStart();
        try {
            RemoteDpc.setAsProfileOwner(profile);

            assertThat(TestApis.devicePolicy().getProfileOwner(profile)).isNotNull();
        } finally {
            profile.remove();
        }
    }

    @Test
    @EnsureHasDeviceOwner
    public void devicePolicyController_returnsDevicePolicyController() {
        RemoteDpc remoteDPC = RemoteDpc.deviceOwner();

        try {
            assertThat(remoteDPC.devicePolicyController())
                    .isEqualTo(TestApis.devicePolicy().getDeviceOwner());
        } finally {
            remoteDPC.remove();
        }
    }

    @Test
    @EnsureHasDeviceOwner
    public void remove_deviceOwner_removes() {
        RemoteDpc remoteDPC = RemoteDpc.deviceOwner();

        remoteDPC.remove();

        assertThat(TestApis.devicePolicy().getDeviceOwner()).isNull();
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoWorkProfile
    @RequireRunOnInitialUser
    @RequireSdkVersion(min = Q, reason = "Cannot use RemoteDPC cross-user prior to Q")
    public void remove_profileOwner_removes() {
        try (UserReference profile = TestApis.users().createUser()
                .parent(sUser)
                .type(TestApis.users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME))
                .createAndStart()) {
            RemoteDpc remoteDPC = RemoteDpc.setAsProfileOwner(profile);

            remoteDPC.remove();

            assertThat(TestApis.devicePolicy().getProfileOwner(profile)).isNull();
        }
    }

    // TODO(scottjonathan): Do we need to support the case where there is both a DO and a PO on
    //  older versions of Android?

    @Test
    @EnsureHasDeviceOwner
    public void frameworkCall_makesCall() {
        sDeviceState.dpc().devicePolicyManager().getCurrentFailedPasswordAttempts();
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoWorkProfile
    @RequireRunOnInitialUser
    @RequireSdkVersion(min = Q, reason = "Cannot use RemoteDPC cross-user prior to Q")
    public void frameworkCall_onProfile_makesCall() {
        try (UserReference profile = TestApis.users().createUser()
                .parent(sUser)
                .type(TestApis.users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME))
                .createAndStart()) {
            RemoteDpc remoteDPC = RemoteDpc.setAsProfileOwner(profile);

            // Checking that the call succeeds
            remoteDPC.devicePolicyManager().isUsingUnifiedPassword(remoteDPC.componentName());
        }
    }

    @Test
    @EnsureHasDeviceOwner
    public void frameworkCallRequiresProfileOwner_notProfileOwner_throwsSecurityException() {
        RemoteDpc remoteDPC = RemoteDpc.deviceOwner();

        assertThrows(SecurityException.class, () ->
                remoteDPC.devicePolicyManager().isUsingUnifiedPassword(remoteDPC.componentName()));
    }

    @Test
    public void forDevicePolicyController_nullDevicePolicyController_throwsException() {
        assertThrows(NullPointerException.class, () -> RemoteDpc.forDevicePolicyController(null));
    }

    @Test
    @RequireRunOnSystemUser
    @EnsureHasNoDeviceOwner
    @EnsureHasNoProfileOwner
    @EnsureHasNoSecondaryUser // TODO(b/202832720): Replace with @EnsureHasOnlyInstrumentedUser
    @EnsureHasNoWorkProfile // TODO(b/202832720): Replace with @EnsureHasOnlyInstrumentedUser
    public void forDevicePolicyController_nonRemoteDpcDevicePolicyController_throwsException() {
        DeviceOwner deviceOwner = TestApis.devicePolicy().setDeviceOwner(NON_REMOTE_DPC_COMPONENT);

        try {
            assertThrows(IllegalStateException.class,
                    () -> RemoteDpc.forDevicePolicyController(deviceOwner));
        } finally {
            deviceOwner.remove();
        }
    }

    @Test
    @EnsureHasDeviceOwner
    public void forDevicePolicyController_remoteDpcDevicePolicyController_returnsRemoteDpc() {
        RemoteDpc remoteDPC = RemoteDpc.deviceOwner();

        assertThat(RemoteDpc.forDevicePolicyController(remoteDPC.devicePolicyController()))
                .isNotNull();
    }

    @Test
    @EnsureHasNoWorkProfile
    @EnsureHasNoDeviceOwner
    @RequireRunOnInitialUser
    @RequireSdkVersion(min = Q, reason = "Cannot use RemoteDPC cross-user prior to Q")
    public void getParentProfileInstance_returnsUsableInstance() {
        try (UserReference profile = TestApis.users().createUser()
                .parent(sUser)
                .type(TestApis.users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME))
                .createAndStart()) {
            RemoteDpc remoteDpc = RemoteDpc.setAsProfileOwner(profile);

            // Confirm that we can call methods on the RemoteDevicePolicyManager which comes from
            // getParentProfileInstance
            remoteDpc.devicePolicyManager()
                    .getParentProfileInstance(remoteDpc.componentName())
                    .getPasswordQuality(remoteDpc.componentName());

            // APIs which are not supported on parent instances should throw SecurityException
            assertThrows(SecurityException.class, () ->
                    remoteDpc.devicePolicyManager()
                            .getParentProfileInstance(remoteDpc.componentName())
                            .getParentProfileInstance(remoteDpc.componentName()));
        }
    }

    @Test
    public void setAsProfileOwner_alreadySetToDifferentRemoteDpc_replacesRemoteDpc() {
        try {
            RemoteDpc.setAsProfileOwner(sUser,
                    sDeviceState.testApps().query().whereTargetSdkVersion().isLessThan(28));
            RemoteDpc.setAsProfileOwner(sUser,
                    sDeviceState.testApps().query().whereTargetSdkVersion().isGreaterThan(28));

            assertThat(TestApis.devicePolicy().getProfileOwner()
                    .pkg().targetSdkVersion()).isGreaterThan(28);
        } finally {
            ProfileOwner profileOwner = TestApis.devicePolicy().getProfileOwner(sUser);
            if (profileOwner != null) {
                profileOwner.remove();
            }
        }
    }

    @Test
    public void setAsDeviceOwner_alreadySetToDifferentRemoteDpc_replacesRemoteDpc() {
        try {
            RemoteDpc.setAsDeviceOwner(
                    sDeviceState.testApps().query().whereTargetSdkVersion().isLessThan(28));
            RemoteDpc.setAsDeviceOwner(
                    sDeviceState.testApps().query().whereTargetSdkVersion().isGreaterThan(28));

            assertThat(TestApis.devicePolicy().getDeviceOwner()
                    .pkg().targetSdkVersion()).isGreaterThan(28);
        } finally {
            DeviceOwner deviceOwner = TestApis.devicePolicy().getDeviceOwner();
            if (deviceOwner != null) {
                deviceOwner.remove();
            }
        }
    }

    @Test
    public void setAsProfileOwner_matchesExistingRemoteDpc_doesNotReplace() {
        try {
            RemoteDpc.setAsProfileOwner(sUser,
                    sDeviceState.testApps().query().whereTargetSdkVersion().isEqualTo(28));
            RemoteDpc.setAsProfileOwner(sUser,
                    sDeviceState.testApps().query().whereTargetSdkVersion().isEqualTo(28));

            assertThat(TestApis.devicePolicy().getProfileOwner()
                    .pkg().targetSdkVersion()).isEqualTo(28);
        } finally {
            ProfileOwner profileOwner = TestApis.devicePolicy().getProfileOwner(sUser);
            if (profileOwner != null) {
                profileOwner.remove();
            }
        }
    }

    @Test
    public void setAsDeviceOwner_matchesExistingRemoteDpc_doesNotReplace() {
        try {
            RemoteDpc.setAsDeviceOwner(
                    sDeviceState.testApps().query().whereTargetSdkVersion().isEqualTo(28));
            RemoteDpc.setAsDeviceOwner(
                    sDeviceState.testApps().query().whereTargetSdkVersion().isEqualTo(28));

            assertThat(TestApis.devicePolicy().getDeviceOwner()
                    .pkg().targetSdkVersion()).isEqualTo(28);
        } finally {
            DeviceOwner deviceOwner = TestApis.devicePolicy().getDeviceOwner();
            if (deviceOwner != null) {
                deviceOwner.remove();
            }
        }
    }

}
