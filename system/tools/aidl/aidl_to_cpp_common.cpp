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
#include "aidl_to_cpp_common.h"

#include <android-base/format.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>

#include <limits>
#include <set>
#include <unordered_map>

#include "comments.h"
#include "logging.h"
#include "os.h"

using ::android::base::Join;
using ::android::base::Split;

namespace android {
namespace aidl {
namespace cpp {

char kTransactionLogStruct[] = R"(struct TransactionLog {
  double duration_ms;
  std::string interface_name;
  std::string method_name;
  const void* proxy_address;
  const void* stub_address;
  std::vector<std::pair<std::string, std::string>> input_args;
  std::vector<std::pair<std::string, std::string>> output_args;
  std::string result;
  std::string exception_message;
  int32_t exception_code;
  int32_t transaction_error;
  int32_t service_specific_error_code;
};
)";

bool HasDeprecatedField(const AidlParcelable& parcelable) {
  return std::any_of(parcelable.GetFields().begin(), parcelable.GetFields().end(),
                     [](const auto& field) { return field->IsDeprecated(); });
}

string ClassName(const AidlDefinedType& defined_type, ClassNames type) {
  if (type == ClassNames::MAYBE_INTERFACE && defined_type.AsInterface()) {
    type = ClassNames::INTERFACE;
  }
  string base_name = defined_type.GetName();
  if (base_name.length() >= 2 && base_name[0] == 'I' && isupper(base_name[1])) {
    base_name = base_name.substr(1);
  }

  switch (type) {
    case ClassNames::CLIENT:
      return "Bp" + base_name;
    case ClassNames::SERVER:
      return "Bn" + base_name;
    case ClassNames::INTERFACE:
      return "I" + base_name;
    case ClassNames::DEFAULT_IMPL:
      return "I" + base_name + "Default";
    case ClassNames::BASE:
      return base_name;
    case ClassNames::DELEGATOR_IMPL:
      return "I" + base_name + "Delegator";
    case ClassNames::RAW:
      [[fallthrough]];
    default:
      return defined_type.GetName();
  }
}

std::string HeaderFile(const AidlDefinedType& defined_type, ClassNames class_type,
                       bool use_os_sep) {
  // For a nested type, we need to include its top-most parent type's header.
  const AidlDefinedType* toplevel = &defined_type;
  for (auto parent = toplevel->GetParentType(); parent;) {
    // When including the parent's header, it should be always RAW
    class_type = ClassNames::RAW;
    toplevel = parent;
    parent = toplevel->GetParentType();
  }
  AIDL_FATAL_IF(toplevel->GetParentType() != nullptr, defined_type)
      << "Can't find a top-level decl";

  char separator = (use_os_sep) ? OS_PATH_SEPARATOR : '/';
  vector<string> paths = toplevel->GetSplitPackage();
  paths.push_back(ClassName(*toplevel, class_type));
  return Join(paths, separator) + ".h";
}

// Ensures that output_file is  <out_dir>/<packagename>/<typename>.cpp
bool ValidateOutputFilePath(const string& output_file, const Options& options,
                            const AidlDefinedType& defined_type) {
  const auto& out_dir =
      !options.OutputDir().empty() ? options.OutputDir() : options.OutputHeaderDir();
  if (output_file.empty() || !android::base::StartsWith(output_file, out_dir)) {
    // If output_file is not set (which happens in the unit tests) or is outside of out_dir, we can
    // help but accepting it, because the path is what the user has requested.
    return true;
  }

  string canonical_name = defined_type.GetCanonicalName();
  std::replace(canonical_name.begin(), canonical_name.end(), '.', OS_PATH_SEPARATOR);
  const string expected = out_dir + canonical_name + ".cpp";
  if (expected != output_file) {
    AIDL_ERROR(defined_type) << "Output file is expected to be at " << expected << ", but is "
                             << output_file << ".\n If this is an Android platform "
                             << "build, consider providing the input AIDL files using a filegroup "
                             << "with `path:\"<base>\"` so that the AIDL files are located at "
                             << "<base>/<packagename>/<typename>.aidl.";
    return false;
  }
  return true;
}

void EnterNamespace(CodeWriter& out, const AidlDefinedType& defined_type) {
  const std::vector<std::string> packages = defined_type.GetSplitPackage();
  for (const std::string& package : packages) {
    out << "namespace " << package << " {\n";
  }
}
void LeaveNamespace(CodeWriter& out, const AidlDefinedType& defined_type) {
  const std::vector<std::string> packages = defined_type.GetSplitPackage();
  for (auto it = packages.rbegin(); it != packages.rend(); ++it) {
    out << "}  // namespace " << *it << "\n";
  }
}

string BuildVarName(const AidlArgument& a) {
  string prefix = "out_";
  if (a.GetDirection() & AidlArgument::IN_DIR) {
    prefix = "in_";
  }
  return prefix + a.GetName();
}

void WriteLogForArgument(CodeWriter& w, const AidlArgument& a, bool is_server,
                         const string& log_var, bool is_ndk) {
  const string var_name = is_server || is_ndk ? BuildVarName(a) : a.GetName();
  const bool is_pointer = a.IsOut() && !is_server;
  const string value_expr = (is_pointer ? "*" : "") + var_name;
  w << log_var
    << ".emplace_back(\"" + var_name + "\", ::android::internal::ToString(" + value_expr + "));\n";
}

const string GenLogBeforeExecute(const string className, const AidlMethod& method, bool isServer,
                                 bool isNdk) {
  string code;
  CodeWriterPtr writer = CodeWriter::ForString(&code);
  (*writer) << className << "::TransactionLog _transaction_log;\n";

  (*writer) << "if (" << className << "::logFunc != nullptr) {\n";
  (*writer).Indent();

  for (const auto& a : method.GetInArguments()) {
    WriteLogForArgument(*writer, *a, isServer, "_transaction_log.input_args", isNdk);
  }

  (*writer).Dedent();
  (*writer) << "}\n";

  (*writer) << "auto _log_start = std::chrono::steady_clock::now();\n";
  writer->Close();
  return code;
}

const string GenLogAfterExecute(const string className, const AidlInterface& interface,
                                const AidlMethod& method, const string& statusVarName,
                                const string& returnVarName, bool isServer, bool isNdk) {
  string code;
  CodeWriterPtr writer = CodeWriter::ForString(&code);

  (*writer) << "if (" << className << "::logFunc != nullptr) {\n";
  (*writer).Indent();
  const auto address = (isNdk && isServer) ? "_aidl_impl.get()" : "static_cast<const void*>(this)";
  (*writer) << "auto _log_end = std::chrono::steady_clock::now();\n";
  (*writer) << "_transaction_log.duration_ms = std::chrono::duration<double, std::milli>(_log_end "
               "- _log_start).count();\n";
  (*writer) << "_transaction_log.interface_name = \"" << interface.GetCanonicalName() << "\";\n";
  (*writer) << "_transaction_log.method_name = \"" << method.GetName() << "\";\n";
  (*writer) << "_transaction_log.stub_address = " << (isServer ? address : "nullptr") << ";\n";
  (*writer) << "_transaction_log.proxy_address = " << (isServer ? "nullptr" : address) << ";\n";

  if (isNdk) {
    (*writer) << "_transaction_log.exception_code = AStatus_getExceptionCode(" << statusVarName
              << ".get());\n";
    (*writer) << "_transaction_log.exception_message = AStatus_getMessage(" << statusVarName
              << ".get());\n";
    (*writer) << "_transaction_log.transaction_error = AStatus_getStatus(" << statusVarName
              << ".get());\n";
    (*writer) << "_transaction_log.service_specific_error_code = AStatus_getServiceSpecificError("
              << statusVarName << ".get());\n";
  } else {
    (*writer) << "_transaction_log.exception_code = " << statusVarName << ".exceptionCode();\n";
    (*writer) << "_transaction_log.exception_message = " << statusVarName
              << ".exceptionMessage();\n";
    (*writer) << "_transaction_log.transaction_error = " << statusVarName
              << ".transactionError();\n";
    (*writer) << "_transaction_log.service_specific_error_code = " << statusVarName
              << ".serviceSpecificErrorCode();\n";
  }

  for (const auto& a : method.GetOutArguments()) {
    WriteLogForArgument(*writer, *a, isServer, "_transaction_log.output_args", isNdk);
  }

  if (method.GetType().GetName() != "void") {
    const string expr = (isServer ? "" : "*") + returnVarName;
    (*writer) << "_transaction_log.result = ::android::internal::ToString(" << expr << ");\n";
  }

  // call the user-provided function with the transaction log object
  (*writer) << className << "::logFunc(_transaction_log);\n";

  (*writer).Dedent();
  (*writer) << "}\n";

  writer->Close();
  return code;
}

// Returns Parent1::Parent2::Self. Namespaces are not included.
string GetQualifiedName(const AidlDefinedType& type, ClassNames class_names) {
  string q_name = ClassName(type, class_names);
  for (auto parent = type.GetParentType(); parent; parent = parent->GetParentType()) {
    q_name = ClassName(*parent, ClassNames::MAYBE_INTERFACE) + "::" + q_name;
  }
  return q_name;
}

// Generates enum's class declaration. This should be called in a proper scope. For example, in its
// namespace or parent type.
void GenerateEnumClassDecl(CodeWriter& out, const AidlEnumDeclaration& enum_decl,
                           const std::string& backing_type, ConstantValueDecorator decorator) {
  out << "enum class";
  GenerateDeprecated(out, enum_decl);
  out << " " << enum_decl.GetName() << " : " << backing_type << " {\n";
  out.Indent();
  for (const auto& enumerator : enum_decl.GetEnumerators()) {
    out << enumerator->GetName();
    GenerateDeprecated(out, *enumerator);
    out << " = " << enumerator->ValueString(enum_decl.GetBackingType(), decorator) << ",\n";
  }
  out.Dedent();
  out << "};\n";
}

static bool IsEnumDeprecated(const AidlEnumDeclaration& enum_decl) {
  if (enum_decl.IsDeprecated()) {
    return true;
  }
  for (const auto& e : enum_decl.GetEnumerators()) {
    if (e->IsDeprecated()) {
      return true;
    }
  }
  return false;
}

// enum_values template value is defined in its own namespace (android::internal or ndk::internal),
// so the enum_decl type should be fully qualified.
std::string GenerateEnumValues(const AidlEnumDeclaration& enum_decl,
                               const std::vector<std::string>& enclosing_namespaces_of_enum_decl) {
  const auto fq_name =
      Join(Append(enclosing_namespaces_of_enum_decl, enum_decl.GetSplitPackage()), "::") +
      "::" + GetQualifiedName(enum_decl);
  const auto size = enum_decl.GetEnumerators().size();
  std::ostringstream code;
  code << "#pragma clang diagnostic push\n";
  code << "#pragma clang diagnostic ignored \"-Wc++17-extensions\"\n";
  if (IsEnumDeprecated(enum_decl)) {
    code << "#pragma clang diagnostic ignored \"-Wdeprecated-declarations\"\n";
  }
  code << "template <>\n";
  code << "constexpr inline std::array<" << fq_name << ", " << size << ">";
  code << " enum_values<" << fq_name << "> = {\n";
  for (const auto& enumerator : enum_decl.GetEnumerators()) {
    code << "  " << fq_name << "::" << enumerator->GetName() << ",\n";
  }
  code << "};\n";
  code << "#pragma clang diagnostic pop\n";
  return code.str();
}

// toString(enum_type) is defined in the same namespace of the type.
// So, if enum_decl is nested in parent type(s), it should be qualified with parent type(s).
std::string GenerateEnumToString(const AidlEnumDeclaration& enum_decl,
                                 const std::string& backing_type) {
  const auto q_name = GetQualifiedName(enum_decl);
  std::ostringstream code;
  bool is_enum_deprecated = IsEnumDeprecated(enum_decl);
  if (is_enum_deprecated) {
    code << "#pragma clang diagnostic push\n";
    code << "#pragma clang diagnostic ignored \"-Wdeprecated-declarations\"\n";
  }
  code << "[[nodiscard]] static inline std::string toString(" + q_name + " val) {\n";
  code << "  switch(val) {\n";
  std::set<std::string> unique_cases;
  for (const auto& enumerator : enum_decl.GetEnumerators()) {
    std::string c = enumerator->ValueString(enum_decl.GetBackingType(), AidlConstantValueDecorator);
    // Only add a case if its value has not yet been used in the switch
    // statement. C++ does not allow multiple cases with the same value, but
    // enums does allow this. In this scenario, the first declared
    // enumerator with the given value is printed.
    if (unique_cases.count(c) == 0) {
      unique_cases.insert(c);
      code << "  case " << q_name << "::" << enumerator->GetName() << ":\n";
      code << "    return \"" << enumerator->GetName() << "\";\n";
    }
  }
  code << "  default:\n";
  code << "    return std::to_string(static_cast<" << backing_type << ">(val));\n";
  code << "  }\n";
  code << "}\n";
  if (is_enum_deprecated) {
    code << "#pragma clang diagnostic pop\n";
  }
  return code.str();
}

std::string TemplateDecl(const AidlParcelable& defined_type) {
  std::string decl = "";
  if (defined_type.IsGeneric()) {
    std::vector<std::string> template_params;
    for (const auto& parameter : defined_type.GetTypeParameters()) {
      template_params.push_back(parameter);
    }
    decl = base::StringPrintf("template <typename %s>\n",
                              base::Join(template_params, ", typename ").c_str());
  }
  return decl;
}

void GenerateParcelableComparisonOperators(CodeWriter& out, const AidlParcelable& parcelable) {
  std::set<string> operators{"<", ">", "==", ">=", "<=", "!="};

  if (parcelable.AsUnionDeclaration() && parcelable.IsFixedSize()) {
    auto name = parcelable.GetName();
    auto max_tag = parcelable.GetFields().back()->GetName();
    auto min_tag = parcelable.GetFields().front()->GetName();
    constexpr auto tmpl = R"--(static int _cmp(const {name}& _lhs, const {name}& _rhs) {{
  return _cmp_value(_lhs.getTag(), _rhs.getTag()) || _cmp_value_at<{max_tag}>(_lhs, _rhs);
}}
template <Tag _Tag>
static int _cmp_value_at(const {name}& _lhs, const {name}& _rhs) {{
  if constexpr (_Tag == {min_tag}) {{
    return _cmp_value(_lhs.get<_Tag>(), _rhs.get<_Tag>());
  }} else {{
    return (_lhs.getTag() == _Tag)
      ? _cmp_value(_lhs.get<_Tag>(), _rhs.get<_Tag>())
      : _cmp_value_at<static_cast<Tag>(static_cast<size_t>(_Tag)-1)>(_lhs, _rhs);
  }}
}}
template <typename _Type>
static int _cmp_value(const _Type& _lhs, const _Type& _rhs) {{
  return (_lhs == _rhs) ? 0 : (_lhs < _rhs) ? -1 : 1;
}}
)--";
    out << fmt::format(tmpl, fmt::arg("name", name), fmt::arg("min_tag", min_tag),
                       fmt::arg("max_tag", max_tag));
    for (const auto& op : operators) {
      out << "inline bool operator" << op << "(const " << name << "&_rhs) const {\n";
      out << "  return _cmp(*this, _rhs) " << op << " 0;\n";
      out << "}\n";
    }
    return;
  }

  bool is_empty = false;

  auto comparable = [&](const string& prefix) {
    vector<string> fields;
    if (auto p = parcelable.AsStructuredParcelable(); p != nullptr) {
      is_empty = p->GetFields().empty();
      for (const auto& f : p->GetFields()) {
        fields.push_back(prefix + f->GetName());
      }
      return "std::tie(" + Join(fields, ", ") + ")";
    } else if (auto p = parcelable.AsUnionDeclaration(); p != nullptr) {
      return prefix + "_value";
    } else {
      AIDL_FATAL(parcelable) << "Unknown paracelable type";
    }
  };

  string lhs = comparable("");
  string rhs = comparable("_rhs.");

  // Delegate < and == to the fields.
  for (const auto& op : {"==", "<"}) {
    out << "inline bool operator" << op << "(const " << parcelable.GetName() << "&"
        << (is_empty ? "" : " _rhs") << ") const {\n"
        << "  return " << lhs << " " << op << " " << rhs << ";\n"
        << "}\n";
  }
  // Delegate other ops to < and == for *this, which lets a custom parcelable
  // to be used with structured parcelables without implementation all operations.
  out << fmt::format(R"--(inline bool operator!=(const {name}& _rhs) const {{
  return !(*this == _rhs);
}}
inline bool operator>(const {name}& _rhs) const {{
  return _rhs < *this;
}}
inline bool operator>=(const {name}& _rhs) const {{
  return !(*this < _rhs);
}}
inline bool operator<=(const {name}& _rhs) const {{
  return !(_rhs < *this);
}}
)--",
                     fmt::arg("name", parcelable.GetName()));
  out << "\n";
}

// Output may look like:
// inline std::string toString() const {
//   std::ostringstream _aidl_os;
//   _aidl_os << "MyData{";
//   _aidl_os << "field1: " << field1;
//   _aidl_os << ", field2: " << v.field2;
//   ...
//   _aidl_os << "}";
//   return _aidl_os.str();
// }
void GenerateToString(CodeWriter& out, const AidlStructuredParcelable& parcelable) {
  out << "inline std::string toString() const {\n";
  out.Indent();
  out << "std::ostringstream _aidl_os;\n";
  out << "_aidl_os << \"" << parcelable.GetName() << "{\";\n";
  bool first = true;
  for (const auto& f : parcelable.GetFields()) {
    if (first) {
      out << "_aidl_os << \"";
      first = false;
    } else {
      out << "_aidl_os << \", ";
    }
    out << f->GetName() << ": \" << ::android::internal::ToString(" << f->GetName() << ");\n";
  }
  out << "_aidl_os << \"}\";\n";
  out << "return _aidl_os.str();\n";
  out.Dedent();
  out << "}\n";
}

// Output may look like:
// inline std::string toString() const {
//   std::ostringstream os;
//   os << "MyData{";
//   switch (v.getTag()) {
//   case MyData::field: os << "field: " << v.get<MyData::field>(); break;
//   ...
//   }
//   os << "}";
//   return os.str();
// }
void GenerateToString(CodeWriter& out, const AidlUnionDecl& parcelable) {
  out << "inline std::string toString() const {\n";
  out.Indent();
  out << "std::ostringstream os;\n";
  out << "os << \"" + parcelable.GetName() + "{\";\n";
  out << "switch (getTag()) {\n";
  for (const auto& f : parcelable.GetFields()) {
    const string tag = f->GetName();
    out << "case " << tag << ": os << \"" << tag << ": \" << "
        << "::android::internal::ToString(get<" + tag + ">()); break;\n";
  }
  out << "}\n";
  out << "os << \"}\";\n";
  out << "return os.str();\n";
  out.Dedent();
  out << "}\n";
}

std::string GetDeprecatedAttribute(const AidlCommentable& type) {
  if (auto deprecated = FindDeprecated(type.GetComments()); deprecated.has_value()) {
    if (deprecated->note.empty()) {
      return "__attribute__((deprecated))";
    }
    return "__attribute__((deprecated(" + QuotedEscape(deprecated->note) + ")))";
  }
  return "";
}

size_t AlignmentOf(const AidlTypeSpecifier& type, const AidlTypenames& typenames) {
  static map<string, size_t> alignment = {
      {"boolean", 1}, {"byte", 1}, {"char", 2}, {"double", 8},
      {"float", 4},   {"int", 4},  {"long", 8},
  };

  string name = type.GetName();
  if (auto enum_decl = typenames.GetEnumDeclaration(type); enum_decl) {
    name = enum_decl->GetBackingType().GetName();
  }
  // default to 0 for parcelable types
  return alignment[name];
}

std::set<std::string> UnionWriter::GetHeaders(const AidlUnionDecl& decl) {
  std::set<std::string> union_headers = {
      "cassert",      // __assert for logging
      "type_traits",  // std::is_same_v
      "utility",      // std::mode/forward for value
      "variant",      // union's impl
  };
  if (decl.IsFixedSize()) {
    union_headers.insert("tuple");  // fixed-sized union's typelist
  }
  return union_headers;
}

// fixed-sized union class looks like:
// class Union {
// public:
//   enum Tag : uint8_t {
//     field1 = 0,
//     field2,
//   };
//  ... methods ...
// private:
//   Tag _tag;
//   union {
//     type1 field1;
//     type2 field2;
//   } _value;
// };

void UnionWriter::PrivateFields(CodeWriter& out) const {
  if (decl.IsFixedSize()) {
    AIDL_FATAL_IF(decl.GetFields().empty(), decl) << "Union '" << decl.GetName() << "' is empty.";
    const auto& first_field = decl.GetFields()[0];
    const auto& default_name = first_field->GetName();
    const auto& default_value = name_of(first_field->GetType(), typenames) + "(" +
                                first_field->ValueString(decorator) + ")";

    out << "Tag _tag = " << default_name << ";\n";
    out << "union _value_t {\n";
    out.Indent();
    out << "_value_t() {}\n";
    out << "~_value_t() {}\n";
    for (const auto& f : decl.GetFields()) {
      const auto& fn = f->GetName();
      out << name_of(f->GetType(), typenames) << " " << fn;
      if (decl.IsFixedSize()) {
        int alignment = AlignmentOf(f->GetType(), typenames);
        if (alignment > 0) {
          out << " __attribute__((aligned (" << std::to_string(alignment) << ")))";
        }
      }
      if (fn == default_name) {
        out << " = " << default_value;
      }
      out << ";\n";
    }
    out.Dedent();
    out << "} _value;\n";
  } else {
    vector<string> field_types;
    for (const auto& f : decl.GetFields()) {
      field_types.push_back(name_of(f->GetType(), typenames));
    }
    out << "std::variant<" + Join(field_types, ", ") + "> _value;\n";
  }
}

void UnionWriter::PublicFields(CodeWriter& out) const {
  out << "// Expose tag symbols for legacy code\n";
  for (const auto& f : decl.GetFields()) {
    out << "static const inline Tag";
    GenerateDeprecated(out, *f);
    out << " " << f->GetName() << " = Tag::" << f->GetName() << ";\n";
  }

  const auto& name = decl.GetName();

  if (decl.IsFixedSize()) {
    vector<string> field_types;
    for (const auto& f : decl.GetFields()) {
      field_types.push_back(name_of(f->GetType(), typenames));
    }
    auto typelist = Join(field_types, ", ");
    constexpr auto tmpl = R"--(
template <Tag _Tag>
using _at = typename std::tuple_element<static_cast<size_t>(_Tag), std::tuple<{typelist}>>::type;
template <Tag _Tag, typename _Type>
static {name} make(_Type&& _arg) {{
  {name} _inst;
  _inst.set<_Tag>(std::forward<_Type>(_arg));
  return _inst;
}}
constexpr Tag getTag() const {{
  return _tag;
}}
template <Tag _Tag>
const _at<_Tag>& get() const {{
  if (_Tag != _tag) {{ __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "bad access: a wrong tag"); }}
  return *(_at<_Tag>*)(&_value);
}}
template <Tag _Tag>
_at<_Tag>& get() {{
  if (_Tag != _tag) {{ __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "bad access: a wrong tag"); }}
  return *(_at<_Tag>*)(&_value);
}}
template <Tag _Tag, typename _Type>
void set(_Type&& _arg) {{
  _tag = _Tag;
  get<_Tag>() = std::forward<_Type>(_arg);
}}
)--";
    out << fmt::format(tmpl, fmt::arg("name", name), fmt::arg("typelist", typelist));
  } else {
    AIDL_FATAL_IF(decl.GetFields().empty(), decl) << "Union '" << name << "' is empty.";
    const auto& first_field = decl.GetFields()[0];
    const auto& default_name = first_field->GetName();
    const auto& default_value = name_of(first_field->GetType(), typenames) + "(" +
                                first_field->ValueString(decorator) + ")";

    constexpr auto tmpl = R"--(
template<typename _Tp>
static constexpr bool _not_self = !std::is_same_v<std::remove_cv_t<std::remove_reference_t<_Tp>>, {name}>;

{name}() : _value(std::in_place_index<static_cast<size_t>({default_name})>, {default_value}) {{ }}

template <typename _Tp, typename = std::enable_if_t<_not_self<_Tp>>>
// NOLINTNEXTLINE(google-explicit-constructor)
constexpr {name}(_Tp&& _arg)
    : _value(std::forward<_Tp>(_arg)) {{}}

template <size_t _Np, typename... _Tp>
constexpr explicit {name}(std::in_place_index_t<_Np>, _Tp&&... _args)
    : _value(std::in_place_index<_Np>, std::forward<_Tp>(_args)...) {{}}

template <Tag _tag, typename... _Tp>
static {name} make(_Tp&&... _args) {{
  return {name}(std::in_place_index<static_cast<size_t>(_tag)>, std::forward<_Tp>(_args)...);
}}

template <Tag _tag, typename _Tp, typename... _Up>
static {name} make(std::initializer_list<_Tp> _il, _Up&&... _args) {{
  return {name}(std::in_place_index<static_cast<size_t>(_tag)>, std::move(_il), std::forward<_Up>(_args)...);
}}

Tag getTag() const {{
  return static_cast<Tag>(_value.index());
}}

template <Tag _tag>
const auto& get() const {{
  if (getTag() != _tag) {{ __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "bad access: a wrong tag"); }}
  return std::get<static_cast<size_t>(_tag)>(_value);
}}

template <Tag _tag>
auto& get() {{
  if (getTag() != _tag) {{ __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "bad access: a wrong tag"); }}
  return std::get<static_cast<size_t>(_tag)>(_value);
}}

template <Tag _tag, typename... _Tp>
void set(_Tp&&... _args) {{
  _value.emplace<static_cast<size_t>(_tag)>(std::forward<_Tp>(_args)...);
}}

)--";
    out << fmt::format(tmpl, fmt::arg("name", name), fmt::arg("default_name", default_name),
                       fmt::arg("default_value", default_value));
  }
}

void UnionWriter::ReadFromParcel(CodeWriter& out, const ParcelWriterContext& ctx) const {
  // Even though @FixedSize union may use a smaller type than int32_t, we need to read/write it
  // as if it is int32_t for compatibility with other bckends.
  auto tag_type = typenames.MakeResolvedType(AIDL_LOCATION_HERE, "int", /* is_array= */ false);

  const string tag = "_aidl_tag";
  const string value = "_aidl_value";
  const string status = "_aidl_ret_status";

  auto read_var = [&](const string& var, const AidlTypeSpecifier& type) {
    out << fmt::format("{} {};\n", name_of(type, typenames), var);
    out << fmt::format("if (({} = ", status);
    ctx.read_func(out, var, type);
    out << fmt::format(") != {}) return {};\n", ctx.status_ok, status);
  };

  out << fmt::format("{} {};\n", ctx.status_type, status);
  read_var(tag, *tag_type);
  out << fmt::format("switch (static_cast<Tag>({})) {{\n", tag);
  for (const auto& variable : decl.GetFields()) {
    out << fmt::format("case {}: {{\n", variable->GetName());
    out.Indent();
    if (variable->IsNew()) {
      out << fmt::format("if (true) return {};\n", ctx.status_bad);
    }
    const auto& type = variable->GetType();
    read_var(value, type);
    out << fmt::format("if constexpr (std::is_trivially_copyable_v<{}>) {{\n",
                       name_of(type, typenames));
    out.Indent();
    out << fmt::format("set<{}>({});\n", variable->GetName(), value);
    out.Dedent();
    out << "} else {\n";
    out.Indent();
    // Even when the `if constexpr` is false, the compiler runs the tidy check for the
    // next line, which doesn't make sense. Silence the check for the unreachable code.
    out << "// NOLINTNEXTLINE(performance-move-const-arg)\n";
    out << fmt::format("set<{}>(std::move({}));\n", variable->GetName(), value);
    out.Dedent();
    out << "}\n";
    out << fmt::format("return {}; }}\n", ctx.status_ok);
    out.Dedent();
  }
  out << "}\n";
  out << fmt::format("return {};\n", ctx.status_bad);
}

void UnionWriter::WriteToParcel(CodeWriter& out, const ParcelWriterContext& ctx) const {
  // Even though @FixedSize union may use a smaller type than int32_t, we need to read/write it
  // as if it is int32_t for compatibility with other bckends.
  auto tag_type = typenames.MakeResolvedType(AIDL_LOCATION_HERE, "int", /* is_array= */ false);

  const string tag = "_aidl_tag";
  const string value = "_aidl_value";
  const string status = "_aidl_ret_status";

  out << fmt::format("{} {} = ", ctx.status_type, status);
  ctx.write_func(out, "static_cast<int32_t>(getTag())", *tag_type);
  out << ";\n";
  out << fmt::format("if ({} != {}) return {};\n", status, ctx.status_ok, status);
  out << "switch (getTag()) {\n";
  for (const auto& variable : decl.GetFields()) {
    if (variable->IsDeprecated()) {
      out << "#pragma clang diagnostic push\n";
      out << "#pragma clang diagnostic ignored \"-Wdeprecated-declarations\"\n";
    }
    if (variable->IsNew()) {
      out << fmt::format("case {}: return true ? {} : ", variable->GetName(), ctx.status_bad);
    } else {
      out << fmt::format("case {}: return ", variable->GetName());
    }
    ctx.write_func(out, "get<" + variable->GetName() + ">()", variable->GetType());
    out << ";\n";
    if (variable->IsDeprecated()) {
      out << "#pragma clang diagnostic pop\n";
    }
  }
  out << "}\n";
  out << "__assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, \"can't reach here\");\n";
}

std::string CppConstantValueDecorator(
    const AidlTypeSpecifier& type,
    const std::variant<std::string, std::vector<std::string>>& raw_value, bool is_ndk) {
  if (type.IsArray()) {
    auto values = std::get<std::vector<std::string>>(raw_value);
    // Hexadecimal literals for byte arrays should be casted to uint8_t
    if (type.GetName() == "byte" &&
        std::any_of(values.begin(), values.end(),
                    [](const auto& value) { return !value.empty() && value[0] == '-'; })) {
      for (auto& value : values) {
        // cast only if necessary
        if (value[0] == '-') {
          value = "uint8_t(" + value + ")";
        }
      }
    }
    std::string value = "{" + Join(values, ", ") + "}";

    if (type.IsFixedSizeArray()) {
      // For arrays, use double braces because arrays can be nested.
      //  e.g.) array<array<int, 2>, 3> ints = {{ {{1,2}}, {{3,4}}, {{5,6}} }};
      // Vectors might need double braces, but since we don't have nested vectors (yet)
      // single brace would work even for optional vectors.
      value = "{" + value + "}";
    }

    if (!type.IsMutated() && type.IsNullable()) {
      // For outermost std::optional<>, we need an additional brace pair to initialize its value.
      value = "{" + value + "}";
    }
    return value;
  }

  const std::string& value = std::get<std::string>(raw_value);
  if (AidlTypenames::IsBuiltinTypename(type.GetName())) {
    if (type.GetName() == "boolean") {
      return value;
    } else if (type.GetName() == "byte") {
      return value;
    } else if (type.GetName() == "char") {
      // TODO: consider 'L'-prefix for wide char literal
      return value;
    } else if (type.GetName() == "double") {
      return value;
    } else if (type.GetName() == "float") {
      return value;  // value has 'f' suffix
    } else if (type.GetName() == "int") {
      return value;
    } else if (type.GetName() == "long") {
      return value + "L";
    } else if (type.GetName() == "String") {
      if (is_ndk || type.IsUtf8InCpp()) {
        return value;
      } else {
        return "::android::String16(" + value + ")";
      }
    }
    AIDL_FATAL(type) << "Unknown built-in type: " << type.GetName();
  }

  auto defined_type = type.GetDefinedType();
  AIDL_FATAL_IF(!defined_type, type) << "Invalid type for \"" << value << "\"";
  auto enum_type = defined_type->AsEnumDeclaration();
  AIDL_FATAL_IF(!enum_type, type) << "Invalid type for \"" << value << "\"";

  auto cpp_type_name = "::" + Join(Split(enum_type->GetCanonicalName(), "."), "::");
  if (is_ndk) {
    cpp_type_name = "::aidl" + cpp_type_name;
  }
  return cpp_type_name + "::" + value.substr(value.find_last_of('.') + 1);
}

// Collect all forward declarations for the type's interface header.
// Nested types are visited as well via VisitTopDown.
void GenerateForwardDecls(CodeWriter& out, const AidlDefinedType& root_type, bool is_ndk) {
  struct Visitor : AidlVisitor {
    using PackagePath = std::vector<std::string>;
    struct ClassDeclInfo {
      std::string template_decl;
    };
    std::map<PackagePath, std::map<std::string, ClassDeclInfo>> classes;
    // Collect class names for each interface or parcelable type
    void Visit(const AidlTypeSpecifier& type) override {
      const auto defined_type = type.GetDefinedType();
      if (defined_type && !defined_type->GetParentType()) {
        // Forward declarations are not supported for nested types
        auto package = defined_type->GetSplitPackage();
        if (defined_type->AsInterface() != nullptr) {
          auto name = ClassName(*defined_type, ClassNames::INTERFACE);
          classes[package][std::move(name)] = ClassDeclInfo();
        } else if (auto* p = defined_type->AsStructuredParcelable(); p != nullptr) {
          auto name = defined_type->GetName();
          ClassDeclInfo info;
          info.template_decl = TemplateDecl(*p);
          classes[package][std::move(name)] = std::move(info);
        }
      }
    }
  } v;
  VisitTopDown(v, root_type);

  if (v.classes.empty()) {
    return;
  }

  for (const auto& it : v.classes) {
    auto package = it.first;
    auto& classes = it.second;

    if (is_ndk) {
      package.insert(package.begin(), "aidl");
    }

    std::string namespace_name = Join(package, "::");
    if (!namespace_name.empty()) {
      out << "namespace " << namespace_name << " {\n";
    }
    for (const auto& [name, info] : classes) {
      out << info.template_decl << "class " << name << ";\n";
    }
    if (!namespace_name.empty()) {
      out << "}  // namespace " << namespace_name << "\n";
    }
  }
}
}  // namespace cpp
}  // namespace aidl
}  // namespace android
