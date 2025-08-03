/*
 * Copyright (C) 2018 The Android Open Source Project
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

#ifndef MINIKIN_MINIKIN_RECT_H
#define MINIKIN_MINIKIN_RECT_H

#include <ostream>

#include "minikin/Point.h"

namespace minikin {

struct MinikinRect {
    MinikinRect() : mLeft(0), mTop(0), mRight(0), mBottom(0) {}
    MinikinRect(float left, float top, float right, float bottom)
            : mLeft(left), mTop(top), mRight(right), mBottom(bottom) {}

    bool isEmpty() const { return mLeft == mRight || mTop == mBottom; }
    bool isValid() const { return !std::isnan(mLeft); }
    float width() const { return mRight - mLeft; }

    void setEmpty() { mLeft = mRight = mTop = mBottom = 0; }

    // Shift the rectangle with given amount.
    void offset(float dx, float dy) {
        mLeft += dx;
        mTop += dy;
        mRight += dx;
        mBottom += dy;
    }

    // Update the rectangle with the union of the given rectangle with shifting.
    void join(float l, float t, float r, float b, float dx, float dy) {
        if (isEmpty()) {
            mLeft = l + dx;
            mTop = t + dy;
            mRight = r + dx;
            mBottom = b + dy;
        } else {
            mLeft = std::min(mLeft, l + dx);
            mTop = std::min(mTop, t + dy);
            mRight = std::max(mRight, r + dx);
            mBottom = std::max(mBottom, b + dy);
        }
    }

    void offset(const Point& p) { offset(p.x, p.y); }
    void join(const MinikinRect& r) { return join(r.mLeft, r.mTop, r.mRight, r.mBottom, 0, 0); }
    void join(const MinikinRect& r, float dx, float dy) {
        return join(r.mLeft, r.mTop, r.mRight, r.mBottom, dx, dy);
    }
    void join(const MinikinRect& r, const Point& p) {
        return join(r.mLeft, r.mTop, r.mRight, r.mBottom, p.x, p.y);
    }

    static MinikinRect makeInvalid() {
        return MinikinRect(
                std::numeric_limits<float>::quiet_NaN(), std::numeric_limits<float>::quiet_NaN(),
                std::numeric_limits<float>::quiet_NaN(), std::numeric_limits<float>::quiet_NaN());
    }

    float mLeft;
    float mTop;
    float mRight;
    float mBottom;
};

// For gtest output
inline std::ostream& operator<<(std::ostream& os, const MinikinRect& r) {
    return os << "(" << r.mLeft << ", " << r.mTop << ")-(" << r.mRight << ", " << r.mBottom << ")";
}

inline bool operator==(const MinikinRect& l, const MinikinRect& r) {
    return l.mLeft == r.mLeft && l.mTop == r.mTop && l.mRight == r.mRight && l.mBottom == r.mBottom;
}

inline bool operator!=(const MinikinRect& l, const MinikinRect& r) {
    return !(l == r);
}

}  // namespace minikin

#endif  // MINIKIN_MINIKIN_RECT_H
