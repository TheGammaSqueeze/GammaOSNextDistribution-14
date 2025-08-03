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
#define LOG_TAG "NativeMuxerTest"
#include <log/log.h>

#include <dlfcn.h>
#include <fcntl.h>
#include <jni.h>
#include <media/NdkMediaExtractor.h>
#include <media/NdkMediaFormat.h>
#include <media/NdkMediaMuxer.h>
#include <sys/stat.h>
#include <unistd.h>

#include <cmath>
#include <cstring>
#include <fstream>
#include <map>
#include <vector>

#include "NativeMediaCommon.h"

// Transcoding arrived in Android 12/S, which is api 31.
#define __TRANSCODING_MIN_API__ 31

/**
 * MuxerNativeTestHelper breaks a media file to elements that a muxer can use to rebuild its clone.
 * While testing muxer, if the test doesn't use MediaCodecs class to generate elementary stream,
 * but uses MediaExtractor, this class will be handy
 */
class MuxerNativeTestHelper {
  public:
    explicit MuxerNativeTestHelper(const char* srcPath, const char* mediaType = nullptr,
                                   int frameLimit = -1)
        : mSrcPath(srcPath), mMediaType(mediaType), mTrackCount(0), mBuffer(nullptr) {
        mFrameLimit = frameLimit < 0 ? INT_MAX : frameLimit;
        splitMediaToMuxerParameters();
    }

    ~MuxerNativeTestHelper() {
        for (auto format : mFormat) AMediaFormat_delete(format);
        delete[] mBuffer;
        for (const auto& buffInfoTrack : mBufferInfo) {
            for (auto info : buffInfoTrack) delete info;
        }
    }

    int getTrackCount() { return mTrackCount; }

    size_t getSampleCount(size_t trackId) {
        if (trackId >= mTrackCount) {
            return 0;
        }
        return mBufferInfo[(mInpIndexMap.at(trackId))].size();
    }

    AMediaFormat* getTrackFormat(size_t trackId) {
        if (trackId >= mTrackCount) {
            return nullptr;
        }
        return mFormat[trackId];
    }

    bool registerTrack(AMediaMuxer* muxer);

    bool insertSampleData(AMediaMuxer* muxer);

    bool writeAFewSamplesData(AMediaMuxer* muxer, uint32_t fromIndex, uint32_t toIndex);

    bool writeAFewSamplesDataFromTime(AMediaMuxer* muxer, int64_t* fromTime, uint32_t numSamples,
                                      bool lastSplit);

    bool muxMedia(AMediaMuxer* muxer);

    bool appendMedia(AMediaMuxer *muxer, uint32_t fromIndex, uint32_t toIndex);

    bool appendMediaFromTime(AMediaMuxer *muxer, int64_t *appendFromTime,
                                    uint32_t numSamples, bool lastSplit);

    bool combineMedias(AMediaMuxer* muxer, MuxerNativeTestHelper* that, const int* repeater);

    bool isSubsetOf(MuxerNativeTestHelper* that);

    void offsetTimeStamp(int64_t tsAudioOffsetUs, int64_t tsVideoOffsetUs, int sampleOffset);

  private:
    void splitMediaToMuxerParameters();

    static const int STTS_TOLERANCE_US = 100;
    const char* mSrcPath;
    const char* mMediaType;
    int mTrackCount;
    std::vector<AMediaFormat*> mFormat;
    uint8_t* mBuffer;
    std::vector<std::vector<AMediaCodecBufferInfo*>> mBufferInfo;
    std::map<int, int> mInpIndexMap;
    std::vector<int> mTrackIdxOrder;
    int mFrameLimit;
    // combineMedias() uses local version of this variable
    std::map<int, int> mOutIndexMap;
};

void MuxerNativeTestHelper::splitMediaToMuxerParameters() {
    FILE* ifp = fopen(mSrcPath, "rbe");
    int fd;
    int fileSize;
    if (ifp) {
        struct stat buf {};
        stat(mSrcPath, &buf);
        fileSize = buf.st_size;
        fd = fileno(ifp);
    } else {
        return;
    }
    AMediaExtractor* extractor = AMediaExtractor_new();
    if (extractor == nullptr) {
        fclose(ifp);
        return;
    }
    // Set up MediaExtractor to read from the source.
    media_status_t status = AMediaExtractor_setDataSourceFd(extractor, fd, 0, fileSize);
    if (AMEDIA_OK != status) {
        AMediaExtractor_delete(extractor);
        fclose(ifp);
        return;
    }

    // Set up MediaFormat
    int index = 0;
    for (size_t trackID = 0; trackID < AMediaExtractor_getTrackCount(extractor); trackID++) {
        AMediaExtractor_selectTrack(extractor, trackID);
        AMediaFormat* format = AMediaExtractor_getTrackFormat(extractor, trackID);
        if (mMediaType == nullptr) {
            mTrackCount++;
            mFormat.push_back(format);
            mInpIndexMap[trackID] = index++;
        } else {
            const char* mediaType;
            bool hasKey = AMediaFormat_getString(format, AMEDIAFORMAT_KEY_MIME, &mediaType);
            if (hasKey && !strcmp(mediaType, mMediaType)) {
                mTrackCount++;
                mFormat.push_back(format);
                mInpIndexMap[trackID] = index;
                break;
            } else {
                AMediaFormat_delete(format);
                AMediaExtractor_unselectTrack(extractor, trackID);
            }
        }
    }

    if (mTrackCount <= 0) {
        AMediaExtractor_delete(extractor);
        fclose(ifp);
        return;
    }

    // Set up location for elementary stream
    int bufferSize = ((fileSize + 127) >> 7) << 7;
    // Ideally, Sum of return values of extractor.readSampleData(...) should not exceed
    // source file size. But in case of Vorbis, aosp extractor appends an additional 4 bytes to
    // the data at every readSampleData() call. bufferSize <<= 1 empirically large enough to
    // hold the excess 4 bytes per read call
    bufferSize <<= 1;
    mBuffer = new uint8_t[bufferSize];
    if (mBuffer == nullptr) {
        mTrackCount = 0;
        AMediaExtractor_delete(extractor);
        fclose(ifp);
        return;
    }

    // Let MediaExtractor do its thing
    bool sawEOS = false;
    int frameCount = 0;
    int offset = 0;
    mBufferInfo.resize(mTrackCount);
    while (!sawEOS && frameCount < mFrameLimit) {
        auto* bufferInfo = new AMediaCodecBufferInfo();
        bufferInfo->offset = offset;
        bufferInfo->size =
                AMediaExtractor_readSampleData(extractor, mBuffer + offset, (bufferSize - offset));
        if (bufferInfo->size < 0) {
            sawEOS = true;
        } else {
            bufferInfo->presentationTimeUs = AMediaExtractor_getSampleTime(extractor);
            bufferInfo->flags = AMediaExtractor_getSampleFlags(extractor);
            int trackID = AMediaExtractor_getSampleTrackIndex(extractor);
            mTrackIdxOrder.push_back(trackID);
            mBufferInfo[(mInpIndexMap.at(trackID))].push_back(bufferInfo);
            AMediaExtractor_advance(extractor);
            frameCount++;
        }
        offset += bufferInfo->size;
    }
    ALOGV("frameCount:%d", frameCount);
    AMediaExtractor_delete(extractor);
    fclose(ifp);
}

bool MuxerNativeTestHelper::registerTrack(AMediaMuxer* muxer) {
    for (int trackID = 0; trackID < mTrackCount; trackID++) {
        int dstIndex = AMediaMuxer_addTrack(muxer, mFormat[trackID]);
        if (dstIndex < 0) return false;
        mOutIndexMap[trackID] = dstIndex;
    }
    return true;
}

bool MuxerNativeTestHelper::insertSampleData(AMediaMuxer* muxer) {
    // write all registered tracks in interleaved order
    int* frameCount = new int[mTrackCount]{0};
    for (int trackID : mTrackIdxOrder) {
        int index = mInpIndexMap.at(trackID);
        AMediaCodecBufferInfo* info = mBufferInfo[index][frameCount[index]];
        if (AMediaMuxer_writeSampleData(muxer, mOutIndexMap.at(index), mBuffer, info) !=
            AMEDIA_OK) {
            delete[] frameCount;
            return false;
        }
        ALOGV("Track: %d Timestamp: %" PRId64 "", trackID, info->presentationTimeUs);
        frameCount[index]++;
    }
    delete[] frameCount;
    ALOGV("Total track samples %zu", mTrackIdxOrder.size());
    return true;
}

bool MuxerNativeTestHelper::writeAFewSamplesData(AMediaMuxer* muxer, uint32_t fromIndex,
                                                 uint32_t toIndex) {
    ALOGV("fromIndex:%u, toIndex:%u", fromIndex, toIndex);
    // write all registered tracks in interleaved order
    ALOGV("mTrackIdxOrder.size:%zu", mTrackIdxOrder.size());
    if (fromIndex > toIndex || toIndex >= mTrackIdxOrder.size()) {
        ALOGE("wrong index");
        return false;
    }
    int* frameCount = new int[mTrackCount]{0};
    for (int i = 0; i < fromIndex; ++i) {
        ++frameCount[mInpIndexMap.at(mTrackIdxOrder[i])];
    }
    ALOGV("Initial samples skipped:");
    for (int i = 0; i < mTrackCount; ++i) {
        ALOGV("i:%d:%d", i, frameCount[i]);
    }
    for (int i = fromIndex; i <= toIndex; ++i) {
        int trackID = mTrackIdxOrder[i];
        int trackIndex = mInpIndexMap.at(trackID);
        ALOGV("trackID:%d, trackIndex:%d, frameCount:%d", trackID, trackIndex,
              frameCount[trackIndex]);
        AMediaCodecBufferInfo* info = mBufferInfo[trackIndex][frameCount[trackIndex]];
        ALOGV("Got info offset:%d, size:%d", info->offset, info->size);
        if (AMediaMuxer_writeSampleData(muxer, mOutIndexMap.at(trackIndex), mBuffer, info) !=
            AMEDIA_OK) {
            delete[] frameCount;
            return false;
        }
        ALOGV("Track: %d Timestamp: %" PRId64 "", trackID, info->presentationTimeUs);
        ++frameCount[trackIndex];
    }
    ALOGV("Last sample counts:");
    for (int i = 0; i < mTrackCount; ++i) {
        ALOGV("i:%d", frameCount[i]);
    }
    delete[] frameCount;
    return true;
}

bool MuxerNativeTestHelper::writeAFewSamplesDataFromTime(AMediaMuxer* muxer, int64_t* fromTime,
                                                         uint32_t numSamples, bool lastSplit) {
    for (int tc = 0; tc < mTrackCount; ++tc) {
        ALOGV("fromTime[%d]:%lld", tc, (long long)fromTime[tc]);
    }
    ALOGV("numSamples:%u", numSamples);
    uint32_t samplesWritten = 0;
    uint32_t i = 0;
    int* frameCount = new int[mTrackCount]{0};
    do {
        int trackID = mTrackIdxOrder[i++];
        int trackIndex = mInpIndexMap.at(trackID);
        ALOGV("trackID:%d, trackIndex:%d, frameCount:%d", trackID, trackIndex,
              frameCount[trackIndex]);
        AMediaCodecBufferInfo* info = mBufferInfo[trackIndex][frameCount[trackIndex]];
        ++frameCount[trackIndex];
        ALOGV("Got info offset:%d, size:%d, PTS:%" PRId64 "", info->offset, info->size,
              info->presentationTimeUs);
        if (info->presentationTimeUs < fromTime[trackID]) {
            ALOGV("skipped");
            continue;
        }
        if (AMediaMuxer_writeSampleData(muxer, mOutIndexMap.at(trackIndex), mBuffer, info) !=
            AMEDIA_OK) {
            delete[] frameCount;
            return false;
        } else {
            ++samplesWritten;
        }
    } while ((lastSplit) ? (i < mTrackIdxOrder.size())
                         : ((samplesWritten < numSamples) && (i < mTrackIdxOrder.size())));
    ALOGV("samplesWritten:%u", samplesWritten);

    delete[] frameCount;
    return true;
}

bool MuxerNativeTestHelper::muxMedia(AMediaMuxer* muxer) {
    return (registerTrack(muxer) && (AMediaMuxer_start(muxer) == AMEDIA_OK) &&
            insertSampleData(muxer) && (AMediaMuxer_stop(muxer) == AMEDIA_OK));
}

bool MuxerNativeTestHelper::appendMedia(AMediaMuxer *muxer, uint32_t fromIndex, uint32_t toIndex) {
    if (__builtin_available(android __TRANSCODING_MIN_API__, *)) {
        ALOGV("fromIndex:%u, toIndex:%u", fromIndex, toIndex);
        if (fromIndex == 0) {
            registerTrack(muxer);
        } else {
            size_t trackCount = AMediaMuxer_getTrackCount(muxer);
            ALOGV("appendMedia:trackCount:%zu", trackCount);
            for(size_t i = 0; i < trackCount; ++i) {
                ALOGV("track i:%zu", i);
                ALOGV("%s", AMediaFormat_toString(AMediaMuxer_getTrackFormat(muxer, i)));
                ALOGV("%s", AMediaFormat_toString(mFormat[i]));
                for(size_t j = 0; j < mFormat.size(); ++j) {
                    const char* thatMediaType = nullptr;
                    AMediaFormat_getString(AMediaMuxer_getTrackFormat(muxer, i),
                                                AMEDIAFORMAT_KEY_MIME, &thatMediaType);
                    const char* thisMediaType = nullptr;
                    AMediaFormat_getString(mFormat[j], AMEDIAFORMAT_KEY_MIME, &thisMediaType);
                    ALOGV("strlen(thisMediaType)%zu", strlen(thisMediaType));
                    if (strcmp(thatMediaType, thisMediaType) == 0) {
                        ALOGV("appendMedia:i:%zu, j:%zu", i, j);
                        mOutIndexMap[j]=i;
                    }
                }
            }
        }
        AMediaMuxer_start(muxer);
        bool res = writeAFewSamplesData(muxer, fromIndex, toIndex);
        AMediaMuxer_stop(muxer);
        return res;
    } else {
        return false;
    }
}

bool MuxerNativeTestHelper::appendMediaFromTime(AMediaMuxer *muxer, int64_t *appendFromTime,
                                                        uint32_t numSamples, bool lastSplit) {
    if (__builtin_available(android __TRANSCODING_MIN_API__, *)) {
        size_t trackCount = AMediaMuxer_getTrackCount(muxer);
        ALOGV("appendMediaFromTime:trackCount:%zu", trackCount);
        for(size_t i = 0; i < trackCount; ++i) {
            ALOGV("track i:%zu", i);
            ALOGV("%s", AMediaFormat_toString(AMediaMuxer_getTrackFormat(muxer, i)));
            ALOGV("%s", AMediaFormat_toString(mFormat[i]));
            for(size_t j = 0; j < mFormat.size(); ++j) {
                const char* thatMediaType = nullptr;
                AMediaFormat_getString(AMediaMuxer_getTrackFormat(muxer, i),
                                       AMEDIAFORMAT_KEY_MIME, &thatMediaType);
                const char* thisMediaType = nullptr;
                AMediaFormat_getString(mFormat[j], AMEDIAFORMAT_KEY_MIME, &thisMediaType);
                ALOGV("strlen(thisMediaType)%zu", strlen(thisMediaType));
                if (strcmp(thatMediaType, thisMediaType) == 0) {
                    ALOGV("appendMediaFromTime:i:%zu, j:%zu", i, j);
                    mOutIndexMap[j]=i;
                }
            }
        }
        AMediaMuxer_start(muxer);
        bool res = writeAFewSamplesDataFromTime(muxer, appendFromTime, numSamples, lastSplit);
        AMediaMuxer_stop(muxer);
        return res;
    } else {
        return false;
    }
}
bool MuxerNativeTestHelper::combineMedias(AMediaMuxer* muxer, MuxerNativeTestHelper* that,
                                          const int* repeater) {
    if (that == nullptr) return false;
    if (repeater == nullptr) return false;

    // register tracks
    int totalTracksToAdd = repeater[0] * this->mTrackCount + repeater[1] * that->mTrackCount;
    int outIndexMap[totalTracksToAdd];
    MuxerNativeTestHelper* group[2]{this, that};
    for (int k = 0, idx = 0; k < 2; k++) {
        for (int j = 0; j < repeater[k]; j++) {
            for (AMediaFormat* format : group[k]->mFormat) {
                int dstIndex = AMediaMuxer_addTrack(muxer, format);
                if (dstIndex < 0) return false;
                outIndexMap[idx++] = dstIndex;
            }
        }
    }
    // start
    if (AMediaMuxer_start(muxer) != AMEDIA_OK) return false;
    // write sample data
    // write all registered tracks in planar order viz all samples of a track A then all
    // samples of track B, ...
    for (int k = 0, idx = 0; k < 2; k++) {
        for (int j = 0; j < repeater[k]; j++) {
            for (int i = 0; i < group[k]->mTrackCount; i++) {
                for (int p = 0; p < group[k]->mBufferInfo[i].size(); p++) {
                    AMediaCodecBufferInfo* info = group[k]->mBufferInfo[i][p];
                    if (AMediaMuxer_writeSampleData(muxer, outIndexMap[idx], group[k]->mBuffer,
                                                    info) != AMEDIA_OK) {
                        return false;
                    }
                    ALOGV("Track: %d Timestamp: %" PRId64 "", outIndexMap[idx],
                          info->presentationTimeUs);
                }
                idx++;
            }
        }
    }
    // stop
    return (AMediaMuxer_stop(muxer) == AMEDIA_OK);
}

// returns true if 'this' stream is a subset of 'that'. That is all tracks in current media
// stream are present in ref media stream
bool MuxerNativeTestHelper::isSubsetOf(MuxerNativeTestHelper* that) {
    if (this == that) return true;
    if (that == nullptr) return false;

    for (int i = 0; i < mTrackCount; i++) {
        AMediaFormat* thisFormat = mFormat[i];
        const char* thisMediaType = nullptr;
        AMediaFormat_getString(thisFormat, AMEDIAFORMAT_KEY_MIME, &thisMediaType);
        int tolerance = !strncmp(thisMediaType, "video/", strlen("video/")) ? STTS_TOLERANCE_US : 0;
        tolerance += 1;  // rounding error
        int j = 0;
        for (; j < that->mTrackCount; j++) {
            AMediaFormat* thatFormat = that->mFormat[j];
            const char* thatMediaType = nullptr;
            AMediaFormat_getString(thatFormat, AMEDIAFORMAT_KEY_MIME, &thatMediaType);
            if (thisMediaType != nullptr && thatMediaType != nullptr &&
                !strcmp(thisMediaType, thatMediaType)) {
                if (!isFormatSimilar(thisFormat, thatFormat)) continue;
                if (mBufferInfo[i].size() <= that->mBufferInfo[j].size()) {
                    int tolerance = !strncmp(thisMediaType, "video/", strlen("video/"))
                                            ? STTS_TOLERANCE_US
                                            : 0;
                    tolerance += 1;  // rounding error
                    int k = 0;
                    for (; k < mBufferInfo[i].size(); k++) {
                        ALOGV("k:%d", k);
                        AMediaCodecBufferInfo* thisInfo = mBufferInfo[i][k];
                        AMediaCodecBufferInfo* thatInfo = that->mBufferInfo[j][k];
                        if (thisInfo->flags != thatInfo->flags) {
                            ALOGD("flags this:%u, that:%u", thisInfo->flags, thatInfo->flags);
                            break;
                        }
                        if (thisInfo->size != thatInfo->size) {
                            ALOGD("size  this:%d, that:%d", thisInfo->size, thatInfo->size);
                            break;
                        } else if (memcmp(mBuffer + thisInfo->offset,
                                          that->mBuffer + thatInfo->offset, thisInfo->size)) {
                            ALOGD("memcmp failed");
                            break;
                        }
                        if (abs(thisInfo->presentationTimeUs - thatInfo->presentationTimeUs) >
                            tolerance) {
                            ALOGD("time this:%lld, that:%lld",
                                  (long long)thisInfo->presentationTimeUs,
                                  (long long)thatInfo->presentationTimeUs);
                            break;
                        }
                    }
                    if (k == mBufferInfo[i].size()) break;
                }
            }
        }
        if (j == that->mTrackCount) {
            AMediaFormat_getString(thisFormat, AMEDIAFORMAT_KEY_MIME, &thisMediaType);
            ALOGV("For media type %s, Couldn't find a match", thisMediaType);
            return false;
        }
    }
    return true;
}

void MuxerNativeTestHelper::offsetTimeStamp(int64_t tsAudioOffsetUs, int64_t tsVideoOffsetUs,
                                            int sampleOffset) {
    // offset pts of samples from index sampleOffset till the end by tsOffset for each audio and
    // video track
    for (int trackID = 0; trackID < mTrackCount; trackID++) {
        int64_t tsOffsetUs = 0;
        const char* thisMediaType = nullptr;
        AMediaFormat_getString(mFormat[trackID], AMEDIAFORMAT_KEY_MIME, &thisMediaType);
        if (thisMediaType != nullptr) {
            if (strncmp(thisMediaType, "video/", strlen("video/")) == 0) {
                tsOffsetUs = tsVideoOffsetUs;
            } else if (strncmp(thisMediaType, "audio/", strlen("audio/")) == 0) {
                tsOffsetUs = tsAudioOffsetUs;
            }
            for (int i = sampleOffset; i < mBufferInfo[trackID].size(); i++) {
                AMediaCodecBufferInfo* info = mBufferInfo[trackID][i];
                info->presentationTimeUs += tsOffsetUs;
            }
        }
    }
}

static bool isCodecContainerPairValid(OutputFormat format, const char* mediaType) {
    static const std::map<OutputFormat, std::vector<const char*>> codecListforType = {
            {AMEDIAMUXER_OUTPUT_FORMAT_MPEG_4,
             {AMEDIA_MIMETYPE_VIDEO_MPEG4, AMEDIA_MIMETYPE_VIDEO_H263, AMEDIA_MIMETYPE_VIDEO_AVC,
              AMEDIA_MIMETYPE_VIDEO_HEVC, AMEDIA_MIMETYPE_AUDIO_AAC}},
            {AMEDIAMUXER_OUTPUT_FORMAT_WEBM,
             {AMEDIA_MIMETYPE_VIDEO_VP8, AMEDIA_MIMETYPE_VIDEO_VP9, AMEDIA_MIMETYPE_AUDIO_VORBIS,
              AMEDIA_MIMETYPE_AUDIO_OPUS}},
            {AMEDIAMUXER_OUTPUT_FORMAT_THREE_GPP,
             {AMEDIA_MIMETYPE_VIDEO_MPEG4, AMEDIA_MIMETYPE_VIDEO_H263, AMEDIA_MIMETYPE_VIDEO_AVC,
              AMEDIA_MIMETYPE_AUDIO_AAC, AMEDIA_MIMETYPE_AUDIO_AMR_NB,
              AMEDIA_MIMETYPE_AUDIO_AMR_WB}},
            {AMEDIAMUXER_OUTPUT_FORMAT_OGG, {AMEDIA_MIMETYPE_AUDIO_OPUS}},
    };

    if (format == AMEDIAMUXER_OUTPUT_FORMAT_MPEG_4 &&
        strncmp(mediaType, "application/", strlen("application/")) == 0)
        return true;

    auto it = codecListforType.find(format);
    if (it != codecListforType.end())
        for (auto it2 : it->second)
            if (strcmp(it2, mediaType) == 0) return true;

    return false;
}

static jboolean nativeTestSetLocation(JNIEnv* env, jobject, jint jformat, jstring jsrcPath,
                                      jstring jdstPath) {
    bool isPass = true;
    bool isGeoDataSupported;
    const float atlanticLat = 14.59f;
    const float atlanticLong = 28.67f;
    const float tooFarNorth = 90.5f;
    const float tooFarWest = -180.5f;
    const float tooFarSouth = -90.5f;
    const float tooFarEast = 180.5f;
    const float annapurnaLat = 28.59f;
    const float annapurnaLong = 83.82f;
    const char* cdstPath = env->GetStringUTFChars(jdstPath, nullptr);
    FILE* ofp = fopen(cdstPath, "wbe+");
    if (ofp) {
        AMediaMuxer* muxer = AMediaMuxer_new(fileno(ofp), (OutputFormat)jformat);
        media_status_t status = AMediaMuxer_setLocation(muxer, tooFarNorth, atlanticLong);
        if (status == AMEDIA_OK) {
            isPass = false;
            ALOGE("setLocation succeeds on bad args: (%f, %f)", tooFarNorth, atlanticLong);
        }
        status = AMediaMuxer_setLocation(muxer, tooFarSouth, atlanticLong);
        if (status == AMEDIA_OK) {
            isPass = false;
            ALOGE("setLocation succeeds on bad args: (%f, %f)", tooFarSouth, atlanticLong);
        }
        status = AMediaMuxer_setLocation(muxer, atlanticLat, tooFarWest);
        if (status == AMEDIA_OK) {
            isPass = false;
            ALOGE("setLocation succeeds on bad args: (%f, %f)", atlanticLat, tooFarWest);
        }
        status = AMediaMuxer_setLocation(muxer, atlanticLat, tooFarEast);
        if (status == AMEDIA_OK) {
            isPass = false;
            ALOGE("setLocation succeeds on bad args: (%f, %f)", atlanticLat, tooFarEast);
        }
        status = AMediaMuxer_setLocation(muxer, tooFarNorth, tooFarWest);
        if (status == AMEDIA_OK) {
            isPass = false;
            ALOGE("setLocation succeeds on bad args: (%f, %f)", tooFarNorth, tooFarWest);
        }
        status = AMediaMuxer_setLocation(muxer, atlanticLat, atlanticLong);
        isGeoDataSupported = (status == AMEDIA_OK);
        if (isGeoDataSupported) {
            status = AMediaMuxer_setLocation(muxer, annapurnaLat, annapurnaLong);
            if (status != AMEDIA_OK) {
                isPass = false;
                ALOGE("setLocation fails on args: (%f, %f)", annapurnaLat, annapurnaLong);
            }
        } else {
            isPass &= ((OutputFormat)jformat != AMEDIAMUXER_OUTPUT_FORMAT_MPEG_4 &&
                       (OutputFormat)jformat != AMEDIAMUXER_OUTPUT_FORMAT_THREE_GPP);
        }
        const char* csrcPath = env->GetStringUTFChars(jsrcPath, nullptr);
        auto* mediaInfo = new MuxerNativeTestHelper(csrcPath);
        if (mediaInfo->registerTrack(muxer) && AMediaMuxer_start(muxer) == AMEDIA_OK) {
            status = AMediaMuxer_setLocation(muxer, atlanticLat, atlanticLong);
            if (status == AMEDIA_OK) {
                isPass = false;
                ALOGE("setLocation succeeds after starting the muxer");
            }
            if (mediaInfo->insertSampleData(muxer) && AMediaMuxer_stop(muxer) == AMEDIA_OK) {
                status = AMediaMuxer_setLocation(muxer, atlanticLat, atlanticLong);
                if (status == AMEDIA_OK) {
                    isPass = false;
                    ALOGE("setLocation succeeds after stopping the muxer");
                }
            } else {
                isPass = false;
                ALOGE("failed to writeSampleData or stop muxer");
            }
        } else {
            isPass = false;
            ALOGE("failed to addTrack or start muxer");
        }
        delete mediaInfo;
        env->ReleaseStringUTFChars(jsrcPath, csrcPath);
        AMediaMuxer_delete(muxer);
        fclose(ofp);
    } else {
        isPass = false;
        ALOGE("failed to open output file %s", cdstPath);
    }
    env->ReleaseStringUTFChars(jdstPath, cdstPath);
    return static_cast<jboolean>(isPass);
}

// @ApiTest = AMEDIAMUXER_OUTPUT_FORMAT_MPEG_4
// @ApiTest = AMEDIAMUXER_OUTPUT_FORMAT_THREE_GPP
//
static jboolean nativeTestSetOrientationHint(JNIEnv* env, jobject, jint jformat, jstring jsrcPath,
                                             jstring jdstPath) {
    bool isPass = true;
    bool isOrientationSupported;
    const int badRotation[] = {360, 45, -90};
    const int oldRotation = 90;
    const int currRotation = 180;
    const char* cdstPath = env->GetStringUTFChars(jdstPath, nullptr);
    FILE* ofp = fopen(cdstPath, "wbe+");
    if (ofp) {
        AMediaMuxer* muxer = AMediaMuxer_new(fileno(ofp), (OutputFormat)jformat);
        media_status_t status;
        for (int degrees : badRotation) {
            status = AMediaMuxer_setOrientationHint(muxer, degrees);
            if (status == AMEDIA_OK) {
                isPass = false;
                ALOGE("setOrientationHint succeeds on bad args: %d", degrees);
            }
        }
        status = AMediaMuxer_setOrientationHint(muxer, oldRotation);
        isOrientationSupported = (status == AMEDIA_OK);
        if (isOrientationSupported) {
            status = AMediaMuxer_setOrientationHint(muxer, currRotation);
            if (status != AMEDIA_OK) {
                isPass = false;
                ALOGE("setOrientationHint fails on args: %d", currRotation);
            }
        } else {
            isPass &= ((OutputFormat)jformat != AMEDIAMUXER_OUTPUT_FORMAT_MPEG_4 &&
                       (OutputFormat)jformat != AMEDIAMUXER_OUTPUT_FORMAT_THREE_GPP);
        }
        const char* csrcPath = env->GetStringUTFChars(jsrcPath, nullptr);
        auto* mediaInfo = new MuxerNativeTestHelper(csrcPath);
        if (mediaInfo->registerTrack(muxer) && AMediaMuxer_start(muxer) == AMEDIA_OK) {
            status = AMediaMuxer_setOrientationHint(muxer, currRotation);
            if (status == AMEDIA_OK) {
                isPass = false;
                ALOGE("setOrientationHint succeeds after starting the muxer");
            }
            if (mediaInfo->insertSampleData(muxer) && AMediaMuxer_stop(muxer) == AMEDIA_OK) {
                status = AMediaMuxer_setOrientationHint(muxer, currRotation);
                if (status == AMEDIA_OK) {
                    isPass = false;
                    ALOGE("setOrientationHint succeeds after stopping the muxer");
                }
            } else {
                isPass = false;
                ALOGE("failed to writeSampleData or stop muxer");
            }
        } else {
            isPass = false;
            ALOGE("failed to addTrack or start muxer");
        }
        delete mediaInfo;
        env->ReleaseStringUTFChars(jsrcPath, csrcPath);
        AMediaMuxer_delete(muxer);
        fclose(ofp);
    } else {
        isPass = false;
        ALOGE("failed to open output file %s", cdstPath);
    }
    env->ReleaseStringUTFChars(jdstPath, cdstPath);
    return static_cast<jboolean>(isPass);
}

static jboolean nativeTestMultiTrack(JNIEnv* env, jobject, jint jformat, jstring jsrcPathA,
                                     jstring jsrcPathB, jstring jrefPath, jstring jdstPath) {
    bool isPass = true;
    const char* csrcPathA = env->GetStringUTFChars(jsrcPathA, nullptr);
    const char* csrcPathB = env->GetStringUTFChars(jsrcPathB, nullptr);
    auto* mediaInfoA = new MuxerNativeTestHelper(csrcPathA);
    auto* mediaInfoB = new MuxerNativeTestHelper(csrcPathB);
    if (mediaInfoA->getTrackCount() == 1 && mediaInfoB->getTrackCount() == 1) {
        const char* crefPath = env->GetStringUTFChars(jrefPath, nullptr);
        // number of times to repeat {mSrcFileA, mSrcFileB} in Output
        // values should be in sync with testMultiTrack
        static const int numTracks[][2] = {{1, 1}, {2, 0}, {0, 2}, {1, 2}, {2, 1}, {2, 2}};
        // prepare reference
        FILE* rfp = fopen(crefPath, "wbe+");
        if (rfp) {
            AMediaMuxer* muxer = AMediaMuxer_new(fileno(rfp), (OutputFormat)jformat);
            bool muxStatus = mediaInfoA->combineMedias(muxer, mediaInfoB, numTracks[0]);
            AMediaMuxer_delete(muxer);
            fclose(rfp);
            if (muxStatus) {
                auto* refInfo = new MuxerNativeTestHelper(crefPath);
                if (!mediaInfoA->isSubsetOf(refInfo) || !mediaInfoB->isSubsetOf(refInfo)) {
                    isPass = false;
                    ALOGE("testMultiTrack: inputs: %s %s, fmt: %d, error ! muxing src A and src B "
                          "failed", csrcPathA, csrcPathB, jformat);
                } else {
                    const char* cdstPath = env->GetStringUTFChars(jdstPath, nullptr);
                    for (int i = 1; i < sizeof(numTracks) / sizeof(numTracks[0]) && isPass; i++) {
                        FILE* ofp = fopen(cdstPath, "wbe+");
                        if (ofp) {
                            muxer = AMediaMuxer_new(fileno(ofp), (OutputFormat)jformat);
                            bool status =
                                    mediaInfoA->combineMedias(muxer, mediaInfoB, numTracks[i]);
                            AMediaMuxer_delete(muxer);
                            fclose(ofp);
                            if (status) {
                                auto* dstInfo = new MuxerNativeTestHelper(cdstPath);
                                if (!dstInfo->isSubsetOf(refInfo)) {
                                    isPass = false;
                                    ALOGE("testMultiTrack: inputs: %s %s, fmt: %d, error ! muxing "
                                          "src A: %d, src B: %d failed", csrcPathA, csrcPathB,
                                          jformat, numTracks[i][0], numTracks[i][1]);
                                }
                                delete dstInfo;
                            } else {
                                if ((OutputFormat)jformat != AMEDIAMUXER_OUTPUT_FORMAT_MPEG_4) {
                                    isPass = false;
                                    ALOGE("testMultiTrack: inputs: %s %s, fmt: %d, error ! muxing "
                                          "src A: %d, src B: %d failed", csrcPathA, csrcPathB,
                                          jformat, numTracks[i][0], numTracks[i][1]);
                                }
                            }
                        } else {
                            isPass = false;
                            ALOGE("failed to open output file %s", cdstPath);
                        }
                    }
                    env->ReleaseStringUTFChars(jdstPath, cdstPath);
                }
                delete refInfo;
            } else {
                if ((OutputFormat)jformat != AMEDIAMUXER_OUTPUT_FORMAT_OGG) {
                    isPass = false;
                    ALOGE("testMultiTrack: inputs: %s %s, fmt: %d, error ! muxing src A and src B "
                          "failed", csrcPathA, csrcPathB, jformat);
                }
            }
        } else {
            isPass = false;
            ALOGE("failed to open reference output file %s", crefPath);
        }
        env->ReleaseStringUTFChars(jrefPath, crefPath);
    } else {
        isPass = false;
        if (mediaInfoA->getTrackCount() != 1) {
            ALOGE("error: file %s, track count exp/rec - %d/%d", csrcPathA, 1,
                  mediaInfoA->getTrackCount());
        }
        if (mediaInfoB->getTrackCount() != 1) {
            ALOGE("error: file %s, track count exp/rec - %d/%d", csrcPathB, 1,
                  mediaInfoB->getTrackCount());
        }
    }
    env->ReleaseStringUTFChars(jsrcPathA, csrcPathA);
    env->ReleaseStringUTFChars(jsrcPathB, csrcPathB);
    delete mediaInfoA;
    delete mediaInfoB;
    return static_cast<jboolean>(isPass);
}

static jboolean nativeTestOffsetPts(JNIEnv* env, jobject, jint format, jstring jsrcPath,
                                    jstring jdstPath, jintArray joffsetIndices) {
    bool isPass = true;
    // values should be in sync with testOffsetPresentationTime
    static const int64_t OFFSET_TS_AUDIO_US[4] = {-23220LL, 0LL, 200000LL, 400000LL};
    static const int64_t OFFSET_TS_VIDEO_US[3] = {0LL, 200000LL, 400000LL};
    jsize len = env->GetArrayLength(joffsetIndices);
    jint* coffsetIndices = env->GetIntArrayElements(joffsetIndices, nullptr);
    const char* csrcPath = env->GetStringUTFChars(jsrcPath, nullptr);
    const char* cdstPath = env->GetStringUTFChars(jdstPath, nullptr);
    auto* mediaInfo = new MuxerNativeTestHelper(csrcPath);
    if (mediaInfo->getTrackCount() != 0) {
        for (int64_t audioOffsetUs : OFFSET_TS_AUDIO_US) {
            for (int64_t videoOffsetUs : OFFSET_TS_VIDEO_US) {
                for (int i = 0; i < len; i++) {
                    mediaInfo->offsetTimeStamp(audioOffsetUs, videoOffsetUs, coffsetIndices[i]);
                }
                FILE* ofp = fopen(cdstPath, "wbe+");
                if (ofp) {
                    AMediaMuxer* muxer = AMediaMuxer_new(fileno(ofp), (OutputFormat)format);
                    mediaInfo->muxMedia(muxer);
                    AMediaMuxer_delete(muxer);
                    fclose(ofp);
                    auto* outInfo = new MuxerNativeTestHelper(cdstPath);
                    isPass = mediaInfo->isSubsetOf(outInfo);
                    if (!isPass) {
                        ALOGE("Validation failed after adding timestamp offsets audio: %lld,"
                              " video: %lld", (long long) audioOffsetUs, (long long) videoOffsetUs);
                    }
                    delete outInfo;
                } else {
                    isPass = false;
                    ALOGE("failed to open output file %s", cdstPath);
                }
                for (int i = len - 1; i >= 0; i--) {
                    mediaInfo->offsetTimeStamp(-audioOffsetUs, -videoOffsetUs, coffsetIndices[i]);
                }
            }
        }
    } else {
        isPass = false;
        ALOGE("no valid track found in input file %s", csrcPath);
    }
    env->ReleaseStringUTFChars(jdstPath, cdstPath);
    env->ReleaseStringUTFChars(jsrcPath, csrcPath);
    env->ReleaseIntArrayElements(joffsetIndices, coffsetIndices, 0);
    delete mediaInfo;
    return static_cast<jboolean>(isPass);
}

// simple muxer tests, including varying the output container format.
//
// @ApiTest = AMEDIAMUXER_OUTPUT_FORMAT_HEIF
// @ApiTest = AMEDIAMUXER_OUTPUT_FORMAT_MPEG_4
// @ApiTest = AMEDIAMUXER_OUTPUT_FORMAT_OGG
// @ApiTest = AMEDIAMUXER_OUTPUT_FORMAT_THREE_GPP
// @ApiTest = AMEDIAMUXER_OUTPUT_FORMAT_WEBM
//
static jboolean nativeTestSimpleMux(JNIEnv* env, jobject, jstring jsrcPath, jstring jdstPath,
                                    jstring jMediaType, jstring jselector) {
    bool isPass = true;
    const char* cMediaType = env->GetStringUTFChars(jMediaType, nullptr);
    const char* csrcPath = env->GetStringUTFChars(jsrcPath, nullptr);
    const char* cselector = env->GetStringUTFChars(jselector, nullptr);
    auto* mediaInfo = new MuxerNativeTestHelper(csrcPath, cMediaType);
    static const std::map<OutputFormat, const char*> formatStringPair = {
            {AMEDIAMUXER_OUTPUT_FORMAT_MPEG_4, "mp4"},
            {AMEDIAMUXER_OUTPUT_FORMAT_WEBM, "webm"},
            {AMEDIAMUXER_OUTPUT_FORMAT_THREE_GPP, "3gp"},
            {AMEDIAMUXER_OUTPUT_FORMAT_HEIF, "heif"},
            {AMEDIAMUXER_OUTPUT_FORMAT_OGG, "ogg"}};
    if (mediaInfo->getTrackCount() == 1) {
        const char* cdstPath = env->GetStringUTFChars(jdstPath, nullptr);
        for (int fmt = LOCAL_AMEDIAMUXER_OUTPUT_FORMAT_FIRST;
             fmt <= LOCAL_AMEDIAMUXER_OUTPUT_FORMAT_LAST && isPass; fmt++) {
            auto it = formatStringPair.find((OutputFormat)fmt);
            if (it == formatStringPair.end() || strstr(cselector, it->second) == nullptr) {
                continue;
            }
            if (fmt == AMEDIAMUXER_OUTPUT_FORMAT_WEBM) continue;  // TODO(b/146923551)
            FILE* ofp = fopen(cdstPath, "wbe+");
            if (ofp) {
                AMediaMuxer* muxer = AMediaMuxer_new(fileno(ofp), (OutputFormat)fmt);
                bool muxStatus = mediaInfo->muxMedia(muxer);
                bool result = true;
                AMediaMuxer_delete(muxer);
                fclose(ofp);
                if (muxStatus) {
                    auto* outInfo = new MuxerNativeTestHelper(cdstPath, cMediaType);
                    result = mediaInfo->isSubsetOf(outInfo);
                    delete outInfo;
                }
                if ((muxStatus && !result) ||
                    (!muxStatus && isCodecContainerPairValid((OutputFormat)fmt, cMediaType))) {
                    isPass = false;
                    ALOGE("error: file %s, mediaType %s, output != clone(input) for format %d",
                          csrcPath, cMediaType, fmt);
                }
            } else {
                isPass = false;
                ALOGE("error: file %s, mediaType %s, failed to open output file %s", csrcPath,
                      cMediaType, cdstPath);
            }
        }
        env->ReleaseStringUTFChars(jdstPath, cdstPath);
    } else {
        isPass = false;
        ALOGE("error: file %s, mediaType %s, track count exp/rec - %d/%d", csrcPath, cMediaType, 1,
              mediaInfo->getTrackCount());
    }
    env->ReleaseStringUTFChars(jselector, cselector);
    env->ReleaseStringUTFChars(jsrcPath, csrcPath);
    env->ReleaseStringUTFChars(jMediaType, cMediaType);
    delete mediaInfo;
    return static_cast<jboolean>(isPass);
}

/* Check whether AMediaMuxer_getTrackCount works as expected.
 */
static jboolean nativeTestGetTrackCount(JNIEnv* env, jobject, jstring jsrcPath, jstring jdstPath,
                                            jint jformat, jint jtrackCount) {
    bool isPass = true;
    if (__builtin_available(android __TRANSCODING_MIN_API__, *)) {
        const char* csrcPath = env->GetStringUTFChars(jsrcPath, nullptr);
        const char* cdstPath = env->GetStringUTFChars(jdstPath, nullptr);
        FILE* ofp = fopen(cdstPath, "w+");
        if (ofp) {
            AMediaMuxer *muxer = AMediaMuxer_new(fileno(ofp), (OutputFormat)jformat);
            if (muxer) {
                auto* mediaInfo = new MuxerNativeTestHelper(csrcPath);
                if (!mediaInfo->registerTrack(muxer)) {
                    isPass = false;
                    ALOGE("register track failed");
                }
                if (AMediaMuxer_getTrackCount(muxer) != jtrackCount) {
                    isPass = false;
                    ALOGE("track counts are not equal");
                }
                delete mediaInfo;
                AMediaMuxer_delete(muxer);
            } else {
                isPass = false;
                ALOGE("Failed to create muxer");
            }
            fclose(ofp);
        } else {
            isPass = false;
            ALOGE("file open error: file  %s", csrcPath);
        }
        env->ReleaseStringUTFChars(jsrcPath, csrcPath);
        env->ReleaseStringUTFChars(jdstPath, cdstPath);
    } else {
        isPass = false;
    }
    return static_cast<jboolean>(isPass);
}

/* Check whether AMediaMuxer_getTrackCount works as expected when the file is opened in
 * append mode.
 */
static jboolean nativeTestAppendGetTrackCount(JNIEnv* env, jobject, jstring jsrcPath,
                                                        jint jtrackCount) {
    bool isPass = true;
    if (__builtin_available(android __TRANSCODING_MIN_API__, *)) {
        const char* csrcPath = env->GetStringUTFChars(jsrcPath, nullptr);
        for (unsigned int mode = AMEDIAMUXER_APPEND_IGNORE_LAST_VIDEO_GOP;
                    mode <= AMEDIAMUXER_APPEND_TO_EXISTING_DATA; ++mode) {
            ALOGV("mode:%u", mode);
            FILE* ofp = fopen(csrcPath, "r");
            if (ofp) {
                AMediaMuxer *muxer = AMediaMuxer_append(fileno(ofp), (AppendMode)mode);
                if (muxer) {
                    ssize_t trackCount = AMediaMuxer_getTrackCount(muxer);
                    if ( trackCount != jtrackCount) {
                        isPass = false;
                        ALOGE("trackcounts are not equal, trackCount:%ld vs jtrackCount:%d",
                                    (long)trackCount, jtrackCount);
                    }
                    AMediaMuxer_delete(muxer);
                } else {
                    isPass = false;
                    ALOGE("Failed to create muxer");
                }
                fclose(ofp);
                ofp = nullptr;
            } else {
                isPass = false;
                ALOGE("file open error: file  %s", csrcPath);
            }
        }
        env->ReleaseStringUTFChars(jsrcPath, csrcPath);
    } else {
        isPass = false;
    }
    return static_cast<jboolean>(isPass);
}

/* Checks whether AMediaMuxer_getTrackFormat works as expected in muxer mode.
 */
static jboolean nativeTestGetTrackFormat(JNIEnv* env, jobject, jstring jsrcPath, jstring jdstPath,
                                                    jint joutFormat) {
    bool isPass = true;
    if (__builtin_available(android __TRANSCODING_MIN_API__, *)) {
        const char* csrcPath = env->GetStringUTFChars(jsrcPath, nullptr);
        const char* cdstPath = env->GetStringUTFChars(jdstPath, nullptr);
        FILE* ofp = fopen(cdstPath, "w+");
        if (ofp) {
            AMediaMuxer *muxer = AMediaMuxer_new(fileno(ofp), (OutputFormat)joutFormat);
            if (muxer) {
                auto* mediaInfo = new MuxerNativeTestHelper(csrcPath);
                if (!mediaInfo->registerTrack(muxer)) {
                    isPass = false;
                    ALOGE("register track failed");
                }
                for(int i = 0 ; i < mediaInfo->getTrackCount(); ++i ) {
                    if (!isFormatSimilar(mediaInfo->getTrackFormat(i),
                            AMediaMuxer_getTrackFormat(muxer, i))) {
                        isPass = false;
                        ALOGE("track formats are not similar");
                    }
                }
                delete mediaInfo;
                AMediaMuxer_delete(muxer);
            } else {
                isPass = false;
                ALOGE("Failed to create muxer");
            }
            fclose(ofp);
        } else {
            isPass = false;
            ALOGE("file open error: file  %s", csrcPath);
        }
        env->ReleaseStringUTFChars(jsrcPath, csrcPath);
        env->ReleaseStringUTFChars(jdstPath, cdstPath);
    } else {
        isPass = false;
    }
    return static_cast<jboolean>(isPass);
}

/* Checks whether AMediaMuxer_getTrackFormat works as expected when the file is opened in
 * append mode.
 */
static jboolean nativeTestAppendGetTrackFormat(JNIEnv* env, jobject, jstring jsrcPath) {
    bool isPass = true;
    if (__builtin_available(android __TRANSCODING_MIN_API__, *)) {
        const char* csrcPath = env->GetStringUTFChars(jsrcPath, nullptr);
        for (unsigned int mode = AMEDIAMUXER_APPEND_IGNORE_LAST_VIDEO_GOP;
                    mode <= AMEDIAMUXER_APPEND_TO_EXISTING_DATA; ++mode) {
            ALOGV("mode:%u", mode);
            FILE* ofp = fopen(csrcPath, "r");
            if (ofp) {
                AMediaMuxer *muxer = AMediaMuxer_append(fileno(ofp), (AppendMode)mode);
                if (muxer) {
                    auto* mediaInfo = new MuxerNativeTestHelper(csrcPath);
                    for(int i = 0 ; i < mediaInfo->getTrackCount(); ++i ) {
                        if (!isFormatSimilar(mediaInfo->getTrackFormat(i),
                                AMediaMuxer_getTrackFormat(muxer, i))) {
                            isPass = false;
                            ALOGE("track formats are not similar");
                        }
                    }
                    delete mediaInfo;
                    AMediaMuxer_delete(muxer);
                } else {
                    isPass = false;
                    ALOGE("Failed to create muxer");
                }
                fclose(ofp);
            } else {
                isPass = false;
                ALOGE("file open error: file  %s", csrcPath);
            }
        }
        env->ReleaseStringUTFChars(jsrcPath, csrcPath);
    }
    else {
        isPass = false;
    }
    return static_cast<jboolean>(isPass);
}

/*
 * Checks if appending media data to the end of existing media data in a file works good.
 * Mode : AMEDIAMUXER_APPEND_TO_EXISTING_DATA.  Splits the contents of source file equally
 * starting from one and increasing the number of splits by one for every iteration.  Starts
 * with writing first split into a new file and appends the rest of the contents split by split.
 */
static jboolean nativeTestSimpleAppend(JNIEnv* env, jobject, jint joutFormat, jstring jsrcPath,
                        jstring jdstPath) {
    bool isPass = true;
    if (__builtin_available(android __TRANSCODING_MIN_API__, *)) {
        const char* csrcPath = env->GetStringUTFChars(jsrcPath, nullptr);
        const char* cdstPath = env->GetStringUTFChars(jdstPath, nullptr);
        ALOGV("csrcPath:%s", csrcPath);
        ALOGV("cdstPath:%s", cdstPath);
        auto* mediaInfo = new MuxerNativeTestHelper(csrcPath);
        for (int numSplits = 1; numSplits <= 5; ++numSplits) {
            ALOGV("numSplits:%d", numSplits);
            size_t totalSampleCount = 0;
            AMediaMuxer *muxer = nullptr;
            // Start by writing first split into a new file.
            FILE* ofp = fopen(cdstPath, "w+");
            if (ofp) {
                muxer = AMediaMuxer_new(fileno(ofp), (OutputFormat)joutFormat);
                if (muxer) {
                    for(int i = 0 ; i < mediaInfo->getTrackCount(); ++i ) {
                        ALOGV("getSampleCount:%d:%zu", i, mediaInfo->getSampleCount(i));
                        totalSampleCount += mediaInfo->getSampleCount(i);
                    }
                    mediaInfo->appendMedia(muxer, 0, (totalSampleCount/numSplits)-1);
                    AMediaMuxer_delete(muxer);
                } else {
                    isPass = false;
                    ALOGE("Failed to create muxer");
                }
                fclose(ofp);
                ofp = nullptr;
                // Check if the contents in the new file is as same as in the source file.
                if (isPass) {
                    auto* mediaInfoDest = new MuxerNativeTestHelper(cdstPath, nullptr);
                    isPass = mediaInfoDest->isSubsetOf(mediaInfo);
                    delete mediaInfoDest;
                }
            } else {
                isPass = false;
                ALOGE("failed to open output file %s", cdstPath);
            }

            // Append rest of the contents from the source file to the new file split by split.
            int curSplit = 1;
            while (curSplit < numSplits && isPass) {
                ofp = fopen(cdstPath, "r+");
                if (ofp) {
                    muxer = AMediaMuxer_append(fileno(ofp), AMEDIAMUXER_APPEND_TO_EXISTING_DATA);
                    if (muxer) {
                        ssize_t trackCount = AMediaMuxer_getTrackCount(muxer);
                        if (trackCount > 0) {
                            decltype(trackCount) tc = 0;
                            while(tc < trackCount) {
                                AMediaFormat* format = AMediaMuxer_getTrackFormat(muxer, tc);
                                int64_t val = 0;
                                if (AMediaFormat_getInt64(format,
                                            AMEDIAFORMAT_KEY_SAMPLE_TIME_BEFORE_APPEND, &val)) {
                                    ALOGV("sample-time-before-append:%lld", (long long)val);
                                }
                                ++tc;
                            }
                            mediaInfo->appendMedia(muxer, totalSampleCount*curSplit/numSplits,
                                                        totalSampleCount*(curSplit+1)/numSplits-1);
                        } else {
                            isPass = false;
                            ALOGE("no tracks in the file");
                        }
                        AMediaMuxer_delete(muxer);
                    } else {
                        isPass = false;
                        ALOGE("failed to create muxer");
                    }
                    fclose(ofp);
                    ofp = nullptr;
                    if (isPass) {
                        auto* mediaInfoDest = new MuxerNativeTestHelper(cdstPath, nullptr);
                        isPass = mediaInfoDest->isSubsetOf(mediaInfo);
                        delete mediaInfoDest;
                    }
                } else {
                    isPass = false;
                    ALOGE("failed to open output file %s", cdstPath);
                }
                ++curSplit;
            }
        }
        delete mediaInfo;
        env->ReleaseStringUTFChars(jdstPath, cdstPath);
        env->ReleaseStringUTFChars(jsrcPath, csrcPath);
    } else {
        isPass = false;
    }
    return static_cast<jboolean>(isPass);
}

/* Checks if opening a file to append data and closing it without actually appending data
 * works good in all append modes.
 */
static jboolean nativeTestNoSamples(JNIEnv* env, jobject, jint joutFormat, jstring jinPath,
                                        jstring joutPath) {
    bool isPass = true;
    if (__builtin_available(android __TRANSCODING_MIN_API__, *)) {
        const char* cinPath = env->GetStringUTFChars(jinPath, nullptr);
        const char* coutPath = env->GetStringUTFChars(joutPath, nullptr);
        ALOGV("cinPath:%s", cinPath);
        ALOGV("coutPath:%s", coutPath);
        auto* mediaInfo = new MuxerNativeTestHelper(cinPath);
        for (unsigned int mode = AMEDIAMUXER_APPEND_IGNORE_LAST_VIDEO_GOP;
                    mode <= AMEDIAMUXER_APPEND_TO_EXISTING_DATA; ++mode) {
            if (mediaInfo->getTrackCount() != 0) {
                // Create a new file and write media data to it.
                FILE *ofp = fopen(coutPath, "wbe+");
                if (ofp) {
                    AMediaMuxer *muxer = AMediaMuxer_new(fileno(ofp), (OutputFormat) joutFormat);
                    mediaInfo->muxMedia(muxer);
                    AMediaMuxer_delete(muxer);
                    fclose(ofp);
                } else {
                    isPass = false;
                    ALOGE("failed to open output file %s", coutPath);
                }
            } else {
                isPass = false;
                ALOGE("no tracks in input file");
            }
            ALOGV("after file close");
            FILE* ofp = fopen(coutPath, "r+");
            if (ofp) {
                ALOGV("create append muxer");
                // Open the new file in one of the append modes and close it without writing data.
                AMediaMuxer *muxer = AMediaMuxer_append(fileno(ofp), (AppendMode)mode);
                if (muxer) {
                    AMediaMuxer_start(muxer);
                    AMediaMuxer_stop(muxer);
                    ALOGV("delete append muxer");
                    AMediaMuxer_delete(muxer);
                } else {
                    isPass = false;
                    ALOGE("failed to create muxer");
                }
                fclose(ofp);
                ofp = nullptr;
            } else {
                isPass = false;
                ALOGE("failed to open output file to append %s", coutPath);
            }
            // Check if contents written in the new file match with contents in the original file.
            auto* mediaInfoOut = new MuxerNativeTestHelper(coutPath, nullptr);
            isPass = mediaInfoOut->isSubsetOf(mediaInfo);
            delete mediaInfoOut;
        }
        delete mediaInfo;
        env->ReleaseStringUTFChars(jinPath, cinPath);
        env->ReleaseStringUTFChars(joutPath, coutPath);
    } else {
        isPass = false;
    }
    return static_cast<jboolean>(isPass);
}

/*
 * Checks if appending media data in AMEDIAMUXER_APPEND_IGNORE_LAST_VIDEO_GOP mode works good.
 * Splits the contents of source file equally starting from one and increasing the number of
 * splits by one for every iteration.  Starts with writing first split into a new file and
 * appends the rest of the contents split by split.
 */
static jboolean nativeTestIgnoreLastGOPAppend(JNIEnv* env, jobject, jint joutFormat,
                                    jstring jsrcPath, jstring jdstPath) {
    bool isPass = true;
    if (__builtin_available(android __TRANSCODING_MIN_API__, *)) {
        const char* csrcPath = env->GetStringUTFChars(jsrcPath, nullptr);
        const char* cdstPath = env->GetStringUTFChars(jdstPath, nullptr);
        ALOGV("csrcPath:%s", csrcPath);
        ALOGV("cdstPath:%s", cdstPath);
        auto* mediaInfo = new MuxerNativeTestHelper(csrcPath);
        for (int numSplits = 1; numSplits <= 5 && isPass; ++numSplits) {
            ALOGV("numSplits:%d", numSplits);
            size_t totalSampleCount = 0;
            size_t totalSamplesWritten = 0;
            AMediaMuxer *muxer = nullptr;
            FILE* ofp = fopen(cdstPath, "w+");
            if (ofp) {
                // Start by writing first split into a new file.
                muxer = AMediaMuxer_new(fileno(ofp), (OutputFormat)joutFormat);
                if (muxer) {
                    for(int i = 0 ; i < mediaInfo->getTrackCount(); ++i ) {
                        ALOGV("getSampleCount:%d:%zu", i, mediaInfo->getSampleCount(i));
                        totalSampleCount += mediaInfo->getSampleCount(i);
                    }
                    mediaInfo->appendMedia(muxer, 0, (totalSampleCount/numSplits)-1);
                    totalSamplesWritten += (totalSampleCount/numSplits);
                    AMediaMuxer_delete(muxer);
                } else {
                    isPass = false;
                    ALOGE("Failed to create muxer");
                }
                fclose(ofp);
                ofp = nullptr;
                if (isPass) {
                    // Check if the contents in the new file is as same as in the source file.
                    auto* mediaInfoDest = new MuxerNativeTestHelper(cdstPath, nullptr);
                    isPass = mediaInfoDest->isSubsetOf(mediaInfo);
                    delete mediaInfoDest;
                }
            } else {
                isPass = false;
                ALOGE("failed to open output file %s", cdstPath);
            }

            // Append rest of the contents from the source file to the new file split by split.
            int curSplit = 1;
            while (curSplit < numSplits && isPass) {
                ofp = fopen(cdstPath, "r+");
                if (ofp) {
                    muxer = AMediaMuxer_append(fileno(ofp),
                                AMEDIAMUXER_APPEND_IGNORE_LAST_VIDEO_GOP);
                    if (muxer) {
                        auto trackCount = AMediaMuxer_getTrackCount(muxer);
                        if (trackCount > 0) {
                            decltype(trackCount) tc = 0;
                            int64_t* appendFromTime = new int64_t[trackCount]{0};
                            while(tc < trackCount) {
                                AMediaFormat* format = AMediaMuxer_getTrackFormat(muxer, tc);
                                int64_t val = 0;
                                if (AMediaFormat_getInt64(format,
                                            AMEDIAFORMAT_KEY_SAMPLE_TIME_BEFORE_APPEND, &val)) {
                                    ALOGV("sample-time-before-append:%lld", (long long)val);
                                    appendFromTime[tc] = val;
                                }
                                ++tc;
                            }
                            bool lastSplit = (curSplit == numSplits-1) ? true : false;
                            mediaInfo->appendMediaFromTime(muxer, appendFromTime,
                                totalSampleCount/numSplits + ((curSplit-1) * 30), lastSplit);
                            delete[] appendFromTime;
                        } else {
                            isPass = false;
                            ALOGE("no tracks in the file");
                        }
                        AMediaMuxer_delete(muxer);
                    } else {
                        isPass = false;
                        ALOGE("failed to create muxer");
                    }
                    fclose(ofp);
                    ofp = nullptr;
                    if (isPass) {
                        auto* mediaInfoDest = new MuxerNativeTestHelper(cdstPath, nullptr);
                        isPass = mediaInfoDest->isSubsetOf(mediaInfo);
                        delete mediaInfoDest;
                    }
                } else {
                    isPass = false;
                    ALOGE("failed to open output file %s", cdstPath);
                }
                ++curSplit;
            }
        }
        delete mediaInfo;
        env->ReleaseStringUTFChars(jdstPath, cdstPath);
        env->ReleaseStringUTFChars(jsrcPath, csrcPath);
    } else {
        isPass = false;
    }
    return static_cast<jboolean>(isPass);
}

int registerAndroidMediaV2CtsMuxerTestApi(JNIEnv* env) {
    const JNINativeMethod methodTable[] = {
            {"nativeTestSetOrientationHint", "(ILjava/lang/String;Ljava/lang/String;)Z",
             (void*)nativeTestSetOrientationHint},
            {"nativeTestSetLocation", "(ILjava/lang/String;Ljava/lang/String;)Z",
             (void*)nativeTestSetLocation},
            {"nativeTestGetTrackCount", "(Ljava/lang/String;Ljava/lang/String;II)Z",
             (void*)nativeTestGetTrackCount},
            {"nativeTestGetTrackFormat", "(Ljava/lang/String;Ljava/lang/String;I)Z",
             (void*)nativeTestGetTrackFormat},
    };
    jclass c = env->FindClass("android/mediav2/cts/MuxerTest$TestApi");
    return env->RegisterNatives(c, methodTable, sizeof(methodTable) / sizeof(JNINativeMethod));
}

int registerAndroidMediaV2CtsMuxerTestMultiTrack(JNIEnv* env) {
    const JNINativeMethod methodTable[] = {
            {"nativeTestMultiTrack",
             "(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z",
             (void*)nativeTestMultiTrack},
    };
    jclass c = env->FindClass("android/mediav2/cts/MuxerTest$TestMultiTrack");
    return env->RegisterNatives(c, methodTable, sizeof(methodTable) / sizeof(JNINativeMethod));
}

int registerAndroidMediaV2CtsMuxerTestOffsetPts(JNIEnv* env) {
    const JNINativeMethod methodTable[] = {
            {"nativeTestOffsetPts", "(ILjava/lang/String;Ljava/lang/String;[I)Z",
             (void*)nativeTestOffsetPts},
    };
    jclass c = env->FindClass("android/mediav2/cts/MuxerTest$TestOffsetPts");
    return env->RegisterNatives(c, methodTable, sizeof(methodTable) / sizeof(JNINativeMethod));
}

int registerAndroidMediaV2CtsMuxerTestSimpleMux(JNIEnv* env) {
    const JNINativeMethod methodTable[] = {
            {"nativeTestSimpleMux",
             "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z",
             (void*)nativeTestSimpleMux},
    };
    jclass c = env->FindClass("android/mediav2/cts/MuxerTest$TestSimpleMux");
    return env->RegisterNatives(c, methodTable, sizeof(methodTable) / sizeof(JNINativeMethod));
}

int registerAndroidMediaV2CtsMuxerTestSimpleAppend(JNIEnv* env) {
    const JNINativeMethod methodTable[] = {
            {"nativeTestSimpleAppend",
             "(ILjava/lang/String;Ljava/lang/String;)Z",
             (void*)nativeTestSimpleAppend},
            {"nativeTestAppendGetTrackCount",
             "(Ljava/lang/String;I)Z",
             (void*)nativeTestAppendGetTrackCount},
            {"nativeTestAppendGetTrackFormat", "(Ljava/lang/String;)Z",
             (void*)nativeTestAppendGetTrackFormat},
             {"nativeTestNoSamples",
              "(ILjava/lang/String;Ljava/lang/String;)Z",
              (void*)nativeTestNoSamples},
            {"nativeTestIgnoreLastGOPAppend",
             "(ILjava/lang/String;Ljava/lang/String;)Z",
             (void*)nativeTestIgnoreLastGOPAppend},
    };
    jclass c = env->FindClass("android/mediav2/cts/MuxerTest$TestSimpleAppend");
    return env->RegisterNatives(c, methodTable, sizeof(methodTable) / sizeof(JNINativeMethod));
}

extern int registerAndroidMediaV2CtsMuxerUnitTestApi(JNIEnv* env);

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    if (registerAndroidMediaV2CtsMuxerTestApi(env) != JNI_OK) return JNI_ERR;
    if (registerAndroidMediaV2CtsMuxerTestMultiTrack(env) != JNI_OK) return JNI_ERR;
    if (registerAndroidMediaV2CtsMuxerTestOffsetPts(env) != JNI_OK) return JNI_ERR;
    if (registerAndroidMediaV2CtsMuxerTestSimpleMux(env) != JNI_OK) return JNI_ERR;
    if (registerAndroidMediaV2CtsMuxerTestSimpleAppend(env) != JNI_OK) return JNI_ERR;
    if (registerAndroidMediaV2CtsMuxerUnitTestApi(env) != JNI_OK) return JNI_ERR;
    return JNI_VERSION_1_6;
}
