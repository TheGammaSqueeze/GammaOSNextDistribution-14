/*
 * Copyright (C) 2015 The Android Open Source Project
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

// NOLINTNEXTLINE(cppcoreguidelines-macro-usage)
#define LOG_TAG "hwc-drm-property"

#include "DrmProperty.h"

#include <xf86drmMode.h>

#include <cerrno>
#include <cstdint>
#include <string>

#include "DrmDevice.h"
#include "utils/log.h"

namespace android {

DrmProperty::DrmPropertyEnum::DrmPropertyEnum(drm_mode_property_enum *e)
    : value(e->value), name(e->name) {
}

DrmProperty::DrmProperty(uint32_t obj_id, drmModePropertyPtr p,
                         uint64_t value) {
  Init(obj_id, p, value);
}

void DrmProperty::Init(uint32_t obj_id, drmModePropertyPtr p, uint64_t value) {
  obj_id_ = obj_id;
  id_ = p->prop_id;
  flags_ = p->flags;
  name_ = p->name;
  value_ = value;

  for (int i = 0; i < p->count_values; ++i)
    // NOLINTNEXTLINE(cppcoreguidelines-pro-bounds-pointer-arithmetic):
    values_.emplace_back(p->values[i]);

  for (int i = 0; i < p->count_enums; ++i)
    // NOLINTNEXTLINE(cppcoreguidelines-pro-bounds-pointer-arithmetic):
    enums_.emplace_back(&p->enums[i]);

  for (int i = 0; i < p->count_blobs; ++i)
    // NOLINTNEXTLINE(cppcoreguidelines-pro-bounds-pointer-arithmetic):
    blob_ids_.emplace_back(p->blob_ids[i]);
}

std::optional<uint64_t> DrmProperty::GetValue() const {
  if ((flags_ & DRM_MODE_PROP_BLOB) != 0)
    return value_;

  if (values_.empty())
    return {};

  if ((flags_ & DRM_MODE_PROP_RANGE) != 0)
    return value_;

  if ((flags_ & DRM_MODE_PROP_ENUM) != 0) {
    if (value_ >= enums_.size())
      return {};

    return enums_[value_].value;
  }

  if ((flags_ & DRM_MODE_PROP_OBJECT) != 0)
    return value_;

  return {};
}

std::tuple<int, uint64_t> DrmProperty::RangeMin() const {
  if (!IsRange())
    return std::make_tuple(-EINVAL, 0);
  if (values_.empty())
    return std::make_tuple(-ENOENT, 0);

  return std::make_tuple(0, values_[0]);
}

std::tuple<int, uint64_t> DrmProperty::RangeMax() const {
  if (!IsRange())
    return std::make_tuple(-EINVAL, 0);
  if (values_.size() < 2)
    return std::make_tuple(-ENOENT, 0);

  return std::make_tuple(0, values_[1]);
}

std::tuple<uint64_t, int> DrmProperty::GetEnumValueWithName(
    const std::string &name) const {
  for (const auto &it : enums_) {
    if (it.name == name) {
      return std::make_tuple(it.value, 0);
    }
  }

  return std::make_tuple(UINT64_MAX, -EINVAL);
}

auto DrmProperty::AtomicSet(drmModeAtomicReq &pset, uint64_t value) const
    -> bool {
  if (id_ == 0) {
    ALOGE("AtomicSet() is called on non-initialized property!");
    return false;
  }
  if (drmModeAtomicAddProperty(&pset, obj_id_, id_, value) < 0) {
    ALOGE("Failed to add obj_id: %u, prop_id: %u (%s) to pset", obj_id_, id_,
          name_.c_str());
    return false;
  }
  return true;
}

}  // namespace android
