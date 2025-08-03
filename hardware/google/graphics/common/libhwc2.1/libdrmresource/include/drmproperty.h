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

#ifndef ANDROID_DRM_PROPERTY_H_
#define ANDROID_DRM_PROPERTY_H_

#include <stdint.h>
#include <xf86drmMode.h>
#include <string>
#include <unordered_map>
#include <vector>

namespace android {

enum DrmPropertyType {
  DRM_PROPERTY_TYPE_INT,
  DRM_PROPERTY_TYPE_ENUM,
  DRM_PROPERTY_TYPE_OBJECT,
  DRM_PROPERTY_TYPE_BLOB,
  DRM_PROPERTY_TYPE_BITMASK,
  DRM_PROPERTY_TYPE_INVALID,
};

class DrmProperty {
 public:
  DrmProperty() = default;
  DrmProperty(drmModePropertyPtr p, uint64_t value);
  DrmProperty(const DrmProperty &) = delete;
  DrmProperty &operator=(const DrmProperty &) = delete;

  void init(drmModePropertyPtr p, uint64_t value);
  void setName(std::string name) { name_ = name; };
  std::tuple<uint64_t, int> getEnumValueWithName(std::string name) const;

  uint32_t id() const;
  std::string name() const;

  std::tuple<int, uint64_t> value() const;
  bool isImmutable() const;
  bool isRange() const;
  bool isSignedRange() const;
  bool isBitmask() const;

  std::tuple<int, uint64_t> rangeMin() const;
  std::tuple<int, uint64_t> rangeMax() const;

  bool validateChange(uint64_t value) const;
  void updateValue(const uint64_t value);
  void printProperty() const;

 private:
  class DrmPropertyEnum {
   public:
    DrmPropertyEnum(drm_mode_property_enum *e);
    ~DrmPropertyEnum();

    uint64_t value_;
    std::string name_;
  };

  uint32_t id_ = 0;

  DrmPropertyType type_ = DRM_PROPERTY_TYPE_INVALID;
  uint32_t flags_ = 0;
  std::string name_;
  uint64_t value_ = 0;

  std::vector<uint64_t> values_;
  std::vector<DrmPropertyEnum> enums_;
  std::vector<uint32_t> blob_ids_;
};

class DrmEnumParser {
public:
    using MapHal2DrmEnum = std::unordered_map<uint32_t, uint64_t>;

    static std::tuple<uint64_t, int> halToDrmEnum(const uint32_t halData,
                                                  const MapHal2DrmEnum& drmEnums);

    static void parseEnums(const DrmProperty &property,
                           const std::vector<std::pair<uint32_t, const char *>> &enums,
                           MapHal2DrmEnum& out_enums);
};

}  // namespace android

#endif  // ANDROID_DRM_PROPERTY_H_
