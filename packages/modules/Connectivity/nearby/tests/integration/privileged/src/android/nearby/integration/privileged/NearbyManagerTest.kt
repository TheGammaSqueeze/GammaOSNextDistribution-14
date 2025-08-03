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

package android.nearby.integration.privileged

import android.content.Context
import android.nearby.BroadcastCallback
import android.nearby.BroadcastRequest
import android.nearby.NearbyDevice
import android.nearby.NearbyManager
import android.nearby.PresenceBroadcastRequest
import android.nearby.PresenceCredential
import android.nearby.PrivateCredential
import android.nearby.ScanCallback
import android.nearby.ScanRequest
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NearbyManagerTest {
    private lateinit var appContext: Context

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext<Context>()
    }

    /** Verify privileged app can get Nearby service. */
    @Test
    fun testContextGetNearbySystemService_fromPrivilegedApp_returnsNoneNull() {
        assertThat(appContext.getSystemService(Context.NEARBY_SERVICE)).isNotNull()
    }

    /** Verify privileged app can start/stop scan without exception. */
    @Test
    fun testNearbyManagerStartScanStopScan_fromPrivilegedApp_succeed() {
        val nearbyManager = appContext.getSystemService(Context.NEARBY_SERVICE) as NearbyManager
        val scanRequest = ScanRequest.Builder()
            .setScanMode(ScanRequest.SCAN_MODE_LOW_LATENCY)
            .setScanType(ScanRequest.SCAN_TYPE_FAST_PAIR)
            .setBleEnabled(true)
            .build()
        val scanCallback = object : ScanCallback {
            override fun onDiscovered(device: NearbyDevice) {}

            override fun onUpdated(device: NearbyDevice) {}

            override fun onLost(device: NearbyDevice) {}

            override fun onError(errorCode: Int) {}
        }

        nearbyManager.startScan(scanRequest, /* executor */ { it.run() }, scanCallback)
        nearbyManager.stopScan(scanCallback)
    }

    /** Verify privileged app can start/stop broadcast without exception. */
    @Test
    fun testNearbyManagerStartBroadcastStopBroadcast_fromPrivilegedApp_succeed() {
        val nearbyManager = appContext.getSystemService(Context.NEARBY_SERVICE) as NearbyManager
        val salt = byteArrayOf(1, 2)
        val secreteId = byteArrayOf(1, 2, 3, 4)
        val metadataEncryptionKey = ByteArray(14)
        val authenticityKey = byteArrayOf(0, 1, 1, 1)
        val deviceName = "test_device"
        val mediums = listOf(BroadcastRequest.MEDIUM_BLE)
        val credential =
            PrivateCredential.Builder(secreteId, authenticityKey, metadataEncryptionKey, deviceName)
                .setIdentityType(PresenceCredential.IDENTITY_TYPE_PRIVATE)
                .build()
        val broadcastRequest: BroadcastRequest =
            PresenceBroadcastRequest.Builder(mediums, salt, credential)
                .addAction(123)
                .build()
        val broadcastCallback = BroadcastCallback { }

        nearbyManager.startBroadcast(
            broadcastRequest, /* executor */ { it.run() }, broadcastCallback
        )
        nearbyManager.stopBroadcast(broadcastCallback)
    }

    /** Verify privileged app can set powered off finding ephemeral IDs without exception. */
    @Test
    fun testNearbyManagerSetPoweredOffFindingEphemeralIds_fromPrivilegedApp_succeed() {
        val nearbyManager = appContext.getSystemService(Context.NEARBY_SERVICE) as NearbyManager
        // Only test supporting devices.
        if (nearbyManager.getPoweredOffFindingMode()
                == NearbyManager.POWERED_OFF_FINDING_MODE_UNSUPPORTED) return

        val eid = ByteArray(20)

        nearbyManager.setPoweredOffFindingEphemeralIds(listOf(eid))
    }

    /**
     * Verifies that [NearbyManager.setPoweredOffFindingEphemeralIds] checkes the ephemeral ID
     * length.
     */
    @Test
    fun testNearbyManagerSetPoweredOffFindingEphemeralIds_wrongSize_throwsException() {
        val nearbyManager = appContext.getSystemService(Context.NEARBY_SERVICE) as NearbyManager
        // Only test supporting devices.
        if (nearbyManager.getPoweredOffFindingMode()
                == NearbyManager.POWERED_OFF_FINDING_MODE_UNSUPPORTED) return

        assertThrows(IllegalArgumentException::class.java) {
            nearbyManager.setPoweredOffFindingEphemeralIds(listOf(ByteArray(21)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            nearbyManager.setPoweredOffFindingEphemeralIds(listOf(ByteArray(19)))
        }
    }

    /** Verify privileged app can set and get powered off finding mode without exception. */
    @Test
    fun testNearbyManagerSetGetPoweredOffMode_fromPrivilegedApp_succeed() {
        val nearbyManager = appContext.getSystemService(Context.NEARBY_SERVICE) as NearbyManager
        // Only test supporting devices.
        if (nearbyManager.getPoweredOffFindingMode()
                == NearbyManager.POWERED_OFF_FINDING_MODE_UNSUPPORTED) return

        nearbyManager.setPoweredOffFindingMode(NearbyManager.POWERED_OFF_FINDING_MODE_DISABLED)
        assertThat(nearbyManager.getPoweredOffFindingMode())
                .isEqualTo(NearbyManager.POWERED_OFF_FINDING_MODE_DISABLED)
    }
}
