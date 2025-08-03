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

#ifndef WIFICOND_SERVER_H_
#define WIFICOND_SERVER_H_

#include <memory>
#include <string>
#include <vector>

#include <android-base/macros.h>
#include <wifi_system/interface_tool.h>

#include "android/net/wifi/nl80211/BnWificond.h"
#include "android/net/wifi/nl80211/IApInterface.h"
#include "android/net/wifi/nl80211/IClientInterface.h"
#include "android/net/wifi/nl80211/IInterfaceEventCallback.h"
#include "android/net/wifi/nl80211/IWificondEventCallback.h"

#include "wificond/ap_interface_impl.h"
#include "wificond/client_interface_impl.h"

namespace android {
namespace wificond {

class NL80211Packet;
class NetlinkUtils;
class ScanUtils;

struct InterfaceInfo;

class Server : public android::net::wifi::nl80211::BnWificond {
 public:
  Server(std::unique_ptr<wifi_system::InterfaceTool> if_tool,
         NetlinkUtils* netlink_utils,
         ScanUtils* scan_utils);
  ~Server() override = default;

  android::binder::Status registerWificondEventCallback(
      const android::sp<android::net::wifi::nl80211::IWificondEventCallback>&
          callback) override;
  android::binder::Status unregisterWificondEventCallback(
      const android::sp<android::net::wifi::nl80211::IWificondEventCallback>&
          callback) override;

  android::binder::Status RegisterCallback(
      const android::sp<android::net::wifi::nl80211::IInterfaceEventCallback>&
          callback) override;
  android::binder::Status UnregisterCallback(
      const android::sp<android::net::wifi::nl80211::IInterfaceEventCallback>&
          callback) override;
  // Returns a vector of available frequencies for 2.4GHz channels.
  android::binder::Status getAvailable2gChannels(
      ::std::optional<::std::vector<int32_t>>* out_frequencies) override;
  // Returns a vector of available frequencies for 5GHz non-DFS channels.
  android::binder::Status getAvailable5gNonDFSChannels(
      ::std::optional<::std::vector<int32_t>>* out_frequencies) override;
  // Returns a vector of available frequencies for DFS channels.
  android::binder::Status getAvailableDFSChannels(
      ::std::optional<::std::vector<int32_t>>* out_frequencies) override;
  // Returns a vector of available frequencies for 6GHz channels.
  android::binder::Status getAvailable6gChannels(
      ::std::optional<::std::vector<int32_t>>* out_frequencies) override;
  // Returns a vector of available frequencies for 60GHz channels.
  android::binder::Status getAvailable60gChannels(
      ::std::optional<::std::vector<int32_t>>* out_frequencies) override;

  android::binder::Status createApInterface(
      const std::string& iface_name,
      android::sp<android::net::wifi::nl80211::IApInterface>*
          created_interface) override;

  android::binder::Status createClientInterface(
      const std::string& iface_name,
      android::sp<android::net::wifi::nl80211::IClientInterface>*
          created_interface) override;

  android::binder::Status tearDownApInterface(
      const std::string& iface_name,
      bool* out_success) override;

  android::binder::Status tearDownClientInterface(
      const std::string& iface_name,
      bool* out_success) override;

  android::binder::Status tearDownInterfaces() override;

  android::binder::Status GetClientInterfaces(
      std::vector<android::sp<android::IBinder>>* out_client_ifs) override;
  android::binder::Status GetApInterfaces(
      std::vector<android::sp<android::IBinder>>* out_ap_ifs) override;
  status_t dump(int fd, const Vector<String16>& args) override;

  // Returns device wiphy capabilities for an interface
  android::binder::Status getDeviceWiphyCapabilities(
      const std::string& iface_name,
      ::std::optional<net::wifi::nl80211::DeviceWiphyCapabilities>* capabilities) override;

  android::binder::Status notifyCountryCodeChanged() override;

 private:
  // Request interface information from kernel and setup local interface object.
  // This assumes that interface should be in STATION mode. Even if we setup
  // interface on behalf of createApInterace(), it is Hostapd that configure
  // the interface to Ap mode later.
  // Returns true on success, false otherwise.
  bool SetupInterface(const std::string& iface_name, InterfaceInfo* interface,
      uint32_t *wiphy_index);
  void LogSupportedBands(uint32_t wiphy_index);
  void OnRegDomainChanged(uint32_t wiphy_index, std::string& country_code);
  void handleCountryCodeChanged();
  void BroadcastClientInterfaceReady(
      android::sp<android::net::wifi::nl80211::IClientInterface> network_interface);
  void BroadcastApInterfaceReady(
      android::sp<android::net::wifi::nl80211::IApInterface> network_interface);
  void BroadcastClientInterfaceTornDown(
      android::sp<android::net::wifi::nl80211::IClientInterface> network_interface);
  void BroadcastApInterfaceTornDown(
      android::sp<android::net::wifi::nl80211::IApInterface> network_interface);
  void BroadcastRegDomainChanged();
  void MarkDownAllInterfaces();
  int FindWiphyIndex(const std::string& iface_name);
  bool GetBandInfo(int wiphy_index, BandInfo* band_info);
  int GetWiphyIndexFromBand(int band);
  void UpdateBandWiphyIndexMap(int wiphy_index);
  void EraseBandWiphyIndexMap(int wiphy_index);
  bool hasNoIfaceForWiphyIndex(int wiphy_index);

  const std::unique_ptr<wifi_system::InterfaceTool> if_tool_;
  NetlinkUtils* const netlink_utils_;
  ScanUtils* const scan_utils_;

  // Chips serves mutually exclusive bands.
  std::map<uint32_t, uint32_t> band_to_wiphy_index_map_;
  std::map<std::string, uint32_t> iface_to_wiphy_index_map_;
  std::map<std::string, std::unique_ptr<ApInterfaceImpl>> ap_interfaces_;
  std::map<std::string, std::unique_ptr<ClientInterfaceImpl>> client_interfaces_;
  std::vector<android::sp<android::net::wifi::nl80211::IInterfaceEventCallback>>
      interface_event_callbacks_;
  std::vector<android::sp<android::net::wifi::nl80211::IWificondEventCallback>>
      wificond_event_callbacks_;

  // Cached interface list from kernel for dumping.
  std::vector<InterfaceInfo> debug_interfaces_;

  std::string current_country_code_;

  DISALLOW_COPY_AND_ASSIGN(Server);
};

}  // namespace wificond
}  // namespace android

#endif  // WIFICOND_SERVER_H_
