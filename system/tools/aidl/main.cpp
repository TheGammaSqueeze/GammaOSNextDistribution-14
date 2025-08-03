/*
 * Copyright (C) 2015, The Android Open Source Project
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

#include "aidl.h"
#include "io_delegate.h"
#include "logging.h"
#include "options.h"

#include <iostream>

using android::aidl::Options;

#ifdef AIDL_CPP_BUILD
constexpr Options::Language kDefaultLang = Options::Language::CPP;
#else
constexpr Options::Language kDefaultLang = Options::Language::JAVA;
#endif

int main(int argc, char* argv[]) {
  Options options(argc, argv, kDefaultLang);

  // Only minimal functionality should go here, so that as much of possible of
  // the aidl compiler is mocked with the single function `aidl_entry`

  android::aidl::IoDelegate io_delegate;
  int ret = aidl_entry(options, io_delegate);

  return ret;
}
