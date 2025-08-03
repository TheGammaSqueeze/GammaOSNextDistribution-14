/*
 * Copyright (C) 2024 The Android Open Source Project
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

#include <FrameDecoder.h>
#include <fuzzer/FuzzedDataProvider.h>
#include <media/IMediaSource.h>
#include <media/stagefright/MetaData.h>
#include <media/stagefright/foundation/AString.h>
#include "FrameDecoderHelpers.h"
#include "IMediaSourceFuzzImpl.h"

namespace android {

#define MAX_MEDIA_BUFFER_SIZE 2048

// Fuzzer entry point.
extern "C" int LLVMFuzzerTestOneInput(const uint8_t *data, size_t size) {
    // Init our wrapper
    FuzzedDataProvider fdp(data, size);

    std::string name = fdp.ConsumeRandomLengthString(fdp.remaining_bytes());
    AString componentName(name.c_str());
    sp<MetaData> trackMeta = generateMetaData(&fdp);
    sp<IMediaSource> source = new IMediaSourceFuzzImpl(&fdp, MAX_MEDIA_BUFFER_SIZE);

    // Image or video Decoder?
    sp<FrameDecoder> decoder;
    bool isVideoDecoder = fdp.ConsumeBool();
    if (isVideoDecoder) {
        decoder = new VideoFrameDecoder(componentName, trackMeta, source);
    } else {
        decoder = new MediaImageDecoder(componentName, trackMeta, source);
    }

    while (fdp.remaining_bytes()) {
        if (fdp.ConsumeBool()) {
            int64_t frameTimeUs = fdp.ConsumeIntegral<int64_t>();
            int option = fdp.ConsumeIntegral<int>();
            int colorFormat = fdp.ConsumeIntegral<int>();
            decoder->init(frameTimeUs, option, colorFormat);
            decoder->extractFrame();
        } else {
            FrameRect rect;
            rect.left = fdp.ConsumeIntegral<int32_t>();
            rect.top = fdp.ConsumeIntegral<int32_t>();
            rect.right = fdp.ConsumeIntegral<int32_t>();
            rect.bottom = fdp.ConsumeIntegral<int32_t>();
            int64_t frameTimeUs = fdp.ConsumeIntegral<int64_t>();
            int option = fdp.ConsumeIntegral<int>();
            int colorFormat = fdp.ConsumeIntegral<int>();
            decoder->init(frameTimeUs, option, colorFormat);
            decoder->extractFrame(&rect);
        }
    }

    generated_mime_types.clear();

    return 0;
}
}  // namespace android