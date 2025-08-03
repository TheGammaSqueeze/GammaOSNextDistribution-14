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

#include "fcp/client/flags.h"

namespace fcp {
namespace client {
namespace engine {
namespace jni {

class FlagsImpl : public fcp::client::Flags {
 public:
  bool use_tflite_training() const override { return true; }
  bool use_http_federated_compute_protocol() const override { return true; }
  bool enable_opstats() const override { return false; }

  int64_t condition_polling_period_millis() const override { return 1000; }
  int64_t tf_execution_teardown_grace_period_millis() const override {
    return 1000;
  }
  int64_t tf_execution_teardown_extended_period_millis() const override {
    return 2000;
  }
  int64_t grpc_channel_deadline_seconds() const override { return 0; }
  bool log_tensorflow_error_messages() const override { return true; }
  bool enable_example_query_plan_engine() const override { return true; }
};

}  // namespace jni
}  // namespace engine
}  // namespace client
}  // namespace fcp
