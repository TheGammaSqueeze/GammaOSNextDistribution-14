/*
 * Copyright (C) 2019, The Android Open Source Project
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

#pragma once

#include <memory>
#include <regex>
#include <string>
#include <unordered_set>
#include <variant>
#include <vector>

#include <android-base/result.h>
#include <android-base/strings.h>

#include "aidl_typenames.h"
#include "code_writer.h"
#include "comments.h"
#include "diagnostics.h"
#include "io_delegate.h"
#include "location.h"
#include "logging.h"
#include "options.h"
#include "permission.h"

using android::aidl::AidlTypenames;
using android::aidl::CodeWriter;
using android::aidl::Comments;
using android::aidl::Options;
using android::base::Result;
using std::shared_ptr;
using std::string;
using std::unique_ptr;
using std::vector;
class AidlNode;

// helper to see if T is the same to one of Args types.
template <typename T, typename... Args>
struct is_one_of : std::false_type {};

template <typename T, typename S, typename... Args>
struct is_one_of<T, S, Args...> {
  enum { value = std::is_same_v<T, S> || is_one_of<T, Args...>::value };
};

// helper to see if T is std::vector of something.
template <typename>
struct is_vector : std::false_type {};

template <typename T>
struct is_vector<std::vector<T>> : std::true_type {};

// helper for static_assert(false)
template <typename T>
struct unsupported_type : std::false_type {};

namespace android {
namespace aidl {
namespace mappings {
std::string dump_location(const AidlNode& method);
}  // namespace mappings
namespace java {
std::string dump_location(const AidlNode& method);
}  // namespace java
}  // namespace aidl
}  // namespace android

bool ParseFloating(std::string_view sv, double* parsed);
bool ParseFloating(std::string_view sv, float* parsed);

class AidlDocument;
class AidlInterface;
class AidlParcelable;
class AidlStructuredParcelable;
class AidlEnumDeclaration;
class AidlUnionDecl;
class AidlVariableDeclaration;
class AidlConstantDeclaration;
class AidlEnumerator;
class AidlMethod;
class AidlArgument;
class AidlConstantValue;
class AidlConstantReference;
class AidlUnaryConstExpression;
class AidlBinaryConstExpression;
class AidlAnnotation;

// Interface for visitors that can traverse AidlTraversable nodes.
class AidlVisitor {
 public:
  virtual ~AidlVisitor() = default;
  virtual void Visit(const AidlDocument&) {}
  virtual void Visit(const AidlInterface&) {}
  virtual void Visit(const AidlParcelable&) {}
  virtual void Visit(const AidlStructuredParcelable&) {}
  virtual void Visit(const AidlUnionDecl&) {}
  virtual void Visit(const AidlEnumDeclaration&) {}
  virtual void Visit(const AidlEnumerator&) {}
  virtual void Visit(const AidlMethod&) {}
  virtual void Visit(const AidlVariableDeclaration&) {}
  virtual void Visit(const AidlConstantDeclaration&) {}
  virtual void Visit(const AidlArgument&) {}
  virtual void Visit(const AidlTypeSpecifier&) {}
  virtual void Visit(const AidlConstantValue&) {}
  virtual void Visit(const AidlConstantReference&) {}
  virtual void Visit(const AidlUnaryConstExpression&) {}
  virtual void Visit(const AidlBinaryConstExpression&) {}
  virtual void Visit(const AidlAnnotation&) {}
};

class AidlScope {
 public:
  AidlScope(const AidlNode* self) : self_(self) {}
  virtual ~AidlScope() = default;
  virtual std::string ResolveName(const std::string& name) const = 0;
  void SetEnclosingScope(const AidlScope* enclosing) {
    AIDL_FATAL_IF(enclosing_, AIDL_LOCATION_HERE) << "SetEnclosingScope can be set only once.";
    enclosing_ = enclosing;
  }
  const AidlScope* GetEnclosingScope() const { return enclosing_; }
  const AidlNode& GetNode() const { return *self_; }

 private:
  const AidlNode* self_;
  const AidlScope* enclosing_ = nullptr;
};

// Anything that is locatable in a .aidl file.
class AidlNode {
 public:
  AidlNode(const AidlLocation& location, const Comments& comments = {});

  virtual ~AidlNode();

  AidlNode(AidlNode&) = delete;
  AidlNode& operator=(AidlNode&) = delete;
  AidlNode(AidlNode&&) = delete;
  AidlNode& operator=(AidlNode&&) = delete;

  // To be able to print AidlLocation
  friend class AidlErrorLog;
  friend std::string android::aidl::mappings::dump_location(const AidlNode&);
  friend std::string android::aidl::java::dump_location(const AidlNode&);

  const AidlLocation& GetLocation() const { return location_; }
  virtual void TraverseChildren(std::function<void(const AidlNode&)> traverse) const = 0;
  virtual void DispatchVisit(AidlVisitor&) const = 0;

  const Comments& GetComments() const { return comments_; }
  void PrependComments(const Comments& comments) {
    comments_.insert(comments_.begin(), comments.begin(), comments.end());
  }

  static void ClearUnvisitedNodes();
  static const std::vector<AidlLocation>& GetLocationsOfUnvisitedNodes();
  void MarkVisited() const;
  bool IsUserDefined() const { return !GetLocation().IsInternal(); }

 private:
  std::string PrintLine() const;
  std::string PrintLocation() const;
  const AidlLocation location_;
  Comments comments_;

  // make sure we are able to abort if types are not visited
  mutable bool visited_ = false;
  static std::vector<AidlLocation> unvisited_locations_;
};

// unique_ptr<AidlTypeSpecifier> for type arugment,
// std::string for type parameter(T, U, and so on).
template <typename T>
class AidlParameterizable {
 public:
  AidlParameterizable(std::vector<T>* type_params) : type_params_(type_params) {}
  virtual ~AidlParameterizable() = default;
  bool IsGeneric() const { return type_params_ != nullptr; }
  const std::vector<T>& GetTypeParameters() const { return *type_params_; }
  bool CheckValid() const;

  __attribute__((warn_unused_result)) bool SetTypeParameters(std::vector<T>* type_params) {
    if (type_params_) return false;
    type_params_.reset(type_params);
    return true;
  }

  virtual const AidlNode& AsAidlNode() const = 0;

 private:
  unique_ptr<std::vector<T>> type_params_;
  static_assert(std::is_same<T, unique_ptr<AidlTypeSpecifier>>::value ||
                std::is_same<T, std::string>::value);
};
template <>
bool AidlParameterizable<std::string>::CheckValid() const;

class AidlCommentable : public AidlNode {
 public:
  AidlCommentable(const AidlLocation& location, const Comments& comments)
      : AidlNode(location, comments) {}
  virtual ~AidlCommentable() = default;

  bool IsHidden() const;
  bool IsDeprecated() const;
};

// Transforms a value string into a language specific form. Raw value as produced by
// AidlConstantValue.
using ConstantValueDecorator = std::function<std::string(
    const AidlTypeSpecifier& type,
    const std::variant<std::string, std::vector<std::string>>& raw_value)>;

class AidlAnnotation : public AidlNode {
 public:
  enum class Type {
    BACKING = 1,
    JAVA_STABLE_PARCELABLE,
    NDK_STABLE_PARCELABLE,
    RUST_STABLE_PARCELABLE,
    UNSUPPORTED_APP_USAGE,
    VINTF_STABILITY,
    NULLABLE,
    UTF8_IN_CPP,
    SENSITIVE_DATA,
    JAVA_PASSTHROUGH,
    JAVA_DERIVE,
    JAVA_DEFAULT,
    JAVA_DELEGATOR,
    JAVA_ONLY_IMMUTABLE,
    JAVA_SUPPRESS_LINT,
    FIXED_SIZE,
    DESCRIPTOR,
    RUST_DERIVE,
    SUPPRESS_WARNINGS,
    PERMISSION_ENFORCE,
    PERMISSION_NONE,
    PERMISSION_MANUAL,
    PROPAGATE_ALLOW_BLOCKING,
  };

  using TargetContext = uint16_t;
  static constexpr TargetContext CONTEXT_TYPE_INTERFACE = 0x1 << 0;
  static constexpr TargetContext CONTEXT_TYPE_ENUM = 0x1 << 1;
  static constexpr TargetContext CONTEXT_TYPE_STRUCTURED_PARCELABLE = 0x1 << 2;
  static constexpr TargetContext CONTEXT_TYPE_UNION = 0x1 << 3;
  static constexpr TargetContext CONTEXT_TYPE_UNSTRUCTURED_PARCELABLE = 0x1 << 4;
  static constexpr TargetContext CONTEXT_TYPE =
      CONTEXT_TYPE_INTERFACE | CONTEXT_TYPE_ENUM | CONTEXT_TYPE_STRUCTURED_PARCELABLE |
      CONTEXT_TYPE_UNION | CONTEXT_TYPE_UNSTRUCTURED_PARCELABLE;
  static constexpr TargetContext CONTEXT_CONST = 0x1 << 5;
  static constexpr TargetContext CONTEXT_FIELD = 0x1 << 6;
  static constexpr TargetContext CONTEXT_METHOD = 0x1 << 7;
  static constexpr TargetContext CONTEXT_MEMBER = CONTEXT_CONST | CONTEXT_FIELD | CONTEXT_METHOD;
  static constexpr TargetContext CONTEXT_TYPE_SPECIFIER = 0x1 << 8;
  static constexpr TargetContext CONTEXT_ALL =
      CONTEXT_TYPE | CONTEXT_MEMBER | CONTEXT_TYPE_SPECIFIER;

  static std::string TypeToString(Type type);

  static std::unique_ptr<AidlAnnotation> Parse(
      const AidlLocation& location, const string& name,
      std::map<std::string, std::shared_ptr<AidlConstantValue>> parameter_list,
      const Comments& comments);

  virtual ~AidlAnnotation() = default;
  bool CheckValid() const;
  bool CheckContext(TargetContext context) const;
  const string& GetName() const { return schema_.name; }
  const Type& GetType() const { return schema_.type; }
  bool Repeatable() const { return schema_.repeatable; }

  // ToString is for dumping AIDL.
  // Returns string representation of this annotation.
  // e.g) "@RustDerive(Clone=true, Copy=true)"
  string ToString() const;

  template <typename T>
  std::optional<T> ParamValue(const std::string& param_name) const;

  std::map<std::string, std::string> AnnotationParams(
      const ConstantValueDecorator& decorator) const;
  void TraverseChildren(std::function<void(const AidlNode&)> traverse) const override;
  void DispatchVisit(AidlVisitor& v) const override { v.Visit(*this); }

  Result<unique_ptr<android::aidl::perm::Expression>> EnforceExpression() const;

 private:
  struct ParamTypeDetails {
    std::string name;
    const AidlTypeSpecifier& type;
    bool required = false;
  };

  struct Schema {
    AidlAnnotation::Type type;
    std::string name;
    TargetContext target_context;
    std::vector<ParamTypeDetails> parameters;
    bool repeatable = false;

    const ParamTypeDetails* ParamType(const std::string& name) const {
      for (const auto& param : parameters) {
        if (param.name == name) {
          return &param;
        }
      }
      return nullptr;
    }
  };

  static const std::vector<Schema>& AllSchemas();

  AidlAnnotation(const AidlLocation& location, const Schema& schema,
                 std::map<std::string, std::shared_ptr<AidlConstantValue>> parameters,
                 const Comments& comments);

  const Schema& schema_;
  std::map<std::string, std::shared_ptr<AidlConstantValue>> parameters_;
};

static inline bool operator<(const AidlAnnotation& lhs, const AidlAnnotation& rhs) {
  return lhs.GetName() < rhs.GetName();
}
static inline bool operator==(const AidlAnnotation& lhs, const AidlAnnotation& rhs) {
  return lhs.GetName() == rhs.GetName();
}

class AidlAnnotatable : public AidlCommentable {
 public:
  AidlAnnotatable(const AidlLocation& location, const Comments& comments);

  virtual ~AidlAnnotatable() = default;

  void Annotate(vector<std::unique_ptr<AidlAnnotation>>&& annotations) {
    for (auto i = annotations.rbegin(); i != annotations.rend(); ++i) {
      // TODO: we may want to mark all comments as "unoutputed"
      // in the lexer phase, and then verify after successful backend
      // output that every comment is "emitted". If we do this, we would
      // need to move rather than copy the comments here.
      PrependComments((*i)->GetComments());
    }
    for (auto& annotation : annotations) {
      annotations_.emplace_back(std::move(annotation));
    }
  }
  bool IsNullable() const;
  bool IsHeapNullable() const;
  bool IsUtf8InCpp() const;
  bool IsSensitiveData() const;
  bool IsVintfStability() const;
  bool IsJavaOnlyImmutable() const;
  bool IsFixedSize() const;
  bool IsStableApiParcelable(Options::Language lang) const;
  bool JavaDerive(const std::string& method) const;
  bool IsJavaDefault() const;
  bool IsJavaDelegator() const;
  std::string GetDescriptor() const;

  const AidlAnnotation* UnsupportedAppUsage() const;
  std::vector<std::string> RustDerive() const;
  const AidlAnnotation* BackingType() const;
  std::vector<std::string> SuppressWarnings() const;
  std::unique_ptr<android::aidl::perm::Expression> EnforceExpression() const;
  bool IsPermissionManual() const;
  bool IsPermissionNone() const;
  bool IsPermissionAnnotated() const;
  bool IsPropagateAllowBlocking() const;

  // ToString is for dumping AIDL.
  // Returns string representation of annotations.
  // e.g) "@JavaDerive(toString=true) @RustDerive(Clone=true, Copy=true)"
  std::string ToString() const;

  const vector<std::unique_ptr<AidlAnnotation>>& GetAnnotations() const { return annotations_; }
  bool CheckValid(const AidlTypenames&) const;
  void TraverseChildren(std::function<void(const AidlNode&)> traverse) const override {
    for (const auto& annot : GetAnnotations()) {
      traverse(*annot);
    }
  }

 private:
  vector<std::unique_ptr<AidlAnnotation>> annotations_;
};

// Represents `[]`
struct DynamicArray {};
// Represents `[N][M]..`
struct FixedSizeArray {
  FixedSizeArray(std::unique_ptr<AidlConstantValue> dim) { dimensions.push_back(std::move(dim)); }
  std::vector<std::unique_ptr<AidlConstantValue>> dimensions;
  std::vector<int32_t> GetDimensionInts() const;
};
// Represents `[]` or `[N]` part of type specifier
using ArrayType = std::variant<DynamicArray, FixedSizeArray>;

// AidlTypeSpecifier represents a reference to either a built-in type,
// a defined type, or a variant (e.g., array of generic) of a type.
class AidlTypeSpecifier final : public AidlAnnotatable,
                                public AidlParameterizable<unique_ptr<AidlTypeSpecifier>> {
 public:
  AidlTypeSpecifier(const AidlLocation& location, const string& unresolved_name,
                    std::optional<ArrayType> array,
                    vector<unique_ptr<AidlTypeSpecifier>>* type_params, const Comments& comments);
  virtual ~AidlTypeSpecifier() = default;

  // View of this type which has one-less dimension(s).
  // e.g.) T[] => T, T[N][M] => T[M]
  void ViewAsArrayBase(std::function<void(const AidlTypeSpecifier&)> func) const;
  // ViewAsArrayBase passes "mutated" type to its callback.
  bool IsMutated() const { return mutated_; }

  // Returns the full-qualified name of the base type.
  // int -> int
  // int[] -> int
  // List<String> -> List
  // IFoo -> foo.bar.IFoo (if IFoo is in package foo.bar)
  const string& GetName() const {
    if (IsResolved()) {
      return fully_qualified_name_;
    } else {
      return GetUnresolvedName();
    }
  }

  // ToString is for dumping AIDL.
  // Returns string representation of this type specifier including annotations.
  // This is "annotations type_name type_params? array_marker?".
  // e.g) "@utf8InCpp String[]";
  std::string ToString() const;

  // Signature is for comparing AIDL types.
  // Returns string representation of this type specifier.
  // This is "type_name type_params? array_marker?".
  // e.g.) "String[]" (even if it is annotated with @utf8InCpp)
  std::string Signature() const;

  const string& GetUnresolvedName() const { return unresolved_name_; }

  const std::vector<std::string> GetSplitName() const { return split_name_; }

  bool IsResolved() const { return fully_qualified_name_ != ""; }

  bool IsArray() const { return array_.has_value(); }
  bool IsDynamicArray() const {
    return array_.has_value() && std::get_if<DynamicArray>(&*array_) != nullptr;
  }
  bool IsFixedSizeArray() const {
    return array_.has_value() && std::get_if<FixedSizeArray>(&*array_) != nullptr;
  }
  std::vector<int32_t> GetFixedSizeArrayDimensions() const;

  const ArrayType& GetArray() const {
    AIDL_FATAL_IF(!array_.has_value(), this) << "GetArray() for non-array type";
    return array_.value();
  }

  // Accept transitions from
  //    T    to T[]
  // or T    to T[N]
  // or T[N] to T[N][M]
  __attribute__((warn_unused_result)) bool MakeArray(ArrayType array_type);

  // Resolve the base type name to a fully-qualified name. Return false if the
  // resolution fails.
  bool Resolve(const AidlTypenames& typenames, const AidlScope* scope);

  bool CheckValid(const AidlTypenames& typenames) const;
  bool LanguageSpecificCheckValid(Options::Language lang) const;
  const AidlNode& AsAidlNode() const override { return *this; }

  const AidlDefinedType* GetDefinedType() const;
  void TraverseChildren(std::function<void(const AidlNode&)> traverse) const override;
  void DispatchVisit(AidlVisitor& v) const override { v.Visit(*this); }

 private:
  const string unresolved_name_;
  string fully_qualified_name_;
  mutable std::optional<ArrayType> array_;
  mutable bool mutated_ = false;  // ViewAsArrayBase() sets this as true to distinguish mutated one
                                  // from the original type
  vector<string> split_name_;
  const AidlDefinedType* defined_type_ = nullptr;  // set when Resolve() for defined types
};

// Returns the universal value unaltered.
std::string AidlConstantValueDecorator(
    const AidlTypeSpecifier& type,
    const std::variant<std::string, std::vector<std::string>>& raw_value);

class AidlMember : public AidlAnnotatable {
 public:
  AidlMember(const AidlLocation& location, const Comments& comments);
  virtual ~AidlMember() = default;

  // non-copyable, non-movable
  AidlMember(const AidlMember&) = delete;
  AidlMember(AidlMember&&) = delete;
  AidlMember& operator=(const AidlMember&) = delete;
  AidlMember& operator=(AidlMember&&) = delete;
  void MarkNew() { new_ = true; }
  bool IsNew() const { return new_; }

 private:
  bool new_ = false;
};

// TODO: This class is used for method arguments and also parcelable fields,
// and it should be split up since default values don't apply to method
// arguments
class AidlVariableDeclaration : public AidlMember {
 public:
  AidlVariableDeclaration(const AidlLocation& location, AidlTypeSpecifier* type,
                          const std::string& name);
  AidlVariableDeclaration(const AidlLocation& location, AidlTypeSpecifier* type,
                          const std::string& name, AidlConstantValue* default_value);
  virtual ~AidlVariableDeclaration() = default;

  // non-copyable, non-movable
  AidlVariableDeclaration(const AidlVariableDeclaration&) = delete;
  AidlVariableDeclaration(AidlVariableDeclaration&&) = delete;
  AidlVariableDeclaration& operator=(const AidlVariableDeclaration&) = delete;
  AidlVariableDeclaration& operator=(AidlVariableDeclaration&&) = delete;

  std::string GetName() const { return name_; }
  std::string GetCapitalizedName() const;
  const AidlTypeSpecifier& GetType() const { return *type_; }
  // if this was constructed explicitly with a default value
  bool IsDefaultUserSpecified() const { return default_user_specified_; }
  // will return the default value this is constructed with or a default value
  // if one is available
  const AidlConstantValue* GetDefaultValue() const { return default_value_.get(); }
  bool HasUsefulDefaultValue() const;

  AidlTypeSpecifier* GetMutableType() { return type_.get(); }

  bool CheckValid(const AidlTypenames& typenames) const;

  // ToString is for dumping AIDL.
  // Returns string representation of this variable decl including default value.
  // This is "annotations type name default_value?".
  // e.g) "@utf8InCpp String[] names = {"hello"}"
  std::string ToString() const;

  // Signature is for comparing AIDL types.
  // Returns string representation of this variable decl.
  // This is "type name".
  // e.g) "String[] name" (even if it is annotated with @utf8InCpp and has a default value.)
  std::string Signature() const;

  std::string ValueString(const ConstantValueDecorator& decorator) const;

  void TraverseChildren(std::function<void(const AidlNode&)> traverse) const override;
  void DispatchVisit(AidlVisitor& v) const override { v.Visit(*this); }

 private:
  std::unique_ptr<AidlTypeSpecifier> type_;
  std::string name_;
  bool default_user_specified_;
  std::unique_ptr<AidlConstantValue> default_value_;
};

class AidlArgument : public AidlVariableDeclaration {
 public:
  enum Direction { IN_DIR = 1, OUT_DIR = 2, INOUT_DIR = 3 };

  AidlArgument(const AidlLocation& location, AidlArgument::Direction direction,
               AidlTypeSpecifier* type, const std::string& name);
  AidlArgument(const AidlLocation& location, AidlTypeSpecifier* type, const std::string& name);
  virtual ~AidlArgument() = default;

  // non-copyable, non-movable
  AidlArgument(const AidlArgument&) = delete;
  AidlArgument(AidlArgument&&) = delete;
  AidlArgument& operator=(const AidlArgument&) = delete;
  AidlArgument& operator=(AidlArgument&&) = delete;

  Direction GetDirection() const { return direction_; }
  bool IsOut() const { return direction_ & OUT_DIR; }
  bool IsIn() const { return direction_ & IN_DIR; }
  bool DirectionWasSpecified() const { return direction_specified_; }
  string GetDirectionSpecifier() const;
  bool CheckValid(const AidlTypenames& typenames) const;

  // ToString is for dumping AIDL.
  // Returns string representation of this argument including direction
  // This is "direction annotations type name".
  // e.g) "in @utf8InCpp String[] names"
  std::string ToString() const;

  void DispatchVisit(AidlVisitor& v) const override { v.Visit(*this); }

 private:
  Direction direction_;
  bool direction_specified_;
};

struct ArgumentAspect {
  std::string name;
  std::set<AidlArgument::Direction> possible_directions;
};

class AidlUnaryConstExpression;
class AidlBinaryConstExpression;
class AidlConstantReference;

class AidlConstantValue : public AidlNode {
 public:
  enum class Type {
    // WARNING: Don't change this order! The order is used to determine type
    // promotion during a binary expression.
    BOOLEAN,
    INT8,
    INT32,
    INT64,
    ARRAY,
    CHARACTER,
    STRING,
    REF,
    FLOATING,
    UNARY,
    BINARY,
    ERROR,
  };

  // Returns the evaluated value. T> should match to the actual type.
  template <typename T>
  T EvaluatedValue() const {
    is_evaluated_ || (CheckValid() && evaluate());
    AIDL_FATAL_IF(!is_valid_, this);

    if constexpr (is_vector<T>::value) {
      AIDL_FATAL_IF(final_type_ != Type::ARRAY, this);
      T result;
      for (const auto& v : values_) {
        result.push_back(v->EvaluatedValue<typename T::value_type>());
      }
      return result;
    } else if constexpr (is_one_of<T, float, double>::value) {
      AIDL_FATAL_IF(final_type_ != Type::FLOATING, this);
      T result;
      AIDL_FATAL_IF(!ParseFloating(value_, &result), this);
      return result;
    } else if constexpr (std::is_same<T, std::string>::value) {
      AIDL_FATAL_IF(final_type_ != Type::STRING, this);
      return final_string_value_.substr(1, final_string_value_.size() - 2);  // unquote "
    } else if constexpr (is_one_of<T, int8_t, int32_t, int64_t>::value) {
      AIDL_FATAL_IF(final_type_ < Type::INT8 && final_type_ > Type::INT64, this);
      return static_cast<T>(final_value_);
    } else if constexpr (std::is_same<T, char16_t>::value) {
      AIDL_FATAL_IF(final_type_ != Type::CHARACTER, this);
      return final_string_value_.at(1);  // unquote '
    } else if constexpr (std::is_same<T, bool>::value) {
      static_assert(std::is_same<T, bool>::value, "..");
      AIDL_FATAL_IF(final_type_ != Type::BOOLEAN, this);
      return final_value_ != 0;
    } else {
      static_assert(unsupported_type<T>::value);
    }
  }

  virtual ~AidlConstantValue() = default;

  // non-copyable, non-movable
  AidlConstantValue(const AidlConstantValue&) = delete;
  AidlConstantValue(AidlConstantValue&&) = delete;
  AidlConstantValue& operator=(const AidlConstantValue&) = delete;
  AidlConstantValue& operator=(AidlConstantValue&&) = delete;

  // creates default value, when one isn't specified
  // nullptr if no default available
  static AidlConstantValue* Default(const AidlTypeSpecifier& specifier);

  static AidlConstantValue* Boolean(const AidlLocation& location, bool value);
  static AidlConstantValue* Character(const AidlLocation& location, const std::string& value);
  // example: 123, -5498, maybe any size
  static AidlConstantValue* Integral(const AidlLocation& location, const std::string& value);
  static AidlConstantValue* Floating(const AidlLocation& location, const std::string& value);
  static AidlConstantValue* Array(const AidlLocation& location,
                                  std::unique_ptr<vector<unique_ptr<AidlConstantValue>>> values);
  // example: "\"asdf\""
  static AidlConstantValue* String(const AidlLocation& location, const string& value);

  Type GetType() const { return final_type_; }
  const std::string& Literal() const { return value_; }

  bool Evaluate() const;
  virtual bool CheckValid() const;

  // Raw value of type (currently valid in C++ and Java). Empty string on error.
  string ValueString(const AidlTypeSpecifier& type, const ConstantValueDecorator& decorator) const;

  void TraverseChildren(std::function<void(const AidlNode&)> traverse) const override {
    if (type_ == Type::ARRAY) {
      for (const auto& v : values_) {
        traverse(*v);
      }
    }
  }
  void DispatchVisit(AidlVisitor& visitor) const override { visitor.Visit(*this); }
  size_t Size() const { return values_.size(); }
  const AidlConstantValue& ValueAt(size_t index) const { return *values_.at(index); }
  static string ToString(Type type);

 private:
  AidlConstantValue(const AidlLocation& location, Type parsed_type, int64_t parsed_value,
                    const string& checked_value);
  AidlConstantValue(const AidlLocation& location, Type type, const string& checked_value);
  AidlConstantValue(const AidlLocation& location, Type type,
                    std::unique_ptr<vector<unique_ptr<AidlConstantValue>>> values,
                    const std::string& value);
  static bool ParseIntegral(const string& value, int64_t* parsed_value, Type* parsed_type);
  static bool IsHex(const string& value);

  virtual bool evaluate() const;
  bool IsLiteral() const;

  const Type type_ = Type::ERROR;
  const vector<unique_ptr<AidlConstantValue>> values_;  // if type_ == ARRAY
  const string value_;                                  // otherwise

  // State for tracking evaluation of expressions
  mutable bool is_valid_ = false;      // cache of CheckValid, but may be marked false in evaluate
  mutable bool is_evaluated_ = false;  // whether evaluate has been called
  mutable Type final_type_;
  mutable int64_t final_value_;
  mutable string final_string_value_ = "";

  friend AidlUnaryConstExpression;
  friend AidlBinaryConstExpression;
  friend AidlConstantReference;
};

// Represents "<type>.<field>" which resolves to a constant which is one of
// - constant declaration
// - enumerator
// When a <type> is missing, <field> is of the enclosing type.
class AidlConstantReference : public AidlConstantValue {
 public:
  AidlConstantReference(const AidlLocation& location, const std::string& value);

  const std::unique_ptr<AidlTypeSpecifier>& GetRefType() const { return ref_type_; }
  const std::string& GetFieldName() const { return field_name_; }

  bool CheckValid() const override;
  void TraverseChildren(std::function<void(const AidlNode&)> traverse) const override {
    if (ref_type_) {
      traverse(*ref_type_);
    }
  }
  void DispatchVisit(AidlVisitor& v) const override { v.Visit(*this); }
  const AidlConstantValue* Resolve(const AidlDefinedType* scope) const;

 private:
  bool evaluate() const override;

  std::unique_ptr<AidlTypeSpecifier> ref_type_;
  std::string field_name_;
  mutable const AidlConstantValue* resolved_ = nullptr;
};

class AidlUnaryConstExpression : public AidlConstantValue {
 public:
  AidlUnaryConstExpression(const AidlLocation& location, const string& op,
                           std::unique_ptr<AidlConstantValue> rval);

  static bool IsCompatibleType(Type type, const string& op);
  bool CheckValid() const override;
  void TraverseChildren(std::function<void(const AidlNode&)> traverse) const override {
    traverse(*unary_);
  }
  void DispatchVisit(AidlVisitor& v) const override { v.Visit(*this); }
  const std::unique_ptr<AidlConstantValue>& Val() const { return unary_; }
  const std::string& Op() const { return op_; }

 private:
  bool evaluate() const override;

  std::unique_ptr<AidlConstantValue> unary_;
  const string op_;
};

class AidlBinaryConstExpression : public AidlConstantValue {
 public:
  AidlBinaryConstExpression(const AidlLocation& location, std::unique_ptr<AidlConstantValue> lval,
                            const string& op, std::unique_ptr<AidlConstantValue> rval);

  bool CheckValid() const override;

  static bool AreCompatibleOperandTypes(Type t1, Type t2);
  static bool AreCompatibleArrayTypes(Type t1, Type t2);

  // Returns the promoted kind for both operands
  static Type UsualArithmeticConversion(Type left, Type right);
  // Returns the promoted integral type where INT32 is the smallest type
  static Type IntegralPromotion(Type in);
  void TraverseChildren(std::function<void(const AidlNode&)> traverse) const override {
    traverse(*left_val_);
    traverse(*right_val_);
  }
  void DispatchVisit(AidlVisitor& v) const override { v.Visit(*this); }
  const std::unique_ptr<AidlConstantValue>& Left() const { return left_val_; }
  const std::unique_ptr<AidlConstantValue>& Right() const { return right_val_; }
  const std::string& Op() const { return op_; }

 private:
  bool evaluate() const override;

  std::unique_ptr<AidlConstantValue> left_val_;
  std::unique_ptr<AidlConstantValue> right_val_;
  const string op_;
};

struct AidlAnnotationParameter {
  std::string name;
  std::unique_ptr<AidlConstantValue> value;
};

class AidlConstantDeclaration : public AidlMember {
 public:
  AidlConstantDeclaration(const AidlLocation& location, AidlTypeSpecifier* specifier,
                          const string& name, AidlConstantValue* value);
  virtual ~AidlConstantDeclaration() = default;

  // non-copyable, non-movable
  AidlConstantDeclaration(const AidlConstantDeclaration&) = delete;
  AidlConstantDeclaration(AidlConstantDeclaration&&) = delete;
  AidlConstantDeclaration& operator=(const AidlConstantDeclaration&) = delete;
  AidlConstantDeclaration& operator=(AidlConstantDeclaration&&) = delete;

  const AidlTypeSpecifier& GetType() const { return *type_; }
  AidlTypeSpecifier* GetMutableType() { return type_.get(); }
  const string& GetName() const { return name_; }
  const AidlConstantValue& GetValue() const { return *value_; }
  bool CheckValid(const AidlTypenames& typenames) const;

  // ToString is for dumping AIDL.
  // Returns string representation of this const decl including a const value.
  // This is "`const` annotations type name value".
  // e.g) "const @utf8InCpp String[] names = { "hello" }"
  string ToString() const;

  // Signature is for comparing types.
  // Returns string representation of this const decl.
  // This is "direction annotations type name".
  // e.g) "String[] names"
  string Signature() const;

  string ValueString(const ConstantValueDecorator& decorator) const {
    return value_->ValueString(GetType(), decorator);
  }

  void TraverseChildren(std::function<void(const AidlNode&)> traverse) const override {
    traverse(GetType());
    traverse(GetValue());
  }
  void DispatchVisit(AidlVisitor& v) const override { v.Visit(*this); }

 private:
  const unique_ptr<AidlTypeSpecifier> type_;
  const string name_;
  unique_ptr<AidlConstantValue> value_;
};

class AidlMethod : public AidlMember {
 public:
  AidlMethod(const AidlLocation& location, bool oneway, AidlTypeSpecifier* type, const string& name,
             vector<unique_ptr<AidlArgument>>* args, const Comments& comments);
  AidlMethod(const AidlLocation& location, bool oneway, AidlTypeSpecifier* type, const string& name,
             vector<unique_ptr<AidlArgument>>* args, const Comments& comments, int id);
  virtual ~AidlMethod() = default;

  // non-copyable, non-movable
  AidlMethod(const AidlMethod&) = delete;
  AidlMethod(AidlMethod&&) = delete;
  AidlMethod& operator=(const AidlMethod&) = delete;
  AidlMethod& operator=(AidlMethod&&) = delete;

  bool CheckValid(const AidlTypenames&) const;
  const AidlTypeSpecifier& GetType() const { return *type_; }
  AidlTypeSpecifier* GetMutableType() { return type_.get(); }

  // set if this method is part of an interface that is marked oneway
  void ApplyInterfaceOneway(bool oneway) { oneway_ = oneway_ || oneway; }
  bool IsOneway() const { return oneway_; }
  bool HasOnewayAnnotation() const { return oneway_annotation_; }

  const std::string& GetName() const { return name_; }
  bool HasId() const { return has_id_; }
  int GetId() const { return id_; }

  // TODO: Set is errorprone, what if it was set before?
  void SetId(unsigned id) { id_ = id; }

  const std::vector<std::unique_ptr<AidlArgument>>& GetArguments() const {
    return arguments_;
  }
  // An inout parameter will appear in both GetInArguments()
  // and GetOutArguments().  AidlMethod retains ownership of the argument
  // pointers returned in this way.
  const std::vector<const AidlArgument*>& GetInArguments() const {
    return in_arguments_;
  }
  const std::vector<const AidlArgument*>& GetOutArguments() const {
    return out_arguments_;
  }

  // ToString is for dumping AIDL.
  // Returns string representation of this method including everything.
  // This is "ret_type name ( arg_list ) = id".
  // e.g) "boolean foo(int, @Nullable String) = 1"
  std::string ToString() const;

  // Signature is for comparing AIDL types.
  // Returns string representation of this method's name & type.
  // e.g) "foo(int, String)"
  std::string Signature() const;

  void TraverseChildren(std::function<void(const AidlNode&)> traverse) const override {
    traverse(GetType());
    for (const auto& a : GetArguments()) {
      traverse(*a);
    }
  }
  void DispatchVisit(AidlVisitor& v) const override { v.Visit(*this); }

 private:
  // oneway_ may be set by the method or the parent interface. If the interface is oneway,
  // is also oneway. oneway_annotation_ may only be set on creation, and may not be overridden
  // by the parent interface. It is used to detect redundant oneway annotations.
  bool oneway_;
  bool oneway_annotation_;
  std::unique_ptr<AidlTypeSpecifier> type_;
  std::string name_;
  const std::vector<std::unique_ptr<AidlArgument>> arguments_;
  std::vector<const AidlArgument*> in_arguments_;
  std::vector<const AidlArgument*> out_arguments_;
  bool has_id_;
  int id_;
};

// AidlDefinedType represents either an interface, parcelable, or enum that is
// defined in the source file.
class AidlDefinedType : public AidlMember, public AidlScope {
 public:
  AidlDefinedType(const AidlLocation& location, const std::string& name, const Comments& comments,
                  const std::string& package, std::vector<std::unique_ptr<AidlMember>>* members);
  virtual ~AidlDefinedType() = default;

  // non-copyable, non-movable
  AidlDefinedType(const AidlDefinedType&) = delete;
  AidlDefinedType(AidlDefinedType&&) = delete;
  AidlDefinedType& operator=(const AidlDefinedType&) = delete;
  AidlDefinedType& operator=(AidlDefinedType&&) = delete;

  const std::string& GetName() const { return name_; };

  std::string ResolveName(const std::string& name) const override;

  /* dot joined package, example: "android.package.foo" */
  std::string GetPackage() const { return package_; }
  /* dot joined package and name, example: "android.package.foo.IBar" */
  std::string GetCanonicalName() const;
  std::vector<std::string> GetSplitPackage() const {
    if (package_.empty()) return std::vector<std::string>();
    return android::base::Split(package_, ".");
  }
  const AidlDocument& GetDocument() const;

  virtual std::string GetPreprocessDeclarationName() const = 0;

  virtual const AidlStructuredParcelable* AsStructuredParcelable() const { return nullptr; }
  virtual const AidlParcelable* AsParcelable() const { return nullptr; }
  virtual const AidlEnumDeclaration* AsEnumDeclaration() const { return nullptr; }
  virtual const AidlUnionDecl* AsUnionDeclaration() const { return nullptr; }
  virtual const AidlInterface* AsInterface() const { return nullptr; }
  virtual const AidlParameterizable<std::string>* AsParameterizable() const { return nullptr; }
  virtual bool CheckValid(const AidlTypenames& typenames) const;
  bool LanguageSpecificCheckValid(Options::Language lang) const;
  AidlStructuredParcelable* AsStructuredParcelable() {
    return const_cast<AidlStructuredParcelable*>(
        const_cast<const AidlDefinedType*>(this)->AsStructuredParcelable());
  }
  AidlParcelable* AsParcelable() {
    return const_cast<AidlParcelable*>(const_cast<const AidlDefinedType*>(this)->AsParcelable());
  }
  AidlEnumDeclaration* AsEnumDeclaration() {
    return const_cast<AidlEnumDeclaration*>(
        const_cast<const AidlDefinedType*>(this)->AsEnumDeclaration());
  }
  AidlUnionDecl* AsUnionDeclaration() {
    return const_cast<AidlUnionDecl*>(
        const_cast<const AidlDefinedType*>(this)->AsUnionDeclaration());
  }
  AidlInterface* AsInterface() {
    return const_cast<AidlInterface*>(const_cast<const AidlDefinedType*>(this)->AsInterface());
  }

  AidlParameterizable<std::string>* AsParameterizable() {
    return const_cast<AidlParameterizable<std::string>*>(
        const_cast<const AidlDefinedType*>(this)->AsParameterizable());
  }

  const AidlParcelable* AsUnstructuredParcelable() const {
    if (this->AsStructuredParcelable() != nullptr) return nullptr;
    if (this->AsUnionDeclaration() != nullptr) return nullptr;
    return this->AsParcelable();
  }
  AidlParcelable* AsUnstructuredParcelable() {
    return const_cast<AidlParcelable*>(
        const_cast<const AidlDefinedType*>(this)->AsUnstructuredParcelable());
  }
  const AidlDefinedType* GetParentType() const;
  const AidlDefinedType* GetRootType() const;
  const std::vector<std::unique_ptr<AidlDefinedType>>& GetNestedTypes() const { return types_; }
  const std::vector<std::unique_ptr<AidlVariableDeclaration>>& GetFields() const {
    return variables_;
  }
  const std::vector<std::unique_ptr<AidlConstantDeclaration>>& GetConstantDeclarations() const {
    return constants_;
  }
  const std::vector<std::unique_ptr<AidlMethod>>& GetMethods() const { return methods_; }
  const std::vector<const AidlMember*>& GetMembers() const { return members_; }
  void TraverseChildren(std::function<void(const AidlNode&)> traverse) const override {
    AidlAnnotatable::TraverseChildren(traverse);
    for (const auto c : GetMembers()) {
      traverse(*c);
    }
  }

  // Modifiers
  void AddMethod(std::unique_ptr<AidlMethod> method) {
    members_.push_back(method.get());
    methods_.push_back(std::move(method));
  }
  void AddType(std::unique_ptr<AidlDefinedType> type) {
    type->SetEnclosingScope(this);
    members_.push_back(type.get());
    types_.push_back(std::move(type));
  }

 protected:
  // utility for subclasses with getter names
  bool CheckValidForGetterNames() const;

 private:
  bool CheckValidWithMembers(const AidlTypenames& typenames) const;

  std::string name_;
  std::string package_;
  std::vector<std::unique_ptr<AidlVariableDeclaration>> variables_;
  std::vector<std::unique_ptr<AidlConstantDeclaration>> constants_;
  std::vector<std::unique_ptr<AidlMethod>> methods_;
  std::vector<std::unique_ptr<AidlDefinedType>> types_;
  std::vector<const AidlMember*> members_;  // keep members in order of appearance.
};

struct AidlUnstructuredHeaders {
  std::string cpp;
  std::string ndk;
  std::string rust_type;
};

class AidlParcelable : public AidlDefinedType, public AidlParameterizable<std::string> {
 public:
  AidlParcelable(const AidlLocation& location, const std::string& name, const std::string& package,
                 const Comments& comments, const AidlUnstructuredHeaders& headers,
                 std::vector<std::string>* type_params,
                 std::vector<std::unique_ptr<AidlMember>>* members = nullptr);
  virtual ~AidlParcelable() = default;

  // non-copyable, non-movable
  AidlParcelable(const AidlParcelable&) = delete;
  AidlParcelable(AidlParcelable&&) = delete;
  AidlParcelable& operator=(const AidlParcelable&) = delete;
  AidlParcelable& operator=(AidlParcelable&&) = delete;

  std::string GetCppHeader() const { return headers_.cpp; }
  std::string GetNdkHeader() const { return headers_.ndk; }
  std::string GetRustType() const { return headers_.rust_type; }

  bool CheckValid(const AidlTypenames& typenames) const override;
  const AidlParcelable* AsParcelable() const override { return this; }
  const AidlParameterizable<std::string>* AsParameterizable() const override { return this; }
  const AidlNode& AsAidlNode() const override { return *this; }
  std::string GetPreprocessDeclarationName() const override { return "parcelable"; }

  void DispatchVisit(AidlVisitor& v) const override { v.Visit(*this); }

 private:
  AidlUnstructuredHeaders headers_;
};

class AidlStructuredParcelable : public AidlParcelable {
 public:
  AidlStructuredParcelable(const AidlLocation& location, const std::string& name,
                           const std::string& package, const Comments& comments,
                           std::vector<std::string>* type_params,
                           std::vector<std::unique_ptr<AidlMember>>* members);
  virtual ~AidlStructuredParcelable() = default;

  // non-copyable, non-movable
  AidlStructuredParcelable(const AidlStructuredParcelable&) = delete;
  AidlStructuredParcelable(AidlStructuredParcelable&&) = delete;
  AidlStructuredParcelable& operator=(const AidlStructuredParcelable&) = delete;
  AidlStructuredParcelable& operator=(AidlStructuredParcelable&&) = delete;

  const AidlStructuredParcelable* AsStructuredParcelable() const override { return this; }
  std::string GetPreprocessDeclarationName() const override { return "structured_parcelable"; }

  bool CheckValid(const AidlTypenames& typenames) const override;
  void DispatchVisit(AidlVisitor& v) const override { v.Visit(*this); }
};

class AidlEnumerator : public AidlCommentable {
 public:
  AidlEnumerator(const AidlLocation& location, const std::string& name, AidlConstantValue* value,
                 const Comments& comments);
  virtual ~AidlEnumerator() = default;

  // non-copyable, non-movable
  AidlEnumerator(const AidlEnumerator&) = delete;
  AidlEnumerator(AidlEnumerator&&) = delete;
  AidlEnumerator& operator=(const AidlEnumerator&) = delete;
  AidlEnumerator& operator=(AidlEnumerator&&) = delete;

  const std::string& GetName() const { return name_; }
  AidlConstantValue* GetValue() const { return value_.get(); }
  bool CheckValid(const AidlTypeSpecifier& enum_backing_type) const;

  string ValueString(const AidlTypeSpecifier& backing_type,
                     const ConstantValueDecorator& decorator) const;

  // TODO: Set is errorprone. What if it was set before?
  void SetValue(std::unique_ptr<AidlConstantValue> value) { value_ = std::move(value); }
  bool IsValueUserSpecified() const { return value_user_specified_; }

  void TraverseChildren(std::function<void(const AidlNode&)> traverse) const override {
    traverse(*value_);
  }
  void DispatchVisit(AidlVisitor& v) const override { v.Visit(*this); }

 private:
  const std::string name_;
  unique_ptr<AidlConstantValue> value_;
  const bool value_user_specified_;
};

class AidlEnumDeclaration : public AidlDefinedType {
 public:
  AidlEnumDeclaration(const AidlLocation& location, const string& name,
                      std::vector<std::unique_ptr<AidlEnumerator>>* enumerators,
                      const std::string& package, const Comments& comments);
  virtual ~AidlEnumDeclaration() = default;

  // non-copyable, non-movable
  AidlEnumDeclaration(const AidlEnumDeclaration&) = delete;
  AidlEnumDeclaration(AidlEnumDeclaration&&) = delete;
  AidlEnumDeclaration& operator=(const AidlEnumDeclaration&) = delete;
  AidlEnumDeclaration& operator=(AidlEnumDeclaration&&) = delete;

  bool Autofill(const AidlTypenames&);
  const AidlTypeSpecifier& GetBackingType() const { return *backing_type_; }
  const std::vector<std::unique_ptr<AidlEnumerator>>& GetEnumerators() const {
    return enumerators_;
  }
  bool CheckValid(const AidlTypenames& typenames) const override;
  std::string GetPreprocessDeclarationName() const override { return "enum"; }

  const AidlEnumDeclaration* AsEnumDeclaration() const override { return this; }

  void TraverseChildren(std::function<void(const AidlNode&)> traverse) const override {
    AidlDefinedType::TraverseChildren(traverse);
    if (backing_type_) {
      traverse(*backing_type_);
    }
    for (const auto& c : GetEnumerators()) {
      traverse(*c);
    }
  }
  void DispatchVisit(AidlVisitor& v) const override { v.Visit(*this); }

 private:

  const std::string name_;
  const std::vector<std::unique_ptr<AidlEnumerator>> enumerators_;
  std::unique_ptr<AidlTypeSpecifier> backing_type_;
};

class AidlUnionDecl : public AidlParcelable {
 public:
  AidlUnionDecl(const AidlLocation& location, const std::string& name, const std::string& package,
                const Comments& comments, std::vector<std::string>* type_params,
                std::vector<std::unique_ptr<AidlMember>>* members);
  virtual ~AidlUnionDecl() = default;

  // non-copyable, non-movable
  AidlUnionDecl(const AidlUnionDecl&) = delete;
  AidlUnionDecl(AidlUnionDecl&&) = delete;
  AidlUnionDecl& operator=(const AidlUnionDecl&) = delete;
  AidlUnionDecl& operator=(AidlUnionDecl&&) = delete;


  const AidlNode& AsAidlNode() const override { return *this; }
  bool CheckValid(const AidlTypenames& typenames) const override;
  std::string GetPreprocessDeclarationName() const override { return "union"; }

  const AidlUnionDecl* AsUnionDeclaration() const override { return this; }
  void DispatchVisit(AidlVisitor& v) const override { v.Visit(*this); }
};

class AidlInterface final : public AidlDefinedType {
 public:
  AidlInterface(const AidlLocation& location, const std::string& name, const Comments& comments,
                bool oneway, const std::string& package,
                std::vector<std::unique_ptr<AidlMember>>* members);
  virtual ~AidlInterface() = default;

  // non-copyable, non-movable
  AidlInterface(const AidlInterface&) = delete;
  AidlInterface(AidlInterface&&) = delete;
  AidlInterface& operator=(const AidlInterface&) = delete;
  AidlInterface& operator=(AidlInterface&&) = delete;

  const AidlInterface* AsInterface() const override { return this; }
  std::string GetPreprocessDeclarationName() const override { return "interface"; }

  bool CheckValid(const AidlTypenames& typenames) const override;
  bool CheckValidPermissionAnnotations(const AidlMethod& m) const;
  bool UsesPermissions() const;
  std::string GetDescriptor() const;
  void DispatchVisit(AidlVisitor& v) const override { v.Visit(*this); }
  bool HasOnewayAnnotation() const { return oneway_annotation_; }

 private:
  bool oneway_annotation_;
};

inline std::string SimpleName(const std::string& qualified_name) {
  return qualified_name.substr(qualified_name.rfind('.') + 1);
}

// AidlDocument models an AIDL file
class AidlDocument : public AidlCommentable, public AidlScope {
 public:
  AidlDocument(const AidlLocation& location, const Comments& comments,
               std::vector<std::string> imports,
               std::vector<std::unique_ptr<AidlDefinedType>> defined_types, bool is_preprocessed);
  ~AidlDocument() = default;

  // non-copyable, non-movable
  AidlDocument(const AidlDocument&) = delete;
  AidlDocument(AidlDocument&&) = delete;
  AidlDocument& operator=(const AidlDocument&) = delete;
  AidlDocument& operator=(AidlDocument&&) = delete;

  std::string ResolveName(const std::string& name) const override;
  const std::vector<std::string>& Imports() const { return imports_; }
  const std::vector<std::unique_ptr<AidlDefinedType>>& DefinedTypes() const {
    return defined_types_;
  }
  bool IsPreprocessed() const { return is_preprocessed_; }

  void TraverseChildren(std::function<void(const AidlNode&)> traverse) const override {
    for (const auto& t : DefinedTypes()) {
      traverse(*t);
    }
  }
  void DispatchVisit(AidlVisitor& v) const override { v.Visit(*this); }

 private:
  const std::vector<std::string> imports_;
  const std::vector<std::unique_ptr<AidlDefinedType>> defined_types_;
  bool is_preprocessed_;
};

template <typename T>
std::optional<T> AidlAnnotation::ParamValue(const std::string& param_name) const {
  auto it = parameters_.find(param_name);
  if (it == parameters_.end()) {
    return std::nullopt;
  }
  return it->second->EvaluatedValue<T>();
}

// Utilities to make a visitor to visit AST tree in top-down order
// Given:       foo
//              / \
//            bar baz
// VisitTopDown(v, foo) makes v visit foo -> bar -> baz.
inline void VisitTopDown(std::function<void(const AidlNode&)> v, const AidlNode& node) {
  std::function<void(const AidlNode&)> top_down = [&](const AidlNode& n) {
    v(n);
    n.TraverseChildren(top_down);
  };
  top_down(node);
}
inline void VisitTopDown(AidlVisitor& v, const AidlNode& node) {
  VisitTopDown([&](const AidlNode& n) { n.DispatchVisit(v); }, node);
}

// Utility to make a visitor to visit AST tree in bottom-up order
// Given:       foo
//              / \
//            bar baz
// VisitBottomUp(v, foo) makes v visit bar -> baz -> foo.
inline void VisitBottomUp(AidlVisitor& v, const AidlNode& node) {
  std::function<void(const AidlNode&)> bottom_up = [&](const AidlNode& n) {
    n.TraverseChildren(bottom_up);
    n.DispatchVisit(v);
  };
  bottom_up(node);
}

template <typename T>
const T* AidlCast(const AidlNode& node) {
  struct CastVisitor : AidlVisitor {
    const T* cast = nullptr;
    void Visit(const T& t) override { cast = &t; }
  } visitor;
  node.DispatchVisit(visitor);
  return visitor.cast;
}

template <>
const AidlDefinedType* AidlCast<AidlDefinedType>(const AidlNode& node);

template <typename T>
T* AidlCast(AidlNode& node) {
  return const_cast<T*>(AidlCast<T>(const_cast<const AidlNode&>(node)));
}

template <typename AidlNodeType>
vector<const AidlNodeType*> Collect(const AidlNode& root) {
  vector<const AidlNodeType*> result;
  std::function<void(const AidlNode&)> top_down = [&](const AidlNode& n) {
    if (auto cast = AidlCast<AidlNodeType>(n); cast) {
      result.push_back(cast);
    }
    n.TraverseChildren(top_down);
  };
  top_down(root);
  return result;
}

template <typename VisitFn>
bool TopologicalVisit(const vector<unique_ptr<AidlDefinedType>>& nested_types, VisitFn visit) {
  // 1. Maps deeply nested types to one of nested_types
  map<const AidlDefinedType*, const AidlDefinedType*> roots;
  for (const auto& nested : nested_types) {
    for (const auto& t : Collect<AidlDefinedType>(*nested)) {
      roots[t] = nested.get();
    }
  }
  // 2. Collect sibling types referenced within each nested type.
  map<const AidlDefinedType*, vector<const AidlDefinedType*>> required_types;
  for (const auto& nested : nested_types) {
    for (const auto& t : Collect<AidlTypeSpecifier>(*nested)) {
      if (auto defined_type = t->GetDefinedType(); defined_type) {
        auto sibling = roots[defined_type];
        if (sibling && sibling != nested.get()) {
          required_types[nested.get()].push_back(sibling);
        }
      }
    }
  };
  // 3. Run DFS
  enum { NOT_STARTED = 0, STARTED = 1, FINISHED = 2 };
  map<const AidlDefinedType*, int> visited;
  std::function<bool(const AidlDefinedType&)> dfs = [&](const AidlDefinedType& type) {
    if (visited[&type] == FINISHED) {
      return true;
    } else if (visited[&type] == STARTED) {
      return false;
    } else {
      visited[&type] = STARTED;
      // Visit every required dep first
      for (const auto& dep_type : required_types[&type]) {
        if (!dfs(*dep_type)) {
          return false;
        }
      }
      visited[&type] = FINISHED;
      visit(type);
      return true;
    }
  };

  for (const auto& type : nested_types) {
    if (!dfs(*type)) {
      return false;
    }
  }

  return true;
}
