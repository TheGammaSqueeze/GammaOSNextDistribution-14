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

package com.android.server.connectivity.mdns

import android.net.InetAddresses
import com.android.net.module.util.HexDump
import com.android.testutils.DevSdkIgnoreRunner
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(DevSdkIgnoreRunner::class)
class MdnsPacketTest {
    private fun makeFlags(isLabelCountLimitEnabled: Boolean = false): MdnsFeatureFlags =
            MdnsFeatureFlags.newBuilder()
                    .setIsLabelCountLimitEnabled(isLabelCountLimitEnabled).build()
    @Test
    fun testParseQuery() {
        // Probe packet with 1 question for Android.local, and 4 additionalRecords with 4 addresses
        // for Android.local (similar to legacy mdnsresponder probes, although it used to put 4
        // identical questions(!!) for Android.local when there were 4 addresses).
        val packetHex = "007b0000000100000004000007416e64726f6964056c6f63616c0000ff0001c00c000100" +
                "01000000780004c000027bc00c001c000100000078001020010db8000000000000000000000123c0" +
                "0c001c000100000078001020010db8000000000000000000000456c00c001c000100000078001020" +
                "010db8000000000000000000000789"

        val bytes = HexDump.hexStringToByteArray(packetHex)
        val reader = MdnsPacketReader(bytes, bytes.size, makeFlags())
        val packet = MdnsPacket.parse(reader)

        assertEquals(123, packet.transactionId)
        assertEquals(1, packet.questions.size)
        assertEquals(0, packet.answers.size)
        assertEquals(4, packet.authorityRecords.size)
        assertEquals(0, packet.additionalRecords.size)

        val hostname = arrayOf("Android", "local")
        packet.questions[0].let {
            assertTrue(it is MdnsAnyRecord)
            assertContentEquals(hostname, it.name)
        }

        packet.authorityRecords.forEach {
            assertTrue(it is MdnsInetAddressRecord)
            assertContentEquals(hostname, it.name)
            assertEquals(120000, it.ttl)
        }

        assertEquals(InetAddresses.parseNumericAddress("192.0.2.123"),
                (packet.authorityRecords[0] as MdnsInetAddressRecord).inet4Address!!)
        assertEquals(InetAddresses.parseNumericAddress("2001:db8::123"),
                (packet.authorityRecords[1] as MdnsInetAddressRecord).inet6Address!!)
        assertEquals(InetAddresses.parseNumericAddress("2001:db8::456"),
                (packet.authorityRecords[2] as MdnsInetAddressRecord).inet6Address!!)
        assertEquals(InetAddresses.parseNumericAddress("2001:db8::789"),
                (packet.authorityRecords[3] as MdnsInetAddressRecord).inet6Address!!)
    }

    @Test
    fun testParseQueryWithLabelLoop_ThrowsParseException() {
        val packetWithErrorHex = "000084000000000100000000054C4142454C0454455354C006000C800100000" +
                "07800140454455354056C6F63616C00"

        val bytes = HexDump.hexStringToByteArray(packetWithErrorHex)
        val reader = MdnsPacketReader(
                bytes, bytes.size, makeFlags(isLabelCountLimitEnabled = true))
        assertFailsWith<MdnsPacket.ParseException> {
            MdnsPacket.parse(reader)
        }
    }
}
