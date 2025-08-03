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

final class CoreAudioRoutingUtils {

    static final List<AudioVolumeGroup> VOLUME_GROUPS;
    static final List<AudioProductStrategy> PRODUCT_STRATEGIES;

    static final AudioAttributes MUSIC_ATTRIBUTES = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build();
    static final int MUSIC_STRATEGY_ID = 777;
    static final String MUSIC_CONTEXT_NAME = "MUSIC_CONTEXT";
    static final int MUSIC_GROUP_ID = 666;
    static final String MUSIC_GROUP_NAME = "MUSIC_GROUP";
    static final int MUSIC_AM_INIT_INDEX = 8;
    static final int MUSIC_MIN_INDEX = 0;
    static final int MUSIC_MAX_INDEX = 40;
    static final AudioProductStrategy MUSIC_STRATEGY;
    static final AudioVolumeGroup MUSIC_GROUP;

    static final AudioAttributes NAV_ATTRIBUTES = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build();
    static final int NAV_STRATEGY_ID = 99;
    static final String NAV_CONTEXT_NAME = "NAV_CONTEXT";
    static final int NAV_GROUP_ID = 8;
    static final String NAV_GROUP_NAME = "NAV_GROUP";
    static final int NAV_AM_INIT_INDEX = 8;
    static final int NAV_MIN_INDEX = 5;
    static final int NAV_MAX_INDEX = 35;
    static final AudioProductStrategy NAV_STRATEGY;
    static final AudioVolumeGroup NAV_GROUP;

    static final AudioAttributes OEM_ATTRIBUTES;
    static final int OEM_STRATEGY_ID = 1979;
    static final String OEM_CONTEXT_NAME = "OEM_CONTEXT";
    static final int OEM_GROUP_ID = 55;
    static final String OEM_GROUP_NAME = "OEM_GROUP";
    static final int OEM_MIN_INDEX = 1;
    static final int OEM_MAX_INDEX = 15;
    static final int OEM_AM_INIT_INDEX = 8;
    static final String OEM_FORMATTED_TAGS = "oem=extension_1979";
    static final AudioProductStrategy OEM_STRATEGY;
    static final AudioVolumeGroup OEM_GROUP;

    static final int INVALID_STRATEGY_ID = 999999;
    static final int INVALID_GROUP_ID = 999999;
    static final String INVALID_GROUP_NAME = "INVALID_GROUP";

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

        parcel.writeInt(/* nb attributes groups= */ 1);
        parcel.writeInt(/* volumeGroupId= */ MUSIC_GROUP_ID);
        parcel.writeInt(/* stream type= */ 0);
        parcel.writeInt(/* nb attributes= */ 2);

        parcel.writeInt(/* mUsage= */ AudioAttributes.USAGE_MEDIA);
        parcel.writeInt(/* mContentType= */ AudioAttributes.CONTENT_TYPE_MUSIC);
        parcel.writeInt(/* mSource= */ MediaRecorder.AudioSource.AUDIO_SOURCE_INVALID);
        parcel.writeInt(/* mFlags= */ AudioAttributes.FLAG_MUTE_HAPTIC);
        parcel.writeInt(AudioAttributes.FLATTEN_TAGS);
        parcel.writeString(/* mFormattedTags= */ "");
        parcel.writeInt(/* ATTR_PARCEL_IS_NULL_BUNDLE= */ -1977);

        parcel.writeInt(/* mUsage= */ AudioAttributes.USAGE_UNKNOWN);
        parcel.writeInt(/* mContentType= */ AudioAttributes.CONTENT_TYPE_UNKNOWN);
        parcel.writeInt(/* mSource= */ MediaRecorder.AudioSource.AUDIO_SOURCE_INVALID);
        parcel.writeInt(/* mFlags= */ AudioAttributes.FLAG_MUTE_HAPTIC);
        parcel.writeInt(AudioAttributes.FLATTEN_TAGS);
        parcel.writeString(/* mFormattedTags= */ "");
        parcel.writeInt(/* ATTR_PARCEL_IS_NULL_BUNDLE= */ -1977);

        parcel.setDataPosition(0);
        MUSIC_STRATEGY = AudioProductStrategy.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        parcel = Parcel.obtain();
        // marshall AudioProductStrategy data
        parcel.writeString(NAV_CONTEXT_NAME);
        parcel.writeInt(NAV_STRATEGY_ID);

        parcel.writeInt(/* nb attributes groups= */ 1);
        parcel.writeInt(/* volumeGroupId= */ NAV_GROUP_ID);
        parcel.writeInt(/* stream type= */ 0);
        parcel.writeInt(/* nb attributes= */ 2);

        parcel.writeInt(/* mUsage= */ AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE);
        parcel.writeInt(/* mContentType= */ AudioAttributes.CONTENT_TYPE_SPEECH);
        parcel.writeInt(/* mSource= */ MediaRecorder.AudioSource.AUDIO_SOURCE_INVALID);
        parcel.writeInt(/* mFlags= */ AudioAttributes.FLAG_MUTE_HAPTIC);
        parcel.writeInt(AudioAttributes.FLATTEN_TAGS);
        parcel.writeString(/* mFormattedTags= */ "");
        parcel.writeInt(/* ATTR_PARCEL_IS_NULL_BUNDLE= */ -1977);

        parcel.writeInt(/* mUsage= */ AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE);
        parcel.writeInt(/* mContentType= */ AudioAttributes.CONTENT_TYPE_UNKNOWN);
        parcel.writeInt(/* mSource= */ MediaRecorder.AudioSource.AUDIO_SOURCE_INVALID);
        parcel.writeInt(/* mFlags= */ AudioAttributes.FLAG_MUTE_HAPTIC);
        parcel.writeInt(AudioAttributes.FLATTEN_TAGS);
        parcel.writeString(/* mFormattedTags= */ "");
        parcel.writeInt(/* ATTR_PARCEL_IS_NULL_BUNDLE= */ -1977);

        parcel.setDataPosition(0);
        NAV_STRATEGY = AudioProductStrategy.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        parcel = Parcel.obtain();
        // marshall AudioProductStrategy data
        parcel.writeString(OEM_CONTEXT_NAME);
        parcel.writeInt(OEM_STRATEGY_ID);

        parcel.writeInt(/* nb attributes groups= */ 1);
        parcel.writeInt(/* volumeGroupId= */ OEM_GROUP_ID);
        parcel.writeInt(/* stream type= */ 0);
        parcel.writeInt(/* nb attributes= */ 1);
        parcel.writeInt(/* mUsage= */ AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE);
        parcel.writeInt(/* mContentType= */ AudioAttributes.CONTENT_TYPE_SPEECH);
        parcel.writeInt(/* mSource= */ MediaRecorder.AudioSource.AUDIO_SOURCE_INVALID);
        parcel.writeInt(/* mFlags= */ AudioAttributes.FLAG_MUTE_HAPTIC);
        parcel.writeInt(AudioAttributes.FLATTEN_TAGS);
        parcel.writeString(/* mFormattedTags= */ OEM_FORMATTED_TAGS);
        parcel.writeInt(/* ATTR_PARCEL_IS_NULL_BUNDLE= */ -1977);

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

        parcel.writeInt(/* nb attributes= */ 2);
        parcel.writeInt(/* mUsage= */ AudioAttributes.USAGE_MEDIA);
        parcel.writeInt(/* mContentType= */ AudioAttributes.CONTENT_TYPE_MUSIC);
        parcel.writeInt(/* mSource= */ MediaRecorder.AudioSource.AUDIO_SOURCE_INVALID);
        parcel.writeInt(/* mFlags= */ AudioAttributes.FLAG_MUTE_HAPTIC);
        parcel.writeInt(AudioAttributes.FLATTEN_TAGS);
        parcel.writeString(/* mFormattedTags= */ "");
        parcel.writeInt(/* ATTR_PARCEL_IS_NULL_BUNDLE= */ -1977);

        parcel.writeInt(/* mUsage= */ AudioAttributes.USAGE_UNKNOWN);
        parcel.writeInt(/* mContentType= */ AudioAttributes.CONTENT_TYPE_UNKNOWN);
        parcel.writeInt(/* mSource= */ MediaRecorder.AudioSource.AUDIO_SOURCE_INVALID);
        parcel.writeInt(/* mFlags= */ AudioAttributes.FLAG_MUTE_HAPTIC);
        parcel.writeInt(AudioAttributes.FLATTEN_TAGS);
        parcel.writeString(/* mFormattedTags= */ "");
        parcel.writeInt(/* ATTR_PARCEL_IS_NULL_BUNDLE= */ -1977);

        parcel.writeInt(/* nb stream types= */ 0);

        parcel.setDataPosition(0);
        MUSIC_GROUP = AudioVolumeGroup.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        parcel = Parcel.obtain();
        // marshall AudioProductStrategy data
        parcel.writeString(NAV_GROUP_NAME);
        parcel.writeInt(NAV_GROUP_ID);

        parcel.writeInt(/* nb attributes= */ 1);
        parcel.writeInt(/* mUsage= */ AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE);
        parcel.writeInt(/* mContentType= */ AudioAttributes.CONTENT_TYPE_SPEECH);
        parcel.writeInt(/* mSource= */ MediaRecorder.AudioSource.AUDIO_SOURCE_INVALID);
        parcel.writeInt(/* mFlags= */ AudioAttributes.FLAG_MUTE_HAPTIC);
        parcel.writeInt(AudioAttributes.FLATTEN_TAGS);
        parcel.writeString(/* mFormattedTags= */ "");
        parcel.writeInt(/* ATTR_PARCEL_IS_NULL_BUNDLE= */ -1977);

        parcel.writeInt(/* nb stream types= */ 0);

        parcel.setDataPosition(0);
        NAV_GROUP = AudioVolumeGroup.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        parcel = Parcel.obtain();
        // marshall AudioProductStrategy data
        parcel.writeString(OEM_GROUP_NAME);
        parcel.writeInt(OEM_GROUP_ID);

        parcel.writeInt(/* nb attributes= */ 1);
        parcel.writeInt(/* mUsage= */ AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE);
        parcel.writeInt(/* mContentType= */ AudioAttributes.CONTENT_TYPE_SPEECH);
        parcel.writeInt(/* mSource= */ MediaRecorder.AudioSource.AUDIO_SOURCE_INVALID);
        parcel.writeInt(/* mFlags= */ AudioAttributes.FLAG_MUTE_HAPTIC);
        parcel.writeInt(AudioAttributes.FLATTEN_TAGS);
        parcel.writeString(/* mFormattedTags= */ "oem=extension_1979");
        parcel.writeInt(/* ATTR_PARCEL_IS_NULL_BUNDLE= */ -1977);

        parcel.writeInt(/* nb stream types= */ 0);

        parcel.setDataPosition(0);
        OEM_GROUP = AudioVolumeGroup.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        VOLUME_GROUPS = List.of(MUSIC_GROUP, NAV_GROUP, OEM_GROUP);
    }

    private CoreAudioRoutingUtils() {
        throw new UnsupportedOperationException("CoreAudioRoutingUtils class is non instantiable");
    }

    static List<AudioVolumeGroup> getVolumeGroups() {
        return VOLUME_GROUPS;
    }

    static List<AudioProductStrategy> getProductStrategies() {
        return PRODUCT_STRATEGIES;
    }

    static List<CarAudioContextInfo> getCarAudioContextInfos() {
        List<CarAudioContextInfo> carAudioContextInfos = new ArrayList<>(3);

        carAudioContextInfos.add(new CarAudioContextInfo(
                new AudioAttributes[] { MUSIC_ATTRIBUTES }, MUSIC_CONTEXT_NAME,
                MUSIC_STRATEGY_ID));

        carAudioContextInfos.add(new CarAudioContextInfo(
                new AudioAttributes[] { NAV_ATTRIBUTES }, NAV_CONTEXT_NAME, NAV_STRATEGY_ID));

        carAudioContextInfos.add(new CarAudioContextInfo(
                new AudioAttributes[] { OEM_ATTRIBUTES }, OEM_CONTEXT_NAME, OEM_STRATEGY_ID));

        return carAudioContextInfos;
    }
}
