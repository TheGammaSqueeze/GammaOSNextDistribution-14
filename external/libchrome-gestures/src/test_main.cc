// Copyright 2012 The ChromiumOS Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <stdarg.h>
#include <stdio.h>

#include <gtest/gtest.h>

#include "include/command_line.h"
#include "include/gestures.h"

int main(int argc, char **argv) {
  gestures::CommandLine::Init(argc, argv);
  ::testing::InitGoogleTest(&argc, argv);
  return RUN_ALL_TESTS();
}

extern "C" {

// Provide this symbol for unittests
void gestures_log(int verb, const char* fmt, ...) {
  va_list args;
  va_start(args, fmt);
  vfprintf(stdout, fmt, args);
  va_end(args);
}

}
