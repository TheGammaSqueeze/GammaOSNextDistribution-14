/*
 * Copyright (C) 2020 The Android Open Source Project
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
#define LOG_TAG "NativeCodecDecoderTestCommon"
#include <log/log.h>

#include <android/native_window_jni.h>
#include <jni.h>
#include <media/NdkMediaExtractor.h>
#include <sys/stat.h>

#include <array>
#include <fstream>
#include <string>

#include "NativeCodecDecoderTestCommon.h"
#include "NativeCodecTestBase.h"
#include "NativeMediaCommon.h"

class CodecDecoderTest final : public CodecTestBase {
  private:
    bool mIsInterlaced;
    uint8_t* mRefData;
    size_t mRefLength;
    AMediaExtractor* mExtractor;
    AMediaFormat* mInpDecFormat;
    AMediaFormat* mInpDecDupFormat;
    std::vector<std::pair<void*, size_t>> mCsdBuffers;
    int mCurrCsdIdx;
    ANativeWindow* mWindow;

    void setUpAudioReference(const char* refFile);
    void deleteReference();
    bool setUpExtractor(const char* srcFile, int colorFormat);
    void deleteExtractor();
    bool configureCodec(AMediaFormat* format, bool isAsync, bool signalEOSWithLastFrame,
                        bool isEncoder) override;
    bool enqueueInput(size_t bufferIndex) override;
    bool dequeueOutput(size_t bufferIndex, AMediaCodecBufferInfo* bufferInfo) override;
    bool isTestStateValid() override;
    bool isOutputFormatOk(AMediaFormat* configFormat);
    bool queueCodecConfig();
    bool enqueueCodecConfig(int32_t bufferIndex);
    bool decodeToMemory(const char* decoder, AMediaFormat* format, int frameLimit,
                        OutputManager* ref, int64_t pts, SeekMode mode);

  public:
    explicit CodecDecoderTest(const char* mediaType, ANativeWindow* window);
    ~CodecDecoderTest();

    bool testSimpleDecode(const char* decoder, const char* testFile, const char* refFile,
                          int colorFormat, float rmsError, uLong checksum);
    bool testFlush(const char* decoder, const char* testFile, int colorFormat);
    bool testOnlyEos(const char* decoder, const char* testFile, int colorFormat);
    bool testSimpleDecodeQueueCSD(const char* decoder, const char* testFile, int colorFormat);
};

CodecDecoderTest::CodecDecoderTest(const char* mediaType, ANativeWindow* window)
    : CodecTestBase(mediaType),
      mRefData(nullptr),
      mRefLength(0),
      mExtractor(nullptr),
      mInpDecFormat(nullptr),
      mInpDecDupFormat(nullptr),
      mCurrCsdIdx(0),
      mWindow{window} {}

CodecDecoderTest::~CodecDecoderTest() {
    deleteReference();
    deleteExtractor();
}

void CodecDecoderTest::setUpAudioReference(const char* refFile) {
    FILE* fp = fopen(refFile, "rbe");
    struct stat buf {};
    if (fp && !fstat(fileno(fp), &buf)) {
        deleteReference();
        mRefLength = buf.st_size;
        mRefData = new uint8_t[mRefLength];
        fread(mRefData, sizeof(uint8_t), mRefLength, fp);
    } else {
        ALOGE("unable to open input file %s", refFile);
    }
    if (fp) fclose(fp);
}

void CodecDecoderTest::deleteReference() {
    if (mRefData) {
        delete[] mRefData;
        mRefData = nullptr;
    }
    mRefLength = 0;
}

bool CodecDecoderTest::setUpExtractor(const char* srcFile, int colorFormat) {
    FILE* fp = fopen(srcFile, "rbe");
    RETURN_IF_NULL(fp, StringFormat("Unable to open file %s", srcFile))
    struct stat buf {};
    if (!fstat(fileno(fp), &buf)) {
        deleteExtractor();
        mExtractor = AMediaExtractor_new();
        media_status_t res =
                AMediaExtractor_setDataSourceFd(mExtractor, fileno(fp), 0, buf.st_size);
        if (res != AMEDIA_OK) {
            deleteExtractor();
            RETURN_IF_TRUE(true,
                           StringFormat("AMediaExtractor_setDataSourceFd failed with error %d",
                                        res))
        } else {
            mBytesPerSample = (colorFormat == COLOR_FormatYUVP010) ? 2 : 1;
            for (size_t trackID = 0; trackID < AMediaExtractor_getTrackCount(mExtractor);
                 trackID++) {
                AMediaFormat* currFormat = AMediaExtractor_getTrackFormat(mExtractor, trackID);
                const char* mediaType = nullptr;
                AMediaFormat_getString(currFormat, AMEDIAFORMAT_KEY_MIME, &mediaType);
                if (mediaType && strcmp(mMediaType, mediaType) == 0) {
                    AMediaExtractor_selectTrack(mExtractor, trackID);
                    if (!mIsAudio) {
                        AMediaFormat_setInt32(currFormat, AMEDIAFORMAT_KEY_COLOR_FORMAT,
                                              colorFormat);
                    }
                    mInpDecFormat = currFormat;
                    // TODO: determine this from the extractor format when it becomes exposed.
                    mIsInterlaced = strstr(srcFile, "_interlaced_") != nullptr;
                    break;
                }
                AMediaFormat_delete(currFormat);
            }
        }
    }
    if (fp) fclose(fp);
    RETURN_IF_NULL(mInpDecFormat,
                   StringFormat("No track with media type %s found in file: %s", mMediaType,
                                srcFile))
    return true;
}

void CodecDecoderTest::deleteExtractor() {
    if (mExtractor) {
        AMediaExtractor_delete(mExtractor);
        mExtractor = nullptr;
    }
    if (mInpDecFormat) {
        AMediaFormat_delete(mInpDecFormat);
        mInpDecFormat = nullptr;
    }
    if (mInpDecDupFormat) {
        AMediaFormat_delete(mInpDecDupFormat);
        mInpDecDupFormat = nullptr;
    }
}

bool CodecDecoderTest::configureCodec(AMediaFormat* format, bool isAsync,
                                      bool signalEOSWithLastFrame, bool isEncoder) {
    resetContext(isAsync, signalEOSWithLastFrame);
    mTestEnv = "###################      Test Environment       #####################\n";
    {
        char* name = nullptr;
        media_status_t val = AMediaCodec_getName(mCodec, &name);
        if (AMEDIA_OK != val) {
            mErrorLogs = StringFormat("%s with error %d \n", "AMediaCodec_getName failed", val);
            return false;
        }
        if (!name) {
            mErrorLogs = std::string{"AMediaCodec_getName returned null"};
            return false;
        }
        mTestEnv.append(StringFormat("Component name %s \n", name));
        AMediaCodec_releaseName(mCodec, name);
    }
    mTestEnv.append(StringFormat("Format under test :- %s \n", AMediaFormat_toString(format)));
    mTestEnv.append(StringFormat("Component operating in :- %s mode \n",
                                 (isAsync ? "asynchronous" : "synchronous")));
    mTestEnv.append(
            StringFormat("Component received input eos :- %s \n",
                         (signalEOSWithLastFrame ? "with full buffer" : "with empty buffer")));
    RETURN_IF_FAIL(mAsyncHandle.setCallBack(mCodec, isAsync),
                   "AMediaCodec_setAsyncNotifyCallback failed")
    RETURN_IF_FAIL(AMediaCodec_configure(mCodec, format, mWindow, nullptr,
                                         isEncoder ? AMEDIACODEC_CONFIGURE_FLAG_ENCODE : 0),
                   "AMediaCodec_configure failed")
    return true;
}

bool CodecDecoderTest::enqueueCodecConfig(int32_t bufferIndex) {
    size_t bufSize;
    uint8_t* buf = AMediaCodec_getInputBuffer(mCodec, bufferIndex, &bufSize);
    RETURN_IF_NULL(buf, std::string{"AMediaCodec_getInputBuffer returned nullptr"})
    void* csdBuffer = mCsdBuffers[mCurrCsdIdx].first;
    size_t csdSize = mCsdBuffers[mCurrCsdIdx].second;
    RETURN_IF_TRUE(bufSize < csdSize,
                   StringFormat("csd exceeds input buffer size, csdSize: %zu bufSize: %zu", csdSize,
                                bufSize))
    memcpy((void*)buf, csdBuffer, csdSize);
    uint32_t flags = AMEDIACODEC_BUFFER_FLAG_CODEC_CONFIG;
    RETURN_IF_FAIL(AMediaCodec_queueInputBuffer(mCodec, bufferIndex, 0, csdSize, 0, flags),
                   "AMediaCodec_queueInputBuffer failed")
    return !hasSeenError();
}

bool CodecDecoderTest::enqueueInput(size_t bufferIndex) {
    if (AMediaExtractor_getSampleSize(mExtractor) < 0) {
        return enqueueEOS(bufferIndex);
    } else {
        uint32_t flags = 0;
        size_t bufSize;
        uint8_t* buf = AMediaCodec_getInputBuffer(mCodec, bufferIndex, &bufSize);
        RETURN_IF_NULL(buf, std::string{"AMediaCodec_getInputBuffer returned nullptr"})
        ssize_t size = AMediaExtractor_getSampleSize(mExtractor);
        int64_t pts = AMediaExtractor_getSampleTime(mExtractor);
        RETURN_IF_TRUE(size > bufSize,
                       StringFormat("extractor sample size exceeds codec input buffer size %zu %zu",
                                    size, bufSize))
        RETURN_IF_TRUE(size != AMediaExtractor_readSampleData(mExtractor, buf, bufSize),
                       std::string{"AMediaExtractor_readSampleData failed"})
        if (!AMediaExtractor_advance(mExtractor) && mSignalEOSWithLastFrame) {
            flags |= AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM;
            mSawInputEOS = true;
        }
        RETURN_IF_FAIL(AMediaCodec_queueInputBuffer(mCodec, bufferIndex, 0, size, pts, flags),
                       "AMediaCodec_queueInputBuffer failed")
        ALOGV("input: id: %zu  size: %zu  pts: %" PRId64 "  flags: %d", bufferIndex, size, pts,
              flags);
        if (size > 0) {
            mOutputBuff->saveInPTS(pts);
            mInputCount++;
        }
    }
    return !hasSeenError();
}

bool CodecDecoderTest::dequeueOutput(size_t bufferIndex, AMediaCodecBufferInfo* info) {
    if ((info->flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) != 0) {
        mSawOutputEOS = true;
    }
    if (info->size > 0 && (info->flags & AMEDIACODEC_BUFFER_FLAG_CODEC_CONFIG) == 0) {
        if (mSaveToMem) {
            size_t buffSize;
            uint8_t* buf = AMediaCodec_getOutputBuffer(mCodec, bufferIndex, &buffSize);
            RETURN_IF_NULL(buf, std::string{"AMediaCodec_getOutputBuffer returned nullptr"})
            // NdkMediaCodec calls ABuffer::data, which already adds offset
            info->offset = 0;
            if (mIsAudio) {
                mOutputBuff->saveToMemory(buf, info);
                mOutputBuff->updateChecksum(buf, info);
            } else {
                AMediaFormat* format =
                        mIsCodecInAsyncMode ? mAsyncHandle.getOutputFormat() : mOutFormat;
                int32_t width, height, stride;
                AMediaFormat_getInt32(format, "width", &width);
                AMediaFormat_getInt32(format, "height", &height);
                AMediaFormat_getInt32(format, "stride", &stride);
                mOutputBuff->updateChecksum(buf, info, width, height, stride, mBytesPerSample);
            }
        }
        mOutputBuff->saveOutPTS(info->presentationTimeUs);
        mOutputCount++;
    }
    ALOGV("output: id: %zu  size: %d  pts: %" PRId64 "  flags: %d", bufferIndex, info->size,
          info->presentationTimeUs, info->flags);
    RETURN_IF_FAIL(AMediaCodec_releaseOutputBuffer(mCodec, bufferIndex, mWindow != nullptr),
                   "AMediaCodec_releaseOutputBuffer failed")
    return !hasSeenError();
}

bool CodecDecoderTest::isTestStateValid() {
    if (!CodecTestBase::isTestStateValid()) return false;
    RETURN_IF_FALSE(mOutputBuff->isPtsStrictlyIncreasing(mPrevOutputPts),
                    std::string{"Output timestamps are not strictly increasing \n"}.append(
                            mOutputBuff->getErrorMsg()))
    RETURN_IF_TRUE(mIsVideo && !mIsInterlaced &&
                   !mOutputBuff->isOutPtsListIdenticalToInpPtsList(false),
                   std::string{"Input pts list and Output pts list are not identical \n"}.append(
                           mOutputBuff->getErrorMsg()))
    return true;
}

bool CodecDecoderTest::isOutputFormatOk(AMediaFormat* configFormat) {
    RETURN_IF_TRUE(mIsCodecInAsyncMode ? !mAsyncHandle.hasOutputFormatChanged()
                                       : !mSignalledOutFormatChanged,
                   std::string{"Input test file format is not same as default format of component, "
                               "but test did not receive INFO_OUTPUT_FORMAT_CHANGED signal.\n"})
    RETURN_IF_TRUE(!isFormatSimilar(configFormat,
                                    mIsCodecInAsyncMode ? mAsyncHandle.getOutputFormat()
                                                        : mOutFormat),
                   StringFormat("Configured input format and received output format are not "
                                "similar. \nConfigured Input format is :- %s \nReceived Output "
                                "format is :- %s \n",
                                AMediaFormat_toString(configFormat),
                                mIsCodecInAsyncMode ? mAsyncHandle.getOutputFormat() : mOutFormat))
    return true;
}

bool CodecDecoderTest::queueCodecConfig() {
    bool isOk = true;
    if (mIsCodecInAsyncMode) {
        for (mCurrCsdIdx = 0; !hasSeenError() && isOk && mCurrCsdIdx < mCsdBuffers.size();
             mCurrCsdIdx++) {
            callbackObject element = mAsyncHandle.getInput();
            if (element.bufferIndex >= 0) {
                isOk = enqueueCodecConfig(element.bufferIndex);
            }
        }
    } else {
        int bufferIndex;
        for (mCurrCsdIdx = 0; isOk && mCurrCsdIdx < mCsdBuffers.size(); mCurrCsdIdx++) {
            bufferIndex = AMediaCodec_dequeueInputBuffer(mCodec, -1);
            if (bufferIndex >= 0) {
                isOk = enqueueCodecConfig(bufferIndex);
            } else {
                auto msg = StringFormat("unexpected return value from "
                                        "AMediaCodec_dequeueInputBuffer: %d \n",
                                        bufferIndex);
                mErrorLogs.append(msg);
                ALOGE("%s", msg.c_str());
                return false;
            }
        }
    }
    return !hasSeenError() && isOk;
}

bool CodecDecoderTest::decodeToMemory(const char* decoder, AMediaFormat* format, int frameLimit,
                                      OutputManager* ref, int64_t pts, SeekMode mode) {
    mSaveToMem = (mWindow == nullptr);
    mOutputBuff = ref;
    AMediaExtractor_seekTo(mExtractor, pts, mode);
    mCodec = AMediaCodec_createCodecByName(decoder);
    RETURN_IF_NULL(mCodec, StringFormat("unable to create codec %s", decoder))
    if (!configureCodec(format, false, true, false)) return false;
    RETURN_IF_FAIL(AMediaCodec_start(mCodec), "AMediaCodec_start failed")
    if (!doWork(frameLimit)) return false;
    if (!queueEOS()) return false;
    if (!waitForAllOutputs()) return false;
    RETURN_IF_FAIL(AMediaCodec_stop(mCodec), "AMediaCodec_stop failed")
    RETURN_IF_FAIL(AMediaCodec_delete(mCodec), "AMediaCodec_delete failed")
    mCodec = nullptr;
    mSaveToMem = false;
    return !hasSeenError();
}

bool CodecDecoderTest::testSimpleDecode(const char* decoder, const char* testFile,
                                        const char* refFile, int colorFormat, float rmsError,
                                        uLong checksum) {
    if (!setUpExtractor(testFile, colorFormat)) return false;
    mSaveToMem = (mWindow == nullptr);
    auto ref = mRefBuff;
    auto test = mTestBuff;
    const bool boolStates[]{true, false};
    int loopCounter = 0;
    for (auto eosType : boolStates) {
        for (auto isAsync : boolStates) {
            bool validateFormat = true;
            mOutputBuff = loopCounter == 0 ? ref : test;
            mOutputBuff->reset();
            AMediaExtractor_seekTo(mExtractor, 0, AMEDIAEXTRACTOR_SEEK_CLOSEST_SYNC);
            /* TODO(b/149981033) */
            /* Instead of create and delete codec at every iteration, we would like to create
             * once and use it for all iterations and delete before exiting */
            mCodec = AMediaCodec_createCodecByName(decoder);
            RETURN_IF_NULL(mCodec, StringFormat("unable to create codec %s", decoder))
            char* name = nullptr;
            RETURN_IF_FAIL(AMediaCodec_getName(mCodec, &name), "AMediaCodec_getName failed")
            RETURN_IF_NULL(name, std::string{"AMediaCodec_getName returned null"})
            auto res = strcmp(name, decoder) != 0;
            AMediaCodec_releaseName(mCodec, name);
            RETURN_IF_TRUE(res, StringFormat("Codec name mismatch act/got: %s/%s", decoder, name))
            if (!configureCodec(mInpDecFormat, isAsync, eosType, false)) return false;
            AMediaFormat* decFormat = AMediaCodec_getOutputFormat(mCodec);
            if (isFormatSimilar(mInpDecFormat, decFormat)) {
                ALOGD("Input format is same as default for format for %s", decoder);
                validateFormat = false;
            }
            AMediaFormat_delete(decFormat);
            RETURN_IF_FAIL(AMediaCodec_start(mCodec), "AMediaCodec_start failed")
            if (!doWork(INT32_MAX)) return false;
            if (!queueEOS()) return false;
            if (!waitForAllOutputs()) return false;
            RETURN_IF_FAIL(AMediaCodec_stop(mCodec), "AMediaCodec_stop failed")
            RETURN_IF_FAIL(AMediaCodec_delete(mCodec), "AMediaCodec_delete failed")
            mCodec = nullptr;
            RETURN_IF_TRUE((loopCounter != 0 && !ref->equals(test)),
                           std::string{"Decoder output is not consistent across runs \n"}.append(
                                   test->getErrorMsg()))
            if (validateFormat && !isOutputFormatOk(mInpDecFormat)) {
                return false;
            }
            RETURN_IF_TRUE(checksum != ref->getChecksum(),
                           StringFormat("sdk output and ndk output for same configuration is not "
                                        "identical. \n sdk buffer output checksum is %lu. \n ndk "
                                        "buffer output checksum is %lu. \n",
                                        checksum, ref->getChecksum()))
            loopCounter++;
        }
    }
    if (mSaveToMem && refFile && rmsError >= 0) {
        setUpAudioReference(refFile);
        float currError = ref->getRmsError(mRefData, mRefLength);
        float errMargin = rmsError * kRmsErrorTolerance;
        RETURN_IF_TRUE(currError > errMargin,
                       StringFormat("rms error too high for file %s, ref/exp/got: %f/%f/%f",
                                    testFile, rmsError, errMargin, currError))
    }
    return true;
}

bool CodecDecoderTest::testFlush(const char* decoder, const char* testFile, int colorFormat) {
    if (!setUpExtractor(testFile, colorFormat)) return false;
    mCsdBuffers.clear();
    for (int i = 0;; i++) {
        char csdName[16];
        void* csdBuffer;
        size_t csdSize;
        snprintf(csdName, sizeof(csdName), "csd-%d", i);
        if (AMediaFormat_getBuffer(mInpDecFormat, csdName, &csdBuffer, &csdSize)) {
            mCsdBuffers.emplace_back(std::make_pair(csdBuffer, csdSize));
        } else break;
    }
    const int64_t pts = 500000;
    const SeekMode mode = AMEDIAEXTRACTOR_SEEK_CLOSEST_SYNC;
    auto ref = mRefBuff;
    RETURN_IF_FALSE(decodeToMemory(decoder, mInpDecFormat, INT32_MAX, ref, pts, mode),
                    StringFormat("decodeToMemory failed for file: %s codec: %s", testFile, decoder))
    auto test = mTestBuff;
    mOutputBuff = test;
    const bool boolStates[]{true, false};
    for (auto isAsync : boolStates) {
        if (isAsync) continue;  // TODO(b/147576107)
        /* TODO(b/149981033) */
        /* Instead of create and delete codec at every iteration, we would like to create
         * once and use it for all iterations and delete before exiting */
        mCodec = AMediaCodec_createCodecByName(decoder);
        RETURN_IF_NULL(mCodec, StringFormat("unable to create codec %s", decoder))
        AMediaExtractor_seekTo(mExtractor, 0, mode);
        if (!configureCodec(mInpDecFormat, isAsync, true, false)) return false;
        AMediaFormat* defFormat = AMediaCodec_getOutputFormat(mCodec);
        bool validateFormat = true;
        if (isFormatSimilar(mInpDecFormat, defFormat)) {
            ALOGD("Input format is same as default for format for %s", decoder);
            validateFormat = false;
        }
        AMediaFormat_delete(defFormat);
        RETURN_IF_FAIL(AMediaCodec_start(mCodec), "AMediaCodec_start failed")

        /* test flush in running state before queuing input */
        if (!flushCodec()) return false;
        if (mIsCodecInAsyncMode) {
            RETURN_IF_FAIL(AMediaCodec_start(mCodec), "AMediaCodec_start failed")
        }
        if (!queueCodecConfig()) return false; /* flushed codec too soon, resubmit csd */
        if (!doWork(1)) return false;

        if (!flushCodec()) return false;
        if (mIsCodecInAsyncMode) {
            RETURN_IF_FAIL(AMediaCodec_start(mCodec), "AMediaCodec_start failed")
        }
        if (!queueCodecConfig()) return false; /* flushed codec too soon, resubmit csd */
        AMediaExtractor_seekTo(mExtractor, 0, mode);
        test->reset();
        if (!doWork(23)) return false;
        RETURN_IF_TRUE(!mIsInterlaced && !test->isPtsStrictlyIncreasing(mPrevOutputPts),
                       std::string{"Output timestamps are not strictly increasing \n"}.append(
                               test->getErrorMsg()))

        /* test flush in running state */
        if (!flushCodec()) return false;
        if (mIsCodecInAsyncMode) {
            RETURN_IF_FAIL(AMediaCodec_start(mCodec), "AMediaCodec_start failed")
        }
        mSaveToMem = (mWindow == nullptr);
        test->reset();
        AMediaExtractor_seekTo(mExtractor, pts, mode);
        if (!doWork(INT32_MAX)) return false;
        if (!queueEOS()) return false;
        if (!waitForAllOutputs()) return false;
        RETURN_IF_TRUE(isMediaTypeOutputUnAffectedBySeek(mMediaType) && !ref->equals(test),
                       std::string{"Decoder output is not consistent across runs \n"}.append(
                               test->getErrorMsg()))

        /* test flush in eos state */
        if (!flushCodec()) return false;
        if (mIsCodecInAsyncMode) {
            RETURN_IF_FAIL(AMediaCodec_start(mCodec), "AMediaCodec_start failed")
        }
        test->reset();
        AMediaExtractor_seekTo(mExtractor, pts, mode);
        if (!doWork(INT32_MAX)) return false;
        if (!queueEOS()) return false;
        if (!waitForAllOutputs()) return false;
        RETURN_IF_FAIL(AMediaCodec_stop(mCodec), "AMediaCodec_stop failed")
        RETURN_IF_FAIL(AMediaCodec_delete(mCodec), "AMediaCodec_delete failed")
        mCodec = nullptr;
        RETURN_IF_TRUE(isMediaTypeOutputUnAffectedBySeek(mMediaType) && !ref->equals(test),
                       std::string{"Decoder output is not consistent across runs \n"}.append(
                               test->getErrorMsg()))
        if (validateFormat && !isOutputFormatOk(mInpDecFormat)) {
            return false;
        }
        mSaveToMem = false;
    }
    return true;
}

bool CodecDecoderTest::testOnlyEos(const char* decoder, const char* testFile, int colorFormat) {
    if (!setUpExtractor(testFile, colorFormat)) return false;
    mSaveToMem = (mWindow == nullptr);
    auto ref = mRefBuff;
    auto test = mTestBuff;
    const bool boolStates[]{true, false};
    int loopCounter = 0;
    for (auto isAsync : boolStates) {
        mOutputBuff = loopCounter == 0 ? ref : test;
        mOutputBuff->reset();
        /* TODO(b/149981033) */
        /* Instead of create and delete codec at every iteration, we would like to create
         * once and use it for all iterations and delete before exiting */
        mCodec = AMediaCodec_createCodecByName(decoder);
        RETURN_IF_NULL(mCodec, StringFormat("unable to create codec %s", decoder))
        if (!configureCodec(mInpDecFormat, isAsync, false, false)) return false;
        RETURN_IF_FAIL(AMediaCodec_start(mCodec), "AMediaCodec_start failed")
        if (!queueEOS()) return false;
        if (!waitForAllOutputs()) return false;
        RETURN_IF_FAIL(AMediaCodec_stop(mCodec), "AMediaCodec_stop failed")
        RETURN_IF_FAIL(AMediaCodec_delete(mCodec), "AMediaCodec_delete failed")
        mCodec = nullptr;
        RETURN_IF_TRUE((loopCounter != 0 && !ref->equals(test)),
                       std::string{"Decoder output is not consistent across runs \n"}.append(
                               test->getErrorMsg()))
        loopCounter++;
    }
    return true;
}

bool CodecDecoderTest::testSimpleDecodeQueueCSD(const char* decoder, const char* testFile,
                                                int colorFormat) {
    if (!setUpExtractor(testFile, colorFormat)) return false;
    std::vector<AMediaFormat*> formats;
    formats.push_back(mInpDecFormat);
    mInpDecDupFormat = AMediaFormat_new();
    AMediaFormat_copy(mInpDecDupFormat, mInpDecFormat);
    formats.push_back(mInpDecDupFormat);
    mCsdBuffers.clear();
    for (int i = 0;; i++) {
        char csdName[16];
        void* csdBuffer;
        size_t csdSize;
        snprintf(csdName, sizeof(csdName), "csd-%d", i);
        if (AMediaFormat_getBuffer(mInpDecDupFormat, csdName, &csdBuffer, &csdSize)) {
            mCsdBuffers.emplace_back(std::make_pair(csdBuffer, csdSize));
            AMediaFormat_setBuffer(mInpDecFormat, csdName, nullptr, 0);
        } else break;
    }

    const bool boolStates[]{true, false};
    mSaveToMem = true;
    auto ref = mRefBuff;
    auto test = mTestBuff;
    int loopCounter = 0;
    for (int i = 0; i < formats.size(); i++) {
        auto fmt = formats[i];
        for (auto eosType : boolStates) {
            for (auto isAsync : boolStates) {
                bool validateFormat = true;
                mOutputBuff = loopCounter == 0 ? ref : test;
                mOutputBuff->reset();
                /* TODO(b/149981033) */
                /* Instead of create and delete codec at every iteration, we would like to create
                 * once and use it for all iterations and delete before exiting */
                mCodec = AMediaCodec_createCodecByName(decoder);
                RETURN_IF_NULL(mCodec, StringFormat("unable to create codec %s", decoder))
                AMediaExtractor_seekTo(mExtractor, 0, AMEDIAEXTRACTOR_SEEK_CLOSEST_SYNC);
                if (!configureCodec(fmt, isAsync, eosType, false)) return false;
                AMediaFormat* defFormat = AMediaCodec_getOutputFormat(mCodec);
                if (isFormatSimilar(defFormat, mInpDecFormat)) {
                    ALOGD("Input format is same as default for format for %s", decoder);
                    validateFormat = false;
                }
                AMediaFormat_delete(defFormat);
                RETURN_IF_FAIL(AMediaCodec_start(mCodec), "AMediaCodec_start failed")
                /* formats[0] doesn't contain csd-data, so queuing csd separately, formats[1]
                 * contain csd-data */
                if (i == 0 && !queueCodecConfig()) return false;
                if (!doWork(INT32_MAX)) return false;
                if (!queueEOS()) return false;
                if (!waitForAllOutputs()) return false;
                RETURN_IF_FAIL(AMediaCodec_stop(mCodec), "AMediaCodec_stop failed")
                RETURN_IF_FAIL(AMediaCodec_delete(mCodec), "AMediaCodec_delete failed")
                mCodec = nullptr;
                RETURN_IF_TRUE((loopCounter != 0 && !ref->equals(test)),
                               std::string{"Decoder output is not consistent across runs \n"}
                                       .append(test->getErrorMsg()))
                if (validateFormat && !isOutputFormatOk(mInpDecFormat)) {
                    return false;
                }
                loopCounter++;
            }
        }
    }
    mSaveToMem = false;
    return true;
}

jboolean nativeTestSimpleDecode(JNIEnv* env, jobject, jstring jDecoder, jobject surface,
                                jstring jMediaType, jstring jtestFile, jstring jrefFile,
                                jint jColorFormat, jfloat jrmsError, jlong jChecksum,
                                jobject jRetMsg) {
    const char* cDecoder = env->GetStringUTFChars(jDecoder, nullptr);
    const char* cMediaType = env->GetStringUTFChars(jMediaType, nullptr);
    const char* cTestFile = env->GetStringUTFChars(jtestFile, nullptr);
    const char* cRefFile = env->GetStringUTFChars(jrefFile, nullptr);
    float cRmsError = jrmsError;
    uLong cChecksum = jChecksum;
    ANativeWindow* window = surface ? ANativeWindow_fromSurface(env, surface) : nullptr;
    auto* codecDecoderTest = new CodecDecoderTest(cMediaType, window);
    bool isPass = codecDecoderTest->testSimpleDecode(cDecoder, cTestFile, cRefFile, jColorFormat,
                                                     cRmsError, cChecksum);
    std::string msg = isPass ? std::string{} : codecDecoderTest->getErrorMsg();
    delete codecDecoderTest;
    jclass clazz = env->GetObjectClass(jRetMsg);
    jmethodID mId =
            env->GetMethodID(clazz, "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
    env->CallObjectMethod(jRetMsg, mId, env->NewStringUTF(msg.c_str()));
    if (window) {
        ANativeWindow_release(window);
        window = nullptr;
    }
    env->ReleaseStringUTFChars(jDecoder, cDecoder);
    env->ReleaseStringUTFChars(jMediaType, cMediaType);
    env->ReleaseStringUTFChars(jtestFile, cTestFile);
    env->ReleaseStringUTFChars(jrefFile, cRefFile);
    return static_cast<jboolean>(isPass);
}

jboolean nativeTestOnlyEos(JNIEnv* env, jobject, jstring jDecoder, jstring jMediaType,
                           jstring jtestFile, jint jColorFormat, jobject jRetMsg) {
    const char* cDecoder = env->GetStringUTFChars(jDecoder, nullptr);
    const char* cMediaType = env->GetStringUTFChars(jMediaType, nullptr);
    const char* cTestFile = env->GetStringUTFChars(jtestFile, nullptr);
    auto* codecDecoderTest = new CodecDecoderTest(cMediaType, nullptr);
    bool isPass = codecDecoderTest->testOnlyEos(cDecoder, cTestFile, jColorFormat);
    std::string msg = isPass ? std::string{} : codecDecoderTest->getErrorMsg();
    delete codecDecoderTest;
    jclass clazz = env->GetObjectClass(jRetMsg);
    jmethodID mId =
            env->GetMethodID(clazz, "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
    env->CallObjectMethod(jRetMsg, mId, env->NewStringUTF(msg.c_str()));
    env->ReleaseStringUTFChars(jDecoder, cDecoder);
    env->ReleaseStringUTFChars(jMediaType, cMediaType);
    env->ReleaseStringUTFChars(jtestFile, cTestFile);
    return static_cast<jboolean>(isPass);
}

jboolean nativeTestFlush(JNIEnv* env, jobject, jstring jDecoder, jobject surface,
                         jstring jMediaType, jstring jtestFile, jint jColorFormat,
                         jobject jRetMsg) {
    const char* cDecoder = env->GetStringUTFChars(jDecoder, nullptr);
    const char* cMediaType = env->GetStringUTFChars(jMediaType, nullptr);
    const char* cTestFile = env->GetStringUTFChars(jtestFile, nullptr);
    ANativeWindow* window = surface ? ANativeWindow_fromSurface(env, surface) : nullptr;
    auto* codecDecoderTest = new CodecDecoderTest(cMediaType, window);
    bool isPass = codecDecoderTest->testFlush(cDecoder, cTestFile, jColorFormat);
    std::string msg = isPass ? std::string{} : codecDecoderTest->getErrorMsg();
    delete codecDecoderTest;
    jclass clazz = env->GetObjectClass(jRetMsg);
    jmethodID mId =
            env->GetMethodID(clazz, "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
    env->CallObjectMethod(jRetMsg, mId, env->NewStringUTF(msg.c_str()));
    if (window) {
        ANativeWindow_release(window);
        window = nullptr;
    }
    env->ReleaseStringUTFChars(jDecoder, cDecoder);
    env->ReleaseStringUTFChars(jMediaType, cMediaType);
    env->ReleaseStringUTFChars(jtestFile, cTestFile);
    return static_cast<jboolean>(isPass);
}

jboolean nativeTestSimpleDecodeQueueCSD(JNIEnv* env, jobject, jstring jDecoder, jstring jMediaType,
                                        jstring jtestFile, jint jColorFormat, jobject jRetMsg) {
    const char* cDecoder = env->GetStringUTFChars(jDecoder, nullptr);
    const char* cMediaType = env->GetStringUTFChars(jMediaType, nullptr);
    const char* cTestFile = env->GetStringUTFChars(jtestFile, nullptr);
    auto codecDecoderTest = new CodecDecoderTest(cMediaType, nullptr);
    bool isPass = codecDecoderTest->testSimpleDecodeQueueCSD(cDecoder, cTestFile, jColorFormat);
    std::string msg = isPass ? std::string{} : codecDecoderTest->getErrorMsg();
    delete codecDecoderTest;
    jclass clazz = env->GetObjectClass(jRetMsg);
    jmethodID mId =
            env->GetMethodID(clazz, "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
    env->CallObjectMethod(jRetMsg, mId, env->NewStringUTF(msg.c_str()));
    env->ReleaseStringUTFChars(jDecoder, cDecoder);
    env->ReleaseStringUTFChars(jMediaType, cMediaType);
    env->ReleaseStringUTFChars(jtestFile, cTestFile);
    return static_cast<jboolean>(isPass);
}
