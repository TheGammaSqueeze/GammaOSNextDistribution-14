// Copyright 2022 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "chrome_to_android_compatibility.h"

// Android's external/libchrome directory is out of date.
// Add missing templates here as a temporary solution
namespace base {

bool operator==(const TimeTicks& t1, const TimeTicks& t2) {
  return t1.since_origin() == t2.since_origin();
}

}  // namespace base
