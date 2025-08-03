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

#include "icing/testing/recorder-test-utils.h"

#include <list>
#include <unordered_map>
#include <unordered_set>

#include "perftools/profiles/proto/profile.proto.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/file/filesystem.h"
#include "util/gzip/gzipstring.h"

namespace icing {
namespace lib {

namespace {

// The implementation for these functions is shamelessly plagiarized from
// https://cs.corp.google.com/search/?q=heap/alloc_recorder_test.cc
void ReadProfileFromFile(const std::string& filename,
                         perftools::profiles::Profile* profile) {
  Filesystem filesystem;
  ScopedFd sfd(filesystem.OpenForRead(filename.c_str()));
  int size = filesystem.GetFileSize(sfd.get());
  std::string str_profilez;
  str_profilez.resize(size);
  ASSERT_TRUE(filesystem.Read(sfd.get(), str_profilez.data(), size));

  std::string str_profile;
  ASSERT_TRUE(GunzipString(str_profilez, &str_profile));

  ASSERT_TRUE(profile->ParseFromString(str_profile));
}

}  // namespace

ProfileInfo SummarizeProfileProto(const std::string& filename) {
  perftools::profiles::Profile profile;
  ReadProfileFromFile(filename, &profile);
  ProfileInfo profile_info{};
  for (const auto &sample : profile.sample()) {
    EXPECT_THAT(sample.value(), testing::SizeIs(6));
    profile_info.peak_objects += sample.value(0);
    profile_info.peak_bytes += sample.value(1);
    profile_info.total_alloc_objects += sample.value(2);
    profile_info.total_alloc_bytes += sample.value(3);
    profile_info.inuse_objects += sample.value(4);
    profile_info.inuse_bytes += sample.value(5);
  }

  return profile_info;
}

}  //  namespace lib
}  //  namespace icing

