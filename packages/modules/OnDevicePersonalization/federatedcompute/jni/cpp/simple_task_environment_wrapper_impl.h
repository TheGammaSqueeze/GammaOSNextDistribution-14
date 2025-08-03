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

#include <memory>

#include "absl/status/statusor.h"
#include "fcp/client/simple_task_environment.h"
#include "fcp/jni/jni_util.h"
#include "fcp/protos/plan.pb.h"

/**
 * A wrapper around a Java class for callbacks into Java. This is required for
 * functionality we do not provide in c++ code, such as Clearcut logging,
 * checking for device conditions, or querying apps for examples.
 */

namespace fcp {
namespace client {
namespace engine {
namespace jni {

using ::google::internal::federated::plan::ExampleSelector;

// Descriptions of Java classes we call into - full class path, and method
// names + JNI signatures.
struct SimpleTaskEnvironmentImplClassDesc {
  static constexpr fcp::jni::JavaMethodSig kTrainingConditionsSatisfied = {
      "trainingConditionsSatisfied", "()Z"};
  static constexpr fcp::jni::JavaMethodSig kCreateExampleIterator = {
      "createExampleIteratorWithContext",
      "([B[B)Lcom/android/federatedcompute/services/training/jni/"
      "JavaExampleIterator;"};
};

class SimpleTaskEnvironmentWrapperImpl : public SimpleTaskEnvironment {
 public:
  explicit SimpleTaskEnvironmentWrapperImpl(JavaVM *jvm,
                                            jobject simple_task_env);

  ~SimpleTaskEnvironmentWrapperImpl() override;

  std::string GetBaseDir() override;

  std::string GetCacheDir() override;

  bool TrainingConditionsSatisfied() override;

  absl::StatusOr<std::unique_ptr<ExampleIterator>> CreateExampleIterator(
      const google::internal::federated::plan::ExampleSelector
          &example_selector) override;

  absl::StatusOr<std::unique_ptr<ExampleIterator>> CreateExampleIterator(
      const ExampleSelector &example_selector,
      const SelectorContext &selector_context) override;

 private:
  JavaVM *jvm_;
  jobject jthis_;
  jmethodID training_conditions_satisfied_id_;
  jmethodID create_example_iterator_id_;
};

}  // namespace jni
}  // namespace engine
}  // namespace client
}  // namespace fcp
