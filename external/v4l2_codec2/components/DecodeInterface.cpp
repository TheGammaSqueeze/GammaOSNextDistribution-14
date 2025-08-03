// Copyright 2023 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

//#define LOG_NDEBUG 0
#define LOG_TAG "DecodeInterface"

#include <v4l2_codec2/components/DecodeInterface.h>

#include <C2PlatformSupport.h>
#include <SimpleC2Interface.h>
#include <android/hardware/graphics/common/1.0/types.h>
#include <log/log.h>
#include <media/stagefright/foundation/MediaDefs.h>

#include <v4l2_codec2/common/Common.h>
#include <v4l2_codec2/plugin_store/V4L2AllocatorId.h>

namespace android {
namespace {

constexpr size_t k1080pArea = 1920 * 1088;
constexpr size_t k4KArea = 3840 * 2160;
// Input bitstream buffer size for up to 1080p streams.
// Set it to 2MB since it is possible for the encoded bitstream to exceed the size of 1MB
// when using higher bitrates, like 1Mb/s on slower devices. Also, this brings up compability
// with the Chrome browser as it is using 2MB buffer size for a 1080p stream, ref:
// https://source.chromium.org/chromium/chromium/src/+/main:media/gpu/gpu_video_encode_accelerator_helpers.cc;l=25
constexpr size_t kInputBufferSizeFor1080p = 2 * 1024 * 1024;  // 2MB
// Input bitstream buffer size for up to 4k streams.
constexpr size_t kInputBufferSizeFor4K = 4 * kInputBufferSizeFor1080p;

size_t calculateInputBufferSize(size_t area) {
    if (area > k4KArea) {
        ALOGW("Input buffer size for video size (%zu) larger than 4K (%zu) might be too small.",
              area, k4KArea);
    }

    // Enlarge the input buffer for 4k video
    if (area > k1080pArea) return kInputBufferSizeFor4K;
    return kInputBufferSizeFor1080p;
}
}  // namespace

// static
C2R DecodeInterface::ProfileLevelSetter(bool /* mayBlock */,
                                        C2P<C2StreamProfileLevelInfo::input>& info) {
    return info.F(info.v.profile)
            .validatePossible(info.v.profile)
            .plus(info.F(info.v.level).validatePossible(info.v.level));
}

// static
C2R DecodeInterface::SizeSetter(bool /* mayBlock */,
                                C2P<C2StreamPictureSizeInfo::output>& videoSize) {
    return videoSize.F(videoSize.v.width)
            .validatePossible(videoSize.v.width)
            .plus(videoSize.F(videoSize.v.height).validatePossible(videoSize.v.height));
}

C2R DecodeInterface::InputSizeSetter(bool /* mayBlock */,
                                     C2P<C2StreamMaxBufferSizeInfo::input>& inputSize) {
    return inputSize.F(inputSize.v.value).validatePossible(inputSize.v.value);
}

// static
template <typename T>
C2R DecodeInterface::DefaultColorAspectsSetter(bool /* mayBlock */, C2P<T>& def) {
    if (def.v.range > C2Color::RANGE_OTHER) {
        def.set().range = C2Color::RANGE_OTHER;
    }
    if (def.v.primaries > C2Color::PRIMARIES_OTHER) {
        def.set().primaries = C2Color::PRIMARIES_OTHER;
    }
    if (def.v.transfer > C2Color::TRANSFER_OTHER) {
        def.set().transfer = C2Color::TRANSFER_OTHER;
    }
    if (def.v.matrix > C2Color::MATRIX_OTHER) {
        def.set().matrix = C2Color::MATRIX_OTHER;
    }
    return C2R::Ok();
}

// static
C2R DecodeInterface::MergedColorAspectsSetter(bool /* mayBlock */,
                                              C2P<C2StreamColorAspectsInfo::output>& merged,
                                              const C2P<C2StreamColorAspectsTuning::output>& def,
                                              const C2P<C2StreamColorAspectsInfo::input>& coded) {
    // Take coded values for all specified fields, and default values for unspecified ones.
    merged.set().range = coded.v.range == RANGE_UNSPECIFIED ? def.v.range : coded.v.range;
    merged.set().primaries =
            coded.v.primaries == PRIMARIES_UNSPECIFIED ? def.v.primaries : coded.v.primaries;
    merged.set().transfer =
            coded.v.transfer == TRANSFER_UNSPECIFIED ? def.v.transfer : coded.v.transfer;
    merged.set().matrix = coded.v.matrix == MATRIX_UNSPECIFIED ? def.v.matrix : coded.v.matrix;
    return C2R::Ok();
}

// static
C2R DecodeInterface::MaxInputBufferSizeCalculator(
        bool /* mayBlock */, C2P<C2StreamMaxBufferSizeInfo::input>& me,
        const C2P<C2StreamPictureSizeInfo::output>& size) {
    size_t calculatedSize = calculateInputBufferSize(size.v.width * size.v.height);

    if (me.v.value < calculatedSize) me.set().value = calculatedSize;

    return C2R::Ok();
}

DecodeInterface::DecodeInterface(const std::string& name,
                                 const std::shared_ptr<C2ReflectorHelper>& helper,
                                 const SupportedCapabilities& caps)
      : C2InterfaceHelper(helper), mInitStatus(C2_OK), mVideoCodec(caps.codec) {
    ALOGV("%s(%s)", __func__, name.c_str());

    setDerivedInstance(this);

    addParameter(DefineParam(mKind, C2_PARAMKEY_COMPONENT_KIND)
                         .withConstValue(new C2ComponentKindSetting(C2Component::KIND_DECODER))
                         .build());

    std::string inputMime;

    ui::Size maxSize(1, 1);

    std::vector<uint32_t> profiles;
    for (const auto& supportedProfile : caps.supportedProfiles) {
        if (isValidProfileForCodec(mVideoCodec.value(), supportedProfile.profile)) {
            profiles.push_back(static_cast<uint32_t>(supportedProfile.profile));
            maxSize.setWidth(std::max(maxSize.width, supportedProfile.max_resolution.width));
            maxSize.setHeight(std::max(maxSize.height, supportedProfile.max_resolution.height));
        }
    }

    // In case of no supported profile or uninitialized device maxSize is set to default
    if (maxSize == ui::Size(1, 1)) maxSize = ui::Size(4096, 4096);

    if (profiles.empty()) {
        ALOGW("No supported profiles for H264 codec");
        switch (*mVideoCodec) {  //default values used when querry is not supported
        case VideoCodec::H264:
            profiles = {
                    C2Config::PROFILE_AVC_BASELINE,
                    C2Config::PROFILE_AVC_CONSTRAINED_BASELINE,
                    C2Config::PROFILE_AVC_MAIN,
                    C2Config::PROFILE_AVC_HIGH,
            };
            break;
        case VideoCodec::VP8:
            profiles = {C2Config::PROFILE_VP8_0};
            break;
        case VideoCodec::VP9:
            profiles = {C2Config::PROFILE_VP9_0};
            break;
        case VideoCodec::HEVC:
            profiles = {C2Config::PROFILE_HEVC_MAIN};
            break;
        }
    }

    uint32_t defaultProfile = caps.defaultProfile;
    if (defaultProfile == C2Config::PROFILE_UNUSED)
        defaultProfile = *std::min_element(profiles.begin(), profiles.end());

    std::vector<unsigned int> levels;
    std::vector<C2Config::level_t> supportedLevels = caps.supportedLevels;
    for (const auto& supportedLevel : supportedLevels) {
        levels.push_back(static_cast<unsigned int>(supportedLevel));
    }

    if (levels.empty()) {
        ALOGE("No supported levels for H264 codec");
        switch (*mVideoCodec) {  //default values used when querry is not supported
        case VideoCodec::H264:
            levels = {C2Config::LEVEL_AVC_1,   C2Config::LEVEL_AVC_1B,  C2Config::LEVEL_AVC_1_1,
                      C2Config::LEVEL_AVC_1_2, C2Config::LEVEL_AVC_1_3, C2Config::LEVEL_AVC_2,
                      C2Config::LEVEL_AVC_2_1, C2Config::LEVEL_AVC_2_2, C2Config::LEVEL_AVC_3,
                      C2Config::LEVEL_AVC_3_1, C2Config::LEVEL_AVC_3_2, C2Config::LEVEL_AVC_4,
                      C2Config::LEVEL_AVC_4_1, C2Config::LEVEL_AVC_4_2, C2Config::LEVEL_AVC_5,
                      C2Config::LEVEL_AVC_5_1, C2Config::LEVEL_AVC_5_2};
            break;
        case VideoCodec::VP8:
            levels = {C2Config::LEVEL_UNUSED};
            break;
        case VideoCodec::VP9:
            levels = {C2Config::LEVEL_VP9_1,   C2Config::LEVEL_VP9_1_1, C2Config::LEVEL_VP9_2,
                      C2Config::LEVEL_VP9_2_1, C2Config::LEVEL_VP9_3,   C2Config::LEVEL_VP9_3_1,
                      C2Config::LEVEL_VP9_4,   C2Config::LEVEL_VP9_4_1, C2Config::LEVEL_VP9_5};
            break;
        case VideoCodec::HEVC:
            levels = {C2Config::LEVEL_HEVC_MAIN_1,   C2Config::LEVEL_HEVC_MAIN_2,
                      C2Config::LEVEL_HEVC_MAIN_2_1, C2Config::LEVEL_HEVC_MAIN_3,
                      C2Config::LEVEL_HEVC_MAIN_3_1, C2Config::LEVEL_HEVC_MAIN_4,
                      C2Config::LEVEL_HEVC_MAIN_4_1, C2Config::LEVEL_HEVC_MAIN_5,
                      C2Config::LEVEL_HEVC_MAIN_5_1, C2Config::LEVEL_HEVC_MAIN_5_2,
                      C2Config::LEVEL_HEVC_MAIN_6,   C2Config::LEVEL_HEVC_MAIN_6_1,
                      C2Config::LEVEL_HEVC_MAIN_6_2};
            break;
        }
    }

    uint32_t defaultLevel = caps.defaultLevel;
    if (defaultLevel == C2Config::LEVEL_UNUSED)
        defaultLevel = *std::min_element(levels.begin(), levels.end());

    switch (*mVideoCodec) {
    case VideoCodec::H264:
        inputMime = MEDIA_MIMETYPE_VIDEO_AVC;
        addParameter(DefineParam(mProfileLevel, C2_PARAMKEY_PROFILE_LEVEL)
                             .withDefault(new C2StreamProfileLevelInfo::input(
                                     0u, static_cast<C2Config::profile_t>(defaultProfile),
                                     static_cast<C2Config::level_t>(defaultLevel)))
                             .withFields({C2F(mProfileLevel, profile).oneOf(profiles),
                                          C2F(mProfileLevel, level).oneOf(levels)})
                             .withSetter(ProfileLevelSetter)
                             .build());
        break;

    case VideoCodec::VP8:
        inputMime = MEDIA_MIMETYPE_VIDEO_VP8;
        addParameter(DefineParam(mProfileLevel, C2_PARAMKEY_PROFILE_LEVEL)
                             .withConstValue(new C2StreamProfileLevelInfo::input(
                                     0u, C2Config::PROFILE_UNUSED, C2Config::LEVEL_UNUSED))
                             .build());
        break;

    case VideoCodec::VP9:
        inputMime = MEDIA_MIMETYPE_VIDEO_VP9;
        addParameter(DefineParam(mProfileLevel, C2_PARAMKEY_PROFILE_LEVEL)
                             .withDefault(new C2StreamProfileLevelInfo::input(
                                     0u, static_cast<C2Config::profile_t>(defaultProfile),
                                     static_cast<C2Config::level_t>(defaultLevel)))
                             .withFields({C2F(mProfileLevel, profile).oneOf(profiles),
                                          C2F(mProfileLevel, level).oneOf(levels)})
                             .withSetter(ProfileLevelSetter)
                             .build());
        break;

    case VideoCodec::HEVC:
        inputMime = MEDIA_MIMETYPE_VIDEO_HEVC;
        addParameter(DefineParam(mProfileLevel, C2_PARAMKEY_PROFILE_LEVEL)
                             .withDefault(new C2StreamProfileLevelInfo::input(
                                     0u, static_cast<C2Config::profile_t>(defaultProfile),
                                     static_cast<C2Config::level_t>(defaultLevel)))
                             .withFields({C2F(mProfileLevel, profile).oneOf(profiles),
                                          C2F(mProfileLevel, level).oneOf(levels)})
                             .withSetter(ProfileLevelSetter)
                             .build());
        break;
    }

    addParameter(
            DefineParam(mInputFormat, C2_PARAMKEY_INPUT_STREAM_BUFFER_TYPE)
                    .withConstValue(new C2StreamBufferTypeSetting::input(0u, C2BufferData::LINEAR))
                    .build());
    addParameter(
            DefineParam(mInputMemoryUsage, C2_PARAMKEY_INPUT_STREAM_USAGE)
                    .withConstValue(new C2StreamUsageTuning::input(
                            0u, static_cast<uint64_t>(android::hardware::graphics::common::V1_0::
                                                              BufferUsage::VIDEO_DECODER)))
                    .build());

    addParameter(DefineParam(mOutputFormat, C2_PARAMKEY_OUTPUT_STREAM_BUFFER_TYPE)
                         .withConstValue(
                                 new C2StreamBufferTypeSetting::output(0u, C2BufferData::GRAPHIC))
                         .build());
    addParameter(
            DefineParam(mOutputDelay, C2_PARAMKEY_OUTPUT_DELAY)
                    .withConstValue(new C2PortDelayTuning::output(getOutputDelay(*mVideoCodec)))
                    .build());

    // This value is set according to the relation between kNumInputBuffers = 16 and the current
    // codec2 framework implementation. Specifically, this generally limits the framework to using
    // <= 16 input buffers, although certain timing of events can result in a few more input buffers
    // being allocated but rarely used. This lets us avoid remapping v4l2 input buffers and DMA
    // buffers in the common case. We could go up to 4 here, to limit the framework to
    // simultaneously enqueuing 16 input buffers, but there doesn't seem to be much of an a
    // performance improvement from that.
    addParameter(DefineParam(mPipelineDelay, C2_PARAMKEY_PIPELINE_DELAY)
                         .withConstValue(new C2PipelineDelayTuning(3))
                         .build());

    addParameter(DefineParam(mInputMediaType, C2_PARAMKEY_INPUT_MEDIA_TYPE)
                         .withConstValue(AllocSharedString<C2PortMediaTypeSetting::input>(
                                 inputMime.c_str()))
                         .build());

    addParameter(DefineParam(mOutputMediaType, C2_PARAMKEY_OUTPUT_MEDIA_TYPE)
                         .withConstValue(AllocSharedString<C2PortMediaTypeSetting::output>(
                                 MEDIA_MIMETYPE_VIDEO_RAW))
                         .build());

    // Note(b/165826281): The check is not used at Android framework currently.
    // In order to fasten the bootup time, we use the maximum supported size instead of querying the
    // capability from the V4L2 device.
    addParameter(DefineParam(mSize, C2_PARAMKEY_PICTURE_SIZE)
                         .withDefault(new C2StreamPictureSizeInfo::output(
                                 0u, std::min(320, maxSize.width), std::min(240, maxSize.height)))
                         .withFields({
                                 C2F(mSize, width).inRange(16, maxSize.width, 16),
                                 C2F(mSize, height).inRange(16, maxSize.height, 16),
                         })
                         .withSetter(SizeSetter)
                         .build());

    addParameter(
            DefineParam(mMaxInputSize, C2_PARAMKEY_INPUT_MAX_BUFFER_SIZE)
                    .withDefault(new C2StreamMaxBufferSizeInfo::input(0u, kInputBufferSizeFor1080p))
                    .withFields({
                            C2F(mMaxInputSize, value).any(),
                    })
                    .withSetter(InputSizeSetter)
                    .calculatedAs(MaxInputBufferSizeCalculator, mSize)
                    .build());

    bool secureMode = name.find(".secure") != std::string::npos;
    const C2Allocator::id_t inputAllocators[] = {secureMode ? V4L2AllocatorId::SECURE_LINEAR
                                                            : C2AllocatorStore::DEFAULT_LINEAR};

    const C2Allocator::id_t outputAllocators[] = {C2PlatformAllocatorStore::GRALLOC};
    const C2Allocator::id_t surfaceAllocator =
            secureMode ? V4L2AllocatorId::SECURE_GRAPHIC : C2PlatformAllocatorStore::BUFFERQUEUE;
    const C2BlockPool::local_id_t outputBlockPools[] = {C2BlockPool::BASIC_GRAPHIC};

    addParameter(
            DefineParam(mInputAllocatorIds, C2_PARAMKEY_INPUT_ALLOCATORS)
                    .withConstValue(C2PortAllocatorsTuning::input::AllocShared(inputAllocators))
                    .build());

    addParameter(
            DefineParam(mOutputAllocatorIds, C2_PARAMKEY_OUTPUT_ALLOCATORS)
                    .withConstValue(C2PortAllocatorsTuning::output::AllocShared(outputAllocators))
                    .build());

    addParameter(DefineParam(mOutputSurfaceAllocatorId, C2_PARAMKEY_OUTPUT_SURFACE_ALLOCATOR)
                         .withConstValue(new C2PortSurfaceAllocatorTuning::output(surfaceAllocator))
                         .build());

    addParameter(
            DefineParam(mOutputBlockPoolIds, C2_PARAMKEY_OUTPUT_BLOCK_POOLS)
                    .withDefault(C2PortBlockPoolsTuning::output::AllocShared(outputBlockPools))
                    .withFields({C2F(mOutputBlockPoolIds, m.values[0]).any(),
                                 C2F(mOutputBlockPoolIds, m.values).inRange(0, 1)})
                    .withSetter(Setter<C2PortBlockPoolsTuning::output>::NonStrictValuesWithNoDeps)
                    .build());

    addParameter(
            DefineParam(mDefaultColorAspects, C2_PARAMKEY_DEFAULT_COLOR_ASPECTS)
                    .withDefault(new C2StreamColorAspectsTuning::output(
                            0u, C2Color::RANGE_UNSPECIFIED, C2Color::PRIMARIES_UNSPECIFIED,
                            C2Color::TRANSFER_UNSPECIFIED, C2Color::MATRIX_UNSPECIFIED))
                    .withFields(
                            {C2F(mDefaultColorAspects, range)
                                     .inRange(C2Color::RANGE_UNSPECIFIED, C2Color::RANGE_OTHER),
                             C2F(mDefaultColorAspects, primaries)
                                     .inRange(C2Color::PRIMARIES_UNSPECIFIED,
                                              C2Color::PRIMARIES_OTHER),
                             C2F(mDefaultColorAspects, transfer)
                                     .inRange(C2Color::TRANSFER_UNSPECIFIED,
                                              C2Color::TRANSFER_OTHER),
                             C2F(mDefaultColorAspects, matrix)
                                     .inRange(C2Color::MATRIX_UNSPECIFIED, C2Color::MATRIX_OTHER)})
                    .withSetter(DefaultColorAspectsSetter)
                    .build());

    addParameter(
            DefineParam(mCodedColorAspects, C2_PARAMKEY_VUI_COLOR_ASPECTS)
                    .withDefault(new C2StreamColorAspectsInfo::input(
                            0u, C2Color::RANGE_LIMITED, C2Color::PRIMARIES_UNSPECIFIED,
                            C2Color::TRANSFER_UNSPECIFIED, C2Color::MATRIX_UNSPECIFIED))
                    .withFields(
                            {C2F(mCodedColorAspects, range)
                                     .inRange(C2Color::RANGE_UNSPECIFIED, C2Color::RANGE_OTHER),
                             C2F(mCodedColorAspects, primaries)
                                     .inRange(C2Color::PRIMARIES_UNSPECIFIED,
                                              C2Color::PRIMARIES_OTHER),
                             C2F(mCodedColorAspects, transfer)
                                     .inRange(C2Color::TRANSFER_UNSPECIFIED,
                                              C2Color::TRANSFER_OTHER),
                             C2F(mCodedColorAspects, matrix)
                                     .inRange(C2Color::MATRIX_UNSPECIFIED, C2Color::MATRIX_OTHER)})
                    .withSetter(DefaultColorAspectsSetter)
                    .build());

    // At this moment v4l2_codec2 support decoding this information only for
    // unprotected H264 and both protected and unprotected HEVC.
    if ((mVideoCodec == VideoCodec::H264 && !secureMode) || mVideoCodec == VideoCodec::HEVC) {
        addParameter(DefineParam(mColorAspects, C2_PARAMKEY_COLOR_ASPECTS)
                             .withDefault(new C2StreamColorAspectsInfo::output(
                                     0u, C2Color::RANGE_UNSPECIFIED, C2Color::PRIMARIES_UNSPECIFIED,
                                     C2Color::TRANSFER_UNSPECIFIED, C2Color::MATRIX_UNSPECIFIED))
                             .withFields({C2F(mColorAspects, range)
                                                  .inRange(C2Color::RANGE_UNSPECIFIED,
                                                           C2Color::RANGE_OTHER),
                                          C2F(mColorAspects, primaries)
                                                  .inRange(C2Color::PRIMARIES_UNSPECIFIED,
                                                           C2Color::PRIMARIES_OTHER),
                                          C2F(mColorAspects, transfer)
                                                  .inRange(C2Color::TRANSFER_UNSPECIFIED,
                                                           C2Color::TRANSFER_OTHER),
                                          C2F(mColorAspects, matrix)
                                                  .inRange(C2Color::MATRIX_UNSPECIFIED,
                                                           C2Color::MATRIX_OTHER)})
                             .withSetter(MergedColorAspectsSetter, mDefaultColorAspects,
                                         mCodedColorAspects)
                             .build());
    }
}

size_t DecodeInterface::getInputBufferSize() const {
    return mMaxInputSize->value;
}

c2_status_t DecodeInterface::queryColorAspects(
        std::shared_ptr<C2StreamColorAspectsInfo::output>* targetColorAspects) {
    std::unique_ptr<C2StreamColorAspectsInfo::output> colorAspects =
            std::make_unique<C2StreamColorAspectsInfo::output>(
                    0u, C2Color::RANGE_UNSPECIFIED, C2Color::PRIMARIES_UNSPECIFIED,
                    C2Color::TRANSFER_UNSPECIFIED, C2Color::MATRIX_UNSPECIFIED);
    c2_status_t status = query({colorAspects.get()}, {}, C2_DONT_BLOCK, nullptr);
    if (status == C2_OK) {
        *targetColorAspects = std::move(colorAspects);
    }
    return status;
}

uint32_t DecodeInterface::getOutputDelay(VideoCodec codec) {
    switch (codec) {
    case VideoCodec::H264:
        // Due to frame reordering an H264 decoder might need multiple additional input frames to be
        // queued before being able to output the associated decoded buffers. We need to tell the
        // codec2 framework that it should not stop queuing new work items until the maximum number
        // of frame reordering is reached, to avoid stalling the decoder.
        return 16;
    case VideoCodec::HEVC:
        return 16;
    case VideoCodec::VP8:
        // The decoder might held a few frames as a reference for decoding. Since Android T
        // the Codec2 is more prone to timeout the component if one is not producing frames. This
        // might especially occur when those frames are held for reference and playback/decoding
        // is paused. With increased output delay we inform Codec2 not to timeout the component,
        // if number of frames in components is less then the number of maximum reference frames
        // that could be held by decoder.
        // Reference: RFC 6386 Section 3. Compressed Frame Types
        return 3;
    case VideoCodec::VP9:
        // Reference: https://www.webmproject.org/vp9/levels/
        return 8;
    }
}

}  // namespace android
