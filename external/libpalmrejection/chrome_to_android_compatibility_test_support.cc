// Copyright 2022 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "ui/gfx/geometry/point_f.h"

#include "base/time/time.h"

namespace gfx {

// clang-format off
void PrintTo(const PointF& point, ::std::ostream* os) {
  *os << point.ToString();
}
// clang-format on
}  // namespace gfx
