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

#include "hci/facade/facade.h"

#include <memory>

#include "blueberry/facade/hci/hci_facade.grpc.pb.h"
#include "common/bind.h"
#include "grpc/grpc_event_queue.h"
#include "hci/controller.h"
#include "hci/hci_layer.h"
#include "hci/hci_packets.h"

using ::grpc::ServerAsyncResponseWriter;
using ::grpc::ServerAsyncWriter;
using ::grpc::ServerContext;

namespace bluetooth {
namespace hci {
namespace facade {

using namespace blueberry::facade::hci;

class HciFacadeService : public HciFacade::Service {
 public:
  HciFacadeService(HciLayer* hci_layer, Controller* controller, ::bluetooth::os::Handler* facade_handler)
      : hci_layer_(hci_layer), controller_(controller), facade_handler_(facade_handler) {}

  virtual ~HciFacadeService() {
    if (unregister_acl_dequeue_) {
      hci_layer_->GetAclQueueEnd()->UnregisterDequeue();
    }
    if (waiting_acl_packet_ != nullptr) {
      hci_layer_->GetAclQueueEnd()->UnregisterEnqueue();
      if (waiting_acl_packet_ != nullptr) {
        waiting_acl_packet_.reset();
      }
    }
  }

  class TestCommandBuilder : public CommandBuilder {
   public:
    explicit TestCommandBuilder(std::vector<uint8_t> bytes) : CommandBuilder(OpCode::NONE), bytes_(std::move(bytes)) {}
    size_t size() const override {
      return bytes_.size();
    }
    void Serialize(BitInserter& bit_inserter) const override {
      for (auto&& b : bytes_) {
        bit_inserter.insert_byte(b);
      }
    }

   private:
    std::vector<uint8_t> bytes_;
  };

  ::grpc::Status SendCommand(
      ::grpc::ServerContext* /* context */,
      const ::blueberry::facade::Data* command,
      ::google::protobuf::Empty* /* response */) override {
    auto payload = std::vector<uint8_t>(command->payload().begin(), command->payload().end());
    auto packet = std::make_unique<TestCommandBuilder>(payload);
    auto opcode = static_cast<const bluetooth::hci::OpCode>(payload.at(1) << 8 | payload.at(0));
    if (Checker::IsCommandStatusOpcode(opcode)) {
      hci_layer_->EnqueueCommand(std::move(packet), facade_handler_->BindOnceOn(this, &HciFacadeService::on_status));
    } else {
      hci_layer_->EnqueueCommand(std::move(packet), facade_handler_->BindOnceOn(this, &HciFacadeService::on_complete));
    }
    return ::grpc::Status::OK;
  }

  ::grpc::Status RequestEvent(
      ::grpc::ServerContext* /* context */,
      const ::blueberry::facade::hci::EventRequest* event,
      ::google::protobuf::Empty* /* response */) override {
    hci_layer_->RegisterEventHandler(
        static_cast<EventCode>(event->code()), facade_handler_->BindOn(this, &HciFacadeService::on_event));
    return ::grpc::Status::OK;
  }

  ::grpc::Status RequestLeSubevent(
      ::grpc::ServerContext* /* context */,
      const ::blueberry::facade::hci::EventRequest* event,
      ::google::protobuf::Empty* /* response */) override {
    hci_layer_->RegisterLeEventHandler(
        static_cast<SubeventCode>(event->code()), facade_handler_->BindOn(this, &HciFacadeService::on_le_subevent));
    return ::grpc::Status::OK;
  }

  ::grpc::Status StreamEvents(
      ::grpc::ServerContext* context,
      const ::google::protobuf::Empty* /* request */,
      ::grpc::ServerWriter<::blueberry::facade::Data>* writer) override {
    return pending_events_.RunLoop(context, writer);
  };

  ::grpc::Status StreamLeSubevents(
      ::grpc::ServerContext* context,
      const ::google::protobuf::Empty* /* request */,
      ::grpc::ServerWriter<::blueberry::facade::Data>* writer) override {
    return pending_le_events_.RunLoop(context, writer);
  };

  class TestAclBuilder : public AclBuilder {
   public:
    explicit TestAclBuilder(std::vector<uint8_t> payload)
        : AclBuilder(0xbad, PacketBoundaryFlag::CONTINUING_FRAGMENT, BroadcastFlag::ACTIVE_PERIPHERAL_BROADCAST),
          bytes_(std::move(payload)) {}

    size_t size() const override {
      return bytes_.size();
    }
    void Serialize(BitInserter& bit_inserter) const override {
      for (auto&& b : bytes_) {
        bit_inserter.insert_byte(b);
      }
    }

   private:
    std::vector<uint8_t> bytes_;
  };

  ::grpc::Status SendAcl(
      ::grpc::ServerContext* /* context */,
      const ::blueberry::facade::Data* acl,
      ::google::protobuf::Empty* /* response */) override {
    waiting_acl_packet_ =
        std::make_unique<TestAclBuilder>(std::vector<uint8_t>(acl->payload().begin(), acl->payload().end()));
    std::promise<void> enqueued;
    auto future = enqueued.get_future();
    if (!completed_packets_callback_registered_) {
      controller_->RegisterCompletedAclPacketsCallback(
          facade_handler_->Bind([](uint16_t, uint16_t) { /* do nothing */ }));
      completed_packets_callback_registered_ = true;
    }
    hci_layer_->GetAclQueueEnd()->RegisterEnqueue(
        facade_handler_,
        common::Bind(&HciFacadeService::handle_enqueue_acl, common::Unretained(this), common::Unretained(&enqueued)));
    auto result = future.wait_for(std::chrono::milliseconds(100));
    ASSERT(std::future_status::ready == result);
    return ::grpc::Status::OK;
  }

  ::grpc::Status StreamAcl(
      ::grpc::ServerContext* context,
      const ::google::protobuf::Empty* /* request */,
      ::grpc::ServerWriter<::blueberry::facade::Data>* writer) override {
    hci_layer_->GetAclQueueEnd()->RegisterDequeue(
        facade_handler_, common::Bind(&HciFacadeService::on_acl_ready, common::Unretained(this)));
    unregister_acl_dequeue_ = true;
    return pending_acl_events_.RunLoop(context, writer);
  };

 private:
  std::unique_ptr<AclBuilder> handle_enqueue_acl(std::promise<void>* promise) {
    promise->set_value();
    hci_layer_->GetAclQueueEnd()->UnregisterEnqueue();
    return std::move(waiting_acl_packet_);
  }

  void on_acl_ready() {
    auto acl_ptr = hci_layer_->GetAclQueueEnd()->TryDequeue();
    ASSERT(acl_ptr != nullptr);
    ASSERT(acl_ptr->IsValid());
    LOG_INFO("Got an Acl message for handle 0x%hx", acl_ptr->GetHandle());
    ::blueberry::facade::Data incoming;
    incoming.set_payload(std::string(acl_ptr->begin(), acl_ptr->end()));
    pending_acl_events_.OnIncomingEvent(std::move(incoming));
  }

  void on_event(hci::EventView view) {
    ASSERT(view.IsValid());
    LOG_INFO("Got an Event %s", EventCodeText(view.GetEventCode()).c_str());
    ::blueberry::facade::Data response;
    response.set_payload(std::string(view.begin(), view.end()));
    pending_events_.OnIncomingEvent(std::move(response));
  }

  void on_le_subevent(hci::LeMetaEventView view) {
    ASSERT(view.IsValid());
    LOG_INFO("Got an LE Event %s", SubeventCodeText(view.GetSubeventCode()).c_str());
    ::blueberry::facade::Data response;
    response.set_payload(std::string(view.begin(), view.end()));
    pending_le_events_.OnIncomingEvent(std::move(response));
  }

  void on_complete(hci::CommandCompleteView view) {
    ASSERT(view.IsValid());
    LOG_INFO("Got a Command complete %s", OpCodeText(view.GetCommandOpCode()).c_str());
    ::blueberry::facade::Data response;
    response.set_payload(std::string(view.begin(), view.end()));
    pending_events_.OnIncomingEvent(std::move(response));
  }

  void on_status(hci::CommandStatusView view) {
    ASSERT(view.IsValid());
    LOG_INFO("Got a Command status %s", OpCodeText(view.GetCommandOpCode()).c_str());
    ::blueberry::facade::Data response;
    response.set_payload(std::string(view.begin(), view.end()));
    pending_events_.OnIncomingEvent(std::move(response));
  }

  HciLayer* hci_layer_;
  Controller* controller_;
  ::bluetooth::os::Handler* facade_handler_;
  ::bluetooth::grpc::GrpcEventQueue<::blueberry::facade::Data> pending_events_{"StreamEvents"};
  ::bluetooth::grpc::GrpcEventQueue<::blueberry::facade::Data> pending_le_events_{"StreamLeSubevents"};
  ::bluetooth::grpc::GrpcEventQueue<::blueberry::facade::Data> pending_acl_events_{"StreamAcl"};
  bool unregister_acl_dequeue_{false};
  std::unique_ptr<TestAclBuilder> waiting_acl_packet_;
  bool completed_packets_callback_registered_{false};
};

void HciFacadeModule::ListDependencies(ModuleList* list) const {
  ::bluetooth::grpc::GrpcFacadeModule::ListDependencies(list);
  list->add<HciLayer>();
  list->add<Controller>();
}

void HciFacadeModule::Start() {
  ::bluetooth::grpc::GrpcFacadeModule::Start();
  service_ = new HciFacadeService(GetDependency<HciLayer>(), GetDependency<Controller>(), GetHandler());
}

void HciFacadeModule::Stop() {
  delete service_;
  ::bluetooth::grpc::GrpcFacadeModule::Stop();
}

::grpc::Service* HciFacadeModule::GetService() const {
  return service_;
}

const ModuleFactory HciFacadeModule::Factory = ::bluetooth::ModuleFactory([]() { return new HciFacadeModule(); });

}  // namespace facade
}  // namespace hci
}  // namespace bluetooth
