/*
 * Copyright (C) 2015 The Android Open Source Project
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

#ifndef ART_RUNTIME_VERIFIER_VERIFIER_ENUMS_H_
#define ART_RUNTIME_VERIFIER_VERIFIER_ENUMS_H_

#include <stdint.h>

#include "base/macros.h"

namespace art HIDDEN {
namespace verifier {

// The mode that the verifier should run as.
enum class VerifyMode : int8_t {
  kNone,      // Everything is assumed verified.
  kEnable,    // Standard verification, try pre-verifying at compile-time.
  kSoftFail,  // Force a soft fail, punting to the interpreter with access checks.
};

// The outcome of verification.
enum class FailureKind {
  kNoFailure,
  kAccessChecksFailure,
  kTypeChecksFailure,
  kSoftFailure,
  kHardFailure,
};
std::ostream& operator<<(std::ostream& os, FailureKind rhs);

// How to log hard failures during verification.
enum class HardFailLogMode {
  kLogNone,                               // Don't log hard failures at all.
  kLogVerbose,                            // Log with severity VERBOSE.
  kLogWarning,                            // Log with severity WARNING.
  kLogInternalFatal,                      // Log with severity FATAL_WITHOUT_ABORT
};

/*
 * "Direct" and "virtual" methods are stored independently. The type of call used to invoke the
 * method determines which list we search, and whether we travel up into superclasses.
 *
 * (<clinit>, <init>, and methods declared "private" or "static" are stored in the "direct" list.
 * All others are stored in the "virtual" list.)
 */
enum MethodType {
  METHOD_UNKNOWN  = 0,
  METHOD_DIRECT,      // <init>, private
  METHOD_STATIC,      // static
  METHOD_VIRTUAL,     // virtual
  METHOD_SUPER,       // super
  METHOD_INTERFACE,   // interface
  METHOD_POLYMORPHIC  // polymorphic
};
std::ostream& operator<<(std::ostream& os, MethodType rhs);

/*
 * An enumeration of problems that can turn up during verification.
 */
enum VerifyError : uint32_t {
  VERIFY_ERROR_BAD_CLASS_HARD =    1 << 0,   // VerifyError; hard error that skips compilation.
  VERIFY_ERROR_NO_CLASS =          1 << 1,   // NoClassDefFoundError.
  VERIFY_ERROR_UNRESOLVED_TYPE_CHECK = 1 << 2,   // Missing class for doing a type check
  VERIFY_ERROR_NO_METHOD =         1 << 3,   // NoSuchMethodError.
  VERIFY_ERROR_ACCESS_CLASS =      1 << 4,   // IllegalAccessError.
  VERIFY_ERROR_ACCESS_FIELD =      1 << 5,   // IllegalAccessError.
  VERIFY_ERROR_ACCESS_METHOD =     1 << 6,   // IllegalAccessError.
  VERIFY_ERROR_CLASS_CHANGE =      1 << 7,   // IncompatibleClassChangeError.
  VERIFY_ERROR_INSTANTIATION =     1 << 8,   // InstantiationError.
  VERIFY_ERROR_LOCKING =           1 << 9,  // Could not guarantee balanced locking. This should be
                                             // punted to the interpreter with access checks.
  VERIFY_ERROR_RUNTIME_THROW =     1 << 10,  // The interpreter found an instruction that will
                                             // throw. Used for app compatibility for apps < T.
};
std::ostream& operator<<(std::ostream& os, VerifyError rhs);

}  // namespace verifier
}  // namespace art

#endif  // ART_RUNTIME_VERIFIER_VERIFIER_ENUMS_H_
