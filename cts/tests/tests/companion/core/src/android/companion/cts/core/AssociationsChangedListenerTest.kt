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

import android.Manifest.permission.MANAGE_COMPANION_DEVICES
import android.Manifest.permission.REQUEST_COMPANION_SELF_MANAGED
import android.companion.AssociationRequest
import android.companion.cts.common.BACKGROUND_THREAD_EXECUTOR
import android.companion.cts.common.DEVICE_DISPLAY_NAME_A
import android.companion.cts.common.MAC_ADDRESS_A
import android.companion.cts.common.RecordingCallback.OnAssociationCreated
import android.companion.cts.common.RecordingCdmEventObserver
import android.companion.cts.common.RecordingCdmEventObserver.AssociationChange
import android.companion.cts.common.RecordingCdmEventObserver.CdmCallback
import android.companion.cts.common.RecordingOnAssociationsChangedListener
import android.companion.cts.common.Repeat
import android.companion.cts.common.RepeatRule
import android.companion.cts.common.SIMPLE_EXECUTOR
import android.companion.cts.common.assertEmpty
import android.platform.test.annotations.AppModeFull
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * Test CDM APIs for listening for changes to [android.companion.AssociationInfo].
 *
 * Run: atest CtsCompanionDeviceManagerCoreTestCases:AssociationsChangedListenerTest
 *
 * @see android.companion.CompanionDeviceManager.OnAssociationsChangedListener
 * @see android.companion.CompanionDeviceManager.addOnAssociationsChangedListener
 * @see android.companion.CompanionDeviceManager.removeOnAssociationsChangedListener
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
class AssociationsChangedListenerTest : CoreTestBase() {
    @get:Rule
    val repeatRule = RepeatRule()

    @Test
    fun test_addOnAssociationsChangedListener_requiresPermission() {
        /**
         * Attempts to add a listener without [MANAGE_COMPANION_DEVICES] permission should
         * throw a [SecurityException] and should not change the existing associations.
         */
        assertFailsWith(SecurityException::class) {
            cdm.addOnAssociationsChangedListener(SIMPLE_EXECUTOR, NO_OP_LISTENER)
        }

        /** Re-running with [MANAGE_COMPANION_DEVICES] permissions: now should succeed */
        withShellPermissionIdentity(MANAGE_COMPANION_DEVICES) {
            cdm.addOnAssociationsChangedListener(SIMPLE_EXECUTOR, NO_OP_LISTENER)

            /** Succeeded, now remove. */
            cdm.removeOnAssociationsChangedListener(NO_OP_LISTENER)
        }
    }

    @Test
    fun test_addOnAssociationsChangedListener() {
        val listener = RecordingOnAssociationsChangedListener()

        withShellPermissionIdentity(MANAGE_COMPANION_DEVICES) {
            cdm.addOnAssociationsChangedListener(SIMPLE_EXECUTOR, listener)
        }

        listener.assertInvokedByActions {
            testApp.associate(MAC_ADDRESS_A)
        }

        listener.invocations[0].let { associations ->
            assertEquals(actual = associations.size, expected = 1)
            assertEquals(actual = associations[0].deviceMacAddress, expected = MAC_ADDRESS_A)
            assertEquals(actual = associations[0].packageName, expected = TEST_APP_PACKAGE_NAME)
        }

        listener.clearRecordedInvocations()

        withShellPermissionIdentity(MANAGE_COMPANION_DEVICES) {
            cdm.removeOnAssociationsChangedListener(listener)
        }

        testApp.disassociate(MAC_ADDRESS_A)
        // The listener shouldn't get involved after removed the onAssociationsChangedListener.
        assertEmpty(listener.invocations)
    }

    @Test
    @Repeat(10)
    fun test_associationChangeListener_notifiedBefore_cdmCallback() {
        val request: AssociationRequest = AssociationRequest.Builder()
            .setSelfManaged(true)
            .setDisplayName(DEVICE_DISPLAY_NAME_A)
            .build()

        val observer = RecordingCdmEventObserver()

        // preparation: register the observer as an association change listener
        withShellPermissionIdentity(MANAGE_COMPANION_DEVICES) {
            cdm.addOnAssociationsChangedListener(BACKGROUND_THREAD_EXECUTOR, observer)
        }

        // test scenario: carry out an association and assert that
        // the association listener is notified BEFORE the CDM observer
        observer.assertInvokedByActions(minOccurrences = 2) {
            withShellPermissionIdentity(REQUEST_COMPANION_SELF_MANAGED) {
                // in order to make sure the OnAssociationsChangedListener and
                // CompanionDeviceManager.Callback callbacks are recorded in the right order use
                // the same Executor - BACKGROUND_THREAD_EXECUTOR - here that we used for
                // addOnAssociationsChangedListener above.
                cdm.associate(request, BACKGROUND_THREAD_EXECUTOR, observer)
            }
        }

        // we should have observed exactly two events
        assertEquals(2, observer.invocations.size)
        val (event1, event2) = observer.invocations

        // the event we observed first should be an association change
        assertIs<AssociationChange>(event1)
        // there should be exactly one association
        assertEquals(1, event1.associations.size)
        val associationInfoFromListener = event1.associations.first()
        assertEquals(
            actual = associationInfoFromListener.displayName,
            expected = DEVICE_DISPLAY_NAME_A
        )

        // the second event should be the callback invocation
        assertIs<CdmCallback>(event2)
        val callbackInvocation = event2.invocation
        assertIs<OnAssociationCreated>(callbackInvocation)

        val associationInfoFromCallback = callbackInvocation.associationInfo
        assertEquals(associationInfoFromListener, associationInfoFromCallback)
    }
}