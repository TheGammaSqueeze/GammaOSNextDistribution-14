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

#pragma once

#include <android/hardware/graphics/composer3/ComposerServiceWriter.h>
#include <utils/Mutex.h>

#include <memory>

#include "include/IComposerHal.h"
#include "include/IResourceManager.h"

namespace aidl::android::hardware::graphics::composer3::impl {

class ComposerCommandEngine {
  public:
      ComposerCommandEngine(IComposerHal* hal, IResourceManager* resources)
            : mHal(hal), mResources(resources) {}
      int32_t init();

      int32_t execute(const std::vector<DisplayCommand>& commands,
                      std::vector<CommandResultPayload>* result);

      template <typename InputType, typename Functor>
      void dispatchLayerCommand(int64_t display, int64_t layer, const std::string& funcName,
                                const InputType input, const Functor func);

      void reset() {
          mWriter->reset();
      }

  private:
      void dispatchDisplayCommand(const DisplayCommand& displayCommand);
      void dispatchLayerCommand(int64_t display, const LayerCommand& displayCommand);

      void executeSetColorTransform(int64_t display, const std::vector<float>& matrix);
      void executeSetClientTarget(int64_t display, const ClientTarget& command);
      void executeSetDisplayBrightness(uint64_t display, const DisplayBrightness& command);
      void executeSetOutputBuffer(uint64_t display, const Buffer& buffer);
      void executeValidateDisplay(int64_t display,
                                  const std::optional<ClockMonotonicTimestamp> expectedPresentTime,
                                  int frameIntervalNs);
      void executePresentOrValidateDisplay(
              int64_t display, const std::optional<ClockMonotonicTimestamp> expectedPresentTime,
              int frameIntervalNs);
      void executeAcceptDisplayChanges(int64_t display);
      int executePresentDisplay(int64_t display);

      void executeSetLayerCursorPosition(int64_t display, int64_t layer,
                                         const common::Point& cursorPosition);
      void executeSetLayerBuffer(int64_t display, int64_t layer, const Buffer& buffer);
      void executeSetLayerSurfaceDamage(int64_t display, int64_t layer,
                                        const std::vector<std::optional<common::Rect>>& damage);
      void executeSetLayerBlendMode(int64_t display, int64_t layer,
                                    const ParcelableBlendMode& blendMode);
      void executeSetLayerColor(int64_t display, int64_t layer, const Color& color);
      void executeSetLayerComposition(int64_t display, int64_t layer,
                                      const ParcelableComposition& composition);
      void executeSetLayerDataspace(int64_t display, int64_t layer,
                                    const ParcelableDataspace& dataspace);
      void executeSetLayerDisplayFrame(int64_t display, int64_t layer, const common::Rect& rect);
      void executeSetLayerPlaneAlpha(int64_t display, int64_t layer, const PlaneAlpha& planeAlpha);
      void executeSetLayerSidebandStream(int64_t display, int64_t layer,
                                         const AidlNativeHandle& sidebandStream);
      void executeSetLayerSourceCrop(int64_t display, int64_t layer,
                                     const common::FRect& sourceCrop);
      void executeSetLayerTransform(int64_t display, int64_t layer,
                                    const ParcelableTransform& transform);
      void executeSetLayerVisibleRegion(
              int64_t display, int64_t layer,
              const std::vector<std::optional<common::Rect>>& visibleRegion);
      void executeSetLayerZOrder(int64_t display, int64_t layer, const ZOrder& zOrder);
      void executeSetLayerPerFrameMetadata(
              int64_t display, int64_t layer,
              const std::vector<std::optional<PerFrameMetadata>>& perFrameMetadata);
      void executeSetLayerColorTransform(int64_t display, int64_t layer,
                                         const std::vector<float>& colorTransform);
      void executeSetLayerPerFrameMetadataBlobs(int64_t display, int64_t layer,
              const std::vector<std::optional<PerFrameMetadataBlob>>& perFrameMetadataBlob);
      void executeSetLayerBrightness(int64_t display, int64_t layer,
                                     const LayerBrightness& brightness);

      int32_t executeValidateDisplayInternal(int64_t display);
      void executeSetExpectedPresentTimeInternal(
              int64_t display, const std::optional<ClockMonotonicTimestamp> expectedPresentTime,
              int frameIntervalNs);

      IComposerHal* mHal;
      IResourceManager* mResources;
      std::unique_ptr<ComposerServiceWriter> mWriter;
      int32_t mCommandIndex;
};

template <typename InputType, typename Functor>
void ComposerCommandEngine::dispatchLayerCommand(int64_t display, int64_t layer,
                                                 const std::string& funcName, const InputType input,
                                                 const Functor func) {
    if (input) {
        auto err = (mHal->*func)(display, layer, *input);
        if (err) {
            LOG(ERROR) << funcName << ": err " << err;
            mWriter->setError(mCommandIndex, err);
        }
    }
};

} // namespace aidl::android::hardware::graphics::composer3::impl

