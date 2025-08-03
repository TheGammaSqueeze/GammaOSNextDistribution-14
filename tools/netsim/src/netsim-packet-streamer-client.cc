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

#include <iostream>

#include "backend/packet_streamer_client.h"
#include "netsim/packet_streamer.grpc.pb.h"

using netsim::common::ChipKind;

int main(int argc, char *argv[]) {
  // Finding the netsimd binary requires this env variable when run
  // interactively export ANDROID_EMULATOR_LAUNCHER_DIR=./objs

  auto channel = netsim::packet::CreateChannel("");

  std::unique_ptr<netsim::packet::PacketStreamer::Stub> stub =
      netsim::packet::PacketStreamer::NewStub(channel);

  grpc::ClientContext context;

  netsim::packet::PacketRequest initial_request;
  netsim::packet::Stream bt_stream = stub->StreamPackets(&context);
  initial_request.mutable_initial_info()->set_name("emulator-5554");
  initial_request.mutable_initial_info()->mutable_chip()->set_kind(
      ChipKind::BLUETOOTH);
  bt_stream->Write(initial_request);

  std::cout << "Press enter to close the connection...";
  std::string s;
  getline(std::cin, s);

  return (0);
}
