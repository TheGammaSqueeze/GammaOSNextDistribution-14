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

#include <jni.h>

#include <android/sensor.h>

namespace android {
namespace virtualdevice {
namespace cts {

jstring getDefaultAccelerometerName(JNIEnv* env, jclass) {
    static constexpr char kPackageName[] = "android.virtualdevice.cts.sensor";
    ASensorManager* sensor_manager = ASensorManager_getInstanceForPackage(kPackageName);
    if (!sensor_manager) {
        return nullptr;
    }
    const ASensor* sensor =
            ASensorManager_getDefaultSensor(sensor_manager, ASENSOR_TYPE_ACCELEROMETER);
    return env->NewStringUTF(ASensor_getName(sensor));
}

// ------------------------------------------------------------------------------------------------

static JNINativeMethod gMethods[] = {
       { "nativeGetDefaultAccelerometerName", "()Ljava/lang/String;",
         (void*)getDefaultAccelerometerName },
};

int register_android_virtualdevice_cts_sensor_util_NativeSensorTestActivity(JNIEnv* env) {
    jclass clazz = env->FindClass("android/virtualdevice/cts/sensor/util/NativeSensorTestActivity");
    return env->RegisterNatives(
            clazz, gMethods, sizeof(gMethods) / sizeof(JNINativeMethod));
}

}  // namespace cts
}  // namespace virtualdevice
}  // namespace android
