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
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

import android.nfc.ErrorCodes;
import android.nfc.INfcTag;
import android.nfc.Tag;
import android.nfc.tech.NfcBarcode;
import android.nfc.tech.TagTechnology;
import android.os.Bundle;
import android.os.RemoteException;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;

public class NfcBarcodeTest {

    private static final byte[] ID = new byte[]{0x01, 0x02, 0x03, 0x04};

    @Mock
    private INfcTag mNfcTagMock;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testGet_isNfcBarcode() {
        NfcBarcode nfcBarcode = createNfcBarcode();

        assertTrue("Expected to not be <null>", nfcBarcode != null);
    }

    @Test
    public void testGet_tagNotNfcBarcode() {
        Tag tag = new Tag(new byte[]{}, new int[]{TagTechnology.NFC_B}, new Bundle[]{}, 0, 0L,
                null);
        NfcBarcode nfcBarcode = NfcBarcode.get(tag);

        assertTrue("Expected: <null> ", nfcBarcode == null);
    }

    @Test
    public void testGet_nullTechExtras() {
        Tag tag = new Tag(new byte[]{}, new int[]{TagTechnology.NFC_BARCODE}, new Bundle[]{null}, 0,
                0L, null);

        assertThrows(() -> NfcBarcode.get(tag));
    }

    @Test
    public void testGetType() throws RemoteException, IOException {
        NfcBarcode nfcBarcode = createNfcBarcode();

        assertEquals(1, nfcBarcode.getType());
    }

    @Test
    public void testGetBarcode_kovio() throws RemoteException, IOException {
        NfcBarcode nfcBarcode = createNfcBarcode();

        assertEquals(ID, nfcBarcode.getBarcode());
    }

    @Test
    public void testGetBarcode_unknown() throws RemoteException, IOException {
        Bundle extras = new Bundle();
        extras.putInt("barcodetype", 0);
        Tag tag = new Tag(ID, new int[]{TagTechnology.NFC_BARCODE}, new Bundle[]{extras}, 0, 0L,
                mNfcTagMock);
        NfcBarcode nfcBarcode = NfcBarcode.get(tag);

        assertEquals(null, nfcBarcode.getBarcode());
    }

    @Test
    public void testGetTag() {
        Bundle extras = new Bundle();
        extras.putInt("barcodetype", 1);
        Tag tag = new Tag(new byte[]{0x01, 0x02, 0x03, 0x04}, new int[]{TagTechnology.NFC_BARCODE},
                new Bundle[]{extras}, 0, 0L, mNfcTagMock);
        NfcBarcode nfcBarcode = NfcBarcode.get(tag);

        assertEquals(tag, nfcBarcode.getTag());
    }

    @Test
    public void testIsConnected_isConnected() throws RemoteException, IOException {
        NfcBarcode nfcBarcode = createConnectedNfcBarcode();
        when(mNfcTagMock.isPresent(anyInt())).thenReturn(true);

        assertEquals(true, nfcBarcode.isConnected());
    }

    @Test
    public void testIsConnected_notConnected() throws RemoteException, IOException {
        NfcBarcode nfcBarcode = createNfcBarcode();
        when(mNfcTagMock.isPresent(anyInt())).thenReturn(false);

        assertEquals(false, nfcBarcode.isConnected());
    }


    @Test
    public void testIsConnected_serviceDead() throws RemoteException, IOException {
        NfcBarcode nfcBarcode = createConnectedNfcBarcode();
        when(mNfcTagMock.isPresent(anyInt())).thenThrow(new RemoteException());

        assertEquals(false, nfcBarcode.isConnected());
    }

    @Test
    public void testReconnect() throws RemoteException, IOException {
        NfcBarcode nfcBarcode = createConnectedNfcBarcode();
        when(mNfcTagMock.reconnect(anyInt())).thenReturn(ErrorCodes.SUCCESS);

        nfcBarcode.reconnect();
        verify(mNfcTagMock, times(1)).reconnect(anyInt());
    }

    @Test
    public void testReconnect_notConnected() throws RemoteException, IOException {
        NfcBarcode nfcBarcode = createNfcBarcode();

        assertThrows(() -> nfcBarcode.reconnect());
    }

    @Test
    public void testReconnect_failReconnect() throws RemoteException, IOException {
        NfcBarcode nfcBarcode = createConnectedNfcBarcode();
        when(mNfcTagMock.reconnect(anyInt())).thenReturn(ErrorCodes.ERROR_CONNECT);

        assertThrows(() -> nfcBarcode.reconnect());
    }

    @Test
    public void testReconnect_remoteException() throws RemoteException, IOException {
        NfcBarcode nfcBarcode = createConnectedNfcBarcode();
        when(mNfcTagMock.reconnect(anyInt())).thenThrow(new RemoteException());

        assertThrows(() -> nfcBarcode.reconnect());
    }

    @Test
    public void testClose() throws RemoteException, IOException {
        NfcBarcode nfcBarcode = createConnectedNfcBarcode();
        when(mNfcTagMock.reconnect(anyInt())).thenReturn(ErrorCodes.SUCCESS);
        when(mNfcTagMock.isPresent(anyInt())).thenReturn(true);
        nfcBarcode.close();

        assertEquals(false, nfcBarcode.isConnected());
    }

    private NfcBarcode createConnectedNfcBarcode() throws RemoteException, IOException {
        NfcBarcode nfcBarcode = createNfcBarcode();

        when(mNfcTagMock.isTagUpToDate(anyLong())).thenReturn(true);
        when(mNfcTagMock.connect(anyInt(), anyInt())).thenReturn(ErrorCodes.SUCCESS);
        nfcBarcode.connect();

        return nfcBarcode;
    }

    private NfcBarcode createNfcBarcode() {
        Bundle extras = new Bundle();
        extras.putInt("barcodetype", 1);
        Tag tag = new Tag(ID, new int[]{TagTechnology.NFC_BARCODE}, new Bundle[]{extras}, 0, 0L,
                mNfcTagMock);

        return NfcBarcode.get(tag);
    }
}
