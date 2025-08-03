/**
 * Copyright 2017 The Android Open Source Project
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

#include "run_tflite.h"

#include <jni.h>
#include <string>
#include <iomanip>
#include <sstream>
#include <fcntl.h>

#include <android/log.h>

#include "tensorflow/lite/nnapi/nnapi_implementation.h"

#define LOG_TAG "NN_BENCHMARK"


// This method loads the NNAPI SL from the given path.
// Is called by a synchronized method in NNTestBase that will cache the
// result. We expect this to be called only once per JVM and the handle
// to be released when the JVM is shut down.
extern "C" JNIEXPORT jlong JNICALL
Java_com_android_nn_benchmark_core_sl_SupportLibraryDriverHandler_loadNnApiSlHandle(
    JNIEnv *env, jobject /* clazz */, jstring _nnapiSlDriverPath) {
  if (_nnapiSlDriverPath != NULL) {
    const char *nnapiSlDriverPath =
        env->GetStringUTFChars(_nnapiSlDriverPath, NULL);
    std::unique_ptr<const tflite::nnapi::NnApiSupportLibrary> tmp =
        tflite::nnapi::loadNnApiSupportLibrary(nnapiSlDriverPath);
    if (!tmp) {
      __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                          "Failed to load NNAPI SL driver from '%s'",
                          nnapiSlDriverPath);
      return false;
    }
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Loaded NNAPI SL");
    return (jlong)(uintptr_t)tmp.release();
  }

  return 0L;
}
