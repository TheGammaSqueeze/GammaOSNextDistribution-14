/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include "derive_classpath.h"

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/properties.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <android-modules-utils/sdk_level.h>
#include <android/api-level.h>
#include <gtest/gtest.h>
#include <stdlib.h>
#include <sys/mman.h>
#include <sys/stat.h>

#include <cstdlib>
#include <string_view>

#include "android-base/unique_fd.h"
#include "packages/modules/common/proto/classpaths.pb.h"

namespace android {
namespace derive_classpath {
namespace {

static const std::string kFrameworkJarFilepath = "/system/framework/framework.jar";
static const std::string kLibartJarFilepath = "/apex/com.android.art/javalib/core-libart.jar";
static const std::string kSdkExtensionsJarFilepath =
    "/apex/com.android.sdkext/javalib/framework-sdkextensions.jar";
static const std::string kServicesJarFilepath = "/system/framework/services.jar";

// The fixture for testing derive_classpath.
class DeriveClasspathTest : public ::testing::Test {
 protected:
  ~DeriveClasspathTest() override {
    // Not really needed, as a test device will re-generate a proper classpath on reboot,
    // but it's better to leave it in a clean state after a test.
    GenerateClasspathExports(default_args_);
  }

  const std::string working_dir() { return std::string(temp_dir_.path); }

  // Parses the generated classpath exports file and returns each line individually.
  std::vector<std::string> ParseExportsFile(const char* file = "/data/system/environ/classpath") {
    std::string contents;
    EXPECT_TRUE(android::base::ReadFileToString(file, &contents, /*follow_symlinks=*/true));
    return android::base::Split(contents, "\n");
  }

  std::vector<std::string> SplitClasspathExportLine(const std::string& line) {
    std::vector<std::string> contents = android::base::Split(line, " ");
    // Export lines are expected to be structured as `export <name> <value>`.
    EXPECT_EQ(3, contents.size());
    EXPECT_EQ("export", contents[0]);
    return contents;
  }

  // Checks the order of the jars in a given classpath.
  // Instead of doing a full order check, it assumes the jars are grouped by partition and checks
  // that partitions come in order of the `prefixes` that is given.
  void CheckClasspathGroupOrder(const std::string classpath,
                                const std::vector<std::string> prefixes) {
    ASSERT_NE(0, prefixes.size());
    ASSERT_NE(0, classpath.size());

    auto jars = android::base::Split(classpath, ":");

    auto prefix = prefixes.begin();
    auto jar = jars.begin();
    for (; jar != jars.end() && prefix != prefixes.end(); ++jar) {
      if (*jar == "/apex/com.android.i18n/javalib/core-icu4j.jar") {
        // core-icu4j.jar is special and is out of order in BOOTCLASSPATH;
        // ignore it when checking for general order
        continue;
      }
      if (!android::base::StartsWith(*jar, *prefix)) {
        ++prefix;
      }
    }
    EXPECT_NE(prefix, prefixes.end());
    // All jars have been iterated over, thus they all have valid prefixes
    EXPECT_EQ(jar, jars.end());
  }

  void WriteConfig(const ExportedClasspathsJars& exported_jars, const std::string& path) {
    std::string fragment_path = working_dir() + path;
    std::string buf;
    exported_jars.SerializeToString(&buf);
    std::string cmd("mkdir -p " + android::base::Dirname(fragment_path));
    ASSERT_EQ(0, system(cmd.c_str()));
    ASSERT_TRUE(android::base::WriteStringToFile(buf, fragment_path, true));
  }

  void AddJarToClasspath(const std::string& partition, const std::string& jar_filepath,
                         Classpath classpath) {
    ExportedClasspathsJars exported_jars;
    Jar* jar = exported_jars.add_jars();
    jar->set_path(jar_filepath);
    jar->set_classpath(classpath);

    std::string basename = Classpath_Name(classpath) + ".pb";
    std::transform(basename.begin(), basename.end(), basename.begin(),
                   [](unsigned char c) { return std::tolower(c); });

    WriteConfig(exported_jars, partition + "/etc/classpaths/" + basename);
  }

  const TemporaryDir temp_dir_;

  const Args default_args_ = {
      .output_path = kGeneratedClasspathExportsFilepath,
  };

  const Args default_args_with_test_dir_ = {
      .output_path = kGeneratedClasspathExportsFilepath,
      .glob_pattern_prefix = temp_dir_.path,
  };
};

using DeriveClasspathDeathTest = DeriveClasspathTest;

// Check only known *CLASSPATH variables are exported.
TEST_F(DeriveClasspathTest, DefaultNoUnknownClasspaths) {
  // Re-generate default on device classpaths
  GenerateClasspathExports(default_args_);

  const std::vector<std::string> exportLines = ParseExportsFile();
  // The first four lines are tested below.
  for (int i = 4; i < exportLines.size(); i++) {
    EXPECT_EQ(exportLines[i], "");
  }
}

// Test that all variables are properly generated.
TEST_F(DeriveClasspathTest, AllVariables) {
  ExportedClasspathsJars exported_jars;
  Jar* jar = exported_jars.add_jars();
  jar->set_path("/apex/com.android.foo/javalib/foo");
  jar->set_classpath(BOOTCLASSPATH);
  jar = exported_jars.add_jars();
  jar->set_path("/apex/com.android.bar/javalib/bar");
  jar->set_classpath(DEX2OATBOOTCLASSPATH);
  WriteConfig(exported_jars, "/system/etc/classpaths/bootclasspath.pb");

  exported_jars.clear_jars();
  jar = exported_jars.add_jars();
  jar->set_path("/apex/com.android.baz/javalib/baz");
  jar->set_classpath(SYSTEMSERVERCLASSPATH);
  jar = exported_jars.add_jars();
  jar->set_path("/apex/com.android.qux/javalib/qux");
  jar->set_classpath(STANDALONE_SYSTEMSERVER_JARS);
  WriteConfig(exported_jars, "/system/etc/classpaths/systemserverclasspath.pb");

  ASSERT_TRUE(GenerateClasspathExports(default_args_with_test_dir_));

  const std::vector<std::string> exportLines = ParseExportsFile();
  std::vector<std::string> splitExportLine;

  splitExportLine = SplitClasspathExportLine(exportLines[0]);
  EXPECT_EQ("BOOTCLASSPATH", splitExportLine[1]);
  EXPECT_EQ("/apex/com.android.foo/javalib/foo", splitExportLine[2]);
  splitExportLine = SplitClasspathExportLine(exportLines[1]);
  EXPECT_EQ("DEX2OATBOOTCLASSPATH", splitExportLine[1]);
  EXPECT_EQ("/apex/com.android.bar/javalib/bar", splitExportLine[2]);
  splitExportLine = SplitClasspathExportLine(exportLines[2]);
  EXPECT_EQ("SYSTEMSERVERCLASSPATH", splitExportLine[1]);
  EXPECT_EQ("/apex/com.android.baz/javalib/baz", splitExportLine[2]);
  splitExportLine = SplitClasspathExportLine(exportLines[3]);
  EXPECT_EQ("STANDALONE_SYSTEMSERVER_JARS", splitExportLine[1]);
  EXPECT_EQ("/apex/com.android.qux/javalib/qux", splitExportLine[2]);
}

// Test that temp directory does not pick up actual jars.
TEST_F(DeriveClasspathTest, TempConfig) {
  AddJarToClasspath("/apex/com.android.foo", "/apex/com.android.foo/javalib/foo", BOOTCLASSPATH);
  AddJarToClasspath("/apex/com.android.baz", "/apex/com.android.baz/javalib/baz",
                    SYSTEMSERVERCLASSPATH);

  ASSERT_TRUE(GenerateClasspathExports(default_args_with_test_dir_));

  const std::vector<std::string> exportLines = ParseExportsFile();

  std::vector<std::string> splitExportLine;

  splitExportLine = SplitClasspathExportLine(exportLines[0]);
  EXPECT_EQ("BOOTCLASSPATH", splitExportLine[1]);
  EXPECT_EQ("/apex/com.android.foo/javalib/foo", splitExportLine[2]);
  splitExportLine = SplitClasspathExportLine(exportLines[2]);
  EXPECT_EQ("SYSTEMSERVERCLASSPATH", splitExportLine[1]);
  EXPECT_EQ("/apex/com.android.baz/javalib/baz", splitExportLine[2]);
}

// Test individual modules are sorted by pathnames.
TEST_F(DeriveClasspathTest, ModulesAreSorted) {
  AddJarToClasspath("/apex/com.android.art", "/apex/com.android.art/javalib/art", BOOTCLASSPATH);
  AddJarToClasspath("/system", "/system/framework/jar", BOOTCLASSPATH);
  AddJarToClasspath("/apex/com.android.foo", "/apex/com.android.foo/javalib/foo", BOOTCLASSPATH);
  AddJarToClasspath("/apex/com.android.bar", "/apex/com.android.bar/javalib/bar", BOOTCLASSPATH);
  AddJarToClasspath("/apex/com.android.baz", "/apex/com.android.baz/javalib/baz", BOOTCLASSPATH);

  ASSERT_TRUE(GenerateClasspathExports(default_args_with_test_dir_));

  const std::vector<std::string> exportLines = ParseExportsFile();
  const std::vector<std::string> splitExportLine = SplitClasspathExportLine(exportLines[0]);
  const std::string exportValue = splitExportLine[2];

  const std::string expectedJars(
      "/apex/com.android.art/javalib/art"
      ":/system/framework/jar"
      ":/apex/com.android.bar/javalib/bar"
      ":/apex/com.android.baz/javalib/baz"
      ":/apex/com.android.foo/javalib/foo");

  EXPECT_EQ(expectedJars, exportValue);
}

// Test we can output to custom files.
TEST_F(DeriveClasspathTest, CustomOutputLocation) {
  AddJarToClasspath("/apex/com.android.art", "/apex/com.android.art/javalib/art", BOOTCLASSPATH);
  AddJarToClasspath("/system", "/system/framework/jar", BOOTCLASSPATH);
  AddJarToClasspath("/apex/com.android.foo", "/apex/com.android.foo/javalib/foo", BOOTCLASSPATH);
  AddJarToClasspath("/apex/com.android.bar", "/apex/com.android.bar/javalib/bar", BOOTCLASSPATH);
  AddJarToClasspath("/apex/com.android.baz", "/apex/com.android.baz/javalib/baz", BOOTCLASSPATH);

  android::base::unique_fd fd(memfd_create("temp_file", MFD_CLOEXEC));
  ASSERT_TRUE(fd.ok()) << "Unable to open temp-file";
  const std::string file_name = android::base::StringPrintf("/proc/self/fd/%d", fd.get());
  Args args = {
      .output_path = file_name,
      .glob_pattern_prefix = working_dir(),
  };
  ASSERT_TRUE(GenerateClasspathExports(args));

  const std::vector<std::string> exportLines = ParseExportsFile(file_name.c_str());
  const std::vector<std::string> splitExportLine = SplitClasspathExportLine(exportLines[0]);
  const std::string exportValue = splitExportLine[2];

  const std::string expectedJars(
      "/apex/com.android.art/javalib/art"
      ":/system/framework/jar"
      ":/apex/com.android.bar/javalib/bar"
      ":/apex/com.android.baz/javalib/baz"
      ":/apex/com.android.foo/javalib/foo");

  EXPECT_EQ(expectedJars, exportValue);
}

// Test alternative .pb for bootclasspath and systemclasspath.
TEST_F(DeriveClasspathTest, CustomInputLocation) {
  AddJarToClasspath("/other", "/other/bcp-jar", BOOTCLASSPATH);
  AddJarToClasspath("/other", "/other/systemserver-jar", SYSTEMSERVERCLASSPATH);
  AddJarToClasspath("/apex/com.android.art", "/apex/com.android.art/javalib/art", BOOTCLASSPATH);
  AddJarToClasspath("/apex/com.android.foo", "/apex/com.android.foo/javalib/foo", BOOTCLASSPATH);
  AddJarToClasspath("/apex/com.android.baz", "/apex/com.android.baz/javalib/baz",
                    SYSTEMSERVERCLASSPATH);

  Args args = default_args_with_test_dir_;
  args.system_bootclasspath_fragment = "/other/etc/classpaths/bootclasspath.pb";
  args.system_systemserverclasspath_fragment = "/other/etc/classpaths/systemserverclasspath.pb";

  ASSERT_TRUE(GenerateClasspathExports(args));

  const std::vector<std::string> exportLines = ParseExportsFile();

  std::vector<std::string> splitExportLine;
  splitExportLine = SplitClasspathExportLine(exportLines[0]);
  EXPECT_EQ("BOOTCLASSPATH", splitExportLine[1]);
  const std::string expectedBcpJars(
      "/apex/com.android.art/javalib/art"
      ":/other/bcp-jar"
      ":/apex/com.android.foo/javalib/foo");
  EXPECT_EQ(expectedBcpJars, splitExportLine[2]);

  splitExportLine = SplitClasspathExportLine(exportLines[2]);
  EXPECT_EQ("SYSTEMSERVERCLASSPATH", splitExportLine[1]);
  const std::string expectedSystemServerJars(
      "/other/systemserver-jar"
      ":/apex/com.android.baz/javalib/baz");
  EXPECT_EQ(expectedSystemServerJars, splitExportLine[2]);
}

// Test output location that can't be written to.
TEST_F(DeriveClasspathTest, NonWriteableOutputLocation) {
  AddJarToClasspath("/apex/com.android.art", "/apex/com.android.art/javalib/art", BOOTCLASSPATH);
  AddJarToClasspath("/system", "/system/framework/jar", BOOTCLASSPATH);

  Args args = {
      .output_path = "/system/non_writable_path",
      .glob_pattern_prefix = working_dir(),
  };
  ASSERT_FALSE(GenerateClasspathExports(args));
}

TEST_F(DeriveClasspathTest, ScanOnlySpecificDirectories) {
  AddJarToClasspath("/system", "/system/framework/jar", BOOTCLASSPATH);
  AddJarToClasspath("/apex/com.android.foo", "/apex/com.android.foo/javalib/foo", BOOTCLASSPATH);
  AddJarToClasspath("/apex/com.android.foo", "/apex/com.android.foo/javalib/sys",
                    SYSTEMSERVERCLASSPATH);
  AddJarToClasspath("/apex/com.android.bar", "/apex/com.android.bar/javalib/bar", BOOTCLASSPATH);
  AddJarToClasspath("/apex/com.android.baz", "/apex/com.android.baz/javalib/baz", BOOTCLASSPATH);

  auto args_with_scan_dirs = default_args_with_test_dir_;
  args_with_scan_dirs.scan_dirs.push_back("/apex/com.android.foo");
  args_with_scan_dirs.scan_dirs.push_back("/apex/com.android.bar");
  ASSERT_TRUE(GenerateClasspathExports(args_with_scan_dirs));

  const std::vector<std::string> exportLines = ParseExportsFile();

  std::vector<std::string> splitExportLine;

  splitExportLine = SplitClasspathExportLine(exportLines[0]);
  EXPECT_EQ("BOOTCLASSPATH", splitExportLine[1]);
  // Not sorted. Maintains the ordering provided in scan_dirs
  const std::string expectedJars(
      "/apex/com.android.foo/javalib/foo"
      ":/apex/com.android.bar/javalib/bar");
  EXPECT_EQ(expectedJars, splitExportLine[2]);
  splitExportLine = SplitClasspathExportLine(exportLines[2]);
  EXPECT_EQ("SYSTEMSERVERCLASSPATH", splitExportLine[1]);
  EXPECT_EQ("/apex/com.android.foo/javalib/sys", splitExportLine[2]);
}

// Test apexes only export their own jars.
TEST_F(DeriveClasspathDeathTest, ApexJarsBelongToApex) {
  // EXPECT_DEATH expects error messages in stderr, log there
  android::base::SetLogger(android::base::StderrLogger);

  AddJarToClasspath("/system", "/system/framework/jar", BOOTCLASSPATH);
  ASSERT_TRUE(GenerateClasspathExports(default_args_with_test_dir_));

  AddJarToClasspath("/apex/com.android.foo", "/apex/com.android.foo/javalib/foo", BOOTCLASSPATH);
  ASSERT_TRUE(GenerateClasspathExports(default_args_with_test_dir_));

  AddJarToClasspath("/apex/com.android.bar@12345.tmp", "/apex/com.android.bar/javalib/bar",
                    BOOTCLASSPATH);
  ASSERT_TRUE(GenerateClasspathExports(default_args_with_test_dir_));

  AddJarToClasspath("/apex/com.android.baz@12345", "/apex/this/path/is/skipped", BOOTCLASSPATH);
  ASSERT_TRUE(GenerateClasspathExports(default_args_with_test_dir_));

  AddJarToClasspath("/apex/com.android.bar", "/apex/wrong/path/bar", BOOTCLASSPATH);
  EXPECT_DEATH(GenerateClasspathExports(default_args_with_test_dir_),
               "must not export a jar.*wrong/path/bar");
}

// Test only bind mounted apexes are skipped
TEST_F(DeriveClasspathTest, OnlyBindMountedApexIsSkipped) {
  AddJarToClasspath("/system", "/system/framework/jar", BOOTCLASSPATH);
  // Normal APEX with format: /apex/<module-name>/*
  AddJarToClasspath("/apex/com.android.foo", "/apex/com.android.foo/javalib/foo", BOOTCLASSPATH);
  // Bind mounted APEX with format: /apex/<module-name>@<version>/*
  AddJarToClasspath("/apex/com.android.bar@123", "/apex/com.android.bar/javalib/bar",
                    BOOTCLASSPATH);
  // Temp mounted APEX with format: /apex/<module-name>@<version>.tmp/*
  AddJarToClasspath("/apex/com.android.baz@123.tmp", "/apex/com.android.baz/javalib/baz",
                    BOOTCLASSPATH);

  ASSERT_TRUE(GenerateClasspathExports(default_args_with_test_dir_));

  const std::vector<std::string> exportLines = ParseExportsFile();

  std::vector<std::string> splitExportLine;

  splitExportLine = SplitClasspathExportLine(exportLines[0]);
  EXPECT_EQ("BOOTCLASSPATH", splitExportLine[1]);
  const std::string expectedJars(
      "/system/framework/jar"
      ":/apex/com.android.baz/javalib/baz"
      ":/apex/com.android.foo/javalib/foo");
  EXPECT_EQ(expectedJars, splitExportLine[2]);
}

// Test classpath fragments export jars for themselves.
TEST_F(DeriveClasspathDeathTest, WrongClasspathInFragments) {
  // Valid configs
  AddJarToClasspath("/system", "/system/framework/framework-jar", BOOTCLASSPATH);
  AddJarToClasspath("/system", "/system/framework/service-jar", SYSTEMSERVERCLASSPATH);

  // Manually create an invalid config with both BCP and SSCP jars...
  ExportedClasspathsJars exported_jars;
  Jar* jar = exported_jars.add_jars();
  jar->set_path("/apex/com.android.foo/javalib/foo");
  jar->set_classpath(BOOTCLASSPATH);
  // note that DEX2OATBOOTCLASSPATH and BOOTCLASSPATH jars are expected to be in the same config
  jar = exported_jars.add_jars();
  jar->set_path("/apex/com.android.foo/javalib/foo");
  jar->set_classpath(DEX2OATBOOTCLASSPATH);
  jar = exported_jars.add_jars();
  jar->set_path("/apex/com.android.foo/javalib/service-foo");
  jar->set_classpath(SYSTEMSERVERCLASSPATH);

  // ...and write this config to bootclasspath.pb
  WriteConfig(exported_jars, "/apex/com.android.foo/etc/classpaths/bootclasspath.pb");

  EXPECT_DEATH(GenerateClasspathExports(default_args_with_test_dir_),
               "must not export a jar for SYSTEMSERVERCLASSPATH");
}

TEST_F(DeriveClasspathDeathTest, CurrentSdkVersion) {
  if (android_get_device_api_level() < __ANDROID_API_S__) {
    GTEST_SKIP();
  }

  ExportedClasspathsJars exported_jars;
  Jar* jar = exported_jars.add_jars();
  jar->set_path("/apex/com.android.foo/javalib/minsdkcurrent");
  jar->set_min_sdk_version("current");
  jar->set_classpath(SYSTEMSERVERCLASSPATH);
  WriteConfig(exported_jars, "/apex/com.android.foo/etc/classpaths/systemserverclasspath.pb");

  EXPECT_DEATH(GenerateClasspathExports(default_args_with_test_dir_), "no conversion");
}

// Test jars with different sdk versions.
TEST_F(DeriveClasspathTest, SdkVersionsAreRespected) {
  if (android_get_device_api_level() < __ANDROID_API_S__) {
    GTEST_SKIP();
  }

  // List of jars expected to be in SYSTEMSERVERCLASSPATH
  std::vector<std::string> expected_jars;

  // Add an unbounded jar
  AddJarToClasspath("/system", "/system/framework/unbounded", SYSTEMSERVERCLASSPATH);
  expected_jars.push_back("/system/framework/unbounded");

  // Manually create a config with jars that set sdk versions...
  ExportedClasspathsJars exported_jars;

  // known released versions:
  Jar* jar = exported_jars.add_jars();
  jar->set_path("/apex/com.android.foo/javalib/minsdk30");
  jar->set_min_sdk_version(std::to_string(__ANDROID_API_R__));
  jar->set_classpath(SYSTEMSERVERCLASSPATH);
  expected_jars.push_back("/apex/com.android.foo/javalib/minsdk30");
  jar = exported_jars.add_jars();
  jar->set_path("/apex/com.android.foo/javalib/maxsdk30");
  jar->set_max_sdk_version(std::to_string(__ANDROID_API_R__));
  jar->set_classpath(SYSTEMSERVERCLASSPATH);

  // Device's reported version:
  jar = exported_jars.add_jars();
  jar->set_path("/apex/com.android.foo/javalib/minsdklatest");
  jar->set_min_sdk_version(std::to_string(android_get_device_api_level()));
  jar->set_classpath(SYSTEMSERVERCLASSPATH);
  expected_jars.push_back("/apex/com.android.foo/javalib/minsdklatest");
  jar = exported_jars.add_jars();
  jar->set_path("/apex/com.android.foo/javalib/maxsdklatest");
  jar->set_max_sdk_version(std::to_string(android_get_device_api_level()));
  jar->set_classpath(SYSTEMSERVERCLASSPATH);
  if ("REL" == android::base::GetProperty("ro.build.version.codename", "")) {
    expected_jars.push_back("/apex/com.android.foo/javalib/maxsdklatest");
  }

  // unknown SDK_INT+1 version
  jar = exported_jars.add_jars();
  jar->set_path("/apex/com.android.foo/javalib/minsdk_plus1");
  jar->set_min_sdk_version(std::to_string(android_get_device_api_level() + 1));
  jar->set_classpath(SYSTEMSERVERCLASSPATH);
  jar = exported_jars.add_jars();
  jar->set_path("/apex/com.android.foo/javalib/maxsdk_plus1");
  jar->set_max_sdk_version(std::to_string(android_get_device_api_level() + 1));
  jar->set_classpath(SYSTEMSERVERCLASSPATH);
  expected_jars.push_back("/apex/com.android.foo/javalib/maxsdk_plus1");

  // known min_sdk_version and future max_sdk_version
  jar = exported_jars.add_jars();
  jar->set_path("/apex/com.android.foo/javalib/minsdk30maxsdk10000");
  jar->set_min_sdk_version(std::to_string(__ANDROID_API_R__));
  jar->set_max_sdk_version(std::to_string(android_get_device_api_level() + 1));
  jar->set_classpath(SYSTEMSERVERCLASSPATH);
  expected_jars.push_back("/apex/com.android.foo/javalib/minsdk30maxsdk10000");

  // codename
  if ("REL" != android::base::GetProperty("ro.build.version.codename", "")) {
    jar = exported_jars.add_jars();
    jar->set_path("/apex/com.android.foo/javalib/minsdkS");
    jar->set_min_sdk_version("S");
    jar->set_classpath(SYSTEMSERVERCLASSPATH);
    expected_jars.push_back("/apex/com.android.foo/javalib/minsdkS");

    jar = exported_jars.add_jars();
    jar->set_path("/apex/com.android.foo/javalib/minsdkSv2");
    jar->set_min_sdk_version("Sv2");
    jar->set_classpath(SYSTEMSERVERCLASSPATH);
    expected_jars.push_back("/apex/com.android.foo/javalib/minsdkSv2");

    jar = exported_jars.add_jars();
    jar->set_path("/apex/com.android.foo/javalib/minsdkTiramisu");
    jar->set_min_sdk_version("Tiramisu");
    jar->set_classpath(SYSTEMSERVERCLASSPATH);
    expected_jars.push_back("/apex/com.android.foo/javalib/minsdkTiramisu");

    jar = exported_jars.add_jars();
    jar->set_path("/apex/com.android.foo/javalib/maxsdkS");
    jar->set_max_sdk_version("S");
    jar->set_classpath(SYSTEMSERVERCLASSPATH);

    jar = exported_jars.add_jars();
    jar->set_path("/apex/com.android.foo/javalib/maxsdkSv2");
    jar->set_max_sdk_version("Sv2");
    jar->set_classpath(SYSTEMSERVERCLASSPATH);

    jar = exported_jars.add_jars();
    jar->set_path("/apex/com.android.foo/javalib/maxsdkZFutureSdkVersion");
    jar->set_max_sdk_version("ZFutureSdkVersion");
    jar->set_classpath(SYSTEMSERVERCLASSPATH);
    expected_jars.push_back("/apex/com.android.foo/javalib/maxsdkZFutureSdkVersion");
  }

  // ...and write this config to systemserverclasspath.pb
  WriteConfig(exported_jars, "/apex/com.android.foo/etc/classpaths/systemserverclasspath.pb");

  // Generate and parse SYSTEMSERVERCLASSPATH
  GenerateClasspathExports(default_args_with_test_dir_);
  const std::vector<std::string> exportLines = ParseExportsFile();
  const std::vector<std::string> splitExportLine = SplitClasspathExportLine(exportLines[2]);
  const std::string exportValue = splitExportLine[2];

  EXPECT_EQ(android::base::Join(expected_jars, ":"), exportValue);
}

}  // namespace
}  // namespace derive_classpath
}  // namespace android

int main(int argc, char** argv) {
  ::testing::InitGoogleTest(&argc, argv);
  return RUN_ALL_TESTS();
}
