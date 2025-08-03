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

package com.android.server.connectivity.mdns;

import static com.android.server.connectivity.mdns.MdnsSocket.INTERFACE_INDEX_UNSPECIFIED;
import static com.android.testutils.DevSdkIgnoreRuleKt.SC_V2;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import android.net.Network;
import android.os.Parcel;

import com.android.server.connectivity.mdns.MdnsServiceInfo.TextEntry;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RunWith(DevSdkIgnoreRunner.class)
@DevSdkIgnoreRule.IgnoreUpTo(SC_V2)
public class MdnsServiceInfoTest {
    @Test
    public void constructor_createWithOnlyTextStrings_correctAttributes() {
        MdnsServiceInfo info =
                new MdnsServiceInfo(
                        "my-mdns-service",
                        new String[] {"_googlecast", "_tcp"},
                        List.of(),
                        new String[] {"my-host", "local"},
                        12345,
                        "192.168.1.1",
                        "2001::1",
                        List.of("vn=Google Inc.", "mn=Google Nest Hub Max"),
                        /* textEntries= */ null);

        assertTrue(info.getAttributeByKey("vn").equals("Google Inc."));
        assertTrue(info.getAttributeByKey("mn").equals("Google Nest Hub Max"));
    }

    @Test
    public void constructor_createWithOnlyTextEntries_correctAttributes() {
        MdnsServiceInfo info =
                new MdnsServiceInfo(
                        "my-mdns-service",
                        new String[] {"_googlecast", "_tcp"},
                        List.of(),
                        new String[] {"my-host", "local"},
                        12345,
                        "192.168.1.1",
                        "2001::1",
                        /* textStrings= */ null,
                        List.of(MdnsServiceInfo.TextEntry.fromString("vn=Google Inc."),
                                MdnsServiceInfo.TextEntry.fromString("mn=Google Nest Hub Max")));

        assertTrue(info.getAttributeByKey("vn").equals("Google Inc."));
        assertTrue(info.getAttributeByKey("mn").equals("Google Nest Hub Max"));
    }

    @Test
    public void constructor_createWithBothTextStringsAndTextEntries_acceptsOnlyTextEntries() {
        MdnsServiceInfo info =
                new MdnsServiceInfo(
                        "my-mdns-service",
                        new String[] {"_googlecast", "_tcp"},
                        List.of(),
                        new String[] {"my-host", "local"},
                        12345,
                        "192.168.1.1",
                        "2001::1",
                        List.of("vn=Alphabet Inc.", "mn=Google Nest Hub Max", "id=12345"),
                        List.of(
                                MdnsServiceInfo.TextEntry.fromString("vn=Google Inc."),
                                MdnsServiceInfo.TextEntry.fromString("mn=Google Nest Hub Max")));

        assertEquals(Map.of("vn", "Google Inc.", "mn", "Google Nest Hub Max"),
                info.getAttributes());
    }

    @Test
    public void constructor_createWithDuplicateKeys_acceptsTheFirstOne() {
        MdnsServiceInfo info =
                new MdnsServiceInfo(
                        "my-mdns-service",
                        new String[] {"_googlecast", "_tcp"},
                        List.of(),
                        new String[] {"my-host", "local"},
                        12345,
                        "192.168.1.1",
                        "2001::1",
                        List.of("vn=Alphabet Inc.", "mn=Google Nest Hub Max", "id=12345"),
                        List.of(MdnsServiceInfo.TextEntry.fromString("vn=Google Inc."),
                                MdnsServiceInfo.TextEntry.fromString("mn=Google Nest Hub Max"),
                                MdnsServiceInfo.TextEntry.fromString("mn=Google WiFi Router")));

        assertEquals(Map.of("vn", "Google Inc.", "mn", "Google Nest Hub Max"),
                info.getAttributes());
    }

    @Test
    public void constructor_createWithUppercaseKeys_correctAttributes() {
        MdnsServiceInfo info =
                new MdnsServiceInfo(
                        "my-mdns-service",
                        new String[] {"_testtype", "_tcp"},
                        List.of(),
                        new String[] {"my-host", "local"},
                        12345,
                        "192.168.1.1",
                        "2001::1",
                        List.of("KEY=Value"),
                        /* textEntries= */ null);

        assertEquals("Value", info.getAttributeByKey("key"));
        assertEquals("Value", info.getAttributeByKey("KEY"));
        assertEquals(1, info.getAttributes().size());
        assertEquals("KEY", info.getAttributes().keySet().iterator().next());
    }

    @Test
    public void getInterfaceIndex_constructorWithDefaultValues_returnsMinusOne() {
        MdnsServiceInfo info =
                new MdnsServiceInfo(
                        "my-mdns-service",
                        new String[] {"_googlecast", "_tcp"},
                        List.of(),
                        new String[] {"my-host", "local"},
                        12345,
                        "192.168.1.1",
                        "2001::1",
                        List.of());

        assertEquals(info.getInterfaceIndex(), INTERFACE_INDEX_UNSPECIFIED);
    }

    @Test
    public void getInterfaceIndex_constructorWithInterfaceIndex_returnsProvidedIndex() {
        MdnsServiceInfo info =
                new MdnsServiceInfo(
                        "my-mdns-service",
                        new String[] {"_googlecast", "_tcp"},
                        List.of(),
                        new String[] {"my-host", "local"},
                        12345,
                        "192.168.1.1",
                        "2001::1",
                        List.of(),
                        /* textEntries= */ null,
                        /* interfaceIndex= */ 20);

        assertEquals(info.getInterfaceIndex(), 20);
    }

    @Test
    public void testGetNetwork() {
        final MdnsServiceInfo info1 =
                new MdnsServiceInfo(
                        "my-mdns-service",
                        new String[] {"_googlecast", "_tcp"},
                        List.of(),
                        new String[] {"my-host", "local"},
                        12345,
                        "192.168.1.1",
                        "2001::1",
                        List.of(),
                        /* textEntries= */ null,
                        /* interfaceIndex= */ 20);

        assertNull(info1.getNetwork());

        final Network network = mock(Network.class);
        final MdnsServiceInfo info2 =
                new MdnsServiceInfo(
                        "my-mdns-service",
                        new String[] {"_googlecast", "_tcp"},
                        List.of(),
                        new String[] {"my-host", "local"},
                        12345,
                        List.of("192.168.1.1"),
                        List.of("2001::1"),
                        List.of(),
                        /* textEntries= */ null,
                        /* interfaceIndex= */ 20,
                        network,
                        Instant.MAX /* expirationTime */);

        assertEquals(network, info2.getNetwork());
    }

    @Test
    public void parcelable_canBeParceledAndUnparceled() {
        Parcel parcel = Parcel.obtain();
        MdnsServiceInfo beforeParcel =
                new MdnsServiceInfo(
                        "my-mdns-service",
                        new String[] {"_googlecast", "_tcp"},
                        List.of(),
                        new String[] {"my-host", "local"},
                        12345,
                        List.of("192.168.1.1", "192.168.1.2"),
                        List.of("2001::1", "2001::2"),
                        List.of("vn=Alphabet Inc.", "mn=Google Nest Hub Max", "id=12345"),
                        List.of(
                                MdnsServiceInfo.TextEntry.fromString("vn=Google Inc."),
                                MdnsServiceInfo.TextEntry.fromString("mn=Google Nest Hub Max"),
                                MdnsServiceInfo.TextEntry.fromString("test=")),
                        20 /* interfaceIndex */,
                        new Network(123),
                        Instant.MAX /* expirationTime */);

        beforeParcel.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        MdnsServiceInfo afterParcel = MdnsServiceInfo.CREATOR.createFromParcel(parcel);

        assertEquals(beforeParcel.getServiceInstanceName(), afterParcel.getServiceInstanceName());
        assertArrayEquals(beforeParcel.getServiceType(), afterParcel.getServiceType());
        assertEquals(beforeParcel.getSubtypes(), afterParcel.getSubtypes());
        assertArrayEquals(beforeParcel.getHostName(), afterParcel.getHostName());
        assertEquals(beforeParcel.getPort(), afterParcel.getPort());
        assertEquals(beforeParcel.getIpv4Address(), afterParcel.getIpv4Address());
        assertEquals(beforeParcel.getIpv4Addresses(), afterParcel.getIpv4Addresses());
        assertEquals(beforeParcel.getIpv6Address(), afterParcel.getIpv6Address());
        assertEquals(beforeParcel.getIpv6Addresses(), afterParcel.getIpv6Addresses());
        assertEquals(beforeParcel.getAttributes(), afterParcel.getAttributes());
        assertEquals(beforeParcel.getInterfaceIndex(), afterParcel.getInterfaceIndex());
        assertEquals(beforeParcel.getNetwork(), afterParcel.getNetwork());
    }

    @Test
    public void textEntry_parcelable_canBeParceledAndUnparceled() {
        Parcel parcel = Parcel.obtain();
        TextEntry beforeParcel = new TextEntry("AA", new byte[] {(byte) 0xFF, (byte) 0xFC});

        beforeParcel.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        TextEntry afterParcel = TextEntry.CREATOR.createFromParcel(parcel);

        assertEquals(beforeParcel, afterParcel);
    }

    @Test
    public void textEntry_fromString_keyValueAreExpected() {
        TextEntry entry = TextEntry.fromString("AA=xxyyzz");

        assertEquals("AA", entry.getKey());
        assertArrayEquals(new byte[] {'x', 'x', 'y', 'y', 'z', 'z'}, entry.getValue());
    }

    @Test
    public void textEntry_fromStringToString_textUnchanged() {
        TextEntry entry = TextEntry.fromString("AA=xxyyzz");

        assertEquals("AA=xxyyzz", entry.toString());
    }

    @Test
    public void textEntry_fromStringWithoutAssignPunc_noValue() {
        TextEntry entry = TextEntry.fromString("AA");

        assertEquals("AA", entry.getKey());
        assertNull(entry.getValue());
    }

    @Test
    public void textEntry_fromStringAssignPuncAtBeginning_returnsNull() {
        TextEntry entry = TextEntry.fromString("=AA");

        assertNull(entry);
    }

    @Test
    public void textEntry_fromBytes_keyAndValueAreExpected() {
        TextEntry entry = TextEntry.fromBytes(
                new byte[] {'A', 'A', '=', 'x', 'x', 'y', 'y', 'z', 'z'});

        assertEquals("AA", entry.getKey());
        assertArrayEquals(new byte[] {'x', 'x', 'y', 'y', 'z', 'z'}, entry.getValue());
    }

    @Test
    public void textEntry_fromBytesToBytes_textUnchanged() {
        TextEntry entry = TextEntry.fromBytes(
                new byte[] {'A', 'A', '=', 'x', 'x', 'y', 'y', 'z', 'z'});

        assertArrayEquals(new byte[] {'A', 'A', '=', 'x', 'x', 'y', 'y', 'z', 'z'},
                entry.toBytes());
    }

    @Test
    public void textEntry_fromBytesWithoutAssignPunc_noValue() {
        TextEntry entry = TextEntry.fromBytes(new byte[] {'A', 'A'});

        assertEquals("AA", entry.getKey());
        assertNull(entry.getValue());
    }

    @Test
    public void textEntry_fromBytesAssignPuncAtBeginning_returnsNull() {
        TextEntry entry = TextEntry.fromBytes(new byte[] {'=', 'A', 'A'});

        assertNull(entry);
    }

    @Test
    public void textEntry_fromNonUtf8Bytes_keyValueAreExpected() {
        TextEntry entry = TextEntry.fromBytes(
                new byte[] {'A', 'A', '=', (byte) 0xFF, (byte) 0xFE, (byte) 0xFD});

        assertEquals("AA", entry.getKey());
        assertArrayEquals(new byte[] {(byte) 0xFF, (byte) 0xFE, (byte) 0xFD}, entry.getValue());
    }

    @Test
    public void textEntry_equals() {
        assertEquals(new TextEntry("AA", "xxyyzz"), new TextEntry("AA", "xxyyzz"));
        assertEquals(new TextEntry("BB", "xxyyzz"), new TextEntry("BB", "xxyyzz"));
        assertEquals(new TextEntry("AA", "XXYYZZ"), new TextEntry("AA", "XXYYZZ"));
    }

    @Test
    public void textEntry_fromString_valueIsEmpty() {
        TextEntry entry = TextEntry.fromString("AA=");

        assertEquals("AA", entry.getKey());
        assertArrayEquals(new byte[] {}, entry.getValue());
    }
}
