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

// Frontend client
#pragma once

#include <cstdint>
#include <memory>
#include <vector>

#include "rust/cxx.h"

namespace netsim {
namespace frontend {

enum class GrpcMethod : ::std::uint8_t;
struct ClientResponseReader;

class ClientResult {
 public:
  ClientResult(bool is_ok, const std::string &err,
               const std::vector<unsigned char> &byte_vec)
      : is_ok_(is_ok), err_(err), byte_vec_(byte_vec){};

  bool IsOk() const { return is_ok_; };
  rust::String Err() const { return err_; };
  const std::vector<unsigned char> &ByteVec() const { return byte_vec_; };

 private:
  bool is_ok_;
  std::string err_;
  const std::vector<unsigned char> byte_vec_;
};

class FrontendClient {
 public:
  virtual ~FrontendClient(){};
  virtual std::unique_ptr<ClientResult> SendGrpc(
      frontend::GrpcMethod const &grpc_method,
      rust::Vec<rust::u8> const &request_byte_vec) const = 0;
  virtual std::unique_ptr<ClientResult> GetVersion() const = 0;
  virtual std::unique_ptr<ClientResult> ListDevice() const = 0;
  virtual std::unique_ptr<ClientResult> PatchDevice(
      rust::Vec<rust::u8> const &request_byte_vec) const = 0;
  virtual std::unique_ptr<ClientResult> Reset() const = 0;
  virtual std::unique_ptr<ClientResult> ListCapture() const = 0;
  virtual std::unique_ptr<ClientResult> PatchCapture(
      rust::Vec<rust::u8> const &request_byte_vec) const = 0;
  virtual std::unique_ptr<ClientResult> GetCapture(
      rust::Vec<::rust::u8> const &request_byte_vec,
      ClientResponseReader const &client_reader) const = 0;
};

std::unique_ptr<FrontendClient> NewFrontendClient(const std::string &server);

}  // namespace frontend
}  // namespace netsim
