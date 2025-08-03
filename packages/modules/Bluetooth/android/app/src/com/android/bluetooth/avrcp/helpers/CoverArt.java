/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.graphics.Bitmap;
import android.util.Log;

import com.android.bluetooth.audio_util.Image;
import com.android.bluetooth.avrcpcontroller.BipEncoding;
import com.android.bluetooth.avrcpcontroller.BipImageDescriptor;
import com.android.bluetooth.avrcpcontroller.BipImageFormat;
import com.android.bluetooth.avrcpcontroller.BipImageProperties;
import com.android.bluetooth.avrcpcontroller.BipPixel;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * An object to represent a piece of cover artwork/
 *
 * This object abstracts away the actual storage method and provides a means for others to
 * understand available formats and get the underlying image in a particular format.
 *
 * All return values are ready to use by a BIP server.
 */
public class CoverArt {
    private static final String TAG = "CoverArt";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    private static final BipPixel PIXEL_THUMBNAIL = BipPixel.createFixed(200, 200);

    private String mImageHandle = null;
    private Bitmap mImage = null;

    /**
     * Create a CoverArt object from an audio_util Image abstraction
     */
    CoverArt(Image image) {
        // Create a scaled version of the image for now, as consumers don't need
        // anything larger than this at the moment. Also makes each image gathered
        // the same dimensions for hashing purposes.
        mImage = Bitmap.createScaledBitmap(image.getImage(), 200, 200, false);
    }

    /**
     * Get the image handle that has been associated with this image.
     *
     * If this returns null then you will fail to generate image properties
     */
    public String getImageHandle() {
        return mImageHandle;
    }

    /**
     * Set the image handle that has been associated with this image.
     *
     * This is required to generate image properties
     */
    public void setImageHandle(String handle) {
        mImageHandle = handle;
    }

    /**
     * Covert a Bitmap to a byte array with an image format without lossy compression
     */
    private byte[] toByteArray(Bitmap bitmap) {
        if (bitmap == null) return null;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(
                    bitmap.getWidth() * bitmap.getHeight());
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, buffer);
        return buffer.toByteArray();
    }

    /**
     * Get a hash code of this CoverArt image
     */
    public String getImageHash() {
        byte[] image = toByteArray(mImage);
        if (image == null) return null;
        String hash = null;
        try {
            final byte[] digestBytes;
            final MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(/* Bitmap to input stream */ image);
            byte[] messageDigest = digest.digest();

            StringBuffer hexString = new StringBuffer();
            for (int i = 0; i < messageDigest.length; i++) {
                hexString.append(Integer.toHexString(0xFF & messageDigest[i]));
            }
            hash = hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Failed to hash bitmap", e);
        }
        return hash;
    }

    /**
     * Get the cover artwork image bytes in the native format
     */
    public byte[] getImage() {
        debug("GetImage(native)");
        if (mImage == null) return null;
        byte[] bytes = null;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        mImage.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
        bytes = outputStream.toByteArray();
        return bytes;
    }

    /**
     * Get the cover artwork image bytes in the given encoding and pixel size
     */
    public byte[] getImage(BipImageDescriptor descriptor) {
        debug("GetImage(descriptor=" + descriptor);
        if (mImage == null) return null;
        if (descriptor == null) return getImage();
        if (!isDescriptorValid(descriptor)) {
            error("Given format isn't available for this image");
            return null;
        }

        byte[] bytes = null;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        mImage.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
        bytes = outputStream.toByteArray();
        return bytes;
    }

    /**
     * Determine if a given image descriptor is valid
     */
    private boolean isDescriptorValid(BipImageDescriptor descriptor) {
        debug("isDescriptorValid(descriptor=" + descriptor + ")");
        if (descriptor == null) return false;

        BipEncoding encoding = descriptor.getEncoding();
        BipPixel pixel = descriptor.getPixel();

        if (encoding.getType() == BipEncoding.JPEG && PIXEL_THUMBNAIL.equals(pixel)) {
            return true;
        }
        return false;
    }

    /**
     * Get the cover artwork image bytes as a 200 x 200 JPEG thumbnail
     */
    public byte[] getThumbnail() {
        debug("GetImageThumbnail()");
        if (mImage == null) return null;
        byte[] bytes = null;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        mImage.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
        bytes = outputStream.toByteArray();
        return bytes;
    }

    /**
     * Get the set of image properties that the cover artwork can be turned into
     */
    public BipImageProperties getImageProperties() {
        debug("GetImageProperties()");
        if (mImage == null) {
            error("Can't associate properties with a null image");
            return null;
        }
        if (mImageHandle == null) {
            error("No handle has been associated with this image. Cannot build properties.");
            return null;
        }
        BipImageProperties.Builder builder = new BipImageProperties.Builder();
        BipEncoding encoding = new BipEncoding(BipEncoding.JPEG);
        BipPixel pixel = BipPixel.createFixed(200, 200);
        BipImageFormat format = BipImageFormat.createNative(encoding, pixel, -1);

        builder.setImageHandle(mImageHandle);
        builder.addNativeFormat(format);

        BipImageProperties properties = builder.build();
        return properties;
    }

    /**
     * Get the storage size of this image in bytes
     */
    public int size() {
        return mImage != null ? mImage.getAllocationByteCount() : 0;
    }

    @Override
    public String toString() {
        return "{handle=" + mImageHandle + ", size=" + size() + " }";
    }

    /**
     * Print a message to DEBUG if debug output is enabled
     */
    private void debug(String msg) {
        if (DEBUG) {
            Log.d(TAG, msg);
        }
    }

    /**
     * Print a message to ERROR
     */
    private void error(String msg) {
        Log.e(TAG, msg);
    }
}
