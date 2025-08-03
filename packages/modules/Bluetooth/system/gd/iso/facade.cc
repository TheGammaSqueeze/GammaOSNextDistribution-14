/*
 * Copyright 2021 The Android Open Source Project
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
#include "iso/facade.h"

#include "blueberry/facade/iso/facade.grpc.pb.h"
#include "common/contextual_callback.h"
#include "grpc/grpc_event_queue.h"
#include "hci/acl_manager.h"
#include "hci/address_with_type.h"
#include "hci/le_address_manager.h"
#include "iso/iso_module.h"
#include "os/handler.h"

using bluetooth::hci::AclManager;

namespace bluetooth {
namespace iso {

using namespace blueberry::facade::iso;

class IsoModuleFacadeService : public IsoModuleFacade::Service {
 public:
  IsoModuleFacadeService(IsoModule* iso_module, AclManager* acl_manager, ::bluetooth::os::Handler* iso_handler)
      : iso_module_(iso_module), acl_manager_(acl_manager), iso_handler_(iso_handler) {
    ASSERT(iso_module_);
    ASSERT(iso_handler_);

    iso_module_->GetIsoManager()->RegisterIsoEstablishedCallback(iso_handler_->Bind(
        [](::bluetooth::grpc::GrpcEventQueue<LeIsoEventsMsg>* le_iso_events_, uint16_t cis_connection_handle) {
          LeIsoEventsMsg msg;
          msg.set_message_type(IsoMsgType::ISO_CIS_ESTABLISHED);
          msg.add_cis_handle(cis_connection_handle);
          le_iso_events_->OnIncomingEvent(msg);
        },
        &le_iso_events_));

    iso_module_->GetIsoManager()->RegisterIsoDataCallback(
        iso_handler_->BindOn(this, &IsoModuleFacadeService::OnIsoPacketReceived));
  }

  ::grpc::Status LeSetCigParameters(
      ::grpc::ServerContext* /* context */,
      const ::bluetooth::iso::LeSetCigParametersRequest* request,
      ::google::protobuf::Empty* /* response */) override {
    std::vector<hci::CisParametersConfig> cis_config;

    hci::CisParametersConfig cfg;
    cfg.cis_id_ = request->cis_id();
    cfg.max_sdu_m_to_s_ = request->max_sdu_m_to_s();
    cfg.max_sdu_s_to_m_ = request->max_sdu_s_to_m();
    cfg.phy_m_to_s_ = request->phy_m_to_s();
    cfg.phy_s_to_m_ = request->phy_s_to_m();
    cfg.rtn_m_to_s_ = request->rtn_m_to_s();
    cfg.rtn_s_to_m_ = request->rtn_s_to_m();

    cis_config.push_back(cfg);

    iso_module_->GetIsoManager()->SetCigParameters(
        request->cig_id(),
        request->sdu_interval_m_to_s(),
        request->sdu_interval_s_to_m(),
        static_cast<hci::ClockAccuracy>(request->peripherals_clock_accuracy()),
        static_cast<hci::Packing>(request->packing()),
        static_cast<hci::Enable>(request->framing()),
        request->max_transport_latency_m_to_s(),
        request->max_transport_latency_s_to_m(),
        cis_config,
        iso_handler_->BindOnce(
            [](::bluetooth::grpc::GrpcEventQueue<LeIsoEventsMsg>* le_iso_events_, std::vector<uint16_t> conn_handles) {
              LeIsoEventsMsg msg;

              msg.set_message_type(IsoMsgType::ISO_PARAMETERS_SET_COMPLETE);
              for (const uint16_t conn_handle : conn_handles) {
                msg.add_cis_handle(conn_handle);
              }
              le_iso_events_->OnIncomingEvent(msg);
            },
            &le_iso_events_));
    return ::grpc::Status::OK;
  }

  ::grpc::Status LeSetCigParametersTest(
      ::grpc::ServerContext* /* context */,
      const ::bluetooth::iso::LeSetCigParametersTestRequest* request,
      ::google::protobuf::Empty* /* response */) override {
    std::vector<hci::LeCisParametersTestConfig> cis_config;

    for (const auto& cc : request->cis_configs()) {
      hci::LeCisParametersTestConfig cfg;
      cfg.cis_id_ = cc.cis_id();
      cfg.nse_ = cc.nse();
      cfg.max_sdu_m_to_s_ = cc.max_sdu_m_to_s();
      cfg.max_sdu_s_to_m_ = cc.max_sdu_s_to_m();
      cfg.max_pdu_m_to_s_ = cc.max_pdu_m_to_s();
      cfg.max_pdu_s_to_m_ = cc.max_pdu_s_to_m();
      cfg.phy_m_to_s_ = cc.phy_m_to_s();
      cfg.phy_s_to_m_ = cc.phy_s_to_m();
      cfg.bn_m_to_s_ = cc.bn_m_to_s();
      cfg.bn_s_to_m_ = cc.bn_s_to_m();
      cis_config.push_back(cfg);
    }
    iso_module_->GetIsoManager()->SetCigParametersTest(
        request->cig_id(),
        request->sdu_interval_m_to_s(),
        request->sdu_interval_s_to_m(),
        request->ft_m_to_s(),
        request->ft_s_to_m(),
        request->iso_interval(),
        static_cast<hci::ClockAccuracy>(request->peripherals_clock_accuracy()),
        static_cast<hci::Packing>(request->packing()),
        static_cast<hci::Enable>(request->framing()),
        request->max_transport_latency_m_to_s(),
        request->max_transport_latency_s_to_m(),
        cis_config,
        iso_handler_->BindOnce(
            [](::bluetooth::grpc::GrpcEventQueue<LeIsoEventsMsg>* le_iso_events_, std::vector<uint16_t> conn_handles) {
              LeIsoEventsMsg msg;

              msg.set_message_type(IsoMsgType::ISO_PARAMETERS_SET_COMPLETE);
              for (const uint16_t conn_handle : conn_handles) {
                msg.add_cis_handle(conn_handle);
              }
              le_iso_events_->OnIncomingEvent(msg);
            },
            &le_iso_events_));
    return ::grpc::Status::OK;
  }

  ::grpc::Status LeCreateCis(
      ::grpc::ServerContext* /* context */,
      const ::bluetooth::iso::LeCreateCisRequest* request,
      ::google::protobuf::Empty* /* response */) override {
    std::vector<std::pair<uint16_t, uint16_t>> create_cis_params;
    for (const auto& handle_pair : request->handle_pair()) {
      create_cis_params.push_back(
          std::make_pair<uint16_t, uint16_t>(handle_pair.cis_handle(), handle_pair.acl_handle()));
    }
    iso_module_->GetIsoManager()->LeCreateCis(create_cis_params);

    return ::grpc::Status::OK;
  }

  ::grpc::Status FetchIsoData(
      ::grpc::ServerContext* context,
      const LeCisHandleMsg* /* request */,
      ::grpc::ServerWriter<IsoPacket>* writer) override {
    return le_iso_data_.RunLoop(context, writer);
  }

  ::grpc::Status FetchIsoEvents(
      ::grpc::ServerContext* context,
      const google::protobuf::Empty* /* request */,
      ::grpc::ServerWriter<LeIsoEventsMsg>* writer) override {
    return le_iso_events_.RunLoop(context, writer);
  }

  ::grpc::Status SendIsoPacket(
      ::grpc::ServerContext* /* context */,
      const ::bluetooth::iso::IsoPacket* request,
      ::google::protobuf::Empty* /* response */) override {
    std::vector<uint8_t> packet(request->payload().begin(), request->payload().end());
    iso_module_->GetIsoManager()->SendIsoPacket(request->handle(), packet);
    return ::grpc::Status::OK;
  }

  void OnIsoPacketReceived(std::unique_ptr<hci::IsoView> iso_view) {
    ASSERT(iso_view->IsValid());

    IsoPacket packet;
    packet.set_handle(iso_view->GetConnectionHandle());

    if (iso_view->GetTsFlag() == hci::TimeStampFlag::NOT_PRESENT) {
      hci::IsoWithoutTimestampView nts = hci::IsoWithoutTimestampView::Create(*iso_view);
      ASSERT(nts.IsValid());

      auto data_vec = nts.GetPayload();
      std::string data = std::string(data_vec.begin(), data_vec.end());
      packet.set_payload(data);
      le_iso_data_.OnIncomingEvent(packet);
    } else {
      hci::IsoWithTimestampView tsv = hci::IsoWithTimestampView::Create(*iso_view);
      ASSERT(tsv.IsValid());

      auto data_vec = tsv.GetPayload();
      std::string data = std::string(data_vec.begin(), data_vec.end());
      packet.set_payload(data);
      le_iso_data_.OnIncomingEvent(packet);
    }
  }

 private:
  IsoModule* iso_module_;
  ::bluetooth::grpc::GrpcEventQueue<LeIsoEventsMsg> le_iso_events_{"LE ISO events"};
  ::bluetooth::grpc::GrpcEventQueue<IsoPacket> le_iso_data_{"LE ISO data"};
  AclManager* acl_manager_ __attribute__((unused));
  ::bluetooth::os::Handler* iso_handler_;
};

void IsoModuleFacadeModule::ListDependencies(ModuleList* list) const {
  ::bluetooth::grpc::GrpcFacadeModule::ListDependencies(list);
  list->add<IsoModule>();
  list->add<AclManager>();
}

void IsoModuleFacadeModule::Start() {
  ::bluetooth::grpc::GrpcFacadeModule::Start();
  service_ = new IsoModuleFacadeService(GetDependency<IsoModule>(), GetDependency<AclManager>(), GetHandler());
}

void IsoModuleFacadeModule::Stop() {
  delete service_;
  ::bluetooth::grpc::GrpcFacadeModule::Stop();
}

::grpc::Service* IsoModuleFacadeModule::GetService() const {
  return service_;
}

const ModuleFactory IsoModuleFacadeModule::Factory =
    ::bluetooth::ModuleFactory([]() { return new IsoModuleFacadeModule(); });

}  // namespace iso
}  // namespace bluetooth
