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
package com.android.bedstead.nene.devicepolicy

import android.Manifest
import android.annotation.TargetApi
import android.app.admin.DevicePolicyManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.os.UserHandle
import android.util.Log
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.annotations.Experimental
import com.android.bedstead.nene.exceptions.AdbException
import com.android.bedstead.nene.exceptions.AdbParseException
import com.android.bedstead.nene.exceptions.NeneException
import com.android.bedstead.nene.packages.ComponentReference
import com.android.bedstead.nene.packages.Package
import com.android.bedstead.nene.permissions.CommonPermissions
import com.android.bedstead.nene.permissions.CommonPermissions.BIND_DEVICE_ADMIN
import com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS_FULL
import com.android.bedstead.nene.permissions.CommonPermissions.NOTIFY_PENDING_SYSTEM_UPDATE
import com.android.bedstead.nene.permissions.CommonPermissions.QUERY_ADMIN_POLICY
import com.android.bedstead.nene.roles.RoleContext
import com.android.bedstead.nene.users.UserReference
import com.android.bedstead.nene.utils.Poll
import com.android.bedstead.nene.utils.Retry
import com.android.bedstead.nene.utils.ShellCommand
import com.android.bedstead.nene.utils.ShellCommandUtils
import com.android.bedstead.nene.utils.Versions
import java.lang.reflect.InvocationTargetException
import java.time.Duration
import java.util.stream.Collectors

/**
 * Test APIs related to device policy.
 */
object DevicePolicy {
    private val mParser = AdbDevicePolicyParser.get(Build.VERSION.SDK_INT)
    private var mCachedDeviceOwner: DeviceOwner? = null
    private var mCachedProfileOwners: Map<UserReference, ProfileOwner>? = null

    /**
     * Set the profile owner for a given [UserReference].
     */
    fun setProfileOwner(user: UserReference, profileOwnerComponent: ComponentName): ProfileOwner {
        val command = ShellCommand.builderForUser(user, "dpm set-profile-owner")
            .addOperand(profileOwnerComponent.flattenToShortString())
            .validate { ShellCommandUtils.startsWithSuccess(it) }

        // TODO(b/187925230): If it fails, we check for terminal failure states - and if not
        //  we retry because if the profile owner was recently removed, it can take some time
        //  to be allowed to set it again
        try {
            Retry.logic { command.execute() }
                .terminalException { ex: Throwable ->
                    if (!Versions.meetsMinimumSdkVersionRequirement(Build.VERSION_CODES.S)) {
                        return@terminalException false // Just retry on old versions as we don't have stderr
                    }
                    if (ex is AdbException) {
                        val error = ex.error()
                        if (error != null && error.contains("is already set")) {
                            // This can happen for a while when it is being tidied up
                            return@terminalException false
                        }
                        if (error != null && error.contains("is being removed")) {
                            return@terminalException false
                        }
                    }
                    true
                }
                .timeout(Duration.ofMinutes(5))
                .run()
        } catch (e: Throwable) {
            throw NeneException(
                "Could not set profile owner for user "
                        + user + " component " + profileOwnerComponent, e
            )
        }
        Poll.forValue("Profile Owner") { TestApis.devicePolicy().getProfileOwner(user) }
            .toNotBeNull()
            .errorOnFail()
            .await()
        return ProfileOwner(
            user,
            TestApis.packages().find(
                profileOwnerComponent.packageName
            ), profileOwnerComponent
        )
    }

    val organizationOwnedProfileOwner: ProfileOwner?
        /**
         * Get the organization owned profile owner for the device, if any, otherwise null.
         */
        get() {
            for (user in TestApis.users().all()) {
                val profileOwner = getProfileOwner(user)
                if (profileOwner != null && profileOwner.isOrganizationOwned) {
                    return profileOwner
                }
            }
            return null
        }

    /**
     * Get the profile owner for a given [UserHandle].
     */
    fun getProfileOwner(user: UserHandle): ProfileOwner? {
        return getProfileOwner(UserReference.of(user))
    }

    /**
     * Get the profile owner for a given [UserReference].
     */
    @JvmOverloads
    fun getProfileOwner(user: UserReference = TestApis.users().instrumented()): ProfileOwner? {
        fillCache()
        // mCachedProfileOwners has been filled by fillCache
        return mCachedProfileOwners!![user]
    }

    /**
     * Set the device owner.
     */
    @JvmOverloads
    fun setDeviceOwner(deviceOwnerComponent: ComponentName, user: UserReference = TestApis.users().system()): DeviceOwner {
        if (!Versions.meetsMinimumSdkVersionRequirement(Build.VERSION_CODES.S)) {
            return setDeviceOwnerPreS(deviceOwnerComponent)
        } else if (!Versions.meetsMinimumSdkVersionRequirement(Versions.U)) {
            return setDeviceOwnerPreU(deviceOwnerComponent)
        }
        try {
            TestApis.permissions().withPermission(
                CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS,
                CommonPermissions.MANAGE_DEVICE_ADMINS,
                Manifest.permission.INTERACT_ACROSS_USERS_FULL,
                Manifest.permission.INTERACT_ACROSS_USERS,
                Manifest.permission.CREATE_USERS
            ).use {
                val command = ShellCommand.builderForUser(
                    user, "dpm set-device-owner --device-owner-only"
                )
                    .addOperand(deviceOwnerComponent.flattenToShortString())
                    .validate { ShellCommandUtils.startsWithSuccess(it) }
                // TODO(b/187925230): If it fails, we check for terminal failure states - and if not
                //  we retry because if the DO/PO was recently removed, it can take some time
                //  to be allowed to set it again
                Retry.logic { command.execute() }
                    .terminalException { e: Throwable ->
                        checkForTerminalDeviceOwnerFailures(
                            user,
                            deviceOwnerComponent,  /* allowAdditionalUsers= */
                            false,
                            e
                        )
                    }
                    .timeout(Duration.ofMinutes(5))
                    .run()
            }
        } catch (e: Throwable) {
            throw NeneException("Error setting device owner.", e)
        }
        val deviceOwnerPackage = TestApis.packages().find(
            deviceOwnerComponent.packageName
        )
        Poll.forValue("Device Owner") { TestApis.devicePolicy().getDeviceOwner() }
            .toNotBeNull()
            .errorOnFail()
            .await()
        return DeviceOwner(user, deviceOwnerPackage, deviceOwnerComponent)
    }

    /**
     * Set Device Owner without changing any other device state.
     *
     *
     * This is used instead of [DevicePolicyManager.setDeviceOwner] directly
     * because on S_V2 and above, that method can also set profile owners and install packages in
     * some circumstances.
     */
    private fun setDeviceOwnerOnly(
        component: ComponentName, deviceOwnerUserId: Int
    ) {
        if (Versions.meetsMinimumSdkVersionRequirement(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)) {
            devicePolicyManager.setDeviceOwnerOnly(component, deviceOwnerUserId)
        } else if (Versions.meetsMinimumSdkVersionRequirement(Build.VERSION_CODES.S_V2)) {
            try {
                DevicePolicyManager::class.java.getMethod(
                    "setDeviceOwnerOnly",
                    ComponentName::class.java,
                    String::class.java,
                    Int::class.javaPrimitiveType
                ).invoke(devicePolicyManager, component, null, deviceOwnerUserId)
            } catch (e: IllegalAccessException) {
                throw NeneException("Error executing setDeviceOwnerOnly", e)
            } catch (e: InvocationTargetException) {
                throw NeneException("Error executing setDeviceOwnerOnly", e)
            } catch (e: NoSuchMethodException) {
                throw NeneException("Error executing setDeviceOwnerOnly", e)
            }
        } else {
            try {
                DevicePolicyManager::class.java.getMethod(
                    "setDeviceOwner",
                    ComponentName::class.java,
                    String::class.java,
                    Int::class.javaPrimitiveType
                ).invoke(devicePolicyManager, component, null, deviceOwnerUserId)
            } catch (e: IllegalAccessException) {
                throw NeneException("Error executing setDeviceOwner", e)
            } catch (e: InvocationTargetException) {
                throw NeneException("Error executing setDeviceOwner", e)
            } catch (e: NoSuchMethodException) {
                throw NeneException("Error executing setDeviceOwner", e)
            }
        }
    }

    /**
     * Resets organization ID via @TestApi.
     *
     * @param user whose organization ID to clear
     */
    @JvmOverloads
    fun clearOrganizationId(user: UserReference = TestApis.users().instrumented()) {
        TestApis.permissions().withPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
            .use { devicePolicyManager(user).clearOrganizationId() }
    }

    /**
     * See [DevicePolicyManager.setNextOperationSafety].
     */
    fun setNextOperationSafety(
        operation: CommonDevicePolicy.DevicePolicyOperation,
        reason: CommonDevicePolicy.OperationSafetyReason
    ) {
        TestApis.permissions().withPermission(
            CommonPermissions.MANAGE_DEVICE_ADMINS, Manifest.permission.INTERACT_ACROSS_USERS
        ).use { devicePolicyManager.setNextOperationSafety(operation.value, reason.value) }
    }

    /**
     * See [DevicePolicyManager.lockNow].
     */
    fun lockNow() {
        devicePolicyManager.lockNow()
    }

    private fun devicePolicyManager(user: UserReference): DevicePolicyManager =
        if (user == TestApis.users().instrumented()) devicePolicyManager
        else TestApis.context().androidContextAsUser(user)
            .getSystemService(DevicePolicyManager::class.java)!!

    private fun setDeviceOwnerPreU(deviceOwnerComponent: ComponentName): DeviceOwner {
        val user = TestApis.users().system()
        val dpmUserSetupComplete = user.setupComplete
        try {
            user.setupComplete = false
            try {
                TestApis.permissions().withPermission(
                    CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS,
                    CommonPermissions.MANAGE_DEVICE_ADMINS,
                    Manifest.permission.INTERACT_ACROSS_USERS_FULL,
                    Manifest.permission.INTERACT_ACROSS_USERS,
                    Manifest.permission.CREATE_USERS
                ).use {
                    // TODO(b/187925230): If it fails, we check for terminal failure states - and if not
                    //  we retry because if the DO/PO was recently removed, it can take some time
                    //  to be allowed to set it again
                    Retry.logic {
                        devicePolicyManager.setActiveAdmin(
                            deviceOwnerComponent,  /* refreshing= */
                            true,
                            user.id()
                        )
                        setDeviceOwnerOnly(deviceOwnerComponent, user.id())
                    }
                        .terminalException { e: Throwable ->
                            checkForTerminalDeviceOwnerFailures(
                                user,
                                deviceOwnerComponent,  /* allowAdditionalUsers= */
                                true,
                                e
                            )
                        }
                        .timeout(Duration.ofMinutes(5))
                        .run()
                }
            } catch (e: Throwable) {
                throw NeneException("Error setting device owner", e)
            }
        } finally {
            user.setupComplete = dpmUserSetupComplete
        }
        Poll.forValue("Device Owner") { TestApis.devicePolicy().getDeviceOwner() }
            .toNotBeNull()
            .errorOnFail()
            .await()
        return DeviceOwner(
            user,
            TestApis.packages().find(deviceOwnerComponent.packageName),
            deviceOwnerComponent
        )
    }

    private fun setDeviceOwnerPreS(deviceOwnerComponent: ComponentName): DeviceOwner {
        val user = TestApis.users().system()
        val command = ShellCommand.builderForUser(
            user, "dpm set-device-owner"
        )
            .addOperand(deviceOwnerComponent.flattenToShortString())
            .validate { ShellCommandUtils.startsWithSuccess(it) }
        // TODO(b/187925230): If it fails, we check for terminal failure states - and if not
        //  we retry because if the device owner was recently removed, it can take some time
        //  to be allowed to set it again
        try {
            Retry.logic { command.execute() }
                .terminalException { e: Throwable ->
                    checkForTerminalDeviceOwnerFailures(
                        user,
                        deviceOwnerComponent,  /* allowAdditionalUsers= */
                        false,
                        e
                    )
                }
                .timeout(Duration.ofMinutes(5))
                .run()
        } catch (e: Throwable) {
            throw NeneException("Error setting device owner", e)
        }
        return DeviceOwner(
            user,
            TestApis.packages().find(
                deviceOwnerComponent.packageName
            ), deviceOwnerComponent
        )
    }

    private fun checkForTerminalDeviceOwnerFailures(
        user: UserReference,
        deviceOwnerComponent: ComponentName,
        allowAdditionalUsers: Boolean,
        e: Throwable
    ): Boolean {
        val deviceOwner = getDeviceOwner()
        if (deviceOwner != null) {
            // TODO(scottjonathan): Should we actually fail here if the component name is the
            //  same?
            throw NeneException(
                "Could not set device owner for user $user as a device owner is already set: $deviceOwner",
                e
            )
        }
        val pkg = TestApis.packages().find(deviceOwnerComponent.packageName)
        if (!TestApis.packages().installedForUser(user).contains(pkg)) {
            throw NeneException(
                "Could not set device owner for user $user as the package $pkg is not installed",
                e
            )
        }
        if (!componentCanBeSetAsDeviceAdmin(deviceOwnerComponent, user)) {
            throw NeneException(
                "Could not set device owner for user $user as component $deviceOwnerComponent is not valid",
                e
            )
        }
        if (!allowAdditionalUsers && nonTestNonPrecreatedUsersExist()) {
            throw NeneException(
                "Could not set device owner for user $user as there are already additional non-test on the device",
                e
            )
        }
        // TODO(scottjonathan): Check accounts
        return false
    }

    private fun componentCanBeSetAsDeviceAdmin(
        component: ComponentName,
        user: UserReference
    ): Boolean {
        val packageManager = TestApis.context().instrumentedContext().packageManager
        val intent = Intent("android.app.action.DEVICE_ADMIN_ENABLED")
        intent.setComponent(component)
        TestApis.permissions().withPermission(Manifest.permission.INTERACT_ACROSS_USERS_FULL)
            .use {
                val r = packageManager.queryBroadcastReceiversAsUser(
                    intent,  /* flags= */0, user.userHandle()
                )
                return r.isNotEmpty()
            }
    }

    /**
     * Get the device owner.
     */
    fun getDeviceOwner(): DeviceOwner? {
        fillCache()
        return mCachedDeviceOwner
    }

    private fun fillCache() {
        var retries = 5
        while (true) {
            try {
                // TODO: Replace use of adb on supported versions of Android
                val devicePolicyDumpsysOutput =
                    ShellCommand.builder("dumpsys device_policy").execute()
                val result = mParser.parse(devicePolicyDumpsysOutput)
                mCachedDeviceOwner = result.mDeviceOwner
                mCachedProfileOwners = result.mProfileOwners
                return
            } catch (e: AdbParseException) {
                if (e.adbOutput().contains("DUMP TIMEOUT") && retries-- > 0) {
                    // Sometimes this call times out - just retry
                    Log.e(LOG_TAG, "Dump timeout when filling cache, retrying", e)
                } else {
                    throw NeneException("Error filling cache", e)
                }
            } catch (e: AdbException) {
                throw NeneException("Error filling cache", e)
            }
        }
    }

    /** See [android.app.admin.DevicePolicyManager.getPolicyExemptApps].  */
    @Experimental
    fun getPolicyExemptApps(): Set<String> {
            TestApis.permissions().withPermission(CommonPermissions.MANAGE_DEVICE_ADMINS).use {
                return devicePolicyManager.policyExemptApps
            }
        }

    @Experimental
    fun forceNetworkLogs() {
        TestApis.permissions().withPermission(
            CommonPermissions.FORCE_DEVICE_POLICY_MANAGER_LOGS
        ).use {
            val throttle = devicePolicyManager.forceNetworkLogs()
            if (throttle == -1L) {
                throw NeneException("Error forcing network logs: returned -1")
            }
            if (throttle == 0L) {
                return
            }
            try {
                Thread.sleep(throttle)
            } catch (e: InterruptedException) {
                throw NeneException("Error waiting for network log throttle", e)
            }
            forceNetworkLogs()
        }
    }

    @Experimental
    fun forceSecurityLogs() {
        TestApis.permissions().withPermission(
            CommonPermissions.FORCE_DEVICE_POLICY_MANAGER_LOGS
        ).use {
            val throttle = devicePolicyManager.forceSecurityLogs()
            if (throttle == -1L) {
                throw NeneException("Error forcing security logs: returned -1")
            }
            if (throttle == 0L) {
                return
            }
            try {
                Thread.sleep(throttle)
            } catch (e: InterruptedException) {
                throw NeneException("Error waiting for security log throttle", e)
            }
            forceSecurityLogs()
        }
    }

    /**
     * Sets the provided `packageName` as a device policy management role holder.
     */
    @TargetApi(Build.VERSION_CODES.TIRAMISU)
    @Experimental
    @JvmOverloads
    fun setDevicePolicyManagementRoleHolder(pkg: Package, user: UserReference = TestApis.users().instrumented()): RoleContext {
        Versions.requireMinimumVersion(Build.VERSION_CODES.TIRAMISU)
        if (!Versions.meetsMinimumSdkVersionRequirement(Versions.U)) {
            if (TestApis.users().all().size > 1) {
                throw NeneException(
                    "Could not set device policy management role holder as"
                            + " more than one user is on the device"
                )
            }
        }
        if (nonTestNonPrecreatedUsersExist()) {
            throw NeneException(
                "Could not set device policy management role holder as"
                        + " non-test users already exist"
            )
        }
        TestApis.roles().setBypassingRoleQualification(true)
        return pkg.setAsRoleHolder(RoleManager.ROLE_DEVICE_POLICY_MANAGEMENT, user)
    }

    private fun nonTestNonPrecreatedUsersExist(): Boolean {
        val expectedPrecreatedUsers = if (TestApis.users().isHeadlessSystemUserMode) 2 else 1
        return TestApis.users().all().stream()
            .filter { u: UserReference -> !u.isForTesting }
            .count() > expectedPrecreatedUsers
    }

    /**
     * Unsets the provided `packageName` as a device policy management role holder.
     */
    @TargetApi(Build.VERSION_CODES.TIRAMISU)
    @Experimental
    @JvmOverloads
    fun unsetDevicePolicyManagementRoleHolder(pkg: Package, user: UserReference = TestApis.users().instrumented()) {
        Versions.requireMinimumVersion(Build.VERSION_CODES.TIRAMISU)
        pkg.removeAsRoleHolder(RoleManager.ROLE_DEVICE_POLICY_MANAGEMENT, user)
    }

    /**
     * Returns true if the AutoTimeRequired policy is set to true for the given user.
     */
    @JvmOverloads
    @Experimental
    fun autoTimeRequired(user: UserReference = TestApis.users().instrumented()) =
        devicePolicyManager(user).autoTimeRequired

    /**
     * See `DevicePolicyManager#isNewUserDisclaimerAcknowledged`.
     */
    @JvmOverloads
    @Experimental
    fun isNewUserDisclaimerAcknowledged(user: UserReference = TestApis.users().instrumented()): Boolean =
        TestApis.permissions().withPermission(CommonPermissions.INTERACT_ACROSS_USERS).use {
            devicePolicyManager(user).isNewUserDisclaimerAcknowledged
        }

    /**
     * Access APIs related to Device Policy resource overriding.
     */
    @TargetApi(Build.VERSION_CODES.TIRAMISU)
    fun resources(): DevicePolicyResources {
        Versions.requireMinimumVersion(Build.VERSION_CODES.TIRAMISU)
        return DevicePolicyResources.sInstance
    }

    /**
     * Get active admins on the given user.
     */
    @JvmOverloads
    fun getActiveAdmins(user: UserReference = TestApis.users().instrumented()): Set<ComponentReference> {
        TestApis.permissions().withPermission(Manifest.permission.INTERACT_ACROSS_USERS_FULL)
            .use {
                val activeAdmins = devicePolicyManager(user).activeAdmins ?: return setOf()
                return activeAdmins.stream()
                    .map { component: ComponentName? -> ComponentReference(component) }
                    .collect(
                        Collectors.toSet()
                    )
            }
    }

    /**
     * See
     * [DevicePolicyManager.resetShouldAllowBypassingDevicePolicyManagementRoleQualificationState].
     */
    @TargetApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun resetShouldAllowBypassingDevicePolicyManagementRoleQualificationState() {
        Versions.requireMinimumVersion(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        TestApis.permissions().withPermission(CommonPermissions.MANAGE_ROLE_HOLDERS).use {
            devicePolicyManager(TestApis.users().instrumented())
                .resetShouldAllowBypassingDevicePolicyManagementRoleQualificationState()
        }
    }

    /**
     * Set or check user restrictions.
     */
    fun userRestrictions(user: UserHandle): UserRestrictions {
        return userRestrictions(UserReference.of(user))
    }

    /**
     * Set or check user restrictions.
     */
    @JvmOverloads
    fun userRestrictions(user: UserReference = TestApis.users().instrumented()): UserRestrictions {
        return UserRestrictions(user)
    }

    /**
     * OEM-Set default cross profile packages.
     */
    @Experimental
    fun defaultCrossProfilePackages(): Set<Package> {
        return devicePolicyManager.defaultCrossProfilePackages
            .stream().map { TestApis.packages().find(it) }
            .collect(Collectors.toSet())
    }

    /**
     * True if there is a Device Owner who can grant sensor permissions.
     */
    @Experimental
    fun canAdminGrantSensorsPermissions(): Boolean {
        return if (!Versions.meetsMinimumSdkVersionRequirement(31)) {
            true
        } else devicePolicyManager.canAdminGrantSensorsPermissions()
    }

    /**
     * @see DevicePolicyManager.getUserProvisioningState
     */
    @Experimental
    @JvmOverloads
    fun getUserProvisioningState(user: UserReference = TestApis.users().instrumented()): Int =
        TestApis.permissions().withPermission(Manifest.permission.INTERACT_ACROSS_USERS)
            .use { devicePolicyManager(user).userProvisioningState }

    /**
     * See [DevicePolicyManager.getPasswordExpirationTimeout].
     */
    @Experimental
    @JvmOverloads
    fun getPasswordExpirationTimeout(user: UserReference = TestApis.users().instrumented()): Long =
        TestApis.permissions().withPermission(Manifest.permission.INTERACT_ACROSS_USERS).use {
            devicePolicyManager(user).getPasswordExpirationTimeout( /* admin= */null)
        }

    /**
     * See [DevicePolicyManager.getMaximumTimeToLock].
     */
    @Experimental
    @JvmOverloads
    fun getMaximumTimeToLock(user: UserReference = TestApis.users().instrumented()): Long =
        TestApis.permissions().withPermission(Manifest.permission.INTERACT_ACROSS_USERS).use {
            devicePolicyManager(user).getMaximumTimeToLock( /* admin= */null)
        }

    /**
     * See [DevicePolicyManager.getRequiredStrongAuthTimeout].
     */
    @Experimental
    @JvmOverloads
    fun getRequiredStrongAuthTimeout(user: UserReference = TestApis.users().instrumented()): Long =
        TestApis.permissions().withPermission(Manifest.permission.INTERACT_ACROSS_USERS).use {
            devicePolicyManager(user).getRequiredStrongAuthTimeout( /* admin= */null)
        }

    // TODO: Consider wrapping keyguard disabled features with a bedstead concept instead of flags
    /**
     * See [DevicePolicyManager.getKeyguardDisabledFeatures].
     */
    @Experimental
    @JvmOverloads
    fun getKeyguardDisabledFeatures(user: UserReference = TestApis.users().instrumented()): Int =
        TestApis.permissions().withPermission(Manifest.permission.INTERACT_ACROSS_USERS).use {
            devicePolicyManager(user).getKeyguardDisabledFeatures( /* admin= */null)
        }

    /**
     * Gets configuration for the `trustAgent` for all admins and `user`.
     *
     *
     * See
     * [DevicePolicyManager.getTrustAgentConfiguration].
     */
    @Experimental
    @JvmOverloads
    fun getTrustAgentConfiguration(
        trustAgent: ComponentName, user: UserReference = TestApis.users().instrumented()
    ): Set<PersistableBundle> {
        TestApis.permissions().withPermission(Manifest.permission.INTERACT_ACROSS_USERS).use {
            val configurations = devicePolicyManager(user)
                .getTrustAgentConfiguration( /* admin= */null, trustAgent)
            return if (configurations == null) setOf() else java.util.Set.copyOf(configurations)
        }
    }


    // TODO(276248451): Make user handle aware so it'll work cross-user
    /**
     * True if either this is the system user or the user is affiliated with a device owner on
     * the device.
     */
    @Experimental
    @JvmOverloads
    fun isAffiliated(user: UserReference = TestApis.users().instrumented()): Boolean =
        devicePolicyManager(user).isAffiliatedUser

    /** See [DevicePolicyManager#permittedInputMethods]. */
    @Experimental
    // TODO: This doesn't currently work cross-user
    fun getPermittedInputMethods(): List<String>? =
            TestApis.permissions().withPermission(CommonPermissions.QUERY_ADMIN_POLICY)
                .use { devicePolicyManager.permittedInputMethodsForCurrentUser }

    /**
     * Recalculate the "hasIncompatibleAccounts" cache inside DevicePolicyManager.
     */
    @Experimental
    fun calculateHasIncompatibleAccounts() {
        if (!Versions.meetsMinimumSdkVersionRequirement(Versions.U)) {
            // Nothing to calculate pre-U
            return
        }
        TestApis.logcat()
            .listen { it.contains("Finished calculating hasIncompatibleAccountsTask") }
            .use { devicePolicyManager.calculateHasIncompatibleAccounts() }
    }

    /**
     * Determine whether Bluetooth devices cannot access contacts on `user`.
     *
     * See `DevicePolicyManager#getBluetoothContactSharingDisabled(UserHandle)`
     */
    @JvmOverloads
    fun getBluetoothContactSharingDisabled(user: UserReference = TestApis.users().instrumented()): Boolean =
        devicePolicyManager.getBluetoothContactSharingDisabled(user.userHandle())

    /** See [DevicePolicyManager.getPermittedAccessibilityServices]  */
    @Experimental
    @JvmOverloads
    fun getPermittedAccessibilityServices(user: UserReference = TestApis.users().instrumented()): Set<Package>? =
        TestApis.permissions().withPermission(
            Manifest.permission.INTERACT_ACROSS_USERS,
            CommonPermissions.QUERY_ADMIN_POLICY
        ).use {
            devicePolicyManager.getPermittedAccessibilityServices(
                user.id()
            )?.stream()
                ?.map { packageName: String? -> TestApis.packages().find(packageName) }
                ?.collect(Collectors.toSet())
        }

    /** See [DevicePolicyManager.getStorageEncryptionStatus]  */
    fun getStorageEncryptionStatus(): Int =
        devicePolicyManager.storageEncryptionStatus

    /** See [DevicePolicyManager.createAdminSupportIntent]  */
    @Experimental
    fun createAdminSupportIntent(restriction: String): Intent? =
        devicePolicyManager.createAdminSupportIntent(restriction)

    /** See [DevicePolicyManager.isFactoryResetProtectionPolicySupported]  */
    fun isFactoryResetProtectionPolicySupported(): Boolean =
        devicePolicyManager.isFactoryResetProtectionPolicySupported

    @Experimental
    fun notifyPendingSystemUpdate(updateReceivedTime: Long, isSecurityPatch: Boolean? = null) {
        TestApis.permissions().withPermission(NOTIFY_PENDING_SYSTEM_UPDATE).use {
            if (isSecurityPatch == null) {
                devicePolicyManager.notifyPendingSystemUpdate(updateReceivedTime)
            } else {
                devicePolicyManager.notifyPendingSystemUpdate(updateReceivedTime, isSecurityPatch)
            }
        }
    }

    /** See [DevicePolicyManager#isInputMethodSetByOwner]. */
    @Experimental
    fun isCurrentInputMethodSetByOwner() =
        isCurrentInputMethodSetByOwner(TestApis.users().instrumented())

    /** See [DevicePolicyManager#isInputMethodSetByOwner]. */
    @Experimental
    fun isCurrentInputMethodSetByOwner(user: UserReference) =
        TestApis.permissions().withPermission(QUERY_ADMIN_POLICY).use {
            devicePolicyManager(user).isCurrentInputMethodSetByOwner
        }

    /** See [DevicePolicyManager#getOwnerInstalledCaCerts]. */
    @Experimental
    fun getOwnerInstalledCaCerts() = getOwnerInstalledCaCerts(TestApis.users().instrumented())

    /** See [DevicePolicyManager#getOwnerInstalledCaCerts]. */
    @Experimental
    fun getOwnerInstalledCaCerts(user: UserReference) =
        TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL, QUERY_ADMIN_POLICY).use {
            devicePolicyManager(user).getOwnerInstalledCaCerts(user.userHandle())
        }


    private const val LOG_TAG = "DevicePolicy"

    private val devicePolicyManager: DevicePolicyManager by lazy { TestApis.context().instrumentedContext()
            .getSystemService(DevicePolicyManager::class.java)!! }
}
