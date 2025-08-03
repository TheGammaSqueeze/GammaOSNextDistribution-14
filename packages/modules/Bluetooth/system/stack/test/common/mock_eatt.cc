/*
 * Copyright 2020 HIMSA II K/S - www.himsa.com. Represented by EHIMA -
 * www.ehima.com
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

#include "mock_eatt.h"

#include "types/raw_address.h"

MockEattExtension* mock_pimpl_;
MockEattExtension* MockEattExtension::GetInstance() {
  bluetooth::eatt::EattExtension::GetInstance();
  return mock_pimpl_;
}

namespace bluetooth {
namespace eatt {

struct EattExtension::impl : public MockEattExtension {
  impl() = default;
  ~impl() = default;
  bool IsRunning() { return mock_pimpl_ ? true : false; }
};

void EattExtension::AddFromStorage(const RawAddress& bd_addr) {}

EattExtension::EattExtension() : pimpl_(std::make_unique<impl>()) {}

bool EattExtension::IsEattSupportedByPeer(const RawAddress& bd_addr) {
  return pimpl_->IsEattSupportedByPeer(bd_addr);
}

void EattExtension::Connect(const RawAddress& bd_addr) {
  pimpl_->Connect(bd_addr);
}

void EattExtension::Disconnect(const RawAddress& bd_addr, uint16_t cid) {
  pimpl_->Disconnect(bd_addr, cid);
}

void EattExtension::Reconfigure(const RawAddress& bd_addr, uint16_t cid,
                                uint16_t mtu) {
  pimpl_->Reconfigure(bd_addr, cid, mtu);
}
void EattExtension::ReconfigureAll(const RawAddress& bd_addr, uint16_t mtu) {
  pimpl_->ReconfigureAll(bd_addr, mtu);
}

EattChannel* EattExtension::FindEattChannelByCid(const RawAddress& bd_addr,
                                                 uint16_t cid) {
  return pimpl_->FindEattChannelByCid(bd_addr, cid);
}

EattChannel* EattExtension::FindEattChannelByTransId(const RawAddress& bd_addr,
                                                     uint32_t trans_id) {
  return pimpl_->FindEattChannelByTransId(bd_addr, trans_id);
}

bool EattExtension::IsIndicationPending(const RawAddress& bd_addr,
                                        uint16_t indication_handle) {
  return pimpl_->IsIndicationPending(bd_addr, indication_handle);
}

EattChannel* EattExtension::GetChannelAvailableForIndication(
    const RawAddress& bd_addr) {
  return pimpl_->GetChannelAvailableForIndication(bd_addr);
}

void EattExtension::FreeGattResources(const RawAddress& bd_addr) {
  pimpl_->FreeGattResources(bd_addr);
}

bool EattExtension::IsOutstandingMsgInSendQueue(const RawAddress& bd_addr) {
  return pimpl_->IsOutstandingMsgInSendQueue(bd_addr);
}

EattChannel* EattExtension::GetChannelWithQueuedDataToSend(
    const RawAddress& bd_addr) {
  return pimpl_->GetChannelWithQueuedDataToSend(bd_addr);
}

EattChannel* EattExtension::GetChannelAvailableForClientRequest(
    const RawAddress& bd_addr) {
  return pimpl_->GetChannelAvailableForClientRequest(bd_addr);
}

/* Start stop GATT indication timer per CID */
void EattExtension::StartIndicationConfirmationTimer(const RawAddress& bd_addr,
                                                     uint16_t cid) {
  pimpl_->StartIndicationConfirmationTimer(bd_addr, cid);
}

void EattExtension::StopIndicationConfirmationTimer(const RawAddress& bd_addr,
                                                    uint16_t cid) {
  pimpl_->StopIndicationConfirmationTimer(bd_addr, cid);
}

/* Start stop application indication timeout */
void EattExtension::StartAppIndicationTimer(const RawAddress& bd_addr,
                                            uint16_t cid) {
  pimpl_->StartAppIndicationTimer(bd_addr, cid);
}

void EattExtension::StopAppIndicationTimer(const RawAddress& bd_addr,
                                           uint16_t cid) {
  pimpl_->StopAppIndicationTimer(bd_addr, cid);
}

void EattExtension::Start() {
  // It is needed here as IsoManager which is a singleton creates it, but in
  // this mock we want to destroy and recreate the mock on each test case.
  if (!pimpl_) {
    pimpl_ = std::make_unique<impl>();
  }

  mock_pimpl_ = pimpl_.get();
  pimpl_->Start();
}

void EattExtension::Stop() {
  // It is needed here as IsoManager which is a singleton creates it, but in
  // this mock we want to destroy and recreate the mock on each test case.
  if (pimpl_) {
    pimpl_->Stop();
    pimpl_.reset();
  }

  mock_pimpl_ = nullptr;
}

EattExtension::~EattExtension() = default;
}  // namespace eatt
}  // namespace bluetooth
