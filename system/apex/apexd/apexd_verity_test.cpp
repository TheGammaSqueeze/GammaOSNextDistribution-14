/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include "apexd_verity.h"

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/result-gmock.h>
#include <android-base/stringprintf.h>
#include <errno.h>
#include <gtest/gtest.h>
#include <sys/stat.h>

#include <string>

#include "apex_file.h"
#include "apexd_test_utils.h"

namespace android {
namespace apex {

using namespace std::literals;

using android::base::GetExecutableDirectory;
using android::base::ReadFileToString;
using android::base::StringPrintf;
using android::base::testing::Ok;
using ::testing::Not;

static std::string GetTestDataDir() { return GetExecutableDirectory(); }
static std::string GetTestFile(const std::string& name) {
  return GetTestDataDir() + "/" + name;
}

TEST(ApexdVerityTest, ReusesHashtree) {
  TemporaryDir td;

  auto apex = ApexFile::Open(GetTestFile("apex.apexd_test_no_hashtree.apex"));
  ASSERT_RESULT_OK(apex);
  auto verity_data = apex->VerifyApexVerity(apex->GetBundledPublicKey());
  ASSERT_RESULT_OK(verity_data);

  auto hashtree_file = StringPrintf("%s/hashtree", td.path);
  auto status = PrepareHashTree(*apex, *verity_data, hashtree_file);
  ASSERT_RESULT_OK(status);
  ASSERT_EQ(KRegenerate, *status);

  std::string first_hashtree;
  ASSERT_TRUE(ReadFileToString(hashtree_file, &first_hashtree))
      << "Failed to read " << hashtree_file;

  // Now call PrepareHashTree again. Since digest matches, hashtree should be
  // reused.
  status = PrepareHashTree(*apex, *verity_data, hashtree_file);
  ASSERT_RESULT_OK(status);
  ASSERT_EQ(kReuse, *status);

  std::string second_hashtree;
  ASSERT_TRUE(ReadFileToString(hashtree_file, &second_hashtree))
      << "Failed to read " << hashtree_file;

  // Hashtree file shouldn't be modified.
  ASSERT_EQ(first_hashtree, second_hashtree)
      << hashtree_file << " was regenerated";
}

TEST(ApexdVerityTest, RegenerateHashree) {
  TemporaryDir td;

  auto apex = ApexFile::Open(GetTestFile("apex.apexd_test_no_hashtree.apex"));
  ASSERT_RESULT_OK(apex);
  auto verity_data = apex->VerifyApexVerity(apex->GetBundledPublicKey());
  ASSERT_RESULT_OK(verity_data);

  auto hashtree_file = StringPrintf("%s/hashtree", td.path);
  auto status = PrepareHashTree(*apex, *verity_data, hashtree_file);
  ASSERT_RESULT_OK(status);
  ASSERT_EQ(KRegenerate, *status);

  std::string first_hashtree;
  ASSERT_TRUE(ReadFileToString(hashtree_file, &first_hashtree))
      << "Failed to read " << hashtree_file;

  auto apex2 =
      ApexFile::Open(GetTestFile("apex.apexd_test_no_hashtree_2.apex"));
  ASSERT_RESULT_OK(apex2);
  auto verity_data2 = apex2->VerifyApexVerity(apex2->GetBundledPublicKey());
  ASSERT_RESULT_OK(verity_data2);

  // Now call PrepareHashTree again. Since digest doesn't match, hashtree
  // should be regenerated.
  status = PrepareHashTree(*apex2, *verity_data2, hashtree_file);
  ASSERT_RESULT_OK(status);
  ASSERT_EQ(KRegenerate, *status);

  std::string second_hashtree;
  ASSERT_TRUE(ReadFileToString(hashtree_file, &second_hashtree))
      << "Failed to read " << hashtree_file;

  // Hashtree file should be regenerated.
  ASSERT_NE(first_hashtree, second_hashtree) << hashtree_file << " was reused";
}

TEST(ApexdVerityTest, CannotPrepareHashTreeForCompressedApex) {
  TemporaryDir td;

  auto apex =
      ApexFile::Open(GetTestFile("com.android.apex.compressed.v1.capex"));
  ASSERT_RESULT_OK(apex);
  std::string hash_tree;
  ApexVerityData verity_data;
  auto result = PrepareHashTree(*apex, verity_data, hash_tree);
  ASSERT_THAT(result, Not(Ok()));
  ASSERT_THAT(
      result.error().message(),
      ::testing::HasSubstr("Cannot prepare HashTree of compressed APEX"));
}

}  // namespace apex
}  // namespace android
