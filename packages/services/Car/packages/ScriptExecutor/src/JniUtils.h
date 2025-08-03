/*
 * Copyright (c) 2021, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#ifndef PACKAGES_SCRIPTEXECUTOR_SRC_JNIUTILS_H_
#define PACKAGES_SCRIPTEXECUTOR_SRC_JNIUTILS_H_

#include "jni.h"

extern "C" {
#include "lua.h"
}

#include "BundleWrapper.h"

#include <android-base/result.h>

namespace com {
namespace android {
namespace car {
namespace scriptexecutor {

using ::android::base::Error;
using ::android::base::Result;

// Helper function which takes android.os.Bundle object in "bundle" argument
// and converts it to Lua table on top of Lua stack. All key-value pairs are
// converted to the corresponding key-value pairs of the Lua table as long as
// the Bundle value types are supported. At this point, we support boolean,
// integer, double and String types in Java.
void pushBundleToLuaTable(JNIEnv* env, lua_State* lua, jobject bundle);

// Helper function that takes list of android.os.Bundle object in "bundleList"
// argument and converts it to Lua table on top of the Lua stack. The Lua table
// will be an array of converted android.os.Bundle objects in Lua table form,
// each having the key-value pairs as converted by pushBundleToLuaTable.
void pushBundleListToLuaTable(JNIEnv* env, lua_State* lua, jobject bundleList);

// Helper function that goes over Lua table fields one by one and populates PersistableBundle
// object wrapped in BundleWrapper.
// It is assumed that Lua table is located on top of the Lua stack. There could be other
// items in the stack, this function will not touch them.
//
// Returns false if the conversion encountered unrecoverable error.
// Otherwise, returns true for success.
//
// If the function succeeds, the stack should be unchanged.
// In case of an error, there is no need to pop elements or clean the stack. When Lua calls C,
// the stack used to pass data between Lua and C is private for each call. According to
// https://www.lua.org/pil/26.1.html, after C function returns back to Lua, Lua
// removes everything that is in the stack below the returned results.
// TODO(b/200849134): Refactor this function.
Result<void> convertLuaTableToBundle(JNIEnv* env, lua_State* lua, BundleWrapper* bundleWrapper);

}  // namespace scriptexecutor
}  // namespace car
}  // namespace android
}  // namespace com

#endif  // PACKAGES_SCRIPTEXECUTOR_SRC_JNIUTILS_H_
