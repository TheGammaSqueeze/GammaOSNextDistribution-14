// Copyright 2022 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "backend/grpc_server.h"

#include <google/protobuf/util/json_util.h>
#include <stdlib.h>

#include <cstdint>
#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>

#include "google/protobuf/empty.pb.h"
#include "grpcpp/server_context.h"
#include "grpcpp/support/status.h"
#include "netsim-daemon/src/ffi.rs.h"
#include "netsim/common.pb.h"
#include "netsim/packet_streamer.grpc.pb.h"
#include "netsim/packet_streamer.pb.h"
#include "util/log.h"

#ifdef NETSIM_ANDROID_EMULATOR
#include "android-qemu2-glue/netsim/libslirp_driver.h"
#endif

namespace netsim {
namespace backend {
namespace {

using netsim::common::ChipKind;

using Stream =
    ::grpc::ServerReaderWriter<packet::PacketResponse, packet::PacketRequest>;

using netsim::startup::Chip;

// Mapping from chip_id to streams.
std::unordered_map<uint32_t, Stream *> chip_id_to_stream;

// Libslirp is not thread safe. Use a lock to prevent concurrent access to
// libslirp.
std::mutex gSlirpMutex;

// Service handles the gRPC StreamPackets requests.

class ServiceImpl final : public packet::PacketStreamer::Service {
 public:
  ::grpc::Status StreamPackets(::grpc::ServerContext *context,
                               Stream *stream) override {
    // Now connected to a peer issuing a bi-directional streaming grpc
    auto peer = context->peer();
    BtsLogInfo("grpc_server new packet_stream for peer %s", peer.c_str());

    packet::PacketRequest request;

    // First packet must have initial_info describing the peer
    bool success = stream->Read(&request);
    if (!success || !request.has_initial_info()) {
      BtsLogError("ServiceImpl no initial information or stream closed");
      return ::grpc::Status(::grpc::StatusCode::INVALID_ARGUMENT,
                            "Missing initial_info in first packet.");
    }

    auto device_name = request.initial_info().name();
    auto chip_kind = request.initial_info().chip().kind();
    // multiple chips of the same chip_kind for a device have a name
    auto chip_name = request.initial_info().chip().id();
    auto manufacturer = request.initial_info().chip().manufacturer();
    auto product_name = request.initial_info().chip().product_name();
    auto chip_address = request.initial_info().chip().address();
    auto bt_properties = request.initial_info().chip().bt_properties();
    // Add a new chip to the device
    std::string chip_kind_string;
    switch (chip_kind) {
      case common::ChipKind::BLUETOOTH:
        chip_kind_string = "BLUETOOTH";
        break;
      case common::ChipKind::WIFI:
        chip_kind_string = "WIFI";
        break;
      case common::ChipKind::UWB:
        chip_kind_string = "UWB";
        break;
      default:
        chip_kind_string = "UNSPECIFIED";
        break;
    }

    std::vector<unsigned char> message_vec(bt_properties.ByteSizeLong());
    if (!bt_properties.SerializeToArray(message_vec.data(),
                                        message_vec.size())) {
      BtsLogError("Failed to serialize bt_properties to bytes");
    }

    auto result = netsim::device::AddChipCxx(
        peer, device_name, chip_kind_string, chip_address, chip_name,
        manufacturer, product_name, message_vec);
    if (result->IsError()) {
      return ::grpc::Status(::grpc::StatusCode::INVALID_ARGUMENT,
                            "AddChipCxx failed to add chip into netsim");
    }
    uint32_t device_id = result->GetDeviceId();
    uint32_t chip_id = result->GetChipId();

    BtsLogInfo(
        "grpc_server: adding chip - chip_id: %d, "
        "device_name: "
        "%s",
        chip_id, device_name.c_str());
    // connect packet responses from chip facade to the peer
    chip_id_to_stream[chip_id] = stream;
    netsim::transport::RegisterGrpcTransport(chip_id);
    this->ProcessRequests(stream, chip_id, chip_kind);

    // no longer able to send responses to peer
    netsim::transport::UnregisterGrpcTransport(chip_id);
    chip_id_to_stream.erase(chip_id);

    // Remove the chip from the device
    netsim::device::RemoveChipCxx(device_id, chip_id);

    BtsLogInfo(
        "grpc_server: removing chip - chip_id: %d, "
        "device_name: "
        "%s",
        chip_id, device_name.c_str());

    return ::grpc::Status::OK;
  }

  // Convert a protobuf bytes field into shared_ptr<<vec<uint8_t>>.
  //
  // Release ownership of the bytes field and convert it to a vector using move
  // iterators. No copy when called with a mutable reference.
  std::shared_ptr<std::vector<uint8_t>> ToSharedVec(std::string *bytes_field) {
    return std::make_shared<std::vector<uint8_t>>(
        std::make_move_iterator(bytes_field->begin()),
        std::make_move_iterator(bytes_field->end()));
  }

  // Process requests in a loop forwarding packets to the packet_hub and
  // returning when the channel is closed.
  void ProcessRequests(Stream *stream, uint32_t chip_id,
                       common::ChipKind chip_kind) {
    packet::PacketRequest request;
    while (true) {
      if (!stream->Read(&request)) {
        BtsLogWarn("grpc_server: reading stopped - chip_id: %d", chip_id);
        break;
      }
      // All kinds possible (bt, uwb, wifi), but each rpc only streames one.
      if (chip_kind == common::ChipKind::BLUETOOTH) {
        if (!request.has_hci_packet()) {
          BtsLogWarn("grpc_server: unknown packet type from chip_id: %d",
                     chip_id);
          continue;
        }
        auto packet_type = request.hci_packet().packet_type();
        auto packet =
            ToSharedVec(request.mutable_hci_packet()->mutable_packet());
        echip::HandleRequestCxx(chip_id, *packet, packet_type);
      } else if (chip_kind == common::ChipKind::WIFI) {
        if (!request.has_packet()) {
          BtsLogWarn("grpc_server: unknown packet type from chip_id: %d",
                     chip_id);
          continue;
        }
        auto packet = ToSharedVec(request.mutable_packet());
        {
          std::lock_guard<std::mutex> guard(gSlirpMutex);
          echip::HandleRequestCxx(chip_id, *packet,
                                  packet::HCIPacket::HCI_PACKET_UNSPECIFIED);
        }
#ifdef NETSIM_ANDROID_EMULATOR
        // main_loop_wait is a non-blocking call where fds maintained by the
        // WiFi service (slirp) are polled and serviced for I/O. When any fd
        // become ready for I/O, slirp_pollfds_poll() will be invoked to read
        // from the open sockets therefore incoming packets are serviced.
        {
          std::lock_guard<std::mutex> guard(gSlirpMutex);
          android::qemu2::libslirp_main_loop_wait(true);
        }
#endif
      } else {
        // TODO: add UWB here
        BtsLogWarn("grpc_server: unknown chip_kind");
      }
    }
  }
};
}  // namespace

// handle_response is called by packet_hub to forward a response to the gRPC
// stream associated with chip_id.
//
// When writing, the packet is copied because is borrowed from a shared_ptr and
// grpc++ doesn't know about smart pointers.
void HandleResponse(uint32_t chip_id, const std::vector<uint8_t> &packet,
                    packet::HCIPacket_PacketType packet_type) {
  auto stream = chip_id_to_stream[chip_id];
  if (stream) {
    // TODO: lock or caller here because gRPC does not allow overlapping writes.
    packet::PacketResponse response;
    // Copies the borrowed packet for output
    auto str_packet = std::string(packet.begin(), packet.end());
    if (packet_type != packet::HCIPacket_PacketType_HCI_PACKET_UNSPECIFIED) {
      response.mutable_hci_packet()->set_packet_type(packet_type);
      response.mutable_hci_packet()->set_packet(str_packet);
    } else {
      response.set_packet(str_packet);
    }
    if (!stream->Write(response)) {
      BtsLogWarn("grpc_server: write failed for chip_id: %d", chip_id);
    }
  } else {
    BtsLogWarn("grpc_server: no stream for chip_id: %d", chip_id);
  }
}

// for cxx
void HandleResponseCxx(uint32_t chip_id, const rust::Vec<rust::u8> &packet,
                       /* optional */ uint8_t packet_type) {
  std::vector<uint8_t> vec(packet.begin(), packet.end());
  HandleResponse(chip_id, vec, packet::HCIPacket_PacketType(packet_type));
}

}  // namespace backend

std::unique_ptr<packet::PacketStreamer::Service> GetBackendService() {
  return std::make_unique<backend::ServiceImpl>();
}
}  // namespace netsim
