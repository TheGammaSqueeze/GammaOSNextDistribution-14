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

import static com.android.networkstack.tethering.OffloadHardwareInterface.OFFLOAD_HAL_VERSION_HIDL_1_0;
import static com.android.networkstack.tethering.OffloadHardwareInterface.OFFLOAD_HAL_VERSION_HIDL_1_1;
import static com.android.networkstack.tethering.util.TetheringUtils.uint16;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.hardware.tetheroffload.config.V1_0.IOffloadConfig;
import android.hardware.tetheroffload.control.V1_0.IOffloadControl;
import android.hardware.tetheroffload.control.V1_0.NatTimeoutUpdate;
import android.hardware.tetheroffload.control.V1_0.NetworkProtocol;
import android.hardware.tetheroffload.control.V1_1.ITetheringOffloadCallback;
import android.hardware.tetheroffload.control.V1_1.OffloadCallbackEvent;
import android.os.Handler;
import android.os.NativeHandle;
import android.os.test.TestLooper;
import android.system.OsConstants;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.net.module.util.SharedLog;
import com.android.networkstack.tethering.OffloadHardwareInterface.ForwardedStats;
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
public final class OffloadHalHidlImplTest {
    private static final String RMNET0 = "test_rmnet_data0";

    private final SharedLog mLog = new SharedLog("test");
    private final TestLooper mTestLooper = new TestLooper();

    private OffloadHalHidlImpl mIOffloadHal;
    private IOffloadConfig mIOffloadConfigMock;
    private IOffloadControl mIOffloadControlMock;
    private ITetheringOffloadCallback mTetheringOffloadCallback;
    private OffloadHalCallback mOffloadHalCallback;

    private void createAndInitOffloadHal(int version) throws Exception {
        final FileDescriptor fd1 = new FileDescriptor();
        final FileDescriptor fd2 = new FileDescriptor();
        final NativeHandle handle1 = new NativeHandle(fd1, true);
        final NativeHandle handle2 = new NativeHandle(fd2, true);
        mIOffloadConfigMock = mock(IOffloadConfig.class);
        switch (version) {
            case OFFLOAD_HAL_VERSION_HIDL_1_0:
                mIOffloadControlMock = mock(IOffloadControl.class);
                break;
            case OFFLOAD_HAL_VERSION_HIDL_1_1:
                mIOffloadControlMock = mock(
                        android.hardware.tetheroffload.control.V1_1.IOffloadControl.class);
                break;
            default:
                fail("Nonexistent HAL version");
                return;
        }
        mIOffloadHal = new OffloadHalHidlImpl(version, mIOffloadConfigMock,
                mIOffloadControlMock, new Handler(mTestLooper.getLooper()), mLog);
        mIOffloadHal.initOffload(handle1, handle2, mOffloadHalCallback);

        final ArgumentCaptor<NativeHandle> nativeHandleCaptor1 =
                ArgumentCaptor.forClass(NativeHandle.class);
        final ArgumentCaptor<NativeHandle> nativeHandleCaptor2 =
                ArgumentCaptor.forClass(NativeHandle.class);
        final ArgumentCaptor<ITetheringOffloadCallback> offloadCallbackCaptor =
                ArgumentCaptor.forClass(ITetheringOffloadCallback.class);
        verify(mIOffloadConfigMock).setHandles(nativeHandleCaptor1.capture(),
                nativeHandleCaptor2.capture(), any());
        verify(mIOffloadControlMock).initOffload(offloadCallbackCaptor.capture(), any());
        assertEquals(nativeHandleCaptor1.getValue().getFileDescriptor().getInt$(),
                handle1.getFileDescriptor().getInt$());
        assertEquals(nativeHandleCaptor2.getValue().getFileDescriptor().getInt$(),
                handle2.getFileDescriptor().getInt$());
        mTetheringOffloadCallback = offloadCallbackCaptor.getValue();
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mOffloadHalCallback = spy(new OffloadHalCallback());
    }

    @Test
    public void testGetForwardedStats() throws Exception {
        createAndInitOffloadHal(OFFLOAD_HAL_VERSION_HIDL_1_0);
        final long rxBytes = 12345;
        final long txBytes = 67890;
        doAnswer(invocation -> {
            ((IOffloadControl.getForwardedStatsCallback) invocation.getArgument(1))
                    .onValues(rxBytes, txBytes);
            return null;
        }).when(mIOffloadControlMock).getForwardedStats(eq(RMNET0), any());
        final ForwardedStats stats = mIOffloadHal.getForwardedStats(RMNET0);
        verify(mIOffloadControlMock).getForwardedStats(eq(RMNET0), any());
        assertNotNull(stats);
        assertEquals(rxBytes, stats.rxBytes);
        assertEquals(txBytes, stats.txBytes);
    }

    private void doTestSetLocalPrefixes(boolean expectSuccess) throws Exception {
        createAndInitOffloadHal(OFFLOAD_HAL_VERSION_HIDL_1_0);
        final ArrayList<String> localPrefixes = new ArrayList<>();
        localPrefixes.add("127.0.0.0/8");
        localPrefixes.add("fe80::/64");
        doAnswer(invocation -> {
            ((IOffloadControl.setLocalPrefixesCallback) invocation.getArgument(1))
                    .onValues(expectSuccess, "");
            return null;
        }).when(mIOffloadControlMock).setLocalPrefixes(eq(localPrefixes), any());
        assertEquals(expectSuccess, mIOffloadHal.setLocalPrefixes(localPrefixes));
        verify(mIOffloadControlMock).setLocalPrefixes(eq(localPrefixes), any());
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
        createAndInitOffloadHal(OFFLOAD_HAL_VERSION_HIDL_1_0);
        final long limit = 12345;
        doAnswer(invocation -> {
            ((IOffloadControl.setDataLimitCallback) invocation.getArgument(2))
                    .onValues(expectSuccess, "");
            return null;
        }).when(mIOffloadControlMock).setDataLimit(eq(RMNET0), eq(limit), any());
        assertEquals(expectSuccess, mIOffloadHal.setDataLimit(RMNET0, limit));
        verify(mIOffloadControlMock).setDataLimit(eq(RMNET0), eq(limit), any());
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
        createAndInitOffloadHal(OFFLOAD_HAL_VERSION_HIDL_1_1);
        final long warning = 12345;
        final long limit = 67890;
        doAnswer(invocation -> {
            ((android.hardware.tetheroffload.control.V1_1.IOffloadControl
                    .setDataWarningAndLimitCallback) invocation.getArgument(3))
                    .onValues(expectSuccess, "");
            return null;
        }).when((android.hardware.tetheroffload.control.V1_1.IOffloadControl) mIOffloadControlMock)
                .setDataWarningAndLimit(eq(RMNET0), eq(warning), eq(limit), any());
        assertEquals(expectSuccess, mIOffloadHal.setDataWarningAndLimit(RMNET0, warning, limit));
        verify((android.hardware.tetheroffload.control.V1_1.IOffloadControl) mIOffloadControlMock)
                .setDataWarningAndLimit(eq(RMNET0), eq(warning), eq(limit), any());
    }

    @Test
    public void testSetDataWarningAndLimitSuccess() throws Exception {
        doTestSetDataWarningAndLimit(true);
    }

    @Test
    public void testSetDataWarningAndLimitFailure() throws Exception {
        // Verify that V1.0 control HAL would reject the function call with exception.
        createAndInitOffloadHal(OFFLOAD_HAL_VERSION_HIDL_1_0);
        final long warning = 12345;
        final long limit = 67890;
        assertThrows(UnsupportedOperationException.class,
                () -> mIOffloadHal.setDataWarningAndLimit(RMNET0, warning, limit));

        doTestSetDataWarningAndLimit(false);
    }

    private void doTestSetUpstreamParameters(boolean expectSuccess) throws Exception {
        createAndInitOffloadHal(OFFLOAD_HAL_VERSION_HIDL_1_0);
        final String v4addr = "192.168.10.1";
        final String v4gateway = "192.168.10.255";
        final ArrayList<String> v6gws = new ArrayList<>(0);
        v6gws.add("2001:db8::1");
        doAnswer(invocation -> {
            ((IOffloadControl.setUpstreamParametersCallback) invocation.getArgument(4))
                    .onValues(expectSuccess, "");
            return null;
        }).when(mIOffloadControlMock).setUpstreamParameters(eq(RMNET0), eq(v4addr), eq(v4gateway),
                eq(v6gws), any());
        assertEquals(expectSuccess, mIOffloadHal.setUpstreamParameters(RMNET0, v4addr, v4gateway,
                v6gws));
        verify(mIOffloadControlMock).setUpstreamParameters(eq(RMNET0), eq(v4addr), eq(v4gateway),
                eq(v6gws), any());
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
        createAndInitOffloadHal(OFFLOAD_HAL_VERSION_HIDL_1_0);
        final String ifName = "wlan1";
        final String prefix = "192.168.43.0/24";
        doAnswer(invocation -> {
            ((IOffloadControl.addDownstreamCallback) invocation.getArgument(2))
                    .onValues(expectSuccess, "");
            return null;
        }).when(mIOffloadControlMock).addDownstream(eq(ifName), eq(prefix), any());
        assertEquals(expectSuccess, mIOffloadHal.addDownstream(ifName, prefix));
        verify(mIOffloadControlMock).addDownstream(eq(ifName), eq(prefix), any());
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
        createAndInitOffloadHal(OFFLOAD_HAL_VERSION_HIDL_1_0);
        final String ifName = "wlan1";
        final String prefix = "192.168.43.0/24";
        doAnswer(invocation -> {
            ((IOffloadControl.removeDownstreamCallback) invocation.getArgument(2))
                    .onValues(expectSuccess, "");
            return null;
        }).when(mIOffloadControlMock).removeDownstream(eq(ifName), eq(prefix), any());
        assertEquals(expectSuccess, mIOffloadHal.removeDownstream(ifName, prefix));
        verify(mIOffloadControlMock).removeDownstream(eq(ifName), eq(prefix), any());
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
        createAndInitOffloadHal(OFFLOAD_HAL_VERSION_HIDL_1_0);

        mTetheringOffloadCallback.onEvent(OffloadCallbackEvent.OFFLOAD_STARTED);
        mTestLooper.dispatchAll();
        verify(mOffloadHalCallback).onStarted();

        mTetheringOffloadCallback.onEvent(OffloadCallbackEvent.OFFLOAD_STOPPED_ERROR);
        mTestLooper.dispatchAll();
        verify(mOffloadHalCallback).onStoppedError();

        mTetheringOffloadCallback.onEvent(OffloadCallbackEvent.OFFLOAD_STOPPED_UNSUPPORTED);
        mTestLooper.dispatchAll();
        verify(mOffloadHalCallback).onStoppedUnsupported();

        mTetheringOffloadCallback.onEvent(OffloadCallbackEvent.OFFLOAD_SUPPORT_AVAILABLE);
        mTestLooper.dispatchAll();
        verify(mOffloadHalCallback).onSupportAvailable();

        mTetheringOffloadCallback.onEvent(OffloadCallbackEvent.OFFLOAD_STOPPED_LIMIT_REACHED);
        mTestLooper.dispatchAll();
        verify(mOffloadHalCallback).onStoppedLimitReached();

        final NatTimeoutUpdate tcpParams = buildNatTimeoutUpdate(NetworkProtocol.TCP);
        mTetheringOffloadCallback.updateTimeout(tcpParams);
        mTestLooper.dispatchAll();
        verify(mOffloadHalCallback).onNatTimeoutUpdate(eq(OsConstants.IPPROTO_TCP),
                eq(tcpParams.src.addr),
                eq(uint16(tcpParams.src.port)),
                eq(tcpParams.dst.addr),
                eq(uint16(tcpParams.dst.port)));

        final NatTimeoutUpdate udpParams = buildNatTimeoutUpdate(NetworkProtocol.UDP);
        mTetheringOffloadCallback.updateTimeout(udpParams);
        mTestLooper.dispatchAll();
        verify(mOffloadHalCallback).onNatTimeoutUpdate(eq(OsConstants.IPPROTO_UDP),
                eq(udpParams.src.addr),
                eq(uint16(udpParams.src.port)),
                eq(udpParams.dst.addr),
                eq(uint16(udpParams.dst.port)));
        reset(mOffloadHalCallback);

        createAndInitOffloadHal(OFFLOAD_HAL_VERSION_HIDL_1_1);

        // Verify the interface will process the events that comes from V1.1 HAL.
        mTetheringOffloadCallback.onEvent_1_1(OffloadCallbackEvent.OFFLOAD_STARTED);
        mTestLooper.dispatchAll();
        final InOrder inOrder = inOrder(mOffloadHalCallback);
        inOrder.verify(mOffloadHalCallback).onStarted();
        inOrder.verifyNoMoreInteractions();

        mTetheringOffloadCallback.onEvent_1_1(OffloadCallbackEvent.OFFLOAD_WARNING_REACHED);
        mTestLooper.dispatchAll();
        inOrder.verify(mOffloadHalCallback).onWarningReached();
        inOrder.verifyNoMoreInteractions();
    }

    private NatTimeoutUpdate buildNatTimeoutUpdate(final int proto) {
        final NatTimeoutUpdate params = new NatTimeoutUpdate();
        params.proto = proto;
        params.src.addr = "192.168.43.200";
        params.src.port = 100;
        params.dst.addr = "172.50.46.169";
        params.dst.port = 150;
        return params;
    }
}
