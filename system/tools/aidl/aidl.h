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

#pragma once

#include <limits>
#include <memory>
#include <string>
#include <vector>

#include "aidl_language.h"
#include "import_resolver.h"
#include "io_delegate.h"
#include "options.h"

namespace android {
namespace aidl {

enum class AidlError {
  UNKOWN = std::numeric_limits<int32_t>::min(),
  BAD_PRE_PROCESSED_FILE,
  PARSE_ERROR,
  FOUND_PARCELABLE,
  BAD_PACKAGE,
  BAD_IMPORT,
  BAD_TYPE,
  BAD_METHOD_ID,
  GENERATION_ERROR,
  BAD_INPUT,
  NOT_STRUCTURED,

  OK = 0,
};

bool compile_aidl(const Options& options, const IoDelegate& io_delegate);
bool dump_mappings(const Options& options, const IoDelegate& io_delegate);

// main entry point to AIDL
int aidl_entry(const Options& options, const IoDelegate& io_delegate);

const char kPreamble[] =
    R"(///////////////////////////////////////////////////////////////////////////////
// THIS FILE IS IMMUTABLE. DO NOT EDIT IN ANY CASE.                          //
///////////////////////////////////////////////////////////////////////////////

// This file is a snapshot of an AIDL file. Do not edit it manually. There are
// two cases:
// 1). this is a frozen version file - do not edit this in any case.
// 2). this is a 'current' file. If you make a backwards compatible change to
//     the interface (from the latest frozen version), the build system will
//     prompt you to update this file with `m <name>-update-api`.
//
// You must not make a backward incompatible change to any AIDL file built
// with the aidl_interface module type with versions property set. The module
// type is used to build AIDL files in a way that they can be used across
// independently updatable components of the system. If a device is shipped
// with such a backward incompatible change, it has a high risk of breaking
// later when a module using the interface is updated, e.g., Mainline modules.

)";

const string kGetInterfaceVersion("getInterfaceVersion");
const string kGetInterfaceHash("getInterfaceHash");

namespace internals {

AidlError load_and_validate_aidl(const std::string& input_file_name, const Options& options,
                                 const IoDelegate& io_delegate, AidlTypenames* typenames,
                                 vector<string>* imported_files);

} // namespace internals

}  // namespace aidl
}  // namespace android
