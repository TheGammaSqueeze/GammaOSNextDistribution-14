// Copyright 2019 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
#pragma once

#include "host-common/GoldfishMediaDefs.h"
#include "host-common/MediaCodec.h"
#include "host-common/MediaVpxDecoderPlugin.h"
#include "host-common/VpxPingInfoParser.h"

#include <inttypes.h>
#include <stddef.h>
#include <memory>
#include <mutex>
#include <unordered_map>

namespace android {
namespace emulation {

class MediaVpxDecoder : public MediaCodec {
public:
    using InitContextParam = VpxPingInfoParser::InitContextParam;
    using DecodeFrameParam = VpxPingInfoParser::DecodeFrameParam;
    using GetImageParam = VpxPingInfoParser::GetImageParam;

    static MediaVpxDecoder* create();
    virtual ~MediaVpxDecoder() = default;

public:
    virtual void save(base::Stream* stream) const = 0;
    virtual bool load(base::Stream* stream) = 0;

protected:
    MediaVpxDecoder() = default;
};

}  // namespace emulation
}  // namespace android
