/*
 * Copyright (C) 2018 The Android Open Source Project
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
import android.content.pm.PackageManager;
import android.media.MediaMetadata;
import android.media.browse.MediaBrowser.MediaItem;
import android.media.session.MediaSession;
import android.util.Log;

import com.android.bluetooth.R;

import java.util.ArrayList;
import java.util.List;

class Util {
    public static String TAG = "audio_util.Util";
    public static boolean DEBUG = false;

    private static final String GPM_KEY = "com.google.android.music.mediasession.music_metadata";

    // TODO (apanicke): Remove this prefix later, for now it makes debugging easier.
    public static final String NOW_PLAYING_PREFIX = "NowPlayingId";

    /**
     * Get an empty set of Metadata
     */
    public static final Metadata empty_data() {
        Metadata.Builder builder = new Metadata.Builder();
        return builder.useDefaults().build();
    }

    /**
     * Determine if a set of Metadata is "empty" as defined by audio_util.
     */
    public static final boolean isEmptyData(Metadata data) {
        if (data == null) return true;
        // Note: We need both equals() and an explicit media id check because equals() does
        // not check for the media ID.
        return (empty_data().equals(data) && data.mediaId.equals(Metadata.EMPTY_MEDIA_ID));
    }

    /**
     * Get whether or not Bluetooth is configured to support URI images or not.
     *
     * Note that creating URI images will dramatically increase memory usage.
     */
    public static boolean areUriImagesSupported(Context context) {
        if (context == null) return false;
        return context.getResources().getBoolean(R.bool.avrcp_target_cover_art_uri_images);
    }

    /**
     * Translate a MediaItem to audio_util's Metadata
     */
    public static Metadata toMetadata(Context context, MediaItem item) {
        Metadata.Builder builder = new Metadata.Builder();
        try {
            return builder.useContext(context).useDefaults().fromMediaItem(item).build();
        } catch (Exception e) {
            Log.e(TAG, "Failed to build Metadata from MediaItem, returning empty data", e);
            return empty_data();
        }
    }

    /**
     * Translate a MediaSession.QueueItem to audio_util's Metadata
     */
    public static Metadata toMetadata(Context context, MediaSession.QueueItem item) {
        Metadata.Builder builder = new Metadata.Builder();

        try {
            builder.useDefaults().fromQueueItem(item);
        } catch (Exception e) {
            Log.e(TAG, "Failed to build Metadata from QueueItem, returning empty data", e);
            return empty_data();
        }

        // For Queue Items, the Media Id will always be just its Queue ID
        // We don't need to use its actual ID since we don't promise UIDS being valid
        // between a file system and it's now playing list.
        if (item != null) builder.setMediaId(NOW_PLAYING_PREFIX + item.getQueueId());
        return builder.build();
    }

    /**
     * Translate a MediaMetadata to audio_util's Metadata
     */
    public static Metadata toMetadata(Context context, MediaMetadata data) {
        Metadata.Builder builder = new Metadata.Builder();
        // This will always be currsong. The AVRCP service will overwrite the mediaId if it needs to
        // TODO (apanicke): Remove when the service is ready, right now it makes debugging much more
        // convenient
        try {
            return builder.useContext(context).useDefaults().fromMediaMetadata(data)
                    .setMediaId("currsong").build();
        } catch (Exception e) {
            Log.e(TAG, "Failed to build Metadata from MediaMetadata, returning empty data", e);
            return empty_data();
        }
    }

    /**
     * Translate a list of MediaSession.QueueItem to a list of audio_util's Metadata
     */
    public static List<Metadata> toMetadataList(Context context,
            List<MediaSession.QueueItem> items) {
        ArrayList<Metadata> list = new ArrayList<Metadata>();

        if (items == null) return list;

        for (int i = 0; i < items.size(); i++) {
            Metadata data = toMetadata(context, items.get(i));
            if (isEmptyData(data)) {
                Log.e(TAG, "Received an empty Metadata item in list. Returning an empty queue");
                return new ArrayList<Metadata>();
            }
            data.trackNum = "" + (i + 1);
            data.numTracks = "" + items.size();
            list.add(data);
        }

        return list;
    }

    // Helper method to close a list of ListItems so that if the callee wants
    // to mutate the list they can do it without affecting any internally cached info
    public static List<ListItem> cloneList(List<ListItem> list) {
        List<ListItem> clone = new ArrayList<ListItem>(list.size());
        for (ListItem item : list) clone.add(item.clone());
        return clone;
    }

    public static String getDisplayName(Context context, String packageName) {
        try {
            PackageManager manager = context.getPackageManager();
            return manager.getApplicationLabel(manager.getApplicationInfo(packageName, 0))
                    .toString();
        } catch (Exception e) {
            Log.w(TAG, "Name Not Found using package name: " + packageName);
            return packageName;
        }
    }
}
