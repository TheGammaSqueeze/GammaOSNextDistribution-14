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

#ifndef PACKAGES_SCRIPTEXECUTOR_SRC_BUNDLEWRAPPER_H_
#define PACKAGES_SCRIPTEXECUTOR_SRC_BUNDLEWRAPPER_H_

#include "jni.h"

#include <android-base/result.h>

#include <string>
#include <vector>

namespace com {
namespace android {
namespace car {
namespace scriptexecutor {

// Used to create a java bundle object and populate its fields one at a time.
class BundleWrapper {
public:
    explicit BundleWrapper(JNIEnv* env);
    // BundleWrapper is not copyable.
    BundleWrapper(const BundleWrapper&) = delete;
    BundleWrapper& operator=(const BundleWrapper&) = delete;

    virtual ~BundleWrapper();

    // Family of methods that puts the provided 'value' into the PersistableBundle
    // under provided 'key'.
    ::android::base::Result<void> putBoolean(const char* key, bool value);
    ::android::base::Result<void> putLong(const char* key, int64_t value);
    ::android::base::Result<void> putDouble(const char* key, double value);
    ::android::base::Result<void> putString(const char* key, const char* value);
    ::android::base::Result<void> putBooleanArray(const char* key,
                                                  const std::vector<unsigned char>& value);
    ::android::base::Result<void> putDoubleArray(const char* key, const std::vector<double>& value);
    ::android::base::Result<void> putLongArray(const char* key, const std::vector<int64_t>& value);
    ::android::base::Result<void> putStringArray(const char* key,
                                                 const std::vector<std::string>& value);
    ::android::base::Result<void> putPersistableBundle(const char* key, const BundleWrapper& value);

    jobject getBundle() const;

private:
    // The class asks Java to create PersistableBundle object and stores the reference.
    // When the instance of this class is destroyed the actual Java PersistableBundle object behind
    // this reference stays on and is managed by Java.
    jobject mBundle;

    // Reference to java PersistableBundle class cached for performance reasons.
    jclass mBundleClass;

    // Stores a JNIEnv* pointer.
    JNIEnv* mJNIEnv;  // not owned
};

}  // namespace scriptexecutor
}  // namespace car
}  // namespace android
}  // namespace com

#endif  // PACKAGES_SCRIPTEXECUTOR_SRC_BUNDLEWRAPPER_H_
