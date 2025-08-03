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

package android.companion.cts.core

import android.companion.cts.common.DEVICE_DISPLAY_NAME_A
import android.companion.cts.common.DEVICE_DISPLAY_NAME_B
import android.companion.cts.common.assertOnlyPrimaryCompanionDeviceServiceNotified
import android.companion.cts.common.assertValidCompanionDeviceServicesUnbind
import android.platform.test.annotations.AppModeFull
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compatibility.common.util.SystemUtil
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Test CDM system dump.
 *
 * Run: atest CtsCompanionDeviceManagerCoreTestCases:DumpSysTest
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
class DumpSysTest : CoreTestBase() {

    @Test
    fun test_dump_noAssociation() {
        // Dump without creating any association.
        val out = dumpCurrentState()
        assertTrue(out[0].contains("Companion Device Associations: <empty>"))
        assertTrue(out[1].contains("Companion Device Present: <empty>"))
        assertFalse(out[2].contains("u/$userId"))
    }

    @Test
    fun test_dump_singleDevice() {
        // Create a self-managed association.
        val associationId = createSelfManagedAssociation(DEVICE_DISPLAY_NAME_A)

        var out = dumpCurrentState()
        assertTrue(out[0].contains("mId=$associationId")) // Device is associated
        assertFalse(out[1].contains("id=$associationId")) // But not present yet
        assertFalse(out[2].contains("u$userId\\$targetPackageName")) // App is not bound yet

        // Publish device's presence and wait for callback.
        cdm.notifyDeviceAppeared(associationId)
        assertOnlyPrimaryCompanionDeviceServiceNotified(associationId, appeared = true)

        out = dumpCurrentState()
        assertTrue(out[0].contains("mId=$associationId")) // Device is still associated
        assertTrue(out[1].contains("id=$associationId")) // And also present
        assertTrue(out[2].contains("u$userId\\$targetPackageName")) // App is now bound

        // Clean up
        cdm.notifyDeviceDisappeared(associationId)
        assertValidCompanionDeviceServicesUnbind()
    }

    @Test
    fun test_dump_multiDevice() {
        // Associate with multiple devices.
        val idA = createSelfManagedAssociation(DEVICE_DISPLAY_NAME_A)
        val idB = createSelfManagedAssociation(DEVICE_DISPLAY_NAME_B)

        var out = dumpCurrentState()
        assertTrue(out[0].contains("mId=$idA")) // Device A associated
        assertTrue(out[0].contains("mId=$idB")) // Device B associated
        assertFalse(out[1].contains("id=$idA")) // Device A not present
        assertFalse(out[1].contains("id=$idB")) // Device B not present
        assertFalse(out[2].contains("u$userId\\$targetPackageName")) // App is not bound yet

        // Only publish device A's presence and wait for callback.
        cdm.notifyDeviceAppeared(idA)
        assertOnlyPrimaryCompanionDeviceServiceNotified(idA, appeared = true)

        out = dumpCurrentState()
        assertTrue(out[1].contains("id=$idA")) // Device A is now present
        assertFalse(out[1].contains("id=$idB")) // Device B still not present
        assertTrue(out[2].contains("u$userId\\$targetPackageName")) // App is now bound

        // Clean up
        cdm.notifyDeviceDisappeared(idA)
        assertValidCompanionDeviceServicesUnbind()
    }

    /**
     * Uses adb shell command to dump current state of CDM and splits output into its components.
     */
    private fun dumpCurrentState(): Array<String> {
        val dump = SystemUtil.runShellCommand("dumpsys companiondevice")

        val headerIndex0 = dump.indexOf("Companion Device Associations:")
        assertTrue(headerIndex0 >= 0)

        val headerIndex1 = dump.indexOf("Companion Device Present:")
        assertTrue(headerIndex1 >= 0)

        val headerIndex2 = dump.indexOf("Companion Device Application Controller:")
        assertTrue(headerIndex2 >= 0)

        return arrayOf(
                dump.substring(headerIndex0, headerIndex1),
                dump.substring(headerIndex1, headerIndex2),
                dump.substring(headerIndex2)
        )
    }
}