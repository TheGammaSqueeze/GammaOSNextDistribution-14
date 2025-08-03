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
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

import android.nfc.ErrorCodes;
import android.nfc.INfcTag;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.Tag;
import android.nfc.tech.NdefFormatable;
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

public class NdefFormatableTest {

    private static final int NDEF_MAX_LENGTH = 128;

    @Mock
    private INfcTag mNfcTagMock;
    @Captor
    private ArgumentCaptor<NdefMessage> mNdefMessageCaptor;
    private NdefMessage mNdefMessage;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        NdefRecord ndefRecord = NdefRecord.createTextRecord("en", "text");
        mNdefMessage = new NdefMessage(ndefRecord);
    }

    @Test
    public void testGet_isNdefFormatable() {
        Tag tag = new Tag(new byte[]{}, new int[]{TagTechnology.NDEF_FORMATABLE}, new Bundle[]{}, 0,
                0L, mNfcTagMock);
        NdefFormatable ndefFormatable = NdefFormatable.get(tag);

        assertTrue("Expected to not be <null>", ndefFormatable != null);
    }

    @Test
    public void testGet_notNdefFormatable() {
        Tag tag = new Tag(new byte[]{}, new int[]{TagTechnology.NDEF}, new Bundle[]{}, 0, 0L,
                mNfcTagMock);
        NdefFormatable ndefFormatable = NdefFormatable.get(tag);

        assertTrue("Expected: <null> ", ndefFormatable == null);
    }

    @Test
    public void testFormat() throws Exception {
        NdefFormatable ndefFormatable = createConnectedNdefFormatable();
        when(mNfcTagMock.formatNdef(anyInt(), any())).thenReturn(ErrorCodes.SUCCESS);
        when(mNfcTagMock.isNdef(anyInt())).thenReturn(true);
        when(mNfcTagMock.ndefWrite(anyInt(), any())).thenReturn(ErrorCodes.SUCCESS);

        ndefFormatable.format(mNdefMessage);

        verify(mNfcTagMock, times(1)).ndefWrite(anyInt(), mNdefMessageCaptor.capture());
        assertEquals(mNdefMessage, mNdefMessageCaptor.getValue());
    }

    @Test
    public void testFormatReadOnly() throws Exception {
        NdefFormatable ndefFormatable = createConnectedNdefFormatable();
        when(mNfcTagMock.formatNdef(anyInt(), any())).thenReturn(ErrorCodes.SUCCESS);
        when(mNfcTagMock.isNdef(anyInt())).thenReturn(true);
        when(mNfcTagMock.ndefWrite(anyInt(), any())).thenReturn(ErrorCodes.SUCCESS);
        when(mNfcTagMock.ndefMakeReadOnly(anyInt())).thenReturn(ErrorCodes.SUCCESS);

        ndefFormatable.formatReadOnly(mNdefMessage);

        verify(mNfcTagMock, times(1)).ndefWrite(anyInt(), mNdefMessageCaptor.capture());
        assertEquals(mNdefMessage, mNdefMessageCaptor.getValue());
    }

    @Test
    public void testFormatReadOnly_formatNdefIOError() throws Exception {
        NdefFormatable ndefFormatable = createConnectedNdefFormatable();
        when(mNfcTagMock.formatNdef(anyInt(), any())).thenReturn(ErrorCodes.ERROR_IO);

        assertThrows(() -> ndefFormatable.formatReadOnly(mNdefMessage));
    }

    @Test
    public void testFormatReadOnly_formatNdefFormatError() throws Exception {
        NdefFormatable ndefFormatable = createConnectedNdefFormatable();
        when(mNfcTagMock.formatNdef(anyInt(), any())).thenReturn(ErrorCodes.ERROR_INVALID_PARAM);

        assertThrows(() -> ndefFormatable.formatReadOnly(mNdefMessage));
    }

    @Test
    public void testFormatReadOnly_formatNdefUnknownError() throws Exception {
        NdefFormatable ndefFormatable = createConnectedNdefFormatable();
        when(mNfcTagMock.formatNdef(anyInt(), any())).thenReturn(1);

        assertThrows(() -> ndefFormatable.formatReadOnly(mNdefMessage));
    }

    @Test
    public void testFormatReadOnly_notNdef() throws Exception {
        NdefFormatable ndefFormatable = createConnectedNdefFormatable();
        when(mNfcTagMock.formatNdef(anyInt(), any())).thenReturn(ErrorCodes.SUCCESS);
        when(mNfcTagMock.isNdef(anyInt())).thenReturn(false);

        assertThrows(() -> ndefFormatable.formatReadOnly(mNdefMessage));
    }

    @Test
    public void testFormatReadOnly_ndefWriteIOError() throws Exception {
        NdefFormatable ndefFormatable = createConnectedNdefFormatable();
        when(mNfcTagMock.formatNdef(anyInt(), any())).thenReturn(ErrorCodes.SUCCESS);
        when(mNfcTagMock.isNdef(anyInt())).thenReturn(true);
        when(mNfcTagMock.ndefWrite(anyInt(), any())).thenReturn(ErrorCodes.ERROR_IO);

        assertThrows(() -> ndefFormatable.formatReadOnly(mNdefMessage));
    }

    @Test
    public void testFormatReadOnly_ndefWriteFormatError() throws Exception {
        NdefFormatable ndefFormatable = createConnectedNdefFormatable();
        when(mNfcTagMock.formatNdef(anyInt(), any())).thenReturn(ErrorCodes.SUCCESS);
        when(mNfcTagMock.isNdef(anyInt())).thenReturn(true);
        when(mNfcTagMock.ndefWrite(anyInt(), any())).thenReturn(ErrorCodes.ERROR_INVALID_PARAM);

        assertThrows(() -> ndefFormatable.formatReadOnly(mNdefMessage));
    }

    @Test
    public void testFormatReadOnly_ndefWriteUnknownError() throws Exception {
        NdefFormatable ndefFormatable = createConnectedNdefFormatable();
        when(mNfcTagMock.formatNdef(anyInt(), any())).thenReturn(ErrorCodes.SUCCESS);
        when(mNfcTagMock.isNdef(anyInt())).thenReturn(true);
        when(mNfcTagMock.ndefWrite(anyInt(), any())).thenReturn(1);

        assertThrows(() -> ndefFormatable.formatReadOnly(mNdefMessage));
    }

    @Test
    public void testFormatReadOnly_makeReadonlyIOError() throws Exception {
        NdefFormatable ndefFormatable = createConnectedNdefFormatable();
        when(mNfcTagMock.formatNdef(anyInt(), any())).thenReturn(ErrorCodes.SUCCESS);
        when(mNfcTagMock.isNdef(anyInt())).thenReturn(true);
        when(mNfcTagMock.ndefWrite(anyInt(), any())).thenReturn(ErrorCodes.SUCCESS);
        when(mNfcTagMock.ndefMakeReadOnly(anyInt())).thenReturn(ErrorCodes.ERROR_IO);

        assertThrows(() -> ndefFormatable.formatReadOnly(mNdefMessage));
    }

    @Test
    public void testFormatReadOnly_makeReadonlyFormatError() throws Exception {
        NdefFormatable ndefFormatable = createConnectedNdefFormatable();
        when(mNfcTagMock.formatNdef(anyInt(), any())).thenReturn(ErrorCodes.SUCCESS);
        when(mNfcTagMock.isNdef(anyInt())).thenReturn(true);
        when(mNfcTagMock.ndefWrite(anyInt(), any())).thenReturn(ErrorCodes.SUCCESS);
        when(mNfcTagMock.ndefMakeReadOnly(anyInt())).thenReturn(ErrorCodes.ERROR_INVALID_PARAM);

        assertThrows(() -> ndefFormatable.formatReadOnly(mNdefMessage));
    }

    @Test
    public void testFormatReadOnly_makeReadonlyUnknownError() throws Exception {
        NdefFormatable ndefFormatable = createConnectedNdefFormatable();
        when(mNfcTagMock.formatNdef(anyInt(), any())).thenReturn(ErrorCodes.SUCCESS);
        when(mNfcTagMock.isNdef(anyInt())).thenReturn(true);
        when(mNfcTagMock.ndefWrite(anyInt(), any())).thenReturn(ErrorCodes.SUCCESS);
        when(mNfcTagMock.ndefMakeReadOnly(anyInt())).thenReturn(1);

        assertThrows(() -> ndefFormatable.formatReadOnly(mNdefMessage));
    }

    @Test
    public void testGetTag() {
        Tag tag = new Tag(new byte[]{}, new int[]{TagTechnology.NDEF_FORMATABLE}, new Bundle[]{}, 0,
                0L, mNfcTagMock);
        NdefFormatable formatable = NdefFormatable.get(tag);

        assertEquals(tag, formatable.getTag());
    }

    @Test
    public void testIsConnected_isConnected() throws RemoteException, IOException {
        NdefFormatable formatable = createConnectedNdefFormatable();
        when(mNfcTagMock.isPresent(anyInt())).thenReturn(true);

        assertEquals(true, formatable.isConnected());
    }

    @Test
    public void testIsConnected_notConnected() throws RemoteException, IOException {
        Tag tag = new Tag(new byte[]{}, new int[]{TagTechnology.NDEF_FORMATABLE}, new Bundle[]{}, 0,
                0L, mNfcTagMock);
        NdefFormatable formatable = NdefFormatable.get(tag);
        when(mNfcTagMock.isPresent(anyInt())).thenReturn(false);

        assertEquals(false, formatable.isConnected());
    }


    @Test
    public void testIsConnected_serviceDead() throws RemoteException, IOException {
        NdefFormatable formatable = createConnectedNdefFormatable();
        when(mNfcTagMock.isPresent(anyInt())).thenThrow(new RemoteException());

        assertEquals(false, formatable.isConnected());
    }

    @Test
    public void testReconnect() throws RemoteException, IOException {
        NdefFormatable formatable = createConnectedNdefFormatable();
        when(mNfcTagMock.reconnect(anyInt())).thenReturn(ErrorCodes.SUCCESS);

        formatable.reconnect();
        verify(mNfcTagMock, times(1)).reconnect(anyInt());
    }

    @Test
    public void testReconnect_notConnected() throws RemoteException, IOException {
        Tag tag = new Tag(new byte[]{}, new int[]{TagTechnology.NDEF_FORMATABLE}, new Bundle[]{}, 0,
                0L, mNfcTagMock);
        NdefFormatable formatable = NdefFormatable.get(tag);

        assertThrows(() -> formatable.reconnect());
    }

    @Test
    public void testReconnect_failReconnect() throws RemoteException, IOException {
        NdefFormatable formatable = createConnectedNdefFormatable();
        when(mNfcTagMock.reconnect(anyInt())).thenReturn(ErrorCodes.ERROR_CONNECT);

        assertThrows(() -> formatable.reconnect());
    }

    @Test
    public void testReconnect_remoteException() throws RemoteException, IOException {
        NdefFormatable formatable = createConnectedNdefFormatable();
        when(mNfcTagMock.reconnect(anyInt())).thenThrow(new RemoteException());

        assertThrows(() -> formatable.reconnect());
    }

    @Test
    public void testClose() throws RemoteException, IOException {
        NdefFormatable formatable = createConnectedNdefFormatable();
        when(mNfcTagMock.reconnect(anyInt())).thenReturn(ErrorCodes.SUCCESS);
        when(mNfcTagMock.isPresent(anyInt())).thenReturn(true);
        formatable.close();

        assertEquals(false, formatable.isConnected());
    }


    private NdefFormatable createConnectedNdefFormatable() throws IOException, RemoteException {
        Tag tag = new Tag(new byte[]{}, new int[]{TagTechnology.NDEF_FORMATABLE}, new Bundle[]{}, 0,
                0L, mNfcTagMock);
        NdefFormatable ndefFormatable = NdefFormatable.get(tag);

        when(mNfcTagMock.isTagUpToDate(anyLong())).thenReturn(true);
        when(mNfcTagMock.connect(anyInt(), anyInt())).thenReturn(ErrorCodes.SUCCESS);
        ndefFormatable.connect();

        return ndefFormatable;
    }
}
