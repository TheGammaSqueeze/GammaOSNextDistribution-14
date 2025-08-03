// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "ui/events/ozone/evdev/touch_evdev_types.h"

namespace ui {

InProgressTouchEvdev::InProgressTouchEvdev() = default;

InProgressTouchEvdev::InProgressTouchEvdev(const InProgressTouchEvdev& other) =
    default;

InProgressTouchEvdev::~InProgressTouchEvdev() = default;

std::ostream& operator<<(std::ostream& out, const InProgressTouchEvdev& touch) {
  out << "InProgressTouchEvdev(x=" << touch.x << ", y=" << touch.y
      << ", tracking_id=" << touch.tracking_id << ", slot=" << touch.slot
      << ", pressure=" << touch.pressure << ", major=" << touch.major
      << ", minor=" << touch.minor << ", tool_type=" << touch.tool_type
      << ", altered=" << touch.altered
      << ", was_touching=" << touch.was_touching
      << ", touching=" << touch.touching << ")";
  return out;
}

InProgressStylusState::InProgressStylusState() = default;

InProgressStylusState::InProgressStylusState(
    const InProgressStylusState& other) = default;

InProgressStylusState::~InProgressStylusState() = default;

}  // namespace ui
