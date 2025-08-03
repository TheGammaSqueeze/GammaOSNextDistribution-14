/*
 * Copyright 2020 The Android Open Source Project
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

#include "main/shim/acl.h"

#include <base/location.h>
#include <base/strings/stringprintf.h>
#include <time.h>

#include <chrono>
#include <cstdint>
#include <functional>
#include <future>
#include <map>
#include <memory>
#include <optional>
#include <string>
#include <unordered_set>

#include "common/bind.h"
#include "common/interfaces/ILoggable.h"
#include "common/strings.h"
#include "common/sync_map_count.h"
#include "device/include/controller.h"
#include "hci/acl_manager.h"
#include "hci/acl_manager/acl_connection.h"
#include "hci/acl_manager/classic_acl_connection.h"
#include "hci/acl_manager/connection_management_callbacks.h"
#include "hci/acl_manager/le_acl_connection.h"
#include "hci/acl_manager/le_connection_management_callbacks.h"
#include "hci/address.h"
#include "hci/address_with_type.h"
#include "hci/class_of_device.h"
#include "hci/controller_interface.h"
#include "internal_include/bt_target.h"
#include "main/shim/dumpsys.h"
#include "main/shim/entry.h"
#include "main/shim/helpers.h"
#include "main/shim/stack.h"
#include "os/handler.h"
#include "osi/include/allocator.h"
#include "stack/acl/acl.h"
#include "stack/btm/btm_int_types.h"
#include "stack/btm/btm_sec_cb.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/btm_log_history.h"
#include "stack/include/main_thread.h"
#include "stack/l2cap/l2c_int.h"
#include "types/ble_address_with_type.h"
#include "types/raw_address.h"

extern tBTM_CB btm_cb;

using namespace bluetooth;

class ConnectAddressWithType : public bluetooth::common::IRedactableLoggable {
 public:
  explicit ConnectAddressWithType(hci::AddressWithType address_with_type)
      : address_(address_with_type.GetAddress()),
        type_(address_with_type.ToFilterAcceptListAddressType()) {}

  // TODO: remove this method
  std::string const ToString() const {
    std::stringstream ss;
    ss << address_.ToString() << "[" << FilterAcceptListAddressTypeText(type_)
       << "]";
    return ss.str();
  }

  std::string ToStringForLogging() const override {
    return ToString();
  }
  std::string ToRedactedStringForLogging() const override {
    std::stringstream ss;
    ss << address_.ToRedactedStringForLogging() << "["
       << FilterAcceptListAddressTypeText(type_) << "]";
    return ss.str();
  }
  bool operator==(const ConnectAddressWithType& rhs) const {
    return address_ == rhs.address_ && type_ == rhs.type_;
  }

 private:
  friend std::hash<ConnectAddressWithType>;
  hci::Address address_;
  hci::FilterAcceptListAddressType type_;
};

namespace std {
template <>
struct hash<ConnectAddressWithType> {
  std::size_t operator()(const ConnectAddressWithType& val) const {
    static_assert(sizeof(uint64_t) >=
                  (bluetooth::hci::Address::kLength +
                   sizeof(bluetooth::hci::FilterAcceptListAddressType)));
    uint64_t int_addr = 0;
    memcpy(reinterpret_cast<uint8_t*>(&int_addr), val.address_.data(),
           bluetooth::hci::Address::kLength);
    memcpy(reinterpret_cast<uint8_t*>(&int_addr) +
               bluetooth::hci::Address::kLength,
           &val.type_, sizeof(bluetooth::hci::FilterAcceptListAddressType));
    return std::hash<uint64_t>{}(int_addr);
  }
};
}  // namespace std

namespace {

constexpr uint32_t kRunicBjarkan = 0x0016D2;
constexpr uint32_t kRunicHagall = 0x0016BC;

using HciHandle = uint16_t;
using PageNumber = uint8_t;

using CreationTime = std::chrono::time_point<std::chrono::system_clock>;
using TeardownTime = std::chrono::time_point<std::chrono::system_clock>;

constexpr char kBtmLogTag[] = "ACL";

using SendDataUpwards = void (*const)(BT_HDR*);
using OnDisconnect = std::function<void(HciHandle, hci::ErrorCode reason)>;

constexpr char kConnectionDescriptorTimeFormat[] = "%Y-%m-%d %H:%M:%S";

constexpr unsigned MillisPerSecond = 1000;
std::string EpochMillisToString(long long time_ms) {
  time_t time_sec = time_ms / MillisPerSecond;
  struct tm tm;
  localtime_r(&time_sec, &tm);
  std::string s = common::StringFormatTime(kConnectionDescriptorTimeFormat, tm);
  return base::StringPrintf(
      "%s.%03u", s.c_str(),
      static_cast<unsigned int>(time_ms % MillisPerSecond));
}

inline bool IsRpa(const hci::AddressWithType address_with_type) {
  return address_with_type.GetAddressType() ==
             hci::AddressType::RANDOM_DEVICE_ADDRESS &&
         ((address_with_type.GetAddress().address.data()[5] & 0xc0) == 0x40);
}

class ShadowAcceptlist {
 public:
  ShadowAcceptlist(uint8_t max_acceptlist_size)
      : max_acceptlist_size_(max_acceptlist_size) {}

  bool Add(const hci::AddressWithType& address_with_type) {
    if (acceptlist_set_.size() == max_acceptlist_size_) {
      LOG_ERROR("Acceptlist is full size:%zu", acceptlist_set_.size());
      return false;
    }
    if (!acceptlist_set_.insert(ConnectAddressWithType(address_with_type))
             .second) {
      LOG_WARN("Attempted to add duplicate le address to acceptlist:%s",
               ADDRESS_TO_LOGGABLE_CSTR(address_with_type));
    }
    return true;
  }

  bool Remove(const hci::AddressWithType& address_with_type) {
    auto iter = acceptlist_set_.find(ConnectAddressWithType(address_with_type));
    if (iter == acceptlist_set_.end()) {
      LOG_WARN("Unknown device being removed from acceptlist:%s",
               ADDRESS_TO_LOGGABLE_CSTR(address_with_type));
      return false;
    }
    acceptlist_set_.erase(ConnectAddressWithType(*iter));
    return true;
  }

  std::unordered_set<ConnectAddressWithType> GetCopy() const {
    return acceptlist_set_;
  }

  bool IsFull() const {
    return acceptlist_set_.size() == static_cast<size_t>(max_acceptlist_size_);
  }

  void Clear() { acceptlist_set_.clear(); }

  uint8_t GetMaxSize() const { return max_acceptlist_size_; }

 private:
  uint8_t max_acceptlist_size_{0};
  std::unordered_set<ConnectAddressWithType> acceptlist_set_;
};

class ShadowAddressResolutionList {
 public:
  ShadowAddressResolutionList(uint8_t max_address_resolution_size)
      : max_address_resolution_size_(max_address_resolution_size) {}

  bool Add(const hci::AddressWithType& address_with_type) {
    if (address_resolution_set_.size() == max_address_resolution_size_) {
      LOG_ERROR("Address Resolution is full size:%zu",
                address_resolution_set_.size());
      return false;
    }
    if (!address_resolution_set_.insert(address_with_type).second) {
      LOG_WARN("Attempted to add duplicate le address to address_resolution:%s",
               ADDRESS_TO_LOGGABLE_CSTR(address_with_type));
    }
    return true;
  }

  bool Remove(const hci::AddressWithType& address_with_type) {
    auto iter = address_resolution_set_.find(address_with_type);
    if (iter == address_resolution_set_.end()) {
      LOG_WARN("Unknown device being removed from address_resolution:%s",
               ADDRESS_TO_LOGGABLE_CSTR(address_with_type));
      return false;
    }
    address_resolution_set_.erase(iter);
    return true;
  }

  std::unordered_set<hci::AddressWithType> GetCopy() const {
    return address_resolution_set_;
  }

  bool IsFull() const {
    return address_resolution_set_.size() ==
           static_cast<size_t>(max_address_resolution_size_);
  }

  size_t Size() const { return address_resolution_set_.size(); }

  void Clear() { address_resolution_set_.clear(); }

  uint8_t GetMaxSize() const { return max_address_resolution_size_; }

 private:
  uint8_t max_address_resolution_size_{0};
  std::unordered_set<hci::AddressWithType> address_resolution_set_;
};

struct ConnectionDescriptor {
  CreationTime creation_time_;
  TeardownTime teardown_time_;
  uint16_t handle_;
  bool is_locally_initiated_;
  hci::ErrorCode disconnect_reason_;
  ConnectionDescriptor(CreationTime creation_time, TeardownTime teardown_time,
                       uint16_t handle, bool is_locally_initiated,
                       hci::ErrorCode disconnect_reason)
      : creation_time_(creation_time),
        teardown_time_(teardown_time),
        handle_(handle),
        is_locally_initiated_(is_locally_initiated),
        disconnect_reason_(disconnect_reason) {}
  virtual std::string GetPrivateRemoteAddress() const = 0;
  virtual ~ConnectionDescriptor() {}
  std::string ToString() const {
    return base::StringPrintf(
        "peer:%s handle:0x%04x is_locally_initiated:%s"
        " creation_time:%s teardown_time:%s disconnect_reason:%s",
        GetPrivateRemoteAddress().c_str(), handle_,
        logbool(is_locally_initiated_).c_str(),
        common::StringFormatTimeWithMilliseconds(
            kConnectionDescriptorTimeFormat, creation_time_)
            .c_str(),
        common::StringFormatTimeWithMilliseconds(
            kConnectionDescriptorTimeFormat, teardown_time_)
            .c_str(),
        hci::ErrorCodeText(disconnect_reason_).c_str());
  }
};

struct ClassicConnectionDescriptor : public ConnectionDescriptor {
  const hci::Address remote_address_;
  ClassicConnectionDescriptor(const hci::Address& remote_address,
                              CreationTime creation_time,
                              TeardownTime teardown_time, uint16_t handle,
                              bool is_locally_initiated,
                              hci::ErrorCode disconnect_reason)
      : ConnectionDescriptor(creation_time, teardown_time, handle,
                             is_locally_initiated, disconnect_reason),
        remote_address_(remote_address) {}
  virtual std::string GetPrivateRemoteAddress() const {
    return ADDRESS_TO_LOGGABLE_CSTR(remote_address_);
  }
};

struct LeConnectionDescriptor : public ConnectionDescriptor {
  const hci::AddressWithType remote_address_with_type_;
  LeConnectionDescriptor(hci::AddressWithType& remote_address_with_type,
                         CreationTime creation_time, TeardownTime teardown_time,
                         uint16_t handle, bool is_locally_initiated,
                         hci::ErrorCode disconnect_reason)
      : ConnectionDescriptor(creation_time, teardown_time, handle,
                             is_locally_initiated, disconnect_reason),
        remote_address_with_type_(remote_address_with_type) {}
  std::string GetPrivateRemoteAddress() const {
    return ADDRESS_TO_LOGGABLE_CSTR(remote_address_with_type_);
  }
};

template <typename T>
class FixedQueue {
 public:
  explicit FixedQueue(size_t max_size) : max_size_(max_size) {}
  void Push(T element) {
    if (queue_.size() == max_size_) {
      queue_.pop_front();
    }
    queue_.push_back(std::move(element));
  }

  std::vector<std::string> ReadElementsAsString() const {
    std::vector<std::string> vector;
    for (auto& entry : queue_) {
      vector.push_back(entry->ToString());
    }
    return vector;
  }

 private:
  size_t max_size_{1};
  std::deque<T> queue_;
};

constexpr size_t kConnectionHistorySize = 40;

inline uint8_t LowByte(uint16_t val) { return val & 0xff; }
inline uint8_t HighByte(uint16_t val) { return val >> 8; }

void ValidateAclInterface(const shim::legacy::acl_interface_t& acl_interface) {
  ASSERT_LOG(acl_interface.on_send_data_upwards != nullptr,
             "Must provide to receive data on acl links");
  ASSERT_LOG(acl_interface.on_packets_completed != nullptr,
             "Must provide to receive completed packet indication");

  ASSERT_LOG(acl_interface.connection.classic.on_connected != nullptr,
             "Must provide to respond to successful classic connections");
  ASSERT_LOG(acl_interface.connection.classic.on_failed != nullptr,
             "Must provide to respond when classic connection attempts fail");
  ASSERT_LOG(
      acl_interface.connection.classic.on_disconnected != nullptr,
      "Must provide to respond when active classic connection disconnects");

  ASSERT_LOG(acl_interface.connection.le.on_connected != nullptr,
             "Must provide to respond to successful le connections");
  ASSERT_LOG(acl_interface.connection.le.on_failed != nullptr,
             "Must provide to respond when le connection attempts fail");
  ASSERT_LOG(acl_interface.connection.le.on_disconnected != nullptr,
             "Must provide to respond when active le connection disconnects");
}

}  // namespace

#define TRY_POSTING_ON_MAIN(cb, ...)                                   \
  do {                                                                 \
    if (cb == nullptr) {                                               \
      LOG_WARN("Dropping ACL event with no callback");                 \
    } else {                                                           \
      do_in_main_thread(FROM_HERE, base::BindOnce(cb, ##__VA_ARGS__)); \
    }                                                                  \
  } while (0)

constexpr HciHandle kInvalidHciHandle = 0xffff;

class ShimAclConnection {
 public:
  ShimAclConnection(const HciHandle handle, SendDataUpwards send_data_upwards,
                    os::Handler* handler,
                    hci::acl_manager::AclConnection::QueueUpEnd* queue_up_end,
                    CreationTime creation_time)
      : handle_(handle),
        handler_(handler),
        send_data_upwards_(send_data_upwards),
        queue_up_end_(queue_up_end),
        creation_time_(creation_time) {
    queue_up_end_->RegisterDequeue(
        handler_, common::Bind(&ShimAclConnection::data_ready_callback,
                               common::Unretained(this)));
  }

  virtual ~ShimAclConnection() {
    if (!queue_.empty())
      LOG_ERROR(
          "ACL cleaned up with non-empty queue handle:0x%04x stranded_pkts:%zu",
          handle_, queue_.size());
    ASSERT_LOG(is_disconnected_,
               "Shim Acl was not properly disconnected handle:0x%04x", handle_);
  }

  void EnqueuePacket(std::unique_ptr<packet::RawBuilder> packet) {
    // TODO Handle queue size exceeds some threshold
    queue_.push(std::move(packet));
    RegisterEnqueue();
  }

  std::unique_ptr<packet::BasePacketBuilder> handle_enqueue() {
    auto packet = std::move(queue_.front());
    queue_.pop();
    if (queue_.empty()) {
      UnregisterEnqueue();
    }
    return packet;
  }

  void data_ready_callback() {
    auto packet = queue_up_end_->TryDequeue();
    uint16_t length = packet->size();
    std::vector<uint8_t> preamble;
    preamble.push_back(LowByte(handle_));
    preamble.push_back(HighByte(handle_));
    preamble.push_back(LowByte(length));
    preamble.push_back(HighByte(length));
    BT_HDR* p_buf = MakeLegacyBtHdrPacket(std::move(packet), preamble);
    ASSERT_LOG(p_buf != nullptr,
               "Unable to allocate BT_HDR legacy packet handle:%04x", handle_);
    if (send_data_upwards_ == nullptr) {
      LOG_WARN("Dropping ACL data with no callback");
      osi_free(p_buf);
    } else if (do_in_main_thread(FROM_HERE,
                                 base::BindOnce(send_data_upwards_, p_buf)) !=
               BT_STATUS_SUCCESS) {
      osi_free(p_buf);
    }
  }

  virtual void InitiateDisconnect(hci::DisconnectReason reason) = 0;
  virtual bool IsLocallyInitiated() const = 0;

  CreationTime GetCreationTime() const { return creation_time_; }
  uint16_t Handle() const { return handle_; }

  void Shutdown() {
    Disconnect();
    LOG_INFO("Shutdown and disconnect ACL connection handle:0x%04x", handle_);
  }

 protected:
  const uint16_t handle_{kInvalidHciHandle};
  os::Handler* handler_;

  void UnregisterEnqueue() {
    if (!is_enqueue_registered_) return;
    is_enqueue_registered_ = false;
    queue_up_end_->UnregisterEnqueue();
  }

  void Disconnect() {
    if (is_disconnected_) {
      LOG_ERROR(
          "Cannot disconnect ACL multiple times handle:%04x creation_time:%s",
          handle_,
          common::StringFormatTimeWithMilliseconds(
              kConnectionDescriptorTimeFormat, creation_time_)
              .c_str());
      return;
    }
    is_disconnected_ = true;
    UnregisterEnqueue();
    queue_up_end_->UnregisterDequeue();
    if (!queue_.empty())
      LOG_WARN(
          "ACL disconnect with non-empty queue handle:%04x stranded_pkts::%zu",
          handle_, queue_.size());
  }

  virtual void ReadRemoteControllerInformation() = 0;

 private:
  SendDataUpwards send_data_upwards_;
  hci::acl_manager::AclConnection::QueueUpEnd* queue_up_end_;

  std::queue<std::unique_ptr<packet::RawBuilder>> queue_;
  bool is_enqueue_registered_{false};
  bool is_disconnected_{false};
  CreationTime creation_time_;

  void RegisterEnqueue() {
    ASSERT_LOG(!is_disconnected_,
               "Unable to send data over disconnected channel handle:%04x",
               handle_);
    if (is_enqueue_registered_) return;
    is_enqueue_registered_ = true;
    queue_up_end_->RegisterEnqueue(
        handler_, common::Bind(&ShimAclConnection::handle_enqueue,
                               common::Unretained(this)));
  }

  virtual void RegisterCallbacks() = 0;
};

class ClassicShimAclConnection
    : public ShimAclConnection,
      public hci::acl_manager::ConnectionManagementCallbacks {
 public:
  ClassicShimAclConnection(
      SendDataUpwards send_data_upwards, OnDisconnect on_disconnect,
      const shim::legacy::acl_classic_link_interface_t& interface,
      os::Handler* handler,
      std::unique_ptr<hci::acl_manager::ClassicAclConnection> connection,
      CreationTime creation_time)
      : ShimAclConnection(connection->GetHandle(), send_data_upwards, handler,
                          connection->GetAclQueueEnd(), creation_time),
        on_disconnect_(on_disconnect),
        interface_(interface),
        connection_(std::move(connection)) {}

  void RegisterCallbacks() override {
    connection_->RegisterCallbacks(this, handler_);
  }

  void ReadRemoteControllerInformation() override {
    connection_->ReadRemoteVersionInformation();
    connection_->ReadRemoteSupportedFeatures();
  }

  void OnConnectionPacketTypeChanged(uint16_t packet_type) override {
    TRY_POSTING_ON_MAIN(interface_.on_packet_type_changed, packet_type);
  }

  void OnAuthenticationComplete(hci::ErrorCode hci_status) override {
    TRY_POSTING_ON_MAIN(interface_.on_authentication_complete, handle_,
                        ToLegacyHciErrorCode(hci_status));
  }

  void OnEncryptionChange(hci::EncryptionEnabled enabled) override {
    bool is_enabled = (enabled == hci::EncryptionEnabled::ON ||
                       enabled == hci::EncryptionEnabled::BR_EDR_AES_CCM);
    TRY_POSTING_ON_MAIN(interface_.on_encryption_change, is_enabled);
  }

  void OnChangeConnectionLinkKeyComplete() override {
    TRY_POSTING_ON_MAIN(interface_.on_change_connection_link_key_complete);
  }

  void OnReadClockOffsetComplete(uint16_t /* clock_offset */) override {
    LOG_INFO("UNIMPLEMENTED");
  }

  void OnModeChange(hci::ErrorCode status, hci::Mode current_mode,
                    uint16_t interval) override {
    TRY_POSTING_ON_MAIN(interface_.on_mode_change, ToLegacyHciErrorCode(status),
                        handle_, ToLegacyHciMode(current_mode), interval);
  }

  void OnSniffSubrating(hci::ErrorCode hci_status,
                        uint16_t maximum_transmit_latency,
                        uint16_t maximum_receive_latency,
                        uint16_t minimum_remote_timeout,
                        uint16_t minimum_local_timeout) {
    TRY_POSTING_ON_MAIN(interface_.on_sniff_subrating,
                        ToLegacyHciErrorCode(hci_status), handle_,
                        maximum_transmit_latency, maximum_receive_latency,
                        minimum_remote_timeout, minimum_local_timeout);
  }

  void OnQosSetupComplete(hci::ServiceType /* service_type */,
                          uint32_t /* token_rate */,
                          uint32_t /* peak_bandwidth */, uint32_t /* latency */,
                          uint32_t /* delay_variation */) override {
    LOG_INFO("UNIMPLEMENTED");
  }

  void OnFlowSpecificationComplete(hci::FlowDirection /* flow_direction */,
                                   hci::ServiceType /* service_type */,
                                   uint32_t /* token_rate */,
                                   uint32_t /* token_bucket_size */,
                                   uint32_t /* peak_bandwidth */,
                                   uint32_t /* access_latency */) override {
    LOG_INFO("UNIMPLEMENTED");
  }

  void OnFlushOccurred() override { LOG_INFO("UNIMPLEMENTED"); }

  void OnRoleDiscoveryComplete(hci::Role /* current_role */) override {
    LOG_INFO("UNIMPLEMENTED");
  }

  void OnReadLinkPolicySettingsComplete(
      uint16_t /* link_policy_settings */) override {
    LOG_INFO("UNIMPLEMENTED");
  }

  void OnReadAutomaticFlushTimeoutComplete(
      uint16_t /* flush_timeout */) override {
    LOG_INFO("UNIMPLEMENTED");
  }

  void OnReadTransmitPowerLevelComplete(
      uint8_t /* transmit_power_level */) override {
    LOG_INFO("UNIMPLEMENTED");
  }

  void OnReadLinkSupervisionTimeoutComplete(
      uint16_t /* link_supervision_timeout */) override {
    LOG_INFO("UNIMPLEMENTED");
  }

  void OnReadFailedContactCounterComplete(
      uint16_t /* failed_contact_counter */) override {
    LOG_INFO("UNIMPLEMENTED");
  }

  void OnReadLinkQualityComplete(uint8_t /* link_quality */) override {
    LOG_INFO("UNIMPLEMENTED");
  }

  void OnReadAfhChannelMapComplete(
      hci::AfhMode /* afh_mode */,
      std::array<uint8_t, 10> /* afh_channel_map */) override {
    LOG_INFO("UNIMPLEMENTED");
  }

  void OnReadRssiComplete(uint8_t /* rssi */) override {
    LOG_INFO("UNIMPLEMENTED");
  }

  void OnReadClockComplete(uint32_t /* clock */,
                           uint16_t /* accuracy */) override {
    LOG_INFO("UNIMPLEMENTED");
  }

  void OnCentralLinkKeyComplete(hci::KeyFlag /* key_flag */) override {
    LOG_INFO("%s UNIMPLEMENTED", __func__);
  }

  void OnRoleChange(hci::ErrorCode hci_status, hci::Role new_role) override {
    TRY_POSTING_ON_MAIN(
        interface_.on_role_change, ToLegacyHciErrorCode(hci_status),
        ToRawAddress(connection_->GetAddress()), ToLegacyRole(new_role));
    BTM_LogHistory(kBtmLogTag, ToRawAddress(connection_->GetAddress()),
                   "Role change",
                   base::StringPrintf("classic New_role:%s status:%s",
                                      hci::RoleText(new_role).c_str(),
                                      hci::ErrorCodeText(hci_status).c_str()));
  }

  void OnDisconnection(hci::ErrorCode reason) override {
    Disconnect();
    on_disconnect_(handle_, reason);
  }

  void OnReadRemoteVersionInformationComplete(hci::ErrorCode hci_status,
                                              uint8_t lmp_version,
                                              uint16_t manufacturer_name,
                                              uint16_t sub_version) override {
    TRY_POSTING_ON_MAIN(interface_.on_read_remote_version_information_complete,
                        ToLegacyHciErrorCode(hci_status), handle_, lmp_version,
                        manufacturer_name, sub_version);
  }

  void OnReadRemoteSupportedFeaturesComplete(uint64_t features) override {
    TRY_POSTING_ON_MAIN(interface_.on_read_remote_supported_features_complete,
                        handle_, features);

    if (features & ((uint64_t(1) << 63))) {
      connection_->ReadRemoteExtendedFeatures(1);
      return;
    }
    LOG_DEBUG("Device does not support extended features");
  }

  void OnReadRemoteExtendedFeaturesComplete(uint8_t page_number,
                                            uint8_t max_page_number,
                                            uint64_t features) override {
    TRY_POSTING_ON_MAIN(interface_.on_read_remote_extended_features_complete,
                        handle_, page_number, max_page_number, features);

    // Supported features aliases to extended features page 0
    if (page_number == 0 && !(features & ((uint64_t(1) << 63)))) {
      LOG_DEBUG("Device does not support extended features");
      return;
    }

    if (max_page_number != 0 && page_number != max_page_number)
      connection_->ReadRemoteExtendedFeatures(page_number + 1);
  }

  hci::Address GetRemoteAddress() const { return connection_->GetAddress(); }

  void InitiateDisconnect(hci::DisconnectReason reason) override {
    connection_->Disconnect(reason);
  }

  void HoldMode(uint16_t max_interval, uint16_t min_interval) {
    ASSERT(connection_->HoldMode(max_interval, min_interval));
  }

  void SniffMode(uint16_t max_interval, uint16_t min_interval, uint16_t attempt,
                 uint16_t timeout) {
    ASSERT(
        connection_->SniffMode(max_interval, min_interval, attempt, timeout));
  }

  void ExitSniffMode() { ASSERT(connection_->ExitSniffMode()); }

  void SniffSubrating(uint16_t maximum_latency, uint16_t minimum_remote_timeout,
                      uint16_t minimum_local_timeout) {
    ASSERT(connection_->SniffSubrating(maximum_latency, minimum_remote_timeout,
                                       minimum_local_timeout));
  }

  void SetConnectionEncryption(hci::Enable is_encryption_enabled) {
    ASSERT(connection_->SetConnectionEncryption(is_encryption_enabled));
  }

  bool IsLocallyInitiated() const override {
    return connection_->locally_initiated_;
  }

 private:
  OnDisconnect on_disconnect_;
  const shim::legacy::acl_classic_link_interface_t interface_;
  std::unique_ptr<hci::acl_manager::ClassicAclConnection> connection_;
};

class LeShimAclConnection
    : public ShimAclConnection,
      public hci::acl_manager::LeConnectionManagementCallbacks {
 public:
  LeShimAclConnection(
      SendDataUpwards send_data_upwards, OnDisconnect on_disconnect,
      const shim::legacy::acl_le_link_interface_t& interface,
      os::Handler* handler,
      std::unique_ptr<hci::acl_manager::LeAclConnection> connection,
      std::chrono::time_point<std::chrono::system_clock> creation_time)
      : ShimAclConnection(connection->GetHandle(), send_data_upwards, handler,
                          connection->GetAclQueueEnd(), creation_time),
        on_disconnect_(on_disconnect),
        interface_(interface),
        connection_(std::move(connection)) {}

  void RegisterCallbacks() override {
    connection_->RegisterCallbacks(this, handler_);
  }

  void LeSubrateRequest(uint16_t subrate_min, uint16_t subrate_max,
                        uint16_t max_latency, uint16_t cont_num,
                        uint16_t sup_tout) {
    connection_->LeSubrateRequest(subrate_min, subrate_max, max_latency,
                                  cont_num, sup_tout);
  }

  void ReadRemoteControllerInformation() override {
    // TODO Issue LeReadRemoteFeatures Command
  }

  bluetooth::hci::AddressWithType GetLocalAddressWithType() {
    return connection_->GetLocalAddress();
  }

  bluetooth::hci::AddressWithType GetLocalOtaAddressWithType() {
    return connection_->GetLocalOtaAddress();
  }

  bluetooth::hci::AddressWithType GetPeerAddressWithType() {
    return connection_->GetPeerAddress();
  }

  bluetooth::hci::AddressWithType GetPeerOtaAddressWithType() {
    return connection_->GetPeerOtaAddress();
  }

  std::optional<uint8_t> GetAdvertisingSetConnectedTo() {
    return std::visit(
        [](auto&& data) {
          using T = std::decay_t<decltype(data)>;
          if constexpr (std::is_same_v<T, hci::acl_manager::DataAsPeripheral>) {
            return data.advertising_set_id;
          } else {
            return std::optional<uint8_t>{};
          }
        },
        connection_->GetRoleSpecificData());
  }

  void OnConnectionUpdate(hci::ErrorCode hci_status,
                          uint16_t connection_interval,
                          uint16_t connection_latency,
                          uint16_t supervision_timeout) {
    TRY_POSTING_ON_MAIN(
        interface_.on_connection_update, ToLegacyHciErrorCode(hci_status),
        handle_, connection_interval, connection_latency, supervision_timeout);
  }
  void OnDataLengthChange(uint16_t max_tx_octets, uint16_t max_tx_time,
                          uint16_t max_rx_octets, uint16_t max_rx_time) {
    TRY_POSTING_ON_MAIN(interface_.on_data_length_change, handle_,
                        max_tx_octets, max_tx_time, max_rx_octets, max_rx_time);
  }
  void OnLeSubrateChange(hci::ErrorCode hci_status, uint16_t subrate_factor,
                         uint16_t peripheral_latency,
                         uint16_t continuation_number,
                         uint16_t supervision_timeout) {
    TRY_POSTING_ON_MAIN(interface_.on_le_subrate_change, handle_,
                        subrate_factor, peripheral_latency, continuation_number,
                        supervision_timeout, ToLegacyHciErrorCode(hci_status));
  }

  void OnReadRemoteVersionInformationComplete(hci::ErrorCode hci_status,
                                              uint8_t lmp_version,
                                              uint16_t manufacturer_name,
                                              uint16_t sub_version) override {
    TRY_POSTING_ON_MAIN(interface_.on_read_remote_version_information_complete,
                        ToLegacyHciErrorCode(hci_status), handle_, lmp_version,
                        manufacturer_name, sub_version);
  }

  void OnLeReadRemoteFeaturesComplete(hci::ErrorCode /* hci_status */,
                                      uint64_t /* features */) {
    // TODO
  }

  void OnPhyUpdate(hci::ErrorCode hci_status, uint8_t tx_phy,
                   uint8_t rx_phy) override {
    TRY_POSTING_ON_MAIN(interface_.on_phy_update,
                        ToLegacyHciErrorCode(hci_status), handle_, tx_phy,
                        rx_phy);
  }

  void OnDisconnection(hci::ErrorCode reason) {
    Disconnect();
    on_disconnect_(handle_, reason);
  }

  hci::AddressWithType GetRemoteAddressWithType() const {
    return connection_->GetRemoteAddress();
  }

  void InitiateDisconnect(hci::DisconnectReason reason) override {
    connection_->Disconnect(reason);
  }

  bool IsLocallyInitiated() const override {
    return connection_->locally_initiated_;
  }

  bool IsInFilterAcceptList() const {
    return connection_->IsInFilterAcceptList();
  }

 private:
  OnDisconnect on_disconnect_;
  const shim::legacy::acl_le_link_interface_t interface_;
  std::unique_ptr<hci::acl_manager::LeAclConnection> connection_;
};

struct shim::legacy::Acl::impl {
  impl(uint8_t max_acceptlist_size, uint8_t max_address_resolution_size)
      : shadow_acceptlist_(ShadowAcceptlist(max_acceptlist_size)),
        shadow_address_resolution_list_(
            ShadowAddressResolutionList(max_address_resolution_size)) {}

  std::map<HciHandle, std::unique_ptr<ClassicShimAclConnection>>
      handle_to_classic_connection_map_;
  std::map<HciHandle, std::unique_ptr<LeShimAclConnection>>
      handle_to_le_connection_map_;

  SyncMapCount<std::string> classic_acl_disconnect_reason_;
  SyncMapCount<std::string> le_acl_disconnect_reason_;

  FixedQueue<std::unique_ptr<ConnectionDescriptor>> connection_history_ =
      FixedQueue<std::unique_ptr<ConnectionDescriptor>>(kConnectionHistorySize);

  ShadowAcceptlist shadow_acceptlist_;
  ShadowAddressResolutionList shadow_address_resolution_list_;

  bool IsClassicAcl(HciHandle handle) {
    return handle_to_classic_connection_map_.find(handle) !=
           handle_to_classic_connection_map_.end();
  }

  void EnqueueClassicPacket(HciHandle handle,
                            std::unique_ptr<packet::RawBuilder> packet) {
    ASSERT_LOG(IsClassicAcl(handle), "handle %d is not a classic connection",
               handle);
    handle_to_classic_connection_map_[handle]->EnqueuePacket(std::move(packet));
  }

  bool IsLeAcl(HciHandle handle) {
    return handle_to_le_connection_map_.find(handle) !=
           handle_to_le_connection_map_.end();
  }

  void EnqueueLePacket(HciHandle handle,
                       std::unique_ptr<packet::RawBuilder> packet) {
    ASSERT_LOG(IsLeAcl(handle), "handle %d is not a LE connection", handle);
    handle_to_le_connection_map_[handle]->EnqueuePacket(std::move(packet));
  }

  void DisconnectClassicConnections(std::promise<void> promise) {
    LOG_INFO("Disconnect gd acl shim classic connections");
    std::vector<HciHandle> disconnect_handles;
    for (auto& connection : handle_to_classic_connection_map_) {
      disconnect_classic(connection.first, HCI_ERR_REMOTE_POWER_OFF,
                         "Suspend disconnect");
      disconnect_handles.push_back(connection.first);
    }

    // Since this is a suspend disconnect, we immediately also call
    // |OnClassicSuspendInitiatedDisconnect| without waiting for it to happen.
    // We want the stack to clean up ahead of the link layer (since we will mask
    // away that event). The reason we do this in a separate loop is that this
    // will also remove the handle from the connection map.
    for (auto& handle : disconnect_handles) {
      auto found = handle_to_classic_connection_map_.find(handle);
      if (found != handle_to_classic_connection_map_.end()) {
        GetAclManager()->OnClassicSuspendInitiatedDisconnect(
            found->first, hci::ErrorCode::CONNECTION_TERMINATED_BY_LOCAL_HOST);
      }
    }

    promise.set_value();
  }

  void ShutdownClassicConnections(std::promise<void> promise) {
    LOG_INFO("Shutdown gd acl shim classic connections");
    for (auto& connection : handle_to_classic_connection_map_) {
      connection.second->Shutdown();
    }
    handle_to_classic_connection_map_.clear();
    promise.set_value();
  }

  void DisconnectLeConnections(std::promise<void> promise) {
    LOG_INFO("Disconnect gd acl shim le connections");
    std::vector<HciHandle> disconnect_handles;
    for (auto& connection : handle_to_le_connection_map_) {
      disconnect_le(connection.first, HCI_ERR_REMOTE_POWER_OFF,
                    "Suspend disconnect");
      disconnect_handles.push_back(connection.first);
    }

    // Since this is a suspend disconnect, we immediately also call
    // |OnLeSuspendInitiatedDisconnect| without waiting for it to happen. We
    // want the stack to clean up ahead of the link layer (since we will mask
    // away that event). The reason we do this in a separate loop is that this
    // will also remove the handle from the connection map.
    for (auto& handle : disconnect_handles) {
      auto found = handle_to_le_connection_map_.find(handle);
      if (found != handle_to_le_connection_map_.end()) {
        GetAclManager()->OnLeSuspendInitiatedDisconnect(
            found->first, hci::ErrorCode::CONNECTION_TERMINATED_BY_LOCAL_HOST);
      }
    }
    promise.set_value();
  }

  void ShutdownLeConnections(std::promise<void> promise) {
    LOG_INFO("Shutdown gd acl shim le connections");
    for (auto& connection : handle_to_le_connection_map_) {
      connection.second->Shutdown();
    }
    handle_to_le_connection_map_.clear();
    promise.set_value();
  }

  void FinalShutdown(std::promise<void> promise) {
    if (!handle_to_classic_connection_map_.empty()) {
      for (auto& connection : handle_to_classic_connection_map_) {
        connection.second->Shutdown();
      }
      handle_to_classic_connection_map_.clear();
      LOG_INFO("Cleared all classic connections count:%zu",
               handle_to_classic_connection_map_.size());
    }

    if (!handle_to_le_connection_map_.empty()) {
      for (auto& connection : handle_to_le_connection_map_) {
        connection.second->Shutdown();
      }
      handle_to_le_connection_map_.clear();
      LOG_INFO("Cleared all le connections count:%zu",
               handle_to_le_connection_map_.size());
    }
    promise.set_value();
  }

  void HoldMode(HciHandle handle, uint16_t max_interval,
                uint16_t min_interval) {
    ASSERT_LOG(IsClassicAcl(handle), "handle %d is not a classic connection",
               handle);
    handle_to_classic_connection_map_[handle]->HoldMode(max_interval,
                                                        min_interval);
  }

  void ExitSniffMode(HciHandle handle) {
    ASSERT_LOG(IsClassicAcl(handle), "handle %d is not a classic connection",
               handle);
    handle_to_classic_connection_map_[handle]->ExitSniffMode();
  }

  void SniffMode(HciHandle handle, uint16_t max_interval, uint16_t min_interval,
                 uint16_t attempt, uint16_t timeout) {
    ASSERT_LOG(IsClassicAcl(handle), "handle %d is not a classic connection",
               handle);
    handle_to_classic_connection_map_[handle]->SniffMode(
        max_interval, min_interval, attempt, timeout);
  }

  void SniffSubrating(HciHandle handle, uint16_t maximum_latency,
                      uint16_t minimum_remote_timeout,
                      uint16_t minimum_local_timeout) {
    ASSERT_LOG(IsClassicAcl(handle), "handle %d is not a classic connection",
               handle);
    handle_to_classic_connection_map_[handle]->SniffSubrating(
        maximum_latency, minimum_remote_timeout, minimum_local_timeout);
  }

  void LeSetDefaultSubrate(uint16_t subrate_min, uint16_t subrate_max,
                           uint16_t max_latency, uint16_t cont_num,
                           uint16_t sup_tout) {
    GetAclManager()->LeSetDefaultSubrate(subrate_min, subrate_max, max_latency,
                                         cont_num, sup_tout);
  }

  void LeSubrateRequest(HciHandle handle, uint16_t subrate_min,
                        uint16_t subrate_max, uint16_t max_latency,
                        uint16_t cont_num, uint16_t sup_tout) {
    ASSERT_LOG(IsLeAcl(handle), "handle %d is not a LE connection", handle);
    handle_to_le_connection_map_[handle]->LeSubrateRequest(
        subrate_min, subrate_max, max_latency, cont_num, sup_tout);
  }

  void SetConnectionEncryption(HciHandle handle, hci::Enable enable) {
    ASSERT_LOG(IsClassicAcl(handle), "handle %d is not a classic connection",
               handle);
    handle_to_classic_connection_map_[handle]->SetConnectionEncryption(enable);
  }

  void disconnect_classic(uint16_t handle, tHCI_STATUS reason,
                          std::string comment) {
    auto connection = handle_to_classic_connection_map_.find(handle);
    if (connection != handle_to_classic_connection_map_.end()) {
      auto remote_address = connection->second->GetRemoteAddress();
      connection->second->InitiateDisconnect(
          ToDisconnectReasonFromLegacy(reason));
      LOG_DEBUG("Disconnection initiated classic remote:%s handle:%hu",
                ADDRESS_TO_LOGGABLE_CSTR(remote_address), handle);
      BTM_LogHistory(kBtmLogTag, ToRawAddress(remote_address),
                     "Disconnection initiated",
                     base::StringPrintf("classic reason:%s comment:%s",
                                        hci_status_code_text(reason).c_str(),
                                        comment.c_str()));
      classic_acl_disconnect_reason_.Put(comment);
    } else {
      LOG_WARN("Unable to disconnect unknown classic connection handle:0x%04x",
               handle);
    }
  }

  void disconnect_le(uint16_t handle, tHCI_STATUS reason, std::string comment) {
    auto connection = handle_to_le_connection_map_.find(handle);
    if (connection != handle_to_le_connection_map_.end()) {
      auto remote_address_with_type =
          connection->second->GetRemoteAddressWithType();
      if (!common::init_flags::use_unified_connection_manager_is_enabled()) {
        GetAclManager()->RemoveFromBackgroundList(remote_address_with_type);
      }
      connection->second->InitiateDisconnect(
          ToDisconnectReasonFromLegacy(reason));
      LOG_DEBUG("Disconnection initiated le remote:%s handle:%hu",
                ADDRESS_TO_LOGGABLE_CSTR(remote_address_with_type), handle);
      BTM_LogHistory(kBtmLogTag,
                     ToLegacyAddressWithType(remote_address_with_type),
                     "Disconnection initiated",
                     base::StringPrintf("Le reason:%s comment:%s",
                                        hci_status_code_text(reason).c_str(),
                                        comment.c_str()));
      le_acl_disconnect_reason_.Put(comment);
    } else {
      LOG_WARN("Unable to disconnect unknown le connection handle:0x%04x",
               handle);
    }
  }

  void accept_le_connection_from(const hci::AddressWithType& address_with_type,
                                 bool is_direct, std::promise<bool> promise) {
    if (shadow_acceptlist_.IsFull()) {
      LOG_ERROR("Acceptlist is full preventing new Le connection");
      promise.set_value(false);
      return;
    }
    shadow_acceptlist_.Add(address_with_type);
    promise.set_value(true);
    GetAclManager()->CreateLeConnection(address_with_type, is_direct);
    LOG_DEBUG("Allow Le connection from remote:%s",
              ADDRESS_TO_LOGGABLE_CSTR(address_with_type));
    BTM_LogHistory(kBtmLogTag, ToLegacyAddressWithType(address_with_type),
                   "Allow connection from", "Le");
  }

  void ignore_le_connection_from(
      const hci::AddressWithType& address_with_type) {
    shadow_acceptlist_.Remove(address_with_type);
    GetAclManager()->CancelLeConnect(address_with_type);
    LOG_DEBUG("Ignore Le connection from remote:%s",
              ADDRESS_TO_LOGGABLE_CSTR(address_with_type));
    BTM_LogHistory(kBtmLogTag, ToLegacyAddressWithType(address_with_type),
                   "Ignore connection from", "Le");
  }

  void clear_acceptlist() {
    auto shadow_acceptlist = shadow_acceptlist_.GetCopy();
    size_t count = shadow_acceptlist.size();
    GetAclManager()->ClearFilterAcceptList();
    shadow_acceptlist_.Clear();
    LOG_DEBUG("Cleared entire Le address acceptlist count:%zu", count);
  }

  void le_rand(LeRandCallback cb ) {
    controller_get_interface()->le_rand(std::move(cb));
  }

  void AddToAddressResolution(const hci::AddressWithType& address_with_type,
                              const std::array<uint8_t, 16>& peer_irk,
                              const std::array<uint8_t, 16>& local_irk) {
    if (shadow_address_resolution_list_.IsFull()) {
      LOG_WARN("Le Address Resolution list is full size:%zu",
               shadow_address_resolution_list_.Size());
      return;
    }
    // TODO This should really be added upon successful completion
    shadow_address_resolution_list_.Add(address_with_type);
    GetAclManager()->AddDeviceToResolvingList(address_with_type, peer_irk,
                                              local_irk);
  }

  void RemoveFromAddressResolution(
      const hci::AddressWithType& address_with_type) {
    // TODO This should really be removed upon successful removal
    if (!shadow_address_resolution_list_.Remove(address_with_type)) {
      LOG_WARN("Unable to remove from Le Address Resolution list device:%s",
               ADDRESS_TO_LOGGABLE_CSTR(address_with_type));
    }
    GetAclManager()->RemoveDeviceFromResolvingList(address_with_type);
  }

  void ClearResolvingList() {
    GetAclManager()->ClearResolvingList();
    // TODO This should really be cleared after successful clear status
    shadow_address_resolution_list_.Clear();
  }

  void SetSystemSuspendState(bool suspended) {
    GetAclManager()->SetSystemSuspendState(suspended);
  }

  void DumpConnectionHistory() const {
    std::vector<std::string> history =
        connection_history_.ReadElementsAsString();
    for (auto& entry : history) {
      LOG_DEBUG("%s", entry.c_str());
    }
    const auto acceptlist = shadow_acceptlist_.GetCopy();
    LOG_DEBUG("Shadow le accept list  size:%-3zu controller_max_size:%hhu",
              acceptlist.size(), shadow_acceptlist_.GetMaxSize());
    for (auto& entry : acceptlist) {
      LOG_DEBUG("acceptlist:%s", ADDRESS_TO_LOGGABLE_CSTR(entry));
    }
  }

#define DUMPSYS_TAG "shim::acl"
  void DumpConnectionHistory(int fd) const {
    std::vector<std::string> history =
        connection_history_.ReadElementsAsString();
    for (auto& entry : history) {
      LOG_DUMPSYS(fd, "%s", entry.c_str());
    }
    if (classic_acl_disconnect_reason_.Size() > 0) {
      LOG_DUMPSYS(fd, "Classic sources of initiated disconnects");
      for (const auto& item :
           classic_acl_disconnect_reason_.GetSortedHighToLow()) {
        LOG_DUMPSYS(fd, "  %s:%zu", item.item.c_str(), item.count);
      }
    }
    if (le_acl_disconnect_reason_.Size() > 0) {
      LOG_DUMPSYS(fd, "Le sources of initiated disconnects");
      for (const auto& item : le_acl_disconnect_reason_.GetSortedHighToLow()) {
        LOG_DUMPSYS(fd, "  %s:%zu", item.item.c_str(), item.count);
      }
    }

    auto acceptlist = shadow_acceptlist_.GetCopy();
    LOG_DUMPSYS(fd,
                "Shadow le accept list              size:%-3zu "
                "controller_max_size:%hhu",
                acceptlist.size(), shadow_acceptlist_.GetMaxSize());
    unsigned cnt = 0;
    for (auto& entry : acceptlist) {
      LOG_DUMPSYS(fd, "  %03u %s", ++cnt, ADDRESS_TO_LOGGABLE_CSTR(entry));
    }
    auto address_resolution_list = shadow_address_resolution_list_.GetCopy();
    LOG_DUMPSYS(fd,
                "Shadow le address resolution list  size:%-3zu "
                "controller_max_size:%hhu",
                address_resolution_list.size(),
                shadow_address_resolution_list_.GetMaxSize());
    cnt = 0;
    for (auto& entry : address_resolution_list) {
      LOG_DUMPSYS(fd, "  %03u %s", ++cnt, ADDRESS_TO_LOGGABLE_CSTR(entry));
    }
  }
#undef DUMPSYS_TAG
};

#define DUMPSYS_TAG "shim::legacy::l2cap"
extern tL2C_CB l2cb;
void DumpsysL2cap(int fd) {
  LOG_DUMPSYS_TITLE(fd, DUMPSYS_TAG);
  for (int i = 0; i < MAX_L2CAP_LINKS; i++) {
    const tL2C_LCB& lcb = l2cb.lcb_pool[i];
    if (!lcb.in_use) continue;
    LOG_DUMPSYS(fd, "link_state:%s", link_state_text(lcb.link_state).c_str());
    LOG_DUMPSYS(fd, "handle:0x%04x", lcb.Handle());

    const tL2C_CCB* ccb = lcb.ccb_queue.p_first_ccb;
    while (ccb != nullptr) {
      LOG_DUMPSYS(
          fd, "  active channel lcid:0x%04x rcid:0x%04x is_ecoc:%s in_use:%s",
          ccb->local_cid, ccb->remote_cid, common::ToString(ccb->ecoc).c_str(),
          common::ToString(ccb->in_use).c_str());
      ccb = ccb->p_next_ccb;
    }
  }
}

#undef DUMPSYS_TAG
#define DUMPSYS_TAG "shim::legacy::acl"
void DumpsysAcl(int fd) {
  const tACL_CB& acl_cb = btm_cb.acl_cb_;

  LOG_DUMPSYS_TITLE(fd, DUMPSYS_TAG);

  if (shim::Stack::GetInstance()->IsRunning()) {
    shim::Stack::GetInstance()->GetAcl()->DumpConnectionHistory(fd);
  }

  for (int i = 0; i < MAX_L2CAP_LINKS; i++) {
    const tACL_CONN& link = acl_cb.acl_db[i];
    if (!link.in_use) continue;

    LOG_DUMPSYS(fd, "remote_addr:%s handle:0x%04x transport:%s",
                ADDRESS_TO_LOGGABLE_CSTR(link.remote_addr), link.hci_handle,
                bt_transport_text(link.transport).c_str());
    LOG_DUMPSYS(fd, "    link_up_issued:%5s",
                (link.link_up_issued) ? "true" : "false");
    LOG_DUMPSYS(fd, "    flush_timeout:0x%04x", link.flush_timeout_in_ticks);
    LOG_DUMPSYS(fd, "    link_supervision_timeout:%.3f sec",
                ticks_to_seconds(link.link_super_tout));
    LOG_DUMPSYS(fd, "    disconnect_reason:0x%02x", link.disconnect_reason);

    if (link.is_transport_br_edr()) {
      for (int j = 0; j < HCI_EXT_FEATURES_PAGE_MAX + 1; j++) {
        if (!link.peer_lmp_feature_valid[j]) continue;
        LOG_DUMPSYS(fd, "    peer_lmp_features[%d] valid:%s data:%s", j,
                    common::ToString(link.peer_lmp_feature_valid[j]).c_str(),
                    bd_features_text(link.peer_lmp_feature_pages[j]).c_str());
      }
      LOG_DUMPSYS(fd, "    [classic] link_policy:%s",
                  link_policy_text(static_cast<tLINK_POLICY>(link.link_policy))
                      .c_str());
      LOG_DUMPSYS(fd, "    [classic] sniff_subrating:%s",
                  common::ToString(HCI_SNIFF_SUB_RATE_SUPPORTED(
                                       link.peer_lmp_feature_pages[0]))
                      .c_str());

      LOG_DUMPSYS(fd, "    pkt_types_mask:0x%04x", link.pkt_types_mask);
      LOG_DUMPSYS(fd, "    role:%s", RoleText(link.link_role).c_str());
    } else if (link.is_transport_ble()) {
      LOG_DUMPSYS(fd, "    [le] peer_features valid:%s data:%s",
                  common::ToString(link.peer_le_features_valid).c_str(),
                  bd_features_text(link.peer_le_features).c_str());

      LOG_DUMPSYS(fd, "    [le] active_remote_addr:%s[%s]",
                  ADDRESS_TO_LOGGABLE_CSTR(link.active_remote_addr),
                  AddressTypeText(link.active_remote_addr_type).c_str());
    }
  }
}
#undef DUMPSYS_TAG

using Record = common::TimestampedEntry<std::string>;
const std::string kTimeFormat("%Y-%m-%d %H:%M:%S");

#define DUMPSYS_TAG "shim::legacy::btm"
void DumpsysBtm(int fd) {
  LOG_DUMPSYS_TITLE(fd, DUMPSYS_TAG);
  if (btm_cb.history_ != nullptr) {
    std::vector<Record> history = btm_cb.history_->Pull();
    for (auto& record : history) {
      time_t then = record.timestamp / 1000;
      struct tm tm;
      localtime_r(&then, &tm);
      auto s2 = common::StringFormatTime(kTimeFormat, tm);
      LOG_DUMPSYS(fd, " %s.%03u %s", s2.c_str(),
                  static_cast<unsigned int>(record.timestamp % 1000),
                  record.entry.c_str());
    }
  }
}
#undef DUMPSYS_TAG

#define DUMPSYS_TAG "shim::legacy::record"
void DumpsysRecord(int fd) {
  LOG_DUMPSYS_TITLE(fd, DUMPSYS_TAG);

  if (btm_sec_cb.sec_dev_rec == nullptr) {
    LOG_DUMPSYS(fd, "Record is empty - no devices");
    return;
  }

  unsigned cnt = 0;
  list_node_t* end = list_end(btm_sec_cb.sec_dev_rec);
  for (list_node_t* node = list_begin(btm_sec_cb.sec_dev_rec); node != end;
       node = list_next(node)) {
    tBTM_SEC_DEV_REC* p_dev_rec =
        static_cast<tBTM_SEC_DEV_REC*>(list_node(node));
    // TODO: handle in tBTM_SEC_DEV_REC.ToString
    LOG_DUMPSYS(fd, "%03u %s", ++cnt, p_dev_rec->ToString().c_str());
  }
}
#undef DUMPSYS_TAG

#define DUMPSYS_TAG "shim::legacy::stack"
void DumpsysNeighbor(int fd) {
  LOG_DUMPSYS(fd, "Stack information %lc%lc", kRunicBjarkan, kRunicHagall);
  if (btm_cb.neighbor.classic_inquiry.start_time_ms == 0) {
    LOG_DUMPSYS(fd, "Classic inquiry:disabled");
  } else {
    LOG_DUMPSYS(fd, "Classic inquiry:enabled duration_s:%.3f results:%lu",
                (timestamper_in_milliseconds.GetTimestamp() -
                 btm_cb.neighbor.classic_inquiry.start_time_ms) /
                    1000.0,
                btm_cb.neighbor.classic_inquiry.results);
  }
  if (btm_cb.neighbor.le_scan.start_time_ms == 0) {
    LOG_DUMPSYS(fd, "Le scan:disabled");
  } else {
    LOG_DUMPSYS(fd, "Le scan:enabled duration_s:%.3f results:%lu",
                (timestamper_in_milliseconds.GetTimestamp() -
                 btm_cb.neighbor.le_scan.start_time_ms) /
                    1000.0,
                btm_cb.neighbor.le_scan.results);
  }
  const auto copy = btm_cb.neighbor.inquiry_history_->Pull();
  LOG_DUMPSYS(fd, "Last %zu inquiry scans:", copy.size());
  for (const auto& it : copy) {
    LOG_DUMPSYS(fd,
                "  %s - %s duration_ms:%-5llu num_resp:%-2u"
                " std:%-2u rssi:%-2u ext:%-2u %12s",
                EpochMillisToString(it.entry.start_time_ms).c_str(),
                EpochMillisToString(it.timestamp).c_str(),
                it.timestamp - it.entry.start_time_ms, it.entry.num_resp,
                it.entry.resp_type[BTM_INQ_RESULT_STANDARD],
                it.entry.resp_type[BTM_INQ_RESULT_WITH_RSSI],
                it.entry.resp_type[BTM_INQ_RESULT_EXTENDED],
                btm_inquiry_cmpl_status_text(it.entry.status).c_str());
  }
}
#undef DUMPSYS_TAG

void shim::legacy::Acl::Dump(int fd) const {
  DumpsysRecord(fd);
  DumpsysNeighbor(fd);
  DumpsysAcl(fd);
  DumpsysL2cap(fd);
  DumpsysBtm(fd);
}

shim::legacy::Acl::Acl(os::Handler* handler,
                       const acl_interface_t& acl_interface,
                       uint8_t max_acceptlist_size,
                       uint8_t max_address_resolution_size)
    : handler_(handler), acl_interface_(acl_interface) {
  ASSERT(handler_ != nullptr);
  ValidateAclInterface(acl_interface_);
  pimpl_ = std::make_unique<Acl::impl>(max_acceptlist_size,
                                       max_address_resolution_size);
  GetAclManager()->RegisterCallbacks(this, handler_);
  GetAclManager()->RegisterLeCallbacks(this, handler_);
  GetController()->RegisterCompletedMonitorAclPacketsCallback(
      handler->BindOn(this, &Acl::on_incoming_acl_credits));
  shim::RegisterDumpsysFunction(static_cast<void*>(this),
                                [this](int fd) { Dump(fd); });
}

shim::legacy::Acl::~Acl() {
  shim::UnregisterDumpsysFunction(static_cast<void*>(this));
  GetController()->UnregisterCompletedMonitorAclPacketsCallback();

  if (CheckForOrphanedAclConnections()) {
    pimpl_->DumpConnectionHistory();
  }
}

bool shim::legacy::Acl::CheckForOrphanedAclConnections() const {
  bool orphaned_acl_connections = false;

  if (!pimpl_->handle_to_classic_connection_map_.empty()) {
    LOG_ERROR("About to destroy classic active ACL");
    for (const auto& connection : pimpl_->handle_to_classic_connection_map_) {
      LOG_ERROR("  Orphaned classic ACL handle:0x%04x bd_addr:%s created:%s",
                connection.second->Handle(),
                ADDRESS_TO_LOGGABLE_CSTR(connection.second->GetRemoteAddress()),
                common::StringFormatTimeWithMilliseconds(
                    kConnectionDescriptorTimeFormat,
                    connection.second->GetCreationTime())
                    .c_str());
    }
    orphaned_acl_connections = true;
  }

  if (!pimpl_->handle_to_le_connection_map_.empty()) {
    LOG_ERROR("About to destroy le active ACL");
    for (const auto& connection : pimpl_->handle_to_le_connection_map_) {
      LOG_ERROR("  Orphaned le ACL handle:0x%04x bd_addr:%s created:%s",
                connection.second->Handle(),
                ADDRESS_TO_LOGGABLE_CSTR(connection.second->GetRemoteAddressWithType()),
                common::StringFormatTimeWithMilliseconds(
                    kConnectionDescriptorTimeFormat,
                    connection.second->GetCreationTime())
                    .c_str());
    }
    orphaned_acl_connections = true;
  }
  return orphaned_acl_connections;
}

void shim::legacy::Acl::on_incoming_acl_credits(uint16_t handle,
                                                uint16_t credits) {
  TRY_POSTING_ON_MAIN(acl_interface_.on_packets_completed, handle, credits);
}

void shim::legacy::Acl::write_data_sync(
    HciHandle handle, std::unique_ptr<packet::RawBuilder> packet) {
  if (pimpl_->IsClassicAcl(handle)) {
    pimpl_->EnqueueClassicPacket(handle, std::move(packet));
  } else if (pimpl_->IsLeAcl(handle)) {
    pimpl_->EnqueueLePacket(handle, std::move(packet));
  } else {
    LOG_ERROR("Unable to find destination to write data\n");
  }
}

void shim::legacy::Acl::WriteData(HciHandle handle,
                                  std::unique_ptr<packet::RawBuilder> packet) {
  handler_->Post(common::BindOnce(&Acl::write_data_sync,
                                  common::Unretained(this), handle,
                                  std::move(packet)));
}

void shim::legacy::Acl::CreateClassicConnection(const hci::Address& address) {
  GetAclManager()->CreateConnection(address);
  LOG_DEBUG("Connection initiated for classic to remote:%s",
            ADDRESS_TO_LOGGABLE_CSTR(address));
  BTM_LogHistory(kBtmLogTag, ToRawAddress(address), "Initiated connection",
                 "classic");
}

void shim::legacy::Acl::CancelClassicConnection(const hci::Address& address) {
  GetAclManager()->CancelConnect(address);
  LOG_DEBUG("Connection cancelled for classic to remote:%s",
            ADDRESS_TO_LOGGABLE_CSTR(address));
  BTM_LogHistory(kBtmLogTag, ToRawAddress(address), "Cancelled connection",
                 "classic");
}

void shim::legacy::Acl::AcceptLeConnectionFrom(
    const hci::AddressWithType& address_with_type, bool is_direct,
    std::promise<bool> promise) {
  LOG_DEBUG("AcceptLeConnectionFrom %s",
            ADDRESS_TO_LOGGABLE_CSTR(address_with_type.GetAddress()));
  handler_->CallOn(pimpl_.get(), &Acl::impl::accept_le_connection_from,
                   address_with_type, is_direct, std::move(promise));
}

void shim::legacy::Acl::IgnoreLeConnectionFrom(
    const hci::AddressWithType& address_with_type) {
  LOG_DEBUG("IgnoreLeConnectionFrom %s",
            ADDRESS_TO_LOGGABLE_CSTR(address_with_type.GetAddress()));
  handler_->CallOn(pimpl_.get(), &Acl::impl::ignore_le_connection_from,
                   address_with_type);
}

void shim::legacy::Acl::OnClassicLinkDisconnected(HciHandle handle,
                                                  hci::ErrorCode reason) {
  hci::Address remote_address =
      pimpl_->handle_to_classic_connection_map_[handle]->GetRemoteAddress();
  CreationTime creation_time =
      pimpl_->handle_to_classic_connection_map_[handle]->GetCreationTime();
  bool is_locally_initiated =
      pimpl_->handle_to_classic_connection_map_[handle]->IsLocallyInitiated();

  TeardownTime teardown_time = std::chrono::system_clock::now();

  pimpl_->handle_to_classic_connection_map_.erase(handle);
  TRY_POSTING_ON_MAIN(acl_interface_.connection.classic.on_disconnected,
                      ToLegacyHciErrorCode(hci::ErrorCode::SUCCESS), handle,
                      ToLegacyHciErrorCode(reason));
  LOG_DEBUG("Disconnected classic link remote:%s handle:%hu reason:%s",
            ADDRESS_TO_LOGGABLE_CSTR(remote_address), handle,
            ErrorCodeText(reason).c_str());
  BTM_LogHistory(
      kBtmLogTag, ToRawAddress(remote_address), "Disconnected",
      base::StringPrintf("classic reason:%s", ErrorCodeText(reason).c_str()));
  pimpl_->connection_history_.Push(
      std::make_unique<ClassicConnectionDescriptor>(
          remote_address, creation_time, teardown_time, handle,
          is_locally_initiated, reason));
}

bluetooth::hci::AddressWithType shim::legacy::Acl::GetConnectionLocalAddress(
    uint16_t handle, bool ota_address) {
  bluetooth::hci::AddressWithType address_with_type;

  for (auto& [acl_handle, connection] : pimpl_->handle_to_le_connection_map_) {
    if (acl_handle != handle) {
      continue;
    }

    if (ota_address) {
      return connection->GetLocalOtaAddressWithType();
    }
    return connection->GetLocalAddressWithType();
  }
  LOG_WARN("address not found!");
  return address_with_type;
}

bluetooth::hci::AddressWithType shim::legacy::Acl::GetConnectionPeerAddress(
    uint16_t handle, bool ota_address) {
  bluetooth::hci::AddressWithType address_with_type;
  for (auto& [acl_handle, connection] : pimpl_->handle_to_le_connection_map_) {
    if (acl_handle != handle) {
      continue;
    }

    if (ota_address) {
      return connection->GetPeerOtaAddressWithType();
    }
    return connection->GetPeerAddressWithType();
  }
  LOG_WARN("address not found!");
  return address_with_type;
}

std::optional<uint8_t> shim::legacy::Acl::GetAdvertisingSetConnectedTo(
    const RawAddress& remote_bda) {
  auto remote_address = ToGdAddress(remote_bda);
  for (auto& [handle, connection] : pimpl_->handle_to_le_connection_map_) {
    if (connection->GetRemoteAddressWithType().GetAddress() == remote_address) {
      return connection->GetAdvertisingSetConnectedTo();
    }
  }
  LOG_WARN("address not found!");
  return {};
}

void shim::legacy::Acl::OnLeLinkDisconnected(HciHandle handle,
                                             hci::ErrorCode reason) {
  hci::AddressWithType remote_address_with_type =
      pimpl_->handle_to_le_connection_map_[handle]->GetRemoteAddressWithType();
  CreationTime creation_time =
      pimpl_->handle_to_le_connection_map_[handle]->GetCreationTime();
  bool is_locally_initiated =
      pimpl_->handle_to_le_connection_map_[handle]->IsLocallyInitiated();

  TeardownTime teardown_time = std::chrono::system_clock::now();

  pimpl_->handle_to_le_connection_map_.erase(handle);
  TRY_POSTING_ON_MAIN(acl_interface_.connection.le.on_disconnected,
                      ToLegacyHciErrorCode(hci::ErrorCode::SUCCESS), handle,
                      ToLegacyHciErrorCode(reason));
  LOG_DEBUG("Disconnected le link remote:%s handle:%hu reason:%s",
            ADDRESS_TO_LOGGABLE_CSTR(remote_address_with_type), handle,
            ErrorCodeText(reason).c_str());
  BTM_LogHistory(
      kBtmLogTag, ToLegacyAddressWithType(remote_address_with_type),
      "Disconnected",
      base::StringPrintf("Le reason:%s", ErrorCodeText(reason).c_str()));
  pimpl_->connection_history_.Push(std::make_unique<LeConnectionDescriptor>(
      remote_address_with_type, creation_time, teardown_time, handle,
      is_locally_initiated, reason));
}

void shim::legacy::Acl::OnConnectSuccess(
    std::unique_ptr<hci::acl_manager::ClassicAclConnection> connection) {
  ASSERT(connection != nullptr);
  auto handle = connection->GetHandle();
  bool locally_initiated = connection->locally_initiated_;
  const hci::Address remote_address = connection->GetAddress();
  const RawAddress bd_addr = ToRawAddress(remote_address);

  pimpl_->handle_to_classic_connection_map_.emplace(
      handle, std::make_unique<ClassicShimAclConnection>(
                  acl_interface_.on_send_data_upwards,
                  std::bind(&shim::legacy::Acl::OnClassicLinkDisconnected, this,
                            std::placeholders::_1, std::placeholders::_2),
                  acl_interface_.link.classic, handler_, std::move(connection),
                  std::chrono::system_clock::now()));
  pimpl_->handle_to_classic_connection_map_[handle]->RegisterCallbacks();
  pimpl_->handle_to_classic_connection_map_[handle]
      ->ReadRemoteControllerInformation();

  TRY_POSTING_ON_MAIN(acl_interface_.connection.classic.on_connected, bd_addr,
                      handle, false, locally_initiated);
  LOG_DEBUG("Connection successful classic remote:%s handle:%hu initiator:%s",
            ADDRESS_TO_LOGGABLE_CSTR(remote_address), handle,
            (locally_initiated) ? "local" : "remote");
  BTM_LogHistory(kBtmLogTag, ToRawAddress(remote_address),
                 "Connection successful",
                 (locally_initiated) ? "classic Local initiated"
                                     : "classic Remote initiated");
}

void shim::legacy::Acl::OnConnectRequest(hci::Address address,
                                         hci::ClassOfDevice cod) {
  const RawAddress bd_addr = ToRawAddress(address);

  TRY_POSTING_ON_MAIN(acl_interface_.connection.classic.on_connect_request,
                      bd_addr, cod);
  LOG_DEBUG("Received connect request remote:%s",
            ADDRESS_TO_LOGGABLE_CSTR(address));
  BTM_LogHistory(kBtmLogTag, ToRawAddress(address), "Connection request");
}

void shim::legacy::Acl::OnConnectFail(hci::Address address,
                                      hci::ErrorCode reason,
                                      bool locally_initiated) {
  const RawAddress bd_addr = ToRawAddress(address);
  TRY_POSTING_ON_MAIN(acl_interface_.connection.classic.on_failed, bd_addr,
                      ToLegacyHciErrorCode(reason), locally_initiated);
  LOG_WARN("Connection failed classic remote:%s reason:%s",
           ADDRESS_TO_LOGGABLE_CSTR(address), hci::ErrorCodeText(reason).c_str());
  BTM_LogHistory(kBtmLogTag, ToRawAddress(address), "Connection failed",
                 base::StringPrintf("classic reason:%s",
                                    hci::ErrorCodeText(reason).c_str()));
}

void shim::legacy::Acl::OnLeConnectSuccess(
    hci::AddressWithType address_with_type,
    std::unique_ptr<hci::acl_manager::LeAclConnection> connection) {
  ASSERT(connection != nullptr);
  auto handle = connection->GetHandle();

  // Save the peer address, if any
  hci::AddressWithType peer_address_with_type =
      connection->peer_address_with_type_;

  hci::Role connection_role = connection->GetRole();
  bool locally_initiated = connection->locally_initiated_;

  uint16_t conn_interval = connection->interval_;
  uint16_t conn_latency = connection->latency_;
  uint16_t conn_timeout = connection->supervision_timeout_;

  RawAddress local_rpa =
      ToRawAddress(connection->local_resolvable_private_address_);
  RawAddress peer_rpa =
      ToRawAddress(connection->peer_resolvable_private_address_);
  tBLE_ADDR_TYPE peer_addr_type =
      (tBLE_ADDR_TYPE)connection->peer_address_with_type_.GetAddressType();

  auto can_read_discoverable_characteristics = std::visit(
      [&](auto&& data) {
        using T = std::decay_t<decltype(data)>;
        if constexpr (std::is_same_v<T, hci::acl_manager::DataAsPeripheral>) {
          return data.connected_to_discoverable;
        } else {
          // if we are the central, the peer can always see discoverable
          // characteristics
          return true;
        }
      },
      connection->GetRoleSpecificData());

  pimpl_->handle_to_le_connection_map_.emplace(
      handle, std::make_unique<LeShimAclConnection>(
                  acl_interface_.on_send_data_upwards,
                  std::bind(&shim::legacy::Acl::OnLeLinkDisconnected, this,
                            std::placeholders::_1, std::placeholders::_2),
                  acl_interface_.link.le, handler_, std::move(connection),
                  std::chrono::system_clock::now()));
  pimpl_->handle_to_le_connection_map_[handle]->RegisterCallbacks();

  // Once an le connection has successfully been established
  // the device address is removed from the controller accept list.

  if (IsRpa(address_with_type)) {
    LOG_DEBUG("Connection address is rpa:%s identity_addr:%s",
              ADDRESS_TO_LOGGABLE_CSTR(address_with_type),
              ADDRESS_TO_LOGGABLE_CSTR(peer_address_with_type));
    pimpl_->shadow_acceptlist_.Remove(peer_address_with_type);
  } else {
    LOG_DEBUG("Connection address is not rpa addr:%s",
              ADDRESS_TO_LOGGABLE_CSTR(address_with_type));
    pimpl_->shadow_acceptlist_.Remove(address_with_type);
  }

  if (!pimpl_->handle_to_le_connection_map_[handle]->IsInFilterAcceptList() &&
      connection_role == hci::Role::CENTRAL) {
    pimpl_->handle_to_le_connection_map_[handle]->InitiateDisconnect(
        hci::DisconnectReason::REMOTE_USER_TERMINATED_CONNECTION);
    LOG_INFO("Disconnected ACL after connection canceled");
    BTM_LogHistory(kBtmLogTag, ToLegacyAddressWithType(address_with_type),
                   "Connection canceled", "Le");
    return;
  }

  pimpl_->handle_to_le_connection_map_[handle]
      ->ReadRemoteControllerInformation();

  tBLE_BD_ADDR legacy_address_with_type =
      ToLegacyAddressWithType(address_with_type);

  TRY_POSTING_ON_MAIN(acl_interface_.connection.le.on_connected,
                      legacy_address_with_type, handle,
                      ToLegacyRole(connection_role), conn_interval,
                      conn_latency, conn_timeout, local_rpa, peer_rpa,
                      peer_addr_type, can_read_discoverable_characteristics);

  LOG_DEBUG("Connection successful le remote:%s handle:%hu initiator:%s",
            ADDRESS_TO_LOGGABLE_CSTR(address_with_type), handle,
            (locally_initiated) ? "local" : "remote");
  BTM_LogHistory(kBtmLogTag, ToLegacyAddressWithType(address_with_type),
                 "Connection successful", "Le");
}

void shim::legacy::Acl::OnLeConnectFail(hci::AddressWithType address_with_type,
                                        hci::ErrorCode reason) {
  tBLE_BD_ADDR legacy_address_with_type =
      ToLegacyAddressWithType(address_with_type);

  uint16_t handle = 0;  /* TODO Unneeded */
  bool enhanced = true; /* TODO logging metrics only */
  tHCI_STATUS status = ToLegacyHciErrorCode(reason);

  TRY_POSTING_ON_MAIN(acl_interface_.connection.le.on_failed,
                      legacy_address_with_type, handle, enhanced, status);

  pimpl_->shadow_acceptlist_.Remove(address_with_type);
  LOG_WARN("Connection failed le remote:%s",
           ADDRESS_TO_LOGGABLE_CSTR(address_with_type));
  BTM_LogHistory(
      kBtmLogTag, ToLegacyAddressWithType(address_with_type),
      "Connection failed",
      base::StringPrintf("le reason:%s", hci::ErrorCodeText(reason).c_str()));
}

void shim::legacy::Acl::DisconnectClassic(uint16_t handle, tHCI_STATUS reason,
                                          std::string comment) {
  handler_->CallOn(pimpl_.get(), &Acl::impl::disconnect_classic, handle, reason,
                   comment);
}

void shim::legacy::Acl::DisconnectLe(uint16_t handle, tHCI_STATUS reason,
                                     std::string comment) {
  handler_->CallOn(pimpl_.get(), &Acl::impl::disconnect_le, handle, reason,
                   comment);
}

bool shim::legacy::Acl::HoldMode(uint16_t hci_handle, uint16_t max_interval,
                                 uint16_t min_interval) {
  handler_->CallOn(pimpl_.get(), &Acl::impl::HoldMode, hci_handle, max_interval,
                   min_interval);
  return false;  // TODO void
}

bool shim::legacy::Acl::SniffMode(uint16_t hci_handle, uint16_t max_interval,
                                  uint16_t min_interval, uint16_t attempt,
                                  uint16_t timeout) {
  handler_->CallOn(pimpl_.get(), &Acl::impl::SniffMode, hci_handle,
                   max_interval, min_interval, attempt, timeout);
  return false;
}

bool shim::legacy::Acl::ExitSniffMode(uint16_t hci_handle) {
  handler_->CallOn(pimpl_.get(), &Acl::impl::ExitSniffMode, hci_handle);
  return false;
}

bool shim::legacy::Acl::SniffSubrating(uint16_t hci_handle,
                                       uint16_t maximum_latency,
                                       uint16_t minimum_remote_timeout,
                                       uint16_t minimum_local_timeout) {
  handler_->CallOn(pimpl_.get(), &Acl::impl::SniffSubrating, hci_handle,
                   maximum_latency, minimum_remote_timeout,
                   minimum_local_timeout);
  return false;
}

void shim::legacy::Acl::LeSetDefaultSubrate(uint16_t subrate_min,
                                            uint16_t subrate_max,
                                            uint16_t max_latency,
                                            uint16_t cont_num,
                                            uint16_t sup_tout) {
  handler_->CallOn(pimpl_.get(), &Acl::impl::LeSetDefaultSubrate, subrate_min,
                   subrate_max, max_latency, cont_num, sup_tout);
}

void shim::legacy::Acl::LeSubrateRequest(uint16_t hci_handle,
                                         uint16_t subrate_min,
                                         uint16_t subrate_max,
                                         uint16_t max_latency,
                                         uint16_t cont_num, uint16_t sup_tout) {
  handler_->CallOn(pimpl_.get(), &Acl::impl::LeSubrateRequest, hci_handle,
                   subrate_min, subrate_max, max_latency, cont_num, sup_tout);
}

void shim::legacy::Acl::DumpConnectionHistory(int fd) const {
  pimpl_->DumpConnectionHistory(fd);
}

void shim::legacy::Acl::DisconnectAllForSuspend() {
  if (CheckForOrphanedAclConnections()) {
    std::promise<void> disconnect_promise;
    auto disconnect_future = disconnect_promise.get_future();
    handler_->CallOn(pimpl_.get(), &Acl::impl::DisconnectClassicConnections,
                     std::move(disconnect_promise));
    disconnect_future.wait();

    disconnect_promise = std::promise<void>();

    disconnect_future = disconnect_promise.get_future();
    handler_->CallOn(pimpl_.get(), &Acl::impl::DisconnectLeConnections,
                     std::move(disconnect_promise));
    disconnect_future.wait();
    LOG_WARN("Disconnected open ACL connections");
  }
}

void shim::legacy::Acl::Shutdown() {
  if (CheckForOrphanedAclConnections()) {
    std::promise<void> shutdown_promise;
    auto shutdown_future = shutdown_promise.get_future();
    handler_->CallOn(pimpl_.get(), &Acl::impl::ShutdownClassicConnections,
                     std::move(shutdown_promise));
    shutdown_future.wait();

    shutdown_promise = std::promise<void>();

    shutdown_future = shutdown_promise.get_future();
    handler_->CallOn(pimpl_.get(), &Acl::impl::ShutdownLeConnections,
                     std::move(shutdown_promise));
    shutdown_future.wait();
    LOG_WARN("Flushed open ACL connections");
  } else {
    LOG_INFO("All ACL connections have been previously closed");
  }
}

void shim::legacy::Acl::FinalShutdown() {
  std::promise<void> promise;
  auto future = promise.get_future();
  GetAclManager()->UnregisterCallbacks(this, std::move(promise));
  future.wait();
  LOG_DEBUG("Unregistered classic callbacks from gd acl manager");

  promise = std::promise<void>();
  future = promise.get_future();
  GetAclManager()->UnregisterLeCallbacks(this, std::move(promise));
  future.wait();
  LOG_DEBUG("Unregistered le callbacks from gd acl manager");

  promise = std::promise<void>();
  future = promise.get_future();
  handler_->CallOn(pimpl_.get(), &Acl::impl::FinalShutdown, std::move(promise));
  future.wait();
  LOG_INFO("Unregistered and cleared any orphaned ACL connections");
}

void shim::legacy::Acl::ClearFilterAcceptList() {
  handler_->CallOn(pimpl_.get(), &Acl::impl::clear_acceptlist);
}

void shim::legacy::Acl::LeRand(LeRandCallback cb) {
  handler_->CallOn(pimpl_.get(), &Acl::impl::le_rand, std::move(cb));
}

void shim::legacy::Acl::AddToAddressResolution(
    const hci::AddressWithType& address_with_type,
    const std::array<uint8_t, 16>& peer_irk,
    const std::array<uint8_t, 16>& local_irk) {
  handler_->CallOn(pimpl_.get(), &Acl::impl::AddToAddressResolution,
                   address_with_type, peer_irk, local_irk);
}

void shim::legacy::Acl::RemoveFromAddressResolution(
    const hci::AddressWithType& address_with_type) {
  handler_->CallOn(pimpl_.get(), &Acl::impl::RemoveFromAddressResolution,
                   address_with_type);
}

void shim::legacy::Acl::ClearAddressResolution() {
  handler_->CallOn(pimpl_.get(), &Acl::impl::ClearResolvingList);
}

void shim::legacy::Acl::SetSystemSuspendState(bool suspended) {
  handler_->CallOn(pimpl_.get(), &Acl::impl::SetSystemSuspendState, suspended);
}
