/*
 * Copyright 2020 The Android Open Source Project
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

package com.android.bluetooth.avrcp;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.TestUtils;
import com.android.bluetooth.audio_util.Image;
import com.android.bluetooth.avrcpcontroller.BipEncoding;
import com.android.bluetooth.avrcpcontroller.BipImageDescriptor;
import com.android.obex.HeaderSet;
import com.android.obex.Operation;
import com.android.obex.ResponseCodes;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

@RunWith(AndroidJUnit4.class)
public class AvrcpBipObexServerTest {
    private static final String TYPE_GET_LINKED_THUMBNAIL = "x-bt/img-thm";
    private static final String TYPE_GET_IMAGE_PROPERTIES = "x-bt/img-properties";
    private static final String TYPE_GET_IMAGE = "x-bt/img-img";
    private static final String TYPE_BAD = "x-bt/bad-type";

    private static final byte HEADER_ID_IMG_HANDLE = 0x30;
    private static final byte HEADER_ID_IMG_DESCRIPTOR = 0x71;

    private static final byte[] BLUETOOTH_UUID_AVRCP_COVER_ART = new byte[] {
        (byte) 0x71,
        (byte) 0x63,
        (byte) 0xDD,
        (byte) 0x54,
        (byte) 0x4A,
        (byte) 0x7E,
        (byte) 0x11,
        (byte) 0xE2,
        (byte) 0xB4,
        (byte) 0x7C,
        (byte) 0x00,
        (byte) 0x50,
        (byte) 0xC2,
        (byte) 0x49,
        (byte) 0x00,
        (byte) 0x48
    };

    private static final byte[] NOT_BLUETOOTH_UUID_AVRCP_COVER_ART = new byte[] {
        (byte) 0x00,
        (byte) 0x00,
        (byte) 0x00,
        (byte) 0x00,
        (byte) 0x00,
        (byte) 0x00,
        (byte) 0x00,
        (byte) 0x00,
        (byte) 0x00,
        (byte) 0x00,
        (byte) 0x00,
        (byte) 0x00,
        (byte) 0x00,
        (byte) 0x00,
        (byte) 0x00,
        (byte) 0x00
    };

    private static final String IMAGE_HANDLE_1 = "0000001";
    private static final String IMAGE_HANDLE_UNSTORED = "0000256";
    private static final String IMAGE_HANDLE_INVALID = "abc1234"; // no non-numeric characters

    private Resources mTestResources;
    private CoverArt mCoverArt;

    private AvrcpCoverArtService mAvrcpCoverArtService = null;
    private AvrcpBipObexServer.Callback mCallback = null;

    private HeaderSet mRequest = null;
    private HeaderSet mReply = null;
    private ByteArrayOutputStream mOutputStream = null;

    private AvrcpBipObexServer mAvrcpBipObexServer;

    @Before
    public void setUp() throws Exception {
        mTestResources = TestUtils.getTestApplicationResources(
                InstrumentationRegistry.getTargetContext());

        mCoverArt = loadCoverArt(com.android.bluetooth.tests.R.raw.image_200_200);

        mAvrcpCoverArtService = mock(AvrcpCoverArtService.class);
        mCallback = mock(AvrcpBipObexServer.Callback.class);

        mRequest = new HeaderSet();
        mReply = new HeaderSet();
        mOutputStream = new ByteArrayOutputStream();

        mAvrcpBipObexServer = new AvrcpBipObexServer(mAvrcpCoverArtService, mCallback);
    }

    @After
    public void tearDown() throws Exception {
        mAvrcpBipObexServer = null;
        mOutputStream = null;
        mReply = null;
        mRequest = null;
        mCallback = null;
        mAvrcpCoverArtService = null;
        mCoverArt = null;
        mTestResources = null;
    }

    private CoverArt loadCoverArt(int resId) {
        InputStream imageInputStream = mTestResources.openRawResource(resId);
        Bitmap bitmap = BitmapFactory.decodeStream(imageInputStream);
        Image image = new Image(null, bitmap);
        return new CoverArt(image);
    }

    private void setCoverArtAvailableAtHandle(String handle, CoverArt art) {
        art.setImageHandle(handle);
        when(mAvrcpCoverArtService.getImage(handle)).thenReturn(art);
    }

    /**
     * Creates a mocked operation that can be used by our server as a client request
     *
     * Our server will use:
     *  - getReceivedHeader
     *  - sendHeaders
     *  - getMaxPacketSize
     *  - openOutputStream
     */
    private Operation makeOperation(HeaderSet requestHeaders, OutputStream os) throws Exception {
        Operation op = mock(Operation.class);
        when(op.getReceivedHeader()).thenReturn(requestHeaders);
        when(op.getMaxPacketSize()).thenReturn(256);
        when(op.openOutputStream()).thenReturn(os);
        return op;
    }

    private byte[] makeDescriptor(int encoding, int width, int height) {
        return new BipImageDescriptor.Builder()
                .setEncoding(encoding)
                .setFixedDimensions(width, height)
                .build().serialize();
    }

    /**
     * Make sure we let a connection through with a valid UUID
     */
    @Test
    public void testConnectWithValidUuidHeader() throws Exception {
        mRequest.setHeader(HeaderSet.TARGET, BLUETOOTH_UUID_AVRCP_COVER_ART);
        int responseCode = mAvrcpBipObexServer.onConnect(mRequest, mReply);
        verify(mCallback, times(1)).onConnected();
        assertThat(responseCode).isEqualTo(ResponseCodes.OBEX_HTTP_OK);
    }

    /**
     * Make sure we deny a connection when there is an invalid UUID
     */
    @Test
    public void testConnectWithInvalidUuidHeader() throws Exception {
        mRequest.setHeader(HeaderSet.TARGET, NOT_BLUETOOTH_UUID_AVRCP_COVER_ART);
        int responseCode = mAvrcpBipObexServer.onConnect(mRequest, mReply);
        verify(mCallback, never()).onConnected();
        assertThat(responseCode).isEqualTo(ResponseCodes.OBEX_HTTP_NOT_ACCEPTABLE);
    }

    /**
     * Make sure onDisconnect notifies the callbacks in the proper way
     */
    @Test
    public void testDisonnect() {
        mAvrcpBipObexServer.onDisconnect(mRequest, mReply);
        verify(mCallback, times(1)).onDisconnected();
    }

    /**
     * Make sure onClose notifies the callbacks in the proper way
     */
    @Test
    public void testOnClose() {
        mAvrcpBipObexServer.onClose();
        verify(mCallback, times(1)).onClose();
    }

    /**
     * Make sure onGet handles null headers gracefully
     */
    @Test
    public void testOnGetNoHeaders() throws Exception {
        Operation op = makeOperation(null, mOutputStream);
        int responseCode = mAvrcpBipObexServer.onGet(op);
        assertThat(responseCode).isEqualTo(ResponseCodes.OBEX_HTTP_BAD_REQUEST);
    }

    /**
     * Make sure onGet handles bad type gracefully
     */
    @Test
    public void testOnGetBadType() throws Exception {
        mRequest.setHeader(HeaderSet.TYPE, TYPE_BAD);
        Operation op = makeOperation(mRequest, mOutputStream);
        int responseCode = mAvrcpBipObexServer.onGet(op);
        assertThat(responseCode).isEqualTo(ResponseCodes.OBEX_HTTP_BAD_REQUEST);
    }

    /**
     * Make sure onGet handles no type gracefully
     */
    @Test
    public void testOnGetNoType() throws Exception {
        mRequest.setHeader(HeaderSet.TYPE, null);
        Operation op = makeOperation(mRequest, mOutputStream);
        int responseCode = mAvrcpBipObexServer.onGet(op);
        assertThat(responseCode).isEqualTo(ResponseCodes.OBEX_HTTP_BAD_REQUEST);
    }

    /**
     * Make sure a getImageThumbnail request with a valid handle works
     */
    @Test
    public void testGetLinkedThumbnailWithValidHandle() throws Exception {
        mRequest.setHeader(HeaderSet.TYPE, TYPE_GET_LINKED_THUMBNAIL);
        mRequest.setHeader(HEADER_ID_IMG_HANDLE, IMAGE_HANDLE_1);
        setCoverArtAvailableAtHandle(IMAGE_HANDLE_1, mCoverArt);
        Operation op = makeOperation(mRequest, mOutputStream);
        int responseCode = mAvrcpBipObexServer.onGet(op);
        assertThat(responseCode).isEqualTo(ResponseCodes.OBEX_HTTP_OK);
    }

    /**
     * Make sure a getImageThumbnail request with a unstored handle returns OBEX_HTTP_NOT_FOUND
     */
    @Test
    public void testGetLinkedThumbnailWithValidUnstoredHandle() throws Exception {
        mRequest.setHeader(HeaderSet.TYPE, TYPE_GET_LINKED_THUMBNAIL);
        mRequest.setHeader(HEADER_ID_IMG_HANDLE, IMAGE_HANDLE_UNSTORED);
        Operation op = makeOperation(mRequest, mOutputStream);
        int responseCode = mAvrcpBipObexServer.onGet(op);
        assertThat(responseCode).isEqualTo(ResponseCodes.OBEX_HTTP_NOT_FOUND);
    }

    /**
     * Make sure a getImageThumbnail request with an invalidly formatted handle returns
     * OBEX_HTTP_BAD_REQUEST
     */
    @Test
    public void testGetLinkedThumbnailWithInvalidHandle() throws Exception {
        mRequest.setHeader(HeaderSet.TYPE, TYPE_GET_LINKED_THUMBNAIL);
        mRequest.setHeader(HEADER_ID_IMG_HANDLE, IMAGE_HANDLE_INVALID);
        Operation op = makeOperation(mRequest, mOutputStream);
        int responseCode = mAvrcpBipObexServer.onGet(op);
        assertThat(responseCode).isEqualTo(ResponseCodes.OBEX_HTTP_PRECON_FAILED);
    }

    /**
     * Make sure a getImageThumbnail request with an invalidly formatted handle returns
     * OBEX_HTTP_BAD_REQUEST
     */
    @Test
    public void testGetLinkedThumbnailWithNullHandle() throws Exception {
        mRequest.setHeader(HeaderSet.TYPE, TYPE_GET_LINKED_THUMBNAIL);
        mRequest.setHeader(HEADER_ID_IMG_HANDLE, null);
        Operation op = makeOperation(mRequest, mOutputStream);
        int responseCode = mAvrcpBipObexServer.onGet(op);
        assertThat(responseCode).isEqualTo(ResponseCodes.OBEX_HTTP_BAD_REQUEST);
    }

    /**
     * Make sure a getImageProperties request with a valid handle returns a valie properties object
     */
    @Test
    public void testGetImagePropertiesWithValidHandle() throws Exception {
        mRequest.setHeader(HeaderSet.TYPE, TYPE_GET_IMAGE_PROPERTIES);
        mRequest.setHeader(HEADER_ID_IMG_HANDLE, IMAGE_HANDLE_1);
        setCoverArtAvailableAtHandle(IMAGE_HANDLE_1, mCoverArt);
        Operation op = makeOperation(mRequest, mOutputStream);
        int responseCode = mAvrcpBipObexServer.onGet(op);
        assertThat(responseCode).isEqualTo(ResponseCodes.OBEX_HTTP_OK);
    }

    /**
     * Make sure a getImageProperties request with a unstored handle returns OBEX_HTTP_NOT_FOUND
     */
    @Test
    public void testGetImagePropertiesWithValidUnstoredHandle() throws Exception {
        mRequest.setHeader(HeaderSet.TYPE, TYPE_GET_IMAGE_PROPERTIES);
        mRequest.setHeader(HEADER_ID_IMG_HANDLE, IMAGE_HANDLE_UNSTORED);
        Operation op = makeOperation(mRequest, mOutputStream);
        int responseCode = mAvrcpBipObexServer.onGet(op);
        assertThat(responseCode).isEqualTo(ResponseCodes.OBEX_HTTP_NOT_FOUND);
    }

    /**
     * Make sure a getImageProperties request with an invalidly formatted handle returns
     * OBEX_HTTP_BAD_REQUEST
     */
    @Test
    public void testGetImagePropertiesWithInvalidHandle() throws Exception {
        mRequest.setHeader(HeaderSet.TYPE, TYPE_GET_IMAGE_PROPERTIES);
        mRequest.setHeader(HEADER_ID_IMG_HANDLE, IMAGE_HANDLE_INVALID);
        Operation op = makeOperation(mRequest, mOutputStream);
        int responseCode = mAvrcpBipObexServer.onGet(op);
        assertThat(responseCode).isEqualTo(ResponseCodes.OBEX_HTTP_PRECON_FAILED);
    }

    /**
     * Make sure a getImageProperties request with an invalidly formatted handle returns
     * OBEX_HTTP_BAD_REQUEST
     */
    @Test
    public void testGetImagePropertiesWithNullHandle() throws Exception {
        mRequest.setHeader(HeaderSet.TYPE, TYPE_GET_IMAGE_PROPERTIES);
        mRequest.setHeader(HEADER_ID_IMG_HANDLE, null);
        Operation op = makeOperation(mRequest, mOutputStream);
        int responseCode = mAvrcpBipObexServer.onGet(op);
        assertThat(responseCode).isEqualTo(ResponseCodes.OBEX_HTTP_BAD_REQUEST);
    }

    /**
     * Make sure a GetImage request with a null descriptor returns a native image
     */
    @Test
    public void testGetImageWithValidHandleAndNullDescriptor() throws Exception {
        mRequest.setHeader(HeaderSet.TYPE, TYPE_GET_IMAGE);
        mRequest.setHeader(HEADER_ID_IMG_HANDLE, IMAGE_HANDLE_1);
        mRequest.setHeader(HEADER_ID_IMG_DESCRIPTOR, null);
        setCoverArtAvailableAtHandle(IMAGE_HANDLE_1, mCoverArt);
        Operation op = makeOperation(mRequest, mOutputStream);
        int responseCode = mAvrcpBipObexServer.onGet(op);
        assertThat(responseCode).isEqualTo(ResponseCodes.OBEX_HTTP_OK);
    }

    /**
     * Make sure a GetImage request with a valid descriptor returns an image
     */
    @Test
    public void testGetImageWithValidHandleAndValidDescriptor() throws Exception {
        mRequest.setHeader(HeaderSet.TYPE, TYPE_GET_IMAGE);
        mRequest.setHeader(HEADER_ID_IMG_HANDLE, IMAGE_HANDLE_1);
        mRequest.setHeader(HEADER_ID_IMG_DESCRIPTOR, makeDescriptor(BipEncoding.JPEG, 200, 200));
        setCoverArtAvailableAtHandle(IMAGE_HANDLE_1, mCoverArt);
        Operation op = makeOperation(mRequest, mOutputStream);
        int responseCode = mAvrcpBipObexServer.onGet(op);
        assertThat(responseCode).isEqualTo(ResponseCodes.OBEX_HTTP_OK);
    }

    /**
     * Make sure a GetImage request with a valid, but unsupported descriptor, returns NOT_ACCEPTABLE
     */
    @Test
    public void testGetImageWithValidHandleAndInvalidDescriptor() throws Exception {
        mRequest.setHeader(HeaderSet.TYPE, TYPE_GET_IMAGE);
        mRequest.setHeader(HEADER_ID_IMG_HANDLE, IMAGE_HANDLE_1);
        mRequest.setHeader(HEADER_ID_IMG_DESCRIPTOR,
                makeDescriptor(BipEncoding.WBMP /* No Android support, won't work */, 200, 200));
        setCoverArtAvailableAtHandle(IMAGE_HANDLE_1, mCoverArt);
        Operation op = makeOperation(mRequest, mOutputStream);
        int responseCode = mAvrcpBipObexServer.onGet(op);
        assertThat(responseCode).isEqualTo(ResponseCodes.OBEX_HTTP_NOT_ACCEPTABLE);
    }

    /**
     * Make sure a GetImage request with a unstored handle returns OBEX_HTTP_NOT_FOUND
     */
    @Test
    public void testGetImageWithValidUnstoredHandle() throws Exception {
        mRequest.setHeader(HeaderSet.TYPE, TYPE_GET_IMAGE);
        mRequest.setHeader(HEADER_ID_IMG_HANDLE, IMAGE_HANDLE_UNSTORED);
        mRequest.setHeader(HEADER_ID_IMG_DESCRIPTOR, makeDescriptor(BipEncoding.JPEG, 200, 200));
        Operation op = makeOperation(mRequest, mOutputStream);
        int responseCode = mAvrcpBipObexServer.onGet(op);
        assertThat(responseCode).isEqualTo(ResponseCodes.OBEX_HTTP_NOT_FOUND);
    }

    /**
     * Make sure a getImage request with an invalidly formatted handle returns OBEX_HTTP_BAD_REQUEST
     */
    @Test
    public void testGetImageWithInvalidHandle() throws Exception {
        mRequest.setHeader(HeaderSet.TYPE, TYPE_GET_IMAGE);
        mRequest.setHeader(HEADER_ID_IMG_HANDLE, IMAGE_HANDLE_INVALID);
        mRequest.setHeader(HEADER_ID_IMG_DESCRIPTOR, makeDescriptor(BipEncoding.JPEG, 200, 200));
        Operation op = makeOperation(mRequest, mOutputStream);
        int responseCode = mAvrcpBipObexServer.onGet(op);
        assertThat(responseCode).isEqualTo(ResponseCodes.OBEX_HTTP_PRECON_FAILED);
    }

    /**
     * Make sure a getImage request with a null handle returns OBEX_HTTP_BAD_REQUEST
     */
    @Test
    public void testGetImageWithNullHandle() throws Exception {
        mRequest.setHeader(HeaderSet.TYPE, TYPE_GET_IMAGE);
        mRequest.setHeader(HEADER_ID_IMG_HANDLE, null);
        mRequest.setHeader(HEADER_ID_IMG_DESCRIPTOR, makeDescriptor(BipEncoding.JPEG, 200, 200));
        Operation op = makeOperation(mRequest, mOutputStream);
        int responseCode = mAvrcpBipObexServer.onGet(op);
        assertThat(responseCode).isEqualTo(ResponseCodes.OBEX_HTTP_BAD_REQUEST);
    }

    /**
     * Make sure onPut is not a supported action
     */
    @Test
    public void testOnPut() {
        Operation op = null;
        int responseCode = mAvrcpBipObexServer.onPut(op);
        assertThat(responseCode).isEqualTo(ResponseCodes.OBEX_HTTP_NOT_IMPLEMENTED);
    }

    /**
     * Make sure onAbort is not a supported action
     */
    @Test
    public void testOnAbort() {
        HeaderSet request = null;
        HeaderSet reply = null;
        int responseCode = mAvrcpBipObexServer.onAbort(request, reply);
        assertThat(responseCode).isEqualTo(ResponseCodes.OBEX_HTTP_NOT_IMPLEMENTED);
    }

    /**
     * Make sure onSetPath is not a supported action
     */
    @Test
    public void testOnSetPath() {
        HeaderSet request = null;
        HeaderSet reply = null;
        boolean backup = false;
        boolean create = false;
        int responseCode = mAvrcpBipObexServer.onSetPath(request, reply, backup, create);
        assertThat(responseCode).isEqualTo(ResponseCodes.OBEX_HTTP_NOT_IMPLEMENTED);
    }
}
