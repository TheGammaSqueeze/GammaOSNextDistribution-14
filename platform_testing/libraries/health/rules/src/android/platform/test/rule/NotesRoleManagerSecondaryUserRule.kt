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
package android.platform.test.rule

import android.Manifest.permission.CREATE_USERS
import android.Manifest.permission.INTERACT_ACROSS_USERS
import android.Manifest.permission.INTERACT_ACROSS_USERS_FULL
import android.Manifest.permission.MANAGE_USERS
import android.app.ActivityManager
import android.app.role.RoleManager
import android.os.Build
import android.os.UserHandle
import android.platform.helpers.notesrole.NotesRoleUtil
import android.system.helpers.UserHelper
import android.util.Log
import androidx.core.content.getSystemService
import java.util.concurrent.TimeUnit
import org.junit.runner.Description

/**
 * This rule allows end-to-end tests to manage requirements pertaining to [RoleManager.ROLE_NOTES]
 * for secondary user CUJs.
 *
 * <p>The rule sets the current user's Notes role to "None" before creating a secondary user for the
 * test and setting the supplied package as it's Notes role holder. The rule also takes care of
 * deleting the secondary test user and restoring the original user's Notes role holder.
 *
 * @param requiredAndroidVersion enforce required Android version, default is Android U
 * @param requiredNotesRoleHolderPackage the package name for the [RoleManager.ROLE_NOTES] holder
 *   that should be used in the tests. The rule will take care of setting and restoring the role
 *   holder for each test. Test will be skipped if the provided package is not installed. The rule
 *   will also ensure to kill this app before and after the test to ensure clean state.
 */
class NotesRoleManagerSecondaryUserRule
@JvmOverloads
constructor(
    private val requiredAndroidVersion: Int = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
    private val requiredNotesRoleHolderPackage: String,
) : TestWatcher() {

    private val notesRoleUtil = NotesRoleUtil(context)
    private val originalUserId = context.userId
    private val userHelper = UserHelper.getInstance()
    private val originalUserPrevNotesRoleHolder = notesRoleUtil.getRoleHolderPackageName()

    private var testUserId: Int? = null

    override fun starting(description: Description?) {
        super.starting(description)

        // Check if the device configuration can run the test. Assumption is used instead of
        // assertion because not all devices have the Notes role or the required app installed.
        // So this rule takes care of skipping the test on incompatible devices. This is a
        // workaround to run the test on select few devices.
        notesRoleUtil.checkAndroidRolePackageAssumptions(
            requiredAndroidVersion,
            requiredNotesRoleHolderPackage
        )

        // Update original user's notes role holder to none. This helps with confidently verifying
        // secondary user CUJs as we avoid the off chance that original user's notes role holder is
        // launched and we accidentally confirm it instead of verifying whether secondary user's
        // notes role holder is launched in tests.
        notesRoleUtil.clearRoleHolder()

        // Create the secondary user.
        testUserId =
            userHelper.createSecondaryUser(SECONDARY_USER).also {
                // Switch to the newly created user.
                switchToUser(it)

                // Update the secondary user's notes role holder.
                notesRoleUtil.setRoleHolder(requiredNotesRoleHolderPackage, UserHandle.of(it))
            }

        notesRoleUtil.forceStopPackage(requiredNotesRoleHolderPackage)
    }

    override fun finished(description: Description?) {
        super.finished(description)

        notesRoleUtil.forceStopPackage(requiredNotesRoleHolderPackage)

        // Delete the secondary user - we don't care about restoring it's notes role as it was a
        // test user which we are deleting anyway.
        testUserId?.let {
            // Switch back to the original user before removing the secondary user.
            switchToUser(originalUserId)
            userHelper.removeSecondaryUser(it)
        }

        // Restore original user's notes role holder app
        notesRoleUtil.setRoleHolder(originalUserPrevNotesRoleHolder)
    }

    private fun switchToUser(userId: Int) {
        assert(
            notesRoleUtil.callWithShellIdentityPermissions(MANAGE_USERS, CREATE_USERS) {
                // Perform the user switch.
                context.getSystemService<ActivityManager>()?.switchUser(UserHandle.of(userId))
                    ?: false
            }
        ) {
            "Failed to switch to user $userId"
        }

        // There's an incredible amount of jank, etc. after switching users. Wait a long time as
        // there is no condition we can wait for to inform us that jank is over.
        Log.i(TAG, "User switching completed, sleeping for some time to avoid jank.")
        try {
            TimeUnit.SECONDS.sleep(JANK_WAIT_TIME_IN_SECONDS)
        } catch (ignored: InterruptedException) {}
        Log.i(TAG, "Resuming from sleep to verify user switch to userID $userId is completed.")

        // Verify that user switch was completed.
        assert(getCurrentUserId() == userId) { "Ensure user switch to user $userId is completed" }
    }

    private fun getCurrentUserId() =
        notesRoleUtil.callWithShellIdentityPermissions(
            INTERACT_ACROSS_USERS,
            INTERACT_ACROSS_USERS_FULL
        ) {
            ActivityManager.getCurrentUser()
        }

    private companion object {
        const val SECONDARY_USER = "secondaryUser"
        const val JANK_WAIT_TIME_IN_SECONDS = 30L

        val TAG = NotesRoleManagerSecondaryUserRule::class.java.toString()
    }
}
