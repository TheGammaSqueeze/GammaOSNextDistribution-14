/*
 * Copyright (C) 2014 The Android Open Source Project
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

#ifndef __EXYNOS_AUDIOHAL_STREAM_H__
#define __EXYNOS_AUDIOHAL_STREAM_H__

/*
 * Audio Streams based on Audio Profile in Audio Policy Configuration
 */
typedef enum {
    ASTREAM_MIN                   = 0,

    ASTREAM_PLAYBACK_PRIMARY      = 0,  // For Primary Output Profile
    ASTREAM_PLAYBACK_FAST,              // For Fast Output Profile
    ASTREAM_PLAYBACK_DEEP_BUFFER,       // For Deep Buffer Output Profile
    ASTREAM_PLAYBACK_LOW_LATENCY,       // For Low Latency Output Profile
    ASTREAM_PLAYBACK_COMPR_OFFLOAD,     // For Compress Offload Profile
    ASTREAM_PLAYBACK_MMAP,              // For MMAP NoIRQ Output Profile
    ASTREAM_PLAYBACK_USB_DEVICE,        // For USB Output Profile
    ASTREAM_PLAYBACK_AUX_DIGITAL,       // For HDMI/DP Profile

    ASTREAM_CAPTURE_PRIMARY,            // For Primary Input Profile
    ASTREAM_CAPTURE_CALL,               // For Call Recording
    ASTREAM_CAPTURE_LOW_LATENCY,        // For Low Latency Input Profile
    ASTREAM_CAPTURE_MMAP,               // For MMAP NoIRQ Input Profile
    ASTREAM_CAPTURE_USB_DEVICE,         // For USB Input Profile
    ASTREAM_CAPTURE_FM,                 // For FM Radio Recording
#ifdef SUPPORT_STHAL_INTERFACE
    ASTREAM_CAPTURE_HOTWORD,            // For VTS seamless Input Profile
#endif

    ASTREAM_NONE,
    ASTREAM_MAX,
    ASTREAM_CNT                   = ASTREAM_MAX
} audio_stream_type;

#endif  // __EXYNOS_AUDIOHAL_STREAM_H__
