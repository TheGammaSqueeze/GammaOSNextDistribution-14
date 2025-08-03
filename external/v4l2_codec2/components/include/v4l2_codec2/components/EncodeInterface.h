// Copyright 2023 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef ANDROID_V4L2_CODEC2_COMPONENTS_ENCODE_INTERFACE_H
#define ANDROID_V4L2_CODEC2_COMPONENTS_ENCODE_INTERFACE_H

#include <optional>
#include <vector>

#include <C2.h>
#include <C2Buffer.h>
#include <C2Config.h>
#include <ui/Size.h>
#include <util/C2InterfaceHelper.h>

#include <v4l2_codec2/common/Common.h>
#include <v4l2_codec2/common/EncodeHelpers.h>

namespace media {
class V4L2Device;
};

namespace android {

// Codec 2.0 interface describing the V4L2EncodeComponent. This interface is used by the codec 2.0
// framework to query the component's capabilities and request configuration changes.
class EncodeInterface : public C2InterfaceHelper {
public:
    EncodeInterface(const C2String& name, std::shared_ptr<C2ReflectorHelper> helper,
                    const SupportedCapabilities& caps);

    // Interfaces for the V4L2EncodeInterface
    // Note: these getters are not thread-safe. For dynamic parameters, component should use
    // formal query API for C2ComponentInterface instead.
    c2_status_t status() const { return mInitStatus; }
    const char* getOutputMediaType() const { return mOutputMediaType->m.value; }
    C2Config::profile_t getOutputProfile() const { return mProfileLevel->profile; }
    C2Config::level_t getOutputLevel() const { return mProfileLevel->level; }
    const ui::Size getInputVisibleSize() const {
        return ui::Size(mInputVisibleSize->width, mInputVisibleSize->height);
    }
    C2BlockPool::local_id_t getBlockPoolId() const { return mOutputBlockPoolIds->m.values[0]; }

    // Get sync key-frame period in frames.
    uint32_t getKeyFramePeriod() const;
    // Get the requested bitrate mode.
    C2Config::bitrate_mode_t getBitrateMode() const { return mBitrateMode->value; }
    // Get the requested bitrate.
    uint32_t getBitrate() const { return mBitrate->value; }
    // Get the requested framerate.
    float getFramerate() const { return mFrameRate->value; }

    // Request changing the framerate to the specified value.
    void setFramerate(uint32_t framerate) { mFrameRate->value = framerate; }

protected:
    void Initialize(const C2String& name, const SupportedCapabilities& caps);

    // Configurable parameter setters.
    static C2R H264ProfileLevelSetter(bool mayBlock, C2P<C2StreamProfileLevelInfo::output>& info,
                                      const C2P<C2StreamPictureSizeInfo::input>& videosize,
                                      const C2P<C2StreamFrameRateInfo::output>& frameRate,
                                      const C2P<C2StreamBitrateInfo::output>& bitrate);
    static C2R VP9ProfileLevelSetter(bool mayBlock, C2P<C2StreamProfileLevelInfo::output>& info,
                                     const C2P<C2StreamPictureSizeInfo::input>& videosize,
                                     const C2P<C2StreamFrameRateInfo::output>& frameRate,
                                     const C2P<C2StreamBitrateInfo::output>& bitrate);

    static C2R SizeSetter(bool mayBlock, C2P<C2StreamPictureSizeInfo::input>& videoSize);

    static C2R IntraRefreshPeriodSetter(bool mayBlock,
                                        C2P<C2StreamIntraRefreshTuning::output>& period);

    // Recorded lowest configured level
    // Is static for the need to use H264ProfileLevelSetter as a setter
    static C2Config::level_t lowestConfigLevel;

    // Constant parameters

    // The kind of the component; should be C2Component::KIND_ENCODER.
    std::shared_ptr<C2ComponentKindSetting> mKind;
    // The input format kind; should be C2FormatVideo.
    std::shared_ptr<C2StreamBufferTypeSetting::input> mInputFormat;
    // The memory usage flag of input buffer; should be BufferUsage::VIDEO_ENCODER.
    std::shared_ptr<C2StreamUsageTuning::input> mInputMemoryUsage;
    // The output format kind; should be C2FormatCompressed.
    std::shared_ptr<C2StreamBufferTypeSetting::output> mOutputFormat;
    // The MIME type of input port; should be MEDIA_MIMETYPE_VIDEO_RAW.
    std::shared_ptr<C2PortMediaTypeSetting::input> mInputMediaType;
    // The MIME type of output port.
    std::shared_ptr<C2PortMediaTypeSetting::output> mOutputMediaType;

    // The suggested usage of input buffer allocator ID.
    std::shared_ptr<C2PortAllocatorsTuning::input> mInputAllocatorIds;
    // The suggested usage of output buffer allocator ID.
    std::shared_ptr<C2PortAllocatorsTuning::output> mOutputAllocatorIds;

    // Initialization parameters

    // The visible size for input raw video.
    std::shared_ptr<C2StreamPictureSizeInfo::input> mInputVisibleSize;
    // The output codec profile and level.
    std::shared_ptr<C2StreamProfileLevelInfo::output> mProfileLevel;
    // The expected period for key frames in microseconds.
    std::shared_ptr<C2StreamSyncFrameIntervalTuning::output> mKeyFramePeriodUs;
    // Component uses this ID to fetch corresponding output block pool from platform.
    std::shared_ptr<C2PortBlockPoolsTuning::output> mOutputBlockPoolIds;

    // Dynamic parameters

    // The requested bitrate of the encoded output stream, in bits per second.
    std::shared_ptr<C2StreamBitrateInfo::output> mBitrate;
    // The requested bitrate mode.
    std::shared_ptr<C2StreamBitrateModeTuning::output> mBitrateMode;
    // The requested framerate, in frames per second.
    std::shared_ptr<C2StreamFrameRateInfo::output> mFrameRate;
    // The switch-type parameter that will be set to true while client requests keyframe. It
    // will be reset once encoder gets the request.
    std::shared_ptr<C2StreamRequestSyncFrameTuning::output> mRequestKeyFrame;
    // The intra-frame refresh period. This is unused for the component now.
    // TODO: adapt intra refresh period to encoder.
    std::shared_ptr<C2StreamIntraRefreshTuning::output> mIntraRefreshPeriod;

    c2_status_t mInitStatus = C2_NO_INIT;
};

}  // namespace android

#endif  // ANDROID_V4L2_CODEC2_COMPONENTS_ENCODE_INTERFACE_H
