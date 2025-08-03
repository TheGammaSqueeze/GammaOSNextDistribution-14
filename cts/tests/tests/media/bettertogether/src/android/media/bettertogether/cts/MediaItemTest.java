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
package android.media.bettertogether.cts;

import android.media.MediaDescription;
import android.media.browse.MediaBrowser.MediaItem;
import android.os.Parcel;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.NonMainlineTest;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link android.media.browse.MediaBrowser.MediaItem}.
 */
@NonMainlineTest
@RunWith(AndroidJUnit4.class)
public class MediaItemTest {
    private static final String DESCRIPTION = "test_description";
    private static final String MEDIA_ID = "test_media_id";
    private static final String TITLE = "test_title";
    private static final String SUBTITLE = "test_subtitle";
    private static final int CONTENT_DESCRIPTION = 0;

    @Test
    public void testBrowsableMediaItem() {
        MediaDescription description = new MediaDescription.Builder()
                .setDescription(DESCRIPTION).setMediaId(MEDIA_ID)
                .setTitle(TITLE).setSubtitle(SUBTITLE).build();
        MediaItem mediaItem = new MediaItem(description, MediaItem.FLAG_BROWSABLE);

        assertThat(mediaItem.getDescription().toString()).isEqualTo(description.toString());
        assertThat(mediaItem.getMediaId()).isEqualTo(MEDIA_ID);
        assertThat(mediaItem.getFlags()).isEqualTo(MediaItem.FLAG_BROWSABLE);
        assertThat(mediaItem.isBrowsable()).isTrue();
        assertThat(mediaItem.isPlayable()).isFalse();
        assertThat(mediaItem.describeContents()).isEqualTo(CONTENT_DESCRIPTION);
        assertThat(mediaItem.toString()).isNotEmpty();

        // Test writeToParcel
        Parcel p = Parcel.obtain();
        mediaItem.writeToParcel(p, 0);
        p.setDataPosition(0);

        MediaItem mediaItemFromParcel = MediaItem.CREATOR.createFromParcel(p);

        assertThat(mediaItemFromParcel).isNotNull();
        assertThat(mediaItemFromParcel.getFlags()).isEqualTo(mediaItem.getFlags());
        assertThat(mediaItemFromParcel.getDescription().toString())
                .isEqualTo(description.toString());

        p.recycle();
    }

    @Test
    public void testPlayableMediaItem() {
        MediaDescription description = new MediaDescription.Builder()
                .setDescription(DESCRIPTION).setMediaId(MEDIA_ID)
                .setTitle(TITLE).setSubtitle(SUBTITLE).build();
        MediaItem mediaItem = new MediaItem(description, MediaItem.FLAG_PLAYABLE);

        assertThat(mediaItem.getDescription().toString()).isEqualTo(description.toString());
        assertThat(mediaItem.getMediaId()).isEqualTo(MEDIA_ID);
        assertThat(mediaItem.getFlags()).isEqualTo(MediaItem.FLAG_PLAYABLE);
        assertThat(mediaItem.isBrowsable()).isFalse();
        assertThat(mediaItem.isPlayable()).isTrue();
        assertThat(mediaItem.describeContents()).isEqualTo(CONTENT_DESCRIPTION);
        assertThat(mediaItem.toString()).isNotEmpty();

        // Test writeToParcel
        Parcel p = Parcel.obtain();
        mediaItem.writeToParcel(p, 0);
        p.setDataPosition(0);

        MediaItem mediaItemFromParcel = MediaItem.CREATOR.createFromParcel(p);

        assertThat(mediaItemFromParcel).isNotNull();
        assertThat(mediaItemFromParcel.getFlags()).isEqualTo(mediaItem.getFlags());
        assertThat(mediaItemFromParcel.getDescription().toString())
                .isEqualTo(description.toString());

        p.recycle();
    }
}
