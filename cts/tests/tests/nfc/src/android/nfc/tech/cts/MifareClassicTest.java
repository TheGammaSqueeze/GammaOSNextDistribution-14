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
import android.nfc.tech.MifareClassic;
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

public class MifareClassicTest {

    @Mock
    private INfcTag mNfcTagMock;
    @Captor
    private ArgumentCaptor<byte[]> mTransceiveDataCaptor;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testGet_isMifareClassic() {
        Bundle extras = new Bundle();
        extras.putShort("sak", (short) 0x01);
        extras.putByteArray("atqa", new byte[]{0x00});
        Tag tag = new Tag(new byte[]{},
                new int[]{TagTechnology.MIFARE_CLASSIC, TagTechnology.NFC_A},
                new Bundle[]{null, extras}, 0, 0L, null);
        MifareClassic classic = MifareClassic.get(tag);

        assertTrue("Expected to not be <null>", classic != null);
    }

    @Test
    public void testGet_tagNotMifareClassic() {
        Tag tag = new Tag(new byte[]{}, new int[]{TagTechnology.NFC_A}, new Bundle[]{}, 0, 0L,
                null);
        MifareClassic classic = MifareClassic.get(tag);

        assertTrue("Expected: <null> ", classic == null);
    }

    @Test
    public void testGetType_classic() {
        Bundle extras = new Bundle();
        extras.putShort("sak", (short) 0x01);
        extras.putByteArray("atqa", new byte[]{0x00});
        Tag tag = new Tag(new byte[]{},
                new int[]{TagTechnology.MIFARE_CLASSIC, TagTechnology.NFC_A},
                new Bundle[]{null, extras}, 0, 0L, null);
        MifareClassic classic = MifareClassic.get(tag);

        assertEquals(MifareClassic.TYPE_CLASSIC, classic.getType());
    }

    @Test
    public void testGetType_plus() {
        Bundle extras = new Bundle();
        extras.putShort("sak", (short) 0x10);
        extras.putByteArray("atqa", new byte[]{0x00});
        Tag tag = new Tag(new byte[]{},
                new int[]{TagTechnology.MIFARE_CLASSIC, TagTechnology.NFC_A},
                new Bundle[]{null, extras}, 0, 0L, null);
        MifareClassic classic = MifareClassic.get(tag);

        assertEquals(MifareClassic.TYPE_PLUS, classic.getType());
    }

    @Test
    public void testGetType_pro() {
        Bundle extras = new Bundle();
        extras.putShort("sak", (short) 0xB8);
        extras.putByteArray("atqa", new byte[]{0x00});
        Tag tag = new Tag(new byte[]{},
                new int[]{TagTechnology.MIFARE_CLASSIC, TagTechnology.NFC_A},
                new Bundle[]{null, extras}, 0, 0L, null);
        MifareClassic classic = MifareClassic.get(tag);

        assertEquals(classic.getType(), MifareClassic.TYPE_PRO);
    }

    @Test
    public void testGetSize_1K() {
        Bundle extras = new Bundle();
        extras.putShort("sak", (short) 0x08);
        extras.putByteArray("atqa", new byte[]{0x00});
        Tag tag = new Tag(new byte[]{},
                new int[]{TagTechnology.MIFARE_CLASSIC, TagTechnology.NFC_A},
                new Bundle[]{null, extras}, 0, 0L, null);
        MifareClassic classic = MifareClassic.get(tag);

        assertEquals(MifareClassic.SIZE_1K, classic.getSize());
    }

    @Test
    public void testGetSize_mini() {
        Bundle extras = new Bundle();
        extras.putShort("sak", (short) 0x09);
        extras.putByteArray("atqa", new byte[]{0x00});
        Tag tag = new Tag(new byte[]{},
                new int[]{TagTechnology.MIFARE_CLASSIC, TagTechnology.NFC_A},
                new Bundle[]{null, extras}, 0, 0L, null);
        MifareClassic classic = MifareClassic.get(tag);

        assertEquals(MifareClassic.SIZE_MINI, classic.getSize());
    }

    @Test
    public void testGetSize_2K() {
        Bundle extras = new Bundle();
        extras.putShort("sak", (short) 0x10);
        extras.putByteArray("atqa", new byte[]{0x00});
        Tag tag = new Tag(new byte[]{},
                new int[]{TagTechnology.MIFARE_CLASSIC, TagTechnology.NFC_A},
                new Bundle[]{null, extras}, 0, 0L, null);
        MifareClassic classic = MifareClassic.get(tag);

        assertEquals(MifareClassic.SIZE_2K, classic.getSize());
    }

    @Test
    public void testGetSize_4K() {
        Bundle extras = new Bundle();
        extras.putShort("sak", (short) 0x11);
        extras.putByteArray("atqa", new byte[]{0x00});
        Tag tag = new Tag(new byte[]{},
                new int[]{TagTechnology.MIFARE_CLASSIC, TagTechnology.NFC_A},
                new Bundle[]{null, extras}, 0, 0L, null);
        MifareClassic classic = MifareClassic.get(tag);

        assertEquals(MifareClassic.SIZE_4K, classic.getSize());
    }

    @Test
    public void testGetSectorCount_1K() {
        Bundle extras = new Bundle();
        extras.putShort("sak", (short) 0x01);
        extras.putByteArray("atqa", new byte[]{0x00});
        Tag tag = new Tag(new byte[]{},
                new int[]{TagTechnology.MIFARE_CLASSIC, TagTechnology.NFC_A},
                new Bundle[]{null, extras}, 0, 0L, null);
        MifareClassic classic = MifareClassic.get(tag);

        assertEquals(16, classic.getSectorCount());
    }

    @Test
    public void testGetSectorCount_2K() {
        Bundle extras = new Bundle();
        extras.putShort("sak", (short) 0x10);
        extras.putByteArray("atqa", new byte[]{0x00});
        Tag tag = new Tag(new byte[]{},
                new int[]{TagTechnology.MIFARE_CLASSIC, TagTechnology.NFC_A},
                new Bundle[]{null, extras}, 0, 0L, null);
        MifareClassic classic = MifareClassic.get(tag);

        assertEquals(32, classic.getSectorCount());
    }

    @Test
    public void testGetSectorCount_4K() {
        Bundle extras = new Bundle();
        extras.putShort("sak", (short) 0x11);
        extras.putByteArray("atqa", new byte[]{0x00});
        Tag tag = new Tag(new byte[]{},
                new int[]{TagTechnology.MIFARE_CLASSIC, TagTechnology.NFC_A},
                new Bundle[]{null, extras}, 0, 0L, null);
        MifareClassic classic = MifareClassic.get(tag);

        assertEquals(40, classic.getSectorCount());
    }

    @Test
    public void testGetSectorCount_mini() {
        Bundle extras = new Bundle();
        extras.putShort("sak", (short) 0x09);
        extras.putByteArray("atqa", new byte[]{0x00});
        Tag tag = new Tag(new byte[]{},
                new int[]{TagTechnology.MIFARE_CLASSIC, TagTechnology.NFC_A},
                new Bundle[]{null, extras}, 0, 0L, null);
        MifareClassic classic = MifareClassic.get(tag);

        assertEquals(5, classic.getSectorCount());
    }

    @Test
    public void testGetBlockCount() {
        MifareClassic classic = createMifareClassic1K();

        assertEquals(64, classic.getBlockCount());
    }

    @Test
    public void testGetBlockCountInSector() {
        MifareClassic classic = createMifareClassic1K();

        assertEquals(4, classic.getBlockCountInSector(0));
        assertEquals(16, classic.getBlockCountInSector(32));
    }

    @Test
    public void testBlockToSector() {
        MifareClassic classic = createMifareClassic1K();

        assertEquals(1, classic.blockToSector(4));
        assertEquals(33, classic.blockToSector(144));
    }

    @Test
    public void testSectorToBlock() {
        MifareClassic classic = createMifareClassic1K();

        assertEquals(4, classic.sectorToBlock(1));
        assertEquals(144, classic.sectorToBlock(33));
    }

    @Test
    public void testAuthenticateSectorWithKeyA() throws RemoteException, IOException {
        MifareClassic classic = createConnectedMifareClassic();

        byte[] key = new byte[]{0xA, 0xB, 0xC, 0xD, 0xE, 0xF};
        when(mNfcTagMock.transceive(anyInt(), any(), anyBoolean())).thenReturn(
                new TransceiveResult(TransceiveResult.RESULT_SUCCESS, new byte[]{}));

        assertTrue(classic.authenticateSectorWithKeyA(0, key));
        verify(mNfcTagMock, times(1)).transceive(anyInt(), mTransceiveDataCaptor.capture(),
                anyBoolean());
        assertArrayEquals(
                new byte[]{0x60, 0x00, 0x01, 0x02, 0x03, 0x04, 0xA, 0xB, 0xC, 0xD, 0xE, 0xF},
                mTransceiveDataCaptor.getValue());
    }

    @Test
    public void testAuthenticateSectorWithKeyB() throws RemoteException, IOException {
        MifareClassic classic = createConnectedMifareClassic();

        byte[] key = new byte[]{0xA, 0xB, 0xC, 0xD, 0xE, 0xF};
        when(mNfcTagMock.transceive(anyInt(), any(), anyBoolean())).thenReturn(
                new TransceiveResult(TransceiveResult.RESULT_SUCCESS, new byte[]{}));

        assertTrue(classic.authenticateSectorWithKeyB(0, key));
        verify(mNfcTagMock, times(1)).transceive(anyInt(), mTransceiveDataCaptor.capture(),
                anyBoolean());
        assertArrayEquals(
                new byte[]{0x61, 0x00, 0x01, 0x02, 0x03, 0x04, 0xA, 0xB, 0xC, 0xD, 0xE, 0xF},
                mTransceiveDataCaptor.getValue());
    }

    @Test
    public void testAuthenticateSectorWithKeyB_nullTransceive()
            throws RemoteException, IOException {
        MifareClassic classic = createConnectedMifareClassic();

        byte[] key = new byte[]{0xA, 0xB, 0xC, 0xD, 0xE, 0xF};
        when(mNfcTagMock.transceive(anyInt(), any(), anyBoolean())).thenReturn(
                new TransceiveResult(TransceiveResult.RESULT_SUCCESS, null));

        assertEquals(false, classic.authenticateSectorWithKeyB(0, key));
    }

    @Test
    public void testAuthenticateSectorWithKeyB_ioException() throws RemoteException, IOException {
        MifareClassic classic = createConnectedMifareClassic();

        byte[] key = new byte[]{0xA, 0xB, 0xC, 0xD, 0xE, 0xF};
        when(mNfcTagMock.transceive(anyInt(), any(), anyBoolean())).thenReturn(null);

        assertEquals(false, classic.authenticateSectorWithKeyB(0, key));
    }

    @Test
    public void testReadBlock() throws RemoteException, IOException {
        MifareClassic classic = createConnectedMifareClassic();

        when(mNfcTagMock.transceive(anyInt(), any(), anyBoolean())).thenReturn(
                new TransceiveResult(TransceiveResult.RESULT_SUCCESS, new byte[]{0xA}));
        byte[] result = classic.readBlock(0);

        verify(mNfcTagMock, times(1)).transceive(anyInt(), mTransceiveDataCaptor.capture(),
                anyBoolean());
        assertArrayEquals(new byte[]{0xA}, classic.readBlock(0));
        assertArrayEquals(new byte[]{0x30, 0x00}, mTransceiveDataCaptor.getValue());
    }

    @Test
    public void testWriteBlock() throws RemoteException, IOException {
        byte[] block = new byte[]{0xA, 0xB, 0xC, 0xD, 0xA, 0xB, 0xC, 0xD, 0xA, 0xB, 0xC, 0xD, 0xA,
                0xB, 0xC, 0xD};
        MifareClassic classic = createConnectedMifareClassic();
        when(mNfcTagMock.transceive(anyInt(), any(), anyBoolean())).thenReturn(
                new TransceiveResult(TransceiveResult.RESULT_SUCCESS, new byte[]{}));

        classic.writeBlock(0, block);

        verify(mNfcTagMock, times(1)).transceive(anyInt(), mTransceiveDataCaptor.capture(),
                anyBoolean());
        assertArrayEquals(
                new byte[]{(byte) 0xA0, 0x0, 0xA, 0xB, 0xC, 0xD, 0xA, 0xB, 0xC, 0xD, 0xA, 0xB, 0xC,
                        0xD, 0xA, 0xB, 0xC, 0xD}, mTransceiveDataCaptor.getValue());
    }

    @Test
    public void testWriteBlock_incorrectSize() throws RemoteException, IOException {
        byte[] block = new byte[]{0xA, 0xB, 0xC, 0xD};
        MifareClassic classic = createConnectedMifareClassic();

        assertThrows(() -> classic.writeBlock(0, block));
    }

    @Test
    public void testIncrement() throws RemoteException, IOException {
        MifareClassic classic = createConnectedMifareClassic();

        when(mNfcTagMock.transceive(anyInt(), any(), anyBoolean())).thenReturn(
                new TransceiveResult(TransceiveResult.RESULT_SUCCESS, new byte[]{0x0}));
        classic.increment(4, 0x01010101);

        verify(mNfcTagMock, times(1)).transceive(anyInt(), mTransceiveDataCaptor.capture(),
                anyBoolean());
        assertArrayEquals(new byte[]{(byte) 0xC1, 0x04, 0x01, 0x01, 0x01, 0x01},
                mTransceiveDataCaptor.getValue());
    }

    @Test
    public void testDecrement() throws RemoteException, IOException {
        MifareClassic classic = createConnectedMifareClassic();

        when(mNfcTagMock.transceive(anyInt(), any(), anyBoolean())).thenReturn(
                new TransceiveResult(TransceiveResult.RESULT_SUCCESS, new byte[]{0x0}));
        classic.decrement(4, 0x01010101);

        verify(mNfcTagMock, times(1)).transceive(anyInt(), mTransceiveDataCaptor.capture(),
                anyBoolean());
        assertArrayEquals(new byte[]{(byte) 0xC0, 0x04, 0x01, 0x01, 0x01, 0x01},
                mTransceiveDataCaptor.getValue());
    }

    @Test
    public void testTransfer() throws RemoteException, IOException {
        MifareClassic classic = createConnectedMifareClassic();

        when(mNfcTagMock.transceive(anyInt(), any(), anyBoolean())).thenReturn(
                new TransceiveResult(TransceiveResult.RESULT_SUCCESS, new byte[]{0x0}));
        classic.transfer(0);

        verify(mNfcTagMock, times(1)).transceive(anyInt(), mTransceiveDataCaptor.capture(),
                anyBoolean());
        assertArrayEquals(new byte[]{(byte) 0xB0, 0x00}, mTransceiveDataCaptor.getValue());
    }

    @Test
    public void testRestore() throws RemoteException, IOException {
        MifareClassic classic = createConnectedMifareClassic();

        when(mNfcTagMock.transceive(anyInt(), any(), anyBoolean())).thenReturn(
                new TransceiveResult(TransceiveResult.RESULT_SUCCESS, new byte[]{0x0}));
        classic.restore(0);

        verify(mNfcTagMock, times(1)).transceive(anyInt(), mTransceiveDataCaptor.capture(),
                anyBoolean());
        assertArrayEquals(new byte[]{(byte) 0xC2, 0x00}, mTransceiveDataCaptor.getValue());
    }

    @Test
    public void testTransceive() throws RemoteException, IOException {
        MifareClassic classic = createConnectedMifareClassic();

        when(mNfcTagMock.transceive(anyInt(), any(), anyBoolean())).thenReturn(
                new TransceiveResult(TransceiveResult.RESULT_SUCCESS, new byte[]{0xF}));
        byte[] transceivedBytes = new byte[]{0xA, 0xB, 0xC, 0xD};

        assertArrayEquals(new byte[]{0xF}, classic.transceive(transceivedBytes));
        verify(mNfcTagMock, times(1)).transceive(anyInt(), mTransceiveDataCaptor.capture(),
                anyBoolean());
        assertArrayEquals(transceivedBytes, mTransceiveDataCaptor.getValue());
    }

    @Test
    public void testGetMaxTransceiveLength() throws RemoteException, IOException {
        MifareClassic classic = createMifareClassic1K();

        when(mNfcTagMock.isTagUpToDate(anyLong())).thenReturn(true);
        when(mNfcTagMock.getMaxTransceiveLength(TagTechnology.MIFARE_CLASSIC)).thenReturn(16);

        assertEquals(16, classic.getMaxTransceiveLength());
    }

    @Test
    public void testSetTimeout() throws RemoteException, IOException {
        MifareClassic classic = createMifareClassic1K();

        when(mNfcTagMock.isTagUpToDate(anyLong())).thenReturn(true);
        when(mNfcTagMock.setTimeout(anyInt(), anyInt())).thenReturn(ErrorCodes.SUCCESS);
        classic.setTimeout(250);

        ArgumentCaptor<Integer> timeout = ArgumentCaptor.forClass(Integer.class);
        verify(mNfcTagMock, times(1)).setTimeout(anyInt(), timeout.capture());
        assertTrue(250 == timeout.getValue());
    }

    @Test
    public void testSetTimeout_invalidTimeout() throws RemoteException, IOException {
        MifareClassic classic = createMifareClassic1K();

        when(mNfcTagMock.isTagUpToDate(anyLong())).thenReturn(true);
        when(mNfcTagMock.setTimeout(anyInt(), anyInt())).thenReturn(ErrorCodes.ERROR_INVALID_PARAM);
        assertThrows(() -> classic.setTimeout(250));
    }

    @Test
    public void testGetTimeout() throws RemoteException, IOException {
        MifareClassic classic = createConnectedMifareClassic();

        when(mNfcTagMock.getTimeout(anyInt())).thenReturn(250);
        assertEquals(250, classic.getTimeout());
    }

    @Test
    public void testGetTimeout_serviceDead() throws RemoteException, IOException {
        MifareClassic classic = createConnectedMifareClassic();

        when(mNfcTagMock.getTimeout(anyInt())).thenThrow(new RemoteException());
        assertEquals(0, classic.getTimeout());
    }

    @Test
    public void testGetTag() {
        Bundle extras = new Bundle();
        extras.putShort("sak", (short) 0x01);
        extras.putByteArray("atqa", new byte[]{0x02});
        Tag tag = new Tag(new byte[]{0x01, 0x02, 0x03, 0x04},
                new int[]{TagTechnology.MIFARE_CLASSIC, TagTechnology.NFC_A},
                new Bundle[]{null, extras}, 0, 0L, mNfcTagMock);
        MifareClassic classic = MifareClassic.get(tag);

        assertEquals(tag, classic.getTag());
    }

    @Test
    public void testIsConnected_isConnected() throws RemoteException, IOException {
        MifareClassic classic = createConnectedMifareClassic();
        when(mNfcTagMock.isPresent(anyInt())).thenReturn(true);

        assertEquals(true, classic.isConnected());
    }

    @Test
    public void testIsConnected_notConnected() throws RemoteException, IOException {
        MifareClassic classic = createMifareClassic1K();
        when(mNfcTagMock.isPresent(anyInt())).thenReturn(false);

        assertEquals(false, classic.isConnected());
    }


    @Test
    public void testIsConnected_serviceDead() throws RemoteException, IOException {
        MifareClassic classic = createConnectedMifareClassic();
        when(mNfcTagMock.isPresent(anyInt())).thenThrow(new RemoteException());

        assertEquals(false, classic.isConnected());
    }

    @Test
    public void testReconnect() throws RemoteException, IOException {
        MifareClassic classic = createConnectedMifareClassic();
        when(mNfcTagMock.reconnect(anyInt())).thenReturn(ErrorCodes.SUCCESS);

        classic.reconnect();
        verify(mNfcTagMock, times(1)).reconnect(anyInt());
    }

    @Test
    public void testReconnect_notConnected() throws RemoteException, IOException {
        MifareClassic classic = createMifareClassic1K();

        assertThrows(() -> classic.reconnect());
    }

    @Test
    public void testReconnect_failReconnect() throws RemoteException, IOException {
        MifareClassic classic = createConnectedMifareClassic();
        when(mNfcTagMock.reconnect(anyInt())).thenReturn(ErrorCodes.ERROR_CONNECT);

        assertThrows(() -> classic.reconnect());
    }

    @Test
    public void testReconnect_remoteException() throws RemoteException, IOException {
        MifareClassic classic = createConnectedMifareClassic();
        when(mNfcTagMock.reconnect(anyInt())).thenThrow(new RemoteException());

        assertThrows(() -> classic.reconnect());
    }

    @Test
    public void testClose() throws RemoteException, IOException {
        MifareClassic classic = createConnectedMifareClassic();
        when(mNfcTagMock.reconnect(anyInt())).thenReturn(ErrorCodes.SUCCESS);
        when(mNfcTagMock.isPresent(anyInt())).thenReturn(true);
        classic.close();

        assertEquals(false, classic.isConnected());
    }

    private MifareClassic createConnectedMifareClassic() throws RemoteException, IOException {
        MifareClassic classic = createMifareClassic1K();

        when(mNfcTagMock.isTagUpToDate(anyLong())).thenReturn(true);
        when(mNfcTagMock.connect(anyInt(), anyInt())).thenReturn(ErrorCodes.SUCCESS);
        classic.connect();

        return classic;
    }

    private MifareClassic createMifareClassic1K() {
        Bundle extras = new Bundle();
        extras.putShort("sak", (short) 0x01);
        extras.putByteArray("atqa", new byte[]{0x00});
        Tag tag = new Tag(new byte[]{0x01, 0x02, 0x03, 0x04},
                new int[]{TagTechnology.MIFARE_CLASSIC, TagTechnology.NFC_A},
                new Bundle[]{null, extras}, 0, 0L, mNfcTagMock);

        return MifareClassic.get(tag);
    }
}
