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

#pragma once
#include <string>

#include "grpcpp/support/sync_stream.h"
#include "netsim/frontend.pb.h"
#include "rust/cxx.h"

namespace netsim {
namespace frontend {

/// The C++ definition of the CxxServerResponseWriter interface for CXX.
class CxxServerResponseWriter {
 public:
  CxxServerResponseWriter(){};
  CxxServerResponseWriter(
      grpc::ServerWriter<netsim::frontend::GetCaptureResponse> *grpc_writer_){};
  virtual ~CxxServerResponseWriter() = default;
  virtual void put_error(unsigned int error_code,
                         const std::string &response) const = 0;
  virtual void put_ok_with_length(const std::string &mime_type,
                                  std::size_t length) const = 0;
  virtual void put_chunk(rust::Slice<const uint8_t> chunk) const = 0;
  virtual void put_ok(const std::string &mime_type,
                      const std::string &body) const = 0;
};

}  // namespace frontend
}  // namespace netsim
