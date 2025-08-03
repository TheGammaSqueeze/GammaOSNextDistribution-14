package android.companion.cts.common

import android.Manifest
import android.companion.AssociationRequest.DEVICE_PROFILE_APP_STREAMING
import android.companion.AssociationRequest.DEVICE_PROFILE_AUTOMOTIVE_PROJECTION
import android.companion.AssociationRequest.DEVICE_PROFILE_COMPUTER
import android.companion.AssociationRequest.DEVICE_PROFILE_GLASSES
import android.companion.AssociationRequest.DEVICE_PROFILE_NEARBY_DEVICE_STREAMING
import android.companion.AssociationRequest.DEVICE_PROFILE_WATCH
import android.net.MacAddress
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelUuid
import java.util.concurrent.Executor

/** Set of all supported CDM Device Profiles. */
val DEVICE_PROFILES = setOf(
        DEVICE_PROFILE_WATCH,
        DEVICE_PROFILE_GLASSES,
        DEVICE_PROFILE_NEARBY_DEVICE_STREAMING,
        DEVICE_PROFILE_COMPUTER,
        DEVICE_PROFILE_APP_STREAMING,
        DEVICE_PROFILE_AUTOMOTIVE_PROJECTION
)

val DEVICE_PROFILE_TO_NAME = mapOf(
        DEVICE_PROFILE_WATCH to "WATCH",
        DEVICE_PROFILE_GLASSES to "GLASSES",
        DEVICE_PROFILE_NEARBY_DEVICE_STREAMING to "NEARBY_DEVICE_STREAMING",
        DEVICE_PROFILE_COMPUTER to "COMPUTER",
        DEVICE_PROFILE_APP_STREAMING to "APP_STREAMING",
        DEVICE_PROFILE_AUTOMOTIVE_PROJECTION to "AUTOMOTIVE_PROJECTION"
)

val DEVICE_PROFILE_TO_PERMISSION = mapOf(
        DEVICE_PROFILE_WATCH to Manifest.permission.REQUEST_COMPANION_PROFILE_WATCH,
        DEVICE_PROFILE_APP_STREAMING to
                Manifest.permission.REQUEST_COMPANION_PROFILE_APP_STREAMING,
        DEVICE_PROFILE_AUTOMOTIVE_PROJECTION to
                Manifest.permission.REQUEST_COMPANION_PROFILE_AUTOMOTIVE_PROJECTION,
        DEVICE_PROFILE_GLASSES to
                Manifest.permission.REQUEST_COMPANION_PROFILE_GLASSES,
        DEVICE_PROFILE_NEARBY_DEVICE_STREAMING to
                Manifest.permission.REQUEST_COMPANION_PROFILE_NEARBY_DEVICE_STREAMING,
        DEVICE_PROFILE_COMPUTER to
                Manifest.permission.REQUEST_COMPANION_PROFILE_COMPUTER
)

val MAC_ADDRESS_A = MacAddress.fromString("00:00:00:00:00:AA")
val MAC_ADDRESS_B = MacAddress.fromString("00:00:00:00:00:BB")
val MAC_ADDRESS_C = MacAddress.fromString("00:00:00:00:00:CC")

val UUID_A: ParcelUuid = ParcelUuid.fromString("bc4990b9-698c-473d-8498-2a5c4119f73d")
val UUID_B: ParcelUuid = ParcelUuid.fromString("ba6d2f1e-9adc-11ee-b9d1-0242ac120002")

const val ASSOCIATION_TAG = "00:00:00:00:00:AA"
var ASSOCIATION_TAG_INVALID = "A".repeat(1025)

const val DEVICE_DISPLAY_NAME_A = "Device A"
const val DEVICE_DISPLAY_NAME_B = "Device B"

val SIMPLE_EXECUTOR: Executor by lazy { Executor { it.run() } }

val MAIN_THREAD_EXECUTOR: Executor by lazy {
    Executor {
        with(Handler.getMain()) { post(it) }
    }
}

val BACKGROUND_THREAD_EXECUTOR: Executor by lazy {
    with(HandlerThread("CdmTestBackgroundThread")) {
        start()
        Executor { threadHandler.post(it) }
    }
}

val PRIMARY_PROCESS_NAME = ":primary"
val SECONDARY_PROCESS_NAME = ":secondary"
