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
package android.devicepolicy.cts

import android.Manifest
import android.accounts.Account
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.app.admin.FullyManagedDeviceProvisioningParams
import android.app.admin.ManagedProfileProvisioningParams
import android.app.admin.ProvisioningException
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.os.BaseBundle
import android.os.Bundle
import android.os.Parcelable
import android.os.PersistableBundle
import android.os.UserHandle
import android.provider.Settings
import com.android.bedstead.deviceadminapp.DeviceAdminApp
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.annotations.EnsureDoesNotHavePermission
import com.android.bedstead.harrier.annotations.EnsureHasAccount
import com.android.bedstead.harrier.annotations.EnsureHasAdditionalUser
import com.android.bedstead.harrier.annotations.EnsureHasNoWorkProfile
import com.android.bedstead.harrier.annotations.EnsureHasPermission
import com.android.bedstead.harrier.annotations.EnsureHasSecondaryUser
import com.android.bedstead.harrier.annotations.EnsureHasUserRestriction
import com.android.bedstead.harrier.annotations.EnsureHasWorkProfile
import com.android.bedstead.harrier.annotations.EnsureIsNotDemoDevice
import com.android.bedstead.harrier.annotations.EnsureTestAppInstalled
import com.android.bedstead.harrier.annotations.NotTestable
import com.android.bedstead.harrier.annotations.PermissionTest
import com.android.bedstead.harrier.annotations.Postsubmit
import com.android.bedstead.harrier.annotations.RequireDoesNotHaveFeature
import com.android.bedstead.harrier.annotations.RequireFeature
import com.android.bedstead.harrier.annotations.RequireHeadlessSystemUserMode
import com.android.bedstead.harrier.annotations.RequireNotHeadlessSystemUserMode
import com.android.bedstead.harrier.annotations.RequireNotWatch
import com.android.bedstead.harrier.annotations.RequireRunOnAdditionalUser
import com.android.bedstead.harrier.annotations.RequireRunOnInitialUser
import com.android.bedstead.harrier.annotations.RequireRunOnSecondaryUser
import com.android.bedstead.harrier.annotations.RequireRunOnWorkProfile
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDeviceOwner
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDevicePolicyManagerRoleHolder
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasNoDeviceOwner
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasNoDpc
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasNoProfileOwner
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasProfileOwner
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.appops.AppOpsMode
import com.android.bedstead.nene.packages.CommonPackages
import com.android.bedstead.nene.permissions.CommonPermissions
import com.android.bedstead.nene.types.OptionalBoolean.TRUE
import com.android.bedstead.nene.userrestrictions.CommonUserRestrictions
import com.android.bedstead.nene.users.UserReference
import com.android.bedstead.nene.users.UserType
import com.android.bedstead.remotedpc.RemoteDpc
import com.android.bedstead.testapp.BaseTestAppDeviceAdminReceiver
import com.android.compatibility.common.util.ApiTest
import com.android.eventlib.premade.EventLibDeviceAdminReceiver
import com.android.eventlib.truth.EventLogsSubject.assertThat
import com.android.queryable.annotations.BooleanQuery
import com.android.queryable.annotations.Query
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import org.junit.Assert
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Properties
import java.util.stream.Collectors

@RunWith(BedsteadJUnit4::class)
class ProvisioningTest {
    @Test
    fun provisioningException_constructor_works() {
        val exception = ProvisioningException(CAUSE, PROVISIONING_ERROR, MESSAGE)

        assertThat(exception.cause).isEqualTo(CAUSE)
        assertThat(exception.provisioningError).isEqualTo(PROVISIONING_ERROR)
        assertThat(exception.message).isEqualTo(MESSAGE)
    }

    @Test
    fun provisioningException_constructor_noErrorMessage_nullByDefault() {
        val exception = ProvisioningException(CAUSE, PROVISIONING_ERROR)

        assertThat(exception.message).isNull()
    }

    // TODO: Get rid of the setup, and replace with a @EnsureDpcDownloaded annotation on the
    //  appropriate methods
    @Before
    fun setUp() {
        TestApis.permissions()
            .withPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS).use {
                localDevicePolicyManager.isDpcDownloaded = false
            }
    }

    @EnsureHasNoDpc
    @RequireFeature(CommonPackages.FEATURE_DEVICE_ADMIN)
    @RequireFeature(CommonPackages.FEATURE_MANAGED_USERS)
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @Test
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#createAndProvisionManagedProfile"])
    fun createAndProvisionManagedProfile_createsManagedProfile() {
        UserReference.of(
            localDevicePolicyManager.createAndProvisionManagedProfile(MANAGED_PROFILE_PARAMS)
        ).use { profile ->
            assertThat(profile.type()).isEqualTo(
                TestApis.users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME)
            )
        }
    }

    @Test
    @EnsureHasProfileOwner
    @EnsureHasDevicePolicyManagerRoleHolder
    @ApiTest(apis = ["android.app.admin.DeviceAdminReceiver#onProfileProvisioningComplete"])
    fun dpmRoleHolderSendsActionProfileProvisioningComplete_provisioningCompleteCallbackCalled() {
        // Ideally we would send this broadcast directly - but we use the role holder as we need
        // BIND_DEVICE_ADMIN
        deviceState.dpmRoleHolder().context().sendBroadcast(
            Intent(DeviceAdminReceiver.ACTION_PROFILE_PROVISIONING_COMPLETE).apply {
                setComponent(deviceState.dpc().componentName())
        })

        assertThat(deviceState.dpc().events().profileProvisioningComplete()).eventOccurred()
    }

    @RequireRunOnInitialUser
    @EnsureHasNoDpc
    @RequireFeature(CommonPackages.FEATURE_DEVICE_ADMIN)
    @RequireFeature(CommonPackages.FEATURE_MANAGED_USERS)
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @Test
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#createAndProvisionManagedProfile"])
    fun createAndProvisionManagedProfile_onInitialUser_createsManagedProfile() {
        UserReference.of(
            localDevicePolicyManager.createAndProvisionManagedProfile(MANAGED_PROFILE_PARAMS)
        ).use { profile -> assertThat(profile.exists()).isTrue() }
    }

    @EnsureHasNoWorkProfile
    @EnsureHasAdditionalUser
    @RequireRunOnAdditionalUser
    @RequireFeature(CommonPackages.FEATURE_DEVICE_ADMIN)
    @RequireFeature(CommonPackages.FEATURE_MANAGED_USERS)
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @Test
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#createAndProvisionManagedProfile"])
    fun createAndProvisionManagedProfile_notOnInitialUser_preconditionFails() {
        val exception = assertThrows(ProvisioningException::class.java) {
            localDevicePolicyManager.createAndProvisionManagedProfile(
                MANAGED_PROFILE_PARAMS
            )
        }
        assertThat(exception.provisioningError)
            .isEqualTo(ProvisioningException.ERROR_PRE_CONDITION_FAILED)
    }

    @EnsureHasNoDpc
    @RequireFeature(CommonPackages.FEATURE_DEVICE_ADMIN)
    @RequireFeature(CommonPackages.FEATURE_MANAGED_USERS)
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @Test
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#createAndProvisionManagedProfile"])
    fun createAndProvisionManagedProfile_setsActiveAdmin() {
        UserReference.of(
            localDevicePolicyManager.createAndProvisionManagedProfile(MANAGED_PROFILE_PARAMS)
        ).use { profile ->
            assertThat(TestApis.devicePolicy().getActiveAdmins(profile)).hasSize(1)
            assertThat(TestApis.devicePolicy().getActiveAdmins(profile).iterator().next())
                .isEqualTo(DEVICE_ADMIN_COMPONENT)
        }
    }

    @EnsureHasNoDpc
    @RequireFeature(CommonPackages.FEATURE_DEVICE_ADMIN)
    @RequireFeature(CommonPackages.FEATURE_MANAGED_USERS)
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @Test
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#createAndProvisionManagedProfile"])
    fun createAndProvisionManagedProfile_setsProfileOwner() {
        UserReference.of(
            localDevicePolicyManager.createAndProvisionManagedProfile(MANAGED_PROFILE_PARAMS)
        ).use { profile ->
            assertThat(
                TestApis.devicePolicy().getProfileOwner(profile)!!.pkg().packageName()
            )
                .isEqualTo(context.packageName)
        }
    }

    @EnsureHasNoDpc
    @RequireFeature(CommonPackages.FEATURE_DEVICE_ADMIN)
    @RequireFeature(CommonPackages.FEATURE_MANAGED_USERS)
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @EnsureHasAccount
    @Test
    @Ignore // TODO(265135960): I think this isn't copying because the authenticator isn't in
    // the work profile
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#createAndProvisionManagedProfile"])
    fun createAndProvisionManagedProfile_copiesAccountToProfile() {
        val params = createManagedProfileProvisioningParamsBuilder()
            .setAccountToMigrate(deviceState.account().account())
            .build()
        UserReference.of(
            localDevicePolicyManager.createAndProvisionManagedProfile(params)
        ).use { profile ->
            assertThat(TestApis.accounts().all(profile)).contains(
                deviceState.account()
            )
        }
    }

    @EnsureHasNoDpc
    @RequireFeature(CommonPackages.FEATURE_DEVICE_ADMIN)
    @RequireFeature(CommonPackages.FEATURE_MANAGED_USERS)
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @EnsureHasAccount
    @Test
    @Ignore // TODO(265135960): I think this isn't copying because the authenticator isn't in
    // the work profile
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#createAndProvisionManagedProfile"])
    fun createAndProvisionManagedProfile_removesAccountFromParentByDefault() {
        val params = createManagedProfileProvisioningParamsBuilder()
            .setAccountToMigrate(deviceState.account().account())
            .build()
        UserReference.of(
            localDevicePolicyManager.createAndProvisionManagedProfile(params)
        ).use {
            assertThat(
                deviceState.accounts().allAccounts()
            )
                .doesNotContain(deviceState.account())
        }
    }

    @EnsureHasNoDpc
    @RequireFeature(CommonPackages.FEATURE_DEVICE_ADMIN)
    @RequireFeature(CommonPackages.FEATURE_MANAGED_USERS)
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @EnsureHasAccount
    @Test
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#createAndProvisionManagedProfile"])
    fun createAndProvisionManagedProfile_keepsAccountInParentIfRequested() {
        val params = createManagedProfileProvisioningParamsBuilder()
            .setAccountToMigrate(deviceState.account().account())
            .setKeepingAccountOnMigration(true)
            .build()
        UserReference.of(
            localDevicePolicyManager.createAndProvisionManagedProfile(params)
        ).use {
            assertThat(
                deviceState.accounts().allAccounts()
            )
                .contains(deviceState.account())
        }
    }

    @EnsureHasNoDpc
    @RequireFeature(CommonPackages.FEATURE_DEVICE_ADMIN)
    @RequireFeature(CommonPackages.FEATURE_MANAGED_USERS)
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @Test
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#createAndProvisionManagedProfile"])
    fun createAndProvisionManagedProfile_removesNonRequiredAppsFromProfile() {
        TestApis.permissions().withPermission(Manifest.permission.INTERACT_ACROSS_USERS_FULL)
            .use {
                UserReference.of(
                    localDevicePolicyManager.createAndProvisionManagedProfile(
                        MANAGED_PROFILE_PARAMS
                    )
                ).use { profile ->
                    val nonRequiredApps = localDevicePolicyManager.getDisallowedSystemApps(
                        DEVICE_ADMIN_COMPONENT_NAME,
                        context.userId,
                        DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE
                    )
                    val nonRequiredAppsInProfile = TestApis.packages().installedForUser(profile)
                    nonRequiredAppsInProfile.retainAll(
                        nonRequiredApps.stream().map { TestApis.packages().find(it) }
                            .collect(Collectors.toSet()))
                    assertThat(nonRequiredAppsInProfile).isEmpty()
                }
            }
    }

    @EnsureHasNoDpc
    @RequireFeature(CommonPackages.FEATURE_DEVICE_ADMIN)
    @RequireFeature(CommonPackages.FEATURE_MANAGED_USERS)
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @Test
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#createAndProvisionManagedProfile"])
    fun createAndProvisionManagedProfile_setsCrossProfilePackages() {
        UserReference.of(
            localDevicePolicyManager.createAndProvisionManagedProfile(MANAGED_PROFILE_PARAMS)
        ).use { profile ->
            val defaultPackages = TestApis.devicePolicy().defaultCrossProfilePackages().stream()
                .filter { it.canConfigureInteractAcrossProfiles() }
                .filter { it.isInstalled }
                .collect(Collectors.toSet())
            for (crossProfilePackage in defaultPackages) {
                Truth.assertWithMessage(
                    "Checking crossprofilepackage : $crossProfilePackage on parent"
                ).that(
                    crossProfilePackage.appOps()[CommonPermissions.INTERACT_ACROSS_PROFILES]
                ).isEqualTo(AppOpsMode.ALLOWED)
                Truth.assertWithMessage(
                    "Checking crossprofilepackage : $crossProfilePackage on profile"
                ).that(
                    crossProfilePackage.appOps(profile)[CommonPermissions.INTERACT_ACROSS_PROFILES]
                ).isEqualTo(AppOpsMode.ALLOWED)
            }
        }
    }

    @RequireFeature(CommonPackages.FEATURE_DEVICE_ADMIN)
    @RequireFeature(CommonPackages.FEATURE_MANAGED_USERS)
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @Postsubmit(reason = "new test")
    @Test
    @EnsureHasWorkProfile
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#createAndProvisionManagedProfile"])
    fun createAndProvisionManagedProfile_withExistingProfile_preconditionFails() {
        val exception = assertThrows(ProvisioningException::class.java
        ) {
            localDevicePolicyManager.createAndProvisionManagedProfile(
                MANAGED_PROFILE_PARAMS
            )
        }
        assertThat(exception.provisioningError)
            .isEqualTo(ProvisioningException.ERROR_PRE_CONDITION_FAILED)
    }

    @EnsureHasNoDpc
    @RequireFeature(CommonPackages.FEATURE_DEVICE_ADMIN)
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @Test
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#provisionFullyManagedDevice"])
    fun provisionFullyManagedDevice_setsDeviceOwner() {
        val setupComplete = TestApis.users().system().setupComplete
        TestApis.users().system().setupComplete = false
        try {
            val params = createDefaultManagedDeviceProvisioningParamsBuilder().build()
            localDevicePolicyManager.provisionFullyManagedDevice(params)
            assertThat(TestApis.devicePolicy().getDeviceOwner()!!.pkg().packageName())
                .isEqualTo(context.packageName)
        } finally {
            val deviceOwner = TestApis.devicePolicy().getDeviceOwner()
            deviceOwner?.remove()
            TestApis.users().system().setupComplete = setupComplete
        }
    }

    @EnsureHasNoDpc
    @RequireFeature(CommonPackages.FEATURE_DEVICE_ADMIN)
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @Test
    @RequireHeadlessSystemUserMode(reason = "Testing headless-specific functionality")
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#provisionFullyManagedDevice"])
    fun provisionFullyManagedDevice_headless_setsProfileOwnerOnInitialUser() {
        val systemSetupComplete = TestApis.users().system().setupComplete
        TestApis.users().system().setupComplete = false
        try {
            val params = createDefaultManagedDeviceProvisioningParamsBuilder().build()
            localDevicePolicyManager.provisionFullyManagedDevice(params)
            assertThat(TestApis.devicePolicy().getProfileOwner()).isNotNull()
        } finally {
            val deviceOwner = TestApis.devicePolicy().getDeviceOwner()
            deviceOwner?.remove()
            val profileOwner = TestApis.devicePolicy().getProfileOwner()
            profileOwner?.remove()
            TestApis.users().system().setupComplete = systemSetupComplete
        }
    }

    @Postsubmit(reason = "New test")
    @EnsureHasNoDpc
    @RequireFeature(CommonPackages.FEATURE_DEVICE_ADMIN)
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @Test
    @RequireHeadlessSystemUserMode(reason = "Testing headless-specific functionality")
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#provisionFullyManagedDevice"])
    fun provisionFullyManagedDevice_headless_dpcDoesNotDeclareHeadlessCompatibility_throwsException() {
        sNoHeadlessSupportTestApp.install().use { testApp ->
            val params = FullyManagedDeviceProvisioningParams.Builder(
                ComponentName(
                    testApp.packageName(),
                    testApp.packageName() + ".DeviceAdminReceiver"
                ),
                DEVICE_OWNER_NAME
            ).build()
            val exception = assertThrows(
                ProvisioningException::class.java
            ) { localDevicePolicyManager.provisionFullyManagedDevice(params) }
            assertThat(exception.provisioningError).isEqualTo(
                ProvisioningException.ERROR_PRE_CONDITION_FAILED
            )
        }
    }

    @EnsureHasNoDpc
    @RequireFeature(CommonPackages.FEATURE_DEVICE_ADMIN)
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @Test
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#provisionFullyManagedDevice"])
    fun provisionFullyManagedDevice_disallowAddUserIsSet() {
        val systemSetupComplete = TestApis.users().system().setupComplete
        TestApis.users().system().setupComplete = false
        try {
            val params = createDefaultManagedDeviceProvisioningParamsBuilder().build()
            localDevicePolicyManager.provisionFullyManagedDevice(params)
            assertThat(
                TestApis.devicePolicy().userRestrictions()
                    .isSet(CommonUserRestrictions.DISALLOW_ADD_USER)
            )
                .isTrue()
        } finally {
            val deviceOwner = TestApis.devicePolicy().getDeviceOwner()
            deviceOwner?.remove()
            val profileOwner = TestApis.devicePolicy().getProfileOwner()
            profileOwner?.remove()
            TestApis.users().system().setupComplete = systemSetupComplete
        }
    }

    @EnsureHasNoDpc
    @RequireFeature(CommonPackages.FEATURE_DEVICE_ADMIN)
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @Test
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#provisionFullyManagedDevice"])
    fun provisionFullyManagedDevice_disallowAddManagedProfileIsSet() {
        val systemSetupComplete = TestApis.users().system().setupComplete
        TestApis.users().system().setupComplete = false
        try {
            val params = createDefaultManagedDeviceProvisioningParamsBuilder().build()
            localDevicePolicyManager.provisionFullyManagedDevice(params)
            assertThat(
                TestApis.devicePolicy().userRestrictions()
                    .isSet(CommonUserRestrictions.DISALLOW_ADD_MANAGED_PROFILE)
            ).isTrue()
        } finally {
            val deviceOwner = TestApis.devicePolicy().getDeviceOwner()
            deviceOwner?.remove()
            val profileOwner = TestApis.devicePolicy().getProfileOwner()
            profileOwner?.remove()
            TestApis.users().system().setupComplete = systemSetupComplete
        }
    }

    @EnsureHasNoDpc
    @RequireFeature(CommonPackages.FEATURE_DEVICE_ADMIN)
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @Test
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#provisionFullyManagedDevice"])
    fun provisionFullyManagedDevice_canControlSensorPermissionGrantsByDefault() {
        val setupComplete = TestApis.users().system().setupComplete
        TestApis.users().system().setupComplete = false
        try {
            val params = createDefaultManagedDeviceProvisioningParamsBuilder().build()
            localDevicePolicyManager.provisionFullyManagedDevice(params)
            assertThat(TestApis.devicePolicy().canAdminGrantSensorsPermissions()).isTrue()
        } finally {
            val deviceOwner = TestApis.devicePolicy().getDeviceOwner()
            deviceOwner?.remove()
            TestApis.users().system().setupComplete = setupComplete
        }
    }

    @EnsureHasNoDpc
    @RequireFeature(CommonPackages.FEATURE_DEVICE_ADMIN)
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    @Test
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#provisionFullyManagedDevice"])
    fun provisionFullyManagedDevice_canOptOutOfControllingSensorPermissionGrants() {
        val setupComplete = TestApis.users().system().setupComplete
        TestApis.users().system().setupComplete = false
        try {
            val params = createDefaultManagedDeviceProvisioningParamsBuilder()
                .setCanDeviceOwnerGrantSensorsPermissions(false)
                .build()
            localDevicePolicyManager.provisionFullyManagedDevice(params)
            assertThat(TestApis.devicePolicy().canAdminGrantSensorsPermissions()).isFalse()
        } finally {
            val deviceOwner = TestApis.devicePolicy().getDeviceOwner()
            deviceOwner?.remove()
            TestApis.users().system().setupComplete = setupComplete
        }
    }

    @EnsureHasNoDpc
    @RequireFeature(CommonPackages.FEATURE_DEVICE_ADMIN)
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @Test
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#provisionFullyManagedDevice"])
    fun provisionFullyManagedDevice_leavesAllSystemAppsEnabledWhenRequested() {
        val setupComplete = TestApis.users().system().setupComplete
        TestApis.users().system().setupComplete = false
        try {
            val systemAppsBeforeProvisioning = TestApis.packages().systemApps()
            val params = createDefaultManagedDeviceProvisioningParamsBuilder()
                .setLeaveAllSystemAppsEnabled(true)
                .build()
            localDevicePolicyManager.provisionFullyManagedDevice(params)
            val systemAppsAfterProvisioning = TestApis.packages().systemApps()
            assertThat(systemAppsAfterProvisioning).isEqualTo(systemAppsBeforeProvisioning)
        } finally {
            val deviceOwner = TestApis.devicePolicy().getDeviceOwner()
            deviceOwner?.remove()
            TestApis.users().system().setupComplete = setupComplete
        }
    }

    @Postsubmit(reason = "New test")
    @EnsureHasNoDpc
    @RequireFeature(CommonPackages.FEATURE_DEVICE_ADMIN)
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @EnsureIsNotDemoDevice
    @Test
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#provisionFullyManagedDevice"])
    fun provisionFullyManagedDevice_setsDeviceAsDemoDeviceWhenRequested() {
        val setupComplete = TestApis.users().system().setupComplete
        TestApis.users().system().setupComplete = false
        try {
            val params = createDefaultManagedDeviceProvisioningParamsBuilder()
                .setDemoDevice(true)
                .build()
            localDevicePolicyManager.provisionFullyManagedDevice(params)
            assertThat(
                TestApis.settings().global().getInt(Settings.Global.DEVICE_DEMO_MODE, 0)
            ).isEqualTo(1)
        } finally {
            val deviceOwner = TestApis.devicePolicy().getDeviceOwner()
            deviceOwner?.remove()
            TestApis.users().system().setupComplete = setupComplete
            TestApis.settings().global().putInt(Settings.Global.DEVICE_DEMO_MODE, 0)
        }
    }

    @Test
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @PermissionTest(CommonPermissions.INTERACT_ACROSS_USERS)
    @EnsureHasAdditionalUser
    fun getUserProvisioningState_differentUser_validPermission_doesNotThrow() {
        TestApis.context()
            .androidContextAsUser(deviceState.additionalUser())
            .getSystemService(DevicePolicyManager::class.java)!!
            .userProvisioningState
    }

    @Test
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @EnsureDoesNotHavePermission(
        CommonPermissions.MANAGE_USERS,
        CommonPermissions.INTERACT_ACROSS_USERS
    )
    @RequireFeature(CommonPackages.FEATURE_DEVICE_ADMIN)
    @EnsureHasAdditionalUser
    fun getUserProvisioningState_differentUser_noPermission_throwsException() {
        assertThrows(
            SecurityException::class.java
        ) {
            TestApis.context().androidContextAsUser(deviceState.additionalUser())
                .getSystemService(DevicePolicyManager::class.java)!!
                .userProvisioningState
        }
    }

    @Postsubmit(reason = "New test")
    @EnsureHasNoDpc
    @RequireFeature(CommonPackages.FEATURE_DEVICE_ADMIN)
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @Test
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#provisionFullyManagedDevice"])
    fun provisionFullyManagedDevice_setsProvisioningStateWhenDemoDeviceIsRequested() {
        val setupComplete = TestApis.users().system().setupComplete
        TestApis.users().system().setupComplete = false
        try {
            val params = createDefaultManagedDeviceProvisioningParamsBuilder()
                .setDemoDevice(true)
                .build()
            localDevicePolicyManager.provisionFullyManagedDevice(params)
            assertThat(
                TestApis.devicePolicy().getUserProvisioningState(TestApis.users().system())
            ).isEqualTo(DevicePolicyManager.STATE_USER_SETUP_FINALIZED)
        } finally {
            val deviceOwner = TestApis.devicePolicy().getDeviceOwner()
            deviceOwner?.remove()
            TestApis.users().system().setupComplete = setupComplete
            TestApis.settings().global().putInt(Settings.Global.DEVICE_DEMO_MODE, 0)
        }
    }

    @Postsubmit(reason = "New test")
    @EnsureHasNoDpc
    @RequireFeature(CommonPackages.FEATURE_DEVICE_ADMIN)
    @EnsureHasPermission(
        Manifest.permission.PROVISION_DEMO_DEVICE
    )
    @EnsureDoesNotHavePermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @Test
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#provisionFullyManagedDevice"])
    fun provisionFullyManagedDevice_withProvisionDemoDevicePermission_throwsSecurityException() {
        val params = createDefaultManagedDeviceProvisioningParamsBuilder()
            .build()
        assertThrows(SecurityException::class.java) {
            localDevicePolicyManager.provisionFullyManagedDevice(
                params
            )
        }
    }

    @Postsubmit(reason = "New test")
    @EnsureHasNoDpc
    @RequireFeature(CommonPackages.FEATURE_DEVICE_ADMIN)
    @EnsureHasPermission(
        Manifest.permission.PROVISION_DEMO_DEVICE
    )
    @EnsureDoesNotHavePermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @Test
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#provisionFullyManagedDevice"])
    fun provisionFullyManagedDevice_withProvisionDemoDevicePermissionForDemoDevice_doesNotThrowException() {
        val setupComplete = TestApis.users().system().setupComplete
        TestApis.users().system().setupComplete = false
        try {
            val params = createDefaultManagedDeviceProvisioningParamsBuilder()
                .setDemoDevice(true)
                .build()
            localDevicePolicyManager.provisionFullyManagedDevice(params)
        } finally {
            val deviceOwner = TestApis.devicePolicy().getDeviceOwner()
            deviceOwner?.remove()
            TestApis.users().system().setupComplete = setupComplete
            TestApis.settings().global().putInt(Settings.Global.DEVICE_DEMO_MODE, 0)
        }
    }

    @Postsubmit(reason = "New test")
    @EnsureHasNoDpc
    @RequireFeature(CommonPackages.FEATURE_DEVICE_ADMIN)
    @EnsureDoesNotHavePermission(
        Manifest.permission.PROVISION_DEMO_DEVICE,
        CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS
    )
    @Test
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#provisionFullyManagedDevice"])
    fun provisionFullyManagedDevice_withoutRequiredPermissionsForDemoDevice_throwsSecurityException() {
        val params = createDefaultManagedDeviceProvisioningParamsBuilder()
            .setDemoDevice(true)
            .build()
        assertThrows(SecurityException::class.java) {
            localDevicePolicyManager.provisionFullyManagedDevice(
                params
            )
        }
    }

    @Test
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#createProvisioningIntentFromNfcIntent"])
    fun createProvisioningIntentFromNfcIntent_validNfcIntent_returnsValidIntent() {
        val nfcIntent = createNfcIntentFromMap(NFC_DATA_VALID)
        val provisioningIntent =
            localDevicePolicyManager.createProvisioningIntentFromNfcIntent(nfcIntent)
        assertThat(provisioningIntent!!.action)
            .isEqualTo(DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE)
        assertBundlesEqual(provisioningIntent.extras, createExpectedValidBundle())
    }

    @Test
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#createProvisioningIntentFromNfcIntent"])
    fun createProvisioningIntentFromNfcIntent_noComponentNorPackage_returnsNull() {
        val nfcIntent = createNfcIntentFromMap(NFC_DATA_EMPTY)
        val provisioningIntent =
            localDevicePolicyManager.createProvisioningIntentFromNfcIntent(nfcIntent)
        assertThat(provisioningIntent).isNull()
    }

    @Test
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#createProvisioningIntentFromNfcIntent"])
    fun createProvisioningIntentFromNfcIntent_withComponent_returnsValidIntent() {
        val nfcIntent = createNfcIntentFromMap(NFC_DATA_WITH_COMPONENT_NAME)
        val provisioningIntent =
            localDevicePolicyManager.createProvisioningIntentFromNfcIntent(nfcIntent)
        assertThat(provisioningIntent!!.action)
            .isEqualTo(DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE)
        assertBundlesEqual(provisioningIntent.extras, EXPECTED_BUNDLE_WITH_COMPONENT_NAME)
    }

    @Test
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#createProvisioningIntentFromNfcIntent"])
    fun createProvisioningIntentFromNfcIntent_withPackage_returnsValidIntent() {
        val nfcIntent = createNfcIntentFromMap(NFC_DATA_WITH_ADMIN_PACKAGE_NAME)
        val provisioningIntent =
            localDevicePolicyManager.createProvisioningIntentFromNfcIntent(nfcIntent)
        assertThat(provisioningIntent!!.action)
            .isEqualTo(DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE)
        assertBundlesEqual(provisioningIntent.extras, EXPECTED_BUNDLE_WITH_PACKAGE_NAME)
    }

    @Test
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#createProvisioningIntentFromNfcIntent"])
    fun createProvisioningIntentFromNfcIntent_badIntentAction_returnsNull() {
        val nfcIntent = createNfcIntentWithAction(NFC_INTENT_BAD_ACTION)

        val provisioningIntent =
            localDevicePolicyManager.createProvisioningIntentFromNfcIntent(nfcIntent)

        assertThat(provisioningIntent).isNull()
    }

    @Test
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#createProvisioningIntentFromNfcIntent"])
    fun createProvisioningIntentFromNfcIntent_badMimeType_returnsNull() {
        val nfcIntent = createNfcIntentWithMimeType(NFC_INTENT_BAD_MIME)

        val provisioningIntent =
            localDevicePolicyManager.createProvisioningIntentFromNfcIntent(nfcIntent)

        assertThat(provisioningIntent).isNull()
    }

    @Test
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#createProvisioningIntentFromNfcIntent"])
    fun createProvisioningIntentFromNfcIntent_doesNotIncludeNdefRecord_returnsNull() {
        val provisioningIntent = localDevicePolicyManager
            .createProvisioningIntentFromNfcIntent(NFC_INTENT_NO_NDEF_RECORD)

        assertThat(provisioningIntent).isNull()
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureDoesNotHavePermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#checkProvisioningPrecondition"])
    fun checkProvisioningPreCondition_withoutRequiredPermission_throwsSecurityException() {
        assertThrows(SecurityException::class.java) {
            localDevicePolicyManager.checkProvisioningPrecondition(
                DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE,
                DEVICE_ADMIN_COMPONENT_NAME.packageName
            )
        }
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#checkProvisioningPrecondition"])
    fun checkProvisioningPreCondition_withRequiredPermission_doesNotThrowSecurityException() {
        localDevicePolicyManager.checkProvisioningPrecondition(
            DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE,
            DEVICE_ADMIN_COMPONENT_NAME.packageName
        )

        // Doesn't throw exception.
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @RequireFeature(CommonPackages.FEATURE_MANAGED_USERS)
    @RequireDoesNotHaveFeature(CommonPackages.FEATURE_DEVICE_ADMIN)
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#checkProvisioningPrecondition"])
    fun checkProvisioningPreCondition_withoutDeviceAdminFeature_returnsDeviceAdminNotSupported() {
        assertThat(
            localDevicePolicyManager.checkProvisioningPrecondition(
                DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE,
                DEVICE_ADMIN_COMPONENT_NAME.packageName
            )
        )
            .isEqualTo(DevicePolicyManager.STATUS_DEVICE_ADMIN_NOT_SUPPORTED)
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @EnsureHasNoDpc
    @RequireFeature(CommonPackages.FEATURE_MANAGED_USERS)
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#checkProvisioningPrecondition"])
    fun checkProvisioningPreCondition_actionPO_returnsOk() {
        assertThat(
            localDevicePolicyManager.checkProvisioningPrecondition(
                DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE,
                DEVICE_ADMIN_COMPONENT_NAME.packageName
            )
        )
            .isEqualTo(DevicePolicyManager.STATUS_OK)
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @RequireDoesNotHaveFeature(CommonPackages.FEATURE_MANAGED_USERS)
    @RequireFeature(CommonPackages.FEATURE_DEVICE_ADMIN)
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#checkProvisioningPrecondition"])
    fun checkProvisioningPreCondition_actionPO_withoutManagedUserFeature_returnsManagedUsersNotSupported() {
        assertThat(
            localDevicePolicyManager.checkProvisioningPrecondition(
                DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE,
                DEVICE_ADMIN_COMPONENT_NAME.packageName
            )
        )
            .isEqualTo(DevicePolicyManager.STATUS_MANAGED_USERS_NOT_SUPPORTED)
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @RequireRunOnSecondaryUser
    @EnsureHasProfileOwner
    @RequireFeature(CommonPackages.FEATURE_MANAGED_USERS)
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#checkProvisioningPrecondition"])
    fun checkProvisioningPreCondition_actionPO_onManagedUser_returnsHasProfileOwner() {
        assertThat(
            localDevicePolicyManager.checkProvisioningPrecondition(
                DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE,
                DEVICE_ADMIN_COMPONENT_NAME.packageName
            )
        )
            .isEqualTo(DevicePolicyManager.STATUS_USER_HAS_PROFILE_OWNER)
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @RequireRunOnWorkProfile
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#checkProvisioningPrecondition"])
    fun checkProvisioningPreCondition_actionPO_onManagedProfile_returnsHasProfileOwner() {
        assertThat(
            localDevicePolicyManager.checkProvisioningPrecondition(
                DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE,
                DEVICE_ADMIN_COMPONENT_NAME.packageName
            )
        )
            .isEqualTo(DevicePolicyManager.STATUS_USER_HAS_PROFILE_OWNER)
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @EnsureHasNoProfileOwner
    @EnsureHasWorkProfile
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#checkProvisioningPrecondition"])
    fun checkProvisioningPreCondition_actionPO_withWorkProfile_returnsCanNotAddManagedProfile() {
        assertThat(
            localDevicePolicyManager.checkProvisioningPrecondition(
                DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE,
                DEVICE_ADMIN_COMPONENT_NAME.packageName
            )
        )
            .isEqualTo(DevicePolicyManager.STATUS_CANNOT_ADD_MANAGED_PROFILE)
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @EnsureHasNoDpc
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#checkProvisioningPrecondition"])
    fun checkProvisioningPreCondition_actionDO_afterSetupComplete_returnsUserSetupComplete() {
        val setupComplete = TestApis.users().system().setupComplete
        TestApis.users().system().setupComplete = true
        try {
            assertThat(
                localDevicePolicyManager.checkProvisioningPrecondition(
                    DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE,
                    DEVICE_ADMIN_COMPONENT_NAME.packageName
                )
            )
                .isEqualTo(DevicePolicyManager.STATUS_USER_SETUP_COMPLETED)
        } finally {
            TestApis.users().system().setupComplete = setupComplete
        }
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @EnsureHasNoDpc
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#checkProvisioningPrecondition"])
    fun checkProvisioningPreCondition_actionDO_returnsOk() {
        val setupComplete = TestApis.users().system().setupComplete
        TestApis.users().system().setupComplete = false
        try {
            assertThat(
                localDevicePolicyManager.checkProvisioningPrecondition(
                    DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE,
                    DEVICE_ADMIN_COMPONENT_NAME.packageName
                )
            )
                .isEqualTo(DevicePolicyManager.STATUS_OK)
        } finally {
            TestApis.users().system().setupComplete = setupComplete
        }
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @EnsureHasNoDpc
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#checkProvisioningPrecondition"])
    fun checkProvisioningPreCondition_actionDO_setupComplete_returnsUserSetupCompleted() {
        val setupComplete = TestApis.users().current().setupComplete
        TestApis.users().current().setupComplete = true
        try {
            assertThat(
                localDevicePolicyManager.checkProvisioningPrecondition(
                    DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE,
                    DEVICE_ADMIN_COMPONENT_NAME.packageName
                )
            )
                .isEqualTo(DevicePolicyManager.STATUS_USER_SETUP_COMPLETED)
        } finally {
            TestApis.users().current().setupComplete = setupComplete
        }
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @EnsureHasDeviceOwner
    @RequireNotWatch(reason = "Watches will fail because they're already paired")
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#checkProvisioningPrecondition"])
    fun checkProvisioningPreCondition_actionDO_onManagedDevice_returnsHasDeviceOwner() {
        val setupComplete = TestApis.users().current().setupComplete
        TestApis.users().current().setupComplete = false
        try {
            assertThat(
                localDevicePolicyManager.checkProvisioningPrecondition(
                    DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE,
                    DEVICE_ADMIN_COMPONENT_NAME.packageName
                )
            )
                .isEqualTo(DevicePolicyManager.STATUS_HAS_DEVICE_OWNER)
        } finally {
            TestApis.users().current().setupComplete = setupComplete
        }
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @RequireRunOnSecondaryUser
    @EnsureHasNoProfileOwner
    @RequireNotHeadlessSystemUserMode(reason = "TODO(b/242189747): Remove or give reason")
    @RequireFeature(CommonPackages.FEATURE_DEVICE_ADMIN)
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#checkProvisioningPrecondition"])
    fun checkProvisioningPreCondition_actionDO_onNonSystemUser_returnsNotSystemUser() {
        val setupComplete = TestApis.users().current().setupComplete
        TestApis.users().current().setupComplete = false
        try {
            assertThat(
                localDevicePolicyManager.checkProvisioningPrecondition(
                    DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE,
                    DEVICE_ADMIN_COMPONENT_NAME.packageName
                )
            )
                .isEqualTo(DevicePolicyManager.STATUS_NOT_SYSTEM_USER)
        } finally {
            TestApis.users().current().setupComplete = setupComplete
        }
    }

    // TODO(b/208843126): add more CTS coverage for setUserProvisioningState
    @Postsubmit(reason = "New test")
    @Test
    @EnsureDoesNotHavePermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @RequireFeature(CommonPackages.FEATURE_DEVICE_ADMIN)
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setUserProvisioningState"])
    fun setUserProvisioningState_withoutRequiredPermission_throwsSecurityException() {
        assertThrows(SecurityException::class.java) {
            localDevicePolicyManager.setUserProvisioningState(
                DevicePolicyManager.STATE_USER_UNMANAGED,
                TestApis.users().current().userHandle()
            )
        }
    }

    @Ignore("b/284786466")
    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @RequireRunOnWorkProfile
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setUserProvisioningState"])
    fun setUserProvisioningState_withRequiredPermission_doesNotThrowSecurityException() {
        localDevicePolicyManager.setUserProvisioningState(
            DevicePolicyManager.STATE_USER_SETUP_COMPLETE,
            TestApis.users().current().userHandle()
        )

        // Doesn't throw exception.
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @EnsureHasNoDpc
    @RequireFeature(CommonPackages.FEATURE_MANAGED_USERS)
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setUserProvisioningState"])
    fun setUserProvisioningState_unmanagedDevice_stateUserSetupIncomplete_throwsIllegalStateException() {
        assertThrows(IllegalStateException::class.java) {
            localDevicePolicyManager.setUserProvisioningState(
                DevicePolicyManager.STATE_USER_SETUP_INCOMPLETE,
                TestApis.users().current().userHandle()
            )
        }
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @EnsureHasNoDpc
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setUserProvisioningState"])
    fun setUserProvisioningState_unmanagedDevice_stateUserSetupComplete_throwsIllegalStateException() {
        assertThrows(IllegalStateException::class.java) {
            localDevicePolicyManager.setUserProvisioningState(
                DevicePolicyManager.STATE_USER_SETUP_COMPLETE,
                TestApis.users().current().userHandle()
            )
        }
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @EnsureHasNoDpc
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setUserProvisioningState"])
    fun setUserProvisioningState_unmanagedDevice_stateUserSetupFinalized_throwsIllegalStateException() {
        assertThrows(IllegalStateException::class.java) {
            localDevicePolicyManager.setUserProvisioningState(
                DevicePolicyManager.STATE_USER_SETUP_FINALIZED,
                TestApis.users().current().userHandle()
            )
        }
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @EnsureHasNoDpc
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setUserProvisioningState"])
    fun setUserProvisioningState_unmanagedDevice_stateUserProfileComplete_throwsIllegalStateException() {
        assertThrows(IllegalStateException::class.java) {
            localDevicePolicyManager.setUserProvisioningState(
                DevicePolicyManager.STATE_USER_PROFILE_COMPLETE,
                TestApis.users().current().userHandle()
            )
        }
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @EnsureHasNoDpc
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setUserProvisioningState"])
    fun setUserProvisioningState_unmanagedDevice_stateUserProfileFinalized_throwsIllegalStateException() {
        assertThrows(IllegalStateException::class.java) {
            localDevicePolicyManager.setUserProvisioningState(
                DevicePolicyManager.STATE_USER_PROFILE_FINALIZED,
                TestApis.users().current().userHandle()
            )
        }
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @EnsureHasNoDpc
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setUserProvisioningState"])
    fun setUserProvisioningState_settingToSameState_throwIllegalStateException() {
        assertThrows(IllegalStateException::class.java) {
            localDevicePolicyManager.setUserProvisioningState(
                DevicePolicyManager.STATE_USER_UNMANAGED,
                TestApis.users().current().userHandle()
            )
        }
    }

    @Test
    @ApiTest(apis = ["android.app.admin.ManagedProfileProvisioningParams#setAdminExtras"])
    fun setAdminExtras_managedProfileParams_works() {
        val params = createManagedProfileProvisioningParamsBuilder()
            .setAdminExtras(ADMIN_EXTRAS_BUNDLE)
            .build()
        assertBundlesEqual(params.adminExtras, ADMIN_EXTRAS_BUNDLE)
    }

    @Test
    @ApiTest(apis = ["android.app.admin.ManagedProfileProvisioningParams#setAdminExtras"])
    fun setAdminExtras_managedProfileParams_modifyBundle_internalBundleNotModified() {
        val adminExtrasBundle = PersistableBundle(ADMIN_EXTRAS_BUNDLE)
        val params = createManagedProfileProvisioningParamsBuilder()
            .setAdminExtras(adminExtrasBundle)
            .build()
        adminExtrasBundle.putString(TEST_KEY, TEST_VALUE)
        assertBundlesEqual(params.adminExtras, ADMIN_EXTRAS_BUNDLE)
    }

    @ApiTest(apis = ["android.app.admin.ManagedProfileProvisioningParams#getAdminExtras"])
    @Test
    fun getAdminExtras_managedProfileParams_modifyResult_internalBundleNotModified() {
        val adminExtrasBundle = PersistableBundle(ADMIN_EXTRAS_BUNDLE)
        val params = createManagedProfileProvisioningParamsBuilder()
            .setAdminExtras(adminExtrasBundle)
            .build()
        params.adminExtras.putString(TEST_KEY, TEST_VALUE)
        assertBundlesEqual(params.adminExtras, ADMIN_EXTRAS_BUNDLE)
    }

    @Test
    @ApiTest(apis = ["android.app.admin.ManagedProfileProvisioningParams#setAdminExtras"])
    fun setAdminExtras_managedProfileParams_emptyBundle_works() {
        val params = createManagedProfileProvisioningParamsBuilder()
            .setAdminExtras(PersistableBundle())
            .build()
        assertThat(params.adminExtras.isEmpty).isTrue()
    }

    @Test
    @ApiTest(apis = ["android.app.admin.ManagedProfileProvisioningParams#setAdminExtras"])
    fun setAdminExtras_fullyManagedParams_works() {
        val params = createDefaultManagedDeviceProvisioningParamsBuilder()
            .setAdminExtras(ADMIN_EXTRAS_BUNDLE)
            .build()
        assertBundlesEqual(params.adminExtras, ADMIN_EXTRAS_BUNDLE)
    }

    @Test
    @ApiTest(apis = ["android.app.admin.ManagedProfileProvisioningParams#setAdminExtras"])
    fun setAdminExtras_fullyManagedParams_modifyBundle_internalBundleNotModified() {
        val adminExtrasBundle = PersistableBundle(ADMIN_EXTRAS_BUNDLE)
        val params = createDefaultManagedDeviceProvisioningParamsBuilder()
            .setAdminExtras(adminExtrasBundle)
            .build()
        adminExtrasBundle.putString(TEST_KEY, TEST_VALUE)
        assertBundlesEqual(params.adminExtras, ADMIN_EXTRAS_BUNDLE)
    }

    @ApiTest(apis = ["android.app.admin.ManagedProfileProvisioningParams#getAdminExtras"])
    @Test
    fun getAdminExtras_fullyManagedParams_modifyResult_internalBundleNotModified() {
        val adminExtrasBundle = PersistableBundle(ADMIN_EXTRAS_BUNDLE)
        val params = createDefaultManagedDeviceProvisioningParamsBuilder()
            .setAdminExtras(adminExtrasBundle)
            .build()
        params.adminExtras.putString(TEST_KEY, TEST_VALUE)
        assertBundlesEqual(params.adminExtras, ADMIN_EXTRAS_BUNDLE)
    }

    @Test
    @ApiTest(apis = ["android.app.admin.ManagedProfileProvisioningParams#setAdminExtras"])
    fun setAdminExtras_fullyManagedParams_emptyBundle_works() {
        val params = createDefaultManagedDeviceProvisioningParamsBuilder()
            .setAdminExtras(PersistableBundle())
            .build()
        assertThat(params.adminExtras.isEmpty).isTrue()
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureDoesNotHavePermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setDpcDownloaded"])
    fun setDpcDownloaded_withoutRequiredPermission_throwsSecurityException() {
        assertThrows(SecurityException::class.java) {
            localDevicePolicyManager.isDpcDownloaded = true
        }
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    fun setDpcDownloaded_withRequiredPermission_doesNotThrowSecurityException() {
        localDevicePolicyManager.isDpcDownloaded = true

        // Doesn't throw exception
    }

    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @Test
    @Postsubmit(reason = "New test")
    fun isDpcDownloaded_returnsResultOfSetDpcDownloaded() {
        localDevicePolicyManager.isDpcDownloaded = true

        assertThat(localDevicePolicyManager.isDpcDownloaded).isTrue()
    }

    @Postsubmit(reason = "new test")
    @Test
    @EnsureHasWorkProfile
    @EnsureDoesNotHavePermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    fun finalizeWorkProfileProvisioning_withoutPermission_throwsException() {
        assertThrows(SecurityException::class.java) {
            localDevicePolicyManager.finalizeWorkProfileProvisioning(
                deviceState.workProfile().userHandle(),  /* migratedAccount= */
                null
            )
        }
    }

    @Postsubmit(reason = "new test")
    @Test
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    fun finalizeWorkProfileProvisioning_nonExistingManagedProfileUser_throwsException() {
        assertThrows(IllegalStateException::class.java) {
            localDevicePolicyManager.finalizeWorkProfileProvisioning( /* managedProfileUser= */
                TestApis.users().nonExisting().userHandle(),  /* migratedAccount= */
                null
            )
        }
    }

    @Postsubmit(reason = "new test")
    @Test
    @EnsureHasSecondaryUser
    @EnsureHasNoDpc
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @RequireFeature(CommonPackages.FEATURE_DEVICE_ADMIN)
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#finalizeWorkProfileProvisioning"])
    fun finalizeWorkProfileProvisioning_managedUser_throwsException() {
        val dpc = RemoteDpc.setAsProfileOwner(deviceState.secondaryUser())
        try {
            assertThrows(IllegalStateException::class.java) {
                localDevicePolicyManager.finalizeWorkProfileProvisioning( /* managedProfileUser= */
                    deviceState.secondaryUser().userHandle(),  /* migratedAccount= */
                    null
                )
            }
        } finally {
            dpc.remove()
        }
    }

    @Postsubmit(reason = "new test")
    @Test
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @EnsureHasWorkProfile
    fun finalizeWorkProfileProvisioning_managedProfileUserWithoutProfileOwner_throwsException() {
        val dpc = deviceState.profileOwner(deviceState.workProfile())
        try {
            dpc.remove()
            assertThrows(IllegalStateException::class.java) {
                localDevicePolicyManager.finalizeWorkProfileProvisioning( /* managedProfileUser= */
                    deviceState.workProfile().userHandle(),  /* migratedAccount= */
                    null
                )
            }
        } finally {
            RemoteDpc.setAsProfileOwner(deviceState.workProfile())
        }
    }

    @Postsubmit(reason = "new test")
    @Test
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @EnsureHasNoProfileOwner
    @EnsureHasNoDeviceOwner
    @EnsureHasWorkProfile
    fun finalizeWorkProfileProvisioning_valid_sendsBroadcast() {
        RemoteDpc.forDevicePolicyController(
            TestApis.devicePolicy().getProfileOwner(
                deviceState.workProfile()
            )
        ).testApp().install().use { personalInstance ->
            // We know that RemoteDPC is the Profile Owner - we need the same package on the
            // personal side to receive the broadcast
            personalInstance.registerReceiver(IntentFilter(DevicePolicyManager.ACTION_MANAGED_PROFILE_PROVISIONED))
            localDevicePolicyManager.finalizeWorkProfileProvisioning( /* managedProfileUser= */
                deviceState.workProfile().userHandle(),  /* migratedAccount= */
                null
            )
            val event = personalInstance.events().broadcastReceived()
                .whereIntent().action()
                .isEqualTo(DevicePolicyManager.ACTION_MANAGED_PROFILE_PROVISIONED)
                .waitForEvent()
            assertThat(
                event.intent().getParcelableExtra(Intent.EXTRA_USER) as UserHandle?
            )
                .isEqualTo(deviceState.workProfile().userHandle())
        }
    }

    @Postsubmit(reason = "new test")
    @Test
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @EnsureHasNoProfileOwner
    @EnsureHasNoDeviceOwner
    @EnsureHasWorkProfile
    fun finalizeWorkProfileProvisioning_withAccount_broadcastIncludesAccount() {
        RemoteDpc.forDevicePolicyController(
            TestApis.devicePolicy().getProfileOwner(
                deviceState.workProfile()
            )
        ).testApp().install().use { personalInstance ->
            // We know that RemoteDPC is the Profile Owner - we need the same package on the
            // personal side to receive the broadcast
            personalInstance.registerReceiver(IntentFilter(DevicePolicyManager.ACTION_MANAGED_PROFILE_PROVISIONED))
            localDevicePolicyManager.finalizeWorkProfileProvisioning( /* managedProfileUser= */
                deviceState.workProfile().userHandle(),  /* migratedAccount= */
                ACCOUNT_WITH_EXISTING_TYPE
            )
            val event = personalInstance.events().broadcastReceived()
                .whereIntent().action()
                .isEqualTo(DevicePolicyManager.ACTION_MANAGED_PROFILE_PROVISIONED)
                .waitForEvent()
            assertThat(
                event.intent()
                    .getParcelableExtra(DevicePolicyManager.EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE) as Account?
            )
                .isEqualTo(ACCOUNT_WITH_EXISTING_TYPE)
        }
    }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#isProvisioningAllowed"])
    @RequireFeature(CommonPackages.FEATURE_MANAGED_USERS)
    @EnsureHasNoDpc
    @Test
    fun isProvisioningAllowed_hasManagedUsersFeature_returnsTrue() {
        assertThat(
            localDevicePolicyManager
                .isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
        ).isTrue()
    }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#isProvisioningAllowed"])
    @RequireFeature(CommonPackages.FEATURE_MANAGED_USERS)
    @EnsureHasProfileOwner
    @Test
    fun isProvisioningAllowed_userIsManaged_returnsFalse() {
        assertThat(
            localDevicePolicyManager
                .isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
        ).isFalse()
    }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#isProvisioningAllowed"])
    @RequireFeature(CommonPackages.FEATURE_MANAGED_USERS)
    @EnsureHasWorkProfile(dpcIsPrimary = true)
    @Test
    fun isProvisioningAllowed_profileIsManaged_returnsFalse() {
        assertThat(
            localDevicePolicyManager
                .isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
        ).isFalse()
    }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#isProvisioningAllowed"])
    @RequireFeature(CommonPackages.FEATURE_MANAGED_USERS)
    @EnsureHasUserRestriction(CommonUserRestrictions.DISALLOW_ADD_MANAGED_PROFILE)
    @Test
    fun isProvisioningAllowed_disallowAddManagedProfile_returnsFalse() {
        assertThat(
            localDevicePolicyManager
                .isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
        ).isFalse()
    }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#isProvisioningAllowed"])
    @RequireDoesNotHaveFeature(CommonPackages.FEATURE_MANAGED_USERS)
    @EnsureHasNoDpc
    @Test
    fun isProvisioningAllowed_doesNotHaveManagedUsersFeature_returnsFalse() {
        assertThat(
            localDevicePolicyManager
                .isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
        ).isFalse()
    }

    @Postsubmit(reason = "new test")
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#isProvisioningAllowed"])
    @Test
    @EnsureHasDeviceOwner
    fun isProvisioningAllowed_hasDeviceOwner_returnsFalse() {
    assertThat(
      localDevicePolicyManager.isProvisioningAllowed(
        DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE
    )).isFalse()
    }
    @Throws(IOException::class)
    private fun createNfcIntentFromMap(input: Map<String, String>): Intent {
        return createNfcIntent(
            input,
            NfcAdapter.ACTION_NDEF_DISCOVERED,
            DevicePolicyManager.MIME_TYPE_PROVISIONING_NFC
        )
    }

    @Throws(IOException::class)
    private fun createNfcIntentWithAction(action: String): Intent {
        return createNfcIntent(
            NFC_DATA_VALID,
            action,
            DevicePolicyManager.MIME_TYPE_PROVISIONING_NFC
        )
    }

    @Throws(IOException::class)
    private fun createNfcIntentWithMimeType(mime: String): Intent {
        return createNfcIntent(NFC_DATA_VALID, NfcAdapter.ACTION_NDEF_DISCOVERED, mime)
    }

    @Throws(IOException::class)
    private fun createNfcIntent(input: Map<String, String>, action: String, mime: String): Intent {
        val nfcIntent = Intent(action)
        val nfcMessages = arrayOf<Parcelable>(createNdefMessage(input, mime))
        nfcIntent.putExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, nfcMessages)
        return nfcIntent
    }

    @Throws(IOException::class)
    private fun createNdefMessage(
        provisioningValues: Map<String, String>,
        mime: String
    ): NdefMessage {
        val stream = ByteArrayOutputStream()
        val properties = Properties()
        // Store all the values into the Properties object
        for ((key, value) in provisioningValues) {
            properties[key] = value
        }
        properties.store(stream, NFC_INTENT_PROVISIONING_SAMPLE)
        val record = NdefRecord.createMime(mime, stream.toByteArray())
        return NdefMessage(arrayOf(record))
    }

    private fun createDefaultManagedDeviceProvisioningParamsBuilder(): FullyManagedDeviceProvisioningParams.Builder {
        return FullyManagedDeviceProvisioningParams.Builder(
            DEVICE_ADMIN_COMPONENT_NAME,
            DEVICE_OWNER_NAME
        ) // Don't remove system apps during provisioning until the testing
            // infrastructure supports restoring uninstalled apps.
            .setLeaveAllSystemAppsEnabled(true)
    }

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val deviceState = DeviceState()

        private val context = TestApis.context().instrumentedContext()
        private val localDevicePolicyManager = context.getSystemService(
            DevicePolicyManager::class.java
        )!!
        private val CAUSE = Exception()
        private const val PROVISIONING_ERROR = ProvisioningException.ERROR_PRE_CONDITION_FAILED
        private const val MESSAGE = "test failure message"
        private const val NFC_INTENT_COMPONENT_NAME =
            "com.test.dpc/com.test.dpc.DeviceAdminReceiver"
        private const val NFC_INTENT_PACKAGE_NAME = "com.test.dpc.DeviceAdminReceiver"
        private const val NFC_INTENT_LOCALE = "en_US"
        private const val NFC_INTENT_TIMEZONE = "America/New_York"
        private const val NFC_INTENT_WIFI_SSID = "\"" + "TestWifiSsid" + "\""
        private const val NFC_INTENT_WIFI_SECURITY_TYPE = ""
        private const val NFC_INTENT_WIFI_PASSWORD = ""
        private const val NFC_INTENT_BAD_ACTION = "badAction"
        private const val NFC_INTENT_BAD_MIME = "badMime"
        private const val NFC_INTENT_PROVISIONING_SAMPLE = "NFC provisioning sample"
        private val NFC_INTENT_NO_NDEF_RECORD = Intent(NfcAdapter.ACTION_NDEF_DISCOVERED)
        private val NFC_DATA_VALID = createNfcIntentData()
        private val NFC_DATA_EMPTY = HashMap<String, String>()
        private val NFC_DATA_WITH_COMPONENT_NAME = java.util.Map.of(
            DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
            NFC_INTENT_COMPONENT_NAME
        )
        private val EXPECTED_BUNDLE_WITH_COMPONENT_NAME = createExpectedBundleWithComponentName()
        private val NFC_DATA_WITH_ADMIN_PACKAGE_NAME = java.util.Map.of(
            DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME,
            NFC_INTENT_PACKAGE_NAME
        )
        private val EXPECTED_BUNDLE_WITH_PACKAGE_NAME = createExpectedBundleWithPackageName()
        private const val NFC_INTENT_LOCAL_TIME: Long = 123456
        private const val NFC_INTENT_WIFI_PROXY_PORT = 1234
        private const val PROFILE_OWNER_NAME = "testDeviceAdmin"
        private const val DEVICE_OWNER_NAME = "testDeviceAdmin"
        private val DEVICE_ADMIN_COMPONENT_NAME = DeviceAdminApp.deviceAdminComponentName(context)
        private val DEVICE_ADMIN_COMPONENT = TestApis.packages().component(
            DEVICE_ADMIN_COMPONENT_NAME
        )

        private val MANAGED_PROFILE_PARAMS = createManagedProfileProvisioningParamsBuilder().build()
        private val ADMIN_EXTRAS_BUNDLE = createAdminExtrasBundle()
        private val ROLE_HOLDER_EXTRAS_BUNDLE = createRoleHolderExtrasBundle()
        private const val TEST_KEY = "test_key"
        private const val TEST_VALUE = "test_value"
        private const val EXISTING_ACCOUNT_TYPE =
            "com.android.bedstead.testapp.AccountManagementApp.account.type"
        private val ACCOUNT_WITH_EXISTING_TYPE = Account("user0", EXISTING_ACCOUNT_TYPE)
        private val sNoHeadlessSupportTestApp = deviceState.testApps().query()
            .wherePackageName().isEqualTo("com.android.bedstead.testapp.DeviceAdminTestApp")
            .get()

        private fun createManagedProfileProvisioningParamsBuilder(): ManagedProfileProvisioningParams.Builder {
            return ManagedProfileProvisioningParams.Builder(
                DEVICE_ADMIN_COMPONENT_NAME,
                PROFILE_OWNER_NAME
            )
        }

        private fun createNfcIntentData(): HashMap<String, String> {
            val nfcIntentInput = HashMap<String, String>()
            nfcIntentInput[DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME] =
                NFC_INTENT_COMPONENT_NAME
            nfcIntentInput[DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME] =
                NFC_INTENT_PACKAGE_NAME
            nfcIntentInput[DevicePolicyManager.EXTRA_PROVISIONING_LOCALE] =
                NFC_INTENT_LOCALE
            nfcIntentInput[DevicePolicyManager.EXTRA_PROVISIONING_TIME_ZONE] = NFC_INTENT_TIMEZONE
            nfcIntentInput[DevicePolicyManager.EXTRA_PROVISIONING_WIFI_SSID] = NFC_INTENT_WIFI_SSID
            nfcIntentInput[DevicePolicyManager.EXTRA_PROVISIONING_WIFI_SECURITY_TYPE] =
                NFC_INTENT_WIFI_SECURITY_TYPE
            nfcIntentInput[DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PASSWORD] =
                NFC_INTENT_WIFI_PASSWORD
            nfcIntentInput[DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE] =
                createAdminExtrasProperties()
            nfcIntentInput[DevicePolicyManager.EXTRA_PROVISIONING_ROLE_HOLDER_EXTRAS_BUNDLE] =
                createRoleHolderExtrasProperties()
            nfcIntentInput[DevicePolicyManager.EXTRA_PROVISIONING_SKIP_EDUCATION_SCREENS] =
                "true"
            nfcIntentInput[DevicePolicyManager.EXTRA_PROVISIONING_LOCAL_TIME] =
                NFC_INTENT_LOCAL_TIME.toString()
            nfcIntentInput[DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PROXY_PORT] =
                NFC_INTENT_WIFI_PROXY_PORT.toString()
            return nfcIntentInput
        }

        private fun createExpectedValidBundle(): Bundle {
            val bundle = Bundle()
            bundle.putParcelable(
                DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                ComponentName.unflattenFromString(NFC_INTENT_COMPONENT_NAME)
            )
            bundle.putString(
                DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME,
                NFC_INTENT_PACKAGE_NAME
            )
            bundle.putString(DevicePolicyManager.EXTRA_PROVISIONING_LOCALE, NFC_INTENT_LOCALE)
            bundle.putString(DevicePolicyManager.EXTRA_PROVISIONING_TIME_ZONE, NFC_INTENT_TIMEZONE)
            bundle.putString(DevicePolicyManager.EXTRA_PROVISIONING_WIFI_SSID, NFC_INTENT_WIFI_SSID)
            bundle.putString(
                DevicePolicyManager.EXTRA_PROVISIONING_WIFI_SECURITY_TYPE,
                NFC_INTENT_WIFI_SECURITY_TYPE
            )
            bundle.putString(
                DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PASSWORD,
                NFC_INTENT_WIFI_PASSWORD
            )
            bundle.putParcelable(
                DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE,
                ADMIN_EXTRAS_BUNDLE
            )
            bundle.putParcelable(
                DevicePolicyManager.EXTRA_PROVISIONING_ROLE_HOLDER_EXTRAS_BUNDLE,
                ROLE_HOLDER_EXTRAS_BUNDLE
            )
            bundle.putBoolean(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_EDUCATION_SCREENS, true)
            bundle.putLong(DevicePolicyManager.EXTRA_PROVISIONING_LOCAL_TIME, NFC_INTENT_LOCAL_TIME)
            bundle.putInt(
                DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PROXY_PORT,
                NFC_INTENT_WIFI_PROXY_PORT
            )
            bundle.putInt(
                DevicePolicyManager.EXTRA_PROVISIONING_TRIGGER,
                DevicePolicyManager.PROVISIONING_TRIGGER_NFC
            )
            return bundle
        }

        private fun createRoleHolderExtrasProperties(): String {
            return "role-holder-extras-key=role holder extras value\n"
        }

        private fun createExpectedBundleWithComponentName(): Bundle {
            val bundle = Bundle()
            bundle.putParcelable(
                DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                ComponentName.unflattenFromString(NFC_INTENT_COMPONENT_NAME)
            )
            bundle.putInt(
                DevicePolicyManager.EXTRA_PROVISIONING_TRIGGER,
                DevicePolicyManager.PROVISIONING_TRIGGER_NFC
            )
            return bundle
        }

        private fun createExpectedBundleWithPackageName(): Bundle {
            val bundle = Bundle()
            bundle.putString(
                DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME,
                NFC_INTENT_PACKAGE_NAME
            )
            bundle.putInt(
                DevicePolicyManager.EXTRA_PROVISIONING_TRIGGER,
                DevicePolicyManager.PROVISIONING_TRIGGER_NFC
            )
            return bundle
        }

        private fun createAdminExtrasBundle(): PersistableBundle {
            val result = PersistableBundle()
            result.putString("admin-extras-key", "admin extras value")
            return result
        }

        private fun createAdminExtrasProperties(): String {
            return "admin-extras-key=admin extras value\n"
        }

        private fun createRoleHolderExtrasBundle(): PersistableBundle {
            val result = PersistableBundle()
            result.putString("role-holder-extras-key", "role holder extras value")
            return result
        }

        private fun assertBundlesEqual(bundle1: BaseBundle?, bundle2: BaseBundle?) {
            if (bundle1 != null) {
                Truth.assertWithMessage("Intent bundles are not equal")
                    .that(bundle2).isNotNull()
                Truth.assertWithMessage("Intent bundles are not equal")
                    .that(bundle1.keySet().size).isEqualTo(bundle2!!.keySet().size)
                for (key in bundle1.keySet()) {
                    if (bundle1[key] != null && bundle1[key] is PersistableBundle) {
                        Truth.assertWithMessage("Intent bundles are not equal")
                            .that(bundle2.containsKey(key)).isTrue()
                        Truth.assertWithMessage("Intent bundles are not equal")
                            .that(bundle2[key]).isInstanceOf(PersistableBundle::class.java)
                        assertBundlesEqual(
                            bundle1[key] as PersistableBundle?,
                            bundle2[key] as PersistableBundle?
                        )
                    } else {
                        Truth.assertWithMessage("Intent bundles are not equal")
                            .that(bundle1[key])
                            .isEqualTo(bundle2[key])
                    }
                }
            } else {
                Truth.assertWithMessage("Intent bundles are not equal").that(bundle2).isNull()
            }
        }
    }
}
