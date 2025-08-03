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

#ifndef HARDWARE_GOOGLE_CAMERA_HAL_AIDL_SERVICE_AIDL_CAMERA_DEVICE_SESSION_H_
#define HARDWARE_GOOGLE_CAMERA_HAL_AIDL_SERVICE_AIDL_CAMERA_DEVICE_SESSION_H_

#include <aidl/android/hardware/camera/device/BnCameraDeviceSession.h>
#include <aidl/android/hardware/camera/device/ICameraDevice.h>
#include <aidl/android/hardware/camera/device/ICameraDeviceCallback.h>
#include <aidl/android/hardware/thermal/IThermal.h>
#include <android-base/thread_annotations.h>
#include <fmq/AidlMessageQueue.h>
#include <utils/StrongPointer.h>

#include <shared_mutex>
#include <vector>

#include "aidl_profiler.h"
#include "camera_device_session.h"
#include "hal_types.h"

namespace android {
namespace hardware {
namespace camera {
namespace device {
namespace implementation {

// AidlCameraDeviceSession implements the AIDL camera device session interface,
// ICameraDeviceSession, that contains the methods to configure and request
// captures from an active camera device.
class AidlCameraDeviceSession
    : public aidl::android::hardware::camera::device::BnCameraDeviceSession {
 public:
  // Create a AidlCameraDeviceSession.
  // device_session is a google camera device session that
  // AidlCameraDeviceSession is going to manage. Creating a
  // AidlCameraDeviceSession will fail if device_session is
  // nullptr.
  static std::shared_ptr<AidlCameraDeviceSession> Create(
      const std::shared_ptr<
          aidl::android::hardware::camera::device::ICameraDeviceCallback>& callback,
      std::unique_ptr<google_camera_hal::CameraDeviceSession> device_session,
      std::shared_ptr<android::hardware::camera::implementation::AidlProfiler>
          aidl_profiler);

  virtual ~AidlCameraDeviceSession();

  // functions in ICameraDeviceSession

  ndk::ScopedAStatus close() override;

  ndk::ScopedAStatus configureStreams(
      const aidl::android::hardware::camera::device::StreamConfiguration&,
      std::vector<aidl::android::hardware::camera::device::HalStream>*) override;

  ndk::ScopedAStatus constructDefaultRequestSettings(
      aidl::android::hardware::camera::device::RequestTemplate in_type,
      aidl::android::hardware::camera::device::CameraMetadata* aidl_return)
      override;

  ndk::ScopedAStatus flush() override;

  ndk::ScopedAStatus getCaptureRequestMetadataQueue(
      aidl::android::hardware::common::fmq::MQDescriptor<
          int8_t, aidl::android::hardware::common::fmq::SynchronizedReadWrite>*
          aidl_return) override;

  ndk::ScopedAStatus getCaptureResultMetadataQueue(
      aidl::android::hardware::common::fmq::MQDescriptor<
          int8_t, aidl::android::hardware::common::fmq::SynchronizedReadWrite>*
          aidl_return) override;

  ndk::ScopedAStatus isReconfigurationRequired(
      const aidl::android::hardware::camera::device::CameraMetadata&
          in_oldSessionParams,
      const aidl::android::hardware::camera::device::CameraMetadata&
          in_newSessionParams,
      bool* aidl_return) override;

  ndk::ScopedAStatus processCaptureRequest(
      const std::vector<aidl::android::hardware::camera::device::CaptureRequest>&
          in_requests,
      const std::vector<aidl::android::hardware::camera::device::BufferCache>&
          in_cachesToRemove,
      int32_t* aidl_return) override;

  ndk::ScopedAStatus signalStreamFlush(const std::vector<int32_t>& in_streamIds,
                                       int32_t in_streamConfigCounter) override;

  ndk::ScopedAStatus switchToOffline(
      const std::vector<int32_t>& in_streamsToKeep,
      aidl::android::hardware::camera::device::CameraOfflineSessionInfo*
          out_offlineSessionInfo,
      std::shared_ptr<
          aidl::android::hardware::camera::device::ICameraOfflineSession>*
          aidl_return) override;

  ndk::ScopedAStatus repeatingRequestEnd(
      int32_t /*in_frameNumber*/,
      const std::vector<int32_t>& /*in_streamIds*/) override {
    return ndk::ScopedAStatus::ok();
  };

  ndk::ScopedAStatus configureStreamsV2(
      const aidl::android::hardware::camera::device::StreamConfiguration&,
      aidl::android::hardware::camera::device::ConfigureStreamsRet*) override;
  AidlCameraDeviceSession() = default;

 protected:
  ndk::SpAIBinder createBinder() override;

 private:
  using MetadataQueue = AidlMessageQueue<
      int8_t, aidl::android::hardware::common::fmq::SynchronizedReadWrite>;

  static constexpr uint32_t kRequestMetadataQueueSizeBytes = 1 << 20;  // 1MB
  static constexpr uint32_t kResultMetadataQueueSizeBytes = 1 << 20;   // 1MB

  // Initialize the latest available gralloc buffer mapper.
  status_t InitializeBufferMapper();

  // Initialize AidlCameraDeviceSession with a CameraDeviceSession.
  status_t Initialize(
      const std::shared_ptr<
          aidl::android::hardware::camera::device::ICameraDeviceCallback>& callback,
      std::unique_ptr<google_camera_hal::CameraDeviceSession> device_session,
      std::shared_ptr<android::hardware::camera::implementation::AidlProfiler>
          aidl_profiler);

  // Create a metadata queue.
  // If override_size_property contains a valid size, it will create a metadata
  // queue of that size. If it override_size_property doesn't contain a valid
  // size, it will create a metadata queue of the default size.
  // default_size_bytes is the default size of the message queue in bytes.
  // override_size_property is the name of the system property that contains
  // the message queue size.
  status_t CreateMetadataQueue(std::unique_ptr<MetadataQueue>* metadata_queue,
                               uint32_t default_size_bytes,
                               const char* override_size_property);

  // Invoked when receiving a result from HAL.
  void ProcessCaptureResult(
      std::unique_ptr<google_camera_hal::CaptureResult> hal_result);

  // Invoked when receiving a batched result from HAL.
  void ProcessBatchCaptureResult(
      std::vector<std::unique_ptr<google_camera_hal::CaptureResult>> hal_results);

  // TODO b/311263114: Remove this method once the feature flag is enabled.
  // This is needed since the framework has the feature support flagged. The HAL
  // should not switch HAL buffer on / off is the framework doesn't support them
  // (flag is off). Since aconfig flags are not shared between the framework and
  // the HAL - the HAL can know about framework support through knowing whether
  // configureStreamsV2 was called or not.
  ndk::ScopedAStatus configureStreamsImpl(
      const aidl::android::hardware::camera::device::StreamConfiguration&,
      bool v2, aidl::android::hardware::camera::device::ConfigureStreamsRet*);
  // Invoked when receiving a message from HAL.
  void NotifyHalMessage(const google_camera_hal::NotifyMessage& hal_message);

  // Invoked when requesting stream buffers from HAL.
  google_camera_hal::BufferRequestStatus RequestStreamBuffers(
      const std::vector<google_camera_hal::BufferRequest>& hal_buffer_requests,
      std::vector<google_camera_hal::BufferReturn>* hal_buffer_returns);

  // Invoked when returning stream buffers from HAL.
  void ReturnStreamBuffers(
      const std::vector<google_camera_hal::StreamBuffer>& return_hal_buffers);

  // Set camera device session callbacks.
  void SetSessionCallbacks();

  // Register a thermal changed callback.
  // notify_throttling will be invoked when thermal status changes.
  // If filter_type is false, type will be ignored and all types will be
  // monitored.
  // If filter_type is true, only type will be monitored.
  status_t RegisterThermalChangedCallback(
      google_camera_hal::NotifyThrottlingFunc notify_throttling,
      bool filter_type, google_camera_hal::TemperatureType type);

  // Unregister thermal changed callback.
  void UnregisterThermalChangedCallback();

  // Log when the first frame buffers are all received.
  void TryLogFirstFrameDone(const google_camera_hal::CaptureResult& result,
                            const char* caller_func_name);

  std::unique_ptr<google_camera_hal::CameraDeviceSession> device_session_;

  // Metadata queue to read the request metadata from.
  std::unique_ptr<MetadataQueue> request_metadata_queue_;

  // Metadata queue to write the result metadata to.
  std::unique_ptr<MetadataQueue> result_metadata_queue_;

  // Assuming callbacks to framework is thread-safe, the shared mutex is only
  // used to protect member variable writing and reading.
  std::shared_mutex aidl_device_callback_lock_;
  // Protected by aidl_device_callback_lock_
  std::shared_ptr<aidl::android::hardware::camera::device::ICameraDeviceCallback>
      aidl_device_callback_;

  std::mutex aidl_thermal_mutex_;
  std::shared_ptr<aidl::android::hardware::thermal::IThermal> thermal_;

  // Must be protected by hidl_thermal_mutex_.
  std::shared_ptr<aidl::android::hardware::thermal::IThermalChangedCallback>
      thermal_changed_callback_ GUARDED_BY(aidl_thermal_mutex_);

  // Flag for profiling first frame processing time.
  bool first_frame_requested_ = false;

  // The frame number of first capture request after configure stream
  uint32_t first_request_frame_number_ = 0;

  std::mutex pending_first_frame_buffers_mutex_;
  // Profiling first frame process time. Stop timer when it become 0.
  // Must be protected by pending_first_frame_buffers_mutex_
  size_t num_pending_first_frame_buffers_ = 0;

  std::shared_ptr<android::hardware::camera::implementation::AidlProfiler>
      aidl_profiler_;
};

}  // namespace implementation
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android

#endif  // HARDWARE_GOOGLE_CAMERA_HAL_AIDL_SERVICE_AIDL_CAMERA_DEVICE_SESSION_H_
