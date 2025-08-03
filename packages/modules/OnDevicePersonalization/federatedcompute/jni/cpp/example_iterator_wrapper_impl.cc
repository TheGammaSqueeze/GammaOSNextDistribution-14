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

#include "example_iterator_wrapper_impl.h"

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

struct JavaExampleIteratorClassDesc {
  static constexpr JavaMethodSig kNext = {"next", "()[B"};
  static constexpr JavaMethodSig kClose = {"close", "()V"};
};

ExampleIteratorWrapperImpl::ExampleIteratorWrapperImpl(JavaVM *jvm,
                                                       jobject example_iterator)
    : jvm_(jvm) {
  {
    std::lock_guard<std::mutex> lock(close_mu_);
    closed_ = false;
  }
  ScopedJniEnv scoped_env(jvm_);
  JNIEnv *env = scoped_env.env();
  jthis_ = env->NewGlobalRef(example_iterator);
  FCP_CHECK(jthis_ != nullptr);

  ScopedLocalRef<jclass> example_iterator_class(
      env, env->GetObjectClass(example_iterator));
  FCP_CHECK(!MoreJniUtil::CheckForJniException(env));
  FCP_CHECK(example_iterator_class.get() != nullptr);

  next_id_ = MoreJniUtil::GetMethodIdOrAbort(
      env, example_iterator_class.get(), JavaExampleIteratorClassDesc::kNext);
  close_id_ = MoreJniUtil::GetMethodIdOrAbort(
      env, example_iterator_class.get(), JavaExampleIteratorClassDesc::kClose);
}

ExampleIteratorWrapperImpl::~ExampleIteratorWrapperImpl() {
  // This ensures that the java iterator instance is released when the
  // TensorFlow session is freed.
  Close();
  ScopedJniEnv scoped_env(jvm_);
  JNIEnv *env = scoped_env.env();
  env->DeleteGlobalRef(jthis_);
}

absl::StatusOr<std::string> ExampleIteratorWrapperImpl::Next() {
  {
    std::lock_guard<std::mutex> lock(close_mu_);
    if (closed_) {
      return absl::InternalError("Next() called on closed iterator.");
    }
  }
  ScopedJniEnv scoped_env(jvm_);
  JNIEnv *env = scoped_env.env();

  ScopedLocalRef<jbyteArray> example(
      env, (jbyteArray)env->CallObjectMethod(jthis_, next_id_));
  FCP_RETURN_IF_ERROR(
      MoreJniUtil::GetExceptionStatus(env, "call JavaExampleIterator.Next()"));
  FCP_CHECK(example.get() != nullptr);

  int result_size = env->GetArrayLength(example.get());
  if (result_size == 0) {
    return absl::OutOfRangeError("end of iterator reached");
  }
  std::string example_string =
      MoreJniUtil::JByteArrayToString(env, example.get());
  return example_string;
}

// Close the iterator to release associated resources.
void ExampleIteratorWrapperImpl::Close() {
  std::lock_guard<std::mutex> lock(close_mu_);
  if (closed_) {
    return;
  }
  ScopedJniEnv scoped_env(jvm_);
  JNIEnv *env = scoped_env.env();

  env->CallVoidMethod(jthis_, close_id_);
  FCP_CHECK(!MoreJniUtil::CheckForJniException(env));
  closed_ = true;
}

}  // namespace jni
}  // namespace engine
}  // namespace client
}  // namespace fcp
