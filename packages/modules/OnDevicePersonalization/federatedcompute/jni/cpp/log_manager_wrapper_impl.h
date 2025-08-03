/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include <jni.h>

#include <optional>
#include <string>

#include "example_iterator_wrapper_impl.h"
#include "fcp/client/flags.h"
#include "fcp/client/log_manager.h"

namespace fcp {
namespace client {
namespace engine {
namespace jni {

// A wrapper around the LogManager Java class. Can be created on an
// arbitrary thread.
class LogManagerWrapperImpl : public LogManager {
 public:
  LogManagerWrapperImpl(const LogManagerWrapperImpl &) = delete;
  void *operator new(std::size_t) = delete;
  LogManagerWrapperImpl &operator=(const LogManagerWrapperImpl &) = delete;

  LogManagerWrapperImpl(JavaVM *jvm, jobject java_log_manager);

  ~LogManagerWrapperImpl() override;

  void LogDiag(ProdDiagCode diag_code) override;

  void LogDiag(DebugDiagCode diag_code) override;

  void LogToLongHistogram(HistogramCounters histogram_counter,
                          int execution_index, int epoch_index,
                          DataSourceType data_source_type,
                          int64_t value) override;

  void SetModelIdentifier(const std::string &model_identifier) override;

 private:
  JavaVM *const jvm_;
  jobject jthis_;
  jmethodID log_prod_diag_id_;
  jmethodID log_debug_diag_id_;
  jmethodID log_to_long_histogram_id_;
  jmethodID log_to_long_histogram_with_model_identifier_id_;
  std::optional<std::string> model_identifier_;
};

}  // namespace jni
}  // namespace engine
}  // namespace client
}  // namespace fcp
