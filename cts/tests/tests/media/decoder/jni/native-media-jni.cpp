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

/* Original code copied from NDK Native-media sample code */

//#define LOG_NDEBUG 0
#define LOG_TAG "NativeMedia"
#include <log/log.h>

#include <assert.h>
#include <jni.h>
#include <mutex>
#include <queue>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#include <android/native_window_jni.h>

#include "media/NdkMediaExtractor.h"
#include "media/NdkMediaCodec.h"
#include "media/NdkMediaDataSource.h"
#include "media/NdkMediaFormat.h"
template <class T>
class simplevector {
    T *storage;
    int capacity;
    int numfilled;
public:
    simplevector() {
        capacity = 16;
        numfilled = 0;
        storage = new T[capacity];
    }
    ~simplevector() {
        delete[] storage;
    }

    void add(T item) {
        if (numfilled == capacity) {
            T *old = storage;
            capacity *= 2;
            storage = new T[capacity];
            for (int i = 0; i < numfilled; i++) {
                storage[i] = old[i];
            }
            delete[] old;
        }
        storage[numfilled] = item;
        numfilled++;
    }

    int size() {
        return numfilled;
    }

    T* data() {
        return storage;
    }
};

struct FdDataSource {

    FdDataSource(int fd, jlong offset, jlong size)
        : mFd(dup(fd)),
          mOffset(offset),
          mSize(size) {
    }

    ssize_t readAt(off64_t offset, void *data, size_t size) {
        ssize_t ssize = size;
        if (!data || offset < 0 || offset + ssize < offset) {
            return -1;
        }
        if (offset >= mSize) {
            return 0; // EOS
        }
        if (offset + ssize > mSize) {
            ssize = mSize - offset;
        }
        if (lseek(mFd, mOffset + offset, SEEK_SET) < 0) {
            return -1;
        }
        return read(mFd, data, ssize);
    }

    ssize_t getSize() {
        return mSize;
    }

    void close() {
        ::close(mFd);
    }

private:

    int mFd;
    off64_t mOffset;
    int64_t mSize;

};

static ssize_t FdSourceReadAt(void *userdata, off64_t offset, void *data, size_t size) {
    FdDataSource *src = (FdDataSource*) userdata;
    return src->readAt(offset, data, size);
}

static ssize_t FdSourceGetSize(void *userdata) {
    FdDataSource *src = (FdDataSource*) userdata;
    return src->getSize();
}

static void FdSourceClose(void *userdata) {
    FdDataSource *src = (FdDataSource*) userdata;
    src->close();
}

class CallbackData {
    std::mutex mMutex;
    std::queue<int32_t> mInputBufferIds;
    std::queue<int32_t> mOutputBufferIds;
    std::queue<AMediaCodecBufferInfo> mOutputBufferInfos;
    std::queue<AMediaFormat*> mFormats;

public:
    CallbackData() { }

    ~CallbackData() {
        mMutex.lock();
        while (!mFormats.empty()) {
            AMediaFormat* format = mFormats.front();
            mFormats.pop();
            AMediaFormat_delete(format);
        }
        mMutex.unlock();
    }

    void addInputBufferId(int32_t index) {
        mMutex.lock();
        mInputBufferIds.push(index);
        mMutex.unlock();
    }

    int32_t getInputBufferId() {
        int32_t id = -1;
        mMutex.lock();
        if (!mInputBufferIds.empty()) {
            id = mInputBufferIds.front();
            mInputBufferIds.pop();
        }
        mMutex.unlock();
        return id;
    }

    void addOutputBuffer(int32_t index, AMediaCodecBufferInfo *bufferInfo) {
        mMutex.lock();
        mOutputBufferIds.push(index);
        mOutputBufferInfos.push(*bufferInfo);
        mMutex.unlock();
    }

    void addOutputFormat(AMediaFormat *format) {
        mMutex.lock();
        mOutputBufferIds.push(AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED);
        mFormats.push(format);
        mMutex.unlock();
    }

    int32_t getOutput(AMediaCodecBufferInfo *bufferInfo, AMediaFormat **format) {
        int32_t id = AMEDIACODEC_INFO_TRY_AGAIN_LATER;
        mMutex.lock();
        if (!mOutputBufferIds.empty()) {
            id = mOutputBufferIds.front();
            mOutputBufferIds.pop();

            if (id >= 0) {
                *bufferInfo = mOutputBufferInfos.front();
                mOutputBufferInfos.pop();
            } else {  // AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED
                *format = mFormats.front();
                mFormats.pop();
            }
        }
        mMutex.unlock();
        return id;
    }
};

static void OnInputAvailableCB(
        AMediaCodec * /* aMediaCodec */,
        void *userdata,
        int32_t index) {
    ALOGV("OnInputAvailableCB: index(%d)", index);
    CallbackData *callbackData = (CallbackData *)userdata;
    callbackData->addInputBufferId(index);
}

static void OnOutputAvailableCB(
        AMediaCodec * /* aMediaCodec */,
        void *userdata,
        int32_t index,
        AMediaCodecBufferInfo *bufferInfo) {
    ALOGV("OnOutputAvailableCB: index(%d), (%d, %d, %lld, 0x%x)",
          index, bufferInfo->offset, bufferInfo->size,
          (long long)bufferInfo->presentationTimeUs, bufferInfo->flags);
    CallbackData *callbackData = (CallbackData *)userdata;
    callbackData->addOutputBuffer(index, bufferInfo);
}

static void OnFormatChangedCB(
        AMediaCodec * /* aMediaCodec */,
        void *userdata,
        AMediaFormat *format) {
    ALOGV("OnFormatChangedCB: format(%s)", AMediaFormat_toString(format));
    CallbackData *callbackData = (CallbackData *)userdata;
    callbackData->addOutputFormat(format);
}

static void OnErrorCB(
        AMediaCodec * /* aMediaCodec */,
        void * /* userdata */,
        media_status_t err,
        int32_t actionCode,
        const char *detail) {
    ALOGV("OnErrorCB: err(%d), actionCode(%d), detail(%s)", err, actionCode, detail);
}

static int adler32(const uint8_t *input, int len) {

    int a = 1;
    int b = 0;
    for (int i = 0; i < len; i++) {
        a += input[i];
        b += a;
        a = a % 65521;
        b = b % 65521;
    }
    int ret = b * 65536 + a;
    ALOGV("adler %d/%d", len, ret);
    return ret;
}

static int checksum(const uint8_t *in, int len, AMediaFormat *format) {
    int width, stride, height;
    if (!AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_WIDTH, &width)) {
        width = len;
    }
    if (!AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_STRIDE, &stride)) {
        stride = width;
    }
    if (!AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_HEIGHT, &height)) {
        height = 1;
    }
    uint8_t *bb = new uint8_t[width * height];
    for (int i = 0; i < height; i++) {
        memcpy(bb + i * width, in + i * stride, width);
    }
    // bb is filled with data
    int sum = adler32(bb, width * height);
    delete[] bb;
    return sum;
}

extern "C" jobject Java_android_media_decoder_cts_NativeDecoderTest_getDecodedDataNative(
        JNIEnv *env, jclass /*clazz*/, int fd, jlong offset, jlong size, jboolean wrapFd,
        jboolean useCallback) {
    ALOGV("getDecodedDataNative");

    FdDataSource fdSrc(fd, offset, size);
    AMediaExtractor *ex = AMediaExtractor_new();
    AMediaDataSource *ndkSrc = AMediaDataSource_new();

    int err;
    if (wrapFd) {
        AMediaDataSource_setUserdata(ndkSrc, &fdSrc);
        AMediaDataSource_setReadAt(ndkSrc, FdSourceReadAt);
        AMediaDataSource_setGetSize(ndkSrc, FdSourceGetSize);
        AMediaDataSource_setClose(ndkSrc, FdSourceClose);
        err = AMediaExtractor_setDataSourceCustom(ex, ndkSrc);
    } else {
        err = AMediaExtractor_setDataSourceFd(ex, fd, offset, size);
    }
    if (err != 0) {
        ALOGE("setDataSource error: %d", err);
        return NULL;
    }

    int numtracks = AMediaExtractor_getTrackCount(ex);

    std::unique_ptr<AMediaCodec*[]> codec(new AMediaCodec*[numtracks]());
    std::unique_ptr<AMediaFormat*[]> format(new AMediaFormat*[numtracks]());
    std::unique_ptr<bool[]> sawInputEOS(new bool[numtracks]);
    std::unique_ptr<bool[]> sawOutputEOS(new bool[numtracks]);
    std::unique_ptr<simplevector<int>[]> sizes(new simplevector<int>[numtracks]);
    std::unique_ptr<CallbackData[]> callbackData(new CallbackData[numtracks]);

    ALOGV("input has %d tracks", numtracks);
    for (int i = 0; i < numtracks; i++) {
        AMediaFormat *format = AMediaExtractor_getTrackFormat(ex, i);
        const char *s = AMediaFormat_toString(format);
        ALOGI("track %d format: %s", i, s);
        const char *mime;
        if (!AMediaFormat_getString(format, AMEDIAFORMAT_KEY_MIME, &mime)) {
            ALOGE("no mime type");
            return NULL;
        } else if (!strncmp(mime, "audio/", 6) || !strncmp(mime, "video/", 6)) {
            codec[i] = AMediaCodec_createDecoderByType(mime);
            AMediaCodec_configure(codec[i], format, NULL /* surface */, NULL /* crypto */, 0);
            if (useCallback) {
                AMediaCodecOnAsyncNotifyCallback aCB = {
                    OnInputAvailableCB,
                    OnOutputAvailableCB,
                    OnFormatChangedCB,
                    OnErrorCB
                };
                AMediaCodec_setAsyncNotifyCallback(codec[i], aCB, &callbackData[i]);
            }
            AMediaCodec_start(codec[i]);
            sawInputEOS[i] = false;
            sawOutputEOS[i] = false;
        } else {
            ALOGE("expected audio or video mime type, got %s", mime);
            return NULL;
        }
        AMediaFormat_delete(format);
        AMediaExtractor_selectTrack(ex, i);
    }
    int eosCount = 0;
    while(eosCount < numtracks) {
        int t = AMediaExtractor_getSampleTrackIndex(ex);
        if (t >=0) {
            ssize_t bufidx;
            if (useCallback) {
                bufidx = callbackData[t].getInputBufferId();
            } else {
                bufidx = AMediaCodec_dequeueInputBuffer(codec[t], 5000);
            }
            ALOGV("track %d, input buffer %zd", t, bufidx);
            if (bufidx >= 0) {
                size_t bufsize;
                uint8_t *buf = AMediaCodec_getInputBuffer(codec[t], bufidx, &bufsize);
                int sampleSize = AMediaExtractor_readSampleData(ex, buf, bufsize);
                ALOGV("read %d", sampleSize);
                if (sampleSize < 0) {
                    sampleSize = 0;
                    sawInputEOS[t] = true;
                    ALOGV("EOS");
                    //break;
                }
                int64_t presentationTimeUs = AMediaExtractor_getSampleTime(ex);

                AMediaCodec_queueInputBuffer(codec[t], bufidx, 0, sampleSize, presentationTimeUs,
                        sawInputEOS[t] ? AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM : 0);
                AMediaExtractor_advance(ex);
            }
        } else {
            ALOGV("@@@@ no more input samples");
            for (int tt = 0; tt < numtracks; tt++) {
                if (!sawInputEOS[tt]) {
                    // we ran out of samples without ever signaling EOS to the codec,
                    // so do that now
                    int bufidx;
                    if (useCallback) {
                        bufidx = callbackData[tt].getInputBufferId();
                    } else {
                        bufidx = AMediaCodec_dequeueInputBuffer(codec[tt], 5000);
                    }
                    if (bufidx >= 0) {
                        AMediaCodec_queueInputBuffer(codec[tt], bufidx, 0, 0, 0,
                                AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM);
                        sawInputEOS[tt] = true;
                    }
                }
            }
        }

        // check all codecs for available data
        AMediaCodecBufferInfo info;
        AMediaFormat *outputFormat;
        for (int tt = 0; tt < numtracks; tt++) {
            if (!sawOutputEOS[tt]) {
                int status;
                if (useCallback) {
                    status = callbackData[tt].getOutput(&info, &outputFormat);
                } else {
                    status = AMediaCodec_dequeueOutputBuffer(codec[tt], &info, 1);
                }
                ALOGV("dequeueoutput on track %d: %d", tt, status);
                if (status >= 0) {
                    if (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) {
                        ALOGV("EOS on track %d", tt);
                        sawOutputEOS[tt] = true;
                        eosCount++;
                    }
                    ALOGV("got decoded buffer for track %d, size %d", tt, info.size);
                    if (info.size > 0) {
                        size_t bufsize;
                        uint8_t *buf = AMediaCodec_getOutputBuffer(codec[tt], status, &bufsize);
                        int adler = checksum(buf, info.size, format[tt]);
                        sizes[tt].add(adler);
                    }
                    AMediaCodec_releaseOutputBuffer(codec[tt], status, false);
                } else if (status == AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED) {
                    ALOGV("output buffers changed for track %d", tt);
                } else if (status == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
                    if (format[tt] != NULL) {
                        AMediaFormat_delete(format[tt]);
                    }
                    if (useCallback) {
                        format[tt] = outputFormat;
                    } else {
                        format[tt] = AMediaCodec_getOutputFormat(codec[tt]);
                    }
                    ALOGV("format changed for track %d: %s", tt, AMediaFormat_toString(format[tt]));
                } else if (status == AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
                    ALOGV("no output buffer right now for track %d", tt);
                } else {
                    ALOGV("unexpected info code for track %d : %d", tt, status);
                }
            } else {
                ALOGV("already at EOS on track %d", tt);
            }
        }
    }
    ALOGV("decoding loop done");

    // allocate java int array for result and return it
    int numsamples = 0;
    for (int i = 0; i < numtracks; i++) {
        numsamples += sizes[i].size();
    }
    ALOGV("checksums: %d", numsamples);
    jintArray ret = env->NewIntArray(numsamples);
    jboolean isCopy;
    jint *org = env->GetIntArrayElements(ret, &isCopy);
    jint *dst = org;
    for (int i = 0; i < numtracks; i++) {
        int *data = sizes[i].data();
        int len = sizes[i].size();
        ALOGV("copying %d", len);
        for (int j = 0; j < len; j++) {
            *dst++ = data[j];
        }
    }
    env->ReleaseIntArrayElements(ret, org, 0);

    for (int i = 0; i < numtracks; i++) {
        AMediaFormat_delete(format[i]);
        AMediaCodec_stop(codec[i]);
        AMediaCodec_delete(codec[i]);
    }
    AMediaExtractor_delete(ex);
    AMediaDataSource_delete(ndkSrc);
    return ret;
}

static bool arePtsListsIdentical(const std::vector<u_long>& refArray,
                                 const std::vector<u_long>& testArray) {
    bool isEqual = true;
    u_long i;
    if (refArray.size() != testArray.size()) {
        ALOGE("Expected and received timestamps list sizes are not identical");
        ALOGE("Expected pts list size is %zu", refArray.size());
        ALOGE("Received pts list size is %zu", testArray.size());
        isEqual = false;
    } else {
        for (i = 0; i < refArray.size(); i++) {
            if (refArray[i] != testArray[i]) {
                isEqual = false;
            }
        }
    }

    if (!isEqual) {
        for (i = 0; i < std::min(refArray.size(), testArray.size()); i++) {
            ALOGE("Frame idx %3lu, expected pts %9luus, received pts %9luus", i, refArray[i],
                  testArray[i]);
        }
        if (refArray.size() < testArray.size()) {
            for (i = refArray.size(); i < testArray.size(); i++) {
                ALOGE("Frame idx %3lu, expected pts %11s, received pts %9luus", i, "EMPTY",
                      testArray[i]);
            }
        } else if (refArray.size() > testArray.size()) {
            for (i = testArray.size(); i < refArray.size(); i++) {
                ALOGE("Frame idx %3lu, expected pts %9luus, received pts %11s", i, refArray[i],
                      "EMPTY");
            }
        }
    }

    return isEqual;
}

bool testNonTunneledTrickPlay(const char *fileName, ANativeWindow *pWindow, bool isAsync) {
    FILE *fp = fopen(fileName, "rbe");
    if (fp == nullptr) {
        ALOGE("Unable to open input file: %s", fileName);
        return false;
    }

    struct stat buf {};
    AMediaExtractor *extractor = AMediaExtractor_new();
    if (!fstat(fileno(fp), &buf)) {
        media_status_t res = AMediaExtractor_setDataSourceFd(extractor, fileno(fp), 0, buf.st_size);
        if (res != AMEDIA_OK) {
            ALOGE("AMediaExtractor_setDataSourceFd failed with error %d", res);
            AMediaExtractor_delete(extractor);
            return false;
        }
    }

    int trackIndex = -1;
    for (size_t trackID = 0; trackID < AMediaExtractor_getTrackCount(extractor); trackID++) {
        AMediaFormat *format = AMediaExtractor_getTrackFormat(extractor, trackID);
        const char *mediaType = nullptr;
        AMediaFormat_getString(format, AMEDIAFORMAT_KEY_MIME, &mediaType);
        bool isVideo = strncmp(mediaType, "video/", strlen("video/")) == 0;
        AMediaFormat_delete(format);
        if (isVideo) {
            trackIndex = trackID;
            ALOGV("mediaType = %s, prefix = \"video/\", trackId = %zu", mediaType, trackID);
            break;
        }
    }

    if (trackIndex < 0) {
        ALOGE("No video track found for track index: %d", trackIndex);
        AMediaExtractor_delete(extractor);
        return false;
    }

    AMediaExtractor_selectTrack(extractor, trackIndex);
    AMediaFormat *format = AMediaExtractor_getTrackFormat(extractor, trackIndex);
    const char *mediaType = nullptr;
    AMediaFormat_getString(format, AMEDIAFORMAT_KEY_MIME, &mediaType);
    AMediaCodec *codec = AMediaCodec_createDecoderByType(mediaType);
    CallbackData *callbackData = new CallbackData();

    if (isAsync) {
        AMediaCodecOnAsyncNotifyCallback callBack = {OnInputAvailableCB, OnOutputAvailableCB,
                                                     OnFormatChangedCB, OnErrorCB};
        auto status = AMediaCodec_setAsyncNotifyCallback(codec, callBack, callbackData);
        if (status != AMEDIA_OK) {
            ALOGE("failed to set async callback");
            delete callbackData;
            AMediaFormat_delete(format);
            AMediaCodec_delete(codec);
            AMediaExtractor_delete(extractor);
            return false;
        }
    }
    AMediaCodec_configure(codec, format, pWindow, nullptr, 0);
    AMediaCodec_start(codec);

    std::atomic<bool> done(false);
    std::vector<u_long> expectedPresentationTimes;
    std::vector<u_long> receivedPresentationTimes;
    bool mEosQueued = false;
    int mDecodeOnlyCounter = 0;
    // keep looping until the codec receives the EOS frame
    while (!done.load()) {
        // enqueue
        if (!mEosQueued) {
            size_t inBufSize;
            int32_t id;
            if (isAsync) {
                id = callbackData->getInputBufferId();
            } else {
                id = AMediaCodec_dequeueInputBuffer(codec, 5000);
            }
            if (id >= 0) {
                uint8_t *inBuf = AMediaCodec_getInputBuffer(codec, id, &inBufSize);
                if (inBuf == nullptr) {
                    ALOGE("AMediaCodec_getInputBuffer returned nullptr");
                    delete callbackData;
                    AMediaFormat_delete(format);
                    AMediaCodec_stop(codec);
                    AMediaCodec_delete(codec);
                    AMediaExtractor_delete(extractor);
                    return false;
                }
                ssize_t sampleSize = AMediaExtractor_readSampleData(extractor, inBuf, inBufSize);
                int64_t presentationTime = AMediaExtractor_getSampleTime(extractor);
                uint32_t flags = 0;
                if (sampleSize < 0) {
                    flags = AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM;
                    sampleSize = 0;
                    mEosQueued = true;
                } else if (mDecodeOnlyCounter % 2 == 0) {
                    flags = AMEDIACODEC_BUFFER_FLAG_DECODE_ONLY;
                } else {
                    expectedPresentationTimes.push_back(presentationTime);
                }
                mDecodeOnlyCounter++;
                AMediaCodec_queueInputBuffer(codec, id, 0, sampleSize, presentationTime, flags);
                AMediaExtractor_advance(extractor);
            }
        }

        // dequeue
        AMediaCodecBufferInfo bufferInfo;
        AMediaFormat *outputFormat;
        int id;
        if (isAsync) {
            id = callbackData->getOutput(&bufferInfo, &outputFormat);
        } else {
            id = AMediaCodec_dequeueOutputBuffer(codec, &bufferInfo, 1);
        }
        if (id >= 0) {
            AMediaCodec_releaseOutputBuffer(codec, id, false);
            if ((bufferInfo.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) != 0) {
                done.store(true);
            } else {
                receivedPresentationTimes.push_back(bufferInfo.presentationTimeUs);
            }
        }
    }

    delete callbackData;
    AMediaFormat_delete(format);
    AMediaCodec_stop(codec);
    AMediaCodec_delete(codec);
    AMediaExtractor_delete(extractor);
    std::sort(expectedPresentationTimes.begin(), expectedPresentationTimes.end());
    return arePtsListsIdentical(expectedPresentationTimes, receivedPresentationTimes);
}

extern "C" jboolean Java_android_media_decoder_cts_DecodeOnlyTest_nativeTestNonTunneledTrickPlay(
        JNIEnv *env, jclass /*clazz*/, jstring jFileName, jobject surface, jboolean isAsync) {
    ALOGD("nativeTestNonTunneledTrickPlay");
    const char *cFileName = env->GetStringUTFChars(jFileName, nullptr);
    ANativeWindow *window = surface ? ANativeWindow_fromSurface(env, surface) : nullptr;
    bool isPass = testNonTunneledTrickPlay(cFileName, window, isAsync);
    env->ReleaseStringUTFChars(jFileName, cFileName);
    return static_cast<jboolean>(isPass);
}
