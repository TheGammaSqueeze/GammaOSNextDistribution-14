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

package android.app.cts

import android.app.Instrumentation
import android.app.StatusBarManager
import android.app.UiAutomation
import android.content.Context
import android.media.MediaRoute2Info
import android.net.Uri
import androidx.test.InstrumentationRegistry
import androidx.test.InstrumentationRegistry.getInstrumentation
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.android.compatibility.common.util.AdoptShellPermissionsRule
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test that the updateMediaTapToTransferReceiverDisplay method fails in all the expected ways.
 *
 * These tests are for [StatusBarManager.updateMediaTapToTransferReceiverDisplay].
 */
@RunWith(AndroidJUnit4::class)
class UpdateMediaTapToTransferReceiverDisplayTest {
    @Rule
    fun permissionsRule() = AdoptShellPermissionsRule(
        getInstrumentation().getUiAutomation(), MEDIA_PERMISSION
    )

    private lateinit var statusBarManager: StatusBarManager
    private lateinit var instrumentation: Instrumentation
    private lateinit var uiAutomation: UiAutomation
    private lateinit var uiDevice: UiDevice
    private lateinit var context: Context

    @Before
    fun setUp() {
        instrumentation = InstrumentationRegistry.getInstrumentation()
        context = instrumentation.getTargetContext()
        statusBarManager = context.getSystemService(StatusBarManager::class.java)!!
        uiAutomation = getInstrumentation().getUiAutomation()
        uiDevice = UiDevice.getInstance(instrumentation)
        uiDevice.wakeUp()
    }

    @After
    fun tearDown() {
        // Explicitly run with the permission granted since it may have been dropped in the test.
        runWithShellPermissionIdentity {
            // Clear any existing chip
            statusBarManager.updateMediaTapToTransferReceiverDisplay(
                StatusBarManager.MEDIA_TRANSFER_RECEIVER_STATE_FAR_FROM_SENDER,
                ROUTE_INFO,
                null,
                null
            )

            // And wait until the chip does disappear.
            eventually {
                val chip = uiDevice.findObject(By.res(MEDIA_RECEIVER_CHIP_ID))
                assertThat(chip).isNull()
            }
        }
    }

    @Test(expected = SecurityException::class)
    fun noPermission_throwsSecurityException() {
        uiAutomation.dropShellPermissionIdentity()
        statusBarManager.updateMediaTapToTransferReceiverDisplay(
            StatusBarManager.MEDIA_TRANSFER_RECEIVER_STATE_CLOSE_TO_SENDER,
            ROUTE_INFO,
            null,
            null
        )
    }

    @Test
    @Ignore("b/278264380")
    fun closeToSender_displaysChip() {
        statusBarManager.updateMediaTapToTransferReceiverDisplay(
            StatusBarManager.MEDIA_TRANSFER_RECEIVER_STATE_CLOSE_TO_SENDER,
            ROUTE_INFO,
            null,
            null
        )

        eventually {
            val chip = uiDevice.findObject(By.res(MEDIA_RECEIVER_CHIP_ID))
            assertThat(chip).isNotNull()
        }
    }

    @Test
    @Ignore("b/278264380")
    fun farFromSender_hidesChip() {
        // First, make sure we display the chip
        statusBarManager.updateMediaTapToTransferReceiverDisplay(
            StatusBarManager.MEDIA_TRANSFER_RECEIVER_STATE_CLOSE_TO_SENDER,
            ROUTE_INFO,
            null,
            null
        )

        eventually {
            val chip = uiDevice.findObject(By.res(MEDIA_RECEIVER_CHIP_ID))
            assertThat(chip).isNotNull()
        }

        // Then, make sure we hide the chip
        statusBarManager.updateMediaTapToTransferReceiverDisplay(
            StatusBarManager.MEDIA_TRANSFER_RECEIVER_STATE_FAR_FROM_SENDER,
            ROUTE_INFO,
            null,
            null
        )

        eventually {
            val chip = uiDevice.findObject(By.res(MEDIA_RECEIVER_CHIP_ID))
            assertThat(chip).isNull()
        }
    }

    @Test
    @Ignore("b/278264380")
    fun transferToReceiverSucceeded_hidesChip() {
        // First, make sure we display the chip
        statusBarManager.updateMediaTapToTransferReceiverDisplay(
            StatusBarManager.MEDIA_TRANSFER_RECEIVER_STATE_CLOSE_TO_SENDER,
            ROUTE_INFO,
            null,
            null
        )

        eventually {
            val chip = uiDevice.findObject(By.res(MEDIA_RECEIVER_CHIP_ID))
            assertThat(chip).isNotNull()
        }

        // Then, make sure we hide the chip
        statusBarManager.updateMediaTapToTransferReceiverDisplay(
            StatusBarManager.MEDIA_TRANSFER_RECEIVER_STATE_TRANSFER_TO_RECEIVER_SUCCEEDED,
            ROUTE_INFO,
            null,
            null
        )

        eventually {
            val chip = uiDevice.findObject(By.res(MEDIA_RECEIVER_CHIP_ID))
            assertThat(chip).isNull()
        }
    }

    @Test
    @Ignore("b/278264380")
    fun transferToReceiverFailed_hidesChip() {
        // First, make sure we display the chip
        statusBarManager.updateMediaTapToTransferReceiverDisplay(
            StatusBarManager.MEDIA_TRANSFER_RECEIVER_STATE_CLOSE_TO_SENDER,
            ROUTE_INFO,
            null,
            null
        )

        eventually {
            val chip = uiDevice.findObject(By.res(MEDIA_RECEIVER_CHIP_ID))
            assertThat(chip).isNotNull()
        }

        // Then, make sure we hide the chip
        statusBarManager.updateMediaTapToTransferReceiverDisplay(
            StatusBarManager.MEDIA_TRANSFER_RECEIVER_STATE_TRANSFER_TO_RECEIVER_FAILED,
            ROUTE_INFO,
            null,
            null
        )

        eventually {
            val chip = uiDevice.findObject(By.res(MEDIA_RECEIVER_CHIP_ID))
            assertThat(chip).isNull()
        }
    }
}

private const val MEDIA_RECEIVER_CHIP_ID = "com.android.systemui:id/media_ttt_receiver_chip"
private val MEDIA_PERMISSION: String = android.Manifest.permission.MEDIA_CONTENT_CONTROL
private val ROUTE_INFO = MediaRoute2Info.Builder("id", "Test Name")
    .addFeature("feature")
    .setIconUri(Uri.parse("content://ctstest"))
    .build()
