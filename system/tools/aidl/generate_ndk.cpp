/*
 * Copyright (C) 2018, The Android Open Source Project
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

#include "generate_ndk.h"

#include "aidl.h"
#include "aidl_language.h"
#include "aidl_to_common.h"
#include "aidl_to_cpp_common.h"
#include "aidl_to_ndk.h"
#include "logging.h"

#include <android-base/stringprintf.h>

namespace android {
namespace aidl {
namespace ndk {

static constexpr const char* kDescriptor = "descriptor";
static constexpr const char* kVersion = "version";
static constexpr const char* kHash = "hash";
static constexpr const char* kCachedVersion = "_aidl_cached_version";
static constexpr const char* kCachedHash = "_aidl_cached_hash";
static constexpr const char* kCachedHashMutex = "_aidl_cached_hash_mutex";

namespace internals {
// 4 outputs for NDK for each type: Header, BpHeader, BnHeader, Source
void GenerateHeader(CodeWriter& out, const AidlTypenames& types,
                    const AidlDefinedType& defined_type, const Options& options);
void GenerateClientHeader(CodeWriter& out, const AidlTypenames& types,
                          const AidlDefinedType& defined_type, const Options& options);
void GenerateServerHeader(CodeWriter& out, const AidlTypenames& types,
                          const AidlDefinedType& defined_type, const Options& options);
void GenerateSource(CodeWriter& out, const AidlTypenames& types,
                    const AidlDefinedType& defined_type, const Options& options);

// Header contains ClassDecl of a root type
void GenerateInterfaceClassDecl(CodeWriter& out, const AidlTypenames& types,
                                const AidlInterface& iface, const Options& options);
void GenerateParcelClassDecl(CodeWriter& out, const AidlTypenames& types,
                             const AidlStructuredParcelable& parcelable, const Options& options);
void GenerateParcelClassDecl(CodeWriter& out, const AidlTypenames& types,
                             const AidlUnionDecl& union_decl, const Options& options);
void GenerateEnumClassDecl(CodeWriter& out, const AidlTypenames& types,
                           const AidlEnumDeclaration& enum_decl, const Options& options);

// BpHeader/BnHeader for interface
void GenerateClientHeader(CodeWriter& out, const AidlTypenames& types,
                          const AidlInterface& defined_type, const Options& options);
void GenerateServerHeader(CodeWriter& out, const AidlTypenames& types,
                          const AidlInterface& defined_type, const Options& options);

// Source for interface
void GenerateSource(CodeWriter& out, const AidlTypenames& types, const AidlInterface& iface,
                    const Options& options);
void GenerateClassSource(CodeWriter& out, const AidlTypenames& types,
                         const AidlInterface& defined_type, const Options& options);
void GenerateClientSource(CodeWriter& out, const AidlTypenames& types,
                          const AidlInterface& defined_type, const Options& options);
void GenerateServerSource(CodeWriter& out, const AidlTypenames& types,
                          const AidlInterface& defined_type, const Options& options);
void GenerateInterfaceSource(CodeWriter& out, const AidlTypenames& types,
                             const AidlInterface& defined_type, const Options& options);

// Source for structured parcelable
void GenerateParcelSource(CodeWriter& out, const AidlTypenames& types,
                          const AidlStructuredParcelable& defined_type, const Options& options);

// Source for union
void GenerateParcelSource(CodeWriter& out, const AidlTypenames& types,
                          const AidlUnionDecl& defined_type, const Options& options);

// Compare headers so that <android/..> and <aidl/..> comes after other (probably) std headers.
struct HeaderComp {
  static int rank(const string& a) {
    return base::StartsWith(a, "aidl/") ? 2 : base::StartsWith(a, "android/") ? 1 : 0;
  }
  bool operator()(const string& a, const string& b) const {
    return std::make_tuple(rank(a), a) < std::make_tuple(rank(b), b);
  }
};
}  // namespace internals

using namespace internals;
using cpp::ClassNames;
using cpp::GetQualifiedName;

void GenerateNdk(const string& output_file, const Options& options, const AidlTypenames& types,
                 const AidlDefinedType& defined_type, const IoDelegate& io_delegate) {
  using GenFn = void (*)(CodeWriter & out, const AidlTypenames& types,
                         const AidlDefinedType& defined_type, const Options& options);
  // Wrap Generate* function to handle CodeWriter for a file.
  auto gen = [&](auto file, GenFn fn) {
    unique_ptr<CodeWriter> writer(io_delegate.GetCodeWriter(file));

    GenerateAutoGenHeader(*writer, options);

    fn(*writer, types, defined_type, options);
    AIDL_FATAL_IF(!writer->Close(), defined_type) << "I/O Error!";
  };

  gen(options.OutputHeaderDir() + NdkHeaderFile(defined_type, ClassNames::RAW), &GenerateHeader);
  gen(options.OutputHeaderDir() + NdkHeaderFile(defined_type, ClassNames::CLIENT),
      &GenerateClientHeader);
  gen(options.OutputHeaderDir() + NdkHeaderFile(defined_type, ClassNames::SERVER),
      &GenerateServerHeader);
  gen(output_file, &GenerateSource);
}

namespace internals {

void EnterNdkNamespace(CodeWriter& out, const AidlDefinedType& defined_type) {
  out << "namespace aidl {\n";
  cpp::EnterNamespace(out, defined_type);
}
void LeaveNdkNamespace(CodeWriter& out, const AidlDefinedType& defined_type) {
  cpp::LeaveNamespace(out, defined_type);
  out << "}  // namespace aidl\n";
}

static void StatusCheckGoto(CodeWriter& out) {
  out << "if (_aidl_ret_status != STATUS_OK) goto _aidl_error;\n\n";
}
static void StatusCheckBreak(CodeWriter& out) {
  out << "if (_aidl_ret_status != STATUS_OK) break;\n\n";
}
static void StatusCheckReturn(CodeWriter& out) {
  out << "if (_aidl_ret_status != STATUS_OK) return _aidl_ret_status;\n\n";
}

void GenerateHeaderIncludes(CodeWriter& out, const AidlTypenames& types,
                            const AidlDefinedType& defined_type, const Options& options);

void GenerateClassDecl(CodeWriter& out, const AidlTypenames& types,
                       const AidlDefinedType& defined_type, const Options& options);

void GenerateNestedTypeDecls(CodeWriter& out, const AidlTypenames& types,
                             const AidlDefinedType& defined_type, const Options& options) {
  auto visit = [&](const auto& nested) { GenerateClassDecl(out, types, nested, options); };
  AIDL_FATAL_IF(!TopologicalVisit(defined_type.GetNestedTypes(), visit), defined_type)
      << "Cycle detected.";
}

void GenerateHeaderDefinitions(CodeWriter& out, const AidlTypenames& types,
                               const AidlDefinedType& defined_type, const Options& options) {
  struct Visitor : AidlVisitor {
    CodeWriter& out;
    const AidlTypenames& types;
    const Options& options;
    Visitor(CodeWriter& out, const AidlTypenames& types, const Options& options)
        : out(out), types(types), options(options) {}

    void Visit(const AidlEnumDeclaration& enum_decl) override {
      const auto backing_type = NdkNameOf(types, enum_decl.GetBackingType(), StorageMode::STACK);
      EnterNdkNamespace(out, enum_decl);
      out << cpp::GenerateEnumToString(enum_decl, backing_type);
      LeaveNdkNamespace(out, enum_decl);

      out << "namespace ndk {\n";
      out << "namespace internal {\n";
      out << cpp::GenerateEnumValues(enum_decl, {"aidl"});
      out << "}  // namespace internal\n";
      out << "}  // namespace ndk\n";
    }

    void Visit(const AidlStructuredParcelable& parcelable) override {
      if (parcelable.IsGeneric()) {
        GenerateParcelSource(out, types, parcelable, options);
      }
    }

    void Visit(const AidlUnionDecl& union_decl) override {
      if (union_decl.IsGeneric()) {
        GenerateParcelSource(out, types, union_decl, options);
      }
    }

  } v(out, types, options);
  VisitTopDown(v, defined_type);
}

void GenerateHeader(CodeWriter& out, const AidlTypenames& types,
                    const AidlDefinedType& defined_type, const Options& options) {
  out << "#pragma once\n\n";
  GenerateHeaderIncludes(out, types, defined_type, options);
  cpp::GenerateForwardDecls(out, defined_type, true);
  EnterNdkNamespace(out, defined_type);
  GenerateClassDecl(out, types, defined_type, options);
  LeaveNdkNamespace(out, defined_type);
  GenerateHeaderDefinitions(out, types, defined_type, options);
}

void GenerateClientHeader(CodeWriter& out, const AidlTypenames& types,
                          const AidlDefinedType& defined_type, const Options& options) {
  if (auto iface = AidlCast<AidlInterface>(defined_type); iface) {
    GenerateClientHeader(out, types, *iface, options);
  } else if (auto parcelable = AidlCast<AidlStructuredParcelable>(defined_type); parcelable) {
    out << "#error TODO(b/111362593) defined_types do not have bp classes\n";
  } else if (auto union_decl = AidlCast<AidlUnionDecl>(defined_type); union_decl) {
    out << "#error TODO(b/111362593) defined_types do not have bp classes\n";
  } else if (auto enum_decl = AidlCast<AidlEnumDeclaration>(defined_type); enum_decl) {
    out << "#error TODO(b/111362593) enums do not have bp classes\n";
  } else {
    AIDL_FATAL(defined_type) << "Unrecognized type sent for NDK generation.";
  }
}

void GenerateServerHeader(CodeWriter& out, const AidlTypenames& types,
                          const AidlDefinedType& defined_type, const Options& options) {
  if (auto iface = AidlCast<AidlInterface>(defined_type); iface) {
    GenerateServerHeader(out, types, *iface, options);
  } else if (auto parcelable = AidlCast<AidlStructuredParcelable>(defined_type); parcelable) {
    out << "#error TODO(b/111362593) defined_types do not have bn classes\n";
  } else if (auto union_decl = AidlCast<AidlUnionDecl>(defined_type); union_decl) {
    out << "#error TODO(b/111362593) defined_types do not have bn classes\n";
  } else if (auto enum_decl = AidlCast<AidlEnumDeclaration>(defined_type); enum_decl) {
    out << "#error TODO(b/111362593) enums do not have bn classes\n";
  } else {
    AIDL_FATAL(defined_type) << "Unrecognized type sent for CPP generation.";
  }
}

void GenerateSource(CodeWriter& out, const AidlTypenames& types,
                    const AidlDefinedType& defined_type, const Options& options) {
  struct Visitor : AidlVisitor {
    CodeWriter& out;
    const AidlTypenames& types;
    const Options& options;
    Visitor(CodeWriter& out, const AidlTypenames& types, const Options& options)
        : out(out), types(types), options(options) {}

    void Visit(const AidlInterface& interface) override {
      GenerateSource(out, types, interface, options);
    }

    void Visit(const AidlStructuredParcelable& parcelable) override {
      if (!parcelable.IsGeneric()) {
        GenerateParcelSource(out, types, parcelable, options);
      }
    }

    void Visit(const AidlUnionDecl& union_decl) override {
      if (!union_decl.IsGeneric()) {
        GenerateParcelSource(out, types, union_decl, options);
      }
    }

    void Visit(const AidlEnumDeclaration& enum_decl) override {
      if (!enum_decl.GetParentType()) {
        out << "// This file is intentionally left blank as placeholder for enum declaration.\n";
      }
    }
  } v(out, types, options);
  VisitTopDown(v, defined_type);
}

void GenerateHeaderIncludes(CodeWriter& out, const AidlTypenames& types,
                            const AidlDefinedType& defined_type, const Options& options) {
  // Collect implementation related headers and referenced defined types by recursivly visiting
  // nested types
  struct Visitor : AidlVisitor {
    const AidlTypenames& types;
    const Options& options;
    std::set<std::string, HeaderComp> includes;
    Visitor(const AidlTypenames& types, const Options& options) : types(types), options(options) {}

    // Collect includes for each type reference
    void Visit(const AidlTypeSpecifier& type) override {
      auto defined = type.GetDefinedType();
      if (!defined) return;
      includes.insert(NdkHeaderFile(*defined, ClassNames::RAW, false /*use_os_sep*/));
    }

    // Collect implementation-specific includes for each type definition
    void Visit(const AidlInterface& interface) override {
      AddCommonHeaders(interface);
      includes.insert("android/binder_interface_utils.h");
      if (options.GenLog()) {
        includes.insert("functional");
        includes.insert("chrono");
        includes.insert("sstream");
      }
      // For nested interfaces client/server classes are defined in the same header.
      // So we need includes for client/server class as well.
      if (interface.GetParentType()) {
        includes.insert("android/binder_ibinder.h");
        if (options.GenTraces()) {
          includes.insert("android/trace.h");
        }
      }
    }

    void Visit(const AidlStructuredParcelable& parcelable) override {
      AddCommonHeaders(parcelable);
      includes.insert("android/binder_interface_utils.h");
      includes.insert("android/binder_parcelable_utils.h");
      includes.insert("android/binder_to_string.h");  // used by toString()
    }

    void Visit(const AidlUnionDecl& union_decl) override {
      AddCommonHeaders(union_decl);
      includes.insert("android/binder_interface_utils.h");
      includes.insert("android/binder_parcelable_utils.h");
      includes.insert("android/binder_to_string.h");  // used by toString()
      auto union_headers = cpp::UnionWriter::GetHeaders(union_decl);
      includes.insert(std::begin(union_headers), std::end(union_headers));
    }

    void Visit(const AidlEnumDeclaration& enum_decl) override {
      AddCommonHeaders(enum_decl);
      includes.insert("array");
      includes.insert("android/binder_enums.h");
    }

    void AddCommonHeaders(const AidlDefinedType& defined_type) {
      includes.insert("cstdint");
      includes.insert("memory");
      includes.insert("optional");
      includes.insert("string");
      includes.insert("vector");
      if (defined_type.IsSensitiveData()) {
        includes.insert("android/binder_parcel_platform.h");
        includes.insert("android/binder_ibinder_platform.h");
      }
    }
  } v(types, options);
  VisitTopDown(v, defined_type);

  for (const auto& path : v.includes) {
    out << "#include <" << path << ">\n";
  }
  out << "#ifdef BINDER_STABILITY_SUPPORT\n";
  out << "#include <android/binder_stability.h>\n";
  out << "#endif  // BINDER_STABILITY_SUPPORT\n";
  out << "\n";

  if (v.includes.count("cassert")) {
    // TODO(b/31559095) bionic on host should define __assert2
    out << "#ifndef __BIONIC__\n#define __assert2(a,b,c,d) ((void)0)\n#endif\n\n";
  }
}

void GenerateClassDecl(CodeWriter& out, const AidlTypenames& types,
                       const AidlDefinedType& defined_type, const Options& options) {
  if (auto iface = AidlCast<AidlInterface>(defined_type); iface) {
    GenerateInterfaceClassDecl(out, types, *iface, options);
  } else if (auto parcelable = AidlCast<AidlStructuredParcelable>(defined_type); parcelable) {
    GenerateParcelClassDecl(out, types, *parcelable, options);
  } else if (auto union_decl = AidlCast<AidlUnionDecl>(defined_type); union_decl) {
    GenerateParcelClassDecl(out, types, *union_decl, options);
  } else if (auto enum_decl = AidlCast<AidlEnumDeclaration>(defined_type); enum_decl) {
    GenerateEnumClassDecl(out, types, *enum_decl, options);
  } else {
    AIDL_FATAL(defined_type) << "Unrecognized type sent for NDK generation.";
  }
}

static void GenerateSourceIncludes(CodeWriter& out, const AidlTypenames& types,
                                   const AidlDefinedType& defined_type, const Options& options) {
  // Once in a .cpp file
  if (defined_type.GetParentType() != nullptr) {
    return;
  }

  const string self_header = NdkHeaderFile(defined_type, ClassNames::RAW, false /*use_os_sep*/);
  out << "#include \"" << self_header << "\"\n";
  out << "\n";

  std::set<std::string, HeaderComp> includes = {self_header};
  includes.insert("android/binder_parcel_utils.h");
  types.IterateTypes([&](const AidlDefinedType& a_defined_type) {
    if (a_defined_type.AsInterface() != nullptr) {
      includes.insert(NdkHeaderFile(a_defined_type, ClassNames::CLIENT, false /*use_os_sep*/));
      includes.insert(NdkHeaderFile(a_defined_type, ClassNames::SERVER, false /*use_os_sep*/));
      includes.insert(NdkHeaderFile(a_defined_type, ClassNames::RAW, false /*use_os_sep*/));
    }
  });

  struct Visitor : AidlVisitor {
    bool has_interface = false;
    void Visit(const AidlInterface&) override { has_interface = true; }
  } v;
  VisitTopDown(v, defined_type);

  if (v.has_interface && options.GenLog()) {
    includes.insert("android/binder_to_string.h");
  }

  // Emit includes except self_header
  includes.erase(includes.find(self_header));
  for (const auto& inc : includes) {
    out << "#include <" << inc << ">\n";
  }
  out << "\n";

  // Emit additional definition for gen_traces
  if (v.has_interface && options.GenTraces()) {
    out << "namespace {\n";
    out << "struct ScopedTrace {\n";
    out.Indent();
    out << "inline explicit ScopedTrace(const char* name) { ATrace_beginSection(name); }\n";
    out << "inline ~ScopedTrace() { ATrace_endSection(); }\n";
    out.Dedent();
    out << "};\n";
    out << "}  // namespace\n";
    out << "\n";
  }
}

static void GenerateConstantDeclarations(CodeWriter& out, const AidlTypenames& types,
                                         const AidlDefinedType& type) {
  for (const auto& constant : type.GetConstantDeclarations()) {
    const AidlTypeSpecifier& type = constant->GetType();

    if (type.Signature() == "String") {
      out << "static const char*";
      cpp::GenerateDeprecated(out, *constant);
      out << " " << constant->GetName() << ";\n";
    } else if (type.Signature() == "float" || type.Signature() == "double") {
      out << "static constexpr " << NdkNameOf(types, type, StorageMode::STACK) << " ";
      out << constant->GetName();
      cpp::GenerateDeprecated(out, *constant);
      out << " = " << constant->ValueString(ConstantValueDecorator) << ";\n";
    } else {
      out << "enum : " << NdkNameOf(types, type, StorageMode::STACK) << " { ";
      out << constant->GetName();
      cpp::GenerateDeprecated(out, *constant);
      out << " = " << constant->ValueString(ConstantValueDecorator) << " };\n";
    }
  }
}

static void GenerateConstantDefinitions(CodeWriter& out, const AidlDefinedType& interface,
                                        const std::string& clazz,
                                        const std::string& tmpl_decl = "") {
  for (const auto& constant : interface.GetConstantDeclarations()) {
    const AidlConstantValue& value = constant->GetValue();
    AIDL_FATAL_IF(value.GetType() == AidlConstantValue::Type::UNARY ||
                      value.GetType() == AidlConstantValue::Type::BINARY,
                  value);
    if (value.GetType() == AidlConstantValue::Type::STRING) {
      out << tmpl_decl;
      out << "const char* " << clazz << "::" << constant->GetName() << " = "
          << constant->ValueString(ConstantValueDecorator) << ";\n";
    }
  }
}

void GenerateSource(CodeWriter& out, const AidlTypenames& types, const AidlInterface& defined_type,
                    const Options& options) {
  GenerateSourceIncludes(out, types, defined_type, options);
  EnterNdkNamespace(out, defined_type);
  GenerateClassSource(out, types, defined_type, options);
  GenerateClientSource(out, types, defined_type, options);
  GenerateServerSource(out, types, defined_type, options);
  GenerateInterfaceSource(out, types, defined_type, options);
  LeaveNdkNamespace(out, defined_type);
}

static std::string MethodId(const AidlMethod& m) {
  return "(FIRST_CALL_TRANSACTION + " + std::to_string(m.GetId()) + " /*" + m.GetName() + "*/)";
}

static void GenerateClientMethodDefinition(CodeWriter& out, const AidlTypenames& types,
                                           const AidlInterface& defined_type,
                                           const AidlMethod& method, const Options& options) {
  const std::string q_name = GetQualifiedName(defined_type, ClassNames::CLIENT);
  const std::string clazz = ClassName(defined_type, ClassNames::CLIENT);

  out << NdkMethodDecl(types, method, q_name) << " {\n";
  out.Indent();
  out << "binder_status_t _aidl_ret_status = STATUS_OK;\n";
  out << "::ndk::ScopedAStatus _aidl_status;\n";

  if (method.GetName() == kGetInterfaceHash && !options.Hash().empty()) {
    out << "const std::lock_guard<std::mutex> lock(" << kCachedHashMutex << ");\n";
    out << "if (" << kCachedHash << " != \"-1\") {\n";
    out.Indent();
    out << "*_aidl_return = " << kCachedHash << ";\n"
        << "_aidl_status.set(AStatus_fromStatus(_aidl_ret_status));\n"
        << "return _aidl_status;\n";
    out.Dedent();
    out << "}\n";
  } else if (method.GetName() == kGetInterfaceVersion && options.Version() > 0) {
    out << "if (" << kCachedVersion << " != -1) {\n";
    out.Indent();
    out << "*_aidl_return = " << kCachedVersion << ";\n"
        << "_aidl_status.set(AStatus_fromStatus(_aidl_ret_status));\n"
        << "return _aidl_status;\n";
    out.Dedent();
    out << "}\n";
  }
  out << "::ndk::ScopedAParcel _aidl_in;\n";
  out << "::ndk::ScopedAParcel _aidl_out;\n";
  out << "\n";

  if (options.GenLog()) {
    out << cpp::GenLogBeforeExecute(q_name, method, false /* isServer */, true /* isNdk */);
  }
  if (options.GenTraces()) {
    out << "ScopedTrace _aidl_trace(\"AIDL::" << to_string(options.TargetLanguage())
        << "::" << ClassName(defined_type, ClassNames::INTERFACE) << "::" << method.GetName()
        << "::client\");\n";
  }

  if (method.IsNew() && ShouldForceDowngradeFor(CommunicationSide::WRITE) &&
      method.IsUserDefined()) {
    out << "if (true) {\n";
    out << "  _aidl_ret_status = STATUS_UNKNOWN_TRANSACTION;\n";
    out << "  goto _aidl_error;\n";
    out << "}\n";
  }

  out << "_aidl_ret_status = AIBinder_prepareTransaction(asBinder().get(), _aidl_in.getR());\n";
  if (defined_type.IsSensitiveData()) {
    out << "AParcel_markSensitive(_aidl_in.get());\n";
  }
  StatusCheckGoto(out);

  for (const auto& arg : method.GetArguments()) {
    const std::string var_name = cpp::BuildVarName(*arg);

    if (arg->IsIn()) {
      out << "_aidl_ret_status = ";
      const std::string prefix = (arg->IsOut() ? "*" : "");
      WriteToParcelFor({out, types, arg->GetType(), "_aidl_in.get()", prefix + var_name});
      out << ";\n";
      StatusCheckGoto(out);
    } else if (arg->IsOut() && arg->GetType().IsDynamicArray()) {
      out << "_aidl_ret_status = ::ndk::AParcel_writeVectorSize(_aidl_in.get(), *" << var_name
          << ");\n";
      StatusCheckGoto(out);
    }
  }
  out << "_aidl_ret_status = AIBinder_transact(\n";
  out.Indent();
  out << "asBinder().get(),\n";
  out << MethodId(method) << ",\n";
  out << "_aidl_in.getR(),\n";
  out << "_aidl_out.getR(),\n";

  std::vector<std::string> flags;
  if (method.IsOneway()) flags.push_back("FLAG_ONEWAY");
  if (defined_type.IsSensitiveData()) flags.push_back("FLAG_CLEAR_BUF");
  out << (flags.empty() ? "0" : base::Join(flags, " | ")) << "\n";

  out << "#ifdef BINDER_STABILITY_SUPPORT\n";
  out << "| FLAG_PRIVATE_LOCAL\n";
  out << "#endif  // BINDER_STABILITY_SUPPORT\n";
  out << ");\n";
  out.Dedent();

  // If the method is not implmented in the server side but the client has
  // provided the default implementation, call it instead of failing hard.
  const std::string iface = ClassName(defined_type, ClassNames::INTERFACE);
  out << "if (_aidl_ret_status == STATUS_UNKNOWN_TRANSACTION && ";
  out << iface << "::getDefaultImpl()) {\n";
  out.Indent();
  out << "_aidl_status = " << iface << "::getDefaultImpl()->" << method.GetName() << "(";
  out << NdkArgList(types, method, FormatArgNameOnly) << ");\n";
  out << "goto _aidl_status_return;\n";
  out.Dedent();
  out << "}\n";

  StatusCheckGoto(out);

  if (!method.IsOneway()) {
    out << "_aidl_ret_status = AParcel_readStatusHeader(_aidl_out.get(), _aidl_status.getR());\n";
    StatusCheckGoto(out);

    out << "if (!AStatus_isOk(_aidl_status.get())) goto _aidl_status_return;\n";
  }

  if (method.GetType().GetName() != "void") {
    out << "_aidl_ret_status = ";
    ReadFromParcelFor({out, types, method.GetType(), "_aidl_out.get()", "_aidl_return"});
    out << ";\n";
    StatusCheckGoto(out);
    if (method.GetName() == kGetInterfaceHash && !options.Hash().empty()) {
      out << kCachedHash << " = *_aidl_return;\n";
    } else if (method.GetName() == kGetInterfaceVersion && options.Version() > 0) {
      out << kCachedVersion << " = *_aidl_return;\n";
    }
  }
  for (const AidlArgument* arg : method.GetOutArguments()) {
    out << "_aidl_ret_status = ";
    ReadFromParcelFor({out, types, arg->GetType(), "_aidl_out.get()", cpp::BuildVarName(*arg)});
    out << ";\n";
    StatusCheckGoto(out);
  }

  out << "_aidl_error:\n";
  out << "_aidl_status.set(AStatus_fromStatus(_aidl_ret_status));\n";
  out << "_aidl_status_return:\n";
  if (options.GenLog()) {
    out << cpp::GenLogAfterExecute(q_name, defined_type, method, "_aidl_status", "_aidl_return",
                                   false /* isServer */, true /* isNdk */);
  }

  out << "return _aidl_status;\n";
  out.Dedent();
  out << "}\n";
}

static void GenerateServerCaseDefinition(CodeWriter& out, const AidlTypenames& types,
                                         const AidlInterface& defined_type,
                                         const AidlMethod& method, const Options& options) {
  const string q_name = GetQualifiedName(defined_type, ClassNames::SERVER);

  out << "case " << MethodId(method) << ": {\n";
  out.Indent();

  if (defined_type.EnforceExpression() || method.GetType().EnforceExpression()) {
    out.Write("#error Permission checks not implemented for the ndk backend\n");
  }

  if (method.IsNew() && ShouldForceDowngradeFor(CommunicationSide::READ) &&
      method.IsUserDefined()) {
    out << "if (true) break;\n";
  }
  for (const auto& arg : method.GetArguments()) {
    out << NdkNameOf(types, arg->GetType(), StorageMode::STACK) << " " << cpp::BuildVarName(*arg)
        << ";\n";
  }
  if (method.GetType().GetName() != "void") {
    out << NdkNameOf(types, method.GetType(), StorageMode::STACK) << " _aidl_return;\n";
  }
  out << "\n";
  if (options.GenTraces()) {
    out << "ScopedTrace _aidl_trace(\"AIDL::" << to_string(options.TargetLanguage())
        << "::" << ClassName(defined_type, ClassNames::INTERFACE) << "::" << method.GetName()
        << "::server\");\n";
  }

  for (const auto& arg : method.GetArguments()) {
    const std::string var_name = cpp::BuildVarName(*arg);

    if (arg->IsIn()) {
      out << "_aidl_ret_status = ";
      ReadFromParcelFor({out, types, arg->GetType(), "_aidl_in", "&" + var_name});
      out << ";\n";
      StatusCheckBreak(out);
    } else if (arg->IsOut() && arg->GetType().IsDynamicArray()) {
      out << "_aidl_ret_status = ::ndk::AParcel_resizeVector(_aidl_in, &" << var_name << ");\n";
      StatusCheckBreak(out);
    }
  }
  if (options.GenLog()) {
    out << cpp::GenLogBeforeExecute(q_name, method, true /* isServer */, true /* isNdk */);
  }
  out << "::ndk::ScopedAStatus _aidl_status = _aidl_impl->" << method.GetName() << "("
      << NdkArgList(types, method, FormatArgForCall) << ");\n";

  if (options.GenLog()) {
    out << cpp::GenLogAfterExecute(q_name, defined_type, method, "_aidl_status", "_aidl_return",
                                   true /* isServer */, true /* isNdk */);
  }
  if (method.IsOneway()) {
    // For a oneway transaction, the kernel will have already returned a result. This is for the
    // in-process case when a oneway transaction is parceled/unparceled in the same process.
    out << "_aidl_ret_status = STATUS_OK;\n";
  } else {
    out << "_aidl_ret_status = AParcel_writeStatusHeader(_aidl_out, _aidl_status.get());\n";
    StatusCheckBreak(out);

    out << "if (!AStatus_isOk(_aidl_status.get())) break;\n\n";

    if (method.GetType().GetName() != "void") {
      out << "_aidl_ret_status = ";
      WriteToParcelFor({out, types, method.GetType(), "_aidl_out", "_aidl_return"});
      out << ";\n";
      StatusCheckBreak(out);
    }
    for (const AidlArgument* arg : method.GetOutArguments()) {
      out << "_aidl_ret_status = ";
      WriteToParcelFor({out, types, arg->GetType(), "_aidl_out", cpp::BuildVarName(*arg)});
      out << ";\n";
      StatusCheckBreak(out);
    }
  }
  out << "break;\n";
  out.Dedent();
  out << "}\n";
}

static string OnTransactFuncName(const AidlInterface& interface) {
  string name = interface.GetCanonicalName();
  std::replace(name.begin(), name.end(), '.', '_');
  return "_aidl_" + name + "_onTransact";
}

static string GlobalClassVarName(const AidlInterface& interface) {
  string name = interface.GetCanonicalName();
  std::replace(name.begin(), name.end(), '.', '_');
  return "_g_aidl_" + name + "_clazz";
}

void GenerateClassSource(CodeWriter& out, const AidlTypenames& types,
                         const AidlInterface& defined_type, const Options& options) {
  const std::string i_name = GetQualifiedName(defined_type, ClassNames::INTERFACE);
  const std::string q_name = GetQualifiedName(defined_type, ClassNames::SERVER);

  const string on_transact = OnTransactFuncName(defined_type);
  bool deprecated = defined_type.IsDeprecated() ||
                    std::any_of(defined_type.GetMethods().begin(), defined_type.GetMethods().end(),
                                [](const auto& m) { return m->IsDeprecated(); });
  if (deprecated) {
    out << "#pragma clang diagnostic push\n";
    out << "#pragma clang diagnostic ignored \"-Wdeprecated\"\n";
  }
  out << "static binder_status_t " << on_transact
      << "(AIBinder* _aidl_binder, transaction_code_t _aidl_code, const AParcel* _aidl_in, "
         "AParcel* _aidl_out) {\n";
  out.Indent();
  out << "(void)_aidl_in;\n";
  out << "(void)_aidl_out;\n";
  out << "binder_status_t _aidl_ret_status = STATUS_UNKNOWN_TRANSACTION;\n";
  if (!defined_type.GetMethods().empty()) {
    // we know this cast is valid because this method is only called by the ICInterface
    // AIBinder_Class object which is associated with this class.
    out << "std::shared_ptr<" << q_name << "> _aidl_impl = std::static_pointer_cast<" << q_name
        << ">(::ndk::ICInterface::asInterface(_aidl_binder));\n";
    out << "switch (_aidl_code) {\n";
    out.Indent();
    for (const auto& method : defined_type.GetMethods()) {
      GenerateServerCaseDefinition(out, types, defined_type, *method, options);
    }
    out.Dedent();
    out << "}\n";
  } else {
    out << "(void)_aidl_binder;\n";
    out << "(void)_aidl_code;\n";
  }
  out << "return _aidl_ret_status;\n";
  out.Dedent();
  out << "}\n\n";

  out << "static AIBinder_Class* " << GlobalClassVarName(defined_type)
      << " = ::ndk::ICInterface::defineClass(" << i_name << "::" << kDescriptor << ", "
      << on_transact << ");\n\n";
  if (deprecated) {
    out << "#pragma clang diagnostic pop\n";
  }
}

void GenerateClientSource(CodeWriter& out, const AidlTypenames& types,
                          const AidlInterface& defined_type, const Options& options) {
  const std::string q_name = GetQualifiedName(defined_type, ClassNames::CLIENT);
  const std::string clazz = ClassName(defined_type, ClassNames::CLIENT);

  out << q_name << "::" << clazz << "(const ::ndk::SpAIBinder& binder) : BpCInterface(binder) {}\n";
  out << q_name << "::~" << clazz << "() {}\n";
  if (options.GenLog()) {
    out << "std::function<void(const " << q_name << "::TransactionLog&)> " << q_name
        << "::logFunc;\n";
  }
  out << "\n";
  for (const auto& method : defined_type.GetMethods()) {
    GenerateClientMethodDefinition(out, types, defined_type, *method, options);
  }
}

void GenerateServerSource(CodeWriter& out, const AidlTypenames& types,
                          const AidlInterface& defined_type, const Options& options) {
  const std::string q_name = GetQualifiedName(defined_type, ClassNames::SERVER);
  const std::string clazz = ClassName(defined_type, ClassNames::SERVER);
  const std::string iface = ClassName(defined_type, ClassNames::INTERFACE);

  out << "// Source for " << clazz << "\n";
  out << q_name << "::" << clazz << "() {}\n";
  out << q_name << "::~" << clazz << "() {}\n";
  if (options.GenLog()) {
    out << "std::function<void(const " << q_name << "::TransactionLog&)> " << q_name
        << "::logFunc;\n";
  }
  out << "::ndk::SpAIBinder " << q_name << "::createBinder() {\n";
  out.Indent();
  out << "AIBinder* binder = AIBinder_new(" << GlobalClassVarName(defined_type)
      << ", static_cast<void*>(this));\n";

  out << "#ifdef BINDER_STABILITY_SUPPORT\n";
  if (defined_type.IsVintfStability()) {
    out << "AIBinder_markVintfStability(binder);\n";
  } else {
    out << "AIBinder_markCompilationUnitStability(binder);\n";
  }
  out << "#endif  // BINDER_STABILITY_SUPPORT\n";

  out << "return ::ndk::SpAIBinder(binder);\n";
  out.Dedent();
  out << "}\n";

  // Implement the meta methods
  for (const auto& method : defined_type.GetMethods()) {
    if (method->IsUserDefined()) {
      continue;
    }
    if (method->GetName() == kGetInterfaceVersion && options.Version() > 0) {
      out << NdkMethodDecl(types, *method, q_name) << " {\n";
      out.Indent();
      out << "*_aidl_return = " << iface << "::" << kVersion << ";\n";
      out << "return ::ndk::ScopedAStatus(AStatus_newOk());\n";
      out.Dedent();
      out << "}\n";
    }
    if (method->GetName() == kGetInterfaceHash && (!options.Hash().empty())) {
      out << NdkMethodDecl(types, *method, q_name) << " {\n";
      out.Indent();
      out << "*_aidl_return = " << iface << "::" << kHash << ";\n";
      out << "return ::ndk::ScopedAStatus(AStatus_newOk());\n";
      out.Dedent();
      out << "}\n";
    }
  }
}
void GenerateInterfaceSource(CodeWriter& out, const AidlTypenames& types,
                             const AidlInterface& defined_type, const Options& options) {
  const std::string q_name = GetQualifiedName(defined_type, ClassNames::INTERFACE);
  const std::string clazz = ClassName(defined_type, ClassNames::INTERFACE);
  const std::string bp_clazz = ClassName(defined_type, ClassNames::CLIENT);

  out << "// Source for " << clazz << "\n";
  out << "const char* " << q_name << "::" << kDescriptor << " = \"" << defined_type.GetDescriptor()
      << "\";\n";
  out << q_name << "::" << clazz << "() {}\n";
  out << q_name << "::~" << clazz << "() {}\n";
  out << "\n";
  GenerateConstantDefinitions(out, defined_type, q_name);
  out << "\n";

  out << "std::shared_ptr<" << q_name << "> " << q_name
      << "::fromBinder(const ::ndk::SpAIBinder& binder) {\n";
  out.Indent();
  out << "if (!AIBinder_associateClass(binder.get(), " << GlobalClassVarName(defined_type)
      << ")) {\n";
  out.Indent();
  // since NDK users don't use weak symbol support, we don't check builtin available. We could
  // optionally check it if __ANDROID_UNAVAILABLE_SYMBOLS_ARE_WEAK__ is defined, but this would
  // also mean that app developers relying on this behavior would be missing test parity, and if
  // local transactions aren't supported, due to missing the descriptor API, they will need to
  // work around it a different way, so it's best to check based on __ANDROID_API__
  out << "#if __ANDROID_API__ >= 31\n";
  out << "const AIBinder_Class* originalClass = AIBinder_getClass(binder.get());\n";
  out << "if (originalClass == nullptr) return nullptr;\n";
  out << "if (0 == strcmp(AIBinder_Class_getDescriptor(originalClass), descriptor)) {\n";
  out.Indent();
  // parcel transactions in process, e.g. NDK<->Rust (okay..)
  out << "return ::ndk::SharedRefBase::make<" << GetQualifiedName(defined_type, ClassNames::CLIENT)
      << ">(binder);\n";
  out.Dedent();
  out << "}\n";
  out << "#endif\n";
  out << "return nullptr;\n";
  out.Dedent();
  out << "}\n";
  out << "std::shared_ptr<::ndk::ICInterface> interface = "
         "::ndk::ICInterface::asInterface(binder.get());\n";
  out << "if (interface) {\n";
  out.Indent();
  out << "return std::static_pointer_cast<" << clazz << ">(interface);\n";
  out.Dedent();
  out << "}\n";
  out << "return ::ndk::SharedRefBase::make<" << GetQualifiedName(defined_type, ClassNames::CLIENT)
      << ">(binder);\n";
  out.Dedent();
  out << "}\n\n";

  out << "binder_status_t " << q_name << "::writeToParcel(AParcel* parcel, const std::shared_ptr<"
      << clazz << ">& instance) {\n";
  out.Indent();
  out << "return AParcel_writeStrongBinder(parcel, instance ? instance->asBinder().get() : "
         "nullptr);\n";
  out.Dedent();
  out << "}\n";

  out << "binder_status_t " << q_name << "::readFromParcel(const AParcel* parcel, std::shared_ptr<"
      << clazz << ">* instance) {\n";
  out.Indent();
  out << "::ndk::SpAIBinder binder;\n";
  out << "binder_status_t status = AParcel_readStrongBinder(parcel, binder.getR());\n";
  out << "if (status != STATUS_OK) return status;\n";
  out << "*instance = " << clazz << "::fromBinder(binder);\n";
  out << "return STATUS_OK;\n";
  out.Dedent();
  out << "}\n";

  // defintion for static member setDefaultImpl
  out << "bool " << q_name << "::setDefaultImpl(const std::shared_ptr<" << clazz << ">& impl) {\n";
  out.Indent();
  out << "// Only one user of this interface can use this function\n";
  out << "// at a time. This is a heuristic to detect if two different\n";
  out << "// users in the same process use this function.\n";
  out << "assert(!" << clazz << "::default_impl);\n";
  out << "if (impl) {\n";
  out.Indent();
  out << clazz << "::default_impl = impl;\n";
  out << "return true;\n";
  out.Dedent();
  out << "}\n";
  out << "return false;\n";
  out.Dedent();
  out << "}\n";

  // definition for static member getDefaultImpl
  out << "const std::shared_ptr<" << q_name << ">& " << q_name << "::getDefaultImpl() {\n";
  out.Indent();
  out << "return " << clazz << "::default_impl;\n";
  out.Dedent();
  out << "}\n";

  // definition for the static field default_impl
  out << "std::shared_ptr<" << q_name << "> " << q_name << "::default_impl = nullptr;\n";

  // default implementation for the <Name>Default class members
  const std::string defaultClazz = q_name + "Default";
  for (const auto& method : defined_type.GetMethods()) {
    if (method->IsUserDefined()) {
      out << "::ndk::ScopedAStatus " << defaultClazz << "::" << method->GetName() << "("
          << NdkArgList(types, *method, FormatArgNameUnused) << ") {\n";
      out.Indent();
      out << "::ndk::ScopedAStatus _aidl_status;\n";
      out << "_aidl_status.set(AStatus_fromStatus(STATUS_UNKNOWN_TRANSACTION));\n";
      out << "return _aidl_status;\n";
      out.Dedent();
      out << "}\n";
    } else {
      if (method->GetName() == kGetInterfaceVersion && options.Version() > 0) {
        out << "::ndk::ScopedAStatus " << defaultClazz << "::" << method->GetName() << "("
            << "int32_t* _aidl_return) {\n";
        out.Indent();
        out << "*_aidl_return = 0;\n";
        out << "return ::ndk::ScopedAStatus(AStatus_newOk());\n";
        out.Dedent();
        out << "}\n";
      }
      if (method->GetName() == kGetInterfaceHash && !options.Hash().empty()) {
        out << "::ndk::ScopedAStatus " << defaultClazz << "::" << method->GetName() << "("
            << "std::string* _aidl_return) {\n";
        out.Indent();
        out << "*_aidl_return = \"\";\n";
        out << "return ::ndk::ScopedAStatus(AStatus_newOk());\n";
        out.Dedent();
        out << "}\n";
      }
    }
  }

  out << "::ndk::SpAIBinder " << defaultClazz << "::asBinder() {\n";
  out.Indent();
  out << "return ::ndk::SpAIBinder();\n";
  out.Dedent();
  out << "}\n";

  out << "bool " << defaultClazz << "::isRemote() {\n";
  out.Indent();
  out << "return false;\n";
  out.Dedent();
  out << "}\n";
}

void GenerateClientClassDecl(CodeWriter& out, const AidlTypenames& types,
                             const AidlInterface& defined_type, const Options& options) {
  const std::string clazz = ClassName(defined_type, ClassNames::CLIENT);

  out << "class";
  cpp::GenerateDeprecated(out, defined_type);
  out << " " << clazz << " : public ::ndk::BpCInterface<"
      << ClassName(defined_type, ClassNames::INTERFACE) << "> {\n";
  out << "public:\n";
  out.Indent();
  out << "explicit " << clazz << "(const ::ndk::SpAIBinder& binder);\n";
  out << "virtual ~" << clazz << "();\n";
  out << "\n";
  for (const auto& method : defined_type.GetMethods()) {
    out << NdkMethodDecl(types, *method) << " override";
    cpp::GenerateDeprecated(out, *method);
    out << ";\n";
  }

  if (options.Version() > 0) {
    out << "int32_t " << kCachedVersion << " = -1;\n";
  }

  if (!options.Hash().empty()) {
    out << "std::string " << kCachedHash << " = \"-1\";\n";
    out << "std::mutex " << kCachedHashMutex << ";\n";
  }
  if (options.GenLog()) {
    out << cpp::kTransactionLogStruct;
    out << "static std::function<void(const TransactionLog&)> logFunc;\n";
  }
  out.Dedent();
  out << "};\n";
}

void GenerateClientHeader(CodeWriter& out, const AidlTypenames& types,
                          const AidlInterface& defined_type, const Options& options) {
  out << "#pragma once\n\n";
  out << "#include \"" << NdkHeaderFile(defined_type, ClassNames::RAW, false /*use_os_sep*/)
      << "\"\n";
  out << "\n";
  out << "#include <android/binder_ibinder.h>\n";
  if (options.GenLog()) {
    out << "#include <functional>\n";
    out << "#include <chrono>\n";
    out << "#include <sstream>\n";
  }
  if (options.GenTraces()) {
    out << "#include <android/trace.h>\n";
  }
  out << "\n";
  EnterNdkNamespace(out, defined_type);
  GenerateClientClassDecl(out, types, defined_type, options);
  LeaveNdkNamespace(out, defined_type);
}

void GenerateDelegatorClassDecl(CodeWriter& out, const AidlTypenames& types,
                                const AidlInterface& defined_type, const Options& options) {
  const std::string clazz = ClassName(defined_type, ClassNames::DELEGATOR_IMPL);
  const std::string iface = ClassName(defined_type, ClassNames::INTERFACE);
  const std::string bn_name = ClassName(defined_type, ClassNames::SERVER);
  const std::string kDelegateImplVarName = "_impl";
  const std::string kStatusType = "::ndk::ScopedAStatus";

  out << "class";
  cpp::GenerateDeprecated(out, defined_type);
  out << " " << clazz << " : public " << bn_name << " {\n";
  out << "public:\n";
  out.Indent();
  out << "explicit " << clazz << "(const std::shared_ptr<" << iface << "> &impl)"
      << " : " << kDelegateImplVarName << "(impl) {\n";
  if (options.Version() > 0) {
    // TODO(b/222347502) If we need to support mismatched versions of delegator and
    // impl, this check will be removed. The NDK backend can't override the
    // getInterface* meta methods because they are marked "final". Removing
    // "final" changes the ABI and breaks prebuilts.
    out << "   int32_t _impl_ver = 0;\n";
    out << "   if (!impl->" << kGetInterfaceVersion << "(&_impl_ver).isOk()) {;\n";
    out << "      __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, \"Delegator failed to get "
           "version of the implementation.\");\n";
    out << "   }\n";
    out << "   if (_impl_ver != " << iface << "::" << kVersion << ") {\n";
    out << "      __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, \"Mismatched versions of "
           "delegator and implementation is not allowed.\");\n";
    out << "   }\n";
  }
  out << "}\n\n";
  for (const auto& method : defined_type.GetMethods()) {
    if (method->IsUserDefined()) {
      out << kStatusType << " " << method->GetName() << "("
          << NdkArgList(types, *method, FormatArgForDecl) << ") override";
      cpp::GenerateDeprecated(out, *method);
      out << " {\n"
          << "  return " << kDelegateImplVarName << "->" << method->GetName() << "("
          << NdkArgList(types, *method, FormatArgNameOnly) << ");\n";
      out << "}\n";
    }
  }
  out.Dedent();
  out << "protected:\n";
  out.Indent();
  out.Dedent();
  out << "private:\n";
  out.Indent();
  out << "std::shared_ptr<" << iface << "> " << kDelegateImplVarName << ";\n";
  out.Dedent();
  out << "};\n\n";
}

void GenerateServerClassDecl(CodeWriter& out, const AidlTypenames& types,
                             const AidlInterface& defined_type, const Options& options) {
  const std::string clazz = ClassName(defined_type, ClassNames::SERVER);
  const std::string iface = ClassName(defined_type, ClassNames::INTERFACE);

  out << "class";
  cpp::GenerateDeprecated(out, defined_type);
  out << " " << clazz << " : public ::ndk::BnCInterface<" << iface << "> {\n";
  out << "public:\n";
  out.Indent();
  out << clazz << "();\n";
  out << "virtual ~" << clazz << "();\n";

  // Declare the meta methods
  for (const auto& method : defined_type.GetMethods()) {
    if (method->IsUserDefined()) {
      continue;
    }
    if (method->GetName() == kGetInterfaceVersion && options.Version() > 0) {
      out << NdkMethodDecl(types, *method) << " final;\n";
    } else if (method->GetName() == kGetInterfaceHash && !options.Hash().empty()) {
      out << NdkMethodDecl(types, *method) << " final;\n";
    } else {
      AIDL_FATAL(defined_type) << "Meta method '" << method->GetName() << "' is unimplemented.";
    }
  }
  if (options.GenLog()) {
    out << cpp::kTransactionLogStruct;
    out << "static std::function<void(const TransactionLog&)> logFunc;\n";
  }
  out.Dedent();
  out << "protected:\n";
  out.Indent();
  out << "::ndk::SpAIBinder createBinder() override;\n";
  out.Dedent();
  out << "private:\n";
  out.Indent();
  out.Dedent();
  out << "};\n";
}

void GenerateServerHeader(CodeWriter& out, const AidlTypenames& types,
                          const AidlInterface& defined_type, const Options& options) {
  out << "#pragma once\n\n";
  out << "#include \"" << NdkHeaderFile(defined_type, ClassNames::RAW, false /*use_os_sep*/)
      << "\"\n";
  out << "\n";
  out << "#include <android/binder_ibinder.h>\n";
  // Needed for *Delegator classes while delegator version is required to be
  // the same as the implementation version
  // TODO(b/222347502) If we ever need to support mismatched versions of delegator and
  // impl, this include can be removed.
  out << "#include <cassert>\n\n";
  // TODO(b/31559095) bionic on host should define __assert2
  out << "#ifndef __BIONIC__\n#ifndef __assert2\n#define __assert2(a,b,c,d) "
         "((void)0)\n#endif\n#endif\n";
  out << "\n";
  EnterNdkNamespace(out, defined_type);
  GenerateServerClassDecl(out, types, defined_type, options);
  GenerateDelegatorClassDecl(out, types, defined_type, options);
  LeaveNdkNamespace(out, defined_type);
}

void GenerateInterfaceClassDecl(CodeWriter& out, const AidlTypenames& types,
                                const AidlInterface& defined_type, const Options& options) {
  const std::string clazz = ClassName(defined_type, ClassNames::INTERFACE);
  out << "class " << ClassName(defined_type, ClassNames::DELEGATOR_IMPL) << ";\n\n";
  out << "class";
  cpp::GenerateDeprecated(out, defined_type);
  out << " " << clazz << " : public ::ndk::ICInterface {\n";
  out << "public:\n";
  out.Indent();
  out << "typedef " << ClassName(defined_type, ClassNames::DELEGATOR_IMPL)
      << " DefaultDelegator;\n";
  out << "static const char* " << kDescriptor << ";\n";
  out << clazz << "();\n";
  out << "virtual ~" << clazz << "();\n";
  out << "\n";
  GenerateNestedTypeDecls(out, types, defined_type, options);
  GenerateConstantDeclarations(out, types, defined_type);
  if (options.Version() > 0) {
    if (options.IsLatestUnfrozenVersion()) {
      out << "static inline const int32_t " << kVersion << " = true ? "
          << std::to_string(options.PreviousVersion()) << " : " << std::to_string(options.Version())
          << ";\n";
    } else {
      out << "static inline const int32_t " << kVersion << " = "
          << std::to_string(options.Version()) << ";\n";
    }
  }
  if (!options.Hash().empty()) {
    if (options.IsLatestUnfrozenVersion()) {
      out << "static inline const std::string " << kHash << " = true ? \"" << options.PreviousHash()
          << "\" : \"" << options.Hash() << "\";\n";
    } else {
      out << "static inline const std::string " << kHash << " = \"" << options.Hash() << "\";\n";
    }
  }
  for (const auto& method : defined_type.GetMethods()) {
    if (!method->IsUserDefined()) {
      continue;
    }
    out << "static constexpr uint32_t TRANSACTION_" << method->GetName() << " = "
        << "FIRST_CALL_TRANSACTION + " << std::to_string(method->GetId()) << ";\n";
  }
  out << "\n";
  out << "static std::shared_ptr<" << clazz << "> fromBinder(const ::ndk::SpAIBinder& binder);\n";
  out << "static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<" << clazz
      << ">& instance);";
  out << "\n";
  out << "static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<" << clazz
      << ">* instance);";
  out << "\n";
  out << "static bool setDefaultImpl(const std::shared_ptr<" << clazz << ">& impl);";
  out << "\n";
  out << "static const std::shared_ptr<" << clazz << ">& getDefaultImpl();";
  out << "\n";
  for (const auto& method : defined_type.GetMethods()) {
    out << "virtual " << NdkMethodDecl(types, *method);
    cpp::GenerateDeprecated(out, *method);
    out << " = 0;\n";
  }
  out.Dedent();
  out << "private:\n";
  out.Indent();
  out << "static std::shared_ptr<" << clazz << "> default_impl;\n";
  out.Dedent();
  out << "};\n";

  const std::string defaultClazz = clazz + "Default";
  out << "class";
  cpp::GenerateDeprecated(out, defined_type);
  out << " " << defaultClazz << " : public " << clazz << " {\n";
  out << "public:\n";
  out.Indent();
  for (const auto& method : defined_type.GetMethods()) {
    if (method->IsUserDefined()) {
      out << NdkMethodDecl(types, *method) << " override";
      cpp::GenerateDeprecated(out, *method);
      out << ";\n";
    } else if (method->GetName() == kGetInterfaceVersion && options.Version() > 0) {
      out << NdkMethodDecl(types, *method) << " override;\n";
    } else if (method->GetName() == kGetInterfaceHash && !options.Hash().empty()) {
      out << NdkMethodDecl(types, *method) << " override;\n";
    }
  }
  out << "::ndk::SpAIBinder asBinder() override;\n";
  out << "bool isRemote() override;\n";
  out.Dedent();
  out << "};\n";

  // When an interface is nested, every class should be defined together here
  // because we don't have separate headers for them.
  // (e.g. IFoo, IFooDefault, BpFoo, BnFoo, IFooDelegator)
  if (defined_type.GetParentType()) {
    GenerateClientClassDecl(out, types, defined_type, options);
    GenerateServerClassDecl(out, types, defined_type, options);
  }
}

void GenerateParcelClassDecl(CodeWriter& out, const AidlTypenames& types,
                             const AidlStructuredParcelable& defined_type, const Options& options) {
  const std::string clazz = ClassName(defined_type, ClassNames::RAW);

  cpp::ClangDiagnosticIgnoreDeprecated guard(out, cpp::HasDeprecatedField(defined_type));
  out << cpp::TemplateDecl(defined_type);
  out << "class";
  cpp::GenerateDeprecated(out, defined_type);
  out << " " << clazz << " {\n";
  out << "public:\n";
  out.Indent();
  if (defined_type.IsFixedSize()) {
    out << "typedef std::true_type fixed_size;\n";
  } else {
    out << "typedef std::false_type fixed_size;\n";
  }
  out << "static const char* descriptor;\n";
  out << "\n";
  GenerateNestedTypeDecls(out, types, defined_type, options);
  for (const auto& variable : defined_type.GetFields()) {
    const auto& type = variable->GetType();
    std::string cpp_type = NdkNameOf(types, type, StorageMode::STACK);
    out << cpp_type;
    cpp::GenerateDeprecated(out, *variable);
    out << " " << variable->GetName();
    if (defined_type.IsFixedSize()) {
      int alignment = cpp::AlignmentOf(type, types);
      if (alignment > 0) {
        out << " __attribute__((aligned (" << std::to_string(alignment) << ")))";
      }
    }
    if (variable->GetDefaultValue()) {
      out << " = " << variable->ValueString(ConstantValueDecorator);
    } else {
      // Some types needs to be explicitly initialized even when no default value is set.
      // - ParcelableHolder should be initialized with stability.
      // - enum should be zero initialized, otherwise the value will be indeterminate
      // - fixed-size arrays should be initialized, otherwise the value will be indeterminate
      if (type.GetName() == "ParcelableHolder") {
        if (defined_type.IsVintfStability()) {
          out << "{::ndk::STABILITY_VINTF}";
        } else {
          out << "{::ndk::STABILITY_LOCAL}";
        }
      } else if (types.GetEnumDeclaration(type) && !type.IsArray()) {
        out << " = " << cpp_type << "(0)";
      } else if (type.IsFixedSizeArray() && !type.IsNullable()) {
        out << " = {{}}";
      }
    }
    out << ";\n";
  }
  out << "\n";
  out << "binder_status_t readFromParcel(const AParcel* parcel);\n";
  out << "binder_status_t writeToParcel(AParcel* parcel) const;\n";
  out << "\n";

  cpp::GenerateParcelableComparisonOperators(out, defined_type);

  out << "static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::"
      << (defined_type.IsVintfStability() ? "STABILITY_VINTF" : "STABILITY_LOCAL") << ";\n";

  GenerateConstantDeclarations(out, types, defined_type);
  cpp::GenerateToString(out, defined_type);

  out.Dedent();
  out << "};\n";
}

void GenerateParcelSource(CodeWriter& out, const AidlTypenames& types,
                          const AidlStructuredParcelable& defined_type, const Options& options) {
  std::string clazz = GetQualifiedName(defined_type);
  if (defined_type.IsGeneric()) {
    std::vector<std::string> template_params;
    for (const auto& parameter : defined_type.GetTypeParameters()) {
      template_params.push_back(parameter);
    }
    clazz += base::StringPrintf("<%s>", base::Join(template_params, ", ").c_str());
  }

  GenerateSourceIncludes(out, types, defined_type, options);
  EnterNdkNamespace(out, defined_type);
  out << cpp::TemplateDecl(defined_type);
  out << "const char* " << clazz << "::" << kDescriptor << " = \""
      << defined_type.GetCanonicalName() << "\";\n";
  out << "\n";

  GenerateConstantDefinitions(out, defined_type, clazz, cpp::TemplateDecl(defined_type));

  {
    cpp::ClangDiagnosticIgnoreDeprecated guard(out, cpp::HasDeprecatedField(defined_type));
    out << cpp::TemplateDecl(defined_type);
    out << "binder_status_t " << clazz << "::readFromParcel(const AParcel* _aidl_parcel) {\n";
    out.Indent();
    out << "binder_status_t _aidl_ret_status = STATUS_OK;\n";
    out << "int32_t _aidl_start_pos = AParcel_getDataPosition(_aidl_parcel);\n";
    out << "int32_t _aidl_parcelable_size = 0;\n";
    out << "_aidl_ret_status = AParcel_readInt32(_aidl_parcel, &_aidl_parcelable_size);\n";
    StatusCheckReturn(out);
    out << "if (_aidl_parcelable_size < 4) return STATUS_BAD_VALUE;\n";
    out << "if (_aidl_start_pos > INT32_MAX - _aidl_parcelable_size) return STATUS_BAD_VALUE;\n";
    for (const auto& variable : defined_type.GetFields()) {
      if (variable->IsNew() && ShouldForceDowngradeFor(CommunicationSide::READ)) {
        out << "if (false) {\n";
        out.Indent();
      }

      out << "if (AParcel_getDataPosition(_aidl_parcel) - _aidl_start_pos >= "
             "_aidl_parcelable_size) "
             "{\n"
          << "  AParcel_setDataPosition(_aidl_parcel, _aidl_start_pos + _aidl_parcelable_size);\n"
          << "  return _aidl_ret_status;\n"
          << "}\n";
      out << "_aidl_ret_status = ";
      ReadFromParcelFor(
          {out, types, variable->GetType(), "_aidl_parcel", "&" + variable->GetName()});
      out << ";\n";
      StatusCheckReturn(out);
      if (variable->IsNew() && ShouldForceDowngradeFor(CommunicationSide::READ)) {
        out.Dedent();
        out << "}\n";
      }
    }
    out << "AParcel_setDataPosition(_aidl_parcel, _aidl_start_pos + _aidl_parcelable_size);\n"
        << "return _aidl_ret_status;\n";
    out.Dedent();
    out << "}\n";

    out << cpp::TemplateDecl(defined_type);
    out << "binder_status_t " << clazz << "::writeToParcel(AParcel* _aidl_parcel) const {\n";
    out.Indent();
    out << "binder_status_t _aidl_ret_status;\n";

    out << "size_t _aidl_start_pos = AParcel_getDataPosition(_aidl_parcel);\n";
    out << "_aidl_ret_status = AParcel_writeInt32(_aidl_parcel, 0);\n";
    StatusCheckReturn(out);

    for (const auto& variable : defined_type.GetFields()) {
      if (variable->IsNew() && ShouldForceDowngradeFor(CommunicationSide::WRITE)) {
        out << "if (false) {\n";
        out.Indent();
      }
      out << "_aidl_ret_status = ";
      WriteToParcelFor({out, types, variable->GetType(), "_aidl_parcel", variable->GetName()});
      out << ";\n";
      StatusCheckReturn(out);
      if (variable->IsNew() && ShouldForceDowngradeFor(CommunicationSide::WRITE)) {
        out.Dedent();
        out << "}\n";
      }
    }
    out << "size_t _aidl_end_pos = AParcel_getDataPosition(_aidl_parcel);\n";
    out << "AParcel_setDataPosition(_aidl_parcel, _aidl_start_pos);\n";
    out << "AParcel_writeInt32(_aidl_parcel, _aidl_end_pos - _aidl_start_pos);\n";
    out << "AParcel_setDataPosition(_aidl_parcel, _aidl_end_pos);\n";

    out << "return _aidl_ret_status;\n";
    out.Dedent();
    out << "}\n";
  }
  out << "\n";
  LeaveNdkNamespace(out, defined_type);
}

void GenerateParcelClassDecl(CodeWriter& out, const AidlTypenames& types,
                             const AidlUnionDecl& defined_type, const Options& options) {
  const std::string clazz = ClassName(defined_type, ClassNames::RAW);

  cpp::ClangDiagnosticIgnoreDeprecated guard(out, cpp::HasDeprecatedField(defined_type));
  cpp::UnionWriter uw{defined_type, types,
                      [&](const AidlTypeSpecifier& type, const AidlTypenames& types) {
                        return NdkNameOf(types, type, StorageMode::STACK);
                      },
                      &ConstantValueDecorator};

  out << cpp::TemplateDecl(defined_type);
  out << "class";
  cpp::GenerateDeprecated(out, defined_type);
  out << " " << clazz << " {\n";
  out << "public:\n";
  out.Indent();
  if (defined_type.IsFixedSize()) {
    out << "typedef std::true_type fixed_size;\n";
  } else {
    out << "typedef std::false_type fixed_size;\n";
  }
  out << "static const char* descriptor;\n";
  out << "\n";
  GenerateNestedTypeDecls(out, types, defined_type, options);
  uw.PublicFields(out);

  out << "binder_status_t readFromParcel(const AParcel* _parcel);\n";
  out << "binder_status_t writeToParcel(AParcel* _parcel) const;\n";
  out << "\n";

  cpp::GenerateParcelableComparisonOperators(out, defined_type);

  out << "static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::"
      << (defined_type.IsVintfStability() ? "STABILITY_VINTF" : "STABILITY_LOCAL") << ";\n";
  GenerateConstantDeclarations(out, types, defined_type);
  cpp::GenerateToString(out, defined_type);
  out.Dedent();
  out << "private:\n";
  out.Indent();
  uw.PrivateFields(out);
  out.Dedent();
  out << "};\n";
}

void GenerateParcelSource(CodeWriter& out, const AidlTypenames& types,
                          const AidlUnionDecl& defined_type, const Options& options) {
  std::string clazz = GetQualifiedName(defined_type);
  if (defined_type.IsGeneric()) {
    std::vector<std::string> template_params;
    for (const auto& parameter : defined_type.GetTypeParameters()) {
      template_params.push_back(parameter);
    }
    clazz += base::StringPrintf("<%s>", base::Join(template_params, ", ").c_str());
  }

  cpp::UnionWriter uw{defined_type, types,
                      [&](const AidlTypeSpecifier& type, const AidlTypenames& types) {
                        return NdkNameOf(types, type, StorageMode::STACK);
                      },
                      &ConstantValueDecorator};
  cpp::ParcelWriterContext ctx{
      .status_type = "binder_status_t",
      .status_ok = "STATUS_OK",
      .status_bad = "STATUS_BAD_VALUE",
      .read_func =
          [&](CodeWriter& out, const std::string& var, const AidlTypeSpecifier& type) {
            ReadFromParcelFor({out, types, type, "_parcel", "&" + var});
          },
      .write_func =
          [&](CodeWriter& out, const std::string& value, const AidlTypeSpecifier& type) {
            WriteToParcelFor({out, types, type, "_parcel", value});
          },
  };

  GenerateSourceIncludes(out, types, defined_type, options);
  EnterNdkNamespace(out, defined_type);
  out << cpp::TemplateDecl(defined_type);
  out << "const char* " << clazz << "::" << kDescriptor << " = \""
      << defined_type.GetCanonicalName() << "\";\n";
  out << "\n";

  GenerateConstantDefinitions(out, defined_type, clazz, cpp::TemplateDecl(defined_type));

  {
    cpp::ClangDiagnosticIgnoreDeprecated guard(out, cpp::HasDeprecatedField(defined_type));
    out << cpp::TemplateDecl(defined_type);
    out << "binder_status_t " << clazz << "::readFromParcel(const AParcel* _parcel) {\n";
    out.Indent();
    uw.ReadFromParcel(out, ctx);
    out.Dedent();
    out << "}\n";

    out << cpp::TemplateDecl(defined_type);
    out << "binder_status_t " << clazz << "::writeToParcel(AParcel* _parcel) const {\n";
    out.Indent();
    uw.WriteToParcel(out, ctx);
    out.Dedent();
    out << "}\n";
  }

  out << "\n";
  LeaveNdkNamespace(out, defined_type);
}

void GenerateEnumClassDecl(CodeWriter& out, const AidlTypenames& types,
                           const AidlEnumDeclaration& enum_decl, const Options&) {
  cpp::GenerateEnumClassDecl(out, enum_decl,
                             NdkNameOf(types, enum_decl.GetBackingType(), StorageMode::STACK),
                             ConstantValueDecorator);
  out << "\n";
}

}  // namespace internals
}  // namespace ndk
}  // namespace aidl
}  // namespace android
