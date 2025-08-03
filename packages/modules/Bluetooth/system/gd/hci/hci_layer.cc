/*
 * Copyright 2019 The Android Open Source Project
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

#include "hci/hci_layer.h"

#ifdef TARGET_FLOSS
#include <signal.h>
#endif

#include "common/bind.h"
#include "common/init_flags.h"
#include "common/stop_watch.h"
#include "hci/class_of_device.h"
#include "hci/hci_metrics_logging.h"
#include "os/alarm.h"
#include "os/metrics.h"
#include "os/queue.h"
#include "osi/include/stack_power_telemetry.h"
#include "packet/packet_builder.h"
#include "storage/storage_module.h"

namespace bluetooth {
namespace hci {
using bluetooth::common::BindOn;
using bluetooth::common::BindOnce;
using bluetooth::common::ContextualCallback;
using bluetooth::common::ContextualOnceCallback;
using bluetooth::hci::CommandBuilder;
using bluetooth::hci::CommandCompleteView;
using bluetooth::hci::CommandStatusView;
using bluetooth::hci::EventView;
using bluetooth::hci::LeMetaEventView;
using bluetooth::os::Handler;
using common::BidiQueue;
using common::BidiQueueEnd;
using hci::OpCode;
using hci::ResetCompleteView;
using os::Alarm;
using os::Handler;
using std::unique_ptr;

static void fail_if_reset_complete_not_success(CommandCompleteView complete) {
  auto reset_complete = ResetCompleteView::Create(complete);
  ASSERT(reset_complete.IsValid());
  LOG_DEBUG("Reset completed with status: %s", ErrorCodeText(ErrorCode::SUCCESS).c_str());
  ASSERT(reset_complete.GetStatus() == ErrorCode::SUCCESS);
}

static void abort_after_time_out(OpCode op_code) {
  bluetooth::os::LogMetricHciTimeoutEvent(static_cast<uint32_t>(op_code));
  ASSERT_LOG(false, "Done waiting for debug information after HCI timeout (%s)", OpCodeText(op_code).c_str());
}

class CommandQueueEntry {
 public:
  CommandQueueEntry(
      unique_ptr<CommandBuilder> command_packet,
      ContextualOnceCallback<void(CommandCompleteView)> on_complete_function)
      : command(std::move(command_packet)),
        waiting_for_status_(false),
        on_complete(std::move(on_complete_function)) {}

  CommandQueueEntry(
      unique_ptr<CommandBuilder> command_packet,
      ContextualOnceCallback<void(CommandStatusView)> on_status_function)
      : command(std::move(command_packet)),
        waiting_for_status_(true),
        on_status(std::move(on_status_function)) {}

  unique_ptr<CommandBuilder> command;
  unique_ptr<CommandView> command_view;

  bool waiting_for_status_;
  ContextualOnceCallback<void(CommandStatusView)> on_status;
  ContextualOnceCallback<void(CommandCompleteView)> on_complete;

  template <typename TView>
  ContextualOnceCallback<void(TView)>* GetCallback() {
    return nullptr;
  }

  template <>
  ContextualOnceCallback<void(CommandStatusView)>* GetCallback<CommandStatusView>() {
    return &on_status;
  }

  template <>
  ContextualOnceCallback<void(CommandCompleteView)>* GetCallback<CommandCompleteView>() {
    return &on_complete;
  }
};

struct HciLayer::impl {
  impl(hal::HciHal* hal, HciLayer& module) : hal_(hal), module_(module) {
    hci_timeout_alarm_ = new Alarm(module.GetHandler());
  }

  ~impl() {
    incoming_acl_buffer_.Clear();
    incoming_sco_buffer_.Clear();
    incoming_iso_buffer_.Clear();
    if (hci_timeout_alarm_ != nullptr) {
      delete hci_timeout_alarm_;
    }
    if (hci_abort_alarm_ != nullptr) {
      delete hci_abort_alarm_;
    }
    command_queue_.clear();
  }

  void drop(EventView event) {
    LOG_INFO("Dropping event %s", EventCodeText(event.GetEventCode()).c_str());
  }

  void on_outbound_acl_ready() {
    auto packet = acl_queue_.GetDownEnd()->TryDequeue();
    std::vector<uint8_t> bytes;
    BitInserter bi(bytes);
    packet->Serialize(bi);
    hal_->sendAclData(bytes);
  }

  void on_outbound_sco_ready() {
    auto packet = sco_queue_.GetDownEnd()->TryDequeue();
    std::vector<uint8_t> bytes;
    BitInserter bi(bytes);
    packet->Serialize(bi);
    hal_->sendScoData(bytes);
  }

  void on_outbound_iso_ready() {
    auto packet = iso_queue_.GetDownEnd()->TryDequeue();
    std::vector<uint8_t> bytes;
    BitInserter bi(bytes);
    packet->Serialize(bi);
    hal_->sendIsoData(bytes);
  }

  template <typename TResponse>
  void enqueue_command(unique_ptr<CommandBuilder> command, ContextualOnceCallback<void(TResponse)> on_response) {
    command_queue_.emplace_back(std::move(command), std::move(on_response));
    send_next_command();
  }

  void on_command_status(EventView event) {
    CommandStatusView response_view = CommandStatusView::Create(event);
    ASSERT(response_view.IsValid());
    OpCode op_code = response_view.GetCommandOpCode();
    ErrorCode status = response_view.GetStatus();
    if (status != ErrorCode::SUCCESS) {
      LOG_ERROR(
          "Received UNEXPECTED command status:%s opcode:0x%02hx (%s)",
          ErrorCodeText(status).c_str(),
          op_code,
          OpCodeText(op_code).c_str());
    }
    handle_command_response<CommandStatusView>(event, "status");
  }

  void on_command_complete(EventView event) {
    handle_command_response<CommandCompleteView>(event, "complete");
  }

  template <typename TResponse>
  void handle_command_response(EventView event, std::string logging_id) {
    TResponse response_view = TResponse::Create(event);
    ASSERT(response_view.IsValid());
    command_credits_ = response_view.GetNumHciCommandPackets();
    OpCode op_code = response_view.GetCommandOpCode();
    if (op_code == OpCode::NONE) {
      send_next_command();
      return;
    }
    bool is_status = logging_id == "status";

    ASSERT_LOG(
        !command_queue_.empty(),
        "Unexpected %s event with OpCode 0x%02hx (%s)",
        logging_id.c_str(),
        op_code,
        OpCodeText(op_code).c_str());
    if (waiting_command_ == OpCode::CONTROLLER_DEBUG_INFO && op_code != OpCode::CONTROLLER_DEBUG_INFO) {
      LOG_ERROR("Discarding event that came after timeout 0x%02hx (%s)", op_code, OpCodeText(op_code).c_str());
      common::StopWatch::DumpStopWatchLog();
      return;
    }
    ASSERT_LOG(
        waiting_command_ == op_code,
        "Waiting for 0x%02hx (%s), got 0x%02hx (%s)",
        waiting_command_,
        OpCodeText(waiting_command_).c_str(),
        op_code,
        OpCodeText(op_code).c_str());

    bool is_vendor_specific = static_cast<int>(op_code) & (0x3f << 10);
    CommandStatusView status_view = CommandStatusView::Create(event);
    if (is_vendor_specific && (is_status && !command_queue_.front().waiting_for_status_) &&
        (status_view.IsValid() && status_view.GetStatus() == ErrorCode::UNKNOWN_HCI_COMMAND)) {
      // If this is a command status of a vendor specific command, and command complete is expected,
      // we can't treat this as hard failure since we have no way of probing this lack of support at
      // earlier time. Instead we let the command complete handler handle a empty Command Complete
      // packet, which will be interpreted as invalid response.
      CommandCompleteView command_complete_view = CommandCompleteView::Create(
          EventView::Create(PacketView<kLittleEndian>(std::make_shared<std::vector<uint8_t>>(std::vector<uint8_t>()))));
      command_queue_.front().GetCallback<CommandCompleteView>()->Invoke(
          std::move(command_complete_view));
    } else {
      if (command_queue_.front().waiting_for_status_ == is_status) {
        command_queue_.front().GetCallback<TResponse>()->Invoke(std::move(response_view));
      } else {
        CommandCompleteView command_complete_view = CommandCompleteView::Create(
            EventView::Create(PacketView<kLittleEndian>(std::make_shared<std::vector<uint8_t>>(std::vector<uint8_t>()))));
        command_queue_.front().GetCallback<CommandCompleteView>()->Invoke(std::move(command_complete_view));
      }
    }

#ifdef TARGET_FLOSS
    // Although UNKNOWN_CONNECTION might be a controller issue in some command status, we treat it
    // as a disconnect event to maintain consistent connection state between stack and controller
    // since there might not be further HCI Disconnect Event after this status event.
    // Currently only do this on LE_READ_REMOTE_FEATURES because it is the only one we know that
    // would return UNKNOWN_CONNECTION in some cases.
    if (op_code == OpCode::LE_READ_REMOTE_FEATURES && is_status && status_view.IsValid() &&
        status_view.GetStatus() == ErrorCode::UNKNOWN_CONNECTION) {
      auto& command_view = *command_queue_.front().command_view;
      auto le_read_features_view = bluetooth::hci::LeReadRemoteFeaturesView::Create(
          LeConnectionManagementCommandView::Create(AclCommandView::Create(command_view)));
      if (le_read_features_view.IsValid()) {
        uint16_t handle = le_read_features_view.GetConnectionHandle();
        module_.Disconnect(handle, ErrorCode::UNKNOWN_CONNECTION);
      }
    }
#endif

    command_queue_.pop_front();
    waiting_command_ = OpCode::NONE;
    if (hci_timeout_alarm_ != nullptr) {
      hci_timeout_alarm_->Cancel();
      send_next_command();
    }
  }

  void on_hci_timeout(OpCode op_code) {
    common::StopWatch::DumpStopWatchLog();
    LOG_ERROR("Timed out waiting for 0x%02hx (%s)", op_code, OpCodeText(op_code).c_str());
    // TODO: LogMetricHciTimeoutEvent(static_cast<uint32_t>(op_code));

    LOG_ERROR("Flushing %zd waiting commands", command_queue_.size());
    // Clear any waiting commands (there is an abort coming anyway)
    command_queue_.clear();
    command_credits_ = 1;
    waiting_command_ = OpCode::NONE;
    // Ignore the response, since we don't know what might come back.
    enqueue_command(ControllerDebugInfoBuilder::Create(), module_.GetHandler()->BindOnce([](CommandCompleteView) {}));
    // Don't time out for this one;
    if (hci_timeout_alarm_ != nullptr) {
      hci_timeout_alarm_->Cancel();
      delete hci_timeout_alarm_;
      hci_timeout_alarm_ = nullptr;
    }
    if (hci_abort_alarm_ == nullptr) {
      hci_abort_alarm_ = new Alarm(module_.GetHandler());
      hci_abort_alarm_->Schedule(BindOnce(&abort_after_time_out, op_code), kHciTimeoutRestartMs);
    } else {
      LOG_WARN("Unable to schedul abort timer");
    }
  }

  void send_next_command() {
    if (command_credits_ == 0) {
      return;
    }
    if (waiting_command_ != OpCode::NONE) {
      return;
    }
    if (command_queue_.size() == 0) {
      return;
    }
    std::shared_ptr<std::vector<uint8_t>> bytes = std::make_shared<std::vector<uint8_t>>();
    BitInserter bi(*bytes);
    command_queue_.front().command->Serialize(bi);
    hal_->sendHciCommand(*bytes);

    auto cmd_view = CommandView::Create(PacketView<kLittleEndian>(bytes));
    ASSERT(cmd_view.IsValid());
    OpCode op_code = cmd_view.GetOpCode();
    power_telemetry::GetInstance().LogHciCmdDetail();
    command_queue_.front().command_view = std::make_unique<CommandView>(std::move(cmd_view));
    log_link_layer_connection_command(command_queue_.front().command_view);
    log_classic_pairing_command_status(command_queue_.front().command_view, ErrorCode::STATUS_UNKNOWN);
    waiting_command_ = op_code;
    command_credits_ = 0;  // Only allow one outstanding command
    if (hci_timeout_alarm_ != nullptr) {
      hci_timeout_alarm_->Schedule(BindOnce(&impl::on_hci_timeout, common::Unretained(this), op_code), kHciTimeoutMs);
    } else {
      LOG_WARN("%s sent without an hci-timeout timer", OpCodeText(op_code).c_str());
    }
  }

  void register_event(EventCode event, ContextualCallback<void(EventView)> handler) {
    ASSERT_LOG(
        event != EventCode::LE_META_EVENT,
        "Can not register handler for %02hhx (%s)",
        EventCode::LE_META_EVENT,
        EventCodeText(EventCode::LE_META_EVENT).c_str());
    // Allow GD Cert tests to register for CONNECTION_REQUEST
    if (event == EventCode::CONNECTION_REQUEST && module_.on_acl_connection_request_.IsEmpty()) {
      LOG_INFO("Registering test for CONNECTION_REQUEST, since there's no ACL");
      event_handlers_.erase(event);
    }
    ASSERT_LOG(
        event_handlers_.count(event) == 0,
        "Can not register a second handler for %02hhx (%s)",
        event,
        EventCodeText(event).c_str());
    event_handlers_[event] = handler;
  }

  void unregister_event(EventCode event) {
    event_handlers_.erase(event);
  }

  void register_le_event(SubeventCode event, ContextualCallback<void(LeMetaEventView)> handler) {
    ASSERT_LOG(
        subevent_handlers_.count(event) == 0,
        "Can not register a second handler for %02hhx (%s)",
        event,
        SubeventCodeText(event).c_str());
    subevent_handlers_[event] = handler;
  }

  void unregister_le_event(SubeventCode event) {
    subevent_handlers_.erase(subevent_handlers_.find(event));
  }

  static void abort_after_root_inflammation(uint8_t vse_error) {
    ASSERT_LOG(false, "Root inflammation with reason 0x%02hhx", vse_error);
  }

  void handle_root_inflammation(uint8_t vse_error_reason) {
    LOG_ERROR("Received a Root Inflammation Event vendor reason 0x%02hhx, scheduling an abort",
              vse_error_reason);
    bluetooth::os::LogMetricBluetoothHalCrashReason(Address::kEmpty, 0, vse_error_reason);
    // Add Logging for crash reason
    if (hci_timeout_alarm_ != nullptr) {
      hci_timeout_alarm_->Cancel();
      delete hci_timeout_alarm_;
      hci_timeout_alarm_ = nullptr;
    }
    if (hci_abort_alarm_ == nullptr) {
      hci_abort_alarm_ = new Alarm(module_.GetHandler());
      hci_abort_alarm_->Schedule(BindOnce(&abort_after_root_inflammation, vse_error_reason), kHciTimeoutRestartMs);
    } else {
      LOG_WARN("Abort timer already scheduled");
    }
  }

  void on_hci_event(EventView event) {
    ASSERT(event.IsValid());
    if (command_queue_.empty()) {
      auto event_code = event.GetEventCode();
      // BT Core spec 5.2 (Volume 4, Part E section 4.4) allows anytime
      // COMMAND_COMPLETE and COMMAND_STATUS with opcode 0x0 for flow control
      if (event_code == EventCode::COMMAND_COMPLETE) {
        auto view = CommandCompleteView::Create(event);
        ASSERT(view.IsValid());
        auto op_code = view.GetCommandOpCode();
        ASSERT_LOG(
            op_code == OpCode::NONE,
            "Received %s event with OpCode 0x%02hx (%s) without a waiting command"
            "(is the HAL sending commands, but not handling the events?)",
            EventCodeText(event_code).c_str(),
            op_code,
            OpCodeText(op_code).c_str());
      }
      if (event_code == EventCode::COMMAND_STATUS) {
        auto view = CommandStatusView::Create(event);
        ASSERT(view.IsValid());
        auto op_code = view.GetCommandOpCode();
        if (op_code == OpCode::READ_REMOTE_VERSION_INFORMATION) {
            LOG_WARN("OpCode 0x%02hx (%s) is not in the command queue, skipped.", op_code, OpCodeText(op_code).c_str());
            return;
        }
        ASSERT_LOG(
            op_code == OpCode::NONE,
            "Received %s event with OpCode 0x%02hx (%s) without a waiting command"
            "(is the HAL sending commands, but not handling the events?)",
            EventCodeText(event_code).c_str(),
            op_code,
            OpCodeText(op_code).c_str());
      }
      std::unique_ptr<CommandView> no_waiting_command{nullptr};
      log_hci_event(no_waiting_command, event, module_.GetDependency<storage::StorageModule>());
    } else {
      log_hci_event(command_queue_.front().command_view, event, module_.GetDependency<storage::StorageModule>());
    }
    power_telemetry::GetInstance().LogHciEvtDetail();
    EventCode event_code = event.GetEventCode();
    // Root Inflamation is a special case, since it aborts here
    if (event_code == EventCode::VENDOR_SPECIFIC) {
      auto view = VendorSpecificEventView::Create(event);
      ASSERT(view.IsValid());
      if (view.GetSubeventCode() == VseSubeventCode::BQR_EVENT) {
        auto bqr_event = BqrEventView::Create(view);
        auto inflammation = BqrRootInflammationEventView::Create(bqr_event);
        if (bqr_event.IsValid() && inflammation.IsValid()) {
          handle_root_inflammation(inflammation.GetVendorSpecificErrorCode());
          return;
        }
      }
    }
    switch (event_code) {
      case EventCode::COMMAND_COMPLETE:
        on_command_complete(event);
        break;
      case EventCode::COMMAND_STATUS:
        on_command_status(event);
        break;
      case EventCode::LE_META_EVENT:
        on_le_meta_event(event);
        break;
      case EventCode::HARDWARE_ERROR:
        on_hardware_error(event);
        break;
      default:
        if (event_handlers_.find(event_code) == event_handlers_.end()) {
          LOG_WARN(
              "Unhandled event of type 0x%02hhx (%s)",
              event_code,
              EventCodeText(event_code).c_str());
        } else {
          event_handlers_[event_code].Invoke(event);
        }
    }
  }

  void on_hardware_error(EventView event) {
    HardwareErrorView event_view = HardwareErrorView::Create(event);
    ASSERT(event_view.IsValid());
#ifdef TARGET_FLOSS
    LOG_WARN("Hardware Error Event with code 0x%02x", event_view.GetHardwareCode());
    // Sending SIGINT to process the exception from BT controller.
    // The Floss daemon will be restarted. HCI reset during restart will clear the
    // error state of the BT controller.
    kill(getpid(), SIGINT);
#else
    LOG_ALWAYS_FATAL("Hardware Error Event with code 0x%02x", event_view.GetHardwareCode());
#endif
  }

  void on_le_meta_event(EventView event) {
    LeMetaEventView meta_event_view = LeMetaEventView::Create(event);
    ASSERT(meta_event_view.IsValid());
    SubeventCode subevent_code = meta_event_view.GetSubeventCode();
    if (subevent_handlers_.find(subevent_code) == subevent_handlers_.end()) {
      LOG_WARN("Unhandled le subevent of type 0x%02hhx (%s)", subevent_code, SubeventCodeText(subevent_code).c_str());
      return;
    }
    subevent_handlers_[subevent_code].Invoke(meta_event_view);
  }

  hal::HciHal* hal_;
  HciLayer& module_;

  // Command Handling
  std::list<CommandQueueEntry> command_queue_;

  std::map<EventCode, ContextualCallback<void(EventView)>> event_handlers_;
  std::map<SubeventCode, ContextualCallback<void(LeMetaEventView)>> subevent_handlers_;
  OpCode waiting_command_{OpCode::NONE};
  uint8_t command_credits_{1};  // Send reset first
  Alarm* hci_timeout_alarm_{nullptr};
  Alarm* hci_abort_alarm_{nullptr};

  // Acl packets
  BidiQueue<AclView, AclBuilder> acl_queue_{3 /* TODO: Set queue depth */};
  os::EnqueueBuffer<AclView> incoming_acl_buffer_{acl_queue_.GetDownEnd()};

  // SCO packets
  BidiQueue<ScoView, ScoBuilder> sco_queue_{3 /* TODO: Set queue depth */};
  os::EnqueueBuffer<ScoView> incoming_sco_buffer_{sco_queue_.GetDownEnd()};

  // ISO packets
  BidiQueue<IsoView, IsoBuilder> iso_queue_{3 /* TODO: Set queue depth */};
  os::EnqueueBuffer<IsoView> incoming_iso_buffer_{iso_queue_.GetDownEnd()};
};

// All functions here are running on the HAL thread
struct HciLayer::hal_callbacks : public hal::HciHalCallbacks {
  hal_callbacks(HciLayer& module) : module_(module) {}

  void hciEventReceived(hal::HciPacket event_bytes) override {
    auto packet = packet::PacketView<packet::kLittleEndian>(std::make_shared<std::vector<uint8_t>>(event_bytes));
    EventView event = EventView::Create(packet);
    module_.CallOn(module_.impl_, &impl::on_hci_event, std::move(event));
  }

  void aclDataReceived(hal::HciPacket data_bytes) override {
    auto packet = packet::PacketView<packet::kLittleEndian>(
        std::make_shared<std::vector<uint8_t>>(std::move(data_bytes)));
    auto acl = std::make_unique<AclView>(AclView::Create(packet));
    module_.impl_->incoming_acl_buffer_.Enqueue(std::move(acl), module_.GetHandler());
  }

  void scoDataReceived(hal::HciPacket data_bytes) override {
    auto packet = packet::PacketView<packet::kLittleEndian>(
        std::make_shared<std::vector<uint8_t>>(std::move(data_bytes)));
    auto sco = std::make_unique<ScoView>(ScoView::Create(packet));
    module_.impl_->incoming_sco_buffer_.Enqueue(std::move(sco), module_.GetHandler());
  }

  void isoDataReceived(hal::HciPacket data_bytes) override {
    auto packet = packet::PacketView<packet::kLittleEndian>(
        std::make_shared<std::vector<uint8_t>>(std::move(data_bytes)));
    auto iso = std::make_unique<IsoView>(IsoView::Create(packet));
    module_.impl_->incoming_iso_buffer_.Enqueue(std::move(iso), module_.GetHandler());
  }

  HciLayer& module_;
};

HciLayer::HciLayer() : impl_(nullptr), hal_callbacks_(nullptr) {}

HciLayer::~HciLayer() {}

common::BidiQueueEnd<AclBuilder, AclView>* HciLayer::GetAclQueueEnd() {
  return impl_->acl_queue_.GetUpEnd();
}

common::BidiQueueEnd<ScoBuilder, ScoView>* HciLayer::GetScoQueueEnd() {
  return impl_->sco_queue_.GetUpEnd();
}

common::BidiQueueEnd<IsoBuilder, IsoView>* HciLayer::GetIsoQueueEnd() {
  return impl_->iso_queue_.GetUpEnd();
}

void HciLayer::EnqueueCommand(
    unique_ptr<CommandBuilder> command, ContextualOnceCallback<void(CommandCompleteView)> on_complete) {
  CallOn(
      impl_,
      &impl::enqueue_command<CommandCompleteView>,
      std::move(command),
      std::move(on_complete));
}

void HciLayer::EnqueueCommand(
    unique_ptr<CommandBuilder> command, ContextualOnceCallback<void(CommandStatusView)> on_status) {
  CallOn(
      impl_, &impl::enqueue_command<CommandStatusView>, std::move(command), std::move(on_status));
}

void HciLayer::RegisterEventHandler(EventCode event, ContextualCallback<void(EventView)> handler) {
  CallOn(impl_, &impl::register_event, event, handler);
}

void HciLayer::UnregisterEventHandler(EventCode event) {
  CallOn(impl_, &impl::unregister_event, event);
}

void HciLayer::RegisterLeEventHandler(SubeventCode event, ContextualCallback<void(LeMetaEventView)> handler) {
  CallOn(impl_, &impl::register_le_event, event, handler);
}

void HciLayer::UnregisterLeEventHandler(SubeventCode event) {
  CallOn(impl_, &impl::unregister_le_event, event);
}

void HciLayer::on_disconnection_complete(EventView event_view) {
  auto disconnection_view = DisconnectionCompleteView::Create(event_view);
  if (!disconnection_view.IsValid()) {
    LOG_INFO("Dropping invalid disconnection packet");
    return;
  }

  uint16_t handle = disconnection_view.GetConnectionHandle();
  ErrorCode reason = disconnection_view.GetReason();
  Disconnect(handle, reason);
}

void HciLayer::on_connection_request(EventView event_view) {
  auto view = ConnectionRequestView::Create(event_view);
  if (!view.IsValid()) {
    LOG_INFO("Dropping invalid connection request packet");
    return;
  }

  Address address = view.GetBdAddr();
  ClassOfDevice cod = view.GetClassOfDevice();
  ConnectionRequestLinkType link_type = view.GetLinkType();
  switch (link_type) {
    case ConnectionRequestLinkType::ACL:
      if (on_acl_connection_request_.IsEmpty()) {
        LOG_WARN("No callback registered for ACL connection requests.");
      } else {
        on_acl_connection_request_.Invoke(address, cod);
      }
      break;
    case ConnectionRequestLinkType::SCO:
    case ConnectionRequestLinkType::ESCO:
      if (on_sco_connection_request_.IsEmpty()) {
        LOG_WARN("No callback registered for SCO connection requests.");
      } else {
        on_sco_connection_request_.Invoke(address, cod, link_type);
      }
      break;
  }
}

void HciLayer::Disconnect(uint16_t handle, ErrorCode reason) {
  std::unique_lock<std::mutex> lock(callback_handlers_guard_);
  for (auto callback : disconnect_handlers_) {
    callback.Invoke(handle, reason);
  }
}

void HciLayer::RegisterForDisconnects(ContextualCallback<void(uint16_t, ErrorCode)> on_disconnect) {
  std::unique_lock<std::mutex> lock(callback_handlers_guard_);
  disconnect_handlers_.push_back(on_disconnect);
}

void HciLayer::on_read_remote_version_complete(EventView event_view) {
  auto view = ReadRemoteVersionInformationCompleteView::Create(event_view);
  ASSERT_LOG(view.IsValid(), "Read remote version information packet invalid");
  ReadRemoteVersion(
      view.GetStatus(),
      view.GetConnectionHandle(),
      view.GetVersion(),
      view.GetManufacturerName(),
      view.GetSubVersion());
}

void HciLayer::ReadRemoteVersion(
    hci::ErrorCode hci_status, uint16_t handle, uint8_t version, uint16_t manufacturer_name, uint16_t sub_version) {
  std::unique_lock<std::mutex> lock(callback_handlers_guard_);
  for (auto callback : read_remote_version_handlers_) {
    callback.Invoke(hci_status, handle, version, manufacturer_name, sub_version);
  }
}

AclConnectionInterface* HciLayer::GetAclConnectionInterface(
    ContextualCallback<void(EventView)> event_handler,
    ContextualCallback<void(uint16_t, ErrorCode)> on_disconnect,
    ContextualCallback<void(Address, ClassOfDevice)> on_connection_request,
    ContextualCallback<void(
        hci::ErrorCode hci_status,
        uint16_t,
        uint8_t version,
        uint16_t manufacturer_name,
        uint16_t sub_version)> on_read_remote_version) {
  {
    std::unique_lock<std::mutex> lock(callback_handlers_guard_);
    disconnect_handlers_.push_back(on_disconnect);
    read_remote_version_handlers_.push_back(on_read_remote_version);
    on_acl_connection_request_ = on_connection_request;
  }
  for (const auto event : AclConnectionEvents) {
    RegisterEventHandler(event, event_handler);
  }
  return &acl_connection_manager_interface_;
}

void HciLayer::PutAclConnectionInterface() {
  for (const auto event : AclConnectionEvents) {
    UnregisterEventHandler(event);
  }
  {
    std::unique_lock<std::mutex> lock(callback_handlers_guard_);
    disconnect_handlers_.clear();
    read_remote_version_handlers_.clear();
  }
}

LeAclConnectionInterface* HciLayer::GetLeAclConnectionInterface(
    ContextualCallback<void(LeMetaEventView)> event_handler,
    ContextualCallback<void(uint16_t, ErrorCode)> on_disconnect,
    ContextualCallback<
        void(hci::ErrorCode hci_status, uint16_t, uint8_t version, uint16_t manufacturer_name, uint16_t sub_version)>
        on_read_remote_version) {
  {
    std::unique_lock<std::mutex> lock(callback_handlers_guard_);
    disconnect_handlers_.push_back(on_disconnect);
    read_remote_version_handlers_.push_back(on_read_remote_version);
  }
  for (const auto event : LeConnectionManagementEvents) {
    RegisterLeEventHandler(event, event_handler);
  }
  return &le_acl_connection_manager_interface_;
}

void HciLayer::PutLeAclConnectionInterface() {
  for (const auto event : LeConnectionManagementEvents) {
    UnregisterLeEventHandler(event);
  }
  {
    std::unique_lock<std::mutex> lock(callback_handlers_guard_);
    disconnect_handlers_.clear();
    read_remote_version_handlers_.clear();
  }
}

void HciLayer::RegisterForScoConnectionRequests(
    common::ContextualCallback<void(Address, ClassOfDevice, ConnectionRequestLinkType)>
        on_sco_connection_request) {
  std::unique_lock<std::mutex> lock(callback_handlers_guard_);
  on_sco_connection_request_ = on_sco_connection_request;
}

SecurityInterface* HciLayer::GetSecurityInterface(ContextualCallback<void(EventView)> event_handler) {
  for (const auto event : SecurityEvents) {
    RegisterEventHandler(event, event_handler);
  }
  return &security_interface;
}

LeSecurityInterface* HciLayer::GetLeSecurityInterface(ContextualCallback<void(LeMetaEventView)> event_handler) {
  for (const auto subevent : LeSecurityEvents) {
    RegisterLeEventHandler(subevent, event_handler);
  }
  return &le_security_interface;
}

LeAdvertisingInterface* HciLayer::GetLeAdvertisingInterface(ContextualCallback<void(LeMetaEventView)> event_handler) {
  for (const auto subevent : LeAdvertisingEvents) {
    RegisterLeEventHandler(subevent, event_handler);
  }
  return &le_advertising_interface;
}

LeScanningInterface* HciLayer::GetLeScanningInterface(ContextualCallback<void(LeMetaEventView)> event_handler) {
  for (const auto subevent : LeScanningEvents) {
    RegisterLeEventHandler(subevent, event_handler);
  }
  return &le_scanning_interface;
}

LeIsoInterface* HciLayer::GetLeIsoInterface(ContextualCallback<void(LeMetaEventView)> event_handler) {
  for (const auto subevent : LeIsoEvents) {
    RegisterLeEventHandler(subevent, event_handler);
  }
  return &le_iso_interface;
}

DistanceMeasurementInterface* HciLayer::GetDistanceMeasurementInterface(
    ContextualCallback<void(LeMetaEventView)> event_handler) {
  for (const auto subevent : DistanceMeasurementEvents) {
    RegisterLeEventHandler(subevent, event_handler);
  }
  return &distance_measurement_interface;
}

const ModuleFactory HciLayer::Factory = ModuleFactory([]() { return new HciLayer(); });

void HciLayer::ListDependencies(ModuleList* list) const {
  list->add<hal::HciHal>();
  list->add<storage::StorageModule>();
}

void HciLayer::Start() {
  auto hal = GetDependency<hal::HciHal>();
  impl_ = new impl(hal, *this);
  hal_callbacks_ = new hal_callbacks(*this);

  Handler* handler = GetHandler();
  impl_->acl_queue_.GetDownEnd()->RegisterDequeue(handler, BindOn(impl_, &impl::on_outbound_acl_ready));
  impl_->sco_queue_.GetDownEnd()->RegisterDequeue(handler, BindOn(impl_, &impl::on_outbound_sco_ready));
  impl_->iso_queue_.GetDownEnd()->RegisterDequeue(
      handler, BindOn(impl_, &impl::on_outbound_iso_ready));
  StartWithNoHalDependencies(handler);
  hal->registerIncomingPacketCallback(hal_callbacks_);
  EnqueueCommand(ResetBuilder::Create(), handler->BindOnce(&fail_if_reset_complete_not_success));
}

// Initialize event handlers that don't depend on the HAL
void HciLayer::StartWithNoHalDependencies(Handler* handler) {
  RegisterEventHandler(EventCode::DISCONNECTION_COMPLETE, handler->BindOn(this, &HciLayer::on_disconnection_complete));
  RegisterEventHandler(
      EventCode::READ_REMOTE_VERSION_INFORMATION_COMPLETE,
      handler->BindOn(this, &HciLayer::on_read_remote_version_complete));
  auto drop_packet = handler->BindOn(impl_, &impl::drop);
  RegisterEventHandler(EventCode::PAGE_SCAN_REPETITION_MODE_CHANGE, drop_packet);
  RegisterEventHandler(EventCode::MAX_SLOTS_CHANGE, drop_packet);
  RegisterEventHandler(
      EventCode::CONNECTION_REQUEST, handler->BindOn(this, &HciLayer::on_connection_request));
}

void HciLayer::Stop() {
  auto hal = GetDependency<hal::HciHal>();
  hal->unregisterIncomingPacketCallback();
  delete hal_callbacks_;

  impl_->acl_queue_.GetDownEnd()->UnregisterDequeue();
  impl_->sco_queue_.GetDownEnd()->UnregisterDequeue();
  impl_->iso_queue_.GetDownEnd()->UnregisterDequeue();
  delete impl_;
}

}  // namespace hci
}  // namespace bluetooth
