/*
 * Copyright (C) 2019 The Android Open Source Project
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

#ifndef MEDIACTSNATIVE_NATIVE_MEDIA_COMMON_H
#define MEDIACTSNATIVE_NATIVE_MEDIA_COMMON_H

#include <inttypes.h>
#include <string>
#include <vector>
#include <media/NdkMediaFormat.h>
#include <media/NdkMediaMuxer.h>

// Migrate this method to std::format when C++20 becomes available
template <typename... Args>
std::string StringFormat(const std::string& format, Args... args) {
    auto size = std::snprintf(nullptr, 0, format.c_str(), args...);
    if (size < 0) return std::string();
    std::vector<char> buffer(size + 1); // Add 1 for terminating null byte
    std::snprintf(buffer.data(), buffer.size(), format.c_str(), args...);
    return std::string(buffer.data(), size); // Exclude the terminating null byte
}

extern const char* AMEDIA_MIMETYPE_VIDEO_VP8;
extern const char* AMEDIA_MIMETYPE_VIDEO_VP9;
extern const char* AMEDIA_MIMETYPE_VIDEO_AV1;
extern const char* AMEDIA_MIMETYPE_VIDEO_AVC;
extern const char* AMEDIA_MIMETYPE_VIDEO_HEVC;
extern const char* AMEDIA_MIMETYPE_VIDEO_MPEG4;
extern const char* AMEDIA_MIMETYPE_VIDEO_H263;

extern const char* AMEDIA_MIMETYPE_AUDIO_AMR_NB;
extern const char* AMEDIA_MIMETYPE_AUDIO_AMR_WB;
extern const char* AMEDIA_MIMETYPE_AUDIO_AAC;
extern const char* AMEDIA_MIMETYPE_AUDIO_FLAC;
extern const char* AMEDIA_MIMETYPE_AUDIO_VORBIS;
extern const char* AMEDIA_MIMETYPE_AUDIO_OPUS;
extern const char* AMEDIA_MIMETYPE_AUDIO_RAW;

extern const float kRmsErrorTolerance;

extern const long kQDeQTimeOutUs;
extern const int kRetryLimit;

// TODO: make this extend itself automatically
constexpr int LOCAL_AMEDIAMUXER_OUTPUT_FORMAT_FIRST = AMEDIAMUXER_OUTPUT_FORMAT_MPEG_4;
constexpr int LOCAL_AMEDIAMUXER_OUTPUT_FORMAT_LAST = AMEDIAMUXER_OUTPUT_FORMAT_OGG;

// Color formats supported by encoder - should mirror supportedColorList
// from MediaCodecConstants.h (are these going to be deprecated)
constexpr int COLOR_FormatYUV420SemiPlanar = 21;
constexpr int COLOR_FormatYUV420Flexible = 0x7F420888;
constexpr int COLOR_FormatSurface = 0x7f000789;
constexpr int COLOR_FormatYUVP010 = 54;

// constants not defined in NDK
extern const char* TBD_AMEDIACODEC_PARAMETER_KEY_REQUEST_SYNC_FRAME;
extern const char* TBD_AMEDIACODEC_PARAMETER_KEY_VIDEO_BITRATE;
extern const char* COMPATIBLE_AMEDIAFORMAT_KEY_MAX_B_FRAMES;    // available >= sdk 34
extern const char* TBD_AMEDIAFORMAT_KEY_BIT_RATE_MODE;

// common utility functions
bool isCSDIdentical(AMediaFormat* refFormat, AMediaFormat* testFormat);
bool isFormatSimilar(AMediaFormat* refFormat, AMediaFormat* testFormat);
AMediaFormat* deSerializeMediaFormat(const char* msg, const char* separator);
bool isMediaTypeOutputUnAffectedBySeek(const char* mediaType);

template <class T>
void flattenField(uint8_t* buffer, int* pos, T value);

#endif  // MEDIACTSNATIVE_NATIVE_MEDIA_COMMON_H
