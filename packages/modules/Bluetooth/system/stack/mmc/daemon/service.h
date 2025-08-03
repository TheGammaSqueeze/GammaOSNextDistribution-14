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

#ifndef MMC_DAEMON_SERVICE_H_
#define MMC_DAEMON_SERVICE_H_

#include <base/functional/callback.h>
#include <base/memory/ref_counted.h>
#include <base/memory/weak_ptr.h>
#include <dbus/bus.h>
#include <dbus/exported_object.h>
#include <dbus/message.h>
#include <sys/un.h>

#include <future>
#include <map>
#include <memory>

#include "common/message_loop_thread.h"
#include "mmc/mmc_interface/mmc_interface.h"

namespace mmc {

class Service final {
 public:
  explicit Service(base::OnceClosure shutdown_callback);

  // Service is neither copyable nor movable.
  Service(const Service&) = delete;
  Service& operator=(const Service&) = delete;

  // Connects to DBus and exports methods for client to call.
  bool Init();

 private:
  /* DBus Methods */
  // Main thread creates a codec server instance and a socket,
  // and calls |StartWorkerThread| to let one thread start listening on the
  // socket.
  //
  // Expected input message:
  //   |CodecInitRequest| with |ConfigParam| set.
  // Response:
  //   |CodecInitResponse|, if |CodecInit| succeeded.
  //   ErrorResponse, otherwise.
  void CodecInit(dbus::MethodCall* method_call,
                 dbus::ExportedObject::ResponseSender sender);

  // Main thread removes idle threads from the thread poll.
  //
  // No input message needed.
  // Response:
  //   dbus::Response, implying |CodecCleanUp| finished.
  void CodecCleanUp(dbus::MethodCall* method_call,
                    dbus::ExportedObject::ResponseSender sender);

  /* Thread Management*/
  // Adds a thread to the thread pool and makes it listen on the socket fd.
  bool StartWorkerThread(int fd, struct sockaddr_un addr,
                         std::unique_ptr<MmcInterface> codec_server);

  // Removes idle threads from the thread pool.
  void RemoveIdleThread();

  base::OnceClosure shutdown_callback_;

  scoped_refptr<dbus::Bus> bus_;
  dbus::ExportedObject* exported_object_;  // Owned by the Bus object.

  std::vector<std::pair<std::unique_ptr<bluetooth::common::MessageLoopThread>,
                        std::unique_ptr<std::future<void>>>>
      thread_pool_;

  base::WeakPtrFactory<Service> weak_ptr_factory_;
};

}  // namespace mmc

#endif  // MMC_DAEMON_SERVICE_H_
