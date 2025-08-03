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

#include "absl/status/statusor.h"
#include "fcp/client/flags.h"
#include "fcp/client/log_manager.h"
#include "fcp/client/simple_task_environment.h"

namespace fcp {
namespace client {
namespace engine {
namespace jni {

// A wrapper around the Java example iterator class. Object is only valid for
// the time of a JNI call and must not be kept around for any longer. Can be
// created on an arbitrary thread.
class ExampleIteratorWrapperImpl : public ExampleIterator {
 public:
  ExampleIteratorWrapperImpl(JavaVM *jvm, jobject example_iterator);

  ~ExampleIteratorWrapperImpl() override;

  absl::StatusOr<std::string> Next() override;

  void Close() override final;

 private:
  jmethodID next_id_;
  jmethodID close_id_;
  std::mutex close_mu_;
  bool closed_ GUARDED_BY(close_mu_);
  JavaVM *const jvm_;
  jobject jthis_;
};

}  // namespace jni
}  // namespace engine
}  // namespace client
}  // namespace fcp
