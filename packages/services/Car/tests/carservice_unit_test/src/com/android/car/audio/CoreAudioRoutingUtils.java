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

package com.android.car.audio;

import static com.google.common.collect.Sets.newHashSet;

import android.media.AudioAttributes;
import android.media.MediaRecorder;
import android.media.audiopolicy.AudioProductStrategy;
import android.media.audiopolicy.AudioVolumeGroup;
import android.os.Parcel;

import java.util.ArrayList;
import java.util.List;

public final class CoreAudioRoutingUtils {

    public static final int MUSIC_MIN_INDEX = 0;
    public static final int MUSIC_MAX_INDEX = 40;
    public static final int MUSIC_AM_INIT_INDEX = 8;
    public static final int NAV_MIN_INDEX = 5;
    public static final int NAV_MAX_INDEX = 35;
    public static final int OEM_MIN_INDEX = 1;
    public static final int OEM_MAX_INDEX = 15;
    public static final int MUSIC_CAR_GROUP_ID = 0;
    public static final int OEM_CAR_GROUP_ID = 2;
    public static final int NAV_CAR_GROUP_ID = 1;
    public static final String MUSIC_DEVICE_ADDRESS = "MUSIC_DEVICE_ADDRESS";
    public static final String NAV_DEVICE_ADDRESS = "NAV_DEVICE_ADDRESS";
    public static final String OEM_DEVICE_ADDRESS = "OEM_DEVICE_ADDRESS";
    public static final List<AudioVolumeGroup> VOLUME_GROUPS;
    public static final List<AudioProductStrategy> PRODUCT_STRATEGIES;

    public static final AudioAttributes MUSIC_ATTRIBUTES = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build();

    public static final AudioAttributes MOVIE_ATTRIBUTES = new AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
            .build();
    public static final int MUSIC_STRATEGY_ID = 777;
    public static final String MUSIC_CONTEXT_NAME = "MUSIC_CONTEXT";
    public static final int MUSIC_GROUP_ID = 666;
    public static final String MUSIC_GROUP_NAME = "MUSIC_GROUP";
    public static final AudioProductStrategy MUSIC_STRATEGY;
    public static final AudioVolumeGroup MUSIC_GROUP;
    public static final CarAudioContextInfo MEDIA_CONTEXT_INFO;

    public static final AudioAttributes NAV_ATTRIBUTES = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build();
    public static final int NAV_STRATEGY_ID = 99;
    public static final String NAV_CONTEXT_NAME = "NAV_CONTEXT";
    public static final int NAV_GROUP_ID = 8;
    public static final String NAV_GROUP_NAME = "NAV_GROUP";
    public static final AudioProductStrategy NAV_STRATEGY;
    public static final AudioVolumeGroup NAV_GROUP;
    public static final CarAudioContextInfo NAV_CONTEXT_INFO;

    public static final AudioAttributes OEM_ATTRIBUTES;
    public static final int OEM_STRATEGY_ID = 1979;
    public static final String OEM_CONTEXT_NAME = "OEM_CONTEXT";
    public static final int OEM_GROUP_ID = 55;
    public static final String OEM_GROUP_NAME = "OEM_GROUP";
    public static final String OEM_FORMATTED_TAGS = "oem=extension_1979";
    public static final AudioProductStrategy OEM_STRATEGY;
    public static final AudioVolumeGroup OEM_GROUP;
    public static final CarAudioContextInfo OEM_CONTEXT_INFO;

    static final String INVALID_CONTEXT_NAME = "INVALID_CONTEXT";
    static final int INVALID_STRATEGY_ID = 999999;
    static final int INVALID_GROUP_ID = 999999;
    static final String INVALID_GROUP_NAME = "INVALID_GROUP";

    static final int INVALID_STRATEGY = -1;

    static final AudioAttributes UNSUPPORTED_ATTRIBUTES = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build();

    static {
        OEM_ATTRIBUTES = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .replaceTags(newHashSet(OEM_FORMATTED_TAGS))
                .build();
        // Note: constructors are private, use marshalling public API to generate the mocks
        Parcel parcel = Parcel.obtain();
        // marshall AudioProductStrategy data
        parcel.writeString(MUSIC_CONTEXT_NAME);
        parcel.writeInt(MUSIC_STRATEGY_ID);

        // nb attributes groups
        parcel.writeInt(1);
        parcel.writeInt(MUSIC_GROUP_ID);
        // stream type
        parcel.writeInt(0);
        // nb attributes
        parcel.writeInt(2);

        parcel.writeInt(AudioAttributes.USAGE_MEDIA);
        parcel.writeInt(AudioAttributes.CONTENT_TYPE_MUSIC);
        parcel.writeInt(MediaRecorder.AudioSource.AUDIO_SOURCE_INVALID);
        parcel.writeInt(AudioAttributes.FLAG_MUTE_HAPTIC);
        parcel.writeInt(AudioAttributes.FLATTEN_TAGS);
        // mFormattedTags
        parcel.writeString("");
        // ATTR_PARCEL_IS_NULL_BUNDLE
        parcel.writeInt(-1977);

        parcel.writeInt(AudioAttributes.USAGE_UNKNOWN);
        parcel.writeInt(AudioAttributes.CONTENT_TYPE_UNKNOWN);
        parcel.writeInt(MediaRecorder.AudioSource.AUDIO_SOURCE_INVALID);
        parcel.writeInt(AudioAttributes.FLAG_MUTE_HAPTIC);
        parcel.writeInt(AudioAttributes.FLATTEN_TAGS);
        // mFormattedTags
        parcel.writeString("");
        // ATTR_PARCEL_IS_NULL_BUNDLE
        parcel.writeInt(-1977);

        parcel.setDataPosition(0);
        MUSIC_STRATEGY = AudioProductStrategy.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        parcel = Parcel.obtain();
        // marshall AudioProductStrategy data
        parcel.writeString(NAV_CONTEXT_NAME);
        parcel.writeInt(NAV_STRATEGY_ID);

        // nb attributes groups
        parcel.writeInt(1);
        parcel.writeInt(NAV_GROUP_ID);
        // stream type
        parcel.writeInt(0);
        // nb attributes
        parcel.writeInt(2);

        parcel.writeInt(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE);
        parcel.writeInt(AudioAttributes.CONTENT_TYPE_SPEECH);
        parcel.writeInt(MediaRecorder.AudioSource.AUDIO_SOURCE_INVALID);
        parcel.writeInt(AudioAttributes.FLAG_MUTE_HAPTIC);
        parcel.writeInt(AudioAttributes.FLATTEN_TAGS);
        // mFormattedTags
        parcel.writeString("");
        // ATTR_PARCEL_IS_NULL_BUNDLE
        parcel.writeInt(-1977);

        parcel.writeInt(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE);
        parcel.writeInt(AudioAttributes.CONTENT_TYPE_UNKNOWN);
        parcel.writeInt(MediaRecorder.AudioSource.AUDIO_SOURCE_INVALID);
        parcel.writeInt(AudioAttributes.FLAG_MUTE_HAPTIC);
        parcel.writeInt(AudioAttributes.FLATTEN_TAGS);
        // mFormattedTags
        parcel.writeString("");
        // ATTR_PARCEL_IS_NULL_BUNDLE
        parcel.writeInt(-1977);

        parcel.setDataPosition(0);
        NAV_STRATEGY = AudioProductStrategy.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        parcel = Parcel.obtain();
        // marshall AudioProductStrategy data
        parcel.writeString(OEM_CONTEXT_NAME);
        parcel.writeInt(OEM_STRATEGY_ID);

        // nb attributes groups
        parcel.writeInt(1);
        parcel.writeInt(OEM_GROUP_ID);
        // stream type
        parcel.writeInt(0);
        // nb attributes
        parcel.writeInt(1);
        parcel.writeInt(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE);
        parcel.writeInt(AudioAttributes.CONTENT_TYPE_SPEECH);
        parcel.writeInt(MediaRecorder.AudioSource.AUDIO_SOURCE_INVALID);
        parcel.writeInt(AudioAttributes.FLAG_MUTE_HAPTIC);
        parcel.writeInt(AudioAttributes.FLATTEN_TAGS);
        parcel.writeString(OEM_FORMATTED_TAGS);
        // ATTR_PARCEL_IS_NULL_BUNDLE
        parcel.writeInt(-1977);

        parcel.setDataPosition(0);
        OEM_STRATEGY = AudioProductStrategy.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        // Order matters, put the default in the middle to check default strategy is not selected
        // if not explicitly requested.
        PRODUCT_STRATEGIES = List.of(OEM_STRATEGY, MUSIC_STRATEGY, NAV_STRATEGY);

        parcel = Parcel.obtain();
        // marshall AudioVolumeGroup data
        parcel.writeString(MUSIC_GROUP_NAME);
        parcel.writeInt(MUSIC_GROUP_ID);

        // nb attributes
        parcel.writeInt(2);
        parcel.writeInt(AudioAttributes.USAGE_MEDIA);
        parcel.writeInt(AudioAttributes.CONTENT_TYPE_MUSIC);
        parcel.writeInt(MediaRecorder.AudioSource.AUDIO_SOURCE_INVALID);
        parcel.writeInt(AudioAttributes.FLAG_MUTE_HAPTIC);
        parcel.writeInt(AudioAttributes.FLATTEN_TAGS);
        // mFormattedTags
        parcel.writeString("");
        // ATTR_PARCEL_IS_NULL_BUNDLE
        parcel.writeInt(-1977);

        parcel.writeInt(AudioAttributes.USAGE_UNKNOWN);
        parcel.writeInt(AudioAttributes.CONTENT_TYPE_UNKNOWN);
        parcel.writeInt(MediaRecorder.AudioSource.AUDIO_SOURCE_INVALID);
        parcel.writeInt(AudioAttributes.FLAG_MUTE_HAPTIC);
        parcel.writeInt(AudioAttributes.FLATTEN_TAGS);
        // mFormattedTags
        parcel.writeString("");
        // ATTR_PARCEL_IS_NULL_BUNDLE
        parcel.writeInt(-1977);

        // nb stream types
        parcel.writeInt(0);

        parcel.setDataPosition(0);
        MUSIC_GROUP = AudioVolumeGroup.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        parcel = Parcel.obtain();
        // marshall AudioProductStrategy data
        parcel.writeString(NAV_GROUP_NAME);
        parcel.writeInt(NAV_GROUP_ID);

        // nb attributes
        parcel.writeInt(1);
        parcel.writeInt(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE);
        parcel.writeInt(AudioAttributes.CONTENT_TYPE_SPEECH);
        parcel.writeInt(MediaRecorder.AudioSource.AUDIO_SOURCE_INVALID);
        parcel.writeInt(AudioAttributes.FLAG_MUTE_HAPTIC);
        parcel.writeInt(AudioAttributes.FLATTEN_TAGS);
        // mFormattedTags
        parcel.writeString("");
        // ATTR_PARCEL_IS_NULL_BUNDLE
        parcel.writeInt(-1977);

        parcel.writeInt(/* nb stream types= */ 0);

        parcel.setDataPosition(0);
        NAV_GROUP = AudioVolumeGroup.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        parcel = Parcel.obtain();
        // marshall AudioProductStrategy data
        parcel.writeString(OEM_GROUP_NAME);
        parcel.writeInt(OEM_GROUP_ID);

        // nb attributes
        parcel.writeInt(1);
        parcel.writeInt(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE);
        parcel.writeInt(AudioAttributes.CONTENT_TYPE_SPEECH);
        parcel.writeInt(MediaRecorder.AudioSource.AUDIO_SOURCE_INVALID);
        parcel.writeInt(AudioAttributes.FLAG_MUTE_HAPTIC);
        parcel.writeInt(AudioAttributes.FLATTEN_TAGS);
        // mFormattedTags
        parcel.writeString("oem=extension_1979");
        // ATTR_PARCEL_IS_NULL_BUNDLE
        parcel.writeInt(-1977);

        // nb stream types
        parcel.writeInt(0);

        parcel.setDataPosition(0);
        OEM_GROUP = AudioVolumeGroup.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        VOLUME_GROUPS = List.of(MUSIC_GROUP, NAV_GROUP, OEM_GROUP);

        AudioAttributes[] oemAttributesArray = { OEM_ATTRIBUTES };
        AudioAttributes[] musicAttributesArray = { MUSIC_ATTRIBUTES, MOVIE_ATTRIBUTES };
        AudioAttributes[] navAttributesArray = { NAV_ATTRIBUTES };

        MEDIA_CONTEXT_INFO = new CarAudioContextInfo(musicAttributesArray, MUSIC_CONTEXT_NAME,
                MUSIC_STRATEGY_ID);
        NAV_CONTEXT_INFO = new CarAudioContextInfo(navAttributesArray,  NAV_CONTEXT_NAME,
                NAV_STRATEGY_ID);
        OEM_CONTEXT_INFO = new CarAudioContextInfo(oemAttributesArray, OEM_CONTEXT_NAME,
                OEM_STRATEGY_ID);
    }

    private CoreAudioRoutingUtils() {
        throw new UnsupportedOperationException("CoreAudioRoutingUtils class is non instantiable");
    }

    public static List<AudioVolumeGroup> getVolumeGroups() {
        return VOLUME_GROUPS;
    }

    public static List<AudioProductStrategy> getProductStrategies() {
        return PRODUCT_STRATEGIES;
    }

    public static List<CarAudioContextInfo> getCarAudioContextInfos() {
        List<CarAudioContextInfo> carAudioContextInfos = new ArrayList<>(3);

        carAudioContextInfos.add(MEDIA_CONTEXT_INFO);
        carAudioContextInfos.add(NAV_CONTEXT_INFO);
        carAudioContextInfos.add(OEM_CONTEXT_INFO);

        return carAudioContextInfos;
    }
}
