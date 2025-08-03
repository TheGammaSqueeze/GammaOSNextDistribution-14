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

#include "frontend/frontend_server.h"

#include <google/protobuf/util/json_util.h>

#include <iostream>
#include <memory>
#include <string>
#include <utility>

#include "google/protobuf/empty.pb.h"
#include "grpcpp/server_context.h"
#include "grpcpp/support/status.h"
#include "netsim-daemon/src/ffi.rs.h"
#include "netsim/frontend.grpc.pb.h"
#include "netsim/frontend.pb.h"

namespace netsim {
namespace {

/// The C++ implementation of the CxxServerResponseWriter interface. This is
/// used by the gRPC server to invoke the Rust pcap handler and process a
/// responses.
class CxxServerResponseWritable : public frontend::CxxServerResponseWriter {
 public:
  CxxServerResponseWritable()
      : grpc_writer_(nullptr), err(""), is_ok(false), body(""), length(0){};
  CxxServerResponseWritable(
      grpc::ServerWriter<netsim::frontend::GetCaptureResponse> *grpc_writer)
      : grpc_writer_(grpc_writer), err(""), is_ok(false), body(""), length(0){};

  void put_error(unsigned int error_code,
                 const std::string &response) const override {
    err = std::to_string(error_code) + ": " + response;
    is_ok = false;
  }

  void put_ok_with_length(const std::string &mime_type,
                          std::size_t length) const override {
    this->length = length;
    is_ok = true;
  }

  void put_chunk(rust::Slice<const uint8_t> chunk) const override {
    netsim::frontend::GetCaptureResponse response;
    response.set_capture_stream(std::string(chunk.begin(), chunk.end()));
    is_ok = grpc_writer_->Write(response);
  }

  void put_ok(const std::string &mime_type,
              const std::string &body) const override {
    this->body = body;
    is_ok = true;
  }

  mutable grpc::ServerWriter<netsim::frontend::GetCaptureResponse>
      *grpc_writer_;
  mutable std::string err;
  mutable bool is_ok;
  mutable std::string body;
  mutable std::size_t length;
};

class FrontendServer final : public frontend::FrontendService::Service {
 public:
  grpc::Status GetVersion(grpc::ServerContext *context,
                          const google::protobuf::Empty *empty,
                          frontend::VersionResponse *reply) {
    reply->set_version(std::string(netsim::GetVersion()));
    return grpc::Status::OK;
  }

  grpc::Status ListDevice(grpc::ServerContext *context,
                          const google::protobuf::Empty *empty,
                          frontend::ListDeviceResponse *reply) {
    CxxServerResponseWritable writer;
    HandleDeviceCxx(writer, "GET", "", "");
    if (writer.is_ok) {
      google::protobuf::util::JsonStringToMessage(writer.body, reply);
      return grpc::Status::OK;
    }
    return grpc::Status(grpc::StatusCode::UNKNOWN, writer.err);
  }

  grpc::Status CreateDevice(grpc::ServerContext *context,
                            const frontend::CreateDeviceRequest *request,
                            frontend::CreateDeviceResponse *response) {
    CxxServerResponseWritable writer;
    std::string request_json;
    google::protobuf::util::MessageToJsonString(*request, &request_json);
    HandleDeviceCxx(writer, "POST", "", request_json);
    if (writer.is_ok) {
      google::protobuf::util::JsonStringToMessage(writer.body, response);
      return grpc::Status::OK;
    }
    return grpc::Status(grpc::StatusCode::UNKNOWN, writer.err);
  }

  grpc::Status DeleteChip(grpc::ServerContext *context,
                          const frontend::DeleteChipRequest *request,
                          google::protobuf::Empty *response) {
    CxxServerResponseWritable writer;
    std::string request_json;
    google::protobuf::util::MessageToJsonString(*request, &request_json);
    HandleDeviceCxx(writer, "DELETE", "", request_json);
    if (writer.is_ok) {
      google::protobuf::util::JsonStringToMessage(writer.body, response);
      return grpc::Status::OK;
    }
    return grpc::Status(grpc::StatusCode::UNKNOWN, writer.err);
  }

  grpc::Status PatchDevice(grpc::ServerContext *context,
                           const frontend::PatchDeviceRequest *request,
                           google::protobuf::Empty *response) {
    CxxServerResponseWritable writer;
    std::string request_json;
    google::protobuf::util::MessageToJsonString(*request, &request_json);
    auto device = request->device();
    // device.id() starts from 1.
    // If you don't populate the id, you must fill the name field.
    if (device.id() == 0) {
      HandleDeviceCxx(writer, "PATCH", "", request_json);
    } else {
      HandleDeviceCxx(writer, "PATCH", std::to_string(device.id()),
                      request_json);
    }
    if (writer.is_ok) {
      return grpc::Status::OK;
    }
    return grpc::Status(grpc::StatusCode::UNKNOWN, writer.err);
  }

  grpc::Status Reset(grpc::ServerContext *context,
                     const google::protobuf::Empty *request,
                     google::protobuf::Empty *empty) {
    CxxServerResponseWritable writer;
    HandleDeviceCxx(writer, "PUT", "", "");
    if (writer.is_ok) {
      return grpc::Status::OK;
    }
    return grpc::Status(grpc::StatusCode::UNKNOWN, writer.err);
  }

  grpc::Status ListCapture(grpc::ServerContext *context,
                           const google::protobuf::Empty *empty,
                           frontend::ListCaptureResponse *reply) {
    CxxServerResponseWritable writer;
    HandleCaptureCxx(writer, "GET", "", "");
    if (writer.is_ok) {
      google::protobuf::util::JsonStringToMessage(writer.body, reply);
      return grpc::Status::OK;
    }
    return grpc::Status(grpc::StatusCode::UNKNOWN, writer.err);
  }

  grpc::Status PatchCapture(grpc::ServerContext *context,
                            const frontend::PatchCaptureRequest *request,
                            google::protobuf::Empty *response) {
    CxxServerResponseWritable writer;
    HandleCaptureCxx(writer, "PATCH", std::to_string(request->id()),
                     std::to_string(request->patch().state()));
    if (writer.is_ok) {
      return grpc::Status::OK;
    }
    return grpc::Status(grpc::StatusCode::UNKNOWN, writer.err);
  }
  grpc::Status GetCapture(
      grpc::ServerContext *context,
      const netsim::frontend::GetCaptureRequest *request,
      grpc::ServerWriter<netsim::frontend::GetCaptureResponse> *grpc_writer) {
    CxxServerResponseWritable writer(grpc_writer);
    HandleCaptureCxx(writer, "GET", std::to_string(request->id()), "");
    if (writer.is_ok) {
      return grpc::Status::OK;
    }
    return grpc::Status(grpc::StatusCode::UNKNOWN, writer.err);
  }
};
}  // namespace

std::unique_ptr<frontend::FrontendService::Service> GetFrontendService() {
  return std::make_unique<FrontendServer>();
}

}  // namespace netsim
