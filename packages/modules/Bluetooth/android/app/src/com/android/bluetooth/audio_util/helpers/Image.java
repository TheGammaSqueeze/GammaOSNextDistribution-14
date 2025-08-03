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
package com.android.bluetooth.audio_util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;

/**
 * An object to represent an image from the Media Framework
 *
 * This object abstracts away the method used to get the bitmap and provides a way for us to
 * determine image equality in an application/folder/item agnostic way.
 */
public class Image {
    private static final String TAG = "Image";

    public static int SOURCE_NONE = 0;
    public static int SOURCE_URI = 1;
    public static int SOURCE_BITMAP = 2;

    private final Context mContext;

    private int mSource = SOURCE_NONE;
    private Bitmap mImage = null;

    // For use with other applications so they can conveniently assign the handle their storage
    // solution has picked for this image and pass this object on directly.
    private String mImageHandle = null;

    /**
     * Create an Image object from a MediaMetadata object
     */
    public Image(Context context, MediaMetadata metadata) {
        mContext = context;

        String uri_art = null;
        String uri_album_art = null;
        String uri_icon = null;
        Bitmap bmp_art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
        Bitmap bmp_album_art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        Bitmap bmp_icon = metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);

        if (mContext != null && Util.areUriImagesSupported(mContext)) {
            uri_art = metadata.getString(MediaMetadata.METADATA_KEY_ART_URI);
            uri_album_art = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI);
            uri_icon = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI);
        }

        if (bmp_art != null) {
            setImage(bmp_art);
        } else if (bmp_album_art != null) {
            setImage(bmp_album_art);
        } else if (bmp_icon != null) {
            setImage(bmp_icon);
        } else if (uri_art != null) {
            setImage(uri_art);
        } else if (uri_album_art != null) {
            setImage(uri_album_art);
        } else if (uri_icon != null) {
            setImage(uri_icon);
        }
    }

    /**
     * Create an image out of a bundle of MediaMetadata keyed values
     */
    public Image(Context context, Bundle bundle) {
        mContext = context;

        String uri_art = null;
        String uri_album_art = null;
        String uri_icon = null;
        Bitmap bmp_art = bundle.getParcelable(MediaMetadata.METADATA_KEY_ART);
        Bitmap bmp_album_art = bundle.getParcelable(MediaMetadata.METADATA_KEY_ALBUM_ART);
        Bitmap bmp_icon = bundle.getParcelable(MediaMetadata.METADATA_KEY_DISPLAY_ICON);

        if (mContext != null && Util.areUriImagesSupported(mContext)) {
            uri_art = bundle.getString(MediaMetadata.METADATA_KEY_ART_URI);
            uri_album_art = bundle.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI);
            uri_icon = bundle.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI);
        }

        if (bmp_art != null) {
            setImage(bmp_art);
        } else if (bmp_album_art != null) {
            setImage(bmp_album_art);
        } else if (bmp_icon != null) {
            setImage(bmp_icon);
        } else if (uri_art != null) {
            setImage(uri_art);
        } else if (uri_album_art != null) {
            setImage(uri_album_art);
        } else if (uri_icon != null) {
            setImage(uri_icon);
        }
    }

    /**
     * Create an Image object from a MediaDescription object
     */
    public Image(Context context, MediaDescription desc) {
        mContext = context;
        Uri uri = desc.getIconUri();
        Bitmap bitmap = desc.getIconBitmap();
        if (bitmap != null) {
            setImage(bitmap);
        } else if (uri != null) {
            setImage(uri);
        }
    }

    /**
     * Create an Image object from a raw image uri
     */
    public Image(Context context, Uri uri) {
        mContext = context;
        setImage(uri);
    }

    /**
     * Create an Image object from a raw image
     */
    public Image(Context context, Bitmap image) {
        mContext = context;
        setImage(image);
    }

    /**
     * Set the image by resolving a URI to a bitmap
     */
    private void setImage(String uriString) {
        if (uriString == null) return;
        Uri uri = Uri.parse(uriString);
        setImage(uri);
    }

    /**
     * Set the image by resolving a URI to a bitmap
     */
    private void setImage(Uri uri) {
        if (uri == null) return;
        Bitmap image = getImageFromUri(uri);
        if (image == null) return;
        setImage(image);
        setSource(SOURCE_URI);
    }

    /**
     * Set the image directly from a bitmap
     */
    private void setImage(Bitmap image) {
        if (image == null) return;
        mImage = image;
        setSource(SOURCE_BITMAP);
    }

    /**
     * Get the bitmap associated with this Image
     */
    public Bitmap getImage() {
        return mImage;
    }

    /**
     * Get an image Bitmap from a Uri
     *
     * Used to convert Uris into the images they represent
     *
     * @param uri A Uri pointing to an image
     * @return A Bitmap object representing the image at the given Uri
     */
    private Bitmap getImageFromUri(Uri uri) {
        if (uri == null) return null;
        Bitmap art = null;
        InputStream input = null;
        try {
            if (mContext == null) return null;
            input = mContext.getContentResolver().openInputStream(uri);
            art = BitmapFactory.decodeStream(input);
        } catch (Exception e) {
            Log.w("Failed to fetch image at uri=" + uri, e);
            art = null;
        }
        try {
            if (input != null) {
                input.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to close image file stream, exception=" + e);
        }
        return art;
    }

    /**
     * Get the source of the image, if known
     *
     * Images currently come from either raw bitmaps or a URI that points to a ContentProvider.
     * This allows us to set where it came from, largely used for debug purposes.
     */
    public int getSource() {
        return mSource;
    }

    /**
     * Set the source of the image.
     *
     * Images currently come from either raw bitmaps or a URI that points to a ContentProvider.
     * This allows us to set where it came from, largely used for debug purposes.
     */
    private void setSource(int source) {
        mSource = source;
    }

    /**
     * Assign a handle value from your storage solution to this image
     */
    public void setImageHandle(String handle) {
        mImageHandle = handle;
    }

    /**
     * Get the handle value associated with this image from your storage situation
     */
    public String getImageHandle() {
        return mImageHandle;
    }

    /**
     * Determine if two image objects are the same.
     */
    public static boolean sameAs(Image l, Image r) {
        if (l == null && r == null) return true;
        if (l == null || r == null) return false;
        final Bitmap bmp = l.getImage();
        if (bmp == null) return (r.getImage() == null);
        return bmp.sameAs(r.getImage());
    }

    /**
     * Get a string representation of the image and its metadata
     */
    @Override
    public String toString() {
        return "<Image source=" + mSource + ">";
    }
}
