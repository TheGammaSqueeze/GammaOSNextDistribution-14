// Copyright 2023 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

//#define LOG_NDEBUG 0
#define LOG_TAG "ComponentStore"

#include <v4l2_codec2/components/ComponentStore.h>

#include <stdint.h>

#include <memory>
#include <mutex>

#include <C2.h>
#include <C2Config.h>
#include <log/log.h>
#include <media/stagefright/foundation/MediaDefs.h>

#include <v4l2_codec2/common/VideoTypes.h>

namespace android {
namespace {
const uint32_t kComponentRank = 0x80;

}  // namespace

ComponentStore::ComponentStore(C2String storeName)
      : mStoreName(std::move(storeName)), mReflector(std::make_shared<C2ReflectorHelper>()) {
    ALOGV("%s()", __func__);
}

ComponentStore::~ComponentStore() {
    ALOGV("%s()", __func__);

    std::lock_guard<std::mutex> lock(mCachedFactoriesLock);
    mCachedFactories.clear();
}

C2String ComponentStore::getName() const {
    return mStoreName;
}

c2_status_t ComponentStore::createComponent(C2String name,
                                            std::shared_ptr<C2Component>* const component) {
    ALOGV("%s(%s)", __func__, name.c_str());

    const auto& decl = mDeclarations.find(name);
    if (decl == mDeclarations.end()) {
        ALOGI("%s(): Invalid component name: %s", __func__, name.c_str());
        return C2_NOT_FOUND;
    }

    auto factory = getFactory(name);
    if (factory == nullptr) return C2_CORRUPTED;

    component->reset();
    return factory->createComponent(0, component);
}

c2_status_t ComponentStore::createInterface(
        C2String name, std::shared_ptr<C2ComponentInterface>* const interface) {
    ALOGV("%s(%s)", __func__, name.c_str());

    const auto& decl = mDeclarations.find(name);
    if (decl == mDeclarations.end()) {
        ALOGI("%s(): Invalid component name: %s", __func__, name.c_str());
        return C2_NOT_FOUND;
    }

    auto factory = getFactory(name);
    if (factory == nullptr) return C2_CORRUPTED;

    interface->reset();
    return factory->createInterface(0, interface);
}

std::vector<std::shared_ptr<const C2Component::Traits>> ComponentStore::listComponents() {
    ALOGV("%s()", __func__);

    std::vector<std::shared_ptr<const C2Component::Traits>> ret;
    for (const auto& decl : mDeclarations) {
        ret.push_back(getTraits(decl.first));
    }

    return ret;
}

std::shared_ptr<C2ParamReflector> ComponentStore::getParamReflector() const {
    return mReflector;
}

c2_status_t ComponentStore::copyBuffer(std::shared_ptr<C2GraphicBuffer> /* src */,
                                       std::shared_ptr<C2GraphicBuffer> /* dst */) {
    return C2_OMITTED;
}

c2_status_t ComponentStore::querySupportedParams_nb(
        std::vector<std::shared_ptr<C2ParamDescriptor>>* const /* params */) const {
    return C2_OK;
}

c2_status_t ComponentStore::query_sm(
        const std::vector<C2Param*>& stackParams,
        const std::vector<C2Param::Index>& heapParamIndices,
        std::vector<std::unique_ptr<C2Param>>* const /* heapParams */) const {
    // There are no supported config params.
    return stackParams.empty() && heapParamIndices.empty() ? C2_OK : C2_BAD_INDEX;
}

c2_status_t ComponentStore::config_sm(
        const std::vector<C2Param*>& params,
        std::vector<std::unique_ptr<C2SettingResult>>* const /* failures */) {
    // There are no supported config params.
    return params.empty() ? C2_OK : C2_BAD_INDEX;
}

c2_status_t ComponentStore::querySupportedValues_sm(
        std::vector<C2FieldSupportedValuesQuery>& fields) const {
    // There are no supported config params.
    return fields.empty() ? C2_OK : C2_BAD_INDEX;
}

::C2ComponentFactory* ComponentStore::getFactory(const C2String& name) {
    ALOGV("%s(%s)", __func__, name.c_str());
    ALOG_ASSERT(V4L2ComponentName::isValid(name.c_str()));

    std::lock_guard<std::mutex> lock(mCachedFactoriesLock);
    const auto it = mCachedFactories.find(name);
    if (it != mCachedFactories.end()) return it->second.get();

    const auto& decl = mDeclarations.find(name);
    if (decl == mDeclarations.end()) {
        ALOGI("%s(): Invalid component name: %s", __func__, name.c_str());
        return nullptr;
    }

    std::unique_ptr<::C2ComponentFactory> factory = decl->second.factory(name, mReflector);
    if (factory == nullptr) {
        ALOGE("Failed to create factory for %s", name.c_str());
        return nullptr;
    }

    auto ret = factory.get();
    mCachedFactories.emplace(name, std::move(factory));
    return ret;
}

std::shared_ptr<const C2Component::Traits> ComponentStore::getTraits(const C2String& name) {
    ALOGV("%s(%s)", __func__, name.c_str());

    const auto& iter = mDeclarations.find(name);
    if (iter == mDeclarations.end()) {
        ALOGE("Invalid component name: %s", name.c_str());
        return nullptr;
    }

    const Declaration& decl = iter->second;

    std::lock_guard<std::mutex> lock(mCachedTraitsLock);
    auto it = mCachedTraits.find(name);
    if (it != mCachedTraits.end()) return it->second;

    auto traits = std::make_shared<C2Component::Traits>();
    traits->name = name;
    traits->domain = C2Component::DOMAIN_VIDEO;
    traits->rank = kComponentRank;
    traits->kind = decl.kind;

    switch (decl.codec) {
    case VideoCodec::H264:
        traits->mediaType = MEDIA_MIMETYPE_VIDEO_AVC;
        break;
    case VideoCodec::VP8:
        traits->mediaType = MEDIA_MIMETYPE_VIDEO_VP8;
        break;
    case VideoCodec::VP9:
        traits->mediaType = MEDIA_MIMETYPE_VIDEO_VP9;
        break;
    case VideoCodec::HEVC:
        traits->mediaType = MEDIA_MIMETYPE_VIDEO_HEVC;
        break;
    }

    mCachedTraits.emplace(name, traits);
    return traits;
}

ComponentStore::Builder::Builder(C2String storeName)
      : mStore(new ComponentStore(std::move(storeName))) {}

ComponentStore::Builder& ComponentStore::Builder::decoder(std::string name, VideoCodec codec,
                                                          GetFactory factory) {
    mStore->mDeclarations[name] = Declaration{codec, C2Component::KIND_DECODER, std::move(factory)};
    return *this;
}

ComponentStore::Builder& ComponentStore::Builder::encoder(std::string name, VideoCodec codec,
                                                          GetFactory factory) {
    mStore->mDeclarations[name] = Declaration{codec, C2Component::KIND_ENCODER, std::move(factory)};
    return *this;
}

std::shared_ptr<ComponentStore> ComponentStore::Builder::build() && {
    return std::shared_ptr<ComponentStore>(std::move(mStore));
}
}  // namespace android
