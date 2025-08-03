// Copyright (C) 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "icing/testing/tmp-directory.h"

#include <string>

#include "icing/absl_ports/str_cat.h"

namespace icing {
namespace lib {

// Some other options for getting a tmp directory:
// 1. FLAGS_test_tmpdir. We don't use FLAGS_test_tmpdir because it only exists
//    in the internal version of googletest.h (as of June 2019)
// 2. ::testing::TempDir(). It returns "/sdcard" for Android emulators in which
//    the sdcard file format is FAT32
//    (https://developer.android.com/studio/command-line/mksdcard). FAT32
//    doesn't support sparse files so that it fails some tests in
//    //icing/file/filesystem_test.cc.
//    The sparse file related methods are mostly for reporting/logging purposes
//    and not affecting any system behaviors.
std::string GetTestTempDir() {
#ifdef ICING_REVERSE_JNI_SEGMENTATION
  // The "icing.jni" should be whatever the package name is set for the
  // Android test app. Set in //icing/testing/AndroidManifest.xml
  // For JNI tests, we can only write to our package's directory.
  return "/data/data/icing.jni";
#elif defined(__ANDROID__)
  return "/data/local/tmp";
#elif defined(__APPLE__)
  return absl_ports::StrCat(getenv("HOME"), "/tmp");
#else
  return "/tmp";
#endif  // ICING_REVERSE_JNI_SEGMENTATION
}

}  // namespace lib
}  // namespace icing
