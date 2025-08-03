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

package android.net.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import android.net.EthernetNetworkSpecifier;
import android.os.Build;

import androidx.test.filters.SmallTest;

import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo;
import com.android.testutils.DevSdkIgnoreRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@IgnoreUpTo(Build.VERSION_CODES.R)
@RunWith(DevSdkIgnoreRunner.class)
public class EthernetNetworkSpecifierTest {

    @Test
    public void testConstructor() {
        final String iface = "testIface";
        final EthernetNetworkSpecifier ns = new EthernetNetworkSpecifier(iface);
        assertEquals(iface, ns.getInterfaceName());
    }

    @Test
    public void testConstructorWithNullValue() {
        assertThrows("Should not be able to call constructor with null value.",
                IllegalArgumentException.class,
                () -> new EthernetNetworkSpecifier(null));
    }

    @Test
    public void testConstructorWithEmptyValue() {
        assertThrows("Should not be able to call constructor with empty value.",
                IllegalArgumentException.class,
                () -> new EthernetNetworkSpecifier(""));
    }

    @Test
    public void testEquals() {
        final String iface = "testIface";
        final EthernetNetworkSpecifier nsOne = new EthernetNetworkSpecifier(iface);
        final EthernetNetworkSpecifier nsTwo = new EthernetNetworkSpecifier(iface);
        assertEquals(nsOne, nsTwo);
    }

    @Test
    public void testNotEquals() {
        final String iface = "testIface";
        final String ifaceTwo = "testIfaceTwo";
        final EthernetNetworkSpecifier nsOne = new EthernetNetworkSpecifier(iface);
        final EthernetNetworkSpecifier nsTwo = new EthernetNetworkSpecifier(ifaceTwo);
        assertNotEquals(nsOne, nsTwo);
    }
}
