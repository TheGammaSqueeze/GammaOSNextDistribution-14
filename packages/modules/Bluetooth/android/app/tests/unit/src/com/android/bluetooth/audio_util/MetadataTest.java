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
import android.media.browse.MediaBrowser.MediaItem;
import android.media.session.MediaSession.QueueItem;
import android.net.Uri;
import android.os.Bundle;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.R;
import com.android.bluetooth.TestUtils;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.InputStream;

@RunWith(AndroidJUnit4.class)
public class MetadataTest {
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
    private static final Image DEFAULT_IMAGE = null;

    private static final String SONG_MEDIA_ID = "abc123";
    private static final String SONG_TITLE = "BT Test Song";
    private static final String SONG_ARTIST = "BT Test Artist";
    private static final String SONG_ALBUM = "BT Test Album";
    private static final String SONG_TRACK_NUM = "12";
    private static final String SONG_NUM_TRACKS = "15";
    private static final String SONG_GENRE = "BT Music";
    private static final String SONG_DURATION = "5000";
    private Image mSongImage = null; /* to be set to Image(mTestBitmap) once context is set */

    private Bitmap mTestBitmap = null;
    private Bitmap mTestBitmap2 = null;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mTargetContext = InstrumentationRegistry.getTargetContext();
        mTestResources = TestUtils.getTestApplicationResources(mTargetContext);

        mTestBitmap = loadImage(com.android.bluetooth.tests.R.raw.image_200_200);
        mTestBitmap2 = loadImage(com.android.bluetooth.tests.R.raw.image_200_200_blue);

        mTestContentResolver = new MockContentResolver(mTargetContext);
        mTestContentResolver.addProvider(TEST_AUTHORITY, new MockContentProvider() {
            @Override
            public AssetFileDescriptor openTypedAssetFile(Uri url, String mimeType, Bundle opts) {
                String handle = url.getQueryParameter("handle");
                if (IMAGE_URI_1.equals(url)) {
                    return mTestResources.openRawResourceFd(
                            com.android.bluetooth.tests.R.raw.image_200_200);
                }
                return null;
            }
        });

        when(mMockContext.getContentResolver()).thenReturn(mTestContentResolver);
        when(mMockResources.getBoolean(R.bool.avrcp_target_cover_art_uri_images)).thenReturn(true);
        when(mMockContext.getResources()).thenReturn(mMockResources);

        mSongImage = new Image(mMockContext, mTestBitmap);
    }

    @After
    public void tearDown() throws Exception {
        mSongImage = null;
        mTestContentResolver = null;
        mTestBitmap = null;
        mTestBitmap2 = null;
        mTestResources = null;
        mTargetContext = null;
        mMockContext = null;
    }

    private Bitmap loadImage(int resId) {
        InputStream imageInputStream = mTestResources.openRawResource(resId);
        return BitmapFactory.decodeStream(imageInputStream);
    }

    private MediaMetadata getMediaMetadata() {
        MediaMetadata.Builder builder = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, SONG_MEDIA_ID)
                .putString(MediaMetadata.METADATA_KEY_TITLE, SONG_TITLE)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, SONG_ARTIST)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, SONG_ALBUM)
                .putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, Long.parseLong(SONG_TRACK_NUM))
                .putLong(MediaMetadata.METADATA_KEY_NUM_TRACKS, Long.parseLong(SONG_NUM_TRACKS))
                .putString(MediaMetadata.METADATA_KEY_GENRE, SONG_GENRE)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, Long.parseLong(SONG_DURATION));
        return builder.build();
    }

    private MediaMetadata getMediaMetadataWithBitmap(String field, Bitmap image) {
        MediaMetadata.Builder builder = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, SONG_MEDIA_ID)
                .putString(MediaMetadata.METADATA_KEY_TITLE, SONG_TITLE)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, SONG_ARTIST)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, SONG_ALBUM)
                .putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, Long.parseLong(SONG_TRACK_NUM))
                .putLong(MediaMetadata.METADATA_KEY_NUM_TRACKS, Long.parseLong(SONG_NUM_TRACKS))
                .putString(MediaMetadata.METADATA_KEY_GENRE, SONG_GENRE)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, Long.parseLong(SONG_DURATION))
                .putBitmap(field, image);
        return builder.build();
    }

    private MediaMetadata getMediaMetadataWithUri(String field, Uri uri) {
        MediaMetadata.Builder builder = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, SONG_MEDIA_ID)
                .putString(MediaMetadata.METADATA_KEY_TITLE, SONG_TITLE)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, SONG_ARTIST)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, SONG_ALBUM)
                .putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, Long.parseLong(SONG_TRACK_NUM))
                .putLong(MediaMetadata.METADATA_KEY_NUM_TRACKS, Long.parseLong(SONG_NUM_TRACKS))
                .putString(MediaMetadata.METADATA_KEY_GENRE, SONG_GENRE)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, Long.parseLong(SONG_DURATION))
                .putString(field, uri.toString());
        return builder.build();
    }

    private MediaDescription getMediaDescription(Bitmap bitmap, Uri uri, Bundle extras) {
        MediaDescription.Builder builder = new MediaDescription.Builder()
                .setMediaId(SONG_MEDIA_ID)
                .setTitle(SONG_TITLE)
                .setSubtitle(SONG_ARTIST)
                .setDescription(SONG_ALBUM);
        if (bitmap != null) {
            builder.setIconBitmap(bitmap);
        }
        if (uri != null) {
            builder.setIconUri(uri);
        }
        if (extras != null) {
            builder.setExtras(extras);
        }
        return builder.build();
    }

    private MediaItem getMediaItem(MediaDescription description) {
        return new MediaItem(description, 0 /* not browsable/playable */);
    }

    private QueueItem getQueueItem(MediaDescription description) {
        return new QueueItem(description, 1 /* queue ID */);
    }

    private Bundle getBundle() {
        Bundle bundle = new Bundle();
        bundle.putString(MediaMetadata.METADATA_KEY_MEDIA_ID, SONG_MEDIA_ID);
        bundle.putString(MediaMetadata.METADATA_KEY_TITLE, SONG_TITLE);
        bundle.putString(MediaMetadata.METADATA_KEY_ARTIST, SONG_ARTIST);
        bundle.putString(MediaMetadata.METADATA_KEY_ALBUM, SONG_ALBUM);
        bundle.putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, Long.parseLong(SONG_TRACK_NUM));
        bundle.putLong(MediaMetadata.METADATA_KEY_NUM_TRACKS, Long.parseLong(SONG_NUM_TRACKS));
        bundle.putString(MediaMetadata.METADATA_KEY_GENRE, SONG_GENRE);
        bundle.putLong(MediaMetadata.METADATA_KEY_DURATION, Long.parseLong(SONG_DURATION));
        return bundle;
    }

    private Bundle getBundleWithBitmap(String field, Bitmap image) {
        Bundle bundle = getBundle();
        bundle.putParcelable(field, image);
        return bundle;
    }

    private Bundle getBundleWithUri(String field, Uri uri) {
        Bundle bundle = getBundle();
        bundle.putString(field, uri.toString());
        return bundle;
    }

    private void assertMetadata(String mediaId, String title, String artist, String album,
            String trackNum, String numTracks, String genre, String duration,
            Image image, Metadata metadata) {
        assertThat(metadata.mediaId).isEqualTo(mediaId);
        assertThat(metadata.title).isEqualTo(title);
        assertThat(metadata.artist).isEqualTo(artist);
        assertThat(metadata.album).isEqualTo(album);
        assertThat(metadata.trackNum).isEqualTo(trackNum);
        assertThat(metadata.numTracks).isEqualTo(numTracks);
        assertThat(metadata.genre).isEqualTo(genre);
        assertThat(metadata.duration).isEqualTo(duration);
        Assert.assertTrue(Image.sameAs(metadata.image, image));
    }

    /**
     * Make sure the media ID we set is transferred to Metadata object we build
     */
    @Test
    public void testBuildMetadataSetMediaId() {
        Metadata metadata = new Metadata.Builder().setMediaId(SONG_MEDIA_ID).build();
        assertMetadata(SONG_MEDIA_ID, null, null, null, null, null, null, null, null, metadata);
    }

    /**
     * Make sure you can build a Metadata from a MediaMetadata that has no art
     */
    @Test
    public void testBuildMetadataFromMediaMetadata() {
        MediaMetadata m = getMediaMetadata();
        Metadata metadata = new Metadata.Builder().fromMediaMetadata(m).build();
        assertMetadata(SONG_MEDIA_ID, SONG_TITLE, SONG_ARTIST, SONG_ALBUM, SONG_TRACK_NUM,
                SONG_NUM_TRACKS, SONG_GENRE, SONG_DURATION, null, metadata);
    }

    /**
     * Make sure you can build a Metadata from a MediaMetadata that has bitmap art
     */
    @Test
    public void testBuildMetadataFromMediaMetadataWithArt() {
        MediaMetadata m =
                getMediaMetadataWithBitmap(MediaMetadata.METADATA_KEY_ART, mTestBitmap);
        Metadata metadata = new Metadata.Builder().fromMediaMetadata(m).build();
        assertMetadata(SONG_MEDIA_ID, SONG_TITLE, SONG_ARTIST, SONG_ALBUM, SONG_TRACK_NUM,
                SONG_NUM_TRACKS, SONG_GENRE, SONG_DURATION, mSongImage, metadata);
    }

    /**
     * Make sure you can build a Metadata from a MediaMetadata that has bitmap album art
     */
    @Test
    public void testBuildMetadataFromMediaMetadataWithAlbumArt() {
        MediaMetadata m =
                getMediaMetadataWithBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, mTestBitmap);
        Metadata metadata = new Metadata.Builder().fromMediaMetadata(m).build();
        assertMetadata(SONG_MEDIA_ID, SONG_TITLE, SONG_ARTIST, SONG_ALBUM, SONG_TRACK_NUM,
                SONG_NUM_TRACKS, SONG_GENRE, SONG_DURATION, mSongImage, metadata);
    }

    /**
     * Make sure you can build a Metadata from a MediaMetadata that a display icon
     */
    @Test
    public void testBuildMetadataFromMediaMetadataWithDisplayIcon() {
        MediaMetadata m =
                getMediaMetadataWithBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, mTestBitmap);
        Metadata metadata = new Metadata.Builder().fromMediaMetadata(m).build();
        assertMetadata(SONG_MEDIA_ID, SONG_TITLE, SONG_ARTIST, SONG_ALBUM, SONG_TRACK_NUM,
                SONG_NUM_TRACKS, SONG_GENRE, SONG_DURATION, mSongImage, metadata);
    }

    /**
     * Make sure you can build a Metadata from a MediaMetadata that has Uri based art
     */
    @Test
    public void testBuildMetadataFromMediaMetadataWithUriArt() {
        MediaMetadata m =
                getMediaMetadataWithUri(MediaMetadata.METADATA_KEY_ART_URI, IMAGE_URI_1);
        Metadata metadata = new Metadata.Builder()
                .useContext(mMockContext)
                .fromMediaMetadata(m)
                .build();
        assertMetadata(SONG_MEDIA_ID, SONG_TITLE, SONG_ARTIST, SONG_ALBUM, SONG_TRACK_NUM,
                SONG_NUM_TRACKS, SONG_GENRE, SONG_DURATION, mSongImage, metadata);
    }

     /**
     * Make sure you can build a Metadata from a MediaMetadata that has Uri based album art
     */
    @Test
    public void testBuildMetadataFromMediaMetadataWithUriAlbumArt() {
        MediaMetadata m =
                getMediaMetadataWithUri(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, IMAGE_URI_1);
        Metadata metadata = new Metadata.Builder()
                .useContext(mMockContext)
                .fromMediaMetadata(m)
                .build();
        assertMetadata(SONG_MEDIA_ID, SONG_TITLE, SONG_ARTIST, SONG_ALBUM, SONG_TRACK_NUM,
                SONG_NUM_TRACKS, SONG_GENRE, SONG_DURATION, mSongImage, metadata);
    }

     /**
     * Make sure you can build a Metadata from a MediaMetadata that has a Uri based display icon
     */
    @Test
    public void testBuildMetadataFromMediaMetadataWithUriDisplayIcon() {
        MediaMetadata m =
                getMediaMetadataWithUri(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI, IMAGE_URI_1);
        Metadata metadata = new Metadata.Builder()
                .useContext(mMockContext)
                .fromMediaMetadata(m)
                .build();
        assertMetadata(SONG_MEDIA_ID, SONG_TITLE, SONG_ARTIST, SONG_ALBUM, SONG_TRACK_NUM,
                SONG_NUM_TRACKS, SONG_GENRE, SONG_DURATION, mSongImage, metadata);
    }

    /**
     * Make sure we're robust to build attempts that include Uri based art without a context
     */
    @Test
    public void testBuildMetadataFromMediaMetadataWithUriNoContext() {
        MediaMetadata m =
                getMediaMetadataWithUri(MediaMetadata.METADATA_KEY_ART_URI, IMAGE_URI_1);
        Metadata metadata = new Metadata.Builder()
                .fromMediaMetadata(m)
                .build();
        assertMetadata(SONG_MEDIA_ID, SONG_TITLE, SONG_ARTIST, SONG_ALBUM, SONG_TRACK_NUM,
                SONG_NUM_TRACKS, SONG_GENRE, SONG_DURATION, null, metadata);
    }

    /**
     * Make sure building with a MediaMetadata that contains URI art when URI art is disabled
     * yields no cover art.
     */
    @Test
    public void testBuildMetadataFromMediaMetadataWithUriAndUrisDisabled() {
        when(mMockResources.getBoolean(
                R.bool.avrcp_target_cover_art_uri_images)).thenReturn(false);
        MediaMetadata m =
                getMediaMetadataWithUri(MediaMetadata.METADATA_KEY_ART_URI, IMAGE_URI_1);
        Metadata metadata = new Metadata.Builder()
                .useContext(mMockContext)
                .fromMediaMetadata(m)
                .build();
        assertMetadata(SONG_MEDIA_ID, SONG_TITLE, SONG_ARTIST, SONG_ALBUM, SONG_TRACK_NUM,
                SONG_NUM_TRACKS, SONG_GENRE, SONG_DURATION, null, metadata);
    }

    /**
     * Make sure we're robust to building with a null MediaMetadata
     */
    @Test
    public void testBuildMetadataFromNullMediaMetadata() {
        Metadata metadata = new Metadata.Builder().fromMediaMetadata(null).build();
        assertMetadata(null, null, null, null, null, null, null, null, null, metadata);
    }

    /**
     * Make sure you can create a Metadata from a simple MediaDescription
     */
    @Test
    public void testBuildMetadataFromMediaDescription() {
        MediaDescription description = getMediaDescription(null, null, null);
        Metadata metadata = new Metadata.Builder().fromMediaDescription(description).build();
        assertMetadata(SONG_MEDIA_ID, SONG_TITLE, SONG_ARTIST, SONG_ALBUM, null, null, null, null,
                null, metadata);
    }

    /**
     * Make sure you can create a Metadata from a MediaDescription that contains cover artwork as a
     * Bitmap Image
     */
    @Test
    public void testBuildMetadataFromMediaDescriptionWithIconArt() {
        MediaDescription description = getMediaDescription(mTestBitmap, null, null);
        Metadata metadata = new Metadata.Builder().fromMediaDescription(description).build();
        assertMetadata(SONG_MEDIA_ID, SONG_TITLE, SONG_ARTIST, SONG_ALBUM, null, null, null, null,
                mSongImage, metadata);
    }

    /**
     * Make sure you can create a Metadata from a MediaDescription that contains cover artwork as
     * an icon Uri
     */
    @Test
    public void testBuildMetadataFromMediaDescriptionWithIconUri() {
        MediaDescription description = getMediaDescription(null, IMAGE_URI_1, null);
        Metadata metadata = new Metadata.Builder()
                .useContext(mMockContext)
                .fromMediaDescription(description)
                .build();
        assertMetadata(SONG_MEDIA_ID, SONG_TITLE, SONG_ARTIST, SONG_ALBUM, null, null, null, null,
                mSongImage, metadata);
    }

    /**
     * Make sure we're robust to attempts to create Uri based images without a context set
     */
    @Test
    public void testBuildMetadataFromMediaDescriptionWithArtNullContext() {
        MediaDescription description = getMediaDescription(null, IMAGE_URI_1, null);
        Metadata metadata = new Metadata.Builder().fromMediaDescription(description).build();
        assertMetadata(SONG_MEDIA_ID, SONG_TITLE, SONG_ARTIST, SONG_ALBUM, null, null, null, null,
                null, metadata);
    }

    /**
     * Make sure you can create a Metadata object from a MediaDesciption with a bundle of extras
     * that provide more detailed information about the item.
     */
    @Test
    public void testBuildMetadataFromMediaDescriptionWithExtras() {
        Bundle extras = getBundleWithBitmap(MediaMetadata.METADATA_KEY_ART, mTestBitmap);
        MediaDescription description = getMediaDescription(null, null, extras);
        Metadata metadata = new Metadata.Builder().fromMediaDescription(description).build();
        assertMetadata(SONG_MEDIA_ID, SONG_TITLE, SONG_ARTIST, SONG_ALBUM, SONG_TRACK_NUM,
                SONG_NUM_TRACKS, SONG_GENRE, SONG_DURATION, mSongImage, metadata);
    }

    /**
     * Make sure we're robust to null MediaDescriptions
     */
    @Test
    public void testBuildMetadataFromNullMediaDescription() {
        Metadata metadata = new Metadata.Builder().fromMediaDescription(null).build();
        assertMetadata(null, null, null, null, null, null, null, null, null, metadata);
    }

    /**
     * Make sure you can create a Metadata from a simple bundle of MediaMetadata based key-values
     */
    @Test
    public void testBuildMetadataFromBundle() {
        Bundle bundle = getBundle();
        Metadata metadata = new Metadata.Builder().fromBundle(bundle).build();
        assertMetadata(SONG_MEDIA_ID, SONG_TITLE, SONG_ARTIST, SONG_ALBUM, SONG_TRACK_NUM,
                SONG_NUM_TRACKS, SONG_GENRE, SONG_DURATION, null, metadata);
    }

    /**
     * Make sure you can create a Metadata from a simple bundle of MediaMetadata based key-values
     * where the bundle contains bitmap art
     */
    @Test
    public void testBuildMetadataFromBundleWithArt() {
        Bundle bundle = getBundleWithBitmap(MediaMetadata.METADATA_KEY_ART, mTestBitmap);
        Metadata metadata = new Metadata.Builder().fromBundle(bundle).build();
        assertMetadata(SONG_MEDIA_ID, SONG_TITLE, SONG_ARTIST, SONG_ALBUM, SONG_TRACK_NUM,
                SONG_NUM_TRACKS, SONG_GENRE, SONG_DURATION, mSongImage, metadata);
    }

    /**
     * Make sure you can create a Metadata from a simple bundle of MediaMetadata based key-values
     * where the bundle contains bitmap album art
     */
    @Test
    public void testBuildMetadataFromBundleWithAlbumArt() {
        Bundle bundle = getBundleWithBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, mTestBitmap);
        Metadata metadata = new Metadata.Builder().fromBundle(bundle).build();
        assertMetadata(SONG_MEDIA_ID, SONG_TITLE, SONG_ARTIST, SONG_ALBUM, SONG_TRACK_NUM,
                SONG_NUM_TRACKS, SONG_GENRE, SONG_DURATION, mSongImage, metadata);
    }

    /**
     * Make sure you can create a Metadata from a simple bundle of MediaMetadata based key-values
     * where the bundle contains a bitmap display icon
     */
    @Test
    public void testBuildMetadataFromBundleWithDisplayIcon() {
        Bundle bundle = getBundleWithBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, mTestBitmap);
        Metadata metadata = new Metadata.Builder().fromBundle(bundle).build();
        assertMetadata(SONG_MEDIA_ID, SONG_TITLE, SONG_ARTIST, SONG_ALBUM, SONG_TRACK_NUM,
                SONG_NUM_TRACKS, SONG_GENRE, SONG_DURATION, mSongImage, metadata);
    }

    /**
     * Make sure you can create a Metadata from a simple bundle of MediaMetadata based key-values
     * where the bundle contains uri art
     */
    @Test
    public void testBuildMetadataFromBundleWithUriArt() {
        Bundle bundle = getBundleWithUri(MediaMetadata.METADATA_KEY_ART_URI, IMAGE_URI_1);
        Metadata metadata = new Metadata.Builder()
                .useContext(mMockContext)
                .fromBundle(bundle)
                .build();
        assertMetadata(SONG_MEDIA_ID, SONG_TITLE, SONG_ARTIST, SONG_ALBUM, SONG_TRACK_NUM,
                SONG_NUM_TRACKS, SONG_GENRE, SONG_DURATION, mSongImage, metadata);
    }

    /**
     * Make sure you can create a Metadata from a simple bundle of MediaMetadata based key-values
     * where the bundle contains uri album art
     */
    @Test
    public void testBuildMetadataFromBundleWithUriAlbumArt() {
        Bundle bundle = getBundleWithUri(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, IMAGE_URI_1);
        Metadata metadata = new Metadata.Builder()
                .useContext(mMockContext)
                .fromBundle(bundle)
                .build();
        assertMetadata(SONG_MEDIA_ID, SONG_TITLE, SONG_ARTIST, SONG_ALBUM, SONG_TRACK_NUM,
                SONG_NUM_TRACKS, SONG_GENRE, SONG_DURATION, mSongImage, metadata);
    }

    /**
     * Make sure you can create a Metadata from a simple bundle of MediaMetadata based key-values
     * where the bundle contains a uri display icon
     */
    @Test
    public void testBuildMetadataFromBundleWithUriDisplayIcon() {
        Bundle bundle = getBundleWithUri(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI, IMAGE_URI_1);
        Metadata metadata = new Metadata.Builder()
                .useContext(mMockContext)
                .fromBundle(bundle)
                .build();
        assertMetadata(SONG_MEDIA_ID, SONG_TITLE, SONG_ARTIST, SONG_ALBUM, SONG_TRACK_NUM,
                SONG_NUM_TRACKS, SONG_GENRE, SONG_DURATION, mSongImage, metadata);
    }

    /**
     * Make sure we're robust to building with a Bundle that contains URI art and no context object
     */
    @Test
    public void testBuildMetadataFromBundleWithUriNoContext() {
        Bundle bundle = getBundleWithUri(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI, IMAGE_URI_1);
        Metadata metadata = new Metadata.Builder()
                .fromBundle(bundle)
                .build();
        assertMetadata(SONG_MEDIA_ID, SONG_TITLE, SONG_ARTIST, SONG_ALBUM, SONG_TRACK_NUM,
                SONG_NUM_TRACKS, SONG_GENRE, SONG_DURATION, null, metadata);
    }

    /**
     * Make sure building with a Bundle that contains URI art when URI art is disabled yields no
     * cover art.
     */
    @Test
    public void testBuildMetadataFromBundleWithUriAndUrisDisabled() {
        when(mMockResources.getBoolean(
                R.bool.avrcp_target_cover_art_uri_images)).thenReturn(false);
        Bundle bundle = getBundleWithUri(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI, IMAGE_URI_1);
        Metadata metadata = new Metadata.Builder()
                .useContext(mMockContext)
                .fromBundle(bundle)
                .build();
        assertMetadata(SONG_MEDIA_ID, SONG_TITLE, SONG_ARTIST, SONG_ALBUM, SONG_TRACK_NUM,
                SONG_NUM_TRACKS, SONG_GENRE, SONG_DURATION, null, metadata);
    }

    /**
     * Make sure we're robust to empty Bundles
     */
    @Test
    public void testBuildMetadataFromEmptyBundle() {
        Bundle bundle = new Bundle();
        Metadata metadata = new Metadata.Builder().fromBundle(bundle).build();
        assertMetadata(null, null, null, null, null, null, null, null, null, metadata);
    }

    /**
     * Make sure we're robust to null Bundles
     */
    @Test
    public void testBuildMetadataFromNullBundle() {
        Metadata metadata = new Metadata.Builder().fromBundle(null).build();
        assertMetadata(null, null, null, null, null, null, null, null, null, metadata);
    }

    /**
     * Make a Metadata with a simple MediaItem
     */
    @Test
    public void testBuildMetadataFromMediaItem() {
        MediaDescription description = getMediaDescription(null, null, null);
        MediaItem item = getMediaItem(description);
        Metadata metadata = new Metadata.Builder().fromMediaItem(item).build();
        assertMetadata(SONG_MEDIA_ID, SONG_TITLE, SONG_ARTIST, SONG_ALBUM, null, null, null, null,
                null, metadata);
    }

    /**
     * Make a Metadata with a MediaItem that has icon art
     */
    @Test
    public void testBuildMetadataFromMediaItemWithIconArt() {
        MediaDescription description = getMediaDescription(mTestBitmap, null, null);
        MediaItem item = getMediaItem(description);
        Metadata metadata = new Metadata.Builder().fromMediaItem(item).build();
        assertMetadata(SONG_MEDIA_ID, SONG_TITLE, SONG_ARTIST, SONG_ALBUM, null, null, null, null,
                mSongImage, metadata);
    }

    /**
     * Make a Metadata with a MediaItem that has an icon uri
     */
    @Test
    public void testBuildMetadataFromMediaItemWithIconUri() {
        MediaDescription description = getMediaDescription(null, IMAGE_URI_1, null);
        MediaItem item = getMediaItem(description);
        Metadata metadata = new Metadata.Builder()
                .useContext(mMockContext)
                .fromMediaItem(item)
                .build();
        assertMetadata(SONG_MEDIA_ID, SONG_TITLE, SONG_ARTIST, SONG_ALBUM, null, null, null, null,
                mSongImage, metadata);
    }

    /**
     * Make a Metadata with a MediaItem that has an icon uri, but don't use a context
     */
    @Test
    public void testBuildMetadataFromMediaItemWithIconUriNoContext() {
        MediaDescription description = getMediaDescription(null, IMAGE_URI_1, null);
        MediaItem item = getMediaItem(description);
        Metadata metadata = new Metadata.Builder().fromMediaItem(item).build();
        assertMetadata(SONG_MEDIA_ID, SONG_TITLE, SONG_ARTIST, SONG_ALBUM, null, null, null, null,
                null, metadata);
    }

    /**
     * Make sure building with a MediaItem that contains URI art when URI art is disabled yields no
     * cover art.
     */
    @Test
    public void testBuildMetadataFromMediaItemWithIconUriAndUrisDisabled() {
        when(mMockResources.getBoolean(
                R.bool.avrcp_target_cover_art_uri_images)).thenReturn(false);
        MediaDescription description = getMediaDescription(null, IMAGE_URI_1, null);
        MediaItem item = getMediaItem(description);
        Metadata metadata = new Metadata.Builder()
                .useContext(mMockContext)
                .fromMediaItem(item)
                .build();
        assertMetadata(SONG_MEDIA_ID, SONG_TITLE, SONG_ARTIST, SONG_ALBUM, null, null, null, null,
                null, metadata);
    }

    /**
     * Make a Metadata with a MediaItem that has extras
     */
    @Test
    public void testBuildMetadataFromMediaItemWithExtras() {
        Bundle extras = getBundleWithBitmap(MediaMetadata.METADATA_KEY_ART, mTestBitmap);
        MediaDescription description = getMediaDescription(null, null, extras);
        MediaItem item = getMediaItem(description);
        Metadata metadata = new Metadata.Builder().fromMediaItem(item).build();
        assertMetadata(SONG_MEDIA_ID, SONG_TITLE, SONG_ARTIST, SONG_ALBUM, SONG_TRACK_NUM,
                SONG_NUM_TRACKS, SONG_GENRE, SONG_DURATION, mSongImage, metadata);
    }

    /**
     * Make a Metadata with a null MediaItem
     */
    @Test
    public void testBuildMetadataFromNullMediaItem() {
        Metadata metadata = new Metadata.Builder().fromMediaItem(null).build();
        assertMetadata(null, null, null, null, null, null, null, null, null, metadata);
    }

    /**
     * Make a Metadata from a simple QueueItem
     */
    @Test
    public void testBuildMetadataFromQueueItem() {
        MediaDescription description = getMediaDescription(null, null, null);
        QueueItem queueItem = getQueueItem(description);
        Metadata metadata = new Metadata.Builder().fromQueueItem(queueItem).build();
        assertMetadata(SONG_MEDIA_ID, SONG_TITLE, SONG_ARTIST, SONG_ALBUM, null, null, null, null,
                null, metadata);
    }

    /**
     * Make a Metadata from a QueueItem with icon art
     */
    @Test
    public void testBuildMetadataFromQueueItemWithIconArt() {
        MediaDescription description = getMediaDescription(mTestBitmap, null, null);
        QueueItem queueItem = getQueueItem(description);
        Metadata metadata = new Metadata.Builder().fromQueueItem(queueItem).build();
        assertMetadata(SONG_MEDIA_ID, SONG_TITLE, SONG_ARTIST, SONG_ALBUM, null, null, null, null,
                mSongImage, metadata);
    }

    /**
     * Make a Metadata from a QueueItem with an icon uri
     */
    @Test
    public void testBuildMetadataFromQueueItemWithIconUri() {
        MediaDescription description = getMediaDescription(null, IMAGE_URI_1, null);
        QueueItem queueItem = getQueueItem(description);
        Metadata metadata = new Metadata.Builder()
                .useContext(mMockContext)
                .fromQueueItem(queueItem)
                .build();
        assertMetadata(SONG_MEDIA_ID, SONG_TITLE, SONG_ARTIST, SONG_ALBUM, null, null, null, null,
                mSongImage, metadata);
    }

    /**
     * Make a Metadata from a QueueItem with an icon uri, but don't use a context
     */
    @Test
    public void testBuildMetadataFromQueueItemWithIconUriNoContext() {
        MediaDescription description = getMediaDescription(null, IMAGE_URI_1, null);
        QueueItem queueItem = getQueueItem(description);
        Metadata metadata = new Metadata.Builder().fromQueueItem(queueItem).build();
        assertMetadata(SONG_MEDIA_ID, SONG_TITLE, SONG_ARTIST, SONG_ALBUM, null, null, null, null,
                null, metadata);
    }

    /**
     * Make sure building with a QueueItem that contains URI art when URI art is disabled yields
     * no cover art.
     */
    @Test
    public void testBuildMetadataFromQueueItemWithIconUriandUrisDisabled() {
        when(mMockResources.getBoolean(
                R.bool.avrcp_target_cover_art_uri_images)).thenReturn(false);
        MediaDescription description = getMediaDescription(null, IMAGE_URI_1, null);
        QueueItem queueItem = getQueueItem(description);
        Metadata metadata = new Metadata.Builder()
                .useContext(mMockContext)
                .fromQueueItem(queueItem)
                .build();
        assertMetadata(SONG_MEDIA_ID, SONG_TITLE, SONG_ARTIST, SONG_ALBUM, null, null, null, null,
                null, metadata);
    }

    /**
     * Make a Metadata from a QueueItem with extras
     */
    @Test
    public void testBuildMetadataFromQueueItemWithExtras() {
        Bundle extras = getBundleWithBitmap(MediaMetadata.METADATA_KEY_ART, mTestBitmap);
        MediaDescription description = getMediaDescription(null, null, extras);
        QueueItem queueItem = getQueueItem(description);
        Metadata metadata = new Metadata.Builder().fromQueueItem(queueItem).build();
        assertMetadata(SONG_MEDIA_ID, SONG_TITLE, SONG_ARTIST, SONG_ALBUM, SONG_TRACK_NUM,
                SONG_NUM_TRACKS, SONG_GENRE, SONG_DURATION, mSongImage, metadata);
    }

    /**
     * Make a Metadata with a null QueueItem
     */
    @Test
    public void testBuildMetadataFromNullQueueItem() {
        Metadata metadata = new Metadata.Builder().fromQueueItem(null).build();
        assertMetadata(null, null, null, null, null, null, null, null, null, metadata);
    }

    /**
     * Build a Metadata using the defaults and nothing else. Ensure it sets all the default values
     */
    @Test
    public void testBuildMetadataUseDefaults() {
        Metadata metadata = new Metadata.Builder().useDefaults().build();
        assertMetadata(Metadata.EMPTY_MEDIA_ID, Metadata.EMPTY_TITLE, Metadata.EMPTY_ARTIST,
                Metadata.EMPTY_ALBUM, Metadata.EMPTY_TRACK_NUM, Metadata.EMPTY_NUM_TRACKS,
                Metadata.EMPTY_GENRE, Metadata.EMPTY_DURATION, DEFAULT_IMAGE, metadata);
    }

    /**
     * Build a Metadata using the defaults and an object with partial fields. Ensure the resulting
     * Metadata has the proper mix of defaults and extracted fields.
     */
    @Test
    public void testBuildMetadataUseDefaultsAndPartialFields() {
        MediaDescription description = getMediaDescription(null, null, null);
        Metadata metadata = new Metadata.Builder()
                .useDefaults()
                .fromMediaDescription(description)
                .build();
        assertMetadata(SONG_MEDIA_ID, SONG_TITLE, SONG_ARTIST, SONG_ALBUM,
                Metadata.EMPTY_TRACK_NUM, Metadata.EMPTY_NUM_TRACKS, Metadata.EMPTY_GENRE,
                Metadata.EMPTY_DURATION, DEFAULT_IMAGE, metadata);
    }

    /**
     * Build a Metadata using the defaults and an object with all fields. Ensure the resulting
     * Metadata has the proper no defaults left over.
     */
    @Test
    public void testBuildMetadataUseDefaultsAndAllFields() {
        MediaMetadata m =
                getMediaMetadataWithBitmap(MediaMetadata.METADATA_KEY_ART, mTestBitmap);
        Metadata metadata = new Metadata.Builder().useDefaults().fromMediaMetadata(m).build();
        assertMetadata(SONG_MEDIA_ID, SONG_TITLE, SONG_ARTIST, SONG_ALBUM, SONG_TRACK_NUM,
                SONG_NUM_TRACKS, SONG_GENRE, SONG_DURATION, mSongImage, metadata);
    }

    /**
     * Build a Metadata using the defaults and an object with missing fields. Ensure the resulting
     * Metadata has the proper mix of defaults and extracted fields.
     */
    @Test
    public void testBuildMetadataUseDefaultsAndMissingFields() {
        Bundle bundle = new Bundle();
        bundle.putString(MediaMetadata.METADATA_KEY_TITLE, SONG_TITLE);
        bundle.putLong(MediaMetadata.METADATA_KEY_NUM_TRACKS, Long.parseLong(SONG_NUM_TRACKS));
        bundle.putString(MediaMetadata.METADATA_KEY_GENRE, SONG_GENRE);
        bundle.putParcelable(MediaMetadata.METADATA_KEY_ART, mTestBitmap);
        Metadata metadata = new Metadata.Builder().useDefaults().fromBundle(bundle).build();
        assertMetadata(Metadata.EMPTY_MEDIA_ID, SONG_TITLE, Metadata.EMPTY_ARTIST,
                Metadata.EMPTY_ALBUM, Metadata.EMPTY_TRACK_NUM, SONG_NUM_TRACKS, SONG_GENRE,
                Metadata.EMPTY_DURATION, mSongImage, metadata);
    }

    /**
     * Build a Metadata using the defaults. Call useDefaults at the end after other fields have
     * been extracted from the media framework objects. Ensure we don't overwrite existing values
     * with defaults. Ensure that the metadata created is the same (by field) as the one where
     * useDefaults is called first.
     */
    @Test
    public void testBuildMetadataUseDefaultsOrderDoesntMatter() {
        Bundle bundle = new Bundle();
        bundle.putString(MediaMetadata.METADATA_KEY_TITLE, SONG_TITLE);
        bundle.putLong(MediaMetadata.METADATA_KEY_NUM_TRACKS, Long.parseLong(SONG_NUM_TRACKS));
        bundle.putString(MediaMetadata.METADATA_KEY_GENRE, SONG_GENRE);
        bundle.putParcelable(MediaMetadata.METADATA_KEY_ART, mTestBitmap);
        Metadata metadata = new Metadata.Builder().fromBundle(bundle).useDefaults().build();
        Metadata metadata2 = new Metadata.Builder().useDefaults().fromBundle(bundle).build();
        assertMetadata(Metadata.EMPTY_MEDIA_ID, SONG_TITLE, Metadata.EMPTY_ARTIST,
                Metadata.EMPTY_ALBUM, Metadata.EMPTY_TRACK_NUM, SONG_NUM_TRACKS, SONG_GENRE,
                Metadata.EMPTY_DURATION, mSongImage, metadata);
        assertMetadata(Metadata.EMPTY_MEDIA_ID, SONG_TITLE, Metadata.EMPTY_ARTIST,
                Metadata.EMPTY_ALBUM, Metadata.EMPTY_TRACK_NUM, SONG_NUM_TRACKS, SONG_GENRE,
                Metadata.EMPTY_DURATION, mSongImage, metadata2);
    }

    /**
     * Make sure you can clone a Metadata
     */
    @Test
    public void testClone() {
        MediaMetadata m =
                getMediaMetadataWithBitmap(MediaMetadata.METADATA_KEY_ART, mTestBitmap);
        Metadata metadata = new Metadata.Builder().fromMediaMetadata(m).build();
        Metadata metadata2 = metadata.clone();
        assertThat(metadata.mediaId).isEqualTo(metadata2.mediaId);
        assertThat(metadata.title).isEqualTo(metadata2.title);
        assertThat(metadata.artist).isEqualTo(metadata2.artist);
        assertThat(metadata.album).isEqualTo(metadata2.album);
        assertThat(metadata.trackNum).isEqualTo(metadata2.trackNum);
        assertThat(metadata.numTracks).isEqualTo(metadata2.numTracks);
        assertThat(metadata.genre).isEqualTo(metadata2.genre);
        assertThat(metadata.duration).isEqualTo(metadata2.duration);
        assertThat(metadata.image).isEqualTo(metadata2.image);
        assertThat(metadata).isEqualTo(metadata2);
    }

    /**
     * Make sure two Metadata objects are different if title doesn't match
     */
    @Test
    public void testEqualsDifferentTitle() {
        MediaMetadata m =
                getMediaMetadataWithBitmap(MediaMetadata.METADATA_KEY_ART, mTestBitmap);
        Metadata metadata = new Metadata.Builder().fromMediaMetadata(m).build();
        Metadata metadata2 = metadata.clone();
        metadata2.title = null;
        assertThat(metadata).isNotEqualTo(metadata2);
    }

    /**
     * Make sure two Metadata objects are different if artist doesn't match
     */
    @Test
    public void testEqualsDifferentArtist() {
        MediaMetadata m =
                getMediaMetadataWithBitmap(MediaMetadata.METADATA_KEY_ART, mTestBitmap);
        Metadata metadata = new Metadata.Builder().fromMediaMetadata(m).build();
        Metadata metadata2 = metadata.clone();
        metadata2.artist = Metadata.EMPTY_ARTIST;
        assertThat(metadata).isNotEqualTo(metadata2);
    }

    /**
     * Make sure two Metadata objects are different if album doesn't match
     */
    @Test
    public void testEqualsDifferentAlbum() {
        MediaMetadata m =
                getMediaMetadataWithBitmap(MediaMetadata.METADATA_KEY_ART, mTestBitmap);
        Metadata metadata = new Metadata.Builder().fromMediaMetadata(m).build();
        Metadata metadata2 = metadata.clone();
        metadata2.album = Metadata.EMPTY_ALBUM;
        assertThat(metadata).isNotEqualTo(metadata2);
    }

    /**
     * Make sure two Metadata objects are different if trackNum doesn't match
     */
    @Test
    public void testEqualsDifferentTrackNum() {
        MediaMetadata m =
                getMediaMetadataWithBitmap(MediaMetadata.METADATA_KEY_ART, mTestBitmap);
        Metadata metadata = new Metadata.Builder().fromMediaMetadata(m).build();
        Metadata metadata2 = metadata.clone();
        metadata2.trackNum = Metadata.EMPTY_TRACK_NUM;
        assertThat(metadata).isNotEqualTo(metadata2);
    }

    /**
     * Make sure two Metadata objects are different if numTracks doesn't match
     */
    @Test
    public void testEqualsDifferentNumTracks() {
        MediaMetadata m =
                getMediaMetadataWithBitmap(MediaMetadata.METADATA_KEY_ART, mTestBitmap);
        Metadata metadata = new Metadata.Builder().fromMediaMetadata(m).build();
        Metadata metadata2 = metadata.clone();
        metadata2.numTracks = Metadata.EMPTY_NUM_TRACKS;
        assertThat(metadata).isNotEqualTo(metadata2);
    }

    /**
     * Make sure you can get any non-null string representation of Metadata
     */
    @Test
    public void testToString() {
        Metadata metadata = new Metadata.Builder().build();
        assertThat(metadata.toString()).isNotNull();
    }
}
