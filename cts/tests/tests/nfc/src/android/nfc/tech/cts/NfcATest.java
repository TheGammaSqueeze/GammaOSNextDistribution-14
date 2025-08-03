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
import android.nfc.tech.NfcA;
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

public class NfcATest {

    private static final short SAK = (short) 0x01;
    private static final byte[] ATQA = new byte[]{0x11};

    @Mock
    private INfcTag mNfcTagMock;
    @Captor
    private ArgumentCaptor<byte[]> mTransceiveDataCaptor;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testGet_isNfcA() {
        NfcA nfcA = createNfcA();

        assertTrue("Expected to not be <null>", nfcA != null);
    }

    @Test
    public void testGet_tagNotNfcA() {
        Tag tag = new Tag(new byte[]{}, new int[]{TagTechnology.NFC_B}, new Bundle[]{}, 0, 0L,
                null);
        NfcA nfcA = NfcA.get(tag);

        assertTrue("Expected: <null> ", nfcA == null);
    }

    @Test
    public void testGetAtqa() {
        NfcA nfcA = createNfcA();

        assertEquals(ATQA, nfcA.getAtqa());
    }

    @Test
    public void testGetSak() {
        NfcA nfcA = createNfcA();

        assertEquals(SAK, nfcA.getSak());
    }

    @Test
    public void testTransceive() throws RemoteException, IOException {
        NfcA nfcA = createConnectedNfcA();

        when(mNfcTagMock.transceive(anyInt(), any(), anyBoolean())).thenReturn(
                new TransceiveResult(TransceiveResult.RESULT_SUCCESS, new byte[]{0xF}));
        byte[] transceivedBytes = new byte[]{0xA, 0xB, 0xC, 0xD};

        assertArrayEquals(new byte[]{0xF}, nfcA.transceive(transceivedBytes));
        verify(mNfcTagMock, times(1)).transceive(anyInt(), mTransceiveDataCaptor.capture(),
                anyBoolean());
        assertArrayEquals(transceivedBytes, mTransceiveDataCaptor.getValue());
    }

    @Test
    public void testGetMaxTransceiveLength() throws RemoteException, IOException {
        NfcA nfcA = createNfcA();

        when(mNfcTagMock.isTagUpToDate(anyLong())).thenReturn(true);
        when(mNfcTagMock.getMaxTransceiveLength(TagTechnology.NFC_A)).thenReturn(16);

        assertEquals(16, nfcA.getMaxTransceiveLength());
    }

    @Test
    public void testSetTimeout() throws RemoteException, IOException {
        NfcA nfcA = createNfcA();

        when(mNfcTagMock.isTagUpToDate(anyLong())).thenReturn(true);
        when(mNfcTagMock.setTimeout(anyInt(), anyInt())).thenReturn(ErrorCodes.SUCCESS);
        nfcA.setTimeout(250);

        ArgumentCaptor<Integer> timeout = ArgumentCaptor.forClass(Integer.class);
        verify(mNfcTagMock, times(1)).setTimeout(anyInt(), timeout.capture());
        assertTrue(250 == timeout.getValue());
    }

    @Test
    public void testSetTimeout_invalidTimeout() throws RemoteException, IOException {
        NfcA nfcA = createNfcA();

        when(mNfcTagMock.isTagUpToDate(anyLong())).thenReturn(true);
        when(mNfcTagMock.setTimeout(anyInt(), anyInt())).thenReturn(ErrorCodes.ERROR_INVALID_PARAM);
        assertThrows(() -> nfcA.setTimeout(-1));
    }

    @Test
    public void testGetTimeout() throws RemoteException, IOException {
        NfcA nfcA = createConnectedNfcA();

        when(mNfcTagMock.getTimeout(anyInt())).thenReturn(250);
        assertEquals(250, nfcA.getTimeout());
    }

    @Test
    public void testGetTimeout_serviceDead() throws RemoteException, IOException {
        NfcA nfcA = createConnectedNfcA();

        when(mNfcTagMock.getTimeout(anyInt())).thenThrow(new RemoteException());
        assertEquals(0, nfcA.getTimeout());
    }

    @Test
    public void testGetTag() {
        Bundle extras = new Bundle();
        extras.putShort("sak", SAK);
        extras.putByteArray("atqa", ATQA);
        Tag tag = new Tag(new byte[]{0x01, 0x02, 0x03, 0x04}, new int[]{TagTechnology.NFC_A},
                new Bundle[]{extras}, 0, 0L, mNfcTagMock);

        NfcA nfcA = NfcA.get(tag);

        assertEquals(tag, nfcA.getTag());
    }

    @Test
    public void testIsConnected_isConnected() throws RemoteException, IOException {
        NfcA nfcA = createConnectedNfcA();
        when(mNfcTagMock.isPresent(anyInt())).thenReturn(true);

        assertEquals(true, nfcA.isConnected());
    }

    @Test
    public void testIsConnected_notConnected() throws RemoteException, IOException {
        NfcA nfcA = createNfcA();
        when(mNfcTagMock.isPresent(anyInt())).thenReturn(false);

        assertEquals(false, nfcA.isConnected());
    }


    @Test
    public void testIsConnected_serviceDead() throws RemoteException, IOException {
        NfcA nfcA = createConnectedNfcA();
        when(mNfcTagMock.isPresent(anyInt())).thenThrow(new RemoteException());

        assertEquals(false, nfcA.isConnected());
    }

    @Test
    public void testReconnect() throws RemoteException, IOException {
        NfcA nfcA = createConnectedNfcA();
        when(mNfcTagMock.reconnect(anyInt())).thenReturn(ErrorCodes.SUCCESS);

        nfcA.reconnect();
        verify(mNfcTagMock, times(1)).reconnect(anyInt());
    }

    @Test
    public void testReconnect_notConnected() throws RemoteException, IOException {
        NfcA nfcA = createNfcA();

        assertThrows(() -> nfcA.reconnect());
    }

    @Test
    public void testReconnect_failReconnect() throws RemoteException, IOException {
        NfcA nfcA = createConnectedNfcA();
        when(mNfcTagMock.reconnect(anyInt())).thenReturn(ErrorCodes.ERROR_CONNECT);

        assertThrows(() -> nfcA.reconnect());
    }

    @Test
    public void testReconnect_remoteException() throws RemoteException, IOException {
        NfcA nfcA = createConnectedNfcA();
        when(mNfcTagMock.reconnect(anyInt())).thenThrow(new RemoteException());

        assertThrows(() -> nfcA.reconnect());
    }

    @Test
    public void testClose() throws RemoteException, IOException {
        NfcA nfcA = createConnectedNfcA();
        when(mNfcTagMock.reconnect(anyInt())).thenReturn(ErrorCodes.SUCCESS);
        when(mNfcTagMock.isPresent(anyInt())).thenReturn(true);
        nfcA.close();

        assertEquals(false, nfcA.isConnected());
    }

    private NfcA createConnectedNfcA() throws RemoteException, IOException {
        NfcA nfcA = createNfcA();

        when(mNfcTagMock.isTagUpToDate(anyLong())).thenReturn(true);
        when(mNfcTagMock.connect(anyInt(), anyInt())).thenReturn(ErrorCodes.SUCCESS);
        nfcA.connect();

        return nfcA;
    }

    private NfcA createNfcA() {
        Bundle extras = new Bundle();
        extras.putShort("sak", SAK);
        extras.putByteArray("atqa", ATQA);
        Tag tag = new Tag(new byte[]{0x01, 0x02, 0x03, 0x04}, new int[]{TagTechnology.NFC_A},
                new Bundle[]{extras}, 0, 0L, mNfcTagMock);

        return NfcA.get(tag);
    }
}
