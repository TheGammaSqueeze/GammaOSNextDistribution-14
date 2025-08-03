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
/* Header for class com_android_adservices_ActJni */

#include <jni.h>
#include <google/protobuf/message_lite.h>

#ifndef ADSERVICES_SERVICE_CORE_JNI_INCLUDE_JNI_UTIL_H_
#define ADSERVICES_SERVICE_CORE_JNI_INCLUDE_JNI_UTIL_H_

namespace jni_util {

class JniUtil {
    public:
        static void ThrowJavaException(JNIEnv* env, const char* exception_class_name,
                                                        const char* message) {
            env->ThrowNew(env->FindClass(exception_class_name), message);

        }

        static bool BytesToCppProto(JNIEnv* env, google::protobuf::MessageLite* proto,
                                                                            jbyteArray input) {
            bool parsed_ok = false;
            const int size = env->GetArrayLength(input);
            void* ptr = env->GetPrimitiveArrayCritical(input, nullptr);
            if (ptr) {
                parsed_ok = proto->ParseFromArray(static_cast<char*>(ptr), size);
                env->ReleasePrimitiveArrayCritical(input, ptr, JNI_ABORT);
            }
            return parsed_ok;
        }

        static jbyteArray SerializeProtoToJniByteArray(JNIEnv* env,
                                                   const google::protobuf::MessageLite& protobuf) {
           const int size = protobuf.ByteSizeLong();
           jbyteArray ret = env->NewByteArray(size);
           if (ret == nullptr) {
               return nullptr;
           }

           uint8_t* scoped_array
                            = static_cast<uint8_t* >(env->GetPrimitiveArrayCritical(ret, nullptr));
           protobuf.SerializeWithCachedSizesToArray(scoped_array);
           env->ReleasePrimitiveArrayCritical(ret, scoped_array, 0);
           return ret;
       }
};

}

#endif //ADSERVICES_SERVICE_CORE_JNI_INCLUDE_JNI_UTIL_H_
