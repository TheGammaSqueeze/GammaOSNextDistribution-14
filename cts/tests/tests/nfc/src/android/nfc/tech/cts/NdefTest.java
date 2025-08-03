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
import android.nfc.tech.Ndef;
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

public class NdefTest {

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
    public void testGet_isNdef() {
        Ndef ndef = createNdef(1);

        assertTrue("Expected to not be <null>", ndef != null);
    }

    @Test
    public void testGet_notNdef() {
        Tag tag = new Tag(new byte[]{}, new int[]{TagTechnology.NFC_A}, new Bundle[]{}, 0, 0L,
                null);
        Ndef ndef = Ndef.get(tag);

        assertTrue("Expected: <null> ", ndef == null);
    }

    @Test
    public void testGetCachedNdefMessage() {
        Ndef ndef = createNdef(1);

        assertEquals(mNdefMessage, ndef.getCachedNdefMessage());
    }

    @Test
    public void testGetType_type1() {
        Ndef ndef = createNdef(1);

        assertEquals(Ndef.NFC_FORUM_TYPE_1, ndef.getType());
    }

    @Test
    public void testGetType_type2() {
        Ndef ndef = createNdef(2);

        assertEquals(Ndef.NFC_FORUM_TYPE_2, ndef.getType());
    }

    @Test
    public void testGetType_type3() {
        Ndef ndef = createNdef(3);

        assertEquals(Ndef.NFC_FORUM_TYPE_3, ndef.getType());
    }

    @Test
    public void testGetType_type4() {
        Ndef ndef = createNdef(4);

        assertEquals(Ndef.NFC_FORUM_TYPE_4, ndef.getType());
    }

    @Test
    public void testGetType_mifareClassic() {
        Ndef ndef = createNdef(101);

        assertEquals(Ndef.MIFARE_CLASSIC, ndef.getType());
    }

    @Test
    public void testGetType_iCodeSLI() {
        Ndef ndef = createNdef(102);

        assertEquals(Ndef.ICODE_SLI, ndef.getType());
    }

    @Test
    public void testGetType_unknown() {
        Ndef ndef = createNdef(-1);

        assertEquals(Ndef.UNKNOWN, ndef.getType());
    }

    @Test
    public void testGetMaxLength() {
        Ndef ndef = createNdef(1);

        assertEquals(NDEF_MAX_LENGTH, ndef.getMaxSize());
    }

    @Test
    public void testIsWritable() {
        Ndef ndef = createNdef(1);

        assertEquals(true, ndef.isWritable());
    }

    @Test
    public void testGetNdefMessage() throws Exception {
        Ndef ndef = createConnectedNdef();
        when(mNfcTagMock.isNdef(anyInt())).thenReturn(true);
        when(mNfcTagMock.ndefRead(anyInt())).thenReturn(mNdefMessage);
        when(mNfcTagMock.isPresent(anyInt())).thenReturn(true);

        assertEquals(mNdefMessage, ndef.getNdefMessage());
    }

    @Test
    public void testGetNdefMessage_noTagService() throws Exception {
        Ndef ndef = createNdefNoTagService();

        assertThrows(() -> ndef.getNdefMessage());
    }

    @Test
    public void testGetNdefMessage_notNdef() throws Exception {
        Ndef ndef = createConnectedNdef();
        when(mNfcTagMock.isNdef(anyInt())).thenReturn(true);
        when(mNfcTagMock.isPresent(anyInt())).thenReturn(true);

        assertEquals(null, ndef.getNdefMessage());
    }

    @Test
    public void testGetNdefMessage_notNdefNotPresent() throws Exception {
        Ndef ndef = createConnectedNdef();
        when(mNfcTagMock.isNdef(anyInt())).thenReturn(true);
        when(mNfcTagMock.isPresent(anyInt())).thenReturn(false);

        assertThrows(() -> ndef.getNdefMessage());
    }

    @Test
    public void testGetNdefMessage_noTagOnRead() throws Exception {
        Ndef ndef = createConnectedNdef();
        when(mNfcTagMock.isNdef(anyInt())).thenReturn(true);
        when(mNfcTagMock.ndefRead(anyInt())).thenReturn(null);
        when(mNfcTagMock.isPresent(anyInt())).thenReturn(false);

        assertThrows(() -> ndef.getNdefMessage());
    }

    @Test
    public void testWriteNdefMessage() throws Exception {
        Ndef ndef = createConnectedNdef();
        when(mNfcTagMock.isNdef(anyInt())).thenReturn(true);
        when(mNfcTagMock.ndefWrite(anyInt(), any())).thenReturn(ErrorCodes.SUCCESS);

        ndef.writeNdefMessage(mNdefMessage);

        verify(mNfcTagMock, times(1)).ndefWrite(anyInt(), mNdefMessageCaptor.capture());
        assertEquals(mNdefMessage, mNdefMessageCaptor.getValue());
    }

    @Test
    public void testWriteNdefMessage_noTagService() throws Exception {
        Ndef ndef = createNdefNoTagService();

        assertThrows(() -> ndef.writeNdefMessage(mNdefMessage));
    }

    @Test
    public void testWriteNdefMessage_notNdef() throws Exception {
        Ndef ndef = createConnectedNdef();
        when(mNfcTagMock.isNdef(anyInt())).thenReturn(false);

        assertThrows(() -> ndef.writeNdefMessage(mNdefMessage));
    }

    @Test
    public void testWriteNdefMessage_ioError() throws Exception {
        Ndef ndef = createConnectedNdef();
        when(mNfcTagMock.isNdef(anyInt())).thenReturn(true);
        when(mNfcTagMock.ndefWrite(anyInt(), any())).thenReturn(ErrorCodes.ERROR_IO);

        assertThrows(() -> ndef.writeNdefMessage(mNdefMessage));
    }

    @Test
    public void testWriteNdefMessage_formatError() throws Exception {
        Ndef ndef = createConnectedNdef();
        when(mNfcTagMock.isNdef(anyInt())).thenReturn(true);
        when(mNfcTagMock.ndefWrite(anyInt(), any())).thenReturn(ErrorCodes.ERROR_INVALID_PARAM);

        assertThrows(() -> ndef.writeNdefMessage(mNdefMessage));
    }

    @Test
    public void testWriteNdefMessage_unknownError() throws Exception {
        Ndef ndef = createConnectedNdef();
        when(mNfcTagMock.isNdef(anyInt())).thenReturn(true);
        when(mNfcTagMock.ndefWrite(anyInt(), any())).thenReturn(1);

        assertThrows(() -> ndef.writeNdefMessage(mNdefMessage));
    }

    @Test
    public void testCanMakeReadOnly() throws RemoteException, IOException {
        Ndef ndef = createConnectedNdef();
        when(mNfcTagMock.canMakeReadOnly(anyInt())).thenReturn(true);

        assertEquals(true, ndef.canMakeReadOnly());
    }

    @Test
    public void testCanMakeReadOnly_noTagService() throws RemoteException, IOException {
        Ndef ndef = createNdefNoTagService();

        assertEquals(false, ndef.canMakeReadOnly());
    }

    @Test
    public void testCanMakeReadOnly_serviceDead() throws RemoteException, IOException {
        Ndef ndef = createConnectedNdef();
        when(mNfcTagMock.canMakeReadOnly(anyInt())).thenThrow(new RemoteException());

        assertEquals(false, ndef.canMakeReadOnly());
    }

    @Test
    public void testMakeReadOnly() throws RemoteException, IOException {
        Ndef ndef = createConnectedNdef();
        when(mNfcTagMock.isNdef(anyInt())).thenReturn(true);
        when(mNfcTagMock.ndefMakeReadOnly(anyInt())).thenReturn(ErrorCodes.SUCCESS);

        assertEquals(true, ndef.makeReadOnly());
    }

    @Test
    public void testMakeReadOnly_noTagService() throws RemoteException, IOException {
        Ndef ndef = createNdefNoTagService();

        assertEquals(false, ndef.makeReadOnly());
    }

    @Test
    public void testMakeReadOnly_notNdef() throws RemoteException, IOException {
        Ndef ndef = createConnectedNdef();
        when(mNfcTagMock.isNdef(anyInt())).thenReturn(false);

        assertThrows(() -> ndef.makeReadOnly());
    }

    @Test
    public void testMakeReadOnly_serviceDead() throws RemoteException, IOException {
        Ndef ndef = createConnectedNdef();
        when(mNfcTagMock.isNdef(anyInt())).thenReturn(true);
        when(mNfcTagMock.ndefMakeReadOnly(anyInt())).thenThrow(new RemoteException());

        assertEquals(false, ndef.makeReadOnly());
    }

    @Test
    public void testMakeReadOnly_ioError() throws RemoteException, IOException {
        Ndef ndef = createConnectedNdef();
        when(mNfcTagMock.isNdef(anyInt())).thenReturn(true);
        when(mNfcTagMock.ndefMakeReadOnly(anyInt())).thenReturn(ErrorCodes.ERROR_IO);

        assertThrows(() -> ndef.makeReadOnly());
    }

    @Test
    public void testMakeReadOnly_formatError() throws RemoteException, IOException {
        Ndef ndef = createConnectedNdef();
        when(mNfcTagMock.isNdef(anyInt())).thenReturn(true);
        when(mNfcTagMock.ndefMakeReadOnly(anyInt())).thenReturn(ErrorCodes.ERROR_INVALID_PARAM);

        assertEquals(false, ndef.makeReadOnly());
    }

    @Test
    public void testMakeReadOnly_unknownError() throws RemoteException, IOException {
        Ndef ndef = createConnectedNdef();
        when(mNfcTagMock.isNdef(anyInt())).thenReturn(true);
        when(mNfcTagMock.ndefMakeReadOnly(anyInt())).thenReturn(1);

        assertThrows(() -> ndef.makeReadOnly());
    }

    @Test
    public void testGetTag() {
        Bundle extras = new Bundle();
        extras.putInt(Ndef.EXTRA_NDEF_MAXLENGTH, NDEF_MAX_LENGTH);
        extras.putInt(Ndef.EXTRA_NDEF_CARDSTATE, 2);
        extras.putParcelable(Ndef.EXTRA_NDEF_MSG, mNdefMessage);
        extras.putInt(Ndef.EXTRA_NDEF_TYPE, 1);
        Tag tag = new Tag(new byte[]{0x01, 0x02, 0x03, 0x04}, new int[]{TagTechnology.NDEF},
                new Bundle[]{extras}, 0, 0L, mNfcTagMock);
        Ndef ndef = Ndef.get(tag);

        assertEquals(tag, ndef.getTag());
    }

    @Test
    public void testIsConnected_isConnected() throws RemoteException, IOException {
        Ndef ndef = createConnectedNdef();
        when(mNfcTagMock.isPresent(anyInt())).thenReturn(true);

        assertEquals(true, ndef.isConnected());
    }

    @Test
    public void testIsConnected_notConnected() throws RemoteException, IOException {
        Ndef ndef = createNdef(1);
        when(mNfcTagMock.isPresent(anyInt())).thenReturn(false);

        assertEquals(false, ndef.isConnected());
    }


    @Test
    public void testIsConnected_serviceDead() throws RemoteException, IOException {
        Ndef ndef = createConnectedNdef();
        when(mNfcTagMock.isPresent(anyInt())).thenThrow(new RemoteException());

        assertEquals(false, ndef.isConnected());
    }

    @Test
    public void testReconnect() throws RemoteException, IOException {
        Ndef ndef = createConnectedNdef();
        when(mNfcTagMock.reconnect(anyInt())).thenReturn(ErrorCodes.SUCCESS);

        ndef.reconnect();
        verify(mNfcTagMock, times(1)).reconnect(anyInt());
    }

    @Test
    public void testReconnect_notConnected() throws RemoteException, IOException {
        Ndef ndef = createNdef(1);

        assertThrows(() -> ndef.reconnect());
    }

    @Test
    public void testReconnect_failReconnect() throws RemoteException, IOException {
        Ndef ndef = createConnectedNdef();
        when(mNfcTagMock.reconnect(anyInt())).thenReturn(ErrorCodes.ERROR_CONNECT);

        assertThrows(() -> ndef.reconnect());
    }

    @Test
    public void testReconnect_remoteException() throws RemoteException, IOException {
        Ndef ndef = createConnectedNdef();
        when(mNfcTagMock.reconnect(anyInt())).thenThrow(new RemoteException());

        assertThrows(() -> ndef.reconnect());
    }

    @Test
    public void testClose() throws RemoteException, IOException {
        Ndef ndef = createConnectedNdef();
        when(mNfcTagMock.reconnect(anyInt())).thenReturn(ErrorCodes.SUCCESS);
        when(mNfcTagMock.isPresent(anyInt())).thenReturn(true);
        ndef.close();

        assertEquals(false, ndef.isConnected());
    }

    private Ndef createConnectedNdef() throws RemoteException, IOException {
        Ndef ndef = createNdef(1);

        when(mNfcTagMock.isTagUpToDate(anyLong())).thenReturn(true);
        when(mNfcTagMock.connect(anyInt(), anyInt())).thenReturn(ErrorCodes.SUCCESS);
        ndef.connect();

        return ndef;
    }

    private Ndef createNdef(int type) {
        Bundle extras = new Bundle();
        extras.putInt(Ndef.EXTRA_NDEF_MAXLENGTH, NDEF_MAX_LENGTH);
        extras.putInt(Ndef.EXTRA_NDEF_CARDSTATE, 2);
        extras.putParcelable(Ndef.EXTRA_NDEF_MSG, mNdefMessage);
        extras.putInt(Ndef.EXTRA_NDEF_TYPE, type);
        Tag tag = new Tag(new byte[]{0x01, 0x02, 0x03, 0x04}, new int[]{TagTechnology.NDEF},
                new Bundle[]{extras}, 0, 0L, mNfcTagMock);

        return Ndef.get(tag);
    }

    private Ndef createNdefNoTagService() {
        Bundle extras = new Bundle();
        extras.putInt(Ndef.EXTRA_NDEF_MAXLENGTH, NDEF_MAX_LENGTH);
        extras.putInt(Ndef.EXTRA_NDEF_CARDSTATE, 2);
        extras.putParcelable(Ndef.EXTRA_NDEF_MSG, mNdefMessage);
        extras.putInt(Ndef.EXTRA_NDEF_TYPE, 1);
        Tag tag = new Tag(new byte[]{0x01, 0x02, 0x03, 0x04}, new int[]{TagTechnology.NDEF},
                new Bundle[]{extras}, 0, 0L, null);
        tag.setConnectedTechnology(TagTechnology.NDEF);
        return Ndef.get(tag);
    }
}
