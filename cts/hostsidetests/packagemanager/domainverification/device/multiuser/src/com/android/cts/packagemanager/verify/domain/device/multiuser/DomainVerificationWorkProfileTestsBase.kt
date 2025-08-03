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

package com.android.cts.packagemanager.verify.domain.device.multiuser

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.UserType
import com.android.bedstead.harrier.annotations.AfterClass
import com.android.bedstead.harrier.annotations.BeforeClass
import com.android.bedstead.harrier.annotations.EnsureHasWorkProfile
import com.android.bedstead.harrier.annotations.Postsubmit
import com.android.bedstead.harrier.annotations.RequireRunOnInitialUser
import com.android.bedstead.harrier.annotations.RequireRunOnWorkProfile
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.packages.Packages
import com.android.bedstead.nene.users.UserReference
import com.android.bedstead.nene.utils.ShellCommand
import com.android.compatibility.common.util.ShellUtils
import com.android.cts.packagemanager.verify.domain.android.DomainUtils.DECLARING_PKG_1_COMPONENT
import com.android.cts.packagemanager.verify.domain.android.DomainUtils.DECLARING_PKG_2_COMPONENT
import com.android.cts.packagemanager.verify.domain.android.SharedVerifications
import com.android.cts.packagemanager.verify.domain.java.DomainUtils
import com.android.cts.packagemanager.verify.domain.java.DomainUtils.DECLARING_PKG_APK_1
import com.android.cts.packagemanager.verify.domain.java.DomainUtils.DECLARING_PKG_APK_2
import com.android.cts.packagemanager.verify.domain.java.DomainUtils.DECLARING_PKG_NAME_1
import com.android.cts.packagemanager.verify.domain.java.DomainUtils.DECLARING_PKG_NAME_2
import com.android.cts.packagemanager.verify.domain.java.DomainUtils.DOMAIN_1
import com.android.cts.packagemanager.verify.domain.java.DomainUtils.DOMAIN_UNHANDLED
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@EnsureHasWorkProfile(forUser = UserType.INITIAL_USER)
@RunWith(BedsteadJUnit4::class)
abstract class DomainVerificationWorkProfileTestsBase {

    companion object {

        @JvmField
        @ClassRule
        @Rule
        val deviceState = DeviceState()

        private val TARGET_INTENT = Intent(Intent.ACTION_VIEW, Uri.parse("https://$DOMAIN_1"))
        private val BROWSER_INTENT =
            Intent(Intent.ACTION_VIEW, Uri.parse("https://$DOMAIN_UNHANDLED"))

        @JvmStatic
        protected val FORWARD_TO_PARENT =
            ComponentName("android", "com.android.internal.app.ForwardIntentToParent")

        @JvmStatic
        protected val FORWARD_TO_MANAGED =
            ComponentName("android", "com.android.internal.app.ForwardIntentToManagedProfile")

        @JvmStatic
        protected val PERSONAL_APP = DECLARING_PKG_NAME_1

        @JvmStatic
        protected val WORK_APP = DECLARING_PKG_NAME_2

        @JvmStatic
        protected val PERSONAL_COMPONENT = DECLARING_PKG_1_COMPONENT

        @JvmStatic
        protected val WORK_COMPONENT = DECLARING_PKG_2_COMPONENT

        @JvmStatic
        protected lateinit var personalBrowsers: Collection<ComponentName>

        @JvmStatic
        protected lateinit var workBrowsers: Collection<ComponentName>

        private lateinit var personalUser: UserReference
        private lateinit var workUser: UserReference

        @JvmStatic
        @BeforeClass
        fun installApks() {
            personalUser = deviceState.initialUser()
            workUser = deviceState.workProfile(UserType.INITIAL_USER)
            personalBrowsers = collectBrowsers(personalUser)
            workBrowsers = collectBrowsers(workUser)
            TestApis.packages().run {
                install(personalUser, Packages.JavaResource.javaResource(DECLARING_PKG_APK_1.value))
                install(workUser, Packages.JavaResource.javaResource(DECLARING_PKG_APK_2.value))
            }
        }

        @JvmStatic
        @AfterClass
        fun uninstallApks() {
            TestApis.packages().run {
                find(PERSONAL_APP).uninstallFromAllUsers()
                find(WORK_APP).uninstallFromAllUsers()
            }
        }

        private fun collectBrowsers(user: UserReference) =
            withUserContext(user) { context ->
                context.packageManager
                    .queryIntentActivities(BROWSER_INTENT, PackageManager.MATCH_DEFAULT_ONLY)
                    .map { it.activityInfo }
                    .map { ComponentName(it.packageName, it.name) }
                    .also { assumeTrue(it.isNotEmpty()) }
            }

        @JvmStatic
        protected fun assertResolvesTo(vararg components: ComponentName) =
            assertResolvesTo(components.toList())

        @JvmStatic
        protected fun assertResolvesTo(components: Collection<ComponentName>) {
            val results = TestApis.context()
                .instrumentedContext()
                .packageManager
                .queryIntentActivities(TARGET_INTENT, PackageManager.MATCH_DEFAULT_ONLY)
                .map { it.activityInfo }
                .map { ComponentName(it.packageName, it.name) }
            assertThat(results).containsExactlyElementsIn(components)
        }

        @JvmStatic
        protected fun verify(vararg packageNames: String) = packageNames.forEach {
            assertWithMessage("pm set-app-links should be empty on success").that(
                ShellUtils.runShellCommand(DomainUtils.setAppLinks(it, "STATE_APPROVED", DOMAIN_1))
            ).isEmpty()
        }
    }

    @Before
    @After
    fun resetState() {
        listOf(personalUser, workUser).forEach {
            withUserContext(it) {
                SharedVerifications.reset(it, resetEnable = true)
            }
        }
    }

    @RequireRunOnInitialUser
    @Postsubmit(reason = "New test")
    @Test
    fun inPersonal_unverified() {
        assertResolvesTo(personalBrowsers)
    }

    @RequireRunOnInitialUser
    @Postsubmit(reason = "New test")
    @Test
    fun inPersonal_verifiedInCurrentProfile() {
        verify(PERSONAL_APP)

        assertResolvesTo(PERSONAL_COMPONENT)
    }

    // The assertion for this method varies based on general versus specific cross profile config
    abstract fun inPersonal_verifiedInOtherProfile()

    @RequireRunOnInitialUser
    @Postsubmit(reason = "New test")
    @Test
    fun inPersonal_verifiedInBothProfiles() {
        verify(PERSONAL_APP, WORK_APP)

        assertResolvesTo(PERSONAL_COMPONENT)
    }

    @RequireRunOnWorkProfile
    @Postsubmit(reason = "New test")
    @Test
    fun inWork_unverified() {
        assertResolvesTo(workBrowsers)
    }

    @RequireRunOnWorkProfile
    @Postsubmit(reason = "New test")
    @Test
    fun inWork_verifiedInCurrentProfile() {
        verify(WORK_APP)

        assertResolvesTo(WORK_COMPONENT)
    }

    @RequireRunOnWorkProfile
    @Postsubmit(reason = "New test")
    @Test
    fun inWork_verifiedInOtherProfile() {
        verify(PERSONAL_APP)

        assertResolvesTo(workBrowsers + FORWARD_TO_PARENT)
    }

    @RequireRunOnWorkProfile
    @Postsubmit(reason = "New test")
    @Test
    fun inWork_verifiedInOtherProfileDisabledApp() {
        verify(PERSONAL_APP)
        disableApp(personalUser, PERSONAL_APP)

        assertResolvesTo(workBrowsers)
    }

    @RequireRunOnWorkProfile
    @Postsubmit(reason = "New test")
    @Test
    fun inWork_verifiedInOtherProfileDisabledComponent() {
        verify(PERSONAL_APP)
        disableComponent(personalUser, PERSONAL_COMPONENT)

        assertResolvesTo(workBrowsers)
    }

    @RequireRunOnWorkProfile
    @Postsubmit(reason = "New test")
    @Test
    fun inWork_verifiedInBothProfiles() {
        verify(PERSONAL_APP, WORK_APP)

        assertResolvesTo(WORK_COMPONENT)
    }

    @RequireRunOnWorkProfile
    @Postsubmit(reason = "New test")
    @Test
    fun inWork_verifiedInBothProfilesDisabledAppInOther() {
        verify(PERSONAL_APP, WORK_APP)
        disableApp(personalUser, PERSONAL_APP)

        assertResolvesTo(WORK_COMPONENT)
    }

    @RequireRunOnWorkProfile
    @Postsubmit(reason = "New test")
    @Test
    fun inWork_verifiedInBothProfilesDisabledComponentInOther() {
        verify(PERSONAL_APP, WORK_APP)
        disableComponent(personalUser, PERSONAL_COMPONENT)

        assertResolvesTo(WORK_COMPONENT)
    }

    @RequireRunOnWorkProfile
    @Postsubmit(reason = "New test")
    @Test
    fun inWork_verifiedInBothProfilesDisabledAppInCurrent() {
        verify(PERSONAL_APP, WORK_APP)
        disableApp(workUser, WORK_APP)

        assertResolvesTo(workBrowsers + FORWARD_TO_PARENT)
    }

    @RequireRunOnWorkProfile
    @Postsubmit(reason = "New test")
    @Test
    fun inWork_verifiedInBothProfilesDisabledComponentInCurrent() {
        verify(PERSONAL_APP, WORK_APP)
        disableComponent(workUser, WORK_COMPONENT)

        assertResolvesTo(workBrowsers + FORWARD_TO_PARENT)
    }

    @RequireRunOnWorkProfile
    @Postsubmit(reason = "New test")
    @Test
    fun inWork_verifiedInBothProfilesDisabledAppInBoth() {
        verify(PERSONAL_APP, WORK_APP)
        disableApp(personalUser, PERSONAL_APP)
        disableApp(workUser, WORK_APP)

        assertResolvesTo(workBrowsers)
    }

    @RequireRunOnWorkProfile
    @Postsubmit(reason = "New test")
    @Test
    fun inWork_verifiedInBothProfilesDisabledComponentInBoth() {
        verify(PERSONAL_APP, WORK_APP)
        disableComponent(personalUser, PERSONAL_COMPONENT)
        disableComponent(workUser, WORK_COMPONENT)

        assertResolvesTo(workBrowsers)
    }

    private fun disableApp(user: UserReference, packageName: String) {
        ShellCommand.builderForUser(user, "pm disable-user")
            .addOperand(packageName)
            .validate { it.trim().endsWith("new state: disabled-user") }
            .execute()
    }

    private fun disableComponent(user: UserReference, component: ComponentName) {
        ShellCommand.builderForUser(user, "pm disable")
            .addOperand(component.flattenToString())
            .validate { it.trim().endsWith("new state: disabled") }
            .execute()
    }
}
