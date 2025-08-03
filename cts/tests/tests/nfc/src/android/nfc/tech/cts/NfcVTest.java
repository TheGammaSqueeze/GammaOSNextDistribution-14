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

package android.nfc.tech.cts;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

import android.nfc.ErrorCodes;
import android.nfc.INfcTag;
import android.nfc.Tag;
import android.nfc.TransceiveResult;
import android.nfc.tech.NfcV;
import android.nfc.tech.TagTechnology;
import android.os.Bundle;
import android.os.RemoteException;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;

public class NfcVTest {

    private static final byte RESPONSE_FLAGS = 0xA;
    private static final byte DSF_ID = 0xB;

    @Mock
    private INfcTag mNfcTagMock;
    @Captor
    private ArgumentCaptor<byte[]> mTransceiveDataCaptor;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testGet_isNfcV() {
        NfcV nfcV = createNfcV();

        assertTrue("Expected to not be <null>", nfcV != null);
    }

    @Test
    public void testGet_tagNotNfcV() {
        Tag tag = new Tag(new byte[]{}, new int[]{TagTechnology.NFC_B}, new Bundle[]{}, 0, 0L,
                null);
        NfcV nfcV = NfcV.get(tag);

        assertTrue("Expected: <null> ", nfcV == null);
    }

    @Test
    public void testGetResponseFlags() {
        NfcV nfcV = createNfcV();

        assertEquals(RESPONSE_FLAGS, nfcV.getResponseFlags());
    }

    @Test
    public void testGetDsfId() {
        NfcV nfcV = createNfcV();

        assertEquals(DSF_ID, nfcV.getDsfId());
    }

    @Test
    public void testTransceive() throws RemoteException, IOException {
        NfcV nfcV = createConnectedNfcV();

        when(mNfcTagMock.transceive(anyInt(), any(), anyBoolean())).thenReturn(
                new TransceiveResult(TransceiveResult.RESULT_SUCCESS, new byte[]{0xF}));
        byte[] transceivedBytes = new byte[]{0xA, 0xB, 0xC, 0xD};

        assertArrayEquals(new byte[]{0xF}, nfcV.transceive(transceivedBytes));
        verify(mNfcTagMock, times(1)).transceive(anyInt(), mTransceiveDataCaptor.capture(),
                anyBoolean());
        assertArrayEquals(transceivedBytes, mTransceiveDataCaptor.getValue());
    }

    @Test
    public void testGetMaxTransceiveLength() throws RemoteException, IOException {
        NfcV nfcV = createNfcV();

        when(mNfcTagMock.isTagUpToDate(anyLong())).thenReturn(true);
        when(mNfcTagMock.getMaxTransceiveLength(TagTechnology.NFC_V)).thenReturn(16);

        assertEquals(16, nfcV.getMaxTransceiveLength());
    }

    @Test
    public void testGetTag() {
        Bundle extras = new Bundle();
        extras.putByte("respflag", RESPONSE_FLAGS);
        extras.putByte("dsfid", DSF_ID);
        Tag tag = new Tag(new byte[]{0x01, 0x02, 0x03, 0x04},
                new int[]{TagTechnology.NFC_V},
                new Bundle[]{extras}, 0, 0L, mNfcTagMock);

        NfcV nfcV = NfcV.get(tag);

        assertEquals(tag, nfcV.getTag());
    }

    @Test
    public void testIsConnected_isConnected() throws RemoteException, IOException {
        NfcV nfcV = createConnectedNfcV();
        when(mNfcTagMock.isPresent(anyInt())).thenReturn(true);

        assertEquals(true, nfcV.isConnected());
    }

    @Test
    public void testIsConnected_notConnected() throws RemoteException, IOException {
        NfcV nfcV = createNfcV();
        when(mNfcTagMock.isPresent(anyInt())).thenReturn(false);

        assertEquals(false, nfcV.isConnected());
    }


    @Test
    public void testIsConnected_serviceDead() throws RemoteException, IOException {
        NfcV nfcV = createConnectedNfcV();
        when(mNfcTagMock.isPresent(anyInt())).thenThrow(new RemoteException());

        assertEquals(false, nfcV.isConnected());
    }

    @Test
    public void testReconnect() throws RemoteException, IOException {
        NfcV nfcV = createConnectedNfcV();
        when(mNfcTagMock.reconnect(anyInt())).thenReturn(ErrorCodes.SUCCESS);

        nfcV.reconnect();
        verify(mNfcTagMock, times(1)).reconnect(anyInt());
    }

    @Test
    public void testReconnect_notConnected() throws RemoteException, IOException {
        NfcV nfcV = createNfcV();

        assertThrows(() -> nfcV.reconnect());
    }

    @Test
    public void testReconnect_failReconnect() throws RemoteException, IOException {
        NfcV nfcV = createConnectedNfcV();
        when(mNfcTagMock.reconnect(anyInt())).thenReturn(ErrorCodes.ERROR_CONNECT);

        assertThrows(() -> nfcV.reconnect());
    }

    @Test
    public void testReconnect_remoteException() throws RemoteException, IOException {
        NfcV nfcV = createConnectedNfcV();
        when(mNfcTagMock.reconnect(anyInt())).thenThrow(new RemoteException());

        assertThrows(() -> nfcV.reconnect());
    }

    @Test
    public void testClose() throws RemoteException, IOException {
        NfcV nfcV = createConnectedNfcV();
        when(mNfcTagMock.reconnect(anyInt())).thenReturn(ErrorCodes.SUCCESS);
        when(mNfcTagMock.isPresent(anyInt())).thenReturn(true);
        nfcV.close();

        assertEquals(false, nfcV.isConnected());
    }

    private NfcV createConnectedNfcV() throws RemoteException, IOException {
        NfcV nfcV = createNfcV();

        when(mNfcTagMock.isTagUpToDate(anyLong())).thenReturn(true);
        when(mNfcTagMock.connect(anyInt(), anyInt())).thenReturn(ErrorCodes.SUCCESS);
        nfcV.connect();

        return nfcV;
    }

    private NfcV createNfcV() {
        Bundle extras = new Bundle();
        extras.putByte("respflags", RESPONSE_FLAGS);
        extras.putByte("dsfid", DSF_ID);
        Tag tag = new Tag(new byte[]{0x01, 0x02, 0x03, 0x04},
                new int[]{TagTechnology.NFC_V},
                new Bundle[]{extras}, 0, 0L, mNfcTagMock);

        return NfcV.get(tag);
    }
}
