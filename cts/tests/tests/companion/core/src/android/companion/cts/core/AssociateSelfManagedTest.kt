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

package android.companion.cts.core

import android.Manifest.permission.REQUEST_COMPANION_PROFILE_WATCH
import android.Manifest.permission.REQUEST_COMPANION_SELF_MANAGED
import android.companion.AssociationRequest
import android.companion.AssociationRequest.DEVICE_PROFILE_WATCH
import android.companion.cts.common.RecordingCallback
import android.companion.cts.common.RecordingCallback.OnAssociationCreated
import android.companion.cts.common.SIMPLE_EXECUTOR
import android.companion.cts.common.assertEmpty
import android.platform.test.annotations.AppModeFull
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test CDM APIs for requesting establishing new associations.
 *
 * Run: atest CtsCompanionDeviceManagerCoreTestCases:AssociateSelfManagedTest
 *
 * @see android.companion.CompanionDeviceManager.associate
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
class AssociateSelfManagedTest : CoreTestBase() {

    @Test
    fun test_associate_selfManaged_requiresPermission() {
        val request: AssociationRequest = AssociationRequest.Builder()
                .setSelfManaged(true)
                .setDisplayName(DEVICE_DISPLAY_NAME)
                .build()
        val callback = RecordingCallback()

        // Attempts to create a "self-managed" association without the MANAGE_COMPANION_DEVICES
        // permission should lead to a SecurityException being thrown.
        assertFailsWith(SecurityException::class) {
            cdm.associate(request, SIMPLE_EXECUTOR, callback)
        }
        assertEmpty(callback.invocations)

        // Same call with the MANAGE_COMPANION_DEVICES permissions should succeed.
        withShellPermissionIdentity(REQUEST_COMPANION_SELF_MANAGED) {
            cdm.associate(request, SIMPLE_EXECUTOR, callback)
        }
    }

    @Test
    fun test_associate_selfManaged_nullProfile_leadsToNoUiFlow() {
        val request: AssociationRequest = AssociationRequest.Builder()
                .setSelfManaged(true)
                .setDisplayName(DEVICE_DISPLAY_NAME)
                .build()
        val callback = RecordingCallback()

        callback.assertInvokedByActions {
            withShellPermissionIdentity(REQUEST_COMPANION_SELF_MANAGED) {
                cdm.associate(request, SIMPLE_EXECUTOR, callback)
            }
        }

        // Check callback invocations: there should have been exactly 1 invocation of the
        // onAssociationCreated() method.
        assertEquals(1, callback.invocations.size)
        val associationInvocation = callback.invocations.first()
        assertIs<OnAssociationCreated>(associationInvocation)
        with(associationInvocation.associationInfo) {
            assertEquals(actual = displayName, expected = DEVICE_DISPLAY_NAME)
            assertNull(deviceProfile)
            assertTrue(isSelfManaged)
        }

        // Check that the newly created association is included in
        // CompanionDeviceManager.getMyAssociations()
        assertContentEquals(
            actual = cdm.myAssociations,
            expected = listOf(associationInvocation.associationInfo)
        )
    }

    @Test
    fun test_associate_selfManaged_alreadyRoleHolder_leadsToNoUiFlow() =
            targetApp.withRole(ROLE_WATCH) {
                val request: AssociationRequest = AssociationRequest.Builder()
                        .setSelfManaged(true)
                        .setDeviceProfile(DEVICE_PROFILE_WATCH)
                        .setDisplayName(DEVICE_DISPLAY_NAME)
                        .build()
                val callback = RecordingCallback()

                callback.assertInvokedByActions {
                    withShellPermissionIdentity(
                        REQUEST_COMPANION_SELF_MANAGED,
                        REQUEST_COMPANION_PROFILE_WATCH
                    ) {
                        cdm.associate(request, SIMPLE_EXECUTOR, callback)
                    }
                }

                // Check callback invocations: there should have been exactly 1 invocation of the
                // onAssociationCreated().
                assertEquals(1, callback.invocations.size)
                val associationInvocation = callback.invocations.first()
                assertIs<OnAssociationCreated>(associationInvocation)
                with(associationInvocation.associationInfo) {
                    assertEquals(actual = displayName, expected = DEVICE_DISPLAY_NAME)
                    assertEquals(actual = deviceProfile, expected = ROLE_WATCH)
                    assertTrue(isSelfManaged)
                }

                // Check that the newly created association is included in
                // CompanionDeviceManager.getMyAssociations()
                assertContentEquals(
                    actual = cdm.myAssociations,
                    expected = listOf(associationInvocation.associationInfo)
                )
            }

    override fun tearDown() {
        targetApp.removeFromHoldersOfRole(ROLE_WATCH)
        super.tearDown()
    }

    companion object {
        private const val DEVICE_DISPLAY_NAME = "My device"
        private const val ROLE_WATCH = DEVICE_PROFILE_WATCH
    }
}
