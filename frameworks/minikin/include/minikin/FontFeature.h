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

#ifndef MINIKIN_FONT_FEATURE_H
#define MINIKIN_FONT_FEATURE_H

#include <hb.h>

#include <ostream>
#include <string_view>
#include <vector>

namespace minikin {

struct MinikinPaint;

// Subset of the hb_feature_t since we don't allow setting features on ranges.
struct FontFeature {
    hb_tag_t tag;
    uint32_t value;

    static std::vector<FontFeature> parse(std::string_view fontFeatureString);
};

/**
 * Returns the final set of font features based on the features requested by this paint object and
 * extra defaults or implied font features.
 *
 * Features are included from the paint object if they are:
 *   1) in a supported range
 *
 * Default features are added based if they are:
 *   1) implied due to Paint settings such as letterSpacing
 *   2) default features that do not conflict with requested features
 */
std::vector<hb_feature_t> cleanAndAddDefaultFontFeatures(const MinikinPaint& features);

// For gtest output
inline std::ostream& operator<<(std::ostream& os, const FontFeature& feature) {
    return os << static_cast<char>(feature.tag >> 24) << static_cast<char>(feature.tag >> 16)
              << static_cast<char>(feature.tag >> 8) << static_cast<char>(feature.tag) << " "
              << feature.value;
}

inline std::ostream& operator<<(std::ostream& os, const std::vector<FontFeature>& features) {
    for (size_t i = 0; i < features.size(); ++i) {
        if (i != 0) {
            os << ", ";
        }
        os << features;
    }
    return os;
}

constexpr bool operator==(const FontFeature& l, const FontFeature& r) {
    return l.tag == r.tag && l.value == r.value;
}

constexpr bool operator!=(const FontFeature& l, const FontFeature& r) {
    return !(l == r);
}

}  // namespace minikin
#endif  // MINIKIN_LAYOUT_H
