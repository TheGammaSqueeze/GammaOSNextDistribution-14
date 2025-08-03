/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.net

import android.net.InvalidPacketException.ERROR_INVALID_IP_ADDRESS
import android.net.InvalidPacketException.ERROR_INVALID_PORT
import android.net.NattSocketKeepalive.NATT_PORT
import android.os.Build
import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import com.android.testutils.ConnectivityModuleTest
import com.android.testutils.DevSdkIgnoreRule
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo
import com.android.testutils.assertEqualBothWays
import com.android.testutils.assertParcelingIsLossless
import com.android.testutils.parcelingRoundTrip
import java.net.Inet6Address
import java.net.InetAddress
import kotlin.test.assertFailsWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class NattKeepalivePacketDataTest {
    @Rule @JvmField
    val ignoreRule: DevSdkIgnoreRule = DevSdkIgnoreRule()

    private val TEST_PORT = 4243
    private val TEST_PORT2 = 4244
    // ::FFFF:1.2.3.4
    private val SRC_V4_MAPPED_V6_ADDRESS_BYTES = byteArrayOf(
        0x00.toByte(),
        0x00.toByte(),
        0x00.toByte(),
        0x00.toByte(),
        0x00.toByte(),
        0x00.toByte(),
        0x00.toByte(),
        0x00.toByte(),
        0x00.toByte(),
        0x00.toByte(),
        0xff.toByte(),
        0xff.toByte(),
        0x01.toByte(),
        0x02.toByte(),
        0x03.toByte(),
        0x04.toByte()
    )
    private val TEST_SRC_ADDRV4 = "198.168.0.2".address()
    private val TEST_DST_ADDRV4 = "198.168.0.1".address()
    private val TEST_ADDRV6 = "2001:db8::1".address()
    // This constant requires to be an Inet6Address, but InetAddresses.parseNumericAddress() will
    // convert v4 mapped v6 address into an Inet4Address. So use Inet6Address.getByAddress() to
    // create the address.
    private val TEST_ADDRV4MAPPEDV6 = Inet6Address.getByAddress(null /* host */,
        SRC_V4_MAPPED_V6_ADDRESS_BYTES, -1 /* scope_id */)
    private val TEST_ADDRV4 = "1.2.3.4".address()

    private fun String.address() = InetAddresses.parseNumericAddress(this)
    private fun nattKeepalivePacket(
        srcAddress: InetAddress? = TEST_SRC_ADDRV4,
        srcPort: Int = TEST_PORT,
        dstAddress: InetAddress? = TEST_DST_ADDRV4,
        dstPort: Int = NATT_PORT
    ) = NattKeepalivePacketData.nattKeepalivePacket(srcAddress, srcPort, dstAddress, dstPort)

    @Test
    fun testConstructor() {
        assertFailsWith<InvalidPacketException>(
            "Dst port is not NATT port should cause exception") {
            nattKeepalivePacket(dstPort = TEST_PORT)
        }.let {
            assertEquals(it.error, ERROR_INVALID_PORT)
        }

        assertFailsWith<InvalidPacketException>("A v6 srcAddress should cause exception") {
            nattKeepalivePacket(srcAddress = TEST_ADDRV6)
        }.let {
            assertEquals(it.error, ERROR_INVALID_IP_ADDRESS)
        }

        assertFailsWith<InvalidPacketException>("A v6 dstAddress should cause exception") {
            nattKeepalivePacket(dstAddress = TEST_ADDRV6)
        }.let {
            assertEquals(it.error, ERROR_INVALID_IP_ADDRESS)
        }

        assertFailsWith<IllegalArgumentException>("Invalid data should cause exception") {
            parcelingRoundTrip(
                NattKeepalivePacketData(TEST_SRC_ADDRV4, TEST_PORT, TEST_DST_ADDRV4, TEST_PORT,
                    byteArrayOf(12, 31, 22, 44)))
        }
    }

    @Test @IgnoreUpTo(Build.VERSION_CODES.R) @ConnectivityModuleTest
    fun testConstructor_afterR() {
        // v4 mapped v6 will be translated to a v4 address.
        assertFailsWith<InvalidPacketException> {
            nattKeepalivePacket(srcAddress = TEST_ADDRV6, dstAddress = TEST_ADDRV4MAPPEDV6)
        }
        assertFailsWith<InvalidPacketException> {
            nattKeepalivePacket(srcAddress = TEST_ADDRV4MAPPEDV6, dstAddress = TEST_ADDRV6)
        }

        // Both src and dst address will be v4 after translation, so it won't cause exception.
        val packet1 = nattKeepalivePacket(
            dstAddress = TEST_ADDRV4MAPPEDV6, srcAddress = TEST_ADDRV4MAPPEDV6)
        assertEquals(TEST_ADDRV4, packet1.srcAddress)
        assertEquals(TEST_ADDRV4, packet1.dstAddress)

        // Packet with v6 src and v6 dst address is valid.
        val packet2 = nattKeepalivePacket(srcAddress = TEST_ADDRV6, dstAddress = TEST_ADDRV6)
        assertEquals(TEST_ADDRV6, packet2.srcAddress)
        assertEquals(TEST_ADDRV6, packet2.dstAddress)
    }

    @Test
    fun testParcel() {
        assertParcelingIsLossless(nattKeepalivePacket())
    }

    @Test
    fun testEquals() {
        assertEqualBothWays(nattKeepalivePacket(), nattKeepalivePacket())
        assertNotEquals(nattKeepalivePacket(dstAddress = TEST_SRC_ADDRV4), nattKeepalivePacket())
        assertNotEquals(nattKeepalivePacket(srcAddress = TEST_DST_ADDRV4), nattKeepalivePacket())
        // Test src port only because dst port have to be NATT_PORT
        assertNotEquals(nattKeepalivePacket(srcPort = TEST_PORT2), nattKeepalivePacket())
    }

    @Test
    fun testHashCode() {
        assertEquals(nattKeepalivePacket().hashCode(), nattKeepalivePacket().hashCode())
    }
}
