// Copyright 2018 The Chromium OS Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "bsdiff/utils.h"

namespace bsdiff {

int64_t ParseInt64(const uint8_t* buf) {
  // BSPatch uses a non-standard encoding of integers.
  // Highest bit of that integer is used as a sign bit, 1 = negative
  // and 0 = positive.
  // Therefore, if the highest bit is set, flip it, then do 2's complement
  // to get the integer in standard form
  int64_t result = buf[7] & 0x7F;
  for (int i = 6; i >= 0; i--) {
    result <<= 8;
    result |= buf[i];
  }

  if (buf[7] & 0x80)
    result = -result;
  return result;
}

}  // namespace bsdiff
