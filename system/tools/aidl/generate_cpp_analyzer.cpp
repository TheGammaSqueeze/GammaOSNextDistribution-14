/*
 * Copyright (C) 2022, The Android Open Source Project
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

#include "generate_cpp_analyzer.h"

#include <string>
#include "aidl.h"
#include "aidl_language.h"
#include "aidl_to_common.h"
#include "aidl_to_cpp.h"
#include "code_writer.h"
#include "logging.h"

using std::string;
using std::unique_ptr;

namespace android {
namespace aidl {
namespace cpp {
namespace {

const char kAndroidStatusVarName[] = "_aidl_ret_status";
const char kReturnVarName[] = "_aidl_return";
const char kDataVarName[] = "_aidl_data";
const char kReplyVarName[] = "_aidl_reply";

void GenerateAnalyzerTransaction(CodeWriter& out, const AidlInterface& interface,
                                 const AidlMethod& method, const AidlTypenames& typenames,
                                 const Options& options) {
  // Reading past the interface descriptor and reply binder status
  out << "_aidl_ret_status = ::android::OK;\n";
  out.Write("if (!(%s.enforceInterface(android::String16(\"%s\")))) {\n", kDataVarName,
            interface.GetDescriptor().c_str());
  out.Write("  %s = ::android::BAD_TYPE;\n", kAndroidStatusVarName);
  out << "  std::cout << \"  Failure: Parcel interface does not match.\" << std::endl;\n"
      << "  break;\n"
      << "}\n";

  // Declare parameters
  for (const unique_ptr<AidlArgument>& a : method.GetArguments()) {
    out.Write("%s %s;\n", CppNameOf(a->GetType(), typenames).c_str(), BuildVarName(*a).c_str());
  }
  out << "::android::binder::Status binderStatus;\n";
  // Declare and read the return value.
  // Read past the binder status.
  out.Write("binderStatus.readFromParcel(%s);\n", kReplyVarName);
  if (method.GetType().GetName() != "void") {
    out.Write("%s %s;\n", CppNameOf(method.GetType(), typenames).c_str(), kReturnVarName);
    out.Write("bool returnError = false;\n");
  }

  // Read Reply
  if (method.GetType().GetName() != "void") {
    out.Write("%s = %s.%s(%s);\n", kAndroidStatusVarName, kReplyVarName,
              ParcelReadMethodOf(method.GetType(), typenames).c_str(),
              ParcelReadCastOf(method.GetType(), typenames, string("&") + kReturnVarName).c_str());
    out.Write("if (((%s) != (android::NO_ERROR))) {\n", kAndroidStatusVarName);
    out.Indent();
    out.Write(
        "std::cerr << \"Failure: error in reading return value from Parcel.\" << std::endl;\n");
    out.Write("returnError = true;\n");
    out.Dedent();
    out.Write("}\n");
  }

  // Reading arguments
  out << "do { // Single-pass loop to break if argument reading fails\n";
  out.Indent();
  for (const auto& a : method.GetArguments()) {
    out.Write("%s = %s.%s(%s);\n", kAndroidStatusVarName, kDataVarName,
              ParcelReadMethodOf(a->GetType(), typenames).c_str(),
              ParcelReadCastOf(a->GetType(), typenames, "&" + BuildVarName(*a)).c_str());
    out.Write("if (((%s) != (android::NO_ERROR))) {\n", kAndroidStatusVarName);
    out.Indent();
    out.Write("std::cerr << \"Failure: error in reading argument %s from Parcel.\" << std::endl;\n",
              a->GetName().c_str());
    out.Dedent();
    out.Write("  break;\n}\n");
  }
  out.Dedent();
  out << "} while(false);\n";

  if (!method.GetArguments().empty() && options.GetMinSdkVersion() >= SDK_VERSION_Tiramisu) {
    out.Write(
        "if (!%s.enforceNoDataAvail().isOk()) {\n  %s = android::BAD_VALUE;\n  std::cout << \"  "
        "Failure: Parcel has too much data.\" << std::endl;\n  break;\n}\n",
        kDataVarName, kAndroidStatusVarName);
  }

  // Arguments
  out.Write("std::cout << \"  arguments: \" << std::endl;\n");
  for (const auto& a : method.GetArguments()) {
    out.Write(
        "std::cout << \"    %s: \" << ::android::internal::ToString(%s) "
        "<< std::endl;\n",
        a->GetName().c_str(), BuildVarName(*a).c_str());
  }

  // Return Value
  if (method.GetType().GetName() != "void") {
    out.Write("if (returnError) {\n");
    out.Indent();
    out.Write("std::cout << \"  return: <error>\" << std::endl;\n");
    out.Dedent();
    out.Write("} else {");
    out.Indent();
    out.Write("std::cout << \"  return: \" << ::android::internal::ToString(%s) << std::endl;\n",
              kReturnVarName);
    out.Dedent();
    out.Write("}\n");
  } else {
    out.Write("std::cout << \"  return: void\" << std::endl;\n");
  }
}

void GenerateAnalyzerSource(CodeWriter& out, const AidlDefinedType& defined_type,
                            const AidlTypenames& typenames, const Options& options) {
  auto interface = AidlCast<AidlInterface>(defined_type);
  string q_name = GetQualifiedName(*interface, ClassNames::INTERFACE);

  string canonicalName = defined_type.GetCanonicalName();
  string interfaceName = defined_type.GetName();

  // Includes
  vector<string> include_list{
      "iostream", "binder/Parcel.h", "android/binder_to_string.h",
      HeaderFile(*interface, ClassNames::RAW, false),
      // HeaderFile(*interface, ClassNames::INTERFACE, false),
  };
  for (const auto& include : include_list) {
    out << "#include <" << include << ">\n";
  }

  out << "namespace {\n";
  // Function Start
  out.Write(
      "android::status_t analyze%s(uint32_t _aidl_code, const android::Parcel& %s, const "
      "android::Parcel& %s) {\n",
      q_name.c_str(), kDataVarName, kReplyVarName);
  out.Indent();
  out.Write("android::status_t %s;\nswitch(_aidl_code) {\n", kAndroidStatusVarName);
  out.Indent();

  // Main Switch Statement
  for (const auto& method : interface->GetMethods()) {
    out.Write("case ::android::IBinder::FIRST_CALL_TRANSACTION + %d:\n{\n", (method->GetId()));
    out.Indent();
    out.Write("std::cout << \"%s.%s()\" << std::endl;\n", interfaceName.c_str(),
              method->GetName().c_str());
    GenerateAnalyzerTransaction(out, *interface, *method, typenames, options);
    out.Dedent();
    out << "}\n";
    out << "break;\n";
  }
  out << "default:\n{\n  std::cout << \"  Transaction code \" << _aidl_code << \" not known.\" << "
         "std::endl;\n";
  out.Write("%s = android::UNKNOWN_TRANSACTION;\n}\n", kAndroidStatusVarName);
  out.Dedent();
  out.Write("}\nreturn %s;\n", kAndroidStatusVarName);
  out << "// To prevent unused variable warnings\n";
  out.Write("(void)%s; (void)%s; (void)%s;\n", kAndroidStatusVarName, kDataVarName, kReplyVarName);
  out.Dedent();
  out << "}\n\n} // namespace\n";

  out << "\n#include <Analyzer.h>\nusing android::aidl::Analyzer;\n";
  out.Write(
      "__attribute__((constructor)) static void addAnalyzer() {\n"
      "  Analyzer::installAnalyzer(std::make_unique<Analyzer>(\"%s\", \"%s\", &analyze%s));\n}\n",
      canonicalName.c_str(), interfaceName.c_str(), q_name.c_str());
}

void GenerateAnalyzerPlaceholder(CodeWriter& out, const AidlDefinedType& /*defined_type*/,
                                 const AidlTypenames& /*typenames*/, const Options& /*options*/) {
  out << "// This file is intentionally left blank as placeholder for building an analyzer.\n";
}

}  // namespace

bool GenerateCppAnalyzer(const string& output_file, const Options& options,
                         const AidlTypenames& typenames, const AidlDefinedType& defined_type,
                         const IoDelegate& io_delegate) {
  if (!ValidateOutputFilePath(output_file, options, defined_type)) {
    return false;
  }

  using GenFn = void (*)(CodeWriter & out, const AidlDefinedType& defined_type,
                         const AidlTypenames& typenames, const Options& options);
  auto gen = [&](auto file, GenFn fn) {
    unique_ptr<CodeWriter> writer(io_delegate.GetCodeWriter(file));

    GenerateAutoGenHeader(*writer, options);

    fn(*writer, defined_type, typenames, options);
    AIDL_FATAL_IF(!writer->Close(), defined_type) << "I/O Error!";
    return true;
  };

  if (AidlCast<AidlInterface>(defined_type)) {
    return gen(output_file, &GenerateAnalyzerSource);
  } else {
    return gen(output_file, &GenerateAnalyzerPlaceholder);
  }
}

}  // namespace cpp
}  // namespace aidl
}  // namespace android
