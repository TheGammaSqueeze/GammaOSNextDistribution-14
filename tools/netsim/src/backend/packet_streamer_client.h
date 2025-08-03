/*
 * Copyright 2022 The Android Open Source Project
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

#pragma once

#include "grpcpp/channel.h"
#include "netsim/packet_streamer.pb.h"

namespace netsim::packet {
/*

// Example:

auto channel = CreateChannel({.no_cli_ui = false,
                              .no_web_ui = true,
                              .netsim_args = "--dev --hci-port 12345"});
std::unique_ptr<PacketStreamer::Stub> stub = PacketStreamer::NewStub(channel);

::grpc::ClientContext context;
// set deadline, add metadata to context

PacketsRequest initial_request;
Stream bt_stream = stub.StreamPackets(&context);
initial_request.mutable_initial_info().set_name("Pixel_XL_3");
initial_request.mutable_initial_info().mutable_chip().set_kind(ChipKind::BLUETOOTH);
bt_stream.write(initial_request);

Stream wifi_stream = stub.StreamPackets(&context);
initial_request.mutable_initial_info().set_name("Pixel_XL_3");
initial_request.mutable_initial_info().mutable_chip().set_kind(ChipKind::WIFI);
wifi_stream.write(initial_request);
*/
using Stream = std::unique_ptr<
    ::grpc::ClientReaderWriter<packet::PacketRequest, packet::PacketResponse>>;

struct NetsimdOptions {
  bool no_cli_ui;
  bool no_web_ui;
  std::string netsim_args;
};

// Configure the endpoint for a server other than the local netsimd server.
void SetPacketStreamEndpoint(const std::string &endpoint);

std::shared_ptr<grpc::Channel> CreateChannel(NetsimdOptions);

// Deprecated.
std::shared_ptr<grpc::Channel> CreateChannel(
    std::string rootcanal_controller_properties_file = "");

}  // namespace netsim::packet
