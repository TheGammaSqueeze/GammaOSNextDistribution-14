/*
 * Copyright 2023 The Android Open Source Project
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

#include "mmc/daemon/service.h"

#include <base/functional/bind.h>
#include <base/functional/callback_helpers.h>
#include <base/logging.h>
#include <base/stl_util.h>
#include <base/task/single_thread_task_runner.h>
#include <base/unguessable_token.h>
#include <poll.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/un.h>
#include <unistd.h>

#include <cerrno>
#include <cstring>
#include <future>

#include "common/message_loop_thread.h"
#include "mmc/codec_server/hfp_lc3_mmc_decoder.h"
#include "mmc/codec_server/hfp_lc3_mmc_encoder.h"
#include "mmc/daemon/constants.h"
#include "mmc/mmc_interface/mmc_interface.h"
#include "mmc/proto/mmc_service.pb.h"

#if !defined(EXCLUDE_NONSTANDARD_CODECS)
#include "mmc/codec_server/a2dp_aac_mmc_encoder.h"
#endif

namespace mmc {
namespace {
// Task that would run on the thread.
void StartSocketListener(int fd, struct sockaddr_un addr,
                         std::promise<void> task_ended,
                         std::unique_ptr<MmcInterface> codec_server) {
  socklen_t addr_size = sizeof(struct sockaddr_un);
  int client_fd = accept(fd, (struct sockaddr*)&addr, &addr_size);
  // |fd| is only used for accept.
  close(fd);

  if (client_fd < 0) {
    LOG(ERROR) << "Failed to accept: " << strerror(errno);
    codec_server.release();
    task_ended.set_value();
    return;
  }

  std::array<uint8_t, kMaximumBufferSize> i_buf = {};
  std::array<uint8_t, kMaximumBufferSize> o_buf = {};

  struct pollfd pfd;
  pfd.fd = client_fd;
  pfd.events = POLLIN;

  while (1) {
    // Blocking poll.
    int poll_ret = poll(&pfd, 1, -1);
    if (poll_ret <= 0) {
      LOG(ERROR) << "Poll failed: " << strerror(errno);
      break;
    }

    // Ignore remaining data in the closed socket.
    if (pfd.revents & (POLLHUP | POLLNVAL)) {
      LOG(INFO) << "Socket disconnected";
      break;
    }

    int i_data_len =
        recv(client_fd, i_buf.data(), kMaximumBufferSize, MSG_NOSIGNAL);
    if (i_data_len <= 0) {
      LOG(ERROR) << "Failed to recv data: " << strerror(errno);
      break;
    }

    // Start transcode.
    int o_data_len = codec_server->transcode(i_buf.data(), i_data_len,
                                             o_buf.data(), kMaximumBufferSize);
    if (o_data_len < 0) {
      LOG(ERROR) << "Failed to transcode: " << strerror(-o_data_len);
      break;
    }

    int sent_rc = send(client_fd, o_buf.data(), o_data_len, MSG_NOSIGNAL);
    if (sent_rc <= 0) {
      LOG(ERROR) << "Failed to send data: " << strerror(errno);
      break;
    }
    o_buf.fill(0);
  }
  close(client_fd);
  unlink(addr.sun_path);
  codec_server.release();
  task_ended.set_value();
  return;
}

}  // namespace

Service::Service(base::OnceClosure shutdown_callback)
    : shutdown_callback_(std::move(shutdown_callback)),
      weak_ptr_factory_(this) {}

bool Service::Init() {
  // Set up the dbus service.
  dbus::Bus::Options opts;
  opts.bus_type = dbus::Bus::SYSTEM;
  bus_ = new dbus::Bus(std::move(opts));

  if (!bus_->Connect()) {
    LOG(ERROR) << "Failed to connect to system bus";
    return false;
  }

  exported_object_ = bus_->GetExportedObject(dbus::ObjectPath(kMmcServicePath));
  if (!exported_object_) {
    LOG(ERROR) << "Failed to export " << kMmcServicePath << " object";
    return false;
  }

  using ServiceMethod = void (Service::*)(dbus::MethodCall*,
                                          dbus::ExportedObject::ResponseSender);
  const std::map<const char*, ServiceMethod> kServiceMethods = {
      {kCodecInitMethod, &Service::CodecInit},
      {kCodecCleanUpMethod, &Service::CodecCleanUp},
  };

  for (const auto& iter : kServiceMethods) {
    bool ret = exported_object_->ExportMethodAndBlock(
        kMmcServiceInterface, iter.first,
        base::BindRepeating(iter.second, weak_ptr_factory_.GetWeakPtr()));
    if (!ret) {
      LOG(ERROR) << "Failed to export method: " << iter.first;
      return false;
    }
  }

  if (!bus_->RequestOwnershipAndBlock(kMmcServiceName,
                                      dbus::Bus::REQUIRE_PRIMARY)) {
    LOG(ERROR) << "Failed to take ownership of " << kMmcServiceName;
    return false;
  }
  return true;
}

void Service::CodecInit(dbus::MethodCall* method_call,
                        dbus::ExportedObject::ResponseSender sender) {
  dbus::MessageReader reader(method_call);
  auto dbus_response = dbus::Response::FromMethodCall(method_call);

  dbus::MessageWriter writer(dbus_response.get());

  CodecInitRequest request;
  CodecInitResponse response;

  if (!reader.PopArrayOfBytesAsProto(&request)) {
    std::move(sender).Run(dbus::ErrorResponse::FromMethodCall(
        method_call, kMmcServiceError,
        "Unable to parse CodecInitRequest from message"));
    return;
  }

  if (!request.has_config()) {
    std::move(sender).Run(dbus::ErrorResponse::FromMethodCall(
        method_call, kMmcServiceError, "'Config Param' must be set"));
    return;
  }

  // Create codec server instance.
  std::unique_ptr<MmcInterface> codec_server;
  if (request.config().has_hfp_lc3_decoder_param()) {
    codec_server = std::make_unique<HfpLc3Decoder>();
  } else if (request.config().has_hfp_lc3_encoder_param()) {
    codec_server = std::make_unique<HfpLc3Encoder>();
  }
#if !defined(EXCLUDE_NONSTANDARD_CODECS)
  else if (request.config().has_a2dp_aac_encoder_param()) {
    codec_server = std::make_unique<A2dpAacEncoder>();
  }
#endif
  else {
    std::move(sender).Run(dbus::ErrorResponse::FromMethodCall(
        method_call, kMmcServiceError, "Codec type must be specified"));
    return;
  }

  int frame_size = codec_server->init(request.config());
  if (frame_size < 0) {
    std::move(sender).Run(dbus::ErrorResponse::FromMethodCall(
        method_call, kMmcServiceError,
        "Init codec server failed: " + std::string(strerror(-frame_size))));
    return;
  }
  response.set_input_frame_size(frame_size);

  // Generate socket name for client.
  std::string socket_path =
      std::string(kMmcSocketName) + base::UnguessableToken::Create().ToString();
  response.set_socket_token(socket_path);

  int skt_fd = socket(AF_UNIX, SOCK_SEQPACKET, 0);
  if (skt_fd < 0) {
    std::move(sender).Run(dbus::ErrorResponse::FromMethodCall(
        method_call, kMmcServiceError,
        "Create socket failed: " + std::string(strerror(errno))));
    return;
  }

  struct sockaddr_un addr = {};
  addr.sun_family = AF_UNIX;
  strncpy(addr.sun_path, response.socket_token().c_str(),
          sizeof(addr.sun_path) - 1);
  unlink(addr.sun_path);

  if (bind(skt_fd, (struct sockaddr*)&addr, sizeof(struct sockaddr_un)) == -1) {
    std::move(sender).Run(dbus::ErrorResponse::FromMethodCall(
        method_call, kMmcServiceError,
        "Bind socket failed: " + std::string(strerror(errno))));
    return;
  }

  // mmc_service group can read/write the socket.
  int rc = chmod(addr.sun_path, 0770);
  if (rc < 0) {
    std::move(sender).Run(dbus::ErrorResponse::FromMethodCall(
        method_call, kMmcServiceError,
        "Chmod socket failed: " + std::string(strerror(errno))));
    return;
  }

  if (listen(skt_fd, kClientMaximum) == -1) {
    std::move(sender).Run(dbus::ErrorResponse::FromMethodCall(
        method_call, kMmcServiceError,
        "Listen socket failed: " + std::string(strerror(errno))));
    return;
  }

  // Create a thread and pass codec server and socket fd to it.
  if (!StartWorkerThread(skt_fd, std::move(addr), std::move(codec_server))) {
    std::move(sender).Run(dbus::ErrorResponse::FromMethodCall(
        method_call, kMmcServiceError, "No free thread available"));
    return;
  }

  writer.AppendProtoAsArrayOfBytes(response);
  std::move(sender).Run(std::move(dbus_response));
  return;
}

void Service::CodecCleanUp(dbus::MethodCall* method_call,
                           dbus::ExportedObject::ResponseSender sender) {
  auto dbus_response = dbus::Response::FromMethodCall(method_call);
  RemoveIdleThread();
  std::move(sender).Run(std::move(dbus_response));
  return;
}

bool Service::StartWorkerThread(int fd, struct sockaddr_un addr,
                                std::unique_ptr<MmcInterface> codec_server) {
  // Each thread has its associated future to indicate task completion.
  std::promise<void> task_ended;
  thread_pool_.push_back(std::make_pair(
      std::make_unique<bluetooth::common::MessageLoopThread>(kWorkerThreadName),
      std::make_unique<std::future<void>>(task_ended.get_future())));

  // Start up thread and assign task to it.
  thread_pool_.back().first->StartUp();
  if (!thread_pool_.back().first->IsRunning()) {
    LOG(ERROR) << "Failed to start thread";
    return false;
  }

  // Real-time scheduling increases thread priority.
  // Without it, the thread still works.
  if (!thread_pool_.back().first->EnableRealTimeScheduling()) {
    LOG(WARNING) << "Failed to enable real time scheduling";
  }

  if (!thread_pool_.back().first->DoInThread(
          FROM_HERE,
          base::BindOnce(&StartSocketListener, fd, std::move(addr),
                         std::move(task_ended), std::move(codec_server)))) {
    LOG(ERROR) << "Failed to run task";
    return false;
  }

  return true;
}

void Service::RemoveIdleThread() {
  for (auto thread = thread_pool_.begin(); thread != thread_pool_.end();) {
    if (thread->second->wait_for(std::chrono::milliseconds(
            kThreadCheckTimeout)) == std::future_status::ready) {
      // The task is over, close the thread and remove it from the thread pool.
      thread->first->ShutDown();
      thread = thread_pool_.erase(thread);
    } else {
      thread++;
    }
  }
}

}  // namespace mmc
