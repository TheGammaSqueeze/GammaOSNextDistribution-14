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

#include <memory>
#include <string>

#include "common/contextual_callback.h"
#include "hci/address.h"
#include "module.h"

namespace bluetooth {
namespace hci {
namespace acl_manager {

// The AclScheduler is responsible for *scheduling* ACL connection-related operations (outgoing connections,
// incoming connections, and remote name requests). It maintains a queue of operations initiated by us, and tracks
// all incoming connections. We should never initiate a connection operation directly - instead, it should always
// pass through this class, so that we can be sure that it does not conflict with other operations.
//
// However, it does not perform any actual HCI operations itself - it simply takes in callbacks, and executes them
// at the appropriate time.
class AclScheduler : public bluetooth::Module {
 public:
  // Schedule an ACL Create Connection request
  void EnqueueOutgoingAclConnection(Address address, common::ContextualOnceCallback<void()> start_connection);

  // Inform the scheduler that we are handling an incoming connection. This will block all future outgoing ACL
  // connection events until the incoming connection is deregistered.
  void RegisterPendingIncomingConnection(Address address);

  // Report that an ACL connection has completed, and dispatch to the appropriate callback based on the internal
  // state. Then, start the next operation.
  virtual void ReportAclConnectionCompletion(
      Address address,
      common::ContextualOnceCallback<void()> handle_outgoing_connection,
      common::ContextualOnceCallback<void()> handle_incoming_connection,
      common::ContextualOnceCallback<void(std::string)> handle_unknown_connection);

  // Same as above, but for the outgoing ACL connection in particular (and no callbacks)
  void ReportOutgoingAclConnectionFailure();

  // Cancel an ACL connection. If the request is already outgoing, we will invoke cancel_connection, without clearing
  // the outgoing request. Otherwise, we will remove the request from the queue, invoke cancel_connection_completed,
  // and execute the next request in the queue.
  void CancelAclConnection(
      Address address,
      common::ContextualOnceCallback<void()> cancel_connection,
      common::ContextualOnceCallback<void()> cancel_connection_completed);

  // Schedule a Remote Name Request. When the request is started, start_request will be invoked. If the request is
  // cancelled before it is dequeued, cancel_request_completed will be invoked.
  void EnqueueRemoteNameRequest(
      Address address,
      common::ContextualOnceCallback<void()> start_request,
      common::ContextualOnceCallback<void()> cancel_request_completed);

  // Report that a Remote Name Request connection has completed, so we can resume popping from the queue.
  void ReportRemoteNameRequestCompletion(Address address);

  // Cancel an Remote Name Request. If the request is already outgoing, we will invoke cancel_request, without
  // clearing the outgoing request. Otherwise, we will invoke the cancel_request_completed callback registered on
  // the initial enqueue.
  void CancelRemoteNameRequest(Address address, common::ContextualOnceCallback<void()> cancel_request);

 private:
  struct impl;
  std::unique_ptr<impl> pimpl_;

 protected:
  void ListDependencies(ModuleList* list) const override;
  void Start() override;
  void Stop() override;
  std::string ToString() const override {
    return std::string("AclSchedulerModule");
  }

 public:
  static const ModuleFactory Factory;
  AclScheduler();
  virtual ~AclScheduler();
};

}  // namespace acl_manager
}  // namespace hci
}  // namespace bluetooth
