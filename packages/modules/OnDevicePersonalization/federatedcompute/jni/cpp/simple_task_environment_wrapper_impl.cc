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

#include "simple_task_environment_wrapper_impl.h"

#include "example_iterator_wrapper_impl.h"
#include "fcp/protos/federatedcompute/common.pb.h"
#include "more_jni_util.h"
#include "nativehelper/scoped_local_ref.h"

namespace fcp {
namespace client {
namespace engine {
namespace jni {

using ::fcp::jni::ParseProtoFromJByteArray;
using ::fcp::jni::ScopedJniEnv;
using ::fcp::jni::SerializeProtoToJByteArray;
using ::google::internal::federated::plan::ExampleSelector;

SimpleTaskEnvironmentWrapperImpl::SimpleTaskEnvironmentWrapperImpl(
    JavaVM *jvm, jobject simple_task_env)
    : jvm_(jvm) {
  ScopedJniEnv scoped_env(jvm_);
  JNIEnv *env = scoped_env.env();
  jthis_ = env->NewGlobalRef(simple_task_env);
  FCP_CHECK(jthis_ != nullptr);

  ScopedLocalRef<jclass> simple_task_env_class(
      env, env->GetObjectClass(simple_task_env));
  FCP_CHECK(simple_task_env_class.get() != nullptr);

  training_conditions_satisfied_id_ = MoreJniUtil::GetMethodIdOrAbort(
      env, simple_task_env_class.get(),
      SimpleTaskEnvironmentImplClassDesc::kTrainingConditionsSatisfied);
  create_example_iterator_id_ = MoreJniUtil::GetMethodIdOrAbort(
      env, simple_task_env_class.get(),
      SimpleTaskEnvironmentImplClassDesc::kCreateExampleIterator);
}

SimpleTaskEnvironmentWrapperImpl::~SimpleTaskEnvironmentWrapperImpl() {
  ScopedJniEnv scoped_env(jvm_);
  JNIEnv *env = scoped_env.env();
  env->DeleteGlobalRef(jthis_);
}

absl::StatusOr<std::unique_ptr<ExampleIterator>>
SimpleTaskEnvironmentWrapperImpl::CreateExampleIterator(
    const ExampleSelector &example_selector,
    const SelectorContext &selector_context) {
  ScopedJniEnv scoped_env(jvm_);
  JNIEnv *env = scoped_env.env();
  ScopedLocalRef<jbyteArray> serialized_example_selector(
      env, SerializeProtoToJByteArray(env, example_selector));
  ScopedLocalRef<jbyteArray> serialized_selector_context(
      env, SerializeProtoToJByteArray(env, selector_context));

  jobject java_example_iterator = env->CallObjectMethod(
      jthis_, create_example_iterator_id_, serialized_example_selector.get(),
      serialized_selector_context.get());
  FCP_RETURN_IF_ERROR(
      MoreJniUtil::GetExceptionStatus(env, "call JavaCreateExampleIterator"));
  FCP_CHECK(java_example_iterator != nullptr);
  // TODO(b/301323421): check if abseil can be used in JNI layer.
  return absl::StatusOr<std::unique_ptr<ExampleIterator>>(
      std::make_unique<ExampleIteratorWrapperImpl>(jvm_,
                                                   java_example_iterator));
}

absl::StatusOr<std::unique_ptr<ExampleIterator>>
SimpleTaskEnvironmentWrapperImpl::CreateExampleIterator(
    const ExampleSelector &example_selector) {
  return CreateExampleIterator(example_selector, SelectorContext());
}

// Don't call to java implementation because training runs in isolated process
// which doesn't have file system access.
std::string SimpleTaskEnvironmentWrapperImpl::GetBaseDir() { return ""; }

// Don't call to java implementation because training runs in isolated process
// which doesn't have file system access.
std::string SimpleTaskEnvironmentWrapperImpl::GetCacheDir() { return ""; }

bool SimpleTaskEnvironmentWrapperImpl::TrainingConditionsSatisfied() {
  ScopedJniEnv scoped_env(jvm_);
  JNIEnv *env = scoped_env.env();
  bool training_conditions_satisfied =
      env->CallBooleanMethod(jthis_, training_conditions_satisfied_id_);
  FCP_CHECK(!MoreJniUtil::CheckForJniException(env));
  return training_conditions_satisfied;
}

}  // namespace jni
}  // namespace engine
}  // namespace client
}  // namespace fcp