/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include "utils/java/jni-helper.h"

#include "utils/base/status_macros.h"

namespace libtextclassifier3 {

StatusOr<ScopedLocalRef<jclass>> JniHelper::FindClass(JNIEnv* env,
                                                      const char* class_name) {
  TC3_ENSURE_LOCAL_CAPACITY_OR_RETURN;
  ScopedLocalRef<jclass> result(env->FindClass(class_name), env);
  TC3_NO_EXCEPTION_OR_RETURN;
  TC3_NOT_NULL_OR_RETURN;
  return result;
}

StatusOr<ScopedLocalRef<jclass>> JniHelper::GetObjectClass(JNIEnv* env,
                                                           jobject object) {
  TC3_ENSURE_LOCAL_CAPACITY_OR_RETURN;
  ScopedLocalRef<jclass> result(env->GetObjectClass(object), env);
  TC3_NO_EXCEPTION_OR_RETURN;
  TC3_NOT_NULL_OR_RETURN;
  return result;
}

StatusOr<jmethodID> JniHelper::GetMethodID(JNIEnv* env, jclass clazz,
                                           const char* method_name,
                                           const char* signature) {
  jmethodID result = env->GetMethodID(clazz, method_name, signature);
  TC3_NO_EXCEPTION_OR_RETURN;
  TC3_NOT_NULL_OR_RETURN;
  return result;
}

StatusOr<jmethodID> JniHelper::GetStaticMethodID(JNIEnv* env, jclass clazz,
                                                 const char* method_name,
                                                 const char* signature) {
  jmethodID result = env->GetStaticMethodID(clazz, method_name, signature);
  TC3_NO_EXCEPTION_OR_RETURN;
  TC3_NOT_NULL_OR_RETURN;
  return result;
}

StatusOr<jfieldID> JniHelper::GetFieldID(JNIEnv* env, jclass clazz,
                                         const char* field_name,
                                         const char* signature) {
  jfieldID result = env->GetFieldID(clazz, field_name, signature);
  TC3_NO_EXCEPTION_OR_RETURN;
  TC3_NOT_NULL_OR_RETURN;
  return result;
}

StatusOr<jfieldID> JniHelper::GetStaticFieldID(JNIEnv* env, jclass clazz,
                                               const char* field_name,
                                               const char* signature) {
  jfieldID result = env->GetStaticFieldID(clazz, field_name, signature);
  TC3_NO_EXCEPTION_OR_RETURN;
  TC3_NOT_NULL_OR_RETURN;
  return result;
}

StatusOr<ScopedLocalRef<jobject>> JniHelper::GetStaticObjectField(
    JNIEnv* env, jclass class_name, jfieldID field_id) {
  TC3_ENSURE_LOCAL_CAPACITY_OR_RETURN;
  ScopedLocalRef<jobject> result(
      env->GetStaticObjectField(class_name, field_id), env);
  TC3_NO_EXCEPTION_OR_RETURN;
  TC3_NOT_NULL_OR_RETURN;
  return result;
}

StatusOr<jint> JniHelper::GetStaticIntField(JNIEnv* env, jclass class_name,
                                            jfieldID field_id) {
  TC3_ENSURE_LOCAL_CAPACITY_OR_RETURN;
  jint result = env->GetStaticIntField(class_name, field_id);
  TC3_NO_EXCEPTION_OR_RETURN;
  return result;
}

StatusOr<ScopedLocalRef<jbyteArray>> JniHelper::NewByteArray(JNIEnv* env,
                                                             jsize length) {
  TC3_ENSURE_LOCAL_CAPACITY_OR_RETURN;
  ScopedLocalRef<jbyteArray> result(env->NewByteArray(length), env);
  TC3_NO_EXCEPTION_OR_RETURN;
  TC3_NOT_NULL_OR_RETURN;
  return result;
}

Status JniHelper::CallVoidMethod(JNIEnv* env, jobject object,
                                 jmethodID method_id, ...) {
  va_list args;
  va_start(args, method_id);
  env->CallVoidMethodV(object, method_id, args);
  va_end(args);

  TC3_NO_EXCEPTION_OR_RETURN;
  return Status::OK;
}

StatusOr<bool> JniHelper::CallBooleanMethod(JNIEnv* env, jobject object,
                                            jmethodID method_id, ...) {
  va_list args;
  va_start(args, method_id);
  bool result = env->CallBooleanMethodV(object, method_id, args);
  va_end(args);

  TC3_NO_EXCEPTION_OR_RETURN;
  return result;
}

StatusOr<int32> JniHelper::CallIntMethod(JNIEnv* env, jobject object,
                                         jmethodID method_id, ...) {
  va_list args;
  va_start(args, method_id);
  jint result = env->CallIntMethodV(object, method_id, args);
  va_end(args);

  TC3_NO_EXCEPTION_OR_RETURN;
  return result;
}

StatusOr<int64> JniHelper::CallLongMethod(JNIEnv* env, jobject object,
                                          jmethodID method_id, ...) {
  va_list args;
  va_start(args, method_id);
  jlong result = env->CallLongMethodV(object, method_id, args);
  va_end(args);

  TC3_NO_EXCEPTION_OR_RETURN;
  return result;
}

StatusOr<float> JniHelper::CallFloatMethod(JNIEnv* env, jobject object,
                                           jmethodID method_id, ...) {
  va_list args;
  va_start(args, method_id);
  jfloat result = env->CallFloatMethodV(object, method_id, args);
  va_end(args);

  TC3_NO_EXCEPTION_OR_RETURN;
  return result;
}

StatusOr<double> JniHelper::CallDoubleMethod(JNIEnv* env, jobject object,
                                             jmethodID method_id, ...) {
  va_list args;
  va_start(args, method_id);
  jdouble result = env->CallDoubleMethodV(object, method_id, args);
  va_end(args);

  TC3_NO_EXCEPTION_OR_RETURN;
  return result;
}

StatusOr<ScopedLocalRef<jintArray>> JniHelper::NewIntArray(JNIEnv* env,
                                                           jsize length) {
  TC3_ENSURE_LOCAL_CAPACITY_OR_RETURN;
  ScopedLocalRef<jintArray> result(env->NewIntArray(length), env);
  TC3_NO_EXCEPTION_OR_RETURN;
  TC3_NOT_NULL_OR_RETURN;
  return result;
}

StatusOr<ScopedLocalRef<jfloatArray>> JniHelper::NewFloatArray(JNIEnv* env,
                                                               jsize length) {
  TC3_ENSURE_LOCAL_CAPACITY_OR_RETURN;
  ScopedLocalRef<jfloatArray> result(env->NewFloatArray(length), env);
  TC3_NO_EXCEPTION_OR_RETURN;
  TC3_NOT_NULL_OR_RETURN;
  return result;
}

Status JniHelper::SetObjectArrayElement(JNIEnv* env, jobjectArray array,
                                        jsize index, jobject val) {
  TC3_ENSURE_LOCAL_CAPACITY_OR_RETURN;
  env->SetObjectArrayElement(array, index, val);
  TC3_NO_EXCEPTION_OR_RETURN;
  return Status::OK;
}

StatusOr<jsize> JniHelper::GetArrayLength(JNIEnv* env, jarray array) {
  TC3_ENSURE_LOCAL_CAPACITY_OR_RETURN;
  jsize result = env->GetArrayLength(array);
  TC3_NO_EXCEPTION_OR_RETURN;
  return result;
}

Status JniHelper::GetByteArrayRegion(JNIEnv* env, jbyteArray array, jsize start,
                                     jsize len, jbyte* buf) {
  TC3_ENSURE_LOCAL_CAPACITY_OR_RETURN;
  env->GetByteArrayRegion(array, start, len, buf);
  TC3_NO_EXCEPTION_OR_RETURN;
  return Status::OK;
}

Status JniHelper::SetByteArrayRegion(JNIEnv* env, jbyteArray array, jsize start,
                                     jsize len, const jbyte* buf) {
  TC3_ENSURE_LOCAL_CAPACITY_OR_RETURN;
  env->SetByteArrayRegion(array, start, len, buf);
  TC3_NO_EXCEPTION_OR_RETURN;
  return Status::OK;
}

Status JniHelper::SetIntArrayRegion(JNIEnv* env, jintArray array, jsize start,
                                    jsize len, const jint* buf) {
  TC3_ENSURE_LOCAL_CAPACITY_OR_RETURN;
  env->SetIntArrayRegion(array, start, len, buf);
  TC3_NO_EXCEPTION_OR_RETURN;
  return Status::OK;
}

Status JniHelper::SetFloatArrayRegion(JNIEnv* env, jfloatArray array,
                                      jsize start, jsize len,
                                      const jfloat* buf) {
  TC3_ENSURE_LOCAL_CAPACITY_OR_RETURN;
  env->SetFloatArrayRegion(array, start, len, buf);
  TC3_NO_EXCEPTION_OR_RETURN;
  return Status::OK;
}

StatusOr<ScopedLocalRef<jobjectArray>> JniHelper::NewObjectArray(
    JNIEnv* env, jsize length, jclass element_class, jobject initial_element) {
  TC3_ENSURE_LOCAL_CAPACITY_OR_RETURN;
  ScopedLocalRef<jobjectArray> result(
      env->NewObjectArray(length, element_class, initial_element), env);
  TC3_NO_EXCEPTION_OR_RETURN;
  TC3_NOT_NULL_OR_RETURN;
  return result;
}

StatusOr<ScopedLocalRef<jstring>> JniHelper::NewStringUTF(JNIEnv* env,
                                                          const char* bytes) {
  TC3_ENSURE_LOCAL_CAPACITY_OR_RETURN;
  ScopedLocalRef<jstring> result(env->NewStringUTF(bytes), env);
  TC3_NO_EXCEPTION_OR_RETURN;
  TC3_NOT_NULL_OR_RETURN;
  return result;
}

StatusOr<std::string> JByteArrayToString(JNIEnv* env, jbyteArray array) {
  std::string result;
  TC3_ASSIGN_OR_RETURN(const int array_length,
                       JniHelper::GetArrayLength(env, array));
  result.resize(array_length);
  TC3_RETURN_IF_ERROR(JniHelper::GetByteArrayRegion(
      env, array, 0, array_length,
      reinterpret_cast<jbyte*>(const_cast<char*>(result.data()))));
  return result;
}

StatusOr<std::string> JStringToUtf8String(JNIEnv* env, jstring jstr) {
  if (jstr == nullptr) {
    return "";
  }

  TC3_ASSIGN_OR_RETURN(ScopedLocalRef<jclass> string_class,
                       JniHelper::FindClass(env, "java/lang/String"));
  TC3_ASSIGN_OR_RETURN(
      jmethodID get_bytes_id,
      JniHelper::GetMethodID(env, string_class.get(), "getBytes",
                             "(Ljava/lang/String;)[B"));

  TC3_ASSIGN_OR_RETURN(ScopedLocalRef<jstring> encoding,
                       JniHelper::NewStringUTF(env, "UTF-8"));

  TC3_ASSIGN_OR_RETURN(ScopedLocalRef<jbyteArray> array,
                       JniHelper::CallObjectMethod<jbyteArray>(
                           env, jstr, get_bytes_id, encoding.get()));

  return JByteArrayToString(env, array.get());
}

}  // namespace libtextclassifier3
