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

package com.android.net.module.util.netlink;

import static com.android.net.module.util.netlink.RtNetlinkAddressMessage.IFA_FLAGS;
import static com.android.net.module.util.netlink.RtNetlinkLinkMessage.IFLA_ADDRESS;
import static com.android.net.module.util.netlink.RtNetlinkLinkMessage.IFLA_IFNAME;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.net.MacAddress;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class StructNlAttrTest {
    private static final MacAddress TEST_MAC_ADDRESS = MacAddress.fromString("00:11:22:33:44:55");
    private static final String TEST_INTERFACE_NAME = "wlan0";
    private static final int TEST_ADDR_FLAGS = 0x80;

    @Test
    public void testGetValueAsMacAddress() {
        final StructNlAttr attr1 = new StructNlAttr(IFLA_ADDRESS, TEST_MAC_ADDRESS);
        final MacAddress address1 = attr1.getValueAsMacAddress();
        assertEquals(address1, TEST_MAC_ADDRESS);

        // Invalid mac address byte array.
        final byte[] array = new byte[] {
                (byte) 0x00, (byte) 0x11, (byte) 0x22, (byte) 0x33,
                (byte) 0x44, (byte) 0x55, (byte) 0x66,
        };
        final StructNlAttr attr2 = new StructNlAttr(IFLA_ADDRESS, array);
        final MacAddress address2 = attr2.getValueAsMacAddress();
        assertNull(address2);
    }

    @Test
    public void testGetValueAsString() {
        final StructNlAttr attr1 = new StructNlAttr(IFLA_IFNAME, TEST_INTERFACE_NAME);
        final String str1 = attr1.getValueAsString();
        assertEquals(str1, TEST_INTERFACE_NAME);

        final byte[] array = new byte[] {
                (byte) 0x77, (byte) 0x6c, (byte) 0x61, (byte) 0x6E, (byte) 0x30, (byte) 0x00,
        };
        final StructNlAttr attr2 = new StructNlAttr(IFLA_IFNAME, array);
        final String str2 = attr2.getValueAsString();
        assertEquals(str2, TEST_INTERFACE_NAME);
    }

    @Test
    public void testGetValueAsIntger() {
        final StructNlAttr attr1 = new StructNlAttr(IFA_FLAGS, TEST_ADDR_FLAGS);
        final Integer integer1 = attr1.getValueAsInteger();
        final int int1 = attr1.getValueAsInt(0x08 /* default value */);
        assertEquals(integer1, new Integer(TEST_ADDR_FLAGS));
        assertEquals(int1, TEST_ADDR_FLAGS);

        // Malformed attribute.
        final byte[] malformed_int = new byte[] { (byte) 0x0, (byte) 0x0, (byte) 0x80, };
        final StructNlAttr attr2 = new StructNlAttr(IFA_FLAGS, malformed_int);
        final Integer integer2 = attr2.getValueAsInteger();
        final int int2 = attr2.getValueAsInt(0x08 /* default value */);
        assertNull(integer2);
        assertEquals(int2, 0x08 /* default value */);

        // Null attribute value.
        final byte[] null_int = null;
        final StructNlAttr attr3 = new StructNlAttr(IFA_FLAGS, null_int);
        final Integer integer3 = attr3.getValueAsInteger();
        final int int3 = attr3.getValueAsInt(0x08 /* default value */);
        assertNull(integer3);
        assertEquals(int3, 0x08 /* default value */);
    }

    @Test
    public void testGetValueAsLong() {
        final Long input = 1234567L;
        // Not a real netlink attribute, just for testing
        final StructNlAttr attr = new StructNlAttr(IFA_FLAGS, input);

        final Long output = attr.getValueAsLong();

        assertEquals(input, output);
    }

    @Test
    public void testGetValueAsLong_malformed() {
        final int input = 1234567;
        // Not a real netlink attribute, just for testing
        final StructNlAttr attr = new StructNlAttr(IFA_FLAGS, input);

        final Long output = attr.getValueAsLong();

        assertNull(output);
    }
}
