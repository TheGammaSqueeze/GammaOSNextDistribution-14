/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <cstdint>
#include <filesystem>
#include <unordered_set>

#include "android-base/file.h"
#include "dex/dex_file_verifier.h"
#include "dex/standard_dex_file.h"
#include "gtest/gtest.h"
#include "ziparchive/zip_archive.h"

namespace art {

class FuzzerCorpusTest : public testing::Test {
 public:
  static void VerifyDexFile(const uint8_t* data,
                            size_t size,
                            const std::string& name,
                            bool expected_success) {
    // Do not verify the checksum as we only care about the DEX file contents,
    // and know that the checksum would probably be erroneous (i.e. random).
    constexpr bool kVerify = false;

    // Special case for empty dex file. Set a fake data since the size is 0 anyway.
    if (data == nullptr) {
      ASSERT_EQ(size, 0);
      data = reinterpret_cast<const uint8_t*>(&name);
    }

    auto container = std::make_shared<art::MemoryDexFileContainer>(data, size);
    art::StandardDexFile dex_file(data,
                                  /*location=*/name,
                                  /*location_checksum=*/0,
                                  /*oat_dex_file=*/nullptr,
                                  container);

    std::string error_msg;
    bool success = art::dex::Verify(&dex_file, dex_file.GetLocation().c_str(), kVerify, &error_msg);
    ASSERT_EQ(success, expected_success) << " Failed for " << name;
  }
};

// Class that manages the ZipArchiveHandle liveness.
class ZipArchiveHandleScope {
 public:
  explicit ZipArchiveHandleScope(ZipArchiveHandle* handle) : handle_(handle) {}
  ~ZipArchiveHandleScope() { CloseArchive(*(handle_.release())); }

 private:
  std::unique_ptr<ZipArchiveHandle> handle_;
};

// Returns true if `str` ends with `suffix`.
inline bool EndsWith(std::string const& str, std::string const& suffix) {
  if (suffix.size() > str.size()) {
    return false;
  }
  return std::equal(suffix.rbegin(), suffix.rend(), str.rbegin());
}

// Tests that we can verify dex files without crashing.
TEST_F(FuzzerCorpusTest, VerifyCorpusDexFiles) {
  // These dex files are expected to pass verification. The others are regressions tests.
  const std::unordered_set<std::string> valid_dex_files = {"Main.dex", "hello_world.dex"};

  // Consistency checks.
  const std::string folder = android::base::GetExecutableDirectory();
  ASSERT_TRUE(std::filesystem::is_directory(folder)) << folder << " is not a folder";
  ASSERT_FALSE(std::filesystem::is_empty(folder)) << " No files found for directory " << folder;

  const std::string filename = folder + "/fuzzer_corpus.zip";

  // Iterate using ZipArchiveHandle. We have to be careful about managing the pointers with
  // CloseArchive, StartIteration, and EndIteration.
  std::string error_msg;
  ZipArchiveHandle handle;
  ZipArchiveHandleScope scope(&handle);
  int32_t error = OpenArchive(filename.c_str(), &handle);
  ASSERT_TRUE(error == 0) << "Error: " << error;

  void* cookie;
  error = StartIteration(handle, &cookie);
  ASSERT_TRUE(error == 0) << "couldn't iterate " << filename << " : " << ErrorCodeString(error);

  ZipEntry64 entry;
  std::string name;
  std::vector<char> data;
  while ((error = Next(cookie, &entry, &name)) >= 0) {
    if (!EndsWith(name, ".dex")) {
      // Skip non-DEX files.
      LOG(WARNING) << "Found a non-dex file: " << name;
      continue;
    }
    data.resize(entry.uncompressed_length);
    error = ExtractToMemory(handle, &entry, reinterpret_cast<uint8_t*>(data.data()), data.size());
    ASSERT_TRUE(error == 0) << "failed to extract entry: " << name << " from " << filename << ""
                            << ErrorCodeString(error);

    const bool expected_success = valid_dex_files.find(name) != valid_dex_files.end();
    VerifyDexFile(
        reinterpret_cast<const uint8_t*>(data.data()), data.size(), name.c_str(), expected_success);
  }

  ASSERT_TRUE(error >= -1) << "failed iterating " << filename << " : " << ErrorCodeString(error);
  EndIteration(cookie);
}

}  // namespace art
