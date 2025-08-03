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

#ifndef LIBTEXTCLASSIFIER_UTILS_INTENTS_JNI_LUA_H_
#define LIBTEXTCLASSIFIER_UTILS_INTENTS_JNI_LUA_H_

#include <map>
#include <vector>

#include "utils/base/statusor.h"
#include "utils/i18n/locale.h"
#include "utils/intents/remote-action-template.h"
#include "utils/java/jni-base.h"
#include "utils/java/jni-cache.h"
#include "utils/lua-utils.h"
#include "utils/resources.h"
#include "utils/strings/stringpiece.h"
#include "utils/variant.h"

namespace libtextclassifier3 {

// An Android specific Lua environment with JNI backed callbacks.
class JniLuaEnvironment : public LuaEnvironment {
 public:
  JniLuaEnvironment(const Resources& resources, const JniCache* jni_cache,
                    const jobject context,
                    const std::vector<Locale>& device_locales);
  // Environment setup.
  bool Initialize();

  // Runs an intent generator snippet.
  bool RunIntentGenerator(const std::string& generator_snippet,
                          std::vector<RemoteActionTemplate>* remote_actions);

 protected:
  virtual void SetupExternalHook();
  bool PreallocateConstantJniStrings();

  int HandleExternalCallback();
  int HandleAndroidCallback();
  int HandleUserRestrictionsCallback();
  int HandleUrlEncode();
  int HandleUrlSchema();
  int HandleHash();
  int HandleFormat();
  int HandleAndroidStringResources();
  int HandleUrlHost();

  // Checks and retrieves string resources from the model.
  bool LookupModelStringResource() const;

  // Reads and create a RemoteAction result from Lua.
  RemoteActionTemplate ReadRemoteActionTemplateResult() const;

  // Reads the extras from the Lua result.
  std::map<std::string, Variant> ReadExtras() const;

  // Retrieves user manager if not previously done.
  bool RetrieveUserManager();

  // Retrieves system resources if not previously done.
  bool RetrieveSystemResources();

  // Parse the url string by using Uri.parse from Java.
  StatusOr<ScopedLocalRef<jobject>> ParseUri(StringPiece url) const;

  // Read remote action templates from lua generator.
  int ReadRemoteActionTemplates(std::vector<RemoteActionTemplate>* result);

  const Resources& resources_;
  JNIEnv* jenv_;
  const JniCache* jni_cache_;
  const jobject context_;
  std::vector<Locale> device_locales_;

  ScopedGlobalRef<jobject> usermanager_;
  // Whether we previously attempted to retrieve the UserManager before.
  bool usermanager_retrieved_;

  ScopedGlobalRef<jobject> system_resources_;
  // Whether we previously attempted to retrieve the system resources.
  bool system_resources_resources_retrieved_;

  // Cached JNI references for Java strings `string` and `android`.
  ScopedGlobalRef<jstring> string_;
  ScopedGlobalRef<jstring> android_;
};

}  // namespace libtextclassifier3

#endif  // LIBTEXTCLASSIFIER_UTILS_INTENTS_JNI_LUA_H_
