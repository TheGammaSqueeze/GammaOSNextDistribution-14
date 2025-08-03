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
#include "jni.h"
#include "nativehelper/scoped_local_ref.h"

namespace com {
namespace android {
namespace car {
namespace scriptexecutor {

using ::android::base::Error;
using ::android::base::Result;

BundleWrapper::BundleWrapper(JNIEnv* env) {
    mJNIEnv = env;
    ScopedLocalRef<jclass> localBundleClassRef(mJNIEnv,
                                               mJNIEnv->FindClass("android/os/PersistableBundle"));
    mBundleClass = static_cast<jclass>(mJNIEnv->NewGlobalRef(localBundleClassRef.get()));

    jmethodID bundleConstructor = mJNIEnv->GetMethodID(mBundleClass, "<init>", "()V");
    ScopedLocalRef<jobject> localBundleObjectRef(mJNIEnv,
                                                 mJNIEnv->NewObject(mBundleClass,
                                                                    bundleConstructor));
    mBundle = mJNIEnv->NewGlobalRef(localBundleObjectRef.get());
}

BundleWrapper::~BundleWrapper() {
    // Delete global JNI references.
    if (mBundle != NULL) {
        mJNIEnv->DeleteGlobalRef(mBundle);
    }
    if (mBundleClass != NULL) {
        mJNIEnv->DeleteGlobalRef(mBundleClass);
    }
}

Result<void> BundleWrapper::putBoolean(const char* key, bool value) {
    ScopedLocalRef<jstring> keyStringRef(mJNIEnv, mJNIEnv->NewStringUTF(key));
    if (keyStringRef == nullptr) {
        return Error() << "Failed to create a string for a key=" << key << " due to OOM error";
    }

    // TODO(b/188832769): consider caching the references.
    jmethodID putBooleanMethod =
            mJNIEnv->GetMethodID(mBundleClass, "putBoolean", "(Ljava/lang/String;Z)V");
    mJNIEnv->CallVoidMethod(mBundle, putBooleanMethod, keyStringRef.get(),
                            static_cast<jboolean>(value));
    return {};  // ok result
}

Result<void> BundleWrapper::putLong(const char* key, int64_t value) {
    ScopedLocalRef<jstring> keyStringRef(mJNIEnv, mJNIEnv->NewStringUTF(key));
    if (keyStringRef == nullptr) {
        return Error() << "Failed to create a string for a key=" << key << " due to OOM error";
    }

    jmethodID putLongMethod =
            mJNIEnv->GetMethodID(mBundleClass, "putLong", "(Ljava/lang/String;J)V");
    mJNIEnv->CallVoidMethod(mBundle, putLongMethod, keyStringRef.get(), static_cast<jlong>(value));
    return {};  // ok result
}

Result<void> BundleWrapper::putDouble(const char* key, double value) {
    ScopedLocalRef<jstring> keyStringRef(mJNIEnv, mJNIEnv->NewStringUTF(key));
    if (keyStringRef == nullptr) {
        return Error() << "Failed to create a string for a key=" << key << " due to OOM error";
    }

    jmethodID putDoubleMethod =
            mJNIEnv->GetMethodID(mBundleClass, "putDouble", "(Ljava/lang/String;D)V");
    mJNIEnv->CallVoidMethod(mBundle, putDoubleMethod, keyStringRef.get(),
                            static_cast<jdouble>(value));
    return {};  // ok result
}

Result<void> BundleWrapper::putString(const char* key, const char* value) {
    jmethodID putStringMethod = mJNIEnv->GetMethodID(mBundleClass, "putString",
                                                     "(Ljava/lang/String;Ljava/lang/String;)V");
    ScopedLocalRef<jstring> keyStringRef(mJNIEnv, mJNIEnv->NewStringUTF(key));
    if (keyStringRef == nullptr) {
        return Error() << "Failed to create a string for a key=" << key << " due to OOM error";
    }

    ScopedLocalRef<jstring> valueStringRef(mJNIEnv, mJNIEnv->NewStringUTF(value));
    if (valueStringRef == nullptr) {
        return Error() << "Failed to create a string for the provided value due to OOM error";
    }

    mJNIEnv->CallVoidMethod(mBundle, putStringMethod, keyStringRef.get(), valueStringRef.get());
    return {};  // ok result
}

Result<void> BundleWrapper::putBooleanArray(const char* key,
                                            const std::vector<unsigned char>& value) {
    ScopedLocalRef<jstring> keyStringRef(mJNIEnv, mJNIEnv->NewStringUTF(key));
    if (keyStringRef == nullptr) {
        return Error() << "Failed to create a string for a key=" << key << " due to OOM error";
    }

    jmethodID putBooleanArrayMethod =
            mJNIEnv->GetMethodID(mBundleClass, "putBooleanArray", "(Ljava/lang/String;[Z)V");

    ScopedLocalRef<jbooleanArray> arrayRef(mJNIEnv, mJNIEnv->NewBooleanArray(value.size()));
    mJNIEnv->SetBooleanArrayRegion(arrayRef.get(), 0, value.size(), &value.at(0));
    mJNIEnv->CallVoidMethod(mBundle, putBooleanArrayMethod, keyStringRef.get(), arrayRef.get());
    return {};  // ok result
}

Result<void> BundleWrapper::putDoubleArray(const char* key, const std::vector<double>& value) {
    ScopedLocalRef<jstring> keyStringRef(mJNIEnv, mJNIEnv->NewStringUTF(key));
    if (keyStringRef == nullptr) {
        return Error() << "Failed to create a string for a key=" << key << " due to OOM error";
    }

    jmethodID putDoubleArrayMethod =
            mJNIEnv->GetMethodID(mBundleClass, "putDoubleArray", "(Ljava/lang/String;[D)V");

    ScopedLocalRef<jdoubleArray> arrayRef(mJNIEnv, mJNIEnv->NewDoubleArray(value.size()));
    mJNIEnv->SetDoubleArrayRegion(arrayRef.get(), 0, value.size(), &value[0]);
    mJNIEnv->CallVoidMethod(mBundle, putDoubleArrayMethod, keyStringRef.get(), arrayRef.get());
    return {};  // ok result
}

Result<void> BundleWrapper::putLongArray(const char* key, const std::vector<int64_t>& value) {
    ScopedLocalRef<jstring> keyStringRef(mJNIEnv, mJNIEnv->NewStringUTF(key));
    if (keyStringRef == nullptr) {
        return Error() << "Failed to create a string for a key=" << key << " due to OOM error";
    }

    jmethodID putLongArrayMethod =
            mJNIEnv->GetMethodID(mBundleClass, "putLongArray", "(Ljava/lang/String;[J)V");

    ScopedLocalRef<jlongArray> arrayRef(mJNIEnv, mJNIEnv->NewLongArray(value.size()));
    mJNIEnv->SetLongArrayRegion(arrayRef.get(), 0, value.size(), &value[0]);
    mJNIEnv->CallVoidMethod(mBundle, putLongArrayMethod, keyStringRef.get(), arrayRef.get());
    return {};  // ok result
}

Result<void> BundleWrapper::putStringArray(const char* key, const std::vector<std::string>& value) {
    ScopedLocalRef<jstring> keyStringRef(mJNIEnv, mJNIEnv->NewStringUTF(key));
    if (keyStringRef == nullptr) {
        return Error() << "Failed to create a string for a key=" << key << " due to OOM error";
    }

    jmethodID putStringArrayMethod =
            mJNIEnv->GetMethodID(mBundleClass, "putStringArray",
                                 "(Ljava/lang/String;[Ljava/lang/String;)V");

    ScopedLocalRef<jclass> stringClassRef(mJNIEnv, mJNIEnv->FindClass("java/lang/String"));
    ScopedLocalRef<jobjectArray> arrayRef(mJNIEnv,
                                          mJNIEnv->NewObjectArray(value.size(),
                                                                  mJNIEnv->FindClass(
                                                                          "java/lang/String"),
                                                                  nullptr));
    for (int i = 0; i < value.size(); i++) {
        ScopedLocalRef<jstring> valueStringRef(mJNIEnv, mJNIEnv->NewStringUTF(value[i].c_str()));
        if (valueStringRef == nullptr) {
            return Error() << "Failed to create a string for provided value due to OOM error";
        }
        mJNIEnv->SetObjectArrayElement(arrayRef.get(), i, valueStringRef.get());
    }
    mJNIEnv->CallVoidMethod(mBundle, putStringArrayMethod, keyStringRef.get(), arrayRef.get());
    return {};  // ok result
}

Result<void> BundleWrapper::putPersistableBundle(const char* key, const BundleWrapper& value) {
    jmethodID putPersistableBundleMethod =
            mJNIEnv->GetMethodID(mBundleClass, "putPersistableBundle",
                                 "(Ljava/lang/String;Landroid/os/PersistableBundle;)V");
    ScopedLocalRef<jstring> keyStringRef(mJNIEnv, mJNIEnv->NewStringUTF(key));
    if (keyStringRef == nullptr) {
        return Error() << "Failed to create a string for a key=" << key << " due to OOM error";
    }
    mJNIEnv->CallVoidMethod(mBundle, putPersistableBundleMethod, keyStringRef.get(),
                            value.getBundle());
    return {};  // ok result
}

jobject BundleWrapper::getBundle() const {
    return mBundle;
}

}  // namespace scriptexecutor
}  // namespace car
}  // namespace android
}  // namespace com
