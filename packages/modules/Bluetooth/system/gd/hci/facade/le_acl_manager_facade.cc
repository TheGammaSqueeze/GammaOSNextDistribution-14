/*
 * Copyright 2019 The Android Open Source Project
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

#include "hci/facade/le_acl_manager_facade.h"

#include <memory>
#include <mutex>

#include "blueberry/facade/hci/le_acl_manager_facade.grpc.pb.h"
#include "blueberry/facade/hci/le_acl_manager_facade.pb.h"
#include "common/bind.h"
#include "grpc/grpc_event_queue.h"
#include "hci/acl_manager.h"
#include "hci/hci_packets.h"
#include "hci/octets.h"
#include "packet/raw_builder.h"

using ::grpc::ServerAsyncResponseWriter;
using ::grpc::ServerAsyncWriter;
using ::grpc::ServerContext;

using ::bluetooth::packet::RawBuilder;

namespace bluetooth {
namespace hci {
namespace facade {

using acl_manager::LeAclConnection;
using acl_manager::LeConnectionCallbacks;
using acl_manager::LeConnectionManagementCallbacks;

using namespace blueberry::facade::hci;

class LeAclManagerFacadeService : public LeAclManagerFacade::Service, public LeConnectionCallbacks {
 public:
  LeAclManagerFacadeService(AclManager* acl_manager, ::bluetooth::os::Handler* facade_handler)
      : acl_manager_(acl_manager), facade_handler_(facade_handler) {
    acl_manager_->RegisterLeCallbacks(this, facade_handler_);
  }

  ~LeAclManagerFacadeService() {
    std::unique_lock<std::mutex> lock(acl_connections_mutex_);
    for (auto& conn : acl_connections_) {
      if (conn.second.connection_ != nullptr) {
        conn.second.connection_->GetAclQueueEnd()->UnregisterDequeue();
        conn.second.connection_.reset();
      }
    }
  }

  ::grpc::Status CreateConnection(
      ::grpc::ServerContext* context,
      const CreateConnectionMsg* request,
      ::grpc::ServerWriter<LeConnectionEvent>* writer) override {
    LOG_INFO(
        "peer=%s, type=%d, id_direct=%d",
        request->peer_address().address().address().c_str(),
        request->peer_address().type(),
        request->is_direct());
    Address peer_address;
    ASSERT(Address::FromString(request->peer_address().address().address(), peer_address));
    AddressWithType peer(peer_address, static_cast<AddressType>(request->peer_address().type()));
    bool is_direct = request->is_direct();
    acl_manager_->CreateLeConnection(peer, is_direct);

    if (is_direct) {
      if (direct_connection_events_ != nullptr) {
        return ::grpc::Status(
            ::grpc::StatusCode::RESOURCE_EXHAUSTED, "Only one outstanding direct request is supported");
      }
      direct_connection_events_ = std::make_shared<::bluetooth::grpc::GrpcEventQueue<LeConnectionEvent>>(
          std::string("direct connection attempt ") + peer.ToString());
      direct_connection_address_ = peer;
      return direct_connection_events_->RunLoop(context, writer);
    }
    per_connection_events_.emplace(
        peer,
        std::make_unique<::bluetooth::grpc::GrpcEventQueue<LeConnectionEvent>>(
            std::string("connection attempt ") + peer.ToString()));
    return per_connection_events_[peer]->RunLoop(context, writer);
  }

  ::grpc::Status CancelConnection(
      ::grpc::ServerContext* /* context */,
      const ::blueberry::facade::BluetoothAddressWithType* request,
      google::protobuf::Empty* /* response */) override {
    LOG_INFO("peer=%s, type=%d", request->address().address().c_str(), request->type());
    Address peer_address;
    ASSERT(Address::FromString(request->address().address(), peer_address));
    AddressWithType peer(peer_address, static_cast<AddressType>(request->type()));
    if (peer == direct_connection_address_) {
      direct_connection_address_ = AddressWithType();
      direct_connection_events_.reset();
    } else {
      if (per_connection_events_.count(peer) == 0) {
        return ::grpc::Status(::grpc::StatusCode::INVALID_ARGUMENT, "No matching outstanding connection");
      }
    }
    acl_manager_->CancelLeConnect(peer);
    return ::grpc::Status::OK;
  }

  ::grpc::Status Disconnect(
      ::grpc::ServerContext* /* context */,
      const LeHandleMsg* request,
      ::google::protobuf::Empty* /* response */) override {
    LOG_INFO("handle=%d", request->handle());
    std::unique_lock<std::mutex> lock(acl_connections_mutex_);
    auto connection = acl_connections_.find(request->handle());
    if (connection == acl_connections_.end()) {
      LOG_ERROR("Invalid handle");
      return ::grpc::Status(::grpc::StatusCode::INVALID_ARGUMENT, "Invalid handle");
    } else {
      connection->second.connection_->Disconnect(DisconnectReason::REMOTE_USER_TERMINATED_CONNECTION);
      return ::grpc::Status::OK;
    }
  }

#define GET_CONNECTION(view)                                                         \
  std::map<uint16_t, Connection>::iterator connection;                               \
  do {                                                                               \
    if (!view.IsValid()) {                                                           \
      return ::grpc::Status(::grpc::StatusCode::INVALID_ARGUMENT, "Invalid handle"); \
    }                                                                                \
    std::unique_lock<std::mutex> lock(acl_connections_mutex_);                       \
    connection = acl_connections_.find(view.GetConnectionHandle());                  \
    if (connection == acl_connections_.end()) {                                      \
      return ::grpc::Status(::grpc::StatusCode::INVALID_ARGUMENT, "Invalid handle"); \
    }                                                                                \
  } while (0)

  ::grpc::Status ConnectionCommand(
      ::grpc::ServerContext* /* context */,
      const LeConnectionCommandMsg* request,
      ::google::protobuf::Empty* /* response */) override {
    LOG_INFO("size=%zu", request->packet().size());
    auto command_view =
        ConnectionManagementCommandView::Create(AclCommandView::Create(CommandView::Create(PacketView<kLittleEndian>(
            std::make_shared<std::vector<uint8_t>>(request->packet().begin(), request->packet().end())))));
    if (!command_view.IsValid()) {
      return ::grpc::Status(::grpc::StatusCode::INVALID_ARGUMENT, "Invalid command packet");
    }
    LOG_INFO("opcode=%s", OpCodeText(command_view.GetOpCode()).c_str());
    switch (command_view.GetOpCode()) {
      case OpCode::DISCONNECT: {
        auto view = DisconnectView::Create(command_view);
        GET_CONNECTION(view);
        connection->second.connection_->Disconnect(view.GetReason());
        return ::grpc::Status::OK;
      }
      default:
        return ::grpc::Status(::grpc::StatusCode::INVALID_ARGUMENT, "Invalid command packet");
    }
  }
#undef GET_CONNECTION

  ::grpc::Status FetchIncomingConnection(
      ::grpc::ServerContext* context,
      const google::protobuf::Empty* /* request */,
      ::grpc::ServerWriter<LeConnectionEvent>* writer) override {
    LOG_INFO("wait for one incoming connection");
    if (incoming_connection_events_ != nullptr) {
      return ::grpc::Status(
          ::grpc::StatusCode::RESOURCE_EXHAUSTED, "Only one outstanding incoming connection is supported");
    }
    incoming_connection_events_ =
        std::make_unique<::bluetooth::grpc::GrpcEventQueue<LeConnectionEvent>>(std::string("incoming connection "));
    return incoming_connection_events_->RunLoop(context, writer);
  }

  ::grpc::Status AddDeviceToResolvingList(
      ::grpc::ServerContext* /* context */,
      const IrkMsg* request,
      ::google::protobuf::Empty* /* response */) override {
    LOG_INFO("peer=%s, type=%d", request->peer().address().address().c_str(), request->peer().type());
    Address peer_address;
    ASSERT(Address::FromString(request->peer().address().address(), peer_address));
    AddressWithType peer(peer_address, static_cast<AddressType>(request->peer().type()));

    auto request_peer_irk_length = request->peer_irk().end() - request->peer_irk().begin();

    if (request_peer_irk_length != kOctet16Length) {
      return ::grpc::Status(::grpc::StatusCode::INVALID_ARGUMENT, "Invalid Peer IRK");
    }

    auto request_local_irk_length = request->local_irk().end() - request->local_irk().begin();
    if (request_local_irk_length != kOctet16Length) {
      return ::grpc::Status(::grpc::StatusCode::INVALID_ARGUMENT, "Invalid Local IRK");
    }

    Octet16 peer_irk = {};
    Octet16 local_irk = {};

    std::vector<uint8_t> peer_irk_data(request->peer_irk().begin(), request->peer_irk().end());
    std::copy_n(peer_irk_data.begin(), kOctet16Length, peer_irk.begin());

    std::vector<uint8_t> local_irk_data(request->local_irk().begin(), request->local_irk().end());
    std::copy_n(local_irk_data.begin(), kOctet16Length, local_irk.begin());

    acl_manager_->AddDeviceToResolvingList(peer, peer_irk, local_irk);
    return ::grpc::Status::OK;
  }

  ::grpc::Status SendAclData(
      ::grpc::ServerContext* /* context */,
      const LeAclData* request,
      ::google::protobuf::Empty* /* response */) override {
    LOG_INFO("handle=%d, size=%zu", request->handle(), request->payload().size());
    std::promise<void> promise;
    auto future = promise.get_future();
    {
      std::unique_lock<std::mutex> lock(acl_connections_mutex_);
      auto connection = acl_connections_.find(request->handle());
      if (connection == acl_connections_.end()) {
        return ::grpc::Status(::grpc::StatusCode::INVALID_ARGUMENT, "Invalid handle");
      }
      connection->second.connection_->GetAclQueueEnd()->RegisterEnqueue(
          facade_handler_,
          common::Bind(
              &LeAclManagerFacadeService::enqueue_packet,
              common::Unretained(this),
              common::Unretained(request),
              common::Passed(std::move(promise))));
      auto status = future.wait_for(std::chrono::milliseconds(1000));
      if (status != std::future_status::ready) {
        return ::grpc::Status(::grpc::StatusCode::RESOURCE_EXHAUSTED, "Can't send packet");
      }
    }
    return ::grpc::Status::OK;
  }

  std::unique_ptr<BasePacketBuilder> enqueue_packet(const LeAclData* request, std::promise<void> promise) {
    auto connection = acl_connections_.find(request->handle());
    ASSERT_LOG(connection != acl_connections_.end(), "handle %d", request->handle());
    connection->second.connection_->GetAclQueueEnd()->UnregisterEnqueue();
    std::unique_ptr<RawBuilder> packet =
        std::make_unique<RawBuilder>(std::vector<uint8_t>(request->payload().begin(), request->payload().end()));
    promise.set_value();
    return packet;
  }

  ::grpc::Status FetchAclData(
      ::grpc::ServerContext* context, const LeHandleMsg* request, ::grpc::ServerWriter<LeAclData>* writer) override {
    LOG_INFO("handle=%d", request->handle());
    auto connection = acl_connections_.find(request->handle());
    if (connection == acl_connections_.end()) {
      return ::grpc::Status(::grpc::StatusCode::INVALID_ARGUMENT, "Invalid handle");
    }
    return connection->second.pending_acl_data_.RunLoop(context, writer);
  }

  static inline std::string builder_to_string(std::unique_ptr<BasePacketBuilder> builder) {
    std::vector<uint8_t> bytes;
    BitInserter bit_inserter(bytes);
    builder->Serialize(bit_inserter);
    return std::string(bytes.begin(), bytes.end());
  }

  void on_incoming_acl(std::shared_ptr<LeAclConnection> connection, uint16_t handle) {
    LOG_INFO("handle=%d, addr=%s", connection->GetHandle(),
              ADDRESS_TO_LOGGABLE_CSTR(connection->GetRemoteAddress()));
    auto packet = connection->GetAclQueueEnd()->TryDequeue();
    auto connection_tracker = acl_connections_.find(handle);
    ASSERT_LOG(connection_tracker != acl_connections_.end(), "handle %d", handle);
    LeAclData acl_data;
    acl_data.set_handle(handle);
    acl_data.set_payload(std::string(packet->begin(), packet->end()));
    connection_tracker->second.pending_acl_data_.OnIncomingEvent(acl_data);
  }

  void OnLeConnectSuccess(AddressWithType peer, std::unique_ptr<LeAclConnection> connection) override {
    LOG_INFO("handle=%d, addr=%s", connection->GetHandle(),
             ADDRESS_TO_LOGGABLE_CSTR(peer));
    std::unique_lock<std::mutex> lock(acl_connections_mutex_);
    std::shared_ptr<LeAclConnection> shared_connection = std::move(connection);
    uint16_t handle = shared_connection->GetHandle();
    auto role = shared_connection->GetRole();
    if (role == Role::PERIPHERAL) {
      ASSERT(incoming_connection_events_ != nullptr);
      if (per_connection_events_.find(peer) == per_connection_events_.end()) {
        per_connection_events_.emplace(peer, incoming_connection_events_);
      } else {
        per_connection_events_[peer] = incoming_connection_events_;
      }
      incoming_connection_events_.reset();
    } else if (direct_connection_address_ == peer) {
      direct_connection_address_ = AddressWithType();
      per_connection_events_.emplace(peer, direct_connection_events_);
      direct_connection_events_.reset();
    } else {
      ASSERT_LOG(
          per_connection_events_.count(peer) > 0,
          "No connection request for %s", ADDRESS_TO_LOGGABLE_CSTR(peer));
    }
    acl_connections_.erase(handle);
    acl_connections_.emplace(
        std::piecewise_construct,
        std::forward_as_tuple(handle),
        std::forward_as_tuple(handle, shared_connection, per_connection_events_[peer]));
    shared_connection->GetAclQueueEnd()->RegisterDequeue(
        facade_handler_,
        common::Bind(&LeAclManagerFacadeService::on_incoming_acl, common::Unretained(this), shared_connection, handle));
    auto callbacks = acl_connections_.find(handle)->second.GetCallbacks();
    shared_connection->RegisterCallbacks(callbacks, facade_handler_);
    {
      std::unique_ptr<BasePacketBuilder> builder = LeConnectionCompleteBuilder::Create(
          ErrorCode::SUCCESS, handle, role, peer.GetAddressType(), peer.GetAddress(), 1, 2, 3, ClockAccuracy::PPM_20);
      LeConnectionEvent success;
      success.set_payload(builder_to_string(std::move(builder)));
      per_connection_events_[peer]->OnIncomingEvent(success);
    }
  }

  void OnLeConnectFail(AddressWithType address, ErrorCode reason) override {
    LOG_INFO("addr=%s, reason=%s",
             ADDRESS_TO_LOGGABLE_CSTR(address), ErrorCodeText(reason).c_str());
    std::unique_ptr<BasePacketBuilder> builder = LeConnectionCompleteBuilder::Create(
        reason, 0, Role::CENTRAL, address.GetAddressType(), address.GetAddress(), 0, 0, 0, ClockAccuracy::PPM_20);
    LeConnectionEvent fail;
    fail.set_payload(builder_to_string(std::move(builder)));
    if (address == direct_connection_address_) {
      direct_connection_address_ = AddressWithType();
      direct_connection_events_->OnIncomingEvent(fail);
    } else {
      per_connection_events_[address]->OnIncomingEvent(fail);
    }
  }

  class Connection : public LeConnectionManagementCallbacks {
   public:
    Connection(
        uint16_t handle,
        std::shared_ptr<LeAclConnection> connection,
        std::shared_ptr<::bluetooth::grpc::GrpcEventQueue<LeConnectionEvent>> event_stream)
        : handle_(handle), connection_(std::move(connection)), event_stream_(std::move(event_stream)) {}
    void OnConnectionUpdate(
        hci::ErrorCode /* hci_status */,
        uint16_t connection_interval,
        uint16_t connection_latency,
        uint16_t supervision_timeout) override {
      LOG_INFO(
          "interval: 0x%hx, latency: 0x%hx, timeout 0x%hx",
          connection_interval,
          connection_latency,
          supervision_timeout);
    }

    void OnDataLengthChange(uint16_t tx_octets, uint16_t tx_time, uint16_t rx_octets, uint16_t rx_time) override {
      LOG_INFO(
          "tx_octets: 0x%hx, tx_time: 0x%hx, rx_octets 0x%hx, rx_time 0x%hx", tx_octets, tx_time, rx_octets, rx_time);
    }

    void OnPhyUpdate(
        hci::ErrorCode /* hci_status */, uint8_t /* tx_phy */, uint8_t /* rx_phy */) override {}
    void OnDisconnection(ErrorCode reason) override {
      LOG_INFO("reason: %s", ErrorCodeText(reason).c_str());
      std::unique_ptr<BasePacketBuilder> builder =
          DisconnectionCompleteBuilder::Create(ErrorCode::SUCCESS, handle_, reason);
      LeConnectionEvent disconnection;
      disconnection.set_payload(builder_to_string(std::move(builder)));
      event_stream_->OnIncomingEvent(disconnection);
    }

    void OnReadRemoteVersionInformationComplete(
        hci::ErrorCode /* hci_status */,
        uint8_t /* lmp_version */,
        uint16_t /* manufacturer_name */,
        uint16_t /* sub_version */) override {}
    void OnLeReadRemoteFeaturesComplete(
        hci::ErrorCode /* hci_status */, uint64_t /* features */) override {}

    LeConnectionManagementCallbacks* GetCallbacks() {
      return this;
    }
    void OnLeSubrateChange(
        hci::ErrorCode hci_status,
        uint16_t subrate_factor,
        uint16_t peripheral_latency,
        uint16_t continuation_number,
        uint16_t supervision_timeout) override {
      LOG_INFO(
          "hci_status: %s, subrate_factor: %#hx, peripheral_latency: %#hx, continuation_number: %#hx, "
          "supervision_timeout: %#hx",
          ErrorCodeText(hci_status).c_str(),
          subrate_factor,
          peripheral_latency,
          continuation_number,
          supervision_timeout);
    }

    uint16_t handle_;
    std::shared_ptr<LeAclConnection> connection_;
    std::shared_ptr<::bluetooth::grpc::GrpcEventQueue<LeConnectionEvent>> event_stream_;
    ::bluetooth::grpc::GrpcEventQueue<LeAclData> pending_acl_data_{std::string("PendingAclData") +
                                                                   std::to_string(handle_)};
  };

  ::grpc::Status IsOnBackgroundList(
      ::grpc::ServerContext* /* context */,
      const ::blueberry::facade::hci::BackgroundRequestMsg* request,
      ::blueberry::facade::hci::BackgroundResultMsg* msg) {
    Address peer_address;
    ASSERT(Address::FromString(request->peer_address().address().address(), peer_address));
    AddressWithType peer(peer_address, static_cast<AddressType>(request->peer_address().type()));
    std::promise<bool> promise;
    auto future = promise.get_future();
    acl_manager_->IsOnBackgroundList(peer, std::move(promise));
    msg->set_is_on_background_list(future.get());
    return ::grpc::Status::OK;
  }

  ::grpc::Status RemoveFromBackgroundList(
      ::grpc::ServerContext* /* context */,
      const ::blueberry::facade::hci::BackgroundRequestMsg* request,
      ::google::protobuf::Empty* /* response */) {
    Address peer_address;
    ASSERT(Address::FromString(request->peer_address().address().address(), peer_address));
    AddressWithType peer(peer_address, static_cast<AddressType>(request->peer_address().type()));
    acl_manager_->RemoveFromBackgroundList(peer);
    return ::grpc::Status::OK;
  }

 private:
  AclManager* acl_manager_;
  ::bluetooth::os::Handler* facade_handler_;
  mutable std::mutex acl_connections_mutex_;
  std::map<bluetooth::hci::AddressWithType, std::shared_ptr<::bluetooth::grpc::GrpcEventQueue<LeConnectionEvent>>>
      per_connection_events_;
  std::shared_ptr<::bluetooth::grpc::GrpcEventQueue<LeConnectionEvent>> direct_connection_events_;
  bluetooth::hci::AddressWithType direct_connection_address_;
  std::shared_ptr<::bluetooth::grpc::GrpcEventQueue<LeConnectionEvent>> incoming_connection_events_;
  std::map<uint16_t, Connection> acl_connections_;
};

void LeAclManagerFacadeModule::ListDependencies(ModuleList* list) const {
  ::bluetooth::grpc::GrpcFacadeModule::ListDependencies(list);
  list->add<AclManager>();
}

void LeAclManagerFacadeModule::Start() {
  ::bluetooth::grpc::GrpcFacadeModule::Start();
  service_ = new LeAclManagerFacadeService(GetDependency<AclManager>(), GetHandler());
}

void LeAclManagerFacadeModule::Stop() {
  delete service_;
  ::bluetooth::grpc::GrpcFacadeModule::Stop();
}

::grpc::Service* LeAclManagerFacadeModule::GetService() const {
  return service_;
}

const ModuleFactory LeAclManagerFacadeModule::Factory =
    ::bluetooth::ModuleFactory([]() { return new LeAclManagerFacadeModule(); });

}  // namespace facade
}  // namespace hci
}  // namespace bluetooth
