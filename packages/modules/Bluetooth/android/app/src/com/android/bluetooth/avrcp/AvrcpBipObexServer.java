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

import android.util.Log;

import com.android.bluetooth.avrcpcontroller.BipImageDescriptor;
import com.android.bluetooth.avrcpcontroller.BipImageProperties;
import com.android.bluetooth.avrcpcontroller.ParseException;
import com.android.obex.HeaderSet;
import com.android.obex.Operation;
import com.android.obex.ResponseCodes;
import com.android.obex.ServerRequestHandler;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * A class responsible for handling requests from a specific client connection
 */
public class AvrcpBipObexServer extends ServerRequestHandler {
    private static final String TAG = "AvrcpBipObexServer";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private final AvrcpCoverArtService mAvrcpCoverArtService;

    // AVRCP Controller BIP Image Initiator/Cover Art UUID - AVRCP 1.6 Section 5.14.2.1
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

    private static final String TYPE_GET_LINKED_THUMBNAIL = "x-bt/img-thm";
    private static final String TYPE_GET_IMAGE_PROPERTIES = "x-bt/img-properties";
    private static final String TYPE_GET_IMAGE = "x-bt/img-img";

    private static final byte HEADER_ID_IMG_HANDLE = 0x30;
    private static final byte HEADER_ID_IMG_DESCRIPTOR = 0x71;

    private final Callback mCallback;

    /**
     * A set of callbacks to notify the creator of important AVRCP BIP events.
     */
    public interface Callback {
        /**
         * Receive a notification when this server session connects to a device
         */
        void onConnected();

        /**
         * Receive a notification when this server session disconnects with a device
         */
        void onDisconnected();
         /**
          * Receive a notification when this server session closes a connection with a device
          */
        void onClose();
    }

    public AvrcpBipObexServer(AvrcpCoverArtService service, Callback callback) {
        super();
        mAvrcpCoverArtService = service;
        mCallback = callback;
    }

    @Override
    public int onConnect(final HeaderSet request, HeaderSet reply) {
        debug("onConnect");
        try {
            byte[] uuid = (byte[]) request.getHeader(HeaderSet.TARGET);
            debug("onConnect - uuid=" + Arrays.toString(uuid));
            if (!Arrays.equals(uuid, BLUETOOTH_UUID_AVRCP_COVER_ART)) {
                warn("onConnect - uuid didn't match. Not Acceptable");
                return ResponseCodes.OBEX_HTTP_NOT_ACCEPTABLE;
            }
            // ...
        } catch (IOException e) {
            warn("onConnect - Something bad happened");
            return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
        }

        reply.setHeader(HeaderSet.WHO, BLUETOOTH_UUID_AVRCP_COVER_ART);
        debug("onConnect - Successful");
        if (mCallback != null) {
            mCallback.onConnected();
        }
        return ResponseCodes.OBEX_HTTP_OK;
    }

    @Override
    public void onDisconnect(final HeaderSet request, HeaderSet reply) {
        debug("onDisconnect");
        if (mCallback != null) {
            mCallback.onDisconnected();
        }
    }

    @Override
    public int onGet(final Operation op) {
        debug("onGet");
        try {
            HeaderSet request = op.getReceivedHeader();
            if (request == null) return ResponseCodes.OBEX_HTTP_BAD_REQUEST;

            // Route the request to the proper response handler
            String type = (String) request.getHeader(HeaderSet.TYPE);
            if (TYPE_GET_LINKED_THUMBNAIL.equals(type)) {
                return handleGetImageThumbnail(op);
            } else if (TYPE_GET_IMAGE_PROPERTIES.equals(type)) {
                return handleGetImageProperties(op);
            } else if (TYPE_GET_IMAGE.equals(type)) {
                return handleGetImage(op);
            } else {
                warn("Received unknown type '" + type + "'");
                return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
            }
        } catch (IllegalArgumentException e) {
            return ResponseCodes.OBEX_HTTP_PRECON_FAILED;
        } catch (ParseException e) {
            return ResponseCodes.OBEX_HTTP_PRECON_FAILED;
        } catch (Exception e) {
            return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        }
    }

    @Override
    public int onPut(final Operation op) {
        return ResponseCodes.OBEX_HTTP_NOT_IMPLEMENTED;
    }

    @Override
    public int onAbort(final HeaderSet request, HeaderSet reply) {
        return ResponseCodes.OBEX_HTTP_NOT_IMPLEMENTED;
    }

    @Override
    public int onSetPath(final HeaderSet request, HeaderSet reply, final boolean backup,
            final boolean create) {
        return ResponseCodes.OBEX_HTTP_NOT_IMPLEMENTED;
    }

    @Override
    public void onClose() {
        debug("Connection closed");
        if (mCallback != null) {
            mCallback.onClose();
        }
    }

    /**
     * Determine if a given image handle is valid in format
     *
     * An image handle a 9 character string of numbers 0-9 only. Anything else is invalid. This is
     * defined in section 4.4.4 (Image Handles) of the BIP specification, which is inherited by the
     * AVRCP specification.
     *
     * @return True if the image handle is valid, false otherwise.
     */
    private boolean isImageHandleValid(String handle) {
        if (handle == null || handle.length() != 7) return false;
        for (int i = 0; i < 7; i++) {
            char c = handle.charAt(i);
            if (!Character.isDigit(c)) return false;
        }
        return true;
    }

    private int handleGetImageThumbnail(Operation op)throws IOException  {
        HeaderSet request = op.getReceivedHeader();
        String imageHandle = (String) request.getHeader(HEADER_ID_IMG_HANDLE);

        debug("Received GetImageThumbnail(handle='" + imageHandle + "')");

        if (imageHandle == null) {
            warn("Received GetImageThumbnail without an image handle");
            return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        }

        if (!isImageHandleValid(imageHandle)) {
            debug("Received GetImageThumbnail with an invalid image handle");
            return ResponseCodes.OBEX_HTTP_PRECON_FAILED;
        }

        CoverArt image = mAvrcpCoverArtService.getImage(imageHandle);
        if (image == null) {
            warn("No image stored at handle '" + imageHandle + "'");
            return ResponseCodes.OBEX_HTTP_NOT_FOUND;
        }

        byte[] thumbnail = image.getThumbnail();
        if (thumbnail == null) {
            warn("Failed to serialize image");
            return ResponseCodes.OBEX_HTTP_NOT_FOUND;
        }

        HeaderSet replyHeaders = new HeaderSet();
        return sendResponse(op, replyHeaders, thumbnail);
    }

    private int handleGetImageProperties(Operation op) throws IOException {
        HeaderSet request = op.getReceivedHeader();
        String imageHandle = (String) request.getHeader(HEADER_ID_IMG_HANDLE);

        debug("Received GetImageProperties(handle='" + imageHandle + "')");

        if (imageHandle == null) {
            warn("Received GetImageProperties without an image handle");
            return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        }

        if (!isImageHandleValid(imageHandle)) {
            debug("Received GetImageProperties with an invalid image handle");
            return ResponseCodes.OBEX_HTTP_PRECON_FAILED;
        }

        CoverArt image = mAvrcpCoverArtService.getImage(imageHandle);
        if (image == null) {
            warn("No image stored at handle '" + imageHandle + "'");
            return ResponseCodes.OBEX_HTTP_NOT_FOUND;
        }
        BipImageProperties properties = image.getImageProperties();
        if (properties == null) {
            warn("Failed to get properties for known image");
            return ResponseCodes.OBEX_HTTP_NOT_FOUND;
        }

        byte[] propertiesBytes = properties.serialize();
        if (propertiesBytes == null) {
            debug("Failed to serialize properties for image");
            return ResponseCodes.OBEX_HTTP_NOT_FOUND;
        }

        debug("Sending image properties: " + properties);
        HeaderSet replyHeaders = new HeaderSet();
        return sendResponse(op, replyHeaders, propertiesBytes);
    }

    private int handleGetImage(Operation op) throws IOException {
        HeaderSet request = op.getReceivedHeader();
        String imageHandle = (String) request.getHeader(HEADER_ID_IMG_HANDLE);
        byte[] descriptorBytes = (byte[]) request.getHeader(HEADER_ID_IMG_DESCRIPTOR);
        BipImageDescriptor descriptor = null;

        if (descriptorBytes != null) {
            descriptor = new BipImageDescriptor(new ByteArrayInputStream(descriptorBytes));
        }

        debug("Received GetImage(handle='" + imageHandle + "', descriptor='" + descriptor + "')");

        if (imageHandle == null) {
            warn("Received GetImage without an image handle");
            return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        }

        if (!isImageHandleValid(imageHandle)) {
            debug("Received GetImage with an invalid image handle");
            return ResponseCodes.OBEX_HTTP_PRECON_FAILED;
        }

        CoverArt image = mAvrcpCoverArtService.getImage(imageHandle);
        if (image == null) {
            warn("No image stored at handle '" + imageHandle + "'");
            return ResponseCodes.OBEX_HTTP_NOT_FOUND;
        }

        byte[] imageBytes = null;
        if (descriptor == null) {
            debug("Received GetImage without an image descriptor. Returning native format");
            imageBytes = image.getImage();
        } else {
            imageBytes = image.getImage(descriptor);
        }

        if (imageBytes == null) {
            warn("Failed to serialize image with given format");
            return ResponseCodes.OBEX_HTTP_NOT_ACCEPTABLE; // BIP Section 5.3 unsupported format
        }

        debug("Sending image");
        HeaderSet replyHeaders = new HeaderSet();
        replyHeaders.setHeader(HeaderSet.LENGTH, null); // Section 4.5.8, Required, null is fine
        return sendResponse(op, replyHeaders, imageBytes);
    }

    /**
     * Send a response to the given operation using the given headers and bytes.
     */
    private int sendResponse(Operation op, HeaderSet replyHeaders, byte[] bytes) {
        if (op != null && bytes != null && replyHeaders != null) {
            OutputStream outStream = null;
            int maxChunkSize = 0;
            int bytesToWrite = 0;
            int bytesWritten = 0;
            try {
                op.sendHeaders(replyHeaders); // Do this before getting chunk size
                maxChunkSize = op.getMaxPacketSize();
                outStream = op.openOutputStream();
                while (bytesWritten < bytes.length) {
                    bytesToWrite = Math.min(maxChunkSize, bytes.length - bytesWritten);
                    outStream.write(bytes, bytesWritten, bytesToWrite);
                    bytesWritten += bytesToWrite;
                }
            } catch (IOException e) {
                warn("An exception occurred while writing response, e=" + e);
            } finally {
                // Make sure we close
                if (outStream != null) {
                    try {
                        outStream.close();
                    } catch (IOException e) { }
                }
            }
            // If we didn't write everything then send the error code
            if (bytesWritten != bytes.length) {
                warn("Failed to write entire response");
                return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
            }
            // Otherwise, success!
            return ResponseCodes.OBEX_HTTP_OK;
        }
        // If had no header or no body to send then assume we didn't find anything at all
        return ResponseCodes.OBEX_HTTP_NOT_FOUND;
    }

    private void warn(String msg) {
        Log.w(TAG, msg);
    }

    private void debug(String msg) {
        if (DEBUG) {
            Log.d(TAG, msg);
        }
    }
}
