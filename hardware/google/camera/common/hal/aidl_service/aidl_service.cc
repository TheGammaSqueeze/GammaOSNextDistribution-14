/*
 * Copyright (C) 2022 The Android Open Source Project
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

#ifdef LAZY_SERVICE
#define LOG_TAG "android.hardware.pixel.camera.provider@2.7-service-lazy"
#else
#define LOG_TAG "android.hardware.pixel.camera.provider@2.7-service"
#endif

#include <aidl/android/hardware/camera/provider/ICameraProvider.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <apex_update_listener.h>
#include <cutils/properties.h>
#include <hidl/HidlTransportSupport.h>
#include <malloc.h>
#include <utils/AndroidThreads.h>
#include <utils/Errors.h>

#include <cinttypes>

#include "aidl_camera_build_version.h"
#include "aidl_camera_provider.h"

using aidl::android::hardware::camera::provider::ICameraProvider;
using ::android::hardware::camera::provider::implementation::AidlCameraProvider;

#ifdef LAZY_SERVICE
const bool kLazyService = true;
#else
const bool kLazyService = false;
#endif

const std::string kProviderInstance = "/internal/0";

int main() {
  ALOGI("Google camera provider service is starting.");
  mallopt(M_DECAY_TIME, 1);
  android::hardware::configureRpcThreadpool(/*maxThreads=*/6,
                                            /*callerWillJoin=*/true);

  // Don't depend on vndbinder setting up threads in case we stop using them
  // some day
  ABinderProcess_setThreadPoolMaxThreadCount(6);
  ABinderProcess_startThreadPool();

#ifdef __ANDROID_APEX__
  int start_count = property_get_int32("vendor.camera.hal.start.count", 0);
  property_set("vendor.camera.hal.start.count",
               std::to_string(++start_count).c_str());
  property_set("vendor.camera.hal.version",
               std::to_string(kHalManifestBuildNumber).c_str());
  property_set("vendor.camera.hal.build_id", kAndroidBuildId);
  auto start_on_update =
      ApexUpdateListener::Make("com.google.pixel.camera.hal", [](auto, auto) {
        ALOGI("APEX version updated. starting.");
        exit(0);
      });
  ALOGI(
      "Using ApexUpdateListener: %p Start Count: %d Current Version: %s "
      "(%" PRId64 ")",
      start_on_update.get(), start_count, kAndroidBuildId,
      kHalManifestBuildNumber);
#else
  ALOGI("Not using ApexUpdateListener since not running in an apex.");
#endif

  std::shared_ptr<ICameraProvider> camera_provider =
      AidlCameraProvider::Create();
  if (camera_provider == nullptr) {
    return android::NO_INIT;
  }
  std::string instance =
      std::string() + AidlCameraProvider::descriptor + kProviderInstance;
  if (kLazyService) {
    if (AServiceManager_registerLazyService(camera_provider->asBinder().get(),
                                            instance.c_str()) != STATUS_OK) {
      ALOGE("Cannot register AIDL Google camera provider lazy service");
      return android::NO_INIT;
    }
  } else {
    if (AServiceManager_addService(camera_provider->asBinder().get(),
                                   instance.c_str()) != STATUS_OK) {
      ALOGE("Cannot register AIDL Google camera provider service");
      return android::NO_INIT;
    }
  }
  androidSetThreadName("google.camera.provider");
  ABinderProcess_joinThreadPool();

  // In normal operation, the threadpool should never return.
  return EXIT_FAILURE;
}
