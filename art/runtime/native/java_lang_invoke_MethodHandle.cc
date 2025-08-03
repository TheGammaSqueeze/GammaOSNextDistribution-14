/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include "java_lang_invoke_MethodHandle.h"

#include "nativehelper/jni_macros.h"

#include "handle_scope-inl.h"
#include "jni/jni_internal.h"
#include "method_handles.h"
#include "mirror/field.h"
#include "mirror/emulated_stack_frame.h"
#include "mirror/method_handle_impl.h"
#include "native_util.h"
#include "scoped_thread_state_change-inl.h"

namespace art HIDDEN {

static void MethodHandle_invokeExactWithFrame(JNIEnv* env, jobject thiz, jobject arguments) {
  ScopedObjectAccess soa(env);
  StackHandleScope<2> hs(soa.Self());
  auto handle = hs.NewHandle(soa.Decode<mirror::MethodHandle>(thiz));
  auto frame =  hs.NewHandle(soa.Decode<mirror::EmulatedStackFrame>(arguments));
  MethodHandleInvokeExactWithFrame(soa.Self(), handle, frame);
}

static JNINativeMethod gMethods[] = {
  NATIVE_METHOD(MethodHandle, invokeExactWithFrame, "(Ldalvik/system/EmulatedStackFrame;)V")
};

void register_java_lang_invoke_MethodHandle(JNIEnv* env) {
  REGISTER_NATIVE_METHODS("java/lang/invoke/MethodHandle");
}

}  // namespace art
