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

package com.android.net.module.util

import android.Manifest.permission.NETWORK_STACK
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.net.module.util.PermissionUtils.enforceAnyPermissionOf
import com.android.net.module.util.PermissionUtils.enforceNetworkStackPermission
import com.android.net.module.util.PermissionUtils.enforceNetworkStackPermissionOr
import com.android.net.module.util.PermissionUtils.enforcePackageNameMatchesUid
import com.android.net.module.util.PermissionUtils.enforceSystemFeature
import com.android.net.module.util.PermissionUtils.hasAnyPermissionOf
import com.android.testutils.DevSdkIgnoreRule
import com.android.testutils.DevSdkIgnoreRunner
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock

/** Tests for PermissionUtils */
@RunWith(DevSdkIgnoreRunner::class)
@SmallTest
class PermissionUtilsTest {
    @get:Rule
    val ignoreRule = DevSdkIgnoreRule()
    private val TEST_PERMISSION1 = "android.permission.TEST_PERMISSION1"
    private val TEST_PERMISSION2 = "android.permission.TEST_PERMISSION2"
    private val TEST_UID1 = 1234
    private val TEST_UID2 = 1235
    private val TEST_PACKAGE_NAME = "test.package"
    private val mockContext = mock(Context::class.java)
    private val mockPackageManager = mock(PackageManager::class.java)

    private val context by lazy { InstrumentationRegistry.getInstrumentation().context }

    @Before
    fun setup() {
        doReturn(mockPackageManager).`when`(mockContext).packageManager
        doReturn(mockContext).`when`(mockContext).createContextAsUser(any(), anyInt())
    }

    @Test
    fun testEnforceAnyPermissionOf() {
        doReturn(PERMISSION_GRANTED).`when`(mockContext)
            .checkCallingOrSelfPermission(TEST_PERMISSION1)
        doReturn(PERMISSION_DENIED).`when`(mockContext)
            .checkCallingOrSelfPermission(TEST_PERMISSION2)
        assertTrue(hasAnyPermissionOf(mockContext, TEST_PERMISSION1, TEST_PERMISSION2))
        enforceAnyPermissionOf(mockContext, TEST_PERMISSION1, TEST_PERMISSION2)

        doReturn(PERMISSION_DENIED).`when`(mockContext)
            .checkCallingOrSelfPermission(TEST_PERMISSION1)
        doReturn(PERMISSION_GRANTED).`when`(mockContext)
            .checkCallingOrSelfPermission(TEST_PERMISSION2)
        assertTrue(hasAnyPermissionOf(mockContext, TEST_PERMISSION1, TEST_PERMISSION2))
        enforceAnyPermissionOf(mockContext, TEST_PERMISSION1, TEST_PERMISSION2)

        doReturn(PERMISSION_DENIED).`when`(mockContext).checkCallingOrSelfPermission(any())
        assertFalse(hasAnyPermissionOf(mockContext, TEST_PERMISSION1, TEST_PERMISSION2))
        assertFailsWith<SecurityException>("Expect fail but permission granted.") {
            enforceAnyPermissionOf(mockContext, TEST_PERMISSION1, TEST_PERMISSION2)
        }
    }

    @Test
    fun testEnforceNetworkStackPermissionOr() {
        doReturn(PERMISSION_GRANTED).`when`(mockContext).checkCallingOrSelfPermission(NETWORK_STACK)
        doReturn(PERMISSION_DENIED).`when`(mockContext)
            .checkCallingOrSelfPermission(PERMISSION_MAINLINE_NETWORK_STACK)
        enforceNetworkStackPermission(mockContext)
        enforceNetworkStackPermissionOr(mockContext, TEST_PERMISSION1)

        doReturn(PERMISSION_DENIED).`when`(mockContext).checkCallingOrSelfPermission(NETWORK_STACK)
        doReturn(PERMISSION_GRANTED).`when`(mockContext)
            .checkCallingOrSelfPermission(PERMISSION_MAINLINE_NETWORK_STACK)
        enforceNetworkStackPermission(mockContext)
        enforceNetworkStackPermissionOr(mockContext, TEST_PERMISSION2)

        doReturn(PERMISSION_DENIED).`when`(mockContext).checkCallingOrSelfPermission(NETWORK_STACK)
        doReturn(PERMISSION_DENIED).`when`(mockContext)
            .checkCallingOrSelfPermission(PERMISSION_MAINLINE_NETWORK_STACK)
        doReturn(PERMISSION_GRANTED).`when`(mockContext)
            .checkCallingOrSelfPermission(TEST_PERMISSION1)
        assertFailsWith<SecurityException>("Expect fail but permission granted.") {
            enforceNetworkStackPermission(mockContext)
        }
        enforceNetworkStackPermissionOr(mockContext, TEST_PERMISSION1)

        doReturn(PERMISSION_DENIED).`when`(mockContext).checkCallingOrSelfPermission(any())
        assertFailsWith<SecurityException>("Expect fail but permission granted.") {
            enforceNetworkStackPermission(mockContext)
        }
        assertFailsWith<SecurityException>("Expect fail but permission granted.") {
            enforceNetworkStackPermissionOr(mockContext, TEST_PERMISSION2)
        }
    }

    private fun mockHasSystemFeature(featureName: String, hasFeature: Boolean) {
        doReturn(hasFeature).`when`(mockPackageManager)
            .hasSystemFeature(ArgumentMatchers.eq(featureName))
    }

    @Test
    fun testEnforceSystemFeature() {
        val systemFeature = "test.system.feature"
        val exceptionMessage = "test exception message"
        mockHasSystemFeature(featureName = systemFeature, hasFeature = false)
        val e = assertFailsWith<UnsupportedOperationException>("Should fail without feature") {
            enforceSystemFeature(mockContext, systemFeature, exceptionMessage)
        }
        assertEquals(exceptionMessage, e.message)

        mockHasSystemFeature(featureName = systemFeature, hasFeature = true)
        try {
            enforceSystemFeature(mockContext, systemFeature, "")
        } catch (e: UnsupportedOperationException) {
            Assert.fail("Exception should have not been thrown with system feature enabled")
        }
    }

    @Test
    fun testEnforcePackageNameMatchesUid() {
        // Verify name not found throws.
        doThrow(NameNotFoundException()).`when`(mockPackageManager)
            .getPackageUid(eq(TEST_PACKAGE_NAME), anyInt())
        assertFailsWith<SecurityException> {
            enforcePackageNameMatchesUid(mockContext, TEST_UID1, TEST_PACKAGE_NAME)
        }

        // Verify uid mismatch throws.
        doReturn(TEST_UID1).`when`(mockPackageManager)
            .getPackageUid(eq(TEST_PACKAGE_NAME), anyInt())
        assertFailsWith<SecurityException> {
            enforcePackageNameMatchesUid(mockContext, TEST_UID2, TEST_PACKAGE_NAME)
        }

        // Verify uid match passes.
        enforcePackageNameMatchesUid(mockContext, TEST_UID1, TEST_PACKAGE_NAME)
    }
}
