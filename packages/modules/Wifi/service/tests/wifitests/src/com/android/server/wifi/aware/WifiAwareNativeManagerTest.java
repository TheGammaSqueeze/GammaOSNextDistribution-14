/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.server.wifi.aware;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.hardware.wifi.V1_0.WifiStatus;
import android.hardware.wifi.V1_0.WifiStatusCode;
import android.os.Handler;
import android.os.WorkSource;

import androidx.test.filters.SmallTest;

import com.android.server.wifi.HalDeviceManager;
import com.android.server.wifi.WifiBaseTest;
import com.android.server.wifi.hal.WifiNanIface;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit test harness for WifiAwareNativeManager.
 */
@SmallTest
public class WifiAwareNativeManagerTest extends WifiBaseTest {
    private static final WorkSource TEST_WS = new WorkSource();

    private WifiAwareNativeManager mDut;
    @Mock private WifiAwareStateManager mWifiAwareStateManagerMock;
    @Mock private HalDeviceManager mHalDeviceManager;
    @Mock private WifiAwareNativeCallback mWifiAwareNativeCallback;
    @Mock private WifiNanIface mWifiNanIfaceMock;
    @Mock private Handler mHandlerMock;
    private ArgumentCaptor<HalDeviceManager.ManagerStatusListener> mManagerStatusListenerCaptor =
            ArgumentCaptor.forClass(HalDeviceManager.ManagerStatusListener.class);
    private ArgumentCaptor<HalDeviceManager.InterfaceDestroyedListener>
            mDestroyedListenerCaptor = ArgumentCaptor.forClass(
            HalDeviceManager.InterfaceDestroyedListener.class);
    private InOrder mInOrder;
    @Rule public ErrorCollector collector = new ErrorCollector();

    private WifiStatus mStatusOk;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mStatusOk = new WifiStatus();
        mStatusOk.code = WifiStatusCode.SUCCESS;

        when(mWifiNanIfaceMock.registerFrameworkCallback(any())).thenReturn(true);

        mDut = new WifiAwareNativeManager(mWifiAwareStateManagerMock,
                mHalDeviceManager, mWifiAwareNativeCallback);
        mDut.enableVerboseLogging(true, true);
        mDut.start(mHandlerMock);

        mInOrder = inOrder(mWifiAwareStateManagerMock, mHalDeviceManager, mWifiNanIfaceMock);

        // validate (and capture) that register manage status callback
        mInOrder.verify(mHalDeviceManager).initialize();
        mInOrder.verify(mHalDeviceManager).registerStatusListener(
                mManagerStatusListenerCaptor.capture(), any());
    }

    /**
     * Test the control flow of the manager when Aware isn't being actively used:
     *
     * 1. onStatusChange (ready/started)
     * 2. on available -> enableUsage
     * 3. onStatusChange (!started) -> disableUsage
     * 4. onStatusChange (ready/started) + available -> enableUsage
     * 5. on not available -> disableUsage
     *
     * --> no interface creation at any point!
     */
    @Test
    public void testControlFlowWithoutInterface() {
        // configure HalDeviceManager as ready/wifi started (and to return an interface if
        // requested)
        when(mHalDeviceManager.isStarted()).thenReturn(true);
        when(mHalDeviceManager.createNanIface(any(), any(), any()))
                .thenReturn(mWifiNanIfaceMock);

        // 1. onStatusChange (ready/started)
        mManagerStatusListenerCaptor.getValue().onStatusChanged();
        mInOrder.verify(mWifiAwareStateManagerMock).tryToGetAwareCapability();

        // 2. onStatusChange (not ready) -> disableUsage
        when(mHalDeviceManager.isStarted()).thenReturn(false);
        mManagerStatusListenerCaptor.getValue().onStatusChanged();

        mInOrder.verify(mWifiAwareStateManagerMock).disableUsage(false);

        // 3. onStatusChange (ready/started) + available -> enableUsage
        when(mHalDeviceManager.isStarted()).thenReturn(true);
        mManagerStatusListenerCaptor.getValue().onStatusChanged();
        mInOrder.verify(mWifiAwareStateManagerMock).tryToGetAwareCapability();

        mInOrder.verify(mHalDeviceManager, never()).createNanIface(any(), any(), any());
        verifyNoMoreInteractions(mWifiAwareStateManagerMock, mWifiNanIfaceMock);
        assertNull("Interface non-null!", mDut.getWifiNanIface());
    }

    /**
     * Test the control flow (and reference counting) of the manager when Aware is actively used and
     * reference counted (i.e. irregular requests/releases).
     */
    @Test
    public void testReferenceCounting() throws Exception {
        // configure HalDeviceManager as ready/wifi started (and to return an interface if
        // requested)
        when(mHalDeviceManager.isStarted()).thenReturn(true);
        when(mHalDeviceManager.createNanIface(any(), any(), any()))
                .thenReturn(mWifiNanIfaceMock);

        // 1. onStatusChange (ready/started)
        mManagerStatusListenerCaptor.getValue().onStatusChanged();
        mInOrder.verify(mWifiAwareStateManagerMock).tryToGetAwareCapability();
        assertNull("Interface non-null!", mDut.getWifiNanIface());

        // 2. request (interface obtained)
        mDut.tryToGetAware(TEST_WS);
        mInOrder.verify(mHalDeviceManager).createNanIface(mDestroyedListenerCaptor.capture(),
                any(), eq(TEST_WS));
        mInOrder.verify(mWifiNanIfaceMock).registerFrameworkCallback(any());
        mInOrder.verify(mWifiNanIfaceMock).enableVerboseLogging(anyBoolean());
        assertEquals("Interface mismatch", mWifiNanIfaceMock, mDut.getWifiNanIface());

        // 3. release (interface released)
        mDut.releaseAware();
        mInOrder.verify(mHalDeviceManager).removeIface(mWifiNanIfaceMock);
        assertNull("Interface non-null!", mDut.getWifiNanIface());

        mDestroyedListenerCaptor.getValue().onDestroyed("nan0");

        // 4. request (interface obtained)
        mDut.tryToGetAware(TEST_WS);
        mInOrder.verify(mHalDeviceManager).createNanIface(mDestroyedListenerCaptor.capture(),
                any(), eq(TEST_WS));
        mInOrder.verify(mWifiNanIfaceMock).registerFrameworkCallback(any());
        mInOrder.verify(mWifiNanIfaceMock).enableVerboseLogging(anyBoolean());
        assertEquals("Interface mismatch", mWifiNanIfaceMock, mDut.getWifiNanIface());

        // 5. request (nop - already have interface)
        mDut.tryToGetAware(TEST_WS);
        assertEquals("Interface mismatch", mWifiNanIfaceMock, mDut.getWifiNanIface());

        // 6. release (nop - reference counting requests)
        mDut.releaseAware();
        assertEquals("Interface mismatch", mWifiNanIfaceMock, mDut.getWifiNanIface());

        // 7. release (interface released)
        mDut.releaseAware();
        mInOrder.verify(mHalDeviceManager).removeIface(mWifiNanIfaceMock);
        assertNull("Interface non-null!", mDut.getWifiNanIface());

        mDestroyedListenerCaptor.getValue().onDestroyed("nan0");

        mInOrder.verify(mHalDeviceManager, never()).createNanIface(any(), any(), any());
        mInOrder.verify(mHalDeviceManager, never()).removeIface(any());
        verifyNoMoreInteractions(mWifiAwareStateManagerMock, mWifiNanIfaceMock);
    }

    /**
     * Test the control flow when the interface gets deleted due to external
     */
    @Test
    public void testRequestFlowWithAsyncDeletes() throws Exception {
        // configure HalDeviceManager as ready/wifi started (and to return an interface if
        // requested)
        when(mHalDeviceManager.isStarted()).thenReturn(true);
        when(mHalDeviceManager.createNanIface(any(), any(), any()))
                .thenReturn(mWifiNanIfaceMock);

        // 1. onStatusChange (ready/started)
        mManagerStatusListenerCaptor.getValue().onStatusChanged();
        mInOrder.verify(mWifiAwareStateManagerMock).tryToGetAwareCapability();
        assertNull("Interface non-null!", mDut.getWifiNanIface());

        // 2. request (interface obtained)
        mDut.tryToGetAware(TEST_WS);
        mInOrder.verify(mHalDeviceManager).createNanIface(mDestroyedListenerCaptor.capture(),
                any(), eq(TEST_WS));
        mInOrder.verify(mWifiNanIfaceMock).registerFrameworkCallback(any());
        mInOrder.verify(mWifiNanIfaceMock).enableVerboseLogging(anyBoolean());
        assertEquals("Interface mismatch", mWifiNanIfaceMock, mDut.getWifiNanIface());

        // 3. interface gets destroyed
        mDestroyedListenerCaptor.getValue().onDestroyed("nan0");

        mInOrder.verify(mWifiAwareStateManagerMock).disableUsage(true);
        assertNull("Interface non-null!", mDut.getWifiNanIface());

        // 4. a release doesn't do much
        mDut.releaseAware();

        mInOrder.verify(mHalDeviceManager, never()).createNanIface(any(), any(), any());
        mInOrder.verify(mHalDeviceManager, never()).removeIface(any());
        verifyNoMoreInteractions(mWifiAwareStateManagerMock, mWifiNanIfaceMock);
    }
}
