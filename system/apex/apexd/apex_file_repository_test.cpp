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

#include "apex_file_repository.h"

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/properties.h>
#include <android-base/result-gmock.h>
#include <android-base/stringprintf.h>
#include <errno.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <microdroid/metadata.h>
#include <sys/stat.h>

#include <filesystem>
#include <string>

#include "apex_file.h"
#include "apexd_test_utils.h"
#include "apexd_verity.h"

namespace android {
namespace apex {

using namespace std::literals;

namespace fs = std::filesystem;

using android::apex::testing::ApexFileEq;
using android::base::GetExecutableDirectory;
using android::base::StringPrintf;
using android::base::testing::Ok;
using ::testing::ByRef;
using ::testing::Not;
using ::testing::UnorderedElementsAre;

static std::string GetTestDataDir() { return GetExecutableDirectory(); }
static std::string GetTestFile(const std::string& name) {
  return GetTestDataDir() + "/" + name;
}

namespace {
// Copies the compressed apex to |built_in_dir| and decompresses it to
// |decompression_dir
void PrepareCompressedApex(const std::string& name,
                           const std::string& built_in_dir,
                           const std::string& decompression_dir) {
  fs::copy(GetTestFile(name), built_in_dir);
  auto compressed_apex =
      ApexFile::Open(StringPrintf("%s/%s", built_in_dir.c_str(), name.c_str()));

  const auto& pkg_name = compressed_apex->GetManifest().name();
  const int version = compressed_apex->GetManifest().version();

  auto decompression_path =
      StringPrintf("%s/%s@%d%s", decompression_dir.c_str(), pkg_name.c_str(),
                   version, kDecompressedApexPackageSuffix);
  compressed_apex->Decompress(decompression_path);
}
}  // namespace

TEST(ApexFileRepositoryTest, InitializeSuccess) {
  // Prepare test data.
  TemporaryDir built_in_dir, data_dir, decompression_dir;
  fs::copy(GetTestFile("apex.apexd_test.apex"), built_in_dir.path);
  fs::copy(GetTestFile("apex.apexd_test_different_app.apex"),
           built_in_dir.path);

  fs::copy(GetTestFile("apex.apexd_test.apex"), data_dir.path);
  fs::copy(GetTestFile("apex.apexd_test_different_app.apex"), data_dir.path);

  ApexFileRepository instance;
  ASSERT_RESULT_OK(instance.AddPreInstalledApex({built_in_dir.path}));
  ASSERT_RESULT_OK(instance.AddDataApex(data_dir.path));

  // Now test that apexes were scanned correctly;
  auto test_fn = [&](const std::string& apex_name) {
    auto apex = ApexFile::Open(GetTestFile(apex_name));
    ASSERT_RESULT_OK(apex);

    {
      auto ret = instance.GetPublicKey(apex->GetManifest().name());
      ASSERT_RESULT_OK(ret);
      ASSERT_EQ(apex->GetBundledPublicKey(), *ret);
    }

    {
      auto ret = instance.GetPreinstalledPath(apex->GetManifest().name());
      ASSERT_RESULT_OK(ret);
      ASSERT_EQ(StringPrintf("%s/%s", built_in_dir.path, apex_name.c_str()),
                *ret);
    }

    {
      auto ret = instance.GetDataPath(apex->GetManifest().name());
      ASSERT_RESULT_OK(ret);
      ASSERT_EQ(StringPrintf("%s/%s", data_dir.path, apex_name.c_str()), *ret);
    }

    ASSERT_TRUE(instance.HasPreInstalledVersion(apex->GetManifest().name()));
    ASSERT_TRUE(instance.HasDataVersion(apex->GetManifest().name()));
  };

  test_fn("apex.apexd_test.apex");
  test_fn("apex.apexd_test_different_app.apex");

  // Check that second call will succeed as well.
  ASSERT_RESULT_OK(instance.AddPreInstalledApex({built_in_dir.path}));
  ASSERT_RESULT_OK(instance.AddDataApex(data_dir.path));

  test_fn("apex.apexd_test.apex");
  test_fn("apex.apexd_test_different_app.apex");
}

TEST(ApexFileRepositoryTest, InitializeFailureCorruptApex) {
  // Prepare test data.
  TemporaryDir td;
  fs::copy(GetTestFile("apex.apexd_test.apex"), td.path);
  fs::copy(GetTestFile("apex.apexd_test_corrupt_superblock_apex.apex"),
           td.path);

  ApexFileRepository instance;
  ASSERT_THAT(instance.AddPreInstalledApex({td.path}), Not(Ok()));
}

TEST(ApexFileRepositoryTest, InitializeCompressedApexWithoutApex) {
  // Prepare test data.
  TemporaryDir td;
  fs::copy(GetTestFile("com.android.apex.compressed.v1_without_apex.capex"),
           td.path);

  ApexFileRepository instance;
  // Compressed APEX without APEX cannot be opened
  ASSERT_THAT(instance.AddPreInstalledApex({td.path}), Not(Ok()));
}

TEST(ApexFileRepositoryTest, InitializeSameNameDifferentPathAborts) {
  // Prepare test data.
  TemporaryDir td;
  fs::copy(GetTestFile("apex.apexd_test.apex"), td.path);
  fs::copy(GetTestFile("apex.apexd_test.apex"),
           StringPrintf("%s/other.apex", td.path));

  ASSERT_DEATH(
      {
        ApexFileRepository instance;
        instance.AddPreInstalledApex({td.path});
      },
      "");
}

TEST(ApexFileRepositoryTest, InitializeMultiInstalledSuccess) {
  // Prepare test data.
  TemporaryDir td;
  std::string apex_file = GetTestFile("apex.apexd_test.apex");
  fs::copy(apex_file, StringPrintf("%s/version_a.apex", td.path));
  fs::copy(apex_file, StringPrintf("%s/version_b.apex", td.path));
  std::string apex_name = ApexFile::Open(apex_file)->GetManifest().name();

  std::string persist_prefix = "debug.apexd.test.persistprefix.";
  std::string bootconfig_prefix = "debug.apexd.test.bootconfigprefix.";
  ApexFileRepository instance(/*enforce_multi_install_partition=*/false,
                              /*multi_install_select_prop_prefixes=*/{
                                  persist_prefix, bootconfig_prefix});

  auto test_fn = [&](const std::string& selected_filename) {
    ASSERT_RESULT_OK(instance.AddPreInstalledApex({td.path}));
    auto ret = instance.GetPreinstalledPath(apex_name);
    ASSERT_RESULT_OK(ret);
    ASSERT_EQ(StringPrintf("%s/%s", td.path, selected_filename.c_str()), *ret);
    instance.Reset();
  };

  // Start with version_a in bootconfig.
  android::base::SetProperty(bootconfig_prefix + apex_name, "version_a.apex");
  test_fn("version_a.apex");
  // Developer chooses version_b with persist prop.
  android::base::SetProperty(persist_prefix + apex_name, "version_b.apex");
  test_fn("version_b.apex");
  // Developer goes back to version_a with persist prop.
  android::base::SetProperty(persist_prefix + apex_name, "version_a.apex");
  test_fn("version_a.apex");

  android::base::SetProperty(persist_prefix + apex_name, "");
  android::base::SetProperty(bootconfig_prefix + apex_name, "");
}

TEST(ApexFileRepositoryTest, InitializeMultiInstalledSkipsForDifferingKeys) {
  // Prepare test data.
  TemporaryDir td;
  fs::copy(GetTestFile("apex.apexd_test.apex"),
           StringPrintf("%s/version_a.apex", td.path));
  fs::copy(GetTestFile("apex.apexd_test_different_key.apex"),
           StringPrintf("%s/version_b.apex", td.path));
  std::string apex_name =
      ApexFile::Open(GetTestFile("apex.apexd_test.apex"))->GetManifest().name();
  std::string prop_prefix = "debug.apexd.test.bootconfigprefix.";
  std::string prop = prop_prefix + apex_name;
  android::base::SetProperty(prop, "version_a.apex");

  ApexFileRepository instance(
      /*enforce_multi_install_partition=*/false,
      /*multi_install_select_prop_prefixes=*/{prop_prefix});
  ASSERT_RESULT_OK(instance.AddPreInstalledApex({td.path}));
  // Neither version should be have been installed.
  ASSERT_THAT(instance.GetPreinstalledPath(apex_name), Not(Ok()));

  android::base::SetProperty(prop, "");
}

TEST(ApexFileRepositoryTest, InitializeMultiInstalledSkipsForInvalidPartition) {
  // Prepare test data.
  TemporaryDir td;
  // Note: These test files are on /data, which is not a valid partition for
  // multi-installed APEXes.
  fs::copy(GetTestFile("apex.apexd_test.apex"),
           StringPrintf("%s/version_a.apex", td.path));
  fs::copy(GetTestFile("apex.apexd_test.apex"),
           StringPrintf("%s/version_b.apex", td.path));
  std::string apex_name =
      ApexFile::Open(GetTestFile("apex.apexd_test.apex"))->GetManifest().name();
  std::string prop_prefix = "debug.apexd.test.bootconfigprefix.";
  std::string prop = prop_prefix + apex_name;
  android::base::SetProperty(prop, "version_a.apex");

  ApexFileRepository instance(
      /*enforce_multi_install_partition=*/true,
      /*multi_install_select_prop_prefixes=*/{prop_prefix});
  ASSERT_RESULT_OK(instance.AddPreInstalledApex({td.path}));
  // Neither version should be have been installed.
  ASSERT_THAT(instance.GetPreinstalledPath(apex_name), Not(Ok()));

  android::base::SetProperty(prop, "");
}

TEST(ApexFileRepositoryTest,
     InitializeSameNameDifferentPathAbortsCompressedApex) {
  // Prepare test data.
  TemporaryDir td;
  fs::copy(GetTestFile("com.android.apex.compressed.v1.capex"), td.path);
  fs::copy(GetTestFile("com.android.apex.compressed.v1.capex"),
           StringPrintf("%s/other.capex", td.path));

  ASSERT_DEATH(
      {
        ApexFileRepository instance;
        instance.AddPreInstalledApex({td.path});
      },
      "");
}

TEST(ApexFileRepositoryTest, InitializePublicKeyUnexpectdlyChangedAborts) {
  // Prepare test data.
  TemporaryDir td;
  fs::copy(GetTestFile("apex.apexd_test.apex"), td.path);

  ApexFileRepository instance;
  ASSERT_RESULT_OK(instance.AddPreInstalledApex({td.path}));

  // Check that apex was loaded.
  auto path = instance.GetPreinstalledPath("com.android.apex.test_package");
  ASSERT_RESULT_OK(path);
  ASSERT_EQ(StringPrintf("%s/apex.apexd_test.apex", td.path), *path);

  auto public_key = instance.GetPublicKey("com.android.apex.test_package");
  ASSERT_RESULT_OK(public_key);

  // Substitute it with another apex with the same name, but different public
  // key.
  fs::copy(GetTestFile("apex.apexd_test_different_key.apex"), *path,
           fs::copy_options::overwrite_existing);

  {
    auto apex = ApexFile::Open(*path);
    ASSERT_RESULT_OK(apex);
    // Check module name hasn't changed.
    ASSERT_EQ("com.android.apex.test_package", apex->GetManifest().name());
    // Check public key has changed.
    ASSERT_NE(*public_key, apex->GetBundledPublicKey());
  }

  ASSERT_DEATH({ instance.AddPreInstalledApex({td.path}); }, "");
}

TEST(ApexFileRepositoryTest,
     InitializePublicKeyUnexpectdlyChangedAbortsCompressedApex) {
  // Prepare test data.
  TemporaryDir td;
  fs::copy(GetTestFile("com.android.apex.compressed.v1.capex"), td.path);

  ApexFileRepository instance;
  ASSERT_RESULT_OK(instance.AddPreInstalledApex({td.path}));

  // Check that apex was loaded.
  auto path = instance.GetPreinstalledPath("com.android.apex.compressed");
  ASSERT_RESULT_OK(path);
  ASSERT_EQ(StringPrintf("%s/com.android.apex.compressed.v1.capex", td.path),
            *path);

  auto public_key = instance.GetPublicKey("com.android.apex.compressed");
  ASSERT_RESULT_OK(public_key);

  // Substitute it with another apex with the same name, but different public
  // key.
  fs::copy(GetTestFile("com.android.apex.compressed_different_key.capex"),
           *path, fs::copy_options::overwrite_existing);

  {
    auto apex = ApexFile::Open(*path);
    ASSERT_RESULT_OK(apex);
    // Check module name hasn't changed.
    ASSERT_EQ("com.android.apex.compressed", apex->GetManifest().name());
    // Check public key has changed.
    ASSERT_NE(*public_key, apex->GetBundledPublicKey());
  }

  ASSERT_DEATH({ instance.AddPreInstalledApex({td.path}); }, "");
}

TEST(ApexFileRepositoryTest, IsPreInstalledApex) {
  // Prepare test data.
  TemporaryDir td;
  fs::copy(GetTestFile("apex.apexd_test.apex"), td.path);
  fs::copy(GetTestFile("com.android.apex.compressed.v1.capex"), td.path);

  ApexFileRepository instance;
  ASSERT_RESULT_OK(instance.AddPreInstalledApex({td.path}));

  auto compressed_apex = ApexFile::Open(
      StringPrintf("%s/com.android.apex.compressed.v1.capex", td.path));
  ASSERT_RESULT_OK(compressed_apex);
  ASSERT_TRUE(instance.IsPreInstalledApex(*compressed_apex));

  auto apex1 = ApexFile::Open(StringPrintf("%s/apex.apexd_test.apex", td.path));
  ASSERT_RESULT_OK(apex1);
  ASSERT_TRUE(instance.IsPreInstalledApex(*apex1));

  // It's same apex, but path is different. Shouldn't be treated as
  // pre-installed.
  auto apex2 = ApexFile::Open(GetTestFile("apex.apexd_test.apex"));
  ASSERT_RESULT_OK(apex2);
  ASSERT_FALSE(instance.IsPreInstalledApex(*apex2));

  auto apex3 =
      ApexFile::Open(GetTestFile("apex.apexd_test_different_app.apex"));
  ASSERT_RESULT_OK(apex3);
  ASSERT_FALSE(instance.IsPreInstalledApex(*apex3));
}

TEST(ApexFileRepositoryTest, IsDecompressedApex) {
  // Prepare instance
  TemporaryDir decompression_dir;
  ApexFileRepository instance(decompression_dir.path);

  // Prepare decompressed apex
  std::string filename = "com.android.apex.compressed.v1_original.apex";
  fs::copy(GetTestFile(filename), decompression_dir.path);
  auto decompressed_path =
      StringPrintf("%s/%s", decompression_dir.path, filename.c_str());
  auto decompressed_apex = ApexFile::Open(decompressed_path);

  // Any file which is already located in |decompression_dir| should be
  // considered decompressed
  ASSERT_TRUE(instance.IsDecompressedApex(*decompressed_apex));

  // Hard links with same file name is not considered decompressed
  TemporaryDir active_dir;
  auto active_path = StringPrintf("%s/%s", active_dir.path, filename.c_str());
  std::error_code ec;
  fs::create_hard_link(decompressed_path, active_path, ec);
  ASSERT_FALSE(ec) << "Failed to create hardlink";
  auto active_apex = ApexFile::Open(active_path);
  ASSERT_FALSE(instance.IsDecompressedApex(*active_apex));
}

TEST(ApexFileRepositoryTest, AddAndGetDataApex) {
  // Prepare test data.
  TemporaryDir built_in_dir, data_dir, decompression_dir;
  fs::copy(GetTestFile("apex.apexd_test.apex"), built_in_dir.path);
  fs::copy(GetTestFile("apex.apexd_test_v2.apex"), data_dir.path);
  PrepareCompressedApex("com.android.apex.compressed.v1.capex",
                        built_in_dir.path, decompression_dir.path);
  // Add a data apex that has kDecompressedApexPackageSuffix
  fs::copy(GetTestFile("com.android.apex.compressed.v1_original.apex"),
           StringPrintf("%s/com.android.apex.compressed@1%s", data_dir.path,
                        kDecompressedApexPackageSuffix));

  ApexFileRepository instance(decompression_dir.path);
  ASSERT_RESULT_OK(instance.AddPreInstalledApex({built_in_dir.path}));
  ASSERT_RESULT_OK(instance.AddDataApex(data_dir.path));

  // ApexFileRepository should only deal with APEX in /data/apex/active.
  // Decompressed APEX should not be included
  auto data_apexs = instance.GetDataApexFiles();
  auto normal_apex =
      ApexFile::Open(StringPrintf("%s/apex.apexd_test_v2.apex", data_dir.path));
  ASSERT_THAT(data_apexs,
              UnorderedElementsAre(ApexFileEq(ByRef(*normal_apex))));
}

TEST(ApexFileRepositoryTest, AddDataApexIgnoreCompressedApex) {
  // Prepare test data.
  TemporaryDir data_dir, decompression_dir;
  fs::copy(GetTestFile("com.android.apex.compressed.v1.capex"), data_dir.path);

  ApexFileRepository instance;
  ASSERT_RESULT_OK(instance.AddDataApex(data_dir.path));

  auto data_apexs = instance.GetDataApexFiles();
  ASSERT_EQ(data_apexs.size(), 0u);
}

TEST(ApexFileRepositoryTest, AddDataApexIgnoreIfNotPreInstalled) {
  // Prepare test data.
  TemporaryDir data_dir, decompression_dir;
  fs::copy(GetTestFile("apex.apexd_test.apex"), data_dir.path);

  ApexFileRepository instance;
  ASSERT_RESULT_OK(instance.AddDataApex(data_dir.path));

  auto data_apexs = instance.GetDataApexFiles();
  ASSERT_EQ(data_apexs.size(), 0u);
}

TEST(ApexFileRepositoryTest, AddDataApexPrioritizeHigherVersionApex) {
  // Prepare test data.
  TemporaryDir built_in_dir, data_dir, decompression_dir;
  fs::copy(GetTestFile("apex.apexd_test.apex"), built_in_dir.path);
  fs::copy(GetTestFile("apex.apexd_test.apex"), data_dir.path);
  fs::copy(GetTestFile("apex.apexd_test_v2.apex"), data_dir.path);

  ApexFileRepository instance;
  ASSERT_RESULT_OK(instance.AddPreInstalledApex({built_in_dir.path}));
  ASSERT_RESULT_OK(instance.AddDataApex(data_dir.path));

  auto data_apexs = instance.GetDataApexFiles();
  auto normal_apex =
      ApexFile::Open(StringPrintf("%s/apex.apexd_test_v2.apex", data_dir.path));
  ASSERT_THAT(data_apexs,
              UnorderedElementsAre(ApexFileEq(ByRef(*normal_apex))));
}

TEST(ApexFileRepositoryTest, AddDataApexDoesNotScanDecompressedApex) {
  // Prepare test data.
  TemporaryDir built_in_dir, data_dir, decompression_dir;
  PrepareCompressedApex("com.android.apex.compressed.v1.capex",
                        built_in_dir.path, decompression_dir.path);

  ApexFileRepository instance(decompression_dir.path);
  ASSERT_RESULT_OK(instance.AddPreInstalledApex({built_in_dir.path}));
  ASSERT_RESULT_OK(instance.AddDataApex(data_dir.path));

  auto data_apexs = instance.GetDataApexFiles();
  ASSERT_EQ(data_apexs.size(), 0u);
}

TEST(ApexFileRepositoryTest, AddDataApexIgnoreWrongPublicKey) {
  // Prepare test data.
  TemporaryDir built_in_dir, data_dir, decompression_dir;
  fs::copy(GetTestFile("apex.apexd_test.apex"), built_in_dir.path);
  fs::copy(GetTestFile("apex.apexd_test_different_key.apex"), data_dir.path);

  ApexFileRepository instance;
  ASSERT_RESULT_OK(instance.AddPreInstalledApex({built_in_dir.path}));
  ASSERT_RESULT_OK(instance.AddDataApex(data_dir.path));

  auto data_apexs = instance.GetDataApexFiles();
  ASSERT_EQ(data_apexs.size(), 0u);
}

TEST(ApexFileRepositoryTest, GetPreInstalledApexFiles) {
  // Prepare test data.
  TemporaryDir built_in_dir;
  fs::copy(GetTestFile("apex.apexd_test.apex"), built_in_dir.path);
  fs::copy(GetTestFile("com.android.apex.compressed.v1.capex"),
           built_in_dir.path);

  ApexFileRepository instance;
  ASSERT_RESULT_OK(instance.AddPreInstalledApex({built_in_dir.path}));

  auto pre_installed_apexs = instance.GetPreInstalledApexFiles();
  auto pre_apex_1 = ApexFile::Open(
      StringPrintf("%s/apex.apexd_test.apex", built_in_dir.path));
  auto pre_apex_2 = ApexFile::Open(StringPrintf(
      "%s/com.android.apex.compressed.v1.capex", built_in_dir.path));
  ASSERT_THAT(pre_installed_apexs,
              UnorderedElementsAre(ApexFileEq(ByRef(*pre_apex_1)),
                                   ApexFileEq(ByRef(*pre_apex_2))));
}

TEST(ApexFileRepositoryTest, AllApexFilesByName) {
  TemporaryDir built_in_dir, decompression_dir;
  fs::copy(GetTestFile("apex.apexd_test.apex"), built_in_dir.path);
  fs::copy(GetTestFile("com.android.apex.cts.shim.apex"), built_in_dir.path);
  fs::copy(GetTestFile("com.android.apex.compressed.v1.capex"),
           built_in_dir.path);
  ApexFileRepository instance;
  ASSERT_RESULT_OK(instance.AddPreInstalledApex({built_in_dir.path}));

  TemporaryDir data_dir;
  fs::copy(GetTestFile("com.android.apex.cts.shim.v2.apex"), data_dir.path);
  ASSERT_RESULT_OK(instance.AddDataApex(data_dir.path));

  auto result = instance.AllApexFilesByName();

  // Verify the contents of result
  auto apexd_test_file = ApexFile::Open(
      StringPrintf("%s/apex.apexd_test.apex", built_in_dir.path));
  auto shim_v1 = ApexFile::Open(
      StringPrintf("%s/com.android.apex.cts.shim.apex", built_in_dir.path));
  auto compressed_apex = ApexFile::Open(StringPrintf(
      "%s/com.android.apex.compressed.v1.capex", built_in_dir.path));
  auto shim_v2 = ApexFile::Open(
      StringPrintf("%s/com.android.apex.cts.shim.v2.apex", data_dir.path));

  ASSERT_EQ(result.size(), 3u);
  ASSERT_THAT(result[apexd_test_file->GetManifest().name()],
              UnorderedElementsAre(ApexFileEq(ByRef(*apexd_test_file))));
  ASSERT_THAT(result[shim_v1->GetManifest().name()],
              UnorderedElementsAre(ApexFileEq(ByRef(*shim_v1)),
                                   ApexFileEq(ByRef(*shim_v2))));
  ASSERT_THAT(result[compressed_apex->GetManifest().name()],
              UnorderedElementsAre(ApexFileEq(ByRef(*compressed_apex))));
}

TEST(ApexFileRepositoryTest, GetDataApex) {
  // Prepare test data.
  TemporaryDir built_in_dir, data_dir;
  fs::copy(GetTestFile("apex.apexd_test.apex"), built_in_dir.path);
  fs::copy(GetTestFile("apex.apexd_test_v2.apex"), data_dir.path);

  ApexFileRepository instance;
  ASSERT_RESULT_OK(instance.AddPreInstalledApex({built_in_dir.path}));
  ASSERT_RESULT_OK(instance.AddDataApex(data_dir.path));

  auto apex =
      ApexFile::Open(StringPrintf("%s/apex.apexd_test_v2.apex", data_dir.path));
  ASSERT_RESULT_OK(apex);

  auto ret = instance.GetDataApex("com.android.apex.test_package");
  ASSERT_THAT(ret, ApexFileEq(ByRef(*apex)));
}

TEST(ApexFileRepositoryTest, GetDataApexNoSuchApexAborts) {
  ASSERT_DEATH(
      {
        ApexFileRepository instance;
        instance.GetDataApex("whatever");
      },
      "");
}

TEST(ApexFileRepositoryTest, GetPreInstalledApex) {
  // Prepare test data.
  TemporaryDir built_in_dir;
  fs::copy(GetTestFile("apex.apexd_test.apex"), built_in_dir.path);

  ApexFileRepository instance;
  ASSERT_RESULT_OK(instance.AddPreInstalledApex({built_in_dir.path}));

  auto apex = ApexFile::Open(
      StringPrintf("%s/apex.apexd_test.apex", built_in_dir.path));
  ASSERT_RESULT_OK(apex);

  auto ret = instance.GetPreInstalledApex("com.android.apex.test_package");
  ASSERT_THAT(ret, ApexFileEq(ByRef(*apex)));
}

TEST(ApexFileRepositoryTest, GetPreInstalledApexNoSuchApexAborts) {
  ASSERT_DEATH(
      {
        ApexFileRepository instance;
        instance.GetPreInstalledApex("whatever");
      },
      "");
}

struct ApexFileRepositoryTestAddBlockApex : public ::testing::Test {
  TemporaryDir test_dir;

  struct ApexMetadata {
    std::string public_key;
    std::string root_digest;
    int64_t last_update_seconds;
    bool is_factory = true;
    int64_t manifest_version;
    std::string manifest_name;
  };

  struct PayloadMetadata {
    android::microdroid::Metadata metadata;
    std::string path;
    PayloadMetadata(const std::string& path) : path(path) {}
    PayloadMetadata& apex(const std::string& name) {
      return apex(name, ApexMetadata{});
    }
    PayloadMetadata& apex(const std::string& name,
                          const ApexMetadata& apex_metadata) {
      auto apex = metadata.add_apexes();
      apex->set_name(name);
      apex->set_public_key(apex_metadata.public_key);
      apex->set_root_digest(apex_metadata.root_digest);
      apex->set_last_update_seconds(apex_metadata.last_update_seconds);
      apex->set_is_factory(apex_metadata.is_factory);
      apex->set_manifest_version(apex_metadata.manifest_version);
      apex->set_manifest_name(apex_metadata.manifest_name);
      return *this;
    }
    ~PayloadMetadata() {
      metadata.set_version(1);
      std::ofstream out(path);
      android::microdroid::WriteMetadata(metadata, out);
    }
  };
};

TEST_F(ApexFileRepositoryTestAddBlockApex,
       ScansPayloadDisksAndAddApexFilesToPreInstalled) {
  // prepare payload disk
  //  <test-dir>/vdc1 : metadata
  //            /vdc2 : apex.apexd_test.apex
  //            /vdc3 : apex.apexd_test_different_app.apex

  const auto& test_apex_foo = GetTestFile("apex.apexd_test.apex");
  const auto& test_apex_bar = GetTestFile("apex.apexd_test_different_app.apex");

  const std::string metadata_partition_path = test_dir.path + "/vdc1"s;
  const std::string apex_foo_path = test_dir.path + "/vdc2"s;
  const std::string apex_bar_path = test_dir.path + "/vdc3"s;

  PayloadMetadata(metadata_partition_path)
      .apex(test_apex_foo)
      .apex(test_apex_bar);
  auto block_apex1 = WriteBlockApex(test_apex_foo, apex_foo_path);
  auto block_apex2 = WriteBlockApex(test_apex_bar, apex_bar_path);

  // call ApexFileRepository::AddBlockApex()
  ApexFileRepository instance;
  auto status = instance.AddBlockApex(metadata_partition_path);
  ASSERT_RESULT_OK(status);

  auto apex_foo = ApexFile::Open(apex_foo_path);
  ASSERT_RESULT_OK(apex_foo);
  // block apexes can be identified with IsBlockApex
  ASSERT_TRUE(instance.IsBlockApex(*apex_foo));

  // "block" apexes are treated as "pre-installed"
  auto ret_foo = instance.GetPreInstalledApex("com.android.apex.test_package");
  ASSERT_THAT(ret_foo, ApexFileEq(ByRef(*apex_foo)));

  auto apex_bar = ApexFile::Open(apex_bar_path);
  ASSERT_RESULT_OK(apex_bar);
  auto ret_bar =
      instance.GetPreInstalledApex("com.android.apex.test_package_2");
  ASSERT_THAT(ret_bar, ApexFileEq(ByRef(*apex_bar)));
}

TEST_F(ApexFileRepositoryTestAddBlockApex,
       ScansOnlySpecifiedInMetadataPartition) {
  // prepare payload disk
  //  <test-dir>/vdc1 : metadata with apex.apexd_test.apex only
  //            /vdc2 : apex.apexd_test.apex
  //            /vdc3 : apex.apexd_test_different_app.apex

  const auto& test_apex_foo = GetTestFile("apex.apexd_test.apex");
  const auto& test_apex_bar = GetTestFile("apex.apexd_test_different_app.apex");

  const std::string metadata_partition_path = test_dir.path + "/vdc1"s;
  const std::string apex_foo_path = test_dir.path + "/vdc2"s;
  const std::string apex_bar_path = test_dir.path + "/vdc3"s;

  // metadata lists only "foo"
  PayloadMetadata(metadata_partition_path).apex(test_apex_foo);
  auto block_apex1 = WriteBlockApex(test_apex_foo, apex_foo_path);
  auto block_apex2 = WriteBlockApex(test_apex_bar, apex_bar_path);

  // call ApexFileRepository::AddBlockApex()
  ApexFileRepository instance;
  auto status = instance.AddBlockApex(metadata_partition_path);
  ASSERT_RESULT_OK(status);

  // foo is added, but bar is not
  auto ret_foo = instance.GetPreinstalledPath("com.android.apex.test_package");
  ASSERT_RESULT_OK(ret_foo);
  ASSERT_EQ(apex_foo_path, *ret_foo);
  auto ret_bar =
      instance.GetPreinstalledPath("com.android.apex.test_package_2");
  ASSERT_THAT(ret_bar, Not(Ok()));
}

TEST_F(ApexFileRepositoryTestAddBlockApex, FailsWhenTheresDuplicateNames) {
  // prepare payload disk
  //  <test-dir>/vdc1 : metadata with v1 and v2 of apex.apexd_test
  //            /vdc2 : apex.apexd_test.apex
  //            /vdc3 : apex.apexd_test_v2.apex

  const auto& test_apex_foo = GetTestFile("apex.apexd_test.apex");
  const auto& test_apex_bar = GetTestFile("apex.apexd_test_v2.apex");

  const std::string metadata_partition_path = test_dir.path + "/vdc1"s;
  const std::string apex_foo_path = test_dir.path + "/vdc2"s;
  const std::string apex_bar_path = test_dir.path + "/vdc3"s;

  PayloadMetadata(metadata_partition_path)
      .apex(test_apex_foo)
      .apex(test_apex_bar);
  auto block_apex1 = WriteBlockApex(test_apex_foo, apex_foo_path);
  auto block_apex2 = WriteBlockApex(test_apex_bar, apex_bar_path);

  ApexFileRepository instance;
  auto status = instance.AddBlockApex(metadata_partition_path);
  ASSERT_THAT(status, Not(Ok()));
}

TEST_F(ApexFileRepositoryTestAddBlockApex, GetBlockApexRootDigest) {
  // prepare payload disk with root digest
  //  <test-dir>/vdc1 : metadata with apex.apexd_test.apex only
  //            /vdc2 : apex.apexd_test.apex

  const auto& test_apex_foo = GetTestFile("apex.apexd_test.apex");

  const std::string metadata_partition_path = test_dir.path + "/vdc1"s;
  const std::string apex_foo_path = test_dir.path + "/vdc2"s;

  // root digest is stored as bytes in metadata and as hexadecimal in
  // ApexFileRepository
  const std::string root_digest = "root_digest";
  const std::string hex_root_digest = BytesToHex(
      reinterpret_cast<const uint8_t*>(root_digest.data()), root_digest.size());

  // metadata lists "foo"
  ApexMetadata apex_metadata;
  apex_metadata.root_digest = root_digest;
  PayloadMetadata(metadata_partition_path).apex(test_apex_foo, apex_metadata);
  auto block_apex1 = WriteBlockApex(test_apex_foo, apex_foo_path);

  // call ApexFileRepository::AddBlockApex()
  ApexFileRepository instance;
  auto status = instance.AddBlockApex(metadata_partition_path);
  ASSERT_RESULT_OK(status);

  ASSERT_EQ(hex_root_digest, instance.GetBlockApexRootDigest(apex_foo_path));
}

TEST_F(ApexFileRepositoryTestAddBlockApex, GetBlockApexLastUpdateSeconds) {
  // prepare payload disk with last update time
  //  <test-dir>/vdc1 : metadata with apex.apexd_test.apex only
  //            /vdc2 : apex.apexd_test.apex

  const auto& test_apex_foo = GetTestFile("apex.apexd_test.apex");

  const std::string metadata_partition_path = test_dir.path + "/vdc1"s;
  const std::string apex_foo_path = test_dir.path + "/vdc2"s;

  const int64_t last_update_seconds = 123456789;

  // metadata lists "foo"
  ApexMetadata apex_metadata;
  apex_metadata.last_update_seconds = last_update_seconds;
  PayloadMetadata(metadata_partition_path).apex(test_apex_foo, apex_metadata);
  auto block_apex1 = WriteBlockApex(test_apex_foo, apex_foo_path);

  // call ApexFileRepository::AddBlockApex()
  ApexFileRepository instance;
  auto status = instance.AddBlockApex(metadata_partition_path);
  ASSERT_RESULT_OK(status);

  ASSERT_EQ(last_update_seconds,
            instance.GetBlockApexLastUpdateSeconds(apex_foo_path));
}

TEST_F(ApexFileRepositoryTestAddBlockApex, SucceedsWhenMetadataMatches) {
  // prepare payload disk
  //  <test-dir>/vdc1 : metadata with apex.apexd_test.apex only
  //            /vdc2 : apex.apexd_test.apex

  const auto& test_apex_foo = GetTestFile("apex.apexd_test.apex");

  const std::string metadata_partition_path = test_dir.path + "/vdc1"s;
  const std::string apex_foo_path = test_dir.path + "/vdc2"s;

  std::string public_key;
  const auto& key_path =
      GetTestFile("apexd_testdata/com.android.apex.test_package.avbpubkey");
  ASSERT_TRUE(android::base::ReadFileToString(key_path, &public_key))
      << "Failed to read " << key_path;

  // metadata lists "foo"
  ApexMetadata apex_metadata;
  apex_metadata.public_key = public_key;
  apex_metadata.manifest_version = 1;
  apex_metadata.manifest_name = "com.android.apex.test_package";
  PayloadMetadata(metadata_partition_path).apex(test_apex_foo, apex_metadata);
  auto block_apex1 = WriteBlockApex(test_apex_foo, apex_foo_path);

  // call ApexFileRepository::AddBlockApex()
  ApexFileRepository instance;
  auto status = instance.AddBlockApex(metadata_partition_path);
  ASSERT_RESULT_OK(status);
}

TEST_F(ApexFileRepositoryTestAddBlockApex, VerifyPublicKeyWhenAddingBlockApex) {
  // prepare payload disk
  //  <test-dir>/vdc1 : metadata with apex.apexd_test.apex only
  //            /vdc2 : apex.apexd_test.apex

  const auto& test_apex_foo = GetTestFile("apex.apexd_test.apex");

  const std::string metadata_partition_path = test_dir.path + "/vdc1"s;
  const std::string apex_foo_path = test_dir.path + "/vdc2"s;

  // metadata lists "foo"
  ApexMetadata apex_metadata;
  apex_metadata.public_key = "wrong public key";
  PayloadMetadata(metadata_partition_path).apex(test_apex_foo, apex_metadata);
  auto block_apex1 = WriteBlockApex(test_apex_foo, apex_foo_path);

  // call ApexFileRepository::AddBlockApex()
  ApexFileRepository instance;
  auto status = instance.AddBlockApex(metadata_partition_path);
  ASSERT_THAT(status, Not(Ok()));
}

TEST_F(ApexFileRepositoryTestAddBlockApex,
       VerifyManifestVersionWhenAddingBlockApex) {
  // prepare payload disk
  //  <test-dir>/vdc1 : metadata with apex.apexd_test.apex only
  //            /vdc2 : apex.apexd_test.apex

  const auto& test_apex_foo = GetTestFile("apex.apexd_test.apex");

  const std::string metadata_partition_path = test_dir.path + "/vdc1"s;
  const std::string apex_foo_path = test_dir.path + "/vdc2"s;

  // metadata lists "foo"
  ApexMetadata apex_metadata;
  apex_metadata.manifest_version = 2;
  PayloadMetadata(metadata_partition_path).apex(test_apex_foo, apex_metadata);
  auto block_apex1 = WriteBlockApex(test_apex_foo, apex_foo_path);

  // call ApexFileRepository::AddBlockApex()
  ApexFileRepository instance;
  auto status = instance.AddBlockApex(metadata_partition_path);
  ASSERT_THAT(status, Not(Ok()));
}

TEST_F(ApexFileRepositoryTestAddBlockApex,
       VerifyManifestNameWhenAddingBlockApex) {
  // prepare payload disk
  //  <test-dir>/vdc1 : metadata with apex.apexd_test.apex only
  //            /vdc2 : apex.apexd_test.apex

  const auto& test_apex_foo = GetTestFile("apex.apexd_test.apex");

  const std::string metadata_partition_path = test_dir.path + "/vdc1"s;
  const std::string apex_foo_path = test_dir.path + "/vdc2"s;

  // metadata lists "foo"
  ApexMetadata apex_metadata;
  apex_metadata.manifest_name = "Wrong name";
  PayloadMetadata(metadata_partition_path).apex(test_apex_foo, apex_metadata);
  auto block_apex1 = WriteBlockApex(test_apex_foo, apex_foo_path);

  // call ApexFileRepository::AddBlockApex()
  ApexFileRepository instance;
  auto status = instance.AddBlockApex(metadata_partition_path);
  ASSERT_THAT(status, Not(Ok()));
}

TEST_F(ApexFileRepositoryTestAddBlockApex, RespectIsFactoryBitFromMetadata) {
  // prepare payload disk
  //  <test-dir>/vdc1 : metadata with apex.apexd_test.apex only
  //            /vdc2 : apex.apexd_test.apex

  const auto& test_apex_foo = GetTestFile("apex.apexd_test.apex");

  const std::string metadata_partition_path = test_dir.path + "/vdc1"s;
  const std::string apex_foo_path = test_dir.path + "/vdc2"s;
  auto block_apex1 = WriteBlockApex(test_apex_foo, apex_foo_path);

  for (const bool is_factory : {true, false}) {
    // metadata lists "foo"
    ApexMetadata apex_metadata;
    apex_metadata.is_factory = is_factory;
    PayloadMetadata(metadata_partition_path).apex(test_apex_foo, apex_metadata);

    // call ApexFileRepository::AddBlockApex()
    ApexFileRepository instance;
    auto status = instance.AddBlockApex(metadata_partition_path);
    ASSERT_RESULT_OK(status)
        << "failed to add block apex with is_factory=" << is_factory;
    ASSERT_EQ(is_factory,
              instance.HasPreInstalledVersion("com.android.apex.test_package"));
  }
}

}  // namespace apex
}  // namespace android
