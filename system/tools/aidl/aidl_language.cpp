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

#include "aidl_language.h"
#include "aidl_typenames.h"
#include "parser.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <algorithm>
#include <iostream>
#include <set>
#include <sstream>
#include <string>
#include <utility>

#include <android-base/parsedouble.h>
#include <android-base/parseint.h>
#include <android-base/result.h>
#include <android-base/strings.h>

#include "aidl.h"
#include "aidl_language_y.h"
#include "comments.h"
#include "logging.h"
#include "permission.h"

#ifdef _WIN32
int isatty(int  fd)
{
    return (fd == 0);
}
#endif

using android::aidl::IoDelegate;
using android::base::Error;
using android::base::Join;
using android::base::Result;
using android::base::Split;
using std::cerr;
using std::pair;
using std::set;
using std::string;
using std::unique_ptr;
using std::vector;

namespace {
bool IsJavaKeyword(const char* str) {
  static const std::vector<std::string> kJavaKeywords{
      "abstract", "assert", "boolean",    "break",     "byte",       "case",      "catch",
      "char",     "class",  "const",      "continue",  "default",    "do",        "double",
      "else",     "enum",   "extends",    "final",     "finally",    "float",     "for",
      "goto",     "if",     "implements", "import",    "instanceof", "int",       "interface",
      "long",     "native", "new",        "package",   "private",    "protected", "public",
      "return",   "short",  "static",     "strictfp",  "super",      "switch",    "synchronized",
      "this",     "throw",  "throws",     "transient", "try",        "void",      "volatile",
      "while",    "true",   "false",      "null",
  };
  return std::find(kJavaKeywords.begin(), kJavaKeywords.end(), str) != kJavaKeywords.end();
}
}  // namespace

AidlNode::~AidlNode() {
  if (!visited_) {
    unvisited_locations_.push_back(location_);
  }
}

void AidlNode::ClearUnvisitedNodes() {
  unvisited_locations_.clear();
}

const std::vector<AidlLocation>& AidlNode::GetLocationsOfUnvisitedNodes() {
  return unvisited_locations_;
}

void AidlNode::MarkVisited() const {
  visited_ = true;
}

AidlNode::AidlNode(const AidlLocation& location, const Comments& comments)
    : location_(location), comments_(comments) {}

std::string AidlNode::PrintLine() const {
  std::stringstream ss;
  ss << location_.file_ << ":" << location_.begin_.line;
  return ss.str();
}

std::string AidlNode::PrintLocation() const {
  std::stringstream ss;
  ss << location_.file_ << ":" << location_.begin_.line << ":" << location_.begin_.column << ":"
     << location_.end_.line << ":" << location_.end_.column;
  return ss.str();
}

std::vector<AidlLocation> AidlNode::unvisited_locations_;

static const AidlTypeSpecifier kStringType{AIDL_LOCATION_HERE, "String", /*array=*/std::nullopt,
                                           nullptr, Comments{}};
static const AidlTypeSpecifier kStringArrayType{AIDL_LOCATION_HERE, "String", DynamicArray{},
                                                nullptr, Comments{}};
static const AidlTypeSpecifier kIntType{AIDL_LOCATION_HERE, "int", /*array=*/std::nullopt, nullptr,
                                        Comments{}};
static const AidlTypeSpecifier kLongType{AIDL_LOCATION_HERE, "long", /*array=*/std::nullopt,
                                         nullptr, Comments{}};
static const AidlTypeSpecifier kBooleanType{AIDL_LOCATION_HERE, "boolean", /*array=*/std::nullopt,
                                            nullptr, Comments{}};

const std::vector<AidlAnnotation::Schema>& AidlAnnotation::AllSchemas() {
  static const std::vector<Schema> kSchemas{
      {AidlAnnotation::Type::NULLABLE,
       "nullable",
       CONTEXT_TYPE_SPECIFIER,
       {{"heap", kBooleanType}}},
      {AidlAnnotation::Type::UTF8_IN_CPP, "utf8InCpp", CONTEXT_TYPE_SPECIFIER, {}},
      {AidlAnnotation::Type::SENSITIVE_DATA, "SensitiveData", CONTEXT_TYPE_INTERFACE, {}},
      {AidlAnnotation::Type::VINTF_STABILITY, "VintfStability", CONTEXT_TYPE, {}},
      {AidlAnnotation::Type::UNSUPPORTED_APP_USAGE,
       "UnsupportedAppUsage",
       CONTEXT_TYPE | CONTEXT_MEMBER,
       {{"expectedSignature", kStringType},
        {"implicitMember", kStringType},
        {"maxTargetSdk", kIntType},
        {"publicAlternatives", kStringType},
        {"trackingBug", kLongType}}},
      {AidlAnnotation::Type::JAVA_STABLE_PARCELABLE,
       "JavaOnlyStableParcelable",
       CONTEXT_TYPE_UNSTRUCTURED_PARCELABLE,
       {}},
      {AidlAnnotation::Type::NDK_STABLE_PARCELABLE,
       "NdkOnlyStableParcelable",
       CONTEXT_TYPE_UNSTRUCTURED_PARCELABLE,
       {}},
      {AidlAnnotation::Type::RUST_STABLE_PARCELABLE,
       "RustOnlyStableParcelable",
       CONTEXT_TYPE_UNSTRUCTURED_PARCELABLE,
       {}},
      {AidlAnnotation::Type::BACKING,
       "Backing",
       CONTEXT_TYPE_ENUM,
       {{"type", kStringType, /* required= */ true}}},
      {AidlAnnotation::Type::JAVA_PASSTHROUGH,
       "JavaPassthrough",
       CONTEXT_ALL,
       {{"annotation", kStringType, /* required= */ true}},
       /* repeatable= */ true},
      {AidlAnnotation::Type::JAVA_DERIVE,
       "JavaDerive",
       CONTEXT_TYPE_STRUCTURED_PARCELABLE | CONTEXT_TYPE_UNION | CONTEXT_TYPE_ENUM,
       {{"toString", kBooleanType}, {"equals", kBooleanType}}},
      {AidlAnnotation::Type::JAVA_DEFAULT, "JavaDefault", CONTEXT_TYPE_INTERFACE, {}},
      {AidlAnnotation::Type::JAVA_DELEGATOR, "JavaDelegator", CONTEXT_TYPE_INTERFACE, {}},
      {AidlAnnotation::Type::JAVA_ONLY_IMMUTABLE,
       "JavaOnlyImmutable",
       CONTEXT_TYPE_STRUCTURED_PARCELABLE | CONTEXT_TYPE_UNION |
           CONTEXT_TYPE_UNSTRUCTURED_PARCELABLE,
       {}},
      {AidlAnnotation::Type::JAVA_SUPPRESS_LINT,
       "JavaSuppressLint",
       CONTEXT_ALL,
       {{"value", kStringArrayType, /* required= */ true}}},
      {AidlAnnotation::Type::FIXED_SIZE,
       "FixedSize",
       CONTEXT_TYPE_STRUCTURED_PARCELABLE | CONTEXT_TYPE_UNION,
       {}},
      {AidlAnnotation::Type::DESCRIPTOR,
       "Descriptor",
       CONTEXT_TYPE_INTERFACE,
       {{"value", kStringType, /* required= */ true}}},
      {AidlAnnotation::Type::RUST_DERIVE,
       "RustDerive",
       CONTEXT_TYPE_STRUCTURED_PARCELABLE | CONTEXT_TYPE_UNION,
       {{"Copy", kBooleanType},
        {"Clone", kBooleanType},
        {"PartialOrd", kBooleanType},
        {"Ord", kBooleanType},
        {"PartialEq", kBooleanType},
        {"Eq", kBooleanType},
        {"Hash", kBooleanType}}},
      {AidlAnnotation::Type::SUPPRESS_WARNINGS,
       "SuppressWarnings",
       CONTEXT_TYPE | CONTEXT_MEMBER,
       {{"value", kStringArrayType, /* required= */ true}}},
      {AidlAnnotation::Type::PERMISSION_ENFORCE,
       "EnforcePermission",
       CONTEXT_TYPE_INTERFACE | CONTEXT_METHOD,
       {{"value", kStringType}, {"anyOf", kStringArrayType}, {"allOf", kStringArrayType}}},
      {AidlAnnotation::Type::PERMISSION_MANUAL,
       "PermissionManuallyEnforced",
       CONTEXT_TYPE_INTERFACE | CONTEXT_METHOD,
       {}},
      {AidlAnnotation::Type::PERMISSION_NONE,
       "RequiresNoPermission",
       CONTEXT_TYPE_INTERFACE | CONTEXT_METHOD,
       {}},
      {AidlAnnotation::Type::PROPAGATE_ALLOW_BLOCKING,
       "PropagateAllowBlocking",
       CONTEXT_METHOD,
       {}},
  };
  return kSchemas;
}

std::string AidlAnnotation::TypeToString(Type type) {
  for (const Schema& schema : AllSchemas()) {
    if (type == schema.type) return schema.name;
  }
  AIDL_FATAL(AIDL_LOCATION_HERE) << "Unrecognized type: " << static_cast<size_t>(type);
  __builtin_unreachable();
}

std::unique_ptr<AidlAnnotation> AidlAnnotation::Parse(
    const AidlLocation& location, const string& name,
    std::map<std::string, std::shared_ptr<AidlConstantValue>> parameter_list,
    const Comments& comments) {
  const Schema* schema = nullptr;
  for (const Schema& a_schema : AllSchemas()) {
    if (a_schema.name == name) {
      schema = &a_schema;
    }
  }

  if (schema == nullptr) {
    std::ostringstream stream;
    stream << "'" << name << "' is not a recognized annotation. ";
    stream << "It must be one of:";
    for (const Schema& s : AllSchemas()) {
      stream << " " << s.name;
    }
    stream << ".";
    AIDL_ERROR(location) << stream.str();
    return {};
  }

  return std::unique_ptr<AidlAnnotation>(
      new AidlAnnotation(location, *schema, std::move(parameter_list), comments));
}

AidlAnnotation::AidlAnnotation(const AidlLocation& location, const Schema& schema,
                               std::map<std::string, std::shared_ptr<AidlConstantValue>> parameters,
                               const Comments& comments)
    : AidlNode(location, comments), schema_(schema), parameters_(std::move(parameters)) {}

struct ConstReferenceFinder : AidlVisitor {
  const AidlConstantReference* found = nullptr;
  void Visit(const AidlConstantReference& ref) override {
    if (!found) found = &ref;
  }
  static const AidlConstantReference* Find(const AidlConstantValue& c) {
    ConstReferenceFinder finder;
    VisitTopDown(finder, c);
    return finder.found;
  }
};

// Checks if annotation complies with the schema
// - every parameter is known and has well-typed value.
// - every required parameter is present.
bool AidlAnnotation::CheckValid() const {
  for (const auto& name_and_param : parameters_) {
    const std::string& param_name = name_and_param.first;
    const std::shared_ptr<AidlConstantValue>& param = name_and_param.second;

    const auto param_type = schema_.ParamType(param_name);
    if (!param_type) {
      std::ostringstream stream;
      stream << "Parameter " << param_name << " not supported ";
      stream << "for annotation " << GetName() << ". ";
      stream << "It must be one of:";
      for (const auto& param : schema_.parameters) {
        stream << " " << param.name;
      }
      AIDL_ERROR(this) << stream.str();
      return false;
    }

    const auto& found = ConstReferenceFinder::Find(*param);
    if (found) {
      AIDL_ERROR(found) << "Value must be a constant expression but contains reference to "
                        << found->GetFieldName() << ".";
      return false;
    }

    if (!param->CheckValid()) {
      AIDL_ERROR(this) << "Invalid value for parameter " << param_name << " on annotation "
                       << GetName() << ".";
      return false;
    }

    const std::string param_value =
        param->ValueString(param_type->type, AidlConstantValueDecorator);
    // Assume error on empty string.
    if (param_value == "") {
      AIDL_ERROR(this) << "Invalid value for parameter " << param_name << " on annotation "
                       << GetName() << ".";
      return false;
    }
  }
  bool success = true;
  for (const auto& param : schema_.parameters) {
    if (param.required && parameters_.count(param.name) == 0) {
      AIDL_ERROR(this) << "Missing '" << param.name << "' on @" << GetName() << ".";
      success = false;
    }
  }
  if (!success) {
    return false;
  }
  // For @Enforce annotations, validates the expression.
  if (schema_.type == AidlAnnotation::Type::PERMISSION_ENFORCE) {
    auto expr = EnforceExpression();
    if (!expr.ok()) {
      AIDL_ERROR(this) << "Unable to parse @EnforcePermission annotation: " << expr.error();
      return false;
    }
  }
  return true;
}

Result<unique_ptr<android::aidl::perm::Expression>> AidlAnnotation::EnforceExpression() const {
  auto single = ParamValue<std::string>("value");
  auto anyOf = ParamValue<std::vector<std::string>>("anyOf");
  auto allOf = ParamValue<std::vector<std::string>>("allOf");
  if (single.has_value()) {
    return std::make_unique<android::aidl::perm::Expression>(single.value());
  } else if (anyOf.has_value()) {
    auto v = android::aidl::perm::AnyOf{anyOf.value()};
    return std::make_unique<android::aidl::perm::Expression>(v);
  } else if (allOf.has_value()) {
    auto v = android::aidl::perm::AllOf{allOf.value()};
    return std::make_unique<android::aidl::perm::Expression>(v);
  }
  return Error() << "No parameter for @EnforcePermission";
}

// Checks if the annotation is applicable to the current context.
// For example, annotations like @VintfStability, @FixedSize is not applicable to AidlTypeSpecifier
// nodes.
bool AidlAnnotation::CheckContext(TargetContext context) const {
  if (schema_.target_context & static_cast<uint32_t>(context)) {
    return true;
  }
  const static map<TargetContext, string> context_name_map{
      {CONTEXT_TYPE_INTERFACE, "interface"},
      {CONTEXT_TYPE_ENUM, "enum"},
      {CONTEXT_TYPE_STRUCTURED_PARCELABLE, "parcelable definition"},
      {CONTEXT_TYPE_UNION, "union"},
      {CONTEXT_TYPE_UNSTRUCTURED_PARCELABLE, "parcelable declaration"},
      {CONTEXT_CONST, "constant"},
      {CONTEXT_FIELD, "field"},
      {CONTEXT_METHOD, "method"},
      {CONTEXT_TYPE_SPECIFIER, "type"},
  };
  vector<string> available;
  for (const auto& [context, name] : context_name_map) {
    if (schema_.target_context & context) {
      available.push_back(name);
    }
  }
  AIDL_ERROR(this) << "@" << GetName()
                   << " is not available. It can only annotate: " << Join(available, ", ") << ".";
  return false;
}

std::map<std::string, std::string> AidlAnnotation::AnnotationParams(
    const ConstantValueDecorator& decorator) const {
  std::map<std::string, std::string> raw_params;
  for (const auto& name_and_param : parameters_) {
    const std::string& param_name = name_and_param.first;
    const std::shared_ptr<AidlConstantValue>& param = name_and_param.second;
    const auto param_type = schema_.ParamType(param_name);
    AIDL_FATAL_IF(!param_type, this);
    raw_params.emplace(param_name, param->ValueString(param_type->type, decorator));
  }
  return raw_params;
}

std::string AidlAnnotation::ToString() const {
  if (parameters_.empty()) {
    return "@" + GetName();
  } else {
    vector<string> param_strings;
    for (const auto& [name, value] : AnnotationParams(AidlConstantValueDecorator)) {
      param_strings.emplace_back(name + "=" + value);
    }
    return "@" + GetName() + "(" + Join(param_strings, ", ") + ")";
  }
}

void AidlAnnotation::TraverseChildren(std::function<void(const AidlNode&)> traverse) const {
  for (const auto& [name, value] : parameters_) {
    (void)name;
    traverse(*value);
  }
}

static const AidlAnnotation* GetAnnotation(
    const vector<std::unique_ptr<AidlAnnotation>>& annotations, AidlAnnotation::Type type) {
  for (const auto& a : annotations) {
    if (a->GetType() == type) {
      AIDL_FATAL_IF(a->Repeatable(), a)
          << "Trying to get a single annotation when it is repeatable.";
      return a.get();
    }
  }
  return nullptr;
}

static const AidlAnnotation* GetScopedAnnotation(const AidlDefinedType& defined_type,
                                                 AidlAnnotation::Type type) {
  const AidlAnnotation* annotation = GetAnnotation(defined_type.GetAnnotations(), type);
  if (annotation) {
    return annotation;
  }
  const AidlDefinedType* enclosing_type = defined_type.GetParentType();
  if (enclosing_type) {
    return GetScopedAnnotation(*enclosing_type, type);
  }
  return nullptr;
}

AidlAnnotatable::AidlAnnotatable(const AidlLocation& location, const Comments& comments)
    : AidlCommentable(location, comments) {}

bool AidlAnnotatable::IsNullable() const {
  return GetAnnotation(annotations_, AidlAnnotation::Type::NULLABLE);
}

bool AidlAnnotatable::IsHeapNullable() const {
  auto annot = GetAnnotation(annotations_, AidlAnnotation::Type::NULLABLE);
  if (annot) {
    return annot->ParamValue<bool>("heap").value_or(false);
  }
  return false;
}

bool AidlAnnotatable::IsUtf8InCpp() const {
  return GetAnnotation(annotations_, AidlAnnotation::Type::UTF8_IN_CPP);
}

bool AidlAnnotatable::IsSensitiveData() const {
  return GetAnnotation(annotations_, AidlAnnotation::Type::SENSITIVE_DATA);
}

bool AidlAnnotatable::IsVintfStability() const {
  auto defined_type = AidlCast<AidlDefinedType>(*this);
  AIDL_FATAL_IF(!defined_type, *this) << "@VintfStability is not attached to a type";
  return GetScopedAnnotation(*defined_type, AidlAnnotation::Type::VINTF_STABILITY);
}

bool AidlAnnotatable::IsJavaOnlyImmutable() const {
  return GetAnnotation(annotations_, AidlAnnotation::Type::JAVA_ONLY_IMMUTABLE);
}

bool AidlAnnotatable::IsFixedSize() const {
  return GetAnnotation(annotations_, AidlAnnotation::Type::FIXED_SIZE);
}

const AidlAnnotation* AidlAnnotatable::UnsupportedAppUsage() const {
  return GetAnnotation(annotations_, AidlAnnotation::Type::UNSUPPORTED_APP_USAGE);
}

std::vector<std::string> AidlAnnotatable::RustDerive() const {
  std::vector<std::string> ret;
  if (const auto* ann = GetAnnotation(annotations_, AidlAnnotation::Type::RUST_DERIVE)) {
    for (const auto& name_and_param : ann->AnnotationParams(AidlConstantValueDecorator)) {
      if (name_and_param.second == "true") {
        ret.push_back(name_and_param.first);
      }
    }
  }
  return ret;
}

const AidlAnnotation* AidlAnnotatable::BackingType() const {
  return GetAnnotation(annotations_, AidlAnnotation::Type::BACKING);
}

std::vector<std::string> AidlAnnotatable::SuppressWarnings() const {
  auto annot = GetAnnotation(annotations_, AidlAnnotation::Type::SUPPRESS_WARNINGS);
  if (annot) {
    auto names = annot->ParamValue<std::vector<std::string>>("value");
    AIDL_FATAL_IF(!names.has_value(), this);
    return std::move(names.value());
  }
  return {};
}

// Parses the @Enforce annotation expression.
std::unique_ptr<android::aidl::perm::Expression> AidlAnnotatable::EnforceExpression() const {
  auto annot = GetAnnotation(annotations_, AidlAnnotation::Type::PERMISSION_ENFORCE);
  if (annot) {
    auto perm_expr = annot->EnforceExpression();
    if (!perm_expr.ok()) {
      // This should have been caught during validation.
      AIDL_FATAL(this) << "Unable to parse @EnforcePermission annotation: " << perm_expr.error();
    }
    return std::move(perm_expr.value());
  }
  return {};
}

bool AidlAnnotatable::IsPermissionManual() const {
  return GetAnnotation(annotations_, AidlAnnotation::Type::PERMISSION_MANUAL);
}

bool AidlAnnotatable::IsPermissionNone() const {
  return GetAnnotation(annotations_, AidlAnnotation::Type::PERMISSION_NONE);
}

bool AidlAnnotatable::IsPermissionAnnotated() const {
  return IsPermissionNone() || IsPermissionManual() || EnforceExpression();
}

bool AidlAnnotatable::IsPropagateAllowBlocking() const {
  return GetAnnotation(annotations_, AidlAnnotation::Type::PROPAGATE_ALLOW_BLOCKING);
}

bool AidlAnnotatable::IsStableApiParcelable(Options::Language lang) const {
  if (lang == Options::Language::JAVA)
    return GetAnnotation(annotations_, AidlAnnotation::Type::JAVA_STABLE_PARCELABLE);
  if (lang == Options::Language::NDK)
    return GetAnnotation(annotations_, AidlAnnotation::Type::NDK_STABLE_PARCELABLE);
  if (lang == Options::Language::RUST)
    return GetAnnotation(annotations_, AidlAnnotation::Type::RUST_STABLE_PARCELABLE);
  return false;
}

bool AidlAnnotatable::JavaDerive(const std::string& method) const {
  auto annotation = GetAnnotation(annotations_, AidlAnnotation::Type::JAVA_DERIVE);
  if (annotation != nullptr) {
    return annotation->ParamValue<bool>(method).value_or(false);
  }
  return false;
}

bool AidlAnnotatable::IsJavaDefault() const {
  return GetAnnotation(annotations_, AidlAnnotation::Type::JAVA_DEFAULT);
}

bool AidlAnnotatable::IsJavaDelegator() const {
  return GetAnnotation(annotations_, AidlAnnotation::Type::JAVA_DELEGATOR);
}

std::string AidlAnnotatable::GetDescriptor() const {
  auto annotation = GetAnnotation(annotations_, AidlAnnotation::Type::DESCRIPTOR);
  if (annotation != nullptr) {
    return annotation->ParamValue<std::string>("value").value();
  }
  return "";
}

bool AidlAnnotatable::CheckValid(const AidlTypenames&) const {
  for (const auto& annotation : GetAnnotations()) {
    if (!annotation->CheckValid()) {
      return false;
    }
  }

  std::map<AidlAnnotation::Type, AidlLocation> declared;
  for (const auto& annotation : GetAnnotations()) {
    const auto& [iter, inserted] =
        declared.emplace(annotation->GetType(), annotation->GetLocation());
    if (!inserted && !annotation->Repeatable()) {
      AIDL_ERROR(this) << "'" << annotation->GetName()
                       << "' is repeated, but not allowed. Previous location: " << iter->second;
      return false;
    }
  }

  return true;
}

string AidlAnnotatable::ToString() const {
  vector<string> ret;
  for (const auto& a : annotations_) {
    ret.emplace_back(a->ToString());
  }
  std::sort(ret.begin(), ret.end());
  return Join(ret, " ");
}

AidlTypeSpecifier::AidlTypeSpecifier(const AidlLocation& location, const string& unresolved_name,
                                     std::optional<ArrayType> array,
                                     vector<unique_ptr<AidlTypeSpecifier>>* type_params,
                                     const Comments& comments)
    : AidlAnnotatable(location, comments),
      AidlParameterizable<unique_ptr<AidlTypeSpecifier>>(type_params),
      unresolved_name_(unresolved_name),
      array_(std::move(array)),
      split_name_(Split(unresolved_name, ".")) {}

void AidlTypeSpecifier::ViewAsArrayBase(std::function<void(const AidlTypeSpecifier&)> func) const {
  AIDL_FATAL_IF(!array_.has_value(), this);
  // Declaring array of generic type cannot happen, it is grammar error.
  AIDL_FATAL_IF(IsGeneric(), this);

  bool is_mutated = mutated_;
  mutated_ = true;
  // mutate the array type to its base by removing a single dimension
  // e.g.) T[] => T, T[N][M] => T[M] (note that, M is removed)
  if (IsFixedSizeArray() && std::get<FixedSizeArray>(*array_).dimensions.size() > 1) {
    auto& dimensions = std::get<FixedSizeArray>(*array_).dimensions;
    auto dim = std::move(dimensions.front());
    dimensions.erase(dimensions.begin());
    func(*this);
    dimensions.insert(dimensions.begin(), std::move(dim));
  } else {
    ArrayType array_type = std::move(array_.value());
    array_ = std::nullopt;
    func(*this);
    array_ = std::move(array_type);
  }
  mutated_ = is_mutated;
}

bool AidlTypeSpecifier::MakeArray(ArrayType array_type) {
  // T becomes T[] or T[N]
  if (!IsArray()) {
    array_ = std::move(array_type);
    return true;
  }
  // T[N] becomes T[N][M]
  if (auto fixed_size_array = std::get_if<FixedSizeArray>(&array_type);
      fixed_size_array != nullptr && IsFixedSizeArray()) {
    // concat dimensions
    for (auto& dim : fixed_size_array->dimensions) {
      std::get<FixedSizeArray>(*array_).dimensions.push_back(std::move(dim));
    }
    return true;
  }
  return false;
}

std::vector<int32_t> FixedSizeArray::GetDimensionInts() const {
  std::vector<int32_t> ints;
  for (const auto& dim : dimensions) {
    ints.push_back(dim->EvaluatedValue<int32_t>());
  }
  return ints;
}

std::vector<int32_t> AidlTypeSpecifier::GetFixedSizeArrayDimensions() const {
  AIDL_FATAL_IF(!IsFixedSizeArray(), "not a fixed-size array");
  return std::get<FixedSizeArray>(GetArray()).GetDimensionInts();
}

string AidlTypeSpecifier::Signature() const {
  string ret = GetName();
  if (IsGeneric()) {
    vector<string> arg_names;
    for (const auto& ta : GetTypeParameters()) {
      arg_names.emplace_back(ta->Signature());
    }
    ret += "<" + Join(arg_names, ",") + ">";
  }
  if (IsArray()) {
    if (IsFixedSizeArray()) {
      for (const auto& dim : GetFixedSizeArrayDimensions()) {
        ret += "[" + std::to_string(dim) + "]";
      }
    } else {
      ret += "[]";
    }
  }
  return ret;
}

string AidlTypeSpecifier::ToString() const {
  string ret = Signature();
  string annotations = AidlAnnotatable::ToString();
  if (annotations != "") {
    ret = annotations + " " + ret;
  }
  return ret;
}

// When `scope` is specified, name is resolved first based on it.
// `scope` can be null for built-in types and fully-qualified types.
bool AidlTypeSpecifier::Resolve(const AidlTypenames& typenames, const AidlScope* scope) {
  AIDL_FATAL_IF(IsResolved(), this);
  std::string name = unresolved_name_;
  if (scope) {
    name = scope->ResolveName(name);
  }
  AidlTypenames::ResolvedTypename result = typenames.ResolveTypename(name);
  if (result.is_resolved) {
    fully_qualified_name_ = result.canonical_name;
    split_name_ = Split(fully_qualified_name_, ".");
    defined_type_ = result.defined_type;
  }
  return result.is_resolved;
}

const AidlDefinedType* AidlTypeSpecifier::GetDefinedType() const {
  return defined_type_;
}

bool AidlTypeSpecifier::CheckValid(const AidlTypenames& typenames) const {
  if (!AidlAnnotatable::CheckValid(typenames)) {
    return false;
  }
  if (IsGeneric()) {
    const auto& types = GetTypeParameters();
    for (const auto& arg : types) {
      if (!arg->CheckValid(typenames)) {
        return false;
      }
    }

    const string& type_name = GetName();
    // TODO(b/136048684) Disallow to use primitive types only if it is List or Map.
    if (type_name == "List" || type_name == "Map") {
      if (std::any_of(types.begin(), types.end(), [&](auto& type_ptr) {
            return !type_ptr->IsArray() &&
                   (typenames.GetEnumDeclaration(*type_ptr) ||
                    AidlTypenames::IsPrimitiveTypename(type_ptr->GetName()));
          })) {
        AIDL_ERROR(this) << "A generic type cannot have any primitive type parameters.";
        return false;
      }
    }
    const auto defined_type = typenames.TryGetDefinedType(type_name);
    const auto parameterizable =
        defined_type != nullptr ? defined_type->AsParameterizable() : nullptr;
    const bool is_user_defined_generic_type =
        parameterizable != nullptr && parameterizable->IsGeneric();
    const size_t num_params = GetTypeParameters().size();
    if (type_name == "List") {
      if (num_params > 1) {
        AIDL_ERROR(this) << "List can only have one type parameter, but got: '" << Signature()
                         << "'";
        return false;
      }
      static const char* kListUsage =
          "List<T> supports interface/parcelable/union, String, IBinder, and ParcelFileDescriptor.";
      const AidlTypeSpecifier& contained_type = *GetTypeParameters()[0];
      if (contained_type.IsArray()) {
        AIDL_ERROR(this) << "List of arrays is not supported. " << kListUsage;
        return false;
      }
      const string& contained_type_name = contained_type.GetName();
      if (AidlTypenames::IsBuiltinTypename(contained_type_name)) {
        if (contained_type_name != "String" && contained_type_name != "IBinder" &&
            contained_type_name != "ParcelFileDescriptor") {
          AIDL_ERROR(this) << "List<" << contained_type_name << "> is not supported. "
                           << kListUsage;
          return false;
        }
      }
    } else if (type_name == "Map") {
      if (num_params != 0 && num_params != 2) {
        AIDL_ERROR(this) << "Map must have 0 or 2 type parameters, but got "
                         << "'" << Signature() << "'";
        return false;
      }
      if (num_params == 2) {
        const string& key_type = GetTypeParameters()[0]->Signature();
        if (key_type != "String") {
          AIDL_ERROR(this) << "The type of key in map must be String, but it is "
                           << "'" << key_type << "'";
          return false;
        }
      }
    } else if (is_user_defined_generic_type) {
      const size_t allowed = parameterizable->GetTypeParameters().size();
      if (num_params != allowed) {
        AIDL_ERROR(this) << type_name << " must have " << allowed << " type parameters, but got "
                         << num_params;
        return false;
      }
    } else {
      AIDL_ERROR(this) << type_name << " is not a generic type.";
      return false;
    }
  }

  const bool is_generic_string_list = GetName() == "List" && IsGeneric() &&
                                      GetTypeParameters().size() == 1 &&
                                      GetTypeParameters()[0]->GetName() == "String";
  if (IsUtf8InCpp() && (GetName() != "String" && !is_generic_string_list)) {
    AIDL_ERROR(this) << "@utf8InCpp can only be used on String, String[], and List<String>.";
    return false;
  }

  if (GetName() == "void") {
    if (IsArray() || IsNullable() || IsUtf8InCpp()) {
      AIDL_ERROR(this) << "void type cannot be an array or nullable or utf8 string";
      return false;
    }
  }

  if (IsArray()) {
    if (GetName() == "ParcelableHolder" || GetName() == "List" || GetName() == "Map" ||
        GetName() == "CharSequence") {
      AIDL_ERROR(this) << "Arrays of " << GetName() << " are not supported.";
      return false;
    }
  }

  if (IsNullable()) {
    if (AidlTypenames::IsPrimitiveTypename(GetName()) && !IsArray()) {
      AIDL_ERROR(this) << "Primitive type cannot get nullable annotation";
      return false;
    }
    const auto defined_type = typenames.TryGetDefinedType(GetName());
    if (defined_type != nullptr && defined_type->AsEnumDeclaration() != nullptr && !IsArray()) {
      AIDL_ERROR(this) << "Enum type cannot get nullable annotation";
      return false;
    }
    if (GetName() == "ParcelableHolder") {
      AIDL_ERROR(this) << "ParcelableHolder cannot be nullable.";
      return false;
    }
    if (IsHeapNullable()) {
      if (!defined_type || IsArray() || !defined_type->AsParcelable()) {
        AIDL_ERROR(this) << "@nullable(heap=true) is available to parcelables.";
        return false;
      }
    }
  }

  if (IsFixedSizeArray()) {
    for (const auto& dim : std::get<FixedSizeArray>(GetArray()).dimensions) {
      if (!dim->Evaluate()) {
        return false;
      }
      if (dim->GetType() > AidlConstantValue::Type::INT32) {
        AIDL_ERROR(this) << "Array size must be a positive number: " << dim->Literal();
        return false;
      }
      auto value = dim->EvaluatedValue<int32_t>();
      if (value < 0) {
        AIDL_ERROR(this) << "Array size must be a positive number: " << value;
        return false;
      }
    }
  }
  return true;
}

void AidlTypeSpecifier::TraverseChildren(std::function<void(const AidlNode&)> traverse) const {
  AidlAnnotatable::TraverseChildren(traverse);
  if (IsGeneric()) {
    for (const auto& tp : GetTypeParameters()) {
      traverse(*tp);
    }
  }
  if (IsFixedSizeArray()) {
    for (const auto& dim : std::get<FixedSizeArray>(GetArray()).dimensions) {
      traverse(*dim);
    }
  }
}

std::string AidlConstantValueDecorator(
    const AidlTypeSpecifier& type,
    const std::variant<std::string, std::vector<std::string>>& raw_value) {
  if (type.IsArray()) {
    const auto& values = std::get<std::vector<std::string>>(raw_value);
    return "{" + Join(values, ", ") + "}";
  }
  const std::string& value = std::get<std::string>(raw_value);
  if (auto defined_type = type.GetDefinedType(); defined_type) {
    auto enum_type = defined_type->AsEnumDeclaration();
    AIDL_FATAL_IF(!enum_type, type) << "Invalid type for \"" << value << "\"";
    return type.GetName() + "." + value.substr(value.find_last_of('.') + 1);
  }
  return value;
}

AidlVariableDeclaration::AidlVariableDeclaration(const AidlLocation& location,
                                                 AidlTypeSpecifier* type, const std::string& name)
    : AidlVariableDeclaration(location, type, name, AidlConstantValue::Default(*type)) {
  default_user_specified_ = false;
}

AidlVariableDeclaration::AidlVariableDeclaration(const AidlLocation& location,
                                                 AidlTypeSpecifier* type, const std::string& name,
                                                 AidlConstantValue* default_value)
    : AidlMember(location, type->GetComments()),
      type_(type),
      name_(name),
      default_user_specified_(true),
      default_value_(default_value) {}

bool AidlVariableDeclaration::HasUsefulDefaultValue() const {
  if (GetDefaultValue()) {
    return true;
  }
  // null is accepted as a valid default value in all backends
  if (GetType().IsNullable()) {
    return true;
  }
  return false;
}

bool AidlVariableDeclaration::CheckValid(const AidlTypenames& typenames) const {
  bool valid = true;
  valid &= type_->CheckValid(typenames);

  if (type_->GetName() == "void") {
    AIDL_ERROR(this) << "Declaration " << name_
                     << " is void, but declarations cannot be of void type.";
    valid = false;
  }

  if (default_value_ == nullptr) return valid;
  valid &= default_value_->CheckValid();

  if (!valid) return false;

  return !ValueString(AidlConstantValueDecorator).empty();
}

string AidlVariableDeclaration::GetCapitalizedName() const {
  AIDL_FATAL_IF(name_.size() <= 0, *this) << "Name can't be empty.";
  string str = name_;
  str[0] = static_cast<char>(toupper(str[0]));
  return str;
}

string AidlVariableDeclaration::ToString() const {
  string ret = type_->ToString() + " " + name_;
  if (default_value_ != nullptr && default_user_specified_) {
    ret += " = " + ValueString(AidlConstantValueDecorator);
  }
  return ret;
}

string AidlVariableDeclaration::Signature() const {
  return type_->Signature() + " " + name_;
}

std::string AidlVariableDeclaration::ValueString(const ConstantValueDecorator& decorator) const {
  if (default_value_ != nullptr) {
    return default_value_->ValueString(GetType(), decorator);
  } else {
    return "";
  }
}

void AidlVariableDeclaration::TraverseChildren(
    std::function<void(const AidlNode&)> traverse) const {
  traverse(GetType());
  if (auto default_value = GetDefaultValue(); default_value) {
    traverse(*default_value);
  }
}

AidlArgument::AidlArgument(const AidlLocation& location, AidlArgument::Direction direction,
                           AidlTypeSpecifier* type, const std::string& name)
    : AidlVariableDeclaration(location, type, name),
      direction_(direction),
      direction_specified_(true) {}

AidlArgument::AidlArgument(const AidlLocation& location, AidlTypeSpecifier* type,
                           const std::string& name)
    : AidlVariableDeclaration(location, type, name),
      direction_(AidlArgument::IN_DIR),
      direction_specified_(false) {}

static std::string to_string(AidlArgument::Direction direction) {
  switch (direction) {
    case AidlArgument::IN_DIR:
      return "in";
    case AidlArgument::OUT_DIR:
      return "out";
    case AidlArgument::INOUT_DIR:
      return "inout";
  }
}

string AidlArgument::GetDirectionSpecifier() const {
  string ret;
  if (direction_specified_) {
    ret = to_string(direction_);
  }
  return ret;
}

string AidlArgument::ToString() const {
  if (direction_specified_) {
    return GetDirectionSpecifier() + " " + AidlVariableDeclaration::ToString();
  } else {
    return AidlVariableDeclaration::ToString();
  }
}

static std::string FormatDirections(const std::set<AidlArgument::Direction>& directions) {
  std::vector<std::string> out;
  for (const auto& d : directions) {
    out.push_back(to_string(d));
  }

  if (out.size() <= 1) {  // [] => "" or [A] => "A"
    return Join(out, "");
  } else if (out.size() == 2) {  // [A,B] => "A or B"
    return Join(out, " or ");
  } else {  // [A,B,C] => "A, B, or C"
    out.back() = "or " + out.back();
    return Join(out, ", ");
  }
}

bool AidlArgument::CheckValid(const AidlTypenames& typenames) const {
  if (!GetType().CheckValid(typenames)) {
    return false;
  }

  const auto& aspect = typenames.GetArgumentAspect(GetType());

  if (aspect.possible_directions.size() == 0) {
    AIDL_ERROR(this) << aspect.name << " cannot be an argument type";
    return false;
  }

  // when direction is not specified, "in" is assumed and should be the only possible direction
  if (!DirectionWasSpecified() && aspect.possible_directions != std::set{AidlArgument::IN_DIR}) {
    AIDL_ERROR(this) << "The direction of '" << GetName() << "' is not specified. " << aspect.name
                     << " can be an " << FormatDirections(aspect.possible_directions)
                     << " parameter.";
    return false;
  }

  if (aspect.possible_directions.count(GetDirection()) == 0) {
    AIDL_ERROR(this) << "'" << GetName() << "' can't be an " << GetDirectionSpecifier()
                     << " parameter because " << aspect.name << " can only be an "
                     << FormatDirections(aspect.possible_directions) << " parameter.";
    return false;
  }

  return true;
}

bool AidlCommentable::IsHidden() const {
  return android::aidl::HasHideInComments(GetComments());
}

bool AidlCommentable::IsDeprecated() const {
  return android::aidl::FindDeprecated(GetComments()).has_value();
}

AidlMember::AidlMember(const AidlLocation& location, const Comments& comments)
    : AidlAnnotatable(location, comments) {}

AidlConstantDeclaration::AidlConstantDeclaration(const AidlLocation& location,
                                                 AidlTypeSpecifier* type, const std::string& name,
                                                 AidlConstantValue* value)
    : AidlMember(location, type->GetComments()), type_(type), name_(name), value_(value) {}

bool AidlConstantDeclaration::CheckValid(const AidlTypenames& typenames) const {
  bool valid = true;
  valid &= type_->CheckValid(typenames);
  valid &= value_->CheckValid();
  valid = valid && !ValueString(AidlConstantValueDecorator).empty();
  if (!valid) return false;

  const static set<string> kSupportedConstTypes = {"String", "byte",  "int",
                                                   "long",   "float", "double"};
  if (kSupportedConstTypes.find(type_->Signature()) == kSupportedConstTypes.end()) {
    AIDL_ERROR(this) << "Constant of type " << type_->Signature() << " is not supported.";
    return false;
  }

  return true;
}

string AidlConstantDeclaration::ToString() const {
  return "const " + type_->ToString() + " " + name_ + " = " +
         ValueString(AidlConstantValueDecorator);
}

string AidlConstantDeclaration::Signature() const {
  return type_->Signature() + " " + name_;
}

AidlMethod::AidlMethod(const AidlLocation& location, bool oneway, AidlTypeSpecifier* type,
                       const std::string& name, std::vector<std::unique_ptr<AidlArgument>>* args,
                       const Comments& comments)
    : AidlMethod(location, oneway, type, name, args, comments, 0) {
  has_id_ = false;
}

AidlMethod::AidlMethod(const AidlLocation& location, bool oneway, AidlTypeSpecifier* type,
                       const std::string& name, std::vector<std::unique_ptr<AidlArgument>>* args,
                       const Comments& comments, int id)
    : AidlMember(location, comments),
      oneway_(oneway),
      oneway_annotation_(oneway),
      type_(type),
      name_(name),
      arguments_(std::move(*args)),
      id_(id) {
  has_id_ = true;
  delete args;
  for (const unique_ptr<AidlArgument>& a : arguments_) {
    if (a->IsIn()) { in_arguments_.push_back(a.get()); }
    if (a->IsOut()) { out_arguments_.push_back(a.get()); }
  }
}

string AidlMethod::Signature() const {
  vector<string> arg_signatures;
  for (const auto& arg : GetArguments()) {
    arg_signatures.emplace_back(arg->GetType().Signature());
  }
  return GetName() + "(" + Join(arg_signatures, ", ") + ")";
}

string AidlMethod::ToString() const {
  vector<string> arg_strings;
  for (const auto& arg : GetArguments()) {
    arg_strings.emplace_back(arg->ToString());
  }
  string ret = (IsOneway() ? "oneway " : "") + GetType().ToString() + " " + GetName() + "(" +
               Join(arg_strings, ", ") + ")";
  if (HasId()) {
    ret += " = " + std::to_string(GetId());
  }
  return ret;
}

bool AidlMethod::CheckValid(const AidlTypenames& typenames) const {
  if (!GetType().CheckValid(typenames)) {
    return false;
  }

  // TODO(b/156872582): Support it when ParcelableHolder supports every backend.
  if (GetType().GetName() == "ParcelableHolder") {
    AIDL_ERROR(this) << "ParcelableHolder cannot be a return type";
    return false;
  }
  if (IsOneway() && GetType().GetName() != "void") {
    AIDL_ERROR(this) << "oneway method '" << GetName() << "' cannot return a value";
    return false;
  }

  set<string> argument_names;
  for (const auto& arg : GetArguments()) {
    auto it = argument_names.find(arg->GetName());
    if (it != argument_names.end()) {
      AIDL_ERROR(this) << "method '" << GetName() << "' has duplicate argument name '"
                       << arg->GetName() << "'";
      return false;
    }
    argument_names.insert(arg->GetName());

    if (!arg->CheckValid(typenames)) {
      return false;
    }

    if (IsOneway() && arg->IsOut()) {
      AIDL_ERROR(this) << "oneway method '" << this->GetName() << "' cannot have out parameters";
      return false;
    }

    // check that the name doesn't match a keyword
    if (IsJavaKeyword(arg->GetName().c_str())) {
      AIDL_ERROR(arg) << "Argument name is a Java or aidl keyword";
      return false;
    }

    // Reserve a namespace for internal use
    if (android::base::StartsWith(arg->GetName(), "_aidl")) {
      AIDL_ERROR(arg) << "Argument name cannot begin with '_aidl'";
      return false;
    }

    if (arg->GetType().GetName() == "void") {
      AIDL_ERROR(arg->GetType()) << "'void' is an invalid type for the parameter '"
                                 << arg->GetName() << "'";
      return false;
    }
  }
  return true;
}

AidlDefinedType::AidlDefinedType(const AidlLocation& location, const std::string& name,
                                 const Comments& comments, const std::string& package,
                                 std::vector<std::unique_ptr<AidlMember>>* members)
    : AidlMember(location, comments), AidlScope(this), name_(name), package_(package) {
  // adjust name/package when name is fully qualified (for preprocessed files)
  if (package_.empty() && name_.find('.') != std::string::npos) {
    // Note that this logic is absolutely wrong.  Given a parcelable
    // org.some.Foo.Bar, the class name is Foo.Bar, but this code will claim that
    // the class is just Bar.  However, this was the way it was done in the past.
    //
    // See b/17415692
    auto pos = name.rfind('.');
    // name is the last part.
    name_ = name.substr(pos + 1);
    // package is the initial parts (except the last).
    package_ = name.substr(0, pos);
  }
  if (members) {
    for (auto& m : *members) {
      if (auto constant = AidlCast<AidlConstantDeclaration>(*m); constant) {
        constants_.emplace_back(constant);
      } else if (auto variable = AidlCast<AidlVariableDeclaration>(*m); variable) {
        variables_.emplace_back(variable);
      } else if (auto method = AidlCast<AidlMethod>(*m); method) {
        methods_.emplace_back(method);
      } else if (auto type = AidlCast<AidlDefinedType>(*m); type) {
        type->SetEnclosingScope(this);
        types_.emplace_back(type);
      } else {
        AIDL_FATAL(*m) << "Unknown member type.";
      }
      members_.push_back(m.release());
    }
    delete members;
  }
}

bool AidlDefinedType::CheckValid(const AidlTypenames& typenames) const {
  if (!AidlAnnotatable::CheckValid(typenames)) {
    return false;
  }
  if (!CheckValidWithMembers(typenames)) {
    return false;
  }
  return true;
}

std::string AidlDefinedType::GetCanonicalName() const {
  if (auto parent = GetParentType(); parent) {
    return parent->GetCanonicalName() + "." + GetName();
  }
  if (package_.empty()) {
    return GetName();
  }
  return GetPackage() + "." + GetName();
}

bool AidlDefinedType::CheckValidWithMembers(const AidlTypenames& typenames) const {
  bool success = true;

  for (const auto& t : GetNestedTypes()) {
    success = success && t->CheckValid(typenames);
  }

  if (auto parameterizable = AsParameterizable();
      parameterizable && parameterizable->IsGeneric() && !GetNestedTypes().empty()) {
    AIDL_ERROR(this) << "Generic types can't have nested types.";
    return false;
  }

  std::set<std::string> nested_type_names;
  for (const auto& t : GetNestedTypes()) {
    bool duplicated = !nested_type_names.emplace(t->GetName()).second;
    if (duplicated) {
      AIDL_ERROR(t) << "Redefinition of '" << t->GetName() << "'.";
      success = false;
    }
    // nested type can't have a parent name
    if (t->GetName() == GetName()) {
      AIDL_ERROR(t) << "Nested type '" << GetName() << "' has the same name as its parent.";
      success = false;
    }
    // Having unstructured parcelables as nested types doesn't make sense because they are defined
    // somewhere else in native languages (e.g. C++, Java...).
    if (AidlCast<AidlParcelable>(*t)) {
      AIDL_ERROR(t) << "'" << t->GetName()
                    << "' is nested. Unstructured parcelables should be at the root scope.";
      return false;
    }
  }

  if (!TopologicalVisit(GetNestedTypes(), [](auto&) {})) {
    AIDL_ERROR(this) << GetName()
                     << " has nested types with cyclic references. C++ and NDK backends don't "
                        "support cyclic references.";
    return false;
  }

  for (const auto& v : GetFields()) {
    const bool field_valid = v->CheckValid(typenames);
    success = success && field_valid;
  }

  // field names should be unique
  std::set<std::string> fieldnames;
  for (const auto& v : GetFields()) {
    bool duplicated = !fieldnames.emplace(v->GetName()).second;
    if (duplicated) {
      AIDL_ERROR(v) << "'" << GetName() << "' has duplicate field name '" << v->GetName() << "'";
      success = false;
    }
  }

  // immutable parcelables should have immutable fields.
  if (IsJavaOnlyImmutable()) {
    for (const auto& v : GetFields()) {
      if (!typenames.CanBeJavaOnlyImmutable(v->GetType())) {
        AIDL_ERROR(v) << "The @JavaOnlyImmutable '" << GetName() << "' has a "
                      << "non-immutable field named '" << v->GetName() << "'.";
        success = false;
      }
    }
  }

  // Rust derive fields must be transitive
  const std::vector<std::string> rust_derives = RustDerive();
  for (const auto& v : GetFields()) {
    const AidlDefinedType* field = typenames.TryGetDefinedType(v->GetType().GetName());
    if (!field) continue;

    // could get this from CONTEXT_*, but we don't currently save this info when we validated
    // contexts
    if (!field->AsStructuredParcelable() && !field->AsUnionDeclaration()) continue;

    auto subs = field->RustDerive();
    for (const std::string& derive : rust_derives) {
      if (std::find(subs.begin(), subs.end(), derive) == subs.end()) {
        AIDL_ERROR(v) << "Field " << v->GetName() << " of type with @RustDerive " << derive
                      << " also needs to derive this";
        success = false;
      }
    }
  }

  set<string> constant_names;
  for (const auto& constant : GetConstantDeclarations()) {
    if (constant_names.count(constant->GetName()) > 0) {
      AIDL_ERROR(constant) << "Found duplicate constant name '" << constant->GetName() << "'";
      success = false;
    }
    constant_names.insert(constant->GetName());
    success = success && constant->CheckValid(typenames);
  }

  return success;
}

bool AidlDefinedType::CheckValidForGetterNames() const {
  bool success = true;
  std::set<std::string> getters;
  for (const auto& v : GetFields()) {
    bool duplicated = !getters.emplace(v->GetCapitalizedName()).second;
    if (duplicated) {
      AIDL_ERROR(v) << "'" << GetName() << "' has duplicate field name '" << v->GetName()
                    << "' after capitalizing the first letter";
      success = false;
    }
  }
  return success;
}

const AidlDefinedType* AidlDefinedType::GetParentType() const {
  AIDL_FATAL_IF(GetEnclosingScope() == nullptr, this) << "Scope is not set.";
  return AidlCast<AidlDefinedType>(GetEnclosingScope()->GetNode());
}

const AidlDefinedType* AidlDefinedType::GetRootType() const {
  const AidlDefinedType* root = this;
  for (auto parent = root->GetParentType(); parent; parent = parent->GetParentType()) {
    root = parent;
  }
  return root;
}

// Resolve `name` in the current scope. If not found, delegate to the parent
std::string AidlDefinedType::ResolveName(const std::string& name) const {
  // For example, in the following, t1's type Baz means x.Foo.Bar.Baz
  // while t2's type is y.Baz.
  // package x;
  // import y.Baz;
  // parcelable Foo {
  //   parcelable Bar {
  //     enum Baz { ... }
  //     Baz t1; // -> should be x.Foo.Bar.Baz
  //   }
  //   Baz t2; // -> should be y.Baz
  //   Bar.Baz t3; // -> should be x.Foo.Bar.Baz
  // }
  AIDL_FATAL_IF(!GetEnclosingScope(), this)
      << "Type should have an enclosing scope.(e.g. AidlDocument)";
  if (AidlTypenames::IsBuiltinTypename(name)) {
    return name;
  }

  const auto first_dot = name.find_first_of('.');
  // For "Outer.Inner", we look up "Outer" in the import list.
  const std::string class_name =
      (first_dot == std::string::npos) ? name : name.substr(0, first_dot);
  // Keep ".Inner", to make a fully-qualified name
  const std::string nested_type = (first_dot == std::string::npos) ? "" : name.substr(first_dot);

  // check if it is a nested type
  for (const auto& type : GetNestedTypes()) {
    if (type->GetName() == class_name) {
      return type->GetCanonicalName() + nested_type;
    }
  }

  return GetEnclosingScope()->ResolveName(name);
}

template <>
const AidlDefinedType* AidlCast<AidlDefinedType>(const AidlNode& node) {
  struct Visitor : AidlVisitor {
    const AidlDefinedType* defined_type = nullptr;
    void Visit(const AidlInterface& t) override { defined_type = &t; }
    void Visit(const AidlEnumDeclaration& t) override { defined_type = &t; }
    void Visit(const AidlStructuredParcelable& t) override { defined_type = &t; }
    void Visit(const AidlUnionDecl& t) override { defined_type = &t; }
    void Visit(const AidlParcelable& t) override { defined_type = &t; }
  } v;
  node.DispatchVisit(v);
  return v.defined_type;
}

const AidlDocument& AidlDefinedType::GetDocument() const {
  const AidlDefinedType* root = GetRootType();
  auto scope = root->GetEnclosingScope();
  AIDL_FATAL_IF(!scope, this) << "no scope defined.";
  auto doc = AidlCast<AidlDocument>(scope->GetNode());
  AIDL_FATAL_IF(!doc, this) << "root scope is not a document.";
  return *doc;
}

AidlParcelable::AidlParcelable(const AidlLocation& location, const std::string& name,
                               const std::string& package, const Comments& comments,
                               const AidlUnstructuredHeaders& headers,
                               std::vector<std::string>* type_params,
                               std::vector<std::unique_ptr<AidlMember>>* members)
    : AidlDefinedType(location, name, comments, package, members),
      AidlParameterizable<std::string>(type_params),
      headers_(headers) {
  // Strip off quotation marks if we actually have headers.
  if (headers_.cpp.length() >= 2) {
    headers_.cpp = headers_.cpp.substr(1, headers_.cpp.length() - 2);
  }
  if (headers_.ndk.length() >= 2) {
    headers_.ndk = headers_.ndk.substr(1, headers_.ndk.length() - 2);
  }
  if (headers_.rust_type.length() >= 2) {
    headers_.rust_type = headers_.rust_type.substr(1, headers_.rust_type.length() - 2);
  }
}

template <typename T>
bool AidlParameterizable<T>::CheckValid() const {
  return true;
};

template <>
bool AidlParameterizable<std::string>::CheckValid() const {
  if (!IsGeneric()) {
    return true;
  }
  std::unordered_set<std::string> set(GetTypeParameters().begin(), GetTypeParameters().end());
  if (set.size() != GetTypeParameters().size()) {
    AIDL_ERROR(this->AsAidlNode()) << "Every type parameter should be unique.";
    return false;
  }
  return true;
}

bool AidlParcelable::CheckValid(const AidlTypenames& typenames) const {
  if (!AidlDefinedType::CheckValid(typenames)) {
    return false;
  }
  if (!AidlParameterizable<std::string>::CheckValid()) {
    return false;
  }

  bool success = true;
  if (IsFixedSize()) {
    for (const auto& v : GetFields()) {
      if (!typenames.CanBeFixedSize(v->GetType())) {
        AIDL_ERROR(v) << "The @FixedSize parcelable '" << this->GetName() << "' has a "
                      << "non-fixed size field named " << v->GetName() << ".";
        success = false;
      }
    }
  }

  return success;
}

AidlStructuredParcelable::AidlStructuredParcelable(
    const AidlLocation& location, const std::string& name, const std::string& package,
    const Comments& comments, std::vector<std::string>* type_params,
    std::vector<std::unique_ptr<AidlMember>>* members)
    : AidlParcelable(location, name, package, comments, {} /*headers*/, type_params, members) {}

bool AidlStructuredParcelable::CheckValid(const AidlTypenames& typenames) const {
  if (!AidlParcelable::CheckValid(typenames)) {
    return false;
  }

  bool success = true;

  if (IsJavaOnlyImmutable()) {
    // Immutable parcelables provide getters
    if (!CheckValidForGetterNames()) {
      success = false;
    }
  }

  return success;
}

// TODO: we should treat every backend all the same in future.
bool AidlTypeSpecifier::LanguageSpecificCheckValid(Options::Language lang) const {
  if (this->GetName() == "FileDescriptor" &&
      (lang == Options::Language::NDK || lang == Options::Language::RUST)) {
    AIDL_ERROR(this) << "FileDescriptor isn't supported by the " << to_string(lang) << " backend.";
    return false;
  }

  if (lang != Options::Language::JAVA) {
    if (this->GetName() == "List" && !this->IsGeneric()) {
      AIDL_ERROR(this) << "Currently, only the Java backend supports non-generic List.";
      return false;
    }
    if (this->GetName() == "Map" || this->GetName() == "CharSequence") {
      AIDL_ERROR(this) << "Currently, only Java backend supports " << this->GetName() << ".";
      return false;
    }
  }

  return true;
}

// TODO: we should treat every backend all the same in future.
bool AidlDefinedType::LanguageSpecificCheckValid(Options::Language lang) const {
  struct Visitor : AidlVisitor {
    Visitor(Options::Language lang) : lang(lang) {}
    void Visit(const AidlTypeSpecifier& type) override {
      success = success && type.LanguageSpecificCheckValid(lang);
    }
    Options::Language lang;
    bool success = true;
  } v(lang);
  VisitTopDown(v, *this);
  return v.success;
}

AidlEnumerator::AidlEnumerator(const AidlLocation& location, const std::string& name,
                               AidlConstantValue* value, const Comments& comments)
    : AidlCommentable(location, comments),
      name_(name),
      value_(value),
      value_user_specified_(value != nullptr) {}

bool AidlEnumerator::CheckValid(const AidlTypeSpecifier& enum_backing_type) const {
  if (GetValue() == nullptr) {
    return false;
  }
  if (!GetValue()->CheckValid()) {
    return false;
  }
  if (GetValue()->ValueString(enum_backing_type, AidlConstantValueDecorator).empty()) {
    AIDL_ERROR(this) << "Enumerator type differs from enum backing type.";
    return false;
  }
  return true;
}

string AidlEnumerator::ValueString(const AidlTypeSpecifier& backing_type,
                                   const ConstantValueDecorator& decorator) const {
  return GetValue()->ValueString(backing_type, decorator);
}

AidlEnumDeclaration::AidlEnumDeclaration(const AidlLocation& location, const std::string& name,
                                         std::vector<std::unique_ptr<AidlEnumerator>>* enumerators,
                                         const std::string& package, const Comments& comments)
    : AidlDefinedType(location, name, comments, package, nullptr),
      enumerators_(std::move(*enumerators)) {
  // Fill missing enumerator values with <prev + 1>
  // This can't be done in Autofill() because type/ref resolution depends on this.
  // For example, with enum E { A, B = A }, B's value 'A' is a reference which can't be
  // resolved if A has no value set.
  const AidlEnumerator* previous = nullptr;
  for (const auto& enumerator : enumerators_) {
    if (enumerator->GetValue() == nullptr) {
      auto loc = enumerator->GetLocation();
      if (previous == nullptr) {
        enumerator->SetValue(
            std::unique_ptr<AidlConstantValue>(AidlConstantValue::Integral(loc, "0")));
      } else {
        auto prev_value = std::make_unique<AidlConstantReference>(loc, previous->GetName());
        enumerator->SetValue(std::make_unique<AidlBinaryConstExpression>(
            loc, std::move(prev_value), "+",
            std::unique_ptr<AidlConstantValue>(AidlConstantValue::Integral(loc, "1"))));
      }
    }
    previous = enumerator.get();
  }
}

bool AidlEnumDeclaration::Autofill(const AidlTypenames& typenames) {
  if (auto annot = BackingType(); annot != nullptr) {
    // Autofill() is called before the grand CheckValid(). But AidlAnnotation::ParamValue()
    // calls AidlConstantValue::evaluate() which requires CheckValid() to be called before. So we
    // need to call CheckValid().
    if (!annot->CheckValid()) {
      return false;
    }
    auto type = annot->ParamValue<std::string>("type").value();
    backing_type_ = typenames.MakeResolvedType(annot->GetLocation(), type, false);
  } else {
    // Default to byte type for enums.
    backing_type_ = typenames.MakeResolvedType(GetLocation(), "byte", false);
  }

  // we only support/test a few backing types, so make sure this is a supported
  // one (otherwise boolean might work, which isn't supported/tested in all
  // backends)
  static std::set<string> kBackingTypes = {"byte", "int", "long"};
  if (kBackingTypes.find(backing_type_->GetName()) == kBackingTypes.end()) {
    AIDL_ERROR(this) << "Invalid backing type: " << backing_type_->GetName()
                     << ". Backing type must be one of: " << Join(kBackingTypes, ", ");
    return false;
  }
  return true;
}

bool AidlEnumDeclaration::CheckValid(const AidlTypenames& typenames) const {
  if (!AidlDefinedType::CheckValid(typenames)) {
    return false;
  }
  if (!GetMembers().empty()) {
    AIDL_ERROR(this) << "Enum doesn't support fields/constants/methods.";
    return false;
  }
  if (backing_type_ == nullptr) {
    AIDL_ERROR(this) << "Enum declaration missing backing type.";
    return false;
  }
  bool success = true;
  for (const auto& enumerator : enumerators_) {
    success = success && enumerator->CheckValid(GetBackingType());
  }

  return success;
}

AidlUnionDecl::AidlUnionDecl(const AidlLocation& location, const std::string& name,
                             const std::string& package, const Comments& comments,
                             std::vector<std::string>* type_params,
                             std::vector<std::unique_ptr<AidlMember>>* members)
    : AidlParcelable(location, name, package, comments, {} /*headers*/, type_params, members) {}

bool AidlUnionDecl::CheckValid(const AidlTypenames& typenames) const {
  // visit parents
  if (!AidlParcelable::CheckValid(typenames)) {
    return false;
  }

  // unions provide getters always
  if (!CheckValidForGetterNames()) {
    return false;
  }

  // now, visit self!
  bool success = true;

  // TODO(b/170807936) do we need to allow ParcelableHolder in union?
  for (const auto& v : GetFields()) {
    if (v->GetType().GetName() == "ParcelableHolder") {
      AIDL_ERROR(*v) << "A union can't have a member of ParcelableHolder '" << v->GetName() << "'";
      success = false;
    }
  }

  if (GetFields().empty()) {
    AIDL_ERROR(*this) << "The union '" << this->GetName() << "' has no fields.";
    return false;
  }

  // first member should have useful default value (implicit or explicit)
  const auto& first = GetFields()[0];
  if (!first->HasUsefulDefaultValue()) {
    // Most types can be initialized without a default value. For example,
    // interface types are inherently nullable. But, enum types should have
    // an explicit default value.
    if (!first->GetType().IsArray() && typenames.GetEnumDeclaration(first->GetType())) {
      AIDL_ERROR(first)
          << "The union's first member should have a useful default value. Enum types can be "
             "initialized with a reference. (e.g. ... = MyEnum.FOO;)";
      return false;
    }
    // In Java, array types are initialized as null without a default value. To be sure that default
    // initialized unions are accepted by other backends we require arrays also have a default
    // value.
    if (first->GetType().IsArray()) {
      AIDL_ERROR(first)
          << "The union's first member should have a useful default value. Arrays can be "
             "initialized with values(e.g. ... = { values... };) or marked as @nullable.";
      return false;
    }
  }

  return success;
}

AidlInterface::AidlInterface(const AidlLocation& location, const std::string& name,
                             const Comments& comments, bool oneway, const std::string& package,
                             std::vector<std::unique_ptr<AidlMember>>* members)
    : AidlDefinedType(location, name, comments, package, members), oneway_annotation_(oneway) {
  for (auto& m : GetMethods()) {
    m.get()->ApplyInterfaceOneway(oneway);
  }
}

bool AidlInterface::CheckValid(const AidlTypenames& typenames) const {
  if (!AidlDefinedType::CheckValid(typenames)) {
    return false;
  }
  // Has to be a pointer due to deleting copy constructor. No idea why.
  map<string, const AidlMethod*> method_names;
  for (const auto& m : GetMethods()) {
    if (!m->CheckValid(typenames)) {
      return false;
    }

    auto it = method_names.find(m->GetName());
    // prevent duplicate methods
    if (it == method_names.end()) {
      method_names[m->GetName()] = m.get();
    } else {
      AIDL_ERROR(m) << "attempt to redefine method " << m->GetName() << ":";
      AIDL_ERROR(it->second) << "previously defined here.";
      return false;
    }

    static set<string> reserved_methods{"asBinder()", "getInterfaceHash()", "getInterfaceVersion()",
                                        "getTransactionName(int)"};

    if (reserved_methods.find(m->Signature()) != reserved_methods.end()) {
      AIDL_ERROR(m) << " method " << m->Signature() << " is reserved for internal use.";
      return false;
    }

    if (!CheckValidPermissionAnnotations(*m.get())) {
      return false;
    }
  }

  return true;
}

bool AidlInterface::CheckValidPermissionAnnotations(const AidlMethod& m) const {
  if (IsPermissionAnnotated() && m.GetType().IsPermissionAnnotated()) {
    AIDL_ERROR(m) << "The interface " << GetName()
                  << " uses a permission annotation but the method " << m.GetName()
                  << " is also annotated.\n"
                  << "Consider distributing the annotation to each method.";
    return false;
  }
  return true;
}

bool AidlInterface::UsesPermissions() const {
  if (EnforceExpression()) {
    return true;
  }
  for (auto& m : GetMethods()) {
    if (m->GetType().EnforceExpression()) {
      return true;
    }
  }
  return false;
}

std::string AidlInterface::GetDescriptor() const {
  std::string annotatedDescriptor = AidlAnnotatable::GetDescriptor();
  if (annotatedDescriptor != "") {
    return annotatedDescriptor;
  }
  return GetCanonicalName();
}

AidlDocument::AidlDocument(const AidlLocation& location, const Comments& comments,
                           std::vector<string> imports,
                           std::vector<std::unique_ptr<AidlDefinedType>> defined_types,
                           bool is_preprocessed)
    : AidlCommentable(location, comments),
      AidlScope(this),
      imports_(std::move(imports)),
      defined_types_(std::move(defined_types)),
      is_preprocessed_(is_preprocessed) {
  for (const auto& t : defined_types_) {
    t->SetEnclosingScope(this);
  }
}

// Resolves type name in the current document.
// - built-in types
// - imported types
// - top-level type
std::string AidlDocument::ResolveName(const std::string& name) const {
  if (AidlTypenames::IsBuiltinTypename(name)) {
    return name;
  }

  const auto first_dot = name.find_first_of('.');
  // For "Outer.Inner", we look up "Outer" in the import list.
  const std::string class_name =
      (first_dot == std::string::npos) ? name : name.substr(0, first_dot);
  // Keep ".Inner", to make a fully-qualified name
  const std::string nested_type = (first_dot == std::string::npos) ? "" : name.substr(first_dot);

  for (const auto& import : Imports()) {
    if (SimpleName(import) == class_name) {
      return import + nested_type;
    }
  }

  // check if it is a top-level type.
  for (const auto& type : DefinedTypes()) {
    if (type->GetName() == class_name) {
      return type->GetCanonicalName() + nested_type;
    }
  }

  // name itself might be fully-qualified name.
  return name;
}
