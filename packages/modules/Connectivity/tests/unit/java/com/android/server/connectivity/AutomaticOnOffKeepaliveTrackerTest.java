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

package com.android.server.connectivity;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.net.ConnectivityManager.TYPE_MOBILE;
import static android.net.NetworkAgent.CMD_STOP_SOCKET_KEEPALIVE;
import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static com.android.server.connectivity.AutomaticOnOffKeepaliveTracker.METRICS_COLLECTION_DURATION_MS;
import static com.android.testutils.HandlerUtils.visibleOnHandlerThread;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.ignoreStubs;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.app.AlarmManager;
import android.content.Context;
import android.content.res.Resources;
import android.net.INetd;
import android.net.ISocketKeepaliveCallback;
import android.net.InetAddresses;
import android.net.KeepalivePacketData;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.MarkMaskParcel;
import android.net.NattKeepalivePacketData;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.SocketKeepalive;
import android.net.TcpKeepalivePacketData;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.telephony.SubscriptionManager;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.filters.SmallTest;
import com.android.internal.util.IndentingPrintWriter;
import com.android.server.connectivity.AutomaticOnOffKeepaliveTracker.AutomaticOnOffKeepalive;
import com.android.server.connectivity.KeepaliveTracker.KeepaliveInfo;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRunner;
import com.android.testutils.HandlerUtils;
import java.io.FileDescriptor;
import java.io.StringWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import libcore.util.HexEncoding;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(DevSdkIgnoreRunner.class)
@SmallTest
@DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
public class AutomaticOnOffKeepaliveTrackerTest {
    private static final String TAG = AutomaticOnOffKeepaliveTrackerTest.class.getSimpleName();
    private static final int TEST_SLOT = 1;
    private static final int TEST_NETID = 0xA85;
    private static final int TEST_NETID_FWMARK = 0x0A85;
    private static final int OTHER_NETID = 0x1A85;
    private static final int NETID_MASK = 0xffff;
    private static final int TIMEOUT_MS = 30_000;
    private static final int MOCK_RESOURCE_ID = 5;
    private static final int TEST_KEEPALIVE_INTERVAL_SEC = 10;
    private static final int TEST_KEEPALIVE_INVALID_INTERVAL_SEC = 9;
    private static final byte[] V4_SRC_ADDR = new byte[] { (byte) 192, 0, 0, (byte) 129 };
    private static final String TEST_V4_IFACE = "v4-testIface";
    private AutomaticOnOffKeepaliveTracker mAOOKeepaliveTracker;
    private HandlerThread mHandlerThread;

    @Mock INetd mNetd;
    @Mock AutomaticOnOffKeepaliveTracker.Dependencies mDependencies;
    @Mock Context mCtx;
    @Mock AlarmManager mAlarmManager;
    @Mock NetworkAgentInfo mNai;
    @Mock SubscriptionManager mSubscriptionManager;
    @Mock KeepaliveTracker.Dependencies mKeepaliveTrackerDeps;
    KeepaliveStatsTracker mKeepaliveStatsTracker;
    TestKeepaliveTracker mKeepaliveTracker;
    AOOTestHandler mTestHandler;
    TestTcpKeepaliveController mTcpController;

    // Hexadecimal representation of a SOCK_DIAG response with tcp info.
    private static final String SOCK_DIAG_TCP_INET_HEX =
            // struct nlmsghdr.
            "14010000"        // length = 276
            + "1400"            // type = SOCK_DIAG_BY_FAMILY
            + "0301"            // flags = NLM_F_REQUEST | NLM_F_DUMP
            + "00000000"        // seqno
            + "00000000"        // pid (0 == kernel)
            // struct inet_diag_req_v2
            + "02"              // family = AF_INET
            + "06"              // state
            + "00"              // timer
            + "00"              // retrans
            // inet_diag_sockid
            + "DEA5"            // idiag_sport = 42462
            + "71B9"            // idiag_dport = 47473
            + "0a006402000000000000000000000000" // idiag_src = 10.0.100.2
            + "08080808000000000000000000000000" // idiag_dst = 8.8.8.8
            + "00000000"            // idiag_if
            + "34ED000076270000"    // idiag_cookie = 43387759684916
            + "00000000"            // idiag_expires
            + "00000000"            // idiag_rqueue
            + "00000000"            // idiag_wqueue
            + "39300000"            // idiag_uid = 12345
            + "00000000"            // idiag_inode
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
            + "01"               // state = TCP_ESTABLISHED
            + "00"               // ca_state = TCP_CA_OPEN
            + "05"               // retransmits = 5
            + "00"               // probes = 0
            + "00"               // backoff = 0
            + "07"               // option = TCPI_OPT_WSCALE|TCPI_OPT_SACK|TCPI_OPT_TIMESTAMPS
            + "88"               // wscale = 8
            + "00"               // delivery_rate_app_limited = 0
            + "4A911B00"         // rto = 1806666
            + "00000000"         // ato = 0
            + "2E050000"         // sndMss = 1326
            + "18020000"         // rcvMss = 536
            + "00000000"         // unsacked = 0
            + "00000000"         // acked = 0
            + "00000000"         // lost = 0
            + "00000000"         // retrans = 0
            + "00000000"         // fackets = 0
            + "BB000000"         // lastDataSent = 187
            + "00000000"         // lastAckSent = 0
            + "BB000000"         // lastDataRecv = 187
            + "BB000000"         // lastDataAckRecv = 187
            + "DC050000"         // pmtu = 1500
            + "30560100"         // rcvSsthresh = 87600
            + "3E2C0900"         // rttt = 601150
            + "1F960400"         // rttvar = 300575
            + "78050000"         // sndSsthresh = 1400
            + "0A000000"         // sndCwnd = 10
            + "A8050000"         // advmss = 1448
            + "03000000"         // reordering = 3
            + "00000000"         // rcvrtt = 0
            + "30560100"         // rcvspace = 87600
            + "00000000"         // totalRetrans = 0
            + "53AC000000000000"     // pacingRate = 44115
            + "FFFFFFFFFFFFFFFF"     // maxPacingRate = 18446744073709551615
            + "0100000000000000"     // bytesAcked = 1
            + "0000000000000000"     // bytesReceived = 0
            + "0A000000"         // SegsOut = 10
            + "00000000"         // SegsIn = 0
            + "00000000"         // NotSentBytes = 0
            + "3E2C0900"         // minRtt = 601150
            + "00000000"         // DataSegsIn = 0
            + "00000000"         // DataSegsOut = 0
            + "0000000000000000"; // deliverRate = 0
    private static final String SOCK_DIAG_NO_TCP_INET_HEX =
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
    private static final byte[] SOCK_DIAG_NO_TCP_INET_BYTES =
            HexEncoding.decode(SOCK_DIAG_NO_TCP_INET_HEX.toCharArray(), false);
    private static final String TEST_RESPONSE_HEX =
            SOCK_DIAG_TCP_INET_HEX + SOCK_DIAG_NO_TCP_INET_HEX;
    private static final byte[] TEST_RESPONSE_BYTES =
            HexEncoding.decode(TEST_RESPONSE_HEX.toCharArray(), false);

    private static class TestKeepaliveInfo {
        private static List<Socket> sOpenSockets = new ArrayList<>();

        public static void closeAllSockets() throws Exception {
            for (final Socket socket : sOpenSockets) {
                socket.close();
            }
            sOpenSockets.clear();
        }

        public final Socket socket;
        public final Binder binder;
        public final FileDescriptor fd;
        public final ISocketKeepaliveCallback socketKeepaliveCallback;
        public final Network underpinnedNetwork;
        public final KeepalivePacketData kpd;

        TestKeepaliveInfo(KeepalivePacketData kpd) throws Exception {
            this.kpd = kpd;
            socket = new Socket();
            socket.bind(null);
            sOpenSockets.add(socket);
            fd = socket.getFileDescriptor$();

            binder = new Binder();
            socketKeepaliveCallback = mock(ISocketKeepaliveCallback.class);
            doReturn(binder).when(socketKeepaliveCallback).asBinder();
            underpinnedNetwork = mock(Network.class);
        }
    }

    private class TestKeepaliveTracker extends KeepaliveTracker {
        private KeepaliveInfo mKi;

        TestKeepaliveTracker(@NonNull final Context context, @NonNull final Handler handler,
                @NonNull final TcpKeepaliveController tcpController) {
            super(context, handler, tcpController, mKeepaliveTrackerDeps);
        }

        public void setReturnedKeepaliveInfo(@NonNull final KeepaliveInfo ki) {
            mKi = ki;
        }

        @NonNull
        @Override
        public KeepaliveInfo makeNattKeepaliveInfo(@Nullable final NetworkAgentInfo nai,
                @Nullable final FileDescriptor fd, final int intervalSeconds,
                @NonNull final ISocketKeepaliveCallback cb, @NonNull final String srcAddrString,
                final int srcPort,
                @NonNull final String dstAddrString, final int dstPort) {
            if (null == mKi) {
                throw new IllegalStateException("Must call setReturnedKeepaliveInfo");
            }
            return mKi;
        }

        @NonNull
        @Override
        public KeepaliveInfo makeTcpKeepaliveInfo(@Nullable final NetworkAgentInfo nai,
                @Nullable final FileDescriptor fd, final int intervalSeconds,
                @NonNull final ISocketKeepaliveCallback cb) {
            if (null == mKi) {
                throw new IllegalStateException("Please call `setReturnedKeepaliveInfo`"
                        + " before makeTcpKeepaliveInfo is called");
            }
            return mKi;
        }
    }

    private static class TestTcpKeepaliveController extends TcpKeepaliveController {
        TestTcpKeepaliveController(final Handler connectivityServiceHandler) {
            super(connectivityServiceHandler);
        }
    }

    private <T> void mockService(String serviceName, Class<T> serviceClass, T service) {
        doReturn(serviceName).when(mCtx).getSystemServiceName(serviceClass);
        doReturn(service).when(mCtx).getSystemService(serviceName);
        if (mCtx.getSystemService(serviceClass) == null) {
            // Test is using mockito-extended
            doCallRealMethod().when(mCtx).getSystemService(serviceClass);
        }
    }

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        mockService(Context.TELEPHONY_SUBSCRIPTION_SERVICE, SubscriptionManager.class,
                mSubscriptionManager);

        mNai.networkCapabilities =
                new NetworkCapabilities.Builder().addTransportType(TRANSPORT_CELLULAR).build();
        mNai.networkInfo = new NetworkInfo(TYPE_MOBILE, 0 /* subtype */, "LTE", "LTE");
        mNai.networkInfo.setDetailedState(
                NetworkInfo.DetailedState.CONNECTED, "test reason", "test extra info");
        doReturn(new Network(TEST_NETID)).when(mNai).network();
        mNai.linkProperties = new LinkProperties();
        doReturn(null).when(mNai).translateV4toClatV6(any());
        doReturn(null).when(mNai).getClatv6SrcAddress();

        doReturn(PERMISSION_GRANTED).when(mCtx).checkPermission(any() /* permission */,
                anyInt() /* pid */, anyInt() /* uid */);
        ConnectivityResources.setResourcesContextForTest(mCtx);
        final Resources mockResources = mock(Resources.class);
        doReturn(mockResources).when(mCtx).getResources();
        doReturn(mNetd).when(mDependencies).getNetd();
        doReturn(mAlarmManager).when(mDependencies).getAlarmManager(any());
        doReturn(makeMarkMaskParcel(NETID_MASK, TEST_NETID_FWMARK)).when(mNetd)
                .getFwmarkForNetwork(TEST_NETID);

        doNothing().when(mDependencies).sendRequest(any(), any());
        doReturn(true).when(mKeepaliveTrackerDeps).isAddressTranslationEnabled(mCtx);
        doReturn(new ConnectivityResources(mCtx)).when(mKeepaliveTrackerDeps)
                .createConnectivityResources(mCtx);
        doReturn(new int[] {3, 0, 0, 3}).when(mKeepaliveTrackerDeps).getSupportedKeepalives(mCtx);

        mHandlerThread = new HandlerThread("KeepaliveTrackerTest");
        mHandlerThread.start();
        mTestHandler = new AOOTestHandler(mHandlerThread.getLooper());
        mTcpController = new TestTcpKeepaliveController(mTestHandler);
        mKeepaliveTracker = new TestKeepaliveTracker(mCtx, mTestHandler, mTcpController);
        mKeepaliveStatsTracker = spy(new KeepaliveStatsTracker(mCtx, mTestHandler));
        doReturn(mKeepaliveTracker).when(mDependencies).newKeepaliveTracker(mCtx, mTestHandler);
        doReturn(mKeepaliveStatsTracker)
                .when(mDependencies)
                .newKeepaliveStatsTracker(mCtx, mTestHandler);

        doReturn(true).when(mDependencies).isTetheringFeatureNotChickenedOut(any());
        doReturn(0L).when(mDependencies).getElapsedRealtime();
        mAOOKeepaliveTracker =
                new AutomaticOnOffKeepaliveTracker(mCtx, mTestHandler, mDependencies);
    }

    @After
    public void teardown() throws Exception {
        TestKeepaliveInfo.closeAllSockets();
        if (mHandlerThread != null) {
            mHandlerThread.quitSafely();
            mHandlerThread.join();
        }
    }

    private final class AOOTestHandler extends Handler {
        public AutomaticOnOffKeepaliveTracker.AutomaticOnOffKeepalive mLastAutoKi = null;

        AOOTestHandler(@NonNull final Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull final Message msg) {
            switch (msg.what) {
                case AutomaticOnOffKeepaliveTracker.CMD_REQUEST_START_KEEPALIVE:
                    Log.d(TAG, "Test handler received CMD_REQUEST_START_KEEPALIVE : " + msg);
                    mAOOKeepaliveTracker.handleStartKeepalive(msg);
                    break;
                case AutomaticOnOffKeepaliveTracker.CMD_MONITOR_AUTOMATIC_KEEPALIVE:
                    Log.d(TAG, "Test handler received CMD_MONITOR_AUTOMATIC_KEEPALIVE : " + msg);
                    mLastAutoKi = mAOOKeepaliveTracker.getKeepaliveForBinder((IBinder) msg.obj);
                    break;
                case CMD_STOP_SOCKET_KEEPALIVE:
                    Log.d(TAG, "Test handler received CMD_STOP_SOCKET_KEEPALIVE : " + msg);
                    mLastAutoKi = mAOOKeepaliveTracker.getKeepaliveForBinder((IBinder) msg.obj);
                    if (mLastAutoKi == null) {
                        fail("Attempt to stop an already stopped keepalive");
                    }
                    mAOOKeepaliveTracker.handleStopKeepalive(mLastAutoKi, msg.arg2);
                    break;
            }
        }
    }

    @Test
    public void testIsAnyTcpSocketConnected_runOnNonHandlerThread() throws Exception {
        setupResponseWithSocketExisting();
        assertThrows(IllegalStateException.class,
                () -> mAOOKeepaliveTracker.isAnyTcpSocketConnected(TEST_NETID));
    }

    @Test
    public void testIsAnyTcpSocketConnected_withTargetNetId() throws Exception {
        setupResponseWithSocketExisting();
        assertTrue(visibleOnHandlerThread(mTestHandler,
                () -> mAOOKeepaliveTracker.isAnyTcpSocketConnected(TEST_NETID)));
    }

    @Test
    public void testIsAnyTcpSocketConnected_withIncorrectNetId() throws Exception {
        setupResponseWithSocketExisting();
        assertFalse(visibleOnHandlerThread(mTestHandler,
                () -> mAOOKeepaliveTracker.isAnyTcpSocketConnected(OTHER_NETID)));
    }

    @Test
    public void testIsAnyTcpSocketConnected_noSocketExists() throws Exception {
        setupResponseWithoutSocketExisting();
        assertFalse(visibleOnHandlerThread(mTestHandler,
                () -> mAOOKeepaliveTracker.isAnyTcpSocketConnected(TEST_NETID)));
    }

    private void triggerEventKeepalive(int slot, int reason) {
        visibleOnHandlerThread(
                mTestHandler,
                () -> mAOOKeepaliveTracker.handleEventSocketKeepalive(mNai, slot, reason));
    }

    private TestKeepaliveInfo doStartNattKeepalive(int intervalSeconds) throws Exception {
        final InetAddress srcAddress = InetAddress.getByAddress(V4_SRC_ADDR);
        final int srcPort = 12345;
        final InetAddress dstAddress = InetAddress.getByAddress(new byte[] {8, 8, 8, 8});
        final int dstPort = 12345;

        mNai.linkProperties.addLinkAddress(new LinkAddress(srcAddress, 24));

        final NattKeepalivePacketData kpd = new NattKeepalivePacketData(srcAddress, srcPort,
                dstAddress, dstPort, new byte[] {1});

        final TestKeepaliveInfo testInfo = new TestKeepaliveInfo(kpd);

        final KeepaliveInfo ki = mKeepaliveTracker.new KeepaliveInfo(
                testInfo.socketKeepaliveCallback, mNai, kpd, intervalSeconds,
                KeepaliveInfo.TYPE_NATT, testInfo.fd);
        mKeepaliveTracker.setReturnedKeepaliveInfo(ki);

        mAOOKeepaliveTracker.startNattKeepalive(mNai, testInfo.fd, intervalSeconds,
                testInfo.socketKeepaliveCallback, srcAddress.toString(), srcPort,
                dstAddress.toString(), dstPort, true /* automaticOnOffKeepalives */,
                testInfo.underpinnedNetwork);
        HandlerUtils.waitForIdle(mTestHandler, TIMEOUT_MS);

        return testInfo;
    }

    private TestKeepaliveInfo doStartNattKeepalive() throws Exception {
        return doStartNattKeepalive(TEST_KEEPALIVE_INTERVAL_SEC);
    }

    private void doPauseKeepalive(AutomaticOnOffKeepalive autoKi) throws Exception {
        setupResponseWithoutSocketExisting();
        visibleOnHandlerThread(
                mTestHandler,
                () -> mAOOKeepaliveTracker.handleMonitorAutomaticKeepalive(autoKi, TEST_NETID));
    }

    private void doResumeKeepalive(AutomaticOnOffKeepalive autoKi) throws Exception {
        setupResponseWithSocketExisting();
        visibleOnHandlerThread(
                mTestHandler,
                () -> mAOOKeepaliveTracker.handleMonitorAutomaticKeepalive(autoKi, TEST_NETID));
    }

    private void doStopKeepalive(AutomaticOnOffKeepalive autoKi) throws Exception {
        visibleOnHandlerThread(
                mTestHandler,
                () -> mAOOKeepaliveTracker.handleStopKeepalive(autoKi, SocketKeepalive.SUCCESS));
    }

    @Test
    public void testAlarm() throws Exception {
        // Mock elapsed real time to verify the alarm timer.
        final long time = SystemClock.elapsedRealtime();
        doReturn(time).when(mDependencies).getElapsedRealtime();
        final TestKeepaliveInfo testInfo = doStartNattKeepalive();

        final ArgumentCaptor<AlarmManager.OnAlarmListener> listenerCaptor =
                ArgumentCaptor.forClass(AlarmManager.OnAlarmListener.class);
        // The alarm timer should be smaller than the keepalive delay. Verify the alarm trigger time
        // is higher than base time but smaller than the keepalive delay.
        verify(mAlarmManager).setExact(eq(AlarmManager.ELAPSED_REALTIME),
                longThat(t -> t > time + 1000L && t < time + TEST_KEEPALIVE_INTERVAL_SEC * 1000L),
                any() /* tag */, listenerCaptor.capture(), eq(mTestHandler));
        final AlarmManager.OnAlarmListener listener = listenerCaptor.getValue();

        // For realism, the listener should be posted on the handler
        visibleOnHandlerThread(mTestHandler, () -> listener.onAlarm());
        // Wait for the message posted by the listener to be processed.
        HandlerUtils.waitForIdle(mTestHandler, TIMEOUT_MS);

        assertNotNull(mTestHandler.mLastAutoKi);
        assertEquals(testInfo.socketKeepaliveCallback, mTestHandler.mLastAutoKi.getCallback());
        assertEquals(testInfo.underpinnedNetwork, mTestHandler.mLastAutoKi.getUnderpinnedNetwork());
    }

    @Test
    public void testAlarm_writeMetrics() throws Exception {
        final ArgumentCaptor<AlarmManager.OnAlarmListener> listenerCaptor =
                ArgumentCaptor.forClass(AlarmManager.OnAlarmListener.class);

        // First AlarmManager.set call from the constructor.
        verify(mAlarmManager).set(eq(AlarmManager.ELAPSED_REALTIME_WAKEUP),
                eq(METRICS_COLLECTION_DURATION_MS), any() /* tag */, listenerCaptor.capture(),
                eq(mTestHandler));

        final AlarmManager.OnAlarmListener listener = listenerCaptor.getValue();

        doReturn(METRICS_COLLECTION_DURATION_MS).when(mDependencies).getElapsedRealtime();
        // For realism, the listener should be posted on the handler
        visibleOnHandlerThread(mTestHandler, () -> listener.onAlarm());

        verify(mKeepaliveStatsTracker).writeAndResetMetrics();
        // Alarm is rescheduled.
        verify(mAlarmManager).set(eq(AlarmManager.ELAPSED_REALTIME_WAKEUP),
                eq(METRICS_COLLECTION_DURATION_MS * 2),
                any() /* tag */, listenerCaptor.capture(), eq(mTestHandler));
    }

    private void setupResponseWithSocketExisting() throws Exception {
        final ByteBuffer tcpBufferV6 = getByteBuffer(TEST_RESPONSE_BYTES);
        final ByteBuffer tcpBufferV4 = getByteBuffer(TEST_RESPONSE_BYTES);
        doReturn(tcpBufferV6, tcpBufferV4).when(mDependencies).recvSockDiagResponse(any());
    }

    private void setupResponseWithoutSocketExisting() throws Exception {
        final ByteBuffer tcpBufferV6 = getByteBuffer(SOCK_DIAG_NO_TCP_INET_BYTES);
        final ByteBuffer tcpBufferV4 = getByteBuffer(SOCK_DIAG_NO_TCP_INET_BYTES);
        doReturn(tcpBufferV6, tcpBufferV4).when(mDependencies).recvSockDiagResponse(any());
    }

    private MarkMaskParcel makeMarkMaskParcel(final int mask, final int mark) {
        final MarkMaskParcel parcel = new MarkMaskParcel();
        parcel.mask = mask;
        parcel.mark = mark;
        return parcel;
    }

    private ByteBuffer getByteBuffer(final byte[] bytes) {
        final ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(ByteOrder.nativeOrder());
        return buffer;
    }

    private AutomaticOnOffKeepalive getAutoKiForBinder(IBinder binder) {
        return visibleOnHandlerThread(
                mTestHandler, () -> mAOOKeepaliveTracker.getKeepaliveForBinder(binder));
    }

    private void checkAndProcessKeepaliveStart(final KeepalivePacketData kpd) throws Exception {
        checkAndProcessKeepaliveStart(TEST_SLOT, kpd);
    }

    private void checkAndProcessKeepaliveStart(
            int slot, final KeepalivePacketData kpd) throws Exception {
        verify(mNai).onStartNattSocketKeepalive(
                slot, TEST_KEEPALIVE_INTERVAL_SEC, (NattKeepalivePacketData) kpd);
        verify(mNai).onAddNattKeepalivePacketFilter(slot, (NattKeepalivePacketData) kpd);
        triggerEventKeepalive(slot, SocketKeepalive.SUCCESS);
    }

    private void checkAndProcessKeepaliveStop() throws Exception {
        checkAndProcessKeepaliveStop(TEST_SLOT);
    }

    private void checkAndProcessKeepaliveStop(int slot) throws Exception {
        verify(mNai).onStopSocketKeepalive(slot);
        verify(mNai).onRemoveKeepalivePacketFilter(slot);
        triggerEventKeepalive(slot, SocketKeepalive.SUCCESS);
    }

    @Test
    public void testStartNattKeepalive_valid() throws Exception {
        final TestKeepaliveInfo testInfo = doStartNattKeepalive();

        checkAndProcessKeepaliveStart(testInfo.kpd);

        final AutomaticOnOffKeepalive autoKi = getAutoKiForBinder(testInfo.binder);
        assertNotNull(autoKi);
        assertEquals(testInfo.socketKeepaliveCallback, autoKi.getCallback());

        verify(testInfo.socketKeepaliveCallback).onStarted();
        verifyNoMoreInteractions(ignoreStubs(testInfo.socketKeepaliveCallback));
    }

    @Test
    public void testStartNattKeepalive_invalidInterval() throws Exception {
        final TestKeepaliveInfo testInfo =
                doStartNattKeepalive(TEST_KEEPALIVE_INVALID_INTERVAL_SEC);

        assertNull(getAutoKiForBinder(testInfo.binder));

        verify(testInfo.socketKeepaliveCallback).onError(SocketKeepalive.ERROR_INVALID_INTERVAL);
        verifyNoMoreInteractions(ignoreStubs(testInfo.socketKeepaliveCallback));
    }

    private void setupTestNaiForClat(InetAddress v6Src, InetAddress v6Dst) throws Exception {
        doReturn(v6Dst).when(mNai).translateV4toClatV6(any());
        doReturn(v6Src).when(mNai).getClatv6SrcAddress();
        doReturn(InetAddress.getByAddress(V4_SRC_ADDR)).when(mNai).getClatv4SrcAddress();
        // Setup nai to add clat address
        final LinkProperties stacked = new LinkProperties();
        stacked.setInterfaceName(TEST_V4_IFACE);
        final InetAddress srcAddress = InetAddress.getByAddress(
                new byte[] { (byte) 192, 0, 0, (byte) 129 });
        mNai.linkProperties.addLinkAddress(new LinkAddress(srcAddress, 24));
        mNai.linkProperties.addStackedLink(stacked);
    }

    private TestKeepaliveInfo doStartTcpKeepalive(InetAddress srcAddr) throws Exception {
        final KeepalivePacketData kpd = new TcpKeepalivePacketData(
                srcAddr,
                12345 /* srcPort */,
                InetAddress.getByAddress(new byte[] { 8, 8, 8, 8}) /* dstAddr */,
                12345 /* dstPort */, new byte[] {1},  111 /* tcpSeq */,
                222 /* tcpAck */, 800 /* tcpWindow */, 2 /* tcpWindowScale */,
                4 /* ipTos */, 64 /* ipTtl */);
        final TestKeepaliveInfo testInfo = new TestKeepaliveInfo(kpd);

        final KeepaliveInfo ki = mKeepaliveTracker.new KeepaliveInfo(
                testInfo.socketKeepaliveCallback, mNai, kpd,
                TEST_KEEPALIVE_INTERVAL_SEC, KeepaliveInfo.TYPE_TCP, testInfo.fd);
        mKeepaliveTracker.setReturnedKeepaliveInfo(ki);

        // Setup TCP keepalive.
        mAOOKeepaliveTracker.startTcpKeepalive(mNai, testInfo.fd, TEST_KEEPALIVE_INTERVAL_SEC,
                testInfo.socketKeepaliveCallback);
        HandlerUtils.waitForIdle(mTestHandler, TIMEOUT_MS);
        return testInfo;
    }
    @Test
    public void testStartTcpKeepalive_addressTranslationOnClat() throws Exception {
        setupTestNaiForClat(InetAddresses.parseNumericAddress("2001:db8::1") /* v6Src */,
                InetAddresses.parseNumericAddress("2001:db8::2") /* v6Dst */);
        final InetAddress srcAddr = InetAddress.getByAddress(V4_SRC_ADDR);
        doStartTcpKeepalive(srcAddr);
        final ArgumentCaptor<TcpKeepalivePacketData> tpdCaptor =
                ArgumentCaptor.forClass(TcpKeepalivePacketData.class);
        verify(mNai).onStartTcpSocketKeepalive(
                eq(TEST_SLOT), eq(TEST_KEEPALIVE_INTERVAL_SEC), tpdCaptor.capture());
        final TcpKeepalivePacketData tpd = tpdCaptor.getValue();
        // Verify the addresses still be the same address when clat is started.
        assertEquals(srcAddr, tpd.getSrcAddress());
    }

    @Test
    public void testStartNattKeepalive_addressTranslationOnClatNotSupported() throws Exception {
        // Disable address translation feature and verify the behavior
        doReturn(false).when(mKeepaliveTrackerDeps).isAddressTranslationEnabled(mCtx);

        setupTestNaiForClat(InetAddresses.parseNumericAddress("2001:db8::1"),
                InetAddresses.parseNumericAddress("2001:db8::2"));

        doStartNattKeepalive();
        final ArgumentCaptor<NattKeepalivePacketData> kpdCaptor =
                ArgumentCaptor.forClass(NattKeepalivePacketData.class);
        verify(mNai).onStartNattSocketKeepalive(
                eq(TEST_SLOT), eq(TEST_KEEPALIVE_INTERVAL_SEC), kpdCaptor.capture());
        // Verify that address translation is not triggered so the addresses are still v4.
        final NattKeepalivePacketData kpd = kpdCaptor.getValue();
        assertTrue(kpd.getSrcAddress() instanceof Inet4Address);
        assertTrue(kpd.getDstAddress() instanceof Inet4Address);
    }

    @Test
    public void testStartNattKeepalive_addressTranslationOnClat() throws Exception {
        final InetAddress v6AddrSrc = InetAddresses.parseNumericAddress("2001:db8::1");
        final InetAddress v6AddrDst = InetAddresses.parseNumericAddress("2001:db8::2");
        setupTestNaiForClat(v6AddrSrc, v6AddrDst);

        final TestKeepaliveInfo testInfo = doStartNattKeepalive();
        final ArgumentCaptor<NattKeepalivePacketData> kpdCaptor =
                ArgumentCaptor.forClass(NattKeepalivePacketData.class);
        verify(mNai).onStartNattSocketKeepalive(
                eq(TEST_SLOT), eq(TEST_KEEPALIVE_INTERVAL_SEC), kpdCaptor.capture());
        final NattKeepalivePacketData kpd = kpdCaptor.getValue();
        // Verify the addresses are updated to v6 when clat is started.
        assertEquals(v6AddrSrc, kpd.getSrcAddress());
        assertEquals(v6AddrDst, kpd.getDstAddress());

        triggerEventKeepalive(TEST_SLOT, SocketKeepalive.SUCCESS);
        verify(testInfo.socketKeepaliveCallback).onStarted();

        // Remove clat address should stop the keepalive.
        doReturn(null).when(mNai).getClatv6SrcAddress();
        visibleOnHandlerThread(
                mTestHandler, () -> mAOOKeepaliveTracker.handleCheckKeepalivesStillValid(mNai));
        checkAndProcessKeepaliveStop();
        assertNull(getAutoKiForBinder(testInfo.binder));

        verify(testInfo.socketKeepaliveCallback).onError(SocketKeepalive.ERROR_INVALID_IP_ADDRESS);
        verifyNoMoreInteractions(ignoreStubs(testInfo.socketKeepaliveCallback));
    }

    @Test
    public void testHandleEventSocketKeepalive_startingFailureHardwareError() throws Exception {
        final TestKeepaliveInfo testInfo = doStartNattKeepalive();

        verify(mNai).onStartNattSocketKeepalive(
                TEST_SLOT, TEST_KEEPALIVE_INTERVAL_SEC, (NattKeepalivePacketData) testInfo.kpd);
        verify(mNai).onAddNattKeepalivePacketFilter(
                TEST_SLOT, (NattKeepalivePacketData) testInfo.kpd);
        // Network agent returns an error, fails to start the keepalive.
        triggerEventKeepalive(TEST_SLOT, SocketKeepalive.ERROR_HARDWARE_ERROR);

        checkAndProcessKeepaliveStop();

        assertNull(getAutoKiForBinder(testInfo.binder));

        verify(testInfo.socketKeepaliveCallback).onError(SocketKeepalive.ERROR_HARDWARE_ERROR);
        verifyNoMoreInteractions(ignoreStubs(testInfo.socketKeepaliveCallback));
    }

    @Test
    public void testHandleCheckKeepalivesStillValid_linkPropertiesChanged() throws Exception {
        // Successful start of NATT keepalive.
        final TestKeepaliveInfo testInfo = doStartNattKeepalive();
        checkAndProcessKeepaliveStart(testInfo.kpd);
        verify(testInfo.socketKeepaliveCallback).onStarted();

        // Source address is removed from link properties by clearing.
        mNai.linkProperties.clear();

        // Check for valid keepalives
        visibleOnHandlerThread(
                mTestHandler, () -> mAOOKeepaliveTracker.handleCheckKeepalivesStillValid(mNai));

        checkAndProcessKeepaliveStop();

        assertNull(getAutoKiForBinder(testInfo.binder));

        verify(testInfo.socketKeepaliveCallback).onError(SocketKeepalive.ERROR_INVALID_IP_ADDRESS);
        verifyNoMoreInteractions(ignoreStubs(testInfo.socketKeepaliveCallback));
    }

    @Test
    public void testStopKeepalive() throws Exception {
        // Successful start of NATT keepalive.
        final TestKeepaliveInfo testInfo = doStartNattKeepalive();
        checkAndProcessKeepaliveStart(testInfo.kpd);
        verify(testInfo.socketKeepaliveCallback).onStarted();

        doStopKeepalive(getAutoKiForBinder(testInfo.binder));
        checkAndProcessKeepaliveStop();

        assertNull(getAutoKiForBinder(testInfo.binder));
        verify(testInfo.socketKeepaliveCallback).onStopped();
        verifyNoMoreInteractions(ignoreStubs(testInfo.socketKeepaliveCallback));
    }

    @Test
    public void testPauseKeepalive() throws Exception {
        // Successful start of NATT keepalive.
        final TestKeepaliveInfo testInfo = doStartNattKeepalive();
        checkAndProcessKeepaliveStart(testInfo.kpd);
        verify(testInfo.socketKeepaliveCallback).onStarted();

        doPauseKeepalive(getAutoKiForBinder(testInfo.binder));

        checkAndProcessKeepaliveStop();
        verify(testInfo.socketKeepaliveCallback).onPaused();

        // Pausing does not cleanup the autoKi
        assertNotNull(getAutoKiForBinder(testInfo.binder));

        clearInvocations(mNai);
        doStopKeepalive(getAutoKiForBinder(testInfo.binder));
        // The keepalive is already stopped.
        verify(mNai, never()).onStopSocketKeepalive(TEST_SLOT);
        verify(mNai, never()).onRemoveKeepalivePacketFilter(TEST_SLOT);

        // Stopping while paused still calls onStopped.
        verify(testInfo.socketKeepaliveCallback).onStopped();
        // autoKi is cleaned up.
        assertNull(getAutoKiForBinder(testInfo.binder));

        verifyNoMoreInteractions(ignoreStubs(testInfo.socketKeepaliveCallback));

        // Make sure the slot is free
        final TestKeepaliveInfo testInfo2 = doStartNattKeepalive();
        checkAndProcessKeepaliveStart(testInfo2.kpd);
    }

    @Test
    public void testResumeKeepalive() throws Exception {
        // Successful start of NATT keepalive.
        final TestKeepaliveInfo testInfo = doStartNattKeepalive();
        checkAndProcessKeepaliveStart(testInfo.kpd);
        verify(testInfo.socketKeepaliveCallback).onStarted();

        doPauseKeepalive(getAutoKiForBinder(testInfo.binder));
        checkAndProcessKeepaliveStop();
        verify(testInfo.socketKeepaliveCallback).onPaused();

        clearInvocations(mNai);
        doResumeKeepalive(getAutoKiForBinder(testInfo.binder));
        checkAndProcessKeepaliveStart(testInfo.kpd);
        assertNotNull(getAutoKiForBinder(testInfo.binder));
        verify(testInfo.socketKeepaliveCallback).onResumed();

        doStopKeepalive(getAutoKiForBinder(testInfo.binder));
        checkAndProcessKeepaliveStop();
        assertNull(getAutoKiForBinder(testInfo.binder));

        verify(testInfo.socketKeepaliveCallback).onStopped();
        verifyNoMoreInteractions(ignoreStubs(testInfo.socketKeepaliveCallback));
    }

    @Test
    public void testResumeKeepalive_invalidSourceAddress() throws Exception {
        // Successful start of NATT keepalive.
        final TestKeepaliveInfo testInfo = doStartNattKeepalive();
        checkAndProcessKeepaliveStart(testInfo.kpd);
        verify(testInfo.socketKeepaliveCallback).onStarted();

        doPauseKeepalive(getAutoKiForBinder(testInfo.binder));
        checkAndProcessKeepaliveStop();
        verify(testInfo.socketKeepaliveCallback).onPaused();

        mNai.linkProperties.clear();

        clearInvocations(mNai);
        doResumeKeepalive(getAutoKiForBinder(testInfo.binder));
        verify(mNai, never()).onStartNattSocketKeepalive(anyInt(), anyInt(), any());
        verify(mNai, never()).onAddNattKeepalivePacketFilter(anyInt(), any());

        assertNull(getAutoKiForBinder(testInfo.binder));

        verify(testInfo.socketKeepaliveCallback).onError(SocketKeepalive.ERROR_INVALID_IP_ADDRESS);
        verifyNoMoreInteractions(ignoreStubs(testInfo.socketKeepaliveCallback));
    }

    @Test
    public void testResumeKeepalive_startingFailureHardwareError() throws Exception {
        // Successful start of NATT keepalive.
        final TestKeepaliveInfo testInfo = doStartNattKeepalive();
        checkAndProcessKeepaliveStart(testInfo.kpd);
        verify(testInfo.socketKeepaliveCallback).onStarted();

        doPauseKeepalive(getAutoKiForBinder(testInfo.binder));
        checkAndProcessKeepaliveStop();
        verify(testInfo.socketKeepaliveCallback).onPaused();

        clearInvocations(mNai);
        doResumeKeepalive(getAutoKiForBinder(testInfo.binder));

        verify(mNai).onStartNattSocketKeepalive(
                TEST_SLOT, TEST_KEEPALIVE_INTERVAL_SEC, (NattKeepalivePacketData) testInfo.kpd);
        verify(mNai).onAddNattKeepalivePacketFilter(
                TEST_SLOT, (NattKeepalivePacketData) testInfo.kpd);
        // Network agent returns error on starting the keepalive.
        triggerEventKeepalive(TEST_SLOT, SocketKeepalive.ERROR_HARDWARE_ERROR);

        checkAndProcessKeepaliveStop();

        assertNull(getAutoKiForBinder(testInfo.binder));
        verify(testInfo.socketKeepaliveCallback).onError(SocketKeepalive.ERROR_HARDWARE_ERROR);
        verifyNoMoreInteractions(ignoreStubs(testInfo.socketKeepaliveCallback));
    }

    @Test
    public void testStopAllKeepalives() throws Exception {
        final TestKeepaliveInfo testInfo1 = doStartNattKeepalive();
        final TestKeepaliveInfo testInfo2 = doStartNattKeepalive();
        checkAndProcessKeepaliveStart(TEST_SLOT, testInfo1.kpd);
        checkAndProcessKeepaliveStart(TEST_SLOT + 1, testInfo2.kpd);

        verify(testInfo1.socketKeepaliveCallback).onStarted();
        verify(testInfo2.socketKeepaliveCallback).onStarted();

        // Pause the first keepalive
        doPauseKeepalive(getAutoKiForBinder(testInfo1.binder));
        checkAndProcessKeepaliveStop(TEST_SLOT);
        verify(testInfo1.socketKeepaliveCallback).onPaused();

        visibleOnHandlerThread(
                mTestHandler,
                () -> mAOOKeepaliveTracker.handleStopAllKeepalives(
                        mNai, SocketKeepalive.ERROR_INVALID_NETWORK));

        // Note that checkAndProcessKeepaliveStop is not called since the network agent is assumed
        // to be disconnected for a handleStopAllKeepalives call.
        assertNull(getAutoKiForBinder(testInfo1.binder));
        assertNull(getAutoKiForBinder(testInfo2.binder));

        verify(testInfo1.socketKeepaliveCallback, never()).onStopped();
        verify(testInfo2.socketKeepaliveCallback, never()).onStopped();
        verify(testInfo1.socketKeepaliveCallback).onError(SocketKeepalive.ERROR_INVALID_NETWORK);
        verify(testInfo2.socketKeepaliveCallback).onError(SocketKeepalive.ERROR_INVALID_NETWORK);

        verifyNoMoreInteractions(ignoreStubs(testInfo1.socketKeepaliveCallback));
        verifyNoMoreInteractions(ignoreStubs(testInfo2.socketKeepaliveCallback));
    }

    @Test
    public void testTwoKeepalives_startAfterPause() throws Exception {
        final TestKeepaliveInfo testInfo1 = doStartNattKeepalive();
        checkAndProcessKeepaliveStart(testInfo1.kpd);
        verify(testInfo1.socketKeepaliveCallback).onStarted();
        assertNotNull(getAutoKiForBinder(testInfo1.binder));

        final AutomaticOnOffKeepalive autoKi1  = getAutoKiForBinder(testInfo1.binder);
        doPauseKeepalive(autoKi1);
        checkAndProcessKeepaliveStop(TEST_SLOT);
        verify(testInfo1.socketKeepaliveCallback).onPaused();
        assertNotNull(getAutoKiForBinder(testInfo1.binder));

        clearInvocations(mNai);
        // Start the second keepalive while the first is paused.
        final TestKeepaliveInfo testInfo2 = doStartNattKeepalive();
        // The slot used is TEST_SLOT + 1 since TEST_SLOT is being taken by the paused keepalive.
        checkAndProcessKeepaliveStart(TEST_SLOT + 1, testInfo2.kpd);
        verify(testInfo2.socketKeepaliveCallback).onStarted();
        assertNotNull(getAutoKiForBinder(testInfo2.binder));

        clearInvocations(mNai);
        doResumeKeepalive(autoKi1);
        // Resume on TEST_SLOT.
        checkAndProcessKeepaliveStart(TEST_SLOT, testInfo1.kpd);
        verify(testInfo1.socketKeepaliveCallback).onResumed();

        clearInvocations(mNai);
        doStopKeepalive(autoKi1);
        checkAndProcessKeepaliveStop(TEST_SLOT);
        verify(testInfo1.socketKeepaliveCallback).onStopped();
        verify(testInfo2.socketKeepaliveCallback, never()).onStopped();
        assertNull(getAutoKiForBinder(testInfo1.binder));

        clearInvocations(mNai);
        assertNotNull(getAutoKiForBinder(testInfo2.binder));
        doStopKeepalive(getAutoKiForBinder(testInfo2.binder));
        checkAndProcessKeepaliveStop(TEST_SLOT + 1);
        verify(testInfo2.socketKeepaliveCallback).onStopped();
        assertNull(getAutoKiForBinder(testInfo2.binder));

        verifyNoMoreInteractions(ignoreStubs(testInfo1.socketKeepaliveCallback));
        verifyNoMoreInteractions(ignoreStubs(testInfo2.socketKeepaliveCallback));
    }

    @Test
    public void testStartTcpKeepalive_fdInitiatedStop() throws Exception {
        final InetAddress srcAddress = InetAddress.getByAddress(
                new byte[] { (byte) 192, 0, 0, (byte) 129 });
        mNai.linkProperties.addLinkAddress(new LinkAddress(srcAddress, 24));

        final TestKeepaliveInfo testInfo =
                doStartTcpKeepalive(InetAddress.getByAddress(V4_SRC_ADDR));

        // A closed socket will result in EVENT_HANGUP and trigger error to
        // FileDescriptorEventListener.
        testInfo.socket.close();
        HandlerUtils.waitForIdle(mTestHandler, TIMEOUT_MS);

        // The keepalive should be removed in AutomaticOnOffKeepaliveTracker.
        assertNull(getAutoKiForBinder(testInfo.binder));
    }

    @Test
    public void testDumpDoesNotCrash() throws Exception {
        final TestKeepaliveInfo testInfo1 = doStartNattKeepalive();
        final TestKeepaliveInfo testInfo2 = doStartNattKeepalive();
        checkAndProcessKeepaliveStart(TEST_SLOT, testInfo1.kpd);
        checkAndProcessKeepaliveStart(TEST_SLOT + 1, testInfo2.kpd);
        final AutomaticOnOffKeepalive autoKi1  = getAutoKiForBinder(testInfo1.binder);
        doPauseKeepalive(autoKi1);

        final StringWriter stringWriter = new StringWriter();
        final IndentingPrintWriter pw = new IndentingPrintWriter(stringWriter, "   ");
        visibleOnHandlerThread(mTestHandler, () -> mAOOKeepaliveTracker.dump(pw));
        assertFalse(stringWriter.toString().isEmpty());
    }
}
