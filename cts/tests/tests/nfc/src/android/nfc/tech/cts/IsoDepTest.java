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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.nfc.ErrorCodes;
import android.nfc.INfcTag;
import android.nfc.Tag;
import android.nfc.TransceiveResult;
import android.nfc.tech.IsoDep;
import android.nfc.tech.TagTechnology;
import android.os.Bundle;
import android.os.RemoteException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;

public class IsoDepTest {
    @Mock private INfcTag mINfcTag;
    @Captor private ArgumentCaptor<byte[]> mTransceiveCaptor;

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void createNullInstance() {
        Tag tag = new Tag(new byte[]{0x00}, new int[]{}, new Bundle[]{}, 0, 0L, null);
        IsoDep id = IsoDep.get(tag);
        Assert.assertNull(id);
    }

    @Test
    public void createNonNullInstance() {
        IsoDep id = createIsoDepInstance();
        Assert.assertNotNull(id);
    }

    @Test
    public void testGetTag() {
        Bundle extras = new Bundle();
        extras.putByteArray(IsoDep.EXTRA_HI_LAYER_RESP, new byte[]{0x00});
        extras.putByteArray(IsoDep.EXTRA_HIST_BYTES, new byte[]{0x00});
        Tag tag = new Tag(new byte[]{0x00}, new int[]{TagTechnology.ISO_DEP},
            new Bundle[]{extras}, 0, 0L, mINfcTag);
        IsoDep id = IsoDep.get(tag);
        Assert.assertEquals(tag, id.getTag());
    }

    @Test
    public void testIsConnected_isConnected() throws RemoteException, IOException {
        IsoDep id = createConnectedIsoDepInstance();
        when(mINfcTag.isPresent(anyInt())).thenReturn(true);
        Assert.assertTrue(id.isConnected());
    }

    @Test
    public void testIsConnected_isNotConnected() throws RemoteException, IOException {
        IsoDep id = createIsoDepInstance();
        when(mINfcTag.isPresent(anyInt())).thenReturn(false);
        Assert.assertFalse(id.isConnected());
    }

    @Test
    public void testIsConnected_serviceDead() throws RemoteException, IOException {
        IsoDep id = createConnectedIsoDepInstance();
        when(mINfcTag.isPresent(anyInt())).thenThrow(new RemoteException());
        Assert.assertFalse(id.isConnected());
    }

    @Test
    public void testReconnect_success() throws RemoteException, IOException {
        IsoDep id = createConnectedIsoDepInstance();
        when(mINfcTag.reconnect(anyInt())).thenReturn(ErrorCodes.SUCCESS);
        id.reconnect();
        verify(mINfcTag, times(1)).reconnect(anyInt());
    }

    @Test
    public void testReconnect_notConnected() throws RemoteException, IOException {
        IsoDep id = createIsoDepInstance();
        Assert.assertThrows(IllegalStateException.class, () -> id.reconnect());
    }

    @Test
    public void testReconnect_error() throws RemoteException, IOException {
        IsoDep id = createConnectedIsoDepInstance();
        when(mINfcTag.reconnect(anyInt())).thenReturn(ErrorCodes.ERROR_IO);
        Assert.assertThrows(IOException.class, () -> id.reconnect());
    }

    @Test
    public void testReconnect_serviceDead() throws RemoteException, IOException {
        IsoDep id = createConnectedIsoDepInstance();
        when(mINfcTag.reconnect(anyInt())).thenThrow(new RemoteException());
        Assert.assertThrows(IOException.class, () -> id.reconnect());
    }

    @Test
    public void testClose() throws RemoteException, IOException {
        IsoDep id = createConnectedIsoDepInstance();
        when(mINfcTag.reconnect(anyInt())).thenReturn(ErrorCodes.SUCCESS);
        id.close();
        Assert.assertFalse(id.isConnected());
    }

    @Test
    public void testSetTimeout_success() throws RemoteException, IOException {
        IsoDep id = createConnectedIsoDepInstance();
        when(mINfcTag.setTimeout(anyInt(), anyInt())).thenReturn(ErrorCodes.SUCCESS);
        id.setTimeout(250);
        ArgumentCaptor<Integer> timeout = ArgumentCaptor.forClass(Integer.class);
        verify(mINfcTag, times(1)).setTimeout(anyInt(), timeout.capture());
        Assert.assertEquals((int) timeout.getValue(), 250);
    }

    @Test
    public void testSetTimeout_invalid() throws RemoteException, IOException {
        IsoDep id = createConnectedIsoDepInstance();
        when(mINfcTag.setTimeout(anyInt(), anyInt())).thenReturn(ErrorCodes.ERROR_INVALID_PARAM);
        Assert.assertThrows(IllegalArgumentException.class, () -> id.setTimeout(250));
    }

    @Test
    public void testGetTimeout() throws RemoteException, IOException {
        IsoDep id = createConnectedIsoDepInstance();
        when(mINfcTag.getTimeout(anyInt())).thenReturn(250);
        Assert.assertEquals((int) id.getTimeout(), 250);
    }

    @Test
    public void testGetTimeout_serviceDead() throws RemoteException, IOException {
        IsoDep id = createConnectedIsoDepInstance();
        when(mINfcTag.getTimeout(anyInt())).thenThrow(new RemoteException());
        Assert.assertEquals((int) id.getTimeout(), 0);
    }

    @Test
    public void testGetHistoricalBytes() {
        IsoDep id = createIsoDepInstance();
        Assert.assertArrayEquals(id.getHistoricalBytes(), new byte[]{0x00});
    }

    @Test
    public void testGetHiLayerResponse() {
        IsoDep id = createIsoDepInstance();
        Assert.assertArrayEquals(id.getHiLayerResponse(), new byte[]{0x00});
    }

    @Test
    public void testTransceive() throws RemoteException, IOException {
        IsoDep id = createConnectedIsoDepInstance();
        when(mINfcTag.transceive(anyInt(), any(), anyBoolean())).thenReturn(
            new TransceiveResult(TransceiveResult.RESULT_SUCCESS, new byte[]{0xF}));
        byte[] transceivedBytes = new byte[]{0xA, 0xB, 0xC, 0xD};
        Assert.assertArrayEquals(new byte[]{0xF}, id.transceive(transceivedBytes));
        verify(mINfcTag, times(1)).transceive(anyInt(), mTransceiveCaptor.capture(), anyBoolean());
        Assert.assertArrayEquals(transceivedBytes, mTransceiveCaptor.getValue());
    }

    @Test
    public void testGetMaxTransceiveLength() throws RemoteException, IOException {
        IsoDep id = createConnectedIsoDepInstance();
        when(mINfcTag.getMaxTransceiveLength(TagTechnology.ISO_DEP)).thenReturn(16);
        Assert.assertTrue(id.getMaxTransceiveLength() == 16);
    }

    @Test
    public void testIsExtendedLengthApduSupported() throws RemoteException, IOException {
        IsoDep id = createConnectedIsoDepInstance();
        when(mINfcTag.getExtendedLengthApdusSupported()).thenReturn(true);
        Assert.assertTrue(id.isExtendedLengthApduSupported());
    }

    public IsoDep createConnectedIsoDepInstance() throws RemoteException, IOException {
        IsoDep id = createIsoDepInstance();
        when(mINfcTag.isTagUpToDate(anyLong())).thenReturn(true);
        when(mINfcTag.connect(anyInt(), anyInt())).thenReturn(ErrorCodes.SUCCESS);
        id.connect();
        return id;
    }

    public IsoDep createIsoDepInstance() {
        Bundle extras = new Bundle();
        extras.putByteArray(IsoDep.EXTRA_HI_LAYER_RESP, new byte[]{0x00});
        extras.putByteArray(IsoDep.EXTRA_HIST_BYTES, new byte[]{0x00});
        Tag tag = new Tag(new byte[]{0x00}, new int[]{TagTechnology.ISO_DEP},
            new Bundle[]{extras}, 0, 0L, mINfcTag);
        return IsoDep.get(tag);
    }
}
