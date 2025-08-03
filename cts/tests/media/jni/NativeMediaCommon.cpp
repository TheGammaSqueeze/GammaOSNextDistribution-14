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
//#define LOG_NDEBUG 0
#define LOG_TAG "NativeMediaCommon"
#include <log/log.h>

#include <cstdio>
#include <cstring>
#include <utility>

#include "NativeMediaCommon.h"

/* TODO(b/153592281)
 * Note: constants used by the native media tests but not available in media ndk api
 */
const char* AMEDIA_MIMETYPE_VIDEO_VP8 = "video/x-vnd.on2.vp8";
const char* AMEDIA_MIMETYPE_VIDEO_VP9 = "video/x-vnd.on2.vp9";
const char* AMEDIA_MIMETYPE_VIDEO_AV1 = "video/av01";
const char* AMEDIA_MIMETYPE_VIDEO_AVC = "video/avc";
const char* AMEDIA_MIMETYPE_VIDEO_HEVC = "video/hevc";
const char* AMEDIA_MIMETYPE_VIDEO_MPEG4 = "video/mp4v-es";
const char* AMEDIA_MIMETYPE_VIDEO_H263 = "video/3gpp";

const char* AMEDIA_MIMETYPE_AUDIO_AMR_NB = "audio/3gpp";
const char* AMEDIA_MIMETYPE_AUDIO_AMR_WB = "audio/amr-wb";
const char* AMEDIA_MIMETYPE_AUDIO_AAC = "audio/mp4a-latm";
const char* AMEDIA_MIMETYPE_AUDIO_FLAC = "audio/flac";
const char* AMEDIA_MIMETYPE_AUDIO_VORBIS = "audio/vorbis";
const char* AMEDIA_MIMETYPE_AUDIO_OPUS = "audio/opus";
const char* AMEDIA_MIMETYPE_AUDIO_RAW = "audio/raw";

/* TODO(b/153592281) */
const char* TBD_AMEDIACODEC_PARAMETER_KEY_REQUEST_SYNC_FRAME = "request-sync";
const char* TBD_AMEDIACODEC_PARAMETER_KEY_VIDEO_BITRATE = "video-bitrate";
const char* COMPATIBLE_AMEDIAFORMAT_KEY_MAX_B_FRAMES = "max-bframes";
const char* TBD_AMEDIAFORMAT_KEY_BIT_RATE_MODE = "bitrate-mode";

// NDK counterpart of RMS_ERROR_TOLERANCE of CodecDecoderTest class
const float kRmsErrorTolerance = 1.05f;

// NDK counterpart of Q_DEQ_TIMEOUT_US and RETRY_LIMIT of CodecTestBase class
const long kQDeQTimeOutUs = 5000; // block at most 5ms while looking for io buffers
const int kRetryLimit = 100; // max poll counter before test aborts and returns error

bool isCSDIdentical(AMediaFormat* refFormat, AMediaFormat* testFormat) {
    for (int i = 0;; i++) {
        std::pair<void*, size_t> refCsd;
        std::pair<void*, size_t> testCsd;
        char name[16];
        snprintf(name, sizeof(name), "csd-%d", i);
        bool hasRefCSD = AMediaFormat_getBuffer(refFormat, name, &refCsd.first, &refCsd.second);
        bool hasTestCSD = AMediaFormat_getBuffer(testFormat, name, &testCsd.first, &testCsd.second);
        if (hasRefCSD != hasTestCSD) {
            ALOGW("mismatch, ref fmt has CSD %d, test fmt has CSD %d", hasRefCSD, hasTestCSD);
            return false;
        }
        if (hasRefCSD) {
            if (refCsd.second != testCsd.second) {
                ALOGW("ref/test %s buffer sizes are not identical %zu/%zu", name, refCsd.second,
                      testCsd.second);
                return false;
            }
            if (memcmp(refCsd.first, testCsd.first, refCsd.second)) {
                ALOGW("ref/test %s buffers are not identical", name);
                return false;
            }
        } else break;
    }
    return true;
}

template <class T>
void flattenField(uint8_t* buffer, int* pos, T value) {
    uint8_t* ptr = (buffer + *pos);
    for (int i = sizeof(T) - 1; i >= 0; i--) {
        *ptr++ = (uint8_t)((value >> (i * 8)) & 0xff);
    }
    *pos += sizeof(T);
}

template void flattenField<int32_t>(uint8_t* buffer, int* pos, int32_t value);
template void flattenField<int64_t>(uint8_t* buffer, int* pos, int64_t value);

bool isFormatSimilar(AMediaFormat* refFormat, AMediaFormat* testFormat) {
    const char *refMediaType = nullptr, *testMediaType = nullptr;
    int64_t refKeyDuration, testKeyDuration;
    bool hasRefMediaType = AMediaFormat_getString(refFormat, AMEDIAFORMAT_KEY_MIME, &refMediaType);
    if (!hasRefMediaType) return false;
    bool hasTestMediaType =
            AMediaFormat_getString(testFormat, AMEDIAFORMAT_KEY_MIME, &testMediaType);
    if (!hasTestMediaType) return false;
    if (strcmp(refMediaType, testMediaType) != 0) return false;
    bool hasRefKeyDuration =
            AMediaFormat_getInt64(refFormat, AMEDIAFORMAT_KEY_DURATION, &refKeyDuration);
    if (!hasRefKeyDuration) return false;
    bool hasTestKeyDuration =
            AMediaFormat_getInt64(testFormat, AMEDIAFORMAT_KEY_DURATION, &testKeyDuration);
    if (!hasTestKeyDuration) return false;
    if (refKeyDuration != testKeyDuration) {
        ALOGW("Duration mismatches ref / test = %lld / %lld", (long long)refKeyDuration,
              (long long)testKeyDuration);
        // TODO (b/163477410)(b/163478168)
//        return false;
    }
    if (!isCSDIdentical(refFormat, testFormat)) return false;
    if (!strncmp(refMediaType, "audio/", strlen("audio/"))) {
        int32_t refSampleRate, testSampleRate, refNumChannels, testNumChannels;
        bool hasRefSampleRate =
                AMediaFormat_getInt32(refFormat, AMEDIAFORMAT_KEY_SAMPLE_RATE, &refSampleRate);
        if (!hasRefSampleRate) return false;
        bool hasTestSampleRate =
                AMediaFormat_getInt32(testFormat, AMEDIAFORMAT_KEY_SAMPLE_RATE, &testSampleRate);
        if (!hasTestSampleRate) return false;
        if (refSampleRate != testSampleRate) return false;
        bool hasRefNumChannels =
                AMediaFormat_getInt32(refFormat, AMEDIAFORMAT_KEY_CHANNEL_COUNT, &refNumChannels);
        if (!hasRefNumChannels) return false;
        bool hasTestNumChannels =
                AMediaFormat_getInt32(testFormat, AMEDIAFORMAT_KEY_CHANNEL_COUNT, &testNumChannels);
        if (!hasTestNumChannels) return false;
        if (refNumChannels != testNumChannels) return false;
    } else if (!strncmp(refMediaType, "video/", strlen("video/"))) {
        int32_t refWidth, testWidth, refHeight, testHeight;
        bool hasRefWidth = AMediaFormat_getInt32(refFormat, AMEDIAFORMAT_KEY_WIDTH, &refWidth);
        if (!hasRefWidth) return false;
        bool hasTestWidth = AMediaFormat_getInt32(testFormat, AMEDIAFORMAT_KEY_WIDTH, &testWidth);
        if (!hasTestWidth) return false;
        if (refWidth != testWidth) return false;
        bool hasRefHeight = AMediaFormat_getInt32(refFormat, AMEDIAFORMAT_KEY_HEIGHT, &refHeight);
        if (!hasRefHeight) return false;
        bool hasTestHeight =
                AMediaFormat_getInt32(testFormat, AMEDIAFORMAT_KEY_HEIGHT, &testHeight);
        if (!hasTestHeight) return false;
        if (refHeight != testHeight) return false;
    }
    return true;
}

bool isMediaTypeOutputUnAffectedBySeek(const char* mediaType) {
    if (strcmp(mediaType, AMEDIA_MIMETYPE_AUDIO_FLAC) == 0) return true;
    if (strcmp(mediaType, AMEDIA_MIMETYPE_AUDIO_RAW) == 0) return true;
    if (strncmp(mediaType, "video/", strlen("video/")) == 0) return true;
    return false;
}

AMediaFormat* deSerializeMediaFormat(const char* msg, const char* separator) {
    // constants to be kept in sync with definitions at MediaFormat.java
    static const int TYPE_INTEGER = 1;
    static const int TYPE_FLOAT = 3;
    static const int TYPE_STRING = 4;
    std::string limiter{separator};
    std::string fmtMsg{msg};
    AMediaFormat* fmt = AMediaFormat_new();
    if (fmt == nullptr) {
        ALOGE("no format received");
        return nullptr;
    }
    auto start = 0u;
    auto end = fmtMsg.find(limiter);
    std::string keyStr, valueTypeStr, valueStr;
    for (; end != std::string::npos;) {
        // key
        keyStr = fmtMsg.substr(start, end - start);
        start = end + limiter.length();
        end = fmtMsg.find(limiter, start);
        if (end == std::string::npos) {
            ALOGE("incomplete media format received %s", msg);
            AMediaFormat_delete(fmt);
            return nullptr;
        }
        // value type
        valueTypeStr = fmtMsg.substr(start, end - start);
        start = end + limiter.length();
        end = fmtMsg.find(limiter, start);
        if (end == std::string::npos) {
            ALOGE("incomplete media format received %s", msg);
            AMediaFormat_delete(fmt);
            return nullptr;
        }

        // value
        valueStr = fmtMsg.substr(start, end - start);
        start = end + limiter.length();
        end = fmtMsg.find(limiter, start);

        auto valueType = std::stoi(valueTypeStr);
        if (valueType == TYPE_INTEGER) {
            AMediaFormat_setInt32(fmt, keyStr.c_str(), std::stoi(valueStr));
        } else if (valueType == TYPE_FLOAT) {
            AMediaFormat_setFloat(fmt, keyStr.c_str(), std::stof(valueStr));
        } else if (valueType == TYPE_STRING) {
            AMediaFormat_setString(fmt, keyStr.c_str(), valueStr.c_str());
        } else {
            ALOGE("unrecognized type for key %s", keyStr.c_str());
            AMediaFormat_delete(fmt);
            return nullptr;
        }
    }
    return fmt;
}
