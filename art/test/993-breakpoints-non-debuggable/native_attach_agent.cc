/*
 * Copyright (C) 2013 The Android Open Source Project
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

#include <sstream>

#include "jni.h"
#include "runtime.h"

namespace art {
namespace Test993BreakpointsNonDebuggable {

extern "C" JNIEXPORT void JNICALL Java_art_Test993AttachAgent_setupJvmti(JNIEnv* env, jclass) {
  Runtime* runtime = Runtime::Current();
  std::ostringstream oss;
  oss << (kIsDebugBuild ? "libtiagentd.so" : "libtiagent.so") << "=993-non-debuggable,art";
  LOG(INFO) << "agent " << oss.str();
  runtime->AttachAgent(env, oss.str(), nullptr);
}

}  // namespace Test993BreakpointsNonDebuggable
}  // namespace art
