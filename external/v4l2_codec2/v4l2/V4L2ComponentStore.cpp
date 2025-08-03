// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

//#define LOG_NDEBUG 0
#define LOG_TAG "V4L2ComponentStore"

#include <v4l2_codec2/v4l2/V4L2ComponentStore.h>

#include <stdint.h>

#include <memory>
#include <mutex>

#include <C2.h>
#include <C2Config.h>
#include <log/log.h>
#include <media/stagefright/foundation/MediaDefs.h>

#include <v4l2_codec2/components/ComponentStore.h>
#include <v4l2_codec2/v4l2/V4L2ComponentCommon.h>
#include <v4l2_codec2/v4l2/V4L2ComponentFactory.h>

namespace android {

// static
std::shared_ptr<C2ComponentStore> V4L2ComponentStore::Create() {
    ALOGV("%s()", __func__);

    static std::mutex mutex;
    static std::weak_ptr<C2ComponentStore> platformStore;

    std::lock_guard<std::mutex> lock(mutex);
    std::shared_ptr<C2ComponentStore> store = platformStore.lock();
    if (store != nullptr) return store;

    auto builder = ComponentStore::Builder("android.componentStore.v4l2");

    builder.encoder(V4L2ComponentName::kH264Encoder, VideoCodec::H264,
                    &V4L2ComponentFactory::create);
    builder.encoder(V4L2ComponentName::kVP8Encoder, VideoCodec::VP8, &V4L2ComponentFactory::create);
    builder.encoder(V4L2ComponentName::kVP9Encoder, VideoCodec::VP9, &V4L2ComponentFactory::create);

    builder.decoder(V4L2ComponentName::kH264Decoder, VideoCodec::H264,
                    &V4L2ComponentFactory::create);
    builder.decoder(V4L2ComponentName::kVP8Decoder, VideoCodec::VP8, &V4L2ComponentFactory::create);
    builder.decoder(V4L2ComponentName::kVP9Decoder, VideoCodec::VP9, &V4L2ComponentFactory::create);
    builder.decoder(V4L2ComponentName::kHEVCDecoder, VideoCodec::HEVC,
                    &V4L2ComponentFactory::create);

    builder.decoder(V4L2ComponentName::kH264SecureDecoder, VideoCodec::H264,
                    &V4L2ComponentFactory::create);
    builder.decoder(V4L2ComponentName::kVP8SecureDecoder, VideoCodec::VP8,
                    &V4L2ComponentFactory::create);
    builder.decoder(V4L2ComponentName::kVP9SecureDecoder, VideoCodec::VP9,
                    &V4L2ComponentFactory::create);
    builder.decoder(V4L2ComponentName::kHEVCSecureDecoder, VideoCodec::HEVC,
                    &V4L2ComponentFactory::create);

    store = std::shared_ptr<C2ComponentStore>(std::move(builder).build());
    platformStore = store;
    return store;
}

}  // namespace android
