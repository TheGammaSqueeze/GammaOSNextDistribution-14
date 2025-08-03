// Copyright 2022 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "ui/events/ozone/evdev/touch_filter/shared_palm_detection_filter_state.h"

namespace ui {

std::ostream& operator<<(std::ostream& out,
                         const SharedPalmDetectionFilterState& state) {
  base::TimeTicks now = base::TimeTicks::Now();
  out << "SharedPalmDetectionFilterState(\n";
  out << "  latest_stylus_touch_time = " << state.latest_stylus_touch_time
      << " (" << now - state.latest_stylus_touch_time << " from now)\n";
  out << "  latest_finger_touch_time = " << state.latest_finger_touch_time
      << "\n";
  out << "  active_finger_touches = " << state.active_finger_touches << "\n";
  out << "  active_palm_touches = " << state.active_palm_touches << "\n";
  out << "  latest_palm_touch_time = " << state.latest_palm_touch_time << "\n";
  out << "  Now() = " << now << "\n";
  out << ")";
  return out;
}

}  // namespace ui