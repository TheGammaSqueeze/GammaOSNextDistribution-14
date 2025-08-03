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

#include <jni.h>

#include "example_iterator_wrapper_impl.h"
#include "fcp/client/fcp_runner.h"
#include "fcp/client/fl_runner.pb.h"
#include "fcp/client/interruptible_runner.h"
#include "fcp/jni/jni_util.h"
#include "flags_impl.h"
#include "log_manager_wrapper_impl.h"
#include "more_jni_util.h"
#include "simple_task_environment_wrapper_impl.h"

#define JFUN(METHOD_NAME) \
  Java_com_android_federatedcompute_services_training_jni_FlRunnerWrapper_##METHOD_NAME  // NOLINT

using fcp::jni::ParseProtoFromJByteArray;

extern "C" JNIEXPORT jbyteArray JNICALL JFUN(runNativeFederatedComputation)(
    JNIEnv *env, jclass, jobject java_simple_task_env,
    jstring population_name_jstring, jstring session_name_jstring,
    jstring task_name_jstring, jobject java_native_log_manager,
    jbyteArray client_only_plan_bytes,
    jstring checkpoint_input_filename_jstring,
    jstring checkpoint_output_filename_jstring) {
  google::internal::federated::plan::ClientOnlyPlan client_only_plan =
      ParseProtoFromJByteArray<
          google::internal::federated::plan::ClientOnlyPlan>(
          env, client_only_plan_bytes);

  const fcp::client::engine::jni::FlagsImpl flags;
  JavaVM *jvm = MoreJniUtil::getJavaVm(env);
  fcp::client::engine::jni::SimpleTaskEnvironmentWrapperImpl
      simple_task_env_impl(jvm, java_simple_task_env);

  std::string population_name =
      MoreJniUtil::JStringToString(env, population_name_jstring);
  std::string session_name =
      MoreJniUtil::JStringToString(env, session_name_jstring);
  std::string task_name = MoreJniUtil::JStringToString(env, task_name_jstring);
  // TODO: add real implementation of log manager.
  fcp::client::engine::jni::LogManagerWrapperImpl log_manager_impl(
      jvm, java_native_log_manager);
  std::string checkpoint_input_filename =
      MoreJniUtil::JStringToString(env, checkpoint_input_filename_jstring);
  std::string checkpoint_output_filename =
      MoreJniUtil::JStringToString(env, checkpoint_output_filename_jstring);
  fcp::client::InterruptibleRunner::TimingConfig timing_config = {
      .polling_period = absl::Milliseconds(1000),
  };

  absl::StatusOr<fcp::client::FLRunnerResult> fl_runner_result =
      fcp::client::RunFederatedComputation(
          &simple_task_env_impl, &log_manager_impl, &flags, client_only_plan,
          checkpoint_input_filename, checkpoint_output_filename, session_name,
          population_name, task_name, timing_config);
  FCP_CHECK(fl_runner_result.ok());
  // Serialize + return the FLRunnerResult.
  jbyteArray fl_runner_result_serialized =
      fcp::jni::SerializeProtoToJByteArray(env, fl_runner_result.value());
  return fl_runner_result_serialized;
}
