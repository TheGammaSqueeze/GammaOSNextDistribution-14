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

package com.android.networkstack.tethering;

import static android.system.OsConstants.AF_INET;
import static android.system.OsConstants.AF_UNIX;
import static android.system.OsConstants.SOCK_STREAM;

import static com.android.networkstack.tethering.OffloadHardwareInterface.NF_NETLINK_CONNTRACK_DESTROY;
import static com.android.networkstack.tethering.OffloadHardwareInterface.NF_NETLINK_CONNTRACK_NEW;
import static com.android.networkstack.tethering.OffloadHardwareInterface.OFFLOAD_HAL_VERSION_AIDL;
import static com.android.networkstack.tethering.OffloadHardwareInterface.OFFLOAD_HAL_VERSION_HIDL_1_0;
import static com.android.networkstack.tethering.OffloadHardwareInterface.OFFLOAD_HAL_VERSION_HIDL_1_1;
import static com.android.networkstack.tethering.OffloadHardwareInterface.OFFLOAD_HAL_VERSION_NONE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.Handler;
import android.os.NativeHandle;
import android.os.test.TestLooper;
import android.system.ErrnoException;
import android.system.Os;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.net.module.util.SharedLog;
import com.android.net.module.util.netlink.StructNfGenMsg;
import com.android.net.module.util.netlink.StructNlMsgHdr;
import com.android.networkstack.tethering.OffloadHardwareInterface.ForwardedStats;
import com.android.networkstack.tethering.OffloadHardwareInterface.OffloadHalCallback;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockitoAnnotations;

import java.io.FileDescriptor;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

@RunWith(AndroidJUnit4.class)
@SmallTest
public final class OffloadHardwareInterfaceTest {
    private static final String RMNET0 = "test_rmnet_data0";

    private final TestLooper mTestLooper = new TestLooper();

    private OffloadHardwareInterface mOffloadHw;
    private OffloadHalCallback mOffloadHalCallback;

    private IOffloadHal mIOffload;
    private NativeHandle mNativeHandle1;
    private NativeHandle mNativeHandle2;

    // Random values to test Netlink message.
    private static final short TEST_TYPE = 184;
    private static final short TEST_FLAGS = 263;

    class MyDependencies extends OffloadHardwareInterface.Dependencies {
        private final int mMockOffloadHalVersion;
        MyDependencies(Handler handler, SharedLog log, final int mockOffloadHalVersion) {
            super(handler, log);
            mMockOffloadHalVersion = mockOffloadHalVersion;
            when(mIOffload.getVersion()).thenReturn(mMockOffloadHalVersion);
        }

        @Override
        public IOffloadHal getOffload() {
            return mMockOffloadHalVersion == OFFLOAD_HAL_VERSION_NONE ? null : mIOffload;
        }

        @Override
        public NativeHandle createConntrackSocket(final int groups) {
            return groups == (NF_NETLINK_CONNTRACK_NEW | NF_NETLINK_CONNTRACK_DESTROY)
                    ? mNativeHandle1
                    : mNativeHandle2;
        }
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mOffloadHalCallback = new OffloadHalCallback();
        mIOffload = mock(IOffloadHal.class);
    }

    private void startOffloadHardwareInterface(int offloadHalVersion, boolean isHalInitSuccess)
            throws Exception {
        startOffloadHardwareInterface(offloadHalVersion, isHalInitSuccess, mock(NativeHandle.class),
                mock(NativeHandle.class));
    }

    private void startOffloadHardwareInterface(int offloadHalVersion, boolean isHalInitSuccess,
            NativeHandle handle1, NativeHandle handle2) throws Exception {
        final SharedLog log = new SharedLog("test");
        final Handler handler = new Handler(mTestLooper.getLooper());
        final boolean hasNullHandle = handle1 == null || handle2 == null;
        // If offloadHalVersion is OFFLOAD_HAL_VERSION_NONE or it has null NativeHandle arguments,
        // mIOffload.initOffload() shouldn't be called.
        final int initNum = (offloadHalVersion != OFFLOAD_HAL_VERSION_NONE && !hasNullHandle)
                ? 1
                : 0;
        // If it is HIDL or has null NativeHandle argument, NativeHandles should be closed.
        final int handleCloseNum = (hasNullHandle
                || offloadHalVersion == OFFLOAD_HAL_VERSION_HIDL_1_0
                || offloadHalVersion == OFFLOAD_HAL_VERSION_HIDL_1_1) ? 1 : 0;
        mNativeHandle1 = handle1;
        mNativeHandle2 = handle2;
        when(mIOffload.initOffload(any(NativeHandle.class), any(NativeHandle.class),
                any(OffloadHalCallback.class))).thenReturn(isHalInitSuccess);
        mOffloadHw = new OffloadHardwareInterface(handler, log,
                new MyDependencies(handler, log, offloadHalVersion));
        assertEquals(isHalInitSuccess && !hasNullHandle
                ? offloadHalVersion
                : OFFLOAD_HAL_VERSION_NONE,
                mOffloadHw.initOffload(mOffloadHalCallback));
        verify(mIOffload, times(initNum)).initOffload(any(NativeHandle.class),
                any(NativeHandle.class), eq(mOffloadHalCallback));
        if (mNativeHandle1 != null) verify(mNativeHandle1, times(handleCloseNum)).close();
        if (mNativeHandle2 != null) verify(mNativeHandle2, times(handleCloseNum)).close();
    }

    @Test
    public void testInitFailureWithNoHal() throws Exception {
        startOffloadHardwareInterface(OFFLOAD_HAL_VERSION_NONE, true);
    }

    @Test
    public void testInitSuccessWithAidl() throws Exception {
        startOffloadHardwareInterface(OFFLOAD_HAL_VERSION_AIDL, true);
    }

    @Test
    public void testInitSuccessWithHidl_1_0() throws Exception {
        startOffloadHardwareInterface(OFFLOAD_HAL_VERSION_HIDL_1_0, true);
    }

    @Test
    public void testInitSuccessWithHidl_1_1() throws Exception {
        startOffloadHardwareInterface(OFFLOAD_HAL_VERSION_HIDL_1_1, true);
    }

    @Test
    public void testInitFailWithAidl() throws Exception {
        startOffloadHardwareInterface(OFFLOAD_HAL_VERSION_AIDL, false);
    }

    @Test
    public void testInitFailWithHidl_1_0() throws Exception {
        startOffloadHardwareInterface(OFFLOAD_HAL_VERSION_HIDL_1_0, false);
    }

    @Test
    public void testInitFailWithHidl_1_1() throws Exception {
        startOffloadHardwareInterface(OFFLOAD_HAL_VERSION_HIDL_1_1, false);
    }

    @Test
    public void testInitFailDueToNullHandles() throws Exception {
        startOffloadHardwareInterface(OFFLOAD_HAL_VERSION_AIDL, true, mock(NativeHandle.class),
                null);
    }

    @Test
    public void testGetForwardedStats() throws Exception {
        startOffloadHardwareInterface(OFFLOAD_HAL_VERSION_HIDL_1_0, true);
        ForwardedStats stats = new ForwardedStats(12345, 56780);
        when(mIOffload.getForwardedStats(anyString())).thenReturn(stats);
        assertEquals(mOffloadHw.getForwardedStats(RMNET0), stats);
        verify(mIOffload).getForwardedStats(eq(RMNET0));
    }

    @Test
    public void testSetLocalPrefixes() throws Exception {
        startOffloadHardwareInterface(OFFLOAD_HAL_VERSION_HIDL_1_0, true);
        final ArrayList<String> localPrefixes = new ArrayList<>();
        localPrefixes.add("127.0.0.0/8");
        localPrefixes.add("fe80::/64");
        when(mIOffload.setLocalPrefixes(any())).thenReturn(true);
        assertTrue(mOffloadHw.setLocalPrefixes(localPrefixes));
        verify(mIOffload).setLocalPrefixes(eq(localPrefixes));
        when(mIOffload.setLocalPrefixes(any())).thenReturn(false);
        assertFalse(mOffloadHw.setLocalPrefixes(localPrefixes));
    }

    @Test
    public void testSetDataLimit() throws Exception {
        startOffloadHardwareInterface(OFFLOAD_HAL_VERSION_HIDL_1_0, true);
        final long limit = 12345;
        when(mIOffload.setDataLimit(anyString(), anyLong())).thenReturn(true);
        assertTrue(mOffloadHw.setDataLimit(RMNET0, limit));
        verify(mIOffload).setDataLimit(eq(RMNET0), eq(limit));
        when(mIOffload.setDataLimit(anyString(), anyLong())).thenReturn(false);
        assertFalse(mOffloadHw.setDataLimit(RMNET0, limit));
    }

    @Test
    public void testSetDataWarningAndLimitFailureWithHidl_1_0() throws Exception {
        // Verify V1.0 control HAL would reject the function call with exception.
        startOffloadHardwareInterface(OFFLOAD_HAL_VERSION_HIDL_1_0, true);
        final long warning = 12345;
        final long limit = 67890;
        assertThrows(UnsupportedOperationException.class,
                () -> mOffloadHw.setDataWarningAndLimit(RMNET0, warning, limit));
    }

    @Test
    public void testSetDataWarningAndLimit() throws Exception {
        // Verify V1.1 control HAL could receive this function call.
        startOffloadHardwareInterface(OFFLOAD_HAL_VERSION_HIDL_1_1, true);
        final long warning = 12345;
        final long limit = 67890;
        when(mIOffload.setDataWarningAndLimit(anyString(), anyLong(), anyLong())).thenReturn(true);
        assertTrue(mOffloadHw.setDataWarningAndLimit(RMNET0, warning, limit));
        verify(mIOffload).setDataWarningAndLimit(eq(RMNET0), eq(warning), eq(limit));
        when(mIOffload.setDataWarningAndLimit(anyString(), anyLong(), anyLong())).thenReturn(false);
        assertFalse(mOffloadHw.setDataWarningAndLimit(RMNET0, warning, limit));
    }

    @Test
    public void testSetUpstreamParameters() throws Exception {
        startOffloadHardwareInterface(OFFLOAD_HAL_VERSION_HIDL_1_0, true);
        final String v4addr = "192.168.10.1";
        final String v4gateway = "192.168.10.255";
        final ArrayList<String> v6gws = new ArrayList<>(0);
        v6gws.add("2001:db8::1");
        when(mIOffload.setUpstreamParameters(anyString(), anyString(), anyString(), any()))
                .thenReturn(true);
        assertTrue(mOffloadHw.setUpstreamParameters(RMNET0, v4addr, v4gateway, v6gws));
        verify(mIOffload).setUpstreamParameters(eq(RMNET0), eq(v4addr), eq(v4gateway), eq(v6gws));

        final ArgumentCaptor<ArrayList<String>> mArrayListCaptor =
                ArgumentCaptor.forClass(ArrayList.class);
        when(mIOffload.setUpstreamParameters(anyString(), anyString(), anyString(), any()))
                .thenReturn(false);
        assertFalse(mOffloadHw.setUpstreamParameters(null, null, null, null));
        verify(mIOffload).setUpstreamParameters(eq(""), eq(""), eq(""), mArrayListCaptor.capture());
        assertEquals(mArrayListCaptor.getValue().size(), 0);
    }

    @Test
    public void testUpdateDownstream() throws Exception {
        startOffloadHardwareInterface(OFFLOAD_HAL_VERSION_HIDL_1_0, true);
        final String ifName = "wlan1";
        final String prefix = "192.168.43.0/24";
        when(mIOffload.addDownstream(anyString(), anyString())).thenReturn(true);
        assertTrue(mOffloadHw.addDownstream(ifName, prefix));
        verify(mIOffload).addDownstream(eq(ifName), eq(prefix));
        when(mIOffload.addDownstream(anyString(), anyString())).thenReturn(false);
        assertFalse(mOffloadHw.addDownstream(ifName, prefix));
        when(mIOffload.removeDownstream(anyString(), anyString())).thenReturn(true);
        assertTrue(mOffloadHw.removeDownstream(ifName, prefix));
        verify(mIOffload).removeDownstream(eq(ifName), eq(prefix));
        when(mIOffload.removeDownstream(anyString(), anyString())).thenReturn(false);
        assertFalse(mOffloadHw.removeDownstream(ifName, prefix));
    }

    @Test
    public void testSendIpv4NfGenMsg() throws Exception {
        startOffloadHardwareInterface(OFFLOAD_HAL_VERSION_HIDL_1_0, true);
        FileDescriptor writeSocket = new FileDescriptor();
        FileDescriptor readSocket = new FileDescriptor();
        try {
            Os.socketpair(AF_UNIX, SOCK_STREAM, 0, writeSocket, readSocket);
        } catch (ErrnoException e) {
            fail();
            return;
        }
        when(mNativeHandle1.getFileDescriptor()).thenReturn(writeSocket);

        mOffloadHw.sendIpv4NfGenMsg(mNativeHandle1, TEST_TYPE, TEST_FLAGS);

        ByteBuffer buffer = ByteBuffer.allocate(9823);  // Arbitrary value > expectedLen.
        buffer.order(ByteOrder.nativeOrder());

        int read = Os.read(readSocket, buffer);
        final int expectedLen = StructNlMsgHdr.STRUCT_SIZE + StructNfGenMsg.STRUCT_SIZE;
        assertEquals(expectedLen, read);

        buffer.flip();
        assertEquals(expectedLen, buffer.getInt());
        assertEquals(TEST_TYPE, buffer.getShort());
        assertEquals(TEST_FLAGS, buffer.getShort());
        assertEquals(0 /* seq */, buffer.getInt());
        assertEquals(0 /* pid */, buffer.getInt());
        assertEquals(AF_INET, buffer.get());             // nfgen_family
        assertEquals(0 /* error */, buffer.get());       // version
        assertEquals(0 /* error */, buffer.getShort());  // res_id
        assertEquals(expectedLen, buffer.position());
    }
}
