/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.networkstack.netlink;

import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED;
import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.util.DataStallUtils.CONFIG_TCP_PACKETS_FAIL_PERCENTAGE;
import static android.net.util.DataStallUtils.DEFAULT_TCP_PACKETS_FAIL_PERCENTAGE;
import static android.os.PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED;
import static android.os.PowerManager.ACTION_DEVICE_LIGHT_IDLE_MODE_CHANGED;
import static android.provider.DeviceConfig.NAMESPACE_CONNECTIVITY;
import static android.system.OsConstants.AF_INET;

import static com.android.net.module.util.NetworkStackConstants.DNS_OVER_TLS_PORT;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.IntDef;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.INetd;
import android.net.InetAddresses;
import android.net.LinkProperties;
import android.net.MarkMaskParcel;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;
import android.util.Log.TerribleFailureHandler;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.modules.utils.build.SdkLevel;
import com.android.net.module.util.netlink.NetlinkUtils;
import com.android.net.module.util.netlink.StructNlMsgHdr;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRule.IgnoreAfter;
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo;

import libcore.util.HexEncoding;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.FileDescriptor;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

// TODO: Add more tests for missing coverage.
@RunWith(AndroidJUnit4.class)
@SmallTest
public class TcpSocketTrackerTest {
    private static final int TEST_BUFFER_SIZE = 1024;
    private static final String DIAG_MSG_HEX =
            // struct nlmsghdr.
            "10000000" +     // length = 16
            "1400" +         // type = SOCK_DIAG_BY_FAMILY
            "0301" +         // flags = NLM_F_REQUEST | NLM_F_DUMP
            "00000000" +     // seqno
            "00000000";      // pid (0 == kernel)
    private static final byte[] SOCK_DIAG_MSG_BYTES =
            HexEncoding.decode(DIAG_MSG_HEX.toCharArray(), false);
    // Hexadecimal representation of a SOCK_DIAG response with tcp info.
    private static final String SOCK_DIAG_TCP_TEST_HEX =
            composeSockDiagTcpHex(5 /* retrans */, 10 /* sent */);
    private static final byte[] SOCK_DIAG_TCP_INET_TEST_BYTES =
            HexEncoding.decode(SOCK_DIAG_TCP_TEST_HEX.toCharArray(), false);
    private static final TcpInfo TEST_TCPINFO =
            new TcpInfo(10 /* segsOut */, 0 /* segsIn */, 5 /* totalRetrans */);
    private static final String NLMSG_DONE_HEX =
            // struct nlmsghdr
            "14000000"     // length = 20
            + "0300"         // type = NLMSG_DONE
            + "0301"         // flags = NLM_F_REQUEST | NLM_F_DUMP
            + "00000000"     // seqno
            + "00000000"     // pid (0 == kernel)
            // struct inet_diag_req_v2
            + "02"           // family = AF_INET
            + "06"           // state
            + "00"           // timer
            + "00";          // retrans
    private static final String TEST_RESPONSE_HEX = SOCK_DIAG_TCP_TEST_HEX + NLMSG_DONE_HEX;
    private static final byte[] TEST_RESPONSE_BYTES =
            HexEncoding.decode(TEST_RESPONSE_HEX.toCharArray(), false);
    private static final int TEST_NETID1 = 0xA85;
    private static final int TEST_NETID2 = 0x1A85;
    private static final int TEST_NETID1_FWMARK = 0x0A85;
    private static final int TEST_NETID2_FWMARK = 0x1A85;
    private static final int NETID_MASK = 0xffff;
    private static final int TEST_UID1 = 1234;
    private static final int TEST_UID2 = TEST_UID1 + 1;
    private static final short TEST_DST_PORT = 29113;
    private static final long TEST_COOKIE1 = 43387759684916L;
    private static final long TEST_COOKIE2 = TEST_COOKIE1 + 1;
    private static final InetAddress TEST_DNS1 = InetAddresses.parseNumericAddress("8.8.8.8");

    private static final NetworkCapabilities CELL_METERED_CAPABILITIES =
            new NetworkCapabilities()
                    .addTransportType(TRANSPORT_CELLULAR)
                    .addCapability(NET_CAPABILITY_INTERNET);

    private static final NetworkCapabilities CELL_NOT_METERED_CAPABILITIES =
            new NetworkCapabilities()
                    .addTransportType(TRANSPORT_CELLULAR)
                    .addCapability(NET_CAPABILITY_INTERNET)
                    .addCapability(NET_CAPABILITY_NOT_METERED);
    @Mock private TcpSocketTracker.Dependencies mDependencies;
    @Mock private INetd mNetd;
    private final Network mNetwork = new Network(TEST_NETID1);
    private final Network mOtherNetwork = new Network(TEST_NETID2);
    private TerribleFailureHandler mOldWtfHandler;
    @Mock private Context mContext;
    @Mock private PowerManager mPowerManager;
    @Mock private ConnectivityManager mCm;

    @Rule
    public final DevSdkIgnoreRule mIgnoreRule = new DevSdkIgnoreRule();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        // Override the default TerribleFailureHandler, as that handler might terminate the process
        // (if we're on an eng build).
        mOldWtfHandler =
                Log.setWtfHandler((tag, what, system) -> Log.e(tag, what.getMessage(), what));
        when(mDependencies.getNetd()).thenReturn(mNetd);
        when(mDependencies.connectToKernel()).thenReturn(new FileDescriptor());
        when(mDependencies.getDeviceConfigPropertyInt(
                eq(NAMESPACE_CONNECTIVITY),
                eq(CONFIG_TCP_PACKETS_FAIL_PERCENTAGE),
                anyInt())).thenReturn(DEFAULT_TCP_PACKETS_FAIL_PERCENTAGE);
        when(mDependencies.shouldDisableInLightDoze(anyBoolean())).thenReturn(true);

        when(mNetd.getFwmarkForNetwork(eq(TEST_NETID1)))
                .thenReturn(makeMarkMaskParcel(NETID_MASK, TEST_NETID1_FWMARK));
        doReturn(mContext).when(mDependencies).getContext();
        doReturn(mPowerManager).when(mContext).getSystemService(PowerManager.class);
        doReturn(mCm).when(mContext).getSystemService(ConnectivityManager.class);
    }

    @After
    public void tearDown() {
        Log.setWtfHandler(mOldWtfHandler);
    }

    private MarkMaskParcel makeMarkMaskParcel(final int mask, final int mark) {
        final MarkMaskParcel parcel = new MarkMaskParcel();
        parcel.mask = mask;
        parcel.mark = mark;
        return parcel;
    }

    private ByteBuffer getByteBufferFromHexString(String hexStr) {
        final byte[] bytes = HexEncoding.decode(hexStr.toCharArray(), false);
        return getByteBuffer(bytes);
    }

    private ByteBuffer getByteBuffer(final byte[] bytes) {
        final ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(ByteOrder.nativeOrder());
        return buffer;
    }

    @Test
    public void testParseSockInfo() {
        final ByteBuffer buffer = getByteBuffer(SOCK_DIAG_TCP_INET_TEST_BYTES);
        final ArrayList<TcpSocketTracker.SocketInfo> infoList = new ArrayList<>();
        final TcpSocketTracker tst = new TcpSocketTracker(mDependencies, mNetwork);
        tst.parseMessage(buffer, AF_INET, infoList, 100L);
        assertEquals(1, infoList.size());
        final TcpSocketTracker.SocketInfo parsed = infoList.get(0);

        assertEquals(parsed.tcpInfo, TEST_TCPINFO);
        assertEquals(parsed.fwmark, 789125);
        assertEquals(parsed.updateTime, 100);
        assertEquals(parsed.ipFamily, AF_INET);
        assertEquals(parsed.uid, TEST_UID1);
        assertEquals(parsed.cookie, TEST_COOKIE1);
        assertEquals(parsed.dstPort, TEST_DST_PORT);
    }

    @Test
    public void testEnoughBytesRemainForValidNlMsg() {
        final ByteBuffer buffer = ByteBuffer.allocate(TEST_BUFFER_SIZE);

        buffer.position(TEST_BUFFER_SIZE - StructNlMsgHdr.STRUCT_SIZE);
        assertTrue(NetlinkUtils.enoughBytesRemainForValidNlMsg(buffer));
        // Remaining buffer size is less than a valid StructNlMsgHdr size.
        buffer.position(TEST_BUFFER_SIZE - StructNlMsgHdr.STRUCT_SIZE + 1);
        assertFalse(NetlinkUtils.enoughBytesRemainForValidNlMsg(buffer));

        buffer.position(TEST_BUFFER_SIZE);
        assertFalse(NetlinkUtils.enoughBytesRemainForValidNlMsg(buffer));
    }

    @Test
    public void testPollSocketsInfo() throws Exception {
        final TcpSocketTracker tst = new TcpSocketTracker(mDependencies, mNetwork);

        // No enough bytes remain for a valid NlMsg.
        final ByteBuffer invalidBuffer = ByteBuffer.allocate(1);
        invalidBuffer.order(ByteOrder.nativeOrder());
        when(mDependencies.recvMessage(any())).thenReturn(invalidBuffer);
        assertTrue(tst.pollSocketsInfo());
        assertEquals(-1, tst.getLatestPacketFailPercentage());
        assertEquals(0, tst.getSentSinceLastRecv());

        // Header only.
        final ByteBuffer headerBuffer = getByteBuffer(SOCK_DIAG_MSG_BYTES);
        when(mDependencies.recvMessage(any())).thenReturn(headerBuffer);
        assertTrue(tst.pollSocketsInfo());
        assertEquals(-1, tst.getLatestPacketFailPercentage());
        assertEquals(0, tst.getSentSinceLastRecv());

        setupNormalTestTcpInfo();
        assertTrue(tst.pollSocketsInfo());

        assertEquals(10, tst.getSentSinceLastRecv());
        assertEquals(50, tst.getLatestPacketFailPercentage());
        assertFalse(tst.isDataStallSuspected());
        // Lower the threshold.
        when(mDependencies.getDeviceConfigPropertyInt(any(), eq(CONFIG_TCP_PACKETS_FAIL_PERCENTAGE),
                anyInt())).thenReturn(40);
        // No device config change. Using cache value.
        assertFalse(tst.isDataStallSuspected());
        // Trigger a config update
        tst.mConfigListener.onPropertiesChanged(null /* properties */);
        assertTrue(tst.isDataStallSuspected());
    }

    @Test
    public void testPollSocketsInfo_ignorePrivateDnsPort() throws Exception {
        final TcpSocketTracker tst = new TcpSocketTracker(mDependencies, mNetwork);
        // Simulate 1 message with data stall happened.
        doReturn(getByteBufferFromHexString(
                        composeSockDiagTcpHex(9, 10) + NLMSG_DONE_HEX))
                .when(mDependencies).recvMessage(any());
        assertTrue(tst.pollSocketsInfo());

        // 9 retrans / 10 sent = 90 percent.
        assertEquals(90, tst.getLatestPacketFailPercentage());
        assertEquals(10, tst.getSentSinceLastRecv());
        assertTrue(tst.isDataStallSuspected());

        // Append another message with private dns port which is generated
        // in opportunistic mode. Also simulated the private dns probe is not finished.
        tst.setOpportunisticMode(true);
        final LinkProperties testLp = new LinkProperties();
        testLp.addDnsServer(TEST_DNS1);
        tst.setLinkProperties(testLp);
        doReturn(getByteBufferFromHexString(composeSockDiagTcpHex(9, 10)
                + composeSockDiagTcpHex(9, 10, DNS_OVER_TLS_PORT, TEST_COOKIE2, TEST_UID1)
                + NLMSG_DONE_HEX))
                .when(mDependencies).recvMessage(any());
        assertTrue(tst.pollSocketsInfo());

        // Verify that when in opportunistic mode, the message with private dns
        // port won't get involved with the calculation.
        // While there is no packet sent in this polling cycle, 0 percentage is expected while the
        // sent counter remains the same.
        assertEquals(0, tst.getLatestPacketFailPercentage());
        assertEquals(10, tst.getSentSinceLastRecv());
        assertFalse(tst.isDataStallSuspected());

        // Verify that when private dns servers are all validated, the message with private dns port
        // will be counted.
        testLp.addValidatedPrivateDnsServer(TEST_DNS1);
        tst.setLinkProperties(testLp);
        doReturn(getByteBufferFromHexString(composeSockDiagTcpHex(10, 12)
                + composeSockDiagTcpHex(11, 12, DNS_OVER_TLS_PORT, TEST_COOKIE2, TEST_UID1)
                + NLMSG_DONE_HEX))
                .when(mDependencies).recvMessage(any());
        assertTrue(tst.pollSocketsInfo());
        // Retrans ( 1 + 2 ) / ( 2 + 2 ) sent = 75 percent.
        assertEquals(75, tst.getLatestPacketFailPercentage());
        assertEquals(14, tst.getSentSinceLastRecv());
        assertFalse(tst.isDataStallSuspected());

        // Verify that when exited opportunistic mode, the message with private dns port will be
        // counted. And the stat is correctly subtracted from the stat ignored in the previous
        // polling cycle.
        tst.setOpportunisticMode(false);
        doReturn(getByteBufferFromHexString(composeSockDiagTcpHex(11, 14)
                + composeSockDiagTcpHex(13, 14, DNS_OVER_TLS_PORT, TEST_COOKIE2, TEST_UID1)
                + NLMSG_DONE_HEX))
                .when(mDependencies).recvMessage(any());
        assertTrue(tst.pollSocketsInfo());
        // Retrans ( 1 + 2 ) / ( 2 + 2 ) sent = 75 percent.
        assertEquals(75, tst.getLatestPacketFailPercentage());
        assertEquals(18, tst.getSentSinceLastRecv());
        assertFalse(tst.isDataStallSuspected());
    }

    // b/326143935 isUidNetworkingBlocked is not supported on pre-U device.
    @IgnoreAfter(Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testPollSocketsInfo_ignoreBlockedUid_featureDisabled_beforeU() throws Exception {
        doTestPollSocketsInfo_ignoreBlockedUid_featureDisabled();
    }

    // b/326143935 isUidNetworkingBlocked is not supported on pre-U device.
    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testPollSocketsInfo_ignoreBlockedUid_featureDisabled_UOrAbove() throws Exception {
        doTestPollSocketsInfo_ignoreBlockedUid_featureDisabled();
        verify(mCm, never()).isUidNetworkingBlocked(anyInt(), anyBoolean());
    }

    private void doTestPollSocketsInfo_ignoreBlockedUid_featureDisabled() throws Exception {
        doReturn(false).when(mDependencies).shouldIgnoreTcpInfoForBlockedUids();
        final TcpSocketTracker tst = new TcpSocketTracker(mDependencies, mNetwork);
        // Simulate 1 message with data stall happened.
        doReturn(getByteBufferFromHexString(
                composeSockDiagTcpHex(4, 10) + NLMSG_DONE_HEX))
                .when(mDependencies).recvMessage(any());
        assertTrue(tst.pollSocketsInfo());
        // 4 retran / 10 sent = 40 percent.
        assertEquals(40, tst.getLatestPacketFailPercentage());
        assertEquals(10, tst.getSentSinceLastRecv());
        assertFalse(tst.isDataStallSuspected());

        // With the feature disabled, append another message with blocked uid, verify the
        // traffic of networking-blocked uid is not filtered.
        doReturn(getByteBufferFromHexString(composeSockDiagTcpHex(9, 10)
                + composeSockDiagTcpHex(5, 10, DNS_OVER_TLS_PORT, TEST_COOKIE2, TEST_UID2)
                + NLMSG_DONE_HEX))
                .when(mDependencies).recvMessage(any());
        assertTrue(tst.pollSocketsInfo());
        // 5 + 5 retrans / 10 sent = 100 percent.
        assertEquals(100, tst.getLatestPacketFailPercentage());
        assertEquals(20, tst.getSentSinceLastRecv());
        assertTrue(tst.isDataStallSuspected());
    }

    // b/326143935 isUidNetworkingBlocked is not supported on pre-U device.
    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testPollSocketsInfo_ignoreBlockedUid_featureEnabled() throws Exception {
        doReturn(true).when(mDependencies).shouldIgnoreTcpInfoForBlockedUids();
        final TcpSocketTracker tst = new TcpSocketTracker(mDependencies, mNetwork);
        tst.setNetworkCapabilities(CELL_NOT_METERED_CAPABILITIES);
        doReturn(true).when(mCm).isUidNetworkingBlocked(TEST_UID2, false /* metered */);
        // With the feature enabled, append another message with blocked uid, verify the
        // traffic of networking-blocked uid is filtered out.
        doReturn(getByteBufferFromHexString(composeSockDiagTcpHex(4, 10)
                + composeSockDiagTcpHex(6, 12, DNS_OVER_TLS_PORT, TEST_COOKIE2, TEST_UID2)
                + NLMSG_DONE_HEX))
                .when(mDependencies).recvMessage(any());
        assertTrue(tst.pollSocketsInfo());
        assertEquals(40, tst.getLatestPacketFailPercentage());
        assertEquals(10, tst.getSentSinceLastRecv());
        assertFalse(tst.isDataStallSuspected());

        // Unblock traffic of the uid, verify the traffic of the uid is not filtered.
        doReturn(false).when(mCm).isUidNetworkingBlocked(TEST_UID2, false /* metered */);
        doReturn(getByteBufferFromHexString(composeSockDiagTcpHex(4, 10)
                + composeSockDiagTcpHex(8, 14, DNS_OVER_TLS_PORT, TEST_COOKIE2, TEST_UID2)
                + NLMSG_DONE_HEX))
                .when(mDependencies).recvMessage(any());
        assertTrue(tst.pollSocketsInfo());
        // Lost 2 / 2 sent = 100 percent.
        assertEquals(100, tst.getLatestPacketFailPercentage());
        assertEquals(12, tst.getSentSinceLastRecv());
        assertTrue(tst.isDataStallSuspected());
    }

    // b/326143935 isUidNetworkingBlocked is not supported on pre-U device.
    @IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testPollSocketsInfo_ignoreBlockedUid_featureEnabled_dataSaver() throws Exception {
        doReturn(true).when(mDependencies).shouldIgnoreTcpInfoForBlockedUids();
        final TcpSocketTracker tst = new TcpSocketTracker(mDependencies, mNetwork);

        tst.setNetworkCapabilities(CELL_NOT_METERED_CAPABILITIES);
        final ByteBuffer mockMessage = getByteBufferFromHexString(composeSockDiagTcpHex(4, 10)
                + NLMSG_DONE_HEX);
        doReturn(mockMessage).when(mDependencies).recvMessage(any());
        assertTrue(tst.pollSocketsInfo());
        verify(mCm).isUidNetworkingBlocked(TEST_UID1, false /* metered */);

        // Verify the metered parameter will be correctly passed to ConnectivityManager.
        tst.setNetworkCapabilities(CELL_METERED_CAPABILITIES);
        mockMessage.rewind(); // Reset read position to 0 since the same buffer is used.
        assertTrue(tst.pollSocketsInfo());
        verify(mCm).isUidNetworkingBlocked(TEST_UID1, true /* metered */);

        // Correctness of the logic which handling different blocked status is
        // verified in other tests, see {@code testPollSocketsInfo_ignoreBlockedUid_featureEnabled}.
    }

    @Test
    public void testTcpInfoParsingWithMultipleMsgs() throws Exception {
        final TcpSocketTracker tst = new TcpSocketTracker(mDependencies, mNetwork);

        // Case 1: A message about 5 sockets, then a message about 2 sockets,
        // then a message about 2 sockets together with DONE
        //
        // Mocking 6 return results for different IP families(3 for IPv6; 3 for Ipv4). Use the same
        // message for different IP families to reduce the complexity.
        doReturn(getByteBufferFromHexString(repeat(composeSockDiagTcpHex(5, 10), 5)),
                getByteBufferFromHexString(repeat(composeSockDiagTcpHex(5, 10), 2)),
                getByteBufferFromHexString(
                        repeat(composeSockDiagTcpHex(5, 10), 2) + NLMSG_DONE_HEX),
                getByteBufferFromHexString(repeat(composeSockDiagTcpHex(5, 10), 5)),
                getByteBufferFromHexString(repeat(composeSockDiagTcpHex(5, 10), 2)),
                getByteBufferFromHexString(
                        repeat(composeSockDiagTcpHex(5, 10), 2) + NLMSG_DONE_HEX))
                .when(mDependencies).recvMessage(any());

        assertTrue(tst.pollSocketsInfo());
        // Verify that code reads all the messages. (3 times for IPv4, 3 times for IPv6)
        verify(mDependencies, times(6)).recvMessage(any());
        // Calculated from totalRetrans / segsout.
        // Note that the counters cannot be verified given that the cookie of the mocked sockets
        // are the same, the latest SocketInfo would overwrite previous reported ones.
        assertEquals(50, tst.getLatestPacketFailPercentage());
        // Lower than the 80% threshold
        assertFalse(tst.isDataStallSuspected());

        // Case 2: A message about 1 socket, then a message about 5 sockets,
        // then a message about 1 socket with DONE.
        // "Sent" increases by 5. No change for lost and retrans.
        //
        // Mocking 6 return results for different IP families(3 for IPv6; 3 for Ipv4). Use the same
        // message for different IP families to reduce the complexity.
        doReturn(getByteBufferFromHexString(composeSockDiagTcpHex(10, 15)),
                getByteBufferFromHexString(repeat(composeSockDiagTcpHex(10, 15), 5)),
                getByteBufferFromHexString(composeSockDiagTcpHex(10, 15) + NLMSG_DONE_HEX),
                getByteBufferFromHexString(composeSockDiagTcpHex(10, 15)),
                getByteBufferFromHexString(repeat(composeSockDiagTcpHex(10, 15), 5)),
                getByteBufferFromHexString(composeSockDiagTcpHex(10, 15) + NLMSG_DONE_HEX))
                .when(mDependencies).recvMessage(any());

        assertTrue(tst.pollSocketsInfo());
        // Not reset mDependencies because it will reset other mocks.
        // Another 3 times for IPv6 and 3 times for IPv4
        verify(mDependencies, times(12)).recvMessage(any());
        // (5 lost + 0 retrans)/5 sent
        assertEquals(100, tst.getLatestPacketFailPercentage());
        assertTrue(tst.isDataStallSuspected());

        // Case 3: A message about 5 sockets, then a message about 1 socket,
        // then a message about 1 socket with DONE.
        // No change for sent, lost and retrans.
        //
        // Mocking 4 return results for different IP families(2 for IPv6; 2 for Ipv4). Use the same
        // message for different IP families to reduce the complexity.
        doReturn(getByteBufferFromHexString(repeat(composeSockDiagTcpHex(10, 15), 5)),
                getByteBufferFromHexString(composeSockDiagTcpHex(10, 15)),
                getByteBufferFromHexString(composeSockDiagTcpHex(10, 15) + NLMSG_DONE_HEX),
                getByteBufferFromHexString(repeat(composeSockDiagTcpHex(10, 15), 5)),
                getByteBufferFromHexString(composeSockDiagTcpHex(10, 15)),
                getByteBufferFromHexString(composeSockDiagTcpHex(10, 15) + NLMSG_DONE_HEX))
                .when(mDependencies).recvMessage(any());

        assertTrue(tst.pollSocketsInfo());
        // Another 3 times for IPv6 and 3 times for IPv4
        verify(mDependencies, times(18)).recvMessage(any());
        // (0 lost + 0 retrans)/0 sent
        assertEquals(0, tst.getLatestPacketFailPercentage());
        // Lower than the 80% threshold
        assertFalse(tst.isDataStallSuspected());

        // Case 4: A message about 8 sockets with DONE.
        // "lost" increases by 3 and "sent" increases by 5
        //
        // Mocking 2 return results for different IP families(1 for IPv6; 1 for Ipv4). Use the same
        // message for different IP families to reduce the complexity.
        doReturn(getByteBufferFromHexString(
                        repeat(composeSockDiagTcpHex(14, 20), 8) + NLMSG_DONE_HEX),
                getByteBufferFromHexString(
                        repeat(composeSockDiagTcpHex(14, 20), 8) + NLMSG_DONE_HEX))
                .when(mDependencies).recvMessage(any());

        assertTrue(tst.pollSocketsInfo());
        // Another 1 time for IPv6 and 1 time for IPv4
        verify(mDependencies, times(20)).recvMessage(any());
        // (4 lost + 0 retrans)/5 sent
        assertEquals(80, tst.getLatestPacketFailPercentage());
        //Reach 80% threshold
        assertTrue(tst.isDataStallSuspected());

        // Case 5: A message about DONE with 2 sockets.
        // No socket information will be parsed though "lost" increases by 6 and "sent"
        // increases by 6.
        //
        // Mocking 2 return results for different IP families(1 for IPv6; 1 for Ipv4). Use the same
        // message for different IP families to reduce the complexity.
        doReturn(getByteBufferFromHexString(
                        NLMSG_DONE_HEX + repeat(composeSockDiagTcpHex(20, 26), 2)),
                getByteBufferFromHexString(
                        NLMSG_DONE_HEX + repeat(composeSockDiagTcpHex(20, 26), 2)))
                .when(mDependencies).recvMessage(any());
        assertTrue(tst.pollSocketsInfo());
        // Another 1 time for IPv6 and 1 time for IPv4
        verify(mDependencies, times(22)).recvMessage(any());
        // (0 lost + 0 retrans)/0 sent.
        // Parsing will be stopped in DONE message. No socket information will be parsed.
        assertEquals(0, tst.getLatestPacketFailPercentage());
        // Lower than the 80% threshold
        assertFalse(tst.isDataStallSuspected());
    }

    private String repeat(String orig, int times) {
        if (SdkLevel.isAtLeastT()) {
            // Only supported from Java 11
            return orig.repeat(times);
        } else {
            String repeated = "";
            for (int i = 0; i < times; i++) {
                repeated += orig;
            }
            return repeated;
        }
    }

    private static String getHexStringFromInt(int v) {
        // Android is always little-endian. Refer to https://developer.android.com/ndk/guides/abis.
        return getHexStringOfSize(v, ByteOrder.nativeOrder(), Integer.BYTES);
    }

    private static String getHexStringFromShort(short v, ByteOrder order) {
        return getHexStringOfSize(v, order, Short.BYTES);
    }

    private static String getHexStringFromLong(long v) {
        // Android is always little-endian. Refer to https://developer.android.com/ndk/guides/abis.
        return getHexStringOfSize(v, ByteOrder.nativeOrder(), Long.BYTES);
    }

    private static String getHexStringOfSize(long v, ByteOrder order, int size) {
        final ByteBuffer bb = ByteBuffer.allocate(size);
        bb.order(order);
        switch (size) {
            case Short.BYTES:
                bb.putShort((short) v);
                break;
            case Integer.BYTES:
                bb.putInt((int) v);
                break;
            case Long.BYTES:
                bb.putLong(v);
                break;
            default:
                throw new IllegalArgumentException("Unsupported size: " + size);
        }
        String s = "";
        for (byte b : bb.array()) {
            s += String.format("%02X", b);
        }
        return s;
    }

    private static String composeSockDiagTcpHex(int retrans, int sent) {
        return composeSockDiagTcpHex(retrans, sent, TEST_DST_PORT, TEST_COOKIE1, TEST_UID1);
    }

    private static String composeSockDiagTcpHex(int retrans, int sent, short dstPort,
            long cookie, int uid) {
        return // struct nlmsghdr.
                "14010000"          // length = 276
                + "1400"            // type = SOCK_DIAG_BY_FAMILY
                + "0301"            // flags = NLM_F_REQUEST | NLM_F_DUMP
                + "00000000"        // seqno
                + "00000000"        // pid (0 == kernel)
                // struct inet_diag_req_v2
                + "02"              // family = AF_INET
                + "06"              // state
                + "00"              // timer
                + "00"              // retrans
                // inet_diag_sockid: ports and addresses are always in big endian,
                // see StructInetDiagSockId.
                + "DEA5"                                                // idiag_sport = 56997
                + getHexStringFromShort(dstPort, ByteOrder.BIG_ENDIAN)  // idiag_dport
                + "0a006402000000000000000000000000"                    // idiag_src = 10.0.100.2
                + "08080808000000000000000000000000"                    // idiag_dst = 8.8.8.8
                + "00000000"                                            // idiag_if
                + getHexStringFromLong(cookie)                          // idiag_cookie
                + "00000000"                                            // idiag_expires
                + "00000000"                                            // idiag_rqueue
                + "00000000"                                            // idiag_wqueue
                + getHexStringFromInt(uid)                              // idiag_uid
                + "00000000"                                            // idiag_inode
                // rtattr
                + "0500"            // len = 5
                + "0800"            // type = 8
                + "00000000"        // data
                + "0800"            // len = 8
                + "0F00"            // type = 15(INET_DIAG_MARK)
                + "850A0C00"        // data, socket mark=789125
                + "AC00"            // len = 172
                + "0200"            // type = 2(INET_DIAG_INFO)
                // tcp_info
                + "01"              // state = TCP_ESTABLISHED
                + "00"              // ca_state = TCP_CA_OPEN
                + "05"              // retransmits = 5
                + "00"              // probes = 0
                + "00"              // backoff = 0
                + "07"              // option = TCPI_OPT_WSCALE|TCPI_OPT_SACK|TCPI_OPT_TIMESTAMPS
                + "88"              // wscale = 8
                + "00"              // delivery_rate_app_limited = 0
                + "4A911B00"        // rto = 1806666
                + "00000000"        // ato = 0
                + "2E050000"        // sndMss = 1326
                + "18020000"        // rcvMss = 536
                + "00000000"        // unsacked = 0
                + "00000000"        // acked = 0
                + "00000000"        // lost
                + "00000000"        // retrans = 0
                + "00000000"        // fackets = 0
                + "BB000000"        // lastDataSent = 187
                + "00000000"        // lastAckSent = 0
                + "BB000000"        // lastDataRecv = 187
                + "BB000000"        // lastDataAckRecv = 187
                + "DC050000"        // pmtu = 1500
                + "30560100"        // rcvSsthresh = 87600
                + "3E2C0900"        // rttt = 601150
                + "1F960400"        // rttvar = 300575
                + "78050000"        // sndSsthresh = 1400
                + "0A000000"        // sndCwnd = 10
                + "A8050000"        // advmss = 1448
                + "03000000"        // reordering = 3
                + "00000000"        // rcvrtt = 0
                + "30560100"        // rcvspace = 87600
                + getHexStringFromInt(retrans)   // totalRetrans
                + "53AC000000000000"    // pacingRate = 44115
                + "FFFFFFFFFFFFFFFF"    // maxPacingRate = 18446744073709551615
                + "0100000000000000"    // bytesAcked = 1
                + "0000000000000000"    // bytesReceived = 0
                + getHexStringFromInt(sent) // SegsOut
                + "00000000"        // SegsIn = 0
                + "00000000"        // NotSentBytes = 0
                + "3E2C0900"        // minRtt = 601150
                + "00000000"        // DataSegsIn = 0
                + "00000000"        // DataSegsOut = 0
                + "0000000000000000"; // deliverRate = 0
    }

    private static final int DEEP_DOZE = 0;
    private static final int LIGHT_DOZE = 1;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
            DEEP_DOZE,
            LIGHT_DOZE
    })
    private @interface DozeModeType {}

    @Test
    public void testTcpInfoParsingWithDozeMode_enabled() throws Exception {
        doReturn(false).when(mDependencies).shouldIgnoreTcpInfoForBlockedUids();
        doReturn(false).when(mDependencies).shouldDisableInLightDoze(anyBoolean());
        doTestTcpInfoDisableParsingWithDozeMode(DEEP_DOZE, true /* featureEnabled */);
    }

    // Ignore blocked uids is supported on T. Thus, for pre-T device this feature is always
    // needed since there is no replacement.
    @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    @Test
    public void testTcpInfoParsingWithDozeMode_disabled() throws Exception {
        doReturn(true).when(mDependencies).shouldIgnoreTcpInfoForBlockedUids();
        doReturn(false).when(mDependencies).shouldDisableInLightDoze(anyBoolean());
        doTestTcpInfoDisableParsingWithDozeMode(DEEP_DOZE, false /* featureEnabled */);
    }

    @Test @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testTcpInfoDisableParsingWithLightDozeMode_enabled() throws Exception {
        doReturn(true).when(mDependencies).shouldDisableInLightDoze(anyBoolean());
        doTestTcpInfoDisableParsingWithDozeMode(LIGHT_DOZE, true /* featureEnabled */);
    }

    @Test @IgnoreUpTo(Build.VERSION_CODES.S_V2)
    public void testTcpInfoDisableParsingWithLightDozeMode_disabled() throws Exception {
        doReturn(false).when(mDependencies).shouldDisableInLightDoze(anyBoolean());
        doTestTcpInfoDisableParsingWithDozeMode(LIGHT_DOZE, false /* featureEnabled */);
    }

    private void doTestTcpInfoDisableParsingWithDozeMode(@DozeModeType int dozeModeType,
            boolean featureEnabled) throws Exception {
        final TcpSocketTracker tst = new TcpSocketTracker(mDependencies, mNetwork);
        tst.setNetworkCapabilities(CELL_NOT_METERED_CAPABILITIES);
        final ArgumentCaptor<BroadcastReceiver> receiverCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);

        // Enable doze mode with 1 netlink message.
        verify(mDependencies).addDeviceIdleReceiver(receiverCaptor.capture(),
                anyBoolean(), anyBoolean());
        final BroadcastReceiver receiver = receiverCaptor.getValue();
        if (dozeModeType == DEEP_DOZE) {
            doReturn(true).when(mPowerManager).isDeviceIdleMode();
            receiver.onReceive(mContext, new Intent(ACTION_DEVICE_IDLE_MODE_CHANGED));
        } else {
            doReturn(true).when(mPowerManager).isDeviceLightIdleMode();
            receiver.onReceive(mContext, new Intent(ACTION_DEVICE_LIGHT_IDLE_MODE_CHANGED));
        }
        doReturn(getByteBufferFromHexString(composeSockDiagTcpHex(9, 10)
                + NLMSG_DONE_HEX)).when(mDependencies).recvMessage(any());

        if (!featureEnabled) {
            // Verify TcpInfo is still processed.
            assertTrue(tst.pollSocketsInfo());
            assertEquals(10, tst.getSentSinceLastRecv());
            // Lost 4 + default 5 retrans / 10 sent.
            assertEquals(90, tst.getLatestPacketFailPercentage());
            assertTrue(tst.isDataStallSuspected());
            return;
        }

        // Verify counters are not updated.
        assertFalse(tst.pollSocketsInfo());
        assertEquals(0, tst.getSentSinceLastRecv());
        // -1 if not enough packets.
        assertEquals(-1, tst.getLatestPacketFailPercentage());
        assertFalse(tst.isDataStallSuspected());

        // Disable deep/light doze mode, verify polling are processed and counters are updated.
        if (dozeModeType == DEEP_DOZE) {
            doReturn(false).when(mPowerManager).isDeviceIdleMode();
            receiver.onReceive(mContext, new Intent(ACTION_DEVICE_IDLE_MODE_CHANGED));
        } else {
            doReturn(false).when(mPowerManager).isDeviceLightIdleMode();
            receiver.onReceive(mContext, new Intent(ACTION_DEVICE_LIGHT_IDLE_MODE_CHANGED));
        }
        assertTrue(tst.pollSocketsInfo());
        assertEquals(10, tst.getSentSinceLastRecv());
        // Lost 4 + default 5 retrans / 10 sent.
        assertEquals(90, tst.getLatestPacketFailPercentage());
        assertTrue(tst.isDataStallSuspected());
    }

    private void setupNormalTestTcpInfo() throws Exception {
        final ByteBuffer tcpBufferV6 = getByteBuffer(TEST_RESPONSE_BYTES);
        final ByteBuffer tcpBufferV4 = getByteBuffer(TEST_RESPONSE_BYTES);
        doReturn(tcpBufferV6, tcpBufferV4).when(mDependencies).recvMessage(any());
    }

    private static final String BAD_DIAG_MSG_HEX =
        // struct nlmsghdr.
            "00000058"      // length = 1476395008
            + "1400"         // type = SOCK_DIAG_BY_FAMILY
            + "0301"         // flags = NLM_F_REQUEST | NLM_F_DUMP
            + "00000000"     // seqno
            + "00000000"     // pid (0 == kernel)
            // struct inet_diag_req_v2
            + "02"           // family = AF_INET
            + "06"           // state
            + "00"           // timer
            + "00"           // retrans
            // inet_diag_sockid
            + "DEA5"         // idiag_sport = 42462
            + "71B9"         // idiag_dport = 47473
            + "0a006402000000000000000000000000" // idiag_src = 10.0.100.2
            + "08080808000000000000000000000000" // idiag_dst = 8.8.8.8
            + "00000000"    // idiag_if
            + "34ED000076270000" // idiag_cookie = 43387759684916
            + "00000000"    // idiag_expires
            + "00000000"    // idiag_rqueue
            + "00000000"    // idiag_wqueue
            + "00000000"    // idiag_uid
            + "00000000";    // idiag_inode
    private static final byte[] BAD_SOCK_DIAG_MSG_BYTES =
        HexEncoding.decode(BAD_DIAG_MSG_HEX.toCharArray(), false);

    @Test
    public void testPollSocketsInfo_BadFormat() throws Exception {
        final TcpSocketTracker tst = new TcpSocketTracker(mDependencies, mNetwork);
        setupNormalTestTcpInfo();
        assertTrue(tst.pollSocketsInfo());
        assertEquals(10, tst.getSentSinceLastRecv());
        assertEquals(50, tst.getLatestPacketFailPercentage());

        final ByteBuffer badTcpBufferV6 = getByteBuffer(BAD_SOCK_DIAG_MSG_BYTES);
        final ByteBuffer badTcpBufferV4 = getByteBuffer(BAD_SOCK_DIAG_MSG_BYTES);
        doReturn(badTcpBufferV6, badTcpBufferV4).when(mDependencies).recvMessage(any());
        assertTrue(tst.pollSocketsInfo());
        // Expect no additional packets, so still 10.
        assertEquals(10, tst.getSentSinceLastRecv());
        // Expect to reset to 0.
        assertEquals(0, tst.getLatestPacketFailPercentage());
    }

    @Test
    public void testUnMatchNetwork() throws Exception {
        when(mNetd.getFwmarkForNetwork(eq(TEST_NETID2)))
                .thenReturn(makeMarkMaskParcel(NETID_MASK, TEST_NETID2_FWMARK));
        final TcpSocketTracker tst = new TcpSocketTracker(mDependencies, mOtherNetwork);
        setupNormalTestTcpInfo();
        assertTrue(tst.pollSocketsInfo());

        assertEquals(0, tst.getSentSinceLastRecv());
        assertEquals(-1, tst.getLatestPacketFailPercentage());
        assertFalse(tst.isDataStallSuspected());
    }
}
