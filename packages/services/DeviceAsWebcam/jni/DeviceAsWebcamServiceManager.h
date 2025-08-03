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

/*
 *  Manage the remote camera service native functions.
 */
#pragma once
#include <jni.h>
#include <mutex>
#include <thread>

namespace android {
namespace webcam {

class UVCProvider;
class DeviceAsWebcamServiceManager {
  public:
    // Singleton instance. All member functions/fields should be accessed through this instance.
    // It lives indefinitely. Ctor and Dtor should not be relied on for cleanup of resources.
    static DeviceAsWebcamServiceManager* kInstance;

    // Returns true if the java service needs to be started. This is called by the USB Broadcast
    // receiver which might multiple receive spurious calls to start the service.
    bool shouldStartService(jobjectArray jIgnoredNodes);
    // Inits the native side of the service. This function should be called by the Java service
    // before any of the functions below it
    int setupServicesAndStartListening(JNIEnv* env, jobject javaService,
                                       jobjectArray jIgnoredNodes);
    // Called by Java to encode a frame
    int encodeImage(JNIEnv* env, jobject hardwareBuffer, jlong timestamp, jint rotation);
    // Called by native service to set the stream configuration in the Java Service.
    void setStreamConfig(bool mjpeg, uint32_t width, uint32_t height, uint32_t fps);
    // Called by native service to notify the Java service to start streaming the camera.
    void startStreaming();
    // Called by native service to notify the Java service to stop streaming the camera.
    void stopStreaming();
    // Called by native service to return an Image to the Java service.
    void returnImage(long timestamp);
    // Called by the Native Service when it wants to signal the Java service to stop.
    // This is non-blocking and does not guarantee that the Java service has stopped on return.
    void stopService();
    // Called by Java when the foreground service is being destroyed.
    void onDestroy();

    ~DeviceAsWebcamServiceManager() = default;

  private:
    DeviceAsWebcamServiceManager() = default;

    std::mutex mSerializationLock;   // Serializes all methods in class
    bool mServiceRunning = false;    // if this is true, then the variables underneath can be
                                     // considered safe to use without further checking.
    jobject mJavaService = nullptr;  // strong reference to the current foreground service.
    std::shared_ptr<UVCProvider> mUVCProvider;
    std::thread mJniThread;  // thread to make asynchronous calls to Java
};

}  // namespace webcam
}  // namespace android
