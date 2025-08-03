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
 * limitations under the License
 */

package com.android.server.connectivity.mdns;

import static android.net.InetAddresses.parseNumericAddress;

import static com.android.server.connectivity.mdns.util.MdnsUtils.Clock;
import static com.android.testutils.DevSdkIgnoreRuleKt.SC_V2;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import android.net.Network;
import android.util.ArraySet;

import com.android.net.module.util.HexDump;
import com.android.server.connectivity.mdns.MdnsResponseTests.MdnsInet4AddressRecord;
import com.android.server.connectivity.mdns.MdnsResponseTests.MdnsInet6AddressRecord;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRunner;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RunWith(DevSdkIgnoreRunner.class)
@DevSdkIgnoreRule.IgnoreUpTo(SC_V2)
public class MdnsResponseDecoderTests {
    private static final byte[] data = HexDump.hexStringToByteArray(
            "0000840000000004"
            + "00000003134A6F68"
            + "6E6E792773204368"
            + "726F6D6563617374"
            + "0B5F676F6F676C65"
            + "63617374045F7463"
            + "70056C6F63616C00"
            + "0010800100001194"
            + "006C2369643D3937"
            + "3062663534376237"
            + "3533666336336332"
            + "6432613336626238"
            + "3936616261380576"
            + "653D30320D6D643D"
            + "4368726F6D656361"
            + "73741269633D2F73"
            + "657475702F69636F"
            + "6E2E706E6716666E"
            + "3D4A6F686E6E7927"
            + "73204368726F6D65"
            + "636173740463613D"
            + "350473743D30095F"
            + "7365727669636573"
            + "075F646E732D7364"
            + "045F756470C03100"
            + "0C00010000119400"
            + "02C020C020000C00"
            + "01000011940002C0"
            + "0CC00C0021800100"
            + "000078001C000000"
            + "001F49134A6F686E"
            + "6E79277320436872"
            + "6F6D6563617374C0"
            + "31C0F30001800100"
            + "0000780004C0A864"
            + "68C0F3002F800100"
            + "0000780005C0F300"
            + "0140C00C002F8001"
            + "000011940009C00C"
            + "00050000800040");

    private static final byte[] data6 = HexDump.hexStringToByteArray(
            "0000840000000001000000030B5F676F6F676C656361737404"
            + "5F746370056C6F63616C00000C000100000078003330476F6F676C"
            + "652D486F6D652D4D61782D61363836666331323961366638636265"
            + "31643636353139343065336164353766C00CC02E00108001000011"
            + "9400C02369643D6136383666633132396136663863626531643636"
            + "3531393430653361643537662363643D4133304233303032363546"
            + "36384341313233353532434639344141353742314613726D3D4335"
            + "35393134383530383841313638330576653D3035126D643D476F6F"
            + "676C6520486F6D65204D61781269633D2F73657475702F69636F6E"
            + "2E706E6710666E3D417474696320737065616B65720863613D3130"
            + "3234340473743D320F62733D464138464341363734453537046E66"
            + "3D320372733DC02E0021800100000078002D000000001F49246136"
            + "3836666331322D396136662D386362652D316436362D3531393430"
            + "65336164353766C01DC13F001C8001000000780010200033330000"
            + "0000DA6C63FFFE7C74830109018001000000780004C0A801026C6F"
            + "63616C0000018001000000780004C0A8010A000001800100000078"
            + "0004C0A8010A00000000000000");

    // Expected to contain two SRV records which point to the same hostname.
    private static final byte[] matterDuplicateHostname = HexDump.hexStringToByteArray(
            "00008000000000080000000A095F7365727669636573075F646E732D73"
            + "64045F756470056C6F63616C00000C000100000078000F075F6D61"
            + "74746572045F746370C023C00C000C000100000078001A125F4943"
            + "324639453337374632454139463430045F737562C034C034000C00"
            + "0100000078002421433246394533373746324541394634302D3030"
            + "3030303030304534443041334641C034C04F000C00010000007800"
            + "02C075C00C000C0001000000780002C034C00C000C000100000078"
            + "0015125F4941413035363731333439334135343144C062C034000C"
            + "000100000078002421414130353637313334393341353431442D30"
            + "303030303030304331324446303344C034C0C1000C000100000078"
            + "0002C0E2C075002100010000007800150000000015A40C33433631"
            + "3035304338394638C023C07500100001000011940015084352493D"
            + "35303030074352413D33303003543D31C126001C00010000007800"
            + "10FE800000000000003E6105FFFE0C89F8C126001C000100000078"
            + "00102605A601A84657003E6105FFFE0C89F8C12600010001000000"
            + "780004C0A8018AC0E2002100010000007800080000000015A4C126"
            + "C0E200100001000011940015084352493D35303030074352413D33"
            + "303003543D31C126001C0001000000780010FE800000000000003E"
            + "6105FFFE0C89F8C126001C00010000007800102605A601A8465700"
            + "3E6105FFFE0C89F8C12600010001000000780004C0A8018A313035"
            + "304338394638C02300010001000000780004C0A8018AC0A0001000"
            + "0100001194003A0E56503D36353532312B3332373639084352493D"
            + "35303030074352413D33303003543D3106443D3236353704434D3D"
            + "320550483D33360350493D21433246394533373746324541394634"
            + "302D30303030303030304534443041334641C0F700210001000000"
            + "7800150000000015A40C334336313035304338394638C023214332"
            + "46394533373746324541394634302D303030303030303045344430"
            + "41334641C0F700100001000011940015084352493D353030300743"
            + "52413D33303003543D310C334336313035304338394638C023001C"
            + "0001000000780010FE800000000000003E6105FFFE0C89F80C3343"
            + "36313035304338394638C023001C00010000007800102605A601A8"
            + "4657003E6105FFFE0C89F80C334336313035304338394638C02300"
            + "010001000000780004C0A8018A0000000000000000000000000000"
            + "000000");

    // MDNS record for name "testhost1" with an IPv4 address of 10.1.2.3. Also set cache flush bit
    // for the records changed.
    private static final byte[] DATAIN_IPV4_1 = HexDump.hexStringToByteArray(
            "0974657374686f73743100000180010000007800040a010203");
    // MDNS record for name "testhost1" with an IPv4 address of 10.1.2.4. Also set cache flush bit
    // for the records changed.
    private static final byte[] DATAIN_IPV4_2 = HexDump.hexStringToByteArray(
            "0974657374686f73743100000180010000007800040a010204");
    // MDNS record w/name "testhost1" & IPv6 address of aabb:ccdd:1122:3344:a0b0:c0d0:1020:3040.
    // Also set cache flush bit for the records changed.
    private static final byte[] DATAIN_IPV6_1 = HexDump.hexStringToByteArray(
            "0974657374686f73743100001c8001000000780010aabbccdd11223344a0b0c0d010203040");
    // MDNS record w/name "testhost1" & IPv6 address of aabb:ccdd:1122:3344:a0b0:c0d0:1020:3030.
    // Also set cache flush bit for the records changed.
    private static final byte[] DATAIN_IPV6_2 = HexDump.hexStringToByteArray(
            "0974657374686f73743100001c8001000000780010aabbccdd11223344a0b0c0d010203030");
    // MDNS record w/name "test" & PTR to foo.bar.quxx
    private static final byte[] DATAIN_PTR_1 = HexDump.hexStringToByteArray(
            "047465737400000C000100001194000E03666F6F03626172047175787800");
    // MDNS record w/name "test" & PTR to foo.bar.quxy
    private static final byte[] DATAIN_PTR_2 = HexDump.hexStringToByteArray(
            "047465737400000C000100001194000E03666F6F03626172047175787900");
    // SRV record for: scapy.DNSRRSRV(rrname='foo.bar.quxx', ttl=120, port=1234, target='testhost1')
    private static final byte[] DATAIN_SERVICE_1 = HexDump.hexStringToByteArray(
            "03666f6f03626172047175787800002100010000007800110000000004d20974657374686f73743100");
    // SRV record for: scapy.DNSRRSRV(rrname='foo.bar.quxx', ttl=120, port=1234, target='testhost2')
    private static final byte[] DATAIN_SERVICE_2 = HexDump.hexStringToByteArray(
            "03666f6f03626172047175787800002100010000007800110000000004d20974657374686f73743200");
    // TXT record for: scapy.DNSRR(rrname='foo.bar.quxx', type='TXT', ttl=120,
    //     rdata=[b'a=hello there', b'b=1234567890', b'xyz=!$$$'])
    private static final byte[] DATAIN_TEXT_1 = HexDump.hexStringToByteArray(
            "03666f6f03626172047175787800001000010000007800240d613d68656c6c6f2074686572650c623d3132"
                    + "33343536373839300878797a3d21242424");

    // TXT record for: scapy.DNSRR(rrname='foo.bar.quxx', type='TXT', ttl=120,
    //     rdata=[b'a=hello there', b'b=1234567890', b'xyz=!$$$'])
    private static final byte[] DATAIN_TEXT_2 = HexDump.hexStringToByteArray(
            "03666f6f03626172047175787800001000010000007800240d613d68656c6c6f2074686572650c623d3132"
                    + "33343536373839300878797a3d21402324");

    private static final String[] DATAIN_SERVICE_NAME_1 = new String[] { "foo", "bar", "quxx" };

    private static final String CAST_SERVICE_NAME = "_googlecast";
    private static final String[] CAST_SERVICE_TYPE =
            new String[] {CAST_SERVICE_NAME, "_tcp", "local"};
    private static final String MATTER_SERVICE_NAME = "_matter";
    private static final String[] MATTER_SERVICE_TYPE =
            new String[] {MATTER_SERVICE_NAME, "_tcp", "local"};

    private ArraySet<MdnsResponse> responses;

    private final Clock mClock = mock(Clock.class);

    @Before
    public void setUp() throws Exception {
        MdnsResponseDecoder decoder = new MdnsResponseDecoder(mClock, CAST_SERVICE_TYPE);
        assertNotNull(data);
        responses = decode(decoder, data);
        assertEquals(1, responses.size());
    }

    @Test
    public void testDecodeWithNullServiceType() throws Exception {
        MdnsResponseDecoder decoder = new MdnsResponseDecoder(mClock, null);
        responses = decode(decoder, data);
        assertEquals(2, responses.size());
    }

    @Test
    public void testDecodeMultipleAnswerPacket() throws IOException {
        MdnsResponse response = responses.valueAt(0);
        assertTrue(response.isComplete());

        MdnsInetAddressRecord inet4AddressRecord = response.getInet4AddressRecord();
        Inet4Address inet4Addr = inet4AddressRecord.getInet4Address();

        assertNotNull(inet4Addr);
        assertEquals("/192.168.100.104", inet4Addr.toString());

        MdnsServiceRecord serviceRecord = response.getServiceRecord();
        String serviceName = serviceRecord.getServiceName();
        assertEquals(CAST_SERVICE_NAME, serviceName);

        String serviceInstanceName = serviceRecord.getServiceInstanceName();
        assertEquals("Johnny's Chromecast", serviceInstanceName);

        String serviceHost = MdnsRecord.labelsToString(serviceRecord.getServiceHost());
        assertEquals("Johnny's Chromecast.local", serviceHost);

        int serviceProto = serviceRecord.getServiceProtocol();
        assertEquals(MdnsServiceRecord.PROTO_TCP, serviceProto);

        int servicePort = serviceRecord.getServicePort();
        assertEquals(8009, servicePort);

        int servicePriority = serviceRecord.getServicePriority();
        assertEquals(0, servicePriority);

        int serviceWeight = serviceRecord.getServiceWeight();
        assertEquals(0, serviceWeight);

        MdnsTextRecord textRecord = response.getTextRecord();
        List<String> textStrings = textRecord.getStrings();
        assertEquals(7, textStrings.size());
        assertEquals("id=970bf547b753fc63c2d2a36bb896aba8", textStrings.get(0));
        assertEquals("ve=02", textStrings.get(1));
        assertEquals("md=Chromecast", textStrings.get(2));
        assertEquals("ic=/setup/icon.png", textStrings.get(3));
        assertEquals("fn=Johnny's Chromecast", textStrings.get(4));
        assertEquals("ca=5", textStrings.get(5));
        assertEquals("st=0", textStrings.get(6));
    }

    private void verifyResponse(ArraySet<MdnsResponse> responseArraySet) {
        assertEquals(1, responseArraySet.size());
        MdnsResponse response = responseArraySet.valueAt(0);
        assertTrue(response.isComplete());

        MdnsInetAddressRecord inet6AddressRecord = response.getInet6AddressRecord();
        assertNotNull(inet6AddressRecord);
        Inet4Address inet4Addr = inet6AddressRecord.getInet4Address();
        assertNull(inet4Addr);

        Inet6Address inet6Addr = inet6AddressRecord.getInet6Address();
        assertNotNull(inet6Addr);
        assertEquals(inet6Addr.getHostAddress(), "2000:3333::da6c:63ff:fe7c:7483");
    }

    @Test
    public void testDecodeIPv6AnswerPacket() throws IOException {
        MdnsResponseDecoder decoder = new MdnsResponseDecoder(mClock, CAST_SERVICE_TYPE);
        assertNotNull(data6);
        verifyResponse(decode(decoder, data6));
    }

    @Test
    public void testDecodeCaseInsensitiveMatch() throws IOException {
        final String[] castServiceTypeUpperCase =
                new String[] {"_GOOGLECAST", "_TCP", "LOCAL"};
        MdnsResponseDecoder decoder = new MdnsResponseDecoder(mClock, castServiceTypeUpperCase);
        assertNotNull(data6);
        verifyResponse(decode(decoder, data6));
    }

    @Test
    public void testIsComplete() {
        MdnsResponse response = new MdnsResponse(responses.valueAt(0));
        assertTrue(response.isComplete());

        response.clearPointerRecords();
        // The service name is still known in MdnsResponse#getServiceName
        assertTrue(response.isComplete());

        response = new MdnsResponse(responses.valueAt(0));
        response.clearInet4AddressRecords();
        assertFalse(response.isComplete());

        response.addInet6AddressRecord(new MdnsInetAddressRecord(new String[] { "testhostname" },
                0L /* receiptTimeMillis */, false /* cacheFlush */, 1234L /* ttlMillis */,
                parseNumericAddress("2008:db1::123")));
        assertTrue(response.isComplete());

        response.clearInet6AddressRecords();
        assertFalse(response.isComplete());

        response = new MdnsResponse(responses.valueAt(0));
        response.setServiceRecord(null);
        assertFalse(response.isComplete());

        response = new MdnsResponse(responses.valueAt(0));
        response.setTextRecord(null);
        assertFalse(response.isComplete());
    }

    @Test
    public void decode_withInterfaceIndex_populatesInterfaceIndex() throws Exception {
        MdnsResponseDecoder decoder = new MdnsResponseDecoder(mClock, CAST_SERVICE_TYPE);
        assertNotNull(data6);
        DatagramPacket packet = new DatagramPacket(data6, data6.length);
        packet.setSocketAddress(
                new InetSocketAddress(MdnsConstants.getMdnsIPv6Address(), MdnsConstants.MDNS_PORT));

        final MdnsPacket parsedPacket = MdnsResponseDecoder.parseResponse(
                data6, data6.length, MdnsFeatureFlags.newBuilder().build());
        assertNotNull(parsedPacket);

        final Network network = mock(Network.class);
        responses = new ArraySet<>(decoder.augmentResponses(parsedPacket,
                /* existingResponses= */ Collections.emptyList(),
                /* interfaceIndex= */ 10, network /* expireOnExit= */).first);

        assertEquals(responses.size(), 1);
        assertEquals(responses.valueAt(0).getInterfaceIndex(), 10);
        assertEquals(network, responses.valueAt(0).getNetwork());
    }

    @Test
    public void decode_singleHostname_multipleSrvRecords_flagEnabled_multipleCompleteResponses()
            throws Exception {
        //MdnsScannerConfigsFlagsImpl.allowMultipleSrvRecordsPerHost.override(true);
        MdnsResponseDecoder decoder = new MdnsResponseDecoder(mClock, MATTER_SERVICE_TYPE);
        assertNotNull(matterDuplicateHostname);

        responses = decode(decoder, matterDuplicateHostname);

        // This should emit two records:
        assertEquals(2, responses.size());

        MdnsResponse response1 = responses.valueAt(0);
        MdnsResponse response2 = responses.valueAt(0);

        // Both of which are complete:
        assertTrue(response1.isComplete());
        assertTrue(response2.isComplete());

        // And should both have the same IPv6 address:
        assertTrue(response1.getInet6AddressRecords().stream().anyMatch(
                record -> record.getInet6Address().equals(
                        parseNumericAddress("2605:a601:a846:5700:3e61:5ff:fe0c:89f8"))));
        assertTrue(response2.getInet6AddressRecords().stream().anyMatch(
                record -> record.getInet6Address().equals(
                        parseNumericAddress("2605:a601:a846:5700:3e61:5ff:fe0c:89f8"))));
    }

    @Test
    @Ignore("MdnsConfigs is not configurable currently.")
    public void decode_singleHostname_multipleSrvRecords_flagDisabled_singleCompleteResponse()
            throws Exception {
        //MdnsScannerConfigsFlagsImpl.allowMultipleSrvRecordsPerHost.override(false);
        MdnsResponseDecoder decoder = new MdnsResponseDecoder(mClock, MATTER_SERVICE_TYPE);
        assertNotNull(matterDuplicateHostname);

        responses = decode(decoder, matterDuplicateHostname);

        // This should emit only two records:
        assertEquals(2, responses.size());

        // But only the first is complete:
        assertTrue(responses.valueAt(0).isComplete());
        assertFalse(responses.valueAt(1).isComplete());
    }

    @Test
    public void testDecodeWithIpv4AddressChange() throws IOException {
        MdnsResponse response = makeMdnsResponse(0, DATAIN_SERVICE_NAME_1, List.of(
                new PacketAndRecordClass(DATAIN_PTR_1,
                        MdnsPointerRecord.class),
                new PacketAndRecordClass(DATAIN_SERVICE_1,
                        MdnsServiceRecord.class),
                new PacketAndRecordClass(DATAIN_IPV4_1,
                        MdnsInet4AddressRecord.class)));
        // Now update the response with another address
        final MdnsResponseDecoder decoder = new MdnsResponseDecoder(mClock, null);
        final ArraySet<MdnsResponse> updatedResponses = decode(
                decoder, makeResponsePacket(DATAIN_IPV4_2), List.of(response));
        assertEquals(1, updatedResponses.size());
        assertEquals(parseNumericAddress("10.1.2.4"),
                updatedResponses.valueAt(0).getInet4AddressRecord().getInet4Address());
        assertEquals(parseNumericAddress("10.1.2.3"),
                response.getInet4AddressRecord().getInet4Address());
    }

    @Test
    public void testDecodeWithIpv4AddressRemove() throws IOException {
        MdnsResponse response = makeMdnsResponse(0, DATAIN_SERVICE_NAME_1, List.of(
                new PacketAndRecordClass(DATAIN_PTR_1,
                        MdnsPointerRecord.class),
                new PacketAndRecordClass(DATAIN_SERVICE_1,
                        MdnsServiceRecord.class),
                new PacketAndRecordClass(DATAIN_IPV4_1,
                        MdnsInet4AddressRecord.class),
                new PacketAndRecordClass(DATAIN_IPV4_2,
                        MdnsInet4AddressRecord.class)));
        // Now update the response removing the second v4 address
        final MdnsResponseDecoder decoder = new MdnsResponseDecoder(mClock, null);

        final byte[] removedAddrResponse = makeResponsePacket(
                List.of(DATAIN_PTR_1, DATAIN_SERVICE_1, DATAIN_IPV4_1));
        final ArraySet<MdnsResponse> changes = decode(
                decoder, removedAddrResponse, List.of(response));

        assertEquals(1, changes.size());
        assertEquals(1, changes.valueAt(0).getInet4AddressRecords().size());
        assertEquals(parseNumericAddress("10.1.2.3"),
                changes.valueAt(0).getInet4AddressRecords().get(0).getInet4Address());
    }

    @Test
    public void testDecodeWithIpv6AddressChange() throws IOException {
        MdnsResponse response = makeMdnsResponse(0, DATAIN_SERVICE_NAME_1, List.of(
                new PacketAndRecordClass(DATAIN_PTR_1,
                        MdnsPointerRecord.class),
                new PacketAndRecordClass(DATAIN_SERVICE_1,
                        MdnsServiceRecord.class),
                new PacketAndRecordClass(DATAIN_IPV6_1,
                        MdnsInet6AddressRecord.class)));
        // Now update the response with another address
        final MdnsResponseDecoder decoder = new MdnsResponseDecoder(mClock, null);
        final ArraySet<MdnsResponse> updatedResponses = decode(
                decoder, makeResponsePacket(DATAIN_IPV6_2), List.of(response));
        assertEquals(1, updatedResponses.size());
        assertEquals(parseNumericAddress("aabb:ccdd:1122:3344:a0b0:c0d0:1020:3030"),
                updatedResponses.valueAt(0).getInet6AddressRecord().getInet6Address());
        assertEquals(parseNumericAddress("aabb:ccdd:1122:3344:a0b0:c0d0:1020:3040"),
                response.getInet6AddressRecord().getInet6Address());
    }

    @Test
    public void testDecodeWithIpv6AddressRemoved() throws IOException {
        MdnsResponse response = makeMdnsResponse(0, DATAIN_SERVICE_NAME_1, List.of(
                new PacketAndRecordClass(DATAIN_PTR_1,
                        MdnsPointerRecord.class),
                new PacketAndRecordClass(DATAIN_SERVICE_1,
                        MdnsServiceRecord.class),
                new PacketAndRecordClass(DATAIN_IPV6_1,
                        MdnsInet6AddressRecord.class),
                new PacketAndRecordClass(DATAIN_IPV6_2,
                        MdnsInet6AddressRecord.class)));
        // Now update the response adding an address
        final MdnsResponseDecoder decoder = new MdnsResponseDecoder(mClock, null);
        final byte[] removedAddrResponse = makeResponsePacket(
                List.of(DATAIN_PTR_1, DATAIN_SERVICE_1, DATAIN_IPV6_1));
        final ArraySet<MdnsResponse> updatedResponses = decode(
                decoder, removedAddrResponse, List.of(response));
        assertEquals(1, updatedResponses.size());
        assertEquals(1, updatedResponses.valueAt(0).getInet6AddressRecords().size());
        assertEquals(parseNumericAddress("aabb:ccdd:1122:3344:a0b0:c0d0:1020:3040"),
                updatedResponses.valueAt(0).getInet6AddressRecords().get(0).getInet6Address());
    }

    @Test
    public void testDecodeWithChangeOnText() throws IOException {
        MdnsResponse response = makeMdnsResponse(0, DATAIN_SERVICE_NAME_1, List.of(
                new PacketAndRecordClass(DATAIN_PTR_1,
                        MdnsPointerRecord.class),
                new PacketAndRecordClass(DATAIN_SERVICE_1,
                        MdnsServiceRecord.class),
                new PacketAndRecordClass(DATAIN_TEXT_1,
                        MdnsTextRecord.class)));
        // Now update the response with another address
        final MdnsResponseDecoder decoder = new MdnsResponseDecoder(mClock, null);
        final ArraySet<MdnsResponse> updatedResponses = decode(
                decoder, makeResponsePacket(DATAIN_TEXT_2), List.of(response));
        assertEquals(1, updatedResponses.size());
        assertEquals(List.of(
                new MdnsServiceInfo.TextEntry("a", "hello there"),
                new MdnsServiceInfo.TextEntry("b", "1234567890"),
                new MdnsServiceInfo.TextEntry("xyz", "!@#$")),
                updatedResponses.valueAt(0).getTextRecord().getEntries());
    }

    @Test
    public void testDecodeWithChangeOnService() throws IOException {
        MdnsResponse response = makeMdnsResponse(0, DATAIN_SERVICE_NAME_1, List.of(
                new PacketAndRecordClass(DATAIN_PTR_1,
                        MdnsPointerRecord.class),
                new PacketAndRecordClass(DATAIN_SERVICE_1,
                        MdnsServiceRecord.class),
                new PacketAndRecordClass(DATAIN_IPV4_1,
                        MdnsInet4AddressRecord.class)));
        assertArrayEquals(new String[] { "testhost1" },
                response.getServiceRecord().getServiceHost());
        assertNotNull(response.getInet4AddressRecord());
        // Now update the response with another hostname
        final MdnsResponseDecoder decoder = new MdnsResponseDecoder(mClock, null);
        final ArraySet<MdnsResponse> updatedResponses = decode(
                decoder, makeResponsePacket(DATAIN_SERVICE_2), List.of(response));
        assertEquals(1, updatedResponses.size());
        assertArrayEquals(new String[] { "testhost2" },
                updatedResponses.valueAt(0).getServiceRecord().getServiceHost());
        // Hostname changed, so address records are dropped
        assertNull(updatedResponses.valueAt(0).getInet4AddressRecord());
    }

    @Test
    public void testDecodeWithChangeOnPtr() throws IOException {
        MdnsResponse response = makeMdnsResponse(0, DATAIN_SERVICE_NAME_1, List.of(
                new PacketAndRecordClass(DATAIN_PTR_1,
                        MdnsPointerRecord.class),
                new PacketAndRecordClass(DATAIN_SERVICE_1,
                        MdnsServiceRecord.class)));
        // Now update the response with another address
        final MdnsResponseDecoder decoder = new MdnsResponseDecoder(mClock, null);
        final ArraySet<MdnsResponse> updatedResponses = decode(
                decoder, makeResponsePacket(DATAIN_PTR_2), List.of(response));
        assertEquals(1, updatedResponses.size());
        assertArrayEquals(new String[] { "foo", "bar", "quxy" },
                updatedResponses.valueAt(0).getPointerRecords().get(0).getPointer());
    }

    @Test
    public void testDecodeWithNoChange() throws IOException {
        List<PacketAndRecordClass> recordList =
                Arrays.asList(
                        new PacketAndRecordClass(DATAIN_IPV4_1, MdnsInet4AddressRecord.class),
                        new PacketAndRecordClass(DATAIN_IPV6_1, MdnsInet6AddressRecord.class),
                        new PacketAndRecordClass(DATAIN_PTR_1, MdnsPointerRecord.class),
                        new PacketAndRecordClass(DATAIN_SERVICE_1, MdnsServiceRecord.class),
                        new PacketAndRecordClass(DATAIN_TEXT_1, MdnsTextRecord.class));
        // Create a two identical responses.
        MdnsResponse response = makeMdnsResponse(12L /* time */, DATAIN_SERVICE_NAME_1, recordList);

        doReturn(34L).when(mClock).elapsedRealtime();
        final MdnsResponseDecoder decoder = new MdnsResponseDecoder(mClock, null);
        final byte[] identicalResponse = makeResponsePacket(
                recordList.stream().map(p -> p.packetData).collect(Collectors.toList()));
        final ArraySet<MdnsResponse> changes = decode(
                decoder, identicalResponse, List.of(response));

        // Decoding should not indicate any change.
        assertEquals(0, changes.size());
    }

    private static MdnsResponse makeMdnsResponse(long time, String[] serviceName,
            List<PacketAndRecordClass> responseList) throws IOException {
        final MdnsResponse response = new MdnsResponse(
                time, serviceName, 999 /* interfaceIndex */, mock(Network.class));
        for (PacketAndRecordClass responseData : responseList) {
            DatagramPacket packet =
                    new DatagramPacket(responseData.packetData, responseData.packetData.length);
            MdnsPacketReader reader = new MdnsPacketReader(packet);
            String[] name = reader.readLabels();
            reader.skip(2); // skip record type indication.
            // Apply the right kind of record to the response.
            if (responseData.recordClass == MdnsInet4AddressRecord.class) {
                response.addInet4AddressRecord(new MdnsInet4AddressRecord(name, reader));
            } else if (responseData.recordClass == MdnsInet6AddressRecord.class) {
                response.addInet6AddressRecord(new MdnsInet6AddressRecord(name, reader));
            } else if (responseData.recordClass == MdnsPointerRecord.class) {
                response.addPointerRecord(new MdnsPointerRecord(name, reader));
            } else if (responseData.recordClass == MdnsServiceRecord.class) {
                response.setServiceRecord(new MdnsServiceRecord(name, reader));
            } else if (responseData.recordClass == MdnsTextRecord.class) {
                response.setTextRecord(new MdnsTextRecord(name, reader));
            } else {
                fail("Unsupported/unexpected MdnsRecord subtype used in test - invalid test!");
            }
        }
        return response;
    }

    private static byte[] makeResponsePacket(byte[] responseRecord) throws IOException {
        return makeResponsePacket(List.of(responseRecord));
    }

    private static byte[] makeResponsePacket(List<byte[]> responseRecords) throws IOException {
        final MdnsPacketWriter writer = new MdnsPacketWriter(1500);
        writer.writeUInt16(0); // Transaction ID (advertisement: 0)
        writer.writeUInt16(0x8400); // Flags: response, authoritative
        writer.writeUInt16(0); // questions count
        writer.writeUInt16(responseRecords.size()); // answers count
        writer.writeUInt16(0); // authority entries count
        writer.writeUInt16(0); // additional records count

        for (byte[] record : responseRecords) {
            writer.writeBytes(record);
        }
        final DatagramPacket packet = writer.getPacket(new InetSocketAddress(0 /* port */));
        return Arrays.copyOf(packet.getData(), packet.getLength());
    }


    // This helper class just wraps the data bytes of a response packet with the contained record
    // type.
    // Its only purpose is to make the test code a bit more readable.
    private static class PacketAndRecordClass {
        public final byte[] packetData;
        public final Class<?> recordClass;

        PacketAndRecordClass(byte[] data, Class<?> c) {
            packetData = data;
            recordClass = c;
        }
    }

    private ArraySet<MdnsResponse> decode(MdnsResponseDecoder decoder, byte[] data)
            throws MdnsPacket.ParseException {
        return decode(decoder, data, Collections.emptyList());
    }

    private ArraySet<MdnsResponse> decode(MdnsResponseDecoder decoder, byte[] data,
            Collection<MdnsResponse> existingResponses) throws MdnsPacket.ParseException {
        final MdnsPacket parsedPacket = MdnsResponseDecoder.parseResponse(
                data, data.length, MdnsFeatureFlags.newBuilder().build());
        assertNotNull(parsedPacket);

        return new ArraySet<>(decoder.augmentResponses(parsedPacket,
                existingResponses,
                MdnsSocket.INTERFACE_INDEX_UNSPECIFIED, mock(Network.class)).first);
    }
}