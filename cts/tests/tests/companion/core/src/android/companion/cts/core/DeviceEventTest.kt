package android.companion.cts.core
import android.Manifest
import android.companion.DevicePresenceEvent.EVENT_BLE_APPEARED
import android.companion.DevicePresenceEvent.EVENT_BLE_DISAPPEARED
import android.companion.DevicePresenceEvent.EVENT_BT_CONNECTED
import android.companion.DevicePresenceEvent.EVENT_BT_DISCONNECTED
import android.companion.Flags.FLAG_DEVICE_PRESENCE
import android.companion.cts.common.MAC_ADDRESS_A
import android.companion.cts.common.PrimaryCompanionService
import android.companion.cts.common.toUpperCaseString
import android.os.SystemClock
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test CDM APIs for device events.
 *
 * Run: atest CtsCompanionDeviceManagerCoreTestCases:DeviceEventTest
 *
 * @see android.companion.CompanionDeviceService.onDevicePresenceEvent
 * @see android.companion.DevicePresenceEvent
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(FLAG_DEVICE_PRESENCE)
class DeviceEventTest : CoreTestBase() {
    @get:Rule
    val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()
    @Test
    fun test_ble_device_event() {
        targetApp.associate(MAC_ADDRESS_A)
        val associationId = cdm.myAssociations[0].id

        withShellPermissionIdentity(Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE) {
            cdm.startObservingDevicePresence(MAC_ADDRESS_A.toUpperCaseString())
        }

        simulateDeviceEvent(associationId, EVENT_BLE_APPEARED)
        PrimaryCompanionService.waitAssociationToAppear(associationId)
        SystemClock.sleep(10000)
        assertEquals(
                expected = EVENT_BLE_APPEARED,
                actual = PrimaryCompanionService.getCurrentEvent()
        )

        simulateDeviceEvent(associationId, EVENT_BLE_DISAPPEARED)
        PrimaryCompanionService.waitAssociationToDisappear(associationId)
        assertEquals(
                expected = EVENT_BLE_DISAPPEARED,
                actual = PrimaryCompanionService.getCurrentEvent()
        )

        PrimaryCompanionService.forgetDevicePresence(associationId)
        withShellPermissionIdentity(Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE) {
            cdm.stopObservingDevicePresence(MAC_ADDRESS_A.toUpperCaseString())
        }
    }

    @Test
    fun test_classic_bt_device_event() {
        targetApp.associate(MAC_ADDRESS_A)
        val idA = cdm.myAssociations[0].id

        withShellPermissionIdentity(Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE) {
            cdm.startObservingDevicePresence(MAC_ADDRESS_A.toUpperCaseString())
        }

        simulateDeviceEvent(idA, EVENT_BT_CONNECTED)
        PrimaryCompanionService.waitAssociationToBtConnect(idA)
        assertEquals(
                expected = EVENT_BT_CONNECTED,
                actual = PrimaryCompanionService.getCurrentEvent()
        )

        simulateDeviceEvent(idA, EVENT_BT_DISCONNECTED)
        PrimaryCompanionService.waitAssociationToBtDisconnect(idA)
        PrimaryCompanionService.waitAssociationToDisappear(idA)
        assertEquals(
                expected = EVENT_BT_DISCONNECTED,
                actual = PrimaryCompanionService.getCurrentEvent()
        )

        PrimaryCompanionService.forgetDevicePresence(idA)
        withShellPermissionIdentity(Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE) {
            cdm.stopObservingDevicePresence(MAC_ADDRESS_A.toUpperCaseString())
        }
    }

    @Test
    fun test_both_bt_ble_device_event() {
        targetApp.associate(MAC_ADDRESS_A)
        val associationId = cdm.myAssociations[0].id

        withShellPermissionIdentity(Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE) {
            cdm.startObservingDevicePresence(MAC_ADDRESS_A.toUpperCaseString())
        }

        simulateDeviceEvent(associationId, EVENT_BLE_APPEARED)
        PrimaryCompanionService.waitAssociationToAppear(associationId)
        assertEquals(
                expected = EVENT_BLE_APPEARED,
                actual = PrimaryCompanionService.getCurrentEvent()
        )

        simulateDeviceEvent(associationId, EVENT_BT_CONNECTED)
        PrimaryCompanionService.waitAssociationToBtConnect(associationId)
        assertEquals(
                expected = EVENT_BT_CONNECTED,
                actual = PrimaryCompanionService.getCurrentEvent()
        )

        simulateDeviceEvent(associationId, EVENT_BT_DISCONNECTED)
        PrimaryCompanionService.waitAssociationToBtDisconnect(associationId)
        assertEquals(
                expected = EVENT_BT_DISCONNECTED,
                actual = PrimaryCompanionService.getCurrentEvent()
        )

        simulateDeviceEvent(associationId, EVENT_BLE_DISAPPEARED)
        PrimaryCompanionService.waitAssociationToDisappear(associationId)
        assertEquals(
                expected = EVENT_BLE_DISAPPEARED,
                actual = PrimaryCompanionService.getCurrentEvent()
        )

        PrimaryCompanionService.forgetDevicePresence(associationId)
        withShellPermissionIdentity(Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE) {
            cdm.stopObservingDevicePresence(MAC_ADDRESS_A.toUpperCaseString())
        }
    }
}
