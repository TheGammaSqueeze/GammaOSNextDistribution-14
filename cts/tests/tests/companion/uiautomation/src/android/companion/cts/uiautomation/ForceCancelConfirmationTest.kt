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

package android.companion.cts.uiautomation

import android.companion.CompanionDeviceManager.REASON_CANCELED
import android.companion.cts.common.CompanionActivity
import android.companion.cts.common.RecordingCallback.OnFailure
import android.os.SystemClock
import android.platform.test.annotations.AppModeFull
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test buildAssociationCancellationIntent API.
 *
 * Build/Install/Run:
 * atest CtsCompanionDeviceManagerUiAutomationTestCases:ForceCancelConfirmationTest
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
class ForceCancelConfirmationTest : UiAutomationTestBase(null, null) {

    @Test
    fun test_cancel_confirmation() {
        sendRequestAndLaunchConfirmation(singleDevice = true)

        callback.clearRecordedInvocations()

        val pendingIntent = cdm.buildAssociationCancellationIntent()

        assertNotNull(pendingIntent)

        CompanionActivity.startIntentSender(pendingIntent)

        SystemClock.sleep(1000)

        assertContentEquals(
            actual = callback.invocations,
            expected = listOf(OnFailure(REASON_CANCELED))
        )
    }

    @Test
    fun test_cancel_confirmation_not_exist() {
        val pendingIntent = cdm.buildAssociationCancellationIntent()

        assertNotNull(pendingIntent)

        CompanionActivity.launchAndWait(context)
        CompanionActivity.startIntentSender(pendingIntent)

        assertEquals(actual = callback.invocations.size, expected = 0)
    }
}
