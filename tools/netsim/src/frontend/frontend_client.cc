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

// Frontend client
#include "frontend/frontend_client.h"

#include <google/protobuf/util/json_util.h>
#include <grpcpp/support/status.h>

#include <chrono>
#include <cstdint>
#include <memory>
#include <string>

#include "google/protobuf/empty.pb.h"
#include "grpcpp/create_channel.h"
#include "grpcpp/security/credentials.h"
#include "grpcpp/support/status_code_enum.h"
#include "netsim-cli/src/ffi.rs.h"
#include "netsim/frontend.grpc.pb.h"
#include "netsim/frontend.pb.h"
#include "netsim/model.pb.h"
#include "util/log.h"
#include "util/os_utils.h"

namespace netsim {
namespace frontend {
namespace {
const std::chrono::duration kConnectionDeadline = std::chrono::seconds(1);

std::unique_ptr<frontend::FrontendService::Stub> NewFrontendStub(
    std::string server) {
  if (server == "") {
    return {};
  }
  std::shared_ptr<grpc::Channel> channel =
      grpc::CreateChannel(server, grpc::InsecureChannelCredentials());

  auto deadline = std::chrono::system_clock::now() + kConnectionDeadline;
  if (!channel->WaitForConnected(deadline)) {
    BtsLogWarn("Frontend gRPC channel not connected");
    return nullptr;
  }

  return frontend::FrontendService::NewStub(channel);
}

// A synchronous client for the netsim frontend service.
class FrontendClientImpl : public FrontendClient {
 public:
  FrontendClientImpl(std::unique_ptr<frontend::FrontendService::Stub> stub)
      : stub_(std::move(stub)) {}

  std::unique_ptr<ClientResult> make_result(
      const grpc::Status &status,
      const google::protobuf::Message &message) const {
    std::vector<unsigned char> message_vec(message.ByteSizeLong());
    message.SerializeToArray(message_vec.data(), message_vec.size());
    if (!status.ok()) {
      return std::make_unique<ClientResult>(false, status.error_message(),
                                            message_vec);
    }
    return std::make_unique<ClientResult>(true, "", message_vec);
  }

  // Gets the version of the network simulator service.
  std::unique_ptr<ClientResult> GetVersion() const override {
    frontend::VersionResponse response;
    grpc::ClientContext context_;
    auto status = stub_->GetVersion(&context_, {}, &response);
    return make_result(status, response);
  }

  // Gets the list of device information
  std::unique_ptr<ClientResult> ListDevice() const override {
    frontend::ListDeviceResponse response;
    grpc::ClientContext context_;
    auto status = stub_->ListDevice(&context_, {}, &response);
    return make_result(status, response);
  }

  std::unique_ptr<ClientResult> Reset() const override {
    grpc::ClientContext context_;
    google::protobuf::Empty response;
    auto status = stub_->Reset(&context_, {}, &response);
    return make_result(status, response);
  }

  std::unique_ptr<ClientResult> CreateDevice(
      rust::Vec<::rust::u8> const &request_byte_vec) const {
    frontend::CreateDeviceResponse response;
    grpc::ClientContext context_;
    frontend::CreateDeviceRequest request;
    if (!request.ParseFromArray(request_byte_vec.data(),
                                request_byte_vec.size())) {
      return make_result(
          grpc::Status(
              grpc::StatusCode::INVALID_ARGUMENT,
              "Error parsing CreateDevice request protobuf. request size:" +
                  std::to_string(request_byte_vec.size())),
          response);
    }
    auto status = stub_->CreateDevice(&context_, request, &response);
    return make_result(status, response);
  }

  // Patchs the information of the device
  std::unique_ptr<ClientResult> PatchDevice(
      rust::Vec<::rust::u8> const &request_byte_vec) const override {
    google::protobuf::Empty response;
    grpc::ClientContext context_;
    frontend::PatchDeviceRequest request;
    if (!request.ParseFromArray(request_byte_vec.data(),
                                request_byte_vec.size())) {
      return make_result(
          grpc::Status(
              grpc::StatusCode::INVALID_ARGUMENT,
              "Error parsing PatchDevice request protobuf. request size:" +
                  std::to_string(request_byte_vec.size())),
          response);
    };
    auto status = stub_->PatchDevice(&context_, request, &response);
    return make_result(status, response);
  }

  std::unique_ptr<ClientResult> DeleteChip(
      rust::Vec<::rust::u8> const &request_byte_vec) const {
    google::protobuf::Empty response;
    grpc::ClientContext context_;
    frontend::DeleteChipRequest request;
    if (!request.ParseFromArray(request_byte_vec.data(),
                                request_byte_vec.size())) {
      return make_result(
          grpc::Status(
              grpc::StatusCode::INVALID_ARGUMENT,
              "Error parsing DeleteChip request protobuf. request size:" +
                  std::to_string(request_byte_vec.size())),
          response);
    }
    auto status = stub_->DeleteChip(&context_, request, &response);
    return make_result(status, response);
  }

  // Get the list of Capture information
  std::unique_ptr<ClientResult> ListCapture() const override {
    frontend::ListCaptureResponse response;
    grpc::ClientContext context_;
    auto status = stub_->ListCapture(&context_, {}, &response);
    return make_result(status, response);
  }

  // Patch the Capture
  std::unique_ptr<ClientResult> PatchCapture(
      rust::Vec<::rust::u8> const &request_byte_vec) const override {
    google::protobuf::Empty response;
    grpc::ClientContext context_;
    frontend::PatchCaptureRequest request;
    if (!request.ParseFromArray(request_byte_vec.data(),
                                request_byte_vec.size())) {
      return make_result(
          grpc::Status(
              grpc::StatusCode::INVALID_ARGUMENT,
              "Error parsing PatchCapture request protobuf. request size:" +
                  std::to_string(request_byte_vec.size())),
          response);
    };
    auto status = stub_->PatchCapture(&context_, request, &response);
    return make_result(status, response);
  }

  // Download capture file by using ClientResponseReader to handle streaming
  // grpc
  std::unique_ptr<ClientResult> GetCapture(
      rust::Vec<::rust::u8> const &request_byte_vec,
      ClientResponseReader const &client_reader) const override {
    grpc::ClientContext context_;
    frontend::GetCaptureRequest request;
    if (!request.ParseFromArray(request_byte_vec.data(),
                                request_byte_vec.size())) {
      return make_result(
          grpc::Status(
              grpc::StatusCode::INVALID_ARGUMENT,
              "Error parsing GetCapture request protobuf. request size:" +
                  std::to_string(request_byte_vec.size())),
          google::protobuf::Empty());
    };
    auto reader = stub_->GetCapture(&context_, request);
    frontend::GetCaptureResponse chunk;
    // Read every available chunks from grpc reader
    while (reader->Read(&chunk)) {
      // Using a mutable protobuf here so the move iterator can move
      // the capture stream without copying.
      auto mut_stream = chunk.mutable_capture_stream();
      auto bytes =
          std::vector<uint8_t>(std::make_move_iterator(mut_stream->begin()),
                               std::make_move_iterator(mut_stream->end()));
      client_reader.handle_chunk(
          rust::Slice<const uint8_t>{bytes.data(), bytes.size()});
    }
    auto status = reader->Finish();
    return make_result(status, google::protobuf::Empty());
  }

  // Helper function to redirect to the correct Grpc call
  std::unique_ptr<ClientResult> SendGrpc(
      frontend::GrpcMethod const &grpc_method,
      rust::Vec<::rust::u8> const &request_byte_vec) const override {
    switch (grpc_method) {
      case frontend::GrpcMethod::GetVersion:
        return GetVersion();
      case frontend::GrpcMethod::CreateDevice:
        return CreateDevice(request_byte_vec);
      case frontend::GrpcMethod::DeleteChip:
        return DeleteChip(request_byte_vec);
      case frontend::GrpcMethod::PatchDevice:
        return PatchDevice(request_byte_vec);
      case frontend::GrpcMethod::ListDevice:
        return ListDevice();
      case frontend::GrpcMethod::Reset:
        return Reset();
      case frontend::GrpcMethod::ListCapture:
        return ListCapture();
      case frontend::GrpcMethod::PatchCapture:
        return PatchCapture(request_byte_vec);
      default:
        return make_result(grpc::Status(grpc::StatusCode::INVALID_ARGUMENT,
                                        "Unknown GrpcMethod found."),
                           google::protobuf::Empty());
    }
  }

 private:
  std::unique_ptr<frontend::FrontendService::Stub> stub_;

  static bool CheckStatus(const grpc::Status &status,
                          const std::string &message) {
    if (status.ok()) return true;
    if (status.error_code() == grpc::StatusCode::UNAVAILABLE)
      BtsLogError(
          "netsim frontend service is unavailable, "
          "please restart.");
    else
      BtsLogError("request to frontend service failed (%d) - %s",
                  status.error_code(), status.error_message().c_str());
    return false;
  }
};

}  // namespace

std::unique_ptr<FrontendClient> NewFrontendClient(const std::string &server) {
  auto stub = NewFrontendStub(server);
  return (stub == nullptr
              ? nullptr
              : std::make_unique<FrontendClientImpl>(std::move(stub)));
}

}  // namespace frontend
}  // namespace netsim