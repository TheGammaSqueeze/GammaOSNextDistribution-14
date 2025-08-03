/*
 * Copyright (C) 2021 The Android Open Source Project
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

#ifndef HARDWARE_GOOGLE_CAMERA_HAL_GOOGLE_CAMERA_HAL_ZSL_SNAPSHOT_CAPTURE_SESSION_H_
#define HARDWARE_GOOGLE_CAMERA_HAL_GOOGLE_CAMERA_HAL_ZSL_SNAPSHOT_CAPTURE_SESSION_H_

#include "basic_result_processor.h"
#include "camera_buffer_allocator_hwl.h"
#include "camera_device_session_hwl.h"
#include "capture_session.h"
#include "capture_session_utils.h"
#include "capture_session_wrapper_process_block.h"
#include "hwl_types.h"
#include "process_block.h"
#include "realtime_zsl_request_processor.h"
#include "realtime_zsl_result_processor.h"
#include "realtime_zsl_result_request_processor.h"
#include "request_processor.h"
#include "result_processor.h"
#include "snapshot_request_processor.h"
#include "snapshot_result_processor.h"
#include "zsl_result_dispatcher.h"

namespace android {
namespace google_camera_hal {

class CameraDeviceSession;

// ZslSnapshotCaptureSession implements a CaptureSession that contains two
// process chains
//
//  1.SnapshotRequestProcessor->SnapshotProcessBlock->SnapshotResultProcessor
//
//  2.RealtimeZslRequestProcessor->CaptureSessionWrapperProcessBlock->RealtimeZslResultRequestProcessor->DenoiseProcessBlock->BasicResultProcessor
//                                    ||  /\
//                                    \/  ||
//                             embedded capture session
class ZslSnapshotCaptureSession : public CaptureSession {
 public:
  // Return if the device session HWL and stream configuration are supported.
  static bool IsStreamConfigurationSupported(
      CameraDeviceSessionHwl* device_session_hwl,
      const StreamConfiguration& stream_config);

  // Create a ZslSnapshotCaptureSession.
  //
  // device_session_hwl is owned by the caller and must be valid during the
  // lifetime of ZslSnapshotCaptureSession.
  // stream_config is the stream configuration.
  // process_capture_result is the callback function to notify results.
  // notify is the callback function to notify messages.
  // hal_configured_streams will be filled with HAL configured streams.
  // camera_allocator_hwl is owned by the caller and must be valid during the
  // lifetime of ZslSnapshotCaptureSession.
  static std::unique_ptr<CaptureSession> Create(
      const StreamConfiguration& stream_config,
      const std::vector<ExternalCaptureSessionFactory*>&
          external_capture_session_entries,
      const std::vector<CaptureSessionEntryFuncs>& capture_session_entries,
      HwlSessionCallback hwl_session_callback,
      CameraBufferAllocatorHwl* camera_buffer_allocator_hwl,
      CameraDeviceSessionHwl* camera_device_session_hwl,
      std::vector<HalStream>* hal_configured_streams,
      ProcessCaptureResultFunc process_capture_result, NotifyFunc notify);

  virtual ~ZslSnapshotCaptureSession();

  // Override functions in CaptureSession start.
  status_t ProcessRequest(const CaptureRequest& request) override;

  status_t Flush() override;
  // Override functions in CaptureSession end.

 protected:
  ZslSnapshotCaptureSession(
      const std::vector<ExternalCaptureSessionFactory*>&
          external_capture_session_entries,
      const std::vector<CaptureSessionEntryFuncs>& capture_session_entries,
      HwlSessionCallback hwl_session_callback,
      CameraBufferAllocatorHwl* camera_buffer_allocator_hwl,
      CameraDeviceSessionHwl* camera_device_session_hwl);

 private:
  static constexpr uint32_t kPartialResult = 1;
  static constexpr int kAdditionalBufferNumber = 3;

  status_t Initialize(CameraDeviceSessionHwl* device_session_hwl,
                      const StreamConfiguration& stream_config,
                      ProcessCaptureResultFunc process_capture_result,
                      NotifyFunc notify,
                      std::vector<HalStream>* hal_configured_streams);

  status_t SetupSnapshotProcessChain(
      const StreamConfiguration& stream_config,
      ProcessCaptureResultFunc process_capture_result, NotifyFunc notify);

  status_t SetupRealtimeProcessChain(
      const StreamConfiguration& stream_config,
      ProcessCaptureResultFunc process_capture_result, NotifyFunc notify);

  status_t ConfigureSnapshotStreams(const StreamConfiguration& stream_config);

  // Configure streams for request processor and process block.
  status_t ConfigureStreams(const StreamConfiguration& stream_config,
                            RequestProcessor* request_processor,
                            ProcessBlock* process_block,
                            ProcessCaptureResultFunc process_capture_result,
                            NotifyFunc notify, int32_t& additiona_stream_id);

  // Build pipelines and return HAL configured streams.
  status_t BuildPipelines(ProcessBlock* process_block,
                          ProcessBlock* snapshot_process_block,
                          std::vector<HalStream>* hal_configured_streams);

  status_t PurgeHalConfiguredStream(
      const StreamConfiguration& stream_config,
      std::vector<HalStream>* hal_configured_streams);

  std::unique_ptr<ProcessBlock> CreateSnapshotProcessBlock();
  std::unique_ptr<ProcessBlock> CreateDenoiseProcessBlock();

  // Invoked when receiving a result from result processor.
  void ProcessCaptureResult(std::unique_ptr<CaptureResult> result);

  // Invoked when receiving a message from result processor.
  void NotifyHalMessage(const NotifyMessage& message);

  std::unique_ptr<InternalStreamManager> internal_stream_manager_;

  std::unique_ptr<RealtimeZslRequestProcessor> realtime_request_processor_;
  RealtimeZslResultRequestProcessor* realtime_zsl_result_request_processor_ =
      nullptr;
  // CaptureSessionWrapperProcessBlock will be owned and released by
  // RealtimeZslRequestProcessor.
  CaptureSessionWrapperProcessBlock* realtime_process_block_ = nullptr;

  std::unique_ptr<SnapshotRequestProcessor> snapshot_request_processor_;
  // SnapshotProcessBlock will be owned and released by
  // SnapshotRequestProcessor.
  ProcessBlock* snapshot_process_block_ = nullptr;
  // SnapshotResultProcessor will be owned and released by SnapshotProcessBlock.
  SnapshotResultProcessor* snapshot_result_processor_ = nullptr;

  BasicResultProcessor* basic_result_processor_ = nullptr;

  // Use this stream id to check the request is ZSL compatible
  int32_t hal_preview_stream_id_ = -1;

  int32_t additional_stream_id_ = -1;

  std::unique_ptr<ZslResultDispatcher> result_dispatcher_;

  std::mutex callback_lock_;
  // The following callbacks must be protected by callback_lock_.
  ProcessCaptureResultFunc process_capture_result_;
  NotifyFunc notify_;
  // For error notify to framework directly
  NotifyFunc device_session_notify_;

  const std::vector<ExternalCaptureSessionFactory*>&
      external_capture_session_entries_;
  const std::vector<CaptureSessionEntryFuncs>& capture_session_entries_;
  HwlSessionCallback hwl_session_callback_;
  CameraBufferAllocatorHwl* camera_buffer_allocator_hwl_ = nullptr;
  // device_session_hwl_ is owned by the client.
  CameraDeviceSessionHwl* camera_device_session_hwl_ = nullptr;

  std::vector<HalStream>* hal_config_ = nullptr;

  using GetProcessBlockFactoryFunc = ExternalProcessBlockFactory* (*)();
  GetProcessBlockFactoryFunc snapshot_process_block_factory_;
  // Opened library handles that should be closed on destruction
  void* snapshot_process_block_lib_handle_ = nullptr;

  GetProcessBlockFactoryFunc denoise_process_block_factory_;
  // Opened library handles that should be closed on destruction
  void* denoise_process_block_lib_handle_ = nullptr;

  // Partial result count reported by HAL
  uint32_t partial_result_count_ = 1;

  // Whether video software denoise is enabled
  bool video_sw_denoise_enabled_ = false;
};

}  // namespace google_camera_hal
}  // namespace android

#endif  // HARDWARE_GOOGLE_CAMERA_HAL_GOOGLE_CAMERA_HAL_ZSL_SNAPSHOT_CAPTURE_SESSION_H_