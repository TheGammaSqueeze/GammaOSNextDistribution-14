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

#ifndef MINIKIN_POINT_H
#define MINIKIN_POINT_H

#include <ostream>

namespace minikin {

struct Point {
    Point(float x, float y) : x(x), y(y) {}
    float x, y;
};

// For gtest
inline std::ostream& operator<<(std::ostream& os, const Point& p) {
    return os << "(" << p.x << ", " << p.y << ")";
}

inline bool operator==(const Point& l, const Point& r) {
    return l.x == r.x && l.y == r.y;
}

inline bool operator!=(const Point& l, const Point& r) {
    return !(l == r);
}

}  // namespace minikin

#endif  // MINIKIN_POINT_H
