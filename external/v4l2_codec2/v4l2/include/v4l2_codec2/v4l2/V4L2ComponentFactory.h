// Copyright 2021 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef ANDROID_V4L2_CODEC2_V4L2_V4L2_COMPONENT_FACTORY_H
#define ANDROID_V4L2_CODEC2_V4L2_V4L2_COMPONENT_FACTORY_H

#include <memory>
#include <string>

#include <C2ComponentFactory.h>
#include <util/C2InterfaceHelper.h>
#include <v4l2_codec2/common/Common.h>

namespace android {

struct SupportedCapabilities;
class DecodeInterface;
class EncodeInterface;

class V4L2ComponentFactory : public C2ComponentFactory {
public:
    static std::unique_ptr<V4L2ComponentFactory> create(
            const std::string& componentName, std::shared_ptr<C2ReflectorHelper> reflector);
    V4L2ComponentFactory(const std::string& componentName, bool isEncoder,
                         std::shared_ptr<C2ReflectorHelper> reflector);
    ~V4L2ComponentFactory() override = default;

    // Implementation of C2ComponentFactory.
    c2_status_t createComponent(c2_node_id_t id, std::shared_ptr<C2Component>* const component,
                                ComponentDeleter deleter) override;
    c2_status_t createInterface(c2_node_id_t id,
                                std::shared_ptr<C2ComponentInterface>* const interface,
                                InterfaceDeleter deleter) override;

private:
    c2_status_t createEncodeInterface(std::shared_ptr<EncodeInterface>* intfImpl);
    c2_status_t createDecodeInterface(std::shared_ptr<DecodeInterface>* intfImpl);

    const std::string mComponentName;
    const bool mIsEncoder;
    std::shared_ptr<C2ReflectorHelper> mReflector;
    std::unique_ptr<SupportedCapabilities> mCapabilites;
};

}  // namespace android

#endif  // ANDROID_V4L2_CODEC2_V4L2_V4L2_COMPONENT_FACTORY_H
