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
 * Test that the updateMediaTapToTransferSenderDisplay method fails in all the expected ways.
 *
 * These tests are for [StatusBarManager.updateMediaTapToTransferSenderDisplay].
 */
@RunWith(AndroidJUnit4::class)
class UpdateMediaTapToTransferSenderDisplayTest {
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
            statusBarManager.updateMediaTapToTransferSenderDisplay(
                StatusBarManager.MEDIA_TRANSFER_SENDER_STATE_FAR_FROM_RECEIVER,
                ROUTE_INFO,
                null,
                null
            )

            // And wait until the chip does disappear. (Some states may ignore the FAR_FROM_RECEIVER
            // event and need to time out before disappearing.)
            eventually {
                val chip = uiDevice.findObject(By.res(MEDIA_SENDER_CHIP_ID))
                assertThat(chip).isNull()
            }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun undoCallbackForNotSucceedState_throwsException() {
        statusBarManager.updateMediaTapToTransferSenderDisplay(
            StatusBarManager.MEDIA_TRANSFER_SENDER_STATE_ALMOST_CLOSE_TO_START_CAST,
            ROUTE_INFO,
            context.getMainExecutor(),
            Runnable { }
        )
    }

    @Test
    fun noUndoCallbackWithNotSucceedState_noException() {
        // No assert, just want to check no crash
        statusBarManager.updateMediaTapToTransferSenderDisplay(
            StatusBarManager.MEDIA_TRANSFER_SENDER_STATE_ALMOST_CLOSE_TO_START_CAST,
            ROUTE_INFO,
            /* executor= */ null,
            /* callback= */ null
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun transferToReceiverSucceeded_undoCallbackButNoExecutor_throwsException() {
        statusBarManager.updateMediaTapToTransferSenderDisplay(
            StatusBarManager.MEDIA_TRANSFER_SENDER_STATE_TRANSFER_TO_RECEIVER_SUCCEEDED,
            ROUTE_INFO,
            /* executor= */ null,
            Runnable { }
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun transferToThisDeviceSucceeded_undoCallbackButNoExecutor_throwsException() {
        statusBarManager.updateMediaTapToTransferSenderDisplay(
            StatusBarManager.MEDIA_TRANSFER_SENDER_STATE_TRANSFER_TO_RECEIVER_SUCCEEDED,
            ROUTE_INFO,
            /* executor= */ null,
            Runnable { }
        )
    }

    @Test(expected = SecurityException::class)
    fun noPermission_throwsSecurityException() {
        uiAutomation.dropShellPermissionIdentity()
        statusBarManager.updateMediaTapToTransferSenderDisplay(
            StatusBarManager.MEDIA_TRANSFER_SENDER_STATE_ALMOST_CLOSE_TO_START_CAST,
            ROUTE_INFO,
            /* executor= */ null,
            /* callback= */ null
        )
    }

    @Test
    @Ignore("b/279455374")
    fun almostCloseToStartCast_displaysChip() {
        statusBarManager.updateMediaTapToTransferSenderDisplay(
            StatusBarManager.MEDIA_TRANSFER_SENDER_STATE_ALMOST_CLOSE_TO_START_CAST,
            ROUTE_INFO,
            /* executor= */ null,
            /* callback= */ null
        )

        eventually {
            val chip = uiDevice.findObject(By.res(MEDIA_SENDER_CHIP_ID))
            assertThat(chip).isNotNull()
        }
    }

    @Test
    @Ignore("b/279455374")
    fun almostCloseToEndCast_displaysChip() {
        statusBarManager.updateMediaTapToTransferSenderDisplay(
            StatusBarManager.MEDIA_TRANSFER_SENDER_STATE_ALMOST_CLOSE_TO_END_CAST,
            ROUTE_INFO,
            /* executor= */ null,
            /* callback= */ null
        )

        eventually {
            val chip = uiDevice.findObject(By.res(MEDIA_SENDER_CHIP_ID))
            assertThat(chip).isNotNull()
        }
    }

    @Test
    @Ignore("b/279455374")
    fun transferToReceiverTriggered_displaysChip() {
        statusBarManager.updateMediaTapToTransferSenderDisplay(
            StatusBarManager.MEDIA_TRANSFER_SENDER_STATE_TRANSFER_TO_RECEIVER_TRIGGERED,
            ROUTE_INFO,
            /* executor= */ null,
            /* callback= */ null
        )

        eventually {
            val chip = uiDevice.findObject(By.res(MEDIA_SENDER_CHIP_ID))
            assertThat(chip).isNotNull()
        }
    }

    @Test
    @Ignore("b/279455374")
    fun transferToThisDeviceTriggered_displaysChip() {
        statusBarManager.updateMediaTapToTransferSenderDisplay(
            StatusBarManager.MEDIA_TRANSFER_SENDER_STATE_TRANSFER_TO_THIS_DEVICE_TRIGGERED,
            ROUTE_INFO,
            /* executor= */ null,
            /* callback= */ null
        )

        eventually {
            val chip = uiDevice.findObject(By.res(MEDIA_SENDER_CHIP_ID))
            assertThat(chip).isNotNull()
        }
    }

    @Test
    @Ignore("b/279455374")
    fun transferToReceiverSucceeded_nullCallback_displaysChip() {
        // Required to display the TRIGGERED state before the SUCCEEDED state
        statusBarManager.updateMediaTapToTransferSenderDisplay(
            StatusBarManager.MEDIA_TRANSFER_SENDER_STATE_TRANSFER_TO_RECEIVER_TRIGGERED,
            ROUTE_INFO,
            /* executor= */ null,
            /* callback= */ null,
        )

        statusBarManager.updateMediaTapToTransferSenderDisplay(
            StatusBarManager.MEDIA_TRANSFER_SENDER_STATE_TRANSFER_TO_RECEIVER_SUCCEEDED,
            ROUTE_INFO,
            /* executor= */ null,
            /* callback= */ null
        )

        eventually {
            val chip = uiDevice.findObject(By.res(MEDIA_SENDER_CHIP_ID))
            assertThat(chip).isNotNull()
        }
    }

    @Test
    @Ignore("b/279455374")
    fun transferToReceiverSucceeded_withCallbackAndExecutor_displaysChip() {
        // Required to display the TRIGGERED state before the SUCCEEDED state
        statusBarManager.updateMediaTapToTransferSenderDisplay(
            StatusBarManager.MEDIA_TRANSFER_SENDER_STATE_TRANSFER_TO_RECEIVER_TRIGGERED,
            ROUTE_INFO,
            /* executor= */ null,
            /* callback= */ null,
        )

        statusBarManager.updateMediaTapToTransferSenderDisplay(
            StatusBarManager.MEDIA_TRANSFER_SENDER_STATE_TRANSFER_TO_RECEIVER_SUCCEEDED,
            ROUTE_INFO,
            context.getMainExecutor(),
            Runnable { }
        )

        eventually {
            val chip = uiDevice.findObject(By.res(MEDIA_SENDER_CHIP_ID))
            assertThat(chip).isNotNull()
        }
    }

    @Test
    @Ignore("b/279455374")
    fun transferToThisDeviceSucceeded_nullCallback_displaysChip() {
        // Required to display the TRIGGERED state before the SUCCEEDED state
        statusBarManager.updateMediaTapToTransferSenderDisplay(
            StatusBarManager.MEDIA_TRANSFER_SENDER_STATE_TRANSFER_TO_THIS_DEVICE_TRIGGERED,
            ROUTE_INFO,
            /* executor= */ null,
            /* callback= */ null,
        )

        statusBarManager.updateMediaTapToTransferSenderDisplay(
            StatusBarManager.MEDIA_TRANSFER_SENDER_STATE_TRANSFER_TO_THIS_DEVICE_SUCCEEDED,
            ROUTE_INFO,
            /* executor= */ null,
            /* callback= */ null
        )

        eventually {
            val chip = uiDevice.findObject(By.res(MEDIA_SENDER_CHIP_ID))
            assertThat(chip).isNotNull()
        }
    }

    @Test
    @Ignore("b/279455374")
    fun transferToThisDeviceSucceeded_withCallbackAndExecutor_displaysChip() {
        // Required to display the TRIGGERED state before the SUCCEEDED state
        statusBarManager.updateMediaTapToTransferSenderDisplay(
            StatusBarManager.MEDIA_TRANSFER_SENDER_STATE_TRANSFER_TO_THIS_DEVICE_TRIGGERED,
            ROUTE_INFO,
            /* executor= */ null,
            /* callback= */ null,
        )

        statusBarManager.updateMediaTapToTransferSenderDisplay(
            StatusBarManager.MEDIA_TRANSFER_SENDER_STATE_TRANSFER_TO_THIS_DEVICE_SUCCEEDED,
            ROUTE_INFO,
            context.getMainExecutor(),
            Runnable { }
        )

        eventually {
            val chip = uiDevice.findObject(By.res(MEDIA_SENDER_CHIP_ID))
            assertThat(chip).isNotNull()
        }
    }

    @Test
    @Ignore("b/279455374")
    fun transferToReceiverFailed_displaysChip() {
        // Required to display the TRIGGERED state before the FAILED state
        statusBarManager.updateMediaTapToTransferSenderDisplay(
            StatusBarManager.MEDIA_TRANSFER_SENDER_STATE_TRANSFER_TO_RECEIVER_TRIGGERED,
            ROUTE_INFO,
            /* executor= */ null,
            /* callback= */ null,
        )

        statusBarManager.updateMediaTapToTransferSenderDisplay(
            StatusBarManager.MEDIA_TRANSFER_SENDER_STATE_TRANSFER_TO_RECEIVER_FAILED,
            ROUTE_INFO,
            /* executor= */ null,
            /* callback= */ null
        )

        eventually {
            val chip = uiDevice.findObject(By.res(MEDIA_SENDER_CHIP_ID))
            assertThat(chip).isNotNull()
        }
    }

    @Test
    @Ignore("b/279455374")
    fun transferToThisDeviceFailed_displaysChip() {
        // Required to display the TRIGGERED state before the FAILED state
        statusBarManager.updateMediaTapToTransferSenderDisplay(
            StatusBarManager.MEDIA_TRANSFER_SENDER_STATE_TRANSFER_TO_THIS_DEVICE_TRIGGERED,
            ROUTE_INFO,
            /* executor= */ null,
            /* callback= */ null,
        )

        statusBarManager.updateMediaTapToTransferSenderDisplay(
            StatusBarManager.MEDIA_TRANSFER_SENDER_STATE_TRANSFER_TO_THIS_DEVICE_FAILED,
            ROUTE_INFO,
            /* executor= */ null,
            /* callback= */ null
        )

        eventually {
            val chip = uiDevice.findObject(By.res(MEDIA_SENDER_CHIP_ID))
            assertThat(chip).isNotNull()
        }
    }

    @Test
    @Ignore("b/279455374")
    fun farFromReceiver_hidesChip() {
        // First, make sure we display the chip
        statusBarManager.updateMediaTapToTransferSenderDisplay(
            StatusBarManager.MEDIA_TRANSFER_SENDER_STATE_ALMOST_CLOSE_TO_START_CAST,
            ROUTE_INFO,
            /* executor= */ null,
            /* callback= */ null
        )

        eventually {
            val chip = uiDevice.findObject(By.res(MEDIA_SENDER_CHIP_ID))
            assertThat(chip).isNotNull()
        }

        // Then, make sure we hide the chip
        statusBarManager.updateMediaTapToTransferSenderDisplay(
            StatusBarManager.MEDIA_TRANSFER_SENDER_STATE_FAR_FROM_RECEIVER,
            ROUTE_INFO,
            /* executor= */ null,
            /* callback= */ null
        )

        eventually {
            val chip = uiDevice.findObject(By.res(MEDIA_SENDER_CHIP_ID))
            assertThat(chip).isNull()
        }
    }
}

private const val MEDIA_SENDER_CHIP_ID = "com.android.systemui:id/chipbar_root_view"
private val MEDIA_PERMISSION: String = android.Manifest.permission.MEDIA_CONTENT_CONTROL
private val ROUTE_INFO = MediaRoute2Info.Builder("id", "Test Name")
    .addFeature("feature")
    .setIconUri(Uri.parse("content://ctstest"))
    .build()
