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
#define LOG_TAG "VorbisDecoderTest"
#include <utils/Log.h>

#include <fstream>

#include "VorbisDecoderTestEnvironment.h"

#define OUTPUT_FILE_NAME "/data/local/tmp/VorbisDecoderOutput.raw"

constexpr uint32_t kMaxChannels = 255;
constexpr uint32_t kMaxNumSamplesPerChannel = 8192;

struct vorbis_dsp_state;
struct vorbis_info;
struct FrameInfo {
    int32_t bytesCount;
    uint32_t flags;
    int64_t timestamp;
};

extern "C" {
    #include <Tremolo/codec_internal.h>

    int _vorbis_unpack_books(vorbis_info* vi, oggpack_buffer* opb);
    int _vorbis_unpack_info(vorbis_info* vi, oggpack_buffer* opb);
    int _vorbis_unpack_comment(vorbis_comment* vc, oggpack_buffer* opb);
}

static VorbisDecoderTestEnvironment* gEnv = nullptr;

class VorbisDecoderTest : public ::testing::TestWithParam<pair<string, string>> {
  public:
    VorbisDecoderTest()
        : mNumFramesLeftOnPage(-1),
          mInfoUnpacked(false),
          mBooksUnpacked(false),
          mInputBuffer(nullptr),
          mOutputBuffer(nullptr),
          mState(nullptr),
          mVi(nullptr) {}

    ~VorbisDecoderTest() {
        if (mInputBuffer) free(mInputBuffer);
        if (mOutputBuffer) free(mOutputBuffer);
        if (mEleStream.is_open()) mEleStream.close();
        if (mState) {
            vorbis_dsp_clear(mState);
            delete mState;
            mState = nullptr;
        }

        if (mVi) {
            vorbis_info_clear(mVi);
            delete mVi;
            mVi = nullptr;
        }
        mNumFramesLeftOnPage = -1;
        if (gEnv->cleanUp()) remove(OUTPUT_FILE_NAME);
    }

    int32_t initVorbisDecoder();
    void processVorbisDecoder(vector<FrameInfo> Info, int32_t offset, int32_t range,
                              ofstream& ostrm);

    ifstream mEleStream;
    int32_t mNumFramesLeftOnPage;
    bool mInfoUnpacked;
    bool mBooksUnpacked;
    char* mInputBuffer;
    int16_t* mOutputBuffer;
    vorbis_dsp_state* mState;
    vorbis_info* mVi;
};

void getInfo(string infoFileName, vector<FrameInfo>& Info) {
    ifstream eleInfo;
    eleInfo.open(infoFileName);
    ASSERT_EQ(eleInfo.is_open(), true) << "Failed to open " << infoFileName;
    while (1) {
        int32_t bytesCount = 0;
        uint32_t flags = 0;
        uint32_t timestamp = 0;

        if (!(eleInfo >> bytesCount)) break;
        eleInfo >> flags;
        eleInfo >> timestamp;
        Info.push_back({bytesCount, flags, timestamp});
    }
    if (eleInfo.is_open()) eleInfo.close();
}

int32_t VorbisDecoderTest::initVorbisDecoder() {
    if (!mVi) {
        mVi = new vorbis_info{};
        if (!mVi) return -1;
    }
    vorbis_info_clear(mVi);

    if (!mState) {
        mState = new vorbis_dsp_state{};
        if (!mState) return -1;
    }
    vorbis_dsp_clear(mState);

    return 0;
}

static void makeBitReader(const void* inputBuffer, size_t size, ogg_buffer* buf, ogg_reference* ref,
                          oggpack_buffer* bits) {
    buf->data = (uint8_t*)inputBuffer;
    buf->size = size;
    buf->refcount = 1;
    buf->ptr.owner = nullptr;

    ref->buffer = buf;
    ref->begin = 0;
    ref->length = size;
    ref->next = nullptr;

    oggpack_readinit(bits, ref);
}

void VorbisDecoderTest::processVorbisDecoder(vector<FrameInfo> Info, int32_t offset, int32_t range,
                                             ofstream& ostrm) {
    int32_t frameID = offset;
    ASSERT_GE(range, 0) << "Invalid Range";
    ASSERT_GE(offset, 0) << "Invalid Offset";
    ASSERT_LT(offset, Info.size()) << "Offset must be less than ";

    while (1) {
        if (frameID == Info.size() || frameID == (offset + range)) break;
        int32_t size = (Info)[frameID].bytesCount;
        ASSERT_GE(size, 0) << "Size for the memory allocation is negative" << size;

        if (!mInputBuffer) {
            mInputBuffer = (char*)malloc(size);
            ASSERT_NE(mInputBuffer, nullptr) << "Insufficient memory to read frame";
        }

        mEleStream.read(mInputBuffer, size);
        ASSERT_EQ(mEleStream.gcount(), size)
                << "Invalid size read. Requested: " << size << " and read: " << mEleStream.gcount();

        int32_t numChannels = mVi->channels;
        /* Decode vorbis headers only once */
        if (size > 7 && !memcmp(&mInputBuffer[1], "vorbis", 6) &&
            (!mInfoUnpacked || !mBooksUnpacked)) {
            ASSERT_TRUE((mInputBuffer[0] == 1) || (mInputBuffer[0] == 5))
                    << "unexpected type received " << mInputBuffer[0];
            ogg_buffer buf;
            ogg_reference ref;
            oggpack_buffer bits;

            // skip 7 <type + "vorbis"> bytes
            makeBitReader((const uint8_t*)mInputBuffer + 7, size - 7, &buf, &ref, &bits);
            if (mInputBuffer[0] == 1) {
                vorbis_info_init(mVi);
                int32_t status = _vorbis_unpack_info(mVi, &bits);
                ASSERT_EQ(status, 0) << "Encountered error while unpacking info";
                if (mVi->channels != numChannels) {
                    ALOGV("num channels changed: %d, sample rate: %ld", mVi->channels, mVi->rate);
                    numChannels = mVi->channels;
                }
                ASSERT_FALSE(numChannels < 1 || numChannels > kMaxChannels)
                        << "Invalid number of channels: " << numChannels;
                mInfoUnpacked = true;
            } else {
                ASSERT_TRUE(mInfoUnpacked) << "Data with type:5 sent before sending type:1";
                int32_t status = _vorbis_unpack_books(mVi, &bits);
                ASSERT_EQ(status, 0) << "Encountered error while unpacking books";
                status = vorbis_dsp_init(mState, mVi);
                ASSERT_EQ(status, 0) << "Encountered error while dsp init";
                mBooksUnpacked = true;
            }
            ALOGV("frameID= %d", frameID);
            frameID++;
            free(mInputBuffer);
            mInputBuffer = nullptr;
            continue;
        }

        ASSERT_TRUE(mInfoUnpacked && mBooksUnpacked)
                << "Missing CODEC_CONFIG data mInfoUnpacked: " << mInfoUnpacked
                << " mBooksUnpack: " << mBooksUnpacked;

        int32_t numPageFrames = 0;
        ASSERT_GE(size, sizeof(numPageFrames))
                << "input header has size: " << size << " expected: " << sizeof(numPageFrames);
        memcpy(&numPageFrames, mInputBuffer + size - sizeof(numPageFrames), sizeof(numPageFrames));
        size -= sizeof(numPageFrames);
        if (numPageFrames >= 0) {
            mNumFramesLeftOnPage = numPageFrames;
        }

        ogg_buffer buf;
        buf.data = reinterpret_cast<unsigned char*>(mInputBuffer);
        buf.size = size;
        buf.refcount = 1;
        buf.ptr.owner = nullptr;

        ogg_reference ref;
        ref.buffer = &buf;
        ref.begin = 0;
        ref.length = buf.size;
        ref.next = nullptr;

        ogg_packet pack;
        pack.packet = &ref;
        pack.bytes = ref.length;
        pack.b_o_s = 0;
        pack.e_o_s = 0;
        pack.granulepos = 0;
        pack.packetno = 0;

        size_t outCapacity = kMaxNumSamplesPerChannel * numChannels * sizeof(int16_t);
        if (!mOutputBuffer) {
            mOutputBuffer = (int16_t*)malloc(outCapacity);
            ASSERT_NE(mOutputBuffer, nullptr) << "Insufficient memory";
        }

        int32_t numFrames = 0;
        int32_t ret = vorbis_dsp_synthesis(mState, &pack, 1);
        if (0 != ret) {
            ALOGV("vorbis_dsp_synthesis returned %d; ignored", ret);
        } else {
            numFrames = vorbis_dsp_pcmout(mState, mOutputBuffer, kMaxNumSamplesPerChannel);
            if (numFrames < 0) {
                ALOGV("vorbis_dsp_pcmout returned %d", numFrames);
                numFrames = 0;
            }
        }

        if (mNumFramesLeftOnPage >= 0) {
            if (numFrames > mNumFramesLeftOnPage) {
                ALOGV("discarding %d frames at end of page", numFrames - mNumFramesLeftOnPage);
                numFrames = mNumFramesLeftOnPage;
            }
            mNumFramesLeftOnPage -= numFrames;
        }
        if (numFrames) {
            int32_t outSize = numFrames * sizeof(int16_t) * numChannels;
            ostrm.write(reinterpret_cast<char*>(mOutputBuffer), outSize);
        }
        frameID++;
        free(mInputBuffer);
        mInputBuffer = nullptr;
    }
    ALOGV("Last frame decoded = %d", frameID);
}

TEST_P(VorbisDecoderTest, FlushTest) {
    string inputFileName = gEnv->getRes() + GetParam().first;
    string infoFileName = gEnv->getRes() + GetParam().second;

    vector<FrameInfo> Info;
    ASSERT_NO_FATAL_FAILURE(getInfo(infoFileName, Info));

    mEleStream.open(inputFileName, ifstream::binary);
    ASSERT_EQ(mEleStream.is_open(), true) << "Failed to open " << inputFileName;

    ofstream ostrm;
    ostrm.open(OUTPUT_FILE_NAME, std::ofstream::binary);
    ASSERT_EQ(ostrm.is_open(), true) << "Failed to open " << OUTPUT_FILE_NAME;

    int32_t err = initVorbisDecoder();
    ASSERT_EQ(err, 0) << "initVorbisDecoder: failed to create decoder " << err;

    ASSERT_NO_FATAL_FAILURE(processVorbisDecoder(Info, 0, Info.size() / 3, ostrm));

    // flushing the decoder
    mNumFramesLeftOnPage = -1;
    int32_t status = vorbis_dsp_restart(mState);
    ASSERT_EQ(status, 0) << "Encountered error while restarting";

    ASSERT_NO_FATAL_FAILURE(processVorbisDecoder(Info, (Info.size() / 3), Info.size(), ostrm));

    ostrm.close();
    Info.clear();
}

TEST_P(VorbisDecoderTest, DecodeTest) {
    string inputFileName = gEnv->getRes() + GetParam().first;
    string infoFileName = gEnv->getRes() + GetParam().second;

    vector<FrameInfo> Info;
    ASSERT_NO_FATAL_FAILURE(getInfo(infoFileName, Info));

    mEleStream.open(inputFileName, ifstream::binary);
    ASSERT_EQ(mEleStream.is_open(), true) << "Failed to open " << inputFileName;

    ofstream ostrm;
    ostrm.open(OUTPUT_FILE_NAME, std::ofstream::binary);
    ASSERT_EQ(ostrm.is_open(), true) << "Failed to open " << OUTPUT_FILE_NAME;

    int32_t err = initVorbisDecoder();
    ASSERT_EQ(err, 0) << "initVorbisDecoder: failed to create decoder " << err;

    ASSERT_NO_FATAL_FAILURE(processVorbisDecoder(Info, 0, Info.size(), ostrm));
    ostrm.close();
    Info.clear();
}

INSTANTIATE_TEST_SUITE_P(
        VorbisDecoderTestAll, VorbisDecoderTest,
        ::testing::Values(make_pair("bbb_vorbis_mono_64kbps_48000hz.vorbis",
                                    "bbb_vorbis_mono_64kbps_48000hz.info"),
                          make_pair("bbb_vorbis_stereo_128kbps_44100hz_crypt.vorbis",
                                    "bbb_vorbis_stereo_128kbps_44100hz_crypt.info"),
                          make_pair("bbb_vorbis_stereo_128kbps_48000hz.vorbis",
                                    "bbb_vorbis_stereo_128kbps_48000hz.info"),
                          make_pair("bbb_vorbis_5ch_320kbps_48000hz.vorbis",
                                    "bbb_vorbis_5ch_320kbps_48000hz.info"),
                          make_pair("bbb_vorbis_6ch_384kbps_24000hz.vorbis",
                                    "bbb_vorbis_6ch_384kbps_24000hz.info")));

int main(int argc, char** argv) {
    gEnv = new VorbisDecoderTestEnvironment();
    ::testing::AddGlobalTestEnvironment(gEnv);
    ::testing::InitGoogleTest(&argc, argv);
    int status = gEnv->initFromOptions(argc, argv);
    if (status == 0) {
        status = RUN_ALL_TESTS();
        ALOGV("Vorbis Decoder Test Result = %d", status);
    }
    return status;
}
