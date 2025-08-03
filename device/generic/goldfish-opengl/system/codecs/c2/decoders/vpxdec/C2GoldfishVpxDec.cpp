/*
 * Copyright (C) 2018 The Android Open Source Project
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
#define LOG_TAG "C2GoldfishVpxDec"
#include <log/log.h>

#include <algorithm>

#include <media/stagefright/foundation/AUtils.h>
#include <media/stagefright/foundation/MediaDefs.h>

#include <C2AllocatorGralloc.h>
#include <C2PlatformSupport.h>
//#include <android/hardware/graphics/common/1.0/types.h>

#include <android/hardware/graphics/allocator/3.0/IAllocator.h>
#include <android/hardware/graphics/mapper/3.0/IMapper.h>
#include <hidl/LegacySupport.h>

#include <C2Debug.h>
#include <C2PlatformSupport.h>
#include <SimpleC2Interface.h>
#include <goldfish_codec2/store/GoldfishComponentStore.h>

#include <gralloc_cb_bp.h>

#include <color_buffer_utils.h>

#include "C2GoldfishVpxDec.h"

#define DEBUG 0
#if DEBUG
#define DDD(...) ALOGW(__VA_ARGS__)
#else
#define DDD(...) ((void)0)
#endif
using ::android::hardware::graphics::common::V1_0::BufferUsage;
using ::android::hardware::graphics::common::V1_2::PixelFormat;

namespace android {
constexpr size_t kMinInputBufferSize = 6 * 1024 * 1024;
#ifdef VP9
constexpr char COMPONENT_NAME[] = "c2.goldfish.vp9.decoder";
#else
constexpr char COMPONENT_NAME[] = "c2.goldfish.vp8.decoder";
#endif

class C2GoldfishVpxDec::IntfImpl : public SimpleInterface<void>::BaseParams {
  public:
    explicit IntfImpl(const std::shared_ptr<C2ReflectorHelper> &helper)
        : SimpleInterface<void>::BaseParams(helper, COMPONENT_NAME,
                                            C2Component::KIND_DECODER,
                                            C2Component::DOMAIN_VIDEO,
#ifdef VP9
                                            MEDIA_MIMETYPE_VIDEO_VP9
#else
                                            MEDIA_MIMETYPE_VIDEO_VP8
#endif
          ) {
        DDD("calling IntfImpl now helper %p", helper.get());
        noPrivateBuffers(); // TODO: account for our buffers here
        noInputReferences();
        noOutputReferences();
        noInputLatency();
        noTimeStretch();

        // TODO: output latency and reordering

        addParameter(DefineParam(mAttrib, C2_PARAMKEY_COMPONENT_ATTRIBUTES)
                         .withConstValue(new C2ComponentAttributesSetting(
                             C2Component::ATTRIB_IS_TEMPORAL))
                         .build());

        addParameter(
            DefineParam(mSize, C2_PARAMKEY_PICTURE_SIZE)
                .withDefault(new C2StreamPictureSizeInfo::output(0u, 320, 240))
                .withFields({
                    C2F(mSize, width).inRange(2, 4096, 2),
                    C2F(mSize, height).inRange(2, 4096, 2),
                })
                .withSetter(SizeSetter)
                .build());

#ifdef VP9
        // TODO: Add C2Config::PROFILE_VP9_2HDR ??
        addParameter(
            DefineParam(mProfileLevel, C2_PARAMKEY_PROFILE_LEVEL)
                .withDefault(new C2StreamProfileLevelInfo::input(
                    0u, C2Config::PROFILE_VP9_0, C2Config::LEVEL_VP9_5))
                .withFields({C2F(mProfileLevel, profile)
                                 .oneOf({C2Config::PROFILE_VP9_0,
                                         C2Config::PROFILE_VP9_2}),
                             C2F(mProfileLevel, level)
                                 .oneOf({
                                     C2Config::LEVEL_VP9_1,
                                     C2Config::LEVEL_VP9_1_1,
                                     C2Config::LEVEL_VP9_2,
                                     C2Config::LEVEL_VP9_2_1,
                                     C2Config::LEVEL_VP9_3,
                                     C2Config::LEVEL_VP9_3_1,
                                     C2Config::LEVEL_VP9_4,
                                     C2Config::LEVEL_VP9_4_1,
                                     C2Config::LEVEL_VP9_5,
                                 })})
                .withSetter(ProfileLevelSetter, mSize)
                .build());

        mHdr10PlusInfoInput = C2StreamHdr10PlusInfo::input::AllocShared(0);
        addParameter(
            DefineParam(mHdr10PlusInfoInput, C2_PARAMKEY_INPUT_HDR10_PLUS_INFO)
                .withDefault(mHdr10PlusInfoInput)
                .withFields({
                    C2F(mHdr10PlusInfoInput, m.value).any(),
                })
                .withSetter(Hdr10PlusInfoInputSetter)
                .build());

        mHdr10PlusInfoOutput = C2StreamHdr10PlusInfo::output::AllocShared(0);
        addParameter(DefineParam(mHdr10PlusInfoOutput,
                                 C2_PARAMKEY_OUTPUT_HDR10_PLUS_INFO)
                         .withDefault(mHdr10PlusInfoOutput)
                         .withFields({
                             C2F(mHdr10PlusInfoOutput, m.value).any(),
                         })
                         .withSetter(Hdr10PlusInfoOutputSetter)
                         .build());

#if 0
        // sample BT.2020 static info
        mHdrStaticInfo = std::make_shared<C2StreamHdrStaticInfo::output>();
        mHdrStaticInfo->mastering = {
            .red   = { .x = 0.708,  .y = 0.292 },
            .green = { .x = 0.170,  .y = 0.797 },
            .blue  = { .x = 0.131,  .y = 0.046 },
            .white = { .x = 0.3127, .y = 0.3290 },
            .maxLuminance = 1000,
            .minLuminance = 0.1,
        };
        mHdrStaticInfo->maxCll = 1000;
        mHdrStaticInfo->maxFall = 120;

        mHdrStaticInfo->maxLuminance = 0; // disable static info

        helper->addStructDescriptors<C2MasteringDisplayColorVolumeStruct, C2ColorXyStruct>();
        addParameter(
                DefineParam(mHdrStaticInfo, C2_PARAMKEY_HDR_STATIC_INFO)
                .withDefault(mHdrStaticInfo)
                .withFields({
                    C2F(mHdrStaticInfo, mastering.red.x).inRange(0, 1),
                    // TODO
                })
                .withSetter(HdrStaticInfoSetter)
                .build());
#endif
#else
        addParameter(
            DefineParam(mProfileLevel, C2_PARAMKEY_PROFILE_LEVEL)
                .withConstValue(new C2StreamProfileLevelInfo::input(
                    0u, C2Config::PROFILE_UNUSED, C2Config::LEVEL_UNUSED))
                .build());
#endif

        addParameter(DefineParam(mMaxSize, C2_PARAMKEY_MAX_PICTURE_SIZE)
                         .withDefault(new C2StreamMaxPictureSizeTuning::output(
                             0u, 320, 240))
                         .withFields({
                             C2F(mSize, width).inRange(2, 4096, 2),
                             C2F(mSize, height).inRange(2, 4096, 2),
                         })
                         .withSetter(MaxPictureSizeSetter, mSize)
                         .build());

        addParameter(
            DefineParam(mMaxInputSize, C2_PARAMKEY_INPUT_MAX_BUFFER_SIZE)
                .withDefault(new C2StreamMaxBufferSizeInfo::input(
                    0u, kMinInputBufferSize))
                .withFields({
                    C2F(mMaxInputSize, value).any(),
                })
                .calculatedAs(MaxInputSizeSetter, mMaxSize)
                .build());

        C2ChromaOffsetStruct locations[1] = {
            C2ChromaOffsetStruct::ITU_YUV_420_0()};
        std::shared_ptr<C2StreamColorInfo::output> defaultColorInfo =
            C2StreamColorInfo::output::AllocShared(1u, 0u, 8u /* bitDepth */,
                                                   C2Color::YUV_420);
        memcpy(defaultColorInfo->m.locations, locations, sizeof(locations));

        defaultColorInfo = C2StreamColorInfo::output::AllocShared(
            {C2ChromaOffsetStruct::ITU_YUV_420_0()}, 0u, 8u /* bitDepth */,
            C2Color::YUV_420);
        helper->addStructDescriptors<C2ChromaOffsetStruct>();

        addParameter(DefineParam(mColorInfo, C2_PARAMKEY_CODED_COLOR_INFO)
                         .withConstValue(defaultColorInfo)
                         .build());

        addParameter(
            DefineParam(mDefaultColorAspects, C2_PARAMKEY_DEFAULT_COLOR_ASPECTS)
                .withDefault(new C2StreamColorAspectsTuning::output(
                    0u, C2Color::RANGE_UNSPECIFIED,
                    C2Color::PRIMARIES_UNSPECIFIED,
                    C2Color::TRANSFER_UNSPECIFIED, C2Color::MATRIX_UNSPECIFIED))
                .withFields({C2F(mDefaultColorAspects, range)
                                 .inRange(C2Color::RANGE_UNSPECIFIED,
                                          C2Color::RANGE_OTHER),
                             C2F(mDefaultColorAspects, primaries)
                                 .inRange(C2Color::PRIMARIES_UNSPECIFIED,
                                          C2Color::PRIMARIES_OTHER),
                             C2F(mDefaultColorAspects, transfer)
                                 .inRange(C2Color::TRANSFER_UNSPECIFIED,
                                          C2Color::TRANSFER_OTHER),
                             C2F(mDefaultColorAspects, matrix)
                                 .inRange(C2Color::MATRIX_UNSPECIFIED,
                                          C2Color::MATRIX_OTHER)})
                .withSetter(DefaultColorAspectsSetter)
                .build());

        // TODO: support more formats?
        addParameter(DefineParam(mPixelFormat, C2_PARAMKEY_PIXEL_FORMAT)
                         .withConstValue(new C2StreamPixelFormatInfo::output(
                             0u, HAL_PIXEL_FORMAT_YCBCR_420_888))
                         .build());
    }

    static C2R SizeSetter(bool mayBlock,
                          const C2P<C2StreamPictureSizeInfo::output> &oldMe,
                          C2P<C2StreamPictureSizeInfo::output> &me) {
        (void)mayBlock;
        DDD("calling sizesetter old w %d", oldMe.v.width);
        DDD("calling sizesetter old h %d", oldMe.v.height);
        DDD("calling sizesetter change to w %d", me.v.width);
        DDD("calling sizesetter change to h %d", me.v.height);
        C2R res = C2R::Ok();
        auto mewidth = me.F(me.v.width);
        auto meheight = me.F(me.v.height);

        if (!mewidth.supportsAtAll(me.v.width)) {
            res = res.plus(C2SettingResultBuilder::BadValue(me.F(me.v.width)));
            DDD("override width with oldMe value");
            me.set().width = oldMe.v.width;
            DDD("something wrong here %s %d", __func__, __LINE__);
        }
        if (!meheight.supportsAtAll(me.v.height)) {
            res = res.plus(C2SettingResultBuilder::BadValue(me.F(me.v.height)));
            DDD("override height with oldMe value");
            me.set().height = oldMe.v.height;
            DDD("something wrong here %s %d", __func__, __LINE__);
        }
        return res;
    }

    static C2R
    MaxPictureSizeSetter(bool mayBlock,
                         C2P<C2StreamMaxPictureSizeTuning::output> &me,
                         const C2P<C2StreamPictureSizeInfo::output> &size) {
        (void)mayBlock;
        // TODO: get max width/height from the size's field helpers vs.
        // hardcoding
        me.set().width = c2_min(c2_max(me.v.width, size.v.width), 4096u);
        me.set().height = c2_min(c2_max(me.v.height, size.v.height), 4096u);
        return C2R::Ok();
    }

    static C2R MaxInputSizeSetter(
        bool mayBlock, C2P<C2StreamMaxBufferSizeInfo::input> &me,
        const C2P<C2StreamMaxPictureSizeTuning::output> &maxSize) {
        (void)mayBlock;
        // assume compression ratio of 2
        me.set().value = c2_max((((maxSize.v.width + 63) / 64) *
                                 ((maxSize.v.height + 63) / 64) * 3072),
                                kMinInputBufferSize);
        return C2R::Ok();
    }

    static C2R
    DefaultColorAspectsSetter(bool mayBlock,
                              C2P<C2StreamColorAspectsTuning::output> &me) {
        (void)mayBlock;
        if (me.v.range > C2Color::RANGE_OTHER) {
            me.set().range = C2Color::RANGE_OTHER;
        }
        if (me.v.primaries > C2Color::PRIMARIES_OTHER) {
            me.set().primaries = C2Color::PRIMARIES_OTHER;
        }
        if (me.v.transfer > C2Color::TRANSFER_OTHER) {
            me.set().transfer = C2Color::TRANSFER_OTHER;
        }
        if (me.v.matrix > C2Color::MATRIX_OTHER) {
            me.set().matrix = C2Color::MATRIX_OTHER;
        }
        return C2R::Ok();
    }

    static C2R
    ProfileLevelSetter(bool mayBlock, C2P<C2StreamProfileLevelInfo::input> &me,
                       const C2P<C2StreamPictureSizeInfo::output> &size) {
        (void)mayBlock;
        (void)size;
        (void)me; // TODO: validate
        return C2R::Ok();
    }
    std::shared_ptr<C2StreamColorAspectsTuning::output>
    getDefaultColorAspects_l() {
        return mDefaultColorAspects;
    }

    int width() const { return mSize->width; }

    int height() const { return mSize->height; }

    int primaries() const { return mDefaultColorAspects->primaries; }

    int range() const { return mDefaultColorAspects->range; }

    int transfer() const { return mDefaultColorAspects->transfer; }

    static C2R Hdr10PlusInfoInputSetter(bool mayBlock,
                                        C2P<C2StreamHdr10PlusInfo::input> &me) {
        (void)mayBlock;
        (void)me; // TODO: validate
        return C2R::Ok();
    }

    static C2R
    Hdr10PlusInfoOutputSetter(bool mayBlock,
                              C2P<C2StreamHdr10PlusInfo::output> &me) {
        (void)mayBlock;
        (void)me; // TODO: validate
        return C2R::Ok();
    }

  private:
    std::shared_ptr<C2StreamProfileLevelInfo::input> mProfileLevel;
    std::shared_ptr<C2StreamPictureSizeInfo::output> mSize;
    std::shared_ptr<C2StreamMaxPictureSizeTuning::output> mMaxSize;
    std::shared_ptr<C2StreamMaxBufferSizeInfo::input> mMaxInputSize;
    std::shared_ptr<C2StreamColorInfo::output> mColorInfo;
    std::shared_ptr<C2StreamPixelFormatInfo::output> mPixelFormat;
    std::shared_ptr<C2StreamColorAspectsTuning::output> mDefaultColorAspects;
#ifdef VP9
#if 0
    std::shared_ptr<C2StreamHdrStaticInfo::output> mHdrStaticInfo;
#endif
    std::shared_ptr<C2StreamHdr10PlusInfo::input> mHdr10PlusInfoInput;
    std::shared_ptr<C2StreamHdr10PlusInfo::output> mHdr10PlusInfoOutput;
#endif
};

C2GoldfishVpxDec::ConverterThread::ConverterThread(
    const std::shared_ptr<Mutexed<ConversionQueue>> &queue)
    : Thread(false), mQueue(queue) {}

bool C2GoldfishVpxDec::ConverterThread::threadLoop() {
    Mutexed<ConversionQueue>::Locked queue(*mQueue);
    if (queue->entries.empty()) {
        queue.waitForCondition(queue->cond);
        if (queue->entries.empty()) {
            return true;
        }
    }
    std::function<void()> convert = queue->entries.front();
    queue->entries.pop_front();
    if (!queue->entries.empty()) {
        queue->cond.signal();
    }
    queue.unlock();

    convert();

    queue.lock();
    if (--queue->numPending == 0u) {
        queue->cond.broadcast();
    }
    return true;
}

C2GoldfishVpxDec::C2GoldfishVpxDec(const char *name, c2_node_id_t id,
                                   const std::shared_ptr<IntfImpl> &intfImpl)
    : SimpleC2Component(
          std::make_shared<SimpleInterface<IntfImpl>>(name, id, intfImpl)),
      mIntf(intfImpl), mCtx(nullptr), mQueue(new Mutexed<ConversionQueue>) {}

C2GoldfishVpxDec::~C2GoldfishVpxDec() { onRelease(); }

c2_status_t C2GoldfishVpxDec::onInit() {
    status_t err = initDecoder();
    return err == OK ? C2_OK : C2_CORRUPTED;
}

c2_status_t C2GoldfishVpxDec::onStop() {
    mSignalledError = false;
    mSignalledOutputEos = false;

    return C2_OK;
}

void C2GoldfishVpxDec::onReset() {
    (void)onStop();
    c2_status_t err = onFlush_sm();
    if (err != C2_OK) {
        ALOGW("Failed to flush decoder. Try to hard reset decoder");
        destroyDecoder();
        (void)initDecoder();
    }
}

void C2GoldfishVpxDec::onRelease() { destroyDecoder(); }

void C2GoldfishVpxDec::sendMetadata() {
    // compare and send if changed
    MetaDataColorAspects currentMetaData = {1, 0, 0, 0};
    currentMetaData.primaries = mIntf->primaries();
    currentMetaData.range = mIntf->range();
    currentMetaData.transfer = mIntf->transfer();

    DDD("metadata primaries %d range %d transfer %d",
            (int)(currentMetaData.primaries),
            (int)(currentMetaData.range),
            (int)(currentMetaData.transfer)
       );

    if (mSentMetadata.primaries == currentMetaData.primaries &&
        mSentMetadata.range == currentMetaData.range &&
        mSentMetadata.transfer == currentMetaData.transfer) {
        DDD("metadata is the same, no need to update");
        return;
    }
    std::swap(mSentMetadata, currentMetaData);

    vpx_codec_send_metadata(mCtx, &(mSentMetadata));
}

c2_status_t C2GoldfishVpxDec::onFlush_sm() {
    if (mFrameParallelMode) {
        // Flush decoder by passing nullptr data ptr and 0 size.
        // Ideally, this should never fail.
        if (vpx_codec_flush(mCtx)) {
            ALOGE("Failed to flush on2 decoder.");
            return C2_CORRUPTED;
        }
    }

    // Drop all the decoded frames in decoder.
    if (mCtx) {
        setup_ctx_parameters(mCtx);
        while ((mImg = vpx_codec_get_frame(mCtx))) {
        }
    }

    mSignalledError = false;
    mSignalledOutputEos = false;
    return C2_OK;
}

status_t C2GoldfishVpxDec::initDecoder() {
    ALOGI("calling init GoldfishVPX");
#ifdef VP9
    mMode = MODE_VP9;
#else
    mMode = MODE_VP8;
#endif

    mWidth = 320;
    mHeight = 240;
    mFrameParallelMode = false;
    mSignalledOutputEos = false;
    mSignalledError = false;

    return OK;
}

void C2GoldfishVpxDec::checkContext(const std::shared_ptr<C2BlockPool> &pool) {
    if (mCtx)
        return;

    mWidth = mIntf->width();
    mHeight = mIntf->height();
    ALOGI("created decoder context w %d h %d", mWidth, mHeight);
    mCtx = new vpx_codec_ctx_t;
    mCtx->vpversion = mMode == MODE_VP8 ? 8 : 9;

    //const bool isGraphic = (pool->getLocalId() == C2PlatformAllocatorStore::GRALLOC);
    const bool isGraphic = (pool->getAllocatorId() & C2Allocator::GRAPHIC);
    DDD("buffer pool allocator id %x",  (int)(pool->getAllocatorId()));
    if (isGraphic) {
        uint64_t client_usage = getClientUsage(pool);
        DDD("client has usage as 0x%llx", client_usage);
        if (client_usage & BufferUsage::CPU_READ_MASK) {
            DDD("decoding to guest byte buffer as client has read usage");
            mEnableAndroidNativeBuffers = false;
        } else {
            DDD("decoding to host color buffer");
            mEnableAndroidNativeBuffers = true;
        }
    } else {
        DDD("decoding to guest byte buffer");
        mEnableAndroidNativeBuffers = false;
    }

    mCtx->version = mEnableAndroidNativeBuffers ? 200 : 100;

    int vpx_err = 0;
    if ((vpx_err = vpx_codec_dec_init(mCtx))) {
        ALOGE("vpx decoder failed to initialize. (%d)", vpx_err);
        delete mCtx;
        mCtx = NULL;
    }
}

status_t C2GoldfishVpxDec::destroyDecoder() {
    if (mCtx) {
        ALOGI("calling destroying GoldfishVPX ctx %p", mCtx);
        vpx_codec_destroy(mCtx);
        delete mCtx;
        mCtx = NULL;
    }

    return OK;
}

void fillEmptyWork(const std::unique_ptr<C2Work> &work) {
    uint32_t flags = 0;
    if (work->input.flags & C2FrameData::FLAG_END_OF_STREAM) {
        flags |= C2FrameData::FLAG_END_OF_STREAM;
        DDD("signalling eos");
    }
    work->worklets.front()->output.flags = (C2FrameData::flags_t)flags;
    work->worklets.front()->output.buffers.clear();
    work->worklets.front()->output.ordinal = work->input.ordinal;
    work->workletsProcessed = 1u;
}

void C2GoldfishVpxDec::finishWork(
    uint64_t index, const std::unique_ptr<C2Work> &work,
    const std::shared_ptr<C2GraphicBlock> &block) {
    std::shared_ptr<C2Buffer> buffer =
        createGraphicBuffer(block, C2Rect(mWidth, mHeight));
    auto fillWork = [buffer, index,
                     intf = this->mIntf](const std::unique_ptr<C2Work> &work) {
        uint32_t flags = 0;
        if ((work->input.flags & C2FrameData::FLAG_END_OF_STREAM) &&
            (c2_cntr64_t(index) == work->input.ordinal.frameIndex)) {
            flags |= C2FrameData::FLAG_END_OF_STREAM;
            DDD("signalling eos");
        }
        work->worklets.front()->output.flags = (C2FrameData::flags_t)flags;
        work->worklets.front()->output.buffers.clear();
        work->worklets.front()->output.buffers.push_back(buffer);
        work->worklets.front()->output.ordinal = work->input.ordinal;
        work->workletsProcessed = 1u;

        for (const std::unique_ptr<C2Param> &param : work->input.configUpdate) {
            if (param) {
                C2StreamHdr10PlusInfo::input *hdr10PlusInfo =
                    C2StreamHdr10PlusInfo::input::From(param.get());

                if (hdr10PlusInfo != nullptr) {
                    std::vector<std::unique_ptr<C2SettingResult>> failures;
                    std::unique_ptr<C2Param> outParam = C2Param::CopyAsStream(
                        *param.get(), true /*output*/, param->stream());
                    c2_status_t err =
                        intf->config({outParam.get()}, C2_MAY_BLOCK, &failures);
                    if (err == C2_OK) {
                        work->worklets.front()->output.configUpdate.push_back(
                            C2Param::Copy(*outParam.get()));
                    } else {
                        ALOGE("finishWork: Config update size failed");
                    }
                    break;
                }
            }
        }
    };
    if (work && c2_cntr64_t(index) == work->input.ordinal.frameIndex) {
        fillWork(work);
    } else {
        finish(index, fillWork);
    }
}

void C2GoldfishVpxDec::process(const std::unique_ptr<C2Work> &work,
                               const std::shared_ptr<C2BlockPool> &pool) {
    DDD("%s %d doing work now", __func__, __LINE__);
    // Initialize output work
    work->result = C2_OK;
    work->workletsProcessed = 0u;
    work->worklets.front()->output.configUpdate.clear();
    work->worklets.front()->output.flags = work->input.flags;

    if (mSignalledError || mSignalledOutputEos) {
        work->result = C2_BAD_VALUE;
        return;
    }

    size_t inOffset = 0u;
    size_t inSize = 0u;
    C2ReadView rView = mDummyReadView;
    if (!work->input.buffers.empty()) {
        rView =
            work->input.buffers[0]->data().linearBlocks().front().map().get();
        inSize = rView.capacity();
        if (inSize && rView.error()) {
            ALOGE("read view map failed %d", rView.error());
            work->result = C2_CORRUPTED;
            return;
        }
    }

    checkContext(pool);

    bool codecConfig =
        ((work->input.flags & C2FrameData::FLAG_CODEC_CONFIG) != 0);
    bool eos = ((work->input.flags & C2FrameData::FLAG_END_OF_STREAM) != 0);

    DDD("in buffer attr. size %zu timestamp %d frameindex %d, flags %x", inSize,
        (int)work->input.ordinal.timestamp.peeku(),
        (int)work->input.ordinal.frameIndex.peeku(), work->input.flags);

    // Software VP9 Decoder does not need the Codec Specific Data (CSD)
    // (specified in http://www.webmproject.org/vp9/profiles/). Ignore it if
    // it was passed.
    if (codecConfig) {
        // Ignore CSD buffer for VP9.
        if (mMode == MODE_VP9) {
            fillEmptyWork(work);
            return;
        } else {
            // Tolerate the CSD buffer for VP8. This is a workaround
            // for b/28689536. continue
            ALOGW("WARNING: Got CSD buffer for VP8. Continue");
        }
    }

    sendMetadata();

    if (inSize) {
        uint8_t *bitstream = const_cast<uint8_t *>(rView.data() + inOffset);
        vpx_codec_err_t err = vpx_codec_decode(
            mCtx, bitstream, inSize, &work->input.ordinal.frameIndex, 0);
        if (err != 0) {
            ALOGE("on2 decoder failed to decode frame. err: ");
            mSignalledError = true;
            work->workletsProcessed = 1u;
            work->result = C2_CORRUPTED;
            return;
        }
    }

    status_t err = outputBuffer(pool, work);
    if (err == NOT_ENOUGH_DATA) {
        if (inSize > 0) {
            DDD("Maybe non-display frame at %lld.",
                work->input.ordinal.frameIndex.peekll());
            // send the work back with empty buffer.
            inSize = 0;
        }
    } else if (err != OK) {
        ALOGD("Error while getting the output frame out");
        // work->result would be already filled; do fillEmptyWork() below to
        // send the work back.
        inSize = 0;
    }

    if (eos) {
        drainInternal(DRAIN_COMPONENT_WITH_EOS, pool, work);
        mSignalledOutputEos = true;
    } else if (!inSize) {
        fillEmptyWork(work);
    }
}

static void copyOutputBufferToYuvPlanarFrame(
    uint8_t *dst, const uint8_t *srcY, const uint8_t *srcU, const uint8_t *srcV,
    size_t srcYStride, size_t srcUStride, size_t srcVStride, size_t dstYStride,
    size_t dstUVStride, uint32_t width, uint32_t height) {
    uint8_t *dstStart = dst;

    for (size_t i = 0; i < height; ++i) {
        memcpy(dst, srcY, width);
        srcY += srcYStride;
        dst += dstYStride;
    }

    dst = dstStart + dstYStride * height;
    for (size_t i = 0; i < height / 2; ++i) {
        memcpy(dst, srcV, width / 2);
        srcV += srcVStride;
        dst += dstUVStride;
    }

    dst = dstStart + (dstYStride * height) + (dstUVStride * height / 2);
    for (size_t i = 0; i < height / 2; ++i) {
        memcpy(dst, srcU, width / 2);
        srcU += srcUStride;
        dst += dstUVStride;
    }
}

void C2GoldfishVpxDec::setup_ctx_parameters(vpx_codec_ctx_t *ctx,
                                            int hostColorBufferId) {
    ctx->width = mWidth;
    ctx->height = mHeight;
    ctx->hostColorBufferId = hostColorBufferId;
    ctx->outputBufferWidth = mWidth;
    ctx->outputBufferHeight = mHeight;
    int32_t bpp = 1;
    ctx->bpp = bpp;
}

status_t
C2GoldfishVpxDec::outputBuffer(const std::shared_ptr<C2BlockPool> &pool,
                               const std::unique_ptr<C2Work> &work) {
    if (!(work && pool))
        return BAD_VALUE;

    // now get the block
    std::shared_ptr<C2GraphicBlock> block;
    uint32_t format = HAL_PIXEL_FORMAT_YCBCR_420_888;
    const C2MemoryUsage usage = {(uint64_t)(BufferUsage::VIDEO_DECODER),
                                 C2MemoryUsage::CPU_WRITE | C2MemoryUsage::CPU_READ};

    c2_status_t err = pool->fetchGraphicBlock(align(mWidth, 2), mHeight, format,
                                              usage, &block);
    if (err != C2_OK) {
        ALOGE("fetchGraphicBlock for Output failed with status %d", err);
        work->result = err;
        return UNKNOWN_ERROR;
    }

    int hostColorBufferId = -1;
    const bool decodingToHostColorBuffer = mEnableAndroidNativeBuffers;
    if(decodingToHostColorBuffer){
        auto c2Handle = block->handle();
        native_handle_t *grallocHandle =
            UnwrapNativeCodec2GrallocHandle(c2Handle);
        hostColorBufferId = getColorBufferHandle(grallocHandle);
        if (hostColorBufferId > 0) {
            DDD("found handle %d", hostColorBufferId);
        } else {
            DDD("decode to buffer, because handle %d is invalid",
                hostColorBufferId);
            // change to -1 so host knows it is definitely invalid
            // 0 is a bit confusing
            hostColorBufferId = -1;
        }
    }
    setup_ctx_parameters(mCtx, hostColorBufferId);

    vpx_image_t *img = vpx_codec_get_frame(mCtx);

    if (!img)
        return NOT_ENOUGH_DATA;

    if (img->d_w != mWidth || img->d_h != mHeight) {
        DDD("updating w %d h %d to w %d h %d", mWidth, mHeight, img->d_w,
            img->d_h);
        mWidth = img->d_w;
        mHeight = img->d_h;

        // need to re-allocate since size changed, especially for byte buffer
        // mode
        if (true) {
            c2_status_t err = pool->fetchGraphicBlock(align(mWidth, 2), mHeight,
                                                      format, usage, &block);
            if (err != C2_OK) {
                ALOGE("fetchGraphicBlock for Output failed with status %d",
                      err);
                work->result = err;
                return UNKNOWN_ERROR;
            }
        }

        C2StreamPictureSizeInfo::output size(0u, mWidth, mHeight);
        std::vector<std::unique_ptr<C2SettingResult>> failures;
        c2_status_t err = mIntf->config({&size}, C2_MAY_BLOCK, &failures);
        if (err == C2_OK) {
            work->worklets.front()->output.configUpdate.push_back(
                C2Param::Copy(size));
        } else {
            ALOGE("Config update size failed");
            mSignalledError = true;
            work->workletsProcessed = 1u;
            work->result = C2_CORRUPTED;
            return UNKNOWN_ERROR;
        }
    }
    if (img->fmt != VPX_IMG_FMT_I420 && img->fmt != VPX_IMG_FMT_I42016) {
        ALOGE("img->fmt %d not supported", img->fmt);
        mSignalledError = true;
        work->workletsProcessed = 1u;
        work->result = C2_CORRUPTED;
        return false;
    }

    if (img->fmt == VPX_IMG_FMT_I42016) {
        IntfImpl::Lock lock = mIntf->lock();
        std::shared_ptr<C2StreamColorAspectsTuning::output>
            defaultColorAspects = mIntf->getDefaultColorAspects_l();

        if (defaultColorAspects->primaries == C2Color::PRIMARIES_BT2020 &&
            defaultColorAspects->matrix == C2Color::MATRIX_BT2020 &&
            defaultColorAspects->transfer == C2Color::TRANSFER_ST2084) {
            format = HAL_PIXEL_FORMAT_RGBA_1010102;
        }
    }

    if (!decodingToHostColorBuffer) {

        C2GraphicView wView = block->map().get();
        if (wView.error()) {
            ALOGE("graphic view map failed %d", wView.error());
            work->result = C2_CORRUPTED;
            return UNKNOWN_ERROR;
        }

        DDD("provided (%dx%d) required (%dx%d), out frameindex %lld",
            block->width(), block->height(), mWidth, mHeight,
            ((c2_cntr64_t *)img->user_priv)->peekll());

        uint8_t *dst =
            const_cast<uint8_t *>(wView.data()[C2PlanarLayout::PLANE_Y]);
        size_t srcYStride = mWidth;
        size_t srcUStride = mWidth / 2;
        size_t srcVStride = mWidth / 2;
        C2PlanarLayout layout = wView.layout();
        size_t dstYStride = layout.planes[C2PlanarLayout::PLANE_Y].rowInc;
        size_t dstUVStride = layout.planes[C2PlanarLayout::PLANE_U].rowInc;

        if (img->fmt == VPX_IMG_FMT_I42016) {
            ALOGW("WARNING: not I42016 is not supported !!!");
        } else if (1) {
            const uint8_t *srcY = (const uint8_t *)mCtx->dst;
            const uint8_t *srcV = srcY + mWidth * mHeight;
            const uint8_t *srcU = srcV + mWidth * mHeight / 4;
            // TODO: the following crashes
            copyOutputBufferToYuvPlanarFrame(dst, srcY, srcU, srcV, srcYStride,
                                             srcUStride, srcVStride, dstYStride,
                                             dstUVStride, mWidth, mHeight);
            // memcpy(dst, srcY, mWidth * mHeight / 2);
        }
    }
    DDD("provided (%dx%d) required (%dx%d), out frameindex %lld",
        block->width(), block->height(), mWidth, mHeight,
        ((c2_cntr64_t *)img->user_priv)->peekll());

    finishWork(((c2_cntr64_t *)img->user_priv)->peekull(), work,
               std::move(block));
    return OK;
}

c2_status_t
C2GoldfishVpxDec::drainInternal(uint32_t drainMode,
                                const std::shared_ptr<C2BlockPool> &pool,
                                const std::unique_ptr<C2Work> &work) {
    if (drainMode == NO_DRAIN) {
        ALOGW("drain with NO_DRAIN: no-op");
        return C2_OK;
    }
    if (drainMode == DRAIN_CHAIN) {
        ALOGW("DRAIN_CHAIN not supported");
        return C2_OMITTED;
    }

    while (outputBuffer(pool, work) == OK) {
    }

    if (drainMode == DRAIN_COMPONENT_WITH_EOS && work &&
        work->workletsProcessed == 0u) {
        fillEmptyWork(work);
    }

    return C2_OK;
}
c2_status_t C2GoldfishVpxDec::drain(uint32_t drainMode,
                                    const std::shared_ptr<C2BlockPool> &pool) {
    return drainInternal(drainMode, pool, nullptr);
}

class C2GoldfishVpxFactory : public C2ComponentFactory {
  public:
    C2GoldfishVpxFactory()
        : mHelper(std::static_pointer_cast<C2ReflectorHelper>(
              GoldfishComponentStore::Create()->getParamReflector())) {

        ALOGI("platform store is %p, reflector is %p",
              GetCodec2PlatformComponentStore().get(),
              GetCodec2PlatformComponentStore()->getParamReflector().get());
    }

    virtual c2_status_t
    createComponent(c2_node_id_t id,
                    std::shared_ptr<C2Component> *const component,
                    std::function<void(C2Component *)> deleter) override {
        *component = std::shared_ptr<C2Component>(
            new C2GoldfishVpxDec(
                COMPONENT_NAME, id,
                std::make_shared<C2GoldfishVpxDec::IntfImpl>(mHelper)),
            deleter);
        return C2_OK;
    }

    virtual c2_status_t createInterface(
        c2_node_id_t id, std::shared_ptr<C2ComponentInterface> *const interface,
        std::function<void(C2ComponentInterface *)> deleter) override {
        *interface = std::shared_ptr<C2ComponentInterface>(
            new SimpleInterface<C2GoldfishVpxDec::IntfImpl>(
                COMPONENT_NAME, id,
                std::make_shared<C2GoldfishVpxDec::IntfImpl>(mHelper)),
            deleter);
        return C2_OK;
    }

    virtual ~C2GoldfishVpxFactory() override = default;

  private:
    std::shared_ptr<C2ReflectorHelper> mHelper;
};

} // namespace android

extern "C" ::C2ComponentFactory *CreateCodec2Factory() {
    DDD("in %s", __func__);
    return new ::android::C2GoldfishVpxFactory();
}

extern "C" void DestroyCodec2Factory(::C2ComponentFactory *factory) {
    DDD("in %s", __func__);
    delete factory;
}
