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

#include "JniUtils.h"

#include "nativehelper/scoped_local_ref.h"

namespace com {
namespace android {
namespace car {
namespace scriptexecutor {

// TODO(b/199415783): Revisit the topic of limits to potentially move it to standalone file.
constexpr int MAX_ARRAY_SIZE = 1000;

void pushBundleToLuaTable(JNIEnv* env, lua_State* lua, jobject bundle) {
    lua_newtable(lua);
    // null bundle object is allowed. We will treat it as an empty table.
    if (bundle == nullptr) {
        return;
    }

    // TODO(b/188832769): Consider caching some of these JNI references for
    // performance reasons.
    ScopedLocalRef<jclass> persistableBundleClass(env,
                                                  env->FindClass("android/os/PersistableBundle"));
    jmethodID getKeySetMethod =
            env->GetMethodID(persistableBundleClass.get(), "keySet", "()Ljava/util/Set;");
    ScopedLocalRef<jobject> keys(env, env->CallObjectMethod(bundle, getKeySetMethod));
    ScopedLocalRef<jclass> setClass(env, env->FindClass("java/util/Set"));
    jmethodID iteratorMethod =
            env->GetMethodID(setClass.get(), "iterator", "()Ljava/util/Iterator;");
    ScopedLocalRef<jobject> keySetIteratorObject(env,
                                                 env->CallObjectMethod(keys.get(), iteratorMethod));

    ScopedLocalRef<jclass> iteratorClass(env, env->FindClass("java/util/Iterator"));
    jmethodID hasNextMethod = env->GetMethodID(iteratorClass.get(), "hasNext", "()Z");
    jmethodID nextMethod = env->GetMethodID(iteratorClass.get(), "next", "()Ljava/lang/Object;");

    ScopedLocalRef<jclass> booleanClass(env, env->FindClass("java/lang/Boolean"));
    ScopedLocalRef<jclass> integerClass(env, env->FindClass("java/lang/Integer"));
    ScopedLocalRef<jclass> longClass(env, env->FindClass("java/lang/Long"));
    ScopedLocalRef<jclass> numberClass(env, env->FindClass("java/lang/Number"));
    ScopedLocalRef<jclass> stringClass(env, env->FindClass("java/lang/String"));
    ScopedLocalRef<jclass> booleanArrayClass(env, env->FindClass("[Z"));
    ScopedLocalRef<jclass> intArrayClass(env, env->FindClass("[I"));
    ScopedLocalRef<jclass> longArrayClass(env, env->FindClass("[J"));
    ScopedLocalRef<jclass> doubleArrayClass(env, env->FindClass("[D"));
    ScopedLocalRef<jclass> stringArrayClass(env, env->FindClass("[Ljava/lang/String;"));

    jmethodID getMethod = env->GetMethodID(persistableBundleClass.get(), "get",
                                           "(Ljava/lang/String;)Ljava/lang/Object;");

    // Iterate over key set of the bundle one key at a time.
    while (env->CallBooleanMethod(keySetIteratorObject.get(), hasNextMethod)) {
        // Read the value object that corresponds to this key.
        ScopedLocalRef<jstring> key(env,
                                    (jstring)env->CallObjectMethod(keySetIteratorObject.get(),
                                                                   nextMethod));
        ScopedLocalRef<jobject> value(env, env->CallObjectMethod(bundle, getMethod, key.get()));

        // Get the value of the type, extract it accordingly from the bundle and
        // push the extracted value and the key to the Lua table.
        if (env->IsInstanceOf(value.get(), booleanClass.get())) {
            jmethodID boolMethod = env->GetMethodID(booleanClass.get(), "booleanValue", "()Z");
            bool boolValue = static_cast<bool>(env->CallBooleanMethod(value.get(), boolMethod));
            lua_pushboolean(lua, boolValue);
        } else if (env->IsInstanceOf(value.get(), integerClass.get())) {
            jmethodID intMethod = env->GetMethodID(integerClass.get(), "intValue", "()I");
            lua_pushinteger(lua, env->CallIntMethod(value.get(), intMethod));
        } else if (env->IsInstanceOf(value.get(), longClass.get())) {
            jmethodID longMethod = env->GetMethodID(longClass.get(), "longValue", "()J");
            lua_pushinteger(lua, env->CallLongMethod(value.get(), longMethod));
        } else if (env->IsInstanceOf(value.get(), numberClass.get())) {
            // Condense other numeric types using one class. Because lua supports only
            // integer or double, and we handled integer in previous if clause.
            jmethodID numberMethod = env->GetMethodID(numberClass.get(), "doubleValue", "()D");
            /* Pushes a double onto the stack */
            lua_pushnumber(lua, env->CallDoubleMethod(value.get(), numberMethod));
        } else if (env->IsInstanceOf(value.get(), stringClass.get())) {
            // Produces a string in Modified UTF-8 encoding. Any null character
            // inside the original string is converted into two-byte encoding.
            // This way we can directly use the output of GetStringUTFChars in C API that
            // expects a null-terminated string.
            const char* rawStringValue =
                    env->GetStringUTFChars(static_cast<jstring>(value.get()), nullptr);
            lua_pushstring(lua, rawStringValue);
            env->ReleaseStringUTFChars(static_cast<jstring>(value.get()), rawStringValue);
        } else if (env->IsInstanceOf(value.get(), booleanArrayClass.get())) {
            jbooleanArray booleanArray = static_cast<jbooleanArray>(value.get());
            const auto kLength = env->GetArrayLength(booleanArray);
            // Arrays are represented as a table of sequential elements in Lua.
            // We are creating a nested table to represent this array. We specify number of elements
            // in the Java array to preallocate memory accordingly.
            lua_createtable(lua, kLength, 0);
            jboolean* rawBooleanArray = env->GetBooleanArrayElements(booleanArray, nullptr);
            // Fills in the table at stack idx -2 with key value pairs, where key is a
            // Lua index and value is an integer from the long array at that index
            for (int i = 0; i < kLength; i++) {
                lua_pushboolean(lua, rawBooleanArray[i]);
                lua_rawseti(lua, /* idx= */ -2,
                            i + 1);  // lua index starts from 1
            }
            // JNI_ABORT is used because we do not need to copy back elements.
            env->ReleaseBooleanArrayElements(booleanArray, rawBooleanArray, JNI_ABORT);
        } else if (env->IsInstanceOf(value.get(), intArrayClass.get())) {
            jintArray intArray = static_cast<jintArray>(value.get());
            const auto kLength = env->GetArrayLength(intArray);
            // Arrays are represented as a table of sequential elements in Lua.
            // We are creating a nested table to represent this array. We specify number of elements
            // in the Java array to preallocate memory accordingly.
            lua_createtable(lua, kLength, 0);
            jint* rawIntArray = env->GetIntArrayElements(intArray, nullptr);
            // Fills in the table at stack idx -2 with key value pairs, where key is a
            // Lua index and value is an integer from the int array at that index
            for (int i = 0; i < kLength; i++) {
                // Stack at index -1 is rawIntArray[i] after this push.
                lua_pushinteger(lua, rawIntArray[i]);
                lua_rawseti(lua, /* idx= */ -2,
                            i + 1);  // lua index starts from 1
            }
            // JNI_ABORT is used because we do not need to copy back elements.
            env->ReleaseIntArrayElements(intArray, rawIntArray, JNI_ABORT);
        } else if (env->IsInstanceOf(value.get(), longArrayClass.get())) {
            jlongArray longArray = static_cast<jlongArray>(value.get());
            const auto kLength = env->GetArrayLength(longArray);
            // Arrays are represented as a table of sequential elements in Lua.
            // We are creating a nested table to represent this array. We specify number of elements
            // in the Java array to preallocate memory accordingly.
            lua_createtable(lua, kLength, 0);
            jlong* rawLongArray = env->GetLongArrayElements(longArray, nullptr);
            // Fills in the table at stack idx -2 with key value pairs, where key is a
            // Lua index and value is an integer from the long array at that index
            for (int i = 0; i < kLength; i++) {
                lua_pushinteger(lua, rawLongArray[i]);
                lua_rawseti(lua, /* idx= */ -2,
                            i + 1);  // lua index starts from 1
            }
            // JNI_ABORT is used because we do not need to copy back elements.
            env->ReleaseLongArrayElements(longArray, rawLongArray, JNI_ABORT);
        } else if (env->IsInstanceOf(value.get(), doubleArrayClass.get())) {
            jdoubleArray doubleArray = static_cast<jdoubleArray>(value.get());
            const auto kLength = env->GetArrayLength(doubleArray);
            // Arrays are represented as a table of sequential elements in Lua.
            // We are creating a nested table to represent this array. We specify number of elements
            // in the Java array to preallocate memory accordingly.
            lua_createtable(lua, kLength, 0);
            jdouble* rawDoubleArray = env->GetDoubleArrayElements(doubleArray, nullptr);
            // Fills in the table at stack idx -2 with key value pairs, where key is a
            // Lua index and value is an double from the double array at that index
            for (int i = 0; i < kLength; i++) {
                lua_pushnumber(lua, rawDoubleArray[i]);
                lua_rawseti(lua, /* idx= */ -2,
                            i + 1);  // lua index starts from 1
            }
            env->ReleaseDoubleArrayElements(doubleArray, rawDoubleArray, JNI_ABORT);
        } else if (env->IsInstanceOf(value.get(), stringArrayClass.get())) {
            jobjectArray stringArray = static_cast<jobjectArray>(value.get());
            const auto kLength = env->GetArrayLength(stringArray);
            // Arrays are represented as a table of sequential elements in Lua.
            // We are creating a nested table to represent this array. We specify number of elements
            // in the Java array to preallocate memory accordingly.
            lua_createtable(lua, kLength, 0);
            // Fills in the table at stack idx -2 with key value pairs, where key is a Lua index and
            // value is an string value extracted from the object array at that index
            for (int i = 0; i < kLength; i++) {
                ScopedLocalRef<jobject> localStringRef(env,
                                                       env->GetObjectArrayElement(stringArray, i));
                jstring element = static_cast<jstring>(localStringRef.get());
                const char* rawStringValue = env->GetStringUTFChars(element, nullptr);
                lua_pushstring(lua, rawStringValue);
                env->ReleaseStringUTFChars(element, rawStringValue);
                // lua index starts from 1
                lua_rawseti(lua, /* idx= */ -2, i + 1);
            }
        } else if (env->IsInstanceOf(value.get(), persistableBundleClass.get())) {
            jobject bundle = static_cast<jobject>(value.get());
            // After this call, the lua stack will have 1 new item at the top of the stack: a table
            // representing the PersistableBundle
            pushBundleToLuaTable(env, lua, bundle);
        } else {
            // Other types are not implemented yet, skipping.
            continue;
        }

        const char* rawKey = env->GetStringUTFChars(key.get(), nullptr);
        // table[rawKey] = value, where value is on top of the stack,
        // and the table is the next element in the stack.
        lua_setfield(lua, /* idx= */ -2, rawKey);
        env->ReleaseStringUTFChars(key.get(), rawKey);
    }
}

void pushBundleListToLuaTable(JNIEnv* env, lua_State* lua, jobject bundleList) {
    // Creates a new table as the encompassing array to contain the converted bundles.
    // Pushed to top of stack.
    lua_newtable(lua);

    ScopedLocalRef<jclass> listClass(env, env->FindClass("java/util/List"));
    jmethodID sizeMethod = env->GetMethodID(listClass.get(), "size", "()I");
    jmethodID getMethod = env->GetMethodID(listClass.get(), "get", "(I)Ljava/lang/Object;");

    const auto listSize = env->CallIntMethod(bundleList, sizeMethod);
    // For each bundle in the bundleList set a converted Lua table into the table array.
    for (int i = 0; i < listSize; i++) {
        // Push to stack the index at which the next Lua table will be at. Lua index start at 1.
        lua_pushnumber(lua, i + 1);
        // Convert the bundle at i into Lua table and push to top of stack.
        pushBundleToLuaTable(env, lua, env->CallObjectMethod(bundleList, getMethod, i));
        // table[k] = v, table should be at the given index (-3), and expects v the value to be
        // at the top of the stack, and k the key to be just below the top.
        lua_settable(lua, /* idx= */ -3);
    }
}

Result<void> convertLuaTableToBundle(JNIEnv* env, lua_State* lua, BundleWrapper* bundleWrapper) {
    // Iterate over Lua table which is expected to be at the top of Lua stack.
    // lua_next call pops the key from the top of the stack and finds the next
    // key-value pair. It returns 0 if the next pair was not found.
    // More on lua_next in: https://www.lua.org/manual/5.3/manual.html#lua_next
    lua_pushnil(lua);  // First key is a null value, at index -1
    while (lua_next(lua, /* table index = */ -2) != 0) {
        //  'key' is at index -2 and 'value' is at index -1
        // -1 index is the top of the stack.
        // remove 'value' and keep 'key' for next iteration
        // Process each key-value depending on a type and push it to Java PersistableBundle.
        // TODO(b/199531928): Consider putting limits on key sizes as well.
        const char* key = lua_tostring(lua, /* index = */ -2);
        Result<void> bundleInsertionResult;
        if (lua_isboolean(lua, /* index = */ -1)) {
            bundleInsertionResult =
                    bundleWrapper->putBoolean(key,
                                              static_cast<bool>(
                                                      lua_toboolean(lua, /* index = */ -1)));
        } else if (lua_isinteger(lua, /* index = */ -1)) {
            bundleInsertionResult =
                    bundleWrapper->putLong(key,
                                           static_cast<int64_t>(
                                                   lua_tointeger(lua, /* index = */ -1)));
        } else if (lua_isnumber(lua, /* index = */ -1)) {
            bundleInsertionResult =
                    bundleWrapper->putDouble(key,
                                             static_cast<double>(
                                                     lua_tonumber(lua, /* index = */ -1)));
        } else if (lua_isstring(lua, /* index = */ -1)) {
            // TODO(b/199415783): We need to have a limit on how long these strings could be.
            bundleInsertionResult =
                    bundleWrapper->putString(key, lua_tostring(lua, /* index = */ -1));
        } else if (lua_istable(lua, /* index =*/-1) && lua_rawlen(lua, -1) > 0) {
            // Lua uses tables to represent arrays and PersistableBundles.
            // If lua_rawlen is greater than 0, this table is a sequence, which means it is an
            // array.

            // TODO(b/199438375): Document to users that we expect tables to be either only indexed
            // or keyed but not both. If the table contains consecutively indexed values starting
            // from 1, we will treat it as an array. lua_rawlen call returns the size of the indexed
            // part. We copy this part into an array, but any keyed values in this table are
            // ignored. There is a test that documents this current behavior. If a user wants a
            // nested table to be represented by a PersistableBundle object, they must make sure
            // that the nested table does not contain indexed data, including no key=1.
            const auto kTableLength = lua_rawlen(lua, -1);
            if (kTableLength > MAX_ARRAY_SIZE) {
                return Error()
                        << "Returned table " << key << " exceeds maximum allowed size of "
                        << MAX_ARRAY_SIZE
                        << " elements. This key-value cannot be unpacked successfully. This error "
                           "is unrecoverable.";
            }

            std::vector<unsigned char> boolArray;
            std::vector<double> doubleArray;
            std::vector<int64_t> longArray;
            std::vector<std::string> stringArray;
            int originalLuaType = LUA_TNIL;
            for (int i = 0; i < kTableLength; i++) {
                lua_rawgeti(lua, -1, i + 1);
                // Lua allows arrays to have values of varying type. We need to force all Lua
                // arrays to stick to single type within the same array. We use the first value
                // in the array to determine the type of all values in the array that follow
                // after. If the second, third, etc element of the array does not match the type
                // of the first element we stop the extraction and return an error via a
                // callback.
                if (i == 0) {
                    originalLuaType = lua_type(lua, /* index = */ -1);
                }
                int currentType = lua_type(lua, /* index= */ -1);
                if (currentType != originalLuaType) {
                    return Error()
                            << "Returned Lua arrays must have elements of the same type. Returned "
                               "table with key="
                            << key << " has the first element of type=" << originalLuaType
                            << ", but the element at index=" << i + 1 << " has type=" << currentType
                            << ". Integer type codes are defined in lua.h file. This error is "
                               "unrecoverable.";
                }
                switch (currentType) {
                    case LUA_TBOOLEAN:
                        boolArray.push_back(
                                static_cast<unsigned char>(lua_toboolean(lua, /* index = */ -1)));
                        break;
                    case LUA_TNUMBER:
                        if (lua_isinteger(lua, /* index = */ -1)) {
                            longArray.push_back(lua_tointeger(lua, /* index = */ -1));
                        } else {
                            doubleArray.push_back(lua_tonumber(lua, /* index = */ -1));
                        }
                        break;
                    case LUA_TSTRING:
                        // TODO(b/200833728): Investigate optimizations to minimize string
                        // copying. For example, populate JNI object array one element at a
                        // time, as we go.
                        stringArray.push_back(lua_tostring(lua, /* index = */ -1));
                        break;
                    default:
                        return Error() << "Returned value for key=" << key
                                       << " is an array with values of type="
                                       << lua_typename(lua, lua_type(lua, /* index = */ -1))
                                       << ", which is not supported yet.";
                }
                lua_pop(lua, 1);
            }
            switch (originalLuaType) {
                case LUA_TBOOLEAN:
                    bundleInsertionResult = bundleWrapper->putBooleanArray(key, boolArray);
                    break;
                case LUA_TNUMBER:
                    if (longArray.size() > 0) {
                        bundleInsertionResult = bundleWrapper->putLongArray(key, longArray);
                    } else {
                        bundleInsertionResult = bundleWrapper->putDoubleArray(key, doubleArray);
                    }
                    break;
                case LUA_TSTRING:
                    bundleInsertionResult = bundleWrapper->putStringArray(key, stringArray);
                    break;
            }
        } else if (lua_istable(lua, /* index =*/-1)) {
            // If the Lua table is not a sequence, i.e., it is a table with string keys, then it is
            // a PersistableBundle
            BundleWrapper nestedBundleWrapper(env);
            // After this line, the Lua stack is unchanged, so the top of the stack is still a
            // table, but the nestedBundleWrapper will be populated
            const auto status = convertLuaTableToBundle(env, lua, &nestedBundleWrapper);
            if (!status.ok()) {
                return Error() << "Failed to parse nested tables into nested PersistableBundles";
            }
            bundleInsertionResult = bundleWrapper->putPersistableBundle(key, nestedBundleWrapper);
        } else {
            return Error() << "key=" << key << " has a Lua type="
                           << lua_typename(lua, lua_type(lua, /* index = */ -1))
                           << ", which is not supported yet.";
        }
        // Pop value from the stack, keep the key for the next iteration.
        lua_pop(lua, 1);
        // The key is at index -1, the table is at index -2 now.

        // Check if insertion of the current key-value into the bundle was successful. If not,
        // fail-fast out of this extraction routine.
        if (!bundleInsertionResult.ok()) {
            return bundleInsertionResult;
        }
    }
    return {};  // ok result
}

}  // namespace scriptexecutor
}  // namespace car
}  // namespace android
}  // namespace com
