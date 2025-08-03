/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include "wificond/server.h"

#include <algorithm> // for std::find_if
#include <sstream>
#include <set>

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/strings.h>
#include <binder/IPCThreadState.h>
#include <binder/PermissionCache.h>

#include "wificond/logging_utils.h"
#include "wificond/net/netlink_utils.h"
#include "wificond/scanning/scan_utils.h"

using android::base::WriteStringToFd;
using android::binder::Status;
using android::sp;
using android::IBinder;
using android::net::wifi::nl80211::IApInterface;
using android::net::wifi::nl80211::IClientInterface;
using android::net::wifi::nl80211::IInterfaceEventCallback;
using android::net::wifi::nl80211::DeviceWiphyCapabilities;
using android::net::wifi::nl80211::IWificondEventCallback;
using android::wifi_system::InterfaceTool;

using std::endl;
using std::optional;
using std::placeholders::_1;
using std::placeholders::_2;
using std::set;
using std::string;
using std::stringstream;
using std::unique_ptr;
using std::vector;

namespace android {
namespace wificond {

namespace {

constexpr const char* kPermissionDump = "android.permission.DUMP";

}  // namespace

Server::Server(unique_ptr<InterfaceTool> if_tool,
               NetlinkUtils* netlink_utils,
               ScanUtils* scan_utils)
    : if_tool_(std::move(if_tool)),
      netlink_utils_(netlink_utils),
      scan_utils_(scan_utils) {
}

Status Server::registerWificondEventCallback(const sp<IWificondEventCallback>& callback) {
  for (const auto& it : wificond_event_callbacks_) {
    if (IInterface::asBinder(callback) == IInterface::asBinder(it)) {
      LOG(WARNING) << "Ignore duplicate wificond event callback registration";
      return Status::ok();
    }
  }
  LOG(INFO) << "New wificond event callback registered";
  wificond_event_callbacks_.push_back(callback);

  // Inform immediately about current country code
  if (!current_country_code_.empty())
    callback->OnRegDomainChanged(current_country_code_);

  return Status::ok();
}

Status Server::unregisterWificondEventCallback(const sp<IWificondEventCallback>& callback) {
  for (auto it = wificond_event_callbacks_.begin();
       it != wificond_event_callbacks_.end();
       it++) {
    if (IInterface::asBinder(callback) == IInterface::asBinder(*it)) {
      wificond_event_callbacks_.erase(it);
      LOG(INFO) << "Unregister interface event callback";
      return Status::ok();
    }
  }
  LOG(WARNING) << "Failed to find registered wificond event callback"
               << " to unregister";
  return Status::ok();
}

Status Server::RegisterCallback(const sp<IInterfaceEventCallback>& callback) {
  for (auto& it : interface_event_callbacks_) {
    if (IInterface::asBinder(callback) == IInterface::asBinder(it)) {
      LOG(WARNING) << "Ignore duplicate interface event callback registration";
      return Status::ok();
    }
  }
  LOG(INFO) << "New interface event callback registered";
  interface_event_callbacks_.push_back(callback);
  return Status::ok();
}

Status Server::UnregisterCallback(const sp<IInterfaceEventCallback>& callback) {
  for (auto it = interface_event_callbacks_.begin();
       it != interface_event_callbacks_.end();
       it++) {
    if (IInterface::asBinder(callback) == IInterface::asBinder(*it)) {
      interface_event_callbacks_.erase(it);
      LOG(INFO) << "Unregister interface event callback";
      return Status::ok();
    }
  }
  LOG(WARNING) << "Failed to find registered interface event callback"
               << " to unregister";
  return Status::ok();
}

Status Server::createApInterface(const std::string& iface_name,
                                 sp<IApInterface>* created_interface) {
  InterfaceInfo interface;
  uint32_t wiphy_index;

  if (!SetupInterface(iface_name, &interface, &wiphy_index)) {
    return Status::ok();  // Logging was done internally
  }

  LOG(INFO) << "createApInterface: wiphy_index " << wiphy_index << " iface_name " << iface_name;

  unique_ptr<ApInterfaceImpl> ap_interface(new ApInterfaceImpl(
      interface.name,
      interface.if_index,
      netlink_utils_,
      if_tool_.get()));
  *created_interface = ap_interface->GetBinder();
  BroadcastApInterfaceReady(ap_interface->GetBinder());
  ap_interfaces_[iface_name] = std::move(ap_interface);
  if (hasNoIfaceForWiphyIndex(wiphy_index)) {
    UpdateBandWiphyIndexMap(wiphy_index);
  } else {
    LOG(INFO) << "Band info for wiphy_index " << wiphy_index << " already available";
  }
  iface_to_wiphy_index_map_[iface_name] = wiphy_index;
  return Status::ok();
}

Status Server::tearDownApInterface(const std::string& iface_name,
                                   bool* out_success) {
  *out_success = false;
  const auto iter = ap_interfaces_.find(iface_name);
  if (iter != ap_interfaces_.end()) {
    BroadcastApInterfaceTornDown(iter->second->GetBinder());
    ap_interfaces_.erase(iter);
    *out_success = true;
  }

  const auto iter_wi = iface_to_wiphy_index_map_.find(iface_name);
  if (iter_wi != iface_to_wiphy_index_map_.end()) {
    int wiphy_index = iter_wi->second;
    LOG(INFO) << "tearDownApInterface: erasing wiphy_index for iface_name " << iface_name;
    iface_to_wiphy_index_map_.erase(iter_wi);
    if (hasNoIfaceForWiphyIndex(wiphy_index)) {
      EraseBandWiphyIndexMap(wiphy_index);
    } else {
      LOG(INFO) << "Band info for wiphy_index " << wiphy_index << " retained";
    }
  }

  return Status::ok();
}

bool Server::hasNoIfaceForWiphyIndex(int wiphy_index) {
  return std::find_if(
      iface_to_wiphy_index_map_.begin(),
      iface_to_wiphy_index_map_.end(),
      [wiphy_index](const auto& kv) { return kv.second == wiphy_index; })
      == iface_to_wiphy_index_map_.end();
}

Status Server::createClientInterface(const std::string& iface_name,
                                     sp<IClientInterface>* created_interface) {
  InterfaceInfo interface;
  uint32_t wiphy_index;

  if (!SetupInterface(iface_name, &interface, &wiphy_index)) {
    return Status::ok();  // Logging was done internally
  }

  LOG(INFO) << "createClientInterface: wiphy_index " << wiphy_index << " iface_name " << iface_name;

  unique_ptr<ClientInterfaceImpl> client_interface(new ClientInterfaceImpl(
      wiphy_index,
      interface.name,
      interface.if_index,
      interface.mac_address,
      if_tool_.get(),
      netlink_utils_,
      scan_utils_));
  *created_interface = client_interface->GetBinder();
  BroadcastClientInterfaceReady(client_interface->GetBinder());
  client_interfaces_[iface_name] = std::move(client_interface);
  if (hasNoIfaceForWiphyIndex(wiphy_index)) {
    UpdateBandWiphyIndexMap(wiphy_index);
  } else {
    LOG(INFO) << "Band info for wiphy_index " << wiphy_index << " already available";
  }
  iface_to_wiphy_index_map_[iface_name] = wiphy_index;

  return Status::ok();
}

Status Server::tearDownClientInterface(const std::string& iface_name,
                                       bool* out_success) {
  *out_success = false;
  const auto iter = client_interfaces_.find(iface_name);
  if (iter != client_interfaces_.end()) {
    BroadcastClientInterfaceTornDown(iter->second->GetBinder());
    client_interfaces_.erase(iter);
    *out_success = true;
  }

  const auto iter_wi = iface_to_wiphy_index_map_.find(iface_name);
  if (iter_wi != iface_to_wiphy_index_map_.end()) {
    int wiphy_index = iter_wi->second;
    LOG(INFO) << "tearDownClientInterface: erasing wiphy_index for iface_name " << iface_name;
    iface_to_wiphy_index_map_.erase(iter_wi);
    if (hasNoIfaceForWiphyIndex(wiphy_index)) {
      EraseBandWiphyIndexMap(wiphy_index);
    } else {
      LOG(INFO) << "Band info for wiphy_index " << wiphy_index << " retained";
    }
  }

  return Status::ok();
}

Status Server::tearDownInterfaces() {
  for (auto& it : client_interfaces_) {
    BroadcastClientInterfaceTornDown(it.second->GetBinder());
  }
  client_interfaces_.clear();

  for (auto& it : ap_interfaces_) {
    BroadcastApInterfaceTornDown(it.second->GetBinder());
  }
  ap_interfaces_.clear();

  MarkDownAllInterfaces();

  for (auto& it : iface_to_wiphy_index_map_) {
    netlink_utils_->UnsubscribeRegDomainChange(it.second);
    EraseBandWiphyIndexMap(it.second);
  }
  iface_to_wiphy_index_map_.clear();

  return Status::ok();
}

Status Server::GetClientInterfaces(vector<sp<IBinder>>* out_client_interfaces) {
  vector<sp<android::IBinder>> client_interfaces_binder;
  for (auto& it : client_interfaces_) {
    out_client_interfaces->push_back(asBinder(it.second->GetBinder()));
  }
  return binder::Status::ok();
}

Status Server::GetApInterfaces(vector<sp<IBinder>>* out_ap_interfaces) {
  vector<sp<IBinder>> ap_interfaces_binder;
  for (auto& it : ap_interfaces_) {
    out_ap_interfaces->push_back(asBinder(it.second->GetBinder()));
  }
  return binder::Status::ok();
}

status_t Server::dump(int fd, const Vector<String16>& /*args*/) {
  if (!PermissionCache::checkCallingPermission(String16(kPermissionDump))) {
    IPCThreadState* ipc = android::IPCThreadState::self();
    LOG(ERROR) << "Caller (uid: " << ipc->getCallingUid()
               << ") is not permitted to dump wificond state";
    return PERMISSION_DENIED;
  }

  stringstream ss;
  ss << "Cached interfaces list from kernel message: " << endl;
  for (const auto& iface : debug_interfaces_) {
    ss << "Interface index: " << iface.if_index
       << ", name: " << iface.name
       << ", wiphy index: " << iface.wiphy_index
       << ", mac address: "
       << LoggingUtils::GetMacString(iface.mac_address) << endl;
  }

  string country_code;
  uint32_t wiphy_index;
  if (netlink_utils_->GetWiphyIndex(&wiphy_index) &&
      netlink_utils_->GetCountryCode(wiphy_index, &country_code)) {
    ss << "Current country code from kernel: " << country_code << endl;
  } else {
    ss << "Failed to get country code from kernel." << endl;
  }

  for (const auto& iface : client_interfaces_) {
    iface.second->Dump(&ss);
  }

  for (const auto& iface : ap_interfaces_) {
    iface.second->Dump(&ss);
  }

  ss << "Channel Type / Wiphy Index Mapping:" << endl;
  for (const auto& it : band_to_wiphy_index_map_) {
    ss << "Channel type " << it.first << ": " << it.second << endl;
  }

  if (!WriteStringToFd(ss.str(), fd)) {
    PLOG(ERROR) << "Failed to dump state to fd " << fd;
    return FAILED_TRANSACTION;
  }

  return OK;
}

void Server::MarkDownAllInterfaces() {
  std::string iface_name;

  for (auto& it : iface_to_wiphy_index_map_) {
    iface_name = it.first;
    if_tool_->SetUpState(iface_name.c_str(), false);
  }
}

Status Server::getAvailable2gChannels(
    std::optional<vector<int32_t>>* out_frequencies) {

  int wiphy_index = GetWiphyIndexFromBand(NL80211_BAND_2GHZ);
  BandInfo band_info;

  if (!GetBandInfo(wiphy_index, &band_info)) {
    out_frequencies->reset();
    return Status::ok();
  }

  if (band_info.band_2g.size() == 0)
    out_frequencies->reset();
  else
    out_frequencies->emplace(band_info.band_2g.begin(), band_info.band_2g.end());
  return Status::ok();
}

Status Server::getAvailable5gNonDFSChannels(
    std::optional<vector<int32_t>>* out_frequencies) {
  int wiphy_index = GetWiphyIndexFromBand(NL80211_BAND_5GHZ);
  BandInfo band_info;
  if (!GetBandInfo(wiphy_index, &band_info)) {
    out_frequencies->reset();
    return Status::ok();
  }

  if (band_info.band_5g.size() == 0)
    out_frequencies->reset();
  else
    out_frequencies->emplace(band_info.band_5g.begin(), band_info.band_5g.end());
  return Status::ok();
}

Status Server::getAvailableDFSChannels(
    std::optional<vector<int32_t>>* out_frequencies) {
  int wiphy_index = GetWiphyIndexFromBand(NL80211_BAND_5GHZ);
  BandInfo band_info;
  if (!GetBandInfo(wiphy_index, &band_info)) {
    out_frequencies->reset();
    return Status::ok();
  }

  if (band_info.band_dfs.size() == 0)
    out_frequencies->reset();
  else
    out_frequencies->emplace(band_info.band_dfs.begin(),
                           band_info.band_dfs.end());
  return Status::ok();
}

Status Server::getAvailable6gChannels(
    std::optional<vector<int32_t>>* out_frequencies) {
  int wiphy_index = GetWiphyIndexFromBand(NL80211_BAND_6GHZ);
  BandInfo band_info;
  if (!GetBandInfo(wiphy_index, &band_info)) {
    out_frequencies->reset();
    return Status::ok();
  }

  if (band_info.band_6g.size() == 0)
    out_frequencies->reset();
  else
    out_frequencies->emplace(band_info.band_6g.begin(), band_info.band_6g.end());
  return Status::ok();
}

Status Server::getAvailable60gChannels(
    std::optional<vector<int32_t>>* out_frequencies) {
  int wiphy_index = GetWiphyIndexFromBand(NL80211_BAND_60GHZ);
  BandInfo band_info;
  if (!GetBandInfo(wiphy_index, &band_info)) {
    out_frequencies->reset();
    return Status::ok();
  }

  if (band_info.band_60g.size() == 0)
    out_frequencies->reset();
  else
    out_frequencies->emplace(
      band_info.band_60g.begin(), band_info.band_60g.end());

  return Status::ok();
}

Status Server::getDeviceWiphyCapabilities(
    const std::string& iface_name,
    std::optional<DeviceWiphyCapabilities>* capabilities) {
  int wiphy_index = FindWiphyIndex(iface_name);

  if (wiphy_index == -1) {
    LOG(ERROR) << "Failed to find iface_name " << iface_name;
    capabilities = nullptr;
    return Status::ok();
  }

  BandInfo band_info;
  ScanCapabilities scan_capabilities_ignored;
  WiphyFeatures wiphy_features;
  DriverCapabilities driver_capabilities;

  if (!netlink_utils_->GetWiphyInfo(wiphy_index, &band_info,
                                    &scan_capabilities_ignored,
                                    &wiphy_features,
                                    &driver_capabilities)) {
    LOG(ERROR) << "Failed to get wiphy info from kernel";
    capabilities = nullptr;
    return Status::ok();
  }

  capabilities->emplace();

  capabilities->value().is80211nSupported_  = band_info.is_80211n_supported;
  capabilities->value().is80211acSupported_ = band_info.is_80211ac_supported;
  capabilities->value().is80211axSupported_ = band_info.is_80211ax_supported;
  capabilities->value().is80211beSupported_ = band_info.is_80211be_supported;
  capabilities->value().is160MhzSupported_ = band_info.is_160_mhz_supported;
  capabilities->value().is80p80MhzSupported_ = band_info.is_80p80_mhz_supported;
  capabilities->value().is320MhzSupported_ = band_info.is_320_mhz_supported;
  capabilities->value().maxTxStreams_ = band_info.max_tx_streams;
  capabilities->value().maxRxStreams_ = band_info.max_rx_streams;
  capabilities->value().maxNumAkms_ = driver_capabilities.max_num_akms;

  return Status::ok();
}

bool Server::SetupInterface(const std::string& iface_name,
                            InterfaceInfo* interface,
                            uint32_t *wiphy_index) {
  if (!netlink_utils_->GetWiphyIndex(wiphy_index, iface_name)) {
    LOG(ERROR) << "Failed to get wiphy index";
    return false;
  }

  std::string country_code;
  if (!netlink_utils_->GetCountryCode(*wiphy_index, &country_code) ||
      country_code.empty()) {
    LOG(ERROR) << "Fail to get country code";
  } else {
    LOG(INFO) << "Current driver country code " << country_code;
    if (current_country_code_.empty() ||
        current_country_code_.compare(country_code) != 0) {
      current_country_code_ = country_code;
      BroadcastRegDomainChanged();
    }
  }

  // TODO: It may need to handle multi-chips case to get multi-wiphy index and
  // register corresponding callback.
  netlink_utils_->SubscribeRegDomainChange(
          *wiphy_index,
           std::bind(&Server::OnRegDomainChanged,
           this,
           _1,
           _2));

  debug_interfaces_.clear();
  if (!netlink_utils_->GetInterfaces(*wiphy_index, &debug_interfaces_)) {
    LOG(ERROR) << "Failed to get interfaces info from kernel for iface_name " << iface_name << " wiphy_index " << *wiphy_index;
    return false;
  }

  for (const auto& iface : debug_interfaces_) {
    if (iface.name == iface_name) {
      *interface = iface;
      return true;
    }
  }

  LOG(ERROR) << "No usable interface found";
  return false;
}

void Server::handleCountryCodeChanged() {
  uint32_t wiphy_index;
  set<uint32_t> handled_wiphy_index;
  for (auto& it : client_interfaces_) {
    it.second->UpdateBandInfo();
    if (netlink_utils_->GetWiphyIndex(&wiphy_index, it.first)) {
      if (handled_wiphy_index.find(wiphy_index) == handled_wiphy_index.end()) {
        UpdateBandWiphyIndexMap(wiphy_index);
        LogSupportedBands(wiphy_index);
        handled_wiphy_index.insert(wiphy_index);
      }
    }
  }
  for (auto& it : ap_interfaces_) {
    if (netlink_utils_->GetWiphyIndex(&wiphy_index, it.first)) {
      if (handled_wiphy_index.find(wiphy_index) == handled_wiphy_index.end()) {
        UpdateBandWiphyIndexMap(wiphy_index);
        LogSupportedBands(wiphy_index);
        handled_wiphy_index.insert(wiphy_index);
      }
    }
  }
}

void Server::OnRegDomainChanged(uint32_t wiphy_index, std::string& country_code) {
  if (country_code.empty()) {
    LOG(DEBUG) << "Regulatory domain changed with empty country code (world mode?)";
    if (!netlink_utils_->GetCountryCode(wiphy_index, &current_country_code_)) {
        LOG(ERROR) << "Fail to get country code on wiphy_index:" << wiphy_index;
    }
  } else {
      current_country_code_ = country_code;
  }
  if (!current_country_code_.empty()) {
      LOG(INFO) << "Regulatory domain changed to country: " << current_country_code_
                << " on wiphy_index: " << wiphy_index;
      BroadcastRegDomainChanged();
  }
  // Sometimes lower layer sends stale wiphy index when there are no
  // interfaces. So update band - wiphy index mapping only if an
  // interface exists
  if (!hasNoIfaceForWiphyIndex(wiphy_index)) {
    handleCountryCodeChanged();
  }
}

android::binder::Status Server::notifyCountryCodeChanged() {
  LOG(INFO) << "Receive notifyCountryCodeChanged";
  handleCountryCodeChanged();
  return Status::ok();
}

void Server::LogSupportedBands(uint32_t wiphy_index) {
  BandInfo band_info;
  ScanCapabilities scan_capabilities;
  WiphyFeatures wiphy_features;
  DriverCapabilities driver_capabilities_ignored;
  netlink_utils_->GetWiphyInfo(wiphy_index,
                               &band_info,
                               &scan_capabilities,
                               &wiphy_features,
                               &driver_capabilities_ignored);

  stringstream ss;
  for (unsigned int i = 0; i < band_info.band_2g.size(); i++) {
    ss << " " << band_info.band_2g[i];
  }
  LOG(INFO) << "2.4Ghz frequencies:"<< ss.str();
  ss.str("");

  for (unsigned int i = 0; i < band_info.band_5g.size(); i++) {
    ss << " " << band_info.band_5g[i];
  }
  LOG(INFO) << "5Ghz non-DFS frequencies:"<< ss.str();
  ss.str("");

  for (unsigned int i = 0; i < band_info.band_dfs.size(); i++) {
    ss << " " << band_info.band_dfs[i];
  }
  LOG(INFO) << "5Ghz DFS frequencies:"<< ss.str();
  ss.str("");

  for (unsigned int i = 0; i < band_info.band_6g.size(); i++) {
    ss << " " << band_info.band_6g[i];
  }
  LOG(INFO) << "6Ghz frequencies:"<< ss.str();
  ss.str("");

  for (unsigned int i = 0; i < band_info.band_60g.size(); i++) {
    ss << " " << band_info.band_60g[i];
  }
  LOG(INFO) << "60Ghz frequencies:"<< ss.str();
}

void Server::BroadcastClientInterfaceReady(
    sp<IClientInterface> network_interface) {
  for (auto& it : interface_event_callbacks_) {
    it->OnClientInterfaceReady(network_interface);
  }
}

void Server::BroadcastApInterfaceReady(
    sp<IApInterface> network_interface) {
  for (auto& it : interface_event_callbacks_) {
    it->OnApInterfaceReady(network_interface);
  }
}

void Server::BroadcastClientInterfaceTornDown(
    sp<IClientInterface> network_interface) {
  for (auto& it : interface_event_callbacks_) {
    it->OnClientTorndownEvent(network_interface);
  }
}

void Server::BroadcastApInterfaceTornDown(
    sp<IApInterface> network_interface) {
  for (auto& it : interface_event_callbacks_) {
    it->OnApTorndownEvent(network_interface);
  }
}

void Server::BroadcastRegDomainChanged() {
  for (const auto& it : wificond_event_callbacks_) {
    it->OnRegDomainChanged(current_country_code_);
  }
}

int Server::FindWiphyIndex(
    const std::string& iface_name) {
  int wiphy_index = -1;

  for (auto& it : iface_to_wiphy_index_map_) {
    if (it.first == iface_name) {
      wiphy_index = it.second;
      break;
    }
  }

  return wiphy_index;
}

bool Server::GetBandInfo(
    int wiphy_index,
    BandInfo* band_info) {

  if (wiphy_index == -1) return false;

  ScanCapabilities scan_capabilities_ignored;
  WiphyFeatures wiphy_features_ignored;
  DriverCapabilities driver_capabilities_ignored;

  if (!netlink_utils_->GetWiphyInfo(wiphy_index, band_info,
                                    &scan_capabilities_ignored,
                                    &wiphy_features_ignored,
                                    &driver_capabilities_ignored)) {
    LOG(ERROR) << "Failed to get wiphy index " << wiphy_index << " info from kernel";
    return false;
  }

  return true;
}

int Server::GetWiphyIndexFromBand(int band) {
    auto iter = band_to_wiphy_index_map_.find(band);
    return (iter != band_to_wiphy_index_map_.end()) ? iter->second : -1;
}

void Server::UpdateBandWiphyIndexMap(int wiphy_index) {
  LOG(DEBUG) << "UpdateBandWiphyIndexMap for wiphy_index " << wiphy_index;
  BandInfo band_info;
  if (!GetBandInfo(wiphy_index, &band_info)) return;

  if (band_info.band_2g.size() != 0
      && band_to_wiphy_index_map_.find(NL80211_BAND_2GHZ) == band_to_wiphy_index_map_.end()) {
    band_to_wiphy_index_map_[NL80211_BAND_2GHZ] = wiphy_index;
    LOG(INFO) << "add channel type " << NL80211_BAND_2GHZ
               << " support at wiphy index: " << wiphy_index;
  }
  if (band_info.band_5g.size() != 0
      && band_to_wiphy_index_map_.find(NL80211_BAND_5GHZ) == band_to_wiphy_index_map_.end()) {
    band_to_wiphy_index_map_[NL80211_BAND_5GHZ] = wiphy_index;
    LOG(INFO) << "add channel type " << NL80211_BAND_5GHZ
               << " support at wiphy index: " << wiphy_index;
  }
  if (band_info.band_dfs.size() != 0
      && band_to_wiphy_index_map_.find(NL80211_BAND_5GHZ) == band_to_wiphy_index_map_.end()) {
    band_to_wiphy_index_map_[NL80211_BAND_5GHZ] = wiphy_index;
    LOG(INFO) << "add channel type " << NL80211_BAND_5GHZ
               << " support at wiphy index: " << wiphy_index;
  }
  if (band_info.band_6g.size() != 0
      && band_to_wiphy_index_map_.find(NL80211_BAND_6GHZ) == band_to_wiphy_index_map_.end()) {
    band_to_wiphy_index_map_[NL80211_BAND_6GHZ] = wiphy_index;
    LOG(INFO) << "add channel type " << NL80211_BAND_6GHZ
               << " support at wiphy index: " << wiphy_index;
  }
  if (band_info.band_60g.size() != 0
      && band_to_wiphy_index_map_.find(NL80211_BAND_60GHZ) == band_to_wiphy_index_map_.end()) {
    band_to_wiphy_index_map_[NL80211_BAND_60GHZ] = wiphy_index;
    LOG(INFO) << "add channel type " << NL80211_BAND_60GHZ
               << " support at wiphy index: " << wiphy_index;
  }
}

void Server::EraseBandWiphyIndexMap(int wiphy_index) {
  LOG(DEBUG) << "EraseBandWiphyIndexMap for wiphy_index " << wiphy_index;
  for (auto it = band_to_wiphy_index_map_.begin();
      // end() is computed every iteration since erase() could invalidate it
      it != band_to_wiphy_index_map_.end();
      /* no increment */ ) {
    if (it->second == wiphy_index) {
      LOG(INFO) << "remove channel type " << it->first
                 << " support at wiphy index " << it->second;
      // erase returns iterator to element following erased element, or end() if the last element
      // was erased
      it = band_to_wiphy_index_map_.erase(it);
    } else {
      ++it;
    }
  }
}
}  // namespace wificond
}  // namespace android
