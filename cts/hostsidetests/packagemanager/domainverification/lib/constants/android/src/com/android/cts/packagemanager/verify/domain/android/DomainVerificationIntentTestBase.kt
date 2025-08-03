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

package com.android.cts.packagemanager.verify.domain.android

import android.app.Instrumentation
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.verify.domain.DomainVerificationManager
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.ShellUtils
import com.android.cts.packagemanager.verify.domain.android.DomainUtils.DECLARING_PKG_1_COMPONENT
import com.android.cts.packagemanager.verify.domain.android.DomainUtils.DECLARING_PKG_2_COMPONENT
import com.android.cts.packagemanager.verify.domain.java.DomainUtils
import com.android.cts.packagemanager.verify.domain.java.DomainUtils.DECLARING_PKG_NAME_1
import com.android.cts.packagemanager.verify.domain.java.DomainUtils.DECLARING_PKG_NAME_2
import com.android.cts.packagemanager.verify.domain.java.DomainUtils.DOMAIN_UNHANDLED
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
abstract class DomainVerificationIntentTestBase(
    private val domain: String,
    private val assertResolvesToBrowsersInBefore: Boolean = true,
    private val resetEnable: Boolean = false,
) {

    companion object {

        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun parameters() = IntentVariant.values()
    }

    @Parameterized.Parameter(0)
    lateinit var intentVariant: IntentVariant

    protected val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    protected val context = instrumentation.targetContext
    protected val packageManager = context.packageManager
    protected val userId = context.userId
    protected val manager = context.getSystemService(DomainVerificationManager::class.java)!!

    protected lateinit var browsers: List<ComponentName>
    protected lateinit var allResults: List<ComponentName>

    @Before
    fun findBrowsers() {
        SharedVerifications.reset(context, resetEnable)

        browsers = Intent(Intent.ACTION_VIEW, Uri.parse("https://$DOMAIN_UNHANDLED"))
            .applyIntentVariant(intentVariant)
            .let { context.packageManager.queryIntentActivities(it, 0) }
            .map { it.activityInfo }
            .map { ComponentName(it.packageName, it.name) }
            .also { assumeTrue(it.isNotEmpty()) }

        val allResults = browsers.toMutableList()
        try {
            packageManager.getPackageInfo(DECLARING_PKG_NAME_1, 0)
            allResults += DECLARING_PKG_1_COMPONENT
        } catch (ignored: PackageManager.NameNotFoundException) {
        }
        try {
            packageManager.getPackageInfo(DECLARING_PKG_NAME_2, 0)
            allResults += DECLARING_PKG_2_COMPONENT
        } catch (ignored: PackageManager.NameNotFoundException) {
        }

        this.allResults = allResults

        if (assertResolvesToBrowsersInBefore) {
            assertResolvesTo(browsers)
        }
    }

    @After
    fun reset() {
        SharedVerifications.reset(context, resetEnable)
    }

    protected fun runShellCommand(vararg commands: String) = commands.forEach {
        assertThat(ShellUtils.runShellCommand(it)).isEmpty()
    }

    protected fun assertResolvesTo(result: ComponentName, domain: String = this.domain) =
        assertResolvesTo(listOf(result), domain)

    protected fun assertResolvesTo(
        components: Collection<ComponentName>,
        domain: String = this.domain,
    ) {
        val message = ShellUtils.runShellCommand(
            "pm get-app-links --user ${context.userId} $DECLARING_PKG_NAME_1"
        ) + "\n" + ShellUtils.runShellCommand(
            "pm get-app-links --user ${context.userId} $DECLARING_PKG_NAME_2"
        )

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://$domain"))
            .addFlags(Intent.FLAG_DEBUG_LOG_RESOLUTION)
            .applyIntentVariant(intentVariant)

        // Pass MATCH_DEFAULT_ONLY to mirror startActivity resolution
        assertWithMessage(message)
            .that(packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                .map { it.activityInfo }
                .map { ComponentName(it.packageName, it.name) })
            .containsExactlyElementsIn(components)

        if (intent.hasCategory(Intent.CATEGORY_DEFAULT)) {
            // Verify explicit DEFAULT mirrors MATCH_DEFAULT_ONLY
            assertWithMessage(message)
                .that(packageManager.queryIntentActivities(intent, 0)
                .map { it.activityInfo }
                .map { ComponentName(it.packageName, it.name) })
                .containsExactlyElementsIn(components)
        } else {
            val expected = allResults.filter {
                browsers.contains(it) || (isComponentEnabled(
                    packageManager.getApplicationEnabledSetting(
                        it.packageName
                    )
                ) && isComponentEnabled(packageManager.getComponentEnabledSetting(it)))
            }

            // Verify that non-DEFAULT match returns all results
            assertWithMessage(message)
                .that(packageManager.queryIntentActivities(intent, 0)
                    .map { it.activityInfo }
                    .map { ComponentName(it.packageName, it.name) })
                .containsExactlyElementsIn(expected)
        }
    }

    private fun isComponentEnabled(enabledSetting: Int) = when (enabledSetting) {
        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true

        else -> false
    }

    fun resetAppLinks(packageName: String) {
        runShellCommand(DomainUtils.resetAppLinks(packageName))
    }

    fun setAppLinks(packageName: String, enabled: Boolean, vararg domains: String) {
        val state = "STATE_APPROVED".takeIf { enabled } ?: "STATE_DENIED"
        runShellCommand(DomainUtils.setAppLinks(packageName, state, *domains))
    }

    fun setAppLinksAllowed(packageName: String, userId: Int, enabled: Boolean) {
        runShellCommand(DomainUtils.setAppLinksAllowed(packageName, userId, enabled))
    }

    fun setAppLinksUserSelection(
        packageName: String,
        userId: Int,
        enabled: Boolean,
        vararg domains: String
    ) {
        runShellCommand(
            DomainUtils.setAppLinksUserSelection(packageName, userId, enabled, *domains)
        )
    }
}
