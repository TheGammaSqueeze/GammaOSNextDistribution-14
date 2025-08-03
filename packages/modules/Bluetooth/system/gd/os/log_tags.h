/*
 * Copyright 2023 The Android Open Source Project
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
#pragma once

// TODO(b/305066880) - Deprecate once replaced with fmtlib implementation.
// These log levels may need to be mapped to system values. These values are
// used to control the log level and should match
// `bluetooth::log_internal::Level`.
enum LogLevels {
  LOG_TAG_VERBOSE = 2,
  LOG_TAG_DEBUG = 3,
  LOG_TAG_INFO = 4,
  LOG_TAG_WARN = 5,
  LOG_TAG_ERROR = 6,
  LOG_TAG_FATAL = 7,
};
