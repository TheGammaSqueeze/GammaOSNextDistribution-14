/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.car.internal.annotation;

import static android.media.AudioAttributes.USAGE_ALARM;
import static android.media.AudioAttributes.USAGE_ANNOUNCEMENT;
import static android.media.AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY;
import static android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
import static android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION;
import static android.media.AudioAttributes.USAGE_ASSISTANT;
import static android.media.AudioAttributes.USAGE_CALL_ASSISTANT;
import static android.media.AudioAttributes.USAGE_EMERGENCY;
import static android.media.AudioAttributes.USAGE_GAME;
import static android.media.AudioAttributes.USAGE_MEDIA;
import static android.media.AudioAttributes.USAGE_NOTIFICATION;
import static android.media.AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_DELAYED;
import static android.media.AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT;
import static android.media.AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_REQUEST;
import static android.media.AudioAttributes.USAGE_NOTIFICATION_EVENT;
import static android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE;
import static android.media.AudioAttributes.USAGE_SAFETY;
import static android.media.AudioAttributes.USAGE_UNKNOWN;
import static android.media.AudioAttributes.USAGE_VEHICLE_STATUS;
import static android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION;
import static android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING;

import android.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Tells the audio attribute usage (i.e. what type of audio source to use for playback)
 *
 * Copied from frameworks/base/media/java/android/media/AudioAttributes.java
 *
 * @hide
 */
@IntDef({
        USAGE_UNKNOWN,
        USAGE_MEDIA,
        USAGE_VOICE_COMMUNICATION,
        USAGE_VOICE_COMMUNICATION_SIGNALLING,
        USAGE_ALARM,
        USAGE_NOTIFICATION,
        USAGE_NOTIFICATION_RINGTONE,
        USAGE_NOTIFICATION_COMMUNICATION_REQUEST,
        USAGE_NOTIFICATION_COMMUNICATION_INSTANT,
        USAGE_NOTIFICATION_COMMUNICATION_DELAYED,
        USAGE_NOTIFICATION_EVENT,
        USAGE_ASSISTANCE_ACCESSIBILITY,
        USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
        USAGE_ASSISTANCE_SONIFICATION,
        USAGE_GAME,
        USAGE_ASSISTANT,
        USAGE_CALL_ASSISTANT,
        USAGE_EMERGENCY,
        USAGE_SAFETY,
        USAGE_VEHICLE_STATUS,
        USAGE_ANNOUNCEMENT,
})
@Retention(RetentionPolicy.SOURCE)
public @interface AttributeUsage {}
