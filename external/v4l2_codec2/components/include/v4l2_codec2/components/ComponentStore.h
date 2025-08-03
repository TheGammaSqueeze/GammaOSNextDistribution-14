// Copyright 2023 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef ANDROID_V4L2_CODEC2_COMPONENTS_COMPONENT_STORE_MIXIN_H
#define ANDROID_V4L2_CODEC2_COMPONENTS_COMPONENT_STORE_MIXIN_H

#include <map>
#include <mutex>

#include <C2Component.h>
#include <C2ComponentFactory.h>
#include <android-base/thread_annotations.h>
#include <util/C2InterfaceHelper.h>

namespace android {

enum class VideoCodec;

class ComponentStore : public C2ComponentStore {
public:
    using GetFactory = std::function<std::unique_ptr<C2ComponentFactory>(
            const std::string& /* name */, std::shared_ptr<C2ReflectorHelper>)>;
    class Builder;

    virtual ~ComponentStore();

    // C2ComponentStore implementation.
    C2String getName() const override;
    c2_status_t createComponent(C2String name,
                                std::shared_ptr<C2Component>* const component) override;
    c2_status_t createInterface(C2String name,
                                std::shared_ptr<C2ComponentInterface>* const interface) override;
    std::vector<std::shared_ptr<const C2Component::Traits>> listComponents() override;
    std::shared_ptr<C2ParamReflector> getParamReflector() const override;
    c2_status_t copyBuffer(std::shared_ptr<C2GraphicBuffer> src,
                           std::shared_ptr<C2GraphicBuffer> dst) override;
    c2_status_t querySupportedParams_nb(
            std::vector<std::shared_ptr<C2ParamDescriptor>>* const params) const override;
    c2_status_t query_sm(const std::vector<C2Param*>& stackParams,
                         const std::vector<C2Param::Index>& heapParamIndices,
                         std::vector<std::unique_ptr<C2Param>>* const heapParams) const override;
    c2_status_t config_sm(const std::vector<C2Param*>& params,
                          std::vector<std::unique_ptr<C2SettingResult>>* const failures) override;
    c2_status_t querySupportedValues_sm(
            std::vector<C2FieldSupportedValuesQuery>& fields) const override;

private:
    struct Declaration {
        VideoCodec codec;
        C2Component::kind_t kind;
        GetFactory factory;
    };

    ComponentStore(C2String storeName);

    ::C2ComponentFactory* getFactory(const C2String& name);

    std::shared_ptr<const C2Component::Traits> getTraits(const C2String& name);

    C2String mStoreName;

    std::map<std::string, Declaration> mDeclarations;

    std::shared_ptr<C2ReflectorHelper> mReflector;

    std::mutex mCachedFactoriesLock;
    std::map<C2String, std::unique_ptr<::C2ComponentFactory>> mCachedFactories
            GUARDED_BY(mCachedFactoriesLock);
    std::mutex mCachedTraitsLock;
    std::map<C2String, std::shared_ptr<const C2Component::Traits>> mCachedTraits
            GUARDED_BY(mCachedTraitsLock);

    friend class Builder;
};

class ComponentStore::Builder final {
public:
    Builder(C2String storeName);
    ~Builder() = default;

    Builder& decoder(std::string name, VideoCodec codec, GetFactory factory);

    Builder& encoder(std::string name, VideoCodec codec, GetFactory factory);

    std::shared_ptr<ComponentStore> build() &&;

private:
    std::unique_ptr<ComponentStore> mStore;
};

}  // namespace android

#endif  // ANDROID_V4L2_CODEC2_COMPONENTS_COMPONENT_STORE_MIXIN_H
