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

#ifndef ICING_TESTING_RECORDER_TEST_UTILS_H_
#define ICING_TESTING_RECORDER_TEST_UTILS_H_

#include <cstdint>
#include <string>

namespace icing {
namespace lib {

struct ProfileInfo {
  int64_t peak_objects;
  int64_t peak_bytes;
  int64_t total_alloc_objects;
  int64_t total_alloc_bytes;
  int64_t inuse_objects;
  int64_t inuse_bytes;
};

// Reads a compressed profile.proto from a file and returns
// statistics summed across all samples. This functions is heavily tied to the
// implementation of https://cs.corp.google.com/search/?q=heap/alloc_recorder.cc
ProfileInfo SummarizeProfileProto(const std::string& filename);

}  //  namespace lib
}  //  namespace icing

#endif  // ICING_TESTING_RECORDER_TEST_UTILS_H_
