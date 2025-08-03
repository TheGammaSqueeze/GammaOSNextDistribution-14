/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server;

import static com.android.server.IpSecXfrmControllerTestHex.XFRM_ESRCH_HEX;
import static com.android.server.IpSecXfrmControllerTestHex.XFRM_NEW_SA_HEX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.InetAddresses;
import android.system.ErrnoException;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.net.module.util.netlink.NetlinkMessage;
import com.android.net.module.util.netlink.xfrm.XfrmNetlinkNewSaMessage;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.FileDescriptor;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class IpSecXfrmControllerTest {
    private static final InetAddress DEST_ADDRESS =
            InetAddresses.parseNumericAddress("2001:db8::111");
    private static final long SPI = 0xaabbccddL;
    private static final int ESRCH = -3;

    private IpSecXfrmController mXfrmController;
    private FileDescriptor mDummyNetlinkSocket;

    @Mock private IpSecXfrmController.Dependencies mMockDeps;

    @Captor private ArgumentCaptor<byte[]> mRequestByteArrayCaptor;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mDummyNetlinkSocket = new FileDescriptor();

        when(mMockDeps.newNetlinkSocket()).thenReturn(mDummyNetlinkSocket);
        mXfrmController = new IpSecXfrmController(mMockDeps);
    }

    @Test
    public void testStartStop() throws Exception {
        mXfrmController.openNetlinkSocketIfNeeded();

        verify(mMockDeps).newNetlinkSocket();
        assertNotNull(mXfrmController.getNetlinkSocket());

        mXfrmController.closeNetlinkSocketIfNeeded();
        verify(mMockDeps).releaseNetlinkSocket(eq(mDummyNetlinkSocket));
        assertNull(mXfrmController.getNetlinkSocket());
    }

    private static void injectRxMessage(IpSecXfrmController.Dependencies mockDeps, byte[] bytes)
            throws Exception {
        final ByteBuffer buff = ByteBuffer.wrap(bytes);
        buff.order(ByteOrder.nativeOrder());

        when(mockDeps.recvMessage(any(FileDescriptor.class))).thenReturn(buff);
    }

    @Test
    public void testIpSecGetSa() throws Exception {
        final int expectedReqLen = 40;
        injectRxMessage(mMockDeps, XFRM_NEW_SA_HEX);

        final NetlinkMessage netlinkMessage = mXfrmController.ipSecGetSa(DEST_ADDRESS, SPI);
        final XfrmNetlinkNewSaMessage message = (XfrmNetlinkNewSaMessage) netlinkMessage;

        // Verifications
        assertEquals(SPI, message.getXfrmUsersaInfo().getSpi());
        assertEquals(DEST_ADDRESS, message.getXfrmUsersaInfo().getDestAddress());

        verify(mMockDeps).sendMessage(eq(mDummyNetlinkSocket), mRequestByteArrayCaptor.capture());
        final byte[] request = mRequestByteArrayCaptor.getValue();
        assertEquals(expectedReqLen, request.length);

        verify(mMockDeps).recvMessage(eq(mDummyNetlinkSocket));
    }

    @Test
    public void testIpSecGetSa_NlErrorMsg() throws Exception {
        injectRxMessage(mMockDeps, XFRM_ESRCH_HEX);

        try {
            mXfrmController.ipSecGetSa(DEST_ADDRESS, SPI);
            fail("Expected to fail with ESRCH ");
        } catch (ErrnoException e) {
            assertEquals(ESRCH, e.errno);
        }
    }
}
