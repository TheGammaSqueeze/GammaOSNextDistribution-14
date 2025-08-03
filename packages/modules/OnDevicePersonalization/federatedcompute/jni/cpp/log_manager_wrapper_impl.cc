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

#include "log_manager_wrapper_impl.h"

#include <jni.h>

#include <string>

#include "fcp/jni/jni_util.h"
#include "fcp/protos/federatedcompute/common.pb.h"
#include "more_jni_util.h"
#include "nativehelper/scoped_local_ref.h"

namespace fcp {
namespace client {
namespace engine {
namespace jni {

using ::fcp::jni::JavaMethodSig;
using ::fcp::jni::ParseProtoFromJByteArray;
using ::fcp::jni::ScopedJniEnv;
using ::fcp::jni::SerializeProtoToJByteArray;

struct LogManagerClassDesc {
  static constexpr JavaMethodSig kLogProdDiag = {"logProdDiag", "(I)V"};
  static constexpr JavaMethodSig kLogDebugDiag = {"logDebugDiag", "(I)V"};
  static constexpr JavaMethodSig kLogToLongHistogram = {"logToLongHistogram",
                                                        "(IIIIJ)V"};
  static constexpr JavaMethodSig kLogToLongHistogramWithModelIdentifier = {
      "logToLongHistogram", "(IIIILjava/lang/String;J)V"};
};

LogManagerWrapperImpl::LogManagerWrapperImpl(JavaVM* jvm,
                                             jobject java_log_manager)
    : jvm_(jvm) {
  ScopedJniEnv scoped_env(jvm_);
  JNIEnv* env = scoped_env.env();
  jthis_ = env->NewGlobalRef(java_log_manager);
  FCP_CHECK(jthis_ != nullptr);

  ScopedLocalRef<jclass> java_log_manager_class(
      env, env->GetObjectClass(java_log_manager));
  FCP_CHECK(java_log_manager_class.get() != nullptr);

  log_prod_diag_id_ = MoreJniUtil::GetMethodIdOrAbort(
      env, java_log_manager_class.get(), LogManagerClassDesc::kLogProdDiag);

  log_debug_diag_id_ = MoreJniUtil::GetMethodIdOrAbort(
      env, java_log_manager_class.get(), LogManagerClassDesc::kLogDebugDiag);

  log_to_long_histogram_id_ =
      MoreJniUtil::GetMethodIdOrAbort(env, java_log_manager_class.get(),
                                      LogManagerClassDesc::kLogToLongHistogram);
  log_to_long_histogram_with_model_identifier_id_ =
      MoreJniUtil::GetMethodIdOrAbort(
          env, java_log_manager_class.get(),
          LogManagerClassDesc::kLogToLongHistogramWithModelIdentifier);
}

LogManagerWrapperImpl::~LogManagerWrapperImpl() {
  ScopedJniEnv scoped_env(jvm_);
  JNIEnv* env = scoped_env.env();
  env->DeleteGlobalRef(jthis_);
}

void LogManagerWrapperImpl::LogDiag(ProdDiagCode diag_code) {
  ScopedJniEnv scoped_env(jvm_);
  JNIEnv* env = scoped_env.env();
  env->CallVoidMethod(jthis_, log_prod_diag_id_, static_cast<jint>(diag_code));
  FCP_CHECK(!MoreJniUtil::CheckForJniException(env));
}

void LogManagerWrapperImpl::LogDiag(DebugDiagCode diag_code) {
  ScopedJniEnv scoped_env(jvm_);
  JNIEnv* env = scoped_env.env();
  env->CallVoidMethod(jthis_, log_debug_diag_id_, static_cast<jint>(diag_code));
  FCP_CHECK(!MoreJniUtil::CheckForJniException(env));
}

void LogManagerWrapperImpl::LogToLongHistogram(
    HistogramCounters histogram_counter, int execution_index, int epoch_index,
    DataSourceType data_source_type, int64_t value) {
  ScopedJniEnv scoped_env(jvm_);
  JNIEnv* env = scoped_env.env();
  if (model_identifier_.has_value()) {
    ScopedLocalRef<jstring> model_identifier_jstring(
        env, env->NewStringUTF(model_identifier_.value().c_str()));
    FCP_CHECK(!MoreJniUtil::CheckForJniException(env));

    env->CallVoidMethod(jthis_, log_to_long_histogram_with_model_identifier_id_,
                        static_cast<jint>(histogram_counter), execution_index,
                        epoch_index, static_cast<jint>(data_source_type),
                        model_identifier_jstring.get(),
                        static_cast<jlong>(value));
  } else {
    env->CallVoidMethod(jthis_, log_to_long_histogram_id_,
                        static_cast<jint>(histogram_counter), execution_index,
                        epoch_index, static_cast<jint>(data_source_type),
                        static_cast<jlong>(value));
  }
  FCP_CHECK(!MoreJniUtil::CheckForJniException(env));
}

void LogManagerWrapperImpl::SetModelIdentifier(
    const std::string& model_identifier) {
  model_identifier_ = model_identifier;
}

}  // namespace jni
}  // namespace engine
}  // namespace client
}  // namespace fcp
