/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include <hardware/hwcomposer2.h>

#include "bufferinfo/BufferInfoGetter.h"
#include "compositor/LayerData.h"

namespace android {

class HwcDisplay;

class HwcLayer {
 public:
  explicit HwcLayer(HwcDisplay *parent_display) : parent_(parent_display){};

  HWC2::Composition GetSfType() const {
    return sf_type_;
  }
  HWC2::Composition GetValidatedType() const {
    return validated_type_;
  }
  void AcceptTypeChange() {
    sf_type_ = validated_type_;
  }
  void SetValidatedType(HWC2::Composition type) {
    validated_type_ = type;
  }
  bool IsTypeChanged() const {
    return sf_type_ != validated_type_;
  }

  bool GetPriorBufferScanOutFlag() const {
    return prior_buffer_scanout_flag_;
  }

  void SetPriorBufferScanOutFlag(bool state) {
    prior_buffer_scanout_flag_ = state;
  }

  uint32_t GetZOrder() const {
    return z_order_;
  }

  auto &GetLayerData() {
    return layer_data_;
  }

  // Layer hooks
  HWC2::Error SetCursorPosition(int32_t /*x*/, int32_t /*y*/);
  HWC2::Error SetLayerBlendMode(int32_t mode);
  HWC2::Error SetLayerBuffer(buffer_handle_t buffer, int32_t acquire_fence);
  HWC2::Error SetLayerColor(hwc_color_t /*color*/);
  HWC2::Error SetLayerCompositionType(int32_t type);
  HWC2::Error SetLayerDataspace(int32_t dataspace);
  HWC2::Error SetLayerDisplayFrame(hwc_rect_t frame);
  HWC2::Error SetLayerPlaneAlpha(float alpha);
  HWC2::Error SetLayerSidebandStream(const native_handle_t *stream);
  HWC2::Error SetLayerSourceCrop(hwc_frect_t crop);
  HWC2::Error SetLayerSurfaceDamage(hwc_region_t damage);
  HWC2::Error SetLayerTransform(int32_t transform);
  HWC2::Error SetLayerVisibleRegion(hwc_region_t visible);
  HWC2::Error SetLayerZOrder(uint32_t order);

 private:
  // sf_type_ stores the initial type given to us by surfaceflinger,
  // validated_type_ stores the type after running ValidateDisplay
  HWC2::Composition sf_type_ = HWC2::Composition::Invalid;
  HWC2::Composition validated_type_ = HWC2::Composition::Invalid;

  uint32_t z_order_ = 0;
  LayerData layer_data_;

  /* The following buffer data can have 2 sources:
   * 1 - Mapper@4 metadata API
   * 2 - HWC@2 API
   * We keep ability to have 2 sources in drm_hwc. It may be useful for CLIENT
   * layer, at this moment HWC@2 API can't specify blending mode for this layer,
   * but Mapper@4 can do that
   */
  BufferColorSpace color_space_{};
  BufferSampleRange sample_range_{};
  BufferBlendMode blend_mode_{};
  buffer_handle_t buffer_handle_{};
  bool buffer_handle_updated_{};

  bool prior_buffer_scanout_flag_{};

  HwcDisplay *const parent_;

  /* Layer state */
 public:
  void PopulateLayerData();

  bool IsLayerUsableAsDevice() const {
    return !bi_get_failed_ && !fb_import_failed_ && buffer_handle_ != nullptr;
  }

 private:
  void ImportFb();
  bool bi_get_failed_{};
  bool fb_import_failed_{};

  /* SwapChain Cache */
 public:
  void SwChainClearCache();

 private:
  struct SwapChainElement {
    std::optional<BufferInfo> bi;
    std::shared_ptr<DrmFbIdHandle> fb;
  };

  bool SwChainGetBufferFromCache(BufferUniqueId unique_id);
  void SwChainReassemble(BufferUniqueId unique_id);
  void SwChainAddCurrentBuffer(BufferUniqueId unique_id);

  std::map<int /*seq_no*/, SwapChainElement> swchain_cache_;
  std::map<BufferUniqueId, int /*seq_no*/> swchain_lookup_table_;
  bool swchain_reassembled_{};
};

}  // namespace android
