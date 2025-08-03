/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include <string>
#include <vector>

#include <android-base/file.h>
#include <apex_manifest.pb.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <linker_config.pb.h>

#include "apex_testbase.h"
#include "linkerconfig/apex.h"
#include "linkerconfig/basecontext.h"
#include "linkerconfig/configwriter.h"
#include "linkerconfig/namespace.h"
#include "linkerconfig/section.h"

using ::android::base::WriteStringToFile;
using ::android::linkerconfig::modules::ApexInfo;
using ::android::linkerconfig::modules::BaseContext;
using ::android::linkerconfig::modules::ConfigWriter;
using ::android::linkerconfig::modules::InitializeWithApex;
using ::android::linkerconfig::modules::Namespace;
using ::android::linkerconfig::modules::ScanActiveApexes;
using ::android::linkerconfig::modules::Section;
using ::apex::proto::ApexManifest;
using ::testing::Contains;

TEST(apex_namespace, build_namespace) {
  Namespace ns("foo");
  InitializeWithApex(ns,
                     ApexInfo("com.android.foo",
                              "/apex/com.android.foo",
                              /*provide_libs=*/{},
                              /*require_libs=*/{},
                              /*jni_libs=*/{},
                              /*permitted_paths=*/{},
                              /*has_bin=*/false,
                              /*has_lib=*/true,
                              /*visible=*/false,
                              /*has_shared_lib=*/false));

  ConfigWriter writer;
  ns.WriteConfig(writer);
  ASSERT_EQ(
      "namespace.foo.isolated = false\n"
      "namespace.foo.search.paths = /apex/com.android.foo/${LIB}\n"
      "namespace.foo.permitted.paths = /apex/com.android.foo/${LIB}\n"
      "namespace.foo.permitted.paths += /system/${LIB}\n"
      "namespace.foo.permitted.paths += /system_ext/${LIB}\n"
      "namespace.foo.asan.search.paths = /apex/com.android.foo/${LIB}\n"
      "namespace.foo.asan.permitted.paths = /apex/com.android.foo/${LIB}\n"
      "namespace.foo.asan.permitted.paths += /data/asan/system/${LIB}\n"
      "namespace.foo.asan.permitted.paths += /system/${LIB}\n"
      "namespace.foo.asan.permitted.paths += /data/asan/system_ext/${LIB}\n"
      "namespace.foo.asan.permitted.paths += /system_ext/${LIB}\n"
      "namespace.foo.hwasan.search.paths = /apex/com.android.foo/${LIB}/hwasan\n"
      "namespace.foo.hwasan.search.paths += /apex/com.android.foo/${LIB}\n"
      "namespace.foo.hwasan.permitted.paths = /apex/com.android.foo/${LIB}/hwasan\n"
      "namespace.foo.hwasan.permitted.paths += /apex/com.android.foo/${LIB}\n"
      "namespace.foo.hwasan.permitted.paths += /system/${LIB}/hwasan\n"
      "namespace.foo.hwasan.permitted.paths += /system/${LIB}\n"
      "namespace.foo.hwasan.permitted.paths += /system_ext/${LIB}/hwasan\n"
      "namespace.foo.hwasan.permitted.paths += /system_ext/${LIB}\n",
      writer.ToString());
}

TEST(apex_namespace, resolve_between_apex_namespaces) {
  BaseContext ctx;
  Namespace foo("foo"), bar("bar");
  InitializeWithApex(foo,
                     ApexInfo("com.android.foo",
                              "/apex/com.android.foo",
                              /*provide_libs=*/{"foo.so"},
                              /*require_libs=*/{"bar.so"},
                              /*jni_libs=*/{},
                              /*permitted_paths=*/{},
                              /*has_bin=*/false,
                              /*has_lib=*/true,
                              /*visible=*/false,
                              /*has_shared_lib=*/false));
  InitializeWithApex(bar,
                     ApexInfo("com.android.bar",
                              "/apex/com.android.bar",
                              /*provide_libs=*/{"bar.so"},
                              /*require_libs=*/{},
                              /*jni_libs=*/{},
                              /*permitted_paths=*/{},
                              /*has_bin=*/false,
                              /*has_lib=*/true,
                              /*visible=*/false,
                              /*has_shared_lib=*/false));

  std::vector<Namespace> namespaces;
  namespaces.push_back(std::move(foo));
  namespaces.push_back(std::move(bar));
  Section section("section", std::move(namespaces));

  section.Resolve(ctx);

  // See if two namespaces are linked correctly
  ASSERT_THAT(section.GetNamespace("foo")->GetLink("bar").GetSharedLibs(),
              Contains("bar.so"));
}

TEST(apex_namespace, extra_permitted_paths) {
  Namespace ns("foo");
  InitializeWithApex(ns,
                     ApexInfo("com.android.foo",
                              "/apex/com.android.foo",
                              /*provide_libs=*/{},
                              /*require_libs=*/{},
                              /*jni_libs=*/{},
                              /*permitted_paths=*/{"/a", "/b/c"},
                              /*has_bin=*/false,
                              /*has_lib=*/true,
                              /*visible=*/false,
                              /*has_shared_lib=*/false));

  ConfigWriter writer;
  ns.WriteConfig(writer);
  ASSERT_EQ(
      "namespace.foo.isolated = false\n"
      "namespace.foo.search.paths = /apex/com.android.foo/${LIB}\n"
      "namespace.foo.permitted.paths = /apex/com.android.foo/${LIB}\n"
      "namespace.foo.permitted.paths += /system/${LIB}\n"
      "namespace.foo.permitted.paths += /system_ext/${LIB}\n"
      "namespace.foo.permitted.paths += /a\n"
      "namespace.foo.permitted.paths += /b/c\n"
      "namespace.foo.asan.search.paths = /apex/com.android.foo/${LIB}\n"
      "namespace.foo.asan.permitted.paths = /apex/com.android.foo/${LIB}\n"
      "namespace.foo.asan.permitted.paths += /data/asan/system/${LIB}\n"
      "namespace.foo.asan.permitted.paths += /system/${LIB}\n"
      "namespace.foo.asan.permitted.paths += /data/asan/system_ext/${LIB}\n"
      "namespace.foo.asan.permitted.paths += /system_ext/${LIB}\n"
      "namespace.foo.asan.permitted.paths += /data/asan/a\n"
      "namespace.foo.asan.permitted.paths += /a\n"
      "namespace.foo.asan.permitted.paths += /data/asan/b/c\n"
      "namespace.foo.asan.permitted.paths += /b/c\n"
      "namespace.foo.hwasan.search.paths = /apex/com.android.foo/${LIB}/hwasan\n"
      "namespace.foo.hwasan.search.paths += /apex/com.android.foo/${LIB}\n"
      "namespace.foo.hwasan.permitted.paths = /apex/com.android.foo/${LIB}/hwasan\n"
      "namespace.foo.hwasan.permitted.paths += /apex/com.android.foo/${LIB}\n"
      "namespace.foo.hwasan.permitted.paths += /system/${LIB}/hwasan\n"
      "namespace.foo.hwasan.permitted.paths += /system/${LIB}\n"
      "namespace.foo.hwasan.permitted.paths += /system_ext/${LIB}/hwasan\n"
      "namespace.foo.hwasan.permitted.paths += /system_ext/${LIB}\n"
      "namespace.foo.hwasan.permitted.paths += /a/hwasan\n"
      "namespace.foo.hwasan.permitted.paths += /a\n"
      "namespace.foo.hwasan.permitted.paths += /b/c/hwasan\n"
      "namespace.foo.hwasan.permitted.paths += /b/c\n",
      writer.ToString());
}

TEST_F(ApexTest, scan_apex_dir) {
  PrepareApex("foo", {}, {"bar.so"}, {});
  WriteFile("/apex/foo/bin/foo", "");
  PrepareApex("bar", {"bar.so"}, {}, {});
  WriteFile("/apex/bar/lib64/bar.so", "");
  PrepareApex("baz", {}, {}, {"baz.so"});
  WriteFile("/apex/baz/lib64/baz.so", "");
  CreateApexInfoList();
  CreatePublicLibrariesTxt();

  auto apexes = ScanActiveApexes(root);
  ASSERT_TRUE(apexes.ok()) << "Failed to scan active APEXes : "
                           << apexes.error();
  ASSERT_EQ(3U, apexes->size());

  ASSERT_THAT((*apexes)["foo"].require_libs, Contains("bar.so"));
  ASSERT_TRUE((*apexes)["foo"].has_bin);
  ASSERT_FALSE((*apexes)["foo"].has_lib);

  ASSERT_THAT((*apexes)["bar"].provide_libs, Contains("bar.so"));
  ASSERT_FALSE((*apexes)["bar"].has_bin);
  ASSERT_TRUE((*apexes)["bar"].has_lib);

  ASSERT_THAT((*apexes)["baz"].jni_libs, Contains("baz.so"));
  ASSERT_FALSE((*apexes)["baz"].has_bin);
  ASSERT_TRUE((*apexes)["baz"].has_lib);
}

TEST_F(ApexTest, validate_path) {
  PrepareApex("foo", {}, {}, {});
  CreateApexInfoList();
  CreatePublicLibrariesTxt();

  ::android::linkerconfig::proto::LinkerConfig two_slash;
  two_slash.add_permittedpaths("/two//slash");

  WriteFile("/apex/foo/etc/linker.config.pb", two_slash.SerializeAsString());
  auto apexes = ScanActiveApexes(root);
  ASSERT_FALSE(apexes.ok()) << "Two slash is not allowed from path string";

  ::android::linkerconfig::proto::LinkerConfig invalid_char;
  invalid_char.add_permittedpaths("/path/with*/invalid/char");

  WriteFile("/apex/foo/etc/linker.config.pb", invalid_char.SerializeAsString());
  apexes = ScanActiveApexes(root);
  ASSERT_FALSE(apexes.ok()) << "* is invalid char for path.";

  ::android::linkerconfig::proto::LinkerConfig end_with_lib;
  end_with_lib.add_permittedpaths("/somewhere/${LIB}");

  WriteFile("/apex/foo/etc/linker.config.pb", end_with_lib.SerializeAsString());
  apexes = ScanActiveApexes(root);
  ASSERT_TRUE(apexes.ok()) << "Path ends with ${LIB} should be accepted. : "
                           << apexes.error();

  ::android::linkerconfig::proto::LinkerConfig lib_plus_char;
  lib_plus_char.add_permittedpaths("/somewhere/${LIB}x/hw");

  WriteFile("/apex/foo/etc/linker.config.pb", lib_plus_char.SerializeAsString());
  apexes = ScanActiveApexes(root);
  ASSERT_FALSE(apexes.ok())
      << "There should be no extra char after ${LIB} in path.";

  ::android::linkerconfig::proto::LinkerConfig char_plus_lib;
  char_plus_lib.add_permittedpaths("/somewhere/x${LIB}/hw");

  WriteFile("/apex/foo/etc/linker.config.pb", char_plus_lib.SerializeAsString());
  apexes = ScanActiveApexes(root);
  ASSERT_FALSE(apexes.ok())
      << "There should be no extra char before ${LIB} in path.";

  ::android::linkerconfig::proto::LinkerConfig lib_and_lib64;
  lib_and_lib64.add_permittedpaths("/somewhere/${LIB}/hw");

  WriteFile("/apex/foo/etc/linker.config.pb", lib_and_lib64.SerializeAsString());
  apexes = ScanActiveApexes(root);
  ASSERT_TRUE(apexes.ok()) << "Valid path with ${LIB} should be accepted. : "
                           << apexes.error();
}

TEST_F(ApexTest, skip_sharedlibs_apex) {
  PrepareApex("foo", {}, {}, {});
  WriteFile("/apex/apex-info-list.xml", R"(<apex-info-list>
    <apex-info moduleName="foo"
      preinstalledModulePath="/system/apex/foo.apex"
      modulePath="/data/apex/active/foo.apex"
      isActive="true" />
    <apex-info moduleName="sharedlibs"
      preinstalledModulePath="/system/apex/sharedlibs.apex"
      modulePath="/data/apex/active/sharedlibs.apex"
      provideSharedApexLibs="true"
      isActive="true" />
  </apex-info-list>)");
  auto apexes = ScanActiveApexes(root);
  ASSERT_TRUE(apexes.ok()) << apexes.error();
  ASSERT_EQ(apexes->find("sharedlibs"), apexes->end());
}

TEST_F(ApexTest, public_libraries_txt_malformed_line) {
  PrepareApex("foo", {}, {}, {});
  CreateApexInfoList();
  WriteFile("/system/etc/public.libraries.txt", "foo.so blah blah blah");
  auto apexes = ScanActiveApexes(root);
  ASSERT_FALSE(apexes.ok());
  ASSERT_THAT(apexes.error().message(), testing::HasSubstr("Malformed line"));
}

TEST_F(ApexTest, public_libs_with_public_libraries_txt) {
  PrepareApex("foo", /*provide_libs=*/{"libfoo.so"}, {}, {});
  WriteFile("/apex/apex-info-list.xml", R"(<apex-info-list>
    <apex-info moduleName="foo"
      preinstalledModulePath="/system/apex/foo.apex"
      modulePath="/data/apex/active/foo.apex"
      isActive="true" />
  </apex-info-list>)");
  WriteFile("/system/etc/public.libraries.txt", "libfoo.so");
  auto apexes = ScanActiveApexes(root);
  ASSERT_TRUE(apexes.ok()) << apexes.error();
  ASSERT_EQ(apexes->at("foo").public_libs,
            std::vector<std::string>{"libfoo.so"});
}

TEST_F(ApexTest, public_libs_should_be_system_apex) {
  PrepareApex("foo", /*provide_libs=*/{"libfoo.so"}, {}, {});
  WriteFile("/apex/apex-info-list.xml", R"(<apex-info-list>
    <apex-info moduleName="foo"
      preinstalledModulePath="/vendor/apex/foo.apex"
      modulePath="/data/apex/active/foo.apex"
      isActive="true" />
  </apex-info-list>)");
  WriteFile("/system/etc/public.libraries.txt", "libfoo.so");
  auto apexes = ScanActiveApexes(root);
  ASSERT_TRUE(apexes.ok()) << apexes.error();
  ASSERT_EQ(apexes->at("foo").public_libs, std::vector<std::string>{});
}

TEST_F(ApexTest, system_ext_can_be_linked_to_system_system_ext) {
  PrepareApex("foo", /*provide_libs=*/{"libfoo.so"}, {}, {});
  WriteFile("/apex/apex-info-list.xml", R"(<apex-info-list>
    <apex-info moduleName="foo"
      preinstalledModulePath="/system/system_ext/apex/foo.apex"
      modulePath="/data/apex/active/foo.apex"
      isActive="true" />
  </apex-info-list>)");
  WriteFile("/system/etc/public.libraries.txt", "libfoo.so");
  auto apexes = ScanActiveApexes(root);
  ASSERT_TRUE(apexes.ok()) << apexes.error();
  ASSERT_EQ(apexes->at("foo").public_libs,
            std::vector<std::string>{"libfoo.so"});
}
