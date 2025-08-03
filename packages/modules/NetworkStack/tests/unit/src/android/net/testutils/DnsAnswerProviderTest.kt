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

package android.net.testutils

import android.net.DnsResolver.CLASS_IN
import android.net.DnsResolver.TYPE_A
import android.net.DnsResolver.TYPE_AAAA
import androidx.test.filters.SmallTest
import com.android.net.module.util.DnsPacket
import com.android.testutils.DnsAnswerProvider
import libcore.net.InetAddressUtils
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.assertEquals

@RunWith(JUnit4::class)
@SmallTest
class DnsAnswerProviderTest {
    val ansProvider = DnsAnswerProvider()

    private fun listOfAddresses(vararg addresses: String) =
        addresses.map { address -> InetAddressUtils.parseNumericAddress(address) }

    @Test
    fun testIpv4Answers() {
        val record1 = getTestDnsRecord("www.google.com", "1.2.3.4")
        val record2 = getTestDnsRecord("www.google.com", "5.6.7.8")

        // Verifies that empty response is returned before mocking.
        assertEquals(emptyList(), ansProvider.getAnswer("www.google.com", TYPE_A))

        // Verifies that single response can be returned correctly.
        ansProvider.setAnswer("www.google.com", listOfAddresses("1.2.3.4"))
        assertEquals(listOf(record1), ansProvider.getAnswer("www.google.com", TYPE_A))

        // Verifies that multiple responses can be returned correctly.
        ansProvider.setAnswer("www.google.com", listOfAddresses("1.2.3.4", "5.6.7.8"))
        assertEquals(listOf(record1, record2), ansProvider.getAnswer("www.google.com", TYPE_A))

        // Verifies that null response is returned if queried with wrong type or wrong name.
        assertEquals(emptyList(), ansProvider.getAnswer("www.google.com", TYPE_AAAA))
        assertEquals(emptyList(), ansProvider.getAnswer("www.android.com", TYPE_A))

        // Verifies that the answers for different entry has no effect to the testing one.
        ansProvider.setAnswer("www.example.com", listOfAddresses("8.8.8.8"))
        assertEquals(listOf(record1, record2), ansProvider.getAnswer("www.google.com", TYPE_A))

        // Verifies that the responses can be cleared.
        ansProvider.clearAnswer("www.google.com")
        assertEquals(emptyList(), ansProvider.getAnswer("www.google.com", TYPE_A))
    }

    @Test
    fun testIpv4v6Answers() {
        val record1 = getTestDnsRecord("www.google.com", "2001:db8::1")
        val record2 = getTestDnsRecord("www.google.com", "2001:db8::2")
        val record3 = getTestDnsRecord("www.google.com", "2001:db8::4")
        val v4Record1 = getTestDnsRecord("www.google.com", "1.2.3.4")

        // Verifies that null response is returned before mocking
        assertEquals(emptyList(), ansProvider.getAnswer("www.google.com", TYPE_AAAA))

        ansProvider.setAnswer("www.google.com", listOfAddresses("2001:db8::1", "2001:db8::2"))
        assertEquals(listOf(record1, record2), ansProvider.getAnswer("www.google.com", TYPE_AAAA))

        // Verifies that the answers for different entry has no effect to the testing one.
        ansProvider.setAnswer("www.example.com", listOfAddresses("2001:db8::3"))
        assertEquals(listOf(record1, record2), ansProvider.getAnswer("www.google.com", TYPE_AAAA))

        // Verifies that null response is returned if queried with wrong type or wrong name.
        assertEquals(emptyList(), ansProvider.getAnswer("www.google.com", TYPE_A))
        assertEquals(emptyList(), ansProvider.getAnswer("www.android.com", TYPE_AAAA))

        // Verifies that the responses can be replaced. And different types can be mixed.
        ansProvider.setAnswer("www.google.com", listOfAddresses("2001:db8::4", "1.2.3.4"))
        assertEquals(listOf(record3), ansProvider.getAnswer("www.google.com", TYPE_AAAA))
        assertEquals(listOf(v4Record1), ansProvider.getAnswer("www.google.com", TYPE_A))

        // Verifies that the responses can be cleared.
        ansProvider.clearAnswer("www.google.com")
        assertEquals(emptyList(), ansProvider.getAnswer("www.google.com", TYPE_AAAA))
    }

    private fun getTestDnsRecord(dName: String, address: String) =
            DnsPacket.DnsRecord.makeAOrAAAARecord(DnsPacket.ANSECTION, dName, CLASS_IN, 5 /* ttl */,
                    InetAddressUtils.parseNumericAddress(address))
}