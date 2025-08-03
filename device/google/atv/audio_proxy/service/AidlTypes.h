// Copyright (C) 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#pragma once

#include <aidl/device/google/atv/audio_proxy/AudioChannelMask.h>
#include <aidl/device/google/atv/audio_proxy/AudioConfig.h>
#include <aidl/device/google/atv/audio_proxy/AudioDrain.h>
#include <aidl/device/google/atv/audio_proxy/AudioFormat.h>
#include <aidl/device/google/atv/audio_proxy/MmapBufferInfo.h>
#include <aidl/device/google/atv/audio_proxy/PresentationPosition.h>
#include <aidl/device/google/atv/audio_proxy/WriteStatus.h>

namespace audio_proxy::service {

// Short name for aidl types.
using AidlAudioChannelMask =
    aidl::device::google::atv::audio_proxy::AudioChannelMask;
using AidlAudioConfig = aidl::device::google::atv::audio_proxy::AudioConfig;
using AidlAudioDrain = aidl::device::google::atv::audio_proxy::AudioDrain;
using AidlAudioFormat = aidl::device::google::atv::audio_proxy::AudioFormat;
using AidlMmapBufferInfo =
    aidl::device::google::atv::audio_proxy::MmapBufferInfo;
using AidlPresentationPosition =
    aidl::device::google::atv::audio_proxy::PresentationPosition;
using AidlWriteStatus = aidl::device::google::atv::audio_proxy::WriteStatus;

}  // namespace audio_proxy::service