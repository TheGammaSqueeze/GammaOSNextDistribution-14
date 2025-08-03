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

#ifndef HARDWARE_GOOGLE_CAMERA_HAL_AIDL_SERVICE_AIDL_UTILS_H_
#define HARDWARE_GOOGLE_CAMERA_HAL_AIDL_SERVICE_AIDL_UTILS_H_

#include <aidl/android/hardware/camera/common/CameraMetadataType.h>
#include <aidl/android/hardware/camera/common/Status.h>
#include <aidl/android/hardware/camera/device/ICameraDevice.h>
#include <aidl/android/hardware/camera/provider/ICameraProvider.h>
#include <aidlcommonsupport/NativeHandle.h>
/*#include <android/hardware/camera/common/1.0/types.h>
#include <android/hardware/camera/device/3.7/ICameraDeviceSession.h>
#include <android/hardware/camera/device/3.8/types.h>*/
#include <fmq/AidlMessageQueue.h>
#include <fmq/MessageQueue.h>
#include <hal_types.h>

#include <memory>

#include "aidl_camera_provider.h"

namespace android {
namespace hardware {
namespace camera {
namespace implementation {
namespace aidl_utils {

using aidl::android::hardware::camera::common::CameraDeviceStatus;
using aidl::android::hardware::camera::common::CameraMetadataType;
using aidl::android::hardware::camera::common::CameraResourceCost;
using aidl::android::hardware::camera::common::Status;
using aidl::android::hardware::camera::common::TorchModeStatus;
using aidl::android::hardware::camera::common::VendorTagSection;
using aidl::android::hardware::camera::device::BufferCache;
using aidl::android::hardware::camera::device::BufferRequest;
using aidl::android::hardware::camera::device::BufferRequestStatus;
using aidl::android::hardware::camera::device::BufferStatus;
using aidl::android::hardware::camera::device::CaptureRequest;
using aidl::android::hardware::camera::device::CaptureResult;
using aidl::android::hardware::camera::device::ConfigureStreamsRet;
using aidl::android::hardware::camera::device::ErrorCode;
using aidl::android::hardware::camera::device::ErrorMsg;
using aidl::android::hardware::camera::device::HalStream;
using aidl::android::hardware::camera::device::NotifyMsg;
using aidl::android::hardware::camera::device::RequestTemplate;
using aidl::android::hardware::camera::device::ShutterMsg;
using aidl::android::hardware::camera::device::Stream;
using aidl::android::hardware::camera::device::StreamBuffer;
using aidl::android::hardware::camera::device::StreamBufferRequestError;
using aidl::android::hardware::camera::device::StreamBufferRet;
using aidl::android::hardware::camera::device::StreamBuffersVal;
using aidl::android::hardware::camera::device::StreamConfiguration;
using aidl::android::hardware::camera::device::StreamConfigurationMode;
using aidl::android::hardware::camera::device::StreamRotation;
using aidl::android::hardware::camera::device::StreamType;
using aidl::android::hardware::camera::provider::ICameraProvider;
using aidl::android::hardware::common::NativeHandle;
using aidl::android::hardware::common::fmq::SynchronizedReadWrite;

using ndk::ScopedAStatus;

// Util functions to convert the types between AIDL and Google Camera HAL.

// Conversions from HAL to AIDL

ScopedAStatus ConvertToAidlReturn(status_t hal_status);

status_t ConvertToAidlVendorTagSections(
    const std::vector<google_camera_hal::VendorTagSection>& hal_sections,
    std::vector<VendorTagSection>* aidl_sections);

status_t ConvertToAidlVendorTagType(
    google_camera_hal::CameraMetadataType hal_type,
    CameraMetadataType* aidl_type);

status_t ConvertToAidlResourceCost(
    const google_camera_hal::CameraResourceCost& hal_cost,
    CameraResourceCost* aidl_cost);

status_t ConvertToAidlHalStreamConfig(
    const google_camera_hal::ConfigureStreamsReturn& hal_config,
    ConfigureStreamsRet* aidl_config);

status_t ConverToAidlNotifyMessage(
    const google_camera_hal::NotifyMessage& hal_message,
    NotifyMsg* aidl_message);

// Convert from HAL CameraDeviceStatus to AIDL CameraDeviceStatus
// kNotPresent is converted to CameraDeviceStatus::NOT_PRESENT.
// kPresent is converted to CameraDeviceStatus::PRESENT.
// kEnumerating is converted to CameraDeviceStatus::ENUMERATING.
status_t ConvertToAidlCameraDeviceStatus(
    google_camera_hal::CameraDeviceStatus hal_camera_device_status,
    CameraDeviceStatus* aidl_camera_device_status);

// Convert from HAL TorchModeStatus to AIDL TorchModeStatus
// kNotAvailable is converted to TorchModeStatus::NOT_AVAILABLE.
// kAvailableOff is converted to TorchModeStatus::AVAILABLE_OFF.
// kAvailableOn is converted to TorchModeStatus::AVAILABLE_ON.
status_t ConvertToAidlTorchModeStatus(
    google_camera_hal::TorchModeStatus hal_torch_status,
    TorchModeStatus* aidl_torch_status);

// Convert a HAL request to a AIDL request.
status_t ConvertToAidlBufferRequest(
    const std::vector<google_camera_hal::BufferRequest>& hal_buffer_requests,
    std::vector<BufferRequest>* aidl_buffer_requests);

status_t ConvertToHalBufferStatus(BufferStatus aidl_status,
                                  google_camera_hal::BufferStatus* hal_status);

// Convert a HAL result to a AIDL result. It will try to write the result
// metadata to result_metadata_queue. If it fails, it will write the result
// metadata in aidl_result.
status_t ConvertToAidlCaptureResult(
    AidlMessageQueue<int8_t, SynchronizedReadWrite>* result_metadata_queue,
    std::unique_ptr<google_camera_hal::CaptureResult> hal_result,
    CaptureResult* aidl_result);

// Convert a HAL stream buffer to a AIDL aidl stream buffer.
status_t ConvertToAidlStreamBuffer(
    const google_camera_hal::StreamBuffer& hal_buffer,
    StreamBuffer* aidl_buffer);

// Conversions from AIDL to HAL.
status_t ConvertToHalTemplateType(
    RequestTemplate aidl_template,
    google_camera_hal::RequestTemplate* hal_template);

bool IsAidlNativeHandleNull(const NativeHandle& handle);

status_t ConvertToHalStreamBuffer(
    const StreamBuffer& aidl_buffer, google_camera_hal::StreamBuffer* hal_buffer,
    std::vector<native_handle_t*>* handles_to_delete);

status_t ConvertToHalMetadata(
    uint32_t message_queue_setting_size,
    AidlMessageQueue<int8_t, SynchronizedReadWrite>* request_metadata_queue,
    const std::vector<uint8_t>& request_settings,
    std::unique_ptr<google_camera_hal::HalCameraMetadata>* hal_metadata);

status_t ConvertToHalCaptureRequest(
    const CaptureRequest& aidl_request,
    AidlMessageQueue<int8_t, SynchronizedReadWrite>* request_metadata_queue,
    google_camera_hal::CaptureRequest* hal_request,
    std::vector<native_handle_t*>* native_handles_to_delete);

status_t ConvertToHalBufferCaches(
    const std::vector<BufferCache>& aidl_buffer_caches,
    std::vector<google_camera_hal::BufferCache>* hal_buffer_caches);

// Optimize sensor pixel modes list: If all streams' sensor pixel modes
// list contains DEFAULT, just override them to DEFAULT only.
void FixSensorPixelModesInStreamConfig(
    StreamConfiguration* out_aidl_stream_config);

status_t ConvertToHalStreamConfig(
    const StreamConfiguration& aidl_stream_config,
    google_camera_hal::StreamConfiguration* hal_stream_config);

status_t ConvertToHalStreamConfigurationMode(
    StreamConfigurationMode aidl_mode,
    google_camera_hal::StreamConfigurationMode* hal_mode);

status_t ConvertToHalStream(const Stream& aidl_stream,
                            google_camera_hal::Stream* hal_stream);

status_t ConvertToHalStreamRotation(
    StreamRotation aidl_stream_rotation,
    google_camera_hal::StreamRotation* hal_stream_rotation);

status_t ConvertToHalStreamType(StreamType aidl_stream_type,
                                google_camera_hal::StreamType* hal_stream_type);

status_t ConvertToHalBufferRequestStatus(
    const BufferRequestStatus& aidl_buffer_request_status,
    google_camera_hal::BufferRequestStatus* hal_buffer_request_status);

status_t ConvertToHalBufferReturnStatus(
    const StreamBufferRet& aidl_stream_buffer_return,
    google_camera_hal::BufferReturn* hal_buffer_return);

status_t ConvertToHalDeviceState(
    const int64_t aidl_device_state,
    google_camera_hal::DeviceState& hal_device_state);

}  // namespace aidl_utils
}  // namespace implementation
}  // namespace camera
}  // namespace hardware
}  // namespace android

#endif  // HARDWARE_GOOGLE_CAMERA_HAL_AIDL_SERVICE_AIDL_UTILS_H_
