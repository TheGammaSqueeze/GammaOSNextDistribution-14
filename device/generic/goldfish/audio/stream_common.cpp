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

#include <log/log.h>
#include "stream_common.h"
#include "util.h"
#include "debug.h"

namespace android {
namespace hardware {
namespace audio {
namespace CPP_VERSION {
namespace implementation {

using ::android::hardware::Void;

StreamCommon::StreamCommon(int32_t ioHandle,
                           const DeviceAddress& device,
                           const AudioConfig& config,
                           hidl_vec<AudioInOutFlag> flags)
        : m_ioHandle(ioHandle)
        , m_device(device)
        , m_config(config)
        , m_flags(std::move(flags))
{}

uint64_t StreamCommon::getFrameSize() const {
    return util::countChannels(m_config.base.channelMask)
           * util::getBytesPerSample(m_config.base.format);
}

uint64_t StreamCommon::getFrameCount() const {
    return m_config.frameCount;
}

uint64_t StreamCommon::getBufferSize() const {
    return getFrameSize() * getFrameCount();
}

uint32_t StreamCommon::getSampleRate() const {
    return m_config.base.sampleRateHz;
}

void StreamCommon::getSupportedProfiles(const IStream::getSupportedProfiles_cb &_hidl_cb) const {
    AudioProfile profile;

    profile.format = m_config.base.format;
    profile.sampleRates = { m_config.base.sampleRateHz };
    profile.channelMasks = { m_config.base.channelMask };

    _hidl_cb(Result::OK, {profile});
}

AudioFormat StreamCommon::getFormat() const {
    return m_config.base.format;
}

Result StreamCommon::setFormat(AudioFormat format) const {
    (void)format;
    return FAILURE(Result::NOT_SUPPORTED);
}

void StreamCommon::getAudioProperties(const IStream::getAudioProperties_cb &_hidl_cb) const {
    _hidl_cb(Result::OK, m_config.base);
}

void StreamCommon::getDevices(const IStream::getDevices_cb &_hidl_cb) const {
    _hidl_cb(Result::OK, {m_device});
}

Result StreamCommon::setDevices(const hidl_vec<DeviceAddress>& devices) const {
    (void)devices;
    return FAILURE(Result::NOT_SUPPORTED);
}

}  // namespace implementation
}  // namespace CPP_VERSION
}  // namespace audio
}  // namespace hardware
}  // namespace android
