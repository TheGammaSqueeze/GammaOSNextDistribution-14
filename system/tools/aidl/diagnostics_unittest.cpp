/*
 * Copyright (C) 2020, The Android Open Source Project
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
#include "diagnostics.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <string>
#include <vector>

#include "aidl.h"
#include "parser.h"
#include "tests/fake_io_delegate.h"

using android::aidl::AidlTypenames;
using android::aidl::DiagnosticID;
using android::aidl::Options;
using android::aidl::internals::load_and_validate_aidl;
using android::aidl::test::FakeIoDelegate;
using testing::internal::CaptureStderr;
using testing::internal::GetCapturedStderr;

struct DiagnosticsTest : testing::Test {
  void ParseFiles(std::vector<std::pair<std::string, std::string>>&& files) {
    ASSERT_TRUE(files.size() > 0);
    const std::string main = files.begin()->first;
    for (const auto& [file, contents] : files) {
      io.SetFileContents(file, contents);
    }
    if (!enable_diagnostic) {
      ASSERT_TRUE(expect_diagnostic);
      enable_diagnostic = expect_diagnostic;
    }
    // emit diagnostics as warnings.
    // "java" has no specific meaning here because we're testing CheckValid()
    const Options options = Options::From("aidl " + optional_args + " -I . --lang java -o out -W" +
                                          to_string(*enable_diagnostic) + " " + main);
    CaptureStderr();
    load_and_validate_aidl(main, options, io, &typenames, nullptr);
    const std::string err = GetCapturedStderr();
    if (expect_diagnostic) {
      EXPECT_THAT(err, testing::HasSubstr("-W" + to_string(*expect_diagnostic)));
    } else {
      EXPECT_EQ("", err);
    }
  }

  AidlTypenames typenames;
  FakeIoDelegate io;
  std::string optional_args;
  // The type of diagnostic to enable for the test. If expect_diagnostic is
  // set, use the same value.
  std::optional<DiagnosticID> enable_diagnostic;
  // The expected diagnostic. Must be set.
  std::optional<DiagnosticID> expect_diagnostic;
};

TEST_F(DiagnosticsTest, const_name_ForEnumerator) {
  expect_diagnostic = DiagnosticID::const_name;
  ParseFiles({{"Foo.aidl", "enum Foo { foo }"}});
}

TEST_F(DiagnosticsTest, const_name_ForConstants) {
  expect_diagnostic = DiagnosticID::const_name;
  ParseFiles({{"IFoo.aidl", "interface IFoo { const int foo = 1; }"}});
}

TEST_F(DiagnosticsTest, interface_name) {
  expect_diagnostic = DiagnosticID::interface_name;
  ParseFiles({{"Foo.aidl", "interface Foo { }"}});
}

TEST_F(DiagnosticsTest, enum_explicit_default) {
  expect_diagnostic = DiagnosticID::enum_explicit_default;
  ParseFiles({{"Foo.aidl", "parcelable Foo { E e; }"}, {"E.aidl", "enum E { A }"}});
}

TEST_F(DiagnosticsTest, inout_parameter) {
  expect_diagnostic = DiagnosticID::inout_parameter;
  ParseFiles({{"IFoo.aidl", "interface IFoo { void foo(inout Bar bar); }"},
              {"Bar.aidl", "parcelable Bar {}"}});
}

TEST_F(DiagnosticsTest, inout_parameter_SuppressAtMethodLevel) {
  enable_diagnostic = DiagnosticID::inout_parameter;
  expect_diagnostic = {};
  ParseFiles({
      {"IFoo.aidl",
       "interface IFoo { @SuppressWarnings(value={\"inout-parameter\"}) void foo(inout Bar b); }"},
      {"Bar.aidl", "parcelable Bar {}"},
  });
}

TEST_F(DiagnosticsTest, inout_parameter_SuppressAtDeclLevel) {
  enable_diagnostic = DiagnosticID::inout_parameter;
  expect_diagnostic = {};
  ParseFiles({
      {"IFoo.aidl",
       "@SuppressWarnings(value={\"inout-parameter\"}) interface IFoo { void foo(inout Bar b); }"},
      {"Bar.aidl", "parcelable Bar {}"},
  });
}

TEST_F(DiagnosticsTest, UnknownWarning) {
  expect_diagnostic = DiagnosticID::unknown_warning;
  ParseFiles({
      {"IFoo.aidl", "@SuppressWarnings(value={\"blahblah\"}) interface IFoo { void foo(); }"},
  });
}

TEST_F(DiagnosticsTest, CantSuppressUnknownWarning) {
  expect_diagnostic = DiagnosticID::unknown_warning;
  ParseFiles({
      {"IFoo.aidl",
       "@SuppressWarnings(value={\"unknown-warning\"})\n"
       "interface IFoo { @SuppressWarnings(value={\"blah-blah\"}) void foo(); }"},
  });
}

TEST_F(DiagnosticsTest, DontMixOnewayWithTwowayMethods) {
  expect_diagnostic = DiagnosticID::mixed_oneway;
  ParseFiles({
      {"IFoo.aidl", "interface IFoo { void foo(); oneway void bar(); }"},
  });
}

TEST_F(DiagnosticsTest, DontMixOnewayWithTwowayMethodsSuppressedAtMethod) {
  enable_diagnostic = DiagnosticID::mixed_oneway;
  expect_diagnostic = {};
  ParseFiles({
      {"IFoo.aidl",
       "interface IFoo {\n"
       "  void foo();\n"
       "  @SuppressWarnings(value={\"mixed-oneway\"}) oneway void bar();\n"
       "}"},
  });
}

TEST_F(DiagnosticsTest, OnewayInterfaceIsOkayWithSyntheticMethods) {
  optional_args = "--version 2";  // will add getInterfaceVersion() synthetic method
  enable_diagnostic = DiagnosticID::mixed_oneway;
  expect_diagnostic = {};
  ParseFiles({
      {"IFoo.aidl", "oneway interface IFoo { void foo(); }"},
  });
}

TEST_F(DiagnosticsTest, RedundantOnewayMethodAnnotationInOnewayInterface) {
  expect_diagnostic = DiagnosticID::redundant_oneway;
  ParseFiles({
      {"IFoo.aidl", "oneway interface IFoo { oneway void foo(int a); }"},
  });
}

TEST_F(DiagnosticsTest, RedundantOnewayMethodSuppressedAtMethod) {
  enable_diagnostic = DiagnosticID::redundant_oneway;
  expect_diagnostic = {};
  ParseFiles({
      {"IFoo.aidl",
       "oneway interface IFoo {\n"
       "  @SuppressWarnings(value={\"redundant-oneway\"}) oneway void bar();\n"
       "}"},
  });
}

TEST_F(DiagnosticsTest, ArraysAsOutputParametersConsideredHarmful) {
  expect_diagnostic = DiagnosticID::out_array;
  ParseFiles({
      {"IFoo.aidl", "interface IFoo { void foo(out String[] ret); }"},
  });
}

TEST_F(DiagnosticsTest, file_descriptor) {
  expect_diagnostic = DiagnosticID::file_descriptor;
  ParseFiles({{"IFoo.aidl",
               "interface IFoo {\n"
               "  void foo(in FileDescriptor fd);\n"
               "}"}});
}

TEST_F(DiagnosticsTest, out_nullable) {
  expect_diagnostic = DiagnosticID::out_nullable;
  ParseFiles({{"IFoo.aidl",
               "interface IFoo {\n"
               "  void foo(out @nullable Bar bar);\n"
               "}"},
              {"Bar.aidl", "parcelable Bar {}"}});
}

TEST_F(DiagnosticsTest, inout_nullable) {
  expect_diagnostic = DiagnosticID::out_nullable;
  ParseFiles({{"IFoo.aidl",
               "interface IFoo {\n"
               "  void foo(inout @nullable Bar bar);\n"
               "}"},
              {"Bar.aidl", "parcelable Bar {}"}});
}

TEST_F(DiagnosticsTest, out_nullable_OkayForArrays) {
  expect_diagnostic = DiagnosticID::out_array;  // not triggering out_nullable
  ParseFiles({{"IFoo.aidl",
               "interface IFoo {\n"
               "  void foo(inout @nullable Bar[] bar1, out @nullable Bar[] bar2);\n"
               "}"},
              {"Bar.aidl", "parcelable Bar {}"}});
}

TEST_F(DiagnosticsTest, RejectImportsCollisionWithTopLevelDecl) {
  expect_diagnostic = DiagnosticID::unique_import;
  ParseFiles({{"p/IFoo.aidl",
               "package p;\n"
               "import q.IFoo;\n"  // should collide with previous import
               "interface IFoo{}"},
              {"q/IFoo.aidl", "package q; interface IFoo{}"}});
}

TEST_F(DiagnosticsTest, RejectImportsCollision) {
  expect_diagnostic = DiagnosticID::unique_import;
  ParseFiles({{"p/IFoo.aidl",
               "package p;\n"
               "import q.IBar;\n"
               "import r.IBar;\n"  // should collide with previous import
               "interface IFoo{}"},
              {"q/IBar.aidl", "package q; interface IBar{}"},
              {"r/IBar.aidl", "package r; interface IBar{}"}});
}

TEST_F(DiagnosticsTest, AllowImportingSelf) {
  enable_diagnostic = DiagnosticID::unique_import;
  expect_diagnostic = {};
  ParseFiles({{"p/IFoo.aidl",
               "package p;\n"
               "import p.IFoo;\n"
               "interface IFoo{}"}});
}

TEST_F(DiagnosticsTest, RedundantImports) {
  expect_diagnostic = DiagnosticID::unique_import;
  ParseFiles({{"p/IFoo.aidl",
               "package p;\n"
               "import q.IBar;\n"
               "import q.IBar;\n"
               "interface IFoo{}"},
              {"q/IBar.aidl", "package q; interface IBar{}"}});
}

TEST_F(DiagnosticsTest, UntypedCollectionInterface) {
  expect_diagnostic = DiagnosticID::untyped_collection;
  ParseFiles({{"IFoo.aidl", "interface IFoo { void foo(in Map m); }"}});
}

TEST_F(DiagnosticsTest, UntypedCollectionParcelable) {
  expect_diagnostic = DiagnosticID::untyped_collection;
  ParseFiles({{"Foo.aidl", "parcelable Foo { Map m; }"}});
}

TEST_F(DiagnosticsTest, UntypedCollectionUnion) {
  expect_diagnostic = DiagnosticID::untyped_collection;
  ParseFiles({{"Foo.aidl", "union Foo { List l; }"}});
}

TEST_F(DiagnosticsTest, UntypedCollectionInTypeArg) {
  expect_diagnostic = DiagnosticID::untyped_collection;
  ParseFiles({{"IFoo.aidl", "interface IFoo { void foo(in Bar<Map> m); }"},
              {"Bar.aidl", "parcelable Bar<T> {}"}});
}

TEST_F(DiagnosticsTest, PermissionMissing) {
  expect_diagnostic = DiagnosticID::missing_permission_annotation;
  ParseFiles({{"IFoo.aidl", "interface IFoo { void food(); }"}});
}

TEST_F(DiagnosticsTest, PermissionMethod) {
  enable_diagnostic = DiagnosticID::missing_permission_annotation;
  expect_diagnostic = {};
  ParseFiles({{"IFoo.aidl", "interface IFoo { @EnforcePermission(\"INTERNET\") void food(); }"}});
}

TEST_F(DiagnosticsTest, PermissionMethodMissing) {
  expect_diagnostic = DiagnosticID::missing_permission_annotation;
  ParseFiles({{"IFoo.aidl",
               "interface IFoo { @EnforcePermission(\"INTERNET\") void food(); void foo2(); }"}});
}

TEST_F(DiagnosticsTest, PermissionInterface) {
  enable_diagnostic = DiagnosticID::missing_permission_annotation;
  expect_diagnostic = {};
  ParseFiles({{"IFoo.aidl", "@EnforcePermission(\"INTERNET\") interface IFoo { void food(); }"}});
}

TEST_F(DiagnosticsTest, RedundantPrefixConstantInterface) {
  enable_diagnostic = DiagnosticID::redundant_name;
  expect_diagnostic = DiagnosticID::redundant_name;
  ParseFiles({{"SomethingStatus.aidl", "interface SomethingStatus { const int STATUS_ONE = 1; }"}});
}

TEST_F(DiagnosticsTest, RedundantPrefixConstantParcelable) {
  enable_diagnostic = DiagnosticID::redundant_name;
  expect_diagnostic = DiagnosticID::redundant_name;
  ParseFiles(
      {{"SomethingStatus.aidl", "parcelable SomethingStatus { const int STATUS_ONE = 1; }"}});
}

TEST_F(DiagnosticsTest, RedundantSuffixConstantParcelable) {
  enable_diagnostic = DiagnosticID::redundant_name;
  expect_diagnostic = DiagnosticID::redundant_name;
  ParseFiles(
      {{"SomethingStatus.aidl", "parcelable SomethingStatus { const int ONE_STATUS = 1; }"}});
}

TEST_F(DiagnosticsTest, RedundantSuffixConstantParcelable2) {
  enable_diagnostic = DiagnosticID::redundant_name;
  expect_diagnostic = DiagnosticID::redundant_name;
  ParseFiles(
      {{"SomethingStatus.aidl", "parcelable SomethingStatus { const int ONE_SOMETHING = 1; }"}});
}

TEST_F(DiagnosticsTest, RedundantConstantUnion) {
  enable_diagnostic = DiagnosticID::redundant_name;
  expect_diagnostic = DiagnosticID::redundant_name;
  ParseFiles({{"SomethingStatus.aidl",
               "union SomethingStatus { const int ONE_SOMETHING = 1; int a; int b;}"}});
}

TEST_F(DiagnosticsTest, RedundantPrefixEnum) {
  enable_diagnostic = DiagnosticID::redundant_name;
  expect_diagnostic = DiagnosticID::redundant_name;
  ParseFiles({{"SomethingStatus.aidl", "enum SomethingStatus { STATUS_ONE = 1, }"}});
}

TEST_F(DiagnosticsTest, RedundantSuffixEnum) {
  enable_diagnostic = DiagnosticID::redundant_name;
  expect_diagnostic = DiagnosticID::redundant_name;
  ParseFiles({{"SomethingStatus.aidl", "enum SomethingStatus { ONE_STATUS = 1, }"}});
}

TEST_F(DiagnosticsTest, RedundantSuffixEnum2) {
  enable_diagnostic = DiagnosticID::redundant_name;
  expect_diagnostic = DiagnosticID::redundant_name;
  ParseFiles({{"SomethingStatus.aidl", "enum SomethingStatus { ONE_SOMETHING = 1, }"}});
}
