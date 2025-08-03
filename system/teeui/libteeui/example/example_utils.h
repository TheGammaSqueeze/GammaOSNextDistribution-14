/*
 *
 * Copyright 2019, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include <stddef.h>

#include <teeui/label.h>

namespace teeui {
namespace example {

/*
 * AOSP color scheme constants.
 */
constexpr static const Color kShieldColor = Color(0xff778500);
constexpr static const Color kShieldColorInv = Color(0xffc4cb80);
constexpr static const Color kTextColor = Color(0xff212121);
constexpr static const Color kTextColorInv = Color(0xffdedede);
constexpr static const Color kBackGroundColor = Color(0xffffffff);
constexpr static const Color kBackGroundColorInv = Color(0xff212121);

uint32_t alfaCombineChannel(uint32_t shift, double alfa, uint32_t a, uint32_t b);

template <typename T> uint32_t renderPixel(uint32_t x, uint32_t y, const T& e) {
    return e.bounds_.drawPoint(Point<pxs>(x, y));
}

struct FrameBuffer {
    uint32_t left_;
    uint32_t top_;
    uint32_t width_;
    uint32_t height_;
    uint32_t* buffer_;
    size_t size_in_elements_;
    uint32_t lineStride_;

    Error drawPixel(uint32_t x, uint32_t y, uint32_t color) const;
};

template <typename... Elements>
Error drawElements(std::tuple<Elements...>& layout, const PixelDrawer& drawPixel) {
    // Error::operator|| is overloaded, so we don't get short circuit evaluation.
    // But we get the first error that occurs. We will still try and draw the remaining
    // elements in the order they appear in the layout tuple.
    return (std::get<Elements>(layout).draw(drawPixel) || ...);
}

template <typename... Elements>
Error handleAllEvent(std::tuple<Elements...>& layout, const Event& event) {
    return (std::get<Elements>(layout).hit(event) || ...);
}

void translate(LabelImpl* label);

template <typename T, typename Layout> void translateLabel(Layout* layout) {
    translate(&std::get<T>(*layout));
}
}  // namespace example
}  // namespace teeui