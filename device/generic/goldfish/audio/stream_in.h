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

#pragma once
#include PATH(android/hardware/audio/CORE_TYPES_FILE_VERSION/IStreamIn.h)
#include PATH(android/hardware/audio/FILE_VERSION/IDevice.h)
#include "stream_common.h"
#include "io_thread.h"
#include "primary_device.h"

namespace android {
namespace hardware {
namespace audio {
namespace CPP_VERSION {
namespace implementation {

using ::android::sp;
using ::android::hardware::hidl_bitfield;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using namespace ::android::hardware::audio::common::COMMON_TYPES_CPP_VERSION;
using namespace ::android::hardware::audio::CORE_TYPES_CPP_VERSION;
using ::android::hardware::audio::CORE_TYPES_CPP_VERSION::IStreamIn;

struct StreamIn : public IStreamIn {
    StreamIn(sp<Device> dev,
             int32_t ioHandle,
             const DeviceAddress& device,
             const AudioConfig& config,
             hidl_vec<AudioInOutFlag> flags,
             const SinkMetadata& sinkMetadata);
    ~StreamIn();

    // IStream
    Return<uint64_t> getFrameSize() override;
    Return<uint64_t> getFrameCount() override;
    Return<uint64_t> getBufferSize() override;
    Return<void> getSupportedProfiles(getSupportedProfiles_cb _hidl_cb) override;
    Return<void> getAudioProperties(getAudioProperties_cb _hidl_cb) override;
    Return<Result> setAudioProperties(const AudioConfigBaseOptional& config) override;
    Return<Result> addEffect(uint64_t effectId) override;
    Return<Result> removeEffect(uint64_t effectId) override;
    Return<Result> standby() override;
    Return<void> getDevices(getDevices_cb _hidl_cb) override;
    Return<Result> setDevices(const hidl_vec<DeviceAddress>& devices) override;
    Return<Result> setHwAvSync(uint32_t hwAvSync) override;
    Return<void> getParameters(const hidl_vec<ParameterValue>& context,
                               const hidl_vec<hidl_string>& keys,
                               getParameters_cb _hidl_cb) override;
    Return<Result> setParameters(const hidl_vec<ParameterValue>& context,
                                 const hidl_vec<ParameterValue>& parameters) override;
    Return<Result> start() override;
    Return<Result> stop() override;
    Return<void> createMmapBuffer(int32_t minSizeFrames, createMmapBuffer_cb _hidl_cb) override;
    Return<void> getMmapPosition(getMmapPosition_cb _hidl_cb) override;
    Return<Result> close() override;

    // IStreamIn
    Return<void> getAudioSource(getAudioSource_cb _hidl_cb) override;
    Return<Result> setGain(float gain) override;
    Return<Result> updateSinkMetadata(const SinkMetadata& sinkMetadata) override;
    Return<void> prepareForReading(uint32_t frameSize, uint32_t framesCount,
                                   prepareForReading_cb _hidl_cb) override;
    Return<uint32_t> getInputFramesLost() override;
    Return<void> getCapturePosition(getCapturePosition_cb _hidl_cb) override;
    Return<void> getActiveMicrophones(getActiveMicrophones_cb _hidl_cb) override;
    Return<Result> setMicrophoneDirection(MicrophoneDirection direction) override;
    Return<Result> setMicrophoneFieldDimension(float zoom) override;

    const DeviceAddress &getDeviceAddress() const { return mCommon.m_device; }
    const AudioConfig &getAudioConfig() const { return mCommon.m_config; }
    const hidl_vec<AudioInOutFlag> &getAudioOutputFlags() const { return mCommon.m_flags; }

    uint64_t &getFrameCounter() { return mFrames; }
    void setMicMute(bool mute);
    void addInputFramesLost(size_t n) { mInputFramesLost += n; }
    float getEffectiveVolume() const { return mEffectiveVolume; }

    static bool validateDeviceAddress(const DeviceAddress& device);
    static bool validateFlags(const hidl_vec<AudioInOutFlag>& flags);
    static bool validateSinkMetadata(const SinkMetadata& sinkMetadata);

private:
    Result closeImpl(bool fromDctor);

    sp<Device> mDev;
    const StreamCommon mCommon;
    const SinkMetadata mSinkMetadata;
    std::unique_ptr<IOThread> mReadThread;

    // The count is not reset to zero when output enters standby.
    uint64_t mFrames = 0;

    std::atomic<uint32_t> mInputFramesLost = 0;
    std::atomic<float> mEffectiveVolume = 1.0f;
};

}  // namespace implementation
}  // namespace CPP_VERSION
}  // namespace audio
}  // namespace hardware
}  // namespace android
