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

#ifndef MINIKIN_DEBUG_H
#define MINIKIN_DEBUG_H

#include <string>

namespace minikin {

struct Point;
struct MinikinRect;
struct MinikinExtent;
struct MinikinPaint;
struct FontFeature;
class Range;
class U16StringPiece;
class LayoutPiece;

namespace debug {

// Convert UTF16 string to std::string for debugging purpose.
std::string toUtf8(const U16StringPiece& text);

std::string toString(const Point& point);
std::string toString(const MinikinRect& rect);
std::string toString(const Range& range);
std::string toString(const MinikinExtent& extent);
std::string toString(const LayoutPiece& layout);
std::string toString(const MinikinPaint& paint);
std::string toString(const FontFeature& feature);
std::string toString(const std::vector<FontFeature>& features);

}  // namespace debug

}  // namespace minikin
#endif  // MINIKIN_DEBUG_H
