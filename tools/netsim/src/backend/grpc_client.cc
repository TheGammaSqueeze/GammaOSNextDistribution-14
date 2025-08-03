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

#include "backend/grpc_client.h"

#include <google/protobuf/util/json_util.h>

#include <cstdint>
#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>
#include <utility>

#include "grpcpp/channel.h"
#include "grpcpp/create_channel.h"
#include "grpcpp/security/credentials.h"
#include "grpcpp/server_context.h"
#include "netsim/packet_streamer.grpc.pb.h"
#include "netsim/packet_streamer.pb.h"
#include "rust/cxx.h"
#include "util/log.h"

// Backend Packet Streamer Client

namespace netsim {
namespace backend {
namespace client {

const std::chrono::duration kConnectionDeadline = std::chrono::seconds(5);

using Stream = ::grpc::ClientReaderWriter<netsim::packet::PacketRequest,
                                          netsim::packet::PacketResponse>;

std::mutex mutex_;
uint32_t stream_id_max_ = 0;

// Active StreamPacket calls
std::unordered_map<uint32_t, std::unique_ptr<Stream>> streams_;

// Single connection to a server with multiple StreamPackets calls
std::string server_;
std::shared_ptr<grpc::Channel> channel_;
grpc::ClientContext context_;
std::unique_ptr<netsim::packet::PacketStreamer::Stub> stub_;

// Call the StreamPackets RPC on server.
//
// This function allows multiple StreamPacket calls at once but only one
// connection to a server. If the server isn't already connected a new
// connection is created.

uint32_t StreamPackets(const rust::String &server_rust) {
  std::unique_lock<std::mutex> lock(mutex_);
  auto server = std::string(server_rust);
  if (server_.empty()) {
    server_ = server;
    channel_ = grpc::CreateChannel(server, grpc::InsecureChannelCredentials());
    auto deadline = std::chrono::system_clock::now() + kConnectionDeadline;
    if (!channel_->WaitForConnected(deadline)) {
      BtsLog("Failed to create packet streamer client to %s", server_.c_str());
      return -1;
    }
    stub_ = netsim::packet::PacketStreamer::NewStub(channel_);
  } else if (server_ != server) {
    BtsLog("grpc_client: multiple servers not supported");
    return -1;
  }
  auto stream = stub_->StreamPackets(&context_);
  streams_[++stream_id_max_] = std::move(stream);
  BtsLog("Created packet streamer client to %s", server_.c_str());
  return stream_id_max_;
}

/// Loop reading packets on the stream identified by stream_id and call the
//  ReadCallback function with the PacketResponse byte proto.

bool ReadPacketResponseLoop(uint32_t stream_id, ReadCallback read_fn) {
  netsim::packet::PacketResponse response;
  while (true) {
    {
      std::unique_lock<std::mutex> lock(mutex_);
      if (streams_.find(stream_id) == streams_.end()) {
        BtsLogWarn("grpc_client: no stream for stream_id %d", stream_id);
        return false;
      }
    }
    // TODO: fix locking here
    if (!streams_[stream_id]->Read(&response)) {
      BtsLogWarn("grpc_client: reading stopped stream_id %d", stream_id);
      return false;
    }
    std::vector<unsigned char> proto_bytes(response.ByteSizeLong());
    response.SerializeToArray(proto_bytes.data(), proto_bytes.size());
    rust::Slice<const uint8_t> slice{proto_bytes.data(), proto_bytes.size()};
    (*read_fn)(stream_id, slice);
  }
}

// Write a packet to the stream identified by stream_id

bool WritePacketRequest(uint32_t stream_id,
                        const rust::Slice<::std::uint8_t const> proto_bytes) {
  netsim::packet::PacketRequest request;
  if (!request.ParseFromArray(proto_bytes.data(), proto_bytes.size())) {
    BtsLogWarn("grpc_client: write failed stream_id %d", stream_id);
    return false;
  };

  std::unique_lock<std::mutex> lock(mutex_);
  if (streams_.find(stream_id) == streams_.end()) {
    BtsLogWarn("grpc_client: no stream for stream_id %d", stream_id);
    return false;
  }
  if (!streams_[stream_id]->Write(request)) {
    BtsLogWarn("grpc_client: write failed stream_id %d", stream_id);
    return false;
  }
  return true;
};

}  // namespace client
}  // namespace backend
}  // namespace netsim
