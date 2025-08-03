/*
 * Copyright (C) 2023 The Android Open Source Project
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

#pragma once

#include <stddef.h>
#include <stdint.h>

/* maximum audio device address length */
#define AUDIO_DEVICE_MAX_ADDRESS_LEN 32

/* Audio attributes */
#define AUDIO_ATTRIBUTES_TAGS_MAX_SIZE 256

static const char AUDIO_ATTRIBUTES_TAGS_SEPARATOR = ';';

typedef enum {
  AUDIO_SOURCE_DEFAULT = 0,
  AUDIO_SOURCE_MIC = 1,
  AUDIO_SOURCE_VOICE_UPLINK = 2,
  AUDIO_SOURCE_VOICE_DOWNLINK = 3,
  AUDIO_SOURCE_VOICE_CALL = 4,
  AUDIO_SOURCE_CAMCORDER = 5,
  AUDIO_SOURCE_VOICE_RECOGNITION = 6,
  AUDIO_SOURCE_VOICE_COMMUNICATION = 7,
  AUDIO_SOURCE_REMOTE_SUBMIX = 8,
  AUDIO_SOURCE_UNPROCESSED = 9,
  AUDIO_SOURCE_VOICE_PERFORMANCE = 10,
  AUDIO_SOURCE_ECHO_REFERENCE = 1997,
  AUDIO_SOURCE_FM_TUNER = 1998,
  AUDIO_SOURCE_HOTWORD = 1999,
  AUDIO_SOURCE_INVALID = -1,
} audio_source_t;

typedef enum {
  AUDIO_CONTENT_TYPE_UNKNOWN = 0u,
  AUDIO_CONTENT_TYPE_SPEECH = 1u,
  AUDIO_CONTENT_TYPE_MUSIC = 2u,
  AUDIO_CONTENT_TYPE_MOVIE = 3u,
  AUDIO_CONTENT_TYPE_SONIFICATION = 4u,
} audio_content_type_t;

typedef enum {
  AUDIO_USAGE_UNKNOWN = 0,
  AUDIO_USAGE_MEDIA = 1,
  AUDIO_USAGE_VOICE_COMMUNICATION = 2,
  AUDIO_USAGE_VOICE_COMMUNICATION_SIGNALLING = 3,
  AUDIO_USAGE_ALARM = 4,
  AUDIO_USAGE_NOTIFICATION = 5,
  AUDIO_USAGE_NOTIFICATION_TELEPHONY_RINGTONE = 6,
  AUDIO_USAGE_NOTIFICATION_COMMUNICATION_REQUEST = 7,
  AUDIO_USAGE_NOTIFICATION_COMMUNICATION_INSTANT = 8,
  AUDIO_USAGE_NOTIFICATION_COMMUNICATION_DELAYED = 9,
  AUDIO_USAGE_NOTIFICATION_EVENT = 10,
  AUDIO_USAGE_ASSISTANCE_ACCESSIBILITY = 11,
  AUDIO_USAGE_ASSISTANCE_NAVIGATION_GUIDANCE = 12,
  AUDIO_USAGE_ASSISTANCE_SONIFICATION = 13,
  AUDIO_USAGE_GAME = 14,
  AUDIO_USAGE_VIRTUAL_SOURCE = 15,
  AUDIO_USAGE_ASSISTANT = 16,
  AUDIO_USAGE_CALL_ASSISTANT = 17,
  AUDIO_USAGE_EMERGENCY = 1000,
  AUDIO_USAGE_SAFETY = 1001,
  AUDIO_USAGE_VEHICLE_STATUS = 1002,
  AUDIO_USAGE_ANNOUNCEMENT = 1003,
} audio_usage_t;

typedef enum {
  AUDIO_DEVICE_DEFAULT = 0,
} audio_devices_t;

// The "channel mask" enum is comprised of discrete channels,
// their combinations (masks), and special values.
typedef enum : uint32_t {
  AUDIO_CHANNEL_REPRESENTATION_POSITION = 0x0u,
  AUDIO_CHANNEL_REPRESENTATION_INDEX = 0x2u,
  AUDIO_CHANNEL_NONE = 0x0u,
  AUDIO_CHANNEL_INVALID = 0xC0000000u,

  AUDIO_CHANNEL_OUT_FRONT_LEFT = 0x1u,
  AUDIO_CHANNEL_OUT_FRONT_RIGHT = 0x2u,
  AUDIO_CHANNEL_OUT_FRONT_CENTER = 0x4u,
  AUDIO_CHANNEL_OUT_LOW_FREQUENCY = 0x8u,
  AUDIO_CHANNEL_OUT_BACK_LEFT = 0x10u,
  AUDIO_CHANNEL_OUT_BACK_RIGHT = 0x20u,
  AUDIO_CHANNEL_OUT_FRONT_LEFT_OF_CENTER = 0x40u,
  AUDIO_CHANNEL_OUT_FRONT_RIGHT_OF_CENTER = 0x80u,
  AUDIO_CHANNEL_OUT_BACK_CENTER = 0x100u,
  AUDIO_CHANNEL_OUT_SIDE_LEFT = 0x200u,
  AUDIO_CHANNEL_OUT_SIDE_RIGHT = 0x400u,
  AUDIO_CHANNEL_OUT_TOP_CENTER = 0x800u,
  AUDIO_CHANNEL_OUT_TOP_FRONT_LEFT = 0x1000u,
  AUDIO_CHANNEL_OUT_TOP_FRONT_CENTER = 0x2000u,
  AUDIO_CHANNEL_OUT_TOP_FRONT_RIGHT = 0x4000u,
  AUDIO_CHANNEL_OUT_TOP_BACK_LEFT = 0x8000u,
  AUDIO_CHANNEL_OUT_TOP_BACK_CENTER = 0x10000u,
  AUDIO_CHANNEL_OUT_TOP_BACK_RIGHT = 0x20000u,
  AUDIO_CHANNEL_OUT_TOP_SIDE_LEFT = 0x40000u,
  AUDIO_CHANNEL_OUT_TOP_SIDE_RIGHT = 0x80000u,
  AUDIO_CHANNEL_OUT_HAPTIC_A = 0x20000000u,
  AUDIO_CHANNEL_OUT_HAPTIC_B = 0x10000000u,
  AUDIO_CHANNEL_OUT_MONO = 0x1u,    // OUT_FRONT_LEFT
  AUDIO_CHANNEL_OUT_STEREO = 0x3u,  // OUT_FRONT_LEFT | OUT_FRONT_RIGHT
  AUDIO_CHANNEL_OUT_2POINT1 =
      0xBu,  // OUT_FRONT_LEFT | OUT_FRONT_RIGHT | OUT_LOW_FREQUENCY
  AUDIO_CHANNEL_OUT_2POINT0POINT2 =
      0xC0003u,  // OUT_FRONT_LEFT | OUT_FRONT_RIGHT | OUT_TOP_SIDE_LEFT |
                 // OUT_TOP_SIDE_RIGHT
  AUDIO_CHANNEL_OUT_2POINT1POINT2 =
      0xC000Bu,  // OUT_FRONT_LEFT | OUT_FRONT_RIGHT | OUT_TOP_SIDE_LEFT |
                 // OUT_TOP_SIDE_RIGHT | OUT_LOW_FREQUENCY
  AUDIO_CHANNEL_OUT_3POINT0POINT2 =
      0xC0007u,  // OUT_FRONT_LEFT | OUT_FRONT_RIGHT | OUT_FRONT_CENTER |
                 // OUT_TOP_SIDE_LEFT | OUT_TOP_SIDE_RIGHT
  AUDIO_CHANNEL_OUT_3POINT1POINT2 =
      0xC000Fu,  // OUT_FRONT_LEFT | OUT_FRONT_RIGHT | OUT_FRONT_CENTER |
                 // OUT_TOP_SIDE_LEFT | OUT_TOP_SIDE_RIGHT | OUT_LOW_FREQUENCY
  AUDIO_CHANNEL_OUT_QUAD = 0x33u,        // OUT_FRONT_LEFT | OUT_FRONT_RIGHT |
                                         // OUT_BACK_LEFT | OUT_BACK_RIGHT
  AUDIO_CHANNEL_OUT_QUAD_BACK = 0x33u,   // OUT_QUAD
  AUDIO_CHANNEL_OUT_QUAD_SIDE = 0x603u,  // OUT_FRONT_LEFT | OUT_FRONT_RIGHT |
                                         // OUT_SIDE_LEFT | OUT_SIDE_RIGHT
  AUDIO_CHANNEL_OUT_SURROUND = 0x107u,   // OUT_FRONT_LEFT | OUT_FRONT_RIGHT |
                                         // OUT_FRONT_CENTER | OUT_BACK_CENTER
  AUDIO_CHANNEL_OUT_PENTA = 0x37u,       // OUT_QUAD | OUT_FRONT_CENTER
  AUDIO_CHANNEL_OUT_5POINT1 =
      0x3Fu,  // OUT_FRONT_LEFT | OUT_FRONT_RIGHT | OUT_FRONT_CENTER |
              // OUT_LOW_FREQUENCY | OUT_BACK_LEFT | OUT_BACK_RIGHT
  AUDIO_CHANNEL_OUT_5POINT1_BACK = 0x3Fu,  // OUT_5POINT1
  AUDIO_CHANNEL_OUT_5POINT1_SIDE =
      0x60Fu,  // OUT_FRONT_LEFT | OUT_FRONT_RIGHT | OUT_FRONT_CENTER |
               // OUT_LOW_FREQUENCY | OUT_SIDE_LEFT | OUT_SIDE_RIGHT
  AUDIO_CHANNEL_OUT_5POINT1POINT2 =
      0xC003Fu,  // OUT_5POINT1 | OUT_TOP_SIDE_LEFT | OUT_TOP_SIDE_RIGHT
  AUDIO_CHANNEL_OUT_5POINT1POINT4 =
      0x2D03Fu,  // OUT_5POINT1 | OUT_TOP_FRONT_LEFT | OUT_TOP_FRONT_RIGHT |
                 // OUT_TOP_BACK_LEFT | OUT_TOP_BACK_RIGHT
  AUDIO_CHANNEL_OUT_6POINT1 =
      0x13Fu,  // OUT_FRONT_LEFT | OUT_FRONT_RIGHT | OUT_FRONT_CENTER |
               // OUT_LOW_FREQUENCY | OUT_BACK_LEFT | OUT_BACK_RIGHT |
               // OUT_BACK_CENTER
  AUDIO_CHANNEL_OUT_7POINT1 =
      0x63Fu,  // OUT_FRONT_LEFT | OUT_FRONT_RIGHT | OUT_FRONT_CENTER |
               // OUT_LOW_FREQUENCY | OUT_BACK_LEFT | OUT_BACK_RIGHT |
               // OUT_SIDE_LEFT | OUT_SIDE_RIGHT
  AUDIO_CHANNEL_OUT_7POINT1POINT2 =
      0xC063Fu,  // OUT_7POINT1 | OUT_TOP_SIDE_LEFT | OUT_TOP_SIDE_RIGHT
  AUDIO_CHANNEL_OUT_7POINT1POINT4 =
      0x2D63Fu,  // OUT_7POINT1 | OUT_TOP_FRONT_LEFT | OUT_TOP_FRONT_RIGHT |
                 // OUT_TOP_BACK_LEFT | OUT_TOP_BACK_RIGHT
  AUDIO_CHANNEL_OUT_MONO_HAPTIC_A =
      0x20000001u,  // OUT_FRONT_LEFT | OUT_HAPTIC_A
  AUDIO_CHANNEL_OUT_STEREO_HAPTIC_A =
      0x20000003u,  // OUT_FRONT_LEFT | OUT_FRONT_RIGHT | OUT_HAPTIC_A
  AUDIO_CHANNEL_OUT_HAPTIC_AB = 0x30000000u,  // OUT_HAPTIC_A | OUT_HAPTIC_B
  AUDIO_CHANNEL_OUT_MONO_HAPTIC_AB =
      0x30000001u,  // OUT_FRONT_LEFT | OUT_HAPTIC_A | OUT_HAPTIC_B
  AUDIO_CHANNEL_OUT_STEREO_HAPTIC_AB =
      0x30000003u,  // OUT_FRONT_LEFT | OUT_FRONT_RIGHT | OUT_HAPTIC_A |
                    // OUT_HAPTIC_B

  AUDIO_CHANNEL_IN_LEFT = 0x4u,
  AUDIO_CHANNEL_IN_RIGHT = 0x8u,
  AUDIO_CHANNEL_IN_FRONT = 0x10u,
  AUDIO_CHANNEL_IN_BACK = 0x20u,
  AUDIO_CHANNEL_IN_LEFT_PROCESSED = 0x40u,
  AUDIO_CHANNEL_IN_RIGHT_PROCESSED = 0x80u,
  AUDIO_CHANNEL_IN_FRONT_PROCESSED = 0x100u,
  AUDIO_CHANNEL_IN_BACK_PROCESSED = 0x200u,
  AUDIO_CHANNEL_IN_PRESSURE = 0x400u,
  AUDIO_CHANNEL_IN_X_AXIS = 0x800u,
  AUDIO_CHANNEL_IN_Y_AXIS = 0x1000u,
  AUDIO_CHANNEL_IN_Z_AXIS = 0x2000u,
  AUDIO_CHANNEL_IN_BACK_LEFT = 0x10000u,
  AUDIO_CHANNEL_IN_BACK_RIGHT = 0x20000u,
  AUDIO_CHANNEL_IN_CENTER = 0x40000u,
  AUDIO_CHANNEL_IN_LOW_FREQUENCY = 0x100000u,
  AUDIO_CHANNEL_IN_TOP_LEFT = 0x200000u,
  AUDIO_CHANNEL_IN_TOP_RIGHT = 0x400000u,
  AUDIO_CHANNEL_IN_VOICE_UPLINK = 0x4000u,
  AUDIO_CHANNEL_IN_VOICE_DNLINK = 0x8000u,
  AUDIO_CHANNEL_IN_MONO = 0x10u,        // IN_FRONT
  AUDIO_CHANNEL_IN_STEREO = 0xCu,       // IN_LEFT | IN_RIGHT
  AUDIO_CHANNEL_IN_FRONT_BACK = 0x30u,  // IN_FRONT | IN_BACK
  AUDIO_CHANNEL_IN_6 = 0xFCu,  // IN_LEFT | IN_RIGHT | IN_FRONT | IN_BACK |
                               // IN_LEFT_PROCESSED | IN_RIGHT_PROCESSED
  AUDIO_CHANNEL_IN_2POINT0POINT2 =
      0x60000Cu,  // IN_LEFT | IN_RIGHT | IN_TOP_LEFT | IN_TOP_RIGHT
  AUDIO_CHANNEL_IN_2POINT1POINT2 =
      0x70000Cu,  // IN_LEFT | IN_RIGHT | IN_TOP_LEFT | IN_TOP_RIGHT |
                  // IN_LOW_FREQUENCY
  AUDIO_CHANNEL_IN_3POINT0POINT2 =
      0x64000Cu,  // IN_LEFT | IN_CENTER | IN_RIGHT | IN_TOP_LEFT | IN_TOP_RIGHT
  AUDIO_CHANNEL_IN_3POINT1POINT2 =
      0x74000Cu,  // IN_LEFT | IN_CENTER | IN_RIGHT | IN_TOP_LEFT | IN_TOP_RIGHT
                  // | IN_LOW_FREQUENCY
  AUDIO_CHANNEL_IN_5POINT1 =
      0x17000Cu,  // IN_LEFT | IN_CENTER | IN_RIGHT | IN_BACK_LEFT |
                  // IN_BACK_RIGHT | IN_LOW_FREQUENCY
  AUDIO_CHANNEL_IN_VOICE_UPLINK_MONO = 0x4010u,  // IN_VOICE_UPLINK | IN_MONO
  AUDIO_CHANNEL_IN_VOICE_DNLINK_MONO = 0x8010u,  // IN_VOICE_DNLINK | IN_MONO
  AUDIO_CHANNEL_IN_VOICE_CALL_MONO =
      0xC010u,  // IN_VOICE_UPLINK_MONO | IN_VOICE_DNLINK_MONO

  AUDIO_CHANNEL_COUNT_MAX = 30u,
  AUDIO_CHANNEL_INDEX_HDR = 0x80000000u,  // REPRESENTATION_INDEX << COUNT_MAX
  AUDIO_CHANNEL_INDEX_MASK_1 = 0x80000001u,   // INDEX_HDR | (1 << 1) - 1
  AUDIO_CHANNEL_INDEX_MASK_2 = 0x80000003u,   // INDEX_HDR | (1 << 2) - 1
  AUDIO_CHANNEL_INDEX_MASK_3 = 0x80000007u,   // INDEX_HDR | (1 << 3) - 1
  AUDIO_CHANNEL_INDEX_MASK_4 = 0x8000000Fu,   // INDEX_HDR | (1 << 4) - 1
  AUDIO_CHANNEL_INDEX_MASK_5 = 0x8000001Fu,   // INDEX_HDR | (1 << 5) - 1
  AUDIO_CHANNEL_INDEX_MASK_6 = 0x8000003Fu,   // INDEX_HDR | (1 << 6) - 1
  AUDIO_CHANNEL_INDEX_MASK_7 = 0x8000007Fu,   // INDEX_HDR | (1 << 7) - 1
  AUDIO_CHANNEL_INDEX_MASK_8 = 0x800000FFu,   // INDEX_HDR | (1 << 8) - 1
  AUDIO_CHANNEL_INDEX_MASK_9 = 0x800001FFu,   // INDEX_HDR | (1 << 9) - 1
  AUDIO_CHANNEL_INDEX_MASK_10 = 0x800003FFu,  // INDEX_HDR | (1 << 10) - 1
  AUDIO_CHANNEL_INDEX_MASK_11 = 0x800007FFu,  // INDEX_HDR | (1 << 11) - 1
  AUDIO_CHANNEL_INDEX_MASK_12 = 0x80000FFFu,  // INDEX_HDR | (1 << 12) - 1
  AUDIO_CHANNEL_INDEX_MASK_13 = 0x80001FFFu,  // INDEX_HDR | (1 << 13) - 1
  AUDIO_CHANNEL_INDEX_MASK_14 = 0x80003FFFu,  // INDEX_HDR | (1 << 14) - 1
  AUDIO_CHANNEL_INDEX_MASK_15 = 0x80007FFFu,  // INDEX_HDR | (1 << 15) - 1
  AUDIO_CHANNEL_INDEX_MASK_16 = 0x8000FFFFu,  // INDEX_HDR | (1 << 16) - 1
  AUDIO_CHANNEL_INDEX_MASK_17 = 0x8001FFFFu,  // INDEX_HDR | (1 << 17) - 1
  AUDIO_CHANNEL_INDEX_MASK_18 = 0x8003FFFFu,  // INDEX_HDR | (1 << 18) - 1
  AUDIO_CHANNEL_INDEX_MASK_19 = 0x8007FFFFu,  // INDEX_HDR | (1 << 19) - 1
  AUDIO_CHANNEL_INDEX_MASK_20 = 0x800FFFFFu,  // INDEX_HDR | (1 << 20) - 1
  AUDIO_CHANNEL_INDEX_MASK_21 = 0x801FFFFFu,  // INDEX_HDR | (1 << 21) - 1
  AUDIO_CHANNEL_INDEX_MASK_22 = 0x803FFFFFu,  // INDEX_HDR | (1 << 22) - 1
  AUDIO_CHANNEL_INDEX_MASK_23 = 0x807FFFFFu,  // INDEX_HDR | (1 << 23) - 1
  AUDIO_CHANNEL_INDEX_MASK_24 = 0x80FFFFFFu,  // INDEX_HDR | (1 << 24) - 1
} audio_channel_mask_t;

/** Metadata of a playback track for an in stream. */
typedef struct playback_track_metadata {
  audio_usage_t usage;
  audio_content_type_t content_type;
  float gain;  // Normalized linear volume. 0=silence, 1=0dbfs...
} playback_track_metadata_t;

/** Metadata of a record track for an out stream. */
typedef struct record_track_metadata {
  audio_source_t source;
  float gain;  // Normalized linear volume. 0=silence, 1=0dbfs...
               // For record tracks originating from a software patch, the
               // dest_device fields provide information about the downstream
               // device.
  audio_devices_t dest_device;
  char dest_device_address[AUDIO_DEVICE_MAX_ADDRESS_LEN];
} record_track_metadata_t;

typedef struct source_metadata {
  size_t track_count;
  /** Array of metadata of each track connected to this source. */
  struct playback_track_metadata* tracks;
} source_metadata_t;

typedef struct sink_metadata {
  size_t track_count;
  /** Array of metadata of each track connected to this sink. */
  struct record_track_metadata* tracks;
} sink_metadata_t;

/** Metadata of a playback track for an in stream. */
typedef struct playback_track_metadata_v7 {
  struct playback_track_metadata base;
  audio_channel_mask_t channel_mask;
  char tags[AUDIO_ATTRIBUTES_TAGS_MAX_SIZE]; /* UTF8 */
} playback_track_metadata_v7_t;

/** Metadata of a record track for an out stream. */
typedef struct record_track_metadata_v7 {
  struct record_track_metadata base;
  audio_channel_mask_t channel_mask;
  char tags[AUDIO_ATTRIBUTES_TAGS_MAX_SIZE]; /* UTF8 */
} record_track_metadata_v7_t;

/* HAL version 3.2 and higher only. */
typedef struct source_metadata_v7 {
  size_t track_count;
  /** Array of metadata of each track connected to this source. */
  struct playback_track_metadata_v7* tracks;
} source_metadata_v7_t;

/* HAL version 3.2 and higher only. */
typedef struct sink_metadata_v7 {
  size_t track_count;
  /** Array of metadata of each track connected to this sink. */
  struct record_track_metadata_v7* tracks;
} sink_metadata_v7_t;