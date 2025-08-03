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

#include "logging.h"

#include "aidl_language.h"

bool AidlErrorLog::sHadError = false;

AidlErrorLog::AidlErrorLog(Severity severity, const AidlLocation& location,
                           const std::string& suffix /* = "" */)
    : os_(&std::cerr), severity_(severity), location_(location), suffix_(suffix) {
  sHadError |= severity_ >= ERROR;
  if (severity_ != NO_OP) {
    (*os_) << (severity_ == WARNING ? "WARNING: " : "ERROR: ");
    (*os_) << location << ": ";
  }
}

AidlErrorLog::AidlErrorLog(Severity severity, const AidlNode& node)
    : AidlErrorLog(severity, node.location_) {}

AidlErrorLog::AidlErrorLog(Severity severity, const std::string& filename)
    : AidlErrorLog(severity, AidlLocation(filename, AidlLocation::Source::EXTERNAL)) {}

AidlErrorLog::~AidlErrorLog() {
  if (severity_ == NO_OP) return;
  (*os_) << suffix_ << std::endl;
  if (severity_ == FATAL) abort();
  if (location_.IsInternal()) {
    (*os_) << "Logging an internal location should not happen. Offending location: " << location_
           << std::endl;
    abort();
  }
}
