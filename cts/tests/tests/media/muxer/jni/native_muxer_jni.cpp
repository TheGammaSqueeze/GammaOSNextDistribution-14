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
//#define LOG_NDEBUG 0
#define LOG_TAG "NativeMuxer"
#include <log/log.h>
#include <assert.h>
#include <jni.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <vector>
#include "media/NdkMediaCodec.h"
#include "media/NdkMediaExtractor.h"
#include "media/NdkMediaMuxer.h"

extern "C" jboolean Java_android_media_muxer_cts_NativeMuxerTest_testMuxerNative(
        JNIEnv */*env*/, jclass /*clazz*/, int infd, jlong inoffset, jlong insize, int outfd,
        jboolean webm, jboolean signaleos) {
    AMediaMuxer *muxer = AMediaMuxer_new(outfd,
            webm ? AMEDIAMUXER_OUTPUT_FORMAT_WEBM : AMEDIAMUXER_OUTPUT_FORMAT_MPEG_4);
    AMediaExtractor *ex = AMediaExtractor_new();
    int err = AMediaExtractor_setDataSourceFd(ex, infd, inoffset, insize);
    if (err != 0) {
        ALOGE("setDataSource error: %d", err);
        return false;
    }
    int numtracks = AMediaExtractor_getTrackCount(ex);
    ALOGI("input tracks: %d", numtracks);
    std::vector<size_t> muxer_track_indices;
    for (int i = 0; i < numtracks; i++) {
        AMediaFormat *format = AMediaExtractor_getTrackFormat(ex, i);
        const char *s = AMediaFormat_toString(format);
        ALOGI("track %d format: %s", i, s);
        const char *mime;
        if (!AMediaFormat_getString(format, AMEDIAFORMAT_KEY_MIME, &mime)) {
            ALOGE("no mime type");
            return false;
        } else if (!strncmp(mime, "audio/", 6) || !strncmp(mime, "video/", 6)) {
            ssize_t tidx = AMediaMuxer_addTrack(muxer, format);
            muxer_track_indices.push_back(tidx);
            ALOGI("track %d -> %zd format %s", i, tidx, s);
            AMediaExtractor_selectTrack(ex, i);
        } else {
            ALOGE("expected audio or video mime type, got %s", mime);
            return false;
        }
        AMediaFormat_delete(format);
        AMediaExtractor_selectTrack(ex, i);
    }
    AMediaMuxer_start(muxer);
    int bufsize = 1024*1024;
    uint8_t *buf = new uint8_t[bufsize];
    AMediaCodecBufferInfo info;
    while(true) {
        int n = AMediaExtractor_readSampleData(ex, buf, bufsize);
        if (n < 0) {
            break;
        }
        info.offset = 0;
        info.size = n;
        info.presentationTimeUs = AMediaExtractor_getSampleTime(ex);
        info.flags = AMediaExtractor_getSampleFlags(ex);
        size_t idx = (size_t) AMediaExtractor_getSampleTrackIndex(ex);
        AMediaMuxer_writeSampleData(muxer, idx, buf, &info);
        AMediaExtractor_advance(ex);
    }
    // By explicitly passing in an EOS buffer with the correct timestamp, we can
    // control the duration of the last sample so that the duration of the
    // produced file matches the input file. This is needed for input files
    // whose last sample's duration doesn't match the preceding one, because the
    // MP4 MediaMuxer uses the duration of the sample second to last for the
    // last sample by default.
    if (signaleos) {
        info.size = 0;
        info.flags = AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM;
        for (int i = 0; i < numtracks; i++) {
            AMediaFormat *format = AMediaExtractor_getTrackFormat(ex, i);
            if (AMediaFormat_getInt64(format, AMEDIAFORMAT_KEY_DURATION,
                                      &info.presentationTimeUs)) {
                AMediaMuxer_writeSampleData(muxer, muxer_track_indices.at(i),
                                            buf, &info);
            }
        }
    }
    AMediaExtractor_delete(ex);
    AMediaMuxer_stop(muxer);
    AMediaMuxer_delete(muxer);
    return true;
}
