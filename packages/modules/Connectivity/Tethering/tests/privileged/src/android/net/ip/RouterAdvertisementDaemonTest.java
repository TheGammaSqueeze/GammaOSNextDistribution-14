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

package android.net.ip;

import static com.android.net.module.util.NetworkStackConstants.ETHER_HEADER_LEN;
import static com.android.net.module.util.NetworkStackConstants.ETHER_TYPE_IPV6;
import static com.android.net.module.util.NetworkStackConstants.ICMPV6_ND_OPTION_MTU;
import static com.android.net.module.util.NetworkStackConstants.ICMPV6_ND_OPTION_PIO;
import static com.android.net.module.util.NetworkStackConstants.ICMPV6_ND_OPTION_RDNSS;
import static com.android.net.module.util.NetworkStackConstants.ICMPV6_ND_OPTION_SLLA;
import static com.android.net.module.util.NetworkStackConstants.ICMPV6_RA_HEADER_LEN;
import static com.android.net.module.util.NetworkStackConstants.ICMPV6_ROUTER_ADVERTISEMENT;
import static com.android.net.module.util.NetworkStackConstants.IPV6_ADDR_ALL_NODES_MULTICAST;
import static com.android.net.module.util.NetworkStackConstants.IPV6_ADDR_LEN;
import static com.android.net.module.util.NetworkStackConstants.IPV6_HEADER_LEN;
import static com.android.net.module.util.NetworkStackConstants.PIO_FLAG_AUTONOMOUS;
import static com.android.net.module.util.NetworkStackConstants.PIO_FLAG_ON_LINK;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.content.Context;
import android.net.INetd;
import android.net.IpPrefix;
import android.net.MacAddress;
import android.net.ip.RouterAdvertisementDaemon.RaParams;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.util.ArraySet;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.net.module.util.InterfaceParams;
import com.android.net.module.util.Ipv6Utils;
import com.android.net.module.util.Struct;
import com.android.net.module.util.structs.EthernetHeader;
import com.android.net.module.util.structs.Icmpv6Header;
import com.android.net.module.util.structs.Ipv6Header;
import com.android.net.module.util.structs.LlaOption;
import com.android.net.module.util.structs.MtuOption;
import com.android.net.module.util.structs.PrefixInformationOption;
import com.android.net.module.util.structs.RaHeader;
import com.android.net.module.util.structs.RdnssOption;
import com.android.testutils.TapPacketReader;
import com.android.testutils.TapPacketReaderRule;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.ByteBuffer;

@RunWith(AndroidJUnit4.class)
@SmallTest
public final class RouterAdvertisementDaemonTest {
    private static final String TAG = RouterAdvertisementDaemonTest.class.getSimpleName();
    private static final int DATA_BUFFER_LEN = 4096;
    private static final int PACKET_TIMEOUT_MS = 5_000;

    @Rule
    public final TapPacketReaderRule mTetheredReader = new TapPacketReaderRule(
            DATA_BUFFER_LEN, false /* autoStart */);

    private InterfaceParams mTetheredParams;
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private TapPacketReader mTetheredPacketReader;
    private RouterAdvertisementDaemon mRaDaemon;

    private static INetd sNetd;

    @BeforeClass
    public static void setupOnce() {
        final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        final IBinder netdIBinder =
                (IBinder) inst.getContext().getSystemService(Context.NETD_SERVICE);
        sNetd = INetd.Stub.asInterface(netdIBinder);
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mHandlerThread = new HandlerThread(getClass().getSimpleName());
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        setupTapInterfaces();

        // Looper must be prepared here since AndroidJUnitRunner runs tests on separate threads.
        if (Looper.myLooper() == null) Looper.prepare();

        mRaDaemon = new RouterAdvertisementDaemon(mTetheredParams);
        sNetd.networkAddInterface(INetd.LOCAL_NET_ID, mTetheredParams.name);
    }

    @After
    public void tearDown() throws Exception {
        mTetheredReader.stop();
        if (mHandlerThread != null) {
            mHandlerThread.quitSafely();
            mHandlerThread.join(PACKET_TIMEOUT_MS);
        }

        if (mTetheredParams != null) {
            sNetd.networkRemoveInterface(INetd.LOCAL_NET_ID, mTetheredParams.name);
        }
    }

    private void setupTapInterfaces() {
        // Create tethered test iface.
        mTetheredReader.start(mHandler);
        mTetheredParams = InterfaceParams.getByName(mTetheredReader.iface.getInterfaceName());
        assertNotNull(mTetheredParams);
        mTetheredPacketReader = mTetheredReader.getReader();
        mHandler.post(mTetheredPacketReader::start);
    }

    private class TestRaPacket {
        final RaParams mNewParams, mOldParams;

        TestRaPacket(final RaParams oldParams, final RaParams newParams) {
            mOldParams = oldParams;
            mNewParams = newParams;
        }

        public boolean isPacketMatched(final byte[] pkt, boolean multicast) throws Exception {
            if (pkt.length < (ETHER_HEADER_LEN + IPV6_HEADER_LEN + ICMPV6_RA_HEADER_LEN)) {
                return false;
            }
            final ByteBuffer buf = ByteBuffer.wrap(pkt);

            // Parse Ethernet header
            final EthernetHeader ethHdr = Struct.parse(EthernetHeader.class, buf);
            if (ethHdr.etherType != ETHER_TYPE_IPV6) return false;

            // Parse IPv6 header
            final Ipv6Header ipv6Hdr = Struct.parse(Ipv6Header.class, buf);
            assertEquals((ipv6Hdr.vtf >> 28), 6 /* ip version*/);

            final int payLoadLength = pkt.length - ETHER_HEADER_LEN - IPV6_HEADER_LEN;
            assertEquals(payLoadLength, ipv6Hdr.payloadLength);

            // Parse ICMPv6 header
            final Icmpv6Header icmpv6Hdr = Struct.parse(Icmpv6Header.class, buf);
            if (icmpv6Hdr.type != (short) ICMPV6_ROUTER_ADVERTISEMENT) return false;

            // Check whether IPv6 destination address is multicast or unicast
            if (multicast) {
                assertEquals(ipv6Hdr.dstIp, IPV6_ADDR_ALL_NODES_MULTICAST);
            } else {
                // The unicast IPv6 destination address in RA can be either link-local or global
                // IPv6 address. This test only expects link-local address.
                assertTrue(ipv6Hdr.dstIp.isLinkLocalAddress());
            }

            // Parse RA header
            final RaHeader raHdr = Struct.parse(RaHeader.class, buf);
            assertEquals(mNewParams.hopLimit, raHdr.hopLimit);

            while (buf.position() < pkt.length) {
                final int currentPos = buf.position();
                final int type = Byte.toUnsignedInt(buf.get());
                final int length = Byte.toUnsignedInt(buf.get());
                switch (type) {
                    case ICMPV6_ND_OPTION_PIO:
                        // length is 4 because this test only expects one PIO included in the
                        // router advertisement packet.
                        assertEquals(4, length);

                        final ByteBuffer pioBuf = ByteBuffer.wrap(buf.array(), currentPos,
                                Struct.getSize(PrefixInformationOption.class));
                        final PrefixInformationOption pio =
                                Struct.parse(PrefixInformationOption.class, pioBuf);
                        assertEquals((byte) (PIO_FLAG_ON_LINK | PIO_FLAG_AUTONOMOUS), pio.flags);

                        final InetAddress address = InetAddress.getByAddress(pio.prefix);
                        final IpPrefix prefix = new IpPrefix(address, pio.prefixLen);
                        if (mNewParams.prefixes.contains(prefix)) {
                            assertTrue(pio.validLifetime > 0);
                            assertTrue(pio.preferredLifetime > 0);
                        } else if (mOldParams != null && mOldParams.prefixes.contains(prefix)) {
                            assertEquals(0, pio.validLifetime);
                            assertEquals(0, pio.preferredLifetime);
                        } else {
                            fail("Unexpected prefix: " + prefix);
                        }

                        // Move ByteBuffer position to the next option.
                        buf.position(currentPos + Struct.getSize(PrefixInformationOption.class));
                        break;
                    case ICMPV6_ND_OPTION_MTU:
                        assertEquals(1, length);

                        final ByteBuffer mtuBuf = ByteBuffer.wrap(buf.array(), currentPos,
                                Struct.getSize(MtuOption.class));
                        final MtuOption mtu = Struct.parse(MtuOption.class, mtuBuf);
                        assertEquals(mNewParams.mtu, mtu.mtu);

                        // Move ByteBuffer position to the next option.
                        buf.position(currentPos + Struct.getSize(MtuOption.class));
                        break;
                    case ICMPV6_ND_OPTION_RDNSS:
                        final int rdnssHeaderLen = Struct.getSize(RdnssOption.class);
                        final ByteBuffer RdnssBuf = ByteBuffer.wrap(buf.array(), currentPos,
                                rdnssHeaderLen);
                        final RdnssOption rdnss = Struct.parse(RdnssOption.class, RdnssBuf);
                        final String msg =
                                rdnss.lifetime > 0 ? "Unknown dns" : "Unknown deprecated dns";
                        final ArraySet<Inet6Address> dnses =
                                rdnss.lifetime > 0 ? mNewParams.dnses : mOldParams.dnses;
                        assertNotNull(msg, dnses);

                        // Check DNS servers included in this option.
                        buf.position(currentPos + rdnssHeaderLen); // skip the rdnss option header
                        final int numOfDnses = (length - 1) / 2;
                        for (int i = 0; i < numOfDnses; i++) {
                            byte[] rawAddress = new byte[IPV6_ADDR_LEN];
                            buf.get(rawAddress);
                            final Inet6Address dns =
                                    (Inet6Address) InetAddress.getByAddress(rawAddress);
                            if (!dnses.contains(dns)) fail("Unexpected dns: " + dns);
                        }
                        // Unnecessary to move ByteBuffer position here, since the position has been
                        // moved forward correctly after reading DNS servers from ByteBuffer.
                        break;
                    case ICMPV6_ND_OPTION_SLLA:
                        // Do nothing, just move ByteBuffer position to the next option.
                        buf.position(currentPos + Struct.getSize(LlaOption.class));
                        break;
                    default:
                        fail("Unknown RA option type " + type);
                }
            }
            return true;
        }
    }

    private RaParams createRaParams(final String ipv6Address) throws Exception {
        final RaParams params = new RaParams();
        final Inet6Address address = (Inet6Address) InetAddress.getByName(ipv6Address);
        params.dnses.add(address);
        params.prefixes.add(new IpPrefix(address, 64));

        return params;
    }

    private boolean isRaPacket(final TestRaPacket testRa, boolean multicast) throws Exception {
        byte[] packet;
        while ((packet = mTetheredPacketReader.poll(PACKET_TIMEOUT_MS)) != null) {
            if (testRa.isPacketMatched(packet, multicast)) {
                return true;
            }
        }
        return false;
    }

    private void assertUnicastRaPacket(final TestRaPacket testRa) throws Exception {
        assertTrue(isRaPacket(testRa, false /* multicast */));
    }

    private void assertMulticastRaPacket(final TestRaPacket testRa) throws Exception {
        assertTrue(isRaPacket(testRa, true /* multicast */));
    }

    private ByteBuffer createRsPacket(final String srcIp) throws Exception {
        final MacAddress dstMac = MacAddress.fromString("33:33:03:04:05:06");
        final MacAddress srcMac = mTetheredParams.macAddr;
        final ByteBuffer slla = LlaOption.build((byte) ICMPV6_ND_OPTION_SLLA, srcMac);

        return Ipv6Utils.buildRsPacket(srcMac, dstMac, (Inet6Address) InetAddress.getByName(srcIp),
                IPV6_ADDR_ALL_NODES_MULTICAST, slla);
    }

    @Test
    public void testUnSolicitRouterAdvertisement() throws Exception {
        assertTrue(mRaDaemon.start());
        final RaParams params1 = createRaParams("2001:1122:3344::5566");
        mRaDaemon.buildNewRa(null, params1);
        assertMulticastRaPacket(new TestRaPacket(null, params1));

        final RaParams params2 = createRaParams("2006:3344:5566::7788");
        mRaDaemon.buildNewRa(params1, params2);
        assertMulticastRaPacket(new TestRaPacket(params1, params2));
    }

    @Test
    public void testSolicitRouterAdvertisement() throws Exception {
        // Enable IPv6 forwarding is necessary, which makes kernel process RS correctly and
        // create the neighbor entry for peer's link-layer address and IPv6 address. Otherwise,
        // when device receives RS with IPv6 link-local address as source address, it has to
        // initiate the address resolution first before responding the unicast RA.
        sNetd.setProcSysNet(INetd.IPV6, INetd.CONF, mTetheredParams.name, "forwarding", "1");

        assertTrue(mRaDaemon.start());
        final RaParams params1 = createRaParams("2001:1122:3344::5566");
        mRaDaemon.buildNewRa(null, params1);
        assertMulticastRaPacket(new TestRaPacket(null, params1));

        // Add a default route "fe80::/64 -> ::" to local network, otherwise, device will fail to
        // send the unicast RA out due to the ENETUNREACH error(No route to the peer's link-local
        // address is present).
        try {
            sNetd.networkAddRoute(INetd.LOCAL_NET_ID, mTetheredParams.name,
                    "fe80::/64", INetd.NEXTHOP_NONE);
        } catch (RemoteException | ServiceSpecificException e) {
            throw new IllegalStateException(e);
        }

        final ByteBuffer rs = createRsPacket("fe80::1122:3344:5566:7788");
        mTetheredPacketReader.sendResponse(rs);
        assertUnicastRaPacket(new TestRaPacket(null, params1));
    }
}
