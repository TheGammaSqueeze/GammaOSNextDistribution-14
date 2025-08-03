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
import android.nfc.tech.MifareUltralight;
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

public class MifareUltralightTest {

    @Mock
    private INfcTag mNfcTagMock;
    @Captor
    private ArgumentCaptor<byte[]> mTransceiveDataCaptor;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testGet_isUltralight() {
        MifareUltralight ultralight = createMifareUltralight();

        assertTrue("Expected to not be <null>", ultralight != null);
    }

    @Test
    public void testGet_notUltralight() {
        Tag tag = new Tag(new byte[]{}, new int[]{TagTechnology.NFC_A}, new Bundle[]{}, 0, 0L,
                null);
        MifareUltralight ultralight = MifareUltralight.get(tag);

        assertTrue("Expected: <null> ", ultralight == null);
    }

    @Test
    public void testGetType_ultralight() {
        MifareUltralight ultralight = createMifareUltralight();

        assertEquals(MifareUltralight.TYPE_ULTRALIGHT, ultralight.getType());
    }

    @Test
    public void testGetType_ultralightC() {
        Bundle nfcAExtras = new Bundle();
        nfcAExtras.putShort("sak", (short) 0x00);
        nfcAExtras.putByteArray("atqa", new byte[]{0x00});
        Bundle ultralightExtras = new Bundle();
        ultralightExtras.putBoolean("isulc", true);
        Tag tag = new Tag(new byte[]{0x04},
                new int[]{TagTechnology.MIFARE_ULTRALIGHT, TagTechnology.NFC_A},
                new Bundle[]{ultralightExtras, nfcAExtras}, 0, 0L, null);
        MifareUltralight ultralight = MifareUltralight.get(tag);

        assertEquals(MifareUltralight.TYPE_ULTRALIGHT_C, ultralight.getType());
    }

    @Test
    public void testReadPages() throws RemoteException, IOException {
        byte[] pages = new byte[]{0x00, 0x01, 0x02, 0x03, 0x00, 0x01, 0x02, 0x03, 0x00, 0x01, 0x02,
                0x03, 0x00, 0x01, 0x02, 0x03};
        MifareUltralight ultralight = createConnectedMifareUltralight();
        when(mNfcTagMock.transceive(anyInt(), any(), anyBoolean())).thenReturn(
                new TransceiveResult(TransceiveResult.RESULT_SUCCESS, pages));

        assertEquals(pages, ultralight.readPages(0));
        verify(mNfcTagMock, times(1)).transceive(anyInt(), mTransceiveDataCaptor.capture(),
                anyBoolean());
        assertArrayEquals(new byte[]{0x30, 0x0}, mTransceiveDataCaptor.getValue());
    }

    @Test
    public void testReadPages_invalidIndex() throws RemoteException, IOException {
        MifareUltralight ultralight = createMifareUltralight();

        assertThrows(() -> ultralight.readPages(-1));
    }

    @Test
    public void testWritePage() throws RemoteException, IOException {
        byte[] page = new byte[]{0xA, 0xB, 0xC, 0xD};
        MifareUltralight ultralight = createConnectedMifareUltralight();
        when(mNfcTagMock.transceive(anyInt(), any(), anyBoolean())).thenReturn(
                new TransceiveResult(TransceiveResult.RESULT_SUCCESS, new byte[]{}));

        ultralight.writePage(0, page);

        verify(mNfcTagMock, times(1)).transceive(anyInt(), mTransceiveDataCaptor.capture(),
                anyBoolean());
        assertArrayEquals(new byte[]{(byte) 0xA2, 0x0, 0xA, 0xB, 0xC, 0xD},
                mTransceiveDataCaptor.getValue());
    }

    @Test
    public void testWritePage_invalidIndex() throws RemoteException, IOException {
        MifareUltralight ultralight = createMifareUltralight();

        assertThrows(() -> ultralight.writePage(-1, new byte[]{0xA, 0xB, 0xC, 0xD}));
    }

    @Test
    public void testTransceieve() throws RemoteException, IOException {
        byte[] data = new byte[]{0xA, 0xB, 0xC, 0xD};
        byte[] response = new byte[]{0x1, 0x2, 0x3, 0x4};
        MifareUltralight ultralight = createConnectedMifareUltralight();
        when(mNfcTagMock.transceive(anyInt(), any(), anyBoolean())).thenReturn(
                new TransceiveResult(TransceiveResult.RESULT_SUCCESS, response));

        assertArrayEquals(response, ultralight.transceive(data));
        verify(mNfcTagMock, times(1)).transceive(anyInt(), mTransceiveDataCaptor.capture(),
                anyBoolean());
        assertArrayEquals(data, mTransceiveDataCaptor.getValue());
    }

    @Test
    public void testGetMaxTransceiveLength() throws RemoteException, IOException {
        MifareUltralight ultralight = createMifareUltralight();

        when(mNfcTagMock.isTagUpToDate(anyLong())).thenReturn(true);
        when(mNfcTagMock.getMaxTransceiveLength(TagTechnology.MIFARE_ULTRALIGHT)).thenReturn(16);

        assertEquals(16, ultralight.getMaxTransceiveLength());
    }

    @Test
    public void testSetTimeout() throws RemoteException, IOException {
        MifareUltralight ultralight = createMifareUltralight();
        when(mNfcTagMock.isTagUpToDate(anyLong())).thenReturn(true);
        when(mNfcTagMock.setTimeout(anyInt(), anyInt())).thenReturn(ErrorCodes.SUCCESS);

        ultralight.setTimeout(250);

        ArgumentCaptor<Integer> timeout = ArgumentCaptor.forClass(Integer.class);
        verify(mNfcTagMock, times(1)).setTimeout(anyInt(), timeout.capture());
        assertTrue(250 == timeout.getValue());
    }

    @Test
    public void testSetTimeout_invalidTimeout() throws RemoteException, IOException {
        MifareUltralight ultralight = createMifareUltralight();

        when(mNfcTagMock.isTagUpToDate(anyLong())).thenReturn(true);
        when(mNfcTagMock.setTimeout(anyInt(), anyInt())).thenReturn(ErrorCodes.ERROR_INVALID_PARAM);
        assertThrows(() -> ultralight.setTimeout(250));
    }

    @Test
    public void testGetTimeout() throws RemoteException, IOException {
        MifareUltralight ultralight = createMifareUltralight();
        when(mNfcTagMock.isTagUpToDate(anyLong())).thenReturn(true);
        when(mNfcTagMock.getTimeout(anyInt())).thenReturn(250);

        assertEquals(250, ultralight.getTimeout());
    }

    @Test
    public void testGetTimeout_serviceDead() throws RemoteException, IOException {
        MifareUltralight ultralight = createConnectedMifareUltralight();

        when(mNfcTagMock.getTimeout(anyInt())).thenThrow(new RemoteException());
        assertEquals(0, ultralight.getTimeout());
    }


    @Test
    public void testGetTag() {
        Bundle extras = new Bundle();
        extras.putShort("sak", (short) 0x01);
        extras.putByteArray("atqa", new byte[]{0x02});
        Tag tag = new Tag(new byte[]{0x01, 0x02, 0x03, 0x04},
                new int[]{TagTechnology.MIFARE_ULTRALIGHT, TagTechnology.NFC_A},
                new Bundle[]{null, extras}, 0, 0L, mNfcTagMock);
        MifareUltralight ultralight = MifareUltralight.get(tag);

        assertEquals(tag, ultralight.getTag());
    }

    @Test
    public void testIsConnected_isConnected() throws RemoteException, IOException {
        MifareUltralight ultralight = createConnectedMifareUltralight();
        when(mNfcTagMock.isPresent(anyInt())).thenReturn(true);

        assertEquals(true, ultralight.isConnected());
    }

    @Test
    public void testIsConnected_notConnected() throws RemoteException, IOException {
        MifareUltralight ultralight = createMifareUltralight();
        when(mNfcTagMock.isPresent(anyInt())).thenReturn(false);

        assertEquals(false, ultralight.isConnected());
    }


    @Test
    public void testIsConnected_serviceDead() throws RemoteException, IOException {
        MifareUltralight ultralight = createConnectedMifareUltralight();
        when(mNfcTagMock.isPresent(anyInt())).thenThrow(new RemoteException());

        assertEquals(false, ultralight.isConnected());
    }

    @Test
    public void testReconnect() throws RemoteException, IOException {
        MifareUltralight ultralight = createConnectedMifareUltralight();
        when(mNfcTagMock.reconnect(anyInt())).thenReturn(ErrorCodes.SUCCESS);

        ultralight.reconnect();
        verify(mNfcTagMock, times(1)).reconnect(anyInt());
    }

    @Test
    public void testReconnect_notConnected() throws RemoteException, IOException {
        MifareUltralight ultralight = createMifareUltralight();

        assertThrows(() -> ultralight.reconnect());
    }

    @Test
    public void testReconnect_failReconnect() throws RemoteException, IOException {
        MifareUltralight ultralight = createConnectedMifareUltralight();
        when(mNfcTagMock.reconnect(anyInt())).thenReturn(ErrorCodes.ERROR_CONNECT);

        assertThrows(() -> ultralight.reconnect());
    }

    @Test
    public void testReconnect_remoteException() throws RemoteException, IOException {
        MifareUltralight ultralight = createConnectedMifareUltralight();
        when(mNfcTagMock.reconnect(anyInt())).thenThrow(new RemoteException());

        assertThrows(() -> ultralight.reconnect());
    }

    @Test
    public void testClose() throws RemoteException, IOException {
        MifareUltralight ultralight = createConnectedMifareUltralight();
        when(mNfcTagMock.reconnect(anyInt())).thenReturn(ErrorCodes.SUCCESS);
        when(mNfcTagMock.isPresent(anyInt())).thenReturn(true);
        ultralight.close();

        assertEquals(false, ultralight.isConnected());
    }

    private MifareUltralight createConnectedMifareUltralight() throws RemoteException, IOException {
        MifareUltralight ultralight = createMifareUltralight();

        when(mNfcTagMock.isTagUpToDate(anyLong())).thenReturn(true);
        when(mNfcTagMock.connect(anyInt(), anyInt())).thenReturn(ErrorCodes.SUCCESS);
        ultralight.connect();

        return ultralight;
    }

    private MifareUltralight createMifareUltralight() {
        Bundle nfcAExtras = new Bundle();
        nfcAExtras.putShort("sak", (short) 0x00);
        nfcAExtras.putByteArray("atqa", new byte[]{0x00});
        Bundle ultralightExtras = new Bundle();
        ultralightExtras.putBoolean("isulc", false);
        Tag tag = new Tag(new byte[]{0x04},
                new int[]{TagTechnology.MIFARE_ULTRALIGHT, TagTechnology.NFC_A},
                new Bundle[]{ultralightExtras, nfcAExtras}, 0, 0L, mNfcTagMock);

        return MifareUltralight.get(tag);
    }
}
