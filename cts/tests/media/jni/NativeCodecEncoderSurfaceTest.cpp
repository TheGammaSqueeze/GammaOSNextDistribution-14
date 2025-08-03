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
#define LOG_TAG "NativeCodecEncoderSurfaceTest"
#include <log/log.h>

#include <android/native_window_jni.h>
#include <jni.h>
#include <media/NdkMediaExtractor.h>
#include <media/NdkMediaMuxer.h>
#include <sys/stat.h>

#include "NativeCodecTestBase.h"
#include "NativeMediaCommon.h"

class CodecEncoderSurfaceTest {
  private:
    const char* mMediaType;
    ANativeWindow* mWindow;
    AMediaExtractor* mExtractor;
    AMediaFormat* mDecFormat;
    AMediaFormat* mEncFormat;
    AMediaMuxer* mMuxer;
    AMediaCodec* mDecoder;
    AMediaCodec* mEncoder;
    CodecAsyncHandler mAsyncHandleDecoder;
    CodecAsyncHandler mAsyncHandleEncoder;
    bool mIsCodecInAsyncMode;
    bool mSawDecInputEOS;
    bool mSawDecOutputEOS;
    bool mSawEncOutputEOS;
    bool mSignalEOSWithLastFrame;
    int mDecInputCount;
    int mDecOutputCount;
    int mEncOutputCount;
    int mMaxBFrames;
    int mLatency;
    bool mReviseLatency;
    int mMuxTrackID;

    OutputManager* mOutputBuff;
    OutputManager* mRefBuff;
    OutputManager* mTestBuff;
    bool mSaveToMem;

    std::string mErrorLogs;
    std::string mTestEnv;

    bool setUpExtractor(const char* srcFile, int colorFormat);
    void deleteExtractor();
    bool configureCodec(bool isAsync, bool signalEOSWithLastFrame, bool usePersistentSurface);
    void resetContext(bool isAsync, bool signalEOSWithLastFrame);
    bool enqueueDecoderInput(size_t bufferIndex);
    bool dequeueDecoderOutput(size_t bufferIndex, AMediaCodecBufferInfo* bufferInfo);
    bool dequeueEncoderOutput(size_t bufferIndex, AMediaCodecBufferInfo* info);
    bool tryEncoderOutput(long timeOutUs);
    bool waitForAllEncoderOutputs();
    bool queueEOS();
    bool enqueueDecoderEOS(size_t bufferIndex);
    bool doWork(int frameLimit);
    bool hasSeenError() { return mAsyncHandleDecoder.getError() || mAsyncHandleEncoder.getError(); }

  public:
    std::string getErrorMsg() {
        return mTestEnv +
                "###################       Error Details         #####################\n" +
                mErrorLogs;
    }
    CodecEncoderSurfaceTest(const char* mediaType, const char* cfgParams, const char* separator);
    ~CodecEncoderSurfaceTest();

    bool testSimpleEncode(const char* encoder, const char* decoder, const char* srcPath,
                          const char* muxOutPath, int colorFormat, bool usePersistentSurface);
};

CodecEncoderSurfaceTest::CodecEncoderSurfaceTest(const char* mediaType, const char* cfgParams,
                                                 const char* separator)
    : mMediaType{mediaType} {
    mWindow = nullptr;
    mExtractor = nullptr;
    mDecFormat = nullptr;
    mEncFormat = deSerializeMediaFormat(cfgParams, separator);
    mMuxer = nullptr;
    mDecoder = nullptr;
    mEncoder = nullptr;
    resetContext(false, false);
    mMaxBFrames = 0;
    if (mEncFormat != nullptr) {
        // key formalized in Android U (sdk==34).
        // Use internally-defined when running on earlier releases, such as happens with MTS
        if (__builtin_available(android __ANDROID_API_U__, *)) {
            AMediaFormat_getInt32(mEncFormat, AMEDIAFORMAT_KEY_MAX_B_FRAMES, &mMaxBFrames);
        } else {
            AMediaFormat_getInt32(mEncFormat, COMPATIBLE_AMEDIAFORMAT_KEY_MAX_B_FRAMES,
                                  &mMaxBFrames);
        }
    }
    mLatency = mMaxBFrames;
    mReviseLatency = false;
    mMuxTrackID = -1;
    mRefBuff = new OutputManager();
    mTestBuff = new OutputManager(mRefBuff->getSharedErrorLogs());
}

CodecEncoderSurfaceTest::~CodecEncoderSurfaceTest() {
    deleteExtractor();
    if (mWindow) {
        ANativeWindow_release(mWindow);
        mWindow = nullptr;
    }
    if (mEncFormat) {
        AMediaFormat_delete(mEncFormat);
        mEncFormat = nullptr;
    }
    if (mMuxer) {
        AMediaMuxer_delete(mMuxer);
        mMuxer = nullptr;
    }
    if (mDecoder) {
        AMediaCodec_delete(mDecoder);
        mDecoder = nullptr;
    }
    if (mEncoder) {
        AMediaCodec_delete(mEncoder);
        mEncoder = nullptr;
    }
    delete mRefBuff;
    delete mTestBuff;
}

bool CodecEncoderSurfaceTest::setUpExtractor(const char* srcFile, int colorFormat) {
    FILE* fp = fopen(srcFile, "rbe");
    struct stat buf {};
    if (fp && !fstat(fileno(fp), &buf)) {
        deleteExtractor();
        mExtractor = AMediaExtractor_new();
        media_status_t res =
                AMediaExtractor_setDataSourceFd(mExtractor, fileno(fp), 0, buf.st_size);
        if (res != AMEDIA_OK) {
            deleteExtractor();
        } else {
            for (size_t trackID = 0; trackID < AMediaExtractor_getTrackCount(mExtractor);
                 trackID++) {
                AMediaFormat* currFormat = AMediaExtractor_getTrackFormat(mExtractor, trackID);
                const char* mediaType = nullptr;
                AMediaFormat_getString(currFormat, AMEDIAFORMAT_KEY_MIME, &mediaType);
                if (mediaType && strncmp(mediaType, "video/", strlen("video/")) == 0) {
                    AMediaExtractor_selectTrack(mExtractor, trackID);
                    AMediaFormat_setInt32(currFormat, AMEDIAFORMAT_KEY_COLOR_FORMAT, colorFormat);
                    mDecFormat = currFormat;
                    break;
                }
                AMediaFormat_delete(currFormat);
            }
        }
    }
    if (fp) fclose(fp);
    return mDecFormat != nullptr;
}

void CodecEncoderSurfaceTest::deleteExtractor() {
    if (mExtractor) {
        AMediaExtractor_delete(mExtractor);
        mExtractor = nullptr;
    }
    if (mDecFormat) {
        AMediaFormat_delete(mDecFormat);
        mDecFormat = nullptr;
    }
}

bool CodecEncoderSurfaceTest::configureCodec(bool isAsync, bool signalEOSWithLastFrame,
                                             bool usePersistentSurface) {
    RETURN_IF_NULL(mEncFormat,
                   std::string{"encountered error during deserialization of media format"})
    resetContext(isAsync, signalEOSWithLastFrame);
    mTestEnv = "###################      Test Environment       #####################\n";
    {
        char* name = nullptr;
        media_status_t val = AMediaCodec_getName(mEncoder, &name);
        if (AMEDIA_OK != val) {
            mErrorLogs = StringFormat("%s with error %d \n", "AMediaCodec_getName failed", val);
            return false;
        }
        if (!name) {
            mErrorLogs = std::string{"AMediaCodec_getName returned null"};
            return false;
        }
        mTestEnv.append(StringFormat("Component name %s \n", name));
        AMediaCodec_releaseName(mEncoder, name);
    }
    {
        char* name = nullptr;
        media_status_t val = AMediaCodec_getName(mDecoder, &name);
        if (AMEDIA_OK != val) {
            mErrorLogs = StringFormat("%s with error %d \n", "AMediaCodec_getName failed", val);
            return false;
        }
        if (!name) {
            mErrorLogs = std::string{"AMediaCodec_getName returned null"};
            return false;
        }
        mTestEnv.append(StringFormat("Decoder Component name %s \n", name));
        AMediaCodec_releaseName(mDecoder, name);
    }
    mTestEnv += StringFormat("Format under test :- %s \n", AMediaFormat_toString(mEncFormat));
    mTestEnv += StringFormat("Format of Decoder input :- %s \n", AMediaFormat_toString(mDecFormat));
    mTestEnv += StringFormat("Encoder and Decoder are operating in :- %s mode \n",
                             (isAsync ? "asynchronous" : "synchronous"));
    mTestEnv += StringFormat("Components received input eos :- %s \n",
                             (signalEOSWithLastFrame ? "with full buffer" : "with empty buffer"));
    RETURN_IF_FAIL(mAsyncHandleEncoder.setCallBack(mEncoder, isAsync),
                   "AMediaCodec_setAsyncNotifyCallback failed")
    RETURN_IF_FAIL(AMediaCodec_configure(mEncoder, mEncFormat, nullptr, nullptr,
                                         AMEDIACODEC_CONFIGURE_FLAG_ENCODE),
                   "AMediaCodec_configure failed")
    AMediaFormat* inpFormat = AMediaCodec_getInputFormat(mEncoder);
    mReviseLatency = AMediaFormat_getInt32(inpFormat, AMEDIAFORMAT_KEY_LATENCY, &mLatency);
    AMediaFormat_delete(inpFormat);

    if (usePersistentSurface) {
        RETURN_IF_FAIL(AMediaCodec_createPersistentInputSurface(&mWindow),
                       "AMediaCodec_createPersistentInputSurface failed")
        RETURN_IF_FAIL(AMediaCodec_setInputSurface(mEncoder,
                                                   reinterpret_cast<ANativeWindow*>(mWindow)),
                       "AMediaCodec_setInputSurface failed")
    } else {
        RETURN_IF_FAIL(AMediaCodec_createInputSurface(mEncoder, &mWindow),
                       "AMediaCodec_createInputSurface failed")
    }
    RETURN_IF_FAIL(mAsyncHandleDecoder.setCallBack(mDecoder, isAsync),
                   "AMediaCodec_setAsyncNotifyCallback failed")
    RETURN_IF_FAIL(AMediaCodec_configure(mDecoder, mDecFormat, mWindow, nullptr, 0),
                   "AMediaCodec_configure failed")
    return !hasSeenError();
}

void CodecEncoderSurfaceTest::resetContext(bool isAsync, bool signalEOSWithLastFrame) {
    mAsyncHandleDecoder.resetContext();
    mAsyncHandleEncoder.resetContext();
    mIsCodecInAsyncMode = isAsync;
    mSawDecInputEOS = false;
    mSawDecOutputEOS = false;
    mSawEncOutputEOS = false;
    mSignalEOSWithLastFrame = signalEOSWithLastFrame;
    mDecInputCount = 0;
    mDecOutputCount = 0;
    mEncOutputCount = 0;
}

bool CodecEncoderSurfaceTest::enqueueDecoderEOS(size_t bufferIndex) {
    if (!hasSeenError() && !mSawDecInputEOS) {
        RETURN_IF_FAIL(AMediaCodec_queueInputBuffer(mDecoder, bufferIndex, 0, 0, 0,
                                                    AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM),
                       "Queued Decoder End of Stream Failed")
        mSawDecInputEOS = true;
        ALOGV("Queued Decoder End of Stream");
    }
    return !hasSeenError();
}

bool CodecEncoderSurfaceTest::enqueueDecoderInput(size_t bufferIndex) {
    if (AMediaExtractor_getSampleSize(mExtractor) < 0) {
        return enqueueDecoderEOS(bufferIndex);
    } else {
        uint32_t flags = 0;
        size_t bufSize = 0;
        uint8_t* buf = AMediaCodec_getInputBuffer(mDecoder, bufferIndex, &bufSize);
        RETURN_IF_NULL(buf, std::string{"AMediaCodec_getInputBuffer failed"})
        ssize_t size = AMediaExtractor_getSampleSize(mExtractor);
        int64_t pts = AMediaExtractor_getSampleTime(mExtractor);
        RETURN_IF_TRUE(size > bufSize,
                       StringFormat("extractor sample size exceeds codec input buffer size %zu %zu",
                                    size, bufSize))
        RETURN_IF_TRUE(size != AMediaExtractor_readSampleData(mExtractor, buf, bufSize),
                       std::string{"AMediaExtractor_readSampleData failed"})
        if (!AMediaExtractor_advance(mExtractor) && mSignalEOSWithLastFrame) {
            flags |= AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM;
            mSawDecInputEOS = true;
        }
        RETURN_IF_FAIL(AMediaCodec_queueInputBuffer(mDecoder, bufferIndex, 0, size, pts, flags),
                       "AMediaCodec_queueInputBuffer failed")
        ALOGV("input: id: %zu  size: %zu  pts: %" PRId64 "  flags: %d", bufferIndex, size, pts,
              flags);
        if (size > 0) {
            mOutputBuff->saveInPTS(pts);
            mDecInputCount++;
        }
    }
    return !hasSeenError();
}

bool CodecEncoderSurfaceTest::dequeueDecoderOutput(size_t bufferIndex,
                                                   AMediaCodecBufferInfo* bufferInfo) {
    if ((bufferInfo->flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) != 0) {
        mSawDecOutputEOS = true;
    }
    if (bufferInfo->size > 0 && (bufferInfo->flags & AMEDIACODEC_BUFFER_FLAG_CODEC_CONFIG) == 0) {
        mDecOutputCount++;
    }
    ALOGV("output: id: %zu  size: %d  pts: %" PRId64 "  flags: %d", bufferIndex, bufferInfo->size,
          bufferInfo->presentationTimeUs, bufferInfo->flags);
    RETURN_IF_FAIL(AMediaCodec_releaseOutputBuffer(mDecoder, bufferIndex, mWindow != nullptr),
                   "AMediaCodec_releaseOutputBuffer failed")
    return !hasSeenError();
}

bool CodecEncoderSurfaceTest::dequeueEncoderOutput(size_t bufferIndex,
                                                   AMediaCodecBufferInfo* info) {
    if ((info->flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) != 0) {
        mSawEncOutputEOS = true;
    }
    if (info->size > 0) {
        size_t buffSize;
        uint8_t* buf = AMediaCodec_getOutputBuffer(mEncoder, bufferIndex, &buffSize);
        // NdkMediaCodec calls ABuffer::data, which already adds offset
        info->offset = 0;
        if (mSaveToMem) {
            mOutputBuff->saveToMemory(buf, info);
        }
        if (mMuxer != nullptr) {
            if (mMuxTrackID == -1) {
                mMuxTrackID = AMediaMuxer_addTrack(mMuxer, AMediaCodec_getOutputFormat(mEncoder));
                RETURN_IF_FAIL(AMediaMuxer_start(mMuxer), "AMediaMuxer_start failed")
            }
            RETURN_IF_FAIL(AMediaMuxer_writeSampleData(mMuxer, mMuxTrackID, buf, info),
                           "AMediaMuxer_writeSampleData failed")
        }
        if ((info->flags & AMEDIACODEC_BUFFER_FLAG_CODEC_CONFIG) == 0) {
            mOutputBuff->saveOutPTS(info->presentationTimeUs);
            mEncOutputCount++;
        }
    }
    ALOGV("output: id: %zu  size: %d  pts: %" PRId64 "  flags: %d", bufferIndex, info->size,
          info->presentationTimeUs, info->flags);
    RETURN_IF_FAIL(AMediaCodec_releaseOutputBuffer(mEncoder, bufferIndex, false),
                   "AMediaCodec_releaseOutputBuffer failed")
    return !hasSeenError();
}

bool CodecEncoderSurfaceTest::tryEncoderOutput(long timeOutUs) {
    if (mIsCodecInAsyncMode) {
        if (!hasSeenError() && !mSawEncOutputEOS) {
            while (mReviseLatency) {
                if (!mAsyncHandleEncoder.waitOnFormatChange()) {
                    mErrorLogs.append("taking too long to receive onOutputFormatChanged callback");
                    return false;
                }
                int actualLatency;
                mReviseLatency = false;
                if (AMediaFormat_getInt32(mAsyncHandleEncoder.getOutputFormat(),
                                          AMEDIAFORMAT_KEY_LATENCY, &actualLatency)) {
                    if (mLatency < actualLatency) {
                        mLatency = actualLatency;
                        return !hasSeenError();
                    }
                }
            }
            callbackObject element = mAsyncHandleEncoder.getOutput();
            if (element.bufferIndex >= 0) {
                if (!dequeueEncoderOutput(element.bufferIndex, &element.bufferInfo)) return false;
            }
        }
    } else {
        AMediaCodecBufferInfo outInfo;
        if (!mSawEncOutputEOS) {
            int bufferID = AMediaCodec_dequeueOutputBuffer(mEncoder, &outInfo, timeOutUs);
            if (bufferID >= 0) {
                if (!dequeueEncoderOutput(bufferID, &outInfo)) return false;
            } else if (bufferID == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
                AMediaFormat* outFormat = AMediaCodec_getOutputFormat(mEncoder);
                AMediaFormat_getInt32(outFormat, AMEDIAFORMAT_KEY_LATENCY, &mLatency);
                AMediaFormat_delete(outFormat);
            } else if (bufferID == AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
            } else if (bufferID == AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED) {
            } else {
                mErrorLogs.append(
                        StringFormat("unexpected return value from *_dequeueOutputBuffer: %d",
                                     bufferID));
                return false;
            }
        }
    }
    return !hasSeenError();
}

bool CodecEncoderSurfaceTest::waitForAllEncoderOutputs() {
    if (mIsCodecInAsyncMode) {
        while (!hasSeenError() && !mSawEncOutputEOS) {
            if (!tryEncoderOutput(kQDeQTimeOutUs)) return false;
        }
    } else {
        while (!mSawEncOutputEOS) {
            if (!tryEncoderOutput(kQDeQTimeOutUs)) return false;
        }
    }
    return !hasSeenError();
}

bool CodecEncoderSurfaceTest::queueEOS() {
    if (mIsCodecInAsyncMode) {
        while (!hasSeenError() && !mSawDecInputEOS) {
            callbackObject element = mAsyncHandleDecoder.getWork();
            if (element.bufferIndex >= 0) {
                if (element.isInput) {
                    if (!enqueueDecoderEOS(element.bufferIndex)) return false;
                } else {
                    if (!dequeueDecoderOutput(element.bufferIndex, &element.bufferInfo)) {
                        return false;
                    }
                }
            }
        }
    } else {
        AMediaCodecBufferInfo outInfo;
        while (!mSawDecInputEOS) {
            ssize_t oBufferID = AMediaCodec_dequeueOutputBuffer(mDecoder, &outInfo, kQDeQTimeOutUs);
            if (oBufferID >= 0) {
                if (!dequeueDecoderOutput(oBufferID, &outInfo)) return false;
            } else if (oBufferID == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
            } else if (oBufferID == AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
            } else if (oBufferID == AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED) {
            } else {
                mErrorLogs.append(
                        StringFormat("unexpected return value from *_dequeueOutputBuffer: %d",
                                     oBufferID));
                return false;
            }
            ssize_t iBufferId = AMediaCodec_dequeueInputBuffer(mDecoder, kQDeQTimeOutUs);
            if (iBufferId >= 0) {
                if (!enqueueDecoderEOS(iBufferId)) return false;
            } else if (iBufferId == AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
            } else {
                mErrorLogs.append(
                        StringFormat("unexpected return value from *_dequeueInputBuffer: %zd",
                                     iBufferId));
                return false;
            }
        }
    }

    if (mIsCodecInAsyncMode) {
        // output processing after queuing EOS is done in waitForAllOutputs()
        while (!hasSeenError() && !mSawDecOutputEOS) {
            callbackObject element = mAsyncHandleDecoder.getOutput();
            if (element.bufferIndex >= 0) {
                if (!dequeueDecoderOutput(element.bufferIndex, &element.bufferInfo)) return false;
            }
            if (mSawDecOutputEOS) AMediaCodec_signalEndOfInputStream(mEncoder);
            if (mDecOutputCount - mEncOutputCount > mLatency) {
                if (!tryEncoderOutput(-1)) return false;
            }
        }
    } else {
        AMediaCodecBufferInfo outInfo;
        // output processing after queuing EOS is done in waitForAllOutputs()
        while (!mSawDecOutputEOS) {
            if (!mSawDecOutputEOS) {
                ssize_t oBufferID =
                        AMediaCodec_dequeueOutputBuffer(mDecoder, &outInfo, kQDeQTimeOutUs);
                if (oBufferID >= 0) {
                    if (!dequeueDecoderOutput(oBufferID, &outInfo)) return false;
                } else if (oBufferID == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
                } else if (oBufferID == AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
                } else if (oBufferID == AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED) {
                } else {
                    mErrorLogs.append(
                            StringFormat("unexpected return value from *_dequeueOutputBuffer: %d",
                                         oBufferID));
                    return false;
                }
            }
            if (mSawDecOutputEOS) AMediaCodec_signalEndOfInputStream(mEncoder);
            if (mDecOutputCount - mEncOutputCount > mLatency) {
                if (!tryEncoderOutput(-1)) return false;
            }
        }
    }
    return !hasSeenError();
}

bool CodecEncoderSurfaceTest::doWork(int frameLimit) {
    int frameCnt = 0;
    if (mIsCodecInAsyncMode) {
        // output processing after queuing EOS is done in waitForAllOutputs()
        while (!hasSeenError() && !mSawDecInputEOS && frameCnt < frameLimit) {
            callbackObject element = mAsyncHandleDecoder.getWork();
            if (element.bufferIndex >= 0) {
                if (element.isInput) {
                    if (!enqueueDecoderInput(element.bufferIndex)) return false;
                    frameCnt++;
                } else {
                    if (!dequeueDecoderOutput(element.bufferIndex, &element.bufferInfo)) {
                        return false;
                    }
                }
            }
            // check decoder EOS
            if (mSawDecOutputEOS) AMediaCodec_signalEndOfInputStream(mEncoder);
            // encoder output
            if (mDecOutputCount - mEncOutputCount > mLatency) {
                if (!tryEncoderOutput(-1)) return false;
            }
        }
    } else {
        AMediaCodecBufferInfo outInfo;
        // output processing after queuing EOS is done in waitForAllOutputs()
        while (!mSawDecInputEOS && frameCnt < frameLimit) {
            ssize_t oBufferID = AMediaCodec_dequeueOutputBuffer(mDecoder, &outInfo, kQDeQTimeOutUs);
            if (oBufferID >= 0) {
                if (!dequeueDecoderOutput(oBufferID, &outInfo)) return false;
            } else if (oBufferID == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
            } else if (oBufferID == AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
            } else if (oBufferID == AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED) {
            } else {
                mErrorLogs.append(
                        StringFormat("unexpected return value from *_dequeueOutputBuffer: %zd",
                                     oBufferID));
                return false;
            }
            ssize_t iBufferId = AMediaCodec_dequeueInputBuffer(mDecoder, kQDeQTimeOutUs);
            if (iBufferId >= 0) {
                if (!enqueueDecoderInput(iBufferId)) return false;
                frameCnt++;
            } else if (iBufferId == AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
            } else {
                mErrorLogs.append(
                        StringFormat("unexpected return value from *_dequeueInputBuffer: %zd",
                                     iBufferId));
                return false;
            }
            if (mSawDecOutputEOS) AMediaCodec_signalEndOfInputStream(mEncoder);
            if (mDecOutputCount - mEncOutputCount > mLatency) {
                if (!tryEncoderOutput(-1)) return false;
            }
        }
    }
    return !hasSeenError();
}

bool CodecEncoderSurfaceTest::testSimpleEncode(const char* encoder, const char* decoder,
                                               const char* srcPath, const char* muxOutPath,
                                               int colorFormat, bool usePersistentSurface) {
    RETURN_IF_FALSE(setUpExtractor(srcPath, colorFormat), std::string{"setUpExtractor failed"})
    bool muxOutput = muxOutPath != nullptr;

    /* TODO(b/149027258) */
    if (true) mSaveToMem = false;
    else mSaveToMem = true;
    auto ref = mRefBuff;
    auto test = mTestBuff;
    int loopCounter = 0;
    const bool boolStates[]{true, false};
    for (bool isAsync : boolStates) {
        AMediaExtractor_seekTo(mExtractor, 0, AMEDIAEXTRACTOR_SEEK_CLOSEST_SYNC);
        mOutputBuff = loopCounter == 0 ? ref : test;
        mOutputBuff->reset();

        /* TODO(b/147348711) */
        /* Instead of create and delete codec at every iteration, we would like to create
         * once and use it for all iterations and delete before exiting */
        mEncoder = AMediaCodec_createCodecByName(encoder);
        mDecoder = AMediaCodec_createCodecByName(decoder);
        RETURN_IF_NULL(mDecoder, StringFormat("unable to create media codec by name %s", decoder))
        RETURN_IF_NULL(mEncoder, StringFormat("unable to create media codec by name %s", encoder))
        FILE* ofp = nullptr;
        if (muxOutput && loopCounter == 0) {
            OutputFormat muxerFormat = AMEDIAMUXER_OUTPUT_FORMAT_MPEG_4;
            if (!strcmp(mMediaType, AMEDIA_MIMETYPE_VIDEO_VP8) ||
                !strcmp(mMediaType, AMEDIA_MIMETYPE_VIDEO_VP9)) {
                muxerFormat = AMEDIAMUXER_OUTPUT_FORMAT_WEBM;
            }
            ofp = fopen(muxOutPath, "wbe+");
            if (ofp) {
                mMuxer = AMediaMuxer_new(fileno(ofp), muxerFormat);
            }
        }
        if (!configureCodec(isAsync, false, usePersistentSurface)) return false;
        RETURN_IF_FAIL(AMediaCodec_start(mEncoder), "Encoder AMediaCodec_start failed")
        RETURN_IF_FAIL(AMediaCodec_start(mDecoder), "Decoder AMediaCodec_start failed")
        if (!doWork(INT32_MAX)) return false;
        if (!queueEOS()) return false;
        if (!waitForAllEncoderOutputs()) return false;
        if (muxOutput) {
            if (mMuxer != nullptr) {
                RETURN_IF_FAIL(AMediaMuxer_stop(mMuxer), "AMediaMuxer_stop failed")
                mMuxTrackID = -1;
                RETURN_IF_FAIL(AMediaMuxer_delete(mMuxer), "AMediaMuxer_delete failed")
                mMuxer = nullptr;
            }
            if (ofp) fclose(ofp);
        }
        RETURN_IF_FAIL(AMediaCodec_stop(mDecoder), "AMediaCodec_stop failed for Decoder")
        RETURN_IF_FAIL(AMediaCodec_stop(mEncoder), "AMediaCodec_stop failed for Encoder")
        RETURN_IF_TRUE(mAsyncHandleDecoder.getError(),
                       std::string{"Decoder has encountered error in async mode. \n"}.append(
                               mAsyncHandleDecoder.getErrorMsg()))
        RETURN_IF_TRUE(mAsyncHandleEncoder.getError(),
                       std::string{"Encoder has encountered error in async mode. \n"}.append(
                               mAsyncHandleEncoder.getErrorMsg()))
        RETURN_IF_TRUE((0 == mDecInputCount), std::string{"Decoder has not received any input \n"})
        RETURN_IF_TRUE((0 == mDecOutputCount), std::string{"Decoder has not sent any output \n"})
        RETURN_IF_TRUE((0 == mEncOutputCount), std::string{"Encoder has not sent any output \n"})
        RETURN_IF_TRUE((mDecInputCount != mDecOutputCount),
                       StringFormat("Decoder output count is not equal to decoder input count\n "
                                    "Input count : %s, Output count : %s\n",
                                    mDecInputCount, mDecOutputCount))
        RETURN_IF_TRUE((mMaxBFrames == 0 && !mOutputBuff->isPtsStrictlyIncreasing(INT32_MIN)),
                       std::string{"Output timestamps are not strictly increasing \n"}.append(
                               ref->getErrorMsg()))
        /* TODO(b/153127506)
         *  Currently disabling all encoder output checks. Added checks only for encoder timeStamp
         *  is in increasing order or not.
         *  Once issue is fixed remove increasing timestamp check and enable encoder checks.
         */
        /*RETURN_IF_TRUE((mEncOutputCount != mDecOutputCount),
                       StringFormat("Encoder output count is not equal to decoder input count\n "
                                    "Input count : %s, Output count : %s\n",
                                    mDecInputCount, mEncOutputCount))
        RETURN_IF_TRUE((loopCounter != 0 && !ref->equals(test)),
                       std::string{"Encoder output is not consistent across runs \n"}.append(
                               test->getErrorMsg()))
        RETURN_IF_TRUE((loopCounter == 0 &&
                        !mOutputBuff->isOutPtsListIdenticalToInpPtsList(mMaxBFrames != 0)),
                       std::string{"Input pts list and Output pts list are not identical \n"}
                               .append(ref->getErrorMsg()))*/
        loopCounter++;
        ANativeWindow_release(mWindow);
        mWindow = nullptr;
        RETURN_IF_FAIL(AMediaCodec_delete(mEncoder), "AMediaCodec_delete failed for encoder")
        mEncoder = nullptr;
        RETURN_IF_FAIL(AMediaCodec_delete(mDecoder), "AMediaCodec_delete failed for decoder")
        mDecoder = nullptr;
    }
    return true;
}

static jboolean nativeTestSimpleEncode(JNIEnv* env, jobject, jstring jEncoder, jstring jDecoder,
                                       jstring jMediaType, jstring jtestFile, jstring jmuxFile,
                                       jint jColorFormat, jboolean jUsePersistentSurface,
                                       jstring jCfgParams, jstring jSeparator, jobject jRetMsg) {
    const char* cEncoder = env->GetStringUTFChars(jEncoder, nullptr);
    const char* cDecoder = env->GetStringUTFChars(jDecoder, nullptr);
    const char* cMediaType = env->GetStringUTFChars(jMediaType, nullptr);
    const char* cTestFile = env->GetStringUTFChars(jtestFile, nullptr);
    const char* cMuxFile = jmuxFile ? env->GetStringUTFChars(jmuxFile, nullptr) : nullptr;
    const char* cCfgParams = env->GetStringUTFChars(jCfgParams, nullptr);
    const char* cSeparator = env->GetStringUTFChars(jSeparator, nullptr);
    auto codecEncoderSurfaceTest = new CodecEncoderSurfaceTest(cMediaType, cCfgParams, cSeparator);
    bool isPass = codecEncoderSurfaceTest->testSimpleEncode(cEncoder, cDecoder, cTestFile, cMuxFile,
                                                            jColorFormat, jUsePersistentSurface);
    std::string msg = isPass ? std::string{} : codecEncoderSurfaceTest->getErrorMsg();
    delete codecEncoderSurfaceTest;
    jclass clazz = env->GetObjectClass(jRetMsg);
    jmethodID mId =
            env->GetMethodID(clazz, "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
    env->CallObjectMethod(jRetMsg, mId, env->NewStringUTF(msg.c_str()));
    env->ReleaseStringUTFChars(jEncoder, cEncoder);
    env->ReleaseStringUTFChars(jDecoder, cDecoder);
    env->ReleaseStringUTFChars(jMediaType, cMediaType);
    env->ReleaseStringUTFChars(jtestFile, cTestFile);
    if (cMuxFile) env->ReleaseStringUTFChars(jmuxFile, cMuxFile);
    env->ReleaseStringUTFChars(jCfgParams, cCfgParams);
    env->ReleaseStringUTFChars(jSeparator, cSeparator);

    return isPass;
}

int registerAndroidMediaV2CtsEncoderSurfaceTest(JNIEnv* env) {
    const JNINativeMethod methodTable[] = {
            {"nativeTestSimpleEncode",
             "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;"
             "Ljava/lang/String;IZLjava/lang/String;Ljava/lang/String;Ljava/lang/StringBuilder;)Z",
             (void*)nativeTestSimpleEncode},
    };
    jclass c = env->FindClass("android/mediav2/cts/CodecEncoderSurfaceTest");
    return env->RegisterNatives(c, methodTable, sizeof(methodTable) / sizeof(JNINativeMethod));
}

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    if (registerAndroidMediaV2CtsEncoderSurfaceTest(env) != JNI_OK) return JNI_ERR;
    return JNI_VERSION_1_6;
}
