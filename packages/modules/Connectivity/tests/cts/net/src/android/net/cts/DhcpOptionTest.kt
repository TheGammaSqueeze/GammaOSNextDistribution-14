/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.net.cts

import android.net.DhcpOption
import androidx.test.filters.SmallTest
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo
import com.android.testutils.DevSdkIgnoreRunner
import com.android.testutils.SC_V2
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.runner.RunWith
import org.junit.Test

@SmallTest
@IgnoreUpTo(SC_V2) // TODO: Use to Build.VERSION_CODES.SC_V2 when available
@RunWith(DevSdkIgnoreRunner::class)
class DhcpOptionTest {
    private val DHCP_OPTION_TYPE: Byte = 2
    private val DHCP_OPTION_VALUE = byteArrayOf(0, 1, 2, 4, 8, 16)

    @Test
    fun testConstructor() {
        val dhcpOption = DhcpOption(DHCP_OPTION_TYPE, DHCP_OPTION_VALUE)
        assertEquals(DHCP_OPTION_TYPE, dhcpOption.type)
        assertArrayEquals(DHCP_OPTION_VALUE, dhcpOption.value)
    }

    @Test
    fun testConstructorWithNullValue() {
        val dhcpOption = DhcpOption(DHCP_OPTION_TYPE, null)
        assertEquals(DHCP_OPTION_TYPE, dhcpOption.type)
        assertNull(dhcpOption.value)
    }
}