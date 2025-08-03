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
import android.nfc.tech.NfcB;
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

public class NfcBTest {

    private static final byte[] APP_DATA = new byte[]{0xA};
    private static final byte[] PROTOCOL_INFO = new byte[]{0xB};

    @Mock
    private INfcTag mNfcTagMock;
    @Captor
    private ArgumentCaptor<byte[]> mTransceiveDataCaptor;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testGet_isNfcB() {
        NfcB nfcB = createNfcB();

        assertTrue("Expected to not be <null>", nfcB != null);
    }

    @Test
    public void testGet_notNfcB() {
        Tag tag = new Tag(new byte[]{}, new int[]{TagTechnology.NFC_A}, new Bundle[]{}, 0, 0L,
                null);
        NfcB nfcB = NfcB.get(tag);

        assertTrue("Expected: <null> ", nfcB == null);
    }

    @Test
    public void testGetApplicationData() {
        NfcB nfcB = createNfcB();

        assertEquals(APP_DATA, nfcB.getApplicationData());
    }

    @Test
    public void testGetProtocolInfo() {
        NfcB nfcB = createNfcB();

        assertEquals(PROTOCOL_INFO, nfcB.getProtocolInfo());
    }

    @Test

    public void testTransceive() throws RemoteException, IOException {
        NfcB nfcB = createConnectedNfcB();

        when(mNfcTagMock.transceive(anyInt(), any(), anyBoolean())).thenReturn(
                new TransceiveResult(TransceiveResult.RESULT_SUCCESS, new byte[]{0xF}));
        byte[] transceivedBytes = new byte[]{0xA, 0xB, 0xC, 0xD};

        assertArrayEquals(new byte[]{0xF}, nfcB.transceive(transceivedBytes));
        verify(mNfcTagMock, times(1)).transceive(anyInt(), mTransceiveDataCaptor.capture(),
                anyBoolean());
        assertArrayEquals(transceivedBytes, mTransceiveDataCaptor.getValue());
    }

    @Test
    public void testGetMaxTransceieveLength() throws RemoteException, IOException {
        NfcB nfcB = createNfcB();

        when(mNfcTagMock.isTagUpToDate(anyLong())).thenReturn(true);
        when(mNfcTagMock.getMaxTransceiveLength(TagTechnology.NFC_B)).thenReturn(16);

        assertEquals(16, nfcB.getMaxTransceiveLength());
    }


    @Test
    public void testGetTag() {
        Bundle extras = new Bundle();
        extras.putByteArray("appdata", APP_DATA);
        extras.putByteArray("protinfo", PROTOCOL_INFO);
        Tag tag = new Tag(new byte[]{}, new int[]{TagTechnology.NFC_B}, new Bundle[]{extras}, 0, 0L,
                null);

        NfcB nfcB = NfcB.get(tag);

        assertEquals(tag, nfcB.getTag());
    }

    @Test
    public void testIsConnected_isConnected() throws RemoteException, IOException {
        NfcB nfcB = createConnectedNfcB();
        when(mNfcTagMock.isPresent(anyInt())).thenReturn(true);

        assertEquals(true, nfcB.isConnected());
    }

    @Test
    public void testIsConnected_notConnected() throws RemoteException, IOException {
        NfcB nfcB = createNfcB();
        when(mNfcTagMock.isPresent(anyInt())).thenReturn(false);

        assertEquals(false, nfcB.isConnected());
    }


    @Test
    public void testIsConnected_serviceDead() throws RemoteException, IOException {
        NfcB nfcB = createConnectedNfcB();
        when(mNfcTagMock.isPresent(anyInt())).thenThrow(new RemoteException());

        assertEquals(false, nfcB.isConnected());
    }

    @Test
    public void testReconnect() throws RemoteException, IOException {
        NfcB nfcB = createConnectedNfcB();
        when(mNfcTagMock.reconnect(anyInt())).thenReturn(ErrorCodes.SUCCESS);

        nfcB.reconnect();
        verify(mNfcTagMock, times(1)).reconnect(anyInt());
    }

    @Test
    public void testReconnect_notConnected() throws RemoteException, IOException {
        NfcB nfcB = createNfcB();

        assertThrows(() -> nfcB.reconnect());
    }

    @Test
    public void testReconnect_failReconnect() throws RemoteException, IOException {
        NfcB nfcB = createConnectedNfcB();
        when(mNfcTagMock.reconnect(anyInt())).thenReturn(ErrorCodes.ERROR_CONNECT);

        assertThrows(() -> nfcB.reconnect());
    }

    @Test
    public void testReconnect_remoteException() throws RemoteException, IOException {
        NfcB nfcB = createConnectedNfcB();
        when(mNfcTagMock.reconnect(anyInt())).thenThrow(new RemoteException());

        assertThrows(() -> nfcB.reconnect());
    }

    @Test
    public void testClose() throws RemoteException, IOException {
        NfcB nfcB = createConnectedNfcB();
        when(mNfcTagMock.reconnect(anyInt())).thenReturn(ErrorCodes.SUCCESS);
        when(mNfcTagMock.isPresent(anyInt())).thenReturn(true);
        nfcB.close();

        assertEquals(false, nfcB.isConnected());
    }


    private NfcB createConnectedNfcB() throws RemoteException, IOException {
        NfcB nfcB = createNfcB();

        when(mNfcTagMock.isTagUpToDate(anyLong())).thenReturn(true);
        when(mNfcTagMock.connect(anyInt(), anyInt())).thenReturn(ErrorCodes.SUCCESS);
        nfcB.connect();

        return nfcB;
    }

    private NfcB createNfcB() {
        Bundle extras = new Bundle();
        extras.putByteArray("appdata", APP_DATA);
        extras.putByteArray("protinfo", PROTOCOL_INFO);
        Tag tag = new Tag(new byte[]{}, new int[]{TagTechnology.NFC_B}, new Bundle[]{extras}, 0, 0L,
                mNfcTagMock);

        return NfcB.get(tag);
    }
}
