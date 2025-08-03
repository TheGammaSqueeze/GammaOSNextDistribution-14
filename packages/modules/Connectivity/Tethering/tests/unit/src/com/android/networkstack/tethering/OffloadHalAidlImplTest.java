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

package com.android.networkstack.tethering;

import static com.android.networkstack.tethering.OffloadHardwareInterface.OFFLOAD_HAL_VERSION_AIDL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.hardware.tetheroffload.ForwardedStats;
import android.hardware.tetheroffload.IOffload;
import android.hardware.tetheroffload.IPv4AddrPortPair;
import android.hardware.tetheroffload.ITetheringOffloadCallback;
import android.hardware.tetheroffload.NatTimeoutUpdate;
import android.hardware.tetheroffload.NetworkProtocol;
import android.hardware.tetheroffload.OffloadCallbackEvent;
import android.os.Handler;
import android.os.NativeHandle;
import android.os.ParcelFileDescriptor;
import android.os.ServiceSpecificException;
import android.os.test.TestLooper;
import android.system.OsConstants;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.net.module.util.SharedLog;
import com.android.networkstack.tethering.OffloadHardwareInterface.OffloadHalCallback;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockitoAnnotations;

import java.io.FileDescriptor;
import java.util.ArrayList;

@RunWith(AndroidJUnit4.class)
@SmallTest
public final class OffloadHalAidlImplTest {
    private static final String RMNET0 = "test_rmnet_data0";

    private final SharedLog mLog = new SharedLog("test");
    private final TestLooper mTestLooper = new TestLooper();

    private IOffload mIOffloadMock;
    private OffloadHalAidlImpl mIOffloadHal;
    private ITetheringOffloadCallback mTetheringOffloadCallback;
    private OffloadHalCallback mOffloadHalCallback;

    private void initAndValidateOffloadHal(boolean initSuccess)
            throws Exception {
        final FileDescriptor fd1 = new FileDescriptor();
        final FileDescriptor fd2 = new FileDescriptor();
        final NativeHandle handle1 = new NativeHandle(fd1, true);
        final NativeHandle handle2 = new NativeHandle(fd2, true);
        final ArgumentCaptor<ParcelFileDescriptor> fdCaptor1 =
                ArgumentCaptor.forClass(ParcelFileDescriptor.class);
        final ArgumentCaptor<ParcelFileDescriptor> fdCaptor2 =
                ArgumentCaptor.forClass(ParcelFileDescriptor.class);
        final ArgumentCaptor<ITetheringOffloadCallback> offloadCallbackCaptor =
                ArgumentCaptor.forClass(ITetheringOffloadCallback.class);
        if (initSuccess) {
            doNothing().when(mIOffloadMock).initOffload(any(), any(), any());
        } else {
            doThrow(new IllegalStateException()).when(mIOffloadMock).initOffload(any(), any(),
                    any());
        }
        assertEquals(mIOffloadHal.initOffload(handle1, handle2, mOffloadHalCallback),
                     initSuccess);
        verify(mIOffloadMock).initOffload(fdCaptor1.capture(), fdCaptor2.capture(),
                                          offloadCallbackCaptor.capture());
        assertEquals(fdCaptor1.getValue().getFd(), fd1.getInt$());
        assertEquals(fdCaptor2.getValue().getFd(), fd2.getInt$());
        mTetheringOffloadCallback = offloadCallbackCaptor.getValue();
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mIOffloadMock = mock(IOffload.class);
        mIOffloadHal = new OffloadHalAidlImpl(OFFLOAD_HAL_VERSION_AIDL, mIOffloadMock,
                new Handler(mTestLooper.getLooper()), mLog);
        mOffloadHalCallback = spy(new OffloadHalCallback());
    }

    @Test
    public void testInitOffloadSuccess() throws Exception {
        initAndValidateOffloadHal(true /* initSuccess */);
    }

    @Test
    public void testInitOffloadFailure() throws Exception {
        initAndValidateOffloadHal(false /* initSuccess */);
    }

    @Test
    public void testStopOffloadSuccess() throws Exception {
        initAndValidateOffloadHal(true);
        doNothing().when(mIOffloadMock).stopOffload();
        assertTrue(mIOffloadHal.stopOffload());
        verify(mIOffloadMock).stopOffload();
    }

    @Test
    public void testStopOffloadFailure() throws Exception {
        initAndValidateOffloadHal(true);
        doThrow(new IllegalStateException()).when(mIOffloadMock).stopOffload();
        assertFalse(mIOffloadHal.stopOffload());
    }

    private void doTestGetForwardedStats(boolean expectSuccess) throws Exception {
        initAndValidateOffloadHal(true);
        final ForwardedStats returnStats = new ForwardedStats();
        if (expectSuccess) {
            returnStats.rxBytes = 12345;
            returnStats.txBytes = 67890;
            when(mIOffloadMock.getForwardedStats(anyString())).thenReturn(returnStats);
        } else {
            when(mIOffloadMock.getForwardedStats(anyString()))
                    .thenThrow(new ServiceSpecificException(IOffload.ERROR_CODE_UNUSED));
        }
        final OffloadHardwareInterface.ForwardedStats stats =
                mIOffloadHal.getForwardedStats(RMNET0);
        verify(mIOffloadMock).getForwardedStats(eq(RMNET0));
        assertNotNull(stats);
        assertEquals(stats.rxBytes, returnStats.rxBytes);
        assertEquals(stats.txBytes, returnStats.txBytes);
    }

    @Test
    public void testGetForwardedStatsSuccess() throws Exception {
        doTestGetForwardedStats(true);
    }

    @Test
    public void testGetForwardedStatsFailure() throws Exception {
        doTestGetForwardedStats(false);
    }

    private void doTestSetLocalPrefixes(boolean expectSuccess) throws Exception {
        initAndValidateOffloadHal(true);
        final ArrayList<String> localPrefixes = new ArrayList<>();
        localPrefixes.add("127.0.0.0/8");
        localPrefixes.add("fe80::/64");
        final String[] localPrefixesArray =
                localPrefixes.toArray(new String[localPrefixes.size()]);
        if (expectSuccess) {
            doNothing().when(mIOffloadMock).setLocalPrefixes(any());
        } else {
            doThrow(new IllegalArgumentException()).when(mIOffloadMock).setLocalPrefixes(any());
        }
        assertEquals(expectSuccess, mIOffloadHal.setLocalPrefixes(localPrefixes));
        verify(mIOffloadMock).setLocalPrefixes(eq(localPrefixesArray));
    }

    @Test
    public void testSetLocalPrefixesSuccess() throws Exception {
        doTestSetLocalPrefixes(true);
    }

    @Test
    public void testSetLocalPrefixesFailure() throws Exception {
        doTestSetLocalPrefixes(false);
    }

    private void doTestSetDataLimit(boolean expectSuccess) throws Exception {
        initAndValidateOffloadHal(true);
        final long limit = 12345;
        if (expectSuccess) {
            doNothing().when(mIOffloadMock).setDataWarningAndLimit(anyString(), anyLong(),
                    anyLong());
        } else {
            doThrow(new IllegalArgumentException())
                    .when(mIOffloadMock).setDataWarningAndLimit(anyString(), anyLong(), anyLong());
        }
        assertEquals(expectSuccess, mIOffloadHal.setDataLimit(RMNET0, limit));
        verify(mIOffloadMock).setDataWarningAndLimit(eq(RMNET0), eq(Long.MAX_VALUE), eq(limit));
    }

    @Test
    public void testSetDataLimitSuccess() throws Exception {
        doTestSetDataLimit(true);
    }

    @Test
    public void testSetDataLimitFailure() throws Exception {
        doTestSetDataLimit(false);
    }

    private void doTestSetDataWarningAndLimit(boolean expectSuccess) throws Exception {
        initAndValidateOffloadHal(true);
        final long warning = 12345;
        final long limit = 67890;
        if (expectSuccess) {
            doNothing().when(mIOffloadMock).setDataWarningAndLimit(anyString(), anyLong(),
                    anyLong());
        } else {
            doThrow(new IllegalArgumentException())
                    .when(mIOffloadMock).setDataWarningAndLimit(anyString(), anyLong(), anyLong());
        }
        assertEquals(expectSuccess, mIOffloadHal.setDataWarningAndLimit(RMNET0, warning, limit));
        verify(mIOffloadMock).setDataWarningAndLimit(eq(RMNET0), eq(warning), eq(limit));
    }

    @Test
    public void testSetDataWarningAndLimitSuccess() throws Exception {
        doTestSetDataWarningAndLimit(true);
    }

    @Test
    public void testSetDataWarningAndLimitFailure() throws Exception {
        doTestSetDataWarningAndLimit(false);
    }

    private void doTestSetUpstreamParameters(boolean expectSuccess) throws Exception {
        initAndValidateOffloadHal(true);
        final String v4addr = "192.168.10.1";
        final String v4gateway = "192.168.10.255";
        final ArrayList<String> v6gws = new ArrayList<>(0);
        v6gws.add("2001:db8::1");
        String[] v6gwsArray = v6gws.toArray(new String[v6gws.size()]);
        if (expectSuccess) {
            doNothing().when(mIOffloadMock).setUpstreamParameters(anyString(), anyString(),
                    anyString(), any());
        } else {
            doThrow(new IllegalArgumentException()).when(mIOffloadMock).setUpstreamParameters(
                    anyString(), anyString(), anyString(), any());
        }
        assertEquals(expectSuccess, mIOffloadHal.setUpstreamParameters(RMNET0, v4addr, v4gateway,
                  v6gws));
        verify(mIOffloadMock).setUpstreamParameters(eq(RMNET0), eq(v4addr), eq(v4gateway),
                  eq(v6gwsArray));
    }

    @Test
    public void testSetUpstreamParametersSuccess() throws Exception {
        doTestSetUpstreamParameters(true);
    }

    @Test
    public void testSetUpstreamParametersFailure() throws Exception {
        doTestSetUpstreamParameters(false);
    }

    private void doTestAddDownstream(boolean expectSuccess) throws Exception {
        initAndValidateOffloadHal(true);
        final String ifName = "wlan1";
        final String prefix = "192.168.43.0/24";
        if (expectSuccess) {
            doNothing().when(mIOffloadMock).addDownstream(anyString(), anyString());
        } else {
            doThrow(new IllegalStateException()).when(mIOffloadMock).addDownstream(anyString(),
                    anyString());
        }
        assertEquals(expectSuccess, mIOffloadHal.addDownstream(ifName, prefix));
        verify(mIOffloadMock).addDownstream(eq(ifName), eq(prefix));
    }

    @Test
    public void testAddDownstreamSuccess() throws Exception {
        doTestAddDownstream(true);
    }

    @Test
    public void testAddDownstreamFailure() throws Exception {
        doTestAddDownstream(false);
    }

    private void doTestRemoveDownstream(boolean expectSuccess) throws Exception {
        initAndValidateOffloadHal(true);
        final String ifName = "wlan1";
        final String prefix = "192.168.43.0/24";
        if (expectSuccess) {
            doNothing().when(mIOffloadMock).removeDownstream(anyString(), anyString());
        } else {
            doThrow(new IllegalArgumentException()).when(mIOffloadMock).removeDownstream(
                    anyString(), anyString());
        }
        assertEquals(expectSuccess, mIOffloadHal.removeDownstream(ifName, prefix));
        verify(mIOffloadMock).removeDownstream(eq(ifName), eq(prefix));
    }

    @Test
    public void testRemoveDownstreamSuccess() throws Exception {
        doTestRemoveDownstream(true);
    }

    @Test
    public void testRemoveDownstreamFailure() throws Exception {
        doTestRemoveDownstream(false);
    }

    @Test
    public void testTetheringOffloadCallback() throws Exception {
        initAndValidateOffloadHal(true);

        mTetheringOffloadCallback.onEvent(OffloadCallbackEvent.OFFLOAD_STARTED);
        mTestLooper.dispatchAll();
        final InOrder inOrder = inOrder(mOffloadHalCallback);
        inOrder.verify(mOffloadHalCallback).onStarted();
        inOrder.verifyNoMoreInteractions();

        mTetheringOffloadCallback.onEvent(OffloadCallbackEvent.OFFLOAD_STOPPED_ERROR);
        mTestLooper.dispatchAll();
        inOrder.verify(mOffloadHalCallback).onStoppedError();
        inOrder.verifyNoMoreInteractions();

        mTetheringOffloadCallback.onEvent(OffloadCallbackEvent.OFFLOAD_STOPPED_UNSUPPORTED);
        mTestLooper.dispatchAll();
        inOrder.verify(mOffloadHalCallback).onStoppedUnsupported();
        inOrder.verifyNoMoreInteractions();

        mTetheringOffloadCallback.onEvent(OffloadCallbackEvent.OFFLOAD_SUPPORT_AVAILABLE);
        mTestLooper.dispatchAll();
        inOrder.verify(mOffloadHalCallback).onSupportAvailable();
        inOrder.verifyNoMoreInteractions();

        mTetheringOffloadCallback.onEvent(OffloadCallbackEvent.OFFLOAD_STOPPED_LIMIT_REACHED);
        mTestLooper.dispatchAll();
        inOrder.verify(mOffloadHalCallback).onStoppedLimitReached();
        inOrder.verifyNoMoreInteractions();

        mTetheringOffloadCallback.onEvent(OffloadCallbackEvent.OFFLOAD_WARNING_REACHED);
        mTestLooper.dispatchAll();
        inOrder.verify(mOffloadHalCallback).onWarningReached();
        inOrder.verifyNoMoreInteractions();

        final NatTimeoutUpdate tcpParams = buildNatTimeoutUpdate(NetworkProtocol.TCP);
        mTetheringOffloadCallback.updateTimeout(tcpParams);
        mTestLooper.dispatchAll();
        inOrder.verify(mOffloadHalCallback).onNatTimeoutUpdate(eq(OsConstants.IPPROTO_TCP),
                eq(tcpParams.src.addr),
                eq(tcpParams.src.port),
                eq(tcpParams.dst.addr),
                eq(tcpParams.dst.port));
        inOrder.verifyNoMoreInteractions();

        final NatTimeoutUpdate udpParams = buildNatTimeoutUpdate(NetworkProtocol.UDP);
        mTetheringOffloadCallback.updateTimeout(udpParams);
        mTestLooper.dispatchAll();
        inOrder.verify(mOffloadHalCallback).onNatTimeoutUpdate(eq(OsConstants.IPPROTO_UDP),
                eq(udpParams.src.addr),
                eq(udpParams.src.port),
                eq(udpParams.dst.addr),
                eq(udpParams.dst.port));
        inOrder.verifyNoMoreInteractions();
    }

    private NatTimeoutUpdate buildNatTimeoutUpdate(final int proto) {
        final NatTimeoutUpdate params = new NatTimeoutUpdate();
        params.proto = proto;
        params.src = new IPv4AddrPortPair();
        params.dst = new IPv4AddrPortPair();
        params.src.addr = "192.168.43.200";
        params.src.port = 100;
        params.dst.addr = "172.50.46.169";
        params.dst.port = 150;
        return params;
    }
}
