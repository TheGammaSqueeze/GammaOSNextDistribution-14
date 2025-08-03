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

package com.android.internal.net.ipsec.test.ike.keepalive;

import static android.net.SocketKeepalive.ERROR_INVALID_IP_ADDRESS;
import static android.net.ipsec.test.ike.IkeSessionParams.IKE_OPTION_AUTOMATIC_KEEPALIVE_ON_OFF;

import static com.android.internal.net.ipsec.test.ike.keepalive.IkeNattKeepalive.KeepaliveConfig;
import static com.android.internal.net.ipsec.test.ike.utils.IkeAlarm.IkeAlarmConfig;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.app.PendingIntent;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.IpSecManager.UdpEncapsulationSocket;
import android.net.Network;
import android.net.SocketKeepalive;
import android.net.ipsec.test.ike.IkeSessionParams;
import android.os.Build;
import android.os.Message;

import com.android.internal.net.ipsec.test.ike.IkeContext;
import com.android.internal.net.ipsec.test.ike.keepalive.IkeNattKeepalive.KeepaliveConfig;
import com.android.internal.net.ipsec.test.ike.utils.IkeAlarm.IkeAlarmConfig;
import com.android.testutils.DevSdkIgnoreRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.net.Inet4Address;
import java.util.concurrent.TimeUnit;

public class IkeNattKeepaliveTest {
    private static final int KEEPALIVE_DELAY_CALLER_CONFIGURED_SECONDS = 50;

    @Rule
    public final DevSdkIgnoreRule ignoreRule = new DevSdkIgnoreRule();

    private ConnectivityManager mMockConnectManager;
    private IkeSessionParams mMockIkeParams;
    private IkeNattKeepalive.Dependencies mMockDeps;
    private SocketKeepalive mMockSocketKeepalive;
    private SoftwareKeepaliveImpl mMockSoftwareKeepalive;
    private SoftwareKeepaliveImpl mMockSoftwareKeepaliveOne;
    private SoftwareKeepaliveImpl mMockSoftwareKeepaliveTwo;
    private HardwareKeepaliveImpl mMockHardwareKeepalive;
    private HardwareKeepaliveImpl mMockHardwareKeepaliveOne;
    private HardwareKeepaliveImpl mHardwareKeepaliveTwo;

    private IkeNattKeepalive mIkeNattKeepalive;
    private KeepaliveConfig mKeepaliveConfig;

    @Before
    public void setUp() throws Exception {
        mMockIkeParams = mock(IkeSessionParams.class);
        doReturn(KEEPALIVE_DELAY_CALLER_CONFIGURED_SECONDS)
                .when(mMockIkeParams)
                .getNattKeepAliveDelaySeconds();

        mMockConnectManager = mock(ConnectivityManager.class);
        mMockSocketKeepalive = mock(SocketKeepalive.class);
        resetMockConnectManager();

        mMockDeps = spy(new IkeNattKeepalive.Dependencies());
        mMockSoftwareKeepalive = mock(SoftwareKeepaliveImpl.class);
        mMockHardwareKeepalive = mock(HardwareKeepaliveImpl.class);
        resetMockDeps(mMockSoftwareKeepalive, mMockHardwareKeepalive);

        mKeepaliveConfig =
                new KeepaliveConfig(
                        mock(Inet4Address.class),
                        mock(Inet4Address.class),
                        mock(UdpEncapsulationSocket.class),
                        mock(Network.class),
                        mock(Network.class),
                        new IkeAlarmConfig(
                                mock(Context.class),
                                "TEST",
                                TimeUnit.SECONDS.toMillis(
                                        KEEPALIVE_DELAY_CALLER_CONFIGURED_SECONDS),
                                mock(PendingIntent.class),
                                mock(Message.class)),
                        mMockIkeParams);
        mIkeNattKeepalive = createIkeNattKeepalive();

        mMockHardwareKeepaliveOne = mock(HardwareKeepaliveImpl.class);
        mHardwareKeepaliveTwo = mock(HardwareKeepaliveImpl.class);
        mMockSoftwareKeepaliveOne = mock(SoftwareKeepaliveImpl.class);
        mMockSoftwareKeepaliveTwo = mock(SoftwareKeepaliveImpl.class);
    }

    private void resetMockConnectManager() {
        reset(mMockConnectManager);
        doReturn(mMockSocketKeepalive)
                .when(mMockConnectManager)
                .createSocketKeepalive(
                        anyObject(),
                        anyObject(),
                        anyObject(),
                        anyObject(),
                        anyObject(),
                        anyObject());
    }

    private void resetMockDeps(
            SoftwareKeepaliveImpl softwareKeepalive, HardwareKeepaliveImpl hardwareKeepalive) {
        reset(mMockDeps);
        doReturn(softwareKeepalive)
                .when(mMockDeps)
                .createSoftwareKeepaliveImpl(anyObject(), anyObject(), anyObject(), anyObject());
        doReturn(hardwareKeepalive)
                .when(mMockDeps)
                .createHardwareKeepaliveImpl(anyObject(), anyObject(), anyObject(), anyObject());
    }

    private IkeNattKeepalive createIkeNattKeepalive() throws Exception {
        return new IkeNattKeepalive(
                mock(IkeContext.class), mMockConnectManager, mKeepaliveConfig, mMockDeps);
    }

    private IkeNattKeepalive createIkeNattKeepaliveWithInjectedSocketKeepalive() throws Exception {
        reset(mMockDeps);
        doReturn(mMockSoftwareKeepalive)
                .when(mMockDeps)
                .createSoftwareKeepaliveImpl(anyObject(), anyObject(), anyObject(), anyObject());
        return createIkeNattKeepalive();
    }

    @After
    public void tearDown() throws Exception {
        mIkeNattKeepalive.stop();
    }

    @DevSdkIgnoreRule.IgnoreAfter(Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testStartStopHardwareKeepaliveBeforeU() throws Exception {
        testStartStopHardwareKeepalive(true);
    }

    @DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testStartStopHardwareKeepaliveAfterU() throws Exception {
        testStartStopHardwareKeepalive(false);
    }

    private void testStartStopHardwareKeepalive(boolean beforeU) throws Exception {
        mIkeNattKeepalive.stop();
        mIkeNattKeepalive = createIkeNattKeepaliveWithInjectedSocketKeepalive();
        mIkeNattKeepalive.start();
        if (beforeU) {
            verify(mMockSocketKeepalive).start(eq(KEEPALIVE_DELAY_CALLER_CONFIGURED_SECONDS));
        } else {
            // Flag should be 0 if IKE_OPTION_AUTOMATIC_KEEPALIVE_ON_OFF is not set.
            verify(mMockSocketKeepalive).start(eq(KEEPALIVE_DELAY_CALLER_CONFIGURED_SECONDS),
                    eq(0), any());
        }

        mIkeNattKeepalive.stop();
        verify(mMockSocketKeepalive).stop();
    }

    private IkeNattKeepalive setupKeepaliveWithDisableKeepaliveNoTcpConnectionsOption()
            throws Exception {
        doReturn(true).when(mMockIkeParams)
                .hasIkeOption(IKE_OPTION_AUTOMATIC_KEEPALIVE_ON_OFF);
        return createIkeNattKeepaliveWithInjectedSocketKeepalive();
    }

    @DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    @Test
    public void testKeepaliveWithDisableKeepaliveNoTcpConnectionsOption() throws Exception {
        final IkeNattKeepalive ikeNattKeepalive =
                setupKeepaliveWithDisableKeepaliveNoTcpConnectionsOption();

        try {
            ikeNattKeepalive.start();
            verify(mMockSocketKeepalive).start(
                    eq(KEEPALIVE_DELAY_CALLER_CONFIGURED_SECONDS),
                    eq(SocketKeepalive.FLAG_AUTOMATIC_ON_OFF),
                    any());
        } finally {
            ikeNattKeepalive.stop();
        }
    }

    private static SocketKeepalive.Callback verifyHardwareKeepaliveAndGetCb(
            ConnectivityManager mockConnectManager) throws Exception {
        ArgumentCaptor<SocketKeepalive.Callback> socketKeepaliveCbCaptor =
                ArgumentCaptor.forClass(SocketKeepalive.Callback.class);

        verify(mockConnectManager)
                .createSocketKeepalive(
                        anyObject(),
                        anyObject(),
                        anyObject(),
                        anyObject(),
                        anyObject(),
                        socketKeepaliveCbCaptor.capture());

        return socketKeepaliveCbCaptor.getValue();
    }

    @Test
    public void testSwitchToSoftwareKeepalive() throws Exception {
        mIkeNattKeepalive.stop();
        mIkeNattKeepalive = createIkeNattKeepaliveWithInjectedSocketKeepalive();

        SocketKeepalive.Callback hardwareKeepaliveCb =
                verifyHardwareKeepaliveAndGetCb(mMockConnectManager);
        hardwareKeepaliveCb.onError(ERROR_INVALID_IP_ADDRESS);

        verify(mMockSocketKeepalive).stop();

        ArgumentCaptor<IkeAlarmConfig> alarmConfigCaptor =
                ArgumentCaptor.forClass(IkeAlarmConfig.class);
        verify(mMockDeps)
                .createSoftwareKeepaliveImpl(any(), any(), any(), alarmConfigCaptor.capture());
        assertEquals(
                TimeUnit.SECONDS.toMillis((long) KEEPALIVE_DELAY_CALLER_CONFIGURED_SECONDS),
                alarmConfigCaptor.getValue().delayMs);

        mIkeNattKeepalive.stop();
        verify(mMockSocketKeepalive).stop();
        verify(mMockSoftwareKeepalive).stop();
    }

    private HardwareKeepaliveImpl.HardwareKeepaliveCallback verifyHardwareKeepaliveImplAndGetCb()
            throws Exception {
        ArgumentCaptor<HardwareKeepaliveImpl.HardwareKeepaliveCallback> hardwareKeepaliveCbCaptor =
                ArgumentCaptor.forClass(HardwareKeepaliveImpl.HardwareKeepaliveCallback.class);

        verify(mMockDeps)
                .createHardwareKeepaliveImpl(
                        any(), any(), any(), hardwareKeepaliveCbCaptor.capture());

        return hardwareKeepaliveCbCaptor.getValue();
    }

    private void verifyHardwareKeepaliveStarted(
            HardwareKeepaliveImpl hardwareKeepalive, KeepaliveConfig keepaliveConfig) {
        verify(mMockDeps).createHardwareKeepaliveImpl(any(), any(), eq(keepaliveConfig), any());
        verify(hardwareKeepalive).start();
    }

    private void verifyHardwareKeepaliveNeverStarted(HardwareKeepaliveImpl hardwareKeepalive) {
        verify(mMockDeps, never()).createHardwareKeepaliveImpl(any(), any(), any(), any());
        verify(hardwareKeepalive, never()).start();
    }

    private void verifySoftwareKeepaliveStarted(SoftwareKeepaliveImpl softwareKeepalive) {
        verify(mMockDeps).createSoftwareKeepaliveImpl(any(), any(), any(), any());
        verify(softwareKeepalive).start();
    }

    private KeepaliveConfig createCopyWithNewUnderpinnedNetwork(KeepaliveConfig keepaliveConfig) {
        return new KeepaliveConfig(
                keepaliveConfig.src,
                keepaliveConfig.dest,
                keepaliveConfig.socket,
                keepaliveConfig.network,
                mock(Network.class),
                keepaliveConfig.ikeAlarmConfig,
                keepaliveConfig.ikeParams);
    }

    private KeepaliveConfig createCopyWithNewUnderlyingNetwork(KeepaliveConfig keepaliveConfig) {
        return new KeepaliveConfig(
                keepaliveConfig.src,
                keepaliveConfig.dest,
                keepaliveConfig.socket,
                mock(Network.class),
                keepaliveConfig.underpinnedNetwork,
                keepaliveConfig.ikeAlarmConfig,
                keepaliveConfig.ikeParams);
    }

    private void restartFromHardwareKeepalive(
            KeepaliveConfig newKeepaliveConfig,
            HardwareKeepaliveImpl oldHardwareKeepalive,
            HardwareKeepaliveImpl newHardwareKeepalive,
            SoftwareKeepaliveImpl newSoftwareKeepalive)
            throws Exception {
        // Reset Dependencies to track new keepalives
        resetMockDeps(newSoftwareKeepalive, newHardwareKeepalive);

        // Restart and verify switching from hardware to software keepalive
        mIkeNattKeepalive.restart(newKeepaliveConfig);

        verify(oldHardwareKeepalive).stop();
        verifySoftwareKeepaliveStarted(newSoftwareKeepalive);
        verifyHardwareKeepaliveNeverStarted(newHardwareKeepalive);
        assertTrue(mIkeNattKeepalive.isRestarting());
    }

    private void verifyRestart_withHardwareKeepalive(
            KeepaliveConfig newKeepaliveConfig, int onStoppedCallCnt) throws Exception {
        final HardwareKeepaliveImpl.HardwareKeepaliveCallback hardwareKeepaliveCb =
                verifyHardwareKeepaliveImplAndGetCb();

        restartFromHardwareKeepalive(
                newKeepaliveConfig,
                mMockHardwareKeepalive,
                mMockHardwareKeepaliveOne,
                mMockSoftwareKeepaliveOne);

        // Fire onStopped and verify switching from software to hardware keepalive
        for (int cnt = 0; cnt < onStoppedCallCnt; cnt++) {
            hardwareKeepaliveCb.onStopped(mMockHardwareKeepalive);
        }
        verify(mMockSoftwareKeepaliveOne).stop();
        verifyHardwareKeepaliveStarted(mMockHardwareKeepaliveOne, newKeepaliveConfig);
    }

    private void verifyRestart_withSoftwareKeepalive(KeepaliveConfig newKeepaliveConfig)
            throws Exception {
        final HardwareKeepaliveImpl.HardwareKeepaliveCallback hardwareKeepaliveCb =
                verifyHardwareKeepaliveImplAndGetCb();

        // Reset Dependencies to track new keepalives
        resetMockDeps(mMockSoftwareKeepaliveOne, mMockHardwareKeepaliveOne);

        // Switch to use software keepalive
        hardwareKeepaliveCb.onHardwareOffloadError();
        verifySoftwareKeepaliveStarted(mMockSoftwareKeepaliveOne);

        // Restart and verify switching from software to hardware keepalive
        mIkeNattKeepalive.restart(newKeepaliveConfig);
        verify(mMockSoftwareKeepaliveOne).stop();
        verifyHardwareKeepaliveStarted(mMockHardwareKeepaliveOne, newKeepaliveConfig);
    }

    @Test
    public void testUpdateUnderpinnedNetwork_withHardwareKeepalive() throws Exception {
        verifyRestart_withHardwareKeepalive(
                createCopyWithNewUnderpinnedNetwork(mKeepaliveConfig), 1 /* onStoppedCallCnt */);
    }

    @Test
    public void testUpdateUnderpinnedNetwork_withHardwareKeepalive_onStoppedFiredMoreThanOnce()
            throws Exception {
        // Makes sure the onStopped call is idempotent to IkeNattKeepalive
        verifyRestart_withHardwareKeepalive(
                createCopyWithNewUnderpinnedNetwork(mKeepaliveConfig), 10 /* onStoppedCallCnt */);
    }

    @Test
    public void testUpdateUnderpinnedNetwork_withSoftwareKeepalive() throws Exception {
        verifyRestart_withSoftwareKeepalive(createCopyWithNewUnderpinnedNetwork(mKeepaliveConfig));
    }

    @Test
    public void testUpdateUnderlyingNetwork_withHardwareKeepalive() throws Exception {
        verifyRestart_withHardwareKeepalive(
                createCopyWithNewUnderlyingNetwork(mKeepaliveConfig), 1 /* onStoppedCallCnt */);
    }

    @Test
    public void testUpdateUnderlyingNetwork_withHardwareKeepalive_onStoppedFiredMoreThanOnce()
            throws Exception {
        // Makes sure the onStopped call is idempotent to IkeNattKeepalive
        verifyRestart_withHardwareKeepalive(
                createCopyWithNewUnderlyingNetwork(mKeepaliveConfig), 10 /* onStoppedCallCnt */);
    }

    @Test
    public void testUpdateUnderlyingNetwork_withSoftwareKeepalive() throws Exception {
        verifyRestart_withSoftwareKeepalive(createCopyWithNewUnderlyingNetwork(mKeepaliveConfig));
    }

    @Test
    public void testRestart_duringRestart() throws Exception {
        final KeepaliveConfig newKeepaliveConfigOne =
                createCopyWithNewUnderpinnedNetwork(mKeepaliveConfig);
        final KeepaliveConfig newKeepaliveConfigTwo =
                createCopyWithNewUnderlyingNetwork(mKeepaliveConfig);
        final HardwareKeepaliveImpl.HardwareKeepaliveCallback hardwareKeepaliveCb =
                verifyHardwareKeepaliveImplAndGetCb();

        restartFromHardwareKeepalive(
                newKeepaliveConfigOne,
                mMockHardwareKeepalive,
                mMockHardwareKeepaliveOne,
                mMockSoftwareKeepaliveOne);

        // Reset Dependencies to track new keepalives
        resetMockDeps(mMockSoftwareKeepaliveTwo, mMockHardwareKeepaliveOne);
        mIkeNattKeepalive.restart(newKeepaliveConfigTwo);
        verify(mMockSoftwareKeepaliveOne).stop();
        verifySoftwareKeepaliveStarted(mMockSoftwareKeepaliveTwo);

        // Fire onStopped and verify switching from software to hardware keepalive
        hardwareKeepaliveCb.onStopped(mMockHardwareKeepalive);
        verify(mMockSoftwareKeepaliveTwo).stop();
        verifyHardwareKeepaliveStarted(mMockHardwareKeepaliveOne, newKeepaliveConfigTwo);
    }

    @Test
    public void testRestart_withHardwareKeepalive_ignoreOldHardwareCallback() throws Exception {
        final KeepaliveConfig newKeepaliveConfigOne =
                createCopyWithNewUnderlyingNetwork(mKeepaliveConfig);
        final KeepaliveConfig newKeepaliveConfigTwo =
                createCopyWithNewUnderlyingNetwork(mKeepaliveConfig);

        // First round of restart
        final HardwareKeepaliveImpl.HardwareKeepaliveCallback hardwareKeepaliveCb =
                verifyHardwareKeepaliveImplAndGetCb();
        restartFromHardwareKeepalive(
                newKeepaliveConfigOne,
                mMockHardwareKeepalive,
                mMockHardwareKeepaliveOne,
                mMockSoftwareKeepaliveOne);

        hardwareKeepaliveCb.onStopped(mMockHardwareKeepalive);
        verify(mMockSoftwareKeepaliveOne).stop();
        verifyHardwareKeepaliveStarted(mMockHardwareKeepaliveOne, newKeepaliveConfigOne);

        // Second round of restart
        final HardwareKeepaliveImpl.HardwareKeepaliveCallback hardwareKeepaliveCbOne =
                verifyHardwareKeepaliveImplAndGetCb();
        restartFromHardwareKeepalive(
                newKeepaliveConfigTwo,
                mMockHardwareKeepaliveOne,
                mHardwareKeepaliveTwo,
                mMockSoftwareKeepaliveTwo);

        hardwareKeepaliveCb.onStopped(mMockHardwareKeepalive);
        verifyHardwareKeepaliveNeverStarted(mHardwareKeepaliveTwo);

        hardwareKeepaliveCbOne.onStopped(mMockHardwareKeepaliveOne);
        verifyHardwareKeepaliveStarted(mHardwareKeepaliveTwo, newKeepaliveConfigTwo);
    }

    @Test
    public void testSwitchToHardwareKeepalive_duringNetworkError() throws Exception {
        final KeepaliveConfig newKeepaliveConfigOne =
                createCopyWithNewUnderlyingNetwork(mKeepaliveConfig);
        final KeepaliveConfig newKeepaliveConfigTwo =
                createCopyWithNewUnderlyingNetwork(mKeepaliveConfig);

        // First round of restart
        final HardwareKeepaliveImpl.HardwareKeepaliveCallback hardwareKeepaliveCb =
                verifyHardwareKeepaliveImplAndGetCb();
        restartFromHardwareKeepalive(
                newKeepaliveConfigOne,
                mMockHardwareKeepalive,
                mMockHardwareKeepaliveOne,
                mMockSoftwareKeepaliveOne);

        hardwareKeepaliveCb.onStopped(mMockHardwareKeepalive);
        verify(mMockSoftwareKeepaliveOne).stop();
        verifyHardwareKeepaliveStarted(mMockHardwareKeepaliveOne, newKeepaliveConfigOne);

        // Second round of restart
        final HardwareKeepaliveImpl.HardwareKeepaliveCallback hardwareKeepaliveCbOne =
                verifyHardwareKeepaliveImplAndGetCb();
        restartFromHardwareKeepalive(
                newKeepaliveConfigTwo,
                mMockHardwareKeepaliveOne,
                mHardwareKeepaliveTwo,
                mMockSoftwareKeepaliveTwo);

        // If stop keepalive, such as when the Ike Session is terminated,
        mIkeNattKeepalive.stop();
        // no need to start hardware keepalive anymore.
        verifyHardwareKeepaliveNeverStarted(mHardwareKeepaliveTwo);

        // Since IkeNattKeepalive is stopped,
        hardwareKeepaliveCbOne.onStopped(mMockHardwareKeepaliveOne);
        // no need to start hardware keepalive anymore.
        verifyHardwareKeepaliveNeverStarted(mHardwareKeepaliveTwo);
    }
}
