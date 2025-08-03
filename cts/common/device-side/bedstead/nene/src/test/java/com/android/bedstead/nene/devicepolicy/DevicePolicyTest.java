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

import static com.android.bedstead.harrier.UserType.ADDITIONAL_USER;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_REMOVE_USER;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.content.ComponentName;
import android.os.Build;
import android.os.UserManager;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.AfterClass;
import com.android.bedstead.harrier.annotations.BeforeClass;
import com.android.bedstead.harrier.annotations.EnsureHasAdditionalUser;
import com.android.bedstead.harrier.annotations.EnsureHasNoWorkProfile;
import com.android.bedstead.harrier.annotations.EnsureHasSecondaryUser;
import com.android.bedstead.harrier.annotations.RequireRunOnSystemUser;
import com.android.bedstead.harrier.annotations.RequireSdkVersion;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDeviceOwner;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasNoDeviceOwner;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasNoDpc;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasNoProfileOwner;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasProfileOwner;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.users.UserType;
import com.android.bedstead.remotedpc.RemoteDpc;
import com.android.bedstead.testapp.TestApp;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public class DevicePolicyTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    //  TODO(180478924): We shouldn't need to hardcode this
    private static final String DEVICE_ADMIN_TESTAPP_PACKAGE_NAME =
            "com.android.bedstead.testapp.DeviceAdminTestApp";
    private static final ComponentName NON_EXISTING_DPC_COMPONENT_NAME =
            new ComponentName("com.a.package", "com.a.package.Receiver");
    private static final ComponentName DPC_COMPONENT_NAME =
            new ComponentName(
                    DEVICE_ADMIN_TESTAPP_PACKAGE_NAME,
                    "com.android.bedstead.testapp.DeviceAdminTestApp.DeviceAdminReceiver");
    private static final ComponentName NOT_DPC_COMPONENT_NAME =
            new ComponentName(DEVICE_ADMIN_TESTAPP_PACKAGE_NAME,
                    "incorrect.class.name");

    private static final UserReference sUser = TestApis.users().instrumented();
    private static final UserReference NON_EXISTENT_USER = TestApis.users().find(99999);

    private static final String USER_RESTRICTION = UserManager.DISALLOW_AUTOFILL;

    private static TestApp sTestApp;

    @BeforeClass
    public static void setupClass() {
        sTestApp = sDeviceState.testApps().query()
                .wherePackageName().isEqualTo(DEVICE_ADMIN_TESTAPP_PACKAGE_NAME)
                .get();

        sTestApp.install();
        if (!sUser.equals(TestApis.users().system())) {
            // We're going to set the device owner on the system user
            sTestApp.install(TestApis.users().system());
        }
    }

    @AfterClass
    public static void teardownClass() {
        sTestApp.uninstallFromAllUsers();
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoWorkProfile
    public void setProfileOwner_profileOwnerIsSet() {
        UserReference profile = TestApis.users().createUser()
                .parent(sUser)
                .type(TestApis.users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME))
                .createAndStart();
        sTestApp.install(profile);

        ProfileOwner profileOwner =
                TestApis.devicePolicy().setProfileOwner(profile, DPC_COMPONENT_NAME);

        try {
            assertThat(TestApis.devicePolicy().getProfileOwner(profile)).isEqualTo(profileOwner);
        } finally {
            profile.remove();
        }
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoWorkProfile
    @Ignore // Now we retry on this error so this test takes too long
    public void setProfileOwner_profileOwnerIsAlreadySet_throwsException() {
        UserReference profile = TestApis.users().createUser()
                .parent(sUser)
                .type(TestApis.users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME))
                .createAndStart();
        try {
            sTestApp.install(profile);

            TestApis.devicePolicy().setProfileOwner(profile, DPC_COMPONENT_NAME);

            assertThrows(NeneException.class,
                    () -> TestApis.devicePolicy().setProfileOwner(profile, DPC_COMPONENT_NAME));
        } finally {
            profile.remove();
        }
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoWorkProfile
    public void setProfileOwner_componentNameNotInstalled_throwsException() {
        UserReference profile = TestApis.users().createUser()
                .parent(sUser)
                .type(TestApis.users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME))
                .createAndStart();
        try {
            assertThrows(NeneException.class,
                    () -> TestApis.devicePolicy().setProfileOwner(
                            profile, NON_EXISTING_DPC_COMPONENT_NAME));
        } finally {
            profile.remove();
        }
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoProfileOwner
    public void setProfileOwner_componentNameIsNotDPC_throwsException() {
        assertThrows(NeneException.class,
                () -> TestApis.devicePolicy().setProfileOwner(sUser, NOT_DPC_COMPONENT_NAME));
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoProfileOwner
    public void setProfileOwner_nullUser_throwsException() {
        assertThrows(NullPointerException.class,
                () -> TestApis.devicePolicy().setProfileOwner(
                        /* user= */ null, DPC_COMPONENT_NAME));
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoProfileOwner
    public void setProfileOwner_nullComponentName_throwsException() {
        assertThrows(NullPointerException.class,
                () -> TestApis.devicePolicy().setProfileOwner(
                        sUser, /* profileOwnerComponent= */ null));
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoProfileOwner
    public void setProfileOwner_userDoesNotExist_throwsException() {
        assertThrows(NeneException.class,
                () -> TestApis.devicePolicy().setProfileOwner(
                        NON_EXISTENT_USER, DPC_COMPONENT_NAME));
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoWorkProfile
    public void getProfileOwner_returnsProfileOwner() {
        UserReference profile = TestApis.users().createUser()
                .parent(sUser)
                .type(TestApis.users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME))
                .createAndStart();
        try {
            sTestApp.install(profile);

            ProfileOwner profileOwner =
                    TestApis.devicePolicy().setProfileOwner(profile, DPC_COMPONENT_NAME);

            assertThat(TestApis.devicePolicy().getProfileOwner(profile)).isEqualTo(profileOwner);
        } finally {
            profile.remove();
        }
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoWorkProfile
    public void getProfileOwner_noProfileOwner_returnsNull() {
        UserReference profile = TestApis.users().createUser()
                .parent(sUser)
                .type(TestApis.users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME))
                .createAndStart();

        try {
            assertThat(TestApis.devicePolicy().getProfileOwner(profile)).isNull();
        } finally {
            profile.remove();
        }

    }

    @Test
    public void getProfileOwner_nullUser_throwsException() {
        assertThrows(NullPointerException.class,
                () -> TestApis.devicePolicy().getProfileOwner((UserReference) null));
    }

    @Test
    @RequireRunOnSystemUser
    @EnsureHasNoDeviceOwner
    @EnsureHasNoProfileOwner
    public void setDeviceOwner_deviceOwnerIsSet() {
        DeviceOwner deviceOwner = TestApis.devicePolicy().setDeviceOwner(DPC_COMPONENT_NAME);

        try {
            assertThat(TestApis.devicePolicy().getDeviceOwner()).isEqualTo(deviceOwner);
        } finally {
            deviceOwner.remove();
        }
    }

    @Test
    @RequireRunOnSystemUser
    @EnsureHasDeviceOwner
    public void setDeviceOwner_deviceOwnerIsAlreadySet_throwsException() {
        assertThrows(NeneException.class,
                () -> TestApis.devicePolicy()
                        .setDeviceOwner(DPC_COMPONENT_NAME));
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoProfileOwner
    public void setDeviceOwner_componentNameNotInstalled_throwsException() {
        try {
            assertThrows(NeneException.class,
                    () -> TestApis.devicePolicy().setDeviceOwner(
                            NON_EXISTING_DPC_COMPONENT_NAME));
        } finally {
            sTestApp.install(sUser);
        }
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoProfileOwner
    public void setDeviceOwner_componentNameIsNotDPC_throwsException() {
        assertThrows(NeneException.class,
                () -> TestApis.devicePolicy()
                        .setDeviceOwner(NOT_DPC_COMPONENT_NAME));
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoProfileOwner
    @EnsureHasSecondaryUser
    @RequireSdkVersion(max = Build.VERSION_CODES.R)
    public void setDeviceOwner_preS_userAlreadyOnDevice_throwsException() {
        assertThrows(NeneException.class,
                () -> TestApis.devicePolicy()
                        .setDeviceOwner(DPC_COMPONENT_NAME));
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoProfileOwner
    @EnsureHasSecondaryUser
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    public void setDeviceOwner_sPlus_userAlreadyOnDevice_deviceOwnerIsSet() {
        DeviceOwner deviceOwner = TestApis.devicePolicy().setDeviceOwner(DPC_COMPONENT_NAME);

        try {
            assertThat(TestApis.devicePolicy().getDeviceOwner()).isNotNull();
        } finally {
            deviceOwner.remove();
        }
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoProfileOwner
    public void setDeviceOwner_nullComponentName_throwsException() {
        assertThrows(NullPointerException.class,
                () -> TestApis.devicePolicy().setDeviceOwner(/* deviceOwnerComponent= */ null));
    }


    @Test
    @EnsureHasDeviceOwner
    public void getDeviceOwner_returnsDeviceOwner() {
        assertThat(TestApis.devicePolicy().getDeviceOwner()).isNotNull();
    }

    @Test
    @EnsureHasNoDeviceOwner
    public void getDeviceOwner_noDeviceOwner_returnsNull() {
        assertThat(TestApis.devicePolicy().getDeviceOwner()).isNull();
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoProfileOwner
    public void profileOwner_autoclose_removesProfileOwner() {
        try (ProfileOwner profileOwner =
                     TestApis.devicePolicy().setProfileOwner(sUser, DPC_COMPONENT_NAME)) {
            // We intentionally don't do anything here, just rely on the auto-close behaviour
        }

        assertThat(TestApis.devicePolicy().getProfileOwner(sUser)).isNull();
    }

    @Test
    @RequireRunOnSystemUser
    @EnsureHasNoDeviceOwner
    @EnsureHasNoProfileOwner
    public void deviceOwner_autoclose_removesDeviceOwner() {
        assertThat(
                        TestApis.packages()
                                .find("com.android.bedstead.testapp.DeviceAdminTestApp")
                                .isInstalled())
                .isTrue();
        try (DeviceOwner deviceOwner = TestApis.devicePolicy().setDeviceOwner(DPC_COMPONENT_NAME)) {
            // We intentionally don't do anything here, just rely on the auto-close behaviour
        }

        assertThat(TestApis.devicePolicy().getDeviceOwner()).isNull();
    }

    @Test
    @RequireRunOnSystemUser
    @EnsureHasNoDeviceOwner
    @EnsureHasNoProfileOwner
    public void setDeviceOwner_recentlyUnsetProfileOwner_sets() {
        TestApis.devicePolicy().setProfileOwner(sUser, DPC_COMPONENT_NAME).remove();

        TestApis.devicePolicy().setDeviceOwner(DPC_COMPONENT_NAME);

        assertThat(TestApis.devicePolicy().getDeviceOwner()).isNotNull();
    }

    @Test
    @RequireRunOnSystemUser
    @EnsureHasNoDeviceOwner
    @EnsureHasNoProfileOwner
    public void setDeviceOwner_recentlyUnsetDeviceOwner_sets() {
        TestApis.devicePolicy()
                .setDeviceOwner(DPC_COMPONENT_NAME)
                .remove();

        TestApis.devicePolicy().setDeviceOwner(DPC_COMPONENT_NAME);

        assertThat(TestApis.devicePolicy().getDeviceOwner()).isNotNull();
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoProfileOwner
    public void setProfileOwner_recentlyUnsetProfileOwner_sets() {
        TestApis.devicePolicy().setProfileOwner(sUser, DPC_COMPONENT_NAME).remove();

        TestApis.devicePolicy().setProfileOwner(sUser, DPC_COMPONENT_NAME);

        assertThat(TestApis.devicePolicy().getProfileOwner(sUser)).isNotNull();
    }

    @Test
    @RequireRunOnSystemUser
    @EnsureHasNoDeviceOwner
    @EnsureHasNoProfileOwner
    public void setProfileOwner_recentlyUnsetDeviceOwner_sets() {
        TestApis.devicePolicy().setDeviceOwner(DPC_COMPONENT_NAME)
                .remove();

        TestApis.devicePolicy().setProfileOwner(sUser, DPC_COMPONENT_NAME);

        assertThat(TestApis.devicePolicy().getProfileOwner(sUser)).isNotNull();
    }

    @Test
    public void userRestrictions_withUserHandle_returnsObject() {
        assertThat(TestApis.devicePolicy().userRestrictions(
                TestApis.users().instrumented().userHandle())).isNotNull();
    }

    @Test
    public void userRestrictions_withUserReference_returnsObject() {
        assertThat(TestApis.devicePolicy().userRestrictions(
                TestApis.users().instrumented())).isNotNull();
    }

//    @Test
//    public void setUserRestriction_userRestrictionIsSet() {
//        try (UserRestrictionsContext r =
//                     TestApis.devicePolicy().userRestrictions().set(USER_RESTRICTION)){
//            assertThat(
//            TestApis.devicePolicy().userRestrictions().isSet(USER_RESTRICTION)).isTrue();
//        }
//    }
//
//    @Test
//    public void unsetUserRestriction_userRestrictionIsNotSet() {
//        try (UserRestrictionsContext r =
//                     TestApis.devicePolicy().userRestrictions().set(USER_RESTRICTION)){
//            try (UserRestrictionsContext r2 =
//                         TestApis.devicePolicy().userRestrictions().unset(USER_RESTRICTION)){
//                assertThat(TestApis.devicePolicy().userRestrictions().isSet(USER_RESTRICTION))
//                        .isFalse();
//            }
//        }
//    }


    @Test
    @EnsureHasProfileOwner(isPrimary = true)
    public void isSet_userRestrictionIsSet_returnsTrue() {
        boolean originalIsSet = sDeviceState.dpc().devicePolicyManager().getUserRestrictions(
                sDeviceState.dpc().componentName())
                .getBoolean(USER_RESTRICTION, /*default= */ false);
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), USER_RESTRICTION);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(USER_RESTRICTION)).isTrue();
        } finally {
            if (!originalIsSet) {
                sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                        sDeviceState.dpc().componentName(), USER_RESTRICTION);
            }
        }
    }

    @Test
    @EnsureHasProfileOwner(isPrimary = true)
    public void isSet_userRestrictionIsNotSet_returnsFalse() {
        boolean originalIsSet = sDeviceState.dpc().devicePolicyManager().getUserRestrictions(
                sDeviceState.dpc().componentName())
                .getBoolean(USER_RESTRICTION, /*default= */ false);
        try {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), USER_RESTRICTION);

            assertThat(
                    TestApis.devicePolicy().userRestrictions().isSet(USER_RESTRICTION)).isFalse();
        } finally {
            if (!originalIsSet) {
                sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), USER_RESTRICTION);
            }
        }
    }

    @Test
    @EnsureHasAdditionalUser
    @EnsureHasProfileOwner(isPrimary = true, onUser = ADDITIONAL_USER)
    public void isSet_userRestrictionIsSet_differentUser_returnsTrue() {
        boolean originalIsSet = sDeviceState.dpc().devicePolicyManager().getUserRestrictions(
                sDeviceState.dpc().componentName()).getBoolean(USER_RESTRICTION,
                /*default= */ false);
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), USER_RESTRICTION);

            assertThat(TestApis.devicePolicy().userRestrictions(sDeviceState.additionalUser())
                    .isSet(USER_RESTRICTION)).isTrue();
        } finally {
            if (!originalIsSet) {
                sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                        sDeviceState.dpc().componentName(), USER_RESTRICTION);
            }
        }
    }

    @Test
    @EnsureHasAdditionalUser
    @EnsureHasProfileOwner(isPrimary = true, onUser = ADDITIONAL_USER)
    public void isSet_userRestrictionIsNotSet_differentUser_returnsFalse() {
        boolean originalIsSet = sDeviceState.dpc().devicePolicyManager().getUserRestrictions(
                sDeviceState.dpc().componentName()).getBoolean(USER_RESTRICTION,
                /*default= */ false);
        try {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), USER_RESTRICTION);

            assertThat(TestApis.devicePolicy().userRestrictions(sDeviceState.additionalUser())
                    .isSet(USER_RESTRICTION)).isFalse();
        } finally {
            if (!originalIsSet) {
                sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), USER_RESTRICTION);
            }
        }
    }
}
