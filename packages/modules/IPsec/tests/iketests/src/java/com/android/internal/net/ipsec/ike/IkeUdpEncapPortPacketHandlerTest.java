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

package com.android.internal.net.ipsec.test.ike;

import static com.android.internal.net.ipsec.test.ike.IkeUdpEncapPortPacketHandler.NON_ESP_MARKER_LEN;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.util.LongSparseArray;

import org.junit.Before;
import org.junit.Test;

public class IkeUdpEncapPortPacketHandlerTest {
    private static final byte[] SHORT_PACKET = new byte[NON_ESP_MARKER_LEN - 1];
    private static final byte[] INVALID_NON_ESP_MARKER = {(byte) 1, (byte) 2, (byte) 3, (byte) 4};

    private LongSparseArray<IkeSocket.Callback> mMockSpiToCallback;

    private IkeUdpEncapPortPacketHandler.PacketReceiver mPacketReceiver;

    @Before
    public void setUp() {
        mMockSpiToCallback = mock(LongSparseArray.class);

        mPacketReceiver = new IkeUdpEncapPortPacketHandler.PacketReceiver();
    }

    @Test
    public void testPacketReceiverHandlePacketShortPacket() {
        mPacketReceiver.handlePacket(SHORT_PACKET, mMockSpiToCallback);

        verifyNoMoreInteractions(mMockSpiToCallback);
    }

    @Test
    public void testPacketReceiverHandlePacketInvalidNonEspMarkerPacket() {
        mPacketReceiver.handlePacket(INVALID_NON_ESP_MARKER, mMockSpiToCallback);

        verifyNoMoreInteractions(mMockSpiToCallback);
    }
}
