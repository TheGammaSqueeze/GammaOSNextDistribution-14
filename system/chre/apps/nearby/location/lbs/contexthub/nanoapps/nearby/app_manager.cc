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

#include "location/lbs/contexthub/nanoapps/nearby/app_manager.h"

#include <inttypes.h>
#include <pb_decode.h>
#include <pb_encode.h>

#include <utility>

#include "chre_api/chre.h"
#include "location/lbs/contexthub/nanoapps/common/math/macros.h"
#ifdef ENABLE_EXTENSION
#include "location/lbs/contexthub/nanoapps/nearby/nearby_extension.h"
#include "location/lbs/contexthub/nanoapps/nearby/proto/nearby_extension.nanopb.h"
#endif
#include "location/lbs/contexthub/nanoapps/proto/filter.nanopb.h"
#include "third_party/contexthub/chre/util/include/chre/util/macros.h"
#include "third_party/contexthub/chre/util/include/chre/util/nanoapp/log.h"

#define LOG_TAG "[NEARBY][APP_MANAGER]"

namespace nearby {

using ::chre::Nanoseconds;

AppManager::AppManager() {
  fp_filter_cache_time_nanosec_ = chreGetTime();
#ifdef NEARBY_PROFILE
  ashProfileInit(
      &profile_data_, "[NEARBY_MATCH_ADV_PERF]", 1000 /* print_interval_ms */,
      false /* report_total_thread_cycles */, true /* printCsvFormat */);
#endif
}
bool AppManager::IsInitialized() {
  // AppManager initialized successfully only when BLE scan is available.
  return ble_scanner_.isAvailable();
}

void AppManager::HandleEvent(uint32_t sender_instance_id, uint16_t event_type,
                             const void *event_data) {
  Nanoseconds wakeup_start_ns = Nanoseconds(chreGetTime());
  LOGD("NanoApp wakeup starts by event %" PRIu16, event_type);
  UNUSED_VAR(sender_instance_id);
  const chreBleAdvertisementEvent *event;
  switch (event_type) {
    case CHRE_EVENT_MESSAGE_FROM_HOST:
      HandleMessageFromHost(
          static_cast<const chreMessageFromHostData *>(event_data));
      break;
    case CHRE_EVENT_BLE_ADVERTISEMENT:
      event = static_cast<const chreBleAdvertisementEvent *>(event_data);
      LOGD("Received %d BLE reports", event->numReports);
      // Print BLE advertisements for debug only.
      for (int i = 0; i < event->numReports; i++) {
        LOGD_SENSITIVE_INFO("Report %d has %d bytes service data", i,
                            event->reports[i].dataLength);
        LOGD_SENSITIVE_INFO("timestamp msec: %" PRIu64,
                            event->reports[i].timestamp / MSEC_TO_NANOS(1));
        LOGD_SENSITIVE_INFO("service data byte: ");
        LOGD_SENSITIVE_INFO("Tx power: %d", event->reports[i].txPower);
        LOGD_SENSITIVE_INFO("RSSI: %d", event->reports[i].rssi);
        for (int j = 0; j < 6; j++) {
          LOGD_SENSITIVE_INFO("direct address %d: %d", j,
                              event->reports[i].directAddress[j]);
          LOGD_SENSITIVE_INFO("address %d: %d", j,
                              event->reports[i].address[j]);
        }
        for (int j = 0; j < event->reports[i].dataLength; j++) {
          LOGD_SENSITIVE_INFO("%d", event->reports[i].data[j]);
        }
        // Adds advertise report to advertise report cache with deduplicating.
        adv_reports_cache_.Push(event->reports[i]);
      }

      // If batch scan is not supported, requests the match process here.
      // Otherwise, the match process is postponed to the batch complete event.
      if (!ble_scanner_.IsBatchSupported()) {
        HandleMatchAdvReports(adv_reports_cache_);
      }
      break;
    case CHRE_EVENT_BLE_FLUSH_COMPLETE:
      ble_scanner_.HandleEvent(event_type, event_data);
      break;
    case CHRE_EVENT_BLE_ASYNC_RESULT:
      ble_scanner_.HandleEvent(event_type, event_data);
      break;
    case CHRE_EVENT_BLE_BATCH_COMPLETE:
      LOGD("Received batch complete event");
      HandleMatchAdvReports(adv_reports_cache_);
      break;
    default:
      LOGD("Unknown event type: %d", event_type);
  }
  Nanoseconds wakeup_duration_ns = Nanoseconds(chreGetTime()) - wakeup_start_ns;
  LOGD("NanoApp wakeup ends after %" PRIu64 " ns by event %" PRIu16,
       wakeup_duration_ns.toRawNanoseconds(), event_type);
}

void AppManager::HandleMatchAdvReports(AdvReportCache &adv_reports_cache) {
#ifdef NEARBY_PROFILE
  ashProfileBegin(&profile_data_);
#endif
  chre::DynamicVector<nearby_BleFilterResult> filter_results;
  chre::DynamicVector<nearby_BleFilterResult> fp_filter_results;
  for (const auto &report : adv_reports_cache.GetAdvReports()) {
    if (report.dataLength == 0) {
      continue;
    }
    filter_.MatchBle(report, &filter_results, &fp_filter_results);
  }
  if (!filter_results.empty()) {
    LOGD("Send filter results back");
    SendBulkFilterResultsToHost(filter_results);
  }
  if (!fp_filter_results.empty()) {
    // FP host requires to receive scan results once during screen on
    if (screen_on_ && !fp_screen_on_sent_) {
      LOGD("Send FP filter results back");
      SendBulkFilterResultsToHost(fp_filter_results);
      fp_screen_on_sent_ = true;
    }
    LOGD("update FP filter cache");
    fp_filter_cache_results_ = std::move(fp_filter_results);
    fp_filter_cache_time_nanosec_ = chreGetTime();
  }
#ifdef ENABLE_EXTENSION
  // Matches extended filters.
  chre::DynamicVector<FilterExtensionResult> filter_extension_results;
  filter_extension_.Match(adv_reports_cache.GetAdvReports(),
                          &filter_extension_results,
                          &screen_on_filter_extension_results_);
  if (!filter_extension_results.empty()) {
    SendFilterExtensionResultToHost(filter_extension_results);
  }
  if (!screen_on_filter_extension_results_.empty()) {
    if (screen_on_) {
      LOGD("Send screen on filter extension results back");
      SendFilterExtensionResultToHost(screen_on_filter_extension_results_);
      screen_on_filter_extension_results_.clear();
    } else {
      for (auto &filter_result : screen_on_filter_extension_results_) {
        filter_result.RefreshIfNeeded();
      }
      LOGD("Updated filter extension result cache");
    }
  }
#endif
  adv_reports_cache.Clear();
#ifdef NEARBY_PROFILE
  ashProfileEnd(&profile_data_, nullptr /* output */);
#endif
}

void AppManager::HandleMessageFromHost(const chreMessageFromHostData *event) {
  LOGD("Got message from host with type %" PRIu32 " size %" PRIu32
       " hostEndpoint 0x%" PRIx16,
       event->messageType, event->messageSize, event->hostEndpoint);
  switch (event->messageType) {
    case lbs_FilterMessageType_MESSAGE_FILTERS:
      host_endpoint_ = event->hostEndpoint;
      RespondHostSetFilterRequest(filter_.Update(
          static_cast<const uint8_t *>(event->message), event->messageSize));
      fp_screen_on_sent_ = false;
      if (filter_.IsEmpty()) {
        ble_scanner_.ClearDefaultFilters();
      } else {
        ble_scanner_.SetDefaultFilters();
      }
      UpdateBleScanState();
      break;
    case lbs_FilterMessageType_MESSAGE_CONFIG:
      HandleHostConfigRequest(static_cast<const uint8_t *>(event->message),
                              event->messageSize);
      break;
#ifdef ENABLE_EXTENSION
    case lbs_FilterMessageType_MESSAGE_EXT_CONFIG_REQUEST:
      HandleHostExtConfigRequest(event);
      break;
#endif
  }
}

void AppManager::UpdateBleScanState() {
  if (!filter_.IsEmpty()
#ifdef ENABLE_EXTENSION
      || !filter_extension_.IsEmpty()
#endif
  ) {
    ble_scanner_.Restart();
  } else {
    ble_scanner_.Stop();
  }
}

void AppManager::RespondHostSetFilterRequest(const bool success) {
  auto resp_type = (success ? lbs_FilterMessageType_MESSAGE_SUCCESS
                            : lbs_FilterMessageType_MESSAGE_FAILURE);
  // TODO(b/238708594): change back to zero size response.
  void *msg_buf = chreHeapAlloc(3);
  LOGI("Acknowledge filter config.");
  if (chreSendMessageWithPermissions(
          msg_buf, 3, resp_type, host_endpoint_, CHRE_MESSAGE_PERMISSION_BLE,
          [](void *msg, size_t /*size*/) { chreHeapFree(msg); })) {
    LOGI("Succeeded to acknowledge Filter update");
  } else {
    LOGI("Failed to acknowledge Filter update");
  }
}

void AppManager::HandleHostConfigRequest(const uint8_t *message,
                                         uint32_t message_size) {
  nearby_BleConfig config = nearby_BleConfig_init_zero;
  pb_istream_t stream = pb_istream_from_buffer(message, message_size);
  if (!pb_decode(&stream, nearby_BleConfig_fields, &config)) {
    LOGE("failed to decode config message");
    return;
  }
  if (config.has_screen_on) {
    screen_on_ = config.screen_on;
    LOGD("received screen config %d", screen_on_);
    if (screen_on_) {
      fp_screen_on_sent_ = false;
      if (ble_scanner_.isScanning()) {
        ble_scanner_.Flush();
      }
      // TODO(b/255338604): used the default report delay value only because
      // FP offload scan doesn't use low latency report delay.
      // when the flushed packet droping issue is resolved, try to reconfigure
      // report delay for Nearby Presence.
      if (!fp_filter_cache_results_.empty()) {
        LOGD("send FP filter result from cache");
        uint64_t current_time = chreGetTime();
        if (current_time - fp_filter_cache_time_nanosec_ <
            fp_filter_cache_expire_nanosec_) {
          SendBulkFilterResultsToHost(fp_filter_cache_results_);
        } else {
          // nanoapp receives screen_on message for both screen_on and unlock
          // events. To send FP cache results on both events, keeps FP cache
          // results until cache timeout.
          fp_filter_cache_results_.clear();
        }
      }
#ifdef ENABLE_EXTENSION
      if (!screen_on_filter_extension_results_.empty()) {
        LOGD("try to send filter extension result from cache");
        SendFilterExtensionResultToHost(screen_on_filter_extension_results_);
        screen_on_filter_extension_results_.clear();
      }
#endif
    }
  }
  if (config.has_fast_pair_cache_expire_time_sec) {
    fp_filter_cache_expire_nanosec_ = config.fast_pair_cache_expire_time_sec;
  }
}

void AppManager::SendBulkFilterResultsToHost(
    const chre::DynamicVector<nearby_BleFilterResult> &filter_results) {
  size_t encoded_size = 0;
  if (!GetEncodedSizeFromFilterResults(filter_results, encoded_size)) {
    return;
  }
  if (encoded_size <= kFilterResultsBufSize) {
    SendFilterResultsToHost(filter_results);
    return;
  }
  LOGD("Encoded size %zu is larger than buffer size %zu. Sends each one",
       encoded_size, kFilterResultsBufSize);
  for (const auto &filter_result : filter_results) {
    SendFilterResultToHost(filter_result);
  }
}

void AppManager::SendFilterResultsToHost(
    const chre::DynamicVector<nearby_BleFilterResult> &filter_results) {
  void *msg_buf = chreHeapAlloc(kFilterResultsBufSize);
  if (msg_buf == nullptr) {
    LOGE("Failed to allocate message buffer of size %zu for dispatch.",
         kFilterResultsBufSize);
    return;
  }
  auto stream = pb_ostream_from_buffer(static_cast<pb_byte_t *>(msg_buf),
                                       kFilterResultsBufSize);
  size_t msg_size = 0;
  if (!EncodeFilterResults(filter_results, &stream, &msg_size)) {
    LOGE("Unable to encode protobuf for BleFilterResults, error %s",
         PB_GET_ERROR(&stream));
    chreHeapFree(msg_buf);
    return;
  }
  if (!chreSendMessageWithPermissions(
          msg_buf, msg_size, lbs_FilterMessageType_MESSAGE_FILTER_RESULTS,
          host_endpoint_, CHRE_MESSAGE_PERMISSION_BLE,
          [](void *msg, size_t size) {
            UNUSED_VAR(size);
            chreHeapFree(msg);
          })) {
    LOGE("Failed to send FilterResults");
  } else {
    LOGD("Successfully sent the filter result.");
  }
}

void AppManager::SendFilterResultToHost(
    const nearby_BleFilterResult &filter_result) {
  void *msg_buf = chreHeapAlloc(kFilterResultsBufSize);
  if (msg_buf == nullptr) {
    LOGE("Failed to allocate message buffer of size %zu for dispatch.",
         kFilterResultsBufSize);
    return;
  }
  auto stream = pb_ostream_from_buffer(static_cast<pb_byte_t *>(msg_buf),
                                       kFilterResultsBufSize);
  size_t msg_size = 0;
  if (!EncodeFilterResult(filter_result, &stream, &msg_size)) {
    LOGE("Unable to encode protobuf for BleFilterResult, error %s",
         PB_GET_ERROR(&stream));
    chreHeapFree(msg_buf);
    return;
  }
  if (!chreSendMessageWithPermissions(
          msg_buf, msg_size, lbs_FilterMessageType_MESSAGE_FILTER_RESULTS,
          host_endpoint_, CHRE_MESSAGE_PERMISSION_BLE,
          [](void *msg, size_t size) {
            UNUSED_VAR(size);
            chreHeapFree(msg);
          })) {
    LOGE("Failed to send FilterResults");
  } else {
    LOGD("Successfully sent the filter result.");
  }
}

// Struct to pass into EncodeFilterResult as *arg.
struct EncodeFieldResultsArg {
  size_t *msg_size;
  const chre::DynamicVector<nearby_BleFilterResult> *results;
};

// Callback to encode repeated result in nearby_BleFilterResults.
static bool EncodeFilterResultCallback(pb_ostream_t *stream,
                                       const pb_field_t *field,
                                       void *const *arg) {
  UNUSED_VAR(field);
  bool success = true;
  auto *encode_arg = static_cast<EncodeFieldResultsArg *>(*arg);

  for (const auto &result : *encode_arg->results) {
    if (!pb_encode_tag_for_field(
            stream,
            &nearby_BleFilterResults_fields[nearby_BleFilterResults_result_tag -
                                            1])) {
      return false;
    }
    success =
        pb_encode_submessage(stream, nearby_BleFilterResult_fields, &result);
  }
  if (success) {
    *encode_arg->msg_size = stream->bytes_written;
  }
  return success;
}

bool AppManager::GetEncodedSizeFromFilterResults(
    const chre::DynamicVector<nearby_BleFilterResult> &filter_results,
    size_t &encoded_size) {
  size_t total_encoded_size = 0;
  for (const auto &filter_result : filter_results) {
    constexpr size_t kHeaderSize = 2;
    size_t single_encoded_size = 0;
    if (!pb_get_encoded_size(&single_encoded_size,
                             nearby_BleFilterResult_fields, &filter_result)) {
      LOGE("Failed to get encoded size for BleFilterResult");
      return false;
    }
    total_encoded_size += single_encoded_size + kHeaderSize;
  }
  encoded_size = total_encoded_size;
  return true;
}

bool AppManager::EncodeFilterResults(
    const chre::DynamicVector<nearby_BleFilterResult> &filter_results,
    pb_ostream_t *stream, size_t *msg_size) {
  // Ensure stream is properly initialized before encoding.
  CHRE_ASSERT(stream->bytes_written == 0);
  *msg_size = 0;

  EncodeFieldResultsArg arg = {
      .msg_size = msg_size,
      .results = &filter_results,
  };
  nearby_BleFilterResults pb_results = {
      .result =
          {
              .funcs =
                  {
                      .encode = EncodeFilterResultCallback,
                  },
              .arg = &arg,
          },
  };
  return pb_encode(stream, nearby_BleFilterResults_fields, &pb_results);
}

bool AppManager::EncodeFilterResult(const nearby_BleFilterResult &filter_result,
                                    pb_ostream_t *stream, size_t *msg_size) {
  // Ensure stream is properly initialized before encoding.
  CHRE_ASSERT(stream->bytes_written == 0);
  if (!pb_encode_tag_for_field(
          stream,
          &nearby_BleFilterResults_fields[nearby_BleFilterResults_result_tag -
                                          1])) {
    return false;
  }
  if (!pb_encode_submessage(stream, nearby_BleFilterResult_fields,
                            &filter_result)) {
    return false;
  }
  *msg_size = stream->bytes_written;
  return true;
}

#ifdef ENABLE_EXTENSION
void AppManager::HandleHostExtConfigRequest(
    const chreMessageFromHostData *event) {
  chreHostEndpointInfo host_info;
  pb_istream_t stream =
      pb_istream_from_buffer(static_cast<const uint8_t *>(event->message),
                             static_cast<size_t>(event->messageSize));
  nearby_extension_ExtConfigRequest config =
      nearby_extension_ExtConfigRequest_init_default;
  nearby_extension_ExtConfigResponse config_response =
      nearby_extension_ExtConfigResponse_init_zero;

  if (!pb_decode(&stream, nearby_extension_ExtConfigRequest_fields, &config)) {
    LOGE("Failed to decode extended config msg: %s", PB_GET_ERROR(&stream));
  } else if (!chreGetHostEndpointInfo(event->hostEndpoint, &host_info)) {
    LOGE("Failed to get host info.");
    config_response.result = CHREX_NEARBY_RESULT_INTERNAL_ERROR;
  } else if (!host_info.isNameValid) {
    LOGE("Failed to get package name");
    config_response.result = CHREX_NEARBY_RESULT_UNKNOWN_PACKAGE;
  } else {
    LOGD("*** Receiving %s extended config ***",
         GetExtConfigNameFromTag(config.which_config));

    switch (config.which_config) {
      case nearby_extension_ExtConfigRequest_filter_config_tag:
        if (!HandleExtFilterConfig(host_info, config.config.filter_config,
                                   &config_response)) {
          LOGE("Failed to handle extended filter config");
        }
        break;
      case nearby_extension_ExtConfigRequest_service_config_tag:
        if (!HandleExtServiceConfig(host_info, config.config.service_config,
                                    &config_response)) {
          LOGE("Failed to handle extended service config");
        }
        break;
      default:
        LOGE("Unknown extended config %d", config.which_config);
        config_response.result = CHREX_NEARBY_RESULT_FEATURE_NOT_SUPPORTED;
        break;
    }
  }
  SendExtConfigResponseToHost(config.request_id, event->hostEndpoint,
                              config_response);
}

bool AppManager::HandleExtFilterConfig(
    const chreHostEndpointInfo &host_info,
    const nearby_extension_ExtConfigRequest_FilterConfig &config,
    nearby_extension_ExtConfigResponse *config_response) {
  chre::DynamicVector<chreBleGenericFilter> generic_filters;

  filter_extension_.Update(host_info, config, &generic_filters,
                           config_response);
  if (config_response->result != CHREX_NEARBY_RESULT_OK) {
    return false;
  }
  if (!ble_scanner_.UpdateFilters(host_info.hostEndpointId, &generic_filters)) {
    config_response->result = CHREX_NEARBY_RESULT_INTERNAL_ERROR;
    return false;
  }
  UpdateBleScanState();
  return true;
}

bool AppManager::HandleExtServiceConfig(
    const chreHostEndpointInfo &host_info,
    const nearby_extension_ExtConfigRequest_ServiceConfig &config,
    nearby_extension_ExtConfigResponse *config_response) {
  filter_extension_.ConfigureService(host_info, config, config_response);
  if (config_response->result != CHREX_NEARBY_RESULT_OK) {
    return false;
  }
  return true;
}

void AppManager::SendExtConfigResponseToHost(
    uint32_t request_id, uint16_t host_end_point,
    nearby_extension_ExtConfigResponse &config_response) {
  config_response.has_request_id = true;
  config_response.request_id = request_id;
  uint8_t *msg_buf = (uint8_t *)chreHeapAlloc(kFilterResultsBufSize);
  if (msg_buf == nullptr) {
    LOGE("Failed to allocate message buffer of size %zu for dispatch.",
         kFilterResultsBufSize);
    return;
  }
  size_t encoded_size;
  if (!FilterExtension::EncodeConfigResponse(
          config_response, ByteArray(msg_buf, kFilterResultsBufSize),
          &encoded_size)) {
    chreHeapFree(msg_buf);
    return;
  }
  if (chreSendMessageWithPermissions(
          msg_buf, encoded_size,
          lbs_FilterMessageType_MESSAGE_EXT_CONFIG_RESPONSE, host_end_point,
          CHRE_MESSAGE_PERMISSION_BLE,
          [](void *msg, size_t /*size*/) { chreHeapFree(msg); })) {
    LOGD("Successfully sent the extended config response for request %" PRIu32
         ".",
         request_id);
  } else {
    LOGE("Failed to send extended config response for request %" PRIu32 ".",
         request_id);
  }
}

void AppManager::SendFilterExtensionResultToHost(
    chre::DynamicVector<FilterExtensionResult> &filter_results) {
  for (auto &result : filter_results) {
    auto &reports = result.GetAdvReports();
    if (reports.empty()) {
      continue;
    }
    for (auto &report : reports) {
      size_t encoded_size;
      uint8_t *msg_buf = (uint8_t *)chreHeapAlloc(kFilterResultsBufSize);
      if (msg_buf == nullptr) {
        LOGE("Failed to allocate message buffer of size %zu for dispatch.",
             kFilterResultsBufSize);
        return;
      }
      if (!FilterExtension::EncodeAdvReport(
              report, ByteArray(msg_buf, kFilterResultsBufSize),
              &encoded_size)) {
        chreHeapFree(msg_buf);
        return;
      }
      if (chreSendMessageWithPermissions(
              msg_buf, encoded_size,
              lbs_FilterMessageType_MESSAGE_FILTER_RESULTS, result.end_point,
              CHRE_MESSAGE_PERMISSION_BLE,
              [](void *msg, size_t /*size*/) { chreHeapFree(msg); })) {
        LOGD("Successfully sent the filter extension result.");
      } else {
        LOGE("Failed to send filter extension result.");
      }
    }
  }
}

const char *AppManager::GetExtConfigNameFromTag(pb_size_t config_tag) {
  switch (config_tag) {
    case nearby_extension_ExtConfigRequest_filter_config_tag:
      return "FilterConfig";
    case nearby_extension_ExtConfigRequest_service_config_tag:
      return "ServiceConfig";
    default:
      return "Unknown";
  }
}
#endif

}  // namespace nearby
