/*
 * Copyright 2024 The Android Open Source Project
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

package android.packageinstaller.install.cts

import android.app.UiAutomation
import android.content.ComponentName
import android.content.pm.Flags
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.function.ThrowingRunnable
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@AppModeFull(reason = "Instant apps do not need pre-verified domains")
class InstallPreVerifiedDomainsTest : PackageInstallerTestBase() {
    private val testDomains = setOf("com.foo", "com.bar")
    private val uiAutomation: UiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation()

    @JvmField
    @Rule
    val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @RequiresFlagsEnabled(Flags.FLAG_SET_PRE_VERIFIED_DOMAINS)
    @Test(expected = IllegalArgumentException::class)
    fun testSetPreVerifiedDomainsWithEmpty() {
        createSessionWithPreVerifiedDomains(setOf())
    }

    @RequiresFlagsEnabled(Flags.FLAG_SET_PRE_VERIFIED_DOMAINS)
    @Test
    fun testSetPreVerifiedDomainsWithoutPermission() {
        assertThrows(
                "You need android.permission.ACCESS_INSTANT_APPS " +
                        "permission to set pre-verified domains.",
                SecurityException::class.java,
                ThrowingRunnable {
                    createSessionWithPreVerifiedDomains(testDomains)
                }
        )
    }

    @RequiresFlagsEnabled(Flags.FLAG_SET_PRE_VERIFIED_DOMAINS)
    @Test
    fun testSetPreVerifiedDomainsNotInstantAppInstaller() {
        val defaultInstantAppInstaller: ComponentName? = pm.getInstantAppInstallerComponent()
        uiAutomation.adoptShellPermissionIdentity(android.Manifest.permission.ACCESS_INSTANT_APPS)
        try {
            if (defaultInstantAppInstaller != null) {
                assertThrows(
                        "Only the instant app installer can call this API.",
                        SecurityException::class.java,
                        ThrowingRunnable {
                            createSessionWithPreVerifiedDomains(testDomains)
                        }
                )
            } else {
                assertThrows(
                        "Instant app installer is not available. " +
                                "Only the instant app installer can call this API.",
                        IllegalStateException::class.java,
                        ThrowingRunnable {
                            createSessionWithPreVerifiedDomains(testDomains)
                        }
                )
            }
        } finally {
            uiAutomation.dropShellPermissionIdentity()
        }
    }

    private fun createSessionWithPreVerifiedDomains(domains: Set<String>): Int {
        val (sessionId, session) = createSession(0, false, null)
        try {
            session.setPreVerifiedDomains(domains)
        } catch (e: Exception) {
            session.abandon()
            throw e
        }
        return sessionId
    }
}
