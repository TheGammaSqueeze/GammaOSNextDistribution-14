/*
 * Copyright 2022 The Android Open Source Project
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

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.browse.MediaBrowser;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;

import androidx.test.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;

public class UtilTest {
    private static final String SONG_MEDIA_ID = "abc123";
    private static final String SONG_TITLE = "BT Test Song";
    private static final String SONG_ARTIST = "BT Test Artist";
    private static final String SONG_ALBUM = "BT Test Album";

    private Context mContext;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getTargetContext();
    }

    @Test
    public void getDisplayName() throws Exception {
        PackageManager manager = mContext.getPackageManager();
        String displayName =  manager.getApplicationLabel(
                manager.getApplicationInfo(mContext.getPackageName(), 0)).toString();
        assertThat(Util.getDisplayName(mContext, mContext.getPackageName())).isEqualTo(displayName);

        String invalidPackage = "invalidPackage";
        assertThat(Util.getDisplayName(mContext, invalidPackage)).isEqualTo(invalidPackage);
    }

    @Test
    public void toMetadata_withMediaItem() {
        Metadata metadata = Util.toMetadata(mContext,
                new MediaBrowser.MediaItem(createDescription(), 0));
        assertThat(metadata.mediaId).isEqualTo(SONG_MEDIA_ID);
        assertThat(metadata.title).isEqualTo(SONG_TITLE);
        assertThat(metadata.artist).isEqualTo(SONG_ARTIST);
        assertThat(metadata.album).isEqualTo(SONG_ALBUM);
    }

    @Test
    public void toMetadata_withQueueItem() {
        // This will change the media ID to NOW_PLAYING_PREFIX ('NowPlayingId') + the given id
        long queueId = 1;
        Metadata metadata = Util.toMetadata(mContext,
                new MediaSession.QueueItem(createDescription(), queueId));
        assertThat(metadata.mediaId).isEqualTo(Util.NOW_PLAYING_PREFIX + queueId);
        assertThat(metadata.title).isEqualTo(SONG_TITLE);
        assertThat(metadata.artist).isEqualTo(SONG_ARTIST);
        assertThat(metadata.album).isEqualTo(SONG_ALBUM);
    }

    @Test
    public void toMetadata_withMediaMetadata() {
        MediaMetadata.Builder builder = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, SONG_MEDIA_ID)
                .putString(MediaMetadata.METADATA_KEY_TITLE, SONG_TITLE)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, SONG_ARTIST)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, SONG_ALBUM);
        // This will change the media ID to "currsong".
        Metadata metadata = Util.toMetadata(mContext, builder.build());
        assertThat(metadata.mediaId).isEqualTo("currsong");
        assertThat(metadata.title).isEqualTo(SONG_TITLE);
        assertThat(metadata.artist).isEqualTo(SONG_ARTIST);
        assertThat(metadata.album).isEqualTo(SONG_ALBUM);
    }

    @Test
    public void playStatus_playbackStateToAvrcpState() {
        assertThat(PlayStatus.playbackStateToAvrcpState(PlaybackState.STATE_STOPPED))
                .isEqualTo(PlayStatus.STOPPED);
        assertThat(PlayStatus.playbackStateToAvrcpState(PlaybackState.STATE_NONE))
                .isEqualTo(PlayStatus.STOPPED);
        assertThat(PlayStatus.playbackStateToAvrcpState(PlaybackState.STATE_CONNECTING))
                .isEqualTo(PlayStatus.STOPPED);

        assertThat(PlayStatus.playbackStateToAvrcpState(PlaybackState.STATE_BUFFERING))
                .isEqualTo(PlayStatus.PLAYING);
        assertThat(PlayStatus.playbackStateToAvrcpState(PlaybackState.STATE_PLAYING))
                .isEqualTo(PlayStatus.PLAYING);

        assertThat(PlayStatus.playbackStateToAvrcpState(PlaybackState.STATE_PAUSED))
                .isEqualTo(PlayStatus.PAUSED);

        assertThat(PlayStatus.playbackStateToAvrcpState(PlaybackState.STATE_FAST_FORWARDING))
                .isEqualTo(PlayStatus.FWD_SEEK);
        assertThat(PlayStatus.playbackStateToAvrcpState(PlaybackState.STATE_SKIPPING_TO_NEXT))
                .isEqualTo(PlayStatus.FWD_SEEK);
        assertThat(PlayStatus.playbackStateToAvrcpState(PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM))
                .isEqualTo(PlayStatus.FWD_SEEK);

        assertThat(PlayStatus.playbackStateToAvrcpState(PlaybackState.STATE_REWINDING))
                .isEqualTo(PlayStatus.REV_SEEK);
        assertThat(PlayStatus.playbackStateToAvrcpState(PlaybackState.STATE_SKIPPING_TO_PREVIOUS))
                .isEqualTo(PlayStatus.REV_SEEK);

        assertThat(PlayStatus.playbackStateToAvrcpState(PlaybackState.STATE_ERROR))
                .isEqualTo(PlayStatus.ERROR);
        assertThat(PlayStatus.playbackStateToAvrcpState(-100))
                .isEqualTo(PlayStatus.ERROR);
    }

    MediaDescription createDescription() {
        MediaDescription.Builder builder = new MediaDescription.Builder()
                .setMediaId(SONG_MEDIA_ID)
                .setTitle(SONG_TITLE)
                .setSubtitle(SONG_ARTIST)
                .setDescription(SONG_ALBUM);
        return builder.build();
    }
}
