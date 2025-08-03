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

#pragma once

#include <vector>

#include "hci/address.h"
#include "hci/controller_interface.h"
#include "hci/hci_packets.h"
#include "hci/le_rand_callback.h"
#include "module.h"

// TODO Remove this once all QTI specific hacks are removed.
#define LMP_COMPID_QTI 0x001D

namespace bluetooth {
namespace hci {

class Controller : public Module, public ControllerInterface {
 public:
  Controller();
  Controller(const Controller&) = delete;
  Controller& operator=(const Controller&) = delete;

  virtual ~Controller();

  virtual void RegisterCompletedAclPacketsCallback(CompletedAclPacketsCallback cb) override;

  virtual void UnregisterCompletedAclPacketsCallback() override;

  virtual void RegisterCompletedMonitorAclPacketsCallback(CompletedAclPacketsCallback cb) override;
  virtual void UnregisterCompletedMonitorAclPacketsCallback() override;

  virtual std::string GetLocalName() const override;

  virtual LocalVersionInformation GetLocalVersionInformation() const override;

  virtual bool SupportsSimplePairing() const override;
  virtual bool SupportsSecureConnections() const override;
  virtual bool SupportsSimultaneousLeBrEdr() const override;
  virtual bool SupportsInterlacedInquiryScan() const override;
  virtual bool SupportsRssiWithInquiryResults() const override;
  virtual bool SupportsExtendedInquiryResponse() const override;
  virtual bool SupportsRoleSwitch() const override;
  virtual bool Supports3SlotPackets() const override;
  virtual bool Supports5SlotPackets() const override;
  virtual bool SupportsClassic2mPhy() const override;
  virtual bool SupportsClassic3mPhy() const override;
  virtual bool Supports3SlotEdrPackets() const override;
  virtual bool Supports5SlotEdrPackets() const override;
  virtual bool SupportsSco() const override;
  virtual bool SupportsHv2Packets() const override;
  virtual bool SupportsHv3Packets() const override;
  virtual bool SupportsEv3Packets() const override;
  virtual bool SupportsEv4Packets() const override;
  virtual bool SupportsEv5Packets() const override;
  virtual bool SupportsEsco2mPhy() const override;
  virtual bool SupportsEsco3mPhy() const override;
  virtual bool Supports3SlotEscoEdrPackets() const override;
  virtual bool SupportsHoldMode() const override;
  virtual bool SupportsSniffMode() const override;
  virtual bool SupportsParkMode() const override;
  virtual bool SupportsNonFlushablePb() const override;
  virtual bool SupportsSniffSubrating() const override;
  virtual bool SupportsEncryptionPause() const override;
  virtual bool SupportsBle() const override;

  virtual bool SupportsBleEncryption() const override;
  virtual bool SupportsBleConnectionParametersRequest() const override;
  virtual bool SupportsBleExtendedReject() const override;
  virtual bool SupportsBlePeripheralInitiatedFeaturesExchange() const override;
  virtual bool SupportsBlePing() const override;
  virtual bool SupportsBleDataPacketLengthExtension() const override;
  virtual bool SupportsBlePrivacy() const override;
  virtual bool SupportsBleExtendedScannerFilterPolicies() const override;
  virtual bool SupportsBle2mPhy() const override;
  virtual bool SupportsBleStableModulationIndexTx() const override;
  virtual bool SupportsBleStableModulationIndexRx() const override;
  virtual bool SupportsBleCodedPhy() const override;
  virtual bool SupportsBleExtendedAdvertising() const override;
  virtual bool SupportsBlePeriodicAdvertising() const override;
  virtual bool SupportsBleChannelSelectionAlgorithm2() const override;
  virtual bool SupportsBlePowerClass1() const override;
  virtual bool SupportsBleMinimumUsedChannels() const override;
  virtual bool SupportsBleConnectionCteRequest() const override;
  virtual bool SupportsBleConnectionCteResponse() const override;
  virtual bool SupportsBleConnectionlessCteTransmitter() const override;
  virtual bool SupportsBleConnectionlessCteReceiver() const override;
  virtual bool SupportsBleAntennaSwitchingDuringCteTx() const override;
  virtual bool SupportsBleAntennaSwitchingDuringCteRx() const override;
  virtual bool SupportsBleReceivingConstantToneExtensions() const override;
  virtual bool SupportsBlePeriodicAdvertisingSyncTransferSender() const override;
  virtual bool SupportsBlePeriodicAdvertisingSyncTransferRecipient() const override;
  virtual bool SupportsBleSleepClockAccuracyUpdates() const override;
  virtual bool SupportsBleRemotePublicKeyValidation() const override;
  virtual bool SupportsBleConnectedIsochronousStreamCentral() const override;
  virtual bool SupportsBleConnectedIsochronousStreamPeripheral() const override;
  virtual bool SupportsBleIsochronousBroadcaster() const override;
  virtual bool SupportsBleSynchronizedReceiver() const override;
  virtual bool SupportsBleIsochronousChannelsHostSupport() const override;
  virtual bool SupportsBlePowerControlRequest() const override;
  virtual bool SupportsBlePowerChangeIndication() const override;
  virtual bool SupportsBlePathLossMonitoring() const override;
  virtual bool SupportsBlePeriodicAdvertisingAdi() const override;
  virtual bool SupportsBleConnectionSubrating() const override;
  virtual bool SupportsBleConnectionSubratingHost() const override;

  virtual uint16_t GetAclPacketLength() const override;

  virtual uint16_t GetNumAclPacketBuffers() const override;

  virtual uint8_t GetScoPacketLength() const override;

  virtual uint16_t GetNumScoPacketBuffers() const override;

  virtual Address GetMacAddress() const override;

  virtual void SetEventMask(uint64_t event_mask) override;

  virtual void Reset() override;

  virtual void LeRand(LeRandCallback cb) override;

  virtual void SetEventFilterClearAll() override;

  virtual void SetEventFilterInquiryResultAllDevices() override;

  virtual void SetEventFilterInquiryResultClassOfDevice(
      ClassOfDevice class_of_device, ClassOfDevice class_of_device_mask) override;

  virtual void SetEventFilterInquiryResultAddress(Address address) override;

  virtual void SetEventFilterConnectionSetupAllDevices(AutoAcceptFlag auto_accept_flag) override;

  virtual void SetEventFilterConnectionSetupClassOfDevice(
      ClassOfDevice class_of_device,
      ClassOfDevice class_of_device_mask,
      AutoAcceptFlag auto_accept_flag) override;

  virtual void SetEventFilterConnectionSetupAddress(
      Address address, AutoAcceptFlag auto_accept_flag) override;

  virtual void WriteLocalName(std::string local_name) override;

  virtual void HostBufferSize(
      uint16_t host_acl_data_packet_length,
      uint8_t host_synchronous_data_packet_length,
      uint16_t host_total_num_acl_data_packets,
      uint16_t host_total_num_synchronous_data_packets) override;

  // LE controller commands
  virtual void LeSetEventMask(uint64_t le_event_mask) override;

  virtual LeBufferSize GetLeBufferSize() const override;

  virtual uint64_t GetLeSupportedStates() const override;

  virtual LeBufferSize GetControllerIsoBufferSize() const override;

  virtual uint64_t GetControllerLeLocalSupportedFeatures() const override;

  virtual uint8_t GetLeFilterAcceptListSize() const override;

  virtual uint8_t GetLeResolvingListSize() const override;

  virtual LeMaximumDataLength GetLeMaximumDataLength() const override;

  virtual uint16_t GetLeMaximumAdvertisingDataLength() const override;

  virtual uint16_t GetLeSuggestedDefaultDataLength() const override;

  virtual uint8_t GetLeNumberOfSupportedAdverisingSets() const override;

  virtual uint8_t GetLePeriodicAdvertiserListSize() const override;

  // 7.4.8 Read Local Supported Codecs command v1 only returns codecs on the BR/EDR transport
  virtual std::vector<uint8_t> GetLocalSupportedBrEdrCodecIds() const override;

  virtual VendorCapabilities GetVendorCapabilities() const override;

  virtual uint32_t GetDabSupportedCodecs() const override;
  virtual const std::array<DynamicAudioBufferCodecCapability, 32>& GetDabCodecCapabilities()
      const override;

  virtual void SetDabAudioBufferTime(uint16_t buffer_time_ms) override;

  virtual bool IsSupported(OpCode op_code) const override;

  static const ModuleFactory Factory;

  static constexpr uint64_t kDefaultEventMask = 0x3dbfffffffffffff;
  static constexpr uint64_t kDefaultLeEventMask = 0x000000074d02fe7f;

  static constexpr uint64_t kLeEventMask53 = 0x00000007ffffffff;
  static constexpr uint64_t kLeEventMask52 = 0x00000003ffffffff;
  static constexpr uint64_t kLeEventMask51 = 0x0000000000ffffff;
  static constexpr uint64_t kLeEventMask50 = 0x0000000000ffffff;
  static constexpr uint64_t kLeEventMask42 = 0x00000000000003ff;
  static constexpr uint64_t kLeEventMask41 = 0x000000000000003f;

  static uint64_t MaskLeEventMask(HciVersion version, uint64_t mask);

 protected:
  void ListDependencies(ModuleList* list) const override;

  void Start() override;

  void Stop() override;

  std::string ToString() const override;

  DumpsysDataFinisher GetDumpsysData(flatbuffers::FlatBufferBuilder* builder) const override;  // Module

 private:
  virtual uint64_t GetLocalFeatures(uint8_t page_number) const;
  virtual uint64_t GetLocalLeFeatures() const;

  struct impl;
  std::unique_ptr<impl> impl_;
};

}  // namespace hci
}  // namespace bluetooth
