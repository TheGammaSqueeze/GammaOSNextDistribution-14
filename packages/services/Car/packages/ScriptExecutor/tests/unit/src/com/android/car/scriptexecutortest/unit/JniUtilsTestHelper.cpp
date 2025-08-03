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

#include "BundleWrapper.h"
#include "JniUtils.h"
#include "LuaEngine.h"
#include "jni.h"
#include "nativehelper/scoped_local_ref.h"

#include <cstdint>
#include <cstring>

namespace com {
namespace android {
namespace car {
namespace scriptexecutortest {
namespace unit {
namespace {

template <typename T>
bool hasValidNumberArray(JNIEnv* env, jobject object, jlong luaEnginePtr, jstring key,
                         T rawInputArray, const int arrayLength, bool checkIsInteger) {
    const char* rawKey = env->GetStringUTFChars(key, nullptr);
    scriptexecutor::LuaEngine* engine =
            reinterpret_cast<scriptexecutor::LuaEngine*>(static_cast<intptr_t>(luaEnginePtr));
    // Assumes the table is on top of the stack.
    auto* luaState = engine->getLuaState();
    lua_pushstring(luaState, rawKey);
    env->ReleaseStringUTFChars(key, rawKey);
    lua_gettable(luaState, -2);
    bool result = false;
    if (!lua_istable(luaState, -1)) {
        result = false;
    } else {
        // First, compare the input and Lua array sizes.
        const auto kActualLength = lua_rawlen(luaState, -1);
        if (arrayLength != kActualLength) {
            // No need to compare further if number of elements in the two arrays are not equal.
            result = false;
        } else {
            // Do element by element comparison.
            bool is_equal = true;
            for (int i = 0; i < arrayLength; ++i) {
                lua_rawgeti(luaState, -1, i + 1);
                if (checkIsInteger) {
                    is_equal = lua_isinteger(luaState, /* idx = */ -1) &&
                            lua_tointeger(luaState, /* idx = */ -1) == rawInputArray[i];
                } else {
                    is_equal = lua_isnumber(luaState, /* idx = */ -1) &&
                            lua_tonumber(luaState, /* idx = */ -1) == rawInputArray[i];
                }
                lua_pop(luaState, 1);
                if (!is_equal) break;
            }
            result = is_equal;
        }
    }
    lua_pop(luaState, 1);
    return result;
}

template <typename T>
bool hasValidBooleanArray(JNIEnv* env, jobject object, jlong luaEnginePtr, jstring key,
                          T rawInputArray, const int arrayLength) {
    const char* rawKey = env->GetStringUTFChars(key, nullptr);
    scriptexecutor::LuaEngine* engine =
            reinterpret_cast<scriptexecutor::LuaEngine*>(static_cast<intptr_t>(luaEnginePtr));
    // Assumes the table is on top of the stack.
    auto* luaState = engine->getLuaState();
    lua_pushstring(luaState, rawKey);
    env->ReleaseStringUTFChars(key, rawKey);
    lua_gettable(luaState, -2);
    bool result = false;
    if (!lua_istable(luaState, -1)) {
        result = false;
    } else {
        // First, compare the input and Lua array sizes.
        const auto kActualLength = lua_rawlen(luaState, -1);
        if (arrayLength != kActualLength) {
            // No need to compare further if number of elements in the two arrays are not equal.
            result = false;
        } else {
            // Do element by element comparison.
            bool is_equal = true;
            for (int i = 0; i < arrayLength; ++i) {
                lua_rawgeti(luaState, -1, i + 1);
                is_equal = lua_isboolean(luaState, /* idx = */ -1) &&
                        lua_toboolean(luaState, /* idx = */ -1) ==
                                static_cast<bool>(rawInputArray[i]);
                lua_pop(luaState, 1);
                if (!is_equal) break;
            }
            result = is_equal;
        }
    }
    lua_pop(luaState, 1);
    return result;
}

extern "C" {

#include "lua.h"

JNIEXPORT jlong JNICALL
Java_com_android_car_scriptexecutortest_unit_JniUtilsTest_nativeCreateLuaEngine(JNIEnv* env,
                                                                                jobject object) {
    // Cast first to intptr_t to ensure int can hold the pointer without loss.
    return static_cast<jlong>(reinterpret_cast<intptr_t>(new scriptexecutor::LuaEngine()));
}

JNIEXPORT void JNICALL
Java_com_android_car_scriptexecutortest_unit_JniUtilsTest_nativeDestroyLuaEngine(
        JNIEnv* env, jobject object, jlong luaEnginePtr) {
    delete reinterpret_cast<scriptexecutor::LuaEngine*>(static_cast<intptr_t>(luaEnginePtr));
}

JNIEXPORT void JNICALL
Java_com_android_car_scriptexecutortest_unit_JniUtilsTest_nativePushBundleToLuaTableCaller(
        JNIEnv* env, jobject object, jlong luaEnginePtr, jobject bundle) {
    scriptexecutor::LuaEngine* engine =
            reinterpret_cast<scriptexecutor::LuaEngine*>(static_cast<intptr_t>(luaEnginePtr));
    scriptexecutor::pushBundleToLuaTable(env, engine->getLuaState(), bundle);
}

JNIEXPORT void JNICALL
Java_com_android_car_scriptexecutortest_unit_JniUtilsTest_nativePushBundleListToLuaTableCaller(
        JNIEnv* env, jobject object, jlong luaEnginePtr, jobject bundleList) {
    scriptexecutor::LuaEngine* engine =
            reinterpret_cast<scriptexecutor::LuaEngine*>(static_cast<intptr_t>(luaEnginePtr));
    scriptexecutor::pushBundleListToLuaTable(env, engine->getLuaState(), bundleList);
}

JNIEXPORT jint JNICALL
Java_com_android_car_scriptexecutortest_unit_JniUtilsTest_nativeGetObjectSize(JNIEnv* env,
                                                                              jobject object,
                                                                              jlong luaEnginePtr,
                                                                              jint index) {
    scriptexecutor::LuaEngine* engine =
            reinterpret_cast<scriptexecutor::LuaEngine*>(static_cast<intptr_t>(luaEnginePtr));
    return lua_rawlen(engine->getLuaState(), static_cast<int>(index));
}

JNIEXPORT bool JNICALL
Java_com_android_car_scriptexecutortest_unit_JniUtilsTest_nativeHasBooleanValue(
        JNIEnv* env, jobject object, jlong luaEnginePtr, jstring key, jboolean value) {
    const char* rawKey = env->GetStringUTFChars(key, nullptr);
    scriptexecutor::LuaEngine* engine =
            reinterpret_cast<scriptexecutor::LuaEngine*>(static_cast<intptr_t>(luaEnginePtr));
    auto* luaState = engine->getLuaState();
    lua_pushstring(luaState, rawKey);
    env->ReleaseStringUTFChars(key, rawKey);
    lua_gettable(luaState, -2);
    bool result = false;
    if (!lua_isboolean(luaState, -1))
        result = false;
    else
        result = static_cast<bool>(lua_toboolean(luaState, -1)) == static_cast<bool>(value);
    lua_pop(luaState, 1);
    return result;
}

JNIEXPORT bool JNICALL Java_com_android_car_scriptexecutortest_unit_JniUtilsTest_nativeHasIntValue(
        JNIEnv* env, jobject object, jlong luaEnginePtr, jstring key, jint value) {
    const char* rawKey = env->GetStringUTFChars(key, nullptr);
    scriptexecutor::LuaEngine* engine =
            reinterpret_cast<scriptexecutor::LuaEngine*>(static_cast<intptr_t>(luaEnginePtr));
    // Assumes the table is on top of the stack.
    auto* luaState = engine->getLuaState();
    lua_pushstring(luaState, rawKey);
    env->ReleaseStringUTFChars(key, rawKey);
    lua_gettable(luaState, -2);
    bool result = false;
    if (!lua_isinteger(luaState, -1))
        result = false;
    else
        result = lua_tointeger(luaState, -1) == static_cast<int>(value);
    lua_pop(luaState, 1);
    return result;
}

JNIEXPORT bool JNICALL
Java_com_android_car_scriptexecutortest_unit_JniUtilsTest_nativeHasDoubleValue(
        JNIEnv* env, jobject object, jlong luaEnginePtr, jstring key, jdouble value) {
    const char* rawKey = env->GetStringUTFChars(key, nullptr);
    scriptexecutor::LuaEngine* engine =
            reinterpret_cast<scriptexecutor::LuaEngine*>(static_cast<intptr_t>(luaEnginePtr));
    // Assumes the table is on top of the stack.
    auto* luaState = engine->getLuaState();
    lua_pushstring(luaState, rawKey);
    env->ReleaseStringUTFChars(key, rawKey);
    lua_gettable(luaState, -2);
    bool result = false;
    if (!lua_isnumber(luaState, -1))
        result = false;
    else
        result = static_cast<double>(lua_tonumber(luaState, -1)) == static_cast<double>(value);
    lua_pop(luaState, 1);
    return result;
}

JNIEXPORT bool JNICALL
Java_com_android_car_scriptexecutortest_unit_JniUtilsTest_nativeHasStringValue(
        JNIEnv* env, jobject object, jlong luaEnginePtr, jstring key, jstring value) {
    const char* rawKey = env->GetStringUTFChars(key, nullptr);
    scriptexecutor::LuaEngine* engine =
            reinterpret_cast<scriptexecutor::LuaEngine*>(static_cast<intptr_t>(luaEnginePtr));
    // Assumes the table is on top of the stack.
    auto* luaState = engine->getLuaState();
    lua_pushstring(luaState, rawKey);
    env->ReleaseStringUTFChars(key, rawKey);
    lua_gettable(luaState, -2);
    bool result = false;
    if (!lua_isstring(luaState, -1)) {
        result = false;
    } else {
        std::string s = lua_tostring(luaState, -1);
        const char* rawValue = env->GetStringUTFChars(value, nullptr);
        result = strcmp(lua_tostring(luaState, -1), rawValue) == 0;
        env->ReleaseStringUTFChars(value, rawValue);
    }
    lua_pop(luaState, 1);
    return result;
}

JNIEXPORT bool JNICALL
Java_com_android_car_scriptexecutortest_unit_JniUtilsTest_nativeHasBooleanArrayValue(
        JNIEnv* env, jobject object, jlong luaEnginePtr, jstring key, jbooleanArray value) {
    jboolean* rawInputArray = env->GetBooleanArrayElements(value, nullptr);
    const auto kInputLength = env->GetArrayLength(value);
    bool result = hasValidBooleanArray(env, object, luaEnginePtr, key, rawInputArray, kInputLength);
    env->ReleaseBooleanArrayElements(value, rawInputArray, JNI_ABORT);
    return result;
}

JNIEXPORT bool JNICALL
Java_com_android_car_scriptexecutortest_unit_JniUtilsTest_nativeHasIntArrayValue(
        JNIEnv* env, jobject object, jlong luaEnginePtr, jstring key, jintArray value) {
    jint* rawInputArray = env->GetIntArrayElements(value, nullptr);
    const auto kInputLength = env->GetArrayLength(value);
    bool result = hasValidNumberArray(env, object, luaEnginePtr, key, rawInputArray, kInputLength,
                                      /* checkIsInteger= */ true);
    env->ReleaseIntArrayElements(value, rawInputArray, JNI_ABORT);
    return result;
}

JNIEXPORT bool JNICALL
Java_com_android_car_scriptexecutortest_unit_JniUtilsTest_nativeHasLongArrayValue(
        JNIEnv* env, jobject object, jlong luaEnginePtr, jstring key, jlongArray value) {
    jlong* rawInputArray = env->GetLongArrayElements(value, nullptr);
    const auto kInputLength = env->GetArrayLength(value);
    bool result = hasValidNumberArray(env, object, luaEnginePtr, key, rawInputArray, kInputLength,
                                      /* checkIsInteger= */ true);
    env->ReleaseLongArrayElements(value, rawInputArray, JNI_ABORT);
    return result;
}

JNIEXPORT bool JNICALL
Java_com_android_car_scriptexecutortest_unit_JniUtilsTest_nativeHasDoubleArrayValue(
        JNIEnv* env, jobject object, jlong luaEnginePtr, jstring key, jdoubleArray value) {
    jdouble* rawInputArray = env->GetDoubleArrayElements(value, nullptr);
    const auto kInputLength = env->GetArrayLength(value);
    bool result = hasValidNumberArray(env, object, luaEnginePtr, key, rawInputArray, kInputLength,
                                      /* checkIsInteger= */ false);
    env->ReleaseDoubleArrayElements(value, rawInputArray, JNI_ABORT);
    return result;
}

JNIEXPORT bool JNICALL
Java_com_android_car_scriptexecutortest_unit_JniUtilsTest_nativeHasNumberOfTables(
        JNIEnv* env, jobject object, jlong luaEnginePtr, jint num) {
    scriptexecutor::LuaEngine* engine =
            reinterpret_cast<scriptexecutor::LuaEngine*>(static_cast<intptr_t>(luaEnginePtr));
    auto* luaState = engine->getLuaState();
    bool result = true;
    for (int i = 1; i <= num; i++) {
        lua_pushinteger(luaState, i);
        lua_gettable(luaState, -2);
        if (!lua_istable(luaState, -1)) {
            result = false;
        }
        lua_pop(luaState, 1);
    }
    return result;
}

JNIEXPORT bool JNICALL
Java_com_android_car_scriptexecutortest_unit_JniUtilsTest_nativeHasTableAtIndexWithIntValue(
        JNIEnv* env, jobject object, jlong luaEnginePtr, jint index, jstring key, jint value) {
    const char* rawKey = env->GetStringUTFChars(key, nullptr);
    scriptexecutor::LuaEngine* engine =
            reinterpret_cast<scriptexecutor::LuaEngine*>(static_cast<intptr_t>(luaEnginePtr));
    // Assumes the table is on top of the stack.
    auto* luaState = engine->getLuaState();
    lua_pushinteger(luaState, index);
    lua_gettable(luaState, -2);
    lua_pushstring(luaState, rawKey);
    env->ReleaseStringUTFChars(key, rawKey);
    lua_gettable(luaState, -2);
    bool result = false;
    if (!lua_isinteger(luaState, -1))
        result = false;
    else
        result = lua_tointeger(luaState, -1) == static_cast<int>(value);
    lua_pop(luaState, 2);
    return result;
}

JNIEXPORT bool JNICALL
Java_com_android_car_scriptexecutortest_unit_JniUtilsTest_nativeHasPersistableBundleOfStringValue(
        JNIEnv* env, jobject object, jlong luaEnginePtr, jstring key, jstring expected) {
    const char* rawKey = env->GetStringUTFChars(key, nullptr);
    scriptexecutor::LuaEngine* engine =
            reinterpret_cast<scriptexecutor::LuaEngine*>(static_cast<intptr_t>(luaEnginePtr));
    // Assumes the table is on top of the stack.
    auto* luaState = engine->getLuaState();
    lua_pushstring(luaState, rawKey);
    lua_gettable(luaState, -2);
    // check if the key maps to a value of type table
    if (!lua_istable(luaState, -1)) {
        return false;
    }
    // convert value (a table) into a PersistableBundle
    scriptexecutor::BundleWrapper bundleWrapper(env);
    scriptexecutor::convertLuaTableToBundle(env, luaState, &bundleWrapper);
    // call PersistableBundle#toString() to compare the string representation with the expected
    // representation
    ScopedLocalRef<jclass> persistableBundleClass(env,
                                                  env->FindClass("android/os/PersistableBundle"));
    jmethodID toStringMethod =
            env->GetMethodID(persistableBundleClass.get(), "toString", "()Ljava/lang/String;");
    ScopedLocalRef<jstring> actual(env,
                                   (jstring)env->CallObjectMethod(bundleWrapper.getBundle(),
                                                                  toStringMethod));
    // convert jstring into c string
    const char* nativeActualString = env->GetStringUTFChars(actual.get(), nullptr);
    const char* nativeExpectedString = env->GetStringUTFChars(expected, nullptr);

    // compare actual vs expected
    int res = strncmp(nativeActualString, nativeExpectedString, strlen(nativeActualString));

    env->ReleaseStringUTFChars(key, rawKey);
    env->ReleaseStringUTFChars(actual.get(), nativeActualString);
    env->ReleaseStringUTFChars(expected, nativeExpectedString);
    return res == 0;
}

}  //  extern "C"

}  // namespace
}  // namespace unit
}  // namespace scriptexecutortest
}  // namespace car
}  // namespace android
}  // namespace com
