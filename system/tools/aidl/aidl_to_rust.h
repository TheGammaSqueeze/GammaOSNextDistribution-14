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
#pragma once

#include "aidl_language.h"
#include "aidl_typenames.h"

namespace android {
namespace aidl {
namespace rust {

// This header provides functions that translate AIDL things to Rust things.

enum class StorageMode {
  VALUE,
  DEFAULT_VALUE,       // Value that implements Default::default()
  IN_ARGUMENT,         // Value for primitives, & for larger types
  UNSIZED_ARGUMENT,    // Unsized input argument, e.g., str/slice
  OUT_ARGUMENT,        // Mutable reference to write-only raw type
  INOUT_ARGUMENT,      // Mutable reference to inout argument
  PARCELABLE_FIELD,    // Field in a parcelable
};

enum class ReferenceMode {
  VALUE,
  REF,
  MUT_REF,
  AS_REF,
  AS_DEREF,
};

enum class Lifetime {
  NONE,
  A,
};

inline bool IsReference(ReferenceMode ref_mode) {
  switch (ref_mode) {
    case ReferenceMode::REF:
    case ReferenceMode::MUT_REF:
      return true;

    default:
      return false;
  }
}

std::string ConstantValueDecorator(
    const AidlTypeSpecifier& type,
    const std::variant<std::string, std::vector<std::string>>& raw_value);

std::string ConstantValueDecoratorRef(
    const AidlTypeSpecifier& type,
    const std::variant<std::string, std::vector<std::string>>& raw_value);

std::string ArrayDefaultValue(const AidlTypeSpecifier& type);

// Returns "'lifetime_name " including the initial apostrophe and the trailing space.
// Returns empty string for NONE.
std::string RustLifetimeName(Lifetime lifetime);

// Returns "<'lifetime_name>" or empty string for NONE.
std::string RustLifetimeGeneric(Lifetime lifetime);

// Returns the Rust type signature of the AIDL type spec
// This includes generic type parameters with array modifiers.
//
// The lifetime argument is used to annotate all references.
std::string RustNameOf(const AidlTypeSpecifier& aidl, const AidlTypenames& typenames,
                       StorageMode mode, Lifetime lifetime);

StorageMode ArgumentStorageMode(const AidlArgument& arg, const AidlTypenames& typenames);

ReferenceMode ArgumentReferenceMode(const AidlArgument& arg, const AidlTypenames& typenames);

std::string TakeReference(ReferenceMode ref_mode, const std::string& name);

bool TypeIsInterface(const AidlTypeSpecifier& type, const AidlTypenames& typenames);

bool TypeNeedsOption(const AidlTypeSpecifier& type, const AidlTypenames& typenames);

}  // namespace rust
}  // namespace aidl
}  // namespace android
