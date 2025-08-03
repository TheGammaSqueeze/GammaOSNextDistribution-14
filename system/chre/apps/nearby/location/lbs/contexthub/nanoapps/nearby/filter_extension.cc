#include "location/lbs/contexthub/nanoapps/nearby/filter_extension.h"

#include <inttypes.h>
#include <pb_decode.h>
#include <pb_encode.h>

#include <cstddef>
#include <cstdint>
#include <utility>

#include "chre_api/chre.h"
#include "location/lbs/contexthub/nanoapps/nearby/nearby_extension.h"
#include "location/lbs/contexthub/nanoapps/nearby/proto/nearby_extension.nanopb.h"
#include "third_party/contexthub/chre/util/include/chre/util/nanoapp/log.h"

#define LOG_TAG "[NEARBY][FILTER_EXTENSION]"

namespace nearby {

const size_t kChreBleGenericFilterDataSize = 29;

constexpr nearby_extension_FilterResult kEmptyFilterResult =
    nearby_extension_FilterResult_init_zero;

void FilterExtension::Update(
    const chreHostEndpointInfo &host_info,
    const nearby_extension_ExtConfigRequest_FilterConfig &filter_config,
    chre::DynamicVector<chreBleGenericFilter> *generic_filters,
    nearby_extension_ExtConfigResponse *config_response) {
  LOGD("Update extension filter");
  const int32_t host_index = FindOrCreateHostIndex(host_info);
  if (host_index < 0) {
    LOGE("Failed to find or create the host.");
    return;
  }
  HostEndpointInfo &host = host_list_[static_cast<size_t>(host_index)];
  config_response->has_result = true;
  config_response->has_vendor_status = true;

  // Returns hardware filters.
  for (int i = 0; i < filter_config.hardware_filter_count; i++) {
    const nearby_extension_ChreBleGenericFilter &hw_filter =
        filter_config.hardware_filter[i];
    chreBleGenericFilter generic_filter;
    generic_filter.type = hw_filter.type;
    generic_filter.len = static_cast<uint8_t>(hw_filter.len);
    memcpy(generic_filter.data, hw_filter.data, kChreBleGenericFilterDataSize);
    memcpy(generic_filter.dataMask, hw_filter.data_mask,
           kChreBleGenericFilterDataSize);
    generic_filters->push_back(generic_filter);
  }
  chreBleScanFilter scan_filter;
  scan_filter.rssiThreshold = CHRE_BLE_RSSI_THRESHOLD_NONE;
  scan_filter.scanFilterCount = static_cast<uint8_t>(generic_filters->size());
  scan_filter.scanFilters = generic_filters->data();

  chrexNearbyExtendedFilterConfig config;
  config.data = filter_config.oem_filter;
  config.data_length = filter_config.oem_filter_length;
  host.cache_expire_ms = filter_config.cache_expire_ms;

  config_response->result = static_cast<int32_t>(
      chrexNearbySetExtendedFilterConfig(&host.host_info, &scan_filter, &config,
                                         &config_response->vendor_status));
  if (config_response->result != CHREX_NEARBY_RESULT_OK) {
    LOGE("Failed to config filters, result %" PRId32, config_response->result);
    host_list_.erase(static_cast<size_t>(host_index));
    return;
  }
  // Removes the host if both hardware and oem filters are empty.
  if (filter_config.hardware_filter_count == 0 &&
      filter_config.oem_filter_length == 0) {
    LOGD("Remove host: id (%d), package name (%s)",
         host.host_info.hostEndpointId,
         host.host_info.isNameValid ? host.host_info.packageName : "unknown");
    host_list_.erase(static_cast<size_t>(host_index));
  }
}

void FilterExtension::ConfigureService(
    const chreHostEndpointInfo &host_info,
    const nearby_extension_ExtConfigRequest_ServiceConfig &service_config,
    nearby_extension_ExtConfigResponse *config_response) {
  LOGD("Configure extension service");
  config_response->has_result = true;
  config_response->has_vendor_status = true;

  chrexNearbyExtendedServiceConfig config;
  config.data = service_config.data;
  config.data_length = service_config.data_length;

  config_response->result =
      static_cast<int32_t>(chrexNearbySetExtendedServiceConfig(
          &host_info, &config, &config_response->vendor_status));
}

int32_t FilterExtension::FindOrCreateHostIndex(
    const chreHostEndpointInfo &host_info) {
  for (size_t index = 0; index < host_list_.size(); index++) {
    if (host_info.hostEndpointId ==
        host_list_[index].host_info.hostEndpointId) {
      return static_cast<int32_t>(index);
    }
  }
  if (!host_list_.push_back(HostEndpointInfo(host_info))) {
    LOGE("Failed to add new host info.");
    return -1;
  }
  return static_cast<int32_t>(host_list_.size() - 1);
}

/* Adds a FilterExtensionResult (initialized by endpoint_id) to filter_results
 * if it has not been included in filter_results.
 * Returns the index of the entry.
 */
size_t AddToFilterResults(
    const HostEndpointInfo &host,
    chre::DynamicVector<FilterExtensionResult> *filter_results) {
  FilterExtensionResult result(host.host_info.hostEndpointId,
                               host.cache_expire_ms);
  size_t idx = filter_results->find(result);
  if (filter_results->size() == idx) {
    filter_results->push_back(std::move(result));
  }
  return idx;
}

void FilterExtension::Match(
    const chre::DynamicVector<chreBleAdvertisingReport> &ble_adv_list,
    chre::DynamicVector<FilterExtensionResult> *filter_results,
    chre::DynamicVector<FilterExtensionResult> *screen_on_filter_results) {
  for (const HostEndpointInfo &host : host_list_) {
    size_t idx = AddToFilterResults(host, filter_results);
    size_t screen_on_idx = AddToFilterResults(host, screen_on_filter_results);
    for (const auto &ble_adv_report : ble_adv_list) {
      switch (
          chrexNearbyMatchExtendedFilter(&host.host_info, &ble_adv_report)) {
        case CHREX_NEARBY_FILTER_ACTION_IGNORE:
          continue;
        case CHREX_NEARBY_FILTER_ACTION_DELIVER_ON_WAKE:
          LOGD("Include BLE report to screen on list.");
          (*screen_on_filter_results)[screen_on_idx].reports.Push(
              ble_adv_report);
          continue;
        case CHREX_NEARBY_FILTER_ACTION_DELIVER_IMMEDIATELY:
          LOGD("Include BLE report to immediate delivery list.");
          (*filter_results)[idx].reports.Push(ble_adv_report);
          continue;
      }
    }
  }
}

bool FilterExtension::EncodeConfigResponse(
    const nearby_extension_ExtConfigResponse &config_response,
    ByteArray data_buf, size_t *encoded_size) {
  if (!pb_get_encoded_size(encoded_size,
                           nearby_extension_ExtConfigResponse_fields,
                           &config_response)) {
    LOGE("Failed to get extended config response size.");
    return false;
  }
  pb_ostream_t ostream = pb_ostream_from_buffer(data_buf.data, data_buf.length);

  if (!pb_encode(&ostream, nearby_extension_ExtConfigResponse_fields,
                 &config_response)) {
    LOGE("Unable to encode protobuf for ExtConfigResponse, error %s",
         PB_GET_ERROR(&ostream));
    return false;
  }
  return true;
}

bool FilterExtension::Encode(
    const chre::DynamicVector<chreBleAdvertisingReport> &reports,
    ByteArray data_buf, size_t *encoded_size) {
  nearby_extension_FilterResult filter_result = kEmptyFilterResult;
  size_t idx = 0;
  for (const auto &report : reports) {
    nearby_extension_ChreBleAdvertisingReport &report_proto =
        filter_result.report[idx];
    report_proto.has_timestamp = true;
    report_proto.timestamp = report.timestamp;
    report_proto.has_event_type_and_data_status = true;
    report_proto.event_type_and_data_status = report.eventTypeAndDataStatus;
    report_proto.has_address = true;
    for (size_t i = 0; i < 6; i++) {
      report_proto.address[i] = report.address[i];
    }
    report_proto.has_tx_power = true;
    report_proto.tx_power = report.txPower;
    report_proto.has_rssi = true;
    report_proto.rssi = report.rssi;
    report_proto.has_data_length = true;
    report_proto.data_length = report.dataLength;
    if (report.dataLength > 0) {
      report_proto.has_data = true;
    }
    for (size_t i = 0; i < report.dataLength; i++) {
      report_proto.data[i] = report.data[i];
    }
    idx++;
  }
  filter_result.report_count = static_cast<pb_size_t>(idx);
  filter_result.has_error_code = true;
  filter_result.error_code = nearby_extension_FilterResult_ErrorCode_SUCCESS;

  if (!pb_get_encoded_size(encoded_size, nearby_extension_FilterResult_fields,
                           &filter_result)) {
    LOGE("Failed to get filter extension result size.");
    return false;
  }
  pb_ostream_t ostream = pb_ostream_from_buffer(data_buf.data, data_buf.length);

  if (!pb_encode(&ostream, nearby_extension_FilterResult_fields,
                 &filter_result)) {
    LOGE("Unable to encode protobuf for FilterExtensionResults, error %s",
         PB_GET_ERROR(&ostream));
    return false;
  }
  return true;
}

bool FilterExtension::EncodeAdvReport(chreBleAdvertisingReport &report,
                                      ByteArray data_buf,
                                      size_t *encoded_size) {
  nearby_extension_FilterResult filter_result = kEmptyFilterResult;
  nearby_extension_ChreBleAdvertisingReport &report_proto =
      filter_result.report[0];
  report_proto.has_timestamp = true;
  report_proto.timestamp =
      report.timestamp +
      static_cast<uint64_t>(chreGetEstimatedHostTimeOffset());
  report_proto.has_event_type_and_data_status = true;
  report_proto.event_type_and_data_status = report.eventTypeAndDataStatus;
  report_proto.has_address = true;
  for (size_t i = 0; i < 6; i++) {
    report_proto.address[i] = report.address[i];
  }
  report_proto.has_tx_power = true;
  report_proto.tx_power = report.txPower;
  report_proto.has_rssi = true;
  report_proto.rssi = report.rssi;
  report_proto.has_data_length = true;
  report_proto.data_length = report.dataLength;
  if (report.dataLength > 0) {
    report_proto.has_data = true;
  }
  for (size_t i = 0; i < report.dataLength; i++) {
    report_proto.data[i] = report.data[i];
  }
  filter_result.report_count = 1;
  filter_result.has_error_code = true;
  filter_result.error_code = nearby_extension_FilterResult_ErrorCode_SUCCESS;

  if (!pb_get_encoded_size(encoded_size, nearby_extension_FilterResult_fields,
                           &filter_result)) {
    LOGE("Failed to get filter extension result size.");
    return false;
  }
  pb_ostream_t ostream = pb_ostream_from_buffer(data_buf.data, data_buf.length);

  if (!pb_encode(&ostream, nearby_extension_FilterResult_fields,
                 &filter_result)) {
    LOGE("Unable to encode protobuf for FilterExtensionResults, error %s",
         PB_GET_ERROR(&ostream));
    return false;
  }
  return true;
}

}  // namespace nearby
