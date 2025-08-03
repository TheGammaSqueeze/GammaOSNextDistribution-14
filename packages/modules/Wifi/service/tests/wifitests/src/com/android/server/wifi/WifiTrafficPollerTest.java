/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.server.wifi;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.wifi.ITrafficStateCallback;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.test.TestLooper;

import androidx.test.filters.SmallTest;

import com.android.wifi.resources.R;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link com.android.server.wifi.WifiTrafficPoller}.
 */
@SmallTest
public class WifiTrafficPollerTest extends WifiBaseTest {
    public static final String TAG = "WifiTrafficPollerTest";

    private TestLooper mLooper;
    private WifiTrafficPoller mWifiTrafficPoller;
    private final static long DEFAULT_PACKET_COUNT = 10;
    private final static long TX_PACKET_COUNT = 40;
    private final static long RX_PACKET_COUNT = 50;

    @Mock IBinder mAppBinder;
    @Mock ITrafficStateCallback mTrafficStateCallback;

    @Mock IBinder mAppBinder2;
    @Mock ITrafficStateCallback mTrafficStateCallback2;

    @Mock private Context mContext;
    private MockResources mResources;
    private InOrder mInOrder;

    /**
     * Called before each test
     */
    @Before
    public void setUp() throws Exception {
        // Ensure looper exists
        mLooper = new TestLooper();
        MockitoAnnotations.initMocks(this);
        mResources = new MockResources();
        mResources.setInteger(R.integer.config_wifiTrafficPollerTxPacketThreshold, 5);
        mResources.setInteger(R.integer.config_wifiTrafficPollerRxPacketThreshold, 9);
        when(mContext.getResources()).thenReturn(mResources);

        mWifiTrafficPoller = new WifiTrafficPoller(mContext);

        // Set the current mTxPkts and mRxPkts to DEFAULT_PACKET_COUNT
        mWifiTrafficPoller.notifyOnDataActivity(DEFAULT_PACKET_COUNT, DEFAULT_PACKET_COUNT);

        when(mTrafficStateCallback.asBinder()).thenReturn(mAppBinder);
        when(mTrafficStateCallback2.asBinder()).thenReturn(mAppBinder2);
    }

    /**
     * Verify that clients should be notified of activity in case Tx/Rx packet count changes.
     */
    @Test
    public void testClientNotification() throws RemoteException {
        // Register Client to verify that Tx/RX packet message is properly received.
        mWifiTrafficPoller.addCallback(mTrafficStateCallback);
        mWifiTrafficPoller.notifyOnDataActivity(TX_PACKET_COUNT, RX_PACKET_COUNT);

        // Client should get the DATA_ACTIVITY_NOTIFICATION
        verify(mTrafficStateCallback).onStateChanged(
                WifiManager.TrafficStateCallback.DATA_ACTIVITY_INOUT);
    }

    /**
     * Verify that Tx/Rx packet count meets the threshold for updating data activity type
     */
    @Test
    public void testDataActivityUpdatePacketThreshold() throws RemoteException {
        // Register Client to verify that Tx/RX packet message is properly received.
        mWifiTrafficPoller.addCallback(mTrafficStateCallback);
        mWifiTrafficPoller.notifyOnDataActivity(TX_PACKET_COUNT, RX_PACKET_COUNT);

        mInOrder = inOrder(mTrafficStateCallback);
        // Client should get the DATA_ACTIVITY_NOTIFICATION
        mInOrder.verify(mTrafficStateCallback).onStateChanged(
                WifiManager.TrafficStateCallback.DATA_ACTIVITY_INOUT);

        // TxPacket increase below threshold
        mWifiTrafficPoller.notifyOnDataActivity(TX_PACKET_COUNT + 3, RX_PACKET_COUNT);
        // Client should get the no DATA_ACTIVITY_NOTIFICATION
        mInOrder.verify(mTrafficStateCallback).onStateChanged(
                WifiManager.TrafficStateCallback.DATA_ACTIVITY_NONE);

        // TxPacket increase above threshold
        mWifiTrafficPoller.notifyOnDataActivity(TX_PACKET_COUNT + 10, RX_PACKET_COUNT);
        // called once with OUT
        mInOrder.verify(mTrafficStateCallback)
                .onStateChanged(WifiManager.TrafficStateCallback.DATA_ACTIVITY_OUT);

        // RxPacket increase below threshold
        mWifiTrafficPoller.notifyOnDataActivity(TX_PACKET_COUNT + 10, RX_PACKET_COUNT + 6);
        // Client should get the no DATA_ACTIVITY_NOTIFICATION
        mInOrder.verify(mTrafficStateCallback).onStateChanged(
                WifiManager.TrafficStateCallback.DATA_ACTIVITY_NONE);

        // RxPacket increase above threshold
        mWifiTrafficPoller.notifyOnDataActivity(TX_PACKET_COUNT + 10, RX_PACKET_COUNT + 20);
        // called once with IN
        mInOrder.verify(mTrafficStateCallback)
                .onStateChanged(WifiManager.TrafficStateCallback.DATA_ACTIVITY_IN);
    }


    /**
     * Verify that remove client should be handled
     */
    @Test
    public void testRemoveClient() throws RemoteException {
        // Register Client to verify that Tx/RX packet message is properly received.
        mWifiTrafficPoller.addCallback(mTrafficStateCallback);
        mWifiTrafficPoller.removeCallback(mTrafficStateCallback);
        verify(mAppBinder).unlinkToDeath(any(), anyInt());

        mWifiTrafficPoller.notifyOnDataActivity(TX_PACKET_COUNT, RX_PACKET_COUNT);

        // Client should not get any message after the client is removed.
        verify(mTrafficStateCallback, never()).onStateChanged(anyInt());
    }

    /**
     * Verify that remove client ignores when callback is wrong.
     */
    @Test
    public void testRemoveClientWithWrongCallback() throws RemoteException {
        // Register Client to verify that Tx/RX packet message is properly received.
        mWifiTrafficPoller.addCallback(mTrafficStateCallback);
        mWifiTrafficPoller.removeCallback(mTrafficStateCallback2);
        mLooper.dispatchAll();

        mWifiTrafficPoller.notifyOnDataActivity(TX_PACKET_COUNT, RX_PACKET_COUNT);

        // Client should get the DATA_ACTIVITY_NOTIFICATION
        verify(mTrafficStateCallback).onStateChanged(
                WifiManager.TrafficStateCallback.DATA_ACTIVITY_INOUT);
    }

    /**
     *
     * Verify that traffic poller registers for death notification on adding client.
     */
    @Test
    public void registersForBinderDeathOnAddClient() throws Exception {
        mWifiTrafficPoller.addCallback(mTrafficStateCallback);
        verify(mAppBinder).linkToDeath(any(IBinder.DeathRecipient.class), anyInt());
    }

    /**
     *
     * Verify that traffic poller registers for death notification on adding client.
     */
    @Test
    public void addCallbackFailureOnLinkToDeath() throws Exception {
        doThrow(new RemoteException())
                .when(mAppBinder).linkToDeath(any(IBinder.DeathRecipient.class), anyInt());
        mWifiTrafficPoller.addCallback(mTrafficStateCallback);
        verify(mAppBinder).linkToDeath(any(IBinder.DeathRecipient.class), anyInt());

        mWifiTrafficPoller.notifyOnDataActivity(TX_PACKET_COUNT, RX_PACKET_COUNT);

        // Client should not get any message callback add failed.
        verify(mTrafficStateCallback, never()).onStateChanged(anyInt());
    }

    /** Test that if the data activity didn't change, the client is not notified. */
    @Test
    public void unchangedDataActivityNotNotified() throws Exception {
        mWifiTrafficPoller.addCallback(mTrafficStateCallback);
        mWifiTrafficPoller.notifyOnDataActivity(TX_PACKET_COUNT, RX_PACKET_COUNT);

        verify(mTrafficStateCallback).onStateChanged(
                WifiManager.TrafficStateCallback.DATA_ACTIVITY_INOUT);

        // since TX and RX both increased, should still be INOUT. But since it's the same data
        // activity as before, the callback should not be triggered again.
        mWifiTrafficPoller.notifyOnDataActivity(TX_PACKET_COUNT + 10, RX_PACKET_COUNT + 10);

        // still only called once
        verify(mTrafficStateCallback).onStateChanged(anyInt());
    }

    /**
     * If there are 2 callbacks, but the data activity only changed for one of them, only that
     * callback should be triggered.
     */
    @Test
    public void multipleCallbacksOnlyChangedNotified() throws Exception {
        mWifiTrafficPoller.addCallback(mTrafficStateCallback);
        mWifiTrafficPoller.notifyOnDataActivity(TX_PACKET_COUNT, RX_PACKET_COUNT);

        verify(mTrafficStateCallback).onStateChanged(
                WifiManager.TrafficStateCallback.DATA_ACTIVITY_INOUT);
        verify(mTrafficStateCallback2, never()).onStateChanged(anyInt());

        mWifiTrafficPoller.addCallback(mTrafficStateCallback2);
        mWifiTrafficPoller.notifyOnDataActivity(TX_PACKET_COUNT + 10, RX_PACKET_COUNT + 10);

        // still only called once
        verify(mTrafficStateCallback).onStateChanged(anyInt());
        // called for the first time with INOUT
        verify(mTrafficStateCallback2)
                .onStateChanged(WifiManager.TrafficStateCallback.DATA_ACTIVITY_INOUT);
        // not called with anything else
        verify(mTrafficStateCallback2).onStateChanged(anyInt());

        // now only TX increased
        mWifiTrafficPoller.notifyOnDataActivity(TX_PACKET_COUNT + 20, RX_PACKET_COUNT + 10);

        // called once with OUT
        verify(mTrafficStateCallback)
                .onStateChanged(WifiManager.TrafficStateCallback.DATA_ACTIVITY_OUT);
        // called twice total
        verify(mTrafficStateCallback, times(2)).onStateChanged(anyInt());

        // called once with OUT
        verify(mTrafficStateCallback2)
                .onStateChanged(WifiManager.TrafficStateCallback.DATA_ACTIVITY_OUT);
        // called twice total
        verify(mTrafficStateCallback2, times(2)).onStateChanged(anyInt());
    }
}
