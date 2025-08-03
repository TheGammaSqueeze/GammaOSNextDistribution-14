/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include "minikin/Debug.h"

#include <unicode/utf16.h>
#include <unicode/utf8.h>

#include <sstream>

#include "minikin/FontFeature.h"
#include "minikin/FontFileParser.h"
#include "minikin/LayoutCore.h"
#include "minikin/LocaleList.h"
#include "minikin/MinikinExtent.h"
#include "minikin/MinikinPaint.h"
#include "minikin/MinikinRect.h"
#include "minikin/Point.h"
#include "minikin/Range.h"
#include "minikin/U16StringPiece.h"

namespace minikin {

namespace debug {

std::string toUtf8(const U16StringPiece& text) {
    std::string output;
    output.resize(text.size() * 4);
    uint32_t in_i = 0;
    uint32_t out_i = 0;
    bool isError = false;

    while (in_i < text.size()) {
        uint32_t ch;
        U16_NEXT(text, in_i, text.size(), ch);
        U8_APPEND(output, out_i, output.size(), ch, isError);
        if (isError) {
            return "";
        }
    }

    output.resize(out_i);
    return output;
}

std::string toString(const Point& point) {
    std::stringstream ss;
    ss << point;
    return ss.str();
}

std::string toString(const MinikinRect& rect) {
    std::stringstream ss;
    ss << rect;
    return ss.str();
}

std::string toString(const Range& range) {
    std::stringstream ss;
    ss << range;
    return ss.str();
}

std::string toString(const MinikinExtent& extent) {
    std::stringstream ss;
    ss << extent;
    return ss.str();
}

std::string toString(const FontFeature& feature) {
    std::stringstream ss;
    ss << feature;
    return ss.str();
}

std::string toString(const std::vector<FontFeature>& features) {
    std::stringstream ss;
    ss << features;
    return ss.str();
}

std::string toString(const LayoutPiece& layout) {
    std::stringstream ss;
    ss << "{advance=" << layout.advance() << ", extent=" << toString(layout.extent())
       << ", glyphs=";

    for (uint32_t i = 0; i < layout.glyphCount(); ++i) {
        ss << "{id:" << layout.glyphIdAt(i) << ", pos = " << toString(layout.pointAt(i))
           << ", font = \""
           << FontFileParser(layout.fontAt(i).hbFont()).getPostScriptName().value_or("[UNKNOWN]")
           << "\"}, ";
    }
    return ss.str();
}

std::string toString(const MinikinPaint& paint) {
    std::stringstream ss;
    ss << "{size=" << paint.size << ", scaleX=" << paint.scaleX << ", skewX=" << paint.skewX
       << ", letterSpacing=" << paint.letterSpacing << ", wordSpacing=" << paint.wordSpacing
       << ", fontFlags=" << paint.fontFlags
       << ", localeList=" << getLocaleString(paint.localeListId)
       << ", fontStyle=" << paint.fontStyle << ", familyVariant=" << paint.familyVariant
       << ", fontFeatureSettings=" << paint.fontFeatureSettings
       << ", fontCollectionId=" << paint.font->getId();
    return ss.str();
}

}  // namespace debug

}  // namespace minikin
