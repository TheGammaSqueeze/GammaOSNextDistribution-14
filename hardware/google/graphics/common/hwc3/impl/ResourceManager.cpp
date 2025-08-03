/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include <aidlcommonsupport/NativeHandle.h>

#include "ResourceManager.h"
#include "TranslateHwcAidl.h"

using android::hardware::graphics::composer::V2_1::Display;
using android::hardware::graphics::composer::V2_1::Error;
using android::hardware::graphics::composer::V2_1::Layer;

namespace aidl::android::hardware::graphics::composer3::impl {

std::unique_ptr<IResourceManager> IResourceManager::create() {
    return std::make_unique<ResourceManager>();
}

std::unique_ptr<IBufferReleaser> ResourceManager::createReleaser(bool isBuffer) {
    return std::make_unique<BufferReleaser>(isBuffer);
}

void ResourceManager::clear(RemoveDisplay removeDisplay) {
    mResources->clear([removeDisplay](Display hwcDisplay, bool isVirtual,
                                      const std::vector<Layer> hwcLayers) {
        int64_t display;
        std::vector<int64_t> layers;
        h2a::translate(hwcDisplay, display);
        h2a::translate(hwcLayers, layers);

        removeDisplay(display, isVirtual, layers);
    });
}

bool ResourceManager::hasDisplay(int64_t display) {
    Display hwcDisplay;
    a2h::translate(display, hwcDisplay);

    return mResources->hasDisplay(hwcDisplay);
}

int32_t ResourceManager::addPhysicalDisplay(int64_t display) {
    Display hwcDisplay;
    a2h::translate(display, hwcDisplay);

    Error hwcErr = mResources->addPhysicalDisplay(hwcDisplay);

    int32_t err;
    h2a::translate(hwcErr, err);
    return err;
}


int32_t ResourceManager::addVirtualDisplay(int64_t display, uint32_t outputBufferCacheSize) {
    Display hwcDisplay;
    a2h::translate(display, hwcDisplay);

    Error hwcErr = mResources->addVirtualDisplay(hwcDisplay, outputBufferCacheSize);

    int32_t err;
    h2a::translate(hwcErr, err);
    return err;
}

int32_t ResourceManager::removeDisplay(int64_t display) {
    Display hwcDisplay;
    a2h::translate(display, hwcDisplay);

    Error hwcErr = mResources->removeDisplay(hwcDisplay);

    int32_t err;
    h2a::translate(hwcErr, err);
    return err;
}

int32_t ResourceManager::setDisplayClientTargetCacheSize(int64_t display,
                                                         uint32_t clientTargetCacheSize) {
    Display hwcDisplay;
    a2h::translate(display, hwcDisplay);

    Error hwcErr = mResources->setDisplayClientTargetCacheSize(hwcDisplay, clientTargetCacheSize);

    int32_t err;
    h2a::translate(hwcErr, err);
    return err;
}

int32_t ResourceManager::getDisplayClientTargetCacheSize(int64_t display, size_t* outCacheSize) {
    Display hwcDisplay;
    a2h::translate(display, hwcDisplay);

    Error hwcErr = mResources->getDisplayClientTargetCacheSize(hwcDisplay, outCacheSize);

    int32_t err;
    h2a::translate(hwcErr, err);
    return err;
}

int32_t ResourceManager::getDisplayOutputBufferCacheSize(int64_t display, size_t* outCacheSize) {
    Display hwcDisplay;
    a2h::translate(display, hwcDisplay);

    Error hwcErr = mResources->getDisplayOutputBufferCacheSize(hwcDisplay, outCacheSize);

    int32_t err;
    h2a::translate(hwcErr, err);
    return err;
}

int32_t ResourceManager::addLayer(int64_t display, int64_t layer, uint32_t bufferCacheSize) {
    Display hwcDisplay;
    Layer hwcLayer;

    a2h::translate(display, hwcDisplay);
    a2h::translate(layer, hwcLayer);

    Error hwcErr = mResources->addLayer(hwcDisplay, hwcLayer, bufferCacheSize);

    int32_t err;
    h2a::translate(hwcErr, err);
    return err;
}

int32_t ResourceManager::removeLayer(int64_t display, int64_t layer) {
    Display hwcDisplay;
    Layer hwcLayer;

    a2h::translate(display, hwcDisplay);
    a2h::translate(layer, hwcLayer);
    Error hwcErr = mResources->removeLayer(hwcDisplay, hwcLayer);

    int32_t err;
    h2a::translate(hwcErr, err);
    return err;
}

void ResourceManager::setDisplayMustValidateState(int64_t display, bool mustValidate) {
    Display hwcDisplay;
    a2h::translate(display, hwcDisplay);

    mResources->setDisplayMustValidateState(hwcDisplay, mustValidate);
}

bool ResourceManager::mustValidateDisplay(int64_t display) {
    Display hwcDisplay;
    a2h::translate(display, hwcDisplay);

    return mResources->mustValidateDisplay(hwcDisplay);
}

int32_t ResourceManager::getDisplayReadbackBuffer(int64_t display, const buffer_handle_t handle,
                                                  buffer_handle_t& outHandle,
                                                  IBufferReleaser* bufReleaser) {
    Display hwcDisplay;
    a2h::translate(display, hwcDisplay);

    // dynamic_cast is not available
    auto br = static_cast<BufferReleaser*>(bufReleaser);
    Error hwcErr = mResources->getDisplayReadbackBuffer(hwcDisplay, handle, &outHandle,
                                                        br->getReplacedHandle());

    int32_t err;
    h2a::translate(hwcErr, err);
    return err;
}

int32_t ResourceManager::getDisplayClientTarget(int64_t display, uint32_t slot, bool fromCache,
                                                const buffer_handle_t handle,
                                                buffer_handle_t& outHandle,
                                                IBufferReleaser* bufReleaser) {
    Display hwcDisplay;
    a2h::translate(display, hwcDisplay);

    auto br = static_cast<BufferReleaser*>(bufReleaser);
    Error hwcErr = mResources->getDisplayClientTarget(hwcDisplay, slot, fromCache, handle,
                                                      &outHandle, br->getReplacedHandle());

    int32_t err;
    h2a::translate(hwcErr, err);
    return err;
}

int32_t ResourceManager::getDisplayOutputBuffer(int64_t display, uint32_t slot, bool fromCache,
                                   const buffer_handle_t handle,
                                   buffer_handle_t& outHandle,
                                   IBufferReleaser* bufReleaser) {
    Display hwcDisplay;
    a2h::translate(display, hwcDisplay);

    auto br = static_cast<BufferReleaser*>(bufReleaser);
    Error hwcErr = mResources->getDisplayOutputBuffer(hwcDisplay, slot, fromCache, handle,
                                                      &outHandle, br->getReplacedHandle());

    int32_t err;
    h2a::translate(hwcErr, err);
    return err;
}

int32_t ResourceManager::getLayerBuffer(int64_t display, int64_t layer, uint32_t slot,
                                        bool fromCache, const buffer_handle_t rawHandle,
                                        buffer_handle_t& outBufferHandle,
                                        IBufferReleaser* bufReleaser) {
    Display hwcDisplay;
    a2h::translate(display, hwcDisplay);

    Layer hwcLayer;
    a2h::translate(layer, hwcLayer);

    auto br = static_cast<BufferReleaser*>(bufReleaser);
    Error hwcErr = mResources->getLayerBuffer(hwcDisplay, hwcLayer, slot, fromCache,
                                                rawHandle, &outBufferHandle,
                                                br->getReplacedHandle());

    int32_t err;
    h2a::translate(hwcErr, err);
    return err;
}

int32_t ResourceManager::getLayerSidebandStream(int64_t display, int64_t layer,
                                                const buffer_handle_t rawHandle,
                                                buffer_handle_t& outStreamHandle,
                                                IBufferReleaser* bufReleaser) {
    Display hwcDisplay;
    a2h::translate(display, hwcDisplay);

    Layer hwcLayer;
    a2h::translate(layer, hwcLayer);

    auto br = static_cast<BufferReleaser*>(bufReleaser);
    Error hwcErr = mResources->getLayerSidebandStream(hwcDisplay, hwcLayer, rawHandle,
                                                      &outStreamHandle, br->getReplacedHandle());

    int32_t err;
    h2a::translate(hwcErr, err);
    return err;
}

} // namespace aidl::android::hardware::graphics::composer3::impl
