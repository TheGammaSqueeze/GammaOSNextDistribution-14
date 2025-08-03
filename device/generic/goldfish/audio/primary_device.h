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
#include <mutex>
#include <unordered_map>
#include <unordered_set>
#include PATH(android/hardware/audio/FILE_VERSION/IPrimaryDevice.h)

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
using ::android::hardware::audio::CPP_VERSION::IPrimaryDevice;
using ::android::hardware::audio::CPP_VERSION::IStreamOut;

struct StreamIn;
struct StreamOut;

struct Device : public IDevice {
    Device();
    Return<Result> initCheck() override;
    Return<Result> setMasterVolume(float volume) override;
    Return<void> getMasterVolume(getMasterVolume_cb _hidl_cb) override;
    Return<Result> setMicMute(bool mute) override;
    Return<void> getMicMute(getMicMute_cb _hidl_cb) override;
    Return<Result> setMasterMute(bool mute) override;
    Return<void> getMasterMute(getMasterMute_cb _hidl_cb) override;
    Return<void> getInputBufferSize(const AudioConfig& config,
                                    getInputBufferSize_cb _hidl_cb) override;
    Return<void> openOutputStream(int32_t ioHandle,
                                  const DeviceAddress& device,
                                  const AudioConfig& config,
                                  const hidl_vec<AudioInOutFlag>& flags,
                                  const SourceMetadata& sourceMetadata,
                                  openOutputStream_cb _hidl_cb) override;
    Return<void> openInputStream(int32_t ioHandle,
                                 const DeviceAddress& device,
                                 const AudioConfig& config,
                                 const hidl_vec<AudioInOutFlag>& flags,
                                 const SinkMetadata& sinkMetadata,
                                 openInputStream_cb _hidl_cb) override;
    Return<bool> supportsAudioPatches() override;
    Return<void> createAudioPatch(const hidl_vec<AudioPortConfig>& sources,
                                  const hidl_vec<AudioPortConfig>& sinks,
                                  createAudioPatch_cb _hidl_cb) override;
    Return<void> updateAudioPatch(AudioPatchHandle previousPatch,
                                  const hidl_vec<AudioPortConfig>& sources,
                                  const hidl_vec<AudioPortConfig>& sinks,
                                  updateAudioPatch_cb _hidl_cb) override;
    Return<Result> releaseAudioPatch(AudioPatchHandle patch) override;
    Return<void> getAudioPort(const AudioPort& port, getAudioPort_cb _hidl_cb) override;
    Return<Result> setAudioPortConfig(const AudioPortConfig& config) override;
    Return<Result> setScreenState(bool turnedOn) override;
    Return<void> getHwAvSync(getHwAvSync_cb _hidl_cb) override;
    Return<void> getParameters(const hidl_vec<ParameterValue>& context,
                               const hidl_vec<hidl_string>& keys,
                               getParameters_cb _hidl_cb) override;
    Return<Result> setParameters(const hidl_vec<ParameterValue>& context,
                                 const hidl_vec<ParameterValue>& parameters) override;
    Return<void> getMicrophones(getMicrophones_cb _hidl_cb) override;
    Return<Result> setConnectedState(const DeviceAddress& address, bool connected) override;
    Return<Result> close() override;
    Return<Result> addDeviceEffect(AudioPortHandle device, uint64_t effectId) override;
    Return<Result> removeDeviceEffect(AudioPortHandle device, uint64_t effectId) override;

#if MAJOR_VERSION == 7 && MINOR_VERSION == 1
    Return<void> openOutputStream_7_1(int32_t ioHandle, const DeviceAddress& device,
                                      const AudioConfig& config,
                                      const hidl_vec<AudioInOutFlag>& flags,
                                      const SourceMetadata& sourceMetadata,
                                      openOutputStream_7_1_cb _hidl_cb) override;
    Return<Result> setConnectedState_7_1(const AudioPort& devicePort,
                                         bool connected) override;
#endif

  private:
    friend StreamIn;
    friend StreamOut;

    std::tuple<Result, sp<IStreamOut>, AudioConfig> openOutputStreamImpl(
            int32_t ioHandle, const DeviceAddress& device,
            const AudioConfig& config, const hidl_vec<AudioInOutFlag>& flags,
            const SourceMetadata& sourceMetadata);
    std::tuple<Result, sp<IStreamIn>, AudioConfig> openInputStreamImpl(
            int32_t ioHandle, const DeviceAddress& device,
            const AudioConfig& config, const hidl_vec<AudioInOutFlag>& flags,
            const SinkMetadata& sinkMetadata);
    void unrefDevice(StreamIn *);
    void unrefDevice(StreamOut *);
    void updateOutputStreamVolume(float masterVolume) const;
    void updateInputStreamMicMute(bool micMute) const;

    struct AudioPatch {
        AudioPortConfig source;
        AudioPortConfig sink;
    };

    AudioPatchHandle    mNextAudioPatchHandle = 0;
    std::unordered_map<AudioPatchHandle, AudioPatch> mAudioPatches;

    std::unordered_set<StreamIn *>  mInputStreams;  // requires mMutex
    std::unordered_set<StreamOut *> mOutputStreams; // requires mMutex
    mutable std::mutex mMutex;

    float  mMasterVolume = 1.0f;
    bool   mMasterMute = false;
    bool   mMicMute = false;
};

struct PrimaryDevice : public IPrimaryDevice {
    PrimaryDevice();

    // Implementation of IDevice.
    Return<Result> initCheck() override;
    Return<Result> setMasterVolume(float volume) override;
    Return<void> getMasterVolume(getMasterVolume_cb _hidl_cb) override;
    Return<Result> setMicMute(bool mute) override;
    Return<void> getMicMute(getMicMute_cb _hidl_cb) override;
    Return<Result> setMasterMute(bool mute) override;
    Return<void> getMasterMute(getMasterMute_cb _hidl_cb) override;
    Return<void> getInputBufferSize(const AudioConfig& config,
                                    getInputBufferSize_cb _hidl_cb) override;
    Return<void> openOutputStream(int32_t ioHandle,
                                  const DeviceAddress& device,
                                  const AudioConfig& config,
                                  const hidl_vec<AudioInOutFlag>& flags,
                                  const SourceMetadata& sourceMetadata,
                                  openOutputStream_cb _hidl_cb) override;
    Return<void> openInputStream(int32_t ioHandle,
                                 const DeviceAddress& device,
                                 const AudioConfig& config,
                                 const hidl_vec<AudioInOutFlag>& flags,
                                 const SinkMetadata& sinkMetadata,
                                 openInputStream_cb _hidl_cb) override;
    Return<bool> supportsAudioPatches() override;
    Return<void> createAudioPatch(const hidl_vec<AudioPortConfig>& sources,
                                  const hidl_vec<AudioPortConfig>& sinks,
                                  createAudioPatch_cb _hidl_cb) override;
    Return<void> updateAudioPatch(AudioPatchHandle previousPatch,
                                  const hidl_vec<AudioPortConfig>& sources,
                                  const hidl_vec<AudioPortConfig>& sinks,
                                  updateAudioPatch_cb _hidl_cb) override;
    Return<Result> releaseAudioPatch(AudioPatchHandle patch) override;
    Return<void> getAudioPort(const AudioPort& port, getAudioPort_cb _hidl_cb) override;
    Return<Result> setAudioPortConfig(const AudioPortConfig& config) override;
    Return<Result> setScreenState(bool turnedOn) override;
    Return<void> getHwAvSync(getHwAvSync_cb _hidl_cb) override;
    Return<void> getParameters(const hidl_vec<ParameterValue>& context,
                               const hidl_vec<hidl_string>& keys,
                               getParameters_cb _hidl_cb) override;
    Return<Result> setParameters(const hidl_vec<ParameterValue>& context,
                                 const hidl_vec<ParameterValue>& parameters) override;
    Return<void> getMicrophones(getMicrophones_cb _hidl_cb) override;
    Return<Result> setConnectedState(const DeviceAddress& address, bool connected) override;
    Return<Result> close() override;
    Return<Result> addDeviceEffect(AudioPortHandle device, uint64_t effectId) override;
    Return<Result> removeDeviceEffect(AudioPortHandle device, uint64_t effectId) override;

    // Implementation of IPrimaryDevice.
    Return<Result> setVoiceVolume(float volume) override;
    Return<Result> setMode(AudioMode mode) override;
    Return<Result> setBtScoHeadsetDebugName(const hidl_string& name) override;
    Return<void> getBtScoNrecEnabled(getBtScoNrecEnabled_cb _hidl_cb) override;
    Return<Result> setBtScoNrecEnabled(bool enabled) override;
    Return<void> getBtScoWidebandEnabled(getBtScoWidebandEnabled_cb _hidl_cb) override;
    Return<Result> setBtScoWidebandEnabled(bool enabled) override;
    Return<void> getTtyMode(getTtyMode_cb _hidl_cb) override;
    Return<Result> setTtyMode(IPrimaryDevice::TtyMode mode) override;
    Return<void> getHacEnabled(getHacEnabled_cb _hidl_cb) override;
    Return<Result> setHacEnabled(bool enabled) override;
    Return<void> getBtHfpEnabled(getBtHfpEnabled_cb _hidl_cb) override;
    Return<Result> setBtHfpEnabled(bool enabled) override;
    Return<Result> setBtHfpSampleRate(uint32_t sampleRateHz) override;
    Return<Result> setBtHfpVolume(float volume) override;
    Return<Result> updateRotation(IPrimaryDevice::Rotation rotation) override;

#if MAJOR_VERSION == 7 && MINOR_VERSION == 1
    Return<sp<::android::hardware::audio::V7_1::IDevice>> getDevice() override {
        return mDevice;
    }
#endif

private:
    sp<Device> mDevice;
};

}  // namespace implementation
}  // namespace CPP_VERSION
}  // namespace audio
}  // namespace hardware
}  // namespace android
