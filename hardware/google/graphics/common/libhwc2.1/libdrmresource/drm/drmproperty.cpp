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

#include "drmproperty.h"
#include "drmdevice.h"

#include <errno.h>
#include <stdint.h>
#include <string>

#include <xf86drmMode.h>
#include <log/log.h>
#include <inttypes.h>
#include <utils/Errors.h>

static inline int64_t U642I64(uint64_t val) {
  return *(reinterpret_cast<int64_t *>(&val));
}

namespace android {

DrmProperty::DrmPropertyEnum::DrmPropertyEnum(drm_mode_property_enum *e)
    : value_(e->value), name_(e->name) {
}

DrmProperty::DrmPropertyEnum::~DrmPropertyEnum() {
}

DrmProperty::DrmProperty(drmModePropertyPtr p, uint64_t value)
    : id_(0), type_(DRM_PROPERTY_TYPE_INVALID), flags_(0), name_("") {
  init(p, value);
}

void DrmProperty::init(drmModePropertyPtr p, uint64_t value) {
  id_ = p->prop_id;
  flags_ = p->flags;
  name_ = p->name;
  value_ = value;

  for (int i = 0; i < p->count_values; ++i) values_.push_back(p->values[i]);

  for (int i = 0; i < p->count_enums; ++i) enums_.push_back(DrmPropertyEnum(&p->enums[i]));

  for (int i = 0; i < p->count_blobs; ++i) blob_ids_.push_back(p->blob_ids[i]);

  if ((flags_ & DRM_MODE_PROP_RANGE) || (flags_ & DRM_MODE_PROP_SIGNED_RANGE))
    type_ = DRM_PROPERTY_TYPE_INT;
  else if (flags_ & DRM_MODE_PROP_ENUM)
    type_ = DRM_PROPERTY_TYPE_ENUM;
  else if (flags_ & DRM_MODE_PROP_OBJECT)
    type_ = DRM_PROPERTY_TYPE_OBJECT;
  else if (flags_ & DRM_MODE_PROP_BLOB)
    type_ = DRM_PROPERTY_TYPE_BLOB;
  else if (flags_ & DRM_MODE_PROP_BITMASK)
    type_ = DRM_PROPERTY_TYPE_BITMASK;
}

uint32_t DrmProperty::id() const {
  return id_;
}

std::string DrmProperty::name() const {
  return name_;
}

std::tuple<int, uint64_t> DrmProperty::value() const {
  if (type_ == DRM_PROPERTY_TYPE_BLOB)
    return std::make_tuple(0, value_);

  if (values_.size() == 0)
    return std::make_tuple(-ENOENT, 0);

  switch (type_) {
    case DRM_PROPERTY_TYPE_INT:
      return std::make_tuple(0, value_);

    case DRM_PROPERTY_TYPE_BITMASK:
      return std::make_tuple(0, value_);

    case DRM_PROPERTY_TYPE_ENUM:
      if (value_ >= enums_.size())
        return std::make_tuple(-ENOENT, 0);

      return std::make_tuple(0, enums_[value_].value_);

    case DRM_PROPERTY_TYPE_OBJECT:
      return std::make_tuple(0, value_);

    default:
      return std::make_tuple(-EINVAL, 0);
  }
}

void DrmProperty::printProperty() const
{
  ALOGD("=====================================================");
  ALOGD("name: %s, type(%d), value_(%" PRId64 ")", name_.c_str(), type_, value_);
  ALOGD("values.size(%zu)", values_.size());
  for (uint32_t i = 0; i < values_.size(); i++) {
    ALOGD("[%d] %" PRId64 "", i, values_[i]);
  }
  ALOGD("enums.size(%zu)", enums_.size());
  uint32_t i = 0;
  for (auto const &it : enums_) {
    ALOGD("[%d] %s %" PRId64 "", i++, it.name_.c_str(), it.value_);
  }
}

bool DrmProperty::isImmutable() const {
  return id_ && (flags_ & DRM_MODE_PROP_IMMUTABLE);
}

bool DrmProperty::isRange() const {
  return id_ && (flags_ & DRM_MODE_PROP_RANGE);
}

bool DrmProperty::isSignedRange() const {
  return (flags_ & DRM_MODE_PROP_EXTENDED_TYPE) == DRM_MODE_PROP_SIGNED_RANGE;
}

bool DrmProperty::isBitmask() const {
  return id_ && (flags_ & DRM_MODE_PROP_BITMASK);
}

std::tuple<int, uint64_t> DrmProperty::rangeMin() const {
  if (!isRange())
    return std::make_tuple(-EINVAL, 0);
  if (values_.size() < 1)
    return std::make_tuple(-ENOENT, 0);

  return std::make_tuple(0, values_[0]);
}

std::tuple<int, uint64_t> DrmProperty::rangeMax() const {
  if (!isRange())
    return std::make_tuple(-EINVAL, 0);
  if (values_.size() < 2)
    return std::make_tuple(-ENOENT, 0);

  return std::make_tuple(0, values_[1]);
}

std::tuple<uint64_t, int> DrmProperty::getEnumValueWithName(std::string name) const {
  for (auto it : enums_) {
    if (it.name_.compare(name) == 0) {
      return std::make_tuple(it.value_, 0);
    }
  }

  return std::make_tuple(UINT64_MAX, -EINVAL);
}

bool DrmProperty::validateChange(uint64_t value) const {
  if (isImmutable()) {
    ALOGE("%s: %s is immutable drm property (%zu)", __func__, name().c_str());
    return false;
  } else if (isRange()) {
    if (value < values_[0] || value > values_[1]) {
      ALOGE("%s: range property %s set to %" PRIu64 " is invalid [%" PRIu64 "-%" PRIu64 "]",
            __func__, name().c_str(), value, values_[0], values_[1]);
      return false;
    }
  } else if (isSignedRange()) {
    int64_t svalue = U642I64(value);

    if (svalue < U642I64(values_[0]) || svalue > U642I64(values_[1])) {
      ALOGE("%s: signed property %s set to %" PRIi64 " is invalid [%" PRIi64 "-%" PRIi64 "]",
            __func__, name().c_str(), svalue, U642I64(values_[0]), U642I64(values_[1]));
      return false;
    }
  } else if (isBitmask()) {
    uint64_t valid_mask = 0;

    for (auto i = 0; i < values_.size(); i++) {
      valid_mask |= (1ULL << values_[i]);
    }
    if (value & ~valid_mask) {
      ALOGE("%s: bitmask property %s set to 0x%" PRIx64 " is invalid [0x%" PRIx64 "]", __func__,
            name().c_str(), value, valid_mask);
      return false;
    }
  }

  return true;
}

void DrmProperty::updateValue(uint64_t value) {
  value_ = value;
}

std::tuple<uint64_t, int> DrmEnumParser::halToDrmEnum(const uint32_t halData,
                                                      const MapHal2DrmEnum& drmEnums) {
  auto it = drmEnums.find(halData);
  if (it != drmEnums.end()) {
    return std::make_tuple(it->second, NO_ERROR);
  } else {
    ALOGE("%s: Failed to find standard enum(%d)", __func__, halData);
    return std::make_tuple(0, -EINVAL);
  }
}

void DrmEnumParser::parseEnums(const DrmProperty &property,
                               const std::vector<std::pair<uint32_t, const char *>> &enums,
                               MapHal2DrmEnum& out_enums) {
  uint64_t value;
  int err;
  for (auto &e : enums) {
    std::tie(value, err) = property.getEnumValueWithName(e.second);
    if (err) {
      ALOGE("%s: Fail to find enum value with name %s", __func__, e.second);
    } else {
      out_enums[e.first] = value;
    }
  }
}

}  // namespace android
