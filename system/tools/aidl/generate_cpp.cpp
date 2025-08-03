/**
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

#include "generate_cpp.h"
#include "aidl.h"

#include <algorithm>
#include <cctype>
#include <cstring>
#include <memory>
#include <random>
#include <set>
#include <string>

#include <android-base/format.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>

#include "aidl_language.h"
#include "aidl_to_common.h"
#include "aidl_to_cpp.h"

#include "aidl_typenames.h"
#include "logging.h"
#include "os.h"

using android::base::Join;
using android::base::StringPrintf;
using std::set;
using std::string;
using std::unique_ptr;
using std::vector;

namespace android {
namespace aidl {
namespace cpp {
namespace internals {
namespace {

const char kAndroidStatusVarName[] = "_aidl_ret_status";
const char kCodeVarName[] = "_aidl_code";
const char kFlagsVarName[] = "_aidl_flags";
const char kDataVarName[] = "_aidl_data";
const char kErrorLabel[] = "_aidl_error";
const char kImplVarName[] = "_aidl_impl";
const char kDelegateImplVarName[] = "_aidl_delegate";
const char kParcelVarName[] = "_aidl_parcel";
const char kReplyVarName[] = "_aidl_reply";
const char kReturnVarName[] = "_aidl_return";
const char kStatusVarName[] = "_aidl_status";
const char kTraceVarName[] = "_aidl_trace";
const char kAndroidParcelLiteral[] = "::android::Parcel";
const char kAndroidStatusLiteral[] = "::android::status_t";
const char kAndroidStatusOk[] = "::android::OK";
const char kAndroidStatusBadValue[] = "::android::BAD_VALUE";
const char kBinderStatusLiteral[] = "::android::binder::Status";
const char kIBinderHeader[] = "binder/IBinder.h";
const char kIInterfaceHeader[] = "binder/IInterface.h";
const char kBinderDelegateHeader[] = "binder/Delegate.h";
const char kParcelHeader[] = "binder/Parcel.h";
const char kStabilityHeader[] = "binder/Stability.h";
const char kStatusHeader[] = "binder/Status.h";
const char kString16Header[] = "utils/String16.h";
const char kTraceHeader[] = "binder/Trace.h";
const char kStrongPointerHeader[] = "utils/StrongPointer.h";

void GenerateBreakOnStatusNotOk(CodeWriter& out) {
  out.Write("if (((%s) != (%s))) {\n", kAndroidStatusVarName, kAndroidStatusOk);
  out.Write("  break;\n");
  out.Write("}\n");
}

void GenerateGotoErrorOnBadStatus(CodeWriter& out) {
  out.Write("if (((%s) != (%s))) {\n", kAndroidStatusVarName, kAndroidStatusOk);
  out.Write("  goto %s;\n", kErrorLabel);
  out.Write("}\n");
}

// Format three types of arg list for method.
//  for_declaration & !type_name_only: int a      // for method decl with type and arg
//  for_declaration &  type_name_only: int /*a*/  // for method decl with type
// !for_declaration                  :     a      // for method call with arg (with direction)
string GenerateArgList(const AidlTypenames& typenames, const AidlMethod& method,
                       bool for_declaration, bool type_name_only) {
  vector<string> method_arguments;
  for (const unique_ptr<AidlArgument>& a : method.GetArguments()) {
    string literal;
    // b/144943748: CppNameOf FileDescriptor is unique_fd. Don't pass it by
    // const reference but by value to make it easier for the user to keep
    // it beyond the scope of the call. unique_fd is a thin wrapper for an
    // int (fd) so passing by value is not expensive.
    const bool non_copyable = IsNonCopyableType(a->GetType(), typenames);
    if (for_declaration) {
      // Method declarations need typenames, pointers to out params, and variable
      // names that match the .aidl specification.
      literal = CppNameOf(a->GetType(), typenames);

      if (a->IsOut()) {
        literal = literal + "*";
      } else {
        const auto defined_type = typenames.TryGetDefinedType(a->GetType().GetName());

        const bool is_enum = defined_type && defined_type->AsEnumDeclaration() != nullptr;
        const bool is_primitive = AidlTypenames::IsPrimitiveTypename(a->GetType().GetName());

        // We pass in parameters that are not primitives by const reference.
        // Arrays of primitives are not primitives.
        if (!(is_primitive || is_enum || non_copyable) || a->GetType().IsArray()) {
          literal = "const " + literal + "&";
        }
      }
      if (type_name_only) {
        literal += " /*" + a->GetName() + "*/";
      } else {
        literal += " " + a->GetName();
      }
    } else {
      std::string var_name = BuildVarName(*a);
      if (a->IsOut()) {
        literal = "&" + var_name;
      } else if (non_copyable) {
        literal = "std::move(" + var_name + ")";
      } else {
        literal = var_name;
      }
    }
    method_arguments.push_back(literal);
  }

  if (method.GetType().GetName() != "void") {
    string literal;
    if (for_declaration) {
      literal = CppNameOf(method.GetType(), typenames) + "*";
      if (type_name_only) {
        literal += " /*" + string(kReturnVarName) + "*/";
      } else {
        literal += " " + string(kReturnVarName);
      }
    } else {
      literal = string{"&"} + kReturnVarName;
    }
    method_arguments.push_back(literal);
  }

  return Join(method_arguments, ", ");
}

void GenerateMethodDecl(CodeWriter& out, const AidlTypenames& types, const AidlMethod& method,
                        const string& clazz) {
  string clazz_prefix = clazz.empty() ? "" : clazz + "::";
  out << "::android::binder::Status " << clazz_prefix << method.GetName() << "("
      << GenerateArgList(types, method, /*for_declartion=*/true, /*type_name_only=*/false) << ")";
}

void GenerateClientTransaction(CodeWriter& out, const AidlTypenames& typenames,
                               const AidlInterface& interface, const AidlMethod& method,
                               const Options& options) {
  const string i_name = ClassName(interface, ClassNames::INTERFACE);
  const string bp_name = GetQualifiedName(interface, ClassNames::CLIENT);
  const string bn_name = GetQualifiedName(interface, ClassNames::SERVER);

  GenerateMethodDecl(out, typenames, method, bp_name);
  out << " {\n";
  out.Indent();

  // Declare parcels to hold our query and the response.
  out.Write("%s %s;\n", kAndroidParcelLiteral, kDataVarName);
  if (interface.IsSensitiveData()) {
    out.Write("%s.markSensitive();\n", kDataVarName);
  }
  out.Write("%s.markForBinder(remoteStrong());\n", kDataVarName);

  // Even if we're oneway, the transact method still takes a parcel.
  out.Write("%s %s;\n", kAndroidParcelLiteral, kReplyVarName);

  // Declare the status_t variable we need for error handling.
  out.Write("%s %s = %s;\n", kAndroidStatusLiteral, kAndroidStatusVarName, kAndroidStatusOk);
  // We unconditionally return a Status object.
  out.Write("%s %s;\n", kBinderStatusLiteral, kStatusVarName);

  if (options.GenTraces()) {
    out.Write(
        "::android::binder::ScopedTrace %s(ATRACE_TAG_AIDL, \"AIDL::cpp::%s::%s::cppClient\");\n",
        kTraceVarName, interface.GetName().c_str(), method.GetName().c_str());
  }

  if (options.GenLog()) {
    out << GenLogBeforeExecute(bp_name, method, false /* isServer */, false /* isNdk */);
  }

  if (method.IsNew() && ShouldForceDowngradeFor(CommunicationSide::WRITE)) {
    out << "if (true) {\n";
    out.Write("  %s = ::android::UNKNOWN_TRANSACTION;\n", kAndroidStatusVarName);
    out << "} else {\n";
    out.Indent();
  }

  // Add the name of the interface we're hoping to call.
  out.Write("%s = %s.writeInterfaceToken(getInterfaceDescriptor());\n", kAndroidStatusVarName,
            kDataVarName);
  GenerateGotoErrorOnBadStatus(out);

  for (const auto& a : method.GetArguments()) {
    const string var_name = ((a->IsOut()) ? "*" : "") + a->GetName();

    if (a->IsIn()) {
      // Serialization looks roughly like:
      //     _aidl_ret_status = _aidl_data.WriteInt32(in_param_name);
      //     if (_aidl_ret_status != ::android::OK) { goto error; }
      out.Write("%s = %s.%s(%s);\n", kAndroidStatusVarName, kDataVarName,
                ParcelWriteMethodOf(a->GetType(), typenames).c_str(),
                ParcelWriteCastOf(a->GetType(), typenames, var_name).c_str());
      GenerateGotoErrorOnBadStatus(out);
    } else if (a->IsOut() && a->GetType().IsDynamicArray()) {
      // Special case, the length of the out array is written into the parcel.
      //     _aidl_ret_status = _aidl_data.writeVectorSize(&out_param_name);
      //     if (_aidl_ret_status != ::android::OK) { goto error; }
      out.Write("%s = %s.writeVectorSize(%s);\n", kAndroidStatusVarName, kDataVarName,
                var_name.c_str());
      GenerateGotoErrorOnBadStatus(out);
    }
  }

  // Invoke the transaction on the remote binder and confirm status.
  std::vector<std::string> flags;
  if (method.IsOneway()) flags.push_back("::android::IBinder::FLAG_ONEWAY");
  if (interface.IsSensitiveData()) flags.push_back("::android::IBinder::FLAG_CLEAR_BUF");

  out.Write("%s = remote()->transact(%s, %s, &%s, %s);\n", kAndroidStatusVarName,
            GetTransactionIdFor(bn_name, method).c_str(), kDataVarName, kReplyVarName,
            flags.empty() ? "0" : Join(flags, " | ").c_str());

  // If the method is not implemented in the remote side, try to call the
  // default implementation, if provided.
  vector<string> arg_names;
  for (const auto& a : method.GetArguments()) {
    if (IsNonCopyableType(a->GetType(), typenames)) {
      arg_names.emplace_back(StringPrintf("std::move(%s)", a->GetName().c_str()));
    } else {
      arg_names.emplace_back(a->GetName());
    }
  }
  if (method.GetType().GetName() != "void") {
    arg_names.emplace_back(kReturnVarName);
  }
  if (method.IsNew() && ShouldForceDowngradeFor(CommunicationSide::WRITE)) {
    out.Dedent();
    out << "}\n";
  }
  out.Write("if (%s == ::android::UNKNOWN_TRANSACTION && %s::getDefaultImpl()) [[unlikely]] {\n",
            kAndroidStatusVarName, i_name.c_str());
  out.Write("   return %s::getDefaultImpl()->%s(%s);\n", i_name.c_str(), method.GetName().c_str(),
            Join(arg_names, ", ").c_str());
  out.Write("}\n");
  GenerateGotoErrorOnBadStatus(out);

  if (!method.IsOneway()) {
    // Strip off the exception header and fail if we see a remote exception.
    // _aidl_ret_status = _aidl_status.readFromParcel(_aidl_reply);
    // if (_aidl_ret_status != ::android::OK) { goto error; }
    // if (!_aidl_status.isOk()) { return _aidl_ret_status; }
    out.Write("%s = %s.readFromParcel(%s);\n", kAndroidStatusVarName, kStatusVarName,
              kReplyVarName);
    GenerateGotoErrorOnBadStatus(out);
    out.Write("if (!%s.isOk()) {\n", kStatusVarName);
    out.Write("  return %s;\n", kStatusVarName);
    out.Write("}\n");
  }

  // Type checking should guarantee that nothing below emits code until "return
  // status" if we are a oneway method, so no more fear of accessing reply.

  // If the method is expected to return something, read it first by convention.
  if (method.GetType().GetName() != "void") {
    out.Write("%s = %s.%s(%s);\n", kAndroidStatusVarName, kReplyVarName,
              ParcelReadMethodOf(method.GetType(), typenames).c_str(),
              ParcelReadCastOf(method.GetType(), typenames, kReturnVarName).c_str());
    GenerateGotoErrorOnBadStatus(out);
  }

  for (const AidlArgument* a : method.GetOutArguments()) {
    // Deserialization looks roughly like:
    //     _aidl_ret_status = _aidl_reply.ReadInt32(out_param_name);
    //     if (_aidl_status != ::android::OK) { goto _aidl_error; }
    out.Write("%s = %s.%s(%s);\n", kAndroidStatusVarName, kReplyVarName,
              ParcelReadMethodOf(a->GetType(), typenames).c_str(),
              ParcelReadCastOf(a->GetType(), typenames, a->GetName()).c_str());
    GenerateGotoErrorOnBadStatus(out);
  }

  // If we've gotten to here, one of two things is true:
  //   1) We've read some bad status_t
  //   2) We've only read status_t == OK and there was no exception in the
  //      response.
  // In both cases, we're free to set Status from the status_t and return.
  out.Write("%s:\n", kErrorLabel);
  out.Write("%s.setFromStatusT(%s);\n", kStatusVarName, kAndroidStatusVarName);

  if (options.GenLog()) {
    out << GenLogAfterExecute(bp_name, interface, method, kStatusVarName, kReturnVarName,
                              false /* isServer */, false /* isNdk */);
  }

  out.Write("return %s;\n", kStatusVarName);
  out.Dedent();
  out << "}\n";
}

void GenerateClientMetaTransaction(CodeWriter& out, const AidlInterface& interface,
                                   const AidlMethod& method, const Options& options) {
  AIDL_FATAL_IF(method.IsUserDefined(), method);
  const string bp_name = GetQualifiedName(interface, ClassNames::CLIENT);
  const string bn_name = GetQualifiedName(interface, ClassNames::SERVER);
  if (method.GetName() == kGetInterfaceVersion && options.Version() > 0) {
    // Note: race condition can happen here, but no locking is required
    // because 1) writing an interger is atomic and 2) this transaction
    // will always return the same value, i.e., competing threads will
    // give write the same value to cached_version_.
    out << "int32_t " << bp_name << "::" << kGetInterfaceVersion << "() {\n"
        << "  if (cached_version_ == -1) {\n"
        << "    ::android::Parcel data;\n"
        << "    ::android::Parcel reply;\n"
        << "    data.writeInterfaceToken(getInterfaceDescriptor());\n"
        << "    ::android::status_t err = remote()->transact("
        << GetTransactionIdFor(bn_name, method) << ", data, &reply);\n"
        << "    if (err == ::android::OK) {\n"
        << "      ::android::binder::Status _aidl_status;\n"
        << "      err = _aidl_status.readFromParcel(reply);\n"
        << "      if (err == ::android::OK && _aidl_status.isOk()) {\n"
        << "        cached_version_ = reply.readInt32();\n"
        << "      }\n"
        << "    }\n"
        << "  }\n"
        << "  return cached_version_;\n"
        << "}\n";
    out << "\n";
  }
  if (method.GetName() == kGetInterfaceHash && !options.Hash().empty()) {
    out << "std::string " << bp_name << "::" << kGetInterfaceHash << "() {\n"
        << "  std::lock_guard<std::mutex> lockGuard(cached_hash_mutex_);\n"
        << "  if (cached_hash_ == \"-1\") {\n"
        << "    ::android::Parcel data;\n"
        << "    ::android::Parcel reply;\n"
        << "    data.writeInterfaceToken(getInterfaceDescriptor());\n"
        << "    ::android::status_t err = remote()->transact("
        << GetTransactionIdFor(bn_name, method) << ", data, &reply);\n"
        << "    if (err == ::android::OK) {\n"
        << "      ::android::binder::Status _aidl_status;\n"
        << "      err = _aidl_status.readFromParcel(reply);\n"
        << "      if (err == ::android::OK && _aidl_status.isOk()) {\n"
        << "        reply.readUtf8FromUtf16(&cached_hash_);\n"
        << "      }\n"
        << "    }\n"
        << "  }\n"
        << "  return cached_hash_;\n"
        << "}\n";
  }
}

}  // namespace

void GenerateClientSource(CodeWriter& out, const AidlInterface& interface,
                          const AidlTypenames& typenames, const Options& options) {
  vector<string> include_list = {
      HeaderFile(interface, ClassNames::CLIENT, false),
      HeaderFile(interface, ClassNames::SERVER, false),  // for TRANSACTION_* consts
      kParcelHeader};
  if (options.GenLog()) {
    include_list.emplace_back("chrono");
    include_list.emplace_back("functional");
  }
  for (const auto& path : include_list) {
    out << "#include <" << path << ">\n";
  }
  out << "\n";

  const string i_name = ClassName(interface, ClassNames::INTERFACE);
  const string bp_name = ClassName(interface, ClassNames::CLIENT);
  const string q_name = GetQualifiedName(interface, ClassNames::CLIENT);

  EnterNamespace(out, interface);
  out << "\n";

  // The constructor just passes the IBinder instance up to the super
  // class.
  out << q_name << "::" << bp_name << "(const ::android::sp<::android::IBinder>& _aidl_impl)\n";
  out << "    : BpInterface<" + i_name + ">(_aidl_impl){\n";
  out << "}\n";
  out << "\n";

  if (options.GenLog()) {
    out << "std::function<void(const " + q_name + "::TransactionLog&)> " << q_name
        << "::logFunc;\n";
    out << "\n";
  }

  // Clients define a method per transaction.
  for (const auto& method : interface.GetMethods()) {
    if (method->IsUserDefined()) {
      GenerateClientTransaction(out, typenames, interface, *method, options);
    } else {
      GenerateClientMetaTransaction(out, interface, *method, options);
    }
    out << "\n";
  }

  LeaveNamespace(out, interface);
}

namespace {

void GenerateConstantDefinitions(CodeWriter& out, const AidlDefinedType& type,
                                 const AidlTypenames& typenames, const string& template_decl,
                                 const string& q_name) {
  for (const auto& constant : type.GetConstantDeclarations()) {
    const AidlConstantValue& value = constant->GetValue();
    if (value.GetType() != AidlConstantValue::Type::STRING) continue;

    std::string cpp_type = CppNameOf(constant->GetType(), typenames);
    out << template_decl;
    out << "const " << cpp_type << "& " << q_name << "::" << constant->GetName() << "() {\n";
    out << "  static const " << cpp_type << " value("
        << constant->ValueString(ConstantValueDecorator) << ");\n";
    out << "  return value;\n";
    out << "}\n";
  }
}

void GenerateConstantDeclarations(CodeWriter& out, const AidlDefinedType& type,
                                  const AidlTypenames& typenames) {
  for (const auto& constant : type.GetConstantDeclarations()) {
    const AidlTypeSpecifier& type = constant->GetType();
    const auto cpp_type = CppNameOf(type, typenames);
    if (type.Signature() == "String") {
      out << "static const " << cpp_type << "& " << constant->GetName() << "()";
      GenerateDeprecated(out, *constant);
      out << ";\n";
    } else if (type.Signature() == "float" || type.Signature() == "double") {
      out << "static constexpr " << cpp_type << " " << constant->GetName();
      GenerateDeprecated(out, *constant);
      out << " = " << constant->ValueString(ConstantValueDecorator) << ";\n";
    } else {
      out << "enum : " << cpp_type << " { " << constant->GetName();
      GenerateDeprecated(out, *constant);
      out << " = " << constant->ValueString(ConstantValueDecorator) << " };\n";
    }
  }
}

void GenerateServerTransaction(CodeWriter& out, const AidlInterface& interface,
                               const AidlMethod& method, const AidlTypenames& typenames,
                               const Options& options) {
  const string bn_name = GetQualifiedName(interface, ClassNames::SERVER);
  if (method.IsNew() && ShouldForceDowngradeFor(CommunicationSide::READ)) {
    out << "if (true) {\n";
    out << "  _aidl_ret_status = ::android::UNKNOWN_TRANSACTION;\n";
    out << "  break;\n";
    out << "}\n";
  }

  // Declare all the parameters now.  In the common case, we expect no errors
  // in serialization.
  for (const unique_ptr<AidlArgument>& a : method.GetArguments()) {
    out.Write("%s %s;\n", CppNameOf(a->GetType(), typenames).c_str(), BuildVarName(*a).c_str());
  }

  // Declare a variable to hold the return value.
  if (method.GetType().GetName() != "void") {
    out.Write("%s %s;\n", CppNameOf(method.GetType(), typenames).c_str(), kReturnVarName);
  }

  // Check that the client is calling the correct interface.
  out.Write("if (!(%s.checkInterface(this))) {\n", kDataVarName);
  out.Write("  %s = ::android::BAD_TYPE;\n", kAndroidStatusVarName);
  out.Write("  break;\n");
  out.Write("}\n");

  if (options.GenTraces()) {
    out.Write(
        "::android::binder::ScopedTrace %s(ATRACE_TAG_AIDL, \"AIDL::cpp::%s::%s::cppServer\");\n",
        kTraceVarName, interface.GetName().c_str(), method.GetName().c_str());
  }

  if (interface.EnforceExpression() || method.GetType().EnforceExpression()) {
    out.Write("#error Permission checks not implemented for the cpp backend\n");
  }

  // Deserialize each "in" parameter to the transaction.
  for (const auto& a: method.GetArguments()) {
    // Deserialization looks roughly like:
    //     _aidl_ret_status = _aidl_data.ReadInt32(&in_param_name);
    //     if (_aidl_ret_status != ::android::OK) { break; }
    const string& var_name = "&" + BuildVarName(*a);
    if (a->IsIn()) {
      out.Write("%s = %s.%s(%s);\n", kAndroidStatusVarName, kDataVarName,
                ParcelReadMethodOf(a->GetType(), typenames).c_str(),
                ParcelReadCastOf(a->GetType(), typenames, var_name).c_str());
      GenerateBreakOnStatusNotOk(out);
    } else if (a->IsOut() && a->GetType().IsDynamicArray()) {
      // Special case, the length of the out array is written into the parcel.
      //     _aidl_ret_status = _aidl_data.resizeOutVector(&out_param_name);
      //     if (_aidl_ret_status != ::android::OK) { break; }
      out.Write("%s = %s.resizeOutVector(%s);\n", kAndroidStatusVarName, kDataVarName,
                var_name.c_str());
      GenerateBreakOnStatusNotOk(out);
    }
  }

  if (options.GenLog()) {
    out << GenLogBeforeExecute(bn_name, method, true /* isServer */, false /* isNdk */);
  }

  if (!method.GetArguments().empty() && options.GetMinSdkVersion() >= SDK_VERSION_Tiramisu) {
    out << "if (auto st = _aidl_data.enforceNoDataAvail(); !st.isOk()) {\n";
    out << "  _aidl_ret_status = st.writeToParcel(_aidl_reply);\n";
    out << "  break;\n";
    out << "}\n";
  }

  // Call the actual method.  This is implemented by the subclass.
  out.Write("%s %s(%s(%s));\n", kBinderStatusLiteral, kStatusVarName, method.GetName().c_str(),
            GenerateArgList(typenames, method, /*for_declaration=*/false, /*type_name_only=*/false)
                .c_str());

  if (options.GenLog()) {
    out << GenLogAfterExecute(bn_name, interface, method, kStatusVarName, kReturnVarName,
                              true /* isServer */, false /* isNdk */);
  }

  // Write exceptions during transaction handling to parcel.
  if (!method.IsOneway()) {
    out.Write("%s = %s.writeToParcel(%s);\n", kAndroidStatusVarName, kStatusVarName, kReplyVarName);
    GenerateBreakOnStatusNotOk(out);
    out.Write("if (!%s.isOk()) {\n", kStatusVarName);
    out.Write("  break;\n");
    out.Write("}\n");
  }

  // If we have a return value, write it first.
  if (method.GetType().GetName() != "void") {
    out.Write("%s = %s->%s(%s);\n", kAndroidStatusVarName, kReplyVarName,
              ParcelWriteMethodOf(method.GetType(), typenames).c_str(),
              ParcelWriteCastOf(method.GetType(), typenames, kReturnVarName).c_str());
    GenerateBreakOnStatusNotOk(out);
  }
  // Write each out parameter to the reply parcel.
  for (const AidlArgument* a : method.GetOutArguments()) {
    // Serialization looks roughly like:
    //     _aidl_ret_status = data.WriteInt32(out_param_name);
    //     if (_aidl_ret_status != ::android::OK) { break; }
    out.Write("%s = %s->%s(%s);\n", kAndroidStatusVarName, kReplyVarName,
              ParcelWriteMethodOf(a->GetType(), typenames).c_str(),
              ParcelWriteCastOf(a->GetType(), typenames, BuildVarName(*a)).c_str());
    GenerateBreakOnStatusNotOk(out);
  }
}

void GenerateServerMetaTransaction(CodeWriter& out, const AidlInterface& interface,
                                   const AidlMethod& method, const Options& options) {
  AIDL_FATAL_IF(method.IsUserDefined(), method);

  string iface = ClassName(interface, ClassNames::INTERFACE);
  if (method.GetName() == kGetInterfaceVersion && options.Version() > 0) {
    out << "_aidl_data.checkInterface(this);\n"
        << "_aidl_reply->writeNoException();\n";
    out << "_aidl_reply->writeInt32(" << iface << "::VERSION);\n";
  }
  if (method.GetName() == kGetInterfaceHash && !options.Hash().empty()) {
    out << "_aidl_data.checkInterface(this);\n"
        << "_aidl_reply->writeNoException();\n";
    out << "_aidl_reply->writeUtf8AsUtf16(" << iface << "::HASH);\n";
  }
}

}  // namespace

void GenerateServerOnTransact(CodeWriter& out, const AidlInterface& interface,
                              const AidlTypenames& typenames, const Options& options) {
  const string bn_name = ClassName(interface, ClassNames::SERVER);
  const string q_name = GetQualifiedName(interface, ClassNames::SERVER);

  bool deprecated = interface.IsDeprecated() ||
                    std::any_of(interface.GetMethods().begin(), interface.GetMethods().end(),
                                [](const auto& m) { return m->IsDeprecated(); });
  if (deprecated) {
    out << "#pragma clang diagnostic push\n";
    out << "#pragma clang diagnostic ignored \"-Wdeprecated\"\n";
    out << "\n";
  }

  out.Write("%s %s::onTransact(uint32_t %s, const %s& %s, %s* %s, uint32_t %s) {\n",
            kAndroidStatusLiteral, q_name.c_str(), kCodeVarName, kAndroidParcelLiteral,
            kDataVarName, kAndroidParcelLiteral, kReplyVarName, kFlagsVarName);
  out.Indent();
  // Declare the status_t variable
  out.Write("%s %s = %s;\n", kAndroidStatusLiteral, kAndroidStatusVarName, kAndroidStatusOk);

  // Add the all important switch statement
  out.Write("switch (%s) {\n", kCodeVarName);

  // The switch statement has a case statement for each transaction code.
  for (const auto& method : interface.GetMethods()) {
    out.Write("case %s:\n", GetTransactionIdFor(bn_name, *method).c_str());
    out << "{\n";
    out.Indent();
    if (method->IsUserDefined()) {
      GenerateServerTransaction(out, interface, *method, typenames, options);
    } else {
      GenerateServerMetaTransaction(out, interface, *method, options);
    }
    out.Dedent();
    out << "}\n";
    out << "break;\n";
  }

  // The switch statement has a default case which defers to the super class.
  // The superclass handles a few pre-defined transactions.
  out << "default:\n";
  out << "{\n";
  out.Write("  %s = ::android::BBinder::onTransact(%s, %s, %s, %s);\n", kAndroidStatusVarName,
            kCodeVarName, kDataVarName, kReplyVarName, kFlagsVarName);
  out << "}\n";
  out << "break;\n";
  out << "}\n";  // switch

  // If we saw a null reference, we can map that to an appropriate exception.
  out.Write("if (%s == ::android::UNEXPECTED_NULL) {\n", kAndroidStatusVarName);
  out.Write("  %s = %s::fromExceptionCode(%s::EX_NULL_POINTER).writeOverParcel(%s);\n",
            kAndroidStatusVarName, kBinderStatusLiteral, kBinderStatusLiteral, kReplyVarName);
  out.Write("}\n");

  // Finally, the server's onTransact method just returns a status code.
  out.Write("return %s;\n", kAndroidStatusVarName);

  out.Dedent();
  out << "}\n";
  out << "\n";

  if (deprecated) {
    out << "#pragma clang diagnostic pop\n";
    out << "\n";
  }
}

void GenerateServerSource(CodeWriter& out, const AidlInterface& interface,
                          const AidlTypenames& typenames, const Options& options) {
  vector<string> include_list{
      HeaderFile(interface, ClassNames::SERVER, false),
      kParcelHeader,
      kStabilityHeader,
  };
  if (options.GenLog()) {
    include_list.emplace_back("chrono");
    include_list.emplace_back("functional");
  }
  for (const auto& include : include_list) {
    out << "#include <" << include << ">\n";
  }
  out << "\n";

  const string i_name = ClassName(interface, ClassNames::INTERFACE);
  const string bn_name = ClassName(interface, ClassNames::SERVER);
  const string q_name = GetQualifiedName(interface, ClassNames::SERVER);

  EnterNamespace(out, interface);
  out << "\n";

  // constructor
  out.Write("%s::%s()\n", q_name.c_str(), bn_name.c_str());
  out << "{\n";
  out.Indent();
  if (interface.IsVintfStability()) {
    out << "::android::internal::Stability::markVintf(this);\n";
  } else {
    out << "::android::internal::Stability::markCompilationUnit(this);\n";
  }
  out.Dedent();
  out << "}\n";
  out << "\n";

  GenerateServerOnTransact(out, interface, typenames, options);

  if (options.Version() > 0) {
    out << "int32_t " << q_name << "::" << kGetInterfaceVersion << "() {\n"
        << "  return " << i_name << "::VERSION;\n"
        << "}\n";
  }
  if (!options.Hash().empty()) {
    out << "std::string " << q_name << "::" << kGetInterfaceHash << "() {\n"
        << "  return " << i_name << "::HASH;\n"
        << "}\n";
  }
  if (options.GenLog()) {
    out << "std::function<void(const " + q_name + "::TransactionLog&)> " << q_name
        << "::logFunc;\n";
  }

  LeaveNamespace(out, interface);
}

void GenerateInterfaceSource(CodeWriter& out, const AidlInterface& interface,
                             const AidlTypenames& typenames, const Options&) {
  out << "#include <" << HeaderFile(interface, ClassNames::RAW, false) << ">\n";
  out << "#include <" << HeaderFile(interface, ClassNames::CLIENT, false) << ">\n";

  EnterNamespace(out, interface);

  if (auto parent = interface.GetParentType(); parent) {
    out << fmt::format("DO_NOT_DIRECTLY_USE_ME_IMPLEMENT_META_NESTED_INTERFACE({}, {}, \"{}\")\n",
                       GetQualifiedName(*parent, ClassNames::MAYBE_INTERFACE),
                       ClassName(interface, ClassNames::BASE), interface.GetDescriptor());
  } else {
    out << fmt::format("DO_NOT_DIRECTLY_USE_ME_IMPLEMENT_META_INTERFACE({}, \"{}\")\n",
                       ClassName(interface, ClassNames::BASE), interface.GetDescriptor());
  }

  GenerateConstantDefinitions(out, interface, typenames, /*template_decl=*/"",
                              ClassName(interface, ClassNames::INTERFACE));

  LeaveNamespace(out, interface);
}

void GenerateClientClassDecl(CodeWriter& out, const AidlInterface& interface,
                             const AidlTypenames& typenames, const Options& options) {
  const string bp_name = ClassName(interface, ClassNames::CLIENT);
  const string iface = ClassName(interface, ClassNames::INTERFACE);

  out << "class";
  GenerateDeprecated(out, interface);
  out << " " << bp_name << " : public ::android::BpInterface<" << iface << "> {\n";
  out << "public:\n";
  out.Indent();
  out << "explicit " << bp_name << "(const ::android::sp<::android::IBinder>& " << kImplVarName
      << ");\n";
  out << "virtual ~" << bp_name << "() = default;\n";

  for (const auto& method : interface.GetMethods()) {
    if (method->IsUserDefined()) {
      GenerateMethodDecl(out, typenames, *method, /*clazz=*/"");
      out << " override";
      GenerateDeprecated(out, *method);
      out << ";\n";
    } else if (method->GetName() == kGetInterfaceVersion && options.Version() > 0) {
      out << "int32_t " << method->GetName() << "() override;\n";
    } else if (method->GetName() == kGetInterfaceHash && !options.Hash().empty()) {
      out << "std::string " << method->GetName() << "() override;\n";
    }
  }

  if (options.GenLog()) {
    out << kTransactionLogStruct;
    out << "static std::function<void(const TransactionLog&)> logFunc;\n";
  }
  out.Dedent();

  if (options.Version() > 0 || !options.Hash().empty()) {
    out << "private:\n";
    out.Indent();
    if (options.Version() > 0) {
      out << "int32_t cached_version_ = -1;\n";
    }
    if (!options.Hash().empty()) {
      out << "std::string cached_hash_ = \"-1\";\n";
      out << "std::mutex cached_hash_mutex_;\n";
    }
    out.Dedent();
  }

  out << "};  // class " << bp_name << "\n";
}

void GenerateClientHeader(CodeWriter& out, const AidlInterface& interface,
                          const AidlTypenames& typenames, const Options& options) {
  out << "#pragma once\n\n";
  out << "#include <" << kIBinderHeader << ">\n";
  out << "#include <" << kIInterfaceHeader << ">\n";
  out << "#include <utils/Errors.h>\n";
  out << "#include <" << HeaderFile(interface, ClassNames::RAW, false) << ">\n";
  if (options.GenLog()) {
    out << "#include <functional>\n";  // for std::function
    out << "#include <android/binder_to_string.h>\n";
  }
  out << "\n";
  EnterNamespace(out, interface);
  GenerateClientClassDecl(out, interface, typenames, options);
  LeaveNamespace(out, interface);
}

// Some interfaces are declared in .aidl files, but defined elsewhere.
// These interfaces can not have Delegators and need to be avoided.
// TODO(b/242920522) These should all be defined in .aidl files.
bool isKnownUndefinedInterface(const std::string& canonicalName) {
  static const auto* kKnownUndefinedInterfaces = new std::set<std::string>{
      "android.hardware.ICamera", "android.hardware.ICameraClient",
      "android.IOMXNode",         "android.IMediaExtractor",
      "android.IDataSource",
  };
  return kKnownUndefinedInterfaces->find(canonicalName) != kKnownUndefinedInterfaces->end();
};

bool isDelegateable(const AidlTypeSpecifier& type) {
  return type.GetDefinedType() && type.GetDefinedType()->AsInterface() &&
         !isKnownUndefinedInterface(type.GetDefinedType()->GetCanonicalName()) && !type.IsArray();
}

void wrapDelegate(CodeWriter& out, const std::string& argName, const AidlTypeSpecifier& type,
                  bool in) {
  const std::string argRef = in ? argName : "*" + argName;
  const std::string targetArgName = in ? "_" + argName : argName;
  const std::string targetArgRef = in ? targetArgName : "*" + targetArgName;
  // input binders need local variables for each arg to pass to the delegate
  // because the parameters are const
  if (in) {
    out << "::android::sp<::" << Join(type.GetSplitName(), "::") << "Delegator> " << targetArgName
        << ";\n";
  }
  out << "if (" << argRef << ") {\n";
  out.Indent();
  out << targetArgRef << " = ::android::sp<::" << Join(type.GetSplitName(), "::")
      << "Delegator>::cast(delegate(" << argRef << "));\n";
  out.Dedent();
  out << "}\n";
}

void GenerateServerClassDecl(CodeWriter& out, const AidlInterface& interface,
                             const AidlTypenames& typenames, const Options& options) {
  const string bn_name = ClassName(interface, ClassNames::SERVER);
  const string iface = ClassName(interface, ClassNames::INTERFACE);

  out << "class";
  GenerateDeprecated(out, interface);
  out << " " << bn_name << " : public "
      << "::android::BnInterface<" << iface << "> {\n";
  out << "public:\n";
  out.Indent();
  for (const auto& method : interface.GetMethods()) {
    out << "static constexpr uint32_t TRANSACTION_" << method->GetName() << " = "
        << "::android::IBinder::FIRST_CALL_TRANSACTION + " << std::to_string(method->GetId())
        << ";\n";
  }
  out << "explicit " << bn_name << "();\n";
  out << fmt::format("{} onTransact(uint32_t {}, const {}& {}, {}* {}, uint32_t {}) override;\n",
                     kAndroidStatusLiteral, kCodeVarName, kAndroidParcelLiteral, kDataVarName,
                     kAndroidParcelLiteral, kReplyVarName, kFlagsVarName);
  if (options.Version() > 0) {
    out << "int32_t " << kGetInterfaceVersion << "();\n";
  }
  if (!options.Hash().empty()) {
    out << "std::string " << kGetInterfaceHash << "();\n";
  }
  if (options.GenLog()) {
    out << kTransactionLogStruct;
    out << "static std::function<void(const TransactionLog&)> logFunc;\n";
  }
  out.Dedent();
  out << "};  // class " << bn_name << "\n\n";

  std::string d_name = ClassName(interface, ClassNames::DELEGATOR_IMPL);
  out << "class";
  GenerateDeprecated(out, interface);
  out << " " << d_name << " : public " << bn_name << " {\n";
  out << "public:\n";
  out.Indent();
  out << "explicit " << d_name << "("
      << StringPrintf("const ::android::sp<%s> &impl", iface.c_str()) << ") "
      << StringPrintf(": %s(impl)", kDelegateImplVarName) << " {}\n\n";
  out << "::android::sp<" << iface << "> getImpl() { return " << kDelegateImplVarName << "; }\n";
  for (const auto& method : interface.GetMethods()) {
    if (method->IsUserDefined()) {
      GenerateMethodDecl(out, typenames, *method, /*clazz=*/"");
      out << " override";
      GenerateDeprecated(out, *method);

      std::vector<std::string> args;

      // arg name, type
      std::vector<pair<const std::string, const AidlTypeSpecifier&>> outBinders;
      std::vector<pair<const std::string, const AidlTypeSpecifier&>> inBinders;
      for (const auto& arg : method->GetArguments()) {
        if (isDelegateable(arg->GetType())) {
          if (arg->IsOut()) {
            outBinders.push_back({arg->GetName(), arg->GetType()});
          } else if (arg->IsIn()) {
            inBinders.push_back({arg->GetName(), arg->GetType()});
          } else {
            AIDL_FATAL(*arg) << "inout interface?";
          }
          AIDL_FATAL_IF(!arg->IsIn() && !arg->IsOut(), *arg) << "Not in or out?";
          args.push_back("_" + arg->GetName());
        } else {
          if (IsNonCopyableType(arg->GetType(), typenames)) {
            args.push_back(StringPrintf("std::move(%s)", arg->GetName().c_str()));
          } else {
            args.push_back(arg->GetName());
          }
        }
      }
      if (method->GetType().GetName() != "void") {
        if (isDelegateable(method->GetType())) {
          outBinders.push_back({kReturnVarName, method->GetType()});
        }
        args.push_back(kReturnVarName);
      }
      out << " {\n";
      out.Indent();
      for (const auto binder : inBinders) {
        wrapDelegate(out, binder.first, binder.second, true);
      }
      if (outBinders.empty()) {
        out << "return " << kDelegateImplVarName << "->" << method->GetName() << "("
            << base::Join(args, ", ") << ");\n";
      } else {
        out << "auto _status = " << kDelegateImplVarName << "->" << method->GetName() << "("
            << base::Join(args, ", ") << ");\n";
        for (const auto& binder : outBinders) {
          wrapDelegate(out, binder.first, binder.second, false);
        }
        out << "return _status;\n";
      }
      out.Dedent();
      out << "}\n";
    } else if (method->GetName() == kGetInterfaceVersion && options.Version()) {
      out << "int32_t " << kGetInterfaceVersion << "()"
          << " override {\n";
      out.Indent();
      out << "int32_t _delegator_ver = " << bn_name << "::" << kGetInterfaceVersion << "();\n";
      out << "int32_t _impl_ver = " << kDelegateImplVarName << "->" << kGetInterfaceVersion
          << "();\n";
      out << "return _delegator_ver < _impl_ver ? _delegator_ver : _impl_ver;\n";
      out.Dedent();
      out << "}\n";
    } else if (method->GetName() == kGetInterfaceHash && !options.Hash().empty()) {
      out << "std::string " << kGetInterfaceHash << "()"
          << " override {\n";
      out << "  return " << kDelegateImplVarName << "->" << kGetInterfaceHash << "();\n";
      out << "}\n";
    }
  }
  out.Dedent();
  out << "private:\n";
  out.Indent();
  out << "::android::sp<" << iface << "> " << kDelegateImplVarName << ";\n";
  out.Dedent();
  out << "};  // class " << d_name << "\n";
}

// Collect all includes for the type's server header. Nested types are visited as well via
// VisitTopDown.
void GenerateServerHeaderIncludes(CodeWriter& out, const AidlDefinedType& defined_type,
                                  const AidlTypenames& typenames, const Options& options) {
  struct Visitor : AidlVisitor {
    const AidlTypenames& typenames;
    const Options& options;
    std::set<std::string> includes;
    Visitor(const AidlTypenames& typenames, const Options& options)
        : typenames(typenames), options(options) {}

    // Collect includes for each type reference
    void Visit(const AidlTypeSpecifier& type) override {
      // Add Bn* header files for types used in this header. The *Delegator
      // definitions require them.
      const auto defined_type = type.GetDefinedType();
      if (defined_type && defined_type->AsInterface()) {
        if (!isKnownUndefinedInterface(defined_type->GetCanonicalName())) {
          includes.insert(HeaderFile(*defined_type, ClassNames::SERVER, /*use_os_sep=*/false));
        }
      }
    }

    // Collect implementation-specific includes for each type definition
    void Visit(const AidlInterface& iface) override {
      includes.insert(HeaderFile(iface, ClassNames::SERVER, false));
    }
  } v(typenames, options);
  VisitTopDown(v, defined_type);

  v.includes.insert(kBinderDelegateHeader);
  for (const auto& path : v.includes) {
    out << "#include <" << path << ">\n";
  }
  out << "\n";
}

void GenerateServerHeader(CodeWriter& out, const AidlInterface& interface,
                          const AidlTypenames& typenames, const Options& options) {
  out << "#pragma once\n\n";
  out << "#include <binder/IInterface.h>\n";
  out << "#include <" << HeaderFile(interface, ClassNames::RAW, false) << ">\n";
  if (options.GenLog()) {
    out << "#include <functional>\n";  // for std::function
    out << "#include <android/binder_to_string.h>\n";
  }
  GenerateServerHeaderIncludes(out, interface, typenames, options);
  out << "\n";
  EnterNamespace(out, interface);
  GenerateServerClassDecl(out, interface, typenames, options);
  LeaveNamespace(out, interface);
}

void GenerateClassDecl(CodeWriter& out, const AidlDefinedType& defined_type,
                       const AidlTypenames& typenames, const Options& options);

void GenerateNestedTypeDecls(CodeWriter& out, const AidlDefinedType& type,
                             const AidlTypenames& typenames, const Options& options) {
  auto visit = [&](const auto& nested) { GenerateClassDecl(out, nested, typenames, options); };
  AIDL_FATAL_IF(!TopologicalVisit(type.GetNestedTypes(), visit), type) << "Cycle detected.";
}

void GenerateInterfaceClassDecl(CodeWriter& out, const AidlInterface& interface,
                                const AidlTypenames& typenames, const Options& options) {
  const string i_name = ClassName(interface, ClassNames::INTERFACE);
  out << "class " << ClassName(interface, ClassNames::DELEGATOR_IMPL) << ";\n\n";
  out << "class";
  GenerateDeprecated(out, interface);
  out << " " << i_name << " : public ::android::IInterface {\n";
  out << "public:\n";
  out.Indent();
  out << "typedef " << ClassName(interface, ClassNames::DELEGATOR_IMPL) << " DefaultDelegator;\n";
  out << "DECLARE_META_INTERFACE(" << ClassName(interface, ClassNames::BASE) << ")\n";
  if (options.Version() > 0) {
    if (options.IsLatestUnfrozenVersion()) {
      out << "static inline const int32_t VERSION = true ? "
          << std::to_string(options.PreviousVersion()) << " : " << std::to_string(options.Version())
          << ";\n";
    } else {
      out << "static inline const int32_t VERSION = " << std::to_string(options.Version()) << ";\n";
    }
  }
  if (!options.Hash().empty()) {
    if (options.IsLatestUnfrozenVersion()) {
      out << "static inline const std::string HASH = true ? \"" << options.PreviousHash()
          << "\" : \"" << options.Hash() << "\";\n";
    } else {
      out << "static inline const std::string HASH = \"" << options.Hash() << "\";\n";
    }
  }
  GenerateNestedTypeDecls(out, interface, typenames, options);
  GenerateConstantDeclarations(out, interface, typenames);
  for (const auto& method : interface.GetMethods()) {
    if (method->IsUserDefined()) {
      out << "virtual ";
      GenerateMethodDecl(out, typenames, *method, /*clazz=*/"");
      GenerateDeprecated(out, *method);
      out << " = 0;\n";
    } else if (method->GetName() == kGetInterfaceVersion && options.Version() > 0) {
      out << "virtual int32_t " << method->GetName() << "() = 0;\n";
    } else if (method->GetName() == kGetInterfaceHash && !options.Hash().empty()) {
      out << "virtual std::string " << method->GetName() << "() = 0;\n";
    }
  }
  out.Dedent();
  out << "};  // class " << i_name << "\n";
  out << "\n";

  // Implement the default impl class.
  const string default_impl = ClassName(interface, ClassNames::DEFAULT_IMPL);
  out << "class";
  GenerateDeprecated(out, interface);
  out << " " << default_impl << " : public " << i_name << " {\n";
  out << "public:\n";
  out.Indent();
  // onAsBinder returns nullptr as this interface is not associated with a
  // real binder.
  out << "::android::IBinder* onAsBinder() override {\n"
      << "  return nullptr;\n"
      << "}\n";

  // Each interface method by default returns UNKNOWN_TRANSACTION with is
  // the same status that is returned by transact() when the method is
  // not implemented in the server side. In other words, these default
  // methods do nothing; they only exist to aid making a real default
  // impl class without having to override all methods in an interface.
  for (const auto& method : interface.GetMethods()) {
    if (method->IsUserDefined()) {
      out << "::android::binder::Status " << method->GetName() << "("
          << GenerateArgList(typenames, *method, /*for_declaration=*/true, /*type_name_only=*/true)
          << ") override";
      GenerateDeprecated(out, *method);
      out << " {\n"
          << "  return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);\n"
          << "}\n";
    } else if (method->GetName() == kGetInterfaceVersion && options.Version() > 0) {
      out << "int32_t " << kGetInterfaceVersion << "() override {\n"
          << "  return 0;\n"
          << "}\n";
    } else if (method->GetName() == kGetInterfaceHash && !options.Hash().empty()) {
      out << "std::string " << kGetInterfaceHash << "() override {\n"
          << "  return \"\";\n"
          << "}\n";
    }
  }
  out.Dedent();
  out << "};  // class " << default_impl << "\n";

  // When an interface is nested, every class should be defined together here
  // because we don't have separate headers for them.
  // (e.g. IFoo, IFooDefault, BpFoo, BnFoo, IFooDelegator)
  if (interface.GetParentType()) {
    GenerateClientClassDecl(out, interface, typenames, options);
    GenerateServerClassDecl(out, interface, typenames, options);
  }
}

void GenerateReadFromParcel(CodeWriter& out, const AidlStructuredParcelable& parcel,
                            const AidlTypenames& typenames) {
  out << "::android::status_t _aidl_ret_status = ::android::OK;\n";
  out << "size_t _aidl_start_pos = _aidl_parcel->dataPosition();\n";
  out << "int32_t _aidl_parcelable_raw_size = 0;\n";
  out << "_aidl_ret_status = _aidl_parcel->readInt32(&_aidl_parcelable_raw_size);\n";
  out << "if (((_aidl_ret_status) != (::android::OK))) {\n";
  out << "  return _aidl_ret_status;\n";
  out << "}\n";
  out << "if (_aidl_parcelable_raw_size < 4) return ::android::BAD_VALUE;\n";
  out << "size_t _aidl_parcelable_size = static_cast<size_t>(_aidl_parcelable_raw_size);\n";
  out << "if (_aidl_start_pos > INT32_MAX - _aidl_parcelable_size) return ::android::BAD_VALUE;\n";
  for (const auto& variable : parcel.GetFields()) {
    string method = ParcelReadMethodOf(variable->GetType(), typenames);
    string arg = ParcelReadCastOf(variable->GetType(), typenames, "&" + variable->GetName());
    out << "if (_aidl_parcel->dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) {\n";
    out << "  _aidl_parcel->setDataPosition(_aidl_start_pos + _aidl_parcelable_size);\n";
    out << "  return _aidl_ret_status;\n";
    out << "}\n";
    if (variable->IsNew() && ShouldForceDowngradeFor(CommunicationSide::READ)) {
      out << "if (false) {\n";
      out.Indent();
    }
    out << "_aidl_ret_status = _aidl_parcel->" << method << "(" << arg << ");\n";
    out << "if (((_aidl_ret_status) != (::android::OK))) {\n";
    out << "  return _aidl_ret_status;\n";
    out << "}\n";
    if (variable->IsNew() && ShouldForceDowngradeFor(CommunicationSide::READ)) {
      out.Dedent();
      out << "}\n";
    }
  }
  out << "_aidl_parcel->setDataPosition(_aidl_start_pos + _aidl_parcelable_size);\n";
  out << "return _aidl_ret_status;\n";
}

void GenerateWriteToParcel(CodeWriter& out, const AidlStructuredParcelable& parcel,
                           const AidlTypenames& typenames) {
  out << "::android::status_t _aidl_ret_status = ::android::OK;\n";
  out << "size_t _aidl_start_pos = " << kParcelVarName << "->dataPosition();\n";
  out << kParcelVarName << "->writeInt32(0);\n";
  for (const auto& variable : parcel.GetFields()) {
    string method = ParcelWriteMethodOf(variable->GetType(), typenames);
    string arg = ParcelWriteCastOf(variable->GetType(), typenames, variable->GetName());
    if (variable->IsNew() && ShouldForceDowngradeFor(CommunicationSide::WRITE)) {
      out << "if (false) {\n";
      out.Indent();
    }
    out << "_aidl_ret_status = " << kParcelVarName << "->" << method << "(" << arg << ");\n";
    out << "if (((_aidl_ret_status) != (::android::OK))) {\n";
    out << "  return _aidl_ret_status;\n";
    out << "}\n";
    if (variable->IsNew() && ShouldForceDowngradeFor(CommunicationSide::WRITE)) {
      out.Dedent();
      out << "}\n";
    }
  }
  out << "size_t _aidl_end_pos = " << kParcelVarName << "->dataPosition();\n";
  out << kParcelVarName << "->setDataPosition(_aidl_start_pos);\n";
  out << kParcelVarName << "->writeInt32(static_cast<int32_t>(_aidl_end_pos - _aidl_start_pos));\n";
  out << kParcelVarName << "->setDataPosition(_aidl_end_pos);\n";
  out << "return _aidl_ret_status;\n";
}

ParcelWriterContext GetParcelWriterContext(const AidlTypenames& typenames) {
  return ParcelWriterContext{
      .status_type = kAndroidStatusLiteral,
      .status_ok = kAndroidStatusOk,
      .status_bad = kAndroidStatusBadValue,
      .read_func =
          [&](CodeWriter& out, const string& var, const AidlTypeSpecifier& type) {
            out << fmt::format("{}->{}({})", kParcelVarName, ParcelReadMethodOf(type, typenames),
                               ParcelReadCastOf(type, typenames, "&" + var));
          },
      .write_func =
          [&](CodeWriter& out, const string& value, const AidlTypeSpecifier& type) {
            out << fmt::format("{}->{}({})", kParcelVarName, ParcelWriteMethodOf(type, typenames),
                               ParcelWriteCastOf(type, typenames, value));
          },
  };
}

void GenerateReadFromParcel(CodeWriter& out, const AidlUnionDecl& decl,
                            const AidlTypenames& typenames) {
  UnionWriter uw{decl, typenames, &CppNameOf, &ConstantValueDecorator};
  uw.ReadFromParcel(out, GetParcelWriterContext(typenames));
}

void GenerateWriteToParcel(CodeWriter& out, const AidlUnionDecl& decl,
                           const AidlTypenames& typenames) {
  UnionWriter uw{decl, typenames, &CppNameOf, &ConstantValueDecorator};
  uw.WriteToParcel(out, GetParcelWriterContext(typenames));
}

void GenerateParcelFields(CodeWriter& out, const AidlStructuredParcelable& decl,
                          const AidlTypenames& typenames) {
  for (const auto& variable : decl.GetFields()) {
    const auto& type = variable->GetType();
    std::string cpp_type = CppNameOf(type, typenames);
    out << cpp_type;
    GenerateDeprecated(out, *variable);
    out << " " << variable->GetName();
    if (variable->GetDefaultValue()) {
      out << " = " << variable->ValueString(ConstantValueDecorator);
    } else {
      // Some types needs to be explicitly initialized even when no default value is set.
      // - ParcelableHolder should be initialized with stability
      // - enum should be zero initialized, otherwise the value will be indeterminate
      // - fixed-size arrays should be initialized, otherwise the value will be indeterminate
      if (type.GetName() == "ParcelableHolder") {
        if (decl.IsVintfStability()) {
          out << " { ::android::Parcelable::Stability::STABILITY_VINTF }";
        } else {
          out << " { ::android::Parcelable::Stability::STABILITY_LOCAL }";
        }
      } else if (typenames.GetEnumDeclaration(type) && !type.IsArray()) {
        out << " = " << cpp_type << "(0)";
      } else if (type.IsFixedSizeArray() && !type.IsNullable()) {
        out << " = {{}}";
      }
    }
    out << ";\n";
  }
}

void GenerateParcelFields(CodeWriter& out, const AidlUnionDecl& decl,
                          const AidlTypenames& typenames) {
  UnionWriter uw{decl, typenames, &CppNameOf, &ConstantValueDecorator};
  uw.PublicFields(out);
}

template <typename ParcelableType>
void GenerateParcelClassDecl(CodeWriter& out, const ParcelableType& parcel,
                             const AidlTypenames& typenames, const Options& options) {
  const string clazz = parcel.GetName();

  ClangDiagnosticIgnoreDeprecated guard(out, HasDeprecatedField(parcel));
  out << TemplateDecl(parcel);
  out << "class";
  GenerateDeprecated(out, parcel);
  out << " " << clazz << " : public ::android::Parcelable {\n";
  out << "public:\n";
  out.Indent();

  GenerateNestedTypeDecls(out, parcel, typenames, options);
  GenerateParcelFields(out, parcel, typenames);
  GenerateParcelableComparisonOperators(out, parcel);
  GenerateConstantDeclarations(out, parcel, typenames);

  if (parcel.IsVintfStability()) {
    out << "::android::Parcelable::Stability getStability() const override { return "
           "::android::Parcelable::Stability::STABILITY_VINTF; }\n";
  }

  out << kAndroidStatusLiteral << " readFromParcel(const ::android::Parcel* _aidl_parcel) final;\n";
  out << kAndroidStatusLiteral << " writeToParcel(::android::Parcel* _aidl_parcel) const final;\n";

  const string canonical_name = parcel.GetCanonicalName();
  out << "static const ::android::String16& getParcelableDescriptor() {\n"
      << "  static const ::android::StaticString16 DESCRIPTOR (u\"" << canonical_name << "\");\n"
      << "  return DESCRIPTOR;\n"
      << "}\n";

  GenerateToString(out, parcel);

  out.Dedent();

  if (auto decl = AidlCast<AidlUnionDecl>(parcel); decl) {
    out << "private:\n";
    out.Indent();
    UnionWriter uw{*decl, typenames, &CppNameOf, &ConstantValueDecorator};
    uw.PrivateFields(out);
    out.Dedent();
  }

  out << "};  // class " << clazz << "\n";
}

template <typename T>
void GenerateParcelSource(CodeWriter& out, const T& parcel, const AidlTypenames& typenames,
                          const Options&) {
  string q_name = GetQualifiedName(parcel);
  if (parcel.IsGeneric()) {
    q_name += "<" + Join(parcel.GetTypeParameters(), ",") + ">";
  }

  out << "#include <" << CppHeaderForType(parcel) << ">\n\n";

  EnterNamespace(out, parcel);
  GenerateConstantDefinitions(out, parcel, typenames, TemplateDecl(parcel), q_name);

  {
    ClangDiagnosticIgnoreDeprecated guard(out, HasDeprecatedField(parcel));
    out << TemplateDecl(parcel);
    out << "::android::status_t " << q_name << "::readFromParcel(const ::android::Parcel* "
        << kParcelVarName << ") {\n";
    out.Indent();
    GenerateReadFromParcel(out, parcel, typenames);
    out.Dedent();
    out << "}\n";

    out << TemplateDecl(parcel);
    out << "::android::status_t " << q_name << "::writeToParcel(::android::Parcel* "
        << kParcelVarName << ") const {\n";
    out.Indent();
    GenerateWriteToParcel(out, parcel, typenames);
    out.Dedent();
    out << "}\n";
  }
  LeaveNamespace(out, parcel);
}

void GenerateEnumClassDecl(CodeWriter& out, const AidlEnumDeclaration& enum_decl,
                           const AidlTypenames& typenames) {
  cpp::GenerateEnumClassDecl(out, enum_decl, CppNameOf(enum_decl.GetBackingType(), typenames),
                             ConstantValueDecorator);
}

void GenerateClassDecl(CodeWriter& out, const AidlDefinedType& defined_type,
                       const AidlTypenames& typenames, const Options& options) {
  if (auto iface = AidlCast<AidlInterface>(defined_type); iface) {
    GenerateInterfaceClassDecl(out, *iface, typenames, options);
  } else if (auto parcelable = AidlCast<AidlStructuredParcelable>(defined_type); parcelable) {
    GenerateParcelClassDecl(out, *parcelable, typenames, options);
  } else if (auto union_decl = AidlCast<AidlUnionDecl>(defined_type); union_decl) {
    GenerateParcelClassDecl(out, *union_decl, typenames, options);
  } else if (auto enum_decl = AidlCast<AidlEnumDeclaration>(defined_type); enum_decl) {
    GenerateEnumClassDecl(out, *enum_decl, typenames);
  } else {
    AIDL_FATAL(defined_type) << "Unrecognized type sent for CPP generation.";
  }
}

}  // namespace internals

using namespace internals;

// Collect all includes for the type's header. Nested types are visited as well via VisitTopDown.
void GenerateHeaderIncludes(CodeWriter& out, const AidlDefinedType& defined_type,
                            const AidlTypenames& typenames, const Options& options) {
  struct Visitor : AidlVisitor {
    const AidlTypenames& typenames;
    const Options& options;
    std::set<std::string> includes;
    Visitor(const AidlTypenames& typenames, const Options& options)
        : typenames(typenames), options(options) {}

    // Collect includes for each type reference including built-in type
    void Visit(const AidlTypeSpecifier& type) override {
      cpp::AddHeaders(type, typenames, &includes);
    }

    // Collect implementation-specific includes for each type definition
    void Visit(const AidlInterface& iface) override {
      includes.insert(kIBinderHeader);        // IBinder
      includes.insert(kIInterfaceHeader);     // IInterface
      includes.insert(kStatusHeader);         // Status
      includes.insert(kStrongPointerHeader);  // sp<>

      if (options.GenTraces()) {
        includes.insert(kTraceHeader);
      }

      // For a nested interface, client/server classes are declared the same header as well.
      if (iface.GetParentType()) {
        includes.insert(kBinderDelegateHeader);  // Delegate.h
        // client/server class provides logFunc when gen_log is on
        if (options.GenLog()) {
          includes.insert("functional");                  // std::function for logFunc
          includes.insert("android/binder_to_string.h");  // Generic ToString helper
        }
      }
    }

    void Visit(const AidlStructuredParcelable&) override {
      AddParcelableCommonHeaders();
      includes.insert("tuple");  // std::tie in comparison operators
    }

    void Visit(const AidlUnionDecl& union_decl) override {
      AddParcelableCommonHeaders();
      auto union_headers = cpp::UnionWriter::GetHeaders(union_decl);
      includes.insert(std::begin(union_headers), std::end(union_headers));
    }

    void Visit(const AidlEnumDeclaration&) override {
      includes.insert("array");           // used in enum_values
      includes.insert("binder/Enums.h");  // provides enum_range
      includes.insert("string");          // toString() returns std::string
    }

    void AddParcelableCommonHeaders() {
      includes.insert(kParcelHeader);                 // Parcel in readFromParcel/writeToParcel
      includes.insert(kStatusHeader);                 // Status
      includes.insert(kString16Header);               // String16 in getParcelableDescriptor
      includes.insert("android/binder_to_string.h");  // toString()
    }
  } v(typenames, options);
  VisitTopDown(v, defined_type);

  for (const auto& path : v.includes) {
    out << "#include <" << path << ">\n";
  }
  out << "\n";
  if (v.includes.count("cassert")) {
    // TODO(b/31559095) bionic on host should define __assert2
    out << "#ifndef __BIONIC__\n#define __assert2(a,b,c,d) ((void)0)\n#endif\n\n";
  }
}

// Generic parcelables and enum utilities should be defined in header.
void GenerateHeaderDefinitions(CodeWriter& out, const AidlDefinedType& defined_type,
                               const AidlTypenames& typenames, const Options& options) {
  struct Visitor : AidlVisitor {
    CodeWriter& out;
    const AidlTypenames& typenames;
    const Options& options;
    Visitor(CodeWriter& out, const AidlTypenames& typenames, const Options& options)
        : out(out), typenames(typenames), options(options) {}

    void Visit(const AidlEnumDeclaration& enum_decl) override {
      const auto backing_type = CppNameOf(enum_decl.GetBackingType(), typenames);
      EnterNamespace(out, enum_decl);
      out << GenerateEnumToString(enum_decl, backing_type);
      LeaveNamespace(out, enum_decl);

      out << "namespace android {\n";
      out << "namespace internal {\n";
      out << GenerateEnumValues(enum_decl, {""});
      out << "}  // namespace internal\n";
      out << "}  // namespace android\n";
    }

    void Visit(const AidlStructuredParcelable& parcelable) override {
      if (parcelable.IsGeneric()) {
        GenerateParcelSource(out, parcelable, typenames, options);
      }
    }

    void Visit(const AidlUnionDecl& union_decl) override {
      if (union_decl.IsGeneric()) {
        GenerateParcelSource(out, union_decl, typenames, options);
      }
    }

  } v(out, typenames, options);
  VisitTopDown(v, defined_type);
}

void GenerateHeader(CodeWriter& out, const AidlDefinedType& defined_type,
                    const AidlTypenames& typenames, const Options& options) {
  if (auto parcelable = AidlCast<AidlParcelable>(defined_type); parcelable) {
    out << "#error TODO(b/111362593) parcelables do not have headers";
    return;
  }
  out << "#pragma once\n\n";
  GenerateHeaderIncludes(out, defined_type, typenames, options);
  GenerateForwardDecls(out, defined_type, false);
  EnterNamespace(out, defined_type);
  // Each class decl contains its own nested types' class decls
  GenerateClassDecl(out, defined_type, typenames, options);
  LeaveNamespace(out, defined_type);
  GenerateHeaderDefinitions(out, defined_type, typenames, options);
}

void GenerateClientHeader(CodeWriter& out, const AidlDefinedType& defined_type,
                          const AidlTypenames& typenames, const Options& options) {
  if (auto iface = AidlCast<AidlInterface>(defined_type); iface) {
    GenerateClientHeader(out, *iface, typenames, options);
  } else if (auto parcelable = AidlCast<AidlStructuredParcelable>(defined_type); parcelable) {
    out << "#error TODO(b/111362593) parcelables do not have bp classes";
  } else if (auto union_decl = AidlCast<AidlUnionDecl>(defined_type); union_decl) {
    out << "#error TODO(b/111362593) parcelables do not have bp classes";
  } else if (auto enum_decl = AidlCast<AidlEnumDeclaration>(defined_type); enum_decl) {
    out << "#error TODO(b/111362593) enums do not have bp classes";
  } else if (auto parcelable = AidlCast<AidlParcelable>(defined_type); parcelable) {
    out << "#error TODO(b/111362593) parcelables do not have bp classes";
  } else {
    AIDL_FATAL(defined_type) << "Unrecognized type sent for CPP generation.";
  }
}

void GenerateServerHeader(CodeWriter& out, const AidlDefinedType& defined_type,
                          const AidlTypenames& typenames, const Options& options) {
  if (auto iface = AidlCast<AidlInterface>(defined_type); iface) {
    GenerateServerHeader(out, *iface, typenames, options);
  } else if (auto parcelable = AidlCast<AidlStructuredParcelable>(defined_type); parcelable) {
    out << "#error TODO(b/111362593) parcelables do not have bn classes";
  } else if (auto union_decl = AidlCast<AidlUnionDecl>(defined_type); union_decl) {
    out << "#error TODO(b/111362593) parcelables do not have bn classes";
  } else if (auto enum_decl = AidlCast<AidlEnumDeclaration>(defined_type); enum_decl) {
    out << "#error TODO(b/111362593) enums do not have bn classes";
  } else if (auto parcelable = AidlCast<AidlParcelable>(defined_type); parcelable) {
    out << "#error TODO(b/111362593) parcelables do not have bn classes";
  } else {
    AIDL_FATAL(defined_type) << "Unrecognized type sent for CPP generation.";
  }
}

void GenerateSource(CodeWriter& out, const AidlDefinedType& defined_type,
                    const AidlTypenames& typenames, const Options& options) {
  struct Visitor : AidlVisitor {
    CodeWriter& out;
    const AidlTypenames& typenames;
    const Options& options;
    Visitor(CodeWriter& out, const AidlTypenames& typenames, const Options& options)
        : out(out), typenames(typenames), options(options) {}

    void Visit(const AidlInterface& interface) override {
      GenerateInterfaceSource(out, interface, typenames, options);
      GenerateClientSource(out, interface, typenames, options);
      GenerateServerSource(out, interface, typenames, options);
    }

    void Visit(const AidlStructuredParcelable& parcelable) override {
      if (!parcelable.IsGeneric()) {
        GenerateParcelSource(out, parcelable, typenames, options);
      } else {
        out << "\n";
      }
    }

    void Visit(const AidlUnionDecl& union_decl) override {
      if (!union_decl.IsGeneric()) {
        GenerateParcelSource(out, union_decl, typenames, options);
      } else {
        out << "\n";
      }
    }

    void Visit(const AidlEnumDeclaration& enum_decl) override {
      if (!enum_decl.GetParentType()) {
        out << "// This file is intentionally left blank as placeholder for enum declaration.\n";
      }
    }

    void Visit(const AidlParcelable& parcelable) override {
      AIDL_FATAL_IF(parcelable.GetParentType(), parcelable)
          << "Unstructured parcelable can't be nested.";
      out << "// This file is intentionally left blank as placeholder for parcel declaration.\n";
    }
  } v(out, typenames, options);
  VisitTopDown(v, defined_type);
}

bool GenerateCpp(const string& output_file, const Options& options, const AidlTypenames& typenames,
                 const AidlDefinedType& defined_type, const IoDelegate& io_delegate) {
  if (!ValidateOutputFilePath(output_file, options, defined_type)) {
    return false;
  }

  using GenFn = void (*)(CodeWriter& out, const AidlDefinedType& defined_type,
                         const AidlTypenames& typenames, const Options& options);
  // Wrap Generate* function to handle CodeWriter for a file.
  auto gen = [&](auto file, GenFn fn) {
    unique_ptr<CodeWriter> writer(io_delegate.GetCodeWriter(file));

    GenerateAutoGenHeader(*writer, options);

    fn(*writer, defined_type, typenames, options);
    AIDL_FATAL_IF(!writer->Close(), defined_type) << "I/O Error!";
    return true;
  };

  return gen(options.OutputHeaderDir() + HeaderFile(defined_type, ClassNames::RAW),
             &GenerateHeader) &&
         gen(options.OutputHeaderDir() + HeaderFile(defined_type, ClassNames::CLIENT),
             &GenerateClientHeader) &&
         gen(options.OutputHeaderDir() + HeaderFile(defined_type, ClassNames::SERVER),
             &GenerateServerHeader) &&
         gen(output_file, &GenerateSource);
}

}  // namespace cpp
}  // namespace aidl
}  // namespace android
