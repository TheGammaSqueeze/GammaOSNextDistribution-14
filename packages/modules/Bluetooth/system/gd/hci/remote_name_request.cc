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

#include "remote_name_request.h"

#include <android_bluetooth_flags.h>

#include "hci/acl_manager/acl_scheduler.h"
#include "hci/event_checkers.h"
#include "hci/hci_layer.h"
#include "hci/hci_packets.h"

namespace bluetooth {
namespace hci {

struct RemoteNameRequestModule::impl {
 public:
  impl(const RemoteNameRequestModule& module) : module_(module) {}

  void Start() {
    LOG_INFO("Starting RemoteNameRequestModule");
    hci_layer_ = module_.GetDependency<HciLayer>();
    acl_scheduler_ = module_.GetDependency<acl_manager::AclScheduler>();
    handler_ = module_.GetHandler();

    hci_layer_->RegisterEventHandler(
        EventCode::REMOTE_HOST_SUPPORTED_FEATURES_NOTIFICATION,
        handler_->BindOn(
            this, &RemoteNameRequestModule::impl::on_remote_host_supported_features_notification));
    hci_layer_->RegisterEventHandler(
        EventCode::REMOTE_NAME_REQUEST_COMPLETE,
        handler_->BindOn(this, &RemoteNameRequestModule::impl::on_remote_name_request_complete));
  }

  void Stop() {
    LOG_INFO("Stopping RemoteNameRequestModule");
    hci_layer_->UnregisterEventHandler(EventCode::REMOTE_HOST_SUPPORTED_FEATURES_NOTIFICATION);
    hci_layer_->UnregisterEventHandler(EventCode::REMOTE_NAME_REQUEST_COMPLETE);
  }

  void StartRemoteNameRequest(
      Address address,
      std::unique_ptr<RemoteNameRequestBuilder> request,
      CompletionCallback on_completion,
      RemoteHostSupportedFeaturesCallback on_remote_host_supported_features_notification,
      RemoteNameCallback on_remote_name_complete) {
    LOG_INFO("Enqueuing remote name request to %s", address.ToRedactedStringForLogging().c_str());

    // This callback needs to be shared between the *start* callback and the *cancel_completed*
    // callback, so we refcount it for safety. But since the scheduler guarantees that exactly one
    // of these callbacks will be invokes, this is safe.
    auto on_remote_name_complete_ptr =
        std::make_shared<RemoteNameCallback>(std::move(on_remote_name_complete));

    acl_scheduler_->EnqueueRemoteNameRequest(
        address,
        handler_->BindOnceOn(
            this,
            &impl::actually_start_remote_name_request,
            address,
            std::move(request),
            std::move(on_completion),
            std::move(on_remote_host_supported_features_notification),
            on_remote_name_complete_ptr),
        handler_->BindOnce(
            [&](Address address, std::shared_ptr<RemoteNameCallback> on_remote_name_complete_ptr) {
              LOG_INFO(
                  "Dequeued remote name request to %s since it was cancelled",
                  address.ToRedactedStringForLogging().c_str());
              on_remote_name_complete_ptr->Invoke(ErrorCode::PAGE_TIMEOUT, {});
            },
            address,
            on_remote_name_complete_ptr));
  }

  void CancelRemoteNameRequest(Address address) {
    LOG_INFO(
        "Enqueuing cancel of remote name request to %s",
        address.ToRedactedStringForLogging().c_str());
    acl_scheduler_->CancelRemoteNameRequest(
        address, handler_->BindOnceOn(this, &impl::actually_cancel_remote_name_request, address));
  }

  void ReportRemoteNameRequestCancellation(Address address) {
    if (pending_) {
      LOG_INFO(
          "Received CONNECTION_COMPLETE (corresponding INCORRECTLY to an RNR cancellation) from %s",
          address.ToRedactedStringForLogging().c_str());
      pending_ = false;
      on_remote_name_complete_.Invoke(ErrorCode::UNKNOWN_CONNECTION, {});
      acl_scheduler_->ReportRemoteNameRequestCompletion(address);
    } else {
      LOG_ERROR(
          "Received unexpected CONNECTION_COMPLETE when no Remote Name Request OR ACL connection "
          "is outstanding");
    }
  }

 private:
  void actually_start_remote_name_request(
      Address address,
      std::unique_ptr<RemoteNameRequestBuilder> request,
      CompletionCallback on_completion,
      RemoteHostSupportedFeaturesCallback on_remote_host_supported_features_notification,
      std::shared_ptr<RemoteNameCallback> on_remote_name_complete_ptr) {
    LOG_INFO("Starting remote name request to %s", address.ToRedactedStringForLogging().c_str());
    ASSERT(pending_ == false);
    pending_ = true;
    on_remote_host_supported_features_notification_ =
        std::move(on_remote_host_supported_features_notification);
    on_remote_name_complete_ = std::move(*on_remote_name_complete_ptr.get());
    hci_layer_->EnqueueCommand(
        std::move(request),
        handler_->BindOnceOn(
            this, &impl::on_start_remote_name_request_status, address, std::move(on_completion)));
  }

  void on_start_remote_name_request_status(
      Address address, CompletionCallback on_completion, CommandStatusView status) {
    // TODO(b/294961421): Remove the ifdef when firmware fix in place. Realtek controllers
    // unexpectedly sent a Remote Name Req Complete HCI event without the corresponding HCI command.
#ifndef TARGET_FLOSS
    ASSERT(pending_ == true);
#else
    if (pending_ != true) {
      LOG_WARN("Unexpected remote name response with no request pending");
      return;
    }
#endif
    ASSERT(status.GetCommandOpCode() == OpCode::REMOTE_NAME_REQUEST);
    LOG_INFO(
        "Started remote name request peer:%s status:%s",
        address.ToString().c_str(),
        ErrorCodeText(status.GetStatus()).c_str());
    on_completion.Invoke(status.GetStatus());
    if (status.GetStatus() != ErrorCode::SUCCESS /* pending */) {
      pending_ = false;
      acl_scheduler_->ReportRemoteNameRequestCompletion(address);
    }
  }

  void actually_cancel_remote_name_request(Address address) {
    if (IS_FLAG_ENABLED(rnr_cancel_before_event_race)) {
      if (pending_) {
        LOG_INFO(
            "Cancelling remote name request to %s", address.ToRedactedStringForLogging().c_str());
        hci_layer_->EnqueueCommand(
            RemoteNameRequestCancelBuilder::Create(address),
            handler_->BindOnce(check_complete<RemoteNameRequestCancelCompleteView>));
      } else {
        LOG_INFO(
            "Ignoring cancel RNR as RNR event already received to %s",
            address.ToRedactedStringForLogging().c_str());
      }
    } else {
      ASSERT(pending_ == true);
      LOG_INFO(
          "Cancelling remote name request to %s", address.ToRedactedStringForLogging().c_str());
      hci_layer_->EnqueueCommand(
          RemoteNameRequestCancelBuilder::Create(address),
          handler_->BindOnce(check_complete<RemoteNameRequestCancelCompleteView>));
    }
  }

  void on_remote_host_supported_features_notification(EventView view) {
    auto packet = RemoteHostSupportedFeaturesNotificationView::Create(view);
    ASSERT(packet.IsValid());
    if (pending_ && !on_remote_host_supported_features_notification_.IsEmpty()) {
      LOG_INFO(
          "Received REMOTE_HOST_SUPPORTED_FEATURES_NOTIFICATION from %s",
          packet.GetBdAddr().ToRedactedStringForLogging().c_str());
      on_remote_host_supported_features_notification_.Invoke(packet.GetHostSupportedFeatures());
      // Remove the callback so that we won't call it again.
      on_remote_host_supported_features_notification_ = RemoteHostSupportedFeaturesCallback();
    } else if (!pending_) {
      LOG_ERROR(
          "Received unexpected REMOTE_HOST_SUPPORTED_FEATURES_NOTIFICATION when no Remote Name "
          "Request is outstanding");
    } else {  // callback is not set, which indicates we have processed the feature notification.
      LOG_ERROR(
          "Received more than one REMOTE_HOST_SUPPORTED_FEATURES_NOTIFICATION during Remote Name "
          "Request");
    }
  }

  void on_remote_name_request_complete(EventView view) {
    auto packet = RemoteNameRequestCompleteView::Create(view);
    ASSERT(packet.IsValid());
    if (pending_) {
      LOG_INFO(
          "Received REMOTE_NAME_REQUEST_COMPLETE from %s",
          packet.GetBdAddr().ToRedactedStringForLogging().c_str());
      pending_ = false;
      on_remote_name_complete_.Invoke(packet.GetStatus(), packet.GetRemoteName());
      acl_scheduler_->ReportRemoteNameRequestCompletion(packet.GetBdAddr());
    } else {
      LOG_ERROR(
          "Received unexpected REMOTE_NAME_REQUEST_COMPLETE when no Remote Name Request is "
          "outstanding");
    }
  }

  const RemoteNameRequestModule& module_;
  HciLayer* hci_layer_;
  acl_manager::AclScheduler* acl_scheduler_;
  os::Handler* handler_;

  bool pending_ = false;
  RemoteHostSupportedFeaturesCallback on_remote_host_supported_features_notification_;
  RemoteNameCallback on_remote_name_complete_;
};

const ModuleFactory RemoteNameRequestModule::Factory =
    ModuleFactory([]() { return new RemoteNameRequestModule(); });

RemoteNameRequestModule::RemoteNameRequestModule() : pimpl_(std::make_unique<impl>(*this)){};
RemoteNameRequestModule::~RemoteNameRequestModule() = default;

void RemoteNameRequestModule::StartRemoteNameRequest(
    Address address,
    std::unique_ptr<RemoteNameRequestBuilder> request,
    CompletionCallback on_completion,
    RemoteHostSupportedFeaturesCallback on_remote_host_supported_features_notification,
    RemoteNameCallback on_remote_name_complete) {
  CallOn(
      pimpl_.get(),
      &impl::StartRemoteNameRequest,
      address,
      std::move(request),
      std::move(on_completion),
      std::move(on_remote_host_supported_features_notification),
      std::move(on_remote_name_complete));
}

void RemoteNameRequestModule::CancelRemoteNameRequest(Address address) {
  CallOn(pimpl_.get(), &impl::CancelRemoteNameRequest, address);
}

void RemoteNameRequestModule::ReportRemoteNameRequestCancellation(Address address) {
  CallOn(pimpl_.get(), &impl::ReportRemoteNameRequestCancellation, address);
}

void RemoteNameRequestModule::ListDependencies(ModuleList* list) const {
  list->add<HciLayer>();
  list->add<acl_manager::AclScheduler>();
}

void RemoteNameRequestModule::Start() {
  pimpl_->Start();
}

void RemoteNameRequestModule::Stop() {
  pimpl_->Stop();
}

}  // namespace hci
}  // namespace bluetooth
