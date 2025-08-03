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
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.browse.MediaBrowser.MediaItem;
import android.media.session.MediaSession;
import android.os.Bundle;

import com.android.bluetooth.R;

import java.util.Objects;

public class Metadata implements Cloneable {
    public String mediaId;
    public String title;
    public String artist;
    public String album;
    public String trackNum;
    public String numTracks;
    public String genre;
    public String duration;
    public Image image;

    // Media ID is an implementation detail and doesn't need to be localized
    public static final String EMPTY_MEDIA_ID = "Not Provided";
    public static final String EMPTY_TITLE = "Not Provided";
    public static final String EMPTY_ARTIST = "";
    public static final String EMPTY_ALBUM = "";
    public static final String EMPTY_TRACK_NUM = "1";
    public static final String EMPTY_NUM_TRACKS = "1";
    public static final String EMPTY_GENRE = "";
    public static final String EMPTY_DURATION = "0";

    @Override
    public Metadata clone() {
        Metadata data = new Metadata();
        data.mediaId = mediaId;
        data.title = title;
        data.artist = artist;
        data.album = album;
        data.trackNum = trackNum;
        data.numTracks = numTracks;
        data.genre = genre;
        data.duration = duration;
        data.image = image;
        return data;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) return false;
        if (!(o instanceof Metadata)) return false;

        final Metadata m = (Metadata) o;
        if (!Objects.equals(title, m.title)) return false;
        if (!Objects.equals(artist, m.artist)) return false;
        if (!Objects.equals(album, m.album)) return false;
        if (!Objects.equals(trackNum, m.trackNum)) return false;
        if (!Objects.equals(numTracks, m.numTracks)) return false;
        if (!Objects.equals(genre, m.genre)) return false;
        if (!Objects.equals(duration, m.duration)) return false;
        // Actual image comparisons have shown to be very expensive. Since it's rare that
        // an application changes the cover artwork between multiple images once it's not
        // null anymore, we just look for changes between "something" and "nothing".
        if ((image == null && m.image != null) || (image != null && m.image == null)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        // Do not hash the Image as it does not implement hashCode
        return Objects.hash(mediaId, title, artist, album, trackNum, numTracks, genre, duration);
    }

    @Override
    public String toString() {
        return "{ mediaId=\"" + mediaId + "\" title=\"" + title + "\" artist=\"" + artist
                + "\" album=\"" + album + "\" duration=" + duration
                + " trackPosition=" + trackNum + "/" + numTracks + " image=" + image + " }";
    }

    /**
     * Replaces default values by {@code filledMetadata} non default values.
     */
    public void replaceDefaults(Metadata filledMetadata) {
        if (filledMetadata == null) {
            return;
        }

        Metadata empty = Util.empty_data();

        if (empty.mediaId.equals(mediaId)) {
            mediaId = filledMetadata.mediaId;
        }
        if (empty.title.equals(title)) {
            title = filledMetadata.title;
        }
        if (empty.artist.equals(artist)) {
            artist = filledMetadata.artist;
        }
        if (empty.album.equals(album)) {
            album = filledMetadata.album;
        }
        if (empty.trackNum.equals(trackNum)) {
            trackNum = filledMetadata.trackNum;
        }
        if (empty.numTracks.equals(numTracks)) {
            numTracks = filledMetadata.numTracks;
        }
        if (empty.genre.equals(genre)) {
            genre = filledMetadata.genre;
        }
        if (empty.duration.equals(duration)) {
            duration = filledMetadata.duration;
        }
        if (image == null) {
            image = filledMetadata.image;
        }
    }

    /**
     * A Builder object to populate a Metadata from various different Media Framework objects
     */
    public static class Builder {
        private Metadata mMetadata = new Metadata();
        private Context mContext = null;

        /**
         * Set the Media ID fot the Metadata Object
         */
        public Builder setMediaId(String id) {
            mMetadata.mediaId = id;
            return this;
        }

        /**
         * Set the context this builder should use when resolving images
         */
        public Builder useContext(Context context) {
            mContext = context;
            return this;
        }

        /**
         * Extract the fields from a MediaMetadata object into a Metadata, if they exist
         */
        public Builder fromMediaMetadata(MediaMetadata data) {
            if (data == null) return this;

            // First, use the basic description available with the MediaMetadata
            fromMediaDescription(data.getDescription());

            // Then, replace with better data if available on the MediaMetadata
            if (data.containsKey(MediaMetadata.METADATA_KEY_MEDIA_ID)) {
                mMetadata.mediaId = data.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);
            }
            if (data.containsKey(MediaMetadata.METADATA_KEY_TITLE)) {
                mMetadata.title = data.getString(MediaMetadata.METADATA_KEY_TITLE);
            }
            if (data.containsKey(MediaMetadata.METADATA_KEY_ARTIST)) {
                mMetadata.artist = data.getString(MediaMetadata.METADATA_KEY_ARTIST);
            }
            if (data.containsKey(MediaMetadata.METADATA_KEY_ALBUM)) {
                mMetadata.album = data.getString(MediaMetadata.METADATA_KEY_ALBUM);
            }
            if (data.containsKey(MediaMetadata.METADATA_KEY_TRACK_NUMBER)) {
                mMetadata.trackNum = "" + data.getLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER);
            }
            if (data.containsKey(MediaMetadata.METADATA_KEY_NUM_TRACKS)) {
                mMetadata.numTracks = "" + data.getLong(MediaMetadata.METADATA_KEY_NUM_TRACKS);
            }
            if (data.containsKey(MediaMetadata.METADATA_KEY_GENRE)) {
                mMetadata.genre = data.getString(MediaMetadata.METADATA_KEY_GENRE);
            }
            if (data.containsKey(MediaMetadata.METADATA_KEY_DURATION)) {
                mMetadata.duration = "" + data.getLong(MediaMetadata.METADATA_KEY_DURATION);
            }
            if ((mContext != null && Util.areUriImagesSupported(mContext)
                    && (data.containsKey(MediaMetadata.METADATA_KEY_ART_URI)
                    || data.containsKey(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                    || data.containsKey(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)))
                    || data.containsKey(MediaMetadata.METADATA_KEY_ART)
                    || data.containsKey(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    || data.containsKey(MediaMetadata.METADATA_KEY_DISPLAY_ICON)) {
                mMetadata.image = new Image(mContext, data);
            }
            return this;
        }

        /**
         * Extract the fields from a MediaItem object into a Metadata, if they exist
         */
        public Builder fromMediaItem(MediaItem item) {
            if (item == null) return this;
            return fromMediaDescription(item.getDescription()).setMediaId(item.getMediaId());
        }

        /**
         * Extract the fields from a MediaDescription object into a Metadata, if they exist
         */
        public Builder fromMediaDescription(MediaDescription desc) {
            if (desc == null) return this;

            // Default the following mapping if they exist
            if (desc.getTitle() != null) mMetadata.title = desc.getTitle().toString();
            if (desc.getSubtitle() != null) mMetadata.artist = desc.getSubtitle().toString();
            if (desc.getDescription() != null) mMetadata.album = desc.getDescription().toString();

            // Check for artwork
            if (desc.getIconBitmap() != null) {
                mMetadata.image = new Image(mContext, desc.getIconBitmap());
            } else if (mContext != null && Util.areUriImagesSupported(mContext)
                    && desc.getIconUri() != null) {
                mMetadata.image = new Image(mContext, desc.getIconUri());
            }

            // Then, check the extras in the description for even better data
            return fromBundle(desc.getExtras()).setMediaId(desc.getMediaId());
        }

        /**
         * Extract the fields from a MediaSession.QueueItem object into a Metadata, if they exist
         */
        public Builder fromQueueItem(MediaSession.QueueItem item) {
            if (item == null) return this;
            return fromMediaDescription(item.getDescription());
        }

        /**
         * Extract the fields from a Bundle of MediaMetadata constants into a Metadata, if they
         * exist
         */
        public Builder fromBundle(Bundle bundle) {
            if (bundle == null) return this;
            if (bundle.containsKey(MediaMetadata.METADATA_KEY_MEDIA_ID)) {
                mMetadata.mediaId = bundle.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);
            }
            if (bundle.containsKey(MediaMetadata.METADATA_KEY_TITLE)) {
                mMetadata.title = bundle.getString(MediaMetadata.METADATA_KEY_TITLE);
            }
            if (bundle.containsKey(MediaMetadata.METADATA_KEY_ARTIST)) {
                mMetadata.artist = bundle.getString(MediaMetadata.METADATA_KEY_ARTIST);
            }
            if (bundle.containsKey(MediaMetadata.METADATA_KEY_ALBUM)) {
                mMetadata.album = bundle.getString(MediaMetadata.METADATA_KEY_ALBUM);
            }
            if (bundle.containsKey(MediaMetadata.METADATA_KEY_TRACK_NUMBER)) {
                mMetadata.trackNum = "" + bundle.getLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER);
            }
            if (bundle.containsKey(MediaMetadata.METADATA_KEY_NUM_TRACKS)) {
                mMetadata.numTracks = "" + bundle.getLong(MediaMetadata.METADATA_KEY_NUM_TRACKS);
            }
            if (bundle.containsKey(MediaMetadata.METADATA_KEY_GENRE)) {
                mMetadata.genre = bundle.getString(MediaMetadata.METADATA_KEY_GENRE);
            }
            if (bundle.containsKey(MediaMetadata.METADATA_KEY_DURATION)) {
                mMetadata.duration = "" + bundle.getLong(MediaMetadata.METADATA_KEY_DURATION);
            }
            if ((mContext != null && Util.areUriImagesSupported(mContext)
                    && (bundle.containsKey(MediaMetadata.METADATA_KEY_ART_URI)
                    || bundle.containsKey(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                    || bundle.containsKey(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)))
                    || bundle.containsKey(MediaMetadata.METADATA_KEY_ART)
                    || bundle.containsKey(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    || bundle.containsKey(MediaMetadata.METADATA_KEY_DISPLAY_ICON)) {
                mMetadata.image = new Image(mContext, bundle);
            }
            return this;
        }

        /**
         * Elect to use default values in the Metadata in place of any missing values
         */
        public Builder useDefaults() {
            if (mMetadata.mediaId == null) {
                mMetadata.mediaId = EMPTY_MEDIA_ID;
            }
            if (mMetadata.title == null) {
                mMetadata.title =
                        mContext != null ? mContext.getString(R.string.not_provided)
                                : EMPTY_TITLE;
            }
            if (mMetadata.artist == null) mMetadata.artist = EMPTY_ARTIST;
            if (mMetadata.album == null) mMetadata.album = EMPTY_ALBUM;
            if (mMetadata.trackNum == null) mMetadata.trackNum = EMPTY_TRACK_NUM;
            if (mMetadata.numTracks == null) mMetadata.numTracks = EMPTY_NUM_TRACKS;
            if (mMetadata.genre == null) mMetadata.genre = EMPTY_GENRE;
            if (mMetadata.duration == null) mMetadata.duration = EMPTY_DURATION;
            // The default value chosen for an image is null. Update here if we pick something else
            return this;
        }

        /**
         * Get the final Metadata objects you're building
         */
        public Metadata build() {
            return mMetadata.clone();
        }
    }
}
