/*
 * Copyright (C) 2019-2020 The LineageOS Project
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

#include <android-base/file.h>
#include <android-base/strings.h>
#include <vendor/lineage/livedisplay/2.0/IAutoContrast.h>
#include <vendor/lineage/livedisplay/2.0/IColorEnhancement.h>
#include <vendor/lineage/livedisplay/2.0/IReadingEnhancement.h>

namespace vendor {
namespace lineage {
namespace livedisplay {
namespace V2_0 {
namespace sysfs {

using ::android::hardware::Return;

template <typename T>
struct SimpleModeTrait {
    static constexpr const char* kNode = nullptr;
};

template <typename T>
class SimpleMode : public T {
    static constexpr const char* kNode = SimpleModeTrait<T>::kNode;

  public:
    static bool isSupported() {
        static_assert(kNode, "Node unspecified.");
        return !access(SimpleModeTrait<T>::kNode, R_OK | W_OK);
    }

    // Methods from ::vendor::lineage::livedisplay::V2_0::I* follow.
    Return<bool> isEnabled() override {
        std::string tmp;
        int32_t contents = 0;

        if (::android::base::ReadFileToString(kNode, &tmp)) {
            contents = std::stoi(::android::base::Trim(tmp));
        }

        return contents > 0;
    }

    Return<bool> setEnabled(bool enabled) override {
        return ::android::base::WriteStringToFile(std::to_string(enabled), kNode, true);
    }
};

template <>
struct SimpleModeTrait<IAutoContrast> {
    static constexpr const char* kNode = "/sys/class/graphics/fb0/aco";
};
using AutoContrast = SimpleMode<IAutoContrast>;

template <>
struct SimpleModeTrait<IColorEnhancement> {
    static constexpr const char* kNode = "/sys/class/graphics/fb0/color_enhance";
};
using ColorEnhancement = SimpleMode<IColorEnhancement>;

template <>
struct SimpleModeTrait<IReadingEnhancement> {
    static constexpr const char* kNode = "/sys/class/graphics/fb0/reading_mode";
};
using ReadingEnhancement = SimpleMode<IReadingEnhancement>;

}  // namespace sysfs
}  // namespace V2_0
}  // namespace livedisplay
}  // namespace lineage
}  // namespace vendor
