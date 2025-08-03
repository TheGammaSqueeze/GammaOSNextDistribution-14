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

package com.android.bluetooth.audio_util;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.net.Uri;
import android.os.Bundle;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.R;
import com.android.bluetooth.TestUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.InputStream;

@RunWith(AndroidJUnit4.class)
public class ImageTest {
    private Context mTargetContext;

    private @Mock Context mMockContext;
    private @Mock Resources mMockResources;
    private Resources mTestResources;
    private MockContentResolver mTestContentResolver;

    private static final String TEST_AUTHORITY = "com.android.bluetooth.avrcp.test";
    private static final Uri TEST_CONTENT_URI = new Uri.Builder()
            .scheme(ContentResolver.SCHEME_CONTENT)
            .authority(TEST_AUTHORITY)
            .build();

    private static final String IMAGE_HANDLE_1 = "0000001";
    private static final Uri IMAGE_URI_1 = TEST_CONTENT_URI.buildUpon()
            .appendQueryParameter("handle", IMAGE_HANDLE_1)
            .build();
    private static final String IMAGE_STRING_1 = IMAGE_URI_1.toString();

    private static final String IMAGE_HANDLE_SECURITY_ERROR = "sec_error";
    private static final Uri IMAGE_URI_SECURITY_ERROR = TEST_CONTENT_URI.buildUpon()
            .appendQueryParameter("handle", IMAGE_HANDLE_SECURITY_ERROR)
            .build();

    private Bitmap mTestBitmap = null;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mTargetContext = InstrumentationRegistry.getTargetContext();
        mTestResources = TestUtils.getTestApplicationResources(mTargetContext);

        mTestBitmap = loadImage(com.android.bluetooth.tests.R.raw.image_200_200);

        mTestContentResolver = new MockContentResolver(mTargetContext);
        mTestContentResolver.addProvider(TEST_AUTHORITY, new MockContentProvider() {
            @Override
            public AssetFileDescriptor openTypedAssetFile(Uri url, String mimeType, Bundle opts) {
                String handle = url.getQueryParameter("handle");
                if (IMAGE_URI_1.equals(url)) {
                    return mTestResources.openRawResourceFd(
                            com.android.bluetooth.tests.R.raw.image_200_200);
                } else if (IMAGE_URI_SECURITY_ERROR.equals(url)) {
                    throw new SecurityException();
                }
                return null;
            }
        });

        when(mMockContext.getContentResolver()).thenReturn(mTestContentResolver);
        when(mMockContext.getResources()).thenReturn(mMockResources);
        when(mMockResources.getBoolean(R.bool.avrcp_target_cover_art_uri_images)).thenReturn(true);
    }

    @After
    public void tearDown() throws Exception {
        mTestContentResolver = null;
        mTestBitmap = null;
        mTestResources = null;
        mTargetContext = null;
        mMockContext = null;
    }

    private Bitmap loadImage(int resId) {
        InputStream imageInputStream = mTestResources.openRawResource(resId);
        return BitmapFactory.decodeStream(imageInputStream);
    }

    private MediaMetadata getMediaMetadataWithoutArt() {
        MediaMetadata.Builder builder = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, "BT Test Song")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "BT Test Artist")
                .putString(MediaMetadata.METADATA_KEY_ALBUM, "BT Test Album")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, 5000L);
        return builder.build();
    }

    private MediaMetadata getMediaMetadataWithBitmap(String field, Bitmap image) {
        MediaMetadata.Builder builder = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, "BT Test Song")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "BT Test Artist")
                .putString(MediaMetadata.METADATA_KEY_ALBUM, "BT Test Album")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, 5000L)
                .putBitmap(field, image);
        return builder.build();
    }

    private MediaMetadata getMediaMetadataWithUri(String field, String uri) {
        MediaMetadata.Builder builder = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, "BT Test Song")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "BT Test Artist")
                .putString(MediaMetadata.METADATA_KEY_ALBUM, "BT Test Album")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, 5000L)
                .putString(field, uri);
        return builder.build();
    }

    private MediaDescription getMediaDescriptionWithoutArt() {
        MediaDescription.Builder builder = new MediaDescription.Builder()
                .setTitle("BT Test Song")
                .setDescription("BT Test Description");
        return builder.build();
    }

    private MediaDescription getMediaDescriptionWithBitmap(Bitmap image) {
        MediaDescription.Builder builder = new MediaDescription.Builder()
                .setTitle("BT Test Song")
                .setDescription("BT Test Description")
                .setIconBitmap(image);
        return builder.build();
    }

    private MediaDescription getMediaDescriptionWithUri(Uri uri) {
        MediaDescription.Builder builder = new MediaDescription.Builder()
                .setTitle("BT Test Song")
                .setDescription("BT Test Description")
                .setIconUri(uri);
        return builder.build();
    }

    private Bundle getBundleWithBitmap(String field, Bitmap image) {
        Bundle bundle = new Bundle();
        bundle.putParcelable(field, image);
        return bundle;
    }

    private Bundle getBundleWithUri(String field, String uri) {
        Bundle bundle = new Bundle();
        bundle.putString(field, uri);
        return bundle;
    }

    /**
     * Make sure you can create an Image from a MediaMetadata object that contains cover artwork
     * as an Art Bitmap
     */
    @Test
    public void testCreateImageFromMediaMetadataWithArt() {
        MediaMetadata metadata =
                getMediaMetadataWithBitmap(MediaMetadata.METADATA_KEY_ART, mTestBitmap);
        Image artwork = new Image(mMockContext, metadata);
        assertThat(mTestBitmap.sameAs(artwork.getImage())).isTrue();
        assertThat(artwork.getSource()).isEqualTo(Image.SOURCE_BITMAP);
        assertThat(artwork.getImageHandle()).isNull();
    }

    /**
     * Make sure you can create an Image from a MediaMetadata object that contains cover artwork
     * as an Album Art Bitmap
     */
    @Test
    public void testCreateImageFromMediaMetadataWithAlbumArt() {
        MediaMetadata metadata =
                getMediaMetadataWithBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, mTestBitmap);
        Image artwork = new Image(mMockContext, metadata);
        assertThat(mTestBitmap.sameAs(artwork.getImage())).isTrue();
        assertThat(artwork.getSource()).isEqualTo(Image.SOURCE_BITMAP);
        assertThat(artwork.getImageHandle()).isNull();
    }

    /**
     * Make sure you can create an Image from a MediaMetadata object that contains cover artwork
     * as a Display Icon Bitmap
     */
    @Test
    public void testCreateImageFromMediaMetadataWithDisplayIcon() {
        MediaMetadata metadata =
                getMediaMetadataWithBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, mTestBitmap);
        Image artwork = new Image(mMockContext, metadata);
        assertThat(mTestBitmap.sameAs(artwork.getImage())).isTrue();
        assertThat(artwork.getSource()).isEqualTo(Image.SOURCE_BITMAP);
        assertThat(artwork.getImageHandle()).isNull();
    }

    /**
     * Make sure you can create an Image from a MediaMetadata object that contains cover artwork
     * as an Art Uri
     */
    @Test
    public void testCreateImageFromMediaMetadataWithArtUri() {
        MediaMetadata metadata =
                getMediaMetadataWithUri(MediaMetadata.METADATA_KEY_ART_URI, IMAGE_STRING_1);
        Image artwork = new Image(mMockContext, metadata);
        assertThat(mTestBitmap.sameAs(artwork.getImage())).isTrue();
        assertThat(artwork.getSource()).isEqualTo(Image.SOURCE_URI);
        assertThat(artwork.getImageHandle()).isNull();
    }

    /**
     * Make sure you can create an Image from a MediaMetadata object that contains cover artwork
     * as an Album Art Uri
     */
    @Test
    public void testCreateImageFromMediaMetadataWithAlbumArtUri() {
        MediaMetadata metadata =
                getMediaMetadataWithUri(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, IMAGE_STRING_1);
        Image artwork = new Image(mMockContext, metadata);
        assertThat(mTestBitmap.sameAs(artwork.getImage())).isTrue();
        assertThat(artwork.getSource()).isEqualTo(Image.SOURCE_URI);
        assertThat(artwork.getImageHandle()).isNull();
    }

    /**
     * Make sure you can create an Image from a MediaMetadata object that contains cover artwork
     * as a Display Icon Uri
     */
    @Test
    public void testCreateImageFromMediaMetadataWithDisplayIconUri() {
        MediaMetadata metadata =
                getMediaMetadataWithUri(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,
                        IMAGE_STRING_1);
        Image artwork = new Image(mMockContext, metadata);
        assertThat(mTestBitmap.sameAs(artwork.getImage())).isTrue();
        assertThat(artwork.getSource()).isEqualTo(Image.SOURCE_URI);
        assertThat(artwork.getImageHandle()).isNull();
    }

    /**
     * Make sure no image is set when you create an Image from a MediaMetadata object that contains
     * cover artwork as an Art Uri and Bluetooth is not configured to support URI images.
     */
    @Test
    public void testCreateImageFromMediaMetadataWithArtUriDisabled() {
        when(mMockResources.getBoolean(R.bool.avrcp_target_cover_art_uri_images))
                .thenReturn(false);
        MediaMetadata metadata =
                getMediaMetadataWithUri(MediaMetadata.METADATA_KEY_ART_URI, IMAGE_STRING_1);
        Image artwork = new Image(mMockContext, metadata);
        assertThat(artwork.getImage()).isNull();
        assertThat(artwork.getSource()).isEqualTo(Image.SOURCE_NONE);
        assertThat(artwork.getImageHandle()).isNull();
    }

    /**
     * Make sure no image is set when you create an Image from a MediaMetadata object that contains
     * cover artwork as an Album Art Uri and Bluetooth is not configured to support URI images.
     */
    @Test
    public void testCreateImageFromMediaMetadataWithAlbumArtUriDisabled() {
        when(mMockResources.getBoolean(R.bool.avrcp_target_cover_art_uri_images))
                .thenReturn(false);
        MediaMetadata metadata =
                getMediaMetadataWithUri(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, IMAGE_STRING_1);
        Image artwork = new Image(mMockContext, metadata);
        assertThat(artwork.getImage()).isNull();
        assertThat(artwork.getSource()).isEqualTo(Image.SOURCE_NONE);
        assertThat(artwork.getImageHandle()).isNull();
    }

    /**
     * Make sure no image is set when you create an Image from a MediaMetadata object that contains
     * cover artwork as a Display Icon Uri and Bluetooth is not configured to support URI images.
     */
    @Test
    public void testCreateImageFromMediaMetadataWithDisplayIconUriDisabled() {
        when(mMockResources.getBoolean(R.bool.avrcp_target_cover_art_uri_images))
                .thenReturn(false);
        MediaMetadata metadata =
                getMediaMetadataWithUri(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,
                        IMAGE_STRING_1);
        Image artwork = new Image(mMockContext, metadata);
        assertThat(artwork.getImage()).isNull();
        assertThat(artwork.getSource()).isEqualTo(Image.SOURCE_NONE);
        assertThat(artwork.getImageHandle()).isNull();
    }

    /**
     * Make sure you can create an Image from a MediaMetadata object that contains no cover artwork
     */
    @Test
    public void testCreateImageFromMediaMetadataWithoutArtwork() {
        MediaMetadata metadata = getMediaMetadataWithoutArt();
        Image artwork = new Image(mMockContext, metadata);
        assertThat(artwork.getImage()).isNull();
        assertThat(artwork.getSource()).isEqualTo(Image.SOURCE_NONE);
        assertThat(artwork.getImageHandle()).isNull();
    }

    /**
     * Make sure you can create an image from a MediaDescription object that contains cover artwork
     * as a Uri
     */
    @Test
    public void testCreateImageFromMediaDescriptionWithImage() {
        MediaDescription description = getMediaDescriptionWithBitmap(mTestBitmap);
        Image artwork = new Image(mMockContext, description);
        assertThat(mTestBitmap.sameAs(artwork.getImage())).isTrue();
        assertThat(artwork.getSource()).isEqualTo(Image.SOURCE_BITMAP);
        assertThat(artwork.getImageHandle()).isNull();
    }

    /**
     * Make sure you can create an image from a MediaDescription object that contains cover artwork
     * as a Bitmap Image
     */
    @Test
    public void testCreateImageFromMediaDescriptionWithUri() {
        MediaDescription description = getMediaDescriptionWithUri(IMAGE_URI_1);
        Image artwork = new Image(mMockContext, description);
        assertThat(mTestBitmap.sameAs(artwork.getImage())).isTrue();
        assertThat(artwork.getSource()).isEqualTo(Image.SOURCE_URI);
        assertThat(artwork.getImageHandle()).isNull();
    }

    /**
     * Make sure you can create an image from a MediaDescription object that contains no cover
     * artwork
     */
    @Test
    public void testCreateImageFromMediaDescriptionWithoutArtwork() {
        MediaDescription description = getMediaDescriptionWithoutArt();
        Image artwork = new Image(mMockContext, description);
        assertThat(artwork.getImage()).isNull();
        assertThat(artwork.getSource()).isEqualTo(Image.SOURCE_NONE);
        assertThat(artwork.getImageHandle()).isNull();
    }

    /**
     * Make sure you can create an Image from a Bundle that contains cover artwork as an Art Bitmap
     */
    @Test
    public void testCreateImageFromBundleWithArt() {
        Bundle bundle = getBundleWithBitmap(MediaMetadata.METADATA_KEY_ART, mTestBitmap);
        Image artwork = new Image(mMockContext, bundle);
        assertThat(mTestBitmap.sameAs(artwork.getImage())).isTrue();
        assertThat(artwork.getSource()).isEqualTo(Image.SOURCE_BITMAP);
        assertThat(artwork.getImageHandle()).isNull();
    }

    /**
     * Make sure you can create an Image from a Bundle that contains cover artwork as an Album Art
     * Bitmap
     */
    @Test
    public void testCreateImageFromBundleWithAlbumArt() {
        Bundle bundle = getBundleWithBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, mTestBitmap);
        Image artwork = new Image(mMockContext, bundle);
        assertThat(mTestBitmap.sameAs(artwork.getImage())).isTrue();
        assertThat(artwork.getSource()).isEqualTo(Image.SOURCE_BITMAP);
        assertThat(artwork.getImageHandle()).isNull();
    }

    /**
     * Make sure you can create an Image from a Bundle that contains cover artwork as a Display
     * Icon Bitmap
     */
    @Test
    public void testCreateImageFromBundleWithDisplayIcon() {
        Bundle bundle = getBundleWithBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, mTestBitmap);
        Image artwork = new Image(mMockContext, bundle);
        assertThat(mTestBitmap.sameAs(artwork.getImage())).isTrue();
        assertThat(artwork.getSource()).isEqualTo(Image.SOURCE_BITMAP);
        assertThat(artwork.getImageHandle()).isNull();
    }

    /**
     * Make sure you can create an Image from a Bundle that contains cover artwork as an Art Uri
     */
    @Test
    public void testCreateImageFromBundleWithArtUri() {
        Bundle bundle = getBundleWithUri(MediaMetadata.METADATA_KEY_ART_URI, IMAGE_STRING_1);
        Image artwork = new Image(mMockContext, bundle);
        assertThat(mTestBitmap.sameAs(artwork.getImage())).isTrue();
        assertThat(artwork.getSource()).isEqualTo(Image.SOURCE_URI);
        assertThat(artwork.getImageHandle()).isNull();
    }

    /**
     * Make sure you can create an Image from a Bundle that contains cover artwork as an Album Art
     * Uri
     */
    @Test
    public void testCreateImageFromBundleWithAlbumArtUri() {
        Bundle bundle = getBundleWithUri(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, IMAGE_STRING_1);
        Image artwork = new Image(mMockContext, bundle);
        assertThat(mTestBitmap.sameAs(artwork.getImage())).isTrue();
        assertThat(artwork.getSource()).isEqualTo(Image.SOURCE_URI);
        assertThat(artwork.getImageHandle()).isNull();
    }

    /**
     * Make sure you can create an Image from a Bundle that contains cover artwork as a Display
     * Icon Uri
     */
    @Test
    public void testCreateImageFromBundleWithDisplayIconUri() {
        Bundle bundle =
                getBundleWithUri(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI, IMAGE_STRING_1);
        Image artwork = new Image(mMockContext, bundle);
        assertThat(mTestBitmap.sameAs(artwork.getImage())).isTrue();
        assertThat(artwork.getSource()).isEqualTo(Image.SOURCE_URI);
        assertThat(artwork.getImageHandle()).isNull();
    }

    /**
     * Make sure no image is set when you create an Image from a Bundle that contains cover artwork
     * as an Art Uri and Bluetooth is not configured to support URI images.
     */
    @Test
    public void testCreateImageFromBundleWithArtUriDisabled() {
        when(mMockResources.getBoolean(R.bool.avrcp_target_cover_art_uri_images))
                .thenReturn(false);
        Bundle bundle = getBundleWithUri(MediaMetadata.METADATA_KEY_ART_URI, IMAGE_STRING_1);
        Image artwork = new Image(mMockContext, bundle);
        assertThat(artwork.getImage()).isNull();
        assertThat(artwork.getSource()).isEqualTo(Image.SOURCE_NONE);
        assertThat(artwork.getImageHandle()).isNull();
    }

    /**
     * Make sure no image is set when you create an Image from a Bundle that contains cover artwork
     * as an Album Art Uri and Bluetooth is not configured to support URI images.
     */
    @Test
    public void testCreateImageFromBundleWithAlbumArtUriDisabled() {
        when(mMockResources.getBoolean(R.bool.avrcp_target_cover_art_uri_images))
                .thenReturn(false);
        Bundle bundle = getBundleWithUri(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, IMAGE_STRING_1);
        Image artwork = new Image(mMockContext, bundle);
        assertThat(artwork.getImage()).isNull();
        assertThat(artwork.getSource()).isEqualTo(Image.SOURCE_NONE);
        assertThat(artwork.getImageHandle()).isNull();
    }

    /**
     * Make sure no image is set when you create an Image from a Bundle that contains cover artwork
     * as a Display Icon Uri and Bluetooth is not configured to support URI images.
     */
    @Test
    public void testCreateImageFromBundleWithDisplayIconUriDisabled() {
        when(mMockResources.getBoolean(R.bool.avrcp_target_cover_art_uri_images))
                .thenReturn(false);
        Bundle bundle =
                getBundleWithUri(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI, IMAGE_STRING_1);
        Image artwork = new Image(mMockContext, bundle);
        assertThat(artwork.getImage()).isNull();
        assertThat(artwork.getSource()).isEqualTo(Image.SOURCE_NONE);
        assertThat(artwork.getImageHandle()).isNull();
    }

    /**
     * Make sure you can create an Image from a Bundle that contains no cover artwork
     */
    @Test
    public void testCreateImageFromBundleWithoutArtwork() {
        Bundle bundle = new Bundle();
        Image artwork = new Image(mMockContext, bundle);
        assertThat(artwork.getImage()).isNull();
        assertThat(artwork.getSource()).isEqualTo(Image.SOURCE_NONE);
        assertThat(artwork.getImageHandle()).isNull();
    }

    /**
     * Make sure you can create an image from a simple Uri
     */
    @Test
    public void testCreateImageFromUri() {
        Image artwork = new Image(mMockContext, IMAGE_URI_1);
        assertThat(mTestBitmap.sameAs(artwork.getImage())).isTrue();
        assertThat(artwork.getSource()).isEqualTo(Image.SOURCE_URI);
        assertThat(artwork.getImageHandle()).isNull();
    }

    /**
     * Make sure you can create an image from a simple Bitmap Image
     */
    @Test
    public void testCreateImageFromBitmap() {
        Image artwork = new Image(mMockContext, mTestBitmap);
        assertThat(mTestBitmap.sameAs(artwork.getImage())).isTrue();
        assertThat(artwork.getSource()).isEqualTo(Image.SOURCE_BITMAP);
        assertThat(artwork.getImageHandle()).isNull();
    }

    /**
     * Make sure you can get the image handle associated with this object when there is none set
     */
    @Test
    public void testGetImageHandleWithEmptyHandle() {
        Image artwork = new Image(mMockContext, mTestBitmap);
        assertThat(artwork.getImageHandle()).isNull();
    }

    /**
     * Make sure you can get and set the image handle associated with this object
     */
    @Test
    public void testSetAndGetImageHandle() {
        Image artwork = new Image(mMockContext, mTestBitmap);
        artwork.setImageHandle(IMAGE_HANDLE_1);
        assertThat(artwork.getImageHandle()).isEqualTo(IMAGE_HANDLE_1);
    }

    /**
     * Make sure image URI resolution with erroneous resources doesn't crash and results in a null
     * image.
     */
    @Test
    public void testLoadImageFromUriWithSecurityException() {
        Image artwork = new Image(mMockContext, IMAGE_URI_SECURITY_ERROR);
        assertThat(artwork.getImageHandle()).isNull();
        assertThat(artwork.getSource()).isEqualTo(Image.SOURCE_NONE);
        assertThat(artwork.getImage()).isNull();
    }

    /**
     * Make sure you can get a string representation of this Image
     */
    @Test
    public void testToString() {
        Image artwork = new Image(mMockContext, mTestBitmap);
        assertThat(artwork.toString()).isNotNull();
    }
}
