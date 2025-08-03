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

#include "acl_scheduler.h"

#include <optional>
#include <unordered_set>
#include <variant>

namespace bluetooth {
namespace hci {

namespace acl_manager {

struct AclCreateConnectionQueueEntry {
  Address address;
  common::ContextualOnceCallback<void()> callback;
};

struct RemoteNameRequestQueueEntry {
  Address address;
  common::ContextualOnceCallback<void()> callback;
  common::ContextualOnceCallback<void()> callback_when_cancelled;
};

using QueueEntry = std::variant<AclCreateConnectionQueueEntry, RemoteNameRequestQueueEntry>;

struct AclScheduler::impl {
  void EnqueueOutgoingAclConnection(Address address, common::ContextualOnceCallback<void()> start_connection) {
    pending_outgoing_operations_.push_back(AclCreateConnectionQueueEntry{address, std::move(start_connection)});
    try_dequeue_next_operation();
  }

  void RegisterPendingIncomingConnection(Address address) {
    incoming_connecting_address_set_.insert(address);
  }

  void ReportAclConnectionCompletion(
      Address address,
      common::ContextualOnceCallback<void()> handle_outgoing_connection,
      common::ContextualOnceCallback<void()> handle_incoming_connection,
      common::ContextualOnceCallback<void(std::string)> handle_unknown_connection) {
    // Check if an outgoing request (a) exists, (b) is a Create Connection, (c) matches the received address
    if (outgoing_entry_.has_value()) {
      auto entry = std::get_if<AclCreateConnectionQueueEntry>(&outgoing_entry_.value());
      if (entry != nullptr && entry->address == address) {
        // If so, clear the current entry and advance the queue
        outgoing_entry_.reset();
        handle_outgoing_connection.InvokeIfNotEmpty();
        try_dequeue_next_operation();
        return;
      }
    }

    // Otherwise check if it's an incoming request and advance the queue if so
    if (incoming_connecting_address_set_.find(address) != incoming_connecting_address_set_.end()) {
      incoming_connecting_address_set_.erase(address);
      handle_incoming_connection.InvokeIfNotEmpty();
    } else {
      handle_unknown_connection.InvokeIfNotEmpty(set_of_incoming_connecting_addresses());
    }
    try_dequeue_next_operation();
  }

  void ReportOutgoingAclConnectionFailure() {
    if (!outgoing_entry_.has_value()) {
      LOG_ERROR("Outgoing connection failure reported, but none present!");
      return;
    }
    auto entry = std::get_if<AclCreateConnectionQueueEntry>(&outgoing_entry_.value());
    if (entry == nullptr) {
      LOG_ERROR("Outgoing connection failure reported, but we're currently doing an RNR!");
      return;
    }
    outgoing_entry_.reset();
    try_dequeue_next_operation();
  }

  void CancelAclConnection(
      Address address,
      common::ContextualOnceCallback<void()> cancel_connection,
      common::ContextualOnceCallback<void()> cancel_connection_completed) {
    auto ok = cancel_outgoing_or_queued_connection(
        [&](auto& entry) {
          auto entry_ptr = std::get_if<AclCreateConnectionQueueEntry>(&entry);
          return entry_ptr != nullptr && entry_ptr->address == address;
        },
        [&]() { cancel_connection.Invoke(); },
        [&](auto /* entry */) { cancel_connection_completed.Invoke(); });
    if (!ok) {
      LOG_ERROR("Attempted to cancel connection to %s that does not exist",
                ADDRESS_TO_LOGGABLE_CSTR(address));
    }
  }

  void EnqueueRemoteNameRequest(
      Address address,
      common::ContextualOnceCallback<void()> start_request,
      common::ContextualOnceCallback<void()> cancel_request_completed) {
    pending_outgoing_operations_.push_back(
        RemoteNameRequestQueueEntry{address, std::move(start_request), std::move(cancel_request_completed)});
    try_dequeue_next_operation();
  }

  void ReportRemoteNameRequestCompletion(Address /* address */) {
    if (!outgoing_entry_.has_value()) {
      LOG_ERROR("Remote name request completion reported, but none taking place!");
      return;
    }

    std::visit(
        [](auto&& entry) {
          using T = std::decay_t<decltype(entry)>;
          if constexpr (std::is_same_v<T, RemoteNameRequestQueueEntry>) {
            LOG_INFO("Remote name request completed");
          } else if constexpr (std::is_same_v<T, AclCreateConnectionQueueEntry>) {
            LOG_ERROR(
                "Received RNR completion when ACL connection is outstanding - assuming the connection has failed and "
                "continuing");
          } else {
            static_assert(!sizeof(T*), "non-exhaustive visitor!");
          }
        },
        outgoing_entry_.value());

    outgoing_entry_.reset();
    try_dequeue_next_operation();
  }

  void CancelRemoteNameRequest(Address address, common::ContextualOnceCallback<void()> cancel_request) {
    auto ok = cancel_outgoing_or_queued_connection(
        [&](auto& entry) {
          auto entry_ptr = std::get_if<RemoteNameRequestQueueEntry>(&entry);
          return entry_ptr != nullptr && entry_ptr->address == address;
        },
        [&]() { cancel_request.Invoke(); },
        [](auto entry) { std::get<RemoteNameRequestQueueEntry>(entry).callback_when_cancelled.Invoke(); });
    if (!ok) {
      LOG_ERROR("Attempted to cancel remote name request "
                "to %s that does not exist", ADDRESS_TO_LOGGABLE_CSTR(address));
    }
  };

  void Stop() {
    stopped_ = true;
  }

 private:
  void try_dequeue_next_operation() {
    if (stopped_) {
      return;
    }
    if (incoming_connecting_address_set_.empty() && !outgoing_entry_.has_value() &&
        !pending_outgoing_operations_.empty()) {
      LOG_INFO("Pending connections is not empty; so sending next connection");
      auto entry = std::move(pending_outgoing_operations_.front());
      pending_outgoing_operations_.pop_front();
      std::visit([](auto&& variant) { variant.callback.Invoke(); }, entry);
      outgoing_entry_ = std::move(entry);
    }
  }

  template <typename T, typename U, typename V>
  bool cancel_outgoing_or_queued_connection(T matcher, U cancel_outgoing, V cancelled_queued) {
    // Check if relevant connection is currently outgoing
    if (outgoing_entry_.has_value()) {
      if (matcher(outgoing_entry_.value())) {
        cancel_outgoing();
        return true;
      }
    }
    // Otherwise, clear from the queue
    auto it = std::find_if(pending_outgoing_operations_.begin(), pending_outgoing_operations_.end(), matcher);
    if (it == pending_outgoing_operations_.end()) {
      return false;
    }
    cancelled_queued(std::move(*it));
    pending_outgoing_operations_.erase(it);
    return true;
  }

  const std::string set_of_incoming_connecting_addresses() const {
    std::stringstream buffer;
    for (const auto& c : incoming_connecting_address_set_) buffer << " " << c;
    return buffer.str();
  }

  std::optional<QueueEntry> outgoing_entry_;
  std::deque<QueueEntry> pending_outgoing_operations_;
  std::unordered_set<Address> incoming_connecting_address_set_;
  bool stopped_ = false;
};

const ModuleFactory AclScheduler::Factory = ModuleFactory([]() { return new AclScheduler(); });

AclScheduler::AclScheduler() : pimpl_(std::make_unique<impl>()){};
AclScheduler::~AclScheduler() = default;

void AclScheduler::EnqueueOutgoingAclConnection(
    Address address, common::ContextualOnceCallback<void()> start_connection) {
  GetHandler()->Call(
      &impl::EnqueueOutgoingAclConnection, common::Unretained(pimpl_.get()), address, std::move(start_connection));
}

void AclScheduler::RegisterPendingIncomingConnection(Address address) {
  GetHandler()->Call(&impl::RegisterPendingIncomingConnection, common::Unretained(pimpl_.get()), address);
}

void AclScheduler::ReportAclConnectionCompletion(
    Address address,
    common::ContextualOnceCallback<void()> handle_outgoing_connection,
    common::ContextualOnceCallback<void()> handle_incoming_connection,
    common::ContextualOnceCallback<void(std::string)> handle_unknown_connection) {
  GetHandler()->Call(
      &impl::ReportAclConnectionCompletion,
      common::Unretained(pimpl_.get()),
      address,
      std::move(handle_outgoing_connection),
      std::move(handle_incoming_connection),
      std::move(handle_unknown_connection));
}

void AclScheduler::ReportOutgoingAclConnectionFailure() {
  GetHandler()->Call(&impl::ReportOutgoingAclConnectionFailure, common::Unretained(pimpl_.get()));
}

void AclScheduler::CancelAclConnection(
    Address address,
    common::ContextualOnceCallback<void()> cancel_connection,
    common::ContextualOnceCallback<void()> cancel_connection_completed) {
  GetHandler()->Call(
      &impl::CancelAclConnection,
      common::Unretained(pimpl_.get()),
      address,
      std::move(cancel_connection),
      std::move(cancel_connection_completed));
}

void AclScheduler::EnqueueRemoteNameRequest(
    Address address,
    common::ContextualOnceCallback<void()> start_request,
    common::ContextualOnceCallback<void()> cancel_request_completed) {
  GetHandler()->Call(
      &impl::EnqueueRemoteNameRequest,
      common::Unretained(pimpl_.get()),
      address,
      std::move(start_request),
      std::move(cancel_request_completed));
}

void AclScheduler::ReportRemoteNameRequestCompletion(Address address) {
  GetHandler()->Call(&impl::ReportRemoteNameRequestCompletion, common::Unretained(pimpl_.get()), address);
}

void AclScheduler::CancelRemoteNameRequest(Address address, common::ContextualOnceCallback<void()> cancel_request) {
  GetHandler()->Call(
      &impl::CancelRemoteNameRequest, common::Unretained(pimpl_.get()), address, std::move(cancel_request));
}

void AclScheduler::ListDependencies(ModuleList* /* list */) const {}

void AclScheduler::Start() {}

void AclScheduler::Stop() {
  pimpl_->Stop();
}

}  // namespace acl_manager
}  // namespace hci
}  // namespace bluetooth
