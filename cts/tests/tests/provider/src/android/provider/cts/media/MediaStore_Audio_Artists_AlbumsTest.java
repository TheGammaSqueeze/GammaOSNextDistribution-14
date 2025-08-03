/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.provider.cts.media;

import static android.provider.cts.media.MediaStoreTest.TAG;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio.Artists.Albums;
import android.provider.MediaStore.Audio.Media;
import android.provider.cts.ProviderTestUtils;
import android.provider.cts.media.MediaStoreAudioTestHelper.Audio1;
import android.provider.cts.media.MediaStoreAudioTestHelper.Audio2;
import android.provider.cts.media.MediaStoreAudioTestHelper.Audio3;
import android.provider.cts.media.MediaStoreAudioTestHelper.Audio6;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SdkSuppress;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@SdkSuppress(minSdkVersion = Build.VERSION_CODES.R)
@RunWith(Parameterized.class)
public class MediaStore_Audio_Artists_AlbumsTest {
    private Context mContext;
    private ContentResolver mContentResolver;

    @Parameter(0)
    public String mVolumeName;

    @Parameters
    public static Iterable<? extends Object> data() {
        return ProviderTestUtils.getSharedVolumeNames();
    }

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mContentResolver = mContext.getContentResolver();

        Log.d(TAG, "Using volume " + mVolumeName);
    }

    @Test
    public void testGetContentUri() {
        Cursor c = null;
        Uri contentUri = MediaStore.Audio.Artists.Albums.getContentUri(mVolumeName, 1);
        assertNotNull(c = mContentResolver.query(contentUri, null, null, null, null));
        c.close();
    }

    @Test
    public void testStoreAudioArtistsAlbums() {
        // the album item is inserted when inserting audio media
        Uri audioMediaUri = Audio1.getInstance().insert(mContentResolver, mVolumeName);
        // get artist id
        Cursor c = mContentResolver.query(audioMediaUri, new String[] { Media.ARTIST_ID }, null,
                null, null);
        c.moveToFirst();
        Long artistId = c.getLong(c.getColumnIndex(Media.ARTIST_ID));
        c.close();
        Uri artistsAlbumsUri = MediaStore.Audio.Artists.Albums.getContentUri(mVolumeName, artistId);
        // do not support insert operation of the albums
        try {
            mContentResolver.insert(artistsAlbumsUri, new ContentValues());
            fail("Should throw UnsupportedOperationException!");
        } catch (UnsupportedOperationException e) {
            // expected
        }

        try {
            // query
            c = mContentResolver.query(artistsAlbumsUri, null, null, null, null);
            assertEquals(1, c.getCount());
            c.moveToFirst();

            assertFalse(c.isNull(c.getColumnIndex(Albums._ID)));
            assertFalse(c.isNull(c.getColumnIndex(Albums.ALBUM_ID)));
            assertEquals(Audio1.ALBUM, c.getString(c.getColumnIndex(Albums.ALBUM)));
            assertNull(c.getString(c.getColumnIndex(Albums.ALBUM_ART)));
            assertNotNull(c.getString(c.getColumnIndex(Albums.ALBUM_KEY)));
            assertFalse(c.isNull(c.getColumnIndex(Albums.ARTIST_ID)));
            assertEquals(Audio1.ARTIST, c.getString(c.getColumnIndex(Albums.ARTIST)));
            assertEquals(Audio1.YEAR, c.getInt(c.getColumnIndex(Albums.FIRST_YEAR)));
            assertEquals(Audio1.YEAR, c.getInt(c.getColumnIndex(Albums.LAST_YEAR)));
            assertEquals(1, c.getInt(c.getColumnIndex(Albums.NUMBER_OF_SONGS)));
            assertEquals(1, c.getInt(c.getColumnIndex(Albums.NUMBER_OF_SONGS_FOR_ARTIST)));
            c.close();

            // do not support update operation of the albums
            ContentValues albumValues = new ContentValues();
            albumValues.put(Albums.ALBUM, Audio2.ALBUM);
            try {
                mContentResolver.update(artistsAlbumsUri, albumValues, null, null);
                fail("Should throw UnsupportedOperationException!");
            } catch (UnsupportedOperationException e) {
                // expected
            }

            // do not support delete operation of the albums
            try {
                mContentResolver.delete(artistsAlbumsUri, null, null);
                fail("Should throw UnsupportedOperationException!");
            } catch (UnsupportedOperationException e) {
                // expected
            }
        } finally {
            mContentResolver.delete(audioMediaUri, null, null);
        }
        // the album items are deleted when deleting the audio media which belongs to the album
        c = mContentResolver.query(artistsAlbumsUri, null, null, null, null);
        assertEquals(0, c.getCount());
        c.close();
    }

    @Test
    public void testDifferentArtistInSameAlbum() {
        // the album item is inserted when inserting audio media
        final Uri audioMediaUri = Audio1.getInstance().insert(mContentResolver, mVolumeName);

        assertThat(Audio1.ALBUM).isEqualTo(Audio3.ALBUM);
        assertThat(Audio1.ARTIST).isEqualTo(Audio3.ARTIST);
        final Uri audioMediaSameArtistUri = Audio3.getInstance().insert(mContentResolver,
                mVolumeName);

        assertThat(Audio1.ALBUM).isEqualTo(Audio6.ALBUM);
        assertThat(Audio1.ARTIST).isNotEqualTo(Audio6.ARTIST);
        final Uri audioMediaOtherArtistUri = Audio6.getInstance().insert(mContentResolver,
                mVolumeName);

        // get artist id
        Cursor c = mContentResolver.query(audioMediaUri, new String[] { Media.ARTIST_ID }, null,
                null, null);
        c.moveToFirst();
        Long artistId = c.getLong(c.getColumnIndex(Media.ARTIST_ID));
        c.close();
        final Uri artistsAlbumsUri = MediaStore.Audio.Artists.Albums.getContentUri(mVolumeName,
                artistId);

        c = mContentResolver.query(audioMediaSameArtistUri, new String[] { Media.ARTIST_ID }, null,
                null, null);
        c.moveToFirst();
        artistId = c.getLong(c.getColumnIndex(Media.ARTIST_ID));
        c.close();
        final Uri artistsAlbumsSameArtistUri = MediaStore.Audio.Artists.Albums.getContentUri(
                mVolumeName, artistId);

        c = mContentResolver.query(audioMediaOtherArtistUri, new String[] { Media.ARTIST_ID }, null,
                null, null);
        c.moveToFirst();
        artistId = c.getLong(c.getColumnIndex(Media.ARTIST_ID));
        c.close();
        final Uri artistsAlbumsOtherArtistUri = MediaStore.Audio.Artists.Albums.getContentUri(
                mVolumeName, artistId);

        try {
            c = mContentResolver.query(artistsAlbumsUri, null, null, null, null);
            assertEquals(1, c.getCount());
            c.moveToFirst();

            assertThat(c.isNull(c.getColumnIndex(Albums._ID))).isFalse();
            assertThat(c.isNull(c.getColumnIndex(Albums.ALBUM_ID))).isFalse();
            assertThat(c.getString(c.getColumnIndex(Albums.ALBUM))).isEqualTo(Audio1.ALBUM);
            assertThat(c.getString(c.getColumnIndex(Albums.ALBUM_ART))).isNull();
            assertThat(c.getString(c.getColumnIndex(Albums.ALBUM_KEY))).isNotNull();
            assertThat(c.isNull(c.getColumnIndex(Albums.ARTIST_ID))).isFalse();
            assertThat(c.getString(c.getColumnIndex(Albums.ARTIST))).isEqualTo(Audio1.ARTIST);
            assertThat(c.getInt(c.getColumnIndex(Albums.FIRST_YEAR))).isEqualTo(Audio1.YEAR);
            assertThat(c.getInt(c.getColumnIndex(Albums.LAST_YEAR))).isEqualTo(Audio1.YEAR);
            assertThat(c.getInt(c.getColumnIndex(Albums.NUMBER_OF_SONGS))).isEqualTo(3);
            assertThat(c.getInt(c.getColumnIndex(Albums.NUMBER_OF_SONGS_FOR_ARTIST))).isEqualTo(2);
            c.close();

            c = mContentResolver.query(artistsAlbumsSameArtistUri, null, null, null, null);
            assertEquals(1, c.getCount());
            c.moveToFirst();

            assertThat(c.isNull(c.getColumnIndex(Albums._ID))).isFalse();
            assertThat(c.isNull(c.getColumnIndex(Albums.ALBUM_ID))).isFalse();
            assertThat(c.getString(c.getColumnIndex(Albums.ALBUM))).isEqualTo(Audio3.ALBUM);
            assertThat(c.getString(c.getColumnIndex(Albums.ALBUM_ART))).isNull();
            assertThat(c.getString(c.getColumnIndex(Albums.ALBUM_KEY))).isNotNull();
            assertThat(c.isNull(c.getColumnIndex(Albums.ARTIST_ID))).isFalse();
            assertThat(c.getString(c.getColumnIndex(Albums.ARTIST))).isEqualTo(Audio3.ARTIST);
            assertThat(c.getInt(c.getColumnIndex(Albums.FIRST_YEAR))).isEqualTo(Audio3.YEAR);
            assertThat(c.getInt(c.getColumnIndex(Albums.LAST_YEAR))).isEqualTo(Audio3.YEAR);
            assertThat(c.getInt(c.getColumnIndex(Albums.NUMBER_OF_SONGS))).isEqualTo(3);
            assertThat(c.getInt(c.getColumnIndex(Albums.NUMBER_OF_SONGS_FOR_ARTIST))).isEqualTo(2);
            c.close();

            c = mContentResolver.query(artistsAlbumsOtherArtistUri, null, null, null, null);
            assertEquals(1, c.getCount());
            c.moveToFirst();

            assertThat(c.isNull(c.getColumnIndex(Albums._ID))).isFalse();
            assertThat(c.isNull(c.getColumnIndex(Albums.ALBUM_ID))).isFalse();
            assertThat(c.getString(c.getColumnIndex(Albums.ALBUM))).isEqualTo(Audio6.ALBUM);
            assertThat(c.getString(c.getColumnIndex(Albums.ALBUM_ART))).isNull();
            assertThat(c.getString(c.getColumnIndex(Albums.ALBUM_KEY))).isNotNull();
            assertThat(c.isNull(c.getColumnIndex(Albums.ARTIST_ID))).isFalse();
            assertThat(c.getString(c.getColumnIndex(Albums.ARTIST))).isEqualTo(Audio6.ARTIST);
            assertThat(c.getInt(c.getColumnIndex(Albums.FIRST_YEAR))).isEqualTo(Audio6.YEAR);
            assertThat(c.getInt(c.getColumnIndex(Albums.LAST_YEAR))).isEqualTo(Audio6.YEAR);
            assertThat(c.getInt(c.getColumnIndex(Albums.NUMBER_OF_SONGS))).isEqualTo(3);
            assertThat(c.getInt(c.getColumnIndex(Albums.NUMBER_OF_SONGS_FOR_ARTIST))).isEqualTo(1);
            c.close();
        } finally {
            mContentResolver.delete(audioMediaUri, null, null);
            mContentResolver.delete(audioMediaSameArtistUri, null, null);
            mContentResolver.delete(audioMediaOtherArtistUri, null, null);
        }
        // the album items are deleted when deleting the audio media which belongs to the album
        c = mContentResolver.query(artistsAlbumsUri, null, null, null, null);
        assertEquals(0, c.getCount());
        c.close();

        c = mContentResolver.query(artistsAlbumsSameArtistUri, null, null, null, null);
        assertEquals(0, c.getCount());
        c.close();

        c = mContentResolver.query(audioMediaOtherArtistUri, null, null, null, null);
        assertEquals(0, c.getCount());
        c.close();
    }
}
