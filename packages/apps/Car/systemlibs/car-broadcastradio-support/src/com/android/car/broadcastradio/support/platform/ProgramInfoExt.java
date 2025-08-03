/**
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

package com.android.car.broadcastradio.support.platform;

import android.graphics.Bitmap;
import android.hardware.radio.ProgramSelector;
import android.hardware.radio.RadioManager;
import android.hardware.radio.RadioManager.ProgramInfo;
import android.hardware.radio.RadioMetadata;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.RatingCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Comparator;
import java.util.Objects;

/**
 * Proposed extensions to android.hardware.radio.RadioManager.ProgramInfo.
 *
 * They might eventually get pushed to the framework.
 */
public class ProgramInfoExt {
    private static final String TAG = "BcRadioApp.pinfoext";

    /**
     * If there is no suitable program name, return null instead of doing
     * a fallback to channel display name.
     */
    public static final int NAME_NO_CHANNEL_FALLBACK = 1 << 16;

    /**
     * Flags to control how to fetch program name with {@link #getProgramName}.
     *
     * Lower 16 bits are reserved for {@link ProgramSelectorExt.NameFlag}.
     */
    @IntDef(flag = true, value = {
        ProgramSelectorExt.NAME_NO_MODULATION,
        ProgramSelectorExt.NAME_MODULATION_ONLY,
        NAME_NO_CHANNEL_FALLBACK,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface NameFlag {}

    private static final char EN_DASH = '\u2013';
    private static final String TITLE_SEPARATOR = " " + EN_DASH + " ";

    private static final String[] PROGRAM_NAME_ORDER = new String[] {
        RadioMetadata.METADATA_KEY_PROGRAM_NAME,
        RadioMetadata.METADATA_KEY_DAB_COMPONENT_NAME,
        RadioMetadata.METADATA_KEY_DAB_SERVICE_NAME,
        RadioMetadata.METADATA_KEY_DAB_ENSEMBLE_NAME,
        RadioMetadata.METADATA_KEY_RDS_PS,
    };

    /**
     * Returns program name suitable to display.
     *
     * <p>If there is no program name, it falls back to channel name. Flags related to
     * the channel name display will be forwarded to the channel name generation method.
     *
     * @param info {@link ProgramInfo} to get name from
     * @param flags Fallback method
     * @param programNameOrder {@link RadioMetadata} metadata keys to pull from {@link ProgramInfo}
     * for the program name
     */
    @NonNull
    public static String getProgramName(@NonNull ProgramInfo info, @NameFlag int flags,
            @NonNull String[] programNameOrder) {
        Objects.requireNonNull(info, "info can not be null.");
        Objects.requireNonNull(programNameOrder, "programNameOrder can not be null");

        RadioMetadata meta = info.getMetadata();
        if (meta != null) {
            for (String key : programNameOrder) {
                String value = meta.getString(key);
                if (value != null) return value;
            }
        }

        if ((flags & NAME_NO_CHANNEL_FALLBACK) != 0) return "";

        ProgramSelector sel = info.getSelector();

        // if it's AM/FM program, prefer to display currently used AF frequency
        if (ProgramSelectorExt.isAmFmProgram(sel)) {
            ProgramSelector.Identifier phy = info.getPhysicallyTunedTo();
            if (phy != null && phy.getType() == ProgramSelector.IDENTIFIER_TYPE_AMFM_FREQUENCY) {
                String chName = ProgramSelectorExt.formatAmFmFrequency(phy.getValue(), flags);
                if (chName != null) return chName;
            }
        }

        String selName = ProgramSelectorExt.getDisplayName(sel, flags);
        if (selName != null) return selName;

        Log.w(TAG, "ProgramInfo without a name nor channel name");
        return "";
    }

    /**
     * Returns program name suitable to display.
     *
     * <p>If there is no program name, it falls back to channel name. Flags related to
     * the channel name display will be forwarded to the channel name generation method.
     */
    @NonNull
    public static String getProgramName(@NonNull ProgramInfo info, @NameFlag int flags) {
        return getProgramName(info, flags, PROGRAM_NAME_ORDER);
    }

    /**
     * Proposed reimplementation of {@link RadioManager.ProgramInfo#getMetadata}.
     *
     * As opposed to the original implementation, it never returns null.
     */
    public static @NonNull RadioMetadata getMetadata(@NonNull ProgramInfo info) {
        RadioMetadata meta = info.getMetadata();
        if (meta != null) return meta;

        /* Creating new Metadata object on each get won't be necessary after we
         * push this code to the framework. */
        return (new RadioMetadata.Builder()).build();
    }

    /**
     * Converts {@link ProgramInfo} to {@link MediaMetadataCompat} for displaying.
     *
     * <p>This method is meant to be used for displaying the currently playing station in
     *  {@link MediaSessionCompat}, only a subset of keys populated in {@link #toMediaMetadata}
     *  will be populated in this method.
     *
     * <ul>
     * The following keys will be populated in the {@link MediaMetadataCompat}:
     *  <li>{@link MediaMetadataCompat#METADATA_KEY_DISPLAY_TITLE}</li>
     *  <li>{@link MediaMetadataCompat#METADATA_KEY_DISPLAY_SUBTITLE}</li>
     *  <li>{@link MediaMetadataCompat#METADATA_KEY_ALBUM_ART}</li>
     *  <li>{@link MediaMetadataCompat#METADATA_KEY_USER_RATING}</li>
     * <ul/>
     *
     * @param info {@link ProgramInfo} to convert
     * @param isFavorite {@code true}, if a given program is a favorite
     * @param imageResolver metadata images resolver/cache
     * @param programNameOrder order of keys to look for program name in {@link ProgramInfo}
     * @return {@link MediaMetadataCompat} object
     */
    @NonNull
    public static MediaMetadataCompat toMediaDisplayMetadata(@NonNull ProgramInfo info,
            boolean isFavorite, @NonNull ImageResolver imageResolver,
            @NonNull String[] programNameOrder) {
        Objects.requireNonNull(info, "info can not be null.");
        Objects.requireNonNull(imageResolver, "imageResolver can not be null.");
        Objects.requireNonNull(programNameOrder, "programNameOrder can not be null.");

        MediaMetadataCompat.Builder bld = new MediaMetadataCompat.Builder();

        ProgramSelector selector;
        ProgramSelector.Identifier logicallyTunedTo = info.getLogicallyTunedTo();
        if (logicallyTunedTo != null && logicallyTunedTo.getType()
                == ProgramSelector.IDENTIFIER_TYPE_AMFM_FREQUENCY) {
            selector = ProgramSelectorExt.createAmFmSelector(logicallyTunedTo.getValue());
        } else {
            selector = info.getSelector();
        }
        String displayTitle = ProgramSelectorExt.getDisplayName(selector, info.getChannel());
        bld.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, displayTitle);
        String subtitle = getProgramName(info, /* flags= */ 0, programNameOrder);
        bld.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, subtitle);

        Bitmap bm = resolveAlbumArtBitmap(info.getMetadata(), imageResolver);
        if (bm != null) bld.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bm);

        bld.putRating(MediaMetadataCompat.METADATA_KEY_USER_RATING,
                RatingCompat.newHeartRating(isFavorite));

        return bld.build();
    }

    /**
     * Converts {@link ProgramInfo} to {@link MediaMetadataCompat}.
     *
     * <p>This method is meant to be used for currently playing station in
     * {@link MediaSessionCompat}.
     *
     * <ul>
     * The following keys will be populated in the {@link MediaMetadataCompat}:
     *  <li>{@link MediaMetadataCompat#METADATA_KEY_DISPLAY_TITLE}</li>
     *  <li>{@link MediaMetadataCompat#METADATA_KEY_TITLE}</li>
     *  <li>{@link MediaMetadataCompat#METADATA_KEY_ARTIST}</li>
     *  <li>{@link MediaMetadataCompat#METADATA_KEY_ALBUM}</li>
     *  <li>{@link MediaMetadataCompat#METADATA_KEY_DISPLAY_SUBTITLE}</li>
     *  <li>{@link MediaMetadataCompat#METADATA_KEY_ALBUM_ART}</li>
     *  <li>{@link MediaMetadataCompat#METADATA_KEY_USER_RATING}</li>
     * <ul/>
     *
     * @param info {@link ProgramInfo} to convert
     * @param isFavorite {@code true}, if a given program is a favorite
     * @param imageResolver metadata images resolver/cache
     * @return {@link MediaMetadataCompat} object
     */
    public static @NonNull MediaMetadataCompat toMediaMetadata(@NonNull ProgramInfo info,
            boolean isFavorite, @Nullable ImageResolver imageResolver) {
        MediaMetadataCompat.Builder bld = new MediaMetadataCompat.Builder();

        bld.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, getProgramName(info, 0));

        RadioMetadata meta = info.getMetadata();
        if (meta != null) {
            String title = meta.getString(RadioMetadata.METADATA_KEY_TITLE);
            if (title != null) {
                bld.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title);
            }
            String artist = meta.getString(RadioMetadata.METADATA_KEY_ARTIST);
            if (artist != null) {
                bld.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist);
            }
            String album = meta.getString(RadioMetadata.METADATA_KEY_ALBUM);
            if (album != null) {
                bld.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album);
            }
            if (title != null || artist != null) {
                String subtitle;
                if (title == null) {
                    subtitle = artist;
                } else if (artist == null) {
                    subtitle = title;
                } else {
                    subtitle = title + TITLE_SEPARATOR + artist;
                }
                bld.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, subtitle);
            }

            Bitmap bm = resolveAlbumArtBitmap(meta, imageResolver);
            if (bm != null) bld.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bm);
        }

        bld.putRating(MediaMetadataCompat.METADATA_KEY_USER_RATING,
                RatingCompat.newHeartRating(isFavorite));

        return bld.build();
    }

    private static Bitmap resolveAlbumArtBitmap(@NonNull RadioMetadata meta,
            @NonNull ImageResolver imageResolver) {
        long albumArtId = RadioMetadataExt.getGlobalBitmapId(meta, RadioMetadata.METADATA_KEY_ART);
        if (albumArtId != 0 && imageResolver != null) {
            return imageResolver.resolve(albumArtId);
        }
        return null;
    }
    public static class ProgramInfoComparator implements Comparator<RadioManager.ProgramInfo> {
        @Override
        public int compare(RadioManager.ProgramInfo info1, RadioManager.ProgramInfo info2) {
            Comparator<ProgramSelector> selectorComparator =
                    new ProgramSelectorExt.ProgramSelectorComparator();
            return selectorComparator.compare(info1.getSelector(), info2.getSelector());
        }
    }
}
