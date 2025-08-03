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

#include "linkerconfig/section.h"

#include <android-base/result.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include "linkerconfig/apex.h"
#include "linkerconfig/basecontext.h"
#include "linkerconfig/configwriter.h"
#include "linkerconfig/variables.h"
#include "modules_testbase.h"

using namespace android::linkerconfig::modules;

constexpr const char* kSectionWithNamespacesExpectedResult =
    R"([test_section]
additional.namespaces = namespace1,namespace2
namespace.default.isolated = true
namespace.default.visible = true
namespace.default.search.paths = /search_path1
namespace.default.search.paths += /apex/search_path2
namespace.default.permitted.paths = /permitted_path1
namespace.default.permitted.paths += /apex/permitted_path2
namespace.default.asan.search.paths = /data/asan/search_path1
namespace.default.asan.search.paths += /search_path1
namespace.default.asan.search.paths += /apex/search_path2
namespace.default.asan.permitted.paths = /data/asan/permitted_path1
namespace.default.asan.permitted.paths += /permitted_path1
namespace.default.asan.permitted.paths += /apex/permitted_path2
namespace.default.hwasan.search.paths = /search_path1/hwasan
namespace.default.hwasan.search.paths += /search_path1
namespace.default.hwasan.search.paths += /apex/search_path2/hwasan
namespace.default.hwasan.search.paths += /apex/search_path2
namespace.default.hwasan.permitted.paths = /permitted_path1/hwasan
namespace.default.hwasan.permitted.paths += /permitted_path1
namespace.default.hwasan.permitted.paths += /apex/permitted_path2/hwasan
namespace.default.hwasan.permitted.paths += /apex/permitted_path2
namespace.default.links = namespace1,namespace2
namespace.default.link.namespace1.shared_libs = lib1.so:lib2.so:lib3.so
namespace.default.link.namespace2.allow_all_shared_libs = true
namespace.namespace1.isolated = false
namespace.namespace1.search.paths = /search_path1
namespace.namespace1.search.paths += /apex/search_path2
namespace.namespace1.permitted.paths = /permitted_path1
namespace.namespace1.permitted.paths += /apex/permitted_path2
namespace.namespace1.asan.search.paths = /data/asan/search_path1
namespace.namespace1.asan.search.paths += /search_path1
namespace.namespace1.asan.search.paths += /apex/search_path2
namespace.namespace1.asan.permitted.paths = /data/asan/permitted_path1
namespace.namespace1.asan.permitted.paths += /permitted_path1
namespace.namespace1.asan.permitted.paths += /apex/permitted_path2
namespace.namespace1.hwasan.search.paths = /search_path1/hwasan
namespace.namespace1.hwasan.search.paths += /search_path1
namespace.namespace1.hwasan.search.paths += /apex/search_path2/hwasan
namespace.namespace1.hwasan.search.paths += /apex/search_path2
namespace.namespace1.hwasan.permitted.paths = /permitted_path1/hwasan
namespace.namespace1.hwasan.permitted.paths += /permitted_path1
namespace.namespace1.hwasan.permitted.paths += /apex/permitted_path2/hwasan
namespace.namespace1.hwasan.permitted.paths += /apex/permitted_path2
namespace.namespace1.links = default,namespace2
namespace.namespace1.link.default.shared_libs = lib1.so:lib2.so:lib3.so
namespace.namespace1.link.namespace2.allow_all_shared_libs = true
namespace.namespace2.isolated = false
namespace.namespace2.search.paths = /search_path1
namespace.namespace2.search.paths += /apex/search_path2
namespace.namespace2.permitted.paths = /permitted_path1
namespace.namespace2.permitted.paths += /apex/permitted_path2
namespace.namespace2.asan.search.paths = /data/asan/search_path1
namespace.namespace2.asan.search.paths += /search_path1
namespace.namespace2.asan.search.paths += /apex/search_path2
namespace.namespace2.asan.permitted.paths = /data/asan/permitted_path1
namespace.namespace2.asan.permitted.paths += /permitted_path1
namespace.namespace2.asan.permitted.paths += /apex/permitted_path2
namespace.namespace2.hwasan.search.paths = /search_path1/hwasan
namespace.namespace2.hwasan.search.paths += /search_path1
namespace.namespace2.hwasan.search.paths += /apex/search_path2/hwasan
namespace.namespace2.hwasan.search.paths += /apex/search_path2
namespace.namespace2.hwasan.permitted.paths = /permitted_path1/hwasan
namespace.namespace2.hwasan.permitted.paths += /permitted_path1
namespace.namespace2.hwasan.permitted.paths += /apex/permitted_path2/hwasan
namespace.namespace2.hwasan.permitted.paths += /apex/permitted_path2
)";

constexpr const char* kSectionWithOneNamespaceExpectedResult =
    R"([test_section]
namespace.default.isolated = false
namespace.default.search.paths = /search_path1
namespace.default.search.paths += /apex/search_path2
namespace.default.permitted.paths = /permitted_path1
namespace.default.permitted.paths += /apex/permitted_path2
namespace.default.asan.search.paths = /data/asan/search_path1
namespace.default.asan.search.paths += /search_path1
namespace.default.asan.search.paths += /apex/search_path2
namespace.default.asan.permitted.paths = /data/asan/permitted_path1
namespace.default.asan.permitted.paths += /permitted_path1
namespace.default.asan.permitted.paths += /apex/permitted_path2
namespace.default.hwasan.search.paths = /search_path1/hwasan
namespace.default.hwasan.search.paths += /search_path1
namespace.default.hwasan.search.paths += /apex/search_path2/hwasan
namespace.default.hwasan.search.paths += /apex/search_path2
namespace.default.hwasan.permitted.paths = /permitted_path1/hwasan
namespace.default.hwasan.permitted.paths += /permitted_path1
namespace.default.hwasan.permitted.paths += /apex/permitted_path2/hwasan
namespace.default.hwasan.permitted.paths += /apex/permitted_path2
)";

TEST(linkerconfig_section, section_with_namespaces) {
  ConfigWriter writer;

  std::vector<Namespace> namespaces;

  namespaces.emplace_back(CreateNamespaceWithLinks(
      "default", true, true, "namespace1", "namespace2"));
  namespaces.emplace_back(CreateNamespaceWithLinks(
      "namespace1", false, false, "default", "namespace2"));
  namespaces.emplace_back(CreateNamespaceWithPaths("namespace2", false, false));

  Section section("test_section", std::move(namespaces));

  section.WriteConfig(writer);
  auto config = writer.ToString();
  ASSERT_EQ(kSectionWithNamespacesExpectedResult, config);
}

TEST(linkerconfig_section, section_with_one_namespace) {
  android::linkerconfig::modules::ConfigWriter writer;

  std::vector<Namespace> namespaces;
  namespaces.emplace_back(CreateNamespaceWithPaths("default", false, false));

  Section section("test_section", std::move(namespaces));
  section.WriteConfig(writer);
  auto config = writer.ToString();
  ASSERT_EQ(kSectionWithOneNamespaceExpectedResult, config);
}

TEST(linkerconfig_section, resolve_contraints) {
  BaseContext ctx;
  std::vector<Namespace> namespaces;
  Namespace& foo = namespaces.emplace_back("foo");
  foo.AddProvides(std::vector{"libfoo.so"});
  foo.AddRequires(std::vector{"libbar.so"});
  Namespace& bar = namespaces.emplace_back("bar");
  bar.AddProvides(std::vector{"libbar.so"});
  Namespace& baz = namespaces.emplace_back("baz");
  baz.AddRequires(std::vector{"libfoo.so"});

  Section section("section", std::move(namespaces));
  section.Resolve(ctx);

  ConfigWriter writer;
  section.WriteConfig(writer);

  ASSERT_EQ(
      "[section]\n"
      "additional.namespaces = bar,baz,foo\n"
      "namespace.bar.isolated = false\n"
      "namespace.baz.isolated = false\n"
      "namespace.baz.links = foo\n"
      "namespace.baz.link.foo.shared_libs = libfoo.so\n"
      "namespace.foo.isolated = false\n"
      "namespace.foo.links = bar\n"
      "namespace.foo.link.bar.shared_libs = libbar.so\n",
      writer.ToString());
}

TEST(linkerconfig_section, error_if_duplicate_providing) {
  BaseContext ctx;
  // TODO(b/297821005) : remove vendor / product vndk version set up
  android::linkerconfig::modules::Variables::AddValue("ro.vndk.version", "99");
  android::linkerconfig::modules::Variables::AddValue("ro.product.vndk.version",
                                                      "99");
  std::vector<Namespace> namespaces;
  Namespace& foo1 = namespaces.emplace_back("foo1");
  foo1.AddProvides(std::vector{"libfoo.so"});
  Namespace& foo2 = namespaces.emplace_back("foo2");
  foo2.AddProvides(std::vector{"libfoo.so"});
  Namespace& bar = namespaces.emplace_back("bar");
  bar.AddRequires(std::vector{"libfoo.so"});

  Section section("section", std::move(namespaces));
  ASSERT_EXIT(section.Resolve(ctx),
              testing::KilledBySignal(SIGABRT),
#ifndef __ANDROID__
              "duplicate: libfoo\\.so is provided by foo1 and foo2"
#else
              ""
#endif
  );
}

TEST(linkerconfig_section, error_if_no_providers_in_strict_mode) {
  BaseContext ctx;
  ctx.SetStrictMode(true);

  std::vector<Namespace> namespaces;
  Namespace& foo = namespaces.emplace_back("foo");
  foo.AddRequires(std::vector{"libfoo.so"});

  Section section("section", std::move(namespaces));
  ASSERT_EXIT(section.Resolve(ctx),
              testing::KilledBySignal(SIGABRT),
#ifndef __ANDROID__
              "not found: libfoo\\.so is required by foo"
#else
              ""
#endif
  );
}

TEST(linkerconfig_section, ignore_unmet_requirements) {
  BaseContext ctx;
  ctx.SetStrictMode(false);  // default

  std::vector<Namespace> namespaces;
  Namespace& foo = namespaces.emplace_back("foo");
  foo.AddRequires(std::vector{"libfoo.so"});

  Section section("section", std::move(namespaces));
  section.Resolve(ctx);

  ConfigWriter writer;
  section.WriteConfig(writer);

  ASSERT_EQ(
      "[section]\n"
      "namespace.foo.isolated = false\n",
      writer.ToString());
}

TEST(linkerconfig_section, resolve_section_with_apex) {
  BaseContext ctx;
  ctx.SetApexModules(
      {ApexInfo("foo", "", {"a.so"}, {"b.so"}, {}, {}, true, true, false, false),
       ApexInfo("bar", "", {"b.so"}, {}, {}, {}, true, true, false, false),
       ApexInfo(
           "baz", "", {"c.so"}, {"a.so"}, {}, {}, true, true, false, false)});
  std::vector<Namespace> namespaces;
  Namespace& default_ns = namespaces.emplace_back("default");
  default_ns.AddRequires(std::vector{"a.so", "b.so"});

  Section section("section", std::move(namespaces));
  section.Resolve(ctx);

  EXPECT_THAT(
      std::vector<std::string>{"a.so"},
      ::testing::ContainerEq(
          section.GetNamespace("default")->GetLink("foo").GetSharedLibs()));
  EXPECT_THAT(
      std::vector<std::string>{"b.so"},
      ::testing::ContainerEq(
          section.GetNamespace("default")->GetLink("bar").GetSharedLibs()));
  EXPECT_THAT(std::vector<std::string>{"b.so"},
              ::testing::ContainerEq(
                  section.GetNamespace("foo")->GetLink("bar").GetSharedLibs()));
  EXPECT_EQ(nullptr, section.GetNamespace("baz"));
}

TEST(linkerconfig_section, resolve_link_modifiers) {
  BaseContext ctx;
  std::vector<Namespace> namespaces;
  Namespace& default_ns = namespaces.emplace_back("default");
  default_ns.AddRequires(std::vector{":foo", ":bar"});

  LibProviders providers;
  providers[":foo"].emplace_back(LibProvider{
      "foo",
      []() { return Namespace("foo"); },
      AllowAllSharedLibs{},
  });
  providers[":bar"].emplace_back(LibProvider{
      "bar",
      []() { return Namespace("bar"); },
      SharedLibs{{"libbar.so"}},
  });

  Section section("section", std::move(namespaces));
  section.Resolve(ctx, providers);

  EXPECT_TRUE(
      section.GetNamespace("default")->GetLink("foo").IsAllSharedLibsAllowed());
  EXPECT_THAT(section.GetNamespace("default")->GetLink("bar").GetSharedLibs(),
              ::testing::ContainerEq(std::vector<std::string>{"libbar.so"}));
}
