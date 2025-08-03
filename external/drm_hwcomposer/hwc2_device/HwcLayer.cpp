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

#define LOG_TAG "hwc-layer"

#include "HwcLayer.h"

#include "HwcDisplay.h"
#include "bufferinfo/BufferInfoGetter.h"
#include "utils/log.h"

namespace android {

// NOLINTNEXTLINE(readability-convert-member-functions-to-static)
HWC2::Error HwcLayer::SetCursorPosition(int32_t /*x*/, int32_t /*y*/) {
  return HWC2::Error::None;
}

HWC2::Error HwcLayer::SetLayerBlendMode(int32_t mode) {
  switch (static_cast<HWC2::BlendMode>(mode)) {
    case HWC2::BlendMode::None:
      blend_mode_ = BufferBlendMode::kNone;
      break;
    case HWC2::BlendMode::Premultiplied:
      blend_mode_ = BufferBlendMode::kPreMult;
      break;
    case HWC2::BlendMode::Coverage:
      blend_mode_ = BufferBlendMode::kCoverage;
      break;
    default:
      ALOGE("Unknown blending mode b=%d", blend_mode_);
      blend_mode_ = BufferBlendMode::kUndefined;
      break;
  }
  return HWC2::Error::None;
}

/* Find API details at:
 * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r3:hardware/libhardware/include/hardware/hwcomposer2.h;l=2314
 */
HWC2::Error HwcLayer::SetLayerBuffer(buffer_handle_t buffer,
                                     int32_t acquire_fence) {
  layer_data_.acquire_fence = MakeSharedFd(acquire_fence);
  buffer_handle_ = buffer;
  buffer_handle_updated_ = true;

  return HWC2::Error::None;
}

// NOLINTNEXTLINE(readability-convert-member-functions-to-static)
HWC2::Error HwcLayer::SetLayerColor(hwc_color_t /*color*/) {
  // TODO(nobody): Put to client composition here?
  return HWC2::Error::None;
}

HWC2::Error HwcLayer::SetLayerCompositionType(int32_t type) {
  sf_type_ = static_cast<HWC2::Composition>(type);
  return HWC2::Error::None;
}

HWC2::Error HwcLayer::SetLayerDataspace(int32_t dataspace) {
  switch (dataspace & HAL_DATASPACE_STANDARD_MASK) {
    case HAL_DATASPACE_STANDARD_BT709:
      color_space_ = BufferColorSpace::kItuRec709;
      break;
    case HAL_DATASPACE_STANDARD_BT601_625:
    case HAL_DATASPACE_STANDARD_BT601_625_UNADJUSTED:
    case HAL_DATASPACE_STANDARD_BT601_525:
    case HAL_DATASPACE_STANDARD_BT601_525_UNADJUSTED:
      color_space_ = BufferColorSpace::kItuRec601;
      break;
    case HAL_DATASPACE_STANDARD_BT2020:
    case HAL_DATASPACE_STANDARD_BT2020_CONSTANT_LUMINANCE:
      color_space_ = BufferColorSpace::kItuRec2020;
      break;
    default:
      color_space_ = BufferColorSpace::kUndefined;
  }

  switch (dataspace & HAL_DATASPACE_RANGE_MASK) {
    case HAL_DATASPACE_RANGE_FULL:
      sample_range_ = BufferSampleRange::kFullRange;
      break;
    case HAL_DATASPACE_RANGE_LIMITED:
      sample_range_ = BufferSampleRange::kLimitedRange;
      break;
    default:
      sample_range_ = BufferSampleRange::kUndefined;
  }
  return HWC2::Error::None;
}

HWC2::Error HwcLayer::SetLayerDisplayFrame(hwc_rect_t frame) {
  layer_data_.pi.display_frame = frame;
  return HWC2::Error::None;
}

HWC2::Error HwcLayer::SetLayerPlaneAlpha(float alpha) {
  layer_data_.pi.alpha = std::lround(alpha * UINT16_MAX);
  return HWC2::Error::None;
}

// NOLINTNEXTLINE(readability-convert-member-functions-to-static)
HWC2::Error HwcLayer::SetLayerSidebandStream(
    const native_handle_t* /*stream*/) {
  // TODO(nobody): We don't support sideband
  return HWC2::Error::Unsupported;
}

HWC2::Error HwcLayer::SetLayerSourceCrop(hwc_frect_t crop) {
  layer_data_.pi.source_crop = crop;
  return HWC2::Error::None;
}

// NOLINTNEXTLINE(readability-convert-member-functions-to-static)
HWC2::Error HwcLayer::SetLayerSurfaceDamage(hwc_region_t /*damage*/) {
  // TODO(nobody): We don't use surface damage, marking as unsupported
  return HWC2::Error::None;
}

HWC2::Error HwcLayer::SetLayerTransform(int32_t transform) {
  uint32_t l_transform = 0;

  // 270* and 180* cannot be combined with flips. More specifically, they
  // already contain both horizontal and vertical flips, so those fields are
  // redundant in this case. 90* rotation can be combined with either horizontal
  // flip or vertical flip, so treat it differently
  if (transform == HWC_TRANSFORM_ROT_270) {
    l_transform = LayerTransform::kRotate270;
  } else if (transform == HWC_TRANSFORM_ROT_180) {
    l_transform = LayerTransform::kRotate180;
  } else {
    if ((transform & HWC_TRANSFORM_FLIP_H) != 0)
      l_transform |= LayerTransform::kFlipH;
    if ((transform & HWC_TRANSFORM_FLIP_V) != 0)
      l_transform |= LayerTransform::kFlipV;
    if ((transform & HWC_TRANSFORM_ROT_90) != 0)
      l_transform |= LayerTransform::kRotate90;
  }

  layer_data_.pi.transform = static_cast<LayerTransform>(l_transform);
  return HWC2::Error::None;
}

// NOLINTNEXTLINE(readability-convert-member-functions-to-static)
HWC2::Error HwcLayer::SetLayerVisibleRegion(hwc_region_t /*visible*/) {
  // TODO(nobody): We don't use this information, marking as unsupported
  return HWC2::Error::None;
}

HWC2::Error HwcLayer::SetLayerZOrder(uint32_t order) {
  z_order_ = order;
  return HWC2::Error::None;
}

void HwcLayer::ImportFb() {
  if (!IsLayerUsableAsDevice() || !buffer_handle_updated_) {
    return;
  }
  buffer_handle_updated_ = false;

  layer_data_.fb = {};

  auto unique_id = BufferInfoGetter::GetInstance()->GetUniqueId(buffer_handle_);
  if (unique_id && SwChainGetBufferFromCache(*unique_id)) {
    return;
  }

  layer_data_.bi = BufferInfoGetter::GetInstance()->GetBoInfo(buffer_handle_);
  if (!layer_data_.bi) {
    ALOGW("Unable to get buffer information (0x%p)", buffer_handle_);
    bi_get_failed_ = true;
    return;
  }

  layer_data_
      .fb = parent_->GetPipe().device->GetDrmFbImporter().GetOrCreateFbId(
      &layer_data_.bi.value());

  if (!layer_data_.fb) {
    ALOGV("Unable to create framebuffer object for buffer 0x%p",
          buffer_handle_);
    fb_import_failed_ = true;
    return;
  }

  if (unique_id) {
    SwChainAddCurrentBuffer(*unique_id);
  }
}

void HwcLayer::PopulateLayerData() {
  ImportFb();

  if (!layer_data_.bi) {
    ALOGE("%s: Invalid state", __func__);
    return;
  }

  if (blend_mode_ != BufferBlendMode::kUndefined) {
    layer_data_.bi->blend_mode = blend_mode_;
  }
  if (color_space_ != BufferColorSpace::kUndefined) {
    layer_data_.bi->color_space = color_space_;
  }
  if (sample_range_ != BufferSampleRange::kUndefined) {
    layer_data_.bi->sample_range = sample_range_;
  }
}

/* SwapChain Cache */

bool HwcLayer::SwChainGetBufferFromCache(BufferUniqueId unique_id) {
  if (swchain_lookup_table_.count(unique_id) == 0) {
    return false;
  }

  auto seq = swchain_lookup_table_[unique_id];

  if (swchain_cache_.count(seq) == 0) {
    return false;
  }

  auto& el = swchain_cache_[seq];
  if (!el.bi) {
    return false;
  }

  layer_data_.bi = el.bi;
  layer_data_.fb = el.fb;

  return true;
}

void HwcLayer::SwChainReassemble(BufferUniqueId unique_id) {
  if (swchain_lookup_table_.count(unique_id) != 0) {
    if (swchain_lookup_table_[unique_id] ==
        int(swchain_lookup_table_.size()) - 1) {
      /* Skip same buffer */
      return;
    }
    if (swchain_lookup_table_[unique_id] == 0) {
      swchain_reassembled_ = true;
      return;
    }
    /* Tracking error */
    SwChainClearCache();
    return;
  }

  swchain_lookup_table_[unique_id] = int(swchain_lookup_table_.size());
}

void HwcLayer::SwChainAddCurrentBuffer(BufferUniqueId unique_id) {
  if (!swchain_reassembled_) {
    SwChainReassemble(unique_id);
  }

  if (swchain_reassembled_) {
    if (swchain_lookup_table_.count(unique_id) == 0) {
      SwChainClearCache();
      return;
    }

    auto seq = swchain_lookup_table_[unique_id];

    if (swchain_cache_.count(seq) == 0) {
      swchain_cache_[seq] = {};
    }

    swchain_cache_[seq].bi = layer_data_.bi;
    swchain_cache_[seq].fb = layer_data_.fb;
  }
}

void HwcLayer::SwChainClearCache() {
  swchain_cache_.clear();
  swchain_lookup_table_.clear();
  swchain_reassembled_ = false;
}

}  // namespace android