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
import android.nfc.tech.NfcF;
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

public class NfcFTest {

    private static final byte[] SYSTEM_CODE = new byte[]{0xA};
    private static final byte[] MANUFACTURER = new byte[]{0xB};
    @Mock
    private INfcTag mNfcTagMock;
    @Captor
    private ArgumentCaptor<byte[]> mTransceiveDataCaptor;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testGet_isNfcF() {
        NfcF nfcF = createNfcF();

        assertTrue("Expected to not be <null>", nfcF != null);
    }

    @Test
    public void testGet_tagNotNfcF() {
        Tag tag = new Tag(new byte[]{}, new int[]{TagTechnology.NFC_B}, new Bundle[]{}, 0, 0L,
                null);
        NfcF nfcF = NfcF.get(tag);

        assertTrue("Expected: <null> ", nfcF == null);
    }

    @Test
    public void testGetSystemCode() {
        NfcF nfcF = createNfcF();

        assertEquals(SYSTEM_CODE, nfcF.getSystemCode());
    }

    @Test
    public void testGetManufacturer() {
        NfcF nfcF = createNfcF();

        assertEquals(MANUFACTURER, nfcF.getManufacturer());
    }

    @Test
    public void testTransceive() throws RemoteException, IOException {
        NfcF nfcF = createConnectedNfcF();

        when(mNfcTagMock.transceive(anyInt(), any(), anyBoolean())).thenReturn(
                new TransceiveResult(TransceiveResult.RESULT_SUCCESS, new byte[]{0xF}));
        byte[] transceivedBytes = new byte[]{0xA, 0xB, 0xC, 0xD};

        assertArrayEquals(new byte[]{0xF}, nfcF.transceive(transceivedBytes));
        verify(mNfcTagMock, times(1)).transceive(anyInt(), mTransceiveDataCaptor.capture(),
                anyBoolean());
        assertArrayEquals(transceivedBytes, mTransceiveDataCaptor.getValue());
    }

    @Test
    public void testGetMaxTransceiveLength() throws RemoteException, IOException {
        NfcF nfcF = createNfcF();

        when(mNfcTagMock.isTagUpToDate(anyLong())).thenReturn(true);
        when(mNfcTagMock.getMaxTransceiveLength(TagTechnology.NFC_F)).thenReturn(16);

        assertEquals(16, nfcF.getMaxTransceiveLength());
    }

    @Test
    public void testSetTimeout() throws RemoteException, IOException {
        NfcF nfcF = createNfcF();

        when(mNfcTagMock.isTagUpToDate(anyLong())).thenReturn(true);
        when(mNfcTagMock.setTimeout(anyInt(), anyInt())).thenReturn(ErrorCodes.SUCCESS);
        nfcF.setTimeout(250);

        ArgumentCaptor<Integer> timeout = ArgumentCaptor.forClass(Integer.class);
        verify(mNfcTagMock, times(1)).setTimeout(anyInt(), timeout.capture());
        assertTrue(250 == timeout.getValue());
    }

    @Test
    public void testSetTimeout_invalidTimeout() throws RemoteException, IOException {
        NfcF nfcF = createNfcF();

        when(mNfcTagMock.isTagUpToDate(anyLong())).thenReturn(true);
        when(mNfcTagMock.setTimeout(anyInt(), anyInt())).thenReturn(ErrorCodes.ERROR_INVALID_PARAM);
        assertThrows(() -> nfcF.setTimeout(-1));
    }

    @Test
    public void testGetTimeout() throws RemoteException, IOException {
        NfcF nfcF = createConnectedNfcF();

        when(mNfcTagMock.getTimeout(anyInt())).thenReturn(250);
        assertEquals(250, nfcF.getTimeout());
    }

    @Test
    public void testGetTimeout_serviceDead() throws RemoteException, IOException {
        NfcF nfcF = createConnectedNfcF();

        when(mNfcTagMock.getTimeout(anyInt())).thenThrow(new RemoteException());
        assertEquals(0, nfcF.getTimeout());
    }

    @Test
    public void testGetTag() {
        Bundle extras = new Bundle();
        extras.putByteArray("systemcode", SYSTEM_CODE);
        extras.putByteArray("pmm", MANUFACTURER);
        Tag tag = new Tag(new byte[]{}, new int[]{TagTechnology.NFC_F}, new Bundle[]{extras}, 0, 0L,
                mNfcTagMock);

        NfcF nfcF = NfcF.get(tag);

        assertEquals(tag, nfcF.getTag());
    }

    @Test
    public void testIsConnected_isConnected() throws RemoteException, IOException {
        NfcF nfcF = createConnectedNfcF();
        when(mNfcTagMock.isPresent(anyInt())).thenReturn(true);

        assertEquals(true, nfcF.isConnected());
    }

    @Test
    public void testIsConnected_notConnected() throws RemoteException, IOException {
        NfcF nfcF = createNfcF();
        when(mNfcTagMock.isPresent(anyInt())).thenReturn(false);

        assertEquals(false, nfcF.isConnected());
    }


    @Test
    public void testIsConnected_serviceDead() throws RemoteException, IOException {
        NfcF nfcF = createConnectedNfcF();
        when(mNfcTagMock.isPresent(anyInt())).thenThrow(new RemoteException());

        assertEquals(false, nfcF.isConnected());
    }

    @Test
    public void testReconnect() throws RemoteException, IOException {
        NfcF nfcF = createConnectedNfcF();
        when(mNfcTagMock.reconnect(anyInt())).thenReturn(ErrorCodes.SUCCESS);

        nfcF.reconnect();
        verify(mNfcTagMock, times(1)).reconnect(anyInt());
    }

    @Test
    public void testReconnect_notConnected() throws RemoteException, IOException {
        NfcF nfcF = createNfcF();

        assertThrows(() -> nfcF.reconnect());
    }

    @Test
    public void testReconnect_failReconnect() throws RemoteException, IOException {
        NfcF nfcF = createConnectedNfcF();
        when(mNfcTagMock.reconnect(anyInt())).thenReturn(ErrorCodes.ERROR_CONNECT);

        assertThrows(() -> nfcF.reconnect());
    }

    @Test
    public void testReconnect_remoteException() throws RemoteException, IOException {
        NfcF nfcF = createConnectedNfcF();
        when(mNfcTagMock.reconnect(anyInt())).thenThrow(new RemoteException());

        assertThrows(() -> nfcF.reconnect());
    }

    @Test
    public void testClose() throws RemoteException, IOException {
        NfcF nfcF = createConnectedNfcF();
        when(mNfcTagMock.reconnect(anyInt())).thenReturn(ErrorCodes.SUCCESS);
        when(mNfcTagMock.isPresent(anyInt())).thenReturn(true);
        nfcF.close();

        assertEquals(false, nfcF.isConnected());
    }

    private NfcF createConnectedNfcF() throws RemoteException, IOException {
        NfcF nfcF = createNfcF();

        when(mNfcTagMock.isTagUpToDate(anyLong())).thenReturn(true);
        when(mNfcTagMock.connect(anyInt(), anyInt())).thenReturn(ErrorCodes.SUCCESS);
        nfcF.connect();

        return nfcF;
    }

    private NfcF createNfcF() {
        Bundle extras = new Bundle();
        extras.putByteArray("systemcode", SYSTEM_CODE);
        extras.putByteArray("pmm", MANUFACTURER);
        Tag tag = new Tag(new byte[]{}, new int[]{TagTechnology.NFC_F}, new Bundle[]{extras}, 0, 0L,
                mNfcTagMock);

        return NfcF.get(tag);
    }
}
