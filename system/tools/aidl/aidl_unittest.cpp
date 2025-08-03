/*
 * Copyright (C) 2015, The Android Open Source Project
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

#include "aidl.h"

#include <android-base/format.h>
#include <android-base/stringprintf.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <map>
#include <memory>
#include <set>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <variant>
#include <vector>

#include "aidl_checkapi.h"
#include "aidl_dumpapi.h"
#include "aidl_language.h"
#include "aidl_to_cpp.h"
#include "aidl_to_cpp_common.h"
#include "aidl_to_java.h"
#include "aidl_to_ndk.h"
#include "aidl_to_rust.h"
#include "comments.h"
#include "logging.h"
#include "options.h"
#include "parser.h"
#include "preprocess.h"
#include "tests/fake_io_delegate.h"

using android::aidl::test::FakeIoDelegate;
using android::base::StringPrintf;
using std::map;
using std::set;
using std::string;
using std::unique_ptr;
using std::variant;
using std::vector;
using testing::HasSubstr;
using testing::TestParamInfo;
using testing::internal::CaptureStderr;
using testing::internal::GetCapturedStderr;

namespace android {
namespace aidl {
namespace {

const char kExpectedDepFileContents[] =
R"(place/for/output/p/IFoo.java : \
  p/IFoo.aidl

p/IFoo.aidl :
)";

const char kExpectedNinjaDepFileContents[] =
R"(place/for/output/p/IFoo.java : \
  p/IFoo.aidl
)";

const char kExpectedParcelableDeclarationDepFileContents[] =
    R"( : \
  p/Foo.aidl

p/Foo.aidl :
)";

const char kExpectedStructuredParcelableDepFileContents[] =
    R"(place/for/output/p/Foo.java : \
  p/Foo.aidl

p/Foo.aidl :
)";

}  // namespace

const string INVALID_INT8_VALUE = "Invalid type specifier for an int8 literal";
const string INVALID_FLOAT_VALUE = "Invalid type specifier for a literal float";
const string INVALID_OPERATION = "Cannot perform operation";

class AidlTest : public ::testing::TestWithParam<Options::Language> {
 protected:
  AidlDefinedType* Parse(const string& path, const string& contents, AidlTypenames& typenames_,
                         Options::Language lang, AidlError* error = nullptr,
                         const vector<string> additional_arguments = {}) {
    io_delegate_.SetFileContents(path, contents);
    vector<string> args;
    args.emplace_back("aidl");
    args.emplace_back("--min_sdk_version=current");
    args.emplace_back("--lang=" + to_string(lang));
    for (const string& s : additional_arguments) {
      args.emplace_back(s);
    }
    for (const string& f : preprocessed_files_) {
      args.emplace_back("--preprocessed=" + f);
    }
    args.emplace_back("--include=.");
    for (const string& i : import_paths_) {
      args.emplace_back("--include=" + i);
    }
    args.emplace_back(path);
    Options options = Options::From(args);
    vector<string> imported_files;
    ImportResolver import_resolver{io_delegate_, path, import_paths_};
    AidlError actual_error = ::android::aidl::internals::load_and_validate_aidl(
        path, options, io_delegate_, &typenames_, &imported_files);

    if (error != nullptr) {
      *error = actual_error;
    }

    if (actual_error != AidlError::OK) {
      return nullptr;
    }

    const auto& defined_types = typenames_.MainDocument().DefinedTypes();
    EXPECT_EQ(1ul, defined_types.size());

    return defined_types.front().get();
  }

  void EvaluateInvalidAssignment(string content, string expected_stderr, AidlTypenames& typenames_,
                                 Options::Language lang) {
    AidlError error;
    CaptureStderr();
    EXPECT_EQ(nullptr, Parse("a/IFoo.aidl", content, typenames_, lang, &error));
    EXPECT_THAT(GetCapturedStderr(), HasSubstr(expected_stderr));
  };

  void EvaluateValidAssignment(string content, string expected_stderr, AidlTypenames& typenames_,
                               Options::Language lang) {
    AidlError error;
    CaptureStderr();
    EXPECT_NE(nullptr, Parse("a/IFoo.aidl", content, typenames_, lang, &error));
    EXPECT_THAT(GetCapturedStderr(), HasSubstr(expected_stderr));
  };

  Options::Language GetLanguage() { return GetParam(); }

  FakeIoDelegate io_delegate_;
  vector<string> preprocessed_files_;
  set<string> import_paths_;
  AidlTypenames typenames_;
};

// Instantiate the AidlTest parameterized suite, calling all of the TEST_P
// tests with each of the supported languages as a parameter.
INSTANTIATE_TEST_SUITE_P(AidlTestSuite, AidlTest,
                         testing::Values(Options::Language::CPP, Options::Language::JAVA,
                                         Options::Language::NDK, Options::Language::RUST),
                         [](const testing::TestParamInfo<Options::Language>& info) {
                           return to_string(info.param);
                         });

TEST_P(AidlTest, AcceptMissingPackage) {
  EXPECT_NE(nullptr, Parse("IFoo.aidl", "interface IFoo { }", typenames_, GetLanguage()));
}

TEST_P(AidlTest, EndsInSingleLineComment) {
  EXPECT_NE(nullptr, Parse("IFoo.aidl", "interface IFoo { } // foo", typenames_, GetLanguage()));
}

TEST_P(AidlTest, InterfaceRequiresCorrectPath) {
  const string expected_stderr =
      "ERROR: a/Foo.aidl:1.11-21: IBar should be declared in a file called a/IBar.aidl\n";
  const std::string file_contents = "package a; interface IBar {}";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("a/Foo.aidl", file_contents, typenames_, GetLanguage()));
  EXPECT_EQ(expected_stderr, GetCapturedStderr()) << file_contents;
}

TEST_P(AidlTest, ParcelableRequiresCorrectPath) {
  const string expected_stderr =
      "ERROR: a/Foo.aidl:1.11-21: Bar should be declared in a file called a/Bar.aidl\n";
  const std::string file_contents = "package a; interface Bar {}";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("a/Foo.aidl", file_contents, typenames_, GetLanguage()));
  EXPECT_EQ(expected_stderr, GetCapturedStderr()) << file_contents;
}

TEST_P(AidlTest, UnstructuredParcelableRequiresCorrectPath) {
  const string expected_stderr =
      "ERROR: a/Foo.aidl:1.22-26: Bar should be declared in a file called a/Bar.aidl\n";
  const std::string file_contents = "package a; parcelable Bar cpp_header \"anything.h\";";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("a/Foo.aidl", file_contents, typenames_, GetLanguage()));
  EXPECT_EQ(expected_stderr, GetCapturedStderr()) << file_contents;
}

TEST_P(AidlTest, EnumRequiresCorrectPath) {
  const string expected_stderr =
      "ERROR: a/Foo.aidl:1.16-20: Bar should be declared in a file called a/Bar.aidl\n";
  const std::string file_contents = "package a; enum Bar { A, }";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("a/Foo.aidl", file_contents, typenames_, GetLanguage()));
  EXPECT_EQ(expected_stderr, GetCapturedStderr()) << file_contents;
}

TEST_P(AidlTest, SupportOnlyOutParameters) {
  const string interface_list = "package a; interface IBar { void f(out List<String> bar); }";
  EXPECT_NE(nullptr, Parse("a/IBar.aidl", interface_list, typenames_, GetLanguage()));
}

TEST_P(AidlTest, RejectOutParametersForIBinder) {
  const string interface_ibinder = "package a; interface IBaz { void f(out IBinder bar); }";
  const string expected_ibinder_stderr =
      "ERROR: a/IBaz.aidl:1.47-51: 'bar' can't be an out parameter because IBinder can only be an "
      "in parameter.\n";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("a/IBaz.aidl", interface_ibinder, typenames_, GetLanguage()));
  EXPECT_EQ(expected_ibinder_stderr, GetCapturedStderr());
}

TEST_P(AidlTest, RejectsOutParametersInOnewayInterface) {
  const string oneway_interface = "package a; oneway interface IBar { void f(out int[] bar); }";
  const string expected_stderr =
      "ERROR: a/IBar.aidl:1.40-42: oneway method 'f' cannot have out parameters\n";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("a/IBar.aidl", oneway_interface, typenames_, GetLanguage()));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_P(AidlTest, RejectsOutParametersInOnewayMethod) {
  const string oneway_method = "package a; interface IBar { oneway void f(out int[] bar); }";
  const string expected_stderr =
      "ERROR: a/IBar.aidl:1.40-42: oneway method 'f' cannot have out parameters\n";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("a/IBar.aidl", oneway_method, typenames_, GetLanguage()));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_P(AidlTest, RejectsOnewayNonVoidReturn) {
  const string oneway_method = "package a; interface IFoo { oneway int f(); }";
  const string expected_stderr =
      "ERROR: a/IFoo.aidl:1.39-41: oneway method 'f' cannot return a value\n";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("a/IFoo.aidl", oneway_method, typenames_, GetLanguage()));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_P(AidlTest, RejectsNullablePrimitive) {
  const string oneway_method = "package a; interface IFoo { @nullable int f(); }";
  const string expected_stderr =
      "ERROR: a/IFoo.aidl:1.38-42: Primitive type cannot get nullable annotation\n";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("a/IFoo.aidl", oneway_method, typenames_, GetLanguage()));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_P(AidlTest, AcceptNullableList) {
  const string oneway_method = "package a; interface IFoo { @nullable List<String> f(); }";
  const string expected_stderr = "";
  CaptureStderr();
  EXPECT_NE(nullptr, Parse("a/IFoo.aidl", oneway_method, typenames_, GetLanguage()));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_P(AidlTest, RejectRecursiveParcelable) {
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("Foo.aidl", "parcelable Foo { Foo foo; }", typenames_, GetLanguage()));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr("Foo is a recursive parcelable"));
}

TEST_P(AidlTest, RejectIndirectRecursiveParcelable) {
  io_delegate_.SetFileContents("Bar.aidl", "parcelable Bar { Foo foo; }");
  import_paths_.emplace("");
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("Foo.aidl", "parcelable Foo { Bar bar; }", typenames_, GetLanguage()));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr("Foo is a recursive parcelable"));
}

TEST_P(AidlTest, RejectRecursiveTypeEvenIfNullable) {
  // Note: in native backends @nullable is mapped to non-heap wrapper like std::optional/Option<T>
  io_delegate_.SetFileContents("Bar.aidl", "parcelable Bar { @nullable Foo foo; }");
  import_paths_.emplace("");
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("Foo.aidl", "parcelable Foo { Bar bar; }", typenames_, GetLanguage()));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr("Foo is a recursive parcelable"));
}

TEST_P(AidlTest, OkayIfRecursionInvolvesHeapType) {
  CaptureStderr();
  std::string java_only_map_field;
  if (GetLanguage() == Options::Language::JAVA) {
    java_only_map_field = "  Map<String, Foo> map;\n";
  }
  EXPECT_NE(nullptr, Parse("Foo.aidl",
                           "parcelable Foo {\n"
                           "  List<Foo> list;\n" +
                               java_only_map_field +
                               "  Foo[] arr;\n"
                               "  @nullable(heap=true) Foo heap_nullable;\n"
                               "}\n",
                           typenames_, GetLanguage()));
  EXPECT_THAT(GetCapturedStderr(), "");
}

TEST_P(AidlTest, InterfaceCanReferenceItself) {
  CaptureStderr();
  EXPECT_NE(nullptr, Parse("IFoo.aidl", "interface IFoo { void foo(in IFoo self); }", typenames_,
                           GetLanguage()));
  EXPECT_THAT(GetCapturedStderr(), "");
}

TEST_P(AidlTest, HeapNullableCantApplyToOtherThanParcelables) {
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("Foo.aidl",
                           "parcelable Foo {\n"
                           "  @nullable(heap=true) String s;\n"
                           "}",
                           typenames_, GetLanguage()));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr("@nullable(heap=true) is available to parcelables"));
}

TEST_P(AidlTest, RejectsDuplicatedArgumentNames) {
  const string method = "package a; interface IFoo { void f(int a, int a); }";
  const string expected_stderr =
      "ERROR: a/IFoo.aidl:1.33-35: method 'f' has duplicate argument name 'a'\n";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("a/IFoo.aidl", method, typenames_, GetLanguage()));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_P(AidlTest, RejectsDuplicatedFieldNames) {
  const string method = "package a; parcelable Foo { int a; String a; }";
  const string expected_stderr = "ERROR: a/Foo.aidl:1.42-44: 'Foo' has duplicate field name 'a'\n";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("a/Foo.aidl", method, typenames_, GetLanguage()));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_P(AidlTest, AcceptsEmptyParcelable) {
  CaptureStderr();
  EXPECT_NE(nullptr, Parse("Foo.aidl", "parcelable Foo {}", typenames_, GetLanguage()));
  EXPECT_EQ("", GetCapturedStderr());
}

TEST_P(AidlTest, RejectsDuplicatedAnnotationParams) {
  const string method = "package a; interface IFoo { @UnsupportedAppUsage(foo=1, foo=2)void f(); }";
  const string expected_stderr = "ERROR: a/IFoo.aidl:1.56-62: Trying to redefine parameter foo.\n";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("a/IFoo.aidl", method, typenames_, GetLanguage()));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_P(AidlTest, RejectUnsupportedInterfaceAnnotations) {
  AidlError error;
  const string method = "package a; @nullable interface IFoo { int f(); }";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("a/IFoo.aidl", method, typenames_, GetLanguage(), &error));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr("@nullable is not available."));
  EXPECT_EQ(AidlError::BAD_TYPE, error);
}

TEST_P(AidlTest, RejectUnsupportedTypeAnnotations) {
  AidlError error;
  const string method = "package a; interface IFoo { @JavaOnlyStableParcelable int f(); }";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("a/IFoo.aidl", method, typenames_, GetLanguage(), &error));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr("@JavaOnlyStableParcelable is not available."));
  EXPECT_EQ(AidlError::BAD_TYPE, error);
}

TEST_P(AidlTest, RejectUnsupportedParcelableDefineAnnotations) {
  AidlError error;
  const string method = "package a; @nullable parcelable Foo { String a; String b; }";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("a/Foo.aidl", method, typenames_, GetLanguage(), &error));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr("@nullable is not available."));
  EXPECT_EQ(AidlError::BAD_TYPE, error);
}

TEST_P(AidlTest, ParsesNonNullableAnnotation) {
  auto parse_result =
      Parse("a/IFoo.aidl", "package a; interface IFoo { String f(); }", typenames_, GetLanguage());
  ASSERT_NE(nullptr, parse_result);
  const AidlInterface* interface = parse_result->AsInterface();
  ASSERT_NE(nullptr, interface);
  ASSERT_FALSE(interface->GetMethods().empty());
  EXPECT_FALSE(interface->GetMethods()[0]->GetType().IsNullable());
}

TEST_P(AidlTest, ParsesNullableAnnotation) {
  auto parse_result = Parse("a/IFoo.aidl", "package a; interface IFoo { @nullable String f(); }",
                            typenames_, GetLanguage());
  ASSERT_NE(nullptr, parse_result);
  const AidlInterface* interface = parse_result->AsInterface();
  ASSERT_NE(nullptr, interface);
  ASSERT_FALSE(interface->GetMethods().empty());
  EXPECT_TRUE(interface->GetMethods()[0]->GetType().IsNullable());
}

TEST_P(AidlTest, ParsesNonUtf8Annotations) {
  auto parse_result =
      Parse("a/IFoo.aidl", "package a; interface IFoo { String f(); }", typenames_, GetLanguage());
  ASSERT_NE(nullptr, parse_result);
  const AidlInterface* interface = parse_result->AsInterface();
  ASSERT_NE(nullptr, interface);
  ASSERT_FALSE(interface->GetMethods().empty());
  EXPECT_FALSE(interface->GetMethods()[0]->GetType().IsUtf8InCpp());
}

TEST_P(AidlTest, ParsesUtf8Annotations) {
  auto parse_result = Parse("a/IFoo.aidl", "package a; interface IFoo { @utf8InCpp String f(); }",
                            typenames_, GetLanguage());
  ASSERT_NE(nullptr, parse_result);
  const AidlInterface* interface = parse_result->AsInterface();
  ASSERT_NE(nullptr, interface);
  ASSERT_FALSE(interface->GetMethods().empty());
  EXPECT_TRUE(interface->GetMethods()[0]->GetType().IsUtf8InCpp());
}

TEST_P(AidlTest, VintfRequiresStructuredAndStability) {
  AidlError error;
  const string expected_stderr =
      "ERROR: IFoo.aidl:1.16-26: Must compile @VintfStability type w/ aidl_interface 'stability: "
      "\"vintf\"'\n"
      "ERROR: IFoo.aidl:1.16-26: Must compile @VintfStability type w/ aidl_interface "
      "--structured\n";
  CaptureStderr();
  ASSERT_EQ(nullptr, Parse("IFoo.aidl", "@VintfStability interface IFoo {}", typenames_,
                           GetLanguage(), &error));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
  ASSERT_EQ(AidlError::NOT_STRUCTURED, error);
}

TEST_P(AidlTest, VintfRequiresStructured) {
  AidlError error;
  const string expected_stderr =
      "ERROR: IFoo.aidl:1.16-26: Must compile @VintfStability type w/ aidl_interface "
      "--structured\n";
  CaptureStderr();
  ASSERT_EQ(nullptr, Parse("IFoo.aidl", "@VintfStability interface IFoo {}", typenames_,
                           GetLanguage(), &error, {"--stability", "vintf"}));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
  ASSERT_EQ(AidlError::NOT_STRUCTURED, error);
}

TEST_P(AidlTest, VintfRequiresSpecifiedStability) {
  AidlError error;
  const string expected_stderr =
      "ERROR: IFoo.aidl:1.16-26: Must compile @VintfStability type w/ aidl_interface 'stability: "
      "\"vintf\"'\n";
  CaptureStderr();
  ASSERT_EQ(nullptr, Parse("IFoo.aidl", "@VintfStability interface IFoo {}", typenames_,
                           GetLanguage(), &error, {"--structured"}));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
  ASSERT_EQ(AidlError::NOT_STRUCTURED, error);
}

TEST_P(AidlTest, ParsesStabilityAnnotations) {
  AidlError error;
  auto parse_result = Parse("IFoo.aidl", "@VintfStability interface IFoo {}", typenames_,
                            GetLanguage(), &error, {"--structured", "--stability", "vintf"});
  ASSERT_EQ(AidlError::OK, error);
  ASSERT_NE(nullptr, parse_result);
  const AidlInterface* interface = parse_result->AsInterface();
  ASSERT_NE(nullptr, interface);
  ASSERT_TRUE(interface->IsVintfStability());
}

TEST_P(AidlTest, TypesShouldHaveVintfStabilityWhenCompilingWithTheVintfFlag) {
  CaptureStderr();
  string code =
      "@VintfStability\n"
      "parcelable Foo {\n"
      "  interface INested { interface INastyNester {} }"
      "}";
  EXPECT_NE(nullptr, Parse("Foo.aidl", code, typenames_, GetLanguage(), nullptr,
                           {"--structured", "--stability", "vintf"}));
  EXPECT_EQ(GetCapturedStderr(), "");
  auto nested = typenames_.TryGetDefinedType("Foo.INested");
  ASSERT_NE(nullptr, nested);
  ASSERT_TRUE(nested->IsVintfStability());

  auto nastyNester = typenames_.TryGetDefinedType("Foo.INested.INastyNester");
  ASSERT_NE(nullptr, nastyNester);
  ASSERT_TRUE(nastyNester->IsVintfStability());
}

TEST_P(AidlTest, VintfStabilityAppliesToNestedTypesAsWell) {
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("Foo.aidl", "parcelable Foo {}", typenames_, GetLanguage(), nullptr,
                           {"--structured", "--stability", "vintf"}));
  EXPECT_THAT(GetCapturedStderr(),
              HasSubstr("Foo does not have VINTF level stability (marked @VintfStability)"));
}

TEST_F(AidlTest, ParsesJavaOnlyStableParcelable) {
  Options java_options = Options::From("aidl -I . -o out --structured a/Foo.aidl");
  Options cpp_options = Options::From("aidl -I . --lang=cpp -o out -h out/include a/Foo.aidl");
  Options cpp_structured_options =
      Options::From("aidl --lang=cpp -I . --structured -o out -h out/include a/Foo.aidl");
  Options rust_options = Options::From("aidl -I . --lang=rust -o out --structured a/Foo.aidl");
  io_delegate_.SetFileContents(
      "a/Foo.aidl",
      StringPrintf("package a; @JavaOnlyStableParcelable parcelable Foo cpp_header \"Foo.h\" ;"));

  EXPECT_TRUE(compile_aidl(java_options, io_delegate_));
  EXPECT_TRUE(compile_aidl(cpp_options, io_delegate_));

  CaptureStderr();
  EXPECT_FALSE(compile_aidl(cpp_structured_options, io_delegate_));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr("Cannot declare unstructured"));

  CaptureStderr();
  EXPECT_FALSE(compile_aidl(rust_options, io_delegate_));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr("Cannot declare unstructured"));
}

TEST_F(AidlTest, ParsesNdkOnlyStableParcelable) {
  Options java_options = Options::From("aidl -I . -o out --structured a/Foo.aidl");
  Options ndk_structured_options =
      Options::From("aidl --lang=ndk --structured -I . -o out -h out/include a/Foo.aidl");
  Options rust_options = Options::From("aidl --lang=rust -I . -o out --structured a/Foo.aidl");
  Options cpp_options = Options::From("aidl --lang=cpp -I . -o out -h out/include a/Foo.aidl");
  io_delegate_.SetFileContents(
      "a/Foo.aidl",
      StringPrintf("package a; @NdkOnlyStableParcelable parcelable Foo cpp_header \"Foo.h\" ;"));

  EXPECT_TRUE(compile_aidl(cpp_options, io_delegate_));

  // not considered unstructured, but it still can't be compiled directly with
  // --structured AIDL - it can only be used as an import
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(ndk_structured_options, io_delegate_));
  EXPECT_THAT(GetCapturedStderr(),
              HasSubstr("Refusing to generate code with unstructured parcelables"));

  CaptureStderr();
  EXPECT_FALSE(compile_aidl(java_options, io_delegate_));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr("Cannot declare unstructured"));

  CaptureStderr();
  EXPECT_FALSE(compile_aidl(rust_options, io_delegate_));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr("Cannot declare unstructured"));
}

TEST_P(AidlTest, NdkAndJavaAndRustStabilityIsVintfStable) {
  CaptureStderr();

  io_delegate_.SetFileContents(
      "NonPortableThing.aidl",
      "@NdkOnlyStableParcelable @JavaOnlyStableParcelable @RustOnlyStableParcelable parcelable "
      "NonPortableThing ndk_header \"lol.h\" cpp_header \"lolol.h\" rust_type \"lol::Lol\";");
  import_paths_.emplace("");

  auto result =
      Parse("IFoo.aidl",
            "import NonPortableThing; @VintfStability interface IFoo { NonPortableThing get(); }",
            typenames_, GetLanguage(), nullptr, {"--structured", "--stability", "vintf"});

  if (GetLanguage() == Options::Language::NDK || GetLanguage() == Options::Language::JAVA ||
      GetLanguage() == Options::Language::RUST) {
    EXPECT_NE(result, nullptr);
    EXPECT_EQ(GetCapturedStderr(), "");
  } else {
    EXPECT_EQ(result, nullptr);
    EXPECT_THAT(
        GetCapturedStderr(),
        HasSubstr("NonPortableThing does not have VINTF level stability (marked @VintfStability)"));
  }
}

TEST_F(AidlTest, ParcelableSupportJavaDeriveToString) {
  io_delegate_.SetFileContents("a/Foo.aidl", R"(package a;
    @JavaDerive(toString=true) parcelable Foo { int a; float b; })");
  Options java_options = Options::From("aidl --lang=java -I . -o out a/Foo.aidl");
  EXPECT_TRUE(compile_aidl(java_options, io_delegate_));

  string java_out;
  EXPECT_TRUE(io_delegate_.GetWrittenContents("out/a/Foo.java", &java_out));
  EXPECT_THAT(java_out, testing::HasSubstr("public String toString() {"));

  // Other backends shouldn't be bothered
  Options cpp_options = Options::From("aidl --lang=cpp -I . -o out -h out a/Foo.aidl");
  EXPECT_TRUE(compile_aidl(cpp_options, io_delegate_));

  Options ndk_options = Options::From("aidl --lang=ndk -I . -o out -h out a/Foo.aidl");
  EXPECT_TRUE(compile_aidl(ndk_options, io_delegate_));
}

TEST_F(AidlTest, UnionSupportJavaDeriveToString) {
  io_delegate_.SetFileContents("a/Foo.aidl", R"(package a;
    @JavaDerive(toString=true) union Foo { int a; int[] b; })");
  CaptureStderr();
  Options java_options = Options::From("aidl --lang=java -I . -o out a/Foo.aidl");
  EXPECT_TRUE(compile_aidl(java_options, io_delegate_));
  EXPECT_EQ("", GetCapturedStderr());
}

TEST_F(AidlTest, ParcelableSupportJavaDeriveEquals) {
  io_delegate_.SetFileContents("a/Foo.aidl", R"(package a;
    @JavaDerive(equals=true) parcelable Foo { int a; float b; })");
  CaptureStderr();
  Options java_options = Options::From("aidl --lang=java -I . -o out a/Foo.aidl");
  EXPECT_TRUE(compile_aidl(java_options, io_delegate_));
  EXPECT_EQ("", GetCapturedStderr());

  const std::string expected = R"--(
  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (other == null) return false;
    if (!(other instanceof Foo)) return false;
    Foo that = (Foo)other;
    if (!java.util.Objects.deepEquals(a, that.a)) return false;
    if (!java.util.Objects.deepEquals(b, that.b)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    return java.util.Arrays.deepHashCode(java.util.Arrays.asList(a, b).toArray());
  }
)--";

  string java_out;
  EXPECT_TRUE(io_delegate_.GetWrittenContents("out/a/Foo.java", &java_out));
  EXPECT_THAT(java_out, testing::HasSubstr(expected));
}

TEST_F(AidlTest, UnionSupportJavaDeriveEquals) {
  io_delegate_.SetFileContents("a/Foo.aidl", R"(package a;
    @JavaDerive(equals=true) union Foo { int a; int[] b; })");
  CaptureStderr();
  Options java_options = Options::From("aidl --lang=java -I . -o out a/Foo.aidl");
  EXPECT_TRUE(compile_aidl(java_options, io_delegate_));
  EXPECT_EQ("", GetCapturedStderr());

  const std::string expected = R"--(
  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (other == null) return false;
    if (!(other instanceof Foo)) return false;
    Foo that = (Foo)other;
    if (_tag != that._tag) return false;
    if (!java.util.Objects.deepEquals(_value, that._value)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    return java.util.Arrays.deepHashCode(java.util.Arrays.asList(_tag, _value).toArray());
  }
)--";

  string java_out;
  EXPECT_TRUE(io_delegate_.GetWrittenContents("out/a/Foo.java", &java_out));
  EXPECT_THAT(java_out, testing::HasSubstr(expected));
}

TEST_F(AidlTest, RejectsJavaDeriveAnnotation) {
  {
    io_delegate_.SetFileContents("a/Foo.aidl",
                                 "package a; @JavaDerive(blah=true) parcelable Foo{}");
    Options java_options = Options::From("aidl --lang=java -I . -o out a/Foo.aidl");
    CaptureStderr();
    EXPECT_FALSE(compile_aidl(java_options, io_delegate_));
    const std::string expected_stderr =
        "ERROR: a/Foo.aidl:1.11-34: Parameter blah not supported for annotation JavaDerive.";
    EXPECT_THAT(GetCapturedStderr(),
                HasSubstr("Parameter blah not supported for annotation JavaDerive."));
  }

  {
    io_delegate_.SetFileContents("a/IFoo.aidl", "package a; @JavaDerive interface IFoo{}");
    Options java_options = Options::From("aidl --lang=java -I . -o out a/IFoo.aidl");
    CaptureStderr();
    EXPECT_FALSE(compile_aidl(java_options, io_delegate_));
    EXPECT_THAT(GetCapturedStderr(), HasSubstr("@JavaDerive is not available."));
  }
}

TEST_P(AidlTest, ParseDescriptorAnnotation) {
  AidlError error;
  auto parse_result = Parse("IFoo.aidl", R"(@Descriptor(value="IBar") interface IFoo{})",
                            typenames_, GetLanguage(), &error, {"--structured"});
  ASSERT_EQ(AidlError::OK, error);
  ASSERT_NE(nullptr, parse_result);
  const AidlInterface* interface = parse_result->AsInterface();
  ASSERT_NE(nullptr, interface);
  ASSERT_EQ("IBar", interface->GetDescriptor());
}

TEST_P(AidlTest, UnknownAnnotation) {
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("a/IFoo.aidl", "package a; @Unknown interface IFoo { }", typenames_,
                           GetLanguage()));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr("not a recognized annotation"));

  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("a/IFoo.aidl", "package a; @Unknown(param=true) interface IFoo { }",
                           typenames_, GetLanguage()));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr("not a recognized annotation"));
}

TEST_P(AidlTest, AcceptsOnewayMethod) {
  const string oneway_method = "package a; interface IFoo { oneway void f(int a); }";
  EXPECT_NE(nullptr, Parse("a/IFoo.aidl", oneway_method, typenames_, GetLanguage()));
}

TEST_P(AidlTest, AcceptsOnewayInterface) {
  const string oneway_interface = "package a; oneway interface IBar { void f(int a); }";
  EXPECT_NE(nullptr, Parse("a/IBar.aidl", oneway_interface, typenames_, GetLanguage()));
}

TEST_P(AidlTest, AcceptsAnnotatedOnewayMethod) {
  const string oneway_method =
      "package a; interface IFoo { @UnsupportedAppUsage oneway void f(int a); }";
  EXPECT_NE(nullptr, Parse("a/IFoo.aidl", oneway_method, typenames_, GetLanguage()));
}

TEST_P(AidlTest, AnnotationsInMultiplePlaces) {
  const string oneway_method =
      "package a; interface IFoo { @UnsupportedAppUsage oneway @PropagateAllowBlocking void f(int "
      "a); }";
  const AidlDefinedType* defined = Parse("a/IFoo.aidl", oneway_method, typenames_, GetLanguage());
  ASSERT_NE(nullptr, defined);
  const AidlInterface* iface = defined->AsInterface();
  ASSERT_NE(nullptr, iface);

  const auto& methods = iface->GetMethods();
  ASSERT_EQ(1u, methods.size());
  const auto& method = methods[0];
  const AidlTypeSpecifier& ret_type = method->GetType();

  // TODO(b/151102494): these annotations should be on the method
  ASSERT_NE(nullptr, ret_type.UnsupportedAppUsage());
  ASSERT_TRUE(ret_type.IsPropagateAllowBlocking());
}

TEST_P(AidlTest, AnnotationValueAttribute) {
  const string content =
      "package a; @Descriptor(\"descriptor_value\") interface IFoo { void f(int a); }";
  const AidlDefinedType* defined = Parse("a/IFoo.aidl", content, typenames_, GetLanguage());
  ASSERT_NE(nullptr, defined);
  const AidlInterface* iface = defined->AsInterface();
  ASSERT_NE(nullptr, iface);

  ASSERT_EQ("descriptor_value", iface->GetDescriptor());
}

TEST_F(AidlTest, CheckApiForAnnotationValueAttribute) {
  Options options = Options::From("aidl --checkapi=equal old new");

  io_delegate_.SetFileContents("old/p/IFoo.aidl",
                               "package p; @Descriptor(value=\"v1\") interface IFoo{ void foo();}");
  io_delegate_.SetFileContents("new/p/IFoo.aidl",
                               "package p; @Descriptor(\"v1\") interface IFoo{ void foo();}");

  EXPECT_TRUE(::android::aidl::check_api(options, io_delegate_));
}

TEST_P(AidlTest, WritesComments) {
  string foo_interface =
      R"(package a;
        /* foo */
        interface IFoo {
          /* i */
          int i();
          // j
          @nullable String j();
          // k1
          /* k2 */
          @UnsupportedAppUsage oneway void k(int a);
        })";

  CaptureStderr();
  auto parse_result = Parse("a/IFoo.aidl", foo_interface, typenames_, GetLanguage());
  EXPECT_NE(nullptr, parse_result);
  EXPECT_EQ("", GetCapturedStderr());

  EXPECT_EQ((Comments{{"/* foo */"}}), parse_result->GetComments());

  const AidlInterface* interface = parse_result->AsInterface();
  EXPECT_EQ((Comments{{"/* i */"}}), interface->GetMethods()[0]->GetComments());
  EXPECT_EQ((Comments{{"// j\n"}}), interface->GetMethods()[1]->GetComments());
  EXPECT_EQ((Comments{{"// k1\n"}, {"/* k2 */"}}), interface->GetMethods()[2]->GetComments());
}

TEST_P(AidlTest, CppHeaderCanBeIdentifierAsWell) {
  io_delegate_.SetFileContents("p/cpp_header.aidl",
                               R"(package p;
         parcelable cpp_header cpp_header "bar/header" ndk_header "ndk/bar/header" )"
                               R"(rust_type "package::SomeType";)");
  import_paths_.emplace("");
  const string input_path = "p/IFoo.aidl";
  const string input = R"(package p;
                          import p.cpp_header;
                          interface IFoo {
                            // get bar
                            cpp_header get();
                          })";

  auto parse_result = Parse(input_path, input, typenames_, GetLanguage());
  EXPECT_NE(nullptr, parse_result);
  const AidlInterface* interface = parse_result->AsInterface();
  EXPECT_EQ((Comments{{"// get bar\n"}}), interface->GetMethods()[0]->GetComments());
}

TEST_F(AidlTest, RejectsIfCppHeaderIsMissing) {
  io_delegate_.SetFileContents("Foo.aidl", "parcelable Foo;");
  Options options = Options::From("aidl -I . --lang cpp -h h -o o Foo.aidl");
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr("must have cpp_header defined"));
}

TEST_F(AidlTest, RejectsIfTypeRefsCppHeaderIsMissing) {
  io_delegate_.SetFileContents("Foo.aidl", "parcelable Foo;");
  io_delegate_.SetFileContents("IBar.aidl", "interface IBar { void bar(in Foo foo); }");
  Options options = Options::From("aidl -I . --lang cpp -h h -o o IBar.aidl");
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr("must have cpp_header defined"));
}

TEST_F(AidlTest, ParsesPreprocessedFile) {
  string simple_content = "parcelable a.Foo;\ninterface b.IBar;";
  io_delegate_.SetFileContents("path", simple_content);
  EXPECT_FALSE(typenames_.ResolveTypename("a.Foo").is_resolved);
  EXPECT_TRUE(Parser::Parse("path", io_delegate_, typenames_, /*is_preprocessed=*/true));
  EXPECT_TRUE(typenames_.ResolveTypename("a.Foo").is_resolved);
  EXPECT_TRUE(typenames_.ResolveTypename("b.IBar").is_resolved);
}

TEST_F(AidlTest, ParsesPreprocessedFileWithWhitespace) {
  string simple_content = "parcelable    a.Foo;\n  interface b.IBar  ;\t";
  io_delegate_.SetFileContents("path", simple_content);

  EXPECT_FALSE(typenames_.ResolveTypename("a.Foo").is_resolved);
  EXPECT_TRUE(Parser::Parse("path", io_delegate_, typenames_, /*is_preprocessed=*/true));
  EXPECT_TRUE(typenames_.ResolveTypename("a.Foo").is_resolved);
  EXPECT_TRUE(typenames_.ResolveTypename("b.IBar").is_resolved);
}

TEST_P(AidlTest, PreferImportToPreprocessed) {
  io_delegate_.SetFileContents("preprocessed", "interface another.IBar;");
  io_delegate_.SetFileContents("one/IBar.aidl", "package one; "
                                                "interface IBar {}");
  preprocessed_files_.push_back("preprocessed");
  import_paths_.emplace("");
  auto parse_result = Parse("p/IFoo.aidl", "package p; import one.IBar; interface IFoo {}",
                            typenames_, GetLanguage());
  EXPECT_NE(nullptr, parse_result);

  // We expect to know about both kinds of IBar
  EXPECT_TRUE(typenames_.ResolveTypename("one.IBar").is_resolved);
  EXPECT_TRUE(typenames_.ResolveTypename("another.IBar").is_resolved);
  // But if we request just "IBar" we should get our imported one.
  AidlTypeSpecifier ambiguous_type(AIDL_LOCATION_HERE, "IBar", /*array=*/std::nullopt, nullptr, {});
  ambiguous_type.Resolve(typenames_, parse_result);
  EXPECT_EQ("one.IBar", ambiguous_type.GetName());
}

// Special case of PreferImportToPreprocessed. Imported type should be preferred
// even when the preprocessed file already has the same type.
TEST_P(AidlTest, B147918827) {
  io_delegate_.SetFileContents("preprocessed", "interface another.IBar;\ninterface one.IBar;");
  io_delegate_.SetFileContents("one/IBar.aidl",
                               "package one; "
                               "interface IBar {}");
  preprocessed_files_.push_back("preprocessed");
  import_paths_.emplace("");
  auto parse_result = Parse("p/IFoo.aidl", "package p; import one.IBar; interface IFoo {}",
                            typenames_, GetLanguage());
  EXPECT_NE(nullptr, parse_result);

  // We expect to know about both kinds of IBar
  EXPECT_TRUE(typenames_.ResolveTypename("one.IBar").is_resolved);
  EXPECT_TRUE(typenames_.ResolveTypename("another.IBar").is_resolved);
  // But if we request just "IBar" we should get our imported one.
  AidlTypeSpecifier ambiguous_type(AIDL_LOCATION_HERE, "IBar", /*array=*/std::nullopt, nullptr, {});
  ambiguous_type.Resolve(typenames_, parse_result);
  EXPECT_EQ("one.IBar", ambiguous_type.GetName());
}

TEST_F(AidlTest, WritePreprocessedFile) {
  io_delegate_.SetFileContents("p/Outer.aidl",
                               "package p; parcelable Outer.Inner;");
  io_delegate_.SetFileContents("one/IBar.aidl", "package one; import p.Outer;"
                                                "interface IBar {}");

  vector<string> args{"aidl", "--preprocess", "preprocessed",
                      "-I.",  "p/Outer.aidl", "one/IBar.aidl"};
  Options options = Options::From(args);
  EXPECT_TRUE(::android::aidl::Preprocess(options, io_delegate_));

  std::map<std::string, std::string> expected = {{"preprocessed",
                                                  "parcelable p.Outer.Inner;\n"
                                                  "interface one.IBar {\n"
                                                  "}\n"}};
  EXPECT_THAT(io_delegate_.OutputFiles(), testing::Eq(expected));
}

TEST_F(AidlTest, PreprocessVariousThings) {
  io_delegate_.SetFileContents("foo/bar/IFoo.aidl",
                               "package foo.bar;\n"
                               "interface IFoo {\n"
                               "    int foo();\n"
                               "    const int FOO = foo.bar.Bar.BAR + 1; // should be 44\n"
                               "}\n");
  io_delegate_.SetFileContents("foo/bar/Bar.aidl",
                               "package foo.bar;\n"
                               "parcelable Bar {\n"
                               "    const int BAR = imported.Foo.FOO + 1; // should be 43\n"
                               "    imported.Foo foo;\n"
                               "}\n");
  io_delegate_.SetFileContents("foo/bar/Gen.aidl",
                               "package foo.bar;\n"
                               "parcelable Gen<T> {\n"
                               "}\n");
  io_delegate_.SetFileContents("foo/bar/Enum.aidl",
                               "package foo.bar;\n"
                               "enum Enum {\n"
                               "    FOO = 3, BAR = FOO + 3, // should be 3, 6\n"
                               "}\n");
  io_delegate_.SetFileContents("sub/imported/Foo.aidl",
                               "package imported;\n"
                               "parcelable Foo {\n"
                               "    const int FOO = 42;\n"
                               "}\n");

  vector<string> args = {
      "aidl",
      "--preprocess",
      "preprocessed",
      "-Isub",
      "-I.",
      "foo/bar/IFoo.aidl",
      "foo/bar/Bar.aidl",
      "foo/bar/Gen.aidl",
      "foo/bar/Enum.aidl",
  };
  ASSERT_TRUE(Preprocess(Options::From(args), io_delegate_));
  std::string preprocessed =
      "interface foo.bar.IFoo {\n"
      "  const int FOO = 44;\n"
      "}\n"
      "parcelable foo.bar.Bar {\n"
      "  const int BAR = 43;\n"
      "}\n"
      "parcelable foo.bar.Gen<T> {\n"
      "}\n"
      "enum foo.bar.Enum {\n"
      "  FOO = 3,\n"
      "  BAR = 6,\n"
      "}\n";
  std::map<std::string, std::string> expected = {{"preprocessed", preprocessed}};
  EXPECT_THAT(io_delegate_.OutputFiles(), testing::Eq(expected));

  // use preprocessed
  io_delegate_.SetFileContents("a/Foo.aidl",
                               "package a; parcelable Foo { const int y = foo.bar.Bar.BAR; }");
  io_delegate_.SetFileContents("preprocessed", preprocessed);
  CaptureStderr();
  auto options = Options::From("aidl --lang java -I . -o out a/Foo.aidl -ppreprocessed");
  EXPECT_TRUE(compile_aidl(options, io_delegate_));
  EXPECT_EQ("", GetCapturedStderr());
  string code;
  EXPECT_TRUE(io_delegate_.GetWrittenContents("out/a/Foo.java", &code));
  EXPECT_THAT(code, testing::HasSubstr("public static final int y = 43;"));
}

TEST_F(AidlTest, AllowMultipleUnstructuredNestedParcelablesInASingleDocument) {
  io_delegate_.SetFileContents("p/IFoo.aidl",
                               "package p;\n"
                               "import x.Outer;\n"
                               "interface IFoo {\n"
                               "  void foo(in Outer.Inner1 in1, in Outer.Inner2 in2);\n"
                               "}");
  io_delegate_.SetFileContents("imported/x/Outer.aidl",
                               "package x;\n"
                               "parcelable Outer.Inner1;\n"
                               "parcelable Outer.Inner2;\n");
  auto opt = Options::From("aidl -I . -Iimported --lang=java p/IFoo.aidl");
  CaptureStderr();
  EXPECT_TRUE(compile_aidl(opt, io_delegate_));
  EXPECT_EQ("", GetCapturedStderr());
}

TEST_F(AidlTest,
       StubsSourceIsGeneratedFromDuplicateDefinitionWithFrameworkAidl_FrameworkAidlLater) {
  // Main doc(Foo.aidl) is loaded
  // And then framework.aidl is loaded as preprocessed. (conflict)
  io_delegate_.SetFileContents("sdk/framework.aidl", "parcelable x.Foo.Inner;\n");
  io_delegate_.SetFileContents("x/Foo.aidl",
                               "package x;\n"
                               "parcelable Foo.Inner;\n");
  auto opt = Options::From("aidl -psdk/framework.aidl -I. x/Foo.aidl");
  CaptureStderr();
  EXPECT_TRUE(compile_aidl(opt, io_delegate_));
  EXPECT_EQ("", GetCapturedStderr());
}

TEST_F(AidlTest,
       StubsSourceIsGeneratedFromDuplicateDefinitionWithFrameworkAidl_FrameworkAidlFirst) {
  // Main doc(IBar.aidl) is loaded first.
  // Framework.aidl is loaded as preprocessed.
  // And then import(Foo.aidl) is loaded. (conflict)
  io_delegate_.SetFileContents("sdk/framework.aidl", "parcelable x.Foo.Inner;\n");
  io_delegate_.SetFileContents("x/IBar.aidl",
                               "package x;\n"
                               "import x.Foo;\n"
                               "interface IBar {\n"
                               "  void bar(in Foo.Inner inner);\n"
                               "}");
  io_delegate_.SetFileContents("x/Foo.aidl",
                               "package x;\n"
                               "parcelable Foo.Inner;\n");
  auto opt = Options::From("aidl -psdk/framework.aidl -I. x/IBar.aidl");
  CaptureStderr();
  EXPECT_TRUE(compile_aidl(opt, io_delegate_));
  EXPECT_EQ("", GetCapturedStderr());
}

TEST_F(AidlTest, PreprocessedFileCantDeclarePackage) {
  string simple_content = "package xxx; parcelable a.Foo;";
  io_delegate_.SetFileContents("path", simple_content);
  CaptureStderr();
  EXPECT_FALSE(Parser::Parse("path", io_delegate_, typenames_, /*is_preprocessed=*/true));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr("Preprocessed file can't declare package."));
}

TEST_F(AidlTest, RejectQualifiedTypeNameUnlessPreprocessed) {
  string simple_content = "parcelable a.Foo {}";
  io_delegate_.SetFileContents("path", simple_content);
  CaptureStderr();
  EXPECT_FALSE(Parser::Parse("path", io_delegate_, typenames_, /*is_preprocessed=*/false));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr("Type name can't be qualified"));
}

TEST_P(AidlTest, PreprocessedCanDeclareJavaStyleBuiltinTypes) {
  string contents = R"(
    interface android.os.IBinder;
    interface android.os.IInterface;
    parcelable android.os.ParcelFileDescriptor;
  )";
  io_delegate_.SetFileContents("path", contents);
  CaptureStderr();
  EXPECT_TRUE(Parser::Parse("path", io_delegate_, typenames_, /*is_preprocessed=*/true));
  EXPECT_THAT(GetCapturedStderr(), "");
}

TEST_P(AidlTest, SupportDeprecated) {
  struct TestCase {
    std::string output_file;
    std::string annotation;
  };

  auto CheckDeprecated = [&](const std::string& filename, const std::string& contents,
                             std::vector<std::pair<Options::Language, TestCase>> expectations) {
    io_delegate_.SetFileContents(filename, contents);

    auto options = Options::From("aidl -I . --lang=" + to_string(GetLanguage()) + " " + filename +
                                 " --out=out --header_out=out");
    EXPECT_TRUE(compile_aidl(options, io_delegate_));
    for (const auto& [lang, test_case] : expectations) {
      if (lang != GetLanguage()) continue;
      string output;
      EXPECT_TRUE(io_delegate_.GetWrittenContents(test_case.output_file, &output));
      EXPECT_THAT(output, HasSubstr(test_case.annotation));
    }
  };

  // Emit escaped string for notes
  CheckDeprecated(
      "IFoo.aidl",
      R"(interface IFoo {
           /**
            * @note asdf
            * @deprecated a really long deprecation message
            *
            *    which is really long
            * @param foo bar
            */
           List<String> foo();
        })",
      {
          {Options::Language::JAVA, {"out/IFoo.java", "@Deprecated"}},
          {Options::Language::CPP,
           {"out/IFoo.h",
            R"(__attribute__((deprecated("a really long deprecation message which is really long"))))"}},
          {Options::Language::NDK,
           {"out/aidl/IFoo.h",
            R"(__attribute__((deprecated("a really long deprecation message which is really long"))))"}},
          {Options::Language::RUST,
           {"out/IFoo.rs",
            R"(#[deprecated = "a really long deprecation message which is really long"])"}},
      });

  // In AIDL @deprecated can be in block comments as well as javadoc style
  CheckDeprecated(
      "IFoo.aidl",
      "interface IFoo {\n"
      "  /* @deprecated use bar() */\n"
      "  List<String> foo();\n"
      "}",
      {
          {Options::Language::JAVA, {"out/IFoo.java", "@Deprecated"}},
          {Options::Language::JAVA, {"out/IFoo.java", "/** @deprecated use bar() */"}},
          {Options::Language::CPP, {"out/IFoo.h", "__attribute__((deprecated(\"use bar()\")))"}},
          {Options::Language::NDK,
           {"out/aidl/IFoo.h", "__attribute__((deprecated(\"use bar()\")))"}},
          {Options::Language::RUST, {"out/IFoo.rs", "#[deprecated = \"use bar()\"]"}},
      });

  // but not in line comments
  auto parsed = Parse("IFoo.aidl", "// @deprecated\ninterface IFoo {}", typenames_, GetLanguage());
  EXPECT_FALSE(parsed->IsDeprecated());

  // parcelable
  CheckDeprecated("Foo.aidl",
                  "parcelable Foo {\n"
                  "  /** @deprecated use bar*/\n"
                  "  int foo = 0;\n"
                  "}",
                  {
                      {Options::Language::JAVA, {"out/Foo.java", "@Deprecated"}},
                      {Options::Language::CPP, {"out/Foo.h", "__attribute__((deprecated"}},
                      {Options::Language::NDK, {"out/aidl/Foo.h", "__attribute__((deprecated"}},
                      {Options::Language::RUST, {"out/Foo.rs", "#[deprecated"}},
                  });

  // interface constants
  CheckDeprecated("IFoo.aidl",
                  "interface IFoo {\n"
                  "  /** @deprecated use bar*/\n"
                  "  const int FOO = 0;\n"
                  "}",
                  {
                      {Options::Language::JAVA, {"out/IFoo.java", "@Deprecated"}},
                      {Options::Language::CPP, {"out/IFoo.h", "__attribute__((deprecated"}},
                      {Options::Language::NDK, {"out/aidl/IFoo.h", "__attribute__((deprecated"}},
                      {Options::Language::RUST, {"out/IFoo.rs", "#[deprecated"}},
                  });

  // union fields
  CheckDeprecated("Foo.aidl",
                  "union Foo {\n"
                  "  int bar = 0;\n"
                  "  /** @deprecated use bar*/\n"
                  "  int foo;\n"
                  "}",
                  {
                      {Options::Language::JAVA, {"out/Foo.java", "@Deprecated"}},
                      {Options::Language::CPP, {"out/Foo.h", "__attribute__((deprecated"}},
                      {Options::Language::NDK, {"out/aidl/Foo.h", "__attribute__((deprecated"}},
                      {Options::Language::RUST, {"out/Foo.rs", "#[deprecated"}},
                  });

  CheckDeprecated("Foo.aidl",
                  "/** @deprecated use Bar */\n"
                  "parcelable Foo {}",
                  {
                      {Options::Language::JAVA, {"out/Foo.java", "@Deprecated"}},
                      {Options::Language::CPP, {"out/Foo.h", "__attribute__((deprecated"}},
                      {Options::Language::NDK, {"out/aidl/Foo.h", "__attribute__((deprecated"}},
                      {Options::Language::RUST, {"out/Foo.rs", "#[deprecated"}},
                  });

  CheckDeprecated("Foo.aidl",
                  "/** @deprecated use Bar */\n"
                  "union Foo { int foo = 0; }",
                  {
                      {Options::Language::JAVA, {"out/Foo.java", "@Deprecated"}},
                      {Options::Language::CPP, {"out/Foo.h", "__attribute__((deprecated"}},
                      {Options::Language::NDK, {"out/aidl/Foo.h", "__attribute__((deprecated"}},
                      {Options::Language::RUST, {"out/Foo.rs", "#[deprecated"}},
                  });

  CheckDeprecated("IFoo.aidl",
                  "/** @deprecated use IBar */\n"
                  "interface IFoo {}",
                  {
                      {Options::Language::JAVA, {"out/IFoo.java", "@Deprecated"}},
                      {Options::Language::CPP, {"out/IFoo.h", "__attribute__((deprecated"}},
                      {Options::Language::NDK, {"out/aidl/IFoo.h", "__attribute__((deprecated"}},
                      {Options::Language::RUST, {"out/IFoo.rs", "#[deprecated"}},
                  });

  CheckDeprecated("Foo.aidl",
                  "/** @deprecated use IBar */\n"
                  "enum Foo { FOO }",
                  {
                      {Options::Language::JAVA, {"out/Foo.java", "@Deprecated"}},
                      {Options::Language::CPP, {"out/Foo.h", "__attribute__((deprecated"}},
                      {Options::Language::NDK, {"out/aidl/Foo.h", "__attribute__((deprecated"}},
                      {Options::Language::RUST, {"out/Foo.rs", "#[deprecated"}},
                  });

  CheckDeprecated("Foo.aidl",
                  "enum Foo {\n"
                  " /** @deprecated */\n"
                  " FOO,\n"
                  " BAR,\n"
                  "}",
                  {
                      {Options::Language::JAVA, {"out/Foo.java", "@Deprecated"}},
                      {Options::Language::CPP, {"out/Foo.h", "__attribute__((deprecated"}},
                      {Options::Language::NDK, {"out/aidl/Foo.h", "__attribute__((deprecated"}},
                      {Options::Language::RUST, {"out/Foo.rs", "#[deprecated"}},
                  });
}

TEST_P(AidlTest, RequireOuterClass) {
  const string expected_stderr =
      "ERROR: p/IFoo.aidl: Couldn't find import for class Inner. Searched here:\n - ./\nERROR: "
      "p/IFoo.aidl:1.54-60: Failed to resolve 'Inner'\n";
  io_delegate_.SetFileContents("p/Outer.aidl",
                               "package p; parcelable Outer.Inner;");
  import_paths_.emplace("");
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("p/IFoo.aidl",
                           "package p; import p.Outer; interface IFoo { void f(in Inner c); }",
                           typenames_, GetLanguage()));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_P(AidlTest, ParseCompoundParcelableFromPreprocess) {
  io_delegate_.SetFileContents("preprocessed",
                               "parcelable p.Outer.Inner cpp_header \"inner.h\" ndk_header "
                               "\"ndk/inner.h\" rust_type \"package::Inner\";");
  preprocessed_files_.push_back("preprocessed");
  auto parse_result = Parse("p/IFoo.aidl", "package p; interface IFoo { void f(in Inner c); }",
                            typenames_, GetLanguage());
  // Require legacy behavior - inner class name can be used without outer class name.
  EXPECT_NE(nullptr, parse_result);
}

TEST_F(AidlTest, ApiMappingAcceptsUnstructuredParcelables) {
  io_delegate_.SetFileContents("p/Foo.aidl", "package p; parcelable Foo;");

  Options options1 = Options::From("aidl -I . --apimapping mapping.txt p/Foo.aidl");
  CaptureStderr();
  EXPECT_EQ(0, aidl_entry(options1, io_delegate_));
  EXPECT_EQ("", GetCapturedStderr());
}

TEST_F(AidlTest, FailOnParcelable) {
  const string expected_foo_stderr =
      "ERROR: p/IFoo.aidl:1.22-27: Refusing to generate code with unstructured parcelables. "
      "Declared parcelables should be in their own file and/or cannot be used with --structured "
      "interfaces.\n";
  io_delegate_.SetFileContents("p/IFoo.aidl", "package p; parcelable IFoo;");

  // By default, we shouldn't fail on parcelable.
  Options options1 = Options::From("aidl -I . p/IFoo.aidl");
  CaptureStderr();
  EXPECT_TRUE(compile_aidl(options1, io_delegate_));
  EXPECT_EQ("", GetCapturedStderr());

  // -b considers this an error
  Options options2 = Options::From("aidl -I . -b p/IFoo.aidl");
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options2, io_delegate_));
  EXPECT_EQ(expected_foo_stderr, GetCapturedStderr());

  const string expected_bar_stderr =
      "ERROR: p/IBar.aidl:1.22-26: Refusing to generate code with unstructured parcelables. "
      "Declared parcelables should be in their own file and/or cannot be used with --structured "
      "interfaces.\n";
  io_delegate_.SetFileContents("p/IBar.aidl", "package p; parcelable Foo; interface IBar{}");

  // With '-b' option, a parcelable and an interface should fail.
  Options options3 = Options::From("aidl -I . p/IBar.aidl");
  CaptureStderr();
  EXPECT_TRUE(compile_aidl(options3, io_delegate_));
  EXPECT_EQ("", GetCapturedStderr());
  Options options4 = Options::From("aidl -I . -b p/IBar.aidl");
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options4, io_delegate_));
  EXPECT_EQ(expected_bar_stderr, GetCapturedStderr());
}

TEST_P(AidlTest, ImportingJavaStyleBuiltinTypesIsAllowed) {
  string contents = R"(
    import android.os.IBinder;
    import android.os.IInterface;
    interface IFoo {
      void foo(in IBinder b);
    }
  )";
  EXPECT_NE(nullptr, Parse("IFoo.aidl", contents, typenames_, GetLanguage()));
}

TEST_P(AidlTest, StructuredFailOnUnstructuredParcelable) {
  const string expected_stderr =
      "o.WhoKnowsWhat is not structured, but this is a structured interface";
  io_delegate_.SetFileContents("o/WhoKnowsWhat.aidl",
                               "package o; parcelable WhoKnowsWhat cpp_header \"who_knows.h\" "
                               "ndk_header \"ndk/who_knows.h\" rust_type \"WhoKnows\";");
  import_paths_.emplace("");
  AidlError error;
  CaptureStderr();
  EXPECT_EQ(
      nullptr,
      Parse("p/IFoo.aidl",
            "package p; import o.WhoKnowsWhat; interface IFoo { void f(in WhoKnowsWhat thisIs); }",
            typenames_, GetLanguage(), &error, {"--structured"}));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr(expected_stderr));
  EXPECT_EQ(AidlError::NOT_STRUCTURED, error);
}

TEST_P(AidlTest, FailOnDuplicateConstantNames) {
  AidlError error;
  const string expected_stderr =
      "ERROR: p/IFoo.aidl:4.34-45: Found duplicate constant name 'DUPLICATED'\n";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("p/IFoo.aidl",
                           R"(package p;
                      interface IFoo {
                        const String DUPLICATED = "d";
                        const int DUPLICATED = 1;
                      }
                   )",
                           typenames_, GetLanguage(), &error));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
  EXPECT_EQ(AidlError::BAD_TYPE, error);
}

TEST_P(AidlTest, FailOnDoubleUnderscore) {
  AidlError error;
  const string expected_stderr = "ERROR: p/IFoo.aidl:3.52-57: Could not parse integer: 1__0\n";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("p/IFoo.aidl",
                           R"(package p;
                      interface IFoo {
                        const int UNDERSCORE_USER = 1__0;
                      }
                   )",
                           typenames_, GetLanguage(), &error));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
  EXPECT_EQ(AidlError::PARSE_ERROR, error);
}

TEST_P(AidlTest, FailOnUnderscoreBeforeModifier) {
  AidlError error;
  const string expected_stderr = "ERROR: p/IFoo.aidl:3.52-59: Could not parse hexvalue: 0xFF_L\n";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("p/IFoo.aidl",
                           R"(package p;
                      interface IFoo {
                        const int UNDERSCORE_USER = 0xFF_L;
                      }
                   )",
                           typenames_, GetLanguage(), &error));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
  EXPECT_EQ(AidlError::PARSE_ERROR, error);
}

TEST_P(AidlTest, FailOnEndingUnderscore) {
  AidlError error;
  const string expected_stderr = "ERROR: p/IFoo.aidl:3.52-56: Could not parse integer: 10_\n";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("p/IFoo.aidl",
                           R"(package p;
                      interface IFoo {
                        const int UNDERSCORE_USER = 10_;
                      }
                   )",
                           typenames_, GetLanguage(), &error));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
  EXPECT_EQ(AidlError::PARSE_ERROR, error);
}

TEST_P(AidlTest, FailOnLeadingUnderscore) {
  AidlError error;
  const string expected_stderr =
      "ERROR: p/IFoo.aidl:3.52-56: Can't find _10 in IFoo\nERROR: p/IFoo.aidl:3.52-56: Unknown "
      "reference '_10'\n";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("p/IFoo.aidl",
                           R"(package p;
                      interface IFoo {
                        const int UNDERSCORE_USER = _10;
                      }
                   )",
                           typenames_, GetLanguage(), &error));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
  EXPECT_EQ(AidlError::BAD_TYPE, error);
}

TEST_P(AidlTest, InvalidConstString) {
  AidlError error;
  const string expected_stderr =
      "ERROR: p/IFoo.aidl:3.47-60: Found invalid character '\\' at index 4 in string constant "
      "'\"test\\\"test\"'\n";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("p/IFoo.aidl",
                           R"(package p;
                      interface IFoo {
                        const String someVar = "test\"test";
                      }
                   )",
                           typenames_, GetLanguage(), &error));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
  EXPECT_EQ(AidlError::BAD_TYPE, error);
}

TEST_F(AidlTest, InvalidCharLiteral) {
  auto filename = "Foo.aidl";
  char code[] = "parcelable Foo { char a = '\0'; char b = '\t'; }";
  io_delegate_.SetFileContents(filename,
                               string{code, sizeof(code) - 1});  // -1 to drop nil at the end
  CaptureStderr();
  EXPECT_TRUE(Parser::Parse(filename, io_delegate_, typenames_, /*is_preprocessed=*/false));
  auto err = GetCapturedStderr();
  EXPECT_THAT(err, HasSubstr("Invalid character literal \\0"));
  EXPECT_THAT(err, HasSubstr("Invalid character literal \\t"));
}

TEST_P(AidlTest, RejectUnstructuredParcelablesInNDKandRust) {
  io_delegate_.SetFileContents("o/Foo.aidl", "package o; parcelable Foo cpp_header \"cpp/Foo.h\";");
  const auto options =
      Options::From("aidl --lang=" + to_string(GetLanguage()) + " -o out -h out -I. o/Foo.aidl");
  const bool reject_unstructured_parcelables =
      GetLanguage() == Options::Language::NDK || GetLanguage() == Options::Language::RUST;
  const string expected_err = reject_unstructured_parcelables
                                  ? "Refusing to generate code with unstructured parcelables"
                                  : "";
  CaptureStderr();
  EXPECT_EQ(compile_aidl(options, io_delegate_), !reject_unstructured_parcelables);
  EXPECT_THAT(GetCapturedStderr(), HasSubstr(expected_err));
}

TEST_F(AidlTest, CosntantValueType) {
  unique_ptr<AidlConstantValue> num{AidlConstantValue::Integral(AIDL_LOCATION_HERE, "1")};
  EXPECT_EQ(num->GetType(), AidlConstantValue::Type::INT8);
}

TEST_P(AidlTest, FailOnTooBigConstant) {
  AidlError error;
  const string expected_stderr =
      "ERROR: p/IFoo.aidl:3.48-52: Invalid type specifier for an int32 literal: byte (256).\n";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("p/IFoo.aidl",
                           R"(package p;
                      interface IFoo {
                        const byte type2small = 256;
                      }
                   )",
                           typenames_, GetLanguage(), &error));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
  EXPECT_EQ(AidlError::BAD_TYPE, error);
}

TEST_F(AidlTest, BoolConstantsEvaluatesToIntegers) {
  io_delegate_.SetFileContents("a/Foo.aidl", "package a; parcelable Foo { const int y = true; }");
  CaptureStderr();
  auto options = Options::From("aidl -I . --lang java -o out a/Foo.aidl");
  EXPECT_TRUE(compile_aidl(options, io_delegate_));
  EXPECT_EQ("", GetCapturedStderr());
  string code;
  EXPECT_TRUE(io_delegate_.GetWrittenContents("out/a/Foo.java", &code));
  EXPECT_THAT(code, testing::HasSubstr("public static final int y = 1;"));
}

TEST_F(AidlTest, AidlConstantValue_EvaluatedValue) {
  using Ptr = unique_ptr<AidlConstantValue>;
  const AidlLocation& loc = AIDL_LOCATION_HERE;

  EXPECT_EQ('c', Ptr(AidlConstantValue::Character(loc, "'c'"))->EvaluatedValue<char16_t>());
  EXPECT_EQ("abc", Ptr(AidlConstantValue::String(loc, "\"abc\""))->EvaluatedValue<string>());
  EXPECT_FLOAT_EQ(1.0f, Ptr(AidlConstantValue::Floating(loc, "1.0f"))->EvaluatedValue<float>());
  EXPECT_EQ(true, Ptr(AidlConstantValue::Boolean(loc, true))->EvaluatedValue<bool>());

  AidlBinaryConstExpression one_plus_one(loc, Ptr(AidlConstantValue::Integral(loc, "1")), "+",
                                         Ptr(AidlConstantValue::Integral(loc, "1")));
  EXPECT_EQ(2, one_plus_one.EvaluatedValue<int32_t>());

  auto values = unique_ptr<vector<Ptr>>{new vector<Ptr>};
  values->emplace_back(AidlConstantValue::String(loc, "\"hello\""));
  values->emplace_back(AidlConstantValue::String(loc, "\"world\""));
  vector<string> expected{"hello", "world"};
  EXPECT_EQ(
      expected,
      Ptr(AidlConstantValue::Array(loc, std::move(values)))->EvaluatedValue<vector<string>>());

  // evaluate array of floating
  vector<float> testFloats({32.f, 14.f, 50.f});
  auto testFloatValues = unique_ptr<vector<Ptr>>{new vector<Ptr>()};
  testFloatValues->emplace_back(AidlConstantValue::Floating(loc, "32.f"));
  testFloatValues->emplace_back(AidlConstantValue::Floating(loc, "14.f"));
  testFloatValues->emplace_back(AidlConstantValue::Floating(loc, "50.f"));
  EXPECT_EQ(testFloats, Ptr(AidlConstantValue::Array(loc, std::move(testFloatValues)))
                            ->EvaluatedValue<vector<float>>());
}

// TODO: b/313951203 add floating/string operands test
TEST_F(AidlTest, AidlBinaryConstExpression_CompatibleTypes) {
  const AidlLocation& loc = AIDL_LOCATION_HERE;
  static const std::unordered_map<AidlConstantValue::Type,
                                  const std::unordered_set<AidlConstantValue::Type>>
      compatibleTypesMap({{AidlConstantValue::Type::BOOLEAN,
                           {AidlConstantValue::Type::BOOLEAN, AidlConstantValue::Type::INT8,
                            AidlConstantValue::Type::INT32, AidlConstantValue::Type::INT64}},
                          {AidlConstantValue::Type::INT8,
                           {AidlConstantValue::Type::BOOLEAN, AidlConstantValue::Type::INT8,
                            AidlConstantValue::Type::INT32, AidlConstantValue::Type::INT64}},
                          {AidlConstantValue::Type::INT32,
                           {AidlConstantValue::Type::BOOLEAN, AidlConstantValue::Type::INT8,
                            AidlConstantValue::Type::INT32, AidlConstantValue::Type::INT64}},
                          {AidlConstantValue::Type::INT64,
                           {AidlConstantValue::Type::BOOLEAN, AidlConstantValue::Type::INT8,
                            AidlConstantValue::Type::INT32, AidlConstantValue::Type::INT64}},
                          {AidlConstantValue::Type::ARRAY, {AidlConstantValue::Type::ARRAY}},
                          {AidlConstantValue::Type::STRING, {AidlConstantValue::Type::STRING}}});

  // go over all possible combination
  for (int l = 0; l <= (int)AidlConstantValue::Type::ERROR; ++l) {
    for (int r = 0; r <= (int)AidlConstantValue::Type::ERROR; ++r) {
      const AidlConstantValue::Type lType = static_cast<AidlConstantValue::Type>(l),
                                    rType = static_cast<AidlConstantValue::Type>(r);
      const auto typeItor = compatibleTypesMap.find(lType);
      const bool isCompatibleOperandType = typeItor != compatibleTypesMap.end() &&
                                           typeItor->second.find(rType) != typeItor->second.end();
      const bool isCompatibleArrayType =
          isCompatibleOperandType || (lType == AidlConstantValue::Type::FLOATING &&
                                      rType == AidlConstantValue::Type::FLOATING);
      EXPECT_EQ(isCompatibleOperandType,
                AidlBinaryConstExpression::AreCompatibleOperandTypes(lType, rType))
          << AidlConstantValue::ToString(lType) << ", " << AidlConstantValue::ToString(rType);
      EXPECT_EQ(isCompatibleArrayType,
                AidlBinaryConstExpression::AreCompatibleArrayTypes(lType, rType))
          << AidlConstantValue::ToString(lType) << ", " << AidlConstantValue::ToString(rType);
    }
  }
}

TEST_F(AidlTest, AidlConstantCharacterDefault) {
  auto char_type = typenames_.MakeResolvedType(AIDL_LOCATION_HERE, "char", false);
  auto default_value = unique_ptr<AidlConstantValue>(AidlConstantValue::Default(*char_type));
  EXPECT_EQ("'\\0'", default_value->ValueString(*char_type, cpp::ConstantValueDecorator));
  EXPECT_EQ("'\\0'", default_value->ValueString(*char_type, ndk::ConstantValueDecorator));
  EXPECT_EQ("'\\0'", default_value->ValueString(*char_type, java::ConstantValueDecorator));
}

TEST_P(AidlTest, FailOnManyDefinedTypes) {
  AidlError error;
  const string expected_stderr =
      "ERROR: p/IFoo.aidl:3.33-38: You must declare only one type per file.\n";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("p/IFoo.aidl",
                           R"(package p;
                      interface IFoo {}
                      parcelable IBar {}
                      parcelable StructuredParcelable {}
                      interface IBaz {}
                  )",
                           typenames_, GetLanguage(), &error));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
  // Parse success is important for clear error handling even if the cases aren't
  // actually supported in code generation.
  EXPECT_EQ(AidlError::BAD_TYPE, error);
}

TEST_P(AidlTest, FailOnNoDefinedTypes) {
  AidlError error;
  const string expected_stderr = "ERROR: p/IFoo.aidl:1.11-11: syntax error, unexpected $end\n";
  const string expected_stderr_newbison =
      "ERROR: p/IFoo.aidl:1.11-11: syntax error, unexpected end of file\n";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("p/IFoo.aidl", R"(package p;)", typenames_, GetLanguage(), &error));
  EXPECT_THAT(GetCapturedStderr(),
              testing::AnyOf(testing::Eq(expected_stderr), testing::Eq(expected_stderr_newbison)));
  EXPECT_EQ(AidlError::PARSE_ERROR, error);
}

TEST_P(AidlTest, FailOnEmptyListWithComma) {
  AidlError error;
  const string expected_stderr =
      "ERROR: p/Foo.aidl:1.45-47: syntax error, unexpected ',', expecting '}'\n";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("p/Foo.aidl", R"(package p; parcelable Foo { uint64_t[] a = { , }; })",
                           typenames_, GetLanguage(), &error));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
  EXPECT_EQ(AidlError::PARSE_ERROR, error);
}

TEST_P(AidlTest, FailOnMalformedConstHexValue) {
  AidlError error;
  const string expected_stderr =
      "ERROR: p/IFoo.aidl:3.50-71: Could not parse hexvalue: 0xffffffffffffffffff\n";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("p/IFoo.aidl",
                           R"(package p;
                      interface IFoo {
                        const int BAD_HEX_VALUE = 0xffffffffffffffffff;
                      }
                   )",
                           typenames_, GetLanguage(), &error));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
  EXPECT_EQ(AidlError::PARSE_ERROR, error);
}

TEST_P(AidlTest, FailOnMalformedQualifiedNameAsIdentifier) {
  AidlError error;
  const string expected_stderr =
      "ERROR: p/IFoo.aidl:1.25-26: syntax error, unexpected ';', expecting identifier";
  CaptureStderr();
  // Notice the trailing dot(.) in the name, which isn't a correct name
  EXPECT_EQ(nullptr, Parse("p/IFoo.aidl", R"(package p; parcelable A.; )", typenames_,
                           GetLanguage(), &error));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr(expected_stderr));
  EXPECT_EQ(AidlError::PARSE_ERROR, error);
}

TEST_P(AidlTest, FailOnAssigningByteSizeIntToByteWithHelp) {
  EvaluateInvalidAssignment(
      R"(package a; interface IFoo { const byte INT_VALUE = 0xFF; })",
      "Invalid type specifier for an int32 literal: byte (0xFF). Did you mean: 0xFFu8?", typenames_,
      GetLanguage());
}

TEST_P(AidlTest, FailOnAssigningDoubleInFloatConst) {
  EvaluateInvalidAssignment(R"(package a; interface IFoo { const float DOUBLE_VALUE = 1.1; })",
                            INVALID_FLOAT_VALUE, typenames_, GetLanguage());
}

TEST_P(AidlTest, FailOnAssigningFloatInDoubleConst) {
  EvaluateValidAssignment(R"(package a; interface IFoo { const double FLOAT_VALUE = 1.1f; })", "",
                          typenames_, GetLanguage());
}

TEST_P(AidlTest, FailOnAssigningIntInFloatConst) {
  EvaluateInvalidAssignment(R"(package a; interface IFoo { const float INT_VALUE = 1; })",
                            INVALID_INT8_VALUE, typenames_, GetLanguage());
}

TEST_P(AidlTest, FailOnAssigningFloatInIntConst) {
  EvaluateInvalidAssignment(R"(package a; interface IFoo { const int FLOAT_VALUE = 1.1f; })",
                            INVALID_FLOAT_VALUE, typenames_, GetLanguage());
}

TEST_P(AidlTest, FailOnAssigningIntInDoubleConst) {
  EvaluateInvalidAssignment(R"(package a; interface IFoo { const double INT_VALUE = 1; })",
                            INVALID_INT8_VALUE, typenames_, GetLanguage());
}

TEST_P(AidlTest, FailOnAssigningDoubleInIntConst) {
  EvaluateInvalidAssignment(R"(package a; interface IFoo { const int DOUBLE_VALUE = 1.1; })",
                            INVALID_FLOAT_VALUE, typenames_, GetLanguage());
}

TEST_P(AidlTest, FailOnAssigningFloatPlusIntConst) {
  EvaluateInvalidAssignment(R"(package a; interface IFoo { const float FLOAT_VALUE = 1.1f + 1; })",
                            INVALID_OPERATION, typenames_, GetLanguage());
}

TEST_P(AidlTest, FailOnAssigningIntPlusFloatConst) {
  EvaluateInvalidAssignment(R"(package a; interface IFoo { const float FLOAT_VALUE = 1 + 1.1f; })",
                            INVALID_OPERATION, typenames_, GetLanguage());
}

TEST_P(AidlTest, FailOnAssigningDoublePlusIntConst) {
  EvaluateInvalidAssignment(R"(package a; interface IFoo { const double DOUBLE_VALUE = 1.1 + 1; })",
                            INVALID_OPERATION, typenames_, GetLanguage());
}

TEST_P(AidlTest, FailOnAssigningIntPlusDoubleConst) {
  EvaluateInvalidAssignment(R"(package a; interface IFoo { const double DOUBLE_VALUE = 1 + 1.1; })",
                            INVALID_OPERATION, typenames_, GetLanguage());
}

TEST_P(AidlTest, FailOnAssigningTooLargeFloatConst) {
  EvaluateInvalidAssignment(R"(package a; interface IFoo { const float DOUBLE_VALUE = 1e50f; })",
                            INVALID_FLOAT_VALUE, typenames_, GetLanguage());
}

TEST_P(AidlTest, FailOnAssigningTooLargeDoubleConst) {
  EvaluateInvalidAssignment(R"(package a; interface IFoo { const double DOUBLE_VALUE = 1e310; })",
                            INVALID_FLOAT_VALUE, typenames_, GetLanguage());
}

TEST_P(AidlTest, PassOnAssigningLargeFloatConst) {
  EvaluateValidAssignment(R"(package a; interface IFoo { const float DOUBLE_VALUE = 1e20f; })", "",
                          typenames_, GetLanguage());
}

TEST_P(AidlTest, PassOnAssigningLargeDoubleConst) {
  EvaluateValidAssignment(R"(package a; interface IFoo { const double DOUBLE_VALUE = 1e150; })", "",
                          typenames_, GetLanguage());
}

TEST_P(AidlTest, FailOnMalformedQualifiedNameAsPackage) {
  AidlError error;
  const string expected_stderr =
      "ERROR: p/IFoo.aidl:1.11-12: syntax error, unexpected ';', expecting identifier";
  CaptureStderr();
  // Notice the trailing dot(.) in the package name
  EXPECT_EQ(nullptr, Parse("p/IFoo.aidl", R"(package p.; parcelable A; )", typenames_,
                           GetLanguage(), &error));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr(expected_stderr));
  EXPECT_EQ(AidlError::PARSE_ERROR, error);
}

TEST_P(AidlTest, ParsePositiveConstHexValue) {
  AidlError error;
  auto parse_result = Parse("p/IFoo.aidl",
                            R"(package p;
              interface IFoo {
                const int POSITIVE_HEX_VALUE = 0xf5;
              }
           )",
                            typenames_, GetLanguage(), &error);
  EXPECT_NE(nullptr, parse_result);
  const AidlInterface* interface = parse_result->AsInterface();
  ASSERT_NE(nullptr, interface);
  const auto& cpp_constants = interface->GetConstantDeclarations();
  EXPECT_EQ((size_t)1, cpp_constants.size());
  EXPECT_EQ("POSITIVE_HEX_VALUE", cpp_constants[0]->GetName());
  EXPECT_TRUE(cpp_constants[0]->CheckValid(typenames_));
  EXPECT_EQ("245", cpp_constants[0]->ValueString(cpp::ConstantValueDecorator));
}

TEST_P(AidlTest, ParseNegativeConstHexValue) {
  AidlError error;
  auto parse_result = Parse("p/IFoo.aidl",
                            R"(package p;
              interface IFoo {
                const int NEGATIVE_HEX_VALUE = 0xffffffff;
              }
           )",
                            typenames_, GetLanguage(), &error);
  ASSERT_NE(nullptr, parse_result);
  const AidlInterface* interface = parse_result->AsInterface();
  ASSERT_NE(nullptr, interface);
  const auto& cpp_constants = interface->GetConstantDeclarations();
  EXPECT_EQ((size_t)1, cpp_constants.size());
  EXPECT_EQ("NEGATIVE_HEX_VALUE", cpp_constants[0]->GetName());
  EXPECT_EQ(true, cpp_constants[0]->CheckValid(typenames_));
  EXPECT_EQ("-1", cpp_constants[0]->ValueString(cpp::ConstantValueDecorator));
}

TEST_F(AidlTest, ByteAndByteArrayDifferInCpp) {
  auto type = Parse("p/Foo.aidl",
                    R"(
                      package p;
                      parcelable Foo {
                        byte a = -1;
                        byte[] b = {-1, 1};
                        @nullable byte[] c = {-1, 1};
                      }
                    )",
                    typenames_, Options::Language::CPP);
  ASSERT_NE(nullptr, type);
  auto& fields = type->GetFields();
  ASSERT_EQ(3ul, fields.size());
  EXPECT_EQ("-1", fields[0]->ValueString(cpp::ConstantValueDecorator));
  EXPECT_EQ("{uint8_t(-1), 1}", fields[1]->ValueString(cpp::ConstantValueDecorator));
  EXPECT_EQ("{{uint8_t(-1), 1}}", fields[2]->ValueString(cpp::ConstantValueDecorator));
}

TEST_F(AidlTest, ByteAndByteArrayDifferInNdk) {
  auto type = Parse("p/Foo.aidl",
                    R"(
                      package p;
                      parcelable Foo {
                        byte a = -1;
                        byte[] b = {-1, 1};
                        @nullable byte[] c = {-1, 1};
                      }
                    )",
                    typenames_, Options::Language::NDK);
  ASSERT_NE(nullptr, type);
  auto& fields = type->GetFields();
  ASSERT_EQ(3ul, fields.size());
  EXPECT_EQ("-1", fields[0]->ValueString(ndk::ConstantValueDecorator));
  EXPECT_EQ("{uint8_t(-1), 1}", fields[1]->ValueString(ndk::ConstantValueDecorator));
  EXPECT_EQ("{{uint8_t(-1), 1}}", fields[2]->ValueString(ndk::ConstantValueDecorator));
}

TEST_P(AidlTest, UnderstandsNestedUnstructuredParcelables) {
  io_delegate_.SetFileContents("p/Outer.aidl",
                               "package p; parcelable Outer.Inner cpp_header \"baz/header\" "
                               "ndk_header \"ndk/baz/header\" rust_type \"baz::Inner\";");
  import_paths_.emplace("");
  const string input_path = "p/IFoo.aidl";
  const string input = "package p; import p.Outer; interface IFoo"
                       " { Outer.Inner get(); }";

  auto parse_result = Parse(input_path, input, typenames_, GetLanguage());
  EXPECT_NE(nullptr, parse_result);

  EXPECT_TRUE(typenames_.ResolveTypename("p.Outer.Inner").is_resolved);
  // C++ uses "::" instead of "." to refer to a inner class.
  AidlTypeSpecifier nested_type(AIDL_LOCATION_HERE, "p.Outer.Inner", /*array=*/std::nullopt,
                                nullptr, {});
  EXPECT_EQ("::p::Outer::Inner", cpp::CppNameOf(nested_type, typenames_));
}

TEST_P(AidlTest, UnderstandsNestedUnstructuredParcelablesWithoutImports) {
  io_delegate_.SetFileContents("p/Outer.aidl",
                               "package p; parcelable Outer.Inner cpp_header \"baz/header\" "
                               "ndk_header \"ndk/baz/header\" rust_type \"baz::Inner\";");
  import_paths_.emplace("");
  const string input_path = "p/IFoo.aidl";
  const string input = "package p; interface IFoo { p.Outer.Inner get(); }";

  auto parse_result = Parse(input_path, input, typenames_, GetLanguage());
  EXPECT_NE(nullptr, parse_result);

  EXPECT_TRUE(typenames_.ResolveTypename("p.Outer.Inner").is_resolved);
  // C++ uses "::" instead of "." to refer to a inner class.
  AidlTypeSpecifier nested_type(AIDL_LOCATION_HERE, "p.Outer.Inner", /*array=*/std::nullopt,
                                nullptr, {});
  EXPECT_EQ("::p::Outer::Inner", cpp::CppNameOf(nested_type, typenames_));
}

TEST_F(AidlTest, UnderstandsNestedTypes) {
  io_delegate_.SetFileContents("p/IOuter.aidl",
                               "package p;\n"
                               "interface IOuter {\n"
                               "  parcelable Inner {}\n"
                               "}");
  import_paths_.emplace("");
  const string input_path = "p/IFoo.aidl";
  const string input =
      "package p;\n"
      "import p.IOuter;\n"
      "interface IFoo {\n"
      "  IOuter.Inner get();\n"
      "}";
  CaptureStderr();
  EXPECT_NE(nullptr, Parse(input_path, input, typenames_, Options::Language::CPP));
  EXPECT_EQ(GetCapturedStderr(), "");

  EXPECT_TRUE(typenames_.ResolveTypename("p.IOuter.Inner").is_resolved);
  // C++ uses "::" instead of "." to refer to a inner class.
  AidlTypeSpecifier nested_type(AIDL_LOCATION_HERE, "p.IOuter.Inner", /*array=*/std::nullopt,
                                nullptr, {});
  EXPECT_EQ("::p::IOuter::Inner", cpp::CppNameOf(nested_type, typenames_));
}

TEST_F(AidlTest, DefinedTypeKnowsItsParentScope) {
  const string input_path = "p/IFoo.aidl";
  const string input =
      "package p;\n"
      "interface IFoo {\n"
      "  parcelable Inner {\n"
      "    enum Enum { A }\n"
      "  }\n"
      "}";
  CaptureStderr();
  EXPECT_NE(nullptr, Parse(input_path, input, typenames_, Options::Language::CPP));
  EXPECT_EQ(GetCapturedStderr(), "");

  auto enum_type = typenames_.ResolveTypename("p.IFoo.Inner.Enum");
  auto inner_type = typenames_.ResolveTypename("p.IFoo.Inner");
  auto ifoo_type = typenames_.ResolveTypename("p.IFoo");
  EXPECT_TRUE(enum_type.is_resolved);
  EXPECT_TRUE(inner_type.is_resolved);
  EXPECT_TRUE(ifoo_type.is_resolved);
  // GetParentType()
  EXPECT_EQ(inner_type.defined_type, enum_type.defined_type->GetParentType());
  EXPECT_EQ(ifoo_type.defined_type, inner_type.defined_type->GetParentType());
  EXPECT_EQ(nullptr, ifoo_type.defined_type->GetParentType());
  // GetRootType()
  EXPECT_EQ(ifoo_type.defined_type, enum_type.defined_type->GetRootType());
  EXPECT_EQ(ifoo_type.defined_type, inner_type.defined_type->GetRootType());
  EXPECT_EQ(ifoo_type.defined_type, ifoo_type.defined_type->GetRootType());
  // GetDocument()
  auto main_document = &typenames_.MainDocument();
  EXPECT_EQ(main_document, &enum_type.defined_type->GetDocument());
  EXPECT_EQ(main_document, &inner_type.defined_type->GetDocument());
  EXPECT_EQ(main_document, &ifoo_type.defined_type->GetDocument());
}

TEST_F(AidlTest, UnderstandsNestedTypesViaFullyQualifiedName) {
  io_delegate_.SetFileContents("p/IOuter.aidl",
                               "package p;\n"
                               "interface IOuter {\n"
                               "  parcelable Inner {}\n"
                               "}");
  import_paths_.emplace("");
  const string input_path = "p/IFoo.aidl";
  const string input =
      "package p;\n"
      "interface IFoo {\n"
      "  p.IOuter.Inner get();\n"
      "}";
  CaptureStderr();
  EXPECT_NE(nullptr, Parse(input_path, input, typenames_, Options::Language::CPP));
  EXPECT_EQ(GetCapturedStderr(), "");

  EXPECT_TRUE(typenames_.ResolveTypename("p.IOuter.Inner").is_resolved);
}

TEST_F(AidlTest, IncludeParentsRawHeaderForNestedInterface) {
  CaptureStderr();
  EXPECT_NE(nullptr, Parse("p/Outer.aidl",
                           "package p;\n"
                           "parcelable Outer {\n"
                           "  interface IInner {}\n"
                           "}",
                           typenames_, Options::Language::CPP));

  EXPECT_EQ(GetCapturedStderr(), "");
  auto resolved = typenames_.ResolveTypename("p.Outer.IInner");
  ASSERT_TRUE(resolved.defined_type);
  EXPECT_EQ(cpp::HeaderFile(*resolved.defined_type, cpp::ClassNames::CLIENT), "p/Outer.h");
}

TEST_F(AidlTest, UnderstandsNestedTypesViaFullyQualifiedImport) {
  io_delegate_.SetFileContents("p/IOuter.aidl",
                               "package p;\n"
                               "interface IOuter {\n"
                               "  parcelable Inner {}\n"
                               "}");
  import_paths_.emplace("");
  const string input_path = "p/IFoo.aidl";
  const string input =
      "package p;\n"
      "import p.IOuter.Inner;"
      "interface IFoo {\n"
      "  Inner get();\n"
      "}";
  CaptureStderr();
  EXPECT_NE(nullptr, Parse(input_path, input, typenames_, Options::Language::CPP));
  EXPECT_EQ(GetCapturedStderr(), "");

  EXPECT_TRUE(typenames_.ResolveTypename("p.IOuter.Inner").is_resolved);
}

TEST_F(AidlTest, UnderstandsNestedTypesInTheSameScope) {
  const string input_path = "p/IFoo.aidl";
  const string input =
      "package p;\n"
      "interface IFoo {\n"
      "  parcelable Result {}\n"
      "  Result get();\n"
      "}";
  CaptureStderr();
  EXPECT_NE(nullptr, Parse(input_path, input, typenames_, Options::Language::CPP));
  EXPECT_EQ(GetCapturedStderr(), "");

  EXPECT_TRUE(typenames_.ResolveTypename("p.IFoo.Result").is_resolved);
}

// Finding the type of nested named member.
struct TypeFinder : AidlVisitor {
  string name;
  const AidlTypeSpecifier* type = nullptr;
  TypeFinder(std::string name) : name(name) {}
  void Visit(const AidlVariableDeclaration& v) override {
    if (v.GetName() == name) {
      type = &v.GetType();
    }
  }
  void Visit(const AidlMethod& m) override {
    if (m.GetName() == name) {
      type = &m.GetType();
    }
  }
  static string Get(const AidlDefinedType& type, const string& name) {
    TypeFinder v(name);
    VisitTopDown(v, type);
    return v.type ? v.type->Signature() : "(null)";
  };
};

TEST_F(AidlTest, UnderstandsNestedTypesViaQualifiedInTheSameScope) {
  io_delegate_.SetFileContents("q/IBar.aidl",
                               "package q;\n"
                               "interface IBar {\n"
                               "  parcelable Baz {}\n"
                               "}");
  import_paths_.emplace("");
  const string input_path = "p/IFoo.aidl";
  const string input =
      "package p;\n"
      "import q.IBar;\n"
      "interface IFoo {\n"
      "  parcelable Nested {\n"
      "    Baz t1;\n"
      "  }\n"
      "  parcelable Baz { }\n"
      "  IBar.Baz t2();\n"
      "  Baz t3();\n"
      "}";
  CaptureStderr();
  auto foo = Parse(input_path, input, typenames_, Options::Language::CPP);
  EXPECT_EQ(GetCapturedStderr(), "");
  ASSERT_NE(nullptr, foo);

  EXPECT_EQ(TypeFinder::Get(*foo, "t1"), "p.IFoo.Baz");
  EXPECT_EQ(TypeFinder::Get(*foo, "t2"), "q.IBar.Baz");
  EXPECT_EQ(TypeFinder::Get(*foo, "t3"), "p.IFoo.Baz");
}

TEST_F(AidlTest, NestedTypeResolutionWithNoPackage) {
  const string input_path = "Foo.aidl";
  const string input =
      "parcelable Foo {\n"
      "  parcelable Bar {\n"
      "    enum Baz { BAZ }\n"
      "    Baz baz = Baz.BAZ;\n"
      "  }\n"
      "  Bar bar;\n"
      "}";
  CaptureStderr();
  auto foo = Parse(input_path, input, typenames_, Options::Language::CPP);
  EXPECT_EQ(GetCapturedStderr(), "");
  ASSERT_NE(nullptr, foo);

  EXPECT_EQ(TypeFinder::Get(*foo, "bar"), "Foo.Bar");
  EXPECT_EQ(TypeFinder::Get(*foo, "baz"), "Foo.Bar.Baz");
}

TEST_F(AidlTest, RejectsNestedTypesWithParentsName) {
  const string input_path = "p/Foo.aidl";
  const string input =
      "package p;\n"
      "parcelable Foo {\n"
      "  parcelable Foo {}\n"
      "}";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse(input_path, input, typenames_, Options::Language::CPP));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr("Nested type 'Foo' has the same name as its parent."));
}

TEST_F(AidlTest, RejectUnstructuredParcelableAsNestedTypes) {
  const string input_path = "p/IFoo.aidl";
  const string input =
      "package p;\n"
      "interface IFoo {\n"
      "  parcelable Bar cpp_header \"Bar.h\";\n"
      "}";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse(input_path, input, typenames_, Options::Language::CPP));
  EXPECT_THAT(GetCapturedStderr(),
              HasSubstr("Unstructured parcelables should be at the root scope"));
}

TEST_F(AidlTest, RejectGenericTypeWithNestedTypes) {
  const string input_path = "p/Foo.aidl";
  const string input =
      "package p;\n"
      "parcelable Foo<T> {\n"
      "  parcelable Bar {}\n"
      "}";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse(input_path, input, typenames_, Options::Language::CPP));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr("Generic types can't have nested types."));
}

TEST_F(AidlTest, HandleSyntaxErrorsInNestedDecl) {
  const string input_path = "p/IFoo.aidl";
  const string input =
      "package p;\n"
      "interface IFoo {\n"
      "  parcelable;\n"  // missing identifier
      "}";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse(input_path, input, typenames_, Options::Language::CPP));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr("expecting identifier"));
}

TEST_F(AidlTest, RejectsNestedTypesWithDuplicateNames) {
  const string input_path = "p/Foo.aidl";
  const string input =
      "package p;\n"
      "interface Foo {\n"
      "  parcelable Bar {}\n"
      "  parcelable Bar {}\n"
      "}";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse(input_path, input, typenames_, Options::Language::CPP));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr("Redefinition of 'Bar'"));
}

TEST_F(AidlTest, TypeResolutionWithMultipleLevelsOfNesting) {
  struct Failure {
    string err;
  };
  struct TestCase {
    string type;
    variant<string, Failure> expected;  // success<0> or failure<1>
  };
  vector<TestCase> cases = {
      {"foo.A", "foo.A"},
      {"foo.A.B", "foo.A.B"},
      {"@nullable(heap=true) A", "foo.A.B.A"},
      // In the scope of foo.A.B.A, B is resolved to A.B.A.B first.
      {"B.A", Failure{"Failed to resolve 'B.A'"}},
      {"B", "foo.A.B.A.B"},
      {"A.B", "foo.A.B.A.B"},
  };
  const string input_path = "foo/A.aidl";
  for (auto& [type, expected] : cases) {
    AidlTypenames typenames;
    // clang-format off
    const string input =
        "package foo;\n"
        "parcelable A {\n"
        "  parcelable B {\n"
        "    parcelable A {\n"
        "      parcelable B {\n"
        "      }\n"
        "      " + type + " m;\n"
        "    }\n"
        "  }\n"
        "}";
    // clang-format on
    CaptureStderr();
    auto foo = Parse(input_path, input, typenames, Options::Language::CPP);
    if (auto failure = std::get_if<Failure>(&expected); failure) {
      ASSERT_EQ(nullptr, foo);
      EXPECT_THAT(GetCapturedStderr(), HasSubstr(failure->err));
    } else {
      EXPECT_EQ(GetCapturedStderr(), "");
      ASSERT_NE(nullptr, foo);
      EXPECT_EQ(TypeFinder::Get(*foo, "m"), std::get<string>(expected));
    }
  }
}

TEST_F(AidlTest, HeaderForNestedTypeShouldPointToTopMostParent) {
  const string input_path = "p/IFoo.aidl";
  const string input =
      "package p;\n"
      "interface IFoo {\n"
      "  parcelable Result {}\n"
      "}";
  CaptureStderr();
  auto foo = Parse(input_path, input, typenames_, Options::Language::CPP);
  ASSERT_NE(nullptr, foo);
  EXPECT_EQ(GetCapturedStderr(), "");

  auto result = typenames_.ResolveTypename("p.IFoo.Result").defined_type;
  ASSERT_NE(nullptr, result);
  EXPECT_EQ("p/IFoo.h", cpp::CppHeaderForType(*result));
}

TEST_F(AidlTest, ReorderNestedTypesForCppOutput) {
  const string input_path = "p/IFoo.aidl";
  const string input = R"(
    package p;
    interface IFoo {
      // partial orderings for [A, D, G]:
      //   D - A
      //   A - G
      parcelable A {
        // partial orderings for [B, C]:
        //   C - B
        parcelable B {
          C c;
          D.E d;
        }
        parcelable C {}
      }
      parcelable D {
        // partial orderings for [E, F]:
        //   F - E
        parcelable E {
          F f;
        }
        parcelable F {}
      }
      parcelable G {
        A.B b;
      }
    }
  )";
  Options options = Options::From("aidl --lang cpp -I. -oout -hout p/IFoo.aidl");
  io_delegate_.SetFileContents(input_path, input);
  CaptureStderr();
  EXPECT_TRUE(compile_aidl(options, io_delegate_));
  EXPECT_EQ(GetCapturedStderr(), "");

  string code;
  EXPECT_TRUE(io_delegate_.GetWrittenContents("out/p/IFoo.h", &code));
  // check partial orderings: a should comes before b
  EXPECT_LE(code.find("class D"), code.find("class A"));
  EXPECT_LE(code.find("class A"), code.find("class G"));
  EXPECT_LE(code.find("class C"), code.find("class B"));
  EXPECT_LE(code.find("class F"), code.find("class E"));
}

TEST_F(AidlTest, RejectsNestedTypesWithCyclicDeps) {
  const string input_path = "p/IFoo.aidl";
  const string input = R"(
    package p;
    interface IFoo {
      // Cycles:
      //   D - A
      //   A - G
      //   G - D
      parcelable A {
        parcelable B {
          C c;
        }
      }
      parcelable C {
        parcelable D {
          E e;
        }
      }
      parcelable E {
        parcelable F {
          A a;
        }
      }
    }
  )";
  Options options = Options::From("aidl --lang cpp -I. -oout -hout p/IFoo.aidl");
  io_delegate_.SetFileContents(input_path, input);
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr("IFoo has nested types with cyclic references."));
}

TEST_F(AidlTest, RejectsCyclicNestedInterfaces) {
  Options options = Options::From(
      "aidl --lang cpp -I. -oout -hout "
      "p/IFoo.aidl p/IBar.aidl p/IQux.aidl");
  io_delegate_.SetFileContents("p/IFoo.aidl",
                               "package p; import p.IBar; "
                               "interface IFoo { IBar getBar(); }");
  io_delegate_.SetFileContents("p/IBar.aidl",
                               "package p; import p.IQux; "
                               "interface IBar { IQux.Inner getQux(); }");
  io_delegate_.SetFileContents("p/IQux.aidl",
                               "package p; import p.IFoo; "
                               "interface IQux { interface Inner { IFoo getFoo(); } }");
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_THAT(GetCapturedStderr(),
              HasSubstr("ERROR: p/IQux.aidl:1.43-53: has cyclic references to nested types."));
}

TEST_F(AidlTest, RejectsCyclicNestedInterfacesAndParcelables) {
  Options options = Options::From(
      "aidl --lang cpp -I. -oout -hout "
      "p/IFoo.aidl p/Bar.aidl");
  io_delegate_.SetFileContents("p/IFoo.aidl",
                               "package p; import p.Bar; "
                               "interface IFoo { interface Inner { Bar getBar(); } }");
  io_delegate_.SetFileContents("p/Bar.aidl",
                               "package p; import p.IFoo; "
                               "parcelable Bar { IFoo.Inner foo; }");
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_THAT(GetCapturedStderr(),
              HasSubstr("ERROR: p/IFoo.aidl:1.42-52: has cyclic references to nested types."));
}

TEST_F(AidlTest, CppNameOf_GenericType) {
  const string input_path = "p/Wrapper.aidl";
  const string input = "package p; parcelable Wrapper<T> {}";

  auto parse_result = Parse(input_path, input, typenames_, Options::Language::CPP);
  EXPECT_NE(nullptr, parse_result);

  auto type = [](std::string name, auto&&... type_params) -> std::unique_ptr<AidlTypeSpecifier> {
    auto params = new std::vector<std::unique_ptr<AidlTypeSpecifier>>;
    (..., params->emplace_back(std::move(type_params)));
    return std::make_unique<AidlTypeSpecifier>(AIDL_LOCATION_HERE, name, std::nullopt, params,
                                               Comments{});
  };

  auto set_nullable = [](std::unique_ptr<AidlTypeSpecifier>&& type) {
    std::vector<std::unique_ptr<AidlAnnotation>> annotations;
    annotations.emplace_back(std::unique_ptr<AidlAnnotation>(
        AidlAnnotation::Parse(AIDL_LOCATION_HERE, "nullable", {}, {})));
    type->Annotate(std::move(annotations));
    return std::move(type);
  };

  auto set_array = [](std::unique_ptr<AidlTypeSpecifier>&& type) {
    (void)type->MakeArray(DynamicArray{});
    return std::move(type);
  };

  auto w = type("p.Wrapper", type("String"));
  EXPECT_EQ("::p::Wrapper<::android::String16>", cpp::CppNameOf(*w, typenames_));

  auto nullable_w = set_nullable(type("p.Wrapper", type("String")));
  EXPECT_EQ("::std::optional<::p::Wrapper<::android::String16>>",
            cpp::CppNameOf(*nullable_w, typenames_));

  auto array_w = set_array(type("p.Wrapper", type("String")));
  EXPECT_EQ("::std::vector<::p::Wrapper<::android::String16>>",
            cpp::CppNameOf(*array_w, typenames_));

  auto nullable_array_w = set_nullable(set_array(type("p.Wrapper", type("String"))));
  EXPECT_EQ("::std::optional<::std::vector<::std::optional<::p::Wrapper<::android::String16>>>>",
            cpp::CppNameOf(*nullable_array_w, typenames_));

  auto list_w = type("List", type("p.Wrapper", type("String")));
  EXPECT_EQ("::std::vector<::p::Wrapper<::android::String16>>",
            cpp::CppNameOf(*list_w, typenames_));

  auto nullable_list_w = set_nullable(type("List", type("p.Wrapper", type("String"))));
  EXPECT_EQ("::std::optional<::std::vector<::std::optional<::p::Wrapper<::android::String16>>>>",
            cpp::CppNameOf(*nullable_list_w, typenames_));
}

TEST_P(AidlTest, UnderstandsNativeParcelables) {
  io_delegate_.SetFileContents(
      "p/Bar.aidl",
      "package p; parcelable Bar cpp_header \"baz/header\";");
  import_paths_.emplace("");
  const string input_path = "p/IFoo.aidl";
  const string input = "package p; import p.Bar; interface IFoo { }";
  auto parse_result = Parse(input_path, input, typenames_, GetLanguage());
  EXPECT_NE(nullptr, parse_result);
  EXPECT_TRUE(typenames_.ResolveTypename("p.Bar").is_resolved);
  AidlTypeSpecifier native_type(AIDL_LOCATION_HERE, "p.Bar", /*array=*/std::nullopt, nullptr, {});
  native_type.Resolve(typenames_, parse_result);

  EXPECT_EQ("p.Bar", java::InstantiableJavaSignatureOf(native_type));
  // C++ understands C++ specific stuff
  EXPECT_EQ("::p::Bar", cpp::CppNameOf(native_type, typenames_));
  set<string> headers;
  cpp::AddHeaders(native_type, typenames_, &headers);
  EXPECT_EQ(1u, headers.size());
  EXPECT_EQ(1u, headers.count("baz/header"));
}

TEST_F(AidlTest, WritesCorrectDependencyFile) {
  // While the in tree build system always gives us an output file name,
  // other android tools take advantage of our ability to infer the intended
  // file name.  This test makes sure we handle this correctly.
  vector<string> args = {"aidl", "-I .", "-d dep/file/path", "-o place/for/output", "p/IFoo.aidl"};
  Options options = Options::From(args);
  io_delegate_.SetFileContents(options.InputFiles().front(), "package p; interface IFoo {}");
  EXPECT_TRUE(compile_aidl(options, io_delegate_));
  string actual_dep_file_contents;
  EXPECT_TRUE(io_delegate_.GetWrittenContents(options.DependencyFile(), &actual_dep_file_contents));
  EXPECT_EQ(actual_dep_file_contents, kExpectedDepFileContents);
}

TEST_F(AidlTest, WritesCorrectDependencyFileNinja) {
  // While the in tree build system always gives us an output file name,
  // other android tools take advantage of our ability to infer the intended
  // file name.  This test makes sure we handle this correctly.
  vector<string> args = {"aidl",       "-I .", "-d dep/file/path", "--ninja", "-o place/for/output",
                         "p/IFoo.aidl"};
  Options options = Options::From(args);
  io_delegate_.SetFileContents(options.InputFiles().front(), "package p; interface IFoo {}");
  EXPECT_TRUE(compile_aidl(options, io_delegate_));
  string actual_dep_file_contents;
  EXPECT_TRUE(io_delegate_.GetWrittenContents(options.DependencyFile(), &actual_dep_file_contents));
  EXPECT_EQ(actual_dep_file_contents, kExpectedNinjaDepFileContents);
}

TEST_F(AidlTest, WritesTrivialDependencyFileForParcelableDeclaration) {
  // The SDK uses aidl to decide whether a .aidl file is a parcelable.  It does
  // this by calling aidl with every .aidl file it finds, then parsing the
  // generated dependency files.  Those that reference .java output files are
  // for interfaces and those that do not are parcelables.  However, for both
  // parcelables and interfaces, we *must* generate a non-empty dependency file.
  vector<string> args = {"aidl", "-I .", "-o place/for/output", "-d dep/file/path", "p/Foo.aidl"};
  Options options = Options::From(args);
  io_delegate_.SetFileContents(options.InputFiles().front(), "package p; parcelable Foo;");
  EXPECT_TRUE(compile_aidl(options, io_delegate_));
  string actual_dep_file_contents;
  EXPECT_TRUE(io_delegate_.GetWrittenContents(options.DependencyFile(), &actual_dep_file_contents));
  EXPECT_EQ(actual_dep_file_contents, kExpectedParcelableDeclarationDepFileContents);
}

TEST_F(AidlTest, WritesDependencyFileForStructuredParcelable) {
  vector<string> args = {
      "aidl", "-I .", "--structured", "-o place/for/output", "-d dep/file/path", "p/Foo.aidl"};
  Options options = Options::From(args);
  io_delegate_.SetFileContents(options.InputFiles().front(), "package p; parcelable Foo {int a;}");
  EXPECT_TRUE(compile_aidl(options, io_delegate_));
  string actual_dep_file_contents;
  EXPECT_TRUE(io_delegate_.GetWrittenContents(options.DependencyFile(), &actual_dep_file_contents));
  EXPECT_EQ(actual_dep_file_contents, kExpectedStructuredParcelableDepFileContents);
}

TEST_F(AidlTest, NoJavaOutputForParcelableDeclaration) {
  vector<string> args = {"aidl", "-I .", "--lang=java", "-o place/for/output", "p/Foo.aidl"};
  Options options = Options::From(args);
  io_delegate_.SetFileContents(options.InputFiles().front(), "package p; parcelable Foo;");
  EXPECT_TRUE(compile_aidl(options, io_delegate_));
  string output_file_contents;
  EXPECT_FALSE(io_delegate_.GetWrittenContents(options.OutputFile(), &output_file_contents));
}

TEST_P(AidlTest, RejectsListArray) {
  const string input = "package a; parcelable Foo { List<String>[] lists; }";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("a/Foo.aidl", input, typenames_, GetLanguage()));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr("Arrays of List are not supported"));
}

TEST_P(AidlTest, RejectsPrimitiveListInStableAidl) {
  AidlError error;
  string expected_stderr =
      "ERROR: a/IFoo.aidl:2.7-11: "
      "Encountered an untyped List or Map. The use of untyped List/Map is "
      "prohibited because it is not guaranteed that the objects in the list are recognizable in "
      "the receiving side. Consider switching to an array or a generic List/Map.\n";
  if (GetLanguage() != Options::Language::JAVA) {
    expected_stderr =
        "ERROR: a/IFoo.aidl:2.1-7: "
        "Currently, only the Java backend supports non-generic List.\n";
  }

  const string primitive_interface =
      "package a; interface IFoo {\n"
      "  List foo(); }";
  CaptureStderr();
  AidlTypenames tn1;
  EXPECT_EQ(nullptr, Parse("a/IFoo.aidl", primitive_interface, tn1, GetLanguage(), &error,
                           {"--structured"}));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());

  string primitive_parcelable =
      "package a; parcelable IFoo {\n"
      "  List foo;}";
  CaptureStderr();
  AidlTypenames tn2;
  EXPECT_EQ(nullptr, Parse("a/IFoo.aidl", primitive_parcelable, tn2, GetLanguage(), &error,
                           {"--structured"}));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_P(AidlTest, ExtensionTest) {
  CaptureStderr();
  string extendable_parcelable =
      "package a; parcelable Data {\n"
      "  ParcelableHolder extension;\n"
      "  ParcelableHolder extension2;\n"
      "}";
  EXPECT_NE(nullptr, Parse("a/Data.aidl", extendable_parcelable, typenames_, GetLanguage()));
  EXPECT_EQ("", GetCapturedStderr());
}

TEST_P(AidlTest, ParcelableHolderAsReturnType) {
  CaptureStderr();
  string parcelableholder_return_interface =
      "package a; interface IFoo {\n"
      "  ParcelableHolder foo();\n"
      "}";
  EXPECT_EQ(nullptr,
            Parse("a/IFoo.aidl", parcelableholder_return_interface, typenames_, GetLanguage()));

  EXPECT_EQ("ERROR: a/IFoo.aidl:2.19-23: ParcelableHolder cannot be a return type\n",
            GetCapturedStderr());
}

TEST_P(AidlTest, ParcelableHolderAsArgumentType) {
  CaptureStderr();
  string extendable_parcelable_arg_interface =
      "package a; interface IFoo {\n"
      "  void foo(in ParcelableHolder ph);\n"
      "}";
  EXPECT_EQ(nullptr,
            Parse("a/IFoo.aidl", extendable_parcelable_arg_interface, typenames_, GetLanguage()));

  EXPECT_EQ("ERROR: a/IFoo.aidl:2.31-34: ParcelableHolder cannot be an argument type\n",
            GetCapturedStderr());
}

TEST_P(AidlTest, RejectNullableParcelableHolderField) {
  io_delegate_.SetFileContents("Foo.aidl", "parcelable Foo { @nullable ParcelableHolder ext; }");
  Options options = Options::From("aidl Foo.aidl -I . --lang=" + to_string(GetLanguage()));
  const string expected_stderr = "ERROR: Foo.aidl:1.27-44: ParcelableHolder cannot be nullable.\n";
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_P(AidlTest, ParcelablesWithConstants) {
  io_delegate_.SetFileContents("Foo.aidl", "parcelable Foo { const int BIT = 0x1 << 3; }");
  Options options = Options::From("aidl Foo.aidl -I . --lang=" + to_string(GetLanguage()));
  CaptureStderr();
  EXPECT_TRUE(compile_aidl(options, io_delegate_));
  EXPECT_EQ("", GetCapturedStderr());
}

TEST_P(AidlTest, UnionWithConstants) {
  io_delegate_.SetFileContents("Foo.aidl", "union Foo { const int BIT = 0x1 << 3; int n; }");
  Options options = Options::From("aidl Foo.aidl -I . --lang=" + to_string(GetLanguage()));
  CaptureStderr();
  EXPECT_TRUE(compile_aidl(options, io_delegate_));
  EXPECT_EQ("", GetCapturedStderr());
}

TEST_F(AidlTest, ConstantsWithAnnotations) {
  io_delegate_.SetFileContents("IFoo.aidl",
                               "interface IFoo {\n"
                               " @JavaPassthrough(annotation=\"@Foo\")\n"
                               " const @JavaPassthrough(annotation=\"@Bar\") int FOO = 0;\n"
                               "}");
  Options options = Options::From("aidl IFoo.aidl -I . --lang=java -o out");
  CaptureStderr();
  EXPECT_TRUE(compile_aidl(options, io_delegate_));
  EXPECT_EQ("", GetCapturedStderr());
  string code;
  EXPECT_TRUE(io_delegate_.GetWrittenContents("out/IFoo.java", &code));
  EXPECT_THAT(code, HasSubstr("@Foo\n"));
  EXPECT_THAT(code, HasSubstr("@Bar\n"));
}

TEST_F(AidlTest, ApiDump) {
  io_delegate_.SetFileContents(
      "foo/bar/IFoo.aidl",
      "// comment\n"
      "package foo.bar;\n"
      "import foo.bar.Data;\n"
      "// commented /* @hide */\n"
      "interface IFoo {\n"
      "    /* @hide applied \n"
      "       @deprecated use foo2 */\n"
      "    int foo(out int[] a, String b, boolean c, inout List<String> d);\n"
      "    int foo2(@utf8InCpp String x, inout List<String> y);\n"
      "    IFoo foo3(IFoo foo);\n"
      "    void foo4(in int[2][3] fixedArray);\n"
      "    Data getData();\n"
      "    // @hide not applied\n"
      "    /** blahblah\n"
      "        @deprecated\n"
      "          reason why... */\n"
      "    const int A = 1;\n"
      "    // @deprecated tags in line comments are ignored\n"
      "    const String STR = \"Hello\";\n"
      "}\n");
  io_delegate_.SetFileContents("foo/bar/Data.aidl",
                               "// comment\n"
                               "package foo.bar;\n"
                               "import foo.bar.IFoo;\n"
                               "@JavaDerive(equals=true, toString=true)\n"
                               "/* @hide*/\n"
                               "parcelable Data {\n"
                               "   // @hide\n"
                               "   int x = 10;\n"
                               "   // @hide\n"
                               "   int y;\n"
                               "   /*@hide2*/\n"
                               "   IFoo foo;\n"
                               "   // Ignore @hide property in line comment\n"
                               "   @nullable String[] c;\n"
                               "}\n");
  io_delegate_.SetFileContents("api.aidl", "");
  vector<string> args = {"aidl", "--dumpapi", "--out=dump", "--include=.",
                         "foo/bar/IFoo.aidl", "foo/bar/Data.aidl"};
  Options options = Options::From(args);
  bool result = dump_api(options, io_delegate_);
  ASSERT_TRUE(result);
  string actual;
  EXPECT_TRUE(io_delegate_.GetWrittenContents("dump/foo/bar/IFoo.aidl", &actual));
  EXPECT_EQ(string("// comment\n").append(string(kPreamble)).append(R"(package foo.bar;
interface IFoo {
  /**
   * @hide
   * @deprecated use foo2
   */
  int foo(out int[] a, String b, boolean c, inout List<String> d);
  int foo2(@utf8InCpp String x, inout List<String> y);
  foo.bar.IFoo foo3(foo.bar.IFoo foo);
  void foo4(in int[2][3] fixedArray);
  foo.bar.Data getData();
  /**
   * @deprecated reason why...
   */
  const int A = 1;
  const String STR = "Hello";
}
)"),
            actual);

  EXPECT_TRUE(io_delegate_.GetWrittenContents("dump/foo/bar/Data.aidl", &actual));
  EXPECT_EQ(string("// comment\n").append(string(kPreamble)).append(R"(package foo.bar;
/* @hide */
@JavaDerive(equals=true, toString=true)
parcelable Data {
  int x = 10;
  int y;
  foo.bar.IFoo foo;
  @nullable String[] c;
}
)"),
            actual);
}

TEST_F(AidlTest, ApiDumpWithManualIds) {
  io_delegate_.SetFileContents(
      "foo/bar/IFoo.aidl",
      "package foo.bar;\n"
      "interface IFoo {\n"
      "    int foo() = 1;\n"
      "    int bar() = 2;\n"
      "    int baz() = 10;\n"
      "}\n");

  vector<string> args = {"aidl", "-I . ", "--dumpapi", "-o dump", "foo/bar/IFoo.aidl"};
  Options options = Options::From(args);
  bool result = dump_api(options, io_delegate_);
  ASSERT_TRUE(result);
  string actual;
  EXPECT_TRUE(io_delegate_.GetWrittenContents("dump/foo/bar/IFoo.aidl", &actual));
  EXPECT_EQ(actual, string(kPreamble).append(R"(package foo.bar;
interface IFoo {
  int foo() = 1;
  int bar() = 2;
  int baz() = 10;
}
)"));
}

TEST_F(AidlTest, ApiDumpWithManualIdsOnlyOnSomeMethods) {
  const string expected_stderr =
      "ERROR: foo/bar/IFoo.aidl:4.8-12: You must either assign id's to all methods or to none of "
      "them.\n";
  io_delegate_.SetFileContents(
      "foo/bar/IFoo.aidl",
      "package foo.bar;\n"
      "interface IFoo {\n"
      "    int foo() = 1;\n"
      "    int bar();\n"
      "    int baz() = 10;\n"
      "}\n");

  vector<string> args = {"aidl", "-I . ", "--dumpapi", "-o dump", "foo/bar/IFoo.aidl"};
  Options options = Options::From(args);
  CaptureStderr();
  EXPECT_FALSE(dump_api(options, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_F(AidlTest, ApiDumpConstWithAnnotation) {
  io_delegate_.SetFileContents("foo/bar/IFoo.aidl",
                               "package foo.bar;\n"
                               "interface IFoo {\n"
                               "    @utf8InCpp String foo();\n"
                               "    const @utf8InCpp String bar = \"bar\";\n"
                               "}\n");

  vector<string> args = {"aidl", "-I . ", "--dumpapi", "-o dump", "foo/bar/IFoo.aidl"};
  Options options = Options::From(args);
  CaptureStderr();
  EXPECT_TRUE(dump_api(options, io_delegate_));
  EXPECT_EQ("", GetCapturedStderr());
  string actual;
  EXPECT_TRUE(io_delegate_.GetWrittenContents("dump/foo/bar/IFoo.aidl", &actual));
  EXPECT_EQ(string(kPreamble).append(R"(package foo.bar;
interface IFoo {
  @utf8InCpp String foo();
  const @utf8InCpp String bar = "bar";
}
)"),
            actual);
}

TEST_F(AidlTest, ApiDumpWithEnums) {
  io_delegate_.SetFileContents("foo/bar/Enum.aidl",
                               "package foo.bar;\n"
                               "enum Enum {\n"
                               "    FOO,\n"
                               "    BAR = FOO + 1,\n"
                               "}\n");

  vector<string> args = {"aidl", "--dumpapi", "-I . ", "-o dump", "foo/bar/Enum.aidl"};
  Options options = Options::From(args);
  CaptureStderr();
  EXPECT_TRUE(dump_api(options, io_delegate_));
  EXPECT_EQ("", GetCapturedStderr());
  string actual;
  EXPECT_TRUE(io_delegate_.GetWrittenContents("dump/foo/bar/Enum.aidl", &actual));
  EXPECT_EQ(string(kPreamble).append("package foo.bar;\n"
                                     "enum Enum {\n"
                                     "  FOO,\n"
                                     "  BAR = (FOO + 1) /* 1 */,\n"
                                     "}\n"),
            actual);
}

TEST_F(AidlTest, ApiDumpWithEnumDefaultValues) {
  io_delegate_.SetFileContents("foo/bar/Enum.aidl",
                               "package foo.bar;\n"
                               "enum Enum {\n"
                               "    FOO,\n"
                               "}\n");
  io_delegate_.SetFileContents("foo/bar/Foo.aidl",
                               "package foo.bar;\n"
                               "import foo.bar.Enum;\n"
                               "parcelable Foo {\n"
                               "    Enum e = Enum.FOO;\n"
                               "    int n = Enum.FOO;\n"
                               "}\n");

  vector<string> args = {"aidl", "--dumpapi", "-I . ", "-o dump", "foo/bar/Foo.aidl"};
  Options options = Options::From(args);
  CaptureStderr();
  EXPECT_TRUE(dump_api(options, io_delegate_));
  EXPECT_EQ("", GetCapturedStderr());
  string actual;
  EXPECT_TRUE(io_delegate_.GetWrittenContents("dump/foo/bar/Foo.aidl", &actual));
  EXPECT_EQ(string(kPreamble).append("package foo.bar;\n"
                                     "parcelable Foo {\n"
                                     "  foo.bar.Enum e = foo.bar.Enum.FOO;\n"
                                     "  int n = foo.bar.Enum.FOO /* 0 */;\n"
                                     "}\n"),
            actual);
}

TEST_F(AidlTest, ApiDumpWithGenerics) {
  io_delegate_.SetFileContents("foo/bar/Foo.aidl",
                               "package foo.bar;\n"
                               "parcelable Foo<T, U> {\n"
                               "}\n");

  vector<string> args = {"aidl", "--dumpapi", "-I . ", "-o dump", "foo/bar/Foo.aidl"};
  Options options = Options::From(args);
  CaptureStderr();
  EXPECT_TRUE(dump_api(options, io_delegate_));
  EXPECT_EQ("", GetCapturedStderr());
  string actual;
  EXPECT_TRUE(io_delegate_.GetWrittenContents("dump/foo/bar/Foo.aidl", &actual));
  EXPECT_EQ(string(kPreamble).append("package foo.bar;\n"
                                     "parcelable Foo<T, U> {\n"
                                     "}\n"),
            actual);
}

TEST_F(AidlTest, ImportedDocumentHasDuplicateDefinitions) {
  io_delegate_.SetFileContents("IFoo.aidl", "interface IFoo; interface IFoo;\n");
  io_delegate_.SetFileContents("Bar.aidl", "enum Bar { CONST = IFoo.NONE }\n");

  vector<string> args = {"aidl", "--dumpapi", "-I.", "-o out", "Bar.aidl"};
  Options options = Options::From(args);
  CaptureStderr();
  EXPECT_FALSE(dump_api(options, io_delegate_));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr("Can't find NONE in IFoo"));
}

TEST_F(AidlTest, CheckNumGenericTypeSecifier) {
  const string expected_list_stderr =
      "ERROR: p/IFoo.aidl:1.37-41: List can only have one type parameter, but got: "
      "'List<String,String>'\n";
  const string expected_map_stderr =
      "ERROR: p/IFoo.aidl:1.37-40: Map must have 0 or 2 type parameters, but got 'Map<String>'\n";
  Options options = Options::From("aidl -I . p/IFoo.aidl IFoo.java");
  io_delegate_.SetFileContents(options.InputFiles().front(),
                               "package p; interface IFoo {"
                               "void foo(List<String, String> a);}");
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_EQ(expected_list_stderr, GetCapturedStderr());

  io_delegate_.SetFileContents(options.InputFiles().front(),
                               "package p; interface IFoo {"
                               "void foo(Map<String> a);}");
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_EQ(expected_map_stderr, GetCapturedStderr());
}

TEST_F(AidlTest, CheckTypeParameterInMapType) {
  const string expected_stderr =
      "ERROR: p/IFoo.aidl:1.28-31: The type of key in map must be String, but it is 'p.Bar'\n";
  Options options = Options::From("aidl -I . p/IFoo.aidl");
  io_delegate_.SetFileContents("p/Bar.aidl", "package p; parcelable Bar { String s; }");

  io_delegate_.SetFileContents("p/IFoo.aidl",
                               "package p; interface IFoo {"
                               "Map<String, p.Bar> foo();}");
  EXPECT_TRUE(compile_aidl(options, io_delegate_));

  io_delegate_.SetFileContents("p/IFoo.aidl",
                               "package p; interface IFoo {"
                               "Map<p.Bar, p.Bar> foo();}");
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());

  io_delegate_.SetFileContents("p/IFoo.aidl",
                               "package p; interface IFoo {"
                               "Map<String, String> foo();}");
  EXPECT_TRUE(compile_aidl(options, io_delegate_));

  io_delegate_.SetFileContents("p/IFoo.aidl",
                               "package p; interface IFoo {"
                               "Map<String, ParcelFileDescriptor> foo();}");
  EXPECT_TRUE(compile_aidl(options, io_delegate_));
}

TEST_F(AidlTest, WrongGenericType) {
  const string expected_stderr = "ERROR: p/IFoo.aidl:1.28-34: String is not a generic type.\n";
  Options options = Options::From("aidl -I . p/IFoo.aidl IFoo.java");
  io_delegate_.SetFileContents(options.InputFiles().front(),
                               "package p; interface IFoo {"
                               "String<String> foo(); }");
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_F(AidlTest, UserDefinedUnstructuredGenericParcelableType) {
  Options optionsForParcelable = Options::From("aidl -I . p/Bar.aidl");
  io_delegate_.SetFileContents("p/Bar.aidl", "package p; parcelable Bar<T, T>;");
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(optionsForParcelable, io_delegate_));
  EXPECT_EQ("ERROR: p/Bar.aidl:1.22-26: Every type parameter should be unique.\n",
            GetCapturedStderr());

  Options options = Options::From("aidl -I . p/IFoo.aidl");
  io_delegate_.SetFileContents("p/Bar.aidl", "package p; parcelable Bar;");
  io_delegate_.SetFileContents("p/IFoo.aidl",
                               "package p; interface IFoo {"
                               "p.Bar<String, String> foo();}");
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr("p.Bar is not a generic type"));
  io_delegate_.SetFileContents("p/Bar.aidl", "package p; parcelable Bar<T>;");
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr("p.Bar must have 1 type parameters, but got 2"));
  io_delegate_.SetFileContents("p/Bar.aidl", "package p; parcelable Bar<T, V>;");
  EXPECT_TRUE(compile_aidl(options, io_delegate_));
  io_delegate_.SetFileContents("p/IFoo.aidl",
                               "package p; interface IFoo {"
                               "p.Bar<String, ParcelFileDescriptor> foo();}");
  EXPECT_TRUE(compile_aidl(options, io_delegate_));

  io_delegate_.SetFileContents("p/IFoo.aidl",
                               "package p; interface IFoo {"
                               "p.Bar<int, long> foo();}");

  io_delegate_.SetFileContents("p/IFoo.aidl",
                               "package p; interface IFoo {"
                               "p.Bar<int[], long[]> foo();}");

  EXPECT_TRUE(compile_aidl(options, io_delegate_));
}

TEST_F(AidlTest, FailOnMultipleTypesInSingleFile) {
  std::vector<std::string> rawOptions{"aidl --lang=java -I . -o out foo/bar/Foo.aidl",
                                      "aidl --lang=cpp -I . -o out -h out/include foo/bar/Foo.aidl",
                                      "aidl --lang=rust -I . -o out foo/bar/Foo.aidl"};
  for (const auto& rawOption : rawOptions) {
    string expected_stderr =
        "ERROR: foo/bar/Foo.aidl:3.1-10: You must declare only one type per file.\n";
    Options options = Options::From(rawOption);
    io_delegate_.SetFileContents(options.InputFiles().front(),
                                 "package foo.bar;\n"
                                 "interface IFoo1 { int foo(); }\n"
                                 "interface IFoo2 { int foo(); }\n"
                                 "parcelable Data1 { int a; int b;}\n"
                                 "parcelable Data2 { int a; int b;}\n");
    CaptureStderr();
    EXPECT_FALSE(compile_aidl(options, io_delegate_));
    EXPECT_EQ(expected_stderr, GetCapturedStderr());

    io_delegate_.SetFileContents(options.InputFiles().front(),
                                 "package foo.bar;\n"
                                 "interface IFoo1 { int foo(); }\n"
                                 "interface IFoo2 { int foo(); }\n");
    CaptureStderr();
    EXPECT_FALSE(compile_aidl(options, io_delegate_));
    EXPECT_EQ(expected_stderr, GetCapturedStderr());

    expected_stderr = "ERROR: foo/bar/Foo.aidl:3.11-17: You must declare only one type per file.\n";
    io_delegate_.SetFileContents(options.InputFiles().front(),
                                 "package foo.bar;\n"
                                 "parcelable Data1 { int a; int b;}\n"
                                 "parcelable Data2 { int a; int b;}\n");
    CaptureStderr();
    EXPECT_FALSE(compile_aidl(options, io_delegate_));
    EXPECT_EQ(expected_stderr, GetCapturedStderr());
  }
}

TEST_P(AidlTest, FailParseOnEmptyFile) {
  const string contents = "";
  const string expected_stderr = "ERROR: a/IFoo.aidl:1.1-1: syntax error, unexpected $end\n";
  const string expected_stderr_newbison =
      "ERROR: a/IFoo.aidl:1.1-1: syntax error, unexpected end of file\n";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("a/IFoo.aidl", contents, typenames_, GetLanguage()));
  EXPECT_THAT(GetCapturedStderr(),
              testing::AnyOf(testing::Eq(expected_stderr), testing::Eq(expected_stderr_newbison)));
}

TEST_F(AidlTest, MultipleInputFiles) {
  Options options = Options::From(
      "aidl --lang=java -o out -I . foo/bar/IFoo.aidl foo/bar/Data.aidl");

  io_delegate_.SetFileContents(options.InputFiles().at(0),
      "package foo.bar;\n"
      "import foo.bar.Data;\n"
      "interface IFoo { Data getData(); }\n");

  io_delegate_.SetFileContents(options.InputFiles().at(1),
        "package foo.bar;\n"
        "import foo.bar.IFoo;\n"
        "parcelable Data { IFoo foo; }\n");

  EXPECT_TRUE(compile_aidl(options, io_delegate_));

  string content;
  for (const auto file : {
    "out/foo/bar/IFoo.java", "out/foo/bar/Data.java"}) {
    content.clear();
    EXPECT_TRUE(io_delegate_.GetWrittenContents(file, &content));
    EXPECT_FALSE(content.empty());
  }
}

TEST_F(AidlTest, MultipleInputFilesCpp) {
  Options options = Options::From(
      "aidl --lang=cpp -I . -o out -h out/include "
      "-I . foo/bar/IFoo.aidl foo/bar/Data.aidl");

  io_delegate_.SetFileContents(options.InputFiles().at(0),
      "package foo.bar;\n"
      "import foo.bar.Data;\n"
      "interface IFoo { Data getData(); }\n");

  io_delegate_.SetFileContents(options.InputFiles().at(1),
        "package foo.bar;\n"
        "import foo.bar.IFoo;\n"
        "parcelable Data { IFoo foo; }\n");

  EXPECT_TRUE(compile_aidl(options, io_delegate_));

  string content;
  for (const auto file : {
    "out/foo/bar/IFoo.cpp", "out/foo/bar/Data.cpp",
    "out/include/foo/bar/IFoo.h", "out/include/foo/bar/Data.h",
    "out/include/foo/bar/BpFoo.h", "out/include/foo/bar/BpData.h",
    "out/include/foo/bar/BnFoo.h", "out/include/foo/bar/BnData.h"}) {
    content.clear();
    EXPECT_TRUE(io_delegate_.GetWrittenContents(file, &content));
    EXPECT_FALSE(content.empty());
  }
}

TEST_F(AidlTest, MultipleInputFilesRust) {
  Options options =
      Options::From("aidl --lang=rust -o out -I . foo/bar/IFoo.aidl foo/bar/Data.aidl");

  io_delegate_.SetFileContents(options.InputFiles().at(0),
                               "package foo.bar;\n"
                               "import foo.bar.Data;\n"
                               "interface IFoo { Data getData(); }\n");

  io_delegate_.SetFileContents(options.InputFiles().at(1),
                               "package foo.bar;\n"
                               "import foo.bar.IFoo;\n"
                               "parcelable Data { IFoo foo; }\n");

  EXPECT_TRUE(compile_aidl(options, io_delegate_));

  string content;
  for (const auto file : {"out/foo/bar/IFoo.rs", "out/foo/bar/Data.rs"}) {
    content.clear();
    EXPECT_TRUE(io_delegate_.GetWrittenContents(file, &content));
    EXPECT_FALSE(content.empty());
  }
}

TEST_F(AidlTest, ConflictWithMetaTransactionGetVersion) {
  const string expected_stderr =
      "ERROR: p/IFoo.aidl:1.31-51:  method getInterfaceVersion() is reserved for internal use.\n";
  Options options = Options::From("aidl --lang=java -I . -o place/for/output p/IFoo.aidl");
  // int getInterfaceVersion() is one of the meta transactions
  io_delegate_.SetFileContents(options.InputFiles().front(),
                               "package p; interface IFoo {"
                               "int getInterfaceVersion(); }");
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_F(AidlTest, ConflictWithSimilarMetaTransaction) {
  // boolean getInterfaceVersion() is not a meta transaction, but should be
  // prevented because return type is not part of a method signature
  const string expected_stderr =
      "ERROR: p/IFoo.aidl:1.35-55:  method getInterfaceVersion() is reserved for internal use.\n";
  Options options = Options::From("aidl --lang=java -I . -o place/for/output p/IFoo.aidl");
  io_delegate_.SetFileContents(options.InputFiles().front(),
                               "package p; interface IFoo {"
                               "boolean getInterfaceVersion(); }");
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_F(AidlTest, ConflictWithMetaTransactionGetName) {
  // this is another reserved name
  const string expected_stderr =
      "ERROR: p/IFoo.aidl:1.34-53:  method getTransactionName(int) is reserved for internal use.\n";
  Options options = Options::From("aidl --lang=java -I . -o place/for/output p/IFoo.aidl");
  io_delegate_.SetFileContents(options.InputFiles().front(),
                               "package p; interface IFoo {"
                               "String getTransactionName(int code); }");
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());

  // this is not a meta interface method as it differs type arguments
  io_delegate_.SetFileContents(options.InputFiles().front(),
                               "package p; interface IFoo {"
                               "String getTransactionName(); }");
  EXPECT_TRUE(compile_aidl(options, io_delegate_));
}

TEST_F(AidlTest, CheckApiForEquality) {
  CaptureStderr();
  Options options = Options::From("aidl --checkapi=equal old new");

  io_delegate_.SetFileContents("old/p/IFoo.aidl",
                               "package p; interface IFoo{ @utf8InCpp @nullable String foo();}");
  io_delegate_.SetFileContents("new/p/IFoo.aidl",
                               "package p; interface IFoo{ @utf8InCpp String foo();}");

  EXPECT_FALSE(::android::aidl::check_api(options, io_delegate_));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr("+  @utf8InCpp String foo();"));
}

TEST_F(AidlTest, DifferentOrderAnnotationsInCheckAPI) {
  Options options = Options::From("aidl --checkapi old new");
  io_delegate_.SetFileContents("old/p/IFoo.aidl",
                               "package p; interface IFoo{ @utf8InCpp @nullable String foo();}");
  io_delegate_.SetFileContents("new/p/IFoo.aidl",
                               "package p; interface IFoo{ @nullable @utf8InCpp String foo();}");

  EXPECT_TRUE(::android::aidl::check_api(options, io_delegate_));
}

TEST_F(AidlTest, JavaPassthroughAnnotationAddedInCheckApi) {
  Options options = Options::From("aidl --checkapi old new");
  io_delegate_.SetFileContents("old/p/IFoo.aidl", "package p; interface IFoo{}");
  io_delegate_.SetFileContents("new/p/IFoo.aidl",
                               "package p; @JavaPassthrough(annotation=\"@foo\") interface IFoo{}");

  EXPECT_TRUE(::android::aidl::check_api(options, io_delegate_));
}

TEST_F(AidlTest, SuccessOnIdenticalApiDumps) {
  Options options = Options::From("aidl --checkapi old new");
  io_delegate_.SetFileContents("old/p/IFoo.aidl", "package p; interface IFoo{ void foo();}");
  io_delegate_.SetFileContents("new/p/IFoo.aidl", "package p; interface IFoo{ void foo();}");

  EXPECT_TRUE(::android::aidl::check_api(options, io_delegate_));
}

TEST_F(AidlTest, CheckApi_EnumFieldsWithDefaultValues) {
  Options options = Options::From("aidl --checkapi old new");
  const string foo_definition = "package p; parcelable Foo{ p.Enum e = p.Enum.FOO; }";
  const string enum_definition = "package p; enum Enum { FOO }";
  io_delegate_.SetFileContents("old/p/Foo.aidl", foo_definition);
  io_delegate_.SetFileContents("old/p/Enum.aidl", enum_definition);
  io_delegate_.SetFileContents("new/p/Foo.aidl", foo_definition);
  io_delegate_.SetFileContents("new/p/Enum.aidl", enum_definition);

  EXPECT_TRUE(::android::aidl::check_api(options, io_delegate_));
}

TEST_F(AidlTest, CheckApi_EnumFieldsFromImported) {
  Options options = Options::From("aidl --checkapi old new -I import");

  io_delegate_.SetFileContents("old/p/Foo.aidl", "package p; parcelable Foo{ other.Enum e; }");
  io_delegate_.SetFileContents("new/p/Foo.aidl",
                               "package p; parcelable Foo{ other.Enum e = other.Enum.FOO; }");
  io_delegate_.SetFileContents("import/other/Enum.aidl", "package other; enum Enum { FOO }");

  EXPECT_TRUE(::android::aidl::check_api(options, io_delegate_));
}

TEST_F(AidlTest, CheckApiEqual_EnumFieldsWithDefaultValues) {
  Options options = Options::From("aidl --checkapi=equal old new");
  const string foo_definition = "package p; parcelable Foo{ p.Enum e = p.Enum.FOO; }";
  const string enum_definition = "package p; enum Enum { FOO }";
  io_delegate_.SetFileContents("old/p/Foo.aidl", foo_definition);
  io_delegate_.SetFileContents("old/p/Enum.aidl", enum_definition);
  io_delegate_.SetFileContents("new/p/Foo.aidl", foo_definition);
  io_delegate_.SetFileContents("new/p/Enum.aidl", enum_definition);
  CaptureStderr();
  EXPECT_TRUE(::android::aidl::check_api(options, io_delegate_));
  EXPECT_EQ("", GetCapturedStderr());
}

class AidlTestCompatibleChanges : public AidlTest {
 protected:
  Options options_ = Options::From("aidl --checkapi old new");
};

TEST_F(AidlTestCompatibleChanges, NewType) {
  io_delegate_.SetFileContents("old/p/IFoo.aidl",
                               "package p;"
                               "interface IFoo {"
                               "  void foo(int a);"
                               "}");
  io_delegate_.SetFileContents("new/p/IFoo.aidl",
                               "package p;"
                               "interface IFoo {"
                               "  void foo(int a);"
                               "}");
  io_delegate_.SetFileContents("new/p/IBar.aidl",
                               "package p;"
                               "interface IBar {"
                               "  void bar();"
                               "}");
  EXPECT_TRUE(::android::aidl::check_api(options_, io_delegate_));
}

TEST_F(AidlTestCompatibleChanges, NewMethod) {
  io_delegate_.SetFileContents("old/p/IFoo.aidl",
                               "package p;"
                               "interface IFoo {"
                               "  void foo(int a);"
                               "}");
  io_delegate_.SetFileContents("new/p/IFoo.aidl",
                               "package p;"
                               "interface IFoo {"
                               "  void foo(int a);"
                               "  void bar();"
                               "  void baz(in List<String> arg);"
                               "}");
  EXPECT_TRUE(::android::aidl::check_api(options_, io_delegate_));
}

TEST_F(AidlTestCompatibleChanges, NewField) {
  io_delegate_.SetFileContents("old/p/Data.aidl",
                               "package p;"
                               "parcelable Data {"
                               "  int foo;"
                               "}");
  io_delegate_.SetFileContents("new/p/Data.aidl",
                               "package p;"
                               "parcelable Data {"
                               "  int foo;"
                               "  int bar = 0;"
                               "  @nullable List<Data> list;"
                               "}");
  EXPECT_TRUE(::android::aidl::check_api(options_, io_delegate_));
}

TEST_F(AidlTestCompatibleChanges, NewField2) {
  io_delegate_.SetFileContents("old/p/Data.aidl",
                               "package p;"
                               "parcelable Data {"
                               "}");
  io_delegate_.SetFileContents("new/p/Data.aidl",
                               "package p;"
                               "parcelable Data {"
                               "  int foo = 0;"
                               "}");
  EXPECT_TRUE(::android::aidl::check_api(options_, io_delegate_));
}

TEST_F(AidlTestCompatibleChanges, NewEnumerator) {
  io_delegate_.SetFileContents("old/p/Enum.aidl",
                               "package p;"
                               "enum Enum {"
                               "  FOO = 1,"
                               "}");
  io_delegate_.SetFileContents("new/p/Enum.aidl",
                               "package p;"
                               "enum Enum {"
                               "  FOO = 1,"
                               "  BAR = 2,"
                               "}");
  EXPECT_TRUE(::android::aidl::check_api(options_, io_delegate_));
}

TEST_F(AidlTestCompatibleChanges, ReorderedEnumerator) {
  io_delegate_.SetFileContents("old/p/Enum.aidl",
                               "package p;"
                               "enum Enum {"
                               "  FOO = 1,"
                               "  BAR = 2,"
                               "}");
  io_delegate_.SetFileContents("new/p/Enum.aidl",
                               "package p;"
                               "enum Enum {"
                               "  BAR = 2,"
                               "  FOO = 1,"
                               "}");
  EXPECT_TRUE(::android::aidl::check_api(options_, io_delegate_));
}

TEST_F(AidlTestCompatibleChanges, NewUnionField) {
  io_delegate_.SetFileContents("old/p/Union.aidl",
                               "package p;"
                               "union Union {"
                               "  String foo;"
                               "}");
  io_delegate_.SetFileContents("new/p/Union.aidl",
                               "package p;"
                               "union Union {"
                               "  String foo;"
                               "  int num;"
                               "}");
  EXPECT_TRUE(::android::aidl::check_api(options_, io_delegate_));
}

TEST_F(AidlTestCompatibleChanges, NewPackage) {
  io_delegate_.SetFileContents("old/p/IFoo.aidl",
                               "package p;"
                               "interface IFoo {"
                               "  void foo(int a);"
                               "}");
  io_delegate_.SetFileContents("old/p/Data.aidl",
                               "package p;"
                               "parcelable Data {"
                               "  int foo;"
                               "}");
  io_delegate_.SetFileContents("new/p/IFoo.aidl",
                               "package p;"
                               "interface IFoo {"
                               "  void foo(int a);"
                               "}");
  io_delegate_.SetFileContents("new/p/Data.aidl",
                               "package p;"
                               "parcelable Data {"
                               "  int foo;"
                               "}");
  io_delegate_.SetFileContents("new/q/IFoo.aidl",
                               "package q;"
                               "interface IFoo {"
                               "  void foo(int a);"
                               "}");
  io_delegate_.SetFileContents("new/q/Data.aidl",
                               "package q;"
                               "parcelable Data {"
                               "  int foo;"
                               "}");
  EXPECT_TRUE(::android::aidl::check_api(options_, io_delegate_));
}

TEST_F(AidlTestCompatibleChanges, ArgNameChange) {
  io_delegate_.SetFileContents("old/p/IFoo.aidl",
                               "package p;"
                               "interface IFoo {"
                               "  void foo(int a);"
                               "}");
  io_delegate_.SetFileContents("new/p/IFoo.aidl",
                               "package p;"
                               "interface IFoo {"
                               "  void foo(int b);"
                               "}");
  EXPECT_TRUE(::android::aidl::check_api(options_, io_delegate_));
}

TEST_F(AidlTestCompatibleChanges, AddedConstValue) {
  io_delegate_.SetFileContents("old/p/I.aidl",
                               "package p; interface I {"
                               "const int A = 1; }");
  io_delegate_.SetFileContents("new/p/I.aidl",
                               "package p ; interface I {"
                               "const int A = 1; const int B = 2;}");
  EXPECT_TRUE(::android::aidl::check_api(options_, io_delegate_));
}

TEST_F(AidlTestCompatibleChanges, ChangedConstValueOrder) {
  io_delegate_.SetFileContents("old/p/I.aidl",
                               "package p; interface I {"
                               "const int A = 1; const int B = 2;}");
  io_delegate_.SetFileContents("new/p/I.aidl",
                               "package p ; interface I {"
                               "const int B = 2; const int A = 1;}");
  EXPECT_TRUE(::android::aidl::check_api(options_, io_delegate_));
}

TEST_F(AidlTestCompatibleChanges, ReorderedAnnatations) {
  io_delegate_.SetFileContents("old/p/Foo.aidl",
                               "package p;"
                               "@JavaPassthrough(annotation=\"Alice\")"
                               "@JavaPassthrough(annotation=\"Bob\")"
                               "parcelable Foo {}");
  io_delegate_.SetFileContents("new/p/Foo.aidl",
                               "package p;"
                               "@JavaPassthrough(annotation=\"Bob\")"
                               "@JavaPassthrough(annotation=\"Alice\")"
                               "parcelable Foo {}");
  EXPECT_TRUE(::android::aidl::check_api(options_, io_delegate_));
}

TEST_F(AidlTestCompatibleChanges, OkayToDeprecate) {
  io_delegate_.SetFileContents("old/p/Foo.aidl",
                               "package p;"
                               "parcelable Foo {}");
  io_delegate_.SetFileContents("new/p/Foo.aidl",
                               "package p;"
                               "@JavaPassthrough(annotation=\"@Deprecated\")"
                               "parcelable Foo {}");
  EXPECT_TRUE(::android::aidl::check_api(options_, io_delegate_));
}

TEST_F(AidlTestCompatibleChanges, NewFieldOfNewType) {
  io_delegate_.SetFileContents("old/p/Data.aidl",
                               "package p;"
                               "parcelable Data {"
                               "  int num;"
                               "}");
  io_delegate_.SetFileContents(
      "new/p/Data.aidl",
      "package p;"
      "parcelable Data {"
      "  int num;"
      "  p.Enum e;"  // this is considered as valid since 0(enum default) is valid for "Enum" type
      "}");
  io_delegate_.SetFileContents("new/p/Enum.aidl",
                               "package p;"
                               "enum Enum {"
                               "  FOO = 0,"
                               "  BAR = 1,"
                               "}");
  EXPECT_TRUE(::android::aidl::check_api(options_, io_delegate_));
}

TEST_F(AidlTestCompatibleChanges, CompatibleExplicitDefaults) {
  io_delegate_.SetFileContents("old/p/Data.aidl",
                               "package p;\n"
                               "parcelable Data {\n"
                               "  p.Enum e;\n"
                               "}");
  io_delegate_.SetFileContents("old/p/Enum.aidl",
                               "package p;\n"
                               "enum Enum {\n"
                               "  FOO = 0,\n"
                               "  BAR = 1,\n"
                               "}");
  io_delegate_.SetFileContents("new/p/Data.aidl",
                               "package p;\n"
                               "parcelable Data {\n"
                               "  p.Enum e = p.Enum.FOO;\n"
                               "}");
  io_delegate_.SetFileContents("new/p/Enum.aidl",
                               "package p;\n"
                               "enum Enum {\n"
                               "  FOO = 0,\n"
                               "  BAR = 1,\n"
                               "}");
  EXPECT_TRUE(::android::aidl::check_api(options_, io_delegate_));
}

TEST_F(AidlTestCompatibleChanges, NewNestedTypes) {
  io_delegate_.SetFileContents("old/p/Data.aidl",
                               "package p;\n"
                               "parcelable Data {\n"
                               "  int n;\n"
                               "}");
  io_delegate_.SetFileContents("new/p/Data.aidl",
                               "package p;\n"
                               "parcelable Data {\n"
                               "  enum NewEnum { N = 3 }\n"
                               "  int n;\n"
                               "  NewEnum e = NewEnum.N;\n"
                               "}");
  EXPECT_TRUE(::android::aidl::check_api(options_, io_delegate_));
}

TEST_F(AidlTestCompatibleChanges, NewJavaSuppressLint) {
  io_delegate_.SetFileContents("old/p/IFoo.aidl",
                               "package p;"
                               "interface IFoo {"
                               "  void foo(int a);"
                               "}");
  io_delegate_.SetFileContents("new/p/IFoo.aidl",
                               "package p;"
                               "@JavaSuppressLint({\"NewApi\"})"
                               "interface IFoo {"
                               "  void foo(int a);"
                               "}");
  EXPECT_TRUE(::android::aidl::check_api(options_, io_delegate_));
}

class AidlTestIncompatibleChanges : public AidlTest {
 protected:
  Options options_ = Options::From("aidl --checkapi old new");
};

TEST_F(AidlTestIncompatibleChanges, RemovedType) {
  const string expected_stderr = "ERROR: old/p/IFoo.aidl:1.11-20: Removed type: p.IFoo\n";
  io_delegate_.SetFileContents("old/p/IFoo.aidl",
                               "package p;"
                               "interface IFoo {"
                               "  void foo(in String[] str);"
                               "  void bar(@utf8InCpp String str);"
                               "}");
  CaptureStderr();
  EXPECT_FALSE(::android::aidl::check_api(options_, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_F(AidlTestIncompatibleChanges, RemovedMethod) {
  const string expected_stderr =
      "ERROR: old/p/IFoo.aidl:1.61-65: Removed or changed method: p.IFoo.bar(String)\n";
  io_delegate_.SetFileContents("old/p/IFoo.aidl",
                               "package p;"
                               "interface IFoo {"
                               "  void foo(in String[] str);"
                               "  void bar(@utf8InCpp String str);"
                               "}");
  io_delegate_.SetFileContents("new/p/IFoo.aidl",
                               "package p;"
                               "interface IFoo {"
                               "  void foo(in String[] str);"
                               "}");
  CaptureStderr();
  EXPECT_FALSE(::android::aidl::check_api(options_, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_F(AidlTestIncompatibleChanges, UntypedListInInterface) {
  const string expected_stderr =
      "ERROR: new/p/IFoo.aidl:1.61-65: "
      "Encountered an untyped List or Map. The use of untyped List/Map is "
      "prohibited because it is not guaranteed that the objects in the list are recognizable in "
      "the receiving side. Consider switching to an array or a generic List/Map.\n"
      "ERROR: new/p/IFoo.aidl: Failed to read.\n";
  io_delegate_.SetFileContents("old/p/IFoo.aidl",
                               "package p;"
                               "interface IFoo {"
                               "  void foo(in String[] str);"
                               "}");
  io_delegate_.SetFileContents("new/p/IFoo.aidl",
                               "package p;"
                               "interface IFoo {"
                               "  void foo(in String[] str);"
                               "  void bar(in List arg);"
                               "}");
  CaptureStderr();
  EXPECT_FALSE(::android::aidl::check_api(options_, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_F(AidlTestCompatibleChanges, UntypedListInParcelable) {
  const string expected_stderr =
      "ERROR: new/p/Data.aidl:1.54-59: "
      "Encountered an untyped List or Map. The use of untyped List/Map is "
      "prohibited because it is not guaranteed that the objects in the list are recognizable in "
      "the receiving side. Consider switching to an array or a generic List/Map.\n"
      "ERROR: new/p/Data.aidl: Failed to read.\n";
  io_delegate_.SetFileContents("old/p/Data.aidl",
                               "package p;"
                               "parcelable Data {"
                               "  int foo;"
                               "}");
  io_delegate_.SetFileContents("new/p/Data.aidl",
                               "package p;"
                               "parcelable Data {"
                               "  int foo;"
                               "  @nullable List list;"
                               "}");
  CaptureStderr();
  EXPECT_FALSE(::android::aidl::check_api(options_, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_F(AidlTestIncompatibleChanges, RemovedField) {
  const string expected_stderr =
      "ERROR: new/p/Data.aidl:1.21-26: Number of fields in p.Data is reduced from 2 to 1.\n";
  io_delegate_.SetFileContents("old/p/Data.aidl",
                               "package p;"
                               "parcelable Data {"
                               "  int foo;"
                               "  int bar;"
                               "}");
  io_delegate_.SetFileContents("new/p/Data.aidl",
                               "package p;"
                               "parcelable Data {"
                               "  int foo;"
                               "}");
  CaptureStderr();
  EXPECT_FALSE(::android::aidl::check_api(options_, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_F(AidlTestIncompatibleChanges, NewFieldWithNoDefault) {
  const string expected_stderr =
      "ERROR: new/p/Data.aidl:1.46-50: Field 'str' does not have a useful default in some "
      "backends. Please either provide a default value for this field or mark the field as "
      "@nullable. This value or a null value will be used automatically when an old version of "
      "this parcelable is sent to a process which understands a new version of this parcelable. In "
      "order to make sure your code continues to be backwards compatible, make sure the default or "
      "null value does not cause a semantic change to this parcelable.\n";
  io_delegate_.SetFileContents("old/p/Data.aidl",
                               "package p;"
                               "parcelable Data {"
                               "  int num;"
                               "}");
  io_delegate_.SetFileContents("new/p/Data.aidl",
                               "package p;"
                               "parcelable Data {"
                               "  int num;"
                               "  String str;"
                               "}");
  CaptureStderr();
  EXPECT_FALSE(::android::aidl::check_api(options_, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_F(AidlTestIncompatibleChanges, NewFieldWithNonZeroEnum) {
  const string expected_stderr =
      "ERROR: new/p/Data.aidl:1.46-48: Field 'e' of enum 'Enum' can't be initialized as '0'. "
      "Please make sure 'Enum' has '0' as a valid value.\n";
  io_delegate_.SetFileContents("old/p/Data.aidl",
                               "package p;"
                               "parcelable Data {"
                               "  int num;"
                               "}");
  io_delegate_.SetFileContents("new/p/Data.aidl",
                               "package p;"
                               "parcelable Data {"
                               "  int num;"
                               "  p.Enum e;"
                               "}");
  io_delegate_.SetFileContents("new/p/Enum.aidl",
                               "package p;"
                               "enum Enum {"
                               "  FOO = 1,"
                               "  BAR = 2,"
                               "}");
  CaptureStderr();
  EXPECT_FALSE(::android::aidl::check_api(options_, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_F(AidlTestIncompatibleChanges, RemovedEnumerator) {
  const string expected_stderr =
      "ERROR: new/p/Enum.aidl:1.15-20: Removed enumerator from p.Enum: FOO\n";
  io_delegate_.SetFileContents("old/p/Enum.aidl",
                               "package p;"
                               "enum Enum {"
                               "  FOO = 1,"
                               "  BAR = 2,"
                               "}");
  io_delegate_.SetFileContents("new/p/Enum.aidl",
                               "package p;"
                               "enum Enum {"
                               "  BAR = 2,"
                               "}");
  CaptureStderr();
  EXPECT_FALSE(::android::aidl::check_api(options_, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_F(AidlTestIncompatibleChanges, RemovedUnionField) {
  const string expected_stderr =
      "ERROR: new/p/Union.aidl:1.16-22: Number of fields in p.Union is reduced from 2 to 1.\n";
  io_delegate_.SetFileContents("old/p/Union.aidl",
                               "package p;"
                               "union Union {"
                               "  String str;"
                               "  int num;"
                               "}");
  io_delegate_.SetFileContents("new/p/Union.aidl",
                               "package p;"
                               "union Union {"
                               "  String str;"
                               "}");
  CaptureStderr();
  EXPECT_FALSE(::android::aidl::check_api(options_, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_F(AidlTestIncompatibleChanges, RenamedMethod) {
  const string expected_stderr =
      "ERROR: old/p/IFoo.aidl:1.61-65: Removed or changed method: p.IFoo.bar(String)\n";
  io_delegate_.SetFileContents("old/p/IFoo.aidl",
                               "package p;"
                               "interface IFoo {"
                               "  void foo(in String[] str);"
                               "  void bar(@utf8InCpp String str);"
                               "}");
  io_delegate_.SetFileContents("new/p/IFoo.aidl",
                               "package p;"
                               "interface IFoo {"
                               "  void foo(in String[] str);"
                               "  void bar2(@utf8InCpp String str);"
                               "}");
  CaptureStderr();
  EXPECT_FALSE(::android::aidl::check_api(options_, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_F(AidlTestIncompatibleChanges, RenamedType) {
  const string expected_stderr = "ERROR: old/p/IFoo.aidl:1.11-20: Removed type: p.IFoo\n";
  io_delegate_.SetFileContents("old/p/IFoo.aidl",
                               "package p;"
                               "interface IFoo {"
                               "  void foo(in String[] str);"
                               "  void bar(@utf8InCpp String str);"
                               "}");
  io_delegate_.SetFileContents("new/p/IFoo2.aidl",
                               "package p;"
                               "interface IFoo2 {"
                               "  void foo(in String[] str);"
                               "  void bar(@utf8InCpp String str);"
                               "}");
  CaptureStderr();
  EXPECT_FALSE(::android::aidl::check_api(options_, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_F(AidlTestIncompatibleChanges, ChangedEnumerator) {
  const string expected_stderr =
      "ERROR: new/p/Enum.aidl:1.15-20: Changed enumerator value: p.Enum::FOO from 1 to 3.\n";
  io_delegate_.SetFileContents("old/p/Enum.aidl",
                               "package p;"
                               "enum Enum {"
                               "  FOO = 1,"
                               "  BAR = 2,"
                               "}");
  io_delegate_.SetFileContents("new/p/Enum.aidl",
                               "package p;"
                               "enum Enum {"
                               "  FOO = 3,"
                               "  BAR = 2,"
                               "}");
  CaptureStderr();
  EXPECT_FALSE(::android::aidl::check_api(options_, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_F(AidlTestIncompatibleChanges, ReorderedMethod) {
  const string expected_stderr =
      "ERROR: new/p/IFoo.aidl:1.67-71: Transaction ID changed: p.IFoo.foo(String[]) is changed "
      "from 0 to 1.\n"
      "ERROR: new/p/IFoo.aidl:1.33-37: Transaction ID changed: p.IFoo.bar(String) is changed from "
      "1 to 0.\n";
  io_delegate_.SetFileContents("old/p/IFoo.aidl",
                               "package p;"
                               "interface IFoo {"
                               "  void foo(in String[] str);"
                               "  void bar(@utf8InCpp String str);"
                               "}");
  io_delegate_.SetFileContents("new/p/IFoo.aidl",
                               "package p;"
                               "interface IFoo {"
                               "  void bar(@utf8InCpp String str);"
                               "  void foo(in String[] str);"
                               "}");
  CaptureStderr();
  EXPECT_FALSE(::android::aidl::check_api(options_, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_F(AidlTestIncompatibleChanges, ReorderedField) {
  const string expected_stderr =
      "ERROR: new/p/Data.aidl:1.33-37: Reordered bar from 1 to 0.\n"
      "ERROR: new/p/Data.aidl:1.43-47: Reordered foo from 0 to 1.\n";
  io_delegate_.SetFileContents("old/p/Data.aidl",
                               "package p;"
                               "parcelable Data {"
                               "  int foo;"
                               "  int bar;"
                               "}");
  io_delegate_.SetFileContents("new/p/Data.aidl",
                               "package p;"
                               "parcelable Data {"
                               "  int bar;"
                               "  int foo;"
                               "}");
  CaptureStderr();
  EXPECT_FALSE(::android::aidl::check_api(options_, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_F(AidlTestIncompatibleChanges, ChangedDirectionSpecifier) {
  const string expected_stderr = "ERROR: new/p/IFoo.aidl:1.33-37: Direction changed: in to out.\n";
  io_delegate_.SetFileContents("old/p/IFoo.aidl",
                               "package p;"
                               "interface IFoo {"
                               "  void foo(in String[] str);"
                               "  void bar(@utf8InCpp String str);"
                               "}");
  io_delegate_.SetFileContents("new/p/IFoo.aidl",
                               "package p;"
                               "interface IFoo {"
                               "  void foo(out String[] str);"
                               "  void bar(@utf8InCpp String str);"
                               "}");
  CaptureStderr();
  EXPECT_FALSE(::android::aidl::check_api(options_, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_F(AidlTestIncompatibleChanges, AddedAnnotation) {
  const string expected_stderr =
      "ERROR: new/p/IFoo.aidl:1.51-58: Changed annotations: (empty) to @utf8InCpp\n";
  io_delegate_.SetFileContents("old/p/IFoo.aidl",
                               "package p;"
                               "interface IFoo {"
                               "  void foo(in String[] str);"
                               "  void bar(@utf8InCpp String str);"
                               "}");
  io_delegate_.SetFileContents("new/p/IFoo.aidl",
                               "package p;"
                               "interface IFoo {"
                               "  void foo(in @utf8InCpp String[] str);"
                               "  void bar(@utf8InCpp String str);"
                               "}");
  CaptureStderr();
  EXPECT_FALSE(::android::aidl::check_api(options_, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_F(AidlTestIncompatibleChanges, RemovedAnnotation) {
  const string expected_stderr =
      "ERROR: new/p/IFoo.aidl:1.66-72: Changed annotations: @utf8InCpp to (empty)\n";
  io_delegate_.SetFileContents("old/p/IFoo.aidl",
                               "package p;"
                               "interface IFoo {"
                               "  void foo(in String[] str);"
                               "  void bar(@utf8InCpp String str);"
                               "}");
  io_delegate_.SetFileContents("new/p/IFoo.aidl",
                               "package p;"
                               "interface IFoo {"
                               "  void foo(in String[] str);"
                               "  void bar(String str);"
                               "}");
  CaptureStderr();
  EXPECT_FALSE(::android::aidl::check_api(options_, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_F(AidlTestIncompatibleChanges, ChangedFixedSizeArraySize) {
  const string expected_stderr =
      "ERROR: new/p/Data.aidl:1.28-33: Type changed: int[8] to int[9].\n";
  io_delegate_.SetFileContents("old/p/Data.aidl",
                               "package p;"
                               "parcelable Data {"
                               "  int[8] bar;"
                               "}");
  io_delegate_.SetFileContents("new/p/Data.aidl",
                               "package p;"
                               "parcelable Data {"
                               "  int[9] bar;"
                               "}");
  CaptureStderr();
  EXPECT_FALSE(::android::aidl::check_api(options_, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_F(AidlTestIncompatibleChanges, ChangedAnnatationParams) {
  // this is also the test for backing type remaining the same
  const string expected_stderr =
      "ERROR: new/p/Foo.aidl:1.36-40: Changed annotations: @Backing(type=\"int\") "
      "to @Backing(type=\"long\")\n";
  io_delegate_.SetFileContents("old/p/Foo.aidl",
                               "package p;"
                               "@Backing(type=\"int\")"
                               "enum Foo {A}");
  io_delegate_.SetFileContents("new/p/Foo.aidl",
                               "package p;"
                               "@Backing(type=\"long\")"
                               "enum Foo {A}");

  CaptureStderr();
  EXPECT_FALSE(::android::aidl::check_api(options_, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_F(AidlTestIncompatibleChanges, AddedParcelableAnnotation) {
  const string expected_stderr =
      "ERROR: new/p/Foo.aidl:1.32-36: Changed annotations: (empty) to @FixedSize\n";
  io_delegate_.SetFileContents("old/p/Foo.aidl",
                               "package p;"
                               "parcelable Foo {"
                               "  int A;"
                               "}");
  io_delegate_.SetFileContents("new/p/Foo.aidl",
                               "package p;"
                               "@FixedSize parcelable Foo {"
                               "  int A;"
                               "}");
  CaptureStderr();
  EXPECT_FALSE(::android::aidl::check_api(options_, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_F(AidlTestIncompatibleChanges, RemovedParcelableAnnotation) {
  const string expected_stderr =
      "ERROR: new/p/Foo.aidl:1.21-25: Changed annotations: @FixedSize to (empty)\n";
  io_delegate_.SetFileContents("old/p/Foo.aidl",
                               "package p;"
                               "@FixedSize parcelable Foo {"
                               "  int A;"
                               "}");
  io_delegate_.SetFileContents("new/p/Foo.aidl",
                               "package p;"
                               "parcelable Foo {"
                               "  int A;"
                               "}");
  CaptureStderr();
  EXPECT_FALSE(::android::aidl::check_api(options_, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_F(AidlTestIncompatibleChanges, RemovedPackage) {
  const string expected_stderr = "ERROR: old/q/IFoo.aidl:1.11-21: Removed type: q.IFoo\n";
  io_delegate_.SetFileContents("old/p/IFoo.aidl", "package p; interface IFoo{}");
  io_delegate_.SetFileContents("old/q/IFoo.aidl", "package q; interface IFoo{}");
  io_delegate_.SetFileContents("new/p/IFoo.aidl", "package p; interface IFoo{}");
  CaptureStderr();
  EXPECT_FALSE(::android::aidl::check_api(options_, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_F(AidlTestIncompatibleChanges, ChangedDefaultValue) {
  const string expected_stderr = "ERROR: new/p/D.aidl:1.30-32: Changed default value: 1 to 2.\n";
  io_delegate_.SetFileContents("old/p/D.aidl", "package p; parcelable D { int a = 1; }");
  io_delegate_.SetFileContents("new/p/D.aidl", "package p; parcelable D { int a = 2; }");
  CaptureStderr();
  EXPECT_FALSE(::android::aidl::check_api(options_, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_F(AidlTestIncompatibleChanges, RemovedConstValue) {
  const string expected_stderr =
      "ERROR: old/p/I.aidl:1.51-53: Removed constant declaration: p.I.B\n";
  io_delegate_.SetFileContents("old/p/I.aidl",
                               "package p; interface I {"
                               "const int A = 1; const int B = 2;}");
  io_delegate_.SetFileContents("new/p/I.aidl", "package p; interface I { const int A = 1; }");
  CaptureStderr();
  EXPECT_FALSE(::android::aidl::check_api(options_, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_F(AidlTestIncompatibleChanges, ChangedConstValue) {
  const string expected_stderr =
      "ERROR: new/p/I.aidl:1.11-21: Changed constant value: p.I.A from 1 to 2.\n";
  io_delegate_.SetFileContents("old/p/I.aidl", "package p; interface I { const int A = 1; }");
  io_delegate_.SetFileContents("new/p/I.aidl", "package p; interface I { const int A = 2; }");
  CaptureStderr();
  EXPECT_FALSE(::android::aidl::check_api(options_, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_F(AidlTestIncompatibleChanges, FixedSizeAddedField) {
  const string expected_stderr =
      "ERROR: new/p/Foo.aidl:1.33-37: Number of fields in p.Foo is changed from 1 to 2. "
      "This is an incompatible change for FixedSize types.\n";
  io_delegate_.SetFileContents("old/p/Foo.aidl",
                               "package p; @FixedSize parcelable Foo { int A = 1; }");
  io_delegate_.SetFileContents("new/p/Foo.aidl",
                               "package p; @FixedSize parcelable Foo { int A = 1; int B = 2; }");
  CaptureStderr();
  EXPECT_FALSE(::android::aidl::check_api(options_, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_F(AidlTestIncompatibleChanges, UidRangeParcelAddedField) {
  const string expected_stderr =
      "ERROR: new/android/net/UidRangeParcel.aidl:1.32-47: Number of fields in "
      "android.net.UidRangeParcel is changed from 1 to 2. "
      "But it is forbidden because of legacy support.\n";
  io_delegate_.SetFileContents("old/android/net/UidRangeParcel.aidl",
                               "package android.net; parcelable UidRangeParcel { int A = 1; }");
  io_delegate_.SetFileContents(
      "new/android/net/UidRangeParcel.aidl",
      "package android.net; parcelable UidRangeParcel { int A = 1; int B = 2; }");
  CaptureStderr();
  EXPECT_FALSE(::android::aidl::check_api(options_, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_F(AidlTestIncompatibleChanges, FixedSizeRemovedField) {
  const string expected_stderr =
      "ERROR: new/p/Foo.aidl:1.33-37: Number of fields in p.Foo is reduced from 2 to 1.\n";
  io_delegate_.SetFileContents("old/p/Foo.aidl",
                               "package p; @FixedSize parcelable Foo { int A = 1; int B = 1; }");
  io_delegate_.SetFileContents("new/p/Foo.aidl",
                               "package p; @FixedSize parcelable Foo { int A = 1; }");
  CaptureStderr();
  EXPECT_FALSE(::android::aidl::check_api(options_, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_F(AidlTestIncompatibleChanges, IncompatibleChangesInNestedType) {
  const string expected_stderr =
      "ERROR: new/p/Foo.aidl:1.33-37: Number of fields in p.Foo is reduced from 2 to 1.\n";
  io_delegate_.SetFileContents("old/p/Foo.aidl",
                               "package p;\n"
                               "parcelable Foo {\n"
                               "  interface IBar {\n"
                               "    void foo();"
                               "  }\n"
                               "}");
  io_delegate_.SetFileContents("new/p/Foo.aidl",
                               "package p;\n"
                               "parcelable Foo {\n"
                               "  interface IBar {\n"
                               "    void foo(int n);"  // incompatible: signature changed
                               "  }\n"
                               "}");
  CaptureStderr();
  EXPECT_FALSE(::android::aidl::check_api(options_, io_delegate_));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr("Removed or changed method: p.Foo.IBar.foo()"));
}

TEST_P(AidlTest, RejectNonFixedSizeFromFixedSize) {
  const string expected_stderr =
      "ERROR: Foo.aidl:2.8-10: The @FixedSize parcelable 'Foo' has a non-fixed size field named "
      "a.\n"
      "ERROR: Foo.aidl:3.6-8: The @FixedSize parcelable 'Foo' has a non-fixed size field named b.\n"
      "ERROR: Foo.aidl:4.9-11: The @FixedSize parcelable 'Foo' has a non-fixed size field named "
      "c.\n"
      "ERROR: Foo.aidl:5.23-25: The @FixedSize parcelable 'Foo' has a non-fixed size field named "
      "d.\n"
      "ERROR: Foo.aidl:6.10-12: The @FixedSize parcelable 'Foo' has a non-fixed size field named "
      "e.\n"
      "ERROR: Foo.aidl:7.15-17: The @FixedSize parcelable 'Foo' has a non-fixed size field named "
      "f.\n"
      "ERROR: Foo.aidl:9.23-33: The @FixedSize parcelable 'Foo' has a non-fixed size field named "
      "nullable1.\n"
      "ERROR: Foo.aidl:10.34-44: The @FixedSize parcelable 'Foo' has a non-fixed size field named "
      "nullable2.\n";

  io_delegate_.SetFileContents("Foo.aidl",
                               "@FixedSize parcelable Foo {\n"
                               "  int[] a;\n"
                               "  Bar b;\n"
                               "  String c;\n"
                               "  ParcelFileDescriptor d;\n"
                               "  IBinder e;\n"
                               "  List<String> f;\n"
                               "  int isFixedSize;\n"
                               "  @nullable OtherFixed nullable1;\n"
                               "  @nullable(heap=true) OtherFixed nullable2;\n"
                               "  float[16] floats;\n"
                               "}");
  io_delegate_.SetFileContents("Bar.aidl", "parcelable Bar { int a; }");
  io_delegate_.SetFileContents("OtherFixed.aidl", "@FixedSize parcelable OtherFixed { int a; }");
  Options options = Options::From("aidl Foo.aidl -I . --lang=" + to_string(GetLanguage()));

  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_P(AidlTest, RejectNonFixedSizeFromFixedSize_Union) {
  const string expected_stderr =
      "ERROR: Foo.aidl:2.8-10: The @FixedSize parcelable 'Foo' has a non-fixed size field named "
      "a.\n"
      "ERROR: Foo.aidl:3.6-8: The @FixedSize parcelable 'Foo' has a non-fixed size field named b.\n"
      "ERROR: Foo.aidl:4.9-11: The @FixedSize parcelable 'Foo' has a non-fixed size field named "
      "c.\n"
      "ERROR: Foo.aidl:5.23-25: The @FixedSize parcelable 'Foo' has a non-fixed size field named "
      "d.\n"
      "ERROR: Foo.aidl:6.10-12: The @FixedSize parcelable 'Foo' has a non-fixed size field named "
      "e.\n"
      "ERROR: Foo.aidl:7.15-17: The @FixedSize parcelable 'Foo' has a non-fixed size field named "
      "f.\n"
      "ERROR: Foo.aidl:9.23-33: The @FixedSize parcelable 'Foo' has a non-fixed size field named "
      "nullable1.\n"
      "ERROR: Foo.aidl:10.34-44: The @FixedSize parcelable 'Foo' has a non-fixed size field named "
      "nullable2.\n";

  io_delegate_.SetFileContents("Foo.aidl",
                               "@FixedSize union Foo {\n"
                               "  int[] a = {};\n"
                               "  Bar b;\n"
                               "  String c;\n"
                               "  ParcelFileDescriptor d;\n"
                               "  IBinder e;\n"
                               "  List<String> f;\n"
                               "  int isFixedSize;\n"
                               "  @nullable OtherFixed nullable1;\n"
                               "  @nullable(heap=true) OtherFixed nullable2;\n"
                               "  float[16] floats;\n"
                               "}");
  io_delegate_.SetFileContents("Bar.aidl", "parcelable Bar { int a; }");
  io_delegate_.SetFileContents("OtherFixed.aidl", "@FixedSize parcelable OtherFixed { int a; }");
  Options options = Options::From("aidl Foo.aidl -I . --lang=" + to_string(GetLanguage()));

  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_P(AidlTest, AcceptFixedSizeFromFixedSize) {
  const string expected_stderr = "";

  io_delegate_.SetFileContents("Foo.aidl", "@FixedSize parcelable Foo { int a; Bar b; }");
  io_delegate_.SetFileContents("Bar.aidl", "@FixedSize parcelable Bar { Val c; }");
  io_delegate_.SetFileContents("Val.aidl", "enum Val { A, B, }");
  Options options = Options::From("aidl Foo.aidl -I . --lang=" + to_string(GetLanguage()));

  CaptureStderr();
  EXPECT_TRUE(compile_aidl(options, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_F(AidlTest, RejectAmbiguousImports) {
  const string expected_stderr =
      "ERROR: p/IFoo.aidl: Duplicate files found for q.IBar from:\n"
      "dir1/q/IBar.aidl\n"
      "dir2/q/IBar.aidl\n";
  Options options = Options::From("aidl --lang=java -o out -I . -I dir1 -I dir2 p/IFoo.aidl");
  io_delegate_.SetFileContents("p/IFoo.aidl", "package p; import q.IBar; interface IFoo{}");
  io_delegate_.SetFileContents("dir1/q/IBar.aidl", "package q; interface IBar{}");
  io_delegate_.SetFileContents("dir2/q/IBar.aidl", "package q; interface IBar{}");

  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_F(AidlTest, HandleManualIdAssignments) {
  const string expected_stderr =
      "ERROR: new/p/IFoo.aidl:1.32-36: Transaction ID changed: p.IFoo.foo() is changed from 10 to "
      "11.\n";
  Options options = Options::From("aidl --checkapi old new");
  io_delegate_.SetFileContents("old/p/IFoo.aidl", "package p; interface IFoo{ void foo() = 10;}");
  io_delegate_.SetFileContents("new/p/IFoo.aidl", "package p; interface IFoo{ void foo() = 10;}");

  EXPECT_TRUE(::android::aidl::check_api(options, io_delegate_));

  io_delegate_.SetFileContents("new/p/IFoo.aidl", "package p; interface IFoo{ void foo() = 11;}");
  CaptureStderr();
  EXPECT_FALSE(::android::aidl::check_api(options, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_P(AidlTest, ParcelFileDescriptorIsBuiltinType) {
  Options options =
      Options::From("aidl -I . --lang=" + to_string(GetLanguage()) + " -h out -o out p/IFoo.aidl");

  // use without import
  io_delegate_.SetFileContents("p/IFoo.aidl",
                               "package p; interface IFoo{ void foo(in ParcelFileDescriptor fd);}");
  EXPECT_TRUE(compile_aidl(options, io_delegate_));

  // capture output files
  map<string, string> outputs = io_delegate_.OutputFiles();

  // use without import but with full name
  io_delegate_.SetFileContents(
      "p/IFoo.aidl",
      "package p; interface IFoo{ void foo(in android.os.ParcelFileDescriptor fd);}");
  EXPECT_TRUE(compile_aidl(options, io_delegate_));
  // output files should be the same
  EXPECT_EQ(outputs, io_delegate_.OutputFiles());

  // use with import (as before)
  io_delegate_.SetFileContents("p/IFoo.aidl",
                               "package p;"
                               "import android.os.ParcelFileDescriptor;"
                               "interface IFoo{"
                               "  void foo(in ParcelFileDescriptor fd);"
                               "}");
  EXPECT_TRUE(compile_aidl(options, io_delegate_));
  // output files should be the same
  EXPECT_EQ(outputs, io_delegate_.OutputFiles());
}

TEST_P(AidlTest, RejectsOutputParcelFileDescriptor) {
  Options options = Options::From("aidl p/IFoo.aidl -I . --lang=" + to_string(GetLanguage()));
  CaptureStderr();
  io_delegate_.SetFileContents("p/IFoo.aidl",
                               "package p;"
                               "interface IFoo{"
                               "  void foo(out ParcelFileDescriptor fd);"
                               "}");
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr("can't be an out parameter"));
}

TEST_P(AidlTest, RejectsArgumentDirectionNotSpecified) {
  Options options = Options::From("aidl p/IFoo.aidl -I . --lang=" + to_string(GetLanguage()));
  CaptureStderr();
  io_delegate_.SetFileContents("p/IFoo.aidl",
                               "package p;"
                               "interface IFoo{"
                               "  void foo(ParcelFileDescriptor fd);"
                               "}");
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_THAT(GetCapturedStderr(),
              HasSubstr("ParcelFileDescriptor can be an in or inout parameter."));
}

TEST_F(AidlTest, ManualIds) {
  Options options = Options::From("aidl --lang=java -I . -o out IFoo.aidl");
  io_delegate_.SetFileContents("IFoo.aidl",
                               "interface IFoo {\n"
                               "  void foo() = 0;\n"
                               "  void bar() = 1;\n"
                               "}");
  EXPECT_TRUE(compile_aidl(options, io_delegate_));
}

TEST_F(AidlTest, ManualIdsWithMetaTransactions) {
  Options options = Options::From("aidl --lang=java -I . --version 10 -o out IFoo.aidl");
  io_delegate_.SetFileContents("IFoo.aidl",
                               "interface IFoo {\n"
                               "  void foo() = 0;\n"
                               "  void bar() = 1;\n"
                               "}");
  EXPECT_TRUE(compile_aidl(options, io_delegate_));
}

TEST_F(AidlTest, FailOnDuplicatedIds) {
  const string expected_stderr =
      "ERROR: IFoo.aidl:3.7-11: Found duplicate method id (3) for method bar\n";
  Options options = Options::From("aidl --lang=java -I . --version 10 -o out IFoo.aidl");
  io_delegate_.SetFileContents("IFoo.aidl",
                               "interface IFoo {\n"
                               "  void foo() = 3;\n"
                               "  void bar() = 3;\n"
                               "}");
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_F(AidlTest, FailOnOutOfRangeIds) {
  // 16777115 is kLastMetaMethodId + 1
  const string expected_stderr =
      "ERROR: IFoo.aidl:3.7-11: Found out of bounds id (16777115) for method bar. "
      "Value for id must be between 0 and 16777114 inclusive.\n";
  Options options = Options::From("aidl --lang=java -I . --version 10 -o out IFoo.aidl");
  io_delegate_.SetFileContents("IFoo.aidl",
                               "interface IFoo {\n"
                               "  void foo() = 3;\n"
                               "  void bar() = 16777115;\n"
                               "}");
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_F(AidlTest, FailOnPartiallyAssignedIds) {
  const string expected_stderr =
      "ERROR: IFoo.aidl:3.7-11: You must either assign id's to all methods or to none of them.\n";
  Options options = Options::From("aidl --lang=java -I . --version 10 -o out IFoo.aidl");
  io_delegate_.SetFileContents("IFoo.aidl",
                               "interface IFoo {\n"
                               "  void foo() = 3;\n"
                               "  void bar();\n"
                               "}");
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_F(AidlTest, AssignedIds) {
  CaptureStderr();
  EXPECT_NE(nullptr, Parse("IFoo.aidl",
                           "interface IFoo {\n"
                           "  void foo();\n"
                           "  void bar();\n"
                           "  interface INested {\n"
                           "    void foo();\n"
                           "    void bar();\n"
                           "  }\n"
                           "}",
                           typenames_, Options::Language::JAVA));
  EXPECT_EQ("", GetCapturedStderr());
  auto foo = typenames_.ResolveTypename("IFoo").defined_type;
  ASSERT_EQ(2u, foo->GetMethods().size());
  EXPECT_EQ(0, foo->GetMethods()[0]->GetId());
  EXPECT_EQ(1, foo->GetMethods()[1]->GetId());
  auto nested = typenames_.ResolveTypename("IFoo.INested").defined_type;
  ASSERT_EQ(2u, nested->GetMethods().size());
  EXPECT_EQ(0, nested->GetMethods()[0]->GetId());
  EXPECT_EQ(1, nested->GetMethods()[1]->GetId());
}

TEST_F(AidlTest, AllowDuplicatedImportPaths) {
  Options options = Options::From("aidl --lang=java -I . -I dir -I dir IFoo.aidl");
  io_delegate_.SetFileContents("dir/IBar.aidl", "interface IBar{}");
  io_delegate_.SetFileContents("IFoo.aidl", "import IBar; interface IFoo{}");
  EXPECT_TRUE(compile_aidl(options, io_delegate_));
}

TEST_F(AidlTest, UnusedImportDoesNotContributeInclude) {
  io_delegate_.SetFileContents("a/b/IFoo.aidl",
                               "package a.b;\n"
                               "import a.b.IBar;\n"
                               "import a.b.IQux;\n"
                               "interface IFoo { IQux foo(); }\n");
  io_delegate_.SetFileContents("a/b/IBar.aidl", "package a.b; interface IBar { void foo(); }");
  io_delegate_.SetFileContents("a/b/IQux.aidl", "package a.b; interface IQux { void foo(); }");

  Options options = Options::From("aidl --lang=ndk a/b/IFoo.aidl -I . -o out -h out/include");
  EXPECT_TRUE(compile_aidl(options, io_delegate_));

  string output;
  EXPECT_TRUE(io_delegate_.GetWrittenContents("out/include/aidl/a/b/IFoo.h", &output));
  // IBar was imported but wasn't used. include is not expected.
  EXPECT_THAT(output, Not(testing::HasSubstr("#include <aidl/a/b/IBar.h>")));
  // IBar was imported and used. include is expected.
  EXPECT_THAT(output, (testing::HasSubstr("#include <aidl/a/b/IQux.h>")));
}

TEST_F(AidlTest, BasePathAsImportPath) {
  Options options = Options::From("aidl --lang=java -I some -I other some/dir/pkg/name/IFoo.aidl");
  io_delegate_.SetFileContents("some/dir/pkg/name/IFoo.aidl",
      "package pkg.name; interface IFoo { void foo(); }");
  const string expected_stderr =
      "ERROR: some/dir/pkg/name/IFoo.aidl:1.18-28: directory some/dir/ is not found in any of "
      "the import paths:\n - other/\n - some/\n";
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_F(AidlTest, ParseJavaPassthroughAnnotation) {
  io_delegate_.SetFileContents("a/IFoo.aidl", R"--(package a;
    import a.MyEnum;
    @JavaPassthrough(annotation="@com.android.Alice(arg=com.android.Alice.Value.A)")
    @JavaPassthrough(annotation="@com.android.AliceTwo")
    interface IFoo {
        @JavaPassthrough(annotation="@com.android.Bob")
        void foo(@JavaPassthrough(annotation="@com.android.Cat") int x, MyEnum y);
        const @JavaPassthrough(annotation="@com.android.David") int A = 3;
    })--");
  // JavaPassthrough should work with other types as well (e.g. enum)
  io_delegate_.SetFileContents("a/MyEnum.aidl", R"--(package a;
    @JavaPassthrough(annotation="@com.android.Alice(arg=com.android.Alice.Value.A)")
    @JavaPassthrough(annotation="@com.android.AliceTwo")
    @Backing(type="byte")
    enum MyEnum {
      a, b, c
    })--");

  Options java_options = Options::From("aidl -I . --lang=java -o out a/IFoo.aidl a/MyEnum.aidl");
  EXPECT_TRUE(compile_aidl(java_options, io_delegate_));

  string java_out;
  EXPECT_TRUE(io_delegate_.GetWrittenContents("out/a/IFoo.java", &java_out));
  // type-decl-level annotations with newline at the end
  EXPECT_THAT(java_out, testing::HasSubstr("@com.android.Alice(arg=com.android.Alice.Value.A)\n"));
  EXPECT_THAT(java_out, testing::HasSubstr("@com.android.AliceTwo\n"));
  // member-decl-level annotations with newline at the end
  EXPECT_THAT(java_out, testing::HasSubstr("@com.android.Bob\n"));
  EXPECT_THAT(java_out, testing::HasSubstr("@com.android.David\n"));
  // inline annotations with space at the end
  EXPECT_THAT(java_out, testing::HasSubstr("@com.android.Cat "));

  EXPECT_TRUE(io_delegate_.GetWrittenContents("out/a/MyEnum.java", &java_out));
  // type-decl-level annotations with newline at the end
  EXPECT_THAT(java_out, testing::HasSubstr("@com.android.Alice(arg=com.android.Alice.Value.A)\n"));
  EXPECT_THAT(java_out, testing::HasSubstr("@com.android.AliceTwo\n"));

  // Other backends shouldn't be bothered
  Options cpp_options =
      Options::From("aidl -I . --lang=cpp -o out -h out a/IFoo.aidl a/MyEnum.aidl");
  EXPECT_TRUE(compile_aidl(cpp_options, io_delegate_));

  Options ndk_options =
      Options::From("aidl -I . --lang=ndk -o out -h out a/IFoo.aidl a/MyEnum.aidl");
  EXPECT_TRUE(compile_aidl(ndk_options, io_delegate_));

  Options rust_options = Options::From("aidl -I . --lang=rust -o out a/IFoo.aidl a/MyEnum.aidl");
  EXPECT_TRUE(compile_aidl(rust_options, io_delegate_));
}

TEST_F(AidlTest, ParseRustDerive) {
  io_delegate_.SetFileContents("a/Foo.aidl", R"(package a;
    @RustDerive(Clone=true, Copy=false)
    parcelable Foo {
        int a;
    })");

  Options rust_options = Options::From("aidl -I . --lang=rust -o out a/Foo.aidl");
  EXPECT_TRUE(compile_aidl(rust_options, io_delegate_));

  string rust_out;
  EXPECT_TRUE(io_delegate_.GetWrittenContents("out/a/Foo.rs", &rust_out));
  EXPECT_THAT(rust_out, testing::HasSubstr("#[derive(Debug, Clone)]"));

  // Other backends shouldn't be bothered
  Options cpp_options = Options::From("aidl --lang=cpp -I . -o out -h out a/Foo.aidl");
  EXPECT_TRUE(compile_aidl(cpp_options, io_delegate_));

  Options ndk_options = Options::From("aidl --lang=ndk -I . -o out -h out a/Foo.aidl");
  EXPECT_TRUE(compile_aidl(ndk_options, io_delegate_));

  Options java_options = Options::From("aidl --lang=java -I . -o out a/Foo.aidl");
  EXPECT_TRUE(compile_aidl(java_options, io_delegate_));
}

TEST_P(AidlTest, TypesShouldHaveRustDerive) {
  CaptureStderr();
  string code =
      "@RustDerive(PartialEq=true)\n"
      "parcelable Foo {\n"
      "  parcelable Bar {}\n"
      "  Bar bar;\n"
      "}";
  EXPECT_EQ(nullptr, Parse("Foo.aidl", code, typenames_, GetLanguage(), nullptr, {}));
  EXPECT_THAT(
      GetCapturedStderr(),
      testing::HasSubstr("Field bar of type with @RustDerive PartialEq also needs to derive this"));
}

TEST_F(AidlTest, EmptyEnforceAnnotation) {
  io_delegate_.SetFileContents("a/IFoo.aidl", R"(package a;
    interface IFoo {
        @EnforcePermission()
        void Protected();
    })");

  Options options = Options::From("aidl --lang=java -I . -o out a/IFoo.aidl");
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr("Unable to parse @EnforcePermission annotation"));
}

TEST_F(AidlTest, InterfaceEnforceCondition) {
  io_delegate_.SetFileContents("a/IFoo.aidl", R"(package a;
    @EnforcePermission("INTERNET")
    interface IFoo {
        void Protected();
    })");

  Options options = Options::From("aidl --lang=java -I . -o out a/IFoo.aidl");
  EXPECT_TRUE(compile_aidl(options, io_delegate_));
}

TEST_F(AidlTest, EnforceConditionAny) {
  io_delegate_.SetFileContents("a/IFoo.aidl", R"(package a;
    interface IFoo {
        @EnforcePermission(anyOf={"INTERNET", "READ_PHONE_STATE"})
        void Protected();
    })");

  Options options = Options::From("aidl --lang=java -I . -o out a/IFoo.aidl");
  EXPECT_TRUE(compile_aidl(options, io_delegate_));
}

TEST_F(AidlTest, EnforceConditionAll) {
  io_delegate_.SetFileContents("a/IFoo.aidl", R"(package a;
    interface IFoo {
        @EnforcePermission(allOf={"INTERNET", "READ_PHONE_STATE"})
        void Protected();
    })");

  Options options = Options::From("aidl --lang=java -I . -o out a/IFoo.aidl");
  EXPECT_TRUE(compile_aidl(options, io_delegate_));
}

TEST_F(AidlTest, InterfaceAndMethodEnforceCondition) {
  io_delegate_.SetFileContents("a/IFoo.aidl", R"(package a;
    @EnforcePermission("INTERNET")
    interface IFoo {
        @EnforcePermission("SYSTEM_UID")
        void Protected();
    })");

  Options options = Options::From("aidl --lang=java -I . -o out a/IFoo.aidl");
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr("The interface IFoo uses a permission annotation but "
                                             "the method Protected is also annotated"));
}

TEST_F(AidlTest, NoPermissionInterfaceEnforceMethod) {
  io_delegate_.SetFileContents("a/IFoo.aidl", R"(package a;
    @RequiresNoPermission
    interface IFoo {
        @EnforcePermission("INTERNET")
        void Protected();
    })");

  Options options = Options::From("aidl --lang=java -I . -o out a/IFoo.aidl");
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr("The interface IFoo uses a permission annotation but "
                                             "the method Protected is also annotated"));
}

TEST_F(AidlTest, ManualPermissionInterfaceEnforceMethod) {
  io_delegate_.SetFileContents("a/IFoo.aidl", R"(package a;
    @PermissionManuallyEnforced
    interface IFoo {
        @EnforcePermission("INTERNET")
        void Protected();
    })");

  Options options = Options::From("aidl --lang=java -I . -o out a/IFoo.aidl");
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr("The interface IFoo uses a permission annotation but "
                                             "the method Protected is also annotated"));
}

TEST_F(AidlTest, EnforceInterfaceNoPermissionsMethod) {
  io_delegate_.SetFileContents("a/IFoo.aidl", R"(package a;
    @EnforcePermission("INTERNET")
    interface IFoo {
        @RequiresNoPermission
        void Protected();
    })");

  Options options = Options::From("aidl --lang=java -I . -o out a/IFoo.aidl");
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr("The interface IFoo uses a permission annotation but "
                                             "the method Protected is also annotated"));
}

TEST_F(AidlTest, EnforceInterfaceManualPermissionMethod) {
  io_delegate_.SetFileContents("a/IFoo.aidl", R"(package a;
    @EnforcePermission("INTERNET")
    interface IFoo {
        @PermissionManuallyEnforced
        void Protected();
    })");

  Options options = Options::From("aidl --lang=java -I . -o out a/IFoo.aidl");
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr("The interface IFoo uses a permission annotation but "
                                             "the method Protected is also annotated"));
}

TEST_F(AidlTest, JavaSuppressLint) {
  io_delegate_.SetFileContents("a/IFoo.aidl", R"(package a;
    @JavaSuppressLint({"NewApi"})
    interface IFoo {
    })");

  Options options = Options::From("aidl --lang=java -I . -o out a/IFoo.aidl");
  CaptureStderr();
  EXPECT_TRUE(compile_aidl(options, io_delegate_));
  EXPECT_EQ(GetCapturedStderr(), "");
  string code;
  EXPECT_TRUE(io_delegate_.GetWrittenContents("out/a/IFoo.java", &code));
  EXPECT_THAT(code, HasSubstr("@android.annotation.SuppressLint(value = {\"NewApi\"})"));
}

class AidlOutputPathTest : public AidlTest {
 protected:
  void SetUp() override {
    AidlTest::SetUp();
    io_delegate_.SetFileContents("sub/dir/foo/bar/IFoo.aidl", "package foo.bar; interface IFoo {}");
  }

  void Test(const Options& options, const std::string expected_output_path) {
    EXPECT_TRUE(compile_aidl(options, io_delegate_));
    // check the existence
    EXPECT_TRUE(io_delegate_.GetWrittenContents(expected_output_path, nullptr));
  }
};

TEST_F(AidlOutputPathTest, OutDirWithNoOutputFile) {
  // <out_dir> / <package_name> / <type_name>.java
  Test(Options::From("aidl -I sub/dir -o out sub/dir/foo/bar/IFoo.aidl"), "out/foo/bar/IFoo.java");
}

TEST_F(AidlOutputPathTest, OutDirWithOutputFile) {
  // when output file is explicitly set, it is always respected. -o option is
  // ignored.
  Test(Options::From("aidl -I sub/dir -o out sub/dir/foo/bar/IFoo.aidl output/IFoo.java"),
       "output/IFoo.java");
}

TEST_F(AidlOutputPathTest, NoOutDirWithOutputFile) {
  Test(Options::From("aidl -I sub/dir -o out sub/dir/foo/bar/IFoo.aidl output/IFoo.java"),
       "output/IFoo.java");
}

TEST_F(AidlOutputPathTest, NoOutDirWithNoOutputFile) {
  // output is the same as the input file except for the suffix
  Test(Options::From("aidl -I sub/dir sub/dir/foo/bar/IFoo.aidl"), "sub/dir/foo/bar/IFoo.java");
}

TEST_P(AidlTest, PassOnValidUnsignedInt32Int) {
  EvaluateValidAssignment(
      R"(package a; interface IFoo { const int UINT32_VALUE = 2147483650u32; })", "", typenames_,
      GetLanguage());
}

TEST_P(AidlTest, FailOnOutOfBoundsUInt32MaxConstInt) {
  AidlError error;
  const string expected_stderr =
      "ERROR: p/IFoo.aidl:3.59-73: Could not parse integer: 4294967300u32\n";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("p/IFoo.aidl",
                           R"(package p;
                              interface IFoo {
                                const int uint32_max_oob = 4294967300u32;
                              }
                             )",
                           typenames_, GetLanguage(), &error));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
  EXPECT_EQ(AidlError::PARSE_ERROR, error);
}

TEST_P(AidlTest, PassOnValidUnsignedInt64Int) {
  EvaluateValidAssignment(
      R"(package a; interface IFoo { const long UINT64_VALUE = 18446744073709551615u64; })", "",
      typenames_, GetLanguage());
}

TEST_P(AidlTest, FailOnOutOfBoundsUInt64MaxConstInt) {
  AidlError error;
  const string expected_stderr =
      "ERROR: p/IFoo.aidl:3.60-84: Could not parse integer: 18446744073709551620u64\n";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("p/IFoo.aidl",
                           R"(package p;
                              interface IFoo {
                                const long uint64_max_oob = 18446744073709551620u64;
                              }
                             )",
                           typenames_, GetLanguage(), &error));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
  EXPECT_EQ(AidlError::PARSE_ERROR, error);
}

TEST_P(AidlTest, PassOnValidUnsignedInt32Hex) {
  EvaluateValidAssignment(
      R"(package a; interface IFoo { const int UINT32_VALUE = 0xffffffffu32; })", "", typenames_,
      GetLanguage());
}

TEST_P(AidlTest, FailOnOutOfBoundsUInt32MaxConstHex) {
  AidlError error;
  const string expected_stderr =
      "ERROR: p/IFoo.aidl:3.59-74: Invalid type specifier for an int64 literal: int "
      "(0xfffffffffu32).\n";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("p/IFoo.aidl",
                           R"(package p;
                              interface IFoo {
                                const int uint32_max_oob = 0xfffffffffu32;
                              }
                             )",
                           typenames_, GetLanguage(), &error));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
  EXPECT_EQ(AidlError::BAD_TYPE, error);
}

TEST_P(AidlTest, PassOnValidUnsignedInt64Hex) {
  EvaluateValidAssignment(
      R"(package a; interface IFoo { const int UINT64_VALUE = 0xffffffffffffffffu64; })", "",
      typenames_, GetLanguage());
}

TEST_P(AidlTest, FailOnOutOfBoundsUInt64MaxConstHex) {
  AidlError error;
  const string expected_stderr =
      "ERROR: p/IFoo.aidl:3.59-82: Could not parse hexvalue: 0xfffffffffffffffffu64\n";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("p/IFoo.aidl",
                           R"(package p;
                              interface IFoo {
                                const int uint64_max_oob = 0xfffffffffffffffffu64;
                              }
                             )",
                           typenames_, GetLanguage(), &error));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
  EXPECT_EQ(AidlError::PARSE_ERROR, error);
}

TEST_P(AidlTest, FailOnOutOfBoundsInt32MaxConstInt) {
  AidlError error;
  const string expected_stderr =
      "ERROR: p/IFoo.aidl:3.58-69: Invalid type specifier for an int64 literal: int "
      "(2147483650). Did you mean: 2147483650u32?\n";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("p/IFoo.aidl",
                           R"(package p;
                              interface IFoo {
                                const int int32_max_oob = 2147483650;
                              }
                             )",
                           typenames_, GetLanguage(), &error));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
  EXPECT_EQ(AidlError::BAD_TYPE, error);
}

TEST_P(AidlTest, FailOnOutOfBoundsInt32MinConstInt) {
  AidlError error;
  const string expected_stderr =
      "ERROR: p/IFoo.aidl:3.58-60: Invalid type specifier for an int64 literal: int "
      "(-2147483650).\n";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("p/IFoo.aidl",
                           R"(package p;
                              interface IFoo {
                                const int int32_min_oob = -2147483650;
                              }
                             )",
                           typenames_, GetLanguage(), &error));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
  EXPECT_EQ(AidlError::BAD_TYPE, error);
}

TEST_P(AidlTest, FailOnOutOfBoundsInt64MaxConstInt) {
  AidlError error;
  const string expected_stderr =
      "ERROR: p/IFoo.aidl:3.59-86: Could not parse integer: 21474836509999999999999999\n";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("p/IFoo.aidl",
                           R"(package p;
                              interface IFoo {
                                const long int64_max_oob = 21474836509999999999999999;
                              }
                             )",
                           typenames_, GetLanguage(), &error));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
  EXPECT_EQ(AidlError::PARSE_ERROR, error);
}

TEST_P(AidlTest, FailOnOutOfBoundsInt64MinConstInt) {
  AidlError error;
  const string expected_stderr =
      "ERROR: p/IFoo.aidl:3.61-87: Could not parse integer: 21474836509999999999999999\n";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("p/IFoo.aidl",
                           R"(package p;
                              interface IFoo {
                                const long int64_min_oob = -21474836509999999999999999;
                              }
                             )",
                           typenames_, GetLanguage(), &error));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
  EXPECT_EQ(AidlError::PARSE_ERROR, error);
}

TEST_P(AidlTest, FailOnOutOfBoundsAutofilledEnum) {
  AidlError error;
  const string expected_stderr =
      "ERROR: p/TestEnum.aidl:5.1-36: Invalid type specifier for an int32 literal: byte (FOO+1).\n"
      "ERROR: p/TestEnum.aidl:5.1-36: Enumerator type differs from enum backing type.\n";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("p/TestEnum.aidl",
                           R"(package p;
                              @Backing(type="byte")
                              enum TestEnum {
                                FOO = 127,
                                BAR,
                              }
                             )",
                           typenames_, GetLanguage(), &error));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
  EXPECT_EQ(AidlError::BAD_TYPE, error);
}

TEST_P(AidlTest, FailOnUnsupportedBackingType) {
  AidlError error;
  const string expected_stderr =
      "ERROR: p/TestEnum.aidl:3.35-44: Invalid backing type: boolean. Backing type must be one of: "
      "byte, int, long\n";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("p/TestEnum.aidl",
                           R"(package p;
                              @Backing(type="boolean")
                              enum TestEnum {
                                FOO = 0,
                                BAR = 1,
                              }
                             )",
                           typenames_, GetLanguage(), &error));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
  EXPECT_EQ(AidlError::BAD_TYPE, error);
}

TEST_P(AidlTest, UnsupportedBackingAnnotationParam) {
  AidlError error;
  const string expected_stderr =
      "ERROR: p/TestEnum.aidl:2.1-51: Parameter foo not supported for annotation Backing. It must "
      "be one of: type\n";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("p/TestEnum.aidl",
                           R"(package p;
                              @Backing(foo="byte")
                              enum TestEnum {
                                FOO = 1,
                                BAR,
                              }
                             )",
                           typenames_, GetLanguage(), &error));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
  EXPECT_EQ(AidlError::BAD_TYPE, error);
}

TEST_P(AidlTest, BackingAnnotationRequireTypeParameter) {
  const string expected_stderr = "ERROR: Enum.aidl:1.1-9: Missing 'type' on @Backing.\n";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("Enum.aidl", "@Backing enum Enum { FOO }", typenames_, GetLanguage()));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_F(AidlTest, SupportJavaOnlyImmutableAnnotation) {
  io_delegate_.SetFileContents("Foo.aidl",
                               "@JavaOnlyImmutable parcelable Foo { int a; Bar b; List<Bar> c; "
                               "Map<String, Baz> d; Bar[] e; }");
  io_delegate_.SetFileContents("Bar.aidl", "@JavaOnlyImmutable parcelable Bar { String a; }");
  io_delegate_.SetFileContents("Baz.aidl",
                               "@JavaOnlyImmutable @JavaOnlyStableParcelable parcelable Baz;");
  Options options = Options::From("aidl --lang=java -I . Foo.aidl");
  EXPECT_TRUE(compile_aidl(options, io_delegate_));
}

TEST_F(AidlTest, RejectMutableParcelableFromJavaOnlyImmutableParcelable) {
  io_delegate_.SetFileContents("Foo.aidl", "@JavaOnlyImmutable parcelable Foo { Bar bar; }");
  io_delegate_.SetFileContents("Bar.aidl", "parcelable Bar { String a; }");
  string expected_error =
      "ERROR: Foo.aidl:1.40-44: The @JavaOnlyImmutable 'Foo' has a non-immutable field "
      "named 'bar'.\n";
  CaptureStderr();
  Options options = Options::From("aidl --lang=java Foo.aidl -I .");
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_EQ(expected_error, GetCapturedStderr());
}

TEST_F(AidlTest, JavaOnlyImmutableParcelableWithEnumFields) {
  io_delegate_.SetFileContents("Foo.aidl", "@JavaOnlyImmutable parcelable Foo { Bar bar; }");
  io_delegate_.SetFileContents("Bar.aidl", "enum Bar { FOO }");
  CaptureStderr();
  Options options = Options::From("aidl --lang=java Foo.aidl -I .");
  EXPECT_TRUE(compile_aidl(options, io_delegate_));
  EXPECT_EQ("", GetCapturedStderr());
}

TEST_F(AidlTest, RejectMutableParcelableFromJavaOnlyImmutableUnion) {
  io_delegate_.SetFileContents("Foo.aidl", "@JavaOnlyImmutable union Foo { Bar bar; }");
  io_delegate_.SetFileContents("Bar.aidl", "parcelable Bar { String a; }");
  string expected_error =
      "ERROR: Foo.aidl:1.35-39: The @JavaOnlyImmutable 'Foo' has a non-immutable field "
      "named 'bar'.\n";
  CaptureStderr();
  Options options = Options::From("aidl --lang=java Foo.aidl -I .");
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_EQ(expected_error, GetCapturedStderr());
}

TEST_F(AidlTest, ImmutableParcelableCannotBeInOut) {
  io_delegate_.SetFileContents("Foo.aidl", "@JavaOnlyImmutable parcelable Foo { int a; }");
  io_delegate_.SetFileContents("IBar.aidl", "interface IBar { void my(inout Foo foo); }");
  string expected_error =
      "ERROR: IBar.aidl:1.35-39: 'foo' can't be an inout parameter because @JavaOnlyImmutable can "
      "only be an in parameter.\n";
  CaptureStderr();
  Options options = Options::From("aidl --lang=java IBar.aidl -I .");
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_EQ(expected_error, GetCapturedStderr());
}

TEST_F(AidlTest, ImmutableParcelableCannotBeOut) {
  io_delegate_.SetFileContents("Foo.aidl", "@JavaOnlyImmutable parcelable Foo { int a; }");
  io_delegate_.SetFileContents("IBar.aidl", "interface IBar { void my(out Foo foo); }");
  string expected_error =
      "ERROR: IBar.aidl:1.33-37: 'foo' can't be an out parameter because @JavaOnlyImmutable can "
      "only be an in parameter.\n";
  CaptureStderr();
  Options options = Options::From("aidl --lang=java IBar.aidl -I .");
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_EQ(expected_error, GetCapturedStderr());
}

TEST_F(AidlTest, ImmutableParcelableFieldNameRestriction) {
  io_delegate_.SetFileContents("Foo.aidl", "@JavaOnlyImmutable parcelable Foo { int a; int A; }");
  Options options = Options::From("aidl -I . --lang=java Foo.aidl");
  const string expected_stderr =
      "ERROR: Foo.aidl:1.47-49: 'Foo' has duplicate field name 'A' after capitalizing the first "
      "letter\n";
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_P(AidlTest, UnionInUnion) {
  import_paths_.insert(".");
  io_delegate_.SetFileContents("Bar.aidl", "union Bar { int n = 42; long l; }");
  CaptureStderr();
  EXPECT_NE(nullptr, Parse("Foo.aidl", "union Foo { Bar b; int n; }", typenames_, GetLanguage()));
  EXPECT_THAT("", GetCapturedStderr());
}

TEST_P(AidlTest, UnionRejectsEmptyDecl) {
  const string method = "package a; union Foo {}";
  const string expected_stderr = "ERROR: a/Foo.aidl:1.17-21: The union 'Foo' has no fields.\n";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("a/Foo.aidl", method, typenames_, GetLanguage()));
  EXPECT_THAT(GetCapturedStderr(), testing::HasSubstr(expected_stderr));
}

TEST_P(AidlTest, UnionRejectsParcelableHolder) {
  const string method = "package a; union Foo { ParcelableHolder x; }";
  const string expected_stderr =
      "ERROR: a/Foo.aidl:1.40-42: A union can't have a member of ParcelableHolder 'x'\n";
  CaptureStderr();
  EXPECT_EQ(nullptr, Parse("a/Foo.aidl", method, typenames_, GetLanguage()));
  EXPECT_THAT(GetCapturedStderr(), testing::HasSubstr(expected_stderr));
}

TEST_P(AidlTest, UnionRejectsFirstEnumWithNoDefaults) {
  import_paths_.insert(".");
  io_delegate_.SetFileContents("a/Enum.aidl", "package a; enum Enum { FOO, BAR }");
  const string expected_err = "The union's first member should have a useful default value.";
  CaptureStderr();
  EXPECT_EQ(nullptr,
            Parse("a/Foo.aidl", "package a; union Foo { a.Enum e; }", typenames_, GetLanguage()));
  EXPECT_THAT(GetCapturedStderr(), testing::HasSubstr(expected_err));
}

TEST_P(AidlTest, GenericStructuredParcelable) {
  io_delegate_.SetFileContents("Foo.aidl", "parcelable Foo<T, U> { int a; int A; }");
  Options options = Options::From("aidl -I . Foo.aidl --lang=" + to_string(GetLanguage()));
  const string expected_stderr = "";
  CaptureStderr();
  EXPECT_TRUE(compile_aidl(options, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_F(AidlTest, GenericStructuredParcelableWithStringConstants_Cpp) {
  io_delegate_.SetFileContents("Foo.aidl",
                               "parcelable Foo<T, U> { int a; const String s = \"\"; }");
  Options options = Options::From("aidl Foo.aidl -I . --lang=" + to_string(Options::Language::CPP) +
                                  " -o out -h out");
  const string expected_stderr = "";
  CaptureStderr();
  EXPECT_TRUE(compile_aidl(options, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());

  string code;
  EXPECT_TRUE(io_delegate_.GetWrittenContents("out/Foo.h", &code));
  EXPECT_THAT(code, testing::HasSubstr(R"--(template <typename T, typename U>
const ::android::String16& Foo<T,U>::s() {
  static const ::android::String16 value(::android::String16(""));
  return value;
})--"));
}

TEST_F(AidlTest, GenericStructuredParcelableWithStringConstants_Ndk) {
  io_delegate_.SetFileContents("Foo.aidl",
                               "parcelable Foo<T, U> { int a; const String s = \"\"; }");
  Options options = Options::From("aidl Foo.aidl -I . --lang=" + to_string(Options::Language::NDK) +
                                  " -o out -h out");
  const string expected_stderr = "";
  CaptureStderr();
  EXPECT_TRUE(compile_aidl(options, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());

  string code;
  EXPECT_TRUE(io_delegate_.GetWrittenContents("out/aidl/Foo.h", &code));
  EXPECT_THAT(code, testing::HasSubstr(R"--(template <typename T, typename U>
const char* Foo<T, U>::s = "";
)--"));
}

TEST_F(AidlTest, NestedTypeArgs) {
  io_delegate_.SetFileContents("a/Bar.aidl", "package a; parcelable Bar<A> { }");
  io_delegate_.SetFileContents("a/Baz.aidl", "package a; parcelable Baz<A, B> { }");

  io_delegate_.SetFileContents("a/Foo.aidl",
                               "package a; import a.Bar; import a.Baz; parcelable Foo { "
                               "Baz<Bar<Bar<String[]>>[], Bar<String>> barss; }");
  Options options = Options::From("aidl a/Foo.aidl -I . -o out --lang=java");
  const string expected_stderr = "";
  CaptureStderr();
  EXPECT_TRUE(compile_aidl(options, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());

  string code;
  EXPECT_TRUE(io_delegate_.GetWrittenContents("out/a/Foo.java", &code));
  EXPECT_THAT(code,
              testing::HasSubstr(
                  "a.Baz<a.Bar<a.Bar<java.lang.String[]>>[],a.Bar<java.lang.String>> barss;"));
}

TEST_F(AidlTest, AcceptMultiDimensionalFixedSizeArray) {
  io_delegate_.SetFileContents("a/Bar.aidl", "package a; parcelable Bar { String[2][3] a; }");

  Options options = Options::From("aidl a/Bar.aidl -I . -o out --lang=ndk");
  CaptureStderr();
  EXPECT_TRUE(compile_aidl(options, io_delegate_));
  EXPECT_EQ("", GetCapturedStderr());
}

TEST_F(AidlTest, AcceptBinarySizeArray) {
  io_delegate_.SetFileContents(
      "a/Bar.aidl", "package a; parcelable Bar { const int CONST = 3; String[CONST + 1] a; }");

  Options options = Options::From("aidl a/Bar.aidl -I . -o out --lang=ndk");
  CaptureStderr();
  EXPECT_TRUE(compile_aidl(options, io_delegate_));
  EXPECT_EQ("", GetCapturedStderr());
}

TEST_F(AidlTest, AcceptRefSizeArray) {
  io_delegate_.SetFileContents(
      "a/Bar.aidl", "package a; parcelable Bar { const int CONST = 3; String[CONST] a; }");

  Options options = Options::From("aidl a/Bar.aidl -I . -o out --lang=ndk");
  CaptureStderr();
  EXPECT_TRUE(compile_aidl(options, io_delegate_));
  EXPECT_EQ("", GetCapturedStderr());
}

TEST_F(AidlTest, RejectArrayOfFixedSizeArray) {
  io_delegate_.SetFileContents("a/Bar.aidl", "package a; parcelable Bar { String[2][] a; }");

  Options options = Options::From("aidl a/Bar.aidl -I . -o out --lang=java");
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr("Multi-dimensional arrays must be fixed size."));
}

TEST_F(AidlTest, RejectFixedSizeArrayOfDynamicArray) {
  io_delegate_.SetFileContents("a/Bar.aidl", "package a; parcelable Bar { String[][3] a; }");

  Options options = Options::From("aidl a/Bar.aidl -I . -o out --lang=java");
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr("Multi-dimensional arrays must be fixed size."));
}

TEST_F(AidlTest, RejectArrayOfArray) {
  io_delegate_.SetFileContents("a/Bar.aidl", "package a; parcelable Bar { String[][] a; }");

  Options options = Options::From("aidl a/Bar.aidl -I . -o out --lang=java");
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr("Multi-dimensional arrays must be fixed size."));
}

TEST_F(AidlTest, RejectInvalidArraySize_Negative) {
  io_delegate_.SetFileContents("a/Bar.aidl", "package a; parcelable Bar { String[-1] a; }");

  Options options = Options::From("aidl a/Bar.aidl -I . -o out --lang=java");
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr("Array size must be a positive number"));
}

TEST_F(AidlTest, RejectInvalidArraySize_WrongType) {
  io_delegate_.SetFileContents("a/Bar.aidl", "package a; parcelable Bar { String[\"3\"] a; }");

  Options options = Options::From("aidl a/Bar.aidl -I . -o out --lang=java");
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr("Array size must be a positive number"));
}

TEST_F(AidlTest, DoubleGenericError) {
  io_delegate_.SetFileContents("a/Bar.aidl",
                               "package a; parcelable Bar { List<String><String> a; }");

  Options options = Options::From("aidl a/Bar.aidl -I . -o out --lang=java");
  const string expected_stderr =
      "ERROR: a/Bar.aidl:1.28-33: Can only specify one set of type parameters.\n";
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_F(AidlTest, ArrayBeforeGenericError) {
  io_delegate_.SetFileContents("a/Bar.aidl", "package a; parcelable Bar { List[]<String> a; }");

  Options options = Options::From("aidl a/Bar.aidl -I . -o out --lang=java");
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_THAT(GetCapturedStderr(), testing::HasSubstr("syntax error, unexpected '<'"));
}

TEST_F(AidlTest, NullableArraysAreNotSupported) {
  io_delegate_.SetFileContents("a/Bar.aidl",
                               "package a; parcelable Bar { String @nullable [] a; }");

  Options options = Options::From("aidl a/Bar.aidl -I . -o out --lang=java");
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_THAT(GetCapturedStderr(), testing::HasSubstr("Annotations for arrays are not supported."));
}

TEST_F(AidlTest, ListOfNullablesAreNotSupported) {
  io_delegate_.SetFileContents("a/Bar.aidl",
                               "package a; parcelable Bar { List<@nullable String> a; }");

  Options options = Options::From("aidl a/Bar.aidl -I . -o out --lang=java");
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_THAT(GetCapturedStderr(),
              testing::HasSubstr("Annotations for type arguments are not supported."));
}

TEST_F(AidlTest, DefaultShouldMatchWithFixedSizeArray) {
  io_delegate_.SetFileContents("a/Bar.aidl",
                               "package a;\n"
                               "parcelable Bar {\n"
                               "  int[2][3] a = {{1,2,3}, {4,5,6}};\n"
                               "}");

  Options options = Options::From("aidl a/Bar.aidl -I . -o out --lang=ndk");
  CaptureStderr();
  EXPECT_TRUE(compile_aidl(options, io_delegate_));
  EXPECT_EQ("", GetCapturedStderr());
}

TEST_F(AidlTest, FixedSizeArrayWithWrongTypeDefaultValue) {
  io_delegate_.SetFileContents("a/Bar.aidl",
                               "package a;\n"
                               "parcelable Bar {\n"
                               "  int[2][3] a = {{\"1\",\"2\",\"3\"}, {4,5,6}};\n"
                               "}");

  Options options = Options::From("aidl a/Bar.aidl -I . -o out --lang=java");
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr("Invalid type specifier for a literal string: int"));
}

TEST_F(AidlTest, FixedSizeArrayWithMoreElements) {
  io_delegate_.SetFileContents("a/Bar.aidl",
                               "package a;\n"
                               "parcelable Bar {\n"
                               "  int[2][3] a = {{1,2,3,4}, {4,5,6}};\n"
                               "}");

  Options options = Options::From("aidl a/Bar.aidl -I . -o out --lang=java");
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_THAT(GetCapturedStderr(),
              HasSubstr("Expected an array of 3 elements, but found one with 4 elements"));
}

TEST_F(AidlTest, FixedSizeArrayWithFewerElements) {
  io_delegate_.SetFileContents("a/Bar.aidl",
                               "package a;\n"
                               "parcelable Bar {\n"
                               "  int[2][3] a = {};\n"
                               "}");

  Options options = Options::From("aidl a/Bar.aidl -I . -o out --lang=java");
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_THAT(GetCapturedStderr(),
              HasSubstr("Expected an array of 2 elements, but found one with 0 elements"));
}

struct GenericAidlTest : ::testing::Test {
  FakeIoDelegate io_delegate_;
  void Compile(string cmd) {
    io_delegate_.SetFileContents("Foo.aidl", "parcelable Foo { Bar<Baz<Qux>> x; }");
    io_delegate_.SetFileContents("Bar.aidl", "parcelable Bar<T> {  }");
    io_delegate_.SetFileContents("Baz.aidl", "parcelable Baz<T> {  }");
    io_delegate_.SetFileContents("Qux.aidl", "parcelable Qux {  }");

    Options options = Options::From(cmd);
    CaptureStderr();
    EXPECT_TRUE(compile_aidl(options, io_delegate_));
    EXPECT_EQ("", GetCapturedStderr());
  }
};

TEST_F(GenericAidlTest, ImportGenericParameterTypesCPP) {
  Compile("aidl Foo.aidl --lang=cpp -I . -o out -h out");
  string code;
  EXPECT_TRUE(io_delegate_.GetWrittenContents("out/Foo.h", &code));
  EXPECT_THAT(code, testing::HasSubstr("#include <Bar.h>"));
  EXPECT_THAT(code, testing::HasSubstr("#include <Baz.h>"));
  EXPECT_THAT(code, testing::HasSubstr("#include <Qux.h>"));
}

TEST_F(GenericAidlTest, ImportGenericParameterTypesNDK) {
  Compile("aidl Foo.aidl --lang=ndk -I . -o out -h out");
  string code;
  EXPECT_TRUE(io_delegate_.GetWrittenContents("out/aidl/Foo.h", &code));
  EXPECT_THAT(code, testing::HasSubstr("#include <aidl/Bar.h>"));
  EXPECT_THAT(code, testing::HasSubstr("#include <aidl/Baz.h>"));
  EXPECT_THAT(code, testing::HasSubstr("#include <aidl/Qux.h>"));
}

TEST_P(AidlTest, RejectGenericStructuredParcelabelRepeatedParam) {
  io_delegate_.SetFileContents("Foo.aidl", "parcelable Foo<T,T> { int a; int A; }");
  Options options = Options::From("aidl Foo.aidl -I . --lang=" + to_string(GetLanguage()));
  const string expected_stderr =
      "ERROR: Foo.aidl:1.11-15: Every type parameter should be unique.\n";
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_P(AidlTest, RejectGenericStructuredParcelableField) {
  io_delegate_.SetFileContents("Foo.aidl", "parcelable Foo<T,T> { T a; int A; }");
  Options options = Options::From("aidl Foo.aidl -I . --lang=" + to_string(GetLanguage()));
  const string expected_stderr =
      "ERROR: Foo.aidl: Couldn't find import for class T. Searched here:\n - ./\nERROR: "
      "Foo.aidl:1.22-24: Failed to resolve 'T'\n";
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_EQ(expected_stderr, GetCapturedStderr());
}

TEST_P(AidlTest, LongCommentWithinConstExpression) {
  io_delegate_.SetFileContents("Foo.aidl", "enum Foo { FOO = (1 << 1) /* comment */ | 0x0 }");
  Options options = Options::From("aidl Foo.aidl -I . --lang=" + to_string(GetLanguage()));
  CaptureStderr();
  EXPECT_TRUE(compile_aidl(options, io_delegate_));
  EXPECT_EQ("", GetCapturedStderr());
}

TEST_F(AidlTest, RejectUntypdeListAndMapInUnion) {
  io_delegate_.SetFileContents("a/Foo.aidl", "package a; union Foo { List l; Map m; }");
  Options options = Options::From("aidl a/Foo.aidl -I . --lang=java -o out");
  std::string expectedErr =
      "ERROR: a/Foo.aidl:1.28-30: "
      "Encountered an untyped List or Map. The use of untyped List/Map is "
      "prohibited because it is not guaranteed that the objects in the list are recognizable in "
      "the receiving side. Consider switching to an array or a generic List/Map.\n"
      "ERROR: a/Foo.aidl:1.35-37: "
      "Encountered an untyped List or Map. The use of untyped List/Map is "
      "prohibited because it is not guaranteed that the objects in the list are recognizable in "
      "the receiving side. Consider switching to an array or a generic List/Map.\n";
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_EQ(expectedErr, GetCapturedStderr());
}

TEST_F(AidlTest, RejectUntypdeListAndMapInUnstructuredParcelable) {
  io_delegate_.SetFileContents("a/Foo.aidl", "package a; parcelable Foo { List l; Map m; }");
  Options options = Options::From("aidl a/Foo.aidl -I . --lang=java -o out");
  std::string expectedErr =
      "ERROR: a/Foo.aidl:1.33-35: "
      "Encountered an untyped List or Map. The use of untyped List/Map is "
      "prohibited because it is not guaranteed that the objects in the list are recognizable in "
      "the receiving side. Consider switching to an array or a generic List/Map.\n"
      "ERROR: a/Foo.aidl:1.40-42: "
      "Encountered an untyped List or Map. The use of untyped List/Map is "
      "prohibited because it is not guaranteed that the objects in the list are recognizable in "
      "the receiving side. Consider switching to an array or a generic List/Map.\n";
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_EQ(expectedErr, GetCapturedStderr());
}

TEST_F(AidlTest, RejectNestedUntypedListAndMap) {
  io_delegate_.SetFileContents("a/Bar.aidl", "package a; parcelable Bar<T>;");
  io_delegate_.SetFileContents(
      "a/Foo.aidl", "package a; import a.Bar; parcelable Foo { Bar<List> a; Bar<Map> b; }");
  Options options = Options::From("aidl a/Foo.aidl -I . --lang=java -o out");
  std::string expectedErr =
      "ERROR: a/Foo.aidl:1.52-54: "
      "Encountered an untyped List or Map. The use of untyped List/Map is "
      "prohibited because it is not guaranteed that the objects in the list are recognizable in "
      "the receiving side. Consider switching to an array or a generic List/Map.\n"
      "ERROR: a/Foo.aidl:1.64-66: "
      "Encountered an untyped List or Map. The use of untyped List/Map is "
      "prohibited because it is not guaranteed that the objects in the list are recognizable in "
      "the receiving side. Consider switching to an array or a generic List/Map.\n";
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_EQ(expectedErr, GetCapturedStderr());
}

TEST_F(AidlTest, EnumWithDefaults_Java) {
  io_delegate_.SetFileContents("a/p/Enum.aidl", "package p; enum Enum { FOO, BAR }");
  io_delegate_.SetFileContents("a/p/Foo.aidl", R"(
package p;
import p.Enum;
parcelable Foo {
  Enum e = Enum.BAR;
})");
  CaptureStderr();
  auto options = Options::From("aidl -I a --lang java -o out a/p/Foo.aidl");
  EXPECT_TRUE(compile_aidl(options, io_delegate_));
  auto err = GetCapturedStderr();
  EXPECT_EQ("", err);

  string code;
  EXPECT_TRUE(io_delegate_.GetWrittenContents("out/p/Foo.java", &code));
  EXPECT_THAT(code, testing::HasSubstr("byte e = p.Enum.BAR"));
}

TEST_F(AidlTest, EnumWithDefaults_Cpp) {
  io_delegate_.SetFileContents("a/p/Enum.aidl", "package p; enum Enum { FOO, BAR }");
  io_delegate_.SetFileContents("a/p/Foo.aidl", R"(
package p;
import p.Enum;
parcelable Foo {
  Enum e = Enum.BAR;
})");
  CaptureStderr();
  auto options = Options::From("aidl -I a --lang cpp -o out -h out a/p/Foo.aidl");
  EXPECT_TRUE(compile_aidl(options, io_delegate_));
  auto err = GetCapturedStderr();
  EXPECT_EQ("", err);

  string code;
  EXPECT_TRUE(io_delegate_.GetWrittenContents("out/p/Foo.h", &code));
  EXPECT_THAT(code, testing::HasSubstr("::p::Enum e = ::p::Enum::BAR;"));
}

TEST_F(AidlTest, EnumWithDefaults_Ndk) {
  io_delegate_.SetFileContents("a/p/Enum.aidl", "package p; enum Enum { FOO, BAR }");
  io_delegate_.SetFileContents("a/p/Foo.aidl", R"(
package p;
import p.Enum;
parcelable Foo {
  Enum e = Enum.BAR;
})");
  CaptureStderr();
  auto options = Options::From("aidl -I a --lang ndk -o out -h out a/p/Foo.aidl");
  EXPECT_TRUE(compile_aidl(options, io_delegate_));
  auto err = GetCapturedStderr();
  EXPECT_EQ("", err);

  string code;
  EXPECT_TRUE(io_delegate_.GetWrittenContents("out/aidl/p/Foo.h", &code));
  EXPECT_THAT(code, testing::HasSubstr("::aidl::p::Enum e = ::aidl::p::Enum::BAR;"));
}

TEST_F(AidlTest, EnumWithDefaults_Rust) {
  io_delegate_.SetFileContents("a/p/Enum.aidl", "package p; enum Enum { FOO, BAR }");
  io_delegate_.SetFileContents("a/p/Foo.aidl", R"(
package p;
import p.Enum;
parcelable Foo {
  int  n = 42;
  Enum e = Enum.BAR;
})");
  CaptureStderr();
  auto options = Options::From("aidl -I a --lang rust -o out -h out a/p/Foo.aidl");
  EXPECT_TRUE(compile_aidl(options, io_delegate_));
  auto err = GetCapturedStderr();
  EXPECT_EQ("", err);

  string code;
  EXPECT_TRUE(io_delegate_.GetWrittenContents("out/p/Foo.rs", &code));
  EXPECT_THAT(code, testing::HasSubstr(R"(
  fn default() -> Self {
    Self {
      r#n: 42,
      r#e: crate::mangled::_1_p_4_Enum::BAR,
    }
  })"));
}

TEST_P(AidlTest, EnumeratorIsConstantValue_DefaultValue) {
  import_paths_.insert("a");
  io_delegate_.SetFileContents("a/p/Enum.aidl", "package p; enum Enum { FOO = 1, BAR = 2}");
  CaptureStderr();
  const AidlDefinedType* type = Parse("a/p/Foo.aidl", R"(
package p;
import p.Enum;
parcelable Foo {
  int e = Enum.FOO | Enum.BAR;
})",
                                      typenames_, GetLanguage());
  auto err = GetCapturedStderr();
  EXPECT_EQ("", err);
  EXPECT_TRUE(type);
  const auto& fields = type->AsStructuredParcelable()->GetFields();
  EXPECT_EQ("int e = 3", fields[0]->ToString());
}

TEST_F(AidlTest, EnumeratorIsConstantValue_CanDefineOtherEnumerator) {
  CaptureStderr();
  const AidlDefinedType* type = Parse("a/p/Foo.aidl", R"(
package a.p;
@Backing(type="int")
enum Foo {
      STANDARD_SHIFT = 16,
      STANDARD_BT709 = 1 << STANDARD_SHIFT,
      STANDARD_BT601_625 = 2 << STANDARD_SHIFT,
}
)",
                                      typenames_, Options::Language::JAVA);
  auto err = GetCapturedStderr();
  EXPECT_EQ("", err);
  ASSERT_NE(type, nullptr);
  const auto& enum_type = type->AsEnumDeclaration();
  string code;
  auto writer = CodeWriter::ForString(&code);
  DumpVisitor visitor(*writer, /*inline_constants=*/true);
  visitor.Visit(*enum_type);
  writer->Close();
  EXPECT_EQ(R"--(@Backing(type="int")
enum Foo {
  STANDARD_SHIFT = 16,
  STANDARD_BT709 = 65536,
  STANDARD_BT601_625 = 131072,
}
)--",
            code);
}

TEST_F(AidlTest, DumpApiWithConstantReferences) {
  io_delegate_.SetFileContents("foo/bar/Foo.aidl", R"(
package foo.bar;
import foo.bar.Bar;
import foo.bar.Enum;
parcelable Foo {
  int n = Bar.A + 1;
  int[] ns = {1, Bar.A, Bar.B + 1};
  Enum e = Enum.A;
  Enum[] es = {Enum.A, Enum.B};
}
)");
  io_delegate_.SetFileContents("foo/bar/Bar.aidl", R"(
package foo.bar;
parcelable Bar {
  const int A = 1;
  const int B = A + 1;
}
)");
  io_delegate_.SetFileContents("foo/bar/Enum.aidl", R"(
package foo.bar;
enum Enum {
  A,
  B = A + 2,
}
)");
  vector<string> args = {"aidl",
                         "--dumpapi",
                         "--out=dump",
                         "--include=.",
                         "foo/bar/Foo.aidl",
                         "foo/bar/Bar.aidl",
                         "foo/bar/Enum.aidl"};
  ASSERT_TRUE(dump_api(Options::From(args), io_delegate_));

  string actual;
  EXPECT_TRUE(io_delegate_.GetWrittenContents("dump/foo/bar/Foo.aidl", &actual));
  EXPECT_EQ(string(kPreamble).append(R"(package foo.bar;
parcelable Foo {
  int n = (foo.bar.Bar.A + 1) /* 2 */;
  int[] ns = {1, foo.bar.Bar.A /* 1 */, (foo.bar.Bar.B + 1) /* 3 */};
  foo.bar.Enum e = foo.bar.Enum.A;
  foo.bar.Enum[] es = {foo.bar.Enum.A, foo.bar.Enum.B};
}
)"),
            actual);
  EXPECT_TRUE(io_delegate_.GetWrittenContents("dump/foo/bar/Bar.aidl", &actual));
  EXPECT_EQ(string(kPreamble).append(R"(package foo.bar;
parcelable Bar {
  const int A = 1;
  const int B = (A + 1) /* 2 */;
}
)"),
            actual);
  EXPECT_TRUE(io_delegate_.GetWrittenContents("dump/foo/bar/Enum.aidl", &actual));
  EXPECT_EQ(string(kPreamble).append(R"(package foo.bar;
enum Enum {
  A,
  B = (A + 2) /* 2 */,
}
)"),
            actual);
}

TEST_F(AidlTest, EnumDefaultShouldBeEnumerators) {
  io_delegate_.SetFileContents("a/p/Enum.aidl", "package p; enum Enum { FOO = 1, BAR = 2}");
  io_delegate_.SetFileContents("a/p/Foo.aidl", R"(
package p;
import p.Enum;
parcelable Foo {
  Enum e = Enum.FOO | Enum.BAR;
})");
  CaptureStderr();
  auto options = Options::From("aidl -I a --lang java -o out -h out a/p/Foo.aidl");
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  auto err = GetCapturedStderr();
  EXPECT_EQ("ERROR: a/p/Foo.aidl:5.11-20: Invalid value (Enum.FOO|Enum.BAR) for enum p.Enum\n",
            err);
}

TEST_F(AidlTest, EnumDefaultShouldBeEnumerators_RejectsNumericValue) {
  io_delegate_.SetFileContents("a/p/Enum.aidl", "package p; enum Enum { FOO = 1, BAR = 2}");
  io_delegate_.SetFileContents("a/p/Foo.aidl", R"(
package p;
import p.Enum;
parcelable Foo {
  Enum e = 1;
})");
  CaptureStderr();
  auto options = Options::From("aidl -I a --lang java -o out -h out a/p/Foo.aidl");
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr("Invalid value (1) for enum p.Enum"));
}

TEST_P(AidlTest, DefaultWithEmptyArray) {
  io_delegate_.SetFileContents("a/p/Foo.aidl", "package p; parcelable Foo { p.Bar[] bars = {}; }");
  io_delegate_.SetFileContents("a/p/Bar.aidl", "package p; parcelable Bar { }");
  CaptureStderr();
  auto options =
      Options::From("aidl -I a --lang " + to_string(GetLanguage()) + " -o out -h out a/p/Foo.aidl");
  EXPECT_TRUE(compile_aidl(options, io_delegate_));
  auto err = GetCapturedStderr();
  EXPECT_EQ("", err);
}

TEST_P(AidlTest, RejectRefsInAnnotation) {
  io_delegate_.SetFileContents("a/p/IFoo.aidl",
                               "package p; interface IFoo {\n"
                               "  const String ANNOTATION = \"@Annotation\";\n"
                               "  @JavaPassthrough(annotation=ANNOTATION) void foo();\n"
                               "}");
  CaptureStderr();
  auto options = Options::From("aidl -I a --lang " + to_string(GetLanguage()) +
                               " -o out -h out a/p/IFoo.aidl");
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  auto err = GetCapturedStderr();
  EXPECT_EQ(
      "ERROR: a/p/IFoo.aidl:3.31-41: Value must be a constant expression but contains reference to "
      "ANNOTATION.\n",
      err);
}

TEST_F(AidlTest, DefaultWithEnumValues) {
  io_delegate_.SetFileContents(
      "a/p/Foo.aidl",
      "package p; import p.Bar; parcelable Foo { Bar[] bars = { Bar.FOO, Bar.FOO }; }");
  io_delegate_.SetFileContents("a/p/Bar.aidl", "package p; enum Bar { FOO, BAR }");
  CaptureStderr();
  auto options = Options::From("aidl -I a --lang ndk -o out -h out a/p/Foo.aidl");
  EXPECT_TRUE(compile_aidl(options, io_delegate_));
  auto err = GetCapturedStderr();
  EXPECT_EQ("", err);
  string code;
  EXPECT_TRUE(io_delegate_.GetWrittenContents("out/aidl/p/Foo.h", &code));
  EXPECT_THAT(
      code, testing::HasSubstr(
                "std::vector<::aidl::p::Bar> bars = {::aidl::p::Bar::FOO, ::aidl::p::Bar::FOO};"));
}

TEST_F(AidlTest, RejectsCircularReferencingEnumerators) {
  io_delegate_.SetFileContents("a/p/Foo.aidl", "package p; enum Foo { A = B, B }");
  CaptureStderr();
  auto options = Options::From("aidl -I a --lang ndk -o out -h out a/p/Foo.aidl");
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  auto err = GetCapturedStderr();
  EXPECT_EQ("ERROR: a/p/Foo.aidl:1.26-28: Found a circular reference: B -> A -> B\n", err);
}

TEST_F(AidlTest, RejectsCircularReferencingConsts) {
  io_delegate_.SetFileContents("a/p/Foo.aidl",
                               "package p; parcelable Foo { const int A = A + 1; }");
  CaptureStderr();
  auto options = Options::From("aidl -I a --lang ndk -o out -h out a/p/Foo.aidl");
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  auto err = GetCapturedStderr();
  EXPECT_EQ("ERROR: a/p/Foo.aidl:1.42-44: Found a circular reference: A -> A\n", err);
}

TEST_F(AidlTest, RecursiveReferences) {
  io_delegate_.SetFileContents("a/p/Foo.aidl",
                               "package p; parcelable Foo { const int A = p.Bar.A + 1; }");
  io_delegate_.SetFileContents("a/p/Bar.aidl",
                               "package p; parcelable Bar { const int A = p.Baz.A + 1; }");
  io_delegate_.SetFileContents("a/p/Baz.aidl", "package p; parcelable Baz { const int A = 1; }");
  CaptureStderr();
  auto options = Options::From("aidl -I a --lang ndk -o out -h out a/p/Foo.aidl");
  EXPECT_TRUE(compile_aidl(options, io_delegate_));
  EXPECT_EQ("", GetCapturedStderr());
}

TEST_P(AidlTest, CircularReferenceWithFullyQualified) {
  io_delegate_.SetFileContents("Foo.aidl", "enum Foo { A = Foo.A }");
  auto options =
      Options::From("aidl --lang " + to_string(GetLanguage()) + " -I . -o out -h out Foo.aidl");
  const string err = "ERROR: Foo.aidl:1.15-21: Found a circular reference: Foo.A -> Foo.A\n";
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_EQ(err, GetCapturedStderr());
}

TEST_P(AidlTest, ConstRefsCanPointToTheSameValue) {
  io_delegate_.SetFileContents("Foo.aidl", "enum Foo { A = 0 }");
  // this demonstrates the case that "Foo.A" const-ref node is visited twice by B and C.
  io_delegate_.SetFileContents("Bar.aidl", "enum Bar { A = Foo.A, B = A, C = A }");
  auto options =
      Options::From("aidl --lang " + to_string(GetLanguage()) + " -I . -o out -h out Bar.aidl");
  CaptureStderr();
  EXPECT_TRUE(compile_aidl(options, io_delegate_));
  EXPECT_EQ("", GetCapturedStderr());
}

TEST_P(AidlTest, UnknownConstReference) {
  io_delegate_.SetFileContents("Foo.aidl", " parcelable Foo { int field = UNKNOWN_REF; }");
  auto options =
      Options::From("aidl -I . --lang " + to_string(GetLanguage()) + " -o out -h out Foo.aidl");
  const string err =
      "ERROR: Foo.aidl:1.30-42: Can't find UNKNOWN_REF in Foo\n"
      "ERROR: Foo.aidl:1.30-42: Unknown reference 'UNKNOWN_REF'\n";
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_EQ(err, GetCapturedStderr());
}

TEST_P(AidlTest, JavaCompatibleBuiltinTypes) {
  string contents = R"(
import android.os.IBinder;
import android.os.IInterface;
interface IFoo {}
  )";
  EXPECT_NE(nullptr, Parse("IFoo.aidl", contents, typenames_, GetLanguage()));
}

TEST_P(AidlTest, WarningInterfaceName) {
  io_delegate_.SetFileContents("p/Foo.aidl", "package p; interface Foo {}");
  auto options = Options::From("aidl -I . --lang " + to_string(GetLanguage()) +
                               " -Winterface-name -o out -h out p/Foo.aidl");
  CaptureStderr();
  EXPECT_TRUE(compile_aidl(options, io_delegate_));
  EXPECT_EQ(
      "WARNING: p/Foo.aidl:1.11-21: Interface names should start with I. [-Winterface-name]\n",
      GetCapturedStderr());
}

TEST_P(AidlTest, ErrorInterfaceName) {
  io_delegate_.SetFileContents("p/Foo.aidl", "package p; interface Foo {}");
  auto options = Options::From("aidl -I . --lang " + to_string(GetLanguage()) +
                               " -Winterface-name -Werror -o out -h out p/Foo.aidl");
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_EQ("ERROR: p/Foo.aidl:1.11-21: Interface names should start with I. [-Winterface-name]\n",
            GetCapturedStderr());
}

TEST_F(AidlTest, RejectsIncorrectOutputFilePathOnLegacyCppInput) {
  const std::string input_file = "base/p/q/IFoo.aidl";
  const std::string header_dir = "out/";
  const std::string output_file = "out/base/p/q/IFoo.cpp";
  io_delegate_.SetFileContents(input_file, "package p.q; interface IFoo {}");

  auto options = Options::From({"aidl-cpp", "-I base", input_file, header_dir, output_file});
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_THAT(
      GetCapturedStderr(),
      testing::StartsWith(
          "ERROR: base/p/q/IFoo.aidl:1.13-23: Output file is expected to be at out/p/q/IFoo.cpp, "
          "but is out/base/p/q/IFoo.cpp."));
}

TEST_F(AidlTest, FormatCommentsForJava) {
  using android::aidl::FormatCommentsForJava;

  struct TestCase {
    vector<Comment> comments;
    string formatted;
  };
  vector<TestCase> testcases = {
      {{}, ""},
      {{{"// line comments\n"}}, "// line comments\n"},
      {{{"// @hide \n"}}, "// @hide \n"},
      // Transform the last block comment as Javadoc.
      {{{"/*\n"
         " * Hello, world!\n"
         " */"}},
       "/** Hello, world! */\n"},
      {{{"/* @hide */"}}, "/** @hide */\n"},
      {{{"/**\n"
         "   @param foo ...\n"
         "*/"}},
       "/** @param foo ... */\n"},
      {{{"/* @hide */"}, {"/* @hide */"}}, "/* @hide */\n/** @hide */\n"},
      {{{"/* @deprecated first */"}, {"/* @deprecated second */"}},
       "/* @deprecated first */\n/** @deprecated second */\n"},
      {{{"/* @deprecated */"}, {"/** @param foo */"}}, "/* @deprecated */\n/** @param foo */\n"},
      // Line comments are printed as they are
      {{{"/* @deprecated */"}, {"// line comments\n"}}, "/* @deprecated */\n// line comments\n"},
  };
  for (const auto& [input, formatted] : testcases) {
    EXPECT_EQ(formatted, FormatCommentsForJava(input));
  }
}

TEST_F(AidlTest, SuppressWarningsIsNotForArgs) {
  io_delegate_.SetFileContents(
      "IFoo.aidl",
      "interface IFoo {\n"
      "  void foo(in @SuppressWarnings(value={\"inout-parameter\"}) int x);\n"
      "}");
  auto options = Options::From("aidl -I . --lang=java IFoo.aidl");
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr("@SuppressWarnings is not available"));
}

TEST_F(AidlTest, VoidCantBeUsedInMethodParameterType) {
  io_delegate_.SetFileContents("p/IFoo.aidl", "package p; interface IFoo{ void j(void n);}");
  auto options = Options::From("aidl -I . --lang=java -o out p/IFoo.aidl");
  CaptureStderr();
  EXPECT_FALSE(compile_aidl(options, io_delegate_));
  EXPECT_THAT(GetCapturedStderr(), HasSubstr("'void' is an invalid type for the parameter 'n'"));
}

TEST_F(AidlTest, InterfaceVectorIsAvailableAfterTiramisu) {
  io_delegate_.SetFileContents("p/IFoo.aidl",
                               "package p;\n"
                               "interface IFoo{\n"
                               "  void foo(in IFoo[] n);\n"
                               "  void bar(in List<IFoo> n);\n"
                               "}");
  CaptureStderr();
  EXPECT_FALSE(
      compile_aidl(Options::From("aidl -I . --lang=java --min_sdk_version 30 -o out p/IFoo.aidl"),
                   io_delegate_));
  auto captured_stderr = GetCapturedStderr();
  EXPECT_THAT(captured_stderr, HasSubstr("Array of interfaces is available since"));
  EXPECT_THAT(captured_stderr, HasSubstr("List of interfaces is available since"));

  CaptureStderr();
  EXPECT_TRUE(compile_aidl(
      Options::From("aidl -I . --lang=java --min_sdk_version Tiramisu -o out p/IFoo.aidl"),
      io_delegate_));
  EXPECT_EQ(GetCapturedStderr(), "");
}

TEST_F(AidlTest, RustNameOf_PfdFixedArray) {
  auto pfd = typenames_.MakeResolvedType(AIDL_LOCATION_HERE, "ParcelFileDescriptor", false);
  ASSERT_TRUE(pfd->MakeArray(FixedSizeArray{
      std::unique_ptr<AidlConstantValue>(AidlConstantValue::Integral(AIDL_LOCATION_HERE, "2"))}));
  ASSERT_TRUE(pfd->MakeArray(FixedSizeArray{
      std::unique_ptr<AidlConstantValue>(AidlConstantValue::Integral(AIDL_LOCATION_HERE, "3"))}));
  EXPECT_EQ(
      rust::RustNameOf(*pfd, typenames_, rust::StorageMode::PARCELABLE_FIELD, rust::Lifetime::NONE),
      "[[Option<binder::ParcelFileDescriptor>; 3]; 2]");
  EXPECT_EQ(
      rust::RustNameOf(*pfd, typenames_, rust::StorageMode::DEFAULT_VALUE, rust::Lifetime::NONE),
      "[[Option<binder::ParcelFileDescriptor>; 3]; 2]");
  EXPECT_EQ(
      rust::RustNameOf(*pfd, typenames_, rust::StorageMode::IN_ARGUMENT, rust::Lifetime::NONE),
      "&[[binder::ParcelFileDescriptor; 3]; 2]");
  EXPECT_EQ(rust::RustNameOf(*pfd, typenames_, rust::StorageMode::VALUE, rust::Lifetime::NONE),
            "[[binder::ParcelFileDescriptor; 3]; 2]");
}

TEST_F(AidlTest, RustNameOf_PfdDynamicArray) {
  auto pfd = typenames_.MakeResolvedType(AIDL_LOCATION_HERE, "ParcelFileDescriptor", true);
  EXPECT_EQ(
      rust::RustNameOf(*pfd, typenames_, rust::StorageMode::PARCELABLE_FIELD, rust::Lifetime::NONE),
      "Vec<binder::ParcelFileDescriptor>");
  EXPECT_EQ(
      rust::RustNameOf(*pfd, typenames_, rust::StorageMode::DEFAULT_VALUE, rust::Lifetime::NONE),
      "Vec<Option<binder::ParcelFileDescriptor>>");
  // we use UNSIZED_ARGUMENT mode for input argument of dynamic array
  EXPECT_EQ(
      rust::RustNameOf(*pfd, typenames_, rust::StorageMode::UNSIZED_ARGUMENT, rust::Lifetime::NONE),
      "&[binder::ParcelFileDescriptor]");
  EXPECT_EQ(rust::RustNameOf(*pfd, typenames_, rust::StorageMode::VALUE, rust::Lifetime::NONE),
            "Vec<binder::ParcelFileDescriptor>");
}

struct TypeParam {
  string kind;
  string literal;
};

const TypeParam kTypeParams[] = {
    {"primitive", "int"},
    {"primitiveArray", "int[]"},
    {"primitiveFixedArray", "int[3]"},
    {"String", "String"},
    {"StringArray", "String[]"},
    {"IBinder", "IBinder"},
    {"ParcelFileDescriptor", "ParcelFileDescriptor"},
    {"parcelable", "a.Foo"},
    {"enum", "a.Enum"},
    {"union", "a.Union"},
    {"interface", "a.IBar"},
};

struct ExpectedResult {
  string expected_error;
  string expected_error_for_nullable;
};

const std::map<std::string, ExpectedResult> kListSupportExpectations = {
    {"cpp_primitive", {"A generic type cannot", "A generic type cannot"}},
    {"java_primitive", {"A generic type cannot", "A generic type cannot"}},
    {"ndk_primitive", {"A generic type cannot", "A generic type cannot"}},
    {"rust_primitive", {"A generic type cannot", "A generic type cannot"}},
    {"cpp_primitiveArray", {"List of arrays is not supported", "List of arrays is not supported"}},
    {"java_primitiveArray", {"List of arrays is not supported", "List of arrays is not supported"}},
    {"ndk_primitiveArray", {"List of arrays is not supported", "List of arrays is not supported"}},
    {"rust_primitiveArray", {"List of arrays is not supported", "List of arrays is not supported"}},
    {"cpp_primitiveFixedArray",
     {"List of arrays is not supported", "List of arrays is not supported"}},
    {"java_primitiveFixedArray",
     {"List of arrays is not supported", "List of arrays is not supported"}},
    {"ndk_primitiveFixedArray",
     {"List of arrays is not supported", "List of arrays is not supported"}},
    {"rust_primitiveFixedArray",
     {"List of arrays is not supported", "List of arrays is not supported"}},
    {"cpp_String", {"", ""}},
    {"java_String", {"", ""}},
    {"ndk_String", {"", ""}},
    {"rust_String", {"", ""}},
    {"cpp_StringArray", {"List of arrays is not supported", "List of arrays is not supported"}},
    {"java_StringArray", {"List of arrays is not supported", "List of arrays is not supported"}},
    {"ndk_StringArray", {"List of arrays is not supported", "List of arrays is not supported"}},
    {"rust_StringArray", {"List of arrays is not supported", "List of arrays is not supported"}},
    {"cpp_IBinder", {"", ""}},
    {"java_IBinder", {"", ""}},
    {"ndk_IBinder", {"", ""}},
    {"rust_IBinder", {"", ""}},
    {"cpp_ParcelFileDescriptor", {"", ""}},
    {"java_ParcelFileDescriptor", {"", ""}},
    {"ndk_ParcelFileDescriptor", {"", ""}},
    {"rust_ParcelFileDescriptor", {"", ""}},
    {"cpp_interface", {"", ""}},
    {"java_interface", {"", ""}},
    {"ndk_interface", {"", ""}},
    {"rust_interface", {"", ""}},
    {"cpp_parcelable", {"", ""}},
    {"java_parcelable", {"", ""}},
    {"ndk_parcelable", {"", ""}},
    {"rust_parcelable", {"", ""}},
    {"cpp_enum", {"A generic type cannot", "A generic type cannot"}},
    {"java_enum", {"A generic type cannot", "A generic type cannot"}},
    {"ndk_enum", {"A generic type cannot", "A generic type cannot"}},
    {"rust_enum", {"A generic type cannot", "A generic type cannot"}},
    {"cpp_union", {"", ""}},
    {"java_union", {"", ""}},
    {"ndk_union", {"", ""}},
    {"rust_union", {"", ""}},
};

const std::map<std::string, ExpectedResult> kArraySupportExpectations = {
    {"cpp_primitive", {"", ""}},
    {"java_primitive", {"", ""}},
    {"ndk_primitive", {"", ""}},
    {"rust_primitive", {"", ""}},
    {"cpp_primitiveArray",
     {"Multi-dimensional arrays must be fixed size.",
      "Multi-dimensional arrays must be fixed size."}},
    {"java_primitiveArray",
     {"Multi-dimensional arrays must be fixed size.",
      "Multi-dimensional arrays must be fixed size."}},
    {"ndk_primitiveArray",
     {"Multi-dimensional arrays must be fixed size.",
      "Multi-dimensional arrays must be fixed size."}},
    {"rust_primitiveArray",
     {"Multi-dimensional arrays must be fixed size.",
      "Multi-dimensional arrays must be fixed size."}},
    {"cpp_primitiveFixedArray",
     {"Multi-dimensional arrays must be fixed size.",
      "Multi-dimensional arrays must be fixed size."}},
    {"java_primitiveFixedArray",
     {"Multi-dimensional arrays must be fixed size.",
      "Multi-dimensional arrays must be fixed size."}},
    {"ndk_primitiveFixedArray",
     {"Multi-dimensional arrays must be fixed size.",
      "Multi-dimensional arrays must be fixed size."}},
    {"rust_primitiveFixedArray",
     {"Multi-dimensional arrays must be fixed size.",
      "Multi-dimensional arrays must be fixed size."}},
    {"cpp_String", {"", ""}},
    {"java_String", {"", ""}},
    {"ndk_String", {"", ""}},
    {"rust_String", {"", ""}},
    {"cpp_StringArray",
     {"Multi-dimensional arrays must be fixed size.",
      "Multi-dimensional arrays must be fixed size."}},
    {"java_StringArray",
     {"Multi-dimensional arrays must be fixed size.",
      "Multi-dimensional arrays must be fixed size."}},
    {"ndk_StringArray",
     {"Multi-dimensional arrays must be fixed size.",
      "Multi-dimensional arrays must be fixed size."}},
    {"rust_StringArray",
     {"Multi-dimensional arrays must be fixed size.",
      "Multi-dimensional arrays must be fixed size."}},
    {"cpp_IBinder", {"", ""}},
    {"java_IBinder", {"", ""}},
    {"ndk_IBinder", {"", ""}},
    {"rust_IBinder", {"", ""}},
    {"cpp_ParcelFileDescriptor", {"", ""}},
    {"java_ParcelFileDescriptor", {"", ""}},
    {"ndk_ParcelFileDescriptor", {"", ""}},
    {"rust_ParcelFileDescriptor", {"", ""}},
    {"cpp_interface", {"", ""}},
    {"java_interface", {"", ""}},
    {"ndk_interface", {"", ""}},
    {"rust_interface", {"", ""}},
    {"cpp_parcelable", {"", ""}},
    {"java_parcelable", {"", ""}},
    {"ndk_parcelable", {"", ""}},
    {"rust_parcelable", {"", ""}},
    {"cpp_enum", {"", ""}},
    {"java_enum", {"", ""}},
    {"ndk_enum", {"", ""}},
    {"rust_enum", {"", ""}},
    {"cpp_union", {"", ""}},
    {"java_union", {"", ""}},
    {"ndk_union", {"", ""}},
    {"rust_union", {"", ""}},
};

const std::map<std::string, ExpectedResult> kFieldSupportExpectations = {
    {"cpp_primitive", {"", "cannot get nullable annotation"}},
    {"java_primitive", {"", "cannot get nullable annotation"}},
    {"ndk_primitive", {"", "cannot get nullable annotation"}},
    {"rust_primitive", {"", "cannot get nullable annotation"}},
    {"cpp_primitiveArray", {"", ""}},
    {"java_primitiveArray", {"", ""}},
    {"ndk_primitiveArray", {"", ""}},
    {"rust_primitiveArray", {"", ""}},
    {"cpp_primitiveFixedArray", {"", ""}},
    {"java_primitiveFixedArray", {"", ""}},
    {"ndk_primitiveFixedArray", {"", ""}},
    {"rust_primitiveFixedArray", {"", ""}},
    {"cpp_String", {"", ""}},
    {"java_String", {"", ""}},
    {"ndk_String", {"", ""}},
    {"rust_String", {"", ""}},
    {"cpp_StringArray", {"", ""}},
    {"java_StringArray", {"", ""}},
    {"ndk_StringArray", {"", ""}},
    {"rust_StringArray", {"", ""}},
    {"cpp_IBinder", {"", ""}},
    {"java_IBinder", {"", ""}},
    {"ndk_IBinder", {"", ""}},
    {"rust_IBinder", {"", ""}},
    {"cpp_ParcelFileDescriptor", {"", ""}},
    {"java_ParcelFileDescriptor", {"", ""}},
    {"ndk_ParcelFileDescriptor", {"", ""}},
    {"rust_ParcelFileDescriptor", {"", ""}},
    {"cpp_interface", {"", ""}},
    {"java_interface", {"", ""}},
    {"ndk_interface", {"", ""}},
    {"rust_interface", {"", ""}},
    {"cpp_parcelable", {"", ""}},
    {"java_parcelable", {"", ""}},
    {"ndk_parcelable", {"", ""}},
    {"rust_parcelable", {"", ""}},
    {"cpp_enum", {"", "cannot get nullable annotation"}},
    {"java_enum", {"", "cannot get nullable annotation"}},
    {"ndk_enum", {"", "cannot get nullable annotation"}},
    {"rust_enum", {"", "cannot get nullable annotation"}},
    {"cpp_union", {"", ""}},
    {"java_union", {"", ""}},
    {"ndk_union", {"", ""}},
    {"rust_union", {"", ""}},
};

class AidlTypeParamTest
    : public testing::TestWithParam<std::tuple<Options::Language, TypeParam, bool>> {
 public:
  void Run(const std::string& generic_type_decl,
           const std::map<std::string, ExpectedResult>& expectations) {
    const auto& param = GetParam();
    const auto& lang = to_string(std::get<0>(param));
    const auto& kind = std::get<1>(param).kind;
    const bool nullable = std::get<2>(param);

    FakeIoDelegate io;
    io.SetFileContents("a/IBar.aidl", "package a; interface IBar { }");
    io.SetFileContents("a/Enum.aidl", "package a; enum Enum { A }");
    io.SetFileContents("a/Union.aidl", "package a; union Union { int a; }");
    io.SetFileContents("a/Foo.aidl", "package a; parcelable Foo { int a; }");
    std::string decl = fmt::format(fmt::runtime(generic_type_decl), std::get<1>(param).literal);
    if (nullable) {
      decl = "@nullable " + decl;
    }
    io.SetFileContents("a/Target.aidl", "package a; parcelable Target { " + decl + " f; }");

    const auto options = Options::From(fmt::format(
        "aidl -I . --min_sdk_version current --lang={} a/Target.aidl -o out -h out", lang));
    CaptureStderr();
    compile_aidl(options, io);
    auto it = expectations.find(lang + "_" + kind);
    ASSERT_TRUE(it != expectations.end()) << "missing expectation for " << lang << "_" << kind;
    const string err = GetCapturedStderr();
    const string expected_error =
        nullable ? it->second.expected_error_for_nullable : it->second.expected_error;
    if (expected_error.empty()) {
      EXPECT_EQ("", err);
    } else {
      EXPECT_THAT(err, testing::HasSubstr(expected_error));
    }
  }
};

INSTANTIATE_TEST_SUITE_P(
    AidlTestSuite, AidlTypeParamTest,
    testing::Combine(testing::Values(Options::Language::CPP, Options::Language::JAVA,
                                     Options::Language::NDK, Options::Language::RUST),
                     testing::ValuesIn(kTypeParams), testing::Values(true, false)),
    [](const testing::TestParamInfo<std::tuple<Options::Language, TypeParam, bool>>& info) {
      string name = to_string(std::get<0>(info.param)) + "_" + std::get<1>(info.param).kind;
      if (std::get<2>(info.param)) {
        name += "_nullable";
      }
      return name;
    });

TEST_P(AidlTypeParamTest, ListSupportedTypes) {
  Run("List<{}>", kListSupportExpectations);
}

TEST_P(AidlTypeParamTest, ArraySupportedTypes) {
  Run("{}[]", kArraySupportExpectations);
}

TEST_P(AidlTypeParamTest, ParcelableFieldTypes) {
  Run("{}", kFieldSupportExpectations);
}

}  // namespace aidl
}  // namespace android
