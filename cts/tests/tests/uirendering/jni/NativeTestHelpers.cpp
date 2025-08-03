/*
 * Copyright 2018 The Android Open Source Project
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
 *
 */

#include "NativeTestHelpers.h"

#include <cstdlib>
#include <cstring>
#include "jni.h"

// This file is copied over from CtsGraphicsTestCases, so that
// CtsUiRenderingTestCases can have the same functionality.
void fail(JNIEnv *env, const char *format, ...) {
  va_list args;

  va_start(args, format);
  char *msg;
  vasprintf(&msg, format, args);
  va_end(args);

  jclass exClass;

  // CtsGraphicsTestsCases has an exception to access the private constructor
  // for AssertionError. This utility class allows creating a java.lang.AssertionError
  // with a single String argument so it can be created by ThrowNew.
  const char *className = "android/uirendering/cts/util/AssertionError";
  exClass = env->FindClass(className);
  env->ThrowNew(exClass, msg);
  free(msg);
}
