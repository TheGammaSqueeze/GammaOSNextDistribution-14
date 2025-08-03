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

package android.companion.cts.core

import android.Manifest
import android.companion.AssociationInfo
import android.companion.CompanionDeviceManager.MESSAGE_REQUEST_PING
import android.companion.cts.common.MAC_ADDRESS_A
import android.companion.cts.common.SIMPLE_EXECUTOR
import android.platform.test.annotations.AppModeFull
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.function.BiConsumer
import java.util.function.Consumer
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test System APIs for using CDM transports.
 *
 * Run: atest CtsCompanionDeviceManagerCoreTestCases:TransportPermissionTest
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
class TransportPermissionTest : CoreTestBase() {

    @Test
    fun test_addOnTransportChangedListener_requiresPermission() {
        // Create a regular (not self-managed) association.
        targetApp.associate(MAC_ADDRESS_A)
        val listener = Consumer<List<AssociationInfo>> { _: List<AssociationInfo> -> }

        // Attempts to call addOnTransportChangedListener without the
        // USE_COMPANION_TRANSPORTS permission should lead to a SecurityException
        // being thrown.
        assertFailsWith(SecurityException::class) {
            cdm.addOnTransportsChangedListener(SIMPLE_EXECUTOR, listener)
        }

        // Same call with the USE_COMPANION_TRANSPORTS permissions should succeed.
        withShellPermissionIdentity(Manifest.permission.USE_COMPANION_TRANSPORTS) {
            cdm.addOnTransportsChangedListener(SIMPLE_EXECUTOR, listener)
        }

        // Attempts to call removeOnTransportChangedListener without the
        // USE_COMPANION_TRANSPORTS permission should lead to a SecurityException
        // being thrown.
        assertFailsWith(SecurityException::class) {
            cdm.removeOnTransportsChangedListener(listener)
        }

        // Same call with the USE_COMPANION_TRANSPORTS permissions should succeed.
        withShellPermissionIdentity(Manifest.permission.USE_COMPANION_TRANSPORTS) {
            cdm.removeOnTransportsChangedListener(listener)
        }
    }

    @Test
    fun test_addOnMessageReceivedListener_requiresPermission() {
        // Create a regular (not self-managed) association.
        targetApp.associate(MAC_ADDRESS_A)
        val listener = BiConsumer<Int, ByteArray> { _: Int, _: ByteArray -> }

        // Attempts to call addOnMessageReceivedListener without the
        // USE_COMPANION_TRANSPORTS permission should lead to a SecurityException
        // being thrown.
        assertFailsWith(SecurityException::class) {
            cdm.addOnMessageReceivedListener(SIMPLE_EXECUTOR, MESSAGE_REQUEST_PING, listener)
        }

        // Same call with the USE_COMPANION_TRANSPORTS permissions should succeed.
        withShellPermissionIdentity(Manifest.permission.USE_COMPANION_TRANSPORTS) {
            cdm.addOnMessageReceivedListener(SIMPLE_EXECUTOR, MESSAGE_REQUEST_PING, listener)
        }

        // Attempts to call removeOnMessageReceivedListener without the
        // USE_COMPANION_TRANSPORTS permission should lead to a SecurityException
        // being thrown.
        assertFailsWith(SecurityException::class) {
            cdm.removeOnMessageReceivedListener(MESSAGE_REQUEST_PING, listener)
        }

        // Same call with the USE_COMPANION_TRANSPORTS permissions should succeed.
        withShellPermissionIdentity(Manifest.permission.USE_COMPANION_TRANSPORTS) {
            cdm.removeOnMessageReceivedListener(MESSAGE_REQUEST_PING, listener)
        }
    }

    @Test
    fun test_sendMessage_requiresPermission() {
        // Create a regular (not self-managed) association.
        targetApp.associate(MAC_ADDRESS_A)
        val associationId = cdm.myAssociations[0].id

        // Attempts to call sendMessage without the
        // USE_COMPANION_TRANSPORTS  permission should lead to a SecurityException
        // being thrown.
        assertFailsWith(SecurityException::class) {
            cdm.sendMessage(MESSAGE_REQUEST_PING, byteArrayOf(), intArrayOf(associationId))
        }

        // Same call with the USE_COMPANION_TRANSPORTS permissions should succeed.
        withShellPermissionIdentity(Manifest.permission.USE_COMPANION_TRANSPORTS) {
            cdm.sendMessage(MESSAGE_REQUEST_PING, byteArrayOf(), intArrayOf(associationId))
        }
    }
}
