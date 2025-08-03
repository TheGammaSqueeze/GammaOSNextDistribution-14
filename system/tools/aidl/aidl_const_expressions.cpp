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

#include "aidl_language.h"
#include "aidl_typenames.h"
#include "logging.h"

#include <stdlib.h>
#include <algorithm>
#include <iostream>
#include <limits>
#include <memory>

#include <android-base/parsedouble.h>
#include <android-base/parseint.h>
#include <android-base/strings.h>

using android::base::ConsumeSuffix;
using android::base::EndsWith;
using android::base::Join;
using android::base::Split;
using android::base::StartsWith;
using std::string;
using std::unique_ptr;
using std::vector;

template <typename T>
constexpr int CLZ(T x) {
  // __builtin_clz(0) is undefined
  if (x == 0) return sizeof(T) * 8;
  return (sizeof(T) == sizeof(uint64_t)) ? __builtin_clzl(x) : __builtin_clz(x);
}

template <typename T>
class OverflowGuard {
 public:
  OverflowGuard(T value) : mValue(value) {}
  bool Overflowed() const { return mOverflowed; }

  T operator+() { return +mValue; }
  T operator-() {
    if (isMin()) {
      mOverflowed = true;
      return 0;
    }
    return -mValue;
  }
  T operator!() { return !mValue; }
  T operator~() { return ~mValue; }

  T operator+(T o) {
    T out;
    mOverflowed = __builtin_add_overflow(mValue, o, &out);
    return out;
  }
  T operator-(T o) {
    T out;
    mOverflowed = __builtin_sub_overflow(mValue, o, &out);
    return out;
  }
  T operator*(T o) {
    T out;
#ifdef _WIN32
    // ___mulodi4 not on windows https://bugs.llvm.org/show_bug.cgi?id=46669
    // we should still get an error here from ubsan, but the nice error
    // is needed on linux for aidl_parser_fuzzer, where we are more
    // concerned about overflows elsewhere in the compiler in addition to
    // those in interfaces.
    out = mValue * o;
#else
    mOverflowed = __builtin_mul_overflow(mValue, o, &out);
#endif
    return out;
  }
  T operator/(T o) {
    if (o == 0 || (isMin() && o == -1)) {
      mOverflowed = true;
      return 0;
    }
    return static_cast<T>(mValue / o);
  }
  T operator%(T o) {
    if (o == 0 || (isMin() && o == -1)) {
      mOverflowed = true;
      return 0;
    }
    return static_cast<T>(mValue % o);
  }
  T operator|(T o) { return mValue | o; }
  T operator^(T o) { return mValue ^ o; }
  T operator&(T o) { return mValue & o; }
  T operator<(T o) { return mValue < o; }
  T operator>(T o) { return mValue > o; }
  T operator<=(T o) { return mValue <= o; }
  T operator>=(T o) { return mValue >= o; }
  T operator==(T o) { return mValue == o; }
  T operator!=(T o) { return mValue != o; }
  T operator>>(T o) {
    if (o < 0 || o >= static_cast<T>(sizeof(T) * 8) || mValue < 0) {
      mOverflowed = true;
      return 0;
    }
    return static_cast<T>(mValue >> o);
  }
  T operator<<(T o) {
    if (o < 0 || mValue < 0 || o > CLZ(mValue) || o >= static_cast<T>(sizeof(T) * 8)) {
      mOverflowed = true;
      return 0;
    }
    return static_cast<T>(mValue << o);
  }
  T operator||(T o) { return mValue || o; }
  T operator&&(T o) { return mValue && o; }

 private:
  bool isMin() { return mValue == std::numeric_limits<T>::min(); }

  T mValue;
  bool mOverflowed = false;
};

// some compilers don't provide __builtin_add_overflow for bool
// see https://gcc.gnu.org/bugzilla/show_bug.cgi?id=71479
template <>
bool OverflowGuard<bool>::operator+(bool o) {
  if (mValue && o) {
    mOverflowed = true;
    return false;
  }
  return mValue || o;
}

template <>
bool OverflowGuard<bool>::operator-(bool o) {
  if (!mValue && o) {
    mOverflowed = true;
    return true;
  }
  return mValue && !o;
}

template <>
bool OverflowGuard<bool>::operator*(bool o) {
  return mValue && o;
}

template <typename T>
bool processGuard(const OverflowGuard<T>& guard, const AidlConstantValue& context) {
  if (guard.Overflowed()) {
    AIDL_ERROR(context) << "Constant expression computation overflows.";
    return false;
  }
  return true;
}

// TODO: factor out all these macros
#define SHOULD_NOT_REACH() AIDL_FATAL(AIDL_LOCATION_HERE) << "Should not reach."
#define OPEQ(__y__) (string(op_) == string(__y__))
#define COMPUTE_UNARY(T, __op__)         \
  if (op == string(#__op__)) {           \
    OverflowGuard<T> guard(val);         \
    *out = __op__ guard;                 \
    return processGuard(guard, context); \
  }
#define COMPUTE_BINARY(T, __op__)        \
  if (op == string(#__op__)) {           \
    OverflowGuard<T> guard(lval);        \
    *out = guard __op__ rval;            \
    return processGuard(guard, context); \
  }
#define OP_IS_BIN_ARITHMETIC (OPEQ("+") || OPEQ("-") || OPEQ("*") || OPEQ("/") || OPEQ("%"))
#define OP_IS_BIN_BITFLIP (OPEQ("|") || OPEQ("^") || OPEQ("&"))
#define OP_IS_BIN_COMP \
  (OPEQ("<") || OPEQ(">") || OPEQ("<=") || OPEQ(">=") || OPEQ("==") || OPEQ("!="))
#define OP_IS_BIN_SHIFT (OPEQ(">>") || OPEQ("<<"))
#define OP_IS_BIN_LOGICAL (OPEQ("||") || OPEQ("&&"))

// NOLINT to suppress missing parentheses warnings about __def__.
#define SWITCH_KIND(__cond__, __action__, __def__) \
  switch (__cond__) {                              \
    case Type::BOOLEAN:                            \
      __action__(bool);                            \
    case Type::INT8:                               \
      __action__(int8_t);                          \
    case Type::INT32:                              \
      __action__(int32_t);                         \
    case Type::INT64:                              \
      __action__(int64_t);                         \
    default:                                       \
      __def__; /* NOLINT */                        \
  }

template <class T>
bool handleUnary(const AidlConstantValue& context, const string& op, T val, int64_t* out) {
  COMPUTE_UNARY(T, +)
  COMPUTE_UNARY(T, -)
  COMPUTE_UNARY(T, !)
  COMPUTE_UNARY(T, ~)
  AIDL_FATAL(context) << "Could not handleUnary for " << op << " " << val;
  return false;
}
template <>
bool handleUnary<bool>(const AidlConstantValue& context, const string& op, bool val, int64_t* out) {
  COMPUTE_UNARY(bool, +)
  COMPUTE_UNARY(bool, -)
  COMPUTE_UNARY(bool, !)

  if (op == "~") {
    AIDL_ERROR(context) << "Bitwise negation of a boolean expression is always true.";
    return false;
  }
  AIDL_FATAL(context) << "Could not handleUnary for " << op << " " << val;
  return false;
}

template <class T>
bool handleBinaryCommon(const AidlConstantValue& context, T lval, const string& op, T rval,
                        int64_t* out) {
  COMPUTE_BINARY(T, +)
  COMPUTE_BINARY(T, -)
  COMPUTE_BINARY(T, *)
  COMPUTE_BINARY(T, /)
  COMPUTE_BINARY(T, %)
  COMPUTE_BINARY(T, |)
  COMPUTE_BINARY(T, ^)
  COMPUTE_BINARY(T, &)
  // comparison operators: return 0 or 1 by nature.
  COMPUTE_BINARY(T, ==)
  COMPUTE_BINARY(T, !=)
  COMPUTE_BINARY(T, <)
  COMPUTE_BINARY(T, >)
  COMPUTE_BINARY(T, <=)
  COMPUTE_BINARY(T, >=)

  AIDL_FATAL(context) << "Could not handleBinaryCommon for " << lval << " " << op << " " << rval;
  return false;
}

template <class T>
bool handleShift(const AidlConstantValue& context, T lval, const string& op, T rval, int64_t* out) {
  // just cast rval to int64_t and it should fit.
  COMPUTE_BINARY(T, >>)
  COMPUTE_BINARY(T, <<)

  AIDL_FATAL(context) << "Could not handleShift for " << lval << " " << op << " " << rval;
  return false;
}

bool handleLogical(const AidlConstantValue& context, bool lval, const string& op, bool rval,
                   int64_t* out) {
  COMPUTE_BINARY(bool, ||);
  COMPUTE_BINARY(bool, &&);

  AIDL_FATAL(context) << "Could not handleLogical for " << lval << " " << op << " " << rval;
  return false;
}

static bool isValidLiteralChar(char c) {
  return !(c <= 0x1f ||  // control characters are < 0x20
           c >= 0x7f ||  // DEL is 0x7f
           c == '\\');   // Disallow backslashes for future proofing.
}

static std::string PrintCharLiteral(char c) {
  std::ostringstream os;
  switch (c) {
    case '\0':
      os << "\\0";
      break;
    case '\'':
      os << "\\'";
      break;
    case '\\':
      os << "\\\\";
      break;
    case '\a':
      os << "\\a";
      break;
    case '\b':
      os << "\\b";
      break;
    case '\f':
      os << "\\f";
      break;
    case '\n':
      os << "\\n";
      break;
    case '\r':
      os << "\\r";
      break;
    case '\t':
      os << "\\t";
      break;
    case '\v':
      os << "\\v";
      break;
    default:
      if (std::isprint(static_cast<unsigned char>(c))) {
        os << c;
      } else {
        os << "\\x" << std::hex << std::uppercase << static_cast<int>(c);
      }
  }
  return os.str();
}

bool ParseFloating(std::string_view sv, double* parsed) {
  // float literal should be parsed successfully.
  android::base::ConsumeSuffix(&sv, "f");
  return android::base::ParseDouble(std::string(sv).data(), parsed);
}

bool ParseFloating(std::string_view sv, float* parsed) {
  // we only care about float literal (with suffix "f").
  if (!android::base::ConsumeSuffix(&sv, "f")) {
    return false;
  }
  return android::base::ParseFloat(std::string(sv).data(), parsed);
}

bool AidlUnaryConstExpression::IsCompatibleType(Type type, const string& op) {
  // Verify the unary type here
  switch (type) {
    case Type::BOOLEAN:  // fall-through
    case Type::INT8:     // fall-through
    case Type::INT32:    // fall-through
    case Type::INT64:
      return true;
    case Type::FLOATING:
      return (op == "+" || op == "-");
    default:
      return false;
  }
}

bool AidlBinaryConstExpression::AreCompatibleOperandTypes(Type t1, Type t2) {
  switch (t1) {
    case Type::ARRAY:
      if (t2 == Type::ARRAY) {
        return true;
      }
      break;
    case Type::STRING:
      if (t2 == Type::STRING) {
        return true;
      }
      break;
    case Type::FLOATING:
      // TODO: b/313951203, check op for supported floating operands (+ - * / < > <= >= == !=)
      return false;
    case Type::BOOLEAN:  // fall-through
    case Type::INT8:     // fall-through
    case Type::INT32:    // fall-through
    case Type::INT64:
      switch (t2) {
        case Type::BOOLEAN:  // fall-through
        case Type::INT8:     // fall-through
        case Type::INT32:    // fall-through
        case Type::INT64:
          return true;
          break;
        default:
          break;
      }
      break;
    default:
      break;
  }

  return false;
}

bool AidlBinaryConstExpression::AreCompatibleArrayTypes(Type t1, Type t2) {
  // treat floating type differently here, because float array is supported but not operand
  if (t1 == Type::FLOATING && t2 == Type::FLOATING) return true;

  return AreCompatibleOperandTypes(t1, t2);
}

// Returns the promoted kind for both operands
AidlConstantValue::Type AidlBinaryConstExpression::UsualArithmeticConversion(Type left,
                                                                             Type right) {
  // These are handled as special cases
  // TODO: b/313951203, remove this after support string and floating operands
  AIDL_FATAL_IF(left == Type::STRING || right == Type::STRING, AIDL_LOCATION_HERE);
  AIDL_FATAL_IF(left == Type::FLOATING || right == Type::FLOATING, AIDL_LOCATION_HERE);

  // Kinds in concern: bool, (u)int[8|32|64]
  if (left == right) return left;  // easy case
  if (left == Type::BOOLEAN) return right;
  if (right == Type::BOOLEAN) return left;

  return left < right ? right : left;
}

// Returns the promoted integral type where INT32 is the smallest type
AidlConstantValue::Type AidlBinaryConstExpression::IntegralPromotion(Type in) {
  return (Type::INT32 < in) ? in : Type::INT32;
}

AidlConstantValue* AidlConstantValue::Default(const AidlTypeSpecifier& specifier) {
  AidlLocation location = specifier.GetLocation();

  // allocation of int[0] is a bit wasteful in Java
  if (specifier.IsArray()) {
    return nullptr;
  }

  const std::string name = specifier.GetName();
  if (name == "boolean") {
    return Boolean(location, false);
  }
  if (name == "char") {
    return Character(location, "'\\0'");  // literal to be used in backends
  }
  if (name == "byte" || name == "int" || name == "long") {
    return Integral(location, "0");
  }
  if (name == "float") {
    return Floating(location, "0.0f");
  }
  if (name == "double") {
    return Floating(location, "0.0");
  }
  return nullptr;
}

AidlConstantValue* AidlConstantValue::Boolean(const AidlLocation& location, bool value) {
  return new AidlConstantValue(location, Type::BOOLEAN, value ? "true" : "false");
}

AidlConstantValue* AidlConstantValue::Character(const AidlLocation& location,
                                                const std::string& value) {
  static const char* kZeroString = "'\\0'";

  // We should have better supports for escapes in the future, but for now
  // allow only what is needed for defaults.
  if (value != kZeroString) {
    AIDL_FATAL_IF(value.size() != 3 || value[0] != '\'' || value[2] != '\'', location) << value;

    if (!isValidLiteralChar(value[1])) {
      AIDL_ERROR(location) << "Invalid character literal " << PrintCharLiteral(value[1]);
      return new AidlConstantValue(location, Type::ERROR, value);
    }
  }

  return new AidlConstantValue(location, Type::CHARACTER, value);
}

AidlConstantValue* AidlConstantValue::Floating(const AidlLocation& location,
                                               const std::string& value) {
  return new AidlConstantValue(location, Type::FLOATING, value);
}

bool AidlConstantValue::IsHex(const string& value) {
  return StartsWith(value, "0x") || StartsWith(value, "0X");
}

bool AidlConstantValue::ParseIntegral(const string& value, int64_t* parsed_value,
                                      Type* parsed_type) {
  if (parsed_value == nullptr || parsed_type == nullptr) {
    return false;
  }

  std::string_view value_view = value;
  const bool is_byte = ConsumeSuffix(&value_view, "u8");
  const bool is_unsigned_int = ConsumeSuffix(&value_view, "u32");
  const bool is_long = ConsumeSuffix(&value_view, "l") || ConsumeSuffix(&value_view, "L");
  const bool is_unsigned_long = ConsumeSuffix(&value_view, "u64");

  if (is_byte + is_long + is_unsigned_int + is_unsigned_long > 1) return false;

  const std::string value_substr = ({
    std::string raw_value_substr = std::string(value_view);
    // remove "_" in integral constant
    const std::vector<std::string> value_pieces = Split(raw_value_substr, "_");
    if (std::any_of(value_pieces.begin(), value_pieces.end(),
                    [](const auto& s) { return s.empty(); })) {
      return false;
    }
    Join(value_pieces, "");
  });

  *parsed_value = 0;
  *parsed_type = Type::ERROR;

  if (IsHex(value)) {
    // AIDL considers 'const int foo = 0xffffffff' as -1, but if we want to
    // handle that when computing constant expressions, then we need to
    // represent 0xffffffff as a uint32_t. However, AIDL only has signed types;
    // so we parse as an unsigned int when possible and then cast to a signed
    // int. One example of this is in ICameraService.aidl where a constant int
    // is used for bit manipulations which ideally should be handled with an
    // unsigned int.
    //
    // Note, for historical consistency, we need to consider small hex values
    // as an integral type. Recognizing them as INT8 could break some files,
    // even though it would simplify this code.
    if (is_byte) {
      uint8_t raw_value8;
      if (!android::base::ParseUint<uint8_t>(value_substr, &raw_value8)) {
        return false;
      }
      *parsed_value = static_cast<int8_t>(raw_value8);
      *parsed_type = Type::INT8;
    } else if (uint32_t raw_value32;
               (!is_long || is_unsigned_int) &&
               android::base::ParseUint<uint32_t>(value_substr, &raw_value32)) {
      *parsed_value = static_cast<int32_t>(raw_value32);
      *parsed_type = Type::INT32;
    } else if (uint64_t raw_value64;
               android::base::ParseUint<uint64_t>(value_substr, &raw_value64)) {
      *parsed_value = static_cast<int64_t>(raw_value64);
      *parsed_type = Type::INT64;
    } else {
      return false;
    }
    return true;
  }

  if (is_unsigned_long) {
    if (uint64_t raw_value64; android::base::ParseUint<uint64_t>(value_substr, &raw_value64)) {
      *parsed_value = static_cast<int64_t>(raw_value64);
      *parsed_type = Type::INT64;
      return true;
    } else {
      return false;
    }
  }
  if (!android::base::ParseInt<int64_t>(value_substr, parsed_value)) {
    return false;
  }

  if (is_byte) {
    if (*parsed_value > UINT8_MAX || *parsed_value < 0) {
      return false;
    }
    *parsed_value = static_cast<int8_t>(*parsed_value);
    *parsed_type = Type::INT8;
  } else if (is_unsigned_int) {
    if (*parsed_value > UINT32_MAX || *parsed_value < 0) {
      return false;
    }
    *parsed_value = static_cast<int32_t>(*parsed_value);
    *parsed_type = Type::INT32;
  } else if (is_long) {
    *parsed_type = Type::INT64;
  } else {
    // guess literal type.
    if (*parsed_value <= INT8_MAX && *parsed_value >= INT8_MIN) {
      *parsed_type = Type::INT8;
    } else if (*parsed_value <= INT32_MAX && *parsed_value >= INT32_MIN) {
      *parsed_type = Type::INT32;
    } else {
      *parsed_type = Type::INT64;
    }
  }
  return true;
}

AidlConstantValue* AidlConstantValue::Integral(const AidlLocation& location, const string& value) {
  AIDL_FATAL_IF(value.empty(), location);

  Type parsed_type;
  int64_t parsed_value = 0;
  bool success = ParseIntegral(value, &parsed_value, &parsed_type);
  if (!success) {
    return nullptr;
  }

  return new AidlConstantValue(location, parsed_type, parsed_value, value);
}

AidlConstantValue* AidlConstantValue::Array(
    const AidlLocation& location, std::unique_ptr<vector<unique_ptr<AidlConstantValue>>> values) {
  AIDL_FATAL_IF(values == nullptr, location);
  // Reconstruct literal value
  std::vector<std::string> str_values;
  for (const auto& v : *values) {
    str_values.push_back(v->value_);
  }
  return new AidlConstantValue(location, Type::ARRAY, std::move(values),
                               "{" + Join(str_values, ", ") + "}");
}

AidlConstantValue* AidlConstantValue::String(const AidlLocation& location, const string& value) {
  AIDL_FATAL_IF(value.size() == 0, "If this is unquoted, we need to update the index log");
  AIDL_FATAL_IF(value[0] != '\"', "If this is unquoted, we need to update the index log");

  for (size_t i = 0; i < value.length(); ++i) {
    if (!isValidLiteralChar(value[i])) {
      AIDL_ERROR(location) << "Found invalid character '" << value[i] << "' at index " << i - 1
                           << " in string constant '" << value << "'";
      return new AidlConstantValue(location, Type::ERROR, value);
    }
  }

  return new AidlConstantValue(location, Type::STRING, value);
}

string AidlConstantValue::ValueString(const AidlTypeSpecifier& type,
                                      const ConstantValueDecorator& decorator) const {
  if (type.IsGeneric()) {
    AIDL_ERROR(type) << "Generic type cannot be specified with a constant literal.";
    return "";
  }
  if (!is_evaluated_) {
    // TODO(b/142722772) CheckValid() should be called before ValueString()
    bool success = CheckValid();
    success &= evaluate();
    if (!success) {
      // the detailed error message shall be printed in evaluate
      return "";
    }
  }
  if (!is_valid_) {
    AIDL_ERROR(this) << "Invalid constant value: " + value_;
    return "";
  }

  std::vector<std::string> alternatives;

  const AidlDefinedType* defined_type = type.GetDefinedType();
  if (defined_type && final_type_ != Type::ARRAY) {
    const AidlEnumDeclaration* enum_type = defined_type->AsEnumDeclaration();
    if (!enum_type) {
      AIDL_ERROR(this) << "Invalid type (" << defined_type->GetCanonicalName()
                       << ") for a const value (" << value_ << ")";
      return "";
    }
    if (type_ != Type::REF) {
      AIDL_ERROR(this) << "Invalid value (" << value_ << ") for enum "
                       << enum_type->GetCanonicalName();
      return "";
    }
    return decorator(type, value_);
  }

  const string& type_string = type.Signature();
  int err = 0;

  switch (final_type_) {
    case Type::CHARACTER:
      if (type_string == "char") {
        return decorator(type, final_string_value_);
      }
      err = -1;
      break;
    case Type::STRING:
      if (type_string == "String") {
        return decorator(type, final_string_value_);
      }
      err = -1;
      break;
    case Type::BOOLEAN:  // fall-through
    case Type::INT8:     // fall-through
    case Type::INT32:    // fall-through
    case Type::INT64:
      if (type_string == "byte") {
        if (final_value_ > INT8_MAX || final_value_ < INT8_MIN) {
          err = -1;

          if (final_value_ >= 0 && final_value_ <= UINT8_MAX && IsLiteral()) {
            alternatives.push_back(value_ + "u8");
          }
          break;
        }
        return decorator(type, std::to_string(static_cast<int8_t>(final_value_)));
      } else if (type_string == "int") {
        if (final_value_ > INT32_MAX || final_value_ < INT32_MIN) {
          err = -1;
          if (final_value_ >= 0 && final_value_ <= UINT32_MAX && IsLiteral()) {
            alternatives.push_back(value_ + "u32");
          }
          break;
        }
        return decorator(type, std::to_string(static_cast<int32_t>(final_value_)));
      } else if (type_string == "long") {
        return decorator(type, std::to_string(final_value_));
      } else if (type_string == "boolean") {
        return decorator(type, final_value_ ? "true" : "false");
      }
      err = -1;
      break;
    case Type::ARRAY: {
      if (!type.IsArray()) {
        err = -1;
        break;
      }
      vector<string> value_strings;
      value_strings.reserve(values_.size());
      bool success = true;

      for (const auto& value : values_) {
        string value_string;
        type.ViewAsArrayBase([&](const auto& base_type) {
          value_string = value->ValueString(base_type, decorator);
        });
        if (value_string.empty()) {
          success = false;
          break;
        }
        value_strings.push_back(value_string);
      }
      if (!success) {
        err = -1;
        break;
      }
      if (type.IsFixedSizeArray()) {
        auto size =
            std::get<FixedSizeArray>(type.GetArray()).dimensions.front()->EvaluatedValue<int32_t>();
        if (values_.size() != static_cast<size_t>(size)) {
          AIDL_ERROR(this) << "Expected an array of " << size << " elements, but found one with "
                           << values_.size() << " elements";
          err = -1;
          break;
        }
      }
      return decorator(type, value_strings);
    }
    case Type::FLOATING: {
      if (type_string == "double") {
        double parsed_value;
        if (!ParseFloating(value_, &parsed_value)) {
          AIDL_ERROR(this) << "Could not parse " << value_;
          err = -1;
          break;
        }
        return decorator(type, std::to_string(parsed_value));
      }
      if (type_string == "float") {
        float parsed_value;
        if (!ParseFloating(value_, &parsed_value)) {
          AIDL_ERROR(this) << "Could not parse " << value_;
          err = -1;
          break;
        }
        return decorator(type, std::to_string(parsed_value) + "f");
      }
      err = -1;
      break;
    }
    default:
      err = -1;
      break;
  }

  std::string alternative_help;
  if (!alternatives.empty()) {
    alternative_help = " Did you mean: " + Join(alternatives, ", ") + "?";
  }

  AIDL_FATAL_IF(err == 0, this);
  AIDL_ERROR(this) << "Invalid type specifier for " << ToString(final_type_) << ": " << type_string
                   << " (" << value_ << ")." << alternative_help;
  return "";
}

bool AidlConstantValue::CheckValid() const {
  // Nothing needs to be checked here. The constant value will be validated in
  // the constructor or in the evaluate() function.
  if (is_evaluated_) return is_valid_;

  switch (type_) {
    case Type::BOOLEAN:    // fall-through
    case Type::INT8:       // fall-through
    case Type::INT32:      // fall-through
    case Type::INT64:      // fall-through
    case Type::CHARACTER:  // fall-through
    case Type::STRING:     // fall-through
    case Type::REF:        // fall-through
    case Type::FLOATING:   // fall-through
    case Type::UNARY:      // fall-through
    case Type::BINARY:
      is_valid_ = true;
      break;
    case Type::ARRAY:
      is_valid_ = true;
      for (const auto& v : values_) is_valid_ &= v->CheckValid();
      break;
    case Type::ERROR:
      return false;
    default:
      AIDL_FATAL(this) << "Unrecognized constant value type: " << ToString(type_);
      return false;
  }

  return true;
}

bool AidlConstantValue::Evaluate() const {
  if (CheckValid()) {
    return evaluate();
  } else {
    return false;
  }
}

bool AidlConstantValue::evaluate() const {
  if (is_evaluated_) {
    return is_valid_;
  }
  int err = 0;
  is_evaluated_ = true;

  switch (type_) {
    case Type::ARRAY: {
      Type array_type = Type::ERROR;
      bool success = true;
      for (const auto& value : values_) {
        success = value->CheckValid();
        if (success) {
          success = value->evaluate();
          if (!success) {
            AIDL_ERROR(this) << "Invalid array element: " << value->value_;
            break;
          }
          if (array_type == Type::ERROR) {
            array_type = value->final_type_;
          } else if (!AidlBinaryConstExpression::AreCompatibleArrayTypes(array_type,
                                                                         value->final_type_)) {
            AIDL_ERROR(this) << "Incompatible array element type: " << ToString(value->final_type_)
                             << ". Expecting type compatible with " << ToString(array_type);
            success = false;
            break;
          }
        } else {
          break;
        }
      }
      if (!success) {
        err = -1;
        break;
      }
      final_type_ = type_;
      break;
    }
    case Type::BOOLEAN:
      if ((value_ != "true") && (value_ != "false")) {
        AIDL_ERROR(this) << "Invalid constant boolean value: " << value_;
        err = -1;
        break;
      }
      final_value_ = (value_ == "true") ? 1 : 0;
      final_type_ = type_;
      break;
    case Type::INT8:   // fall-through
    case Type::INT32:  // fall-through
    case Type::INT64:
      // Parsing happens in the constructor
      final_type_ = type_;
      break;
    case Type::CHARACTER:  // fall-through
    case Type::STRING:
      final_string_value_ = value_;
      final_type_ = type_;
      break;
    case Type::FLOATING:
      // Just parse on the fly in ValueString
      final_type_ = type_;
      break;
    default:
      AIDL_FATAL(this) << "Unrecognized constant value type: " << ToString(type_);
      err = -1;
  }

  return (err == 0) ? true : false;
}

bool AidlConstantValue::IsLiteral() const {
  return !AidlCast<AidlUnaryConstExpression>(*this) &&
         !AidlCast<AidlBinaryConstExpression>(*this) && !AidlCast<AidlConstantReference>(*this);
}

string AidlConstantValue::ToString(Type type) {
  switch (type) {
    case Type::BOOLEAN:
      return "a literal boolean";
    case Type::INT8:
      return "an int8 literal";
    case Type::INT32:
      return "an int32 literal";
    case Type::INT64:
      return "an int64 literal";
    case Type::ARRAY:
      return "a literal array";
    case Type::CHARACTER:
      return "a literal char";
    case Type::STRING:
      return "a literal string";
    case Type::REF:
      return "a reference";
    case Type::FLOATING:
      return "a literal float";
    case Type::UNARY:
      return "a unary expression";
    case Type::BINARY:
      return "a binary expression";
    case Type::ERROR:
      AIDL_FATAL(AIDL_LOCATION_HERE) << "aidl internal error: error type failed to halt program";
      return "";
    default:
      AIDL_FATAL(AIDL_LOCATION_HERE)
          << "aidl internal error: unknown constant type: " << static_cast<int>(type);
      return "";  // not reached
  }
}

AidlConstantReference::AidlConstantReference(const AidlLocation& location, const std::string& value)
    : AidlConstantValue(location, Type::REF, value) {
  const auto pos = value.find_last_of('.');
  if (pos == string::npos) {
    field_name_ = value;
  } else {
    ref_type_ = std::make_unique<AidlTypeSpecifier>(location, value.substr(0, pos),
                                                    /*array=*/std::nullopt, /*type_params=*/nullptr,
                                                    Comments{});
    field_name_ = value.substr(pos + 1);
  }
}

const AidlConstantValue* AidlConstantReference::Resolve(const AidlDefinedType* scope) const {
  if (resolved_) return resolved_;

  const AidlDefinedType* defined_type;
  if (ref_type_) {
    defined_type = ref_type_->GetDefinedType();
  } else {
    defined_type = scope;
  }

  if (!defined_type) {
    // This can happen when "const reference" is used in an unsupported way,
    // but missed in checks there. It works as a safety net.
    AIDL_ERROR(*this) << "Can't resolve the reference (" << value_ << ")";
    return nullptr;
  }

  if (auto enum_decl = defined_type->AsEnumDeclaration(); enum_decl) {
    for (const auto& e : enum_decl->GetEnumerators()) {
      if (e->GetName() == field_name_) {
        return resolved_ = e->GetValue();
      }
    }
  } else {
    for (const auto& c : defined_type->GetConstantDeclarations()) {
      if (c->GetName() == field_name_) {
        return resolved_ = &c->GetValue();
      }
    }
  }
  AIDL_ERROR(*this) << "Can't find " << field_name_ << " in " << defined_type->GetName();
  return nullptr;
}

bool AidlConstantReference::CheckValid() const {
  if (is_evaluated_) return is_valid_;
  AIDL_FATAL_IF(!resolved_, this) << "Should be resolved first: " << value_;
  is_valid_ = resolved_->CheckValid();
  return is_valid_;
}

bool AidlConstantReference::evaluate() const {
  if (is_evaluated_) return is_valid_;
  AIDL_FATAL_IF(!resolved_, this) << "Should be resolved first: " << value_;
  is_evaluated_ = true;

  resolved_->evaluate();
  is_valid_ = resolved_->is_valid_;
  final_type_ = resolved_->final_type_;
  if (is_valid_) {
    if (final_type_ == Type::STRING) {
      final_string_value_ = resolved_->final_string_value_;
    } else {
      final_value_ = resolved_->final_value_;
    }
  }
  return is_valid_;
}

bool AidlUnaryConstExpression::CheckValid() const {
  if (is_evaluated_) return is_valid_;
  AIDL_FATAL_IF(unary_ == nullptr, this);

  is_valid_ = unary_->CheckValid();
  if (!is_valid_) {
    final_type_ = Type::ERROR;
    return false;
  }

  return AidlConstantValue::CheckValid();
}

bool AidlUnaryConstExpression::evaluate() const {
  if (is_evaluated_) {
    return is_valid_;
  }
  is_evaluated_ = true;

  // Recursively evaluate the expression tree
  if (!unary_->is_evaluated_) {
    // TODO(b/142722772) CheckValid() should be called before ValueString()
    bool success = CheckValid();
    success &= unary_->evaluate();
    if (!success) {
      is_valid_ = false;
      return false;
    }
  }
  if (!IsCompatibleType(unary_->final_type_, op_)) {
    AIDL_ERROR(unary_) << "'" << op_ << "'"
                       << " is not compatible with " << ToString(unary_->final_type_)
                       << ": " + value_;
    is_valid_ = false;
    return false;
  }
  if (!unary_->is_valid_) {
    AIDL_ERROR(unary_) << "Invalid constant unary expression: " + value_;
    is_valid_ = false;
    return false;
  }
  final_type_ = unary_->final_type_;

  if (final_type_ == Type::FLOATING) {
    // don't do anything here. ValueString() will handle everything.
    is_valid_ = true;
    return true;
  }

#define CASE_UNARY(__type__) \
  return is_valid_ =         \
             handleUnary(*this, op_, static_cast<__type__>(unary_->final_value_), &final_value_);

  SWITCH_KIND(final_type_, CASE_UNARY, SHOULD_NOT_REACH(); final_type_ = Type::ERROR;
              is_valid_ = false; return false;)
}

bool AidlBinaryConstExpression::CheckValid() const {
  bool success = false;
  if (is_evaluated_) return is_valid_;
  AIDL_FATAL_IF(left_val_ == nullptr, this);
  AIDL_FATAL_IF(right_val_ == nullptr, this);

  success = left_val_->CheckValid();
  if (!success) {
    final_type_ = Type::ERROR;
    AIDL_ERROR(this) << "Invalid left operand in binary expression: " + value_;
  }

  success = right_val_->CheckValid();
  if (!success) {
    AIDL_ERROR(this) << "Invalid right operand in binary expression: " + value_;
    final_type_ = Type::ERROR;
  }

  if (final_type_ == Type::ERROR) {
    is_valid_ = false;
    return false;
  }

  is_valid_ = true;
  return AidlConstantValue::CheckValid();
}

bool AidlBinaryConstExpression::evaluate() const {
  if (is_evaluated_) {
    return is_valid_;
  }
  is_evaluated_ = true;
  AIDL_FATAL_IF(left_val_ == nullptr, this);
  AIDL_FATAL_IF(right_val_ == nullptr, this);

  // Recursively evaluate the binary expression tree
  if (!left_val_->is_evaluated_ || !right_val_->is_evaluated_) {
    // TODO(b/142722772) CheckValid() should be called before ValueString()
    bool success = CheckValid();
    success &= left_val_->evaluate();
    success &= right_val_->evaluate();
    if (!success) {
      is_valid_ = false;
      return false;
    }
  }
  if (!left_val_->is_valid_ || !right_val_->is_valid_) {
    is_valid_ = false;
    return false;
  }
  is_valid_ = AreCompatibleOperandTypes(left_val_->final_type_, right_val_->final_type_);
  if (!is_valid_) {
    AIDL_ERROR(this) << "Cannot perform operation '" << op_ << "' on "
                     << ToString(right_val_->GetType()) << " and " << ToString(left_val_->GetType())
                     << ".";
    return false;
  }

  bool isArithmeticOrBitflip = OP_IS_BIN_ARITHMETIC || OP_IS_BIN_BITFLIP;

  // Handle String case first
  if (left_val_->final_type_ == Type::STRING) {
    AIDL_FATAL_IF(right_val_->final_type_ != Type::STRING, this);
    if (!OPEQ("+")) {
      AIDL_ERROR(this) << "Only '+' is supported for strings, not '" << op_ << "'.";
      final_type_ = Type::ERROR;
      is_valid_ = false;
      return false;
    }

    // Remove trailing " from lhs
    const string& lhs = left_val_->final_string_value_;
    if (lhs.back() != '"') {
      AIDL_ERROR(this) << "'" << lhs << "' is missing a trailing quote.";
      final_type_ = Type::ERROR;
      is_valid_ = false;
      return false;
    }
    const string& rhs = right_val_->final_string_value_;
    // Remove starting " from rhs
    if (rhs.front() != '"') {
      AIDL_ERROR(this) << "'" << rhs << "' is missing a leading quote.";
      final_type_ = Type::ERROR;
      is_valid_ = false;
      return false;
    }

    final_string_value_ = string(lhs.begin(), lhs.end() - 1).append(rhs.begin() + 1, rhs.end());
    final_type_ = Type::STRING;
    return true;
  }

  // CASE: + - *  / % | ^ & < > <= >= == !=
  if (isArithmeticOrBitflip || OP_IS_BIN_COMP) {
    // promoted kind for both operands.
    Type promoted = UsualArithmeticConversion(IntegralPromotion(left_val_->final_type_),
                                              IntegralPromotion(right_val_->final_type_));
    // result kind.
    final_type_ = isArithmeticOrBitflip
                      ? promoted        // arithmetic or bitflip operators generates promoted type
                      : Type::BOOLEAN;  // comparison operators generates bool

#define CASE_BINARY_COMMON(__type__)                                                        \
  return is_valid_ =                                                                        \
             handleBinaryCommon(*this, static_cast<__type__>(left_val_->final_value_), op_, \
                                static_cast<__type__>(right_val_->final_value_), &final_value_);

    SWITCH_KIND(promoted, CASE_BINARY_COMMON, SHOULD_NOT_REACH(); final_type_ = Type::ERROR;
                is_valid_ = false; return false;)
  }

  // CASE: << >>
  string newOp = op_;
  if (OP_IS_BIN_SHIFT) {
    // promoted kind for both operands.
    final_type_ = UsualArithmeticConversion(IntegralPromotion(left_val_->final_type_),
                                            IntegralPromotion(right_val_->final_type_));
    auto numBits = right_val_->final_value_;
    if (numBits < 0) {
      // shifting with negative number of bits is undefined in C. In AIDL it
      // is defined as shifting into the other direction.
      newOp = OPEQ("<<") ? ">>" : "<<";
      numBits = -numBits;
    }

#define CASE_SHIFT(__type__)                                                                   \
  return is_valid_ = handleShift(*this, static_cast<__type__>(left_val_->final_value_), newOp, \
                                 static_cast<__type__>(numBits), &final_value_);

    SWITCH_KIND(final_type_, CASE_SHIFT, SHOULD_NOT_REACH(); final_type_ = Type::ERROR;
                is_valid_ = false; return false;)
  }

  // CASE: && ||
  if (OP_IS_BIN_LOGICAL) {
    final_type_ = Type::BOOLEAN;
    // easy; everything is bool.
    return handleLogical(*this, left_val_->final_value_, op_, right_val_->final_value_,
                         &final_value_);
  }

  SHOULD_NOT_REACH();
  is_valid_ = false;
  return false;
}

// Constructor for integer(byte, int, long)
// Keep parsed integer & literal
AidlConstantValue::AidlConstantValue(const AidlLocation& location, Type parsed_type,
                                     int64_t parsed_value, const string& checked_value)
    : AidlNode(location),
      type_(parsed_type),
      value_(checked_value),
      final_type_(parsed_type),
      final_value_(parsed_value) {
  AIDL_FATAL_IF(value_.empty() && type_ != Type::ERROR, location);
  AIDL_FATAL_IF(type_ != Type::INT8 && type_ != Type::INT32 && type_ != Type::INT64, location);
}

// Constructor for non-integer(String, char, boolean, float, double)
// Keep literal as it is. (e.g. String literal has double quotes at both ends)
AidlConstantValue::AidlConstantValue(const AidlLocation& location, Type type,
                                     const string& checked_value)
    : AidlNode(location),
      type_(type),
      value_(checked_value),
      final_type_(type) {
  AIDL_FATAL_IF(value_.empty() && type_ != Type::ERROR, location);
  switch (type_) {
    case Type::INT8:
    case Type::INT32:
    case Type::INT64:
    case Type::ARRAY:
      AIDL_FATAL(this) << "Invalid type: " << ToString(type_);
      break;
    default:
      break;
  }
}

// Constructor for array
AidlConstantValue::AidlConstantValue(const AidlLocation& location, Type type,
                                     std::unique_ptr<vector<unique_ptr<AidlConstantValue>>> values,
                                     const std::string& value)
    : AidlNode(location),
      type_(type),
      values_(std::move(*values)),
      value_(value),
      is_valid_(false),
      is_evaluated_(false),
      final_type_(type) {
  AIDL_FATAL_IF(type_ != Type::ARRAY, location);
}

AidlUnaryConstExpression::AidlUnaryConstExpression(const AidlLocation& location, const string& op,
                                                   std::unique_ptr<AidlConstantValue> rval)
    : AidlConstantValue(location, Type::UNARY, op + rval->value_),
      unary_(std::move(rval)),
      op_(op) {
  final_type_ = Type::UNARY;
}

AidlBinaryConstExpression::AidlBinaryConstExpression(const AidlLocation& location,
                                                     std::unique_ptr<AidlConstantValue> lval,
                                                     const string& op,
                                                     std::unique_ptr<AidlConstantValue> rval)
    : AidlConstantValue(location, Type::BINARY, lval->value_ + op + rval->value_),
      left_val_(std::move(lval)),
      right_val_(std::move(rval)),
      op_(op) {
  final_type_ = Type::BINARY;
}
